package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

// TODO: Currently unused
public class FCMUnregistrationTask extends BackgroundAsyncTask<Void, Void, Boolean> {
    private static final String TAG = FCMUnregistrationTask.class.getName();

    public FCMUnregistrationTask(Activity activity, String titleText, String text) {
        super(activity, titleText, text);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Log.i(TAG, "Unregistering to FCM");

        Preferences preferences = new Preferences(activity);

        try {
            FirebaseInstanceId.getInstance().deleteToken(FCMRegistrationTask.SENDER_ID, "FCM");
            FirebaseInstanceId.getInstance().deleteInstanceId();
            preferences.setGcmRegistrationId(null);
        } catch (Exception e) {
            Log.e(TAG, "Error trying to unregister to FCM", e);
            return false;
        }

        return true;
    }
}
