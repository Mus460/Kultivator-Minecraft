package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What happens on one road segment (DESIGN 39.6.2): a road shrine (20 %), a wanderer (10 %), bandits (6 %), a spirit
 * beast (8 %), a member of a rival group (4 %), a caravan or pedlar (6 %), a view that opens the heart (5 %). The danger
 * rows scale with caution (careful 0.6 ... reckless 1.5), the road (Imperial x0.5, village x1.0, path or wilds x1.5) and
 * the night (x1.5); a bandit camp of 38 within 600 blocks makes bandits twice as likely and sets their strength. Rain
 * makes the segment 10 % longer; a thunderstorm makes the party shelter for an hour.
 * <p>
 * Bandits: the party wins with {@code clamp(0.5 + 0.12 (P - band), 0.10, 0.95)} (2-8 NB, a help counted, a proud
 * memory); a careful party that loses runs away (10-30 % of the purse lost); otherwise it is beaten: wounded, half the
 * purse and one bag stack gone, and one time in fifty captured (a Return Talisman brings it home instead).
 */
final class PeristiwaJalan {

	private enum Jenis {
		KUIL, PENGEMBARA, BEGAL, BINATANG, SAINGAN, KAFILAH, PEMANDANGAN
	}

	static void selesaikan(Tahap.Konteks k) {
		Rencana r = k.r();
		RandomSource rnd = k.rnd();
		int kelas = k.t().getIntOr("kelas", KontrakKota.LIAR);
		double bahaya = Tahap.faktorHati(Watak.kehatian(k.pimpinan())) * KontrakKota.faktorJalan(kelas) * (k.jam() >= 19 || k.jam() < 5 ? 1.5D : 1.0D);
		DuniaSites.Site camp = KontrakKota.kemahBegal(k.level(), k.pos(), 600);
		double begal = 0.06D * bahaya * (camp != null ? 2.0D : 1.0D);
		double[] w = {0.20D, 0.10D, begal, 0.08D * bahaya, 0.04D * bahaya, 0.06D, 0.05D};
		double total = 0.0D;
		for (double v : w) {
			total += v;
		}
		double scale = total > 0.95D ? 0.95D / total : 1.0D;
		double roll = rnd.nextDouble();
		Jenis hit = null;
		for (int i = 0; i < w.length; i++) {
			roll -= w[i] * scale;
			if (roll < 0.0D) {
				hit = Jenis.values()[i];
				break;
			}
		}
		if (k.level().isThundering()) {
			r.geser(k.index() + 1 < r.jumlahTahap() ? k.index() + 1 : k.index(), Tahap.JAM);
			r.kisah(JalanIds.k("kisah.jalan.badai"), List.of(k.namaPemimpin()));
			Kepulangan.aturPulang(k.server(), r);
		} else if (k.level().isRaining() && rnd.nextFloat() < 0.5F) {
			long extra = Math.max(20L, (k.sampai() - k.mulai()) / 10L);
			r.geser(k.index() + 1 < r.jumlahTahap() ? k.index() + 1 : k.index(), extra);
			Kepulangan.aturPulang(k.server(), r);
		}
		if (hit == null) {
			if (k.t().getIntOr("n", 0) == 0) {
				r.kisah(JalanIds.k("kisah.jalan.tenang." + rnd.nextInt(5)), List.of(k.namaPemimpin()));
			}
			return;
		}
		switch (hit) {
			case KUIL -> kuil(k);
			case PENGEMBARA -> pengembara(k);
			case BEGAL -> begal(k, camp);
			case BINATANG -> binatang(k);
			case SAINGAN -> saingan(k);
			case KAFILAH -> kafilah(k);
			case PEMANDANGAN -> pemandangan(k);
		}
	}

	private static void kuil(Tahap.Konteks k) {
		Rencana r = k.r();
		long dupa = MandiriConfig.get().harga.dupaJalan;
		for (UUID soul : r.rombongan()) {
			if (r.sisaUang(soul) >= dupa) {
				r.ubahUang(soul, -dupa);
			}
			Tahap.tambahIblis(r, soul, -1);
		}
		r.kisah(JalanIds.k("kisah.jalan.kuil." + k.rnd().nextInt(3)), List.of(k.namaPemimpin()));
		Tahap.catat(k, r.soul(), "jalan.kuil", JalanIds.k("harian.jalan.kuil." + k.rnd().nextInt(3)), List.of(k.namaPemimpin()),
				EntriHarian.Suasana.TENANG, false);
	}

	private static void pengembara(Tahap.Konteks k) {
		Rencana r = k.r();
		RandomSource rnd = k.rnd();
		int look = (int) (r.benih() ^ k.index() * 31L);
		boolean perempuan = rnd.nextBoolean();
		List<String> nama = JalanIds.orang(look, perempuan);
		List<String> args = new ArrayList<>();
		args.add(k.namaPemimpin());
		args.addAll(nama);
		String hasil;
		if (rnd.nextFloat() < 0.30F) {
			if (rnd.nextBoolean()) {
				Tahap.tambahPaham(r, r.soul(), 0.5D);
				hasil = "paham";
			} else {
				r.barang(r.soul(), "kultivator:catatan_pengembara", 1, DaftarBelanja.KE_TAS, 0L, false);
				hasil = "tukar";
			}
		} else {
			hasil = "obrol";
		}
		r.kisah(JalanIds.k("kisah.jalan.pengembara." + hasil), args);
		Tahap.catat(k, r.soul(), "jalan.pengembara", JalanIds.k("harian.jalan.pengembara." + hasil), args, EntriHarian.Suasana.SENANG, false);
	}

	private static void begal(Tahap.Konteks k, DuniaSites.Site camp) {
		Rencana r = k.r();
		RandomSource rnd = k.rnd();
		double p = r.kekuatan();
		int band = camp != null ? Math.max(1, camp.tier() + 1) : Math.max(1, (int) Math.round(p) - 1 + rnd.nextInt(3));
		double win = Mth.clamp(0.5D + 0.12D * (p - band), 0.10D, 0.95D);
		List<String> args = new ArrayList<>();
		args.add(k.namaPemimpin());
		UUID lead = r.soul();
		if (rnd.nextDouble() < win) {
			long reward = 2L + rnd.nextInt(7);
			r.tambahPendapatan(lead, reward);
			Tahap.tambahAngka(r, "menolong", lead, 1);
			r.kenangan("mandiri.begal_menang");
			args.add(String.valueOf(reward));
			r.kisah(JalanIds.k("kisah.jalan.begal.menang." + rnd.nextInt(3)), args);
			Tahap.catat(k, lead, "jalan.begal", JalanIds.k("harian.jalan.begal.menang"), args, EntriHarian.Suasana.BANGGA, false);
			return;
		}
		boolean careful = Watak.kehatian(k.pimpinan()).ordinal() <= 1;
		if (careful && rnd.nextBoolean()) {
			for (UUID soul : r.rombongan()) {
				long lost = Math.round(r.sisaUang(soul) * (0.10D + rnd.nextDouble() * 0.20D));
				r.ubahUang(soul, -lost);
				if (soul.equals(lead)) {
					args.add(String.valueOf(lost));
				}
			}
			r.kisah(JalanIds.k("kisah.jalan.begal.kabur"), args);
			Tahap.catat(k, lead, "jalan.begal", JalanIds.k("harian.jalan.begal.kabur"), args, EntriHarian.Suasana.KESAL, false);
			return;
		}
		for (UUID soul : r.rombongan()) {
			long lost = r.sisaUang(soul) / 2L;
			r.ubahUang(soul, -lost);
			r.lukai(soul, JenisLuka.TERLUKA);
			Tahap.tambahAngka(r, "hilangTas", soul, 1);
			if (soul.equals(lead)) {
				args.add(String.valueOf(lost));
			}
		}
		r.kenangan("mandiri.begal_kalah");
		r.kisah(JalanIds.k("kisah.jalan.begal.kalah"), args);
		Tahap.catat(k, lead, "jalan.begal", JalanIds.k("harian.jalan.begal.kalah"), args, EntriHarian.Suasana.SEDIH, true);
		if (rnd.nextFloat() < 0.02F) {
			BlockPos at = camp != null ? camp.pos() : k.pos();
			Tawanan.tangkap(k, "begal", at);
		}
	}

	private static void binatang(Tahap.Konteks k) {
		Rencana r = k.r();
		RandomSource rnd = k.rnd();
		int tier = Math.max(1, Math.min(4, 1 + k.band() / 2 + (rnd.nextInt(4) == 0 ? 1 : 0)));
		double chance = Tahap.peluang(r, tier, 0.0D);
		List<String> args = new ArrayList<>();
		args.add(k.namaPemimpin());
		args.add("kultivator.sekte.mandiri.jalan.binatang." + rnd.nextInt(6));
		if (rnd.nextDouble() < chance) {
			r.barang(r.soul(), "kultivator:beast_core@" + tier, 1, DaftarBelanja.KE_TAS, 0L, false);
			r.kisah(JalanIds.k("kisah.jalan.binatang.menang"), args);
			Tahap.catat(k, r.soul(), "jalan.binatang", JalanIds.k("harian.jalan.binatang.menang"), args, EntriHarian.Suasana.BANGGA, false);
			r.hasil().putInt("binatangTier", Math.max(r.hasil().getIntOr("binatangTier", 0), tier));
		} else {
			r.lukai(r.soul(), JenisLuka.TERLUKA);
			r.kisah(JalanIds.k("kisah.jalan.binatang.kalah"), args);
			Tahap.catat(k, r.soul(), "jalan.binatang", JalanIds.k("harian.jalan.binatang.kalah"), args, EntriHarian.Suasana.LELAH, false);
		}
	}

	private static void saingan(Tahap.Konteks k) {
		Rencana r = k.r();
		RandomSource rnd = k.rnd();
		int look = (int) (r.benih() >>> 7 ^ k.index() * 131L);
		List<String> args = new ArrayList<>();
		args.add(k.namaPemimpin());
		args.addAll(JalanIds.orang(look, rnd.nextBoolean()));
		double rival = Math.max(0.0D, r.anggota(r.soul()).getDoubleOr("power", 0.0D) + rnd.nextInt(3) - 1);
		double win = Mth.clamp(0.5D + 0.12D * (r.kekuatan() - rival), 0.10D, 0.95D);
		if (rnd.nextDouble() < win) {
			r.kenangan("mandiri.saingan_menang");
			r.kisah(JalanIds.k("kisah.jalan.saingan.menang"), args);
			Tahap.catat(k, r.soul(), "jalan.saingan", JalanIds.k("harian.jalan.saingan.menang"), args, EntriHarian.Suasana.BANGGA, false);
		} else {
			r.kenangan("mandiri.saingan_kalah");
			r.hasil().putString("dendam", "saingan:" + Integer.toHexString(look));
			r.kisah(JalanIds.k("kisah.jalan.saingan.kalah"), args);
			Tahap.catat(k, r.soul(), "jalan.saingan", JalanIds.k("harian.jalan.saingan.kalah"), args, EntriHarian.Suasana.KESAL, false);
		}
	}

	private static void kafilah(Tahap.Konteks k) {
		Rencana r = k.r();
		UUID lead = r.soul();
		List<String> args = new ArrayList<>();
		args.add(k.namaPemimpin());
		DaftarBelanja.Baris want = null;
		for (int i = 0; i < r.anggota(lead).getListOrEmpty("daftar").size(); i++) {
			DaftarBelanja.Baris b = DaftarBelanja.Baris.fromTag(r.anggota(lead).getListOrEmpty("daftar").getCompoundOrEmpty(i));
			if (!Perlengkapan.semu(b.item()) && KunjunganKota.sudahDibeli(r, lead, b.item(), b.ke()) < b.n()) {
				want = b;
				break;
			}
		}
		if (want != null) {
			long price = Math.max(1L, Math.round(PasarBawaan.dasar(want.item(), r.benih()) * 0.9D));
			if (price > 0L && r.sisaUang(lead) >= price + (want.prio() == 1 ? 0L : 10L * (k.band() + 1L))) {
				r.ubahUang(lead, -price);
				r.barang(lead, want.item(), 1, want.ke(), price, true);
				args.add(JalanIds.kunciBarang(want.item()));
				args.add(String.valueOf(price));
				r.kisah(JalanIds.k("kisah.jalan.kafilah.beli"), args);
				Tahap.catat(k, lead, "jalan.kafilah", JalanIds.k("harian.jalan.kafilah.beli"), args, EntriHarian.Suasana.SENANG, false);
				return;
			}
		}
		long pay = 5L * (k.band() + 1L);
		r.tambahPendapatan(lead, pay);
		r.geser(k.index() + 1 < r.jumlahTahap() ? k.index() + 1 : k.index(), 4800L);
		Kepulangan.aturPulang(k.server(), r);
		args.add(String.valueOf(pay));
		r.kisah(JalanIds.k("kisah.jalan.kafilah.kawal"), args);
		Tahap.catat(k, lead, "jalan.kafilah", JalanIds.k("harian.jalan.kafilah.kawal"), args, EntriHarian.Suasana.LELAH, false);
	}

	private static void pemandangan(Tahap.Konteks k) {
		Rencana r = k.r();
		for (UUID soul : r.rombongan()) {
			Tahap.tambahPaham(r, soul, 1.0D);
		}
		List<String> args = List.of(k.namaPemimpin(), JalanIds.k("pemandangan." + k.rnd().nextInt(8)));
		r.kisah(JalanIds.k("kisah.jalan.pemandangan"), args);
		Tahap.catat(k, r.soul(), "jalan.ilham", JalanIds.k("harian.jalan.pemandangan." + k.rnd().nextInt(3)), args,
				EntriHarian.Suasana.TENANG, false);
	}

	private PeristiwaJalan() {
	}
}
