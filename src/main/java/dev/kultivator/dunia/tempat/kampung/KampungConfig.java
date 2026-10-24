package dev.kultivator.dunia.tempat.kampung;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * Section {@code kampung} of {@code config/kultivator-dunia.json} (DESIGN 38.D.9, owner 38DS), clamped on load.
 * Registry entries (structures, sets, the anchors' handler) exist whatever these switches say (38.0 rule 17); the
 * switches gate the world generation pack {@code kultivator:dunia_kampung} and the behaviour of the villages.
 */
public final class KampungConfig {

	/** Registers {@code kultivator:dunia_kampung} (mortal villages and clan villages, new chunks only). */
	public boolean worldgen = true;
	/** Village life of this module: markets, harvest festival, clans, talented children, tribute bonus. */
	public boolean aktif = true;
	/** Bodies this module keeps per village at once (the young master, a sparring clan disciple, the market keeper). */
	public int tubuhMaks = 10;
	/** The Harvest Festival every {@link #siklusPanen} days (38.C.2.3: {@code d % 12 == 0}). */
	public boolean festivalPanen = true;
	/** Days between two Harvest Festivals of one village. */
	public int siklusPanen = 12;
	/** Loyalty a pledged village gains from its festival (through the public {@code Desa.loyaltyAt}). */
	public int kesetiaanPanen = 2;
	/** Days between two talented children of one pledged village (38.M.4.3: one per 10 days). */
	public int hariAnakBerbakat = 10;
	/** Regard a clan needs before it lets a sect pledge it without a beaten patriarch (38.C.2.3). */
	public int sikapIkrarKlan = 50;
	/** Realm ordinal below which the young master challenges a visitor (Foundation = 2: Qi Refining and mortals). */
	public int ranahTantangan = 2;
	/** Price factor of the village market (38.M.2.2: {@code Harga.value x 0.9}). */
	public double hargaPasar = 0.9D;

	KampungConfig clamp() {
		tubuhMaks = Mth.clamp(tubuhMaks, 0, 24);
		siklusPanen = Mth.clamp(siklusPanen, 3, 60);
		kesetiaanPanen = Mth.clamp(kesetiaanPanen, 0, 10);
		hariAnakBerbakat = Mth.clamp(hariAnakBerbakat, 3, 60);
		sikapIkrarKlan = Mth.clamp(sikapIkrarKlan, 0, 100);
		ranahTantangan = Mth.clamp(ranahTantangan, 0, 8);
		hargaPasar = Mth.clamp(hargaPasar, 0.5D, 2.0D);
		return this;
	}

	private static volatile KampungConfig instance;

	/** The live section (cached; {@code uji jelajah muat} refreshes the same object in place). */
	public static KampungConfig get() {
		KampungConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("kampung", KampungConfig.class, KampungConfig::new, KampungConfig::clamp);
			instance = c;
		}
		return c;
	}

	/** True when village life runs ({@code kampung.aktif} and {@code tempat.aktif}). */
	public static boolean hidup() {
		return get().aktif && dev.kultivator.dunia.tempat.TempatConfig.get().aktif;
	}
}
