package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.android.vending.licensing.ILicenseResultListener;
import com.android.vending.licensing.ILicensingService;

import java.io.IOException;
import java.util.HashMap;

public class LicenseCheckingTask extends BackgroundAsyncTask<Void, Void, LicenseCheckingTask.LicenseCheckingStatus> {

    private static final String TAG = LicenseCheckingTask.class.getSimpleName();
    private ILicensingService service;

    private static final int LICENSED = 0x0;
    private static final int NOT_LICENSED = 0x1;
    private static final int LICENSED_OLD_KEY = 0x2;
    private Server server;

    public LicenseCheckingTask(Activity activity) {
        super(activity);
    }

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

        long startTime = System.currentTimeMillis();
        while (service == null && System.currentTimeMillis() - startTime < 10000) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return LicenseCheckingStatus.Error;
            }
        }

        if (service == null) {
            Log.e(TAG, "Could not connect to service in time");
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

        startTime = System.currentTimeMillis();
        Object signedDataObject = null;
        while (signedDataObject == null && System.currentTimeMillis() - startTime < 10000) {
            signedDataObject = licenseResponseData[1];
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return LicenseCheckingStatus.Error;
            }
        }

        if (signedDataObject == null) {
            Log.e(TAG, "Could not connect to service in time");
            return LicenseCheckingStatus.Error;
        }

        /* Response codes:
            private static final int LICENSED = 0x0;
            private static final int NOT_LICENSED = 0x1;
            private static final int LICENSED_OLD_KEY = 0x2;
            private static final int ERROR_NOT_MARKET_MANAGED = 0x3;
            private static final int ERROR_SERVER_FAILURE = 0x4;
            private static final int ERROR_OVER_QUOTA = 0x5;

            private static final int ERROR_CONTACTING_SERVER = 0x101;
            private static final int ERROR_INVALID_PACKAGE_NAME = 0x102;
            private static final int ERROR_NON_MATCHING_UID = 0x103;
         */
        int responseCode = (Integer) licenseResponseData[0];
        String signedData = (String) licenseResponseData[1];
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
        map.put("SignedData", makeBase64UrlSafe(signedData));
        map.put("Signature", makeBase64UrlSafe(signature));

        ServerResponse response;
        try {
            response = server.post(new MessageToServer(map), Server.ServerTarget.License);
        } catch (IOException e) {
            e.printStackTrace();
            return LicenseCheckingStatus.Error;
        }

        if (response == null || !response.wasSuccesful() || response.getResponseString() == null) {
            Log.w(TAG, "Licensing: Invalid response");
            return LicenseCheckingStatus.Error;
        }

        if (response.getResponseString().equals("OK")) {
            Log.i(TAG, "IrssiNotifier+ licensed succesfully!");
            Preferences prefs = new Preferences(activity);
            prefs.setLastLicenseTime(System.currentTimeMillis());
            prefs.setLicenseCount(prefs.getLicenseCount() + 1);

            return LicenseCheckingStatus.Allow;
        }

        Log.w(TAG, "Licensing: Disallowing, server said: " + response.getResponseString());
        return LicenseCheckingStatus.Disallow;
    }

    private String makeBase64UrlSafe(String data) {
        return data.replace("=", "%3D");
    }
}
