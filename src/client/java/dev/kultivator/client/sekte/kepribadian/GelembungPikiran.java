package dev.kultivator.client.sekte.kepribadian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.sekte.kepribadian.KepribadianPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gelembung Pikiran, the thought bubbles of module P (DESIGN 36.2.1): a small cloud over a disciple's head with an icon
 * of its heart: a rain cloud (Gelisah), a storm with a bolt (Membangkang), a red mark (Akan Pergi), a bundle (walking out),
 * a heart (very content), and short pops after a moment (a heart after a gift, a tear after a blow, a star after praise).
 * <p>
 * Budget (DESIGN 36.3.9): only within 24 blocks, at most 6 at once (nearest first), two camera-facing quads each from one
 * atlas ({@code textures/sekte/kepribadian/gelembung.png}, 4 x 2 cells of 32 px), drawn from Fabric
 * {@code LevelRenderEvents.COLLECT_SUBMITS} with the plain {@code RenderTypes.entityTranslucent} (no post chain, no core
 * shader: Sodium / Iris safe; skipped in a shader pack's shadow pass). Minimal quality: the icon alone, flat, no bobbing.
 */
final class GelembungPikiran {

	private static final RenderType TYPE = RenderTypes.entityTranslucent(Kultivator.id("textures/sekte/kepribadian/gelembung.png"));
	private static final double RANGE = 24.0D;
	private static final int MAX_SHOWN = 6;
	private static final int POP_TICKS = 60;
	/** Moods not refreshed by the server for this long are dropped (the server resends every 10 s). */
	private static final int STALE_TICKS = 300;

	private record Pop(int entityId, int icon, long born) {
	}

	private static final Map<Integer, Integer> MOODS = new HashMap<>();
	private static final List<Pop> POPS = new ArrayList<>();
	private static final Sink SINK = new Sink();
	private static long ticks;
	private static long moodsAt;
	private static boolean registered;

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(GelembungPikiran::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> clear()));
	}

	static void clear() {
		MOODS.clear();
		POPS.clear();
	}

	static void onMoods(KepribadianPayloads.Suasana payload) {
		MOODS.clear();
		for (int i = 0; i < Math.min(payload.ids().length, payload.icons().length); i++) {
			MOODS.put(payload.ids()[i], payload.icons()[i]);
		}
		moodsAt = ticks;
	}

	static void onPop(KepribadianPayloads.Letup payload) {
		POPS.removeIf(pop -> pop.entityId() == payload.entityId());
		POPS.add(new Pop(payload.entityId(), payload.icon(), ticks));
		while (POPS.size() > 16) {
			POPS.remove(0);
		}
	}

	private static void tick() {
		ticks++;
		POPS.removeIf(pop -> ticks - pop.born() > POP_TICKS);
		if (!MOODS.isEmpty() && ticks - moodsAt > STALE_TICKS) {
			MOODS.clear();
		}
	}

	// ================================================================== render

	private record Shown(Entity entity, int icon, float pop, double distSq) {
	}

	private static void render(LevelRenderContext context) {
		if ((MOODS.isEmpty() && POPS.isEmpty()) || ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
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
			List<Shown> shown = collect(level, cam, partial);
			if (shown.isEmpty()) {
				return;
			}
			boolean minimal = EffectQuality.level() == EffectQuality.Level.MINIMAL;
			float time = ticks + partial;
			SINK.reset();
			for (Shown s : shown) {
				draw(s, cam, right, up, partial, time, minimal);
			}
			PoseStack pose = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			SINK.submit(collector, pose);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: thought bubbles turned off for this frame", exception);
		}
	}

	private static List<Shown> collect(ClientLevel level, Vec3 cam, float partial) {
		Map<Integer, Shown> byId = new HashMap<>();
		for (Map.Entry<Integer, Integer> mood : MOODS.entrySet()) {
			add(level, cam, mood.getKey(), mood.getValue(), 1.0F, byId);
		}
		for (Pop pop : POPS) {
			float age = (ticks - pop.born() + partial) / POP_TICKS;
			// A pop replaces the mood of its disciple while it lasts.
			add(level, cam, pop.entityId(), pop.icon(), Math.max(0.0001F, Math.min(1.0F, age)), byId);
		}
		List<Shown> list = new ArrayList<>(byId.values());
		list.sort((a, b) -> Double.compare(a.distSq(), b.distSq()));
		while (list.size() > MAX_SHOWN) {
			list.remove(list.size() - 1);
		}
		return list;
	}

	private static void add(ClientLevel level, Vec3 cam, int id, int icon, float pop, Map<Integer, Shown> byId) {
		if (icon <= KepribadianPayloads.ICON_NONE || icon >= KepribadianPayloads.ICONS) {
			return;
		}
		Entity entity = level.getEntity(id);
		if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) {
			return;
		}
		double distSq = entity.position().distanceToSqr(cam);
		if (distSq > RANGE * RANGE) {
			return;
		}
		Shown old = byId.get(id);
		if (old == null || pop < 1.0F) {
			byId.put(id, new Shown(entity, icon, pop, distSq));
		}
	}

	/**
	 * One bubble: the cloud (cell 0) and the icon a hair in front of it. A pop grows in over its first tenth, floats a
	 * little and fades over its last third; a mood bobs gently. Far bubbles fade from 16 to 24 blocks.
	 */
	private static void draw(Shown s, Vec3 cam, Vector3f right, Vector3f up, float partial, float time, boolean minimal) {
		Entity entity = s.entity();
		Vec3 base = entity.getPosition(partial);
		double lift = entity.getBbHeight() + 0.62D + (entity.hasCustomName() ? 0.3D : 0.0D);
		float alpha = 1.0F;
		float scale = 1.0F;
		if (s.pop() < 1.0F) {
			float p = s.pop();
			scale = p < 0.1F ? p / 0.1F : 1.0F;
			scale = scale * scale * (3.0F - 2.0F * scale);
			alpha = p > 0.66F ? 1.0F - (p - 0.66F) / 0.34F : 1.0F;
			lift += p * 0.35D;
		} else if (!minimal) {
			lift += Math.sin(time * 0.12F + entity.getId()) * 0.04D;
		}
		double dist = Math.sqrt(s.distSq());
		if (dist > 16.0D) {
			alpha *= (float) Math.max(0.0D, 1.0D - (dist - 16.0D) / (RANGE - 16.0D));
		}
		if (alpha <= 0.02F || scale <= 0.02F) {
			return;
		}
		Vec3 at = base.add(0.0D, lift, 0.0D);
		Vec3 toCam = cam.subtract(at);
		double len = toCam.length();
		Vec3 nudge = len < 1.0E-4D ? Vec3.ZERO : toCam.scale(0.02D / len);
		int icon = s.icon();
		float pulse = icon == KepribadianPayloads.ICON_HEART && !minimal ? 1.0F + 0.08F * (float) Math.sin(time * 0.35F) : 1.0F;
		if (!minimal) {
			quad(cam, at, right, up, 0.34F * scale, 0, color(alpha * 0.92F, 0xFFFFFF));
		}
		float half = (minimal ? 0.24F : 0.21F) * scale * pulse;
		quad(cam, at.add(nudge).add(0.0D, minimal ? 0.0D : 0.03D, 0.0D), right, up, half, icon, color(alpha, 0xFFFFFF));
	}

	/** A camera-facing square of atlas cell {@code cell} (4 x 2 cells). */
	private static void quad(Vec3 cam, Vec3 at, Vector3f right, Vector3f up, float half, int cell, int color) {
		float x = (float) (at.x - cam.x);
		float y = (float) (at.y - cam.y);
		float z = (float) (at.z - cam.z);
		float u0 = (cell % 4) * 0.25F;
		float v0 = (cell / 4) * 0.5F;
		float u1 = u0 + 0.25F;
		float v1 = v0 + 0.5F;
		float rx = right.x * half;
		float ry = right.y * half;
		float rz = right.z * half;
		float ux = up.x * half;
		float uy = up.y * half;
		float uz = up.z * half;
		SINK.vertex(x - rx - ux, y - ry - uy, z - rz - uz, u0, v1, color);
		SINK.vertex(x + rx - ux, y + ry - uy, z + rz - uz, u1, v1, color);
		SINK.vertex(x + rx + ux, y + ry + uy, z + rz + uz, u1, v0, color);
		SINK.vertex(x - rx + ux, y - ry + uy, z - rz + uz, u0, v0, color);
	}

	private static int color(float alpha, int rgb) {
		return ARGB.color(Math.max(0, Math.min(255, Math.round(alpha * 255.0F))), rgb & 0xFFFFFF);
	}

	/** The vertices of this frame (camera-relative, full bright), submitted once. */
	private static final class Sink {
		private static final int STRIDE = 5;
		private final float[] data = new float[STRIDE * 64];
		private final int[] colors = new int[64];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		void vertex(float x, float y, float z, float u, float v, int argb) {
			if (count >= colors.length) {
				return;
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
			collector.submitCustomGeometry(poseStack, TYPE, writer);
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

	private GelembungPikiran() {
	}
}
