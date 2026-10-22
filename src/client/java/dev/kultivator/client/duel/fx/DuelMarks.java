package dev.kultivator.client.duel.fx;

import dev.kultivator.client.fx.spec.DisplayKey;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The world marks of the duel (DESIGN 35.4.11, 35.3.8, 35.3.6): the red 危 ("Bahaya") over a fighter from the first tick
 * of a perilous windup until just after its impact, struck in with a red glint like a sword catching the light; the red
 * 決 over a staggered fighter (posture broken) for anyone who could land the deathblow; and, while the duellists walk to
 * their start marks (GATHER), ink chevrons flowing along the ground from the local duellist to their mark with a glowing
 * circle on the mark itself. The glyphs are client-only text displays posed through {@link DisplayKey} (they pulse); the
 * HUD versions are module U's.
 */
final class DuelMarks {

	private static final int DANGER_RGB = 0xE01818;
	private static final int DEATHBLOW_RGB = 0xC8141E;

	private static final class Mark {
		final int entityId;
		final boolean danger;
		final Display.TextDisplay display;
		final DisplayKey key;
		long until;
		final long born;

		Mark(int entityId, boolean danger, Display.TextDisplay display, long until, long born) {
			this.entityId = entityId;
			this.danger = danger;
			this.display = display;
			this.key = new DisplayKey(display);
			this.until = until;
			this.born = born;
		}
	}

	private static final Map<Long, Mark> MARKS = new HashMap<>();

	private static long key(int entityId, boolean danger) {
		return ((long) entityId << 1) | (danger ? 1L : 0L);
	}

	/** A swing arrived: a perilous one gets its 危 for its windup (a feint or a rejection takes it away). */
	static void onSwing(DuelFx.SwingView swing, boolean fresh) {
		long k = key(swing.entityId, true);
		if (swing.has(DuelCodes.SWING_FEINT_CANCEL) || swing.has(DuelCodes.SWING_REJECTED)) {
			remove(k);
			return;
		}
		if (!swing.has(DuelCodes.SWING_PERILOUS)) {
			return;
		}
		double now = DuelLinks.serverTick(0.0F);
		long left = swing.charging() ? 60L : (long) Math.max(4.0D, swing.impact() - now + 3.0D);
		long until = DuelFx.ticks() + Math.min(80L, left);
		Mark existing = MARKS.get(k);
		if (existing != null) {
			existing.until = until;
			return;
		}
		Entity entity = DuelFx.entity(swing.entityId);
		if (entity == null || !near(entity, 48.0D)) {
			return;
		}
		Vec3 head = head(entity);
		Display.TextDisplay display = DuelDisplays.glyph(head, "危", DANGER_RGB, 0.55F);
		if (display != null) {
			MARKS.put(k, new Mark(swing.entityId, true, display, until, DuelFx.ticks()));
		}
		// the red glint of a perilous windup (Sekiro's warning flash)
		MetalFlash.add(head.subtract(0.0D, 0.6D, 0.0D), 0.9F, 0xFF3030, 5);
	}

	/** A fighter's flags changed: the 決 comes and goes with the deathblow mark. */
	static void onFighter(DuelFx.FighterView f, int oldFlags) {
		long k = key(f.entityId, false);
		boolean on = f.has(DuelCodes.FIGHTER_DEATHBLOW_MARK);
		if (!on) {
			remove(k);
			return;
		}
		if (MARKS.containsKey(k)) {
			MARKS.get(k).until = DuelFx.ticks() + 40L;
			return;
		}
		Entity entity = DuelFx.entity(f.entityId);
		if (entity == null || !mayStrike(f.entityId, entity)) {
			return;
		}
		Display.TextDisplay display = DuelDisplays.glyph(head(entity), "決", DEATHBLOW_RGB, 0.6F);
		if (display != null) {
			MARKS.put(k, new Mark(f.entityId, false, display, DuelFx.ticks() + 40L, DuelFx.ticks()));
			MetalFlash.add(head(entity).subtract(0.0D, 0.5D, 0.0D), 1.0F, DEATHBLOW_RGB, 5);
		}
	}

	/** The 決 is for whoever could strike: the opponent, or anyone within 8 b. */
	private static boolean mayStrike(int staggeredId, Entity entity) {
		int self = DuelFx.localId();
		DuelFx.SessionView s = DuelFx.sessionOf(staggeredId);
		if (s != null && s.partnerOf(staggeredId) == self) {
			return true;
		}
		return near(entity, 8.0D);
	}

	static void tick(Minecraft minecraft) {
		if (MARKS.isEmpty()) {
			return;
		}
		long now = DuelFx.ticks();
		for (Iterator<Mark> it = MARKS.values().iterator(); it.hasNext(); ) {
			Mark mark = it.next();
			Entity entity = DuelFx.entity(mark.entityId);
			if (now >= mark.until || entity == null || !entity.isAlive() || mark.display.isRemoved()) {
				DuelDisplays.remove(mark.display);
				it.remove();
				continue;
			}
			mark.key.move(head(entity));
			// a stamp-in (1.6 → 1.0 over 3 ticks), then a slow pulse; the danger mark beats faster as the impact nears
			long age = now - mark.born;
			float stamp = age < 3 ? 1.6F - 0.2F * age : 1.0F;
			float beat = mark.danger ? 0.08F * Mth.sin(age * 1.4F) : 0.05F * Mth.sin(age * 0.5F);
			float scale = (stamp + beat) * (mark.danger ? 0.55F : 0.6F) / 0.25F;
			mark.key.key(new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(scale, scale, scale), 1);
		}
	}

	/** The GATHER chevrons from the local duellist to their mark, and the glowing start marks. */
	static void render(DuelGeo frame) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		int self = minecraft.player.getId();
		for (DuelFx.SessionView s : DuelFx.sessions()) {
			if (s.phase != DuelCodes.PHASE_GATHER || !s.involves(self)) {
				continue;
			}
			Vec3 mark = self == s.aId ? s.markA() : s.markB();
			int rgb = self == s.aId ? s.aRgb : s.bRgb;
			Vec3 feet = minecraft.player.getPosition(frame.partial);
			Vec3 ground = new Vec3(mark.x, groundY(mark), mark.z);
			float pulse = 0.6F + 0.4F * Mth.sin((float) frame.time * 0.4F);
			if (frame.take(4)) {
				Vec3 x = new Vec3(1.0D, 0.0D, 0.0D);
				Vec3 z = new Vec3(0.0D, 0.0D, 1.0D);
				frame.plane(frame.shock, ground.add(0.0D, 0.04D, 0.0D), x, z, 0.9F, 0.9F, DuelGeo.argb(0.75F * pulse, DuelGeo.mix(rgb, 0xFFFFFF, 0.3F)),
						LightCoordsUtil.FULL_BRIGHT, true);
				frame.plane(frame.glow, ground.add(0.0D, 0.05D, 0.0D), x, z, 0.6F, 0.6F, DuelGeo.argb(0.5F * pulse, rgb), LightCoordsUtil.FULL_BRIGHT,
						true);
			}
			Vec3 path = new Vec3(ground.x - feet.x, 0.0D, ground.z - feet.z);
			double length = path.length();
			if (length < 1.5D) {
				continue;
			}
			Vec3 dir = path.scale(1.0D / length);
			Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
			int chevrons = (int) Math.min(8, Math.floor((length - 1.0D) / 1.2D));
			double flow = (frame.time * 0.08D) % 1.0D;
			if (chevrons <= 0 || !frame.take(chevrons)) {
				continue;
			}
			for (int i = 0; i < chevrons; i++) {
				double along = 1.0D + (i + flow) * 1.2D;
				if (along > length - 0.6D) {
					break;
				}
				Vec3 p = feet.add(dir.scale(along));
				p = new Vec3(p.x, groundY(p) + 0.04D, p.z);
				float fade = (float) Math.min(1.0D, Math.min(along - 1.0D, length - 0.6D - along) / 0.8D);
				// the arrow texture points to +u: u along the path, v across
				frame.plane(frame.arrow, p, dir, side, 0.35F, 0.35F, DuelGeo.argb(0.85F * Math.max(0.0F, fade), 0x201A1A), LightCoordsUtil.FULL_BRIGHT,
						false);
			}
		}
	}

	private static double groundY(Vec3 near) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? near.y : DuelRingVisual.ground(minecraft.level, near.x, near.y, near.z);
	}

	private static Vec3 head(Entity entity) {
		return entity.position().add(0.0D, entity.getBbHeight() + 0.75D, 0.0D);
	}

	private static boolean near(Entity entity, double range) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.player.distanceToSqr(entity) <= range * range;
	}

	private static void remove(long key) {
		Mark mark = MARKS.remove(key);
		if (mark != null) {
			DuelDisplays.remove(mark.display);
		}
	}

	static void clear() {
		for (Mark mark : MARKS.values()) {
			DuelDisplays.remove(mark.display);
		}
		MARKS.clear();
	}

	private DuelMarks() {
	}
}
