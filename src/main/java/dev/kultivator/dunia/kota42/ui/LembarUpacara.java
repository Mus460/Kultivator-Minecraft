package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota42.acara.KalenderKota;
import dev.kultivator.dunia.kota42.acara.KotaAcaraData;
import dev.kultivator.dunia.kota42.acara.Rombongan;
import dev.kultivator.dunia.kota42.acara.Upacara;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The two small event screens of DESIGN 42.11.14 (keys {@link KotaLembar.Upacara} and {@link KotaLembar.Rombongan}).
 * <ul>
 * <li>{@code upacara}: the invitation card of a ceremony of the town, read from 42D-P's {@link Upacara#daftar} (the name with
 * the people, day and hour, the phase the procession is in, the countdown, the family with their part, where it is held,
 * whether the player stands there) and the gift envelope (angpao at joy, condolence money at a funeral; the limits and the
 * giving are {@link Upacara#batasBeri} and {@link Upacara#beri}: once per ceremony, while it runs). The selection of
 * {@code LembarKotaDasar.setPilih} (a ceremony id) wins over "the soonest".</li>
 * <li>{@code rombongan}: a troupe staying in town now ({@link Rombongan#semuaDiKota}; the stay from {@link Rombongan#lokasi}):
 * who they are, the leader, arrival, leaving, the next town, the shows with the running one marked, the twelve stories
 * of Lao Shuo or the three songs of Ah Qin the player heard, and the tip. The tip and the song request go to the module that
 * runs the troupe through {@link AksiRombongan} when it registered one, else to a plain version here.</li>
 * </ul>
 * Ops: {@code beri:NB}, {@code tip:NB}, {@code minta:LAGU}. 42D marks what a player heard through {@link #kisahDidengar} and
 * {@link #laguDidengar}.
 */
public final class LembarUpacara {

	private static final String K = LembarKotaDasar.K + "upacara.";
	private static final String KR = LembarKotaDasar.K + "rombongan.";
	private static final String BAGIAN_DENGAR = "dengar";

	/** The troupes with a screen (the wedding musicians of Suona have none), in the order of DESIGN 42.7.1. */
	public static final List<String> ROMBONGAN = List.of(Rombongan.BULAN_SABIT, Rombongan.LAO_SHUO, Rombongan.AH_QIN, Rombongan.HU_LU, Rombongan.MA_FU);
	/** Ah Qin's three erhu songs (ids of {@code Lagu.LAGU_AH_QIN}). */
	public static final List<String> LAGU = List.of("jembatan", "bulan_sungai", "pulang_gugur");

	/** Show hours (clock x 100 + minute) and length in hours of each troupe (the {@code Grup} table of 42D). */
	private static final Map<String, int[]> SESI = Map.of(
			Rombongan.BULAN_SABIT, new int[] {1000, 1500, 1900},
			Rombongan.LAO_SHUO, new int[] {1000, 1500, 2000},
			Rombongan.AH_QIN, new int[] {900, 1600},
			Rombongan.HU_LU, new int[] {1100, 1600},
			Rombongan.MA_FU, new int[] {900});
	private static final Map<String, Double> LAMA_JAM = Map.of(Rombongan.BULAN_SABIT, 3.6D, Rombongan.LAO_SHUO, 3.6D, Rombongan.AH_QIN, 3.0D,
			Rombongan.HU_LU, 1.8D, Rombongan.MA_FU, 8.0D);

	/** The module that runs the troupes answers a tip and a song request itself (true = it was given). */
	public interface AksiRombongan {
		boolean tip(ServerPlayer player, long situs, String rombongan, long nb);

		boolean minta(ServerPlayer player, long situs, String rombongan, String lagu);
	}

	private static final List<AksiRombongan> AKSI = new CopyOnWriteArrayList<>();

	public static void daftarAksi(AksiRombongan aksi) {
		AKSI.add(aksi);
	}

	static void init() {
		LembarKotaDasar.daftar(KotaLembar.Upacara.LAYAR, LembarUpacara::bangunUpacara);
		LembarKotaDasar.bawaan(KotaLembar.Upacara.LAYAR, KotaLembar.Upacara.OP_BERI, (p, npc, situs, op) -> beri(p, situs, LembarKunci.argLong(op, 1, 0L)));
		LembarKotaDasar.daftar(KotaLembar.Rombongan.LAYAR, LembarUpacara::bangunRombongan);
		LembarKotaDasar.bawaan(KotaLembar.Rombongan.LAYAR, KotaLembar.Rombongan.OP_TIP, (p, npc, situs, op) -> tip(p, situs, LembarKunci.argLong(op, 1, 0L)));
		LembarKotaDasar.bawaan(KotaLembar.Rombongan.LAYAR, KotaLembar.Rombongan.OP_MINTA, (p, npc, situs, op) -> minta(p, situs, LembarKunci.arg(op, 1)));
	}

	private static BlockPos pusat(MinecraftServer server, long situs, ServerPlayer player) {
		TempatInfo info = PajakKota.tempat(player);
		if (info != null && info.situsId() == situs) {
			return info.kotak().getCenter();
		}
		DuniaSites.Site site = DuniaSites.byId(server, situs);
		return site != null ? site.pos() : player.blockPosition();
	}

	// ================================================================== upacara

	private static @Nullable Upacara.Info pilih(ServerPlayer player, MinecraftServer server, long situs) {
		List<Upacara.Info> semua = Upacara.daftar(server, situs);
		String dipilih = LembarKotaDasar.pilih(player, KotaLembar.Upacara.LAYAR);
		for (Upacara.Info i : semua) {
			if (Integer.toString(i.id()).equals(dipilih)) {
				return i;
			}
		}
		return semua.isEmpty() ? null : semua.get(0);
	}

	static @Nullable CompoundTag bangunUpacara(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(K + "judul"), LembarKunci.GAYA_KAIN);
		LembarKotaDasar.kepala(tag, player, situs);
		Upacara.Info info = situs == 0L ? null : pilih(player, server, situs);
		if (info == null) {
			return tag;
		}
		BlockPos at = info.tempat() != null ? info.tempat() : pusat(server, situs, player);
		boolean sudah = Upacara.sudahBeri(player, situs, info.id());

		CompoundTag u = new CompoundTag();
		u.putString(LembarKunci.ID, Integer.toString(info.id()));
		u.putString(LembarKunci.JENIS, info.jenis().id());
		LembarKunci.teks(u, LembarKunci.NAMA, info.nama());
		u.putLong(LembarKunci.HARI, info.hari());
		u.putInt(KotaLembar.JAM, info.jam());
		u.putInt(KotaLembar.MENIT, info.menit());
		LembarKunci.teks(u, LembarKunci.TEMPAT, PajakKota.nama(server, situs));
		u.putInt(LembarKunci.X, at.getX());
		u.putInt(LembarKunci.Z, at.getZ());
		u.putString(KotaLembar.Upacara.FASE, info.fase());
		if (info.sisaTick() > 0L) {
			u.putLong(LembarKunci.SISA_TICK, info.sisaTick());
		}
		List<CompoundTag> keluarga = new ArrayList<>();
		for (Upacara.Keluarga f : info.keluarga()) {
			CompoundTag t = new CompoundTag();
			LembarKunci.teks(t, LembarKunci.NAMA, f.nama());
			t.putInt(KotaLembar.WARGA, f.warga());
			LembarKunci.teks(t, LembarKunci.PERAN, f.peran());
			keluarga.add(t);
			if (keluarga.size() >= 6) {
				break;
			}
		}
		u.put(KotaLembar.Upacara.KELUARGA, LembarKotaDasar.daftar(keluarga));
		u.putBoolean(KotaLembar.Upacara.HADIR, Upacara.hadir(player, situs, info.id()));
		u.putBoolean(KotaLembar.Upacara.SUDAH_BERI, sudah);
		tag.put(KotaLembar.Upacara.UNDANGAN, u);

		long[] batas = Upacara.batasBeri(player);
		CompoundTag beri = new CompoundTag();
		beri.putString(LembarKunci.JENIS, info.jenis() == JenisUpacara.MAKAM ? KotaLembar.Upacara.BE_DUKA : KotaLembar.Upacara.BE_ANGPAO);
		beri.putLong(KotaLembar.Upacara.MIN, batas[0]);
		beri.putLong(KotaLembar.Upacara.MAKS, batas[1]);
		beri.putLong(KotaLembar.Upacara.SARAN, batas[0] * (info.jenis() == JenisUpacara.NIKAH ? 3L : 2L));
		Component alasan = sudah ? Component.translatable(K + "beri.sudah")
				: !info.berlangsung() ? Component.translatable(K + "beri.belum") : null;
		beri.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(beri, LembarKunci.ALASAN, alasan);
		}
		tag.put(KotaLembar.Upacara.BERI, beri);
		return tag;
	}

	private static void beri(ServerPlayer p, long situs, long nb) {
		MinecraftServer server = p.level().getServer();
		Upacara.Info info = situs == 0L ? null : pilih(p, server, situs);
		if (info == null) {
			LembarKotaDasar.pesan(p, Component.translatable(K + "beri.tiada"), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		long[] batas = Upacara.batasBeri(p);
		if (nb < batas[0] || nb > batas[1]) {
			LembarKotaDasar.pesan(p, Component.translatable(K + "beri.jumlah", Barang.nb(batas[0]), Barang.nb(batas[1])), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		if (Upacara.beri(p, situs, info.id(), nb)) {
			LembarKotaDasar.pesan(p, Component.translatable(K + (info.jenis() == JenisUpacara.MAKAM ? "beri.ok_duka" : "beri.ok_angpao"), Barang.nb(nb)),
					LembarKunci.PESAN_OK);
		} else {
			// 42D-P already said why on the action bar (not running, too far, not enough stones)
			LembarKotaDasar.pesan(p, Component.translatable(K + "beri.gagal"), LembarKunci.PESAN_PERINGATAN);
		}
	}

	// ================================================================== rombongan

	/** The troupe the sheet shows: the selection when it stays here, else the first one in town (never Suona's musicians). */
	private static @Nullable String rombongan(MinecraftServer server, long situs, ServerPlayer player) {
		List<String> ada = Rombongan.semuaDiKota(server, situs);
		String dipilih = LembarKotaDasar.pilih(player, KotaLembar.Rombongan.LAYAR);
		if (ada.contains(dipilih) && ROMBONGAN.contains(dipilih)) {
			return dipilih;
		}
		for (String id : ada) {
			if (ROMBONGAN.contains(id)) {
				return id;
			}
		}
		return null;
	}

	static @Nullable CompoundTag bangunRombongan(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(KR + "judul"), LembarKunci.GAYA_KAYU);
		LembarKotaDasar.kepala(tag, player, situs);
		String id = situs == 0L ? null : rombongan(server, situs, player);
		if (id == null) {
			return tag;
		}
		long hari = KalenderKota.hari(server);
		double sekarang = KalenderKota.jamDesimal(server);
		BlockPos pusat = pusat(server, situs, player);

		// the stay: consecutive days of the route that put the troupe in this town
		long tiba = hari;
		while (hari - tiba < 6L && Rombongan.lokasi(server, id, tiba - 1L) == situs) {
			tiba--;
		}
		long pergi = hari;
		while (pergi - hari < 6L && Rombongan.lokasi(server, id, pergi + 1L) == situs) {
			pergi++;
		}
		long berikutSitus = Rombongan.lokasi(server, id, pergi + 1L);

		CompoundTag r = new CompoundTag();
		r.putString(LembarKunci.ID, id);
		LembarKunci.teks(r, LembarKunci.NAMA, Component.translatable(KR + "nama." + id));
		LembarKunci.teks(r, LembarKunci.JENIS, Component.translatable(KR + "jenis." + id));
		LembarKunci.teks(r, KotaLembar.Rombongan.KETUA, Component.translatable(KR + "ketua." + id));
		r.putLong(KotaLembar.Rombongan.TIBA, tiba);
		r.putLong(KotaLembar.Rombongan.PERGI, pergi);
		if (berikutSitus != 0L && berikutSitus != situs) {
			LembarKunci.teks(r, KotaLembar.Rombongan.BERIKUT, PajakKota.nama(server, berikutSitus));
		}
		List<CompoundTag> sesi = new ArrayList<>();
		double lama = LAMA_JAM.getOrDefault(id, 1.0D);
		for (int hm : SESI.getOrDefault(id, new int[0])) {
			CompoundTag s = new CompoundTag();
			int jam = hm / 100;
			int menit = hm % 100;
			s.putInt(KotaLembar.JAM, jam);
			s.putInt(KotaLembar.MENIT, menit);
			LembarKunci.teks(s, LembarKunci.TEKS, Component.translatable(KR + "sesi." + id));
			LembarKunci.teks(s, LembarKunci.TEMPAT, Component.translatable(KR + "tempat." + id));
			s.putInt(LembarKunci.X, pusat.getX());
			s.putInt(LembarKunci.Z, pusat.getZ());
			double mulai = jam + menit / 60.0D;
			s.putBoolean(KotaLembar.Rombongan.BERLANGSUNG, sekarang >= mulai && sekarang < mulai + lama);
			sesi.add(s);
		}
		r.put(KotaLembar.Rombongan.SESI, LembarKotaDasar.daftar(sesi));
		r.putLong(KotaLembar.Rombongan.TIP_MIN, 1L);
		r.putLong(KotaLembar.Rombongan.TIP_MAKS, 10L * (Barang.pita(player) + 1L));
		tag.put(KotaLembar.Rombongan.ROMBONGAN, r);

		CompoundTag dengar = dengar(server, player);
		if (Rombongan.LAO_SHUO.equals(id)) {
			ListTag didengar = dengar.getListOrEmpty("kisah");
			List<CompoundTag> kisah = new ArrayList<>();
			for (String k : Rombongan.KISAH) {
				CompoundTag t = new CompoundTag();
				t.putString(LembarKunci.ID, k);
				LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(KR + "kisah." + k));
				boolean ada = berisi(didengar, k);
				t.putBoolean(KotaLembar.Rombongan.DIDENGAR, ada);
				if (ada) {
					t.putLong(LembarKunci.HARI, dengar.getCompoundOrEmpty("hari").getLongOr(k, 0L));
				}
				kisah.add(t);
			}
			tag.put(KotaLembar.Rombongan.KISAH, LembarKotaDasar.daftar(kisah));
		} else if (Rombongan.AH_QIN.equals(id)) {
			ListTag didengar = dengar.getListOrEmpty("lagu");
			List<CompoundTag> lagu = new ArrayList<>();
			for (String k : LAGU) {
				CompoundTag t = new CompoundTag();
				t.putString(LembarKunci.ID, k);
				LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(KR + "lagu." + k));
				t.putBoolean(KotaLembar.Rombongan.DIDENGAR, berisi(didengar, k));
				lagu.add(t);
			}
			tag.put(KotaLembar.Rombongan.LAGU, LembarKotaDasar.daftar(lagu));
		}
		return tag;
	}

	private static CompoundTag dengar(MinecraftServer server, ServerPlayer player) {
		return KotaAcaraData.get(server).bagian(BAGIAN_DENGAR).getCompoundOrEmpty(player.getUUID().toString());
	}

	private static boolean berisi(ListTag l, String id) {
		for (Tag t : l) {
			if (t instanceof StringTag s && s.value().equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static void catat(MinecraftServer server, ServerPlayer player, String daftar, String id) {
		CompoundTag semua = KotaAcaraData.get(server).bagian(BAGIAN_DENGAR);
		String key = player.getUUID().toString();
		CompoundTag t = semua.getCompoundOrEmpty(key);
		ListTag l = t.getListOrEmpty(daftar);
		if (!berisi(l, id)) {
			l.add(StringTag.valueOf(id));
			t.put(daftar, l);
			CompoundTag hari = t.getCompoundOrEmpty("hari");
			hari.putLong(id, KalenderKota.hari(server));
			t.put("hari", hari);
			semua.put(key, t);
			KotaAcaraData.get(server).ubah();
		}
	}

	/** 42D: the player listened to story {@code id} of Lao Shuo (an id of {@link Rombongan#KISAH}). */
	public static void kisahDidengar(ServerPlayer player, String id) {
		if (Rombongan.KISAH.contains(id)) {
			catat(player.level().getServer(), player, "kisah", id);
		}
	}

	/** 42D: the player heard song {@code id} of Ah Qin (an id of {@link #LAGU}). */
	public static void laguDidengar(ServerPlayer player, String id) {
		if (LAGU.contains(id)) {
			catat(player.level().getServer(), player, "lagu", id);
		}
	}

	private static void tip(ServerPlayer p, long situs, long nb) {
		MinecraftServer server = p.level().getServer();
		String id = situs == 0L ? null : rombongan(server, situs, p);
		if (id == null) {
			LembarKotaDasar.pesan(p, Component.translatable(KR + "tiada"), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		long maks = 10L * (Barang.pita(p) + 1L);
		if (nb < 1L || nb > maks) {
			LembarKotaDasar.pesan(p, Component.translatable(KR + "tip.jumlah", Barang.nb(maks)), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		boolean diberi = false;
		boolean ditangani = false;
		for (AksiRombongan a : AKSI) {
			ditangani = true;
			diberi |= a.tip(p, situs, id, nb);
		}
		if (!ditangani) {
			diberi = EkonomiApi.bayar(p, nb, "kota42.rombongan.tip");
			if (diberi) {
				// the crowd around the stage notices a generous tip
				WargaApi.peristiwaPemain(p, situs, nb >= maks / 2L ? "hadiah_suka" : "hadiah_biasa");
			}
		}
		if (diberi) {
			LembarKotaDasar.pesan(p, Component.translatable(KR + "tip.ok", Barang.nb(nb), Component.translatable(KR + "nama." + id)), LembarKunci.PESAN_OK);
		} else {
			LembarKotaDasar.pesan(p, Component.translatable("kultivator.kota.kurang_batu", Barang.nb(nb)), LembarKunci.PESAN_BAHAYA);
		}
	}

	private static void minta(ServerPlayer p, long situs, String lagu) {
		MinecraftServer server = p.level().getServer();
		String id = situs == 0L ? null : rombongan(server, situs, p);
		if (id == null || !Rombongan.AH_QIN.equals(id) || !LAGU.contains(lagu)) {
			LembarKotaDasar.pesan(p, Component.translatable(KR + "minta.tiada"), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		boolean diberi = false;
		boolean ditangani = false;
		for (AksiRombongan a : AKSI) {
			ditangani = true;
			diberi |= a.minta(p, situs, id, lagu);
		}
		if (!ditangani) {
			diberi = EkonomiApi.bayar(p, Rombongan.HARGA_KOIN_LAGU, "kota42.rombongan.koin");
		}
		if (!diberi) {
			LembarKotaDasar.pesan(p, Component.translatable("kultivator.kota.kurang_batu", Barang.nb(Rombongan.HARGA_KOIN_LAGU)), LembarKunci.PESAN_BAHAYA);
			return;
		}
		laguDidengar(p, lagu);
		LembarKotaDasar.pesan(p, Component.translatable(KR + "minta.ok", Component.translatable(KR + "lagu." + lagu)), LembarKunci.PESAN_OK);
	}

	private LembarUpacara() {
	}
}
