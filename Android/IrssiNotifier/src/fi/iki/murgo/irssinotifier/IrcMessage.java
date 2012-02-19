package fi.iki.murgo.irssinotifier;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

public class IrcMessage {
	
	public static final String PRIVATE = "!PRIVATE";
	private String message;
	private String channel;
	private String nick;
	private String timestamp;
	private Date serverTimestamp;
	
	public void Deserialize(String payload) throws JSONException {
		Deserialize(new JSONObject(payload));
	}
	
	public void Deserialize(JSONObject obj) {
		try {
			setMessage(obj.getString("message"));
			setChannel(obj.getString("channel"));
			setNick(obj.getString("nick"));
			setTimestamp(obj.getString("timestamp"));
			setServerTimestamp(obj.getString("server_timestamp"));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
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

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public Date getServerTimestamp() {
		return serverTimestamp;
	}

	public void setServerTimestamp(String serverTimestamp) {
		this.serverTimestamp = new Date((long) Double.parseDouble(serverTimestamp) * 1000);
	}
	
	public String getServerTimestampAsString() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
		return dateFormat.format(serverTimestamp);
	}

	public void Decrypt(String encryptionKey) throws CryptoException {
		message = Crypto.decrypt(encryptionKey, message);
		channel = Crypto.decrypt(encryptionKey, channel);
		nick = Crypto.decrypt(encryptionKey, nick);
		
		message = message.replace('´', '\'');
		channel = channel.replace('´', '\'');
		nick = nick.replace('´', '\'');
	}

	public boolean isPrivate() {
		return channel.equals(PRIVATE);
	}

}
