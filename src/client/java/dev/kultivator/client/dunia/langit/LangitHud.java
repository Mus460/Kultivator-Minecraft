package dev.kultivator.client.dunia.langit;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.langit.HartaLahirEntity;
import dev.kultivator.dunia.langit.LangitFxPayload;
import dev.kultivator.dunia.langit.LangitSkyPayload;
import dev.kultivator.dunia.langit.TreasureRewards;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The HUD of module L (DESIGN 34.5.8-34.5.10): the faint red vignette of Gerhana Darah (0.03 / 0.05 / 0.07 by Effect
 * Quality), the soft mist vignette of the Qi mists (0.04 / 0.08 / 0.12, violet at Fajar Ungu), the chips at the top left
 * ("Malam ini: Gerhana Darah", the running event with its time left, the spirit weather, the nearest rainbow pillar with
 * its distance and an arrow, "dijaga" while guardians live; a Meteor Agung falling or just fallen with its arrow), the
 * short bolide flash, and the claim ring around the crosshair while the player channels a treasure. Hidden with
 * F1 and the debug screen; fills and one vignette blit only (no shader).
 */
final class LangitHud implements HudElement {

	private static final Identifier VIGNETTE = Kultivator.id("textures/gui/epic/vignette.png");
	private static final float[] ECLIPSE_ALPHA = {0.03F, 0.05F, 0.07F};
	private static final float[] MIST_ALPHA = {0.04F, 0.08F, 0.12F};
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		int width = g.guiWidth();
		int height = g.guiHeight();
		int quality = EffectQuality.level().ordinal();
		long now = Util.getMillis();

		// vignettes
		if (LangitState.shown() == SkyKind.BLOOD_ECLIPSE) {
			float beat = 0.8F + 0.2F * Mth.sin(now / 900.0F);
			float a = ECLIPSE_ALPHA[quality] * LangitState.skyWeight(partial) * beat;
			// the faint red fill of the design, deeper at the edges through the 7b vignette mask
			g.fill(0, 0, width, height, Palette.argb(Math.round(a * 255.0F), 0x8A0F0A));
			vignette(g, width, height, a * 4.0F, 0xB01810);
		}
		float omen = LangitState.sealOmen(partial);
		if (omen > 0.01F) {
			// a weak demon prison seal nearby: an ash-red edge that breathes slowly (same budget as the eclipse)
			float beat = 0.75F + 0.25F * Mth.sin(now / 1400.0F);
			vignette(g, width, height, ECLIPSE_ALPHA[quality] * 4.0F * omen * beat, 0x6A1020);
		}
		float mist = LangitState.mist(partial);
		if (mist > 0.01F) {
			int rgb = LangitRenderer.lerpRgb(0xC8E8DC, 0xC8A8F0, LangitState.dawn);
			vignette(g, width, height, MIST_ALPHA[quality] * 3.0F * mist, rgb);
			g.fillGradient(0, Math.round(height * 0.72F), width, height, Palette.argb(0, rgb),
					Palette.argb(Math.round(255.0F * MIST_ALPHA[quality] * mist), rgb));
		}
		if (LangitState.flash > 0.005F) {
			// a bolide or a Meteor Agung landing near (Effect Quality "Kilat"): a short soft flash
			g.fill(0, 0, width, height, Palette.argb(Math.round(Mth.clamp(LangitState.flash, 0.0F, 0.6F) * 255.0F), LangitState.flashRgb));
		}
		if (minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = minecraft.font;
		channelRing(g, minecraft, level, player, width, height);
		// the sky vignettes and the channel ring are the event itself; the chips follow the "HUD Kultivator" coin (audit M5, S30)
		if (!EffectQuality.hudVisible()) {
			return;
		}
		// chips at the top left
		float y = 6.0F;
		SkyKind kind = LangitState.kind();
		if (kind != null && LangitState.skyState != LangitSkyPayload.NONE) {
			Component text;
			if (LangitState.skyState == LangitSkyPayload.OMEN) {
				text = Component.translatable("kultivator.langit.hud.tonight", name(kind));
			} else {
				long left = Math.max(0L, (LangitState.skyEnd - level.getOverworldClockTime()) / 20L);
				text = Component.translatable("kultivator.langit.hud.active", name(kind), left / 60L, String.format("%02d", left % 60L));
			}
			y = chip(g, font, text, y, color(kind), glyph(kind));
		}
		SpiritWeather weather = LangitState.weather;
		if (weather != SpiritWeather.CLEAR && mist > 0.05F) {
			Component text = Component.translatable("kultivator.langit.hud.weather", Component.translatable("kultivator.langit.weather." + weather.id()));
			y = chip(g, font, text, y, weather == SpiritWeather.PURPLE_DAWN ? 0xD8B0FF : 0xA8F0D0, weather == SpiritWeather.PURPLE_DAWN ? "紫" : "霧");
		}
		LangitState.BirthView nearest = null;
		double best = Double.MAX_VALUE;
		for (LangitState.BirthView view : LangitState.BIRTHS.values()) {
			if (!view.live()) {
				continue;
			}
			Vec3 base = view.base();
			double dx = base.x - player.getX();
			double dz = base.z - player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			if (distance < best) {
				best = distance;
				nearest = view;
			}
		}
		if (nearest != null && best > 6.0D) {
			Vec3 base = nearest.base();
			int arrow = arrow(player, base);
			Component text = LangitState.guards.length > 0
					? Component.translatable("kultivator.langit.hud.pillar_guarded", ARROWS[arrow], (int) Math.round(best), LangitState.guards.length)
					: Component.translatable("kultivator.langit.hud.pillar", ARROWS[arrow], (int) Math.round(best));
			y = chip(g, font, text, y, TreasureRewards.tierColor(nearest.tier), "宝");
		}
		// a Meteor Agung falling or fallen within the last half minute: where to run
		for (LangitState.Fx fx : LangitState.FX) {
			boolean falling = fx.kind == LangitFxPayload.KIND_GREAT_INCOMING;
			if (!falling && !(fx.kind == LangitFxPayload.KIND_GREAT_IMPACT && fx.age < 600)) {
				continue;
			}
			Vec3 at = new Vec3(fx.x, fx.y, fx.z);
			double distance = Math.sqrt(Mth.square(at.x - player.getX()) + Mth.square(at.z - player.getZ()));
			if (distance < 8.0D) {
				break;
			}
			Component text = Component.translatable(falling ? "kultivator.langit.hud.great_falling" : "kultivator.langit.hud.great_landed",
					ARROWS[arrow(player, at)], (int) Math.round(distance));
			chip(g, font, text, y, 0x8FE6FF, "隕");
			break;
		}
	}

	/** The arrow index (0 ahead .. clockwise) from the player's view to {@code target}. */
	private static int arrow(Player player, Vec3 target) {
		double bearing = Math.toDegrees(Math.atan2(-(target.x - player.getX()), target.z - player.getZ()));
		double relative = Mth.wrapDegrees(bearing - player.getYRot());
		return (int) Math.floorMod(Math.round(relative / 45.0D), 8L);
	}

	private static void vignette(GuiGraphicsExtractor g, int width, int height, float alpha, int rgb) {
		int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
		if (a <= 0) {
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, 256, 256, 256, 256, Palette.argb(a, rgb));
	}

	/** A small jade-ink chip with a seal glyph; returns the y of the next chip. */
	private static float chip(GuiGraphicsExtractor g, Font font, Component text, float y, int rgb, String seal) {
		float scale = 0.85F;
		int textWidth = Math.round(font.width(text) * scale);
		int x0 = 6;
		int y0 = Math.round(y);
		int x1 = x0 + 14 + textWidth + 6;
		int y1 = y0 + 13;
		Brush.chamferGradient(g, x0, y0, x1, y1, 2, Palette.argb(0xB8, 0x10161A), Palette.argb(0xB8, 0x1A2428));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0x90, rgb));
		Brush.rect(g, x0 + 2.0F, y0 + 2.0F, x0 + 11.0F, y1 - 2.0F, Palette.argb(0xD0, Palette.darken(Palette.opaque(rgb), 0.45F)));
		Brush.centered(g, font, Component.literal(seal), x0 + 6.5F, y0 + 2.5F, 0xFFF4EAD0, 0.75F, false);
		Brush.text(g, font, text, x0 + 14.0F, y0 + 3.0F, Palette.opaque(rgb), scale, true);
		return y1 + 3.0F;
	}

	/** The claim ring around the crosshair while the local player channels a treasure. */
	private static void channelRing(GuiGraphicsExtractor g, Minecraft minecraft, ClientLevel level, Player player, int width, int height) {
		if (LangitState.BIRTHS.isEmpty()) {
			return;
		}
		for (HartaLahirEntity treasure : level.getEntitiesOfClass(HartaLahirEntity.class, player.getBoundingBox().inflate(6.0D))) {
			if (treasure.channeler() != player.getId() || treasure.channel() <= 0) {
				continue;
			}
			float progress = treasure.channel() / 100.0F;
			float cx = width / 2.0F;
			float cy = height / 2.0F;
			int rgb = TreasureRewards.tierColor(treasure.tier());
			Brush.arc(g, cx, cy, 11.0F, 13.5F, 0.0F, 1.0F, Palette.argb(0x60, 0x101418));
			Brush.arc(g, cx, cy, 11.0F, 13.5F, 0.0F, progress, Palette.opaque(rgb));
			Brush.centered(g, minecraft.font, Component.translatable("kultivator.langit.hud.channel", Math.round(progress * 100.0F)), cx, cy + 17.0F,
					Palette.opaque(rgb), 0.8F, true);
			return;
		}
	}

	private static Component name(SkyKind kind) {
		return Component.translatable("kultivator.langit.kind." + kind.id());
	}

	private static int color(SkyKind kind) {
		return switch (kind) {
			case METEOR_SHOWER -> 0x8FD8FF;
			case BLOOD_ECLIPSE -> 0xFF5A48;
			case TWIN_MOONS -> 0xD8E2FF;
		};
	}

	private static String glyph(SkyKind kind) {
		return switch (kind) {
			case METEOR_SHOWER -> "星";
			case BLOOD_ECLIPSE -> "血";
			case TWIN_MOONS -> "月";
		};
	}
}
