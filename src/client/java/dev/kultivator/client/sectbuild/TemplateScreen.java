package dev.kultivator.client.sectbuild;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.PlanStats;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.Glaze;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The template catalog of Sekte › Bangun › Templat (DESIGN 21.12 step 2, 21.21, 43.12.1 "H"): once a screen of its own, now
 * the painter of that sub inside the Gulungan Utama (the class stays, its base is gone). On the left a 3x3 grid of jade
 * plaques (a numeral seal and an ink sketch of each of the nine templates, the name in the tooltip) with the "Templat Awan"
 * coin under it (Sekte Langit, DESIGN 37.3.4: the sky family A1-A9 = templates 11-19). On the right, in one scroll: the
 * chosen template's name, a site diagram with the tier talisman (the wheel over the diagram works too) and the skyline
 * strip, the numbers (footprint, envelope, components, bill, time, perks, realm hint), the glaze coins (on a sky preview
 * they are the plaza height, down 4 / up 4), and the whole bill or, once the footprint preview of the chosen template
 * arrived, its warnings as bamboo slips. Under the scroll the talismans "Lihat Tapak", "Putar", "Batal", "Lihat di dunia"
 * (the Gulungan steps aside so the hologram can be walked around; Enter or Esc brings it back) and "Konfirmasi", which asks
 * the heavy {@link SegelKonfirmasi} (tick the consent, hold 3 s) of DESIGN 43.7 in place of the old checkbox and seal. With
 * a template already standing it is the "Naik Templat" catalog (only larger templates). Text 1.0 (43.0.5).
 */
final class TemplateScreen {

	/** DESIGN 21.1 Table B perks: tower rooms, Qi seat tier, gate altars. */
	private static final int[] TOWER_ROOMS = {3, 3, 4, 4, 5, 6, 6, 7, 7};
	private static final int[] QI_SEAT = {1, 1, 1, 2, 2, 2, 3, 3, 3};
	private static final int[] ALTARS = {0, 0, 0, 0, 0, 2, 4, 4, 4};
	private static final int[] TERRITORY = {24, 32, 40, 46, 58, 68, 80, 96, 108};
	/** DESIGN 21.1 Table A: envelope above / below the plaza. */
	private static final int[] ABOVE = {14, 24, 36, 40, 48, 56, 62, 70, 100};
	private static final int[] BELOW = {14, 14, 18, 18, 22, 26, 26, 30, 30};
	/** DESIGN 21.11 target totals (units, Tinta) before the dry run answers, and 21.15 minutes at the base rate. */
	private static final int[] TARGET_UNITS = {3240, 6350, 10170, 15850, 21090, 29430, 36440, 51820, 65230};
	private static final int[] TARGET_MINUTES = {25, 34, 43, 53, 64, 81, 96, 116, 142};
	private static final int LINE = 10;
	private static final String K = "kultivator.sectbuild.ui.";
	private static final String M = "kultivator.gui43.m07.";

	// Kept across openings of the Gulungan (the player steps out to look at the hologram and comes back).
	private static int selected = 1;
	/** The sky family (templates 11-19) is shown. */
	private static boolean sky;
	private static int glaze = -1;
	private static float scroll;

	private final BuildPage page;
	private float content;
	private int scrollX0;
	private int scrollY0;
	private int scrollX1;
	private int scrollY1;
	private int diagramX0;
	private int diagramY0;
	private int diagramSize;

	TemplateScreen(BuildPage page) {
		this.page = page;
	}

	/** The server or a focus tag asked for {@code template}; 0 keeps the current choice. */
	void select(int template) {
		if (SectTemplates.validAny(template) && template != selected) {
			selected = template;
			sky = SectTemplates.isSky(template);
			scroll = 0.0F;
		}
	}

	/** The catalog came on screen: ask for it and take the stored glaze the first time. */
	void opened() {
		BuildClient.send(0, "catalog");
		if (glaze < 0) {
			Glaze chosen = Glaze.byId(BuildClient.catalog().getStringOr("glaze", "tinta"));
			glaze = chosen == null ? 0 : chosen.ordinal();
		}
		if (!SectTemplates.validAny(selected)) {
			selected = 1;
		}
	}

	private boolean upgrade() {
		return page.template() > 0;
	}

	// ================================================================== state

	private static CompoundTag catalog() {
		return BuildClient.catalog();
	}

	private static PlanStats stats(int template) {
		ListTag list = catalog().getListOrEmpty("stats");
		for (Tag each : list) {
			if (each instanceof CompoundTag tag && tag.getIntOr("t", 0) == template) {
				return PlanStats.fromTag(tag);
			}
		}
		return PlanStats.empty(template);
	}

	/** The template of grid cell {@code n} (1-9) on the current family. */
	private static int templateOf(int n) {
		return sky ? SectTemplates.SKY_FIRST + n - 1 : n;
	}

	/** Why a sky template cannot be chosen (the gate of DESIGN 37.3.3 as far as the client knows it), or null. */
	private @Nullable Component lockedSky(int template) {
		CompoundTag catalog = catalog();
		if (!catalog.getBooleanOr("sky_on", false)) {
			return Component.translatable("kultivator.sekte_langit.bangun.mati");
		}
		int n = template - SectTemplates.SKY_FIRST + 1;
		int max = catalog.getIntOr("max_t", 9);
		if (n > max) {
			return Component.translatable("kultivator.sectbuild.template.too_large", max);
		}
		dev.kultivator.data.Realm realm = dev.kultivator.sect.build.flow.LangitRancang.realmFor(template);
		if (catalog.getBooleanOr("sky_gate", true) && catalog.getIntOr("realm", 0) < realm.ordinal()) {
			return Component.translatable("kultivator.sekte_langit.bangun.ranah", SectTemplates.name(template), realm.displayName());
		}
		int current = catalog.getIntOr("t", 0);
		if (upgrade()) {
			if (!SectTemplates.isSky(current)) {
				return Component.translatable("kultivator.blueprint.error.upgrade_family");
			}
			return template <= current ? Component.translatable("kultivator.sectbuild.upgrade.smaller", SectTemplates.name(current)) : null;
		}
		int skyTemplate = catalog.getIntOr("sky_t", 0);
		if (skyTemplate > 0 || SectTemplates.isSky(current)) {
			return Component.translatable("kultivator.sekte_langit.bangun.sudah", SectTemplates.name(skyTemplate > 0 ? skyTemplate : current));
		}
		if (current <= 0 && n > catalog.getIntOr("sky_max_b", 3)) {
			return Component.translatable("kultivator.sekte_langit.bangun.jalan_b", catalog.getIntOr("sky_max_b", 3));
		}
		if (current > 0 && n > current + 1) {
			return Component.translatable("kultivator.sekte_langit.bangun.loncat", SectTemplates.name(SectTemplates.SKY_FIRST + current));
		}
		return null;
	}

	/** Why a template cannot be chosen (null when it can). */
	private @Nullable Component locked(int template) {
		if (SectTemplates.isSky(template)) {
			return lockedSky(template);
		}
		CompoundTag catalog = catalog();
		if (upgrade() && SectTemplates.isSky(catalog.getIntOr("t", 0))) {
			return Component.translatable("kultivator.blueprint.error.upgrade_family");
		}
		int max = catalog.getIntOr("max_t", 9);
		if (template > max) {
			return Component.translatable("kultivator.sectbuild.template.too_large", max);
		}
		if (catalog.getBooleanOr("gate", false) && catalog.getIntOr("realm", 0) < template - 1) {
			return Component.translatable("kultivator.sectbuild.template.realm_gate", SectTemplates.name(template),
					Component.translatable("kultivator.sectbuild.realm_hint." + template));
		}
		int current = catalog.getIntOr("t", 0);
		if (upgrade() && template <= current) {
			return Component.translatable("kultivator.sectbuild.upgrade.smaller", SectTemplates.name(Math.max(1, current)));
		}
		if (!upgrade() && current > 0) {
			return Component.translatable("kultivator.sectbuild.template.already", SectTemplates.name(current));
		}
		return null;
	}

	/** The preview of the selected template, or null. */
	static @Nullable CompoundTag preview() {
		if (!BuildClient.previewActive()) {
			return null;
		}
		CompoundTag tag = BuildClient.preview();
		return tag.getIntOr("t", 0) == selected ? tag : null;
	}

	private static boolean confirmable() {
		CompoundTag preview = preview();
		return preview != null && preview.getBooleanOr("confirmable", false) && preview.getLongOr("ticket", 0L) != 0L;
	}

	/** True while the shown preview is a sky design. */
	private static boolean skyPreview() {
		CompoundTag preview = preview();
		return preview != null && preview.getBooleanOr("sky", false);
	}

	/** A glaze coin's label, or the sky height control it stands for. */
	private static Component coinLabel(Glaze each) {
		if (!skyPreview()) {
			return each.displayName();
		}
		return switch (each.ordinal()) {
			case 0 -> Component.literal("▼ 4");
			case 2 -> Component.literal("▲ 4");
			default -> Component.translatable(K + "sky_height", preview() == null ? 0 : preview().getIntOr("yP", 0));
		};
	}

	/** The realm hint of a template: the ground's own line, or the sky family's realm (DESIGN 37.3.3). */
	private static Component realmHint(int template) {
		if (SectTemplates.isSky(template)) {
			return dev.kultivator.sect.build.flow.LangitRancang.realmFor(template).displayName();
		}
		return Component.translatable("kultivator.sectbuild.realm_hint." + template);
	}

	// ================================================================== drawing

	/** Draws the catalog into the box (the whole content box of the page). */
	void draw(Lukis l, int x, int y, int w, int h) {
		int bh = page.buttonH();
		int gridW = Math.max(84, Math.min(156, Math.round(w * 0.36F)));
		int gap = w < 360 ? 2 : 4;
		int plaqueW = (gridW - 2 * gap) / 3;
		int coinY0 = y + h - bh;
		int plaqueH = Math.max(18, Math.min(Math.round(plaqueW * 0.84F), (coinY0 - 4 - y - 2 * gap) / 3));
		drawGrid(l, x, y, plaqueW, plaqueH, gap);
		int under = y + 3 * (plaqueH + gap) + 2;
		BuildPage.koin(l, "tpl:awan", x, Math.max(under, coinY0 - 2), 3 * plaqueW + 2 * gap, bh, Component.translatable(K + "tab_awan"), sky,
				catalog().getBooleanOr("sky_on", false), Component.translatable("kultivator.sekte_langit.bangun.mati"), () -> {
					sky = !sky;
					selected = templateOf(SectTemplates.rank(selected));
					scroll = 0.0F;
				}, List.of(Component.translatable(K + "tab_awan"), Component.translatable(K + "tab_awan_tip")));
		int textTop = under + 2;
		if (coinY0 - 4 - textTop >= 18) {
			l.bungkus(Component.translatable(K + "promise"), x, textTop, 3 * plaqueW + 2 * gap, l.gaya.teksSamar, coinY0 - 4 - textTop);
		}

		int rx = x + 3 * plaqueW + 2 * gap + 8;
		int rw = x + w - rx;
		CompoundTag preview = preview();
		int rows = preview != null ? 2 : 1;
		int buttonsTop = y + h - rows * (bh + 2) + 2;
		drawButtons(l, rx, buttonsTop, rw, bh, preview);
		scrollX0 = rx;
		scrollY0 = y;
		scrollX1 = rx + rw;
		scrollY1 = buttonsTop - 3;
		float view = scrollY1 - scrollY0;
		scroll = Math.max(0.0F, Math.min(scroll, Math.max(0.0F, content - view)));
		l.klip(rx, y, rx + rw, scrollY1);
		float top = y - scroll;
		float bottom = drawDetail(l, rx, top, rw - 4, preview);
		l.lepasKlip();
		content = bottom - top;
		page.bead(l, rx + rw, y, scrollY1, scroll, Math.max(0.0F, content - view));
	}

	/** The 3x3 grid of jade plaques. */
	private void drawGrid(Lukis l, int gridX, int gridY, int plaqueW, int plaqueH, int gap) {
		for (int n = 1; n <= SectTemplates.COUNT; n++) {
			int template = templateOf(n);
			int col = (n - 1) % 3;
			int row = (n - 1) / 3;
			int px = gridX + col * (plaqueW + gap);
			int py = gridY + row * (plaqueH + gap);
			boolean over = l.di(px, py, px + plaqueW, py + plaqueH);
			boolean chosen = template == selected;
			Component lock = locked(template);
			int top = chosen ? Palette.JADE_LIGHT : over ? Palette.lighten(Palette.JADE_PALE, 0.1F) : Palette.JADE_PALE;
			int bottom = chosen ? Palette.JADE : Palette.mix(Palette.JADE_PALE, Palette.JADE_DULL, 0.5F);
			if (lock != null) {
				top = Palette.grey(top, 0.75F);
				bottom = Palette.grey(bottom, 0.75F);
			}
			l.g.fill(px + 1, py + 2, px + plaqueW + 1, py + plaqueH + 1, 0x40000000);
			Brush.chamferGradient(l.g, px, py, px + plaqueW, py + plaqueH, 2, top, bottom);
			Brush.frame(l.g, px + 1, py + 1, px + plaqueW - 1, py + plaqueH - 1, chosen ? Palette.GOLD : Palette.argb(0x90, Palette.JADE_DARK));
			boolean named = plaqueH >= 30;
			float size = Math.min(plaqueW - 6.0F, plaqueH - (named ? 13.0F : 4.0F));
			SiteDiagram.draw(l.g, px + (plaqueW - size) / 2.0F, py + 2.0F, size, SiteDiagram.sketch(n), SiteDiagram.Bounds.of(n), 9, 0, -1, -1, false);
			// The numeral seal; the name when the plaque is tall enough (else in the tooltip).
			Component numeral = Component.literal(sky ? "A" + n : Integer.toString(n));
			int sealW = Math.max(9, l.font.width(numeral) + 3);
			l.g.fill(px + 2, py + 2, px + 2 + sealW, py + 12, lock == null ? Palette.SEAL : Palette.grey(Palette.SEAL, 0.7F));
			Brush.text(l.g, l.font, numeral, px + 4.0F, py + 3.0F, Palette.TALISMAN, 1.0F, false);
			if (named) {
				l.tengah(SectTemplates.name(template), px + plaqueW / 2.0F, py + plaqueH - 10.0F, plaqueW - 4.0F, lock == null ? Palette.INK : Palette.INK_FAINT);
			}
			if (lock != null) {
				Glyphs.lock(l.g, px + plaqueW - 6.0F, py + 6.0F, 6.0F, Palette.INK_SOFT);
			}
			List<Component> tip = new ArrayList<>();
			tip.add(SectTemplates.name(template));
			tip.add(Component.translatable("kultivator.sectbuild.archetype." + template));
			tip.add(Component.translatable(K + "realm_hint", realmHint(template)));
			if (lock != null) {
				tip.add(lock);
			}
			String key = "tpl:plakat:" + n;
			l.zona(key, px, py, px + plaqueW, py + plaqueH, () -> {
				if (template != selected) {
					select(template);
					UiSound.tab();
				}
			}, true, null, true);
			l.fokusCincin(key, px, py, px + plaqueW, py + plaqueH);
			if (over) {
				l.tip(tip);
			}
		}
	}

	/** The chosen template in one scroll; returns the bottom of what was laid out. */
	private float drawDetail(Lukis l, int rx, float top, int rw, @Nullable CompoundTag preview) {
		float y = top;
		l.teks(Brush.bold(SectTemplates.name(selected)), rx, y, rw, Palette.SEAL_DARK);
		y += LINE + 1;
		y += l.bungkus(Component.translatable("kultivator.sectbuild.archetype." + selected), rx, y, rw, l.gaya.teksSamar) + 3;

		List<SiteDiagram.Part> parts;
		SiteDiagram.Bounds bounds;
		int[] stele = preview == null ? null : preview.getIntArray("stele").orElse(null);
		int[] fp = preview == null ? null : SiteHologram.box(preview, "fp");
		if (preview != null && stele != null && stele.length == 3 && fp != null && !preview.getListOrEmpty("comps").isEmpty()) {
			int rot = preview.getIntOr("rot", 0);
			parts = SiteDiagram.fromComponents(preview.getListOrEmpty("comps"), stele, rot);
			bounds = SiteDiagram.bounds(fp, stele, rot);
		} else {
			parts = SiteDiagram.sketch(SectTemplates.rank(selected));
			bounds = SiteDiagram.Bounds.of(SectTemplates.rank(selected));
		}
		int scrub = BuildClient.scrubTier();
		diagramSize = Math.max(48, Math.min(120, rw));
		diagramX0 = rx;
		diagramY0 = Math.round(y);
		int hover = SiteDiagram.draw(l.g, rx, y, diagramSize, parts, bounds, scrub, 0, l.mx, l.my, true);
		Brush.frame(l.g, rx - 1, Math.round(y) - 1, rx + diagramSize + 1, Math.round(y) + diagramSize + 1, Palette.argb(0xC0, Palette.GOLD));
		if (hover >= 0) {
			SiteDiagram.Part part = parts.get(hover);
			List<Component> tip = new ArrayList<>();
			tip.add(part.name() != null ? part.name() : Component.translatable(K + "part." + part.shape()));
			tip.add(Component.translatable(K + "part_tier", part.tier(), SectTemplates.tierName(part.tier())));
			l.tip(tip);
		}
		// The nine jade beads (the scrubber's state) under the diagram.
		float beadY = y + diagramSize + 4.0F;
		float beadStep = diagramSize / 9.0F;
		for (int tier = 1; tier <= 9; tier++) {
			float bx = rx + beadStep * (tier - 0.5F);
			boolean lit = tier <= scrub;
			Brush.disc(l.g, bx, beadY + 2.5F, 2.6F, Palette.darken(lit ? Palette.JADE_LIGHT : Palette.JADE_DULL, 0.3F));
			Brush.disc(l.g, bx, beadY + 2.5F, 2.0F, lit ? Palette.JADE_LIGHT : Palette.JADE_DULL);
			if (tier == scrub) {
				Brush.ring(l.g, bx, beadY + 2.5F, 2.8F, 3.4F, Palette.GOLD);
			}
		}
		y = beadY + 8.0F;
		Component tierLabel = Component.translatable(K + "part_tier", scrub, SectTemplates.tierName(scrub));
		TalismanButton.gambar(l, "tpl:tingkat", rx, Math.round(y), Math.min(rw, Math.max(diagramSize, TalismanButton.lebar(l.font, tierLabel, 60, rw))),
				page.buttonH(), tierLabel, true, null, () -> BuildClient.setScrubTier(scrub >= 9 ? 1 : scrub + 1));
		if (l.di(rx, Math.round(y), rx + rw, Math.round(y) + page.buttonH())) {
			l.tip(List.of(tierLabel, Component.translatable(M + "tingkat.tip")));
		}
		y += page.buttonH() + 3;
		SiteDiagram.skyline(l.g, rx, y, Math.min(rw, diagramSize + 40), 10.0F, parts, bounds, scrub);
		y += 13;

		y = drawNumbers(l, rx, y, rw, preview);
		y = drawGlaze(l, rx, y + 2, rw);
		if (preview != null) {
			y = drawWarnings(l, rx, y + 2, rw, preview);
		} else {
			y = drawBill(l, rx, y + 2, rw);
		}
		return y + 2;
	}

	/** The numbers of the chosen template, one 1.0 line each (cut with "…", whole in the tooltip). */
	private float drawNumbers(Lukis l, int x, float y, int w, @Nullable CompoundTag preview) {
		int t = SectTemplates.rank(selected) - 1;
		PlanStats stats = stats(selected);
		boolean real = !stats.isEmpty();
		List<Component> lines = new ArrayList<>();
		int width = real ? stats.width() : 2 * SiteDiagram.X_HALF[t] + 1;
		int depth = real ? stats.depth() : SiteDiagram.Z_MAX[t] - SiteDiagram.Z_MIN[t] + 1;
		lines.add(Component.translatable(K + "footprint", width, depth));
		lines.add(Component.translatable(K + "envelope", real ? stats.above() : ABOVE[t], real ? stats.below() : BELOW[t]));
		if (real) {
			lines.add(Component.translatable(K + "components", stats.totalComponents()));
		}
		long units = real ? stats.total().totalCenti() / Bahan.CENTI : TARGET_UNITS[t];
		lines.add(Component.translatable(real ? K + "units" : K + "units_target", units));
		if (real) {
			int exact = stats.total().exact().values().stream().mapToInt(Integer::intValue).sum();
			lines.add(Component.translatable(K + "exact", exact));
		}
		int builders = catalog().getIntOr("builders", 0);
		double speed = Math.max(0.25D, catalog().getDoubleOr("speed", 1.0D));
		double minutes = (real && stats.minutes() > 0.0D ? stats.minutes() : TARGET_MINUTES[t]) / ((1.0D + 0.25D * builders) * speed);
		lines.add(Component.translatable(K + "time", Math.max(1L, Math.round(minutes)), builders));
		int[] skyTerritory = catalog().getIntArray("sky_terr").orElse(null);
		int territory = SectTemplates.isSky(selected) && skyTerritory != null && skyTerritory.length > t ? skyTerritory[t] : TERRITORY[t];
		lines.add(Component.translatable(K + "perks", TOWER_ROOMS[t], QI_SEAT[t], territory));
		if (ALTARS[t] > 0) {
			lines.add(Component.translatable(K + "altars", ALTARS[t]));
		}
		if (t + 1 >= 5) {
			lines.add(Component.translatable(K + "cap_bonus"));
		}
		if (t + 1 >= 6) {
			lines.add(Component.translatable(K + "candidate_bonus"));
		}
		lines.add(Component.translatable(K + "realm_hint", realmHint(selected)));
		if (preview != null) {
			lines.add(Component.translatable(K + "gate",
					Component.translatable("kultivator.sectbuild.gate." + SectTemplates.gate(preview.getIntOr("rot", 0)).getSerializedName())));
		}
		for (Component line : lines) {
			Brush.rect(l.g, x, y + 3.0F, x + 2.0F, y + 6.0F, Palette.SEAL);
			y += l.bungkus(line, x + 4, y, w - 4, Palette.INK) + 1;
		}
		return y;
	}

	/** The three glaze coins (Tinta, Giok, Langit), or the plaza height on a sky preview. */
	private float drawGlaze(Lukis l, int x, float y, int w) {
		int bh = page.buttonH();
		int coinW = (w - 4) / 3;
		boolean skyShown = skyPreview();
		for (Glaze each : Glaze.values()) {
			int index = each.ordinal();
			boolean enabled = !sky || skyShown && index != 1;
			BuildPage.koin(l, "tpl:glasir:" + index, x + index * (coinW + 2), Math.round(y), coinW, bh, coinLabel(each), !skyShown && glaze == index, enabled,
					Component.translatable(M + "glasir_langit"), () -> {
						if (skyPreview()) {
							// Down 4 / up 4: the plaza height of the sky design (the middle coin only shows it).
							if (index != 1) {
								BuildClient.send(0, "langit_tinggi:" + (index == 0 ? -1 : 1));
							}
							return;
						}
						glaze = index;
					}, skyShown ? List.of(Component.translatable(K + "sky_height_tip"))
							: List.of(each.displayName(), Component.translatable("kultivator.sectbuild.glaze_tip." + each.id())));
		}
		return y + bh + 3;
	}

	/** Without a preview: the whole bill as bamboo slips and the hint to look at the footprint. */
	private float drawBill(Lukis l, int x, float y, int w) {
		PlanStats stats = stats(selected);
		if (!stats.isEmpty()) {
			Bill total = stats.total();
			int columns = w >= 240 ? 2 : 1;
			float colW = w / (float) columns;
			int index = 0;
			for (Bahan cat : Bahan.values()) {
				long centi = total.centi(cat);
				if (centi <= 0L) {
					continue;
				}
				float sx = x + (index % columns) * colW;
				float sy = y + (index / columns) * LINE;
				Brush.rect(l.g, sx, sy + 2.0F, sx + 3.0F, sy + 7.0F, Palette.opaque(cat.rgb()));
				l.teks(Component.translatable("kultivator.sectbuild.amount", centi / Bahan.CENTI, cat.displayName()), sx + 5.0F, sy, colW - 6.0F, Palette.INK);
				index++;
			}
			y += ((index + columns - 1) / columns) * LINE + 2;
		}
		return y + l.bungkus(Component.translatable(M + "lihat_hint"), x, y, w, l.gaya.teksSamar);
	}

	/** With a preview: the snapshot line and the warnings as slips (worst first). */
	private float drawWarnings(Lukis l, int x, float y, int w, CompoundTag preview) {
		List<CompoundTag> warnings = warnings(preview);
		int snap = preview.getIntOr("snap", 0);
		l.teks(Component.translatable("kultivator.sectbuild.hud.snapshot", snap), x, y, w, snap >= 100 ? Palette.JADE_DARK : Palette.WARN);
		y += LINE + 1;
		if (warnings.isEmpty()) {
			l.teks(Component.translatable(K + "no_warnings"), x, y, w, Palette.JADE_DARK);
			return y + LINE;
		}
		for (CompoundTag warning : warnings) {
			int sev = warning.getIntOr("sev", 0);
			int slip = sev >= 2 ? 0xFFE8B4A8 : sev == 1 ? Palette.BAMBOO : Palette.BAMBOO_ALT;
			Component text = BuildClient.warning(warning);
			float height = Math.max(LINE, Brush.wrapHeight(l.font, text, w - 5, 1.0F) + 1);
			Brush.rect(l.g, x, y, x + w, y + height, Palette.argb(0xD0, slip));
			Brush.rect(l.g, x, y, x + 1.5F, y + height, sev >= 2 ? Palette.SEAL : sev == 1 ? Palette.WARN : Palette.BAMBOO_KNOT);
			l.bungkus(text, x + 4, y + 1, w - 5, Palette.INK);
			y += height + 1;
		}
		return y;
	}

	private static List<CompoundTag> warnings(CompoundTag preview) {
		List<CompoundTag> warnings = new ArrayList<>();
		for (Tag each : preview.getListOrEmpty("warn")) {
			if (each instanceof CompoundTag warning) {
				warnings.add(warning);
			}
		}
		warnings.sort((a, b) -> Integer.compare(b.getIntOr("sev", 0), a.getIntOr("sev", 0)));
		return warnings;
	}

	/** Look / turn / cancel, and with a preview: see it in the world / confirm. */
	private void drawButtons(Lukis l, int x, int y, int w, int bh, @Nullable CompoundTag preview) {
		int third = (w - 4) / 3;
		Component lock = locked(selected);
		boolean ro = page.readonly();
		Component why = ro ? Component.translatable(M + "baca_saja") : lock;
		TalismanButton.gambar(l, "tpl:lihat", x, y, third, bh, Component.translatable(K + "look"), lock == null && !ro, why, () -> {
			if (sky && !upgrade()) {
				BuildClient.send(0, "langit:" + (selected - SectTemplates.SKY_FIRST + 1));
			} else {
				BuildClient.send(0, "preview:" + selected + ":-1:" + Glaze.byOrdinal(Math.max(0, glaze)).id() + (upgrade() ? ":upgrade" : ""));
			}
		}, lock == null && !ro && preview == null, false);
		if (l.di(x, y, x + third, y + bh)) {
			l.tip(List.of(Component.translatable(K + "look"), Component.translatable(M + "lihat_tapak.tip")));
		}
		Component noPreview = Component.translatable(M + "tanpa_pratinjau");
		TalismanButton.gambar(l, "tpl:putar", x + third + 2, y, third, bh, Component.translatable(K + "rotate"), preview != null && !upgrade(),
				preview == null ? noPreview : Component.translatable(M + "putar_naik"), () -> BuildClient.send(0, "rotate"));
		TalismanButton.gambar(l, "tpl:batal", x + 2 * (third + 2), y, w - 2 * (third + 2), bh, Component.translatable(K + "cancel"), preview != null,
				noPreview, () -> BuildClient.send(0, "cancel"));
		if (preview == null) {
			return;
		}
		int ry = y + bh + 2;
		int half = (w - 2) / 2;
		TalismanButton.gambar(l, "tpl:dunia", x, ry, half, bh, Component.translatable(M + "lihat_dunia"), true, null, BuildClient::lihatDiDunia);
		if (l.di(x, ry, x + half, ry + bh)) {
			l.tip(List.of(Component.translatable(M + "lihat_dunia"), Component.translatable(M + "lihat_dunia.tip")));
		}
		boolean ready = confirmable() && !ro;
		Component notReady = ro ? Component.translatable(M + "baca_saja") : Component.translatable(K + "not_ready");
		TalismanButton.gambar(l, "tpl:konfirmasi", x + half + 2, ry, w - half - 2, bh, Component.translatable(K + "confirm"), ready, notReady,
				this::askConfirm, ready, false);
	}

	/** The heavy seal (tick the consent, hold 3 s) of DESIGN 43.7, then the confirm ticket. */
	private void askConfirm() {
		CompoundTag shown = preview();
		if (shown == null) {
			return;
		}
		long ticket = shown.getLongOr("ticket", 0L);
		int count = warnings(shown).size();
		Component text = Component.translatable(M + "konfirmasi_tapak", SectTemplates.name(selected), count, Component.translatable("kultivator.sectbuild.consent"));
		page.minta(SegelKonfirmasi.Tingkat.BERAT, text, () -> {
			CompoundTag now = preview();
			if (now == null || now.getLongOr("ticket", 0L) != ticket) {
				return;
			}
			BuildClient.send(0, "confirm:" + ticket);
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.8F, 0.9F));
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F));
			page.afterConfirm();
		});
	}

	// ================================================================== input

	/** The wheel: over the diagram the tier, elsewhere in the right column the scroll. True when used. */
	boolean scrolled(double mx, double my, double amount) {
		if (amount == 0.0D) {
			return false;
		}
		if (mx >= diagramX0 && mx < diagramX0 + diagramSize && my >= diagramY0 && my < diagramY0 + diagramSize && my >= scrollY0 && my < scrollY1) {
			BuildClient.setScrubTier(BuildClient.scrubTier() + (amount > 0.0D ? 1 : -1));
			return true;
		}
		if (mx >= scrollX0 && mx < scrollX1 && my >= scrollY0 && my < scrollY1) {
			scroll = Math.max(0.0F, scroll - (float) amount * 18.0F);
			return true;
		}
		return false;
	}

	/** PgUp / PgDn / Home / End: the right column's scroll (keyboard only, 43.0.7; clamped by the next frame). True when used. */
	boolean tombol(int key) {
		float page = Math.max(18.0F, scrollY1 - scrollY0 - 18.0F);
		switch (key) {
			case InputConstants.KEY_PAGEDOWN -> scroll += page;
			case InputConstants.KEY_PAGEUP -> scroll = Math.max(0.0F, scroll - page);
			case InputConstants.KEY_HOME -> scroll = 0.0F;
			case InputConstants.KEY_END -> scroll = Float.MAX_VALUE / 4.0F;
			default -> {
				return false;
			}
		}
		return true;
	}

	/** "1570": numbers stay plain (no locale surprises). */
	static String number(long value) {
		return String.format(Locale.ROOT, "%d", value);
	}
}
