
package fi.iki.murgo.irssinotifier;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class MessageServerResponse extends ServerResponse {
    private static final String TAG = MessageServerResponse.class.getName();
    private static final String MESSAGE = "servermessage";

    private String serverMessage;
    private JSONObject responseJson;

    public MessageServerResponse(int statusCode, String responseString) {
        super(statusCode, responseString);

        if (!wasSuccesful() || getResponseString() == null || getResponseString().length() == 0)
            return;

        try {
            responseJson = new JSONObject(getResponseString());
        } catch (Exception e) {
            this.success = false;
            return;
        }

        try {
            this.serverMessage = responseJson.getString(MESSAGE);
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON response: " + e);
            e.printStackTrace();
        }
    }

    public String getServerMessage() {
        return serverMessage;
    }

    public JSONObject getJson() {
        return responseJson;
    }

}
