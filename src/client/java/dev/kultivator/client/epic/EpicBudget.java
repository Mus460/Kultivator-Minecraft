package dev.kultivator.client.epic;

import dev.kultivator.client.EffectQuality;
import net.minecraft.client.Minecraft;

import java.util.Arrays;

/**
 * The client budgets of 7b (DESIGN 23.14.1) and the frame guard (23.14.2).
 * <p>
 * Tiers: 0 Minimal, 1 Normal, 2 Maximal, −1 no geometry. The tier in effect is the Effect Quality level, lowered by the
 * frame guard for the rest of a scene and, after drops in three scenes in a row, for the session. A shader pack takes
 * × 0.6 quads and one large layer. Per-frame counters are reset by {@code EpicRenderer} at the start of every frame.
 * <p>
 * Frame guard: one {@code System.nanoTime()} per frame (the only idle work of 7b) fills a 2-s ring; when a scene's first
 * beat arrives the baseline is the median. 20 frames in a row above 1.35 × baseline or below 30 fps, or 7b's own CPU time
 * above 2.0 ms averaged over 30 frames, drop one tier. Frames while the window is unfocused are ignored.
 */
public final class EpicBudget {

	public static final int[] QUADS = {400, 1500, 3000};
	public static final int[] LARGE = {1, 2, 3};
	public static final int[] BIG_BOLTS = {1, 3, 6};
	public static final int[] BIG_PILLARS = {1, 2, 3};
	public static final int[] FIGURES = {0, 1, 2};
	public static final int[] SILHOUETTES = {1, 6, 12};
	public static final int[] DISPLAYS = {2, 8, 24};
	public static final float PACK_QUADS = 0.6F;
	public static final float SLOW_FACTOR = 1.35F;
	public static final float MIN_FPS = 30.0F;
	public static final float CPU_MS = 2.0F;
	public static final int TRIGGER_FRAMES = 20;
	public static final int CPU_FRAMES = 30;

	private static final int RING = 512;
	private static final long[] STAMP = new long[RING];
	private static final long[] FRAME = new long[RING];
	private static final long[] SORT = new long[RING];
	private static int ringIndex;
	private static long lastFrame;

	private static long baseline;
	private static int slowFrames;
	private static long cpuSum;
	private static int cpuFrames;
	private static long cpuFrame;
	private static float cpuAverageMs;
	/** Tier drops in the running scene group (0 = none), and the session cap (2 = no cap). */
	private static int sceneDrop;
	private static int sessionCap = 2;
	private static int dropStreak;
	private static int dropsThisSession;
	private static boolean sceneRunning;
	private static boolean sceneDropped;

	// ---- per-frame counters (render thread == client thread)
	private static int quadsUsed;
	private static int quadsLast;
	private static int largeUsed;
	private static int largeLast;
	private static int boltsUsed;
	private static int pillarsUsed;
	private static int figuresUsed;

	/** The tier in effect: Effect Quality, the session cap and this scene's drops (−1 = no geometry). */
	public static int tier() {
		int base = EffectQuality.level().ordinal();
		return Math.max(-1, Math.min(base, sessionCap) - sceneDrop);
	}

	/** The tier for counts that never go below Minimal (particles, cards). */
	public static int tierOrMinimal() {
		return Math.max(0, tier());
	}

	public static int quadCap() {
		int tier = tier();
		if (tier < 0) {
			return 0;
		}
		float cap = QUADS[tier] * (ShaderCompat.packOn() ? PACK_QUADS : 1.0F);
		return Math.round(cap);
	}

	public static int largeCap() {
		int tier = tier();
		if (tier < 0) {
			return 0;
		}
		return Math.max(1, LARGE[tier] - (ShaderCompat.packOn() ? 1 : 0));
	}

	public static int boltCap() {
		return BIG_BOLTS[tierOrMinimal()];
	}

	public static int pillarCap() {
		return BIG_PILLARS[tierOrMinimal()];
	}

	public static int figureCap() {
		int tier = tier();
		return tier < 0 ? 0 : FIGURES[tier];
	}

	public static int silhouetteCap() {
		return SILHOUETTES[tierOrMinimal()];
	}

	public static int displayCap() {
		return DISPLAYS[tierOrMinimal()];
	}

	/** Soft layers may draw as geometry (not Minimal, "Geometri raksasa" on, and the pack switch). */
	public static boolean softGeometry() {
		if (tier() <= 0 || !EpicSettings.geometry()) {
			return false;
		}
		return !ShaderCompat.packOn() || EpicSettings.packGeometry();
	}

	/** Thin lightning-type geometry may draw (every tier but "no geometry", "Geometri raksasa" on). */
	public static boolean thinGeometry() {
		return tier() >= 0 && EpicSettings.geometry();
	}

	// ================================================================ per-frame counters

	/** Starts a frame's counting (called by the renderer before any element draws). */
	public static void beginFrame() {
		quadsLast = quadsUsed;
		largeLast = largeUsed;
		quadsUsed = 0;
		largeUsed = 0;
		boltsUsed = 0;
		pillarsUsed = 0;
		figuresUsed = 0;
	}

	/** Takes {@code quads} from this frame's budget; false (nothing taken) when it would overflow. */
	public static boolean takeQuads(int quads) {
		if (quadsUsed + quads > quadCap()) {
			return false;
		}
		quadsUsed += quads;
		return true;
	}

	/** Takes one large-layer slot for this frame. */
	public static boolean takeLarge() {
		if (largeUsed >= largeCap()) {
			return false;
		}
		largeUsed++;
		return true;
	}

	/** Takes one big-bolt slot ({@code half} = a thin convergence bolt, which costs half a slot). */
	public static boolean takeBolt(boolean half) {
		int cost = half ? 1 : 2;
		if (boltsUsed + cost > boltCap() * 2) {
			return false;
		}
		boltsUsed += cost;
		return true;
	}

	public static boolean takePillar() {
		if (pillarsUsed >= pillarCap()) {
			return false;
		}
		pillarsUsed++;
		return true;
	}

	public static boolean takeFigure() {
		if (figuresUsed >= figureCap()) {
			return false;
		}
		figuresUsed++;
		return true;
	}

	// ================================================================ frame guard

	/** One sample per frame, from the {@code epic_screen} HUD element. */
	public static void frame() {
		long now = System.nanoTime();
		long dt = lastFrame == 0L ? 0L : now - lastFrame;
		lastFrame = now;
		if (dt <= 0L || dt > 1_000_000_000L) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.isWindowActive()) {
			slowFrames = 0;
			return;
		}
		STAMP[ringIndex] = now;
		FRAME[ringIndex] = dt;
		ringIndex = (ringIndex + 1) % RING;
		cpuSum += cpuFrame;
		cpuFrame = 0L;
		if (++cpuFrames >= CPU_FRAMES) {
			cpuAverageMs = cpuSum / (float) cpuFrames / 1_000_000.0F;
			cpuSum = 0L;
			cpuFrames = 0;
			if (sceneRunning && cpuAverageMs > CPU_MS) {
				drop();
			}
		}
		if (!sceneRunning || baseline <= 0L) {
			slowFrames = 0;
			return;
		}
		float fps = 1_000_000_000.0F / dt;
		if (dt > baseline * SLOW_FACTOR || fps < MIN_FPS) {
			if (++slowFrames >= TRIGGER_FRAMES) {
				slowFrames = 0;
				drop();
			}
		} else {
			slowFrames = 0;
		}
	}

	/** Adds 7b's own CPU time of this frame (renderer submits and the HUD element). */
	public static void addCpu(long nanos) {
		cpuFrame += Math.max(0L, nanos);
	}

	/** The first beat of a scene group arrived: take the baseline (median of the last 2 s). */
	public static void sceneStarted() {
		if (sceneRunning) {
			return;
		}
		sceneRunning = true;
		sceneDropped = false;
		sceneDrop = 0;
		slowFrames = 0;
		baseline = median();
	}

	/** Every scene ended: remember drops for the session rule. */
	public static void sceneEnded() {
		if (!sceneRunning) {
			return;
		}
		sceneRunning = false;
		sceneDrop = 0;
		if (sceneDropped) {
			if (++dropStreak >= 3) {
				dropStreak = 0;
				sessionCap = Math.max(0, Math.min(sessionCap, EffectQuality.level().ordinal()) - 1);
			}
		} else {
			dropStreak = 0;
		}
	}

	/** {@code /sinematik reset}: forgets the session memory. */
	public static void resetSession() {
		sessionCap = 2;
		dropStreak = 0;
		dropsThisSession = 0;
		sceneDrop = 0;
		sceneDropped = false;
	}

	private static void drop() {
		if (tier() < 0) {
			return;
		}
		sceneDrop++;
		sceneDropped = true;
		dropsThisSession++;
	}

	private static long median() {
		long now = System.nanoTime();
		int n = 0;
		for (int i = 0; i < RING; i++) {
			if (FRAME[i] > 0L && now - STAMP[i] <= 2_000_000_000L) {
				SORT[n++] = FRAME[i];
			}
		}
		if (n == 0) {
			return 0L;
		}
		Arrays.sort(SORT, 0, n);
		return SORT[n / 2];
	}

	// ================================================================ info

	public static float baselineMs() {
		return baseline / 1_000_000.0F;
	}

	public static float cpuMs() {
		return cpuAverageMs;
	}

	public static int dropsThisSession() {
		return dropsThisSession;
	}

	public static int sessionCap() {
		return sessionCap;
	}

	public static int quadsLastFrame() {
		return Math.max(quadsLast, quadsUsed);
	}

	public static int largeLastFrame() {
		return Math.max(largeLast, largeUsed);
	}

	private EpicBudget() {
	}
}
