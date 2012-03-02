package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DataAccess extends SQLiteOpenHelper {

	private static final String DATABASE_NAME = "IrssiNotifier";
	private static final int DATABASE_VERSION = 2;

	public DataAccess(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		try {
			db.execSQL("CREATE TABLE Channel (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, orderIndex INTEGER)");
			db.execSQL("CREATE TABLE IrcMessage (id INTEGER PRIMARY KEY AUTOINCREMENT, channelId INTEGER, message TEXT, nick TEXT, serverTimestamp INTEGER, timestamp TEXT, externalId TEXT, FOREIGN KEY(channelId) REFERENCES Channel(Id))");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 3) {
			db.execSQL("DROP TABLE IF EXISTS Channel");
			db.execSQL("DROP TABLE IF EXISTS IrcMessage");
			onCreate(db);
		}
	}
	
	public void HandleMessage(IrcMessage message) {
		try {
			SQLiteDatabase database = getWritableDatabase();
			
			String channelName = message.getLogicalChannel();
			List<Channel> channels = getChannels(database);
			
			int biggestOrder = 0;
			Channel found = null;
			for (Channel ch : channels) {
				biggestOrder = Math.max(biggestOrder, ch.getOrder() + 1);
				if (ch.getName().equals(channelName)) {
					found = ch;
					break;
				}
			}
			
			long channelId;
			if (found == null) {
				ContentValues values = new ContentValues();
				values.put("name", channelName);
				values.put("orderIndex", biggestOrder);
				channelId = database.insert("Channel", null, values);
			} else {
				channelId = found.getId();
			}
			
			ContentValues messageValues = new ContentValues();
			messageValues.put("channelId", channelId);
			messageValues.put("message", message.getMessage());
			messageValues.put("nick", message.getNick());
			messageValues.put("serverTimestamp", message.getServerTimestamp().getTime());
			messageValues.put("timestamp", message.getTimestamp());
			messageValues.put("externalId", message.getExternalId());

			Cursor cur = database.query("IrcMessage", new String[] {"externalId", "message"}, "externalId = ?", new String[] {message.getExternalId()}, null, null, null, "1");
			if (cur.isAfterLast()) {
				database.insert("IrcMessage", null, messageValues);
			} else {
				// already in database, update if necessary
				database.update("IrcMessage", messageValues, "externalId = ?", new String[] { message.getExternalId() });
			}
			cur.close();
			database.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void clearChannel(Channel channel) {
		SQLiteDatabase database = getWritableDatabase();
		database.delete("IrcMessage", "channelId = ?", new String[] { Long.toString(channel.getId()) });
		database.close();
	}
	
	public void clearAll() {
		SQLiteDatabase database = getWritableDatabase();
		database.delete("Channel", null, null);
		database.delete("IrcMessage", null, null);
		database.close();
	}

	private List<Channel> getChannels(SQLiteDatabase database) {
		Cursor cursor = database.query("Channel", new String[] {"id", "name", "orderIndex"}, null, null, null, null, "orderIndex");
		cursor.moveToFirst();
		List<Channel> list = new ArrayList<Channel>();
		while (!cursor.isAfterLast()) {
			Channel ch = new Channel();
			ch.setId(cursor.getLong(cursor.getColumnIndex("id")));
			ch.setName(cursor.getString(cursor.getColumnIndex("name")));
			ch.setOrder(cursor.getInt(cursor.getColumnIndex("orderIndex")));
			list.add(ch);
			cursor.moveToNext();
		}

		cursor.close();
		return list;
	}

	public List<Channel> getChannels() {
		SQLiteDatabase database = getReadableDatabase();
		List<Channel> channels = getChannels(database);
		database.close();
		
		return channels;
	}

	public List<IrcMessage> getMessagesForChannel(Channel channel) {
		SQLiteDatabase database = getReadableDatabase();

		Cursor cursor = database.query("IrcMessage", new String[] {"message", "nick", "serverTimestamp", "timestamp", "externalId"}, "channelId = ?", new String[] { Long.toString(channel.getId()) }, null, null, null);
		cursor.moveToFirst();
		List<IrcMessage> list = new ArrayList<IrcMessage>();

		int colMessage = cursor.getColumnIndex("message");
		int colNick = cursor.getColumnIndex("nick");
		int colServerTimestamp = cursor.getColumnIndex("serverTimestamp");
		int colTimestamp = cursor.getColumnIndex("timestamp");
		int colExternalId = cursor.getColumnIndex("externalId");

		while (!cursor.isAfterLast()) {
			IrcMessage message = new IrcMessage();
			message.setMessage(cursor.getString(colMessage));
			message.setNick(cursor.getString(colNick));
			message.setServerTimestamp(cursor.getLong(colServerTimestamp));
			message.setTimestamp(cursor.getString(colTimestamp));
			message.setExternalId(cursor.getString(colExternalId));
			message.setChannel(channel.getName());
			
			list.add(message);
			cursor.moveToNext();
		}

		cursor.close();
		database.close();
		return list;
	}
}
