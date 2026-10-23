package dev.kultivator.sect.build.engine;

import dev.kultivator.Kultivator;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Emission;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.TerrainView;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A compiled plan (DESIGN 21.9 {@code SitePlan.compile}): the template spec, the site rotation, glaze, seed, the stele
 * (origin), the frozen terrain and the frozen zone levels. Ops of a component are a pure function of these, emitted off
 * the server thread ({@link #emitAsync}) from immutable data; the engine maps template-local positions to the world with
 * {@link #world(long)} (rotate, then translate) and states with {@code BlockState.rotate}.
 */
public final class SitePlan {

	private final TemplateSpec spec;
	private final int rotation;
	private final Glaze glaze;
	private final long seed;
	private final BlockPos stele;
	private final TerrainView terrain;
	private final Map<String, Integer> levels;
	private final Ctx ctx;
	private final Rotation vanilla;

	public SitePlan(TemplateSpec spec, int rotation, Glaze glaze, long seed, BlockPos stele, TerrainView terrain, Map<String, Integer> levels) {
		this.spec = spec;
		this.rotation = Math.floorMod(rotation, 4);
		this.glaze = glaze;
		this.seed = seed;
		this.stele = stele.immutable();
		this.terrain = terrain;
		this.levels = Map.copyOf(levels);
		// A sky plan's parts stand on its own islands (DESIGN 37.4.9): the real snapshot only shows through at the stair foot.
		TerrainView view = spec.sky() ? new dev.kultivator.sect.build.blueprint.awan.AwanTerrain(spec.template(), terrain) : terrain;
		this.ctx = new Ctx(spec, this.rotation, glaze, seed, view, this.levels);
		this.vanilla = SectTemplates.rotation(this.rotation);
	}

	public TemplateSpec spec() {
		return spec;
	}

	public Ctx ctx() {
		return ctx;
	}

	public int rotation() {
		return rotation;
	}

	public Glaze glaze() {
		return glaze;
	}

	public BlockPos stele() {
		return stele;
	}

	public Map<String, Integer> levels() {
		return levels;
	}

	/** Emits one component now (any thread; reads only immutable inputs). */
	public Emission emit(Component component) {
		Sink sink = Sink.of(component.id(), spec);
		try {
			component.emit(ctx, sink);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sect blueprint component {} of template {} failed to emit", component.id(), spec.id(), exception);
			return Emission.empty(component.id());
		}
		return sink.finish();
	}

	/** Emits one component on the background executor. */
	public CompletableFuture<Emission> emitAsync(Component component) {
		return CompletableFuture.supplyAsync(() -> emit(component), Util.backgroundExecutor());
	}

	/** The world position of a template-local packed position. */
	public BlockPos world(long local) {
		return world(BlockPos.getX(local), BlockPos.getY(local), BlockPos.getZ(local));
	}

	public BlockPos world(int x, int y, int z) {
		return switch (rotation) {
			case 1 -> new BlockPos(stele.getX() - z, stele.getY() + y, stele.getZ() + x);
			case 2 -> new BlockPos(stele.getX() - x, stele.getY() + y, stele.getZ() - z);
			case 3 -> new BlockPos(stele.getX() + z, stele.getY() + y, stele.getZ() - x);
			default -> new BlockPos(stele.getX() + x, stele.getY() + y, stele.getZ() + z);
		};
	}

	/** The world orientation of a template-local state. */
	public BlockState world(BlockState local) {
		return rotation == 0 ? local : local.rotate(vanilla);
	}

	/** The world box of a template-local box. */
	public BoundingBox worldBox(@Nullable BoundingBox local) {
		if (local == null) {
			return new BoundingBox(stele);
		}
		BlockPos a = world(local.minX(), local.minY(), local.minZ());
		BlockPos b = world(local.maxX(), local.maxY(), local.maxZ());
		return BoundingBox.fromCorners(a, b);
	}

	/** The footprint column in world coordinates. */
	public BoundingBox footprint() {
		return worldBox(spec.column());
	}

	/**
	 * True when the world position lies in the confirmed footprint column, or (a sky plan, DESIGN 37.4.6) in its stair-foot
	 * column from the fitted foot level minus 4 up to the island column's floor.
	 */
	public boolean inFootprint(BlockPos pos) {
		if (footprint().isInside(pos)) {
			return true;
		}
		BoundingBox kaki = kakiBox();
		return kaki != null && kaki.isInside(pos);
	}

	/** The stair-foot column of a sky plan in world coordinates (from the fitted foot level - 4), or null. */
	public @Nullable BoundingBox kakiBox() {
		BoundingBox local = spec.kakiColumn();
		TemplateSpec.Zone zone = spec.kaki();
		if (local == null || zone == null) {
			return null;
		}
		int foot = levels.getOrDefault(zone.id(), zone.design());
		BoundingBox fitted = new BoundingBox(local.minX(), Math.max(local.minY(), foot - 4), local.minZ(), local.maxX(), local.maxY(), local.maxZ());
		return worldBox(fitted);
	}

	/** The template-local packed position of a world position. */
	public long local(BlockPos pos) {
		int dx = pos.getX() - stele.getX();
		int dy = pos.getY() - stele.getY();
		int dz = pos.getZ() - stele.getZ();
		return switch (rotation) {
			case 1 -> BlockPos.asLong(dz, dy, -dx);
			case 2 -> BlockPos.asLong(-dx, dy, -dz);
			case 3 -> BlockPos.asLong(-dz, dy, dx);
			default -> BlockPos.asLong(dx, dy, dz);
		};
	}
}
