package fi.iki.murgo.irssinotifier;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;

import com.actionbarsherlock.view.ActionProvider;

public class SettingsActionProvider extends ActionProvider {
	
	private Context context;

	public SettingsActionProvider(Context context) {
		super(context);
		this.context = context;
	}

	@Override
	public View onCreateActionView() {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View view = layoutInflater.inflate(R.layout.actionbar_settings, null);
        
        ImageButton button = (ImageButton)view.findViewById(R.id.button_settings);

        button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
                Intent settingsActivity = new Intent(context, SettingsActivity.class);
                context.startActivity(settingsActivity);
			}
        });
        return view;
    }
	
	@Override
	public boolean onPerformDefaultAction() {
        Intent settingsActivity = new Intent(context, SettingsActivity.class);
        context.startActivity(settingsActivity);
        return true;
	}

}
