
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
import org.apache.http.impl.cookie.BasicClientCookie2;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.util.Log;

public class Server {
    private static final String TAG = Server.class.getSimpleName();

    private boolean usingDevServer = true; // must be false when deploying

    public enum ServerTarget {
        SaveSettings,
        Test,
        FetchData,
        Authenticate,
        Message,
    }

    private Map<ServerTarget, String> serverUrls = new HashMap<ServerTarget, String>();

    private String baseServerUrl = "https://irssinotifier.appspot.com";

    private DefaultHttpClient http_client = new DefaultHttpClient();

    private static final int maxRetryCount = 3;

    public Server() {
        if (usingDevServer) {
            baseServerUrl = "http://10.0.2.2:8080";
        }

        serverUrls.put(ServerTarget.SaveSettings, baseServerUrl + "/API/Settings");
        serverUrls.put(ServerTarget.Message, baseServerUrl + "/API/Message");
        serverUrls.put(ServerTarget.Authenticate, baseServerUrl + "/_ah/login?continue=https://localhost/&auth=");
    }

    public boolean authenticate(String token) throws IOException {
        return authenticate(token, 0);
    }
    
    private boolean authenticate(String token, int retryCount) throws IOException {
        if (usingDevServer) {
            BasicClientCookie2 cookie = new BasicClientCookie2("dev_appserver_login", "irssinotifier@gmail.com:False:118887942201532232498");
            cookie.setDomain("10.0.2.2");
            cookie.setPath("/");
            http_client.getCookieStore().addCookie(cookie);

            return true;
        }

        if (retryCount >= maxRetryCount) {
            return false;
        }

        boolean success = doAuthenticate(token);
        if (success) {
            Log.v(TAG, "Succesfully logged in.");
            return true;
        }
        
        Log.w(TAG, "Login failed, retrying... Retry count " + (retryCount + 1));
        http_client = new DefaultHttpClient();

        return authenticate(token, retryCount + 1);
    }
    
    private boolean doAuthenticate(String token) throws IOException {
        if (checkCookie()) return true;

        try {
            Log.v(TAG, "Authenticating...");

            // Don't follow redirects
            http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);

            HttpGet http_get = new HttpGet(serverUrls.get(ServerTarget.Authenticate) + token);
            HttpResponse response;
            response = http_client.execute(http_get);

            EntityUtils.toString(response.getEntity()); // read response to prevent warning
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 302) {
                Log.w(TAG, "No redirect, login failed. Status code: " + statusCode);
                return false;
            } else {
                Log.v(TAG, "Redirected, OK. Status code: " + statusCode);
            }

            return checkCookie();
        } finally {
            if (http_client != null)
                http_client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
        }
    }

    private boolean checkCookie() {
        for (Cookie c : http_client.getCookieStore().getCookies()) {
            if (c.getName().equals("SACSID")) {
                Log.v(TAG, "Found SACSID cookie");
                return true;
            }
        }
        
        Log.w(TAG, "SACSID cookie not found");
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
        
        if (serverResponse.success) {
            Log.i(TAG, "Settings sent to server");
        } else {
            Log.e(TAG, "Unable to send settings! Response status code: " + statusCode + ", response string: " + responseString);
        }

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
        
        if (serverResponse.success) {
            Log.i(TAG, "Data fetched from server, target type " + target);
        } else {
            Log.e(TAG, "Unable to fetch data from server! Response status code: " + statusCode + ", response string: " + responseString);
        }
        
        return serverResponse;
    }

    private static String buildUrlWithParameters(String url, Map<String, String> parameters) {
        if (!url.endsWith("?"))
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
