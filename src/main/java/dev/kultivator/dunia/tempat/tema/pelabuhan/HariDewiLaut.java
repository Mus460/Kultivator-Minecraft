package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.AdeganPayload;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TanduEntity;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hari Dewi Laut (DESIGN 38.T.3.4), the harbour's festival on world days {@code d % 28 == 23}, 6000-12000 (calendar
 * event {@code dewi_laut}; the client draws the red lanterns on every pier post and the firecrackers from the
 * {@code TemaNet.FESTIVAL} packet the calendar sends).
 * <ol>
 * <li><b>Procession</b> (6000): the palanquin ({@code TanduEntity}, variant Dewi Laut) leaves the Kuil Dewi Laut with the
 * small statue and four bearers ({@code pikul_tandu}, in step) and goes along the streets to the Dermaga Agung, drums
 * beating.</li>
 * <li><b>Prayer</b> at the quay: the priestess bows ({@code sujud_dupa}), players near get the short scene
 * {@code tema_pelabuhan_dewi} (7b rules).</li>
 * <li><b>Fishing-boat race</b> (9000, entry 5 NB at the boat hire from 6000): six boats on the {@code jalur_perahu}
 * course of the Tambatan Nelayan, two laps; player boats are rowed by the players, the fishermen's boats follow the
 * course. Winner: 30 NB, a Jimat Ombak and Nama Baik +2; quest events {@code pelabuhan_lomba juara|ikut|kalahkan_alan}
 * (A-Lan's boat is the red one).</li>
 * </ol>
 * The same race engine runs A-Lan's practice race ({@link #latihan}, two boats, any calm day) for {@code tema_tali_putus}.
 */
public final class HariDewiLaut {

	public static final String ACARA = "dewi_laut";
	public static final int DARI = 6000;
	public static final int SAMPAI = 12000;
	public static final int LOMBA_MULAI = 9000;
	public static final int PUTARAN = 2;
	public static final long HADIAH_NB = 30L;
	public static final int PERAHU_NPC = 5;

	/** One fisherman's (or A-Lan's) boat on the course. */
	static final class PerahuNpc {
		final PerahuNelayanEntity body;
		final double laju;
		final boolean alan;
		double s;
		boolean selesai;

		PerahuNpc(PerahuNelayanEntity body, double laju, boolean alan) {
			this.body = body;
			this.laju = laju;
			this.alan = alan;
		}
	}

	/** One player in the race. */
	static final class Peserta {
		final UUID pemain;
		final PerahuNelayanEntity perahu;
		int titik;
		int putaran;
		boolean selesai;

		Peserta(UUID pemain, PerahuNelayanEntity perahu) {
			this.pemain = pemain;
			this.perahu = perahu;
		}
	}

	/** One race (festival or practice). */
	static final class Lomba {
		final long situs;
		final List<Vec3> titik;
		final double panjang;
		final boolean latihan;
		final List<PerahuNpc> npc = new ArrayList<>();
		final Map<UUID, Peserta> pemain = new LinkedHashMap<>();
		long mulai = -1L;
		final long daftarSampai;
		int finish;
		boolean selesai;

		Lomba(long situs, List<Vec3> titik, boolean latihan, long daftarSampai) {
			this.situs = situs;
			this.titik = titik;
			this.latihan = latihan;
			this.daftarSampai = daftarSampai;
			double len = 0.0D;
			for (int i = 0; i < titik.size(); i++) {
				len += titik.get(i).distanceTo(titik.get((i + 1) % titik.size()));
			}
			this.panjang = len;
		}
	}

	/** The procession of a town. */
	static final class Arak {
		final long situs;
		final TanduEntity tandu;
		final List<KultivatorNpc> pemikul = new ArrayList<>();
		boolean sembahyang;

		Arak(long situs, TanduEntity tandu) {
			this.situs = situs;
			this.tandu = tandu;
		}
	}

	private static final Map<Long, Lomba> LOMBA = new HashMap<>();
	private static final Map<Long, Arak> ARAK = new HashMap<>();
	private static final Map<Long, Boolean> BERJALAN = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaKalender.daftar(PelabuhanKota.TEMA, ACARA, DARI, SAMPAI, TemaKalender.setiap(28, 23), new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				mulaiFestival(level, situsId);
			}

			@Override
			public void tick(ServerLevel level, long situsId, int tickKe) {
				tickFestival(level, situsId, tickKe);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				akhirFestival(level, situsId);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(HariDewiLaut::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOMBA.clear();
			ARAK.clear();
			BERJALAN.clear();
		});
	}

	/** True while the festival runs in {@code situs}. */
	public static boolean berjalan(long situs) {
		return BERJALAN.getOrDefault(situs, false);
	}

	/** True while race entry is open in {@code situs} (festival before 9000, or a practice race waiting). */
	public static boolean pendaftaran(long situs) {
		Lomba race = LOMBA.get(situs);
		return race != null && race.mulai < 0L;
	}

	// ================================================================== festival

	private static void mulaiFestival(ServerLevel level, long situs) {
		BERJALAN.put(situs, true);
		PelabuhanKota.Hidup kota = PelabuhanKota.cari(situs);
		CeritaApi.kabar(level, situs, "tema_pelabuhan_dewi_laut");
		if (kota == null) {
			return;
		}
		// the race entry opens at the boat hire
		List<Vec3> course = jalur(level, kota);
		if (course.size() >= 3) {
			long entryEnd = level.getGameTime() + Math.max(200L, LOMBA_MULAI - PelabuhanKota.jam(level));
			LOMBA.put(situs, new Lomba(situs, course, false, entryEnd));
		}
		mulaiArak(level, kota);
		for (ServerPlayer p : level.getPlayers(pp -> kota.jarak(pp.position()) < PelabuhanKota.JANGKAU_KOTA)) {
			Msg.title(p, Component.translatable("kultivator.kota_tema.pelabuhan.dewi.judul").withColor(0xF0C060),
					Component.translatable("kultivator.kota_tema.pelabuhan.dewi.sub").withColor(0xF8E8C8), 10, 60, 20);
		}
	}

	private static void akhirFestival(ServerLevel level, long situs) {
		BERJALAN.remove(situs);
		Arak arak = ARAK.remove(situs);
		if (arak != null) {
			for (KultivatorNpc npc : arak.pemikul) {
				npc.vanish();
			}
			TemaMekanisme.lepas(arak.tandu);
		}
		Lomba race = LOMBA.get(situs);
		if (race != null && !race.latihan) {
			akhiriLomba(level, race);
		}
	}

	private static void tickFestival(ServerLevel level, long situs, int tickKe) {
		Arak arak = ARAK.get(situs);
		if (arak != null && tickKe % 2 == 0) {
			level.playSound(null, arak.tandu.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.AMBIENT, 1.4F, 0.8F);
		}
		if (arak != null && !arak.sembahyang && !arak.tandu.dipikul()) {
			arak.sembahyang = true;
			sembahyang(level, arak);
		}
		Lomba race = LOMBA.get(situs);
		if (race != null && !race.latihan && race.mulai < 0L && PelabuhanKota.jam(level) >= LOMBA_MULAI) {
			mulaiLomba(level, race);
		}
	}

	/** The palanquin's route: temple -> street of the sailors' quarter -> the market -> the sea wall -> the quay. */
	private static void mulaiArak(ServerLevel level, PelabuhanKota.Hidup kota) {
		if (kota.kota == null || ARAK.containsKey(kota.situs)) {
			return;
		}
		TemaKota.Kota k = kota.kota;
		int y = k.dasarY();
		int[][] grid = {{-96, 72}, {-96, 48}, {-96, 0}, {-48, 0}, {0, 0}, {0, -40}, {0, -52}, {0, -60}};
		List<Vec3> path = new ArrayList<>();
		for (int[] g : grid) {
			BlockPos w = k.dunia(g[0], g[1], y);
			path.add(new Vec3(w.getX() + 0.5D, y, w.getZ() + 0.5D));
		}
		TanduEntity tandu = TemaMekanisme.muncul(level, TemaEntities.TANDU, path.getFirst(), 0.0F, k, BlockPos.containing(path.getFirst()));
		if (tandu == null) {
			return;
		}
		tandu.setVarian(TanduEntity.VARIAN_DEWI_LAUT);
		tandu.arak(path, 0.05D, false);
		Arak arak = new Arak(kota.situs, tandu);
		for (int i = 0; i < 4; i++) {
			KultivatorNpc npc = HidupApi.tubuh(level, kota.situs, NpcRole.KULI, tandu.blockPosition(), tandu.getYRot());
			if (npc != null) {
				npc.memory().putBoolean("hold", true);
				arak.pemikul.add(npc);
			}
		}
		ARAK.put(kota.situs, arak);
	}

	/** The bearers walk in step at the four poles of the palanquin. */
	private static void pemikul(Arak arak) {
		if (arak.tandu.isRemoved()) {
			return;
		}
		double r = Math.toRadians(arak.tandu.getYRot());
		Vec3 fwd = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		Vec3 side = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
		for (int i = 0; i < arak.pemikul.size(); i++) {
			KultivatorNpc npc = arak.pemikul.get(i);
			if (npc.isRemoved()) {
				continue;
			}
			double f = i < 2 ? 2.6D : -2.6D;
			double s = i % 2 == 0 ? 0.7D : -0.7D;
			Vec3 at = arak.tandu.position().add(fwd.scale(f)).add(side.scale(s));
			npc.teleportTo(at.x, at.y, at.z);
			npc.setYRot(arak.tandu.getYRot());
			npc.setYHeadRot(arak.tandu.getYRot());
			if (arak.tandu.dipikul() && npc.tickCount % 30 == 0) {
				HidupApi.klip(npc, GerakId.PIKUL_TANDU, 0, 40);
			}
		}
	}

	private static void sembahyang(ServerLevel level, Arak arak) {
		for (ServerPlayer p : level.getPlayers(pp -> pp.distanceToSqr(arak.tandu) < 48.0D * 48.0D)) {
			AdeganPayload.kirim(p, "tema_pelabuhan_dewi", arak.tandu, arak.tandu.position(), 0);
			CeritaApi.peristiwa(p, "pergi", "tema_pelabuhan/dewi_laut");
		}
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, arak.tandu.getBoundingBox().inflate(24.0D),
				n -> n.role() == NpcRole.PENDETA_LAUT || n.role() == NpcRole.NELAYAN || n.role() == NpcRole.PELAUT)) {
			HidupApi.klip(npc, GerakId.SUJUD_DUPA, 0, 90);
			if (npc.role() == NpcRole.PENDETA_LAUT && VoiceEvents.exists("tema.pelabuhan.goddess_day")) {
				Voice.say(npc, "tema.pelabuhan.goddess_day", null);
			}
		}
	}

	// ================================================================== the race

	/** The course: the {@code jalur_perahu} points of lane {@code lomba} in order, at the water surface. */
	static List<Vec3> jalur(ServerLevel level, PelabuhanKota.Hidup kota) {
		List<Vec3> out = new ArrayList<>();
		if (kota.kota == null) {
			return out;
		}
		List<Titik> pts = new ArrayList<>();
		for (Titik t : TemaKota.titik(level, kota.kota, "jalur_perahu")) {
			if ("lomba".equals(t.data().get("jalur"))) {
				pts.add(t);
			}
		}
		pts.sort(Comparator.comparingInt(t -> t.angka("urut", 0)));
		for (Titik t : pts) {
			Double surface = JadwalKapal.permukaanAir(level, t.pos());
			if (surface != null) {
				out.add(new Vec3(t.pos().getX() + 0.5D, surface - 0.12D, t.pos().getZ() + 0.5D));
			}
		}
		return out;
	}

	/** A player with a race boat joins the waiting race. */
	static void daftarPeserta(ServerLevel level, long situs, ServerPlayer player, PerahuNelayanEntity boat) {
		Lomba race = LOMBA.get(situs);
		if (race == null || race.mulai >= 0L) {
			return;
		}
		race.pemain.put(player.getUUID(), new Peserta(player.getUUID(), boat));
		Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.lomba.daftar").withStyle(ChatFormatting.GOLD));
		if (race.latihan) {
			mulaiLomba(level, race);
		}
	}

	/** A-Lan's practice race for one player (any calm day, not during the festival): rent the race boat, then go. */
	static void latihan(ServerLevel level, long situs, ServerPlayer player) {
		if (LOMBA.containsKey(situs) || PelabuhanKota.badai(level)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.lomba.sibuk").withStyle(ChatFormatting.GRAY));
			return;
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.cari(situs);
		List<Vec3> course = kota == null ? List.of() : jalur(level, kota);
		if (course.size() < 3) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.lomba.tak_ada_jalur").withStyle(ChatFormatting.GRAY));
			return;
		}
		LOMBA.put(situs, new Lomba(situs, course, true, level.getGameTime() + 1200L));
		Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.lomba.latihan").withStyle(ChatFormatting.AQUA));
	}

	private static void mulaiLomba(ServerLevel level, Lomba race) {
		race.mulai = level.getGameTime();
		PelabuhanKota.Hidup kota = PelabuhanKota.cari(race.situs);
		int boats = race.latihan ? 1 : PERAHU_NPC;
		Vec3 start = race.titik.getFirst();
		Vec3 next = race.titik.get(1);
		float yaw = yaw(start, next);
		for (int i = 0; i < boats; i++) {
			boolean alan = i == 0;
			Vec3 at = start.add((i - boats / 2.0D) * 2.5D * Math.cos(Math.toRadians(yaw)), 0.0D, (i - boats / 2.0D) * 2.5D * Math.sin(Math.toRadians(yaw)));
			PerahuNelayanEntity boat = TemaMekanisme.muncul(level, TemaEntities.PERAHU_NELAYAN, at, yaw, kota == null ? null : kota.kota, BlockPos.containing(at));
			if (boat == null) {
				continue;
			}
			boat.setVarian(PerahuNelayanEntity.VARIAN_LOMBA);
			boat.setKeadaan(PerahuNelayanEntity.KEADAAN_LOMBA);
			double speed = (alan ? 0.205D : 0.17D + 0.03D * level.getRandom().nextDouble()) * (race.latihan ? 0.95D : 1.0D);
			race.npc.add(new PerahuNpc(boat, speed, alan));
		}
		for (Peserta p : race.pemain.values()) {
			p.perahu.setKeadaan(PerahuNelayanEntity.KEADAAN_LOMBA);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(p.pemain);
			if (player != null) {
				Msg.title(player, Component.translatable("kultivator.kota_tema.pelabuhan.lomba.mulai").withColor(0xF0D070), Component.empty(), 5, 30, 10);
				player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!ARAK.isEmpty() && server.getTickCount() % 2 == 0) {
			for (Arak arak : ARAK.values()) {
				pemikul(arak);
			}
		}
		if (LOMBA.isEmpty()) {
			return;
		}
		for (Iterator<Lomba> it = LOMBA.values().iterator(); it.hasNext(); ) {
			Lomba race = it.next();
			if (race.selesai) {
				it.remove();
				continue;
			}
			if (race.mulai < 0L) {
				if (race.latihan && level.getGameTime() > race.daftarSampai) {
					race.selesai = true;
				}
				continue;
			}
			gerakNpc(race);
			periksaPemain(level, race);
			if (level.getGameTime() - race.mulai > 3600L || (race.pemain.isEmpty() && race.npc.stream().allMatch(b -> b.selesai))
					|| (!race.pemain.isEmpty() && race.pemain.values().stream().allMatch(p -> p.selesai))) {
				akhiriLomba(level, race);
			}
		}
	}

	private static void gerakNpc(Lomba race) {
		for (PerahuNpc b : race.npc) {
			if (b.selesai || b.body.isRemoved()) {
				continue;
			}
			b.s += b.laju * (0.92D + 0.16D * Math.sin(b.s * 0.07D + b.laju * 40.0D));
			if (b.s >= race.panjang * PUTARAN) {
				b.selesai = true;
				race.finish++;
				if (b.alan) {
					b.body.setKeadaan(PerahuNelayanEntity.KEADAAN_DIAM);
				}
				continue;
			}
			double d = b.s % race.panjang;
			for (int i = 0; i < race.titik.size(); i++) {
				Vec3 a = race.titik.get(i);
				Vec3 c = race.titik.get((i + 1) % race.titik.size());
				double len = a.distanceTo(c);
				if (d <= len) {
					Vec3 at = a.add(c.subtract(a).scale(len <= 0.0D ? 0.0D : d / len));
					b.body.pindah(at, yaw(a, c));
					b.body.setNilai(1.0F);
					break;
				}
				d -= len;
			}
		}
	}

	/** Player checkpoints (within 6 blocks of each point in order, two laps). */
	private static void periksaPemain(ServerLevel level, Lomba race) {
		for (Peserta p : race.pemain.values()) {
			if (p.selesai || p.perahu.isRemoved()) {
				continue;
			}
			Vec3 target = race.titik.get(p.titik % race.titik.size());
			if (p.perahu.position().distanceToSqr(target.x, p.perahu.getY(), target.z) < 36.0D) {
				p.titik++;
				if (p.titik % race.titik.size() == 0) {
					p.putaran++;
				}
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(p.pemain);
				if (player != null) {
					player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.2F + 0.1F * p.titik);
				}
				if (p.putaran >= PUTARAN) {
					p.selesai = true;
					race.finish++;
					selesaiPemain(level, race, p, race.finish);
				}
			}
		}
	}

	private static void selesaiPemain(ServerLevel level, Lomba race, Peserta p, int place) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(p.pemain);
		if (player == null) {
			return;
		}
		boolean beatAlan = false;
		for (PerahuNpc b : race.npc) {
			if (b.alan && !b.selesai) {
				beatAlan = true;
			}
		}
		CeritaApi.peristiwa(player, "pelabuhan_lomba", "ikut");
		if (beatAlan) {
			CeritaApi.peristiwa(player, "pelabuhan_lomba", "kalahkan_alan");
		}
		if (place == 1) {
			CeritaApi.peristiwa(player, "pelabuhan_lomba", "juara");
			if (!race.latihan) {
				EkonomiApi.beri(player, HADIAH_NB, "pelabuhan.lomba");
				PelabuhanCerita.hadiah(player, "jimat");
				PelabuhanKota.Hidup kota = PelabuhanKota.cari(race.situs);
				if (kota != null) {
					KotaData.get(level.getServer()).addReputation(kota.town, player.getUUID(), 2);
				}
				CeritaApi.kabar(level, race.situs, "tema_pelabuhan_lomba_juara", player.getName().getString());
				dev.kultivator.util.Advancements.grant(player, "tempat/tema_pelabuhan_lomba");
			}
			Msg.title(player, Component.translatable("kultivator.kota_tema.pelabuhan.lomba.juara").withColor(0xF8D048), Component.empty(), 5, 50, 15);
		} else {
			Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.lomba.tiba", place).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void akhiriLomba(ServerLevel level, Lomba race) {
		race.selesai = true;
		for (PerahuNpc b : race.npc) {
			TemaMekanisme.lepas(b.body);
		}
		for (Peserta p : race.pemain.values()) {
			PerahuSewa.akhiri(level.getServer(), p.pemain, "lomba_selesai");
		}
	}

	private static float yaw(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	/** Tests: forces the race of a town to start now (a festival must be running or a practice waiting). */
	static boolean paksaLomba(ServerLevel level, long situs) {
		Lomba race = LOMBA.get(situs);
		if (race == null || race.mulai >= 0L) {
			return false;
		}
		mulaiLomba(level, race);
		return true;
	}

	static @Nullable DuniaAnchorEntity kuil(PelabuhanKota.Hidup kota) {
		List<DuniaAnchorEntity> list = kota.jangkar(PelabuhanAnchors.KUIL);
		return list.isEmpty() ? null : list.getFirst();
	}

	private HariDewiLaut() {
	}
}
