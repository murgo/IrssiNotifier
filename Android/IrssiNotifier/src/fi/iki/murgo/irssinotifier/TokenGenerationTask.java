package fi.iki.murgo.irssinotifier;

import java.io.IOException;

import android.accounts.Account;
import android.app.Activity;
import android.util.Log;

public class TokenGenerationTask extends BackgroundAsyncTask<Account, Void, StringOrException> {
	private static final String TAG = TokenGenerationTask.class.getSimpleName();

	public TokenGenerationTask(Activity activity, String titleText,
			String text) {
		super(activity, titleText, text);
	}

	@Override
	protected StringOrException doInBackground(Account... params) {
		StringOrException hack = new StringOrException();
		try {
			UserHelper uf = new UserHelper();
			String token = uf.getAuthToken(activity, params[0]);
			Preferences prefs = new Preferences(activity);
			prefs.setAuthToken(token);
			hack.setString(token);
		} catch (IOException e) {
			// Network error
			hack.setException(e);
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Unable to generate token: " + e.toString());
			hack.setException(e);
		}
		return hack;
	}
}
