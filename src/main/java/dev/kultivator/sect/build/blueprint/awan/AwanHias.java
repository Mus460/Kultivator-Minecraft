package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Pavilion;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.Tree;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The small set pieces of the Awan family (DESIGN 37.4.5), each a {@link Part} made by a factory:
 * <ul>
 * <li>{@link #pola}: the two formation nodes the Mata Formasi is set into by hand (never placed by the builder): the 5×5
 * Cloud Transfer node ({@code awan_transfer}: a quartz ring round the eye, amethyst at the corners) and the 7×7 Cloud Net
 * node ({@code jaring_awan}: quartz ring, quartz-pillar posts crowned with amethyst, end rods at the corners, an amethyst
 * heart under the eye), exactly the patterns of {@code formasi.catalog.CatalogLangit}.</li>
 * <li>{@link #tiangJaring}: a Cloud Net pole on the plateau rim (anchor {@code jaring_<i>}).</li>
 * <li>{@link #takik}: the lip of a mist waterfall (anchor {@code airterjun_<i>_<d>}; the fall itself is drawn by the
 * client: no water ever leaves an island).</li>
 * <li>{@link #meditasi}, {@link #halte}, {@link #panggung}: the rim pavilions of T7-T8 with their stairs down from the
 * plateau (anchors {@code meditasi_<i>}, {@code feri_atas}, {@code panggung_pedang}).</li>
 * <li>{@link #akar}: hanging roots, glow-berry vines and mangrove-root strands under an island (GARDEN).</li>
 * <li>{@link #taman}: trees and rocks scattered over the free cells of a ring (the island never looks empty).</li>
 * </ul>
 */
public final class AwanHias {

	/** The formation node kinds. */
	public enum Pola {
		TRANSFER,
		JARING
	}

	// ================================================================== formation nodes

	public static Part pola(Pola kind, int x, int z, Lv level, String anchor) {
		int half = kind == Pola.TRANSFER ? 2 : 3;
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level);
				return new BoundingBox(x - half, y - 1, z - half, x + half, y + 3, z + half);
			}

			@Override
			public String kind() {
				return "formation_node";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int y = ctx.level(level);
				Phase previous = sink.phase();
				// A quartz pad under the whole node (the pattern stands on it).
				sink.phase(Phase.PLINTH);
				for (int dx = -half; dx <= half; dx++) {
					for (int dz = -half; dz <= half; dz++) {
						boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == half;
						sink.set(x + dx, y - 1, z + dz, Palette.st(rim ? AwanPalette.AWAN_TEPI : Blocks.QUARTZ_BRICKS));
					}
				}
				if (kind == Pola.JARING) {
					sink.set(x, y - 1, z, Palette.st(Blocks.AMETHYST_BLOCK));
				}
				sink.phase(Phase.FRAME);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) {
							sink.set(x + dx, y, z + dz, Palette.st(Blocks.SMOOTH_QUARTZ));
						}
					}
				}
				// The eye's cell stays empty: the owner sets the Mata Formasi into it by hand (inactive until then).
				sink.air(x, y, z);
				if (kind == Pola.TRANSFER) {
					for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
						sink.set(x + c[0], y, z + c[1], Palette.st(Blocks.AMETHYST_BLOCK));
					}
				} else {
					for (int[] c : new int[][]{{3, 0}, {0, 3}, {-3, 0}, {0, -3}}) {
						sink.set(x + c[0], y, z + c[1], Palette.st(Blocks.QUARTZ_PILLAR));
						sink.set(x + c[0], y + 1, z + c[1], Palette.st(Blocks.QUARTZ_PILLAR));
						sink.set(x + c[0], y + 2, z + c[1], Palette.st(Blocks.AMETHYST_BLOCK));
					}
					for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
						sink.set(x + c[0], y, z + c[1], Palette.facing(Blocks.END_ROD, Direction.UP));
					}
				}
				sink.phase(previous);
				sink.anchor(anchor, x, y, z);
			}
		};
	}

	// ================================================================== landing points

	/**
	 * The landing points {@code darat_<i>} (DESIGN 37.7.4, read by 37G's raids and cloud pirates): eight points two cells
	 * inside the main island's rim up to {@code ring}, on the diagonals of each octant (never on the gate axis, a bridge,
	 * a dock lane or a cleft). Anchors only; no block.
	 */
	public static Part darat(int ring) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return new BoundingBox(0, 0, 0, 0, 0, 0);
			}

			@Override
			public String kind() {
				return "landing";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				AwanLayout layout = AwanLayout.of(ctx.template());
				AwanLayout.Pulau main = layout.main;
				int reach = (int) Math.ceil(main.radius() * 1.2D) + 2;
				int index = 0;
				for (int k = 0; k < 8; k++) {
					double angle = Math.PI / 8.0D + k * Math.PI / 4.0D;
					int best = -1;
					for (int r = 4; r <= reach; r++) {
						int x = main.cx + (int) Math.round(Math.cos(angle) * r);
						int z = main.cz + (int) Math.round(Math.sin(angle) * r);
						int at = main.ring(x, z);
						if (at < 0 || at > ring) {
							break;
						}
						if (!layout.cleft(x, z) && !onBridge(layout, x, z)) {
							best = r;
						}
					}
					if (best < 6) {
						continue;
					}
					int r = best - 2;
					int x = main.cx + (int) Math.round(Math.cos(angle) * r);
					int z = main.cz + (int) Math.round(Math.sin(angle) * r);
					int at = Math.max(0, main.ring(x, z));
					sink.anchor("darat_" + index++, x, layout.levelInRing(x, z, at), z);
				}
			}
		};
	}

	private static boolean onBridge(AwanLayout layout, int x, int z) {
		for (AwanLayout.Jembatan bridge : layout.bridges) {
			if (bridge.covers(x, z, 3)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== rim pieces

	/** A Cloud Net pole: a quartz-pillar shaft 4 high, an amethyst cap and an end rod, on the plateau rim. */
	public static Part tiangJaring(AwanLayout.Titik spot) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return new BoundingBox(spot.x(), spot.level() - 1, spot.z(), spot.x(), spot.level() + 6, spot.z());
			}

			@Override
			public String kind() {
				return "net_pole";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int y = spot.level();
				Phase previous = sink.phase();
				sink.phase(Phase.FRAME);
				// The fence the pole replaces goes first (it stands on the rim).
				sink.demolish(spot.x(), y, spot.z());
				sink.set(spot.x(), y - 1, spot.z(), Palette.st(Palette.PILLAR_BASE));
				for (int h = 0; h < 4; h++) {
					sink.set(spot.x(), y + h, spot.z(), Palette.st(Blocks.QUARTZ_PILLAR));
				}
				sink.set(spot.x(), y + 4, spot.z(), Palette.st(Blocks.AMETHYST_BLOCK));
				sink.phase(Phase.ORNAMENT);
				sink.set(spot.x(), y + 5, spot.z(), Palette.facing(Blocks.END_ROD, Direction.UP));
				sink.phase(previous);
				sink.anchor(spot.id(), spot.x(), y, spot.z());
			}
		};
	}

	/** The lip of a mist waterfall: mossy stones on both sides of the cleft, a calcite sill, the anchor. */
	public static Part takik(AwanLayout.Titik lip) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return new BoundingBox(lip.x() - 2, lip.level() - 2, lip.z() - 2, lip.x() + 2, lip.level() + 2, lip.z() + 2);
			}

			@Override
			public String kind() {
				return "mist_fall";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				Direction out = lip.out();
				Direction side = out.getClockWise();
				int y = lip.level();
				Phase previous = sink.phase();
				sink.phase(Phase.PLINTH);
				sink.set(lip.x(), y - 1, lip.z(), Palette.st(Blocks.CALCITE));
				sink.phase(Phase.GARDEN);
				for (int s : new int[]{-2, 2}) {
					int x = lip.x() + side.getStepX() * s;
					int z = lip.z() + side.getStepZ() * s;
					sink.set(x, y, z, Palette.st(Blocks.MOSSY_COBBLESTONE));
					// The fern's footing: the notch may cut the rim under it.
					sink.phase(Phase.PLINTH);
					sink.setIfAbsent(x - out.getStepX(), y - 1, z - out.getStepZ(), Palette.st(Blocks.MOSS_BLOCK));
					sink.phase(Phase.GARDEN);
					sink.setIfAbsent(x - out.getStepX(), y, z - out.getStepZ(), Palette.st(Blocks.FERN));
				}
				sink.setIfAbsent(lip.x() - out.getStepX(), y, lip.z() - out.getStepZ(), Palette.st(Blocks.MOSS_CARPET));
				sink.phase(previous);
				sink.anchor(lip.id() + "_" + AwanLayout.letter(out), lip.x(), y, lip.z());
			}
		};
	}

	/**
	 * A stair from the plateau (level 0) down to a terrace spot (x, z) at {@code level}, 3 wide, running inward against
	 * {@code out}; it opens the plateau balustrade where it arrives. Nothing when the spot is on the plateau.
	 */
	static void stairDown(AwanLayout layout, Sink sink, int x, int z, Direction out, int level) {
		if (level >= 0) {
			return;
		}
		Direction in = out.getOpposite();
		// The plateau rim inward from the spot.
		int rimX = x;
		int rimZ = z;
		for (int i = 1; i < 24; i++) {
			int cx = x + in.getStepX() * i;
			int cz = z + in.getStepZ() * i;
			int ring = layout.main.ring(cx, cz);
			if (ring >= 0 && ring <= 2) {
				rimX = cx;
				rimZ = cz;
				break;
			}
		}
		int rise = -level;
		Direction side = out.getClockWise();
		Phase previous = sink.phase();
		for (int j = 1; j <= rise; j++) {
			// Step j stands j cells outside the rim, its top at level 0 - j.
			int cx = rimX + out.getStepX() * j;
			int cz = rimZ + out.getStepZ() * j;
			int top = -j;
			for (int w = -1; w <= 1; w++) {
				int px = cx + side.getStepX() * w;
				int pz = cz + side.getStepZ() * w;
				sink.phase(Phase.FRAME);
				sink.demolish(px, top + 1, pz);
				sink.demolish(px, top + 2, pz);
				sink.phase(Phase.PLINTH);
				sink.set(px, top, pz, Palette.stair(Blocks.SMOOTH_QUARTZ_STAIRS, in, false));
				for (int fy = level - 1; fy < top; fy++) {
					sink.set(px, fy, pz, Palette.st(Blocks.CALCITE));
				}
			}
		}
		// The balustrade at the plateau rim and the head room over the stair's top.
		sink.phase(Phase.FRAME);
		for (int w = -1; w <= 1; w++) {
			for (int k = 0; k <= 1; k++) {
				int px = rimX + side.getStepX() * w - in.getStepX() * k;
				int pz = rimZ + side.getStepZ() * w - in.getStepZ() * k;
				sink.demolish(px, 0, pz);
				sink.demolish(px, 1, pz);
			}
		}
		sink.phase(previous);
	}

	/** A meditation pavilion on the rim: a hexagonal ting with a bench, its stair down from the plateau. */
	public static Part meditasi(AwanLayout.Titik spot) {
		Pavilion ting = new Pavilion(spot.x(), spot.z(), Lv.at(spot.level())).size(3).sides(6).pillar(3).rank(Rank.B).entry(spot.out().getOpposite());
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return ting.bounds(ctx);
			}

			@Override
			public String kind() {
				return "meditation_ting";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				AwanLayout layout = AwanLayout.of(ctx.template());
				stairDown(layout, sink, spot.x() - spot.out().getStepX() * 3, spot.z() - spot.out().getStepZ() * 3, spot.out(), spot.level());
				ting.emit(ctx, sink);
				sink.anchor(spot.id(), spot.x(), spot.level(), spot.z());
			}

			@Override
			public void decorate(Ctx ctx, Sink sink, int tier) {
				ting.decorate(ctx, sink, tier);
			}
		};
	}

	/**
	 * The ferry stop (Halte Feri Awan, T7): a little deck jutting out over the rim, two cinnabar posts under a tiled roof,
	 * the bell that calls the ferry (an exact item), a lantern; anchor {@code feri_atas} at the deck's outer edge.
	 */
	public static Part halte(AwanLayout.Titik spot) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return new BoundingBox(spot.x() - 5, spot.level() - 3, spot.z() - 5, spot.x() + 5, spot.level() + 8, spot.z() + 5);
			}

			@Override
			public String kind() {
				return "ferry_stop";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				AwanLayout layout = AwanLayout.of(ctx.template());
				Direction out = spot.out();
				Direction side = out.getClockWise();
				int y = spot.level();
				stairDown(layout, sink, spot.x() - out.getStepX() * 2, spot.z() - out.getStepZ() * 2, out, y);
				Phase previous = sink.phase();
				// The deck: 5 wide, 4 out over the rim.
				sink.phase(Phase.PLINTH);
				for (int a = -1; a <= 4; a++) {
					for (int w = -2; w <= 2; w++) {
						int x = spot.x() + out.getStepX() * a + side.getStepX() * w;
						int z = spot.z() + out.getStepZ() * a + side.getStepZ() * w;
						sink.set(x, y - 1, z, Palette.st(Blocks.POLISHED_ANDESITE));
						if (a >= 1) {
							sink.set(x, y - 2, z, Palette.stair(Blocks.POLISHED_ANDESITE_STAIRS, out.getOpposite(), true));
						}
					}
				}
				sink.phase(Phase.FRAME);
				int[][] posts = {{0, -2}, {0, 2}, {3, -2}, {3, 2}};
				for (int[] p : posts) {
					int x = spot.x() + out.getStepX() * p[0] + side.getStepX() * p[1];
					int z = spot.z() + out.getStepZ() * p[0] + side.getStepZ() * p[1];
					sink.set(x, y, z, Palette.st(Palette.PILLAR_BASE));
					for (int h = 1; h <= 3; h++) {
						sink.set(x, y + h, z, Palette.st(Palette.PILLAR_THIN));
					}
				}
				sink.phase(Phase.WALL);
				for (int a = 0; a <= 3; a++) {
					for (int w : new int[]{-2, 2}) {
						if (a == 1 || a == 2) {
							int x = spot.x() + out.getStepX() * a + side.getStepX() * w;
							int z = spot.z() + out.getStepZ() * a + side.getStepZ() * w;
							sink.set(x, y, z, Palette.st(AwanPalette.PAGAR_KAYU));
						}
					}
				}
				// A small roof over the posts (pyramid, rank B tiles).
				int cx = spot.x() + out.getStepX() * 2;
				int cz = spot.z() + out.getStepZ() * 2;
				sink.push(cx, 0, cz, 0);
				new RoofBuilder(RoofBuilder.Type.PYRAMID, -3, 3, -3, 3, y + 4, ctx.roof(Rank.B)).overhang(1).emit(sink);
				sink.pop();
				// The bell hanging from the roof's middle, and a lantern.
				sink.phase(Phase.ORNAMENT);
				sink.set(cx, y + 3, cz, Palette.bell(out, true));
				int lx = spot.x() + side.getStepX() * -1;
				int lz = spot.z() + side.getStepZ() * -1;
				sink.setIfAbsent(lx, y, lz, Palette.lantern(Blocks.LANTERN, false));
				sink.phase(previous);
				sink.anchor("feri_atas", spot.x() + out.getStepX() * 4, y, spot.z() + out.getStepZ() * 4);
			}
		};
	}

	/**
	 * The sword terrace (Panggung Pedang, T7): a cantilevered quartz deck 7 wide and 9 out over the rim with a marble
	 * balustrade, two weapon racks, red banners at the corners and a jade lantern; patrolling disciples take off and land
	 * here (anchor {@code panggung_pedang}).
	 */
	public static Part panggung(AwanLayout.Titik spot, int width) {
		int hw = width / 2;
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return new BoundingBox(spot.x() - 10, spot.level() - 3, spot.z() - 10, spot.x() + 10, spot.level() + 6, spot.z() + 10);
			}

			@Override
			public String kind() {
				return "sword_terrace";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				AwanLayout layout = AwanLayout.of(ctx.template());
				Direction out = spot.out();
				Direction side = out.getClockWise();
				int y = spot.level();
				stairDown(layout, sink, spot.x() - out.getStepX() * 2, spot.z() - out.getStepZ() * 2, out, y);
				Phase previous = sink.phase();
				sink.phase(Phase.PLINTH);
				for (int a = -1; a <= 8; a++) {
					for (int w = -hw; w <= hw; w++) {
						int x = spot.x() + out.getStepX() * a + side.getStepX() * w;
						int z = spot.z() + out.getStepZ() * a + side.getStepZ() * w;
						boolean border = Math.abs(w) == hw || a == 8;
						sink.set(x, y - 1, z, Palette.st(border ? AwanPalette.AWAN_TEPI : AwanPalette.AWAN_LANTAI));
						if (a >= 1 && (Math.abs(w) == hw || a % 3 == 0)) {
							// Corbels under the deck.
							sink.set(x, y - 2, z, Palette.stair(Blocks.QUARTZ_STAIRS, out.getOpposite(), true));
						}
					}
				}
				sink.phase(Phase.WALL);
				for (int a = 0; a <= 8; a++) {
					for (int w = -hw; w <= hw; w++) {
						boolean edge = Math.abs(w) == hw || a == 8;
						if (!edge || a <= 0) {
							continue;
						}
						int x = spot.x() + out.getStepX() * a + side.getStepX() * w;
						int z = spot.z() + out.getStepZ() * a + side.getStepZ() * w;
						boolean post = (a + w) % 3 == 0;
						sink.set(x, y, z, Palette.st(post ? Blocks.QUARTZ_PILLAR : AwanPalette.PAGAR_BATU));
					}
				}
				sink.phase(Phase.ORNAMENT);
				for (int w : new int[]{-hw, hw}) {
					int x = spot.x() + out.getStepX() * 8 + side.getStepX() * w;
					int z = spot.z() + out.getStepZ() * 8 + side.getStepZ() * w;
					for (int h = 1; h <= 3; h++) {
						sink.set(x, y + h, z, Palette.st(Palette.FENCE));
					}
					sink.set(x, y + 4, z, Palette.banner(DyeColor.RED, out));
				}
				int rx = spot.x() + out.getStepX() * 2;
				int rz = spot.z() + out.getStepZ() * 2;
				Stamp.weaponRack(rx + side.getStepX() * (hw - 1), rz + side.getStepZ() * (hw - 1), side.getOpposite(), Lv.at(y)).emit(ctx, sink);
				Stamp.weaponRack(rx - side.getStepX() * (hw - 1), rz - side.getStepZ() * (hw - 1), side, Lv.at(y)).emit(ctx, sink);
				LanternPost.emitOne(sink, spot.x() + out.getStepX() * 7, y, spot.z() + out.getStepZ() * 7, LanternPost.Style.JADE, out);
				sink.phase(previous);
				sink.anchor("panggung_pedang", spot.x() + out.getStepX() * 4, y, spot.z() + out.getStepZ() * 4);
			}
		};
	}

	// ================================================================== under the island

	/**
	 * Hanging growth under one island (DESIGN 37.4.2 steps 5-6): hanging roots (1:6), glow-berry cave vines 1-4 long on
	 * the outer belly (their heads at the last age: they never grow), and mangrove-root strands under the tip ending in
	 * rooted dirt and hanging roots. At most one spore blossom (the sect's two are the satellites' and the main one's).
	 */
	public static Part akar(String island, boolean spore) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				AwanLayout layout = AwanLayout.of(ctx.template());
				AwanLayout.Pulau p = PulauAwan.islandOf(layout, island);
				int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
				return new BoundingBox(p.cx - reach, p.deepest() - 9, p.cz - reach, p.cx + reach, p.level, p.cz + reach);
			}

			@Override
			public String kind() {
				return "roots";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				AwanLayout layout = AwanLayout.of(ctx.template());
				AwanLayout.Pulau p = PulauAwan.islandOf(layout, island);
				int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
				Random random = ctx.random("akar_" + island);
				Phase previous = sink.phase();
				sink.phase(Phase.GARDEN);
				int deepestY = Integer.MAX_VALUE;
				int tipX = p.cx;
				int tipZ = p.cz;
				for (int x = p.cx - reach; x <= p.cx + reach; x++) {
					for (int z = p.cz - reach; z <= p.cz + reach; z++) {
						if (!PulauAwan.exists(layout, p, x, z, 3)) {
							continue;
						}
						int top = PulauAwan.levelOf(layout, p, x, z) - 1;
						int bottom = PulauAwan.bottomOf(p, x, z, top);
						if (bottom < deepestY) {
							deepestY = bottom;
							tipX = x;
							tipZ = z;
						}
						double n = p.norm(x, z) / p.radius();
						double roll = p.shape.roll(x, bottom, z, 9);
						if (roll < 1.0D / 6.0D) {
							sink.setIfAbsent(x, bottom - 1, z, Palette.st(AwanPalette.LUMUT_GANTUNG));
						} else if (n >= 0.7D && roll < 1.0D / 6.0D + 1.0D / 12.0D) {
							int length = 1 + random.nextInt(4);
							boolean berries = random.nextInt(3) == 0;
							for (int i = 1; i < length; i++) {
								sink.setIfAbsent(x, bottom - i, z, AwanPalette.sulurBatang(berries && i % 2 == 0));
							}
							sink.setIfAbsent(x, bottom - length, z, AwanPalette.sulur(berries));
						}
					}
				}
				// Mangrove-root strands under the tip.
				int strands = 4 + (int) (p.radius() / 6.0D);
				for (int s = 0; s < strands && deepestY != Integer.MAX_VALUE; s++) {
					int x = tipX + random.nextInt(5) - 2;
					int z = tipZ + random.nextInt(5) - 2;
					if (!PulauAwan.exists(layout, p, x, z, 3)) {
						continue;
					}
					int top = PulauAwan.levelOf(layout, p, x, z) - 1;
					int y = PulauAwan.bottomOf(p, x, z, top) - 1;
					int length = 3 + random.nextInt(5);
					for (int i = 0; i < length; i++) {
						sink.set(x, y - i, z, Palette.st(AwanPalette.AKAR));
						if (random.nextInt(3) == 0 && i > 1) {
							int bx = x + (random.nextBoolean() ? 1 : -1);
							sink.setIfAbsent(bx, y - i, z, Palette.st(AwanPalette.AKAR));
						}
					}
					sink.set(x, y - length, z, Palette.st(AwanPalette.AKAR_TANAH));
					sink.setIfAbsent(x, y - length - 1, z, Palette.st(AwanPalette.LUMUT_GANTUNG));
				}
				if (spore && deepestY != Integer.MAX_VALUE) {
					int x = tipX + 3;
					int z = tipZ;
					if (PulauAwan.exists(layout, p, x, z, 3)) {
						int top = PulauAwan.levelOf(layout, p, x, z) - 1;
						sink.set(x, PulauAwan.bottomOf(p, x, z, top) - 1, z, Palette.st(Blocks.SPORE_BLOSSOM));
					}
				}
				sink.phase(previous);
			}
		};
	}

	// ================================================================== landscaping

	/**
	 * Trees, rocks and flowers scattered over the free cells of a ring (or a satellite), deterministic, clear of every
	 * plot, bridge, dock and rim feature by a margin: pines lean at the rim, plums and ginkgos on the plateau, maples on
	 * the terraces, scholar rocks and azaleas between.
	 */
	public static Part taman(String island, int ring, int every) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				AwanLayout layout = AwanLayout.of(ctx.template());
				AwanLayout.Pulau p = PulauAwan.islandOf(layout, island);
				int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
				return new BoundingBox(p.cx - reach, p.level - 6, p.cz - reach, p.cx + reach, p.level + 16, p.cz + reach);
			}

			@Override
			public String kind() {
				return "landscape";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				AwanLayout layout = AwanLayout.of(ctx.template());
				AwanLayout.Pulau p = PulauAwan.islandOf(layout, island);
				for (int[] spot : spots(layout, p, ring, every)) {
					int x = spot[0];
					int z = spot[1];
					int kind = spot[2];
					int level = PulauAwan.levelOf(layout, p, x, z);
					Lv lv = Lv.at(level);
					switch (kind) {
						case 0 -> new Tree(Tree.Kind.PINE, x, z, lv).force(true).within(x - 3, z - 3, x + 3, z + 3).emit(ctx, sink);
						case 1 -> new Tree(Tree.Kind.PLUM, x, z, lv).force(true).within(x - 3, z - 3, x + 3, z + 3).emit(ctx, sink);
						case 2 -> new Tree(Tree.Kind.GINKGO, x, z, lv).force(true).within(x - 2, z - 2, x + 2, z + 2).emit(ctx, sink);
						case 3 -> new Tree(Tree.Kind.MAPLE, x, z, lv).force(true).red(true).within(x - 2, z - 2, x + 2, z + 2).emit(ctx, sink);
						case 4 -> dev.kultivator.sect.build.blueprint.Rock.scholar(x, z, lv, 2 + Math.floorMod(x + z, 3)).emit(ctx, sink);
						default -> {
							Phase previous = sink.phase();
							sink.phase(Phase.GARDEN);
							sink.setIfAbsent(x, level, z, Palette.st(Math.floorMod(x * 3 + z, 2) == 0 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA));
							sink.phase(previous);
						}
					}
				}
			}
		};
	}

	/** The scatter spots {x, z, kind} of a ring: a jittered grid, clear of plots, bridges, docks, rims and features. */
	static List<int[]> spots(AwanLayout layout, AwanLayout.Pulau p, int ring, int every) {
		List<int[]> list = new ArrayList<>();
		int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
		for (int gx = p.cx - reach; gx <= p.cx + reach; gx += every) {
			for (int gz = p.cz - reach; gz <= p.cz + reach; gz += every) {
				double jx = p.shape.roll(gx, 0, gz, 21);
				double jz = p.shape.roll(gx, 0, gz, 22);
				int x = gx + (int) Math.floor(jx * (every - 2));
				int z = gz + (int) Math.floor(jz * (every - 2));
				int r = p.main ? p.ring(x, z) : 0;
				if (!PulauAwan.exists(layout, p, x, z, 3) || r != ring || !clear(layout, p, x, z)) {
					continue;
				}
				double n = p.norm(x, z) / p.radius();
				double pick = p.shape.roll(x, 1, z, 23);
				int kind;
				if (n > 0.86D) {
					kind = pick < 0.6D ? 0 : pick < 0.8D ? 4 : 5;
				} else if (p.main && ring == 3) {
					kind = pick < 0.35D ? 3 : pick < 0.6D ? 0 : pick < 0.8D ? 4 : 5;
				} else {
					kind = pick < 0.3D ? 1 : pick < 0.5D ? 2 : pick < 0.7D ? 0 : pick < 0.85D ? 4 : 5;
				}
				list.add(new int[]{x, z, kind});
			}
		}
		return list;
	}

	private static boolean clear(AwanLayout layout, AwanLayout.Pulau p, int x, int z) {
		int margin = 4;
		for (AwanLayout.Box box : layout.plots.values()) {
			if (p.main && x >= box.x0() - margin && x <= box.x1() + margin && z >= box.z0() - margin && z <= box.z1() + margin) {
				return false;
			}
		}
		for (AwanLayout.Jembatan b : layout.bridges) {
			if (b.covers(x, z, margin)) {
				return false;
			}
		}
		for (AwanLayout.Dermaga d : layout.docks) {
			if (x >= d.deck().x0() - margin && x <= d.deck().x1() + margin && z >= d.deck().z0() - margin && z <= d.deck().z1() + margin + 8) {
				return false;
			}
		}
		List<AwanLayout.Titik> spots = new ArrayList<>(layout.meditasi);
		spots.addAll(layout.takik);
		spots.addAll(layout.jaring);
		spots.add(layout.panggung);
		spots.add(layout.halte);
		for (AwanLayout.Titik t : spots) {
			if (Math.abs(x - t.x()) <= 6 && Math.abs(z - t.z()) <= 6) {
				return false;
			}
		}
		if (p.main && layout.frontBand(x, z) && Math.abs(x) <= 6) {
			return false;
		}
		if (p.main && layout.hillR > 0 && Math.hypot(x - layout.hillX, z - layout.hillZ) <= layout.hillR + 2) {
			return false;
		}
		// Not on a cliff edge: every neighbour within 2 stands on the same level.
		int level = PulauAwan.levelOf(layout, p, x, z);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (!PulauAwan.exists(layout, p, x + dx, z + dz, 3) || PulauAwan.levelOf(layout, p, x + dx, z + dz) != level) {
					return false;
				}
			}
		}
		return true;
	}

	/** Unused helper kept for parts that need a plain state (GARDEN azalea). */
	static BlockState azalea() {
		return Palette.st(Blocks.AZALEA);
	}

	private AwanHias() {
	}
}
