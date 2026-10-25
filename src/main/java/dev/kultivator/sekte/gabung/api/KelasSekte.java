package dev.kultivator.sekte.gabung.api;

import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The five sect classes of DESIGN 41.4.1, derived from the NPC sect's existing tier 1-9
 * ({@code DiplomasiService.tier}), so there is never a second number to keep in step. Pure data, safe on both sides.
 * <p>
 * {@link #kf} multiplies promotion PK costs (41.6.1), the weekly allowance (41.6.7) and PK rewards (41.6.2); Paviliun
 * prices are never multiplied (one price source, 41.0.4). The offer columns of 41.4.5 (best technique grade, name
 * protection against ambushes, compound meditation bonus, highest mission rank) live here as well, so the screens,
 * the shops and the ambush provider read one table.
 */
public enum KelasSekte {

	/** Perguruan Kecil, tier 1-2, "Sekte Kelas Tiga". */
	KECIL(1, "kecil", 1, 2, 3, 40, 0.6D, TechniqueGrade.HUANG, 0.90D, 512, 1.10D, 2, 0xA89F91),
	/** Sekte Menengah, tier 3-4, "Sekte Kelas Dua". */
	MENENGAH(2, "menengah", 3, 4, 4, 80, 0.8D, TechniqueGrade.XUAN, 0.85D, 768, 1.15D, 3, 0x7FB3A3),
	/** Sekte Besar, tier 5-6, "Sekte Kelas Satu". */
	BESAR(3, "besar", 5, 6, 5, 120, 1.0D, TechniqueGrade.DI, 0.75D, 1024, 1.20D, 4, 0x5C9EE0),
	/** Sekte Agung, tier 7-8, "Tanah Suci". */
	AGUNG(4, "agung", 7, 8, 6, 120, 1.4D, TechniqueGrade.TIAN, 0.65D, 1536, 1.25D, 5, 0xB487F0),
	/** Sekte Abadi, tier 9, "Sekte Abadi". */
	ABADI(5, "abadi", 9, 9, 7, 120, 2.0D, TechniqueGrade.ABADI, 0.55D, 2048, 1.30D, 5, 0xF2D27A);

	/** Mission ranks E..S as indices 0..5 (the same letters as 38.M.3). */
	public static final String[] PANGKAT_MISI = {"E", "D", "C", "B", "A", "S"};

	private final int nomor;
	private final String id;
	private final int tierMin;
	private final int tierMax;
	private final int kursiTetua;
	private final int penduduk;
	private final double kf;
	private final TechniqueGrade teknikMaks;
	private final double faktorSergap;
	private final int radiusSergap;
	private final double bonusMeditasi;
	private final int misiMaks;
	private final int warna;

	KelasSekte(int nomor, String id, int tierMin, int tierMax, int kursiTetua, int penduduk, double kf, TechniqueGrade teknikMaks,
			double faktorSergap, int radiusSergap, double bonusMeditasi, int misiMaks, int warna) {
		this.nomor = nomor;
		this.id = id;
		this.tierMin = tierMin;
		this.tierMax = tierMax;
		this.kursiTetua = kursiTetua;
		this.penduduk = penduduk;
		this.kf = kf;
		this.teknikMaks = teknikMaks;
		this.faktorSergap = faktorSergap;
		this.radiusSergap = radiusSergap;
		this.bonusMeditasi = bonusMeditasi;
		this.misiMaks = misiMaks;
		this.warna = warna;
	}

	/** 1 (Kecil) .. 5 (Abadi), the saved and synced class number. */
	public int nomor() {
		return nomor;
	}

	public String id() {
		return id;
	}

	public int tierMin() {
		return tierMin;
	}

	public int tierMax() {
		return tierMax;
	}

	/** Elder seats of the class (41.4.1): 3, 4, 5, 6, 7. */
	public int kursiTetua() {
		return kursiTetua;
	}

	/** Data population of 38 (Agung/Abadi get +30 on a sky palace, counted by 37). */
	public int penduduk() {
		return penduduk;
	}

	/** The class factor of PK costs, allowances and rewards. */
	public double kf() {
		return kf;
	}

	/** The best technique grade the Paviliun Jasa of this class stocks (41.4.5). */
	public TechniqueGrade teknikMaks() {
		return teknikMaks;
	}

	/** Ambush chance multiplier for members of rank Luar+ near the gate (41.4.5 "Perlindungan nama"). */
	public double faktorSergap() {
		return faktorSergap;
	}

	/** Radius in blocks around the gate where {@link #faktorSergap} applies. */
	public int radiusSergap() {
		return radiusSergap;
	}

	/** Meditation factor of the {@code gabung_kompleks} SpiritSites provider inside the compound. */
	public double bonusMeditasi() {
		return bonusMeditasi;
	}

	/** Highest board mission rank (index into {@link #PANGKAT_MISI}); Abadi adds the Langit missions. */
	public int misiMaks() {
		return misiMaks;
	}

	/** The class colour of directory cards and the class banner trim (client visuals of 41V). */
	public int warna() {
		return warna;
	}

	/** True for Besar and above: guards come when a member is attacked near the gate (41.4.5). */
	public boolean penjagaDatang() {
		return nomor >= BESAR.nomor;
	}

	/** "Perguruan Kecil" / "Minor School". */
	public MutableComponent nama() {
		return Component.translatable("kultivator.gabung.kelas." + id);
	}

	/** The world title ("Sekte Kelas Tiga", "Tanah Suci"). */
	public MutableComponent gelar() {
		return Component.translatable("kultivator.gabung.kelas." + id + ".gelar");
	}

	// ================================================================== tier mapping (41.4.1)

	/** The class number 1-5 of tier {@code tier}: {@code <= 2 -> 1}, {@code <= 4 -> 2}, {@code <= 6 -> 3}, {@code <= 8 -> 4}, else 5. */
	public static int of(int tier) {
		if (tier <= 2) {
			return 1;
		}
		if (tier <= 4) {
			return 2;
		}
		if (tier <= 6) {
			return 3;
		}
		return tier <= 8 ? 4 : 5;
	}

	/** The class of tier {@code tier}. */
	public static KelasSekte dariTier(int tier) {
		return byNomor(of(tier));
	}

	/** The class of number {@code nomor} (1-5, clamped). */
	public static KelasSekte byNomor(int nomor) {
		KelasSekte[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, nomor - 1))];
	}

	/** The class of id {@code id}, or {@code fallback}. */
	public static KelasSekte byId(String id, KelasSekte fallback) {
		for (KelasSekte k : values()) {
			if (k.id.equals(id)) {
				return k;
			}
		}
		return fallback;
	}

	/** The master's realm of tier {@code t}: {@code Realm.values()[2 + (t - 1) / 2]} (t1-2 Fondasi ... t9 Penyatuan Dao). */
	public static Realm ranahKetua(int tier) {
		int t = Math.max(1, Math.min(9, tier));
		return Realm.values()[2 + (t - 1) / 2];
	}

	/** An elder's realm: one ordinal under the master's, at least Pemurnian Qi. */
	public static Realm ranahTetua(int tier) {
		return Realm.values()[Math.max(Realm.QI_REFINING.ordinal(), ranahKetua(tier).ordinal() - 1)];
	}

	/**
	 * The strength order of the world's planned sects (41.4.2): exactly the comparator of {@code LangitNpcPlan.of}
	 * (plan tier descending, band descending, then the same seed hash), so the two sects that receive the sky palaces
	 * are always ranks 0 and 1. {@code LangitNpcPlan}'s hash is private, so its formula is copied in {@link #campur};
	 * {@code uji gabung sebaran} proves both orders agree on the live seed.
	 */
	public static List<GerbangPlan.Entry> urutan(GerbangPlan.Plan plan) {
		long seed = plan.seed();
		List<GerbangPlan.Entry> entries = new ArrayList<>(plan.entries());
		entries.sort(Comparator.comparingInt(GerbangPlan.Entry::tier).reversed()
				.thenComparing(Comparator.comparingInt(GerbangPlan.Entry::band).reversed())
				.thenComparingLong(e -> campur(seed, e.index())));
		return entries;
	}

	/** The tie-break hash of {@code LangitNpcPlan.mix} (copied verbatim; keep both in step). */
	public static long campur(long seed, int index) {
		long h = seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ h >>> 29;
	}
}
