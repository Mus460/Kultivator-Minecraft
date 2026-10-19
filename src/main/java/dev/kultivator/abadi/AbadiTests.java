package dev.kultivator.abadi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.Cultivators;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The test commands of P9-langit (DESIGN 26.3.7, 26.11): {@code uji abadi naik [sukses|gagal]}, {@code masuk}, {@code pulang},
 * {@code konversi <0-100>}, {@code batu <n>}, {@code pribumi <kind> [tier]} / {@code pribumi hapus}, {@code pulau},
 * {@code tepi}, {@code void}; and {@code uji lelang sesi|tutup|kejar}.
 */
final class AbadiTests {

	static void add(LiteralArgumentBuilder<CommandSourceStack> uji) {
		LiteralArgumentBuilder<CommandSourceStack> abadi = Commands.literal("abadi");
		abadi.then(Commands.literal("naik")
				.executes(ctx -> Ascend.start(ctx.getSource().getPlayerOrException(), true, null) ? 1 : 0)
				.then(Commands.literal("sukses").executes(ctx -> Ascend.start(ctx.getSource().getPlayerOrException(), true, Boolean.TRUE) ? 1 : 0))
				.then(Commands.literal("gagal").executes(ctx -> Ascend.start(ctx.getSource().getPlayerOrException(), true, Boolean.FALSE) ? 1 : 0)));
		abadi.then(Commands.literal("masuk").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (Abadi.immortalLevel(player.level().getServer()) == null) {
				return fail(player, "kultivator.abadi.why.no_dimension");
			}
			boolean first = !Abadi.ascended(player);
			Abadi.extra(player).putBoolean("ascended", true);
			Cultivators.dirty(player);
			Ascend.saveReturn(player);
			Ascend.arrive(player, first);
			return 1;
		}));
		abadi.then(Commands.literal("pulang").executes(ctx -> {
			Crossing.testHome(ctx.getSource().getPlayerOrException());
			return 1;
		}));
		abadi.then(Commands.literal("konversi").then(Commands.argument("n", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			OriginStones.setConv(player, IntegerArgumentType.getInteger(ctx, "n"));
			return ok(player, Component.translatable("kultivator.abadi.test.conv", OriginStones.conv(player)));
		})));
		abadi.then(Commands.literal("batu").then(Commands.argument("n", IntegerArgumentType.integer(1, 640)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			int n = IntegerArgumentType.getInteger(ctx, "n");
			Abadi.giveStones(player, n);
			return ok(player, Component.translatable("kultivator.abadi.test.stones", n));
		})));
		LiteralArgumentBuilder<CommandSourceStack> natives = Commands.literal("pribumi");
		for (String kind : Natives.KINDS) {
			natives.then(Commands.literal(kind)
					.executes(ctx -> native_(ctx.getSource().getPlayerOrException(), kind, 0))
					.then(Commands.argument("tier", IntegerArgumentType.integer(2, 3))
							.executes(ctx -> native_(ctx.getSource().getPlayerOrException(), kind, IntegerArgumentType.getInteger(ctx, "tier")))));
		}
		natives.then(Commands.literal("hapus").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			return ok(player, Component.translatable("kultivator.abadi.test.natives_removed", Natives.clearNear(player, 64.0D)));
		}));
		abadi.then(natives);
		abadi.then(Commands.literal("pulau").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			MinecraftServer server = player.level().getServer();
			BlockPos before = AlamAbadiData.get(server).arrival();
			ArrivalIsland.restart(server);
			BlockPos after = ArrivalIsland.arrival(server);
			return ok(player, Component.translatable("kultivator.abadi.test.island", before == null ? "-" : before.toShortString(), after.toShortString(),
					ArrivalIsland.usesFallback(server) ? Component.translatable("kultivator.abadi.test.island_fallback") : Component.empty()));
		}));
		abadi.then(Commands.literal("tepi").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (!Abadi.isImmortalLevel(player.level())) {
				return fail(player, "kultivator.abadi.test.not_inside");
			}
			double radius = dev.kultivator.KultivatorConfig.get().immortalRealmRadius - 10.0D;
			double distance = Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
			double nx = distance < 1.0D ? 1.0D : player.getX() / distance;
			double nz = distance < 1.0D ? 0.0D : player.getZ() / distance;
			player.teleportTo(nx * radius, Math.max(player.getY(), 120.0D), nz * radius);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 200, 0, false, false, true));
			return ok(player, Component.translatable("kultivator.abadi.test.edge"));
		}));
		abadi.then(Commands.literal("void").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (!Abadi.isImmortalLevel(player.level())) {
				return fail(player, "kultivator.abadi.test.not_inside");
			}
			player.teleportTo(player.getX(), -4.0D, player.getZ());
			return ok(player, Component.translatable("kultivator.abadi.test.void"));
		}));
		uji.then(abadi);

		uji.then(Commands.literal("lelang")
				.then(Commands.literal("sesi").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					if (!Auction.testOpen(player.level().getServer())) {
						return fail(player, "kultivator.abadi.test.auction_running");
					}
					return ok(player, Component.translatable("kultivator.abadi.test.auction_open"));
				}))
				.then(Commands.literal("tutup").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					if (!Auction.testClose(player.level().getServer())) {
						return fail(player, "kultivator.abadi.test.auction_none");
					}
					return ok(player, Component.translatable("kultivator.abadi.test.auction_closed"));
				}))
				.then(Commands.literal("kejar").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					String why = Pursuit.testNow(player);
					if (why != null) {
						return fail(player, why);
					}
					return ok(player, Component.translatable("kultivator.abadi.test.pursuit"));
				})));
	}

	private static int native_(ServerPlayer player, String kind, int tier) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 at = player.position().add(flat.scale(6.0D));
		int chosen = tier > 0 ? tier : ("penjaga".equals(kind) ? 3 : 2);
		Mob mob = Natives.spawn(level, at, kind, chosen, Abadi.effectiveRealm(player));
		if (mob == null) {
			return fail(player, "kultivator.abadi.test.native_failed");
		}
		return ok(player, Component.translatable("kultivator.abadi.test.native", Component.translatable("kultivator.abadi.native." + kind),
				mob.getDisplayName(), chosen, Math.round(mob.getMaxHealth())));
	}

	private static int ok(ServerPlayer player, Component message) {
		Msg.chat(player, message.copy().withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int fail(ServerPlayer player, String key) {
		Msg.chat(player, Component.translatable(key).withStyle(ChatFormatting.GRAY));
		return 0;
	}

	private AbadiTests() {
	}
}
