package fi.iki.murgo.irssinotifier;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class ServerResponse {
	private static final String TAG = IrssiNotifierActivity.class.getSimpleName();

	private static final String MESSAGE = "message";
	
	private final boolean success;
	private String message;

	private JSONObject responseJson;
	
	public ServerResponse(boolean success, String responseString) {
		this.success = success;
		if (!success || responseString == null || responseString.length() == 0) 
			return;

		try {
			responseJson = new JSONObject(responseString);
		} catch (Exception e) {
			success = false;
			return;
		}
		
		try {
			this.message = responseJson.getString(MESSAGE);
		} catch (JSONException e) {
			Log.e(TAG, "Invalid JSON response: " + e);
			e.printStackTrace();
		}
	}
	
	public boolean wasSuccesful() {
		return success;
	}

	public String getMessage() {
		return message;
	}
}
