package fi.iki.murgo.irssinotifier;

import java.util.HashMap;
import java.util.Map;

public class IrcMessages {
	
	private static IrcMessages instance;
	Map<String, Integer> unreadCounts = new HashMap<String, Integer>();

	private IrcMessages() {
		
	}
	
	public static IrcMessages getInstance() {
		if (instance == null)
			instance = new IrcMessages();
		
		return instance;
	}
	
	public int getUnreadCount() {
		int total = 0;
		for (int i : unreadCounts.values()) {
			total += i;
		}
		return total;
	}

	public int getUnreadCountForChannel(String channel) {
		if (!unreadCounts.containsKey(channel))
			return 0;
		
		return unreadCounts.get(channel);
	}

	public void read() {
		unreadCounts = new HashMap<String, Integer>();
	}

	public void addUnread(IrcMessage msg) {
		String key = msg.isPrivate() ? msg.getNick() : msg.getChannel();
		
		if (unreadCounts.containsKey(key))
			unreadCounts.put(key, unreadCounts.get(key) + 1);
		else
			unreadCounts.put(key, 1);
	}

}
