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
final class AvatarFx {

	/** A giant translucent copy: {@code scale} × the caster, in {@code rgb} at {@code alpha}. */
	record Avatar(long start, long end, float scale, int rgb, int alpha) {
	}

	/** {@code count} tinted copies on a ring of {@code radius} blocks around the caster. */
	record Clones(long start, long end, int count, float radius, int rgb) {
	}

	private static final Map<Integer, Avatar> AVATARS = new HashMap<>();
	private static final Map<Integer, Clones> CLONES = new HashMap<>();

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
			return;
		}
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
