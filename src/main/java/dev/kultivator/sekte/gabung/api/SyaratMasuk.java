package dev.kultivator.sekte.gabung.api;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.kotabesar.Hukum;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarData;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.ArrayList;
import java.util.List;

/**
 * The one source of entrance requirements (DESIGN 41.4.4), used by the Lamaran and Direktori screens and by
 * {@code /kultivasi gabung lamar}. Each {@link Baris} is one tick-box line: passed or not, its lang key
 * ({@code kultivator.gabung.syarat.<what>}) and the arguments to show. {@link #lolos} is true when every line passed.
 * <p>
 * Per class: minimum realm, minimum spiritual root, maximum Bone Age ({@code Stats.PLAY_TIME / 24000}, the number of the
 * Mirror of Karma, {@code base + step x excess realm ordinals}), minimum standing (the one per-player number of
 * Diplomasi, R7), Infami per alignment ({@code TemaApi.infami}, no second karma score) and the worst capital crime
 * record ({@code Hukum}). Doors waive parts (41.5.2): Token the Bone Age, Undangan roots and Bone Age, Pelayan roots and
 * Bone Age (Agung/Abadi still Qi Refining) with Infami Lurus at most 30; Tamu has its own (hall level 3 or a realm one
 * below the master's, standing 20). A clean player applying to a demonic sect passes the Infami line through the Ujian
 * Darah when its style test would lift Infami (+10) to the minimum. General lines first: already a member, open
 * application, application pause, past membership, R11 (war with the own hall), the config switches.
 */
public final class SyaratMasuk {

	/** One requirement line. */
	public record Baris(boolean ok, String kunci, Object[] isian) {

		/** The line as text ({@code kultivator.gabung.syarat.<kunci>}), green when passed, red when not. */
		public MutableComponent teks() {
			return Component.translatable(PREFIX + kunci, isian).withColor(ok ? 0x7FD67F : 0xE06060);
		}
	}

	public static final String PREFIX = "kultivator.gabung.syarat.";

	/** Bone Age limits per class (index nomor-1): base days and days per excess realm ordinal; Kecil has none (-1). */
	private static final int[] USIA_DASAR = {-1, 120, 80, 60, 45};
	private static final int[] USIA_PER = {0, 60, 50, 40, 30};
	/** Minimum standing per class. */
	private static final int[] STANDING_MIN = {-30, -10, 0, 10, 20};
	/** Infami limits per class: righteous max, neutral max, demonic min. */
	private static final int[] INFAMI_LURUS = {40, 30, 20, 15, 10};
	private static final int[] INFAMI_NETRAL = {70, 60, 50, 40, 30};
	private static final int[] INFAMI_IBLIS = {0, 5, 10, 20, 30};
	/** The Ujian Darah's Infami gain (41.4.4). */
	public static final int INFAMI_UJIAN_DARAH = 10;
	/** Infami Lurus limit of the Pelayan door. */
	public static final int INFAMI_PELAYAN = 30;

	/** Every line for {@code player} applying to {@code sekte} through {@code pintu}. */
	public static List<Baris> periksa(ServerPlayer player, String sekte, Pintu pintu) {
		List<Baris> out = new ArrayList<>();
		MinecraftServer server = player.level().getServer();
		DiplomasiService d = SekteApi.diplomasi();
		int tier = d.tier(sekte);
		int aliran = d.aliran(sekte);
		if (server == null || tier <= 0 || aliran < 0) {
			out.add(new Baris(false, "sekte_tak_dikenal", new Object[]{sekte}));
			return out;
		}
		GabungConfig cfg = GabungConfig.get();
		KelasSekte kelas = KelasSekte.dariTier(tier);
		Corak corak = GabungApi.corak(sekte);
		long hari = Kota.day(server);
		Keanggotaan ada = GabungData.get(server).peek(player.getUUID());
		Keanggotaan k = ada == null ? new Keanggotaan() : ada;
		Component nama = namaSekte(d, sekte);
		// 41.7.2: expelled from a sect of the same path within 30 days = judged as one class higher
		if (pintu != Pintu.TAMU && diusirSeAliran(k, aliran, hari, d) && kelas.nomor() < KelasSekte.ABADI.nomor()) {
			kelas = KelasSekte.byNomor(kelas.nomor() + 1);
			out.add(new Baris(true, "diusir_kelas", new Object[]{kelas.nama()}));
		}

		umum(out, player, k, sekte, nama, pintu, hari, cfg, d);
		if (pintu == Pintu.TAMU) {
			tamu(out, player, tier, d, sekte);
			return out;
		}

		CultivationData cd = Cultivators.get(player);
		Rank rank = cd.rank();
		Realm ranah = rank.realm();
		RootGrade akar = cd.rootGrade();

		// realm (Pelayan: Fana, Agung/Abadi Pemurnian Qi)
		Realm minRanah = ranahMin(kelas, pintu);
		boolean ranahOk = ranah.ordinal() > minRanah.ordinal() || ranah == minRanah && tahapOk(kelas, pintu, rank, akar);
		if (!ranahOk && kelas == KelasSekte.ABADI && pintu != Pintu.PELAYAN && ranah == Realm.FOUNDATION && akar == RootGrade.HEAVENLY) {
			ranahOk = true;
		}
		out.add(new Baris(ranahOk, kunciRanah(kelas, pintu), new Object[]{minRanah.displayName(), rank.displayName()}));

		// spiritual root
		if (!pintu.tanpaAkar()) {
			RootGrade minAkar = akarMin(kelas);
			out.add(new Baris(akar.ordinal() >= minAkar.ordinal(), "akar", new Object[]{minAkar.displayName(), akar.displayName()}));
		}

		// Bone Age
		int batas = batasUsiaTulang(kelas, ranah);
		if (cfg.usiaTulang && !pintu.tanpaUsiaTulang() && batas >= 0) {
			int usia = usiaTulang(player);
			out.add(new Baris(usia <= batas, "usia_tulang", new Object[]{usia, batas}));
		}

		// standing (one number per player per sect, R7)
		int standing = d.standing(player.getUUID(), sekte);
		int minStanding = STANDING_MIN[kelas.nomor() - 1];
		out.add(new Baris(standing >= minStanding, "standing", new Object[]{standing, minStanding}));

		// Infami (TemaApi, one value)
		if (cfg.syaratInfami) {
			infami(out, player, kelas, aliran, corak, pintu);
		}

		// crime record of the known capitals
		int buruk = kejahatanTerburuk(server, player);
		int tolak = tolakKejahatan(kelas, aliran);
		if (tolak > 0) {
			out.add(new Baris(buruk < tolak, "kejahatan", new Object[]{Component.translatable(PREFIX + "kejahatan." + buruk),
					Component.translatable(PREFIX + "kejahatan." + tolak)}));
		}
		return out;
	}

	/** Days an expulsion weighs on applications to sects of the same path (41.7.2). */
	public static final int DIUSIR_BERAT_HARI = 30;

	/** True when the player was expelled from a sect of path {@code aliran} within {@link #DIUSIR_BERAT_HARI} days. */
	public static boolean diusirSeAliran(Keanggotaan k, int aliran, long hari, DiplomasiService d) {
		for (java.util.Map.Entry<String, Keanggotaan.Riwayat> e : k.riwayatSekte.entrySet()) {
			Keanggotaan.Riwayat r = e.getValue();
			if (r.status() == StatusKeluar.DIUSIR && hari - r.hari() < DIUSIR_BERAT_HARI && d.aliran(e.getKey()) == aliran) {
				return true;
			}
		}
		return false;
	}

	/** True when every line passed. */
	public static boolean lolos(List<Baris> baris) {
		for (Baris b : baris) {
			if (!b.ok()) {
				return false;
			}
		}
		return !baris.isEmpty();
	}

	/** The player's Bone Age: world days lived in this world ({@code Stats.PLAY_TIME / 24000}). */
	public static int usiaTulang(ServerPlayer player) {
		return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 24000;
	}

	/** The Bone Age limit of {@code kelas} for a player of {@code ranah} (-1 = none). */
	public static int batasUsiaTulang(KelasSekte kelas, Realm ranah) {
		int dasar = USIA_DASAR[kelas.nomor() - 1];
		if (dasar < 0) {
			return -1;
		}
		int lebih = Math.max(0, ranah.ordinal() - ranahMin(kelas, Pintu.GERBANG).ordinal());
		return dasar + USIA_PER[kelas.nomor() - 1] * lebih;
	}

	/** The minimum realm of {@code kelas} through {@code pintu}. */
	public static Realm ranahMin(KelasSekte kelas, Pintu pintu) {
		if (pintu == Pintu.PELAYAN) {
			return kelas.nomor() >= KelasSekte.AGUNG.nomor() ? Realm.QI_REFINING : Realm.MORTAL;
		}
		return switch (kelas) {
			case KECIL -> Realm.MORTAL;
			case MENENGAH, BESAR -> Realm.QI_REFINING;
			case AGUNG -> Realm.FOUNDATION;
			case ABADI -> Realm.GOLDEN_CORE;
		};
	}

	/** The minimum spiritual root of {@code kelas}. */
	public static RootGrade akarMin(KelasSekte kelas) {
		return switch (kelas) {
			case KECIL -> RootGrade.TRASH;
			case MENENGAH -> RootGrade.LOW;
			case BESAR, AGUNG -> RootGrade.MID;
			case ABADI -> RootGrade.HIGH;
		};
	}

	/**
	 * The worst crime level 0-3 ({@code Hukum.tingkat}: clean, suspected, wanted, heavily wanted) over every capital the
	 * player has a record in.
	 */
	public static int kejahatanTerburuk(MinecraftServer server, ServerPlayer player) {
		KotaBesarData data = KotaBesarData.get(server);
		int worst = 0;
		for (long town : data.kotaIds()) {
			worst = Math.max(worst, Hukum.tingkat(data.kejahatan(town, player.getUUID())));
		}
		return worst;
	}

	// ================================================================== parts

	private static void umum(List<Baris> out, ServerPlayer player, Keanggotaan k, String sekte, Component nama, Pintu pintu, long hari,
			GabungConfig cfg, DiplomasiService d) {
		out.add(new Baris(cfg.aktif, "aktif", new Object[0]));
		if (k.status.anggota()) {
			boolean sama = sekte.equals(k.sekte);
			out.add(new Baris(false, sama ? "sudah_anggota" : "anggota_lain", new Object[]{namaSekte(d, k.sekte)}));
		} else if (k.status == StatusAnggota.CALON && !sekte.equals(k.sekte)) {
			out.add(new Baris(false, "lamaran_lain", new Object[]{namaSekte(d, k.sekte)}));
		}
		Long jeda = k.jedaLamar.get(sekte);
		if (jeda != null && jeda > hari) {
			out.add(new Baris(false, "jeda", new Object[]{nama, jeda - hari}));
		}
		Keanggotaan.Riwayat lalu = k.riwayatSekte.get(sekte);
		if (lalu != null) {
			int tunggu = lalu.status().jedaHari();
			if (tunggu < 0) {
				out.add(new Baris(false, "pembelot", new Object[]{nama}));
			} else if (hari - lalu.hari() < tunggu) {
				out.add(new Baris(false, "riwayat", new Object[]{lalu.status().nama(), tunggu - (hari - lalu.hari())}));
			}
		}
		if (d.atWar(player.getUUID(), sekte)) {
			out.add(new Baris(false, "perang", new Object[]{nama}));
		}
		if (pintu == Pintu.TAMU && !cfg.bolehTamu) {
			out.add(new Baris(false, "tamu_mati", new Object[0]));
		}
	}

	private static void tamu(List<Baris> out, ServerPlayer player, int tier, DiplomasiService d, String sekte) {
		int level = Halls.level(player.getUUID());
		Realm butuh = Realm.values()[Math.max(0, KelasSekte.ranahKetua(tier).ordinal() - 1)];
		Realm ranah = Cultivators.get(player).realm();
		boolean ok = level >= 3 || ranah.ordinal() >= butuh.ordinal();
		out.add(new Baris(ok, "tamu_syarat", new Object[]{level, butuh.displayName()}));
		int standing = d.standing(player.getUUID(), sekte);
		out.add(new Baris(standing >= 20, "standing", new Object[]{standing, 20}));
	}

	private static boolean tahapOk(KelasSekte kelas, Pintu pintu, Rank rank, RootGrade akar) {
		if (pintu == Pintu.PELAYAN) {
			return true;
		}
		return switch (kelas) {
			// Besar: Pemurnian Qi Akhir (stage Late or Peak)
			case BESAR -> rank.stage() >= 2;
			// Agung: Fondasi; its Early stage needs a HIGH root
			case AGUNG -> rank.stage() >= 1 || akar.ordinal() >= RootGrade.HIGH.ordinal();
			default -> true;
		};
	}

	private static String kunciRanah(KelasSekte kelas, Pintu pintu) {
		if (pintu == Pintu.PELAYAN) {
			return "ranah";
		}
		return switch (kelas) {
			case BESAR -> "ranah_akhir";
			case AGUNG -> "ranah_agung";
			case ABADI -> "ranah_abadi";
			default -> "ranah";
		};
	}

	private static void infami(List<Baris> out, ServerPlayer player, KelasSekte kelas, int aliran, Corak corak, Pintu pintu) {
		int infami = TemaApi.infami(player);
		int i = kelas.nomor() - 1;
		switch (aliran) {
			case 0 -> {
				int maks = pintu == Pintu.PELAYAN ? Math.max(INFAMI_LURUS[i], INFAMI_PELAYAN) : INFAMI_LURUS[i];
				out.add(new Baris(infami <= maks, "infami_lurus", new Object[]{infami, maks}));
			}
			case 1 -> out.add(new Baris(infami <= INFAMI_NETRAL[i], "infami_netral", new Object[]{infami, INFAMI_NETRAL[i]}));
			default -> {
				int min = INFAMI_IBLIS[i];
				if (infami >= min) {
					out.add(new Baris(true, "infami_iblis", new Object[]{infami, min}));
				} else {
					boolean darah = corak.ujianDarah() && pintu.jalani(2) && infami + INFAMI_UJIAN_DARAH >= min;
					out.add(new Baris(darah, darah ? "infami_darah" : "infami_iblis", new Object[]{infami, min, corak.namaUjian()}));
				}
			}
		}
	}

	/** The crime level from which the sect refuses (0 = it does not care): 41.4.4 last column. */
	private static int tolakKejahatan(KelasSekte kelas, int aliran) {
		if (aliran == 2) {
			return 0;
		}
		return switch (kelas) {
			case KECIL -> aliran == 0 ? 3 : 0;
			case MENENGAH -> aliran == 0 ? 2 : 0;
			case BESAR -> 2;
			case AGUNG, ABADI -> aliran == 0 ? 1 : 0;
		};
	}

	private static Component namaSekte(DiplomasiService d, String sekte) {
		Component name = sekte == null || sekte.isEmpty() ? null : d.name(sekte);
		return name == null ? Component.literal(sekte == null ? "?" : sekte) : name;
	}

	private SyaratMasuk() {
	}
}
