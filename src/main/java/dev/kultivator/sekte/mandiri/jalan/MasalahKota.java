package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Trouble in town (DESIGN 39.6.6), one roll per stop: a pickpocket (6 %; twice in a slum district or the Water City),
 * a provoking rival (4 %), a swindler for the naive (5 %), a townsperson to help (5 %, twice for the kind-hearted), a
 * child staring at the disciple's sword who wants to become a cultivator (3 %), a sweetheart (3 %, with the goal JODOH
 * and nobody yet) and too much wine for the lazy and the proud (2 %). Good deeds and caught thieves move the master's
 * Good Name in that town ({@code KotaData.addReputation}, C39.K3) and spread a rumour (C39.K8); the black market's raid
 * (8 %) fines {@code 20 x band} NB or hands the disciple to the guards.
 */
final class MasalahKota {

	private enum Jenis {
		COPET, PROVOKASI, PENIPU, TOLONG, CALON, ASMARA, MABUK
	}

	static void coba(Tahap.Konteks k, KunjunganKota.Stop stop, long town, List<String> base) {
		Rencana r = k.r();
		RandomSource rnd = k.rnd();
		CompoundTagView lead = new CompoundTagView(r.anggota(r.soul()));
		int sifat = lead.sifat;
		boolean kumuh = kumuh(k, town);
		double[] w = new double[Jenis.values().length];
		w[Jenis.COPET.ordinal()] = 0.06D * (kumuh ? 2.0D : 1.0D);
		w[Jenis.PROVOKASI.ordinal()] = 0.04D;
		w[Jenis.PENIPU.ordinal()] = !Watak.punya(sifat, Sifat.LICIK) && lead.band <= 2 ? 0.05D : 0.0D;
		w[Jenis.TOLONG.ordinal()] = 0.05D * (Watak.punya(sifat, Sifat.PENYAYANG) ? 2.0D : 1.0D);
		w[Jenis.CALON.ordinal()] = 0.03D;
		w[Jenis.ASMARA.ordinal()] = Watak.punyaCita(r.anggota(r.soul()), JenisCita.JODOH.id()) && !r.hasil().getBooleanOr("asmara", false) ? 0.03D : 0.0D;
		w[Jenis.MABUK.ordinal()] = (Watak.punya(sifat, Sifat.MALAS) || Watak.punya(sifat, Sifat.SOMBONG))
				&& (stop == KunjunganKota.Stop.RUMAH_MAKAN || stop == KunjunganKota.Stop.KEDAI_TEH) ? 0.02D : 0.0D;
		double roll = rnd.nextDouble();
		Jenis hit = null;
		for (Jenis j : Jenis.values()) {
			roll -= w[j.ordinal()];
			if (roll < 0.0D) {
				hit = j;
				break;
			}
		}
		if (hit == null) {
			return;
		}
		UUID lead0 = r.soul();
		List<String> args = new ArrayList<>(base);
		switch (hit) {
			case COPET -> {
				boolean tangkap = (Watak.punya(sifat, Sifat.BERANI) || lead.band >= 2) && rnd.nextFloat() < 0.70F;
				if (tangkap) {
					namaBaik(k, town, "copet", 1);
					KontrakKota.kabar(k.level(), town, "copet", k.namaPemimpin(), namaAula(k));
					r.kenangan("mandiri.copet_tangkap");
					r.kisah(JalanIds.k("kisah.masalah.copet.tangkap"), args);
					Tahap.catat(k, lead0, "kota.copet", JalanIds.k("harian.kota.copet.tangkap"), args, EntriHarian.Suasana.BANGGA, false);
				} else {
					long lost = Math.round(r.sisaUang(lead0) * (0.10D + rnd.nextDouble() * 0.15D));
					r.ubahUang(lead0, -lost);
					args.add(String.valueOf(lost));
					r.kisah(JalanIds.k("kisah.masalah.copet.lolos"), args);
					Tahap.catat(k, lead0, "kota.copet", JalanIds.k("harian.kota.copet.lolos"), args, EntriHarian.Suasana.KESAL, false);
				}
				r.hasil().putString("adegan", "copet");
			}
			case PROVOKASI -> {
				boolean menang = rnd.nextDouble() < Math.max(0.1D, Math.min(0.9D, 0.5D + 0.1D * (r.kekuatan() - lead.band)));
				r.kenangan(menang ? "mandiri.provokasi_menang" : "mandiri.provokasi_kalah");
				if (menang) {
					KontrakKota.kabar(k.level(), town, "duel", k.namaPemimpin(), namaAula(k));
				}
				r.kisah(JalanIds.k("kisah.masalah.provokasi." + (menang ? "menang" : "kalah")), args);
				Tahap.catat(k, lead0, "kota.provokasi", JalanIds.k("harian.kota.provokasi." + (menang ? "menang" : "kalah")), args,
						menang ? EntriHarian.Suasana.BANGGA : EntriHarian.Suasana.KESAL, false);
				r.hasil().putString("adegan", "provokasi");
			}
			case PENIPU -> {
				long cost = Math.min(r.sisaUang(lead0), 12L + rnd.nextInt(10));
				if (cost > 0L) {
					r.ubahUang(lead0, -cost);
					r.barang(lead0, "kultivator:healing_pill", 1, DaftarBelanja.KE_TAS, cost, true);
					args.add(String.valueOf(cost));
					r.kenangan("mandiri.ditipu");
					r.kisah(JalanIds.k("kisah.masalah.penipu"), args);
					Tahap.catat(k, lead0, "kota.penipu", JalanIds.k("harian.kota.penipu"), args, EntriHarian.Suasana.SEDIH, false);
				}
			}
			case TOLONG -> {
				namaBaik(k, town, "tolong", 1);
				KontrakKota.kabar(k.level(), town, "tolong", k.namaPemimpin(), namaAula(k));
				Tahap.tambahAngka(r, "menolong", lead0, 1);
				r.kenangan("mandiri.tolong_warga");
				String apa = JalanIds.k("tolong." + rnd.nextInt(6));
				args.add(apa);
				r.kisah(JalanIds.k("kisah.masalah.tolong"), args);
				Tahap.catat(k, lead0, "kota.tolong", JalanIds.k("harian.kota.tolong"), args, EntriHarian.Suasana.SENANG, false);
				r.hasil().putString("adegan", "tolong");
			}
			case CALON -> {
				r.hasil().putBoolean("calon", true);
				r.kisah(JalanIds.k("kisah.masalah.calon"), args);
				Tahap.catat(k, lead0, "kota.calon", JalanIds.k("harian.kota.calon"), args, EntriHarian.Suasana.SENANG, true);
			}
			case ASMARA -> {
				int look = (int) (town ^ lead0.getLeastSignificantBits());
				boolean she = !r.anggota(lead0).getBooleanOr("perempuan", false);
				args.addAll(JalanIds.orang(look, she));
				r.hasil().putBoolean("asmara", true);
				r.hasil().putString("kenalanHati", Integer.toHexString(look) + (she ? "p" : "l"));
				r.kenangan("mandiri.kenalan_hati");
				r.kisah(JalanIds.k("kisah.masalah.asmara"), args);
				Tahap.catat(k, lead0, "kota.asmara", JalanIds.k("harian.kota.asmara"), args, EntriHarian.Suasana.SENANG, true);
			}
			case MABUK -> {
				long lost = Math.min(r.sisaUang(lead0), 5L + rnd.nextInt(11));
				r.ubahUang(lead0, -lost);
				args.add(String.valueOf(lost));
				r.geser(k.index() + 1 < r.jumlahTahap() ? k.index() + 1 : k.index(), 6000L);
				Kepulangan.aturPulang(k.server(), r);
				r.kisah(JalanIds.k("kisah.masalah.mabuk"), args);
				Tahap.catat(k, lead0, "kota.mabuk", JalanIds.k("harian.kota.mabuk"), args, EntriHarian.Suasana.LELAH, false);
				r.hasil().putString("adegan", "mabuk");
			}
		}
	}

	/** The black market's raid (8 %): a fine of {@code 20 x band} NB, or the guards hold the disciple. */
	static void razia(Tahap.Konteks k, long town, List<String> base) {
		Rencana r = k.r();
		if (k.rnd().nextFloat() >= 0.08F) {
			return;
		}
		UUID lead = r.soul();
		long fine = 20L * Math.max(1, r.anggota(lead).getIntOr("band", 0));
		namaBaik(k, town, "razia", -2);
		List<String> args = new ArrayList<>(base);
		args.add(String.valueOf(fine));
		if (r.sisaUang(lead) >= fine) {
			r.ubahUang(lead, -fine);
			r.kisah(JalanIds.k("kisah.masalah.razia.denda"), args);
			Tahap.catat(k, lead, "kota.razia", JalanIds.k("harian.kota.razia.denda"), args, EntriHarian.Suasana.SEDIH, true);
		} else {
			r.kisah(JalanIds.k("kisah.masalah.razia.tahan"), args);
			Tawanan.tangkap(k, "razia", KontrakKota.tiba(k.level(), town));
		}
	}

	/** C39.K3: the master's Good Name in the town, with the bus event 38KS may hear. */
	private static void namaBaik(Tahap.Konteks k, long town, String jenis, int delta) {
		Rencana r = k.r();
		if (town != 0L && Towns.centre(k.server(), town) != null) {
			KotaData.get(k.server()).addReputation(town, r.owner(), delta);
		}
		MandiriBus.kotaPeristiwa(k.server(), r.owner(), r.soul(), town, jenis, delta);
	}

	/** The name of the master's sect for a rumour ("Sekte Awan Biru"), or a plain placeholder. */
	private static String namaAula(Tahap.Konteks k) {
		dev.kultivator.sect.hall.Halls.HallView hall = dev.kultivator.sect.hall.Halls.hallOf(k.r().owner());
		return hall != null && hall.name() != null && !hall.name().isBlank() ? hall.name() : "-";
	}

	/** A slum district of the capital or the Water City doubles the pickpockets. */
	private static boolean kumuh(Tahap.Konteks k, long town) {
		if (town != 0L && Towns.JENIS_KOTA_TEMA.equals(Towns.jenis(k.server(), town))) {
			dev.kultivator.dunia.DuniaSites.Site site = KontrakKota.situs(k.level(), town);
			if (site != null && site.kind().endsWith("/air")) {
				return true;
			}
		}
		TempatInfo info = KontrakKota.tempat(k.level(), k.pos());
		return info != null && info.distrik() != null && info.distrik().contains("kumuh");
	}

	/** The few numbers of the leader's gate snapshot this table reads. */
	private static final class CompoundTagView {
		final int sifat;
		final int band;

		CompoundTagView(net.minecraft.nbt.CompoundTag tag) {
			this.sifat = tag.getIntOr("sifat", 0);
			this.band = tag.getIntOr("band", 0);
		}
	}

	private MasalahKota() {
	}
}
