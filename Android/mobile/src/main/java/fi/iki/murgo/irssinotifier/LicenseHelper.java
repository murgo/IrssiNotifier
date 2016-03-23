package fi.iki.murgo.irssinotifier;

import android.content.Context;

public class LicenseHelper {

    public static final String PACKAGE_FREE = "fi.iki.murgo.irssinotifier";
    public static final String PACKAGE_PLUS = "fi.iki.murgo.irssinotifier.plus";

    public static boolean bothEditionsInstalled(Context context) {
        boolean freeAvailable = IntentSniffer.isPackageAvailable(context, PACKAGE_FREE);
        boolean plusAvailable = IntentSniffer.isPackageAvailable(context, PACKAGE_PLUS);
        return freeAvailable && plusAvailable;
    }

    public static boolean isPlusVersion(Context context) {
        return context.getPackageName().equals(PACKAGE_PLUS);
    }
}
