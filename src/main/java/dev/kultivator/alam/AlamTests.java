package dev.kultivator.alam;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * The test commands of module A (DESIGN 25.11): {@code uji alam <id> [lapisan]} (at once: no key, no omen, no cooldown),
 * {@code uji alam ruang <n>|selesai|gagal|penjaga}, {@code uji alam ringan nyala|mati}, {@code uji alam slot} and
 * {@code uji kristal <n>}.
 */
final class AlamTests {

	static void add(LiteralArgumentBuilder<CommandSourceStack> uji) {
		LiteralArgumentBuilder<CommandSourceStack> alam = Commands.literal("alam");
		for (String id : RealmSpecs.ids()) {
			alam.then(Commands.literal(id)
					.executes(ctx -> open(ctx.getSource().getPlayerOrException(), id, -1))
					.then(Commands.argument("lapisan", IntegerArgumentType.integer(1, 9999))
							.executes(ctx -> open(ctx.getSource().getPlayerOrException(), id, IntegerArgumentType.getInteger(ctx, "lapisan")))));
		}
		alam.then(Commands.literal("ruang").then(Commands.argument("n", IntegerArgumentType.integer(1, 12)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			RealmRun run = running(player);
			if (run == null) {
				return 0;
			}
			run.testSkipTo(IntegerArgumentType.getInteger(ctx, "n"));
			return ok(player, "kultivator.alam.test.room");
		})));
		alam.then(Commands.literal("selesai").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			RealmRun run = running(player);
			if (run == null) {
				return 0;
			}
			run.testClear();
			return ok(player, "kultivator.alam.test.clear");
		}));
		alam.then(Commands.literal("gagal").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			RealmRun run = running(player);
			if (run == null) {
				return 0;
			}
			run.leave(player, true);
			return ok(player, "kultivator.alam.test.fail");
		}));
		alam.then(Commands.literal("penjaga").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			RealmRun run = running(player);
			if (run == null) {
				return 0;
			}
			run.testGuardian();
			return ok(player, "kultivator.alam.test.guardian");
		}));
		alam.then(Commands.literal("ringan")
				.then(Commands.literal("nyala").executes(ctx -> light(ctx.getSource().getPlayerOrException(), true)))
				.then(Commands.literal("mati").executes(ctx -> light(ctx.getSource().getPlayerOrException(), false))));
		alam.then(Commands.literal("slot").executes(ctx -> slots(ctx.getSource().getPlayerOrException())));
		uji.then(alam);
		uji.then(Commands.literal("kristal").then(Commands.argument("n", IntegerArgumentType.integer(0, Alam.CRYSTAL_CAP)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			int n = IntegerArgumentType.getInteger(ctx, "n");
			Alam.setCrystals(player, n);
			Msg.chat(player, Component.translatable("kultivator.alam.test.crystals", n, Msg.number(n * Alam.CRYSTAL_HEALTH)).withStyle(ChatFormatting.GOLD));
			return 1;
		})));
	}

	private static int open(ServerPlayer player, String id, int layer) {
		RealmSpec spec = RealmSpecs.byId(id);
		if (spec != null) {
			Alam.resetCooldown(player, spec);
		}
		return Alam.enter(player, id, true, layer) ? 1 : 0;
	}

	private static RealmRun running(ServerPlayer player) {
		RealmRun run = RealmRun.of(player);
		if (run == null) {
			Msg.chat(player, Component.translatable("kultivator.alam.why.not_running").withStyle(ChatFormatting.GRAY));
		}
		return run;
	}

	private static int ok(ServerPlayer player, String key) {
		Msg.chat(player, Component.translatable(key).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int light(ServerPlayer player, boolean on) {
		Alam.setForceLight(player, on);
		Msg.chat(player, Component.translatable(on ? "kultivator.alam.test.light_on" : "kultivator.alam.test.light_off").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int slots(ServerPlayer player) {
		int allowed = KultivatorConfig.get().secretRealmSlots;
		StringBuilder used = new StringBuilder();
		for (int s = 0; s < Slots.MAX; s++) {
			if (Slots.used(s)) {
				used.append(used.length() == 0 ? "" : ", ").append(s).append(" @ ").append(Slots.centre(s).toShortString());
			}
		}
		Msg.chat(player, Component.translatable("kultivator.alam.test.slots", Slots.leased(), allowed, used.length() == 0 ? "-" : used.toString())
				.withStyle(ChatFormatting.GOLD));
		for (Map.Entry<String, String> entry : RealmRun.summary().entrySet()) {
			Msg.chat(player, Component.literal(" - " + entry.getValue()).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private AlamTests() {
	}
}
