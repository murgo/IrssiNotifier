package fi.iki.murgo.irssinotifier;

import java.util.HashMap;

import org.apache.http.auth.AuthenticationException;
import org.json.JSONArray;
import org.json.JSONObject;

import fi.iki.murgo.irssinotifier.Server.ServerTarget;
import android.os.AsyncTask;
import android.util.Log;

public class DataFetcherTask extends AsyncTask<Void, Void, DataFetchResult> {
	private static final String TAG = DataFetcherTask.class.getSimpleName();
	
	private final String authToken;
	private final Callback<DataFetchResult> callback;
	private final String encryptionKey;
	private final long lastFetchTime;

	public DataFetcherTask(String authToken, String encryptionKey, long lastFetchTime, Callback<DataFetchResult> callback) {
		this.authToken = authToken;
		this.lastFetchTime = lastFetchTime;
		this.callback = callback;
		this.encryptionKey = encryptionKey;
	}
	
	@Override
	protected DataFetchResult doInBackground(Void... params) {
		DataFetchResult result = new DataFetchResult();
		try {
			Server server = new Server();
			boolean authenticated = server.authenticate(authToken);
			if (!authenticated) {
				throw new AuthenticationException();
			}
			
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("timestamp", Long.toString(lastFetchTime / 1000));
			MessageServerResponse response = (MessageServerResponse)server.get(new MessageToServer(map), ServerTarget.Message);
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
				message.Deserialize(object);
				message.Decrypt(encryptionKey);
				result.getMessages().add(message);
			}
		} catch (Exception e) {
			e.printStackTrace();
			result.setException(e);
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
