package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * Section {@code ekonomi38} of {@code config/kultivator-dunia.json} (DESIGN 38.D.9, owner 38EK), clamped on load. The
 * switches only gate behaviour: every item, entity, component and payload of the money loop stays registered (38.0 rule
 * 17). Read through {@link #get()}.
 */
public final class EkonomiConfig {

	/** Weapon shops, forge orders, beast market, inns, dishes and services of 38.M.2. */
	public boolean toko = true;
	/** Rumah Uang: deposits, Surat Wesel, pawn, Batu Asal exchange, vault (38.M.2.2). */
	public boolean bank = true;
	/** Stone gambling (38.M.2.4). */
	public boolean judi = true;
	/** Expected value of one gambling stone as a share of its price (0.80-0.92, design 0.88). */
	public double judiEv = 0.88D;
	/** Sect stalls in the capital (38.M.4.1). */
	public boolean lapak = true;
	/** Sect caravans and road caravans (38.M.4.2, 38.C.6.3). */
	public boolean kafilah = true;
	/** Commission of the Grand Auction (a sink into the town treasury, 38.M.2.3). */
	public double komisiLelang = 0.10D;
	/** Global price factor of every 38 shop (0.5-2). */
	public double pengaliHarga = 1.0D;
	/** The graded guild board E-S, badges and the Young Dragon list (38.M.3). */
	public boolean gilda = true;

	private static EkonomiConfig instance;

	/** The live section (loaded on first use; the spine's reload re-clamps it in place). */
	public static synchronized EkonomiConfig get() {
		EkonomiConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("ekonomi38", EkonomiConfig.class, EkonomiConfig::new, EkonomiConfig::clamp);
			instance = c;
		}
		return c;
	}

	EkonomiConfig clamp() {
		judiEv = Mth.clamp(judiEv, 0.80D, 0.92D);
		komisiLelang = Mth.clamp(komisiLelang, 0.0D, 0.25D);
		pengaliHarga = Mth.clamp(pengaliHarga, 0.5D, 2.0D);
		return this;
	}
}
