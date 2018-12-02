
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.sonelli.juicessh.pluginlibrary.PluginContract;

import org.apache.http.auth.AuthenticationException;
import yuku.ambilwarna.AmbilWarnaDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = SettingsActivity.class.getName();
    protected static final int ICB_HOST_REQUEST_CODE = 666;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Opened settings");
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_screen);

        final Context ctx = this;

        final CheckBoxPreference enabled = (CheckBoxPreference) findPreference("NotificationsEnabled");
        enabled.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean checked = (Boolean)newValue;
                String s = "Disabling notifications...";
                if (checked) {
                    s = "Enabling notifications...";
                }

                SettingsSendingTask task = new SettingsSendingTask(SettingsActivity.this, "Sending settings", s);
                task.setCallback(new Callback<ServerResponse>() {
                    @Override
                    public void doStuff(ServerResponse result) {
                        if (result.getException() != null) {
                            if (result.getException() instanceof IOException) {
                                MessageBox.Show(ctx, "Network error", "Ensure your internet connection works and try again.", null);
                            } else if (result.getException() instanceof AuthenticationException) {
                                MessageBox.Show(ctx, "Authentication error", "Unable to authenticate to server.", null);
                            } else if (result.getException() instanceof ServerException) {
                                MessageBox.Show(ctx, "Server error", "Mystical server error, check if updates are available", null);
                            } else {
                                MessageBox.Show(ctx, null, "Unable to send settings to the server! Please try again later!", null);
                            }
                            enabled.setChecked(!checked);
                        }
                    }
                });

                task.execute();

                return true;
            }
        });

        Preference aboutPref = findPreference("about");
        aboutPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(ctx, AboutActivity.class);
                startActivity(i);

                finish();
                return true;
            }
        });

        Preference channelsPref = findPreference("channels");
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

        Preference openNotificationChannelSettings = findPreference("openNotificationChannelSettings");
        openNotificationChannelSettings.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                NotificationChannelCreator.openNotificationChannelSettings(SettingsActivity.this);
                return true;
            }
        });

        Preference applyNotificationSettings = findPreference("applyNotificationSettings");
        applyNotificationSettings.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                NotificationChannelCreator.recreateNotificationChannel(SettingsActivity.this);
                return true;
            }
        });

        Preference initialSettingsPref = findPreference("redoInitialSettings");
        initialSettingsPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Preferences prefs = new Preferences(ctx);
                prefs.setAuthToken(null);
                prefs.setAccountName(null);
                prefs.setGcmRegistrationId(null);
                prefs.setLicenseCount(0);

                IrssiNotifierActivity.refreshIsNeeded();
                finish();
                return true;
            }
        });
        
        Preference disableThemePref = findPreference("ThemeDisabled");
        disableThemePref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                IrssiNotifierActivity.refreshIsNeeded();
                return true;
            }
        });

        handleColorPicker();

        handleIcb();

        handleJuiceSSH();

        if (!LicenseHelper.isPlusVersion(this)) {
            CheckBoxPreference usePullMechanismPref = (CheckBoxPreference)findPreference("UsePullMechanism");
            usePullMechanismPref.setSummary(usePullMechanismPref.getSummary() + ". Only in Plus version.");
            usePullMechanismPref.setEnabled(false);
            usePullMechanismPref.setChecked(false);
        }
    }

    private void handleColorPicker() {
        Preference colorPickerPref = findPreference("PickCustomLightColor");
        colorPickerPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final Context ctx = SettingsActivity.this;

                final Preferences preferences = new Preferences(ctx);
                final int color = preferences.getCustomLightColor();

                NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx);
                builder.setSmallIcon(R.drawable.notification_icon);
                builder.setTicker("Preview selected color");
                builder.setAutoCancel(false);
                builder.setOngoing(false);
                builder.setContentText("Wait for the screen to turn off to see selected light color in action");
                builder.setContentTitle("Preview light color");
                builder.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(), 0));
                builder.setLights(color, 300, 5000);

                final Notification notification = builder.build();
                final NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(666, notification);

                final AmbilWarnaDialog dialog = new AmbilWarnaDialog(ctx, color, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                    @Override
                    public void onCancel(AmbilWarnaDialog dialog) {
                        notificationManager.cancel(666);
                    }

                    @Override
                    public void onOk(AmbilWarnaDialog dialog, int color) {
                        notificationManager.cancel(666);
                        preferences.setCustomLightColor(color);
                    }

                    /*
                    @Override
                    public void onColorChanged(AmbilWarnaDialog dialog, int color) {
                        notification.ledARGB = color;
                        notificationManager.notify(666, notification);
                    }
                    */
                });

                dialog.show();

                return true;
            }
        });
    }

    private void handleIcb() {
        CheckBoxPreference showIcbIconPreference = (CheckBoxPreference)findPreference("IcbEnabled");
        if (!IntentSniffer.isPackageAvailable(this, IrssiConnectbotLauncher.PACKAGE_IRSSICONNECTBOT)) {
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

            Preference icbHostPref = findPreference("IcbHost");

            Preferences prefs = new Preferences(this);
            String hostName = prefs.getIcbHostName();

            String summary = "Select which Irssi ConnectBot host to open when pressing the ICB icon";
            if (hostName != null)
                summary += ". Currently selected host: " + hostName;
            icbHostPref.setSummary(summary);

            icbHostPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    Intent i = new Intent("android.intent.action.PICK");
                    i.setClassName(IrssiConnectbotLauncher.PACKAGE_IRSSICONNECTBOT, IrssiConnectbotLauncher.PACKAGE_IRSSICONNECTBOT + ".HostListActivity");
                    startActivityForResult(i, ICB_HOST_REQUEST_CODE);
                    return true;
                }
            });
        }
    }

    private void handleJuiceSSH() {
        CheckBoxPreference showJuiceSSHIconPreference = (CheckBoxPreference)findPreference("JuiceSSHEnabled");
        if (!IntentSniffer.isPackageAvailable(this, JuiceSSHLauncher.PACKAGE_JUICESSH)) {
            PreferenceCategory juiceSSHCategory = (PreferenceCategory)findPreference("JuiceSSHCategory");
            juiceSSHCategory.setEnabled(false);

            showJuiceSSHIconPreference.setChecked(false);
            showJuiceSSHIconPreference.setSummary("Install JuiceSSH to show it in the action bar");
        } else {
            showJuiceSSHIconPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    IrssiNotifierActivity.refreshIsNeeded();

                    return true;
                }
            });

            Preference juiceSSHHostPref = findPreference("JuiceSSHHost");

            final Preferences prefs = new Preferences(this);
            String hostName = prefs.getJuiceSSHHostName();

            String summary = "Select which JuiceSSH host to open when pressing the JuiceSSH icon";
            if (hostName != null)
                summary += ". Currently selected host: " + hostName;
            juiceSSHHostPref.setSummary(summary);

            final Activity context = this;
            juiceSSHHostPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(final Preference preference) {
                    if (ContextCompat.checkSelfPermission(context, PluginContract.Connections.PERMISSION_READ)
                            != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(context,
                                new String[]{PluginContract.Connections.PERMISSION_READ},
                                0);
                    } else {
                        Cursor cursor = getContentResolver().query(
                                PluginContract.Connections.CONTENT_URI,
                                PluginContract.Connections.PROJECTION,
                                null,
                                null,
                                PluginContract.Connections.SORT_ORDER_DEFAULT
                        );

                        final ArrayList<String> allHostNames = new ArrayList<String>();
                        final ArrayList<String> allHostUUIDs = new ArrayList<String>();

                        List<String> connectionTypes = Arrays.asList(
                                "ssh",
                                "mosh",
                                "local",
                                "telnet"
                        );

                        while (cursor.moveToNext()) {

                            String hostname;
                            hostname = String.format(
                                    "%s://%s:%s (%s)",
                                    connectionTypes.get(cursor.getInt(cursor.getColumnIndex(PluginContract.Connections.COLUMN_TYPE))),
                                    cursor.getString(cursor.getColumnIndex(PluginContract.Connections.COLUMN_ADDRESS)),
                                    cursor.getString(cursor.getColumnIndex(PluginContract.Connections.COLUMN_PORT)),
                                    cursor.getString(cursor.getColumnIndex(PluginContract.Connections.COLUMN_NICKNAME))
                                    );

                            allHostNames.add(hostname);
                            allHostUUIDs.add(cursor.getString(cursor.getColumnIndex(PluginContract.Connections.COLUMN_ID)));
                        }
                        cursor.close();

                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("Select JuiceSSH host");
                        builder.setItems(allHostNames.toArray(new String[allHostNames.size()]), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                prefs.setJuiceSSHHost(allHostNames.get(which), allHostUUIDs.get(which));
                                handleJuiceSSH();
                            }
                        });
                        builder.show();
                    }

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
