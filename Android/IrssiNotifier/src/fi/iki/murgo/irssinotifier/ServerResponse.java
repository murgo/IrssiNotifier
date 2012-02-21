package fi.iki.murgo.irssinotifier;

public class ServerResponse {
	//private static final String TAG = ServerResponse.class.getSimpleName();
	protected boolean success;
	private final String responseString;

	public ServerResponse(boolean success, String responseString) {
		this.success = success;
		this.responseString = responseString;
	}

	public boolean wasSuccesful() {
		return success;
	}

	public String getResponseString() {
		return responseString;
	}

}