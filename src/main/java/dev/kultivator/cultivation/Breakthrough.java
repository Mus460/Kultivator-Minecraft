package dev.kultivator.cultivation;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sparring;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Breakthroughs (DESIGN section 3): the Mortal awakening, the Qi Refining chance roll, and from Foundation up an optional
 * trial (heart demon, phase 3) followed by the Heavenly Tribulation. Also the result: quality, rewards, announcements,
 * and on failure a Qi Deviation (or, in Hardcore, the loss of a realm).
 */
public final class Breakthrough {

	/** Lets a later module (the heart demon) put a trial before some tribulations. */
	public interface TrialProvider {
		/** True when the breakthrough into {@code target} needs a trial first. */
		boolean needsTrial(ServerPlayer player, Rank target);

		/** Starts the trial; the provider must call {@link Breakthrough#onTrialFinished} when it is decided. */
		void startTrial(ServerPlayer player, Rank target);
	}

	/**
	 * A phase-7 breakthrough-quality bonus (DESIGN 19.2.6: Batu Roh di Hambatan, Menantang Langit, Hu Fa Tribulasi,
	 * Ujian Fondasi). {@link #bonus} must be pure (the Persiapan panel asks it every few seconds); {@link #commit} runs
	 * once right after a real roll of that breakthrough, e.g. to spend the stones the bonus counted.
	 */
	public interface QualityBonus {
		/** Added to the quality score of the breakthrough into {@code target} (summed with the others, then clamped). */
		double bonus(ServerPlayer p, CultivationData d, Rank target);

		/** The breakthrough into {@code target} succeeded and its quality was rolled with this bonus. */
		default void commit(ServerPlayer p, CultivationData d, Rank target) {
		}
	}

	/**
	 * Told about the stages of every breakthrough (DESIGN 23.5.1, spine 7b), for presentation only; {@code kind} is one of
	 * the {@code KIND_*} constants. Listeners must never change the breakthrough; an exception is logged and ignored.
	 */
	public interface Listener {
		/** A breakthrough from {@code from} into {@code target} began (awakening, qi gathering, or trial / tribulation). */
		default void onBegin(ServerPlayer p, Rank from, Rank target, int kind, boolean test) {
		}

		/** The breakthrough succeeded: the rank is already {@code now} and the quality is applied. */
		default void onSuccess(ServerPlayer p, Rank before, Rank now, BreakthroughQuality q, int kind, boolean test) {
		}

		/** The breakthrough failed; {@code hardcoreDrop} when the player fell one realm instead of a Qi Deviation. */
		default void onFail(ServerPlayer p, boolean hardcoreDrop, boolean test) {
		}
	}

	/** The breakthrough kinds of {@link Listener} (the private {@code Kind} ordinals). */
	public static final int KIND_AWAKENING = 0, KIND_CHANCE = 1, KIND_TRIBULATION = 2, KIND_FORCED = 3;

	/** The sum of all {@link QualityBonus} providers is clamped to this range (the {@code DiscipleHooks} range). */
	public static final double QUALITY_BONUS_MIN = -0.10D;
	public static final double QUALITY_BONUS_MAX = 0.40D;

	/** Length of the awakening cinematic. */
	public static final int AWAKENING_TICKS = 120;
	/** Length of the qi gathering before the Qi Refining -> Foundation chance roll. */
	public static final int GATHERING_TICKS = 100;
	/** A trial that never reports back is dropped (no penalty) after this long. */
	private static final int TRIAL_TIMEOUT = 20 * 180;
	/** Awakening and gathering are interrupted when the player moves further than 6 blocks (horizontally) from the start. */
	private static final double STILL_DISTANCE_SQ = 36.0D;
	/** Breakthrough quality bonus per qi gathering formation tier (read when the breakthrough begins). */
	public static final double FORMATION_BONUS = 0.03D;
	/** Breakthrough bonus of a spiritual site (multiplier above 1, read when the breakthrough begins). */
	public static final double SITE_BONUS = 0.05D;

	private enum Phase { AWAKENING, GATHERING, TRIAL, TRIBULATION }

	/** The ways a breakthrough can succeed (they decide the quality roll and the rewards). */
	private enum Kind { AWAKENING, CHANCE, TRIBULATION, FORCED }

	private static final class Session {
		final Phase phase;
		final Rank from;
		final Rank target;
		final Vec3 start;
		final double siteBonus;
		final boolean test;
		int ticks;
		boolean hurt;
		double chance;
		/** Dimension the breakthrough began in (null for sessions made by commands). */
		@Nullable ResourceKey<Level> dimension;

		Session(Phase phase, Rank from, Rank target, Vec3 start, double siteBonus, boolean test) {
			this.phase = phase;
			this.from = from;
			this.target = target;
			this.start = start;
			this.siteBonus = siteBonus;
			this.test = test;
		}

		Session with(Phase next) {
			Session copy = new Session(next, from, target, start, siteBonus, test);
			copy.chance = chance;
			copy.dimension = dimension;
			return copy;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static @Nullable TrialProvider trialProvider;
	private static final List<QualityBonus> QUALITY_BONUS = new CopyOnWriteArrayList<>();
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && !blocked && baseDamage > 0.0F) {
				Session session = SESSIONS.get(player.getUUID());
				if (session != null) {
					session.hurt = true;
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
	}

	/** Registers the trial provider (phase 3 heart demon). Null removes it. */
	public static void setTrialProvider(@Nullable TrialProvider provider) {
		trialProvider = provider;
	}

	/** Adds a breakthrough-quality bonus (DESIGN 19.2.6); register from a module's {@code init()}. */
	public static void addQualityBonusProvider(QualityBonus provider) {
		QUALITY_BONUS.add(provider);
	}

	/** Adds a breakthrough listener (DESIGN 23.5.1); register from a module's {@code init()}. */
	public static void addListener(Listener listener) {
		LISTENERS.add(listener);
	}

	private static void fireBegin(ServerPlayer p, Rank from, Rank target, Kind kind, boolean test) {
		for (Listener listener : LISTENERS) {
			try {
				listener.onBegin(p, from, target, kind.ordinal(), test);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Breakthrough listener failed at the begin", exception);
			}
		}
	}

	private static void fireSuccess(ServerPlayer p, Rank before, Rank now, BreakthroughQuality quality, Kind kind, boolean test) {
		for (Listener listener : LISTENERS) {
			try {
				listener.onSuccess(p, before, now, quality, kind.ordinal(), test);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Breakthrough listener failed at the success", exception);
			}
		}
	}

	private static void fireFail(ServerPlayer p, boolean hardcoreDrop, boolean test) {
		for (Listener listener : LISTENERS) {
			try {
				listener.onFail(p, hardcoreDrop, test);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Breakthrough listener failed at the failure", exception);
			}
		}
	}

	/**
	 * The phase-7 quality bonus of the breakthrough into {@code target}: the sum of every provider, clamped to
	 * {@link #QUALITY_BONUS_MIN}..{@link #QUALITY_BONUS_MAX}. Pure (0 without providers); the awakening never uses it.
	 */
	public static double qualityBonus(ServerPlayer p, CultivationData d, Rank target) {
		if (QUALITY_BONUS.isEmpty()) {
			return 0.0D;
		}
		double sum = 0.0D;
		for (QualityBonus provider : QUALITY_BONUS) {
			double value = provider.bonus(p, d, target);
			if (Double.isFinite(value)) {
				sum += value;
			}
		}
		return Math.max(QUALITY_BONUS_MIN, Math.min(QUALITY_BONUS_MAX, sum));
	}

	/** Every provider's {@code commit} once, after the quality of a real breakthrough into {@code target} was rolled. */
	private static void commitQualityBonus(ServerPlayer p, CultivationData d, Rank target) {
		for (QualityBonus provider : QUALITY_BONUS) {
			try {
				provider.commit(p, d, target);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Breakthrough quality bonus commit failed", exception);
			}
		}
	}

	/**
	 * Starts a test tribulation into {@code target} (no rank change, never lethal, the failure is only a deviation), as
	 * {@code debug tribulation test} does for the next realm. False with a message to the player when busy or dead.
	 */
	public static boolean beginTest(ServerPlayer player, Rank target) {
		CultivationData data = Cultivators.get(player);
		if (isBusy(player) || data.breakingThrough()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.busy"));
		}
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.dead"));
		}
		begin(player, data, target, true);
		return true;
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		if (data.pendingFailure() && player.isAlive() && !player.isDeadOrDying() && !SESSIONS.containsKey(player.getUUID())
				&& !Tribulation.isActive(player)) {
			// Left the world during a trial or after the first strike: the failure catches up now (deaths are handled
			// in onRespawn instead, dead players are skipped here).
			data.setPendingFailure(false);
			Msg.chat(player, Component.translatable("kultivator.breakthrough.left.backlash").withStyle(ChatFormatting.DARK_RED));
			fail(player, data, false, true, false);
			return;
		}
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || player.isDeadOrDying()) {
			return;
		}
		session.ticks++;
		switch (session.phase) {
			case AWAKENING -> tickAwakening(player, data, session);
			case GATHERING -> tickGathering(player, data, session);
			case TRIAL -> {
				if (session.ticks > TRIAL_TIMEOUT) {
					endWithoutResult(player, "kultivator.breakthrough.cancel.trial_timeout");
				}
			}
			case TRIBULATION -> {
				if (!Tribulation.isActive(player)) {
					// The tribulation vanished without reporting (should not happen): free the player.
					endWithoutResult(player, null);
				}
			}
		}
	}

	/** Tries to start a breakthrough; tells the player why not when it cannot. True when one started. */
	public static boolean attempt(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Rank rank = data.rank();
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.dead"));
		}
		if (isBusy(player) || data.breakingThrough()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.busy"));
		}
		if (Sparring.inDuel(player)) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.sparring"));
		}
		if (dev.kultivator.alam.Alam.blocked(player)) {
			// Phase 8 (DESIGN 25.2 item 12): never inside a secret realm or a run.
			return refuse(player, dev.kultivator.alam.Alam.refusal());
		}
		if (dev.kultivator.purba.Purba.inCalamity(player) || dev.kultivator.abadi.Abadi.inAscension(player)) {
			// Phase 9 (DESIGN 26.14 item 13): never during a star calamity or the ascension ritual.
			return refuse(player, Component.translatable("kultivator.spine9.refuse.busy"));
		}
		if (HeartDemons.inTrial(player)) {
			// A pressure Heart Demon is still fighting: it must be faced first.
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.heart_demon"));
		}
		if (data.deviating()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.deviation",
					Component.literal(String.valueOf(Math.max(1, data.deviationTicks() / 20))).withStyle(ChatFormatting.RED)));
		}
		if (rank.isMax()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.max"));
		}
		if (!rank.isBottleneck()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.not_peak",
					new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1).displayName().withColor(rank.realm().color())));
		}
		if (!data.barFull()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.not_full",
					Msg.number(data.xp()), Msg.number(rank.xpRequired())));
		}
		if (player.isPassenger()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.riding"));
		}
		if (data.flyingSword() || player.isFallFlying() || !player.onGround() && !player.isInWater() && !player.isCreative()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.airborne"));
		}
		if (rank.realm().isMortal() && !data.rootTested()) {
			return refuse(player, Component.translatable("kultivator.breakthrough.refuse.root",
					Component.literal("/cultivate root test").withStyle(ChatFormatting.YELLOW)));
		}
		if (rank.realm().isMortal() && SpiritRoots.ceremonyPending(player)) {
			// The root is saved when the testing ceremony starts; the awakening waits until it has been revealed.
			return refuse(player, Component.translatable("kultivator.roots.ceremony.running"));
		}
		begin(player, data, rank.next(), false);
		return true;
	}

	/** True while a breakthrough of the player is running (including its trial and tribulation). */
	public static boolean isBusy(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID()) || Tribulation.isActive(player);
	}

	/** The trial asked for by the {@link TrialProvider} is decided: passed starts the tribulation, failed is a failure. */
	public static void onTrialFinished(ServerPlayer player, boolean passed) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || session.phase != Phase.TRIAL) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		if (passed) {
			data.setTrialPassed(session.target.realm(), true);
			Msg.chat(player, Component.translatable("kultivator.breakthrough.trial.passed").withStyle(ChatFormatting.GOLD));
			startTribulation(player, session);
		} else {
			Msg.chat(player, Component.translatable("kultivator.breakthrough.trial.failed").withStyle(ChatFormatting.RED));
			SESSIONS.remove(player.getUUID());
			fail(player, data, session.test, false, false);
		}
	}

	/**
	 * Logging out during the omen, the awakening or the gathering costs nothing. Once a trial runs or the first strike
	 * has fallen it counts as a failure (DESIGN section 3: leaving after the first strike fails), applied by
	 * {@link #tick} when the player is back. The protectors are discarded either way.
	 */
	public static void onLeave(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		CultivationData data = Cultivators.get(player);
		boolean committed = session != null && !session.test && (session.phase == Phase.TRIAL
				|| session.phase == Phase.TRIBULATION && Tribulation.strikesDone(player) > 0);
		if (committed && player.isAlive() && !player.isDeadOrDying()) {
			data.setPendingFailure(true);
		}
		stopFx(player, session);
		SESSIONS.remove(player.getUUID());
		Tribulation.onLeave(player);
		data.setBreakingThrough(false);
	}

	/** After death: applies a failure left over from a tribulation (or trial) the player died in. */
	public static void onRespawn(ServerPlayer player, CultivationData data) {
		SESSIONS.remove(player.getUUID());
		if (!data.pendingFailure()) {
			return;
		}
		data.setPendingFailure(false);
		Msg.chat(player, Component.translatable("kultivator.breakthrough.died.backlash").withStyle(ChatFormatting.DARK_RED));
		fail(player, data, false, true, false);
	}

	/** Called by {@link Tribulation} when the tribulation is over. */
	static void onTribulationEnd(ServerPlayer player, Tribulation.Result result, float lowestHealth, int variant, boolean test) {
		Session session = SESSIONS.remove(player.getUUID());
		CultivationData data = Cultivators.get(player);
		boolean isTest = test || session != null && session.test;
		switch (result) {
			case SUCCESS -> {
				if (isTest) {
					Msg.chat(player, Component.translatable("kultivator.breakthrough.test.success").withStyle(ChatFormatting.GREEN));
					finishQuietly(player, data);
				} else if (session == null || !data.rank().equals(session.from)) {
					// The rank was changed meanwhile (commands): nothing to complete.
					finishQuietly(player, data);
				} else {
					succeed(player, data, session, Kind.TRIBULATION, lowestHealth, variant, null);
				}
			}
			case FAILED, FLED -> {
				if (isTest) {
					Msg.chat(player, Component.translatable("kultivator.breakthrough.test.failed").withStyle(ChatFormatting.RED));
					finishQuietly(player, data);
					Deviation.apply(player, 1.0D);
					fireFail(player, false, true);
				} else {
					fail(player, data, false, true, false);
				}
			}
			case CANCELLED -> finishQuietly(player, data);
		}
	}

	/** Adds {@code breakthrough} to /cultivate and {@code breakthrough force|cancel}, {@code tribulation test} to debug. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("breakthrough").executes(ctx -> attempt(ctx.getSource().getPlayerOrException()) ? 1 : 0));

		debug.then(Commands.literal("breakthrough")
				.then(Commands.literal("force")
						.executes(ctx -> force(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> force(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("cancel")
						.executes(ctx -> cancelCommand(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> cancelCommand(ctx, EntityArgument.getPlayer(ctx, "player"))))));
		debug.then(Commands.literal("tribulation")
				.then(Commands.literal("test")
						.executes(ctx -> testCommand(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> testCommand(ctx, EntityArgument.getPlayer(ctx, "player"))))));
	}

	// ================================================================== flow

	private static void begin(ServerPlayer player, CultivationData data, Rank target, boolean test) {
		// Read the spiritual site and the qi gathering formation before meditation stops (stopping resets them). Without
		// a meditation running they are looked up where the player stands (read-only).
		ServerLevel level = player.level();
		double site = data.meditating() ? data.siteMultiplier() : SpiritSites.multiplier(player);
		int formation = data.meditating() ? data.formationTier() : QiFormations.tier(level, player.blockPosition().below());
		double siteBonus = (site > 1.01D ? SITE_BONUS : 0.0D) + FORMATION_BONUS * formation;
		// Flag first, so stopping the meditation stays quiet (the breakthrough tells its own story).
		data.setBreakingThrough(true);
		Meditation.stop(player);
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);
		Rank from = data.rank();
		Vec3 pos = player.position();

		if (!test && from.realm().isMortal()) {
			Session session = new Session(Phase.AWAKENING, from, target, pos, siteBonus, false);
			session.dimension = level.dimension();
			SESSIONS.put(player.getUUID(), session);
			Fx.send(level, pos, FxType.AWAKENING, Element.mainColor(data.rootElements()), AWAKENING_TICKS, 0.0F);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
			Msg.chat(player, Component.translatable("kultivator.breakthrough.awakening.start").withStyle(ChatFormatting.AQUA));
			fireBegin(player, from, target, Kind.AWAKENING, false);
			return;
		}
		if (!test && from.realm() == Realm.QI_REFINING) {
			Session session = new Session(Phase.GATHERING, from, target, pos, siteBonus, false);
			session.dimension = level.dimension();
			session.chance = chance(data, siteBonus);
			SESSIONS.put(player.getUUID(), session);
			Fx.send(level, pos, FxType.BREAKTHROUGH_START, target.realm().ordinal(), GATHERING_TICKS, 0.0F);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.7F);
			Msg.chat(player, Component.translatable("kultivator.breakthrough.gathering.start",
					Component.literal(Math.round(session.chance * 100.0D) + "%").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.AQUA));
			fireBegin(player, from, target, Kind.CHANCE, false);
			return;
		}

		Session session = new Session(Phase.TRIAL, from, target, pos, siteBonus, test);
		session.dimension = level.dimension();
		SESSIONS.put(player.getUUID(), session);
		Fx.send(level, pos, FxType.BREAKTHROUGH_START, target.realm().ordinal(), 60, 0.0F);
		fireBegin(player, from, target, Kind.TRIBULATION, test);
		TrialProvider provider = trialProvider;
		if (!test && provider != null && KultivatorConfig.get().heartDemonTrials && provider.needsTrial(player, target)) {
			provider.startTrial(player, target);
			return;
		}
		startTribulation(player, session);
	}

	private static void startTribulation(ServerPlayer player, Session session) {
		SESSIONS.put(player.getUUID(), session.with(Phase.TRIBULATION));
		if (session.test) {
			Msg.chat(player, Component.translatable("kultivator.breakthrough.test.start",
					session.target.displayName().withColor(session.target.realm().color())).withStyle(ChatFormatting.GRAY));
		}
		Tribulation.start(player, session.target, session.test);
	}

	private static void tickAwakening(ServerPlayer player, CultivationData data, Session session) {
		if (interrupted(player, session)) {
			return;
		}
		ServerLevel level = player.level();
		float progress = (float) session.ticks / AWAKENING_TICKS;
		if (session.ticks % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
					0.6F + progress * 0.6F, 0.5F + progress * 1.2F);
		}
		if (session.ticks % 30 == 15) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS,
					0.8F, 0.6F + progress);
		}
		if (session.ticks >= AWAKENING_TICKS) {
			SESSIONS.remove(player.getUUID());
			// The awakening always succeeds; its quality is rolled like any breakthrough without a tribulation.
			succeed(player, data, session, Kind.AWAKENING, 1.0F, Tribulation.variant(level), null);
		}
	}

	private static void tickGathering(ServerPlayer player, CultivationData data, Session session) {
		if (interrupted(player, session)) {
			return;
		}
		ServerLevel level = player.level();
		float progress = (float) session.ticks / GATHERING_TICKS;
		if (session.ticks % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
					0.5F + progress * 0.5F, 0.6F + progress * 0.8F);
		}
		if (session.ticks >= GATHERING_TICKS) {
			SESSIONS.remove(player.getUUID());
			if (player.getRandom().nextDouble() < session.chance) {
				succeed(player, data, session, Kind.CHANCE, 1.0F, Tribulation.variant(level), null);
			} else {
				Msg.chat(player, Component.translatable("kultivator.breakthrough.fail.chance").withStyle(ChatFormatting.RED));
				fail(player, data, false, false, true);
			}
		}
	}

	/**
	 * Awakening and gathering stop (no penalty) when the player moves more than 6 blocks away (horizontally), rides,
	 * changes dimension, or, during the gathering only, is hurt. The awakening always succeeds, so damage never breaks it.
	 */
	private static boolean interrupted(ServerPlayer player, Session session) {
		String reason = null;
		double dx = player.getX() - session.start.x;
		double dz = player.getZ() - session.start.z;
		boolean otherDimension = session.dimension != null && !player.level().dimension().equals(session.dimension);
		if (session.hurt && session.phase == Phase.GATHERING) {
			reason = "kultivator.breakthrough.cancel.hurt";
		} else if (dx * dx + dz * dz > STILL_DISTANCE_SQ || otherDimension || player.isPassenger()) {
			reason = "kultivator.breakthrough.cancel.moved";
		}
		if (reason == null) {
			return false;
		}
		stopFx(player, session);
		endWithoutResult(player, reason);
		return true;
	}

	/** Tells nearby clients to stop the awakening spiral or the gathering effect of {@code session} (if it had one). */
	private static void stopFx(ServerPlayer player, @Nullable Session session) {
		if (session != null && (session.phase == Phase.AWAKENING || session.phase == Phase.GATHERING)) {
			Fx.send(player.level(), session.start, session.phase == Phase.AWAKENING ? FxType.AWAKENING : FxType.BREAKTHROUGH_START,
					0, 0, 0.0F);
		}
	}

	/** Qi Refining -> Foundation success chance (DESIGN section 3), capped at 0.98. */
	public static double chance(CultivationData data, double siteBonus) {
		double[] parts = chanceParts(data);
		double value = parts[0] + parts[1] + parts[2] + parts[3] + siteBonus;
		return Math.max(0.05D, Math.min(0.98D, value));
	}

	/** The parts of {@link #chance} before the place bonus: {base, root, pill, Stored Qi} (pure; the forecast shows them). */
	public static double[] chanceParts(CultivationData data) {
		double required = Math.max(1.0D, data.rank().xpRequired());
		return new double[]{0.75D, (data.rootGrade().ordinal() - 2) * 0.05D, data.nextBreakthroughBonus(),
				Math.min(0.1D, data.storedQi() / required * 0.1D)};
	}

	// ================================================================== results

	private static void succeed(ServerPlayer player, CultivationData data, Session session, Kind kind, float lowestHealth, int variant,
								@Nullable BreakthroughQuality forced) {
		ServerLevel level = player.level();
		Realm before = data.realm();
		// Phase 7 (DESIGN 19.2.6): the providers' bonus is read before the rank changes (as the Persiapan panel reads it);
		// the awakening and a forced quality have no roll, so they neither use nor commit it.
		boolean rolled = kind != Kind.AWAKENING && forced == null;
		double providerBonus = rolled ? qualityBonus(player, data, session.target) : 0.0D;
		Progression.completeBreakthrough(player);
		Rank now = data.rank();
		Realm realm = now.realm();

		// The awakening keeps a Nether Star's bonus for the first real roll (it is not spent here, so it is not used either).
		double bonus = (kind == Kind.AWAKENING ? 0.0D : data.nextBreakthroughBonus()) + session.siteBonus + providerBonus;
		double roll = player.getRandom().nextDouble();
		BreakthroughQuality quality;
		if (forced != null) {
			quality = forced;
		} else if (kind == Kind.TRIBULATION) {
			quality = BreakthroughQuality.fromScore(roll * 0.5D + lowestHealth * 0.3D + data.rootGrade().ordinal() * 0.05D + bonus);
		} else {
			quality = BreakthroughQuality.fromScore(roll * 0.7D + bonus);
		}
		if (rolled) {
			commitQualityBonus(player, data, session.target);
		}
		BreakthroughQuality previous = data.quality(realm);
		if (realm != before || previous == null || quality.ordinal() > previous.ordinal()) {
			// Immortal Ascension layers stay in one realm: the best quality seen there is kept.
			data.setQuality(realm, quality);
		}
		Dao dao = data.dao();
		if (dao != null && quality.comprehensionBonus() > 0.0D) {
			// The real gain (root affinity x1.5, capped at 100) is what the player is told.
			double gained = Comprehension.grant(player, dao, quality.comprehensionBonus());
			if (gained > 0.0D) {
				Msg.chat(player, Component.translatable("kultivator.breakthrough.dao_bonus", dao.displayName().copy().withColor(dao.color()),
						Comprehension.format(gained)).withStyle(ChatFormatting.GRAY));
			}
		}
		if (kind == Kind.TRIBULATION) {
			data.addTribulationSurvived();
		}
		// Pills are only spent by the breakthrough that used them: the awakening and a forced one keep them.
		if (kind == Kind.TRIBULATION) {
			data.setNextTribulationFactor(1.0D);
		}
		if (kind == Kind.CHANCE || kind == Kind.TRIBULATION) {
			data.setNextBreakthroughBonus(0.0D);
		}
		Stats.apply(player);
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		Qi.restore(player, Qi.max(data));

		Vec3 pos = player.position();
		Fx.send(level, pos, FxType.BREAKTHROUGH_SUCCESS, realm.ordinal(), quality.ordinal(), (float) now.step());
		fireSuccess(player, session.from, now, quality, kind, session.test);
		successSounds(level, pos, kind, quality);

		MutableComponent rankName = now.displayName().withColor(realm.color());
		MutableComponent qualityName = quality.displayName().copy().withColor(quality.color());
		Msg.title(player, rankName.copy().withStyle(ChatFormatting.BOLD),
				Component.translatable("kultivator.breakthrough.success.subtitle", qualityName), 10, 70, 25);
		if (kind == Kind.AWAKENING) {
			Msg.chat(player, Component.translatable("kultivator.breakthrough.success.awakening", rankName).withStyle(ChatFormatting.AQUA));
		} else {
			Msg.chat(player, Component.translatable("kultivator.breakthrough.success.chat", rankName, qualityName).withStyle(ChatFormatting.GOLD));
		}
		if (kind != Kind.FORCED && KultivatorConfig.get().announceBreakthroughs && realm.atLeast(Realm.GOLDEN_CORE)) {
			String key = kind == Kind.TRIBULATION ? "kultivator.breakthrough.announce.tribulation" : "kultivator.breakthrough.announce";
			Msg.broadcast(level.getServer(), Component.translatable(key, player.getDisplayName(), rankName, qualityName)
					.withStyle(ChatFormatting.YELLOW));
		}

		grantAdvancements(player, kind, realm, quality, variant);
		data.setBreakingThrough(false);
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
	}

	private static void grantAdvancements(ServerPlayer player, Kind kind, Realm realm, BreakthroughQuality quality, int variant) {
		if (kind == Kind.AWAKENING || realm == Realm.QI_REFINING) {
			Advancements.grant(player, "awakening");
		}
		if (realm.atLeast(Realm.FOUNDATION)) {
			Advancements.grant(player, "first_breakthrough");
		}
		if (kind == Kind.FORCED) {
			return;
		}
		if (quality.ordinal() >= BreakthroughQuality.PERFECT.ordinal()) {
			Advancements.grant(player, "perfect_breakthrough");
		}
		if (quality == BreakthroughQuality.HEAVENLY) {
			Advancements.grant(player, "heavenly_breakthrough");
		}
		if (kind == Kind.TRIBULATION) {
			Advancements.grant(player, "first_tribulation");
			if (variant == Tribulation.VARIANT_NETHER) {
				Advancements.grant(player, "nether_tribulation");
			} else if (variant == Tribulation.VARIANT_END) {
				Advancements.grant(player, "end_tribulation");
			}
			if (realm == Realm.TRUE_IMMORTAL) {
				Advancements.grant(player, "true_immortal_tribulation");
			}
		}
	}

	private static void successSounds(ServerLevel level, Vec3 pos, Kind kind, BreakthroughQuality quality) {
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.8F);
		if (kind == Kind.TRIBULATION || quality.ordinal() >= BreakthroughQuality.PERFECT.ordinal()) {
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
		}
		if (quality == BreakthroughQuality.HEAVENLY) {
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 1.0F);
		}
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	/**
	 * A failed breakthrough: Hardcore drops one realm, otherwise a Qi Deviation and 30% of the bar is lost. A test only
	 * gets the deviation. {@code usedTribulation} / {@code usedChance} tell which pills were spent by the attempt (the
	 * Enchanted Golden Apple only by a tribulation, the Nether Star by a tribulation or the chance roll).
	 */
	private static void fail(ServerPlayer player, CultivationData data, boolean test, boolean usedTribulation, boolean usedChance) {
		ServerLevel level = player.level();
		data.setBreakingThrough(false);
		if (test) {
			Deviation.apply(player, 1.0D);
			Cultivators.syncNow(player);
			fireFail(player, false, true);
			return;
		}
		boolean hardcore = data.hardcore() || KultivatorConfig.get().hardcoreForced;
		if (hardcore) {
			// Progression tells the player which rank they fell to.
			Msg.chat(player, Component.translatable("kultivator.breakthrough.fail.hardcore").withStyle(ChatFormatting.DARK_RED));
			Progression.dropRealm(player);
			Fx.send(level, player.position(), FxType.DEVIATION, 1, 0, 1.5F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.7F, 0.5F);
		} else {
			data.setXp(data.xp() * 0.7D);
			Msg.chat(player, Component.translatable("kultivator.breakthrough.fail.lost_bar").withStyle(ChatFormatting.RED));
			Deviation.apply(player, 1.0D);
		}
		data.addFailedBreakthrough();
		data.setHeartDemonPressure(data.heartDemonPressure() + 25.0D);
		if (usedTribulation) {
			data.setNextTribulationFactor(1.0D);
		}
		if (usedTribulation || usedChance) {
			data.setNextBreakthroughBonus(0.0D);
		}
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
		fireFail(player, hardcore, false);
	}

	/** Ends a session without success or penalty. */
	private static void endWithoutResult(ServerPlayer player, @Nullable String messageKey) {
		SESSIONS.remove(player.getUUID());
		if (messageKey != null) {
			Msg.chat(player, Component.translatable(messageKey).withStyle(ChatFormatting.GRAY));
		}
		finishQuietly(player, Cultivators.get(player));
	}

	private static void finishQuietly(ServerPlayer player, CultivationData data) {
		data.setBreakingThrough(false);
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
	}

	private static void onDeath(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		stopFx(player, session);
		boolean tribulation = Tribulation.abort(player);
		if (session == null && !tribulation) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		boolean test = session != null && session.test;
		boolean counts = tribulation || session != null && (session.phase == Phase.TRIAL || session.phase == Phase.TRIBULATION);
		if (counts && !test) {
			data.setPendingFailure(true);
			Msg.chat(player, Component.translatable("kultivator.breakthrough.died").withStyle(ChatFormatting.DARK_RED));
		}
		data.setBreakingThrough(false);
		Cultivators.dirty(player);
	}

	/** Stops whatever breakthrough runs for the player, without penalty. True if something was running. */
	public static boolean cancel(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		boolean tribulation = Tribulation.abort(player);
		if (session == null && !tribulation) {
			return false;
		}
		stopFx(player, session);
		Msg.chat(player, Component.translatable("kultivator.breakthrough.cancel.command").withStyle(ChatFormatting.GRAY));
		finishQuietly(player, Cultivators.get(player));
		return true;
	}

	// ================================================================== debug commands

	private static int force(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.rank().isMax()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.breakthrough.refuse.max"));
			return 0;
		}
		SESSIONS.remove(player.getUUID());
		Tribulation.abort(player);
		Rank rank = data.rank();
		if (!rank.isBottleneck()) {
			Progression.setRank(player, new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1));
		}
		data.setXp(data.rank().xpRequired());
		Session session = new Session(Phase.TRIBULATION, data.rank(), data.rank().next(), player.position(), 0.0D, false);
		succeed(player, data, session, Kind.FORCED, 1.0F, Tribulation.variant(player.level()), BreakthroughQuality.NORMAL);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.breakthrough.force.done", player.getDisplayName())), false);
		return 1;
	}

	private static int cancelCommand(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (!cancel(player)) {
			Cultivators.get(player).setBreakingThrough(false);
			Cultivators.syncNow(player);
			ctx.getSource().sendFailure(Component.translatable("kultivator.breakthrough.cancel.none"));
			return 0;
		}
		return 1;
	}

	private static int testCommand(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
		CultivationData data = Cultivators.get(player);
		if (isBusy(player) || data.breakingThrough()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.breakthrough.refuse.busy"));
			return 0;
		}
		if (!player.isAlive() || player.isSpectator()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.breakthrough.refuse.dead"));
			return 0;
		}
		Rank rank = data.rank();
		Rank target = rank.isMax() ? rank : new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1).next();
		if (!target.realm().atLeast(Realm.GOLDEN_CORE)) {
			target = new Rank(Realm.GOLDEN_CORE, 0, 0);
		}
		begin(player, data, target, true);
		return 1;
	}

	private static boolean refuse(ServerPlayer player, MutableComponent reason) {
		Msg.chat(player, reason.withStyle(ChatFormatting.RED));
		return false;
	}

	private Breakthrough() {
	}
}
