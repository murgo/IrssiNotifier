package fi.iki.murgo.irssinotifier;

import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class IrssiNotifierActivity extends Activity {
	
	private static final String TAG = IrssiNotifierActivity.class.getSimpleName();
	
	private ProgressDialog _registerDialog;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        
        if (isFirstTime()) {
        	setContentView(R.layout.initialsettings);
            Button b = (Button)findViewById(R.id.buttonInitialSettingsDone);
            
            b.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				getPrefs().edit().putBoolean(Prefs.FIRST_TIME, false).commit();
    				getPrefs().edit().putString(Prefs.REGISTRATION_ID, null).commit();
    				
    				registerIfNeeded();
    			}});
        }
        else {
        	registerIfNeeded();
        }
    }
	

	
	private void startMainApp() {
        createUi();

        startFetchingData();
	}
    
    private void registerIfNeeded() {
        if (!isRegistered()) {
    		_registerDialog = ProgressDialog.show(this, "", "Registering...", true); // TODO i18n
    		
    		new RegisterTask().execute();
        } else {
        	startMainApp();
        }
    }

	private void registerFailed() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Unable to register to C2DM!") // TODO i18n, better message about what to do
			.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}}).show();
	}

	private void registerToC2DM() {
		Log.d(TAG, "Registering to C2DM");
		C2DMRegistration reg = new C2DMRegistration();
		reg.registerForC2dm(this);
	}

	private boolean isRegistered() {
		return (getRegisterId() != null);
	}
	
	private String getRegisterId() {
		return getPrefs().getString(Prefs.REGISTRATION_ID, null);
	}

	private boolean waitForRegistration() {
		final int loadTime = 10000;
		final long startTime = new Date().getTime();
		
		while (!isRegistered() && new Date().getTime() < startTime + loadTime) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				break;
			}
		}
		return isRegistered();
	}

	private void startFetchingData() {
		// TODO
	}

	private boolean isFirstTime() {
		return getPrefs().getBoolean(Prefs.FIRST_TIME, true);
	}
	
	private void createUi() {
		Log.d(TAG, "(Re)creating UI");
        setContentView(R.layout.main);
        Button b = (Button)findViewById(R.id.buttonRegister);
        
        final Context context = this;
        b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Asd.temp2(context);
			}});
	}
	
	private SharedPreferences getPrefs() {
		return getSharedPreferences(Prefs.PREFERENCES_NAME, Prefs.PREFERENCES_MODE);
	}
	
	private class RegisterTask extends AsyncTask<Void, Void, Boolean> {

		@Override
		protected Boolean doInBackground(Void... params) {
    		registerToC2DM();
    		return waitForRegistration();
		}
		
		protected void onPostExecute(Boolean result) {
    		_registerDialog.dismiss();
    		
        	if (!result) {
        		registerFailed();
        	}
        	else {
        		startMainApp();
        	}
		}
	}
}