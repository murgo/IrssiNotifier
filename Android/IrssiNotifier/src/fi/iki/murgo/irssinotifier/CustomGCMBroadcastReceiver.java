package fi.iki.murgo.irssinotifier;

import android.content.Context;
import com.google.android.gcm.GCMBroadcastReceiver;

public class CustomGCMBroadcastReceiver extends GCMBroadcastReceiver {
    @Override
    protected String getGCMIntentServiceClassName(Context context) {
        return "fi.iki.murgo.irssinotifier.GCMIntentService";
    }
}
