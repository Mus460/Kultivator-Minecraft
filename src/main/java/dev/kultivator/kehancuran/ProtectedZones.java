package dev.kultivator.kehancuran;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where Mode Kehancuran may never change a block, and which blocks it never touches (DESIGN 20.3.21).
 * <p>
 * Protected zones (a cast whose shape touches one is refused; positions inside one are always skipped): within
 * {@code kehancuranHallSafeRadius} horizontal blocks of any Sect Hall stele, within {@code kehancuranPlayerSafeRadius}
 * of the master or any player, the world spawn protection (at least 16), within 32 blocks of a villager, an iron golem
 * or a registered protective formation lodestone, within 6 blocks of a disciple, a tamed pet or a spirit beast (so none
 * falls into a pit), the End within 160 blocks of (0, 0), and unloaded chunks. The hall list holds every hall of
 * {@code HallData} (online or not), every position the log remembers and every stele ever placed.
 * <p>
 * Never touched: bedrock and anything with hardness below 0, every block with a block entity, portals and their frames,
 * obsidian, crying obsidian, reinforced deepslate, lodestone, respawn anchor, the Sect Hall stele, fluids and
 * waterlogged blocks, and the datapack tag {@code #kultivator:indestructible}.
 */
public final class ProtectedZones {

	public static final double VILLAGER_RADIUS = 32.0D;
	public static final double FORMATION_RADIUS = 32.0D;
	public static final double END_ISLAND_RADIUS = 160.0D;
	public static final double COMPANION_RADIUS = 6.0D;
	public static final int MIN_SPAWN_RADIUS = 16;
	public static final TagKey<Block> INDESTRUCTIBLE = TagKey.create(Registries.BLOCK, Kultivator.id("indestructible"));
	private static final Identifier STELE = Kultivator.id("sect_hall_stele");

	/**
	 * An extra protected area (phase 6b: within 16 blocks of any confirmed sect footprint or showcase,
	 * {@code SectBuild.insideAnySite(level, pos, 16)}, DESIGN 21.17). Counted as the hall zone. Called for many positions
	 * per tick, so it must be cheap.
	 */
	@FunctionalInterface
	public interface ZoneProvider {
		boolean protects(ServerLevel level, BlockPos pos);
	}

	private static final List<ZoneProvider> ZONE_PROVIDERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** Adds a protected area provider (DESIGN 21.24); with none the zones are exactly those listed above. */
	public static void addZoneProvider(ZoneProvider provider) {
		if (provider != null) {
			ZONE_PROVIDERS.add(provider);
		}
	}

	/** True when a provider protects {@code pos} (a failing provider protects, to stay safe). */
	private static boolean provided(ServerLevel level, BlockPos pos) {
		for (ZoneProvider provider : ZONE_PROVIDERS) {
			try {
				if (provider.protects(level, pos)) {
					return true;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Mode Kehancuran zone provider failed", exception);
				return true;
			}
		}
		return false;
	}

	/** Why a position is protected (a translation key suffix), for the refusal message. */
	public enum Zone {
		HALL("hall"),
		PLAYER("player"),
		SPAWN("spawn"),
		VILLAGER("villager"),
		COMPANION("companion"),
		FORMATION("formation"),
		END_ISLAND("end"),
		UNLOADED("unloaded"),
		/** The caster's own safe column (DESIGN 22.11.6). */
		CASTER("kultivator.kehancuran_diri.zone.caster", true),
		/** Around a known respawn point (DESIGN 22.11.6). */
		HOME("kultivator.kehancuran_diri.zone.home", true);

		private final String key;

		Zone(String id) {
			this.key = "kultivator.kehancuran.zone." + id;
		}

		Zone(String key, boolean full) {
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	/**
	 * The zones of one level gathered once (halls, players, spawn, villagers and golems around an area, formations), so
	 * thousands of positions can be checked cheaply.
	 */
	public static final class Context {
		final ServerLevel level;
		final List<BlockPos> halls = new ArrayList<>();
		final List<Vec3> players = new ArrayList<>();
		final List<Vec3> villagers = new ArrayList<>();
		final List<Vec3> companions = new ArrayList<>();
		final List<BlockPos> formations = new ArrayList<>();
		final @Nullable BlockPos spawn;
		final int spawnRadius;
		final boolean end;
		final double hallSafe;
		final double playerSafe;
		/** The caster's safe columns (a self cast: at the cast and where the caster stands now), full world height. */
		final List<Vec3> columns = new ArrayList<>();
		final double columnSafe;
		/** Known respawn points of every player in this level (22.11.6). */
		final List<BlockPos> homes;
		final double homeSafe;

		/** {@code area}: the box the positions lie in (villagers and golems are looked for around it). */
		public Context(ServerLevel level, AABB area) {
			this(level, area, null, List.of());
		}

		/**
		 * A self cast's zones (DESIGN 22.11.6): the {@code caster} is exempt from the player zone and protected instead by a
		 * vertical cylinder of {@code kehancuranCasterSafeRadius} around each of {@code casterColumns}.
		 */
		public Context(ServerLevel level, AABB area, java.util.@Nullable UUID caster, List<Vec3> casterColumns) {
			this.level = level;
			KultivatorConfig config = KultivatorConfig.get();
			this.hallSafe = config.kehancuranHallSafeRadius;
			this.playerSafe = config.kehancuranPlayerSafeRadius;
			this.columnSafe = config.kehancuranCasterSafeRadius;
			this.homeSafe = config.kehancuranHomeSafeRadius;
			this.columns.addAll(casterColumns);
			this.homes = homeSafe > 0.0D ? dev.kultivator.technique.spec.run.Homes.in(level) : List.of();
			for (GlobalPos hall : KehancuranLog.knownHalls(level.getServer())) {
				if (hall.dimension().equals(level.dimension())) {
					halls.add(hall.pos());
				}
			}
			for (ServerPlayer player : level.players()) {
				if (caster != null && caster.equals(player.getUUID())) {
					continue;
				}
				players.add(player.position());
			}
			AABB around = area.inflate(VILLAGER_RADIUS);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around, entity -> entity instanceof AbstractVillager
					|| entity instanceof IronGolem || companion(entity))) {
				if (entity instanceof AbstractVillager || entity instanceof IronGolem) {
					villagers.add(entity.position());
				} else {
					companions.add(entity.position());
				}
			}
			for (Formation formation : FormationData.get(level).all()) {
				formations.add(formation.pos());
			}
			GlobalPos spawnPos = level.getServer().overworld().getRespawnData().globalPos();
			this.spawn = spawnPos.dimension().equals(level.dimension()) ? spawnPos.pos() : null;
			int radius = MIN_SPAWN_RADIUS;
			if (level.getServer() instanceof DedicatedServer dedicated) {
				radius = Math.max(MIN_SPAWN_RADIUS, dedicated.spawnProtectionRadius());
			}
			this.spawnRadius = radius;
			this.end = level.dimension().equals(Level.END);
		}

		/** The zone {@code pos} lies in, or null when it is free. */
		public @Nullable Zone zoneOf(BlockPos pos) {
			if (!level.isLoaded(pos)) {
				return Zone.UNLOADED;
			}
			double x = pos.getX() + 0.5D;
			double y = pos.getY() + 0.5D;
			double z = pos.getZ() + 0.5D;
			for (BlockPos hall : halls) {
				double dx = x - (hall.getX() + 0.5D);
				double dz = z - (hall.getZ() + 0.5D);
				if (dx * dx + dz * dz <= hallSafe * hallSafe) {
					return Zone.HALL;
				}
			}
			if (provided(level, pos)) {
				return Zone.HALL;
			}
			for (Vec3 player : players) {
				if (player.distanceToSqr(x, y, z) <= playerSafe * playerSafe) {
					return Zone.PLAYER;
				}
			}
			for (Vec3 column : columns) {
				double dx = x - column.x;
				double dz = z - column.z;
				if (dx * dx + dz * dz <= (columnSafe + 0.5D) * (columnSafe + 0.5D)) {
					return Zone.CASTER;
				}
			}
			for (BlockPos home : homes) {
				if (home.distToCenterSqr(x, y, z) <= homeSafe * homeSafe) {
					return Zone.HOME;
				}
			}
			if (spawn != null) {
				double dx = x - (spawn.getX() + 0.5D);
				double dz = z - (spawn.getZ() + 0.5D);
				if (Math.max(Math.abs(dx), Math.abs(dz)) <= spawnRadius) {
					return Zone.SPAWN;
				}
			}
			for (Vec3 villager : villagers) {
				if (villager.distanceToSqr(x, y, z) <= VILLAGER_RADIUS * VILLAGER_RADIUS) {
					return Zone.VILLAGER;
				}
			}
			for (Vec3 companion : companions) {
				if (companion.distanceToSqr(x, y, z) <= COMPANION_RADIUS * COMPANION_RADIUS) {
					return Zone.COMPANION;
				}
			}
			for (BlockPos lodestone : formations) {
				if (lodestone.distToCenterSqr(x, y, z) <= FORMATION_RADIUS * FORMATION_RADIUS) {
					return Zone.FORMATION;
				}
			}
			if (end && x * x + z * z <= END_ISLAND_RADIUS * END_ISLAND_RADIUS) {
				return Zone.END_ISLAND;
			}
			return null;
		}
	}

	/** Disciples (the caster too), tamed pets and spirit beasts: pets and disciples are never hurt (DESIGN 20.3.21). */
	private static boolean companion(LivingEntity entity) {
		return entity.isAlive() && (entity instanceof DiscipleEntity || entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null
				|| SpiritBeasts.isSpiritBeast(entity));
	}

	/**
	 * True when no destructive shape may touch {@code pos} (one-off check; casts use a {@link Context}); the zone providers
	 * (sect sites) are consulted too.
	 */
	public static boolean blocked(ServerLevel level, BlockPos pos) {
		return new Context(level, new AABB(pos)).zoneOf(pos) != null;
	}

	/** True for a block Mode Kehancuran never touches (whatever the zone). Air counts as untouchable too. */
	public static boolean neverTouch(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return true;
		}
		if (state.getDestroySpeed(level, pos) < 0.0F || state.is(Blocks.BEDROCK)) {
			return true;
		}
		if (state.is(BlockTags.PORTALS) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)
				|| state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
				|| state.is(Blocks.REINFORCED_DEEPSLATE) || state.is(Blocks.LODESTONE) || state.is(Blocks.RESPAWN_ANCHOR)) {
			return true;
		}
		if (state.is(INDESTRUCTIBLE)) {
			return true;
		}
		return STELE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	/** Remembers where a hall is (any hall, active or not) whenever one is seen: the zone list survives restarts. */
	public static void noteHall(@Nullable Halls.HallView hall, java.util.UUID owner, net.minecraft.server.MinecraftServer server) {
		if (hall != null && hall.pos() != null && hall.dim() != null) {
			KehancuranLog.get(server).noteHall(owner, GlobalPos.of(hall.dim(), hall.pos()));
		}
	}

	/** A Sect Hall stele was placed (called by {@code HallBlock}): its hall zone holds while it stands, even when inert. */
	public static void noteStele(ServerLevel level, BlockPos pos) {
		KehancuranLog.get(level.getServer()).noteStele(GlobalPos.of(level.dimension(), pos.immutable()));
	}

	/** A Sect Hall stele was removed (called by {@code HallBlock}). */
	public static void forgetStele(ServerLevel level, BlockPos pos) {
		KehancuranLog.get(level.getServer()).forgetStele(GlobalPos.of(level.dimension(), pos.immutable()));
	}

	private ProtectedZones() {
	}
}
