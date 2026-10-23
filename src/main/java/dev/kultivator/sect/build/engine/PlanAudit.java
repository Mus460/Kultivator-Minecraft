package dev.kultivator.sect.build.engine;

import dev.kultivator.Kultivator;
import dev.kultivator.sect.build.api.AuditReport;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.MaterialLedger;
import dev.kultivator.sect.build.api.PlanStats;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Costs;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Emission;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.Templates;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dry runs of the blueprints on flat land (DESIGN 21.11, 21.22): the real plan numbers ({@link #stats}, the tier bills)
 * and {@code uji sekte audit}, all off the server thread from the immutable specs. The audit checks every (template, tier)
 * emission for ops outside the footprint column, unknown and banned blocks, gravity blocks, unsupported dependants,
 * uncontained water, stair shapes that differ from vanilla, the anchors (library within 16, formation within 24, tower
 * entrance within 12 and the deepest campfire at most 28 below), the cost invariant of Logam and Permata, and reports the
 * colour budget. This is the robustness net, since no Gradle tests are run.
 */
final class PlanAudit {

	/**
	 * One dry run of a template in a glaze: only the numbers are cached (per-component bills and the stats); the
	 * emissions are kept only for an audit ({@code emissions} empty otherwise), so the cache stays small on a weak laptop.
	 */
	record DryRun(int template, Glaze glaze, Map<String, Emission> emissions, Map<String, Bill> bills, PlanStats stats) {
	}

	private static final Map<String, CompletableFuture<DryRun>> RUNS = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<AuditReport>> AUDITS = new ConcurrentHashMap<>();
	/** Dry runs are queued one after another (the catalog asks for all nine at once): never more than one worker busy. */
	private static CompletableFuture<?> queue = CompletableFuture.completedFuture(null);

	static synchronized void clear() {
		RUNS.clear();
		AUDITS.clear();
		queue = CompletableFuture.completedFuture(null);
	}

	static synchronized CompletableFuture<DryRun> dryRun(int template, Glaze glaze) {
		String key = template + "/" + glaze.id();
		CompletableFuture<DryRun> future = RUNS.get(key);
		if (future == null) {
			future = queue.handleAsync((ignored, error) -> run(template, glaze, false), Util.backgroundExecutor());
			queue = future;
			RUNS.put(key, future);
		}
		return future;
	}

	/** The dry run when ready (kicks it off), else null. */
	static @Nullable DryRun ready(int template, Glaze glaze) {
		CompletableFuture<DryRun> future = dryRun(template, glaze);
		return future.isDone() && !future.isCompletedExceptionally() ? future.join() : null;
	}

	private static DryRun run(int template, Glaze glaze, boolean keepEmissions) {
		TemplateSpec spec = Templates.get(template);
		Ctx ctx = Ctx.dry(spec, glaze);
		Map<String, Emission> emissions = new LinkedHashMap<>();
		Map<String, Bill> bills = new LinkedHashMap<>();
		int[] components = new int[SectTemplates.TIERS];
		long[] blocks = new long[SectTemplates.TIERS];
		long[] ops = new long[SectTemplates.TIERS];
		Bill.Builder[] tierBills = new Bill.Builder[SectTemplates.TIERS];
		for (int i = 0; i < tierBills.length; i++) {
			tierBills[i] = Bill.builder();
		}
		for (Component component : spec.components()) {
			Sink sink = Sink.of(component.id(), spec);
			Emission emission;
			try {
				component.emit(ctx, sink);
				emission = sink.finish();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect blueprint {} / {} failed in a dry run", spec.id(), component.id(), exception);
				emission = Emission.empty(component.id());
			}
			if (keepEmissions) {
				emissions.put(component.id(), emission);
			}
			Bill bill = Costs.bill(emission);
			bills.put(component.id(), bill);
			int t = component.tier() - 1;
			components[t]++;
			ops[t] += emission.size();
			for (BlockState state : emission.states) {
				if (!state.isAir()) {
					blocks[t]++;
				}
			}
			tierBills[t].add(bill);
		}
		Bill[] built = new Bill[SectTemplates.TIERS];
		long total = 0L;
		for (int i = 0; i < built.length; i++) {
			built[i] = tierBills[i].build();
			total += ops[i];
		}
		double rate = BuildEngine.baseRate(template);
		PlanStats stats = new PlanStats(template, spec.width(), spec.depth(), spec.above(), spec.below(), components, blocks, ops, built,
				rate <= 0.0D ? 0.0D : total / rate / 60.0D);
		return new DryRun(template, glaze, emissions, bills, stats);
	}

	static PlanStats stats(int template) {
		DryRun run = ready(template, Glaze.TINTA);
		return run == null ? PlanStats.empty(template) : run.stats();
	}

	static Bill tierBill(int template, int tier, Glaze glaze) {
		DryRun run = ready(template, glaze);
		return run == null ? Bill.EMPTY : run.stats().bill(tier);
	}

	/** The last finished audit of {@code template} (or of all nine), kicking one off when none runs. */
	static AuditReport audit(@Nullable Integer template) {
		String key = template == null ? "all" : String.valueOf(template);
		CompletableFuture<AuditReport> future = AUDITS.get(key);
		if (future != null && future.isDone()) {
			AUDITS.remove(key);
			try {
				return future.join();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect audit failed", exception);
				return new AuditReport(template, 0, 1, List.of(net.minecraft.network.chat.Component.literal(String.valueOf(exception))), true, 0L);
			}
		}
		if (future == null) {
			AUDITS.put(key, CompletableFuture.supplyAsync(() -> runAudit(template), Util.backgroundExecutor()));
		}
		return new AuditReport(template, 0, 0, List.of(net.minecraft.network.chat.Component.translatable("kultivator.blueprint.audit.running")), false, 0L);
	}

	private static AuditReport runAudit(@Nullable Integer template) {
		long start = System.currentTimeMillis();
		List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
		int emissions = 0;
		int violations = 0;
		// Sekte Langit (DESIGN 37.4.12): "all" covers the ground templates 1-9 and the sky templates 11-19.
		for (int t = 1; t <= SectTemplates.SKY_LAST; t++) {
			if (!SectTemplates.validAny(t) || template != null && template != t) {
				continue;
			}
			TemplateSpec spec = Templates.get(t);
			if (spec.isPlaceholder()) {
				lines.add(net.minecraft.network.chat.Component.translatable("kultivator.blueprint.audit.placeholder", SectTemplates.name(t)));
				continue;
			}
			DryRun run = run(t, Glaze.TINTA, true);
			emissions += SectTemplates.TIERS;
			int[] found = check(spec, run, lines);
			violations += found[0];
		}
		String summary = "[Kultivator] Sect audit: " + emissions + " emissions, " + violations + " violations";
		Kultivator.LOGGER.info(summary);
		lines.add(0, net.minecraft.network.chat.Component.translatable("kultivator.blueprint.audit.summary", emissions, violations));
		List<net.minecraft.network.chat.Component> shown = lines.size() > 24 ? new ArrayList<>(lines.subList(0, 24)) : lines;
		return new AuditReport(template, emissions, violations, shown, true, System.currentTimeMillis() - start);
	}

	/** Checks one template's dry run; returns {violations}. */
	private static int[] check(TemplateSpec spec, DryRun run, List<net.minecraft.network.chat.Component> lines) {
		int violations = 0;
		String name = spec.id();
		// A sky plan has no ground under it except the stair foot (the dry context's flat land at the foot's design level).
		Ground ground = Ground.of(spec);
		// The final planned map (later components overwrite earlier ones, as the build does).
		Long2ObjectOpenHashMap<BlockState> map = new Long2ObjectOpenHashMap<>();
		int outside = 0;
		int unknown = 0;
		int banned = 0;
		for (Component component : spec.components()) {
			Emission emission = run.emissions().get(component.id());
			outside += emission.outside;
			for (int i = 0; i < emission.size(); i++) {
				BlockState state = emission.states[i];
				map.put(emission.pos[i], state);
				if (!state.isAir() && !Costs.known(state.getBlock())) {
					unknown++;
					log(name, component.id(), "unknown block " + BuiltInRegistries.BLOCK.getKey(state.getBlock()), emission.pos[i]);
				}
				if (bannedBlock(state) || ground.sky() && dev.kultivator.sect.build.blueprint.awan.AwanPalette.banned(state)) {
					banned++;
					log(name, component.id(), "banned block " + BuiltInRegistries.BLOCK.getKey(state.getBlock()), emission.pos[i]);
				}
			}
		}
		int stairs = 0;
		int unsupported = 0;
		int water = 0;
		long[] colour = new long[4];
		// A waterfall's source (anchor "waterfall*") sits in a notch open on its fall side by design.
		it.unimi.dsi.fastutil.longs.LongOpenHashSet falls = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		for (Emission emission : run.emissions().values()) {
			emission.anchors.forEach((id, at) -> {
				if (id.startsWith("waterfall") || id.startsWith("airterjun")) {
					falls.add(at.asLong());
				}
			});
		}
		for (Long2ObjectMap.Entry<BlockState> entry : map.long2ObjectEntrySet()) {
			long pos = entry.getLongKey();
			BlockState state = entry.getValue();
			if (state.isAir()) {
				continue;
			}
			if (state.getBlock() instanceof StairBlock) {
				StairsShape vanilla = Sink.vanillaShape(state, direction -> map.get(BlockPos.offset(pos, direction)));
				if (vanilla != state.getValue(BlockStateProperties.STAIRS_SHAPE)) {
					stairs++;
				}
			}
			if (!supported(map, pos, state, ground)) {
				unsupported++;
				if (unsupported <= 8) {
					log(name, "-", "unsupported " + BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos);
				}
			}
			if (state.is(Blocks.WATER) && !falls.contains(pos) && !contained(map, pos, ground)) {
				water++;
				if (water <= 8) {
					log(name, "-", "uncontained water", pos);
				}
			}
			colour[colourClass(state)]++;
		}
		// Anchors.
		int anchors = 0;
		Map<String, BlockPos> found = new LinkedHashMap<>();
		for (Emission emission : run.emissions().values()) {
			found.putAll(emission.anchors);
		}
		if (ground.sky()) {
			anchors += skyChecks(spec, name, found, map, lines);
		}
		BlockPos library = found.get("library");
		if (!ground.sky() && library != null && library.distSqr(BlockPos.ZERO) > 16 * 16) {
			anchors++;
			lines.add(line(name, "library", library));
		}
		BlockPos formation = found.get("formation");
		if (!ground.sky() && (formation == null || formation.distSqr(BlockPos.ZERO) > 24 * 24)) {
			anchors++;
			lines.add(line(name, "formation", formation == null ? BlockPos.ZERO : formation));
		}
		BlockPos tower = found.get("tower");
		if (!ground.sky() && (tower == null || tower.getX() * tower.getX() + tower.getZ() * tower.getZ() > 12 * 12)) {
			anchors++;
			lines.add(line(name, "tower", tower == null ? BlockPos.ZERO : tower));
		}
		int deepest = 0;
		for (Long2ObjectMap.Entry<BlockState> entry : map.long2ObjectEntrySet()) {
			if (entry.getValue().is(Blocks.SOUL_CAMPFIRE)) {
				deepest = Math.min(deepest, BlockPos.getY(entry.getLongKey()));
			}
		}
		if (!ground.sky() && deepest < -28) {
			anchors++;
			lines.add(line(name, "tower_depth", new BlockPos(0, deepest, 0)));
		}
		// Cost invariant: valuable categories never pay back more than they cost.
		int invariant = 0;
		MaterialLedger ledger = MaterialLedger.get();
		if (MaterialLedger.registered()) {
			for (BlockState state : new java.util.HashSet<>(map.values())) {
				Costs.Cost cost = Costs.of(state);
				if (cost.cat() == Bahan.LOGAM || cost.cat() == Bahan.PERMATA) {
					long deposit = ledger.depositValueCenti(state.getBlock().asItem());
					Bahan cat = ledger.categoryOf(state.getBlock().asItem());
					if (cat == cost.cat() && deposit > cost.centi()) {
						invariant++;
						log(name, "-", "cost invariant " + BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " deposit " + deposit + " > placed " + cost.centi(), 0L);
					}
				}
			}
		}
		// Façades symmetric about their own axis (DESIGN 21.7): odd bay counts with an even total, never a centre pillar.
		for (Component component : spec.components()) {
			for (dev.kultivator.sect.build.blueprint.Part part : component.parts()) {
				if (part instanceof dev.kultivator.sect.build.blueprint.Hall hall && !hall.symmetricBays()) {
					invariant++;
					log(name, component.id(), "asymmetric bays (even count or odd total)", 0L);
				}
			}
		}
		violations = outside + unknown + banned + stairs + unsupported + water + anchors + invariant;
		long total = Math.max(1L, colour[0] + colour[1] + colour[2] + colour[3]);
		lines.add(net.minecraft.network.chat.Component.translatable("kultivator.blueprint.audit.template", SectTemplates.name(spec.template()), outside, unknown + banned,
				stairs, unsupported, water, anchors + invariant));
		lines.add(net.minecraft.network.chat.Component.translatable("kultivator.blueprint.audit.colour", pct(colour[0], total), pct(colour[1], total),
				pct(colour[2], total), pct(colour[3], total)));
		Kultivator.LOGGER.info("[Kultivator] Sect audit {}: outside {}, unknown {}, banned {}, stair shapes {}, unsupported {}, water {}, anchors {}, invariant {}",
				name, outside, unknown, banned, stairs, unsupported, water, anchors, invariant);
		return new int[]{violations};
	}

	private static String pct(long part, long total) {
		return Math.round(100.0D * part / total) + "%";
	}

	private static net.minecraft.network.chat.Component line(String template, String what, BlockPos pos) {
		return net.minecraft.network.chat.Component.translatable("kultivator.blueprint.audit.anchor", template, what, pos.getX(), pos.getY(), pos.getZ());
	}

	private static void log(String template, String component, String what, long pos) {
		Kultivator.LOGGER.warn("[Kultivator] Sect audit {} / {}: {} at {} {} {}", template, component, what, BlockPos.getX(pos), BlockPos.getY(pos),
				BlockPos.getZ(pos));
	}

	/** Blocks never used in builds (DESIGN 21.5 "Banned in builds"). */
	static boolean bannedBlock(BlockState state) {
		Block block = state.getBlock();
		return block instanceof FallingBlock && !state.is(Blocks.SCAFFOLDING) || state.is(BlockTags.FIRE) || state.is(Blocks.SPAWNER)
				|| state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN)
				|| state.is(Blocks.SUGAR_CANE) || state.is(Blocks.KELP) || state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN)
				|| state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.REDSTONE_BLOCK) || state.is(BlockTags.LIGHTNING_RODS);
	}

	/**
	 * Where a dry plan has ground of its own: flat land below y = -1 for a ground template; for a sky template only the
	 * stair-foot square at and below its design level - 1 (the dry context's {@code AwanTerrain}), elsewhere open sky.
	 */
	private record Ground(boolean sky, @Nullable BoundingBox kaki, int kakiTop) {
		static Ground of(TemplateSpec spec) {
			if (!spec.sky()) {
				return new Ground(false, null, -1);
			}
			TemplateSpec.Zone zone = spec.kaki();
			return new Ground(true, spec.kakiColumn(), zone == null ? Integer.MIN_VALUE : zone.design() - 1);
		}

		/** True when an unplanned cell at {@code pos} is ground. */
		boolean solid(long pos) {
			int y = BlockPos.getY(pos);
			if (!sky) {
				return y < 0;
			}
			if (kaki == null) {
				return false;
			}
			int x = BlockPos.getX(pos);
			int z = BlockPos.getZ(pos);
			return x >= kaki.minX() && x <= kaki.maxX() && z >= kaki.minZ() && z <= kaki.maxZ() && y <= kakiTop;
		}
	}

	/** The anchors every sky template must emit (read by 37B's flow, 37M's ferry and 37V's arrival). */
	static final String[] SKY_ANCHORS = {"tahta", "arrival", "tangga_kaki", "tangga_puncak", "feri_atas", "feri_bawah"};

	/**
	 * The sky checks of DESIGN 37.4.12: the anchors the sky modules read, the throne at the origin, and nothing planned
	 * under the island's floor ({@code skyFloor}) outside the stair-foot column. Returns the problems found.
	 */
	private static int skyChecks(TemplateSpec spec, String name, Map<String, BlockPos> found, Long2ObjectOpenHashMap<BlockState> map,
			List<net.minecraft.network.chat.Component> lines) {
		int problems = 0;
		for (String id : SKY_ANCHORS) {
			if (!found.containsKey(id)) {
				problems++;
				lines.add(line(name, id, BlockPos.ZERO));
			}
		}
		BlockPos tahta = found.get("tahta");
		if (tahta != null && (Math.abs(tahta.getX()) > 2 || Math.abs(tahta.getZ()) > 2)) {
			problems++;
			lines.add(line(name, "tahta", tahta));
		}
		int floor = spec.skyFloor();
		BoundingBox kaki = spec.kakiColumn();
		int below = 0;
		for (Long2ObjectMap.Entry<BlockState> entry : map.long2ObjectEntrySet()) {
			long pos = entry.getLongKey();
			if (entry.getValue().isAir() || BlockPos.getY(pos) >= floor) {
				continue;
			}
			int x = BlockPos.getX(pos);
			int z = BlockPos.getZ(pos);
			if (kaki != null && x >= kaki.minX() && x <= kaki.maxX() && z >= kaki.minZ() && z <= kaki.maxZ()) {
				continue;
			}
			below++;
			if (below <= 4) {
				log(name, "-", "below the sky floor " + floor, pos);
			}
		}
		if (below > 0) {
			problems++;
			lines.add(line(name, "sky_floor", new BlockPos(0, floor, 0)));
		}
		return problems;
	}

	/** Blocks that need support have it in the plan (see {@link Ground}). */
	private static boolean supported(Long2ObjectOpenHashMap<BlockState> map, long pos, BlockState state, Ground ground) {
		// Leaves (cherry and flowering azalea leaves are also #flowers) and wall banners (also #banners) hang free.
		if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof WallBannerBlock) {
			return true;
		}
		if (state.hasProperty(BlockStateProperties.HANGING)) {
			boolean hanging = state.getValue(BlockStateProperties.HANGING);
			return solidAt(map, BlockPos.offset(pos, hanging ? Direction.UP : Direction.DOWN), ground);
		}
		if (state.is(BlockTags.CANDLES) || state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)
				|| state.is(Blocks.PINK_PETALS) || state.is(Blocks.LEAF_LITTER) || state.is(BlockTags.BANNERS) || state.is(Blocks.BAMBOO)
				|| state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.MOSS_CARPET) || state.is(BlockTags.FLOWER_POTS)) {
			return solidAt(map, BlockPos.offset(pos, Direction.DOWN), ground);
		}
		return true;
	}

	private static boolean solidAt(Long2ObjectOpenHashMap<BlockState> map, long pos, Ground ground) {
		BlockState below = map.get(pos);
		if (below == null) {
			return ground.solid(pos);
		}
		return !below.isAir();
	}

	/** Every horizontal neighbour and the cell below a water cell is water or a planned solid (flat land below -1). */
	private static boolean contained(Long2ObjectOpenHashMap<BlockState> map, long pos, Ground ground) {
		for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
			long next = BlockPos.offset(pos, direction);
			BlockState state = map.get(next);
			if (state == null) {
				if (!ground.solid(next)) {
					return false;
				}
				continue;
			}
			if (state.isAir()) {
				return false;
			}
		}
		return true;
	}

	/** 0 neutral (stone, roof grey, white), 1 red, 2 teal and jade, 3 gold; plants count as neutral (excluded upstream). */
	private static int colourClass(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.contains("gold") || path.contains("sulfur") || path.contains("yellow") || path.contains("gilded") || state.is(Blocks.BELL)) {
			return 3;
		}
		if (path.contains("copper") || path.contains("warped") || path.contains("prismarine") || path.contains("cyan")) {
			return 2;
		}
		if (path.contains("cinnabar") || path.contains("mangrove") || path.contains("red_") || path.contains("crimson")) {
			return 1;
		}
		return 0;
	}

	private PlanAudit() {
	}
}
