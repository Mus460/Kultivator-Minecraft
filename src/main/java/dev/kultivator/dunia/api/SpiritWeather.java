package dev.kultivator.dunia.api;

import java.util.Locale;

/** The spirit weather of a cell (module L, DESIGN 34.5.10, contract C34.S3); independent of vanilla weather. */
public enum SpiritWeather {
	CLEAR,
	THIN_MIST,
	DENSE_MIST,
	PURPLE_DAWN;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** True for both Qi mists and the purple dawn. */
	public boolean misty() {
		return this != CLEAR;
	}
}
