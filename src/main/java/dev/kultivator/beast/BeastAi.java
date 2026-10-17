package dev.kultivator.beast;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.util.Targeting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Behaviour of spirit beasts: who they may bite, spirit foxes following and defending their owner, and the small
 * changes that keep an awakened fox from touching blocks.
 * <p>
 * Wolves follow, teleport and defend through vanilla (they are tamed). Foxes have no owner in vanilla, only a list of
 * trusted players (a fox only becomes a spirit beast for a player it already trusts), so the module puts the owner
 * first in that list (the way {@code /data merge entity} edits an entity) and drives following itself. A vanilla fox
 * also picks ripe sweet berries and glow berries, which changes the bush / vine block: that goal is removed from
 * awakened foxes, and so are their chicken / rabbit / fish / baby turtle hunting goals (beasts never hurt passive
 * animals) and the goals that keep it from following (sleeping all day under a roof, seeking shelter, sitting down at
 * random). Both goal lists are protected fields of {@code Mob}; Minecraft 26.3 runs with its own (unobfuscated) names,
 * so they are reached by name. If that ever fails, the fox keeps its vanilla goals and a warning is logged once (berry
 * picking is then vanilla behaviour the NoGrief tag cannot stop).
 */
final class BeastAi {

	/** Foxes walk to their owner when farther than this (blocks). */
	static final double FOLLOW_DISTANCE = 6.0D;
	/** ... and teleport next to them when farther than this, in the same dimension. */
	static final double TELEPORT_DISTANCE = 24.0D;
	/** A fox busy fighting still comes back when its owner is this far away. */
	static final double LEASH_DISTANCE = 40.0D;
	/** Foxes within this distance of the owner join the owner's fights. */
	static final double DEFEND_DISTANCE = 24.0D;
	private static final double FOLLOW_SPEED = 1.3D;

	/** Results of {@link #trust}. */
	static final int TRUST_FAILED = -1;
	static final int TRUST_ALREADY = 0;
	static final int TRUST_NEW = 1;

	private static final Codec<List<EntityReference<LivingEntity>>> TRUSTED_CODEC = EntityReference.<LivingEntity>codec().listOf();
	private static final String TRUSTED_KEY = "Trusted";
	private static final String SLEEPING_KEY = "Sleeping";

	private static final Field GOAL_SELECTOR = goalField("goalSelector");
	private static final Field TARGET_SELECTOR = goalField("targetSelector");
	private static boolean warned;

	// ================================================================== who may be bitten

	/**
	 * True when {@code beast} may hurt {@code victim}: exactly what {@link Targeting#isHostileTo} accepts for the owner
	 * (monsters, mobs attacking the owner, a sparring partner), plus a wild mob that attacks the beast itself (a llama's
	 * spit, an angry bee, wolf or polar bear). Without the owner online only monsters that are nobody's pet are fair game.
	 * Never the owner, players outside sparring, villagers, pets or passive animals, and never a silverfish or a
	 * heart-bound creaking ({@link #untouchable}).
	 */
	static boolean mayAttack(Mob beast, LivingEntity victim, @Nullable ServerPlayer owner) {
		if (victim == beast || !victim.isAlive() || untouchable(victim)) {
			return false;
		}
		if (victim instanceof Mob attacker && attacker.getTarget() == beast && !isPet(victim)) {
			return true;
		}
		if (owner != null) {
			return victim != owner && Targeting.isHostileTo(victim, owner);
		}
		return victim instanceof Enemy && !isPet(victim);
	}

	/**
	 * Mobs a beast never hurts because the hit would change blocks: a silverfish calls its friends out of infested
	 * blocks when something hurts it, and a creaking bound to a heart makes the heart grow resin when a player's tamed
	 * wolf (or the player) hurts it.
	 */
	static boolean untouchable(@Nullable Entity entity) {
		return entity instanceof Silverfish || entity instanceof Creaking creaking && creaking.isHeartBound();
	}

	private static boolean isPet(LivingEntity entity) {
		return entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null || entity.hasAttached(BeastData.TYPE);
	}

	/** Drops the beast's current target and anger (it went for something it may not hurt). */
	static void calmDown(Mob beast) {
		if (beast instanceof NeutralMob neutral) {
			neutral.stopBeingAngry();
		}
		beast.setTarget(null);
	}

	/** Called every second for each loaded beast of an online owner: forget targets that are not allowed. */
	static void checkTarget(Mob beast, ServerPlayer owner) {
		LivingEntity target = beast.getTarget();
		if (target != null && target.isAlive() && !mayAttack(beast, target, owner)) {
			calmDown(beast);
		}
	}

	/** Every tick for every loaded beast: a silverfish or heart-bound creaking target is dropped before the bite. */
	static void dropUntouchableTarget(Mob beast) {
		if (untouchable(beast.getTarget())) {
			calmDown(beast);
		}
	}

	// ================================================================== foxes

	/**
	 * Makes the fox trust {@code player} (it stops fleeing from them and defends them like a fox defends whoever it
	 * trusts). Vanilla keeps the trusted players private, so this saves the fox, puts the owner first in its "Trusted"
	 * list and loads it back, exactly like {@code /data merge entity}. Returns {@link #TRUST_NEW}, {@link #TRUST_ALREADY}
	 * or {@link #TRUST_FAILED}.
	 */
	static int trust(Fox fox, ServerPlayer player) {
		try {
			int[] result = {TRUST_FAILED};
			boolean changed = patch(fox, tag -> {
				List<EntityReference<LivingEntity>> old = trustedReferences(tag);
				for (EntityReference<LivingEntity> reference : old) {
					if (reference.getUUID().equals(player.getUUID())) {
						result[0] = TRUST_ALREADY;
						return false;
					}
				}
				// A fox remembers two players: the owner first, then whoever it trusted before (if anyone).
				List<EntityReference<LivingEntity>> trusted = new ArrayList<>();
				trusted.add(EntityReference.<LivingEntity>of(player.getUUID()));
				if (!old.isEmpty()) {
					trusted.add(old.get(0));
				}
				Tag encoded = TRUSTED_CODEC.encodeStart(NbtOps.INSTANCE, trusted).result().orElse(null);
				if (encoded == null) {
					return false;
				}
				tag.put(TRUSTED_KEY, encoded);
				result[0] = TRUST_NEW;
				return true;
			});
			return changed ? TRUST_NEW : result[0];
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not make a fox trust {}", player.getName().getString(), exception);
			return TRUST_FAILED;
		}
	}

	/** The players the fox trusts (read from its saved data, nothing is changed); empty when it trusts no one. */
	static List<UUID> trustedIds(Fox fox) {
		try {
			List<UUID> ids = new ArrayList<>();
			patch(fox, tag -> {
				for (EntityReference<LivingEntity> reference : trustedReferences(tag)) {
					ids.add(reference.getUUID());
				}
				return false;
			});
			return ids;
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not read whom a fox trusts", exception);
			return List.of();
		}
	}

	/** True when the fox already trusts {@code player} (a cub of foxes they bred, or a fox they made trust them). */
	static boolean trusts(Fox fox, ServerPlayer player) {
		return trustedIds(fox).contains(player.getUUID());
	}

	/**
	 * Saves the fox, lets {@code edit} change the saved data and loads it back when {@code edit} returns true (the way
	 * {@code /data merge entity} works; the UUID is kept). True when the fox was changed. Throws on a broken save.
	 */
	private static boolean patch(Fox fox, Predicate<CompoundTag> edit) {
		UUID uuid = fox.getUUID();
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(fox.problemPath(), Kultivator.LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, fox.registryAccess());
			fox.saveWithoutId(output);
			CompoundTag tag = output.buildResult();
			if (!edit.test(tag)) {
				return false;
			}
			fox.load(TagValueInput.create(reporter, fox.registryAccess(), tag));
			fox.setUUID(uuid);
			fox.postDataManipulated();
			return true;
		}
	}

	private static List<EntityReference<LivingEntity>> trustedReferences(CompoundTag tag) {
		Tag trusted = tag.get(TRUSTED_KEY);
		return trusted == null ? List.of() : TRUSTED_CODEC.parse(NbtOps.INSTANCE, trusted).result().orElse(List.of());
	}

	/** Wakes a fox that was saved asleep (no running goal would ever wake it once the sleep goal is gone). */
	private static void wake(Fox fox) {
		try {
			patch(fox, tag -> {
				tag.putBoolean(SLEEPING_KEY, false);
				return true;
			});
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not wake a spirit fox", exception);
		}
	}

	/**
	 * Removes the goals an awakened fox must not run: picking berries (changes the bush / vine block), hunting chickens,
	 * rabbits, fish and baby turtles (beasts never hurt passive animals), and sleeping under a roof all day, seeking
	 * shelter and sitting down at random (it must follow its owner). Removing a running sleep goal wakes the fox; a fox
	 * loaded asleep is woken here. Safe to call again; vanilla adds the goals back whenever the fox is loaded, so this
	 * runs on every load too.
	 */
	static void tame(Fox fox) {
		// A spirit beast never picks up items lying around (vanilla foxes grab anything edible).
		fox.setCanPickUpLoot(false);
		GoalSelector goals = selector(GOAL_SELECTOR, fox);
		if (goals != null) {
			goals.removeAllGoals(goal -> {
				String name = goal.getClass().getSimpleName();
				return goal instanceof Fox.FoxEatBerriesGoal || name.equals("SleepGoal") || name.equals("SeekShelterGoal")
						|| name.equals("PerchAndSearchGoal");
			});
			if (fox.isSleeping()) {
				wake(fox);
			}
		}
		GoalSelector targets = selector(TARGET_SELECTOR, fox);
		if (targets != null) {
			// The plain goals are the hunting ones; the fox's "defend whoever I trust" goal is a subclass and stays.
			targets.removeAllGoals(goal -> goal.getClass() == NearestAttackableTargetGoal.class);
		}
	}

	/**
	 * Once a second for each spirit fox of an online owner in the same dimension: comes back when far away (walks when
	 * farther than 6 blocks, teleports to a safe spot next to the owner when farther than 24), unless it is sleeping,
	 * leashed or riding something. A sitting fox stands up to follow.
	 */
	static void follow(Fox fox, ServerPlayer owner) {
		if (owner.isSpectator() || fox.isLeashed() || fox.isPassenger() || fox.isSleeping()) {
			return;
		}
		LivingEntity target = fox.getTarget();
		boolean fighting = target != null && target.isAlive();
		double distanceSq = fox.distanceToSqr(owner);
		if (fighting && distanceSq <= LEASH_DISTANCE * LEASH_DISTANCE) {
			return;
		}
		if (distanceSq > FOLLOW_DISTANCE * FOLLOW_DISTANCE && fox.isSitting()) {
			fox.setSitting(false);
		}
		if (distanceSq > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
			if (teleportNear(fox, owner) && fighting) {
				fox.setTarget(null);
			}
		} else if (!fighting && distanceSq > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
			fox.getNavigation().moveTo(owner, FOLLOW_SPEED);
		}
	}

	/** Sends the owner's spirit foxes nearby (that are not already fighting) after {@code enemy}, if it is hostile. */
	static void defend(ServerPlayer owner, LivingEntity enemy, List<Mob> beasts) {
		if (enemy == owner || !enemy.isAlive() || untouchable(enemy) || !Targeting.isHostileTo(enemy, owner)) {
			return;
		}
		for (Mob beast : beasts) {
			if (!(beast instanceof Fox fox) || fox.level() != owner.level() || fox == enemy
					|| fox.distanceToSqr(owner) > DEFEND_DISTANCE * DEFEND_DISTANCE) {
				continue;
			}
			LivingEntity current = fox.getTarget();
			if (current == null || !current.isAlive()) {
				fox.setTarget(enemy);
			}
		}
	}

	/**
	 * Puts the mob on a safe free spot 2-3.5 blocks from the owner, never in a portal (read-only checks), and gives it a
	 * portal cooldown. False when there is none.
	 */
	static boolean teleportNear(Mob mob, ServerPlayer owner) {
		ServerLevel level = owner.level();
		RandomSource random = mob.getRandom();
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = 2.0D + random.nextDouble() * 1.5D;
			Vec3 near = owner.position().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
			Vec3 spot = Targeting.safeSpot(level, mob, near);
			if (spot != null && !portalAt(level, mob, spot)) {
				mob.snapTo(spot.x, spot.y, spot.z, mob.getYRot(), mob.getXRot());
				// Portal travel could build an exit portal or the End platform (blocks).
				mob.setPortalCooldown();
				mob.getNavigation().stop();
				level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.FOX_TELEPORT, SoundSource.NEUTRAL, 0.6F, 1.1F);
				return true;
			}
		}
		return false;
	}

	/** Read-only: a Nether portal, End portal or End gateway where the mob would stand at {@code spot}. */
	private static boolean portalAt(ServerLevel level, Mob mob, Vec3 spot) {
		AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(spot);
		return level.getBlockStates(box.inflate(0.01D))
				.anyMatch(state -> state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY));
	}

	// ================================================================== goal lists

	private static @Nullable Field goalField(String name) {
		try {
			Field field = Mob.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Spirit foxes keep their vanilla goals: Mob.{} is not reachable ({})", name, exception.toString());
			return null;
		}
	}

	private static @Nullable GoalSelector selector(@Nullable Field field, Mob mob) {
		if (field == null) {
			return null;
		}
		try {
			return (GoalSelector) field.get(mob);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Spirit foxes keep their vanilla goals ({})", exception.toString());
			}
			return null;
		}
	}

	private BeastAi() {
	}
}
