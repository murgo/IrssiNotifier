package fi.iki.murgo.irssinotifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

public class Asd {
	public static void temp(String id) {
	    // Create a new HttpClient and Post Header
	    HttpClient httpclient = new DefaultHttpClient();
	    HttpPost httppost = new HttpPost("https://android.apis.google.com/c2dm/send");

	    try {
	        // Add your data
	        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
	        nameValuePairs.add(new BasicNameValuePair("registration_id", id));
	        nameValuePairs.add(new BasicNameValuePair("collapse_key", id));
	        nameValuePairs.add(new BasicNameValuePair("registration_id", id));
	        nameValuePairs.add(new BasicNameValuePair("registration_id", id));
	        nameValuePairs.add(new BasicNameValuePair("registration_id", id));
	        
	        
	        httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

	        // Execute HTTP Post Request
	        HttpResponse response = httpclient.execute(httppost);

	    } catch (ClientProtocolException e) {
	        // TODO Auto-generated catch block
	    } catch (IOException e) {
	        // TODO Auto-generated catch block
		} 
	}
	
	public static void temp2(Context context) {
		try {
		AccountManager manager = AccountManager.get(context);
		Account[] accounts = manager.getAccounts();
		System.out.println(accounts);
		} catch (Exception ex) {
			System.out.println(ex);
			
		}
	}
}
