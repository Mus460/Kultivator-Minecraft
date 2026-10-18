package dev.kultivator.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.Stats;
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
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.registry.ModItems;
import dev.kultivator.technique.Techniques;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /cultivate uji ...} (op only): quick ways to try everything — jump to any realm or straight to the top, a
 * realm tour that shows every realm's look for a few seconds and then gives the player's real data back, and test
 * kits with every item of the mod plus the rare vanilla items, formation and alchemy materials.
 */
public final class TestCommands {

	/** The realms the tour visits, each at its Peak (the densest aura). */
	private static final List<Rank> TOUR = List.of(
			Rank.MORTAL,
			new Rank(Realm.QI_REFINING, 0, 3),
			new Rank(Realm.FOUNDATION, 0, 3),
			new Rank(Realm.GOLDEN_CORE, 0, 3),
			new Rank(Realm.NASCENT_SOUL, 0, 3),
			new Rank(Realm.DEITY_TRANSFORMATION, 0, 3),
			new Rank(Realm.DAO_UNION, 0, 3),
			new Rank(Realm.IMMORTAL_ASCENSION, 0, 3),
			new Rank(Realm.IMMORTAL_ASCENSION, 4, 3),
			new Rank(Realm.IMMORTAL_ASCENSION, 8, 3),
			new Rank(Realm.TRUE_IMMORTAL, 0, 3));

	private static final List<ResourceKey<Enchantment>> BOOKS = List.of(Enchantments.SHARPNESS, Enchantments.SWEEPING_EDGE,
			Enchantments.FIRE_ASPECT, Enchantments.FLAME, Enchantments.FROST_WALKER, Enchantments.CHANNELING, Enchantments.PROTECTION,
			Enchantments.THORNS, Enchantments.MENDING, Enchantments.SWIFT_SNEAK, Enchantments.FEATHER_FALLING, Enchantments.INFINITY,
			Enchantments.LOYALTY, Enchantments.SMITE);

	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Realm.values()).map(Realm::id), builder);

	private static final class Tour {
		final CultivationData original;
		final int ticksPerStep;
		int index = -1;
		int ticks;

		Tour(CultivationData original, int ticksPerStep) {
			this.original = original;
			this.ticksPerStep = ticksPerStep;
		}
	}

	private static final Map<UUID, Tour> TOURS = new HashMap<>();

	/** A disciple on a realm tour: its real data, its master and the progress. Keyed by the disciple's UUID. */
	private static final class DiscipleTour {
		final CompoundTag original;
		final UUID master;
		final int ticksPerStep;
		int index = -1;
		int ticks;

		DiscipleTour(CompoundTag original, UUID master, int ticksPerStep) {
			this.original = original;
			this.master = master;
			this.ticksPerStep = ticksPerStep;
		}
	}

	private static final Map<UUID, DiscipleTour> DISCIPLE_TOURS = new HashMap<>();

	/** Suggests the names of the player's loaded disciples (spaces as underscores) and "semua". */
	private static final SuggestionProvider<CommandSourceStack> DISCIPLE_NAMES = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		names.add("semua");
		if (player != null) {
			for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
				names.add(token(disciple));
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	static {
		// Registered once, the first time the commands are built.
		ServerTickEvents.END_SERVER_TICK.register(TestCommands::tick);
		ServerPlayerEvents.JOIN.register(player -> {
			Tour tour = TOURS.remove(player.getUUID());
			if (tour != null) {
				restore(player, tour);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			Tour tour = TOURS.get(player.getUUID());
			if (tour != null) {
				// Put the real data back before the player is saved; JOIN restores again if the save came first.
				player.setAttached(ModAttachments.CULTIVATION, tour.original.copy());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Map.Entry<UUID, Tour> entry : Map.copyOf(TOURS).entrySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					restore(player, entry.getValue());
				}
			}
			TOURS.clear();
			for (Map.Entry<UUID, DiscipleTour> entry : Map.copyOf(DISCIPLE_TOURS).entrySet()) {
				DiscipleEntity disciple = findDisciple(entry.getValue().master, entry.getKey());
				if (disciple != null) {
					restoreDisciple(disciple, entry.getValue());
				}
			}
			DISCIPLE_TOURS.clear();
		});
	}

	/** Adds {@code uji ...} to /cultivate (op only). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
		LiteralArgumentBuilder<CommandSourceStack> uji = Commands.literal("uji");
		uji.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("maks").executes(ctx -> max(ctx)))
				.then(Commands.literal("ranah")
						.then(Commands.argument("realm", StringArgumentType.word()).suggests(REALMS)
								.executes(ctx -> realm(ctx, 4, 1))
								.then(Commands.argument("stage", IntegerArgumentType.integer(1, 4))
										.executes(ctx -> realm(ctx, IntegerArgumentType.getInteger(ctx, "stage"), 1))
										.then(Commands.argument("layer", IntegerArgumentType.integer(1, 9))
												.executes(ctx -> realm(ctx, IntegerArgumentType.getInteger(ctx, "stage"),
														IntegerArgumentType.getInteger(ctx, "layer")))))))
				.then(Commands.literal("tur")
						.executes(ctx -> tour(ctx, 6))
						.then(Commands.literal("stop").executes(ctx -> stopTour(ctx)))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(2, 60))
								.executes(ctx -> tour(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
				.then(Commands.literal("kit").executes(ctx -> kit(ctx)))
				.then(Commands.literal("teknik").executes(ctx -> techniques(ctx)))
				.then(Commands.literal("murid")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(DISCIPLE_NAMES)
								.then(Commands.literal("maks").executes(ctx -> discipleMax(ctx)))
								.then(Commands.literal("teknik").executes(ctx -> discipleTechniques(ctx)))
								.then(Commands.literal("ranah")
										.then(Commands.argument("realm", StringArgumentType.word()).suggests(REALMS)
												.executes(ctx -> discipleRealm(ctx, 4, 1))
												.then(Commands.argument("stage", IntegerArgumentType.integer(1, 4))
														.executes(ctx -> discipleRealm(ctx, IntegerArgumentType.getInteger(ctx, "stage"), 1))
														.then(Commands.argument("layer", IntegerArgumentType.integer(1, 9))
																.executes(ctx -> discipleRealm(ctx, IntegerArgumentType.getInteger(ctx, "stage"),
																		IntegerArgumentType.getInteger(ctx, "layer")))))))
								.then(Commands.literal("tur")
										.executes(ctx -> discipleTour(ctx, 6))
										.then(Commands.argument("seconds", IntegerArgumentType.integer(2, 60))
												.executes(ctx -> discipleTour(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
								// DESIGN 20.0 (phase 6, module D): the Bintang Abadi star tier.
								.then(Commands.literal("bintang")
										.then(Commands.argument("n", IntegerArgumentType.integer(0, 100000))
												.executes(ctx -> discipleStars(ctx, IntegerArgumentType.getInteger(ctx, "n")))))));
		// Phase 6b (DESIGN 21.22, 21.24): uji sekte ...
		dev.kultivator.sect.build.SectBuild.addTestCommands(uji);
		root.then(uji);
	}

	/** Sets the Bintang Abadi star tier through module D's API (True Immortal Peak first, calamities below it passed). */
	private static int discipleStars(CommandContext<CommandSourceStack> ctx, int stars) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		List<DiscipleEntity> found = targets(ctx);
		for (DiscipleEntity disciple : found) {
			dev.kultivator.disciple.path.DisciplePaths.setStars(disciple, stars);
			Msg.chat(player, Component.translatable("kultivator.dpath.test.stars", disciple.getName(), stars,
					dev.kultivator.disciple.path.StarTitles.title(stars)).withStyle(ChatFormatting.GOLD));
		}
		return found.size();
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.test.help.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (String key : List.of("maks", "ranah", "tur", "kit", "teknik", "murid", "more")) {
			source.sendSuccess(() -> Component.translatable("kultivator.test.help." + key).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	// ================================================================ realms

	/** Puts the player at {@code rank} ready to try it: tested root, every lower realm reached, techniques, full qi. */
	private static void prepare(ServerPlayer player, Rank rank, BreakthroughQuality quality, boolean everything) {
		CultivationData data = Cultivators.get(player);
		if (!data.rootTested() || everything) {
			int elements = data.rootTested() && data.rootElements() != 0 ? data.rootElements()
					: Element.METAL.bit() | Element.FIRE.bit() | Element.LIGHTNING.bit();
			data.setRoot(elements, everything ? RootGrade.HEAVENLY : (data.rootTested() ? data.rootGrade() : RootGrade.HIGH));
		}
		for (Realm each : Realm.values()) {
			if (each.isMortal()) {
				continue;
			}
			data.setQuality(each, each.ordinal() <= rank.realm().ordinal() ? quality : null);
		}
		if (rank.realm().atLeast(Realm.GOLDEN_CORE) && data.dao() == null) {
			data.setDao(Dao.SWORD);
		}
		if (everything && data.dao() != null) {
			data.setComprehension(data.dao(), 100.0D);
		}
		data.setXp(0.0D);
		data.setToxicity(0.0D);
		Deviation.clear(player);
		Progression.setRank(player, rank);
		if (everything) {
			for (Technique technique : Technique.values()) {
				data.learn(technique);
			}
		}
		Techniques.refreshUnlocks(player);
		Stats.apply(player);
		Qi.fill(player);
		player.setHealth(player.getMaxHealth());
		Cultivators.syncNow(player);
	}

	private static int max(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		Cultivators.get(player).setAscended(false);
		prepare(player, Rank.ofStep(Rank.MAX_STEP), BreakthroughQuality.HEAVENLY, true);
		Msg.chat(player, Component.translatable("kultivator.test.max").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int realm(CommandContext<CommandSourceStack> ctx, int stage, int layer) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		String id = StringArgumentType.getString(ctx, "realm");
		Realm realm = Realm.byId(id);
		if (realm == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.core.debug.unknown", id));
			return 0;
		}
		Rank rank = new Rank(realm, layer - 1, stage - 1);
		prepare(player, rank, BreakthroughQuality.PERFECT, false);
		Msg.chat(player, Component.translatable("kultivator.test.realm", rank.displayName().withColor(realm.color())));
		return 1;
	}

	private static int techniques(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		CultivationData data = Cultivators.get(player);
		for (Technique technique : Technique.values()) {
			data.learn(technique);
			data.setCooldown(technique, player.level().getGameTime(), 0);
		}
		Techniques.refreshUnlocks(player);
		Qi.fill(player);
		Cultivators.syncNow(player);
		Msg.chat(player, Component.translatable("kultivator.test.techniques"));
		return 1;
	}

	// ================================================================ tour

	private static int tour(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		Tour running = TOURS.remove(player.getUUID());
		CultivationData original = running != null ? running.original : Cultivators.get(player).copy();
		TOURS.put(player.getUUID(), new Tour(original, seconds * 20));
		Msg.chat(player, Component.translatable("kultivator.test.tour.start", TOUR.size(), seconds).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int stopTour(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		Tour tour = TOURS.remove(player.getUUID());
		if (tour != null) {
			restore(player, tour);
		}
		return 1;
	}

	private static void tick(MinecraftServer server) {
		tickDiscipleTours(server);
		if (TOURS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Tour> entry : Map.copyOf(TOURS).entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Tour tour = entry.getValue();
			if (player == null || !player.isAlive()) {
				continue;
			}
			if (tour.index >= 0 && ++tour.ticks < tour.ticksPerStep) {
				continue;
			}
			tour.ticks = 0;
			tour.index++;
			if (tour.index >= TOUR.size()) {
				TOURS.remove(entry.getKey());
				restore(player, tour);
				continue;
			}
			Rank rank = TOUR.get(tour.index);
			prepare(player, rank, BreakthroughQuality.HEAVENLY, false);
			Msg.title(player, rank.displayName().withColor(rank.realm().color()).withStyle(ChatFormatting.BOLD),
					Component.translatable("kultivator.test.tour.step", tour.index + 1, TOUR.size()),
					5, Math.max(20, tour.ticksPerStep - 15), 10);
		}
	}

	/** Gives the player back the cultivation they had before the tour. */
	private static void restore(ServerPlayer player, Tour tour) {
		player.setAttached(ModAttachments.CULTIVATION, tour.original.copy());
		Cultivators.get(player).resetSession();
		Stats.apply(player);
		Cultivators.syncNow(player);
		Msg.chat(player, Component.translatable("kultivator.test.tour.end").withStyle(ChatFormatting.AQUA));
	}

	// ================================================================ disciples

	/** The disciple's name as one command word (spaces become underscores). */
	private static String token(DiscipleEntity disciple) {
		return disciple.getName().getString().trim().replace(' ', '_');
	}

	/** The player's loaded disciples named {@code name} ("semua" = all of them); tells the player when none match. */
	private static List<DiscipleEntity> targets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		String wanted = name.toLowerCase(Locale.ROOT).replace('_', ' ');
		List<DiscipleEntity> found = new ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (name.equalsIgnoreCase("semua") || disciple.getName().getString().trim().toLowerCase(Locale.ROOT).equals(wanted)) {
				found.add(disciple);
			}
		}
		if (found.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.test.disciple.none", name));
		}
		return found;
	}

	/** Puts a disciple at {@code rank} ready to try it: root, qualities, Dao from Golden Core, techniques, full qi and health. */
	private static void prepareDisciple(DiscipleEntity disciple, Rank rank, BreakthroughQuality quality, boolean everything) {
		DiscipleData data = disciple.data();
		if (everything || data.rootElements() == 0) {
			int elements = data.rootElements() != 0 ? data.rootElements() : Element.METAL.bit() | Element.FIRE.bit() | Element.LIGHTNING.bit();
			data.setRoot(elements, everything ? RootGrade.HEAVENLY : data.rootGrade());
		}
		for (Realm each : Realm.values()) {
			if (!each.isMortal()) {
				data.setQuality(each, each.ordinal() <= rank.realm().ordinal() ? quality : null);
			}
		}
		if (rank.realm().atLeast(Realm.GOLDEN_CORE) && data.daoIndex() < 0) {
			data.setDao(Dao.SWORD);
		}
		if (everything) {
			data.setComprehension(100.0D);
			for (Technique technique : Technique.values()) {
				if (DiscipleTechniques.teachable(technique)) {
					data.learn(technique);
					data.setCooldown(technique, 0);
				}
			}
		}
		data.setDeviationTicks(0);
		DiscipleCultivation.setRank(disciple, rank);
		data.setXp(0.0D);
		data.setQi(DiscipleCultivation.maxQi(data));
		disciple.refreshSynced();
		disciple.setHealth(disciple.getMaxHealth());
	}

	private static int discipleMax(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		List<DiscipleEntity> found = targets(ctx);
		for (DiscipleEntity disciple : found) {
			prepareDisciple(disciple, Rank.ofStep(Rank.MAX_STEP), BreakthroughQuality.HEAVENLY, true);
			Msg.chat(player, Component.translatable("kultivator.test.disciple.max", disciple.getName()).withStyle(ChatFormatting.GOLD));
		}
		return found.size();
	}

	private static int discipleTechniques(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		List<DiscipleEntity> found = targets(ctx);
		for (DiscipleEntity disciple : found) {
			DiscipleData data = disciple.data();
			for (Technique technique : Technique.values()) {
				if (DiscipleTechniques.teachable(technique)) {
					data.learn(technique);
					data.setCooldown(technique, 0);
				}
			}
			data.setQi(DiscipleCultivation.maxQi(data));
			disciple.refreshSynced();
			Msg.chat(player, Component.translatable("kultivator.test.disciple.techniques", disciple.getName()));
		}
		return found.size();
	}

	private static int discipleRealm(CommandContext<CommandSourceStack> ctx, int stage, int layer) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		String id = StringArgumentType.getString(ctx, "realm");
		Realm realm = Realm.byId(id);
		if (realm == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.core.debug.unknown", id));
			return 0;
		}
		Rank rank = new Rank(realm, layer - 1, stage - 1);
		List<DiscipleEntity> found = targets(ctx);
		for (DiscipleEntity disciple : found) {
			prepareDisciple(disciple, rank, BreakthroughQuality.PERFECT, false);
			Msg.chat(player, Component.translatable("kultivator.test.disciple.realm", disciple.getName(),
					rank.displayName().withColor(realm.color())));
		}
		return found.size();
	}

	private static int discipleTour(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		List<DiscipleEntity> found = targets(ctx);
		for (DiscipleEntity disciple : found) {
			DiscipleTour running = DISCIPLE_TOURS.remove(disciple.getUUID());
			CompoundTag original = running != null ? running.original : disciple.data().save().copy();
			DISCIPLE_TOURS.put(disciple.getUUID(), new DiscipleTour(original, player.getUUID(), seconds * 20));
			Msg.chat(player, Component.translatable("kultivator.test.disciple.tour", disciple.getName(), TOUR.size(), seconds)
					.withStyle(ChatFormatting.AQUA));
		}
		return found.size();
	}

	private static void tickDiscipleTours(MinecraftServer server) {
		if (DISCIPLE_TOURS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, DiscipleTour> entry : Map.copyOf(DISCIPLE_TOURS).entrySet()) {
			DiscipleTour tour = entry.getValue();
			DiscipleEntity disciple = findDisciple(tour.master, entry.getKey());
			if (disciple == null || !disciple.isAlive()) {
				continue;
			}
			if (tour.index >= 0 && ++tour.ticks < tour.ticksPerStep) {
				continue;
			}
			tour.ticks = 0;
			tour.index++;
			if (tour.index >= TOUR.size()) {
				DISCIPLE_TOURS.remove(entry.getKey());
				restoreDisciple(disciple, tour);
				continue;
			}
			Rank rank = TOUR.get(tour.index);
			prepareDisciple(disciple, rank, BreakthroughQuality.HEAVENLY, false);
			ServerPlayer master = server.getPlayerList().getPlayer(tour.master);
			if (master != null) {
				Msg.actionBar(master, Component.translatable("kultivator.test.disciple.tour_step", disciple.getName(),
						rank.displayName().withColor(rank.realm().color()), tour.index + 1, TOUR.size()));
			}
		}
	}

	private static DiscipleEntity findDisciple(UUID master, UUID id) {
		for (DiscipleEntity disciple : Disciples.loaded(master)) {
			if (disciple.getUUID().equals(id)) {
				return disciple;
			}
		}
		return null;
	}

	private static void restoreDisciple(DiscipleEntity disciple, DiscipleTour tour) {
		disciple.setData(DiscipleData.load(tour.original.copy()));
		disciple.refreshSynced();
		disciple.setHealth(Math.min(disciple.getHealth(), disciple.getMaxHealth()));
		ServerPlayer master = disciple.owner();
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.test.disciple.tour_end", disciple.getName()).withStyle(ChatFormatting.AQUA));
		}
	}

	// ================================================================ kits

	private static int kit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);

		List<ItemStack> mod = new ArrayList<>();
		for (Item item : ModItems.all()) {
			ItemStack stack = new ItemStack(item, Math.min(16, new ItemStack(item).getMaxStackSize()));
			mod.add(stack);
		}
		List<ItemStack> pills = new ArrayList<>();
		for (Item pill : List.of(AlchemyItems.QI_GATHERING_PILL, AlchemyItems.QI_RECOVERY_PILL, AlchemyItems.FOUNDATION_PILL,
				AlchemyItems.GOLDEN_CORE_PILL, AlchemyItems.HEART_CALMING_PILL, AlchemyItems.HEALING_PILL,
				AlchemyItems.TRIBULATION_WARDING_PILL, AlchemyItems.SOUL_NURTURING_PILL, AlchemyItems.IMMORTAL_PILL)) {
			for (int quality = 0; quality <= 3; quality += 3) {
				ItemStack stack = new ItemStack(pill, 4);
				stack.set(AlchemyComponents.PILL_QUALITY, quality);
				pills.add(stack);
			}
		}
		List<ItemStack> books = new ArrayList<>();
		var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (ResourceKey<Enchantment> key : BOOKS) {
			enchantments.get(key).ifPresent(holder -> books.add(EnchantmentHelper.createBook(new EnchantmentInstance(holder, 1))));
		}
		books.add(new ItemStack(Items.DIAMOND_SWORD));
		books.add(new ItemStack(Items.NETHERITE_SWORD));
		List<ItemStack> rare = List.of(
				new ItemStack(Items.GOLDEN_CARROT, 16), new ItemStack(Items.GLOW_BERRIES, 16), new ItemStack(Items.HONEY_BOTTLE, 4),
				new ItemStack(Items.GOLDEN_APPLE, 8), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 4),
				new ItemStack(Items.GLISTERING_MELON_SLICE, 16), new ItemStack(Items.AMETHYST_SHARD, 64), new ItemStack(Items.ECHO_SHARD, 8),
				new ItemStack(Items.DRAGON_BREATH, 4), new ItemStack(Items.NETHER_STAR, 4), new ItemStack(Items.EXPERIENCE_BOTTLE, 16),
				new ItemStack(Items.TOTEM_OF_UNDYING, 2), new ItemStack(Items.HEART_OF_THE_SEA, 2), new ItemStack(Items.GHAST_TEAR, 8),
				new ItemStack(Items.PHANTOM_MEMBRANE, 8), new ItemStack(Items.BLAZE_POWDER, 16), new ItemStack(Items.CHORUS_FRUIT, 16),
				new ItemStack(Items.NETHER_WART, 16), new ItemStack(Items.WOLF_SPAWN_EGG, 2), new ItemStack(Items.FOX_SPAWN_EGG, 2),
				new ItemStack(Items.BONE, 16));
		List<ItemStack> materials = List.of(
				new ItemStack(Items.AMETHYST_BLOCK, 8), new ItemStack(Items.GOLD_BLOCK, 1), new ItemStack(Items.END_ROD, 4),
				new ItemStack(Items.BEACON, 1), new ItemStack(Items.LODESTONE, 1), new ItemStack(Items.CRYING_OBSIDIAN, 4),
				new ItemStack(Items.CAULDRON, 1), new ItemStack(Items.CAMPFIRE, 1), new ItemStack(Items.SOUL_CAMPFIRE, 1),
				new ItemStack(Items.GLOWSTONE_DUST, 16), new ItemStack(Items.SUGAR, 16), new ItemStack(Items.HONEYCOMB, 16),
				new ItemStack(Items.SWEET_BERRIES, 16), new ItemStack(Items.BONE_MEAL, 16), new ItemStack(Items.GOLD_INGOT, 16),
				new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.COPPER_INGOT, 16), new ItemStack(Items.COCOA_BEANS, 16),
				new ItemStack(Items.LILY_OF_THE_VALLEY, 8), new ItemStack(Items.KELP, 16), new ItemStack(Items.INK_SAC, 16),
				new ItemStack(Items.PAPER, 16));

		give(player, box("kultivator.test.kit.mod", mod));
		give(player, box("kultivator.test.kit.pills", pills));
		give(player, box("kultivator.test.kit.books", books));
		give(player, box("kultivator.test.kit.rare", rare));
		give(player, box("kultivator.test.kit.materials", materials));
		Msg.chat(player, Component.translatable("kultivator.test.kit").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** A named shulker box item holding up to 27 stacks (more go into further boxes by the caller's split). */
	private static List<ItemStack> box(String nameKey, List<ItemStack> contents) {
		List<ItemStack> boxes = new ArrayList<>();
		for (int start = 0; start < contents.size(); start += 27) {
			List<ItemStack> slice = new ArrayList<>(contents.subList(start, Math.min(contents.size(), start + 27)));
			ItemStack box = new ItemStack(Items.SHULKER_BOX);
			box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slice));
			box.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey).withStyle(ChatFormatting.GOLD));
			boxes.add(box);
		}
		return boxes;
	}

	private static void give(ServerPlayer player, List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				// Block-safe like every item the mod drops: no portal travel, no plates.
				net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(),
						player.getY() + 0.5D, player.getZ(), stack);
				item.setDefaultPickUpDelay();
				item.addTag(dev.kultivator.util.NoGrief.TAG);
				item.setPortalCooldown();
				player.level().addFreshEntity(item);
			}
		}
	}

	private TestCommands() {
	}
}
