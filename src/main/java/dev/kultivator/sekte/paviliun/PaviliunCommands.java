package dev.kultivator.sekte.paviliun;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.technique.source.JadeSlips;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module A's commands (DESIGN 36.2.5 "Uji"): {@code /kultivasi uji perguruan paviliun} (op) with {@code rak tambah <id>}
 * (a jade slip of technique {@code id} straight onto the shelf), {@code rak daftar}, {@code baca <nama>} (sit down to
 * read now: the gates of the rack still apply, not the chance, the night or the one-a-day rule), {@code selesai <nama>}
 * (finish the reading now), {@code makam tambah <nama>} (a grave in that disciple's likeness; the disciple stays alive),
 * {@code makam bersih} (removes the test graves), {@code upacara [harian|qingming|makam]} (a rite now, without its
 * day rules), {@code berkah} (the blessing worked out) and {@code sinkron} (resend the world picture); and for everyone
 * {@code /kultivasi perguruan paviliun} (the Rak Kitab) and {@code perguruan paviliun makam} (the Makam Leluhur).
 */
final class PaviliunCommands {

	static void init() {
		SekteCommands.test(SekteIds.A, PaviliunCommands::tests);
		SekteCommands.player(SekteIds.A, () -> Commands.literal(SekteIds.A)
				.executes(ctx -> {
					PaviliunView.open(ctx.getSource().getPlayerOrException(), PaviliunPayloads.PAGE_RAK, true);
					return 1;
				})
				.then(Commands.literal("makam").executes(ctx -> {
					PaviliunView.open(ctx.getSource().getPlayerOrException(), PaviliunPayloads.PAGE_MAKAM, true);
					return 1;
				})));
		SekteCommands.chips("perguruan paviliun rak tambah <id>", "perguruan paviliun rak daftar", "perguruan paviliun baca <nama>",
				"perguruan paviliun selesai <nama>", "perguruan paviliun makam tambah <nama>", "perguruan paviliun makam bersih",
				"perguruan paviliun upacara [harian|qingming|makam]", "perguruan paviliun berkah", "perguruan paviliun sinkron");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tests() {
		return Commands.literal(SekteIds.A)
				.executes(PaviliunCommands::status)
				.then(Commands.literal("rak")
						.then(Commands.literal("tambah")
								.then(Commands.argument("id", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(specIds(), b))
										.executes(PaviliunCommands::rackAdd)))
						.then(Commands.literal("daftar").executes(PaviliunCommands::rackList)))
				.then(Commands.literal("baca")
						.then(Commands.argument("nama", StringArgumentType.string()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(ctx), b))
								.executes(PaviliunCommands::read)))
				.then(Commands.literal("selesai")
						.then(Commands.argument("nama", StringArgumentType.string()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(ctx), b))
								.executes(PaviliunCommands::finish)))
				.then(Commands.literal("makam")
						.then(Commands.literal("tambah")
								.then(Commands.argument("nama", StringArgumentType.string())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(ctx), b))
										.executes(PaviliunCommands::graveAdd)))
						.then(Commands.literal("bersih").executes(PaviliunCommands::graveClear)))
				.then(Commands.literal("upacara")
						.executes(ctx -> rite(ctx, "harian"))
						.then(Commands.literal("harian").executes(ctx -> rite(ctx, "harian")))
						.then(Commands.literal("qingming").executes(ctx -> rite(ctx, "qingming")))
						.then(Commands.literal("makam").executes(ctx -> rite(ctx, "makam"))))
				.then(Commands.literal("berkah").executes(PaviliunCommands::blessing))
				.then(Commands.literal("sinkron").executes(ctx -> {
					PaviliunSync.dirty(ctx.getSource().getPlayerOrException().getUUID());
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.sync"), false);
					return 1;
				}));
	}

	// ================================================================== helpers

	private static List<String> specIds() {
		List<String> ids = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.teachable()) {
				ids.add(spec.id());
			}
		}
		return ids;
	}

	private static List<String> names(CommandContext<CommandSourceStack> ctx) {
		List<String> list = new ArrayList<>();
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) {
			for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
				String name = d.getName().getString();
				list.add(name.indexOf(' ') >= 0 ? "\"" + name + "\"" : name);
			}
		}
		return list;
	}

	private static @Nullable DiscipleEntity byName(ServerPlayer player, String name) {
		for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
			if (d.role() == DiscipleEntity.Role.DISCIPLE && d.getName().getString().equalsIgnoreCase(name.trim())) {
				return d;
			}
		}
		return null;
	}

	private static @Nullable DiscipleEntity disciple(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiscipleEntity entity = byName(player, StringArgumentType.getString(ctx, "nama"));
		if (entity == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.paviliun.cmd.no_disciple"));
		}
		return entity;
	}

	// ================================================================== commands

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		MinecraftServer server = ctx.getSource().getServer();
		int books = PaviliunData.rack(owner).size();
		int readers = BelajarMandiri.readers(owner).size();
		int graves = PaviliunData.graves(owner).size();
		int lineage = PaviliunData.lineage(owner).size();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.status", books, RakKitab.capacity(owner), readers,
				graves, Makam.slots(owner, true), lineage, Math.round(AltarLeluhur.blessing(owner) * 1000.0D) / 10.0D,
				Upacara.daysLeft(server, owner)).withStyle(ChatFormatting.AQUA), false);
		BlockPos library = SectBuild.anchor(owner, "library");
		BlockPos altar = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_ALTAR);
		BlockPos centre = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_CENTER);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.anchors", where(library), where(altar), where(centre))
				.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static Component where(@Nullable BlockPos pos) {
		return pos == null ? Component.translatable("kultivator.sekte.paviliun.cmd.none")
				: Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
	}

	private static int rackAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (spec == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.paviliun.cmd.no_spec", id));
			return 0;
		}
		ItemStack slip = JadeSlips.stack(id);
		if (slip.isEmpty() || !RakKitab.accepts(slip)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.paviliun.cmd.not_teachable", spec.displayName()));
			return 0;
		}
		String why = RakKitab.deposit(player, slip);
		if (why != null) {
			ctx.getSource().sendFailure(Component.translatable(why, RakKitab.capacity(player.getUUID())));
			return 0;
		}
		return 1;
	}

	private static int rackList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<PaviliunData.Kitab> rack = PaviliunData.rack(player.getUUID());
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.rack", rack.size(), RakKitab.capacity(player.getUUID()))
				.withStyle(ChatFormatting.GOLD), false);
		for (PaviliunData.Kitab k : rack) {
			ctx.getSource().sendSuccess(() -> Component.literal(" #" + k.id() + " ").append(k.name()).append(Component.literal(" " + k.specs())
					.withStyle(ChatFormatting.DARK_GRAY)), false);
		}
		return rack.size();
	}

	private static int read(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity entity = disciple(ctx);
		if (entity == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		RakKitab.Pick pick = RakKitab.choose(entity, owner, player);
		if (pick == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.paviliun.cmd.nothing", entity.getName()));
			return 0;
		}
		ServerLevel level = (ServerLevel) entity.level();
		BlockPos library = SectBuild.anchor(owner, "library");
		BlockPos base = library != null ? library : player.blockPosition();
		List<BlockPos> taken = new ArrayList<>();
		for (DiscipleEntity r : BelajarMandiri.readers(owner)) {
			if (r.activitySpot() != null) {
				taken.add(r.activitySpot());
			}
		}
		BlockPos seat = Tempat.seat(level, base, taken);
		if (seat == null) {
			seat = entity.blockPosition();
		}
		CompoundTag record = PaviliunData.study(entity.data());
		record.remove(PaviliunData.DAY_DONE);
		BelajarMandiri.start(ctx.getSource().getServer(), owner, entity, pick, seat);
		BlockPos at = seat;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.read", entity.getName(), pick.spec().displayName(),
				at.getX() + " " + at.getY() + " " + at.getZ()), false);
		return 1;
	}

	private static int finish(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity entity = disciple(ctx);
		if (entity == null) {
			return 0;
		}
		String id = PaviliunData.studying(entity.data());
		TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
		if (spec == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.paviliun.cmd.not_reading", entity.getName()));
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BelajarMandiri.learn(ctx.getSource().getServer(), player.getUUID(), entity, spec, player);
		return 1;
	}

	private static int graveAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity entity = disciple(ctx);
		if (entity == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		PaviliunData.Grave grave = Makam.buryTest(ctx.getSource().getServer(), player.getUUID(), entity);
		if (grave == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.paviliun.no_hall"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.grave", grave.name(), grave.slot() + 1), false);
		return 1;
	}

	private static int graveClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		List<PaviliunData.Grave> graves = new ArrayList<>(PaviliunData.graves(owner));
		int before = graves.size();
		graves.removeIf(g -> "uji".equals(g.cause()));
		PaviliunData.setGraves(owner, graves);
		List<PaviliunData.Grave> lineage = new ArrayList<>(PaviliunData.lineage(owner));
		int lineageBefore = lineage.size();
		lineage.removeIf(g -> "uji".equals(g.cause()));
		PaviliunData.setLineage(owner, lineage);
		Makam.rebuild(owner);
		PaviliunSync.dirty(owner);
		int removed = before - graves.size() + lineageBefore - lineage.size();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.cleared", removed), false);
		return removed;
	}

	private static int rite(CommandContext<CommandSourceStack> ctx, String kind) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		MinecraftServer server = ctx.getSource().getServer();
		switch (kind) {
			case "qingming" -> {
				String why = Upacara.startQingming(server, owner, true);
				if (why != null) {
					ctx.getSource().sendFailure(Component.translatable(why, 0));
					return 0;
				}
			}
			case "makam" -> {
				List<PaviliunData.Grave> graves = PaviliunData.graves(owner);
				if (graves.isEmpty()) {
					ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.paviliun.cmd.no_graves"));
					return 0;
				}
				PaviliunData.Grave g = graves.get(0);
				BlockPos stone = g.slot() >= 0 ? SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_GRAVE + g.slot()) : null;
				List<DiscipleEntity> mourners = new ArrayList<>();
				for (DiscipleEntity d : Disciples.loaded(owner)) {
					if (mourners.size() < 4 && Tempat.free(d, true, DiscipleEntity.ACTIVITY_NONE)) {
						mourners.add(d);
					}
				}
				Upacara.funeral(server, owner, g, stone, mourners);
			}
			default -> {
				PaviliunData.putLong(owner, PaviliunData.INCENSE_DAY, Ingatan.today(server) - 1L);
				Upacara.incense(player, ItemStack.EMPTY);
			}
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.rite", kind), false);
		return 1;
	}

	private static int blessing(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		UUID owner = ctx.getSource().getPlayerOrException().getUUID();
		int tablets = Silsilah.tablets(owner);
		double fresh = AltarLeluhur.freshness(owner);
		double total = AltarLeluhur.blessing(owner);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.paviliun.cmd.blessing", tablets,
				Math.round(Math.min(AltarLeluhur.TABLET_MAX, tablets * AltarLeluhur.TABLET_STEP) * 1000.0D) / 10.0D, Math.round(fresh * 100.0D),
				Math.round(fresh * AltarLeluhur.FRESH_MAX * 1000.0D) / 10.0D, Math.round(total * 1000.0D) / 10.0D).withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private PaviliunCommands() {
	}
}
