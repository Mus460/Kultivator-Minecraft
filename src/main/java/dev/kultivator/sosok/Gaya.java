package dev.kultivator.sosok;

import dev.kultivator.sekte.api.Sifat;
import org.jetbrains.annotations.Nullable;

/**
 * The changeable half of a Sosok look (DESIGN 40.2.1): two shown traits, life path, wound, special outfit and mood. One
 * synced int ({@code DATA_GAYA}, NBT key {@code "gaya"} on disciples); DESIGN 39 sets the path, mood, wound and outfit
 * through {@link SosokApi}, never the skin.
 * <p>
 * Packing ({@link #pack()}): bits 0-3 trait 1 (0 none, 1-12 = {@code Sifat.ordinal() + 1}), bits 4-7 trait 2, bits
 * 8-12 path ({@link SosokJalan} bit 0-31, 0 none), bits 13-15 wound, bits 16-19 special outfit, bits 20-23 mood, bits
 * 24-31 reserved (always 0). Only {@link #busanaKhusus} and wound {@link #LUKA_LENGAN} change the painted atlas
 * ({@link #kunciAtlas()}); everything else is animation and face only.
 */
public record Gaya(int sifat1, int sifat2, int jalan, int luka, int busanaKhusus, int suasana) {

	/** Wound: healthy. */
	public static final int LUKA_SEHAT = 0;
	/** Wound: limping (HP under 35 % or after a failed breakthrough): walk clip {@code pincang}, bandaged leg. */
	public static final int LUKA_PINCANG = 1;
	/** Wound: bandaged arm in a sling (painted on the atlas, clip {@code lengan_digendong}). */
	public static final int LUKA_LENGAN = 2;
	/** Wound: drained of qi (deviation, tribulation): pained face, dropped shoulders. */
	public static final int LUKA_HABIS_QI = 3;
	/** Wound: badly hurt. */
	public static final int LUKA_PARAH = 4;
	public static final int LUKA_MAKS = 4;

	/** Outfit: automatic (rank, path, role tables). */
	public static final int BUSANA_OTOMATIS = 0;
	/** Outfit: travelling clothes (straw hat, bundle; DESIGN 39 town trips and the JELMAAN body). */
	public static final int BUSANA_BEPERGIAN = 1;
	/** Outfit: white mourning (three days after a fellow disciple falls). */
	public static final int BUSANA_BERKABUNG = 2;
	/** Outfit: festival clothes. */
	public static final int BUSANA_PESTA = 3;
	/** Outfit: work clothes. */
	public static final int BUSANA_KERJA = 4;
	/** Outfit: sleeping clothes. */
	public static final int BUSANA_TIDUR = 5;
	/** Outfit: duty armour. */
	public static final int BUSANA_ZIRAH = 6;
	/** Outfit: disguise. */
	public static final int BUSANA_PENYAMARAN = 7;
	public static final int BUSANA_MAKS = 7;

	/** Mood: neutral. */
	public static final int SUASANA_NETRAL = 0;
	public static final int SUASANA_SENANG = 1;
	public static final int SUASANA_SEDIH = 2;
	public static final int SUASANA_MARAH = 3;
	public static final int SUASANA_TAKUT = 4;
	public static final int SUASANA_MENGANTUK = 5;
	public static final int SUASANA_MALU = 6;
	public static final int SUASANA_BANGGA = 7;
	public static final int SUASANA_MAKS = 7;

	/** Nothing set. */
	public static final Gaya NETRAL = new Gaya(0, 0, 0, 0, 0, 0);

	public Gaya {
		sifat1 = clamp(sifat1, 0, 12);
		sifat2 = clamp(sifat2, 0, 12);
		jalan = clamp(jalan, 0, 31);
		luka = clamp(luka, 0, LUKA_MAKS);
		busanaKhusus = clamp(busanaKhusus, 0, BUSANA_MAKS);
		suasana = clamp(suasana, 0, SUASANA_MAKS);
	}

	/** The synced int (reserved bits 24-31 are always 0). */
	public int pack() {
		return sifat1 | sifat2 << 4 | jalan << 8 | luka << 13 | busanaKhusus << 16 | suasana << 20;
	}

	/** The record of a synced int (0 gives {@link #NETRAL}). */
	public static Gaya unpack(int packed) {
		if (packed == 0) {
			return NETRAL;
		}
		return new Gaya(packed & 15, packed >>> 4 & 15, packed >>> 8 & 31, packed >>> 13 & 7, packed >>> 16 & 15,
				packed >>> 20 & 15);
	}

	/** Gaya with the two shown traits (either may be null). */
	public static Gaya dariSifat(@Nullable Sifat pertama, @Nullable Sifat kedua) {
		return NETRAL.withSifat(pertama, kedua);
	}

	// ================================================================== copies

	public Gaya withSifat(@Nullable Sifat pertama, @Nullable Sifat kedua) {
		return new Gaya(kode(pertama), kode(kedua), jalan, luka, busanaKhusus, suasana);
	}

	/** Path bit 0-31 ({@link SosokJalan#bit(String)}). */
	public Gaya withJalan(int jalan) {
		return new Gaya(sifat1, sifat2, jalan, luka, busanaKhusus, suasana);
	}

	public Gaya withLuka(int luka) {
		return new Gaya(sifat1, sifat2, jalan, luka, busanaKhusus, suasana);
	}

	public Gaya withBusanaKhusus(int busanaKhusus) {
		return new Gaya(sifat1, sifat2, jalan, luka, busanaKhusus, suasana);
	}

	public Gaya withSuasana(int suasana) {
		return new Gaya(sifat1, sifat2, jalan, luka, busanaKhusus, suasana);
	}

	// ================================================================== reading

	public @Nullable Sifat sifatPertama() {
		return sifat(sifat1);
	}

	public @Nullable Sifat sifatKedua() {
		return sifat(sifat2);
	}

	/** True when either shown trait is {@code s}. */
	public boolean punya(@Nullable Sifat s) {
		return s != null && (sifat1 == s.ordinal() + 1 || sifat2 == s.ordinal() + 1);
	}

	/** The path id ({@link SosokJalan#id(int)}), or null when no path is set. */
	public @Nullable String jalanId() {
		return SosokJalan.id(jalan);
	}

	/**
	 * The part of this value that changes the painted atlas (the client's skin cache key): the special outfit and the
	 * bandaged arm. Two bodies with the same Sosok and the same value here share one atlas (the JELMAAN body copies its
	 * disciple's look and adds the travelling outfit, so it reuses the travelling atlas).
	 */
	public int kunciAtlas() {
		return busanaKhusus << 1 | (luka == LUKA_LENGAN ? 1 : 0);
	}

	private static @Nullable Sifat sifat(int kode) {
		Sifat[] all = Sifat.values();
		return kode >= 1 && kode <= all.length ? all[kode - 1] : null;
	}

	private static int kode(@Nullable Sifat s) {
		return s == null ? 0 : s.ordinal() + 1;
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** Lang key of a mood ({@code kultivator.sosok.suasana.<0-7>}, fragment {@code sosok}, 40I). */
	public static String kunciSuasana(int suasana) {
		return "kultivator.sosok.suasana." + clamp(suasana, 0, SUASANA_MAKS);
	}

	/** Lang key of a wound ({@code kultivator.sosok.luka.<0-4>}). */
	public static String kunciLuka(int luka) {
		return "kultivator.sosok.luka." + clamp(luka, 0, LUKA_MAKS);
	}

	/** Lang key of a special outfit ({@code kultivator.sosok.busana.<0-7>}). */
	public static String kunciBusana(int busana) {
		return "kultivator.sosok.busana." + clamp(busana, 0, BUSANA_MAKS);
	}
}
