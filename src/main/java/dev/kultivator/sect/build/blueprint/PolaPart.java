package dev.kultivator.sect.build.blueprint;

import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Slot;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The 6b pattern component of a Titik Formasi (DESIGN 24.6.2 B, kind {@code FORMASI_PATTERN}): the cells of the pattern of a
 * formation spec around the plot's anchor ({@code Pattern.slots(rot)}, the same cells {@code Pattern.emit(rot)} lists),
 * planned in template-local coordinates like any other part so the engine bills them, places them (BuildPlacer, inside the
 * confirmed footprint only) and repairs them.
 * <p>
 * What it leaves out, so the pattern is placed <b>inactive</b> and nothing risky is built: the Mata Formasi itself and any
 * exact-item socket (beacon, diamond or netherite heart: the owner sets those; the socket stays empty and the tier still
 * completes, as at the gate altars), fluid slots (a water source would flow), and the blocks no sect build may place
 * (falling blocks, fire, spawners, chests, redstone, lightning rods...). Slots under the eye ({@code dy -1}) are PLINTH
 * cells so they replace the plot's own paving; every other slot is a FRAME cell. {@code rot} is the local rotation
 * (world rotation minus the site rotation), so the placed pattern turns out exactly as the hologram showed it.
 */
public final class PolaPart implements Part {

	private final FormasiTitik.Plot plot;
	private final String spec;
	private final int rot;

	public PolaPart(FormasiTitik.Plot plot, String spec, int rot) {
		this.plot = plot;
		this.spec = spec;
		this.rot = Math.floorMod(rot, 4);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(plot.level());
		int r = plot.radius();
		return new BoundingBox(plot.x() - r, y - 1, plot.z() - r, plot.x() + r, y + 5, plot.z() + r);
	}

	@Override
	public String kind() {
		return "formasi_pola";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		FormationSpec formation = FormationRegistry.byId(spec);
		if (formation == null) {
			return;
		}
		int y = ctx.level(plot.level());
		for (Slot slot : formation.pattern().slots(rot)) {
			BlockState state = placeable(slot, rot);
			if (state != null) {
				sink.phase(slot.dy() < 0 ? Phase.PLINTH : Phase.FRAME);
				sink.set(plot.x() + slot.dx(), y + slot.dy(), plot.z() + slot.dz(), Palette.dry(state));
			}
		}
	}

	/** How many cells the pattern of {@code formation} turned {@code rot} plans (the audit checks the emission against it). */
	public static int placeable(FormationSpec formation, int rot) {
		int count = 0;
		for (Slot slot : formation.pattern().slots(rot)) {
			if (placeable(slot, rot) != null) {
				count++;
			}
		}
		return count;
	}

	/** The state a slot plans, or null when it stays an empty socket (exact item), is air or may never be placed. */
	private static BlockState placeable(Slot slot, int rot) {
		if (slot.rule().exactItem() != null) {
			return null;
		}
		BlockState state = slot.rule().canonical(rot);
		return state == null || state.isAir() || skipped(state) ? null : state;
	}

	/** True for blocks a sect build never places ({@code PlanAudit.bannedBlock}) and for fluids. */
	static boolean skipped(BlockState state) {
		return state.getBlock() instanceof LiquidBlock
				|| state.getBlock() instanceof FallingBlock && !state.is(Blocks.SCAFFOLDING) || state.is(BlockTags.FIRE) || state.is(Blocks.SPAWNER)
				|| state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN)
				|| state.is(Blocks.SUGAR_CANE) || state.is(Blocks.KELP) || state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN)
				|| state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.REDSTONE_BLOCK) || state.is(BlockTags.LIGHTNING_RODS);
	}
}
