package fi.iki.murgo.irssinotifier;

import java.util.Map;

public class SimpleEntry<TKey, TValue> implements Map.Entry<TKey, TValue> {
	
	private TKey key;
	private TValue value;

	public SimpleEntry(TKey key, TValue value) {
		this.key = key;
		this.value = value;
	}

	public TKey getKey() {
		return key;
	}

	public TValue getValue() {
		return value;
	}

	public TValue setValue(TValue object) {
		return value = object;
	}

}
