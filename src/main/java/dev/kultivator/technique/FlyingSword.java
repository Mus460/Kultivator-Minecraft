package dev.kultivator.technique;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Technique;
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
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pedang Terbang / Flying Sword (DESIGN section 8): a toggle. In survival/adventure the player really flies
 * ({@code abilities.mayfly/flying}, flying speed {@code 0.05 + 0.01 x realm}) and pays {@code 2 - 0.2 x realm} qi per
 * second (at least 0.5). Out of qi, landing, a hit from an attacker (or a heavy one from anything), riding, a breakthrough, losing the sword, death, leaving or a
 * game mode change ends it. Flight is only taken away again when we granted it (saved flag
 * {@code extra.techniques.granted_flight}, so a logout in the air is repaired on the next join). Creative and spectator
 * players only get the flag and the visuals; their abilities are never touched.
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
	private static final String GRANTED = "granted_flight";
	private static final String SPEED = "flight_speed";
	/** Vanilla's default flying speed (Abilities). */
	private static final float DEFAULT_FLYING_SPEED = 0.05F;
	private static final int SLOW_FALLING_TICKS = 120;
	/** After mounting, landing checks wait this long (the lift-off takes a few ticks to reach the client). */
	private static final int GRACE_TICKS = 15;
	/** Attacker-less damage (fire, cactus...) throws the rider off only at this share of max health in one hit. */
	private static final float HEAVY_HIT = 0.2F;

	/** Game time each rider mounted (session only). */
	private static final Map<UUID, Long> MOUNTED_AT = new HashMap<>();

	/** Flying speed while on the sword. */
	public static float flyingSpeed(CultivationData data) {
		return 0.05F + 0.01F * data.realm().ordinal();
	}

	/** Qi paid every second of flight. */
	public static double drainPerSecond(CultivationData data) {
		return Math.max(0.5D, 2.0D - 0.2D * data.realm().ordinal());
	}

	/** Steps onto the sword. Tells the player why not and returns false when it is not possible. */
	static boolean mount(ServerPlayer player, CultivationData data) {
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
			float speed = flyingSpeed(data);
			Abilities abilities = player.getAbilities();
			abilities.mayfly = true;
			abilities.flying = true;
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
		int colorIndex = SwordColors.index(BuiltInRegistries.ITEM.getKey(sword.getItem()).toString());
		Techniques.fx(player, Technique.FLYING_SWORD, colorIndex << 4, feet, feet, 1.0F);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.9F, 1.4F);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
		Msg.actionBar(player, Component.translatable("kultivator.techniques.flying_sword.mount",
				sword.getHoverName().copy().withColor(SwordColors.color(colorIndex)),
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

	/**
	 * Takes back the flight we granted (see {@link #restore}) and clears the saved flag.
	 */
	private static void revoke(ServerPlayer player, CultivationData data, boolean slowFalling) {
		float ours = data.extra().getCompoundOrEmpty(EXTRA_KEY).getFloatOr(SPEED, -1.0F);
		restore(player, ours, slowFalling);
		setGranted(data, false, 0.0F);
	}

	/**
	 * Survival/adventure lose mayfly/flying and get the default flying speed back (plus Slow Falling when asked); for
	 * creative/spectator only a flying speed we set ourselves ({@code ourSpeed}, -1 when unknown) is put back.
	 */
	private static void restore(ServerPlayer player, float ourSpeed, boolean slowFalling) {
		Abilities abilities = player.getAbilities();
		if (player.isCreative() || player.isSpectator()) {
			if (ourSpeed > 0.0F && Math.abs(abilities.getFlyingSpeed() - ourSpeed) < 1.0E-4F) {
				abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
				player.onUpdateAbilities();
			}
			return;
		}
		boolean wasFlying = abilities.flying;
		abilities.mayfly = false;
		abilities.flying = false;
		abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
		player.onUpdateAbilities();
		if (slowFalling && (wasFlying || !player.onGround())) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, true));
		}
	}

	/** Every tick: qi drain, landing, riding, breakthroughs, game mode changes, a lost sword. */
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
			if (!Qi.trySpend(player, drainPerSecond(data))) {
				dismount(player, data, Reason.NO_QI);
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
	}

	static void onDeath(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.flyingSword() || granted(data)) {
			dismount(player, data, Reason.DEATH);
		}
		MOUNTED_AT.remove(player.getUUID());
	}

	static void clearAll() {
		MOUNTED_AT.clear();
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
