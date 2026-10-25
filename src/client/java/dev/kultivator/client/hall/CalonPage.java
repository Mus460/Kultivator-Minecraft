package dev.kultivator.client.hall;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sekte › Anggota › Calon (DESIGN 20.3.3, 43.5): one card per candidate at the gate ({@link CandidateCard}) with the
 * talismans "Uji (1 Pecahan Kecubung)", "Terima" and "Undang pergi" (confirmed with the seal, 43.7), the next acceptance
 * day, the Hanya Monster coin and, on a war day, "Mulai Perang" (module B's Perang Seratus Dinasti). The cards sit in one
 * kit list (wheel, drag bar, ↑↓ keys), one column when narrow, two from 400 px. A visitor of another master's hall sees the
 * cards read-only. Data: the last {@code HallInfo} ({@link HallClient}), asked for by {@link AnggotaPage}.
 */
public class CalonPage extends Page {

	private static final String K = "kultivator.gui43.m06.";

	private final DaftarGulir daftar = new DaftarGulir();
	private final JadeCoin monster;

	public CalonPage() {
		monster = new JadeCoin(0, 0, 100, 14, () -> Component.translatable("kultivator.hall.ui.monster"),
				() -> info().getBooleanOr("monster", false));
		monster.onPress(() -> HallClient.send(Halls.OP_MONSTER, ""));
		monster.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.monster"), Component.translatable("kultivator.hall.ui.monster.tip"),
				Component.translatable("kultivator.hall.ui.monster.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.calon");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.calon.short");
	}

	@Override
	public int glyph() {
		return Glyphs.STATUS;
	}

	@Override
	public String helpTopic() {
		return "calon";
	}

	private static CompoundTag info() {
		return HallClient.info();
	}

	private static boolean readonly() {
		return info().getBooleanOr("readonly", false);
	}

	private static List<Integer> ids() {
		List<Integer> ids = new ArrayList<>();
		for (Tag tag : info().getListOrEmpty("candidates")) {
			if (tag instanceof CompoundTag entry) {
				ids.add(entry.getIntOr("id", -1));
			}
		}
		return ids;
	}

	private static @Nullable CompoundTag entry(int id) {
		for (Tag tag : info().getListOrEmpty("candidates")) {
			if (tag instanceof CompoundTag entry && entry.getIntOr("id", -1) == id) {
				return entry;
			}
		}
		return null;
	}

	/** True while today's youths wait for the war to start (module B's part of the details, when it sent one). */
	private static boolean warWaiting() {
		CompoundTag war = info().getCompoundOrEmpty("sectevent").getCompoundOrEmpty("war");
		return war.isEmpty() || war.getBooleanOr("today", true) && war.getIntOr("state", 0) == 0;
	}

	@Override
	protected void build() {
		// immediate page: everything is drawn in lukis()
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		if (HallClient.version() == 0L) {
			l.tengah(Component.translatable("kultivator.gui43.konteks.memuat"), x + w / 2.0F, y + h / 2.0F - 4.0F, w - 8, l.gaya.teksSamar);
			return;
		}
		CompoundTag info = info();
		boolean ro = readonly();
		int bh = tata() == Tata.SEMPIT ? 12 : 14;
		int top = y;
		// the next arrival, or today's war (it replaces the arrival; started here or on the Arena)
		boolean warToday = info.getBooleanOr("war_today", false);
		long day = info.getLongOr("day", 0L);
		if (warToday) {
			l.teks(Brush.bold(Component.translatable("kultivator.hall.ui.war_today")), x + 2, top + 1, w - 4, Palette.SEAL_DARK);
			if (l.di(x, top, x + w, top + 10)) {
				l.tip(List.of(Component.translatable("kultivator.hall.ui.war_today"), Component.translatable("kultivator.hall.ui.war_today.tip"),
						Component.translatable("kultivator.hall.ui.war.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
		} else {
			l.teks(Component.translatable(info.getBooleanOr("war_next", false) ? "kultivator.hall.ui.next_war" : "kultivator.hall.ui.next_arrival",
					info.getLongOr("next_arrival", day)), x + 2, top + 1, w - 4, l.gaya.teks);
		}
		top += 12;
		if (!ro) {
			Component monsterLabel = Component.translatable("kultivator.hall.ui.monster");
			monster.x = x + 2;
			monster.y = top;
			monster.w = Math.min(w / 2 - 4, l.font.width(monsterLabel) + bh + 10);
			monster.h = bh;
			l.widget("monster", monster);
			if (warToday) {
				Component label = Component.translatable("kultivator.hall.ui.war_start");
				int bw = Math.min(w / 2 - 4, TalismanButton.lebar(l.font, label, 60, 150));
				boolean waiting = warWaiting();
				if (TalismanButton.gambar(l, "perang", x + w - bw - 2, top, bw, bh, label, waiting, Component.translatable(K + "calon.perang_jalan"),
						() -> send(Actions.SECT_EVENT, 0, "war_start"), waiting, false)) {
					l.tip(List.of(label, Component.translatable("kultivator.hall.ui.war_today.tip"),
							Component.translatable("kultivator.hall.ui.war.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
				}
			}
			top += bh + 3;
		}
		l.garis(x, x + w, top);
		top += 3;
		List<Integer> ids = ids();
		if (ids.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(info.getBooleanOr("dormant", false)
					? "kultivator.hall.ui.dormant_candidates" : "kultivator.hall.ui.no_candidates", info.getLongOr("next_arrival", day)), null, null,
					x, top, x + w, y + h);
			return;
		}
		int bhKartu = bh;
		daftar.gambarKisi(l, "calon", x, top, x + w, y + h, ids.size(), 200, CandidateCard.tinggi(ro),
				(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> kartu(lk, ids.get(i), rx0, ry0, rx1, ry1, sorot, pilih, ro, bhKartu), null, null);
	}

	/** One candidate: the card, then (owner only) its three talismans in the card's bottom row. */
	private void kartu(Lukis l, int id, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih, boolean ro, int bh) {
		CompoundTag e = entry(id);
		if (e == null) {
			return;
		}
		CandidateCard.gambar(l, e, x0, y0, x1, y1, sorot, pilih);
		if (ro) {
			return;
		}
		boolean tested = e.getBooleanOr("tested", false);
		boolean testing = e.getBooleanOr("testing", false);
		int by = y1 - bh - 3;
		int third = (x1 - x0 - 10) / 3;
		Component uji = Component.translatable("kultivator.hall.ui.test");
		if (TalismanButton.gambar(l, "uji:" + id, x0 + 3, by, third, bh, uji, !tested && !testing,
				Component.translatable(testing ? "kultivator.hall.ui.testing" : K + "calon.sudah_uji"), () -> HallClient.send(Halls.OP_TEST,
						String.valueOf(id)))) {
			l.tip(List.of(Component.translatable("kultivator.hall.ui.test.full"), Component.translatable("kultivator.hall.ui.test.tip"),
					Component.translatable("kultivator.hall.ui.test.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
		boolean mendesak = e.getIntOr("verdict", -1) == 2 || e.getBooleanOr("token", false) || e.getBooleanOr("champion", false);
		if (TalismanButton.gambar(l, "terima:" + id, x0 + 5 + third, by, third, bh, Component.translatable("kultivator.hall.ui.accept"), !testing,
				Component.translatable("kultivator.hall.ui.testing"), () -> HallClient.send(Halls.OP_ACCEPT, String.valueOf(id)), mendesak && !testing,
				false)) {
			l.tip(acceptTip(e));
		}
		Component nama = HallUi.name(e, "name");
		if (TalismanButton.gambar(l, "pergi:" + id, x0 + 7 + third * 2, by, x1 - x0 - 10 - third * 2, bh, Component.translatable(
				"kultivator.hall.ui.dismiss"), true, null, () -> konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(K + "calon.usir",
						nama), () -> HallClient.send(Halls.OP_DISMISS, String.valueOf(id))))) {
			l.tip(List.of(Component.translatable("kultivator.hall.ui.dismiss"), Component.translatable("kultivator.hall.ui.dismiss.tip")));
		}
	}

	/** What accepting this candidate means: the rank it joins at, and the servant fee. */
	private static List<Component> acceptTip(CompoundTag e) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.ui.accept"));
		if (e.getBooleanOr("token", false) || e.getBooleanOr("champion", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.accept.token").withStyle(ChatFormatting.GOLD));
		} else if (!e.getBooleanOr("tested", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.accept.blind").withStyle(ChatFormatting.GRAY));
		} else {
			int verdict = e.getIntOr("verdict", 0);
			lines.add(Component.translatable("kultivator.hall.ui.accept." + verdict, 100).withStyle(verdict == 0 ? ChatFormatting.RED
					: verdict == 1 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
		}
		lines.add(Component.translatable("kultivator.hall.ui.accept.cap", info().getIntOr("alive", 0), info().getIntOr("cap", 5))
				.withStyle(ChatFormatting.DARK_AQUA));
		if (e.getBooleanOr("tested", false) && !e.getBooleanOr("token", false) && !e.getBooleanOr("champion", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.verdict.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			if (e.getIntOr("verdict", 0) == 0) {
				lines.add(Component.translatable("kultivator.hall.ui.servant.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		}
		if (e.getBooleanOr("gated", false)) {
			lines.addAll(CandidateCard.gatedTip());
		}
		return lines;
	}
}
