
package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
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
    private static final String TAG = Server.class.getName();

    private final Preferences preferences;
    private final Activity activity;

    private boolean usingDevServer = false; // must be false when deploying

    public enum ServerTarget {
        SaveSettings,
        Test,
        FetchData,
        Authenticate,
        Message,
        WipeSettings,
        GetNonce,
        License,
    }

    private Map<ServerTarget, String> serverUrls = new HashMap<ServerTarget, String>();

    private DefaultHttpClient http_client = new DefaultHttpClient(); // THIS WILL NO LONGER WORK WITH API LEVEL 28+

    private static final int maxRetryCount = 2;

    public Server(Activity activity) {
        this.activity = activity;
        this.preferences = new Preferences(activity);
        String baseServerUrl = "https://irssinotifier.appspot.com";

        if (usingDevServer) {
            baseServerUrl = "http://10.0.2.2:8080";
        }

        serverUrls.put(ServerTarget.SaveSettings, baseServerUrl + "/API/Settings");
        serverUrls.put(ServerTarget.WipeSettings, baseServerUrl + "/API/Wipe");
        serverUrls.put(ServerTarget.Message, baseServerUrl + "/API/Message");
        serverUrls.put(ServerTarget.Authenticate, baseServerUrl + "/_ah/login?continue=https://localhost/&auth=");
        serverUrls.put(ServerTarget.GetNonce, baseServerUrl + "/API/Nonce");
        serverUrls.put(ServerTarget.License, baseServerUrl + "/API/License");
    }

    public boolean authenticate() throws IOException {
        return authenticate(0);
    }
    
    private boolean authenticate(int retryCount) throws IOException {
        if (usingDevServer) {
            BasicClientCookie2 cookie = new BasicClientCookie2("dev_appserver_login", "irssinotifier@gmail.com:False:118887942201532232498");
            cookie.setDomain("10.0.2.2");
            cookie.setPath("/");
            http_client.getCookieStore().addCookie(cookie);

            return true;
        }

        String token = preferences.getAuthToken();
        try {
            if (token == null) {
                String accountName = preferences.getAccountName();
                if (accountName == null) {
                    return false;
                }

                token = generateToken(accountName);
                preferences.setAuthToken(token);
            }

            boolean success = doAuthenticate(token);
            if (success) {
                Log.v(TAG, "Succesfully logged in.");
                return true;
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Unable to send settings: " + e.toString());
            e.printStackTrace();
            preferences.setAccountName(null); // reset because authentication or unforeseen error
            return false;
        }

        Log.w(TAG, "Login failed, retrying... Retry count " + (retryCount + 1));
        http_client = new DefaultHttpClient();
        preferences.setAuthToken(null);

        if (retryCount >= maxRetryCount) {
            preferences.setAccountName(null); // reset because it's not accepted by the server
            return false;
        }

        return authenticate(retryCount + 1);
    }

    private String generateToken(String accountName) throws OperationCanceledException, AuthenticatorException, IOException {
        UserHelper uf = new UserHelper();
        return uf.getAuthToken(activity, new Account(accountName, UserHelper.ACCOUNT_TYPE));
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
                if (!c.isExpired(new Date())) {
                    return true;
                } else {
                    Log.w(TAG, "SACSID cookie expired");
                }
            }
        }
        
        Log.w(TAG, "No valid SACSID cookie found");
        http_client.getCookieStore().clear();
        return false;
    }

    public ServerResponse post(MessageToServer message, ServerTarget target) throws IOException {
        HttpPost httpPost = new HttpPost(serverUrls.get(target));
        httpPost.setEntity(new StringEntity(message.getHttpString()));

        HttpResponse response = http_client.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();
        String responseString = EntityUtils.toString(response.getEntity());

        ServerResponse serverResponse;
        serverResponse = new ServerResponse(statusCode, responseString);

        if (serverResponse.wasSuccesful()) {
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
            serverResponse = new MessageServerResponse(statusCode, responseString);
        else
            serverResponse = new ServerResponse(statusCode, responseString);
        
        if (serverResponse.wasSuccesful()) {
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
