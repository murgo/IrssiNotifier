package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;

// ACRA! http://code.google.com/p/acra/
// Analytics!

public class IrssiNotifierActivity extends Activity {
	private static final String TAG = IrssiNotifierActivity.class.getSimpleName();
	private Preferences preferences;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.i(TAG, "Startup");
        super.onCreate(savedInstanceState);
        
        try {
			MessageToServer.setVersion(getPackageManager().getPackageInfo(getPackageName(), 0).versionCode);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
        
        preferences = new Preferences(this);
        preferences.clear();
        
        // do initial settings
        if (preferences.getAuthToken() == null || preferences.getRegistrationId() == null) {
        	Log.d(TAG, "Asking for initial settings");
	        Intent i = new Intent(IrssiNotifierActivity.this, InitialSettingsActivity.class);
	        startActivity(i);
	        finish();
	        return;
        }
        
        startMainApp();
    }
	
	private void startMainApp() {
    	Log.d(TAG, "Main startup");
        createUi();

        if (preferences.settingsNeedSending()) {
        	Log.d(TAG, "Settings are not saved, odd");
        	sendSettings();
        }
        
        startFetchingData();
	}
    
	private void startFetchingData() {
		// TODO
	}

	private void createUi() {
		Log.d(TAG, "(Re)creating UI");
        setContentView(R.layout.main);
	}
	
	private void sendSettings() {
		// TODO: Not tested
		SettingsSendingTask task = new SettingsSendingTask(this, "", "Generating authentication token..."); // TODO i18n
		
		final Context ctx = this;
		task.setCallback(new Callback<ServerResponse>() {
			public void doStuff(ServerResponse result) {
				if (result == null || !result.wasSuccesful()) {
					MessageBox.Show(ctx, null, "Unable to register to C2DM! Please try again later!", new Callback<Void>() { // TODO i18n
						public void doStuff(Void param) {
							finish();
						}
					});
				}
				if (result.getMessage() != null || result.getMessage().length() == 0) {
					MessageBox.Show(ctx, "TITLE", result.getMessage(), new Callback<Void>() {
						public void doStuff(Void param) {
						}
					});
				}
			}
		});
		
		task.execute();
	}
	
	/*
	 * Stored here if need arises
	public boolean isNetworkAvailable() {
	    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = cm.getActiveNetworkInfo();
	    // if no network is available networkInfo will be null, otherwise check if we are connected
	    if (networkInfo != null && networkInfo.isConnected()) {
	        return true;
	    }
	    return false;
	}
	*/
}