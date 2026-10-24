package dev.kultivator.disciple;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A disciple's breakthrough (DESIGN 18), started by the master from the disciple screen:
 * <ul>
 *   <li>Mortal -> Qi Refining: the awakening, 6 s of gathering qi, always succeeds.</li>
 *   <li>Qi Refining -> Foundation: 5 s of qi gathering, then a chance roll.</li>
 *   <li>Golden Core and every higher step: a mini Heavenly Tribulation. A 6 s omen of clouds, then
 *   {@code 2 + target realm / 2} strikes 3 s apart, each 18% of the disciple's max health (pills soften it). The damage
 *   is clamped so the disciple always keeps 1 health; a strike that would have killed fails the tribulation instead.</li>
 * </ul>
 * Everything visible is a client particle effect (never a LightningBolt entity, no weather change, no block touched);
 * thunder is only sound. The disciple is held still for the whole breakthrough. Failure: 3 minutes of Qi Deviation
 * (weakness) and 30% of the bar lost. The state lives in {@link DiscipleData} as session data only; a tribulation that is
 * saved or unloaded after its first strike counts as failed when the disciple loads again ({@link DiscipleData#load}).
 */
public final class DiscipleBreakthrough {

	public static final int AWAKENING_TICKS = 120;
	public static final int GATHER_TICKS = 100;
	public static final int OMEN_TICKS = 120;
	public static final int STRIKE_INTERVAL = 60;
	private static final int SETTLE_TICKS = 30;
	/** Share of max health each strike takes (before pills and the server's tribulation scale). */
	public static final double STRIKE_SHARE = 0.18D;
	/** All strikes of one tribulation together take at most this share (before pills and the scale): always below 100%. */
	public static final double MAX_TOTAL_SHARE = 0.84D;
	/** The disciple may be moved this far (horizontally) from where the breakthrough began. */
	public static final double MAX_DRIFT = 6.0D;
	/** The clouds hang this high above the disciple. */
	private static final double SKY_HEIGHT = 18.0D;
	/** Failure: Qi Deviation for 3 minutes. */
	public static final int DEVIATION_TICKS = 3600;
	/** Failure: the bar keeps this share. */
	public static final double FAIL_BAR_KEEP = 0.7D;

	/** The three kinds of breakthrough. */
	public enum Kind {
		AWAKENING,
		GATHERING,
		TRIBULATION
	}

	private enum Phase { OMEN, STRIKES, SETTLING }

	/** A running breakthrough (session only). */
	static final class Trial {
		final Kind kind;
		final Rank target;
		final Vec3 start;
		final ResourceKey<Level> dimension;
		final int variant;
		final int strikeCount;
		final double chance;
		Phase phase = Phase.OMEN;
		int ticks;
		int strikesDone;
		int nextStrikeAt;
		float lowestHealth = 1.0F;
		float lastHealth;

		Trial(Kind kind, Rank target, Vec3 start, ResourceKey<Level> dimension, int variant, int strikeCount, double chance) {
			this.kind = kind;
			this.target = target;
			this.start = start;
			this.dimension = dimension;
			this.variant = variant;
			this.strikeCount = strikeCount;
			this.chance = chance;
			this.nextStrikeAt = OMEN_TICKS;
		}
	}

	// ================================================================== rules (pure)

	/** Which breakthrough leads out of {@code current} (a bottleneck that is not the end of the path). */
	public static Kind kindFor(Rank current) {
		if (current.realm().isMortal()) {
			return Kind.AWAKENING;
		}
		return current.next().realm() == Realm.FOUNDATION ? Kind.GATHERING : Kind.TRIBULATION;
	}

	/** Strikes of the mini tribulation into {@code target}: {@code 2 + target realm / 2}. */
	public static int strikeCount(Rank target) {
		return 2 + target.realm().ordinal() / 2;
	}

	/** Share of max health one strike of the running (or the next) tribulation takes, with the pills and the server scale. */
	public static double strikeShare(DiscipleData data) {
		Trial t = data.trial();
		if (t != null) {
			return strikeShare(data, t.target);
		}
		Rank rank = data.rank();
		Rank peak = rank.isBottleneck() ? rank : new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1);
		return strikeShare(data, peak.next());
	}

	/**
	 * Share of max health one strike into {@code target} takes: {@link #STRIKE_SHARE}, but all strikes together at most
	 * {@link #MAX_TOTAL_SHARE} (True Immortal: 6 x 14% instead of 6 x 18% = 108%, a certain failure), then the pills
	 * already taken and the server scale.
	 */
	public static double strikeShare(DiscipleData data, Rank target) {
		double base = Math.min(STRIKE_SHARE, MAX_TOTAL_SHARE / Math.max(1, strikeCount(target)));
		return base * data.nextTribulationFactor() * Math.max(0.0D, KultivatorConfig.get().tribulationDamageScale);
	}

	/** Every strike into {@code target} together, as a share of max health, before any outside help (Hu Fa, formations...). */
	public static double totalShare(DiscipleData data, Rank target) {
		return strikeCount(target) * strikeShare(data, target);
	}

	/** Qi Refining -> Foundation: {@code 0.7 + (grade - 2) x 0.05} plus pill bonuses, 5%..98%. */
	public static double chance(DiscipleData data) {
		return chance(null, data);
	}

	/** As {@link #chance(DiscipleData)} plus the foundationChance hooks of the loaded disciple (DESIGN 20.2.6). */
	public static double chance(@Nullable DiscipleEntity entity, DiscipleData data) {
		double chance = 0.7D + (data.rootGrade().ordinal() - 2) * 0.05D + data.nextBreakthroughBonus();
		if (entity != null) {
			chance += DiscipleHooks.foundationChance(entity);
		}
		return Math.max(0.05D, Math.min(0.98D, chance));
	}

	public static boolean active(DiscipleEntity entity) {
		return entity.data().breakingThrough();
	}

	// ================================================================== start / tick

	/**
	 * Begins the breakthrough. Every requirement must already be checked (see DiscipleCultivation.startBreakthrough and,
	 * for a disciple breaking through on its own, {@code startBreakthroughSelf}, DESIGN 39.5.2). {@code master} may be null
	 * (the master offline or elsewhere): then no chat line is sent; the rules, omen, strikes, endings and the
	 * {@code BreakthroughListener}s are exactly the same.
	 */
	static void start(DiscipleEntity entity, ServerLevel level, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		Rank current = data.rank();
		Rank target = current.next();
		Kind kind = kindFor(current);
		int variant = Tribulation.variant(level);
		Trial trial = new Trial(kind, target, entity.position(), level.dimension(), variant,
				kind == Kind.TRIBULATION ? strikeCount(target) : 0, kind == Kind.GATHERING ? chance(entity, data) : 1.0D);
		trial.lastHealth = entity.getHealth();
		data.setTrial(trial);
		holdStill(entity);
		Component name = name(entity);
		MutableComponent targetName = target.displayName().withColor(target.realm().color());
		switch (kind) {
			case AWAKENING -> {
				Fx.send(level, entity.position(), FxType.AWAKENING, data.rootColor(), AWAKENING_TICKS, 0.0F);
				sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 0.7F);
				sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.6F);
				if (master != null) {
					Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.awakening_start", name).withColor(0x8FE3C8));
				}
			}
			case GATHERING -> {
				Fx.send(level, entity.position(), FxType.BREAKTHROUGH_START, target.realm().ordinal(), GATHER_TICKS, 0.0F);
				sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 0.9F);
				if (master != null) {
					Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.gathering_start", name, targetName,
							percent(trial.chance)).withColor(0x8FE3C8));
				}
			}
			case TRIBULATION -> {
				sendOmen(level, trial, OMEN_TICKS);
				rumble(level, entity.position(), variant, entity.getRandom());
				if (master != null) {
					Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.omen." + variant, name, targetName,
							Component.literal(String.valueOf(trial.strikeCount)).withStyle(ChatFormatting.YELLOW)).withStyle(variantColor(variant)));
				}
			}
		}
		entity.refreshSynced();
		DiscipleHooks.breakthroughStarted(entity, kind, target);
	}

	/** Every server tick of the disciple (cheap when no breakthrough runs). */
	static void tick(DiscipleEntity entity, ServerLevel level, DiscipleData data) {
		Trial t = data.trial();
		if (t == null) {
			return;
		}
		if (!entity.isAlive() || entity.isDeadOrDying()) {
			data.setTrial(null);
			return;
		}
		t.ticks++;
		holdStill(entity);
		double dx = entity.getX() - t.start.x;
		double dz = entity.getZ() - t.start.z;
		boolean away = !level.dimension().equals(t.dimension) || dx * dx + dz * dz > MAX_DRIFT * MAX_DRIFT;
		switch (t.kind) {
			case AWAKENING -> {
				// The awakening is never interrupted by damage; only being carried off stops it.
				if (away) {
					cancel(entity, level, "kultivator.disciplecult.bt.cancel_moved");
				} else if (t.ticks >= AWAKENING_TICKS) {
					succeed(entity, level, data, t);
				}
			}
			case GATHERING -> {
				float health = entity.getHealth();
				if (away) {
					cancel(entity, level, "kultivator.disciplecult.bt.cancel_moved");
				} else if (health < t.lastHealth - 0.01F) {
					cancel(entity, level, "kultivator.disciplecult.bt.cancel_hurt");
				} else if (t.ticks >= GATHER_TICKS) {
					if (entity.getRandom().nextDouble() < t.chance) {
						succeed(entity, level, data, t);
					} else {
						fail(entity, level, data, t, "kultivator.disciplecult.bt.fail_chance");
					}
				} else {
					t.lastHealth = Math.max(t.lastHealth, health);
				}
			}
			case TRIBULATION -> tickTribulation(entity, level, data, t, away);
		}
	}

	private static void tickTribulation(DiscipleEntity entity, ServerLevel level, DiscipleData data, Trial t, boolean away) {
		if (t.phase == Phase.OMEN) {
			if (away) {
				cancel(entity, level, "kultivator.disciplecult.bt.cancel_left");
				return;
			}
			if (t.ticks % 20 == 0) {
				sendOmen(level, t, Math.max(0, OMEN_TICKS - t.ticks));
			}
			if (t.ticks % 40 == 0) {
				rumble(level, entity.position(), t.variant, entity.getRandom());
			}
			if (t.ticks >= OMEN_TICKS) {
				t.phase = Phase.STRIKES;
				t.nextStrikeAt = t.ticks;
			}
		}
		if (t.phase == Phase.STRIKES) {
			if (away) {
				if (t.strikesDone > 0) {
					fail(entity, level, data, t, "kultivator.disciplecult.bt.fail_fled");
				} else {
					cancel(entity, level, "kultivator.disciplecult.bt.cancel_left");
				}
				return;
			}
			trackHealth(entity, t);
			if (t.ticks >= t.nextStrikeAt) {
				if (!strike(entity, level, data, t)) {
					return;
				}
				t.nextStrikeAt = t.ticks + STRIKE_INTERVAL;
				if (t.strikesDone >= t.strikeCount) {
					t.phase = Phase.SETTLING;
					t.nextStrikeAt = t.ticks + SETTLE_TICKS;
				}
			} else if (t.nextStrikeAt - t.ticks == STRIKE_INTERVAL / 2) {
				// Keep the clouds alive and rumbling between strikes.
				sendOmen(level, t, 0);
				rumble(level, entity.position(), t.variant, entity.getRandom());
			}
		} else if (t.phase == Phase.SETTLING) {
			trackHealth(entity, t);
			if (t.ticks >= t.nextStrikeAt) {
				succeed(entity, level, data, t);
			}
		}
	}

	/** One strike. False when the tribulation ended with it (a strike that would have killed). */
	private static boolean strike(DiscipleEntity entity, ServerLevel level, DiscipleData data, Trial t) {
		t.strikesDone++;
		int number = t.strikesDone;
		boolean last = number >= t.strikeCount;
		Vec3 feet = entity.position();
		Fx.send(level, feet, feet.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_STRIKE, t.variant, number, last ? 2.0F : 1.0F);
		strikeSounds(level, feet, t.variant, last, entity.getRandom());
		ServerPlayer master = entity.owner();
		if (master != null && master.level() == level && master.distanceToSqr(entity) < 64.0D * 64.0D) {
			Msg.actionBar(master, Component.translatable("kultivator.disciplecult.bt.strike", number, t.strikeCount, name(entity))
					.withStyle(last ? ChatFormatting.GOLD : variantColor(t.variant)));
		}
		// Phase-6 hooks: a strike may be taken over (Tahan Petir) or softened (Hu Fa, foundations...; floored at 0.5).
		boolean skipped = DiscipleHooks.strikeSkipped(entity, t.target, number);
		float amount = skipped ? 0.0F : (float) Math.max(0.0D, entity.getMaxHealth() * strikeShare(data) * DiscipleHooks.strikeFactor(entity, t.target, number));
		float pool = entity.getHealth() + entity.getAbsorptionAmount();
		boolean wouldKill = !skipped && amount >= pool;
		// Never lethal: the hit is clamped so at least 1 health is left.
		float dealt = Math.min(amount, pool - 1.0F);
		if (dealt > 0.0F) {
			entity.hurtServer(level, Tribulation.damageSource(level), dealt);
		}
		if (entity.isAlive() && entity.getHealth() < 1.0F) {
			entity.setHealth(1.0F);
		}
		trackHealth(entity, t);
		DiscipleHooks.breakthroughStrike(entity, number, t.strikeCount);
		if (wouldKill) {
			fail(entity, level, data, t, "kultivator.disciplecult.bt.fail_strike");
			return false;
		}
		return true;
	}

	private static void trackHealth(DiscipleEntity entity, Trial t) {
		float max = Math.max(1.0F, entity.getMaxHealth());
		t.lowestHealth = Math.min(t.lowestHealth, Math.max(0.0F, entity.getHealth() / max));
	}

	// ================================================================== endings

	/** Ends the running breakthrough without any result (moved away, debug). True when one was running. */
	public static boolean cancel(DiscipleEntity entity, ServerLevel level, @Nullable String messageKey) {
		DiscipleData data = entity.data();
		Trial t = data.trial();
		if (t == null) {
			return false;
		}
		data.setTrial(null);
		stopVisuals(level, entity, t, false);
		ServerPlayer master = entity.owner();
		if (master != null && messageKey != null) {
			Msg.chat(master, Component.translatable(messageKey, name(entity)).withStyle(ChatFormatting.GRAY));
		}
		entity.refreshSynced();
		DiscipleHooks.breakthroughCancelled(entity, t.kind);
		return true;
	}

	private static void succeed(DiscipleEntity entity, ServerLevel level, DiscipleData data, Trial t) {
		data.setTrial(null);
		RandomSource random = entity.getRandom();
		Rank before = data.rank();
		boolean newRealm = t.target.realm() != before.realm();
		data.setRank(t.target);
		data.setXp(0.0D);
		data.addBreakthrough();

		double score = t.kind == Kind.TRIBULATION
				? random.nextDouble() * 0.5D + t.lowestHealth * 0.3D + data.rootGrade().ordinal() * 0.05D
				: random.nextDouble() * 0.7D + data.rootGrade().ordinal() * 0.05D;
		score += DiscipleHooks.qualityBonus(entity, t.target);
		BreakthroughQuality quality = BreakthroughQuality.fromScore(score);
		BreakthroughQuality floor = DiscipleHooks.qualityFloor(entity, t.target);
		if (floor != null && floor.ordinal() > quality.ordinal()) {
			quality = floor;
		}
		BreakthroughQuality old = data.quality(t.target.realm());
		if (newRealm || old == null || quality.ordinal() > old.ordinal()) {
			data.setQuality(t.target.realm(), quality);
		}
		Dao dao = data.dao();
		if (dao != null && quality.comprehensionBonus() > 0.0D) {
			data.addComprehension(DiscipleCultivation.comprehensionGain(data, dao, quality.comprehensionBonus()));
		}
		data.setNextBreakthroughBonus(0.0D);
		data.setNextTribulationFactor(1.0D);

		if (t.kind == Kind.TRIBULATION) {
			Fx.send(level, t.start, t.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_END, t.variant, 1, 0.0F);
		}
		// Stored Qi flows into the new rank (stage-ups announced, no extra effects).
		double stored = data.storedQi();
		data.setStoredQi(0.0D);
		int stageUps = DiscipleCultivation.distribute(data, stored);

		entity.refreshSynced();
		entity.setHealth(entity.getMaxHealth());
		data.setQi(DiscipleCultivation.maxQi(data));

		Vec3 pos = entity.position();
		Fx.send(level, pos, FxType.BREAKTHROUGH_SUCCESS, data.realm().ordinal(), quality.ordinal(), data.rank().step());
		sound(level, pos, SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F);
		sound(level, pos, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.1F);
		sound(level, pos, SoundEvents.BELL_RESONATE, 0.8F, 1.2F);
		DiscipleHooks.breakthroughEnded(entity, true, t.kind);

		ServerPlayer master = entity.owner();
		if (master == null) {
			return;
		}
		Component name = name(entity);
		Rank rank = data.rank();
		Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.success", name, rank.displayName().withColor(rank.realm().color()),
				quality.displayName().copy().withColor(quality.color())).withColor(0xFFD54A));
		if (stored >= 1.0D && stored - data.storedQi() >= 1.0D) {
			Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.stored_flow", name,
					Msg.number(stored - data.storedQi())).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (stageUps > 0) {
			Msg.chat(master, Component.translatable("kultivator.disciplecult.stage_up", name,
					rank.displayName().withColor(rank.realm().color())).withStyle(ChatFormatting.AQUA));
		}
		if (t.target.realm() == Realm.QI_REFINING) {
			Advancements.grant(master, "disciple_awakened");
		}
		if (t.target.realm().atLeast(Realm.GOLDEN_CORE)) {
			Advancements.grant(master, "disciple_golden_core");
			if (data.dao() == null) {
				Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.choose_dao", name).withColor(0xFFE27A));
			}
		}
		DiscipleCultivation.notifyBottleneck(entity, master, data);
	}

	private static void fail(DiscipleEntity entity, ServerLevel level, DiscipleData data, Trial t, String messageKey) {
		data.setTrial(null);
		stopVisuals(level, entity, t, true);
		data.setXp(data.xp() * FAIL_BAR_KEEP);
		data.setDeviationTicks(DEVIATION_TICKS);
		data.addFailedBreakthrough();
		data.setNextBreakthroughBonus(0.0D);
		data.setNextTribulationFactor(1.0D);
		Vec3 pos = entity.position();
		Fx.send(level, pos, FxType.DEVIATION, 0, 0, 1.0F);
		sound(level, pos, SoundEvents.WITHER_AMBIENT, 0.5F, 1.4F);
		sound(level, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 0.4F);
		entity.refreshSynced();
		DiscipleHooks.breakthroughEnded(entity, false, t.kind);
		ServerPlayer master = entity.owner();
		if (master != null) {
			Component name = name(entity);
			Msg.chat(master, Component.translatable(messageKey, name).withStyle(ChatFormatting.RED));
			Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.deviation", name,
					String.valueOf(DEVIATION_TICKS / 20)).withColor(0xFF8A7A));
		}
	}

	/** Stops whatever client effect the breakthrough was showing. */
	private static void stopVisuals(ServerLevel level, DiscipleEntity entity, Trial t, boolean failed) {
		switch (t.kind) {
			case AWAKENING -> Fx.send(level, t.start, FxType.AWAKENING, entity.data().rootColor(), 0, 0.0F);
			case GATHERING -> Fx.send(level, t.start, FxType.BREAKTHROUGH_START, t.target.realm().ordinal(), 0, 0.0F);
			case TRIBULATION -> {
				Fx.send(level, t.start, t.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_END, t.variant, 0, 0.0F);
				if (failed) {
					sound(level, t.start, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 0.4F);
				}
			}
		}
	}

	// ================================================================== helpers

	/** Keeps the disciple where it is: no path, no sideways motion, no target. */
	private static void holdStill(DiscipleEntity entity) {
		entity.getNavigation().stop();
		entity.setTarget(null);
		Vec3 motion = entity.getDeltaMovement();
		if (motion.x != 0.0D || motion.z != 0.0D || motion.y > 0.0D) {
			entity.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
		}
	}

	/** The omen packet; {@code ticksLeft} is doubled because the client grows the clouds over a 12 s scale. */
	private static void sendOmen(ServerLevel level, Trial t, int ticksLeft) {
		Fx.send(level, t.start, t.start.add(0.0D, SKY_HEIGHT, 0.0D), FxType.TRIBULATION_OMEN, t.variant, ticksLeft * 2, 0.0F);
	}

	/** Low, distant thunder (sound only) and a variant voice now and then. */
	private static void rumble(ServerLevel level, Vec3 at, int variant, RandomSource random) {
		Vec3 sky = at.add(0.0D, 10.0D, 0.0D);
		sound(level, sky, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.25F + random.nextFloat() * 0.1F, 0.5F + random.nextFloat() * 0.2F);
		if (random.nextInt(3) == 0) {
			switch (variant) {
				case Tribulation.VARIANT_NETHER -> sound(level, sky, SoundEvents.BLAZE_AMBIENT, 0.3F, 0.5F);
				case Tribulation.VARIANT_END -> sound(level, sky, SoundEvents.ENDER_DRAGON_GROWL, 0.2F, 0.7F);
				default -> sound(level, sky, SoundEvents.BEACON_AMBIENT, 0.4F, 0.6F);
			}
		}
	}

	private static void strikeSounds(ServerLevel level, Vec3 at, int variant, boolean last, RandomSource random) {
		sound(level, at.add(0.0D, 3.0D, 0.0D), SoundEvents.LIGHTNING_BOLT_THUNDER, last ? 2.2F : 1.6F, 0.85F + random.nextFloat() * 0.2F);
		sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, last ? 1.2F : 0.8F, 0.6F + random.nextFloat() * 0.2F);
		switch (variant) {
			case Tribulation.VARIANT_NETHER -> sound(level, at, SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F);
			case Tribulation.VARIANT_END -> sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.5F);
			default -> {
				if (last) {
					sound(level, at, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.8F, 0.7F);
				}
			}
		}
	}

	private static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
	}

	static ChatFormatting variantColor(int variant) {
		return switch (variant) {
			case Tribulation.VARIANT_NETHER -> ChatFormatting.RED;
			case Tribulation.VARIANT_END -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.DARK_AQUA;
		};
	}

	private static Component name(DiscipleEntity entity) {
		return entity.getName().copy().withColor(0xF2E6C8);
	}

	private static String percent(double fraction) {
		return Math.round(fraction * 100.0D) + "%";
	}

	private DiscipleBreakthrough() {
	}
}
