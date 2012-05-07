package fi.iki.murgo.irssinotifier;

import org.acra.ErrorReporter;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class C2DMReceiver extends BroadcastReceiver {
    private static final String TAG = C2DMReceiver.class.getSimpleName();

    private static final String C2DM_DATA_ACTION = "action";
    private static final String C2DM_DATA_MESSAGE = "message";
    
    public static final String NOTIFICATION_CLEARED_INTENT = "fi.iki.murgo.irssinotifier.NOTIFICATION_CLEARED";

    public static final String EMAIL_OF_SENDER = "irssinotifier@gmail.com";
	public static Callback<String[]> callback;
    
    public static void registerToC2DM(Context context) {
        Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
        registrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
        registrationIntent.putExtra("sender", EMAIL_OF_SENDER);
        ComponentName service = context.startService(registrationIntent);
        if (service == null) throw new RuntimeException("Service failed to start");
    }
    
    public static void unregisterFromC2DM(Context context) {
        Intent unregistrationIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
        unregistrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
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
		C2DMReceiver.callback = callback;
    }

    private void handleRegistration(Context context, Intent intent) {
        String registrationId = intent.getStringExtra("registration_id");
        String error = intent.getStringExtra("error");
        String unregistered = intent.getStringExtra("unregistered");

        Log.i(TAG, "RegistrationId: " + registrationId + " Error: " + error + " Unregistered: " + unregistered);
        
    	Preferences preferences = new Preferences(context);
    	
        if (error != null || unregistered != null) {
        	preferences.setRegistrationId(null);
        	ErrorReporter.getInstance().handleSilentException(new Exception("Error while registering to c2dm. Error: " + error + " Unregistered: " + unregistered));
        } else {
        	preferences.setRegistrationId(registrationId);
        }
        
    	if (callback != null) {
    		callback.doStuff(new String[] {registrationId, error, unregistered});
    	}
    }

    private void handleMessage(Context context, Intent intent) {
        Log.d(TAG, "Handling C2DM notification");
        String action = intent.getStringExtra(C2DM_DATA_ACTION);
        String message = intent.getStringExtra(C2DM_DATA_MESSAGE);
        Log.d(TAG, "Action: " + action + " Message: " + message);
        
        IrcNotificationManager manager = IrcNotificationManager.getInstance();
        manager.handle(context, message);
    }
}
