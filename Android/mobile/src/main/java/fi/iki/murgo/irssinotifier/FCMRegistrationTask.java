
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;

import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import com.google.firebase.messaging.FirebaseMessaging;

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

        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }

                // Get new FCM registration token
                String token = task.getResult();

                preferences.setGcmRegistrationId(token);
            });

        return true;
    }
}
