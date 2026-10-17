package dev.kultivator.cultivation;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Heavenly Tribulation (DESIGN section 3): a 12 s omen of gathering clouds, then a series of strikes whose damage is
 * a share of max health, unavoidable and never lethal. One instance per player, ticked on the server. Every visual is a
 * client particle effect (no LightningBolt entity, no weather change, no block is touched); thunder is only sound.
 * <p>
 * Started by {@link Breakthrough}; when it ends it reports back with {@code Breakthrough.onTribulationEnd}.
 */
public final class Tribulation {

	/** Damage type {@code kultivator:tribulation} (data/kultivator/damage_type/tribulation.json). */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Kultivator.id("tribulation"));

	public static final int VARIANT_OVERWORLD = 0;
	public static final int VARIANT_NETHER = 1;
	public static final int VARIANT_END = 2;

	/** Length of the omen. */
	public static final int OMEN_TICKS = 240;
	/** Ticks between two strikes. */
	public static final int STRIKE_INTERVAL = 70;
	/** How far (horizontally) the cultivator may wander from the starting point. */
	public static final double MAX_DISTANCE = 6.0D;
	/** The clouds hang this high above the starting point; strikes come from this height. */
	public static final double SKY_HEIGHT = 22.0D;
	/** Pause after the final strike before the result. */
	private static final int SETTLE_TICKS = 30;

	/** How a tribulation ended. */
	public enum Result {
		/** Every strike survived. */
		SUCCESS,
		/** A strike would have killed: the tribulation failed (the player keeps 1 health). */
		FAILED,
		/** The player left the tribulation ground after the first strike. */
		FLED,
		/** The player left during the omen, or the breakthrough was cancelled: no penalty. */
		CANCELLED
	}

	private enum Phase { OMEN, STRIKES, SETTLING }

	private static final class Instance {
		final Rank target;
		final boolean test;
		final int variant;
		final Vec3 start;
		final ResourceKey<Level> dimension;
		final int strikeCount;
		final double pct;
		Phase phase = Phase.OMEN;
		int ticks;
		int strikesDone;
		int nextStrikeAt = OMEN_TICKS;
		int settleAt;
		float lowestHealth = 1.0F;

		Instance(Rank target, boolean test, int variant, Vec3 start, ResourceKey<Level> dimension) {
			this.target = target;
			this.test = test;
			this.variant = variant;
			this.start = start;
			this.dimension = dimension;
			this.strikeCount = strikeCount(target);
			this.pct = strikePct(target);
		}
	}

	private static final Map<UUID, Instance> ACTIVE = new HashMap<>();
	/** Game time until which on-fire damage from a Nether strike may never kill the player. */
	private static final Map<UUID, Long> FIRE_GUARD = new HashMap<>();
	/**
	 * Game time until which nothing (except /kill and the void) may kill a player whose tribulation just failed: the
	 * wither, arrows or potions the protectors already sent stay behind after they vanish.
	 */
	private static final Map<UUID, Long> POST_GUARD = new HashMap<>();
	private static final long POST_GUARD_TICKS = 100L;

	public static void init() {
		Cultivators.tribulationFlag = Tribulation::isActive;
		// Safety net for "never lethal": our own damage types (and the fire a Nether strike set) never kill, and neither
		// does anything in the few seconds after a failed tribulation.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}
			boolean ours = source.is(DAMAGE) || source.is(Deviation.DAMAGE);
			if (!ours && source.is(DamageTypes.ON_FIRE)) {
				Long until = FIRE_GUARD.get(player.getUUID());
				ours = until != null && player.level().getGameTime() <= until;
			}
			if (!ours) {
				Long guard = POST_GUARD.get(player.getUUID());
				if (guard != null && player.level().getGameTime() > guard) {
					POST_GUARD.remove(player.getUUID());
				} else {
					ours = guard != null && !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD);
				}
			}
			if (!ours) {
				return true;
			}
			player.setHealth(1.0F);
			player.clearFire();
			player.removeEffect(MobEffects.WITHER);
			return false;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			FIRE_GUARD.clear();
			POST_GUARD.clear();
		});
	}

	/** True while a tribulation runs for the player. */
	public static boolean isActive(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	/** Strikes of the tribulation into {@code target}: 3, 5, 7, 9 (Golden Core to Dao Union), 9 + layer, 18. */
	public static int strikeCount(Rank target) {
		Realm realm = target.realm();
		if (realm == Realm.TRUE_IMMORTAL) {
			return 18;
		}
		if (realm == Realm.IMMORTAL_ASCENSION) {
			return 9 + target.layer() + 1;
		}
		return Math.max(3, 3 + 2 * (realm.ordinal() - Realm.GOLDEN_CORE.ordinal()));
	}

	/** Share of max health one (normal) strike takes: 0.12 + 0.01 x realm + 0.005 x layer. */
	public static double strikePct(Rank target) {
		return 0.12D + 0.01D * target.realm().ordinal() + 0.005D * target.layer();
	}

	/** 0 Overworld (and other dimensions), 1 Nether, 2 End. */
	public static int variant(ServerLevel level) {
		if (level.dimension().equals(Level.NETHER)) {
			return VARIANT_NETHER;
		}
		return level.dimension().equals(Level.END) ? VARIANT_END : VARIANT_OVERWORLD;
	}

	/** The {@code kultivator:tribulation} damage source of {@code level}. */
	public static DamageSource damageSource(ServerLevel level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
	}

	/**
	 * Begins the omen over the player. {@code target} decides the strength (targets below Golden Core are raised to
	 * Golden Core). A {@code test} tribulation reports back as a test.
	 */
	public static void start(ServerPlayer player, Rank target, boolean test) {
		ServerLevel level = player.level();
		Rank strength = target.realm().atLeast(Realm.GOLDEN_CORE) ? target : new Rank(Realm.GOLDEN_CORE, 0, 0);
		int variant = variant(level);
		Instance instance = new Instance(strength, test, variant, player.position(), level.dimension());
		ACTIVE.put(player.getUUID(), instance);

		Component strikes = Component.literal(String.valueOf(instance.strikeCount)).withStyle(ChatFormatting.YELLOW);
		Msg.title(player, Component.translatable("kultivator.breakthrough.omen.title." + variant).withStyle(variantColor(variant), ChatFormatting.BOLD),
				Component.translatable("kultivator.breakthrough.omen.subtitle", strikes), 10, 80, 20);
		Msg.chat(player, Component.translatable("kultivator.breakthrough.omen.chat." + variant, strikes).withStyle(variantColor(variant)));
		Msg.chat(player, Component.translatable("kultivator.breakthrough.omen.stay", Component.literal(String.valueOf((int) MAX_DISTANCE)))
				.withStyle(ChatFormatting.GRAY));
		sendOmen(level, instance, OMEN_TICKS);
		rumble(level, player, instance.variant, player.getRandom());
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		Instance t = ACTIVE.get(player.getUUID());
		if (t == null || player.isDeadOrDying()) {
			return;
		}
		ServerLevel level = player.level();
		t.ticks++;
		double dx = player.getX() - t.start.x;
		double dz = player.getZ() - t.start.z;
		boolean away = !level.dimension().equals(t.dimension) || dx * dx + dz * dz > MAX_DISTANCE * MAX_DISTANCE;

		if (t.phase == Phase.OMEN) {
			if (away) {
				finish(player, Result.CANCELLED, "kultivator.breakthrough.cancel.left_area");
				return;
			}
			if (t.ticks % 40 == 0) {
				sendOmen(level, t, Math.max(0, OMEN_TICKS - t.ticks));
				rumble(level, player, t.variant, player.getRandom());
			}
			if (t.ticks % 20 == 0 && t.ticks < OMEN_TICKS) {
				int left = (OMEN_TICKS - t.ticks) / 20;
				Msg.actionBar(player, Component.translatable("kultivator.breakthrough.omen.countdown", left).withStyle(variantColor(t.variant)));
			}
			if (t.ticks >= OMEN_TICKS) {
				t.phase = Phase.STRIKES;
				t.nextStrikeAt = t.ticks;
			}
		}

		if (t.phase == Phase.STRIKES) {
			if (away) {
				if (t.strikesDone > 0) {
					finish(player, Result.FLED, "kultivator.breakthrough.fled");
				} else {
					finish(player, Result.CANCELLED, "kultivator.breakthrough.cancel.left_area");
				}
				return;
			}
			trackHealth(player, t);
			if (t.ticks >= t.nextStrikeAt) {
				if (!strike(player, level, data, t)) {
					return;
				}
				t.nextStrikeAt = t.ticks + STRIKE_INTERVAL;
				if (t.strikesDone >= t.strikeCount) {
					t.phase = Phase.SETTLING;
					t.settleAt = t.ticks + SETTLE_TICKS;
				}
			} else if ((t.nextStrikeAt - t.ticks) == 35) {
				// Keep the clouds alive and rumbling between strikes.
				sendOmen(level, t, 0);
				rumble(level, player, t.variant, player.getRandom());
			}
		} else if (t.phase == Phase.SETTLING) {
			trackHealth(player, t);
			if (t.ticks >= t.settleAt) {
				finish(player, Result.SUCCESS, null);
			}
		}
	}

	/** Lowest health fraction seen since the first strike (1.0 before). */
	public static float lowestHealthFraction(ServerPlayer player) {
		Instance t = ACTIVE.get(player.getUUID());
		return t == null ? 1.0F : t.lowestHealth;
	}

	/** Strikes that already fell (0 without a tribulation). */
	public static int strikesDone(ServerPlayer player) {
		Instance t = ACTIVE.get(player.getUUID());
		return t == null ? 0 : t.strikesDone;
	}

	/** Strikes of the running tribulation (0 without one). */
	public static int strikeTotal(ServerPlayer player) {
		Instance t = ACTIVE.get(player.getUUID());
		return t == null ? 0 : t.strikeCount;
	}

	/** Ends the player's tribulation without any result (death, logout, debug). True if one was running. */
	public static boolean abort(ServerPlayer player) {
		Instance t = ACTIVE.remove(player.getUUID());
		if (t == null) {
			return false;
		}
		DaoProtectors.clear(player);
		Fx.send(player.level(), t.start, t.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_END, t.variant, 0, 0.0F);
		Cultivators.refreshVisual(player);
		return true;
	}

	/** Cancels the tribulation without penalty and reports CANCELLED to the breakthrough. */
	public static void cancel(ServerPlayer player, @Nullable String messageKey) {
		if (ACTIVE.containsKey(player.getUUID())) {
			finish(player, Result.CANCELLED, messageKey);
		}
	}

	public static void onLeave(ServerPlayer player) {
		abort(player);
		FIRE_GUARD.remove(player.getUUID());
		POST_GUARD.remove(player.getUUID());
	}

	// ================================================================== internals

	/** One strike. False when the tribulation ended with it. */
	private static boolean strike(ServerPlayer player, ServerLevel level, CultivationData data, Instance t) {
		t.strikesDone++;
		int number = t.strikesDone;
		boolean last = number >= t.strikeCount;
		Vec3 feet = player.position();
		Vec3 sky = feet.add(0.0D, SKY_HEIGHT, 0.0D);
		Fx.send(level, feet, sky, FxType.TRIBULATION_STRIKE, t.variant, number, last ? 2.0F : 1.0F);
		strikeSounds(level, player, t.variant, last, player.getRandom());
		Msg.actionBar(player, Component.translatable(last ? "kultivator.breakthrough.strike.final" : "kultivator.breakthrough.strike",
				number, t.strikeCount).withStyle(last ? ChatFormatting.GOLD : variantColor(t.variant)));

		boolean protectedPlayer = player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable;
		boolean wouldKill = false;
		if (!protectedPlayer) {
			float maxHealth = player.getMaxHealth();
			// A powered protective formation around the cultivator softens the strikes (DESIGN 3: pills and formations).
			double formation = dev.kultivator.formation.ProtectiveFormations.insideActiveFormation(level, player.position()) ? 0.85D : 1.0D;
			double raw = maxHealth * t.pct * (last ? 2.0D : 1.0D) * KultivatorConfig.get().tribulationDamageScale * data.nextTribulationFactor()
					* formation;
			float amount = (float) Math.max(0.0D, raw);
			float health = player.getHealth();
			float pool = health + player.getAbsorptionAmount();
			wouldKill = amount >= pool;
			float dealt = Math.min(amount, pool - 1.0F);
			if (dealt > 0.0F) {
				player.hurtServer(level, damageSource(level), dealt);
			}
			if (player.isAlive() && player.getHealth() < 1.0F) {
				player.setHealth(1.0F);
			}
			if (t.variant == VARIANT_NETHER && !wouldKill && player.getHealth() > 6.0F) {
				// Entity fire only: igniting an entity never sets blocks alight. The guarded ignite skips the fire next to
				// powder snow or a water / powder snow cauldron and puts it out near one (a burning player would melt or lower them).
				if (dev.kultivator.technique.DaoTechniques.ignite(player, 2.0F)) {
					FIRE_GUARD.put(player.getUUID(), level.getGameTime() + 100L);
				}
			}
		}
		trackHealth(player, t);
		if (wouldKill) {
			finish(player, Result.FAILED, "kultivator.breakthrough.fail.strike");
			return false;
		}
		if (KultivatorConfig.get().daoProtectorEvent && !last && (number - 1) % 3 == 0) {
			DaoProtectors.spawnWave(player, t.target, t.variant);
		}
		return true;
	}

	private static void trackHealth(ServerPlayer player, Instance t) {
		float max = Math.max(1.0F, player.getMaxHealth());
		t.lowestHealth = Math.min(t.lowestHealth, Math.max(0.0F, player.getHealth() / max));
	}

	private static void finish(ServerPlayer player, Result result, @Nullable String messageKey) {
		Instance t = ACTIVE.remove(player.getUUID());
		if (t == null) {
			return;
		}
		ServerLevel level = player.level();
		DaoProtectors.clear(player);
		if (result == Result.FAILED) {
			// Whatever the vanished protectors left behind (wither, fire, arrows in flight) must not finish the player off.
			player.removeEffect(MobEffects.WITHER);
			player.clearFire();
			POST_GUARD.put(player.getUUID(), level.getGameTime() + POST_GUARD_TICKS);
		}
		Fx.send(level, t.start, t.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_END, t.variant,
				result == Result.SUCCESS ? 1 : 0, 0.0F);
		if (result == Result.SUCCESS) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.WEATHER, 1.2F, 0.8F);
		} else if (result != Result.CANCELLED) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 0.4F);
		}
		if (messageKey != null) {
			ChatFormatting color = result == Result.CANCELLED ? ChatFormatting.GRAY : ChatFormatting.RED;
			Msg.chat(player, Component.translatable(messageKey).withStyle(color));
		}
		Cultivators.refreshVisual(player);
		Breakthrough.onTribulationEnd(player, result, t.lowestHealth, t.variant, t.test);
	}

	private static void sendOmen(ServerLevel level, Instance t, int ticksLeft) {
		Fx.send(level, t.start, t.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_OMEN, t.variant, ticksLeft, 0.0F);
	}

	/** Low, distant thunder (sound only) and a variant voice now and then. */
	private static void rumble(ServerLevel level, ServerPlayer player, int variant, RandomSource random) {
		double x = player.getX();
		double y = player.getY() + 12.0D;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.35F + random.nextFloat() * 0.15F,
				0.45F + random.nextFloat() * 0.2F);
		if (random.nextInt(3) == 0) {
			switch (variant) {
				case VARIANT_NETHER -> level.playSound(null, x, y, z, SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 0.4F, 0.5F);
				case VARIANT_END -> level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.25F, 0.6F);
				default -> level.playSound(null, x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.WEATHER, 0.5F, 0.5F);
			}
		}
	}

	private static void strikeSounds(ServerLevel level, ServerPlayer player, int variant, boolean last, RandomSource random) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		level.playSound(null, x, y + 4.0D, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, last ? 3.5F : 2.5F,
				0.8F + random.nextFloat() * 0.2F);
		level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, last ? 1.6F : 1.0F, 0.5F + random.nextFloat() * 0.2F);
		switch (variant) {
			case VARIANT_NETHER -> {
				level.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.6F);
				if (last || random.nextInt(3) == 0) {
					level.playSound(null, x, y + 6.0D, z, SoundEvents.GHAST_WARN, SoundSource.HOSTILE, 0.5F, 0.6F);
				}
			}
			case VARIANT_END -> {
				level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.5F);
				if (last) {
					level.playSound(null, x, y + 6.0D, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.8F, 0.8F);
				}
			}
			default -> {
				if (last) {
					level.playSound(null, x, y, z, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.WEATHER, 1.0F, 0.6F);
				}
			}
		}
	}

	static ChatFormatting variantColor(int variant) {
		return switch (variant) {
			case VARIANT_NETHER -> ChatFormatting.RED;
			case VARIANT_END -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.DARK_AQUA;
		};
	}

	private Tribulation() {
	}
}
