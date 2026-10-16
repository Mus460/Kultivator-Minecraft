package dev.kultivator.dao;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Stats;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.Techniques;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Choosing a Dao (DESIGN 7): once, at Golden Core or higher, through the info screen, the prompt after reaching Golden
 * Core or {@code /cultivate dao choose <dao>}. Choosing grants the Dao's techniques, the Body Dao armour and a first
 * insight. Only an admin can undo it ({@code /cultivate debug dao reset}).
 */
public final class DaoPaths {

	/** Comprehension of the first insight when the Dao is chosen. */
	public static final double FIRST_INSIGHT = 5.0D;
	/** How often players are checked for the choice prompt. */
	private static final int PROMPT_INTERVAL = 40;
	/** No prompt within this many ticks after taking damage. */
	private static final int COMBAT_TICKS = 200;
	/** No prompt right after joining or respawning. */
	private static final int JOIN_GRACE_TICKS = 100;
	private static final String CHOOSE_COMMAND = "/cultivate dao choose ";

	private static final SuggestionProvider<CommandSourceStack> DAOS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Dao.values()).map(Dao::id), builder);

	/** Players prompted to choose in this session. */
	private static final Set<UUID> PROMPTED = new HashSet<>();
	/** Player -> server tick of the last damage taken. */
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DaoPaths::tickPrompts);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				LAST_HURT.put(player.getUUID(), (long) player.level().getServer().getTickCount());
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			PROMPTED.remove(player.getUUID());
			LAST_HURT.remove(player.getUUID());
		});
	}

	// ================================================================== choosing

	/** Chooses the player's Dao if they may (Golden Core+, none chosen yet). True on success. */
	public static boolean choose(ServerPlayer player, Dao dao) {
		CultivationData data = Cultivators.get(player);
		Dao current = data.dao();
		if (current != null) {
			String key = current == dao ? "kultivator.dao.choose.same" : "kultivator.dao.choose.already";
			Msg.chat(player, Component.translatable(key, colored(current)).withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (!data.realm().atLeast(Realm.GOLDEN_CORE)) {
			Msg.chat(player, Component.translatable("kultivator.dao.choose.too_early", Realm.GOLDEN_CORE.displayName()
					.copy().withColor(Realm.GOLDEN_CORE.color()), data.rank().displayName()).withStyle(ChatFormatting.GRAY));
			return false;
		}
		PROMPTED.add(player.getUUID());
		data.setDao(dao);
		Stats.apply(player);
		Comprehension.add(player, dao, FIRST_INSIGHT);

		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.DAO_CHOSEN, dao.ordinal(), 0, 0.0F);
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, x, y, z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);

		Msg.title(player, Component.translatable("kultivator.dao.choose.title", dao.displayName()).withColor(dao.color())
						.withStyle(ChatFormatting.BOLD),
				Component.translatable("kultivator.dao.choose.subtitle").withStyle(ChatFormatting.GRAY), 15, 80, 30);
		Msg.chat(player, Component.translatable("kultivator.dao.choose.chat", colored(dao)).withStyle(ChatFormatting.YELLOW));
		// Grants (and announces) the arts this Dao opens right away; the deeper ones wait in books.
		Techniques.refreshUnlocks(player);
		List<Component> books = new ArrayList<>();
		for (Technique technique : Technique.values()) {
			if (technique.dao() == dao && technique.unlock() == Technique.Unlock.BOOK) {
				books.add(technique.displayName().copy().withColor(dao.color()));
			}
		}
		if (!books.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.dao.choose.books", join(books), Realm.NASCENT_SOUL.displayName())
					.withStyle(ChatFormatting.GRAY));
		}
		if (Comprehension.hasAffinity(data, dao)) {
			Msg.chat(player, Component.translatable("kultivator.dao.choose.affinity").withColor(dao.color()));
		}
		Advancements.grant(player, "dao_chosen");
		Cultivators.syncNow(player);
		return true;
	}

	// ================================================================== prompt

	/** Every 2 s: players at Golden Core+ without a Dao get the choice screen once per session, when calm. */
	private static void tickPrompts(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % PROMPT_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player instanceof FakePlayer || player.isSpectator() || !player.isAlive() || player.tickCount < JOIN_GRACE_TICKS
					|| PROMPTED.contains(player.getUUID())) {
				continue;
			}
			CultivationData data = Cultivators.get(player);
			if (data.dao() != null || !data.realm().atLeast(Realm.GOLDEN_CORE) || data.breakingThrough() || data.meditating()
					|| Breakthrough.isBusy(player)) {
				continue;
			}
			Long hurt = LAST_HURT.get(player.getUUID());
			if (hurt != null && now - hurt < COMBAT_TICKS) {
				continue;
			}
			PROMPTED.add(player.getUUID());
			Net.send(player, new Payloads.OpenScreen(Payloads.OpenScreen.CHOOSE_DAO, 0));
			Msg.chat(player, Component.translatable("kultivator.dao.prompt.chat", Realm.GOLDEN_CORE.displayName()
					.copy().withColor(Realm.GOLDEN_CORE.color())).withStyle(ChatFormatting.GOLD));
			sendChoiceHint(player);
			player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
					SoundSource.PLAYERS, 0.9F, 0.7F);
		}
	}

	/** One clickable line per Dao (suggests the command, never runs it) plus the root affinities. */
	public static void sendChoiceHint(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		MutableComponent line = Component.translatable("kultivator.dao.prompt.hint").withStyle(ChatFormatting.GRAY)
				.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(CHOOSE_COMMAND))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(CHOOSE_COMMAND + "<dao>"))));
		for (Dao dao : Dao.values()) {
			MutableComponent hover = Component.translatable("kultivator.dao.prompt.hover", dao.displayName(), CHOOSE_COMMAND + dao.id());
			if (Comprehension.hasAffinity(data, dao)) {
				hover.append(Component.literal("\n")).append(Component.translatable("kultivator.dao.info.affinity_yes").withColor(dao.color()));
			}
			MutableComponent entry = Component.literal("[").append(dao.displayName()).append(Component.literal(
							Comprehension.hasAffinity(data, dao) ? " ✦]" : "]"))
					.withColor(dao.color())
					.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(CHOOSE_COMMAND + dao.id()))
							.withHoverEvent(new HoverEvent.ShowText(hover)));
			line.append(Component.literal(" ")).append(entry);
		}
		Msg.chat(player, line);
		Msg.chat(player, Component.translatable("kultivator.dao.prompt.forever").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	// ================================================================== commands

	/** Adds {@code dao choose <dao>} to /cultivate. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("dao")
				.executes(ctx -> info(ctx, self(ctx)))
				.then(Commands.literal("info").executes(ctx -> info(ctx, self(ctx))))
				.then(Commands.literal("choose")
						.executes(ctx -> {
							ServerPlayer player = self(ctx);
							Dao current = Cultivators.get(player).dao();
							if (current != null) {
								Msg.chat(player, Component.translatable("kultivator.dao.choose.already", colored(current))
										.withStyle(ChatFormatting.GRAY));
								return 0;
							}
							sendChoiceHint(player);
							return 1;
						})
						.then(Commands.argument("dao", StringArgumentType.word()).suggests(DAOS)
								.executes(ctx -> {
									ServerPlayer player = self(ctx);
									String id = StringArgumentType.getString(ctx, "dao");
									Dao dao = Dao.byId(id);
									if (dao == null) {
										ctx.getSource().sendFailure(Component.translatable("kultivator.dao.command.unknown", id));
										return 0;
									}
									return choose(player, dao) ? 1 : 0;
								}))));

		debug.then(Commands.literal("dao")
				.then(Commands.literal("reset")
						.executes(ctx -> reset(ctx, self(ctx)))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("comprehension")
						.then(Commands.argument("dao", StringArgumentType.word()).suggests(DAOS)
								.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D))
										.executes(ctx -> setComprehension(ctx, self(ctx)))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> setComprehension(ctx, EntityArgument.getPlayer(ctx, "player"))))))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	/** The chosen Dao, its comprehension, level, technique power, root affinity and other insights. */
	private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		CommandSourceStack source = ctx.getSource();
		CultivationData data = Cultivators.get(player);
		Dao dao = data.dao();
		source.sendSuccess(() -> Component.translatable("kultivator.dao.info.header", player.getDisplayName())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		if (dao == null) {
			source.sendSuccess(() -> Component.translatable("kultivator.dao.info.none").withStyle(ChatFormatting.GRAY), false);
			if (data.realm().atLeast(Realm.GOLDEN_CORE)) {
				source.sendSuccess(() -> Component.translatable("kultivator.dao.info.can_choose").withStyle(ChatFormatting.YELLOW), false);
				if (source.getPlayer() == player) {
					sendChoiceHint(player);
				}
			} else {
				source.sendSuccess(() -> Component.translatable("kultivator.dao.info.locked", Realm.GOLDEN_CORE.displayName()
						.copy().withColor(Realm.GOLDEN_CORE.color()), data.rank().displayName()).withStyle(ChatFormatting.GRAY), false);
			}
			List<Component> resonant = new ArrayList<>();
			for (Dao each : Dao.values()) {
				if (Comprehension.hasAffinity(data, each)) {
					resonant.add(colored(each));
				}
			}
			if (!data.rootTested()) {
				source.sendSuccess(() -> Component.translatable("kultivator.dao.info.root_untested").withStyle(ChatFormatting.DARK_GRAY), false);
			} else if (!resonant.isEmpty()) {
				source.sendSuccess(() -> Component.translatable("kultivator.dao.info.resonates", join(resonant)).withStyle(ChatFormatting.GRAY), false);
			}
		} else {
			double value = data.comprehension(dao);
			int level = Dao.comprehensionLevel(value);
			source.sendSuccess(() -> label("kultivator.dao.info.dao", colored(dao).copy().withStyle(ChatFormatting.BOLD)), false);
			source.sendSuccess(() -> label("kultivator.dao.info.comprehension", Component.translatable("kultivator.dao.info.level",
					Comprehension.format(value), Dao.comprehensionName(value)).withColor(dao.color())), false);
			double next = Comprehension.nextThreshold(level);
			if (next > 0.0D) {
				source.sendSuccess(() -> label("kultivator.dao.info.next", Component.translatable("kultivator.dao.info.next_value",
						Component.translatable("kultivator.comprehension." + (level + 1)), Comprehension.format(next))
						.withStyle(ChatFormatting.WHITE)), false);
			}
			source.sendSuccess(() -> label("kultivator.dao.info.power", Component.literal("×" + Comprehension.format(1.0D + value / 100.0D))
					.withStyle(ChatFormatting.WHITE)), false);
			boolean affinity = Comprehension.hasAffinity(data, dao);
			source.sendSuccess(() -> label("kultivator.dao.info.affinity", affinity
					? Component.translatable("kultivator.dao.info.affinity_yes").withColor(dao.color())
					: Component.translatable("kultivator.dao.info.affinity_no").withStyle(ChatFormatting.DARK_GRAY)), false);
		}
		List<Component> others = new ArrayList<>();
		for (Dao each : Dao.values()) {
			if (each != dao && data.comprehension(each) >= 0.05D) {
				others.add(Comprehension.describe(data, each));
			}
		}
		if (!others.isEmpty()) {
			source.sendSuccess(() -> label("kultivator.dao.info.others", join(others)), false);
		}
		return 1;
	}

	/** Debug: forgets the Dao and every technique of any Dao, so the player can choose again. Comprehension is kept. */
	private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		data.setDao(null);
		for (Technique technique : Technique.values()) {
			if (technique.dao() != null && (technique.unlock() == Technique.Unlock.DAO || technique.unlock() == Technique.Unlock.BOOK)) {
				data.forget(technique);
			}
		}
		Techniques.refreshUnlocks(player);
		Stats.apply(player);
		PROMPTED.remove(player.getUUID());
		Cultivators.syncNow(player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.dao.debug.reset", player.getDisplayName())), true);
		return 1;
	}

	/** Debug: sets one Dao's comprehension; levels crossed upwards are announced like real gains. */
	private static int setComprehension(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		String id = StringArgumentType.getString(ctx, "dao");
		Dao dao = Dao.byId(id);
		if (dao == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.dao.command.unknown", id));
			return 0;
		}
		double value = DoubleArgumentType.getDouble(ctx, "value");
		Comprehension.set(player, dao, value);
		Cultivators.syncNow(player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.dao.debug.comprehension", player.getDisplayName(),
				colored(dao), Comprehension.format(Cultivators.get(player).comprehension(dao)))), true);
		return 1;
	}

	// ================================================================== text helpers

	/** The Dao's name in its colour. */
	public static Component colored(Dao dao) {
		return dao.displayName().copy().withColor(dao.color());
	}

	/** "a, b, c" with the translated separator. */
	public static MutableComponent join(List<Component> parts) {
		MutableComponent line = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				line.append(Component.translatable("kultivator.dao.separator"));
			}
			line.append(parts.get(i));
		}
		return line;
	}

	private static MutableComponent label(String key, Component value) {
		return Component.translatable(key).withStyle(ChatFormatting.GRAY).append(Component.literal(" ")).append(value);
	}

	private DaoPaths() {
	}
}
