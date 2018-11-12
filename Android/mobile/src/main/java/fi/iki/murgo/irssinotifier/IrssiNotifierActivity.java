
package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Vibrator;
import org.apache.http.auth.AuthenticationException;

import com.viewpagerindicator.TitlePageIndicator;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

public class IrssiNotifierActivity extends AppCompatActivity {
    public static final String FEED = "------------------------FEED";

    private static final String TAG = IrssiNotifierActivity.class.getName();
    private Preferences preferences;
    // private final String googleAnalyticsCode = "UA-29385499-1";
    private MessagePagerAdapter adapter;
    private boolean progressBarVisibility;
    private static IrssiNotifierActivity instance;
    private static boolean needsRefresh;
    private String channelToView;
    private List<Channel> channels;
    private final Object channelsLock = new Object();
    private int backgroundOperations = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Startup");
        super.onCreate(savedInstanceState);
        
        preferences = new Preferences(this);

        int versionCode = 0;
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        
        if (preferences.isThemeDisabled()) {
            setTheme(R.style.Theme_LameIrssiTheme);
        }

        MessageToServer.setVersion(versionCode);
        Preferences.setVersion(versionCode);

        // do initial settings
        if (preferences.getAccountName() == null || preferences.getGcmRegistrationId() == null || preferences.getGcmRegistrationIdVersion() != versionCode || (LicenseHelper.isPlusVersion(this) && preferences.getLicenseCount() == 0)) {
            Log.d(TAG, "Asking for initial settings");
            Intent i = new Intent(this, InitialSettingsActivity.class);
            startActivity(i);
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setIndeterminateProgressBarVisibility(false);

        // set action Bar icon & Text. Apparently this is no longer a good practice but I'm too lazy to do UI redesign now.
        // See https://stackoverflow.com/questions/26838730/the-application-icon-does-not-show-on-action-bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO);
            actionBar.setLogo(R.mipmap.ic_actionbar);
            //actionBar.setDisplayUseLogoEnabled(true);
            //actionBar.setDisplayShowTitleEnabled(true);
            //actionBar.setDisplayShowHomeEnabled(true);
        }

        Intent i = getIntent();
        if (i != null) {
            String intentChannelToView = i.getStringExtra("Channel");
            if (intentChannelToView != null && !preferences.isFeedViewDefault())
                channelToView = intentChannelToView;
        }

        boolean b = false;
        if (savedInstanceState != null) {
            b = savedInstanceState.getBoolean("rotated", false);
            channelToView = savedInstanceState.getString("channelToView");
        }

        NotificationChannelCreator.createNotificationChannels(this);

        IrcNotificationManager.getInstance().mainActivityOpened(this);
        startMainApp(b);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String intentChannelToView = intent.getStringExtra("Channel");
        if (intentChannelToView != null && !preferences.isFeedViewDefault())
            channelToView = intentChannelToView;

        IrcNotificationManager.getInstance().mainActivityOpened(this);
        startMainApp(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("rotated", true);
        outState.putString("channelToView", channelToView);
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

        boolean hadMessages = IrcNotificationManager.getInstance().mainActivityOpened(this);

        if (hadMessages || needsRefresh) {
            Log.v(TAG, "onResume needs refreshing");
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

    public static void refreshIsNeeded() {
        Log.v(TAG, "Refreshing would be refreshing");
        needsRefresh = true;
    }

    public static IrssiNotifierActivity getForegroundInstance() {
        return instance;
    }

    private void startMainApp(final boolean uptodate) {
        Log.d(TAG, "Main startup");

        if (preferences.settingsNeedSending()) {
            Log.d(TAG, "Settings are not saved, odd");
            sendSettings();
        }

        final long now = new Date().getTime();

        final Callback<List<Channel>> dataAccessCallback = new Callback<List<Channel>>() {
            public void doStuff(List<Channel> param) {
                backgroundOperationEnded();
                synchronized (channelsLock) {
                    channels = param;
                    refreshUi();
                }
            }
        };

        final DataFetcherTask dataFetcherTask = new DataFetcherTask(this, preferences.getEncryptionPassword(), preferences.getLastFetchTime(),
                new Callback<DataFetchResult>() {
                    // TODO: Move this into its own activity, so orientation changes work correctly
                    public void doStuff(DataFetchResult param) {
                        backgroundOperationEnded();
                        preferences.setLastFetchTime(now);

                        if (param.getException() != null) {
                            handleNetworkException(param.getException());
                            return;
                        }

                        if (param.getResponse().getServerMessage() != null && param.getResponse().getServerMessage().length() > 0) {
                            MessageBox.Show(IrssiNotifierActivity.this, null, param.getResponse().getServerMessage(), null);
                        }

                        if (param.getMessages().isEmpty()) {
                            return;
                        }

                        DataAccessTask task = new DataAccessTask(IrssiNotifierActivity.this, dataAccessCallback);
                        List<IrcMessage> messages = param.getMessages();
                        TaskExecutor.executeOnThreadPoolIfPossible(task, messages.toArray(new IrcMessage[messages.size()]));
                        backgroundOperationStarted();
                    }
                });

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshUi();

                if (!uptodate && preferences.isPullMechanismInUse() && LicenseHelper.isPlusVersion(IrssiNotifierActivity.this)) {
                    TaskExecutor.executeOnThreadPoolIfPossible(dataFetcherTask);
                    backgroundOperationStarted();
                }

                DataAccessTask datask = new DataAccessTask(IrssiNotifierActivity.this, dataAccessCallback);
                TaskExecutor.executeOnThreadPoolIfPossible(datask);
                backgroundOperationStarted();

                if (LicenseHelper.isPlusVersion(IrssiNotifierActivity.this)) {
                    checkLicense();
                }
            }
        });
    }

    private void checkLicense() {
        if (preferences.getLicenseCount() >= 2) {
            return;
        }

        if (System.currentTimeMillis() - preferences.getLastLicenseTime() <= 1000 * 60 * 30) {
            return;
        }

        // over half hour since last registration, register again to prevent 15 minute return policy abuse
        LicenseCheckingTask task = new LicenseCheckingTask(this);
        task.setCallback(new Callback<LicenseCheckingTask.LicenseCheckingMessage>() {
            @Override
            public void doStuff(LicenseCheckingTask.LicenseCheckingMessage param) {
                backgroundOperationEnded();

                switch (param.licenseCheckingStatus) {
                    case Allow:
                        // yay! do nothing
                        break;
                    case Disallow:
                        preferences.setLicenseCount(0);
                        MessageBox.Show(IrssiNotifierActivity.this, getText(R.string.not_licensed_title), getText(R.string.not_licensed), new Callback<Void>() {
                            @Override
                            public void doStuff(Void param) {
                                IrssiNotifierActivity.this.finish();
                            }
                        });
                        break;
                    case Error:
                        // do nothing, on next startup licensing will be retried
                        break;
                }
            }
        });
        TaskExecutor.executeOnThreadPoolIfPossible(task);
        backgroundOperationStarted();
    }

    private void backgroundOperationEnded() {
        backgroundOperations--;
        if (backgroundOperations <= 0) {
            backgroundOperations = 0;
            setIndeterminateProgressBarVisibility(false);
        }
    }

    private void backgroundOperationStarted() {
        backgroundOperations++;
        setIndeterminateProgressBarVisibility(true);
    }

    private void refreshUi() {
        synchronized (channelsLock) {
            setContentView(R.layout.main);

            setIndeterminateProgressBarVisibility(!progressBarVisibility); // hack
            setIndeterminateProgressBarVisibility(!progressBarVisibility);

            ViewPager pager = (ViewPager) findViewById(R.id.pager);

            if (adapter == null) {
                adapter = new MessagePagerAdapter(getLayoutInflater());
            }
            if (channels != null) {
                adapter.setChannels(channels);
            }

            pager.setAdapter(adapter);

            TitlePageIndicator titleIndicator = (TitlePageIndicator) findViewById(R.id.titles);
            titleIndicator.setViewPager(pager);

            titleIndicator.setOnPageChangeListener(new OnPageChangeListener() {
                public void onPageSelected(int arg0) {
                    if (channels != null) {
                        Channel ch = channels.get(arg0);
                        if (ch != null) {
                            channelToView = ch.getName();
                        }
                    }
                }

                public void onPageScrolled(int arg0, float arg1, int arg2) {
                }

                public void onPageScrollStateChanged(int arg0) {
                }
            });

            if (channelToView != null && channels != null) {
                for (int i = 0; i < channels.size(); i++) {
                    if (channels.get(i).getName().equalsIgnoreCase(channelToView)) {
                        pager.setCurrentItem(i);
                        break;
                    }
                }
            }

            if (channelToView == null) {
                channelToView = FEED;
            }
        }
    }

    private void setIndeterminateProgressBarVisibility(final boolean state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setSupportProgressBarIndeterminateVisibility(state);
            }
        });
        progressBarVisibility = state;
    }

    private void sendSettings() {
        SettingsSendingTask task = new SettingsSendingTask(this, "", getString(R.string.sending_settings_to_server));

        final Context ctx = this;
        task.setCallback(new Callback<ServerResponse>() {
            public void doStuff(ServerResponse result) {
                backgroundOperationEnded();
                if (result.getException() != null) {
                    handleNetworkException(result.getException());
                    return;
                }

                if (!result.wasSuccesful()) {
                    MessageBox.Show(ctx, null, getString(R.string.unable_to_send_settings), null);
                }
            }
        });

        TaskExecutor.executeOnThreadPoolIfPossible(task);
        backgroundOperationStarted();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.mainmenu, menu);

        if (!preferences.getIcbEnabled() || !IntentSniffer.isPackageAvailable(this, IrssiConnectbotLauncher.PACKAGE_IRSSICONNECTBOT)) {
            menu.findItem(R.id.menu_irssi_connectbot).setVisible(false);
            menu.findItem(R.id.menu_irssi_connectbot).setEnabled(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_irssi_connectbot) {
            IrssiConnectbotLauncher.launchIrssiConnectbot(this);
            //MessageGenerator.Flood(this);
        } else if (item.getItemId() == R.id.menu_settings) {
            Intent settingsActivity = new Intent(this, SettingsActivity.class);
            startActivity(settingsActivity);
        } else if (item.getItemId() == R.id.menu_clear_channel) {
            DataAccess da = new DataAccess(this);
            List<Channel> channels = da.getChannels();
            Channel channelToClear = null;

            if (channelToView == null)
                return true;

            if (channelToView.equals(FEED)) {
                da.clearAllMessagesFromFeed();
                startMainApp(true);
                return true;
            }

            for (Channel ch : channels) {
                if (ch.getName().equalsIgnoreCase(channelToView)) {
                    channelToClear = ch;
                    break;
                }
            }
            if (channelToClear != null) {
                da.clearChannel(channelToClear);
                startMainApp(true);
            }
        } else if (item.getItemId() == R.id.menu_remove_all_channels) {
            DataAccess da = new DataAccess(this);
            da.clearAll();
            startMainApp(true);
        }
        return true;
    }

	public void newMessage(IrcMessage msg) {
		if ((!preferences.isSpamFilterEnabled() || new Date().getTime() > IrcNotificationManager
				.getInstance().getLastSoundDate()
				+ (1000L * preferences.getSpamFilterTime()))) {
            Uri sound = preferences.getNotificationSound();
            if (sound != null) {
                MediaPlayer mp = MediaPlayer.create(this, sound);
                if (mp != null) {
                    mp.start();
                }
            }

            if (preferences.isVibrationEnabled()) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(500);
                }
            }

            IrcNotificationManager.getInstance().setLastSoundDate(new Date().getTime());
        }

        if (preferences.isFeedViewDefault()) {
            channelToView = FEED;
        } else {
            channelToView = msg.getLogicalChannel();
        }
        startMainApp(false);
    }

    private void handleNetworkException(Exception exception) {
        if (exception instanceof AuthenticationException) {
            MessageBox.Show(this, getString(R.string.authentication_error_title), getString(R.string.authentication_error),
                    new Callback<Void>() {
                        public void doStuff(Void param) {
                            restart();
                        }
                    });
        } else if (exception instanceof ServerException) {
            MessageBox.Show(this, getString(R.string.server_error_title), getString(R.string.server_error), null);
        } else if (exception instanceof CryptoException) {
            MessageBox.Show(this, getString(R.string.decryption_error_title), getString(R.string.decryption_error), null);
        } else if (exception instanceof IOException) {
            return;
        } else {
            MessageBox.Show(this, "Error", "What happen", null);
        }
    }

    /*
     * Stored here if need arises
     * public boolean isNetworkAvailable() {
     * ConnectivityManager cm = (ConnectivityManager)
     * getSystemService(Context.CONNECTIVITY_SERVICE); NetworkInfo networkInfo =
     * cm.getActiveNetworkInfo(); // if no network is available networkInfo will
     * be null, otherwise check if we are connected if (networkInfo != null &&
     * networkInfo.isConnected()) { return true; } return false; }
     */
}
