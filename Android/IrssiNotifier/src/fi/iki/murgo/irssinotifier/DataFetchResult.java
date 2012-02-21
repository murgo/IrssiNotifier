package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.List;

public class DataFetchResult {
	private Exception exception;
	private List<IrcMessage> messages;
	
	public DataFetchResult() {
		messages = new ArrayList<IrcMessage>();
	}
	
	public Exception getException() {
		return exception;
	}
	public void setException(Exception exception) {
		this.exception = exception;
	}
	public List<IrcMessage> getMessages() {
		return messages;
	}
	public void setMessages(List<IrcMessage> messages) {
		this.messages = messages;
	}
}
