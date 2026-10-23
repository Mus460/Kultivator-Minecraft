package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Paifang;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The cloud gate (Gerbang Awan, DESIGN 37.4.1 T1-T8) on the axis: a red-lacquered one-bay paifang with a single roof at
 * T1 (the wooden gate of a young sect), red-post lanterns either side; the T6 marble paifang of three bays and three roofs
 * stands at the outer rim ({@link #marmer}). Template-local, opening toward +z.
 */
public final class GerbangAwan implements Part {

	private final int z;
	private final boolean marble;
	private final Paifang paifang;

	private GerbangAwan(int z, boolean marble) {
		this.z = z;
		this.marble = marble;
		this.paifang = marble ? new Paifang(0, z, Lv.PLAZA).bays(3).roofs(3).material(Paifang.Material.WHITE_STONE)
				: new Paifang(0, z, Lv.PLAZA).bays(1).roofs(1).material(Paifang.Material.RED_WOOD);
	}

	/** The T1 red wooden gate at z. */
	public static GerbangAwan kayu(int z) {
		return new GerbangAwan(z, false);
	}

	/** The T6 white marble paifang at z. */
	public static GerbangAwan marmer(int z) {
		return new GerbangAwan(z, true);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		return BoundingBox.encapsulating(paifang.bounds(ctx), new BoundingBox(-7, -1, z - 1, 7, 3, z + 1));
	}

	@Override
	public String kind() {
		return "gate";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		paifang.emit(ctx, sink);
		int side = marble ? 10 : 6;
		new LanternPost(marble ? LanternPost.Style.PALACE : LanternPost.Style.RED_POST, Lv.PLAZA).pair(side, z).emit(ctx, sink);
	}

	@Override
	public void decorate(Ctx ctx, Sink sink, int tier) {
		paifang.decorate(ctx, sink, tier);
	}
}
