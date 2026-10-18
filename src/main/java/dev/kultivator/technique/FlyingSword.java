package dev.kultivator.technique;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.run.CastOptions;
import dev.kultivator.technique.spec.run.Caster;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.technique.spec.run.PlayerCaster;
import dev.kultivator.technique.spec.run.TechniqueRunner;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pedang Terbang / Flying Sword (DESIGN 22.10): a toggle. In survival/adventure the player really flies through the
 * shared {@link FlightLease} (so another flight source never revokes it), flying speed {@code 0.05 + 0.01 × realm} plus
 * 0.015 per tier above Dao Union (at most 0.18; +25% at Kesempurnaan Agung), paying {@code 2 − 0.2 × realm} qi per second
 * (at least 0.5) × Kenaikan Abadi Tahap 1-3 0.75, 4-6 0.5, 7-9 0.25, Dewa Abadi 0. At Puncak Jurus a sprint-flying ram
 * cuts monsters on the path (0.5 × P, once per target per 10 ticks, at most 4 per second, nobody moved). Out of qi,
 * landing, a hit from an attacker (or a heavy one from anything), riding, a breakthrough, losing the sword, death, leaving
 * or a game mode change ends it. The saved flag {@code extra.techniques.granted_flight} repairs a logout in the air.
 */
public final class FlyingSword {

	/** Why the player left the sword (chooses the message and whether Slow Falling is given). */
	public enum Reason {
		TOGGLE("kultivator.techniques.flying_sword.dismount", true),
		NO_QI("kultivator.techniques.flying_sword.no_qi", true),
		LANDED("kultivator.techniques.flying_sword.landed", false),
		HIT("kultivator.techniques.flying_sword.hit", true),
		RIDING(null, true),
		BREAKTHROUGH("kultivator.techniques.flying_sword.breakthrough", true),
		NO_SWORD("kultivator.techniques.flying_sword.lost_sword", true),
		LOST_FLIGHT(null, true),
		GAMEMODE(null, false),
		DEATH(null, false),
		LEAVE(null, true),
		QUIET(null, false);

		private final @Nullable String messageKey;
		private final boolean slowFalling;

		Reason(@Nullable String messageKey, boolean slowFalling) {
			this.messageKey = messageKey;
			this.slowFalling = slowFalling;
		}
	}

	static final String EXTRA_KEY = "techniques";
	public static final String LEASE_SOURCE = "flying_sword";
	private static final String GRANTED = "granted_flight";
	private static final String SPEED = "flight_speed";
	/** Vanilla's default flying speed (Abilities). */
	private static final float DEFAULT_FLYING_SPEED = 0.05F;
	private static final float MAX_SPEED = 0.18F;
	private static final int SLOW_FALLING_TICKS = 120;
	/** After mounting, landing checks wait this long (the lift-off takes a few ticks to reach the client). */
	private static final int GRACE_TICKS = 15;
	/** Attacker-less damage (fire, cactus...) throws the rider off only at this share of max health in one hit. */
	private static final float HEAVY_HIT = 0.2F;
	private static final int RAM_PER_TARGET = 10;
	private static final int RAM_PER_SECOND = 4;

	/** Game time each rider mounted (session only). */
	private static final Map<UUID, Long> MOUNTED_AT = new HashMap<>();
	private static final Map<UUID, FlightLease> LEASES = new HashMap<>();
	private static final Map<UUID, Caster> RAMS = new HashMap<>();
	private static final Map<UUID, Map<Integer, Long>> RAM_HITS = new HashMap<>();

	static void init() {
		FlightLease.init();
	}

	/** Flying speed while on the sword (22.10), without the mastery bonus. */
	public static float flyingSpeed(CultivationData data) {
		Rank rank = data.rank();
		float speed = 0.05F + 0.01F * rank.realm().ordinal();
		int above = TechniquePower.tierIndex(rank, TechniquePower.playerStars(data)) - 3;
		if (above > 0) {
			speed += 0.015F * above;
		}
		return Math.min(MAX_SPEED, speed);
	}

	private static float speedFor(CultivationData data) {
		float speed = flyingSpeed(data);
		if (Mastery.level(data, Technique.FLYING_SWORD.id()) >= 3) {
			speed *= 1.25F;
		}
		return speed;
	}

	/** Qi paid every second of flight (22.10). */
	public static double drainPerSecond(CultivationData data) {
		Rank rank = data.rank();
		double drain = Math.max(0.5D, 2.0D - 0.2D * rank.realm().ordinal());
		if (rank.realm() == Realm.TRUE_IMMORTAL) {
			return 0.0D;
		}
		if (rank.realm() == Realm.IMMORTAL_ASCENSION) {
			drain *= rank.layer() >= 6 ? 0.25D : rank.layer() >= 3 ? 0.5D : 0.75D;
		}
		return drain;
	}

	/** Steps onto the sword. Tells the player why not and returns false when it is not possible. */
	static boolean mount(Caster ctx, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.flyingSword()) {
			return true;
		}
		ItemStack sword = Cultivators.bestSword(player);
		if (sword.isEmpty()) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.flying_sword.need_sword"));
		}
		if (player.isPassenger()) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.flying_sword.riding"));
		}
		if (player.isSwimming() || player.isUnderWater()) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.flying_sword.water"));
		}
		if (player.isSleeping()) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.now"));
		}
		ServerLevel level = player.level();
		boolean special = player.isCreative() || player.isSpectator();
		if (!special) {
			FlightLease lease = FlightLease.acquire(player, LEASE_SOURCE);
			if (lease == null) {
				return Techniques.refuse(player, Component.translatable("kultivator.teknik.refuse.flight_held"));
			}
			LEASES.put(player.getUUID(), lease);
			float speed = speedFor(data);
			Abilities abilities = player.getAbilities();
			abilities.setFlyingSpeed(speed);
			player.onUpdateAbilities();
			setGranted(data, true, speed);
			if (player.onGround()) {
				// The client drops flight at once while standing on the ground: lift off first.
				Vec3 motion = player.getDeltaMovement();
				player.setDeltaMovement(motion.x, 0.45D, motion.z);
				player.connection.send(new ClientboundSetEntityMotionPacket(player));
			}
		}
		data.setFlyingSword(true);
		MOUNTED_AT.put(player.getUUID(), level.getGameTime());
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);

		Vec3 feet = player.position();
		// The sword actually ridden (one not held, when there is one), the same one the look shows.
		ItemStack ridden = Cultivators.flightSword(player);
		int colorIndex = SwordColors.index(BuiltInRegistries.ITEM.getKey(ridden.getItem()).toString());
		Techniques.fx(player, Technique.FLYING_SWORD, colorIndex << 4, feet, feet, 1.0F);
		ctx.castFx(feet, feet, 1.0F, 0.0F, 1, 0);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.9F, 1.4F);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
		Msg.actionBar(player, Component.translatable("kultivator.techniques.flying_sword.mount",
				ridden.getHoverName().copy().withColor(SwordColors.color(colorIndex)),
				Techniques.name(Technique.FLYING_SWORD)).withColor(Techniques.JADE));
		Advancements.grant(player, "flying_sword");
		return true;
	}

	/** Steps down (or is thrown off). Gives back what we changed; creative/spectator abilities are never touched. */
	public static void dismount(ServerPlayer player, CultivationData data, Reason reason) {
		boolean wasMounted = data.flyingSword();
		boolean granted = granted(data);
		data.setFlyingSword(false);
		MOUNTED_AT.remove(player.getUUID());
		RAMS.remove(player.getUUID());
		RAM_HITS.remove(player.getUUID());
		if (granted) {
			revoke(player, data, reason.slowFalling);
		}
		if (!wasMounted && !granted) {
			return;
		}
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);
		if (reason == Reason.DEATH || reason == Reason.LEAVE || reason == Reason.QUIET || reason == Reason.GAMEMODE) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 feet = player.position();
		int colorIndex = SwordColors.index(BuiltInRegistries.ITEM.getKey(Cultivators.bestSword(player).getItem()).toString());
		Techniques.fx(player, Technique.FLYING_SWORD, 1 | colorIndex << 4, feet, feet, 1.0F);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8F, 1.3F);
		if (reason.messageKey != null) {
			Msg.actionBar(player, Component.translatable(reason.messageKey).withColor(reason == Reason.TOGGLE || reason == Reason.LANDED
					? Techniques.MUTED : Techniques.REFUSE));
		}
	}

	/** Takes back the flight we granted (the lease, or the saved flag of an older session) and clears the saved flag. */
	private static void revoke(ServerPlayer player, CultivationData data, boolean slowFalling) {
		float ours = data.extra().getCompoundOrEmpty(EXTRA_KEY).getFloatOr(SPEED, -1.0F);
		restore(player, ours, slowFalling);
		setGranted(data, false, 0.0F);
	}

	/**
	 * Survival/adventure lose mayfly/flying (through the lease when we hold one) and get the default flying speed back
	 * (plus Slow Falling when asked); for creative/spectator only a flying speed we set ourselves is put back.
	 */
	private static void restore(ServerPlayer player, float ourSpeed, boolean slowFalling) {
		Abilities abilities = player.getAbilities();
		FlightLease lease = LEASES.remove(player.getUUID());
		if (player.isCreative() || player.isSpectator()) {
			if (lease != null) {
				lease.release();
			}
			if (ourSpeed > 0.0F && Math.abs(abilities.getFlyingSpeed() - ourSpeed) < 1.0E-4F) {
				abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
				player.onUpdateAbilities();
			}
			return;
		}
		boolean wasFlying = abilities.flying;
		FlightLease other = FlightLease.holder(player);
		if (lease != null) {
			lease.release();
		} else if (other == null) {
			// An older session's grant (no lease after a relog): take it back by hand.
			abilities.mayfly = false;
			abilities.flying = false;
		}
		abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
		player.onUpdateAbilities();
		if (slowFalling && (wasFlying || !player.onGround()) && FlightLease.holder(player) == null) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, true));
		}
	}

	/** Every tick: qi drain, landing, riding, breakthroughs, game mode changes, a lost sword, the Puncak Jurus ram. */
	static void tick(ServerPlayer player, CultivationData data) {
		boolean granted = granted(data);
		if (!data.flyingSword()) {
			Long mounted = MOUNTED_AT.remove(player.getUUID());
			if (granted) {
				// Left over from a reset session (respawn...): give the flight back quietly.
				revoke(player, data, false);
				Cultivators.dirty(player);
			} else if (mounted != null) {
				// Our data was replaced while flying (debug reset): the flight is still ours to take back.
				restore(player, -1.0F, true);
			}
			return;
		}
		if (player.isCreative() || player.isSpectator()) {
			if (granted) {
				// Switched to creative/spectator while flying on our grant: just forget it, keep the visuals.
				revoke(player, data, false);
				Cultivators.dirty(player);
			}
			return;
		}
		if (!granted) {
			// Mounted in creative, now survival/adventure (vanilla took the flight away): step down quietly.
			dismount(player, data, Reason.GAMEMODE);
			return;
		}
		Abilities abilities = player.getAbilities();
		if (!abilities.mayfly) {
			dismount(player, data, Reason.LOST_FLIGHT);
			return;
		}
		if (player.isPassenger()) {
			dismount(player, data, Reason.RIDING);
			return;
		}
		if (data.breakingThrough()) {
			dismount(player, data, Reason.BREAKTHROUGH);
			return;
		}
		long now = player.level().getGameTime();
		long mountedAt = MOUNTED_AT.getOrDefault(player.getUUID(), now - GRACE_TICKS - 1);
		long riding = now - mountedAt;
		if (riding > GRACE_TICKS && !abilities.flying && player.onGround()) {
			dismount(player, data, Reason.LANDED);
			return;
		}
		if (riding > 0 && riding % 20 == 0) {
			if (Cultivators.bestSword(player).isEmpty()) {
				dismount(player, data, Reason.NO_SWORD);
				return;
			}
			if (!TechniqueRunner.bypass(player) && !Qi.trySpend(player, drainPerSecond(data))) {
				dismount(player, data, Reason.NO_QI);
				return;
			}
		}
		if (riding % 5 == 0 && player.isSprinting() && abilities.flying && Mastery.level(data, Technique.FLYING_SWORD.id()) >= TechniquePower.MAX_MASTERY) {
			ram(player, now);
		}
	}

	/** The sprint-flying ram: PATH (half width 1.5) > 0.5 × P, once per target per 10 ticks, ≤ 4 per second, nobody moved. */
	private static void ram(ServerPlayer player, long now) {
		Caster ctx = RAMS.get(player.getUUID());
		if (ctx == null || ctx.ended()) {
			ctx = new PlayerCaster(player, Technique.FLYING_SWORD.spec(), new CastOptions(false, true, false, false, true));
			RAMS.put(player.getUUID(), ctx);
		}
		Map<Integer, Long> hits = RAM_HITS.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
		hits.values().removeIf(time -> now - time > 40L);
		int lastSecond = 0;
		for (long time : hits.values()) {
			if (now - time < 20L) {
				lastSecond++;
			}
		}
		Vec3 from = player.position();
		Vec3 to = from.add(player.getDeltaMovement().scale(5.0D));
		AABB path = new AABB(from, to).inflate(1.5D);
		for (LivingEntity target : ctx.hostiles(from.add(to).scale(0.5D), from.distanceTo(to) * 0.5D + 2.5D, true)) {
			if (lastSecond >= RAM_PER_SECOND) {
				break;
			}
			Long last = hits.get(target.getId());
			if (last != null && now - last < RAM_PER_TARGET || !path.intersects(target.getBoundingBox())) {
				continue;
			}
			if (ctx.hurt(ctx.damage(target, 0.5D).precise(true))) {
				hits.put(target.getId(), now);
				lastSecond++;
			}
		}
	}

	/**
	 * Damage throws the rider off: any hit from an attacker or projectile, or an attacker-less one of at least 20% of
	 * max health at once.
	 */
	static void onHurt(ServerPlayer player, DamageSource source, float damageTaken) {
		CultivationData data = Cultivators.get(player);
		if (data.flyingSword() && damageTaken > 0.0F && (source.getEntity() != null || source.getDirectEntity() != null
				|| damageTaken >= player.getMaxHealth() * HEAVY_HIT)) {
			dismount(player, data, Reason.HIT);
		}
	}

	/** On join, before the session reset: a flag saved in the air means the flight must be taken back now. */
	static void onJoin(ServerPlayer player, CultivationData data) {
		MOUNTED_AT.remove(player.getUUID());
		LEASES.remove(player.getUUID());
		if (granted(data)) {
			revoke(player, data, true);
			Cultivators.dirty(player);
		}
	}

	/** Leaving: give the flight back before the player is saved (Slow Falling carries over to the next join). */
	static void onLeave(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.flyingSword() || granted(data)) {
			dismount(player, data, Reason.LEAVE);
		}
		MOUNTED_AT.remove(player.getUUID());
		LEASES.remove(player.getUUID());
	}

	static void onDeath(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.flyingSword() || granted(data)) {
			dismount(player, data, Reason.DEATH);
		}
		MOUNTED_AT.remove(player.getUUID());
		LEASES.remove(player.getUUID());
	}

	static void clearAll() {
		MOUNTED_AT.clear();
		LEASES.clear();
		RAMS.clear();
		RAM_HITS.clear();
	}

	/** True when the saved flag says we gave this player flight. */
	public static boolean granted(CultivationData data) {
		return data.extra().getCompoundOrEmpty(EXTRA_KEY).getBooleanOr(GRANTED, false);
	}

	private static void setGranted(CultivationData data, boolean granted, float speed) {
		CompoundTag extra = data.extra();
		CompoundTag mine = extra.getCompoundOrEmpty(EXTRA_KEY);
		if (granted) {
			mine.putBoolean(GRANTED, true);
			mine.putFloat(SPEED, speed);
		} else {
			mine.remove(GRANTED);
			mine.remove(SPEED);
		}
		extra.put(EXTRA_KEY, mine);
	}

	private FlyingSword() {
	}
}
