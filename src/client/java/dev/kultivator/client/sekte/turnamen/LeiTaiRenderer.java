package dev.kultivator.client.sekte.turnamen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.sekte.turnamen.TurnamenPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The Lingkar Lei Tai (DESIGN 36.2.3 "tempat"): while the sect tournament runs, a ring of light lies round the stage, drawn
 * only on the client from module T's {@code Lingkar} payload (no block, no entity). Its layers fake a heavy shader with
 * camera-relative additive quads in vanilla render types ({@code entityTranslucentEmissive}; never in the Iris shadow pass,
 * no post chain), collected from {@code LevelRenderEvents.COLLECT_SUBMITS} like the duel's visuals:
 * <ul>
 * <li>the ring band with its key-fret meander turning slowly, a soft radial glow outside it, brighter for a moment when a
 * bout begins;</li>
 * <li>while a bout is fought, a curtain of rising qi round the ring (Normal and Maximal);</li>
 * <li>spirit lamps floating above the ring's edge (Normal: 8, Maximal: 12);</li>
 * <li>when the champion is named, a pillar of light on the stage and plum petals falling (Normal: 20, Maximal: 40).</li>
 * </ul>
 * Colour: cinnabar for a season, gold for the Turnamen Elit and the final, jade for an inter-sect tournament. Minimal draws
 * the ring band only, 24 segments. Nothing is drawn beyond 96 blocks or after the ring is switched off; the idle cost is one
 * empty-map check per frame.
 */
public final class LeiTaiRenderer {

	private static final Identifier RING_TEX = Kultivator.id("textures/sekte/turnamen/leitai_ring.png");
	private static final Identifier CURTAIN_TEX = Kultivator.id("textures/sekte/turnamen/leitai_tirai.png");
	private static final Identifier LAMP_TEX = Kultivator.id("textures/sekte/turnamen/leitai_lentera.png");
	private static final Identifier PETAL_TEX = Kultivator.id("textures/sekte/turnamen/leitai_bunga.png");

	private static final double RANGE_SQ = 96.0D * 96.0D;
	private static final float BAND = 0.45F;
	private static final float HALO = 1.3F;
	private static final float CHAMPION_SECONDS = 8.0F;
	private static final float FLASH_SECONDS = 1.2F;

	static final int CINNABAR = 0xE5543A;
	static final int GOLD = 0xF2C14E;
	static final int JADE = 0x5ED6A0;

	/** One live ring (client state). */
	static final class Ring {
		Vec3 centre;
		float radius;
		int kind;
		int season;
		int bout;
		int flags;
		Component nameA = Component.empty();
		Component nameB = Component.empty();
		/** Client seconds when the current bout began and when the champion was named (-1 never). */
		double boutAt = -100.0D;
		double championAt = -100.0D;
		double since;

		boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	private static final Map<Long, Ring> RINGS = new HashMap<>();
	private static final Kuas RING = new Kuas(RenderTypes.entityTranslucentEmissive(RING_TEX));
	private static final Kuas CURTAIN = new Kuas(RenderTypes.entityTranslucentEmissive(CURTAIN_TEX));
	private static final Kuas LAMP = new Kuas(RenderTypes.entityTranslucentEmissive(LAMP_TEX));
	private static final Kuas PETAL = new Kuas(RenderTypes.entityTranslucentEmissive(PETAL_TEX));
	private static final Vector3f RIGHT = new Vector3f();
	private static final Vector3f UP = new Vector3f();
	private static boolean registered;

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(LeiTaiRenderer::collect);
	}

	/** The server's Lingkar: a new or changed ring, or (inactive) the end of one. */
	static void onLingkar(TurnamenPayloads.Lingkar payload) {
		long key = payload.centre().asLong();
		if (!payload.active()) {
			RINGS.remove(key);
			return;
		}
		double now = seconds();
		Ring ring = RINGS.computeIfAbsent(key, k -> {
			Ring fresh = new Ring();
			fresh.since = now;
			return fresh;
		});
		boolean newBout = payload.has(TurnamenPayloads.FLAG_FIGHTING) && (payload.bout() != ring.bout || !ring.has(TurnamenPayloads.FLAG_FIGHTING));
		boolean newChampion = payload.has(TurnamenPayloads.FLAG_CHAMPION) && !ring.has(TurnamenPayloads.FLAG_CHAMPION);
		ring.centre = Vec3.atBottomCenterOf(payload.centre());
		ring.radius = Math.max(3.0F, Math.min(12.0F, payload.radius()));
		ring.kind = payload.kind();
		ring.season = payload.season();
		ring.bout = payload.bout();
		ring.flags = payload.flags();
		ring.nameA = payload.nameA();
		ring.nameB = payload.nameB();
		if (newBout) {
			ring.boutAt = now;
		}
		if (newChampion) {
			ring.championAt = now;
		}
	}

	/** The ring nearest to {@code at} within {@code range} blocks, or null (the HUD banner reads it). */
	static Ring nearest(Vec3 at, double range) {
		Ring best = null;
		double bestSq = range * range;
		for (Ring ring : RINGS.values()) {
			double d = ring.centre.distanceToSqr(at);
			if (d <= bestSq) {
				bestSq = d;
				best = ring;
			}
		}
		return best;
	}

	static void clear() {
		RINGS.clear();
	}

	static double seconds() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0.0D : (minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0D;
	}

	// ================================================================== drawing

	private static void collect(LevelRenderContext context) {
		if (RINGS.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			Vec3 cam = camera.pos;
			camera.orientation.transform(1.0F, 0.0F, 0.0F, RIGHT);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, UP);
			RING.reset();
			CURTAIN.reset();
			LAMP.reset();
			PETAL.reset();
			double now = seconds();
			EffectQuality.Level quality = EffectQuality.level();
			for (Ring ring : RINGS.values()) {
				if (ring.centre.distanceToSqr(cam) > RANGE_SQ) {
					continue;
				}
				draw(ring, cam, now, quality);
			}
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			RING.submit(collector, poseStack);
			CURTAIN.submit(collector, poseStack);
			PETAL.submit(collector, poseStack);
			LAMP.submit(collector.order(1), poseStack);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Lingkar Lei Tai render failed", exception);
			RINGS.clear();
		}
	}

	private static int colour(Ring ring) {
		if (ring.kind == TurnamenPayloads.KIND_GUESTS) {
			return JADE;
		}
		return ring.kind == TurnamenPayloads.KIND_ELITE || ring.has(TurnamenPayloads.FLAG_FINAL) ? GOLD : CINNABAR;
	}

	private static void draw(Ring ring, Vec3 cam, double now, EffectQuality.Level quality) {
		int rgb = colour(ring);
		boolean minimal = quality == EffectQuality.Level.MINIMAL;
		boolean maximal = quality == EffectQuality.Level.MAXIMAL;
		int segments = minimal ? 24 : maximal ? 72 : 48;
		float fade = (float) Mth.clamp((now - ring.since) / 1.5D, 0.0D, 1.0D);
		float flash = (float) Mth.clamp(1.0D - (now - ring.boutAt) / FLASH_SECONDS, 0.0D, 1.0D);
		boolean fighting = ring.has(TurnamenPayloads.FLAG_FIGHTING);
		float pulse = 0.5F + 0.5F * Mth.sin((float) (now * (fighting ? 3.2D : 1.4D)));
		double cx = ring.centre.x - cam.x;
		double cy = ring.centre.y + 0.04D - cam.y;
		double cz = ring.centre.z - cam.z;
		float r0 = ring.radius;
		float r1 = r0 + BAND;
		float turn = (float) (now * 0.05D);
		float tiles = Math.max(4.0F, Math.round((float) (Math.PI * 2.0D * r0) / 2.0F));
		// 1. the band with its meander, turning slowly
		float bandAlpha = fade * (0.62F + 0.18F * pulse + 0.2F * flash);
		for (int i = 0; i < segments; i++) {
			float a0 = (float) (Math.PI * 2.0D * i / segments);
			float a1 = (float) (Math.PI * 2.0D * (i + 1) / segments);
			float u0 = (float) i / segments * tiles + turn;
			float u1 = (float) (i + 1) / segments * tiles + turn;
			flat(RING, cx, cy, cz, a0, a1, r0, r1, u0, u1, 0.0F, 1.0F, ARGB.color(alpha(bandAlpha), rgb), ARGB.color(alpha(bandAlpha), rgb));
		}
		if (minimal) {
			return;
		}
		// 2. a soft radial glow outside the band (the curtain texture laid flat: bright at the band, streaks outward)
		float haloAlpha = fade * (0.28F + 0.22F * flash + (fighting ? 0.12F * pulse : 0.0F));
		for (int i = 0; i < segments; i++) {
			float a0 = (float) (Math.PI * 2.0D * i / segments);
			float a1 = (float) (Math.PI * 2.0D * (i + 1) / segments);
			float u0 = (float) i / segments * tiles * 0.5F - turn;
			float u1 = (float) (i + 1) / segments * tiles * 0.5F - turn;
			flat(CURTAIN, cx, cy - 0.01D, cz, a0, a1, r1, r1 + HALO, u0, u1, 1.0F, 0.0F, ARGB.color(alpha(haloAlpha), rgb), ARGB.color(0, rgb));
		}
		// 3. the rising qi curtain of a bout
		if (fighting || flash > 0.0F) {
			float height = maximal ? 2.0F : 1.4F;
			float curtainAlpha = fade * Math.max(fighting ? 0.32F + 0.16F * pulse : 0.0F, 0.6F * flash);
			float rise = (float) (now * 0.35D);
			for (int i = 0; i < segments; i++) {
				float a0 = (float) (Math.PI * 2.0D * i / segments);
				float a1 = (float) (Math.PI * 2.0D * (i + 1) / segments);
				float u0 = (float) i / segments * tiles + rise;
				float u1 = (float) (i + 1) / segments * tiles + rise;
				wall(CURTAIN, cx, cy, cz, a0, a1, r1 - 0.1F, height, u0, u1, ARGB.color(alpha(curtainAlpha), rgb));
			}
		}
		// 4. spirit lamps
		int lamps = maximal ? 12 : 8;
		for (int i = 0; i < lamps; i++) {
			double angle = Math.PI * 2.0D * i / lamps + now * 0.12D;
			double bob = Math.sin(now * 1.6D + i * 1.3D) * 0.12D;
			double lx = cx + Math.cos(angle) * (r1 + 0.6F);
			double lz = cz + Math.sin(angle) * (r1 + 0.6F);
			float size = 0.32F + 0.06F * (float) Math.sin(now * 2.3D + i);
			billboard(LAMP, lx, cy + 1.7D + bob, lz, size, ARGB.color(alpha(fade * (0.85F + 0.15F * pulse)), rgb == JADE ? JADE : GOLD));
		}
		// 5. the champion: a pillar of light on the stage and falling plum petals
		double champion = now - ring.championAt;
		if (champion >= 0.0D && champion <= CHAMPION_SECONDS) {
			float t = (float) (champion / CHAMPION_SECONDS);
			float pillar = Mth.clamp(Math.min(t * 6.0F, (1.0F - t) * 2.5F), 0.0F, 1.0F);
			int pillarSegments = maximal ? 16 : 10;
			for (int i = 0; i < pillarSegments; i++) {
				float a0 = (float) (Math.PI * 2.0D * i / pillarSegments);
				float a1 = (float) (Math.PI * 2.0D * (i + 1) / pillarSegments);
				wall(CURTAIN, cx, cy, cz, a0, a1, 0.9F, 7.0F, i, i + 1.0F, ARGB.color(alpha(0.55F * pillar), GOLD));
			}
			int petals = maximal ? 40 : 20;
			Random random = new Random(ring.season * 7919L + ring.bout);
			for (int i = 0; i < petals; i++) {
				double start = random.nextDouble() * 3.0D;
				double fall = champion - start;
				if (fall < 0.0D) {
					continue;
				}
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double dist = Math.sqrt(random.nextDouble()) * (r0 + 1.0D);
				double y = 5.5D - fall * (0.9D + random.nextDouble() * 0.5D);
				if (y < 0.05D) {
					continue;
				}
				double sway = Math.sin(fall * 2.0D + i) * 0.4D;
				double px = cx + Math.cos(angle) * dist + sway;
				double pz = cz + Math.sin(angle) * dist + Math.cos(fall * 1.7D + i) * 0.3D;
				int petal = i % 3 == 0 ? 0xFFFFFF : i % 3 == 1 ? 0xFFC2D2 : 0xF7A6BC;
				billboard(PETAL, px, cy + y, pz, 0.11F, ARGB.color(alpha(Math.min(1.0F, (float) y)), petal));
			}
		}
	}

	private static int alpha(float a) {
		return Mth.clamp(Math.round(a * 255.0F), 0, 255);
	}

	/** One flat quad of an annulus between angles a0..a1 and radii ra..rb (v ra→va, rb→vb). */
	private static void flat(Kuas kuas, double cx, double cy, double cz, float a0, float a1, float ra, float rb, float u0, float u1, float va,
			float vb, int colourA, int colourB) {
		float c0 = Mth.cos(a0);
		float s0 = Mth.sin(a0);
		float c1 = Mth.cos(a1);
		float s1 = Mth.sin(a1);
		float x = (float) cx;
		float y = (float) cy;
		float z = (float) cz;
		kuas.vertex(x + c0 * ra, y, z + s0 * ra, u0, va, colourA, 0.0F, 1.0F, 0.0F);
		kuas.vertex(x + c0 * rb, y, z + s0 * rb, u0, vb, colourB, 0.0F, 1.0F, 0.0F);
		kuas.vertex(x + c1 * rb, y, z + s1 * rb, u1, vb, colourB, 0.0F, 1.0F, 0.0F);
		kuas.vertex(x + c1 * ra, y, z + s1 * ra, u1, va, colourA, 0.0F, 1.0F, 0.0F);
	}

	/** One upright quad of a cylinder wall of radius r and height h (v 1 at the foot, 0 at the top), seen from both sides. */
	private static void wall(Kuas kuas, double cx, double cy, double cz, float a0, float a1, float r, float h, float u0, float u1, int colour) {
		float c0 = Mth.cos(a0);
		float s0 = Mth.sin(a0);
		float c1 = Mth.cos(a1);
		float s1 = Mth.sin(a1);
		float x0 = (float) cx + c0 * r;
		float z0 = (float) cz + s0 * r;
		float x1 = (float) cx + c1 * r;
		float z1 = (float) cz + s1 * r;
		float y0 = (float) cy;
		float y1 = (float) cy + h;
		int top = ARGB.color(0, colour & 0xFFFFFF);
		kuas.vertex(x0, y0, z0, u0, 1.0F, colour, c0, 0.0F, s0);
		kuas.vertex(x1, y0, z1, u1, 1.0F, colour, c1, 0.0F, s1);
		kuas.vertex(x1, y1, z1, u1, 0.0F, top, c1, 0.0F, s1);
		kuas.vertex(x0, y1, z0, u0, 0.0F, top, c0, 0.0F, s0);
		kuas.vertex(x0, y1, z0, u0, 0.0F, top, -c0, 0.0F, -s0);
		kuas.vertex(x1, y1, z1, u1, 0.0F, top, -c1, 0.0F, -s1);
		kuas.vertex(x1, y0, z1, u1, 1.0F, colour, -c1, 0.0F, -s1);
		kuas.vertex(x0, y0, z0, u0, 1.0F, colour, -c0, 0.0F, -s0);
	}

	/** A camera-facing square of half size {@code half} at a camera-relative point. */
	private static void billboard(Kuas kuas, double x, double y, double z, float half, int colour) {
		float rx = RIGHT.x * half;
		float ry = RIGHT.y * half;
		float rz = RIGHT.z * half;
		float ux = UP.x * half;
		float uy = UP.y * half;
		float uz = UP.z * half;
		float px = (float) x;
		float py = (float) y;
		float pz = (float) z;
		kuas.vertex(px - rx - ux, py - ry - uy, pz - rz - uz, 0.0F, 1.0F, colour, 0.0F, 1.0F, 0.0F);
		kuas.vertex(px + rx - ux, py + ry - uy, pz + rz - uz, 1.0F, 1.0F, colour, 0.0F, 1.0F, 0.0F);
		kuas.vertex(px + rx + ux, py + ry + uy, pz + rz + uz, 1.0F, 0.0F, colour, 0.0F, 1.0F, 0.0F);
		kuas.vertex(px - rx + ux, py - ry + uy, pz - rz + uz, 0.0F, 0.0F, colour, 0.0F, 1.0F, 0.0F);
	}

	/** The vertices of one render type for this frame, submitted once (camera-relative, full bright). */
	private static final class Kuas {
		private static final int STRIDE = 8;
		private final RenderType type;
		private float[] data = new float[STRIDE * 256];
		private int[] colours = new int[256];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Kuas(RenderType type) {
			this.type = type;
		}

		void reset() {
			count = 0;
		}

		void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
			if (count >= colours.length) {
				int size = colours.length * 2;
				float[] more = new float[size * STRIDE];
				System.arraycopy(data, 0, more, 0, data.length);
				data = more;
				int[] moreColours = new int[size];
				System.arraycopy(colours, 0, moreColours, 0, colours.length);
				colours = moreColours;
			}
			int o = count * STRIDE;
			data[o] = x;
			data[o + 1] = y;
			data[o + 2] = z;
			data[o + 3] = u;
			data[o + 4] = v;
			data[o + 5] = nx;
			data[o + 6] = ny;
			data[o + 7] = nz;
			colours[count] = argb;
			count++;
		}

		void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
			if (count >= 4) {
				collector.submitCustomGeometry(poseStack, type, writer);
			}
		}

		private void write(PoseStack.Pose pose, VertexConsumer buffer) {
			int vertices = count / 4 * 4;
			for (int i = 0; i < vertices; i++) {
				int o = i * STRIDE;
				buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
						.setColor(colours[i])
						.setUv(data[o + 3], data[o + 4])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
			}
		}
	}

	private LeiTaiRenderer() {
	}
}
