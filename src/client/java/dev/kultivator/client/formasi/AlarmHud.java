package dev.kultivator.client.formasi;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Formation HUD (DESIGN 24.7.4, 24.4.7, 24.4.10): the {@code AlarmPing} arrows at the screen edge pointing at the
 * intruders of an alarm for 8 s (copper for an approach, red for a breach, violet for a stranger, gold for an elite or a
 * boss, with the count), and the trip channel ring at the bottom centre while we channel a transport node. Hidden with
 * F1; drawn under the chat.
 */
public final class AlarmHud implements HudElement {

	/** Colour per {@code AlarmKind} ordinal: APPROACH, BREACH, STRANGER, ELITE, BOSS. */
	private static final int[] KIND_COLOR = {0xE0A060, 0xFF4A3A, 0xC08CFF, 0xFFD54A, 0xFF7A20};
	private static final int MAX_PINGS = 4;

	private record Ping(Vec3 at, int kind, int count, long until) {
	}

	private static final List<Ping> PINGS = new ArrayList<>();
	private static long channelUntil;
	private static float channelDone;
	private static float channelLength;
	private static long channelSeen;

	/** An alarm arrow for {@code seconds}. */
	static void ping(Vec3 at, int kind, int count, float seconds) {
		if (PINGS.size() >= MAX_PINGS) {
			PINGS.remove(0);
		}
		PINGS.add(new Ping(at, Math.max(0, Math.min(KIND_COLOR.length - 1, kind)), count, Util.getMillis() + Math.round(Math.max(1.0F, seconds) * 1000.0F)));
	}

	/** A channel tick near us: {@code done} of {@code length} ticks. */
	static void channel(float done, float length) {
		channelDone = done;
		channelLength = Math.max(1.0F, length);
		channelSeen = Util.getMillis();
		channelUntil = channelSeen + 700L;
	}

	static void tick(Minecraft minecraft) {
		long now = Util.getMillis();
		PINGS.removeIf(ping -> ping.until() < now);
	}

	static void clear() {
		PINGS.clear();
		channelUntil = 0L;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		long now = Util.getMillis();
		int width = g.guiWidth();
		int height = g.guiHeight();
		if (!PINGS.isEmpty()) {
			Vec3 eye = player.getEyePosition();
			float yaw = minecraft.gameRenderer.mainCamera().yRot();
			Iterator<Ping> it = PINGS.iterator();
			int row = 0;
			while (it.hasNext()) {
				Ping ping = it.next();
				double dx = ping.at().x - eye.x;
				double dz = ping.at().z - eye.z;
				// The bearing of the intruders relative to where we look: 0 = straight ahead, clockwise.
				double bearing = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
				double rad = Math.toRadians(bearing);
				float fade = Math.min(1.0F, (ping.until() - now) / 800.0F);
				float pulse = EffectQuality.reduceMotion() ? 1.0F : 0.75F + 0.25F * (float) Math.sin(now / 140.0D + row);
				int color = Palette.argb(Math.round(230 * fade * pulse), KIND_COLOR[ping.kind()]);
				float cx = width / 2.0F;
				float cy = height / 2.0F;
				float rx = width / 2.0F - 22.0F;
				float ry = height / 2.0F - 22.0F;
				float sx = (float) Math.sin(rad);
				float sy = (float) -Math.cos(rad);
				float scale = Math.min(rx / Math.max(0.001F, Math.abs(sx)), ry / Math.max(0.001F, Math.abs(sy)));
				float ax = cx + sx * scale;
				float ay = cy + sy * scale;
				arrow(g, ax, ay, sx, sy, color);
				Component label = Component.literal("⚠ " + ping.count());
				Brush.centered(g, minecraft.font, label, ax - sx * 14.0F, ay - sy * 14.0F - 3.0F, color, 0.75F, true);
				row++;
			}
		}
		if (channelUntil > now && channelLength > 0.0F) {
			float progress = Math.min(1.0F, channelDone / channelLength + (now - channelSeen) / 50.0F / channelLength);
			float cx = width / 2.0F;
			float cy = height - 62.0F;
			Brush.ring(g, cx, cy, 9.0F, 10.5F, Palette.argb(0x60, 0x1A1030));
			Brush.arc(g, cx, cy, 8.5F, 11.0F, 0.0F, progress, Palette.opaque(0xD8C8FF));
			Brush.disc(g, cx, cy, 2.0F + 2.0F * progress, Palette.argb(0xC0, 0xFFFFFF));
		}
	}

	/** A small triangle at (x, y) pointing along (dx, dy). */
	private static void arrow(GuiGraphicsExtractor g, float x, float y, float dx, float dy, int color) {
		float px = -dy;
		float py = dx;
		float len = 9.0F;
		float half = 5.0F;
		float bx = x - dx * len;
		float by = y - dy * len;
		Brush.line(g, bx + px * half, by + py * half, x, y, 2.0F, color);
		Brush.line(g, bx - px * half, by - py * half, x, y, 2.0F, color);
		Brush.line(g, bx + px * half, by + py * half, bx - px * half, by - py * half, 1.5F, color);
		Brush.disc(g, x, y, 1.6F, color);
	}
}
