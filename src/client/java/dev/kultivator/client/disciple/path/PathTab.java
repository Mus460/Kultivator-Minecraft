package dev.kultivator.client.disciple.path;

import dev.kultivator.client.disciple.DiscipleScreen;
import dev.kultivator.client.disciple.DiscipleTab;
import dev.kultivator.client.kehancuran.KehancuranClient;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SectionPicker;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.Bond;
import dev.kultivator.disciple.path.Calamity;
import dev.kultivator.disciple.path.DestroyerArts;
import dev.kultivator.disciple.path.FoundationTypes;
import dev.kultivator.disciple.path.Heir;
import dev.kultivator.disciple.path.HuFa;
import dev.kultivator.disciple.path.SoulLamps;
import dev.kultivator.disciple.path.StarTitles;
import dev.kultivator.network.Actions;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The Jalan tab of the disciple scroll ({@code DiscipleScreen.TAB_PATH}; DESIGN 20.3.18-20.3.21, module D). Four
 * sections chosen with small talismans at the top:
 * <ul>
 *   <li><b>Bintang</b>: the Bintang Abadi title, the Qi Bintang bar, the seven calamities in three Trials, the star
 *   bonuses, soul lamps and Divine Clones; condense, Otomatis, start the calamity, put out a lamp, Tahan Petir.</li>
 *   <li><b>Ikatan</b>: the bond, the Soul Seal, Hu Fa and the Dao foundation's pillars; recalling sealed disciples.</li>
 *   <li><b>Pewaris</b>: the heir, the Nine Hexes and the Generation Hex, the Master's Soul Ring and surpassing the master;
 *   naming the heir, hexes, Jiwa Guru Turun, Tetua Kehormatan, Tanding dengan Guru.</li>
 *   <li><b>Pemusnah</b>: the Jurus Pemusnah and the red seal of Mode Kehancuran (oath and Lepaskan Segel open the
 *   blood-red warning).</li>
 * </ul>
 * Everything shown comes from the {@code path} compound of the disciple's details ({@code PathInfo}).
 */
public class PathTab extends DiscipleTab {

	private static final String P = "kultivator.dpath.ui.";
	private static final String[] SECTION_IDS = {"stars", "bond", "heir", "arts"};
	private static final int CHIP_H = 14;
	private static final int GAP = 3;
	private static final long CONFIRM_MILLIS = 4000L;
	private static final int MAX_RECALL = 4;
	private static final int STAR_GOLD = 0xFFD54A;

	private int section;
	private final Map<Widget, Integer> sectionOf = new IdentityHashMap<>();
	private final Map<Widget, BooleanSupplier> shownWhen = new IdentityHashMap<>();
	private int bodyTop;
	private int leftW;
	private int rightX;
	private int rightW;
	private String signature = "";
	private int pick;
	private long heirArmedUntil;

	public PathTab(DiscipleScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable(P + "tab");
	}

	@Override
	public Component shortName() {
		return Component.translatable(P + "tab.short");
	}

	private CompoundTag path() {
		return info().getCompoundOrEmpty("path");
	}

	private boolean flag(String key) {
		return path().getBooleanOr(key, false);
	}

	// ================================================================== controls

	@Override
	protected void build() {
		sectionOf.clear();
		shownWhen.clear();
		signature = signature();
		// One "‹ section ›" selector at the top-left (DESIGN 5b: no strip of tabs along the top).
		String[] keys = new String[SECTION_IDS.length];
		for (int i = 0; i < SECTION_IDS.length; i++) {
			keys[i] = P + "section." + SECTION_IDS[i];
		}
		SectionPicker picker = new SectionPicker(x, y, Math.min(w, Math.max(110, Math.round(w * 0.42F))), CHIP_H, keys, () -> section,
				index -> section = index);
		picker.tooltip(() -> List.of(Component.translatable(P + "section." + SECTION_IDS[section]),
				Component.translatable(P + "section." + SECTION_IDS[section] + ".tip").withStyle(ChatFormatting.GRAY)));
		add(picker);
		bodyTop = y + CHIP_H + 5;
		rightW = Math.max(96, Math.min(150, Math.round(w * 0.40F)));
		rightX = x + w - rightW;
		leftW = w - rightW - 6;
		buildStars();
		buildBond();
		buildHeir();
		buildArts();
	}

	@Override
	public boolean needsRebuild() {
		return !signature.equals(signature());
	}

	/** What decides which controls exist: the arts, the sealed disciples to recall and whether the server allows Kehancuran. */
	private String signature() {
		CompoundTag p = path();
		StringBuilder text = new StringBuilder();
		for (Tag tag : p.getListOrEmpty("arts")) {
			text.append(tag.asString().orElse("")).append(',');
		}
		text.append('|');
		for (Tag tag : p.getListOrEmpty("sealed_list")) {
			if (tag instanceof CompoundTag entry) {
				text.append(entry.getIntOr("id", 0)).append(',');
			}
		}
		return text.append('|').append(p.getBooleanOr("k_server", false)).toString();
	}

	@Override
	protected boolean visible(Widget widget) {
		Integer owner = sectionOf.get(widget);
		if (owner != null && owner != section) {
			return false;
		}
		BooleanSupplier shown = shownWhen.get(widget);
		return shown == null || shown.getAsBoolean();
	}

	/** Places a section's controls down the right column, as tall as fits (12-18 px). */
	private void column(int owner, List<Widget> list) {
		int n = list.size();
		if (n == 0) {
			return;
		}
		int bodyH = y + h - bodyTop;
		int height = Math.max(12, Math.min(18, (bodyH - GAP * (n - 1)) / n));
		for (int i = 0; i < n; i++) {
			Widget widget = list.get(i);
			widget.x = rightX;
			widget.y = bodyTop + i * (height + GAP);
			widget.w = rightW;
			widget.h = height;
			sectionOf.put(widget, owner);
			add(widget);
		}
	}

	private static TalismanButton button(Supplier<Component> label, BooleanSupplier enabled, Runnable action, Supplier<List<Component>> tip) {
		TalismanButton button = new TalismanButton(0, 0, 10, 10, label);
		button.enabledWhen(enabled);
		button.onPress(action);
		button.tooltip(tip);
		return button;
	}

	private void act(String text) {
		send(Actions.DISCIPLE_PATH, text);
	}

	/** Sends {@code text} and closes the scroll so the effect can be watched in the world. */
	private void actAndClose(String text) {
		send(Actions.DISCIPLE_PATH, text);
		screen.onClose();
	}

	// ---- Bintang

	private void buildStars() {
		List<Widget> list = new ArrayList<>();
		list.add(button(() -> Component.translatable(P + "condense"), () -> flag("can_condense"), () -> act("condense"),
				() -> tip(P + "condense", P + "condense.tip", blocked("can_condense", "condense_reason"))).urgentWhen(() -> flag("can_condense")));
		list.add(button(() -> Component.translatable(P + "auto", onOff(path().getBooleanOr("auto", true))), () -> flag("max_rank"),
				() -> act("auto:" + (path().getBooleanOr("auto", true) ? "off" : "on")), () -> tip(P + "auto.name", P + "auto.tip", null))
				.stampedWhen(() -> path().getBooleanOr("auto", true)));
		list.add(button(() -> path().getIntOr("pending_k", 0) > 0 ? Component.translatable(P + "calamity_k", path().getIntOr("pending_k", 0))
						: Component.translatable(P + "calamity"), () -> flag("calamity_ok"), () -> actAndClose("calamity"),
				() -> tip(P + "calamity", P + "calamity.tip", blocked("calamity_ok", "calamity_reason"))).urgentWhen(() -> flag("calamity_ok")));
		list.add(button(() -> Component.translatable(P + "lamp"), () -> flag("lamp_ok"), () -> actAndClose("lamp"),
				() -> tip(P + "lamp", P + "lamp.tip", blocked("lamp_ok", "lamp_reason"))));
		list.add(tahanButton());
		column(0, list);
	}

	private TalismanButton tahanButton() {
		return button(() -> Component.translatable(P + "tahan", Math.max(0, path().getIntOr("tahan_left", -1)), HuFa.TAHAN_MAX),
				this::tahanReady, () -> act("tahan"), () -> {
					List<Component> lines = tip(P + "tahan.name", P + "tahan.tip", null);
					if (!flag("trial_strikes")) {
						lines.add(Component.translatable(P + "tahan.none").withStyle(ChatFormatting.RED));
					} else if (flag("tahan_pending")) {
						lines.add(Component.translatable(P + "tahan.pending").withStyle(ChatFormatting.GOLD));
					}
					return lines;
				}).urgentWhen(this::tahanReady);
	}

	private boolean tahanReady() {
		return flag("trial_strikes") && path().getIntOr("tahan_left", -1) > 0 && !flag("tahan_pending");
	}

	// ---- Ikatan

	private void buildBond() {
		List<Widget> list = new ArrayList<>();
		list.add(tahanButton());
		int count = 0;
		for (Tag tag : path().getListOrEmpty("sealed_list")) {
			if (!(tag instanceof CompoundTag entry) || entry.getIntOr("id", 0) == screen.entityId()) {
				continue;
			}
			if (count >= MAX_RECALL) {
				break;
			}
			int id = entry.getIntOr("id", 0);
			String name = entry.getStringOr("name", "?");
			list.add(button(() -> Component.translatable(P + "recall", name, sealedValue(id, "dist")), () -> sealedValue(id, "cd") == 0,
					() -> act("recall:" + id), () -> {
						List<Component> lines = tip(P + "recall.name", P + "recall.tip", null);
						int cd = sealedValue(id, "cd");
						if (cd > 0) {
							lines.add(Component.translatable(P + "recall.cooldown", cd).withStyle(ChatFormatting.RED));
						}
						return lines;
					}));
			count++;
		}
		column(1, list);
	}

	/** A live value of a sealed disciple in the recall list (distance or cooldown seconds); -1 when it left the list. */
	private int sealedValue(int id, String key) {
		for (Tag tag : path().getListOrEmpty("sealed_list")) {
			if (tag instanceof CompoundTag entry && entry.getIntOr("id", 0) == id) {
				return entry.getIntOr(key, 0);
			}
		}
		return -1;
	}

	// ---- Pewaris

	private void buildHeir() {
		List<Widget> list = new ArrayList<>();
		list.add(button(() -> Component.translatable(flag("heir") ? P + "heir_done" : heirArmed() ? P + "heir_confirm" : P + "heir"),
				() -> !flag("heir") && path().getStringOr("heir_reason", "").isEmpty(), this::pressHeir, () -> {
					List<Component> lines = tip(P + "heir", P + "heir.tip", blocked(null, "heir_reason"));
					String other = path().getStringOr("heir_other", "");
					if (!other.isEmpty() && !flag("heir")) {
						lines.add(Component.translatable(P + "heir.replace", other).withStyle(ChatFormatting.GOLD));
					}
					lines.add(source("heir.source"));
					return lines;
				}).urgentWhen(this::heirArmed));
		list.add(new Picker());
		list.add(button(() -> Component.translatable(P + "gen_hex"), () -> {
			String id = current();
			return flag("heir") && id != null && listed("gen_options", id) && !id.equals(path().getStringOr("gen_hex", ""));
		}, () -> {
			String id = current();
			if (id != null) {
				act("gen_hex:" + id);
			}
		}, () -> sourced(tip(P + "gen_hex", P + "gen_hex.tip", null), "heir.source")));
		list.add(button(() -> Component.translatable(P + "hex", path().getIntOr("hex_pending", 0)), () -> {
			String id = current();
			return flag("heir") && path().getIntOr("hex_pending", 0) > 0 && id != null && listed("hex_options", id);
		}, () -> {
			String id = current();
			if (id != null) {
				act("hex:" + id);
			}
		}, () -> sourced(tip(P + "hex.name", P + "hex.tip", null), "heir.source")).urgentWhen(() -> flag("heir") && path().getIntOr("hex_pending", 0) > 0));
		list.add(button(() -> Component.translatable(P + "descend"), () -> path().getStringOr("descend_reason", "").isEmpty(), () -> act("descend"),
				() -> tip(P + "descend", P + "descend.tip", blocked(null, "descend_reason"))));
		list.add(button(() -> Component.translatable(P + "honor", onOff(flag("honor"))), () -> flag("surpassed"),
				() -> act("honor:" + (flag("honor") ? "off" : "on")), () -> {
					List<Component> lines = tip(P + "honor.name", P + "honor.tip", null);
					if (!flag("surpassed")) {
						lines.add(Component.translatable(P + "honor.locked").withStyle(ChatFormatting.RED));
					}
					lines.add(source("surpass.source"));
					return lines;
				}).stampedWhen(() -> flag("honor")));
		list.add(button(() -> Component.translatable(P + "duel"), () -> path().getStringOr("duel_reason", "").isEmpty(), () -> actAndClose("duel"),
				() -> sourced(tip(P + "duel", P + "duel.tip", blocked(null, "duel_reason")), "surpass.source")));
		column(2, list);
	}

	private boolean heirArmed() {
		return Util.getMillis() <= heirArmedUntil;
	}

	/** Naming the heir: with another heir the first click only arms the talisman (the old heir loses its hexes). */
	private void pressHeir() {
		String other = path().getStringOr("heir_other", "");
		if (other.isEmpty()) {
			act("heir");
		} else if (heirArmed()) {
			heirArmedUntil = 0L;
			act("heir_confirm");
		} else {
			heirArmedUntil = Util.getMillis() + CONFIRM_MILLIS;
			UiSound.ask();
		}
	}

	/** The techniques the picker cycles through: the hexed ones first, then those that could get a hex. */
	private List<String> options() {
		Set<String> set = new LinkedHashSet<>();
		CompoundTag p = path();
		for (String key : new String[]{"hexes", "hex_options", "gen_options"}) {
			for (Tag tag : p.getListOrEmpty(key)) {
				tag.asString().ifPresent(set::add);
			}
		}
		return new ArrayList<>(set);
	}

	private @Nullable String current() {
		List<String> options = options();
		return options.isEmpty() ? null : options.get(Math.floorMod(pick, options.size()));
	}

	private boolean listed(String key, String id) {
		for (Tag tag : path().getListOrEmpty(key)) {
			if (id.equals(tag.asString().orElse(""))) {
				return true;
			}
		}
		return false;
	}

	private static Component techniqueName(String id) {
		Technique technique = Technique.byId(id);
		return technique == null ? Component.literal(id) : technique.displayName();
	}

	/** "‹ technique ›": click the left half for the previous technique, the right half for the next. */
	private final class Picker extends Widget {

		Picker() {
			super(0, 0, 10, 10);
			tooltip(() -> {
				List<Component> lines = new ArrayList<>();
				lines.add(Component.translatable(P + "picker"));
				lines.add(Component.translatable(P + "picker.tip").withStyle(ChatFormatting.GRAY));
				String id = current();
				if (id != null) {
					if (id.equals(path().getStringOr("gen_hex", ""))) {
						lines.add(Component.translatable(P + "picker.gen").withStyle(ChatFormatting.GOLD));
					}
					if (listed("hexes", id)) {
						lines.add(Component.translatable(P + "picker.hexed").withStyle(ChatFormatting.GOLD));
					}
				}
				return lines;
			});
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean on = flag("heir") && !options().isEmpty();
			int top = on ? Palette.lighten(Palette.BAMBOO, 0.1F * hover.get()) : Palette.grey(Palette.BAMBOO, 0.7F);
			g.fillGradient(x, y, x + w, y + h, top, Palette.BAMBOO_ALT);
			Brush.frame(g, x, y, x + w, y + h, Palette.BAMBOO_KNOT);
			float mid = y + h / 2.0F;
			Brush.centered(g, font, Component.literal("‹"), x + 5.0F, mid - 4.0F, Palette.INK, 1.0F, false);
			Brush.centered(g, font, Component.literal("›"), x + w - 5.0F, mid - 4.0F, Palette.INK, 1.0F, false);
			String id = current();
			Component text = id == null ? Component.translatable(P + "picker.empty") : techniqueName(id);
			if (id != null) {
				Technique technique = Technique.byId(id);
				if (technique != null) {
					Glyphs.technique(g, technique, x + 15.0F, mid, Math.min(9.0F, h - 6.0F), Palette.GOLD_DARK);
				}
				if (id.equals(path().getStringOr("gen_hex", "")) || listed("hexes", id)) {
					Brush.disc(g, x + w - 13.0F, mid, 2.0F, Palette.SEAL);
				}
			}
			Brush.fitCentered(g, font, text, x + w / 2.0F + 3.0F, mid - 3.5F, w - 40.0F, on ? Palette.INK : Palette.INK_FAINT, 0.8F, 0.5F, false);
		}

		@Override
		public boolean release(double mouseX, double mouseY) {
			boolean was = pressed;
			pressed = false;
			if (!was || !contains(mouseX, mouseY)) {
				return false;
			}
			List<String> options = options();
			if (options.isEmpty()) {
				UiSound.denied();
				return false;
			}
			pick = Math.floorMod(pick + (mouseX < x + w / 2.0D ? -1 : 1), options.size());
			UiSound.tab();
			return true;
		}
	}

	// ---- Pemusnah

	private void buildArts() {
		List<Widget> list = new ArrayList<>();
		for (Tag tag : path().getListOrEmpty("arts")) {
			String id = tag.asString().orElse("");
			if (id.isEmpty()) {
				continue;
			}
			list.add(button(() -> Component.translatable("kultivator.dpath.art." + id), () -> true, () -> act("art:" + id), () -> artTip(id))
					.stampedWhen(() -> id.equals(path().getStringOr("art", ""))));
		}
		list.add(button(() -> Component.translatable(flag("k_destructive") ? P + "cast_destroy" : P + "cast"), this::castReady, () -> actAndClose("cast"),
				() -> {
					List<Component> lines = tip(flag("k_destructive") ? P + "cast_destroy" : P + "cast", flag("k_destructive") ? P + "cast_destroy.tip" : P + "cast.tip",
							null);
					if (!flag("art_eligible")) {
						lines.add(Component.translatable(P + "arts_locked", path().getIntOr("calamity", 0), DestroyerArts.MIN_CALAMITIES).withStyle(ChatFormatting.RED));
					} else if (path().getIntOr("art_cd", 0) > 0) {
						lines.add(Component.translatable(P + "art_cooldown", clock(path().getIntOr("art_cd", 0))).withStyle(ChatFormatting.RED));
					}
					return lines;
				}).urgentWhen(() -> castReady() && flag("k_destructive")));
		if (flag("k_server")) {
			list.add(button(() -> Component.translatable(flag("k_sworn") ? P + "oath_done" : P + "oath"), () -> !flag("k_sworn"),
					() -> send(Actions.KEHANCURAN, "oath"), () -> tip(P + "oath", P + "oath.tip", null)));
			list.add(button(() -> Component.translatable(flag("k_released") ? P + "seal_close" : P + "release"),
					() -> flag("k_released") || path().getStringOr("k_release_reason", "").isEmpty(), this::pressRelease, () -> {
						List<Component> lines = tip(flag("k_released") ? P + "seal_close" : P + "release",
								flag("k_released") ? P + "seal_close.tip" : P + "release.tip", null);
						String why = path().getStringOr("k_release_reason", "");
						if (!flag("k_released") && !why.isEmpty()) {
							lines.add(Component.translatable(why, name()).withStyle(ChatFormatting.RED));
						}
						return lines;
					}).urgentWhen(() -> flag("k_released")));
		}
		column(3, list);
	}

	private boolean castReady() {
		return flag("art_eligible") && !path().getStringOr("art", "").isEmpty() && path().getIntOr("art_cd", 0) <= 0 && !flag("running");
	}

	/** Lepaskan Segel opens the blood-red warning (the server checks the locks); closing the seal is one click. */
	private void pressRelease() {
		if (flag("k_released")) {
			send(Actions.KEHANCURAN, "seal");
			return;
		}
		KehancuranClient.remember(screen.entityId(), path(), name());
		send(Actions.KEHANCURAN, "release");
	}

	private List<Component> artTip(String id) {
		List<Component> lines = new ArrayList<>();
		DestroyerArts.Art art = DestroyerArts.Art.byId(id);
		lines.add(Component.translatable("kultivator.dpath.art." + id).withColor(art == null ? 0xFFFFFF : art.color()));
		lines.add(Component.translatable("kultivator.dpath.art." + id + ".safe").withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("kultivator.dpath.art." + id + ".destroy").withStyle(ChatFormatting.DARK_RED));
		lines.add(Component.translatable("kultivator.dpath.art." + id + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}

	// ================================================================== text helpers

	private Component name() {
		String name = info().getStringOr("name", "");
		if (!name.isEmpty()) {
			return Component.literal(name);
		}
		DiscipleEntity entity = screen.entity();
		return entity != null ? entity.getName() : Component.literal("?");
	}

	/**
	 * A reason key of the details as text, with the arguments its line needs: the disciple's name, then the bond needed
	 * and the seal limit for the Soul Seal and heir lines.
	 */
	private Component reason(String key) {
		if (key.contains(".why.seal") || key.contains(".why.sealed")) {
			return Component.translatable(key, name(), Bond.SEAL_BOND, path().getIntOr("seal_max", 3));
		}
		if (key.contains(".why.heir")) {
			return Component.translatable(key, name(), Bond.HEIR_BOND);
		}
		return Component.translatable(key, name());
	}

	/** The reason under {@code reasonKey} in red, or null when allowed ({@code okKey} true) or no reason is given. */
	private @Nullable Component blocked(@Nullable String okKey, String reasonKey) {
		if (okKey != null && flag(okKey)) {
			return null;
		}
		String key = path().getStringOr(reasonKey, "");
		return key.isEmpty() ? null : reason(key).copy().withStyle(ChatFormatting.RED);
	}

	private static List<Component> tip(String title, String body, @Nullable Component extra) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(title));
		lines.add(Component.translatable(body).withStyle(ChatFormatting.GRAY));
		if (extra != null) {
			lines.add(extra);
		}
		return lines;
	}

	/** A story-source line ({@code Sumber: ...}) in dark grey italics. */
	private static Component source(String key) {
		return Component.translatable(P + key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
	}

	/** The tooltip with its story-source line added at the end. */
	private static List<Component> sourced(List<Component> lines, String key) {
		lines.add(source(key));
		return lines;
	}

	private static Component onOff(boolean on) {
		return Component.translatable(on ? P + "on" : P + "off");
	}

	private static String clock(int seconds) {
		int s = Math.max(0, seconds);
		return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}

	private static String times(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag p = path();
		if (p.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(P + "waiting"), x + 4.0F, bodyTop, w - 8.0F, Palette.INK_SOFT, 0.75F, false);
			return;
		}
		g.enableScissor(x, bodyTop, x + leftW, y + h);
		switch (section) {
			case 0 -> drawStars(g, p, mouseX, mouseY);
			case 1 -> drawBond(g, p, mouseX, mouseY);
			case 2 -> drawHeir(g, p, mouseX, mouseY);
			default -> drawArts(g, p);
		}
		g.disableScissor();
	}

	/** A wrapped line in the left column; returns the next line's top. */
	private float line(GuiGraphicsExtractor g, Component text, float ty, int color, float scale) {
		return ty + Brush.wrap(g, font, text, x + 4.0F, ty, leftW - 8.0F, color, scale, false) + 1.5F;
	}

	/** A thin labelled bar with its numbers on the right and a tooltip. */
	private void bar(GuiGraphicsExtractor g, Component label, String numbers, float fraction, int color, int light, float top, int mouseX, int mouseY,
			List<Component> tip) {
		float x0 = x + 4.0F;
		float x1 = x + leftW - 4.0F;
		Component value = Component.literal(numbers);
		float valueW = Math.min((x1 - x0) * 0.55F, font.width(value) * 0.6F);
		Brush.fit(g, font, label, x0, top, x1 - x0 - valueW - 3.0F, Palette.INK_SOFT, 0.6F, 0.45F, false);
		Brush.fit(g, font, value, x1 - valueW, top, valueW, Palette.onPaper(color), 0.6F, 0.45F, false);
		float by = top + 7.0F;
		Brush.rect(g, x0, by, x1, by + 4.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
		Brush.rect(g, x0 + 0.5F, by + 0.5F, x1 - 0.5F, by + 3.5F, Palette.mix(Palette.PAPER_BOTTOM, color, 0.15F));
		float fill = x0 + 0.5F + (x1 - x0 - 1.0F) * Math.max(0.0F, Math.min(1.0F, fraction));
		if (fill > x0 + 0.6F) {
			Brush.rect(g, x0 + 0.5F, by + 0.5F, fill, by + 3.5F, color);
			Brush.rect(g, x0 + 0.5F, by + 0.5F, fill, by + 1.3F, light);
		}
		if (mouseX >= x0 && mouseX < x1 && mouseY >= top && mouseY < by + 5.0F) {
			screen.showTooltip(tip);
		}
	}

	// ---- Bintang

	private void drawStars(GuiGraphicsExtractor g, CompoundTag p, int mouseX, int mouseY) {
		int stars = p.getIntOr("stars", 0);
		float x0 = x + 4.0F;
		float room = leftW - 8.0F;
		float ty = bodyTop;
		if (stars > 0) {
			Component title = Component.translatable("kultivator.dpath.star.title", StarTitles.tierName(stars), stars)
					.withColor(Palette.onPaper(StarTitles.color(stars)) & 0xFFFFFF);
			Brush.fit(g, font, Brush.bold(title), x0, ty, room, Palette.INK, 1.0F, 0.6F, false);
		} else {
			Brush.fit(g, font, Brush.bold(Component.translatable(P + "no_stars")), x0, ty, room, Palette.INK_SOFT, 0.9F, 0.6F, false);
		}
		ty += 11.0F;
		if (!p.getBooleanOr("max_rank", false)) {
			ty = line(g, Component.translatable(P + "not_peak"), ty, Palette.INK_SOFT, 0.65F);
			ty = line(g, Component.translatable(P + "first_gate", p.getIntOr("first_gate", 6)), ty, Palette.INK_FAINT, 0.6F);
			line(g, Component.translatable(P + "stars_source").withStyle(ChatFormatting.ITALIC), ty + 2.0F, Palette.INK_FAINT, 0.55F);
			return;
		}
		double stored = p.getDoubleOr("stored", 0.0D);
		double cost = Math.max(1.0D, p.getDoubleOr("cost", 1.0D));
		bar(g, Component.translatable(P + "star_qi"), Msg.number(stored) + " / " + Msg.number(cost), (float) (stored / cost), Palette.STORED,
				Palette.STORED_LIGHT, ty, mouseX, mouseY, List.of(Component.translatable(P + "star_qi"),
						Component.translatable(P + "star_qi.tip", stars + 1, Msg.number(cost)).withStyle(ChatFormatting.GRAY)));
		ty += 13.0F;

		// The seven calamities in three Trials (2 + 2 + 3).
		int passed = p.getIntOr("calamity", 0);
		float px = x0 + 3.0F;
		float py = ty + 3.5F;
		for (int k = 1; k <= Calamity.COUNT; k++) {
			boolean done = k <= passed;
			boolean next = k == passed + 1;
			int color = done ? Palette.GOLD : next && p.getIntOr("pending_k", 0) == k ? Palette.SEAL : Palette.argb(0xA0, Palette.INK_FAINT);
			diamond(g, px, py, 3.0F, color);
			px += 8.0F + (k == 2 || k == 4 ? 4.0F : 0.0F);
		}
		Component calamities = Component.translatable(P + "calamities", passed, Calamity.COUNT);
		Brush.fit(g, font, calamities, px + 2.0F, ty, x0 + room - px - 2.0F, Palette.INK_SOFT, 0.6F, 0.45F, false);
		if (mouseX >= x0 && mouseX < px && mouseY >= ty && mouseY < ty + 8) {
			screen.showTooltip(List.of(Component.translatable(P + "calamities.name"), Component.translatable(P + "calamities.tip").withStyle(ChatFormatting.GRAY),
					Component.translatable(P + "calamities.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
		ty += 10.0F;
		if (passed >= Calamity.COUNT) {
			ty = line(g, Component.translatable(P + "trampling").withColor(Palette.GOLD_DARK & 0xFFFFFF), ty, Palette.GOLD_DARK, 0.65F);
		} else {
			ty = line(g, Component.translatable(P + "next_gate", passed + 1, p.getIntOr("gate", 0)), ty, Palette.INK_SOFT, 0.6F);
		}
		if (p.getBooleanOr("running", false)) {
			ty = line(g, Component.translatable(P + "calamity_running"), ty, Palette.SEAL_DARK, 0.6F);
		}
		ty = line(g, Component.translatable(P + "trial_factor", p.getIntOr("trials", 0), times(p.getDoubleOr("trial_factor", 1.0D))), ty, Palette.INK_SOFT, 0.6F);

		// The star bonuses.
		ty += 1.0F;
		ty = line(g, Component.translatable(P + "bonus_damage", times(p.getDoubleOr("technique_factor", 1.0D)), times(p.getDoubleOr("melee_factor", 1.0D))),
				ty, Palette.INK, 0.6F);
		ty = line(g, Component.translatable(P + "bonus_body", Math.round(p.getDoubleOr("health_bonus", 0.0D)), times(p.getDoubleOr("max_qi_factor", 1.0D))),
				ty, Palette.INK, 0.6F);
		Component meditation = Component.translatable(P + "bonus_meditation", times(p.getDoubleOr("meditation_factor", 1.0D)));
		if (p.getBooleanOr("night", false)) {
			meditation = Component.translatable(P + "bonus_meditation_night", times(p.getDoubleOr("meditation_factor", 1.0D) * 2.0D));
		}
		ty = line(g, meditation, ty, Palette.INK, 0.6F);
		double reduction = p.getDoubleOr("reduction", 0.0D);
		if (reduction > 0.0D) {
			ty = line(g, Component.translatable(P + "bonus_reduction", Math.round(reduction * 100.0D)), ty, Palette.INK, 0.6F);
		}

		// Divine Clones and soul lamps.
		int clones = p.getIntOr("clones_allowed", 0);
		if (clones > 0) {
			int cd = p.getIntOr("clone_cd", 0);
			ty = line(g, cd > 0 ? Component.translatable(P + "clones_cd", clones, clock(cd)) : Component.translatable(P + "clones", clones,
					p.getIntOr("clone_active", 0)), ty, Palette.JADE_DARK, 0.6F);
		} else {
			ty = line(g, Component.translatable(P + "clones_locked"), ty, Palette.INK_FAINT, 0.55F);
		}
		if (p.getBooleanOr("trampling", false)) {
			int lit = p.getIntOr("lamps_lit", 0);
			int out = p.getIntOr("lamps_out", 0);
			ty = line(g, Component.translatable(P + "lamps", out, lit, SoulLamps.MAX_LAMPS), ty, Palette.INK_SOFT, 0.6F);
			float lx = x0 + 2.0F;
			float dot = Math.max(2.0F, Math.min(4.0F, (room - 4.0F) / SoulLamps.MAX_LAMPS - 1.0F));
			for (int i = 0; i < SoulLamps.MAX_LAMPS; i++) {
				int color = i < out ? Palette.argb(0xFF, 0x2A3A3A) : i < lit ? 0xFF7FF0E0 : Palette.argb(0x70, Palette.INK_FAINT);
				Brush.rect(g, lx, ty, lx + dot, ty + dot + 1.0F, color);
				if (i < lit && i >= out) {
					Brush.rect(g, lx + dot * 0.3F, ty - 1.0F, lx + dot * 0.7F, ty, 0xFFFFF4C0);
				}
				lx += dot + 1.0F;
			}
		}
	}

	private static void diamond(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
		for (int i = 0; i < 3; i++) {
			float half = r * (1.0F - i / 3.0F);
			float dy = r * i / 3.0F;
			Brush.rect(g, cx - half, cy - dy - 0.6F, cx + half, cy - dy + 0.6F, color);
			if (i > 0) {
				Brush.rect(g, cx - half, cy + dy - 0.6F, cx + half, cy + dy + 0.6F, color);
			}
		}
	}

	// ---- Ikatan

	private void drawBond(GuiGraphicsExtractor g, CompoundTag p, int mouseX, int mouseY) {
		float x0 = x + 4.0F;
		float x1 = x + leftW - 4.0F;
		float ty = bodyTop;
		int bond = p.getIntOr("bond", 0);
		bar(g, Component.translatable(P + "bond"), bond + " / 100", bond / 100.0F, 0xFFE07A9A, 0xFFF6C2D0, ty, mouseX, mouseY,
				List.of(Component.translatable(P + "bond"), Component.translatable(P + "bond.tip").withStyle(ChatFormatting.GRAY),
						Component.translatable(P + "bond.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		// Marks at 50 (Soul Seal) and 80 (heir).
		for (int mark : new int[]{Bond.SEAL_BOND, Bond.HEIR_BOND}) {
			float mx = x0 + 0.5F + (x1 - x0 - 1.0F) * mark / 100.0F;
			Brush.rect(g, mx - 0.5F, ty + 6.0F, mx + 0.5F, ty + 12.0F, bond >= mark ? Palette.GOLD_DARK : Palette.INK_SOFT);
		}
		ty += 14.0F;

		// The Soul Seal.
		if (p.getBooleanOr("sealed", false)) {
			ty = line(g, Component.translatable(P + "sealed", p.getIntOr("seal_count", 0), p.getIntOr("seal_max", 3)), ty, Palette.JADE_DARK, 0.65F);
		} else {
			ty = line(g, Component.translatable(P + "not_sealed", p.getIntOr("seal_count", 0), p.getIntOr("seal_max", 3)), ty, Palette.INK_SOFT, 0.65F);
			String why = p.getStringOr("seal_reason", "");
			if (!why.isEmpty()) {
				ty = line(g, reason(why), ty, Palette.SEAL_DARK, 0.55F);
			}
			ty = line(g, Component.translatable(P + "seal_hint"), ty, Palette.INK_FAINT, 0.55F);
		}
		float progress = p.getFloatOr("seal_progress", -1.0F);
		if (progress >= 0.0F) {
			Brush.rect(g, x0, ty, x1, ty + 3.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
			Brush.rect(g, x0 + 0.5F, ty + 0.5F, x0 + 0.5F + (x1 - x0 - 1.0F) * progress, ty + 2.5F, Palette.JADE);
			ty += 5.0F;
		}
		if (p.getListOrEmpty("sealed_list").size() <= (p.getBooleanOr("sealed", false) ? 1 : 0)) {
			ty = line(g, Component.translatable(P + "recall_none"), ty, Palette.INK_FAINT, 0.55F);
		}

		// Hu Fa.
		ty += 2.0F;
		Component hufa;
		int hufaColor;
		if (!p.getBooleanOr("trial", false)) {
			hufa = Component.translatable(P + "hufa_idle");
			hufaColor = Palette.INK_SOFT;
		} else if (p.getBooleanOr("hufa", false)) {
			hufa = Component.translatable(P + "hufa_on");
			hufaColor = Palette.JADE_DARK;
		} else {
			hufa = Component.translatable(P + "hufa_off");
			hufaColor = Palette.SEAL_DARK;
		}
		float hufaTop = ty;
		ty = line(g, hufa, ty, hufaColor, 0.62F);
		if (p.getBooleanOr("trial_strikes", false)) {
			ty = line(g, Component.translatable(P + "tahan_left", Math.max(0, p.getIntOr("tahan_left", 0)), HuFa.TAHAN_MAX), ty, Palette.INK_SOFT, 0.58F);
		}
		if (mouseX >= x0 && mouseX < x1 && mouseY >= hufaTop && mouseY < ty) {
			screen.showTooltip(List.of(Component.translatable(P + "hufa.name"), Component.translatable(P + "hufa.tip").withStyle(ChatFormatting.GRAY),
					Component.translatable(P + "hufa.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}

		// The Dao foundation's pillars.
		int type = p.getIntOr("foundation", 0);
		ty += 2.0F;
		Component foundation = type <= 0 ? Component.translatable(P + "foundation_none")
				: Component.translatable(P + "foundation", Component.translatable("kultivator.dpath.foundation." + FoundationTypes.id(type)), p.getIntOr("pillars", 9));
		float fTop = ty;
		ty = line(g, foundation, ty, type <= 0 ? Palette.INK_FAINT : Palette.onPaper(FoundationTypes.color(type)), 0.62F);
		if (type > 0) {
			ty = pillars(g, type, p.getIntOr("pillars", 9), x0 + 2.0F, ty + 1.0F, Math.min(x1 - x0 - 4.0F, 110.0F));
		}
		if (mouseX >= x0 && mouseX < x1 && mouseY >= fTop && mouseY < ty) {
			screen.showTooltip(List.of(Component.translatable(P + "foundation.name"), Component.translatable(P + "foundation.tip").withStyle(ChatFormatting.GRAY),
					Component.translatable(P + "foundation.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
	}

	/** Foundation pillars: fractured (Fana), cracked (Bumi), flawless gold (Langit), ten white-gold (Sempurna). */
	private static float pillars(GuiGraphicsExtractor g, int type, int count, float x0, float top, float width) {
		int n = Math.max(1, count);
		float pitch = width / n;
		float pw = Math.max(2.0F, pitch * 0.55F);
		float ph = 16.0F;
		int color = Palette.opaque(FoundationTypes.color(type));
		int body = type == FoundationTypes.SEMPURNA ? 0xFFFFF7E0 : type == FoundationTypes.LANGIT ? 0xFFF2CF55 : Palette.mix(Palette.PAPER_DEEP, color, 0.6F);
		for (int i = 0; i < n; i++) {
			float px = x0 + i * pitch + (pitch - pw) / 2.0F;
			Brush.rect(g, px - 0.8F, top + ph - 1.5F, px + pw + 0.8F, top + ph, Palette.INK_SOFT);
			Brush.rect(g, px - 0.6F, top, px + pw + 0.6F, top + 1.5F, Palette.INK_SOFT);
			Brush.rect(g, px, top + 1.5F, px + pw, top + ph - 1.5F, body);
			if (type == FoundationTypes.FANA) {
				Brush.line(g, px, top + 4.0F + i % 3, px + pw, top + 7.0F + i % 3, 0.6F, Palette.INK);
				Brush.line(g, px + pw, top + 9.0F, px + pw * 0.3F, top + 12.0F, 0.6F, Palette.INK);
			} else if (type == FoundationTypes.BUMI) {
				Brush.line(g, px + pw * 0.2F, top + 6.0F + i % 2 * 3, px + pw * 0.8F, top + 8.0F + i % 2 * 3, 0.5F, Palette.INK_SOFT);
			} else if (type == FoundationTypes.SEMPURNA) {
				Brush.rect(g, px + pw * 0.4F, top + 2.0F, px + pw * 0.6F, top + ph - 2.0F, 0xFFFFFFFF);
			}
		}
		return top + ph + 3.0F;
	}

	// ---- Pewaris

	private void drawHeir(GuiGraphicsExtractor g, CompoundTag p, int mouseX, int mouseY) {
		float x0 = x + 4.0F;
		float hx1 = x + leftW - 4.0F;
		float ty = bodyTop;
		boolean heir = p.getBooleanOr("heir", false);
		if (heir) {
			ty = line(g, Brush.bold(Component.translatable(P + "heir_status", p.getIntOr("heir_gen", 0))), ty, Palette.GOLD_DARK, 0.7F);
		} else {
			ty = line(g, Component.translatable(P + "heir_not"), ty, Palette.INK_SOFT, 0.65F);
			String why = p.getStringOr("heir_reason", "");
			if (!why.isEmpty()) {
				ty = line(g, reason(why), ty, Palette.SEAL_DARK, 0.55F);
			}
			String other = p.getStringOr("heir_other", "");
			if (!other.isEmpty()) {
				ty = line(g, Component.translatable(P + "heir.replace", other), ty, Palette.WARN, 0.55F);
			}
		}
		ty = line(g, Component.translatable(P + "generation", p.getIntOr("generation", 0)), ty, Palette.INK_FAINT, 0.55F);
		if (mouseX >= x0 && mouseX < hx1 && mouseY >= bodyTop && mouseY < ty) {
			screen.showTooltip(List.of(Component.translatable(P + "heir"), Component.translatable(P + "heir.tip").withStyle(ChatFormatting.GRAY),
					source("heir.source")));
		}
		float progress = p.getFloatOr("heir_progress", -1.0F);
		if (progress >= 0.0F) {
			float x1 = x + leftW - 4.0F;
			Brush.rect(g, x0, ty, x1, ty + 3.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
			Brush.rect(g, x0 + 0.5F, ty + 0.5F, x0 + 0.5F + (x1 - x0 - 1.0F) * progress, ty + 2.5F, Palette.JADE);
			ty += 5.0F;
		}

		// The Nine Hexes: a 3 x 3 grid, and the Generation Hex beside it.
		if (heir) {
			List<String> hexes = new ArrayList<>();
			for (Tag tag : p.getListOrEmpty("hexes")) {
				tag.asString().ifPresent(hexes::add);
			}
			float cell = 13.0F;
			float gx = x0 + 2.0F;
			float gy = ty + 1.0F;
			for (int i = 0; i < Heir.MAX_HEXES; i++) {
				float cx = gx + (i % 3) * (cell + 2.0F);
				float cy = gy + (i / 3) * (cell + 2.0F);
				boolean filled = i < hexes.size();
				g.fill(Math.round(cx), Math.round(cy), Math.round(cx + cell), Math.round(cy + cell), filled ? Palette.argb(0xC0, Palette.SEAL) : Palette.argb(0x40, Palette.PAPER_DEEP));
				Brush.frame(g, Math.round(cx), Math.round(cy), Math.round(cx + cell), Math.round(cy + cell), Palette.argb(0xC0, Palette.GOLD));
				if (filled) {
					Technique technique = Technique.byId(hexes.get(i));
					if (technique != null) {
						Glyphs.technique(g, technique, cx + cell / 2.0F, cy + cell / 2.0F, cell * 0.6F, Palette.TALISMAN);
					}
				}
			}
			float tx = gx + 3 * (cell + 2.0F) + 4.0F;
			float room = x + leftW - 4.0F - tx;
			float ly = gy;
			Brush.fit(g, font, Component.translatable(P + "hexes", hexes.size(), Heir.MAX_HEXES), tx, ly, room, Palette.INK, 0.65F, 0.45F, false);
			ly += 8.0F;
			String gen = p.getStringOr("gen_hex", "");
			Brush.fit(g, font, Component.translatable(P + "gen_hex_now", gen.isEmpty() ? Component.translatable(P + "none") : techniqueName(gen)), tx, ly, room,
					Palette.GOLD_DARK, 0.6F, 0.45F, false);
			ly += 8.0F;
			int pending = p.getIntOr("hex_pending", 0);
			if (pending > 0) {
				Brush.fit(g, font, Component.translatable(P + "hex_pending", pending), tx, ly, room, Palette.SEAL_DARK, 0.6F, 0.45F, false);
				ly += 8.0F;
			}
			if (hexes.size() >= Heir.MAX_HEXES) {
				Brush.fit(g, font, Component.translatable(P + "nine"), tx, ly, room, Palette.GOLD_DARK, 0.6F, 0.45F, false);
			}
			ty = gy + 3 * (cell + 2.0F) + 3.0F;
		}

		// The Master's Soul Ring.
		ty += 1.0F;
		if (!p.getBooleanOr("ring", false)) {
			ty = line(g, Component.translatable(P + "ring_none"), ty, Palette.INK_FAINT, 0.6F);
		} else if (p.getIntOr("ring_stage", 0) == 0) {
			ty = line(g, Component.translatable(P + "ring_awaken", clock(p.getIntOr("ring_left", 0))), ty, Palette.JADE_DARK, 0.6F);
		} else {
			ty = line(g, Component.translatable(P + "ring_bonded"), ty, Palette.GOLD_DARK, 0.6F);
			if (p.getBooleanOr("descend", false)) {
				ty = line(g, Component.translatable(P + "descend_active"), ty, Palette.SEAL_DARK, 0.6F);
			} else if (p.getIntOr("descend_cd", 0) > 0) {
				ty = line(g, Component.translatable(P + "descend_cd", clock(p.getIntOr("descend_cd", 0))), ty, Palette.INK_SOFT, 0.55F);
			}
		}

		// Surpassing the master.
		ty += 1.0F;
		float surpassTop = ty;
		ty = line(g, Component.translatable(P + "power", p.getIntOr("power", 0), p.getIntOr("master_power", 0)), ty, Palette.INK_SOFT, 0.6F);
		ty = line(g, Component.translatable(p.getBooleanOr("surpassed", false) ? P + "surpassed" : P + "not_surpassed"), ty,
				p.getBooleanOr("surpassed", false) ? Palette.GOLD_DARK : Palette.INK_FAINT, 0.58F);
		if (mouseX >= x0 && mouseX < hx1 && mouseY >= surpassTop && mouseY < ty) {
			screen.showTooltip(List.of(Component.translatable(p.getBooleanOr("surpassed", false) ? P + "surpassed" : P + "not_surpassed"),
					source("surpass.source")));
		}
	}

	// ---- Pemusnah

	private void drawArts(GuiGraphicsExtractor g, CompoundTag p) {
		float ty = bodyTop;
		if (!p.getBooleanOr("art_eligible", false)) {
			ty = line(g, Brush.bold(Component.translatable(P + "arts_locked", p.getIntOr("calamity", 0), DestroyerArts.MIN_CALAMITIES)), ty, Palette.INK_SOFT, 0.65F);
			ty = line(g, Component.translatable(P + "arts_intro"), ty, Palette.INK_FAINT, 0.58F);
		} else {
			String art = p.getStringOr("art", "");
			if (art.isEmpty()) {
				ty = line(g, Component.translatable(P + "arts_none"), ty, Palette.INK_SOFT, 0.62F);
			} else {
				DestroyerArts.Art value = DestroyerArts.Art.byId(art);
				int color = value == null ? Palette.INK : Palette.onPaper(value.color());
				ty = line(g, Brush.bold(Component.translatable("kultivator.dpath.art." + art)), ty, color, 0.75F);
				ty = line(g, Component.translatable("kultivator.dpath.art." + art + ".safe"), ty, Palette.INK, 0.58F);
				String kind = p.getStringOr("art_kind", "");
				Component shape = "trench".equals(kind)
						? Component.translatable(P + "art_shape_trench", p.getIntOr("art_length", 0), 3, p.getIntOr("art_depth", 0))
						: Component.translatable(P + "art_shape", Component.translatable("kultivator.kehancuran.shape." + (kind.isEmpty() ? "sphere" : kind)),
								p.getIntOr("art_radius", 0));
				ty = line(g, shape, ty, Palette.INK_SOFT, 0.56F);
				int cd = p.getIntOr("art_cd", 0);
				ty = line(g, cd > 0 ? Component.translatable(P + "art_cooldown", clock(cd)) : Component.translatable(P + "art_ready"), ty,
						cd > 0 ? Palette.SEAL_DARK : Palette.JADE_DARK, 0.58F);
				ty = line(g, Component.translatable("kultivator.dpath.art." + art + ".source").withStyle(ChatFormatting.ITALIC), ty, Palette.INK_FAINT, 0.5F);
			}
		}

		// The red seal of Mode Kehancuran.
		ty += 3.0F;
		heading(g, Component.translatable(P + "red_seal"), x + 2.0F, ty, leftW - 4.0F, 0.7F);
		ty += 11.0F;
		if (!p.getBooleanOr("k_server", false)) {
			line(g, Component.translatable(P + "k_off"), ty, Palette.INK_SOFT, 0.58F);
			return;
		}
		ty = lockLine(g, ty, true, Component.translatable(P + "k_lock_server"));
		ty = lockLine(g, ty, p.getBooleanOr("k_sworn", false), Component.translatable(P + "k_lock_oath"));
		ty = lockLine(g, ty, p.getBooleanOr("k_released", false), Component.translatable(P + "k_lock_release"));
		String order = p.getStringOr("k_order", "");
		if (!order.isEmpty()) {
			ty = line(g, Component.translatable(P + "k_order." + order), ty, Palette.SEAL_DARK, 0.6F);
		}
		line(g, Component.translatable(p.getBooleanOr("k_destructive", false) ? P + "k_destructive" : P + "k_safe"), ty,
				p.getBooleanOr("k_destructive", false) ? Palette.DANGER : Palette.JADE_DARK, 0.56F);
	}

	private float lockLine(GuiGraphicsExtractor g, float ty, boolean open, Component text) {
		int color = open ? Palette.DANGER : Palette.JADE;
		Glyphs.lock(g, x + 8.0F, ty + 3.5F, 7.0F, color);
		Component state = Component.translatable(open ? "kultivator.kehancuran.lock.open" : "kultivator.kehancuran.lock.closed").withColor(color & 0xFFFFFF);
		return ty + Brush.wrap(g, font, Component.translatable("kultivator.kehancuran.ui.lock_line", text, state), x + 15.0F, ty, leftW - 19.0F,
				Palette.INK, 0.6F, false) + 1.5F;
	}
}
