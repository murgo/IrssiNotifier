
package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.sonelli.juicessh.pluginlibrary.PluginClient;
import com.sonelli.juicessh.pluginlibrary.PluginContract;
import com.sonelli.juicessh.pluginlibrary.exceptions.ServiceNotConnectedException;
import com.sonelli.juicessh.pluginlibrary.listeners.OnClientStartedListener;
import com.sonelli.juicessh.pluginlibrary.listeners.OnSessionFinishedListener;
import com.sonelli.juicessh.pluginlibrary.listeners.OnSessionStartedListener;

public class JuiceSSHLauncher extends Activity implements OnSessionStartedListener, OnSessionFinishedListener, OnClientStartedListener {
    public final static String PACKAGE_JUICESSH = "com.sonelli.juicessh";
    private final static int JUICESSH_REQUEST_ID = 2342;
    private final PluginClient client = new PluginClient();
    private boolean isClientStarted = false;
    private boolean isConnectionInitiated = false;
    private String hostUUID;

    @Override
    public void onStart() {
        super.onStart();

        Preferences prefs = new Preferences(this);
        hostUUID = prefs.getJuiceSSHHostUUID();

        if (hostUUID == null) {
            if (!IntentSniffer.isPackageAvailable(this, JuiceSSHLauncher.PACKAGE_JUICESSH)) {
                finish();
            }

            Intent juiceSSHActivity = new Intent(this.getPackageManager().getLaunchIntentForPackage(PACKAGE_JUICESSH));
            this.startActivity(juiceSSHActivity);
            finish();
        }

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{PluginContract.PERMISSION_OPEN_SESSIONS},
                    0);
            finish();
        }

        if (!isConnectionInitiated) {
            client.start(this, JuiceSSHLauncher.this);
        } else if (isConnectionInitiated && isClientStarted) {
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(isClientStarted && !isConnectionInitiated){
            isClientStarted = false;
            client.stop(this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSessionStarted(final int sessionId, final String sessionKey) {
        try {
            client.attach(sessionId, sessionKey);
        } catch (ServiceNotConnectedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSessionCancelled() {
        if(isClientStarted) {
            isClientStarted = false;
            client.stop(this);
        }
    }

    @Override
    public void onSessionFinished() {
        if(isClientStarted) {
            isClientStarted = false;
            client.stop(this);
        }
    }

    @Override
    public void onClientStarted() {
        isClientStarted = true;

        if (isClientStarted){
            try {
                isConnectionInitiated = true;
                client.connect(this, java.util.UUID.fromString(hostUUID), JuiceSSHLauncher.this, JUICESSH_REQUEST_ID);
            } catch (ServiceNotConnectedException e) {
                Toast.makeText(this, "Could not connect to JuiceSSH Plugin Service", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onClientStopped() {
        isClientStarted = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == JUICESSH_REQUEST_ID){
            client.gotActivityResult(requestCode, resultCode, data);
        }
    }

    private boolean hasPermissions() {
        return (ContextCompat.checkSelfPermission(this, PluginContract.PERMISSION_OPEN_SESSIONS)
                == PackageManager.PERMISSION_GRANTED);
    }
}
