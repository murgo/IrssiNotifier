
package fi.iki.murgo.irssinotifier;

import java.util.List;

public class Channel {
    private long id;
    private String name;
    private int order;
    private List<IrcMessage> messages;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getOrder() {
        return this.order;
    }

    public List<IrcMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<IrcMessage> messages) {
        this.messages = messages;
    }

    public int getUnreadCount() {
        int size = messages.size();
        int lastShown = size;
        for (int i = 0; i < size; i++) {
            if (!messages.get(i).isShown()) {
                lastShown = i;
                break;
            }
        }

        return size - lastShown;
    }

    public boolean markAllAsRead() {
        boolean change = false;
        for (IrcMessage m : messages) {
            if (!m.isShown()) {
                change = true;
                m.setShown(true);
            }
        }
        return change;
    }
}
