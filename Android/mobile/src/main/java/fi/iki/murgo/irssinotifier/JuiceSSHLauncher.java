
package fi.iki.murgo.irssinotifier;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class JuiceSSHLauncher {
    public static final String PACKAGE_JUICESSH = "com.sonelli.juicessh";

    public static boolean launchJuiceSSH(Context context) {
        if (!IntentSniffer.isPackageAvailable(context, JuiceSSHLauncher.PACKAGE_JUICESSH))
            return false;

        Preferences prefs = new Preferences(context);
        String hostUUID = prefs.getJuiceSSHHostUUID();
        if (hostUUID == null) {
            Intent juiceSSHActivity = new Intent(context.getPackageManager().getLaunchIntentForPackage(PACKAGE_JUICESSH));
            context.startActivity(juiceSSHActivity);
            return true;
        }

        Intent juiceSSHActivity = new Intent(Intent.ACTION_VIEW);
        juiceSSHActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        juiceSSHActivity.setData(Uri.parse("ssh://" + hostUUID));
        context.startActivity(juiceSSHActivity);
        return true;
    }

}
