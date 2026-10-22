package dev.kultivator.client.sekte.tangga;

import dev.kultivator.sekte.tangga.JalurTangga;
import dev.kultivator.sekte.tangga.TanggaPayloads;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The client's picture of the running stairs (DESIGN 36.2.4): one entry per hall from module G's
 * {@link TanggaPayloads.State}, kept on the client thread, aged by the client tick. Drawing reads it
 * ({@link TanggaIlusiRenderer}, {@link TanggaHud}, the climb pose). A stair whose server went quiet for 20 s is dropped;
 * {@link TanggaPayloads#PHASE_OFF} fades it out over two seconds.
 */
final class TanggaIlusi {

	/** Ticks the projection takes to unfurl from the projector to the summit. */
	static final float FORM_TICKS = 70.0F;
	static final float FADE_TICKS = 40.0F;
	static final int STALE_TICKS = 400;
	/** Ticks a received step glides to the next one (the server sends every 10). */
	static final float STEP_GLIDE = 10.0F;

	/** One climber as last heard. */
	static final class Climber {
		final int entityId;
		int lane;
		float prevStep;
		float step;
		long stepAt;
		int limit = -1;
		int state;
		int verdict;
		long stateSince;

		Climber(int entityId) {
			this.entityId = entityId;
		}

		/** The step shown at this moment (glides between server updates). */
		float shownStep(double now) {
			float t = (float) Math.max(0.0D, Math.min(1.0D, (now - stepAt) / STEP_GLIDE));
			return prevStep + (step - prevStep) * t;
		}
	}

	/** One stair. */
	static final class Stair {
		final UUID owner;
		JalurTangga.Frame frame;
		int phase;
		int flags;
		int lod;
		long phaseSince;
		long lastUpdate;
		float formed;
		float visible = 1.0F;
		boolean ending;
		final Map<Integer, Climber> climbers = new LinkedHashMap<>();

		Stair(UUID owner) {
			this.owner = owner;
		}

		boolean has(int flag) {
			return (flags & flag) != 0;
		}

		/** How far the projection reaches (0-1000): it unfurls while forming. */
		float reach() {
			return formed * JalurTangga.STEPS;
		}

		/** True while any climber stands on the summit. */
		boolean summit() {
			for (Climber c : climbers.values()) {
				if (c.state == TanggaPayloads.C_PEAK) {
					return true;
				}
			}
			return false;
		}

		Vec3 centre() {
			return new Vec3(frame.cx(), frame.oy() + JalurTangga.HEIGHT * 0.5D, frame.cz());
		}
	}

	private static final Map<UUID, Stair> STAIRS = new HashMap<>();
	/** Entity id to its climber (for the pose). */
	private static final Map<Integer, Climber> BY_ENTITY = new HashMap<>();
	private static long clock;

	static void onState(TanggaPayloads.State payload) {
		Stair stair = STAIRS.get(payload.owner());
		if (payload.phase() == TanggaPayloads.PHASE_OFF) {
			if (stair != null) {
				stair.ending = true;
			}
			return;
		}
		if (stair == null) {
			stair = new Stair(payload.owner());
			STAIRS.put(payload.owner(), stair);
			stair.phaseSince = clock;
			// a late joiner sees the stair already formed
			stair.formed = payload.phase() == TanggaPayloads.PHASE_FORMING ? 0.0F : 1.0F;
		}
		stair.ending = false;
		stair.visible = Math.max(stair.visible, 0.01F);
		stair.frame = JalurTangga.Frame.of(payload.ox(), payload.oy(), payload.oz(), payload.fx(), payload.fz());
		if (stair.phase != payload.phase()) {
			stair.phase = payload.phase();
			stair.phaseSince = clock;
		}
		stair.flags = payload.flags();
		stair.lod = payload.lod();
		stair.lastUpdate = clock;
		List<Integer> seen = new ArrayList<>();
		for (TanggaPayloads.Climber in : payload.climbers()) {
			seen.add(in.entityId());
			Climber c = stair.climbers.computeIfAbsent(in.entityId(), Climber::new);
			BY_ENTITY.put(in.entityId(), c);
			float shown = c.shownStep(clock);
			c.prevStep = c.stepAt == 0L && c.step == 0.0F ? in.step() : shown;
			c.step = in.step();
			c.stepAt = clock;
			c.lane = in.lane();
			c.limit = in.limit();
			c.verdict = in.verdict();
			if (c.state != in.state() || c.stateSince == 0L) {
				c.state = in.state();
				c.stateSince = clock;
			}
		}
		for (Iterator<Map.Entry<Integer, Climber>> it = stair.climbers.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Climber> entry = it.next();
			if (!seen.contains(entry.getKey())) {
				BY_ENTITY.remove(entry.getKey());
				it.remove();
			}
		}
	}

	static void tick() {
		clock++;
		if (STAIRS.isEmpty()) {
			return;
		}
		for (Iterator<Stair> it = STAIRS.values().iterator(); it.hasNext(); ) {
			Stair stair = it.next();
			if (stair.phase == TanggaPayloads.PHASE_FORMING) {
				stair.formed = Math.min(1.0F, stair.formed + 1.0F / FORM_TICKS);
			} else {
				stair.formed = Math.min(1.0F, stair.formed + 3.0F / FORM_TICKS);
			}
			boolean fading = stair.ending || stair.phase == TanggaPayloads.PHASE_FADE;
			if (fading) {
				stair.visible = Math.max(0.0F, stair.visible - 1.0F / FADE_TICKS);
			} else {
				stair.visible = Math.min(1.0F, stair.visible + 1.0F / 20.0F);
			}
			if (stair.ending && stair.visible <= 0.0F || clock - stair.lastUpdate > STALE_TICKS) {
				for (Integer id : stair.climbers.keySet()) {
					BY_ENTITY.remove(id);
				}
				it.remove();
			}
		}
	}

	static void clear() {
		STAIRS.clear();
		BY_ENTITY.clear();
	}

	static boolean any() {
		return !STAIRS.isEmpty();
	}

	static Iterable<Stair> stairs() {
		return STAIRS.values();
	}

	static long clock() {
		return clock;
	}

	/** The climber shown by entity {@code id}, or null (the pose asks every frame: one map lookup). */
	static @Nullable Climber climber(int id) {
		return BY_ENTITY.isEmpty() ? null : BY_ENTITY.get(id);
	}

	/** The stair nearest to {@code pos} within {@code range} blocks of its axis, or null. */
	static @Nullable Stair nearest(Vec3 pos, double range) {
		Stair best = null;
		double bestD = Double.MAX_VALUE;
		for (Stair stair : STAIRS.values()) {
			if (stair.frame == null || !JalurTangga.near(stair.frame, pos.x, pos.y, pos.z, range)) {
				continue;
			}
			double d = stair.centre().distanceToSqr(pos);
			if (d < bestD) {
				bestD = d;
				best = stair;
			}
		}
		return best;
	}

	private TanggaIlusi() {
	}
}
