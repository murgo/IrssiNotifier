
package fi.iki.murgo.irssinotifier;

import java.util.ArrayList;
import java.util.List;

import com.mobeta.android.dslv.DragSortListView;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;

public class ChannelSettingsActivity extends ListActivity {

    private List<Channel> channels;
    private ArrayAdapter<String> adapter;
    private Context ctx;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        ctx = this;

        DataAccess da = new DataAccess(this);
        channels = da.getChannels();
        List<String> channelNames = new ArrayList<String>();
        for (Channel ch : channels)
            channelNames.add(ch.getName());

        setContentView(R.layout.channel_settings);
        final DragSortListView lv = (DragSortListView) getListView();

        lv.setDropListener(onDrop);
        lv.setRemoveListener(onRemove);
        lv.setOnItemLongClickListener(getOnItemLongClickListener());

        adapter = new ArrayAdapter<String>(this, R.layout.channel_settings_row, R.id.label,
                channelNames);
        setListAdapter(adapter);

        MessageBox.Show(this, null, "Drag channels from the grabber to reorder them, long press channel to remove it.", null);
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
                if (n.equalsIgnoreCase(ch.getName())) {
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

        IrssiNotifierActivity.refreshIsNeeded();
    }

    private DragSortListView.DropListener onDrop = new DragSortListView.DropListener() {
        @Override
        public void drop(int from, int to) {
            String item = adapter.getItem(from);

            adapter.remove(item);
            adapter.insert(item, to);
        }
    };

    private DragSortListView.RemoveListener onRemove = new DragSortListView.RemoveListener() {
        @Override
        public void remove(int which) {
            adapter.remove(adapter.getItem(which));
        }
    };

    private OnItemLongClickListener getOnItemLongClickListener() {
        return new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, final View view, final int position,
                    long id) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                builder.setMessage(
                        "Are you sure you want to remove channel " + adapter.getItem(position))
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                String item = adapter.getItem(position);
                                adapter.remove(item);
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();

                return true;
            }
        };
    }
}
