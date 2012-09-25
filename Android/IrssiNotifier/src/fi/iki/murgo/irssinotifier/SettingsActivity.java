
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;

@SuppressWarnings("deprecation") // fuck the police
public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();
    protected static final int ICB_HOST_REQUEST_CODE = 666;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Opened settings");
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_screen);

        final Context ctx = this;
        Preference aboutPref = (Preference) findPreference("about");
        aboutPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(ctx, AboutActivity.class);
                startActivity(i);

                finish();
                return true;
            }
        });

        Preference channelsPref = (Preference) findPreference("channels");
        channelsPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(ctx, ChannelSettingsActivity.class);
                startActivity(i);
                return true;
            }
        });

        ListPreference mode = (ListPreference) findPreference("notificationModeString");
        mode.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                NotificationMode m = NotificationMode.PerChannel;
                String v = (String) newValue;
                if (v.equals(ctx.getResources().getStringArray(R.array.notification_modes)[0]))
                    m = NotificationMode.Single;
                if (v.equals(ctx.getResources().getStringArray(R.array.notification_modes)[1]))
                    m = NotificationMode.PerChannel;
                if (v.equals(ctx.getResources().getStringArray(R.array.notification_modes)[2]))
                    m = NotificationMode.PerMessage;

                Preferences p = new Preferences(ctx);
                p.setNotificationMode(m);
                return true;
            }
        });

        Preference initialSettingsPref = (Preference) findPreference("redoInitialSettings");
        initialSettingsPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Preferences prefs = new Preferences(ctx);
                prefs.setAuthToken(null);
                prefs.setGcmRegistrationId(null);

                IrssiNotifierActivity.refreshIsNeeded();
                finish();
                return true;
            }
        });
        
        Preference disableThemePref = (Preference) findPreference("ThemeDisabled");
        disableThemePref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                IrssiNotifierActivity.refreshIsNeeded();
                return true;
            }
        });

        handleIcb();
    }
    
    private void handleIcb() {
        CheckBoxPreference showIcbIconPreference = (CheckBoxPreference)findPreference("IcbEnabled");
        if (!IntentSniffer.isIntentAvailable(this, IrssiConnectbotLauncher.INTENT_IRSSICONNECTBOT)) {
            PreferenceCategory icbCategory = (PreferenceCategory)findPreference("IcbCategory");
            icbCategory.setEnabled(false);
            
            showIcbIconPreference.setChecked(false);
            showIcbIconPreference.setSummary("Install Irssi ConnectBot to show it in the action bar");
        } else {
            showIcbIconPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    IrssiNotifierActivity.refreshIsNeeded();

                    return true;
                }
            });
            
            Preference icbHostPref = (Preference) findPreference("IcbHost");
            
            Preferences prefs = new Preferences(this);
            String hostName = prefs.getIcbHostName();
            
            String summary = "Select which Irssi ConnectBot host to open when pressing the ICB icon";
            if (hostName != null)
                summary += ". Currently selected host: " + hostName;
            icbHostPref.setSummary(summary);
            
            icbHostPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    Intent i = new Intent("android.intent.action.PICK");
                    i.setClassName(IrssiConnectbotLauncher.INTENT_IRSSICONNECTBOT, IrssiConnectbotLauncher.INTENT_IRSSICONNECTBOT + ".HostListActivity");
                    startActivityForResult(i, ICB_HOST_REQUEST_CODE);
                    return true;
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode != ICB_HOST_REQUEST_CODE) {
            return;
        }

        Preferences prefs = new Preferences(this);

        if (data == null || resultCode != Activity.RESULT_OK) {
            prefs.setIcbHost(null, null);
        } else {
            String hostName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            Intent intent = (Intent) data.getExtras().get(Intent.EXTRA_SHORTCUT_INTENT);
            if (intent != null) {
                String intentUri = intent.toUri(0);
                prefs.setIcbHost(hostName, intentUri);
            } else {
                prefs.setIcbHost(null, null);
            }
        }
        
        handleIcb();
    }
}
