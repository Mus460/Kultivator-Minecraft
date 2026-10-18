package dev.kultivator.formasi.effect.b;

import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formasi.FormasiB;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.keeper.Keepers;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.hall.GrandFormation;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Small read-only helpers shared by module B's formation handlers (DESIGN 24.4.7-24.4.11). */
public final class BUtil {

	/** Ticks within which "the target hurt that mob" still counts (concealment and maze gates, 24.2.10). */
	public static final int RECENT_HURT_TICKS = 60;
	/** The eight compass words (north first, clockwise) used in alarm texts. */
	private static final String[] COMPASS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	/** Map key of one core across dimensions. */
	public record CoreKey(ResourceKey<Level> dim, int id) {
		public static CoreKey of(CoreView core) {
			return new CoreKey(core.dim(), core.id());
		}
	}

	/** A card offset turned {@code rot} clockwise quarter turns, as {@code Pattern} does: (dx, dz) → (−dz, dx). */
	public static int[] rotate(int dx, int dz, int rot) {
		int x = dx;
		int z = dz;
		for (int i = 0; i < Math.floorMod(rot, 4); i++) {
			int turned = -z;
			z = x;
			x = turned;
		}
		return new int[]{x, z};
	}

	/** The centre of the eye block of {@code core}. */
	public static Vec3 centre(CoreView core) {
		return Vec3.atCenterOf(core.eye());
	}

	public static @Nullable FormationSpec spec(CoreView core) {
		return core == null ? null : FormationRegistry.byId(core.spec());
	}

	/** The card radius of {@code core} at its owner's hall level plus the A1 bonus, capped at {@code RADIUS_CAP}. */
	public static double radius(CoreView core) {
		FormationSpec spec = spec(core);
		if (spec == null) {
			return 0.0D;
		}
		double r = spec.radiusAt(Math.max(1, Halls.level(core.owner())));
		if (FormasiB.grandComplete(core.owner()) && !GrandArrayEffect.A1.equals(core.spec())) {
			r += 2.0D;
		}
		return Math.min(Stacking.RADIUS_CAP, r);
	}

	/** True when {@code other} is in {@code owner}'s {@code /sect} (works with the owner offline). */
	public static boolean mates(MinecraftServer server, UUID owner, UUID other) {
		if (server == null || owner == null || other == null || owner.equals(other)) {
			return false;
		}
		Sect sect = SectData.get(server).sectOf(owner);
		return sect != null && sect.isMember(other);
	}

	/** The owner, the owner's sect mates, the owner's disciples and the pets of any of them (24.2.6). */
	public static boolean member(MinecraftServer server, UUID owner, @Nullable Entity entity) {
		if (entity == null || owner == null) {
			return false;
		}
		if (entity instanceof ServerPlayer player) {
			return !player.isSpectator() && (player.getUUID().equals(owner) || mates(server, owner, player.getUUID()));
		}
		if (entity instanceof DiscipleEntity disciple) {
			return owner.equals(disciple.ownerId());
		}
		if (entity instanceof OwnableEntity ownable) {
			EntityReference<LivingEntity> reference = ownable.getOwnerReference();
			UUID petOwner = reference == null ? null : reference.getUUID();
			return petOwner != null && (petOwner.equals(owner) || mates(server, owner, petOwner));
		}
		return false;
	}

	/** True for entities a concealment or maze gate may shield at all (players, disciples, tamed pets). */
	public static boolean shieldable(@Nullable LivingEntity entity) {
		return entity instanceof ServerPlayer || entity instanceof DiscipleEntity
				|| entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null;
	}

	/** Every online player who is the owner or in the owner's sect. */
	public static List<ServerPlayer> onlineMembers(MinecraftServer server, UUID owner) {
		List<ServerPlayer> out = new ArrayList<>();
		if (server == null) {
			return out;
		}
		ServerPlayer self = server.getPlayerList().getPlayer(owner);
		if (self != null) {
			out.add(self);
		}
		Sect sect = SectData.get(server).sectOf(owner);
		if (sect != null) {
			for (ServerPlayer mate : Sects.onlineMembers(server, sect)) {
				if (!out.contains(mate)) {
					out.add(mate);
				}
			}
		}
		return out;
	}

	/** True when {@code target} hurt {@code mob} within the last 60 ticks. */
	public static boolean recentlyHurtBy(Mob mob, LivingEntity target) {
		return mob.getLastHurtByMob() == target && mob.tickCount - mob.getLastHurtByMobTimestamp() < RECENT_HURT_TICKS;
	}

	/** Wither, Ender Dragon, Warden, Elder Guardian or a mod boss. */
	public static boolean boss(Entity entity) {
		return entity instanceof WitherBoss || entity instanceof EnderDragon || entity instanceof Warden || entity instanceof ElderGuardian
				|| entity.entityTags().contains(TechniquePower.TAG_BOSS);
	}

	public static boolean protector(Entity entity) {
		return DaoProtectors.isProtector(entity);
	}

	/** The loaded disciples keeping {@code core}. */
	public static List<DiscipleEntity> keepers(CoreView core) {
		List<UUID> souls = Keepers.of(core.dim(), core.id());
		if (souls.isEmpty()) {
			return List.of();
		}
		List<DiscipleEntity> out = new ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(core.owner())) {
			if (souls.contains(disciple.soulId())) {
				out.add(disciple);
			}
		}
		return out;
	}

	/** True when a keeper of {@code core} has at least sect rank {@code rank} ({@code SectRanks.DALAM}, {@code INTI}). */
	public static boolean keeperRank(CoreView core, int rank) {
		for (DiscipleEntity disciple : keepers(core)) {
			if (SectRanks.rank(disciple.data()) >= rank) {
				return true;
			}
		}
		return false;
	}

	/** True when a keeper of {@code core} follows one of {@code daos}. */
	public static boolean keeperDao(CoreView core, Dao... daos) {
		for (DiscipleEntity disciple : keepers(core)) {
			Dao dao = disciple.data().dao();
			for (Dao wanted : daos) {
				if (dao == wanted) {
					return true;
				}
			}
		}
		return false;
	}

	/** The compass word (n, ne, e ... nw) of the direction (dx, dz) from an eye; north is −z. */
	public static String compass(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int index = (int) Math.floor(Math.floorMod(Math.round(angle + 22.5D), 360) / 45.0D) % 8;
		return COMPASS[index];
	}

	/** The translated compass direction. */
	public static Component direction(double dx, double dz) {
		return Component.translatable("kultivator.formasib.dir." + compass(dx, dz));
	}

	/** "Zombie ×2, Skeleton (Elite)": mob types counted, at most 3 groups. */
	public static Component mobList(List<Mob> mobs) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, Component> names = new LinkedHashMap<>();
		for (Mob mob : mobs) {
			boolean elite = EliteMonsters.isElite(mob);
			String key = mob.getType().getDescriptionId() + (elite ? "#e" : "");
			counts.computeIfAbsent(key, k -> new int[1])[0]++;
			if (!names.containsKey(key)) {
				MutableComponent name = mob.getType().getDescription().copy();
				if (protector(mob)) {
					name = Component.translatable("kultivator.formasib.alarm.protector");
				} else if (elite) {
					name = Component.translatable("kultivator.formasib.alarm.elite", name);
				}
				names.put(key, name);
			}
		}
		MutableComponent out = Component.empty();
		int shown = 0;
		for (Map.Entry<String, int[]> entry : counts.entrySet()) {
			if (shown == 3) {
				out.append(Component.translatable("kultivator.formasib.alarm.more", counts.size() - 3));
				break;
			}
			if (shown > 0) {
				out.append(", ");
			}
			out.append(names.get(entry.getKey()));
			if (entry.getValue()[0] > 1) {
				out.append(" ×" + entry.getValue()[0]);
			}
			shown++;
		}
		return out;
	}

	/** The spec ids of {@code owner}'s running cores (distinct). */
	public static Set<String> runningSpecs(UUID owner) {
		Set<String> out = new HashSet<>();
		for (CoreView core : dev.kultivator.formasi.core.Cores.of(owner)) {
			if (core.state().running()) {
				out.add(core.spec());
			}
		}
		return out;
	}

	/**
	 * The restriction layers of {@code owner}'s active Formasi Agung Sekte inside the hall territory, or −1 when it has
	 * none running (read-only, through {@code GrandFormation.state}).
	 */
	public static int agungLayers(MinecraftServer server, UUID owner) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (server == null || hall == null || !hall.active()) {
			return -1;
		}
		ServerLevel level = server.getLevel(hall.dim());
		if (level == null) {
			return -1;
		}
		int best = -1;
		int tick = server.getTickCount();
		for (Formation formation : FormationData.get(level).all()) {
			if (!owner.equals(formation.owner()) || !formation.active() || formation.power() <= 0) {
				continue;
			}
			BlockPos pos = formation.pos();
			if (!level.isLoaded(pos) || !Halls.inTerritory(owner, level, Vec3.atCenterOf(pos))) {
				continue;
			}
			GrandFormation.State state = GrandFormation.state(level, formation, tick);
			if (state.grand()) {
				best = Math.max(best, state.layers());
			}
		}
		return best;
	}

	/** The phase-7c section of a player's saved data ({@code CultivationData.extra().formasi}; B writes trips and strayed). */
	public static CompoundTag formasiData(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag extra = data.extra();
		CompoundTag section = extra.getCompoundOrEmpty("formasi");
		if (!extra.contains("formasi")) {
			extra.put("formasi", section);
		}
		return section;
	}

	/** Adds {@code amount} to a counter in {@link #formasiData}; returns the new value. */
	public static int addCount(ServerPlayer player, String key, int amount) {
		CompoundTag section = formasiData(player);
		int value = section.getIntOr(key, 0) + amount;
		section.putInt(key, value);
		Cultivators.dirty(player);
		return value;
	}

	private BUtil() {
	}
}
