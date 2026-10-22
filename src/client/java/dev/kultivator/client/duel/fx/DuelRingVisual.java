package dev.kultivator.client.duel.fx;

import dev.kultivator.Kultivator;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.DisplayKey;
import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The duel ring, all on the client and nothing in the world (DESIGN 35.2.3, 35.4.11): posts ({@code ItemDisplay} of
 * module A's {@code kultivator:duel_post} model, posed through {@link DisplayKey}, 4 / 8 / 8 by quality, each standing on
 * the ground found by a read-only {@code level.clip} down 4 b), red ropes between them ({@link DuelRopeRenderer}: sagging
 * 0.25 b, trembling on big impacts, glowing red while a duellist stands outside), the ink circle on the ground following
 * the terrain, the red exit line at radius + 1.5 that pulses while someone is outside, and talismans: white paper scraps
 * fluttering off the posts, and at Maximal eight paper charms hanging from the ropes and swaying. Seen by everyone within
 * 64 b; built at GATHER, gone 2 s after the end; no server entity, nothing saved in any chunk. {@code uji duel
 * gelanggang} shows one without a session.
 */
public final class DuelRingVisual {

	/** One ring in the world. */
	static final class Ring {
		final int key;
		Vec3 center;
		int radius;
		/** Post feet and rope attachment points (world). */
		final List<Vec3> posts = new ArrayList<>();
		final List<Display.ItemDisplay> displays = new ArrayList<>();
		final List<Display.ItemDisplay> charms = new ArrayList<>();
		final List<DisplayKey> charmKeys = new ArrayList<>();
		/** The ink circle: ground heights of its vertices. */
		double[] circleY = new double[0];
		boolean outside;
		float tremble;
		double trembleAt = -1000.0D;
		float alpha;
		boolean ending;
		long previewUntil = -1L;
		int built;
		int light = LightCoordsUtil.FULL_BRIGHT;

		Ring(int key, Vec3 center, int radius) {
			this.key = key;
			this.center = center;
			this.radius = radius;
		}

		double exitRadius() {
			return radius + 1.5D;
		}
	}

	static final int SEGMENTS = 48;
	static final float POST_SCALE = 1.6F;
	/** Ropes hang this high above the post feet (the post is {@link #POST_SCALE} tall). */
	static final double ROPE_HEIGHT = 1.2D;

	private static final Map<Integer, Ring> RINGS = new HashMap<>();
	private static @Nullable ItemStack postStack;
	private static @Nullable ItemStack charmStack;
	private static long ticks;

	static void tick(Minecraft minecraft) {
		ticks++;
		DuelDisplays.tick();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		Vec3 eye = minecraft.player.position();
		// sessions in range get a ring
		for (DuelFx.SessionView s : DuelFx.sessions()) {
			boolean show = s.live() && s.phase >= DuelCodes.PHASE_GATHER && eye.distanceToSqr(s.center) < 64.0D * 64.0D;
			Ring ring = RINGS.get(s.id);
			if (show) {
				if (ring == null) {
					ring = new Ring(s.id, s.center, s.radius);
					RINGS.put(s.id, ring);
				}
				ring.ending = false;
				if (ring.center.distanceToSqr(s.center) > 0.25D || ring.radius != s.radius) {
					// the server moved the ring (the sect stele): rebuild
					removeDisplays(ring);
					ring.center = s.center;
					ring.radius = s.radius;
					ring.posts.clear();
					ring.circleY = new double[0];
					ring.built = 0;
				}
				ring.outside = s.has(DuelCodes.SESSION_OUT_A) || s.has(DuelCodes.SESSION_OUT_B);
			} else if (ring != null) {
				ring.ending = true;
			}
		}
		for (Iterator<Ring> it = RINGS.values().iterator(); it.hasNext(); ) {
			Ring ring = it.next();
			boolean session = ring.previewUntil < 0L;
			if (session && !hasLiveSession(ring.key)) {
				ring.ending = true;
			}
			if (!session && ticks >= ring.previewUntil) {
				ring.ending = true;
			}
			ring.alpha = ring.ending ? Math.max(0.0F, ring.alpha - 0.05F) : Math.min(1.0F, ring.alpha + 0.05F);
			if (ring.ending && ring.alpha <= 0.0F) {
				removeDisplays(ring);
				it.remove();
				continue;
			}
			build(minecraft.level, ring);
			animate(minecraft.level, ring);
		}
	}

	private static boolean hasLiveSession(int key) {
		for (DuelFx.SessionView s : DuelFx.sessions()) {
			if (s.id == key && s.live()) {
				return true;
			}
		}
		return false;
	}

	/** {@code uji duel gelanggang [radius]}: a ring at {@code center} for {@code ticks}. */
	static void preview(Vec3 center, int radius, int life) {
		int key = Integer.MIN_VALUE + 7;
		Ring old = RINGS.remove(key);
		if (old != null) {
			removeDisplays(old);
		}
		Ring ring = new Ring(key, center, Math.max(3, Math.min(16, radius)));
		ring.previewUntil = ticks + Math.max(40, life);
		RINGS.put(key, ring);
	}

	/** A big impact near a ring: its ropes tremble (amplitude in blocks, decaying over 10 ticks). */
	static void tremble(Vec3 at, float amplitude) {
		for (Ring ring : RINGS.values()) {
			if (ring.center.distanceToSqr(at) < (ring.radius + 6.0D) * (ring.radius + 6.0D)) {
				double now = DuelTime.now();
				float left = currentTremble(ring, now);
				if (amplitude >= left) {
					ring.tremble = amplitude;
					ring.trembleAt = now;
				}
			}
		}
	}

	static void onClash(DuelFx.ClashEvent e) {
		switch (e.outcome()) {
			case DuelCodes.GUARD_BREAK, DuelCodes.POSTURE_BREAK, DuelCodes.DEATHBLOW, DuelCodes.BIND_MENANG_A, DuelCodes.BIND_MENANG_B,
					DuelCodes.BIND_LEPAS -> tremble(e.anchor(), 0.15F);
			case DuelCodes.PERFECT, DuelCodes.CLASH_MENANG -> tremble(e.anchor(), 0.08F);
			default -> {
			}
		}
	}

	static float currentTremble(Ring ring, double now) {
		double age = now - ring.trembleAt;
		return age < 0.0D || age > 10.0D ? 0.0F : ring.tremble * (float) (1.0D - age / 10.0D);
	}

	/** Builds the posts (2 per tick) and the circle heights of a ring. */
	private static void build(ClientLevel level, Ring ring) {
		int count = DuelFxSettings.pick(4, 8, 8);
		if (ring.posts.isEmpty()) {
			for (int i = 0; i < count; i++) {
				double angle = Math.PI * 2.0D * (i + 0.5D) / count;
				double x = ring.center.x + Math.cos(angle) * ring.radius;
				double z = ring.center.z + Math.sin(angle) * ring.radius;
				ring.posts.add(new Vec3(x, ground(level, x, ring.center.y, z), z));
			}
			ring.circleY = new double[SEGMENTS];
			for (int i = 0; i < SEGMENTS; i++) {
				double angle = Math.PI * 2.0D * i / SEGMENTS;
				ring.circleY[i] = ground(level, ring.center.x + Math.cos(angle) * ring.radius, ring.center.y, ring.center.z + Math.sin(angle) * ring.radius);
			}
			ring.light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(ring.center.add(0.0D, 1.0D, 0.0D)));
		}
		if (ring.ending) {
			return;
		}
		int made = 0;
		while (ring.built < ring.posts.size() && made < 2 && DuelDisplays.canCreate()) {
			Vec3 foot = ring.posts.get(ring.built);
			Display.ItemDisplay display = DuelDisplays.item(foot, post(), false, 2.0F);
			ring.built++;
			made++;
			if (display == null) {
				continue;
			}
			// face the centre; the model's 16 px cube stands on the ground at POST_SCALE blocks tall
			float yaw = (float) Math.atan2(ring.center.x - foot.x, ring.center.z - foot.z);
			DisplayKey.place(display, new Vector3f(0.0F, POST_SCALE * 0.5F, 0.0F), new Quaternionf().rotationY(yaw),
					new Vector3f(POST_SCALE, POST_SCALE, POST_SCALE));
			ring.displays.add(display);
		}
		if (ring.built >= ring.posts.size() && DuelFxSettings.tier() >= 2 && ring.charms.size() < ring.posts.size() && DuelDisplays.canCreate()) {
			int i = ring.charms.size();
			Vec3 mid = DuelRopeRenderer.ropePoint(ring, i, 0.5D, DuelTime.now(), 0.0F);
			Display.ItemDisplay charm = DuelDisplays.item(mid, charm(), false, 1.0F);
			if (charm != null) {
				DisplayKey key = new DisplayKey(charm);
				key.snap(new Vector3f(0.0F, -0.18F, 0.0F), new Quaternionf(), new Vector3f(0.32F, 0.42F, 0.32F));
				ring.charms.add(charm);
				ring.charmKeys.add(key);
			} else {
				ring.charms.add(null);
				ring.charmKeys.add(null);
			}
		}
	}

	/** The charms sway and follow the trembling ropes; loose paper flutters off the posts. */
	private static void animate(ClientLevel level, Ring ring) {
		double now = DuelTime.now();
		float tremble = currentTremble(ring, now);
		for (int i = 0; i < ring.charms.size(); i++) {
			Display.ItemDisplay charm = ring.charms.get(i);
			DisplayKey key = ring.charmKeys.get(i);
			if (charm == null || key == null) {
				continue;
			}
			Vec3 at = DuelRopeRenderer.ropePoint(ring, i, 0.5D, now + 1.0D, tremble);
			key.move(at);
			float sway = (float) (Math.sin(now * 0.12D + i * 1.7D) * 0.25D + tremble * 2.0F * Math.sin(now * 1.9D));
			Vec3 a = ring.posts.get(i);
			Vec3 b = ring.posts.get((i + 1) % ring.posts.size());
			float yaw = (float) Math.atan2(b.x - a.x, b.z - a.z);
			key.key(new Vector3f(0.0F, -0.18F, 0.0F), new Quaternionf().rotationY(yaw).rotateX(sway), new Vector3f(0.32F, 0.42F, 0.32F), 1);
		}
		if (ring.ending || ring.posts.isEmpty() || DuelFxSettings.tier() == 0 || (ticks + ring.key) % 9 != 0
				|| FxBudget.take(2, FxBudget.Priority.EXTRA) < 2) {
			return;
		}
		// a paper scrap blown off one of the posts
		int i = (int) Math.floorMod(ticks / 9 + ring.key, (long) ring.posts.size());
		Vec3 top = ring.posts.get(i).add(0.0D, ROPE_HEIGHT + 0.2D, 0.0D);
		double drift = 0.03D + (i % 3) * 0.01D;
		level.addParticle(Qi.petal(0xF8F4EA, 0.16F, 50), top.x, top.y, top.z, Math.cos(i) * drift, 0.01D, Math.sin(i) * drift);
	}

	/** The ground height under (x, z) near {@code y} (read only), or {@code y}. */
	static double ground(ClientLevel level, double x, double y, double z) {
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 from = new Vec3(x, y + 2.5D, z);
		Vec3 to = new Vec3(x, y - 4.0D, z);
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, minecraft.player));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : y;
	}

	// ================================================================ render

	static void render(DuelGeo frame) {
		if (RINGS.isEmpty()) {
			return;
		}
		for (Ring ring : RINGS.values()) {
			if (ring.alpha <= 0.0F || ring.circleY.length != SEGMENTS || frame.dist2(ring.center) > 80.0D * 80.0D) {
				continue;
			}
			drawCircle(frame, ring);
			DuelRopeRenderer.render(frame, ring);
		}
	}

	private static void drawCircle(DuelGeo frame, Ring ring) {
		if (!frame.take(SEGMENTS * 2)) {
			return;
		}
		double r = ring.radius;
		double exit = ring.exitRadius();
		float pulse = 0.5F + 0.5F * Mth.sin((float) frame.time * 0.5F);
		int ink = DuelGeo.argb(0.8F * ring.alpha, 0x1A1418);
		int red = DuelGeo.argb((ring.outside ? 0.45F + 0.5F * pulse : 0.14F) * ring.alpha, 0xE02A2A);
		for (int i = 0; i < SEGMENTS; i++) {
			int j = (i + 1) % SEGMENTS;
			double a0 = Math.PI * 2.0D * i / SEGMENTS;
			double a1 = Math.PI * 2.0D * j / SEGMENTS;
			double y0 = ring.circleY[i] + 0.02D;
			double y1 = ring.circleY[j] + 0.02D;
			float u0 = i * 0.5F;
			float u1 = (i + 1) * 0.5F;
			// the ink stroke of the ring line (0.45 b wide)
			Vec3 in0 = new Vec3(ring.center.x + Math.cos(a0) * (r - 0.22D), y0, ring.center.z + Math.sin(a0) * (r - 0.22D));
			Vec3 out0 = new Vec3(ring.center.x + Math.cos(a0) * (r + 0.22D), y0, ring.center.z + Math.sin(a0) * (r + 0.22D));
			Vec3 in1 = new Vec3(ring.center.x + Math.cos(a1) * (r - 0.22D), y1, ring.center.z + Math.sin(a1) * (r - 0.22D));
			Vec3 out1 = new Vec3(ring.center.x + Math.cos(a1) * (r + 0.22D), y1, ring.center.z + Math.sin(a1) * (r + 0.22D));
			frame.quad(frame.ink, in0, out0, out1, in1, u0, 0.0F, u1, 1.0F, ink, ink, ink, ink, ring.light);
			// the exit line: a thin red dashed stroke, every other segment
			if ((i & 1) == 0) {
				Vec3 e0 = new Vec3(ring.center.x + Math.cos(a0) * (exit - 0.08D), y0 + 0.005D, ring.center.z + Math.sin(a0) * (exit - 0.08D));
				Vec3 f0 = new Vec3(ring.center.x + Math.cos(a0) * (exit + 0.08D), y0 + 0.005D, ring.center.z + Math.sin(a0) * (exit + 0.08D));
				Vec3 e1 = new Vec3(ring.center.x + Math.cos(a1) * (exit - 0.08D), y1 + 0.005D, ring.center.z + Math.sin(a1) * (exit - 0.08D));
				Vec3 f1 = new Vec3(ring.center.x + Math.cos(a1) * (exit + 0.08D), y1 + 0.005D, ring.center.z + Math.sin(a1) * (exit + 0.08D));
				frame.quad(frame.inkGlow, e0, f0, f1, e1, u0, 0.0F, u1, 1.0F, red, red, red, red, LightCoordsUtil.FULL_BRIGHT);
			}
		}
	}

	// ================================================================ items (created lazily, never in a static initializer)

	/** The post model of module A ({@code items/duel_post.json}), or a crimson fence when that is missing. */
	private static ItemStack post() {
		if (postStack == null) {
			boolean known = Minecraft.getInstance().getResourceManager().getResource(Kultivator.id("items/duel_post.json")).isPresent();
			if (known) {
				ItemStack stack = new ItemStack(Items.PAPER);
				stack.set(DataComponents.ITEM_MODEL, Kultivator.id("duel_post"));
				postStack = stack;
			} else {
				postStack = new ItemStack(Items.CRIMSON_FENCE);
			}
		}
		return postStack;
	}

	private static ItemStack charm() {
		if (charmStack == null) {
			charmStack = new ItemStack(Items.PAPER);
		}
		return charmStack;
	}

	private static void removeDisplays(Ring ring) {
		for (Display.ItemDisplay display : ring.displays) {
			DuelDisplays.remove(display);
		}
		for (Display.ItemDisplay charm : ring.charms) {
			DuelDisplays.remove(charm);
		}
		ring.displays.clear();
		ring.charms.clear();
		ring.charmKeys.clear();
		ring.built = 0;
	}

	static boolean idle() {
		return RINGS.isEmpty();
	}

	static void clear() {
		for (Ring ring : RINGS.values()) {
			removeDisplays(ring);
		}
		RINGS.clear();
		DuelDisplays.clear();
	}

	private DuelRingVisual() {
	}
}
