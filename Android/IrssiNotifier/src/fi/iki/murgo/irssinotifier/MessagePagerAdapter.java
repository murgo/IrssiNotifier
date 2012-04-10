package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.viewpagerindicator.TitleProvider;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MessagePagerAdapter extends PagerAdapter implements TitleProvider {
	private Context ctx;
	private List<Channel> channels;
	private final LayoutInflater layoutInflater;
    
	public MessagePagerAdapter(Context ctx, LayoutInflater layoutInflater) {
		super();
		this.ctx = ctx;
		this.layoutInflater = layoutInflater;
	}

	@Override
    public int getCount() {
		if (channels == null)
			return 0;
		return channels.size();
    }

/**
 * Create the page for the given position.  The adapter is responsible
 * for adding the view to the container given here, although it only
 * must ensure this is done by the time it returns from
 * {@link #finishUpdate()}.
 *
 * @param container The containing View in which the page will be shown.
 * @param position The page position to be instantiated.
 * @return Returns an Object representing the new page.  This does not
 * need to be a View, but can be some other container of the page.
 */
    @Override
    public Object instantiateItem(View collection, int position) {
    	Channel channel = channels.get(position);
    	List<IrcMessage> messages = channel.getMessages();

    	View channelView = layoutInflater.inflate(R.layout.channel, null);
    	
    	TextView name = (TextView) channelView.findViewById(R.id.channel_name);
    	name.setText(channel.getName());
    	if (channel.getName().startsWith("#")) {
    		// some channels might not start with #, but they're really rare
    		name.setTextColor(0xFF6060FF);
    	} else {
    		name.setTextColor(0xFFFF6060);
    	}

    	LinearLayout messageContainer = (LinearLayout) channelView.findViewById(R.id.message_container);
    	boolean lastShown = false;
    	for (IrcMessage message : messages) {
    		if (!message.isShown()) {
    			if (!lastShown) {
    				lastShown = true;
    				TextView tvEmpty = new TextView(ctx);
    				tvEmpty.setText("--");
    				messageContainer.addView(tvEmpty);
    			}
    		}
    		
			TextView tv = new TextView(ctx);
			String s = message.getServerTimestampAsString() + " (" + message.getNick() + ") " + message.getMessage();
			final SpannableString ss = new SpannableString(s);
			Linkify.addLinks(ss, Linkify.ALL);
			tv.setText(ss);
			tv.setTypeface(Typeface.MONOSPACE);
			tv.setAutoLinkMask(Linkify.ALL);
			tv.setLinksClickable(true);
			tv.setMovementMethod(LinkMovementMethod.getInstance());
			
			messageContainer.addView(tv);
    	}
    	
    	final ScrollView sv = (ScrollView)channelView.findViewById(R.id.scroll_view);
    	sv.post(new Runnable() {
			public void run() {
				sv.fullScroll(ScrollView.FOCUS_DOWN);
			}
		});

		((ViewPager) collection).addView(channelView);
		return channelView;
    }

/**
 * Remove a page for the given position.  The adapter is responsible
 * for removing the view from its container, although it only must ensure
 * this is done by the time it returns from {@link #finishUpdate()}.
 *
 * @param container The containing View from which the page will be removed.
 * @param position The page position to be removed.
 * @param object The same object that was returned by
 * {@link #instantiateItem(View, int)}.
 */
    @Override
    public void destroyItem(View collection, int position, Object view) {
        ((ViewPager) collection).removeView((LinearLayout) view);
    }

    
    
    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == ((LinearLayout)object);
    }

    
/**
 * Called when the a change in the shown pages has been completed.  At this
 * point you must ensure that all of the pages have actually been added or
 * removed from the container as appropriate.
 * @param container The containing View which is displaying this adapter's
 * page views.
 */
    @Override
    public void finishUpdate(View arg0) {
    	
    }

    @Override
    public void restoreState(Parcelable arg0, ClassLoader arg1) {
    	
    }

    @Override
    public Parcelable saveState() {
            return null;
    }

	public void setChannels(List<Channel> ircMessages) {
		if (ircMessages.size() == 0) {
			List<Channel> channelList = new ArrayList<Channel>();
			Channel ch = new Channel();
			channelList.add(ch);
			
			ch.setName("Empty");
			ch.setOrder(0);
			
			IrcMessage msg = new IrcMessage();
			msg.setMessage("No IRC hilights yet!");
			msg.setNick("nobody");
			msg.setServerTimestamp(new Date().getTime());
			
			List<IrcMessage> messageList = new ArrayList<IrcMessage>();
			messageList.add(msg);
			ch.setMessages(messageList);
			
			this.channels = channelList;
			return;
		}
		
		this.channels = ircMessages;
	}

	public String getTitle(int position) {
		return this.channels.get(position).getName();
	}

}
