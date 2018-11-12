
package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

public class IrcNotificationManager {

    private static final String TAG = IrcNotificationManager.class.getName();
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

    public long getLastSoundDate() {
        return lastSoundDate;
    }

    public void setLastSoundDate(long time) {
        lastSoundDate = time;
    }

    private int getUnreadCount() {
        int total = 0;
        for (List<IrcMessage> msgs : unread.values()) {
            total += msgs.size();
        }
        return total;
    }

    public int getUnreadCountForChannel(String channel) {
        if (!unread.containsKey(channel.toLowerCase()))
            return 0;

        return unread.get(channel.toLowerCase()).size();
    }

    private void addUnread(IrcMessage msg) {
        String key = msg.getLogicalChannel().toLowerCase();

        List<IrcMessage> msgs;
        if (unread.containsKey(key))
            msgs = unread.get(key);
        else {
            msgs = new ArrayList<IrcMessage>();
            unread.put(key, msgs);
        }
        
        msgs.add(msg);
    }

    public void handle(Context context, IrcMessage msg) {
        Preferences prefs = new Preferences(context);
        NotificationMode mode = prefs.getNotificationMode();

        String tickerText;
        String notificationMessage;
        String titleText;
        int notificationId;
        long when = new Date().getTime();
        int currentUnreadCount = 1;
        List<String> messageLines = null;

        try {
            msg.decrypt(prefs.getEncryptionPassword());

            if (da == null)
                da = new DataAccess(context);

            if (!da.handleMessage(msg)) {
                return;
            }

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
            titleText = context.getString(R.string.irssinotifier_error_title);
            notificationMessage = context.getString(R.string.decryption_error_notification);
            tickerText = context.getString(R.string.decryption_error_ticker);
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

        int defaults = 0;

        if (prefs.isLightsEnabled()) {
            if (prefs.getUseDefaultLightColor()) {
                defaults |=  Notification.DEFAULT_LIGHTS;
            } else {
                builder.setLights(prefs.getCustomLightColor(), 300, 5000);
            }
        }

		if ((!prefs.isSpamFilterEnabled() || new Date().getTime() > IrcNotificationManager.getInstance().getLastSoundDate() + (1000L * prefs.getSpamFilterTime()))) {
            // no spam filter going on

            // legacy, this stuff affects only pre-android 8 phones
            if (prefs.isSoundEnabled()) {
                builder.setSound(prefs.getNotificationSound());
            }

            if (prefs.isVibrationEnabled()) {
                defaults |= Notification.DEFAULT_VIBRATE;
            }

            lastSoundDate = new Date().getTime();
            builder.setChannelId(NotificationChannelCreator.CHANNEL_DEFAULT_ID);
        } else {
            builder.setChannelId(NotificationChannelCreator.CHANNEL_LOWPRIO_ID);
        }

        builder.setDefaults(defaults);

        Intent toLaunch = new Intent(context, IrssiNotifierActivity.class);
        toLaunch.putExtra("Channel", msg.getLogicalChannel());
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, toLaunch,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        Intent deleteIntent = new Intent(NotificationClearedReceiver.NOTIFICATION_CLEARED_INTENT);
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

        if (prefs.isPebbleEnabled()) {
            try {
                notifyPebble(context, msg);
            } catch (Exception e) {
                // don't crash from pebble notifications
                Log.e(TAG, "Exception while notifying pebble", e);
            }
        }
    }

    private void notifyPebble(Context context, IrcMessage msg) {
        Intent pebbleIntent = new Intent("com.getpebble.action.SEND_NOTIFICATION");

        Map<String, String> data = new HashMap<String, String>();
        ValueList values = getValues(msg, NotificationMode.PerMessage);
        data.put("title", values.title);
        data.put("body", values.text);

        String notificationData = new JSONArray().put(new JSONObject(data)).toString();

        pebbleIntent.putExtra("messageType", "PEBBLE_ALERT");
        pebbleIntent.putExtra("sender", "IrssiNotifier");
        pebbleIntent.putExtra("notificationData", notificationData);

        context.sendBroadcast(pebbleIntent);
    }

    public boolean mainActivityOpened(Context context) {
        boolean hadMessages = false;
        if (unread != null) {
            hadMessages = unread.size() > 0;
            unread.clear();
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

        return hadMessages;
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
                        title = "Query from " + msg.getNick();
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
                    title = "Query from " + msg.getNick();
                    text = msg.getMessage();
                } else {
                    title = "Hilight at " + msg.getChannel();
                    text = "(" + msg.getNick() + ") " + msg.getMessage();
                }
                break;

            case PerChannel:
                int channelUnreadCount = getUnreadCountForChannel(msg.getLogicalChannel());
                id = msg.getLogicalChannel().toLowerCase().hashCode();
                count = channelUnreadCount;
                if (msg.isPrivate()) {
                    if (channelUnreadCount <= 1) {
                        title = "Query from " + msg.getNick();
                        text = msg.getMessage();
                    } else {
                        title = "" + channelUnreadCount + " queries from " + msg.getNick();
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
                
                List<IrcMessage> messages = unread.get(msg.getLogicalChannel().toLowerCase());
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
        if (unread == null) {
            return;
        }
        
        NotificationMode mode = NotificationMode.valueOf(intent.getStringExtra("notificationMode"));
        if (mode == NotificationMode.Single) {
            unread.clear();
        } else if (mode == NotificationMode.PerChannel) {
            String channel = intent.getStringExtra("channel");
            if (channel != null) {
                List<IrcMessage> msgs = unread.get(channel.toLowerCase());
                if (msgs != null) {
                    msgs.clear();
                }
            }
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
