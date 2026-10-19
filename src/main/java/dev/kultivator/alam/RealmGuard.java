package dev.kultivator.alam;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.DeathGuard;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The safety rules of the realms (DESIGN 25.1.1, 25.3.4, 25.3.10):
 * <ul>
 * <li>DeathGuard {@code REALM}: a lethal hit inside a run leaves the player at 1 HP and expels them (Luka Alam); before the
 * Totem, so totems and cheat-death charges are never spent; never for {@code /kill} or {@code out_of_world} (the spine skips
 * those). In Mode Ringan only the run's own damage (its mobs and hazards) is caught;</li>
 * <li>the disciple death guard: a party disciple "killed" inside stays at 1 HP and goes home through {@code depart};</li>
 * <li>prevention inside the realm dimension for every player not in creative: no block breaking, no block use or placement,
 * no buckets, flint and steel, fire charges, bone meal, spawn eggs, ender pearls, chorus fruit or wind charges;</li>
 * <li>Mode Kehancuran never acts in the dimension (a protected-zone provider covering all of it);</li>
 * <li>run entities are refused when loaded from disk; stray disciples in the dimension are sent home.</li>
 * </ul>
 * Prevention only: nothing here changes a block.
 */
public final class RealmGuard {

	/** Luka Alam: technique damage ×0.8 (DESIGN 25.3.4, PW ch. 345); within the spine's caster clamp 0.8-1.30. */
	public static final double LUKA_FACTOR = 0.8D;
	/** A gate is refused within this many ticks of being hurt. */
	public static final long HURT_LOCK = 200L;

	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	static void init() {
		DeathGuard.register(DeathGuard.Stage.REALM, RealmGuard::preventDeath);
		DiscipleHooks.addDeathGuard((disciple, source) -> RealmRun.saveDisciple(disciple));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !restricted(player, level));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> restricted(player, level) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				restricted(player, level) && forbidden(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
		ProtectedZones.addZoneProvider((level, pos) -> Alam.isRealmLevel(level));
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !(entity instanceof RealmRelicEntity || entity.entityTags().contains(RealmRun.RUN_TAG)));
		ServerEntityEvents.ENTITY_LOAD.register(RealmGuard::onLoad);
		TechniquePower.addCasterFactor((attacker, spec, target) ->
				attacker instanceof ServerPlayer player && Alam.lukaLeft(player) > 0L ? LUKA_FACTOR : 1.0D);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				LAST_HURT.put(player.getUUID(), player.level().getServer().overworld().getGameTime());
				RealmRun.hurt(player, damageTaken);
			}
		});
	}

	/** True for a non-creative player in the realm dimension. */
	static boolean restricted(Player player, Level level) {
		return Alam.isRealmLevel(level) && !player.isCreative() && !player.isSpectator();
	}

	/** The items of DESIGN 25.1.1 that must not be used inside the dimension. */
	static boolean forbidden(ItemStack stack) {
		return stack.getItem() instanceof BucketItem || stack.getItem() instanceof SolidBucketItem || stack.getItem() instanceof SpawnEggItem
				|| stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.BONE_MEAL) || stack.is(Items.ENDER_PEARL)
				|| stack.is(Items.CHORUS_FRUIT) || stack.is(Items.WIND_CHARGE);
	}

	/** True when the player was hurt within the last 10 s. */
	static boolean recentlyHurt(ServerPlayer player) {
		Long at = LAST_HURT.get(player.getUUID());
		return at != null && player.level().getServer().overworld().getGameTime() - at < HURT_LOCK;
	}

	static void forget(UUID player) {
		LAST_HURT.remove(player);
	}

	static void clearAll() {
		LAST_HURT.clear();
	}

	private static boolean preventDeath(ServerPlayer player, DamageSource source, float amount) {
		RealmRun run = RealmRun.of(player);
		if (run == null) {
			return false;
		}
		if (run.light) {
			Entity attacker = source.getEntity();
			Entity direct = source.getDirectEntity();
			boolean ours = RealmRun.hazard || (attacker != null && run.entities.contains(attacker.getUUID()))
					|| (direct != null && run.entities.contains(direct.getUUID()));
			if (!ours) {
				return false;
			}
		} else if (player.level() != run.level) {
			return false;
		}
		player.setHealth(1.0F);
		player.setRemainingFireTicks(0);
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		Alam.schedule(1, () -> {
			RealmRun current = RealmRun.of(player);
			if (current != null) {
				current.leave(player, true);
			}
		});
		return true;
	}

	/** A disciple loaded in the dimension that no live run owns goes home (its owner online) or to the hall's away list. */
	private static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof DiscipleEntity) || !Alam.isRealmLevel(level)) {
			return;
		}
		DiscipleEntity disciple = (DiscipleEntity) entity;
		Alam.schedule(2, () -> relocate(disciple));
	}

	static void relocate(DiscipleEntity disciple) {
		if (disciple.isRemoved() || !disciple.isAlive() || RealmRun.owning(disciple) != null || disciple.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		UUID owner = disciple.ownerId();
		if (owner == null || !(disciple.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(owner);
		if (master != null) {
			CompoundTag tag = Disciples.depart(disciple, "alam");
			if (!tag.isEmpty()) {
				RealmRun.pushHome(master, tag);
			}
		} else if (Halls.hallOf(owner) != null) {
			Halls.storeAway(owner, disciple, level.getServer().overworld().getGameTime(), "alam", new CompoundTag());
		}
	}

	private RealmGuard() {
	}
}
