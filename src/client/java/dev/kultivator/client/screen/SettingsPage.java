package dev.kultivator.client.screen;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Settings: effect quality as stacked talismans (Minimal / Normal / Maksimal / Otomatis, the chosen one stamped), and
 * on the right a scrolling column of jade-coin toggles and cycling talismans: the cosmetics, the interface (HUD,
 * hardcore breakthroughs, which asks for a second click within 3 s) and the technique settings of DESIGN 22.12.9
 * (screen shake, screen flash, phantom displays, others' effects, hold G for the wheel, release = cast, reduce motion).
 * Fits 320×240.
 */
final class SettingsPage extends Page {

	private static final int[] FLAGS = {CultivationData.COSMETIC_AURA, CultivationData.COSMETIC_ROBE, CultivationData.COSMETIC_BACK_SWORD,
			CultivationData.COSMETIC_EYES, CultivationData.COSMETIC_DAO_CIRCLE};
	private static final String[] PARTS = {"aura", "robe", "back_sword", "eyes", "dao_circle"};
	private static final long CONFIRM_MILLIS = 3000L;
	private static final String C = "kultivator.codex.settings.";
	private static final String G = "kultivator.goals.settings.";

	private long hardcoreAskUntil;
	private int leftW;
	private int rightX;
	private int rightW;
	private float descY;
	private final List<Widget> column = new ArrayList<>();
	private final List<Integer> columnY = new ArrayList<>();
	private final List<float[]> headings = new ArrayList<>();
	private final List<String> headingKeys = new ArrayList<>();
	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float columnHeight;

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
		column.clear();
		columnY.clear();
		headings.clear();
		headingKeys.clear();
		int gap = 12;
		leftW = Math.round((w - gap) * 0.42F);
		rightX = x + leftW + gap;
		rightW = w - leftW - gap;

		// Effect quality: four talismans, one above the other.
		int th = Math.max(14, Math.min(19, (h - 70) / 4));
		int ty = y + 15;
		for (EffectQuality.Level level : EffectQuality.Level.values()) {
			TalismanButton talisman = new TalismanButton(x + 2, ty, leftW - 4, th, level::displayName);
			talisman.stampedWhen(() -> !EffectQuality.auto() && EffectQuality.chosenLevel() == level);
			talisman.onPress(() -> EffectQuality.setLevel(level));
			talisman.tooltip(() -> List.of(level.displayName(), Component.translatable("kultivator.screen.quality." + level.id() + ".tip")));
			add(talisman);
			ty += th + 4;
		}
		TalismanButton auto = new TalismanButton(x + 2, ty, leftW - 4, th, () -> Component.translatable(C + "auto"));
		auto.stampedWhen(EffectQuality::auto);
		auto.onPress(() -> EffectQuality.setAuto(true));
		auto.tooltip(() -> List.of(Component.translatable(C + "auto"), Component.translatable(C + "auto.tip"),
				Component.translatable(C + "auto.now", EffectQuality.level().displayName()).withStyle(ChatFormatting.GRAY)));
		add(auto);
		ty += th + 4;
		descY = ty + 2;

		// The right column (scrolls).
		int row = Math.max(12, Math.min(16, (h - 30) / 9));
		int coin = row - 3;
		int cy = y;
		heading(cy, "kultivator.screen.settings.cosmetics");
		cy += 15;
		for (int i = 0; i < FLAGS.length; i++) {
			int flag = FLAGS[i];
			String part = PARTS[i];
			JadeCoin toggle = new JadeCoin(rightX + 2, cy, rightW - 6, coin, () -> Component.translatable("kultivator.core.visual." + part),
					() -> data().cosmetic(flag));
			toggle.enabledWhen(Keys::connected);
			toggle.onPress(() -> send(Actions.SET_COSMETIC, flag, data().cosmetic(flag) ? "0" : "1"));
			toggle.tooltip(() -> List.of(Component.translatable("kultivator.core.visual." + part),
					Component.translatable("kultivator.screen.cosmetic." + part + ".tip"),
					Component.translatable(data().cosmetic(flag) ? "kultivator.screen.cosmetic.shown" : "kultivator.screen.cosmetic.hidden")
							.withStyle(ChatFormatting.GRAY)));
			put(toggle, cy);
			cy += row;
		}
		cy += 2;
		heading(cy, "kultivator.screen.settings.interface");
		cy += 15;
		JadeCoin hud = new JadeCoin(rightX + 2, cy, rightW - 6, coin, () -> Component.translatable("kultivator.screen.settings.hud"),
				EffectQuality::hudVisible);
		hud.onPress(() -> EffectQuality.setHudVisible(!EffectQuality.hudVisible()));
		hud.tooltip(() -> List.of(Component.translatable("kultivator.screen.settings.hud"), Component.translatable("kultivator.screen.settings.hud.tip")));
		put(hud, cy);
		cy += row;
		JadeCoin hardcore = new JadeCoin(rightX + 2, cy, rightW - 6, coin, () -> Component.translatable(asking()
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
		put(hardcore, cy);
		cy += row + 2;

		// Techniques and effects (DESIGN 22.12.9).
		heading(cy, C + "techniques");
		cy += 15;
		TalismanButton shake = new TalismanButton(rightX + 2, cy, rightW - 6, coin + 2, () -> Component.translatable(C + "shake",
				Component.translatable(C + "shake." + EffectQuality.shake().id())));
		shake.onPress(() -> EffectQuality.setShake(EffectQuality.Shake.values()[(EffectQuality.shake().ordinal() + 1) % EffectQuality.Shake.values().length]));
		shake.tooltip(() -> List.of(Component.translatable(C + "shake", Component.translatable(C + "shake." + EffectQuality.shake().id())),
				Component.translatable(C + "shake.tip").withStyle(ChatFormatting.GRAY)));
		put(shake, cy);
		cy += row + 2;
		cy = coin(cy, row, coin, "flash", EffectQuality::flash, EffectQuality::setFlash);
		cy = coin(cy, row, coin, "phantoms", EffectQuality::phantoms, EffectQuality::setPhantoms);
		TalismanButton others = new TalismanButton(rightX + 2, cy, rightW - 6, coin + 2, () -> Component.translatable(C + "others",
				Component.translatable(C + "others." + EffectQuality.others().id())));
		others.onPress(() -> EffectQuality.setOthers(EffectQuality.Others.values()[(EffectQuality.others().ordinal() + 1)
				% EffectQuality.Others.values().length]));
		others.tooltip(() -> List.of(Component.translatable(C + "others", Component.translatable(C + "others." + EffectQuality.others().id())),
				Component.translatable(C + "others.tip").withStyle(ChatFormatting.GRAY)));
		put(others, cy);
		cy += row + 2;
		cy = coin(cy, row, coin, "wheel_hold", EffectQuality::wheelHold, EffectQuality::setWheelHold);
		cy = coin(cy, row, coin, "release_cast", EffectQuality::releaseCast, EffectQuality::setReleaseCast);
		cy = coin(cy, row, coin, "reduce_motion", EffectQuality::reduceMotion, EffectQuality::setReduceMotion);

		// Phase 7 HUD comfort (DESIGN 19.3.15): the cooldown ping, the meditation danger alert and the guide tracker.
		cy += 2;
		heading(cy, G + "heading");
		cy += 15;
		cy = goalCoin(cy, row, coin, "cooldown_ping", EffectQuality::cooldownPing, EffectQuality::setCooldownPing);
		cy = goalCoin(cy, row, coin, "danger_alert", EffectQuality::dangerAlert, EffectQuality::setDangerAlert);
		JadeCoin guide = new JadeCoin(rightX + 2, cy, rightW - 6, coin, () -> Component.translatable(G + "guide"),
				() -> !dev.kultivator.goals.Guide.hidden(data()));
		guide.enabledWhen(Keys::connected);
		guide.onPress(() -> send(Actions.JALAN, 0, dev.kultivator.goals.Guide.hidden(data()) ? "show" : "hide"));
		guide.tooltip(() -> List.of(Component.translatable(G + "guide"), Component.translatable(G + "guide.tip").withStyle(ChatFormatting.GRAY)));
		put(guide, cy);
		cy += row;

		// Phase 7b Momen Epik (DESIGN 23.2, 23.5.1 item 9): rows from EpicSettings; on/off rows are jade coins, the
		// camera mode and the VFX scale cycle like "Guncangan layar". Must survive every edit (23.17.5).
		List<dev.kultivator.client.epic.EpicSettings.Row> epicRows = dev.kultivator.client.epic.EpicSettings.rows();
		if (!epicRows.isEmpty()) {
			cy += 2;
			heading(cy, "kultivator.spine7b.settings.heading");
			cy += 15;
			for (dev.kultivator.client.epic.EpicSettings.Row epic : epicRows) {
				if (epic.cycle()) {
					TalismanButton cycle = new TalismanButton(rightX + 2, cy, rightW - 6, coin + 2, epic.value());
					cycle.onPress(epic.next());
					cycle.tooltip(epic.tooltip());
					put(cycle, cy);
					cy += row + 2;
				} else {
					String key = epic.key();
					JadeCoin toggle = new JadeCoin(rightX + 2, cy, rightW - 6, coin, epic.value(),
							() -> dev.kultivator.client.epic.EpicSettings.flag(key));
					toggle.onPress(epic.next());
					toggle.tooltip(epic.tooltip());
					put(toggle, cy);
					cy += row;
				}
			}
		}
		// Voices (module V): Suara bicara, Teks suara and the VOICEVOX credits (a click plays a sample line).
		List<dev.kultivator.client.voice.VoiceSettings.Row> voiceRows = dev.kultivator.client.voice.VoiceSettings.rows();
		if (!voiceRows.isEmpty()) {
			cy += 2;
			heading(cy, dev.kultivator.client.voice.VoiceSettings.heading());
			cy += 15;
			for (dev.kultivator.client.voice.VoiceSettings.Row voice : voiceRows) {
				if (voice.cycle()) {
					TalismanButton cycle = new TalismanButton(rightX + 2, cy, rightW - 6, coin + 2, voice.value());
					cycle.onPress(voice.next());
					cycle.tooltip(voice.tooltip());
					put(cycle, cy);
					cy += row + 2;
				} else {
					JadeCoin toggle = new JadeCoin(rightX + 2, cy, rightW - 6, coin, voice.value(), voice.on());
					toggle.onPress(voice.next());
					toggle.tooltip(voice.tooltip());
					put(toggle, cy);
					cy += row;
				}
			}
		}
		columnHeight = cy - y + 4;
	}

	private int goalCoin(int cy, int row, int coin, String key, BooleanSupplier value, Consumer<Boolean> set) {
		JadeCoin toggle = new JadeCoin(rightX + 2, cy, rightW - 6, coin, () -> Component.translatable(G + key), value);
		toggle.onPress(() -> set.accept(!value.getAsBoolean()));
		toggle.tooltip(() -> List.of(Component.translatable(G + key), Component.translatable(G + key + ".tip").withStyle(ChatFormatting.GRAY)));
		put(toggle, cy);
		return cy + row;
	}

	private int coin(int cy, int row, int coin, String key, BooleanSupplier value, Consumer<Boolean> set) {
		JadeCoin toggle = new JadeCoin(rightX + 2, cy, rightW - 6, coin, () -> Component.translatable(C + key), value);
		toggle.onPress(() -> set.accept(!value.getAsBoolean()));
		toggle.tooltip(() -> List.of(Component.translatable(C + key), Component.translatable(C + key + ".tip").withStyle(ChatFormatting.GRAY)));
		put(toggle, cy);
		return cy + row;
	}

	private void put(Widget widget, int baseY) {
		add(widget);
		column.add(widget);
		columnY.add(baseY);
	}

	private void heading(int baseY, String key) {
		headings.add(new float[]{baseY});
		headingKeys.add(key);
	}

	private boolean asking() {
		return Util.getMillis() < hardcoreAskUntil;
	}

	@Override
	protected boolean visible(Widget widget) {
		if (!column.contains(widget)) {
			return true;
		}
		return widget.y + widget.h > y && widget.y < y + h;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		heading(g, Component.translatable("kultivator.screen.settings.quality"), x + 2.0F, y, leftW - 4.0F, 0.85F);
		EffectQuality.Level level = EffectQuality.level();
		float room = y + h - descY;
		if (room > 10.0F) {
			Component tip = EffectQuality.auto() ? Component.translatable(C + "auto.tip") : Component.translatable("kultivator.screen.quality." + level.id()
					+ ".tip");
			Brush.wrap(g, font, tip, x + 4.0F, descY, leftW - 8.0F, Palette.INK_SOFT, 0.68F, false);
		}
		float maxScroll = Math.max(0.0F, columnHeight - h);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		int offset = Math.round(scrollShown.to(scroll, seconds));
		for (int i = 0; i < column.size(); i++) {
			column.get(i).y = columnY.get(i) - offset;
		}
		g.enableScissor(x, y, x + w, y + h);
		for (int i = 0; i < headings.size(); i++) {
			float hy = headings.get(i)[0] - offset;
			if (hy + 12.0F > y && hy < y + h) {
				heading(g, Component.translatable(headingKeys.get(i)), rightX + 2.0F, hy, rightW - 6.0F, 0.85F);
			}
		}
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		g.disableScissor();
		float maxScroll = Math.max(0.0F, columnHeight - h);
		if (maxScroll > 0.0F) {
			float bead = y + 3.0F + (h - 6.0F) * (scrollShown.get() / maxScroll);
			g.fill(x + w - 2, y, x + w - 1, y + h, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, x + w - 2.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY) && mouseX >= rightX - 4) {
			scroll -= (float) amount * 14.0F;
			return true;
		}
		return false;
	}
}
