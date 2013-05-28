
package fi.iki.murgo.irssinotifier;

import java.util.*;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class DataAccessTask extends AsyncTask<IrcMessage, Void, List<Channel>> {
    private static final String TAG = DataAccessTask.class.getName();

    private final Context context;
    private Callback<List<Channel>> callback;

    public DataAccessTask(Context context, Callback<List<Channel>> callback) {
        this.context = context;
        this.callback = callback;
    }

    @Override
    protected List<Channel> doInBackground(IrcMessage... params) {
        long start = System.nanoTime();
        DataAccess da = new DataAccess(context);
        if (params != null) {
            for (IrcMessage im : params) {
                da.handleMessage(im);
            }
        }

        List<Channel> channels = da.getChannels();
        HashMap<String, String> channelNames = new HashMap<String, String>(channels.size());
        for (Channel ch : channels) {
            channelNames.put(Long.toString(ch.getId()), ch.getName());
        }

        ArrayList<IrcMessage> feedMessagesProcessed = new ArrayList<IrcMessage>();
        List<IrcMessage> feedMessages = da.getFeedMessages();
        for (IrcMessage im : feedMessages) {
            if (!im.getClearedFromFeed()) {
                im.setChannel(channelNames.get(im.getChannel()));
                feedMessagesProcessed.add(im);
            }
        }

        Channel feedChannel = new Channel();
        feedChannel.setName(IrssiNotifierActivity.FEED);
        feedChannel.setMessages(feedMessagesProcessed);
        channels.add(0, feedChannel);

        double elapsed = (System.nanoTime() - start) / 1e6;
        Log.d(TAG, "Data accessing done, elapsed ms: " + elapsed);
        return channels;
    }

    @Override
    protected void onPostExecute(List<Channel> result) {
        if (callback != null)
            callback.doStuff(result);
    }
}
