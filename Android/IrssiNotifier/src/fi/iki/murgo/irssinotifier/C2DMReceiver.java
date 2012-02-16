package fi.iki.murgo.irssinotifier;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class C2DMReceiver extends BroadcastReceiver {
    private static final String TAG = C2DMReceiver.class.getSimpleName();

    private static final String C2DM_DATA_ACTION = "action";
    private static final String C2DM_DATA_MESSAGE = "message";

    public static final String EMAIL_OF_SENDER = "irssinotifier@gmail.com";

	private static Callback<String[]> callback;

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
        String unregistered = intent.getStringExtra("unregistered"); // TODO

        Log.i(TAG, "RegistrationId: " + registrationId + " Error: " + error + " Unregistered: " + unregistered);
        
    	Preferences preferences = new Preferences(context);

        if (error != null || unregistered != null) {
        	preferences.setRegistrationId(null);
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
        
        // TODO
        Log.d(TAG, "Action: " + action + " Message: " + message);
        Toast.makeText(context, "Message: " + message, Toast.LENGTH_LONG).show();
        
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

        IrcMessage msg = new IrcMessage();
        msg.Deserialize(message);
        
        long when = System.currentTimeMillis();

        Notification notification = new Notification(R.drawable.ic_launcher, msg.getMessage(), when);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.defaults |= Notification.DEFAULT_SOUND;
		
        Intent toLaunch = new Intent(context, IrssiNotifierActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, toLaunch, 0);
        notification.setLatestEventInfo(context, "IrssiNotifier", msg.getMessage(), contentIntent);
        notificationManager.notify(666, notification);
    }
}