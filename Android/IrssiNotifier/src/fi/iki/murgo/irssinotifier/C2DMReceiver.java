package fi.iki.murgo.irssinotifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class C2DMReceiver extends BroadcastReceiver {
    private static final String TAG = C2DMReceiver.class.getSimpleName();

    private static final String C2DM_DATA_ACTION = "action";
    private static final String C2DM_DATA_MESSAGE = "message";

    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("com.google.android.c2dm.intent.REGISTRATION")) {
            handleRegistration(context, intent);
        } else if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
            handleMessage(context, intent);
        } else {
            Log.w(TAG, "Unexpected intent: " + intent);
        }
    }

    private void handleRegistration(Context context, Intent intent) {
        String registrationId = intent.getStringExtra("registration_id");
        String error = intent.getStringExtra("error");
        String unregistered = intent.getStringExtra("unregistered");
        
        context.getSharedPreferences(Prefs.PREFERENCES_NAME, Prefs.PREFERENCES_MODE).edit().putString(Prefs.REGISTRATION_ID, registrationId).commit();
        
        Log.i(TAG, "RegistrationId: " + registrationId + " Error: " + error + " Unregistered: " + unregistered);
    }

    private void handleMessage(Context context, Intent intent) {
        Log.d(TAG, "Handling C2DM notification");
        String action = intent.getStringExtra(C2DM_DATA_ACTION);
        String message = intent.getStringExtra(C2DM_DATA_MESSAGE);
        
        // TODO
        Log.d(TAG, "Action: " + action + " Message: " + message);
        Toast.makeText(context, "Message: " + message, Toast.LENGTH_LONG).show();
    }
}