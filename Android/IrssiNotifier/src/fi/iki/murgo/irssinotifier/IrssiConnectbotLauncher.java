package fi.iki.murgo.irssinotifier;

import android.content.Context;
import android.content.Intent;

public class IrssiConnectbotLauncher {
	public static final String INTENT_IRSSICONNECTBOT = "org.woltage.irssiconnectbot";
	
	public static boolean launchIrssiConnectbot(Context context) {
        if (!IntentSniffer.isIntentAvailable(context, IrssiConnectbotLauncher.INTENT_IRSSICONNECTBOT))
        	return false;
        
        Intent icbActivity = new Intent(context.getPackageManager().getLaunchIntentForPackage(INTENT_IRSSICONNECTBOT));
        context.startActivity(icbActivity);
        return true;
	}

}
