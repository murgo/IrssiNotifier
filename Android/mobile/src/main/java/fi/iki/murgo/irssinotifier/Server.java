
package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Server {
    private static final String TAG = Server.class.getName();

    private final Preferences preferences;
    private final Activity activity;

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
        String baseServerUrl = "https://irssinotifier-stage.appspot.com";

        serverUrls.put(ServerTarget.SaveSettings, baseServerUrl + "/API/Settings");
        serverUrls.put(ServerTarget.WipeSettings, baseServerUrl + "/API/Wipe");
        serverUrls.put(ServerTarget.Message, baseServerUrl + "/API/Message");
        serverUrls.put(ServerTarget.Authenticate, baseServerUrl + "/oauth2callback?code=");
        serverUrls.put(ServerTarget.GetNonce, baseServerUrl + "/API/Nonce");
        serverUrls.put(ServerTarget.License, baseServerUrl + "/API/License");
    }

    /*
    public boolean authenticate() throws IOException {
        return authenticate(0);
    }

    private boolean authenticate(int retryCount) throws IOException {
        String token = preferences.getAuthToken();

        try {
            if (token == null) {
                String accountName = preferences.getAccountName();
                if (accountName == null) {
                    return false;
                }

                UserHelper uf = new UserHelper();
                token = uf.getAuthToken(activity, new Account(accountName, UserHelper.ACCOUNT_TYPE));
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
     */

        /*
    private boolean doAuthenticate(String token) throws IOException {
        Request request = new Request.Builder()
                .url(serverUrls.get(ServerTarget.Authenticate) + token)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            Log.i(TAG,"response " + response.code() + " " + response.body());
            int statusCode = response.code();
            if (statusCode != 200) {
                Log.w(TAG, "No redirect, login failed. Status code: " + statusCode);
                return true; //return false;
            } else {
                Log.v(TAG, "Got 200 OK.");
            }
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
         */

    public ServerResponse post(MessageToServer message, ServerTarget target) throws IOException {

        Request request = new Request.Builder()
                .post(message.getRequestBody())
                .addHeader("Authorization", "Bearer " + preferences.getAuthToken())
                .url(serverUrls.get(target))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();
            String responseString;
            if (response.isSuccessful()) {
                responseString = response.body().string();
            } else {
                responseString = response.message();
            }
            ServerResponse serverResponse;
            serverResponse = new ServerResponse(statusCode, responseString);

            if (serverResponse.wasSuccesful()) {
                Log.i(TAG, "Settings sent to server");
            } else {
                Log.e(TAG, "Unable to send settings! Response status code: " + statusCode + ", response string: " + responseString);
            }

            return serverResponse;
        }
    }

    public ServerResponse get(MessageToServer message, ServerTarget target) throws IOException {
        String url = serverUrls.get(target);
        url = message.getUrlWithParameters(url);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + preferences.getAuthToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();
            String responseString;
            if (response.isSuccessful()) {
                responseString = response.body().string();
            } else {
                responseString = response.message();
            }
            ServerResponse serverResponse;
            serverResponse = new ServerResponse(statusCode, responseString);

            if (serverResponse.wasSuccesful()) {
                Log.i(TAG, "Settings sent to server");
            } else {
                Log.e(TAG, "Unable to send settings! Response status code: " + statusCode + ", response string: " + responseString);
            }

            return serverResponse;
        }
    }
}
