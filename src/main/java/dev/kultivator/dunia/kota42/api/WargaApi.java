package dev.kultivator.dunia.kota42.api;

import dev.kultivator.data.RootGrade;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.cakap.Cakap;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.Berbakat;
import dev.kultivator.dunia.kota42.warga.Hubungan;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.NamaWarga;
import dev.kultivator.dunia.kota42.warga.Pekerjaan;
import dev.kultivator.dunia.kota42.warga.SiklusHidup;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.kota42.warga.WargaGen;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.PendudukData;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The resident API of Kota yang Hidup (DESIGN 42.15.3, contract C42.A1), static, server side. Every other 42 module reads
 * and changes residents only through here. While {@code kotaWarga.aktif} is off, or before a place was raised, readers
 * return null/empty and {@link #terapkan} returns the 38 slot unchanged, so the 38 behaviour stays exactly as it was.
 * <p>
 * 42A bodies: the slot bridge ({@link #terapkan}, {@link #keadaan} with the 42 states, {@link #namaPapan}), names from
 * {@code nama.json}, ages in Fana Years, the face and memories of 42A-2 ({@code Suasana}, {@code Ingatan}), player deeds
 * ({@link #peristiwaPemain}: impressions, gossip, 42K appraisal), hall candidates from gifted children
 * ({@link #jadikanCalon}), a disciple's family ({@link #keluargaMurid}), player employment ({@link #pekerjakan}) and the
 * walking companion ({@link #mitraJalan}). Non-resident civilians (34 anchors, service staff) map to a stored person of
 * their place ({@link #dari}).
 */
public final class WargaApi {

	private static final List<PenimpaJadwalKota> PENIMPA = new CopyOnWriteArrayList<>();

	/** True while residents run ({@code kotaWarga.aktif} and a server). */
	public static boolean aktif() {
		return KotaHidup.server() != null && KotaConfig.warga().aktif;
	}

	// ================================================================== lookup

	/** The Warga occupying slot {@code pendudukId} of place {@code situs}, or null. */
	public static @Nullable Warga warga(long situs, int pendudukId) {
		MinecraftServer server = KotaHidup.server();
		WargaData.Situs s = server == null ? null : WargaData.get(server).peek(situs);
		return s == null ? null : s.diSlotCepat(pendudukId, server.getTickCount());
	}

	/**
	 * The Warga a body stands for: a resident body maps to its slot; other civilians (34 town anchors, service staff of
	 * {@code HidupApi.tubuhStaf}) map to a resident of that place with the same role and no body, else to a staff Warga made
	 * for them. Null for non-civilians and while 42 is off.
	 */
	public static @Nullable Warga dari(KultivatorNpc npc) {
		if (npc == null || !aktif()) {
			return null;
		}
		Penduduk p = HidupApi.pendudukDari(npc);
		if (p != null) {
			return warga(npc.situsPenduduk(), p.id());
		}
		return staf(npc);
	}

	/**
	 * A civilian body that is not a resident body stands for one stored person of its place: the Warga already linked to
	 * its look ({@link Warga#staf}), else an adult resident of the same role without a body (linked from now on), else a new
	 * staff Warga of that role (no slot), so every civilian has a name and a memory (42.4.14).
	 */
	private static @Nullable Warga staf(KultivatorNpc npc) {
		WargaData data = data();
		long situs = npc.town();
		WargaData.Situs s = data == null || situs == 0L ? null : data.peek(situs);
		NpcRole role = npc.role();
		if (s == null || !s.bangkit() || role == null || !(role.townsfolk() || role.stationary) || !(npc.level() instanceof ServerLevel level)
				|| !KotaHidup.duniaKota(level)) {
			return null;
		}
		int look = npc.look();
		for (Warga w : s.warga) {
			if (w.staf == look && w.hidup()) {
				return w;
			}
		}
		long hari = KotaHidup.hari(level.getServer());
		TempatHuni huni = Pemanggil.huni(situs);
		for (Warga w : s.warga) {
			if (w.staf == 0 && w.status == Warga.Status.HIDUP && w.peran.equals(role.id()) && SiklusHidup.umurPada(w, hari) >= 16
					&& (huni == null || w.slot == Warga.TANPA_SLOT || huni.tubuhDari(w.slot) == null)) {
				w.staf = look;
				data.ubah();
				return w;
			}
		}
		RandomSource random = RandomSource.create(look * 0x9E3779B97F4A7C15L ^ situs);
		Warga baru = WargaGen.pendatang(data, role, 22, 58, hari, random);
		baru.look = look;
		baru.kelamin = npc.female() ? Warga.WANITA : Warga.PRIA;
		baru.suara = npc.voice();
		baru.datang = -1L;
		baru.staf = look;
		baru.rumahTangga = s.rumahTanggaBaru();
		data.tambah(situs, baru);
		return baru;
	}

	public static @Nullable Warga byId(int id) {
		WargaData data = data();
		return data == null ? null : data.byId(id);
	}

	/** The stored residents of a place (empty before it was raised). A copy. */
	public static List<Warga> wargaTempat(long situs) {
		WargaData data = data();
		WargaData.Situs s = data == null ? null : data.peek(situs);
		return s == null ? List.of() : List.copyOf(s.warga);
	}

	/** A read-only snapshot for screens and commands. */
	public static WargaInfo info(Warga w) {
		WargaData data = data();
		long situs = data == null ? 0L : data.situsDari(w.id);
		int umur = umur(w);
		return new WargaInfo(w.id, situs, nama(w), !w.pria(), umur, WargaInfo.Tahap.dari(umur), w.peranNpc(),
				kodeWajah(w, 0L), w.akar, w.status.name().toLowerCase(java.util.Locale.ROOT), w.tokoh);
	}

	// ================================================================== 38 slot bridge (42.4.14)

	/**
	 * The 38 slot as the stored person makes it: the person's look, voice, house and household; role, bed, work spot and
	 * seed stay the slot's (the work titik and the schedule offset belong to the slot). Unchanged while 42 is off or no
	 * Warga occupies the slot.
	 */
	public static Penduduk terapkan(long situs, Penduduk p) {
		Warga w = aktif() ? warga(situs, p.id()) : null;
		if (w == null) {
			return p;
		}
		int rumah = w.rumah != Penduduk.TANPA ? w.rumah : p.rumah();
		int kelompok = p.tamu() ? Penduduk.TAMU : w.rumahTangga > 0 ? w.rumahTangga : p.kelompok();
		return new Penduduk(p.id(), p.peran(), w.look, w.suara, rumah, p.kasur(), p.kerja(), kelompok, p.benih());
	}

	/**
	 * The life state of slot {@code id} at game time {@code now}: {@code PendudukData.DATANG} (arrived today),
	 * {@code PENGANTIN} (the wedding day and the day after), {@code SAKIT} (health below 30), {@code DUKA} (a household
	 * death in the last 2 days), {@code BERKABUNG} (days 2-7 after it), {@code MABUK} (the night drinker), else
	 * {@code SEHAT} (the 38 wound state {@code PULIH} stays with {@code PendudukData}).
	 */
	public static int keadaan(long situs, int id, long now) {
		Warga w = aktif() ? warga(situs, id) : null;
		MinecraftServer server = KotaHidup.server();
		if (w == null || server == null) {
			return PendudukData.SEHAT;
		}
		long hari = KotaHidup.hari(server);
		if (w.datang >= 0L && hari - w.datang < 1L) {
			return PendudukData.DATANG;
		}
		if (w.menikahHari >= 0L && hari >= w.menikahHari && hari - w.menikahHari <= 1L) {
			return PendudukData.PENGANTIN;
		}
		if (w.kesehatan < 30) {
			return PendudukData.SAKIT;
		}
		WargaData data = data();
		WargaData.Situs s = data == null ? null : data.peek(situs);
		long duka = s == null ? -1L : s.dukaRumahTangga(w.rumahTangga);
		if (duka >= 0L && hari >= duka) {
			if (hari - duka < 2L) {
				return PendudukData.DUKA;
			}
			if (hari - duka < 8L) {
				return PendudukData.BERKABUNG;
			}
		}
		if (Suasana.kode(w, now) == KodeWajah.MABUK) {
			return PendudukData.MABUK;
		}
		return PendudukData.SEHAT;
	}

	/** The name plate of a body: "Zhang Ming'an · Koki", else the role title. */
	public static Component namaPapan(long situs, Penduduk p) {
		Warga w = aktif() ? warga(situs, p.id()) : null;
		if (w == null) {
			return Component.translatable(p.peran().titleKey());
		}
		return Component.translatable("kultivator.kota.papan_nama", nama(w), Component.translatable(p.peran().titleKey()));
	}

	/** The full name of a resident ({@code NamaWarga}, {@code data/kultivator/kota/nama.json}; pinyin reads the same everywhere). */
	public static Component nama(Warga w) {
		return NamaWarga.komponen(w);
	}

	// ================================================================== age, mood

	/** Age in Fana Years ({@code kotaWarga.hariPerTahun} world days each). */
	public static int umur(Warga w) {
		MinecraftServer server = KotaHidup.server();
		return server == null ? 0 : SiklusHidup.umurPada(w, KotaHidup.hari(server));
	}

	/** The Sosok age ({@code Sosok.ANAK/MUDA/DEWASA/TUA}) for {@code SosokApi.setUsia}. */
	public static int usiaSosok(Warga w) {
		return WargaInfo.Tahap.dari(umur(w)).usiaSosok;
	}

	/** The face for {@code SosokApi.setSuasana(npc, wajah, 0)} ({@code Gaya.SUASANA_*}). */
	public static int wajah(Warga w, long now) {
		return kodeWajah(w, now).sosok;
	}

	/** The mood code with its special causes (fear, sickness, anger, drink, mourning; 42A-2 {@code Suasana.kode}). */
	public static KodeWajah kodeWajah(Warga w, long now) {
		return Suasana.kode(w, now);
	}

	/** The mean mood of a place's residents (0 when none). */
	public static int suasanaTempat(long situs) {
		return Suasana.tempatRata(situs);
	}

	// ================================================================== memory of players (42.4.10)

	/** The resident's memory of {@code player}, created on first use (least recently met dropped beyond the cap). */
	public static Ingatan ingatan(Warga w, UUID player) {
		WargaData data = data();
		return data == null ? new Ingatan(-1) : Ingatan.untuk(data, w, player);
	}

	/**
	 * Changes the resident's impression of {@code player} by {@code delta} ({@code sebab} names the cause for logs and
	 * gossip); a non-null {@code kuat} is remembered as a strong event. Impression 60 grants {@code kota42_sahabat}.
	 */
	public static void ubahKesan(Warga w, ServerPlayer player, int delta, String sebab, @Nullable Kenangan kuat) {
		if (w == null || player == null || !aktif()) {
			return;
		}
		Ingatan i = ingatan(w, player.getUUID());
		MinecraftServer server = KotaHidup.server();
		long hari = server == null ? 0L : KotaHidup.hari(server);
		i.ubah(delta, kuat, false, hari);
		if (i.kesan >= 60) {
			Advancements.grant(player, "tempat/kota42_sahabat");
		}
		WargaData data = data();
		if (data != null) {
			data.ubah();
		}
	}

	/**
	 * A player did something residents of {@code situs} notice ({@code jenis}: an {@code Ingatan.Jenis} id such as "sapa",
	 * "bantu", "pukul", "curi", "angpao"...). {@code isian} names the residents it was done to ({@link Warga},
	 * {@link KultivatorNpc} or resident id); with none, the resident bodies within 16 blocks witness it (at most 8). Each
	 * gets the impression and memory of 42A-2 {@code Ingatan.peristiwa} (household and gossip rules there) and the 42K
	 * appraisal ({@code Cakap.peristiwa}).
	 */
	public static void peristiwaPemain(ServerPlayer player, long situs, String jenis, Object... isian) {
		if (player == null || jenis == null || !aktif() || !(player.level() instanceof ServerLevel level) || !KotaHidup.duniaKota(level)) {
			return;
		}
		WargaData data = data();
		if (data == null || data.peek(situs) == null) {
			return;
		}
		List<Warga> sasaran = new ArrayList<>();
		for (Object o : isian == null ? new Object[0] : isian) {
			Warga w = o instanceof Warga ww ? ww : o instanceof KultivatorNpc npc ? dari(npc) : o instanceof Integer id ? byId(id) : null;
			if (w != null && !sasaran.contains(w)) {
				sasaran.add(w);
			}
		}
		if (sasaran.isEmpty()) {
			for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(HidupApi.SAKSI_JARAK),
					n -> n.isAlive() && n.penduduk() && n.situsPenduduk() == situs)) {
				Warga w = dari(npc);
				if (w != null && !sasaran.contains(w) && sasaran.size() < 8) {
					sasaran.add(w);
				}
			}
		}
		int idx = data.indeksPemain(player.getUUID());
		for (Warga w : sasaran) {
			int got = Ingatan.peristiwa(player, situs, w, jenis);
			Ingatan i = w.ingatanDari(idx);
			if (i != null && i.kesan >= 60) {
				Advancements.grant(player, "tempat/kota42_sahabat");
			}
			Cakap.peristiwa(w, jenis, Fakta.Rujuk.pemain(idx), Math.min(1.0F, Math.abs(got) / 30.0F + 0.2F));
		}
	}

	// ================================================================== sects and work

	/** Gifted children of a place: spirit root >= LOW, age 8-15, living here, not yet recruited. */
	public static List<Warga> berbakat(long situs) {
		List<Warga> list = new ArrayList<>();
		for (Warga w : wargaTempat(situs)) {
			int umur = umur(w);
			if (w.status == Warga.Status.HIDUP && w.akar >= 2 && umur >= 8 && umur <= 15) {
				list.add(w);
			}
		}
		return list;
	}

	/**
	 * Turns a gifted resident into a hall candidate for {@code owner} ({@code sumber} = {@code "kota:<situs>"}): the
	 * candidate's root is the resident's, its resolve follows temperament and the family's attitude, the card says "from
	 * Zhang Wenbao"; the Warga becomes {@code SEKTE} (kept in the data, family intact, archived as a living entry). Null
	 * while 42 is off or the resident cannot join (not living here, mortal).
	 */
	public static @Nullable Halls.CandidateSpec jadikanCalon(Warga w, UUID owner, String sumber) {
		MinecraftServer server = KotaHidup.server();
		// 42A-2 Berbakat owns the conversion (family consent, job exit, archive, card)
		return w == null || owner == null || server == null || !aktif() ? null : Berbakat.jadikanCalon(server, w, owner, sumber);
	}

	/**
	 * The family resident of a disciple who came from a town (39 visits): the living mother or father, else a living
	 * sibling. Null when the soul never was a resident ({@code WargaData.catatMurid}).
	 */
	public static @Nullable Warga keluargaMurid(UUID soul) {
		WargaData data = data();
		Warga murid = data == null || soul == null ? null : data.byId(data.muridDari(soul));
		if (murid == null) {
			return null;
		}
		for (int id : new int[]{murid.ibu, murid.ayah}) {
			Warga w = id == Warga.TANPA ? null : data.byId(id);
			if (w != null && w.status == Warga.Status.HIDUP) {
				return w;
			}
		}
		for (int parent : new int[]{murid.ibu, murid.ayah}) {
			Warga p = parent == Warga.TANPA ? null : data.byId(parent);
			for (int c : p == null ? new int[0] : p.anak) {
				Warga sib = data.byId(c);
				if (sib != null && sib != murid && sib.status == Warga.Status.HIDUP) {
					return sib;
				}
			}
		}
		return null;
	}

	/**
	 * Employs a resident for a player ({@code tugas}: "toko", "gudang"...; 42C shop staff); a null or empty {@code tugas}
	 * ends it. Kept in 42A-2's {@code ArsipWarga.Catatan} (keys {@code majikan}, {@code tugas}, {@code kerjaSejak});
	 * fires {@code KotaBus.KERJA_BERUBAH} with {@code "pemain:<tugas>"}.
	 */
	public static void pekerjakan(Warga w, UUID majikan, String tugas) {
		MinecraftServer server = KotaHidup.server();
		WargaData data = data();
		if (w == null || server == null || data == null || !aktif()) {
			return;
		}
		long situs = data.situsDari(w.id);
		ArsipWarga.Catatan catatan = ArsipWarga.catatan(server);
		String lama = catatan.warga(w.id).getStringOr("tugas", "");
		boolean selesai = majikan == null || tugas == null || tugas.isEmpty();
		if (selesai) {
			Pekerjaan.lepas(server, w);
			catatan.warga(w.id).remove("kerjaSejak");
		} else {
			Pekerjaan.pekerjakan(server, situs, w, majikan, tugas);
			catatan.warga(w.id).putLong("kerjaSejak", KotaHidup.hari(server));
		}
		catatan.ubah();
		KotaBus.KERJA_BERUBAH.invoker().onKerjaBerubah(server, situs, w, lama.isEmpty() ? w.peran : "pemain:" + lama,
				selesai ? w.peran : "pemain:" + tugas);
	}

	/** The player employing {@code w} ({@link #pekerjakan}), or null. */
	public static @Nullable UUID majikan(Warga w) {
		return w == null || KotaHidup.server() == null ? null : Pekerjaan.majikan(w);
	}

	/**
	 * The partner a body walks beside, or null (42.4.14 {@code mitraJalan}; {@code TubuhPenduduk} then checks distance and
	 * that the partner is walking too): the lover; the spouse at dusk (17-21); a grandparent and a grandchild of 10 or less.
	 */
	public static @Nullable KultivatorNpc mitraJalan(KultivatorNpc npc) {
		KultivatorNpc grup = npc == null ? null : dev.kultivator.dunia.kota42.ragam.KegiatanKelompok.mitra(npc);
		if (grup != null) {
			return grup;
		}
		Warga w = npc == null || !npc.penduduk() ? null : dari(npc);
		MinecraftServer server = KotaHidup.server();
		TempatHuni huni = w == null ? null : Pemanggil.huni(npc.situsPenduduk());
		WargaData data = data();
		if (huni == null || server == null || data == null) {
			return null;
		}
		long hari = KotaHidup.hari(server);
		int jam = (int) ((Kota.timeOfDay(server) / 1000L + 6L) % 24L);
		List<Integer> calon = new ArrayList<>(4);
		int kekasih = Hubungan.kekasih(w);
		if (kekasih != Warga.TANPA) {
			calon.add(kekasih);
		}
		if (w.pasangan != Warga.TANPA && jam >= 17 && jam < 21) {
			calon.add(w.pasangan);
		}
		int umur = SiklusHidup.umurPada(w, hari);
		if (umur <= 10) {
			for (int parent : new int[]{w.ayah, w.ibu}) {
				Warga p = parent == Warga.TANPA ? null : data.byId(parent);
				if (p != null) {
					calon.add(p.ayah);
					calon.add(p.ibu);
				}
			}
		} else if (umur >= 55) {
			for (int c : w.anak) {
				Warga child = data.byId(c);
				for (int g : child == null ? new int[0] : child.anak) {
					Warga cucu = data.byId(g);
					if (cucu != null && SiklusHidup.umurPada(cucu, hari) <= 10) {
						calon.add(g);
					}
				}
			}
		}
		for (int id : calon) {
			Warga o = id == Warga.TANPA ? null : data.byId(id);
			if (o != null && o.status == Warga.Status.HIDUP && o.slot != Warga.TANPA_SLOT && data.situsDari(o.id) == npc.situsPenduduk()) {
				KultivatorNpc body = huni.tubuhDari(o.slot);
				if (body != null) {
					return body;
				}
			}
		}
		return null;
	}

	// ================================================================== schedule overriders

	/** Registers a 42 schedule overrider (bridged by 42A into {@code Jadwal.addPenimpa}). */
	public static void daftarPenimpa(PenimpaJadwalKota penimpa) {
		if (penimpa != null) {
			PENIMPA.add(penimpa);
			PENIMPA.sort(Comparator.comparingInt(PenimpaJadwalKota::urutan));
		}
	}

	/** The registered overriders in order (for 42A's bridge). */
	public static List<PenimpaJadwalKota> penimpa() {
		return PENIMPA;
	}

	private static @Nullable WargaData data() {
		MinecraftServer server = KotaHidup.server();
		return server == null ? null : WargaData.get(server);
	}

	private WargaApi() {
	}
}
