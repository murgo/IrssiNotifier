package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import android.widget.TextView;

public class MessagePagerAdapter extends PagerAdapter implements TitleProvider {
	private Context ctx;
	private List<Entry<Channel, List<IrcMessage>>> ircMessages;
	private final LayoutInflater layoutInflater;
    
	public MessagePagerAdapter(Context ctx, LayoutInflater layoutInflater) {
		super();
		this.ctx = ctx;
		this.layoutInflater = layoutInflater;
	}

	@Override
    public int getCount() {
		if (ircMessages == null)
			return 0;
		return ircMessages.size();
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
    	Channel channel = ircMessages.get(position).getKey();
    	List<IrcMessage> messages = ircMessages.get(position).getValue();

    	View channelView = layoutInflater.inflate(R.layout.channel, null);
    	// Stupid inflater is broken, gotta do this by hand
//    	LinearLayout outer = new LinearLayout(ctx);

    	TextView name = (TextView) channelView.findViewById(R.id.channel_name);
    	name.setText(channel.getName());

    	LinearLayout messageContainer = (LinearLayout) channelView.findViewById(R.id.message_container);
    	for (IrcMessage message : messages) {
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

	public void setIrcMessages(Map<Channel, List<IrcMessage>> ircMessages) {
		ArrayList<Map.Entry<Channel,List<IrcMessage>>> list = new ArrayList<Map.Entry<Channel,List<IrcMessage>>>();

		for (Entry<Channel, List<IrcMessage>> entry : ircMessages.entrySet()) {
			list.add(entry);
		}
		
		if (ircMessages.size() == 0) {
			Channel ch = new Channel();
			ch.setName(":(");
			ch.setOrder(0);
			
			IrcMessage msg = new IrcMessage();
			msg.setMessage("No messages!");
			msg.setNick("nobody");
			msg.setServerTimestamp(new Date().getTime());
			
			List<IrcMessage> l = new ArrayList<IrcMessage>();
			l.add(msg);
			
			Entry<Channel, List<IrcMessage>> entry = new SimpleEntry<Channel, List<IrcMessage>>(ch, l);
			list.add(entry);
		}
		
		Collections.sort(list, new Comparator<Map.Entry<Channel,List<IrcMessage>>>() {
			public int compare(Entry<Channel, List<IrcMessage>> lhs, Entry<Channel, List<IrcMessage>> rhs) {
				return ((Integer)lhs.getKey().getOrder()).compareTo(rhs.getKey().getOrder());
			}
		});
		
		this.ircMessages = list;
	}

	public String getTitle(int position) {
		return this.ircMessages.get(position).getKey().getName();
	}

}
