package fi.iki.murgo.irssinotifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.os.AsyncTask;

public class DataAccessTask extends AsyncTask<IrcMessage, Void, Map<Channel, List<IrcMessage>>> {

	private final Context context;
	private Callback<Map<Channel, List<IrcMessage>>> callback;

	public DataAccessTask(Context context, Callback<Map<Channel, List<IrcMessage>>> callback) {
		this.context = context;
		this.callback = callback;
	}
	
	@Override
	protected Map<Channel, List<IrcMessage>> doInBackground(IrcMessage... params) {
		DataAccess da = new DataAccess(context);
		if (params != null) {
			for (IrcMessage im : params) {
				da.HandleMessage(im);
			}
		}
		
		List<Channel> channels = da.getChannels();
		Map<Channel, List<IrcMessage>> data = new HashMap<Channel, List<IrcMessage>>();
		for (Channel channel : channels) {
			data.put(channel, da.getMessagesForChannel(channel));
		}
		
		return data;
	}
	
	@Override
	protected void onPostExecute(Map<Channel, List<IrcMessage>> result) {
		if (callback != null)
			callback.doStuff(result);
	}
}
