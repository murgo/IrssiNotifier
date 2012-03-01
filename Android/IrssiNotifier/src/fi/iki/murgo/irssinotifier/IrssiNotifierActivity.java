package fi.iki.murgo.irssinotifier;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.auth.AuthenticationException;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

// TODO: ACRA! http://code.google.com/p/acra/

public class IrssiNotifierActivity extends Activity {
	private static final String TAG = IrssiNotifierActivity.class.getSimpleName();
	private Preferences preferences;
	private GoogleAnalyticsTracker tracker;
	private final String googleAnalyticsCode = "UA-29385499-1";
	private MessagePagerAdapter adapter; 
    private ViewPager pager;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.i(TAG, "Startup");
        super.onCreate(savedInstanceState);
        
        tracker = GoogleAnalyticsTracker.getInstance();
        tracker.startNewSession(googleAnalyticsCode, this);
        
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
			tracker.dispatch();
			finish();
			return;
		}
        
        startMainApp();
    }
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		tracker.stopSession();
	}
	
	private void startMainApp() {
    	Log.d(TAG, "Main startup");
        createUi(new HashMap<Channel, List<IrcMessage>>());
        
        if (preferences.settingsNeedSending()) {
        	Log.d(TAG, "Settings are not saved, odd");
        	sendSettings();
        }
        
        final Activity ctx = this;
        
        final Callback<Map<Channel, List<IrcMessage>>> dataAccessCallback = new Callback<Map<Channel,List<IrcMessage>>>() {
			public void doStuff(Map<Channel, List<IrcMessage>> param) {
				createUi(param);
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
					return;
				}
				
				preferences.setLastFetchTime(now);
				
				if (param.getResponse().getServerMessage() != null && param.getResponse().getServerMessage().length() > 0) {
					MessageBox.Show(ctx, null, param.getResponse().getServerMessage(), null);
				}

				if (param.getMessages().isEmpty()) {
					setProgressBarVisibility(View.GONE);
					return;
				}
				
				DataAccessTask task = new DataAccessTask(ctx, dataAccessCallback);
				task.execute(param.getMessages().toArray(new IrcMessage[0]));
			}
		});

        setProgressBarVisibility(View.VISIBLE);

        //task.execute();
        tracker.dispatch();
        
		DataAccessTask datask = new DataAccessTask(ctx, dataAccessCallback);
		datask.execute();
	}
    
	private void setProgressBarVisibility(int visibility) {
		ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar1);
		if (pb == null)
			return;
		
		pb.setVisibility(visibility);
		pb.bringToFront();
	}

	private void createUi(Map<Channel, List<IrcMessage>> param) {
        setContentView(R.layout.main);
        
        if (adapter == null)
        	adapter = new MessagePagerAdapter(this, getLayoutInflater());
    	adapter.setIrcMessages(param);
        
		pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(adapter);
	}
	
	private void sendSettings() {
		SettingsSendingTask task = new SettingsSendingTask(this, "", "Generating authentication token...");
		
		final Context ctx = this;
		task.setCallback(new Callback<ServerResponse>() {
			public void doStuff(ServerResponse result) {
				if (result == null || !result.wasSuccesful()) {
					MessageBox.Show(ctx, null, "Unable to register to C2DM! Please try again later!", new Callback<Void>() {
						public void doStuff(Void param) {
							finish();
						}
					});
				}
			}
		});
		
		task.execute();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainmenu, menu);
		
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.settings) {
            Intent settingsActivity = new Intent(getBaseContext(), SettingsActivity.class);
            startActivity(settingsActivity);
			return true;
		}
		
		return super.onOptionsItemSelected(item);
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
