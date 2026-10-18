package dev.kultivator.client.sectbuild;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.PlanStats;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.Glaze;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The template screen (DESIGN 21.12 step 2, 21.21): the jade scroll with a 3x3 grid of jade plaques on the left (a numeral
 * seal, the name and an ink sketch of each of the nine templates) and the chosen template on the right: a large site
 * diagram with a scrubber of nine jade beads (later tiers fade in) and a skyline strip along the axis, the numbers
 * (footprint, envelope, components, the whole bill and exact items, the estimated time at the current builder count, the
 * perks and the realm hint), the glaze (Tinta, Giok, Langit), and the talismans "Lihat Tapak" and "Putar". Once the
 * footprint preview of the chosen template arrives, its warnings show as bamboo slips with the consent checkbox and the
 * jade Konfirmasi seal, held for 3 s. In upgrade mode ("Naik Templat") only larger templates can be chosen. The world and
 * its hologram stay visible behind the scroll; Esc keeps the preview for walking around (the HUD and chat take over).
 */
public class TemplateScreen extends Screen {

	private static final int MAX_W = 372;
	private static final int MAX_H = 250;
	private static final long HOLD_MILLIS = 3000L;
	private static final long UNROLL_MILLIS = 250L;
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

	private final boolean upgrade;
	private final @Nullable Screen back;
	private final List<Widget> widgets = new ArrayList<>();
	private final Eased backdrop = new Eased(6.0F);
	private @Nullable Widget pressed;
	private int selected;
	private int glaze;
	private boolean consent;
	private boolean holding;
	private long holdStart;
	private boolean done;
	private long openedAt;
	private long lastFrame;
	private int warnScroll;
	private @Nullable List<Component> tooltip;

	private int x0;
	private int x1;
	private int y0;
	private int y1;
	private int gridX;
	private int gridY;
	private int plaqueW;
	private int plaqueH;
	private int gap;
	private int rightX;
	private int rightW;
	private int contentTop;
	private int contentBottom;
	private int diagramX;
	private int diagramY;
	private int diagramSize;
	private int beadY;
	private int skylineY;
	private int bottomY;
	private float sealX;
	private float sealY;
	private float sealR;
	private int boxX0;
	private int boxY0;
	private int boxX1;
	private int boxY1;

	public TemplateScreen(int select, boolean upgrade, @Nullable Screen back) {
		super(Component.translatable(upgrade ? "kultivator.sectbuild.ui.upgrade_title" : "kultivator.sectbuild.ui.template_title"));
		this.upgrade = upgrade;
		this.back = back instanceof TemplateScreen ? null : back;
		this.selected = SectTemplates.valid(select) ? select : 1;
		this.openedAt = Util.getMillis();
		this.lastFrame = openedAt;
		Glaze chosen = Glaze.byId(BuildClient.catalog().getStringOr("glaze", "tinta"));
		this.glaze = chosen == null ? 0 : chosen.ordinal();
		BuildClient.send(0, "catalog");
	}

	/** The server asked to select {@code template} while the screen is open. */
	public void select(int template) {
		if (SectTemplates.valid(template)) {
			selected = template;
			consent = false;
			warnScroll = 0;
		}
	}

	// ================================================================== state

	private CompoundTag catalog() {
		return BuildClient.catalog();
	}

	private PlanStats stats(int template) {
		ListTag list = catalog().getListOrEmpty("stats");
		for (Tag each : list) {
			if (each instanceof CompoundTag tag && tag.getIntOr("t", 0) == template) {
				return PlanStats.fromTag(tag);
			}
		}
		return PlanStats.empty(template);
	}

	/** Why a template cannot be chosen (null when it can). */
	private @Nullable Component locked(int template) {
		CompoundTag catalog = catalog();
		int max = catalog.getIntOr("max_t", 9);
		if (template > max) {
			return Component.translatable("kultivator.sectbuild.template.too_large", max);
		}
		if (catalog.getBooleanOr("gate", false) && catalog.getIntOr("realm", 0) < template - 1) {
			return Component.translatable("kultivator.sectbuild.template.realm_gate", SectTemplates.name(template),
					Component.translatable("kultivator.sectbuild.realm_hint." + template));
		}
		int current = catalog.getIntOr("t", 0);
		if (upgrade && template <= current) {
			return Component.translatable("kultivator.sectbuild.upgrade.smaller", SectTemplates.name(Math.max(1, current)));
		}
		if (!upgrade && current > 0) {
			return Component.translatable("kultivator.sectbuild.template.already", SectTemplates.name(current));
		}
		return null;
	}

	/** The preview of the selected template, or null. */
	private @Nullable CompoundTag preview() {
		if (!BuildClient.previewActive()) {
			return null;
		}
		CompoundTag tag = BuildClient.preview();
		return tag.getIntOr("t", 0) == selected ? tag : null;
	}

	private boolean confirmable() {
		CompoundTag preview = preview();
		return preview != null && preview.getBooleanOr("confirmable", false) && preview.getLongOr("ticket", 0L) != 0L;
	}

	// ================================================================== layout

	@Override
	protected void init() {
		int w = Math.min(MAX_W, width - 12);
		int h = Math.min(MAX_H, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2 + 5;
		y1 = y0 + h - 10;
		int cx0 = x0 + ScrollFrame.SIDE + 7;
		int cx1 = x1 - ScrollFrame.SIDE - 7;
		contentTop = y0 + ScrollFrame.END + 16;
		contentBottom = y1 - ScrollFrame.END - 5;
		int contentW = cx1 - cx0;
		int gridW = Math.min(150, Math.round(contentW * 0.42F));
		gap = contentW < 280 ? 2 : 4;
		plaqueW = (gridW - 2 * gap) / 3;
		plaqueH = Math.min(Math.round(plaqueW * 0.84F), (contentBottom - contentTop - 2 * gap - 14) / 3);
		gridX = cx0;
		gridY = contentTop;
		rightX = cx0 + gridW + 8;
		rightW = cx1 - rightX;
		int rightH = contentBottom - contentTop;
		diagramSize = Math.max(48, Math.min(Math.round(rightW * 0.5F), rightH - 20 - 10 - 12 - 14 - 15 - 40));
		diagramX = rightX;
		diagramY = contentTop + 20;
		beadY = diagramY + diagramSize + 3;
		skylineY = beadY + 8;
		int glazeY = skylineY + 12;
		int talismanY = glazeY + 13;
		bottomY = talismanY + 15;

		widgets.clear();
		pressed = null;
		int coinW = rightW / 3;
		for (Glaze each : Glaze.values()) {
			int index = each.ordinal();
			JadeCoin coin = new JadeCoin(rightX + index * coinW, glazeY, coinW - 2, 11, each::displayName, () -> glaze == index);
			coin.onPress(() -> {
				glaze = index;
				consent = false;
			});
			coin.tooltip(() -> List.of(each.displayName(), Component.translatable("kultivator.sectbuild.glaze_tip." + each.id())));
			widgets.add(coin);
		}
		int third = (rightW - 4) / 3;
		TalismanButton look = new TalismanButton(rightX, talismanY, third, 13, () -> Component.translatable("kultivator.sectbuild.ui.look"));
		look.enabledWhen(() -> locked(selected) == null);
		look.onPress(() -> {
			consent = false;
			BuildClient.send(0, "preview:" + selected + ":-1:" + Glaze.byOrdinal(glaze).id() + (upgrade ? ":upgrade" : ""));
		});
		look.tooltip(() -> List.of(Component.translatable("kultivator.sectbuild.ui.look"), Component.translatable("kultivator.sectbuild.ui.look_tip")));
		widgets.add(look);
		TalismanButton turn = new TalismanButton(rightX + third + 2, talismanY, third, 13, () -> Component.translatable("kultivator.sectbuild.ui.rotate"));
		turn.enabledWhen(() -> preview() != null && !upgrade);
		turn.onPress(() -> {
			consent = false;
			BuildClient.send(0, "rotate");
		});
		widgets.add(turn);
		TalismanButton cancel = new TalismanButton(rightX + 2 * (third + 2), talismanY, third, 13, () -> Component.translatable("kultivator.sectbuild.ui.cancel"));
		cancel.enabledWhen(() -> preview() != null);
		cancel.onPress(() -> {
			consent = false;
			BuildClient.send(0, "cancel");
		});
		widgets.add(cancel);

		sealR = Math.max(9.0F, Math.min(13.0F, (contentBottom - bottomY) * 0.28F));
		sealX = rightX + rightW - sealR - 4.0F;
		sealY = contentBottom - sealR - 9.0F;
		boxX0 = rightX;
		boxX1 = Math.round(sealX - sealR - 6.0F);
		boxY1 = contentBottom - 2;
		boxY0 = boxY1 - 14;
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float fade = backdrop.to(1.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F), 0.0F);
		g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0x70 * fade), Palette.NIGHT), Palette.argb(Math.round(0xA0 * fade), Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		tooltip = null;
		float open = Eased.out(Math.min(1.0F, (now - openedAt) / (float) UNROLL_MILLIS));
		int mid = (y0 + y1) / 2;
		int half = Math.round((y1 - y0) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, y0, y1);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		if (open < 1.0F) {
			return;
		}
		g.nextStratum();
		// The header between gold rules.
		float cx = (x0 + x1) / 2.0F;
		float headY = y0 + ScrollFrame.END + 4.0F;
		Brush.fitCentered(g, font, Brush.bold(title), cx, headY, x1 - x0 - 60.0F, Palette.INK, 1.0F, 0.6F, false);
		Brush.rect(g, x0 + 20.0F, headY + 10.0F, x1 - 20.0F, headY + 10.6F, Palette.argb(0xB0, Palette.GOLD));

		drawGrid(g, mouseX, mouseY);
		drawDetail(g, mouseX, mouseY, now);
		for (Widget widget : widgets) {
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY) && tooltip == null && !widget.tooltipLines().isEmpty()) {
				tooltip = widget.tooltipLines();
			}
		}
		if (holding && progress(now, mouseX, mouseY) >= 1.0F && !done) {
			confirm();
		}
		if (tooltip != null) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** The 3x3 grid of jade plaques. */
	private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (int template = 1; template <= SectTemplates.COUNT; template++) {
			int col = (template - 1) % 3;
			int row = (template - 1) / 3;
			int px = gridX + col * (plaqueW + gap);
			int py = gridY + row * (plaqueH + gap);
			boolean over = mouseX >= px && mouseX < px + plaqueW && mouseY >= py && mouseY < py + plaqueH;
			boolean chosen = template == selected;
			Component lock = locked(template);
			int top = chosen ? Palette.JADE_LIGHT : over ? Palette.lighten(Palette.JADE_PALE, 0.1F) : Palette.JADE_PALE;
			int bottom = chosen ? Palette.JADE : Palette.mix(Palette.JADE_PALE, Palette.JADE_DULL, 0.5F);
			if (lock != null) {
				top = Palette.grey(top, 0.75F);
				bottom = Palette.grey(bottom, 0.75F);
			}
			g.fill(px + 1, py + 2, px + plaqueW + 1, py + plaqueH + 1, 0x40000000);
			Brush.chamferGradient(g, px, py, px + plaqueW, py + plaqueH, 2, top, bottom);
			Brush.frame(g, px + 1, py + 1, px + plaqueW - 1, py + plaqueH - 1, chosen ? Palette.GOLD : Palette.argb(0x90, Palette.JADE_DARK));
			// The ink sketch.
			float size = Math.min(plaqueW - 6.0F, plaqueH - 12.0F);
			SiteDiagram.draw(g, px + (plaqueW - size) / 2.0F, py + 2.0F, size, SiteDiagram.sketch(template), SiteDiagram.Bounds.of(template), 9, 0,
					-1, -1, false);
			// The numeral seal and the name.
			g.fill(px + 2, py + 2, px + 9, py + 9, lock == null ? Palette.SEAL : Palette.grey(Palette.SEAL, 0.7F));
			Brush.centered(g, font, Component.literal(Integer.toString(template)), px + 5.6F, py + 3.0F, Palette.TALISMAN, 0.62F, false);
			Brush.fitCentered(g, font, SectTemplates.name(template), px + plaqueW / 2.0F, py + plaqueH - 8.0F, plaqueW - 4.0F,
					lock == null ? Palette.INK : Palette.INK_FAINT, 0.6F, 0.4F, false);
			if (lock != null) {
				Glyphs.lock(g, px + plaqueW - 6.0F, py + 6.0F, 6.0F, Palette.INK_SOFT);
			}
			if (over) {
				List<Component> tip = new ArrayList<>();
				tip.add(SectTemplates.name(template));
				tip.add(Component.translatable("kultivator.sectbuild.archetype." + template));
				tip.add(Component.translatable("kultivator.sectbuild.ui.realm_hint", Component.translatable("kultivator.sectbuild.realm_hint." + template)));
				if (lock != null) {
					tip.add(lock);
				}
				tooltip = tip;
			}
		}
		// Below the grid: the source line of the chosen template and the "just deposit" promise.
		int textY = gridY + 3 * (plaqueH + gap) + 2;
		float textW = 3 * plaqueW + 2 * gap;
		Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.promise"), gridX, textY, textW, Palette.INK_SOFT, 0.55F, false);
	}

	/** The chosen template: diagram, beads, skyline, numbers, and the preview's slips, consent and seal. */
	private void drawDetail(GuiGraphicsExtractor g, int mouseX, int mouseY, long now) {
		Brush.fit(g, font, Brush.bold(SectTemplates.name(selected)), rightX, contentTop, rightW, Palette.SEAL_DARK, 0.9F, 0.55F, false);
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.archetype." + selected), rightX, contentTop + 10.0F, rightW, Palette.INK_SOFT,
				0.6F, 0.45F, false);

		CompoundTag preview = preview();
		List<SiteDiagram.Part> parts;
		SiteDiagram.Bounds bounds;
		int[] stele = preview == null ? null : preview.getIntArray("stele").orElse(null);
		int[] fp = preview == null ? null : SiteHologram.box(preview, "fp");
		if (preview != null && stele != null && stele.length == 3 && fp != null && !preview.getListOrEmpty("comps").isEmpty()) {
			int rot = preview.getIntOr("rot", 0);
			parts = SiteDiagram.fromComponents(preview.getListOrEmpty("comps"), stele, rot);
			bounds = SiteDiagram.bounds(fp, stele, rot);
		} else {
			parts = SiteDiagram.sketch(selected);
			bounds = SiteDiagram.Bounds.of(selected);
		}
		int scrub = BuildClient.scrubTier();
		int hover = SiteDiagram.draw(g, diagramX, diagramY, diagramSize, parts, bounds, scrub, 0, mouseX, mouseY, true);
		Brush.frame(g, diagramX - 1, diagramY - 1, diagramX + diagramSize + 1, diagramY + diagramSize + 1, Palette.argb(0xC0, Palette.GOLD));
		if (hover >= 0) {
			SiteDiagram.Part part = parts.get(hover);
			List<Component> tip = new ArrayList<>();
			tip.add(part.name() != null ? part.name() : Component.translatable("kultivator.sectbuild.ui.part." + part.shape()));
			tip.add(Component.translatable("kultivator.sectbuild.ui.part_tier", part.tier(), SectTemplates.tierName(part.tier())));
			tooltip = tip;
		}
		// The nine jade beads of the scrubber.
		float beadStep = diagramSize / 9.0F;
		for (int tier = 1; tier <= 9; tier++) {
			float bx = diagramX + beadStep * (tier - 0.5F);
			boolean lit = tier <= scrub;
			Brush.disc(g, bx, beadY + 2.5F, 2.6F, Palette.darken(lit ? Palette.JADE_LIGHT : Palette.JADE_DULL, 0.3F));
			Brush.disc(g, bx, beadY + 2.5F, 2.0F, lit ? Palette.JADE_LIGHT : Palette.JADE_DULL);
			if (tier == scrub) {
				Brush.ring(g, bx, beadY + 2.5F, 2.8F, 3.4F, Palette.GOLD);
			}
			if (Math.abs(mouseX - bx) < beadStep / 2.0F && Math.abs(mouseY - beadY - 2.5F) < 4.0F) {
				tooltip = List.of(Component.translatable("kultivator.sectbuild.ui.part_tier", tier, SectTemplates.tierName(tier)));
			}
		}
		SiteDiagram.skyline(g, rightX, skylineY, rightW, 10.0F, parts, bounds, scrub);

		drawNumbers(g, preview);
		if (preview != null) {
			drawPreviewBottom(g, preview, mouseX, mouseY, now);
		} else {
			drawBillBottom(g);
		}
	}

	/** The numbers beside the diagram. */
	private void drawNumbers(GuiGraphicsExtractor g, @Nullable CompoundTag preview) {
		int t = selected - 1;
		PlanStats stats = stats(selected);
		boolean real = !stats.isEmpty();
		float x = diagramX + diagramSize + 5.0F;
		float w = rightX + rightW - x;
		float y = diagramY;
		float scale = 0.55F;
		List<Component> lines = new ArrayList<>();
		int width = real ? stats.width() : 2 * SiteDiagram.X_HALF[t] + 1;
		int depth = real ? stats.depth() : SiteDiagram.Z_MAX[t] - SiteDiagram.Z_MIN[t] + 1;
		lines.add(Component.translatable("kultivator.sectbuild.ui.footprint", width, depth));
		lines.add(Component.translatable("kultivator.sectbuild.ui.envelope", real ? stats.above() : ABOVE[t], real ? stats.below() : BELOW[t]));
		if (real) {
			lines.add(Component.translatable("kultivator.sectbuild.ui.components", stats.totalComponents()));
		}
		long units = real ? stats.total().totalCenti() / Bahan.CENTI : TARGET_UNITS[t];
		lines.add(Component.translatable(real ? "kultivator.sectbuild.ui.units" : "kultivator.sectbuild.ui.units_target", units));
		if (real) {
			int exact = stats.total().exact().values().stream().mapToInt(Integer::intValue).sum();
			lines.add(Component.translatable("kultivator.sectbuild.ui.exact", exact));
		}
		int builders = catalog().getIntOr("builders", 0);
		double speed = Math.max(0.25D, catalog().getDoubleOr("speed", 1.0D));
		double minutes = (real && stats.minutes() > 0.0D ? stats.minutes() : TARGET_MINUTES[t]) / ((1.0D + 0.25D * builders) * speed);
		lines.add(Component.translatable("kultivator.sectbuild.ui.time", Math.max(1L, Math.round(minutes)), builders));
		lines.add(Component.translatable("kultivator.sectbuild.ui.perks", TOWER_ROOMS[t], QI_SEAT[t], TERRITORY[t]));
		if (ALTARS[t] > 0) {
			lines.add(Component.translatable("kultivator.sectbuild.ui.altars", ALTARS[t]));
		}
		if (selected >= 5) {
			lines.add(Component.translatable("kultivator.sectbuild.ui.cap_bonus"));
		}
		if (selected >= 6) {
			lines.add(Component.translatable("kultivator.sectbuild.ui.candidate_bonus"));
		}
		lines.add(Component.translatable("kultivator.sectbuild.ui.realm_hint", Component.translatable("kultivator.sectbuild.realm_hint." + selected)));
		if (preview != null) {
			lines.add(Component.translatable("kultivator.sectbuild.ui.gate",
					Component.translatable("kultivator.sectbuild.gate." + SectTemplates.gate(preview.getIntOr("rot", 0)).getSerializedName())));
		}
		float bottom = diagramY + diagramSize;
		for (Component line : lines) {
			if (y + 9.0F * scale > bottom) {
				break;
			}
			Brush.rect(g, x, y + 2.0F * scale, x + 1.6F, y + 4.4F * scale, Palette.SEAL);
			y += Brush.wrap(g, font, line, x + 3.0F, y, w - 3.0F, Palette.INK, scale, false) + 0.5F;
		}
	}

	/** Without a preview: the whole bill as bamboo slips and the hint to look at the footprint. */
	private void drawBillBottom(GuiGraphicsExtractor g) {
		PlanStats stats = stats(selected);
		float y = bottomY;
		float scale = 0.55F;
		if (!stats.isEmpty()) {
			Bill total = stats.total();
			int columns = 3;
			float colW = rightW / (float) columns;
			int index = 0;
			for (Bahan cat : Bahan.values()) {
				long centi = total.centi(cat);
				if (centi <= 0L) {
					continue;
				}
				float sx = rightX + (index % columns) * colW;
				float sy = y + (index / columns) * 8.0F;
				if (sy + 7.0F > contentBottom) {
					break;
				}
				Brush.rect(g, sx, sy + 1.0F, sx + 3.0F, sy + 6.0F, Palette.opaque(cat.rgb()));
				Brush.fit(g, font, Component.translatable("kultivator.sectbuild.amount", centi / Bahan.CENTI, cat.displayName()), sx + 5.0F, sy, colW - 6.0F,
						Palette.INK, scale, 0.4F, false);
				index++;
			}
			y += ((index + columns - 1) / columns) * 8.0F + 2.0F;
		}
		if (y + 8.0F < contentBottom) {
			Brush.wrap(g, font, Component.translatable("kultivator.sectbuild.ui.look_hint"), rightX, y, rightW, Palette.INK_SOFT, scale, false);
		}
	}

	/** With a preview: the warnings as slips, the consent checkbox and the seal to hold. */
	private void drawPreviewBottom(GuiGraphicsExtractor g, CompoundTag preview, int mouseX, int mouseY, long now) {
		List<CompoundTag> warnings = new ArrayList<>();
		for (Tag each : preview.getListOrEmpty("warn")) {
			if (each instanceof CompoundTag warning) {
				warnings.add(warning);
			}
		}
		warnings.sort((a, b) -> Integer.compare(b.getIntOr("sev", 0), a.getIntOr("sev", 0)));
		float y = bottomY;
		int snap = preview.getIntOr("snap", 0);
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.hud.snapshot", snap), rightX, y, boxX1 - rightX, snap >= 100 ? Palette.JADE_DARK
				: Palette.WARN, 0.6F, 0.45F, false);
		y += 7.0F;
		int slots = Math.max(1, (int) ((boxY0 - 2 - y) / 8.0F));
		warnScroll = Math.max(0, Math.min(warnScroll, Math.max(0, warnings.size() - slots)));
		for (int i = warnScroll; i < Math.min(warnings.size(), warnScroll + slots); i++) {
			CompoundTag warning = warnings.get(i);
			int sev = warning.getIntOr("sev", 0);
			int slip = sev >= 2 ? 0xFFE8B4A8 : sev == 1 ? Palette.BAMBOO : Palette.BAMBOO_ALT;
			float sy = y + (i - warnScroll) * 8.0F;
			Brush.rect(g, rightX, sy, boxX1, sy + 7.0F, Palette.argb(0xD0, slip));
			Brush.rect(g, rightX, sy, rightX + 1.5F, sy + 7.0F, sev >= 2 ? Palette.SEAL : sev == 1 ? Palette.WARN : Palette.BAMBOO_KNOT);
			Component text = BuildClient.warning(warning);
			Brush.fit(g, font, text, rightX + 3.0F, sy + 1.0F, boxX1 - rightX - 4.0F, Palette.INK, 0.55F, 0.4F, false);
			if (mouseX >= rightX && mouseX < boxX1 && mouseY >= sy && mouseY < sy + 7.0F) {
				tooltip = List.of(text);
			}
		}
		if (warnings.isEmpty()) {
			Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.no_warnings"), rightX, y, boxX1 - rightX, Palette.JADE_DARK, 0.6F, 0.45F, false);
		}
		// The consent checkbox talisman.
		boolean overBox = mouseX >= boxX0 && mouseX < boxX1 && mouseY >= boxY0 && mouseY < boxY1;
		g.fillGradient(boxX0, boxY0, boxX1, boxY1, overBox ? 0xFFF7DFA0 : Palette.TALISMAN, Palette.TALISMAN_LOW);
		Brush.frame(g, boxX0, boxY0, boxX1, boxY1, consent ? Palette.SEAL : Palette.TALISMAN_EDGE);
		int check = boxY1 - boxY0 - 5;
		g.fill(boxX0 + 2, boxY0 + 2, boxX0 + 2 + check, boxY0 + 2 + check, 0xFFFFF4D6);
		Brush.frame(g, boxX0 + 2, boxY0 + 2, boxX0 + 2 + check, boxY0 + 2 + check, Palette.INK);
		if (consent) {
			float bx = boxX0 + 2.0F;
			float by = boxY0 + 2.0F;
			Brush.line(g, bx + 1.5F, by + check * 0.55F, bx + check * 0.42F, by + check - 1.5F, 1.4F, Palette.SEAL);
			Brush.line(g, bx + check * 0.42F, by + check - 1.5F, bx + check - 1.0F, by + 1.0F, 1.4F, Palette.SEAL);
		}
		Brush.fit(g, font, Component.translatable("kultivator.sectbuild.ui.consent_short"), boxX0 + check + 5.0F, boxY0 + 3.5F, boxX1 - boxX0 - check - 7.0F,
				Palette.INK, 0.6F, 0.4F, false);
		if (overBox) {
			tooltip = List.of(Component.translatable("kultivator.sectbuild.consent"));
		}
		// The jade Konfirmasi seal, held 3 s.
		boolean ready = consent && confirmable();
		float progress = progress(now, mouseX, mouseY);
		boolean overSeal = overSeal(mouseX, mouseY);
		if (ready) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0D);
			Brush.halo(g, sealX, sealY, sealR, 5.0F, 0x9CFFD0, (overSeal ? 0.8F : 0.4F) * (0.6F + 0.4F * pulse));
		}
		Brush.disc(g, sealX, sealY, sealR + 1.5F, Palette.JADE_DARK);
		Brush.band(g, sealX, sealY, sealR, -sealR, sealR, ready ? Palette.JADE_LIGHT : Palette.JADE_DULL, ready ? Palette.JADE : Palette.grey(Palette.JADE, 0.6F));
		if (progress > 0.0F) {
			Brush.arc(g, sealX, sealY, sealR + 2.0F, sealR + 4.0F, 0.0F, progress, 0xFFFFD27A);
		}
		Brush.rect(g, sealX - sealR * 0.45F, sealY - 0.6F, sealX + sealR * 0.45F, sealY + 0.6F, Palette.argb(0xD0, Palette.TALISMAN));
		Brush.rect(g, sealX - 0.6F, sealY - sealR * 0.45F, sealX + 0.6F, sealY + sealR * 0.45F, Palette.argb(0xD0, Palette.TALISMAN));
		Component hint = ready ? Component.translatable("kultivator.sectbuild.ui.hold", HOLD_MILLIS / 1000L)
				: Component.translatable(consent ? "kultivator.sectbuild.ui.not_ready" : "kultivator.sectbuild.ui.check_first");
		Brush.fitCentered(g, font, hint, sealX, sealY + sealR + 3.0F, sealR * 2.0F + 22.0F, Palette.INK_SOFT, 0.55F, 0.4F, false);
		if (overSeal) {
			tooltip = List.of(Component.translatable("kultivator.sectbuild.ui.confirm"), hint);
		}
	}

	// ================================================================== the hold

	private boolean overSeal(double mouseX, double mouseY) {
		double dx = mouseX - sealX;
		double dy = mouseY - sealY;
		return dx * dx + dy * dy <= (sealR + 3.0F) * (sealR + 3.0F);
	}

	private float progress(long now, int mouseX, int mouseY) {
		if (!holding) {
			return 0.0F;
		}
		if (!consent || !confirmable() || !overSeal(mouseX, mouseY)) {
			holding = false;
			return 0.0F;
		}
		return Math.min(1.0F, (now - holdStart) / (float) HOLD_MILLIS);
	}

	private void confirm() {
		CompoundTag preview = preview();
		if (preview == null) {
			holding = false;
			return;
		}
		done = true;
		holding = false;
		BuildClient.send(0, "confirm:" + preview.getLongOr("ticket", 0L));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.8F, 0.9F));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F));
		onClose();
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();
		for (int i = widgets.size() - 1; i >= 0; i--) {
			Widget widget = widgets.get(i);
			if (widget.contains(mx, my)) {
				widget.press();
				pressed = widget;
				return true;
			}
		}
		for (int template = 1; template <= SectTemplates.COUNT; template++) {
			int px = gridX + (template - 1) % 3 * (plaqueW + gap);
			int py = gridY + (template - 1) / 3 * (plaqueH + gap);
			if (mx >= px && mx < px + plaqueW && my >= py && my < py + plaqueH) {
				if (template != selected) {
					select(template);
					UiSound.tab();
				}
				return true;
			}
		}
		float beadStep = diagramSize / 9.0F;
		if (my >= beadY - 2 && my <= beadY + 7 && mx >= diagramX && mx < diagramX + diagramSize) {
			BuildClient.setScrubTier((int) ((mx - diagramX) / beadStep) + 1);
			UiSound.toggle(true);
			return true;
		}
		if (preview() != null) {
			if (mx >= boxX0 && mx < boxX1 && my >= boxY0 && my < boxY1) {
				consent = !consent;
				UiSound.toggle(consent);
				return true;
			}
			if (overSeal(mx, my)) {
				if (consent && confirmable()) {
					holding = true;
					holdStart = Util.getMillis();
					UiSound.ask();
				} else {
					UiSound.denied();
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (holding) {
			holding = false;
			return true;
		}
		if (pressed != null) {
			Widget widget = pressed;
			pressed = null;
			widget.release(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x >= diagramX && x < diagramX + diagramSize && y >= diagramY && y < diagramY + diagramSize && scrollY != 0.0D) {
			BuildClient.setScrubTier(BuildClient.scrubTier() + (scrollY > 0.0D ? 1 : -1));
			return true;
		}
		if (y >= bottomY && x >= rightX && scrollY != 0.0D) {
			warnScroll = Math.max(0, warnScroll + (scrollY < 0.0D ? 1 : -1));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		int number = event.key() - InputConstants.KEY_1;
		if (number >= 0 && number < SectTemplates.COUNT) {
			select(number + 1);
			UiSound.tab();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** "1.570" style grouping is left to the language; numbers stay plain (no locale surprises). */
	static String number(long value) {
		return String.format(Locale.ROOT, "%d", value);
	}
}
