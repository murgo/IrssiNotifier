package fi.iki.murgo.irssinotifier;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class FancyFcmListenerService extends FirebaseMessagingService {
    private static final String TAG = FancyFcmListenerService.class.getName();

    private static final String DATA_ACTION = "action";
    private static final String DATA_MESSAGE = "message";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Handling FCM notification");

        Map<String, String> data = remoteMessage.getData();
        String action = data.get(DATA_ACTION);
        String message = data.get(DATA_MESSAGE);

        Log.d(TAG, "Action: " + action + " Message: " + message);

        // peek into payload to see if it's a message or a command
        IrcMessage msg;
        try {
            JSONObject payload = new JSONObject(message);
            if (payload.has("command")) {
                CommandManager manager = CommandManager.getInstance();
                manager.handle(this, payload.getString("command"));
                return;
            }

            msg = new IrcMessage();
            msg.deserialize(payload);
        } catch (JSONException e) {
            // malformed payload, probably server error or something, who cares
            return;
        }

        IrcNotificationManager manager = IrcNotificationManager.getInstance();
        manager.handle(this, msg);
    }

    @Override
    public void onNewToken(String registrationId) {
        Log.i(TAG, "Registered to FCM with registrationId (Unused): " + registrationId);
    }

    @Override
    public void onSendError(String errorId, Exception e) {
        Log.e(TAG, "Error while registering to FCM: " + errorId, e);
    }

    /*
    @Override
    protected void onUnregistered(Context context, String regId) {
        Log.w(TAG, "Unregistered from GCM: " + regId);

        Preferences preferences = new Preferences(context);
        preferences.setGcmRegistrationId(null);

        if (callback != null) {
            callback.doStuff(false);
        }
    }
    */
}
