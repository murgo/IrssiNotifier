package fi.iki.murgo.irssinotifier;

import android.os.AsyncTask;
import android.util.Log;


public class TaskExecutor {
    private static final String TAG = TaskExecutor.class.getName();

    private static boolean threadPoolExecutorAvailable;

    static {
        try {
            ThreadPoolExecutor.checkThreadPoolExecutorAvailability(); // this throws exception if we're on too old version
            threadPoolExecutorAvailable = true;
        } catch (Throwable t) {
            threadPoolExecutorAvailable = false;
        }
        Log.i(TAG, "Can use thread pool: " + threadPoolExecutorAvailable);
    }

    public static <A, B, C> void executeOnThreadPoolIfPossible(AsyncTask<A, B, C> task, A... params) {
        if (threadPoolExecutorAvailable) {
            ThreadPoolExecutor.execute(task, params);
        } else{
            task.execute(params);
        }
    }
}
