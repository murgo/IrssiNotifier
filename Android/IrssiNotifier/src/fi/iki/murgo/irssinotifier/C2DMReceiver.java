package fi.iki.murgo.irssinotifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class C2DMReceiver extends BroadcastReceiver {
    private static final String TAG = C2DMReceiver.class.getSimpleName();

    private static final String C2DM_DATA_ACTION = "action";

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
        
        Log.d(TAG, "RegistrationId: " + registrationId + " Error: " + error + " Unregistered: " + unregistered);
        Toast.makeText(context, "RegistrationId: " + registrationId + " Error: " + error + " Unregistered: " + unregistered, Toast.LENGTH_LONG).show();
        //Store the Registration ID
    }

    private void handleMessage(Context context, Intent intent) {
        Log.d(TAG, "Handling C2DM notification");
        String action = intent.getStringExtra(C2DM_DATA_ACTION);
        
        // Do something in the program to let the User know the notification has been received.
        Log.d(TAG, "Action: " + action);
        Toast.makeText(context, "Action: " + action, Toast.LENGTH_LONG).show();
    }
}