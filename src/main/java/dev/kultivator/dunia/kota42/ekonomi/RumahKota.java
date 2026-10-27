package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.BukuKas;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Houses to buy or rent (DESIGN 42.6.1, contract C42.C1). Houses come from the {@code dijual} titik of 42W (data
 * {@code rumah} = the name of the house's {@code bangunan} box titik, {@code ukuran}; the titik stands inside at the door),
 * from 42A's empty households ({@link #tawarkan} with {@code RS_KOSONG}) and 42F's finished plots ({@code RS_KAVLING});
 * they are discovered when the place's pieces are loaded and remembered from then on. One house = the {@code bangunan} box
 * + the door.
 * <ul>
 *   <li><b>Price</b>: kecil 300, sedang 900, besar 2.400, rumah toko 1.800, paviliun 6.000 NB x place factor (kampung 0.5,
 *   pos 0.6, kota 1.0, kota tema 1.3, ibu kota 2.0, palace/cloud district 3.0) x {@code HargaKota.faktor(BANGUNAN)} x
 *   prosperity {@code 0.8 + K/250}. Rent {@code sewaPersen} % of it per 7 days (min 10 NB), paid ahead 1-4 weeks;
 *   property tax {@code pajakProperti} % per 7 days to the town purse, three unpaid weeks seal the house.</li>
 *   <li><b>Rules</b>: Nama Baik 10 to rent, 20 to buy, never while wanted; one house per place, {@code rumahMaks} in all.</li>
 *   <li><b>Claim</b>: an owned, rented or sealed house is a player claim ({@code ProtectedZones.addClaimProvider}): 38.R
 *   never records damage, bills or repairs inside, residents never use its titik ({@code TempatHuni.addPenolakTitik}).</li>
 *   <li><b>Seal</b>: rent ended or sold: 14 days only the player may use it; then every container's contents go to the
 *   house's seized goods (free to take back for 14 days, {@link #ambilSitaan}; afterwards sold for the town purse) and the
 *   house returns to the market ({@code KotaBus.RUMAH_BERUBAH} with {@code null}: 42F's {@code Cetak.terima}).</li>
 *   <li><b>Deed and key</b>: {@code akta_rumah} and {@code kunci_rumah} carry {@code KotaEkonomiItems.RUMAH}; a lost one
 *   is re-printed for 5 NB ({@link #cetakUlang}); the key takes its owner home ({@link KunciRumah}).</li>
 *   <li><b>Household help</b>: an unemployed resident, 8/10/12/15 NB a week by place kind; stands in the house while the
 *   owner is near (service body {@code HidupApi.tubuhStaf}) and brews Teh Rumah (+5 % qi regeneration, 5 minutes).</li>
 * </ul>
 * State: {@code KotaEkonomiData.situs("rumah", situs)}: {@code n} next id, {@code r} id -> house; global
 * {@code bagian("rumah")}: {@code teh} owner -> buff end tick, {@code jeda} owner -> last key use tick.
 */
public final class RumahKota {

	/** Re-print price of a deed or key. */
	public static final long CETAK_ULANG = 5L;
	/** Days a sealed house stays the player's, and days seized goods wait. */
	public static final int SEGEL_HARI = 14;
	/** Unpaid tax weeks that seal a house. */
	public static final int TUNGGAK_SEGEL = 3;
	/** Nama Baik to rent / to buy. */
	public static final int NB_SEWA = 10;
	public static final int NB_BELI = 20;
	/** Sale back to the town: share of the price paid. */
	public static final double JUAL_KEMBALI = 0.5D;
	public static final long SEWA_MIN = 10L;
	public static final int MINGGU_MAKS = 4;
	/** Teh Rumah: qi regeneration factor, length and the pause before the help brews again. */
	public static final double TEH_REGEN = 1.05D;
	public static final int TEH_TICK = 5 * 1200;
	public static final int TEH_JEDA = 20 * 1200;
	/** Blocks within which the help's body stands in the house. */
	public static final double PEMBANTU_JARAK = 48.0D;

	/** {@code KotaLembar.Rumah} status ids (stored as is). */
	public static final String ST_MILIK = "milik";
	public static final String ST_SEWA = "sewa";
	public static final String ST_SEGEL = "segel";

	/**
	 * One house: place, house id (inside the place), owner, {@code jenis} ("beli" / "sewa" / "segel"), the box of the house
	 * and the world day the rent or the seal runs to (0 for a bought house).
	 */
	public record Rumah(long situs, int id, UUID pemilik, String jenis, BoundingBox kotak, long sewaSampai) {
	}

	/**
	 * Everything a screen shows of one house ({@code KotaLembar.Rumah} rows): name of its box, size class (RJ_* id), beds,
	 * box, door and facing, source (RS_*), owner (null on the market), status ("" / milik / sewa / segel), rent paid to,
	 * seal end, unpaid tax weeks, price paid, the hired help (warga id, 0 none).
	 */
	public record Info(long situs, int id, String nama, String ukuran, int kasur, BoundingBox kotak, BlockPos pintu, Direction hadap,
			String sumber, @Nullable UUID pemilik, String status, long sewaSampai, long segelSampai, int tunggak, long hargaDibayar,
			int pembantu) {

		public boolean dijual() {
			return pemilik == null;
		}

		public int lebar() {
			return kotak.getXSpan();
		}

		public int panjang() {
			return kotak.getZSpan();
		}
	}

	/** The outcome of an action: ok and the message for the player (already sent by the action). */
	public record Hasil(boolean ok, Component pesan) {
		static Hasil gagal(String key, Object... args) {
			return new Hasil(false, Component.translatable("kultivator.kota42.rumah.gagal." + key, args));
		}
	}

	private record Klaim(long situs, int id, BoundingBox kotak, UUID pemilik, boolean segel) {
	}

	private static final Map<String, Long> HARGA_DASAR = Map.of("kecil", 300L, "sedang", 900L, "besar", 2400L, "rumah_toko", 1800L,
			"paviliun", 6000L);
	private static volatile @Nullable List<Klaim> klaim;
	/** Household help bodies standing in houses: "situs:id" -> body. */
	private static final Map<String, KultivatorNpc> TUBUH = new HashMap<>();
	private static boolean initialised;

	/** From {@code KotaEkonomi.init}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ProtectedZones.addClaimProvider(RumahKota::diklaim);
		TempatHuni.addPenolakTitik((huni, pos) -> diklaimSitus(huni.situsId, pos));
		Qi.addRegenFactor(player -> tehAktif(player) ? TEH_REGEN : 1.0D);
	}

	static void lupakan() {
		klaim = null;
		TUBUH.values().forEach(KultivatorNpc::vanish);
		TUBUH.clear();
	}

	// ================================================================== reading

	/** Every house {@code pemilik} owns, rents or has sealed (all places). */
	public static List<Rumah> milik(UUID pemilik) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || pemilik == null) {
			return List.of();
		}
		List<Rumah> out = new ArrayList<>();
		for (Info info : semua(server)) {
			if (pemilik.equals(info.pemilik())) {
				out.add(rumah(info));
			}
		}
		return out;
	}

	/** True when {@code pos} lies inside a house a player owns, rents or has sealed. Cheap (claim provider). */
	public static boolean diklaim(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		for (Klaim k : klaim(level.getServer())) {
			if (k.kotak().isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	/** True when {@code pos} of place {@code situs} lies in a claimed house (titik veto). */
	public static boolean diklaimSitus(long situs, BlockPos pos) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return false;
		}
		for (Klaim k : klaim(server)) {
			if (k.situs() == situs && k.kotak().isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	/** The owned, rented or sealed house at {@code pos}, or null. */
	public static @Nullable Rumah rumahDi(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		for (Klaim k : klaim(level.getServer())) {
			if (k.kotak().isInside(pos)) {
				Info info = info(level.getServer(), k.situs(), k.id());
				return info == null ? null : rumah(info);
			}
		}
		return null;
	}

	/** Any known house (also on the market) whose box holds {@code pos}, or null. */
	public static @Nullable Info infoDi(ServerLevel level, BlockPos pos) {
		TempatInfo tempat = TempatApi.tempatDi(level, pos);
		if (tempat == null) {
			return null;
		}
		for (Info info : daftar(level.getServer(), tempat.situsId())) {
			if (info.kotak().isInside(pos)) {
				return info;
			}
		}
		return null;
	}

	/** True when {@code player} owns or rents (not sealed) the house holding {@code pos}. */
	public static boolean milikDi(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Rumah r = rumahDi(level, pos);
		return r != null && r.pemilik().equals(player.getUUID()) && !ST_SEGEL.equals(r.jenis());
	}

	/** One house, or null. */
	public static @Nullable Info info(MinecraftServer server, long situs, int id) {
		CompoundTag t = rumahTag(server, situs, id);
		return t == null ? null : baca(situs, t);
	}

	/** Every known house of a place (scans the loaded pieces first when the place is loaded). */
	public static List<Info> daftar(MinecraftServer server, long situs) {
		ServerLevel level = server.overworld();
		pindai(level, situs);
		List<Info> out = new ArrayList<>();
		CompoundTag r = data(server, situs).getCompoundOrEmpty("r");
		for (String key : r.keySet()) {
			out.add(baca(situs, r.getCompoundOrEmpty(key)));
		}
		out.sort((a, b) -> Integer.compare(a.id(), b.id()));
		return out;
	}

	/** The houses of a place still on the market. */
	public static List<Info> dijual(MinecraftServer server, long situs) {
		List<Info> out = new ArrayList<>();
		for (Info info : daftar(server, situs)) {
			if (info.dijual()) {
				out.add(info);
			}
		}
		return out;
	}

	/** Every known house of every place (no scan). */
	public static List<Info> semua(MinecraftServer server) {
		List<Info> out = new ArrayList<>();
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.RUMAH);
		for (String key : part.keySet()) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			long situs;
			try {
				situs = Long.parseLong(key.substring(5));
			} catch (NumberFormatException e) {
				continue;
			}
			CompoundTag r = part.getCompoundOrEmpty(key).getCompoundOrEmpty("r");
			for (String id : r.keySet()) {
				out.add(baca(situs, r.getCompoundOrEmpty(id)));
			}
		}
		return out;
	}

	/** Houses {@code pemilik} holds in all places (owned, rented or sealed). */
	public static int jumlah(MinecraftServer server, UUID pemilik) {
		int n = 0;
		for (Info info : semua(server)) {
			if (pemilik.equals(info.pemilik())) {
				n++;
			}
		}
		return n;
	}

	/** The house {@code pemilik} holds in {@code situs}, or null. */
	public static @Nullable Info milikDiTempat(MinecraftServer server, long situs, UUID pemilik) {
		CompoundTag r = data(server, situs).getCompoundOrEmpty("r");
		for (String key : r.keySet()) {
			Info info = baca(situs, r.getCompoundOrEmpty(key));
			if (pemilik.equals(info.pemilik())) {
				return info;
			}
		}
		return null;
	}

	/** True when {@code pos} is in a house whose owner is {@code player}'s neighbour: same place, other house (dialog). */
	public static boolean tetangga(ServerPlayer player, long situs) {
		return milikDiTempat(player.level().getServer(), situs, player.getUUID()) != null;
	}

	// ================================================================== prices

	/** The buying price of a house today (NB). */
	public static long harga(MinecraftServer server, Info info) {
		long dasar = HARGA_DASAR.getOrDefault(info.ukuran(), 300L);
		double f = faktorTempat(server, info) * HargaKota.faktor(server, info.situs(), Kategori.BANGUNAN) * HargaKota.kemakmuran(info.situs());
		return Math.max(1L, Math.round(dasar * f));
	}

	/** Rent per 7 days: {@code sewaPersen} % of the price, at least 10 NB. */
	public static long sewa(MinecraftServer server, Info info) {
		return Math.max(SEWA_MIN, Math.round(harga(server, info) * KotaConfig.ekonomi().sewaPersen / 100.0D));
	}

	/** Property tax per 7 days of an owned house: {@code pajakProperti} % of the price paid. */
	public static long pajak(MinecraftServer server, Info info) {
		long dasar = info.hargaDibayar() > 0L ? info.hargaDibayar() : harga(server, info);
		return KotaConfig.ekonomi().pajakProperti <= 0 ? 0L : Math.max(1L, Math.round(dasar * KotaConfig.ekonomi().pajakProperti / 100.0D));
	}

	/** What the town pays back for an owned house: half of what was paid. */
	public static long nilaiJual(Info info) {
		return Math.round(info.hargaDibayar() * JUAL_KEMBALI);
	}

	/** The place factor of 42.6.1: kampung 0.5, pos 0.6, kota 1.0, kota tema 1.3, ibu kota 2.0, palace/cloud 3.0. */
	public static double faktorTempat(MinecraftServer server, Info info) {
		TempatInfo t = TempatApi.tempatDi(server.overworld(), info.pintu());
		String jenis = t == null ? "" : t.jenis();
		String distrik = t == null || t.distrik() == null ? "" : t.distrik();
		return faktorJenis(jenis, distrik);
	}

	static double faktorJenis(String jenis, String distrik) {
		if (LembarKunci.DISTRIK_ISTANA.equals(distrik) || LembarKunci.DISTRIK_AWAN.equals(distrik)) {
			return 3.0D;
		}
		String j = jenis.toLowerCase(Locale.ROOT);
		if (j.contains("kota_besar")) {
			return 2.0D;
		}
		if (j.contains("tema")) {
			return 1.3D;
		}
		if (j.startsWith("kampung") || j.startsWith("desa")) {
			return 0.5D;
		}
		if (j.startsWith("pos") || j.contains("singgah")) {
			return 0.6D;
		}
		return 1.0D;
	}

	/** Weekly wage of household help by place kind: kampung 8, pos 10, kota 12, ibu kota 15 NB. */
	public static long gajiPembantu(MinecraftServer server, Info info) {
		double f = faktorTempat(server, info);
		long nb = f <= 0.5D ? 8L : f <= 0.6D ? 10L : f < 2.0D ? 12L : 15L;
		return Math.max(1L, Math.round(nb * KotaConfig.ekonomi().gajiSkala));
	}

	// ================================================================== the player's actions

	/** {@code beli:ID}: buys a house on the market. */
	public static Hasil beli(ServerPlayer player, long situs, int id) {
		MinecraftServer server = player.level().getServer();
		Info info = info(server, situs, id);
		Hasil no = syarat(player, info, NB_BELI);
		if (no != null) {
			return kirim(player, no);
		}
		long harga = harga(server, info);
		if (!EkonomiApi.bayar(player, harga, "rumah_beli")) {
			return kirim(player, Hasil.gagal("uang", NilaiBatu.text(harga)));
		}
		CompoundTag t = rumahTag(server, situs, id);
		t.store("o", UUIDUtil.CODEC, player.getUUID());
		t.putString("st", ST_MILIK);
		t.putLong("hb", harga);
		t.putLong("pj", KotaHidup.hari(server) + 7L);
		t.putInt("tg", 0);
		t.remove("sg");
		t.remove("ss");
		KasKota.tambah(server, situs, harga);
		BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_SEWA, 0, -harga, "kultivator.kota42.rumah.buku.beli", namaTempat(server, situs));
		berubah(server, situs, id, player.getUUID());
		beriKertas(player, situs, id);
		return kirim(player, new Hasil(true, Component.translatable("kultivator.kota42.rumah.beli", nama(server, baca(situs, t)), NilaiBatu.text(harga))));
	}

	/** {@code sewa:ID:MINGGU}: rents a house on the market for 1-4 weeks, paid ahead. */
	public static Hasil sewa(ServerPlayer player, long situs, int id, int minggu) {
		MinecraftServer server = player.level().getServer();
		Info info = info(server, situs, id);
		Hasil no = syarat(player, info, NB_SEWA);
		if (no != null) {
			return kirim(player, no);
		}
		int w = Math.max(1, Math.min(MINGGU_MAKS, minggu));
		long biaya = sewa(server, info) * w;
		if (!EkonomiApi.bayar(player, biaya, "rumah_sewa")) {
			return kirim(player, Hasil.gagal("uang", NilaiBatu.text(biaya)));
		}
		long sampai = KotaHidup.hari(server) + 7L * w;
		CompoundTag t = rumahTag(server, situs, id);
		t.store("o", UUIDUtil.CODEC, player.getUUID());
		t.putString("st", ST_SEWA);
		t.putLong("ss", sampai);
		t.putLong("hb", 0L);
		t.remove("sg");
		KasKota.pajak(server, situs, biaya, "sewa");
		BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_SEWA, 0, -biaya, "kultivator.kota42.rumah.buku.sewa", namaTempat(server, situs), w);
		berubah(server, situs, id, player.getUUID());
		beriKertas(player, situs, id);
		return kirim(player, new Hasil(true, Component.translatable("kultivator.kota42.rumah.sewa", nama(server, baca(situs, t)), w, NilaiBatu.text(biaya), sampai)));
	}

	/** {@code perpanjang:ID:MINGGU}: pays the rent further ahead (at most 4 weeks ahead of today); unseals a sealed rent. */
	public static Hasil perpanjang(ServerPlayer player, long situs, int id, int minggu) {
		MinecraftServer server = player.level().getServer();
		Info info = info(server, situs, id);
		if (info == null || !player.getUUID().equals(info.pemilik()) || ST_MILIK.equals(info.status())) {
			return kirim(player, Hasil.gagal("bukan_sewa"));
		}
		if (ST_SEGEL.equals(info.status()) && info.hargaDibayar() > 0L) {
			return kirim(player, Hasil.gagal("sudah_dijual"));
		}
		long hari = KotaHidup.hari(server);
		long dari = Math.max(hari, info.sewaSampai());
		int w = Math.max(1, Math.min(minggu, (int) ((hari + 7L * MINGGU_MAKS - dari) / 7L)));
		if (dari + 7L * w > hari + 7L * MINGGU_MAKS) {
			return kirim(player, Hasil.gagal("terlalu_jauh", MINGGU_MAKS));
		}
		long biaya = sewa(server, info) * w;
		if (!EkonomiApi.bayar(player, biaya, "rumah_sewa")) {
			return kirim(player, Hasil.gagal("uang", NilaiBatu.text(biaya)));
		}
		CompoundTag t = rumahTag(server, situs, id);
		t.putLong("ss", dari + 7L * w);
		t.putString("st", ST_SEWA);
		t.remove("sg");
		KasKota.pajak(server, situs, biaya, "sewa");
		BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_SEWA, 0, -biaya, "kultivator.kota42.rumah.buku.sewa", namaTempat(server, situs), w);
		berubah(server, situs, id, player.getUUID());
		return kirim(player, new Hasil(true, Component.translatable("kultivator.kota42.rumah.perpanjang", w, dari + 7L * w)));
	}

	/** {@code bayar_pajak:ID}: pays every unpaid property-tax week of an owned house (unseals a tax seal). */
	public static Hasil bayarPajak(ServerPlayer player, long situs, int id) {
		MinecraftServer server = player.level().getServer();
		Info info = info(server, situs, id);
		if (info == null || !player.getUUID().equals(info.pemilik()) || info.hargaDibayar() <= 0L) {
			return kirim(player, Hasil.gagal("bukan_milik"));
		}
		if (info.tunggak() <= 0) {
			return kirim(player, Hasil.gagal("lunas"));
		}
		long biaya = pajak(server, info) * info.tunggak();
		if (!EkonomiApi.bayar(player, biaya, "rumah_pajak")) {
			return kirim(player, Hasil.gagal("uang", NilaiBatu.text(biaya)));
		}
		CompoundTag t = rumahTag(server, situs, id);
		t.putInt("tg", 0);
		if (ST_SEGEL.equals(info.status()) && t.getBooleanOr("sgp", false)) {
			t.putString("st", ST_MILIK);
			t.remove("sg");
			t.remove("sgp");
		}
		KasKota.pajak(server, situs, biaya, "properti");
		BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_SEWA, 0, -biaya, "kultivator.kota42.rumah.buku.pajak", namaTempat(server, situs));
		berubah(server, situs, id, player.getUUID());
		return kirim(player, new Hasil(true, Component.translatable("kultivator.kota42.rumah.pajak_lunas", NilaiBatu.text(biaya))));
	}

	/** {@code kunci:ID}: re-prints the key ({@code akta} false) or the deed for 5 NB. */
	public static Hasil cetakUlang(ServerPlayer player, long situs, int id, boolean akta) {
		MinecraftServer server = player.level().getServer();
		Info info = info(server, situs, id);
		if (info == null || !player.getUUID().equals(info.pemilik())) {
			return kirim(player, Hasil.gagal("bukan_milik"));
		}
		if (!EkonomiApi.bayar(player, CETAK_ULANG, "rumah_cetak")) {
			return kirim(player, Hasil.gagal("uang", NilaiBatu.text(CETAK_ULANG)));
		}
		KasKota.tambah(server, situs, CETAK_ULANG);
		Barang.beri(player, kertas(server, akta ? KotaEkonomiItems.AKTA_RUMAH : KotaEkonomiItems.KUNCI_RUMAH, player.getUUID(), situs, id));
		return kirim(player, new Hasil(true, Component.translatable(akta ? "kultivator.kota42.rumah.cetak_akta" : "kultivator.kota42.rumah.cetak_kunci",
				NilaiBatu.text(CETAK_ULANG))));
	}

	/**
	 * {@code jual_ya:ID}: an owned house is sold back to the town for half of its price; a rented one is given up (no refund).
	 * Either way it is sealed for 14 days, then released.
	 */
	public static Hasil jual(ServerPlayer player, long situs, int id) {
		MinecraftServer server = player.level().getServer();
		Info info = info(server, situs, id);
		if (info == null || !player.getUUID().equals(info.pemilik()) || ST_SEGEL.equals(info.status())) {
			return kirim(player, Hasil.gagal("bukan_milik"));
		}
		long kembali = info.hargaDibayar() > 0L ? nilaiJual(info) : 0L;
		if (kembali > 0L) {
			// the purse pays what it holds (never below zero); the land office covers the rest
			KasKota.ambil(server, situs, kembali);
			EkonomiApi.beri(player, kembali, "rumah_jual");
			BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_SEWA, 0, kembali, "kultivator.kota42.rumah.buku.jual", namaTempat(server, situs));
		}
		segel(server, situs, id, false);
		return kirim(player, new Hasil(true, kembali > 0L
				? Component.translatable("kultivator.kota42.rumah.dijual", NilaiBatu.text(kembali), SEGEL_HARI)
				: Component.translatable("kultivator.kota42.rumah.dilepas", SEGEL_HARI)));
	}

	/** {@code pekerjakan:WARGA:RUMAH}: hires an unemployed resident as household help (first week paid now). */
	public static Hasil pekerjakan(ServerPlayer player, long situs, int id, int wargaId) {
		MinecraftServer server = player.level().getServer();
		Info info = info(server, situs, id);
		if (info == null || !player.getUUID().equals(info.pemilik()) || ST_SEGEL.equals(info.status())) {
			return kirim(player, Hasil.gagal("bukan_milik"));
		}
		Warga w = WargaApi.byId(wargaId);
		if (w == null || !w.hidup() || w.staf != 0 || !menganggur(w)) {
			return kirim(player, Hasil.gagal("bukan_calon"));
		}
		if (info.pembantu() != 0) {
			return kirim(player, Hasil.gagal("sudah_ada"));
		}
		long gaji = gajiPembantu(server, info);
		if (!EkonomiApi.bayar(player, gaji, "rumah_pembantu")) {
			return kirim(player, Hasil.gagal("uang", NilaiBatu.text(gaji)));
		}
		CompoundTag t = rumahTag(server, situs, id);
		t.putInt("pb", wargaId);
		t.putLong("pbs", KotaHidup.hari(server) + 7L);
		KotaEkonomiData.get(server).ubah();
		WargaApi.pekerjakan(w, player.getUUID(), "rumah");
		return kirim(player, new Hasil(true, Component.translatable("kultivator.kota42.rumah.pembantu", WargaApi.nama(w), NilaiBatu.text(gaji))));
	}

	/** {@code berhentikan:WARGA}: lets the household help go. */
	public static Hasil berhentikan(ServerPlayer player, long situs, int id) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = rumahTag(server, situs, id);
		if (t == null || !player.getUUID().equals(pemilikDari(t)) || t.getIntOr("pb", 0) == 0) {
			return kirim(player, Hasil.gagal("tanpa_pembantu"));
		}
		lepasPembantu(server, situs, t);
		return kirim(player, new Hasil(true, Component.translatable("kultivator.kota42.rumah.pembantu_pergi")));
	}

	/** Seized goods of the player's sealed houses in {@code situs} come back (free within 14 days). */
	public static Hasil ambilSitaan(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag d = data(server, situs);
		ListTag list = d.getListOrEmpty("sitaan");
		ListTag rest = new ListTag();
		int n = 0;
		for (Tag each : list) {
			if (each instanceof CompoundTag e && player.getUUID().equals(e.read("o", UUIDUtil.CODEC).orElse(null))) {
				ItemStack stack = Barang.muat(e.get("b"), server.registryAccess());
				if (!stack.isEmpty()) {
					Barang.beri(player, stack);
					n++;
				}
			} else {
				rest.add(each);
			}
		}
		if (n == 0) {
			return kirim(player, Hasil.gagal("tanpa_sitaan"));
		}
		d.put("sitaan", rest);
		KotaEkonomiData.get(server).ubah();
		return kirim(player, new Hasil(true, Component.translatable("kultivator.kota42.rumah.sitaan_kembali", n)));
	}

	/** Stacks of seized goods waiting for {@code pemilik} in {@code situs}. */
	public static int jumlahSitaan(MinecraftServer server, long situs, UUID pemilik) {
		int n = 0;
		for (Tag each : data(server, situs).getListOrEmpty("sitaan")) {
			if (each instanceof CompoundTag e && pemilik.equals(e.read("o", UUIDUtil.CODEC).orElse(null))) {
				n++;
			}
		}
		return n;
	}

	/** Why {@code player} may not take {@code info} (null = he may). */
	private static @Nullable Hasil syarat(ServerPlayer player, @Nullable Info info, int namaBaik) {
		MinecraftServer server = player.level().getServer();
		KotaConfig.Ekonomi cfg = KotaConfig.ekonomi();
		if (!cfg.aktif || !cfg.rumah) {
			return Hasil.gagal("mati");
		}
		if (info == null || !info.dijual()) {
			return Hasil.gagal("tidak_dijual");
		}
		if (KotaBesarApi.buronan(player, info.situs())) {
			return Hasil.gagal("buronan");
		}
		int nb = KotaData.get(server).reputation(info.situs(), player.getUUID());
		if (nb < namaBaik) {
			return Hasil.gagal("nama_baik", namaBaik, nb);
		}
		if (milikDiTempat(server, info.situs(), player.getUUID()) != null) {
			return Hasil.gagal("satu_per_tempat");
		}
		if (jumlah(server, player.getUUID()) >= cfg.rumahMaks) {
			return Hasil.gagal("maks", cfg.rumahMaks);
		}
		return null;
	}

	/** The checklist of a house for the screen ({@code rinci.syarat}): ok, text, what the player has. */
	public static List<Object[]> syaratDaftar(ServerPlayer player, Info info) {
		MinecraftServer server = player.level().getServer();
		int nb = KotaData.get(server).reputation(info.situs(), player.getUUID());
		List<Object[]> out = new ArrayList<>();
		out.add(new Object[] {nb >= NB_SEWA, Component.translatable("kultivator.kota42.rumah.syarat.nb_sewa", NB_SEWA), Integer.toString(nb)});
		out.add(new Object[] {nb >= NB_BELI, Component.translatable("kultivator.kota42.rumah.syarat.nb_beli", NB_BELI), Integer.toString(nb)});
		out.add(new Object[] {!KotaBesarApi.buronan(player, info.situs()), Component.translatable("kultivator.kota42.rumah.syarat.buronan"), ""});
		int punya = jumlah(server, player.getUUID());
		out.add(new Object[] {punya < KotaConfig.ekonomi().rumahMaks, Component.translatable("kultivator.kota42.rumah.syarat.maks",
				KotaConfig.ekonomi().rumahMaks), Integer.toString(punya)});
		out.add(new Object[] {milikDiTempat(server, info.situs(), player.getUUID()) == null, Component.translatable("kultivator.kota42.rumah.syarat.satu"), ""});
		return out;
	}

	static boolean menganggur(Warga w) {
		NpcRole peran = w.peranNpc();
		return peran == NpcRole.WARGA;
	}

	// ================================================================== offers from other modules

	/**
	 * Puts a house on the market from another module: 42A's empty household ({@code KotaLembar.Rumah.RS_KOSONG}) or 42F's
	 * finished plot ({@code RS_KAVLING}); {@code bangunan} is the house's box titik, {@code pintu} its door. Returns the
	 * house id (the existing one when the door is known).
	 */
	public static int tawarkan(MinecraftServer server, long situs, Titik bangunan, BlockPos pintu, String sumber) {
		int kasur = 0;
		for (Titik t : TempatApi.titik(server.overworld(), situs, "kasur")) {
			if (bangunan.kotak().isInside(t.pos())) {
				kasur++;
			}
		}
		return catat(server, situs, bangunan.data().getOrDefault("nama", ""), ukuranDari("", kasur), kasur, bangunan.kotak(), pintu,
				bangunan.hadap(), sumber);
	}

	// ================================================================== discovery

	/** Reads the {@code dijual} titik of a loaded place into the stored houses (once a minute per place at most). */
	static void pindai(ServerLevel level, long situs) {
		MinecraftServer server = level.getServer();
		CompoundTag d = data(server, situs);
		long now = server.getTickCount();
		if (now - d.getLongOr("pindai", -100000L) < 1200L || TempatApi.potongan(level, situs).isEmpty()) {
			return;
		}
		d.putLong("pindai", now);
		List<Titik> dijual = TempatApi.titik(level, situs, KotaTitik.DIJUAL);
		if (dijual.isEmpty()) {
			return;
		}
		List<Titik> kotak = TempatApi.titik(level, situs, "bangunan");
		List<Titik> kasur = TempatApi.titik(level, situs, "kasur");
		for (Titik t : dijual) {
			String nama = t.data().getOrDefault("rumah", "");
			Titik box = null;
			for (Titik b : kotak) {
				if (!nama.isEmpty() && nama.equals(b.data().get("nama")) && b.kotak().inflatedBy(1).isInside(t.pos())) {
					box = b;
					break;
				}
			}
			if (box == null) {
				for (Titik b : kotak) {
					if (b.kotak().inflatedBy(1).isInside(t.pos())) {
						box = b;
						break;
					}
				}
			}
			if (box == null) {
				continue;
			}
			int beds = 0;
			for (Titik k : kasur) {
				if (box.kotak().isInside(k.pos())) {
					beds++;
				}
			}
			catat(server, situs, nama, ukuranDari(t.data().getOrDefault("ukuran", ""), beds), beds, box.kotak(), t.pos(), t.hadap(),
					dev.kultivator.dunia.kota42.ui.KotaLembar.Rumah.RS_DIJUAL);
		}
	}

	private static String ukuranDari(String ukuran, int kasur) {
		return switch (ukuran) {
			case "kecil", "sedang", "besar", "paviliun" -> ukuran;
			case "toko", "rumah_toko" -> "rumah_toko";
			default -> kasur <= 2 ? "kecil" : kasur <= 4 ? "sedang" : "besar";
		};
	}

	private static int catat(MinecraftServer server, long situs, String nama, String ukuran, int kasur, BoundingBox kotak, BlockPos pintu,
			Direction hadap, String sumber) {
		CompoundTag d = data(server, situs);
		CompoundTag r = d.getCompoundOrEmpty("r");
		if (!d.contains("r")) {
			d.put("r", r);
		}
		long door = pintu.asLong();
		for (String key : r.keySet()) {
			if (r.getCompoundOrEmpty(key).getLongOr("d", 0L) == door) {
				return r.getCompoundOrEmpty(key).getIntOr("id", 0);
			}
		}
		int id = d.getIntOr("n", 0) + 1;
		d.putInt("n", id);
		CompoundTag t = new CompoundTag();
		t.putInt("id", id);
		t.putString("nm", nama);
		t.putString("uk", ukuran);
		t.putInt("ks", kasur);
		t.putIntArray("k", new int[] {kotak.minX(), kotak.minY(), kotak.minZ(), kotak.maxX(), kotak.maxY(), kotak.maxZ()});
		t.putLong("d", door);
		t.putInt("h", hadap.get2DDataValue());
		t.putString("src", sumber);
		r.put(Integer.toString(id), t);
		KotaEkonomiData.get(server).ubah();
		return id;
	}

	// ================================================================== the days

	/** A new world day: rents run out, tax weeks fall due, seals end, help is paid (from {@code KotaEkonomi}). */
	static void hariBaru(MinecraftServer server, long hari) {
		if (!KotaConfig.ekonomi().aktif) {
			return;
		}
		for (Info info : semua(server)) {
			if (info.pemilik() == null) {
				continue;
			}
			CompoundTag t = rumahTag(server, info.situs(), info.id());
			ServerPlayer online = server.getPlayerList().getPlayer(info.pemilik());
			switch (info.status()) {
				case ST_SEWA -> {
					if (info.sewaSampai() < hari) {
						segel(server, info.situs(), info.id(), false);
						if (online != null) {
							Msg.chat(online, Component.translatable("kultivator.kota42.rumah.sewa_habis", nama(server, info), SEGEL_HARI).withStyle(ChatFormatting.GOLD));
						}
					} else if (info.sewaSampai() - hari == 1L && online != null) {
						Msg.chat(online, Component.translatable("kultivator.kota42.rumah.sewa_besok", nama(server, info)).withStyle(ChatFormatting.YELLOW));
					}
				}
				case ST_MILIK -> pajakMingguan(server, info, t, hari, online);
				case ST_SEGEL -> {
					if (info.segelSampai() < hari) {
						lepas(server, info.situs(), info.id());
					}
				}
				default -> {
				}
			}
			bayarPembantu(server, info.situs(), t, hari, online);
		}
		buangSitaan(server, hari);
	}

	private static void pajakMingguan(MinecraftServer server, Info info, CompoundTag t, long hari, @Nullable ServerPlayer online) {
		long due = t.getLongOr("pj", hari + 7L);
		if (due > hari) {
			return;
		}
		t.putLong("pj", due + 7L);
		long pajak = pajak(server, info);
		if (pajak <= 0L) {
			return;
		}
		// an owner who is away pays from his drawer or Rumah Uang deposit (offline weeks never count as unpaid while there is money)
		if (online != null && EkonomiApi.bayar(online, pajak, "rumah_pajak")
				|| Investasi.bayarLuring(server, info.pemilik(), pajak, "kultivator.kota42.bank.riwayat.pajak", nama(server, info).getString())) {
			KasKota.pajak(server, info.situs(), pajak, "properti");
			BukuKas.catat(server, info.pemilik(), LembarKunci.BukuKas.SUMBER_SEWA, 0, -pajak, "kultivator.kota42.rumah.buku.pajak", namaTempat(server, info.situs()));
			if (online != null) {
				Msg.chat(online, Component.translatable("kultivator.kota42.rumah.pajak_dibayar", nama(server, info), NilaiBatu.text(pajak)).withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		int tunggak = t.getIntOr("tg", 0) + 1;
		t.putInt("tg", tunggak);
		KotaEkonomiData.get(server).ubah();
		if (tunggak >= TUNGGAK_SEGEL) {
			segel(server, info.situs(), info.id(), true);
		}
		if (online != null) {
			Msg.chat(online, Component.translatable(tunggak >= TUNGGAK_SEGEL ? "kultivator.kota42.rumah.disegel_pajak" : "kultivator.kota42.rumah.pajak_tunggak",
					nama(server, info), tunggak, TUNGGAK_SEGEL).withStyle(ChatFormatting.RED));
		}
	}

	private static void bayarPembantu(MinecraftServer server, long situs, CompoundTag t, long hari, @Nullable ServerPlayer online) {
		int pb = t.getIntOr("pb", 0);
		if (pb == 0 || t.getLongOr("pbs", 0L) > hari) {
			return;
		}
		Info info = baca(situs, t);
		Warga w = WargaApi.byId(pb);
		long gaji = gajiPembantu(server, info);
		boolean lunas = w != null && w.hidup() && !ST_SEGEL.equals(info.status()) && info.pemilik() != null
				&& (online != null && EkonomiApi.bayar(online, gaji, "rumah_pembantu")
				|| Investasi.bayarLuring(server, info.pemilik(), gaji, "kultivator.kota42.bank.riwayat.gaji", nama(server, info).getString()));
		if (!lunas) {
			lepasPembantu(server, situs, t);
			if (online != null) {
				Msg.chat(online, Component.translatable("kultivator.kota42.rumah.pembantu_pergi").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		t.putLong("pbs", hari + 7L);
		KotaEkonomiData.get(server).ubah();
	}

	private static void lepasPembantu(MinecraftServer server, long situs, CompoundTag t) {
		KultivatorNpc body = TUBUH.remove(situs + ":" + t.getIntOr("id", 0));
		if (body != null) {
			body.vanish();
		}
		t.remove("pb");
		t.remove("pbs");
		KotaEkonomiData.get(server).ubah();
	}

	/** Seals a house for 14 days ({@code pajak}: a tax seal, lifted by paying). */
	static void segel(MinecraftServer server, long situs, int id, boolean pajak) {
		CompoundTag t = rumahTag(server, situs, id);
		if (t == null) {
			return;
		}
		t.putString("st", ST_SEGEL);
		t.putLong("sg", KotaHidup.hari(server) + SEGEL_HARI);
		t.putBoolean("sgp", pajak);
		if (!pajak) {
			t.putLong("hb", 0L);
		}
		lepasPembantu(server, situs, t);
		TokoPemain.rumahDisegel(server, situs, id);
		berubah(server, situs, id, pemilikDari(t));
	}

	/** The seal ended: containers emptied into seized goods (when loaded; else tried again tomorrow), back on the market. */
	static void lepas(MinecraftServer server, long situs, int id) {
		CompoundTag t = rumahTag(server, situs, id);
		if (t == null) {
			return;
		}
		Info info = baca(situs, t);
		ServerLevel level = server.overworld();
		BoundingBox box = info.kotak();
		if (!level.isLoaded(new BlockPos(box.minX(), box.minY(), box.minZ())) || !level.isLoaded(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
			return;
		}
		UUID owner = info.pemilik();
		ListTag sitaan = data(server, situs).getListOrEmpty("sitaan");
		long hari = KotaHidup.hari(server);
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockEntity be = level.getBlockEntity(pos);
			if (!(be instanceof Container c) || be instanceof RakDagangBlockEntity) {
				continue;
			}
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack stack = c.removeItemNoUpdate(i);
				if (!stack.isEmpty() && owner != null) {
					CompoundTag e = new CompoundTag();
					e.store("o", UUIDUtil.CODEC, owner);
					e.putLong("h", hari);
					e.put("b", Barang.simpan(stack, server.registryAccess()));
					sitaan.add(e);
				}
			}
			c.setChanged();
		}
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockEntity(pos) instanceof RakDagangBlockEntity rak && owner != null && owner.equals(rak.pemilik())) {
				for (int i = 0; i < RakDagangBlockEntity.SLOT; i++) {
					ItemStack stack = rak.ambil(i);
					if (!stack.isEmpty()) {
						CompoundTag e = new CompoundTag();
						e.store("o", UUIDUtil.CODEC, owner);
						e.putLong("h", hari);
						e.put("b", Barang.simpan(stack, server.registryAccess()));
						sitaan.add(e);
					}
				}
			}
		}
		data(server, situs).put("sitaan", sitaan);
		t.remove("o");
		t.remove("st");
		t.remove("sg");
		t.remove("sgp");
		t.remove("ss");
		t.remove("hb");
		t.remove("tg");
		t.remove("pj");
		TokoPemain.rumahDilepas(server, situs, id);
		berubah(server, situs, id, null);
		ServerPlayer online = owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (online != null) {
			Msg.chat(online, Component.translatable("kultivator.kota42.rumah.dilepas_kota", nama(server, info), SEGEL_HARI).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Seized goods older than 14 days are sold for the town purse (their shop value). */
	private static void buangSitaan(MinecraftServer server, long hari) {
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.RUMAH);
		for (String key : part.keySet()) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			CompoundTag d = part.getCompoundOrEmpty(key);
			ListTag list = d.getListOrEmpty("sitaan");
			if (list.isEmpty()) {
				continue;
			}
			long situs;
			try {
				situs = Long.parseLong(key.substring(5));
			} catch (NumberFormatException e) {
				continue;
			}
			ListTag keep = new ListTag();
			for (Tag each : list) {
				if (each instanceof CompoundTag e && hari - e.getLongOr("h", hari) > SEGEL_HARI) {
					ItemStack stack = Barang.muat(e.get("b"), server.registryAccess());
					long nilai = EkonomiApi.harga(stack) * stack.getCount() / 2L;
					if (nilai > 0L) {
						KasKota.tambah(server, situs, nilai);
					}
				} else {
					keep.add(each);
				}
			}
			d.put("sitaan", keep);
		}
		KotaEkonomiData.get(server).ubah();
	}

	// ================================================================== the help's body and Teh Rumah

	/** Every 2 s from {@code KotaEkonomi}: help bodies stand in houses whose owner is near; Teh Rumah. */
	static void tick(MinecraftServer server) {
		if (!KotaConfig.ekonomi().aktif || !KotaConfig.ekonomi().rumah) {
			return;
		}
		ServerLevel level = server.overworld();
		java.util.Set<String> wanted = new java.util.HashSet<>();
		for (Info info : semua(server)) {
			if (info.pembantu() == 0 || info.pemilik() == null || ST_SEGEL.equals(info.status())) {
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(info.pemilik());
			if (owner == null || owner.level() != level || owner.blockPosition().distSqr(info.pintu()) > PEMBANTU_JARAK * PEMBANTU_JARAK) {
				continue;
			}
			String key = info.situs() + ":" + info.id();
			wanted.add(key);
			KultivatorNpc body = TUBUH.get(key);
			if (body == null || body.isRemoved()) {
				BlockPos at = info.pintu().relative(info.hadap().getOpposite());
				body = HidupApi.tubuhStaf(level, info.situs(), NpcRole.PELAYAN, at, info.hadap().toYRot());
				if (body == null) {
					continue;
				}
				TUBUH.put(key, body);
			}
			if (info.kotak().isInside(owner.blockPosition()) && server.getTickCount() % 600 == 7) {
				// the help feeds the owner's tamed pets inside the house (42.6.1): a hurt one mends a little every 30 s
				for (net.minecraft.world.entity.TamableAnimal pet : level.getEntitiesOfClass(net.minecraft.world.entity.TamableAnimal.class,
						net.minecraft.world.phys.AABB.of(info.kotak()), p -> p.isTame() && p.isOwnedBy(owner) && p.getHealth() < p.getMaxHealth())) {
					pet.heal(4.0F);
				}
			}
			if (info.kotak().isInside(owner.blockPosition()) && !tehAktif(owner)) {
				CompoundTag teh = KotaEkonomiData.get(server).bagian(KotaEkonomiData.RUMAH).getCompoundOrEmpty("teh");
				String id = owner.getUUID().toString();
				long now = server.overworld().getGameTime();
				if (now - teh.getLongOr(id, -TEH_JEDA - TEH_TICK) >= TEH_JEDA + TEH_TICK) {
					teh.putLong(id, now + TEH_TICK);
					KotaEkonomiData.get(server).bagian(KotaEkonomiData.RUMAH).put("teh", teh);
					KotaEkonomiData.get(server).ubah();
					Warga w = WargaApi.byId(info.pembantu());
					Msg.chat(owner, Component.translatable("kultivator.kota42.rumah.teh", w == null ? Component.empty() : WargaApi.nama(w))
							.withStyle(ChatFormatting.GREEN));
				}
			}
		}
		TUBUH.entrySet().removeIf(e -> {
			if (!wanted.contains(e.getKey()) || e.getValue().isRemoved()) {
				e.getValue().vanish();
				return true;
			}
			return false;
		});
	}

	/** True while the player's Teh Rumah (+5 % qi regeneration) runs. */
	public static boolean tehAktif(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		long until = KotaEkonomiData.get(server).bagian(KotaEkonomiData.RUMAH).getCompoundOrEmpty("teh").getLongOr(player.getUUID().toString(), 0L);
		return until > server.overworld().getGameTime();
	}

	// ================================================================== helpers

	/** The display name of a house: "Rumah Sedang · <place>". */
	public static Component nama(MinecraftServer server, Info info) {
		return Component.translatable("kultivator.kota42.rumah.nama", Component.translatable("kultivator.kota42.rumah.ukuran." + info.ukuran()),
				info.id(), namaTempat(server, info.situs()));
	}

	static Component namaTempat(MinecraftServer server, long situs) {
		try {
			return Dagang.namaSitus(server, situs);
		} catch (RuntimeException e) {
			return Component.literal("#" + situs);
		}
	}

	private static void beriKertas(ServerPlayer player, long situs, int id) {
		MinecraftServer server = player.level().getServer();
		Barang.beri(player, kertas(server, KotaEkonomiItems.AKTA_RUMAH, player.getUUID(), situs, id));
		Barang.beri(player, kertas(server, KotaEkonomiItems.KUNCI_RUMAH, player.getUUID(), situs, id));
	}

	private static ItemStack kertas(MinecraftServer server, net.minecraft.world.item.Item item, UUID owner, long situs, int id) {
		ItemStack stack = new ItemStack(item);
		stack.set(KotaEkonomiItems.RUMAH, new KotaEkonomiItems.Rumah(situs, id, owner, namaTempat(server, situs)));
		return stack;
	}

	private static Hasil kirim(ServerPlayer player, Hasil hasil) {
		Msg.chat(player, hasil.pesan().copy().withStyle(hasil.ok() ? ChatFormatting.GOLD : ChatFormatting.RED));
		return hasil;
	}

	private static void berubah(MinecraftServer server, long situs, int id, @Nullable UUID pemilik) {
		KotaEkonomiData.get(server).ubah();
		klaim = null;
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni != null) {
			huni.ubahTitik();
		}
		KotaBus.RUMAH_BERUBAH.invoker().onRumahBerubah(server, situs, id, pemilik);
	}

	private static List<Klaim> klaim(MinecraftServer server) {
		List<Klaim> now = klaim;
		if (now != null) {
			return now;
		}
		List<Klaim> out = new ArrayList<>();
		if (server != null) {
			for (Info info : semua(server)) {
				if (info.pemilik() != null) {
					out.add(new Klaim(info.situs(), info.id(), info.kotak(), info.pemilik(), ST_SEGEL.equals(info.status())));
				}
			}
		}
		klaim = List.copyOf(out);
		return klaim;
	}

	private static Rumah rumah(Info info) {
		String jenis = switch (info.status()) {
			case ST_MILIK -> "beli";
			case ST_SEWA -> "sewa";
			default -> "segel";
		};
		long sampai = ST_SEWA.equals(info.status()) ? info.sewaSampai() : ST_SEGEL.equals(info.status()) ? info.segelSampai() : 0L;
		return new Rumah(info.situs(), info.id(), info.pemilik(), jenis, info.kotak(), sampai);
	}

	private static Info baca(long situs, CompoundTag t) {
		int[] k = t.getIntArray("k").orElse(new int[6]);
		if (k.length < 6) {
			k = new int[6];
		}
		BlockPos door = BlockPos.of(t.getLongOr("d", 0L));
		return new Info(situs, t.getIntOr("id", 0), t.getStringOr("nm", ""), t.getStringOr("uk", "kecil"), t.getIntOr("ks", 0),
				new BoundingBox(k[0], k[1], k[2], k[3], k[4], k[5]), door, Direction.from2DDataValue(t.getIntOr("h", 0)),
				t.getStringOr("src", dev.kultivator.dunia.kota42.ui.KotaLembar.Rumah.RS_DIJUAL), pemilikDari(t), t.getStringOr("st", ""),
				t.getLongOr("ss", 0L), t.getLongOr("sg", 0L), t.getIntOr("tg", 0), t.getLongOr("hb", 0L), t.getIntOr("pb", 0));
	}

	private static @Nullable UUID pemilikDari(CompoundTag t) {
		return t.read("o", UUIDUtil.CODEC).orElse(null);
	}

	private static CompoundTag data(MinecraftServer server, long situs) {
		return KotaEkonomiData.get(server).situs(KotaEkonomiData.RUMAH, situs);
	}

	private static @Nullable CompoundTag rumahTag(MinecraftServer server, long situs, int id) {
		CompoundTag r = data(server, situs).getCompoundOrEmpty("r");
		String key = Integer.toString(id);
		return r.contains(key) ? r.getCompoundOrEmpty(key) : null;
	}

	private RumahKota() {
	}
}
