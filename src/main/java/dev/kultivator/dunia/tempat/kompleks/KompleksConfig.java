package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * Section {@code kompleksSekte} of {@code config/kultivator-dunia.json} (DESIGN 38.D.9, owner 38DS), clamped on load.
 * Registry entries (structure and placement types, the plan) exist whatever these switches say (38.0 rule 17); the
 * switches gate the world generation pack {@code kultivator:dunia_kompleks} and the behaviour of the compounds.
 */
public final class KompleksConfig {

	/** Registers {@code kultivator:dunia_kompleks} (compounds behind the NPC sect gates, new chunks only). */
	public boolean worldgen = true;
	/** Residents, stances, the sect market and the training ground. */
	public boolean aktif = true;
	/** Bodies this module keeps per compound at once (guards, elders, market keeper; 38.C.3.3, kind {@code npc_kompleks}). */
	public int tubuhMaks = 12;
	/** Largest ground height difference under a compound (5 x 5 samples, 38.C.3.1). */
	public int selisihMaks = 30;
	/** Discount of the sect market for allies (fraction; 38.C.3.3 says 10 %, bounded by 38.0 rule 4 at 15 %). */
	public double diskonSekutu = 0.10D;
	/** Standing lost when a hostile visitor walks into the courtyard (once per visit). */
	public int masukBermusuhan = 2;
	/** Standing lost per half minute a visitor of a cool or neutral hall lingers in the inner courts. */
	public int masukDalam = 1;
	/** Standing gained by a friendly sparring bout on the training ground (at most once per day per sect). */
	public int tandingStanding = 1;

	KompleksConfig clamp() {
		tubuhMaks = Mth.clamp(tubuhMaks, 0, 24);
		selisihMaks = Mth.clamp(selisihMaks, 8, 48);
		diskonSekutu = Mth.clamp(diskonSekutu, 0.0D, 0.15D);
		masukBermusuhan = Mth.clamp(masukBermusuhan, 0, 10);
		masukDalam = Mth.clamp(masukDalam, 0, 5);
		tandingStanding = Mth.clamp(tandingStanding, 0, 3);
		return this;
	}

	private static volatile KompleksConfig instance;

	/** The live section (cached; {@code uji jelajah muat} refreshes the same object in place). */
	public static KompleksConfig get() {
		KompleksConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("kompleksSekte", KompleksConfig.class, KompleksConfig::new, KompleksConfig::clamp);
			instance = c;
		}
		return c;
	}
}
