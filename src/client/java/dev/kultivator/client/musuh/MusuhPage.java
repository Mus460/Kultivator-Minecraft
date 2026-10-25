package dev.kultivator.client.musuh;

import dev.kultivator.client.dunia.DuniaClient;
import dev.kultivator.client.dunia.kota.SainganScreen;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Kronik;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dunia › Musuh (DESIGN 25.10, 43.5, G54): every list of enemies and threats in one page.
 * <ul>
 * <li><b>Serbuan</b>: the next raid ("Serbuan berikutnya: hari 31 · Sekte Bulan Darah"), the ambush cooldown, the rogues
 * slain, and a talisman to the sect's own raid page (Sekte › Wilayah › Pertahanan holds the full schedule and the defence
 * actions, DESIGN 33.6 R7; it needs an own hall).</li>
 * <li><b>Saingan</b>: the rival groups that hunt the player ({@link SainganScreen}, the old Dunia card screen).</li>
 * <li><b>Riwayat</b>: the last encounters (ambushes, camps, raids) in the kit's chronicle, newest first under day headings.</li>
 * </ul>
 * Read-only; the data comes from {@link MusuhClient} (asked for while shown, 43.0.12) and, for Saingan, from the town module.
 * Text at 1.0 (43.0.5).
 */
public class MusuhPage extends Page {

	public static final String SUB_SERBUAN = "serbuan";
	public static final String SUB_SAINGAN = "saingan";
	public static final String SUB_RIWAYAT = "riwayat";
	private static final String M = "kultivator.gui43.m13.";

	private final SainganScreen saingan = new SainganScreen();
	private final Kronik riwayat = new Kronik(null);
	private String sub = SUB_SERBUAN;
	private boolean shown;

	public MusuhPage() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.dunia.musuh");
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return SUB_SAINGAN.equals(sub) ? "saingan" : "kultivator_liar";
	}

	@Override
	public List<Sub> subs() {
		return List.of(new Sub(SUB_SERBUAN, Component.translatable("kultivator.gui43.sub.musuh.serbuan")),
				new Sub(SUB_SAINGAN, Component.translatable("kultivator.gui43.sub.musuh.saingan")),
				new Sub(SUB_RIWAYAT, Component.translatable("kultivator.gui43.sub.musuh.riwayat")));
	}

	@Override
	public void showSub(String id) {
		String next = SUB_SAINGAN.equals(id) || SUB_RIWAYAT.equals(id) ? id : SUB_SERBUAN;
		if (!next.equals(sub)) {
			sub = next;
			riwayat.mulaiUlang(net.minecraft.util.Util.getMillis());
		}
		if (shown && SUB_SAINGAN.equals(sub)) {
			SainganScreen.request();
		}
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		shown = true;
		send(Actions.MUSUH, 0, "sync");
		if (SUB_SAINGAN.equals(sub)) {
			SainganScreen.request();
		}
	}

	@Override
	public void onHide() {
		shown = false;
	}

	@Override
	protected void build() {
		// Immediate page: everything is drawn and hit-tested in lukis().
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		if (SUB_SAINGAN.equals(sub)) {
			saingan.gambar(l, x, y, x + w, y + h);
			return;
		}
		CompoundTag data = MusuhClient.data();
		boolean tooBig = DuniaClient.tooBig(Payloads.DuniaSync.CH_MUSUH);
		int bottom = tooBig ? y + h - 11 : y + h;
		if (tooBig) {
			l.teks(Component.translatable("kultivator.sect_fix_feat.too_big"), x + 2, y + h - 9, w - 4, Palette.SEAL_DARK);
		}
		if (data.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable("kultivator.musuh.ui.loading"), null, null, x, y, x + w, bottom);
			return;
		}
		if (SUB_RIWAYAT.equals(sub)) {
			riwayat.gambar(l, "musuh.riwayat", entries(data.getListOrEmpty("log")), data.getLongOr("today", -1L), x, y, x + w, bottom);
			return;
		}
		serbuan(l, data, bottom);
	}

	/** The schedule card, the defence talisman and the source line. */
	private void serbuan(Lukis l, CompoundTag data, int bottom) {
		CompoundTag raid = data.getCompoundOrEmpty("raid");
		int room = w - 12;
		int cardTop = y + 1;
		int cardBottom = Math.min(bottom - 20, cardTop + 52);
		l.kartu(x, cardTop, x + w, cardBottom, 0.0F, false);
		int ty = cardTop + 4;
		ty += Math.round(l.bungkus(MusuhText.nextRaid(raid), x + 6, ty, room, Palette.SEAL_DARK, 18.0F)) + 2;
		long cd = data.getLongOr("ambush_cd", 0L);
		Component ambush = !data.getBooleanOr("ambush_on", true) ? Component.translatable("kultivator.musuh.ui.ambush_off")
				: cd <= 0L ? Component.translatable("kultivator.musuh.ui.ambush_ready")
				: Component.translatable("kultivator.musuh.ui.ambush_cd", MusuhText.days(cd));
		l.teks(ambush, x + 6, ty, room, l.gaya.teks);
		ty += 10;
		l.teks(Component.translatable("kultivator.musuh.ui.kills", data.getIntOr("kills", 0)), x + 6, ty, room, l.gaya.teksLemah);
		// The sect's raid page (schedule, omen, defence score, raid log) lives in Sekte › Wilayah; this talisman leads there.
		Component label = Component.translatable("kultivator.uxpages.musuh.to_defence");
		int bw = TalismanButton.lebar(l.font, label, 96, Math.max(96, w / 2));
		int by = cardBottom + 4;
		boolean hall = raid.getBooleanOr("has_hall", false);
		TalismanButton.gambar(l, "musuh.pertahanan", x + w - bw, by, bw, 14, label, hall, Component.translatable("kultivator.musuh.info.no_hall"),
				() -> tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_WILAYAH).sub("pertahanan")));
		if (hall && l.di(x + w - bw, by, x + w, by + 14)) {
			l.tip(List.of(Component.translatable(M + "serbuan.ke_pertahanan"), Component.translatable("kultivator.uxpages.musuh.to_defence.tip")));
		}
		int sy = by + 18;
		if (sy + 9 <= bottom) {
			l.bungkus(Component.translatable("kultivator.musuh.ui.source"), x + 2, sy, w - 4, l.gaya.teksSamar, bottom - sy);
		}
	}

	/** The encounter log as chronicle entries (oldest first, as the server keeps it). */
	private static List<Kronik.Entri> entries(ListTag log) {
		List<Kronik.Entri> out = new ArrayList<>(log.size());
		for (int i = 0; i < log.size(); i++) {
			CompoundTag entry = log.getCompoundOrEmpty(i);
			boolean won = entry.getBooleanOr("won", false);
			int kind = Math.max(0, Math.min(2, entry.getIntOr("kind", 0)));
			Component text = Component.translatable(M + "riwayat.baris", Component.translatable("kultivator.musuh.kind." + kind), entry.getIntOr("n", 0),
					Component.translatable(won ? "kultivator.musuh.ui.won" : "kultivator.musuh.ui.lost"));
			out.add(new Kronik.Entri(text, entry.getLongOr("day", -1L), String.valueOf(kind), won ? Palette.JADE : Palette.SEAL));
		}
		return out;
	}
}
