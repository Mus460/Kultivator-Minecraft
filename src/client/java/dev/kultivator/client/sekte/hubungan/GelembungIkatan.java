package dev.kultivator.client.sekte.hubungan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.sekte.hubungan.HubunganPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The thought bubbles of module R (DESIGN 36.2.2, 36.3.9) and the lovers' motes, drawn from Fabric
 * {@code LevelRenderEvents.COLLECT_SUBMITS} with two vanilla entity render types (translucent bubble atlas, an emissive
 * glow behind the warm ones), so Sodium and Iris draw them like any entity quad; skipped in a shader pack's shadow pass.
 * <ul>
 * <li>A bubble (tea, heart, lightning, broken heart, crossed swords, red knot, lotus, sparkle) floats over a disciple's
 * head: it pops in, bobs, and fades out. Only within 24 blocks, at most 6 at once (the nearest); at Minimal it is a flat
 * icon without pop, bob or halo.</li>
 * <li>Lovers and Dao companions standing close shed pink petals and motes of {@code kultivator:qi} drifting between them
 * (Normal: one every half second per pair, Maximal: two; Minimal: none), within 32 blocks.</li>
 * </ul>
 */
final class GelembungIkatan {

	private static final Identifier ATLAS_TEX = Kultivator.id("textures/sekte/hubungan/ikon.png");
	private static final Identifier GLOW_TEX = Kultivator.id("textures/sekte/hubungan/cahaya.png");
	private static final RenderType ATLAS = RenderTypes.entityTranslucent(ATLAS_TEX);
	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(GLOW_TEX);

	private static final int MAX_SHOWN = 6;
	private static final double RANGE = 24.0D;
	private static final double PETAL_RANGE = 32.0D;
	private static final int[] HALO = {0xBFE8D0, 0xFF7FA8, 0xFFD040, 0x9A88B0, 0xF0C8A0, 0xFF5A4A, 0xFF9CC8, 0xFFE27A};

	private record Bubble(int icon, long start, long until) {
	}

	private record Lovers(int a, int b, int kind, long until) {
	}

	private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();
	private static final List<Lovers> LOVERS = new ArrayList<>();
	private static final Sink BUBBLE_SINK = new Sink(ATLAS, 96);
	private static final Sink GLOW_SINK = new Sink(GLOW, 48);
	private static final RandomSource RANDOM = RandomSource.create();
	private static long ticks;

	static void onBubble(HubunganPayloads.Gelembung payload) {
		long until = ticks + Math.max(20, payload.ticks());
		if (payload.a() >= 0) {
			BUBBLES.put(payload.a(), new Bubble(payload.icon(), ticks, until));
		}
		if (payload.b() >= 0) {
			// the second one pops a moment later, so a pair reads as an answer
			BUBBLES.put(payload.b(), new Bubble(payload.icon(), ticks + 4, until + 4));
		}
		if (BUBBLES.size() > 64) {
			BUBBLES.clear();
		}
	}

	static void onPairs(HubunganPayloads.Pasangan payload) {
		int[] ids = payload.ids();
		int[] kinds = payload.kinds();
		for (int i = 0; i + 1 < ids.length && i / 2 < kinds.length; i += 2) {
			int a = ids[i];
			int b = ids[i + 1];
			LOVERS.removeIf(l -> l.a() == a && l.b() == b);
			LOVERS.add(new Lovers(a, b, kinds[i / 2], ticks + 140));
		}
		while (LOVERS.size() > 24) {
			LOVERS.remove(0);
		}
	}

	static void clear() {
		BUBBLES.clear();
		LOVERS.clear();
	}

	static void tick(Minecraft minecraft) {
		ticks++;
		BUBBLES.values().removeIf(b -> b.until() < ticks);
		LOVERS.removeIf(l -> l.until() < ticks);
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || LOVERS.isEmpty() || EffectQuality.minimal() || ticks % 10L != 0L) {
			return;
		}
		int per = EffectQuality.maximal() ? 2 : 1;
		Vec3 eye = minecraft.player.position();
		for (Lovers lovers : LOVERS) {
			Entity a = level.getEntity(lovers.a());
			Entity b = level.getEntity(lovers.b());
			if (a == null || b == null || a.distanceToSqr(b) > 64.0D || a.distanceToSqr(eye) > PETAL_RANGE * PETAL_RANGE) {
				continue;
			}
			int pink = lovers.kind() == 1 ? 0xFF7FB0 : 0xFFB0CC;
			for (int i = 0; i < per; i++) {
				double t = RANDOM.nextDouble();
				double x = Mth.lerp(t, a.getX(), b.getX()) + (RANDOM.nextDouble() - 0.5D) * 0.6D;
				double y = Mth.lerp(t, a.getY(), b.getY()) + 1.1D + RANDOM.nextDouble() * 0.7D;
				double z = Mth.lerp(t, a.getZ(), b.getZ()) + (RANDOM.nextDouble() - 0.5D) * 0.6D;
				if (lovers.kind() == 1 && RANDOM.nextInt(3) != 0) {
					level.addParticle(Qi.petal(pink, 0.14F, 46), x, y, z, (RANDOM.nextDouble() - 0.5D) * 0.02D, 0.012D,
							(RANDOM.nextDouble() - 0.5D) * 0.02D);
				} else {
					level.addParticle(Qi.mote(pink, 0.08F, 36), x, y, z, 0.0D, 0.018D, 0.0D);
				}
			}
		}
	}

	// ================================================================== drawing

	static void render(LevelRenderContext context) {
		if (BUBBLES.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		float partial = context.levelState().worldPartialTicks;
		float now = ticks + partial;
		// the nearest few
		List<Object[]> shown = new ArrayList<>();
		for (Iterator<Map.Entry<Integer, Bubble>> it = BUBBLES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Bubble> entry = it.next();
			Bubble bubble = entry.getValue();
			if (bubble.start() > ticks) {
				continue;
			}
			Entity entity = level.getEntity(entry.getKey());
			if (entity == null || entity.isRemoved()) {
				it.remove();
				continue;
			}
			Vec3 at = entity.getPosition(partial).add(0.0D, entity.getBbHeight() + 0.62D, 0.0D);
			double d2 = at.distanceToSqr(cam);
			if (d2 > RANGE * RANGE) {
				continue;
			}
			shown.add(new Object[]{bubble, at, d2, entity.getId()});
		}
		if (shown.isEmpty()) {
			return;
		}
		shown.sort((x, y) -> Double.compare((double) x[2], (double) y[2]));
		Vector3f right = new Vector3f();
		Vector3f up = new Vector3f();
		camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
		camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
		boolean minimal = EffectQuality.minimal();
		BUBBLE_SINK.reset();
		GLOW_SINK.reset();
		// far ones first so the near ones lie on top
		for (int i = Math.min(MAX_SHOWN, shown.size()) - 1; i >= 0; i--) {
			Object[] row = shown.get(i);
			Bubble bubble = (Bubble) row[0];
			Vec3 at = (Vec3) row[1];
			int id = (int) row[3];
			float age = now - bubble.start();
			float left = bubble.until() - now;
			float alpha = Mth.clamp(left / 10.0F, 0.0F, 1.0F) * Mth.clamp(age / 3.0F, 0.0F, 1.0F);
			if (alpha <= 0.02F) {
				continue;
			}
			float scale = 1.0F;
			float bob = 0.0F;
			if (!minimal) {
				// pop: overshoot then settle
				float p = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
				scale = p < 1.0F ? Mth.sin(p * Mth.PI * 0.75F) * 1.2F : 1.0F;
				bob = Mth.sin((now + id * 7) * 0.12F) * 0.04F;
			}
			float half = 0.3F * scale;
			float x = (float) (at.x - cam.x);
			float y = (float) (at.y - cam.y) + bob;
			float z = (float) (at.z - cam.z);
			int icon = Mth.clamp(bubble.icon(), 0, HubunganPayloads.ICONS - 1);
			if (!minimal && icon != HubunganPayloads.ICON_TEH && icon != HubunganPayloads.ICON_PATAH) {
				float pulse = 0.75F + 0.25F * Mth.sin(now * 0.2F + id);
				GLOW_SINK.billboard(x, y, z, right, up, half * 1.9F, 0.0F, 0.0F, 1.0F, 1.0F, argb(0.45F * alpha * pulse, HALO[icon]));
			}
			float u0 = (icon % 4) * 0.25F;
			float v0 = (icon / 4) * 0.5F;
			BUBBLE_SINK.billboard(x, y, z, right, up, half, u0, v0, u0 + 0.25F, v0 + 0.5F, argb(alpha, 0xFFFFFF));
		}
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		GLOW_SINK.submit(collector, pose);
		BUBBLE_SINK.submit(collector, pose);
	}

	private static int argb(float alpha, int rgb) {
		return ARGB.color(Math.max(0, Math.min(255, Math.round(alpha * 255.0F))), rgb & 0xFFFFFF);
	}

	/** The quads of one render type for one frame (written once to the collector, full bright). */
	private static final class Sink {
		private static final int STRIDE = 5;
		private final RenderType type;
		private float[] data;
		private int[] colors;
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type, int vertices) {
			this.type = type;
			this.data = new float[STRIDE * vertices];
			this.colors = new int[vertices];
		}

		void reset() {
			count = 0;
		}

		private void vertex(float x, float y, float z, float u, float v, int argb) {
			if (count >= colors.length) {
				float[] more = new float[data.length * 2];
				System.arraycopy(data, 0, more, 0, data.length);
				data = more;
				int[] moreColors = new int[colors.length * 2];
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

		/** A camera-facing quad of half size {@code half} showing the atlas region u0..u1, v0..v1. */
		void billboard(float x, float y, float z, Vector3f right, Vector3f up, float half, float u0, float v0, float u1, float v1, int color) {
			float rx = right.x * half;
			float ry = right.y * half;
			float rz = right.z * half;
			float ux = up.x * half;
			float uy = up.y * half;
			float uz = up.z * half;
			vertex(x - rx - ux, y - ry - uy, z - rz - uz, u0, v1, color);
			vertex(x + rx - ux, y + ry - uy, z + rz - uz, u1, v1, color);
			vertex(x + rx + ux, y + ry + uy, z + rz + uz, u1, v0, color);
			vertex(x - rx + ux, y - ry + uy, z - rz + uz, u0, v0, color);
		}

		void submit(SubmitNodeCollector collector, PoseStack poseStack) {
			if (count >= 4) {
				collector.submitCustomGeometry(poseStack, type, writer);
			}
		}

		private void write(PoseStack.Pose pose, VertexConsumer buffer) {
			int n = count / 4 * 4;
			for (int i = 0; i < n; i++) {
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

	private GelembungIkatan() {
	}
}
