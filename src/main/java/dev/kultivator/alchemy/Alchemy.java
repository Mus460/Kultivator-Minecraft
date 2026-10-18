package dev.kultivator.alchemy;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.registry.ModItems;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Alchemy (DESIGN sections 9 and 17): the Tungku Alkimia ({@link AlchemyFurnace}), pots ({@link AlchemyPots}), the
 * recipe table ({@link AlchemyRecipes}), the pills ({@link PillItem}, {@link PillEffects}) with their quality component
 * ({@link AlchemyComponents}), and the cultivator's alchemy level: {@code min(10, floor(sqrt(alchemyXp / 40)))}.
 */
public final class Alchemy {

	public static final int MAX_LEVEL = 10;
	/** Level n needs {@code XP_PER_LEVEL x n^2} alchemy XP. */
	public static final double XP_PER_LEVEL = 40.0D;

	private static final int HELP_LINES = 6;
	private static final int LEVEL_COLOR = 0xFFB060;

	private static final SuggestionProvider<CommandSourceStack> PILLS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(PillKind.values()).map(PillKind::shortId), builder);

	public static void init() {
		AlchemyComponents.init();
		AlchemyItems.init();
		UseBlockCallback.EVENT.register(AlchemyFurnace::onUseBlock);
		ServerTickEvents.END_SERVER_TICK.register(AlchemyFurnace::tickServer);
		CreativeModeTabEvents.modifyOutputEvent(ModItems.TAB_KEY).register(Alchemy::addQualitiesToTab);
	}

	/** The creative tab lists every pill in all four qualities (Low, Mid, High, Perfect) instead of only the Mid one. */
	private static void addQualitiesToTab(FabricCreativeModeTabOutput output) {
		for (PillKind kind : PillKind.values()) {
			Item item = AlchemyItems.get(kind);
			output.insertAfter(item, List.of(
					AlchemyComponents.withQuality(new ItemStack(item), PillQuality.HIGH),
					AlchemyComponents.withQuality(new ItemStack(item), PillQuality.PERFECT)));
			output.insertBefore(item, List.of(AlchemyComponents.withQuality(new ItemStack(item), PillQuality.LOW)));
		}
	}

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("alchemy")
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
				.then(Commands.literal("recipes").executes(ctx -> recipes(ctx.getSource())))
				.then(Commands.literal("level").executes(ctx -> showLevel(ctx.getSource(), ctx.getSource().getPlayerOrException()))));
		debug.then(Commands.literal("alchemy")
				.then(Commands.literal("xp")
						.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D, 1.0E7D))
								.executes(ctx -> debugXp(ctx, DoubleArgumentType.getDouble(ctx, "amount")))))
				.then(Commands.literal("give")
						.then(Commands.argument("pill", StringArgumentType.word()).suggests(PILLS)
								.then(Commands.argument("quality", IntegerArgumentType.integer(0, PillQuality.values().length - 1))
										.executes(ctx -> debugGive(ctx, 1))
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
												.executes(ctx -> debugGive(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))));
	}

	// ================================================================ alchemy level

	/** Alchemy level 0..10 for {@code xp}. */
	public static int level(double xp) {
		return Math.min(MAX_LEVEL, (int) Math.floor(Math.sqrt(Math.max(0.0D, xp) / XP_PER_LEVEL)));
	}

	public static int level(CultivationData data) {
		return level(data.alchemyXp());
	}

	/** Alchemy XP needed for {@code level}. */
	public static double xpForLevel(int level) {
		return XP_PER_LEVEL * level * level;
	}

	/** Adds alchemy XP; announces a new level and grants the master advancement at level 10. */
	public static void addXp(ServerPlayer player, double amount) {
		CultivationData data = Cultivators.get(player);
		int before = level(data);
		data.setAlchemyXp(data.alchemyXp() + Math.max(0.0D, amount));
		int after = level(data);
		Cultivators.dirty(player);
		if (after > before) {
			Msg.chat(player, Component.translatable("kultivator.alchemy.level_up", after,
					Msg.number(AlchemyFurnace.successChance(after, false, false) * 100.0D + 1.0E-6D)).withColor(LEVEL_COLOR));
			player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.PLAYER_LEVELUP,
					SoundSource.PLAYERS, 0.8F, 1.2F);
		}
		if (after >= MAX_LEVEL) {
			Advancements.grant(player, "master_alchemist");
		}
	}

	// ================================================================ commands

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.alchemy.help.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (int i = 1; i <= HELP_LINES; i++) {
			int line = i;
			source.sendSuccess(() -> Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.translatable("kultivator.alchemy.help." + line).withStyle(ChatFormatting.GRAY)), false);
		}
		source.sendSuccess(() -> Component.translatable("kultivator.alchemy.help.recipes").withStyle(ChatFormatting.GOLD), false);
		for (AlchemyRecipes.Recipe recipe : AlchemyRecipes.all()) {
			source.sendSuccess(() -> Component.literal("  ◆ ").withColor(recipe.color())
					.append(Component.translatable("kultivator.alchemy.help.recipe." + recipe.id()).withStyle(ChatFormatting.GRAY)), false);
		}
		return 1;
	}

	private static int recipes(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.alchemy.cmd.recipes.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (AlchemyRecipes.Recipe recipe : AlchemyRecipes.all()) {
			Component name = recipe.displayName();
			source.sendSuccess(() -> Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.translatable("kultivator.alchemy.cmd.recipes.entry", name, recipe.count(),
							AlchemyRecipes.ingredientList(recipe).withStyle(ChatFormatting.GRAY))), false);
		}
		source.sendSuccess(() -> Component.translatable("kultivator.alchemy.cmd.recipes.footer").withStyle(ChatFormatting.DARK_GRAY,
				ChatFormatting.ITALIC), false);
		return AlchemyRecipes.all().size();
	}

	private static int showLevel(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		int level = level(data);
		Component line = level >= MAX_LEVEL
				? Component.translatable("kultivator.alchemy.cmd.level_max", level, Msg.number(data.alchemyXp()), data.pillsRefined())
				: Component.translatable("kultivator.alchemy.cmd.level", level, MAX_LEVEL, Msg.number(data.alchemyXp()),
				Msg.number(xpForLevel(level + 1)), data.pillsRefined());
		source.sendSuccess(() -> Msg.prefixed(line.copy().withColor(LEVEL_COLOR)), false);
		boolean fire = data.dao() == Dao.FIRE;
		String base = Msg.number(AlchemyFurnace.successChance(level, false, fire) * 100.0D + 1.0E-6D);
		String soul = Msg.number(AlchemyFurnace.successChance(level, true, fire) * 100.0D + 1.0E-6D);
		source.sendSuccess(() -> Component.translatable(fire ? "kultivator.alchemy.cmd.chance_fire" : "kultivator.alchemy.cmd.chance", base, soul)
				.withStyle(ChatFormatting.GRAY), false);
		return level;
	}

	private static int debugXp(CommandContext<CommandSourceStack> ctx, double amount) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CultivationData data = Cultivators.get(player);
		double current = data.alchemyXp();
		if (amount >= current) {
			addXp(player, amount - current);
		} else {
			data.setAlchemyXp(amount);
			Cultivators.dirty(player);
		}
		int level = level(Cultivators.get(player));
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.alchemy.debug.xp", Msg.number(amount), level)), false);
		return 1;
	}

	private static int debugGive(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "pill");
		PillKind kind = PillKind.byId(id);
		if (kind == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.alchemy.debug.unknown_pill", id)));
			return 0;
		}
		PillQuality quality = PillQuality.byOrdinal(IntegerArgumentType.getInteger(ctx, "quality"));
		ItemStack stack = AlchemyComponents.withQuality(new ItemStack(AlchemyItems.get(kind), count), quality);
		Component name = stack.getHoverName().copy();
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			AlchemyFurnace.spawn(player.level(), player.position().add(0.0D, 0.5D, 0.0D), stack, 0.1D);
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.alchemy.debug.give", count, name,
				quality.displayName())), false);
		return 1;
	}

	private Alchemy() {
	}
}
