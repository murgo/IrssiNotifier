
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

    private Callback<Void> errorCallback = new Callback<Void>() {
        public void doStuff(Void param) {
            whatNext(-1);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.initialsettings);

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
                Preferences prefs = new Preferences(InitialSettingsActivity.this);
                prefs.setAccountName(account.name);
                prefs.setNotificationsEnabled(true);
                whatNext(0);
            }
        });

        if (LicenseHelper.bothEditionsInstalled(this)) {
            MessageBox.Show(this, "Not the greatest idea", "You have both free and paid versions of IrssiNotifier installed. Uninstall either, or you'll get duplicate notifications.", null);
        }
    }

    // stupid state machine
    private void whatNext(int i) {
        Log.d(TAG, "Changing state: " + i);
        switch (i) {
            default:
            case -1:
                Preferences prefs = new Preferences(this);
                prefs.setAccountName(null);
                prefs.setAuthToken(null);
                prefs.setGcmRegistrationId(null);
                finish();
                break;
            case 0:
                registerToGcm();
                break;
            case 1:
                sendSettings();
                break;
            case 2:
                startMainApp();
                break;
        }
    }

    private void startMainApp() {
        final Context ctx = this;
        MessageBox
                .Show(this,
                        "Success",
                        "Check https://irssinotifier.appspot.com for information about setting up your irssi script.",
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
