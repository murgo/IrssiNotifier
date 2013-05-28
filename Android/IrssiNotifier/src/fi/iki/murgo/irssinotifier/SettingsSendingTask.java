
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;
import org.apache.http.auth.AuthenticationException;

public class SettingsSendingTask extends BackgroundAsyncTask<Void, Void, ServerResponse> {

    private static final String TAG = SettingsSendingTask.class.getName();

    public SettingsSendingTask(Activity activity, String titleText, String text) {
        super(activity, titleText, text);
    }

    @Override
    protected ServerResponse doInBackground(Void... params) {
        Log.d(TAG, "Sending settings");

        try {
            Server server = new Server(activity);
            boolean authenticated = server.authenticate();
            if (!authenticated) {
                Log.e(TAG, "Unable to authenticate to server");
                return new ServerResponse(new AuthenticationException());
            }

            Preferences prefs = new Preferences(activity);
            return prefs.sendSettings(server);
        } catch (Exception e) {
            Log.e(TAG, "Unable to send settings: " + e.toString());
            e.printStackTrace();
            return new ServerResponse(e);
        }
    }
}
