
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

    public static final String ACCOUNT_TYPE = "com.google";

    public Account[] getAccounts(Context context)
    {
        AccountManager manager = AccountManager.get(context);
        return manager.getAccountsByType(ACCOUNT_TYPE);
    }

    public String getAuthToken(Activity activity, Account account) throws OperationCanceledException, AuthenticatorException, IOException {
        AccountManager manager = AccountManager.get(activity);
        String token = buildToken(manager, account, activity);
        manager.invalidateAuthToken(account.type, token);
        return buildToken(manager, account, activity);
    }

    private String buildToken(AccountManager manager, Account account, Activity activity) throws OperationCanceledException, AuthenticatorException, IOException {
        AccountManagerFuture<Bundle> future = manager.getAuthToken(account, "ah", null, activity, null, null); // ah is app engine
        Bundle token = future.getResult();
        return token.get(AccountManager.KEY_AUTHTOKEN).toString();
    }

}
