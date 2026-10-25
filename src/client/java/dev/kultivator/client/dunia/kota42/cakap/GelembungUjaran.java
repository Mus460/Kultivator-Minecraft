package dev.kultivator.client.dunia.kota42.cakap;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.dunia.kota.KultivatorNpc;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The speech bubble of a spoken line (DESIGN 42.5A.9 "Terlihat", K17): the line of {@code kota_ujaran} wrapped to a
 * comfortable width, on a soft dark panel above the speaker's head, facing the camera. It pops in (scale ease over the
 * first 4 ticks), floats up a little, fades over its last 15 % and between 10 and 12 blocks; the nearest 8 are drawn, a
 * newer line of the same speaker replaces the old one. Lines are already realised by the client's own language (nested
 * {@code Component.translatable}), so Indonesian and English show the same derivation.
 * <p>
 * Drawn through Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} with {@code submitText} (the vanilla nameplate path: no
 * custom shader, Sodium/Iris safe), skipped in a shader pack's shadow pass. Minimal quality: no pop or float, a shorter
 * wrap width.
 */
final class GelembungUjaran {

	private static final double JARAK = 12.0D;
	private static final double PUDAR = 10.0D;
	private static final int MAKS = 8;
	private static final int LEBAR = 150;
	private static final int LEBAR_MINIMAL = 120;
	private static final float SKALA = 0.0225F;

	/** speaker entity id -> client tick its current line popped in. */
	private static final Map<Integer, Long> MULAI = new HashMap<>();
	private static long ticks;

	static void tick() {
		ticks++;
		if (MULAI.size() > 64) {
			MULAI.entrySet().removeIf(e -> ticks - e.getValue() > 600L);
		}
	}

	static void baru(int entityId) {
		MULAI.put(entityId, ticks);
	}

	static void bersih() {
		MULAI.clear();
	}

	private record Tampil(Entity entity, KotaHidupClient.UjaranMasuk ujaran, double distSq) {
	}

	static void render(LevelRenderContext context) {
		if (MULAI.isEmpty() || ShaderCompat.shadowPass()) {
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
			List<Tampil> list = new ArrayList<>();
			for (int id : MULAI.keySet()) {
				Entity e = level.getEntity(id);
				KotaHidupClient.UjaranMasuk u = KotaHidupClient.ujaran(id);
				if (!(e instanceof KultivatorNpc) || u == null || !e.isAlive() || e.isInvisible()) {
					continue;
				}
				double d = e.position().distanceToSqr(cam);
				if (d <= JARAK * JARAK) {
					list.add(new Tampil(e, u, d));
				}
			}
			if (list.isEmpty()) {
				return;
			}
			list.sort((a, b) -> Double.compare(a.distSq(), b.distSq()));
			while (list.size() > MAKS) {
				list.remove(list.size() - 1);
			}
			boolean minimal = EffectQuality.level() == EffectQuality.Level.MINIMAL;
			PoseStack pose = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			Font font = minecraft.font;
			long now = System.currentTimeMillis();
			// far first, so nearer bubbles draw over farther ones
			for (int i = list.size() - 1; i >= 0; i--) {
				gambar(list.get(i), pose, collector, font, cam, camera, partial, now, minimal);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: speech bubbles turned off for this frame", exception);
		}
	}

	private static void gambar(Tampil t, PoseStack pose, SubmitNodeCollector collector, Font font, Vec3 cam, CameraRenderState camera,
			float partial, long now, boolean minimal) {
		Entity e = t.entity();
		KotaHidupClient.UjaranMasuk u = t.ujaran();
		long total = Math.max(1L, u.ujaran().durasi() * 50L + KotaHidupClient.UJARAN_SISA_MS);
		float age = Math.max(0.0F, Math.min(1.0F, (now - u.ms()) / (float) total));
		float alpha = age > 0.85F ? 1.0F - (age - 0.85F) / 0.15F : 1.0F;
		double dist = Math.sqrt(t.distSq());
		if (dist > PUDAR) {
			alpha *= (float) Math.max(0.0D, 1.0D - (dist - PUDAR) / (JARAK - PUDAR));
		}
		if (alpha < 0.04F) {
			return;
		}
		Long start = MULAI.get(e.getId());
		float pop = 1.0F;
		float rise = 0.0F;
		if (!minimal && start != null) {
			float k = Math.min(1.0F, (ticks - start + partial) / 4.0F);
			pop = 0.6F + 0.4F * (k * k * (3.0F - 2.0F * k));
			rise = Math.min(0.12F, (ticks - start + partial) * 0.004F);
		}
		List<FormattedCharSequence> lines = font.split(u.ujaran().teks(), minimal ? LEBAR_MINIMAL : LEBAR);
		if (lines.isEmpty()) {
			return;
		}
		if (lines.size() > 4) {
			lines = lines.subList(0, 4);
		}
		int lineH = font.lineHeight + 1;
		float height = lines.size() * lineH;
		double lift = e.getBbHeight() + 0.55D + (e.hasCustomName() ? 0.28D : 0.0D) + rise;
		Vec3 at = e.getPosition(partial).add(0.0D, lift, 0.0D);
		float scale = SKALA * pop;
		int a = Math.round(255.0F * alpha);
		int text = ARGB.color(a, 0xFFF6E6);
		int panel = ARGB.color(Math.round(150.0F * alpha), 0x1A140F);
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		pose.rotate(camera.orientation);
		pose.scale(scale, -scale, scale);
		float y = -height;
		for (FormattedCharSequence line : lines) {
			float w = font.width(line);
			collector.submitText(pose, -w / 2.0F, y, line, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, text, panel, 0);
			y += lineH;
		}
		// the little tail under the panel, pointing at the speaker
		if (!minimal) {
			FormattedCharSequence tail = FormattedCharSequence.forward("▼", net.minecraft.network.chat.Style.EMPTY);
			collector.submitText(pose, -font.width(tail) / 2.0F, y - 2.0F, tail, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT,
					ARGB.color(Math.round(150.0F * alpha), 0x1A140F), 0, 0);
		}
		pose.popPose();
	}

	private GelembungUjaran() {
	}
}
