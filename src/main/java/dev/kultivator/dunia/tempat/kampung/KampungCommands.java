package dev.kultivator.dunia.tempat.kampung;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.desa.Tribute;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.kota.Kota;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Commands of the villages (DESIGN 38.D.10). Op tests under {@code /kultivasi uji kampung}:
 * <ul>
 *   <li>{@code cari <ukuran> [gaya]}: teleport to the nearest village of that size (and style), vanilla locate;</li>
 *   <li>{@code klan [sikap <n>]}: the nearest clan village's patriarch realm and the caller's regard, beaten flags;
 *       {@code sikap <n>} sets the regard (to try the pledge gate);</li>
 *   <li>{@code panen}: starts the Harvest Festival of the nearest village bell now;</li>
 *   <li>{@code anak}: the nearest pledged village brings its talented child now (caller must be its master);</li>
 *   <li>{@code daftar}: the villages this world remembers, by size.</li>
 * </ul>
 * Player commands (the chips of the cards): {@code /kultivasi kampung tantang terima|mengalah}, {@code patriark},
 * {@code pasar}, {@code beli <n>}.
 */
final class KampungCommands {

	static final List<String> UKURAN = List.of("dusun", "desa", "desa_besar", "klan");
	static final List<String> GAYA = List.of("sedang", "salju", "sabana", "sakura", "gurun", "rawa", "terasering", "gunung");

	static void init() {
		DuniaCommands.test("kampung", KampungCommands::uji);
		DuniaCommands.player("kampung", KampungCommands::pemain);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("kampung")
				.then(Commands.literal("cari").then(Commands.argument("ukuran", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(UKURAN, b))
						.executes(ctx -> cari(ctx.getSource(), StringArgumentType.getString(ctx, "ukuran"), null))
						.then(Commands.argument("gaya", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(GAYA, b))
								.executes(ctx -> cari(ctx.getSource(), StringArgumentType.getString(ctx, "ukuran"),
										StringArgumentType.getString(ctx, "gaya"))))))
				.then(Commands.literal("klan").executes(ctx -> klan(ctx.getSource(), Integer.MIN_VALUE))
						.then(Commands.literal("sikap").then(Commands.argument("n", IntegerArgumentType.integer(-100, 100))
								.executes(ctx -> klan(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"))))))
				.then(Commands.literal("panen").executes(ctx -> panen(ctx.getSource())))
				.then(Commands.literal("anak").executes(ctx -> anak(ctx.getSource())))
				.then(Commands.literal("daftar").executes(ctx -> daftar(ctx.getSource())));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> pemain() {
		return Commands.literal("kampung")
				.then(Commands.literal("tantang")
						.then(Commands.literal("terima").executes(ctx -> Klan.jawab(ctx.getSource().getPlayerOrException(), true)))
						.then(Commands.literal("mengalah").executes(ctx -> Klan.jawab(ctx.getSource().getPlayerOrException(), false))))
				.then(Commands.literal("patriark").executes(ctx -> Klan.tantangPatriark(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("pasar").executes(ctx -> PasarDesa.lihat(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("beli").then(Commands.argument("no", IntegerArgumentType.integer(0, 31))
						.executes(ctx -> PasarDesa.beli(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "no")))));
	}

	// ------------------------------------------------------------------ uji

	private static int cari(CommandSourceStack source, String ukuran, String gaya) {
		ServerPlayer player = source.getPlayer();
		if (player == null || !UKURAN.contains(ukuran)) {
			source.sendFailure(Component.translatable("kultivator.kampung.uji.ukuran_salah", ukuran));
			return 0;
		}
		if (gaya != null) {
			return DuniaCommands.findAndTeleport(player, Kultivator.id("kampung/" + ukuran + "_" + gaya));
		}
		ServerLevel level = source.getServer().overworld();
		TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, Kultivator.id("kampung/" + ukuran));
		Optional<HolderSet.Named<Structure>> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tag);
		BlockPos found = set.isEmpty() ? null : level.findNearestMapStructure(tag, player.blockPosition(), 100, false);
		if (found == null) {
			source.sendFailure(Component.translatable("kultivator.kampung.uji.tidak_ada", ukuran));
			return 0;
		}
		level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.FULL, true);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
		player.teleportTo(level, found.getX() + 0.5D, y + 1.0D, found.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot(), true);
		source.sendSuccess(() -> Component.translatable("kultivator.kampung.uji.cari", ukuran, found.getX(), y, found.getZ()), false);
		return 1;
	}

	private static int klan(CommandSourceStack source, int set) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		KampungData data = KampungData.get(source.getServer());
		KampungData.Desa d = null;
		double best = Double.MAX_VALUE;
		for (KampungData.Desa each : data.semua()) {
			double dist = each.pusat.distSqr(player.blockPosition());
			if (each.klan() && dist < best) {
				best = dist;
				d = each;
			}
		}
		if (d == null) {
			source.sendFailure(Component.translatable("kultivator.kampung.uji.klan_tidak_ada"));
			return 0;
		}
		if (set != Integer.MIN_VALUE) {
			data.ubahSikap(d, player.getUUID(), set - data.sikap(d, player.getUUID()));
		}
		KampungData.Desa c = d;
		int dist = (int) Math.sqrt(best);
		source.sendSuccess(() -> Component.translatable("kultivator.kampung.uji.klan", KampungNames.nama(c.ukuran, c.situs), c.gaya, dist,
				Component.translatable("kultivator.realm." + Klan.ranahPatriark(c.situs).id()), data.sikap(c, player.getUUID()),
				data.patriarkKalah(c, player.getUUID()), data.tuanMudaKalah(c, player.getUUID()), data.mengalah(c, player.getUUID())), false);
		return 1;
	}

	private static int panen(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		DuniaAnchorEntity bell = DuniaAnchors.nearest(level, source.getPosition(), "kampung/lonceng", 96.0D);
		if (bell == null) {
			source.sendFailure(Component.translatable("kultivator.kampung.uji.lonceng_tidak_ada"));
			return 0;
		}
		KampungData data = KampungData.get(source.getServer());
		long site = KampungAnchors.situs(bell);
		FestivalPanen.akhiri(level, site);
		KampungData.Desa d = data.desa(site);
		FestivalPanen.mulai(level, bell, data, d, Kota.day(source.getServer()), FestivalPanen.MULAI);
		source.sendSuccess(() -> Component.translatable("kultivator.kampung.uji.panen", KampungNames.nama(d.ukuran, d.situs)), false);
		return 1;
	}

	private static int anak(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		ServerLevel level = source.getServer().overworld();
		DuniaAnchorEntity bell = DuniaAnchors.nearest(level, source.getPosition(), "kampung/lonceng", 96.0D);
		if (bell == null) {
			source.sendFailure(Component.translatable("kultivator.kampung.uji.lonceng_tidak_ada"));
			return 0;
		}
		Tribute.Ikrar ikrar = Tribute.ikrarDi(bell.blockPosition(), 24.0D);
		if (ikrar == null || !ikrar.owner().equals(player.getUUID())) {
			source.sendFailure(Component.translatable("kultivator.kampung.uji.bukan_ikrar"));
			return 0;
		}
		boolean ok = AnakBerbakat.coba(level, player, KampungAnchors.situs(bell), true);
		if (!ok) {
			source.sendFailure(Component.translatable("kultivator.kampung.uji.anak_gagal"));
		}
		return ok ? 1 : 0;
	}

	private static int daftar(CommandSourceStack source) {
		KampungData data = KampungData.get(source.getServer());
		int[] n = new int[UKURAN.size()];
		for (KampungData.Desa d : data.semua()) {
			int i = UKURAN.indexOf(d.ukuran);
			if (i >= 0) {
				n[i]++;
			}
		}
		source.sendSuccess(() -> Component.translatable("kultivator.kampung.uji.daftar", n[0], n[1], n[2], n[3]).withStyle(ChatFormatting.GOLD), false);
		return n[0] + n[1] + n[2] + n[3];
	}

	private KampungCommands() {
	}
}
