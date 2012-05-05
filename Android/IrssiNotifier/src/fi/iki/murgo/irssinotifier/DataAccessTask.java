package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
		
		// clear old messages from feed
		List<IrcMessage> messages = new ArrayList<IrcMessage>();
		for (Channel ch : channels) {
			for (IrcMessage message : ch.getMessages()) {
				if (!message.getClearedFromFeed()) {
					messages.add(message);
				}
			}
		}
		
		final int maximumMessagesInFeed = 50;
		
		if (messages.size() > maximumMessagesInFeed) {
			Collections.sort(messages, new Comparator<IrcMessage>(){
				public int compare(IrcMessage lhs, IrcMessage rhs) {
					return lhs.getServerTimestamp().compareTo(rhs.getServerTimestamp());
				}});
			
			List<Long> toClearIds = new ArrayList<Long>();
			
			for (int i = 0; i < messages.size() - maximumMessagesInFeed; i++) {
				toClearIds.add(messages.get(i).getId());
				messages.get(i).setClearedFromFeed(true);
			}
			
			da.clearMessagesFromFeed(toClearIds);
		}

		return channels;
	}
	
	@Override
	protected void onPostExecute(List<Channel> result) {
		if (callback != null)
			callback.doStuff(result);
	}
}
