
package fi.iki.murgo.irssinotifier;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class GcmReceiver extends BroadcastReceiver {
    private static final String TAG = GcmReceiver.class.getSimpleName();

    private static final String GCM_DATA_ACTION = "action";
    private static final String GCM_DATA_MESSAGE = "message";

    public static final String NOTIFICATION_CLEARED_INTENT = "fi.iki.murgo.irssinotifier.NOTIFICATION_CLEARED";

    public static final String EMAIL_OF_SENDER = "irssinotifier@gmail.com";
    public static Callback<String[]> callback;

    public static void registerToGcm(Context context) {
        Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
        registrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
        registrationIntent.putExtra("sender", EMAIL_OF_SENDER);
        ComponentName service = context.startService(registrationIntent);
        if (service == null)
            throw new RuntimeException("Service failed to start");
    }

    public static void unregisterFromGcm(Context context) {
        Intent unregistrationIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
        unregistrationIntent.putExtra("app",
                PendingIntent.getBroadcast(context, 0, new Intent(), 0));
        context.startService(unregistrationIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("com.google.android.c2dm.intent.REGISTRATION")) {
            handleRegistration(context, intent);
        } else if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
            handleMessage(context, intent);
        } else if (intent.getAction().equals(NOTIFICATION_CLEARED_INTENT)) {
            IrcNotificationManager manager = IrcNotificationManager.getInstance();
            manager.notificationCleared(context, intent);
        } else {
            Log.w(TAG, "Unexpected intent: " + intent);
        }
    }

    public static void setRegistrationCallback(Callback<String[]> callback) {
        GcmReceiver.callback = callback;
    }

    private void handleRegistration(Context context, Intent intent) {
        String registrationId = intent.getStringExtra("registration_id");
        String error = intent.getStringExtra("error");
        String unregistered = intent.getStringExtra("unregistered");

        Log.i(TAG, "RegistrationId: " + registrationId + " Error: " + error + " Unregistered: "
                + unregistered);

        Preferences preferences = new Preferences(context);

        if (error != null || unregistered != null) {
            preferences.setRegistrationId(null);
        } else {
            preferences.setRegistrationId(registrationId);
        }

        if (callback != null) {
            callback.doStuff(new String[] {
                    registrationId, error, unregistered
            });
        }
    }

    private void handleMessage(Context context, Intent intent) {
        Log.d(TAG, "Handling GCM notification");
        String action = intent.getStringExtra(GCM_DATA_ACTION);
        String message = intent.getStringExtra(GCM_DATA_MESSAGE);
        Log.d(TAG, "Action: " + action + " Message: " + message);

        IrcNotificationManager manager = IrcNotificationManager.getInstance();
        manager.handle(context, message);
    }
}
