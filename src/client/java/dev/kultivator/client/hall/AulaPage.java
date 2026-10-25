package dev.kultivator.client.hall;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Sekte › Ikhtisar › Aula (DESIGN 20.3.1, 43.5): the hall's name, level seal and renown bar, the numbers its level decides
 * and the master's {@code /sect} group "Saudara Sekte" (43.14 S3, R6: a list here instead of a chat command); then the
 * building checklist with hints, the Formasi Agung line, the schedule of the next events and the teams away. Narrow
 * layout: one scrolling column; otherwise the hall on the left and the rest in a scrolling panel on the right. All text
 * at 1.0 (43.0.5). Renaming moved to Identitas. Reads {@link HallClient#info()}; the Gulungan asks for fresh details every
 * 4 s while the Sekte section is shown.
 */
public class AulaPage extends Page {

	private static final int BARIS = 10;

	private final GulirKolom kiri = new GulirKolom();
	private final GulirKolom kanan = new GulirKolom();

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.aula");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.aula.short");
	}

	@Override
	public int glyph() {
		return Glyphs.HALL;
	}

	@Override
	public String helpTopic() {
		return "aula";
	}

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		HallClient.minta();
		super.onShow(fokus);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		CompoundTag info = HallClient.info();
		if (tata() == Tata.SEMPIT) {
			int cy = kiri.mulai(l, x, y, x + w, y + h);
			int x1 = x + kiri.lebar();
			cy = aula(l, info, x, cy, x1);
			cy = bangunan(l, info, x, cy + 4, x1);
			cy = jadwal(l, info, x, cy + 4, x1);
			cy = saudara(l, info, x, cy + 4, x1);
			kiri.selesai(l, cy);
			return;
		}
		int lw = Math.max(140, Math.min(200, Math.round(w * 0.44F)));
		int cy = kiri.mulai(l, x, y, x + lw - 4, y + h);
		cy = aula(l, info, x, cy, x + lw - 8);
		cy = saudara(l, info, x, cy + 4, x + lw - 8);
		kiri.selesai(l, cy);
		int px0 = x + lw;
		l.sumur(px0, y, x + w, y + h);
		int ry = kanan.mulai(l, px0 + 4, y + 2, x + w - 2, y + h - 2);
		int rx1 = px0 + 4 + kanan.lebar();
		ry = bangunan(l, info, px0 + 4, ry, rx1);
		ry = jadwal(l, info, px0 + 4, ry + 4, rx1);
		kanan.selesai(l, ry);
	}

	// ================================================================== the hall

	/** Name, level seal, master, renown bar and the level's numbers; returns the y below. */
	private int aula(Lukis l, CompoundTag info, int x0, int y0, int x1) {
		int width = x1 - x0;
		int cy = y0 + l.kepala(HallClient.hallName(info), x0, y0, x1);
		int level = info.getIntOr("level", 1);
		float r = 13.0F;
		float sx = x0 + r + 1.0F;
		float sy = cy + r;
		Brush.disc(l.g, sx + 0.5F, sy + 1.0F, r, 0x40000000);
		Brush.disc(l.g, sx, sy, r, Palette.JADE_DARK);
		Brush.disc(l.g, sx, sy, r - 1.5F, Palette.JADE);
		Brush.ring(l.g, sx, sy, r - 3.5F, r - 2.8F, Palette.argb(0xC0, Palette.GOLD_LIGHT));
		Brush.centered(l.g, l.font, Brush.bold(Component.literal(String.valueOf(level))), sx, sy - 6.0F, Palette.GOLD_LIGHT, 1.5F, true);
		if (l.di(Math.round(sx - r), Math.round(sy - r), Math.round(sx + r), Math.round(sy + r))) {
			l.tip(List.of(Component.translatable("kultivator.hall.ui.level", level),
					Component.translatable("kultivator.hall.ui.level.tip", info.getIntOr("renown_level", 1), info.getIntOr("bcap", 2))));
		}
		int tx = Math.round(sx + r + 4.0F);
		int tw = x1 - tx;
		int ty = cy;
		l.teks(Component.translatable("kultivator.hall.ui.level", level), tx, ty, tw, Palette.SEAL_DARK);
		ty += BARIS;
		String sect = info.getStringOr("sect", "");
		if (!sect.isEmpty()) {
			l.teks(Component.translatable("kultivator.hall.ui.sect", sect), tx, ty, tw, l.gaya.teksLemah);
			ty += BARIS;
		}
		l.teks(Component.translatable("kultivator.hall.ui.master", info.getStringOr("owner_name", "")), tx, ty, tw, l.gaya.teksLemah);
		ty += BARIS;
		if (info.getBooleanOr("dormant", false)) {
			l.teks(Component.translatable("kultivator.hall.ui.dormant").withStyle(ChatFormatting.BOLD), tx, ty, tw, Palette.DANGER);
			ty += BARIS;
		}
		cy = Math.max(ty, Math.round(sy + r) + 3);
		// renown: name and value on one line, the bar under it
		long renown = info.getLongOr("renown", 0L);
		long next = info.getLongOr("renown_next", -1L);
		long base = info.getLongOr("renown_base", 0L);
		float fraction = next < 0L ? 1.0F : (float) (renown - base) / Math.max(1L, next - base);
		Component value = Component.literal(next < 0L ? Brush.angka(renown) : Brush.angka(renown) + " / " + Brush.angka(next));
		int vw = Math.min(width / 2, l.font.width(value));
		l.teks(Component.translatable("kultivator.hall.ui.renown"), x0, cy, width - vw - 4, l.gaya.teksLemah);
		l.kanan(value, x1, cy, Palette.GOLD_DARK);
		int by = cy + BARIS;
		Brush.rect(l.g, x0, by, x1, by + 4.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
		float fill = x0 + 0.5F + (width - 1.0F) * Math.max(0.0F, Math.min(1.0F, fraction));
		Brush.rect(l.g, x0 + 0.5F, by + 0.5F, fill, by + 3.5F, Palette.GOLD);
		Brush.rect(l.g, x0 + 0.5F, by + 0.5F, fill, by + 1.3F, Palette.GOLD_LIGHT);
		if (l.di(x0, cy, x1, by + 4)) {
			l.tip(List.of(Component.translatable("kultivator.hall.ui.renown"), Component.translatable("kultivator.hall.ui.renown.tip"),
					Component.translatable("kultivator.hall.ui.renown.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
		cy = by + 8;
		int alive = info.getIntOr("alive", -1);
		Component[] lines = {
				alive >= 0 ? Component.translatable("kultivator.hall.ui.cap", alive, info.getIntOr("cap", 5))
						: Component.translatable("kultivator.hall.ui.cap_only", info.getIntOr("cap", 5)),
				Component.translatable("kultivator.hall.ui.radius", info.getIntOr("radius", 24)),
				Component.translatable("kultivator.hall.ui.price", String.format(Locale.ROOT, "%.2f", info.getDoubleOr("price", 1.0D))),
				Component.translatable("kultivator.hall.ui.per_arrival", info.getIntOr("per_arrival", 2)),
				Component.translatable("kultivator.hall.ui.posts", info.getIntOr("posts", 1), info.getIntOr("missions", 3)),
				Component.translatable("kultivator.hall.ui.pool", info.getIntOr("pool", 0))};
		for (Component line : lines) {
			Brush.rect(l.g, x0 + 1.0F, cy + 3.0F, x0 + 3.0F, cy + 5.0F, Palette.JADE);
			l.teks(line, x0 + 6, cy, width - 6, l.gaya.teks);
			cy += BARIS;
		}
		return cy;
	}

	/** "Saudara Sekte": the master's {@code /sect} group (S3), the master first, online members marked. */
	private int saudara(Lukis l, CompoundTag info, int x0, int y0, int x1) {
		int cy = y0 + l.kepala(Component.translatable("kultivator.sekte_sambung.aula.saudara"), x0, y0, x1);
		ListTag list = info.getListOrEmpty("saudara");
		if (list.isEmpty()) {
			cy += Math.round(l.bungkus(Component.translatable("kultivator.gui43.m05.saudara.kosong"), x0 + 2, cy, x1 - x0 - 2, l.gaya.teksSamar));
			return cy + 2;
		}
		long day = info.getLongOr("day", 0L);
		for (Tag tag : list) {
			if (!(tag instanceof CompoundTag e)) {
				continue;
			}
			boolean online = e.getBooleanOr("online", false);
			boolean master = e.getBooleanOr("master", false);
			Brush.disc(l.g, x0 + 3.0F, cy + 4.0F, 2.0F, online ? Palette.JADE : Palette.LOCKED);
			Component name = Component.literal(e.getStringOr("name", "?"));
			if (master) {
				name = name.copy().append(" · ").append(Component.translatable("kultivator.gui43.m05.saudara.ketua").withColor(Palette.SEAL_DARK & 0xFFFFFF));
			}
			l.teks(name, x0 + 8, cy, x1 - x0 - 8, online ? l.gaya.teks : l.gaya.teksLemah);
			if (l.di(x0, cy, x1, cy + BARIS)) {
				long joinedDay = Math.floorDiv(e.getLongOr("joined", 0L), 24000L);
				l.tip(List.of(Component.literal(e.getStringOr("name", "?")),
						Component.translatable(online ? "kultivator.gui43.m05.saudara.daring" : "kultivator.gui43.m05.saudara.luring"),
						Component.translatable("kultivator.gui43.m05.saudara.sejak", Math.max(0L, day - joinedDay)).withStyle(ChatFormatting.GRAY)));
			}
			cy += BARIS;
		}
		return cy + 2;
	}

	// ================================================================== buildings, schedule, teams away

	private int bangunan(Lukis l, CompoundTag info, int x0, int y0, int x1) {
		int cy = y0 + l.kepala(Component.translatable("kultivator.hall.ui.buildings"), x0, y0, x1);
		Component buildStatus = info.read("build_status", ComponentSerialization.CODEC).orElse(null);
		if (buildStatus != null) {
			// Phase 6b: a sect template builds the hall, so its status line replaces the checklist (DESIGN 21.17).
			cy += Math.round(l.bungkus(buildStatus, x0, cy, x1 - x0, l.gaya.teks)) + 2;
		} else {
			int flags = info.getIntOr("bflags", 0);
			CompoundTag counts = info.getCompoundOrEmpty("bcount");
			cy = bangunan(l, x0, cy, x1, true, "stele", 2, Component.translatable("kultivator.hall.ui.b.stele"));
			cy = bangunan(l, x0, cy, x1, (flags & 1) != 0, "pavilion", 4, Component.translatable("kultivator.hall.ui.b.pavilion",
					counts.getIntOr("books", 0), 10, counts.getIntOr("lecterns", 0)));
			cy = bangunan(l, x0, cy, x1, (flags & 2) != 0, "formation", 6, Component.translatable("kultivator.hall.ui.b.formation"));
			cy = bangunan(l, x0, cy, x1, (flags & 4) != 0, "drill", 8, Component.translatable("kultivator.hall.ui.b.drill",
					counts.getIntOr("targets", 0), 4));
			cy = bangunan(l, x0, cy, x1, (flags & 8) != 0, "tower", 9, Component.translatable("kultivator.hall.ui.b.tower",
					counts.getIntOr("rooms", 0), 3));
		}
		CompoundTag grand = info.getCompoundOrEmpty("formation");
		if (!grand.isEmpty()) {
			cy += 3;
			Component line = grand.getBooleanOr("grand", false)
					? Component.translatable("kultivator.hall.ui.formation_grand", grand.getIntOr("layers", 1), grand.getIntOr("radius", 16))
					: Component.translatable("kultivator.hall.ui.formation_plain");
			l.teks(line, x0, cy, x1 - x0, Palette.JADE_DARK);
			if (l.di(x0, cy, x1, cy + BARIS)) {
				l.tip(List.of(line, Component.translatable("kultivator.hall.ui.formation_grand.source")
						.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
			cy += BARIS;
			String guard = grand.getStringOr("guard", "");
			l.teks(guard.isEmpty() ? Component.translatable("kultivator.hall.ui.formation_unguarded")
					: Component.translatable("kultivator.hall.ui.formation_guard", guard), x0, cy, x1 - x0, l.gaya.teksLemah);
			cy += BARIS;
		}
		return cy;
	}

	/** One checklist line: a tick or cross, the building, and the level it allows; the hint is its tooltip. */
	private int bangunan(Lukis l, int x0, int cy, int x1, boolean ok, String id, int cap, Component text) {
		HallUi.mark(l.g, x0, cy + 2.0F, ok);
		Component allows = Component.translatable("kultivator.hall.ui.b.allows", cap);
		int capW = Math.min((x1 - x0) / 3, l.font.width(allows));
		l.teks(text, x0 + 8, cy, x1 - x0 - 12 - capW, ok ? l.gaya.teks : l.gaya.teksLemah);
		l.kanan(allows, x1, cy, ok ? Palette.JADE_DARK : Palette.INK_FAINT);
		if (l.di(x0, cy, x1, cy + BARIS)) {
			l.tip(List.of(text, Component.translatable("kultivator.hall.ui.b." + id + ".hint"),
					Component.translatable("kultivator.hall.ui.b." + id + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
		return cy + BARIS + 1;
	}

	private int jadwal(Lukis l, CompoundTag info, int x0, int y0, int x1) {
		int width = x1 - x0;
		int cy = y0 + l.kepala(Component.translatable("kultivator.hall.ui.schedule"), x0, y0, x1);
		long day = info.getLongOr("day", 0L);
		long nextArrival = info.getLongOr("next_arrival", day);
		boolean war = info.getBooleanOr("war_next", false);
		l.teks(Component.translatable("kultivator.hall.ui.today", day), x0, cy, width, l.gaya.teks);
		cy += BARIS;
		l.teks(Component.translatable(war ? "kultivator.hall.ui.next_war" : "kultivator.hall.ui.next_arrival", nextArrival), x0, cy, width,
				war ? Palette.SEAL_DARK : l.gaya.teks);
		cy += BARIS;
		l.teks(Component.translatable("kultivator.hall.ui.interval", info.getIntOr("interval", 3)), x0, cy, width, l.gaya.teksLemah);
		cy += BARIS;
		// Module B's events: the next Kompetisi, the forbidden ground and an open Ark (DESIGN 20.3.1 schedule).
		CompoundTag se = info.getCompoundOrEmpty("sectevent");
		if (se.getBooleanOr("hall", false)) {
			long time = se.getLongOr("time", 0L);
			long tourney = se.getCompoundOrEmpty("tourney").getLongOr("next", 0L);
			if (tourney > 0L) {
				l.teks(Component.translatable("kultivator.hall.ui.next_tourney", tourney), x0, cy, width, l.gaya.teks);
				cy += BARIS;
			}
			CompoundTag forbidden = se.getCompoundOrEmpty("forbidden");
			if (forbidden.getBooleanOr("open", false)) {
				l.teks(Component.translatable("kultivator.hall.ui.forbidden_open", hours(forbidden.getLongOr("until", 0L) - time)), x0, cy, width,
						Palette.SEAL_DARK);
				cy += BARIS;
			} else if (forbidden.getLongOr("next", 0L) > 0L) {
				l.teks(Component.translatable("kultivator.hall.ui.forbidden_next", forbidden.getLongOr("next", 0L)), x0, cy, width, l.gaya.teks);
				cy += BARIS;
			}
			CompoundTag ark = se.getCompoundOrEmpty("ark");
			if (ark.getBooleanOr("open", false)) {
				l.teks(Component.translatable("kultivator.hall.ui.ark_open", hours(ark.getLongOr("until", 0L) - time)), x0, cy, width,
						Palette.JADE_DARK);
				cy += BARIS;
			}
		}
		cy += 4;
		cy += l.kepala(Component.translatable("kultivator.hall.ui.away"), x0, cy, x1);
		ListTag away = info.getListOrEmpty("away");
		if (away.isEmpty()) {
			l.samar(Component.translatable("kultivator.hall.ui.away_none"), x0, cy, width);
			cy += BARIS;
		}
		for (Tag tag : away) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			long left = HallClient.ticksLeft(entry.getLongOr("left", 0L));
			String reason = entry.getStringOr("reason", "other");
			Component line = Component.translatable("kultivator.hall.ui.away_entry", HallUi.name(entry, "name"),
					Component.translatableWithFallback("kultivator.hall.away." + reason, reason), String.format(Locale.ROOT, "%.1f", left / 24000.0D));
			l.teks(line, x0, cy, width, l.gaya.teks);
			cy += BARIS;
		}
		return cy + 2;
	}

	/** "1:05" (hours:minutes of real time) for a number of ticks left, counted from when the details arrived. */
	private static String hours(long ticks) {
		long minutes = HallClient.ticksLeft(Math.max(0L, ticks)) / 1200L;
		return String.format(Locale.ROOT, "%d:%02d", minutes / 60L, minutes % 60L);
	}

	// ================================================================== input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || kanan.roda(mouseX, mouseY, scrollY) || kiri.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// both columns page together (each stops at its own end), so the keyboard reaches the left column too
		boolean dipakai = kiri.tombol(event.key());
		if (tata() != Tata.SEMPIT) {
			dipakai = kanan.tombol(event.key()) || dipakai;
		}
		return dipakai || super.keyPressed(event);
	}
}
