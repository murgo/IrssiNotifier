package fi.iki.murgo.irssinotifier;

import java.io.IOException;

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

public class InitialSettingsActivity extends Activity {

	private static final String TAG = InitialSettingsActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.initialsettings);

		UserHelper fetcher = new UserHelper();
		final Account[] accounts = fetcher.getAccounts(this);
		String[] accountNames = new String[accounts.length];
		for (int i = 0; i < accounts.length; i++) {
			accountNames[i] = accounts[i].name;
		}

		ListView listView = (ListView)findViewById(R.id.accountsList);
		listView.setAdapter(new ArrayAdapter<String>(this, R.layout.accountlistitem, accountNames));
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				Account account = accounts[arg2];
				whatNext(0, account);
			}
		});
	}
	
	// stupid state machine
	private void whatNext(int i, Object state) {
		Log.d(TAG, "Changing state: " + i);
		switch (i) {
			default:
			case -1:
				Preferences prefs = new Preferences(this);
				prefs.clear();
				finish();
				break;
			case 0:
				generateToken((Account) state);
				break;
			case 1:
				registerToC2DM((String) state);
				break;
			case 2:
				sendSettings();
				break;
			case 3:
				startMainApp();
				break;
		}
	}

	private void startMainApp() {
		final Context ctx = this;
		MessageBox.Show(this, "Success", "Check http://irssinotifier.appspot.com for information about setting up your irssi script.", new Callback<Void>() {
			public void doStuff(Void param) {
		        Intent i = new Intent(ctx, IrssiNotifierActivity.class);
		        startActivity(i);
		        finish();
			}
		}, true);
	}

	private void sendSettings() {
		SettingsSendingTask task = new SettingsSendingTask(this, "", "Sending settings to server...");
		
		final Context ctx = this;
		task.setCallback(new Callback<ServerResponse>() {
			public void doStuff(ServerResponse result) {
				if (result == null || !result.wasSuccesful()) {
					MessageBox.Show(ctx, null, "Unable to send settings to the server! Please try again later!", new Callback<Void>() { // TODO i18n
						public void doStuff(Void param) {
							whatNext(-1, null);
						}
					});
					return;
				}
				whatNext(3, null);
			}
		});
		
		task.execute();
	}

	private void generateToken(Account account) {
		TokenGenerationTask task = new TokenGenerationTask(this, "", "Generating authentication token...");
		
		final Context ctx = this;
		task.setCallback(new Callback<StringOrException>() {
			public void doStuff(StringOrException result) {
				if (result.getException() != null) {
					Callback<Void> callback = new Callback<Void>() {
						public void doStuff(Void param) {
							whatNext(-1, null);
						}
					};
					if (result.getException() instanceof IOException) {
						MessageBox.Show(ctx, "Network error", "Ensure your internet connection works and try again.", callback); // TODO i18n
					} else {
						MessageBox.Show(ctx, null, "Unable to generate authentication token for account! Please try again later!", callback);  // TODO i18n
					}
					return;
				}

				whatNext(1, result.getString());
			}
		});

		task.execute(account);
	}

	private void registerToC2DM(String token) {
		final C2DMRegistrationTask task = new C2DMRegistrationTask(this, "", "Registering to C2DM..."); // TODO i18n

		final Context ctx = this;
		task.setCallback(new Callback<String[]>() {
			public void doStuff(String[] result) {
				task.getDialog().dismiss();
				// registrationId = result[0];
				String error = result[1];
				String unregistered = result[2];
				
				if ((error != null || unregistered != null)) {
					MessageBox.Show(ctx, null, "Unable to register to C2DM! Please try again later!", new Callback<Void>() { // TODO i18n
						public void doStuff(Void param) {
							whatNext(-1, null);
						}
					});
					return;
				}
				
				whatNext(2, null);
			}
		});
		
		task.execute();
	}
}
