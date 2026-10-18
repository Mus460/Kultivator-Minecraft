package dev.kultivator.harta;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.Element;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The commands of P7-harta (DESIGN 19.4): {@code resep} and the op test commands {@code uji batu|herba|inti|pil|relik}. */
final class HartaCommands {

	private static final String[] STONE_GRADES = {"rendah", "menengah", "tinggi", "agung"};
	private static final String[] CORE_GRADES = {"rendah", "menengah", "tinggi", "raja"};
	/** Indonesian element names in {@code Element} order (English ids are accepted too). */
	private static final String[] ELEMENT_NAMES = {"logam", "kayu", "air", "api", "tanah", "petir", "es", "angin", "ruang"};
	private static final String[] NEW_PILLS = {"dao_insight", "toxin_purging", "enlightenment", "bone_tempering"};
	private static final String[] QUALITIES = {"rendah", "menengah", "tinggi", "sempurna"};

	private static final SuggestionProvider<CommandSourceStack> STONES = (ctx, builder) -> SharedSuggestionProvider.suggest(STONE_GRADES, builder);
	private static final SuggestionProvider<CommandSourceStack> CORES = (ctx, builder) -> SharedSuggestionProvider.suggest(CORE_GRADES, builder);
	private static final SuggestionProvider<CommandSourceStack> ELEMENTS = (ctx, builder) -> SharedSuggestionProvider.suggest(ELEMENT_NAMES, builder);
	private static final SuggestionProvider<CommandSourceStack> PILLS = (ctx, builder) -> SharedSuggestionProvider.suggest(NEW_PILLS, builder);
	private static final SuggestionProvider<CommandSourceStack> QUALITY = (ctx, builder) -> SharedSuggestionProvider.suggest(QUALITIES, builder);
	private static final SuggestionProvider<CommandSourceStack> HERBS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		ids.add("semua");
		for (HerbKind kind : HerbKind.values()) {
			ids.add(kind.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> TABLES = (ctx, builder) -> SharedSuggestionProvider.suggest(HartaLoot.tableNames(), builder);

	static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("resep").executes(ctx -> {
			Net.send(ctx.getSource().getPlayerOrException(), new Payloads.OpenScreen(Payloads.OpenScreen.INFO, Payloads.OpenScreen.TAB_ALKIMIA));
			return 1;
		}));
	}

	static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("batu")
				.then(Commands.argument("tingkat", StringArgumentType.word()).suggests(STONES)
						.executes(ctx -> stones(ctx, 16))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 640))
								.executes(ctx -> stones(ctx, IntegerArgumentType.getInteger(ctx, "n"))))));
		uji.then(Commands.literal("herba")
				.then(Commands.literal("tampak").executes(HartaCommands::forceHerb))
				.then(Commands.argument("id", StringArgumentType.word()).suggests(HERBS)
						.executes(ctx -> herbs(ctx, SpiritHerbs.DEFAULT_AGE))
						.then(Commands.argument("umur", IntegerArgumentType.integer(1, HartaComponents.MAX_HERB_AGE))
								.executes(ctx -> herbs(ctx, IntegerArgumentType.getInteger(ctx, "umur"))))));
		uji.then(Commands.literal("inti")
				.then(Commands.argument("tingkat", StringArgumentType.word()).suggests(CORES)
						.executes(ctx -> cores(ctx, null, 1))
						.then(Commands.argument("unsur", StringArgumentType.word()).suggests(ELEMENTS)
								.executes(ctx -> cores(ctx, StringArgumentType.getString(ctx, "unsur"), 1))
								.then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> cores(ctx, StringArgumentType.getString(ctx, "unsur"), IntegerArgumentType.getInteger(ctx, "n")))))));
		uji.then(Commands.literal("pil")
				.then(Commands.argument("jenis", StringArgumentType.word()).suggests(PILLS)
						.executes(ctx -> pill(ctx, null))
						.then(Commands.argument("kualitas", StringArgumentType.word()).suggests(QUALITY)
								.executes(ctx -> pill(ctx, StringArgumentType.getString(ctx, "kualitas"))))));
		uji.then(Commands.literal("relik")
				.then(Commands.argument("tabel", StringArgumentType.word()).suggests(TABLES)
						.executes(HartaCommands::relic)));
	}

	// ================================================================ test commands

	private static int stones(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "tingkat").toLowerCase(Locale.ROOT);
		int grade = index(STONE_GRADES, name, new String[]{"low", "mid", "high", "supreme"});
		if (grade < 0) {
			return unknown(ctx, name);
		}
		int left = count;
		while (left > 0) {
			int part = Math.min(64, left);
			give(player, SpiritStones.stack(grade, part));
			left -= part;
		}
		Component stone = SpiritStones.name(grade);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.harta.test.given", count, stone)), false);
		return count;
	}

	private static int herbs(CommandContext<CommandSourceStack> ctx, int age) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		List<HerbKind> kinds = new ArrayList<>();
		if (id.equals("semua") || id.equals("all")) {
			kinds.addAll(Arrays.asList(HerbKind.values()));
		} else {
			HerbKind kind = HerbKind.byId(id);
			if (kind == null) {
				return unknown(ctx, id);
			}
			kinds.add(kind);
		}
		for (HerbKind kind : kinds) {
			give(player, SpiritHerbs.create(kind, age));
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.harta.test.herbs", kinds.size(), age)), false);
		return kinds.size();
	}

	private static int forceHerb(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SpiritHerbs.force(player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.harta.test.herb_force", SpiritHerbs.FORCE_TICKS / 20)), false);
		return 1;
	}

	private static int cores(CommandContext<CommandSourceStack> ctx, @Nullable String elementName, int count) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "tingkat").toLowerCase(Locale.ROOT);
		int index = index(CORE_GRADES, name, new String[]{"low", "mid", "high", "king"});
		if (index < 0) {
			return unknown(ctx, name);
		}
		Element element = null;
		if (elementName != null) {
			element = element(elementName);
			if (element == null) {
				return unknown(ctx, elementName);
			}
		}
		Element[] all = Element.values();
		ItemStack shown = ItemStack.EMPTY;
		for (int i = 0; i < count; i++) {
			Element each = element != null ? element : all[player.getRandom().nextInt(all.length)];
			ItemStack core = BeastCores.create(each, index + 1);
			shown = core.copy();
			give(player, core);
		}
		Component coreName = shown.getHoverName().copy();
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.harta.test.given", count, coreName)), false);
		return count;
	}

	private static int pill(CommandContext<CommandSourceStack> ctx, @Nullable String qualityName) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "jenis").toLowerCase(Locale.ROOT);
		PillKind kind = PillKind.byId(id);
		if (kind == null || !kind.phase7()) {
			return unknown(ctx, id);
		}
		PillQuality quality = PillQuality.MID;
		if (qualityName != null) {
			String lower = qualityName.toLowerCase(Locale.ROOT);
			int index = index(QUALITIES, lower, new String[]{"low", "mid", "high", "perfect"});
			if (index < 0) {
				return unknown(ctx, qualityName);
			}
			quality = PillQuality.byOrdinal(index);
		}
		ItemStack stack = AlchemyComponents.withQuality(new ItemStack(AlchemyItems.get(kind)), quality);
		Component name = stack.getHoverName().copy();
		give(player, stack);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.harta.test.given", 1, name)), false);
		return 1;
	}

	private static int relic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "tabel").toLowerCase(Locale.ROOT);
		List<ItemStack> rolled = new ArrayList<>();
		List<Component> lines = HartaLoot.roll(player, name, rolled);
		if (lines.isEmpty()) {
			return unknown(ctx, name);
		}
		for (ItemStack stack : rolled) {
			give(player, stack);
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.harta.test.relic", name, rolled.size())
				.withStyle(ChatFormatting.GOLD)), false);
		for (Component line : lines) {
			ctx.getSource().sendSuccess(() -> line.copy().withStyle(ChatFormatting.GRAY), false);
		}
		if (!dev.kultivator.KultivatorConfig.get().structureLoot) {
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.harta.test.relic_off").withStyle(ChatFormatting.RED), false);
		}
		return rolled.size();
	}

	// ================================================================ helpers

	private static int index(String[] names, String name, String[] english) {
		for (int i = 0; i < names.length; i++) {
			if (names[i].equals(name) || english[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/** Indonesian element name or English id. */
	static @Nullable Element element(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			if (ELEMENT_NAMES[i].equals(lower)) {
				return Element.values()[i];
			}
		}
		return Element.byId(lower);
	}

	private static int unknown(CommandContext<CommandSourceStack> ctx, String what) {
		ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.harta.test.unknown", what)));
		return 0;
	}

	/** Into the inventory, or dropped at the feet as a block-safe item. */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			item.setPortalCooldown();
			player.level().addFreshEntity(item);
		}
	}

	private HartaCommands() {
	}
}
