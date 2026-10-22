package dev.kultivator.duel;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.clash.ClashClock;
import dev.kultivator.duel.clash.ClashDamage;
import dev.kultivator.duel.clash.Fighter;
import dev.kultivator.duel.clash.Fighters;
import dev.kultivator.duel.clash.Reactive;
import dev.kultivator.duel.net.DuelNet;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelEscrow;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.duel.voice.DuelVoices;
import dev.kultivator.technique.Techniques;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * Duel Pedang Kultivator, the sword duel (DESIGN 35), server module S: {@link #init} wires everything (contract C35.S1),
 * {@link #addCommands} adds {@code /kultivasi duel ...} and {@code /kultivasi uji duel ...}. The parts:
 * {@code duel.clash} (the clash layer: swings, windows, posture, locks, waves, damage), {@code duel.session} (formal
 * duels: challenges, rings, stakes, records), {@code duel.ai} (duellists that are not players), {@code duel.net}
 * (payloads). Nothing in the duel changes a block, slows the server or kills anyone in a formal duel.
 * <p>
 * Sumber: Gu Long, Juezhan Qianhou (决战前后, the Lu Xiaofeng series): Ximen Chuixue against Ye Gucheng on the roofs of
 * the Forbidden City on a full-moon night, time and place agreed beforehand; Ye's signature move "Tian Wai Fei Xian"
 * (天外飞仙) (wuxiasociety.com/ye-gucheng; en.wikipedia.org/wiki/The_Duel_of_the_Century) → formal challenges with an
 * agreed ring, the title card of names and titles, one silent decisive cut. Yan Bi Xiao Sheng, Emperor's Domination: Li
 * Qiye's unfinished sword duel with Bai Jianzhen (fan wiki summary only, chapter not verified:
 * emperors-domination.fandom.com/wiki/Bai_Jianzhen) → the draw and the postponement. Game design, not novels: Sekiro:
 * Shadows Die Twice (FromSoftware, 2019) for posture, deflects, the 危 perilous mark, the Mikiri Counter and deathblows;
 * For Honor (Ubisoft, 2017) and Mount &amp; Blade for mouse-direction attacks. No number of this mod is copied from
 * any game.
 */
public final class Duel {

	/** The ALLOW_DAMAGE phase of the clash layer, ordered before the default phase (where DuelRules clamps). */
	public static final Identifier DUEL_PHASE = Kultivator.id("duel_clash");

	/** The last results (for {@code uji duel info}): tick, outcome, attacker id, defender id. */
	private static final ArrayDeque<long[]> RECENT = new ArrayDeque<>();
	private static boolean done;

	/** Once, from {@code Kultivator.onInitialize} after {@code DuniaContent.init()} (C35.S1). */
	public static void init() {
		if (done) {
			return;
		}
		done = true;
		DuelConfig.get();
		DuelNet.init();
		ClashDamage.init();

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DUEL_PHASE, DuelHooks::allowDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(DUEL_PHASE, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				DuelHooks.afterDamage(entity, source, damageTaken));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			DuelistRegistry.onDeath(entity);
			Fighters.removeModifiers(entity);
		});
		ServerTickEvents.END_SERVER_TICK.register(Duel::tick);
		ServerPlayerEvents.JOIN.register(player -> {
			MinecraftServer server = player.level().getServer();
			DuelEscrow.get(server).deliver(player);
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			DuelSessions.onPlayerGone(player, Component.translatable("kultivator.duel.reason.gone", player.getDisplayName()));
			DuelChallenges.forget(player);
			Fighters.onLeave(player);
			DuelNet.forget(player.getUUID());
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
				DuelSessions.onPlayerGone(player, Component.translatable("kultivator.duel.reason.dimension", player.getDisplayName())));
		ServerLifecycleEvents.SERVER_STARTING.register(ClashClock::attach);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				DuelEscrow.get(server).returnAllActive(server);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Duel: returning interrupted stakes failed", exception);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DuelSessions.stopAll();
			DuelChallenges.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Fighters.clear();
			Reactive.clear();
			DuelistRegistry.clear();
			DuelNet.clear();
			DuelHooks.clear();
			RECENT.clear();
			ClashClock.attach(null);
		});
		Techniques.addCastBlocker(Duel::castBlocker);

		// The other three modules of the duel (U sounds and compat, V particles, A items).
		dev.kultivator.sound.DuelSounds.register();
		dev.kultivator.duel.compat.DuelCompat.init();
		dev.kultivator.fx.DuelParticles.register();
		dev.kultivator.duel.item.DuelItems.init();
	}

	private static void tick(MinecraftServer server) {
		if (ClashClock.server() != server) {
			ClashClock.attach(server);
		}
		if (!DuelConfig.get().enabled) {
			return;
		}
		try {
			DuelChallenges.tick(server);
			Fighters.tick(server);
			Reactive.tick(ClashClock.now());
			DuelSessions.tick(server);
			if (server.getTickCount() % 100 == 0) {
				DuelEscrow escrow = DuelEscrow.get(server);
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (escrow.hasPending(player.getUUID())) {
						escrow.deliver(player);
					}
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Duel tick failed", exception);
		}
	}

	/** {@code Techniques.addCastBlocker}: no technique in INTRO, COUNTDOWN, FINISHER, nor in FIGHT under PEDANG_SAJA. */
	static @Nullable Component castBlocker(ServerPlayer player) {
		DuelSession session = DuelSessions.byEntity(player);
		if (session == null) {
			return null;
		}
		if (session.phase() == DuelSession.Phase.FIGHT) {
			return session.rules().jurus() == DuelRuleSet.JurusRule.PEDANG_SAJA
					? Component.translatable("kultivator.duel.msg.swords_only") : null;
		}
		return Component.translatable("kultivator.duel.msg.no_technique_now");
	}

	// ================================================================ API (C35.S2) and the hooks of the clash layer

	/** {@code entity} yields its duel (command, HUD button, AI). */
	public static void surrender(LivingEntity entity) {
		DuelSession session = DuelSessions.byEntity(entity);
		if (session != null) {
			session.surrender(entity);
		} else if (entity instanceof ServerPlayer player) {
			Msg.chat(player, Component.translatable("kultivator.duel.msg.not_in_duel").withColor(0xE0A060));
		}
	}

	/** Every result the clash layer sends (diagnostics of {@code uji duel info}). */
	public static void onClash(LivingEntity a, @Nullable LivingEntity b, int outcome) {
		RECENT.addLast(new long[] {ClashClock.now(), outcome, a.getId(), b == null ? -1 : b.getId()});
		while (RECENT.size() > 10) {
			RECENT.removeFirst();
		}
	}

	/** The last ten results (oldest first): tick, outcome, attacker id, defender id. */
	public static Iterable<long[]> recent() {
		return RECENT;
	}

	/** A cheer moment of {@code fighter}'s formal duel ({@code DuelIds.CHEER_*}). */
	public static void cheer(Fighter fighter, int moment) {
		if (fighter != null && fighter.session() != null) {
			DuelVoices.cheer(fighter.session(), moment);
		}
	}

	/** A blade lock ended with a result. */
	public static void onBindResult(Fighter winner, Fighter loser, int outcome) {
		// The release trick wins the lock too (the meter's own win grants it in Bind).
		if (outcome == DuelIds.OUT_BIND_RELEASE_TRICK && winner.entity instanceof ServerPlayer player) {
			Advancements.grant(player, "duel_bind_win");
		}
	}

	/** A result the session sends itself (the DECISIVE result of a hit the clash layer did not deal). */
	public static void sendResultClash(LivingEntity by, LivingEntity victim, int outcome, Vec3 at, int flags) {
		Fighters.sendResult(by, victim, outcome, at, outcome == DuelIds.OUT_DEATHBLOW ? 255 : 200, flags);
	}

	// ================================================================ commands

	/** {@code /kultivasi duel ...} and {@code /kultivasi uji duel ...}, from {@code CultivateCommand}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> uji) {
		DuelCommands.add(root);
		DuelTests.add(uji);
	}

	/** The chips of the "duel" group of {@code CheatHelp}. */
	public static java.util.List<String> testChips() {
		return DuelTests.CHIPS;
	}

	private Duel() {
	}
}
