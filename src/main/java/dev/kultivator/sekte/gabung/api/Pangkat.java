package dev.kultivator.sekte.gabung.api;

import dev.kultivator.data.Realm;
import dev.kultivator.sect.hall.SectRanks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A player member's rank in an NPC sect (DESIGN 41.6.1; the ordinal is what {@code Keanggotaan.pangkat} saves and the
 * lencana payload syncs, so never reorder). Two paths branch at Inti: the disciple path (Pewaris) and the elder path
 * (Tetua, Wakil); the master (Ketua) is reached from both. Tetua Tamu is a side road equal to an elder that never rises.
 * <pre>
 * Pelayan -> Tercatat -> Luar -> Dalam -> Inti -+-> Pewaris ----------+
 *                                               |                     +--> Ketua
 *                                               +-> Tetua -> Wakil ---+
 * </pre>
 * The trim colours reuse {@code SectRanks.TRIM} so the member ranks read as one family with the player's own disciple
 * ranks. Costs are base PK, multiplied by the class factor {@code KelasSekte.kf} and {@code gabungSekte.skalaBiaya}.
 */
public enum Pangkat {

	/** 0 Murid Pelayan: door PELAYAN only; chores, the dormitory canteen, E missions. */
	PELAYAN("pelayan", 0, Realm.MORTAL, 0, 0x6E6A64),
	/** 1 Murid Tercatat: Pemurnian Qi or 50 chore PK, 3 days after joining. */
	TERCATAT("tercatat", 0, Realm.QI_REFINING, 3, SectRanks.TRIM[0]),
	/** 2 Murid Luar: Pemurnian Qi, 3 days after Tercatat (the "Uji Ulang"). */
	LUAR("luar", 60, Realm.QI_REFINING, 3, SectRanks.TRIM[1]),
	/** 3 Murid Dalam: Fondasi, the Inner Disciple Trial, 7 days after Luar. */
	DALAM("dalam", 300, Realm.FOUNDATION, 7, SectRanks.TRIM[2]),
	/** 4 Murid Inti: Inti Emas, top 3 of the disciple tournament or one A mission, 10 days after Dalam. */
	INTI("inti", 1200, Realm.GOLDEN_CORE, 10, SectRanks.TRIM[3]),
	/** 5 Pewaris ("Putra/Putri Sekte"): Jiwa Baru Lahir, wins the heir election, 20 days of membership. */
	PEWARIS("pewaris", 4000, Realm.NASCENT_SOUL, 20, 0xFFB347),
	/** 6 Tetua: Jiwa Baru Lahir, the Elder Trial, 30 days of membership. */
	TETUA("tetua", 3000, Realm.NASCENT_SOUL, 30, SectRanks.TRIM[4]),
	/** 7 Wakil Ketua: Transformasi Dewa, 15 days as elder, support of half the elders. */
	WAKIL("wakil", 8000, Realm.DEITY_TRANSFORMATION, 15, SectRanks.TRIM[5]),
	/** 8 Ketua: the "Robe and Bowl" succession (realm = the class master's realm). */
	KETUA("ketua", 15000, Realm.DEITY_TRANSFORMATION, 0, 0xFFD24A),
	/** Tetua Tamu (Keqing): door TAMU; elder prices, no office, no vote, at most 2 per sect. */
	TAMU("tamu", 0, Realm.FOUNDATION, 0, 0x7FD6C8);

	/** Inti seats per class number 1-5 (41.6.1: 3 / 4 / 6 / 8 / 10). */
	private static final int[] KURSI_INTI = {3, 4, 6, 8, 10};
	/** Most Tetua Tamu per sect. */
	public static final int TAMU_MAKS = 2;

	private final String id;
	private final int biaya;
	private final Realm ranah;
	private final int hariMin;
	private final int warna;

	Pangkat(String id, int biaya, Realm ranah, int hariMin, int warna) {
		this.id = id;
		this.biaya = biaya;
		this.ranah = ranah;
		this.hariMin = hariMin;
		this.warna = warna;
	}

	public String id() {
		return id;
	}

	/** The base PK cost to rise to this rank (before {@code kf} and {@code skalaBiaya}). */
	public int biaya() {
		return biaya;
	}

	/** The rank's realm requirement in a class below Agung (see {@link #syaratRanah}). */
	public Realm ranah() {
		return ranah;
	}

	/** World days since the previous rank (Pewaris, Tetua: days of membership; Wakil: days as elder). */
	public int hariMin() {
		return hariMin;
	}

	/** The robe trim and name colour. */
	public int warna() {
		return warna;
	}

	/** Pelayan .. Inti: the shared disciple ladder below the branch. */
	public boolean murid() {
		return ordinal() <= INTI.ordinal();
	}

	/** Tetua, Wakil, Ketua and Tamu: voices and prices of an elder (Tamu has the prices, no voice). */
	public boolean setaraTetua() {
		return this == TETUA || this == WAKIL || this == KETUA || this == TAMU;
	}

	/** True when this rank votes in the succession and in appointments (41.8.5, 41.9). */
	public boolean bersuara() {
		return this == TETUA || this == WAKIL || this == KETUA;
	}

	/** At least {@code other} on the shared order (Tamu counts as Tetua; Pewaris counts above Inti, below Tetua). */
	public boolean minimal(Pangkat other) {
		return tingkat() >= other.tingkat();
	}

	/** The comparable height: Pelayan 0 .. Inti 4, Pewaris 5, Tetua/Tamu 6, Wakil 7, Ketua 8. */
	public int tingkat() {
		return this == TAMU ? TETUA.ordinal() : ordinal();
	}

	/**
	 * The realm needed in class {@code kelas} of tier {@code tier}: the table value, one ordinal higher from Dalam up in
	 * Agung and Abadi, and the class master's realm for Ketua.
	 */
	public Realm syaratRanah(KelasSekte kelas, int tier) {
		if (this == KETUA) {
			return KelasSekte.ranahKetua(tier);
		}
		int ord = ranah.ordinal();
		if (kelas.nomor() >= KelasSekte.AGUNG.nomor() && ordinal() >= DALAM.ordinal() && this != TAMU) {
			ord++;
		}
		return Realm.values()[Math.min(Realm.values().length - 1, ord)];
	}

	/** Seats of this rank in class {@code kelas}; -1 = unlimited (Pelayan to Dalam). */
	public int kursi(KelasSekte kelas) {
		return switch (this) {
			case PELAYAN, TERCATAT, LUAR, DALAM -> -1;
			case INTI -> KURSI_INTI[kelas.nomor() - 1];
			case TETUA -> kelas.kursiTetua();
			case TAMU -> TAMU_MAKS;
			case PEWARIS, WAKIL, KETUA -> 1;
		};
	}

	/** The next rank on the plain ladder (Inti forks: {@link #PEWARIS} on the disciple path); null at the top or for Tamu. */
	public Pangkat berikut(boolean jalurTetua) {
		return switch (this) {
			case PELAYAN -> TERCATAT;
			case TERCATAT -> LUAR;
			case LUAR -> DALAM;
			case DALAM -> INTI;
			case INTI -> jalurTetua ? TETUA : PEWARIS;
			case PEWARIS, WAKIL -> KETUA;
			case TETUA -> WAKIL;
			case KETUA, TAMU -> null;
		};
	}

	/** One rank lower for a demotion (min Tercatat for the ladder; Pewaris/Tetua fall to Inti, Wakil to Tetua). */
	public Pangkat turun() {
		return switch (this) {
			case PELAYAN, TERCATAT -> this;
			case LUAR -> TERCATAT;
			case DALAM -> LUAR;
			case INTI -> DALAM;
			case PEWARIS, TETUA -> INTI;
			case WAKIL -> TETUA;
			case KETUA -> WAKIL;
			case TAMU -> TAMU;
		};
	}

	/** "Murid Luar" in the rank colour. */
	public MutableComponent nama() {
		return Component.translatable("kultivator.gabung.pangkat." + id).withColor(warna);
	}

	/** The rank of ordinal {@code ordinal} (saved or synced), clamped to {@link #PELAYAN}. */
	public static Pangkat byOrdinal(int ordinal) {
		Pangkat[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PELAYAN;
	}

	/** The rank of id {@code id}, or null. */
	public static Pangkat byId(String id) {
		for (Pangkat p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}
}
