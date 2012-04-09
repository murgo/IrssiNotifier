package fi.iki.murgo.irssinotifier;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;

public abstract class BackgroundAsyncTask<A, B, C> extends AsyncTask<A, B, C> {

	private ProgressDialog dialog;
	protected final Activity activity;
	private final String titleText;
	private final String text;
	private Callback<C> callback;

	public BackgroundAsyncTask(Activity activity, String titleText, String text) {
		this.activity = activity;
		this.titleText = titleText;
		this.text = text;
	}
	
	@Override
	protected void onPreExecute() {
		if (dialog == null)
			setDialog(ProgressDialog.show(activity, titleText, text, true));
	}
	
	@Override
	protected void onPostExecute(C result) {
		try {
			getDialog().dismiss();
		} catch (Exception e) {
	        // nothing
	    }
		
		if (callback != null) {
			callback.doStuff(result);
		}
	}

	public Callback<C> getCallback() {
		return callback;
	}
	
	public void setCallback(Callback<C> callback) {
		this.callback = callback;
	}

	public ProgressDialog getDialog() {
		return dialog;
	}

	public void setDialog(ProgressDialog dialog) {
		this.dialog = dialog;
	};
}
