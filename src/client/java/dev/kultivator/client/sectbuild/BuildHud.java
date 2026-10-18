package dev.kultivator.client.sectbuild;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The construction HUD (DESIGN 21.12 step 3, 21.21): a small jade panel in the top-left corner.
 * <ul>
 * <li><b>During a preview</b>: "Pratinjau Tapak · Sekte Kelas Dua · Gerbang: Selatan · 2 peringatan", the snapshot
 * ("Tapak terpindai 78%"), whether it can be confirmed, the time left, and an arrow towards the footprint (or towards the
 * part still unscanned when the engine names it) while the player stands outside.</li>
 * <li><b>Near the own site</b> (or a sect mate's): the tier, the open building with its progress, and while the site waits
 * for materials what the next building still needs ("Masih kurang: 32 Batu, 12 Kayu"), so the player always knows what to
 * deposit.</li>
 * </ul>
 * Hidden with F1, the HUD setting or any open screen.
 */
public final class BuildHud implements HudElement {

	private static final int WIDTH = 168;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || !EffectQuality.hudVisible() || minecraft.gui.screen() != null) {
			return;
		}
		Font font = minecraft.font;
		List<Component> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		Vec3 arrowTo = null;
		Component title;
		if (BuildClient.previewActive()) {
			CompoundTag preview = BuildClient.preview();
			int template = preview.getIntOr("t", 0);
			int warnings = 0;
			int red = 0;
			for (Tag each : preview.getListOrEmpty("warn")) {
				if (each instanceof CompoundTag warning) {
					warnings++;
					if (warning.getIntOr("sev", 0) >= 2) {
						red++;
					}
				}
			}
			Component gate = Component.translatable("kultivator.sectbuild.gate." + SectTemplates.gate(preview.getIntOr("rot", 0)).getSerializedName());
			title = Component.translatable("kultivator.sectbuild.hud.preview", SectTemplates.name(template));
			lines.add(Component.translatable("kultivator.sectbuild.hud.gate_warnings", gate, warnings));
			colors.add(Palette.JADE_PALE);
			int snap = preview.getIntOr("snap", 0);
			lines.add(Component.translatable("kultivator.sectbuild.hud.snapshot", snap));
			colors.add(snap >= 100 ? Palette.JADE_LIGHT : Palette.WARN);
			if (red > 0) {
				lines.add(Component.translatable("kultivator.sectbuild.hud.red", red));
				colors.add(0xFFFF6A5A);
			} else if (preview.getBooleanOr("confirmable", false)) {
				lines.add(Component.translatable("kultivator.sectbuild.hud.ready"));
				colors.add(Palette.GOLD_LIGHT);
			}
			lines.add(Component.translatable("kultivator.sectbuild.hud.left", BuildClient.previewLeftMillis() / 1000L));
			colors.add(Palette.INK_FAINT | 0xFF000000);
			int[] miss = preview.getIntArray("miss").orElse(null);
			int[] fp = SiteHologram.box(preview, "fp");
			if (miss != null && miss.length >= 2) {
				arrowTo = new Vec3(miss[0] + 0.5D, minecraft.player.getY(), miss[miss.length - 1] + 0.5D);
			} else if (fp != null && outside(minecraft.player.position(), fp)) {
				arrowTo = new Vec3((fp[0] + fp[3] + 1) / 2.0D, minecraft.player.getY(), (fp[2] + fp[5] + 1) / 2.0D);
			}
		} else {
			CompoundTag status = BuildClient.status();
			if (status.isEmpty()) {
				return;
			}
			int[] fp = SiteHologram.box(status, "fp");
			int state = status.getIntOr("status", SiteView.STATUS_RUNNING);
			boolean waiting = state == SiteView.STATUS_WAITING_FUNDS;
			boolean inside = fp != null && !outside(minecraft.player.position(), new int[]{fp[0] - 8, fp[1], fp[2] - 8, fp[3] + 8, fp[4], fp[5] + 8});
			if (!inside && !waiting) {
				return;
			}
			int template = status.getIntOr("t", 0);
			title = Component.translatable("kultivator.sectbuild.hud.site", SectTemplates.name(template), status.getIntOr("tier", 0), SectTemplates.TIERS);
			CompoundTag open = status.getCompoundOrEmpty("open");
			if (!open.isEmpty()) {
				lines.add(Component.translatable("kultivator.sectbuild.holo.open", open.read("name", ComponentSerialization.CODEC).orElse(Component.empty()),
						open.getIntOr("pg", 0)));
				colors.add(Palette.GOLD_LIGHT);
			}
			Component text = status.read("status_text", ComponentSerialization.CODEC).orElse(null);
			if (text != null && open.isEmpty()) {
				lines.add(text);
				colors.add(waiting ? Palette.WARN : Palette.JADE_PALE);
			}
			Component shortfall = status.read("short", ComponentSerialization.CODEC).orElse(null);
			if (shortfall != null) {
				lines.add(Component.translatable("kultivator.sectbuild.hud.short", shortfall));
				colors.add(0xFFFFC46A);
				lines.add(Component.translatable("kultivator.sectbuild.hud.deposit_hint"));
				colors.add(Palette.INK_FAINT | 0xFF000000);
			}
			if (lines.isEmpty()) {
				return;
			}
		}
		draw(g, font, title, lines, colors, arrowTo, minecraft);
	}

	private static boolean outside(Vec3 pos, int[] fp) {
		return pos.x < fp[0] || pos.x > fp[3] + 1 || pos.z < fp[2] || pos.z > fp[5] + 1;
	}

	private static void draw(GuiGraphicsExtractor g, Font font, Component title, List<Component> lines, List<Integer> colors, Vec3 arrowTo,
			Minecraft minecraft) {
		float scale = 0.7F;
		int x0 = 4;
		int y0 = 4;
		int width = WIDTH;
		int height = Math.round(12 + lines.size() * 9 * scale + 4);
		// A jade slip with a gold edge, a seal and a terrace glyph.
		g.fill(x0 + 1, y0 + 2, x0 + width + 1, y0 + height + 1, 0x50000000);
		g.fillGradient(x0, y0, x0 + width, y0 + height, 0xE0122A22, 0xE00C1C17);
		Brush.frame(g, x0, y0, x0 + width, y0 + height, Palette.argb(0xD0, Palette.GOLD));
		Brush.frame(g, x0 + 1, y0 + 1, x0 + width - 1, y0 + height - 1, Palette.argb(0x60, Palette.JADE_DARK));
		g.fill(x0 + 3, y0 + 3, x0 + 9, y0 + 9, Palette.SEAL);
		Glyphs.tab(g, Glyphs.BUILD, x0 + 6.0F, y0 + 6.0F, 5.0F, Palette.TALISMAN);
		Brush.fit(g, font, Brush.bold(title), x0 + 12.0F, y0 + 3.0F, width - 16.0F, Palette.GOLD_LIGHT, 0.75F, 0.5F, false);
		float y = y0 + 12.0F;
		for (int i = 0; i < lines.size(); i++) {
			Brush.fit(g, font, lines.get(i), x0 + 5.0F, y, width - 10.0F, colors.get(i), scale, 0.5F, false);
			y += 9.0F * scale;
		}
		if (arrowTo != null && minecraft.player != null) {
			double dx = arrowTo.x - minecraft.player.getX();
			double dz = arrowTo.z - minecraft.player.getZ();
			double yaw = Math.toRadians(minecraft.player.getYRot());
			// Forward is (-sin yaw, cos yaw), right is (-cos yaw, -sin yaw): up on the HUD is where the player looks.
			double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
			double right = -dx * Math.cos(yaw) - dz * Math.sin(yaw);
			double length = Math.max(1.0E-4D, Math.sqrt(forward * forward + right * right));
			float cx = x0 + width + 10.0F;
			float cy = y0 + height / 2.0F;
			float pulse = 0.6F + 0.4F * (float) Math.sin(Util.getMillis() / 250.0D);
			Brush.disc(g, cx, cy, 7.0F, 0xC0122A22);
			Brush.ring(g, cx, cy, 6.0F, 7.0F, Palette.argb(0xC0, Palette.GOLD));
			float ax = (float) (right / length);
			float ay = (float) (-forward / length);
			Brush.line(g, cx - ax * 3.5F, cy - ay * 3.5F, cx + ax * 4.5F, cy + ay * 4.5F, 1.4F, Palette.argb(Math.round(255 * pulse), Palette.GOLD_LIGHT));
			Brush.dot(g, cx + ax * 4.5F, cy + ay * 4.5F, 1.4F, Palette.argb(Math.round(255 * pulse), Palette.GOLD_LIGHT));
		}
	}
}
