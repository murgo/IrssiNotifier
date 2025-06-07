
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
    public static final int GET_ACCOUNTS_PERMISSION_REQUEST = 0;

    public String getAuthToken(Activity activity, Account account) throws OperationCanceledException, AuthenticatorException, IOException {
        AccountManager manager = AccountManager.get(activity);
        String token = buildToken(manager, account, activity);
        manager.invalidateAuthToken(account.type, token);
        return buildToken(manager, account, activity);
    }

    private String buildToken(AccountManager manager, Account account, Activity activity) throws OperationCanceledException, AuthenticatorException, IOException {
        AccountManagerFuture<Bundle> future = manager.getAuthToken(account, "oauth2:https://www.googleapis.com/auth/userinfo.email", null, activity, null, null);
        Bundle token = future.getResult();
        return token.get(AccountManager.KEY_AUTHTOKEN).toString();
    }

}
