package dev.kultivator.client.goals;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.cultivation.Tribulation;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.util.Locale;

/**
 * The live tribulation HUD (DESIGN 19.3.10), read from {@code sessionExtra.trib} (written by P7-tempur), on the left
 * edge, vertically centred (clear of the boss bars of the heart demon, the duel arena and the Raja Siluman, of the red
 * sky and of the toasts): bolt pips "Petir 4/9" and a ring emptying toward the next strike; "Berikutnya −5.4 ❤" in white,
 * yellow when the strike takes more than half the current health, and flashing red with a heartbeat and a warning when
 * it would make the tribulation fail; "Keluar dari lingkar!" while a lightning ring is under the player; a small
 * top-down map of the 6-block limit (amber above 4.5, red above 5.5, an arrow back to the centre); "Dikawal: 2".
 * <p>
 * Sumber: game design (UX).
 */
public final class TribulationHud implements HudElement {

	public static final int WIDTH = 104;
	/** Radius of a lightning ring (DESIGN 19.3.8). */
	public static final double RING_RADIUS = 1.5D;
	private static final double AMBER_DISTANCE = 4.5D;
	private static final double RED_DISTANCE = 5.5D;
	private static long lastBeat;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen() || !EffectQuality.hudVisible()
				|| !ClientCultivation.received()) {
			return;
		}
		CompoundTag trib = HudDemo.active() ? HudDemo.sampleTribulation() : ClientCultivation.data().sessionExtra().getCompoundOrEmpty("trib");
		int phase = trib.getIntOr("phase", 0);
		if (phase <= 0) {
			return;
		}
		Font font = minecraft.font;
		int width = g.guiWidth();
		int height = g.guiHeight();
		float scale = Math.max(0.65F, Math.min(1.0F, (width - 180.0F) / 300.0F));
		long now = ClientCultivation.serverGameTime();
		long millis = Util.getMillis();
		float pulse = 0.5F + 0.5F * (float) Math.sin(millis / 160.0D);

		int done = trib.getIntOr("done", 0);
		int total = Math.max(1, trib.getIntOr("total", 1));
		long next = trib.getLongOr("next", now);
		double pct = trib.getDoubleOr("pct", 0.0D);
		double damage = pct * player.getMaxHealth();
		double health = player.getHealth() + player.getAbsorptionAmount();
		boolean deadly = phase <= 2 && damage >= health;
		boolean heavy = !deadly && damage > player.getHealth() * 0.5D;
		boolean ring = trib.contains("rx") && trib.getLongOr("rend", 0L) > now;
		boolean inRing = ring && (inside(player, trib, "rx", "rz") || trib.contains("tx") && inside(player, trib, "tx", "tz"));
		boolean map = trib.contains("cx");
		int guards = trib.getIntOr("guards", 0);

		int lines = 3 + (deadly ? 2 : 0) + (ring ? 1 : 0) + (guards > 0 ? 1 : 0);
		float h = 8.0F + lines * 7.0F + (map ? 34.0F : 0.0F) + (total > 9 ? 6.0F : 0.0F);
		g.pose().pushMatrix();
		g.pose().translate(4.0F, height / 2.0F - h * scale / 2.0F);
		g.pose().scale(scale, scale);
		int w = WIDTH;
		int border = deadly ? Palette.mix(Palette.DANGER, 0xFFFF9A8A, pulse) : Palette.argb(0xFF, 0x9A7CFF);
		g.fill(1, 1, w + 1, Math.round(h) + 1, 0x50000000);
		g.fill(0, 0, w, Math.round(h), 0xE6101828);
		Brush.frame(g, 0, 0, w, Math.round(h), border);
		Brush.frame(g, 1, 1, w - 1, Math.round(h) - 1, Palette.argb(0x70, 0x5A4AA0));

		// Header: bolt, "Petir 4/9" (omen: the countdown, settling: a quiet line) and the ring emptying toward the next strike.
		float y = 4.0F;
		bolt(g, 7.0F, y + 3.5F, 0xFFE8DCFF);
		MutableComponent header;
		float share;
		if (phase == 1) {
			long left = Math.max(0L, next - now);
			header = Component.translatable("kultivator.goals.trib.omen", String.format(Locale.ROOT, "%.1f", left / 20.0D));
			share = Math.min(1.0F, left / (float) Tribulation.OMEN_TICKS);
		} else if (phase == 3) {
			header = Component.translatable("kultivator.goals.trib.settling");
			share = 0.0F;
		} else {
			header = Component.translatable("kultivator.goals.trib.strikes", Math.min(done + 1, total), total);
			share = Math.min(1.0F, Math.max(0.0F, (next - now) / (float) Tribulation.STRIKE_INTERVAL));
		}
		Brush.fit(g, font, Brush.bold(header), 13.0F, y, w - 32.0F, 0xFFE8DCFF, 0.7F, 0.5F, true);
		float rcx = w - 9.0F;
		float rcy = y + 3.5F;
		Brush.arc(g, rcx, rcy, 3.0F, 5.0F, 0.0F, 1.0F, 0xFF2A2440);
		if (share > 0.0F) {
			Brush.arc(g, rcx, rcy, 3.0F, 5.0F, 1.0F - share, 1.0F, deadly ? Palette.mix(Palette.DANGER, 0xFFFFE0E0, pulse) : 0xFFB9A4FF);
		}
		y += 9.0F;

		// Bolt pips, nine per row.
		int perRow = 9;
		float pipStep = (w - 12.0F) / perRow;
		for (int i = 0; i < Math.min(total, 18); i++) {
			float px = 6.0F + (i % perRow) * pipStep + pipStep / 2.0F;
			float py = y + 2.5F + (i / perRow) * 6.0F;
			int color = i < done ? 0xFF4A4460 : i == done && phase == 2 ? Palette.mix(0xFFB9A4FF, 0xFFFFFFFF, pulse) : 0xFFB9A4FF;
			if (i == total - 1) {
				color = i < done ? 0xFF4A4460 : Palette.mix(color, Palette.GOLD_LIGHT, 0.5F);
			}
			bolt(g, px, py, color);
		}
		y += total > 9 ? 13.0F : 7.0F;

		// "Berikutnya −5.4 ❤".
		int nextColor = deadly ? Palette.mix(0xFFFF4A3A, 0xFFFFD0C8, pulse) : heavy ? 0xFFFFE066 : 0xFFFFFFFF;
		Component nextLine = Component.translatable("kultivator.goals.trib.next", String.format(Locale.ROOT, "%.1f", damage / 2.0D));
		if (phase != 3) {
			Brush.fit(g, font, nextLine, 6.0F, y, w - 12.0F, nextColor, 0.65F, 0.45F, true);
		}
		y += 7.0F;
		if (deadly) {
			Component warn = Component.translatable("kultivator.goals.trib.deadly");
			y += Brush.wrap(g, font, warn, 6.0F, y, w - 12.0F, Palette.mix(0xFFFF6A5A, 0xFFFFFFFF, pulse * 0.4F), 0.55F, true);
			if (millis - lastBeat > 900L) {
				lastBeat = millis;
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.7F));
			}
		}
		if (ring) {
			Component text = Component.translatable(inRing ? "kultivator.goals.trib.ring_in" : "kultivator.goals.trib.ring_out");
			Brush.fit(g, font, text, 6.0F, y, w - 12.0F, inRing ? Palette.mix(0xFFFFB347, 0xFFFFFFFF, pulse * 0.5F) : 0xFF8FE3C8, 0.62F, 0.45F, true);
			y += 7.0F;
		}
		if (guards > 0) {
			Brush.fit(g, font, Component.translatable("kultivator.goals.trib.guards", guards), 6.0F, y, w - 12.0F, Palette.GOLD_LIGHT, 0.62F, 0.45F, true);
			y += 7.0F;
		}
		if (map) {
			drawMap(g, font, player, trib, w / 2.0F, y + 16.0F, ring, millis);
		}
		g.pose().popMatrix();
	}

	private static boolean inside(LocalPlayer player, CompoundTag trib, String xKey, String zKey) {
		double dx = player.getX() - trib.getDoubleOr(xKey, 0.0D);
		double dz = player.getZ() - trib.getDoubleOr(zKey, 0.0D);
		double radius = trib.getDoubleOr("rr", RING_RADIUS);
		return dx * dx + dz * dz <= radius * radius;
	}

	/** The 6-block limit seen from above, turned with the player's view (up = where you look). */
	private static void drawMap(GuiGraphicsExtractor g, Font font, LocalPlayer player, CompoundTag trib, float cx, float cy, boolean ring, long millis) {
		float radius = 14.0F;
		double limit = Math.max(1.0D, trib.getDoubleOr("max", Tribulation.MAX_DISTANCE));
		float perBlock = (float) (radius / limit);
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double centreX = trib.getDoubleOr("cx", player.getX());
		double centreZ = trib.getDoubleOr("cz", player.getZ());
		double dx = player.getX() - centreX;
		double dz = player.getZ() - centreZ;
		double distance = Math.sqrt(dx * dx + dz * dz);
		double amber = limit * AMBER_DISTANCE / Tribulation.MAX_DISTANCE;
		int limitColor = distance > limit * RED_DISTANCE / Tribulation.MAX_DISTANCE ? Palette.DANGER : distance > amber ? Palette.WARN : 0xFF6C5CA8;
		Brush.disc(g, cx, cy, radius + 1.0F, 0xC0141020);
		Brush.ring(g, cx, cy, radius, radius + 1.0F, limitColor);
		Brush.ring(g, cx, cy, radius * (float) (amber / limit) - 0.3F, radius * (float) (amber / limit) + 0.3F, 0x40FFFFFF);
		// The tribulation ground sits at the map's centre; the player is drawn where they stand.
		float centreSx = cx;
		float centreSy = cy;
		float px = cx + (float) ((dx * rx + dz * rz) * perBlock);
		float py = cy - (float) ((dx * fx + dz * fz) * perBlock);
		if (ring) {
			ringOnMap(g, trib, "rx", "rz", centreX, centreZ, cx, cy, perBlock, fx, fz, rx, rz, millis);
			if (trib.contains("tx")) {
				ringOnMap(g, trib, "tx", "tz", centreX, centreZ, cx, cy, perBlock, fx, fz, rx, rz, millis);
			}
		}
		Brush.dot(g, centreSx, centreSy, 1.0F, 0xFFB9A4FF);
		float clampX = px;
		float clampY = py;
		float off = (float) Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
		if (off > radius + 2.0F) {
			clampX = cx + (px - cx) / off * (radius + 2.0F);
			clampY = cy + (py - cy) / off * (radius + 2.0F);
		}
		if (distance > amber) {
			// An arrow from the player back to the centre.
			float ax = cx - clampX;
			float ay = cy - clampY;
			float length = (float) Math.sqrt(ax * ax + ay * ay);
			if (length > 2.0F) {
				float ux = ax / length;
				float uy = ay / length;
				float tipX = clampX + ux * Math.min(length - 1.0F, 7.0F);
				float tipY = clampY + uy * Math.min(length - 1.0F, 7.0F);
				Brush.line(g, clampX, clampY, tipX, tipY, 1.0F, limitColor);
				Brush.line(g, tipX, tipY, tipX - ux * 2.5F - uy * 2.0F, tipY - uy * 2.5F + ux * 2.0F, 1.0F, limitColor);
				Brush.line(g, tipX, tipY, tipX - ux * 2.5F + uy * 2.0F, tipY - uy * 2.5F - ux * 2.0F, 1.0F, limitColor);
			}
		}
		Brush.dot(g, clampX, clampY, 1.6F, 0xFFFFFFFF);
		Component text = Component.literal(String.format(Locale.ROOT, "%.1f / %d", distance, (int) limit));
		Brush.centered(g, font, text, cx, cy + radius + 2.5F, limitColor | 0xFF000000, 0.5F, true);
	}

	private static void ringOnMap(GuiGraphicsExtractor g, CompoundTag trib, String xKey, String zKey, double centreX, double centreZ, float cx, float cy,
								  float perBlock, double fx, double fz, double rx, double rz, long millis) {
		double dx = trib.getDoubleOr(xKey, centreX) - centreX;
		double dz = trib.getDoubleOr(zKey, centreZ) - centreZ;
		float sx = cx + (float) ((dx * rx + dz * rz) * perBlock);
		float sy = cy - (float) ((dx * fx + dz * fz) * perBlock);
		float r = (float) (trib.getDoubleOr("rr", RING_RADIUS) * perBlock);
		float pulse = 0.6F + 0.4F * (float) Math.sin(millis / 120.0D);
		Brush.ring(g, sx, sy, Math.max(0.5F, r - 0.6F), r + 0.4F, Palette.argb(Math.round(220 * pulse), 0xC9B8FF));
	}

	/** A small zigzag lightning bolt. */
	private static void bolt(GuiGraphicsExtractor g, float x, float y, int color) {
		Brush.line(g, x + 1.0F, y - 3.0F, x - 1.0F, y + 0.2F, 1.0F, color);
		Brush.line(g, x - 1.0F, y + 0.2F, x + 1.0F, y - 0.2F, 1.0F, color);
		Brush.line(g, x + 1.0F, y - 0.2F, x - 0.8F, y + 3.0F, 1.0F, color);
	}
}
