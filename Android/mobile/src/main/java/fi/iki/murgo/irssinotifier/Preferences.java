
package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

import fi.iki.murgo.irssinotifier.Server.ServerTarget;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

/**
 * Default settings are actually stored in preference_screen.xml :(
 */
public class Preferences {
    private static final String AUTH_TOKEN_KEY = "AuthToken";
    private static final String GCM_REGISTRATION_ID_KEY = "GcmRegistrationId";
    private static final String GCM_REGISTRATION_ID_VERSION_KEY = "GcmRegistrationIdVersion";
    private static final String SETTINGS_SENT_KEY = "SettingsSent";
    private static final String ENCRYPTION_PASSWORD = "EncryptionPassword";
    private static final String NOTIFICATION_MODE = "NotificationMode";
    private static final String LAST_FETCH_TIME = "LastFetchTime";

    private static final String DEVICE_NAME_KEY = "Name";
    private static final String ENABLED_KEY = "Enabled";
    private static final String SOUND_ENABLED = "SoundEnabled";
    private static final String SPAM_FILTER_TIME = "SpamFilterTime";
    private static final String NOTIFICATIONS_ENABLED = "NotificationsEnabled";
    private static final String NOTIFICATION_SOUND = "NotificationSound";
    private static final String LIGHTS_ENABLED = "LightsEnabled";
    private static final String VIBRATION_ENABLED = "VibrationEnabled";
    private static final String FEED_VIEW_DEFAULT = "FeedViewDefault";
    private static final String ICB_HOST_INTENT_URI = "IcbHostIntentUri";
    private static final String ICB_HOST_NAME = "IcbHostName";
    private static final String ICB_ENABLED = "IcbEnabled";
    private static final String THEME_DISABLED = "ThemeDisabled";
    private static final String ACCOUNT_NAME = "AccountName";
    private static final String CUSTOM_LIGHT_COLOR = "CustomLightColor";
    private static final String USE_DEFAULT_LIGHT_COLOR = "UseDefaultLightColor";
    private static final String LAST_LICENSE_TIME = "LastLicenseTime";
    private static final String LICENSE_COUNT = "LicenseCount";
    private static final String USE_PULL_MECHANISM = "UsePullMechanism";
    private static final String PEBBLE_ENABLED = "PebbleEnabled";

    private SharedPreferences sharedPreferences;
    private static int versionCode;

    public Preferences(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void setVersion(int versionCode) {
        Preferences.versionCode = versionCode; 
    }

    public String getGcmRegistrationId() {
        return sharedPreferences.getString(GCM_REGISTRATION_ID_KEY, null);
    }

    public boolean setGcmRegistrationId(String registrationId) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(GCM_REGISTRATION_ID_KEY, registrationId);
        editor.putBoolean(SETTINGS_SENT_KEY, false);
        editor.putInt(GCM_REGISTRATION_ID_VERSION_KEY, versionCode);

        return editor.commit();
    }

    public int getGcmRegistrationIdVersion() {
        return sharedPreferences.getInt(GCM_REGISTRATION_ID_VERSION_KEY, 0);
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

    public boolean settingsNeedSending() {
        return !sharedPreferences.getBoolean(SETTINGS_SENT_KEY, false);
    }

    public ServerResponse sendSettings(Server server) throws IOException {
        HashMap<String, String> map = new HashMap<String, String>();

        if (getGcmRegistrationId() == null) {
            throw new IllegalStateException();
        }

        map.put("RegistrationId", getGcmRegistrationId());
        ServerTarget target;

        if (isNotificationsEnabled()) {
            map.put(DEVICE_NAME_KEY, android.os.Build.MODEL);
            map.put(ENABLED_KEY, "1");
            target = ServerTarget.SaveSettings;
        } else {
            target = ServerTarget.WipeSettings;
        }

        MessageToServer msg = new MessageToServer(map);

        ServerResponse response = server.post(msg, target);
        if (response.wasSuccesful()) {
            sharedPreferences.edit().putBoolean(SETTINGS_SENT_KEY, true).commit();
        }
        return response;
    }

    public String getEncryptionPassword() {
        return sharedPreferences.getString(ENCRYPTION_PASSWORD, "password");
    }

    public NotificationMode getNotificationMode() {
        return NotificationMode.values()[sharedPreferences.getInt(NOTIFICATION_MODE,
                NotificationMode.Single.ordinal())];
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
		return getSpamFilterTime() >= 0;
    }

	public long getSpamFilterTime() {
		return Long.parseLong(sharedPreferences.getString(SPAM_FILTER_TIME, "60"));
    }

    public boolean isNotificationsEnabled() {
        return sharedPreferences.getBoolean(NOTIFICATIONS_ENABLED, true);
    }

    public Uri getNotificationSound() {
        return Uri.parse(sharedPreferences.getString(NOTIFICATION_SOUND,
                Settings.System.DEFAULT_NOTIFICATION_URI.toString()));
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

    public boolean setIcbHost(String hostName, String hostUri) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(ICB_HOST_NAME, hostName);
        editor.putString(ICB_HOST_INTENT_URI, hostUri);
        return editor.commit();
    }
    
    public String getIcbHostName() {
        return sharedPreferences.getString(ICB_HOST_NAME, null);
    }

    public boolean getIcbEnabled() {
        return sharedPreferences.getBoolean(ICB_ENABLED, true);
    }

    public String getIcbHostIntentUri() {
        return sharedPreferences.getString(ICB_HOST_INTENT_URI, null);
    }

    public boolean isThemeDisabled() {
        return sharedPreferences.getBoolean(THEME_DISABLED, false);
    }

    public boolean setAccountName(String name) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(ACCOUNT_NAME, name);
        return editor.commit();
    }

    public String getAccountName() {
        return sharedPreferences.getString(ACCOUNT_NAME, null);
    }

    public boolean setNotificationsEnabled(boolean b) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(NOTIFICATIONS_ENABLED, b);
        return editor.commit();
    }

    public int getCustomLightColor() {
        return sharedPreferences.getInt(CUSTOM_LIGHT_COLOR, 0xff0000ff);
    }

    public boolean setCustomLightColor(int color) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(CUSTOM_LIGHT_COLOR, color);
        return editor.commit();
    }

    public boolean getUseDefaultLightColor() {
        return sharedPreferences.getBoolean(USE_DEFAULT_LIGHT_COLOR, true);
    }

    public boolean setLastLicenseTime(long l) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(LAST_LICENSE_TIME, l);
        return editor.commit();
    }

    public long getLastLicenseTime() {
        return sharedPreferences.getLong(LAST_LICENSE_TIME, 0);
    }

    public boolean setLicenseCount(int i) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(LICENSE_COUNT, i);
        return editor.commit();
    }

    public int getLicenseCount() {
        return sharedPreferences.getInt(LICENSE_COUNT, 0);
    }

    public boolean isPullMechanismInUse() {
        return sharedPreferences.getBoolean(USE_PULL_MECHANISM, true);
    }

    public boolean isPebbleEnabled() {
        return sharedPreferences.getBoolean(PEBBLE_ENABLED, false);
    }
}
