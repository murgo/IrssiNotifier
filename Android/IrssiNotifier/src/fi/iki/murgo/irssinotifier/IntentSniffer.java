package fi.iki.murgo.irssinotifier;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

public class IntentSniffer {
	// dirty dirty
	public static boolean isIntentAvailable(Context context, String action) {
	    try {
	    	@SuppressWarnings("unused")
			String asd = context.getPackageManager().getPackageInfo(action, 0).versionName;
	    	return true;
	    } catch (NameNotFoundException e) {
	    	return false;
	    }
	}
}
