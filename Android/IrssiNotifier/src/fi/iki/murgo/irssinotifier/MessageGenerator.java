package fi.iki.murgo.irssinotifier;

import android.content.Context;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MessageGenerator extends TimerTask {

    private static Random random = new Random();
    private Context context;

    public MessageGenerator(Context context) {
        this.context = context;
    }

    public static void Flood(Context context) {
        new Timer().schedule(new MessageGenerator(context), 5000);
    }

    private static String getRandomString(int min, int max) {
        char[] chars = "ABCDEFHGIJKLMNOPQRSTUVWXYZÅÄÖabcdefghijklmnopqrstuvwxyzåäöABCDEFHGIJKLMNOPQRSTUVWXYZÅÄÖabcdefghijklmnopqrstuvwxyzåäö;:_1234567890+!\"#¤%&/()=?¨'´`^*?-.,<>|\\[]€£$§½µ".toCharArray();
        StringBuilder sb = new StringBuilder();
        int amount = max <= min ? max : min + random.nextInt(1 + (max - min));
        for (int i = 0; i < amount; i++) {
            sb.append(chars[random.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    private static long nextLong(long n) {
        long bits, val;
        do {
            bits = (random.nextLong() << 1) >>> 1;
            val = bits % n;
        } while (bits-val+(n-1) < 0L);
        return val;
    }

    @Override
    public void run() {
        fillDb(222, 2);
        sendNotifications(2, 2);

        this.context = null;
    }

    private void fillDb(int channelCount, int messagesPerChannel) {
        DataAccess dao = new DataAccess(context);

        for (int i = 0; i < channelCount; i++) {
            String channel = getRandomString(5, 10);
            for (int j = 0; j < messagesPerChannel; j++) {
                System.out.println("DB: Faking message " + j + " of " + messagesPerChannel + " for channel " + i + " of " + channelCount);

                IrcMessage msg = new IrcMessage();
                msg.setChannel(channel);
                msg.setMessage(getRandomString(2, 30));
                msg.setNick(getRandomString(4, 10));
                msg.setServerTimestamp(System.currentTimeMillis() - nextLong(1000L * 60 * 60 * 24 * 30));
                msg.setExternalId(getRandomString(6, 6));

                dao.handleMessage(msg);
            }
        }
    }

    private void sendNotifications(int channelCount, int messagesPerChannel) {
        IrcNotificationManager manager = IrcNotificationManager.getInstance();

        for (int i = 0; i < channelCount; i++) {
            String channel = getRandomString(5, 10);
            for (int j = 0; j < messagesPerChannel; j++) {
                System.out.println("Notification: Faking message " + j + " of " + messagesPerChannel + " for channel " + i + " of " + channelCount);

                IrcMessage message = new IrcMessage();
                message.setChannel(channel);
                message.setMessage(getRandomString(2, 30));
                message.setNick(getRandomString(4, 10));
                message.setServerTimestamp(System.currentTimeMillis() - nextLong(1000L * 60 * 60 * 24 * 30));
                message.setExternalId(getRandomString(6, 6));

                manager.handle(context, message);
            }
        }
    }
}
