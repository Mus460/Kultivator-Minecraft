package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * The themed-town config of DESIGN 38.T.13.1 in {@code config/kultivator-dunia.json}. {@code DuniaConfig.section} is one
 * level deep, so the design's {@code kotaTema.<tema>.<kunci>} lives in six sections: {@code kotaTema} (shared) and
 * {@code temaPelabuhan}, {@code temaOasis}, {@code temaAir}, {@code temaGunung}, {@code temaIblis} (no clash with
 * {@code kota}, {@code langit}, {@code sekteLangit}, {@code tempat}, {@code kotaBesar}, {@code kampung},
 * {@code kompleksSekte}, {@code gua}, {@code jalan}). Every value is clamped on load.
 * <p>
 * Rule K1 / 38.0 butir 17: items, entities, payloads, structures and placements of the themes are ALWAYS registered; the
 * switches below only stop behaviour and worldgen packs ({@code worldgen} false: the theme's plan answers "not suitable"
 * for every candidate, so there is never half a town). Loaded once by {@link KotaTema#init()}; {@code DuniaConfig.reload}
 * copies new values into these same objects.
 */
public final class TemaConfig {

	/** Section {@code kotaTema}: the shared switches and numbers. Public fields: Gson reads them. */
	public static final class Inti {
		/** Master switch of all themed-town behaviour (NPC bodies, events, mechanisms, shops). */
		public boolean aktif = true;
		/** Pack {@code kultivator:dunia_kotatema} (all five themes and both satellites). */
		public boolean worldgen = true;
		/** NPC bodies per themed town (12-36), budget kind {@code npc_kota_tema}. */
		public int tubuhMaks = 32;
		/** Live mechanism entities per town (4-16); global cap 24 ({@code mekanisme_tema}). */
		public int mekanismeMaks = 12;
		/** Smallest distance from any suitable capital (800-3000). */
		public int jarakDariIbuKota = 1200;
		/** Smallest distance between two suitable themed towns (600-3000). */
		public int jarakAntarTema = 900;
		/** Inter-town trade goods (buy/sell, saturation, weekly cap). */
		public boolean dagang = true;
		/** Weekly trade-profit cap per player, multiplied by band + 1 (50-400). */
		public int batasLabaMingguan = 150;
		/** Sale saturation per unit sold in 24 world hours (0.02-0.08); floor 0.6. */
		public double jenuhPerUnit = 0.04D;
		/** Festivals and scheduled events of {@code TemaKalender}. */
		public boolean acara = true;
		/** Dangers (pirates, sandstorm, rockfall, monkeys, Tikus Air, Jam Berburu, hunters). */
		public boolean bahaya = true;
		/** The five quest chains. */
		public boolean alurCerita = true;
		/** Cargo contracts (land, sea, sky). */
		public boolean kontrakMuatan = true;

		Inti clamp() {
			tubuhMaks = Mth.clamp(tubuhMaks, 12, 72);
			mekanismeMaks = Mth.clamp(mekanismeMaks, 4, 16);
			jarakDariIbuKota = Mth.clamp(jarakDariIbuKota, 800, 3000);
			jarakAntarTema = Mth.clamp(jarakAntarTema, 600, 3000);
			batasLabaMingguan = Mth.clamp(batasLabaMingguan, 50, 400);
			jenuhPerUnit = finite(jenuhPerUnit, 0.04D, 0.02D, 0.08D);
			return this;
		}
	}

	/** Section {@code temaPelabuhan}. */
	public static final class Pelabuhan {
		public boolean worldgen = true;
		/** Chance a far cell holds a harbour (0-1). */
		public double peluangSel = 0.7D;
		/** Far-cell size in blocks (1536-8192). */
		public int ukuranSel = 3072;
		/** Scheduled merchant junks at the three moorings. */
		public boolean kapalDagang = true;
		/** Kapal Lintas Laut to the Laut Tanpa Batas. */
		public boolean lintasLaut = true;
		/** Pirate raids on the Dermaga Agung. */
		public boolean serbuanBajakLaut = true;
		/** Shipyard keel orders. */
		public boolean galangan = true;

		Pelabuhan clamp() {
			peluangSel = finite(peluangSel, 0.7D, 0.0D, 1.0D);
			ukuranSel = Mth.clamp(ukuranSel, 1536, 8192);
			return this;
		}
	}

	/** Section {@code temaOasis}. */
	public static final class Oasis {
		public boolean worldgen = true;
		public double peluangSel = 0.7D;
		public int ukuranSel = 3584;
		/** Local sandstorms (never the world weather). */
		public boolean badaiPasir = true;
		/** Fewest world days between two storms of one town (2-30). */
		public int badaiHariMin = 4;
		/** Most world days between two storms of one town (badaiHariMin-40). */
		public int badaiHariMaks = 8;
		/** Camel caravans (Kafilah Agung, riding, hired camels). */
		public boolean kafilahUnta = true;
		/** The {@code reruntuhan_gurun} satellite and its Raja Kalajengking. */
		public boolean reruntuhan = true;

		Oasis clamp() {
			peluangSel = finite(peluangSel, 0.7D, 0.0D, 1.0D);
			ukuranSel = Mth.clamp(ukuranSel, 1536, 8192);
			badaiHariMin = Mth.clamp(badaiHariMin, 2, 30);
			badaiHariMaks = Mth.clamp(badaiHariMaks, badaiHariMin, 40);
			return this;
		}
	}

	/** Section {@code temaAir}. */
	public static final class Air {
		public boolean worldgen = true;
		public double peluangSel = 0.5D;
		public int ukuranSel = 3072;
		/** Perahu Pos boat lines between the canal stops. */
		public boolean perahuPos = true;
		/** Malam Lentera Air and the floating lanterns. */
		public boolean lenteraAir = true;
		/** Dragon-boat race. */
		public boolean perahuNaga = true;
		/** The Tikus Air canal thieves. */
		public boolean tikusAir = true;

		Air clamp() {
			peluangSel = finite(peluangSel, 0.5D, 0.0D, 1.0D);
			ukuranSel = Mth.clamp(ukuranSel, 1536, 8192);
			return this;
		}
	}

	/** Section {@code temaGunung}. */
	public static final class Gunung {
		public boolean worldgen = true;
		public double peluangSel = 0.6D;
		public int ukuranSel = 3072;
		/** Cable car and pulley baskets. */
		public boolean keretaGantung = true;
		/** Rockfall in rain (rolling entities, never a block). */
		public boolean longsor = true;
		/** Food-snatching mountain monkeys. */
		public boolean keraGunung = true;
		/** The Buddha's-light dawn phenomenon. */
		public boolean cahayaBudha = true;

		Gunung clamp() {
			peluangSel = finite(peluangSel, 0.6D, 0.0D, 1.0D);
			ukuranSel = Mth.clamp(ukuranSel, 1536, 8192);
			return this;
		}
	}

	/** Section {@code temaIblis}. */
	public static final class Iblis {
		public boolean worldgen = true;
		public double peluangSel = 0.45D;
		public int ukuranSel = 4096;
		/** Night toll ambushes outside the Zona Perjanjian. */
		public boolean jamBerburu = true;
		/** The righteous coalition's siege of the Skull Gate. */
		public boolean penyerbuanLurus = true;
		/** Righteous bounty squads that intercept high-infamy players. */
		public boolean pemburuLurus = true;
		/** The global Infami meter. */
		public boolean infami = true;
		/** Expected value of an arena purse (0.80-0.92). */
		public double arenaEv = 0.90D;
		/** Expected value of a bet (0.80-0.92). */
		public double taruhanEv = 0.90D;

		Iblis clamp() {
			peluangSel = finite(peluangSel, 0.45D, 0.0D, 1.0D);
			ukuranSel = Mth.clamp(ukuranSel, 1536, 8192);
			arenaEv = finite(arenaEv, 0.90D, 0.80D, 0.92D);
			taruhanEv = finite(taruhanEv, 0.90D, 0.80D, 0.92D);
			return this;
		}
	}

	private static Inti inti;
	private static Pelabuhan pelabuhan;
	private static Oasis oasis;
	private static Air air;
	private static Gunung gunung;
	private static Iblis iblis;

	/** Loads (once) all six sections; from {@link KotaTema#init()}. */
	public static synchronized void muat() {
		if (inti != null) {
			return;
		}
		inti = DuniaConfig.section("kotaTema", Inti.class, Inti::new, Inti::clamp);
		pelabuhan = DuniaConfig.section("temaPelabuhan", Pelabuhan.class, Pelabuhan::new, Pelabuhan::clamp);
		oasis = DuniaConfig.section("temaOasis", Oasis.class, Oasis::new, Oasis::clamp);
		air = DuniaConfig.section("temaAir", Air.class, Air::new, Air::clamp);
		gunung = DuniaConfig.section("temaGunung", Gunung.class, Gunung::new, Gunung::clamp);
		iblis = DuniaConfig.section("temaIblis", Iblis.class, Iblis::new, Iblis::clamp);
	}

	public static Inti inti() {
		muat();
		return inti;
	}

	public static Pelabuhan pelabuhan() {
		muat();
		return pelabuhan;
	}

	public static Oasis oasis() {
		muat();
		return oasis;
	}

	public static Air air() {
		muat();
		return air;
	}

	public static Gunung gunung() {
		muat();
		return gunung;
	}

	public static Iblis iblis() {
		muat();
		return iblis;
	}

	/** Behaviour of this theme is on (master switch and the shared switch). */
	public static boolean aktif(Tema tema) {
		return inti().aktif && tema != null;
	}

	/** Worldgen of this theme is on (shared pack switch and the theme's own); false: every candidate is "not suitable". */
	public static boolean worldgen(Tema tema) {
		if (tema == null || !inti().worldgen) {
			return false;
		}
		return switch (tema) {
			case PELABUHAN -> pelabuhan().worldgen;
			case OASIS -> oasis().worldgen;
			case AIR -> air().worldgen;
			case GUNUNG -> gunung().worldgen;
			case IBLIS -> iblis().worldgen;
		};
	}

	/** The live far-cell chance of this theme. */
	public static double peluangSel(Tema tema) {
		return switch (tema) {
			case PELABUHAN -> pelabuhan().peluangSel;
			case OASIS -> oasis().peluangSel;
			case AIR -> air().peluangSel;
			case GUNUNG -> gunung().peluangSel;
			case IBLIS -> iblis().peluangSel;
		};
	}

	/** The live far-cell size of this theme in blocks. */
	public static int ukuranSel(Tema tema) {
		return switch (tema) {
			case PELABUHAN -> pelabuhan().ukuranSel;
			case OASIS -> oasis().ukuranSel;
			case AIR -> air().ukuranSel;
			case GUNUNG -> gunung().ukuranSel;
			case IBLIS -> iblis().ukuranSel;
		};
	}

	private static double finite(double value, double fallback, double min, double max) {
		return Double.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
	}

	private TemaConfig() {
	}
}
