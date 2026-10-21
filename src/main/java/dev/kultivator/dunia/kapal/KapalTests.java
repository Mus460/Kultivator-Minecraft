package dev.kultivator.dunia.kapal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /kultivasi uji kapal ...} (op, DESIGN 34.5.15 "Uji"): {@code beri <perahu|sekte|bahtera>} (a keel with an hour of
 * fuel), {@code panggil}, {@code bahan_bakar <menit>}, {@code lompat <x> <z>} (a cloud leap toward x/z now),
 * {@code bajak} (sky pirates now), {@code rusak}, {@code perbaiki}, {@code hapus}. Every command acts on the ship the
 * player rides, else their deployed one.
 */
final class KapalTests {

	static void register() {
		DuniaCommands.test("kapal", KapalTests::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("kapal")
				.executes(ctx -> usage(ctx.getSource()))
				.then(Commands.literal("beri")
						.then(Commands.argument("kelas", com.mojang.brigadier.arguments.StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(KapalClass.values()).map(KapalClass::command), builder))
								.executes(KapalTests::give)))
				.then(Commands.literal("panggil").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					boolean ok = Kapal.summon(player);
					reply(ctx.getSource(), ok ? "kultivator.kapal.cmd.summoned" : "kultivator.kapal.cmd.no_ship", ok);
					return ok ? 1 : 0;
				}))
				.then(Commands.literal("bahan_bakar")
						.then(Commands.argument("menit", IntegerArgumentType.integer(1, 1440)).executes(ctx -> {
							KapalRohEntity ship = ship(ctx);
							if (ship == null) {
								return 0;
							}
							int minutes = IntegerArgumentType.getInteger(ctx, "menit");
							ship.addFuel(minutes * 1200L);
							ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kapal.cmd.fuel", minutes)), false);
							return 1;
						})))
				.then(Commands.literal("lompat")
						.then(Commands.argument("x", IntegerArgumentType.integer(-29_000_000, 29_000_000))
								.then(Commands.argument("z", IntegerArgumentType.integer(-29_000_000, 29_000_000)).executes(KapalTests::leap))))
				.then(Commands.literal("bajak").executes(ctx -> {
					KapalRohEntity ship = ship(ctx);
					if (ship == null) {
						return 0;
					}
					ship.startPirates((ServerLevel) ship.level(), ship.captain());
					return 1;
				}))
				.then(Commands.literal("rusak").executes(ctx -> hull(ctx, true)))
				.then(Commands.literal("perbaiki").executes(ctx -> hull(ctx, false)))
				.then(Commands.literal("hapus").executes(KapalTests::remove));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.kapal.cmd.usage").withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static void reply(CommandSourceStack source, String key, boolean ok) {
		if (ok) {
			source.sendSuccess(() -> Msg.prefixed(Component.translatable(key)), false);
		} else {
			source.sendFailure(Component.translatable(key));
		}
	}

	private static KapalRohEntity ship(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KapalRohEntity ship = Kapal.shipOf(player);
		if (ship == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kapal.cmd.no_ship"));
		}
		return ship;
	}

	private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kelas");
		KapalClass kind = KapalClass.byName(name);
		if (kind == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kapal.cmd.unknown_class", name));
			return 0;
		}
		ItemStack keel = new ItemStack(KapalItems.keel(kind));
		keel.set(KapalItems.STATE, new KapalItems.State(kind.hull(), 60L * 1200L, KapalRohEntity.defaultColor(player), false, ""));
		keel.set(net.minecraft.core.component.DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		Kapal.give(player, keel);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kapal.cmd.given",
				Component.translatable("kultivator.kapal.class." + kind.model()))), false);
		return 1;
	}

	private static int leap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KapalRohEntity ship = ship(ctx);
		if (ship == null) {
			return 0;
		}
		if (ship.captain() != player) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kapal.sit_helm"));
			return 0;
		}
		int x = IntegerArgumentType.getInteger(ctx, "x");
		int z = IntegerArgumentType.getInteger(ctx, "z");
		ship.testLeap(player, new Vec3(x, 0.0D, z));
		return 1;
	}

	private static int hull(CommandContext<CommandSourceStack> ctx, boolean broken) throws CommandSyntaxException {
		KapalRohEntity ship = ship(ctx);
		if (ship == null) {
			return 0;
		}
		ship.testHull((ServerLevel) ship.level(), broken);
		reply(ctx.getSource(), broken ? "kultivator.kapal.cmd.broken" : "kultivator.kapal.cmd.repaired", true);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		int removed = 0;
		KapalRohEntity ship = Kapal.shipOf(player);
		if (ship != null) {
			// a test removal gives the ship back as its keel (nothing is lost)
			Kapal.give(player, ship.toItem());
			ship.landDisciples((ServerLevel) ship.level(), true);
			ship.markStowed();
			ship.discard();
			removed++;
		}
		for (Map.Entry<UUID, KapalFleet.Entry> entry : KapalFleet.all(server)) {
			if (entry.getKey().equals(player.getUUID())) {
				KapalFleet.remove(server, entry.getKey(), null);
			}
		}
		int count = removed;
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kapal.cmd.removed", count)), false);
		return 1;
	}

	private KapalTests() {
	}
}
