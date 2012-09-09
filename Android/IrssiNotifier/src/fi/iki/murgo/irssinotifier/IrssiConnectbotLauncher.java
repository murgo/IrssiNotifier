
package fi.iki.murgo.irssinotifier;

import java.net.URISyntaxException;

import android.content.Context;
import android.content.Intent;

public class IrssiConnectbotLauncher {
    public static final String INTENT_IRSSICONNECTBOT = "org.woltage.irssiconnectbot";

    public static boolean launchIrssiConnectbot(Context context) {
        if (!IntentSniffer.isIntentAvailable(context, IrssiConnectbotLauncher.INTENT_IRSSICONNECTBOT))
            return false;

        Preferences prefs = new Preferences(context);
        String hostUri = prefs.getIcbHostIntentUri();
        if (hostUri == null) {
            Intent icbActivity = new Intent(context.getPackageManager().getLaunchIntentForPackage(INTENT_IRSSICONNECTBOT));
            context.startActivity(icbActivity);
            return true;
        }
        
        try {
            Intent intent = Intent.parseUri(hostUri, 0);
            context.startActivity(intent);
            return true;
        } catch (URISyntaxException e) {
            prefs.setIcbHost(null, null);
            return false;
        }
    }

}
