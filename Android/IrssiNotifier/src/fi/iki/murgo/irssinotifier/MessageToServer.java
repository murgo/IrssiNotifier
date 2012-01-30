package fi.iki.murgo.irssinotifier;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import org.json.JSONException;
import org.json.JSONObject;

public class MessageToServer {
	private static final String LANGUAGE = "language";
	private static final String VERSION = "version";
	
	private static int version;
	private final JSONObject json;
	
	public MessageToServer(Map<String, Object> values) {
		json = new JSONObject();
		try {
			json.put(LANGUAGE, Locale.getDefault().getISO3Language());
			json.put(VERSION, version);
			for (Map.Entry<String, Object> pair : values.entrySet()) {
				json.put(pair.getKey(), pair.getValue());
			}
		} catch (MissingResourceException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	public JSONObject getJsonObject() {
		return json;
	}
	

	public static void setVersion(int versionCode) {
		version = versionCode;
	}
}
