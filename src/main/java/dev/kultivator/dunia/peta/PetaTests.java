package dev.kultivator.dunia.peta;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /kultivasi uji peta ...} (op, DESIGN 34.5.16 "Uji"): {@code ungkap <radius>} (explores every cell within the
 * radius and queues their samples), {@code situs semua} (discovers every published site), {@code situs bersihkan} (repairs old cave sites, DuniaSites.cleanCaves), {@code reset} (forgets the
 * fog, waypoints and sites of the player), {@code domain} (lists the NPC sect domains), {@code sampel} (the cell under
 * the player and the sampler's queue).
 */
final class PetaTests {

	static void register() {
		DuniaCommands.test("peta", PetaTests::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("peta")
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.peta.cmd.usage").withStyle(ChatFormatting.GRAY), false);
					return 1;
				})
				.then(Commands.literal("ungkap").then(Commands.argument("radius", IntegerArgumentType.integer(16, 8192)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					int radius = IntegerArgumentType.getInteger(ctx, "radius");
					PetaData data = PetaData.get(ctx.getSource().getServer());
					int fresh = Peta.revealAround(ctx.getSource().getServer(), data, player.getUUID(), Peta.dimId(player.level()), player.getX(),
							player.getZ(), radius);
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.peta.cmd.revealed", fresh, radius)), false);
					return 1;
				})))
				.then(Commands.literal("situs").then(Commands.literal("semua").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					int n = 0;
					for (DuniaSites.Site site : DuniaSites.all(ctx.getSource().getServer())) {
						if (DuniaSites.discover(player, site.id())) {
							n++;
						}
					}
					int count = n;
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.peta.cmd.sites", count)), false);
					return 1;
				})).then(Commands.literal("bersihkan").executes(ctx -> {
					// save repair: caves that old rumours and pointer scrolls published as Gua Kecil (integration audit S18)
					int[] result = DuniaSites.cleanCaves(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.sambung.peta.bersihkan", result[0], result[1])), true);
					return result[0];
				})))
				.then(Commands.literal("reset").executes(ctx -> {
					Peta.reset(ctx.getSource().getPlayerOrException());
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.peta.cmd.reset")), false);
					return 1;
				}))
				.then(Commands.literal("domain").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					List<PetaDomains.Domain> domains = PetaDomains.of((ServerLevel) player.level(), Peta.config().npcDomains);
					if (domains.isEmpty()) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.peta.cmd.no_domains"));
						return 0;
					}
					for (PetaDomains.Domain domain : domains) {
						ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.peta.cmd.domain", domain.name(), domain.centre().getX(),
								domain.centre().getZ(), domain.radius()).withColor(domain.color() | 0x404040), false);
					}
					// the domains' middles are revealed so the inked borders show at once
					PetaData data = PetaData.get(ctx.getSource().getServer());
					for (PetaDomains.Domain domain : domains) {
						Peta.revealAround(ctx.getSource().getServer(), data, player.getUUID(), Peta.dimId(player.level()), domain.centre().getX(),
								domain.centre().getZ(), 160.0D);
					}
					return 1;
				}))
				.then(Commands.literal("sampel").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					PetaData data = PetaData.get(ctx.getSource().getServer());
					int cs = data.cellSize;
					int cx = Math.floorDiv(player.getBlockX(), cs);
					int cz = Math.floorDiv(player.getBlockZ(), cs);
					byte cell = Peta.sampleCell((ServerLevel) player.level(), cs, cx, cz);
					data.setCell(Peta.dimId(player.level()), cx, cz, cell);
					int kind = PetaInk.kind(cell);
					int band = PetaInk.band(cell);
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.peta.cmd.sample", cx, cz,
							Component.translatable("kultivator.peta.ink." + kind), band, Peta.queued(), Peta.sampledSoFar(),
							data.exploredCount(player.getUUID(), Peta.dimId(player.level())))), false);
					return 1;
				}));
	}

	private PetaTests() {
	}
}
