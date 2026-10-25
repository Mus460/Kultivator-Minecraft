package dev.kultivator.client.dunia.kapal;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.peta.PetaUi;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ship HUD (DESIGN 34.5.15): while the player rides a ship, a small slip at the bottom left with the class,
 * its state (berlabuh, terbang, autopilot, lompatan awan, bajak langit, rusak), speed, height above the ground, fuel time,
 * the hull bar, the voyage's destination and distance, and for the captain the keys (text at 1.0, DESIGN 43.9; the spirit
 * weather line went to the {@code LangitHud} chip and the map's layer, G57), on the single HUD slip {@link Kabar#slip}
 * (43.9: jade paper, ink text, no shadow). Also the plain fade of a cloud leap
 * when Kamera sinematik is Mati. GUI fills only; hidden by F1.
 */
final class KapalHud implements HudElement {

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		int width = g.guiWidth();
		int height = g.guiHeight();
		float fade = KapalClient.fade();
		if (fade > 0.0F) {
			g.fill(0, 0, width, height, Palette.argb(Math.round(230 * fade), 0x0A0E14));
		}
		// the boarding fade above always plays; the ship panel follows the "HUD Kultivator" coin (audit M5, S30)
		if (minecraft.player == null || !EffectQuality.hudVisible()) {
			return;
		}
		KapalRohEntity ship = KapalClient.riding();
		if (ship == null) {
			return;
		}
		Font font = minecraft.font;
		KapalClass kind = ship.kind();
		boolean captain = KapalClient.atHelm(ship);
		// text at 1.0 (43.9): the tablet widens with the screen; lines that still do not fit are cut with "…"
		int w = Mth.clamp(Math.round(width * 0.45F), 150, 210);
		int tx = 6 + 6;
		int tw = w - 12;
		Component keys = captain ? KeyHints.fill(Component.translatable("kultivator.kapal.hud.keys", minecraft.options.keyUp.getTranslatedKeyMessage(),
				minecraft.options.keyDown.getTranslatedKeyMessage(), minecraft.options.keyLeft.getTranslatedKeyMessage(),
				minecraft.options.keyRight.getTranslatedKeyMessage(), minecraft.options.keyJump.getTranslatedKeyMessage(),
				minecraft.options.keySprint.getTranslatedKeyMessage())) : Component.empty();
		int keysH = captain ? Math.round(Brush.wrapHeight(font, keys, tw, 1.0F)) : 0;
		int h = 4 + 4 * 10 + (captain ? 2 + keysH + 10 : 0) + 2;
		int x0 = 6;
		int y1 = height - 30;
		int y0 = y1 - h;
		Kabar.slip(g, x0, y0, x0 + w, y1, stateColor(ship));
		int ty = y0 + 4;
		Component state = stateName(ship);
		int stateW = Math.min(tw / 2, font.width(state));
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.kapal.class." + kind.model())), tx, ty, tw - stateW - 4, Palette.SEAL_DARK,
				1.0F, 1.0F, false);
		Brush.fit(g, font, state, x0 + w - 6 - stateW, ty, stateW, stateColor(ship), 1.0F, 1.0F, false);
		ty += 10;
		float bps = Math.abs(ship.speed()) * 20.0F;
		int above = height(minecraft, ship);
		Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.motion", Math.round(bps), above), tx, ty, tw, Palette.INK, 1.0F, 1.0F, false);
		ty += 10;
		Component fuel = Component.translatable("kultivator.kapal.hud.fuel", PetaUi.duration(ship.fuelSeconds()));
		int fuelW = Math.min(tw - 30, font.width(fuel));
		Brush.fit(g, font, fuel, tx, ty, fuelW, ship.fuelSeconds() < 30 ? Palette.SEAL : Palette.INK_SOFT, 1.0F, 1.0F, false);
		float hull = kind.hull() <= 0 ? 1.0F : ship.hull() / (float) kind.hull();
		int barColor = hull > 0.5F ? Palette.JADE : hull > 0.25F ? Palette.WARN : Palette.SEAL;
		PetaUi.bar(g, tx + fuelW + 4, ty + 2, tw - fuelW - 4, 5, hull, barColor, 0x40000000);
		ty += 10;
		// the spirit weather under the hull is no longer repeated here (G57): the LangitHud chip and the map's layer show it
		String dest = ship.destination();
		if (!dest.isEmpty()) {
			String[] parts = dest.split(" ", 3);
			try {
				double dx = Double.parseDouble(parts[0]) - ship.getX();
				double dz = Double.parseDouble(parts[1]) - ship.getZ();
				int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
				String name = parts.length > 2 && !parts[2].isBlank() ? parts[2] : parts[0] + ", " + parts[1];
				Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.dest", name, distance), tx, ty, tw, Palette.JADE_DARK, 1.0F, 1.0F, false);
			} catch (RuntimeException ignored) {
				// a broken destination line: skipped
			}
		} else if (ship.disciplesAboard() > 0) {
			Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.disciples", ship.disciplesAboard()), tx, ty, tw, Palette.JADE_DARK, 1.0F, 1.0F, false);
		} else {
			Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.riders", ship.getPassengers().size(), kind.seats()), tx, ty, tw, Palette.INK_SOFT,
					1.0F, 1.0F, false);
		}
		if (captain) {
			ty += 12;
			Brush.wrap(g, font, keys, tx, ty, tw, Palette.INK_FAINT, 1.0F, false);
			ty += keysH;
			Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.helm", minecraft.options.keyInventory.getTranslatedKeyMessage(),
					minecraft.options.keyShift.getTranslatedKeyMessage()), tx, ty, tw, Palette.INK_FAINT, 1.0F, 1.0F, false);
		}
	}

	static Component stateName(KapalRohEntity ship) {
		if (ship.docked()) {
			return Component.translatable("kultivator.kapal.state.docked");
		}
		return Component.translatable("kultivator.kapal.state." + switch (ship.state()) {
			case KapalRohEntity.STATE_FLY -> "fly";
			case KapalRohEntity.STATE_AUTO -> "auto";
			case KapalRohEntity.STATE_LEAP -> "leap";
			case KapalRohEntity.STATE_BROKEN -> "broken";
			case KapalRohEntity.STATE_HALT -> "halt";
			default -> "idle";
		});
	}

	/** State colour as ink on the paper slip (also the slip's left strip). */
	static int stateColor(KapalRohEntity ship) {
		if (ship.docked()) {
			return Palette.JADE_DARK;
		}
		return switch (ship.state()) {
			case KapalRohEntity.STATE_AUTO, KapalRohEntity.STATE_LEAP -> 0xFF1F5A8A;
			case KapalRohEntity.STATE_BROKEN, KapalRohEntity.STATE_HALT -> Palette.SEAL;
			default -> Palette.INK_SOFT;
		};
	}

	/** Blocks between the keel and the ground below (client heightmap; 0 when unknown). */
	private static int height(Minecraft minecraft, KapalRohEntity ship) {
		if (minecraft.level == null) {
			return 0;
		}
		int top = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(ship.getX()), Mth.floor(ship.getZ()));
		return top <= minecraft.level.getMinY() ? 0 : Math.max(0, Mth.floor(ship.getY()) - top);
	}
}
