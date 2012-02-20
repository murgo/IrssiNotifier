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
	private static final int DATABASE_VERSION = 1;

	public DataAccess(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE Channel (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
		db.execSQL("CREATE TABLE IrcMessage (id INTEGER PRIMARY KEY AUTOINCREMENT, channelId INTEGER, message TEXT, nick TEXT, serverTimestamp INTEGER, timestamp TEXT, externalId TEXT, FOREIGN KEY(channelId) REFERENCES Channel(Id))");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}
	
	public void HandleMessage(IrcMessage message) {
		SQLiteDatabase database = getWritableDatabase();
		
		String channelName = message.getLogicalChannel();
		long channelId = 0;
		
		Cursor cursor = database.query("Channel", new String[] {"id"}, "name = ?" , new String[] { channelName }, null, null, null, "LIMIT 1");
		cursor.moveToFirst();
		if (!cursor.isAfterLast()) {
			channelId = cursor.getInt(cursor.getColumnIndex("id"));
		}
		cursor.close();
		
		if (channelId == 0) {
			ContentValues values = new ContentValues();
			values.put("name", channelName);
			channelId = database.insert("Channel", null, values);
		}
		
		ContentValues messageValues = new ContentValues();
		messageValues.put("channelId", channelId);
		messageValues.put("message", message.getMessage());
		messageValues.put("nick", message.getNick());
		messageValues.put("serverTimestamp", message.getServerTimestamp().getTime());
		messageValues.put("timestamp", message.getTimestamp());
		messageValues.put("externalId", message.getExternalId());
		database.insert("IrcMessage", null, messageValues);
		
		database.close();
	}
	
	public void clearChannel(Channel channel) {
		SQLiteDatabase database = getWritableDatabase();
		database.delete("IrcMessage", "channelId = ?", new String[] { Long.toString(channel.getId()) });
		database.close();
	}

	public List<Channel> getChannels() {
		SQLiteDatabase database = getReadableDatabase();
		
		Cursor cursor = database.query("Channel", new String[] {"id", "name"}, null, null, null, null, null);
		cursor.moveToFirst();
		List<Channel> list = new ArrayList<Channel>();
		while (!cursor.isAfterLast()) {
			Channel ch = new Channel();
			ch.setId(cursor.getLong(cursor.getColumnIndex("id")));
			ch.setName(cursor.getString(cursor.getColumnIndex("name")));
			list.add(ch);
		}

		cursor.close();
		database.close();
		return list;
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
		}

		cursor.close();
		database.close();
		return list;
	}
}
