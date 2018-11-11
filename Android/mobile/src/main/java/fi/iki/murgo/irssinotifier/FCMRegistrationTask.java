
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

import java.io.IOException;

public class FCMRegistrationTask extends BackgroundAsyncTask<Void, Void, Boolean> {
    private static final String TAG = FCMRegistrationTask.class.getName();
    public static final String SENDER_ID = "710677821747";

    public FCMRegistrationTask(Activity activity, String titleText, String text) {
        super(activity, titleText, text);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Log.i(TAG, "Registering to FCM");

        Preferences preferences = new Preferences(activity);

        try {
            String token = FirebaseInstanceId.getInstance().getToken(SENDER_ID, "FCM");
            preferences.setGcmRegistrationId(token);
        } catch (Exception e) {
            Log.e(TAG, "Error trying to register to FCM", e);
            return false;
        }
        
        return true;
    }
}
