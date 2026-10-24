package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * Section {@code cerita} of {@code config/kultivator-dunia.json} (DESIGN 38.D.9, owner 38ST), clamped on load. The switch
 * only gates behaviour: the payload, commands and data loader stay registered either way (38.0 rule 17).
 */
public final class CeritaConfig {

	/** Data dialogs, quests, rumours and the capital story arc. Off: 38 NPCs fall back to their spoken line. */
	public boolean aktif = true;
	/** Live rumours kept per place (38.S.5: 24); the world-wide list keeps half of this, at least 12. */
	public int rumorMaks = 24;
	/** The capital arc "Bayangan di Balik Takhta" (38.S.3). Off: chapters never start, quests of the arc stay hidden. */
	public boolean alur = true;
	/** Short story scenes (7b rules: at most 4 s, skippable, never in danger; the client camera setting still decides). */
	public boolean adegan = true;
	/** Days a capital chapter waits for players before it advances by itself with its default outcome (38.S.3: 6). */
	public int batasBab = 6;

	CeritaConfig clamp() {
		rumorMaks = Mth.clamp(rumorMaks, 4, 48);
		batasBab = Mth.clamp(batasBab, 2, 30);
		return this;
	}

	private static volatile CeritaConfig instance;

	/** The live section (refreshed in place by {@code uji jelajah muat}, so it is cached). */
	public static CeritaConfig get() {
		CeritaConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("cerita", CeritaConfig.class, CeritaConfig::new, CeritaConfig::clamp);
			instance = c;
		}
		return c;
	}

	/** True while stories run: the tempat master switch and {@link #aktif}. */
	public static boolean jalan() {
		return CeritaConfig.get().aktif && dev.kultivator.dunia.tempat.TempatConfig.get().aktif
				&& dev.kultivator.dunia.DuniaSpine.enabled();
	}
}
