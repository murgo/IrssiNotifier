
package fi.iki.murgo.irssinotifier;

import android.accounts.AccountManager;
import android.content.pm.PackageManager;
import android.os.CancellationSignal;
import android.os.Message;
import android.text.SpannableStringBuilder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.gms.common.AccountPicker;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class InitialSettingsActivity extends Activity {

    private static final String TAG = InitialSettingsActivity.class.getName();
    //private static final int CHOOSE_ACCOUNT_REQUEST = 1;
    private Preferences preferences;
    //private int _currentStateId = 0;

    private final Callback<Void> errorCallback = new Callback<Void>() {
        public void doStuff(Void param) {
            preferences.setAuthToken(null);
            preferences.setGcmRegistrationId(null);
            finish();
        }
    };

    /*
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
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.initialsettings);

        findViewById(R.id.login_button).setOnClickListener(v -> authenticateToGoogle());

        preferences = new Preferences(this);
    }

    /*
    // stupid state machine
    private void whatNext(int i) {
        Log.d(TAG, "Changing state: " + i);
        _currentStateId = i;
        switch (i) {
            case 1:
                break;
            case 2:
                sendSettings();
                break;
            case 3:
                startMainApp();
                break;
            default:
                //preferences.setAccountName(null);
                preferences.setAuthToken(null);
                preferences.setGcmRegistrationId(null);
                finish();
                break;
        }
    }
     */

    private void authenticateToGoogle() {
        String clientId = "717656048279-t8f9966hla0398bjc4alipfe3tmqu133.apps.googleusercontent.com"; // TODO: SWITCH CLIENT ID

        GetGoogleIdOption opt = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                // .setNonce(<nonce string to use when generating a Google ID token>)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(opt)
                .build();

        CredentialManager credentialManager = CredentialManager.create(this);

        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.setOnCancelListener(() -> {
            //handle cancellation if needed
        });

        InitialSettingsActivity ctx = this;

        credentialManager.getCredentialAsync(this, request, cancellationSignal, Runnable::run,
        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override
            public void onResult(GetCredentialResponse getCredentialResponse) {
                handleGoogleSignIn(getCredentialResponse);
            }

            @Override
            public void onError(@NotNull GetCredentialException e) {
                ctx.runOnUiThread(() -> {
                    MessageBox.Show(ctx, "Error while getting credentials", e.getMessage(), null);
                });
                Log.e(TAG, "Error while getting credentials: " + e.getMessage(), e);
            }
        });
    }

    void handleGoogleSignIn(GetCredentialResponse result) {
        // Handle the successfully returned credential.
        Credential credential = result.getCredential();

        if (credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            try {
                // Use googleIdTokenCredential and extract the ID to validate and
                // authenticate on your server.
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());

                String idTokenString = googleIdTokenCredential.getIdToken();
                Preferences prefs = new Preferences(this);
                prefs.setAuthToken(idTokenString);

                // You can use the members of googleIdTokenCredential directly for UX
                // purposes, but don't use them to store or control access to user
                // data. For that you first need to validate the token:
                // pass googleIdTokenCredential.getIdToken() to the backend server.
                //GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier();


                //GoogleIdToken idToken = verifier.verify(idTokenString);

                // To get a stable account identifier (e.g. for storing user data),
                // use the subject ID:
                //idToken.getPayload().getSubject();
            } catch (Exception e ) {
                Log.e(TAG, "Received an invalid google id token response", e);
            }
        } else {
            // Catch any unrecognized custom credential type here.
            Log.e(TAG, "Unexpected type of credential");
        }

        registerToFcm();
    }

    /*
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == CHOOSE_ACCOUNT_REQUEST && resultCode == RESULT_OK) {
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

            //preferences.setAccountName(accountName);
            preferences.setNotificationsEnabled(true);
            continueStateMachine();
        } else {
            whatNext(-1);
        }
    }
     */

    private void registerToFcm() {
        final FCMRegistrationTask task = new FCMRegistrationTask(this, "", getString(R.string.registering_to_gcm));

        task.setCallback(new Callback<Boolean>() {
            public void doStuff(Boolean result) {
                runOnUiThread(() -> {
                    task.getDialog().dismiss();
                    sendSettings();
                });
            }
        });

        task.execute();
    }

    private void sendSettings() {
        SettingsSendingTask task = new SettingsSendingTask(this, "", getString(R.string.sending_settings_to_server));

        final Context ctx = this;
        task.setCallback(new Callback<ServerResponse>() {
            public void doStuff(ServerResponse result) {
                runOnUiThread(() -> {
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

                    startMainApp();
                });
            }
        });

        task.execute();
    }

    private void startMainApp() {
        final Context ctx = this;
        CharSequence msg = getText(R.string.check_web_page);
        String title = getString(R.string.success);

        runOnUiThread(() -> {
            MessageBox.Show(this, title, msg,
                    new Callback<Void>() {
                        public void doStuff(Void param) {
                            Intent i = new Intent(ctx, IrssiNotifierActivity.class);
                            startActivity(i);
                            finish();
                        }
                    });
        });
    }
}
