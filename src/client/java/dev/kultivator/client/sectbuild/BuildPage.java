package dev.kultivator.client.sectbuild;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.hall.HallPage;
import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SectionPicker;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.flow.Deposits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Bangun tablet of the Sect Hall scroll, placed second after Aula (DESIGN 21.21): on the left the 120x120 site
 * diagram (done parts in full ink, the open one with a pulsing gold edge and its percent, queued ones as faint outlines,
 * blocked ones with a red seal, future tiers faded; hovering names a part with its tier and bill), on the right the chosen
 * panel, picked from a vertical seal column on the right edge (no top tab row):
 * <ul>
 * <li><b>Ikhtisar</b>: "Tingkat 4/9 · Tembok dan Air", the tier ladder drawn as a mountain path of nine terraces, the open
 * building, the rate ("≈ 22 blok/detik · 3 pekerja · x2 tabungan"), the status, what the next building still needs and
 * the talismans <i>Setor dari tas</i> (only what is needed) and the <i>Jeda</i> coin;</li>
 * <li><b>Bahan</b>: bamboo-slip bill bars per category for this tier and the next, exact items, the deposit grid of the
 * bag (click: a stack, shift-click: all of that item), "Setor yang dibutuhkan" and "Ambil dari Lumbung";</li>
 * <li><b>Misi</b>: tier missions, daily offers, gathering parties and sending one;</li>
 * <li><b>Pekerja</b>: up to four builders; <b>Galian</b>: the Peti Galian, the surplus and the "Pakai hasil galian" coin;</li>
 * <li><b>Templat</b>: upgrade, repair, deconstruct, abandon, glaze, history and the obstacle list with "Periksa ulang",
 * "Lewati bagian ini", "Selesaikan tanpa blok terhalang" and "Prioritaskan"; <b>Penyumbang</b>: donors (LAN only).</li>
 * </ul>
 * A sect mate sees it read-only and may deposit. Fits 320x240 (the panels scroll with the wheel). Jade scroll of DESIGN 5b.
 */
public class BuildPage extends HallPage {

	private static final String[] PANELS = {"overview", "materials", "missions", "builders", "salvage", "template", "donors"};
	private static final int[] PANEL_ICONS = {Glyphs.BUILD, Glyphs.TREASURE, Glyphs.PATH, Glyphs.TALENT, Glyphs.SEAL, Glyphs.HALL, Glyphs.STATUS};
	private static final int SEAL_W = 15;
	private static final int BUTTON_H = 12;
	private static final float TEXT = 0.6F;

	private static int panel;
	private static int missionView;
	private static int gatherCat;
	private static boolean gatherLong;
	private static final Set<String> PARTY = new HashSet<>();
	private static final Map<Integer, Float> SCROLL = new LinkedHashMap<>();

	/** A clickable area drawn this frame (list rows, chips, seals). */
	private record Hit(float x0, float y0, float x1, float y1, Runnable action, @Nullable List<Component> tip) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private @Nullable CompoundTag partsFor;
	private List<SiteDiagram.Part> parts = List.of();
	private SiteDiagram.Bounds bounds = SiteDiagram.Bounds.of(1);
	private int diagram;
	private int panelX;
	private int panelW;
	private int panelTop;
	private int listTop;
	private int listBottom;
	private float contentHeight;
	private boolean askDeconstruct;
	private boolean askTotal;
	private boolean askAbandon;
	private long askUntil;

	public BuildPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6b.page.bangun");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6b.page.bangun.short");
	}

	@Override
	public int icon() {
		return Glyphs.BUILD;
	}

	// ================================================================== data

	private CompoundTag b() {
		return info().getCompoundOrEmpty("build");
	}

	private int template() {
		return b().getIntOr("t", 0);
	}

	private int tier() {
		return b().getIntOr("tier", 0);
	}

	private CompoundTag site() {
		return b().getCompoundOrEmpty("site");
	}

	private boolean readonly() {
		return b().getBooleanOr("readonly", false);
	}

	private Bill bill(String key) {
		return Bill.readFrom(b().getCompoundOrEmpty(key));
	}

	private boolean lan() {
		return b().getBooleanOr("lan", false);
	}

	private void act(String text) {
		BuildClient.send(0, text);
	}

	/** Re-reads the diagram parts when new details arrived. */
	private void refreshParts() {
		CompoundTag current = info();
		if (current == partsFor) {
			return;
		}
		partsFor = current;
		CompoundTag site = site();
		int[] stele = site.getIntArray("stele").orElse(null);
		int[] fp = site.getIntArray("fp").orElse(null);
		int rot = site.getIntOr("rot", 0);
		if (template() > 0 && stele != null && stele.length == 3 && fp != null && fp.length == 6 && !b().getListOrEmpty("comps").isEmpty()) {
			parts = SiteDiagram.fromComponents(b().getListOrEmpty("comps"), stele, rot);
			bounds = SiteDiagram.bounds(fp, stele, rot);
		} else {
			int t = Math.max(1, template());
			parts = SiteDiagram.sketch(t);
			bounds = SiteDiagram.Bounds.of(t);
		}
	}

	// ================================================================== layout and controls

	@Override
	protected void build() {
		diagram = Math.max(64, Math.min(120, Math.min(h - 12, Math.round(w * 0.36F))));
		panelX = x + diagram + 6;
		panelW = x + w - SEAL_W - 3 - panelX;
		panelTop = y + 12;
		if (!lan() && panel == 6) {
			panel = 0;
		}
		if (template() <= 0) {
			buildFounding();
			return;
		}
		switch (panel) {
			case 0 -> buildOverview();
			case 1 -> buildMaterials();
			case 2 -> buildMissions();
			case 3 -> {
			}
			case 4 -> buildSalvage();
			case 5 -> buildTemplate();
			default -> {
			}
		}
	}

	@Override
	public boolean needsRebuild() {
		CompoundTag build = b();
		return build.getIntOr("t", 0) > 0 != builtWithTemplate || build.getBooleanOr("readonly", false) != builtReadonly;
	}

	private boolean builtWithTemplate;
	private boolean builtReadonly;

	@Override
	public void onInfo() {
		builtWithTemplate = template() > 0;
		builtReadonly = readonly();
	}

	private TalismanButton talisman(int bx, int by, int bw, String key, Runnable action) {
		TalismanButton button = new TalismanButton(bx, by, bw, BUTTON_H, () -> Component.translatable(key));
		button.onPress(action);
		add(button);
		return button;
	}

	private void buildFounding() {
		int bw = Math.min(120, panelW);
		TalismanButton choose = talisman(panelX, y + h - 2 * BUTTON_H - 6, bw, "kultivator.sectbuild.ui.choose", () -> BuildClient.openTemplates(0, false));
		choose.enabledWhen(() -> !readonly() && b().getBooleanOr("engine", false) && b().getBooleanOr("on", true));
		choose.urgentWhen(() -> !readonly());
		JadeCoin self = new JadeCoin(panelX, y + h - BUTTON_H - 2, bw, 11, () -> Component.translatable("kultivator.sectbuild.ui.self_build"),
				() -> b().getCompoundOrEmpty("flags").getBooleanOr("self", false));
		self.onPress(() -> act("flag:self:" + (b().getCompoundOrEmpty("flags").getBooleanOr("self", false) ? "0" : "1")));
		self.enabledWhen(() -> !readonly());
		self.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.self_build"), Component.translatable("kultivator.sectbuild.ui.self_build_tip")));
		add(self);
	}

	private void buildOverview() {
		int half = (panelW - 3) / 2;
		int by = y + h - BUTTON_H;
		TalismanButton needed = talisman(panelX, by, half, "kultivator.sectbuild.ui.deposit_needed", () -> act("deposit_needed"));
		needed.urgentWhen(() -> site().getIntOr("status", 0) == SiteView.STATUS_WAITING_FUNDS);
		needed.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.deposit_needed"), Component.translatable("kultivator.sectbuild.ui.deposit_needed_tip")));
		TalismanButton all = talisman(panelX + half + 3, by, half, "kultivator.sectbuild.ui.deposit_all", () -> act("deposit_all"));
		all.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.deposit_all"), Component.translatable("kultivator.sectbuild.ui.deposit_all_tip")));
		JadeCoin pause = new JadeCoin(panelX, by - 13, half, 11, () -> Component.translatable("kultivator.sectbuild.ui.pause"),
				() -> site().getIntOr("state", 0) == SiteView.STATE_PAUSED);
		pause.onPress(() -> act("pause:" + (site().getIntOr("state", 0) == SiteView.STATE_PAUSED ? "0" : "1")));
		pause.enabledWhen(() -> !readonly());
		add(pause);
		TalismanButton screenButton = talisman(panelX + half + 3, by - 13, half, "kultivator.sectbuild.ui.templates", () -> BuildClient.openTemplates(template(), false));
		screenButton.enabledWhen(() -> true);
	}

	private void buildMaterials() {
		int third = (panelW - 4) / 3;
		int by = y + h - BUTTON_H;
		TalismanButton needed = talisman(panelX, by, third, "kultivator.sectbuild.ui.deposit_needed_short", () -> act("deposit_needed"));
		needed.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.deposit_needed"), Component.translatable("kultivator.sectbuild.ui.deposit_needed_tip")));
		talisman(panelX + third + 2, by, third, "kultivator.sectbuild.ui.deposit_all_short", () -> act("deposit_all"));
		TalismanButton store = talisman(panelX + 2 * (third + 2), by, third, "kultivator.sectbuild.ui.storehouse", () -> act("lumbung"));
		store.enabledWhen(() -> !readonly());
		store.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.storehouse"), Component.translatable("kultivator.sectbuild.ui.storehouse_tip")));
	}

	private void buildMissions() {
		SectionPicker picker = new SectionPicker(panelX, panelTop, panelW, 11, new String[]{"kultivator.sectbuild.ui.missions_view",
				"kultivator.sectbuild.ui.gather_view"}, () -> missionView, index -> {
			missionView = index;
			SCROLL.put(2, 0.0F);
			layout(x, y, w, h);
		});
		add(picker);
		if (missionView == 1) {
			int by = y + h - BUTTON_H;
			int half = (panelW - 3) / 2;
			SectionPicker cats = new SectionPicker(panelX, panelTop + 13, panelW, 11, catKeys(), () -> gatherCat, index -> gatherCat = index);
			add(cats);
			JadeCoin length = new JadeCoin(panelX, by - 13, panelW, 11, () -> Component.translatable(gatherLong ? "kultivator.sectbuild.ui.trip_long"
					: "kultivator.sectbuild.ui.trip_short"), () -> gatherLong);
			length.onPress(() -> gatherLong = !gatherLong);
			add(length);
			TalismanButton send = talisman(panelX, by, panelW, "kultivator.sectbuild.ui.send_party", () -> {
				act("gather:" + Bahan.values()[gatherCat].id() + ":" + (gatherLong ? "1" : "0") + ":" + String.join(",", PARTY));
				PARTY.clear();
			});
			send.enabledWhen(() -> !readonly() && !PARTY.isEmpty() && parties() < b().getIntOr("party_max", 1));
			send.urgentWhen(() -> !PARTY.isEmpty());
		}
	}

	private void buildSalvage() {
		int by = y + h - BUTTON_H;
		JadeCoin excavation = new JadeCoin(panelX, by, panelW, 11, () -> Component.translatable("kultivator.sectbuild.ui.use_excavation"),
				() -> b().getCompoundOrEmpty("flags").getBooleanOr("exc", true));
		excavation.onPress(() -> act("flag:exc:" + (b().getCompoundOrEmpty("flags").getBooleanOr("exc", true) ? "0" : "1")));
		excavation.enabledWhen(() -> !readonly());
		excavation.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.use_excavation"), Component.translatable("kultivator.sectbuild.ui.use_excavation_tip")));
		add(excavation);
		if (b().getCompoundOrEmpty("flags").getBooleanOr("can_auth", false)) {
			int half = (panelW - 3) / 2;
			JadeCoin remove = new JadeCoin(panelX, by - 13, half, 11, () -> Component.translatable("kultivator.sectbuild.ui.remove_player"),
					() -> b().getCompoundOrEmpty("flags").getBooleanOr("rm", false));
			remove.onPress(() -> act("flag:rm:" + (b().getCompoundOrEmpty("flags").getBooleanOr("rm", false) ? "0" : "1")));
			remove.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.remove_player"), Component.translatable("kultivator.sectbuild.ui.remove_player_tip")));
			add(remove);
			JadeCoin empty = new JadeCoin(panelX + half + 3, by - 13, half, 11, () -> Component.translatable("kultivator.sectbuild.ui.empty_containers"),
					() -> b().getCompoundOrEmpty("flags").getBooleanOr("empty", false));
			empty.onPress(() -> act("flag:empty:" + (b().getCompoundOrEmpty("flags").getBooleanOr("empty", false) ? "0" : "1")));
			empty.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.empty_containers"), Component.translatable("kultivator.sectbuild.ui.empty_containers_tip")));
			add(empty);
		}
	}

	private void buildTemplate() {
		int half = (panelW - 3) / 2;
		int by = y + h - BUTTON_H;
		TalismanButton upgrade = talisman(panelX, by - 2 * (BUTTON_H + 2), half, "kultivator.sectbuild.ui.upgrade", () -> BuildClient.openTemplates(Math.min(9,
				template() + 1), true));
		upgrade.enabledWhen(() -> !readonly() && template() < b().getIntOr("max_t", 9));
		TalismanButton repair = talisman(panelX + half + 3, by - 2 * (BUTTON_H + 2), half, "kultivator.sectbuild.ui.repair", () -> act("repair"));
		repair.enabledWhen(() -> !readonly());
		repair.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.repair"), Component.translatable("kultivator.sectbuild.ui.repair_tip")));
		JadeCoin deconstruct = new JadeCoin(panelX, by - BUTTON_H - 2, half, 11, () -> Component.translatable("kultivator.sectbuild.ui.deconstruct"),
				() -> false);
		deconstruct.askingWhen(() -> askDeconstruct && Util.getMillis() < askUntil);
		deconstruct.onPress(() -> ask(0));
		deconstruct.enabledWhen(() -> !readonly());
		deconstruct.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.deconstruct"), Component.translatable("kultivator.sectbuild.ui.deconstruct_tip")));
		add(deconstruct);
		JadeCoin total = new JadeCoin(panelX + half + 3, by - BUTTON_H - 2, half, 11, () -> Component.translatable("kultivator.sectbuild.ui.deconstruct_total"),
				() -> false);
		total.askingWhen(() -> askTotal && Util.getMillis() < askUntil);
		total.onPress(() -> ask(1));
		total.enabledWhen(() -> !readonly());
		total.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.deconstruct_total"), Component.translatable("kultivator.sectbuild.ui.deconstruct_total_tip")));
		add(total);
		JadeCoin abandon = new JadeCoin(panelX, by, half, 11, () -> Component.translatable("kultivator.sectbuild.ui.abandon"), () -> false);
		abandon.askingWhen(() -> askAbandon && Util.getMillis() < askUntil);
		abandon.onPress(() -> ask(2));
		abandon.enabledWhen(() -> !readonly());
		abandon.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.abandon"), Component.translatable("kultivator.sectbuild.ui.abandon_tip")));
		add(abandon);
		TalismanButton change = talisman(panelX + half + 3, by, half, "kultivator.sectbuild.ui.change", () -> act("change"));
		change.enabledWhen(() -> !readonly() && b().getBooleanOr("can_change", false));
		change.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.change"), Component.translatable("kultivator.sectbuild.ui.change_tip")));
	}

	/** A dangerous coin: the first click asks (its rim pulses red), a second click within 4 s acts. */
	private void ask(int which) {
		long now = Util.getMillis();
		boolean asking = now < askUntil && (which == 0 && askDeconstruct || which == 1 && askTotal || which == 2 && askAbandon);
		if (asking) {
			askDeconstruct = false;
			askTotal = false;
			askAbandon = false;
			askUntil = 0L;
			act(which == 2 ? "abandon" : "deconstruct:" + (which == 1 ? "1" : "0"));
			return;
		}
		askDeconstruct = which == 0;
		askTotal = which == 1;
		askAbandon = which == 2;
		askUntil = now + 4000L;
		UiSound.ask();
	}

	private static String[] catKeys() {
		String[] keys = new String[Bahan.COUNT];
		for (Bahan cat : Bahan.values()) {
			keys[cat.ordinal()] = cat.nameKey();
		}
		return keys;
	}

	private int parties() {
		return b().getListOrEmpty("parties").size();
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		lastGraphics = g;
		hits.clear();
		refreshParts();
		CompoundTag build = b();
		// The diagram on the left.
		int scrub = template() > 0 ? Math.min(9, tier() + 1) : BuildClient.scrubTier();
		int hover = SiteDiagram.draw(g, x, y + 1, diagram, parts, bounds, template() > 0 ? 9 : scrub, template() > 0 ? Math.min(9, tier() + 1) : 0,
				mouseX, mouseY, true);
		Brush.frame(g, x - 1, y, x + diagram + 1, y + diagram + 2, Palette.argb(0xC0, Palette.GOLD));
		if (hover >= 0 && hover < parts.size()) {
			screen.showTooltip(partTip(hover));
		}
		if (diagram + 14 < h) {
			Component caption = template() > 0 ? SectTemplates.name(template()) : Component.translatable("kultivator.sectbuild.ui.no_template_short");
			Brush.fitCentered(g, font, caption, x + diagram / 2.0F, y + diagram + 4.0F, diagram, Palette.SEAL_DARK, 0.7F, 0.45F, false);
		}
		if (!build.getBooleanOr("on", true) || !build.getBooleanOr("engine", false)) {
			heading(g, title(), panelX, y, panelW, 0.75F);
			Brush.wrap(g, font, Component.translatable(build.getBooleanOr("on", true) ? "kultivator.spine6b.unavailable" : "kultivator.sectbuild.disabled"),
					panelX, y + 14, panelW, Palette.INK_SOFT, 0.7F, false);
			if (template() <= 0) {
				drawFounding(g, mouseX, mouseY, y + 34);
			}
			return;
		}
		if (template() <= 0) {
			drawFounding(g, mouseX, mouseY, y);
			return;
		}
		drawSeals(g, mouseX, mouseY);
		switch (panel) {
			case 0 -> drawOverview(g, mouseX, mouseY);
			case 1 -> drawMaterials(g, mouseX, mouseY);
			case 2 -> drawMissions(g, mouseX, mouseY);
			case 3 -> drawBuilders(g, mouseX, mouseY);
			case 4 -> drawSalvage(g, mouseX, mouseY);
			case 5 -> drawTemplate(g, mouseX, mouseY);
			default -> drawDonors(g, mouseX, mouseY);
		}
	}

	private List<Component> partTip(int index) {
		SiteDiagram.Part part = parts.get(index);
		List<Component> tip = new ArrayList<>();
		tip.add(part.name() != null ? part.name() : Component.translatable("kultivator.sectbuild.ui.part." + part.shape()));
		tip.add(Component.translatable("kultivator.sectbuild.ui.part_tier", part.tier(), SectTemplates.tierName(part.tier())));
		if (part.state() >= 0) {
			tip.add(Component.translatable("kultivator.sectbuild.ui.state." + part.state(), part.progress()));
		}
		ListTag comps = b().getListOrEmpty("comps");
		if (index < comps.size() && comps.get(index) instanceof CompoundTag comp && comp.contains("bill")) {
			Bill bill = Bill.readFrom(comp.getCompoundOrEmpty("bill"));
			if (!bill.isEmpty()) {
				tip.add(Component.translatable("kultivator.sectbuild.ui.part_bill", dev.kultivator.sect.build.flow.Fmt.joined(bill, 5)));
			}
		}
		return tip;
	}

	/** The seal column on the right edge. */
	private void drawSeals(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int count = lan() ? PANELS.length : PANELS.length - 1;
		int sx = x + w - SEAL_W;
		int sealH = Math.max(14, Math.min(24, (h - 2) / count));
		for (int i = 0; i < count; i++) {
			int sy = y + i * sealH;
			boolean active = panel == i;
			boolean over = mouseX >= sx && mouseX < sx + SEAL_W && mouseY >= sy && mouseY < sy + sealH - 1;
			int base = active ? Palette.SEAL : over ? Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, 0.3F) : Palette.argb(0xC0, Palette.SEAL_DARK);
			int out = active ? -2 : 0;
			g.fill(sx + out, sy, sx + SEAL_W, sy + sealH - 1, base);
			Brush.frame(g, sx + out + 1, sy + 1, sx + SEAL_W - 1, sy + sealH - 2, Palette.argb(0xB0, Palette.TALISMAN));
			Glyphs.tab(g, PANEL_ICONS[i], sx + out / 2.0F + SEAL_W / 2.0F, sy + (sealH - 1) / 2.0F, Math.min(8.0F, sealH - 6.0F), Palette.TALISMAN);
			int index = i;
			hits.add(new Hit(sx - 2, sy, sx + SEAL_W, sy + sealH - 1, () -> {
				if (panel != index) {
					panel = index;
					UiSound.tab();
					layout(x, y, w, h);
				}
			}, List.of(Component.translatable("kultivator.sectbuild.panel." + PANELS[i]),
					Component.translatable("kultivator.sectbuild.panel." + PANELS[i] + ".tip"))));
		}
	}

	// ---- no template yet

	private void drawFounding(GuiGraphicsExtractor g, int mouseX, int mouseY, int top) {
		heading(g, Component.translatable("kultivator.sectbuild.ui.no_template"), panelX, top, panelW + SEAL_W, 0.8F);
		float ty = top + 14.0F;
		ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.founding_text"), panelX, ty, panelW + SEAL_W, Palette.INK, TEXT, false) + 4.0F;
		Bill ledger = bill("ledger");
		if (!ledger.isEmpty()) {
			ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.ledger_now", dev.kultivator.sect.build.flow.Fmt.joined(ledger, 6)),
					panelX, ty, panelW + SEAL_W, Palette.INK_SOFT, TEXT, false) + 2.0F;
		}
		Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.stele_hint"), panelX, ty, panelW + SEAL_W, Palette.INK_SOFT, TEXT * 0.95F, false);
	}

	// ---- Ikhtisar

	private void drawOverview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int tier = tier();
		int building = Math.min(9, tier + 1);
		Component title = tier >= 9 ? Component.translatable("kultivator.sectbuild.ui.tier_complete", SectTemplates.TIERS, SectTemplates.TIERS)
				: Component.translatable("kultivator.sectbuild.ui.tier_title", tier, SectTemplates.TIERS, SectTemplates.tierName(building));
		heading(g, title, panelX, y, panelW, 0.75F);
		// The mountain path of nine terraces.
		float lx = panelX + 2.0F;
		float ly = y + 13.0F;
		float lw = panelW - 4.0F;
		float lh = 24.0F;
		drawLadder(g, lx, ly, lw, lh, tier, mouseX, mouseY);
		float ty = ly + lh + 4.0F;
		CompoundTag site = site();
		// The open building.
		ListTag comps = b().getListOrEmpty("comps");
		String openId = b().getStringOr("open", "");
		CompoundTag open = null;
		for (Tag each : comps) {
			if (each instanceof CompoundTag comp && comp.getStringOr("id", "").equals(openId) && !openId.isEmpty()) {
				open = comp;
			}
		}
		if (open != null) {
			Component name = open.read("name", ComponentSerialization.CODEC).orElse(Component.literal(openId));
			int pg = open.getIntOr("pg", 0);
			bar(g, panelX, ty, panelW, 7.0F, pg / 100.0F, Palette.GOLD, Component.translatable("kultivator.sectbuild.holo.open", name, pg));
			ty += 10.0F;
		}
		// Rate and status.
		double rate = site.getDoubleOr("rate", 0.0D);
		int active = b().getCompoundOrEmpty("builders").getIntOr("active", 0);
		boolean banked = site.getLongOr("bank", 0L) > 0L;
		Component rateLine = Component.translatable(banked ? "kultivator.sectbuild.ui.rate_bank" : "kultivator.sectbuild.ui.rate",
				String.format(Locale.ROOT, "%.0f", rate), active);
		Brush.fit(g, font, rateLine, panelX, ty, panelW, Palette.INK_SOFT, TEXT, 0.4F, false);
		ty += 7.0F;
		Component status = b().read("status_text", ComponentSerialization.CODEC).orElse(Component.empty());
		int code = site.getIntOr("status", 0);
		int color = code == SiteView.STATUS_RUNNING || code == SiteView.STATUS_COMPLETE ? Palette.JADE_DARK
				: code == SiteView.STATUS_WAITING_FUNDS ? Palette.GOLD_DARK : code == SiteView.STATUS_BLOCKED ? Palette.SEAL : Palette.INK_SOFT;
		ty += Brush.wrap(g, font, status, panelX, ty, panelW, color, TEXT, false) + 2.0F;
		// What the next building still needs.
		CompoundTag next = b().getCompoundOrEmpty("next");
		if (!next.isEmpty()) {
			Bill shortfall = Bill.readFrom(next.getCompoundOrEmpty("short"));
			Component name = next.read("name", ComponentSerialization.CODEC).orElse(Component.empty());
			if (shortfall.isEmpty()) {
				ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.next_ready", name), panelX, ty, panelW, Palette.JADE_DARK, TEXT,
						false) + 1.0F;
			} else {
				ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.next_needs", name), panelX, ty, panelW, Palette.SEAL_DARK, TEXT,
						false) + 1.0F;
				ty = shortfallBars(g, shortfall, ty, y + h - 2 * BUTTON_H - 6, mouseX, mouseY);
			}
		}
	}

	/** Nine terraces climbing a mountain path; done in jade, the one being built glowing gold, later ones in faint ink. */
	private void drawLadder(GuiGraphicsExtractor g, float lx, float ly, float lw, float lh, int tier, int mouseX, int mouseY) {
		float step = lw / 9.0F;
		float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 260.0D);
		// The mountain behind the path.
		for (int i = 0; i < 18; i++) {
			float t = i / 18.0F;
			float mh = lh * (0.25F + 0.7F * t) * (0.9F + 0.1F * (float) Math.sin(i * 1.7F));
			Brush.rect(g, lx + lw * t, ly + lh - mh, lx + lw * (t + 1.0F / 18.0F), ly + lh, Palette.argb(0x28, Palette.MOUNTAIN));
		}
		for (int i = 0; i < 9; i++) {
			float tx0 = lx + i * step;
			float top = ly + lh - (i + 1) * (lh - 3.0F) / 9.0F;
			boolean done = i < tier;
			boolean current = i == tier;
			int color = done ? Palette.JADE : current ? Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse) : Palette.argb(0x70, Palette.INK_FAINT);
			Brush.rect(g, tx0 + 0.5F, top, tx0 + step - 0.5F, top + 2.2F, color);
			Brush.rect(g, tx0 + 0.5F, top + 2.2F, tx0 + step - 0.5F, ly + lh, Palette.argb(done ? 0x50 : 0x20, Palette.PAPER_DEEP));
			if (i > 0) {
				float prevTop = ly + lh - i * (lh - 3.0F) / 9.0F;
				Brush.line(g, tx0, prevTop + 1.0F, tx0 + 1.5F, top + 1.0F, 0.6F, Palette.argb(0x90, Palette.INK_SOFT));
			}
			Brush.centered(g, font, Component.literal(Integer.toString(i + 1)), tx0 + step / 2.0F, top - 6.0F, done ? Palette.JADE_DARK : current ? Palette.GOLD_DARK
					: Palette.INK_FAINT, 0.5F, false);
			if (mouseX >= tx0 && mouseX < tx0 + step && mouseY >= top - 6 && mouseY < ly + lh) {
				screen.showTooltip(List.of(Component.translatable("kultivator.sectbuild.ui.part_tier", i + 1, SectTemplates.tierName(i + 1)),
						Component.translatable("kultivator.sectbuild.tier_feature." + (i + 1))));
			}
		}
		// A small hall at the summit.
		float sx = lx + lw - 3.0F;
		float sy = ly + 1.0F;
		Brush.rect(g, sx - 3.0F, sy, sx + 3.0F, sy + 1.2F, Palette.INK);
		Brush.rect(g, sx - 2.0F, sy + 1.2F, sx + 2.0F, sy + 3.0F, Palette.SEAL);
	}

	/** One bamboo bar with a label in it. */
	private void bar(GuiGraphicsExtractor g, float bx, float by, float bw, float bh, float fill, int color, Component label) {
		g.fill(Math.round(bx), Math.round(by), Math.round(bx + bw), Math.round(by + bh), Palette.argb(0x60, Palette.BAMBOO_ALT));
		Brush.rect(g, bx, by, bx + bw * Math.max(0.0F, Math.min(1.0F, fill)), by + bh, Palette.argb(0xC0, color));
		Brush.frame(g, Math.round(bx), Math.round(by), Math.round(bx + bw), Math.round(by + bh), Palette.argb(0xA0, Palette.BAMBOO_KNOT));
		Brush.fit(g, font, label, bx + 2.0F, by + (bh - 8.0F * 0.55F) / 2.0F, bw - 4.0F, Palette.INK, 0.55F, 0.4F, false);
	}

	/** Bars of what is still missing, by category and exact item; returns the y below them. */
	private float shortfallBars(GuiGraphicsExtractor g, Bill shortfall, float ty, float limit, int mouseX, int mouseY) {
		for (Bahan cat : Bahan.values()) {
			long centi = shortfall.centi(cat);
			if (centi <= 0L || ty + 7.0F > limit) {
				continue;
			}
			Brush.rect(g, panelX, ty + 1.0F, panelX + 3.0F, ty + 6.0F, Palette.opaque(cat.rgb()));
			Brush.fit(g, font, dev.kultivator.sect.build.flow.Fmt.line(cat, centi), panelX + 5.0F, ty, panelW - 5.0F, Palette.INK, TEXT, 0.4F, false);
			ty += 7.0F;
		}
		for (Map.Entry<Item, Integer> entry : shortfall.exact().entrySet()) {
			if (ty + 7.0F > limit) {
				break;
			}
			Brush.rect(g, panelX, ty + 1.0F, panelX + 3.0F, ty + 6.0F, Palette.SEAL);
			Brush.fit(g, font, dev.kultivator.sect.build.flow.Fmt.line(entry.getKey(), entry.getValue()), panelX + 5.0F, ty, panelW - 5.0F, Palette.INK, TEXT,
					0.4F, false);
			ty += 7.0F;
		}
		return ty;
	}

	// ---- Bahan

	private void drawMaterials(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		heading(g, Component.translatable("kultivator.sectbuild.panel.materials"), panelX, y, panelW, 0.75F);
		Bill ledger = bill("ledger");
		Bill need1 = bill("need1");
		Bill need2 = bill("need2");
		beginList(1, panelTop, y + h - BUTTON_H - 3);
		float ty = listTop - scroll(1);
		g.enableScissor(panelX - 1, listTop, panelX + panelW + 1, listBottom);
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.bills_head", Math.min(9, tier() + 1)), panelX, ty, panelW, Palette.INK_SOFT, 0.55F, 0.4F,
				false);
		ty += 7.0F;
		for (Bahan cat : Bahan.values()) {
			long have = ledger.centi(cat);
			long now = need1.centi(cat);
			long later = need2.centi(cat);
			if (now <= 0L && later <= 0L && have <= 0L) {
				continue;
			}
			float fill = now <= 0L ? 1.0F : Math.min(1.0F, have / (float) now);
			int color = Palette.opaque(cat.rgb());
			Component label = now > 0L ? Component.translatable("kultivator.sectbuild.ui.bill_line", cat.displayName(), dev.kultivator.sect.build.flow.Fmt.units(have),
					dev.kultivator.sect.build.flow.Fmt.units(now)) : Component.translatable("kultivator.sectbuild.ui.bill_have", cat.displayName(),
					dev.kultivator.sect.build.flow.Fmt.units(have));
			bar(g, panelX, ty, panelW, 6.5F, fill, color, label);
			if (later > 0L) {
				float nextFill = Math.min(1.0F, Math.max(0L, have - now) / (float) later);
				Brush.rect(g, panelX, ty + 6.5F, panelX + panelW * nextFill, ty + 7.5F, Palette.argb(0xA0, Palette.JADE));
			}
			if (inList(mouseX, mouseY) && mouseY >= ty && mouseY < ty + 8.0F && mouseX >= panelX && mouseX < panelX + panelW) {
				screen.showTooltip(List.of(cat.displayName(), Component.translatable("kultivator.sectbuild.ui.bill_tip", dev.kultivator.sect.build.flow.Fmt.units(have),
						dev.kultivator.sect.build.flow.Fmt.units(now), dev.kultivator.sect.build.flow.Fmt.units(later)),
						Component.translatable("kultivator.sectbuild.bahan_tip." + cat.id())));
			}
			ty += 9.0F;
		}
		// Exact items.
		Map<Item, Integer> exactNeed = new LinkedHashMap<>(need1.exact());
		need2.exact().forEach((item, count) -> exactNeed.merge(item, count, Integer::sum));
		ledger.exact().forEach((item, count) -> exactNeed.putIfAbsent(item, 0));
		if (!exactNeed.isEmpty()) {
			Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.exact_head"), panelX, ty, panelW, Palette.INK_SOFT, 0.55F, 0.4F, false);
			ty += 7.0F;
			int perRow = Math.max(1, (int) (panelW / 30.0F));
			int index = 0;
			for (Map.Entry<Item, Integer> entry : exactNeed.entrySet()) {
				float ix = panelX + (index % perRow) * 30.0F;
				float iy = ty + (index / perRow) * 14.0F;
				icon(g, new ItemStack(entry.getKey()), ix, iy, 0.75F);
				int have = ledger.exact(entry.getKey());
				Brush.text(g, font, Component.literal(have + "/" + entry.getValue()), ix + 13.0F, iy + 3.0F, have >= entry.getValue() ? Palette.JADE_DARK
						: Palette.SEAL_DARK, 0.5F, false);
				if (inList(mouseX, mouseY) && mouseX >= ix && mouseX < ix + 28.0F && mouseY >= iy && mouseY < iy + 13.0F) {
					screen.showTooltip(List.of(new ItemStack(entry.getKey()).getHoverName(), Component.translatable("kultivator.sectbuild.ui.exact_tip", have,
							entry.getValue())));
				}
				index++;
			}
			ty += ((index + perRow - 1) / perRow) * 14.0F + 2.0F;
		}
		// The deposit grid: accepted items in the bag.
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.bag_head"), panelX, ty, panelW, Palette.INK_SOFT, 0.55F, 0.4F, false);
		ty += 7.0F;
		ty = depositGrid(g, ty, mouseX, mouseY);
		g.disableScissor();
		endList(ty + scroll(1));
	}

	/** The bag's accepted items, one cell per item; click deposits a stack, shift-click all of that item. */
	private float depositGrid(GuiGraphicsExtractor g, float ty, int mouseX, int mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return ty;
		}
		Inventory inventory = minecraft.player.getInventory();
		Map<Item, int[]> cells = new LinkedHashMap<>();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || Deposits.valueOf(stack.getItem()) == null) {
				continue;
			}
			int first = slot;
			int[] cell = cells.computeIfAbsent(stack.getItem(), item -> new int[]{first, 0});
			cell[1] += stack.getCount();
		}
		if (cells.isEmpty()) {
			ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.bag_empty"), panelX, ty, panelW, Palette.INK_FAINT, 0.55F, false);
			return ty + 2.0F;
		}
		int perRow = Math.max(1, (int) (panelW / 15.0F));
		int index = 0;
		// Listed by category (Batu ... Emas & Giok), the exact items last.
		List<Map.Entry<Item, int[]>> sorted = new ArrayList<>(cells.entrySet());
		sorted.sort(java.util.Comparator.comparingInt(entry -> {
			Deposits.Value value = Deposits.valueOf(entry.getKey());
			return value == null ? 99 : value.exact() ? 50 : value.cat().ordinal();
		}));
		for (Map.Entry<Item, int[]> entry : sorted) {
			Item item = entry.getKey();
			int slot = entry.getValue()[0];
			int count = entry.getValue()[1];
			float ix = panelX + (index % perRow) * 15.0F;
			float iy = ty + (index / perRow) * 15.0F;
			Deposits.Value value = Deposits.valueOf(item);
			int edge = value == null ? Palette.INK_FAINT : value.exact() ? Palette.SEAL : Palette.opaque(value.cat().rgb());
			boolean over = inList(mouseX, mouseY) && mouseX >= ix && mouseX < ix + 14.0F && mouseY >= iy && mouseY < iy + 14.0F;
			g.fill(Math.round(ix), Math.round(iy), Math.round(ix + 14.0F), Math.round(iy + 14.0F), Palette.argb(over ? 0x90 : 0x50, Palette.PAPER_TOP));
			Brush.frame(g, Math.round(ix), Math.round(iy), Math.round(ix + 14.0F), Math.round(iy + 14.0F), Palette.argb(0xC0, edge));
			icon(g, new ItemStack(item), ix + 1.0F, iy + 1.0F, 0.75F);
			Brush.text(g, font, Component.literal(count > 999 ? "999+" : Integer.toString(count)), ix + 1.0F, iy + 9.0F, Palette.INK, 0.45F, false);
			if (inList(mouseX, mouseY)) {
				List<Component> tip = new ArrayList<>();
				tip.add(new ItemStack(item).getHoverName());
				if (value != null) {
					tip.add(value.exact() ? Component.translatable("kultivator.sectbuild.ui.value_exact")
							: Component.translatable("kultivator.sectbuild.ui.value", dev.kultivator.sect.build.flow.Fmt.units(value.centi()), value.cat().displayName()));
				}
				tip.add(Component.translatable("kultivator.sectbuild.ui.deposit_click"));
				hits.add(new Hit(ix, iy, ix + 14.0F, iy + 14.0F, () -> {
					if (Minecraft.getInstance().hasShiftDown()) {
						act("deposit_item:" + BuiltInRegistries.ITEM.getKey(item));
					} else {
						act("deposit:" + slot);
					}
				}, tip));
			}
			index++;
		}
		return ty + ((index + perRow - 1) / perRow) * 15.0F + 2.0F;
	}

	// ---- Misi

	private void drawMissions(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		heading(g, Component.translatable("kultivator.sectbuild.panel.missions"), panelX, y, panelW, 0.75F);
		if (missionView == 1) {
			drawGather(g, mouseX, mouseY);
			return;
		}
		CompoundTag missions = b().getCompoundOrEmpty("missions");
		beginList(2, panelTop + 13, y + h);
		float ty = listTop - scroll(2);
		g.enableScissor(panelX - 1, listTop, panelX + panelW + 1, listBottom);
		if (missions.getBooleanOr("complete", false)) {
			ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.sect_complete"), panelX, ty, panelW, Palette.JADE_DARK, TEXT, false) + 3.0F;
		}
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.tier_missions", missions.getIntOr("tier", 0)), panelX, ty, panelW, Palette.SEAL_DARK,
				0.6F, 0.4F, false);
		ty += 8.0F;
		for (Tag each : missions.getListOrEmpty("lines")) {
			if (!(each instanceof CompoundTag line)) {
				continue;
			}
			Bahan cat = Bahan.byId(line.getStringOr("cat", ""));
			Item item = item(line.getStringOr("item", ""));
			long amount = line.getLongOr("amount", 0L);
			long have = line.getLongOr("have", 0L);
			boolean prep = line.getBooleanOr("prep", false);
			boolean covered = line.getBooleanOr("covered", false);
			boolean paid = line.getBooleanOr("paid", false);
			Component what = cat != null ? dev.kultivator.sect.build.flow.Fmt.line(cat, amount) : item != null
					? dev.kultivator.sect.build.flow.Fmt.line(item, (int) amount) : Component.literal("?");
			Component label = Component.translatable(prep ? "kultivator.sectbuild.ui.line_prep" : "kultivator.sectbuild.ui.line", what,
					line.getIntOr("pk", 0));
			int color = cat != null ? Palette.opaque(cat.rgb()) : Palette.SEAL;
			bar(g, panelX, ty, panelW, 6.5F, amount <= 0L ? 1.0F : have / (float) amount, paid ? Palette.JADE_LIGHT : covered ? Palette.GOLD : color, label);
			if (paid) {
				Brush.text(g, font, Component.literal("✓"), panelX + panelW - 6.0F, ty, Palette.JADE_DARK, 0.6F, false);
			}
			ty += 8.0F;
		}
		ty += 2.0F;
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.offers", missions.getIntOr("pk_today", 0), missions.getIntOr("pk_cap", 150)), panelX,
				ty, panelW, Palette.SEAL_DARK, 0.6F, 0.4F, false);
		ty += 8.0F;
		ListTag offers = missions.getListOrEmpty("offers");
		if (offers.isEmpty()) {
			ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.no_offers"), panelX, ty, panelW, Palette.INK_FAINT, 0.55F, false) + 2.0F;
		}
		for (int i = 0; i < offers.size(); i++) {
			if (!(offers.get(i) instanceof CompoundTag offer)) {
				continue;
			}
			Bahan cat = Bahan.byId(offer.getStringOr("cat", ""));
			if (cat == null) {
				continue;
			}
			boolean deliver = "deliver".equals(offer.getStringOr("kind", ""));
			Component label;
			if (deliver) {
				long amount = offer.getLongOr("amount", 0L);
				long progress = offer.getLongOr("progress", 0L) / Bahan.CENTI;
				boolean accepted = offer.getBooleanOr("accepted", false);
				boolean done = offer.getBooleanOr("done", false);
				label = Component.translatable(done ? "kultivator.sectbuild.ui.offer_done" : accepted ? "kultivator.sectbuild.ui.offer_progress"
						: "kultivator.sectbuild.ui.offer_deliver", amount, cat.displayName(), progress, offer.getIntOr("pk", 0), offer.getLongOr("bonus", 0L));
				float rowW = accepted || done || readonly() ? panelW : panelW - 34.0F;
				bar(g, panelX, ty, rowW, 8.0F, amount <= 0L ? 0.0F : progress / (float) amount, done ? Palette.JADE_LIGHT : Palette.opaque(cat.rgb()), label);
				if (!accepted && !done && !readonly()) {
					float bx = panelX + panelW - 32.0F;
					boolean over = inList(mouseX, mouseY) && mouseX >= bx && mouseX < bx + 32.0F && mouseY >= ty && mouseY < ty + 8.0F;
					g.fillGradient(Math.round(bx), Math.round(ty), Math.round(bx + 32.0F), Math.round(ty + 8.0F), over ? 0xFFF7DFA0 : Palette.TALISMAN,
							Palette.TALISMAN_LOW);
					Brush.frame(g, Math.round(bx), Math.round(ty), Math.round(bx + 32.0F), Math.round(ty + 8.0F), Palette.TALISMAN_EDGE);
					Brush.fitCentered(g, font, Component.translatable("kultivator.sectbuild.ui.accept"), bx + 16.0F, ty + 1.5F, 30.0F, Palette.INK, 0.55F, 0.4F,
							false);
					int index = i;
					if (inList(mouseX, mouseY)) {
						hits.add(new Hit(bx, ty, bx + 32.0F, ty + 8.0F, () -> act("accept:" + index), null));
					}
				}
			} else {
				label = Component.translatable("kultivator.sectbuild.ui.offer_bless", cat.displayName());
				bar(g, panelX, ty, panelW, 8.0F, 1.0F, Palette.argb(0xFF, 0xB8E6D0), label);
			}
			ty += 10.0F;
		}
		g.disableScissor();
		endList(ty + scroll(2));
	}

	/** The gathering view: parties away, then the party to send (category, length, up to three disciples). */
	private void drawGather(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		beginList(2, panelTop + 26, y + h - BUTTON_H - 16);
		float ty = listTop - scroll(2);
		g.enableScissor(panelX - 1, listTop, panelX + panelW + 1, listBottom);
		ListTag parties = b().getListOrEmpty("parties");
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.parties", parties.size(), b().getIntOr("party_max", 1)), panelX, ty, panelW,
				Palette.SEAL_DARK, 0.6F, 0.4F, false);
		ty += 8.0F;
		for (Tag each : parties) {
			if (!(each instanceof CompoundTag party)) {
				continue;
			}
			Bahan cat = Bahan.byId(party.getStringOr("cat", "batu"));
			List<String> names = new ArrayList<>();
			for (Tag nameTag : party.getListOrEmpty("names")) {
				if (nameTag instanceof CompoundTag compound) {
					compound.read("name", ComponentSerialization.CODEC).ifPresent(name -> names.add(name.getString()));
				}
			}
			long left = dev.kultivator.client.hall.HallClient.ticksLeft(party.getLongOr("left", 0L));
			Component line = Component.translatable("kultivator.sectbuild.ui.party", cat == null ? Component.literal("?") : cat.displayName(),
					String.join(", ", names), dev.kultivator.sect.build.flow.Fmt.clock(left), Math.round(party.getDoubleOr("chance", 0.0D) * 100.0D),
					dev.kultivator.sect.build.flow.Fmt.units(party.getLongOr("yield", 0L)));
			ty += Brush.wrap(g, font, line, panelX, ty, panelW, Palette.INK, 0.55F, false) + 2.0F;
		}
		ty += 2.0F;
		Bahan cat = Bahan.values()[gatherCat];
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.party_pick", cat.displayName()), panelX, ty, panelW, Palette.SEAL_DARK, 0.6F, 0.4F, false);
		ty += 8.0F;
		ListTag candidates = b().getCompoundOrEmpty("builders").getListOrEmpty("cands");
		for (Tag each : candidates) {
			if (!(each instanceof CompoundTag cand)) {
				continue;
			}
			int eid = cand.getIntOr("eid", -1);
			String key = Integer.toString(eid);
			boolean chosen = PARTY.contains(key);
			int rank = cand.getIntOr("rank", 0);
			boolean rankOk = rank >= minRank(cat) && (cat != Bahan.LOGAM || cand.getIntOr("realm", 0) >= 1);
			boolean free = eid >= 0 && cand.getBooleanOr("free", false) && rankOk;
			Component name = cand.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			chip(g, ty, name, Component.translatable("kultivator.sectbuild.ui.cand_info", rankName(rank),
					String.format(Locale.ROOT, "%.1f", cand.getDoubleOr("power", 0.0D))), chosen, free, mouseX, mouseY, () -> {
				if (chosen) {
					PARTY.remove(key);
				} else if (PARTY.size() < 3) {
					PARTY.add(key);
				}
			});
			ty += 9.0F;
		}
		if (candidates.isEmpty()) {
			ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.no_candidates"), panelX, ty, panelW, Palette.INK_FAINT, 0.55F, false);
		}
		g.disableScissor();
		endList(ty + scroll(2));
	}

	private static int minRank(Bahan cat) {
		return switch (cat) {
			case GENTENG, KAPUR, PERNIS, LOGAM -> 1;
			case PERMATA -> 2;
			default -> 0;
		};
	}

	/** A disciple chip: a jade bead, the name and a small line; click toggles. */
	private void chip(GuiGraphicsExtractor g, float ty, Component name, Component info, boolean chosen, boolean enabled, int mouseX, int mouseY, Runnable action) {
		boolean over = inList(mouseX, mouseY) && mouseX >= panelX && mouseX < panelX + panelW && mouseY >= ty && mouseY < ty + 8.0F;
		g.fill(panelX, Math.round(ty), panelX + panelW, Math.round(ty + 8.0F), Palette.argb(chosen ? 0x90 : over ? 0x60 : 0x30, chosen ? Palette.JADE_PALE : Palette.PAPER_DEEP));
		Brush.disc(g, panelX + 4.0F, ty + 4.0F, 2.5F, chosen ? Palette.JADE_LIGHT : enabled ? Palette.JADE_DULL : Palette.LOCKED);
		Brush.fit(g, font, name, panelX + 9.0F, ty + 1.0F, panelW * 0.55F, enabled ? Palette.INK : Palette.INK_FAINT, 0.6F, 0.4F, false);
		Brush.fit(g, font, info, panelX + 9.0F + panelW * 0.56F, ty + 1.5F, panelW * 0.42F - 9.0F, Palette.INK_SOFT, 0.5F, 0.35F, false);
		if (inList(mouseX, mouseY) && (enabled || chosen) && !readonly()) {
			hits.add(new Hit(panelX, ty, panelX + panelW, ty + 8.0F, action, null));
		}
	}

	// ---- Pekerja

	private void drawBuilders(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag builders = b().getCompoundOrEmpty("builders");
		heading(g, Component.translatable("kultivator.sectbuild.panel.builders"), panelX, y, panelW, 0.75F);
		beginList(3, panelTop, y + h);
		float ty = listTop - scroll(3);
		g.enableScissor(panelX - 1, listTop, panelX + panelW + 1, listBottom);
		ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.builders_line", builders.getIntOr("active", 0),
				String.format(Locale.ROOT, "%.2f", builders.getDoubleOr("factor", 1.0D))), panelX, ty, panelW, Palette.INK_SOFT, 0.55F, false) + 2.0F;
		ListTag list = builders.getListOrEmpty("list");
		for (Tag each : list) {
			if (!(each instanceof CompoundTag builder)) {
				continue;
			}
			String soul = builder.getStringOr("soul", "");
			Component name = builder.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			boolean working = builder.getBooleanOr("working", false);
			Component info = Component.translatable(working ? "kultivator.sectbuild.ui.builder_working" : builder.getBooleanOr("loaded", false)
					? "kultivator.sectbuild.ui.builder_idle" : "kultivator.sectbuild.ui.builder_away", builder.getLongOr("ticks", 0L) / 1200L);
			chip(g, ty, name, info, true, true, mouseX, mouseY, () -> act("builder_off:" + soul));
			ty += 9.0F;
		}
		ty += 2.0F;
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.builders_add", list.size(), 4), panelX, ty, panelW, Palette.SEAL_DARK, 0.6F, 0.4F, false);
		ty += 8.0F;
		for (Tag each : builders.getListOrEmpty("cands")) {
			if (!(each instanceof CompoundTag cand)) {
				continue;
			}
			int eid = cand.getIntOr("eid", -1);
			boolean free = eid >= 0 && cand.getBooleanOr("free", false) && list.size() < 4;
			Component name = cand.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			String duty = cand.getStringOr("duty", "");
			Component info = duty.isEmpty() ? rankName(cand.getIntOr("rank", 0))
					: Component.translatable("kultivator.sectbuild.ui.on_duty");
			chip(g, ty, name, info, false, free, mouseX, mouseY, () -> act("builder:" + eid + ":1"));
			ty += 9.0F;
		}
		ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.builders_hint"), panelX, ty + 2.0F, panelW, Palette.INK_FAINT, 0.5F, false);
		g.disableScissor();
		endList(ty + scroll(3));
	}

	// ---- Galian

	private void drawSalvage(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		heading(g, Component.translatable("kultivator.sectbuild.panel.salvage"), panelX, y, panelW, 0.75F);
		boolean auth = b().getCompoundOrEmpty("flags").getBooleanOr("can_auth", false);
		beginList(4, panelTop, y + h - BUTTON_H - (auth ? 16 : 3));
		float ty = listTop - scroll(4);
		g.enableScissor(panelX - 1, listTop, panelX + panelW + 1, listBottom);
		// The surplus of Batu, Tanah and Kayu.
		CompoundTag surplus = b().getCompoundOrEmpty("surplus");
		int third = (int) ((panelW - 4) / 3.0F);
		for (int i = 0; i < Deposits.SURPLUS.length; i++) {
			Bahan cat = Deposits.SURPLUS[i];
			long units = surplus.getLongOr(cat.id(), 0L);
			float bx = panelX + i * (third + 2);
			boolean over = inList(mouseX, mouseY) && mouseX >= bx && mouseX < bx + third && mouseY >= ty && mouseY < ty + 10.0F;
			int top = units > 0L ? over ? 0xFFF7DFA0 : Palette.TALISMAN : Palette.grey(Palette.TALISMAN, 0.7F);
			g.fillGradient(Math.round(bx), Math.round(ty), Math.round(bx + third), Math.round(ty + 10.0F), top, Palette.TALISMAN_LOW);
			Brush.frame(g, Math.round(bx), Math.round(ty), Math.round(bx + third), Math.round(ty + 10.0F), Palette.TALISMAN_EDGE);
			Brush.fitCentered(g, font, Component.translatable("kultivator.sectbuild.ui.surplus", cat.displayName(), units), bx + third / 2.0F, ty + 2.0F, third - 2.0F,
					units > 0L ? Palette.INK : Palette.INK_FAINT, 0.55F, 0.35F, false);
			if (units > 0L && !readonly() && inList(mouseX, mouseY)) {
				hits.add(new Hit(bx, ty, bx + third, ty + 10.0F, () -> act("surplus:" + cat.id()),
						List.of(Component.translatable("kultivator.sectbuild.ui.surplus_tip", cat.displayName()))));
			}
		}
		ty += 13.0F;
		// The Peti Galian.
		CompoundTag salvage = b().getCompoundOrEmpty("salvage");
		ListTag counts = salvage.getListOrEmpty("list");
		if (counts.isEmpty() && salvage.getListOrEmpty("rich").isEmpty()) {
			ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.salvage_empty"), panelX, ty, panelW, Palette.INK_FAINT, 0.55F, false);
		}
		int perRow = Math.max(1, (int) (panelW / 30.0F));
		int index = 0;
		for (Tag each : counts) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			Item item = item(entry.getStringOr("item", ""));
			if (item == null) {
				continue;
			}
			long count = entry.getLongOr("n", 0L);
			float ix = panelX + (index % perRow) * 30.0F;
			float iy = ty + (index / perRow) * 14.0F;
			boolean over = inList(mouseX, mouseY) && mouseX >= ix && mouseX < ix + 28.0F && mouseY >= iy && mouseY < iy + 13.0F;
			g.fill(Math.round(ix), Math.round(iy), Math.round(ix + 28.0F), Math.round(iy + 13.0F), Palette.argb(over ? 0x90 : 0x40, Palette.PAPER_TOP));
			icon(g, new ItemStack(item), ix + 1.0F, iy + 0.5F, 0.75F);
			Brush.text(g, font, Component.literal(count > 9999 ? "9999+" : Long.toString(count)), ix + 13.0F, iy + 3.5F, Palette.INK, 0.5F, false);
			if (inList(mouseX, mouseY) && !readonly()) {
				hits.add(new Hit(ix, iy, ix + 28.0F, iy + 13.0F, () -> act("take:" + BuiltInRegistries.ITEM.getKey(item)),
						List.of(new ItemStack(item).getHoverName(), Component.translatable("kultivator.sectbuild.ui.take_tip", count))));
			}
			index++;
		}
		ty += ((index + perRow - 1) / perRow) * 14.0F + 2.0F;
		if (salvage.getIntOr("more", 0) > 0) {
			Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.salvage_more", salvage.getIntOr("more", 0)), panelX, ty, panelW, Palette.INK_FAINT,
					0.5F, 0.35F, false);
			ty += 7.0F;
		}
		for (Tag each : salvage.getListOrEmpty("rich")) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			int richIndex = entry.getIntOr("index", 0);
			Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			chip(g, ty, name, Component.literal("x" + entry.getIntOr("n", 1)), false, true, mouseX, mouseY, () -> act("take_rich:" + richIndex));
			ty += 9.0F;
		}
		g.disableScissor();
		endList(ty + scroll(4));
	}

	// ---- Templat

	private void drawTemplate(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		heading(g, Component.translatable("kultivator.sectbuild.panel.template"), panelX, y, panelW, 0.75F);
		beginList(5, panelTop, y + h - 3 * (BUTTON_H + 2) - 2);
		float ty = listTop - scroll(5);
		g.enableScissor(panelX - 1, listTop, panelX + panelW + 1, listBottom);
		CompoundTag site = site();
		Glaze glaze = Glaze.byId(site.getStringOr("glaze", "tinta"));
		Component gate = Component.translatable("kultivator.sectbuild.gate." + SectTemplates.gate(site.getIntOr("rot", 0)).getSerializedName());
		ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.template_line", SectTemplates.name(template()),
				glaze == null ? Component.literal("?") : glaze.displayName(), gate), panelX, ty, panelW, Palette.INK, 0.6F, false) + 1.0F;
		CompoundTag guarantee = b().getCompoundOrEmpty("guarantee");
		ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.coupling", b().getIntOr("frozen_cap", -1), guarantee.getIntOr("tier", 0)),
				panelX, ty, panelW, Palette.INK_SOFT, 0.55F, false) + 3.0F;
		// The obstacles: blocked components with their buttons.
		boolean anyBlocked = false;
		for (Tag each : b().getListOrEmpty("comps")) {
			if (!(each instanceof CompoundTag comp) || comp.getIntOr("st", 0) != ComponentView.STATE_BLOCKED) {
				continue;
			}
			if (!anyBlocked) {
				Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.obstacles"), panelX, ty, panelW, Palette.SEAL, 0.6F, 0.4F, false);
				ty += 8.0F;
				anyBlocked = true;
			}
			String id = comp.getStringOr("id", "");
			Component name = comp.read("name", ComponentSerialization.CODEC).orElse(Component.literal(id));
			Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.blocked_line", name, comp.getIntOr("bl", 0)), panelX, ty, panelW, Palette.INK,
					0.55F, 0.4F, false);
			ty += 7.0F;
			ty = miniButtons(g, ty, mouseX, mouseY, new String[]{"kultivator.sectbuild.ui.recheck", "kultivator.sectbuild.ui.skip", "kultivator.sectbuild.ui.finish_blocked"},
					new String[]{"recheck", "skip:" + id, "finish_blocked:" + id});
		}
		// Prioritaskan: the queued components of the tier being built.
		int building = Math.min(9, tier() + 1);
		boolean anyQueued = false;
		for (Tag each : b().getListOrEmpty("comps")) {
			if (!(each instanceof CompoundTag comp) || comp.getIntOr("st", 0) != ComponentView.STATE_QUEUED || comp.getIntOr("tier", 0) != building) {
				continue;
			}
			if (!anyQueued) {
				Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.queue"), panelX, ty, panelW, Palette.SEAL_DARK, 0.6F, 0.4F, false);
				ty += 8.0F;
				anyQueued = true;
			}
			String id = comp.getStringOr("id", "");
			Component name = comp.read("name", ComponentSerialization.CODEC).orElse(Component.literal(id));
			chip(g, ty, name, Component.translatable("kultivator.sectbuild.ui.prioritize"), false, true, mouseX, mouseY, () -> act("prio:" + id));
			ty += 9.0F;
		}
		// The history.
		ListTag history = b().getListOrEmpty("history");
		if (!history.isEmpty()) {
			ty += 2.0F;
			Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.history"), panelX, ty, panelW, Palette.SEAL_DARK, 0.6F, 0.4F, false);
			ty += 8.0F;
			for (int i = history.size() - 1; i >= 0; i--) {
				if (history.get(i) instanceof CompoundTag entry) {
					long to = entry.getLongOr("to", -1L);
					Component line = Component.translatable(to < 0L ? "kultivator.sectbuild.ui.history_now" : "kultivator.sectbuild.ui.history_line",
							SectTemplates.name(entry.getIntOr("t", 1)), entry.getLongOr("from", 0L), to);
					Brush.fit(g, font, line, panelX, ty, panelW, Palette.INK_SOFT, 0.55F, 0.4F, false);
					ty += 7.0F;
				}
			}
		}
		g.disableScissor();
		endList(ty + scroll(5));
	}

	/** A row of small talisman strips; returns the y below them. */
	private float miniButtons(GuiGraphicsExtractor g, float ty, int mouseX, int mouseY, String[] keys, String[] actions) {
		float each = (panelW - 2.0F * (keys.length - 1)) / keys.length;
		for (int i = 0; i < keys.length; i++) {
			float bx = panelX + i * (each + 2.0F);
			boolean over = inList(mouseX, mouseY) && mouseX >= bx && mouseX < bx + each && mouseY >= ty && mouseY < ty + 9.0F;
			g.fillGradient(Math.round(bx), Math.round(ty), Math.round(bx + each), Math.round(ty + 9.0F), over ? 0xFFF7DFA0 : Palette.TALISMAN, Palette.TALISMAN_LOW);
			Brush.frame(g, Math.round(bx), Math.round(ty), Math.round(bx + each), Math.round(ty + 9.0F), Palette.TALISMAN_EDGE);
			Brush.fitCentered(g, font, Component.translatable(keys[i]), bx + each / 2.0F, ty + 2.0F, each - 2.0F, Palette.INK, 0.5F, 0.35F, false);
			String action = actions[i];
			if (inList(mouseX, mouseY) && !readonly()) {
				hits.add(new Hit(bx, ty, bx + each, ty + 9.0F, () -> act(action), null));
			}
		}
		return ty + 11.0F;
	}

	// ---- Penyumbang

	private void drawDonors(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		heading(g, Component.translatable("kultivator.sectbuild.panel.donors"), panelX, y, panelW, 0.75F);
		beginList(6, panelTop, y + h);
		float ty = listTop - scroll(6);
		g.enableScissor(panelX - 1, listTop, panelX + panelW + 1, listBottom);
		ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.donors_text"), panelX, ty, panelW, Palette.INK_SOFT, 0.55F, false) + 3.0F;
		ListTag donors = b().getListOrEmpty("donors");
		int rank = 1;
		for (Tag each : donors) {
			if (each instanceof CompoundTag donor) {
				Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.donor_line", rank++, donor.getStringOr("name", "?"), donor.getLongOr("units", 0L)),
						panelX, ty, panelW, Palette.INK, 0.6F, 0.4F, false);
				ty += 8.0F;
			}
		}
		if (donors.isEmpty()) {
			ty += Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.no_donors"), panelX, ty, panelW, Palette.INK_FAINT, 0.55F, false);
		}
		g.disableScissor();
		endList(ty + scroll(6));
	}

	// ================================================================== list scrolling and clicks

	private void beginList(int id, int top, int bottom) {
		listTop = top;
		listBottom = Math.max(top + 10, bottom);
	}

	private void endList(float contentBottom) {
		contentHeight = contentBottom - listTop;
		float max = Math.max(0.0F, contentHeight - (listBottom - listTop));
		SCROLL.put(panel, Math.max(0.0F, Math.min(max, scroll(panel))));
		if (max > 0.0F) {
			float bead = listTop + 2.0F + (listBottom - listTop - 4.0F) * (scroll(panel) / max);
			g().fill(panelX + panelW, listTop, panelX + panelW + 1, listBottom, Palette.argb(0x80, Palette.CORD));
			Brush.disc(g(), panelX + panelW + 0.5F, bead, 1.8F, Palette.JADE);
		}
	}

	/** The frame's graphics, kept for the list scrollbar drawn in {@link #endList}. */
	private @Nullable GuiGraphicsExtractor lastGraphics;

	private GuiGraphicsExtractor g() {
		return lastGraphics;
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		for (Hit hit : hits) {
			if (hit.tip() != null && mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
				screen.showTooltip(hit.tip());
			}
		}
	}

	private float scroll(int id) {
		return SCROLL.getOrDefault(id, 0.0F);
	}

	private boolean inList(int mouseX, int mouseY) {
		return mouseY >= listTop && mouseY < listBottom;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
				UiSound.press();
				hit.action().run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX >= panelX && mouseX < panelX + panelW + 2 && mouseY >= listTop && mouseY < listBottom) {
			SCROLL.put(panel, Math.max(0.0F, scroll(panel) - (float) amount * 12.0F));
			return true;
		}
		return false;
	}

	// ================================================================== small helpers

	private void icon(GuiGraphicsExtractor g, ItemStack stack, float ix, float iy, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(ix, iy);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	/** Sect rank names of module A (Tercatat ... Tetua Agung). */
	private static final String[] RANK_IDS = {"tercatat", "luar", "dalam", "inti", "tetua", "tetua_agung"};

	private static Component rankName(int rank) {
		return Component.translatable("kultivator.hall.rank." + RANK_IDS[Math.max(0, Math.min(RANK_IDS.length - 1, rank))]);
	}

	private static @Nullable Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		return item == null || item == Items.AIR ? null : item;
	}

}
