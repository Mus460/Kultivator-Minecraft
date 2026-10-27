package dev.kultivator.dunia.kota42.sekte;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.warga.Berbakat;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.kotabesar.HariGerbang;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player sect's recruitment office in a town (DESIGN 42.8.1, contract C42.E1).
 * <ul>
 * <li><b>Booths</b>: a {@code rekrut} titik (the clerk's chair behind the recruiting table; {@code bilik} = booth number) of
 * the hall of a kampung or town (one booth), a theme town (two) or the capital's {@code kantor_rekrut} lot (four). Rent per
 * 7 days: kampung 5, kota 10, kota tema 15, capital 25 NB, paid to the town purse. Needs hall level &ge; 2 and Nama Baik
 * &ge; 10 there.</li>
 * <li><b>Keeper</b>: a disciple on {@code KERJA_KOTA} duty ({@link TugasKotaMurid}, quality 1.25) or a hired resident clerk
 * (week wage of 42.6.2 for the whole lease, quality 1 + skill / 200); an empty desk draws half.</li>
 * <li><b>Applicants</b> once a day from residents with a root (LOW+), not recruited, 8-30 years old: each with
 * {@code 0.3 x root weight x (1 + renown / 1000) x (1 + family impression / 100) x gossip x keeper} (halved while the
 * capital's Gate Opening Day brings the NPC sects' recruiters, x1.2 with a branch training hall, x2 on the open test day),
 * at most 12 on the list.</li>
 * <li><b>Actions</b>: {@link #uji} (the {@code batu_uji} stone, 2 NB, the root is then certain), {@link #terima} (blessing
 * money: proud 0, doubtful {@code 10 x root x (band + 1)} NB, refusing starts the "Restu Ayah" quest), {@link #tolak}; at
 * most 3 accepted per office per 7 days; an accepted resident becomes a hall candidate ({@code WargaApi.jadikanCalon},
 * {@code Halls.addCandidates}) and walks off: a hug with the parent, a wave with the bundle, out of the town edge.</li>
 * <li><b>Open test day</b> ({@link #adakanUjian}, 20 NB, once per 10 days per office): 6-12 youths line up and lay their
 * palms on the stone one by one before a crowd of residents ({@code HidupApi.kerumunan}); all of them are tested.</li>
 * </ul>
 * State: {@code KotaSekteData.situs("kantor", situs)["k"][owner]}. The stone and the clerk are noSave bodies shown while a
 * player is within 64 blocks.
 */
public final class KantorRekrut {

	/** One office: owner, place, rent paid until (world day, exclusive), clerk resident id (0 = none), open applicants. */
	public record Kantor(UUID pemilik, long situs, long sewaSampai, int penjaga, int pelamar) {
	}

	/**
	 * One applicant: the resident, whether the stone tested it, the root grade shown (the real one when tested, else a
	 * guess one grade off at most; {@code Warga.akar} scale 2-5), the day it applied, a rival sect wants it, the blessing
	 * money (-1 = the family refuses: "Restu Ayah"), and the special mark (HIGH+ root and tested).
	 */
	public record Pelamar(Warga warga, boolean diuji, int akarTampil, long hari, boolean saingan, long restu, boolean istimewa) {
	}

	/** One line of the rent checklist: met, text, what the player has. */
	public record Syarat(boolean ok, Component teks, Component punya) {
	}

	public static final int PELAMAR_MAKS = 12;
	public static final int TERIMA_MAKS = 3;
	public static final int HARI_SEWA = 7;
	public static final int AULA_MIN = 2;
	public static final int NAMA_BAIK_MIN = 10;
	public static final long BIAYA_UJI = 2L;
	public static final long BIAYA_UJIAN = 20L;
	public static final int JEDA_UJIAN = 10;
	public static final int UMUR_MIN = 8;
	public static final int UMUR_MAKS = 30;
	static final double JARAK_TAMPIL = 64.0D;

	private static final String K_KANTOR = "k";

	// ================================================================== runtime (bodies and scenes, never saved)

	/** The bodies of one office while a player is near. */
	private static final class Tampil {
		@Nullable BatuUjiEntity batu;
		@Nullable KultivatorNpc juru;
		long padamPada;
		long seruBerikut;
	}

	/** One open test day running. */
	private static final class Upacara {
		final long situs;
		final UUID pemilik;
		final List<Integer> antre = new ArrayList<>();
		final List<KultivatorNpc> tubuh = new ArrayList<>();
		int giliran = -1;
		long berikut;
		boolean selesai;

		Upacara(long situs, UUID pemilik) {
			this.situs = situs;
			this.pemilik = pemilik;
		}
	}

	/** One farewell walk. */
	private static final class Pamit {
		final KultivatorNpc anak;
		final @Nullable KultivatorNpc ortu;
		final BlockPos keluar;
		final long mulai;
		int fase;

		Pamit(KultivatorNpc anak, @Nullable KultivatorNpc ortu, BlockPos keluar, long mulai) {
			this.anak = anak;
			this.ortu = ortu;
			this.keluar = keluar;
			this.mulai = mulai;
		}
	}

	private static final Map<String, Tampil> TAMPIL = new HashMap<>();
	private static final Map<String, Upacara> UPACARA = new HashMap<>();
	private static final List<Pamit> PAMIT = new ArrayList<>();

	static void lupakan() {
		for (Tampil t : TAMPIL.values()) {
			buang(t);
		}
		TAMPIL.clear();
		for (Upacara u : UPACARA.values()) {
			u.tubuh.forEach(KultivatorNpc::vanish);
		}
		UPACARA.clear();
		for (Pamit p : PAMIT) {
			p.anak.vanish();
			if (p.ortu != null) {
				p.ortu.vanish();
			}
		}
		PAMIT.clear();
	}

	private static String kunci(long situs, UUID pemilik) {
		return situs + "|" + pemilik;
	}

	// ================================================================== reading (C42.E1)

	/** The first office in place {@code situs}, or null. */
	public static @Nullable Kantor kantor(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return null;
		}
		CompoundTag all = semua(server, situs, false);
		for (String key : all.keySet()) {
			Kantor k = baca(server, situs, all.getCompoundOrEmpty(key));
			if (k != null) {
				return k;
			}
		}
		return null;
	}

	/** The office of {@code pemilik} in place {@code situs}, or null. */
	public static @Nullable Kantor kantor(long situs, UUID pemilik) {
		MinecraftServer server = KotaHidup.server();
		CompoundTag t = server == null || pemilik == null ? null : tag(server, situs, pemilik, false);
		return t == null ? null : baca(server, situs, t);
	}

	/** Every office in place {@code situs} (one per booth). */
	public static List<Kantor> kantorDi(MinecraftServer server, long situs) {
		List<Kantor> out = new ArrayList<>();
		CompoundTag all = semua(server, situs, false);
		for (String key : all.keySet()) {
			Kantor k = baca(server, situs, all.getCompoundOrEmpty(key));
			if (k != null) {
				out.add(k);
			}
		}
		return out;
	}

	/** Every office of {@code pemilik} (all places). */
	public static List<Kantor> milik(MinecraftServer server, UUID pemilik) {
		List<Kantor> out = new ArrayList<>();
		for (long situs : situsSemua(server)) {
			Kantor k = kantor(situs, pemilik);
			if (k != null) {
				out.add(k);
			}
		}
		return out;
	}

	private static @Nullable Kantor baca(MinecraftServer server, long situs, CompoundTag t) {
		UUID o = t.read("o", UUIDUtil.CODEC).orElse(null);
		if (o == null) {
			return null;
		}
		String pj = t.getStringOr("pj", "");
		int penjaga = pj.startsWith("w:") ? parseInt(pj.substring(2)) : 0;
		return new Kantor(o, situs, t.getLongOr("sampai", 0L), penjaga, t.getListOrEmpty("pel").size());
	}

	/** Booths of a place: kampung/kota 1, kota tema 2, capital 4; 0 where no office may be (posts, others). */
	public static int bilik(MinecraftServer server, long situs) {
		return switch (PajakKota.jenisTempat(server, situs)) {
			case PajakKota.JT_IBU_KOTA -> 4;
			case PajakKota.JT_TEMA -> 2;
			case PajakKota.JT_KOTA, PajakKota.JT_KAMPUNG -> 1;
			default -> 0;
		};
	}

	/** Rent per 7 days: kampung 5, kota 10, kota tema 15, capital 25 NB. */
	public static long sewa(MinecraftServer server, long situs) {
		return switch (PajakKota.jenisTempat(server, situs)) {
			case PajakKota.JT_IBU_KOTA -> 25L;
			case PajakKota.JT_TEMA -> 15L;
			case PajakKota.JT_KOTA -> 10L;
			default -> 5L;
		};
	}

	/** Accepted in the last 7 days. */
	public static int diterimaMinggu(MinecraftServer server, long situs, UUID pemilik) {
		CompoundTag t = tag(server, situs, pemilik, false);
		return t == null ? 0 : terimaMinggu(t, KotaSekte.hari(server));
	}

	/** {total accepted, rejected, applied in the last 7 days}. */
	public static int[] statistik(MinecraftServer server, long situs, UUID pemilik) {
		CompoundTag t = tag(server, situs, pemilik, false);
		if (t == null) {
			return new int[3];
		}
		long hari = KotaSekte.hari(server);
		int minggu = 0;
		for (Tag e : t.getListOrEmpty("pm")) {
			if (e instanceof CompoundTag c && hari - c.getLongOr("d", -99L) < HARI_SEWA) {
				minggu += c.getIntOr("n", 0);
			}
		}
		return new int[]{t.getIntOr("tot", 0), t.getIntOr("tol", 0), minggu};
	}

	/** The office's recent events (newest first): lang key + its argument (a resident name or the place). */
	public static List<Component> kabar(MinecraftServer server, long situs, UUID pemilik) {
		CompoundTag t = tag(server, situs, pemilik, false);
		List<Component> out = new ArrayList<>();
		if (t == null) {
			return out;
		}
		ListTag ev = t.getListOrEmpty("ev");
		for (int i = ev.size() - 1; i >= 0; i--) {
			CompoundTag c = ev.getCompoundOrEmpty(i);
			out.add(Component.translatable(c.getStringOr("k", ""), c.getStringOr("a", ""), c.getLongOr("d", 0L)));
		}
		return out;
	}

	/** The earliest day of the next open test day. */
	public static long ujianBerikut(MinecraftServer server, long situs, UUID pemilik) {
		CompoundTag t = tag(server, situs, pemilik, false);
		return t == null ? 0L : t.getLongOr("uj", -999L) + JEDA_UJIAN;
	}

	/** True while the open test day of this office is being held. */
	public static boolean ujianBerlangsung(long situs, UUID pemilik) {
		Upacara u = UPACARA.get(kunci(situs, pemilik));
		return u != null && !u.selesai;
	}

	/** The office desk (the clerk's chair titik) and the way it faces, or null when not resolved yet. */
	public static @Nullable BlockPos meja(MinecraftServer server, long situs, UUID pemilik) {
		CompoundTag t = tag(server, situs, pemilik, false);
		return t == null || !t.contains("x") ? null : new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0));
	}

	/** The applicants of an office, in the order they applied. */
	public static List<Pelamar> pelamar(MinecraftServer server, long situs, UUID pemilik) {
		CompoundTag t = tag(server, situs, pemilik, false);
		List<Pelamar> out = new ArrayList<>();
		if (t == null) {
			return out;
		}
		WargaData data = WargaData.get(server);
		ServerPlayer owner = server.getPlayerList().getPlayer(pemilik);
		int pita = owner == null ? 0 : Barang.pita(owner);
		for (Tag e : t.getListOrEmpty("pel")) {
			if (!(e instanceof CompoundTag c)) {
				continue;
			}
			Warga w = data.byId(c.getIntOr("w", 0));
			if (w == null) {
				continue;
			}
			boolean diuji = c.getBooleanOr("u", false);
			int tampil = diuji ? w.akar : tebakAkar(w);
			out.add(new Pelamar(w, diuji, tampil, c.getLongOr("d", 0L), c.getBooleanOr("s", false), restu(w, pita), diuji && w.akar >= 4));
		}
		return out;
	}

	/** The blessing money the family wants: 0 proud or already blessed, -1 refusing ("Restu Ayah"), else 10 x root x (b+1). */
	public static long restu(Warga w, int pita) {
		if (!Berbakat.perluRestu(w)) {
			return 0L;
		}
		return w.sikapKeluarga == Berbakat.MENOLAK ? -1L : 10L * w.akar * (pita + 1L);
	}

	/** The guessed grade of an untested applicant: the real one, or one off by a stable hash (2-5). */
	static int tebakAkar(Warga w) {
		int off = switch (Math.floorMod(w.id * 0x9E3779B1, 5)) {
			case 0 -> -1;
			case 4 -> 1;
			default -> 0;
		};
		return Mth.clamp(w.akar + off, 2, 5);
	}

	/** The rent checklist for {@code player} at {@code situs}. */
	public static List<Syarat> syarat(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		List<Syarat> out = new ArrayList<>();
		int level = Halls.level(player.getUUID());
		out.add(new Syarat(level >= AULA_MIN, Component.translatable("kultivator.kota42.sekte.kantor.syarat.aula", AULA_MIN),
				Component.literal(Integer.toString(level))));
		int nb = KotaSekte.namaBaik(server, situs, player.getUUID());
		out.add(new Syarat(nb >= NAMA_BAIK_MIN, Component.translatable("kultivator.kota42.sekte.kantor.syarat.nama_baik", NAMA_BAIK_MIN),
				Component.literal(Integer.toString(nb))));
		int bilik = bilik(server, situs);
		int dipakai = semua(server, situs, false).size();
		out.add(new Syarat(dipakai < bilik, Component.translatable("kultivator.kota42.sekte.kantor.syarat.bilik", bilik),
				Component.literal(dipakai + "/" + bilik)));
		long harga = sewa(server, situs);
		out.add(new Syarat(dev.kultivator.dunia.kota.NilaiBatu.total(player) >= harga,
				Component.translatable("kultivator.kota42.sekte.kantor.syarat.sewa", Barang.nb(harga)),
				Barang.nb(dev.kultivator.dunia.kota.NilaiBatu.total(player))));
		return out;
	}

	// ================================================================== actions

	/** {@code sewa}: rents a free booth of the place the player stands in for 7 days. */
	public static KotaSekte.Hasil sewaKantor(ServerPlayer player, long situs) {
		KotaSekte.Hasil h = KotaSekte.syaratUmum(player, KotaConfig.sekte().kantor);
		if (h != null) {
			return h.kirim(player);
		}
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		if (tag(server, situs, owner, false) != null) {
			return KotaSekte.Hasil.gagal("kantor.sudah").kirim(player);
		}
		int bilik = bilik(server, situs);
		if (bilik <= 0) {
			return KotaSekte.Hasil.gagal("kantor.tempat").kirim(player);
		}
		if (Halls.level(owner) < AULA_MIN) {
			return KotaSekte.Hasil.gagal("aula", AULA_MIN).kirim(player);
		}
		int nb = KotaSekte.namaBaik(server, situs, owner);
		if (nb < NAMA_BAIK_MIN) {
			return KotaSekte.Hasil.gagal("nama_baik", NAMA_BAIK_MIN, nb).kirim(player);
		}
		CompoundTag all = semua(server, situs, true);
		if (all.size() >= bilik) {
			return KotaSekte.Hasil.gagal("kantor.penuh", bilik).kirim(player);
		}
		long harga = sewa(server, situs);
		if (!EkonomiApi.bayar(player, harga, "kota42.kantor.sewa")) {
			return KotaSekte.Hasil.gagal("uang", Barang.nb(harga)).kirim(player);
		}
		EkonomiApi.kasKota(situs, harga);
		EkonomiApi.kas(owner, dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas.SUMBER_SEWA, false, -harga, "kultivator.kota42.sekte.buku.sewa_kantor");
		long hari = KotaSekte.hari(server);
		CompoundTag t = new CompoundTag();
		t.store("o", UUIDUtil.CODEC, owner);
		t.putLong("mulai", hari);
		t.putLong("sampai", hari + HARI_SEWA);
		t.putLong("uj", -999L);
		t.putLong("roll", hari);
		int no = bilikBebas(all, bilik);
		t.putInt("b", no);
		if (player.level() instanceof ServerLevel level) {
			tempatkan(level, situs, t, no, player.blockPosition());
		}
		all.put(owner.toString(), t);
		catat(t, "kultivator.kota42.sekte.kantor.ev.buka", KotaSekte.namaTempat(server, situs).getString(), hari);
		KotaSekteData.get(server).ubah();
		return KotaSekte.Hasil.berhasil("kantor.disewa", KotaSekte.namaTempat(server, situs), no, hari + HARI_SEWA).kirim(player);
	}

	/** {@code perpanjang}: 7 more days (also extends a hired clerk, whose wage is paid for the new week). */
	public static KotaSekte.Hasil perpanjang(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = tag(server, situs, player.getUUID(), false);
		if (t == null) {
			return KotaSekte.Hasil.gagal("kantor.tidak_ada").kirim(player);
		}
		long harga = sewa(server, situs);
		long gaji = t.getStringOr("pj", "").startsWith("w:") ? gajiJuru(server, situs) : 0L;
		if (!EkonomiApi.bayar(player, harga + gaji, "kota42.kantor.sewa")) {
			return KotaSekte.Hasil.gagal("uang", Barang.nb(harga + gaji)).kirim(player);
		}
		EkonomiApi.kasKota(situs, harga);
		EkonomiApi.kas(player.getUUID(), dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas.SUMBER_SEWA, false, -(harga + gaji),
				"kultivator.kota42.sekte.buku.sewa_kantor");
		long baru = Math.max(t.getLongOr("sampai", 0L), KotaSekte.hari(server)) + HARI_SEWA;
		t.putLong("sampai", baru);
		KotaSekteData.get(server).ubah();
		return KotaSekte.Hasil.berhasil("kantor.diperpanjang", baru).kirim(player);
	}

	/** Gives up the office (no refund). */
	public static KotaSekte.Hasil tutup(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		if (tag(server, situs, player.getUUID(), false) == null) {
			return KotaSekte.Hasil.gagal("kantor.tidak_ada").kirim(player);
		}
		hapus(server, situs, player.getUUID());
		return KotaSekte.Hasil.berhasil("kantor.ditutup", KotaSekte.namaTempat(server, situs)).kirim(player);
	}

	/** {@code uji:WARGA}: the applicant lays its palms on the stone (2 NB); its root is certain from now on. */
	public static KotaSekte.Hasil uji(ServerPlayer player, long situs, int wargaId) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = tag(server, situs, player.getUUID(), false);
		CompoundTag row = t == null ? null : baris(t, wargaId);
		Warga w = WargaData.get(server).byId(wargaId);
		if (row == null || w == null) {
			return KotaSekte.Hasil.gagal("kantor.tanpa_pelamar").kirim(player);
		}
		if (row.getBooleanOr("u", false)) {
			return KotaSekte.Hasil.gagal("kantor.sudah_diuji", WargaApi.nama(w)).kirim(player);
		}
		if (!EkonomiApi.bayar(player, BIAYA_UJI, "kota42.kantor.uji")) {
			return KotaSekte.Hasil.gagal("uang", Barang.nb(BIAYA_UJI)).kirim(player);
		}
		EkonomiApi.kasKota(situs, BIAYA_UJI);
		row.putBoolean("u", true);
		KotaSekteData.get(server).ubah();
		nyalakan(server, situs, player.getUUID(), w);
		return KotaSekte.Hasil.berhasil("kantor.diuji", WargaApi.nama(w), namaAkar(w.akar)).kirim(player);
	}

	/** {@code terima:WARGA} (also {@code pilih:WARGA} on the open test day). */
	public static KotaSekte.Hasil terima(ServerPlayer player, long situs, int wargaId) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		CompoundTag t = tag(server, situs, owner, false);
		CompoundTag row = t == null ? null : baris(t, wargaId);
		WargaData data = WargaData.get(server);
		Warga w = data.byId(wargaId);
		if (row == null || w == null) {
			return KotaSekte.Hasil.gagal("kantor.tanpa_pelamar").kirim(player);
		}
		long hari = KotaSekte.hari(server);
		if (terimaMinggu(t, hari) >= TERIMA_MAKS) {
			return KotaSekte.Hasil.gagal("kantor.batas", TERIMA_MAKS).kirim(player);
		}
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active() || Halls.recruitPaused(owner)) {
			return KotaSekte.Hasil.gagal("aula_tidur").kirim(player);
		}
		if (Berbakat.perluRestu(w)) {
			if (w.sikapKeluarga == Berbakat.MENOLAK) {
				boolean quest = CeritaApi.mulaiQuest(player, "kota42_restu_ayah", situs);
				if (quest) {
					KotaSekteData sd = KotaSekteData.get(server);
					sd.bagian(KotaSekteData.RESTU).putInt(player.getUUID().toString(), w.id);
					sd.ubah();
				}
				Warga ayah = w.ayah == Warga.TANPA ? null : data.byId(w.ayah);
				Component nama = ayah != null ? WargaApi.nama(ayah) : Component.translatable("kultivator.kota42.sekte.kantor.keluarga");
				return KotaSekte.Hasil.gagal(quest ? "kantor.restu_quest" : "kantor.restu_tolak", nama, WargaApi.nama(w)).kirim(player);
			}
			long uang = restu(w, Barang.pita(player));
			if (!EkonomiApi.bayar(player, uang, "kota42.kantor.restu")) {
				return KotaSekte.Hasil.gagal("kantor.restu_uang", Barang.nb(uang)).kirim(player);
			}
			EkonomiApi.kas(owner, dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas.SUMBER_UJIAN, false, -uang, "kultivator.kota42.sekte.buku.restu");
			Berbakat.beriRestu(server, w);
		}
		long situsWarga = data.situsDari(w.id);
		TempatHuni huni = Pemanggil.huni(situsWarga);
		KultivatorNpc tubuhAsli = huni == null || w.slot == Warga.TANPA_SLOT ? null : huni.tubuhDari(w.slot);
		Warga ortu = ortuHidup(data, w);
		Halls.CandidateSpec spec = KotaSekte.tandai(WargaApi.jadikanCalon(w, owner, "kantor:" + situs), w);
		if (spec == null) {
			return KotaSekte.Hasil.gagal("kantor.tidak_bisa", WargaApi.nama(w)).kirim(player);
		}
		Halls.addCandidates(owner, List.of(spec));
		buangPelamar(server, wargaId);
		ListTag ter = t.getListOrEmpty("ter");
		ter.add(LongTag.valueOf(hari));
		t.put("ter", ter);
		t.putInt("tot", t.getIntOr("tot", 0) + 1);
		Component nama = WargaApi.nama(w);
		catat(t, "kultivator.kota42.sekte.kantor.ev.terima", nama.getString(), hari);
		KotaSekteData.get(server).ubah();
		Advancements.grant(player, "tempat/kota42_kantor");
		if (w.akar >= 5) {
			Advancements.grant(player, "tempat/kota42_akar_langit");
		}
		if (ortu != null) {
			WargaApi.ubahKesan(ortu, player, w.sikapKeluarga == Berbakat.BANGGA ? 6 : 2, "kantor_terima", null);
		}
		if (player.level() instanceof ServerLevel level) {
			GosipWarga.lahir(level, situsWarga, "DIREKRUT", Fakta.Rujuk.warga(w.id), Fakta.Rujuk.kata("sekte_pemain"));
			mulaiPamit(level, situs, owner, w, ortu, tubuhAsli);
		}
		return KotaSekte.Hasil.berhasil("kantor.diterima", nama, KotaSekte.namaSekte(owner)).kirim(player);
	}

	/** {@code tolak:WARGA}: off the list; the family is a little hurt. */
	public static KotaSekte.Hasil tolak(ServerPlayer player, long situs, int wargaId) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = tag(server, situs, player.getUUID(), false);
		WargaData data = WargaData.get(server);
		Warga w = data.byId(wargaId);
		if (t == null || baris(t, wargaId) == null || w == null) {
			return KotaSekte.Hasil.gagal("kantor.tanpa_pelamar").kirim(player);
		}
		ListTag pel = t.getListOrEmpty("pel");
		pel.removeIf(e -> e instanceof CompoundTag c && c.getIntOr("w", 0) == wargaId);
		t.put("pel", pel);
		ListTag ditolak = t.getListOrEmpty("dt");
		ditolak.add(net.minecraft.nbt.IntTag.valueOf(wargaId));
		t.put("dt", ditolak);
		t.putInt("tol", t.getIntOr("tol", 0) + 1);
		catat(t, "kultivator.kota42.sekte.kantor.ev.tolak", WargaApi.nama(w).getString(), KotaSekte.hari(server));
		KotaSekteData.get(server).ubah();
		Warga ortu = ortuHidup(data, w);
		if (ortu != null && w.sikapKeluarga == Berbakat.BANGGA) {
			WargaApi.ubahKesan(ortu, player, -2, "kantor_tolak", null);
		}
		return KotaSekte.Hasil.berhasil("kantor.ditolak", WargaApi.nama(w)).kirim(player);
	}

	/** {@code adakan_ujian}: the open test day (20 NB, every 10 days): applicants x2 today, every one tested before a crowd. */
	public static KotaSekte.Hasil adakanUjian(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		CompoundTag t = tag(server, situs, owner, false);
		if (t == null) {
			return KotaSekte.Hasil.gagal("kantor.tidak_ada").kirim(player);
		}
		long hari = KotaSekte.hari(server);
		long boleh = t.getLongOr("uj", -999L) + JEDA_UJIAN;
		if (hari < boleh) {
			return KotaSekte.Hasil.gagal("kantor.ujian_jeda", boleh).kirim(player);
		}
		if (!EkonomiApi.bayar(player, BIAYA_UJIAN, "kota42.kantor.ujian")) {
			return KotaSekte.Hasil.gagal("uang", Barang.nb(BIAYA_UJIAN)).kirim(player);
		}
		EkonomiApi.kasKota(situs, BIAYA_UJIAN);
		t.putLong("uj", hari);
		gulir(server, situs, t, hari, true);
		catat(t, "kultivator.kota42.sekte.kantor.ev.ujian", KotaSekte.namaTempat(server, situs).getString(), hari);
		KotaSekteData.get(server).ubah();
		if (player.level() instanceof ServerLevel level) {
			mulaiUpacara(level, situs, owner, t);
		}
		return KotaSekte.Hasil.berhasil("kantor.ujian_diadakan", t.getListOrEmpty("pel").size()).kirim(player);
	}

	/** Hires a resident clerk (adult, unemployed, of this place) for the rest of the lease; null wargaId = let go. */
	public static KotaSekte.Hasil juruTulis(ServerPlayer player, long situs, int wargaId) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = tag(server, situs, player.getUUID(), false);
		if (t == null) {
			return KotaSekte.Hasil.gagal("kantor.tidak_ada").kirim(player);
		}
		WargaData data = WargaData.get(server);
		String lama = t.getStringOr("pj", "");
		if (wargaId <= 0) {
			Warga w = lama.startsWith("w:") ? data.byId(parseInt(lama.substring(2))) : null;
			if (w != null) {
				WargaApi.pekerjakan(w, null, null);
			}
			t.putString("pj", "");
			KotaSekteData.get(server).ubah();
			return KotaSekte.Hasil.berhasil("kantor.juru_pergi").kirim(player);
		}
		Warga w = data.byId(wargaId);
		if (w == null || data.situsDari(w.id) != situs || w.status != Warga.Status.HIDUP || WargaApi.umur(w) < 16
				|| WargaApi.majikan(w) != null) {
			return KotaSekte.Hasil.gagal("kantor.juru_tidak_bisa").kirim(player);
		}
		if (lama.startsWith("w:")) {
			return KotaSekte.Hasil.gagal("kantor.juru_sudah").kirim(player);
		}
		long minggu = Math.max(1L, (t.getLongOr("sampai", 0L) - KotaSekte.hari(server) + 6L) / 7L);
		long gaji = gajiJuru(server, situs) * minggu;
		if (!EkonomiApi.bayar(player, gaji, "kota42.kantor.gaji")) {
			return KotaSekte.Hasil.gagal("uang", Barang.nb(gaji)).kirim(player);
		}
		EkonomiApi.kas(player.getUUID(), dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas.SUMBER_GAJI, false, -gaji,
				"kultivator.kota42.sekte.buku.gaji_juru");
		WargaApi.pekerjakan(w, player.getUUID(), "kantor");
		t.putString("pj", "w:" + w.id);
		KotaSekteData.get(server).ubah();
		return KotaSekte.Hasil.berhasil("kantor.juru_dipekerjakan", WargaApi.nama(w), Barang.nb(gaji)).kirim(player);
	}

	/** The clerk's weekly wage of 42.6.2 by place kind (8 / 10 / 12 / 15 NB). */
	public static long gajiJuru(MinecraftServer server, long situs) {
		return switch (PajakKota.kelas(server, situs)) {
			case 4 -> 15L;
			case 3 -> 12L;
			case 2 -> 10L;
			default -> 8L;
		};
	}

	// ================================================================== test helpers (uji kota_hidup sekte kantor)

	/** Test: an office for {@code player} here, free, ignoring the needs. */
	static KotaSekte.Hasil paksaSewa(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		if (tag(server, situs, owner, false) == null) {
			CompoundTag all = semua(server, situs, true);
			long hari = KotaSekte.hari(server);
			CompoundTag t = new CompoundTag();
			t.store("o", UUIDUtil.CODEC, owner);
			t.putLong("mulai", hari);
			t.putLong("sampai", hari + HARI_SEWA);
			t.putLong("uj", -999L);
			t.putLong("roll", hari);
			int no = bilikBebas(all, Math.max(1, bilik(server, situs)));
			t.putInt("b", no);
			if (player.level() instanceof ServerLevel level) {
				tempatkan(level, situs, t, no, player.blockPosition());
			}
			all.put(owner.toString(), t);
			KotaSekteData.get(server).ubah();
		}
		return KotaSekte.Hasil.berhasil("uji.kantor", KotaSekte.namaTempat(server, situs)).kirim(player);
	}

	/** Test: {@code n} applicants at once from every qualifying resident (no chance roll). */
	static KotaSekte.Hasil paksaPelamar(ServerPlayer player, long situs, int n) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = tag(server, situs, player.getUUID(), false);
		if (t == null) {
			return KotaSekte.Hasil.gagal("kantor.tidak_ada").kirim(player);
		}
		WargaData data = WargaData.get(server);
		ListTag pel = t.getListOrEmpty("pel");
		int tambah = 0;
		for (Warga w : WargaApi.wargaTempat(situs)) {
			if (tambah >= n || pel.size() >= PELAMAR_MAKS) {
				break;
			}
			if (layak(w, data, situs) && baris(t, w.id) == null) {
				CompoundTag row = new CompoundTag();
				row.putInt("w", w.id);
				row.putLong("d", KotaSekte.hari(server));
				pel.add(row);
				t.put("pel", pel);
				tambah++;
			}
		}
		KotaSekteData.get(server).ubah();
		return KotaSekte.Hasil.berhasil("uji.pelamar", tambah).kirim(player);
	}

	/** Test: the open test day now (no fee, no wait). */
	static KotaSekte.Hasil paksaUjian(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = tag(server, situs, player.getUUID(), false);
		if (t == null) {
			return KotaSekte.Hasil.gagal("kantor.tidak_ada").kirim(player);
		}
		long hari = KotaSekte.hari(server);
		t.putLong("uj", hari);
		gulir(server, situs, t, hari, true);
		KotaSekteData.get(server).ubah();
		if (player.level() instanceof ServerLevel level) {
			mulaiUpacara(level, situs, player.getUUID(), t);
		}
		return KotaSekte.Hasil.berhasil("kantor.ujian_diadakan", t.getListOrEmpty("pel").size()).kirim(player);
	}

	// ================================================================== daily

	/** Day step: leases run out, applicants come (each office once per day). */
	static void harian(MinecraftServer server, long hari, boolean pertama) {
		if (!KotaConfig.sekte().kantor) {
			return;
		}
		KotaSekteData data = KotaSekteData.get(server);
		for (long situs : situsSemua(server)) {
			CompoundTag all = semua(server, situs, false);
			for (String key : List.copyOf(all.keySet())) {
				CompoundTag t = all.getCompoundOrEmpty(key);
				UUID owner = t.read("o", UUIDUtil.CODEC).orElse(null);
				if (owner == null) {
					all.remove(key);
					continue;
				}
				if (hari >= t.getLongOr("sampai", 0L)) {
					habis(server, situs, owner);
					continue;
				}
				if (t.getLongOr("roll", -1L) < hari) {
					t.putLong("roll", hari);
					gulir(server, situs, t, hari, t.getLongOr("uj", -999L) == hari);
				}
			}
		}
		data.ubah();
	}

	/**
	 * Draws today's applicants of one office ({@code ganda}: the open test day, x2) and drops those who no longer qualify.
	 * Deterministic per (office, day).
	 */
	static void gulir(MinecraftServer server, long situs, CompoundTag t, long hari, boolean ganda) {
		UUID owner = t.read("o", UUIDUtil.CODEC).orElse(null);
		if (owner == null) {
			return;
		}
		WargaData data = WargaData.get(server);
		ListTag pel = t.getListOrEmpty("pel");
		pel.removeIf(e -> !(e instanceof CompoundTag c) || !layak(data.byId(c.getIntOr("w", 0)), data, situs));
		List<Integer> ada = new ArrayList<>();
		for (Tag e : pel) {
			ada.add(((CompoundTag) e).getIntOr("w", 0));
		}
		List<Integer> ditolak = new ArrayList<>();
		for (Tag e : t.getListOrEmpty("dt")) {
			if (e instanceof net.minecraft.nbt.IntTag i) {
				ditolak.add(i.intValue());
			}
		}
		Halls.HallView hall = Halls.hallOf(owner);
		double renown = hall == null ? 0.0D : hall.renown();
		double kualitas = kualitas(server, situs, owner, t);
		double gosip = 1.0D + Math.min(0.5D, 0.1D * t.getIntOr("tot", 0));
		boolean saingan = HariGerbang.perekrutHadir(server, situs);
		double faktorSaingan = saingan ? 0.5D : 1.0D;
		double cabang = CabangKota.bonusPelamar(server, situs, owner);
		int idx = data.cariPemain(owner);
		RandomSource random = RandomSource.create(situs * 0x9E3779B97F4A7C15L ^ hari * 0xC2B2AE3D27D4EB4FL ^ owner.getLeastSignificantBits());
		int baru = 0;
		List<Warga> calon = new ArrayList<>(WargaApi.wargaTempat(situs));
		calon.sort(Comparator.comparingInt(w -> w.id));
		for (Warga w : calon) {
			if (pel.size() >= PELAMAR_MAKS) {
				break;
			}
			if (ada.contains(w.id) || ditolak.contains(w.id) || !layak(w, data, situs)) {
				continue;
			}
			double kesan = 0.0D;
			Warga ortu = ortuHidup(data, w);
			Ingatan ing = ortu == null || idx < 0 ? null : ortu.ingatanDari(idx);
			if (ing != null) {
				kesan = ing.kesan;
			}
			double p = 0.3D * bobot(w.akar) * (1.0D + renown / 1000.0D) * Math.max(0.2D, 1.0D + kesan / 100.0D) * gosip * kualitas
					* faktorSaingan * cabang * (ganda ? 2.0D : 1.0D);
			if (random.nextDouble() < Math.min(0.9D, p)) {
				CompoundTag row = new CompoundTag();
				row.putInt("w", w.id);
				row.putLong("d", hari);
				row.putBoolean("u", ganda);
				row.putBoolean("s", saingan && w.akar >= 3);
				pel.add(row);
				ada.add(w.id);
				baru++;
			}
		}
		t.put("pel", pel);
		if (baru > 0) {
			ListTag pm = t.getListOrEmpty("pm");
			CompoundTag c = new CompoundTag();
			c.putLong("d", hari);
			c.putInt("n", baru);
			pm.add(c);
			while (pm.size() > HARI_SEWA) {
				pm.remove(0);
			}
			t.put("pm", pm);
		}
	}

	private static boolean layak(@Nullable Warga w, WargaData data, long situs) {
		if (w == null || w.status != Warga.Status.HIDUP || w.akar < 2 || data.situsDari(w.id) != situs) {
			return false;
		}
		int umur = WargaApi.umur(w);
		return umur >= UMUR_MIN && umur <= UMUR_MAKS;
	}

	/** Root weight: LOW 0.6, MID 1, HIGH 1.5, HEAVENLY 2.5. */
	static double bobot(int akar) {
		return switch (akar) {
			case 2 -> 0.6D;
			case 3 -> 1.0D;
			case 4 -> 1.5D;
			case 5 -> 2.5D;
			default -> 0.0D;
		};
	}

	/** Keeper quality: a disciple on duty 1.25 (+0.05 per realm above Qi Refining), a clerk 1 + skill / 200, nobody 0.5. */
	static double kualitas(MinecraftServer server, long situs, UUID owner, CompoundTag t) {
		TugasKotaMurid.Tugas murid = TugasKotaMurid.penjagaDi(situs, TugasKotaMurid.SASARAN_KANTOR, owner);
		if (murid != null) {
			return 1.25D + 0.05D * Math.max(0, murid.ranah() - 1);
		}
		String pj = t.getStringOr("pj", "");
		Warga w = pj.startsWith("w:") ? WargaData.get(server).byId(parseInt(pj.substring(2))) : null;
		return w != null && w.status == Warga.Status.HIDUP ? 1.0D + w.ahli / 200.0D : 0.5D;
	}

	private static void habis(MinecraftServer server, long situs, UUID owner) {
		ServerPlayer p = server.getPlayerList().getPlayer(owner);
		if (p != null) {
			Msg.chat(p, Component.translatable("kultivator.kota42.sekte.kantor.habis", KotaSekte.namaTempat(server, situs))
					.withStyle(ChatFormatting.YELLOW));
		}
		hapus(server, situs, owner);
	}

	private static void hapus(MinecraftServer server, long situs, UUID owner) {
		CompoundTag t = tag(server, situs, owner, false);
		if (t != null) {
			String pj = t.getStringOr("pj", "");
			Warga w = pj.startsWith("w:") ? WargaData.get(server).byId(parseInt(pj.substring(2))) : null;
			if (w != null) {
				WargaApi.pekerjakan(w, null, null);
			}
		}
		semua(server, situs, false).remove(owner.toString());
		Tampil tampil = TAMPIL.remove(kunci(situs, owner));
		if (tampil != null) {
			buang(tampil);
		}
		Upacara u = UPACARA.remove(kunci(situs, owner));
		if (u != null) {
			u.tubuh.forEach(KultivatorNpc::vanish);
		}
		TugasKotaMurid.sasaranHilang(server, situs, TugasKotaMurid.SASARAN_KANTOR, owner);
		KotaSekteData.get(server).ubah();
	}

	/** A resident joined a sect: off every office list. */
	private static void buangPelamar(MinecraftServer server, int wargaId) {
		for (long situs : situsSemua(server)) {
			CompoundTag all = semua(server, situs, false);
			for (String key : all.keySet()) {
				CompoundTag t = all.getCompoundOrEmpty(key);
				ListTag pel = t.getListOrEmpty("pel");
				if (pel.removeIf(e -> e instanceof CompoundTag c && c.getIntOr("w", 0) == wargaId)) {
					t.put("pel", pel);
				}
			}
		}
	}

	// ================================================================== bodies (every second)

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (KotaConfig.sekte().kantor) {
			for (long situs : situsSemua(server)) {
				CompoundTag all = semua(server, situs, false);
				for (String key : all.keySet()) {
					CompoundTag t = all.getCompoundOrEmpty(key);
					UUID owner = t.read("o", UUIDUtil.CODEC).orElse(null);
					if (owner != null) {
						tampilkan(level, situs, owner, t, now);
					}
				}
			}
		}
		for (Iterator<Map.Entry<String, Tampil>> it = TAMPIL.entrySet().iterator(); it.hasNext(); ) {
			Tampil t = it.next().getValue();
			boolean jauh = t.batu == null || t.batu.isRemoved()
					|| !level.hasNearbyAlivePlayer(t.batu.getX(), t.batu.getY(), t.batu.getZ(), JARAK_TAMPIL + 32.0D);
			if (jauh || !KotaConfig.sekte().kantor) {
				buang(t);
				it.remove();
			} else if (t.padamPada > 0L && now >= t.padamPada) {
				t.batu.setKeadaan(BatuUjiEntity.KEADAAN_PADAM);
				t.padamPada = 0L;
			}
		}
		for (Iterator<Upacara> it = UPACARA.values().iterator(); it.hasNext(); ) {
			Upacara u = it.next();
			if (langkahUpacara(level, u, now)) {
				it.remove();
			}
		}
		for (Iterator<Pamit> it = PAMIT.iterator(); it.hasNext(); ) {
			if (langkahPamit(level, it.next(), now)) {
				it.remove();
			}
		}
	}

	private static void tampilkan(ServerLevel level, long situs, UUID owner, CompoundTag t, long now) {
		if (!t.contains("x")) {
			if (!tempatkan(level, situs, t, t.getIntOr("b", 1), null)) {
				return;
			}
			KotaSekteData.get(level.getServer()).ubah();
		}
		BlockPos kursi = new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0));
		if (!level.isLoaded(kursi) || !level.hasNearbyAlivePlayer(kursi.getX() + 0.5D, kursi.getY(), kursi.getZ() + 0.5D, JARAK_TAMPIL)) {
			return;
		}
		Direction hadap = Direction.from2DDataValue(t.getIntOr("h", Direction.NORTH.get2DDataValue()));
		String key = kunci(situs, owner);
		Tampil tampil = TAMPIL.computeIfAbsent(key, k -> new Tampil());
		if (tampil.batu == null || tampil.batu.isRemoved()) {
			BlockPos meja = kursi.relative(hadap);
			Vec3 at = Vec3.atBottomCenterOf(meja).add(0.0D, 1.0D, 0.0D);
			if (DuniaBudget.mayAdd(level, KotaHidup.ANGGARAN, at)) {
				BatuUjiEntity batu = KotaSekteEntities.BATU_UJI.create(level, EntitySpawnReason.EVENT);
				if (batu != null) {
					batu.pindah(at, hadap.getOpposite().toYRot());
					batu.ikat(situs, kursi.asLong());
					batu.setKeadaan(BatuUjiEntity.KEADAAN_DIAM);
					if (level.addFreshEntity(batu)) {
						DuniaBudget.track(KotaHidup.ANGGARAN, batu);
						tampil.batu = batu;
					}
				}
			}
		}
		String pj = t.getStringOr("pj", "");
		Warga juru = pj.startsWith("w:") ? WargaData.get(level.getServer()).byId(parseInt(pj.substring(2))) : null;
		if (juru != null && (tampil.juru == null || tampil.juru.isRemoved())) {
			KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, NpcRole.PETUGAS_GILDA, kursi, hadap.toYRot(), juru.look);
			if (npc != null) {
				npc.setPlate(Component.translatable("kultivator.kota42.sekte.kantor.papan_juru", WargaApi.nama(juru), KotaSekte.namaSekte(owner)));
				SosokApi.setUsia(npc, WargaApi.usiaSosok(juru));
				npc.memory().putBoolean("hold", true);
				tampil.juru = npc;
			}
		} else if (juru == null && tampil.juru != null) {
			tampil.juru.vanish();
			tampil.juru = null;
		}
		if (tampil.juru != null && now >= tampil.seruBerikut) {
			tampil.seruBerikut = now + 600L + Math.floorMod(situs, 200L);
			boolean seru = Math.floorMod(now / 600L, 3L) == 0L;
			HidupApi.klip(tampil.juru, seru ? GerakId.SERU_DAGANG : GerakId.HITUNG_SEMPOA, 0, seru ? 60 : 200);
			if (seru) {
				tampil.juru.say(Component.translatable("kultivator.kota42.sekte.kantor.seru." + Math.floorMod(now / 600L, 4L),
						KotaSekte.namaSekte(owner)), 120);
			}
		}
	}

	/** Resolves the desk of booth {@code no}: the {@code rekrut} titik with that {@code bilik}, else the n-th, else near {@code dekat}. */
	private static boolean tempatkan(ServerLevel level, long situs, CompoundTag t, int no, @Nullable BlockPos dekat) {
		List<Titik> meja = new ArrayList<>(KotaTitik.titik(level, situs, KotaTitik.REKRUT));
		meja.sort(Comparator.comparingLong(x -> x.pos().asLong()));
		Titik pilih = null;
		for (Titik x : meja) {
			if (Integer.toString(no).equals(x.data().get("bilik"))) {
				pilih = x;
			}
		}
		if (pilih == null && !meja.isEmpty()) {
			pilih = meja.get(Math.floorMod(no - 1, meja.size()));
		}
		BlockPos pos;
		Direction hadap;
		if (pilih != null) {
			pos = pilih.pos();
			hadap = pilih.hadap().getAxis().isHorizontal() ? pilih.hadap() : Direction.NORTH;
		} else if (dekat != null) {
			pos = dekat;
			hadap = Direction.NORTH;
		} else {
			// no desk titik loaded and nobody to stand in for it: try again on a later pass
			return false;
		}
		t.putInt("x", pos.getX());
		t.putInt("y", pos.getY());
		t.putInt("z", pos.getZ());
		t.putInt("h", hadap.get2DDataValue());
		return true;
	}

	private static void buang(Tampil t) {
		if (t.batu != null && !t.batu.isRemoved()) {
			t.batu.discard();
		}
		if (t.juru != null) {
			t.juru.vanish();
		}
		t.batu = null;
		t.juru = null;
	}

	/** The stone lights in the applicant's colour and element glyphs (also when nobody shows it: then only the line). */
	private static void nyalakan(MinecraftServer server, long situs, UUID owner, Warga w) {
		Tampil t = TAMPIL.get(kunci(situs, owner));
		if (t == null || t.batu == null || t.batu.isRemoved()) {
			return;
		}
		t.batu.setAkar(Math.max(BatuUjiEntity.AKAR_TIDAK_ADA, w.akar - 1));
		t.batu.setUnsur(unsur(w));
		t.batu.setNilai(Math.min(1.0F, 0.25F + w.akar * 0.15F));
		t.batu.setKeadaan(BatuUjiEntity.KEADAAN_MENYALA);
		t.padamPada = server.overworld().getGameTime() + 100L;
		if (t.batu.level() instanceof ServerLevel level) {
			Fx.burst(level, t.batu.position().add(0.0D, 0.6D, 0.0D), t.batu.warna(), 10 + 4 * w.akar, 0.6F);
		}
	}

	/** The element glyphs that light: fewer the better the root (LOW 4, MID 3, HIGH 2, HEAVENLY 1), by a stable hash. */
	static int unsur(Warga w) {
		int n = Mth.clamp(6 - w.akar, 1, 5);
		int mask = 0;
		int seed = w.id * 0x2545F491;
		for (int i = 0; i < 5 && Integer.bitCount(mask) < n; i++) {
			int bit = Math.floorMod(seed + i * 7, 5);
			while ((mask & (1 << bit)) != 0) {
				bit = (bit + 1) % 5;
			}
			mask |= 1 << bit;
		}
		return mask;
	}

	/** {@code kultivator.root.<grade>} text of a {@code Warga.akar} value. */
	public static Component namaAkar(int akar) {
		dev.kultivator.data.RootGrade[] g = dev.kultivator.data.RootGrade.values();
		dev.kultivator.data.RootGrade grade = g[Mth.clamp(akar - 1, 0, g.length - 1)];
		return grade.displayName().copy().withStyle(s -> s.withColor(grade.color()));
	}

	// ================================================================== the open test day

	private static void mulaiUpacara(ServerLevel level, long situs, UUID owner, CompoundTag t) {
		BlockPos kursi = t.contains("x") ? new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0)) : null;
		if (kursi == null || !level.isLoaded(kursi) || !level.hasNearbyAlivePlayer(kursi.getX(), kursi.getY(), kursi.getZ(), JARAK_TAMPIL)) {
			return;
		}
		Upacara u = new Upacara(situs, owner);
		WargaData data = WargaData.get(level.getServer());
		Direction hadap = Direction.from2DDataValue(t.getIntOr("h", Direction.NORTH.get2DDataValue()));
		Direction samping = hadap.getClockWise();
		BlockPos depan = kursi.relative(hadap, 3);
		for (Tag e : t.getListOrEmpty("pel")) {
			if (u.antre.size() >= PELAMAR_MAKS) {
				break;
			}
			Warga w = data.byId(((CompoundTag) e).getIntOr("w", 0));
			if (w == null) {
				continue;
			}
			int i = u.antre.size();
			BlockPos at = depan.relative(samping, i - 5).relative(hadap, i % 2);
			KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, w.peranNpc() == NpcRole.WARGA && WargaApi.umur(w) < 16 ? NpcRole.ANAK : w.peranNpc(),
					at, hadap.getOpposite().toYRot(), w.look);
			if (npc == null) {
				continue;
			}
			npc.setPlate(WargaApi.nama(w).copy());
			SosokApi.setUsia(npc, WargaApi.usiaSosok(w));
			npc.memory().putBoolean("hold", true);
			u.antre.add(w.id);
			u.tubuh.add(npc);
		}
		if (u.antre.isEmpty()) {
			return;
		}
		HidupApi.kerumunan(level, kursi.relative(hadap, 2), 30 + u.antre.size() * 6);
		u.berikut = level.getGameTime() + 60L;
		UPACARA.put(kunci(situs, owner), u);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(kursi, 48.0D)) {
				Msg.title(p, Component.translatable("kultivator.kota42.sekte.kantor.ujian.judul").withStyle(ChatFormatting.GOLD),
						Component.translatable("kultivator.kota42.sekte.kantor.ujian.sub", KotaSekte.namaSekte(owner), u.antre.size()), 10, 60, 20);
			}
		}
	}

	/** One beat of the test day; true when it is over. */
	private static boolean langkahUpacara(ServerLevel level, Upacara u, long now) {
		if (now < u.berikut) {
			return false;
		}
		MinecraftServer server = level.getServer();
		CompoundTag t = tag(server, u.situs, u.pemilik, false);
		BlockPos kursi = t == null ? null : new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0));
		if (t == null || u.selesai) {
			u.tubuh.forEach(KultivatorNpc::vanish);
			HidupApi.bubarkan(level, u.situs);
			return true;
		}
		Direction hadap = Direction.from2DDataValue(t.getIntOr("h", Direction.NORTH.get2DDataValue()));
		if (u.giliran >= 0 && u.giliran < u.tubuh.size()) {
			// the previous youth steps back into the line
			KultivatorNpc prev = u.tubuh.get(u.giliran);
			if (!prev.isRemoved()) {
				HidupApi.klip(prev, GerakId.HORMAT, 0, 30);
			}
		}
		u.giliran++;
		if (u.giliran >= u.antre.size()) {
			u.selesai = true;
			u.berikut = now + 200L;
			for (ServerPlayer p : level.players()) {
				if (p.blockPosition().closerThan(kursi, 48.0D)) {
					Msg.chat(p, Component.translatable("kultivator.kota42.sekte.kantor.ujian.selesai").withStyle(ChatFormatting.GOLD));
				}
			}
			return false;
		}
		Warga w = WargaData.get(server).byId(u.antre.get(u.giliran));
		KultivatorNpc npc = u.tubuh.get(u.giliran);
		if (w != null && !npc.isRemoved()) {
			BlockPos stand = kursi.relative(hadap, 2);
			npc.teleportTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
			npc.setYRot(hadap.getOpposite().toYRot());
			HidupApi.klip(npc, KotaGerakId.UJI_AKAR, 0, 80);
			CompoundTag row = baris(t, w.id);
			if (row != null) {
				row.putBoolean("u", true);
			}
			nyalakan(server, u.situs, u.pemilik, w);
			Component baris = Component.translatable("kultivator.kota42.sekte.kantor.ujian.giliran", WargaApi.nama(w), WargaApi.umur(w), namaAkar(w.akar));
			for (ServerPlayer p : level.players()) {
				if (p.blockPosition().closerThan(kursi, 48.0D)) {
					Msg.chat(p, baris.copy().withStyle(ChatFormatting.GRAY));
				}
			}
			if (w.akar >= 4) {
				Fx.ring(level, npc.position().add(0.0D, 0.1D, 0.0D), BatuUjiEntity.WARNA_AKAR[Mth.clamp(w.akar - 1, 0, 4)], 2.5F);
			}
			KotaSekteData.get(server).ubah();
		}
		u.berikut = now + 100L;
		return false;
	}

	// ================================================================== the farewell

	private static void mulaiPamit(ServerLevel level, long situs, UUID owner, Warga w, @Nullable Warga ortu, @Nullable KultivatorNpc tubuhAsli) {
		CompoundTag t = tag(level.getServer(), situs, owner, false);
		BlockPos kursi = t == null || !t.contains("x") ? null : new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0));
		BlockPos dari = tubuhAsli != null ? tubuhAsli.blockPosition() : kursi;
		if (dari == null || !level.hasNearbyAlivePlayer(dari.getX(), dari.getY(), dari.getZ(), 48.0D)) {
			if (tubuhAsli != null) {
				tubuhAsli.vanish();
			}
			return;
		}
		Upacara u = UPACARA.get(kunci(situs, owner));
		if (u != null) {
			int i = u.antre.indexOf(w.id);
			if (i >= 0 && !u.tubuh.get(i).isRemoved()) {
				// the test-day body is the youth: it leaves from the line
				dari = u.tubuh.get(i).blockPosition();
				u.tubuh.get(i).vanish();
			}
		}
		if (tubuhAsli != null) {
			tubuhAsli.vanish();
		}
		NpcRole peran = WargaApi.umur(w) < 16 ? NpcRole.ANAK : w.peranNpc();
		KultivatorNpc anak = HidupApi.tubuhStaf(level, situs, peran, dari, 0.0F, w.look);
		if (anak == null) {
			return;
		}
		anak.setPlate(WargaApi.nama(w).copy());
		SosokApi.setUsia(anak, WargaApi.usiaSosok(w));
		anak.memory().putBoolean("hold", true);
		KultivatorNpc ibuBapak = null;
		if (ortu != null) {
			ibuBapak = HidupApi.tubuhStaf(level, situs, ortu.peranNpc(), dari.east(), 90.0F, ortu.look);
			if (ibuBapak != null) {
				ibuBapak.setPlate(WargaApi.nama(ortu).copy());
				SosokApi.setUsia(ibuBapak, WargaApi.usiaSosok(ortu));
				ibuBapak.memory().putBoolean("hold", true);
				HidupApi.tatap(ibuBapak, anak);
				HidupApi.tatap(anak, ibuBapak);
				HidupApi.klip(ibuBapak, KotaGerakId.PELUK, 0, 60);
				ibuBapak.say(Component.translatable("kultivator.kota42.sekte.pamit.ortu." + sikapId(w.sikapKeluarga)), 100);
			}
		}
		HidupApi.klip(anak, ortu != null ? KotaGerakId.PELUK : GerakId.HORMAT, 0, 60);
		anak.say(Component.translatable("kultivator.kota42.sekte.pamit.anak." + Math.floorMod(w.id, 4), KotaSekte.namaSekte(owner)), 100);
		PAMIT.add(new Pamit(anak, ibuBapak, tepiKota(level, situs, dari), level.getGameTime()));
	}

	private static String sikapId(int sikap) {
		return switch (sikap) {
			case Berbakat.BANGGA -> "bangga";
			case Berbakat.MENOLAK -> "menolak";
			default -> "ragu";
		};
	}

	/** The point where the walk leaves the place: its box edge nearest to {@code dari}, on the ground. */
	private static BlockPos tepiKota(ServerLevel level, long situs, BlockPos dari) {
		TempatInfo info = TempatApi.tempatDi(level, dari);
		if (info == null) {
			return dari.offset(24, 0, 0);
		}
		net.minecraft.world.level.levelgen.structure.BoundingBox box = info.kotak();
		int[] jarak = {dari.getX() - box.minX(), box.maxX() - dari.getX(), dari.getZ() - box.minZ(), box.maxZ() - dari.getZ()};
		int best = 0;
		for (int i = 1; i < 4; i++) {
			if (jarak[i] < jarak[best]) {
				best = i;
			}
		}
		int x = best == 0 ? box.minX() - 2 : best == 1 ? box.maxX() + 2 : dari.getX();
		int z = best == 2 ? box.minZ() - 2 : best == 3 ? box.maxZ() + 2 : dari.getZ();
		BlockPos ground = TempatApi.groundSpot(level, x, z, dari.getY());
		return ground != null ? ground : new BlockPos(x, dari.getY(), z);
	}

	/** One beat of a farewell; true when it is over. */
	private static boolean langkahPamit(ServerLevel level, Pamit p, long now) {
		long t = now - p.mulai;
		if (p.anak.isRemoved() || t > 1400L) {
			akhiriPamit(level, p);
			return true;
		}
		if (p.fase == 0 && t >= 70L) {
			p.fase = 1;
			HidupApi.klip(p.anak, KotaGerakId.PAMIT_LAMBAI, 0, 40);
			if (p.ortu != null && !p.ortu.isRemoved()) {
				HidupApi.klip(p.ortu, KotaGerakId.PAMIT_LAMBAI, 0, 60);
			}
		} else if (p.fase == 1 && t >= 110L) {
			p.fase = 2;
			p.anak.memory().putBoolean("hold", false);
			SosokApi.gerak(p.anak, GerakId.BERDIRI_JAGA, 0, 1, KotaGerakId.PROP_BUNTELAN, 0, 0);
		}
		if (p.fase == 2) {
			if (p.anak.blockPosition().closerThan(p.keluar, 3.0D)) {
				akhiriPamit(level, p);
				return true;
			}
			p.anak.getNavigation().moveTo(p.keluar.getX() + 0.5D, p.keluar.getY(), p.keluar.getZ() + 0.5D, 0.6D);
			if (p.ortu != null && !p.ortu.isRemoved() && t >= 300L) {
				p.ortu.vanish();
			}
		}
		return false;
	}

	private static void akhiriPamit(ServerLevel level, Pamit p) {
		if (!p.anak.isRemoved()) {
			Fx.burst(level, p.anak.position().add(0.0D, 1.0D, 0.0D), 0xD8C8A8, 10, 0.6F);
			p.anak.vanish();
		}
		if (p.ortu != null && !p.ortu.isRemoved()) {
			p.ortu.vanish();
		}
	}

	// ================================================================== storage

	private static CompoundTag semua(MinecraftServer server, long situs, boolean buat) {
		KotaSekteData data = KotaSekteData.get(server);
		CompoundTag t = data.situs(KotaSekteData.KANTOR, situs);
		CompoundTag k = t.getCompoundOrEmpty(K_KANTOR);
		if (!t.contains(K_KANTOR) && buat) {
			t.put(K_KANTOR, k);
		}
		return k;
	}

	private static @Nullable CompoundTag tag(MinecraftServer server, long situs, UUID owner, boolean buat) {
		CompoundTag all = semua(server, situs, buat);
		String key = owner.toString();
		return all.contains(key) ? all.getCompoundOrEmpty(key) : null;
	}

	/** Every place holding an office. */
	static List<Long> situsSemua(MinecraftServer server) {
		List<Long> out = new ArrayList<>();
		CompoundTag part = KotaSekteData.get(server).bagian(KotaSekteData.KANTOR);
		for (String key : part.keySet()) {
			if (key.startsWith("kota_") && !part.getCompoundOrEmpty(key).getCompoundOrEmpty(K_KANTOR).isEmpty()) {
				try {
					out.add(Long.parseLong(key.substring(5)));
				} catch (NumberFormatException ignored) {
					// not a place key
				}
			}
		}
		return out;
	}

	private static @Nullable CompoundTag baris(CompoundTag t, int wargaId) {
		for (Tag e : t.getListOrEmpty("pel")) {
			if (e instanceof CompoundTag c && c.getIntOr("w", 0) == wargaId) {
				return c;
			}
		}
		return null;
	}

	private static int terimaMinggu(CompoundTag t, long hari) {
		int n = 0;
		ListTag ter = t.getListOrEmpty("ter");
		for (Tag e : ter) {
			if (e instanceof LongTag l && hari - l.longValue() < HARI_SEWA) {
				n++;
			}
		}
		return n;
	}

	private static int bilikBebas(CompoundTag all, int bilik) {
		for (int no = 1; no <= bilik; no++) {
			boolean dipakai = false;
			for (String key : all.keySet()) {
				if (all.getCompoundOrEmpty(key).getIntOr("b", 0) == no) {
					dipakai = true;
				}
			}
			if (!dipakai) {
				return no;
			}
		}
		return 1;
	}

	private static void catat(CompoundTag t, String key, String arg, long hari) {
		ListTag ev = t.getListOrEmpty("ev");
		CompoundTag c = new CompoundTag();
		c.putString("k", key);
		c.putString("a", arg);
		c.putLong("d", hari);
		ev.add(c);
		while (ev.size() > 8) {
			ev.remove(0);
		}
		t.put("ev", ev);
	}

	static @Nullable Warga ortuHidup(WargaData data, Warga w) {
		for (int id : new int[]{w.ayah, w.ibu}) {
			Warga o = id == Warga.TANPA ? null : data.byId(id);
			if (o != null && o.status == Warga.Status.HIDUP) {
				return o;
			}
		}
		return null;
	}

	private static int parseInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	private KantorRekrut() {
	}
}
