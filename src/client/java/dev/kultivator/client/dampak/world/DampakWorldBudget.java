package dev.kultivator.client.dampak.world;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.fx.spec.PhantomDisplays;

/**
 * The budgets of the Phase 10 world effects (DESIGN 28.6.3, 28.7, 28.9.2, 28.10): every table indexed by the 7b tier in
 * effect ({@link EpicBudget#tier()}: 0 Minimal, 1 Normal, 2 Maximal; −1 = the frame guard took the geometry, particles
 * only). Also the two M2 settings M3 reads: "Puing &amp; monumen" and "Daya dampak". Client thread only.
 */
public final class DampakWorldBudget {

	/** Dampak displays alive (debris, lift cells, crystals, ice spikes). */
	public static final int[] DISPLAYS_ALIVE = {16, 96, 256};
	/** Dampak displays created per tick. */
	public static final int[] DISPLAYS_PER_TICK = {4, 16, 32};
	/** Re-key interval and duration (ticks). */
	public static final int[] KEY_EVERY = {2, 2, 1};
	/** Visual debris per climax (Mode Kehancuran off). */
	public static final int[] FAKE_DEBRIS = {6, 24, 48};
	/** Particles per debris batch. */
	public static final int[] BATCH_PARTICLES = {8, 24, 48};
	/** Lift cells alive (inside the display pool). */
	public static final int[] LIFT_CELLS = {0, 24, 64};
	/** Light-grid segments. */
	public static final int[] GRID_SEGMENTS = {0, 48, 128};
	/** Sigil segments drawn, node discs, crystals. */
	public static final int[] SIGIL_SEGMENTS = {0, 256, 512};
	public static final int[] SIGIL_NODES = {0, 16, 32};
	public static final int[] CRYSTALS = {0, 9, 18};
	/** Monuments alive (all casters). */
	public static final int[] MONUMENTS = {1, 2, 4};
	/** ShockRing segments and dust-wall particles per tick. */
	public static final int[] RING_SEGMENTS = {32, 48, 64};
	public static final int[] RING_DUST = {8, 24, 48};
	/** Ice spikes of a FROST ring. */
	public static final int[] ICE_SPIKES = {8, 16, 32};
	/** Rings alive at once. */
	public static final int RINGS_ALIVE = 16;
	// ---------------------------------------------------------------- Titah Langit (DESIGN 29.8)
	/** The largest visual radius a Titah draws per tier (Minimal: larger rings become dotted particle rings). */
	public static final int[] TITAH_RADIUS = {48, 128, 256};
	/** Titah shock-ring segments (Minimal: particles). */
	public static final int[] TITAH_RING_SEGMENTS = {0, 64, 96};
	/** Titah sky-carried lift cells (3 displays each). */
	public static final int[] TITAH_LIFT_CELLS = {0, 12, 32};
	/** Block displays per Bintang star crystal beside its star_core (7 crystals: 0 / 7 / 21). */
	public static final int[] TITAH_CRYSTAL_BLOCKS = {0, 1, 3};
	/** Bintang's star crystals stay this long (ticks; Minimal: particles only, 60 s of motes). */
	public static final int[] TITAH_CRYSTAL_LIFE = {1200, 2400, 2400};
	/** Displays never spawn farther than this from the camera, and only every other one beyond {@link #HALF_RANGE}. */
	public static final double SPAWN_RANGE = 64.0D;
	public static final double HALF_RANGE = 32.0D;

	/** The 7b tier in effect (−1 = no geometry: particles only). */
	public static int tier() {
		return EpicBudget.tier();
	}

	/** The tier for counts that never go below Minimal. */
	public static int index() {
		return Math.max(0, Math.min(2, EpicBudget.tier()));
	}

	/** True when world quads (rings, sigils, decals) may draw this frame or tick. */
	public static boolean geometry() {
		return EpicBudget.thinGeometry();
	}

	/** "Puing &amp; monumen" (28.11): off = particles only, no monuments. */
	public static boolean worldFx() {
		try {
			return EpicSettings.worldFx();
		} catch (RuntimeException exception) {
			return true;
		}
	}

	/**
	 * "Daya dampak" as a factor 0.5 / 1.0 / 1.5 (a percent value is accepted too). It scales counts, speeds, ring
	 * radius (× 0.8-1.2) and shake, never above the caps.
	 */
	public static float power() {
		float raw;
		try {
			raw = EpicSettings.impactPower();
		} catch (RuntimeException exception) {
			return 1.0F;
		}
		if (!Float.isFinite(raw) || raw <= 0.0F) {
			return 1.0F;
		}
		if (raw > 3.0F) {
			raw /= 100.0F;
		}
		return Math.max(0.5F, Math.min(1.5F, raw));
	}

	/** Ring radius factor of "Daya dampak": 0.8 / 1.0 / 1.2. */
	public static float ringScale() {
		return Math.max(0.8F, Math.min(1.2F, 0.6F + 0.4F * power()));
	}

	/** Debris speed factor of "Daya dampak": 0.85 / 1.0 / 1.15. */
	public static float speedScale() {
		return 0.7F + 0.3F * power();
	}

	/**
	 * True when this cast may make Dampak displays: phantoms on, "Puing &amp; monumen" on, not under the frame guard's
	 * no-geometry tier, no LOW_LOAD flag, and for others' casts only at "Efek pemain lain: Penuh".
	 */
	public static boolean displays(boolean own, boolean lowLoad) {
		if (lowLoad || tier() < 0 || !worldFx() || !PhantomDisplays.enabled()) {
			return false;
		}
		return own || EffectQuality.others() == EffectQuality.Others.FULL;
	}

	/** Count factor for a cast: 1 own, else "Efek pemain lain" (0 = nothing). */
	public static float othersFactor(boolean own) {
		return own ? 1.0F : EffectQuality.othersScale();
	}

	/** A count scaled by power and the others factor, capped by {@code cap}. */
	public static int scaled(int base, boolean own, int cap) {
		float n = base * power() * othersFactor(own);
		return Math.max(0, Math.min(cap, Math.round(n)));
	}

	/**
	 * A Titah visual radius as drawn on this client: the tier's cap ({@link #TITAH_RADIUS}) and the render distance less
	 * one chunk; never below 4.
	 */
	public static float titahRadius(float wanted) {
		if (!Float.isFinite(wanted) || wanted <= 0.0F) {
			return 4.0F;
		}
		float cap = TITAH_RADIUS[index()];
		try {
			int chunks = net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance();
			cap = Math.min(cap, Math.max(32.0F, chunks * 16.0F - 16.0F));
		} catch (RuntimeException exception) {
			// keep the tier cap
		}
		return Math.max(4.0F, Math.min(cap, wanted));
	}

	private DampakWorldBudget() {
	}
}
