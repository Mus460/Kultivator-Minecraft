package dev.kultivator.goals;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * P7-jalan (DESIGN 19.3.9, 19.3.10): the Jalan Kultivasi tablet with Langkah Pertama ({@link Guide}), Ujian Fondasi
 * ({@link RealmTrials}) and Kewajiban Harian ({@link DailyChores}), their counters ({@link GoalCounters}) and the Persiapan
 * data ({@link Prep}). Pure player data in {@code extra.goals}; checks every 20/100 ticks plus one kill and one
 * block-break listener. {@link #init()} runs before {@code ModItems.init()} and never reads {@code TechniqueRegistry}.
 */
public final class Goals {

	/** {@code sessionExtra.hud_demo}: the game time until which {@code uji hud} shows sample HUD values. */
	public static final String HUD_DEMO = "hud_demo";
	public static final int HUD_DEMO_TICKS = 200;

	public static void init() {
		Advancements.addListener(Guide::onAdvancement);
		RealmTrials.init();
		ServerPlayerEvents.JOIN.register(player -> {
			CultivationData data = Cultivators.get(player);
			Guide.backfill(player, data);
			RealmTrials.update(player, data);
			DailyChores.update(player, data);
		});
		ServerPlayerEvents.LEAVE.register(GoalCounters::forget);
		ServerLivingEntityEvents.AFTER_DEATH.register(GoalCounters::onDeath);
		PlayerBlockBreakEvents.AFTER.register(GoalCounters::onBlockBroken);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GoalCounters.clear());
	}

	/** Every server tick for each player: counters every 20 ticks, the guide, trials, chores and Persiapan every 100. */
	public static void tick(ServerPlayer player, CultivationData data) {
		if (player.tickCount % 20 == 7) {
			GoalCounters.second(player, data);
		}
		if (player.tickCount % 100 == 13) {
			GoalCounters.every100(player, data);
			Guide.check(player, data);
			boolean changed = RealmTrials.update(player, data);
			changed |= DailyChores.update(player, data);
			changed |= Prep.update(player, data);
			if (changed) {
				Cultivators.dirty(player);
			}
		}
	}

	/** Action {@code JALAN}: text = "hide", "show", "claim:N" (N = chapter 1-5) or "seen:codex" (DESIGN 19.2.1). */
	public static void handleAction(ServerPlayer player, String text) {
		CultivationData data = Cultivators.get(player);
		CompoundTag goals = GoalCounters.goals(data);
		if ("hide".equals(text) || "show".equals(text)) {
			goals.putBoolean(Guide.HIDDEN, "hide".equals(text));
			Cultivators.dirty(player);
		} else if ("seen:codex".equals(text)) {
			if (!goals.getBooleanOr(Guide.SEEN, false)) {
				goals.putBoolean(Guide.SEEN, true);
				Cultivators.dirty(player);
				Guide.check(player, data);
			}
		} else if (text != null && text.startsWith("claim:")) {
			try {
				Guide.claim(player, Integer.parseInt(text.substring("claim:".length())));
			} catch (NumberFormatException ignored) {
				// A malformed claim from a modified client: nothing to do.
			}
		}
	}

	/** {@code /kultivasi jalan} (opens the Jalan tab, DESIGN 19.2.2) and {@code harian} (today's chores in chat). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("jalan").executes(ctx -> {
			Net.send(ctx.getSource().getPlayerOrException(), new Payloads.OpenScreen(Payloads.OpenScreen.INFO, Payloads.OpenScreen.TAB_JALAN));
			return 1;
		}));
		root.then(Commands.literal("harian").executes(ctx -> DailyChores.print(ctx.getSource().getPlayerOrException())));
	}

	/** {@code uji jalan reset|bab|ujian}, {@code uji harian ulang|selesai|beruntun} and {@code uji hud} (DESIGN 19.4). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("jalan")
				.then(Commands.literal("reset").executes(Goals::reset))
				.then(Commands.literal("bab")
						.then(Commands.argument("n", IntegerArgumentType.integer(1, Guide.CHAPTERS))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Guide.completeUpTo(player, IntegerArgumentType.getInteger(ctx, "n"));
									return ok(ctx, Component.translatable("kultivator.goals.test.chapters", IntegerArgumentType.getInteger(ctx, "n")));
								})))
				.then(Commands.literal("ujian")
						.executes(ctx -> RealmTrials.print(ctx.getSource().getPlayerOrException()))
						.then(Commands.literal("selesai").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							if (!RealmTrials.completeAll(player)) {
								ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.goals.trial.none")));
								return 0;
							}
							return ok(ctx, Component.translatable("kultivator.goals.test.trials"));
						}))));
		uji.then(Commands.literal("harian")
				.then(Commands.literal("ulang").executes(ctx -> {
					DailyChores.reroll(ctx.getSource().getPlayerOrException());
					return ok(ctx, Component.translatable("kultivator.goals.test.daily_reroll"));
				}))
				.then(Commands.literal("selesai").executes(ctx -> {
					DailyChores.completeAll(ctx.getSource().getPlayerOrException());
					return ok(ctx, Component.translatable("kultivator.goals.test.daily_done"));
				}))
				.then(Commands.literal("beruntun")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 1000))
								.executes(ctx -> {
									int n = IntegerArgumentType.getInteger(ctx, "n");
									DailyChores.setStreak(ctx.getSource().getPlayerOrException(), n);
									return ok(ctx, Component.translatable("kultivator.goals.test.streak", n));
								}))));
		uji.then(Commands.literal("hud").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			CultivationData data = Cultivators.get(player);
			data.sessionExtra().putLong(HUD_DEMO, player.level().getGameTime() + HUD_DEMO_TICKS);
			Cultivators.syncNow(player);
			return ok(ctx, Component.translatable("kultivator.goals.test.hud"));
		}));
	}

	/** {@code uji jalan reset}: forgets the guide, the trials and the chores (the lifetime counters stay). */
	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CultivationData data = Cultivators.get(player);
		CompoundTag goals = GoalCounters.goals(data);
		for (String key : new String[]{Guide.KEY, Guide.CLAIMED, Guide.HIDDEN, Guide.SEEN, RealmTrials.KEY, DailyChores.KEY}) {
			goals.remove(key);
		}
		Cultivators.syncNow(player);
		return ok(ctx, Component.translatable("kultivator.goals.test.reset"));
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendSuccess(() -> Msg.prefixed(message), false);
		return 1;
	}

	private Goals() {
	}
}
