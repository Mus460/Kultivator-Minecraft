package dev.kultivator.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * A position on the path: realm, layer (only Immortal Ascension has 9) and stage (0 Early, 1 Middle, 2 Late, 3 Peak;
 * Mortal has only stage 0). {@link #step()} is the linear index, 0 for Mortal up to {@link #MAX_STEP}.
 */
public record Rank(Realm realm, int layer, int stage) {

	public static final Rank MORTAL = new Rank(Realm.MORTAL, 0, 0);
	public static final int PEAK = 3;
	private static final Rank[] BY_STEP = buildSteps();
	public static final int MAX_STEP = BY_STEP.length - 1;

	public Rank {
		layer = Math.max(0, Math.min(realm.layers() - 1, layer));
		stage = Math.max(0, Math.min(realm.stages() - 1, stage));
	}

	private static Rank[] buildSteps() {
		List<Rank> all = new ArrayList<>();
		for (Realm realm : Realm.values()) {
			for (int layer = 0; layer < realm.layers(); layer++) {
				for (int stage = 0; stage < realm.stages(); stage++) {
					all.add(new Rank(realm, layer, stage));
				}
			}
		}
		return all.toArray(new Rank[0]);
	}

	public static Rank ofStep(int step) {
		return BY_STEP[Math.max(0, Math.min(BY_STEP.length - 1, step))];
	}

	public int step() {
		for (int i = 0; i < BY_STEP.length; i++) {
			if (BY_STEP[i].equals(this)) {
				return i;
			}
		}
		return 0;
	}

	/** At the top of the current realm (or Immortal Ascension layer): going on needs a breakthrough. Mortal always is. */
	public boolean isBottleneck() {
		return stage == realm.stages() - 1;
	}

	/** True Immortal Peak: nothing further. */
	public boolean isMax() {
		return step() == MAX_STEP;
	}

	/** The rank right after this one (a stage-up, or the first stage after a breakthrough). */
	public Rank next() {
		return ofStep(step() + 1);
	}

	/** The rank before this one (Mortal stays Mortal). */
	public Rank previous() {
		return ofStep(step() - 1);
	}

	/** True when going to {@link #next()} is a breakthrough into a new realm or Immortal Ascension layer. */
	public boolean nextIsBreakthrough() {
		return isBottleneck() && !isMax();
	}

	/** XP that fills the bar at this rank. */
	public double xpRequired() {
		return realm.baseXp() * (1.0D + 0.35D * stage) * (1.0D + 0.30D * layer);
	}

	public Component stageName() {
		if (realm.isMortal()) {
			return Component.empty();
		}
		return Component.translatable("kultivator.stage." + stage);
	}

	/** "Golden Core, Late" / "Immortal Ascension 3, Peak" / "Mortal". */
	public MutableComponent displayName() {
		if (realm.isMortal()) {
			return realm.displayName().copy();
		}
		if (realm.layers() > 1) {
			return Component.translatable("kultivator.rank.layered", realm.displayName(), layer + 1, stageName());
		}
		return Component.translatable("kultivator.rank.simple", realm.displayName(), stageName());
	}
}
