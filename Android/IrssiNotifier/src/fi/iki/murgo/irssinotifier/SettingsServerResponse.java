package fi.iki.murgo.irssinotifier;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class SettingsServerResponse extends ServerResponse {
	private static final String TAG = SettingsServerResponse.class.getSimpleName();
	private static final String MESSAGE = "message";
	
	private String message;
	private JSONObject responseJson;
	
	public SettingsServerResponse(boolean success, String responseString) {
		super(success, responseString);
		
		if (!wasSuccesful() || getResponseString() == null || getResponseString().length() == 0) 
			return;

		try {
			responseJson = new JSONObject(getResponseString());
		} catch (Exception e) {
			this.success = false;
			return;
		}
		
		try {
			this.message = responseJson.getString(MESSAGE);
		} catch (JSONException e) {
			Log.e(TAG, "Invalid JSON response: " + e);
			e.printStackTrace();
		}
	}
	
	public String getMessage() {
		return message;
	}
}
