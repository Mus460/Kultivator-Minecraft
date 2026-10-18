package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Menara Langit Membara (Blazing Sky Tower, DESIGN 21.2 T9, 21.9 {@code TowerShaft}): 3-7 rooms straight below an
 * entrance, each a 5×5 room of polished blackstone bricks with a cinnabar band, a lit soul campfire on crying obsidian in
 * the middle (exactly what {@code BlazingTower} counts as a floor) and soul lanterns, 4 apart, reached by a doorless
 * half-step spiral (8 cells per turn, one turn per room) around a central post in a 3×3 well. The 1-block shell is placed
 * before the rooms are hollowed (phase SHELL, then the hollowing), so caves and fluids never leak in.
 * <p>
 * Template-local: the well is centred on (x, z), the rooms lie on its {@code roomSide}; {@code entrance} is the standing
 * level at the top of the well (a hall floor, a ting, a grotto); {@code firstRoomY} the standing (campfire) y of room 1.
 * The entrance building is a separate part (CELLAR: the well opens in a hall floor; TING, TOWER, GROTTO: a pavilion, a
 * tower hall or the grotto hill stands over it). Rooms stay within the 32-block sphere of {@code BlazingTower} (entrance
 * within 12 of the stele, the deepest campfire at most 28 below). Exact items per room: 1 crying obsidian, 1 soul campfire.
 * <p>
 * Source: <i>Battle Through the Heavens</i> ch. 491/549/622, the Blazing Sky tower (DESIGN 21.2).
 */
public final class TowerShaft implements Part {

	public static final int ROOM_SPACING = 4;

	/** Clockwise ring of the 3×3 well around its post. */
	private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

	private final int x;
	private final int z;
	private final Lv entrance;
	private final int rooms;
	private final int firstRoomY;
	private Direction roomSide = Direction.SOUTH;

	public TowerShaft(int x, int z, Lv entrance, int rooms, int firstRoomY) {
		this.x = x;
		this.z = z;
		this.entrance = entrance;
		this.rooms = Math.max(1, Math.min(7, rooms));
		this.firstRoomY = firstRoomY;
	}

	public TowerShaft roomSide(Direction side) {
		this.roomSide = side.getAxis().isHorizontal() ? side : Direction.SOUTH;
		return this;
	}

	private int lastRoomY() {
		return firstRoomY - ROOM_SPACING * (rooms - 1);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int top = ctx.level(entrance);
		return Part.box(x, z, roomSide, -3, lastRoomY() - 2, -3, 3, top, 9);
	}

	@Override
	public String kind() {
		return "tower";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int top = ctx.level(entrance);
		sink.pushFacing(x, 0, z, roomSide);
		int bottom = lastRoomY();
		BlockState brick = Palette.st(Palette.TOWER_BRICK);
		// The well shell (5×5 ring), placed before anything is hollowed.
		sink.phase(Phase.SHELL);
		for (int y = bottom - 1; y <= top - 1; y++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == bottom - 1) {
						sink.set(dx, y, dz, y == top - 1 ? Palette.st(Blocks.POLISHED_BLACKSTONE) : brick);
					}
				}
			}
		}
		// Rooms: shell first.
		for (int k = 0; k < rooms; k++) {
			int ry = firstRoomY - ROOM_SPACING * k;
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = 2; dz <= 8; dz++) {
					for (int y = ry - 1; y <= ry + 3; y++) {
						boolean shell = Math.abs(dx) == 3 || dz == 2 || dz == 8 || y == ry - 1 || y == ry + 3;
						if (shell) {
							BlockState state = y == ry + 1 && (Math.abs(dx) == 3 || dz == 8) ? Palette.st((dx == 0 || dz == 5) ? Blocks.CHISELED_CINNABAR : Blocks.CINNABAR_BRICKS) : brick;
							sink.set(dx, y, dz, state);
						}
					}
				}
			}
		}
		// Hollowing, top-down, after the shells.
		sink.phase(Phase.FILL);
		for (int y = top - 1; y >= bottom; y--) {
			sink.layer(-y);
			for (int[] cell : RING) {
				sink.air(cell[0], y, cell[1]);
			}
		}
		for (int k = 0; k < rooms; k++) {
			int ry = firstRoomY - ROOM_SPACING * k;
			for (int y = ry + 2; y >= ry; y--) {
				sink.layer(-y);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = 3; dz <= 7; dz++) {
						sink.air(dx, y, dz);
					}
				}
				// The doorway from the well (beside ring cell (0, 1)).
				sink.air(0, y, 2);
			}
			sink.air(0, ry + 2, 2);
		}
		sink.layerAuto();
		// The central post with a light every turn.
		sink.phase(Phase.FRAME);
		for (int y = bottom; y <= top - 1; y++) {
			sink.set(0, y, 0, Palette.st((top - 1 - y) % 4 == 2 ? Palette.LIGHT : Palette.TOWER_BRICK));
		}
		// The half-step spiral: surface height h (half-blocks) from the bottom room up to the entrance.
		sink.phase(Phase.PLINTH);
		for (int h = 2 * bottom; h <= 2 * top; h++) {
			int index = Math.floorMod(5 + (h - 2 * firstRoomY), 8);
			int[] cell = RING[index];
			int y;
			BlockState step;
			if (h % 2 == 0) {
				y = h / 2 - 1;
				step = Palette.st(Palette.TOWER_BRICK);
			} else {
				y = (h - 1) / 2;
				step = Palette.slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, false);
			}
			if (y >= top - 1 && h == 2 * top) {
				continue;
			}
			// Treads hang off the post (each one turn above the last), so every tread keeps 3 of head room.
			sink.set(cell[0], y, cell[1], step);
		}
		// Room furniture: crying obsidian, the lit soul campfire, soul lanterns in the corners.
		sink.phase(Phase.INTERIOR);
		for (int k = 0; k < rooms; k++) {
			int ry = firstRoomY - ROOM_SPACING * k;
			sink.set(0, ry - 1, 5, Palette.st(Blocks.CRYING_OBSIDIAN));
			sink.set(0, ry, 5, Palette.campfire(Blocks.SOUL_CAMPFIRE, false));
			for (int[] corner : new int[][]{{-2, 3}, {2, 3}, {-2, 7}, {2, 7}}) {
				sink.set(corner[0], ry + 2, corner[1], Palette.lantern(Blocks.SOUL_LANTERN, true));
			}
		}
		sink.anchor("tower", 1, top, -1);
		sink.pop();
	}
}
