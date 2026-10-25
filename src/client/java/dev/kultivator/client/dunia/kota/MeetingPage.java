package dev.kultivator.client.dunia.kota;

import static dev.kultivator.client.dunia.kota.KotaJembatan.M;
import static dev.kultivator.client.dunia.kota.KotaJembatan.kirim;
import static dev.kultivator.client.dunia.kota.KotaJembatan.semua;
import static dev.kultivator.client.dunia.kota.KotaJembatan.teks;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarPilihan;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.sekte.wilayah.GulirIsi;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import net.minecraft.ChatFormatting;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The meeting with a wandering cultivator (DESIGN 34.5.2, 43.8.4), the Di Sini page of a wanderer: the wanderer's card
 * (name, Dao, realm, temperament, sword style) and the numbered choices of the single {@link DaftarPilihan} (43.17 K3: the
 * number keys pick them under the Di Sini tablet). Bertukar Pengetahuan first takes an offering (a jade slip, a herb, a
 * pill or 5 % of the bar), then the Diskusi Dao: three rounds of Gerak / Diam / Selaras (Gerak beats Diam, Diam beats
 * Selaras, Selaras beats Gerak) with a hint from the wanderer's Dao. Tanding, Duel Pedang, Tantang (to the death: a medium
 * confirm seal), Ajak and Pamit end the talk and close the Gulungan so the world can be seen, as the old screen did.
 */
final class MeetingPage implements KotaJembatan.Isi {

	static final String[] OFFERS = {"giok", "herba", "pil", "pengalaman"};
	static final String[] STANCES = {"gerak", "diam", "selaras"};
	static final String[] TEMPERS = {"ramah", "angkuh", "licik"};
	/** The duel styles by ordinal ({@code DuelStyle}: AGRESIF, BERTAHAN, PENIPU, SANTO_PEDANG). */
	static final String[] STYLES = {"agresif", "bertahan", "penipu", "santo_pedang"};
	private static final int BARIS = 17;

	private final GulirIsi gulir = new GulirIsi();

	@Override
	public String id() {
		return "pertemuan";
	}

	@Override
	public Component nama() {
		return Component.translatable("kultivator.kota.tab.pertemuan.short");
	}

	private static boolean discussing() {
		return semua().getBooleanOr("discussing", false);
	}

	private static Component gaya() {
		return Component.translatable("kultivator.kota.gaya_pedang." + STYLES[Math.max(0, Math.min(3, semua().getIntOr("duel_style", 1)))]);
	}

	@Override
	public void lukis(KotaJembatan.Halaman p, Lukis l, int x0, int y0, int x1, int y1) {
		CompoundTag data = semua();
		int r = x1 - 5;
		int lebar = r - x0;
		int y = gulir.mulai(l, x0, y0, r, y1);
		y = kartu(l, data, x0, y, r) + 4;
		if (discussing()) {
			int round = data.getIntOr("round", 0);
			Dao dao = Dao.byOrdinal(data.getIntOr("dao", -1));
			String daoId = dao == null ? "sword" : dao.id();
			int lean = Math.max(0, Math.min(2, data.getIntOr("lean", 0)));
			Component hint = Component.translatable("kultivator.kota.diskusi." + daoId + "." + (lean * 2 + data.getIntOr("hint", 0)));
			l.teks(Brush.bold(Component.translatable("kultivator.kota.diskusi.ronde", round + 1, 3, data.getIntOr("wins", 0))), x0, y, lebar,
					Palette.SEAL_DARK);
			y += 11;
			y += Math.round(l.bungkus(Component.literal("“").append(hint).append("”").withStyle(ChatFormatting.ITALIC), x0 + 4, y, lebar - 4,
					l.gaya.teks)) + 3;
		} else if (data.getBooleanOr("discussed", false)) {
			y += Math.round(l.bungkus(hasil(data), x0, y, lebar, "lore".equals(data.getStringOr("reward", "lore")) ? l.gaya.teksLemah : Palette.JADE_DARK))
					+ 3;
		} else {
			y += Math.round(l.bungkus(Component.translatable("kultivator.kota.diskusi.ajak"), x0, y, lebar, l.gaya.teksSamar)) + 3;
		}
		int last = data.getIntOr("last", 9);
		if (last != 9 && (discussing() || data.getBooleanOr("discussed", false))) {
			Component lastLine = Component.translatable("kultivator.kota.diskusi.terakhir." + (last > 0 ? "menang" : last == 0 ? "seri" : "kalah"),
					Component.translatable("kultivator.kota.diskusi.sikap." + STANCES[Math.max(0, Math.min(2, data.getIntOr("last_theirs", 0)))]));
			l.teks(lastLine, x0, y, lebar, last > 0 ? Palette.JADE_DARK : last == 0 ? l.gaya.teksLemah : Palette.SEAL_DARK);
			y += 12;
		}
		List<DaftarPilihan.Pilihan> pilihan = new ArrayList<>();
		List<Component> tips = new ArrayList<>();
		List<Runnable> aksi = new ArrayList<>();
		pilih(p, data, pilihan, tips, aksi);
		int tinggi = DaftarPilihan.tinggi(pilihan.size(), 1, BARIS - 2);
		DaftarPilihan.gambar(l, "temu", pilihan, x0, y, r, y + tinggi, false, c -> aksi.get(pilihan.indexOf(c)).run());
		for (int i = 0; i < pilihan.size(); i++) {
			int py = y + i * BARIS;
			if (pilihan.get(i).kunci() == null && l.di(x0, py, r, py + BARIS - 2)) {
				l.tip(tips.get(i));
			}
		}
		gulir.selesai(l, y + tinggi + 2);
	}

	/** The wanderer's card: Dao disc, name, Dao and realm, temperament, discussions left and sword style. Returns the y below. */
	private static int kartu(Lukis l, CompoundTag data, int x0, int y, int x1) {
		Dao dao = Dao.byOrdinal(data.getIntOr("dao", -1));
		int daoColor = dao == null ? 0x8090A0 : dao.color();
		l.teks(Brush.bold(Component.translatable("kultivator.kota.pertemuan.judul", teks(data, "name"))), x0, y, x1 - x0, l.gaya.aksen);
		y += 12;
		int top = y;
		Brush.disc(l.g, x0 + 12.0F, top + 13.0F, 11.0F, Palette.argb(0x60, daoColor));
		Brush.halo(l.g, x0 + 12.0F, top + 13.0F, 9.0F, 3.0F, daoColor, 0.5F);
		if (dao != null) {
			Glyphs.trigram(l.g, x0 + 12.0F, top + 13.0F, 11.0F, 1.6F, 3.0F, Glyphs.trigramOf(dao), Palette.argb(0xF0, Palette.INK));
		}
		int tx = x0 + 28;
		Realm realm = Realm.byOrdinal(data.getIntOr("realm", 1));
		l.teks(Component.translatable("kultivator.kota.pertemuan.dao", dao == null ? Component.translatable("kultivator.kota.pertemuan.tanpa_dao")
				: dao.displayName(), realm.displayName(), data.getIntOr("stage", 0) + 1), tx, y, x1 - tx, l.gaya.teks);
		y += 10;
		int temper = Math.max(0, Math.min(2, data.getIntOr("temper", 0)));
		y += Math.round(l.bungkus(Component.translatable("kultivator.kota.pertemuan.watak", Component.translatable("kultivator.kota.watak." + temper),
				Component.translatable("kultivator.kota.watak.isi." + TEMPERS[temper])), tx, y, x1 - tx, l.gaya.teksLemah));
		y += Math.round(l.bungkus(Component.translatable("kultivator.kota.pertemuan.diskusi_gaya", data.getIntOr("disc_left", 0), gaya()), tx, y, x1 - tx,
				Palette.GOLD_DARK));
		return Math.max(y, top + 26);
	}

	private static Component hasil(CompoundTag data) {
		Dao dao = Dao.byOrdinal(data.getIntOr("dao", -1));
		return switch (data.getStringOr("reward", "lore")) {
			case "paham" -> Component.translatable("kultivator.kota.diskusi.hasil.paham", String.format(java.util.Locale.ROOT, "%.1f",
					data.getDoubleOr("reward_n", 0.0D)), dao == null ? Component.empty() : dao.displayName());
			case "catatan" -> Component.translatable("kultivator.kota.diskusi.hasil.catatan");
			case "jurus" -> Component.translatable("kultivator.kota.diskusi.hasil.jurus", teks(data, "taught"), dev.kultivator.dunia.kota.Pengembara.TEACH_POINTS);
			default -> Component.translatable("kultivator.kota.diskusi.hasil.lore", Component.translatable("kultivator.kota.diskusi.lore."
					+ data.getIntOr("lore", 0)));
		};
	}

	/** The numbered choices now: the three stances during a discussion, else the offerings (when allowed) and the five acts. */
	private static void pilih(KotaJembatan.Halaman p, CompoundTag data, List<DaftarPilihan.Pilihan> pilihan, List<Component> tips, List<Runnable> aksi) {
		if (discussing()) {
			for (int i = 0; i < STANCES.length; i++) {
				int index = i;
				tambah(pilihan, tips, aksi, Component.translatable("kultivator.kota.diskusi.sikap." + STANCES[i]), null,
						Component.translatable("kultivator.kota.diskusi.tip_sikap." + STANCES[i]), () -> kirim("diskusi:" + index));
			}
			return;
		}
		if (!data.getBooleanOr("discussed", false) && data.getIntOr("disc_left", 0) > 0) {
			for (int i = 0; i < OFFERS.length; i++) {
				int index = i;
				Component kunci = data.getBooleanOr("has_" + OFFERS[i], false) ? null : Component.translatable(M + "temu.tak_punya");
				tambah(pilihan, tips, aksi, Component.translatable(M + "temu.beri", Component.translatable("kultivator.kota.diskusi.beri." + OFFERS[i])), kunci,
						Component.translatable("kultivator.kota.diskusi.tip_beri." + OFFERS[i]), () -> kirim("tukar:" + index));
			}
		}
		Component sudah = data.getBooleanOr("sparred", false) ? Component.translatable(M + "temu.sudah_tanding") : null;
		tambah(pilihan, tips, aksi, Component.translatable("kultivator.kota.pertemuan.tanding"), sudah,
				Component.translatable("kultivator.kota.pertemuan.tip_tanding"), () -> kirim("tanding"));
		tambah(pilihan, tips, aksi, Component.translatable("kultivator.kota.pertemuan.duel"), sudah,
				Component.translatable("kultivator.kota.pertemuan.tip_duel", gaya()), () -> {
					kirim("duel");
					HubNav.tutupSemua();
				});
		tambah(pilihan, tips, aksi, Component.translatable("kultivator.kota.pertemuan.tantang"), null,
				Component.translatable("kultivator.kota.pertemuan.tip_tantang"), () -> p.yakin(Component.translatable("kultivator.kota.pertemuan.tip_tantang"), () -> {
					kirim("tantang");
					HubNav.tutupSemua();
				}));
		tambah(pilihan, tips, aksi, Component.translatable("kultivator.kota.pertemuan.ajak"),
				data.getBooleanOr("can_invite", false) ? null : Component.translatable(M + "temu.tak_mau"),
				Component.translatable("kultivator.kota.pertemuan.tip_ajak"), () -> {
					kirim("ajak");
					HubNav.tutupSemua();
				});
		tambah(pilihan, tips, aksi, Component.translatable("kultivator.kota.pertemuan.pamit"), null, Component.translatable(M + "temu.tip_pamit"), () -> {
			kirim("pamit");
			HubNav.tutupSemua();
		});
	}

	private static void tambah(List<DaftarPilihan.Pilihan> pilihan, List<Component> tips, List<Runnable> aksi, Component teks, Component kunci, Component tip,
			Runnable run) {
		pilihan.add(new DaftarPilihan.Pilihan(pilihan.size() + 1, teks, kunci, false));
		tips.add(tip);
		aksi.add(run);
	}

	@Override
	public boolean roda(double mx, double my, double dy) {
		return gulir.roda(mx, my, dy);
	}

	@Override
	public boolean tombol(KeyEvent event) {
		return gulir.tombol(event);
	}
}
