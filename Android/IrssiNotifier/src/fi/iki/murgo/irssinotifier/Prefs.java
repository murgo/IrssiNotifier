package fi.iki.murgo.irssinotifier;

import android.content.Context;

public class Prefs {
	public static final String PREFERENCES_NAME = "IrssiNotifierPreferences";
	public static final int PREFERENCES_MODE = Context.MODE_PRIVATE;
	
	private static final String PREFERENCES_PREFIX = "IrssiNotifier";
	
	public static final String FIRST_TIME = PREFERENCES_PREFIX + "FirstTime";
	public static final String REGISTRATION_ID = PREFERENCES_PREFIX + "RegisterId";
}
