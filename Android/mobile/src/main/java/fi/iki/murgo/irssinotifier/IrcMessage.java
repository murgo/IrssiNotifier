
package fi.iki.murgo.irssinotifier;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;

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

    public void deserialize(JSONObject obj) throws JSONException {
        setMessage(obj.getString("message"));
        setChannel(obj.getString("channel"));
        setNick(obj.getString("nick"));
        setServerTimestamp((long) (Double.parseDouble(obj.getString("server_timestamp")) * 1000));
        if (obj.has("id") && !obj.isNull("id")) {
            String externalId = obj.getString("id");
            if (externalId != null && externalId.length() > 0)
                setExternalId(externalId);
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

    private static Calendar clearTimes(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    public String getServerTimestampAsPrettyDate() {
        Calendar today = Calendar.getInstance();
        today = clearTimes(today);

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR,-1);
        yesterday = clearTimes(yesterday);

        Calendar lastWeek = Calendar.getInstance();
        lastWeek.add(Calendar.DAY_OF_YEAR,-7);
        lastWeek = clearTimes(lastWeek);

        if (serverTimestamp.getTime() > today.getTimeInMillis())
            return "today";
        else if (serverTimestamp.getTime() > yesterday.getTimeInMillis())
            return "yesterday";
        else if (serverTimestamp.getTime() > lastWeek.getTimeInMillis()) {
            Locale locale = new Locale("US");
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE", locale);
            return dateFormat.format(serverTimestamp);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(serverTimestamp);
    }

    public void decrypt(String encryptionKey) throws CryptoException {
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
