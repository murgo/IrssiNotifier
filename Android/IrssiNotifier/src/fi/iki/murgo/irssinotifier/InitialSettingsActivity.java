
package fi.iki.murgo.irssinotifier;

import android.text.SpannableStringBuilder;

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

    private static final String TAG = InitialSettingsActivity.class.getName();
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

        if (LicenseHelper.isPlusVersion(this)) {
            TextView tv = (TextView)findViewById(R.id.textViewWelcomeHelp);
            tv.setText(getString(R.string.welcome_thanks_for_support) + " " + tv.getText());
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
            MessageBox.Show(this, null, getString(R.string.both_versions_installed), null);
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
                if (LicenseHelper.isPlusVersion(this)) {
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
        LicenseCheckingTask task = new LicenseCheckingTask(this, "", getString(R.string.verifying_license));

        task.setCallback(new Callback<LicenseCheckingTask.LicenseCheckingMessage>() {
            public void doStuff(LicenseCheckingTask.LicenseCheckingMessage result) {
                switch (result.licenseCheckingStatus) {
                    case Allow:
                        whatNext(3);
                        break;
                    case Disallow:
                        preferences.setLicenseCount(0);
                        MessageBox.Show(InitialSettingsActivity.this, getString(R.string.not_licensed_title), getString(R.string.not_licensed), errorCallback);
                        break;
                    case Error:
                        MessageBox.Show(InitialSettingsActivity.this, getText(R.string.licensing_error_title), new SpannableStringBuilder().append(getText(R.string.license_error)).append(result.errorMessage), errorCallback);
                        break;
                }
            }
        });

        task.execute();
    }

    private void startMainApp() {
        final Context ctx = this;
        CharSequence msg = getText(R.string.check_web_page);
        String title = getString(R.string.success);
        if (LicenseHelper.isPlusVersion(this)) {
            msg = getText(R.string.app_licensed);
            title = getString(R.string.thank_you_for_support);
        }
        MessageBox.Show(this, title, msg,
                        new Callback<Void>() {
                            public void doStuff(Void param) {
                                Intent i = new Intent(ctx, IrssiNotifierActivity.class);
                                startActivity(i);
                                finish();
                            }
                        });
    }

    private void sendSettings() {
        SettingsSendingTask task = new SettingsSendingTask(this, "", getString(R.string.sending_settings_to_server));

        final Context ctx = this;
        task.setCallback(new Callback<ServerResponse>() {
            public void doStuff(ServerResponse result) {
                if (result.getException() != null) {
                    if (result.getException() instanceof IOException) {
                        MessageBox.Show(ctx, getString(R.string.network_error_title), getString(R.string.network_error), errorCallback);
                    } else if (result.getException() instanceof AuthenticationException) {
                        MessageBox.Show(ctx, getString(R.string.authentication_error_title), getString(R.string.authentication_error), errorCallback);
                    } else if (result.getException() instanceof ServerException) {
                        MessageBox.Show(ctx, getString(R.string.server_error_title), getString(R.string.server_error), errorCallback);
                    } else {
                        MessageBox.Show(ctx, null, getString(R.string.unable_to_send_settings), errorCallback);
                    }

                    return;
                }

                if (!result.wasSuccesful()) {
                    MessageBox.Show(ctx, null, getString(R.string.unable_to_send_settings), errorCallback);

                    return;
                }
                whatNext(2);
            }
        });

        task.execute();
    }

    private void registerToGcm() {
        final GCMRegistrationTask task = new GCMRegistrationTask(this, "", getString(R.string.registering_to_gcm));

        final Context ctx = this;
        task.setCallback(new Callback<Boolean>() {
            public void doStuff(Boolean result) {
                task.getDialog().dismiss();
                boolean success = result;

                if (!success) {
                    MessageBox.Show(ctx, null, getString(R.string.unable_to_register_gcm),
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
