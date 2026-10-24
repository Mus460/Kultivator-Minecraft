package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.kehancuran.WorldBreaker;
import net.minecraft.util.Mth;

/**
 * Section {@code perbaikan} of {@code config/kultivator-dunia.json} (DESIGN 38.R.7, owner 38PR), clamped on load and
 * refreshed in place by {@code uji jelajah muat}. The switches only gate behaviour: the payload, the commands and the
 * saved data are always registered (38.0 rule 17).
 */
public final class PerbaikanConfig {

	/** Recording, scanning and repairing of cared-for places at all. */
	public boolean aktif = true;
	/** Damage bills for players who broke a place (off: crews still repair, nobody is billed). */
	public boolean tagihanAktif = true;
	/** The fear of 38.R.5 (off: overwhelming players are billed like anyone). */
	public boolean takutAktif = true;
	/** Realm steps above the place's strength at which the place is too afraid to bill (38.R.5). */
	public int selisihTakut = 3;
	/** Seconds one craftsman needs per block at the base rate (38.R.3). */
	public int detikPerBlok = 5;
	/** Rate multiplier of a hired crew (the bill was paid). */
	public double pengaliSewa = 4.0D;
	/** Rate multiplier while the place is afraid of the culprit. */
	public double pengaliTakut = 0.33D;
	/** Blocks the repairs of the whole server may put back per tick (also under {@code kehancuranBlocksPerTick}). */
	public int blokPerTick = 8;
	/** Ticks between two scans of the same cared-for place (round robin; at most 4096 block reads per tick). */
	public int pindaiTick = 40;
	/** Chunks recorded into blueprints per tick. */
	public int rekamPerTick = 2;
	/** Place factor of the capital's bills (38.R.4). */
	public double faktorIbuKota = 2.5D;

	PerbaikanConfig clamp() {
		selisihTakut = Mth.clamp(selisihTakut, 1, 8);
		detikPerBlok = Mth.clamp(detikPerBlok, 1, 60);
		pengaliSewa = Mth.clamp(pengaliSewa, 1.0D, 8.0D);
		pengaliTakut = Mth.clamp(pengaliTakut, 0.1D, 1.0D);
		blokPerTick = Mth.clamp(blokPerTick, 1, 32);
		pindaiTick = Mth.clamp(pindaiTick, 10, 400);
		rekamPerTick = Mth.clamp(rekamPerTick, 1, 8);
		faktorIbuKota = Mth.clamp(faktorIbuKota, 1.0D, 5.0D);
		return this;
	}

	private static volatile PerbaikanConfig instance;

	/** The live section. */
	public static PerbaikanConfig get() {
		PerbaikanConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("perbaikan", PerbaikanConfig.class, PerbaikanConfig::new, PerbaikanConfig::clamp);
			instance = c;
			WorldBreaker.setRestoreBudget(c.blokPerTick);
		}
		return c;
	}

	/** From {@link Perbaikan#init()}: the repair budget follows the section after every reload. */
	static void init() {
		get();
		DuniaConfig.onReload(() -> WorldBreaker.setRestoreBudget(get().blokPerTick));
	}
}
