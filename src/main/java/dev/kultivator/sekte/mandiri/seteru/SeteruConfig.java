package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.mandiri.api.MandiriIds;

import java.util.Locale;

/**
 * Section {@code mandiri_seteru} of {@code config/kultivator-sekte.json} (DESIGN 39.F.8), owned by module 39F. Only gates
 * behaviour (K1: the payload types and every registry entry are registered whatever it says). Reloaded in place by
 * {@code /kultivasi uji perguruan muat}.
 * <p>
 * {@link #bolehPerang} stays <b>false</b> by default (schedule 0.1: not approved by the user). While it is false the
 * ladder's top rung T7 ends at the official inter-sect tournament challenge or an envoy of peace, never a war.
 */
public final class SeteruConfig {

	/** Intensity: how far the ladder may climb. */
	public enum Intensitas {
		/** At most T3 (brawls). */
		RENDAH(3),
		/** The default: at most T6 (a diplomatic incident). */
		SEDANG(6),
		/** T7: the sect masters act (challenge, war only with {@code bolehPerang}). */
		TINGGI(7);

		private final int maks;

		Intensitas(int maks) {
			this.maks = maks;
		}

		public int maks() {
			return maks;
		}

		public static Intensitas byName(String name) {
			if (name != null) {
				for (Intensitas value : values()) {
					if (value.name().equalsIgnoreCase(name.trim())) {
						return value;
					}
				}
			}
			return SEDANG;
		}
	}

	/** The module lives (together with {@code umum.mandiri} and {@code mandiri.aktif}). */
	public boolean aktif = true;
	/** Multiplier of every trigger chance (0-3). */
	public double frekuensi = 1.0D;
	/** {@code RENDAH} (max T3), {@code SEDANG} (max T6, default), {@code TINGGI} (T7). */
	public String intensitas = Intensitas.SEDANG.name();
	/** War as a T7 branch (only at TINGGI); needs the user's approval to default to true. */
	public boolean bolehPerang = false;
	/** Active feuds per hall (one per disciple). */
	public int seteruMaksAula = 3;
	/** Heat lost per world day. */
	public int panasTurun = 2;
	/** Cross-sect friendship and forbidden lovers. */
	public boolean asmaraLintas = true;
	/** Patrol gangs on the roads from T4. */
	public boolean patroli = true;
	/** Rival bodies of 39F at once on the server (patrols included). */
	public int tubuhMaks = 4;
	/** Townsfolk gathering around a duel (38); 4 without 38. Caps the watchers of a feud duel ({@code HidupApi.kerumunan}). */
	public int kerumunanMaks = 8;
	/** The most standing a sect may lose through feuds in one week. */
	public int standingMingguMaks = 20;
	/** The trigger chances of 39.F.2. */
	public Peluang peluang = new Peluang();

	/** Chances of 39.F.2 (0-1). */
	public static final class Peluang {
		/** A sect's disciples are present at a place: base when the gate is within 3000 blocks. */
		public double hadir = 0.25D;
		public double hinaan = 0.30D;
		public double harta = 0.50D;
		/** Two sects take the same guild mission. */
		public double gildaSama = 0.12D;
		public double gilda = 0.60D;
		/** A lost tournament (the loser is proud or vengeful) and for everyone else. */
		public double turnamen = 0.40D;
		public double turnamenLain = 0.15D;
		public double asmara = 0.20D;
		/** One of their cunning members steals a scroll from our bag; one of ours steals from them. */
		public double curiMereka = 0.03D;
		public double curiKita = 0.02D;
		/** Our disciple steps in against a bully at the market (kind/brave 0.9, coward 0.2). */
		public double perundung = 0.60D;
		/** A demonic or neutral sect squeezes a village (then 0.7 an incident). */
		public double desaPeras = 0.10D;
		public double desa = 0.70D;
		/** A D event of the sect starts a new feud. */
		public double peristiwaBaru = 0.30D;
		/** A meeting that was no incident becomes a friendship. */
		public double sahabat = 0.20D;
		/** A patrol ambushes a journey along its road. */
		public double sergapPatroli = 0.25D;
		/** Forbidden lovers meet at a night market or festival; are found out. */
		public double rahasia = 0.25D;
		public double ketahuan = 0.15D;
	}

	private static SeteruConfig instance = new SeteruConfig();

	/** Reads (or re-reads) the section; called from {@code Seteru.init}. */
	static SeteruConfig load() {
		instance = SekteConfig.section(MandiriIds.DATA_SETERU,SeteruConfig.class, SeteruConfig::new, SeteruConfig::clamp);
		return instance;
	}

	public static SeteruConfig get() {
		return instance;
	}

	public Intensitas tingkatIntensitas() {
		return Intensitas.byName(intensitas);
	}

	/** War may be a T7 branch: only with the switch on and at TINGGI. */
	public boolean perangBoleh() {
		return bolehPerang && tingkatIntensitas() == Intensitas.TINGGI;
	}

	/** A chance scaled by {@link #frekuensi}, clamped to [0, 1]. */
	public double kali(double peluangDasar) {
		return Math.max(0.0D, Math.min(1.0D, peluangDasar * frekuensi));
	}

	private static SeteruConfig clamp(SeteruConfig c) {
		c.frekuensi = clamp(c.frekuensi, 0.0D, 3.0D);
		c.intensitas = Intensitas.byName(c.intensitas).name().toUpperCase(Locale.ROOT);
		c.seteruMaksAula = clamp(c.seteruMaksAula, 0, 3);
		c.panasTurun = clamp(c.panasTurun, 0, 20);
		c.tubuhMaks = clamp(c.tubuhMaks, 0, 4);
		c.kerumunanMaks = clamp(c.kerumunanMaks, 0, 8);
		c.standingMingguMaks = clamp(c.standingMingguMaks, 0, 100);
		if (c.peluang == null) {
			c.peluang = new Peluang();
		}
		Peluang p = c.peluang;
		p.hadir = clamp(p.hadir, 0.0D, 1.0D);
		p.hinaan = clamp(p.hinaan, 0.0D, 1.0D);
		p.harta = clamp(p.harta, 0.0D, 1.0D);
		p.gildaSama = clamp(p.gildaSama, 0.0D, 1.0D);
		p.gilda = clamp(p.gilda, 0.0D, 1.0D);
		p.turnamen = clamp(p.turnamen, 0.0D, 1.0D);
		p.turnamenLain = clamp(p.turnamenLain, 0.0D, 1.0D);
		p.asmara = clamp(p.asmara, 0.0D, 1.0D);
		p.curiMereka = clamp(p.curiMereka, 0.0D, 1.0D);
		p.curiKita = clamp(p.curiKita, 0.0D, 1.0D);
		p.perundung = clamp(p.perundung, 0.0D, 1.0D);
		p.desaPeras = clamp(p.desaPeras, 0.0D, 1.0D);
		p.desa = clamp(p.desa, 0.0D, 1.0D);
		p.peristiwaBaru = clamp(p.peristiwaBaru, 0.0D, 1.0D);
		p.sahabat = clamp(p.sahabat, 0.0D, 1.0D);
		p.sergapPatroli = clamp(p.sergapPatroli, 0.0D, 1.0D);
		p.rahasia = clamp(p.rahasia, 0.0D, 1.0D);
		p.ketahuan = clamp(p.ketahuan, 0.0D, 1.0D);
		return c;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
