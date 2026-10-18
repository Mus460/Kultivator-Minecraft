package dev.kultivator.client.elite;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.EliteFx;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.elite.EliteData;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The name plate of the elite under the crosshair (within 24 blocks, in sight): "Binatang Iblis · Api", the mob, a health
 * line and the affix, drawn as a small ink-and-gold slip below the crosshair. Holding sneak unfolds the Sumber lines of
 * the tier and the affix (DESIGN 19.1.4). Elites carry no custom name (so they despawn like normal mobs), which is why the
 * plate is part of the HUD. Hidden with F1.
 */
final class ElitePlate implements HudElement {

	private static final double RANGE = 24.0D;
	private static final double AIM = 0.985D;
	private static final int GOLD = 0xD4A637;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || minecraft.gui.hud.isHidden() || !EffectQuality.hudVisible()) {
			return;
		}
		Mob mob = lookedAt(player);
		EliteData data = mob == null ? null : EliteData.of(mob);
		if (data == null) {
			return;
		}
		Font font = minecraft.font;
		int color = data.elementValue().color();
		Component title = Brush.bold(data.title());
		Component sub = data.affix() == EliteData.NONE ? mob.getType().getDescription()
				: Component.translatable("kultivator.combat.plate.sub", mob.getType().getDescription(), EliteData.affixName(data.affix()));
		Component hp = Component.translatable("kultivator.combat.plate.hp", Math.round(Math.ceil(mob.getHealth())), Math.round(mob.getMaxHealth()));
		float titleScale = 0.8F;
		float subScale = 0.6F;
		int width = (int) Math.max(font.width(title) * titleScale, Math.max(font.width(sub), font.width(hp)) * subScale) + 12;
		width = Math.max(70, Math.min(width, g.guiWidth() - 20));
		int cx = g.guiWidth() / 2;
		int x0 = cx - width / 2;
		int y0 = g.guiHeight() / 2 + 14;
		int height = 26;
		Brush.chamfer(g, x0, y0, x0 + width, y0 + height, 2, Palette.argb(0xB8, 0x0A1322));
		Brush.frame(g, x0 + 1, y0 + 1, x0 + width - 1, y0 + height - 1, Palette.argb(0xA0, GOLD));
		Brush.rect(g, x0 + 3.0F, y0 + 3.0F, x0 + 5.0F, y0 + height - 3.0F, Palette.argb(0xE0, color));
		Brush.fitCentered(g, font, title, cx, y0 + 3.0F, width - 12, Palette.argb(0xFF, color), titleScale, 0.5F, true);
		Brush.fitCentered(g, font, sub, cx, y0 + 11.0F, width - 12, 0xFFE6EDDB, subScale, 0.4F, true);
		float fraction = Math.max(0.0F, Math.min(1.0F, mob.getHealth() / Math.max(1.0F, mob.getMaxHealth())));
		float barX0 = x0 + 8.0F;
		float barX1 = x0 + width - 8.0F;
		Brush.rect(g, barX0, y0 + 19.0F, barX1, y0 + 21.0F, 0x80000000);
		Brush.rect(g, barX0, y0 + 19.0F, barX0 + (barX1 - barX0) * fraction, y0 + 21.0F, Palette.argb(0xFF, 0xD5322A));
		Brush.centered(g, font, hp, cx, y0 + 22.0F, 0xFFCADBC4, 0.45F, false);
		if (player.isShiftKeyDown()) {
			float y = y0 + height + 2.0F;
			int wrap = Math.min(220, g.guiWidth() - 20);
			Component tier = Component.translatable("kultivator.combat.tier." + EliteData.TIER_IDS[data.tier()] + ".source")
					.withStyle(ChatFormatting.ITALIC);
			y += Brush.wrap(g, font, tier, cx - wrap / 2.0F, y, wrap, 0xFFE6EDDB, 0.55F, true) + 1.0F;
			if (data.affix() != EliteData.NONE) {
				Component affix = Component.translatable("kultivator.combat.affix." + EliteData.AFFIX_IDS[data.affix()] + ".source");
				Brush.wrap(g, font, affix, cx - wrap / 2.0F, y, wrap, 0xFFE6EDDB, 0.55F, true);
			}
		} else {
			Brush.centered(g, font, Component.translatable("kultivator.combat.plate.sneak"), cx, y0 + height + 2.0F, 0x90CADBC4, 0.45F, false);
		}
	}

	/** The elite nearest the crosshair direction within 24 blocks and in sight, or null. */
	private static @Nullable Mob lookedAt(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Mob best = null;
		double bestDot = AIM;
		for (Mob mob : EliteFx.elites()) {
			if (mob.isRemoved() || !mob.isAlive() || mob.isInvisible()) {
				continue;
			}
			Vec3 to = mob.getBoundingBox().getCenter().subtract(eye);
			double distSq = to.lengthSqr();
			if (distSq > RANGE * RANGE || distSq < 1.0E-4D) {
				continue;
			}
			double dot = to.normalize().dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = mob;
			}
		}
		return best != null && player.hasLineOfSight(best) ? best : null;
	}
}
