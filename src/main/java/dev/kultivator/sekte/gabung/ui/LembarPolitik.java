package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.politik.Cabang;
import dev.kultivator.sekte.gabung.politik.KasSekte;
import dev.kultivator.sekte.gabung.politik.Kursi;
import dev.kultivator.sekte.gabung.politik.PanggilanPerang;
import dev.kultivator.sekte.gabung.politik.PemilihanPewaris;
import dev.kultivator.sekte.gabung.politik.Penyatuan;
import dev.kultivator.sekte.gabung.politik.PeringkatSekte;
import dev.kultivator.sekte.gabung.politik.Politik;
import dev.kultivator.sekte.gabung.politik.Suksesi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The politics of the player's sect ({@code gabung_politik}, DESIGN 41.8 and 41.10.8, keys {@link GabungLembar.Politik}),
 * filled from 41D/41D-P:
 * <ul>
 * <li>Majelis: master, vice master, heir and the elders ({@code Kursi.pemegang}: players and named characters), each
 * elder's attitude toward the player, the sect's resource, the next class threshold and its ranking; for a player master
 * the course ({@code arah}) and the members he may appoint or a member may propose ({@link #CALON}).</li>
 * <li>Suksesi: the three chapters of "Jubah dan Mangkuk" ({@code Suksesi.bab}), the running vote (secret), why the
 * player may (not) walk it, and the heir election ({@code PemilihanPewaris}: stage, finalists).</li>
 * <li>Perang: {@code PanggilanPerang.lembarPolitik} (score, days, missions done, the gate-to-gate line, Duel Juara
 * opt-in); hidden without a war.</li>
 * <li>Kas: this month's donations by name, the 7-day resource graph, the master's weekly share, the treasury in NB and
 * the PK value of the stack in hand.</li>
 * <li>Cabang: the branch halls with their tribute, Restu Gunung, absorb / merge for a player master (two-step).</li>
 * </ul>
 * Ops: the ones of {@link GabungLembar.Politik} go to {@code Politik.aksi} (41D); the extra verbs of this class
 * ({@link #OP_SUKSESI}, {@link #OP_TENTANG}, {@link #OP_PEWARIS}, {@link #OP_TANDING}, {@link #OP_RESTU},
 * {@link #OP_CABANG}, {@link #OP_MERDEKA}) call {@code Suksesi}, {@code PemilihanPewaris} and {@code Cabang}.
 */
public final class LembarPolitik {

	// extra keys (not in GabungLembar.Politik)
	/** D members a player may propose as elder / a player master may appoint: id S (UUID), nama T, pangkat I. */
	public static final String CALON = "calon";
	/** B the viewer is the player master of this sect (course, appointments, absorb/merge). */
	public static final String KETUA_ANDA = "ketua_anda";
	/** B the viewer's rank votes (Inti and above). */
	public static final String BERSUARA = "bersuara";
	/** In {@code suksesi}: I the heir election stage ({@code PemilihanPewaris.TAHAP_*}). */
	public static final String PEWARIS_TAHAP = "pewaris_tahap";
	/** In {@code suksesi}: D the heir election's finalists or candidates: id S, nama T, anda B. */
	public static final String FINALIS = "finalis";
	/** In {@code suksesi}: T why the player may not stand for heir (absent: may). */
	public static final String PEWARIS_ALASAN = "pewaris_alasan";
	/** In {@code suksesi}: T the candidate of a running succession vote. */
	public static final String CALON_KETUA = "calon_ketua";
	/** In {@code suksesi}: I votes cast so far (the choices stay secret). */
	public static final String MASUK = "masuk";
	/** In {@code kas}: L the treasury in NB; I the PK value of the stack in hand. */
	public static final String KAS_NB = "kas_nb";
	public static final String NILAI_TANGAN = "nilai_tangan";
	/** In {@code cabang}: I Restu Gunung progress ({@code Cabang.RESTU_*}), I its offering, T why no branch, T the mother sect. */
	public static final String RESTU = "restu";
	public static final String RESTU_PK = "restu_pk";
	public static final String CABANG_ALASAN = "cabang_alasan";
	public static final String INDUK = "induk";

	// extra ops
	public static final String OP_SUKSESI = "suksesi";
	public static final String OP_TENTANG = "tentang";
	public static final String OP_PEWARIS = "pewaris";
	public static final String OP_TANDING = "tanding";
	public static final String OP_RESTU = "restu";
	public static final String OP_CABANG = "cabang_ajukan";
	public static final String OP_MERDEKA = "merdeka";

	private static final String K = "kultivator.gabung.ui2.politik.";
	private static final int CALON_MAKS = 16;

	static void init() {
		String layar = GabungLembar.Politik.LAYAR;
		GabungLayar.daftar(layar, LembarPolitik::bangun);
		for (String verb : List.of(GabungLembar.Politik.OP_SUARA, GabungLembar.Politik.OP_USUL, GabungLembar.Politik.OP_SUMBANG,
				GabungLembar.Politik.OP_TUNJUK, GabungLembar.Politik.OP_ARAH, GabungLembar.Politik.OP_IKUT_DUEL, GabungLembar.Politik.OP_SERAP,
				GabungLembar.Politik.OP_SERAP_YA, GabungLembar.Politik.OP_LEBUR, GabungLembar.Politik.OP_LEBUR_YA)) {
			GabungLayar.bawaan(layar, verb, (p, npcId, op) -> LembarPapan.hasil(p, Politik.aksi(p, String.join(":", op)),
					Component.translatable(K + "ok." + verb)));
		}
		GabungLayar.bawaan(layar, OP_SUKSESI, (p, npcId, op) -> LembarPapan.hasil(p, Suksesi.lanjut(p), null));
		GabungLayar.bawaan(layar, OP_TENTANG, (p, npcId, op) -> LembarPapan.hasil(p, Suksesi.tentang(p), Component.translatable(K + "ok.tentang")));
		GabungLayar.bawaan(layar, OP_PEWARIS, (p, npcId, op) -> LembarPapan.hasil(p, PemilihanPewaris.daftar(p), Component.translatable(K + "ok.pewaris")));
		GabungLayar.bawaan(layar, OP_TANDING, (p, npcId, op) -> LembarPapan.hasil(p, PemilihanPewaris.tanding(p), null));
		GabungLayar.bawaan(layar, OP_RESTU, (p, npcId, op) -> LembarPapan.hasil(p, Cabang.persembahan(p), null));
		GabungLayar.bawaan(layar, OP_CABANG, (p, npcId, op) -> LembarPapan.hasil(p, Cabang.ajukan(p), Component.translatable(K + "ok.cabang")));
		GabungLayar.bawaan(layar, OP_MERDEKA, (p, npcId, op) -> LembarPapan.hasil(p, Cabang.merdeka(p), null));
	}

	static CompoundTag bangun(ServerPlayer player, int npcId) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		CompoundTag tag = GabungLayar.kepala(player, sekte, Component.translatable(K + "judul"));
		if (sekte == null) {
			return LembarPapan.bukanAnggota(tag);
		}
		MinecraftServer server = player.level().getServer();
		boolean ketuaAnda = Politik.sekteKetua(player) != null;
		tag.putBoolean(KETUA_ANDA, ketuaAnda);
		tag.putBoolean(BERSUARA, k.pangkat.bersuara());
		tag.put(GabungLembar.Politik.MAJELIS, majelis(server, player, sekte));
		tag.put(GabungLembar.Politik.SUKSESI, suksesi(server, player, k, sekte));
		CompoundTag perang = PanggilanPerang.lembarPolitik(player);
		ListTag sembunyi = new ListTag();
		if (perang != null) {
			tag.put(GabungLembar.Politik.PERANG, perang);
		} else {
			sembunyi.add(net.minecraft.nbt.StringTag.valueOf(GabungLembar.Politik.TAB_PERANG));
		}
		tag.put(LembarKunci.TAB_SEMBUNYI, sembunyi);
		tag.put(GabungLembar.Politik.KAS, kas(server, player, sekte, ketuaAnda));
		tag.put(GabungLembar.Politik.CABANG, cabang(server, player, sekte));
		if (ketuaAnda) {
			tag.putString(GabungLembar.Politik.ARAH, Politik.arah(server, sekte));
		}
		tag.put(CALON, calon(server, player, sekte));
		return tag;
	}

	// ================================================================== Majelis

	private static CompoundTag majelis(MinecraftServer server, ServerPlayer player, String sekte) {
		CompoundTag m = new CompoundTag();
		orang(server, player, first(Kursi.pemegang(server, sekte, Pangkat.KETUA)), m, GabungLembar.Politik.KETUA);
		orang(server, player, first(Kursi.pemegang(server, sekte, Pangkat.WAKIL)), m, GabungLembar.Politik.WAKIL);
		orang(server, player, first(Kursi.pemegang(server, sekte, Pangkat.PEWARIS)), m, GabungLembar.Politik.PEWARIS);
		ListTag tetua = new ListTag();
		for (String id : Kursi.pemegang(server, sekte, Pangkat.TETUA)) {
			if (tetua.size() >= 10) {
				break;
			}
			CompoundTag t = orangTag(server, player, id);
			TokohSekte.Tokoh tokoh = TokohSekte.idSah(id) ? TokohSekte.tokoh(server, id) : null;
			Jabatan j = tokoh == null ? null : tokoh.jabatan();
			LembarKunci.teks(t, GabungLembar.JABATAN, j != null ? Component.translatable(j.key()) : Pangkat.TETUA.nama());
			t.putInt(GabungLembar.SIKAP, tokoh != null ? TokohSekte.sikap(server, id, player.getUUID()) : 0);
			tetua.add(t);
		}
		m.put(GabungLembar.Politik.TETUA, tetua);
		m.putInt(GabungLembar.Politik.SUMBER, DiplomasiGabung.sumber(server, sekte));
		m.putInt(GabungLembar.Politik.NAIK, DiplomasiGabung.sumberNaik(server, sekte));
		m.putInt(GabungLembar.Politik.PERINGKAT, PeringkatSekte.peringkat(server, sekte));
		return m;
	}

	private static void orang(MinecraftServer server, ServerPlayer player, @Nullable String id, CompoundTag into, String key) {
		if (id != null) {
			into.put(key, orangTag(server, player, id));
		}
	}

	/** One seat holder: nama T, tokoh S, ranah I (characters, online players), pemain B, anda B. */
	private static CompoundTag orangTag(MinecraftServer server, ServerPlayer player, String id) {
		CompoundTag t = new CompoundTag();
		LembarKunci.teks(t, LembarKunci.NAMA, Kursi.namaPemegang(server, id));
		t.putString(GabungLembar.TOKOH, id);
		UUID uuid = uuid(id);
		t.putBoolean(GabungLembar.PEMAIN, uuid != null);
		t.putBoolean(LembarKunci.ANDA, player.getUUID().equals(uuid));
		if (uuid != null) {
			ServerPlayer online = server.getPlayerList().getPlayer(uuid);
			if (online != null) {
				t.putInt(GabungLembar.RANAH, Cultivators.get(online).realm().ordinal());
			}
		} else if (TokohSekte.idSah(id)) {
			TokohSekte.Tokoh tokoh = TokohSekte.tokoh(server, id);
			if (tokoh != null) {
				t.putInt(GabungLembar.RANAH, tokoh.ranah().ordinal());
			}
		}
		return t;
	}

	// ================================================================== Suksesi

	private static CompoundTag suksesi(MinecraftServer server, ServerPlayer player, Keanggotaan k, String sekte) {
		CompoundTag s = new CompoundTag();
		int bab = Suksesi.bab(player);
		s.putInt(GabungLembar.Politik.BAB, bab);
		ListTag tahap = new ListTag();
		for (int i = Suksesi.BAB_UJIAN; i <= Suksesi.BAB_PENYERAHAN; i++) {
			CompoundTag t = new CompoundTag();
			LembarKunci.teks(t, LembarKunci.TEKS, Component.translatable(K + "bab." + i));
			t.putBoolean(GabungLembar.Politik.SELESAI, bab > i);
			tahap.add(t);
		}
		s.put(LembarKunci.TAHAP, tahap);
		CompoundTag sidang = DiplomasiGabung.bagian(server, sekte).getCompoundOrEmpty("suksesi");
		String calon = sidang.getStringOr("calon", "");
		boolean berjalan = !calon.isEmpty();
		s.putBoolean(GabungLembar.Politik.RAHASIA, berjalan);
		if (berjalan) {
			LembarKunci.teks(s, CALON_KETUA, Kursi.namaPemegang(server, calon));
			s.putInt(MASUK, Suksesi.suara(server, sekte).size());
		}
		Component why = Suksesi.syarat(player);
		s.putBoolean(LembarKunci.BISA, why == null && bab < Suksesi.BAB_SELESAI);
		if (why != null) {
			LembarKunci.teks(s, LembarKunci.ALASAN, why);
		}
		s.putInt(LembarKunci.BIAYA, Suksesi.biaya(sekte));
		// the heir election (41.6.1 Pewaris)
		int pt = PemilihanPewaris.tahap(server, sekte);
		s.putInt(PEWARIS_TAHAP, pt);
		List<String> fin = pt == PemilihanPewaris.TAHAP_SUARA ? PemilihanPewaris.finalis(server, sekte) : PemilihanPewaris.hidup(server, sekte);
		ListTag finalis = new ListTag();
		for (String id : fin) {
			if (finalis.size() >= 8) {
				break;
			}
			CompoundTag f = new CompoundTag();
			f.putString(LembarKunci.ID, id);
			LembarKunci.teks(f, LembarKunci.NAMA, Kursi.namaPemegang(server, id));
			f.putBoolean(LembarKunci.ANDA, id.equals(player.getUUID().toString()));
			finalis.add(f);
		}
		s.put(FINALIS, finalis);
		if (pt == PemilihanPewaris.TAHAP_SUARA) {
			s.putInt(MASUK, Math.max(s.getIntOr(MASUK, 0), PemilihanPewaris.suara(server, sekte).size()));
		}
		Component alasanPewaris = PemilihanPewaris.alasan(player);
		if (alasanPewaris != null) {
			LembarKunci.teks(s, PEWARIS_ALASAN, alasanPewaris);
		}
		return s;
	}

	// ================================================================== Kas

	private static CompoundTag kas(MinecraftServer server, ServerPlayer player, String sekte, boolean ketuaAnda) {
		CompoundTag kas = new CompoundTag();
		ListTag sumbangan = new ListTag();
		for (Map.Entry<UUID, Integer> e : KasSekte.sumbangan(server, sekte).entrySet()) {
			if (sumbangan.size() >= 12) {
				break;
			}
			CompoundTag r = new CompoundTag();
			LembarKunci.teks(r, LembarKunci.NAMA, Kursi.namaPemegang(server, e.getKey().toString()));
			r.putInt(LembarKunci.JUMLAH, e.getValue());
			r.putBoolean(LembarKunci.ANDA, e.getKey().equals(player.getUUID()));
			sumbangan.add(r);
		}
		kas.put(GabungLembar.Politik.SUMBANGAN, sumbangan);
		kas.put(GabungLembar.Politik.GRAFIK, new IntArrayTag(KasSekte.grafik(server, sekte)));
		if (ketuaAnda) {
			kas.putInt(GabungLembar.Politik.KAS_KETUA, KasSekte.kasKetua(server, sekte));
		}
		kas.putLong(KAS_NB, KasSekte.kasNb(server, sekte));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!hand.isEmpty()) {
			kas.putInt(NILAI_TANGAN, KasSekte.nilaiPk(hand));
			LembarKunci.teks(kas, LembarKunci.TANGAN, hand.getHoverName());
		}
		return kas;
	}

	// ================================================================== Cabang

	private static CompoundTag cabang(MinecraftServer server, ServerPlayer player, String sekte) {
		CompoundTag c = new CompoundTag();
		ListTag daftar = new ListTag();
		Map<UUID, Integer> upeti = Cabang.upeti(server, sekte);
		for (UUID owner : Cabang.daftar(server, sekte)) {
			if (daftar.size() >= 16) {
				break;
			}
			CompoundTag r = new CompoundTag();
			Halls.HallView hall = Halls.hallOf(owner);
			Component pemilik = Kursi.namaPemegang(server, owner.toString());
			LembarKunci.teks(r, LembarKunci.NAMA, hall != null && !hall.name().isEmpty() ? Component.literal(hall.name())
					: Component.translatable(K + "cabang.aula_dari", pemilik));
			LembarKunci.teks(r, GabungLembar.Politik.PEMILIK, pemilik);
			r.putInt(GabungLembar.Politik.UPETI, upeti.getOrDefault(owner, Cabang.upetiTerakhir(server, sekte, owner)));
			r.putBoolean(LembarKunci.ANDA, owner.equals(player.getUUID()));
			daftar.add(r);
		}
		c.put(LembarKunci.DAFTAR, daftar);
		boolean bolehSatukan = Penyatuan.syarat(player) == null;
		c.putBoolean(GabungLembar.Politik.SERAP, bolehSatukan);
		c.putBoolean(GabungLembar.Politik.LEBUR, bolehSatukan);
		c.putBoolean(GabungLembar.Politik.TERSERAP, Penyatuan.pemilik(server, sekte) != null);
		c.putInt(RESTU, Cabang.restu(player));
		c.putInt(RESTU_PK, Cabang.RESTU_PK * GabungApi.kelas(sekte));
		Component why = Cabang.syarat(player);
		if (why != null) {
			LembarKunci.teks(c, CABANG_ALASAN, why);
		}
		String induk = Cabang.induk(server, player.getUUID());
		if (induk != null) {
			LembarKunci.teks(c, INDUK, DiplomasiGabung.nama(server, induk));
		}
		return c;
	}

	// ================================================================== candidates for usul / tunjuk

	/** Fellow members of Inti and above (players) the viewer may propose or appoint (not the viewer). */
	private static ListTag calon(MinecraftServer server, ServerPlayer player, String sekte) {
		ListTag out = new ListTag();
		GabungData data = GabungData.get(server);
		for (UUID id : data.anggotaSekte(sekte)) {
			if (out.size() >= CALON_MAKS || id.equals(player.getUUID())) {
				continue;
			}
			Keanggotaan m = data.peek(id);
			if (m == null || !m.anggotaDi(sekte) || !m.pangkat.minimal(Pangkat.INTI) || m.pangkat == Pangkat.TAMU || m.pangkat == Pangkat.KETUA) {
				continue;
			}
			CompoundTag r = new CompoundTag();
			r.putString(LembarKunci.ID, id.toString());
			LembarKunci.teks(r, LembarKunci.NAMA, Kursi.namaPemegang(server, id.toString()));
			r.putInt(GabungLembar.PANGKAT, m.pangkat.ordinal());
			out.add(r);
		}
		return out;
	}

	private static @Nullable String first(List<String> list) {
		return list.isEmpty() ? null : list.getFirst();
	}

	private static @Nullable UUID uuid(String s) {
		if (s == null || s.length() != 36) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private LembarPolitik() {
	}
}
