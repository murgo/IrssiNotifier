package fi.iki.murgo.irssinotifier;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.android.gcm.GCMRegistrar;

import org.json.JSONException;
import org.json.JSONObject;

public class GCMIntentService extends GCMBaseIntentService {

    private static final String GCM_DATA_ACTION = "action";
    private static final String GCM_DATA_MESSAGE = "message";
    
    private static final String SENDER_ID = "710677821747";

    public static Callback<Boolean> callback;

    public GCMIntentService() {
        super(SENDER_ID);
    }

    public static void setRegistrationCallback(Callback<Boolean> callback) {
        GCMIntentService.callback = callback;
    }

    public static void registerToGcm(Context context) {
        GCMRegistrar.checkDevice(context); // throws exception if notifications cannot be received
        GCMRegistrar.register(context, SENDER_ID);
    }

    @Override
    protected void onMessage(Context context, Intent intent) {
        Log.d(TAG, "Handling GCM notification");

        String action = intent.getStringExtra(GCM_DATA_ACTION);
        String message = intent.getStringExtra(GCM_DATA_MESSAGE);
        Log.d(TAG, "Action: " + action + " Message: " + message);

        // peek into payload to see if it's a message or a command
        IrcMessage msg;
        try {
            JSONObject payload = new JSONObject(message);
            if (payload.has("command")) {
                CommandManager manager = CommandManager.getInstance();
                manager.handle(context, payload.getString("command"));
                return;
            }

            msg = new IrcMessage();
            msg.deserialize(payload);
        } catch (JSONException e) {
            // malformed payload, probably server error or something, who cares
            return;
        }

        IrcNotificationManager manager = IrcNotificationManager.getInstance();
        manager.handle(context, msg);
    }

    @Override
    protected void onRegistered(Context context, String registrationId) {
        Log.i(TAG, "Registered to GCM with registrationId: " + registrationId);

        Preferences preferences = new Preferences(context);
        preferences.setGcmRegistrationId(registrationId);

        if (callback != null) {
            callback.doStuff(true);
        }
   }

    @Override
    protected void onError(Context context, String errorId) {
        Log.e(TAG, "Error while registering to GCM: " + errorId);
        
        Preferences preferences = new Preferences(context);
        preferences.setGcmRegistrationId(null);

        if (callback != null) {
            callback.doStuff(false);
        }
    }

    @Override
    protected void onUnregistered(Context context, String regId) {
        Log.w(TAG, "Unregistered from GCM: " + regId);
        
        Preferences preferences = new Preferences(context);
        preferences.setGcmRegistrationId(null);

        if (callback != null) {
            callback.doStuff(false);
        }
    }
}
