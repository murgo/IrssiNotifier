package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
	private ChannelMode channelMode;
	
	private static final int FeedColor = 0xffffffff;
	private static final int PrivateColor = 0xFFAF0040; 
	private static final int ChannelColor = 0xFF0072B9; 
    
	public MessagePagerAdapter(Context ctx, LayoutInflater layoutInflater) {
		super();
		this.ctx = ctx;
		this.layoutInflater = layoutInflater;
	}

	@Override
    public int getCount() {
		if (channels == null) {
			return 0;
		}
		
		if (channels.size() == 0) return 1;
		if (channelMode == ChannelMode.Feed) return 1;
		if (channelMode == ChannelMode.Channels) return channels.size();
		return channels.size() + 1; // if (channelMode == ChannelMode.Both)
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
    	View view;
    	
		if (channels.size() == 0) {
			view = createEmptyChannel();
		} else if (channelMode == ChannelMode.Channels) {
			view = createChannel(position);
		} else if (position == 0) {
			view = createFeed();
		} else {
			view = createChannel(position - 1);
		}

		((ViewPager) collection).addView(view);
    	return view;
    }

	private View createEmptyChannel() {
		View channelView = layoutInflater.inflate(R.layout.channel, null);
		TextView name = (TextView) channelView.findViewById(R.id.channel_name);
		name.setText("Empty");
	
		LinearLayout messageContainer = (LinearLayout) channelView.findViewById(R.id.message_container);
		TextView tv = new TextView(ctx);
		tv.setText("No IRC hilights yet!");
		tv.setTypeface(Typeface.MONOSPACE);
		
		messageContainer.addView(tv);

		return channelView;
	}

	private View createFeed() {
		List<IrcMessage> messages = new ArrayList<IrcMessage>();
		for (Channel ch : channels) {
			for (IrcMessage message : ch.getMessages()) {
				if (!message.getClearedFromFeed()) {
					messages.add(message);
				}
			}
		}
		
		Collections.sort(messages, new Comparator<IrcMessage>(){
			public int compare(IrcMessage lhs, IrcMessage rhs) {
				return lhs.getServerTimestamp().compareTo(rhs.getServerTimestamp());
			}});
		
    	View channelView = layoutInflater.inflate(R.layout.channel, null);
		TextView name = (TextView) channelView.findViewById(R.id.channel_name);
		name.setText("Feed");
		name.setTextColor(FeedColor);

		LinearLayout messageContainer = (LinearLayout) channelView.findViewById(R.id.message_container);
		String lastChannel = "";
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
			
			if (!message.getLogicalChannel().equals(lastChannel)) {
				TextView tv = new TextView(ctx);
				lastChannel = message.getLogicalChannel();
				
				tv.setText(lastChannel);
				tv.setTypeface(Typeface.MONOSPACE);
				tv.setTextSize(tv.getTextSize() * 1.05f);
				if (lastChannel.startsWith("#")) {
					// some channels might not start with #, but they're really rare
					tv.setTextColor(ChannelColor);
				} else {
					tv.setTextColor(PrivateColor);
				}				
				messageContainer.addView(tv);
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
	
		return channelView;
	}

	private View createChannel(int position) {
    	Channel channel = channels.get(position);
    	List<IrcMessage> messages = channel.getMessages();

    	View channelView = layoutInflater.inflate(R.layout.channel, null);
		TextView name = (TextView) channelView.findViewById(R.id.channel_name);
		name.setText(channel.getName());
		if (channel.getName().startsWith("#")) {
			// some channels might not start with #, but they're really rare
			name.setTextColor(ChannelColor);
		} else {
			name.setTextColor(PrivateColor);
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
		this.channels = ircMessages;
	}

	public String getTitle(int position) {
		if (channels.size() == 0) return "";
		if (channelMode == ChannelMode.Channels) return channels.get(position).getName();
		if (position == 0) return "Feed";
		return channels.get(position - 1).getName(); // if (channelMode == ChannelMode.Both)
	}

}
