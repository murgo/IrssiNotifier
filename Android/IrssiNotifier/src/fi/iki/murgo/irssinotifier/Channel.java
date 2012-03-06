package fi.iki.murgo.irssinotifier;

import java.util.List;

public class Channel {
	private long id;
	private String name;
	private int order;
	private List<IrcMessage> messages;
	
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setOrder(int order) {
		this.order = order;
	}
	public int getOrder() {
		return this.order;
	}
	public List<IrcMessage> getMessages() {
		return messages;
	}
	public void setMessages(List<IrcMessage> messages) {
		this.messages = messages;
	}
}
