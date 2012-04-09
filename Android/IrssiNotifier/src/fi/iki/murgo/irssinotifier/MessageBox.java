package fi.iki.murgo.irssinotifier;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.WindowManager.BadTokenException;
import android.widget.TextView;

public class MessageBox {
	public static void Show(Context context, String title, String contents, final Callback<Void> callback) {
		Show(context, title, contents, callback, false);
	}
	
	public static void Show(Context context, String title, String contents, final Callback<Void> callback, boolean linkify) {
		CharSequence msg = contents;
		if (linkify) {
			final SpannableString s = new SpannableString(contents);
			Linkify.addLinks(s, Linkify.ALL);
			msg = s;
		}

		AlertDialog dialog = new AlertDialog.Builder(context).setMessage(msg).setTitle(title).setNeutralButton("OK", new OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		}).create();
		
		dialog.setOnDismissListener(new OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				if (callback != null)
					callback.doStuff(null);
			}
		});
		
		try {
			dialog.show();
			
			if (linkify) {
				((TextView)dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
			}
		} catch (BadTokenException e) {
			// weird bug when clicking back at the wrong time
		}
	}
}
