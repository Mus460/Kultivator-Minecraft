package dev.kultivator.dunia.kota42.cakap;

import java.util.Locale;

/**
 * One thing a townsperson knows (DESIGN 42.5A.4; gossip of 42.5.4 is a fact carrying {@link #gosipId}). Immutable value;
 * the knowledge store (42K {@code Pengetahuan}) replaces it when strength, telling count or confidence change.
 * <p>
 * Frozen shape (C42.K1, 42-G0). Saved form (42K, about 18 bytes packed) and golden form use the same field names:
 * {@code {p, s, o, n, b, y, a, d, hk, ht, k, c, v, g}} = predikat, subjek, objek, nilai, kebenaran, yakin, asal (+ dari),
 * hariKejadian, hariTahu, kekuatan, kaliDiceritakan, valensi, gosipId.
 *
 * @param predikat        predicate id of {@code percakapan/predikat.json} (44 kinds, e.g. {@code HARGA_NAIK},
 *                        {@code BERJANJI}, {@code COPET_TERTANGKAP}, {@code OPINI})
 * @param subjek          who/what the fact is about
 * @param objek           second argument ({@link Rujuk#TIADA} when the predicate has none)
 * @param nilai           integer argument: amount, percent, day limit of a promise, opinion -100..100 (0 when unused)
 * @param kebenaran       truth of this copy
 * @param yakin           confidence 0-100
 * @param asal            how this holder learned it
 * @param hariKejadian    world day the event happened
 * @param hariTahu        world day this holder learned it (also the last time it was recalled: forgetting curve base)
 * @param kekuatan        memory strength S in world hours (recall chance {@code e^(-dt/S)})
 * @param kaliDiceritakan times this holder told it (each telling multiplies S by 1.6)
 * @param valensi         emotional weight -3..3
 * @param gosipId         id in the per-place gossip table, -1 when the fact is not a gossip
 */
public record Fakta(String predikat, Rujuk subjek, Rujuk objek, int nilai, Kebenaran kebenaran, int yakin, Asal asal,
		long hariKejadian, long hariTahu, float kekuatan, int kaliDiceritakan, int valensi, int gosipId) {

	/** Default memory strength of a heard fact (24 world hours, 42.5A.4). */
	public static final float KEKUATAN_DENGAR = 24.0F;

	public Fakta {
		yakin = Math.max(0, Math.min(100, yakin));
		valensi = Math.max(-3, Math.min(3, valensi));
		kekuatan = Math.max(1.0F, kekuatan);
	}

	/** True when the fact belongs to a gossip of the shared table. */
	public boolean gosip() {
		return gosipId >= 0;
	}

	/** Truth of a copy (42.5.4): exact, embellished on the way, or invented. */
	public enum Kebenaran {
		BENAR, BUMBU, PALSU
	}

	/** Source kinds (42.5A.4). */
	public enum Sumber {
		/** Saw it happen (confidence 100, S x1.5). */
		SAKSI,
		/** About oneself. */
		DIRI,
		/** Heard from another townsperson ({@link Asal#dari} = warga id). */
		DENGAR,
		/** Read on a notice board / town book. */
		PAPAN,
		/** Told by a player ({@link Asal#dari} = player index of the world player table). */
		PEMAIN
	}

	/**
	 * Where a holder got the fact.
	 *
	 * @param sumber source kind
	 * @param dari   warga id for {@link Sumber#DENGAR}, player index for {@link Sumber#PEMAIN}, otherwise -1
	 */
	public record Asal(Sumber sumber, int dari) {
		public static final Asal SAKSI = new Asal(Sumber.SAKSI, -1);
		public static final Asal DIRI = new Asal(Sumber.DIRI, -1);
		public static final Asal PAPAN = new Asal(Sumber.PAPAN, -1);

		public static Asal dengar(int wargaId) {
			return new Asal(Sumber.DENGAR, wargaId);
		}

		public static Asal pemain(int pemainIdx) {
			return new Asal(Sumber.PEMAIN, pemainIdx);
		}
	}

	/** Kind of a referenced entity. */
	public enum Jenis {
		TIADA, WARGA, PEMAIN, TEMPAT, SEKTE, BARANG, ACARA, KATA;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * A reference to a subject/object of a fact or gossip (42.5.4: warga/player/place; plus sect, item, event, free word).
	 *
	 * @param jenis kind
	 * @param id    warga id, player index, place site id, sect save index; 0 when {@link #kunci} identifies it
	 * @param kunci item id ({@code minecraft:rice}), event id, sect id string or lexicon word; "" when {@link #id} is enough
	 */
	public record Rujuk(Jenis jenis, long id, String kunci) {
		public static final Rujuk TIADA = new Rujuk(Jenis.TIADA, 0L, "");

		public Rujuk {
			kunci = kunci == null ? "" : kunci;
		}

		public static Rujuk warga(int wargaId) {
			return new Rujuk(Jenis.WARGA, wargaId, "");
		}

		public static Rujuk pemain(int pemainIdx) {
			return new Rujuk(Jenis.PEMAIN, pemainIdx, "");
		}

		public static Rujuk tempat(long situs) {
			return new Rujuk(Jenis.TEMPAT, situs, "");
		}

		public static Rujuk barang(String itemId) {
			return new Rujuk(Jenis.BARANG, 0L, itemId);
		}

		public static Rujuk kata(String kata) {
			return new Rujuk(Jenis.KATA, 0L, kata);
		}
	}
}
