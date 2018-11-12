
package fi.iki.murgo.irssinotifier;

import java.io.IOException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class UserHelper {

    public static final String ACCOUNT_TYPE = "com.google";
    public static final int GET_ACCOUNTS_PERMISSION_REQUEST = 0;

    public Account[] getAccounts(Context context)
    {
        // Currently not used in favor of AccountPicker.newChooseAccountIntent, but all this should be replaced
        // https://developers.google.com/identity/sign-in/android/migration-guide#migrate_to_the_id_token_flow

        /*
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity)context, new String[]{android.Manifest.permission.GET_ACCOUNTS}, GET_ACCOUNTS_PERMISSION_REQUEST);
            return null;
        }

        AccountManager manager = AccountManager.get(context);
        return manager.getAccountsByType(ACCOUNT_TYPE);
        */
        return null;
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
