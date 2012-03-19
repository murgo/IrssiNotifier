package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;

public class AboutActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.about);
		
		LinearLayout base = (LinearLayout) findViewById(R.id.about_base);
		
		List<FancyTextView> ftvs = new ArrayList<FancyTextView>();

		PackageInfo pi = null;
		try {
			pi = getPackageManager().getPackageInfo(getPackageName(), 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		ftvs.add(new FancyTextView("About IrssiNotifier, version " + pi.versionName + " (" + pi.versionCode + ")" , this));
		ftvs.get(0).setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
		
		ftvs.add(new FancyTextView("Made by Lauri Härsilä, murgo@iki.fi." , this));
		ftvs.add(new FancyTextView("For instructions and help, please visit http://irssinotifier.appspot.com or join #IrssiNotifier @ IRCnet." , this));
		ftvs.add(new FancyTextView("IrssiNotifier was created entirely on my free time. If you have found IrssiNotifier useful, please consider donating at the web page (http://irssinotifier.appspot.com) to help keep project alive." , this));
		ftvs.add(new FancyTextView("Project is open source, see http://github.com/murgo/IrssiNotifier for more info." , this));
		ftvs.add(new FancyTextView("Thanks to everyone who has donated money or contributed source! Also thanks to everyone working on the Irssi team." , this));
		
		for (FancyTextView ftv : ftvs) {
			base.addView(ftv);
			ftv.start();
		}
	}
}
