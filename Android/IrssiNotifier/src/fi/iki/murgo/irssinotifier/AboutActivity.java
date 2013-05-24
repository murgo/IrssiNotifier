
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

        PackageInfo pi;
        try {
            pi = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return;
        }

        CharSequence appName = getResources().getText(R.string.app_name);

        ftvs.add(new FancyTextView("About " + appName + ", version " + pi.versionName, this));
        ftvs.get(0).setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

        ftvs.add(new FancyTextView(getText(R.string.created_by), this));

        boolean plus = LicenseHelper.isPlusVersion(this);

        if (plus) {
            ftvs.add(new FancyTextView(getText(R.string.thanks_for_support), this));
        }

        ftvs.add(new FancyTextView(getText(R.string.instructions), this));

        if (!plus) {
            ftvs.add(new FancyTextView(getText(R.string.donate), this));
        }

        ftvs.add(new FancyTextView(getText(R.string.open_source), this));
        ftvs.add(new FancyTextView(getText(R.string.thanks_donators), this));

        for (FancyTextView ftv : ftvs) {
            base.addView(ftv);
            ftv.start();
        }
    }
}
