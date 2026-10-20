package dev.kultivator.technique.spec.dsl;

import org.jetbrains.annotations.Nullable;

/**
 * Phase 10 "Dampak Langit" (DESIGN 28, contract C10.1): how an Abadi or Dewa ultimate presents its strike. Plain shared
 * data, authored in the arts files as {@code .dampak(Dampak.builder()...build())} and read by the server (silent climax
 * cues, the Kehancuran surface palette, sigil trenches, debris flags) and by the client (target mark, sky ride, impact
 * frames, delayed boom, debris, sigils, monuments). Never changes a gameplay number.
 *
 * @param mark            Tanda Langit kind (28.2)
 * @param markTicks       how long a mark lives at most (0 = until the climax, at most 200 ticks)
 * @param ride            Perjalanan Langit (28.3): NONE, SHORT (a crane) or FULL (the three-part ride at Kamera Penuh)
 * @param vista           the sky vista of a FULL ride's R2 (28.3.3)
 * @param rideTicks       length of a FULL ride (≤ 80), timed to end on the climax
 * @param climaxAt        ticks after the CAST (after the release of a charged cast) when the climax event lands
 * @param frame1          impact frame colour of tick 0 (RGB, or {@link #INV}, {@link #BLACK}, {@link #DAO}, {@link #DAO2},
 *                        {@link #NO_FRAME})
 * @param frame2          impact frame colour of tick 1
 * @param frame3          impact frame colour of tick 2
 * @param silence         ticks between the silent strike and its boom (0 = classic: the server plays the climax cues)
 * @param debris          the debris mode (28.6)
 * @param ring            the shock ring variant (28.5)
 * @param palette         the Kawah Lebur palette of the Kehancuran surface pass (28.8)
 * @param sigil           the Sigil Bumi pattern (28.9; {@link SigilPattern#NONE} for none)
 * @param sigilRadius     the sigil's radius in blocks at K = 1 (the client draws it at this size)
 * @param sigilIgnite     the phase whose events light one node each (null = everything lights on the climax)
 * @param sigilTrench     with Mode Kehancuran the pattern's lines burn into the land (15 % of the cast's block cap)
 * @param monument        the {@code kultivator:fx/<id>} model left at the impact (null = none; {@link #MONUMENT_BY_DAO} =
 *                        the first Dao's signature prop)
 * @param monumentScale   the monument's display scale
 * @param monumentSeconds how long the monument stays (30-180)
 * @param pose            how the monument stands
 */
public record Dampak(Mark mark, int markTicks, Ride ride, Vista vista, int rideTicks, int climaxAt,
		int frame1, int frame2, int frame3, int silence,
		Debris debris, Ring ring, Palette palette,
		SigilPattern sigil, float sigilRadius, @Nullable Phase sigilIgnite, boolean sigilTrench,
		@Nullable String monument, float monumentScale, int monumentSeconds, MonumentPose pose) {

	/** Tanda Langit kinds (28.2). */
	public enum Mark { NONE, PILLAR, MULTI, SHADOW, PATH, FOOT, RING_ONLY }

	/** Perjalanan Langit (28.3). */
	public enum Ride { NONE, SHORT, FULL }

	/** The sky vistas of a FULL ride (28.3.3). */
	public enum Vista {
		NONE, GERBANG_LANGIT, PUSARAN, SEMBILAN_LANGIT, SUNGAI_BINTANG, MATAHARI_KAISAR, GUNUNG_ES,
		CAKRAM_AKRESI, POHON_CAHAYA, LAUT_UTARA, BY_DAO
	}

	/** Debris modes (28.6.1). */
	public enum Debris { NONE, BURST, WAKE, LIFT, IMPLODE, SUSPEND, CONVERT }

	/** Shock ring variants (28.5). */
	public enum Ring { DUST, EMBER, FROST, VOID, GOLD }

	/** Kawah Lebur palettes (28.8.2); {@code kehancuran.Surface} maps them to blocks. */
	public enum Palette { NONE, FIRE, ICE, LIGHTNING, SWORD, SPACE, EARTH, LIFE, SHADOW, TAIJI }

	/** Monument poses (28.10). */
	public enum MonumentPose { PLANTED, FLAT, UPRIGHT, HOVER }

	/** Frame colour marker: the inverted frame of "Bingkai dampak: Penuh" (Lembut shows a white tint instead). */
	public static final int INV = -1;
	/** Frame colour marker: a black frame. */
	public static final int BLACK = -2;
	/** Frame colour marker: the colour of the caster's first Dao (own arts). */
	public static final int DAO = -3;
	/** Frame colour marker: no frame on this tick. */
	public static final int NO_FRAME = -4;
	/** Frame colour marker: the colour of the caster's second Dao (own arts; the first when the art is pure). */
	public static final int DAO2 = -5;
	/** Monument id marker: the signature prop of the caster's first Dao (own arts; the client picks the model). */
	public static final String MONUMENT_BY_DAO = "@dao";

	/** The longest FULL ride (28.3.2). */
	public static final int MAX_RIDE_TICKS = 80;
	/** The longest a mark lives (28.2). */
	public static final int MAX_MARK_TICKS = 200;

	/** Hit-stop ticks at the climax (28.4.2): Dewa 3, Abadi 2. */
	public static int hitStop(boolean dewa) {
		return dewa ? 3 : 2;
	}

	/** True when the climax cues are played by the clients after the silence (28.4.3). */
	public boolean silent() {
		return silence > 0;
	}

	/** True when this row has impact frames at all ({@code frame1} is not {@link #NO_FRAME}). */
	public boolean frames() {
		return frame1 != NO_FRAME || frame2 != NO_FRAME || frame3 != NO_FRAME;
	}

	/** True when a frame of this row inverts (only ever drawn under "Bingkai dampak: Penuh"). */
	public boolean inverts() {
		return frame1 == INV || frame2 == INV || frame3 == INV;
	}

	/** The {@code Payloads.DampakDebris} motion flags of this row (LIFT 4, IMPLODE 8 | 4, SUSPEND 16, WAKE 32). */
	public int debrisFlags() {
		return switch (debris) {
			case LIFT -> 4;
			case IMPLODE -> 8 | 4;
			case SUSPEND -> 16;
			case WAKE -> 32;
			default -> 0;
		};
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Fluent builder with the defaults of 28: no mark, no ride, no frames, classic sound, BURST, DUST, no palette. */
	public static final class Builder {
		private Mark mark = Mark.NONE;
		private int markTicks;
		private Ride ride = Ride.NONE;
		private Vista vista = Vista.NONE;
		private int rideTicks;
		private int climaxAt;
		private int frame1 = NO_FRAME;
		private int frame2 = NO_FRAME;
		private int frame3 = NO_FRAME;
		private int silence;
		private Debris debris = Debris.BURST;
		private Ring ring = Ring.DUST;
		private Palette palette = Palette.NONE;
		private SigilPattern sigil = SigilPattern.NONE;
		private float sigilRadius = 12.0F;
		private @Nullable Phase sigilIgnite;
		private boolean sigilTrench;
		private @Nullable String monument;
		private float monumentScale = 1.0F;
		private int monumentSeconds = 60;
		private MonumentPose pose = MonumentPose.UPRIGHT;

		private Builder() {
		}

		public Builder mark(Mark value) {
			this.mark = value;
			return this;
		}

		public Builder mark(Mark value, int ticks) {
			this.mark = value;
			this.markTicks = ticks;
			return this;
		}

		/** A SHORT crane (or NONE). */
		public Builder ride(Ride value) {
			this.ride = value;
			return this;
		}

		/** A FULL ride of {@code ticks} with {@code vista}. */
		public Builder ride(Ride value, int ticks, Vista vistaValue) {
			this.ride = value;
			this.rideTicks = ticks;
			this.vista = vistaValue;
			return this;
		}

		public Builder vista(Vista value) {
			this.vista = value;
			return this;
		}

		/** Ticks after the CAST (or the release) when the climax lands. */
		public Builder climaxAt(int ticks) {
			this.climaxAt = ticks;
			return this;
		}

		/** The three impact frame colours (RGB or a marker). */
		public Builder frames(int first, int second, int third) {
			this.frame1 = first;
			this.frame2 = second;
			this.frame3 = third;
			return this;
		}

		/** Two frames (the third tick only fades). */
		public Builder frames(int first, int second) {
			return frames(first, second, NO_FRAME);
		}

		public Builder silence(int ticks) {
			this.silence = ticks;
			return this;
		}

		public Builder debris(Debris value) {
			this.debris = value;
			return this;
		}

		public Builder ring(Ring value) {
			this.ring = value;
			return this;
		}

		public Builder palette(Palette value) {
			this.palette = value;
			return this;
		}

		public Builder sigil(SigilPattern pattern, float radius) {
			this.sigil = pattern;
			this.sigilRadius = radius;
			return this;
		}

		/** The phase whose events light the sigil node by node. */
		public Builder ignite(Phase phase) {
			this.sigilIgnite = phase;
			return this;
		}

		/** With Mode Kehancuran the sigil's lines become burning trenches (28.9.3). */
		public Builder trench() {
			this.sigilTrench = true;
			return this;
		}

		public Builder monument(String model, float scale, int seconds, MonumentPose value) {
			this.monument = model;
			this.monumentScale = scale;
			this.monumentSeconds = seconds;
			this.pose = value;
			return this;
		}

		/** How long the lingering parts stay (the monument, a sigil's crystals) when no model is named. */
		public Builder monumentSeconds(int seconds) {
			this.monumentSeconds = seconds;
			return this;
		}

		public Dampak build() {
			return new Dampak(mark, Math.max(0, Math.min(MAX_MARK_TICKS, markTicks)), ride, vista, Math.max(0, rideTicks),
					Math.max(0, climaxAt), frame1, frame2, frame3, Math.max(0, Math.min(20, silence)), debris, ring, palette, sigil,
					Math.max(0.0F, sigilRadius), sigilIgnite, sigilTrench && sigil != SigilPattern.NONE, monument, Math.max(0.1F, monumentScale),
					monumentSeconds, pose);
		}
	}
}
