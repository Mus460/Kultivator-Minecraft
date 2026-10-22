package dev.kultivator.sekte.tangga;

import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Marks;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.TemplateExtensions;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.TerrainZone;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The building of Ujian Tangga Seribu (DESIGN 36.2.4, contract C36.G4): component {@code sx_tangga_batu}, added to
 * templates 2-9 through {@link TemplateExtensions} and built by the sect engine from deposited materials like every
 * other component (never a block placed at runtime). Two "Batu Hati" stelae (1x1, three carved stones and a jade
 * lantern, on their own little plinth where nothing else paves the cell) flank the axis at the sect's front, the
 * {@code tangga_proyektor} anchor marks where the illusory stair leaves the gate, {@code tangga_baris_1..6} the cells
 * where the candidates line up, {@code tangga_batu_kiri/kanan} the lanterns the projection beams come from.
 * <p>
 * The cells were chosen per template with a dry run of all nine tiers (module G's harness): nothing any other component
 * places, demolishes or keeps touches the stelae's cells, from tier 1 to the T9 finale. Where the outermost gate leaves
 * no band before the footprint edge (templates 2 and 3) the stelae stand in the court behind it; where the gate's
 * forecourt is first paved at tier 4 (template 6) the component waits for that tier so the stones never float.
 * Template 1 has no component: its climb projects from the gate (the {@code arrival} anchor) once the site reaches
 * tier 2. The 3x3 projector inlay of the first draft is left out: on every template the axis paving of another
 * component owns those cells, and the plan must not overwrite it.
 */
public final class BatuHatiKomponen {

	/** The component id ({@code sx_tangga_batu}), stable forever. */
	public static final String ID = SekteIds.componentId(SekteIds.G, "batu");
	public static final String ANCHOR_PROJECTOR = "tangga_proyektor";
	public static final String ANCHOR_LINE = "tangga_baris_";
	public static final String ANCHOR_LEFT = "tangga_batu_kiri";
	public static final String ANCHOR_RIGHT = "tangga_batu_kanan";
	/** Lineup cells (anchors {@code tangga_baris_1..LINE}). */
	public static final int LINE = 6;
	/** Height of a stela with its lantern (blocks above its standing level). */
	public static final int HEIGHT = 4;

	/**
	 * One template's placement: stelae at (±{@code x}, {@code z}) on {@code level}; {@code ground} true when nothing
	 * paves those cells (the stela gets a 1x1 earthwork column and a plinth); the candidates line up at
	 * {@code lineZ}; the projector stands at (0, {@code projZ}) on {@code projLevel}; {@code tier} of the component.
	 */
	private record Site(int x, int z, Lv level, boolean ground, int lineZ, int projZ, Lv projLevel, int tier) {
	}

	/** The placements of templates 2-9 (index = template), from the harness's free-cell grids. */
	private static @Nullable Site site(int template) {
		return switch (template) {
			// T2 Sekte Kelas Tiga: the gate hall fills z 10-14; the stelae frame the passage in the court (axis paving t1).
			case 2 -> new Site(2, 9, Lv.PLAZA, false, 8, 14, Lv.PLAZA, 2);
			// T3 Sekte Kelas Dua: the gate hall fills z 13-18; the stelae stand in the court on the t1 axis paving.
			case 3 -> new Site(2, 10, Lv.PLAZA, false, 8, 18, Lv.PLAZA, 2);
			// T4 Sekte Kelas Satu: the band outside the white wall's gate, beside the axis paving (natural ground).
			case 4 -> new Site(3, 23, Lv.PLAZA, true, 22, 24, Lv.PLAZA, 2);
			// T5 Sekte Besar: on the fitted approach between the gate steps and the white paifang (pillars at x ±3).
			case 5 -> new Site(3, 26, Lv.zone("approach"), false, 25, 30, Lv.zone("approach"), 2);
			// T6 Tanah Suci: on the shanmen's forecourt, paved at tier 4, before the paifang.
			case 6 -> new Site(3, 33, Lv.PLAZA, false, 32, 36, Lv.PLAZA, 4);
			// T7 Gua Surga: at the footprint's edge beyond the paifang, on the fitted court (natural ground).
			case 7 -> new Site(3, 44, Lv.zone("court"), true, 43, 44, Lv.zone("court"), 2);
			// T8 Garis Leluhur Kuno: at the edge of the approach beyond the moat paifang (natural ground).
			case 8 -> new Site(3, 52, Lv.zone("approach"), true, 51, 52, Lv.zone("approach"), 2);
			// T9 Istana Langit: at the foot of the fitted approach, outside the great paifang (natural ground).
			case 9 -> new Site(3, 59, Lv.zone("approach"), true, 58, 60, Lv.zone("approach"), 2);
			default -> null;
		};
	}

	/** The tier of the component in {@code template} (2 or 4), 0 when the template has none. */
	public static int tierOf(int template) {
		Site site = site(template);
		return site == null ? 0 : site.tier();
	}

	/** Registers the extension of templates 2-9 (module G's init, before the first {@code Templates.get}). */
	public static void register() {
		for (int template = 2; template <= 9; template++) {
			int t = template;
			TemplateExtensions.register("sekte:" + SekteIds.G, t, builder -> add(builder, t));
		}
	}

	private static void add(TemplateSpec.Builder builder, int template) {
		Site site = site(template);
		if (site == null || builder.hasComponent(ID)) {
			return;
		}
		Component.Builder c = Component.of(ID, site.tier()).kind("stele").name(ID).group(Component.Group.ORNAMENT);
		for (int side : new int[]{-1, 1}) {
			int x = side * site.x();
			if (site.ground()) {
				// A 1x1 earthwork column under the stela: natural ground cut to its level and filled below it.
				c.add(new TerrainZone(x, site.z(), x, site.z(), site.level()).edge(TerrainZone.Edge.NONE).surface(TerrainZone.Surface.NONE)
						.clearHeight(HEIGHT + 2));
			}
			c.add(stela(x, site.z(), site.level(), site.ground()));
			c.add(Marks.anchor(side < 0 ? ANCHOR_LEFT : ANCHOR_RIGHT, x, site.z(), site.level()));
		}
		c.add(Marks.anchor(ANCHOR_PROJECTOR, 0, site.projZ(), site.projLevel()));
		int[] lineX = {-1, 1, -2, 2, -3, 3};
		for (int i = 0; i < LINE; i++) {
			c.add(Marks.anchor(ANCHOR_LINE + (i + 1), lineX[i], site.lineZ(), site.level()));
		}
		builder.add(c);
	}

	/**
	 * One Batu Hati: a chiseled-tuff base, a carved stone-brick drum, the jade "heart" stone (waxed oxidised chiseled
	 * copper) with the trial's verse, a jade lantern on top; on a polished-andesite plinth replacing the floor cell when
	 * {@code plinth}. Facing the arriving candidates (+z).
	 */
	private static Part stela(int x, int z, Lv level, boolean plinth) {
		Stamp stamp = Stamp.of("batu_hati").floorLayer(plinth).phase(Phase.FRAME);
		if (plinth) {
			stamp.layer("P");
		}
		stamp.layer("B").layer("C").layer("J")
				.legend('P', Palette.st(Palette.PLINTH))
				.legend('B', Palette.st(Palette.PILLAR_BASE))
				.legend('C', Palette.st(Blocks.CHISELED_STONE_BRICKS))
				.legend('J', Palette.st(Palette.jade(Blocks.CHISELED_COPPER)));
		Part body = stamp.at(x, z, Direction.SOUTH, level);
		Part lantern = new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level) + 3;
				return new BoundingBox(x, y, z, x, y, z);
			}

			@Override
			public String kind() {
				return "lantern";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				Phase previous = sink.phase();
				sink.phase(Phase.ORNAMENT).set(x, ctx.level(level) + 3, z, Palette.lantern(Palette.jade(Blocks.COPPER_LANTERN), false));
				sink.phase(previous);
			}
		};
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return BoundingBox.encapsulating(body.bounds(ctx), lantern.bounds(ctx));
			}

			@Override
			public String kind() {
				return "batu_hati";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				body.emit(ctx, sink);
				lantern.emit(ctx, sink);
			}
		};
	}

	private BatuHatiKomponen() {
	}
}
