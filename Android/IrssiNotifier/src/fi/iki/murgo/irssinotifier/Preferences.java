package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.HashMap;

import org.apache.http.auth.AuthenticationException;

import fi.iki.murgo.irssinotifier.Server.ServerTarget;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
	public static final String PREFERENCES_NAME = "IrssiNotifierPreferences";
	public static final int PREFERENCES_MODE = Context.MODE_PRIVATE;
	
	private static final String AUTH_TOKEN_KEY = "AuthToken";
	private static final String REGISTRATION_ID_KEY = "RegistrationId";
	private static final String SETTINGS_SENT_KEY = "SettingsSent";
	private static final String ENCRYPTION_PASSWORD = "EncryptionPassword";
	private static final String NOTIFICATION_MODE = "NotificationMode";

	private static final String DEVICE_NAME_KEY = "Name";
	private static final String ENABLED_KEY = "Enabled";

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

	public ServerResponse sendSettings() throws IOException, AuthenticationException {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put(REGISTRATION_ID_KEY, getRegistrationId());
		map.put(DEVICE_NAME_KEY, android.os.Build.MODEL);
		map.put(ENABLED_KEY, "1");
		MessageToServer msg = new MessageToServer(map);

		Server server = new Server();
		boolean authenticated = server.authenticate(getAuthToken());
		if (!authenticated) throw new AuthenticationException();
		
		return server.send(msg, ServerTarget.SaveSettings);
	}
	
	public boolean setEncryptionPassword(String encryptionPassword) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(ENCRYPTION_PASSWORD, encryptionPassword);
		return editor.commit();
	}

	public String getEncryptionPassword() {
		return sharedPreferences.getString(ENCRYPTION_PASSWORD, null);
	}

	public NotificationMode getNotificationMode() {
		return NotificationMode.values()[sharedPreferences.getInt(NOTIFICATION_MODE, 0)];
	}
	
	public boolean setNotificationMode(NotificationMode notificationMode) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(NOTIFICATION_MODE, notificationMode.ordinal());
		return editor.commit();
	}
}
