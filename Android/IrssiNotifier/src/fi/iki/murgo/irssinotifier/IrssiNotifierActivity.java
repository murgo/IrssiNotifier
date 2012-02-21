package fi.iki.murgo.irssinotifier;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.http.auth.AuthenticationException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

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
        
        // do initial settings
        if (preferences.getAuthToken() == null || preferences.getRegistrationId() == null) {
			Log.d(TAG, "Asking for initial settings");
			preferences.clear();
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
        
        final Context ctx = this;
        final TextView textView = (TextView)findViewById(R.id.mainView);
        
        final Callback<Map<Channel, List<IrcMessage>>> dataAccessCallback = new Callback<Map<Channel,List<IrcMessage>>>() {
			public void doStuff(Map<Channel, List<IrcMessage>> param) {
				StringBuilder sb = new StringBuilder();
				for (Channel channel : param.keySet()) {
					sb.append(channel.getName());
					sb.append("\n");
					for (IrcMessage message : param.get(channel)) {
						sb.append(message.getMessage());
						sb.append("\n");
					}
					sb.append("\n");
				}
				
				textView.setText(sb.toString());
			}
		};
        
		final long now = new Date().getTime();
        DataFetcherTask task = new DataFetcherTask(preferences.getAuthToken(), preferences.getEncryptionPassword(), preferences.getLastFetchTime(), new Callback<DataFetchResult>() {
			public void doStuff(DataFetchResult param) {
				if (param.getException() != null) {
					if (param.getException() instanceof AuthenticationException) {
						MessageBox.Show(ctx, "Authentication error", "Unable to authenticate to server", null);
					} else if (param.getException() instanceof ServerException) {
						MessageBox.Show(ctx, "Server error", "Mystical server error, check if updates are available", null);
					} else {
						MessageBox.Show(ctx, "Error", "What happen", null);
					}
					preferences.clear(); // TODO: Probably not the best way to deal with it
					return;
				}
				
				preferences.setLastFetchTime(now);
				
				if (param.getMessages().isEmpty())
					return;
				
				DataAccessTask task = new DataAccessTask(ctx, dataAccessCallback);
				task.execute(param.getMessages().toArray(new IrcMessage[0]));
			}
		});
        task.execute();
        
		DataAccessTask datask = new DataAccessTask(ctx, dataAccessCallback);
		datask.execute();
	}
    
	private void createUi() {
		Log.d(TAG, "(Re)creating UI");
        setContentView(R.layout.main);
	}
	
	private void sendSettings() {
		// TODO: Not tested
		SettingsSendingTask task = new SettingsSendingTask(this, "", "Generating authentication token..."); // TODO i18n
		
		final Context ctx = this;
		task.setCallback(new Callback<SettingsServerResponse>() {
			public void doStuff(SettingsServerResponse result) {
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