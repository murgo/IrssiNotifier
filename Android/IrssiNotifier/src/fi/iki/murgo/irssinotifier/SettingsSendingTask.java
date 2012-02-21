package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;

public class SettingsSendingTask extends BackgroundAsyncTask<Void, Void, SettingsServerResponse> {
	
	private static final String TAG = InitialSettingsActivity.class.getSimpleName();
	
	public SettingsSendingTask(Activity activity, String titleText, String text) {
		super(activity, titleText, text);
	}

	@Override
	protected SettingsServerResponse doInBackground(Void... params) {
		Log.d(TAG, "Sending settings");
		Preferences prefs = new Preferences(activity);
		
		try {
			SettingsServerResponse response = prefs.sendSettings();
			return response;
		} catch (Exception e) {
			Log.e(TAG, "Unable to send settings: " + e.toString());
			e.printStackTrace();
		}
		return null;
	}
}
