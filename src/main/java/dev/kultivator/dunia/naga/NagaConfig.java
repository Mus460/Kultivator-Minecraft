package dev.kultivator.dunia.naga;

import dev.kultivator.data.Realm;

/**
 * Section {@code naga} of {@code config/kultivator-dunia.json} (DESIGN 34.5.11, 34.5.12, read through
 * {@code DuniaConfig.section}). Missing values get these defaults, out-of-range ones are clamped and written back.
 * Every big system has its own switch: {@link #wild} (wild Jiao in the sky), {@link #subdue} (the Selendang and the
 * Pertarungan Kehendak), {@link #mount} (riding a tamed Jiao), {@link #evolution} (the Kesengsaraan Binatang Roh) and
 * {@link #arts} (the tier 4+ bloodline arts and tier 6 owner passives).
 */
public final class NagaConfig {

	/** Wild Jiao fly over the Overworld. */
	public boolean wild = true;
	/** The lowest realm of a player near whom a wild Jiao may appear. */
	public String minRealm = "nascent_soul";
	/** The chance per roll (every 2 400 ticks per player), doubled during a meteor shower or twin moons. */
	public double chance = 0.06D;
	/** Wild Jiao alive at once on the whole server (0-4). */
	public int worldCap = 2;
	/** The Selendang Pengikat Naga may subdue a weakened Jiao. */
	public boolean subdue = true;
	/** A tamed Jiao may be ridden. */
	public boolean mount = true;
	/** Body segments of the longest Jiao (6-24); the client draws fewer on lower effect quality and with distance. */
	public int segmentsMax = 20;
	/** The beast tribulation (tiers 4-6) is on. */
	public boolean evolution = true;
	/** Strikes of the tribulation to tier 4, 5 and 6. */
	public int[] tribulationStrikes = {3, 5, 7};
	/** Each strike's share of the beast's max health, for tier 4, 5 and 6. */
	public double[] strikeShare = {0.12D, 0.16D, 0.20D};
	/** The share of each strike the owner takes as Hu Fa (within 6 blocks). */
	public double huFaShare = 0.3D;
	/** In-game days of Luka Kesengsaraan after a failed tribulation. */
	public int failCooldownDays = 1;
	/** Offerings from other modules (Batu Meteor Roh, Inti Iblis Kuno, Mutiara Naga, Persik Abadi) may be replaced by spirit stones. */
	public boolean fallbackItems = true;
	/** The tier 4+ bloodline arts and the tier 6 owner passives. */
	public boolean arts = true;
	/** A wild Jiao at half health enters Amukan Naga (faster, harder skills, a stormy sky grade for those near). */
	public boolean enrage = true;
	/** The kind skills: Pusaran Samudra (water), Tirai Awan (cloud), Hujan Petir Langit (thunder); also a tamed Naga Sejati's. */
	public boolean specials = true;
	/** While a soul walks Dunia Bawah, its owner's spirit beasts and tamed Jiao guard the body (Penjaga Raga). */
	public boolean guardBody = true;
	/** The tamed Jiao's remarks to its owner and the wild Jiao's warnings. */
	public boolean dialogue = true;
	/** The chance a slain wild Jiao leaves a Darah Jiao for its slayer (0-1). */
	public double darahChance = 0.5D;

	/** Clamps everything into range (called by {@code DuniaConfig} after every read). */
	public NagaConfig clamp() {
		if (minRealm == null || Realm.byId(minRealm) == null) {
			minRealm = "nascent_soul";
		}
		chance = Double.isFinite(chance) ? Math.max(0.0D, Math.min(1.0D, chance)) : 0.06D;
		worldCap = Math.max(0, Math.min(4, worldCap));
		segmentsMax = Math.max(6, Math.min(24, segmentsMax));
		tribulationStrikes = three(tribulationStrikes, new int[]{3, 5, 7});
		for (int i = 0; i < 3; i++) {
			tribulationStrikes[i] = Math.max(1, Math.min(12, tribulationStrikes[i]));
		}
		strikeShare = threeD(strikeShare, new double[]{0.12D, 0.16D, 0.20D});
		for (int i = 0; i < 3; i++) {
			strikeShare[i] = Double.isFinite(strikeShare[i]) ? Math.max(0.01D, Math.min(0.5D, strikeShare[i])) : 0.15D;
		}
		huFaShare = Double.isFinite(huFaShare) ? Math.max(0.0D, Math.min(0.9D, huFaShare)) : 0.3D;
		failCooldownDays = Math.max(0, Math.min(7, failCooldownDays));
		darahChance = Double.isFinite(darahChance) ? Math.max(0.0D, Math.min(1.0D, darahChance)) : 0.5D;
		return this;
	}

	/** The realm {@link #minRealm} names. */
	public Realm minRealm() {
		Realm realm = Realm.byId(minRealm);
		return realm == null ? Realm.NASCENT_SOUL : realm;
	}

	/** Strikes of the tribulation to {@code tier} (4-6). */
	public int strikes(int tier) {
		return tribulationStrikes[Math.max(0, Math.min(2, tier - 4))];
	}

	/** One strike's share of max health on the way to {@code tier} (4-6). */
	public double share(int tier) {
		return strikeShare[Math.max(0, Math.min(2, tier - 4))];
	}

	private static int[] three(int[] values, int[] fallback) {
		if (values == null || values.length != 3) {
			return fallback;
		}
		return values;
	}

	private static double[] threeD(double[] values, double[] fallback) {
		if (values == null || values.length != 3) {
			return fallback;
		}
		return values;
	}
}
