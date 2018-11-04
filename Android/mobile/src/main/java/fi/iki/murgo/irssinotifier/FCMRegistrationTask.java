
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;

public class FCMRegistrationTask extends BackgroundAsyncTask<Void, Void, Boolean> {
    private static final String TAG = FCMRegistrationTask.class.getName();

    public FCMRegistrationTask(Activity activity, String titleText, String text) {
        super(activity, titleText, text);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Log.i(TAG, "Registering to FCM (not really)");

        try {
            Preferences preferences = new Preferences(activity);
            while (preferences.getGcmRegistrationId() == null) {
                Thread.sleep(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error trying to register to FCM", e);
            return false;
        }
        
        return true;
    }
}
