package fi.iki.murgo.irssinotifier;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;

@ReportsCrashes(formKey = "dHgyVnlwanVub3J5NnZaYnFheWVKUHc6MQ")
public class IrssiNotifierApplication extends Application {
	@Override
	public void onCreate() {
		ACRA.init(this);
		
		super.onCreate();
	}
}
