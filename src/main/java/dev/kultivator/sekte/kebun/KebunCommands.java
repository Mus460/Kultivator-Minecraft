package dev.kultivator.sekte.kebun;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.kebun.Kebun.Aula;
import dev.kultivator.sekte.kebun.TabelHasil.Benih;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * The commands of module W (DESIGN 36.2.10, 36.3.4):
 * <ul>
 * <li>{@code /kultivasi perguruan kebun [tambang]} (everyone): opens the Kebun &amp; Tambang board of one's own sect.</li>
 * <li>{@code /kultivasi uji perguruan kebun} (op): {@code info}; {@code tanam <petak> <herba>}; {@code tumbuh [hari]}
 * (beds grow and the mine works that many days at once); {@code panen} (every ripe bed); {@code tambang tingkat <1-9>};
 * {@code tambang bangun} (a test mine without the building); {@code giliran} (a shift ends now: streaks, report,
 * cave-in roll); {@code runtuh} (a cave-in now); {@code petak <0-12>} (test beds without the building, 0 = the
 * building's); {@code kerja <hari>} (sets every worker's streak, to see forced work); {@code inti} (a Mountain Spirit
 * Core in the hand); {@code sinkron} (the world picture now).</li>
 * </ul>
 * Every test works on the executing player's own hall.
 */
final class KebunCommands {

	private static final SuggestionProvider<CommandSourceStack> HERBS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Benih.all()).map(Benih::id).toList(), builder);

	@FunctionalInterface
	private interface HallCommand {
		int run(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
	}

	static void init() {
		SekteCommands.test(SekteIds.W, KebunCommands::testTree);
		SekteCommands.player(SekteIds.W, KebunCommands::playerTree);
		SekteCommands.chips("perguruan kebun info", "perguruan kebun tanam <petak> <herba>", "perguruan kebun tumbuh [hari]",
				"perguruan kebun panen", "perguruan kebun tambang tingkat <1-9>", "perguruan kebun tambang bangun", "perguruan kebun giliran",
				"perguruan kebun runtuh", "perguruan kebun petak <0-12>", "perguruan kebun kerja <hari>", "perguruan kebun inti",
				"perguruan kebun sinkron");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> playerTree() {
		return Commands.literal(SekteIds.W)
				.executes(ctx -> open(ctx.getSource().getPlayerOrException(), Kebun.PAGE_GARDEN))
				.then(Commands.literal("tambang").executes(ctx -> open(ctx.getSource().getPlayerOrException(), Kebun.PAGE_MINE)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> testTree() {
		return Commands.literal(SekteIds.W)
				.executes(ctx -> hall(ctx, KebunCommands::info))
				.then(Commands.literal("info").executes(ctx -> hall(ctx, KebunCommands::info)))
				.then(Commands.literal("tanam").then(Commands.argument("petak", IntegerArgumentType.integer(1, 12))
						.then(Commands.argument("herba", StringArgumentType.word()).suggests(HERBS)
								.executes(ctx -> hall(ctx, KebunCommands::plant)))))
				.then(Commands.literal("tumbuh").executes(ctx -> hall(ctx, (p, a, c) -> grow(p, a, 1)))
						.then(Commands.argument("hari", IntegerArgumentType.integer(1, 30))
								.executes(ctx -> hall(ctx, (p, a, c) -> grow(p, a, IntegerArgumentType.getInteger(c, "hari"))))))
				.then(Commands.literal("panen").executes(ctx -> hall(ctx, KebunCommands::harvestAll)))
				.then(Commands.literal("tambang")
						.then(Commands.literal("tingkat").then(Commands.argument("tingkat", IntegerArgumentType.integer(1, TambangRoh.MAX_LEVEL))
								.executes(ctx -> hall(ctx, KebunCommands::setLevel))))
						.then(Commands.literal("bangun").executes(ctx -> hall(ctx, KebunCommands::testMine))))
				.then(Commands.literal("giliran").executes(ctx -> hall(ctx, KebunCommands::shift)))
				.then(Commands.literal("runtuh").executes(ctx -> hall(ctx, (p, a, c) -> GiliranTambang.caveIn(a) ? 1 : 0)))
				.then(Commands.literal("petak").then(Commands.argument("n", IntegerArgumentType.integer(0, 12))
						.executes(ctx -> hall(ctx, KebunCommands::testBeds))))
				.then(Commands.literal("kerja").then(Commands.argument("hari", IntegerArgumentType.integer(0, 60))
						.executes(ctx -> hall(ctx, KebunCommands::streaks))))
				.then(Commands.literal("inti").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ItemStack core = new ItemStack(KebunItems.INTI_ROH_GUNUNG);
					if (!player.getInventory().add(core) && !core.isEmpty()) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.kebun.cmd.full"));
						return 0;
					}
					return 1;
				}))
				.then(Commands.literal("sinkron").executes(ctx -> {
					KebunPayloads.syncWorld(ctx.getSource().getPlayerOrException().getUUID());
					return 1;
				}));
	}

	private static int open(ServerPlayer player, int page) {
		Kebun.compute(player.getUUID());
		KebunPayloads.open(player, page);
		return 1;
	}

	private static int hall(CommandContext<CommandSourceStack> ctx, HallCommand command) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Kebun.compute(player.getUUID());
		Aula aula = Kebun.aula(player.getUUID());
		if (aula == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.kebun.no_hall"));
			return 0;
		}
		int result = command.run(player, aula, ctx);
		Halls.setDirty();
		KebunPayloads.syncWorld(player.getUUID());
		return result;
	}

	private static int info(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) {
		int plots = Kebun.plotCount(aula.owner());
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.cmd.info", plots,
				Kebun.tenderSlots(plots), aula.sec().getCompoundOrEmpty("rawat").size(), TambangRoh.level(aula.owner()),
				TambangRoh.miners(aula.sec()).size(), TabelHasil.slots(TambangRoh.level(aula.owner()))).withStyle(ChatFormatting.GOLD), false);
		for (int i = 0; i < plots; i++) {
			PetakHerba bed = PetakHerba.of(aula.sec(), i);
			Benih herb = bed.herb();
			int n = i + 1;
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.cmd.bed", n,
					herb == null ? Component.translatable("kultivator.sekte.kebun.ui.bare") : herb.label(),
					bed.stage(), Math.round(bed.progress() * 100.0D), bed.age()).withStyle(ChatFormatting.GRAY), false);
		}
		CompoundTag stats = Pertumbuhan.stats(aula.sec());
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.cmd.stats", stats.getIntOr("panen", 0),
				stats.getIntOr("herba", 0), stats.getIntOr("gali", 0), stats.getIntOr("inti", 0)).withStyle(ChatFormatting.GRAY), false);
		return plots;
	}

	private static int plant(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) {
		int index = IntegerArgumentType.getInteger(ctx, "petak") - 1;
		Benih seed = Benih.byId(StringArgumentType.getString(ctx, "herba"));
		if (seed == null || index >= Kebun.plotCount(aula.owner())) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.kebun.cmd.bad"));
			return 0;
		}
		PetakHerba bed = PetakHerba.of(aula.sec(), index);
		// The test sows directly (no seed taken), a seed of 100 years.
		bed.plant(seed, 100);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.bed.sown", index + 1, seed.label(),
				1 + seed.mutu()).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int grow(ServerPlayer player, Aula aula, int days) {
		for (int d = 0; d < days; d++) {
			Kebun.advance(aula, 24000L);
		}
		player.sendSystemMessage(Component.translatable("kultivator.sekte.kebun.cmd.grown", days).withStyle(ChatFormatting.GREEN));
		return days;
	}

	private static int harvestAll(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) {
		int done = 0;
		for (int i = 0; i < Kebun.plotCount(aula.owner()); i++) {
			PetakHerba bed = PetakHerba.of(aula.sec(), i);
			if (bed.empty()) {
				continue;
			}
			bed.setProgress(1.0D);
			Pertumbuhan.harvest(aula, i, null, player);
			done++;
		}
		int count = done;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.cmd.harvested", count).withStyle(ChatFormatting.GREEN), false);
		return done;
	}

	private static int setLevel(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) {
		int level = IntegerArgumentType.getInteger(ctx, "tingkat");
		if (!TambangRoh.built(aula.owner())) {
			aula.sec().putBoolean("uji_tambang", true);
		}
		TambangRoh.setLevel(aula.sec(), level);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.cmd.level", level, TabelHasil.slots(level))
				.withStyle(ChatFormatting.GREEN), true);
		return level;
	}

	private static int testMine(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) {
		boolean on = !aula.sec().getBooleanOr("uji_tambang", false);
		aula.sec().putBoolean("uji_tambang", on);
		ctx.getSource().sendSuccess(() -> Component.translatable(on ? "kultivator.sekte.kebun.cmd.test_mine_on" : "kultivator.sekte.kebun.cmd.test_mine_off")
				.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int shift(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) {
		Kebun.advance(aula, 24000L);
		long day = Math.max(aula.sec().getLongOr("day", Ingatan.today(aula.server())), Ingatan.today(aula.server())) + 1L;
		GiliranTambang.endOfDay(aula, day);
		aula.sec().putLong("day", day);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.cmd.shift", day).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int testBeds(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) {
		int n = IntegerArgumentType.getInteger(ctx, "n");
		aula.sec().putInt("uji_petak", n);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.cmd.test_beds", n, Kebun.plotCount(aula.owner()))
				.withStyle(ChatFormatting.GREEN), true);
		return n;
	}

	private static int streaks(ServerPlayer player, Aula aula, CommandContext<CommandSourceStack> ctx) {
		int days = IntegerArgumentType.getInteger(ctx, "hari");
		long today = Math.max(aula.sec().getLongOr("day", 0L), Ingatan.today(aula.server()));
		int set = 0;
		for (DiscipleEntity disciple : Disciples.loaded(aula.owner())) {
			CompoundTag kerja = Kebun.kerja(disciple.data());
			if (kerja.getStringOr("job", "").isEmpty()) {
				continue;
			}
			kerja.putInt("streak", days);
			kerja.putLong("last_day", today);
			kerja.remove("forced_day");
			set++;
		}
		int count = set;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kebun.cmd.streaks", count, days).withStyle(ChatFormatting.GREEN), true);
		return set;
	}

	private KebunCommands() {
	}
}
