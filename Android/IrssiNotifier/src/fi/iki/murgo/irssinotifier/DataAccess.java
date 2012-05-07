package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DataAccess extends SQLiteOpenHelper {

	private static final String DATABASE_NAME = "IrssiNotifier";
	private static final int DATABASE_VERSION = 4;

	public DataAccess(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		try {
			db.execSQL("CREATE TABLE Channel (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, orderIndex INTEGER)");
			db.execSQL("CREATE TABLE IrcMessage (id INTEGER PRIMARY KEY AUTOINCREMENT, channelId INTEGER, message TEXT, nick TEXT, serverTimestamp INTEGER, externalId TEXT, shown INTEGER, clearedFromFeed INTEGER, FOREIGN KEY(channelId) REFERENCES Channel(Id))");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2) {
			db.execSQL("DROP TABLE IF EXISTS Channel");
			db.execSQL("DROP TABLE IF EXISTS IrcMessage");
			onCreate(db);
		} else if (oldVersion < 4) {
			db.execSQL("ALTER TABLE IrcMessage ADD COLUMN clearedFromFeed INTEGER");
		}
	}
	
	public void handleMessage(IrcMessage message) {
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
			messageValues.put("externalId", message.getExternalId());

			Cursor cur = database.query("IrcMessage", new String[] {"externalId", "message"}, "externalId = ?", new String[] {message.getExternalId()}, null, null, null, "1");
			if (cur.isAfterLast()) {
				messageValues.put("shown", 0);
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
	
	public void clearChannel(Channel channel, SQLiteDatabase database) {
		database.delete("IrcMessage", "channelId = ?", new String[] { Long.toString(channel.getId()) });
	}
	
	public void clearChannel(Channel channel) {
		SQLiteDatabase database = getWritableDatabase();
		clearChannel(channel, database);
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
		for (Channel channel : channels) {
			channel.setMessages(getMessagesForChannel(database, channel));
		}
		database.close();
		
		return channels;
	}
	
	public void setAllMessagesAsShown() {
		SQLiteDatabase database = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("shown", true);
		database.update("IrcMessage", values, "shown = ?", new String[] {"0"});
		database.close();
	}

	private List<IrcMessage> getMessagesForChannel(SQLiteDatabase database, Channel channel) {
		Cursor cursor = database.query("IrcMessage", new String[] {"message", "nick", "serverTimestamp", "shown", "externalId", "clearedFromFeed", "id"}, "channelId = ?", new String[] { Long.toString(channel.getId()) }, null, null, "serverTimestamp DESC", "100");
		cursor.moveToFirst();
		List<IrcMessage> list = new ArrayList<IrcMessage>();

		int colMessage = cursor.getColumnIndex("message");
		int colNick = cursor.getColumnIndex("nick");
		int colServerTimestamp = cursor.getColumnIndex("serverTimestamp");
		int colExternalId = cursor.getColumnIndex("externalId");
		int colShown = cursor.getColumnIndex("shown");
		int colClearedFromFeed = cursor.getColumnIndex("clearedFromFeed");
		int colId = cursor.getColumnIndex("id");
		
		while (!cursor.isAfterLast()) {
			IrcMessage message = new IrcMessage();
			message.setMessage(cursor.getString(colMessage));
			message.setNick(cursor.getString(colNick));
			message.setServerTimestamp(cursor.getLong(colServerTimestamp));
			message.setExternalId(cursor.getString(colExternalId));
			message.setChannel(channel.getName());
			message.setShown(cursor.getInt(colShown) == 0 ? false : true);
			message.setClearedFromFeed(cursor.getInt(colClearedFromFeed) == 0 ? false : true);
			message.setId(cursor.getLong(colId));
			
			list.add(message);
			cursor.moveToNext();
		}

		cursor.close();
		Collections.reverse(list);
		return list;
	}

	public void setChannelAsShown(Channel channel) {
		SQLiteDatabase database = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("shown", true);
		database.update("IrcMessage", values, "shown = ? AND channelId = ?", new String[] {"0", "" + channel.getId()});
		database.close();
	}

	public void clearMessagesFromFeed(List<Long> messageIds) {
		SQLiteDatabase database = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("clearedFromFeed", true);
		for (Long id : messageIds) {
			database.update("IrcMessage", values, "id = ?", new String[] { id.toString() });
		}
		database.close();
	}

	public void removeChannel(Channel channel) {
		SQLiteDatabase database = getWritableDatabase();
		
		clearChannel(channel, database);
		database.delete("Channel", "id = ?", new String[] { Long.toString(channel.getId()) });

		database.close();
	}

	public void updateChannel(Channel channel) {
		SQLiteDatabase database = getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put("name", channel.getName());
		values.put("orderIndex", channel.getOrder());
		
		database.update("Channel", values, "id = ?", new String[] { Long.toString(channel.getId()) });
		database.close();
	}

	public void clearAllMessagesFromFeed() {
		SQLiteDatabase database = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("clearedFromFeed", true);
		database.update("IrcMessage", values, null, null);
		database.close();
	}
}
