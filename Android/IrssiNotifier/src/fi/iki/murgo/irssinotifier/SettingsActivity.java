package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class SettingsActivity extends Activity {
	private static final String TAG = SettingsActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "Opened settings");
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.settings);
	}
	
}
