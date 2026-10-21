package dev.kultivator.dunia.api;

import java.util.Locale;

/** The random sky events of module L (DESIGN 34.5.8, contract C34.S3). */
public enum SkyKind {
	METEOR_SHOWER,
	BLOOD_ECLIPSE,
	TWIN_MOONS;

	/** "meteor_shower", "blood_eclipse", "twin_moons" (lang keys and commands). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
