
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;

public class GCMRegistrationTask extends BackgroundAsyncTask<Void, Void, Boolean> {
    private static final String TAG = GCMRegistrationTask.class.getName();

    public GCMRegistrationTask(Activity activity, String titleText, String text) {
        super(activity, titleText, text);
    }

    private boolean done = false;
    private boolean success;
    
    @Override
    protected Boolean doInBackground(Void... params) {
        Log.i(TAG, "Registering to GCM");
        done = false;
        
        Callback<Boolean> registrationCallback = new Callback<Boolean>() {
            @Override
            public void doStuff(Boolean param) {
                done = true;
                success = param;
            }
        };

        try {
            GCMIntentService.setRegistrationCallback(registrationCallback);
            GCMIntentService.registerToGcm(activity);
            
            while (!done) {
                Thread.sleep(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error trying to register to GCM", e);
            return false;
        }
        
        return success;
    }
}
