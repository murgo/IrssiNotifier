package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

public class Server {
	//private static final String TAG = InitialSettingsActivity.class.getSimpleName();
	
	public enum ServerTarget {
		SaveSettings,
		Test,
		FetchData,
		Authenticate,
		Message,
	}
	
	private Map<ServerTarget, String> serverUrls = new HashMap<ServerTarget, String>();

	private static final String SERVER_BASE_URL = "https://irssinotifier.appspot.com/API/";

	private DefaultHttpClient http_client = new DefaultHttpClient();

	public Server() {
		serverUrls.put(ServerTarget.SaveSettings, SERVER_BASE_URL + "Settings");
		serverUrls.put(ServerTarget.Message, SERVER_BASE_URL + "Message");
		serverUrls.put(ServerTarget.Authenticate, "https://irssinotifier.appspot.com/_ah/login?continue=https://localhost/&auth=");
	}
	
	public boolean authenticate(String token) throws IOException {
        for(Cookie c : http_client.getCookieStore().getCookies())
            if(c.getName().equals("SACSID"))
                return true;

        try {
			// Don't follow redirects
	        http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
	        
	        HttpGet http_get = new HttpGet(serverUrls.get(ServerTarget.Authenticate) + token);
	        HttpResponse response;
	        response = http_client.execute(http_get);
	        
	        EntityUtils.toString(response.getEntity()); // read response to prevent warning
	        if(response.getStatusLine().getStatusCode() != 302)
                return false;
	        
	        for(Cookie cookie : http_client.getCookieStore().getCookies()) {
                if(cookie.getName().equals("SACSID"))
                    return true;
	        }
		} finally {
			if (http_client != null)
		        http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
		}
		return false;
	}
	
	public ServerResponse send(MessageToServer message, ServerTarget target) throws IOException {
		HttpPost httpPost = new HttpPost(serverUrls.get(target));
		httpPost.setEntity(new StringEntity(message.getHttpString()));
		
		HttpResponse response = http_client.execute(httpPost);
		int statusCode = response.getStatusLine().getStatusCode();
		String responseString = EntityUtils.toString(response.getEntity());
		
		ServerResponse serverResponse;
		serverResponse = new ServerResponse(statusCode == 200, responseString);

		return serverResponse;
	}

	public ServerResponse get(MessageToServer message, ServerTarget target) throws IOException {
		String url = serverUrls.get(target);
		url = buildUrlWithParameters(url, message.getMap());
		HttpGet httpGet = new HttpGet(url);

		HttpResponse response = http_client.execute(httpGet);
		int statusCode = response.getStatusLine().getStatusCode();
		String responseString = EntityUtils.toString(response.getEntity());
		
		ServerResponse serverResponse;
		if (target == ServerTarget.Message)
			serverResponse = new MessageServerResponse(statusCode == 200, responseString);
		else
			serverResponse = new ServerResponse(statusCode == 200, responseString);
		return serverResponse;
	}
	
	private static String buildUrlWithParameters(String url, Map<String, String> parameters){
	    if(!url.endsWith("?"))
	        url += "?";

	    List<NameValuePair> params = new ArrayList<NameValuePair>();

	    for (Entry<String, String> entry : parameters.entrySet()) {
		    params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
	    }

	    String paramString = URLEncodedUtils.format(params, "utf-8");

	    url += paramString;
	    return url;
	}

}
