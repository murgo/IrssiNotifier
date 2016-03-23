
package fi.iki.murgo.irssinotifier;

public interface Callback<T> {
    void doStuff(T param);
}
