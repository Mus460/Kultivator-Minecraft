package dev.kultivator.client.dunia.kota;

import static dev.kultivator.client.dunia.kota.KotaJembatan.M;
import static dev.kultivator.client.dunia.kota.KotaJembatan.barisAbadi;
import static dev.kultivator.client.dunia.kota.KotaJembatan.ikon;
import static dev.kultivator.client.dunia.kota.KotaJembatan.jam;
import static dev.kultivator.client.dunia.kota.KotaJembatan.judul;
import static dev.kultivator.client.dunia.kota.KotaJembatan.kirim;
import static dev.kultivator.client.dunia.kota.KotaJembatan.peran;
import static dev.kultivator.client.dunia.kota.KotaJembatan.semua;
import static dev.kultivator.client.dunia.kota.KotaJembatan.teks;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KartuBarang;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The four subs of an Immortal City NPC's Di Sini page (DESIGN 34.5.4, 43.8.4): Pasar Abadi (wares in Batu Asal Abadi),
 * Kuliah Dao (the noon lecture), Kebun Persik (the peach every few days) and Perintah Utusan (the weekly edict). Each NPC
 * serves its own sub; the others show where to go. Single kit, text 1.0; the text subs scroll.
 */
final class AbadiPages {

	private static final int TOMBOL_H = 15;

	/** A sub whose content is one scrolled column (lecture, peach, edict). */
	private abstract static class Kolom implements KotaJembatan.Isi {
		private final GulirIsi gulir = new GulirIsi();

		/** Draws the column from {@code y}; returns the y below the last thing drawn. */
		abstract int isi(KotaJembatan.Halaman p, Lukis l, int x0, int y, int x1);

		@Override
		public final void lukis(KotaJembatan.Halaman p, Lukis l, int x0, int y0, int x1, int y1) {
			int r = x1 - 5;
			int y = gulir.mulai(l, x0, y0, r, y1);
			y += judul(l, Component.translatable("kultivator.kota.tab." + id()), x0, y, r);
			y += barisAbadi(l, x0, y, r);
			gulir.selesai(l, isi(p, l, x0, y + 2, r));
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

	// ================================================================== Pasar Abadi

	static final class Pasar implements KotaJembatan.Isi {
		private final DaftarGulir daftar = new DaftarGulir();

		@Override
		public String id() {
			return "pasar_abadi";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.pasar_abadi.short");
		}

		private static void beli(CompoundTag offer, int asal) {
			int price = offer.getIntOr("price", 0);
			if (!offer.isEmpty() && peran() == NpcRole.PEDAGANG_ABADI && asal >= price) {
				kirim("abeli:" + offer.getIntOr("i", -1) + ":" + price);
			}
		}

		@Override
		public void lukis(KotaJembatan.Halaman p, Lukis l, int x0, int y0, int x1, int y1) {
			int y = y0;
			y += judul(l, Component.translatable("kultivator.kota.abadi.pasar_judul", teks(semua(), "town")), x0, y, x1);
			y += barisAbadi(l, x0, y, x1);
			if (peran() != NpcRole.PEDAGANG_ABADI) {
				KeadaanKosong.gambar(l, KeadaanKosong.Gambar.NAMPAN, Component.translatable("kultivator.kota.abadi.ke_pasar"), null, null, x0, y, x1, y1);
				return;
			}
			int asal = semua().getIntOr("origin", 0);
			List<CompoundTag> market = TownPages.daftar(semua().getListOrEmpty("market"));
			int bawah = y1 - TOMBOL_H - 3;
			TownPages.barang(l, "pasar", daftar, market, offer -> {
				int price = offer.getIntOr("price", 0);
				boolean rare = offer.getBooleanOr("rare", false);
				boolean kurang = asal < price;
				return new KartuBarang.Isi(ikon(offer.getStringOr("item", "")), -1, teks(offer, "name"), -1, -1L, -2,
						kurang ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK, kurang ? Component.translatable("kultivator.kota.abadi.kurang_asal", price) : null,
						Component.translatable(rare ? "kultivator.kota.abadi.langka" : "kultivator.kota.abadi.biasa", price), rare, false);
			}, -1L, x0, y, x1, bawah, i -> beli(TownPages.pilihan(market, daftar), asal));
			CompoundTag sel = TownPages.pilihan(market, daftar);
			int price = sel.getIntOr("price", 0);
			Component alasan = sel.isEmpty() ? Component.translatable(M + "pilih_barang")
					: asal < price ? Component.translatable("kultivator.kota.abadi.kurang_asal", price) : null;
			TalismanButton.gambar(l, "pasar:beli", x0, y1 - TOMBOL_H, x1 - x0, TOMBOL_H, Component.translatable(M + "beli_asal", price), alasan == null,
					alasan, () -> beli(sel, asal));
		}
	}

	// ================================================================== Kuliah Dao

	static final class Kuliah extends Kolom {
		@Override
		public String id() {
			return "kuliah";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.kuliah.short");
		}

		@Override
		int isi(KotaJembatan.Halaman p, Lukis l, int x0, int y, int x1) {
			CompoundTag data = semua();
			Component state;
			if (data.getBooleanOr("lecture_done", false)) {
				state = Component.translatable("kultivator.kota.kuliah.sudah");
			} else if (data.getBooleanOr("lecture_now", false)) {
				state = Component.translatable("kultivator.kota.kuliah.sekarang");
			} else {
				state = Component.translatable("kultivator.kota.kuliah.nanti", jam(data.getLongOr("lecture_in", 0L)));
			}
			y += Math.round(l.bungkus(Brush.bold(state), x0, y, x1 - x0, Palette.SEAL_DARK)) + 3;
			y += Math.round(l.bungkus(Component.translatable("kultivator.kota.kuliah.cara"), x0, y, x1 - x0, l.gaya.teksLemah)) + 4;
			int listened = data.getIntOr("listened", 0);
			y += Bilah.baris(l, Component.translatable("kultivator.kota.tab.kuliah.short"), listened, 60, x0, y, x1,
					Component.translatable("kultivator.kota.kuliah.detik", listened, 60), Palette.JADE);
			return y + 2;
		}
	}

	// ================================================================== Kebun Persik

	static final class Persik extends Kolom {
		@Override
		public String id() {
			return "persik";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.persik.short");
		}

		@Override
		int isi(KotaJembatan.Halaman p, Lukis l, int x0, int y, int x1) {
			CompoundTag data = semua();
			boolean keeper = peran() == NpcRole.PENJAGA_KEBUN;
			y += Math.round(l.bungkus(Component.translatable("kultivator.kota.persik.isi", data.getIntOr("peach_days", 3)), x0, y, x1 - x0,
					l.gaya.teksLemah)) + 4;
			long wait = data.getLongOr("peach_in", 0L);
			Component state = wait <= 0L ? Component.translatable("kultivator.kota.persik.siap") : Component.translatable("kultivator.kota.persik.tunggu", wait);
			l.tengah(Brush.bold(state), (x0 + x1) / 2.0F, y, x1 - x0, wait <= 0L ? Palette.JADE_DARK : Palette.SEAL_DARK);
			y += 12;
			if (!keeper) {
				y += Math.round(l.bungkus(Component.translatable("kultivator.kota.persik.ke_kebun"), x0, y, x1 - x0, l.gaya.teksSamar)) + 3;
			}
			boolean bisa = keeper && wait <= 0L;
			Component alasan = !keeper ? Component.translatable("kultivator.kota.persik.ke_kebun") : bisa ? null : state;
			TalismanButton.gambar(l, "persik:petik", x0, y, x1 - x0, 16, Component.translatable("kultivator.kota.persik.tombol"), bisa, alasan,
					() -> kirim("persik"), bisa, false);
			return y + 18;
		}
	}

	// ================================================================== Perintah Utusan

	static final class Titah extends Kolom {
		static final String[] EDICTS = {"batu", "mutiara", "purba", "musuh"};

		@Override
		public String id() {
			return "titah";
		}

		@Override
		public Component nama() {
			return Component.translatable("kultivator.kota.tab.titah.short");
		}

		@Override
		int isi(KotaJembatan.Halaman p, Lukis l, int x0, int y, int x1) {
			CompoundTag data = semua();
			boolean utusan = peran() == NpcRole.UTUSAN;
			if (!data.contains("edict")) {
				y += Math.round(l.bungkus(Component.translatable("kultivator.kota.titah.kosong"), x0, y, x1 - x0, l.gaya.teksLemah)) + 4;
			} else {
				int kind = Math.max(0, Math.min(3, data.getIntOr("edict", 0)));
				int top = y;
				l.teks(Brush.bold(Component.translatable("kultivator.kota.titah.jenis." + EDICTS[kind])), x0 + 4, y + 3, x1 - x0 - 8, Palette.SEAL_DARK);
				y += 14;
				y += Math.round(l.bungkus(Component.translatable("kultivator.kota.titah.isi." + EDICTS[kind], data.getIntOr("edict_need", 1)), x0 + 4, y,
						x1 - x0 - 8, l.gaya.teks)) + 3;
				boolean done = data.getBooleanOr("edict_done", false);
				if (kind >= 2) {
					int need = Math.max(1, data.getIntOr("edict_need", 1));
					Bilah.gambar(l, x0 + 4, y + 1, x1 - 4, done ? 1.0F : data.getIntOr("edict_n", 0) / (float) need, 0xFFC8902A, null);
					y += 8;
				}
				Component state = data.getBooleanOr("edict_paid", false) ? Component.translatable("kultivator.kota.titah.dibayar")
						: done ? Component.translatable("kultivator.kota.titah.siap", data.getIntOr("edict_pay", 0))
						: Component.translatable("kultivator.kota.titah.upah", data.getIntOr("edict_pay", 0));
				l.teks(state, x0 + 4, y, x1 - x0 - 8, Palette.GOLD_DARK);
				y += 12;
				// the edict: a yellow imperial scroll with a red seal, drawn as a frame around the text
				Brush.frame(l.g, x0, top, x1, y, Palette.GOLD_DARK);
				l.g.fill(x1 - 10, y - 10, x1 - 3, y - 3, Palette.SEAL);
				y += 4;
			}
			int half = (x1 - x0 - 4) / 2;
			boolean ambil = utusan && data.getBooleanOr("edict_open", true);
			boolean serah = utusan && data.contains("edict") && !data.getBooleanOr("edict_paid", false);
			Component keUtusan = Component.translatable(M + "titah.ke_utusan");
			TalismanButton.gambar(l, "titah:ambil", x0, y, half, TOMBOL_H, Component.translatable("kultivator.kota.titah.ambil"), ambil,
					utusan ? Component.translatable("kultivator.kota.titah.dibayar") : keUtusan, () -> kirim("titah:ambil"));
			boolean siap = data.getBooleanOr("edict_done", false) && !data.getBooleanOr("edict_paid", false);
			TalismanButton.gambar(l, "titah:serah", x0 + half + 4, y, x1 - x0 - half - 4, TOMBOL_H, Component.translatable("kultivator.kota.titah.serah"),
					serah, utusan ? Component.translatable("kultivator.kota.titah.belum") : keUtusan, () -> kirim("titah:serah"), siap, false);
			return y + TOMBOL_H + 2;
		}
	}

	private AbadiPages() {
	}
}
