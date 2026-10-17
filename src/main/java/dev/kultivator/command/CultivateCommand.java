package dev.kultivator.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.PillToxicity;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.cultivation.Stats;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.dao.DaoPaths;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.ModAttachments;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.data.Technique;
import dev.kultivator.data.XpSource;
import dev.kultivator.network.Net;
import dev.kultivator.alchemy.Alchemy;
import dev.kultivator.ascension.Ascension;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.formation.ProtectiveFormations;
import dev.kultivator.technique.Techniques;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /cultivate} and its alias {@code /kultivasi}. Player subcommands plus op-only {@code debug} test commands.
 * Modules add their own subcommands through their {@code addCommands(root, debug)}; that method is called once per
 * alias, so it must only build nodes.
 */
public final class CultivateCommand {

	/** Name of the optional target argument of the debug commands. */
	private static final String PLAYER = "player";
	private static final String[] COSMETICS = {"aura", "robe", "back_sword", "eyes", "dao_circle"};
	private static final int[] COSMETIC_FLAGS = {CultivationData.COSMETIC_AURA, CultivationData.COSMETIC_ROBE,
			CultivationData.COSMETIC_BACK_SWORD, CultivationData.COSMETIC_EYES, CultivationData.COSMETIC_DAO_CIRCLE};

	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Realm.values()).map(Realm::id), builder);
	private static final SuggestionProvider<CommandSourceStack> DAOS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(Arrays.stream(Dao.values()).map(Dao::id),
					java.util.stream.Stream.of("none")), builder);
	private static final SuggestionProvider<CommandSourceStack> GRADES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(RootGrade.values()).map(RootGrade::id), builder);
	private static final SuggestionProvider<CommandSourceStack> TECHNIQUES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(Arrays.stream(Technique.values()).map(Technique::id),
					java.util.stream.Stream.of("all")), builder);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(build("cultivate"));
		dispatcher.register(build("kultivasi"));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).executes(ctx -> info(ctx.getSource(), self(ctx)));
		root.then(Commands.literal("info").executes(ctx -> info(ctx.getSource(), self(ctx))));
		root.then(Commands.literal("hardcore")
				.then(Commands.literal("on").executes(ctx -> {
					Net.setHardcore(self(ctx), true);
					return 1;
				}))
				.then(Commands.literal("off").executes(ctx -> {
					Net.setHardcore(self(ctx), false);
					return 1;
				})));
		LiteralArgumentBuilder<CommandSourceStack> visual = Commands.literal("visual");
		for (int i = 0; i < COSMETICS.length; i++) {
			int flag = COSMETIC_FLAGS[i];
			String part = COSMETICS[i];
			visual.then(Commands.literal(part)
					.then(Commands.literal("on").executes(ctx -> setCosmetic(ctx, part, flag, true)))
					.then(Commands.literal("off").executes(ctx -> setCosmetic(ctx, part, flag, false))));
		}
		root.then(visual);

		LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		addDebug(debug);

		Meditation.addCommands(root, debug);
		Breakthrough.addCommands(root, debug);
		PillToxicity.addCommands(root, debug);
		SpiritRoots.addCommands(root, debug);
		DaoPaths.addCommands(root, debug);
		Techniques.addCommands(root, debug);
		HeartDemons.addCommands(root, debug);
		SpiritBeasts.addCommands(root, debug);
		ProtectiveFormations.addCommands(root, debug);
		Alchemy.addCommands(root, debug);
		Ascension.addCommands(root, debug);

		root.then(debug);
		return root;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	/** The debug target: the {@code player} argument when the command has one, else the executing player. */
	private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		try {
			return EntityArgument.getPlayer(ctx, PLAYER);
		} catch (IllegalArgumentException absent) {
			// Brigadier throws this when the argument is not part of the parsed command.
			return self(ctx);
		}
	}

	/** {@code node} runs {@code command} as it is and also with a trailing {@code [player]} target. */
	private static <T extends ArgumentBuilder<CommandSourceStack, T>> T targeted(T node, Command<CommandSourceStack> command) {
		return node.executes(command).then(Commands.argument(PLAYER, EntityArgument.player()).executes(command));
	}

	// ================================================================ player commands

	/** A short status card in chat. */
	public static int info(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Rank rank = data.rank();
		source.sendSuccess(() -> Component.translatable("kultivator.core.info.header", player.getDisplayName())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> line("kultivator.core.info.rank", rank.displayName().withColor(rank.realm().color())), false);
		source.sendSuccess(() -> line("kultivator.core.info.xp", Component.literal(Msg.number(data.xp()) + " / "
				+ Msg.number(rank.xpRequired()) + (data.barFull() && rank.isBottleneck() ? " ★" : ""))), false);
		if (data.storedQi() > 0.0D) {
			source.sendSuccess(() -> line("kultivator.core.info.stored", Component.literal(Msg.number(data.storedQi()))
					.withStyle(ChatFormatting.LIGHT_PURPLE)), false);
		}
		source.sendSuccess(() -> line("kultivator.core.info.qi", Component.literal(Msg.number(data.qi()) + " / "
				+ Msg.number(Qi.max(data))).withStyle(ChatFormatting.AQUA)), false);
		source.sendSuccess(() -> line("kultivator.core.info.root", data.rootTested()
				? SpiritRoots.describe(data.rootElements(), data.rootGrade())
				: Component.translatable("kultivator.core.info.root_unknown").withStyle(ChatFormatting.GRAY)), false);
		Dao dao = data.dao();
		source.sendSuccess(() -> line("kultivator.core.info.dao", dao == null
				? Component.translatable("kultivator.core.info.dao_none").withStyle(ChatFormatting.GRAY)
				: Component.translatable("kultivator.core.info.dao_value", dao.displayName(), Msg.number(data.comprehension(dao)),
				Dao.comprehensionName(data.comprehension(dao))).withColor(dao.color())), false);
		if (data.deviating()) {
			source.sendSuccess(() -> line("kultivator.core.info.deviation", Component.literal((data.deviationTicks() / 20) + "s")
					.withStyle(ChatFormatting.RED)), false);
		}
		if (data.hardcore()) {
			source.sendSuccess(() -> Component.translatable("kultivator.core.info.hardcore").withStyle(ChatFormatting.DARK_RED), false);
		}
		return 1;
	}

	private static MutableComponent line(String key, Component value) {
		return Component.translatable(key).withStyle(ChatFormatting.GRAY).append(Component.literal(" ")).append(value);
	}

	private static int setCosmetic(CommandContext<CommandSourceStack> ctx, String part, int flag, boolean on) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		Cultivators.get(player).setCosmetic(flag, on);
		Cultivators.syncNow(player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable(on ? "kultivator.core.visual_on" : "kultivator.core.visual_off",
				Component.translatable("kultivator.core.visual." + part))), false);
		return 1;
	}

	// ================================================================ debug commands

	/**
	 * The debug commands of the spine. Each one acts on the executing player, or on the optional trailing
	 * {@code [player]} (see {@link #target}), so an operator, the console or a command block can set up anyone.
	 */
	private static void addDebug(LiteralArgumentBuilder<CommandSourceStack> debug) {
		LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
		Command<CommandSourceStack> realmOnly = ctx -> setRealm(ctx, 0, 0);
		Command<CommandSourceStack> realmStage = ctx -> setRealm(ctx, IntegerArgumentType.getInteger(ctx, "stage") - 1, 0);
		Command<CommandSourceStack> realmLayer = ctx -> setRealm(ctx, IntegerArgumentType.getInteger(ctx, "stage") - 1,
				IntegerArgumentType.getInteger(ctx, "layer") - 1);
		// The [player] child goes AFTER the number child on each node: brigadier keeps the first of two equal parses, and
		// a number would also parse as a player name, so "2" stays the stage / layer.
		set.then(Commands.literal("realm")
				.then(Commands.argument("realm", StringArgumentType.word()).suggests(REALMS)
						.executes(realmOnly)
						.then(Commands.argument("stage", IntegerArgumentType.integer(1, 4))
								.executes(realmStage)
								.then(targeted(Commands.argument("layer", IntegerArgumentType.integer(1, 9)), realmLayer))
								.then(Commands.argument(PLAYER, EntityArgument.player()).executes(realmStage)))
						.then(Commands.argument(PLAYER, EntityArgument.player()).executes(realmOnly))));
		set.then(Commands.literal("qi")
				.then(targeted(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D)), ctx -> {
					ServerPlayer player = target(ctx);
					CultivationData data = Cultivators.get(player);
					data.setQi(Math.min(Qi.max(data), DoubleArgumentType.getDouble(ctx, "amount")));
					return done(ctx, player);
				})));
		set.then(Commands.literal("xp")
				.then(targeted(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D)), ctx -> {
					ServerPlayer player = target(ctx);
					CultivationData data = Cultivators.get(player);
					data.setXp(Math.min(data.rank().xpRequired(), DoubleArgumentType.getDouble(ctx, "amount")));
					return done(ctx, player);
				})));
		set.then(Commands.literal("storedqi")
				.then(targeted(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D)), ctx -> {
					ServerPlayer player = target(ctx);
					Cultivators.get(player).setStoredQi(DoubleArgumentType.getDouble(ctx, "amount"));
					return done(ctx, player);
				})));
		set.then(Commands.literal("root")
				.then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
						.then(Commands.argument("elements", StringArgumentType.greedyString())
								.executes(CultivateCommand::setRoot))));
		// A player name would also parse as a grade word (and the elements are greedy), so another player's root has its
		// own path: set root_of <player> <grade> <elements>.
		set.then(Commands.literal("root_of")
				.then(Commands.argument(PLAYER, EntityArgument.player())
						.then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
								.then(Commands.argument("elements", StringArgumentType.greedyString())
										.executes(CultivateCommand::setRoot)))));
		set.then(Commands.literal("dao")
				.then(targeted(Commands.argument("dao", StringArgumentType.word()).suggests(DAOS), ctx -> {
					ServerPlayer player = target(ctx);
					String id = StringArgumentType.getString(ctx, "dao");
					Dao dao = Dao.byId(id);
					if (dao == null && !id.equals("none")) {
						return fail(ctx, "kultivator.core.debug.unknown", id);
					}
					Cultivators.get(player).setDao(dao);
					Techniques.refreshUnlocks(player);
					Stats.apply(player);
					return done(ctx, player);
				})));
		set.then(Commands.literal("comprehension")
				.then(targeted(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D)), ctx -> {
					ServerPlayer player = target(ctx);
					CultivationData data = Cultivators.get(player);
					Dao dao = data.dao();
					if (dao == null) {
						return fail(ctx, "kultivator.core.debug.no_dao", "");
					}
					// Through Comprehension.set so crossed levels are announced and their advancements granted.
					Comprehension.set(player, dao, DoubleArgumentType.getDouble(ctx, "value"));
					return done(ctx, player);
				})));
		set.then(Commands.literal("quality")
				.then(targeted(Commands.argument("quality", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(BreakthroughQuality.values())
								.map(BreakthroughQuality::id), builder)), ctx -> {
					ServerPlayer player = target(ctx);
					String id = StringArgumentType.getString(ctx, "quality");
					BreakthroughQuality quality = Arrays.stream(BreakthroughQuality.values()).filter(q -> q.id().equals(id))
							.findFirst().orElse(null);
					if (quality == null) {
						return fail(ctx, "kultivator.core.debug.unknown", id);
					}
					CultivationData data = Cultivators.get(player);
					data.setQuality(data.realm(), quality);
					Stats.apply(player);
					return done(ctx, player);
				})));
		debug.then(set);

		debug.then(Commands.literal("addxp")
				.then(targeted(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D)), ctx -> {
					ServerPlayer player = target(ctx);
					Progression.addXp(player, DoubleArgumentType.getDouble(ctx, "amount"), XpSource.EXACT);
					return done(ctx, player);
				})));
		debug.then(targeted(Commands.literal("fill"), ctx -> {
			ServerPlayer player = target(ctx);
			CultivationData data = Cultivators.get(player);
			data.setXp(data.rank().xpRequired());
			data.setQi(Qi.max(data));
			return done(ctx, player);
		}));
		debug.then(Commands.literal("learn")
				.then(targeted(Commands.argument("technique", StringArgumentType.word()).suggests(TECHNIQUES), ctx -> learn(ctx, true))));
		debug.then(Commands.literal("forget")
				.then(targeted(Commands.argument("technique", StringArgumentType.word()).suggests(TECHNIQUES), ctx -> learn(ctx, false))));
		debug.then(Commands.literal("deviation").then(targeted(Commands.literal("clear"), ctx -> {
			ServerPlayer player = target(ctx);
			dev.kultivator.cultivation.Deviation.clear(player);
			return done(ctx, player);
		})));
		debug.then(Commands.literal("toxicity").then(targeted(Commands.literal("clear"), ctx -> {
			ServerPlayer player = target(ctx);
			Cultivators.get(player).setToxicity(0.0D);
			return done(ctx, player);
		})));
		debug.then(targeted(Commands.literal("reset"), ctx -> {
			ServerPlayer player = target(ctx);
			player.setAttached(ModAttachments.CULTIVATION, new CultivationData());
			Stats.apply(player);
			Techniques.refreshUnlocks(player);
			return done(ctx, player);
		}));
		debug.then(Commands.literal("show")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
	}

	private static int setRealm(CommandContext<CommandSourceStack> ctx, int stage, int layer) throws CommandSyntaxException {
		ServerPlayer player = target(ctx);
		String id = StringArgumentType.getString(ctx, "realm");
		Realm realm = Realm.byId(id);
		if (realm == null) {
			return fail(ctx, "kultivator.core.debug.unknown", id);
		}
		CultivationData data = Cultivators.get(player);
		for (Realm each : Realm.values()) {
			// Every realm up to the new one counts as reached (Normal quality unless one is already recorded).
			if (each.ordinal() <= realm.ordinal() && data.quality(each) == null) {
				data.setQuality(each, BreakthroughQuality.NORMAL);
			} else if (each.ordinal() > realm.ordinal() && !each.isMortal()) {
				data.setQuality(each, null);
			}
		}
		data.setXp(0.0D);
		Progression.setRank(player, new Rank(realm, layer, stage));
		data.setQi(Qi.max(data));
		Techniques.refreshUnlocks(player);
		return done(ctx, player);
	}

	private static int setRoot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = target(ctx);
		String gradeId = StringArgumentType.getString(ctx, "grade");
		RootGrade grade = RootGrade.byId(gradeId);
		if (grade == null) {
			return fail(ctx, "kultivator.core.debug.unknown", gradeId);
		}
		int mask = 0;
		for (String part : StringArgumentType.getString(ctx, "elements").toLowerCase(Locale.ROOT).split("[ ,+]+")) {
			if (part.isEmpty()) {
				continue;
			}
			Element element = Element.byId(part);
			if (element == null) {
				return fail(ctx, "kultivator.core.debug.unknown", part);
			}
			mask |= element.bit();
		}
		if (mask == 0) {
			return fail(ctx, "kultivator.core.debug.unknown", "");
		}
		Cultivators.get(player).setRoot(mask, grade);
		Stats.apply(player);
		return done(ctx, player);
	}

	private static int learn(CommandContext<CommandSourceStack> ctx, boolean learn) throws CommandSyntaxException {
		ServerPlayer player = target(ctx);
		String id = StringArgumentType.getString(ctx, "technique");
		CultivationData data = Cultivators.get(player);
		if (id.equals("all")) {
			for (Technique technique : Technique.values()) {
				if (learn) {
					data.learn(technique);
				} else {
					data.forget(technique);
				}
			}
			if (learn) {
				// Selects a technique when none is selected and grants the collector advancement.
				Techniques.refreshUnlocks(player);
			}
			return done(ctx, player);
		}
		Technique technique = Technique.byId(id);
		if (technique == null) {
			return fail(ctx, "kultivator.core.debug.unknown", id);
		}
		if (learn) {
			data.learn(technique);
			Techniques.refreshUnlocks(player);
		} else {
			data.forget(technique);
		}
		return done(ctx, player);
	}

	private static int done(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		Cultivators.syncNow(player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.core.debug.done")), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String key, String arg) {
		ctx.getSource().sendFailure(Component.translatable(key, arg));
		return 0;
	}

	private CultivateCommand() {
	}
}
