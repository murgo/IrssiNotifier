package fi.iki.murgo.irssinotifier.test;

import fi.iki.murgo.irssinotifier.Crypto;
import fi.iki.murgo.irssinotifier.IrssiNotifierActivity;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

public class CryptoTests extends ActivityInstrumentationTestCase2 {
	
	public CryptoTests() {
		super("fi.iki.murgo.irssinotifier", IrssiNotifierActivity.class);
	}

	public void testCrypto() {
		String decrypted = null;
		try {
			decrypted = Crypto.decrypt("kissa13", "U2FsdGVkX1/N19CS3lxp7x6dSO0/LjfXfbbzvQrHpgg=");
		} catch (Exception e) {
			e.printStackTrace();
			assertNotNull("" + e.getMessage() + Log.getStackTraceString(e));
		}
		
		assertEquals("Foobar", decrypted);
	}
}
