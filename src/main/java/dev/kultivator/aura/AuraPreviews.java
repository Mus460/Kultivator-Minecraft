package dev.kultivator.aura;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.command.TestCommands;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.network.Net;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aura Kultivasi previews (DESIGN 31.6, C31.5), server side: {@code /kultivasi uji aura ...} (op, under the existing
 * {@code uji} literal) shows the cultivation aura of any realm, stage and layer on the player or on one of their loaded
 * disciples for a while, <b>without changing any rank</b>: the server only tells the clients "draw this aura on that
 * entity for so many ticks" through {@link AuraPreviewPayload}. Nothing is saved; previews end on their own, with
 * {@code uji aura stop}, on death, dimension change, logout and server stop.
 *
 * <p>Presentation only: no block, no entity, no weather/time change; the client draws everything (rule 0.4).
 */
public final class AuraPreviews {

	/** One preview: 20 seconds. */
	public static final int PREVIEW_TICKS = 400;
	/** A Peak preview below True Immortal shows the breakthrough-ready glow in its last 10 seconds. */
	public static final int BOTTLENECK_LAST_TICKS = 200;
	/** One step of {@code uji aura semua}: 3 seconds. */
	public static final int CYCLE_STEP_TICKS = 60;
	/** A live preview is re-sent this often (late viewers catch up; the remaining ticks stay right). */
	private static final int RESEND_TICKS = 20;
	/** Cycle steps (except the last) are sent a little longer than they last, so the next step replaces them seamlessly. */
	private static final int CYCLE_SLACK_TICKS = 10;

	/** The 33 steps of {@code uji aura semua} (DESIGN 31.6). */
	public static final List<Rank> CYCLE = buildCycle();

	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Realm.values()).map(Realm::id), builder);

	/** A running preview on one entity. */
	private static final class Preview {
		/** The player who ran the command (and owns the disciple). */
		final UUID owner;
		final int entityId;
		/** Null for the player's own preview (looked up by {@link #owner} every tick, so a respawned copy is never used). */
		final @Nullable DiscipleEntity disciple;
		final ResourceKey<Level> dimension;
		final boolean cycle;
		Rank rank;
		int cycleIndex;
		int ticksLeft;
		int sinceSend;
		int sentFlags = -1;
		Rank sentRank;

		Preview(UUID owner, Entity subject, @Nullable DiscipleEntity disciple, Rank rank, int ticks, boolean cycle) {
			this.owner = owner;
			this.entityId = subject.getId();
			this.disciple = disciple;
			this.dimension = subject.level().dimension();
			this.rank = rank;
			this.ticksLeft = ticks;
			this.cycle = cycle;
			this.sentRank = rank;
		}

		boolean lastCycleStep() {
			return cycleIndex >= CYCLE.size() - 1;
		}
	}

	/** Running previews by subject entity id (one per entity; a new one replaces the old). */
	private static final Map<Integer, Preview> PREVIEWS = new LinkedHashMap<>();
	/** Players whose previews force the accent ({@code uji aura aksen}). */
	private static final Set<UUID> ACCENT = new HashSet<>();

	/** From {@code Kultivator.onInitialize} (both physical sides): the payload type, the ticker and the cleanup hooks. */
	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(AuraPreviewPayload.TYPE, AuraPreviewPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(AuraPreviews::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			endOwnedBy(player.level().getServer(), player.getUUID(), false);
			ACCENT.remove(player.getUUID());
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			Preview own = PREVIEWS.get(player.getId());
			if (own != null && own.disciple == null) {
				end(player.level().getServer(), own, player);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Preview preview = PREVIEWS.get(entity.getId());
			if (preview != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
				end(level.getServer(), preview, entity);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PREVIEWS.clear();
			ACCENT.clear();
		});
	}

	// ================================================================ commands

	/** The {@code aura} node under {@code uji} (added by {@code TestCommands.addCommands}). */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("aura")
				.executes(ctx -> usage(ctx.getSource()))
				.then(Commands.literal("semua").executes(AuraPreviews::cycle))
				.then(Commands.literal("aksen").executes(AuraPreviews::toggleAccent))
				.then(Commands.literal("stop").executes(AuraPreviews::stop))
				.then(Commands.literal("murid")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(TestCommands.DISCIPLE_NAMES)
								.then(realmArguments(true))))
				.then(realmArguments(false));
	}

	/** {@code <realm> [stage 1-4] [layer 1-9]} for the player ({@code disciples} false) or the named disciples. */
	private static RequiredArgumentBuilder<CommandSourceStack, String> realmArguments(boolean disciples) {
		return Commands.argument("realm", StringArgumentType.word()).suggests(REALMS)
				.executes(ctx -> preview(ctx, disciples, Rank.PEAK + 1, 1))
				.then(Commands.argument("stage", IntegerArgumentType.integer(1, 4))
						.executes(ctx -> preview(ctx, disciples, IntegerArgumentType.getInteger(ctx, "stage"), 1))
						.then(Commands.argument("layer", IntegerArgumentType.integer(1, 9))
								.executes(ctx -> preview(ctx, disciples, IntegerArgumentType.getInteger(ctx, "stage"),
										IntegerArgumentType.getInteger(ctx, "layer")))));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.aura.cmd.usage").withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int preview(CommandContext<CommandSourceStack> ctx, boolean disciples, int stage, int layer)
			throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "realm");
		Realm realm = Realm.byId(id.toLowerCase(Locale.ROOT));
		if (realm == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.aura.cmd.unknown_realm", id));
			return 0;
		}
		// Rank clamps: Mortal has one stage, only Immortal Ascension has layers.
		Rank rank = new Rank(realm, layer - 1, stage - 1);
		if (!disciples) {
			start(player, player, null, rank, PREVIEW_TICKS, false);
			Msg.chat(player, Component.translatable("kultivator.aura.cmd.preview", auraName(realm), rankName(rank),
					PREVIEW_TICKS / 20).withStyle(ChatFormatting.AQUA));
			return 1;
		}
		List<DiscipleEntity> found = disciples(player, StringArgumentType.getString(ctx, "name"));
		if (found.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.aura.cmd.no_disciple",
					StringArgumentType.getString(ctx, "name")));
			return 0;
		}
		for (DiscipleEntity disciple : found) {
			start(player, disciple, disciple, rank, PREVIEW_TICKS, false);
			Msg.chat(player, Component.translatable("kultivator.aura.cmd.disciple", disciple.getName(), auraName(realm),
					rankName(rank), PREVIEW_TICKS / 20).withStyle(ChatFormatting.AQUA));
		}
		return found.size();
	}

	private static int cycle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Msg.chat(player, Component.translatable("kultivator.aura.cmd.cycle_start", CYCLE.size(), CYCLE_STEP_TICKS / 20,
				CYCLE.size() * CYCLE_STEP_TICKS / 20).withStyle(ChatFormatting.AQUA));
		Preview preview = start(player, player, null, CYCLE.getFirst(), CYCLE_STEP_TICKS, true);
		announceStep(player, preview);
		return 1;
	}

	private static int toggleAccent(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean on = ACCENT.add(player.getUUID());
		if (!on) {
			ACCENT.remove(player.getUUID());
		}
		// Running previews pick the new flag up on the next tick (their flags changed, so they are re-sent at once).
		Msg.chat(player, Component.translatable(on ? "kultivator.aura.cmd.accent_on" : "kultivator.aura.cmd.accent_off")
				.withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int ended = endOwnedBy(ctx.getSource().getServer(), player.getUUID(), true);
		Msg.chat(player, Component.translatable("kultivator.aura.cmd.stop", ended).withStyle(ChatFormatting.AQUA));
		return Math.max(1, ended);
	}

	/** The player's loaded disciples named {@code name} (spaces as underscores, as in {@code uji murid}); "semua" = all. */
	private static List<DiscipleEntity> disciples(ServerPlayer player, String name) {
		String wanted = name.toLowerCase(Locale.ROOT);
		List<DiscipleEntity> found = new ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.isRemoved() || !disciple.isAlive()) {
				continue;
			}
			if (wanted.equals("semua") || TestCommands.token(disciple).toLowerCase(Locale.ROOT).equals(wanted)) {
				found.add(disciple);
			}
		}
		return found;
	}

	// ================================================================ previews

	private static Preview start(ServerPlayer owner, Entity subject, @Nullable DiscipleEntity disciple, Rank rank, int ticks,
								 boolean cycle) {
		Preview preview = new Preview(owner.getUUID(), subject, disciple, rank, ticks, cycle);
		PREVIEWS.put(preview.entityId, preview);
		send(owner.level().getServer(), preview, subject);
		return preview;
	}

	private static void tick(MinecraftServer server) {
		if (PREVIEWS.isEmpty()) {
			return;
		}
		for (Preview preview : List.copyOf(PREVIEWS.values())) {
			if (PREVIEWS.get(preview.entityId) != preview) {
				continue; // replaced or ended during this pass
			}
			Entity subject = subject(server, preview);
			if (subject == null) {
				end(server, preview, null);
				continue;
			}
			preview.sinceSend++;
			if (--preview.ticksLeft <= 0) {
				if (preview.cycle && !preview.lastCycleStep()) {
					preview.cycleIndex++;
					preview.rank = CYCLE.get(preview.cycleIndex);
					preview.ticksLeft = CYCLE_STEP_TICKS;
					send(server, preview, subject);
					ServerPlayer owner = server.getPlayerList().getPlayer(preview.owner);
					if (owner != null) {
						announceStep(owner, preview);
					}
					continue;
				}
				end(server, preview, subject);
				if (preview.cycle) {
					ServerPlayer owner = server.getPlayerList().getPlayer(preview.owner);
					if (owner != null) {
						Msg.chat(owner, Component.translatable("kultivator.aura.cmd.cycle_end").withStyle(ChatFormatting.AQUA));
					}
				}
				continue;
			}
			if (preview.sinceSend >= RESEND_TICKS || flags(preview) != preview.sentFlags || !preview.rank.equals(preview.sentRank)) {
				send(server, preview, subject);
			}
		}
	}

	/** The preview's subject while it may still show the aura, else null (gone, dead, other dimension, owner offline). */
	private static @Nullable Entity subject(MinecraftServer server, Preview preview) {
		ServerPlayer owner = server.getPlayerList().getPlayer(preview.owner);
		if (owner == null) {
			return null;
		}
		Entity subject = preview.disciple != null ? preview.disciple : owner;
		if (subject.isRemoved() || !subject.isAlive() || subject.getId() != preview.entityId
				|| !subject.level().dimension().equals(preview.dimension)) {
			return null;
		}
		return subject;
	}

	private static int flags(Preview preview) {
		int flags = 0;
		if (ACCENT.contains(preview.owner)) {
			flags |= AuraPreviewPayload.FLAG_ACCENT;
		}
		Rank rank = preview.rank;
		boolean peak = !rank.realm().isMortal() && rank.isBottleneck() && !rank.isMax();
		if (peak && (preview.cycle || preview.ticksLeft <= BOTTLENECK_LAST_TICKS)) {
			flags |= AuraPreviewPayload.FLAG_BOTTLENECK;
		}
		if (preview.cycle) {
			flags |= AuraPreviewPayload.FLAG_CYCLE;
		}
		return flags;
	}

	private static void send(MinecraftServer server, Preview preview, Entity subject) {
		int flags = flags(preview);
		int ticks = preview.ticksLeft + (preview.cycle && !preview.lastCycleStep() ? CYCLE_SLACK_TICKS : 0);
		Rank rank = preview.rank;
		AuraPreviewPayload payload = new AuraPreviewPayload(preview.entityId, rank.realm().ordinal(), rank.layer(), rank.stage(),
				flags, Math.max(1, ticks));
		for (ServerPlayer viewer : viewers(server, preview.owner, subject)) {
			Net.send(viewer, payload);
		}
		preview.sentFlags = flags;
		preview.sentRank = rank;
		preview.sinceSend = 0;
	}

	/** Ends a preview now and tells its viewers (the owner, and those tracking the subject when it is still there). */
	private static void end(MinecraftServer server, Preview preview, @Nullable Entity subject) {
		PREVIEWS.remove(preview.entityId, preview);
		AuraPreviewPayload stop = AuraPreviewPayload.stop(preview.entityId);
		for (ServerPlayer viewer : viewers(server, preview.owner, subject)) {
			Net.send(viewer, stop);
		}
	}

	/** Ends every preview started by {@code owner} (theirs and their disciples'); returns how many ended. */
	private static int endOwnedBy(@Nullable MinecraftServer server, UUID owner, boolean tellOwner) {
		int ended = 0;
		for (Preview preview : List.copyOf(PREVIEWS.values())) {
			if (!preview.owner.equals(owner)) {
				continue;
			}
			ended++;
			if (server == null) {
				PREVIEWS.remove(preview.entityId, preview);
				continue;
			}
			Entity subject = preview.disciple != null ? preview.disciple : server.getPlayerList().getPlayer(owner);
			if (subject != null && (subject.isRemoved() || subject.getId() != preview.entityId)) {
				subject = null;
			}
			if (tellOwner) {
				end(server, preview, subject);
			} else {
				// The owner is leaving: tell only the others who still see the subject.
				PREVIEWS.remove(preview.entityId, preview);
				AuraPreviewPayload stop = AuraPreviewPayload.stop(preview.entityId);
				if (subject != null) {
					for (ServerPlayer viewer : PlayerLookup.tracking(subject)) {
						if (!viewer.getUUID().equals(owner)) {
							Net.send(viewer, stop);
						}
					}
				}
			}
		}
		return ended;
	}

	/** The owner (when online) plus everyone tracking the subject, each once. */
	private static Set<ServerPlayer> viewers(MinecraftServer server, UUID ownerId, @Nullable Entity subject) {
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		if (owner == null && subject == null) {
			return Collections.emptySet();
		}
		Set<ServerPlayer> viewers = new LinkedHashSet<>();
		if (owner != null) {
			viewers.add(owner);
		}
		if (subject != null) {
			if (subject instanceof ServerPlayer self) {
				viewers.add(self);
			}
			viewers.addAll(PlayerLookup.tracking(subject));
		}
		return viewers;
	}

	private static void announceStep(ServerPlayer owner, Preview preview) {
		Rank rank = preview.rank;
		Msg.actionBar(owner, Component.translatable("kultivator.aura.cmd.cycle_step", preview.cycleIndex + 1, CYCLE.size(),
				auraName(rank.realm()), rankName(rank)).withStyle(ChatFormatting.AQUA));
	}

	// ================================================================ helpers

	/** "Aura Inti Emas": the client text key of C31.6 (the lang fragments are merged into one file). */
	public static MutableComponent auraName(Realm realm) {
		return Component.translatable("kultivator.aura.realm." + realm.id() + ".name").withColor(realm.color());
	}

	private static MutableComponent rankName(Rank rank) {
		return rank.displayName().withColor(rank.realm().color());
	}

	/**
	 * Mortal; Qi Refining .. Dao Union stages 1-4; Immortal Ascension layer 1 Early, 3 Middle, 6 Late, 9 Peak; True
	 * Immortal stages 1-4: 1 + 24 + 4 + 4 = 33 steps.
	 */
	private static List<Rank> buildCycle() {
		List<Rank> steps = new ArrayList<>();
		steps.add(Rank.MORTAL);
		for (Realm realm : Realm.values()) {
			if (realm.isMortal() || realm == Realm.IMMORTAL_ASCENSION || realm == Realm.TRUE_IMMORTAL) {
				continue;
			}
			for (int stage = 0; stage < realm.stages(); stage++) {
				steps.add(new Rank(realm, 0, stage));
			}
		}
		int[] layers = {0, 2, 5, 8};
		for (int stage = 0; stage < Realm.IMMORTAL_ASCENSION.stages(); stage++) {
			steps.add(new Rank(Realm.IMMORTAL_ASCENSION, layers[Math.min(stage, layers.length - 1)], stage));
		}
		for (int stage = 0; stage < Realm.TRUE_IMMORTAL.stages(); stage++) {
			steps.add(new Rank(Realm.TRUE_IMMORTAL, 0, stage));
		}
		return List.copyOf(steps);
	}

	private AuraPreviews() {
	}
}
