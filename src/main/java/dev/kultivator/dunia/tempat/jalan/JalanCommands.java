package dev.kultivator.dunia.tempat.jalan;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.TempatZones;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;

/**
 * {@code /kultivasi uji jalan} (DESIGN 38.D.10; op, through {@code DuniaCommands.test}): {@code cari} (teleport onto
 * the nearest born road), {@code peta} (the edges of the 3 x 3 road cells around the caller: class, length, ends,
 * born or not), {@code cek} (the nearest edge: both end places' checks and where its first bridge or ferry is),
 * {@code pos <jenis>} (teleport to the nearest planned road-side post of that kind), {@code papan} (send the carved board
 * of the nearest milestone within 32 blocks at once, also to a creative tester). Planning only reads seed data and
 * generator noise; no chunk is generated except by the teleport itself.
 */
final class JalanCommands {

	static void init() {
		DuniaCommands.test("jalan", JalanCommands::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("jalan")
				.then(Commands.literal("cari").executes(ctx -> cari(ctx.getSource())))
				.then(Commands.literal("peta").executes(ctx -> peta(ctx.getSource())))
				.then(Commands.literal("cek").executes(ctx -> cek(ctx.getSource())))
				.then(Commands.literal("papan").executes(ctx -> papan(ctx.getSource())))
				.then(Commands.literal("pos").then(Commands.argument("jenis", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(RencanaJalan.JENIS_POS, b))
						.executes(ctx -> pos(ctx.getSource(), StringArgumentType.getString(ctx, "jenis")))));
	}

	private static int cari(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		ServerLevel level = source.getServer().overworld();
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		BlockPos at = player.blockPosition();
		RencanaJalan.Sisi best = null;
		double bestD = Double.MAX_VALUE;
		int selX = Math.floorDiv(at.getX(), RencanaJalan.SEL);
		int selZ = Math.floorDiv(at.getZ(), RencanaJalan.SEL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (RencanaJalan.Sisi e : RencanaJalan.sisiSel(state, selX + dx, selZ + dz)) {
					double d = e.jarak2(at.getX(), at.getZ());
					if (d < bestD && RencanaJalan.sah(sumber, state, e)) {
						bestD = d;
						best = e;
					}
				}
			}
		}
		if (best == null) {
			source.sendFailure(Component.translatable("kultivator.jalan.uji.tidak_ada"));
			return 0;
		}
		double s = Tonggak.posisi(best, at.getX(), at.getZ());
		int[] p = best.titikPada(s);
		teleport(player, level, p[0], p[1]);
		RencanaJalan.Sisi e = best;
		source.sendSuccess(() -> Component.translatable("kultivator.jalan.uji.cari", p[0], p[1],
				Component.translatable("kultivator.jalan.kelas." + e.kelas()), Math.round(e.panjang())), false);
		return 1;
	}

	private static int peta(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		BlockPos at = BlockPos.containing(source.getPosition());
		int selX = Math.floorDiv(at.getX(), RencanaJalan.SEL);
		int selZ = Math.floorDiv(at.getZ(), RencanaJalan.SEL);
		int total = 0;
		int posts = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (RencanaJalan.Sisi e : RencanaJalan.sisiSel(state, selX + dx, selZ + dz)) {
					boolean ok = RencanaJalan.sah(sumber, state, e);
					source.sendSuccess(() -> Component.translatable(ok ? "kultivator.jalan.uji.sisi_lahir" : "kultivator.jalan.uji.sisi_tidak",
							Component.translatable("kultivator.jalan.kelas." + e.kelas()), Math.round(e.panjang()),
							Tonggak.namaUjung(level, e.dari()), e.dari().x(), e.dari().z(),
							Tonggak.namaUjung(level, e.ke()), e.ke().x(), e.ke().z()), false);
					total++;
				}
				posts += RencanaJalan.posSel(state, selX + dx, selZ + dz).size();
			}
		}
		int t = total;
		int p = posts;
		source.sendSuccess(() -> Component.translatable("kultivator.jalan.uji.peta", t, p), false);
		return total;
	}

	private static int cek(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		BlockPos at = BlockPos.containing(source.getPosition());
		RencanaJalan.Sisi e = RencanaJalan.terdekat(state, at.getX(), at.getZ(), 512);
		if (e == null) {
			source.sendFailure(Component.translatable("kultivator.jalan.uji.tidak_ada"));
			return 0;
		}
		boolean ok = RencanaJalan.sah(sumber, state, e);
		int n = (int) (e.panjang() / 3.0D);
		int water = -1;
		int run = 0;
		for (int k = 0; k <= n; k++) {
			int[] p = e.titikPada(k * 3.0D);
			if (sumber.surface(p[0], p[1]) > sumber.floor(p[0], p[1])) {
				if (water < 0) {
					water = k;
				}
				run++;
			} else if (water >= 0) {
				break;
			}
		}
		int[] w = water < 0 ? null : e.titikPada(water * 3.0D);
		int runBlocks = run * 3;
		source.sendSuccess(() -> Component.translatable("kultivator.jalan.uji.cek", Tonggak.namaUjung(level, e.dari()),
				Tonggak.namaUjung(level, e.ke()), Component.translatable(ok ? "kultivator.tempat.uji.ya" : "kultivator.tempat.uji.tidak"),
				Math.round(e.panjang())), false);
		if (w != null) {
			source.sendSuccess(() -> Component.translatable(runBlocks <= 28 ? "kultivator.jalan.uji.jembatan" : "kultivator.jalan.uji.feri",
					w[0], w[1], runBlocks), false);
		}
		return 1;
	}

	private static int pos(CommandSourceStack source, String jenis) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		ServerLevel level = source.getServer().overworld();
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		BlockPos at = player.blockPosition();
		int selX = Math.floorDiv(at.getX(), RencanaJalan.SEL);
		int selZ = Math.floorDiv(at.getZ(), RencanaJalan.SEL);
		RencanaJalan.Pos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (RencanaJalan.Pos p : RencanaJalan.posSel(state, selX + dx, selZ + dz)) {
					double d = Math.hypot(p.x() - at.getX(), p.z() - at.getZ());
					if (p.jenis().equals(jenis) && d < bestD) {
						bestD = d;
						best = p;
					}
				}
			}
		}
		if (best == null) {
			source.sendFailure(Component.translatable("kultivator.jalan.uji.pos_tidak", jenis));
			return 0;
		}
		RencanaJalan.Pos p = best;
		teleport(player, level, p.x(), p.z());
		source.sendSuccess(() -> Component.translatable("kultivator.jalan.uji.pos", jenis, p.x(), p.z(), p.jarak()), false);
		return 1;
	}

	private static int papan(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		DuniaAnchorEntity best = null;
		for (DuniaAnchorEntity anchor : level.getEntitiesOfClass(DuniaAnchorEntity.class, player.getBoundingBox().inflate(32.0D))) {
			if ("pos/tonggak".equals(anchor.kind()) && (best == null || anchor.distanceToSqr(player) < best.distanceToSqr(player))) {
				best = anchor;
			}
		}
		if (best == null) {
			source.sendFailure(Component.translatable("kultivator.jalan.uji.papan_tidak"));
			return 0;
		}
		PapanTonggak.kirimSekarang(level, best, player);
		BlockPos at = best.blockPosition();
		source.sendSuccess(() -> Component.translatable("kultivator.jalan.uji.papan", at.getX(), at.getY(), at.getZ()), false);
		return 1;
	}

	private static void teleport(ServerPlayer player, ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		player.teleportTo(level, x + 0.5D, y + 1.0D, z + 0.5D, Set.of(), player.getYRot(), player.getXRot(), true);
	}

	private JalanCommands() {
	}
}
