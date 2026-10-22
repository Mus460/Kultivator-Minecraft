package dev.kultivator.client.duel.fx;

import dev.kultivator.client.epic.ShaderCompat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The duel's own RIBBON renderer and the one render hook of the duel visuals (DESIGN 35.4.4, 35.4.12): from
 * {@code LevelRenderEvents.COLLECT_SUBMITS} (relative to the camera, never in the Iris shadow pass) it samples the blades
 * actually drawn into the live {@link BladeTrail}s, measures the tip speed of every fighter in range (for the spark
 * direction), then draws every layer of the duel into one {@link DuelGeo} frame: scorch decals, the ring's ink circle and
 * ropes, the GATHER arrows, shock rings, blade trails, sword qi waves, blade-lock hot spots, metal flashes, the intro's
 * wind streaks, the finisher's ink stroke and the test previews. Live trails 2 / 6 / 12 (Minimal: qi trails only).
 * <p>
 * When a trail shows: in a formal duel always; outside one only for a qi swing, or for every swing with "Jejak bilah"
 * Selalu; never with Mati. A swing flagged BC (Better Combat draws its own trail) gets only the qi sheet, no steel one.
 * Idle cost: one empty-map check per frame.
 */
public final class DuelTrailRenderer {

	/** Tip samples of fighters in range: the newest two tips and their presentation times. */
	private static final class Probe {
		Vec3 tip0;
		Vec3 tip1;
		double t0;
		double t1;
		long seen;
	}

	private static final Map<Integer, BladeTrail> TRAILS = new HashMap<>();
	private static final Map<Integer, Probe> PROBES = new HashMap<>();
	private static final DuelGeo FRAME = new DuelGeo();
	private static boolean registered;
	private static float lastPartial = 1.0F;
	private static long frames;

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(DuelTrailRenderer::collect);
	}

	/** The partial tick of the last frame (the contact solver reads the blades of that frame). */
	static float lastPartial() {
		return lastPartial;
	}

	/** A swing arrived or changed: its trail starts recording near the impact (or stops on a feint / rejection). */
	static void onSwing(DuelFx.SwingView swing, boolean fresh) {
		if (swing.has(DuelCodes.SWING_FEINT_CANCEL) || swing.has(DuelCodes.SWING_REJECTED)) {
			BladeTrail trail = TRAILS.get(swing.entityId);
			if (trail != null) {
				trail.stop(DuelLinks.serverTick(0.0F));
			}
			return;
		}
		if (!wanted(swing)) {
			return;
		}
		BladeTrail trail = TRAILS.get(swing.entityId);
		if (trail == null) {
			int cap = DuelFxSettings.pick(2, 6, 12);
			if (TRAILS.size() >= cap) {
				return;
			}
			trail = new BladeTrail(swing.entityId);
			TRAILS.put(swing.entityId, trail);
		}
		trail.style(swing);
	}

	/** A clash: the attacker's trail keeps recording through the follow-through (35.8.2: the result may land late). */
	static void onClash(DuelFx.ClashEvent e) {
		BladeTrail trail = TRAILS.get(e.aId());
		if (trail != null) {
			trail.activeUntil = Math.max(trail.activeUntil, e.serverTick() + 3.0D);
		}
	}

	private static boolean wanted(DuelFx.SwingView swing) {
		DuelFxSettings.Trails setting = DuelFxSettings.trails();
		if (setting == DuelFxSettings.Trails.MATI) {
			return false;
		}
		boolean qi = swing.has(DuelCodes.SWING_QI);
		if (swing.has(DuelCodes.SWING_BC) && !qi) {
			// Better Combat draws its own steel trail for this swing
			return false;
		}
		if (DuelFxSettings.tier() == 0 && !qi) {
			return false;
		}
		boolean formal = DuelFx.sessionOf(swing.entityId) != null;
		return formal || qi || setting == DuelFxSettings.Trails.SELALU;
	}

	/** The tip velocity of {@code entityId} (blocks per tick) from the last frames, or null. */
	static @Nullable Vec3 tipVelocity(int entityId) {
		double now = DuelTime.now();
		BladeTrail trail = TRAILS.get(entityId);
		if (trail != null) {
			Vec3 v = trail.tipVelocity(now);
			if (v != null) {
				return v;
			}
		}
		Probe probe = PROBES.get(entityId);
		if (probe == null || probe.tip0 == null || probe.tip1 == null || now - probe.t1 > 3.0D) {
			return null;
		}
		double dt = probe.t1 - probe.t0;
		if (dt < 0.05D) {
			return null;
		}
		return probe.tip1.subtract(probe.tip0).scale(1.0D / dt);
	}

	static void tick() {
		if (!TRAILS.isEmpty()) {
			double now = DuelTime.now();
			double server = DuelLinks.serverTick(0.0F);
			TRAILS.values().removeIf(trail -> trail.dead(now, server));
		}
		if (!PROBES.isEmpty() && DuelFx.ticks() % 40 == 0) {
			PROBES.values().removeIf(probe -> frames - probe.seen > 200);
		}
	}

	static void clear() {
		TRAILS.clear();
		PROBES.clear();
	}

	// ================================================================ the render hook

	private static void collect(LevelRenderContext context) {
		if (ShaderCompat.shadowPass() || idle()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		try {
			frames++;
			CameraRenderState camera = context.levelState().cameraRenderState;
			float partial = context.levelState().worldPartialTicks;
			lastPartial = partial;
			FRAME.setup(camera, partial, context.poseStack(), context.submitNodeCollector());
			sample(partial);
			ScorchDecal.render(FRAME);
			DuelRingVisual.render(FRAME);
			DuelMarks.render(FRAME);
			ShockRing.render(FRAME);
			for (BladeTrail trail : TRAILS.values()) {
				trail.render(FRAME);
			}
			QiWaveVisual.render(FRAME);
			SparkBurst.renderBinds(FRAME);
			MetalFlash.render(FRAME);
			DuelIntroFilm.render(FRAME);
			DuelFinisherFilm.render(FRAME);
			DuelPreviews.render(FRAME);
			FRAME.flush();
		} catch (RuntimeException exception) {
			DuelFx.warn("render", exception);
			FRAME.clear();
		}
	}

	/** True when no duel layer has anything to draw this frame. */
	private static boolean idle() {
		return TRAILS.isEmpty() && DuelFx.sessions().isEmpty() && DuelFx.binds().isEmpty() && MetalFlash.idle() && ShockRing.idle() && ScorchDecal.idle()
				&& QiWaveVisual.idle() && DuelIntroFilm.idle() && DuelFinisherFilm.idle() && DuelPreviews.idle() && DuelRingVisual.idle()
				&& DuelFx.fighters().isEmpty();
	}

	/** Samples the drawn blades of every live trail and every fighter within 48 b (tip speed for the sparks). */
	private static void sample(float partial) {
		double now = FRAME.time;
		double server = DuelLinks.serverTick(partial);
		for (Iterator<BladeTrail> it = TRAILS.values().iterator(); it.hasNext(); ) {
			BladeTrail trail = it.next();
			if (!trail.recording(server)) {
				continue;
			}
			DuelLinks.Blade blade = DuelLinks.drawn(trail.entityId, partial);
			if (blade != null) {
				trail.push(blade, now);
				probe(trail.entityId, blade.tip(), now);
			}
		}
		for (DuelFx.FighterView fighter : DuelFx.fighters()) {
			if (TRAILS.containsKey(fighter.entityId)) {
				continue;
			}
			Entity entity = DuelFx.entity(fighter.entityId);
			if (entity == null || FRAME.dist2(entity.position()) > 48.0D * 48.0D) {
				continue;
			}
			DuelLinks.Blade blade = DuelLinks.drawn(fighter.entityId, partial);
			if (blade != null) {
				probe(fighter.entityId, blade.tip(), now);
			}
		}
	}

	private static void probe(int entityId, Vec3 tip, double now) {
		Probe probe = PROBES.computeIfAbsent(entityId, id -> new Probe());
		probe.seen = frames;
		if (probe.tip1 != null && now - probe.t1 < 0.15D) {
			// too close in time for a stable velocity: keep the older base, move the newest
			probe.tip1 = tip;
			probe.t1 = now;
			return;
		}
		probe.tip0 = probe.tip1;
		probe.t0 = probe.t1;
		probe.tip1 = tip;
		probe.t1 = now;
	}

	private DuelTrailRenderer() {
	}
}
