package fi.iki.murgo.irssinotifier;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class ServerResponse {
	private static final String TAG = IrssiNotifierActivity.class.getSimpleName();

	private static final String MESSAGE = "message";
	
	private final boolean success;
	private String message;
	
	public ServerResponse(boolean success, JSONObject json) {
		this.success = success;
		if (!success) 
			return;
		
		try {
			this.message = json.getString(MESSAGE);
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
