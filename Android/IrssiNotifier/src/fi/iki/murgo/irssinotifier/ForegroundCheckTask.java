package fi.iki.murgo.irssinotifier;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.AsyncTask;

/**
 * Broken piece of shit
 */
class ForegroundCheckTask extends AsyncTask<Context, Void, Boolean> {
	@Override
	protected Boolean doInBackground(Context... params) {
		final Context context = params[0].getApplicationContext();
		return isAppOnForeground(context);
	}

	private boolean isAppOnForeground(Context context) {
		ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
		if (appProcesses == null) {
			return false;
		}
		final String packageName = context.getPackageName();
		for (RunningAppProcessInfo appProcess : appProcesses) {
			if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
				return true;
			}
		}
		return false;
	}
}
