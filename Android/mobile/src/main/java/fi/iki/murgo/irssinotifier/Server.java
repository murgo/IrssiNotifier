
package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.cookie.BasicClientCookie2;
import org.apache.http.message.BasicNameValuePair;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Server {
    private static final String TAG = Server.class.getName();

    private final Preferences preferences;
    private final Activity activity;

    private boolean usingDevServer = false; // must be false when deploying
    private String authenticationCookie = null;

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


    private OkHttpClient httpClient;

    private static final int maxRetryCount = 2;

    public Server(Activity activity) {
        this.httpClient = new OkHttpClient().newBuilder().followRedirects(false).build();
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
            //FIXME: httpClient.getCookieStore().addCookie(cookie);

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
        httpClient = new OkHttpClient();
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

        Request request = new Request.Builder()
                .url(serverUrls.get(ServerTarget.Authenticate) + token)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            Log.i(TAG,"response " + response.code() + " " + response.body());
            int statusCode = response.code();
            if (statusCode != 302) {
                Log.w(TAG, "No redirect, login failed. Status code: " + statusCode);
                return false;
            } else {
                Log.v(TAG, "Redirected, OK. Status code: " + statusCode);
            }
            storeCookie(response);
            return checkCookie();
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    private void storeCookie(Response response) {
        authenticationCookie = response.header("Set-Cookie");
    }

    private boolean checkCookie() {
        return authenticationCookie != null && !authenticationCookie.isEmpty();
        /*FIXME
        for (Cookie c : httpClient.getCookieStore().getCookies()) {
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
        httpClient.getCookieStore().clear();*/
    }

    public ServerResponse post(MessageToServer message, ServerTarget target) throws IOException {

        Request request = new Request.Builder()
                .post(message.getRequestBody())
                .header("cookie", authenticationCookie)
                .url(serverUrls.get(target))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();
            String responseString = response.body().string();
            ServerResponse serverResponse;
            serverResponse = new ServerResponse(statusCode, responseString);

            if (serverResponse.wasSuccesful()) {
                Log.i(TAG, "Settings sent to server");
            } else {
                Log.e(TAG, "Unable to send settings! Response status code: " + statusCode + ", response string: " + responseString);
            }

            return serverResponse;
        }
        catch (Exception e) {
            Log.e(TAG,"TROELOR");
        }
        /*
        HttpPost httpPost = new HttpPost(serverUrls.get(target));
        httpPost.setEntity(new StringEntity(message.getHttpString()));

        HttpResponse response = httpClient.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();
        String responseString = EntityUtils.toString(response.getEntity());

        ServerResponse serverResponse;
        serverResponse = new ServerResponse(statusCode, responseString);

        if (serverResponse.wasSuccesful()) {
            Log.i(TAG, "Settings sent to server");
        } else {
            Log.e(TAG, "Unable to send settings! Response status code: " + statusCode + ", response string: " + responseString);
        }

        return serverResponse;*/
        return null; //FIXME
    }

    public ServerResponse get(MessageToServer message, ServerTarget target) throws IOException {
        String url = serverUrls.get(target);
        url = message.getUrlWithParameters(url);
        Request request = new Request.Builder()
                .url(url)
                .header("cookie", authenticationCookie)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();
            String responseString = response.body().string();
            ServerResponse serverResponse;
            serverResponse = new ServerResponse(statusCode, responseString);

            if (serverResponse.wasSuccesful()) {
                Log.i(TAG, "Settings sent to server");
            } else {
                Log.e(TAG, "Unable to send settings! Response status code: " + statusCode + ", response string: " + responseString);
            }

            return serverResponse;
        }

        /*
        String url = serverUrls.get(target);
        url = buildUrlWithParameters(url, message.getMap());
        HttpGet httpGet = new HttpGet(url);

        HttpResponse response = httpClient.execute(httpGet);
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

        return serverResponse;*/
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
