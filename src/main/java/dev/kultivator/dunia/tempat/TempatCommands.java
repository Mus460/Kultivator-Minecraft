package dev.kultivator.dunia.tempat;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.tempat.jalan.RencanaJalan;
import dev.kultivator.dunia.tempat.worldgen.CincinPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /kultivasi uji tempat} (DESIGN 38.D.10; op, through {@code DuniaCommands.test}):
 * <ul>
 *   <li>{@code rencana}: every registered plan, its candidates and the final verdict of each (suitable with
 *       {@code dasarY}, or the reason);</li>
 *   <li>{@code zona}: protected / safe ground / inside a place box at the caller;</li>
 *   <li>{@code cek}: the place at the caller (kind, site, district, strength), its loaded anchors by kind (warns over 40
 *       per district), its life points by type from the loaded pieces;</li>
 *   <li>{@code kepadatan [radius]}: 38 places per km2 inside the radius from the plans and placements alone (no chunk is
 *       made; biome checks of jigsaw places are not applied, so the counts are upper bounds), road km per km2 and posts,
 *       with the "full but not crowded" check (<= 6 surface places per km2 in R1-R2, themed cities weigh 4);</li>
 *   <li>{@code ukur}: time of the 38 placement checks over 200 chunks around the caller, of the road cells and of the
 *       verdicts, plus live counts (anchors, 38 bodies);</li>
 *   <li>{@code lihat <folder/potongan>}: the template's ghost preview plus its life points by type.</li>
 * </ul>
 * 38PR adds its repair sub-commands through its own node.
 */
public final class TempatCommands {

	/** From {@code TempatSpine.init()}. */
	static void init() {
		DuniaCommands.test("tempat", TempatCommands::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("tempat")
				.then(Commands.literal("rencana").executes(ctx -> rencana(ctx.getSource())))
				.then(Commands.literal("zona").executes(ctx -> zona(ctx.getSource())))
				.then(Commands.literal("cek").executes(ctx -> cek(ctx.getSource())))
				.then(Commands.literal("kepadatan").executes(ctx -> kepadatan(ctx.getSource(), 2000))
						.then(Commands.argument("radius", IntegerArgumentType.integer(500, 12000))
								.executes(ctx -> kepadatan(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
				.then(Commands.literal("ukur").executes(ctx -> ukur(ctx.getSource())))
				.then(Commands.literal("lihat").then(Commands.argument("potongan", StringArgumentType.greedyString())
						.suggests(DuniaCommands.TEMPLATES)
						.executes(ctx -> lihat(ctx.getSource(), StringArgumentType.getString(ctx, "potongan"),
								DuniaCommands.lihat(ctx, StringArgumentType.getString(ctx, "potongan"))))));
	}

	private static int rencana(CommandSourceStack source) {
		ServerLevel overworld = source.getServer().overworld();
		long seed = overworld.getSeed();
		PutusanTanah.Sumber sumber = TempatZones.sumber(overworld);
		int shown = 0;
		for (String id : RencanaRegistry.ids()) {
			Rencana plan = RencanaRegistry.get(id);
			List<Kandidat> candidates = plan.kandidat(seed);
			source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.rencana", id, candidates.size(), plan.semuaDistrik(seed).size()), false);
			int listed = 0;
			for (Kandidat k : candidates) {
				Distrik first = null;
				for (Distrik d : plan.semuaDistrik(seed)) {
					if (d.kandidat().equals(k)) {
						first = d;
						break;
					}
				}
				Putusan p = first == null ? Putusan.TIDAK : plan.putusan(sumber, first);
				int dist = (int) Math.round(Math.hypot(k.pusatX(), k.pusatZ()));
				if (!k.pertama() && dist > 9000 && !p.layak()) {
					continue;
				}
				if (++listed > 40) {
					break;
				}
				source.sendSuccess(() -> Component.translatable(p.layak() ? "kultivator.tempat.uji.kandidat_layak" : "kultivator.tempat.uji.kandidat_tidak",
						k.index(), k.pusatX(), k.pusatZ(), p.layak() ? Integer.toString(p.dasarY()) : p.alasan()), false);
			}
			shown++;
		}
		if (shown == 0) {
			source.sendFailure(Component.translatable("kultivator.tempat.uji.tanpa_rencana"));
		}
		return shown;
	}

	private static int zona(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		boolean protects = TempatZones.protects(level, pos);
		boolean safe = TempatZones.safe(level, pos);
		boolean inside = TempatZones.inside(level, pos.getX(), pos.getZ());
		boolean overworld = level.dimension() == Level.OVERWORLD;
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.zona", pos.getX(), pos.getZ(),
				yaTidak(protects), yaTidak(safe), yaTidak(inside), yaTidak(overworld)), false);
		return 1;
	}

	private static int cek(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		TempatInfo info = TempatApi.tempatDi(level, pos);
		if (info == null) {
			source.sendFailure(Component.translatable("kultivator.tempat.uji.cek_kosong", pos.getX(), pos.getZ()));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.cek", info.jenis(), Long.toHexString(info.situsId()),
				info.distrik() == null ? "-" : info.distrik(), info.kekuatan(), info.kotak().getXSpan(), info.kotak().getZSpan()), false);
		Map<String, Integer> anchors = new TreeMap<>();
		int inDistrict = 0;
		for (String prefix : List.of("kota/", "kotabesar/", "kampung/", "kompleks/", "pos/", "kota_tema/", "gua/", "situs/")) {
			for (DuniaAnchorEntity a : DuniaAnchors.loaded(level, prefix)) {
				if (info.kotak().isInside(a.blockPosition().getX(), info.kotak().minY() + 1, a.blockPosition().getZ())) {
					anchors.merge(a.kind(), 1, Integer::sum);
					if (a.distanceToSqr(source.getPosition()) < 80.0D * 80.0D) {
						inDistrict++;
					}
				}
			}
		}
		anchors.forEach((kind, n) -> source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.cek_jangkar", kind, n), false));
		int near = inDistrict;
		if (near > 40) {
			source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.cek_jangkar_lebih", near).withStyle(ChatFormatting.RED), false);
		}
		Map<String, Integer> points = new TreeMap<>();
		for (Titik t : TempatApi.titik(level, info.situsId(), "")) {
			points.merge(t.jenis(), 1, Integer::sum);
		}
		int pieces = TempatApi.potongan(level, info.situsId()).size();
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.cek_titik", pieces, points.values().stream().mapToInt(i -> i).sum()), false);
		points.forEach((kind, n) -> source.sendSuccess(() -> Component.literal("  " + kind + " x" + n).withStyle(ChatFormatting.GRAY), false));
		return 1;
	}

	private static int kepadatan(CommandSourceStack source, int radius) {
		ServerLevel level = source.getServer().overworld();
		long seed = level.getSeed();
		BlockPos at = BlockPos.containing(source.getPosition());
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		double km2 = Math.PI * radius * radius / 1_000_000.0D;
		Map<String, Integer> counts = new TreeMap<>();
		double weighted = 0.0D;
		// planned places (capital, compounds, themed cities): final verdicts
		for (String id : RencanaRegistry.ids()) {
			Rencana plan = RencanaRegistry.get(id);
			for (Kandidat k : plan.kandidat(seed)) {
				if (Math.hypot(k.pusatX() - at.getX(), k.pusatZ() - at.getZ()) > radius) {
					continue;
				}
				Distrik first = null;
				for (Distrik d : plan.semuaDistrik(seed)) {
					if (d.kandidat().equals(k)) {
						first = d;
						break;
					}
				}
				if (first != null && plan.putusan(sumber, first).layak()) {
					counts.merge(TempatApi.jenisRencana(id), 1, Integer::sum);
					weighted += id.startsWith("kota_tema_") ? 4.0D : 6.0D;
				}
			}
		}
		// spread sets of 38 (villages, caves, sites, ...): placement only (upper bound, no biome check)
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		int rc = radius >> 4;
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			if (!(set.value().placement() instanceof CincinPlacement placement)) {
				continue;
			}
			String name = set.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
			int n = 0;
			int sp = placement.spacing();
			int cx0 = at.getX() >> 4;
			int cz0 = at.getZ() >> 4;
			for (int gx = Math.floorDiv(cx0 - rc, sp); gx <= Math.floorDiv(cx0 + rc, sp); gx++) {
				for (int gz = Math.floorDiv(cz0 - rc, sp); gz <= Math.floorDiv(cz0 + rc, sp); gz++) {
					ChunkPos c = placement.getPotentialStructureChunk(seed, gx * sp, gz * sp);
					if (Math.hypot(c.x() - cx0, c.z() - cz0) <= rc && placement.isStructureChunk(state, c.x(), c.z())) {
						n++;
					}
				}
			}
			if (n > 0) {
				counts.put(name, n);
				if (set.is(CincinPlacement.BERPENGHUNI)) {
					weighted += n;
				}
			}
		}
		// roads and posts
		double roadKm = 0.0D;
		Map<String, Integer> posts = new TreeMap<>();
		int selX = Math.floorDiv(at.getX(), RencanaJalan.SEL);
		int selZ = Math.floorDiv(at.getZ(), RencanaJalan.SEL);
		int cells = radius / RencanaJalan.SEL + 1;
		for (int dx = -cells; dx <= cells; dx++) {
			for (int dz = -cells; dz <= cells; dz++) {
				for (RencanaJalan.Sisi e : RencanaJalan.sisiSel(state, selX + dx, selZ + dz)) {
					int[] mid = e.titikPada(e.panjang() / 2.0D);
					if (Math.hypot(mid[0] - at.getX(), mid[1] - at.getZ()) <= radius) {
						roadKm += e.panjang() / 1000.0D;
					}
				}
				for (RencanaJalan.Pos p : RencanaJalan.posSel(state, selX + dx, selZ + dz)) {
					if (Math.hypot(p.x() - at.getX(), p.z() - at.getZ()) <= radius) {
						posts.merge(p.jenis(), 1, Integer::sum);
						if (p.jenis().startsWith("singgah") || p.jenis().equals("dagang")) {
							weighted += 1.0D;
						}
					}
				}
			}
		}
		double area = km2;
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.kepadatan", radius, String.format(java.util.Locale.ROOT, "%.1f", area)), false);
		counts.forEach((name, n) -> source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "  %s: %d (%.2f/km2)", name, n, n / area))
				.withStyle(ChatFormatting.GRAY), false));
		posts.forEach((name, n) -> source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "  pos/%s: %d (%.2f/km2)", name, n, n / area))
				.withStyle(ChatFormatting.GRAY), false));
		double km = roadKm;
		double perKm2 = weighted / area;
		boolean ok = perKm2 <= 6.0D;
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.kepadatan_jalan", String.format(java.util.Locale.ROOT, "%.1f", km),
				String.format(java.util.Locale.ROOT, "%.2f", km / area)), false);
		source.sendSuccess(() -> Component.translatable(ok ? "kultivator.tempat.uji.kepadatan_ok" : "kultivator.tempat.uji.kepadatan_sesak",
				String.format(java.util.Locale.ROOT, "%.2f", perKm2)).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		return counts.size();
	}

	private static int ukur(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		long seed = level.getSeed();
		BlockPos at = BlockPos.containing(source.getPosition());
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		int cx0 = at.getX() >> 4;
		int cz0 = at.getZ() >> 4;
		long t0 = System.nanoTime();
		int sets = 0;
		int hits = 0;
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			boolean ours = set.unwrapKey().map(k -> k.identifier().getNamespace().equals("kultivator")).orElse(false)
					&& set.value().placement() instanceof RandomSpreadStructurePlacement;
			if (!ours) {
				continue;
			}
			sets++;
			for (int i = 0; i < 200; i++) {
				int x = cx0 + (i % 15) - 7;
				int z = cz0 + (i / 15) - 7;
				if (set.value().placement().isStructureChunk(state, x, z)) {
					hits++;
				}
			}
		}
		long t1 = System.nanoTime();
		RencanaJalan.lupakan();
		int edges = RencanaJalan.sisiSel(state, Math.floorDiv(at.getX(), RencanaJalan.SEL), Math.floorDiv(at.getZ(), RencanaJalan.SEL)).size();
		long t2 = System.nanoTime();
		PutusanTanah.lupakan();
		RencanaKotaBesar.lupakan();
		int candidates = RencanaKotaBesar.of(seed).kandidat().size();
		long t3 = System.nanoTime();
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		int verdicts = 0;
		for (Kandidat k : RencanaKotaBesar.of(seed).kandidat()) {
			if (k.pertama()) {
				RencanaKotaBesar.akhir(sumber, k);
				verdicts++;
			}
		}
		long t4 = System.nanoTime();
		double perChunk = sets == 0 ? 0.0D : (t1 - t0) / 1_000_000.0D / 200.0D;
		int s = sets;
		int h = hits;
		int v = verdicts;
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.ukur_placement", s, String.format(java.util.Locale.ROOT, "%.3f", perChunk), h), false);
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.ukur_rencana",
				String.format(java.util.Locale.ROOT, "%.1f", (t2 - t1) / 1_000_000.0D), edges,
				String.format(java.util.Locale.ROOT, "%.1f", (t3 - t2) / 1_000_000.0D), candidates,
				String.format(java.util.Locale.ROOT, "%.1f", (t4 - t3) / 1_000_000.0D), v), false);
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.ukur_hidup", DuniaAnchors.count(),
				DuniaBudget.count(DuniaBudget.NPC), TempatZones.semuaDinamis().size()), false);
		return 1;
	}

	private static int lihat(CommandSourceStack source, String path, int ghost) {
		if (ghost == 0) {
			return 0;
		}
		Map<String, Integer> points = new TreeMap<>();
		for (TitikTemplat.Lokal p : TitikTemplat.of(source.getServer(), Identifier.fromNamespaceAndPath("kultivator", path))) {
			points.merge(p.jenis(), 1, Integer::sum);
		}
		Map<String, String> sides = TitikTemplat.sisi(source.getServer(), Identifier.fromNamespaceAndPath("kultivator", path));
		source.sendSuccess(() -> Component.translatable("kultivator.tempat.uji.lihat", path, points.values().stream().mapToInt(i -> i).sum(),
				sides.isEmpty() ? "-" : sides.toString()), false);
		points.forEach((kind, n) -> source.sendSuccess(() -> Component.literal("  " + kind + " x" + n).withStyle(ChatFormatting.GRAY), false));
		return 1;
	}

	private static Component yaTidak(boolean b) {
		return Component.translatable(b ? "kultivator.tempat.uji.ya" : "kultivator.tempat.uji.tidak");
	}

	private TempatCommands() {
	}
}
