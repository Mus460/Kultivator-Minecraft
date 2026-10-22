package dev.kultivator.duel.blade;

import dev.kultivator.duel.clash.SwingDir;
import dev.kultivator.duel.clash.SwingKind;
import org.jetbrains.annotations.Nullable;

/**
 * The body poses of the sword duel (DESIGN 35.5.2, contract C35.A1). Pure data shared by both sides: the server samples
 * the same keys for its blade arcs ({@link BladeArcs}) as the client plays on the model, so the blade the server tests is
 * the blade the players see. The keys themselves live in {@link DuelPoseTable}.
 * <p>
 * Never reorder or remove a constant after release (the ordinal travels in {@code DuelPreview} and test commands); new
 * poses go at the end. The table in the design counts 27 poses but lists 28 rows (four guards and stances, nine cuts with
 * the three mirrored left cuts, the thrust and the spin, the charge, two deflects, the blade step, the lock, five
 * reactions, and the four ceremonies): every listed pose is here, none is dropped.
 */
public enum DuelPose {
	/** The opening stance: intro and standing in the ring, the blade low and forward, right foot ahead. */
	SIKAP_PEMBUKA(Kind.STANCE, null),
	/** High guard: when the opponent raises a heavy overhead cut. */
	KUDA_TINGGI(Kind.STANCE, null),
	/** The middle guard: holding guard (the default). */
	KUDA_TENGAH(Kind.STANCE, null),
	/** Low guard: against a Low Sweep, the defending NPC's crouch. */
	KUDA_RENDAH(Kind.STANCE, null),
	TEBAS_ATAS(Kind.CUT, null),
	TEBAS_ATAS_KANAN(Kind.CUT, null),
	TEBAS_KANAN(Kind.CUT, null),
	TEBAS_BAWAH_KANAN(Kind.CUT, null),
	TEBAS_BAWAH(Kind.CUT, null),
	/** The mirrored cuts: the same right arm, cutting from the attacker's left side (backhand). */
	TEBAS_BAWAH_KIRI(Kind.CUT, TEBAS_BAWAH_KANAN),
	TEBAS_KIRI(Kind.CUT, TEBAS_KANAN),
	TEBAS_ATAS_KIRI(Kind.CUT, TEBAS_ATAS_KANAN),
	/** The thrust (also the Death Thrust): a lunge on the front leg. */
	TUSUK(Kind.CUT, null),
	/** The spinning cut of heavy blades and axes: the body coils, then turns through. */
	PUTAR(Kind.CUT, null),
	/** Charging a heavy cut: the blade lifted high behind the head, trembling more the longer it charges. */
	ANGKAT_BERAT(Kind.STANCE, null),
	/** Deflect: a fast wrist snap that meets the incoming blade from the side it comes. */
	TANGKIS(Kind.REACT, null),
	/** Perfect deflect: the snap plus the blade thrown outward. */
	TANGKIS_SEMPURNA(Kind.REACT, null),
	/** Blade Step (mikiri): the foot stamps the thrusting blade down. */
	INJAK(Kind.REACT, null),
	/** Blade lock: both hands press the hilt, leaning into the opponent; trembles with the pressure. */
	KUNCI_BILAH(Kind.STANCE, null),
	/** Recoil after being deflected or clashing: the arm thrown back, a step back. */
	HENTAK(Kind.REACT, null),
	/** Thrown back (lost lock, shockwave): arms open, the body tipping back. */
	TERPENTAL(Kind.REACT, null),
	/** Posture broken: head bowed, blade low, knees soft, shaking. */
	TERHUYUNG(Kind.STANCE, null),
	/** Struck: the body twists away from the side that was hit. */
	TERKENA(Kind.REACT, null),
	/** Shadow Step / dodge: a tilt into the move, the feet wide. */
	MENGHINDAR(Kind.REACT, null),
	/** Drawing the sword (intro): the left hand at the scabbard, the right draws across the body. */
	HUNUS(Kind.CEREMONY, null),
	/** Sheathing (the winner): a flick of the blade, then into the scabbard at the left hip; the item hides at the end. */
	SARUNG(Kind.CEREMONY, null),
	/** The loser kneels, the blade planted point down before him. */
	KALAH_BERLUTUT(Kind.CEREMONY, null),
	/** The fist-and-palm salute before and after a duel. */
	HORMAT(Kind.CEREMONY, null);

	/** How a pose runs in time (see {@link DuelPoseTable#sample}). */
	public enum Kind {
		/** Held while the state lasts (guards, the charge, the lock, the stagger). */
		STANCE,
		/** A cut: windup, strike into contact at the impact tick, follow-through. */
		CUT,
		/** A one-shot reaction: snap to the peak, then settle. */
		REACT,
		/** A ceremony: slow keys, the last one held. */
		CEREMONY
	}

	private static final DuelPose[] VALUES = values();

	private final Kind kind;
	private final @Nullable DuelPose mirrorOf;

	DuelPose(Kind kind, @Nullable DuelPose mirrorOf) {
		this.kind = kind;
		this.mirrorOf = mirrorOf;
	}

	public Kind kind() {
		return kind;
	}

	/** For the three left cuts: the right cut they mirror (sign-flipped turns, backhand wrist); null otherwise. */
	public @Nullable DuelPose mirrorOf() {
		return mirrorOf;
	}

	/** True for the poses whose strike key is the moment of contact (the impact tick of a swing). */
	public boolean cut() {
		return kind == Kind.CUT;
	}

	/** The lang key of the pose name ({@code uji duel pose}). */
	public String langKey() {
		return "kultivator.duel.pose." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static DuelPose byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? KUDA_TENGAH : VALUES[ordinal];
	}

	/** Every pose, in order (the {@code pose semua} preview). */
	public static DuelPose[] all() {
		return VALUES.clone();
	}

	/**
	 * The body pose of a swing (contract C35.A1). Thrusts and spins by direction or kind; the companion sword's thrust
	 * ({@code TERBANG}) is not a body move, its owner keeps the middle guard; every other kind (light, heavy, qi, riposte,
	 * deathblow, Better Combat) cuts along its direction.
	 */
	public static DuelPose forSwing(@Nullable SwingKind kind, @Nullable SwingDir dir) {
		if (kind == SwingKind.TERBANG) {
			return KUDA_TENGAH;
		}
		if (kind == SwingKind.TUSUK) {
			return TUSUK;
		}
		if (dir == null) {
			return TEBAS_KANAN;
		}
		return switch (dir) {
			case ATAS -> TEBAS_ATAS;
			case ATAS_KANAN -> TEBAS_ATAS_KANAN;
			case KANAN -> TEBAS_KANAN;
			case BAWAH_KANAN -> TEBAS_BAWAH_KANAN;
			case BAWAH -> TEBAS_BAWAH;
			case BAWAH_KIRI -> TEBAS_BAWAH_KIRI;
			case KIRI -> TEBAS_KIRI;
			case ATAS_KIRI -> TEBAS_ATAS_KIRI;
			case TUSUK -> TUSUK;
			case PUTAR -> PUTAR;
			default -> TEBAS_KANAN;
		};
	}

	/** {@link #forSwing(SwingKind, SwingDir)} from the wire ordinals of {@code DuelSwing.kind} and {@code .dir}. */
	public static DuelPose forSwing(int kindOrdinal, int dirOrdinal) {
		return forSwing(SwingKind.byOrdinal(kindOrdinal), SwingDir.byOrdinal(dirOrdinal));
	}
}
