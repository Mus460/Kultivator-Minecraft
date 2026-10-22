package dev.kultivator.ascension;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sparring;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The True Immortal ascension ("Pengakuan Langit", DESIGN section 17) and the late-game milestones. When a player has
 * reached True Immortal and is free (no breakthrough, trial or duel), the heavens acknowledge them in a 30 second event:
 * the world darkens, a golden pillar descends, the heavens open with golden clouds and falling petals, a celestial voice
 * speaks and a chord of bells and harps rings. Afterwards the player is ascended (rainbow aura through the ASCENDED visual
 * flag), immune to Qi Deviation and slowly regenerating. Nothing moves the player and no block is ever touched: the whole
 * event is particles (client), titles and sounds.
 */
public final class Ascension {

	/** Length of the whole event (30 s). */
	public static final int DURATION = 600;
	/** Event tick at which the golden pillar starts to descend. */
	public static final int PILLAR_AT = 100;
	/** Ticks the pillar needs to fall from the sky onto the player. */
	public static final int DESCENT_TICKS = 60;
	/** Event tick at which the heavens open. */
	public static final int HEAVENS_AT = 240;
	/** Event tick of the celestial voice. */
	public static final int VOICE_AT = 360;
	/** The pillar falls from this far above the player. */
	public static final double PILLAR_HEIGHT = 40.0D;
	/** Height of the opening in the heavens above the player. */
	public static final double HEAVENS_HEIGHT = 26.0D;

	/** FxType.ASCENSION steps ({@code a}); {@code b} is the ascending player's entity id, {@code f} the event tick. */
	public static final int FX_DARKEN = 0;
	public static final int FX_PILLAR = 1;
	public static final int FX_HEAVENS = 2;
	/** Sent every second while the event runs (keeps observers in step; f = event tick). */
	public static final int FX_PULSE = 3;
	/** The end of the event; f = 1 for a real ascension, 0 for a test. */
	public static final int FX_FINALE = 4;
	/** The event was cancelled: stop drawing it. */
	public static final int FX_STOP = 5;
	/** The celestial voice speaks. */
	public static final int FX_VOICE = 6;

	/** Gold used by the event's chat lines. */
	public static final int GOLD = 0xFFD54A;

	/** Readiness is checked once per second; the event starts after this many ready checks in a row (lets the clouds part). */
	private static final int START_CHECKS = 4;
	/** After a cancelled event, this many seconds pass before it may begin again. */
	private static final int RETRY_CHECKS = 15;
	/** Players this close (blocks) hear the voice and see the chat lines about the event. */
	private static final double NEARBY = 96.0D;
	/** Milestone count goals (advancements). */
	public static final long MEDITATION_GOAL_TICKS = 72000L;
	public static final int TRIBULATION_GOAL = 10;
	public static final int TECHNIQUE_KILL_GOAL = 100;

	/** Harp chords {start tick, notes...} (note-block notes 0..24, F#3 = 0): I, vi, IV, V, I, high I. */
	private static final int[][] CHORDS = {
			{HEAVENS_AT, 6, 10, 13, 18},
			{HEAVENS_AT + 60, 3, 6, 10, 15},
			{VOICE_AT, 11, 15, 18, 23},
			{VOICE_AT + 60, 1, 13, 17, 20},
			{VOICE_AT + 120, 6, 10, 13, 18},
			{DURATION - 40, 6, 13, 18, 22},
	};
	private static final int ARPEGGIO_GAP = 3;

	private static final int COUNTER_MEDITATION = 0;
	private static final int COUNTER_TRIBULATIONS = 1;
	private static final int COUNTER_KILLS = 2;

	private static final class Event {
		/** A real ascension sets {@code ascended} at the end; a test (debug command) changes nothing. */
		final boolean real;
		final ResourceKey<Level> dimension;
		int ticks;

		Event(boolean real, ResourceKey<Level> dimension) {
			this.real = real;
			this.dimension = dimension;
		}
	}

	private static final Map<UUID, Event> EVENTS = new HashMap<>();
	/** Consecutive ready checks (positive) or the seconds left before a retry after a cancellation (negative). */
	private static final Map<UUID, Integer> WAIT = new HashMap<>();

	public static void init() {
		ServerPlayerEvents.LEAVE.register(player -> {
			// Leaving cancels the event without any penalty; it begins again some time after the player is back.
			cancel(player, null);
			WAIT.remove(player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				cancel(player, "kultivator.ascension.cancelled");
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			EVENTS.clear();
			WAIT.clear();
		});
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		Event event = EVENTS.get(player.getUUID());
		if (event != null) {
			tickEvent(player, data, event);
		} else if (player.tickCount % 20 == 0) {
			checkStart(player, data);
		}
		if (player.tickCount % 100 == 0) {
			gifts(player, data);
			milestones(player, data);
		}
	}

	/** {@code debug ascension start|reset|progress|add ...}, each with an optional player. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		debug.then(Commands.literal("ascension")
				.then(Commands.literal("start")
						.executes(ctx -> startCommand(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> startCommand(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("reset")
						.executes(ctx -> resetCommand(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> resetCommand(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("progress")
						.executes(ctx -> progressCommand(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> progressCommand(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("add")
						.then(counterNode("meditation", COUNTER_MEDITATION))
						.then(counterNode("tribulations", COUNTER_TRIBULATIONS))
						.then(counterNode("technique_kills", COUNTER_KILLS))));
	}

	// ================================================================== public helpers

	/** True while the ascension event runs for the player. */
	public static boolean isActive(ServerPlayer player) {
		return EVENTS.containsKey(player.getUUID());
	}

	/** True when the player completed the ascension (immune to Qi Deviation, slow regeneration, rainbow aura). */
	public static boolean isAscended(ServerPlayer player) {
		return Cultivators.get(player).ascended();
	}

	/**
	 * Every realm from Foundation Establishment up to the current one was reached with a Perfect or Heavenly breakthrough,
	 * and the player is at least Golden Core (advancement {@code all_realms_quality}). The Awakening (Qi Refining) is left
	 * out: its quality comes from a one-time roll that can hardly reach Perfect.
	 */
	public static boolean flawlessPath(CultivationData data) {
		Realm current = data.realm();
		if (!current.atLeast(Realm.GOLDEN_CORE)) {
			return false;
		}
		for (Realm realm : Realm.values()) {
			if (!realm.atLeast(Realm.FOUNDATION) || realm.ordinal() > current.ordinal()) {
				continue;
			}
			BreakthroughQuality quality = data.quality(realm);
			if (quality == null || quality.ordinal() < BreakthroughQuality.PERFECT.ordinal()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Begins the event now. {@code real} events set {@code ascended} when they end; tests only show it. False when one
	 * is already running.
	 */
	public static boolean start(ServerPlayer player, boolean real) {
		if (EVENTS.containsKey(player.getUUID())) {
			return false;
		}
		WAIT.remove(player.getUUID());
		ServerLevel level = player.level();
		EVENTS.put(player.getUUID(), new Event(real, level.dimension()));
		Vec3 pos = player.position();
		Fx.send(level, pos, FxType.ASCENSION, FX_DARKEN, player.getId(), 0.0F);
		dev.kultivator.epic.EpicHooks.ascension(player, dev.kultivator.epic.EpicIds.ASC_DARKEN, 0, real);
		// The world dims around the one being judged (the vanilla Darkness screen effect, a few seconds, no icon).
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, false));
		sound(level, pos, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
		sound(level, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.5F);
		sound(level, pos, SoundEvents.BEACON_AMBIENT, 2.0F, 0.5F);
		if (!real) {
			Msg.chat(player, Component.translatable("kultivator.ascension.test_start").withStyle(ChatFormatting.GRAY));
		}
		Msg.chat(player, Component.translatable("kultivator.ascension.begin").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		return true;
	}

	/** Stops a running event without any penalty ({@code messageKey} is told to the player, null for silence). */
	public static boolean cancel(ServerPlayer player, @Nullable String messageKey) {
		Event event = EVENTS.remove(player.getUUID());
		if (event == null) {
			return false;
		}
		Fx.send(player.level(), player.position(), FxType.ASCENSION, FX_STOP, player.getId(), (float) event.ticks);
		dev.kultivator.epic.EpicHooks.ascension(player, dev.kultivator.epic.EpicIds.ASC_STOP, event.ticks, event.real);
		WAIT.put(player.getUUID(), -RETRY_CHECKS);
		if (messageKey != null) {
			Msg.chat(player, Component.translatable(messageKey).withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	// ================================================================== the event

	private static void checkStart(ServerPlayer player, CultivationData data) {
		UUID id = player.getUUID();
		if (data.realm() != Realm.TRUE_IMMORTAL || data.ascended()) {
			WAIT.remove(id);
			return;
		}
		int wait = WAIT.getOrDefault(id, 0);
		if (wait < 0) {
			// Cooling down after a cancelled event.
			WAIT.put(id, wait + 1);
			return;
		}
		if (!canBegin(player, data)) {
			WAIT.remove(id);
			return;
		}
		if (wait + 1 < START_CHECKS) {
			WAIT.put(id, wait + 1);
			return;
		}
		start(player, true);
	}

	/**
	 * Alive, on foot (not riding), not breaking through, not facing a heart demon, not in a duel and free of everything
	 * the shared {@link Sibuk} door knows (Sword Duel, realm runs, wrath, calamity, rituals...). While busy the 30 s wait
	 * simply starts again, so the Acknowledgement is postponed, never lost.
	 */
	private static boolean canBegin(ServerPlayer player, CultivationData data) {
		return player.isAlive() && !player.isDeadOrDying() && !player.isSpectator() && !player.isPassenger()
				&& !Breakthrough.isBusy(player) && !data.breakingThrough() && !HeartDemons.inTrial(player) && !Sparring.inDuel(player)
				&& Sibuk.why(player) == null;
	}

	private static void tickEvent(ServerPlayer player, CultivationData data, Event event) {
		if (!player.isAlive() || player.isDeadOrDying()) {
			cancel(player, "kultivator.ascension.cancelled");
			return;
		}
		if (!player.level().dimension().equals(event.dimension)) {
			cancel(player, "kultivator.ascension.cancelled");
			return;
		}
		if (event.real && (data.ascended() || data.realm() != Realm.TRUE_IMMORTAL)) {
			// Changed by a command meanwhile: nothing left to acknowledge.
			cancel(player, null);
			WAIT.remove(player.getUUID());
			return;
		}
		event.ticks++;
		int t = event.ticks;
		ServerLevel level = player.level();
		Vec3 pos = player.position();
		int id = player.getId();

		if (t == PILLAR_AT) {
			Fx.send(level, pos, FxType.ASCENSION, FX_PILLAR, id, t);
			dev.kultivator.epic.EpicHooks.ascension(player, dev.kultivator.epic.EpicIds.ASC_PILLAR, t, event.real);
			sound(level, pos, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
			sound(level, pos, SoundEvents.BELL_BLOCK, 2.0F, 0.5F);
			Msg.chat(player, Component.translatable("kultivator.ascension.pillar").withColor(GOLD));
		} else if (t == PILLAR_AT + 20 || t == PILLAR_AT + 40) {
			sound(level, pos, SoundEvents.BELL_BLOCK, 2.0F, t == PILLAR_AT + 20 ? 0.6F : 0.75F);
		} else if (t == PILLAR_AT + DESCENT_TICKS) {
			sound(level, pos, SoundEvents.CONDUIT_ACTIVATE, 2.0F, 1.0F);
			sound(level, pos, SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.8F);
		} else if (t == HEAVENS_AT) {
			Fx.send(level, pos, FxType.ASCENSION, FX_HEAVENS, id, t);
			dev.kultivator.epic.EpicHooks.ascension(player, dev.kultivator.epic.EpicIds.ASC_HEAVENS, t, event.real);
			sound(level, pos, SoundEvents.BELL_RESONATE, 2.0F, 1.0F);
			sound(level, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1.2F);
			Msg.chat(player, Component.translatable("kultivator.ascension.heavens").withColor(GOLD));
			tellNearby(player, Component.translatable("kultivator.ascension.heavens_nearby", player.getDisplayName()).withColor(GOLD));
		} else if (t == VOICE_AT) {
			Fx.send(level, pos, FxType.ASCENSION, FX_VOICE, id, t);
			dev.kultivator.epic.EpicHooks.ascension(player, dev.kultivator.epic.EpicIds.ASC_VOICE, t, event.real);
			voice(player, level, pos);
		}
		if (t > HEAVENS_AT && t % 40 == 0) {
			sound(level, pos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F + player.getRandom().nextFloat() * 0.8F);
		}
		chords(level, pos, t);
		if (t % 20 == 0 && t < DURATION) {
			Fx.send(level, pos, FxType.ASCENSION, FX_PULSE, id, t);
		}
		if (t >= DURATION) {
			finish(player, data, event);
		}
	}

	/** "The heavens acknowledge a True Immortal": titles for the player, a bell and a chat line for everyone close. */
	private static void voice(ServerPlayer player, ServerLevel level, Vec3 pos) {
		Msg.title(player, Component.translatable("kultivator.ascension.voice.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.ascension.voice.subtitle").withColor(0xFFF2C0), 15, 90, 25);
		sound(level, pos, SoundEvents.BELL_RESONATE, 2.5F, 0.8F);
		sound(level, pos, SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.2F);
		sound(level, pos, SoundEvents.TOTEM_USE, 0.8F, 1.6F);
		MutableComponent line = Component.translatable("kultivator.ascension.voice.chat").withColor(0xFFF2C0);
		Msg.chat(player, line);
		tellNearby(player, line);
	}

	private static void finish(ServerPlayer player, CultivationData data, Event event) {
		EVENTS.remove(player.getUUID());
		WAIT.remove(player.getUUID());
		ServerLevel level = player.level();
		Vec3 pos = player.position();
		Fx.send(level, pos, FxType.ASCENSION, FX_FINALE, player.getId(), event.real ? 1.0F : 0.0F);
		dev.kultivator.epic.EpicHooks.ascension(player, dev.kultivator.epic.EpicIds.ASC_FINALE, event.ticks, event.real);
		sound(level, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.5F, 1.0F);
		sound(level, pos, SoundEvents.TOTEM_USE, 1.5F, 1.0F);
		sound(level, pos, SoundEvents.BELL_RESONATE, 2.0F, 1.2F);
		sound(level, pos, SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.5F);
		sound(level, pos, SoundEvents.PLAYER_LEVELUP, 1.0F, 0.6F);
		if (!event.real) {
			Msg.chat(player, Component.translatable("kultivator.ascension.test_done").withStyle(ChatFormatting.GRAY));
			return;
		}
		data.setAscended(true);
		if (data.deviating()) {
			Deviation.clear(player);
			Msg.chat(player, Component.translatable("kultivator.ascension.deviation_purged").withStyle(ChatFormatting.GREEN));
		}
		player.setHealth(player.getMaxHealth());
		Qi.fill(player);
		Msg.title(player, Component.translatable("kultivator.ascension.final.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.ascension.final.subtitle").withColor(0xFFF2C0), 10, 80, 30);
		Msg.chat(player, Component.translatable("kultivator.ascension.complete").withColor(GOLD));
		Msg.chat(player, Component.translatable("kultivator.ascension.gifts").withStyle(ChatFormatting.AQUA));
		MutableComponent announce = Component.translatable("kultivator.ascension.announce", player.getDisplayName()).withColor(GOLD);
		if (KultivatorConfig.get().announceBreakthroughs) {
			Msg.broadcast(level.getServer(), announce.withStyle(ChatFormatting.BOLD));
		}
		Advancements.grant(player, "ascended");
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
	}

	// ================================================================== after the ascension

	/** Ascended players heal 1 health every 5 seconds while hurt (called every 100 ticks). */
	private static void gifts(ServerPlayer player, CultivationData data) {
		if (data.ascended() && player.isAlive() && !player.isDeadOrDying() && player.getHealth() < player.getMaxHealth()) {
			player.heal(1.0F);
		}
	}

	/** The late-game advancements (called every 100 ticks). */
	private static void milestones(ServerPlayer player, CultivationData data) {
		if (data.ascended()) {
			Advancements.grant(player, "ascended");
		}
		if (data.rank().isMax()) {
			Advancements.grant(player, "path_complete");
		}
		if (data.meditationTicks() >= MEDITATION_GOAL_TICKS) {
			Advancements.grant(player, "hour_of_stillness");
		}
		if (data.tribulationsSurvived() >= TRIBULATION_GOAL) {
			Advancements.grant(player, "tribulation_veteran");
		}
		if (data.techniqueKills() >= TECHNIQUE_KILL_GOAL) {
			Advancements.grant(player, "technique_slayer");
		}
		if (flawlessPath(data)) {
			Advancements.grant(player, "all_realms_quality");
		}
	}

	// ================================================================== sounds and text

	private static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void note(ServerLevel level, Vec3 pos, Holder<SoundEvent> sound, int note, float volume) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, NoteBlock.getPitchFromNote(note));
	}

	/** Plays the harp chords as rising arpeggios; the last note rings with a bell and the whole chord once more. */
	private static void chords(ServerLevel level, Vec3 pos, int t) {
		for (int[] chord : CHORDS) {
			int offset = t - chord[0];
			if (offset < 0 || offset % ARPEGGIO_GAP != 0) {
				continue;
			}
			int index = offset / ARPEGGIO_GAP + 1;
			if (index >= chord.length) {
				continue;
			}
			note(level, pos, SoundEvents.NOTE_BLOCK_HARP, chord[index], 1.6F);
			if (index == chord.length - 1) {
				note(level, pos, SoundEvents.NOTE_BLOCK_BELL, chord[index], 1.2F);
				for (int i = 1; i < chord.length; i++) {
					note(level, pos, SoundEvents.NOTE_BLOCK_HARP, chord[i], 1.0F);
				}
			}
		}
	}

	/** A chat line for the other players near {@code player}. */
	private static void tellNearby(ServerPlayer player, Component message) {
		for (ServerPlayer other : player.level().players()) {
			if (other != player && other.distanceToSqr(player) <= NEARBY * NEARBY) {
				Msg.chat(other, message);
			}
		}
	}

	// ================================================================== debug commands

	private static LiteralArgumentBuilder<CommandSourceStack> counterNode(String name, int which) {
		return Commands.literal(name)
				.then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000))
						.executes(ctx -> addCommand(ctx.getSource(), ctx.getSource().getPlayerOrException(), which,
								IntegerArgumentType.getInteger(ctx, "amount")))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> addCommand(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), which,
										IntegerArgumentType.getInteger(ctx, "amount")))));
	}

	/** Runs the event now, even below True Immortal; it only ascends a True Immortal who has not ascended yet. */
	private static int startCommand(CommandSourceStack source, ServerPlayer player) {
		if (isActive(player)) {
			source.sendFailure(Component.translatable("kultivator.ascension.debug.running", player.getDisplayName()));
			return 0;
		}
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			source.sendFailure(Component.translatable("kultivator.ascension.debug.cannot", player.getDisplayName()));
			return 0;
		}
		CultivationData data = Cultivators.get(player);
		boolean real = data.realm() == Realm.TRUE_IMMORTAL && !data.ascended();
		start(player, real);
		Component mode = Component.translatable(real ? "kultivator.ascension.debug.mode.real" : "kultivator.ascension.debug.mode.test");
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.ascension.debug.started", player.getDisplayName(), mode)), false);
		return 1;
	}

	/** Clears {@code ascended} (and stops a running event). A True Immortal will be acknowledged again shortly after. */
	private static int resetCommand(CommandSourceStack source, ServerPlayer player) {
		cancel(player, null);
		WAIT.remove(player.getUUID());
		Cultivators.get(player).setAscended(false);
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.ascension.debug.reset", player.getDisplayName())), false);
		return 1;
	}

	/** Shows the counters behind the milestone advancements. */
	private static int progressCommand(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		source.sendSuccess(() -> Component.translatable("kultivator.ascension.debug.progress.header", player.getDisplayName())
				.withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> yesNo("kultivator.ascension.debug.progress.ascended", data.ascended()), false);
		source.sendSuccess(() -> yesNo("kultivator.ascension.debug.progress.peak", data.rank().isMax()), false);
		source.sendSuccess(() -> counterLine("kultivator.ascension.milestone.meditation", data.meditationTicks() / 1200L,
				MEDITATION_GOAL_TICKS / 1200L), false);
		source.sendSuccess(() -> counterLine("kultivator.ascension.milestone.tribulations", data.tribulationsSurvived(), TRIBULATION_GOAL), false);
		source.sendSuccess(() -> counterLine("kultivator.ascension.milestone.technique_kills", data.techniqueKills(), TECHNIQUE_KILL_GOAL), false);
		source.sendSuccess(() -> yesNo("kultivator.ascension.debug.progress.flawless", flawlessPath(data)), false);
		return 1;
	}

	private static int addCommand(CommandSourceStack source, ServerPlayer player, int which, int amount) {
		CultivationData data = Cultivators.get(player);
		String key;
		switch (which) {
			case COUNTER_MEDITATION -> {
				data.addMeditationTicks(amount * 1200L);
				key = "kultivator.ascension.milestone.meditation";
			}
			case COUNTER_TRIBULATIONS -> {
				for (int i = 0; i < amount; i++) {
					data.addTribulationSurvived();
				}
				key = "kultivator.ascension.milestone.tribulations";
			}
			default -> {
				for (int i = 0; i < amount; i++) {
					data.addTechniqueKill();
				}
				key = "kultivator.ascension.milestone.technique_kills";
			}
		}
		Cultivators.syncNow(player);
		milestones(player, data);
		Component counter = Component.translatable(key);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.ascension.debug.added", amount, counter,
				player.getDisplayName())), false);
		return 1;
	}

	private static MutableComponent counterLine(String key, long value, long goal) {
		ChatFormatting color = value >= goal ? ChatFormatting.GREEN : ChatFormatting.WHITE;
		return Component.translatable("kultivator.ascension.debug.progress.line", Component.translatable(key).withStyle(ChatFormatting.GRAY),
				Component.literal(String.valueOf(value)).withStyle(color), Component.literal(String.valueOf(goal)));
	}

	private static MutableComponent yesNo(String key, boolean value) {
		return Component.translatable(key).withStyle(ChatFormatting.GRAY).append(Component.literal(" "))
				.append(Component.translatable(value ? "kultivator.ascension.debug.yes" : "kultivator.ascension.debug.no")
						.withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private Ascension() {
	}
}
