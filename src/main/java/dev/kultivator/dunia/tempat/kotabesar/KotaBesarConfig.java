package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.RencanaKotaBesar;
import net.minecraft.util.Mth;

/**
 * Section {@code kotaBesar} of {@code config/kultivator-dunia.json} (DESIGN 38.D.9, owner 38KS), clamped on load. The
 * plan switches are pushed into {@link RencanaKotaBesar} at once ({@link #terapkan}), so the capital's structure starts,
 * keep-out zones and road nodes follow the file; every registry entry stays registered either way (38.0 rule 17).
 */
public final class KotaBesarConfig {

	/** Registers the {@code kultivator:dunia_kotabesar} pack (the capital in new chunks). */
	public boolean worldgen = true;
	/** Capital behaviour (services, anchors, bodies); false leaves the walls standing and quiet. */
	public boolean aktif = true;
	/** First-ring radius from the world centre (38.B.2.2). */
	public int jarakPertamaMin = 1800;
	/** First-ring width. */
	public int jarakPertamaLebar = 1400;
	/** Chance that a far 4096-cell has a capital candidate. */
	public double peluangSel = 0.6D;
	/** Least distance between two capitals. */
	public int jarakAntar = 2500;
	/** Largest ground height difference the capital accepts (38.B.2.4). */
	public int selisihMaks = 20;
	/** Bodies of capital people per capital (38.B.7; 12-48). */
	public int tubuhMaks = 48;
	/** Curfew: the night drum closes the gates at 16000 (22.00) and the morning bell opens them at 1000 (07.00) (38.B.4.1). */
	public boolean jamMalam = true;
	/** Crime record, wanted state, fines and cells (38.B.5.2). */
	public boolean hukum = true;
	/** The capital calendar of events (38.B.6). */
	public boolean acara = true;
	/** The beast tide defence (38.B.6.3); off: the calendar skips it. */
	public boolean gelombangBinatang = true;
	/** The capital story chapters of 38ST (read by 38ST through {@link KotaBesarConfig#get()}). */
	public boolean alurCerita = true;

	KotaBesarConfig clamp() {
		jarakPertamaMin = Mth.clamp(jarakPertamaMin, 600, 20000);
		jarakPertamaLebar = Mth.clamp(jarakPertamaLebar, 0, 20000);
		peluangSel = Mth.clamp(peluangSel, 0.0D, 1.0D);
		jarakAntar = Mth.clamp(jarakAntar, 1000, 50000);
		selisihMaks = Mth.clamp(selisihMaks, 4, 64);
		tubuhMaks = Mth.clamp(tubuhMaks, 12, 96);
		return this;
	}

	private static volatile KotaBesarConfig instance;

	/** The live section (the same object is refreshed in place by {@code uji jelajah muat}, so it is cached). */
	public static KotaBesarConfig get() {
		KotaBesarConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("kotaBesar", KotaBesarConfig.class, KotaBesarConfig::new, KotaBesarConfig::clamp);
			instance = c;
		}
		return c;
	}

	/** Pushes the plan switches into the capital plan (on init and after a config reload). */
	static void terapkan() {
		KotaBesarConfig c = get();
		RencanaKotaBesar.setAturan(PutusanTanah.Aturan.kotaBesar(c.selisihMaks));
		RencanaKotaBesar.setJarak(c.jarakPertamaMin, c.jarakPertamaLebar, c.peluangSel, c.jarakAntar, c.worldgen);
	}

	/** True when the capital's living behaviour runs (config and the 38 master switch). */
	public static boolean hidup() {
		return get().aktif && dev.kultivator.dunia.tempat.TempatConfig.get().aktif;
	}
}
