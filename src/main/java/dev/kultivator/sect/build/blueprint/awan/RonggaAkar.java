package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * The Root Hollow (Rongga Akar, DESIGN 37.4.1): the spirit-vein mine of a sky island, built as DESIGN 36 W's component
 * {@code sx_kebun_tambang} with the very anchors module W reads ({@code tambang_mulut} in front of the adit,
 * {@code tambang_dalam_1} in the adit, {@code tambang_dalam_2..4} in the hidden cavity behind it): a mossy rock outcrop 5
 * wide and 4 deep with a timber adit, plank floor, lanterns, raw-copper veins and an ore cart, the same set piece as on the
 * ground (module W's char grid, rebuilt here because sky templates never receive the ground extensions), with a hidden
 * froglight in the cavity floor so its darkness never breeds monsters.
 */
public final class RonggaAkar implements Part {

	/** Layers from y-1 (the floor) up; rows from the back (z -3) to the front (z 0); columns x -2..2 (module W's grid). */
	private static final String[][] LAYERS = {
			{"SSSSS", "SpHpS", "SSpSS", "SSpSS"},
			{"RKKKR", "R___R", "RRbRR", "MIbIC"},
			{"RKKKR", "R___R", "VR_RR", "RI_IO"},
			{"RRRRR", "RRRRR", "RRRRV", "L===L"},
			{"rRRRr", "RRgRR", "rRRRr", "sssss"},
			{" rRr ", "rRRRr", " rgr ", "     "},
			{"  g  ", " rRr ", "     ", "     "},
			{"     ", "  f  ", "     ", "     "}};

	private final int x;
	private final int z;
	private final Direction front;
	private final Lv level;

	public RonggaAkar(int x, int z, Direction front, Lv level) {
		this.x = x;
		this.z = z;
		this.front = front;
		this.level = level;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return Part.box(x, z, front, -2, y - 1, -3, 2, y + LAYERS.length - 2, 0);
	}

	@Override
	public String kind() {
		return "mine_mouth";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		Random random = ctx.random("sx_kebun_tambang");
		sink.pushFacing(x, y, z, front);
		Phase previous = sink.phase();
		for (int l = 0; l < LAYERS.length; l++) {
			String[] rows = LAYERS[l];
			int fy = l - 1;
			for (int r = 0; r < rows.length; r++) {
				String row = rows[r];
				int fz = r - 3;
				for (int c = 0; c < row.length(); c++) {
					int fx = c - 2;
					char key = row.charAt(c);
					if (key == ' ') {
						continue;
					}
					if (key == '_') {
						sink.phase(Phase.FRAME);
						sink.air(fx, fy, fz);
						continue;
					}
					BlockState state = state(key, random);
					if (state == null) {
						continue;
					}
					sink.phase(phase(key));
					sink.set(fx, fy, fz, state);
				}
			}
		}
		sink.phase(previous);
		sink.anchor("tambang_mulut", 0, 0, 1);
		sink.anchor("tambang_dalam_1", 0, 0, -1);
		sink.anchor("tambang_dalam_2", 0, 0, -2);
		sink.anchor("tambang_dalam_3", -1, 0, -2);
		sink.anchor("tambang_dalam_4", 1, 0, -2);
		sink.pop();
	}

	private static Phase phase(char key) {
		return switch (key) {
			case 'S', 'p', 'H' -> Phase.PLINTH;
			case 'I', '=', 'b' -> Phase.FRAME;
			case 's' -> Phase.ROOF;
			case 'L', 'O' -> Phase.ORNAMENT;
			case 'g', 'f' -> Phase.GARDEN;
			default -> Phase.WALL;
		};
	}

	private static @Nullable BlockState state(char key, Random random) {
		return switch (key) {
			case 'S' -> Palette.st(Blocks.COBBLED_DEEPSLATE);
			case 'p' -> Palette.st(Blocks.SPRUCE_PLANKS);
			case 'H' -> Palette.st(Palette.HIDDEN_LIGHT);
			case 'I' -> Palette.axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
			case '=' -> Palette.axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
			case 'K' -> Palette.st(Palette.concrete(DyeColor.BLACK));
			case 'b' -> Palette.trapdoorFlat(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, false);
			case 'M' -> Palette.st(Blocks.MOSSY_COBBLESTONE);
			case 'C' -> Palette.barrel(Direction.UP);
			case 'O', 'V' -> Palette.st(Blocks.RAW_COPPER_BLOCK);
			case 'L' -> Palette.lantern(Blocks.LANTERN, true);
			case 's' -> Palette.slab(Blocks.SPRUCE_SLAB, false);
			case 'g' -> Palette.st(Blocks.MOSS_BLOCK);
			case 'f' -> Palette.st(Blocks.FERN);
			case 'r' -> Palette.slab(random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE_SLAB : Blocks.COBBLESTONE_SLAB, false);
			case 'R' -> Palette.st(rock(random));
			default -> null;
		};
	}

	private static Block rock(Random random) {
		int roll = random.nextInt(20);
		if (roll < 6) {
			return Blocks.STONE;
		}
		if (roll < 10) {
			return Blocks.ANDESITE;
		}
		if (roll < 13) {
			return Blocks.TUFF;
		}
		if (roll < 16) {
			return Blocks.COBBLESTONE;
		}
		if (roll < 19) {
			return Blocks.MOSSY_COBBLESTONE;
		}
		return Blocks.CALCITE;
	}
}
