package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.PenimpaJadwalKota;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaData;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The town morning (DESIGN 42.7.2), every day but the rest day for school:
 * <ul>
 * <li><b>05:00-06:30 tai chi</b> in the square: the residents with the habit {@code tai_chi} (at most 16) stand in a grid of
 * four columns facing the eldest of them, who leads in front ({@code PIMPIN}); every body plays {@code tai_chi_pagi} in
 * step from the session's start ({@code SosokApi.gerakSinkron}). A player who stands in the rows for 60 s gets
 * <i>Napas Pagi</i> (Qi regeneration +5 % for 10 minutes, once a day).</li>
 * <li><b>06:00-08:00 morning market</b>: three to six {@code tenda_pasar} on the {@code pasar_pagi_<n>} titik of 42W (or a row
 * beside the square), a farmer or vendor at each calling out with a basket; food is 5 % cheaper until 08:00.</li>
 * <li><b>07:00 to school</b>: the teacher (the character {@code guru_zhou}, a {@code GURU_SEKOLAH}, a scholar, or a teacher
 * of his own when the town has none) walks from door to door; each child comes out when he passes, the mothers wave at
 * the door, and the line follows him 1.2 blocks apart to the {@code sekolah} titik (else the temple, else the square).
 * <b>07:15-10:00</b> the children sit in rows reciting the Three Character Classic (bubbles, voice
 * {@code kota42.sekolah_hafal}), the teacher teaches; <b>10:00</b> they run out to play.</li>
 * </ul>
 * Residents are moved through one schedule overrider (order 120); the teacher of his own and the tents are driven here.
 */
public final class RutinitasPagi {

	/** Most tai chi players in the grid. */
	public static final int TAI_CHI_MAKS = 16;
	/** Seconds in the rows for Napas Pagi. */
	public static final int NAPAS_DETIK = 60;
	/** Teacher's walking pace (blocks per tick) along the door-to-door route. */
	public static final double LAJU_GURU = 0.07D;
	/** Distance between children in the line. */
	public static final double JARAK_BARIS = 1.2D;
	/** Morning market: food price factor until 08:00. */
	public static final double HARGA_PAGI = 0.95D;

	/** One place's morning plan for one day (built lazily, deterministic). */
	static final class Pagi {
		final long hari;
		final List<Integer> taiChi = new ArrayList<>();
		int pemimpin = -1;
		BlockPos pusat = BlockPos.ZERO;
		float arah;
		final List<BlockPos> tenda = new ArrayList<>();
		final List<Integer> pedagang = new ArrayList<>();
		final List<Integer> anak = new ArrayList<>();
		/** Child id -> route distance of its door. */
		final Map<Integer, Double> jemput = new HashMap<>();
		/** Child id -> its place in the line (pick-up order). */
		final Map<Integer, Integer> urut = new HashMap<>();
		final Map<Integer, BlockPos> pintu = new HashMap<>();
		final List<Vec3> rute = new ArrayList<>();
		double[] panjang = new double[0];
		BlockPos sekolah = BlockPos.ZERO;
		int guru = -1;

		Pagi(long hari) {
			this.hari = hari;
		}

		Vec3 titikRute(double s) {
			if (rute.isEmpty()) {
				return Vec3.atBottomCenterOf(sekolah);
			}
			if (rute.size() == 1 || s <= 0.0D) {
				return rute.getFirst();
			}
			double total = panjang[panjang.length - 1];
			if (s >= total) {
				return rute.getLast();
			}
			int seg = 1;
			while (seg < panjang.length - 1 && panjang[seg] < s) {
				seg++;
			}
			double len = Math.max(1.0E-4D, panjang[seg] - panjang[seg - 1]);
			return rute.get(seg - 1).lerp(rute.get(seg), Mth.clamp((s - panjang[seg - 1]) / len, 0.0D, 1.0D));
		}

		double total() {
			return panjang.length == 0 ? 0.0D : panjang[panjang.length - 1];
		}
	}

	private static final Map<Long, Pagi> PAGI = new HashMap<>();
	private static final TendaPasarEntity.Pengelola TENDA = new TendaPasarEntity.Pengelola();
	/** Teachers of their own (towns without one): place -> body. */
	private static final Map<Long, KultivatorNpc> GURU = new HashMap<>();
	/** Seconds in the tai chi rows today: player -> {day, seconds}. */
	private static final Map<UUID, long[]> NAPAS = new HashMap<>();
	/** Game time 07:00 of the current school day per place (the teacher's clock). */
	private static final Map<Long, Long> MULAI_JEMPUT = new HashMap<>();

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		WargaApi.daftarPenimpa(new Penimpa());
		HargaKota.daftarAcara((server, situs, k) -> {
			if (k != Kategori.PANGAN || !KotaConfig.acara().aktif || !KotaConfig.acara().rutinitasPagi) {
				return null;
			}
			double jam = KalenderKota.jamDesimal(server);
			return jam >= 6.0D && jam < 8.0D ? new HargaKota.Pengali(HARGA_PAGI, Component.translatable("kultivator.kota42.acara.harga.pasar_pagi"))
					: null;
		});
		ServerTickEvents.END_SERVER_TICK.register(RutinitasPagi::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PAGI.clear();
			TENDA.hapusSemua();
			for (KultivatorNpc npc : GURU.values()) {
				npc.vanish();
			}
			GURU.clear();
			NAPAS.clear();
			MULAI_JEMPUT.clear();
		});
	}

	// ================================================================== the day's plan

	/** The place's plan for world day {@code hari}, built on first use. */
	static Pagi pagi(TempatHuni huni, long hari) {
		Pagi p = PAGI.get(huni.situsId);
		if (p != null && p.hari == hari) {
			return p;
		}
		p = new Pagi(hari);
		PAGI.put(huni.situsId, p);
		List<Warga> semua = new ArrayList<>(WargaApi.wargaTempat(huni.situsId));
		semua.removeIf(w -> !w.hidup() || w.slot < 0 || huni.orang(w.slot) == null);
		semua.sort(Comparator.comparingInt(w -> w.id));
		// tai chi: the habit holders, the eldest leads
		for (Warga w : semua) {
			if (p.taiChi.size() < TAI_CHI_MAKS && Kebiasaan.punya(w, "tai_chi")) {
				p.taiChi.add(w.id);
			}
		}
		int oldest = -1;
		for (int id : p.taiChi) {
			Warga w = WargaApi.byId(id);
			if (w != null && (oldest < 0 || WargaApi.umur(w) > WargaApi.umur(WargaApi.byId(oldest)))) {
				oldest = id;
			}
		}
		p.pemimpin = oldest;
		p.pusat = KalenderKota.alun(huni);
		p.arah = (float) Math.floorMod(huni.situsId * 90L, 360L);
		// morning market: titik pasar_pagi_<n>, else a row beside the square
		List<Titik> stalls = new ArrayList<>();
		for (String type : huni.ringkasTitik().keySet()) {
			if (type.startsWith(KotaTitik.PASAR_PAGI)) {
				stalls.addAll(huni.titik(type));
			}
		}
		stalls.sort(Comparator.comparingInt(t -> KotaTitik.nomor(t.jenis())));
		for (Titik t : stalls) {
			if (p.tenda.size() < 6) {
				p.tenda.add(t.pos());
			}
		}
		if (p.tenda.isEmpty()) {
			int n = 3 + (int) Math.floorMod(huni.situsId, 3L);
			for (int i = 0; i < n; i++) {
				p.tenda.add(p.pusat.offset(-6 + i * 3, 0, 7));
			}
		}
		for (Warga w : semua) {
			if (p.pedagang.size() >= p.tenda.size()) {
				break;
			}
			NpcRole r = w.peranNpc();
			if (r == NpcRole.PETANI || r.kelompok == NpcRole.Kelompok.PEDAGANG && !r.stationary) {
				p.pedagang.add(w.id);
			}
		}
		// school: children 6-12, door to door
		List<Titik> school = huni.titik("sekolah");
		if (school.isEmpty()) {
			school = huni.titik("dupa");
		}
		p.sekolah = school.isEmpty() ? p.pusat : school.getFirst().pos();
		List<Warga> kids = new ArrayList<>();
		for (Warga w : semua) {
			int umur = WargaApi.umur(w);
			if (umur >= 6 && umur <= 12 && !w.peranNpc().immortal()) {
				kids.add(w);
			}
		}
		Warga guru = null;
		for (Warga w : semua) {
			if ("guru_zhou".equals(w.tokoh)) {
				guru = w;
			}
		}
		if (guru == null) {
			for (Warga w : semua) {
				if (w.peranNpc() == NpcRole.GURU_SEKOLAH) {
					guru = w;
					break;
				}
			}
		}
		if (guru == null) {
			for (Warga w : semua) {
				if (w.peranNpc().kelompok == NpcRole.Kelompok.CENDEKIA && WargaApi.umur(w) >= 30 && (guru == null || w.id < guru.id)) {
					guru = w;
				}
			}
		}
		p.guru = guru == null ? -1 : guru.id;
		BlockPos start = guru != null ? huni.rumahPos(huni.orang(guru.slot)) : p.sekolah;
		List<BlockPos> doors = new ArrayList<>();
		Map<Long, List<Integer>> perPintu = new HashMap<>();
		for (Warga k : kids) {
			Penduduk pd = huni.orang(k.slot);
			if (pd == null) {
				continue;
			}
			BlockPos door = huni.rumahPos(pd);
			p.pintu.put(k.id, door);
			perPintu.computeIfAbsent(door.asLong(), key -> {
				doors.add(door);
				return new ArrayList<>();
			}).add(k.id);
		}
		// nearest-neighbour walk from the teacher's door through every child's door, then to school
		List<BlockPos> order = new ArrayList<>();
		BlockPos at = start;
		List<BlockPos> left = new ArrayList<>(doors);
		while (!left.isEmpty()) {
			BlockPos from = at;
			BlockPos next = left.stream().min(Comparator.comparingDouble(d -> d.distSqr(from))).orElseThrow();
			order.add(next);
			left.remove(next);
			at = next;
		}
		p.rute.add(Vec3.atBottomCenterOf(start));
		for (BlockPos d : order) {
			p.rute.add(Vec3.atBottomCenterOf(d));
		}
		p.rute.add(Vec3.atBottomCenterOf(p.sekolah));
		p.panjang = new double[p.rute.size()];
		for (int i = 1; i < p.rute.size(); i++) {
			p.panjang[i] = p.panjang[i - 1] + p.rute.get(i - 1).distanceTo(p.rute.get(i));
		}
		int rank = 0;
		for (int i = 0; i < order.size(); i++) {
			for (int id : perPintu.getOrDefault(order.get(i).asLong(), List.of())) {
				p.jemput.put(id, p.panjang[i + 1]);
				p.urut.put(id, rank++);
				p.anak.add(id);
			}
		}
		return p;
	}

	/** True on a school day (not the rest day, not a festival). */
	static boolean hariSekolah(long situs, long hari) {
		return Math.floorMod(hari, 7L) != 6L && MusimKota.hariRaya(situs, hari) == MusimKota.HR_TIDAK;
	}

	/** Route distance the teacher has walked since 07:00 of place {@code situs} (0 before). */
	private static double jalanGuru(long situs, long now) {
		Long start = MULAI_JEMPUT.get(situs);
		return start == null ? 0.0D : Math.max(0.0D, (now - start) * LAJU_GURU);
	}

	// ================================================================== the residents

	static final class Penimpa implements PenimpaJadwalKota {
		@Override
		public int urutan() {
			return 120;
		}

		@Override
		public @Nullable Kegiatan timpa(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam, long now) {
			KotaConfig.Acara c = KotaConfig.acara();
			if (!c.aktif || !c.rutinitasPagi || !KalenderKota.kota(huni)) {
				return null;
			}
			Jadwal.Saat s = Jadwal.saat(p, huni.jenis, waktu);
			double h = s.jam() + s.menit() / 60.0D;
			if (h < 5.0D || h >= 10.5D) {
				return null;
			}
			Pagi pg = pagi(huni, s.hari());
			boolean hujan = huni.cuaca().hujan();
			// tai chi
			if (h < 6.5D && !hujan) {
				int idx = pg.taiChi.indexOf(w.id);
				if (idx >= 0) {
					if (w.id == pg.pemimpin) {
						Vec3 at = depanBarisan(pg);
						return new Kegiatan("pagi_pimpin", xz(at), KotaGerakId.PIMPIN, 0, 0, 0, 0, GerakId.JALAN_TUA, false);
					}
					int slot = idx - (pg.pemimpin >= 0 && pg.taiChi.indexOf(pg.pemimpin) < idx ? 1 : 0);
					return new Kegiatan("pagi_tai_chi", xz(slotBarisan(pg, slot)), GerakId.TAI_CHI_PAGI, 0, 0, 0, 0, 0, false);
				}
			}
			// morning market vendors
			if (h >= 6.0D && h < 8.0D) {
				int idx = pg.pedagang.indexOf(w.id);
				if (idx >= 0 && idx < pg.tenda.size()) {
					BlockPos t = pg.tenda.get(idx);
					return new Kegiatan("pagi_pasar", xz(Vec3.atBottomCenterOf(t).add(0.0D, 0.0D, 1.2D)), GerakId.SERU_DAGANG, 0, PropId.KERANJANG, 0,
							PropId.PIKULAN, GerakId.JALAN_PIKUL, false);
				}
			}
			if (!hariSekolah(huni.situsId, s.hari())) {
				return null;
			}
			// to school and the class
			Integer rank = pg.urut.get(w.id);
			if (rank != null) {
				if (h >= 7.0D && h < 7.25D) {
					double walked = jalanGuru(huni.situsId, now);
					double door = pg.jemput.getOrDefault(w.id, 0.0D);
					if (walked + 0.5D < door) {
						return Kegiatan.di("pagi_tunggu_guru", Kegiatan.RUMAH, GerakId.LAMBAI, PropId.KITAB);
					}
					double s0 = Math.max(0.0D, walked - JARAK_BARIS * (rank + 1));
					return new Kegiatan("pagi_baris", xz(pg.titikRute(Math.max(Math.min(door, walked), s0))), GerakId.BERDIRI_JAGA, 0, PropId.KITAB,
							0, 0, GerakId.JALAN_ANAK, false);
				}
				if (h >= 7.25D && h < 10.0D) {
					int klip = (s.menit() / 20) % 3 == 2 ? GerakId.MEMBACA : GerakId.DUDUK_LANTAI;
					return new Kegiatan("pagi_sekolah", xz(kursiKelas(pg, rank)), klip, 0, PropId.KITAB, 0, 0, GerakId.JALAN_ANAK, false);
				}
				if (h >= 10.0D && h < 10.5D) {
					return Kegiatan.di("pagi_bubar", "obrol", GerakId.MAIN_KEJAR, 0).berjalan(GerakId.JALAN_TERGESA);
				}
			}
			// mothers wave the children off at the door
			if (h >= 7.0D && h < 7.25D && !w.pria() && w.anak.length > 0 && w.peranNpc().kelompok != NpcRole.Kelompok.PENJAGA) {
				for (int child : w.anak) {
					if (pg.urut.containsKey(child)) {
						return Kegiatan.di("pagi_lambai", Kegiatan.RUMAH, GerakId.LAMBAI, 0);
					}
				}
			}
			return null;
		}
	}

	/**
	 * The teacher's own round when he lives in the place ({@code KalenderKota.JadwalTokoh} for {@code guru_zhou}, and for
	 * any resident picked as teacher): door to door at 07:00, teaching until 10:00.
	 */
	static @Nullable Kegiatan kegiatanGuru(TempatHuni huni, Warga w, Jadwal.Saat s) {
		KotaConfig.Acara c = KotaConfig.acara();
		MinecraftServer server = KotaHidup.server();
		if (!c.aktif || !c.rutinitasPagi || server == null || !hariSekolah(huni.situsId, s.hari())) {
			return null;
		}
		double h = s.jam() + s.menit() / 60.0D;
		if (h < 6.9D || h >= 10.0D) {
			return null;
		}
		Pagi pg = pagi(huni, s.hari());
		if (h < 7.25D) {
			double walked = jalanGuru(huni.situsId, server.overworld().getGameTime());
			return new Kegiatan("pagi_jemput", xz(pg.titikRute(walked + 1.5D)), GerakId.BICARA_TANGAN, 0, PropId.KITAB, 0, 0, 0, false);
		}
		return new Kegiatan("pagi_mengajar", xz(depanKelas(pg)), GerakId.MENGAJAR, 0, PropId.KITAB, 0, 0, 0, false);
	}

	private static Vec3 depanBarisan(Pagi pg) {
		double r = Math.toRadians(pg.arah);
		return Vec3.atBottomCenterOf(pg.pusat).add(-Math.sin(r) * 3.0D, 0.0D, Math.cos(r) * 3.0D);
	}

	/** Grid slot {@code i}: four columns 1.8 apart, rows 1.8 apart behind the square's centre, facing the leader. */
	private static Vec3 slotBarisan(Pagi pg, int i) {
		double r = Math.toRadians(pg.arah);
		Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		Vec3 sisi = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
		int col = i % 4;
		int row = i / 4;
		return Vec3.atBottomCenterOf(pg.pusat).add(sisi.scale((col - 1.5D) * 1.8D)).add(maju.scale(-row * 1.8D));
	}

	/** The teacher's spot before the class. */
	private static Vec3 depanKelas(Pagi pg) {
		return Vec3.atBottomCenterOf(pg.sekolah);
	}

	/** Child {@code rank}'s seat: rows of five, 1.1 apart, 2 blocks in front of the teacher. */
	private static Vec3 kursiKelas(Pagi pg, int rank) {
		double r = Math.toRadians(pg.arah + 180.0F);
		Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		Vec3 sisi = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
		int col = rank % 5;
		int row = rank / 5;
		return depanKelas(pg).add(maju.scale(2.0D + row * 1.1D)).add(sisi.scale((col - 2) * 1.1D));
	}

	private static String xz(Vec3 at) {
		return Kegiatan.XZ + (int) Math.floor(at.x) + "," + (int) Math.floor(at.z);
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20L != 13L) {
			return;
		}
		ServerLevel level = server.overworld();
		KotaConfig.Acara c = KotaConfig.acara();
		long day = KalenderKota.hari(server);
		double jam = KalenderKota.jamDesimal(server);
		long now = level.getGameTime();
		java.util.Set<Long> live = new java.util.HashSet<>();
		if (c.aktif && c.rutinitasPagi && jam >= 5.0D && jam < 10.5D) {
			for (TempatHuni huni : KalenderKota.aktif(level)) {
				if (!KalenderKota.kota(huni)) {
					continue;
				}
				live.add(huni.situsId);
				Pagi pg = pagi(huni, day);
				if (jam < 6.5D) {
					taiChi(level, huni, pg, now);
				}
				if (jam >= 6.0D && jam < 8.0D) {
					pasar(level, huni, pg, now);
				}
				if (hariSekolah(huni.situsId, day)) {
					sekolah(level, huni, pg, jam, now);
				}
			}
		}
		TENDA.rapikan();
		GURU.entrySet().removeIf(e -> {
			boolean gone = e.getValue().isRemoved() || !live.contains(e.getKey()) || jam >= 10.0D;
			if (gone && !e.getValue().isRemoved()) {
				e.getValue().vanish();
			}
			return gone;
		});
		if (jam >= 10.5D || jam < 5.0D) {
			MULAI_JEMPUT.clear();
		}
	}

	private static void taiChi(ServerLevel level, TempatHuni huni, Pagi pg, long now) {
		if (pg.taiChi.isEmpty() || huni.cuaca().hujan()) {
			return;
		}
		long t0 = now - (long) ((KalenderKota.jamDesimal(level.getServer()) - 5.0D) * 1000.0D);
		KultivatorNpc lead = null;
		if (pg.pemimpin >= 0) {
			Warga w = WargaApi.byId(pg.pemimpin);
			if (w != null && w.slot >= 0) {
				lead = huni.tubuhDari(w.slot);
			}
		}
		for (int id : pg.taiChi) {
			Warga w = WargaApi.byId(id);
			KultivatorNpc body = w == null || w.slot < 0 ? null : huni.tubuhDari(w.slot);
			if (body == null) {
				continue;
			}
			int klip = body == lead ? KotaGerakId.PIMPIN : GerakId.TAI_CHI_PAGI;
			if (body.getNavigation().isDone() && SosokApi.gerakSekarang(body) != klip) {
				SosokApi.gerakSinkron(body, klip, t0);
			}
			if (body != lead && lead != null) {
				HidupApi.tatap(body, lead);
			}
		}
		if (lead != null && now % 400L < 20L) {
			lead.say(Component.translatable("kultivator.kota42.acara.taichi." + (int) Math.floorMod(now / 400L, 4L)), 80);
			if (VoiceEvents.exists("kota42.taichi")) {
				Voice.say(lead, "kota42.taichi", null);
			}
		}
		// a player standing in the rows breathes with them
		double maxR = 2.0D + Math.ceil(pg.taiChi.size() / 4.0D) * 1.8D;
		Vec3 centre = Vec3.atBottomCenterOf(pg.pusat);
		long day = KalenderKota.hari(level.getServer());
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.onGround() || p.position().distanceToSqr(centre) > maxR * maxR || p.getDeltaMovement().horizontalDistanceSqr() > 0.01D) {
				continue;
			}
			long[] n = NAPAS.computeIfAbsent(p.getUUID(), k -> new long[] {day, 0L});
			if (n[0] != day) {
				n[0] = day;
				n[1] = 0L;
			}
			n[1]++;
			if (n[1] == NAPAS_DETIK && KalenderKota.Berkah.sekaliSehari(p, KalenderKota.Berkah.NAPAS_PAGI)) {
				KalenderKota.Berkah.beri(p, KalenderKota.Berkah.NAPAS_PAGI, KalenderKota.Berkah.SEPULUH_MENIT);
				Msg.actionBar(p, Component.translatable("kultivator.kota42.acara.berkah.napas_pagi").withStyle(ChatFormatting.AQUA));
			} else if (n[1] == 5) {
				Msg.actionBar(p, Component.translatable("kultivator.kota42.acara.taichi.ikut").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static void pasar(ServerLevel level, TempatHuni huni, Pagi pg, long now) {
		if (!KalenderKota.adaPemain(level, pg.pusat, KotaConfig.acara().jarakTampil)) {
			return;
		}
		for (int i = 0; i < pg.tenda.size(); i++) {
			BlockPos at = MusimKota.tanah(level, pg.tenda.get(i));
			int varian = i % 2 == 0 ? TendaPasarEntity.VARIAN_JAJANAN : TendaPasarEntity.VARIAN_MAINAN;
			TENDA.pastikan(level, huni.situsId, huni.situsId + ":pagi:" + i, at, 180.0F, i == 0 ? TendaPasarEntity.VARIAN_JAJANAN : varian,
					i % 3 == 0 ? 0x3E7A3A : i % 3 == 1 ? 0xC78A1E : 0x2F5D8A);
		}
		// one vendor calls out now and then
		if (now % 300L < 20L && !pg.pedagang.isEmpty()) {
			Warga w = WargaApi.byId(pg.pedagang.get((int) Math.floorMod(now / 300L, (long) pg.pedagang.size())));
			KultivatorNpc body = w == null || w.slot < 0 ? null : huni.tubuhDari(w.slot);
			if (body != null) {
				KotaPayloads.siarUjaran(level, body.position(), new KotaPayloads.Ujaran(body.getId(),
						Component.translatable("kultivator.kota42.acara.pasar_pagi.seru." + (int) Math.floorMod(now / 300L, 5L)),
						GerakId.SERU_DAGANG, 60, VoiceEvents.exists("kota42.pedagang_seru") ? "kota42.pedagang_seru" : "", -1));
			}
		}
	}

	private static void sekolah(ServerLevel level, TempatHuni huni, Pagi pg, double jam, long now) {
		if (pg.anak.isEmpty()) {
			return;
		}
		if (jam >= 7.0D && jam < 7.25D) {
			MULAI_JEMPUT.computeIfAbsent(huni.situsId, k -> now - (long) ((jam - 7.0D) * 1000.0D));
		}
		// a teacher of his own when the place has none living there
		KultivatorNpc guru = pg.guru >= 0 ? tubuh(huni, pg.guru) : null;
		if (pg.guru < 0 && jam >= 6.9D && jam < 10.0D && KalenderKota.adaPemain(level, pg.sekolah, KotaConfig.acara().jarakTampil)) {
			guru = GURU.get(huni.situsId);
			if (guru == null || guru.isRemoved()) {
				BlockPos start = BlockPos.containing(pg.rute.isEmpty() ? Vec3.atBottomCenterOf(pg.sekolah) : pg.rute.getFirst());
				guru = HidupApi.tubuhStaf(level, huni.situsId, NpcRole.GURU_SEKOLAH, start, 0.0F, (int) (huni.situsId * 7L + 1301L));
				if (guru != null) {
					long first = CeritaData.get(level.getServer()).firstCapital();
					if (first == huni.situsId || first == 0L && KalenderKota.ibuKota(huni)) {
						guru.setPlate(Component.translatable("kultivator.kota.papan_nama",
								Component.translatable("kultivator.kota42.acara.tokoh.guru_zhou"), Component.translatable(NpcRole.GURU_SEKOLAH.titleKey())));
					}
					GURU.put(huni.situsId, guru);
				}
			}
			if (guru != null) {
				Vec3 to = jam < 7.25D ? pg.titikRute(jalanGuru(huni.situsId, now) + 1.5D) : depanKelas(pg);
				if (guru.position().distanceToSqr(to) > 2.25D) {
					guru.getNavigation().moveTo(to.x, to.y, to.z, 0.9D);
				} else if (jam >= 7.25D) {
					HidupApi.klip(guru, GerakId.MENGAJAR, PropId.KITAB, 0);
				}
			}
		}
		if (guru != null && jam >= 7.0D && jam < 7.05D && now % 100L < 20L) {
			guru.say(Component.translatable("kultivator.kota42.acara.sekolah.panggil"), 80);
			if (VoiceEvents.exists("kota42.guru_panggil")) {
				Voice.say(guru, "kota42.guru_panggil", null);
			}
		}
		// recitation of the Three Character Classic while someone listens
		if (jam >= 7.3D && jam < 10.0D && now % 240L < 20L) {
			Integer first = pg.anak.isEmpty() ? null : pg.anak.get((int) Math.floorMod(now / 240L, (long) pg.anak.size()));
			KultivatorNpc child = first == null ? null : tubuh(huni, first);
			if (child != null && KalenderKota.adaPemain(level, child.blockPosition(), 16.0D)) {
				int line = (int) Math.floorMod(now / 240L, 6L);
				KotaPayloads.siarUjaran(level, child.position(), new KotaPayloads.Ujaran(child.getId(),
						Component.translatable("kultivator.kota42.acara.sekolah.hafal." + line), GerakId.MEMBACA, 100,
						VoiceEvents.exists("kota42.sekolah_hafal") ? "kota42.sekolah_hafal" : "", -1));
			}
		}
	}

	private static @Nullable KultivatorNpc tubuh(TempatHuni huni, int wargaId) {
		Warga w = WargaApi.byId(wargaId);
		return w == null || w.slot < 0 ? null : huni.tubuhDari(w.slot);
	}

	// ================================================================== test

	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("pagi").executes(RutinitasPagi::ujiPagi);
	}

	private static int ujiPagi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		TempatInfo info = KalenderKota.tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		TempatHuni huni = Pemanggil.huni(info.situsId());
		if (huni == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.tidak_aktif"));
			return 0;
		}
		Pagi pg = pagi(huni, KalenderKota.hari(ctx.getSource().getServer()));
		Warga lead = pg.pemimpin >= 0 ? WargaApi.byId(pg.pemimpin) : null;
		Warga guru = pg.guru >= 0 ? WargaApi.byId(pg.guru) : null;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.pagi", pg.taiChi.size(),
				lead == null ? Component.literal("-") : WargaApi.nama(lead), pg.tenda.size(), pg.pedagang.size(), pg.anak.size(),
				guru == null ? Component.translatable("kultivator.kota42.acara.uji.guru_sendiri") : WargaApi.nama(guru),
				String.format(java.util.Locale.ROOT, "%.0f", pg.total())), false);
		return 1;
	}

	private RutinitasPagi() {
	}
}
