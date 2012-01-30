package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.HashMap;

import fi.iki.murgo.irssinotifier.Server.Target;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
	public static final String PREFERENCES_NAME = "IrssiNotifierPreferences";
	public static final int PREFERENCES_MODE = Context.MODE_PRIVATE;
	
	private static final String AUTH_TOKEN_KEY = "AuthToken";
	private static final String REGISTRATION_ID_KEY = "RegistrationId";
	private static final String SETTINGS_SENT_KEY = "SettingsSent";
	
	private SharedPreferences sharedPreferences;
	
	public Preferences(Context context) {
		sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, PREFERENCES_MODE);
	}
	
	public String getRegistrationId() {
		return sharedPreferences.getString(REGISTRATION_ID_KEY, null);
	}

	public boolean setRegistrationId(String registrationId) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(REGISTRATION_ID_KEY, registrationId);
		editor.putBoolean(SETTINGS_SENT_KEY, false);
		return editor.commit();
	}

	public String getAuthToken() {
		return sharedPreferences.getString(AUTH_TOKEN_KEY, null);
	}

	public boolean setAuthToken(String token) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(AUTH_TOKEN_KEY, token);
		editor.putBoolean(SETTINGS_SENT_KEY, false);
		return editor.commit();
	}

	public void clear() {
        sharedPreferences.edit().clear().commit();
	}

	public boolean settingsNeedSending() {
		return !sharedPreferences.getBoolean(SETTINGS_SENT_KEY, false);
	}

	public ServerResponse sendSettings() throws IOException {
		HashMap<String, Object> map = new HashMap<String, Object>();
		map.put(REGISTRATION_ID_KEY, getRegistrationId());
		MessageToServer msg = new MessageToServer(map);

		Server server = new Server();
		return server.send(msg, Target.SaveSettings);
	}
}
