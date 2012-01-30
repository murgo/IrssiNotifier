package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.util.Log;

public class C2DMRegistrationTask extends BackgroundAsyncTask<Void, Void, String[]> {
	private static final String TAG = InitialSettingsActivity.class.getSimpleName();

	public C2DMRegistrationTask(Activity activity, String titleText, String text) {
		super(activity, titleText, text);
	}

	@Override
	protected String[] doInBackground(Void... params) {
		Log.d(TAG, "Registering to C2DM");
		
		C2DMReceiver.setRegistrationCallback(getCallback());
		C2DMReceiver.registerToC2DM(activity);
		return null;
	}
	
    @Override
    protected void onPostExecute(String[] result) {
    	// empty method to prevent dialog closing on base class
    }
}
