package fi.iki.murgo.irssinotifier;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class NotificationChannelCreator {
    public static final String CHANNEL_ID = "IrssiNotifierDefaultChannel";

    public static void createNotificationChannel(Context ctx) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "IrssiNotifier default";
            String description = "Default notifications from Irssi hilights.";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            Preferences prefs = new Preferences(ctx);
            channel.enableLights(prefs.isLightsEnabled());
            channel.setLightColor(prefs.getCustomLightColor());
            channel.enableVibration(prefs.isVibrationEnabled());

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = ctx.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private static void deleteNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = ctx.getSystemService(NotificationManager.class);
            notificationManager.deleteNotificationChannel(CHANNEL_ID);
        }
    }

    public static void recreateNotificationChannel(Context ctx) {
        deleteNotificationChannel(ctx);
        createNotificationChannel(ctx);
    }
}
