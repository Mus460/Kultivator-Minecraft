package dev.kultivator.formasi.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The immutable effect numbers of a spec (DESIGN 24.2.2), read by its handler. */
public final class Params {

	public static final Params EMPTY = new Params(Map.of());

	private final Map<String, Double> values;

	public Params(Map<String, Double> values) {
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	/** The number under {@code key}, or {@code fallback}. */
	public double get(String key, double fallback) {
		Double value = values.get(key);
		return value == null ? fallback : value;
	}

	/** The number under {@code key}, or 0. */
	public double get(String key) {
		return get(key, 0.0D);
	}

	public int getInt(String key, int fallback) {
		Double value = values.get(key);
		return value == null ? fallback : (int) Math.round(value);
	}

	public boolean has(String key) {
		return values.containsKey(key);
	}

	public Map<String, Double> asMap() {
		return values;
	}

	@Override
	public String toString() {
		return values.toString();
	}
}
