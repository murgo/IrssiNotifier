
package fi.iki.murgo.irssinotifier;

import java.util.HashMap;

import android.app.Activity;
import org.apache.http.auth.AuthenticationException;
import org.json.JSONArray;
import org.json.JSONObject;

import fi.iki.murgo.irssinotifier.Server.ServerTarget;
import android.os.AsyncTask;
import android.util.Log;

public class DataFetcherTask extends AsyncTask<Void, Void, DataFetchResult> {
    private static final String TAG = DataFetcherTask.class.getName();

    private final Callback<DataFetchResult> callback;
    private final String encryptionKey;
    private final long lastFetchTime;
    private final Activity activity;

    public DataFetcherTask(Activity activity, String encryptionKey, long lastFetchTime, Callback<DataFetchResult> callback) {
        this.activity = activity;
        this.lastFetchTime = lastFetchTime;
        this.callback = callback;
        this.encryptionKey = encryptionKey;
    }

    @Override
    protected DataFetchResult doInBackground(Void... params) {
        long start = System.nanoTime();
        DataFetchResult result = new DataFetchResult();
        try {
            Server server = new Server(activity);
            boolean authenticated = server.authenticate();
            if (!authenticated) {
                throw new AuthenticationException();
            }

            HashMap<String, String> map = new HashMap<String, String>();
            map.put("timestamp", Long.toString(lastFetchTime / 1000));
            MessageServerResponse response = (MessageServerResponse) server.get(new MessageToServer(map), ServerTarget.Message);
            result.setResponse(response);
            if (!response.wasSuccesful()) {
                throw new ServerException();
            }

            Log.d(TAG, response.getResponseString());

            JSONArray arr = response.getJson().getJSONArray("messages");
            if (arr.length() == 0)
                return result;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject object = new JSONObject(arr.getString(i));
                IrcMessage message = new IrcMessage();
                message.deserialize(object);
                message.decrypt(encryptionKey);
                result.getMessages().add(message);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching data from server!", e);
            e.printStackTrace();
            result.setException(e);
        } finally {
            double elapsed = (System.nanoTime() - start) / 1e6;
            Log.d(TAG, "Data fetching done, elapsed ms: " + elapsed);
        }
        return result;
    }

    @Override
    protected void onPostExecute(DataFetchResult result) {
        if (callback != null) {
            callback.doStuff(result);
        }
    }
}
