package dev.kultivator.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import dev.kultivator.client.aura.AuraSettings;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.sekte.gabung.fx.GabungFxClient;
import dev.kultivator.client.sosok.SosokDetail;
import dev.kultivator.client.voice.VoiceSettings;
import dev.kultivator.data.CultivationData;
import dev.kultivator.goals.Guide;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The group table of the Pengaturan pages (DESIGN 33.5, 43.10): which row lives in which of the five groups (one page
 * each, {@link SettingsPage}), and the row builders. Every stored setting keeps its key, default and setter; only where
 * it is shown changes. Every row appears exactly once (23.17.5): Tampilan & Efek (the quality column is drawn by
 * {@link SettingsPage}; cosmetics, aura, gait, Gabung Sekte, Kota yang Hidup, techniques, impact and world, Langit Sekte),
 * Kamera & Film (the film rows of {@link EpicSettings#rows()} and the Film Titah rows), Suara (cinematic music, aura hum,
 * street music, the voice rows), HUD (HUD, cooldown ping, danger alert, guide tracker) and Kontrol (hold G, release =
 * cast, Hardcore, duel input; the live key list rebound in place, "Semua tombol Minecraft…"). The sword-duel rows join
 * their groups. An {@code EpicSettings} row whose key is not in a group table falls into Tampilan & Efek.
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
	 * line (a key name); {@code color} its ink; {@code on} lights a coin; {@code asking} is no longer read (confirmations
	 * are {@code SegelKonfirmasi}, 43.7) and stays false.
	 */
	record Item(Type type, Supplier<Component> label, @Nullable Supplier<Component> value, @Nullable Runnable action,
				Supplier<List<Component>> tooltip, BooleanSupplier on, BooleanSupplier enabled, BooleanSupplier asking, IntSupplier color) {
	}

	private static final String P = "kultivator.pengaturan.";
	private static final String M = "kultivator.gui43.m18.";
	private static final String C = "kultivator.codex.settings.";
	private static final String G = "kultivator.goals.settings.";
	private static final String AURA = "kultivator.aura.setting.";
	private static final String SOSOK = "kultivator.sosok.setelan.";
	/** Lang prefix of the Kota yang Hidup rows (fragment {@code kota_hidup_panduan}, 42V-B). */
	private static final String KOTA42 = "kultivator.kota42.setelan.";
	/**
	 * The client switches of Kota yang Hidup (DESIGN 42.14.1, all default on), stored in {@code kultivator-client.json}:
	 * town lanterns, kitchen smoke, banners, firecrackers, pigeon flocks, shadow-puppet silhouettes, soot marks. The fx
	 * of 42V-F read them with {@link #kota42}.
	 */
	public static final List<String> KOTA42_SAKLAR = List.of("kota42.lentera", "kota42.asap", "kota42.panji", "kota42.petasan",
			"kota42.merpati", "kota42.wayang", "kota42.jelaga");
	/** The street music volume in percent (100/75/50/25/0, Suara group), read with {@link #kota42Melodi}. */
	public static final String KOTA42_MELODI = "kota42.melodi";
	private static final int[] MELODI_LANGKAH = {100, 75, 50, 25, 0};
	private static final String KEY_PREFIX = "key.kultivator.";
	private static final int[] FLAGS = {CultivationData.COSMETIC_AURA, CultivationData.COSMETIC_ROBE, CultivationData.COSMETIC_BACK_SWORD,
			CultivationData.COSMETIC_EYES, CultivationData.COSMETIC_DAO_CIRCLE};
	private static final String[] PARTS = {"aura", "robe", "back_sword", "eyes", "dao_circle"};

	/** The order of the epic rows inside their group (rows not listed keep their source order after these). */
	private static final List<String> FILM_ORDER = List.of(EpicSettings.CAMERA, EpicSettings.LENS, EpicSettings.COSMIC, EpicSettings.KOSMIK3D,
			EpicSettings.BULLET, EpicSettings.SLOWMO, EpicSettings.SKYRIDE, EpicSettings.IMPACT_FRAMES, EpicSettings.SILENT_IMPACT,
			EpicSettings.LETTERBOX, EpicSettings.CARDS, EpicSettings.RECORD);
	private static final List<String> TAMPILAN_ORDER = List.of(EpicSettings.WORLD_FX, EpicSettings.IMPACT_POWER, EpicSettings.GEOMETRY,
			EpicSettings.SKY, EpicSettings.VFX);

	private final SettingsPage page;
	/** The key of each key line of the last {@link #group} call (Kontrol). */
	private final Map<Item, KeyMapping> keyLines = new IdentityHashMap<>();

	SettingsRows(SettingsPage page) {
		this.page = page;
	}

	/** The mapping a key line ({@link Type#LINE}) stands for. */
	@Nullable KeyMapping keyOf(Item line) {
		return keyLines.get(line);
	}

	/** The group's name (the title of its Pengaturan page). */
	static Component groupName(int group) {
		return Component.translatable(P + "group." + GROUP_IDS[clamp(group)]);
	}

	static int clamp(int group) {
		return Math.max(TAMPILAN, Math.min(KONTROL, group));
	}

	/** The items of {@code group}, in order (Tampilan & Efek: the right column only). */
	List<Item> group(int group) {
		List<Item> items = new ArrayList<>();
		keyLines.clear();
		switch (clamp(group)) {
			case TAMPILAN -> tampilan(items);
			case FILM -> film(items);
			case SUARA -> suara(items);
			case HUD -> hud(items);
			default -> kontrol(items);
		}
		if (clamp(group) != KONTROL) {
			duel(items, clamp(group));
		}
		if (clamp(group) == TAMPILAN) {
			rows(items, dev.kultivator.client.sekte.langit.LangitClientConfig.rows());
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
		// Penampilan & Animasi (DESIGN 40.6.2): the detail level cycles, the face expressions are a coin.
		items.add(heading(SOSOK + "judul"));
		items.add(cycle(() -> Component.translatable(SOSOK + "detail", Component.translatable(SOSOK + "detail." + SosokDetail.setelan().id())),
				SosokDetail::putarSetelan, SettingsRows::sosokDetailTooltip));
		items.add(coin(() -> Component.translatable(SOSOK + "wajah"), SosokDetail::wajah, SosokDetail::setWajah,
				() -> List.of(Component.translatable(SOSOK + "wajah"), Component.translatable(SOSOK + "wajah.tip").withStyle(ChatFormatting.GRAY))));
		// the walk and run (Langkah): the player's style cycles Cantik/Vanilla, the strength cycles 50-150 %
		items.add(cycle(() -> Component.translatable(SOSOK + "jalan", Component.translatable(SOSOK + "jalan." + (SosokDetail.jalanPemain() ? "cantik" : "vanilla"))),
				SosokDetail::putarJalan,
				() -> List.of(Component.translatable(SOSOK + "jalan", Component.translatable(SOSOK + "jalan." + (SosokDetail.jalanPemain() ? "cantik" : "vanilla"))),
						Component.translatable(SOSOK + "jalan.tip").withStyle(ChatFormatting.GRAY))));
		items.add(cycle(() -> Component.translatable(SOSOK + "jalan_kuat", SosokDetail.jalanKuatPersen()), SosokDetail::putarJalanKuat,
				() -> List.of(Component.translatable(SOSOK + "jalan_kuat", SosokDetail.jalanKuatPersen()),
						Component.translatable(SOSOK + "jalan_kuat.tip").withStyle(ChatFormatting.GRAY))));
		// Gabung Sekte (DESIGN 41.13.1, client group): the member robe over armour, the sect class visuals, the illusion
		// stair of the entrance test, the rank line above member names. Stored in kultivator-client.json, default on.
		items.add(heading(GabungFxClient.LANG_SETELAN + "judul"));
		for (int i = 0; i < GabungFxClient.SAKLAR.size(); i++) {
			String key = GabungFxClient.SAKLAR.get(i);
			String label = GabungFxClient.LANG_SETELAN + GabungFxClient.SAKLAR_LANG.get(i);
			items.add(coin(() -> Component.translatable(label), () -> GabungFxClient.saklar(key), on -> GabungFxClient.setSaklar(key, on),
					() -> List.of(Component.translatable(label), Component.translatable(label + ".tip").withStyle(ChatFormatting.GRAY))));
		}
		// Kota yang Hidup (DESIGN 42.14.1, client group): the town's lanterns, smoke, banners, firecrackers, pigeons, shadow
		// puppets and soot. Stored in kultivator-client.json, default on.
		items.add(heading(KOTA42 + "judul"));
		for (String key : KOTA42_SAKLAR) {
			String label = KOTA42 + key.substring("kota42.".length());
			items.add(coin(() -> Component.translatable(label), () -> kota42(key), on -> EffectQuality.put(key, new JsonPrimitive(on)),
					() -> List.of(Component.translatable(label), Component.translatable(label + ".tip").withStyle(ChatFormatting.GRAY))));
		}
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
		// Kota yang Hidup (42.14.1): street music of musicians, weddings and troupes, 100/75/50/25 % or off.
		items.add(cycle(SettingsRows::melodiLabel, SettingsRows::putarMelodi,
				() -> List.of(melodiLabel(), Component.translatable(KOTA42 + "melodi.tip").withStyle(ChatFormatting.GRAY))));
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
		// Hardcore breakthroughs: switching on is a medium seal (hold 1.5 s), switching off a light one (43.7).
		items.add(heading(P + "heading.game"));
		items.add(new Item(Type.COIN, () -> Component.translatable("kultivator.screen.settings.hardcore"), null, this::askHardcore,
				() -> List.of(Component.translatable("kultivator.screen.settings.hardcore"),
						Component.translatable("kultivator.screen.settings.hardcore.tip"),
						Component.translatable(Page.data().hardcore() ? "kultivator.screen.settings.hardcore.is_on" : "kultivator.screen.settings.hardcore.is_off")
								.withStyle(Page.data().hardcore() ? ChatFormatting.RED : ChatFormatting.GRAY),
						Component.translatable(M + "hardcore.how").withStyle(ChatFormatting.DARK_GRAY)),
				() -> Page.data().hardcore(), Keys::connected, SettingsRows::no, SettingsRows::ink));
		// Duel Pedang (DESIGN 35.8.3): input mode, parry outside duels, lock-on, PAL animations.
		duel(items, KONTROL);
		// The live list of every Kultivator key (the only key list, DESIGN 33.6 R2), rebound in place (43.10.2).
		items.add(new Item(Type.BREAK, Component::empty, null, null, List::of, SettingsRows::no, SettingsRows::yes, SettingsRows::no,
				SettingsRows::ink));
		items.add(heading(P + "heading.keys"));
		items.add(note(() -> Component.translatable(M + "keys.petunjuk")));
		List<KeyMapping> keys = kultivatorKeys();
		// The G / quick-actions note (DESIGN 33.4.1), only while a Kultivator key really shares the quick-actions key.
		KeyMapping quick = Minecraft.getInstance().options.keyQuickActions;
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
			items.add(note(() -> Component.translatable(M + "clash_note")));
		}
		for (KeyMapping key : keys) {
			Item line = new Item(Type.LINE, () -> Component.translatable(key.getName()), () -> keyValue(key), null, () -> keyTooltip(key),
					SettingsRows::no, SettingsRows::yes, SettingsRows::no, () -> keyColor(key));
			keyLines.put(line, key);
			items.add(line);
		}
		// vanilla's own keys stay on vanilla's screen (43.0.3: the one vanilla screen besides chat)
		items.add(new Item(Type.BUTTON, () -> Component.translatable(M + "vanilla"), null, SettingsRows::openKeyBinds,
				() -> List.of(Component.translatable(M + "vanilla"), Component.translatable(M + "vanilla.tip").withStyle(ChatFormatting.GRAY)),
				SettingsRows::no, SettingsRows::yes, SettingsRows::no, SettingsRows::ink));
	}

	/** The Hardcore coin: asks over the page, then tells the server. */
	private void askHardcore() {
		boolean on = Page.data().hardcore();
		page.konfirmasi(on ? SegelKonfirmasi.Tingkat.RINGAN : SegelKonfirmasi.Tingkat.SEDANG,
				Component.translatable(on ? M + "hardcore.mati" : M + "hardcore.nyala"),
				() -> Page.send(Actions.SET_HARDCORE, on ? 0 : 1, ""));
	}

	// ================================================================ keys

	/** Every mapping of this mod ({@code key.kultivator.*}, the {@code Keys} and {@code SkipKey} ones), in registration order. */
	static List<KeyMapping> kultivatorKeys() {
		List<KeyMapping> keys = new ArrayList<>();
		for (KeyMapping key : Minecraft.getInstance().options.keyMappings) {
			if (key.getName().startsWith(KEY_PREFIX)) {
				keys.add(key);
			}
		}
		return keys;
	}

	/** The other mappings (not F3 debug combos, not itself) bound to the same key. */
	static List<KeyMapping> clashes(KeyMapping key) {
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

	/** The binding as shown on the key line: "G", or "G · juga <other mapping>" on a clash. */
	static Component keyValue(KeyMapping key) {
		List<KeyMapping> others = clashes(key);
		return others.isEmpty() ? keyText(key) : Component.translatable(M + "key.bentrok", keyText(key), Component.translatable(others.get(0).getName()));
	}

	static int keyColor(KeyMapping key) {
		if (key.isUnbound()) {
			return Palette.INK_FAINT;
		}
		return clashes(key).isEmpty() ? Palette.JADE_DARK : Palette.DANGER;
	}

	static List<Component> keyTooltip(KeyMapping key) {
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
					.withStyle(ChatFormatting.RED));
		}
		lines.add(Component.translatable(M + "key.ubah").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	/** "Semua tombol Minecraft…": vanilla's key binds screen over a new Gulungan on Pengaturan › Kontrol (Done/Esc return there). */
	private static void openKeyBinds() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.gui.setScreen(new KeyBindsScreen(CultivationScreen.buka(Tujuan.of(HubNav.PENGATURAN, Urutan.ID_KONTROL)), minecraft.options));
	}

	// ================================================================ Kota yang Hidup switches (42.14.1)

	/** A Kota yang Hidup client switch ({@link #KOTA42_SAKLAR}); default on, also when the file holds something else. */
	public static boolean kota42(String key) {
		JsonElement e = EffectQuality.get(key);
		try {
			return e == null || !e.isJsonPrimitive() || e.getAsBoolean();
		} catch (RuntimeException ignored) {
			return true;
		}
	}

	/** The street music volume 0-100 (default 100). */
	public static int kota42Melodi() {
		JsonElement e = EffectQuality.get(KOTA42_MELODI);
		try {
			return e == null || !e.isJsonPrimitive() ? 100 : Math.max(0, Math.min(100, e.getAsInt()));
		} catch (RuntimeException ignored) {
			return 100;
		}
	}

	private static Component melodiLabel() {
		int v = kota42Melodi();
		return Component.translatable(KOTA42 + "melodi", v <= 0 ? Component.translatable(KOTA42 + "melodi.mati") : Component.literal(v + " %"));
	}

	/** Next step of 100 > 75 > 50 > 25 > off > 100; a value between steps moves to the next lower step. */
	private static void putarMelodi() {
		int v = kota42Melodi();
		int next = MELODI_LANGKAH[0];
		for (int i = 0; i < MELODI_LANGKAH.length; i++) {
			if (v >= MELODI_LANGKAH[i]) {
				next = v == MELODI_LANGKAH[i] ? MELODI_LANGKAH[(i + 1) % MELODI_LANGKAH.length] : MELODI_LANGKAH[i];
				break;
			}
		}
		EffectQuality.put(KOTA42_MELODI, new JsonPrimitive(next));
	}

	// ================================================================ builders

	/** The sword-duel rows of {@code group} (DESIGN 35.8.3, {@code client.duel.ui.DuelSettings}). */
	private static void duel(List<Item> items, int group) {
		rows(items, dev.kultivator.client.duel.ui.DuelSettings.rows(group));
	}

	/** Rows in the {@code DuelSettings.Row} shape (duel, Langit Sekte) as setting items. */
	private static void rows(List<Item> items, List<dev.kultivator.client.duel.ui.DuelSettings.Row> rows) {
		for (dev.kultivator.client.duel.ui.DuelSettings.Row row : rows) {
			if (row.heading()) {
				items.add(new Item(Type.HEADING, row.label(), null, null, List::of, SettingsRows::no, SettingsRows::yes, SettingsRows::no,
						SettingsRows::ink));
			} else {
				items.add(new Item(row.cycle() ? Type.CYCLE : Type.COIN, row.label(), null, row.next(), row.tooltip(), row.on(), SettingsRows::yes,
						SettingsRows::no, SettingsRows::ink));
			}
		}
	}

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

	/**
	 * The "Detail animasi" row's tooltip (DESIGN 40.6.2): what the level changes, the level now in effect, and its
	 * distances and counts (full detail, reduced, silhouette; bodies with full animation and with springs; atlases).
	 */
	private static List<Component> sosokDetailTooltip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(SOSOK + "detail", Component.translatable(SOSOK + "detail." + SosokDetail.setelan().id())));
		lines.add(Component.translatable(SOSOK + "detail.tip").withStyle(ChatFormatting.GRAY));
		SosokDetail.Tingkat t = SosokDetail.tingkat();
		lines.add(Component.translatable(SOSOK + "detail.sekarang", Component.translatable(SOSOK + "detail." + t.id()))
				.withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable(SOSOK + "detail.jarak", t.penuh, t.kurang, t.siluet).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable(SOSOK + "detail.jumlah", t.gerakPenuhMaks, t.pegasMaks, t.atlasMaks).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable(SOSOK + "detail.catatan").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
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
