package dev.kultivator.formasi.keeper;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreStore;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Penjaga Formasi (DESIGN 24.2.8): disciples keeping a core with activity
 * {@link DiscipleEntity#ACTIVITY_FORMATION_KEEPER} at a post within 2 blocks of the eye. Assignments are saved in the core
 * record and in {@code DiscipleData.extra().formasi {core, dim, order}}. {@link #refresh} runs once per second: it frees
 * keepers that took another activity, counts keepers at their post and caches their modifiers on the record.
 */
public final class Keepers {

	public static final String EXTRA = "formasi";
	/** Tugaskan lists disciples within this many blocks of the eye. */
	public static final double ASSIGN_RANGE = 48.0D;
	/** A disciple ordered to STAY within this many blocks of a running eye with a free slot becomes its keeper. */
	public static final double AUTO_RANGE = 3.0D;
	/** A keeper within this many blocks of its post counts as at its post. */
	public static final double POST_RANGE = 3.0D;
	public static final double PERKUAT_PER_REALM = 0.05D;
	public static final double PERKUAT_CAP = 0.25D;
	public static final double JAGA_HIGH = 0.5D;
	public static final double JAGA_SECOND = 0.75D;
	public static final double WASPADA_UPKEEP = 0.9D;
	public static final double UPKEEP_FLOOR = 0.25D;
	/** Perkuat keepers gain only this share of their own meditation XP while keeping. */
	public static final double PERKUAT_OWN_XP = 0.5D;

	/** Assigns {@code disciple} to core {@code coreId} with {@code order}; null on success, else why not. */
	public static @Nullable Component assign(ServerPlayer master, int coreId, DiscipleEntity disciple, KeeperOrder order) {
		if (!(disciple.level() instanceof ServerLevel level)) {
			return Component.translatable("kultivator.formasi.keeper.no_core");
		}
		CoreRecord record = CoreTicker.store(level).byId(coreId);
		if (record == null) {
			return Component.translatable("kultivator.formasi.keeper.no_core");
		}
		return assign(master, level, record, disciple, order, false);
	}

	/** The full assignment; {@code ignoreRank} skips the realm and rank checks (test command). */
	public static @Nullable Component assign(ServerPlayer master, ServerLevel level, CoreRecord record, DiscipleEntity disciple,
			KeeperOrder order, boolean ignoreRank) {
		FormationSpec spec = FormationRegistry.byId(record.spec);
		if (spec == null || !record.owner.equals(master.getUUID())) {
			return Component.translatable("kultivator.formasi.keeper.not_owner");
		}
		if (disciple.role() != DiscipleEntity.Role.DISCIPLE || !master.getUUID().equals(disciple.ownerId()) || disciple.isReleasing()) {
			return Component.translatable("kultivator.formasi.keeper.not_yours", disciple.getName());
		}
		if (disciple.level() != level || disciple.distanceToSqr(Vec3.atCenterOf(record.eye)) > ASSIGN_RANGE * ASSIGN_RANGE) {
			return Component.translatable("kultivator.formasi.keeper.too_far", disciple.getName(), (int) ASSIGN_RANGE);
		}
		if ((disciple.syncedFlags() & DiscipleEntity.FLAG_BREAKING_THROUGH) != 0 || disciple.departed()) {
			return Component.translatable("kultivator.formasi.keeper.busy", disciple.getName());
		}
		boolean already = record.hasKeeper(disciple.soulId());
		if (!already && disciple.activity() != DiscipleEntity.ACTIVITY_NONE && disciple.activity() != DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
			return Component.translatable("kultivator.formasi.keeper.busy", disciple.getName());
		}
		DiscipleData data = disciple.data();
		Grade grade = spec.grade();
		if (!ignoreRank && !data.realm().atLeast(grade.keeperRealm().realm())) {
			return Component.translatable("kultivator.formasi.keeper.realm", disciple.getName(), grade.keeperRealm().realm().displayName());
		}
		if (!already) {
			if (record.keepers.size() >= grade.maxKeepers()) {
				return Component.translatable("kultivator.formasi.keeper.full", grade.maxKeepers());
			}
			if (!ignoreRank && !record.keepers.isEmpty()) {
				int need = grade == Grade.ABADI ? SectRanks.INTI : SectRanks.DALAM;
				if (SectRanks.rank(data) < need) {
					return Component.translatable("kultivator.formasi.keeper.rank", disciple.getName(), SectRanks.rankName(need));
				}
			}
		}
		BlockPos post = post(level, disciple, record.eye);
		if (post == null) {
			return Component.translatable("kultivator.formasi.keeper.no_post");
		}
		// A keeper keeps one core only.
		CompoundTag extra = data.extra().getCompound(EXTRA).orElse(null);
		if (extra != null && !already) {
			releaseRecord(disciple, extra);
		}
		if (already) {
			record.keeper(disciple.soulId()).order = order;
		} else {
			record.keepers.add(new CoreRecord.Keeper(disciple.soulId(), order));
		}
		CompoundTag section = data.extraSection(EXTRA);
		section.putInt("core", record.id);
		section.putString("dim", record.dim.identifier().toString());
		section.putString("order", order.id());
		// The master's order: no longer a post the disciple chose by itself (DESIGN 39D).
		section.remove("self");
		disciple.setActivity(DiscipleEntity.ACTIVITY_FORMATION_KEEPER, post);
		CoreTicker.markDirty(level);
		Advancements.grant(master, "formation_keeper");
		return null;
	}

	// ================================================================== self-chosen keeping (DESIGN 39D, contract C39.D1)

	/**
	 * DESIGN 39D: the running core of {@code owner} in the disciple's level, within {@link #ASSIGN_RANGE} blocks of it, that
	 * the disciple may keep by itself now (a free slot, the grade's keeper realm, the rank a second keeper needs), the
	 * nearest first; null when none. Reads the level's core store only (a handful of records), never blocks.
	 */
	public static @Nullable CoreRecord selfCore(UUID owner, DiscipleEntity disciple) {
		if (owner == null || !(disciple.level() instanceof ServerLevel level)) {
			return null;
		}
		DiscipleData data = disciple.data();
		CoreRecord best = null;
		double bestDistance = ASSIGN_RANGE * ASSIGN_RANGE;
		for (CoreRecord record : CoreTicker.store(level).all()) {
			FormationSpec spec = FormationRegistry.byId(record.spec);
			if (spec == null || !owner.equals(record.owner) || !record.state.running() || record.hasKeeper(disciple.soulId())) {
				continue;
			}
			Grade grade = spec.grade();
			if (record.keepers.size() >= grade.maxKeepers() || !data.realm().atLeast(grade.keeperRealm().realm())) {
				continue;
			}
			if (!record.keepers.isEmpty() && SectRanks.rank(data) < (grade == Grade.ABADI ? SectRanks.INTI : SectRanks.DALAM)) {
				continue;
			}
			double distance = disciple.distanceToSqr(Vec3.atCenterOf(record.eye));
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = record;
			}
		}
		return best;
	}

	/**
	 * DESIGN 39D: the disciple takes keeper post of core {@code coreId} by itself with {@code order} (the planner's
	 * JAGA_FORMASI). The old rules of {@link #assign} apply with {@code owner} in place of the master (its own core, its
	 * own disciple within {@link #ASSIGN_RANGE}, not breaking through, idle, the grade's realm and rank, a free slot, a
	 * safe post), but nothing is said to the master and no advancement is granted. Null on success, else why not.
	 */
	public static @Nullable Component assignSelf(UUID owner, ServerLevel level, int coreId, DiscipleEntity disciple, KeeperOrder order) {
		CoreRecord record = CoreTicker.store(level).byId(coreId);
		FormationSpec spec = record == null ? null : FormationRegistry.byId(record.spec);
		if (record == null || spec == null) {
			return Component.translatable("kultivator.formasi.keeper.no_core");
		}
		if (owner == null || !record.owner.equals(owner) || !record.state.running()) {
			return Component.translatable("kultivator.formasi.keeper.not_owner");
		}
		if (disciple.role() != DiscipleEntity.Role.DISCIPLE || !owner.equals(disciple.ownerId()) || disciple.isReleasing()) {
			return Component.translatable("kultivator.formasi.keeper.not_yours", disciple.getName());
		}
		if (disciple.level() != level || disciple.distanceToSqr(Vec3.atCenterOf(record.eye)) > ASSIGN_RANGE * ASSIGN_RANGE) {
			return Component.translatable("kultivator.formasi.keeper.too_far", disciple.getName(), (int) ASSIGN_RANGE);
		}
		if ((disciple.syncedFlags() & DiscipleEntity.FLAG_BREAKING_THROUGH) != 0 || disciple.departed()
				|| disciple.activity() != DiscipleEntity.ACTIVITY_NONE || record.hasKeeper(disciple.soulId())) {
			return Component.translatable("kultivator.formasi.keeper.busy", disciple.getName());
		}
		DiscipleData data = disciple.data();
		Grade grade = spec.grade();
		if (!data.realm().atLeast(grade.keeperRealm().realm())) {
			return Component.translatable("kultivator.formasi.keeper.realm", disciple.getName(), grade.keeperRealm().realm().displayName());
		}
		if (record.keepers.size() >= grade.maxKeepers()) {
			return Component.translatable("kultivator.formasi.keeper.full", grade.maxKeepers());
		}
		if (!record.keepers.isEmpty()) {
			int need = grade == Grade.ABADI ? SectRanks.INTI : SectRanks.DALAM;
			if (SectRanks.rank(data) < need) {
				return Component.translatable("kultivator.formasi.keeper.rank", disciple.getName(), SectRanks.rankName(need));
			}
		}
		BlockPos post = post(level, disciple, record.eye);
		if (post == null) {
			return Component.translatable("kultivator.formasi.keeper.no_post");
		}
		CompoundTag extra = data.extra().getCompound(EXTRA).orElse(null);
		if (extra != null) {
			releaseRecord(disciple, extra);
		}
		record.keepers.add(new CoreRecord.Keeper(disciple.soulId(), order));
		CompoundTag section = data.extraSection(EXTRA);
		section.putInt("core", record.id);
		section.putString("dim", record.dim.identifier().toString());
		section.putString("order", order.id());
		section.putBoolean("self", true);
		disciple.setActivity(DiscipleEntity.ACTIVITY_FORMATION_KEEPER, post);
		CoreTicker.markDirty(level);
		return null;
	}

	/** True when the disciple keeps a core it chose by itself (DESIGN 39D). */
	public static boolean selfKept(DiscipleEntity disciple) {
		return disciple.activity() == DiscipleEntity.ACTIVITY_FORMATION_KEEPER
				&& disciple.data().extra().getCompoundOrEmpty(EXTRA).getBooleanOr("self", false);
	}

	/** Frees the disciple's keeper post (it walks free; the core loses its keeper). */
	public static void release(DiscipleEntity disciple) {
		CompoundTag extra = disciple.data().extra().getCompound(EXTRA).orElse(null);
		if (extra != null) {
			releaseRecord(disciple, extra);
			disciple.data().extra().remove(EXTRA);
		}
		if (disciple.activity() == DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
			disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	private static void releaseRecord(DiscipleEntity disciple, CompoundTag extra) {
		MinecraftServer server = CoreTicker.server();
		if (server == null) {
			return;
		}
		Identifier id = Identifier.tryParse(extra.getStringOr("dim", ""));
		if (id == null) {
			return;
		}
		ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
		if (level == null) {
			return;
		}
		CoreRecord record = CoreTicker.store(level).byId(extra.getIntOr("core", -1));
		if (record != null && record.keepers.removeIf(keeper -> keeper.soul.equals(disciple.soulId()))) {
			CoreTicker.markDirty(level);
		}
	}

	/** The disciples (soul ids) keeping core {@code coreId}. */
	public static List<UUID> of(ResourceKey<Level> dim, int coreId) {
		CoreRecord record = CoreTicker.record(dim, coreId);
		if (record == null) {
			return List.of();
		}
		List<UUID> list = new ArrayList<>();
		for (CoreRecord.Keeper keeper : record.keepers) {
			list.add(keeper.soul);
		}
		return list;
	}

	/** The order of a disciple keeping a core, or null when it keeps none. */
	public static @Nullable KeeperOrder orderOf(DiscipleEntity disciple) {
		if (disciple.activity() != DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
			return null;
		}
		CompoundTag extra = disciple.data().extra().getCompound(EXTRA).orElse(null);
		return extra == null ? null : KeeperOrder.byId(extra.getStringOr("order", ""));
	}

	/** The core a disciple keeps (same dimension as the disciple), or null. */
	public static @Nullable CoreRecord coreOf(DiscipleEntity disciple) {
		if (!(disciple.level() instanceof ServerLevel level) || disciple.activity() != DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
			return null;
		}
		CompoundTag extra = disciple.data().extra().getCompound(EXTRA).orElse(null);
		if (extra == null || !level.dimension().identifier().toString().equals(extra.getStringOr("dim", ""))) {
			return null;
		}
		return CoreTicker.store(level).byId(extra.getIntOr("core", -1));
	}

	/** A safe post within 2 blocks of the eye (never magma or a lit campfire), or null. */
	public static @Nullable BlockPos post(ServerLevel level, DiscipleEntity disciple, BlockPos eye) {
		int[][] offsets = {{2, 0}, {0, 2}, {-2, 0}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {2, 1}, {1, 2}, {-2, -1}, {-1, -2},
				{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		for (int[] offset : offsets) {
			Vec3 spot = Targeting.safeSpot(level, disciple, Vec3.atBottomCenterOf(eye.offset(offset[0], 0, offset[1])));
			if (spot != null && Math.abs(spot.y - eye.getY()) <= 3.0D) {
				return BlockPos.containing(spot);
			}
		}
		return null;
	}

	/**
	 * Once per second: frees keepers that hold another activity, counts the keepers at their post and caches the keeper
	 * modifiers of every record (24.2.8), then assigns disciples ordered to STAY beside a running eye.
	 */
	public static void refresh(MinecraftServer server) {
		Map<UUID, Map<UUID, DiscipleEntity>> byOwner = new HashMap<>();
		for (CoreStore store : CoreTicker.stores()) {
			ServerLevel level = store.dim() == null ? null : server.getLevel(store.dim());
			if (level == null) {
				continue;
			}
			for (CoreRecord record : store.all()) {
				FormationSpec spec = FormationRegistry.byId(record.spec);
				if (spec == null) {
					continue;
				}
				Map<UUID, DiscipleEntity> loaded = byOwner.computeIfAbsent(record.owner, Keepers::loadedBySoul);
				List<UUID> gone = new ArrayList<>();
				int atPost = 0;
				int jaga = 0;
				double upkeep = 1.0D;
				double k = 0.0D;
				boolean affinity = false;
				boolean dalam = false;
				boolean inti = false;
				for (CoreRecord.Keeper keeper : record.keepers) {
					DiscipleEntity disciple = loaded.get(keeper.soul);
					if (disciple == null) {
						continue;
					}
					if (disciple.activity() != DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
						gone.add(keeper.soul);
						continue;
					}
					BlockPos spot = disciple.activitySpot();
					if (disciple.level() != level || spot == null || disciple.distanceToSqr(Vec3.atBottomCenterOf(spot)) > POST_RANGE * POST_RANGE) {
						continue;
					}
					DiscipleData data = disciple.data();
					int rank = SectRanks.rank(data);
					atPost++;
					double[] mod = modifiers(spec, keeper.order, data.realm(), rank, data.dao(), data.rootElements(), jaga);
					if (keeper.order == KeeperOrder.JAGA) {
						jaga++;
					}
					upkeep *= mod[0];
					k += mod[1];
					affinity |= mod[2] > 0.0D;
					dalam |= rank >= SectRanks.DALAM;
					inti |= rank >= SectRanks.INTI;
				}
				for (CoreRecord.Pretend pretend : record.pretend) {
					atPost++;
					double[] mod = modifiers(spec, pretend.order(), pretend.realm(), pretend.sectRank(), pretend.dao(), 0, jaga);
					if (pretend.order() == KeeperOrder.JAGA) {
						jaga++;
					}
					upkeep *= mod[0];
					k += mod[1];
					affinity |= mod[2] > 0.0D;
					dalam |= pretend.sectRank() >= SectRanks.DALAM;
					inti |= pretend.sectRank() >= SectRanks.INTI;
				}
				if (!gone.isEmpty()) {
					record.keepers.removeIf(keeper -> gone.contains(keeper.soul));
					for (UUID soul : gone) {
						DiscipleEntity disciple = loaded.get(soul);
						if (disciple != null) {
							disciple.data().extra().remove(EXTRA);
						}
					}
					store.setDirty();
				}
				boolean low = spec.grade().ordinal() <= Grade.MISTERIUS.ordinal();
				record.zeroCost = low && jaga > 0;
				record.keepersAtPost = atPost;
				record.keeperUpkeep = record.zeroCost ? 0.0D : atPost == 0 ? 1.0D : Math.max(UPKEEP_FLOOR, upkeep);
				record.keeperK = Math.min(PERKUAT_CAP, k);
				record.keeperAffinity = affinity;
				record.keeperDalam = dalam;
				record.keeperInti = inti;
			}
		}
		autoAssign(server, byOwner);
	}

	/** {upkeep factor, K, affinity (1 or 0)} of one keeper at its post; {@code jagaBefore} Jaga keepers counted before it. */
	private static double[] modifiers(FormationSpec spec, KeeperOrder order, Realm realm, int sectRank, @Nullable Dao dao, int rootMask,
			int jagaBefore) {
		double upkeep = 1.0D;
		double k = 0.0D;
		boolean low = spec.grade().ordinal() <= Grade.MISTERIUS.ordinal();
		switch (order) {
			case JAGA -> upkeep = low ? 0.0D : jagaBefore == 0 ? JAGA_HIGH : JAGA_SECOND;
			case PERKUAT -> k = PERKUAT_PER_REALM * Math.max(0, realm.ordinal() - spec.grade().keeperRealm().realm().ordinal());
			case WASPADA -> upkeep = WASPADA_UPKEEP;
		}
		if (low && order == KeeperOrder.JAGA) {
			upkeep = 1.0D; // zero cost is handled by the record flag
		}
		return new double[]{upkeep, k, Affinity.matches(spec, dao, rootMask, sectRank) ? 1.0D : 0.0D};
	}

	private static Map<UUID, DiscipleEntity> loadedBySoul(UUID owner) {
		Map<UUID, DiscipleEntity> map = new HashMap<>();
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			map.put(disciple.soulId(), disciple);
		}
		return map;
	}

	/** Disciples ordered to STAY within 3 blocks of a running eye of their master with a free keeper slot become Jaga keepers. */
	private static void autoAssign(MinecraftServer server, Map<UUID, Map<UUID, DiscipleEntity>> byOwner) {
		for (Map.Entry<UUID, Map<UUID, DiscipleEntity>> entry : byOwner.entrySet()) {
			ServerPlayer master = server.getPlayerList().getPlayer(entry.getKey());
			if (master == null) {
				continue;
			}
			for (DiscipleEntity disciple : entry.getValue().values()) {
				if (disciple.mode() != DiscipleEntity.Mode.STAY || disciple.activity() != DiscipleEntity.ACTIVITY_NONE || disciple.post() == null
						|| !(disciple.level() instanceof ServerLevel level)) {
					continue;
				}
				for (CoreRecord record : CoreTicker.store(level).all()) {
					FormationSpec spec = FormationRegistry.byId(record.spec);
					if (spec == null || !record.owner.equals(master.getUUID()) || !record.state.running()
							|| record.keepers.size() >= spec.grade().maxKeepers()
							|| Vec3.atCenterOf(record.eye).distanceToSqr(Vec3.atCenterOf(disciple.post())) > (AUTO_RANGE + 0.5D) * (AUTO_RANGE + 0.5D)) {
						continue;
					}
					Component why = assign(master, level, record, disciple, KeeperOrder.JAGA, false);
					if (why == null) {
						dev.kultivator.util.Msg.chat(master, Component.translatable("kultivator.formasi.keeper.auto", disciple.getName(), spec.name()));
					}
					break;
				}
			}
		}
	}

	private Keepers() {
	}
}
