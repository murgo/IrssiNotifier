
package fi.iki.murgo.irssinotifier;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import android.util.Base64;
import android.util.Log;

public class Crypto {
    private static final String TAG = Crypto.class.getName();

    public static String decrypt(String key, String payload) throws CryptoException {
        try {
            byte[] payloadBytes = Base64.decode(payload, Base64.URL_SAFE);

            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");

            // Remove OpenSSL Salted_
            byte[] salt = new byte[8];
            System.arraycopy(payloadBytes, 8, salt, 0, 8);

            SecretKeyFactory fact = SecretKeyFactory.getInstance(
                    "PBEWITHMD5AND128BITAES-CBC-OPENSSL", "BC");
            c.init(Cipher.DECRYPT_MODE,
                    fact.generateSecret(new PBEKeySpec(key.toCharArray(), salt, 100)));

            // Decrypt the rest of the byte array (after stripping off the salt)
            byte[] data = c.doFinal(payloadBytes, 16, payloadBytes.length - 16);
            String decrypted = new String(data, "utf8");
            return decrypted.substring(0, decrypted.length() - 1); // trim out
                                                                   // trailing
                                                                   // \n
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Unable to decrypt data", e);
            throw new CryptoException("Unable to decrypt data", e);
        }
    }
}
