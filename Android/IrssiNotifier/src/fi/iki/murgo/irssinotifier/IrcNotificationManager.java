
package fi.iki.murgo.irssinotifier;

import java.util.Date;
import java.util.HashMap;
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

    private Map<String, Integer> unreadCounts = new HashMap<String, Integer>();
    private int perMessageNotificationId = 2;
    private long lastSoundDate = 0;
    private DataAccess da;

    private int getUnreadCount() {
        int total = 0;
        for (int i : unreadCounts.values()) {
            total += i;
        }
        return total;
    }

    public int getUnreadCountForChannel(String channel) {
        if (!unreadCounts.containsKey(channel))
            return 0;

        return unreadCounts.get(channel);
    }

    private void addUnread(IrcMessage msg) {
        String key = msg.isPrivate() ? msg.getNick() : msg.getChannel();

        if (unreadCounts.containsKey(key))
            unreadCounts.put(key, unreadCounts.get(key) + 1);
        else
            unreadCounts.put(key, 1);
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

        try {
            msg.Deserialize(message);
            msg.Decrypt(prefs.getEncryptionPassword());

            if (da == null)
                da = new DataAccess(context);
            da.handleMessage(msg);

            Object[] values = getValues(msg, mode);
            notificationMessage = (String) values[0];
            titleText = (String) values[1];
            notificationId = (Integer) values[2];
            when = msg.getServerTimestamp().getTime();
            currentUnreadCount = (Integer) values[3];

            /*
             * if (getUnreadCount() <= 1) { tickerText = "New IRC message"; }
             * else { tickerText = "" + getUnreadCount() + " new IRC messages";
             * }
             */
            tickerText = titleText;

            addUnread(msg);
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

        /*
         * // Stupid piece of shit always returns true boolean foreground =
         * false; try { // foreground = new
         * ForegroundCheckTask().execute(context).get(); } catch (Exception e) {
         * e.printStackTrace(); }
         */

        IrssiNotifierActivity foregroundInstance = IrssiNotifierActivity.getForegroundInstance();
        if (foregroundInstance != null) {
            foregroundInstance.newMessage(msg);
            unreadCounts = new HashMap<String, Integer>();
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
        builder.setNumber(currentUnreadCount);
        
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
        
        Notification notification = builder.build();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);
    }

    public void mainActivityOpened(Context context) {
        unreadCounts = new HashMap<String, Integer>();
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    private Object[] getValues(IrcMessage msg, NotificationMode mode) {
        String text = null;
        String title = null;
        int id = 0;
        int count = 0;

        int unreadCount = getUnreadCount() + 1; // this is called before adding latest

        switch (mode) {
            case Single:
                id = 1;
                count = unreadCount;
                if (msg.isPrivate()) {
                    if (unreadCount <= 1) {
                        title = "Private message from " + msg.getNick();
                        text = msg.getMessage();
                    } else {
                        title = "" + unreadCount + " new hilights";
                        text = "Last: " + msg.getLogicalChannel() + " (" + msg.getNick() + ") "
                                + msg.getMessage();
                    }
                } else {
                    if (unreadCount <= 1) {
                        title = "Hilight at " + msg.getChannel();
                        text = "(" + msg.getNick() + ") " + msg.getMessage();
                    } else {
                        title = "" + unreadCount + " new hilights";
                        text = "Last: " + msg.getLogicalChannel() + " (" + msg.getNick() + ") "
                                + msg.getMessage();
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
                int channelUnreadCount = getUnreadCountForChannel(msg.getLogicalChannel()) + 1;
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
                        title = "" + unreadCount + " new hilights at " + msg.getChannel();
                        text = "Last: (" + msg.getNick() + ") " + msg.getMessage();
                    }
                }
                break;
        }

        return new Object[] {
                text, title, id, count
        };
    }

    public void notificationCleared(Context context, Intent intent) {
        NotificationMode mode = NotificationMode.valueOf(intent.getStringExtra("notificationMode"));

        if (mode == NotificationMode.Single) {
            unreadCounts.clear();
        } else if (mode == NotificationMode.PerChannel) {
            String channel = intent.getStringExtra("channel");
            unreadCounts.put(channel, 0);
        }
    }
}
