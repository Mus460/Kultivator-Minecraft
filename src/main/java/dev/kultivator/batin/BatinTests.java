package dev.kultivator.batin;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The phase-9 inner-world and created-art test commands (DESIGN 26.11), op only under {@code /kultivasi uji}: {@code batin
 * lahir|hari|bencana|herba|rubah|batu|mutiara} and {@code ciptaan <slot> <daoA> <daoB> <bentuk> [kesempurnaan]}.
 */
final class BatinTests {

	private static final SuggestionProvider<CommandSourceStack> DAOS = (ctx, builder) -> {
		for (Dao dao : Dao.values()) {
			builder.suggest(Creation.alias(dao));
		}
		return builder.buildFuture();
	};
	private static final SuggestionProvider<CommandSourceStack> FORMS = (ctx, builder) -> {
		for (Creation.Form form : Creation.Form.values()) {
			builder.suggest(form.id());
		}
		return builder.buildFuture();
	};

	static void register(LiteralArgumentBuilder<CommandSourceStack> uji) {
		LiteralArgumentBuilder<CommandSourceStack> born = Commands.literal("lahir").executes(ctx -> birth(ctx, -1));
		for (int grade = 0; grade < InnerWorld.GRADES.length; grade++) {
			int chosen = grade;
			born.then(Commands.literal(InnerWorld.GRADES[grade]).executes(ctx -> birth(ctx, chosen)));
		}
		LiteralArgumentBuilder<CommandSourceStack> calamity = Commands.literal("bencana")
				.then(Commands.literal("data").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					if (!needBorn(ctx, player)) {
						return 0;
					}
					InnerCalamity.resolveData(player, false);
					return ok(ctx, Component.translatable("kultivator.batin.test.data"));
				}));
		for (int type = 0; type < InnerCalamity.TYPE_IDS.length; type++) {
			int chosen = type;
			calamity.then(Commands.literal(InnerCalamity.TYPE_IDS[type])
					.executes(ctx -> live(ctx, chosen, -1))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 30))
							.executes(ctx -> live(ctx, chosen, IntegerArgumentType.getInteger(ctx, "n")))));
		}
		uji.then(Commands.literal("batin")
				.then(born)
				.then(Commands.literal("hari")
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 30))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									if (!needBorn(ctx, player)) {
										return 0;
									}
									int n = IntegerArgumentType.getInteger(ctx, "n");
									InnerWorld.advanceTest(player, n);
									Cultivators.dirty(player);
									Batin.dirty(player);
									return ok(ctx, Component.translatable("kultivator.batin.test.days", n));
								})))
				.then(calamity)
				.then(Commands.literal("herba")
						.then(Commands.argument("slot", IntegerArgumentType.integer(1, InnerWorld.MAX_PLOTS))
								.then(Commands.argument("umur", IntegerArgumentType.integer(1, InnerWorld.HERB_CAP))
										.executes(ctx -> herbAge(ctx, IntegerArgumentType.getInteger(ctx, "slot") - 1, IntegerArgumentType.getInteger(ctx, "umur"))))))
				.then(Commands.literal("rubah")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 4000))
								.executes(ctx -> setInt(ctx, "foxes", IntegerArgumentType.getInteger(ctx, "n"), "kultivator.batin.test.foxes"))))
				.then(Commands.literal("mutiara")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, InnerWorld.PEARL_CAP))
								.executes(ctx -> setInt(ctx, "pearls", IntegerArgumentType.getInteger(ctx, "n"), "kultivator.batin.test.pearls"))))
				.then(Commands.literal("batu").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					if (!needBorn(ctx, player)) {
						return 0;
					}
					CompoundTag rock = InnerWorld.section(InnerWorld.root(Cultivators.get(player)), "rock");
					boolean was = rock.getBooleanOr("awake", false);
					rock.putBoolean("awake", true);
					rock.putInt("wake", InnerWorld.ROCK_WAKE);
					if (!was) {
						rock.putInt("days", 0);
					}
					rock.putInt("i", Math.max(rock.getIntOr("i", 0), 64));
					rock.putInt("g", Math.max(rock.getIntOr("g", 0), 16));
					rock.putInt("d", Math.max(rock.getIntOr("d", 0), 2));
					dev.kultivator.util.Advancements.grant(player, "rockmen");
					Cultivators.dirty(player);
					Batin.dirty(player);
					return ok(ctx, Component.translatable("kultivator.batin.test.rock"));
				})));
		uji.then(Commands.literal("ciptaan")
				.then(Commands.argument("slot", IntegerArgumentType.integer(1, CiptaanArts.SLOTS))
						.then(Commands.argument("daoA", StringArgumentType.word()).suggests(DAOS)
								.then(Commands.argument("daoB", StringArgumentType.word()).suggests(DAOS)
										.then(Commands.argument("bentuk", StringArgumentType.word()).suggests(FORMS)
												.executes(ctx -> create(ctx, -1.0D))
												.then(Commands.argument("kesempurnaan", DoubleArgumentType.doubleArg(Creation.Q_MIN, Creation.Q_MAX))
														.executes(ctx -> create(ctx, DoubleArgumentType.getDouble(ctx, "kesempurnaan")))))))));
	}

	private static int birth(CommandContext<CommandSourceStack> ctx, int grade) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		InnerWorld.birth(player, grade, true);
		return ok(ctx, Component.translatable("kultivator.batin.test.born"));
	}

	private static int live(CommandContext<CommandSourceStack> ctx, int type, int n) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!needBorn(ctx, player)) {
			return 0;
		}
		int strength = n > 0 ? n : Math.max(1, InnerWorld.view(Cultivators.get(player)).getCompoundOrEmpty("cal").getIntOr("n", 1));
		if (!InnerCalamity.forceLive(player, type, strength)) {
			ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.batin.test.live_failed")));
			return 0;
		}
		return ok(ctx, Component.translatable("kultivator.batin.test.live", Component.translatable("kultivator.batin.cal.type." + InnerCalamity.TYPE_IDS[type]),
				strength));
	}

	private static int herbAge(CommandContext<CommandSourceStack> ctx, int slot, int age) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!needBorn(ctx, player)) {
			return 0;
		}
		ListTag plots = InnerWorld.list(InnerWorld.root(Cultivators.get(player)), "plots");
		if (slot >= plots.size()) {
			ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.batin.test.no_plot", plots.size())));
			return 0;
		}
		CompoundTag plot = plots.getCompoundOrEmpty(slot);
		if (plot.getStringOr("k", "").isEmpty()) {
			plot.putString("k", "spirit_grass");
			plot.putInt("p", 1);
		}
		plot.putInt("a", age);
		plot.putInt("p", Math.min(plot.getIntOr("p", 1), 1));
		plot.putInt("s", InnerWorld.STATE_OK);
		Cultivators.dirty(player);
		Batin.dirty(player);
		return ok(ctx, Component.translatable("kultivator.batin.test.herb", slot + 1, age));
	}

	private static int setInt(CommandContext<CommandSourceStack> ctx, String key, int value, String message) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!needBorn(ctx, player)) {
			return 0;
		}
		InnerWorld.root(Cultivators.get(player)).putInt(key, value);
		Cultivators.dirty(player);
		Batin.dirty(player);
		return ok(ctx, Component.translatable(message, value));
	}

	private static int create(CommandContext<CommandSourceStack> ctx, double quality) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Dao a = Creation.daoOf(StringArgumentType.getString(ctx, "daoA"));
		Dao b = Creation.daoOf(StringArgumentType.getString(ctx, "daoB"));
		Creation.Form form = Creation.Form.byId(StringArgumentType.getString(ctx, "bentuk"));
		if (a == null || b == null || form == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.batin.test.bad_art")));
			return 0;
		}
		int slot = IntegerArgumentType.getInteger(ctx, "slot") - 1;
		double q = quality > 0.0D ? quality : Creation.roll(player, a, b);
		Creation.createNow(player, slot, a, b, form, q);
		return ok(ctx, Component.translatable("kultivator.batin.test.art", slot + 1, Math.round(q * 100.0D)));
	}

	private static boolean needBorn(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (Batin.has(player)) {
			return true;
		}
		ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.batin.test.not_born")));
		return false;
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendSuccess(() -> Msg.prefixed(message), false);
		return 1;
	}

	private BatinTests() {
	}
}
