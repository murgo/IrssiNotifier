
package fi.iki.murgo.irssinotifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NotificationClearedReceiver extends BroadcastReceiver {
    private static final String TAG = NotificationClearedReceiver.class.getName();

    public static final String NOTIFICATION_CLEARED_INTENT = "fi.iki.murgo.irssinotifier.NOTIFICATION_CLEARED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(NOTIFICATION_CLEARED_INTENT)) {
            IrcNotificationManager manager = IrcNotificationManager.getInstance();

            if (manager != null) {
                manager.notificationCleared(context, intent);
            }
        } else {
            Log.w(TAG, "Unexpected intent: " + intent);
        }
    }

}
