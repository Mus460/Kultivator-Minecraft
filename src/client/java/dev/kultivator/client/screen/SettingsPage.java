package dev.kultivator.client.screen;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.CultivationData;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Settings: effect quality as three stacked talismans (the chosen one stamped), and the cosmetics, the HUD and
 * hardcore breakthroughs as jade-coin toggles. Hardcore asks for a second click within 3 s.
 */
final class SettingsPage extends Page {

	private static final int[] FLAGS = {CultivationData.COSMETIC_AURA, CultivationData.COSMETIC_ROBE, CultivationData.COSMETIC_BACK_SWORD,
			CultivationData.COSMETIC_EYES, CultivationData.COSMETIC_DAO_CIRCLE};
	private static final String[] PARTS = {"aura", "robe", "back_sword", "eyes", "dao_circle"};
	private static final long CONFIRM_MILLIS = 3000L;

	private long hardcoreAskUntil;
	private int leftW;
	private int rightX;
	private int rightW;
	private float descY;
	private float cosmeticsY;
	private float interfaceY;

	SettingsPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.screen.tab.settings");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.screen.tab.settings.short");
	}

	@Override
	protected void build() {
		int gap = 12;
		leftW = Math.round((w - gap) * 0.46F);
		rightX = x + leftW + gap;
		rightW = w - leftW - gap;

		// Effect quality: three talismans, one above the other.
		int th = Math.max(15, Math.min(20, (h - 60) / 3));
		int ty = y + 15;
		for (EffectQuality.Level level : EffectQuality.Level.values()) {
			TalismanButton talisman = new TalismanButton(x + 2, ty, leftW - 4, th, level::displayName);
			talisman.stampedWhen(() -> EffectQuality.level() == level);
			talisman.onPress(() -> EffectQuality.setLevel(level));
			talisman.tooltip(() -> List.of(level.displayName(), Component.translatable("kultivator.screen.quality." + level.id() + ".tip")));
			add(talisman);
			ty += th + 5;
		}
		descY = ty + 2;

		// Cosmetics, then the interface toggles.
		int row = Math.max(12, Math.min(17, (h - 30) / 8));
		int coin = row - 3;
		cosmeticsY = y;
		int cy = y + 15;
		for (int i = 0; i < FLAGS.length; i++) {
			int flag = FLAGS[i];
			String part = PARTS[i];
			JadeCoin toggle = new JadeCoin(rightX + 2, cy, rightW - 4, coin, () -> Component.translatable("kultivator.core.visual." + part),
					() -> data().cosmetic(flag));
			toggle.enabledWhen(Keys::connected);
			toggle.onPress(() -> send(Actions.SET_COSMETIC, flag, data().cosmetic(flag) ? "0" : "1"));
			toggle.tooltip(() -> List.of(Component.translatable("kultivator.core.visual." + part),
					Component.translatable("kultivator.screen.cosmetic." + part + ".tip"),
					Component.translatable(data().cosmetic(flag) ? "kultivator.screen.cosmetic.shown" : "kultivator.screen.cosmetic.hidden")
							.withStyle(ChatFormatting.GRAY)));
			add(toggle);
			cy += row;
		}
		interfaceY = cy + 2;
		cy += 17;
		JadeCoin hud = new JadeCoin(rightX + 2, cy, rightW - 4, coin, () -> Component.translatable("kultivator.screen.settings.hud"),
				EffectQuality::hudVisible);
		hud.onPress(() -> EffectQuality.setHudVisible(!EffectQuality.hudVisible()));
		hud.tooltip(() -> List.of(Component.translatable("kultivator.screen.settings.hud"), Component.translatable("kultivator.screen.settings.hud.tip")));
		add(hud);
		cy += row;
		JadeCoin hardcore = new JadeCoin(rightX + 2, cy, rightW - 4, coin, () -> Component.translatable(asking()
				? "kultivator.screen.settings.hardcore.confirm" : "kultivator.screen.settings.hardcore"), () -> data().hardcore());
		hardcore.askingWhen(this::asking);
		hardcore.enabledWhen(Keys::connected);
		hardcore.onPress(() -> {
			if (asking()) {
				send(Actions.SET_HARDCORE, data().hardcore() ? 0 : 1, "");
				hardcoreAskUntil = 0L;
			} else {
				hardcoreAskUntil = Util.getMillis() + CONFIRM_MILLIS;
			}
		});
		hardcore.tooltip(() -> List.of(Component.translatable("kultivator.screen.settings.hardcore"),
				Component.translatable("kultivator.screen.settings.hardcore.tip"),
				Component.translatable(data().hardcore() ? "kultivator.screen.settings.hardcore.is_on" : "kultivator.screen.settings.hardcore.is_off")
						.withStyle(data().hardcore() ? ChatFormatting.RED : ChatFormatting.GRAY),
				Component.translatable("kultivator.screen.settings.hardcore.how").withStyle(ChatFormatting.DARK_GRAY)));
		add(hardcore);
	}

	private boolean asking() {
		return Util.getMillis() < hardcoreAskUntil;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		heading(g, Component.translatable("kultivator.screen.settings.quality"), x + 2.0F, y, leftW - 4.0F, 0.85F);
		heading(g, Component.translatable("kultivator.screen.settings.cosmetics"), rightX + 2.0F, cosmeticsY, rightW - 4.0F, 0.85F);
		heading(g, Component.translatable("kultivator.screen.settings.interface"), rightX + 2.0F, interfaceY, rightW - 4.0F, 0.85F);
		// What the chosen quality means, written under the talismans.
		EffectQuality.Level level = EffectQuality.level();
		float room = y + h - descY;
		if (room > 10.0F) {
			Brush.wrap(g, font, Component.translatable("kultivator.screen.quality." + level.id() + ".tip"), x + 4.0F, descY, leftW - 8.0F,
					Palette.INK_SOFT, 0.7F, false);
		}
	}
}
