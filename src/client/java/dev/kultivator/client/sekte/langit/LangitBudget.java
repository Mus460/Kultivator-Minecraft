package dev.kultivator.client.sekte.langit;

import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

/**
 * The quad and particle shares of the sky-sect visuals (DESIGN 37.6.7), on top of the shared 7b frame budget: every
 * quad is first taken from {@link EpicBudget#takeQuads} (so the sky never starves a technique or a cinematic of the
 * same frame) and then counted against the sky's own frame cap {@link #TOTAL} (400 / 1 200 / 2 600, below
 * {@code EpicBudget.QUADS} 400 / 1 500 / 3 000), × {@link EpicBudget#PACK_QUADS} under a shader pack. A slow frame
 * ({@code EpicBudget.softGeometry()} false) drops every layer to its Minimal numbers until it recovers. The layers ask
 * in priority order (cloud sea > falls > dome > stars > cranes > petals > lanterns > halo), so a short budget cuts
 * from the bottom.
 */
final class LangitBudget {

	/** Per-layer counts at Minimal / Normal / Maximal (37.6.7). */
	static final int[] SEA_LAYERS = {1, 2, 3};
	static final int[] SEA_RINGS = {4, 6, 8};
	static final int[] SEA_SEGMENTS = {8, 12, 16};
	static final int[] FALL_SEGMENTS = {4, 8, 16};
	static final int[] FALL_EMITTERS = {4, 8, 8};
	static final int[] FALL_LENGTH = {12, 24, 40};
	static final int[] FALL_PUFFS = {2, 6, 12};
	static final int[] DOME_RINGS = {1, 3, 6};
	static final int[] DOME_SEGMENTS = {24, 40, 64};
	static final int[] RIVER_QUADS = {8, 16, 24};
	static final int[] STARS = {0, 20, 60};
	static final int[] DAWN_SHAFTS = {0, 5, 9};
	static final int[] HALO = {0, 1, 1};
	static final int[] CRANES = {0, 5, 12};
	static final int[] CRANE_FLOCKS = {0, 1, 2};
	static final int[] PETALS = {0, 40, 120};
	static final int[] LANTERNS = {0, 10, 30};
	static final int[] TOTAL = {400, 1200, 2600};
	static final int[] PARTICLES = {40, 300, 800};

	private static int used;
	private static int cap;

	/** The tier in effect for the sky layers: −1 = no geometry at all, a slow frame forces Minimal. */
	static int tier() {
		int tier = EpicBudget.tier();
		if (tier < 0) {
			return -1;
		}
		return EpicBudget.softGeometry() ? Math.min(2, tier) : 0;
	}

	/** The tier for petals, mist puffs and lanterns: the vanilla "Partikel" Minimal forces 0. */
	static int particleTier(Minecraft minecraft, int tier) {
		if (tier <= 0) {
			return Math.max(0, tier);
		}
		return minecraft.options.particles().get() == ParticleStatus.MINIMAL ? 0 : tier;
	}

	/** Starts the sky's count for this frame. */
	static void begin(int tier) {
		used = 0;
		cap = tier < 0 ? 0 : Math.round(TOTAL[tier] * (ShaderCompat.packOn() ? EpicBudget.PACK_QUADS : 1.0F));
	}

	/** Takes {@code quads} for a sky layer; false (nothing taken) when the sky's cap or the 7b frame budget is spent. */
	static boolean take(int quads) {
		if (quads <= 0) {
			return true;
		}
		if (used + quads > cap || !EpicBudget.takeQuads(quads)) {
			return false;
		}
		used += quads;
		return true;
	}

	/** Takes up to {@code quads}; returns how many were granted (0 when none). */
	static int takeUpTo(int quads) {
		int n = Math.min(quads, cap - used);
		while (n > 0 && !EpicBudget.takeQuads(n)) {
			n /= 2;
		}
		if (n <= 0) {
			return 0;
		}
		used += n;
		return n;
	}

	static int used() {
		return used;
	}

	/** Particle tokens for a one-shot effect (vanilla particles), at DETAIL priority, capped at the 37.6.7 row. */
	static int particles(int wanted, int tier) {
		int n = Math.min(wanted, PARTICLES[Math.max(0, Math.min(2, tier))]);
		return FxBudget.take(n, FxBudget.Priority.DETAIL);
	}

	private LangitBudget() {
	}
}
