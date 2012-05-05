package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

import org.apache.http.auth.AuthenticationException;

import fi.iki.murgo.irssinotifier.Server.ServerTarget;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;

/**
 * Default settings are actually stored in preference_screen.xml :(
 */
public class Preferences {
	public static final String PREFERENCES_NAME = "IrssiNotifierPreferences";
	public static final int PREFERENCES_MODE = Context.MODE_PRIVATE;
	
	private static final String AUTH_TOKEN_KEY = "AuthToken";
	private static final String REGISTRATION_ID_KEY = "RegistrationId";
	private static final String SETTINGS_SENT_KEY = "SettingsSent";
	private static final String ENCRYPTION_PASSWORD = "EncryptionPassword";
	private static final String NOTIFICATION_MODE = "NotificationMode";
	private static final String LAST_FETCH_TIME = "LastFetchTime";

	private static final String DEVICE_NAME_KEY = "Name";
	private static final String ENABLED_KEY = "Enabled";
	private static final String SOUND_ENABLED = "SoundEnabled";
	private static final String SPAM_FILTER_ENABLED = "SpamFilterEnabled";
	private static final String NOTIFICATIONS_ENABLED = "NotificationsEnabled";
	private static final String NOTIFICATION_SOUND = "NotificationSound";
	private static final String LIGHTS_ENABLED = "LightsEnabled";
	private static final String VIBRATION_ENABLED = "VibrationEnabled";
	private static final String FEED_VIEW_DEFAULT = "FeedViewDefault";

	private SharedPreferences sharedPreferences;
	
	public Preferences(Context context) {
		//sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, PREFERENCES_MODE);
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
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
		if (!authenticated) {
			setAuthToken(null);
			throw new AuthenticationException();
		}
		
		ServerResponse response = server.send(msg, ServerTarget.SaveSettings);
		if (response.wasSuccesful()) {
			sharedPreferences.edit().putBoolean(SETTINGS_SENT_KEY, true).commit();
		}
		return response;
	}
	
	public boolean setEncryptionPassword(String encryptionPassword) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(ENCRYPTION_PASSWORD, encryptionPassword);
		return editor.commit();
	}

	public String getEncryptionPassword() {
		return sharedPreferences.getString(ENCRYPTION_PASSWORD, "password");
	}

	public NotificationMode getNotificationMode() {
		return NotificationMode.values()[sharedPreferences.getInt(NOTIFICATION_MODE, NotificationMode.Single.ordinal())];
	}
	
	public boolean setNotificationMode(NotificationMode notificationMode) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(NOTIFICATION_MODE, notificationMode.ordinal());
		return editor.commit();
	}

	public long getLastFetchTime() {
		return sharedPreferences.getLong(LAST_FETCH_TIME, new Date().getTime());
	}
	
	public boolean setLastFetchTime(long value) {
		return sharedPreferences.edit().putLong(LAST_FETCH_TIME, value).commit();
	}

	public boolean isSoundEnabled() {
		return sharedPreferences.getBoolean(SOUND_ENABLED, true);
	}

	public boolean isSpamFilterEnabled() {
		return sharedPreferences.getBoolean(SPAM_FILTER_ENABLED, true);
	}
	
	public boolean isNotificationsEnabled() {
		return sharedPreferences.getBoolean(NOTIFICATIONS_ENABLED, true);
	}

	public Uri getNotificationSound() {
		return Uri.parse(sharedPreferences.getString(NOTIFICATION_SOUND, Settings.System.DEFAULT_NOTIFICATION_URI.toString()));
	}

	public boolean isVibrationEnabled() {
		return sharedPreferences.getBoolean(VIBRATION_ENABLED, true);
	}

	public boolean isLightsEnabled() {
		return sharedPreferences.getBoolean(LIGHTS_ENABLED, true);
	}
	
	public boolean isFeedViewDefault() {
		return sharedPreferences.getBoolean(FEED_VIEW_DEFAULT, true);
	}
	
	public boolean setFeedViewDefault(boolean b) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(FEED_VIEW_DEFAULT, b);
		return editor.commit();
	}
}
