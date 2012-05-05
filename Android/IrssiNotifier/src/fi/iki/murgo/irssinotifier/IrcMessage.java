package fi.iki.murgo.irssinotifier;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

public class IrcMessage {
	
	private static final String PRIVATE = "!PRIVATE";
	private static final String TOOLONG = "toolong";
	private String message;
	private String channel;
	private String nick;
	private Date serverTimestamp;
	private String externalId;
	private boolean shown;
	private boolean clearedFromFeed;
	private long id;
	
	public void Deserialize(String payload) throws JSONException {
		Deserialize(new JSONObject(payload));
	}
	
	public void Deserialize(JSONObject obj) {
		try {
			setMessage(obj.getString("message"));
			setChannel(obj.getString("channel"));
			setNick(obj.getString("nick"));
			setServerTimestamp((long) (Double.parseDouble(obj.getString("server_timestamp")) * 1000));
			setExternalId(obj.getString("id"));
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getNick() {
		return nick;
	}

	public void setNick(String sender) {
		this.nick = sender;
	}

	public Date getServerTimestamp() {
		return serverTimestamp;
	}

	public void setServerTimestamp(long serverTimestamp) {
		this.serverTimestamp = new Date(serverTimestamp);
	}
	
	public String getServerTimestampAsString() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
		return dateFormat.format(serverTimestamp);
	}

	public void Decrypt(String encryptionKey) throws CryptoException {
		if (message.equals(TOOLONG))
			message = "Message too long";
		else
			message = Crypto.decrypt(encryptionKey, message);
		channel = Crypto.decrypt(encryptionKey, channel);
		nick = Crypto.decrypt(encryptionKey, nick);
		
		message = message.replace('´', '\'');
		channel = channel.replace('´', '\'');
		nick = nick.replace('´', '\'');
	}

	public boolean isPrivate() {
		return PRIVATE.equals(channel);
	}

	public String getLogicalChannel() {
		return isPrivate() ? getNick() : getChannel();
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}
	
	public String getExternalId() {
		return externalId;
	}

	public void setShown(boolean shown) {
		this.shown = shown;
	}
	
	public boolean isShown() {
		return shown;
	}

	public void setClearedFromFeed(boolean b) {
		this.clearedFromFeed = b;
	}
	
	public boolean getClearedFromFeed() {
		return clearedFromFeed;
	}

	public void setId(long id) {
		this.id = id;
	}
	
	public long getId() {
		return id;
	}

}
