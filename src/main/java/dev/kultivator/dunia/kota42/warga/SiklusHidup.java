package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.tumbuh.Kemakmuran;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Marrying, being born, growing old and dying (DESIGN 42.4.3, 42.4.4, 42.4.6). Each living resident rolls its year once
 * per Fana Year, on its own birthday (births are spread by {@link Warga#lahir}, so the year's events fall evenly over the
 * days), in the design's order: proposal, matchmaking, birth, adoption, remarriage, sickness, recovery, death,
 * retirement. Weddings happen on the betrothal's day ({@link #langkahHari}). Only one man and one woman ever marry (42.16
 * K9); no adoption path joins two people of one sex.
 * <p>
 * Every event fires its {@link KotaBus} event ({@code LAHIR}, {@code NIKAH}, {@code WAFAT}, {@code KERJA_BERUBAH}) so the
 * mood layer (42A-2 {@code Suasana}), the ceremonies (42D {@code Upacara}), the businesses (42C) and the gossip (42K) react
 * without being called from here. A summary day ({@code ringkas}, the catch-up of 42.4.13) keeps only ageing, births,
 * deaths, weddings already set and retirements.
 */
public final class SiklusHidup {

	/** The age in Fana Years on world day {@code hari}. */
	public static int umurPada(Warga w, long hari) {
		long tenths = hari * 10L - w.lahir;
		return (int) Math.max(0L, tenths / (10L * KotaConfig.warga().hariPerTahun));
	}

	/** True when {@code w} has its birthday on world day {@code hari} (its age grows that day). */
	public static boolean ulangTahun(Warga w, long hari) {
		long year = 10L * KotaConfig.warga().hariPerTahun;
		long now = hari * 10L - w.lahir;
		return now > 0L && Math.floorDiv(now, year) != Math.floorDiv(now - 10L, year);
	}

	/** The death hazard per year at (effective) age {@code umur}: 0.2 % below 50, 0.6 % to 59, then 1 % x 1.09 per year. */
	public static double bahaya(double umur) {
		if (umur < 50.0D) {
			return 0.002D;
		}
		if (umur < 60.0D) {
			return 0.006D;
		}
		return 0.01D * Math.pow(1.09D, umur - 60.0D);
	}

	/** The age limit of a realm (mortal {@code kotaWarga.umurMaks}; Qi Refining 120, Foundation 200, Golden Core 500, Nascent Soul 1000). */
	public static int batasUmur(int ranah) {
		return switch (ranah) {
			case 0 -> KotaConfig.warga().umurMaks;
			case 1 -> 120;
			case 2 -> 200;
			case 3 -> 500;
			case 4 -> 1000;
			default -> Integer.MAX_VALUE;
		};
	}

	/** A cultivator ages slower: hazard age = age / this (1, 1, 1.5, 4, 8). */
	public static double pengaliUsia(int ranah) {
		return switch (ranah) {
			case 0, 1 -> 1.0D;
			case 2 -> 1.5D;
			case 3 -> 4.0D;
			default -> 8.0D;
		};
	}

	// ================================================================== the Hari Warga step

	/**
	 * Hari Warga step of place {@code s} on world day {@code hari}: due weddings, then every birthday's yearly rolls.
	 * Counts go to {@code s.stat} ({@code nLahir}, {@code nWafat}, {@code nNikah} totals and {@code hLahir/hWafat/hNikah}
	 * of the day).
	 */
	public static void langkahHari(MinecraftServer server, WargaData data, WargaData.Situs s, long hari, RandomSource random, boolean ringkas) {
		s.stat.putInt("hLahir", 0);
		s.stat.putInt("hWafat", 0);
		s.stat.putInt("hNikah", 0);
		for (Warga w : List.copyOf(s.warga)) {
			if (w.status == Warga.Status.HIDUP && w.pria() && w.tunangan != Warga.TANPA && w.hariNikah <= hari) {
				Warga bride = data.byId(w.tunangan);
				if (bride != null && bride.status == Warga.Status.HIDUP && bride.tunangan == w.id) {
					nikah(server, data, s.id, w, bride, hari, random);
				} else {
					w.tunangan = Warga.TANPA;
				}
			}
		}
		for (Warga w : List.copyOf(s.warga)) {
			if (w.status == Warga.Status.HIDUP && ulangTahun(w, hari)) {
				tahunan(server, data, s, w, umurPada(w, hari), hari, random, ringkas);
			}
		}
	}

	/** The yearly rolls of one resident on its birthday (42.4.6 order). */
	static void tahunan(MinecraftServer server, WargaData data, WargaData.Situs s, Warga w, int umur, long hari, RandomSource random, boolean ringkas) {
		KotaConfig.Warga cfg = KotaConfig.warga();
		long hpt = cfg.hariPerTahun;
		if (umur == 16) {
			// a child the player knew is grown (adv kota42_generasi)
			for (Ingatan i : w.ingatan) {
				ServerPlayer player = online(server, data, i.pemain);
				if (player != null && (i.kenal > 0 || i.nama)) {
					Advancements.grant(player, "tempat/kota42_generasi");
				}
			}
		}
		boolean comblang = adaComblang(s);
		if (!ringkas) {
			// proposal: lovers of 70 or more for a year, both 18-45 and single
			int kekasih = Hubungan.kekasih(w);
			Warga lover = kekasih == Warga.TANPA ? null : data.byId(kekasih);
			if (lover != null && w.lajang() && lover.lajang() && lover.kelamin != w.kelamin && umur >= 18 && umur <= 45) {
				int lu = umurPada(lover, hari);
				long bond = w.hubunganDengan(lover.id);
				int since = (int) ((hari - JenisHubungan.sejak(bond)) & 0xFFFFL);
				if (lu >= 18 && lu <= 45 && JenisHubungan.kekuatan(bond) >= 70 && since >= hpt) {
					double p = cfg.peluangLamaran * (musuhKeluarga(data, s, w, lover) ? 0.3D : 1.0D) * (comblang ? 1.5D : 1.0D);
					if (random.nextDouble() < p) {
						tunangkan(w, lover, hari, random);
					}
				}
			}
			// matchmaking: single 22-35 without a lover
			if (w.lajang() && kekasih == Warga.TANPA && umur >= 22 && umur <= 35 && random.nextDouble() < 0.08D * (comblang ? 1.5D : 1.0D)) {
				Warga jodoh = cariJodoh(data, s, w, 18, 45, hari, random);
				if (jodoh == null && s.warga.size() < KECIL && random.nextDouble() < 0.5D) {
					jodoh = menantuLuar(data, s, w, umur, hari, random);
				}
				if (jodoh != null) {
					Hubungan.ikat(w, jodoh, JenisHubungan.KEKASIH, 72, hari);
					tunangkan(w, jodoh, hari, random);
				}
			}
		}
		// birth (the wife rolls): married, husband alive here, she is 18-42
		if (!w.pria() && w.pasangan != Warga.TANPA && umur >= 18 && umur <= 42) {
			Warga husband = data.byId(w.pasangan);
			if (husband != null && husband.status == Warga.Status.HIDUP && data.situsDari(husband.id) == s.id) {
				double p = cfg.peluangLahir - 0.10D * Math.max(0, w.anak.length - 1);
				if (sesak(s, w.rumahTangga)) {
					p *= 0.5D;
				}
				p *= faktorRuang(s);
				if (Kemakmuran.nilai(s.id) >= 60) {
					p *= 1.2D;
				}
				if (server != null && ArsipWarga.catatan(server).tempat(s.id).getBooleanOr("serbuan", false)) {
					p *= 0.5D;
				}
				if (p > 0.0D && random.nextDouble() < p) {
					lahirkan(server, data, s.id, w, hari, random);
				}
			}
		}
		if (!ringkas) {
			// adoption: a married couple without children takes in an orphan (the wife rolls; a man and a woman only)
			if (!w.pria() && w.pasangan != Warga.TANPA && w.anak.length == 0 && umur < 50 && random.nextDouble() < 0.05D) {
				Warga husband = data.byId(w.pasangan);
				Warga yatim = husband == null ? null : cariYatim(data, s, w.rumahTangga);
				// a mother is at least 17 years older than her child
				if (yatim != null && umur - umurPada(yatim, hari) >= 17) {
					angkat(data, husband, w, yatim);
				}
			}
			// remarriage two Fana Years after the spouse died
			if (w.lajang() && w.jandaHari >= 0L && hari - w.jandaHari >= 2L * hpt && random.nextDouble() < 0.10D) {
				Warga jodoh = cariJodoh(data, s, w, 25, 65, hari, random);
				if (jodoh != null) {
					tunangkan(w, jodoh, hari, random);
				}
			}
			// sickness and recovery
			if (w.kesehatan < 30) {
				if (random.nextDouble() < 0.5D) {
					sembuh(w);
				}
			} else {
				boolean dingin = Math.floorMod(hari / 24L, 4L) == 3L;
				double p = 0.08D + Math.max(0, umur - 60) * 0.005D + (dingin ? 0.04D : 0.0D);
				if (random.nextDouble() < p) {
					w.kesehatan = Mth.clamp(w.kesehatan - 5 - random.nextInt(16) - (umur >= 60 ? 6 : 0), 0, 100);
				} else {
					w.kesehatan = Math.min(umur >= 60 ? 85 : 95, w.kesehatan + 4);
				}
			}
		}
		// death by age
		if (cfg.kematianUsia && !(w.abadi && cfg.tokohAbadi)) {
			int batas = batasUmur(w.ranah);
			double efektif = umur / pengaliUsia(w.ranah);
			double p = bahaya(efektif) * (w.kesehatan < 30 ? 4.0D : 1.0D);
			if (umur >= batas || batas != Integer.MAX_VALUE && random.nextDouble() < p) {
				wafat(server, data, s.id, w, w.kesehatan < 30 ? ArsipWarga.SEBAB_SAKIT : ArsipWarga.SEBAB_USIA, hari);
				return;
			}
		}
		// retirement at 65 (the work slot falls free for 42A-2 Pekerjaan)
		if (umur >= 65 && bekerja(w) && !(w.abadi && cfg.tokohAbadi)) {
			pensiun(server, data, s.id, w);
		}
	}

	/**
	 * Room in the place for more people: the people against the beds of its houses (or the first raising when larger). A
	 * crowded place has fewer births (1 up to 0.95 of the room, 0.25 at 1.2), an emptied one more (up to 1.75), so a town
	 * settles around its houses over the generations instead of swelling or dying out.
	 */
	static double faktorRuang(WargaData.Situs s) {
		CompoundTag kap = s.stat.getCompoundOrEmpty(WargaGen.KAPASITAS);
		int kasur = 0;
		for (String key : kap.keySet()) {
			kasur += Math.max(2, kap.getIntOr(key, 2));
		}
		double ruang = Math.max(kasur, s.stat.getIntOr("awal", 0));
		if (ruang <= 0.0D) {
			return 1.0D;
		}
		double r = s.warga.size() / ruang;
		if (r < 0.7D) {
			return 1.0D + (0.7D - r) * 2.5D;
		}
		return r <= 0.95D ? 1.0D : Math.max(0.15D, 1.0D - (r - 0.95D) * 3.0D);
	}

	/** A place below this many residents finds spouses beyond its walls when it has none to offer. */
	private static final int KECIL = 80;

	/**
	 * A small place has no one left for a single to marry: a man or a woman of the other sex and about the same age comes in
	 * from outside to marry into the house (the household of {@code w}; work follows from 42A-2 {@code Pekerjaan}), so a
	 * village of a dozen does not die out in three generations.
	 */
	private static Warga menantuLuar(WargaData data, WargaData.Situs s, Warga w, int umur, long hari, RandomSource random) {
		int lawan = w.pria() ? Warga.WANITA : Warga.PRIA;
		int min = Math.max(18, umur - 4);
		Warga baru = WargaGen.pendatang(data, NpcRole.WARGA, lawan, min, Math.min(45, Math.max(min, umur + 4)), hari, random);
		baru.rumahTangga = w.rumahTangga;
		baru.rumah = w.rumah;
		Kebiasaan.undiAwal(baru, umurPada(baru, hari), random);
		data.tambah(s.id, baru);
		return baru;
	}

	// ================================================================== events

	/** Betroths two residents (a man and a woman); the wedding follows 1-3 days later. */
	public static boolean tunangkan(Warga a, Warga b, long hari, RandomSource random) {
		if (a.kelamin == b.kelamin || !a.lajang() || !b.lajang()) {
			return false;
		}
		long wedding = hari + 1 + random.nextInt(3);
		a.tunangan = b.id;
		b.tunangan = a.id;
		a.hariNikah = wedding;
		b.hariNikah = wedding;
		return true;
	}

	/**
	 * Marries a man and a woman (42.16 K9: refused for one sex) on world day {@code hari}: spouses, the PASANGAN bond, the
	 * household (the wife joins the husband's 80 %, else he joins hers; a full house sends the young couple to an empty
	 * one of the place), then {@code KotaBus.NIKAH} (the 42D wedding).
	 */
	public static boolean nikah(MinecraftServer server, WargaData data, long situs, Warga pria, Warga wanita, long hari, RandomSource random) {
		if (pria.kelamin == wanita.kelamin || pria.id == wanita.id) {
			return false;
		}
		if (!pria.pria()) {
			Warga t = pria;
			pria = wanita;
			wanita = t;
		}
		pria.pasangan = wanita.id;
		wanita.pasangan = pria.id;
		pria.tunangan = Warga.TANPA;
		wanita.tunangan = Warga.TANPA;
		pria.menikahHari = hari;
		wanita.menikahHari = hari;
		pria.jandaHari = -1L;
		wanita.jandaHari = -1L;
		Hubungan.ikat(pria, wanita, JenisHubungan.PASANGAN, Math.max(70, Hubungan.kekuatan(pria, wanita)), hari);
		WargaData.Situs s = data.situs(situs);
		boolean patrilokal = random.nextInt(100) < 80;
		Warga tuan = patrilokal ? pria : wanita;
		Warga ikut = patrilokal ? wanita : pria;
		if (tuan.rumahTangga != ikut.rumahTangga) {
			int target = tuan.rumahTangga;
			if (target <= 0 || s.rumahTangga(target).size() + 1 > kapasitas(s, target) + 1) {
				int kosong = rumahKosong(s);
				if (kosong > 0) {
					target = kosong;
					pindahRumah(tuan, kosong);
				}
			}
			if (target > 0) {
				pindahRumah(ikut, target);
				if (tuan.rumahTangga != target) {
					pindahRumah(tuan, target);
				}
			}
		}
		catat(s, "nNikah", "hNikah");
		data.ubah();
		KotaBus.NIKAH.invoker().onNikah(server, situs, pria, wanita);
		return true;
	}

	/** Births of {@code ibu} on {@code hari} (twins 2 %); fires {@code KotaBus.LAHIR} for each baby. */
	public static List<Warga> lahirkan(MinecraftServer server, WargaData data, long situs, Warga ibu, long hari, RandomSource random) {
		Warga ayah = ibu.pasangan == Warga.TANPA ? null : data.byId(ibu.pasangan);
		int n = random.nextInt(100) < 2 ? 2 : 1;
		List<Warga> out = new ArrayList<>(n);
		WargaData.Situs s = data.situs(situs);
		for (int i = 0; i < n; i++) {
			Warga bayi = WargaGen.bayi(data, ayah, ibu, hari, random);
			data.tambah(situs, bayi);
			out.add(bayi);
			catat(s, "nLahir", "hLahir");
		}
		for (Warga bayi : out) {
			KotaBus.LAHIR.invoker().onLahir(server, situs, bayi, ibu);
		}
		return out;
	}

	/** Makes a resident sick (health below 30: stays home, 42.5.6 "medicine for mother"). */
	public static void sakit(Warga w, RandomSource random) {
		w.kesehatan = Math.min(w.kesehatan, 10 + random.nextInt(19));
	}

	/** A sick resident recovers (a pill or a paid healer: always). */
	public static void sembuh(Warga w) {
		w.kesehatan = Math.max(w.kesehatan, 62);
	}

	/** Retires a working resident (65): the slot falls free, the role becomes {@code TETUA_DESA}. */
	public static void pensiun(MinecraftServer server, WargaData data, long situs, Warga w) {
		String lama = w.peran;
		w.peran = NpcRole.TETUA_DESA.id();
		w.slot = Warga.TANPA_SLOT;
		data.ubah();
		KotaBus.KERJA_BERUBAH.invoker().onKerjaBerubah(server, situs, w, lama, w.peran);
	}

	/**
	 * A resident dies ({@code sebab}: "usia", "sakit", "serangan"...): the widow(er) may remarry later, debts pass to the
	 * eldest child (else the spouse), children keep half of each memory of a player (42A-2 {@code Ingatan.wariskan}), the
	 * household mourns, orphans go to grandparents, an aunt or uncle, else the temple; then the archive entry with its
	 * gravestone and {@code KotaBus.WAFAT} (the 42D funeral). The resident leaves the data.
	 */
	public static void wafat(MinecraftServer server, WargaData data, long situs, Warga w, String sebab, long hari) {
		if (w.status == Warga.Status.MATI) {
			return;
		}
		WargaData.Situs s = data.situs(situs);
		Warga spouse = w.pasangan == Warga.TANPA ? null : data.byId(w.pasangan);
		if (spouse != null && spouse.pasangan == w.id) {
			spouse.pasangan = Warga.TANPA;
			spouse.jandaHari = hari;
			spouse.hapusHubungan(w.id);
		}
		Warga betrothed = w.tunangan == Warga.TANPA ? null : data.byId(w.tunangan);
		if (betrothed != null && betrothed.tunangan == w.id) {
			betrothed.tunangan = Warga.TANPA;
		}
		for (long bond : w.hubungan) {
			Warga other = data.byId(JenisHubungan.idLain(bond));
			if (other != null) {
				other.hapusHubungan(w.id);
			}
		}
		// debts and memories go to the heirs (adv kota42_cucu: a grandchild inherits what the parent had inherited)
		Warga heir = ahliWaris(data, w);
		for (Ingatan i : w.ingatan) {
			UUID player = data.pemain(i.pemain);
			if (player != null && i.utang != 0 && heir != null) {
				Ingatan.ubahUtang(data, heir, player, i.utang);
			}
			if (i.ingatAkan(Kenangan.CERITA_ORANG_TUA) && i.kesan / 2 != 0 && adaAnakHidup(data, w)) {
				ServerPlayer online = online(server, data, i.pemain);
				if (online != null) {
					Advancements.grant(online, "tempat/kota42_cucu");
				}
			}
		}
		Ingatan.wariskan(data, w, hari);
		s.catatWafat(w.id, hari);
		s.catatDuka(w.rumahTangga, hari);
		// orphans
		for (int c : w.anak) {
			Warga child = data.byId(c);
			if (child == null || child.status != Warga.Status.HIDUP || umurPada(child, hari) >= 13) {
				continue;
			}
			int other = child.ayah == w.id ? child.ibu : child.ayah;
			Warga parent = other == Warga.TANPA ? null : data.byId(other);
			if (parent == null || !parent.hidup()) {
				Warga asuh = pengasuh(data, s, w, parent, hari);
				if (asuh != null && asuh.rumahTangga != child.rumahTangga) {
					pindahRumah(child, asuh.rumahTangga);
					child.rumah = asuh.rumah;
				}
			}
		}
		int nisan = ArsipWarga.nisanBerikut(data, s, s.stat.getIntOr("nisanN", 0));
		ArsipWarga.arsipkan(data, situs, w, sebab, hari, nisan);
		w.status = Warga.Status.MATI;
		w.hariStatus = hari;
		w.slot = Warga.TANPA_SLOT;
		catat(s, "nWafat", "hWafat");
		data.buang(w.id);
		KotaBus.WAFAT.invoker().onWafat(server, situs, w, sebab);
	}

	/** Takes {@code yatim} into the household of a couple (only a man and a woman adopt). */
	public static void angkat(WargaData data, Warga ayah, Warga ibu, Warga yatim) {
		if (ayah.kelamin == ibu.kelamin) {
			return;
		}
		pindahRumah(yatim, ibu.rumahTangga);
		yatim.rumah = ibu.rumah;
		// the family tree follows: the new parents, their family name and the next generation line
		yatim.ayah = ayah.id;
		yatim.ibu = ibu.id;
		yatim.marga = ayah.marga;
		yatim.generasi = ayah.generasi + 1;
		ayah.tambahAnak(yatim.id);
		ibu.tambahAnak(yatim.id);
		data.ubah();
	}

	// ================================================================== helpers

	private static void pindahRumah(Warga w, int rumahTangga) {
		w.rumahTangga = rumahTangga;
		w.rumah = rumahTangga;
	}

	/** Beds of household {@code rt} ({@link WargaGen#KAPASITAS}, at least 2). */
	public static int kapasitas(WargaData.Situs s, int rt) {
		return Math.max(2, s.stat.getCompoundOrEmpty(WargaGen.KAPASITAS).getIntOr(Integer.toString(rt), 2));
	}

	/** True when household {@code rt} holds more people than beds + 1. */
	public static boolean sesak(WargaData.Situs s, int rt) {
		return rt > 0 && s.rumahTangga(rt).size() > kapasitas(s, rt) + 1;
	}

	/** A generated house of the place nobody lives in, or 0. */
	public static int rumahKosong(WargaData.Situs s) {
		CompoundTag kap = s.stat.getCompoundOrEmpty(WargaGen.KAPASITAS);
		for (String key : kap.keySet()) {
			int rt;
			try {
				rt = Integer.parseInt(key);
			} catch (NumberFormatException e) {
				continue;
			}
			if (s.rumahTangga(rt).isEmpty()) {
				return rt;
			}
		}
		return 0;
	}

	private static boolean adaComblang(WargaData.Situs s) {
		for (Warga w : s.warga) {
			if (w.status == Warga.Status.HIDUP && NpcRole.MAK_COMBLANG.id().equals(w.peran)) {
				return true;
			}
		}
		return false;
	}

	/** True when a member of one household is the enemy of a member of the other. */
	private static boolean musuhKeluarga(WargaData data, WargaData.Situs s, Warga a, Warga b) {
		for (Warga x : s.rumahTangga(a.rumahTangga)) {
			for (long bond : x.hubungan) {
				if (JenisHubungan.jenis(bond) == JenisHubungan.MUSUH) {
					Warga y = data.byId(JenisHubungan.idLain(bond));
					if (y != null && y.rumahTangga == b.rumahTangga) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A single of the other sex aged {@code min}-{@code max}, not family, without a lover; the best temperament match of a few. */
	private static @Nullable Warga cariJodoh(WargaData data, WargaData.Situs s, Warga w, int min, int max, long hari, RandomSource random) {
		Warga best = null;
		int bestScore = Integer.MIN_VALUE;
		int seen = 0;
		int start = s.warga.isEmpty() ? 0 : random.nextInt(s.warga.size());
		for (int k = 0; k < s.warga.size() && seen < 6; k++) {
			Warga o = s.warga.get((start + k) % s.warga.size());
			if (o == w || o.kelamin == w.kelamin || !o.lajang() || Hubungan.kekasih(o) != Warga.TANPA || Hubungan.keluarga(data, w, o)) {
				continue;
			}
			int u = umurPada(o, hari);
			if (u < min || u > max) {
				continue;
			}
			seen++;
			int score = Hubungan.cocok(w, o) + Hubungan.cocok(o, w) + Hubungan.kekuatan(w, o) / 10 + random.nextInt(3);
			if (score > bestScore) {
				bestScore = score;
				best = o;
			}
		}
		return best != null && bestScore >= -2 ? best : null;
	}

	/** A child under 13 of the place without a living parent, not of household {@code bukan}. */
	private static @Nullable Warga cariYatim(WargaData data, WargaData.Situs s, int bukan) {
		long hari = s.hariTerakhir;
		for (Warga c : s.warga) {
			if (c.status != Warga.Status.HIDUP || c.rumahTangga == bukan || umurPada(c, Math.max(hari, 0L)) >= 13) {
				continue;
			}
			Warga a = c.ayah == Warga.TANPA ? null : data.byId(c.ayah);
			Warga b = c.ibu == Warga.TANPA ? null : data.byId(c.ibu);
			if ((a == null || !a.hidup()) && (b == null || !b.hidup())) {
				return c;
			}
		}
		return null;
	}

	/** Who raises an orphan: a grandparent, an aunt or uncle, else a temple keeper of the place. */
	private static @Nullable Warga pengasuh(WargaData data, WargaData.Situs s, Warga mati, @Nullable Warga lain, long hari) {
		List<Integer> kakekNenek = new ArrayList<>();
		for (Warga p : new Warga[]{mati, lain}) {
			if (p != null) {
				kakekNenek.add(p.ayah);
				kakekNenek.add(p.ibu);
			}
		}
		for (int id : kakekNenek) {
			Warga g = id == Warga.TANPA ? null : data.byId(id);
			if (g != null && g.status == Warga.Status.HIDUP && data.situsDari(g.id) == s.id) {
				return g;
			}
		}
		for (int id : kakekNenek) {
			Warga g = id == Warga.TANPA ? null : data.byId(id);
			if (g == null) {
				continue;
			}
			for (int c : g.anak) {
				Warga bibi = data.byId(c);
				if (bibi != null && bibi != mati && bibi != lain && bibi.status == Warga.Status.HIDUP && data.situsDari(bibi.id) == s.id
						&& umurPada(bibi, hari) >= 18) {
					return bibi;
				}
			}
		}
		for (Warga r : s.warga) {
			if (r.status == Warga.Status.HIDUP && r.peranNpc().kelompok == NpcRole.Kelompok.RAHIB) {
				return r;
			}
		}
		return null;
	}

	/** The heir: the eldest living child, else the spouse. */
	private static @Nullable Warga ahliWaris(WargaData data, Warga w) {
		Warga best = null;
		for (int c : w.anak) {
			Warga child = data.byId(c);
			if (child != null && child.hidup() && (best == null || child.lahir < best.lahir)) {
				best = child;
			}
		}
		if (best == null && w.pasangan != Warga.TANPA) {
			Warga spouse = data.byId(w.pasangan);
			if (spouse != null && spouse.hidup()) {
				best = spouse;
			}
		}
		return best;
	}

	private static boolean adaAnakHidup(WargaData data, Warga w) {
		for (int c : w.anak) {
			Warga child = data.byId(c);
			if (child != null && child.hidup()) {
				return true;
			}
		}
		return false;
	}

	/** True for a resident in a working role (not a child, an elder or a fixed life). */
	public static boolean bekerja(Warga w) {
		NpcRole.Kelompok k = w.peranNpc().kelompok;
		return w.slot != Warga.TANPA_SLOT && k != NpcRole.Kelompok.ANAK && k != NpcRole.Kelompok.TETUA && k != NpcRole.Kelompok.KHUSUS;
	}

	private static void catat(WargaData.Situs s, String total, String hariIni) {
		s.stat.putInt(total, s.stat.getIntOr(total, 0) + 1);
		s.stat.putInt(hariIni, s.stat.getIntOr(hariIni, 0) + 1);
	}

	static @Nullable ServerPlayer online(MinecraftServer server, WargaData data, int pemain) {
		UUID id = data.pemain(pemain);
		return id == null ? null : server.getPlayerList().getPlayer(id);
	}

	private SiklusHidup() {
	}
}
