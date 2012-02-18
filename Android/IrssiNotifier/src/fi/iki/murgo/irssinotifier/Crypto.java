package fi.iki.murgo.irssinotifier;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import android.util.Base64;
import android.util.Log;

public class Crypto {
	private static final String TAG = Crypto.class.getSimpleName();

	public static String decrypt(String key, String payload) {
		try {
			byte[] encrypted = base64ToBytes(payload);
	
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
	
			// Remove OpenSSL Salted_
			byte[] salt = new byte[8];
			System.arraycopy(encrypted, 8, salt, 0, 8);
			
			SecretKeyFactory fact = SecretKeyFactory.getInstance("PBEWITHMD5AND128BITAES-CBC-OPENSSL", "BC");
			c.init(Cipher.DECRYPT_MODE, fact.generateSecret(new PBEKeySpec(key.toCharArray(), salt, 100)));
	
			// Decrypt the rest of the byte array (after stripping off the salt)
			byte[] data = c.doFinal(encrypted, 16, encrypted.length-16);
			String decrypted = new String(data, "utf8"); 
			return decrypted.substring(0, decrypted.length() - 1); // trim out trailing \n
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Unable to decrypt data", e);
		}
		return null;
	}

	private static byte[] base64ToBytes(String key) {
		return Base64.decode(key, Base64.DEFAULT);
	}
}
