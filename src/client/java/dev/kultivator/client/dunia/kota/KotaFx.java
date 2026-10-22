package dev.kultivator.client.dunia.kota;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KotaNet;
import dev.kultivator.dunia.kota.KultivatorNpc;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The world visuals of module K (DESIGN 34.5.1-34.5.4), all client side, all budgeted (rule V1/V2: layered additive
 * quads, glow textures and the {@code kultivator:qi} particle; no post chain, no core shader, Sodium / Iris safe, drawn
 * from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} and skipped in a shader pack's shadow pass):
 * <ul>
 * <li>lantern glow sprites on the {@code kota/lentera} rows: 16 / 48 / 96 quads per frame, flickering, full within 32
 * blocks, faded to 96, none beyond;</li>
 * <li>the stage light shafts of a running auction: 1 / 2 / 3 pillars through {@link EpicBudget#takePillar};</li>
 * <li>the teahouse pond's qi motes: 4 / 10 / 20 per second within 16 blocks;</li>
 * <li>the Immortal City's cloud skirts (24 / 64 / 128 quads within 160 blocks) and floating petals (4 / 10 / 20 per s);</li>
 * <li>a rival group's sword descent (a falling streak and a flash), a discussion's qi swirl (8 / 16 / 32 motes), the
 * town's name banner, and a soft aura for wanderers, rivals, guards and immortals (motes within 24 blocks, at most 6
 * NPCs).</li>
 * </ul>
 * Under a shader pack the quad counts drop to {@code EpicBudget.PACK_QUADS}.
 */
final class KotaFx {

	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/dunia/kota/lentera_glow.png"));
	private static final RenderType SHAFT = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/dunia/kota/cahaya_panggung.png"));
	private static final RenderType CLOUD = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/dunia/kota/awan_kota.png"));
	private static final RenderType STREAK = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/dunia/kota/jejak_pedang.png"));
	private static final int[] LANTERN_QUADS = {16, 48, 96};
	private static final int[] CLOUD_QUADS = {24, 64, 128};
	private static final int[] PARTICLES = {4, 10, 20};

	/** A point set the server sent (kept for its life, replaced by the same key). */
	private static final class Points {
		final int kind;
		final Vec3 base;
		final int a;
		final int[] offsets;
		long until;

		Points(int kind, Vec3 base, int a, int[] offsets, long until) {
			this.kind = kind;
			this.base = base;
			this.a = a;
			this.offsets = offsets;
			this.until = until;
		}

		int count() {
			return offsets.length / 3;
		}

		Vec3 at(int i) {
			return base.add(offsets[i * 3] + 0.5D, offsets[i * 3 + 1] + 0.5D, offsets[i * 3 + 2] + 0.5D);
		}
	}

	/** A one-shot: the sword descent. */
	private static final class Descent {
		final Vec3 at;
		final int rgb;
		int age;

		Descent(Vec3 at, int rgb) {
			this.at = at;
			this.rgb = rgb;
		}
	}

	private static final Map<String, Points> POINTS = new LinkedHashMap<>();
	private static final List<Descent> DESCENTS = new ArrayList<>();
	private static final Sink GLOW_SINK = new Sink(GLOW);
	private static final Sink SHAFT_SINK = new Sink(SHAFT);
	private static final Sink CLOUD_SINK = new Sink(CLOUD);
	private static final Sink STREAK_SINK = new Sink(STREAK);
	private static long ticks;
	private static boolean registered;

	static void init() {
		if (!registered) {
			registered = true;
			LevelRenderEvents.COLLECT_SUBMITS.register(KotaFx::render);
		}
	}

	static void clear() {
		POINTS.clear();
		DESCENTS.clear();
	}

	// ================================================================== payloads

	static void onFx(KotaNet.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		Vec3 at = new Vec3(fx.x(), fx.y(), fx.z());
		switch (fx.kind()) {
			case KotaNet.FX_LANTERNS, KotaNet.FX_STAGE, KotaNet.FX_POND, KotaNet.FX_CLOUDS, KotaNet.FX_PETALS -> {
				String key = fx.kind() + ":" + fx.b();
				POINTS.put(key, new Points(fx.kind(), at, fx.a(), fx.points(), ticks + Math.max(20, fx.life())));
			}
			case KotaNet.FX_DESCENT -> {
				if (DESCENTS.size() < 8) {
					DESCENTS.add(new Descent(at, fx.a()));
				}
			}
			case KotaNet.FX_SWIRL -> swirl(level, at, fx.a());
			case KotaNet.FX_BANNER -> {
				long town = ((long) (fx.points().length > 0 ? fx.points()[0] : 0) << 32) | (fx.b() & 0xFFFFFFFFL);
				DuniaScenes.banner(fx.a() == 1 ? KotaNames.immortalCity(town) : KotaNames.town(town));
			}
			default -> {
			}
		}
	}

	private static void swirl(ClientLevel level, Vec3 at, int rgb) {
		if (!Qi.on()) {
			return;
		}
		int n = EffectQuality.scale(16);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			double r = 0.6D + i * 0.04D;
			level.addParticle(Qi.mote(rgb, 0.12F, 30), at.x + Math.cos(angle) * r, at.y + i * 0.03D, at.z + Math.sin(angle) * r,
					-Math.sin(angle) * 0.06D, 0.03D, Math.cos(angle) * 0.06D);
		}
	}

	// ================================================================== tick: particles and auras

	static void tick(Minecraft minecraft) {
		ticks++;
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		for (Iterator<Points> it = POINTS.values().iterator(); it.hasNext(); ) {
			if (it.next().until < ticks) {
				it.remove();
			}
		}
		for (Iterator<Descent> it = DESCENTS.iterator(); it.hasNext(); ) {
			Descent descent = it.next();
			descent.age++;
			if (descent.age == 20 && Qi.on()) {
				for (int i = 0; i < EffectQuality.scale(20); i++) {
					double a = i * 0.6D;
					level.addParticle(Qi.spark(descent.rgb, 0.1F, 14), descent.at.x, descent.at.y + 0.2D, descent.at.z, Math.cos(a) * 0.35D, 0.05D,
							Math.sin(a) * 0.35D);
				}
				level.addParticle(Qi.groundRing(descent.rgb, 0.5F, 4.0F, 16), descent.at.x, descent.at.y + 0.05D, descent.at.z, 0.0D, 0.0D, 0.0D);
			}
			if (descent.age > 40) {
				it.remove();
			}
		}
		if (!Qi.on()) {
			return;
		}
		Vec3 eye = minecraft.player.getEyePosition();
		RandomSource random = level.getRandom();
		int perSecond = PARTICLES[EffectQuality.level().ordinal()];
		for (Points points : POINTS.values()) {
			if (points.kind != KotaNet.FX_POND && points.kind != KotaNet.FX_PETALS || points.count() == 0) {
				continue;
			}
			double range = points.kind == KotaNet.FX_POND ? 16.0D : 32.0D;
			if (points.base.distanceToSqr(eye) > (range + 16.0D) * (range + 16.0D)) {
				continue;
			}
			if (random.nextInt(20) >= perSecond) {
				continue;
			}
			Vec3 at = points.at(random.nextInt(points.count())).add(random.nextGaussian() * 1.2D, 0.0D, random.nextGaussian() * 1.2D);
			if (points.kind == KotaNet.FX_POND) {
				level.addParticle(Qi.mote(0x9FE8D8, 0.09F, 50), at.x, at.y + 0.2D, at.z, 0.0D, 0.015D, 0.0D);
			} else {
				level.addParticle(Qi.petal(0xF4A8C0, 0.12F, 90), at.x, at.y + 2.0D + random.nextDouble() * 3.0D, at.z, random.nextGaussian() * 0.01D, -0.02D,
						random.nextGaussian() * 0.01D);
			}
		}
		if (ticks % 4L == 0L) {
			auras(level, eye, random);
		}
	}

	/**
	 * A soft cultivation aura: motes rising around NPCs that carry {@link KultivatorNpc#FLAG_AURA}; and the small visuals
	 * of their body language (detail pass): qi drawn in by the seated and meditating, sparks under the smith's hammer,
	 * steam over the tea master's pot, a glint at the bowing hands. At most six of each kind per 4 ticks, scaled by
	 * {@code EffectQuality}, within 24 blocks.
	 */
	private static void auras(ClientLevel level, Vec3 eye, RandomSource random) {
		int shown = 0;
		int acts = 0;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(eye, eye).inflate(24.0D), KultivatorNpc::isAlive)) {
			if (acts < 6 && npc.distanceToSqr(eye) < 16.0D * 16.0D && body(level, npc, random)) {
				acts++;
			}
			if (!npc.hasFlag(KultivatorNpc.FLAG_AURA) || shown++ >= 6) {
				continue;
			}
			if (!EffectQuality.chance(random, 0.6F)) {
				continue;
			}
			int rgb = npc.dao() == null ? npc.robe() : npc.dao().color();
			if (npc.role().immortal()) {
				rgb = 0xFFF0C0;
			}
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 0.45D + random.nextDouble() * 0.2D;
			level.addParticle(Qi.mote(rgb, 0.07F + npc.realm() * 0.006F, 26), npc.getX() + Math.cos(angle) * r, npc.getY() + 0.2D + random.nextDouble() * 0.6D,
					npc.getZ() + Math.sin(angle) * r, 0.0D, 0.03D + npc.realm() * 0.003D, 0.0D);
		}
	}

	/** One particle of an NPC's body language this pass (true when one was made). */
	private static boolean body(ClientLevel level, KultivatorNpc npc, RandomSource random) {
		if (!EffectQuality.chance(random, 0.5F)) {
			return false;
		}
		float yaw = npc.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		if (npc.hasFlag(KultivatorNpc.FLAG_SITTING)) {
			// breathing in: a mote drifts from a little way off toward the dantian
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 1.1D + random.nextDouble() * 0.4D;
			double ox = Math.cos(angle) * r;
			double oz = Math.sin(angle) * r;
			level.addParticle(Qi.mote(npc.dao() == null ? 0xBFE8FF : npc.dao().color(), 0.06F, 24), npc.getX() + ox, npc.getY() + 0.6D,
					npc.getZ() + oz, -ox * 0.04D, 0.0D, -oz * 0.04D);
			return true;
		}
		if (npc.hasFlag(KultivatorNpc.FLAG_WORK)) {
			double hx = npc.getX() + fx * 0.6D;
			double hz = npc.getZ() + fz * 0.6D;
			switch (npc.role()) {
				case PANDAI_PUSAKA -> {
					for (int i = 0; i < 2; i++) {
						level.addParticle(Qi.spark(0xFFB04A, 0.06F, 8), hx, npc.getY() + 0.85D, hz, random.nextGaussian() * 0.08D, 0.06D + random.nextDouble() * 0.05D,
								random.nextGaussian() * 0.08D);
					}
					return true;
				}
				case PEMILIK_TEH -> {
					level.addParticle(Qi.mote(0xF4F4EE, 0.08F, 40), hx, npc.getY() + 1.15D, hz, 0.0D, 0.02D, 0.0D);
					return true;
				}
				default -> {
					return false;
				}
			}
		}
		if (npc.hasFlag(KultivatorNpc.FLAG_BOW) && random.nextInt(3) == 0) {
			level.addParticle(Qi.spark(0xFFE6A0, 0.05F, 10), npc.getX() + fx * 0.45D, npc.getY() + 1.15D, npc.getZ() + fz * 0.45D, 0.0D, 0.01D, 0.0D);
			return true;
		}
		return false;
	}

	// ================================================================== render

	private static void render(LevelRenderContext context) {
		if ((POINTS.isEmpty() && DESCENTS.isEmpty()) || ShaderCompat.shadowPass()) {
			return;
		}
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			float partial = context.levelState().worldPartialTicks;
			Vec3 cam = camera.pos;
			Vector3f right = new Vector3f();
			Vector3f up = new Vector3f();
			camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
			GLOW_SINK.reset();
			SHAFT_SINK.reset();
			CLOUD_SINK.reset();
			STREAK_SINK.reset();
			float pack = ShaderCompat.packOn() ? EpicBudget.PACK_QUADS : 1.0F;
			int lanterns = Math.round(LANTERN_QUADS[EffectQuality.level().ordinal()] * pack);
			int clouds = Math.round(CLOUD_QUADS[EffectQuality.level().ordinal()] * pack);
			float time = ticks + partial;
			for (Points points : POINTS.values()) {
				switch (points.kind) {
					case KotaNet.FX_LANTERNS -> lanterns = lanterns(points, cam, right, up, time, lanterns);
					case KotaNet.FX_STAGE -> {
						if (points.a == 1) {
							stage(points, cam, time);
						}
					}
					case KotaNet.FX_CLOUDS -> clouds = clouds(points, cam, time, clouds);
					default -> {
					}
				}
			}
			for (Descent descent : DESCENTS) {
				descent(descent, cam, right, up, partial);
			}
			PoseStack pose = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			CLOUD_SINK.submit(collector, pose);
			GLOW_SINK.submit(collector, pose);
			SHAFT_SINK.submit(collector, pose);
			STREAK_SINK.submit(collector, pose);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: town visuals turned off for this frame", exception);
		}
	}

	private static int lanterns(Points points, Vec3 cam, Vector3f right, Vector3f up, float time, int left) {
		for (int i = 0; i < points.count() && left > 0; i++) {
			Vec3 at = points.at(i);
			double d = Math.sqrt(at.distanceToSqr(cam));
			if (d > 96.0D) {
				continue;
			}
			float fade = d <= 32.0D ? 1.0F : (float) (1.0D - (d - 32.0D) / 64.0D);
			float flicker = 0.85F + 0.15F * (float) Math.sin(time * 0.31F + i * 1.7F) * (float) Math.sin(time * 0.13F + i);
			int color = argb(0.55F * fade * flicker, 0xFF8A40);
			billboard(GLOW_SINK, cam, at, right, up, 0.9F * flicker, color);
			left--;
			if (d <= 32.0D && left > 0) {
				billboard(GLOW_SINK, cam, at, right, up, 0.35F, argb(0.8F * flicker, 0xFFE0A0));
				left--;
			}
		}
		return left;
	}

	private static void stage(Points points, Vec3 cam, float time) {
		for (int i = 0; i < points.count(); i++) {
			if (!EpicBudget.takePillar()) {
				return;
			}
			Vec3 base = points.at(i).add(0.0D, -0.5D, 0.0D);
			if (base.distanceToSqr(cam) > 96.0D * 96.0D) {
				continue;
			}
			float sway = (float) Math.sin(time * 0.02F + i) * 0.3F;
			Vec3 top = base.add(sway, 9.0D, 0.0D);
			for (int k = 0; k < 2; k++) {
				double angle = time * 0.01D + k * Math.PI / 2.0D + i;
				Vec3 side = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
				int color = argb(0.32F, 0xFFE6B0);
				vertical(SHAFT_SINK, cam, base, top, side, 0.7F, 1.6F, color);
			}
		}
	}

	private static int clouds(Points points, Vec3 cam, float time, int left) {
		if (points.base.distanceToSqr(cam) > 160.0D * 160.0D) {
			return left;
		}
		for (int i = 0; i < points.count() && left > 0; i++) {
			Vec3 at = points.at(i).add(Math.sin(time * 0.004F + i) * 1.5D, Math.sin(time * 0.01F + i * 0.7F) * 0.3D, 0.0D);
			float half = 4.5F + (i % 3);
			int color = argb(0.55F, 0xFFF4F0);
			flat(CLOUD_SINK, cam, at, half, color, time * 0.002F + i);
			left--;
		}
		return left;
	}

	private static void descent(Descent descent, Vec3 cam, Vector3f right, Vector3f up, float partial) {
		float t = Math.min(1.0F, (descent.age + partial) / 20.0F);
		if (descent.age > 30) {
			return;
		}
		Vec3 sky = descent.at.add(0.0D, 60.0D, 0.0D);
		Vec3 head = sky.lerp(descent.at, t * t);
		Vec3 tail = sky.lerp(descent.at, Math.max(0.0F, t * t - 0.25F));
		float alpha = descent.age <= 20 ? 1.0F : 1.0F - (descent.age - 20) / 10.0F;
		vertical(STREAK_SINK, cam, tail, head, new Vec3(right.x, 0.0D, right.z).normalize(), 0.35F, 1.2F, argb(0.85F * alpha, descent.rgb));
		billboard(GLOW_SINK, cam, head, right, up, 0.9F, argb(0.9F * alpha, 0xFFFFFF));
		if (descent.age >= 18) {
			billboard(GLOW_SINK, cam, descent.at.add(0.0D, 0.6D, 0.0D), right, up, 3.0F * alpha, argb(0.6F * alpha, descent.rgb));
		}
	}

	// ================================================================== geometry

	private static void billboard(Sink sink, Vec3 cam, Vec3 at, Vector3f right, Vector3f up, float half, int color) {
		float x = (float) (at.x - cam.x);
		float y = (float) (at.y - cam.y);
		float z = (float) (at.z - cam.z);
		float rx = right.x * half;
		float ry = right.y * half;
		float rz = right.z * half;
		float ux = up.x * half;
		float uy = up.y * half;
		float uz = up.z * half;
		sink.vertex(x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 1.0F, color);
		sink.vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 1.0F, color);
		sink.vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 0.0F, color);
		sink.vertex(x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 0.0F, color);
	}

	/** A vertical (or slanted) strip from {@code a} to {@code b}, {@code wa} wide at a and {@code wb} at b, both faces. */
	private static void vertical(Sink sink, Vec3 cam, Vec3 a, Vec3 b, Vec3 side, float wa, float wb, int color) {
		float ax = (float) (a.x - cam.x);
		float ay = (float) (a.y - cam.y);
		float az = (float) (a.z - cam.z);
		float bx = (float) (b.x - cam.x);
		float by = (float) (b.y - cam.y);
		float bz = (float) (b.z - cam.z);
		float sx = (float) side.x;
		float sz = (float) side.z;
		float ha = wa * 0.5F;
		float hb = wb * 0.5F;
		sink.vertex(ax - sx * ha, ay, az - sz * ha, 0.0F, 1.0F, color);
		sink.vertex(ax + sx * ha, ay, az + sz * ha, 1.0F, 1.0F, color);
		sink.vertex(bx + sx * hb, by, bz + sz * hb, 1.0F, 0.0F, color);
		sink.vertex(bx - sx * hb, by, bz - sz * hb, 0.0F, 0.0F, color);
		sink.vertex(bx - sx * hb, by, bz - sz * hb, 0.0F, 0.0F, color);
		sink.vertex(bx + sx * hb, by, bz + sz * hb, 1.0F, 0.0F, color);
		sink.vertex(ax + sx * ha, ay, az + sz * ha, 1.0F, 1.0F, color);
		sink.vertex(ax - sx * ha, ay, az - sz * ha, 0.0F, 1.0F, color);
	}

	/** A flat horizontal square (a cloud seen from above and below), turned by {@code angle}. */
	private static void flat(Sink sink, Vec3 cam, Vec3 at, float half, int color, float angle) {
		float x = (float) (at.x - cam.x);
		float y = (float) (at.y - cam.y);
		float z = (float) (at.z - cam.z);
		float c = (float) Math.cos(angle) * half;
		float s = (float) Math.sin(angle) * half;
		sink.vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color);
		sink.vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color);
		sink.vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color);
		sink.vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color);
		sink.vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color);
		sink.vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color);
		sink.vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color);
		sink.vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color);
	}

	private static int argb(float alpha, int rgb) {
		return ARGB.color(Math.max(0, Math.min(255, Math.round(alpha * 255.0F))), rgb & 0xFFFFFF);
	}

	/** Vertices of one render type for this frame, submitted once (camera-relative, full bright). */
	private static final class Sink {
		private static final int STRIDE = 5;
		private final RenderType type;
		private float[] data = new float[STRIDE * 512];
		private int[] colors = new int[512];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type) {
			this.type = type;
		}

		void vertex(float x, float y, float z, float u, float v, int argb) {
			if (count >= colors.length) {
				int size = colors.length * 2;
				float[] more = new float[size * STRIDE];
				System.arraycopy(data, 0, more, 0, data.length);
				data = more;
				int[] moreColors = new int[size];
				System.arraycopy(colors, 0, moreColors, 0, colors.length);
				colors = moreColors;
			}
			int o = count * STRIDE;
			data[o] = x;
			data[o + 1] = y;
			data[o + 2] = z;
			data[o + 3] = u;
			data[o + 4] = v;
			colors[count] = argb;
			count++;
		}

		void reset() {
			count = 0;
		}

		void submit(SubmitNodeCollector collector, PoseStack poseStack) {
			if (count < 4) {
				return;
			}
			collector.submitCustomGeometry(poseStack, type, writer);
		}

		private void write(PoseStack.Pose pose, VertexConsumer buffer) {
			int quads = count / 4 * 4;
			for (int i = 0; i < quads; i++) {
				int o = i * STRIDE;
				buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
						.setColor(colors[i])
						.setUv(data[o + 3], data[o + 4])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(pose, 0.0F, 1.0F, 0.0F);
			}
		}
	}

	private KotaFx() {
	}
}
