package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The calendar of the Kota Gunung (DESIGN 38.T.6.4, registered with {@code TemaKalender}; event ids of 38.T.13.2):
 * <ul>
 * <li>{@code ziarah} — Musim Ziarah, world days {@code d % 30} in 0..4: three times the pilgrims (extra PEZIARAH bodies
 * bowing at the landings near players), four extra stalls (data flag {@link #musimZiarah}), and at noon the
 * ceremony of the Lonceng Awan: 108 strokes compressed into 4 s (one bell sound every 2 ticks, rising) with a
 * {@code FESTIVAL lonceng_awan} packet for the client scene.</li>
 * <li>{@code cahaya_budha} — Fajar Cahaya Budha, 23000-1000 on 30 % of the days without rain: the cloud sea and the
 * rainbow ring ({@code CAHAYA budha} + {@code awan}); a player standing on a viewing point of the Aula Emas for 10 s
 * gets the Restu Cahaya (+1 comprehension of the player's Dao, at most once per 7 days); pilgrims bow together.</li>
 * <li>{@code longsor} — rockfall while it rains: each world hour a 25 % roll per town; 3-6 rolling rocks
 * ({@link BatuRuntuhEntity}) on the lanes of {@link GunungTata#LONGSOR}, the plank path closed by the chain barrier,
 * an announcement. Never a block.</li>
 * <li>{@code lift_macet} — only forced (test command, quest): the next cable-car climb stops mid-line.</li>
 * </ul>
 * Rain also closes the plank path ({@code tema_gunung/palang}): the barrier entity and a keeper standing guard.
 */
public final class GunungAcara {

	public static final String ZIARAH = "ziarah";
	public static final String CAHAYA_BUDHA = "cahaya_budha";
	public static final String LONGSOR = "longsor";
	public static final String LIFT_MACET = "lift_macet";

	public static final long SALT_CAHAYA = 380461L;
	public static final long SALT_LONGSOR = 380462L;
	public static final double PELUANG_LONGSOR = 0.25D;
	public static final int CAHAYA_DETIK = 10;
	public static final long RESTU_CAHAYA_JEDA = 7L * 24000L;

	/** Pending bell strokes: game tick -> (town, position). */
	private record Pukul(long tick, Vec3 pos, float pitch) {
	}

	private static final ArrayDeque<Pukul> PUKULAN = new ArrayDeque<>();
	/** Towns whose noon ceremony ran today: "situs|day". */
	private static final Set<String> UPACARA = new HashSet<>();
	/** Towns in the pilgrim season (runtime mirror of the calendar). */
	private static final Set<Long> MUSIM = new HashSet<>();
	/** Towns with an active rockfall (until game tick). */
	private static final Map<Long, Long> LONGSOR_SAMPAI = new HashMap<>();
	/** The last world hour a rockfall roll was made per town. */
	private static final Map<Long, Long> JAM_LONGSOR = new HashMap<>();
	/** Seconds a player stood on a viewing point during the Buddha's light. */
	private static final Map<UUID, Integer> PANDANG = new HashMap<>();
	/** Extra pilgrims of the season per town. */
	private static final Map<Long, List<KultivatorNpc>> PEZIARAH = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaKalender.daftar(GunungData.TEMA, ZIARAH, 0, 23999, TemaKalender.rentang(30, 0, 4), new MusimZiarah());
		TemaKalender.daftar(GunungData.TEMA, CAHAYA_BUDHA, 23000, 1000,
				TemaKalender.dan(TemaKalender.peluang(0.3D, SALT_CAHAYA), (level, hari, situs, pusat) -> !level.isRaining() && TemaConfig.gunung().cahayaBudha), new Cahaya());
		TemaKalender.daftar(GunungData.TEMA, LONGSOR, 0, 23999, TemaKalender.selalu(), new Longsor());
		TemaKalender.daftar(GunungData.TEMA, LIFT_MACET, 0, 23999, (level, hari, situs, pusat) -> false, new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				KeretaGantung.macetkan(situsId);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(GunungAcara::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PUKULAN.clear();
			UPACARA.clear();
			MUSIM.clear();
			LONGSOR_SAMPAI.clear();
			JAM_LONGSOR.clear();
			PANDANG.clear();
			PEZIARAH.clear();
		});
	}

	/** True while town {@code situsId} is in the pilgrim season (extra stalls, more pilgrims). */
	public static boolean musimZiarah(long situsId) {
		return MUSIM.contains(situsId);
	}

	/** True while a rockfall runs in town {@code situsId}. */
	public static boolean longsor(ServerLevel level, long situsId) {
		Long until = LONGSOR_SAMPAI.get(situsId);
		return until != null && until > level.getGameTime();
	}

	private static void tick(MinecraftServer server) {
		if (PUKULAN.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		while (!PUKULAN.isEmpty() && PUKULAN.peekFirst().tick() <= now) {
			Pukul p = PUKULAN.pollFirst();
			level.playSound(null, p.pos().x, p.pos().y, p.pos().z, SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 2.0F, p.pitch());
		}
	}

	private static void umumkan(ServerLevel level, TemaKota.Kota kota, Component teks, ChatFormatting warna) {
		Vec3 c = Vec3.atCenterOf(kota.pusat());
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(c) < 200.0D * 200.0D) {
				Msg.chat(p, teks.copy().withStyle(warna));
			}
		}
	}

	// ================================================================================================ Musim Ziarah

	private static final class MusimZiarah implements TemaKalender.Pelaksana {

		@Override
		public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
			MUSIM.add(situsId);
			TemaKota.Kota kota = TemaKota.dari(level.getServer(), situsId);
			if (kota != null) {
				umumkan(level, kota, Component.translatable("kultivator.kota_tema.gunung.acara.ziarah.mulai", kota.nama(level.getSeed())),
						ChatFormatting.GOLD);
			}
		}

		@Override
		public void tick(ServerLevel level, long situsId, int tickKe) {
			TemaKota.Kota kota = TemaKota.dari(level.getServer(), situsId);
			if (kota == null) {
				return;
			}
			long jam = TemaKalender.jam(level);
			String key = situsId + "|" + TemaKalender.hari(level);
			if (jam >= 6000 && jam < 6400 && UPACARA.add(key)) {
				upacaraLonceng(level, kota);
			}
			if (tickKe % 200 == 0) {
				peziarahTambahan(level, kota);
			}
		}

		@Override
		public void akhir(ServerLevel level, long situsId) {
			MUSIM.remove(situsId);
			List<KultivatorNpc> extra = PEZIARAH.remove(situsId);
			if (extra != null) {
				extra.forEach(n -> {
					if (!n.isRemoved()) {
						n.vanish();
					}
				});
			}
		}
	}

	/** The noon ceremony: 108 strokes in 4 s (54 sounds, every 2nd tick, two strokes each), a festival packet. */
	public static void upacaraLonceng(ServerLevel level, TemaKota.Kota kota) {
		Vec3 at = GunungTata.dunia(kota, GunungTata.LONCENG);
		long now = level.getGameTime();
		for (int i = 0; i < 54; i++) {
			PUKULAN.addLast(new Pukul(now + 10 + i * 2L, at, 0.6F + 0.6F * i / 54.0F));
		}
		CompoundTag data = new CompoundTag();
		data.putString("acara", "lonceng_awan");
		data.putBoolean("mulai", true);
		data.putLong("tick", now + 10);
		data.putLong("akhir", now + 120);
		data.putInt("x", (int) Math.floor(at.x));
		data.putInt("y", (int) Math.floor(at.y));
		data.putInt("z", (int) Math.floor(at.z));
		TemaNet.kirimSekitar(level, at, 160.0D, TemaNet.FESTIVAL, kota.situsId(), data);
		umumkan(level, kota, Component.translatable("kultivator.kota_tema.gunung.acara.lonceng"), ChatFormatting.YELLOW);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) < 96.0D * 96.0D) {
				CeritaApi.peristiwa(p, "tanda", "gunung_lonceng_awan");
			}
		}
	}

	/** Up to six extra pilgrims bowing on the landings within 64 blocks of a player (DuniaBudget caps them). */
	private static void peziarahTambahan(ServerLevel level, TemaKota.Kota kota) {
		List<KultivatorNpc> list = PEZIARAH.computeIfAbsent(kota.situsId(), k -> new ArrayList<>());
		list.removeIf(Entity::isRemoved);
		if (list.size() >= 6) {
			return;
		}
		for (GunungTata.Titik t : GunungTata.BORDES) {
			if (list.size() >= 6) {
				break;
			}
			Vec3 at = GunungTata.dunia(kota, t);
			if (!TemaMekanisme.pemainDekat(level, at, 64.0D) || level.getRandom().nextInt(3) != 0) {
				continue;
			}
			BlockPos pos = BlockPos.containing(at.x + level.getRandom().nextInt(5) - 2, at.y, at.z + level.getRandom().nextInt(3));
			KultivatorNpc npc = HidupApi.tubuh(level, GunungAnchors.kotaId(level, pos), NpcRole.PEZIARAH, pos, GunungTata.yaw(kota, 180.0F));
			if (npc != null) {
				npc.memory().putString("tema", GunungData.TEMA);
				HidupApi.klip(npc, GerakId.SUJUD_ZIARAH, 0, 0);
				list.add(npc);
			}
		}
	}

	// ================================================================================================ Cahaya Budha

	private static final class Cahaya implements TemaKalender.Pelaksana {

		@Override
		public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
			TemaKota.Kota kota = TemaKota.dari(level.getServer(), situsId);
			if (kota == null) {
				return;
			}
			kirim(level, kota, true);
			umumkan(level, kota, Component.translatable("kultivator.kota_tema.gunung.acara.cahaya"), ChatFormatting.AQUA);
		}

		@Override
		public void tick(ServerLevel level, long situsId, int tickKe) {
			TemaKota.Kota kota = TemaKota.dari(level.getServer(), situsId);
			if (kota == null) {
				return;
			}
			for (ServerPlayer p : level.players()) {
				boolean di = false;
				for (GunungTata.Titik t : GunungTata.PANDANG) {
					if (GunungTata.dunia(kota, t).distanceToSqr(p.position()) < 3.0D * 3.0D) {
						di = true;
						break;
					}
				}
				if (!di) {
					PANDANG.remove(p.getUUID());
					continue;
				}
				int s = PANDANG.merge(p.getUUID(), 1, Integer::sum);
				if (s == CAHAYA_DETIK) {
					restuCahaya(p);
				}
			}
			if (tickKe % 100 == 0) {
				Vec3 c = GunungTata.dunia(kota, GunungTata.AULA_EMAS);
				for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(c, c).inflate(40.0D, 16.0D, 40.0D))) {
					if (npc.role() == NpcRole.PEZIARAH || npc.role() == NpcRole.BIKSU_GUNUNG) {
						HidupApi.klip(npc, GerakId.SUJUD_ZIARAH, 0, 120);
					}
				}
			}
		}

		@Override
		public void akhir(ServerLevel level, long situsId) {
			TemaKota.Kota kota = TemaKota.dari(level.getServer(), situsId);
			if (kota != null) {
				kirim(level, kota, false);
			}
		}

		private static void kirim(ServerLevel level, TemaKota.Kota kota, boolean nyala) {
			Vec3 ring = GunungTata.dunia(kota, GunungTata.CAHAYA);
			for (String apa : new String[]{"budha", "awan"}) {
				CompoundTag data = new CompoundTag();
				data.putString("apa", apa);
				data.putInt("x", (int) Math.floor(ring.x));
				data.putInt("y", (int) Math.floor(ring.y));
				data.putInt("z", (int) Math.floor(ring.z));
				data.putLong("tick", level.getGameTime());
				data.putLong("panjang", 2000L);
				data.putBoolean("nyala", nyala);
				TemaNet.kirimSekitar(level, Vec3.atCenterOf(kota.pusat()), 192.0D, TemaNet.CAHAYA, kota.situsId(), data);
			}
		}
	}

	/** Restu Cahaya: +1 comprehension of the player's Dao, at most once per 7 world days. */
	static void restuCahaya(ServerPlayer player) {
		CompoundTag p = GunungData.pemain(player);
		long now = player.level().getGameTime();
		if (p.getLongOr("rc", 0L) > now) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.acara.cahaya.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		p.putLong("rc", now + RESTU_CAHAYA_JEDA);
		GunungData.ubah(player.level().getServer());
		Dao dao = Cultivators.get(player).dao();
		if (dao != null) {
			Comprehension.grant(player, dao, 1.0D);
		}
		Msg.title(player, Component.translatable("kultivator.kota_tema.gunung.acara.cahaya.restu").withStyle(ChatFormatting.AQUA),
				Component.translatable("kultivator.kota_tema.gunung.acara.cahaya.restu.sub"), 10, 70, 30);
		dev.kultivator.util.Advancements.grant(player, "tempat/tema_gunung_cahaya_budha");
		CeritaApi.peristiwa(player, "tanda", "gunung_cahaya_budha");
	}

	// ================================================================================================ Longsor

	private static final class Longsor implements TemaKalender.Pelaksana {

		@Override
		public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
			if (dipaksa) {
				TemaKota.Kota kota = TemaKota.dari(level.getServer(), situsId);
				if (kota != null) {
					runtuh(level, kota);
				}
			}
		}

		@Override
		public void tick(ServerLevel level, long situsId, int tickKe) {
			if (!TemaConfig.gunung().longsor || !level.isRaining()) {
				return;
			}
			long jam = Math.floorDiv(level.getServer().overworld().getOverworldClockTime(), 1000L);
			Long last = JAM_LONGSOR.put(situsId, jam);
			if (last != null && last == jam) {
				return;
			}
			long h = jam * 0x9E3779B97F4A7C15L ^ situsId * 0xC2B2AE3D27D4EB4FL ^ SALT_LONGSOR;
			h ^= h >>> 29;
			h *= 0xBF58476D1CE4E5B9L;
			h ^= h >>> 32;
			if ((h >>> 11) * 0x1.0p-53 >= PELUANG_LONGSOR) {
				return;
			}
			TemaKota.Kota kota = TemaKota.dari(level.getServer(), situsId);
			if (kota != null) {
				runtuh(level, kota);
			}
		}
	}

	/** Starts a rockfall in town {@code kota}: 3-6 rocks spread over the lanes near players. */
	public static int runtuh(ServerLevel level, TemaKota.Kota kota) {
		int n = 3 + level.getRandom().nextInt(4);
		int made = 0;
		for (int i = 0; i < n; i++) {
			GunungTata.Lajur lajur = GunungTata.LONGSOR.get(level.getRandom().nextInt(GunungTata.LONGSOR.size()));
			List<Vec3> pts = new ArrayList<>();
			for (double[] t : lajur.titik()) {
				pts.add(GunungTata.dunia(kota, lajur.distrik(), t[0] + (level.getRandom().nextDouble() - 0.5D) * 3.0D, t[1], t[2]));
			}
			if (!TemaMekanisme.pemainDekat(level, pts.getFirst(), 96.0D)) {
				continue;
			}
			BatuRuntuhEntity batu = TemaMekanisme.muncul(level, TemaEntities.BATU_RUNTUH, pts.getFirst(), 0.0F, kota, null);
			if (batu != null) {
				batu.setVarian(level.getRandom().nextInt(3));
				batu.luncurkan(pts, i * 15 + level.getRandom().nextInt(10));
				made++;
			}
		}
		if (made > 0) {
			LONGSOR_SAMPAI.put(kota.situsId(), level.getGameTime() + 1200L);
			umumkan(level, kota, Component.translatable("kultivator.kota_tema.gunung.acara.longsor"), ChatFormatting.RED);
			HidupApi.bahaya(level, BlockPos.containing(GunungTata.dunia(kota, "tebing_papan", 24.0D, 10.0D, 50.0D)), 60);
		}
		return made;
	}

	// ================================================================================================ the barrier

	/** The plank path's foot: in rain or during a rockfall the chain barrier closes it and a keeper stands guard. */
	static void dekatPalang(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within, List<Entity> buatan) {
		TemaKota.Kota kota = GunungAnchors.kota(anchor);
		boolean tutup = level.isRaining() || kota != null && longsor(level, kota.situsId());
		PalangEntity palang = null;
		KultivatorNpc jaga = null;
		for (Entity e : buatan) {
			if (e instanceof PalangEntity p) {
				palang = p;
			} else if (e instanceof KultivatorNpc n) {
				jaga = n;
			}
		}
		if (tutup) {
			if (palang == null) {
				palang = TemaMekanisme.muncul(level, TemaEntities.PALANG, anchor.position(), anchor.getYRot() + 90.0F, kota, anchor.blockPosition());
				if (palang != null) {
					buatan.add(palang);
				}
			}
			if (palang != null) {
				palang.setKeadaan(PalangEntity.KEADAAN_TERPASANG);
			}
			if (jaga == null) {
				BlockPos at = anchor.blockPosition().offset(2, 0, 1);
				jaga = HidupApi.tubuh(level, GunungAnchors.kotaId(level, anchor), NpcRole.PEMETIK_HERBA, at, anchor.getYRot());
				if (jaga != null) {
					jaga.memory().putBoolean("hold", true);
					HidupApi.klip(jaga, GerakId.BERDIRI_JAGA, 0, 0);
					buatan.add(jaga);
				}
			}
			for (ServerPlayer p : within) {
				if (p.distanceToSqr(anchor) < 6.0D * 6.0D && level.getServer().getTickCount() % 200 == 0) {
					Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.palang.tutup").withStyle(ChatFormatting.YELLOW));
				}
			}
		} else {
			if (palang != null) {
				palang.setKeadaan(PalangEntity.KEADAAN_TERBUKA);
				if (palang.umurKeadaan(level.getGameTime()) > 40) {
					TemaMekanisme.lepas(palang);
					buatan.remove(palang);
				}
			}
			if (jaga != null) {
				jaga.vanish();
				buatan.remove(jaga);
			}
		}
	}

	private GunungAcara() {
	}
}
