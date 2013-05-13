package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.google.android.vending.licensing.*;

import java.io.IOException;
import java.util.HashMap;

public class LicenseCheckingTask extends BackgroundAsyncTask<Void, Void, LicenseCheckingTask.LicenseCheckingStatus> {

    private static final String TAG = LicenseCheckingTask.class.getSimpleName();
    private ILicensingService service;

    private static final int LICENSED = 0x0;
    private static final int NOT_LICENSED = 0x1;
    private static final int LICENSED_OLD_KEY = 0x2;
    private Server server;

    public LicenseCheckingTask(Activity activity, String titleText, String text) {
        super(activity, titleText, text);
    }

    public enum LicenseCheckingStatus {
        Allow,
        Disallow,
        Error,
    }

    @Override
    protected LicenseCheckingStatus doInBackground(Void... params) {
        server = new Server(activity);
        boolean authenticated = false;

        try {
            authenticated = server.authenticate();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!authenticated) {
            Log.e(TAG, "Unable to authenticate to server");
            return LicenseCheckingStatus.Error;
        }

        ServerResponse response;
        int nonce;
        try {
            response = server.get(new MessageToServer(), Server.ServerTarget.GetNonce);
            nonce = Integer.parseInt(response.getResponseString());
        } catch (IOException e) {
            e.printStackTrace();
            return LicenseCheckingStatus.Error;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return LicenseCheckingStatus.Error;
        }

        return checkLicense(nonce);
    }

    private LicenseCheckingStatus checkLicense(int nonce) {
        boolean bindResult = activity.bindService(new Intent("com.android.vending.licensing.ILicensingService"),
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder s) {
                        service = ILicensingService.Stub.asInterface(s);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        Log.w(TAG, "Service unexpectedly disconnected.");
                        service = null;
                    }
                },
                Context.BIND_AUTO_CREATE);

        if (!bindResult) {
            Log.e(TAG, "Could not bind to service.");
            return LicenseCheckingStatus.Error;
        }

        final Object[] licenseResponseData = new Object[3];
        try {
            Log.i(TAG, "Calling checkLicense");
            service.checkLicense(nonce, LicenseHelper.PACKAGE_PAID, new ILicenseResultListener.Stub() {
                        @Override
                        public void verifyLicense(int responseCode, String signedData, String signature) throws RemoteException {
                            licenseResponseData[0] = responseCode;
                            licenseResponseData[1] = signedData;
                            licenseResponseData[2] = signature;
                        }
                    });
        } catch (RemoteException e) {
            Log.w(TAG, "RemoteException in checkLicense call.", e);
            return LicenseCheckingStatus.Error;
        }

        long startTime = System.currentTimeMillis();
        String signedData = null;
        while (signedData == null || System.currentTimeMillis() - startTime > 10000) {
            signedData = (String) licenseResponseData[1];
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return LicenseCheckingStatus.Error;
            }
        }

        int responseCode = (Integer) licenseResponseData[0];
        String signature = (String) licenseResponseData[2];

        switch (responseCode) {
            case LICENSED:
            case LICENSED_OLD_KEY:
                return verifyResponseData(signedData, signature);
            case NOT_LICENSED:
                return LicenseCheckingStatus.Disallow;
            default:
                Log.e(TAG, "Some error: " + responseCode);
                return LicenseCheckingStatus.Error;
        }
    }

    private LicenseCheckingStatus verifyResponseData(String signedData, String signature) {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("SignedData", signedData);
        map.put("Signature", signature);

        ServerResponse response;
        try {
            response = server.post(new MessageToServer(map), Server.ServerTarget.VerifyPremium);
        } catch (IOException e) {
            e.printStackTrace();
            return LicenseCheckingStatus.Error;
        }

        if (response == null || !response.wasSuccesful() || response.getResponseString() == null) {
            Log.w(TAG, "Licensing: Invalid response");
            return LicenseCheckingStatus.Error;
        }

        if (response.getResponseString().equals("OK")) {
            return LicenseCheckingStatus.Allow;
        }

        Log.w(TAG, "Licensing: Disallowing, server said: " + response.getResponseString());
        return LicenseCheckingStatus.Disallow;
    }
}
