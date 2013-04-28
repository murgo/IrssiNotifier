package fi.iki.murgo.irssinotifier;

import android.content.Context;

public class LicenseHelper {

    private static final String PACKAGE_FREE = "fi.iki.murgo.irssinotifier";
    private static final String PACKAGE_PAID = "fi.iki.murgo.irssinotifier.premium";

    public static boolean bothEditionsInstalled(Context context) {
        boolean freeAvailable = IntentSniffer.isPackageAvailable(context, PACKAGE_FREE);
        boolean paidAvailable = IntentSniffer.isPackageAvailable(context, PACKAGE_PAID);
        return freeAvailable && paidAvailable;
    }

    public static boolean isPaidVersion(Context context) {
        return context.getPackageName().equals(PACKAGE_PAID);
    }
}
