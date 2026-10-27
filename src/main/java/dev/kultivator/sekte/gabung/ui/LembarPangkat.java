package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.Promosi;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.politik.Kursi;
import dev.kultivator.sekte.gabung.ui.GabungLembar.PohonPangkat;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rank tree ({@code gabung_pangkat}, DESIGN 41.6.1 and 41.10.6, keys {@link PohonPangkat}): the disciple line
 * Pelayan → Inti → Pewaris, the elder line Tetua → Wakil, the master at the top of both, the guest elder on the side.
 * Each node carries its colour ({@code SectRanks.TRIM} family), its state for this player, its seats and who holds them
 * ({@code Kursi.pemegang}, contract C41.D1). Below: the requirement lines of the selected rank (next one by default),
 * its rights and PK cost, the seat summary, "Ajukan Kenaikan" and the seat holders that may be challenged.
 * <p>
 * Requirement lines of a rank: realm ({@code Pangkat.syaratRanah}; Tercatat: Qi Refining or 50 PK earned), days
 * (since joining, the last promotion, or as elder), PK ({@code GabungApi.biaya}), the rank's trial flag
 * ({@link GabungLayar#lulus}, set by the module that runs it), a free seat, not in confinement. Default ops (41A may
 * replace {@code ajukan} with its hall ceremony through {@link GabungLayar#daftarAksi}): {@code ajukan:ID} pays and
 * promotes through {@code GabungApi.setPangkat}; {@code tantang:ID} goes to {@code Kursi.tantang}; {@code pilih:ID} only
 * changes the detailed node.
 */
public final class LembarPangkat {

	/** Rights lines per rank in ordinal order (lang {@code kultivator.gabung.ui.hak.<id>.<n>}). */
	private static final int[] HAK_JUMLAH = {3, 3, 4, 4, 4, 4, 4, 3, 3, 3};
	private static final int PEMEGANG_MAKS = 10;

	private static final Map<UUID, Pangkat> PILIH = new HashMap<>();

	static void init() {
		GabungLayar.daftar(PohonPangkat.LAYAR, LembarPangkat::bangun);
		GabungLayar.bawaan(PohonPangkat.LAYAR, PohonPangkat.OP_AJUKAN, (p, npcId, op) -> ajukan(p, Pangkat.byId(LembarKunci.arg(op, 1))));
		GabungLayar.bawaan(PohonPangkat.LAYAR, PohonPangkat.OP_TANTANG, (p, npcId, op) -> tantang(p, LembarKunci.arg(op, 1)));
		GabungLayar.bawaan(PohonPangkat.LAYAR, PohonPangkat.OP_PILIH, (p, npcId, op) -> {
			Pangkat pilih = Pangkat.byId(LembarKunci.arg(op, 1));
			if (pilih != null) {
				PILIH.put(p.getUUID(), pilih);
			}
		});
	}

	static CompoundTag bangun(ServerPlayer player, int npcId) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		CompoundTag tag = GabungLayar.kepala(player, sekte, Component.translatable("kultivator.gabung.ui.pangkat.judul"));
		if (sekte == null) {
			CompoundTag kunci = new CompoundTag();
			LembarKunci.teks(kunci, LembarKunci.ALASAN, Component.translatable("kultivator.gabung.ui.bukan_anggota"));
			LembarKunci.teks(kunci, LembarKunci.SYARAT, Component.translatable("kultivator.gabung.ui.bukan_anggota.syarat"));
			tag.put(LembarKunci.KUNCI_LAYAR, kunci);
			return tag;
		}
		MinecraftServer server = player.level().getServer();
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		Pangkat kini = k.pangkat;
		ListTag simpul = new ListTag();
		Map<Pangkat, List<String>> kursi = new HashMap<>();
		for (Pangkat p : Pangkat.values()) {
			List<String> pemegang = Kursi.pemegang(server, sekte, p);
			kursi.put(p, pemegang);
			CompoundTag n = new CompoundTag();
			n.putInt(GabungLembar.PANGKAT, p.ordinal());
			n.putString(LembarKunci.ID, p.id());
			LembarKunci.teks(n, LembarKunci.NAMA, p.nama());
			n.putInt(GabungLembar.WARNA, p.warna());
			n.putString(PohonPangkat.JALUR, jalur(p));
			n.putString(LembarKunci.KEADAAN, keadaan(kini, p));
			int maks = p.kursi(kelas);
			n.putInt(PohonPangkat.KURSI, pemegang.size());
			n.putInt(PohonPangkat.KURSI_MAKS, Math.max(0, maks));
			ListTag holders = new ListTag();
			for (String id : pemegang) {
				if (holders.size() >= PEMEGANG_MAKS) {
					break;
				}
				holders.add(pemegangTag(server, player, id));
			}
			n.put(PohonPangkat.PEMEGANG, holders);
			simpul.add(n);
		}
		tag.put(PohonPangkat.SIMPUL, simpul);
		tag.putInt(PohonPangkat.KINI, kini.ordinal());
		Pangkat pilih = PILIH.getOrDefault(player.getUUID(), berikutBawaan(kini));
		if (pilih == null) {
			pilih = kini;
		}
		tag.putInt(PohonPangkat.PILIH, pilih.ordinal());
		List<CompoundTag> syarat = syaratNaik(player, k, sekte, pilih, kursi.get(pilih));
		ListTag syaratList = new ListTag();
		syarat.forEach(syaratList::add);
		tag.put(LembarKunci.SYARAT, syaratList);
		ListTag hak = new ListTag();
		for (int i = 1; i <= HAK_JUMLAH[pilih.ordinal()]; i++) {
			CompoundTag h = new CompoundTag();
			LembarKunci.teks(h, LembarKunci.TEKS, Component.translatable("kultivator.gabung.ui.hak." + pilih.id() + "." + i, kelas.teknikMaks().displayName()));
			hak.add(h);
		}
		tag.put(PohonPangkat.HAK, hak);
		tag.putInt(LembarKunci.BIAYA, GabungApi.biaya(sekte, pilih.biaya()));
		LembarKunci.teks(tag, PohonPangkat.RINGKAS, ringkas(server, kelas, kursi));
		Component alasan = alasanAjukan(k, pilih, syarat);
		tag.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, alasan);
		}
		tag.put(PohonPangkat.TANTANG, tantangList(server, player, k, kelas, pilih, kursi.get(pilih)));
		return tag;
	}

	// ================================================================== the requirement lines (shared with the Kartu)

	/** The default target: the next rank on the disciple line (Inti: Pewaris), null at the top or for the guest elder. */
	static @Nullable Pangkat berikutBawaan(Pangkat kini) {
		return kini.berikut(false);
	}

	/** True when {@code tujuan} is one step above {@code kini} (Inti forks to Pewaris or Tetua). */
	static boolean langsung(Pangkat kini, Pangkat tujuan) {
		return tujuan != null && (kini.berikut(false) == tujuan || kini.berikut(true) == tujuan);
	}

	/** Every requirement line of rising to {@code tujuan} ({@code [ok, teks, punya]}). */
	static List<CompoundTag> syaratNaik(ServerPlayer player, Keanggotaan k, String sekte, Pangkat tujuan, @Nullable List<String> pemegang) {
		List<CompoundTag> out = new ArrayList<>();
		if (tujuan == Pangkat.TAMU) {
			out.add(GabungLayar.syarat(k.pangkat == Pangkat.TAMU, Component.translatable("kultivator.gabung.ui.syarat.tamu"), null, null));
			return out;
		}
		MinecraftServer server = player.level().getServer();
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		int tier = Math.max(1, GabungApi.tier(sekte));
		CultivationData cd = Cultivators.get(player);
		Realm ranah = cd.rank().realm();
		long hari = GabungApi.hari(server);
		// realm (Tercatat: Qi Refining or 50 PK earned with chores)
		Realm butuh = tujuan.syaratRanah(kelas, tier);
		if (tujuan == Pangkat.TERCATAT) {
			boolean ok = ranah.ordinal() >= Realm.QI_REFINING.ordinal() || k.pkTotal >= 50L;
			out.add(GabungLayar.syarat(ok, Component.translatable("kultivator.gabung.ui.syarat.tercatat", Realm.QI_REFINING.displayName()),
					Component.translatable("kultivator.gabung.ui.punya.ranah_pk", cd.rank().displayName(), k.pkTotal), null));
		} else {
			out.add(GabungLayar.syarat(ranah.ordinal() >= butuh.ordinal(), Component.translatable("kultivator.gabung.ui.syarat.ranah", butuh.displayName()),
					cd.rank().displayName(), null));
		}
		// days
		long sejak;
		String kunciHari;
		switch (tujuan) {
			case TERCATAT -> {
				sejak = k.hariMasuk;
				kunciHari = "masuk";
			}
			case PEWARIS, TETUA -> {
				sejak = k.hariMasuk;
				kunciHari = "anggota";
			}
			case WAKIL -> {
				sejak = k.pangkat == Pangkat.TETUA || k.pangkat == Pangkat.WAKIL ? k.hariPangkat : hari + 1;
				kunciHari = "tetua";
			}
			default -> {
				sejak = k.hariPangkat;
				kunciHari = "pangkat";
			}
		}
		int perlu = tujuan == Pangkat.TERCATAT ? 3 : tujuan.hariMin();
		if (perlu > 0) {
			long lewat = sejak < 0L ? 0L : Math.max(0L, hari - sejak);
			out.add(GabungLayar.syarat(lewat >= perlu, Component.translatable("kultivator.gabung.ui.syarat.hari." + kunciHari, perlu),
					Component.translatable("kultivator.gabung.ui.punya.hari", lewat), null));
		}
		// PK
		int biaya = GabungApi.biaya(sekte, tujuan.biaya());
		if (biaya > 0) {
			out.add(GabungLayar.syarat(k.pk >= biaya, Component.translatable("kultivator.gabung.ui.syarat.pk", biaya),
					Component.translatable("kultivator.gabung.ui.punya.pk", k.pk), null));
		}
		// the rank's own trial (set by the module that runs it)
		if (tujuan.ordinal() >= Pangkat.LUAR.ordinal() && tujuan != Pangkat.TAMU) {
			out.add(GabungLayar.syarat(GabungLayar.lulus(k, tujuan), Component.translatable("kultivator.gabung.ui.syarat.ujian." + tujuan.id()),
					null, Component.translatable("kultivator.gabung.ui.saran.ujian." + tujuan.id())));
		}
		// a free seat
		int maks = tujuan.kursi(kelas);
		if (maks > 0) {
			int terisi = pemegang == null ? Kursi.pemegang(server, sekte, tujuan).size() : pemegang.size();
			out.add(GabungLayar.syarat(terisi < maks, Component.translatable("kultivator.gabung.ui.syarat.kursi", terisi, maks), null,
					terisi < maks ? null : Component.translatable("kultivator.gabung.ui.saran.kursi")));
		}
		// not confined in the Gua Renungan
		if (k.kurunganSampai > player.level().getGameTime()) {
			out.add(GabungLayar.syarat(false, Component.translatable("kultivator.gabung.ui.syarat.kurungan"), null, null));
		}
		return out;
	}

	/** Why "Ajukan Kenaikan" is refused for {@code tujuan}, or null when it may be asked now. */
	static @Nullable Component alasanAjukan(Keanggotaan k, Pangkat tujuan, List<CompoundTag> syarat) {
		if (tujuan == k.pangkat) {
			return Component.translatable("kultivator.gabung.ui.pangkat.sudah");
		}
		if (k.pangkat == Pangkat.TAMU) {
			return Component.translatable("kultivator.gabung.ui.pangkat.tamu");
		}
		if (tujuan == Pangkat.KETUA) {
			return Component.translatable("kultivator.gabung.ui.pangkat.suksesi");
		}
		if (!langsung(k.pangkat, tujuan)) {
			return Component.translatable("kultivator.gabung.ui.pangkat.satu_satu");
		}
		int gagal = 0;
		Component pertama = null;
		for (CompoundTag s : syarat) {
			if (!s.getBooleanOr(LembarKunci.OK, false)) {
				gagal++;
				pertama = pertama == null ? LembarKunci.teks(s, LembarKunci.TEKS) : pertama;
			}
		}
		// The trial is the only missing line and the hall ceremony (Promosi) starts it when asked: the button is open.
		if (gagal == 1 && !GabungLayar.lulus(k, tujuan) && GabungLayar.adaAksi(PohonPangkat.LAYAR, PohonPangkat.OP_AJUKAN)
				&& Promosi.mulaiUjian(tujuan)) {
			return null;
		}
		return pertama;
	}

	// ================================================================== node helpers

	private static String jalur(Pangkat p) {
		return switch (p) {
			case TETUA, WAKIL, KETUA -> PohonPangkat.JALUR_TETUA;
			case TAMU -> PohonPangkat.JALUR_SAMPING;
			default -> PohonPangkat.JALUR_MURID;
		};
	}

	private static String keadaan(Pangkat kini, Pangkat p) {
		if (p == kini) {
			return PohonPangkat.K_KINI;
		}
		if (kini == Pangkat.TAMU || p == Pangkat.TAMU) {
			return PohonPangkat.K_TERKUNCI;
		}
		if (langsung(kini, p)) {
			return PohonPangkat.K_TERBUKA;
		}
		boolean cabangLain = p == Pangkat.PEWARIS && (kini == Pangkat.TETUA || kini == Pangkat.WAKIL)
				|| (p == Pangkat.TETUA || p == Pangkat.WAKIL) && kini == Pangkat.PEWARIS;
		if (p.tingkat() < kini.tingkat() && !cabangLain) {
			return PohonPangkat.K_LEWAT;
		}
		return PohonPangkat.K_TERKUNCI;
	}

	private static CompoundTag pemegangTag(MinecraftServer server, ServerPlayer player, String id) {
		CompoundTag h = new CompoundTag();
		LembarKunci.teks(h, LembarKunci.NAMA, GabungLayar.namaPemegang(server, id));
		boolean pemain = id.indexOf('#') < 0;
		h.putString(GabungLembar.TOKOH, id);
		h.putBoolean(GabungLembar.PEMAIN, pemain);
		h.putBoolean(LembarKunci.ANDA, id.equals(player.getUUID().toString()));
		return h;
	}

	private static int sikap(MinecraftServer server, String id, UUID pemain) {
		if (TokohSekte.idSah(id)) {
			return TokohSekte.sikap(server, id, pemain);
		}
		return id.indexOf('#') > 0 ? Roster.sikap(server, id, pemain) : 0;
	}

	/** "Kursi Inti 5/6 · Pewaris: Lin Shuangjian · Tetua 5/6 · Tamu 1/2". */
	private static Component ringkas(MinecraftServer server, KelasSekte kelas, Map<Pangkat, List<String>> kursi) {
		MutableComponent out = Component.translatable("kultivator.gabung.ui.pangkat.ringkas.inti", kursi.get(Pangkat.INTI).size(),
				Pangkat.INTI.kursi(kelas));
		List<String> pewaris = kursi.get(Pangkat.PEWARIS);
		out.append(" · ").append(Component.translatable("kultivator.gabung.ui.pangkat.ringkas.pewaris",
				pewaris.isEmpty() ? Component.translatable("kultivator.gabung.ui.kosong_kursi") : GabungLayar.namaPemegang(server, pewaris.getFirst())));
		out.append(" · ").append(Component.translatable("kultivator.gabung.ui.pangkat.ringkas.tetua", kursi.get(Pangkat.TETUA).size(),
				Pangkat.TETUA.kursi(kelas)));
		out.append(" · ").append(Component.translatable("kultivator.gabung.ui.pangkat.ringkas.tamu", kursi.get(Pangkat.TAMU).size(),
				Pangkat.TAMU_MAKS));
		return out;
	}

	/** Seat holders of {@code pilih} the player may challenge (41.6.1: friendly Duel Pedang, once per 7 days). */
	private static ListTag tantangList(MinecraftServer server, ServerPlayer player, Keanggotaan k, KelasSekte kelas, Pangkat pilih,
			List<String> pemegang) {
		ListTag list = new ListTag();
		int maks = pilih.kursi(kelas);
		if (maks <= 0 || pilih == Pangkat.KETUA) {
			return list;
		}
		boolean penuh = pemegang.size() >= maks;
		boolean bawah = langsung(k.pangkat, pilih);
		for (String id : pemegang) {
			if (list.size() >= PEMEGANG_MAKS || id.equals(player.getUUID().toString())) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, id);
			LembarKunci.teks(t, LembarKunci.NAMA, GabungLayar.namaPemegang(server, id));
			t.putInt(GabungLembar.SIKAP, sikap(server, id, player.getUUID()));
			boolean bisa = bawah && penuh;
			t.putBoolean(LembarKunci.BISA, bisa);
			if (!bisa) {
				LembarKunci.teks(t, LembarKunci.ALASAN, Component.translatable(!bawah ? "kultivator.gabung.ui.tantang.bawah"
						: "kultivator.gabung.ui.tantang.belum_penuh"));
			}
			list.add(t);
		}
		return list;
	}

	// ================================================================== defaults of the ops

	/** {@code ajukan:ID}: pays the PK and promotes (41A's hall ceremony replaces this through {@code daftarAksi}). */
	static void ajukan(ServerPlayer player, @Nullable Pangkat tujuan) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || tujuan == null) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.bukan_anggota"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		List<CompoundTag> syarat = syaratNaik(player, k, sekte, tujuan, null);
		Component alasan = alasanAjukan(k, tujuan, syarat);
		if (alasan != null) {
			GabungLayar.pesan(player, alasan, LembarKunci.PESAN_BAHAYA);
			return;
		}
		int biaya = GabungApi.biaya(sekte, tujuan.biaya());
		if (biaya > 0 && !GabungApi.bayarPk(player, biaya, "gabung.naik")) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kurang_pk", biaya), LembarKunci.PESAN_BAHAYA);
			return;
		}
		GabungApi.setPangkat(player, tujuan, "gabung.naik");
		GabungLayar.hapusLulus(k, tujuan);
		GabungData.get(player.level().getServer()).setDirty();
		PILIH.remove(player.getUUID());
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.pangkat.naik", tujuan.nama()), LembarKunci.PESAN_OK);
	}

	/** {@code /kultivasi gabung naik}: the first next rank whose lines all pass, else the reason of the default one. */
	static void ajukanBerikut(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.bukan_anggota"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		for (boolean tetua : new boolean[] {false, true}) {
			Pangkat t = k.pangkat.berikut(tetua);
			if (t != null && alasanAjukan(k, t, syaratNaik(player, k, sekte, t, null)) == null) {
				if (GabungLayar.adaAksi(PohonPangkat.LAYAR, PohonPangkat.OP_AJUKAN)) {
					// a module runs the promotion (hall ceremony): show the tree with the button ready
					PILIH.put(player.getUUID(), t);
					GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.pangkat.tekan_ajukan"), LembarKunci.PESAN_OK);
					GabungLayar.bukaLayar(player, PohonPangkat.LAYAR, -1, null);
				} else {
					ajukan(player, t);
				}
				return;
			}
		}
		Pangkat t = berikutBawaan(k.pangkat);
		Component alasan = t == null ? Component.translatable("kultivator.gabung.ui.pangkat.puncak")
				: alasanAjukan(k, t, syaratNaik(player, k, sekte, t, null));
		GabungLayar.pesan(player, alasan == null ? Component.translatable("kultivator.gabung.ui.pangkat.puncak") : alasan, LembarKunci.PESAN_BAHAYA);
	}

	/** {@code tantang:ID}: the seat challenge of {@code Kursi.tantang}. */
	static void tantang(ServerPlayer player, String pemegang) {
		Component tolak = Kursi.tantang(player, pemegang);
		if (tolak != null) {
			GabungLayar.pesan(player, tolak, LembarKunci.PESAN_BAHAYA);
		} else {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.tantang.diajukan",
					GabungLayar.namaPemegang(player.level().getServer(), pemegang)), LembarKunci.PESAN_OK);
		}
	}

	private LembarPangkat() {
	}
}
