package dev.kultivator.client.duel.anim;

import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.duel.blade.BladeArcs;
import dev.kultivator.duel.blade.BladeSegment;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.net.DuelPayloads;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * Where every blade really is (DESIGN 35.5.4, contract C35.A4). {@link BladeProbeLayer} writes the drawn blade of each
 * armed PlayerModel entity within 32 blocks every frame (world space, after every mixin and animation library touched
 * the arm, so the blade recorded is the blade seen); module V reads it for the contact point of sparks and for the blade
 * trails. An entry older than {@link #MAX_AGE_FRAMES} frames is not returned: the entity is behind the camera, culled or
 * out of range, and {@link #segment} falls back to the shared geometry ({@link BladeArcs}) on the pose the entity shows
 * right now ({@link DuelPoses#channels}), then on the pose of its last swing.
 * <p>
 * Client thread only. Idle cost: one map lookup per probed entity per frame; entries are pruned every 20 ticks.
 */
public final class BladeTracker {

	/** A drawn sample older than this many frames is stale (C35.A4: ≤ 10 frames). */
	public static final int MAX_AGE_FRAMES = 10;
	/** Probes only record within this distance of the camera (35.5.4). */
	public static final double RANGE = 32.0D;

	/** The last two drawn samples of one blade. */
	static final class Entry {
		Vec3 grip = Vec3.ZERO;
		Vec3 tip = Vec3.ZERO;
		Vec3 prevGrip = Vec3.ZERO;
		Vec3 prevTip = Vec3.ZERO;
		/** Presentation time (σ-scaled ticks) of the samples. */
		double time;
		double prevTime = Double.NaN;
		long frame = Long.MIN_VALUE / 2;
		/** Tip velocity in blocks per presentation tick (kept through a freeze). */
		Vec3 velocity = Vec3.ZERO;
		boolean fist;
	}

	private static final Int2ObjectMap<Entry> ENTRIES = new Int2ObjectOpenHashMap<>();
	private static long frame;
	private static int fallbacks;

	/** Called once per rendered level frame (before the entities are drawn). */
	static void beginFrame() {
		frame++;
	}

	static long frame() {
		return frame;
	}

	/** Records the blade of {@code entityId} as drawn this frame (world coordinates). */
	static void record(int entityId, Vec3 grip, Vec3 tip, boolean fist) {
		Entry e = ENTRIES.get(entityId);
		if (e == null) {
			e = new Entry();
			ENTRIES.put(entityId, e);
		}
		double now = EpicClock.time(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		if (e.frame == frame) {
			// a second probe of the same entity this frame (an extra render pass): keep the newest blade only
			e.grip = grip;
			e.tip = tip;
			return;
		}
		boolean continuous = frame - e.frame <= 3;
		if (continuous) {
			double dt = now - e.time;
			if (dt > 1.0E-3D) {
				e.velocity = tip.subtract(e.tip).scale(1.0D / dt);
			}
			e.prevGrip = e.grip;
			e.prevTip = e.tip;
			e.prevTime = e.time;
		} else {
			e.prevGrip = grip;
			e.prevTip = tip;
			e.prevTime = Double.NaN;
			e.velocity = Vec3.ZERO;
		}
		e.grip = grip;
		e.tip = tip;
		e.time = now;
		e.frame = frame;
		e.fist = fist;
	}

	// ================================================================== API (C35.A4)

	/**
	 * The blade of {@code entityId}: the one drawn within the last {@link #MAX_AGE_FRAMES} frames, else the shared
	 * geometry of the pose it shows (or of its last swing), else null (no entity, or nothing in its hands).
	 */
	public static @Nullable BladeSegment segment(int entityId, float partial) {
		Entry e = ENTRIES.get(entityId);
		if (e != null && frame - e.frame <= MAX_AGE_FRAMES) {
			return new BladeSegment(e.grip, e.tip);
		}
		return fallback(entityId, partial);
	}

	/** The drawn blade only (null when it was not drawn in the last {@link #MAX_AGE_FRAMES} frames). */
	public static @Nullable BladeSegment drawn(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e != null && frame - e.frame <= MAX_AGE_FRAMES ? new BladeSegment(e.grip, e.tip) : null;
	}

	/** The blade's previous drawn sample (for interpolating a trail between frames), or null. */
	public static @Nullable BladeSegment previous(int entityId) {
		Entry e = ENTRIES.get(entityId);
		if (e == null || frame - e.frame > MAX_AGE_FRAMES || Double.isNaN(e.prevTime)) {
			return null;
		}
		return new BladeSegment(e.prevGrip, e.prevTip);
	}

	/**
	 * The velocity of the blade's point in blocks per presentation tick (σ-scaled, so it is the speed seen on screen;
	 * zero when unknown). Module V reflects sparks about it.
	 */
	public static Vec3 velocity(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e == null || frame - e.frame > MAX_AGE_FRAMES ? Vec3.ZERO : e.velocity;
	}

	/** Frames since the blade was drawn (large when never). */
	public static long age(int entityId) {
		Entry e = ENTRIES.get(entityId);
		return e == null ? Long.MAX_VALUE : frame - e.frame;
	}

	/** How many blades were drawn in the last frames, and how many fallbacks were computed since the last prune. */
	public static int drawnCount() {
		int n = 0;
		for (Entry e : ENTRIES.values()) {
			if (frame - e.frame <= MAX_AGE_FRAMES) {
				n++;
			}
		}
		return n;
	}

	/** The ids of the blades drawn in the last frames (the debug lines). */
	static int[] drawnIds() {
		it.unimi.dsi.fastutil.ints.IntArrayList ids = new it.unimi.dsi.fastutil.ints.IntArrayList();
		for (Int2ObjectMap.Entry<Entry> e : ENTRIES.int2ObjectEntrySet()) {
			if (frame - e.getValue().frame <= MAX_AGE_FRAMES) {
				ids.add(e.getIntKey());
			}
		}
		return ids.toIntArray();
	}

	public static int fallbackCount() {
		return fallbacks;
	}

	// ================================================================== fallback

	private static @Nullable BladeSegment fallback(int entityId, float partial) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		Entity entity = level.getEntity(entityId);
		if (!(entity instanceof LivingEntity living)) {
			return null;
		}
		WeaponClass weapon = weaponOf(living);
		Vec3 feet = living.getPosition(partial);
		float yaw = lerpDegrees(partial, living.yBodyRotO, living.yBodyRot);
		boolean left = living.getMainArm() == HumanoidArm.LEFT;
		boolean slim = slim(living);
		float[] channels = DuelPoses.channels(entityId);
		fallbacks++;
		if (channels != null) {
			// the pose the entity shows now (authored right-handed; BladeArcs mirrors for the left hand)
			return BladeArcs.fromChannels(channels, weapon, feet, yaw, living.getScale(), slim, left);
		}
		DuelPayloads.DuelSwing swing = DuelClient.swing(entityId);
		if (swing == null || weapon == WeaponClass.TANGAN_KOSONG && DuelClient.fighter(entityId) == null) {
			return null;
		}
		return BladeArcs.sample(dev.kultivator.duel.blade.DuelPose.forSwing(swing.kind(), swing.dir()), weapon, BladeArcs.IMPACT_T01, feet, yaw,
				living.getScale(), slim, left);
	}

	/** The weapon class of an entity: the server's word in a duel, else what it holds. */
	static WeaponClass weaponOf(LivingEntity living) {
		DuelPayloads.DuelFighterSync fighter = DuelClient.fighter(living.getId());
		if (fighter != null) {
			return WeaponClass.byOrdinal(fighter.weaponClass());
		}
		return WeaponClass.of(living);
	}

	static boolean slim(Entity entity) {
		return entity instanceof AbstractClientPlayer player && player.getSkin().model() == PlayerModelType.SLIM;
	}

	static float lerpDegrees(float t, float from, float to) {
		float d = to - from;
		while (d < -180.0F) {
			d += 360.0F;
		}
		while (d >= 180.0F) {
			d -= 360.0F;
		}
		return from + t * d;
	}

	// ================================================================== housekeeping

	/** Drops entries not drawn for a while (every 20 ticks from {@link DuelAnim}). */
	static void prune() {
		Iterator<Int2ObjectMap.Entry<Entry>> it = ENTRIES.int2ObjectEntrySet().iterator();
		while (it.hasNext()) {
			if (frame - it.next().getValue().frame > 200) {
				it.remove();
			}
		}
		fallbacks = 0;
	}

	static void clear() {
		ENTRIES.clear();
		fallbacks = 0;
	}

	static boolean isEmpty() {
		return ENTRIES.isEmpty();
	}

	private BladeTracker() {
	}
}
