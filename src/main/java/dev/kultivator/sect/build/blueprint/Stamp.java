package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Small set pieces drawn from a char grid (DESIGN 21.7 "Stamps"): layers bottom-up (layer 0 stands on the floor, -1 =
 * {@link #floorLayer} replaces the floor cell), each layer a list of rows from the back (-z) to the front (+z), each row a
 * string centred on x = 0 (odd lengths). A space or '.' is "leave as is", '_' is air. The legend maps the other chars to
 * states in the stamp's canonical frame (front = +z); {@link #at} places a copy facing any direction on any level.
 * <pre>
 *   Stamp lion = Stamp.of("lion")
 *       .layer("P")      // layer 0: plinth
 *       .layer("S")      // layer 1: the beast
 *       .legend('P', Palette.st(Blocks.POLISHED_ANDESITE))
 *       .legend('S', Palette.stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
 *   component.add(lion.at(4, 22, Direction.SOUTH, Lv.PLAZA));
 * </pre>
 * The predefined pieces below are ready to use: {@link #lion}, {@link #ding}, {@link #vat}, {@link #weaponRack},
 * {@link #drum}, {@link #flagPole}, {@link #bonsai}, {@link #boundaryStone}, {@link #targetPost}, {@link #dummy},
 * {@link #targetBoard}, {@link #coop}, {@link #horseHeadGable}.
 */
public final class Stamp {

	private final String id;
	private final java.util.List<String[]> layers = new java.util.ArrayList<>();
	private final Map<Character, BlockState> legend = new HashMap<>();
	private boolean floorLayer;
	private Phase phase = Phase.ORNAMENT;

	private Stamp(String id) {
		this.id = id;
	}

	public static Stamp of(String id) {
		return new Stamp(id);
	}

	/** The next layer up, rows from the back (-z) to the front (+z). */
	public Stamp layer(String... rows) {
		layers.add(rows);
		return this;
	}

	public Stamp legend(char key, BlockState state) {
		legend.put(key, state);
		return this;
	}

	/** The first layer replaces the floor cell (y = level - 1) instead of standing on it. */
	public Stamp floorLayer(boolean floor) {
		this.floorLayer = floor;
		return this;
	}

	public Stamp phase(Phase phase) {
		this.phase = phase;
		return this;
	}

	/** A placed copy of the stamp at template-local (x, z) facing {@code front} on {@code level}. */
	public Part at(int x, int z, Direction front, Lv level) {
		Stamp stamp = this;
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level) - (floorLayer ? 1 : 0);
				int halfX = 0;
				int depth = 0;
				for (String[] rows : layers) {
					depth = Math.max(depth, rows.length);
					for (String row : rows) {
						halfX = Math.max(halfX, row.length() / 2);
					}
				}
				return Part.box(x, z, front, -halfX, y, -depth / 2, halfX, y + Math.max(0, layers.size() - 1), depth - depth / 2 - 1);
			}

			@Override
			public String kind() {
				return stamp.id;
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int y = ctx.level(level);
				sink.pushFacing(x, y, z, front);
				stamp.draw(sink, 0, floorLayer ? -1 : 0, 0);
				sink.pop();
			}
		};
	}

	/** Draws the stamp in the current frame with its origin at frame (ox, oy, oz). */
	public void draw(Sink sink, int ox, int oy, int oz) {
		Phase previous = sink.phase();
		sink.phase(phase);
		for (int l = 0; l < layers.size(); l++) {
			String[] rows = layers.get(l);
			int depth = rows.length;
			for (int r = 0; r < depth; r++) {
				String row = rows[r];
				int half = row.length() / 2;
				for (int c = 0; c < row.length(); c++) {
					char key = row.charAt(c);
					if (key == ' ' || key == '.') {
						continue;
					}
					int fx = ox + c - half;
					int fy = oy + l;
					int fz = oz + r - depth / 2;
					if (key == '_') {
						sink.air(fx, fy, fz);
						continue;
					}
					BlockState state = legend.get(key);
					if (state != null) {
						sink.set(fx, fy, fz, state);
					}
				}
			}
		}
		sink.phase(previous);
	}

	// ================================================================== predefined pieces (DESIGN 21.7)

	/** A stone guardian lion on a plinth facing {@code front} (a pair flanks a gate). */
	public static Part lion(int x, int z, Direction front, Lv level) {
		return of("lion").layer("P").layer("S").layer("W")
				.legend('P', Palette.st(Blocks.POLISHED_ANDESITE))
				.legend('S', Palette.stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false))
				.legend('W', Palette.st(Blocks.MOSSY_STONE_BRICK_WALL))
				.at(x, z, front, level);
	}

	/**
	 * A bronze incense ding (waxed exposed copper with a grate): {@code signal} true for the one signal-smoke ding per sect
	 * (a hidden hay block under its lit campfire, DESIGN 21.7); every other ding burns without signal smoke.
	 */
	public static Part ding(int x, int z, Lv level, boolean signal) {
		return of("ding").floorLayer(true)
				.layer("   ", signal ? " H " : " B ", "   ")
				.layer("FFF", "FCF", "FFF")
				.layer("G G", " _ ", "G G")
				.legend('H', Palette.st(Blocks.HAY_BLOCK))
				.legend('B', Palette.st(Palette.bronze(Blocks.CUT_COPPER)))
				.legend('F', Palette.st(Palette.bronze(Blocks.CUT_COPPER)))
				.legend('C', Palette.campfire(Blocks.CAMPFIRE, signal))
				.legend('G', Palette.st(Palette.bronze(Blocks.COPPER_GRATE)))
				.at(x, z, Direction.SOUTH, level);
	}

	/**
	 * A small censer-ding for a cramped court (template 1): a lit campfire with an iron cauldron on it, one cell, so the
	 * axis and the formation disc stay clear.
	 */
	public static Part smallDing(int x, int z, Lv level) {
		return of("small_ding")
				.layer("C")
				.layer("V")
				.legend('C', Palette.campfire(Blocks.CAMPFIRE, false))
				.legend('V', Palette.st(Blocks.CAULDRON))
				.at(x, z, Direction.SOUTH, level);
	}

	/** A water vat: a full water cauldron on a bronze grate stand (Pikul Air, the water duty). */
	public static Part vat(int x, int z, Lv level) {
		return of("vat").layer("V")
				.legend('V', Palette.waterVat())
				.at(x, z, Direction.SOUTH, level);
	}

	/** A weapon rack against a wall: fence posts with lying spears (lightning-rod free: fences and trapdoors only). */
	public static Part weaponRack(int x, int z, Direction front, Lv level) {
		return of("weapon_rack").layer("FTF").layer("FTF").layer("SSS")
				.legend('F', Palette.st(Blocks.DARK_OAK_FENCE))
				.legend('T', Palette.lattice(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH))
				.legend('S', Palette.slab(Blocks.DARK_OAK_SLAB, false))
				.at(x, z, front, level);
	}

	/** A bell frame: two cinnabar posts, a beam with a small tile roof and a bell hanging in the middle (exact item). */
	public static Part bellFrame(int x, int z, Direction front, Lv level) {
		return of("bell_frame")
				.layer("P.P").layer("P.P").layer("P.P").layer("PBP").layer("WWW").layer("sSs")
				.legend('P', Palette.st(Palette.PILLAR_THIN))
				.legend('B', Palette.bell(Direction.SOUTH, true))
				.legend('W', Palette.st(Palette.BEAM))
				.legend('S', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false))
				.legend('s', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, true))
				.at(x, z, front, level);
	}

	/** A drum: an upright barrel on a red cinnabar stand (barrels are exact items for templates 3-9). */
	public static Part drum(int x, int z, Lv level) {
		return of("drum").layer("C").layer("B")
				.legend('C', Palette.st(Blocks.CHISELED_CINNABAR))
				.legend('B', Palette.barrel(Direction.SOUTH))
				.at(x, z, Direction.SOUTH, level);
	}

	/** A red banner with a yellow border on a mangrove pole {@code height} high (4-6). */
	public static Part flagPole(int x, int z, Direction front, Lv level, int height) {
		Stamp stamp = of("flag_pole");
		int poles = Math.max(3, Math.min(6, height)) - 1;
		for (int i = 0; i < poles; i++) {
			stamp.layer("F");
		}
		stamp.layer("B");
		return stamp.legend('F', Palette.st(Palette.FENCE)).legend('B', Palette.banner(DyeColor.RED, Direction.SOUTH)).at(x, z, front, level);
	}

	/** A potted bonsai on a stone stand (azalea, flowering azalea, cherry or spruce by {@code kind} 0-3). */
	public static Part bonsai(int x, int z, Lv level, int kind) {
		BlockState pot = switch (Math.floorMod(kind, 4)) {
			case 1 -> Palette.st(Blocks.POTTED_FLOWERING_AZALEA);
			case 2 -> Palette.st(Blocks.POTTED_CHERRY_SAPLING);
			case 3 -> Palette.st(Blocks.POTTED_SPRUCE_SAPLING);
			default -> Palette.st(Blocks.POTTED_AZALEA);
		};
		return of("bonsai").layer("S").layer("P")
				.legend('S', Palette.st(Blocks.POLISHED_ANDESITE))
				.legend('P', pot)
				.at(x, z, Direction.SOUTH, level);
	}

	/** A boundary stone pillar (界碑): a carved stone stele with a tile cap (no perimeter wall templates). */
	public static Part boundaryStone(int x, int z, Direction front, Lv level) {
		return of("boundary_stone").layer("P").layer("C").layer("C").layer("T")
				.legend('P', Palette.st(Blocks.POLISHED_ANDESITE))
				.legend('C', Palette.st(Blocks.CHISELED_STONE_BRICKS))
				.legend('T', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false))
				.at(x, z, front, level);
	}

	/** An archery target on a stone post (targets are exact items). */
	public static Part targetPost(int x, int z, Direction front, Lv level) {
		return of("target_post").layer("W").layer("T")
				.legend('W', Palette.st(Blocks.STONE_BRICK_WALL))
				.legend('T', Palette.st(Blocks.TARGET))
				.at(x, z, front, level);
	}

	/** A fence training dummy with a straw head (no armor stands: entities are banned in builds). */
	public static Part dummy(int x, int z, Lv level) {
		return of("dummy").layer("F").layer("F").layer("H")
				.legend('F', Palette.st(Blocks.DARK_OAK_FENCE))
				.legend('H', Palette.st(Blocks.HAY_BLOCK))
				.at(x, z, Direction.SOUTH, level);
	}

	/** A framed target board set into a wall face: a target in a dark-oak frame (template 1's courtyard targets). */
	public static Part targetBoard(int x, int y, int z, Direction face) {
		Lv lv = Lv.at(y);
		return of("target_board").layer("T").legend('T', Palette.st(Blocks.TARGET)).at(x, z, face, lv);
	}

	/** The Spirit-Tail coop: a small fenced pen with a hay bale under a tile lean-to (2 wide, 3 deep). */
	public static Part coop(int x, int z, Direction front, Lv level) {
		return of("coop")
				.layer("FFF", "FHF", "F_F")
				.layer("FFF", "F_F", "F_F")
				.layer("SSS", "SSS", "sss")
				.legend('F', Palette.st(Blocks.SPRUCE_FENCE))
				.legend('H', Palette.st(Blocks.HAY_BLOCK))
				.legend('S', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, true))
				.legend('s', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false))
				.at(x, z, front, level);
	}

	/**
	 * A horse-head gable wall (马头墙) of {@code steps} steps (2-3), each 2 high and 2 wide, capped by back-to-back trim
	 * stairs ending in an outward top slab; drawn across x, centred, standing on {@code baseY} (template-local).
	 */
	public static Part horseHeadGable(int x, int z, Direction front, int baseY, int halfWidth, int steps) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return Part.box(x, z, front, -halfWidth - 1, baseY, 0, halfWidth + 1, baseY + 2 * steps + 2, 0);
			}

			@Override
			public String kind() {
				return "horse_head";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				sink.pushFacing(x, baseY, z, front);
				Phase previous = sink.phase();
				sink.phase(Phase.WALL);
				int count = Math.max(2, Math.min(3, steps));
				for (int s = 0; s < count; s++) {
					int inner = halfWidth - 2 * s;
					if (inner < 0) {
						break;
					}
					int top = 2 * (s + 1);
					for (int fx = -inner; fx <= inner; fx++) {
						for (int fy = 0; fy < top; fy++) {
							sink.set(fx, fy, 0, Palette.st(Palette.PLASTER));
						}
					}
					// Caps: back-to-back trim stairs, and an outward top slab at each step end.
					for (int fx = -inner; fx <= inner; fx++) {
						sink.set(fx, top, 0, Palette.slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
					}
					sink.set(-inner - 1, top, 0, Palette.slab(Blocks.POLISHED_DEEPSLATE_SLAB, true));
					sink.set(inner + 1, top, 0, Palette.slab(Blocks.POLISHED_DEEPSLATE_SLAB, true));
					sink.set(-inner, top, 0, Palette.stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, false));
					sink.set(inner, top, 0, Palette.stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST, false));
				}
				sink.phase(previous);
				sink.pop();
			}
		};
	}
}
