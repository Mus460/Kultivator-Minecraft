package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.kota.KotaEntities;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * C38.NL1: the one door of 38NL for every other module (DESIGN 38.E.3, amended by 40.7.4). Server thread only.
 *
 * <ul>
 *   <li>{@link #tubuh}: a service / repair / bill-collector body of a role at a spot (counts toward the NPC cap).</li>
 *   <li>{@link #klip}, {@link #tatap}: animation clip and head gaze, delegated to {@link SosokApi} (DESIGN 40: catalogue
 *       and player are 40G's; clip ids {@code GerakId}, prop ids {@code dev.kultivator.sosok.PropId}).</li>
 *   <li>{@link #timpaJadwal}: an event that overrides the daily schedule of a place ({@code "festival_lentera"},
 *       {@code "tonton:<x,z>"} crowd for 39F C39.K11, {@code "tema.<acara>"} for 38TK); read back by {@code Jadwal}
 *       through {@link #timpaan}. {@link #kerumunan} / {@link #bubarkan} wrap the crowd for a scene at a position.</li>
 *   <li>{@link #penduduk}, {@link #saksi}, {@link #bahaya}: residents, crime witnesses and the danger reaction of 38.N.4.</li>
 * </ul>
 *
 * <p>Residents come from {@link PendudukGen}, their bodies from {@link Pemanggil}, the danger reaction and the rest of
 * their visible life from {@link TubuhPenduduk}; the signatures here never change.
 */
public final class HidupApi {
	/** Witness range of {@link #saksi} (38.N.4 butir 10). */
	public static final double SAKSI_JARAK = 16.0D;
	/** A body made by {@link #tubuh} leaves when no player is within this range for {@link #TUBUH_SEPI} ticks (38.N.1). */
	public static final double TUBUH_JARAK = 56.0D;
	public static final int TUBUH_SEPI = 600;
	/** A workplace body ({@link #tubuhStaf}) is called from up to 128 blocks, so it stays while a player is within this. */
	public static final double STAF_JARAK = 160.0D;
	/** At most this many danger zones are remembered at once (oldest dropped). */
	private static final int BAHAYA_MAKS = 32;
	/** Watchers of a {@code tonton:<x,z>} crowd (C39.K11: at most 8 walk to the ring's edge). */
	public static final int TONTON_MAKS = 8;

	private static final Map<Long, Timpaan> TIMPAAN = new HashMap<>();
	/** Crowd size of the active {@code tonton:} override per place (absent = {@link #TONTON_MAKS}). */
	private static final Map<Long, Integer> TONTON_UKURAN = new HashMap<>();
	private static final ArrayDeque<Bahaya> BAHAYA = new ArrayDeque<>();

	/** A schedule override of one place until game time {@code sampai}. */
	public record Timpaan(String acara, long sampai) {
	}

	/** A danger zone: civilians within {@code jari} blocks flee until game time {@code sampai} (38.N.4 butir 11). */
	public record Bahaya(ResourceKey<Level> dimensi, BlockPos pusat, int jari, long sampai) {
	}

	private HidupApi() {
	}

	// ================================================================== bodies

	/**
	 * A body of {@code peran} at {@code pos} facing {@code yaw}, bound to the place {@code situsId} (anchor site and town
	 * reputation id), or null when the NPC cap ({@link DuniaBudget#NPC}) is full or the spawn fails. The body is
	 * {@code noSave} like every {@link KultivatorNpc}; it leaves when no player is near for a while. Realm follows the
	 * role's tier (mortal civilians, Foundation-Nascent Soul fighters, Immortal City roles ascended).
	 */
	public static @Nullable KultivatorNpc tubuh(ServerLevel level, long situsId, NpcRole peran, BlockPos pos, float yaw) {
		int look = (int) (situsId ^ (situsId >>> 32)) ^ (int) pos.asLong() ^ (peran.ordinal() * 0x9E3779B9);
		return tubuh(level, situsId, peran, pos, yaw, look);
	}

	/**
	 * {@link #tubuh(ServerLevel, long, NpcRole, BlockPos, float)} with an explicit look seed: a named person the module
	 * keeps in its data (a rival alchemist of the pill meeting, a quest giver) gets the same face, sex, voice and name
	 * plate every time ({@code KotaNames.plate(look, peran)}; the Sosok follows from role and look, DESIGN 40.2.2).
	 */
	public static @Nullable KultivatorNpc tubuh(ServerLevel level, long situsId, NpcRole peran, BlockPos pos, float yaw, int look) {
		return buat(level, situsId, peran, pos, yaw, look, false);
	}

	/**
	 * A workplace's own person (shop keeper, service clerk, tea master, named quest giver at its lot): like
	 * {@link #tubuh(ServerLevel, long, NpcRole, BlockPos, float)} but never refused by the NPC caps or the MSPT guard, so every
	 * shop and service is staffed whenever a player is near; it leaves when no player is within {@link #STAF_JARAK}
	 * blocks for {@link #TUBUH_SEPI} ticks. Only the world switch {@code umum.enabled} stops it.
	 */
	public static @Nullable KultivatorNpc tubuhStaf(ServerLevel level, long situsId, NpcRole peran, BlockPos pos, float yaw) {
		int look = (int) (situsId ^ (situsId >>> 32)) ^ (int) pos.asLong() ^ (peran.ordinal() * 0x9E3779B9);
		return buat(level, situsId, peran, pos, yaw, look, true);
	}

	/** {@link #tubuhStaf(ServerLevel, long, NpcRole, BlockPos, float)} with an explicit look seed (a named character). */
	public static @Nullable KultivatorNpc tubuhStaf(ServerLevel level, long situsId, NpcRole peran, BlockPos pos, float yaw, int look) {
		return buat(level, situsId, peran, pos, yaw, look, true);
	}

	private static @Nullable KultivatorNpc buat(ServerLevel level, long situsId, NpcRole peran, BlockPos pos, float yaw, int look, boolean staf) {
		Vec3 at = Vec3.atBottomCenterOf(pos);
		if (staf ? !DuniaSpine.enabled() : !DuniaBudget.mayAdd(level, DuniaBudget.NPC, at)) {
			return null;
		}
		KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, npc, at);
		if (spot == null) {
			spot = at;
		}
		npc.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		npc.setup(peran, look, realmFor(peran, look), Math.floorMod(look >>> 5, 4), Dao.values()[Math.floorMod(look >>> 11, Dao.COUNT)],
				Math.floorMod(look >>> 3, 6), 0, KotaNames.plate(look, peran));
		npc.setHome(BlockPos.containing(spot));
		npc.setTown(situsId);
		npc.setAnchorSite(situsId);
		npc.setLonely(staf ? STAF_JARAK : TUBUH_JARAK, TUBUH_SEPI);
		npc.setFlag(KultivatorNpc.FLAG_GHOST, peran == NpcRole.SISA_JIWA);
		if (peran.kelompok == NpcRole.Kelompok.PENJAGA) {
			npc.setFightsMonsters(true);
		}
		if (peran.stationary) {
			npc.memory().putBoolean("hold", true);
		}
		if (!level.addFreshEntity(npc)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		return npc;
	}

	/** Realm ordinal of a 38 body by role tier (two stages of spread from the look seed). */
	public static int realmFor(NpcRole peran, int look) {
		if (peran.immortal()) {
			return Realm.IMMORTAL_ASCENSION.ordinal();
		}
		int pick = Math.floorMod(look >>> 7, 2);
		return switch (peran.tier) {
			case 0 -> Realm.MORTAL.ordinal();
			case 1 -> Realm.MORTAL.ordinal() + pick;
			case 2 -> Realm.FOUNDATION.ordinal();
			case 3 -> Realm.GOLDEN_CORE.ordinal();
			case 4 -> Realm.NASCENT_SOUL.ordinal() + pick;
			default -> Realm.DEITY_TRANSFORMATION.ordinal() + pick;
		};
	}

	// ================================================================== animation (DESIGN 40.7.4)

	/** Plays clip {@code klip} for {@code tick} ticks (0 = until replaced) with prop {@code prop} in the right hand. */
	public static void klip(KultivatorNpc npc, int klip, int prop, int tick) {
		SosokApi.gerak(npc, klip, 0, tick, prop, 0, 0);
	}

	/** Turns the head and eyes toward {@code sasaran} (null = the walking direction). */
	public static void tatap(KultivatorNpc npc, @Nullable Entity sasaran) {
		SosokApi.tatap(npc, sasaran);
	}

	// ================================================================== schedule overrides

	/**
	 * Overrides the daily schedule of every resident of {@code situsId} with the event {@code acara} until game time
	 * {@code sampai}; an empty {@code acara} or a past {@code sampai} clears the override.
	 */
	public static void timpaJadwal(long situsId, String acara, long sampai) {
		if (acara == null || acara.isEmpty()) {
			TIMPAAN.remove(situsId);
			return;
		}
		TIMPAAN.put(situsId, new Timpaan(acara, sampai));
	}

	/**
	 * A crowd gathers around {@code pusat} for {@code detik} seconds (C39.K11 for 39F's feuds, any scene of another
	 * module): the place at {@code pusat} gets the override {@code tonton:<x,z>}, and its {@value #TONTON_MAKS} nearest free
	 * residents walk to a ring 8-10 blocks around it, face it and cheer, clap or watch arms folded (their day resumes when
	 * it ends; guards keep their posts). Returns the place's site id, or 0 when {@code pusat} lies in no inhabited place.
	 */
	public static long kerumunan(ServerLevel level, BlockPos pusat, int detik) {
		return kerumunan(level, pusat, detik, TONTON_MAKS);
	}

	/** As {@link #kerumunan(ServerLevel, BlockPos, int)}, with at most {@code maks} watchers (0..{@value #TONTON_MAKS}). */
	public static long kerumunan(ServerLevel level, BlockPos pusat, int detik, int maks) {
		TempatHuni huni = Saksi.tempat(level, pusat);
		long situs = huni != null ? huni.situsId : 0L;
		if (situs == 0L) {
			dev.kultivator.dunia.tempat.TempatInfo info = dev.kultivator.dunia.tempat.TempatApi.tempatDi(level, pusat);
			situs = info != null && PendudukGen.dihuni(info.jenis()) ? info.situsId() : 0L;
		}
		if (situs == 0L || detik <= 0) {
			return 0L;
		}
		TONTON_UKURAN.put(situs, Math.max(0, Math.min(TONTON_MAKS, maks)));
		timpaJadwal(situs, "tonton:" + pusat.getX() + "," + pusat.getZ(), level.getGameTime() + detik * 20L);
		if (huni != null) {
			for (KultivatorNpc body : huni.tubuh.values()) {
				TubuhPenduduk.segarkan(body);
			}
		}
		return situs;
	}

	/** The watcher cap of the crowd at {@code situsId}. */
	static int tontonUkuran(long situsId) {
		return TONTON_UKURAN.getOrDefault(situsId, TONTON_MAKS);
	}

	/** Ends the crowd of {@link #kerumunan} at place {@code situsId} (the scene is over). */
	public static void bubarkan(ServerLevel level, long situsId) {
		String now = timpaan(situsId, level.getGameTime());
		if (now != null && now.startsWith("tonton:")) {
			timpaJadwal(situsId, "", 0L);
			TONTON_UKURAN.remove(situsId);
			TempatHuni huni = Pemanggil.huni(situsId);
			if (huni != null) {
				for (KultivatorNpc body : huni.tubuh.values()) {
					TubuhPenduduk.segarkan(body);
				}
			}
		}
	}

	/** The active schedule override of {@code situsId} at game time {@code now}, or null (expired ones are dropped). */
	public static @Nullable String timpaan(long situsId, long now) {
		Timpaan t = TIMPAAN.get(situsId);
		if (t == null) {
			return null;
		}
		if (now >= t.sampai()) {
			TIMPAAN.remove(situsId);
			return null;
		}
		return t.acara();
	}

	// ================================================================== residents, witnesses, danger

	/**
	 * The residents of {@code situsId} as they are now (newcomers applied), empty while no player has been near the
	 * place since the server started ({@link PendudukGen} makes them when the place first becomes active).
	 */
	public static List<Penduduk> penduduk(long situsId) {
		TempatHuni huni = Pemanggil.huni(situsId);
		return huni == null ? List.of() : huni.penduduk();
	}

	/** The resident a body stands for, or null for any other NPC. */
	public static @Nullable Penduduk pendudukDari(KultivatorNpc npc) {
		return TubuhPenduduk.penduduk(npc);
	}

	/**
	 * True when a townsfolk body within {@value #SAKSI_JARAK} blocks of {@code pos} can see {@code pelaku} (38.N.4 butir
	 * 10, 38.B.5.2 crime witnesses), or, failing a body, when the schedule puts a resident of the place in that lot (a
	 * 50 % chance, 38.N.6 "saksi data", {@link Saksi#dataSaksi}). Bodies already fighting the culprit, and the
	 * culprit's companions, do not count.
	 */
	public static boolean saksi(ServerLevel level, BlockPos pos, ServerPlayer pelaku) {
		AABB box = new AABB(pos).inflate(SAKSI_JARAK);
		List<KultivatorNpc> near = level.getEntitiesOfClass(KultivatorNpc.class, box,
				npc -> npc.isAlive() && npc.role().townsfolk() && !pelaku.getUUID().equals(npc.companionOf()) && npc.getTarget() != pelaku);
		for (KultivatorNpc npc : near) {
			if (npc.hasLineOfSight(pelaku)) {
				return true;
			}
		}
		return Saksi.dataSaksi(level, pos, pelaku);
	}

	/**
	 * Marks danger at {@code pos} for {@code detik} seconds: civilians within 16 blocks flee to a door or hide, guards
	 * draw, fighters of tier >= 2 join (38.N.4 butir 11); read back by the schedule through {@link #bahayaDi}.
	 */
	public static void bahaya(ServerLevel level, BlockPos pos, int detik) {
		if (detik <= 0) {
			return;
		}
		long now = level.getGameTime();
		prune(now);
		while (BAHAYA.size() >= BAHAYA_MAKS) {
			BAHAYA.pollFirst();
		}
		BAHAYA.addLast(new Bahaya(level.dimension(), pos.immutable(), 16, now + detik * 20L));
	}

	/** The danger zone covering {@code pos}, or null. */
	public static @Nullable Bahaya bahayaDi(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		prune(now);
		for (Bahaya b : BAHAYA) {
			if (b.dimensi().equals(level.dimension()) && b.pusat().distSqr(pos) <= (double) b.jari() * b.jari()) {
				return b;
			}
		}
		return null;
	}

	private static void prune(long now) {
		for (Iterator<Bahaya> it = BAHAYA.iterator(); it.hasNext(); ) {
			if (now >= it.next().sampai()) {
				it.remove();
			}
		}
	}

	/** Forgets every override and danger zone (server stop; {@link Hidup#init()} registers it). */
	static void lupakan() {
		TIMPAAN.clear();
		TONTON_UKURAN.clear();
		BAHAYA.clear();
	}
}
