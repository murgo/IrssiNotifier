
package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

public class IrcNotificationManager {

    private static IrcNotificationManager instance;

    public static IrcNotificationManager getInstance() {
        if (instance == null) {
            instance = new IrcNotificationManager();
        }
        return instance;
    }

    private IrcNotificationManager() {
    }

    private Map<String, List<IrcMessage>> unread = new HashMap<String, List<IrcMessage>>();
    private int perMessageNotificationId = 2;
    private long lastSoundDate = 0;
    private DataAccess da;

    private int getUnreadCount() {
        int total = 0;
        for (List<IrcMessage> msgs : unread.values()) {
            total += msgs.size();
        }
        return total;
    }

    public int getUnreadCountForChannel(String channel) {
        if (!unread.containsKey(channel))
            return 0;

        return unread.get(channel).size();
    }

    private void addUnread(IrcMessage msg) {
        String key = msg.getLogicalChannel();

        List<IrcMessage> msgs;
        if (unread.containsKey(key))
            msgs = unread.get(key);
        else {
            msgs = new ArrayList<IrcMessage>();
            unread.put(key, msgs);
        }
        
        msgs.add(msg);
    }

    public void handle(Context context, String message) {
        /*
         * removed from the server side for now if (message.startsWith("read"))
         * { NotificationManager notificationManager =
         * (NotificationManager)context
         * .getSystemService(Context.NOTIFICATION_SERVICE);
         * notificationManager.cancelAll(); ircMessages.read(); return; }
         */

        Preferences prefs = new Preferences(context);
        NotificationMode mode = prefs.getNotificationMode();

        String tickerText;
        String notificationMessage;
        String titleText;
        int notificationId;
        long when = new Date().getTime();
        IrcMessage msg = new IrcMessage();
        int currentUnreadCount = 1;
        List<String> messageLines = null;

        try {
            msg.Deserialize(message);
            msg.Decrypt(prefs.getEncryptionPassword());

            if (da == null)
                da = new DataAccess(context);
            da.handleMessage(msg);
            addUnread(msg);

            ValueList values = getValues(msg, mode);
            notificationMessage = values.text;
            titleText = values.title; 
            notificationId = values.id;
            when = msg.getServerTimestamp().getTime();
            currentUnreadCount = values.count;
            messageLines = values.messageLines;

            tickerText = titleText;
        } catch (CryptoException e) {
            titleText = "IrssiNotifier error";
            notificationMessage = "Unable to decrypt data. Perhaps encryption key is wrong?";
            tickerText = "IrssiNotifier decryption error";
            notificationId = 1;
        } catch (JSONException e) {
            titleText = "IrssiNotifier error";
            notificationMessage = "Unable to parse data. Server error?";
            tickerText = "IrssiNotifier parse error";
            notificationId = 1;
        }

        IrssiNotifierActivity foregroundInstance = IrssiNotifierActivity.getForegroundInstance();
        if (foregroundInstance != null) {
            foregroundInstance.newMessage(msg);
            unread.clear();
            return;
        }

        if (!prefs.isNotificationsEnabled()) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setSmallIcon(R.drawable.notification_icon);
        builder.setTicker(tickerText);
        builder.setWhen(when);
        builder.setAutoCancel(true);
        builder.setContentText(notificationMessage);
        builder.setContentTitle(titleText);
        
        if (currentUnreadCount > 1) {
            builder.setNumber(currentUnreadCount);
        }
        
        if ((!prefs.isSpamFilterEnabled() || new Date().getTime() > lastSoundDate + 60000L)) {
            int defaults = 0;
            if (prefs.isSoundEnabled()) {
                builder.setSound(prefs.getNotificationSound());
            }

            if (prefs.isVibrationEnabled()) {
                defaults |= Notification.DEFAULT_VIBRATE;
            }

            if (prefs.isLightsEnabled()) {
                defaults |=  Notification.DEFAULT_LIGHTS;
            }

            lastSoundDate = new Date().getTime();
            builder.setDefaults(defaults);
        }

        Intent toLaunch = new Intent(context, IrssiNotifierActivity.class);
        toLaunch.putExtra("Channel", msg.getLogicalChannel());
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, toLaunch,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        Intent deleteIntent = new Intent(C2DMReceiver.NOTIFICATION_CLEARED_INTENT);
        deleteIntent.putExtra("notificationMode", mode.toString());
        deleteIntent.putExtra("channel", msg.getLogicalChannel());
        PendingIntent pendingDeleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);
        builder.setDeleteIntent(pendingDeleteIntent);
        
        if (messageLines != null && messageLines.size() > 1) {
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            
            for (String line : messageLines)
                inboxStyle.addLine(line);
            
            builder.setStyle(inboxStyle);
        }
        
        Notification notification = builder.build();
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);
    }

    public void mainActivityOpened(Context context) {
        unread.clear();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    private ValueList getValues(IrcMessage msg, NotificationMode mode) {
        String text = null;
        String title = null;
        int id = 0;
        int count = 0;
        ArrayList<String> messageLines = new ArrayList<String>();

        switch (mode) {
            case Single:
                id = 1;
                int unreadCount = getUnreadCount();
                count = unreadCount;
                
                if (unreadCount <= 1) {
                    if (msg.isPrivate()) {
                        title = "Private message from " + msg.getNick();
                        text = msg.getMessage();
                    } else {
                        title = "Hilight at " + msg.getChannel();
                        text = "(" + msg.getNick() + ") " + msg.getMessage();
                    }
                } else {
                    if (msg.isPrivate()) {
                        title = "" + unreadCount + " new hilights";
                        text = "Last: (" + msg.getNick() + ") " + msg.getMessage();
                    } else {
                        title = "" + unreadCount + " new hilights";
                        text = "Last: " + msg.getLogicalChannel() + " (" + msg.getNick() + ") " + msg.getMessage();
                    }

                    List<IrcMessage> allMessages = new ArrayList<IrcMessage>();
                    for (List<IrcMessage> list : unread.values()) {
                        allMessages.addAll(list);
                    }
                    Collections.sort(allMessages, new Comparator<IrcMessage>() {
                        public int compare(IrcMessage lhs, IrcMessage rhs) {
                            return lhs.getServerTimestamp().compareTo(rhs.getServerTimestamp());
                        }
                    });

                    for (IrcMessage message : allMessages) {
                        if (message.isPrivate()) {
                            messageLines.add("(" + message.getNick() + ") " + message.getMessage());
                        } else {
                            messageLines.add(message.getLogicalChannel() + " (" + message.getNick() + ") " + message.getMessage());
                        }
                    }
                }
                break;

            case PerMessage:
                id = perMessageNotificationId++;
                count = 1;
                if (msg.isPrivate()) {
                    title = "Private message from " + msg.getNick();
                    text = msg.getMessage();
                } else {
                    title = "Hilight at " + msg.getChannel();
                    text = "(" + msg.getNick() + ") " + msg.getMessage();
                }
                break;

            case PerChannel:
                int channelUnreadCount = getUnreadCountForChannel(msg.getLogicalChannel());
                id = msg.getLogicalChannel().hashCode();
                count = channelUnreadCount;
                if (msg.isPrivate()) {
                    if (channelUnreadCount <= 1) {
                        title = "Private message from " + msg.getNick();
                        text = msg.getMessage();
                    } else {
                        title = "" + channelUnreadCount + " private messages from " + msg.getNick();
                        text = "Last: " + msg.getMessage();
                    }
                } else {
                    if (channelUnreadCount <= 1) {
                        title = "Hilight at " + msg.getChannel();
                        text = "(" + msg.getNick() + ") " + msg.getMessage();
                    } else {
                        title = "" + channelUnreadCount + " new hilights at " + msg.getChannel();
                        text = "Last: (" + msg.getNick() + ") " + msg.getMessage();
                    }
                }
                
                List<IrcMessage> messages = unread.get(msg.getLogicalChannel());
                for (IrcMessage message : messages) {
                    messageLines.add("(" + message.getNick() + ") " + message.getMessage());
                }
                
                break;
        }
        
        ValueList values = new ValueList();
        values.text = text;
        values.title = title;
        values.id = id;
        values.count = count;
        values.messageLines = messageLines;
        
        return values;
    }

    public void notificationCleared(Context context, Intent intent) {
        NotificationMode mode = NotificationMode.valueOf(intent.getStringExtra("notificationMode"));

        if (mode == NotificationMode.Single) {
            unread.clear();
        } else if (mode == NotificationMode.PerChannel) {
            String channel = intent.getStringExtra("channel");
            unread.get(channel).clear();
        }
    }
    
    private class ValueList {
        public String text;
        public String title;
        public int id;
        public int count;
        public List<String> messageLines;
    }
}
