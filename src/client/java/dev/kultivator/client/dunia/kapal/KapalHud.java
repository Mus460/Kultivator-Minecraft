package dev.kultivator.client.dunia.kapal;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.peta.PetaUi;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.kapal.KapalWeather;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ship HUD (DESIGN 34.5.15): while the player rides a ship, a small lacquer tablet at the bottom left with the class,
 * its state (berlabuh, terbang, autopilot, lompatan awan, bajak langit, rusak), speed, height above the ground, fuel time,
 * the hull bar, the spirit weather under the hull when there is one (and its fuel and speed factors), the voyage's
 * destination and distance, and for the captain the keys. Also the plain fade of a cloud leap
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
		SpiritWeather weather = ship.weather();
		boolean misty = weather != SpiritWeather.CLEAR;
		int w = 156;
		int h = (captain ? 70 : 52) + (misty ? 9 : 0);
		int x0 = 6;
		int y1 = height - 30;
		int y0 = y1 - h;
		PetaUi.lacquer(g, x0, y0, x0 + w, y1, 0.92F);
		int tx = x0 + 6;
		int ty = y0 + 4;
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.kapal.class." + kind.model())), tx, ty, w - 70, Palette.GOLD_LIGHT, 0.75F,
				0.5F, true);
		Component state = stateName(ship);
		Brush.fit(g, font, state, x0 + w - 66, ty, 62, stateColor(ship), 0.65F, 0.45F, true);
		ty += 10;
		float bps = Math.abs(ship.speed()) * 20.0F;
		int above = height(minecraft, ship);
		Component line = Component.translatable("kultivator.kapal.hud.motion", Math.round(bps), above);
		Brush.fit(g, font, line, tx, ty, w - 12, 0xFFDCE8E0, 0.62F, 0.45F, true);
		ty += 9;
		Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.fuel", PetaUi.duration(ship.fuelSeconds())), tx, ty, 70,
				ship.fuelSeconds() < 30 ? 0xFFFF8A7A : 0xFFE8D9A8, 0.62F, 0.45F, true);
		float hull = kind.hull() <= 0 ? 1.0F : ship.hull() / (float) kind.hull();
		int barColor = hull > 0.5F ? 0xFF67C99B : hull > 0.25F ? 0xFFE5821C : 0xFFD5322A;
		PetaUi.bar(g, x0 + 78, ty + 1, w - 84, 5, hull, barColor, 0x80000000);
		ty += 9;
		if (misty) {
			// module L's spirit weather under the hull and what it does to the formation
			Component line2 = Component.translatable("kultivator.kapal.hud.weather", Component.translatable("kultivator.kapal.weather.name." + weather.id()),
					Math.round(KapalWeather.fuel(weather) * 100.0D), Math.round(KapalWeather.speed(weather) * 100.0D));
			Brush.fit(g, font, line2, tx, ty, w - 12, 0xFFC9B6FF, 0.6F, 0.45F, true);
			ty += 9;
		}
		String dest = ship.destination();
		if (!dest.isEmpty()) {
			String[] parts = dest.split(" ", 3);
			try {
				double dx = Double.parseDouble(parts[0]) - ship.getX();
				double dz = Double.parseDouble(parts[1]) - ship.getZ();
				int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
				String name = parts.length > 2 && !parts[2].isBlank() ? parts[2] : parts[0] + ", " + parts[1];
				Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.dest", name, distance), tx, ty, w - 12, 0xFFB4E2FF, 0.62F, 0.45F,
						true);
			} catch (RuntimeException ignored) {
				// a broken destination line: skipped
			}
		} else if (ship.disciplesAboard() > 0) {
			Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.disciples", ship.disciplesAboard()), tx, ty, w - 12, 0xFF9FE0C0,
					0.62F, 0.45F, true);
		} else {
			Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.riders", ship.getPassengers().size(), kind.seats()), tx, ty, w - 12,
					0xFFA8B4AE, 0.62F, 0.45F, true);
		}
		if (captain) {
			ty += 10;
			Component keys = KeyHints.fill(Component.translatable("kultivator.kapal.hud.keys", minecraft.options.keyUp.getTranslatedKeyMessage(),
					minecraft.options.keyDown.getTranslatedKeyMessage(), minecraft.options.keyLeft.getTranslatedKeyMessage(),
					minecraft.options.keyRight.getTranslatedKeyMessage(), minecraft.options.keyJump.getTranslatedKeyMessage(),
					minecraft.options.keySprint.getTranslatedKeyMessage()));
			Brush.wrap(g, font, keys, tx, ty, w - 12, 0xFF93A39B, 0.55F, true);
			ty += 9;
			Brush.fit(g, font, Component.translatable("kultivator.kapal.hud.helm", minecraft.options.keyInventory.getTranslatedKeyMessage(),
					minecraft.options.keyShift.getTranslatedKeyMessage()), tx, ty + 3, w - 12, 0xFF93A39B, 0.55F, 0.4F, true);
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

	static int stateColor(KapalRohEntity ship) {
		if (ship.docked()) {
			return 0xFF9FE0C0;
		}
		return switch (ship.state()) {
			case KapalRohEntity.STATE_AUTO, KapalRohEntity.STATE_LEAP -> 0xFFB4E2FF;
			case KapalRohEntity.STATE_BROKEN, KapalRohEntity.STATE_HALT -> 0xFFFF6A5A;
			default -> 0xFFE8E0C0;
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
