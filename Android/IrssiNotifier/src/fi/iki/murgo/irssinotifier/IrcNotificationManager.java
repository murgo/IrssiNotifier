package fi.iki.murgo.irssinotifier;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class IrcNotificationManager {

	private static IrcNotificationManager instance;

	public static IrcNotificationManager getInstance() {
		if (instance == null)  {
			instance = new IrcNotificationManager();
		}
		return instance;
	}
	
	private IrcNotificationManager() { }
	
	private Map<String, Integer> unreadCounts = new HashMap<String, Integer>();
    private int perMessageNotificationId = 2;
	private long lastSoundDate = 0;
	private DataAccess da;
	
	private int getUnreadCount() {
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

	/*
	private void read() {
		unreadCounts = new HashMap<String, Integer>();
	}
	*/

	private void addUnread(IrcMessage msg) {
		String key = msg.isPrivate() ? msg.getNick() : msg.getChannel();
		
		if (unreadCounts.containsKey(key))
			unreadCounts.put(key, unreadCounts.get(key) + 1);
		else
			unreadCounts.put(key, 1);
	}

	public void handle(Context context, String message) {
		/*
		 * removed from the server side for now
        if (message.startsWith("read")) {
        	NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        	notificationManager.cancelAll();
        	// TODO figure out
        	ircMessages.read();
        	return;
        }
        */
        
        Preferences prefs = new Preferences(context);
        NotificationMode mode = prefs.getNotificationMode();

    	String tickerText;
        String notificationMessage;
    	String titleText;
        int notificationId;
        long when = new Date().getTime();
        IrcMessage msg = new IrcMessage();

    	try {
            msg.Deserialize(message);
        	msg.Decrypt(prefs.getEncryptionPassword());

        	if (da == null)
        		da = new DataAccess(context);
        	da.HandleMessage(msg);

            Object[] values = getValues(msg, mode);
			notificationMessage = (String) values[0];
			titleText = (String) values[1];
			notificationId = (Integer) values[2];
            when = msg.getServerTimestamp().getTime();
            
        	if (getUnreadCount() == 0) {
        		tickerText = "New IRC message";
        	} else {
        		tickerText = "" + getUnreadCount() + " new IRC messages";
        	}
        	
            addUnread(msg);
        } catch (CryptoException e) {
        	titleText = "IrssiNotifier error";
        	notificationMessage = "Unable to decrypt data. Perhaps encryption key is wrong?";
			tickerText = "IrssiNotifier decryption error";
        	notificationId = 1;
        } catch (JSONException e) {
        	titleText = "IrssiNotifier error";
        	notificationMessage = "Unable to parse data. Server error?";
			tickerText = "IrssiNotifier parse error";
        	notificationId = 1;
		}

        Intent toLaunch = new Intent(context, IrssiNotifierActivity.class);
        toLaunch.putExtra("Channel", msg.getLogicalChannel());
    	
        /*
        // Stupid piece of shit always returns true
		boolean foreground = false;
    	try {
			// foreground = new ForegroundCheckTask().execute(context).get(); 
		} catch (Exception e) {
			e.printStackTrace();
		}
		*/
        
        IrssiNotifierActivity foregroundInstance = IrssiNotifierActivity.getForegroundInstance();
		if (foregroundInstance != null) {
			foregroundInstance.newMessage(msg);
			return;
		}

        if (!prefs.isNotificationsEnabled()) {
        	return;
        }

        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification notification = new Notification(R.drawable.icon, tickerText, when);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		
		if (prefs.isSoundEnabled() && (!prefs.isSpamFilterEnabled() || new Date().getTime() > lastSoundDate + 60000L)) {
			notification.defaults |= Notification.DEFAULT_SOUND;
			lastSoundDate = new Date().getTime();
		}
		
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, toLaunch, 0);

        notification.setLatestEventInfo(context, titleText, notificationMessage, contentIntent);
        notificationManager.notify(notificationId, notification);
	}
	
	public void mainActivityOpened(Context context) {
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();
	}
	
	private Object[] getValues(IrcMessage msg, NotificationMode mode) {
		String text = null;
		String title = null;
		int id = 0;

        int unreadCount = getUnreadCount() + 1;
        int channelUnreadCount = msg.isPrivate() ? getUnreadCountForChannel(msg.getNick()) : getUnreadCountForChannel(msg.getChannel()) + 1;

		switch (mode) {
		case Single:
			id = 1;
			if (msg.isPrivate()) {
				if (unreadCount == 0) {
					title = "Private message from " + msg.getNick();
					text = msg.getMessage();
				} else {
					title = "" + unreadCount + " new hilights";
					text = "Last: " + msg.getLogicalChannel() + " (" + msg.getNick() + ") " + msg.getMessage();
				}
			} else {
				if (unreadCount == 0) {
					title = "Hilight at " + msg.getChannel();
					text = "(" + msg.getNick() + ") " + msg.getMessage();
				} else {
					title = "" + unreadCount + " new hilights";
					text = "Last: " + msg.getLogicalChannel() + " (" + msg.getNick() + ") " + msg.getMessage();
				}
			}
			break;

		case PerMessage:
			id = perMessageNotificationId++;
			if (msg.isPrivate()) {
				title = "Private message from " + msg.getNick();
				text = msg.getMessage();
			} else {
				title = "Hilight at " + msg.getChannel();
				text = "(" + msg.getNick() + ") " + msg.getMessage();
			}
			break;
			
		case PerChannel:
			id = msg.getNick().hashCode();
			if (msg.isPrivate()) {
				if (channelUnreadCount == 0) {
					title = "Private message from " + msg.getNick();
					text = msg.getMessage();
				} else {
					title = "" + channelUnreadCount + " private messages from " + msg.getNick();
					text = "Last: " + msg.getMessage();
				}
			} else {
				if (channelUnreadCount == 0) {
					title = "Hilight at " + msg.getChannel();
					text = "(" + msg.getNick() + ") " + msg.getMessage();
				} else {
					title = "" + unreadCount + " new hilights at " + msg.getChannel();
					text = "Last: (" + msg.getNick() + ") " + msg.getMessage();
				}
			}
			break;
		}
		
		return new Object[] {text, title, id};
	}

}
