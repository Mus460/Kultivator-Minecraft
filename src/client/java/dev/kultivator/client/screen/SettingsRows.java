package dev.kultivator.client.screen;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import dev.kultivator.client.aura.AuraSettings;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.voice.VoiceSettings;
import dev.kultivator.data.CultivationData;
import dev.kultivator.goals.Guide;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The group table of the hub's Pengaturan section (DESIGN 33.5): which row lives in which of the five groups, and the
 * row builders. Every stored setting keeps its key, default and setter; only where it is shown changes. Every row of
 * the old single column appears exactly once (23.17.5): Tampilan & Efek (the quality column is drawn by
 * {@link SettingsPage}; cosmetics, aura, techniques, impact and world), Kamera & Film (the film rows of
 * {@link EpicSettings#rows()} and the Film Titah rows), Suara (cinematic music, aura hum, the voice rows), HUD (HUD,
 * cooldown ping, danger alert, guide tracker) and Kontrol (hold G, release = cast, the live key list, "Ubah tombol…",
 * Hardcore). An {@code EpicSettings} row whose key is not in a group table falls into Tampilan & Efek.
 */
final class SettingsRows {

	/** The group ids (the same values as {@code HubNav.GROUP_*}). */
	static final int TAMPILAN = 0;
	static final int FILM = 1;
	static final int SUARA = 2;
	static final int HUD = 3;
	static final int KONTROL = 4;
	static final String[] GROUP_IDS = {"tampilan", "film", "suara", "hud", "kontrol"};

	/** What an item is: a heading, a jade coin, a cycling talisman, a plain talisman, a text line, a note, a column break. */
	enum Type {
		HEADING, COIN, CYCLE, BUTTON, LINE, NOTE, BREAK
	}

	/**
	 * One item of a group. {@code label} is the heading, control or line text; {@code value} is the right-hand text of a
	 * line (a key name); {@code color} its ink; {@code on} lights a coin; {@code asking} pulses a coin (Hardcore).
	 */
	record Item(Type type, Supplier<Component> label, @Nullable Supplier<Component> value, @Nullable Runnable action,
				Supplier<List<Component>> tooltip, BooleanSupplier on, BooleanSupplier enabled, BooleanSupplier asking, IntSupplier color) {
	}

	private static final String P = "kultivator.pengaturan.";
	private static final String C = "kultivator.codex.settings.";
	private static final String G = "kultivator.goals.settings.";
	private static final String AURA = "kultivator.aura.setting.";
	private static final String KEY_PREFIX = "key.kultivator.";
	private static final long CONFIRM_MILLIS = 3000L;
	private static final int[] FLAGS = {CultivationData.COSMETIC_AURA, CultivationData.COSMETIC_ROBE, CultivationData.COSMETIC_BACK_SWORD,
			CultivationData.COSMETIC_EYES, CultivationData.COSMETIC_DAO_CIRCLE};
	private static final String[] PARTS = {"aura", "robe", "back_sword", "eyes", "dao_circle"};

	/** The order of the epic rows inside their group (rows not listed keep their source order after these). */
	private static final List<String> FILM_ORDER = List.of(EpicSettings.CAMERA, EpicSettings.LENS, EpicSettings.COSMIC, EpicSettings.KOSMIK3D,
			EpicSettings.BULLET, EpicSettings.SLOWMO, EpicSettings.SKYRIDE, EpicSettings.IMPACT_FRAMES, EpicSettings.SILENT_IMPACT,
			EpicSettings.LETTERBOX, EpicSettings.CARDS, EpicSettings.RECORD);
	private static final List<String> TAMPILAN_ORDER = List.of(EpicSettings.WORLD_FX, EpicSettings.IMPACT_POWER, EpicSettings.GEOMETRY,
			EpicSettings.SKY, EpicSettings.VFX);

	private final Screen hub;
	private long hardcoreAskUntil;

	SettingsRows(Screen hub) {
		this.hub = hub;
	}

	/** The group's name (the hub's sub-coin). */
	static Component groupName(int group) {
		return Component.translatable(P + "group." + GROUP_IDS[clamp(group)]);
	}

	static int clamp(int group) {
		return Math.max(TAMPILAN, Math.min(KONTROL, group));
	}

	/** The items of {@code group}, in order (Tampilan & Efek: the right column only). */
	List<Item> group(int group) {
		List<Item> items = new ArrayList<>();
		switch (clamp(group)) {
			case TAMPILAN -> tampilan(items);
			case FILM -> film(items);
			case SUARA -> suara(items);
			case HUD -> hud(items);
			default -> kontrol(items);
		}
		return items;
	}

	// ================================================================ the groups

	private void tampilan(List<Item> items) {
		items.add(heading("kultivator.screen.settings.cosmetics"));
		for (int i = 0; i < FLAGS.length; i++) {
			int flag = FLAGS[i];
			String part = PARTS[i];
			items.add(new Item(Type.COIN, () -> Component.translatable("kultivator.core.visual." + part), null,
					() -> Page.send(Actions.SET_COSMETIC, flag, Page.data().cosmetic(flag) ? "0" : "1"),
					() -> List.of(Component.translatable("kultivator.core.visual." + part),
							Component.translatable("kultivator.screen.cosmetic." + part + ".tip"),
							Component.translatable(Page.data().cosmetic(flag) ? "kultivator.screen.cosmetic.shown" : "kultivator.screen.cosmetic.hidden")
									.withStyle(ChatFormatting.GRAY)),
					() -> Page.data().cosmetic(flag), Keys::connected, SettingsRows::no, SettingsRows::ink));
		}
		// Aura Kultivasi (DESIGN 31.5): the mode cycles, others' auras is a coin (the hum lives in Suara).
		items.add(heading(AURA + "heading"));
		items.add(cycle(() -> Component.translatable(AURA + "mode", Component.translatable(AURA + "mode." + AuraSettings.mode().id())),
				AuraSettings::cycleMode, SettingsRows::auraModeTooltip));
		items.add(coin(() -> Component.translatable(AURA + "others"), AuraSettings::others, AuraSettings::setOthers,
				() -> List.of(Component.translatable(AURA + "others"), Component.translatable(AURA + "others.tip").withStyle(ChatFormatting.GRAY))));
		// Techniques and effects (DESIGN 22.12.9; hold G and release = cast live in Kontrol).
		items.add(heading(C + "techniques"));
		items.add(cycle(() -> Component.translatable(C + "shake", Component.translatable(C + "shake." + EffectQuality.shake().id())),
				() -> EffectQuality.setShake(EffectQuality.Shake.values()[(EffectQuality.shake().ordinal() + 1) % EffectQuality.Shake.values().length]),
				() -> List.of(Component.translatable(C + "shake", Component.translatable(C + "shake." + EffectQuality.shake().id())),
						Component.translatable(C + "shake.tip").withStyle(ChatFormatting.GRAY))));
		items.add(codexCoin("flash", EffectQuality::flash, EffectQuality::setFlash));
		items.add(codexCoin("phantoms", EffectQuality::phantoms, EffectQuality::setPhantoms));
		items.add(cycle(() -> Component.translatable(C + "others", Component.translatable(C + "others." + EffectQuality.others().id())),
				() -> EffectQuality.setOthers(EffectQuality.Others.values()[(EffectQuality.others().ordinal() + 1) % EffectQuality.Others.values().length]),
				() -> List.of(Component.translatable(C + "others", Component.translatable(C + "others." + EffectQuality.others().id())),
						Component.translatable(C + "others.tip").withStyle(ChatFormatting.GRAY))));
		items.add(codexCoin("reduce_motion", EffectQuality::reduceMotion, EffectQuality::setReduceMotion));
		// Impact and world (DESIGN 28.11, 23.2), plus any epic row no group claims.
		List<EpicSettings.Row> rows = epicRows(TAMPILAN, TAMPILAN_ORDER);
		if (!rows.isEmpty()) {
			items.add(heading(P + "heading.dampak"));
			for (EpicSettings.Row row : rows) {
				items.add(epic(row));
			}
		}
	}

	private void film(List<Item> items) {
		List<EpicSettings.Row> rows = epicRows(FILM, FILM_ORDER);
		if (!rows.isEmpty()) {
			items.add(heading("kultivator.spine7b.settings.heading"));
			for (EpicSettings.Row row : rows) {
				items.add(epic(row));
			}
		}
		List<EpicSettings.Row> titah = EpicSettings.titahRows();
		if (!titah.isEmpty()) {
			items.add(heading(P + "heading.titah"));
			for (EpicSettings.Row row : titah) {
				items.add(epic(row));
			}
		}
	}

	private void suara(List<Item> items) {
		items.add(heading(P + "heading.ambience"));
		for (EpicSettings.Row row : epicRows(SUARA, List.of(EpicSettings.MUSIC))) {
			items.add(epic(row));
		}
		items.add(coin(() -> Component.translatable(AURA + "hum"), AuraSettings::hum, AuraSettings::setHum,
				() -> List.of(Component.translatable(AURA + "hum"), Component.translatable(AURA + "hum.tip").withStyle(ChatFormatting.GRAY))));
		List<VoiceSettings.Row> voices = VoiceSettings.rows();
		if (!voices.isEmpty()) {
			items.add(heading(VoiceSettings.heading()));
			for (VoiceSettings.Row voice : voices) {
				items.add(new Item(voice.cycle() ? Type.CYCLE : Type.COIN, voice.value(), null, voice.next(), voice.tooltip(), voice.on(),
						SettingsRows::yes, SettingsRows::no, SettingsRows::ink));
			}
		}
	}

	private void hud(List<Item> items) {
		items.add(heading("kultivator.screen.settings.interface"));
		items.add(coin(() -> Component.translatable("kultivator.screen.settings.hud"), EffectQuality::hudVisible, EffectQuality::setHudVisible,
				() -> List.of(Component.translatable("kultivator.screen.settings.hud"), Component.translatable("kultivator.screen.settings.hud.tip"))));
		items.add(goalCoin("cooldown_ping", EffectQuality::cooldownPing, EffectQuality::setCooldownPing));
		items.add(goalCoin("danger_alert", EffectQuality::dangerAlert, EffectQuality::setDangerAlert));
		// The only "Sembunyikan panduan" coin (DESIGN 33.6 R5): the server's JALAN show/hide, needs the connection.
		items.add(new Item(Type.COIN, () -> Component.translatable(G + "guide"), null,
				() -> Page.send(Actions.JALAN, 0, Guide.hidden(Page.data()) ? "show" : "hide"),
				() -> List.of(Component.translatable(G + "guide"), Component.translatable(G + "guide.tip").withStyle(ChatFormatting.GRAY)),
				() -> !Guide.hidden(Page.data()), Keys::connected, SettingsRows::no, SettingsRows::ink));
	}

	private void kontrol(List<Item> items) {
		items.add(heading(P + "heading.cast"));
		items.add(codexCoin("wheel_hold", EffectQuality::wheelHold, EffectQuality::setWheelHold));
		items.add(codexCoin("release_cast", EffectQuality::releaseCast, EffectQuality::setReleaseCast));
		items.add(new Item(Type.BUTTON, () -> Component.translatable(P + "rebind"), null, this::openKeyBinds,
				() -> List.of(Component.translatable(P + "rebind"), Component.translatable(P + "rebind.tip").withStyle(ChatFormatting.GRAY)),
				SettingsRows::no, SettingsRows::yes, SettingsRows::no, SettingsRows::ink));
		List<KeyMapping> keys = kultivatorKeys();
		// The G / quick-actions note (DESIGN 33.4.1), only while a Kultivator key really shares the quick-actions key.
		Minecraft minecraft = Minecraft.getInstance();
		KeyMapping quick = minecraft.options.keyQuickActions;
		KeyMapping sharing = null;
		boolean otherClash = false;
		for (KeyMapping key : keys) {
			if (!key.isUnbound() && !quick.isUnbound() && key.same(quick)) {
				sharing = sharing == null ? key : sharing;
			} else if (!clashes(key).isEmpty()) {
				otherClash = true;
			}
		}
		if (sharing != null) {
			Component keyName = sharing.getTranslatedKeyMessage();
			items.add(note(() -> Component.translatable(P + "quick_actions", keyName, Component.translatable(quick.getName()))));
		}
		if (otherClash) {
			items.add(note(() -> Component.translatable(P + "clash_note")));
		}
		// Hardcore breakthroughs: a second click within 3 s confirms (as before).
		items.add(heading(P + "heading.game"));
		items.add(new Item(Type.COIN, () -> Component.translatable(asking() ? "kultivator.screen.settings.hardcore.confirm" : "kultivator.screen.settings.hardcore"),
				null, () -> {
					if (asking()) {
						Page.send(Actions.SET_HARDCORE, Page.data().hardcore() ? 0 : 1, "");
						hardcoreAskUntil = 0L;
					} else {
						hardcoreAskUntil = Util.getMillis() + CONFIRM_MILLIS;
					}
				},
				() -> List.of(Component.translatable("kultivator.screen.settings.hardcore"),
						Component.translatable("kultivator.screen.settings.hardcore.tip"),
						Component.translatable(Page.data().hardcore() ? "kultivator.screen.settings.hardcore.is_on" : "kultivator.screen.settings.hardcore.is_off")
								.withStyle(Page.data().hardcore() ? ChatFormatting.RED : ChatFormatting.GRAY),
						Component.translatable("kultivator.screen.settings.hardcore.how").withStyle(ChatFormatting.DARK_GRAY)),
				() -> Page.data().hardcore(), Keys::connected, this::asking, SettingsRows::ink));
		// The live list of every Kultivator key (the only key list, DESIGN 33.6 R2), in the second column.
		items.add(new Item(Type.BREAK, Component::empty, null, null, List::of, SettingsRows::no, SettingsRows::yes, SettingsRows::no,
				SettingsRows::ink));
		items.add(heading(P + "heading.keys"));
		for (KeyMapping key : keys) {
			items.add(new Item(Type.LINE, () -> Component.translatable(key.getName()), () -> keyText(key), null, () -> keyTooltip(key),
					SettingsRows::no, SettingsRows::yes, SettingsRows::no, () -> keyColor(key)));
		}
	}

	// ================================================================ keys

	/** Every mapping of this mod ({@code key.kultivator.*}, the {@code Keys} and {@code SkipKey} ones), in registration order. */
	private static List<KeyMapping> kultivatorKeys() {
		List<KeyMapping> keys = new ArrayList<>();
		for (KeyMapping key : Minecraft.getInstance().options.keyMappings) {
			if (key.getName().startsWith(KEY_PREFIX)) {
				keys.add(key);
			}
		}
		return keys;
	}

	/** The other mappings (not F3 debug combos, not itself) bound to the same key. */
	private static List<KeyMapping> clashes(KeyMapping key) {
		List<KeyMapping> others = new ArrayList<>();
		if (key.isUnbound()) {
			return others;
		}
		for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
			if (other != key && !other.isUnbound() && other.getCategory() != KeyMapping.Category.DEBUG && other.same(key)) {
				others.add(other);
			}
		}
		return others;
	}

	private static Component keyText(KeyMapping key) {
		return key.isUnbound() ? Component.translatable(P + "key.unbound") : key.getTranslatedKeyMessage();
	}

	private static int keyColor(KeyMapping key) {
		if (key.isUnbound()) {
			return Palette.INK_FAINT;
		}
		return clashes(key).isEmpty() ? Palette.JADE_DARK : Palette.WARN;
	}

	private static List<Component> keyTooltip(KeyMapping key) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(key.getName()));
		lines.add(keyText(key).copy().withStyle(ChatFormatting.GOLD));
		lines.add((key.getDefaultKey().equals(com.mojang.blaze3d.platform.InputConstants.UNKNOWN)
				? Component.translatable(P + "key.default_none")
				: Component.translatable(P + "key.default", key.getDefaultKey().getDisplayName())).withStyle(ChatFormatting.GRAY));
		List<KeyMapping> others = clashes(key);
		if (!others.isEmpty()) {
			List<Component> names = new ArrayList<>();
			for (KeyMapping other : others) {
				names.add(Component.translatable(other.getName()));
			}
			lines.add(Component.translatable(P + "key.clash", net.minecraft.network.chat.ComponentUtils.formatList(names, Component.literal(", ")))
					.withStyle(ChatFormatting.YELLOW));
		}
		lines.add(Component.translatable(P + "key.change").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	/** "Ubah tombol…": vanilla's key binds screen; closing it comes back to the hub (on the Kontrol group). */
	private void openKeyBinds() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.gui.setScreen(new KeyBindsScreen(hub, minecraft.options));
	}

	// ================================================================ builders

	/** The rows of {@link EpicSettings#rows()} in {@code group}, sorted by {@code order} (unlisted rows after, in source order). */
	private static List<EpicSettings.Row> epicRows(int group, List<String> order) {
		List<EpicSettings.Row> rows = new ArrayList<>();
		for (EpicSettings.Row row : EpicSettings.rows()) {
			if (EpicSettings.group(row.key()) == group) {
				rows.add(row);
			}
		}
		rows.sort(Comparator.comparingInt(row -> {
			int index = order.indexOf(row.key());
			return index < 0 ? order.size() : index;
		}));
		return rows;
	}

	private static Item epic(EpicSettings.Row row) {
		String key = row.key();
		BooleanSupplier on = row.cycle() ? SettingsRows::no : () -> EpicSettings.flag(key);
		return new Item(row.cycle() ? Type.CYCLE : Type.COIN, row.value(), null, row.next(), row.tooltip(), on, SettingsRows::yes,
				SettingsRows::no, SettingsRows::ink);
	}

	private static Item heading(String key) {
		return new Item(Type.HEADING, () -> Component.translatable(key), null, null, List::of, SettingsRows::no, SettingsRows::yes,
				SettingsRows::no, SettingsRows::ink);
	}

	private static Item note(Supplier<Component> text) {
		return new Item(Type.NOTE, text, null, null, List::of, SettingsRows::no, SettingsRows::yes, SettingsRows::no, () -> Palette.INK_SOFT);
	}

	private static Item cycle(Supplier<Component> label, Runnable next, Supplier<List<Component>> tooltip) {
		return new Item(Type.CYCLE, label, null, next, tooltip, SettingsRows::no, SettingsRows::yes, SettingsRows::no, SettingsRows::ink);
	}

	private static Item coin(Supplier<Component> label, BooleanSupplier value, Consumer<Boolean> set, Supplier<List<Component>> tooltip) {
		return new Item(Type.COIN, label, null, () -> set.accept(!value.getAsBoolean()), tooltip, value, SettingsRows::yes, SettingsRows::no,
				SettingsRows::ink);
	}

	private static Item codexCoin(String key, BooleanSupplier value, Consumer<Boolean> set) {
		return coin(() -> Component.translatable(C + key), value, set,
				() -> List.of(Component.translatable(C + key), Component.translatable(C + key + ".tip").withStyle(ChatFormatting.GRAY)));
	}

	private static Item goalCoin(String key, BooleanSupplier value, Consumer<Boolean> set) {
		return coin(() -> Component.translatable(G + key), value, set,
				() -> List.of(Component.translatable(G + key), Component.translatable(G + key + ".tip").withStyle(ChatFormatting.GRAY)));
	}

	/** The Aura Kultivasi mode row's tooltip: the modes, then the aura of your current realm and its Sumber line. */
	private static List<Component> auraModeTooltip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(AURA + "mode", Component.translatable(AURA + "mode." + AuraSettings.mode().id())));
		lines.add(Component.translatable(AURA + "mode.tip").withStyle(ChatFormatting.GRAY));
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			String realm = dev.kultivator.client.ClientCultivation.visual(minecraft.player).realmEnum().id();
			lines.add(Component.translatable(AURA + "mode.current", Component.translatable("kultivator.aura.realm." + realm + ".name"))
					.withStyle(ChatFormatting.GOLD));
			lines.add(Component.translatable("kultivator.aura.realm." + realm + ".desc").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("kultivator.aura.realm." + realm + ".source").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	private boolean asking() {
		return Util.getMillis() < hardcoreAskUntil;
	}

	private static boolean yes() {
		return true;
	}

	private static boolean no() {
		return false;
	}

	private static int ink() {
		return Palette.INK;
	}
}
