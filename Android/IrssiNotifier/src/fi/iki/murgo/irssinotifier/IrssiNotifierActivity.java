package fi.iki.murgo.irssinotifier;

import java.util.Date;
import java.util.List;

import org.apache.http.auth.AuthenticationException;

import com.actionbarsherlock.app.SherlockActivity;
import com.viewpagerindicator.TitlePageIndicator;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;

public class IrssiNotifierActivity extends SherlockActivity {
	private static final String TAG = IrssiNotifierActivity.class.getSimpleName();
	private Preferences preferences;
	//private final String googleAnalyticsCode = "UA-29385499-1";
	private MessagePagerAdapter adapter; 
    private ViewPager pager;
	private boolean progressBarVisibility;
	private static IrssiNotifierActivity instance;
	private static boolean needsRefresh;
	private String channelToView;
	
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
			Intent i = new Intent(this, InitialSettingsActivity.class);
			startActivity(i);
			finish();
			return;
		}
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setIndeterminateProgressBarVisibility(false);
        
    	Intent i = getIntent();
    	if (i != null) {
    		String intentChannelToView = i.getStringExtra("Channel");
    		if (intentChannelToView != null)
    			channelToView = intentChannelToView;
    	}
        
        boolean b = false;
        if (savedInstanceState != null) {
        	b = savedInstanceState.getBoolean("rotated", false);
        	channelToView = savedInstanceState.getString("channelToView");
        }
        
        IrcNotificationManager.getInstance().mainActivityOpened(this);
        startMainApp(b);
    }
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("rotated", true);
		outState.putString("channelToView", channelToView);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		instance = null;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		instance = this;
		
		if (needsRefresh) {
			needsRefresh = false;
			restart();
		}
	}
	
    public void restart() {
	    Intent intent = getIntent();
	    overridePendingTransition(0, 0);
	    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
	    finish();
	
	    overridePendingTransition(0, 0);
	    startActivity(intent);
	}
	
	@Override
	protected void onUserLeaveHint() {
		super.onUserLeaveHint();
		
		DataAccess da = new DataAccess(this);
		da.setAllMessagesAsShown();
	}
	
	public static IrssiNotifierActivity getForegroundInstance() {
		return instance;
	}
	
	private void startMainApp(boolean orientationChanged) {
    	Log.d(TAG, "Main startup");
        createUi(null);
        
        if (preferences.settingsNeedSending()) {
        	Log.d(TAG, "Settings are not saved, odd");
        	sendSettings();
        }
        
        final Activity ctx = this;
        
        final Callback<List<Channel>> dataAccessCallback = new Callback<List<Channel>>() {
			public void doStuff(List<Channel> param) {
				createUi(param);
			}
		};
        
		final long now = new Date().getTime();
		DataFetcherTask task = new DataFetcherTask(preferences.getAuthToken(), preferences.getEncryptionPassword(), preferences.getLastFetchTime(), new Callback<DataFetchResult>() {
			// TODO: Move this into its own activity, so orientation changes work correctly
			public void doStuff(DataFetchResult param) {
		        setIndeterminateProgressBarVisibility(false);
				if (param.getException() != null) {
					if (param.getException() instanceof AuthenticationException) {
						MessageBox.Show(ctx, "Authentication error", "Unable to authenticate to server", null);
					} else if (param.getException() instanceof ServerException) {
						MessageBox.Show(ctx, "Server error", "Mystical server error, check if updates are available", null);
					} else if (param.getException() instanceof CryptoException) {
							MessageBox.Show(ctx, "Decryption error", "Unable to decrypt message, is your decryption password correct?", null);
					} else {
						MessageBox.Show(ctx, "Error", "What happen", null);
					}
					return;
				}
				
				preferences.setLastFetchTime(now);
				
				if (param.getResponse().getServerMessage() != null && param.getResponse().getServerMessage().length() > 0) {
					MessageBox.Show(ctx, null, param.getResponse().getServerMessage(), null, true);
				}

				if (param.getMessages().isEmpty()) {
					return;
				}
				
				DataAccessTask task = new DataAccessTask(ctx, dataAccessCallback);
				task.execute(param.getMessages().toArray(new IrcMessage[0]));
			}
		});

		if (!orientationChanged) {
	        setIndeterminateProgressBarVisibility(true);
	
	        task.execute();
		}
        
		DataAccessTask datask = new DataAccessTask(ctx, dataAccessCallback);
		datask.execute();
	}
    
	private void createUi(final List<Channel> channels) {
        setContentView(R.layout.main);
        setIndeterminateProgressBarVisibility(!progressBarVisibility); // häx häx
        setIndeterminateProgressBarVisibility(!progressBarVisibility);
        
		pager = (ViewPager) findViewById(R.id.pager);

		if (adapter == null) {
        	adapter = new MessagePagerAdapter(this, getLayoutInflater());
        }
		if (channels != null) {
			adapter.setChannels(channels);
		}
        pager.setAdapter(adapter);
        
        TitlePageIndicator titleIndicator = (TitlePageIndicator)findViewById(R.id.titles);
        titleIndicator.setViewPager(pager);
        titleIndicator.setOnPageChangeListener(new OnPageChangeListener() {
			public void onPageSelected(int arg0) {
				channelToView = channels.get(arg0).getName();
			}
			
			public void onPageScrolled(int arg0, float arg1, int arg2) { }
			public void onPageScrollStateChanged(int arg0) { }
		});

        if (channelToView != null && channels != null && channels.size() > 1) {
        	for (int i = 0; i < channels.size(); i++) {
    			if (channels.get(i).getName().equals(channelToView)) {
    	    		pager.setCurrentItem(i);
    	    		break;
    			}
        	}
    	}
	}
	
	private void setIndeterminateProgressBarVisibility(boolean state) {
        setSupportProgressBarIndeterminateVisibility(state);
        progressBarVisibility = state;
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
		super.onCreateOptionsMenu(menu);
		getSupportMenuInflater().inflate(R.menu.mainmenu, menu);
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

	public void newMessage(IrcMessage msg) {
		channelToView = msg.getLogicalChannel();
        startMainApp(false);
	}

	public static void needsRefresh() {
		needsRefresh = true;
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
