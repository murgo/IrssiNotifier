
package fi.iki.murgo.irssinotifier;

import android.accounts.AccountManager;
import android.content.pm.PackageManager;
import android.text.SpannableStringBuilder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.AccountPicker;

import org.apache.http.auth.AuthenticationException;

import java.io.IOException;

public class InitialSettingsActivity extends Activity {

    private static final String TAG = InitialSettingsActivity.class.getName();
    private static final int CHOOSE_ACCOUNT_REQUEST = 1;
    private Preferences preferences;
    private int _currentStateId = 0;

    private Callback<Void> errorCallback = new Callback<Void>() {
        public void doStuff(Void param) {
            whatNext(-1);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        // Not used for now since GET_ACCOUNTS doesn't seem to be necessary?

        switch (requestCode) {
            case UserHelper.GET_ACCOUNTS_PERMISSION_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // stuff you need to do.
                    whatNext(0);
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    whatNext(-1);
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.initialsettings);

        preferences = new Preferences(this);

        if (LicenseHelper.isPlusVersion(this)) {
            TextView tv = (TextView)findViewById(R.id.textViewWelcomeHelp);
            tv.setText(getString(R.string.welcome_thanks_for_support) + " " + tv.getText());
        }

        whatNext(0);
    }

    // stupid state machine
    private void whatNext(int i) {
        Log.d(TAG, "Changing state: " + i);
        _currentStateId = i;
        switch (i) {
            default:
            case -1:
                preferences.setAccountName(null);
                preferences.setAuthToken(null);
                preferences.setGcmRegistrationId(null);
                finish();
                break;
            case 0:
                getAccounts();
                break;
            case 1:
                registerToFcm();
                break;
            case 2:
                sendSettings();
                break;
            case 3:
                if (LicenseHelper.isPlusVersion(this)) {
                    checkLicense();
                    break;
                }

                startMainApp();
                break;
            case 4:
                startMainApp();
                break;
        }
    }

    private void continueStateMachine() {
        whatNext(_currentStateId + 1);
    }

    private void getAccounts() {
        /*
        UserHelper fetcher = new UserHelper();
        final Account[] accounts = fetcher.getAccounts(this);
        if (accounts == null) {
            // no permission to show accounts, wait for permission request
            return;
        }

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
                continueStateMachine();
            }
        });
        */

        Intent intent = AccountPicker.newChooseAccountIntent(null, null, new String[]{"com.google"},
                false, null, null, null, null);
        startActivityForResult(intent, CHOOSE_ACCOUNT_REQUEST);
    }

    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == CHOOSE_ACCOUNT_REQUEST && resultCode == RESULT_OK) {
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

            preferences.setAccountName(accountName);
            preferences.setNotificationsEnabled(true);
            continueStateMachine();
        } else {
            whatNext(-1);
        }
    }

    private void checkLicense() {
        LicenseCheckingTask task = new LicenseCheckingTask(this, "", getString(R.string.verifying_license));

        task.setCallback(new Callback<LicenseCheckingTask.LicenseCheckingMessage>() {
            public void doStuff(LicenseCheckingTask.LicenseCheckingMessage result) {
                switch (result.licenseCheckingStatus) {
                    case Allow:
                        if (LicenseHelper.bothEditionsInstalled(InitialSettingsActivity.this)) {
                            MessageBox.Show(InitialSettingsActivity.this, null, getString(R.string.both_versions_installed), new Callback<Void>() {
                                @Override
                                public void doStuff(Void param) {
                                    continueStateMachine();
                                }
                            });
                        } else {
                            continueStateMachine();
                        }
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
                continueStateMachine();
            }
        });

        task.execute();
    }

    private void waitForFcm() {
        try {
            while (preferences.getGcmRegistrationId() == null) {
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void registerToFcm() {
        final FCMRegistrationTask task = new FCMRegistrationTask(this, "", getString(R.string.registering_to_gcm));

        task.setCallback(new Callback<Boolean>() {
            public void doStuff(Boolean result) {
                task.getDialog().dismiss();
                continueStateMachine();
            }
        });

        task.execute();
    }
}
