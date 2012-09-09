
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;

public class GcmRegistrationTask extends BackgroundAsyncTask<Void, Void, String[]> {
    private static final String TAG = GcmRegistrationTask.class.getSimpleName();

    public GcmRegistrationTask(Activity activity, String titleText, String text) {
        super(activity, titleText, text);
    }

    @Override
    protected String[] doInBackground(Void... params) {
        Log.d(TAG, "Registering to C2DM");

        GcmReceiver.setRegistrationCallback(getCallback());
        GcmReceiver.registerToC2DM(activity);
        return null;
    }

    @Override
    protected void onPostExecute(String[] result) {
        // empty method to prevent dialog closing on base class
    }
}
