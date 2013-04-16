
package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MessagePagerAdapter extends PagerAdapter {
    private List<Channel> channels;
    private final LayoutInflater layoutInflater;

    public MessagePagerAdapter(LayoutInflater layoutInflater) {
        super();
        this.layoutInflater = layoutInflater;
    }

    @Override
    public int getCount() {
        if (channels == null) {
            return 0;
        }

        if (channels.size() == 0)
            return 1;
        return channels.size() + 1;
    }

    @Override
    public Object instantiateItem(ViewGroup collection, int position) {
        View view;

        if (channels.size() == 0) {
            view = createEmptyChannel();
        } else if (position == 0) {
            view = createFeed();
        } else {
            view = createChannel(position - 1);
        }

        collection.addView(view);
        return view;
    }
    
    private View createEmptyChannel() {
        View channelView = layoutInflater.inflate(R.layout.channel, null);

        LinearLayout messageContainer = (LinearLayout) channelView.findViewById(R.id.message_container);
        TextView tv = (TextView) layoutInflater.inflate(R.layout.message, null);
        tv.setText("No IRC hilights yet!");

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

        Collections.sort(messages, new Comparator<IrcMessage>() {
            public int compare(IrcMessage lhs, IrcMessage rhs) {
                return lhs.getServerTimestamp().compareTo(rhs.getServerTimestamp());
            }
        });

        View channelView = layoutInflater.inflate(R.layout.channel, null);

        LinearLayout messageContainer = (LinearLayout) channelView.findViewById(R.id.message_container);
        String lastChannel = "";
        String lastDate = "";
        LinearLayout feedChannel = (LinearLayout)layoutInflater.inflate(R.layout.feed_channel, null);
        for (IrcMessage message : messages) {
            boolean dateChange = false;

            if (!message.getServerTimestampAsPrettyDate().equals(lastDate)) {
                feedChannel = (LinearLayout)layoutInflater.inflate(R.layout.feed_channel, null);
                messageContainer.addView(feedChannel);

                dateChange = true;
                lastDate = message.getServerTimestampAsPrettyDate();

                TextView tv = (TextView) layoutInflater.inflate(R.layout.datechange_header, null);
                tv.setText(lastDate);
                feedChannel.addView(tv);
            }

            if (dateChange || !message.getLogicalChannel().equals(lastChannel)) {
                TextView tv;
                if (!dateChange)
                    tv = (TextView) layoutInflater.inflate(R.layout.channel_header_paddingtop, null);
                else
                    tv = (TextView) layoutInflater.inflate(R.layout.channel_header, null);
                lastChannel = message.getLogicalChannel();
                tv.setText(lastChannel);
                
                feedChannel.addView(tv);
            }

            TextView tv = (TextView) layoutInflater.inflate(R.layout.message, null);
            String s = message.getServerTimestampAsString() + " (" + message.getNick() + ") "
                    + message.getMessage();
            final SpannableString ss = new SpannableString(s);
            Linkify.addLinks(ss, Linkify.ALL);
            tv.setText(ss);
            tv.setAutoLinkMask(Linkify.ALL);
            tv.setLinksClickable(true);
            tv.setMovementMethod(LinkMovementMethod.getInstance());

            feedChannel.addView(tv);
        }

        final ScrollView sv = (ScrollView) channelView.findViewById(R.id.scroll_view);
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

        LinearLayout messageContainer = (LinearLayout) channelView.findViewById(R.id.message_container);
        String lastDate = "";
        for (IrcMessage message : messages) {
            if (!message.getServerTimestampAsPrettyDate().equals(lastDate)) {
                lastDate = message.getServerTimestampAsPrettyDate();

                TextView tv = (TextView) layoutInflater.inflate(R.layout.datechange_header, null);
                tv.setText(lastDate);
                messageContainer.addView(tv);
            }

            TextView tv = (TextView) layoutInflater.inflate(R.layout.message, null);
            String s = message.getServerTimestampAsString() + " (" + message.getNick() + ") "
                    + message.getMessage();
            final SpannableString ss = new SpannableString(s);
            Linkify.addLinks(ss, Linkify.ALL);
            tv.setText(ss);
            tv.setAutoLinkMask(Linkify.ALL);
            tv.setLinksClickable(true);
            tv.setMovementMethod(LinkMovementMethod.getInstance());

            messageContainer.addView(tv);
        }

        final ScrollView sv = (ScrollView) channelView.findViewById(R.id.scroll_view);
        sv.post(new Runnable() {
            public void run() {
                sv.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });

        return channelView;
    }

    @Override
    public void destroyItem(ViewGroup collection, int position, Object view) {
        collection.removeView((LinearLayout) view);
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Parcelable saveState() {
        return null;
    }

    public void setChannels(List<Channel> ircMessages) {
        this.channels = ircMessages;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (channels.size() == 0)
            return "";
        if (position == 0)
            return "Feed";
        return channels.get(position - 1).getName();
    }

}
