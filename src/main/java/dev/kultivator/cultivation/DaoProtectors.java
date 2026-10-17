package dev.kultivator.cultivation;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Dao Protector event (Pelindung Dao): monsters drawn by a tribulation's phenomenon. Waves spawn on valid ground
 * 10-16 blocks from the cultivator (read-only checks, never inside blocks, nothing is changed). They carry the tag
 * {@link #TAG} plus an owner tag, and vanish with a poof when the tribulation ends (no drops). Leftovers from a crash or
 * restart are refused when their chunk loads again. Every protector is hardened so it cannot change blocks (no bows, no
 * door breaking, no raids, no loot pickup) and it can only hurt players.
 */
public final class DaoProtectors {

	/** Every Dao Protector carries this entity tag. */
	public static final String TAG = "kultivator.dao_protector";
	private static final String OWNER_TAG_PREFIX = "kultivator.dao_protector.";
	/** A wave raised for a non-player target (spawnWaveFor) carries this plus the target's UUID: it may hurt that target. */
	private static final String VERSUS_TAG_PREFIX = "kultivator.dao_protector_vs.";
	private static final Identifier HEALTH_ID = Kultivator.id("dao_protector_health");
	private static final int TRIES_PER_MOB = 20;

	/** Owner UUID -> the protectors spawned for them. */
	private static final Map<UUID, Set<UUID>> BY_OWNER = new HashMap<>();
	/** All protectors spawned in this server session (only these may load from disk). */
	private static final Set<UUID> ALIVE = new HashSet<>();

	public static void init() {
		// Survivors of a crash or restart are not allowed back into the world.
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !entity.entityTags().contains(TAG) || ALIVE.contains(entity.getUUID()));
		// A protector that turns into another mob (magma cube splits, zombie -> drowned, skeleton -> stray) keeps its tags
		// but gets a new UUID: track and harden the new one too, so it vanishes with the rest.
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) -> {
			if (!isProtector(previous)) {
				return;
			}
			for (String tag : previous.entityTags()) {
				if (tag.startsWith(OWNER_TAG_PREFIX)) {
					try {
						UUID owner = UUID.fromString(tag.substring(OWNER_TAG_PREFIX.length()));
						BY_OWNER.computeIfAbsent(owner, id -> new HashSet<>()).add(converted.getUUID());
						ALIVE.add(converted.getUUID());
					} catch (IllegalArgumentException ignored) {
						// Not an owner tag after all.
					}
				}
			}
			harden(converted);
		});
		// The phenomenon only hunts players: protectors never hurt villagers, pets, golems or each other (this also
		// keeps a killed villager from turning into a zombie villager that would stay behind).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			Entity attacker = source.getEntity();
			return attacker == null || !isProtector(attacker) || victim instanceof Player
					|| attacker.entityTags().contains(VERSUS_TAG_PREFIX + victim.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BY_OWNER.clear();
			ALIVE.clear();
		});
	}

	/** True for an entity that belongs to a Dao Protector wave. */
	public static boolean isProtector(Entity entity) {
		return entity.entityTags().contains(TAG);
	}

	/** Spawns one wave of {@code 1 + targetRealm / 2} monsters around the player. Returns how many arrived. */
	public static int spawnWave(ServerPlayer player, Rank target, int variant) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		int count = 1 + target.realm().ordinal() / 2;
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			EntityType<? extends Mob> type = pick(variant, target, random);
			if (spawnOne(level, player, type, variant, random, 1.0D)) {
				spawned++;
			}
		}
		if (spawned > 0) {
			Msg.chat(player, Component.translatable("kultivator.breakthrough.protectors.wave", spawned).withStyle(ChatFormatting.DARK_RED));
		}
		return spawned;
	}

	/**
	 * A wave of {@code size} protectors for any living {@code target} (DESIGN 20.2.9: a disciple's tribulation or
	 * calamity, the promotion trial): the same monster pool (by the level's dimension and {@code rank}), hardening, tags
	 * and cleanup as {@link #spawnWave}, health times {@code healthFactor}, aimed at {@code target} first. Unlike player
	 * waves these protectors may also hurt {@code target}. Tracked under the target: {@link #clearFor} removes them.
	 * {@code owner} is the target's master (may be null); no chat line is sent, the caller words its own. Returns how many
	 * arrived.
	 */
	public static int spawnWaveFor(ServerLevel level, LivingEntity target, @Nullable ServerPlayer owner, Rank rank, int size, double healthFactor) {
		if (target == null || !target.isAlive() || target.level() != level || size <= 0) {
			return 0;
		}
		RandomSource random = level.getRandom();
		int variant = Tribulation.variant(level);
		double health = Double.isFinite(healthFactor) ? Math.max(0.1D, healthFactor) : 1.0D;
		int spawned = 0;
		for (int i = 0; i < Math.min(size, 16); i++) {
			EntityType<? extends Mob> type = pick(variant, rank, random);
			if (spawnOne(level, target, type, variant, random, health)) {
				spawned++;
			}
		}
		return spawned;
	}

	/** True when {@code protector} belongs to a wave raised for {@code target} (spawnWave or spawnWaveFor). */
	public static boolean isWaveOf(Entity protector, Entity target) {
		return protector != null && target != null && isProtector(protector) && protector.entityTags().contains(OWNER_TAG_PREFIX + target.getUUID());
	}

	/** Removes every protector raised for {@code target} with a poof (no drops). */
	public static void clearFor(LivingEntity target) {
		if (target != null && target.level() instanceof ServerLevel level) {
			clear(level, target.getUUID());
		}
	}

	/** Removes every protector of the player with a poof (no drops). */
	public static void clear(ServerPlayer player) {
		clear(player.level(), player.getUUID());
	}

	private static void clear(ServerLevel level, UUID key) {
		Set<UUID> ids = BY_OWNER.remove(key);
		if (ids != null) {
			for (UUID id : ids) {
				ALIVE.remove(id);
				Entity entity = findEntity(level, id);
				if (entity != null && entity.isAlive()) {
					vanish(entity);
				}
			}
		}
		// Anything else carrying the owner tag (converted or split mobs, wherever they wandered): collect first, then
		// discard, so the entity lists are not changed while they are walked.
		String ownerTag = OWNER_TAG_PREFIX + key;
		List<Entity> leftovers = new ArrayList<>();
		for (ServerLevel each : level.getServer().getAllLevels()) {
			for (Entity entity : each.getAllEntities()) {
				if (entity.entityTags().contains(ownerTag)) {
					leftovers.add(entity);
				}
			}
		}
		for (Entity entity : leftovers) {
			ALIVE.remove(entity.getUUID());
			if (entity.isAlive()) {
				vanish(entity);
			}
		}
	}

	/** Protectors currently tracked for the player. */
	public static int count(ServerPlayer player) {
		Set<UUID> ids = BY_OWNER.get(player.getUUID());
		return ids == null ? 0 : ids.size();
	}

	// ================================================================== internals

	private static EntityType<? extends Mob> pick(int variant, Rank target, RandomSource random) {
		List<EntityType<? extends Mob>> pool = new ArrayList<>();
		switch (variant) {
			case Tribulation.VARIANT_NETHER -> {
				pool.add(EntityTypes.WITHER_SKELETON);
				pool.add(EntityTypes.MAGMA_CUBE);
				// Hoglins instead of piglin brutes: brutes open and close doors (a block change).
				pool.add(EntityTypes.HOGLIN);
			}
			case Tribulation.VARIANT_END -> {
				pool.add(EntityTypes.ENDERMITE);
				pool.add(EntityTypes.PHANTOM);
			}
			default -> {
				pool.add(EntityTypes.SKELETON);
				pool.add(EntityTypes.SPIDER);
				pool.add(EntityTypes.ZOMBIE);
				pool.add(EntityTypes.STRAY);
				// Husks instead of witches: splash potions are vanilla projectiles and poison bystanders.
				pool.add(EntityTypes.HUSK);
				if (target.realm().atLeast(Realm.DEITY_TRANSFORMATION)) {
					pool.add(EntityTypes.VINDICATOR);
				}
			}
		}
		return pool.get(random.nextInt(pool.size()));
	}

	private static boolean spawnOne(ServerLevel level, LivingEntity player, EntityType<? extends Mob> type, int variant, RandomSource random,
			double healthFactor) {
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			// Peaceful difficulty or a disabled feature.
			return false;
		}
		Vec3 spot = findSpot(level, player, mob, random);
		if (spot == null) {
			return false;
		}
		if (mob instanceof Phantom) {
			Vec3 high = spot.add(0.0D, 4.0D, 0.0D);
			AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(high);
			// Portals have no collision, so check for them too: a phantom must never start inside one.
			if (level.noCollision(mob, box) && level.getBlockStates(box.inflate(0.01D))
					.noneMatch(state -> state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY))) {
				spot = high;
			}
		}
		mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
		// Zombies never become chicken jockeys here (that could grab someone's chicken).
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spot)), EntitySpawnReason.EVENT,
				mob instanceof Zombie ? new Zombie.ZombieGroupData(Zombie.getSpawnAsBabyOdds(random), false) : null);
		if (mob.isPassenger()) {
			mob.stopRiding();
		}
		String ownerTag = ownerTag(player);
		Set<UUID> owned = BY_OWNER.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
		// The mob itself plus a jockey rider that finalizeSpawn may have created (spider jockeys).
		List<Entity> group = new ArrayList<>();
		mob.getSelfAndPassengers().forEach(group::add);
		for (Entity member : group) {
			member.addTag(TAG);
			member.addTag(ownerTag);
			if (!(player instanceof Player)) {
				member.addTag(VERSUS_TAG_PREFIX + player.getUUID());
			}
			if (member instanceof Mob each) {
				harden(each);
				strengthen(each, healthFactor);
			}
			owned.add(member.getUUID());
			ALIVE.add(member.getUUID());
		}
		if (!mob.isAlive()) {
			return false;
		}
		if (!level.tryAddFreshEntityWithPassengers(mob)) {
			for (Entity member : group) {
				owned.remove(member.getUUID());
				ALIVE.remove(member.getUUID());
			}
			return false;
		}
		for (Entity member : group) {
			if (member instanceof Mob each) {
				aim(each, player);
			}
		}
		Fx.send(level, spot, FxType.DAO_PROTECTOR, variant, 0, 0.0F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8F, 0.7F);
		return true;
	}

	/** A free standing spot 10-16 blocks from the target, near its height, inside loaded chunks. Read-only. */
	private static @Nullable Vec3 findSpot(ServerLevel level, LivingEntity player, Mob mob, RandomSource random) {
		Vec3 center = player.position();
		for (int attempt = 0; attempt < TRIES_PER_MOB; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = 10.0D + random.nextDouble() * 6.0D;
			Vec3 candidate = new Vec3(center.x + Math.cos(angle) * distance, center.y, center.z + Math.sin(angle) * distance);
			if (!level.isLoaded(BlockPos.containing(candidate))) {
				continue;
			}
			Vec3 spot = Targeting.safeSpot(level, mob, candidate);
			if (spot == null) {
				continue;
			}
			double dx = spot.x - center.x;
			double dz = spot.z - center.z;
			double horizontal = dx * dx + dz * dz;
			if (horizontal < 81.0D || horizontal > 289.0D || fragileGround(level, BlockPos.containing(spot))) {
				continue;
			}
			return spot;
		}
		return null;
	}

	/** Farmland and turtle eggs could be trampled by a mob landing there: never spawn on or in them. */
	private static boolean fragileGround(ServerLevel level, BlockPos feet) {
		for (BlockPos pos : new BlockPos[]{feet, feet.below()}) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.FARMLAND) || state.is(Blocks.TURTLE_EGG)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Takes away every way a protector could change blocks or keep items: no loot pickup (a discarded mob would destroy
	 * what it picked up), swords instead of bows (arrows break pots and light TNT), zombies never break doors, call no
	 * reinforcements and leave turtle eggs alone, raiders never join a raid (raid goals break and open doors).
	 */
	private static void harden(Mob mob) {
		mob.setCanPickUpLoot(false);
		if (mob instanceof AbstractSkeleton skeleton) {
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
			skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			skeleton.reassessWeaponGoal();
		}
		if (mob instanceof Zombie zombie) {
			zombie.setCanBreakDoors(false);
			AttributeInstance reinforcements = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
			if (reinforcements != null) {
				reinforcements.removeModifiers();
				reinforcements.setBaseValue(0.0D);
			}
			zombie.getGoalSelector().removeAllGoals(goal -> goal instanceof RemoveBlockGoal);
		}
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
		}
	}

	private static void aim(Mob mob, LivingEntity player) {
		mob.setTarget(player);
		if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
			mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
		}
	}

	/** More health for a stronger wave ({@code factor} 1 changes nothing); the mob starts at full health. */
	private static void strengthen(Mob mob, double factor) {
		if (factor == 1.0D) {
			return;
		}
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, factor - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static void vanish(Entity entity) {
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.DAO_PROTECTOR, 0, 1, 0.0F);
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.4F, 1.4F);
		}
		// Discarding never drops loot or experience.
		entity.discard();
	}

	private static @Nullable Entity findEntity(ServerLevel level, UUID id) {
		Entity entity = level.getEntity(id);
		if (entity != null) {
			return entity;
		}
		for (ServerLevel other : level.getServer().getAllLevels()) {
			Entity found = other.getEntity(id);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static String ownerTag(Entity target) {
		return OWNER_TAG_PREFIX + target.getUUID();
	}

	private DaoProtectors() {
	}
}
