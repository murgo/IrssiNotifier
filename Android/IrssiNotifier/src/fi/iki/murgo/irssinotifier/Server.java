package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import android.util.Log;

public class Server {
	private static final String TAG = InitialSettingsActivity.class.getSimpleName();
	
	public enum Target {
		SaveSettings,
		Test,
		FetchData,
	}
	
	private Map<Target, String> serverUrls = new HashMap<Target, String>();

	private static final String SERVER_BASE_URL = "http://irssinotifier.appspot.com/API/";

	public Server() {
		serverUrls.put(Target.SaveSettings, SERVER_BASE_URL);// + "SaveSettings");
		serverUrls.put(Target.Test, SERVER_BASE_URL + "Test");
		serverUrls.put(Target.FetchData, SERVER_BASE_URL + "FetchData");
	}
	
	public ServerResponse send(MessageToServer message, Target target) throws IOException {
		byte[] bytes = message.getJsonObject().toString().getBytes();
		
		URL url = new URL(serverUrls.get(target));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));

		connection.setUseCaches(false);
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setChunkedStreamingMode(0);
		
		OutputStream stream = connection.getOutputStream();
		stream.write(bytes);
		stream.flush();
		stream.close();
		
		int status = connection.getResponseCode();
		String responseString = readResponse(connection.getInputStream());
		connection.disconnect();

		// TODO: WHY ISN'T SENDING WORKING
		Log.d(TAG, responseString);
		
		ServerResponse serverResponse = new ServerResponse(status == 200, responseString);
		return serverResponse;
	}

	private static String readResponse(InputStream is) {
		return new Scanner(is).useDelimiter("\\A").next();
	}

}
