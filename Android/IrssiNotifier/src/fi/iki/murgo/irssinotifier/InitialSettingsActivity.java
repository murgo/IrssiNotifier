
package fi.iki.murgo.irssinotifier;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import org.apache.http.auth.AuthenticationException;

import java.io.IOException;

public class InitialSettingsActivity extends Activity {

    private static final String TAG = InitialSettingsActivity.class.getSimpleName();
    private Preferences preferences;

    private Callback<Void> errorCallback = new Callback<Void>() {
        public void doStuff(Void param) {
            whatNext(-1);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.initialsettings);

        preferences = new Preferences(this);

        if (LicenseHelper.isPaidVersion(this)) {
            TextView tv = (TextView)findViewById(R.id.textViewWelcomeHelp);
            tv.setText("Thanks for supporting IrssiNotifier! " + tv.getText());
        }

        UserHelper fetcher = new UserHelper();
        final Account[] accounts = fetcher.getAccounts(this);
        String[] accountNames = new String[accounts.length];
        for (int i = 0; i < accounts.length; i++) {
            accountNames[i] = accounts[i].name;
        }

        ListView listView = (ListView) findViewById(R.id.accountsList);
        listView.setAdapter(new ArrayAdapter<String>(this, R.layout.accountlistitem, accountNames));
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                Account account = accounts[arg2];
                preferences.setAccountName(account.name);
                preferences.setNotificationsEnabled(true);
                whatNext(0);
            }
        });

        if (LicenseHelper.bothEditionsInstalled(this)) {
            MessageBox.Show(this, "Not the greatest idea.", "You have both free and paid versions of IrssiNotifier installed. Uninstall either, or you'll get duplicate notifications.", null);
        }
    }

    // stupid state machine
    private void whatNext(int i) {
        Log.d(TAG, "Changing state: " + i);
        switch (i) {
            default:
            case -1:
                preferences.setAccountName(null);
                preferences.setAuthToken(null);
                preferences.setGcmRegistrationId(null);
                finish();
                break;
            case 0:
                registerToGcm();
                break;
            case 1:
                sendSettings();
                break;
            case 2:
                if (LicenseHelper.isPaidVersion(this)) {
                    checkLicense();
                    break;
                }

                startMainApp();
                break;
            case 3:
                startMainApp();
                break;
        }
    }

    private void checkLicense() {
        LicenseCheckingTask task = new LicenseCheckingTask(this, "", "Verifying license...");

        task.setCallback(new Callback<LicenseCheckingTask.LicenseCheckingStatus>() {
            public void doStuff(LicenseCheckingTask.LicenseCheckingStatus result) {
                switch (result) {
                    case Allow:
                        whatNext(3);
                        break;
                    case Disallow:
                        preferences.setLicenseCount(0);
                        MessageBox.Show(InitialSettingsActivity.this, "IrssiNotifier+ is not licensed!", "Shame on you!", errorCallback);
                        break;
                    case Error:
                        MessageBox.Show(InitialSettingsActivity.this, "Licensing error",
                                "An error occurred while trying to check IrssiNotifier+ license validity, please try again after a short while. If problem persist, contact support at https://irssinotifier.appspot.com.", errorCallback, true);
                        break;
                }
            }
        });

        task.execute();
    }

    private void startMainApp() {
        final Context ctx = this;
        String msg = "Check https://irssinotifier.appspot.com for information about setting up your irssi script.";
        String title = "Success";
        if (LicenseHelper.isPaidVersion(this)) {
            msg = "App licensed, thanks! Your account has been upgraded to Plus and pull notification system has been enabled. Check https://irssinotifier.appspot.com for information about setting up your irssi script.";
            title = "Thank you for your support!";
        }
        MessageBox.Show(this, title, msg,
                        new Callback<Void>() {
                            public void doStuff(Void param) {
                                Intent i = new Intent(ctx, IrssiNotifierActivity.class);
                                startActivity(i);
                                finish();
                            }
                        }, true);
    }

    private void sendSettings() {
        SettingsSendingTask task = new SettingsSendingTask(this, "",
                "Sending settings to server...");

        final Context ctx = this;
        task.setCallback(new Callback<ServerResponse>() {
            public void doStuff(ServerResponse result) {
                if (result.getException() != null) {
                    if (result.getException() instanceof IOException) {
                        MessageBox.Show(ctx, "Network error", "Ensure your internet connection works and try again.", errorCallback);
                    } else if (result.getException() instanceof AuthenticationException) {
                        MessageBox.Show(ctx, "Authentication error", "Unable to authenticate to server.", errorCallback);
                    } else if (result.getException() instanceof ServerException) {
                        MessageBox.Show(ctx, "Server error", "Mystical server error, check if updates are available", errorCallback);
                    } else {
                        MessageBox.Show(ctx, null, "Unable to send settings to the server! Please try again later!", errorCallback);
                    }

                    return;
                }

                if (!result.wasSuccesful()) {
                    MessageBox.Show(ctx, null, "Unable to send settings to the server! Please try again later!", errorCallback);

                    return;
                }
                whatNext(2);
            }
        });

        task.execute();
    }

    private void registerToGcm() {
        final GCMRegistrationTask task = new GCMRegistrationTask(this, "", "Registering to GCM..."); // TODO i18n

        final Context ctx = this;
        task.setCallback(new Callback<Boolean>() {
            public void doStuff(Boolean result) {
                task.getDialog().dismiss();
                boolean success = result;

                if (!success) {
                    MessageBox.Show(ctx, null, "Unable to register to GCM! Please try again later!", // TODO i18n
                        new Callback<Void>() {
                            public void doStuff(Void param) {
                                whatNext(-1);
                            }
                        });

                    return;
                }

                whatNext(1);
            }
        });

        task.execute();
    }
}
