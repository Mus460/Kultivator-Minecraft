package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.dunia.DuniaConfig;

/**
 * Section {@code gua} of {@code config/kultivator-dunia.json} (DESIGN 38.D.9, owner 38GU), clamped on load. Registry
 * entries (entity types, payload, advancements) stay registered either way (38.0 rule 17); the switches only gate the
 * world generation pack and the behaviour of the caves and natural sites.
 */
public final class GuaConfig {

	/** Registers the {@code kultivator:dunia_gua} pack: caves and natural sites in new chunks only. Read once at start. */
	public boolean worldgen = true;
	/** The behaviour of every cave and site (hermit, tomb trials, lairs, mirror, peak, stage, spring, place factors). */
	public boolean aktif = true;
	/** The two Crystal Guardians of a spirit-vein cave wake on the first visit of a world day. */
	public boolean penjagaKristal = true;

	GuaConfig clamp() {
		return this;
	}

	private static volatile GuaConfig instance;

	/** The live section (refreshed in place by {@code uji jelajah muat}). */
	public static GuaConfig get() {
		GuaConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("gua", GuaConfig.class, GuaConfig::new, GuaConfig::clamp);
			instance = c;
		}
		return c;
	}

	/** True when caves and sites behave (the master switch of 38 is read too). */
	public static boolean on() {
		return get().aktif && dev.kultivator.dunia.tempat.TempatConfig.get().aktif;
	}
}
