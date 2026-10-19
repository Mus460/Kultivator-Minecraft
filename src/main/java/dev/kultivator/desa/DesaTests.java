package dev.kultivator.desa;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /kultivasi uji desa ...} (DESIGN 25.11) and {@code debug desa poi}: every village mechanic can be tried at once on
 * the plaque the player looks at (or their nearest village within 96 blocks).
 */
final class DesaTests {

	private static final double NEAR = 96.0D;

	static void register(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("desa")
				.then(Commands.literal("ikrar").executes(DesaTests::pledge))
				.then(Commands.literal("setia")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							Village village = target(player);
							if (village == null || !village.pledged) {
								return none(ctx);
							}
							int n = IntegerArgumentType.getInteger(ctx, "n");
							MinecraftServer server = player.level().getServer();
							if (n != village.loyalty) {
								Desa.addLoyalty(server, player.getUUID(), village, n - village.loyalty);
							}
							return ok(ctx, Component.translatable("kultivator.desa.test.loyalty", village.name, n));
						})))
				.then(Commands.literal("upeti")
						.executes(ctx -> tribute(ctx, 1))
						.then(Commands.argument("siklus", IntegerArgumentType.integer(1, Tribute.MAX_CATCHUP))
								.executes(ctx -> tribute(ctx, IntegerArgumentType.getInteger(ctx, "siklus")))))
				.then(Commands.literal("minta")
						.then(Commands.argument("jenis", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.asList(Village.REQUESTS), builder))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Village village = target(player);
									String type = StringArgumentType.getString(ctx, "jenis");
									if (village == null || !village.pledged) {
										return none(ctx);
									}
									if (!Arrays.asList(Village.REQUESTS).contains(type)) {
										ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.desa.test.bad_type", type)));
										return 0;
									}
									Requests.open(player.level().getServer(), player.getUUID(), village, type, player);
									return ok(ctx, Component.translatable("kultivator.desa.test.request", village.name, type));
								})))
				.then(Commands.literal("jarah")
						.executes(ctx -> raid(ctx, true))
						.then(Commands.literal("hadir").executes(ctx -> raid(ctx, true)))
						.then(Commands.literal("jauh").executes(ctx -> raid(ctx, false)))));
	}

	/** The plaque the player looks at, or their nearest village within 96 blocks. */
	static @Nullable Village target(ServerPlayer player) {
		BlockHitResult hit = Targeting.blockHit(player, 8.0D);
		if (hit.getType() == HitResult.Type.BLOCK) {
			Map.Entry<UUID, Village> found = Vassals.byPlaque(hit.getBlockPos());
			if (found != null && found.getKey().equals(player.getUUID())) {
				return found.getValue();
			}
		}
		Village best = null;
		double bestSq = NEAR * NEAR;
		for (Village village : Desa.villages(player.getUUID())) {
			double distSq = player.position().distanceToSqr(Vec3.atCenterOf(village.plaque));
			if (distSq <= bestSq) {
				bestSq = distSq;
				best = village;
			}
		}
		return best;
	}

	/** {@code uji desa ikrar}: the looked-at plaque's village swears at once (a record is made when there was none). */
	private static int pledge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		Village village = target(player);
		if (village == null) {
			BlockHitResult hit = Targeting.blockHit(player, 8.0D);
			if (hit.getType() != HitResult.Type.BLOCK || !player.level().getBlockState(hit.getBlockPos()).is(DesaItems.SECT_PATRON_PLAQUE)
					|| Vassals.byPlaque(hit.getBlockPos()) != null) {
				return none(ctx);
			}
			Vassals.Owner entry = Vassals.owner(player.getUUID());
			if (entry == null) {
				return none(ctx);
			}
			BlockPos plaque = hit.getBlockPos().immutable();
			BlockPos bell = VillageScan.findBell(player.level(), plaque);
			village = new Village();
			village.id = entry.nextId++;
			village.plaque = plaque;
			village.bell = bell == null ? plaque : bell;
			village.name = Pledge.NAMES[player.getRandom().nextInt(Pledge.NAMES.length)] + " " + village.id;
			village.villagers = VillageScan.villagersOf(player.level(), village.bell).size();
			entry.villages.add(village);
			Vassals.changed();
		}
		if (village.pledged) {
			return ok(ctx, Component.translatable("kultivator.desa.test.already", village.name));
		}
		VillageRaids.stop(player.getUUID(), village.id);
		Pledge.complete(server, player.getUUID(), village);
		return ok(ctx, Component.translatable("kultivator.desa.test.pledged", village.name));
	}

	private static int tribute(CommandContext<CommandSourceStack> ctx, int cycles) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Village village = target(player);
		if (village == null || !village.pledged) {
			return none(ctx);
		}
		for (int i = 0; i < cycles; i++) {
			double factor = village.halfTribute ? 0.5D : 1.0D;
			village.halfTribute = false;
			Tribute.pay(player.level().getServer(), player.getUUID(), village, factor);
		}
		return ok(ctx, Component.translatable("kultivator.desa.test.tribute", village.name, cycles));
	}

	private static int raid(CommandContext<CommandSourceStack> ctx, boolean present) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Village village = target(player);
		if (village == null || !village.pledged) {
			return none(ctx);
		}
		MinecraftServer server = player.level().getServer();
		if (present) {
			if (!VillageRaids.start(server, player.getUUID(), village, VillageRaids.KIND_RAID)) {
				ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.desa.test.busy")));
				return 0;
			}
		} else {
			VillageRaids.resolveAbsent(server, player.getUUID(), village);
		}
		return ok(ctx, Component.translatable("kultivator.desa.test.raid", village.name));
	}

	/** {@code debug desa poi}: the bell, whether it stands, the counted villagers and the live event of the nearest village. */
	static int debugPoi(ServerPlayer player) {
		Village village = target(player);
		ServerLevel level = player.level();
		if (village == null) {
			BlockPos bell = VillageScan.findBell(level, player.blockPosition());
			Msg.chat(player, Component.translatable("kultivator.desa.debug.free", bell == null ? "-" : bell.toShortString(),
					bell == null ? 0 : VillageScan.villagersOf(level, bell).size()).withStyle(ChatFormatting.GRAY));
			return bell == null ? 0 : 1;
		}
		Msg.chat(player, Component.translatable("kultivator.desa.debug.village", village.name, village.bell.toShortString(),
				String.valueOf(VillageScan.bellStands(level, village.bell)), VillageScan.villagersOf(level, village.bell).size(),
				VillageRaids.describe(player.getUUID(), village.id)).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int none(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.desa.test.none")));
		return 0;
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendSuccess(() -> Msg.prefixed(message), false);
		return 1;
	}

	private DesaTests() {
	}
}
