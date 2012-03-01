package fi.iki.murgo.irssinotifier;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;

import com.actionbarsherlock.view.ActionProvider;

public class IrssiConnectbotActionProvider extends ActionProvider {
	private Context context;

	public IrssiConnectbotActionProvider(Context context) {
		super(context);
		this.context = context;
	}

	@Override
	public View onCreateActionView() {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View view = layoutInflater.inflate(R.layout.actionbar_irssi_connectbot, null);
        
        ImageButton button = (ImageButton)view.findViewById(R.id.button_launch_irssi);

        button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
                Intent settingsActivity = new Intent(context.getPackageManager().getLaunchIntentForPackage("org.woltage.irssiconnectbot"));
                context.startActivity(settingsActivity);
			}
        });
        return view;
    }
	
	@Override
	public boolean onPerformDefaultAction() {
        Intent settingsActivity = new Intent(context.getPackageManager().getLaunchIntentForPackage("org.woltage.irssiconnectbot"));
        context.startActivity(settingsActivity);
        return true;
	}

}
