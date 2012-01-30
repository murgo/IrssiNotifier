package fi.iki.murgo.irssinotifier;

import java.io.IOException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

public class UserHelper {
	
	public Account[] getAccounts(Context context) 
	{
		AccountManager manager = AccountManager.get(context);
		Account[] accounts = manager.getAccountsByType("com.google");
		return accounts;
	}
	
	public String getAuthToken(Activity activity, Account account) throws OperationCanceledException, AuthenticatorException, IOException {
		AccountManager manager = AccountManager.get(activity);
		AccountManagerFuture<Bundle> future = manager.getAuthToken(account, "ac2dm", null, activity, null, null); // TODO
		Bundle token = future.getResult();
		return token.get(AccountManager.KEY_AUTHTOKEN).toString();
	}
	
}
