
package fi.iki.murgo.irssinotifier;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

public class IntentSniffer {
    // dirty dirty
    public static boolean isPackageAvailable(Context context, String packageName) {
        try {
            @SuppressWarnings("unused")
            String asd = context.getPackageManager().getPackageInfo(packageName, 0).versionName;
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }
}
