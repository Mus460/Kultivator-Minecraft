package dev.kultivator.dunia.kota42;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * The seven sections of Kota yang Hidup in {@code config/kultivator-dunia.json} (DESIGN 42.14.1), read through
 * {@code DuniaConfig.section(name, Class, Class::new, Class::clamp)} and re-read in place on {@code DuniaConfig.reload()}.
 * Each section's {@code aktif} only gates behaviour: blocks, items, entities, payloads and advancements of 42 are always
 * registered. Decisions 42.16 are the defaults: K1 {@code hariPerTahun} 2, K2 {@code copetAmbilBarang} false (stones only),
 * K3 {@code kematianUsia} true, K6 {@code serbuanRusakNyata} false, K7 {@code jelaga} true, K15 {@code tampilPeluang} false,
 * K16 {@code sebaranAktif} true (proportional), K17 {@code obrolanTampilJarak} 12 / {@code obrolanMaksTampil} 4.
 * The client toggles ({@code kota42.lentera}, {@code asap}, {@code panji}, {@code petasan}, {@code merpati}, {@code wayang},
 * {@code jelaga}, {@code melodi}) live in {@code config/kultivator-client.json} (42V).
 */
public final class KotaConfig {

	/** Section {@code kotaWarga} (42A residents, 42S spread and variety). */
	public static final class Warga {
		/** 42A switch (off = exactly the 38 behaviour). */
		public boolean aktif = true;
		/** World days per Fana Year (K1, 1-8). */
		public int hariPerTahun = 2;
		/** Residents die of age (K3); off = only retire and move. */
		public boolean kematianUsia = true;
		public int umurMaks = 98;
		/** Birth chance per couple per year. */
		public double peluangLahir = 0.25D;
		public double peluangLamaran = 0.40D;
		/** Moving between towns. */
		public boolean migrasi = true;
		public double rasioPerempuan = 0.5D;
		/** Total chance of a spirit root at birth (the 42.4.2 spread is scaled to it). */
		public double peluangAkar = 0.15D;
		/** Archive entries per place. */
		public int arsipMaks = 400;
		/** Missed days run in full on first touch. */
		public int kejarHari = 60;
		public int ingatanMaks = 6;
		public int hubunganMaks = 8;
		public int gosipMaks = 64;
		/** Story characters do not age (except the ones marked mortal). */
		public boolean tokohAbadi = true;
		/** 42.4A even spread (off = the old Pemanggil order, first titik). */
		public boolean sebaranAktif = true;
		/** Zone cap and floor (42.4A.1). */
		public double kapZona = 1.25D;
		public double lantaiZona = 0.5D;
		/** Distance between bodies that do not interact. */
		public double jarakMin = 1.5D;
		/** Spontaneous turns (42.4A.6). */
		public double belokanPeluang = 0.12D;
		public int belokanMaks = 3;
		/** Micro-break length range in seconds (42.4A.5). */
		public int selinganMin = 8;
		public int selinganMaks = 30;
		/** Penalty for repeating yesterday's activity (42.4A.4). */
		public double ragamPenaltiKemarin = 0.6D;
		/** Family and group walks. */
		public double kelompokPeluang = 0.2D;

		Warga clamp() {
			hariPerTahun = Mth.clamp(hariPerTahun, 1, 8);
			umurMaks = Mth.clamp(umurMaks, 80, 120);
			peluangLahir = jepit(peluangLahir, 0.0D, 0.6D, 0.25D);
			peluangLamaran = jepit(peluangLamaran, 0.0D, 1.0D, 0.40D);
			rasioPerempuan = jepit(rasioPerempuan, 0.2D, 0.8D, 0.5D);
			peluangAkar = jepit(peluangAkar, 0.02D, 0.5D, 0.15D);
			arsipMaks = Mth.clamp(arsipMaks, 100, 2000);
			kejarHari = Mth.clamp(kejarHari, 10, 365);
			ingatanMaks = Mth.clamp(ingatanMaks, 2, 16);
			hubunganMaks = Mth.clamp(hubunganMaks, 4, 16);
			gosipMaks = Mth.clamp(gosipMaks, 16, 256);
			kapZona = jepit(kapZona, 1.0D, 3.0D, 1.25D);
			lantaiZona = jepit(lantaiZona, 0.0D, 1.0D, 0.5D);
			jarakMin = jepit(jarakMin, 0.8D, 4.0D, 1.5D);
			belokanPeluang = jepit(belokanPeluang, 0.0D, 0.5D, 0.12D);
			belokanMaks = Mth.clamp(belokanMaks, 0, 10);
			selinganMin = Mth.clamp(selinganMin, 4, 120);
			selinganMaks = Mth.clamp(selinganMaks, selinganMin, 120);
			ragamPenaltiKemarin = jepit(ragamPenaltiKemarin, 0.0D, 1.0D, 0.6D);
			kelompokPeluang = jepit(kelompokPeluang, 0.0D, 0.6D, 0.2D);
			return this;
		}
	}

	/** Section {@code kotaBicara} (42B talk, 42K conversation engine). */
	public static final class Bicara {
		public boolean aktif = true;
		/** Dialog for every civilian. */
		public boolean semuaWarga = true;
		/** Days before the same line repeats (reserved: no consumer yet, the 42K anti-repeat rings use {@code antiUlangJam}). */
		public int ulangHari = 3;
		public int permintaanPerHari = 3;
		public int permintaanPerTempat = 6;
		/** Loans (42.5.7). */
		public boolean pinjaman = true;
		/** 42.5A (off = the 38 greeting only). */
		public boolean mesinLanjut = true;
		/** NPC-NPC chats (42.5A.9) and their bubbles (K17). */
		public boolean obrolanNpc = true;
		public int obrolanTampilJarak = 12;
		public int obrolanMaksTampil = 4;
		/** Show the success percentage of a tone (K15: off). */
		public boolean tampilPeluang = false;
		/** Speech tic chance. */
		public double tikPeluang = 0.25D;
		public boolean dialek = true;
		public boolean registerKuno = true;
		public boolean suaraSeru = true;
		public int faktaMaks = 24;
		public double lupaSkala = 1.0D;
		public int antiUlangJam = 24;

		Bicara clamp() {
			ulangHari = Mth.clamp(ulangHari, 0, 14);
			permintaanPerHari = Mth.clamp(permintaanPerHari, 0, 10);
			permintaanPerTempat = Mth.clamp(permintaanPerTempat, 0, 20);
			obrolanTampilJarak = Mth.clamp(obrolanTampilJarak, 4, 32);
			obrolanMaksTampil = Mth.clamp(obrolanMaksTampil, 0, 12);
			tikPeluang = jepit(tikPeluang, 0.0D, 0.6D, 0.25D);
			faktaMaks = Mth.clamp(faktaMaks, 8, 64);
			lupaSkala = jepit(lupaSkala, 0.25D, 4.0D, 1.0D);
			antiUlangJam = Mth.clamp(antiUlangJam, 1, 168);
			return this;
		}
	}

	/** Section {@code kotaEkonomi} (42C). */
	public static final class Ekonomi {
		public boolean aktif = true;
		public boolean rumah = true;
		public int rumahMaks = 4;
		/** Minutes of the house lock cooldown. */
		public int kunciJedaMenit = 30;
		/** Rent and property tax, % per 7 days. */
		public int sewaPersen = 3;
		public int pajakProperti = 1;
		public boolean toko = true;
		/** Player shop sales limit, NB per day x (band + 1). */
		public int tokoBatas = 60;
		public boolean hargaDinamis = true;
		public double hargaMin = 0.6D;
		public double hargaMaks = 1.6D;
		public boolean investasi = true;
		/** NB x (band + 1) per business. */
		public int investasiMaks = 200;
		public boolean gudang = true;
		public boolean kontrak = true;
		public boolean pajak = true;
		public int pajakBawaan = 8;
		public boolean jabatan = true;
		public double gajiSkala = 1.0D;
		public boolean pasarHantu = true;
		public double peluangRazia = 0.10D;
		/** K2: a pickpocket also takes an unused stack (default false: spirit stones only) (reserved: Copet takes stones only). */
		public boolean copetAmbilBarang = false;

		Ekonomi clamp() {
			rumahMaks = Mth.clamp(rumahMaks, 1, 12);
			kunciJedaMenit = Mth.clamp(kunciJedaMenit, 5, 240);
			sewaPersen = Mth.clamp(sewaPersen, 1, 10);
			pajakProperti = Mth.clamp(pajakProperti, 0, 5);
			tokoBatas = Mth.clamp(tokoBatas, 10, 300);
			hargaMin = jepit(hargaMin, 0.3D, 1.0D, 0.6D);
			hargaMaks = jepit(hargaMaks, 1.0D, 3.0D, 1.6D);
			investasiMaks = Mth.clamp(investasiMaks, 50, 1000);
			pajakBawaan = Mth.clamp(pajakBawaan, 5, 20);
			gajiSkala = jepit(gajiSkala, 0.25D, 4.0D, 1.0D);
			peluangRazia = jepit(peluangRazia, 0.0D, 0.5D, 0.10D);
			return this;
		}
	}

	/** Section {@code kotaAcara} (42D town life and events). */
	public static final class Acara {
		public boolean aktif = true;
		/** Ceremonies and events are drawn within this many blocks. */
		public int jarakTampil = 96;
		public boolean rombongan = true;
		/** Reserved: the troupe size comes from {@code Rombongan.GRUP}. */
		public int rombonganJumlah = 6;
		public boolean rutinitasPagi = true;
		public boolean ronda = true;
		public boolean musim = true;
		public boolean tahunBaru = true;
		public boolean upacara = true;
		public boolean distrikMalam = true;
		public boolean peristiwa = true;
		public int peristiwaPerHari = 3;
		public boolean copet = true;
		public boolean perkelahian = true;
		public boolean apiKecil = true;

		Acara clamp() {
			jarakTampil = Mth.clamp(jarakTampil, 48, 160);
			rombonganJumlah = Mth.clamp(rombonganJumlah, 0, 12);
			peristiwaPerHari = Mth.clamp(peristiwaPerHari, 0, 8);
			return this;
		}
	}

	/** Section {@code kotaHewan} (42D town animals). */
	public static final class Hewan {
		public boolean aktif = true;
		public int perTempat = 24;
		public boolean kucing = true;
		public boolean anjing = true;
		public boolean ayam = true;
		public boolean merpati = true;
		public boolean ternak = true;

		Hewan clamp() {
			perTempat = Mth.clamp(perTempat, 0, 64);
			return this;
		}
	}

	/** Section {@code kotaSekte} (42E sects and towns). */
	public static final class Sekte {
		public boolean aktif = true;
		public boolean kantor = true;
		public boolean pakta = true;
		public int paktaMaks = 3;
		/** Days between raids on a protected town. */
		public int serbuanHari = 15;
		public boolean murid = true;
		public boolean cabang = true;

		Sekte clamp() {
			paktaMaks = Mth.clamp(paktaMaks, 1, 8);
			serbuanHari = Mth.clamp(serbuanHari, 5, 60);
			return this;
		}
	}

	/** Section {@code kotaTumbuh} (42F growth and rebuilding). */
	public static final class Tumbuh {
		public boolean aktif = true;
		/** Prosperity threshold for a new plot stage. */
		public int ambang = 55;
		/** Plots building at once per place. */
		public int serentak = 1;
		public double kecepatan = 1.0D;
		public boolean bangunKembali = true;
		/** Real damage (blocks) that makes a rebuild project. */
		public int ambangProyek = 200;
		/** K7 soot marks (client, no blocks). */
		public boolean jelaga = true;
		/** K6 (default false): sect raid elites break real blocks through {@code WorldBreaker} (radius 6, outside the core) (reserved: no raid breaks blocks yet). */
		public boolean serbuanRusakNyata = false;

		Tumbuh clamp() {
			ambang = Mth.clamp(ambang, 20, 95);
			serentak = Mth.clamp(serentak, 1, 4);
			kecepatan = jepit(kecepatan, 0.25D, 4.0D, 1.0D);
			ambangProyek = Mth.clamp(ambangProyek, 50, 2000);
			return this;
		}
	}

	private static volatile Warga warga;
	private static volatile Bicara bicara;
	private static volatile Ekonomi ekonomi;
	private static volatile Acara acara;
	private static volatile Hewan hewan;
	private static volatile Sekte sekte;
	private static volatile Tumbuh tumbuh;
	private static boolean listening;

	/** Reads every section (from {@code KotaHidup.init}); a reload re-reads them in place. */
	static synchronized void load() {
		warga = DuniaConfig.section("kotaWarga", Warga.class, Warga::new, Warga::clamp);
		bicara = DuniaConfig.section("kotaBicara", Bicara.class, Bicara::new, Bicara::clamp);
		ekonomi = DuniaConfig.section("kotaEkonomi", Ekonomi.class, Ekonomi::new, Ekonomi::clamp);
		acara = DuniaConfig.section("kotaAcara", Acara.class, Acara::new, Acara::clamp);
		hewan = DuniaConfig.section("kotaHewan", Hewan.class, Hewan::new, Hewan::clamp);
		sekte = DuniaConfig.section("kotaSekte", Sekte.class, Sekte::new, Sekte::clamp);
		tumbuh = DuniaConfig.section("kotaTumbuh", Tumbuh.class, Tumbuh::new, Tumbuh::clamp);
		if (!listening) {
			listening = true;
			DuniaConfig.onReload(KotaConfig::load);
		}
	}

	public static Warga warga() {
		Warga c = warga;
		if (c == null) {
			load();
			c = warga;
		}
		return c;
	}

	public static Bicara bicara() {
		Bicara c = bicara;
		if (c == null) {
			load();
			c = bicara;
		}
		return c;
	}

	public static Ekonomi ekonomi() {
		Ekonomi c = ekonomi;
		if (c == null) {
			load();
			c = ekonomi;
		}
		return c;
	}

	public static Acara acara() {
		Acara c = acara;
		if (c == null) {
			load();
			c = acara;
		}
		return c;
	}

	public static Hewan hewan() {
		Hewan c = hewan;
		if (c == null) {
			load();
			c = hewan;
		}
		return c;
	}

	public static Sekte sekte() {
		Sekte c = sekte;
		if (c == null) {
			load();
			c = sekte;
		}
		return c;
	}

	public static Tumbuh tumbuh() {
		Tumbuh c = tumbuh;
		if (c == null) {
			load();
			c = tumbuh;
		}
		return c;
	}

	private static double jepit(double value, double min, double max, double fallback) {
		return Double.isNaN(value) ? fallback : Mth.clamp(value, min, max);
	}

	private KotaConfig() {
	}
}
