package dev.kultivator.client.sekte.misi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Gulungan Laporan opened (DESIGN 36.2.7, module X): a long paper scroll between two lacquered rods that unrolls
 * downward. At the top the mission's seal, its name and tier, the day, the stance and a big red grade stamp
 * (極 legendary, 成 success, 半 partial, 敗 failure); the team with the leader first; then the story in five
 * paragraphs (the departure in the chosen stance, three stages each in its {@code ok} or {@code gagal} branch with a bead
 * in the stage kind's colour, an ambush or a wound where it happened, and the ending); then what came home (youths,
 * treasures, a treasure birth's place, the found disciple, the standing with the sect met) and up to two lines quoted
 * from the members' own memories. The wheel scrolls; Esc closes (back to the tablet when opened from it).
 * <p>
 * Every text is a lang key resolved here, so the report reads in the reader's language.
 */
public class MisiLaporanScreen extends Screen {

	private static final long UNROLL_MS = 420L;
	private static final String[] GRADE_GLYPH = {"敗", "半", "成", "極"};
	private static final int[] GRADE_COLOR = {0xA0352A, 0xB8860B, 0x2E8B57, 0xD4A017};

	private final CompoundTag report;
	private final @Nullable Screen parent;
	private final long openedAt = Util.getMillis();
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private float scroll;
	private float contentHeight;

	public MisiLaporanScreen(CompoundTag report, @Nullable Screen parent) {
		super(Component.translatable("kultivator.sekte.misi.lapor.judul_layar"));
		this.report = report == null ? new CompoundTag() : report;
		this.parent = parent instanceof MisiLaporanScreen ? null : parent;
	}

	@Override
	protected void init() {
		int w = Math.min(360, width - 20);
		int h = Math.min(270, height - 16);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xB0100A06, 0xD0050302);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		float open = Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MS);
		float eased = 1.0F - (1.0F - open) * (1.0F - open);
		int bottom = Math.round(y0 + 6 + (y1 - y0 - 6) * eased);
		// the paper, its shadow and the two rods
		g.fill(x0 + 3, y0 + 4, x1 + 3, bottom + 4, 0x60000000);
		g.fillGradient(x0, y0, x1, bottom, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		for (int i = 0; i < 7; i++) {
			// faint fibre lines of the bamboo paper
			int fy = y0 + 18 + i * 37;
			if (fy < bottom - 4) {
				g.fill(x0 + 6, fy, x1 - 6, fy + 1, 0x10000000);
			}
		}
		Brush.frame(g, x0, y0, x1, bottom, Palette.argb(0xC0, Palette.GOLD_DARK));
		g.fillGradient(x0 - 5, y0 - 4, x1 + 5, y0 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		g.fillGradient(x0 - 5, bottom - 3, x1 + 5, bottom + 4, Palette.ROD_LIGHT, Palette.ROD_DARK);
		Brush.disc(g, x0 - 6.0F, y0 - 0.5F, 3.0F, Palette.SEAL_DARK);
		Brush.disc(g, x1 + 6.0F, y0 - 0.5F, 3.0F, Palette.SEAL_DARK);
		Brush.disc(g, x0 - 6.0F, bottom + 0.5F, 3.0F, Palette.SEAL_DARK);
		Brush.disc(g, x1 + 6.0F, bottom + 0.5F, 3.0F, Palette.SEAL_DARK);
		if (open < 1.0F) {
			return;
		}
		int clipTop = y0 + 4;
		int clipBottom = y1 - 5;
		g.enableScissor(x0 + 2, clipTop, x1 - 2, clipBottom);
		float end = drawContent(g, y0 + 8.0F - scroll);
		g.disableScissor();
		contentHeight = end + scroll - (y0 + 8.0F);
		float room = clipBottom - clipTop - 6.0F;
		if (contentHeight > room) {
			// a thin cord on the right shows where in the scroll we are
			float share = room / contentHeight;
			float at = scroll / Math.max(1.0F, contentHeight - room);
			float barH = Math.max(12.0F, (clipBottom - clipTop) * share);
			float barY = clipTop + (clipBottom - clipTop - barH) * at;
			Brush.rect(g, x1 - 5.0F, clipTop, x1 - 4.0F, clipBottom, Palette.argb(0x40, Palette.INK_FAINT));
			Brush.rect(g, x1 - 6.0F, barY, x1 - 3.0F, barY + barH, Palette.argb(0xC0, Palette.SEAL));
		}
	}

	/** Draws the report from {@code y}; returns the y below it. */
	private float drawContent(GuiGraphicsExtractor g, float y) {
		String tpl = report.getStringOr("tpl", "");
		int grade = Mth.clamp(report.getIntOr("grade", 0), 0, 3);
		float left = x0 + 16.0F;
		float width = x1 - x0 - 32.0F;
		// ---- the head: seal, title, day and stance, the grade stamp
		MisiTab.seal(g, font, left + 10.0F, y + 10.0F, 10.0F, MisiTab.colorOf(tpl), MisiTab.glyphOf(tpl), false);
		Component title = Brush.bold(Component.translatable("kultivator.sekte.misi.lapor.judul", Component.translatable(MisiTab.tplKey(tpl)),
				report.getIntOr("tier", 1)));
		Brush.fit(g, font, title, left + 25.0F, y + 2.0F, width - 70.0F, Palette.INK, 1.0F, 0.55F, false);
		Component sub = Component.translatable("kultivator.sekte.misi.lapor.sub", report.getLongOr("day", 0L),
				Component.translatable("kultivator.sekte.misi.sikap." + report.getStringOr("sikap", "damai")));
		Brush.fit(g, font, sub, left + 25.0F, y + 13.0F, width - 70.0F, Palette.INK_SOFT, 0.62F, 0.45F, false);
		float sx = x1 - 38.0F;
		float sy = y + 12.0F;
		Brush.disc(g, sx, sy, 15.0F, Palette.argb(0x30, GRADE_COLOR[grade]));
		Brush.ring(g, sx, sy, 12.0F, 14.0F, Palette.argb(0xE0, GRADE_COLOR[grade]));
		Brush.ring(g, sx, sy, 9.5F, 10.5F, Palette.argb(0x90, GRADE_COLOR[grade]));
		Brush.centered(g, font, Component.literal(GRADE_GLYPH[grade]), sx, sy - 7.0F, Palette.argb(0xFF, GRADE_COLOR[grade]), 1.6F, false);
		Brush.centered(g, font, Component.translatable("kultivator.sekte.misi.grade." + grade), sx, sy + 16.0F, Palette.argb(0xFF, GRADE_COLOR[grade]),
				0.55F, false);
		y += 34.0F;
		// ---- the team
		List<CompoundTag> team = MisiTab.compounds(report.getListOrEmpty("team"));
		MutableComponent names = Component.translatable("kultivator.sekte.misi.lapor.tim");
		for (int i = 0; i < team.size(); i++) {
			CompoundTag member = team.get(i);
			names = names.append(Component.literal(i == 0 ? " " : ", "));
			MutableComponent name = MisiTab.name(member, "name").copy().withColor(Palette.onPaper(member.getIntOr("color", 0x888888)));
			if (i == 0) {
				name = name.append(Component.translatable("kultivator.sekte.misi.lapor.pemimpin").withColor(Palette.INK_FAINT));
			}
			if (member.getBooleanOr("left", false)) {
				name = name.append(Component.translatable("kultivator.sekte.misi.lapor.ditinggal_tanda").withColor(Palette.DANGER));
			} else if (member.getBooleanOr("hurt", false)) {
				name = name.append(Component.literal(" ✚").withColor(0xFFC0392B));
			}
			names = names.append(name);
		}
		y += Brush.wrap(g, font, names, left, y, width, Palette.INK_SOFT, 0.65F, false) + 3.0F;
		rule(g, left, y, width);
		y += 6.0F;
		// ---- the story
		Component leader = team.isEmpty() ? Component.literal("?") : MisiTab.name(team.get(0), "name");
		Component companion = team.size() > 1 ? MisiTab.name(team.get(1), "name") : leader;
		Component place = Component.translatable("kultivator.sekte.misi.tempat." + report.getIntOr("place", 0));
		Component foe = report.contains("sect_name") ? MisiTab.name(report, "sect_name")
				: "samar".equals(tpl) ? Component.translatable("kultivator.sekte.misi.sekte_umum")
				: Component.translatable("kultivator.sekte.misi.lawan." + report.getIntOr("foe", 0));
		Component extra = extra(foe);
		y += paragraph(g, Component.translatable("kultivator.sekte.misi.buka." + report.getStringOr("sikap", "damai"), leader, place, team.size()),
				left, y, width, null, 0) + 4.0F;
		ListTag stages = report.getListOrEmpty("stages");
		String[] kinds = MisiTab.kindsOf(tpl);
		for (int i = 0; i < 3; i++) {
			CompoundTag stage = i < stages.size() && stages.get(i) instanceof CompoundTag t && !t.isEmpty() ? t : null;
			if (stage == null) {
				y += paragraph(g, Component.translatable("kultivator.sekte.misi.lapor.batal", i + 1).withStyle(ChatFormatting.ITALIC), left, y, width,
						kinds[i], 0) + 4.0F;
				continue;
			}
			boolean ok = stage.getBooleanOr("ok", false);
			MutableComponent text = Component.empty();
			if (stage.getBooleanOr("amb", false)) {
				text.append(Component.translatable("kultivator.sekte.misi.lapor.sergap", foe).withColor(Palette.DANGER)).append(Component.literal(" "));
			}
			text.append(Component.translatable(MisiTab.tplKey(tpl) + ".s" + (i + 1) + (ok ? ".ok" : ".gagal"), leader, companion, place, foe, extra));
			String hurt = stage.getStringOr("hurt", "");
			if (!hurt.isEmpty()) {
				for (CompoundTag member : team) {
					if (hurt.equals(member.getStringOr("soul", ""))) {
						text.append(Component.literal(" ")).append(Component.translatable("kultivator.sekte.misi.lapor.luka", MisiTab.name(member, "name"))
								.withColor(Palette.DANGER));
					}
				}
			}
			y += paragraph(g, text, left, y, width, stage.getStringOr("kind", kinds[i]), ok ? 1 : 2) + 4.0F;
		}
		if ("cari".equals(tpl)) {
			Component target = report.contains("target_name") ? MisiTab.name(report, "target_name") : Component.literal("?");
			y += paragraph(g, Component.translatable(report.getBooleanOr("found", false) ? "kultivator.sekte.misi.temu.ok" : "kultivator.sekte.misi.temu.gagal",
					target, leader), left, y, width, null, 0) + 4.0F;
		}
		if (report.getBooleanOr("ditarik", false)) {
			y += paragraph(g, Component.translatable("kultivator.sekte.misi.lapor.ditarik").withStyle(ChatFormatting.ITALIC), left, y, width, null, 0) + 4.0F;
		}
		y += paragraph(g, Component.translatable("kultivator.sekte.misi.tutup." + grade, leader), left, y, width, null, 0) + 5.0F;
		// ---- what came home
		rule(g, left, y, width);
		y += 5.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.sekte.misi.lapor.hasil")), left, y, width, Palette.SEAL_DARK, 0.72F, 0.5F, false);
		y += 10.0F;
		List<Component> results = results(team);
		for (Component line : results) {
			Brush.disc(g, left + 2.5F, y + 3.0F, 1.4F, Palette.SEAL);
			y += Brush.wrap(g, font, line, left + 7.0F, y, width - 7.0F, Palette.INK, 0.62F, false) + 2.0F;
		}
		// ---- quoted memories
		List<CompoundTag> quotes = MisiTab.compounds(report.getListOrEmpty("quotes"));
		if (!quotes.isEmpty()) {
			y += 3.0F;
			for (CompoundTag quote : quotes) {
				Component said = Component.translatable(quote.getStringOr("text", ""), quote.getStringOr("arg", ""));
				Component line = Component.translatable("kultivator.sekte.misi.lapor.kutip", MisiTab.name(quote, "name"), said)
						.withStyle(ChatFormatting.ITALIC);
				g.fill(Math.round(left), Math.round(y), Math.round(left) + 2, Math.round(y + Brush.wrapHeight(font, line, width - 8.0F, 0.6F)),
						Palette.argb(0xA0, Palette.JADE_DARK));
				y += Brush.wrap(g, font, line, left + 6.0F, y, width - 8.0F, Palette.INK_SOFT, 0.6F, false) + 3.0F;
			}
		}
		y += 4.0F;
		y += Brush.wrap(g, font, Component.translatable("kultivator.sekte.misi.sumber").withStyle(ChatFormatting.ITALIC), left, y, width,
				Palette.INK_FAINT, 0.5F, false);
		return y + 6.0F;
	}

	private Component extra(Component foe) {
		if (report.contains("target_name")) {
			return MisiTab.name(report, "target_name");
		}
		String village = report.getStringOr("village", "");
		if (!village.isEmpty()) {
			return Component.literal(village);
		}
		if ("desa".equals(report.getStringOr("tpl", ""))) {
			return Component.translatable("kultivator.sekte.misi.desa_umum");
		}
		return foe;
	}

	private List<Component> results(List<CompoundTag> team) {
		List<Component> lines = new ArrayList<>();
		int recruits = report.getIntOr("recruits", 0);
		if (recruits > 0) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.calon", recruits).withColor(Palette.JADE_DARK));
		}
		for (CompoundTag item : MisiTab.compounds(report.getListOrEmpty("items"))) {
			lines.add(MisiTab.name(item, "text"));
		}
		if (report.getIntOr("more", 0) > 0) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.lagi", report.getIntOr("more", 0)));
		}
		if (report.contains("map")) {
			CompoundTag map = report.getCompoundOrEmpty("map");
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.peta", map.getIntOr("x", 0), map.getIntOr("z", 0)).withColor(0xFF8E44AD));
		}
		if (report.getBooleanOr("found", false)) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.ditemukan",
					report.contains("target_name") ? MisiTab.name(report, "target_name") : Component.literal("?")).withColor(Palette.JADE_DARK));
		}
		int standing = report.getIntOr("standing", 0);
		if (standing != 0 && report.contains("sect_name")) {
			lines.add(Component.translatable(standing > 0 ? "kultivator.sekte.misi.standing.naik" : "kultivator.sekte.misi.standing.turun",
					MisiTab.name(report, "sect_name"), Math.abs(standing)));
		}
		if (report.getIntOr("refund", 0) > 0) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.refund", report.getIntOr("refund", 0)));
		}
		for (CompoundTag member : team) {
			if (member.getBooleanOr("left", false)) {
				lines.add(Component.translatable("kultivator.sekte.misi.lapor.ditinggal", MisiTab.name(member, "name")).withColor(Palette.DANGER));
			} else if (member.getBooleanOr("hurt", false)) {
				lines.add(Component.translatable("kultivator.sekte.misi.lapor.terluka", MisiTab.name(member, "name")).withColor(Palette.DANGER));
			}
		}
		if (lines.isEmpty()) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.kosong"));
		}
		return lines;
	}

	/** One paragraph, with a stage bead in the margin when {@code kind} is set; returns its height. */
	private float paragraph(GuiGraphicsExtractor g, Component text, float left, float y, float width, @Nullable String kind, int state) {
		float indent = kind == null ? 0.0F : 12.0F;
		if (kind != null) {
			MisiTab.bead(g, font, left + 4.5F, y + 4.0F, 4.3F, kind, state, false);
		}
		return Math.max(kind == null ? 0.0F : 9.0F, Brush.wrap(g, font, text, left + indent, y, width - indent, Palette.INK, 0.68F, false));
	}

	private static void rule(GuiGraphicsExtractor g, float left, float y, float width) {
		Brush.rect(g, left, y, left + width, y + 0.7F, Palette.argb(0x90, Palette.GOLD));
		Brush.disc(g, left + width / 2.0F, y + 0.35F, 1.6F, Palette.SEAL);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		float room = y1 - y0 - 15.0F;
		scroll = Mth.clamp(scroll - (float) scrollY * 14.0F, 0.0F, Math.max(0.0F, contentHeight - room));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
