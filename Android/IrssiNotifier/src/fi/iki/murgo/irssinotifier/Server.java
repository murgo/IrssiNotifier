package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

public class Server {
	//private static final String TAG = InitialSettingsActivity.class.getSimpleName();
	
	public enum ServerTarget {
		SaveSettings,
		Test,
		FetchData,
		Authenticate,
	}
	
	private Map<ServerTarget, String> serverUrls = new HashMap<ServerTarget, String>();

	private static final String SERVER_BASE_URL = "https://irssinotifier.appspot.com/API/";

	private DefaultHttpClient http_client = new DefaultHttpClient();

	public Server() {
		serverUrls.put(ServerTarget.SaveSettings, SERVER_BASE_URL + "Settings");
		serverUrls.put(ServerTarget.Test, SERVER_BASE_URL + "Test");
		serverUrls.put(ServerTarget.FetchData, SERVER_BASE_URL + "FetchData");
		serverUrls.put(ServerTarget.Authenticate, "https://irssinotifier.appspot.com/_ah/login?continue=https://localhost/&auth=");
	}
	
	public boolean authenticate(String token) {
		try {
			// Don't follow redirects
	        http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
	        
	        HttpGet http_get = new HttpGet(serverUrls.get(ServerTarget.Authenticate) + token);
	        HttpResponse response;
	        response = http_client.execute(http_get);
	        
	        if(response.getStatusLine().getStatusCode() != 302)
                return false;
	        
	        for(Cookie cookie : http_client.getCookieStore().getCookies()) {
                if(cookie.getName().equals("SACSID"))
                    return true;
	        }
	    } catch (ClientProtocolException e) {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
    	} catch (IOException e) { 		
	        // TODO Auto-generated catch block
	        e.printStackTrace();
		} finally {
			if (http_client != null)
		        http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
		}
		return false;
	}
	
	public ServerResponse send(MessageToServer message, ServerTarget target) throws IOException {
		HttpPost httpPost = new HttpPost(serverUrls.get(target));
		
		HttpParams params = new BasicHttpParams();
		Map<String, String> map = message.getMap(); 
		for (Map.Entry<String, String> entry : map.entrySet()) {
			params.setParameter(entry.getKey(), entry.getValue());
		}
		httpPost.setParams(params);
		httpPost.setEntity(new StringEntity(message.getHttpString()));
		
		HttpResponse response = http_client.execute(httpPost);
		int statusCode = response.getStatusLine().getStatusCode();
		String responseString = readResponse(response.getEntity().getContent());
		
		// TODO: error handling (authentication etc)
		
		ServerResponse serverResponse = new ServerResponse(statusCode == 200, responseString);
		return serverResponse;
	}

	private static String readResponse(InputStream is) {
		return new Scanner(is).useDelimiter("\\A").next();
	}

}
