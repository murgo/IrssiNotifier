package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class Server {
	private static final String TAG = InitialSettingsActivity.class.getSimpleName();

	private static final String SERVER_URL = "http://irssinotifier.appspot.com/API";
	private static final String LANGUAGE = "language";
	
	public static ServerResponse send(JSONObject json) throws IOException, JSONException {
		json.put(LANGUAGE, Locale.getDefault().getISO3Language());
		
		byte[] bytes = ("json=" + json.toString()).getBytes();
		
		URL url = new URL(SERVER_URL);
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

		Log.d(TAG, responseString);
		
		JSONObject responseJson = new JSONObject(responseString);
		ServerResponse serverResponse = new ServerResponse(status == 200, responseJson);
		return serverResponse;
	}

	private static String readResponse(InputStream is) {
		return new Scanner(is).useDelimiter("\\A").next();
	}

}
