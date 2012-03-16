package fi.iki.murgo.irssinotifier;

import java.util.List;
import android.content.Context;
import android.os.AsyncTask;

public class DataAccessTask extends AsyncTask<IrcMessage, Void, List<Channel>> {

	private final Context context;
	private Callback<List<Channel>> callback;

	public DataAccessTask(Context context, Callback<List<Channel>> callback) {
		this.context = context;
		this.callback = callback;
	}
	
	@Override
	protected List<Channel> doInBackground(IrcMessage... params) {
		DataAccess da = new DataAccess(context);
		if (params != null) {
			for (IrcMessage im : params) {
				da.handleMessage(im);
			}
		}
		
		List<Channel> channels = da.getChannels();
		return channels;
	}
	
	@Override
	protected void onPostExecute(List<Channel> result) {
		if (callback != null)
			callback.doStuff(result);
	}
}
