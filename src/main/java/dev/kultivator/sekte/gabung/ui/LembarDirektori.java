package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.api.SyaratMasuk;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.masuk.HariUjian;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Direktori;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Direktori Sekte sheet ({@code gabung_direktori}, DESIGN 41.10.1, keys {@link Direktori}): every sect of the world
 * in live strength order. Sects the player knows ({@code Keanggotaan.dikenal}, 41.5.1) show their whole card (class and
 * tier, alignment, corak, master and realm, distance and bearing of the gate, attitude, the requirement lines of the best
 * door, the next test day, cloud palace and MEROSOT); the others are "?" cards with a hint where to hear of them. Tabs
 * Peringkat (strength board with a 7-day trend arrow) and Sekteku (members: rank, PK and this month's Papan Jasa).
 * Opened from the hub card "Direktori Sekte", {@code /kultivasi gabung direktori|info}, or a recruiter
 * ({@link #buka} with its NPC id).
 */
public final class LembarDirektori {

	/** Requirement lines shown on a card (the general ones first; the rest is on the Lamaran). */
	private static final int SYARAT_MAKS = 6;
	/** Rows of the month's Papan Jasa. */
	private static final int JASA_MAKS = 10;
	/** Days of strength positions kept per sect for the trend arrow. */
	private static final int TREN_HARI = 7;

	private static final Map<UUID, String> PILIH = new HashMap<>();

	static void init() {
		GabungLayar.daftar(Direktori.LAYAR, LembarDirektori::bangun);
		GabungLayar.bawaan(Direktori.LAYAR, Direktori.OP_LAMAR, (p, npcId, op) -> LembarLamaran.buka(p, LembarKunci.arg(op, 1), npcId));
		GabungLayar.bawaan(Direktori.LAYAR, Direktori.OP_KARTU, (p, npcId, op) -> GabungLayar.bukaLayar(p, GabungLembar.Kartu.LAYAR, npcId, null));
	}

	/** Opens the directory for {@code player} with {@code sekte} selected (null = none), from NPC {@code npcId} or -1. */
	public static void buka(ServerPlayer player, @Nullable String sekte, int npcId) {
		pilih(player, sekte);
		GabungLayar.bukaLayar(player, Direktori.LAYAR, npcId, null);
	}

	/** Selects {@code sekte} in the next directory sheet of {@code player}. */
	static void pilih(ServerPlayer player, @Nullable String sekte) {
		if (sekte == null || sekte.isEmpty()) {
			PILIH.remove(player.getUUID());
		} else {
			PILIH.put(player.getUUID(), sekte);
		}
	}

	static CompoundTag bangun(ServerPlayer player, int npcId) {
		MinecraftServer server = player.level().getServer();
		DiplomasiService d = SekteApi.diplomasi();
		Keanggotaan k = GabungApi.anggota(player);
		String sendiri = k.sekteAktif();
		CompoundTag tag = GabungLayar.kepala(player, sendiri, Component.translatable("kultivator.gabung.ui.direktori.judul"));
		GerbangPlan.Plan plan = GerbangPlan.of(server.overworld().getSeed());
		List<GerbangPlan.Entry> urut = urutanHidup(plan, d);
		long hari = GabungApi.hari(server);
		catatTren(server, urut, hari);

		ListTag daftar = new ListTag();
		ListTag peringkat = new ListTag();
		int dikenal = 0;
		for (int i = 0; i < urut.size() && daftar.size() < LembarKunci.DAFTAR_MAKS; i++) {
			GerbangPlan.Entry e = urut.get(i);
			String id = e.id();
			boolean kenal = k.kenal(e.index()) || id.equals(k.sekte) && k.status != StatusAnggota.BEBAS;
			CompoundTag row = kenal ? kartu(player, k, e, i + 1, hari) : asing(e, i + 1);
			daftar.add(row);
			if (kenal) {
				dikenal++;
			}
			peringkat.add(barisPeringkat(server, d, e, i + 1, kenal, id.equals(sendiri), hari));
		}
		tag.put(LembarKunci.DAFTAR, daftar);
		tag.put(Direktori.PERINGKAT, peringkat);
		tag.putInt(LembarKunci.JUMLAH, urut.size());
		tag.putInt(Direktori.DIKENAL_JUMLAH, dikenal);
		LembarKunci.teks(tag, LembarKunci.SUBJUDUL, Component.translatable("kultivator.gabung.ui.direktori.sub", urut.size(), dikenal));
		if (sendiri != null) {
			tag.put(Direktori.SEKTEKU, sekteku(server, k, sendiri));
		} else {
			tag.put(LembarKunci.TAB_SEMBUNYI, stringList(Direktori.TAB_SEKTEKU));
		}
		if (urut.isEmpty()) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable("kultivator.gabung.ui.direktori.kosong"));
			tag.put(LembarKunci.KOSONG, kosong);
		}
		String pilih = PILIH.remove(player.getUUID());
		if (pilih != null) {
			tag.putString(Direktori.PILIH, pilih);
		}
		return tag;
	}

	// ================================================================== rows

	/** The full card of a known sect. */
	private static CompoundTag kartu(ServerPlayer player, Keanggotaan k, GerbangPlan.Entry e, int peringkat, long hari) {
		MinecraftServer server = player.level().getServer();
		DiplomasiService d = SekteApi.diplomasi();
		String id = e.id();
		int tier = Math.max(1, d.tier(id) > 0 ? d.tier(id) : e.tier());
		KelasSekte kelas = KelasSekte.dariTier(tier);
		CompoundTag row = new CompoundTag();
		row.putString(LembarKunci.ID, id);
		row.putBoolean(Direktori.DIKENAL, true);
		LembarKunci.teks(row, LembarKunci.NAMA, GabungLayar.namaSekte(id));
		row.putInt(Direktori.PERINGKAT, peringkat);
		row.putInt(GabungLembar.KELAS, kelas.nomor());
		LembarKunci.teks(row, Direktori.GELAR_KELAS, kelas.gelar());
		row.putInt(GabungLembar.TINGKAT, tier);
		row.putInt(GabungLembar.ALIRAN, Math.max(0, d.aliran(id) >= 0 ? d.aliran(id) : e.aliran()));
		row.putString(GabungLembar.CORAK, GabungApi.corak(id).id());
		row.putInt(GabungLembar.WARNA, d.color(id));
		row.putInt(GabungLembar.LAMBANG, d.emblem(id).pack());
		row.putBoolean(GabungLembar.LANGIT, GabungLayar.punyaIstana(server, id));
		GabungData.DataSekteGabung ds = GabungData.get(server).peekSekte(id);
		row.putBoolean(GabungLembar.MEROSOT, ds != null && ds.merosot);
		Component ketua = d.master(id);
		LembarKunci.teks(row, Direktori.KETUA, ketua != null ? ketua : e.master());
		TokohSekte.Tokoh tk = TokohSekte.ketua(server, id);
		row.putInt(Direktori.RANAH_KETUA, (tk != null ? tk.ranah() : KelasSekte.ranahKetua(tier)).ordinal());
		int sikap = d.standing(player.getUUID(), id);
		row.putInt(GabungLembar.SIKAP, sikap);
		LembarKunci.teks(row, GabungLembar.SIKAP_NAMA, Component.translatable(d.stance(player.getUUID(), id).key()));
		BlockPos gerbang = d.gate(id);
		if (gerbang == null) {
			gerbang = d.plannedGate(id);
		}
		if (gerbang == null) {
			gerbang = e.column();
		}
		row.putInt(LembarKunci.X, gerbang.getX());
		row.putInt(LembarKunci.Z, gerbang.getZ());
		if (player.level().dimension() == Level.OVERWORLD) {
			double dx = gerbang.getX() + 0.5D - player.getX();
			double dz = gerbang.getZ() + 0.5D - player.getZ();
			row.putInt(LembarKunci.JARAK, (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
			row.putInt(Direktori.ARAH, GabungLayar.arah(dx, dz));
		} else {
			row.putInt(LembarKunci.JARAK, -1);
			row.putInt(Direktori.ARAH, -1);
		}
		row.putBoolean(Direktori.ANGGOTA, k.anggotaDi(id));
		row.putBoolean(Direktori.CALON, k.status == StatusAnggota.CALON && id.equals(k.sekte));
		row.putLong(GabungLembar.HARI_UJIAN, kelas == KelasSekte.KECIL ? hari
				: HariUjian.berikut(e.index(), hari, GabungConfig.get().hariUjian));
		if (!k.anggotaDi(id)) {
			pintuTerbaik(player, id, row);
		}
		return row;
	}

	/** The "?" card of a sect not yet heard of: only its place in the strength order and a hint. */
	private static CompoundTag asing(GerbangPlan.Entry e, int peringkat) {
		CompoundTag row = new CompoundTag();
		row.putString(LembarKunci.ID, e.id());
		row.putBoolean(Direktori.DIKENAL, false);
		row.putInt(Direktori.PERINGKAT, peringkat);
		LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.gabung.ui.direktori.asing"));
		LembarKunci.teks(row, Direktori.PETUNJUK, Component.translatable("kultivator.gabung.ui.direktori.petunjuk." + Math.floorMod(e.index(), 4)));
		return row;
	}

	/**
	 * The best door's requirement lines (41.10.1 "Syarat: ✓Fondasi ✓MID ✗Usia"): the Ujian Gerbang when the player passes
	 * it, else the servant door when that one passes, else the Ujian Gerbang with its crosses.
	 */
	private static void pintuTerbaik(ServerPlayer player, String sekte, CompoundTag row) {
		List<SyaratMasuk.Baris> gerbang = SyaratMasuk.periksa(player, sekte, Pintu.GERBANG);
		Pintu pintu = Pintu.GERBANG;
		List<SyaratMasuk.Baris> baris = gerbang;
		if (!SyaratMasuk.lolos(gerbang)) {
			List<SyaratMasuk.Baris> pelayan = SyaratMasuk.periksa(player, sekte, Pintu.PELAYAN);
			if (SyaratMasuk.lolos(pelayan)) {
				pintu = Pintu.PELAYAN;
				baris = pelayan;
			}
		}
		row.putString(GabungLembar.PINTU, pintu.id());
		ListTag syarat = new ListTag();
		for (SyaratMasuk.Baris b : baris) {
			if (syarat.size() >= SYARAT_MAKS) {
				break;
			}
			if ("aktif".equals(b.kunci()) && b.ok()) {
				continue;
			}
			CompoundTag s = new CompoundTag();
			s.putBoolean(LembarKunci.OK, b.ok());
			LembarKunci.teks(s, LembarKunci.TEKS, b.teks());
			syarat.add(s);
		}
		row.put(LembarKunci.SYARAT, syarat);
	}

	private static CompoundTag barisPeringkat(MinecraftServer server, DiplomasiService d, GerbangPlan.Entry e, int peringkat, boolean kenal,
			boolean anda, long hari) {
		String id = e.id();
		int tier = Math.max(1, d.tier(id) > 0 ? d.tier(id) : e.tier());
		CompoundTag row = new CompoundTag();
		row.putString(LembarKunci.ID, id);
		LembarKunci.teks(row, LembarKunci.NAMA, kenal ? GabungLayar.namaSekte(id) : Component.translatable("kultivator.gabung.ui.direktori.asing"));
		row.putInt(Direktori.KEKUATAN, kekuatan(d, e));
		row.putInt(Direktori.TREN, tren(server, id, peringkat, hari));
		row.putInt(GabungLembar.KELAS, KelasSekte.of(tier));
		row.putInt(Direktori.PERINGKAT, peringkat);
		row.putBoolean(LembarKunci.ANDA, anda);
		if (kenal) {
			row.putInt(GabungLembar.WARNA, d.color(id));
		}
		return row;
	}

	/** The members' tab: rank, PK and this month's Papan Jasa (players of the sect and the roster seniors). */
	private static CompoundTag sekteku(MinecraftServer server, Keanggotaan k, String sekte) {
		CompoundTag t = new CompoundTag();
		t.putInt(GabungLembar.PANGKAT, k.pangkat.ordinal());
		t.putInt(LembarKunci.PK, k.pk);
		t.putLong(GabungLembar.PK_TOTAL, k.pkTotal);
		long hari = GabungApi.hari(server);
		long bulan = Math.floorDiv(hari, 30L);
		record Jasa(Component nama, int pk, boolean pemain, boolean anda) {
		}
		List<Jasa> semua = new ArrayList<>();
		GabungData data = GabungData.get(server);
		for (UUID uuid : data.anggotaSekte(sekte)) {
			Keanggotaan m = data.peek(uuid);
			if (m == null) {
				continue;
			}
			int pk = m.bulan == bulan ? m.pkBulan : 0;
			semua.add(new Jasa(GabungLayar.namaPemegang(server, uuid.toString()), pk, true, m == k));
		}
		double kf = KelasSekte.dariTier(Math.max(1, GabungApi.tier(sekte))).kf();
		for (Roster.Tokoh r : Roster.daftar(server, sekte)) {
			if (!r.aktif()) {
				continue;
			}
			// the seniors' own month on the board: rank-weighted, varied by their seed and the month
			long h = r.benih() * 0x9E3779B97F4A7C15L + bulan * 0xC2B2AE3D27D4EB4FL;
			h ^= h >>> 29;
			int dasar = 25 + r.peranNomor() * 45 + (int) Math.floorMod(h, 90L);
			semua.add(new Jasa(r.nama(), (int) Math.round(dasar * kf), false, false));
		}
		semua.sort(Comparator.comparingInt(Jasa::pk).reversed());
		ListTag jasa = new ListTag();
		for (Jasa j : semua) {
			if (jasa.size() >= JASA_MAKS && !j.anda()) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, j.nama());
			row.putLong(GabungLembar.PK_TOTAL, j.pk());
			row.putBoolean(GabungLembar.PEMAIN, j.pemain());
			row.putBoolean(LembarKunci.ANDA, j.anda());
			jasa.add(row);
		}
		t.put(Direktori.PAPAN_JASA, jasa);
		return t;
	}

	// ================================================================== strength order and trend

	/** The world's sects strongest first: live tier, then the plan's own order (41.4.2, ties as {@code LangitNpcPlan}). */
	static List<GerbangPlan.Entry> urutanHidup(GerbangPlan.Plan plan, DiplomasiService d) {
		List<GerbangPlan.Entry> rencana = KelasSekte.urutan(plan);
		Map<String, Integer> asal = new HashMap<>();
		for (int i = 0; i < rencana.size(); i++) {
			asal.put(rencana.get(i).id(), i);
		}
		List<GerbangPlan.Entry> out = new ArrayList<>(rencana);
		out.sort(Comparator.comparingInt((GerbangPlan.Entry e) -> -kekuatan(d, e)).thenComparingInt(e -> asal.get(e.id())));
		return out;
	}

	/** A strength number for the board: live tier x 100 plus the plan band, so ties keep the plan order. */
	private static int kekuatan(DiplomasiService d, GerbangPlan.Entry e) {
		int tier = d.tier(e.id()) > 0 ? d.tier(e.id()) : e.tier();
		return tier * 100 + e.band() * 10 + Math.max(0, d.aliran(e.id()) == 0 ? 2 : 1);
	}

	/** Records today's board position of every sect once per day (kept {@link #TREN_HARI} + 1 days). */
	private static void catatTren(MinecraftServer server, List<GerbangPlan.Entry> urut, long hari) {
		GabungData data = GabungData.get(server);
		boolean dirty = false;
		for (int i = 0; i < urut.size(); i++) {
			CompoundTag ui = data.sekte(urut.get(i).id()).bagian(GabungLayar.BAGIAN_UI);
			if (ui.getLongOr("tren_hari", -1L) == hari) {
				continue;
			}
			ListTag list = ui.getListOrEmpty("tren").copy();
			list.add(IntTag.valueOf(i + 1));
			while (list.size() > TREN_HARI + 1) {
				list.remove(0);
			}
			ui.put("tren", list);
			ui.putLong("tren_hari", hari);
			dirty = true;
		}
		if (dirty) {
			data.setDirty();
		}
	}

	/** -1 fell, 0 same, 1 rose over the last 7 recorded days. */
	private static int tren(MinecraftServer server, String sekte, int sekarang, long hari) {
		GabungData.DataSekteGabung ds = GabungData.get(server).peekSekte(sekte);
		if (ds == null) {
			return 0;
		}
		ListTag list = ds.bagian(GabungLayar.BAGIAN_UI).getListOrEmpty("tren");
		if (list.size() < 2) {
			return 0;
		}
		int lama = list.getIntOr(0, sekarang);
		return Integer.compare(lama, sekarang);
	}

	private static ListTag stringList(String... values) {
		ListTag list = new ListTag();
		for (String v : values) {
			list.add(net.minecraft.nbt.StringTag.valueOf(v));
		}
		return list;
	}

	private LembarDirektori() {
	}
}
