
package fi.iki.murgo.irssinotifier;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.support.v7.app.AlertDialog;
import android.text.method.LinkMovementMethod;
import android.view.WindowManager.BadTokenException;
import android.widget.TextView;

public class MessageBox {
    public static void Show(Context context, CharSequence title, CharSequence contents, final Callback<Void> callback) {
        AlertDialog dialog = new AlertDialog.Builder(context).setMessage(contents).setTitle(title).setNeutralButton("OK",
                new OnClickListener() {
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

            ((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        } catch (BadTokenException e) {
            // weird bug when clicking back at the wrong time
        }
    }
}
