package dev.kultivator.client.musuh;

import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.GulirIsi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sekte › Wilayah › Pertahanan (DESIGN 25.10, 43.5; was the Pertahanan tablet of the Sect Hall scroll,
 * {@code Halls.PAGE_PERTAHANAN}): the raid schedule and its omen, the alarm, the defence score with the chance to hold an
 * unattended raid, the raid level and waves, the hurt warning and the last raid results, stacked in one scroll at text 1.0.
 * Read-only; its data is the {@code musuh} part of the {@code HallInfo} (asked for by {@code WilayahPage}).
 */
public class PertahananPage extends Page {

	private final GulirIsi gulir = new GulirIsi();

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine8.page.pertahanan");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine8.page.pertahanan.short");
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return "serbuan";
	}

	/** Only while the hall info carries a raid part (an own hall with the raid module on). */
	@Override
	public boolean tampil() {
		return HallClient.info().contains("musuh");
	}

	@Override
	protected void build() {
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		if (!HallClient.info().contains("musuh")) {
			// P5: before the first hall info (the sub shows meanwhile) no made-up numbers
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.gui43.konteks.memuat"), null, null, x, y, x + w, y + h);
			return;
		}
		CompoundTag raid = HallClient.info().getCompoundOrEmpty("musuh");
		int x0 = x + 2;
		int x1 = x + w - 7;
		int lebar = x1 - x0 - 8;
		int cy = gulir.mulai(l, x, y, x + w - 5, y + h);

		boolean omen = raid.getIntOr("state", 0) == 1;
		List<Component> teks = new ArrayList<>();
		List<Integer> warna = new ArrayList<>();
		teks.add(MusuhText.nextRaid(raid));
		warna.add(Palette.SEAL_DARK);
		teks.add(omen ? Component.translatable("kultivator.musuh.ui.omen_seen", MusuhText.sect(raid.getIntOr("sect", -1)))
				: Component.translatable("kultivator.musuh.ui.omen_none", raid.getIntOr("interval", 9)));
		warna.add(omen ? Palette.SEAL : l.gaya.teksLemah);
		teks.add(Component.translatable(raid.getBooleanOr("alarm", false) ? "kultivator.musuh.ui.alarm_on" : "kultivator.musuh.ui.alarm_off"));
		warna.add(l.gaya.teksLemah);
		teks.add(Component.translatable("kultivator.musuh.ui.defence", raid.getIntOr("score", 0), raid.getIntOr("chance", 10)));
		warna.add(Palette.JADE_DARK);
		teks.add(Component.translatable("kultivator.musuh.ui.level", raid.getIntOr("lr", 1), raid.getIntOr("waves", 3)));
		warna.add(l.gaya.teks);
		float tinggi = 0.0F;
		for (Component t : teks) {
			tinggi += Brush.wrapHeight(font, t, lebar, 1.0F) + 1.0F;
		}
		l.kartu(x0, cy, x1, cy + Math.round(tinggi) + 6, 0.0F, false);
		float ty = cy + 3.0F;
		for (int i = 0; i < teks.size(); i++) {
			ty += l.bungkus(teks.get(i), x0 + 4, ty, lebar, warna.get(i)) + 1.0F;
		}
		cy += Math.round(tinggi) + 10;
		if (raid.getBooleanOr("hurt", false)) {
			cy += Math.round(l.bungkus(Brush.bold(Component.translatable("kultivator.musuh.ui.hurt")), x0, cy, x1 - x0, Palette.SEAL)) + 4;
		}

		cy += l.kepala(Component.translatable("kultivator.musuh.ui.raid_log"), x0, cy, x1);
		ListTag log = raid.getListOrEmpty("log");
		if (log.isEmpty()) {
			cy += Math.round(l.bungkus(Component.translatable("kultivator.musuh.ui.no_raids"), x0 + 2, cy, x1 - x0 - 4, l.gaya.teksSamar)) + 2;
		}
		for (int i = log.size() - 1; i >= 0; i--) {
			CompoundTag entry = log.getCompoundOrEmpty(i);
			int res = entry.getIntOr("res", 0);
			Component line = Component.translatable("kultivator.musuh.ui.raid_entry", entry.getLongOr("day", 0L), MusuhText.sect(entry.getIntOr("sect", -1)),
					MusuhText.result(res), entry.getIntOr("waves", 3), entry.getIntOr("meter", 0));
			int rowH = Math.round(Brush.wrapHeight(font, line, x1 - x0 - 10, 1.0F)) + 4;
			l.g.fill(x0, cy, x1, cy + rowH, Palette.argb(0x40, i % 2 == 0 ? Palette.BAMBOO : Palette.BAMBOO_ALT));
			l.g.fill(x0, cy, x0 + 2, cy + rowH, MusuhText.resultColor(res));
			l.bungkus(line, x0 + 6, cy + 2, x1 - x0 - 10, MusuhText.resultColor(res));
			cy += rowH + 1;
		}
		cy += 4;
		cy += Math.round(l.bungkus(Component.translatable("kultivator.musuh.raid.source"), x0, cy, x1 - x0, l.gaya.teksSamar)) + 2;
		gulir.selesai(l, cy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event) || gulir.tombol(event);
	}
}
