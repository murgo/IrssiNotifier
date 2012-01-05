package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class IrssiNotifierActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        createUi();
        
        // Register and send registration if needed here
    }

	private void createUi() {
        setContentView(R.layout.main);
        Button b = (Button)findViewById(R.id.buttonRegister);
        
        final Activity activity = this;
        
        b.setOnClickListener(new OnClickListener() {
        	// TODO removeme
			@Override
			public void onClick(View v) {
				C2DMRegistration reg = new C2DMRegistration();
				reg.registerForC2dm(activity);
			}});
	}
}