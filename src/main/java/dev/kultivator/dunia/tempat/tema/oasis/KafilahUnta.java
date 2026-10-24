package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Camel caravans of the Kota Oasis (DESIGN 38.T.4.4): the Kafilah Agung, the caravan board {@code tema_kafilah}, camel
 * hire and the caravan of the chain's stage 2.
 * <ul>
 * <li><b>Kafilah Agung</b> (calendar event {@code kafilah_agung}, 08000-14000): on days {@code d % 5 == 1} the great
 * caravan comes up the caravan road, through the Gerbang Giok and into the Karavanserai Agung (bells, drum, the caravan
 * master's shout); it unloads there, which makes the trade goods it brought 10 % cheaper in both bazaars for two days
 * ({@link #diskonKafilah}). On days {@code d % 5 == 3} it leaves the other way and goes down the road to the nearest
 * other town the server knows (else 600 blocks into the Lautan Pasir Barat).</li>
 * <li>The caravan is data that moves along its route (1.2 blocks/s in town, 1.0 on the road); bodies (4-8 camels on a
 * lead rope, the caravan master, 1-2 camel leaders, 2 desert guards) exist only while a player is within 64 blocks of
 * its head, and are re-made when one comes back. A sandstorm stops it: the camels kneel, everyone waits.</li>
 * <li><b>Passengers</b> (8 NB, op {@code ikut}) ride a camel of the string; the AI walks it. "Tidur di pelana" (effect
 * {@code oasis_tidur_pelana} of the caravan master's dialog): the screen goes dark for 3 s and the caravan is 300 blocks
 * further; refused when a hostile is within 64 blocks. <b>Escorts</b> (op {@code kawal}) need the guild mission
 * {@code kawal_unta}; it is reported done when the caravan arrives with them near.</li>
 * <li><b>Hire</b> (op {@code sewa:HARI}, 12 NB a world day, at most 3): a riding camel ({@link UntaKafilahEntity}) at the
 * pen; it walks home when the hire ends or it was left alone more than 64 blocks for two minutes.</li>
 * <li><b>Chain stage 2</b> (effect {@code oasis_kafilah_quest}): a caravan leaves at once with the player as escort and
 * Si Kecil Ajun along; half way down the road a sandstorm rises and six desert beasts go for the camels. All camels safe
 * at 600 blocks: event {@code oasis_kafilah selamat} and a Lempeng Peta; a camel lost: {@code oasis_kafilah hilang}, and
 * Ajun is lost in the storm 30-50 blocks off the road (talking to him ends the search).</li>
 * </ul>
 */
public final class KafilahUnta {

	public static final String ACARA = "kafilah_agung";
	public static final int DARI = 8000;
	public static final int SAMPAI = 14000;
	public static final long HARGA_PENUMPANG = 8L;
	public static final long HARGA_SEWA = 12L;
	public static final int SEWA_HARI_MAKS = 3;
	public static final int LONCAT_PELANA = 300;
	public static final int JALAN_GURUN = 600;
	public static final double JARAK_TUBUH = 64.0D;
	private static final double LAJU_KOTA = 1.2D;
	private static final double LAJU_JALAN = 1.0D;
	private static final double SELA = 3.2D;
	private static final int DISKON_HARI = 2;
	private static final int BONGKAR_TICK = 200;
	private static final int SERBUAN = 6;

	/** Route grid offsets (blocks before rotation from the town centre; tools/gen_tempat_tema_oasis.py builds them). */
	private static final int[][] RUTE_TIBA = {{0, 262}, {0, 210}, {0, 172}, {0, 150}, {0, 124}, {0, 99}, {26, 99}, {48, 99}, {60, 96},
			{78, 96}};

	/** One caravan on its way. */
	static final class Rombongan {
		final long situs;
		final boolean berangkat;
		final boolean quest;
		final List<Vec3> rute = new ArrayList<>();
		final List<Double> panjang = new ArrayList<>();
		final int nUnta;
		double jarak;
		/** Index of the first route point outside the walls (the road part begins there). */
		int titikJalan;
		final List<UntaKafilahEntity> unta = new ArrayList<>();
		final List<KultivatorNpc> orang = new ArrayList<>();
		final Set<UUID> penumpang = new HashSet<>();
		final Set<UUID> pengawal = new HashSet<>();
		final List<Mob> binatang = new ArrayList<>();
		final Map<UUID, Integer> kursi = new HashMap<>();
		int bongkar = -1;
		boolean serbuan;
		boolean hilang;
		int sepi;
		long mulai;
		@Nullable UUID pemilikQuest;

		Rombongan(long situs, boolean berangkat, boolean quest, int nUnta) {
			this.situs = situs;
			this.berangkat = berangkat;
			this.quest = quest;
			this.nUnta = nUnta;
		}

		double total() {
			return panjang.isEmpty() ? 0.0D : panjang.getLast();
		}

		/** The route point {@code d} blocks from the start (clamped). */
		Vec3 di(double d) {
			if (rute.isEmpty()) {
				return Vec3.ZERO;
			}
			double t = Math.max(0.0D, Math.min(total(), d));
			for (int i = 1; i < rute.size(); i++) {
				if (panjang.get(i) >= t) {
					double a = panjang.get(i - 1);
					double seg = Math.max(1.0E-4D, panjang.get(i) - a);
					return rute.get(i - 1).lerp(rute.get(i), (t - a) / seg);
				}
			}
			return rute.getLast();
		}

		double jarakJalan() {
			return titikJalan < panjang.size() ? panjang.get(titikJalan) : total();
		}

		void tambah(Vec3 p) {
			if (!rute.isEmpty()) {
				panjang.add(panjang.getLast() + rute.getLast().distanceTo(p));
			} else {
				panjang.add(0.0D);
			}
			rute.add(p);
		}
	}

	private static final Map<Long, Rombongan> JALAN = new HashMap<>();
	/** Pen camels per anchor are kept by OasisAnchors; hired camels by renter. */
	private static final Map<UUID, UntaKafilahEntity> SEWAAN = new HashMap<>();
	/** Camel-hire handlers per pen anchor. */
	private static final Map<UUID, KultivatorNpc> PENUNTUN = new HashMap<>();

	static void init() {
		TemaKalender.daftar(OasisKota.TEMA, ACARA, DARI, SAMPAI,
				(level, hari, situs, pusat) -> TemaConfig.oasis().kafilahUnta && (Math.floorMod(hari, 5) == 1 || Math.floorMod(hari, 5) == 3),
				new TemaKalender.Pelaksana() {
					@Override
					public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
						long hari = OasisKota.hari(level);
						KafilahUnta.mulai(level, situsId, Math.floorMod(hari, 5) == 3, false, null);
					}
				});
		TemaLayar.daftar(TemaLayar.Kafilah.LAYAR, KafilahUnta::bangun, KafilahUnta::aksi);
	}

	static void reset() {
		JALAN.clear();
		SEWAAN.clear();
		PENUNTUN.clear();
	}

	// ================================================================== Kafilah Agung

	/**
	 * Starts a caravan in town {@code situs}: arrival ({@code berangkat} false) or departure; {@code quest} for the chain's
	 * stage 2 with {@code pemilik} as escort. False when one is already on its way or the town is unknown.
	 */
	public static boolean mulai(ServerLevel level, long situs, boolean berangkat, boolean quest, @Nullable ServerPlayer pemilik) {
		MinecraftServer server = level.getServer();
		OasisKota.Hidup hidup = OasisKota.cari(server, situs);
		TemaKota.Kota kota = TemaKota.dari(server, hidup != null ? hidup.situs : situs);
		if (kota == null || JALAN.containsKey(kota.situsId())) {
			return false;
		}
		long key = kota.situsId();
		int n = 4 + (int) Math.floorMod(OasisKota.hash(key, OasisKota.hari(level), 380432L), 5L);
		Rombongan r = new Rombongan(key, berangkat || quest, quest, quest ? 5 : n);
		int y = kota.dasarY() + 1;
		List<Vec3> kotaRute = new ArrayList<>();
		for (int[] p : RUTE_TIBA) {
			BlockPos w = kota.dunia(p[0], p[1], y);
			kotaRute.add(Vec3.atBottomCenterOf(w));
		}
		if (!r.berangkat) {
			for (Vec3 p : kotaRute) {
				r.tambah(p);
			}
			r.titikJalan = 0;
		} else {
			for (int i = kotaRute.size() - 1; i >= 0; i--) {
				r.tambah(kotaRute.get(i));
			}
			r.titikJalan = r.rute.size() - 1;
			Vec3 gerbang = r.rute.getLast();
			Vec3 tujuan = tujuan(level, kota, gerbang);
			// the road: a waypoint every 24 blocks, on the ground where the chunk is loaded
			double d = gerbang.distanceTo(tujuan);
			int steps = Math.max(1, (int) Math.ceil(d / 24.0D));
			for (int i = 1; i <= steps; i++) {
				Vec3 p = gerbang.lerp(tujuan, i / (double) steps);
				r.tambah(new Vec3(p.x, tanah(level, (int) Math.floor(p.x), (int) Math.floor(p.z), (int) p.y), p.z));
			}
		}
		r.mulai = level.getGameTime();
		r.pemilikQuest = pemilik == null ? null : pemilik.getUUID();
		if (pemilik != null) {
			r.pengawal.add(pemilik.getUUID());
		}
		JALAN.put(key, r);
		if (!r.berangkat) {
			Component nama = OasisNames.nama(server, key);
			for (ServerPlayer p : BadaiPasir.pemainDekat(level, kota.pusat(), 320)) {
				Msg.chat(p, Component.translatable("kultivator.kota_tema.oasis.kafilah.datang", nama).withStyle(ChatFormatting.GOLD));
			}
		} else {
			// the passengers and escorts who signed up for today's departure join the string
			long hari = OasisKota.hari(level);
			for (ServerPlayer p : level.players()) {
				CompoundTag me = OasisKota.pemain(server, p.getUUID());
				if (me.getLongOr("ikut_" + key, Long.MIN_VALUE) == hari) {
					r.penumpang.add(p.getUUID());
				}
				if (me.getLongOr("kawal_" + key, Long.MIN_VALUE) == hari) {
					r.pengawal.add(p.getUUID());
				}
			}
		}
		return true;
	}

	/** Where a departing caravan goes: the nearest other town the server knows within 3000 blocks, else 600 blocks out. */
	private static Vec3 tujuan(ServerLevel level, TemaKota.Kota kota, Vec3 gerbang) {
		BlockPos at = BlockPos.containing(gerbang);
		for (DuniaSites.Site site : DuniaSites.near(level, at, 3000, "kota")) {
			if (site.id() == kota.situsId() || site.pos().distSqr(kota.pusat()) < 400.0D * 400.0D) {
				continue;
			}
			BlockPos arrive = TempatApi.arrival(site);
			BlockPos p = arrive != null ? arrive : site.pos();
			return Vec3.atBottomCenterOf(p);
		}
		BlockPos far = kota.dunia(0, 262 + JALAN_GURUN, kota.dasarY() + 1);
		return Vec3.atBottomCenterOf(far);
	}

	private static int tanah(ServerLevel level, int x, int z, int fallback) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return fallback;
		}
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** Every 20 ticks from {@link Oasis}. */
	static void tick(MinecraftServer server) {
		if (JALAN.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<Long, Rombongan>> it = JALAN.entrySet().iterator();
		while (it.hasNext()) {
			Rombongan r = it.next().getValue();
			ServerLevel level = server.overworld();
			boolean selesai;
			try {
				selesai = langkah(level, r);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Oasis caravan failed", exception);
				selesai = true;
			}
			if (selesai) {
				bubar(r);
				it.remove();
			}
		}
	}

	/** One step; true when the caravan is done. */
	private static boolean langkah(ServerLevel level, Rombongan r) {
		MinecraftServer server = level.getServer();
		r.unta.removeIf(Entity::isRemoved);
		r.orang.removeIf(Entity::isRemoved);
		r.binatang.removeIf(m -> m.isRemoved() || !m.isAlive());
		Vec3 kepala = r.di(r.jarak);
		boolean badai = BadaiPasir.aktifDi(level, BlockPos.containing(kepala));
		boolean dekat = TemaMekanisme.pemainDekat(level, kepala, JARAK_TUBUH) || adaTerdaftarDekat(level, r, kepala);
		// unloading at the karavanserai
		if (r.bongkar >= 0) {
			r.bongkar -= 20;
			if (r.bongkar <= 0) {
				tiba(level, r);
				return true;
			}
			return false;
		}
		if (level.getGameTime() - r.mulai > 24000L * 2) {
			return true;
		}
		if (dekat) {
			r.sepi = 0;
			if (r.unta.isEmpty() && r.orang.isEmpty()) {
				tubuh(level, r);
			}
		} else if (!r.unta.isEmpty() || !r.orang.isEmpty()) {
			r.sepi += 20;
			if (r.sepi >= 200) {
				lepasTubuh(r);
			}
		}
		for (UntaKafilahEntity u : r.unta) {
			u.badai(badai);
		}
		if (r.quest) {
			cekQuest(level, r);
			if (r.hilang) {
				return true;
			}
		}
		if (!badai && r.binatang.isEmpty()) {
			double laju = r.berangkat && r.jarak >= r.jarakJalan() ? LAJU_JALAN : LAJU_KOTA;
			r.jarak = Math.min(r.total(), r.jarak + laju);
		}
		gerakTubuh(level, r);
		if (r.jarak >= r.total() - 0.5D) {
			if (!r.berangkat) {
				r.bongkar = BONGKAR_TICK;
				for (UntaKafilahEntity u : r.unta) {
					u.lutut(true);
				}
				KultivatorNpc bashi = r.orang.isEmpty() ? null : r.orang.getFirst();
				if (bashi != null) {
					Voice.say(bashi, "tema.oasis.caravan", null);
					bashi.say(Component.translatable("kultivator.kota_tema.oasis.kafilah.bongkar"), 80);
				}
				return false;
			}
			tiba(level, r);
			return true;
		}
		// bells and drum while bodies walk in town
		if (!r.unta.isEmpty() && level.getRandom().nextInt(3) == 0) {
			UntaKafilahEntity u = r.unta.get(level.getRandom().nextInt(r.unta.size()));
			level.playSound(null, u.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 0.6F, 0.7F + level.getRandom().nextFloat() * 0.6F);
		}
		if (!r.orang.isEmpty() && !r.berangkat && level.getGameTime() % 40 == 0) {
			level.playSound(null, r.orang.getFirst().blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.NEUTRAL, 0.9F, 0.8F);
		}
		return false;
	}

	private static boolean adaTerdaftarDekat(ServerLevel level, Rombongan r, Vec3 at) {
		for (UUID id : r.penumpang) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null && p.level() == level && p.position().distanceToSqr(at) <= 96.0D * 96.0D) {
				return true;
			}
		}
		return false;
	}

	/** Makes the bodies at their route places. */
	private static void tubuh(ServerLevel level, Rombongan r) {
		MinecraftServer server = level.getServer();
		TemaKota.Kota kota = TemaKota.dari(server, r.situs);
		long day = OasisKota.hari(level);
		List<UUID> riders = new ArrayList<>(r.penumpang);
		for (int i = 0; i < r.nUnta; i++) {
			Vec3 at = r.di(r.jarak - SELA * (i + 1));
			UntaKafilahEntity u = TemaMekanisme.muncul(level, TemaEntities.UNTA_KAFILAH, at, yaw(r, r.jarak - SELA * (i + 1)), kota, null);
			if (u == null) {
				break;
			}
			u.ikat(r.situs);
			u.setRombongan(true);
			boolean kursi = i >= r.nUnta - riders.size();
			u.setVarian(kursi ? UntaKafilahEntity.VARIAN_POLOS : UntaKafilahEntity.VARIAN_BEBAN);
			u.setNilai(0.2F + 0.1F * (i % 3));
			r.unta.add(u);
			if (kursi) {
				UUID id = riders.get(i - (r.nUnta - riders.size()));
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null && p.level() == level && p.position().distanceToSqr(at) <= 96.0D * 96.0D && !p.isPassenger()) {
					p.teleportTo(at.x, at.y, at.z);
					p.startRiding(u);
				}
			}
		}
		Vec3 depan = r.di(r.jarak + 2.0D);
		KultivatorNpc bashi = OasisTokoh.panggil(level, r.situs, OasisTokoh.BULAT_KHAN, BlockPos.containing(depan), yaw(r, r.jarak));
		if (bashi == null) {
			bashi = HidupApi.tubuh(level, r.situs, NpcRole.KAFILAH_BASHI, BlockPos.containing(depan), yaw(r, r.jarak));
		}
		tambahOrang(r, bashi, GerakId.PIMPIN_UNTA, PropId.CAMBUK_KAFILAH);
		for (int i = 0; i < 2; i++) {
			Vec3 at = r.di(r.jarak - SELA * (i * 3 + 1));
			tambahOrang(r, HidupApi.tubuh(level, r.situs, NpcRole.PENUNTUN_UNTA, BlockPos.containing(at), yaw(r, r.jarak)), GerakId.PIMPIN_UNTA,
					PropId.TALI_KEKANG);
		}
		for (int i = 0; i < 2; i++) {
			Vec3 at = r.di(r.jarak - SELA * (r.nUnta + 1 + i));
			tambahOrang(r, HidupApi.tubuh(level, r.situs, NpcRole.PENGAWAL_GURUN, BlockPos.containing(at), yaw(r, r.jarak)), GerakId.BARIS_JAGA,
					PropId.TOMBAK);
		}
		if (r.quest) {
			Vec3 at = r.di(r.jarak - SELA * 2);
			KultivatorNpc ajun = OasisTokoh.panggil(level, r.situs, OasisTokoh.AJUN, BlockPos.containing(at), yaw(r, r.jarak));
			tambahOrang(r, ajun, GerakId.JALAN_ANAK, 0);
		}
		if (!r.berangkat && !r.orang.isEmpty() && level.getRandom().nextBoolean()) {
			Voice.say(r.orang.getFirst(), "tema.oasis.caravan", null);
		}
		dev.kultivator.Kultivator.LOGGER.debug("Oasis caravan bodies at {} (day {})", BlockPos.containing(depan), day);
	}

	private static void tambahOrang(Rombongan r, @Nullable KultivatorNpc npc, int klip, int prop) {
		if (npc == null) {
			return;
		}
		npc.memory().putBoolean("hold", true);
		npc.memory().putBoolean("kafilah", true);
		npc.setLonely(96.0D, 1200);
		SosokApi.gerak(npc, klip, 0, 0, prop, 0, 0);
		r.orang.add(npc);
	}

	private static float yaw(Rombongan r, double d) {
		Vec3 a = r.di(d - 1.0D);
		Vec3 b = r.di(d + 1.0D);
		return (float) (Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z)));
	}

	/** Moves every body toward its place in the string (navigation; a body far behind is brought up). */
	private static void gerakTubuh(ServerLevel level, Rombongan r) {
		for (int i = 0; i < r.unta.size(); i++) {
			UntaKafilahEntity u = r.unta.get(i);
			if (u.berlutut()) {
				continue;
			}
			Vec3 to = r.di(r.jarak - SELA * (i + 1));
			pindah(u, to, 1.1D);
		}
		for (int i = 0; i < r.orang.size(); i++) {
			KultivatorNpc npc = r.orang.get(i);
			double d = i == 0 ? r.jarak + 2.0D : i <= 2 ? r.jarak - SELA * ((i - 1) * 3 + 1) : r.jarak - SELA * (r.nUnta + i - 2);
			Vec3 to = r.di(d);
			npc.setHome(BlockPos.containing(to));
			pindah(npc, to, 0.7D);
		}
	}

	private static void pindah(Mob mob, Vec3 to, double speed) {
		double d2 = mob.position().distanceToSqr(to);
		if (d2 > 24.0D * 24.0D && !mob.isVehicle()) {
			mob.teleportTo(to.x, to.y, to.z);
			mob.getNavigation().stop();
		} else if (d2 > 2.0D) {
			mob.getNavigation().moveTo(to.x, to.y, to.z, speed);
		}
	}

	private static void lepasTubuh(Rombongan r) {
		for (UntaKafilahEntity u : r.unta) {
			u.ejectPassengers();
			TemaMekanisme.lepas(u);
		}
		r.unta.clear();
		for (KultivatorNpc npc : r.orang) {
			if (!npc.isRemoved()) {
				npc.vanish();
			}
		}
		r.orang.clear();
	}

	private static void bubar(Rombongan r) {
		lepasTubuh(r);
		for (Mob m : r.binatang) {
			if (!m.isRemoved()) {
				m.discard();
			}
		}
		r.binatang.clear();
	}

	/** The caravan reached its end: unloaded (arrival) or at its destination (departure). */
	private static void tiba(ServerLevel level, Rombongan r) {
		MinecraftServer server = level.getServer();
		if (!r.berangkat) {
			CompoundTag data = OasisKota.kota(server, r.situs);
			data.putLong("kafilah_tiba", OasisKota.hari(level));
			OasisKota.ubah(server);
			CeritaApi.kabar(level, r.situs, "tema_oasis_kafilah_tiba");
			for (ServerPlayer p : BadaiPasir.pemainDekat(level, BlockPos.containing(r.di(r.total())), 160)) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.oasis.kafilah.diskon").withStyle(ChatFormatting.GOLD));
				QuestTracker.peristiwa(p, "oasis_kafilah", "tiba");
			}
			return;
		}
		Vec3 end = r.di(r.total());
		for (UUID id : r.pengawal) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || p.level() != level || p.position().distanceToSqr(end) > 96.0D * 96.0D) {
				continue;
			}
			CompoundTag me = OasisKota.pemain(server, id);
			me.putInt("kafilah_dikawal", me.getIntOr("kafilah_dikawal", 0) + 1);
			OasisKota.ubah(server);
			EkonomiApi.misiSelesai(p, "kawal_unta");
			if (r.quest) {
				// chain stage 2 branch flag (quest tema_oasis_mata_air, cabang of stage "kafilah")
				CeritaApi.setBendera(p, "oasis.kafilah_hilang", false);
			}
			QuestTracker.peristiwa(p, "oasis_kafilah", r.quest ? "selamat" : "tiba");
			if (r.quest) {
				ItemStack peta = new ItemStack(TemaItems.LEMPENG_PETA);
				OasisKota.beri(p, peta);
				Msg.chat(p, Component.translatable("kultivator.kota_tema.oasis.kafilah.quest_selamat").withStyle(ChatFormatting.GOLD));
			} else {
				Msg.chat(p, Component.translatable("kultivator.kota_tema.oasis.kafilah.kawal_selesai").withStyle(ChatFormatting.GOLD));
			}
		}
		for (UUID id : r.penumpang) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && p.isPassenger() && p.getVehicle() instanceof UntaKafilahEntity) {
				p.stopRiding();
				Msg.chat(p, Component.translatable("kultivator.kota_tema.oasis.kafilah.penumpang_tiba").withStyle(ChatFormatting.GOLD));
				QuestTracker.peristiwa(p, "oasis_kafilah", "penumpang");
			}
		}
	}

	/** {@code true} for two days after the great caravan unloaded in town {@code situs}. */
	public static boolean diskonKafilah(MinecraftServer server, long situs, long hari) {
		long tiba = OasisKota.kota(server, situs).getLongOr("kafilah_tiba", Long.MIN_VALUE);
		return tiba != Long.MIN_VALUE && hari - tiba >= 0 && hari - tiba < DISKON_HARI;
	}

	/** True while a caravan of town {@code situs} is on its way. */
	public static boolean berjalan(long situs) {
		return JALAN.containsKey(situs);
	}

	// ================================================================== chain stage 2

	private static void cekQuest(ServerLevel level, Rombongan r) {
		MinecraftServer server = level.getServer();
		ServerPlayer owner = r.pemilikQuest == null ? null : server.getPlayerList().getPlayer(r.pemilikQuest);
		if (owner == null) {
			r.hilang = true;
			return;
		}
		Vec3 kepala = r.di(r.jarak);
		if (owner.position().distanceToSqr(kepala) > 160.0D * 160.0D) {
			r.sepi += 20;
			if (r.sepi > 1200) {
				gagalQuest(level, r, owner);
			}
			return;
		}
		double tengah = r.jarakJalan() + (r.total() - r.jarakJalan()) * 0.5D;
		if (!r.serbuan && r.jarak >= tengah && !r.unta.isEmpty()) {
			String sibuk = Sibuk.whyExcept(owner, "tema_oasis_badai");
			if (sibuk == null) {
				r.serbuan = true;
				BadaiPasir.mulai(level, r.situs, true);
				serbu(level, r, owner);
			}
		}
	}

	private static void serbu(ServerLevel level, Rombongan r, ServerPlayer owner) {
		int band = Math.max(0, Cultivators.get(owner).realm().ordinal());
		for (int i = 0; i < SERBUAN; i++) {
			EntityType<? extends Mob> type = i % 2 == 0 ? EntityTypes.HUSK : EntityTypes.SPIDER;
			Mob mob = type.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			double a = (i / (double) SERBUAN) * Math.PI * 2.0D;
			Vec3 base = r.di(r.jarak - SELA * 2);
			double x = base.x + Math.cos(a) * 14.0D;
			double z = base.z + Math.sin(a) * 14.0D;
			int y = tanah(level, (int) Math.floor(x), (int) Math.floor(z), (int) base.y);
			mob.snapTo(x, y, z, (float) Math.toDegrees(a), 0.0F);
			TechniquePower.markRealmFoe(mob);
			mob.addTag("kultivator.oasis_serbuan");
			if (level.addFreshEntity(mob)) {
				if (!r.unta.isEmpty()) {
					mob.setTarget(r.unta.get(i % r.unta.size()));
				}
				r.binatang.add(mob);
			}
		}
		Msg.chat(owner, Component.translatable("kultivator.kota_tema.oasis.kafilah.serbuan", band).withStyle(ChatFormatting.RED));
	}

	/** A camel of a caravan died. */
	static void untaMati(ServerLevel level, UntaKafilahEntity unta) {
		for (Rombongan r : JALAN.values()) {
			if (r.unta.contains(unta) && r.quest && !r.hilang) {
				ServerPlayer owner = r.pemilikQuest == null ? null : level.getServer().getPlayerList().getPlayer(r.pemilikQuest);
				if (owner != null) {
					gagalQuest(level, r, owner);
				}
			}
		}
	}

	private static void gagalQuest(ServerLevel level, Rombongan r, ServerPlayer owner) {
		r.hilang = true;
		CeritaApi.setBendera(owner, "oasis.kafilah_hilang", true);
		QuestTracker.peristiwa(owner, "oasis_kafilah", "hilang");
		// Ajun is lost in the storm, 30-50 blocks off the road
		Vec3 at = r.di(r.jarak);
		double a = level.getRandom().nextDouble() * Math.PI * 2.0D;
		double dist = 30.0D + level.getRandom().nextDouble() * 20.0D;
		int x = (int) Math.floor(at.x + Math.cos(a) * dist);
		int z = (int) Math.floor(at.z + Math.sin(a) * dist);
		int y = tanah(level, x, z, (int) at.y);
		KultivatorNpc ajun = OasisTokoh.panggil(level, r.situs, OasisTokoh.AJUN, new BlockPos(x, y, z), 0.0F);
		if (ajun != null) {
			ajun.memory().putBoolean("tersesat", true);
			ajun.memory().putBoolean("hold", true);
			ajun.setLonely(160.0D, 6000);
			SosokApi.gerak(ajun, GerakId.TAKUT_SEMBUNYI, 0, 0);
		}
		Msg.chat(owner, Component.translatable("kultivator.kota_tema.oasis.kafilah.ajun_hilang").withStyle(ChatFormatting.RED));
	}

	/** Effect {@code oasis_kafilah_quest}: the stage-2 caravan leaves now with the player. */
	public static boolean mulaiQuest(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		OasisKota.Hidup kota = OasisKota.di(player);
		if (kota == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.kafilah.bukan_kota").withStyle(ChatFormatting.RED));
			return false;
		}
		if (JALAN.containsKey(kota.situs)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.kafilah.sedang_jalan").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		return mulai(level, kota.situs, true, true, player);
	}

	/** Effect {@code oasis_tidur_pelana}: sleep in the saddle; the caravan is 300 blocks further. */
	public static boolean tidurPelana(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Rombongan r = null;
		for (Rombongan each : JALAN.values()) {
			if (each.berangkat && each.penumpang.contains(player.getUUID())) {
				r = each;
			}
		}
		if (r == null || !(player.getVehicle() instanceof UntaKafilahEntity)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.pelana.bukan").withStyle(ChatFormatting.RED));
			return false;
		}
		AABB box = player.getBoundingBox().inflate(64.0D);
		if (!level.getEntitiesOfClass(Monster.class, box, m -> m.isAlive()).isEmpty() || r.serbuan && !r.binatang.isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.pelana.bahaya").withStyle(ChatFormatting.RED));
			return false;
		}
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0, false, false, false));
		Msg.title(player, Component.translatable("kultivator.kota_tema.oasis.pelana.judul"), Component.translatable("kultivator.kota_tema.oasis.pelana.sub"),
				10, 40, 10);
		r.jarak = Math.min(r.total() - 1.0D, r.jarak + LONCAT_PELANA);
		Vec3 to = r.di(r.jarak - SELA * r.nUnta);
		int y = tanah(level, (int) Math.floor(to.x), (int) Math.floor(to.z), (int) to.y);
		Entity camel = player.getVehicle();
		player.stopRiding();
		camel.teleportTo(to.x, y, to.z);
		player.teleportTo(to.x, y + 1.0D, to.z);
		player.startRiding(camel);
		// the rest of the string is re-made at the new place
		for (UntaKafilahEntity u : new ArrayList<>(r.unta)) {
			if (u != camel) {
				u.ejectPassengers();
				TemaMekanisme.lepas(u);
				r.unta.remove(u);
			}
		}
		for (KultivatorNpc npc : r.orang) {
			npc.teleportTo(to.x, y, to.z);
		}
		return true;
	}

	// ================================================================== camel pens and hire

	/** Anchor {@code tema_oasis/kandang}: resting pen camels (data {@code unta}) and, with {@code sewa 1}, a camel leader. */
	static void kandangDekat(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within, List<Entity> badan) {
		badan.removeIf(Entity::isRemoved);
		long situs = OasisAnchors.situsOf(level, anchor);
		TemaKota.Kota kota = TemaKota.dari(level.getServer(), situs);
		int want = Math.max(0, Math.min(4, OasisAnchors.angka(anchor, "unta", 2)));
		boolean badai = BadaiPasir.aktifDi(level, anchor.blockPosition());
		int have = 0;
		for (Entity e : badan) {
			if (e instanceof UntaKafilahEntity u && !u.disewa()) {
				have++;
				u.badai(badai);
			}
		}
		for (int i = have; i < want; i++) {
			double a = Math.toRadians(anchor.getYRot()) + i * 1.7D;
			Vec3 at = new Vec3(anchor.getX() + Math.cos(a) * (2.5D + i), anchor.getY(), anchor.getZ() + Math.sin(a) * (2.5D + i));
			UntaKafilahEntity u = TemaMekanisme.muncul(level, TemaEntities.UNTA_KAFILAH, at, (float) Math.toDegrees(a), kota, anchor.blockPosition());
			if (u == null) {
				break;
			}
			u.ikat(situs);
			u.setVarian(i % 2 == 0 ? UntaKafilahEntity.VARIAN_BEBAN : UntaKafilahEntity.VARIAN_POLOS);
			if (i % 2 == 1) {
				u.lutut(true);
			}
			badan.add(u);
		}
		if (OasisAnchors.angka(anchor, "sewa", 0) == 1) {
			KultivatorNpc npc = PENUNTUN.get(anchor.getUUID());
			if (npc == null || npc.isRemoved() || !npc.isAlive()) {
				npc = HidupApi.tubuhStaf(level, situs, NpcRole.PENUNTUN_UNTA, anchor.blockPosition().relative(anchor.getDirection(), 2), anchor.getYRot());
				if (npc != null) {
					npc.memory().putBoolean("hold", true);
					npc.memory().putString(Tawar.KIOS, "kandang");
					SosokApi.gerak(npc, GerakId.SAPU, 1, 0, PropId.SAPU, 0, 0);
					PENUNTUN.put(anchor.getUUID(), npc);
				}
			}
		}
	}

	static void kandangLepas(DuniaAnchorEntity anchor) {
		KultivatorNpc npc = PENUNTUN.remove(anchor.getUUID());
		if (npc != null && !npc.isRemoved()) {
			npc.vanish();
		}
	}

	/** Hires a riding camel next to {@code at} for {@code hari} world days. */
	static boolean sewa(ServerPlayer player, int hari, Vec3 at) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		int days = Math.max(1, Math.min(SEWA_HARI_MAKS, hari));
		MinecraftServer server = level.getServer();
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		UntaKafilahEntity mine = SEWAAN.get(player.getUUID());
		long sampai = me.getLongOr("sewa_sampai", 0L);
		boolean masih = sampai > level.getGameTime();
		if (!masih) {
			long harga = HARGA_SEWA * days;
			if (!EkonomiApi.bayar(player, harga, "tema.oasis.sewa_unta")) {
				OasisLayar.pesan(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(harga)), false);
				return false;
			}
			sampai = level.getGameTime() + 24000L * days;
			me.putLong("sewa_sampai", sampai);
			OasisKota.ubah(server);
		} else if (mine != null && !mine.isRemoved() && mine.disewa()) {
			OasisLayar.peringatan(player, Component.translatable("kultivator.kota_tema.oasis.sewa.sudah"));
			return false;
		}
		UntaKafilahEntity u = TemaEntities.UNTA_KAFILAH.create(level, EntitySpawnReason.EVENT);
		if (u == null) {
			return false;
		}
		u.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
		u.ikat(0L);
		u.sewa(player, sampai);
		if (!level.addFreshEntity(u)) {
			return false;
		}
		SEWAAN.put(player.getUUID(), u);
		level.playSound(null, u.blockPosition(), SoundEvents.CAMEL_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
		OasisLayar.pesan(player, Component.translatable(masih ? "kultivator.kota_tema.oasis.sewa.dipanggil" : "kultivator.kota_tema.oasis.sewa.ok", days), true);
		QuestTracker.peristiwa(player, "oasis_unta", "sewa");
		return true;
	}

	/** Ends the hire early (op {@code kembalikan}). */
	static void kembalikan(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		OasisKota.pemain(server, player.getUUID()).putLong("sewa_sampai", 0L);
		OasisKota.ubah(server);
		UntaKafilahEntity u = SEWAAN.remove(player.getUUID());
		if (u != null && !u.isRemoved()) {
			u.pulang();
		}
		OasisLayar.pesan(player, Component.translatable("kultivator.kota_tema.oasis.sewa.kembali"), true);
	}

	/** From the camel: the hire ended (time up or left behind). */
	static void sewaSelesai(ServerLevel level, UntaKafilahEntity unta, @Nullable ServerPlayer owner) {
		if (owner != null) {
			SEWAAN.remove(owner.getUUID(), unta);
			if (level.getGameTime() >= OasisKota.pemain(level.getServer(), owner.getUUID()).getLongOr("sewa_sampai", 0L)) {
				OasisKota.pemain(level.getServer(), owner.getUUID()).putLong("sewa_sampai", 0L);
				OasisKota.ubah(level.getServer());
			}
		}
	}

	// ================================================================== screen tema_kafilah

	private static boolean melayani(@Nullable KultivatorNpc npc) {
		if (npc == null) {
			return false;
		}
		return npc.role() == NpcRole.KAFILAH_BASHI || npc.role() == NpcRole.PENUNTUN_UNTA || OasisTokoh.adalah(npc, OasisTokoh.BULAT_KHAN);
	}

	private static @Nullable KultivatorNpc npcDari(ServerPlayer player, int npcId) {
		Entity e = npcId < 0 ? null : player.level().getEntity(npcId);
		return e instanceof KultivatorNpc k && melayani(k) ? k : null;
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = npcDari(player, npcId);
		if (npc == null || !(player.level() instanceof ServerLevel level)) {
			return null;
		}
		OasisKota.Hidup kota = OasisKota.terdekat(level, npc.position(), OasisKota.JANGKAU_KOTA);
		if (kota == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		CompoundTag tag = OasisLayar.kepala(player, "kultivator.kota_tema.oasis.kafilah.judul", LembarKunci.GAYA_KAIN, npc);
		long hari = OasisKota.hari(level);
		ListTag jadwal = new ListTag();
		Rombongan now = JALAN.get(kota.situs);
		int tibaDalam = (int) Math.floorMod(1L - hari, 5L);
		int pergiDalam = (int) Math.floorMod(3L - hari, 5L);
		jadwal.add(baris("tiba", "kultivator.kota_tema.oasis.kafilah.agung_tiba", "kultivator.kota_tema.oasis.kafilah.dari_barat", tibaDalam,
				now != null && !now.berangkat ? now.nUnta : 6, now != null && !now.berangkat ? (now.bongkar >= 0 ? "bongkar" : "di_jalan") : "tiba"));
		jadwal.add(baris("berangkat", "kultivator.kota_tema.oasis.kafilah.agung_pergi", "kultivator.kota_tema.oasis.kafilah.ke_timur", pergiDalam,
				now != null && now.berangkat ? now.nUnta : 6, now != null && now.berangkat ? "di_jalan" : "berangkat"));
		tag.put(TemaLayar.Kafilah.JADWAL, jadwal);
		CompoundTag ikut = new CompoundTag();
		ikut.putLong(TemaLayar.Kafilah.PENUMPANG, HARGA_PENUMPANG);
		ikut.putBoolean(TemaLayar.Kafilah.PENGAWAL, EkonomiApi.misiAktif(player, "kawal_unta"));
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		boolean sudah = me.getLongOr("ikut_" + kota.situs, Long.MIN_VALUE) == hari + pergiDalam
				|| me.getLongOr("kawal_" + kota.situs, Long.MIN_VALUE) == hari + pergiDalam;
		boolean badai = BadaiPasir.aktif(kota.situs);
		ikut.putBoolean(LembarKunci.BISA, !sudah && !badai && (now == null || !now.berangkat));
		if (sudah) {
			LembarKunci.teks(ikut, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.oasis.kafilah.sudah_daftar", pergiDalam));
		} else if (badai) {
			LembarKunci.teks(ikut, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.oasis.kafilah.badai"));
		}
		tag.put(TemaLayar.Kafilah.IKUT, ikut);
		CompoundTag sewa = new CompoundTag();
		sewa.putLong(LembarKunci.HARGA, HARGA_SEWA);
		sewa.putInt(TemaLayar.Kafilah.HARI_MAKS, SEWA_HARI_MAKS);
		long sampai = me.getLongOr("sewa_sampai", 0L);
		boolean aktif = sampai > level.getGameTime();
		sewa.putBoolean(LembarKunci.AKTIF, aktif);
		sewa.putLong(LembarKunci.SISA_TICK, aktif ? sampai - level.getGameTime() : 0L);
		UntaKafilahEntity u = SEWAAN.get(player.getUUID());
		if (u != null && !u.isRemoved()) {
			sewa.putString(TemaLayar.Kafilah.UNTA, Integer.toString(u.getId()));
		}
		tag.put(TemaLayar.Kafilah.SEWA, sewa);
		tag.putBoolean(TemaLayar.Kafilah.BADAI, badai);
		return tag;
	}

	private static CompoundTag baris(String id, String nama, String tujuan, int hari, int unta, String keadaan) {
		CompoundTag t = new CompoundTag();
		t.putString(LembarKunci.ID, id);
		LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(nama));
		LembarKunci.teks(t, "tujuan", Component.translatable(tujuan));
		t.putInt(TemaLayar.Kafilah.BERANGKAT_HARI, hari);
		t.putInt(TemaLayar.Kafilah.UNTA, unta);
		t.putString(LembarKunci.KEADAAN, keadaan);
		return t;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = npcDari(player, npcId);
		if (npc == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		OasisKota.Hidup kota = OasisKota.terdekat(level, npc.position(), OasisKota.JANGKAU_KOTA);
		if (kota == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		long hari = OasisKota.hari(level);
		long pergi = hari + Math.floorMod(3L - hari, 5L);
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		switch (verb) {
			case TemaLayar.Kafilah.OP_IKUT -> {
				if (BadaiPasir.aktif(kota.situs) || me.getLongOr("ikut_" + kota.situs, Long.MIN_VALUE) == pergi) {
					return;
				}
				if (!EkonomiApi.bayar(player, HARGA_PENUMPANG, "tema.oasis.penumpang")) {
					OasisLayar.pesan(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(HARGA_PENUMPANG)), false);
					return;
				}
				me.putLong("ikut_" + kota.situs, pergi);
				OasisKota.ubah(server);
				npc.say(Component.translatable("kultivator.kota_tema.oasis.kafilah.terima_penumpang"), 70);
				OasisLayar.pesan(player, Component.translatable("kultivator.kota_tema.oasis.kafilah.terdaftar", pergi - hari), true);
			}
			case TemaLayar.Kafilah.OP_KAWAL -> {
				if (!EkonomiApi.misiAktif(player, "kawal_unta")) {
					OasisLayar.peringatan(player, Component.translatable("kultivator.kota_tema.oasis.kafilah.perlu_misi"));
					return;
				}
				me.putLong("kawal_" + kota.situs, pergi);
				OasisKota.ubah(server);
				SosokApi.gerak(npc, GerakId.TEPUK_BAHU, 0, 30);
				OasisLayar.pesan(player, Component.translatable("kultivator.kota_tema.oasis.kafilah.pengawal_terdaftar", pergi - hari), true);
			}
			case TemaLayar.Kafilah.OP_SEWA -> {
				Vec3 at = npc.position().add(npc.getLookAngle().scale(2.0D));
				sewa(player, LembarKunci.argInt(parts, 1, 1), new Vec3(at.x, npc.getY(), at.z));
			}
			case TemaLayar.Kafilah.OP_KEMBALIKAN -> kembalikan(player);
			default -> {
			}
		}
	}

	/** Logout: a hired camel goes home with the player gone (the hire time keeps running). */
	static void lupakan(UUID id) {
		UntaKafilahEntity u = SEWAAN.remove(id);
		if (u != null && !u.isRemoved()) {
			u.pulang();
		}
	}

	private KafilahUnta() {
	}
}
