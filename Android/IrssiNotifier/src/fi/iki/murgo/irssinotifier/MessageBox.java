package fi.iki.murgo.irssinotifier;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;

public class MessageBox {
	public static void Show(Context context, String title, String contents, final Callback<Void> callback) {
		// TODO: i18n
		new AlertDialog.Builder(context).setMessage(contents).setTitle(title).setNeutralButton("OK", new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		}).show().setOnDismissListener(new OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				if (callback != null)
					callback.doStuff(null);
			}
		});
	}
}
