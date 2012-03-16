package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.commonsware.cwac.tlv.TouchListView;

public class ChannelSettingsActivity extends ListActivity {

	private IconicAdapter adapter = null;
	private List<Channel> channels;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.channel_settings);
		
		DataAccess da = new DataAccess(this);
		channels = da.getChannels();
		List<String> channelNames = new ArrayList<String>();
		for (Channel ch : channels)
			channelNames.add(ch.getName());

		
		TouchListView tlv=(TouchListView)getListView();
		adapter = new IconicAdapter(channelNames);
		setListAdapter(adapter);
		
		tlv.setDropListener(onDrop);
		tlv.setRemoveListener(onRemove);
		
		MessageBox.Show(this, null, "Drag channels from the grabber to reorder them, swipe grabber right to remove channel", null);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		int count = adapter.getCount();
		List<String> names = new ArrayList<String>();
		for (int i = 0; i < count; i++) {
			names.add(adapter.getItem(i));
		}
		
		DataAccess da = new DataAccess(this);
		for (Channel ch : channels) {
			int order = -1;
			int i = 0;
			for (String n : names) {
				if (n.equals(ch.getName())) {
					order = i;
					break;
				}
				i++;
			}
			if (order < 0) {
				da.removeChannel(ch);
			} else {
				ch.setOrder(order);
				da.updateChannel(ch);
			}
		}
		
		IrssiNotifierActivity.needsRefresh();
	}
	
	private TouchListView.DropListener onDrop=new TouchListView.DropListener() {
		public void drop(int from, int to) {
				String item = adapter.getItem(from);
				
				adapter.remove(item);
				adapter.insert(item, to);
		}
	};
	
	private TouchListView.RemoveListener onRemove=new TouchListView.RemoveListener() {
		public void remove(int which) {
				adapter.remove(adapter.getItem(which));
		}
	};
	
	class IconicAdapter extends ArrayAdapter<String> {
		private List<String> data;
		
		IconicAdapter(List<String> data) {
			super(ChannelSettingsActivity.this, R.layout.channel_settings_row, data);
			this.data = data;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			
			if (row == null) {
				LayoutInflater inflater=getLayoutInflater();
				row = inflater.inflate(R.layout.channel_settings_row, parent, false);
			}
			
			TextView label = (TextView)row.findViewById(R.id.label);
			
			label.setText("" + (position + 1) + ": " + data.get(position));
			
			return row;
		}
	}
}
