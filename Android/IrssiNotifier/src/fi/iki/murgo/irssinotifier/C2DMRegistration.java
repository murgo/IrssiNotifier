package fi.iki.murgo.irssinotifier;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class C2DMRegistration {
	
	public static final String REGISTRATION_INTENT = "com.google.android.c2dm.intent.REGISTER";
    public static final String REGISTRATION_ID_INTENT_EXTRA_KEY = "registration_id";
    
    public static final String EMAIL_OF_SENDER = "lauri.harsila@gmail.com"; 

    public void registerForC2dm(Context context) {
        Intent registrationIntent = new Intent(REGISTRATION_INTENT);
        registrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
        registrationIntent.putExtra("sender", EMAIL_OF_SENDER);
        context.startService(registrationIntent);
    }
}