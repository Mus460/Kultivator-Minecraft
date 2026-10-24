package dev.kultivator.client.sekte.mandiri;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.sekte.mandiri.cerita.MandiriPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The thought bubbles of DESIGN 39.4.8/39.8 ({@code mandiri_pikir}, sent by the server's observer {@code Pengamat}): a
 * small cloud over an autonomous disciple's head with what it thinks of right now: a qi swirl while it cultivates, a
 * sword at drill, a cauldron, a talisman, spirit stones, tea, a bowl, "Zzz", a scroll, a travel bundle, a town gate, the
 * moon, a violet demon wisp when tempted, a heart, a star, a seal paper; an optional caption line under it when the
 * player stands within 10 blocks ("Satu jurus lagi!").
 * <p>
 * Budget (DESIGN 39.9): within 32 blocks, at most six at once (nearest first), two camera-facing quads each from one atlas
 * ({@code textures/sekte/mandiri/pikir.png}, 8 x 3 cells of 32 px: 16 icons, the cloud, the violet cloud) through Fabric
 * {@code LevelRenderEvents.COLLECT_SUBMITS} with the plain {@code RenderTypes.entityTranslucent} (no post chain, no core
 * shader; skipped in a shader pack's shadow pass, Sodium/Iris safe). Minimal quality: the icon alone, no caption, no bob.
 */
final class GelembungMandiri {

	private static final RenderType TYPE = RenderTypes.entityTranslucent(Kultivator.id("textures/sekte/mandiri/pikir.png"));
	private static final double RANGE = 32.0D;
	private static final double CAPTION_RANGE = 10.0D;
	private static final int MAX_SHOWN = 6;
	private static final int CLOUD = 16;
	private static final int CLOUD_IBLIS = 17;

	private record Pikir(int entityId, int icon, String kunci, long born, int ticks) {
	}

	private static final Map<Integer, Pikir> AKTIF = new HashMap<>();
	private static final Sink SINK = new Sink();
	private static long ticks;
	private static boolean registered;

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(GelembungMandiri::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	static void bersih() {
		AKTIF.clear();
	}

	static void terima(MandiriPayloads.Pikir p) {
		if (AKTIF.size() >= 32 && !AKTIF.containsKey(p.id())) {
			return;
		}
		AKTIF.put(p.id(), new Pikir(p.id(), p.ikon(), p.kunci(), ticks, Math.max(20, p.ticks())));
	}

	/** A bubble raised on this client alone (the sleeper's "Zzz" of a clip hook). */
	static void lokal(int entityId, int icon, int ticks) {
		Pikir now = AKTIF.get(entityId);
		if (now != null && now.icon() == icon && GelembungMandiri.ticks - now.born() < now.ticks() / 2) {
			return;
		}
		if (AKTIF.size() < 32 || now != null) {
			AKTIF.put(entityId, new Pikir(entityId, icon, "", GelembungMandiri.ticks, Math.max(20, ticks)));
		}
	}

	private static void tick() {
		ticks++;
		if (!AKTIF.isEmpty()) {
			AKTIF.values().removeIf(b -> ticks - b.born() > b.ticks());
		}
	}

	private record Shown(Entity entity, Pikir pikir, float age, double distSq) {
	}

	private static void render(LevelRenderContext context) {
		if (AKTIF.isEmpty() || ShaderCompat.shadowPass()) {
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
			List<Shown> shown = new ArrayList<>();
			for (Pikir b : AKTIF.values()) {
				Entity entity = level.getEntity(b.entityId());
				if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) {
					continue;
				}
				double d = entity.position().distanceToSqr(cam);
				if (d <= RANGE * RANGE) {
					shown.add(new Shown(entity, b, (ticks - b.born() + partial) / b.ticks(), d));
				}
			}
			if (shown.isEmpty()) {
				return;
			}
			shown.sort((a, b) -> Double.compare(a.distSq(), b.distSq()));
			while (shown.size() > MAX_SHOWN) {
				shown.remove(shown.size() - 1);
			}
			boolean minimal = EffectQuality.level() == EffectQuality.Level.MINIMAL;
			float time = ticks + partial;
			SINK.reset();
			List<Shown> captions = new ArrayList<>();
			for (Shown s : shown) {
				draw(s, cam, right, up, partial, time, minimal);
				if (!minimal && !s.pikir().kunci().isEmpty() && s.distSq() <= CAPTION_RANGE * CAPTION_RANGE) {
					captions.add(s);
				}
			}
			PoseStack pose = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			SINK.submit(collector, pose);
			for (Shown s : captions) {
				caption(pose, collector, s, cam, camera.orientation, partial);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: disciple thought bubbles turned off for this frame", exception);
		}
	}

	private static double lift(Entity entity) {
		return entity.getBbHeight() + 0.72D + (entity.hasCustomName() ? 0.3D : 0.0D);
	}

	/** A bubble grows in, bobs, and fades over its last fifth; far ones fade from 24 to 32 blocks. */
	private static void draw(Shown s, Vec3 cam, Vector3f right, Vector3f up, float partial, float time, boolean minimal) {
		Entity entity = s.entity();
		float age = Math.max(0.0F, Math.min(1.0F, s.age()));
		float grow = Math.min(1.0F, age * 8.0F);
		grow = grow * grow * (3.0F - 2.0F * grow);
		float alpha = age > 0.8F ? 1.0F - (age - 0.8F) / 0.2F : 1.0F;
		double dist = Math.sqrt(s.distSq());
		if (dist > 24.0D) {
			alpha *= (float) Math.max(0.0D, 1.0D - (dist - 24.0D) / (RANGE - 24.0D));
		}
		if (alpha <= 0.02F || grow <= 0.02F) {
			return;
		}
		double lift = lift(entity);
		if (!minimal) {
			lift += Math.sin(time * 0.11F + entity.getId()) * 0.05D;
		}
		Vec3 at = entity.getPosition(partial).add(0.0D, lift, 0.0D);
		Vec3 toCam = cam.subtract(at);
		double len = toCam.length();
		Vec3 nudge = len < 1.0E-4D ? Vec3.ZERO : toCam.scale(0.02D / len);
		int icon = s.pikir().icon();
		boolean iblis = icon == MandiriPayloads.PIKIR_IBLIS;
		if (!minimal) {
			quad(cam, at, right, up, 0.36F * grow, iblis ? CLOUD_IBLIS : CLOUD, color(alpha * 0.94F));
			// two little trailing puffs toward the head
			Vec3 trail = entity.getPosition(partial).add(0.0D, entity.getBbHeight() + 0.28D, 0.0D);
			quad(cam, trail.add(right.x * 0.12D, right.y * 0.12D, right.z * 0.12D), right, up, 0.06F * grow, iblis ? CLOUD_IBLIS : CLOUD,
					color(alpha * 0.8F));
			quad(cam, trail.add(right.x * 0.18D, 0.16D + right.y * 0.18D, right.z * 0.18D), right, up, 0.09F * grow, iblis ? CLOUD_IBLIS : CLOUD,
					color(alpha * 0.86F));
		}
		float pulse = (icon == MandiriPayloads.PIKIR_HATI || icon == MandiriPayloads.PIKIR_BINTANG) && !minimal
				? 1.0F + 0.08F * (float) Math.sin(time * 0.35F) : 1.0F;
		float half = (minimal ? 0.24F : 0.22F) * grow * pulse;
		quad(cam, at.add(nudge).add(0.0D, minimal ? 0.0D : 0.02D, 0.0D), right, up, half, icon, color(alpha));
	}

	private static void caption(PoseStack poseStack, SubmitNodeCollector collector, Shown s, Vec3 cam, Quaternionf orientation, float partial) {
		Entity entity = s.entity();
		Vec3 at = entity.getPosition(partial).add(0.0D, lift(entity) - 0.42D, 0.0D);
		Font font = Minecraft.getInstance().font;
		FormattedCharSequence seq = Component.translatable(s.pikir().kunci()).getVisualOrderText();
		float width = font.width(seq);
		float scale = 0.16F / 9.0F;
		float age = Math.max(0.0F, Math.min(1.0F, s.age()));
		int alpha = Math.round(255 * (age > 0.8F ? 1.0F - (age - 0.8F) / 0.2F : 1.0F));
		if (alpha < 8) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		poseStack.rotate(orientation);
		poseStack.scale(scale, -scale, scale);
		collector.submitText(poseStack, -width / 2.0F, -4.5F, seq, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT,
				ARGB.color(alpha, 0xFFF4E0), ARGB.color(alpha / 3, 0x000000), 0);
		poseStack.popPose();
	}

	/** A camera-facing square of atlas cell {@code cell} (8 x 3 cells). */
	private static void quad(Vec3 cam, Vec3 at, Vector3f right, Vector3f up, float half, int cell, int color) {
		float x = (float) (at.x - cam.x);
		float y = (float) (at.y - cam.y);
		float z = (float) (at.z - cam.z);
		float u0 = (cell % 8) * 0.125F;
		float v0 = (cell / 8) / 3.0F;
		float u1 = u0 + 0.125F;
		float v1 = v0 + 1.0F / 3.0F;
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

	private static int color(float alpha) {
		return ARGB.color(Math.max(0, Math.min(255, Math.round(alpha * 255.0F))), 0xFFFFFF);
	}

	/** The vertices of this frame (camera-relative, full bright), submitted once. */
	private static final class Sink {
		private static final int STRIDE = 5;
		private static final int MAX = 96;
		private final float[] data = new float[STRIDE * MAX];
		private final int[] colors = new int[MAX];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		void vertex(float x, float y, float z, float u, float v, int argb) {
			if (count >= MAX) {
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

	private GelembungMandiri() {
	}
}
