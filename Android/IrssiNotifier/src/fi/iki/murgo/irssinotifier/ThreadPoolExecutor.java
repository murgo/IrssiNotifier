package fi.iki.murgo.irssinotifier;

import android.os.AsyncTask;

public class ThreadPoolExecutor {
    /**
     * Class initialization fails when this throws an exception.
     * Checking availability is done on static class initialization for Android 2.2 compatibility.
     */
    static {
        try {
            Class.forName("android.os.AsyncTask").getDeclaredField("THREAD_POOL_EXECUTOR");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Calling this forces class initialization
     */
    public static void checkThreadPoolExecutorAvailability() {}

    public static <A, B, C> void execute(AsyncTask<A, B, C> task, A... params) {
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }
}
