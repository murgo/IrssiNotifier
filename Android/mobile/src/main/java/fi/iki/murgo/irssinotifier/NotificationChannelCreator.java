package fi.iki.murgo.irssinotifier;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

public class NotificationChannelCreator {
    public static final String CHANNEL_DEFAULT_ID = "IrssiNotifierDefaultChannel";
    public static final String CHANNEL_LOWPRIO_ID = "IrssiNotifierLowPriorityChannel";

    public static void createNotificationChannels(Context ctx) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            {
                CharSequence name = "IrssiNotifier default";
                String description = "Default notifications for Irssi hilights.";
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel channel = new NotificationChannel(CHANNEL_DEFAULT_ID, name, importance);
                channel.setDescription(description);

                Preferences prefs = new Preferences(ctx);
                channel.enableLights(prefs.isLightsEnabled());
                channel.setLightColor(prefs.getCustomLightColor());
                channel.enableVibration(prefs.isVibrationEnabled());

                if (prefs.isSoundEnabled()) {
                    AudioAttributes att = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    channel.setSound(prefs.getNotificationSound(), att);
                } else {
                    channel.setSound(null, null);
                }

                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                NotificationManager notificationManager = ctx.getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }

            {
                CharSequence name = "IrssiNotifier low priority";
                String description = "Repeated hilights, you can set spam filter duration in app settings.";
                int importance = NotificationManager.IMPORTANCE_LOW;
                NotificationChannel channel = new NotificationChannel(CHANNEL_LOWPRIO_ID, name, importance);
                channel.setDescription(description);

                NotificationManager notificationManager = ctx.getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private static void deleteNotificationChannels(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = ctx.getSystemService(NotificationManager.class);
            notificationManager.deleteNotificationChannel(CHANNEL_DEFAULT_ID);
            notificationManager.deleteNotificationChannel(CHANNEL_LOWPRIO_ID);
        }
    }

    public static void recreateNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deleteNotificationChannels(ctx);
            createNotificationChannels(ctx);

            Toast.makeText(ctx, "Notification Channel settings applied", Toast.LENGTH_SHORT).show();
        }
    }

    public static void openNotificationChannelSettings(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_DEFAULT_ID);
            ctx.startActivity(intent);
        }
    }
}
