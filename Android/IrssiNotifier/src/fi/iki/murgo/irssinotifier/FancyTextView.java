
package fi.iki.murgo.irssinotifier;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class FancyTextView extends TextView {

    private final CharSequence text;
    private final Timer timer;
    private static final Random rnd = new Random();
    private String currentText = "";
    private int currentIteration = 0;
    private final int iterations = 5;
    private int index = 0;
    private final Activity activity;
    private final int delay = 33;

    public FancyTextView(CharSequence fulltext, Activity activity) {
        super(activity);

        this.text = fulltext;
        this.activity = activity;

        this.timer = new Timer();
        setMovementMethod(LinkMovementMethod.getInstance());
        setPadding(0, 10, 0, 0);
        setTypeface(Typeface.MONOSPACE);
    }

    private char getRandomChar() {
        int i = rnd.nextInt(126 - 33);
        return (char) (i + 33);
    }

    public void start() {
        timer.schedule(getTimerTask(), 0, delay);
    }

    private TimerTask getTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                if (currentText.length() > 0) {
                    currentText = currentText.substring(0, currentText.length() - 1);
                }

                if (currentIteration++ >= iterations) {
                    currentText += text.charAt(index);
                    index++;
                }

                if (index >= text.length()) {
                    timer.cancel();
                } else {
                    currentText += getRandomChar();
                }

                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        if (index >= text.length()) {
                            setText(text);
                        } else {
                            setText(currentText);
                        }
                    }
                });
            }
        };
    }

}
