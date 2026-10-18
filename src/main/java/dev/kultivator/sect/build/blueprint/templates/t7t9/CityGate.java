package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Hall;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * A gate of the template-8 citadel (DESIGN 21.3 T4): a deepslate gate block straddling the wall line at z, pierced by an
 * arched passage along z, with a merlon parapet round its deck and a timber gatehouse on top. {@link #tower} is the
 * three-storey gate tower (城楼): a 23-wide block with a 5-wide arch, a two-storey rank B hall with the plaque on top and
 * red banners on poles at the front of its deck; {@link #side} the side gate: a 13-wide block with a 3-wide arch and a
 * single-eave gatehouse. Template-local; the deck is the standing level {@code level + height}.
 * <p>
 * The gatehouse is planned before the block, so its platform foundation never refills the passage (the shared
 * {@code Gates.gateTower} plans them the other way round). Art direction from Ming city gates, not novel canon.
 */
public final class CityGate implements Part {

	private final int x;
	private final int z;
	private final Lv level;
	private final int halfX;
	private final int halfZ;
	private final int arch;
	private final int height;
	private final Hall house;
	private final boolean banners;

	private CityGate(int x, int z, Lv level, int halfX, int halfZ, int arch, int height, Hall house, boolean banners) {
		this.x = x;
		this.z = z;
		this.level = level;
		this.halfX = halfX;
		this.halfZ = halfZ;
		this.arch = arch;
		this.height = height;
		this.house = house;
		this.banners = banners;
	}

	/** The three-storey gate tower: block x ±{@code half}, z ±4, {@code height} high, a two-storey hall on top. */
	public static CityGate tower(int x, int z, Lv level, int half, int height) {
		int h = Math.max(7, height);
		Hall top = new Hall(x, z).site(level.plus(h)).bays(4, 6, 4).depth(7).pillar(5).platform(0).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
				.storeys(2, 4).doors(Hall.Doors.OPEN).porch(0).steps(false, false).plaque(true).gableField(Palette.st(Palette.PILLAR));
		return new CityGate(x, z, level, Math.max(9, half), 4, 2, h, top, true);
	}

	/** A side gate: block x ±6, z ±3, {@code height} high, a single-eave gatehouse on top. */
	public static CityGate side(int x, int z, Lv level, int height) {
		int h = Math.max(6, height);
		Hall house = new Hall(x, z).site(level.plus(h)).bays(3, 4, 3).depth(5).pillar(4).platform(0).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
				.doors(Hall.Doors.NONE).porch(0).steps(false, false).gableField(Palette.st(Palette.PILLAR));
		return new CityGate(x, z, level, 6, 3, 1, h, house, false);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return BoundingBox.encapsulating(house.bounds(ctx), new BoundingBox(x - halfX, y - 2, z - halfZ, x + halfX, y + height + 1, z + halfZ));
	}

	@Override
	public String kind() {
		return "gate_tower";
	}

	private boolean inArch(int dx, int fy) {
		int a = Math.abs(dx);
		if (a > arch || fy < 0) {
			return false;
		}
		return fy <= crown() - (a == arch ? 1 : 0);
	}

	private int crown() {
		return arch + 2 + (arch >= 2 ? 1 : 0);
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		// The gatehouse first: its platform foundation is overwritten by the block (and the passage) below.
		house.emit(ctx, sink);
		int y0 = ctx.level(level);
		Random random = ctx.random("gate" + x + "," + z);
		sink.push(x, y0, z, 0);
		Phase previous = sink.phase();
		for (int dx = -halfX; dx <= halfX; dx++) {
			for (int dz = -halfZ; dz <= halfZ; dz++) {
				int ground = sink.ground(ctx, dx, dz);
				sink.phase(Phase.CLEAR);
				for (int fy = Math.max(ground + 1, height); fy <= Math.min(sink.top(ctx, dx, dz), height + 10); fy++) {
					if (sink.get(dx, fy, dz) == null) {
						sink.air(dx, fy, dz);
					}
				}
				sink.phase(Phase.PLINTH);
				for (int fy = Math.max(ground + 1, -6); fy < height; fy++) {
					if (inArch(dx, fy)) {
						sink.air(dx, fy, dz);
						continue;
					}
					boolean face = Math.abs(dz) == halfZ || Math.abs(dx) == halfX;
					if (fy == height - 1) {
						sink.set(dx, fy, dz, Palette.st(face ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE));
					} else if (!face) {
						sink.set(dx, fy, dz, Palette.st(Palette.FILL_STONE));
					} else if (fy < 0) {
						sink.set(dx, fy, dz, Palette.st(Blocks.COBBLED_DEEPSLATE));
					} else {
						boolean band = fy == height - 3;
						sink.set(dx, fy, dz, Palette.st(band ? Blocks.CHISELED_DEEPSLATE
								: random.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS));
					}
				}
				if (Math.abs(dx) <= arch) {
					sink.set(dx, -1, dz, Palette.st(Palette.AXIS_PAVE));
				}
			}
		}
		// Arch trim on both mouths: eased corners and a keystone.
		int crown = crown();
		for (int dz : new int[]{-halfZ, halfZ}) {
			sink.set(-arch, crown, dz, Palette.stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST, true));
			sink.set(arch, crown, dz, Palette.stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST, true));
			sink.set(0, crown + 1, dz, Palette.st(Blocks.CHISELED_POLISHED_BLACKSTONE));
		}
		sink.phase(Phase.ORNAMENT);
		for (int dz = -halfZ + 1; dz <= halfZ - 1; dz += Math.max(1, halfZ - 1)) {
			sink.set(0, crown, dz, Palette.ironChain());
			sink.set(0, crown - 1, dz, Palette.lantern(Blocks.LANTERN, true));
		}
		// Merlon parapet round the deck.
		int index = 0;
		for (int dx = -halfX; dx <= halfX; dx++) {
			for (int dz = -halfZ; dz <= halfZ; dz++) {
				if (Math.abs(dx) != halfX && Math.abs(dz) != halfZ) {
					continue;
				}
				index++;
				if (sink.get(dx, height, dz) == null) {
					sink.set(dx, height, dz, index % 2 == 0 ? Palette.st(Blocks.DEEPSLATE_BRICKS) : Palette.st(Blocks.DEEPSLATE_BRICK_WALL));
				}
			}
		}
		// Red banners on poles at the front corners of the deck.
		if (banners) {
			for (int side : new int[]{-1, 1}) {
				int bx = side * (halfX - 1);
				for (int fy = height; fy < height + 3; fy++) {
					sink.set(bx, fy, halfZ - 1, Palette.st(Palette.FENCE));
				}
				sink.set(bx, height + 3, halfZ - 1, Palette.banner(DyeColor.RED, Direction.SOUTH));
			}
		}
		sink.phase(previous);
		sink.pop();
	}

	@Override
	public void decorate(Ctx ctx, Sink sink, int tier) {
		house.decorate(ctx, sink, tier);
	}
}
