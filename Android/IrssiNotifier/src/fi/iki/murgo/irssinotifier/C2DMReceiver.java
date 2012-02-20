package fi.iki.murgo.irssinotifier;

import java.util.Date;

import org.json.JSONException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class C2DMReceiver extends BroadcastReceiver {
    private static final String TAG = C2DMReceiver.class.getSimpleName();

    private static final String C2DM_DATA_ACTION = "action";
    private static final String C2DM_DATA_MESSAGE = "message";

    public static final String EMAIL_OF_SENDER = "irssinotifier@gmail.com";
    
    private static int perMessageNotificationId = 2;

	private static Callback<String[]> callback;
	private static IrcMessages ircMessages = new IrcMessages(); // TODO: Thread safety?

    public static void registerToC2DM(Context context) {
        Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
        registrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
        registrationIntent.putExtra("sender", EMAIL_OF_SENDER);
        ComponentName service = context.startService(registrationIntent);
        if (service == null) throw new RuntimeException("Service failed to start");
    }
    
    public static void unregisterFromC2DM(Context context) {
        Intent unregistrationIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
        unregistrationIntent.putExtra("app", PendingIntent.getBroadcast(context, 0, new Intent(), 0));
        context.startService(unregistrationIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("com.google.android.c2dm.intent.REGISTRATION")) {
            handleRegistration(context, intent);
        } else if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
            handleMessage(context, intent);
        } else {
            Log.w(TAG, "Unexpected intent: " + intent);
        }
    }
   
    public static void setRegistrationCallback(Callback<String[]> callback) {
		C2DMReceiver.callback = callback;
    }

    private void handleRegistration(Context context, Intent intent) {
        String registrationId = intent.getStringExtra("registration_id");
        String error = intent.getStringExtra("error");
        String unregistered = intent.getStringExtra("unregistered"); // TODO

        Log.i(TAG, "RegistrationId: " + registrationId + " Error: " + error + " Unregistered: " + unregistered);
        
    	Preferences preferences = new Preferences(context);

        if (error != null || unregistered != null) {
        	preferences.setRegistrationId(null);
        } else {
        	preferences.setRegistrationId(registrationId);
        }
        
    	if (callback != null) {
    		callback.doStuff(new String[] {registrationId, error, unregistered});
    	}
    }

    private void handleMessage(Context context, Intent intent) {
        Log.d(TAG, "Handling C2DM notification");
        String action = intent.getStringExtra(C2DM_DATA_ACTION);
        String message = intent.getStringExtra(C2DM_DATA_MESSAGE);
        Log.d(TAG, "Action: " + action + " Message: " + message);

        if (message.startsWith("read")) { // TODO: implement server side
        	NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        	notificationManager.cancelAll();
        	ircMessages.read();
        	return;
        }
        
        Preferences prefs = new Preferences(context);
        NotificationMode mode = prefs.getNotificationMode();
        if (mode == NotificationMode.None) {
        	return;
        }
        
    	String tickerText;
        String notificationMessage;
    	String titleText;
        int notificationId;
        long when = new Date().getTime();;
        IrcMessage msg = new IrcMessage();
        String tag = null; // TODO why tag

    	try {
            msg.Deserialize(message);
        	msg.Decrypt(prefs.getEncryptionPassword());

            Object[] values = getValues(msg, mode, ircMessages);
			notificationMessage = (String) values[0];
			titleText = (String) values[1];
			notificationId = (Integer) values[2];
            when = msg.getServerTimestamp().getTime();
            tag = msg.isPrivate() ? msg.getNick() : msg.getChannel();
            
        	if (ircMessages.getUnreadCount() == 0) {
        		tickerText = "New IRC message";
        	} else {
        		tickerText = "" + ircMessages.getUnreadCount() + " new IRC messages";
        	}
        	
            ircMessages.addUnread(msg);
        } catch (CryptoException e) {
        	titleText = "IrssiNotifier error";
        	notificationMessage = "Unable to decrypt data. Perhaps encryption key is wrong?";
			tickerText = "IrssiNotifier decryption error";
        	notificationId = 1;
            tag = "error";
        } catch (JSONException e) {
        	titleText = "IrssiNotifier error";
        	notificationMessage = "Unable to parse data. Server error?";
			tickerText = "IrssiNotifier parse error";
        	notificationId = 1;
		}
        
        
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        Notification notification = new Notification(R.drawable.ic_launcher, tickerText, when);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.defaults |= Notification.DEFAULT_SOUND;
		
        Intent toLaunch = new Intent(context, IrssiNotifierActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, toLaunch, 0);

        notification.setLatestEventInfo(context, titleText, notificationMessage, contentIntent);
        notificationManager.notify(tag, notificationId, notification);
    }

	private Object[] getValues(IrcMessage msg, NotificationMode mode, IrcMessages ircMessages) {
		String text = null;
		String title = null;
		int id = 0;

        int unreadCount = ircMessages.getUnreadCount() + 1;
        int channelUnreadCount = msg.isPrivate() ? ircMessages.getUnreadCountForChannel(msg.getNick()) : ircMessages.getUnreadCountForChannel(msg.getChannel()) + 1;

		switch (mode) {
		case Single:
			id = 1;
			if (msg.isPrivate()) {
				if (unreadCount == 0) {
					title = "Private message from " + msg.getNick();
					text = msg.getMessage();
				} else {
					title = "" + unreadCount + " new hilights";
					text = "Last: " + msg.getChannel() + " (" + msg.getNick() + ") " + msg.getMessage();
				}
			} else {
				if (unreadCount == 0) {
					title = "Hilight at " + msg.getChannel();
					text = "(" + msg.getNick() + ") " + msg.getMessage();
				} else {
					title = "" + unreadCount + " new hilights";
					text = "Last: " + msg.getChannel() + " (" + msg.getNick() + ") " + msg.getMessage();
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
