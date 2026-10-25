package dev.kultivator.client.desa;

import dev.kultivator.client.Keys;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.wilayah.GulirIsi;
import dev.kultivator.network.Actions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Sekte › Wilayah › Desa &amp; Pakta (DESIGN 25.10, 43.5; was the Desa tablet of the Sect Hall scroll, {@code Halls.PAGE_DESA}):
 * the vassal villages as a list (and the Penjarahan log as its last row) beside the chosen village's card (loyalty bar,
 * villagers, next tribute, the owed buffer, the open request with "Ambil Surat", the Pelindung Desa with assign / release,
 * the last raid). Narrow boxes (43.3.1 SEMPIT) show the list, and the card in its place with the back control. Reads the
 * {@code desa} part of the hall info (filled by {@code desa.Desa}); a sect mate sees it read-only. Text 1.0.
 */
public class HallDesaPage extends Page {

	private static final int LOG = -1;
	private static final int BARIS = 14;
	private static int chosen;
	/** SEMPIT only: the card (or log) is shown in place of the list. */
	private boolean rinci;
	private final GulirIsi gulirDaftar = new GulirIsi();
	private final GulirIsi gulirKartu = new GulirIsi();

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine8.page.desa");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine8.page.desa.short");
	}

	@Override
	public int glyph() {
		return Glyphs.HALL;
	}

	@Override
	public String helpTopic() {
		return "desa";
	}

	@Override
	public boolean tampil() {
		return HallClient.info().getBooleanOr("has", false) && desa().getBooleanOr("on", false);
	}

	private static CompoundTag desa() {
		return HallClient.info().getCompoundOrEmpty("desa");
	}

	private static ListTag villages() {
		return desa().getListOrEmpty("villages");
	}

	private static boolean own() {
		return desa().getBooleanOr("own", false) && !HallClient.info().getBooleanOr("readonly", false);
	}

	private static CompoundTag current() {
		ListTag list = villages();
		return chosen >= 0 && chosen < list.size() ? list.getCompoundOrEmpty(chosen) : new CompoundTag();
	}

	@Override
	public boolean rinci() {
		return rinci && tata() == Tata.SEMPIT;
	}

	@Override
	public boolean mundur() {
		if (rinci()) {
			rinci = false;
			return true;
		}
		return false;
	}

	@Override
	protected void build() {
		if (tata() != Tata.SEMPIT) {
			rinci = false;
		}
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		CompoundTag desa = desa();
		ListTag list = villages();
		if (chosen >= list.size()) {
			chosen = list.isEmpty() ? LOG : 0;
		}
		int top = y + l.kepala(Component.translatable(DesaCards.P + "hall_title", desa.getIntOr("count", 0), desa.getIntOr("max", 1)), x + 2, y, x + w - 2);
		if (tata() == Tata.SEMPIT) {
			if (rinci) {
				kartu(l, desa, list, x + 2, top, x + w - 2, y + h);
			} else {
				daftar(l, list, x + 2, top, x + w - 2, y + h);
			}
			return;
		}
		int colW = Math.max(80, Math.min(120, Math.round(w * 0.3F)));
		daftar(l, list, x + 2, top, x + 2 + colW, y + h);
		l.g.fill(x + colW + 6, top, x + colW + 7, y + h, Palette.argb(0x60, Palette.GOLD));
		kartu(l, desa, list, x + colW + 12, top, x + w - 2, y + h);
	}

	/** The villages, then the Penjarahan log, one row each. */
	private void daftar(Lukis l, ListTag list, int x0, int y0, int x1, int y1) {
		boolean sempit = tata() == Tata.SEMPIT;
		int ry = gulirDaftar.mulai(l, x0, y0, x1 - 5, y1);
		for (int i = 0; i <= list.size(); i++) {
			int index = i < list.size() ? i : LOG;
			CompoundTag v = index >= 0 ? list.getCompoundOrEmpty(index) : null;
			Component nama = v != null ? Component.literal(v.getStringOr("name", "?")) : Component.translatable(DesaCards.P + "log");
			if (index == LOG) {
				ry += 3;
			}
			boolean sorot = l.di(x0, ry, x1 - 5, ry + BARIS);
			l.kartu(x0, ry, x1 - 5, ry + BARIS - 1, sorot ? 1.0F : 0.0F, !sempit && chosen == index);
			l.teks(nama, x0 + 5, ry + 3, x1 - x0 - (sempit ? 24 : 12), index == LOG ? l.gaya.teksLemah : l.gaya.teks);
			if (sempit) {
				l.kanan(Component.literal("›"), x1 - 9, ry + 3, l.gaya.aksen);
			}
			l.zona("desa:" + index, x0, ry, x1 - 5, ry + BARIS - 1, () -> pilih(index), true, null, true);
			if (sorot && v != null) {
				l.tip(DesaCards.headline(v));
			}
			ry += BARIS;
		}
		gulirDaftar.selesai(l, ry);
	}

	private void pilih(int index) {
		if (chosen != index) {
			gulirKartu.atas();
		}
		chosen = index;
		rinci = true;
	}

	/** The chosen village's card, or the log; the two talismans at the foot of a village card. */
	private void kartu(Lukis l, CompoundTag desa, ListTag list, int x0, int y0, int x1, int y1) {
		boolean log = chosen == LOG;
		int kaki = log || list.isEmpty() ? 0 : 18;
		int ty = gulirKartu.mulai(l, x0, y0, x1 - 5, y1 - kaki);
		int tx = x0 + 4;
		int tw = x1 - 5 - tx - 4;
		if (log) {
			ty += Math.round(l.bungkus(Brush.bold(Component.translatable(DesaCards.P + "log_title")), x0, ty, x1 - 5 - x0, l.gaya.aksen)) + 3;
			ListTag entries = desa.getListOrEmpty("log");
			if (entries.isEmpty()) {
				ty += Math.round(l.bungkus(Component.translatable(DesaCards.P + "log_empty"), x0, ty, x1 - 5 - x0, l.gaya.teksSamar));
			}
			for (int i = entries.size() - 1; i >= 0; i--) {
				CompoundTag entry = entries.getCompoundOrEmpty(i);
				int color = entry.getIntOr("result", 0) == 1 ? DesaCards.LOYAL : DesaCards.WARN;
				ty += Math.round(l.bungkus(DesaCards.logLine(entry), x0, ty, x1 - 5 - x0, color)) + 2;
			}
			gulirKartu.selesai(l, ty);
			return;
		}
		if (list.isEmpty()) {
			gulirKartu.selesai(l, ty);
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(DesaCards.P + "none", desa.getIntOr("range", 256)), null, null,
					x0, y0, x1, y1);
			return;
		}
		CompoundTag v = current();
		long today = desa.getLongOr("day", 0L);
		int kartuTop = ty;
		ty += 3;
		l.judul(Component.literal(v.getStringOr("name", "?")), tx, ty, tw - 12, l.gaya.aksen);
		DesaCards.seal(l.g, font, v, x1 - 13.0F, ty + 5.0F);
		ty += 16;
		ty += Math.round(l.bungkus(DesaCards.headline(v), tx, ty, tw, l.gaya.teks)) + 2;
		if (v.getBooleanOr("pledged", false)) {
			DesaCards.loyaltyBar(l.g, tx, ty, tw, v.getIntOr("loyalty", 0));
			ty += 7;
			ty += Math.round(l.bungkus(Component.translatable(DesaCards.P + "tribute_owed", v.getLongOr("tribute_in", 0L), v.getIntOr("owed", 0)), tx, ty,
					tw, l.gaya.teksLemah)) + 2;
			Component request = DesaCards.request(v);
			ty += Math.round(l.bungkus(request == null ? Component.translatable(DesaCards.P + "no_request") : request, tx, ty, tw,
					request == null ? l.gaya.teksSamar : Palette.GOLD_DARK)) + 2;
			ty += Math.round(l.bungkus(DesaCards.guard(v), tx, ty, tw, l.gaya.teksLemah)) + 2;
			Component raid = DesaCards.lastRaid(v, today);
			if (raid != null) {
				ty += Math.round(l.bungkus(raid, tx, ty, tw, v.getIntOr("last_result", 0) == 1 ? DesaCards.LOYAL : DesaCards.WARN)) + 2;
			}
			if (v.getBooleanOr("siege", false)) {
				ty += Math.round(l.bungkus(Brush.bold(Component.translatable(DesaCards.P + "siege")), tx, ty, tw, DesaCards.WARN)) + 2;
			}
		} else {
			ty += Math.round(l.bungkus(Component.translatable(DesaCards.P + "waiting_long"), tx, ty, tw, l.gaya.teksLemah)) + 2;
		}
		Brush.frame(l.g, x0, kartuTop, x1 - 5, ty + 2, Palette.argb(0x90, Palette.GOLD));
		gulirKartu.selesai(l, ty + 4);

		// the two talismans, fixed at the foot of the card
		int by = y1 - 15;
		int bw = Math.max(TalismanButton.MIN_W, (x1 - x0 - 6) / 2);
		boolean bisa = Keys.connected() && own();
		String req = v.getStringOr("req", "");
		boolean surat = bisa && !req.isEmpty();
		if (TalismanButton.gambar(l, "surat", x0, by, bw, 14, Component.translatable(DesaCards.P + "letter"), surat, null,
				() -> send(Actions.DESA, 0, "letter:" + current().getIntOr("id", 0)))) {
			l.tip(Component.translatable(DesaCards.P + "letter.tip"));
		}
		boolean tanpaPelindung = v.getStringOr("guard", "").isEmpty();
		boolean pelindung = bisa && v.getBooleanOr("pledged", false);
		if (TalismanButton.gambar(l, "pelindung", x0 + bw + 6, by, x1 - x0 - bw - 6, 14,
				Component.translatable(tanpaPelindung ? DesaCards.P + "assign" : DesaCards.P + "release"), pelindung, null,
				() -> send(Actions.DESA, 0, (current().getStringOr("guard", "").isEmpty() ? "guard:" : "release:") + current().getIntOr("id", 0)))) {
			l.tip(Component.translatable(tanpaPelindung ? DesaCards.P + "assign.tip" : DesaCards.P + "release.tip"));
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || gulirKartu.roda(mouseX, mouseY, scrollY)
				|| gulirDaftar.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event) || gulirKartu.tombol(event);
	}
}
