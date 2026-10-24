package dev.kultivator.sosok;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The fixed identity of a human body drawn by the shared Sosok system (DESIGN 40.2.1): sex, visible age group, build
 * variant and look seed. Disciples ({@code DiscipleEntity}) and every human NPC ({@code KultivatorNpc}, DESIGN 38
 * residents, DESIGN 39 town bodies) carry it as one synced int ({@code DATA_SOSOK}); the client builds the whole look
 * (skin, face, hair, clothes, gait) from it plus the recipe tables, so two players always see the same person and a
 * reload gives the same face.
 * <p>
 * Packing ({@link #pack()}): bit 0 sex, bits 1-2 age group, bits 3-5 build, bits 6-31 seed (26 bits). The packed value 0
 * means "no Sosok yet" and never comes out of {@link #pack()} (an all-zero record gets seed 1).
 * <p>
 * The body proportions of 40.2.3 live here too ({@link #skala()}, {@link #skalaKepala()} ...) so the server's
 * {@code Attributes.SCALE} base value (hitbox, eye height, name plate) and the client's part scales always agree.
 */
public record Sosok(int kelamin, int usia, int postur, int benih) {

	/** Sex: male. */
	public static final int LAKI = 0;
	/** Sex: female. */
	public static final int PEREMPUAN = 1;

	/** Visible age group: child, about 7-12. */
	public static final int ANAK = 0;
	/** Visible age group: youth, about 13-20. */
	public static final int MUDA = 1;
	/** Visible age group: adult, about 21-55. */
	public static final int DEWASA = 2;
	/** Visible age group: old, about 56 and up. */
	public static final int TUA = 3;

	/** Highest build variant. */
	public static final int POSTUR_MAKS = 7;
	/** Mask of the 26-bit look seed. */
	public static final int BENIH_MASK = (1 << 26) - 1;
	/** The synced value of "no Sosok yet". */
	public static final int KOSONG = 0;

	/** Build deltas added to the base scale of an adult man / woman (40.2.3, build 0-7). */
	private static final float[] DELTA_LAKI_DEWASA = {-0.03F, -0.02F, -0.01F, 0F, 0F, 0.01F, 0.02F, 0.04F};
	private static final float[] DELTA_PEREMPUAN_DEWASA = {-0.03F, -0.02F, -0.01F, 0F, 0F, 0.01F, 0.02F, 0.03F};

	/**
	 * Start-up of the Sosok module's server half (DESIGN 40.7.2: one line in {@code Kultivator.onInitialize} after
	 * {@code Disciples.init()}): config section {@code sosok}, the test commands {@code /kultivasi uji penampilan} and
	 * their bodies, and the reset of the automatic-look bookkeeping when a server stops.
	 */
	public static void init() {
		SosokConfig.init();
		SosokCommands.init();
		GaleriUji.init();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> SosokUsia.clear());
	}

	public Sosok {
		kelamin = kelamin == PEREMPUAN ? PEREMPUAN : LAKI;
		usia = Math.max(ANAK, Math.min(TUA, usia));
		postur = Math.max(0, Math.min(POSTUR_MAKS, postur));
		benih = benih & BENIH_MASK;
		if (kelamin == LAKI && usia == ANAK && postur == 0 && benih == 0) {
			benih = 1;
		}
	}

	// ================================================================== packing

	/** The synced int (never {@link #KOSONG}). */
	public int pack() {
		return kelamin | usia << 1 | postur << 3 | benih << 6;
	}

	/** The record of a synced int, or null for {@link #KOSONG}. */
	public static @Nullable Sosok unpack(int packed) {
		if (packed == KOSONG) {
			return null;
		}
		return new Sosok(packed & 1, packed >>> 1 & 3, packed >>> 3 & 7, packed >>> 6 & BENIH_MASK);
	}

	/** The record of a synced int, or the entity-seed stand-in {@link #dariBenihEntitas} while it is still 0. */
	public static Sosok dari(int packed, UUID entitas) {
		Sosok sosok = unpack(packed);
		return sosok != null ? sosok : dariBenihEntitas(entitas);
	}

	/**
	 * A deterministic stand-in made from an entity's UUID: used by the client while the synced value is still 0, and by
	 * {@link SosokApi} as the value of bodies that carry no Sosok data. Sex 50/50, youth 55 % or adult 45 %, any build.
	 */
	public static Sosok dariBenihEntitas(UUID uuid) {
		long h = aduk(uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 17));
		int kelamin = (int) (h & 1);
		int usia = (h >>> 1 & 127) < 70 ? MUDA : DEWASA;
		int postur = (int) (h >>> 8 & 7);
		int benih = (int) (h >>> 16) & BENIH_MASK;
		return new Sosok(kelamin, usia, postur, benih);
	}

	/**
	 * SplitMix64 finaliser (the mixing step of the RNG that 40.3.10 writes identically in Java and Python). Shared so every
	 * Sosok seed derivation on the server uses one well-spread hash.
	 */
	public static long aduk(long z) {
		z += 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	// ================================================================== copies

	public Sosok withKelamin(int kelamin) {
		return new Sosok(kelamin, usia, postur, benih);
	}

	public Sosok withUsia(int usia) {
		return new Sosok(kelamin, usia, postur, benih);
	}

	public Sosok withPostur(int postur) {
		return new Sosok(kelamin, usia, postur, benih);
	}

	public Sosok withBenih(int benih) {
		return new Sosok(kelamin, usia, postur, benih);
	}

	// ================================================================== reading

	public boolean perempuan() {
		return kelamin == PEREMPUAN;
	}

	public boolean anak() {
		return usia == ANAK;
	}

	public boolean tua() {
		return usia == TUA;
	}

	/**
	 * True when the body uses the slim (3 px) arm model: every woman and child, and the two thinnest youth builds of a
	 * man (40.2.3 "pemuda kurus").
	 */
	public boolean lenganRamping() {
		return kelamin == PEREMPUAN || usia == ANAK || usia == MUDA && postur <= 1;
	}

	/**
	 * Base value of {@code Attributes.SCALE} (40.2.3): adult man 1.00, adult woman 0.95, young man 0.95, young woman
	 * 0.91, child 0.66, old man 0.97, old woman 0.92, plus the build delta. The renderers keep multiplying their fixed
	 * {@code PLAYER_SCALE 0.9375} on top.
	 */
	public float skala() {
		boolean p = kelamin == PEREMPUAN;
		return switch (usia) {
			case ANAK -> 0.66F + linear(-0.04F, 0.04F);
			case MUDA -> (p ? 0.91F : 0.95F) + linear(-0.02F, 0.02F);
			case TUA -> (p ? 0.92F : 0.97F) + linear(-0.02F, 0.01F);
			default -> p ? 0.95F + DELTA_PEREMPUAN_DEWASA[postur] : 1.0F + DELTA_LAKI_DEWASA[postur];
		};
	}

	/** Head scale relative to the body (child 1.22, youth 1.04 man / 1.05 woman, others 1.00). */
	public float skalaKepala() {
		return switch (usia) {
			case ANAK -> 1.22F;
			case MUDA -> kelamin == PEREMPUAN ? 1.05F : 1.04F;
			default -> 1.0F;
		};
	}

	/** Leg {@code yScale} (women 1.02 for youth and adult, child 0.92, old 0.98, others 1.00). */
	public float skalaKakiY() {
		return switch (usia) {
			case ANAK -> 0.92F;
			case TUA -> 0.98F;
			default -> kelamin == PEREMPUAN ? 1.02F : 1.0F;
		};
	}

	/** Body {@code xScale}: narrower shoulders (adult woman 0.94, young man 0.96, young woman 0.92, others 1.00). */
	public float skalaBadanX() {
		if (usia == DEWASA) {
			return kelamin == PEREMPUAN ? 0.94F : 1.0F;
		}
		if (usia == MUDA) {
			return kelamin == PEREMPUAN ? 0.92F : 0.96F;
		}
		return 1.0F;
	}

	/** Body {@code yScale} (child 0.94, others 1.00). */
	public float skalaBadanY() {
		return usia == ANAK ? 0.94F : 1.0F;
	}

	/**
	 * Pixels the arm pivots move inward after the pose so slim arms hug a narrowed female body (0.25 for youth and adult
	 * women, 0 otherwise; applied as {@code rightArm.x += v}, {@code leftArm.x -= v}).
	 */
	public float geserBahu() {
		return kelamin == PEREMPUAN && (usia == MUDA || usia == DEWASA) ? 0.25F : 0.0F;
	}

	private float linear(float lo, float hi) {
		return lo + (hi - lo) * postur / POSTUR_MAKS;
	}

	/** Lang key of the age group ({@code kultivator.sosok.usia.<0-3>}, fragment {@code sosok}, 40I). */
	public static String kunciUsia(int usia) {
		return "kultivator.sosok.usia." + Math.max(ANAK, Math.min(TUA, usia));
	}

	/** Lang key of the sex ({@code kultivator.sosok.kelamin.<0|1>}). */
	public static String kunciKelamin(int kelamin) {
		return "kultivator.sosok.kelamin." + (kelamin == PEREMPUAN ? PEREMPUAN : LAKI);
	}
}
