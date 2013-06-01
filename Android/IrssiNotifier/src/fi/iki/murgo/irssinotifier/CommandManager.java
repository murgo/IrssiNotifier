package fi.iki.murgo.irssinotifier;

import android.util.Log;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

public class CommandManager {
    private static final String TAG = CommandManager.class.getName();
    private static CommandManager instance;

    public static CommandManager getInstance() {
        if (instance == null) {
            instance = new CommandManager();
        }
        return instance;
    }

    private CommandManager() {
    }

    public void handle(Context context, String encryptedCommand) {
        Preferences prefs = new Preferences(context);

        String command;

        try {
            command = Crypto.decrypt(prefs.getEncryptionPassword(), encryptedCommand);
        } catch (CryptoException e) {
            final int color = prefs.getCustomLightColor();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setSmallIcon(R.drawable.notification_icon);
            builder.setTicker(context.getString(R.string.decryption_error_ticker));
            builder.setAutoCancel(true);
            builder.setOngoing(false);
            builder.setContentText(context.getString(R.string.decryption_error));
            builder.setContentTitle(context.getString(R.string.decryption_error_title));
            builder.setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, IrssiNotifierActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
            builder.setLights(color, 300, 5000);

            final Notification notification = builder.build();
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(666, notification);
            return;
        }

        if (command.equals("clearNotifications")) {
            Log.d(TAG, "Sending clear unread intent");
            Intent deleteIntent = new Intent(NotificationClearedReceiver.NOTIFICATION_CLEARED_INTENT);
            deleteIntent.putExtra("notificationMode", "Single");
            context.sendBroadcast(deleteIntent);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
        } else {
            Log.d(TAG, "Received unknown command '" + command + "'");
        }

    }

}
