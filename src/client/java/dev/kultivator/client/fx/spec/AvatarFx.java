package dev.kultivator.client.fx.spec;

import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Live state of the AVATAR and CLONE renderers (DESIGN 22.4.4), keyed by the caster's entity id: a translucent scaled
 * copy of the caster's own model (giant forms, Dharma colossus) and tinted copies around it. {@link AvatarLayer} reads
 * it while drawing players; nothing here is an entity, so nothing leaks into the level. Times are in milliseconds
 * (render thread).
 */
public final class AvatarFx {

	/** A giant translucent copy: {@code scale} × the caster, in {@code rgb} at {@code alpha}. */
	record Avatar(long start, long end, float scale, int rgb, int alpha) {
	}

	/** {@code count} tinted copies on a ring of {@code radius} blocks around the caster. */
	record Clones(long start, long end, int count, float radius, int rgb) {
	}

	/** Frozen afterimages at fixed world points (movement arts, polish S16), each fading in turn. */
	record Fixed(long start, long end, java.util.List<net.minecraft.world.phys.Vec3> points, float yaw, int rgb, int alpha) {
	}

	private static final Map<Integer, Avatar> AVATARS = new HashMap<>();
	private static final Map<Integer, Clones> CLONES = new HashMap<>();
	private static final Map<Integer, Fixed> FIXED = new HashMap<>();

	/** One phantom copy of a caster (clones, deities, emperors) at a world point, refreshed every tick by {@link PhantomFx}. */
	record Copy(net.minecraft.world.phys.Vec3 at, float yaw, float scale, int rgb, int alpha) {
	}

	private record Copies(long until, java.util.List<Copy> list) {
	}

	private static final Map<Integer, Copies> COPIES = new HashMap<>();

	/** Sets the phantom copies of {@code entityId} for the next 200 ms (PhantomFx refreshes them each tick). */
	static void copies(int entityId, java.util.List<Copy> list) {
		if (list.isEmpty()) {
			COPIES.remove(entityId);
			return;
		}
		COPIES.put(entityId, new Copies(Util.getMillis() + 200L, java.util.List.copyOf(list)));
	}

	static java.util.List<Copy> copiesOf(int entityId) {
		Copies copies = COPIES.get(entityId);
		if (copies == null) {
			return java.util.List.of();
		}
		if (Util.getMillis() >= copies.until()) {
			COPIES.remove(entityId);
			return java.util.List.of();
		}
		return copies.list();
	}

	static void avatar(int entityId, float scale, int rgb, int alpha, int ticks) {
		long now = Util.getMillis();
		AVATARS.put(entityId, new Avatar(now, now + Math.max(1, ticks) * 50L, Math.max(1.0F, Math.min(4.0F, scale)), rgb & 0xFFFFFF,
				Math.max(30, Math.min(220, alpha))));
	}

	static void clones(int entityId, int count, float radius, int rgb, int ticks) {
		if (count <= 0) {
			return;
		}
		long now = Util.getMillis();
		CLONES.put(entityId, new Clones(now, now + Math.max(1, ticks) * 50L, count, Math.max(1.0F, Math.min(6.0F, radius)), rgb & 0xFFFFFF));
	}

	/** Afterimages of the caster frozen at {@code points} facing {@code yaw}, for {@code ticks}. */
	public static void fixed(int entityId, java.util.List<net.minecraft.world.phys.Vec3> points, float yaw, int rgb, int alpha, int ticks) {
		if (points.isEmpty()) {
			return;
		}
		long now = Util.getMillis();
		FIXED.put(entityId, new Fixed(now, now + Math.max(4, ticks) * 50L, java.util.List.copyOf(points), yaw, rgb & 0xFFFFFF,
				Math.max(30, Math.min(220, alpha))));
	}

	static @Nullable Fixed fixedOf(int entityId) {
		Fixed fixed = FIXED.get(entityId);
		if (fixed != null && Util.getMillis() >= fixed.end()) {
			FIXED.remove(entityId);
			return null;
		}
		return fixed;
	}

	/** Ends every copy of a caster (ABORT). */
	static void end(int entityId) {
		AVATARS.remove(entityId);
		CLONES.remove(entityId);
		FIXED.remove(entityId);
		COPIES.remove(entityId);
	}

	static @Nullable Avatar avatarOf(int entityId) {
		Avatar avatar = AVATARS.get(entityId);
		if (avatar != null && Util.getMillis() >= avatar.end()) {
			AVATARS.remove(entityId);
			return null;
		}
		return avatar;
	}

	static @Nullable Clones clonesOf(int entityId) {
		Clones clones = CLONES.get(entityId);
		if (clones != null && Util.getMillis() >= clones.end()) {
			CLONES.remove(entityId);
			return null;
		}
		return clones;
	}

	/** 0..1 for fading in over 0.2 s and out over the last 0.3 s. */
	static float fade(long start, long end) {
		long now = Util.getMillis();
		float in = Math.min(1.0F, (now - start) / 200.0F);
		float out = Math.min(1.0F, Math.max(0.0F, (end - now) / 300.0F));
		return Math.max(0.0F, Math.min(in, out));
	}

	/** Drops finished entries (once a second) and everything on a world change. */
	static void prune(boolean all) {
		if (all) {
			AVATARS.clear();
			CLONES.clear();
			FIXED.clear();
			COPIES.clear();
			return;
		}
		FIXED.values().removeIf(fixed -> Util.getMillis() >= fixed.end());
		long now = Util.getMillis();
		for (Iterator<Avatar> it = AVATARS.values().iterator(); it.hasNext(); ) {
			if (now >= it.next().end()) {
				it.remove();
			}
		}
		for (Iterator<Clones> it = CLONES.values().iterator(); it.hasNext(); ) {
			if (now >= it.next().end()) {
				it.remove();
			}
		}
	}

	private AvatarFx() {
	}
}
