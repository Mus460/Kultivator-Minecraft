package dev.kultivator.client.dunia.kota;

import static dev.kultivator.client.dunia.kota.KotaJembatan.M;
import static dev.kultivator.client.dunia.kota.KotaJembatan.barisKota;
import static dev.kultivator.client.dunia.kota.KotaJembatan.ikon;
import static dev.kultivator.client.dunia.kota.KotaJembatan.jam;
import static dev.kultivator.client.dunia.kota.KotaJembatan.judul;
import static dev.kultivator.client.dunia.kota.KotaJembatan.kirim;
import static dev.kultivator.client.dunia.kota.KotaJembatan.peran;
import static dev.kultivator.client.dunia.kota.KotaJembatan.semua;
import static dev.kultivator.client.dunia.kota.KotaJembatan.tab;
import static dev.kultivator.client.dunia.kota.KotaJembatan.teks;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.LabelHarga;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PapanMisi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.wilayah.GulirIsi;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.KedaiTeh;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * The five subs of a town NPC's Di Sini page (DESIGN 34.5.1, 43.8.4): Toko (the pill or treasure pavilion's wares), Lelang
 * (the dusk auction), Kedai Teh (teas, Kabar Angin, the token), Pasar Gelap (the broker, the raid and its bribe) and Papan
 * Misi (the day's bounties). Each reads its part of {@link KotaClient#data()} and sends the same {@code KotaNet.Action}
 * ops as the old town screen; a service that belongs to another NPC shows where to find it. Drawn with the single kit
 * ({@link KartuBarang} shop rows, {@link PapanMisi}, {@link Bilah}) at text scale 1.0; tall subs scroll.
 */
final class TownPages {

	private static final int BARIS = 22;
	private static final int TOMBOL_H = 15;

	// ================================================================== shared shop block

	/** The compounds of a list tag. */
	static List<CompoundTag> daftar(ListTag list) {
		List<CompoundTag> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag tag) {
				out.add(tag);
			}
		}
		return out;
	}

	/** The selected entry of {@code list}, or an empty tag. */
	static CompoundTag pilihan(List<CompoundTag> list, DaftarGulir daftar) {
		int i = daftar.pilihan();
		return i >= 0 && i < list.size() ? list.get(i) : new CompoundTag();
	}

	/** The goods list of a K shop: {@link KartuBarang} rows; Enter or a second click on the selected row buys it. */
	static void barang(Lukis l, String kunci, DaftarGulir daftar, List<CompoundTag> barang, Function<CompoundTag, KartuBarang.Isi> isi, long uang,
			int x0, int y0, int x1, int y1, IntConsumer beli) {
		daftar.gambar(l, kunci, x0, y0, x1, y1, barang.size(), BARIS, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> KartuBarang.gambar(lk,
				isi.apply(barang.get(i)), rx0, ry0, rx1, ry1, sorot, pilih, uang, KotaClient.received()), () -> beli.accept(daftar.pilihan()), null);
	}

	static Component kurang(long harga, long uang) {
		return Component.translatable("kultivator.tempat_ui.harga.kurang", Brush.angka(Math.max(0L, harga - uang)));
	}

	// ================================================================== Toko

	static final class Toko implements KotaJembatan.Isi {
		private final DaftarGulir daftar = new DaftarGulir();

		@Override
		public String id() {
			return "toko";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.toko.short");
		}

		private static void beli(CompoundTag offer, long uang) {
			long price = offer.getLongOr("price", 0L);
			if (!offer.isEmpty() && offer.getIntOr("stock", 0) > 0 && uang >= price) {
				kirim("beli:" + offer.getIntOr("i", -1) + ":" + price);
			}
		}

		@Override
		public void lukis(KotaJembatan.Halaman p, Lukis l, int x0, int y0, int x1, int y1) {
			CompoundTag toko = tab("toko");
			int y = y0;
			y += judul(l, toko.isEmpty() ? Component.translatable("kultivator.kota.tab.toko")
					: Component.translatable("kultivator.kota.toko.judul." + toko.getStringOr("kind", "pil"), teks(semua(), "town")), x0, y, x1);
			y += barisKota(l, x0, y, x1);
			if (toko.isEmpty()) {
				KeadaanKosong.gambar(l, KeadaanKosong.Gambar.NAMPAN, Component.translatable("kultivator.kota.toko.kunjungi"), null, null, x0, y, x1, y1);
				return;
			}
			long nb = semua().getLongOr("nb", 0L);
			List<CompoundTag> offers = daftar(toko.getListOrEmpty("offers"));
			int bawah = y1 - TOMBOL_H - 14;
			if (offers.isEmpty()) {
				KeadaanKosong.gambar(l, KeadaanKosong.Gambar.NAMPAN, Component.translatable("kultivator.kota.toko.kosong"), null, null, x0, y, x1, bawah);
			} else {
				barang(l, "toko", daftar, offers, offer -> {
					long price = offer.getLongOr("price", 0L);
					int stock = offer.getIntOr("stock", 0);
					int count = offer.getIntOr("count", 1);
					Component name = count > 1 ? Component.translatable("kultivator.kota.layar.kali", teks(offer, "name"), count) : teks(offer, "name");
					String keadaan = stock <= 0 ? LembarKunci.K_HABIS : nb < price ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK;
					return new KartuBarang.Isi(ikon(offer.getStringOr("item", "")), -1, name, -1, price, stock, keadaan, null, null, false, false);
				}, nb, x0, y, x1, bawah, i -> beli(pilihan(offers, daftar), nb));
			}
			Component hand = toko.contains("hand")
					? Component.translatable("kultivator.kota.toko.tangan", teks(toko, "hand"), toko.getIntOr("hand_count", 1), toko.getLongOr("hand_price", 0L))
					: Component.translatable("kultivator.kota.toko.tangan_kosong");
			l.samar(hand, x0, bawah + 3, x1 - x0);
			if (l.di(x0, bawah + 2, x1, bawah + 12)) {
				l.tip(Component.translatable("kultivator.kota.toko.tip_jual", toko.getIntOr("sales_left", 0)));
			}
			CompoundTag sel = pilihan(offers, daftar);
			long price = sel.getLongOr("price", 0L);
			int stock = sel.getIntOr("stock", 0);
			Component alasan = sel.isEmpty() ? Component.translatable(M + "pilih_barang") : stock <= 0 ? Component.translatable("kultivator.kota.toko.habis")
					: nb < price ? kurang(price, nb) : null;
			int half = (x1 - x0 - 4) / 2;
			int by = y1 - TOMBOL_H;
			TalismanButton.gambar(l, "toko:beli", x0, by, half, TOMBOL_H, Component.translatable(M + "beli", Brush.angka(price)), alasan == null, alasan,
					() -> beli(sel, nb));
			boolean jual = toko.contains("hand") && toko.getIntOr("sales_left", 0) > 0;
			Component alasanJual = !toko.contains("hand") ? Component.translatable("kultivator.kota.toko.tangan_kosong")
					: Component.translatable(M + "jual_habis");
			TalismanButton.gambar(l, "toko:jual", x0 + half + 4, by, x1 - x0 - half - 4, TOMBOL_H, Component.translatable("kultivator.kota.toko.jual"),
					jual, jual ? null : alasanJual, () -> kirim("jual"));
		}
	}

	// ================================================================== Lelang

	static final class Lelang implements KotaJembatan.Isi {
		private static final int[] STEPS = {5, 10, 25};
		private final GulirIsi gulir = new GulirIsi();

		@Override
		public String id() {
			return "lelang";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.lelang.short");
		}

		private static CompoundTag lot() {
			CompoundTag lelang = tab("lelang");
			ListTag lots = lelang.getListOrEmpty("lots");
			int current = lelang.getIntOr("current", -1);
			return current >= 0 && current < lots.size() && lots.get(current) instanceof CompoundTag lot ? lot : new CompoundTag();
		}

		/** Why the player cannot bid {@code step} now, or null. */
		private static Component alasanTawar(int step) {
			CompoundTag lelang = tab("lelang");
			CompoundTag lot = lot();
			if (peran() != NpcRole.JURU_LELANG) {
				return Component.translatable(M + "lelang.ke_juru");
			}
			if (!lelang.getBooleanOr("active", false) || lot.getIntOr("state", 0) != 1 || lot.getLongOr("present", 0L) > 0L) {
				return Component.translatable("kultivator.kota.lelang.tidak_ada");
			}
			if (lot.getBooleanOr("mine", false)) {
				return Component.translatable(M + "lelang.memimpin");
			}
			long offer = lot.getLongOr("offer" + step, Long.MAX_VALUE);
			long nb = semua().getLongOr("nb", 0L);
			return nb < offer ? kurang(offer, nb) : null;
		}

		@Override
		public void lukis(KotaJembatan.Halaman p, Lukis l, int x0, int y0, int x1, int y1) {
			CompoundTag lelang = tab("lelang");
			int r = x1 - 5;
			int lebar = r - x0;
			int y = gulir.mulai(l, x0, y0, r, y1);
			y += judul(l, Component.translatable("kultivator.kota.lelang.judul", teks(semua(), "town")), x0, y, r);
			Component status;
			if (!lelang.getBooleanOr("enabled", true)) {
				status = Component.translatable("kultivator.kota.lelang.mati");
			} else if (lelang.getBooleanOr("active", false)) {
				status = Component.translatable("kultivator.kota.lelang.berjalan");
			} else if (lelang.getBooleanOr("done_today", false)) {
				status = Component.translatable("kultivator.kota.lelang.sudah_hari_ini");
			} else {
				status = Component.translatable("kultivator.kota.lelang.berikut", jam(lelang.getLongOr("next", 0L)));
			}
			if (peran() != NpcRole.JURU_LELANG) {
				status = Component.translatable("kultivator.kota.layar.sep", status, Component.translatable("kultivator.kota.lelang.di_panggung"));
			}
			y += Math.round(l.bungkus(status, x0, y, lebar, l.gaya.teksLemah)) + 3;
			List<CompoundTag> lots = daftar(lelang.getListOrEmpty("lots"));
			int current = lelang.getIntOr("current", -1);
			if (lots.isEmpty()) {
				y += Math.round(l.bungkus(Component.translatable("kultivator.kota.lelang.kosong"), x0, y, lebar, l.gaya.teksSamar)) + 3;
			}
			for (int i = 0; i < lots.size() && i < 5; i++) {
				baris(l, i, lots.get(i), i == current, x0, y, r);
				y += BARIS + 2;
			}
			y += 2;
			int bw = (lebar - 2 * 3) / 3;
			for (int i = 0; i < STEPS.length; i++) {
				int step = STEPS[i];
				Component alasan = alasanTawar(step);
				Component label = Component.translatable("kultivator.kota.lelang.langkah", step, Brush.singkat(lot().getLongOr("offer" + step, 0L)));
				boolean over = TalismanButton.gambar(l, "lelang:tawar" + step, x0 + i * (bw + 3), y, bw, TOMBOL_H, label, alasan == null, alasan,
						() -> kirim("tawar:" + tab("lelang").getIntOr("current", -1) + ":" + lot().getLongOr("price", 0L) + ":" + step));
				if (over && alasan == null) {
					l.tip(Component.translatable("kultivator.kota.lelang.tip_tawar", lot().getLongOr("offer" + step, 0L), semua().getLongOr("nb", 0L)));
				}
			}
			y += TOMBOL_H + 3;
			boolean titip = peran() == NpcRole.JURU_LELANG && lelang.getBooleanOr("active", false);
			Component alasanTitip = peran() != NpcRole.JURU_LELANG ? Component.translatable(M + "lelang.ke_juru")
					: Component.translatable("kultivator.kota.lelang.tidak_ada");
			boolean over = TalismanButton.gambar(l, "lelang:titip", x0, y, lebar, TOMBOL_H, Component.translatable("kultivator.kota.lelang.titip"), titip,
					titip ? null : alasanTitip, () -> kirim("titip"));
			if (over && titip) {
				l.tip(Component.translatable("kultivator.kota.lelang.tip_titip", lelang.getIntOr("consign_max", 2)));
			}
			y += TOMBOL_H + 4;
			List<CompoundTag> bidders = daftar(lelang.getListOrEmpty("bidders"));
			if (!bidders.isEmpty()) {
				y += l.kepala(Component.translatable("kultivator.kota.lelang.penawar"), x0, y, r);
				for (CompoundTag bidder : bidders) {
					Brush.rect(l.g, x0 + 2, y + 2, x0 + 7, y + 7, Palette.opaque(bidder.getIntOr("color", 0x808080)));
					l.teks(teks(bidder, "name"), x0 + 10, y, lebar - 10, l.gaya.teks);
					y += 10;
				}
			}
			gulir.selesai(l, y);
		}

		private static void baris(Lukis l, int index, CompoundTag lot, boolean onBlock, int x0, int y, int x1) {
			int state = lot.getIntOr("state", 0);
			l.kartu(x0, y, x1, y + BARIS, onBlock && state == 1 ? 1.0F : 0.0F, onBlock && state == 1);
			l.g.item(ikon(lot.getStringOr("item", "")), x0 + 3, y + 3);
			int tx = x0 + 22;
			int hargaW = LabelHarga.gambar(l, lot.getLongOr("price", 0L), -1L, x1 - 3, y + 2, true, true);
			l.teks(Component.literal((index + 1) + ". ").append(teks(lot, "name")), tx, y + 2, x1 - tx - hargaW - 6, state == 3 ? l.gaya.teksSamar : l.gaya.teks);
			Component leader = teks(lot, "leader");
			Component line = switch (state) {
				case 2 -> Component.translatable("kultivator.kota.lelang.terjual", leader);
				case 3 -> Component.translatable("kultivator.kota.lelang.tak_laku");
				case 1 -> {
					long present = lot.getLongOr("present", 0L);
					long sell = lot.getLongOr("sell", -1L);
					if (present > 0L) {
						yield Component.translatable("kultivator.kota.lelang.dipamerkan", jam(present));
					} else if (sell >= 0L) {
						yield Component.translatable("kultivator.kota.lelang.memimpin", leader, jam(sell));
					}
					yield Component.translatable("kultivator.kota.lelang.dibuka");
				}
				default -> Component.translatable("kultivator.kota.lelang.menunggu");
			};
			int color = lot.getBooleanOr("mine", false) ? Palette.JADE_DARK : state == 2 ? Palette.SEAL_DARK : l.gaya.teksLemah;
			l.teks(line, tx, y + 12, x1 - tx - 3, color);
			if (l.di(x0, y, x1, y + BARIS)) {
				l.tip(List.of(teks(lot, "name"), Component.translatable("kultivator.kota.lelang.harga", lot.getLongOr("price", 0L), lot.getLongOr("base", 0L))));
			}
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

	// ================================================================== Kedai Teh

	static final class Teh implements KotaJembatan.Isi {
		private static final String[] TEAS = {"embun", "awan", "penenang"};
		private static final String[] ITEMS = {"embun_pagi", "awan_ungu", "penenang_hati"};
		/** The Kabar Angin kinds, in the order the server indexes them ({@code kabar:<index>:<band>}). */
		private static final String[] KINDS = KedaiTeh.RUMOUR_KINDS;
		private final GulirIsi gulir = new GulirIsi();
		private int kind;

		@Override
		public String id() {
			return "teh";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.teh.short");
		}

		private static boolean master() {
			return peran() == NpcRole.PEMILIK_TEH;
		}

		private static CompoundTag tea(int i) {
			ListTag teas = tab("teh").getListOrEmpty("teas");
			return i < teas.size() && teas.get(i) instanceof CompoundTag tag ? tag : new CompoundTag();
		}

		@Override
		public void lukis(KotaJembatan.Halaman p, Lukis l, int x0, int y0, int x1, int y1) {
			CompoundTag teh = tab("teh");
			long nb = semua().getLongOr("nb", 0L);
			int r = x1 - 5;
			int lebar = r - x0;
			int y = gulir.mulai(l, x0, y0, r, y1);
			y += judul(l, Component.translatable("kultivator.kota.teh.judul"), x0, y, r);
			y += barisKota(l, x0, y, r);
			int active = teh.getIntOr("active", -1);
			Component status = active >= 0 && active < TEAS.length
					? Component.translatable("kultivator.kota.teh.aktif", Component.translatable("kultivator.kota.teh.nama." + TEAS[active]),
							jam(teh.getLongOr("left", 0L)))
					: Component.translatable("kultivator.kota.teh.tidak_aktif");
			y += Math.round(l.bungkus(Component.translatable("kultivator.kota.layar.sep", status, Component.translatable("kultivator.kota.teh.pengembara",
					teh.getIntOr("wanderers", 0))), x0, y, lebar, Palette.JADE_DARK)) + 3;
			if (!master()) {
				y += Math.round(l.bungkus(Component.translatable("kultivator.kota.teh.di_kedai"), x0, y, lebar, l.gaya.teksSamar)) + 3;
			}
			int bw = Math.max(34, Math.min(56, (lebar - 100) / 2));
			for (int i = 0; i < TEAS.length; i++) {
				int index = i;
				CompoundTag tea = tea(i);
				boolean open = tea.getBooleanOr("open", false);
				long price = tea.getLongOr("price", 1L);
				l.kartu(x0, y, r, y + BARIS, 0.0F, active == i);
				l.g.item(ikon("kultivator:teh_" + ITEMS[i]), x0 + 3, y + 3);
				int tx = x0 + 22;
				int room = r - 2 * bw - 10 - tx;
				l.teks(Component.translatable("item.kultivator.teh_" + ITEMS[i]), tx, y + 2, room, open ? l.gaya.teks : l.gaya.teksSamar);
				Component sub = open ? Component.translatable("kultivator.kota.teh.harga", price)
						: Component.translatable("kultivator.kota.teh.terkunci", Realm.byOrdinal(tea.getIntOr("gate", 0)).displayName());
				l.teks(sub, tx, y + 12, room, Palette.GOLD_DARK);
				if (l.di(x0, y, tx + room, y + BARIS)) {
					l.tip(List.of(Component.translatable("kultivator.kota.teh.efek." + TEAS[i], Math.round(teh.getDoubleOr("bonus", 0.1D) * 100.0D)),
							Component.translatable("kultivator.kota.teh.tip_harga", price)));
				}
				Component alasan = !master() ? Component.translatable("kultivator.kota.teh.di_kedai") : !open ? sub : nb < price ? kurang(price, nb) : null;
				TalismanButton.gambar(l, "teh:minum" + i, r - 2 * bw - 6, y + 4, bw, 14, Component.translatable("kultivator.kota.teh.minum_tombol"),
						alasan == null, alasan, () -> kirim("teh:" + index));
				boolean over = TalismanButton.gambar(l, "teh:bungkus" + i, r - bw - 3, y + 4, bw, 14, Component.translatable("kultivator.kota.teh.bungkus"),
						alasan == null, alasan, () -> kirim("beliteh:" + index));
				if (over && alasan == null) {
					l.tip(Component.translatable("kultivator.kota.teh.tip_bungkus"));
				}
				y += BARIS + 2;
			}
			y += 3;
			long cooldown = teh.getLongOr("cooldown", 0L);
			y += l.kepala(cooldown > 0L ? Component.translatable("kultivator.kota.kabar.judul_tunggu", jam(cooldown))
					: Component.translatable("kultivator.kota.kabar.judul"), x0, y, r);
			int cw = (lebar - 2 * 3) / 3;
			for (int i = 0; i < KINDS.length; i++) {
				int index = i;
				TalismanButton.gambar(l, "teh:jenis" + i, x0 + (i % 3) * (cw + 3), y + (i / 3) * 16, cw, 14,
						Component.translatable("kultivator.kota.kabar.jenis." + KINDS[i]), true, null, () -> kind = index, false, kind == index);
			}
			y += ((KINDS.length + 2) / 3) * 16 + 2;
			Component alasanKabar = !master() ? Component.translatable("kultivator.kota.teh.di_kedai")
					: cooldown > 0L ? Component.translatable("kultivator.kota.kabar.judul_tunggu", jam(cooldown)) : null;
			List<CompoundTag> rumours = daftar(teh.getListOrEmpty("rumours"));
			for (int b = 0; b < 3; b++) {
				int band = b;
				CompoundTag entry = b < rumours.size() ? rumours.get(b) : new CompoundTag();
				boolean over = TalismanButton.gambar(l, "teh:kabar" + b, x0 + b * (cw + 3), y, cw, TOMBOL_H,
						Component.translatable("kultivator.kota.kabar.tombol", entry.getIntOr("radius", 0), entry.getLongOr("price", 0L)), alasanKabar == null,
						alasanKabar, () -> kirim("kabar:" + kind + ":" + band));
				if (over && alasanKabar == null) {
					l.tip(Component.translatable("kultivator.kota.kabar.tip"));
				}
			}
			y += TOMBOL_H + 4;
			boolean token = master() && teh.getBooleanOr("token", false);
			Component tip = Component.translatable("kultivator.kota.teh.tip_token", teh.getIntOr("heard", 0));
			boolean over = TalismanButton.gambar(l, "teh:token", x0, y, lebar, TOMBOL_H,
					Component.translatable("kultivator.kota.teh.token", teh.getLongOr("token_price", 60L)), token, token ? null : tip, () -> kirim("token"));
			if (over && token) {
				l.tip(tip);
			}
			y += TOMBOL_H + 2;
			gulir.selesai(l, y);
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

	// ================================================================== Pasar Gelap

	static final class Gelap implements KotaJembatan.Isi {
		private final DaftarGulir daftar = new DaftarGulir();

		@Override
		public String id() {
			return "gelap";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.gelap.short");
		}

		@Override
		public void lukis(KotaJembatan.Halaman p, Lukis l, int x0, int y0, int x1, int y1) {
			CompoundTag gelap = tab("gelap");
			int y = y0;
			y += judul(l, Component.translatable("kultivator.kota.gelap.judul"), x0, y, x1);
			y += barisKota(l, x0, y, x1);
			if (gelap.isEmpty()) {
				KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable("kultivator.kota.gelap.bisikan"), null, null, x0, y, x1, y1);
				return;
			}
			if (gelap.contains("eclipse")) {
				// Gerhana Darah: the smugglers lie low and every price carries the surcharge
				float glow = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 420.0D);
				l.g.fill(x0, y, x1, y + 1, Palette.argb(Math.round(0x70 + 0x50 * glow), 0xB01020));
				l.teks(Component.translatable("kultivator.kota.gelap.gerhana", gelap.getIntOr("eclipse", 25)), x0, y + 2, x1 - x0, 0xFF9A1020);
				y += 12;
			}
			long nb = semua().getLongOr("nb", 0L);
			int rep = gelap.getIntOr("rep", 0);
			boolean ditolak = rep <= -50;
			List<CompoundTag> offers = daftar(gelap.getListOrEmpty("offers"));
			int bawah = y1 - TOMBOL_H - 30;
			Component raidTip = Component.translatable("kultivator.kota.gelap.tip_beli", Math.round(gelap.getDoubleOr("raid", 0.08D) * 100.0D));
			barang(l, "gelap", daftar, offers, offer -> {
				long price = offer.getLongOr("price", 0L);
				String keadaan = ditolak ? LembarKunci.K_DITOLAK : nb < price ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK;
				return new KartuBarang.Isi(ikon(offer.getStringOr("item", "")), -1, teks(offer, "name"), -1, price, -2, keadaan,
						ditolak ? Component.translatable("kultivator.kota.gelap.ditolak") : null, null, false, false);
			}, nb, x0, y, x1, bawah, i -> beli(pilihan(offers, daftar), nb, ditolak));
			int iy = bawah + 3;
			l.teks(Component.translatable("kultivator.kota.gelap.reputasi", rep, Math.round(gelap.getDoubleOr("raid", 0.08D) * 100.0D)), x0, iy, x1 - x0,
					rep < 0 ? Palette.SEAL_DARK : l.gaya.teksLemah);
			if (l.di(x0, iy, x1, iy + 14)) {
				l.tip(raidTip);
			}
			Bilah.gambar(l, x0, iy + 10, x1, (rep + 100) / 200.0F, rep < 0 ? Palette.SEAL : 0xFF6A4A9A, null);
			iy += 16;
			if (gelap.contains("raid_left")) {
				l.teks(Component.translatable("kultivator.kota.gelap.razia_sisa", jam(gelap.getLongOr("raid_left", 0L))), x0, iy, x1 - x0, Palette.SEAL);
			} else if (gelap.contains("hand")) {
				l.samar(Component.translatable("kultivator.kota.gelap.tangan", teks(gelap, "hand"), gelap.getLongOr("hand_price", 0L)), x0, iy, x1 - x0);
			}
			CompoundTag sel = pilihan(offers, daftar);
			long price = sel.getLongOr("price", 0L);
			Component alasan = sel.isEmpty() ? Component.translatable(M + "pilih_barang") : ditolak ? Component.translatable("kultivator.kota.gelap.ditolak")
					: nb < price ? kurang(price, nb) : null;
			int third = (x1 - x0 - 6) / 3;
			int by = y1 - TOMBOL_H;
			TalismanButton.gambar(l, "gelap:beli", x0, by, third, TOMBOL_H, Component.translatable(M + "beli", Brush.angka(price)), alasan == null, alasan,
					() -> beli(sel, nb, ditolak));
			boolean jual = gelap.contains("hand");
			TalismanButton.gambar(l, "gelap:jual", x0 + third + 3, by, third, TOMBOL_H, Component.translatable("kultivator.kota.gelap.jual"), jual,
					jual ? null : Component.translatable("kultivator.kota.toko.tangan_kosong"), () -> kirim("gjual"));
			boolean razia = gelap.contains("raid_left");
			TalismanButton.gambar(l, "gelap:suap", x0 + 2 * (third + 3), by, x1 - x0 - 2 * (third + 3), TOMBOL_H,
					Component.translatable("kultivator.kota.gelap.suap", gelap.getLongOr("bribe", 0L)), razia,
					razia ? null : Component.translatable("kultivator.kota.gelap.tidak_razia"), () -> kirim("suap"), razia, false);
		}

		private static void beli(CompoundTag offer, long uang, boolean ditolak) {
			long price = offer.getLongOr("price", 0L);
			if (!offer.isEmpty() && !ditolak && uang >= price) {
				kirim("gbeli:" + offer.getIntOr("i", -1) + ":" + price);
			}
		}
	}

	// ================================================================== Papan Misi

	static final class Misi implements KotaJembatan.Isi {
		private static final String[] JENIS = {"buru", "kemah", "herba", "kawal"};
		private final DaftarGulir daftar = new DaftarGulir();

		@Override
		public String id() {
			return "misi";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.misi.short");
		}

		private static String jenis(CompoundTag entry) {
			return JENIS[Math.max(0, Math.min(3, entry.getIntOr("type", 0)))];
		}

		@Override
		public void lukis(KotaJembatan.Halaman p, Lukis l, int x0, int y0, int x1, int y1) {
			CompoundTag misi = tab("misi");
			int y = y0;
			y += judul(l, Component.translatable("kultivator.kota.misi.judul"), x0, y, x1);
			l.samar(Component.translatable("kultivator.kota.misi.sisa", misi.getIntOr("left", 0), misi.getIntOr("rep", 0)), x0, y, x1 - x0);
			y += 11;
			if (!misi.getBooleanOr("enabled", true)) {
				KeadaanKosong.gambar(l, KeadaanKosong.Gambar.PAPAN, Component.translatable("kultivator.kota.misi.mati"), null, null, x0, y, x1, y1);
				return;
			}
			boolean keeper = peran() == NpcRole.PENJAGA;
			CompoundTag active = misi.getCompoundOrEmpty("active");
			List<PapanMisi.Misi> papan = new ArrayList<>();
			List<CompoundTag> board = daftar(misi.getListOrEmpty("board"));
			for (int i = 0; i < board.size(); i++) {
				CompoundTag entry = board.get(i);
				if (entry.isEmpty()) {
					continue;
				}
				String id = jenis(entry);
				Component kunci = entry.getBooleanOr("taken", false) ? Component.translatable("kultivator.kota.misi.sudah_diambil") : null;
				papan.add(new PapanMisi.Misi(String.valueOf(i), "", Component.translatable("kultivator.kota.misi.jenis." + id), null,
						Component.translatable("kultivator.kota.misi.isi." + id), entry.getLongOr("reward", 0L),
						List.of(Component.translatable(M + "misi.nama_baik")), null, null, kunci, false, 0L, 0L, 0L));
			}
			Component alasanTerima = !keeper ? Component.translatable("kultivator.kota.misi.di_papan")
					: !active.isEmpty() ? Component.translatable("kultivator.kota.misi.sudah_ada")
					: misi.getIntOr("left", 0) <= 0 ? Component.translatable(M + "misi.habis") : null;
			int bawah = active.isEmpty() ? y1 : y1 - TOMBOL_H - 13;
			PapanMisi.gambar(l, "misi", daftar, papan, x0, y, x1, bawah, alasanTerima, null, m -> kirim("ambil:" + m.id()), null);
			if (active.isEmpty()) {
				return;
			}
			boolean done = active.getBooleanOr("done", false);
			Component state = Component.translatable(done ? "kultivator.kota.misi.aktif_selesai" : "kultivator.kota.misi.aktif",
					Component.translatable("kultivator.kota.misi.jenis." + jenis(active)));
			l.teks(state, x0, bawah + 2, x1 - x0, done ? Palette.JADE_DARK : Palette.SEAL_DARK);
			int half = (x1 - x0 - 4) / 2;
			int by = y1 - TOMBOL_H;
			Component alasan = keeper ? null : Component.translatable("kultivator.kota.misi.di_papan");
			TalismanButton.gambar(l, "misi:serah", x0, by, half, TOMBOL_H, Component.translatable("kultivator.kota.misi.serah"), keeper, alasan,
					() -> kirim("serah"), done, false);
			TalismanButton.gambar(l, "misi:batal", x0 + half + 4, by, x1 - x0 - half - 4, TOMBOL_H, Component.translatable("kultivator.kota.misi.batal"),
					keeper, alasan, () -> p.yakin(Component.translatable(M + "misi.batal_yakin"), () -> kirim("batal")));
		}
	}

	private TownPages() {
	}
}
