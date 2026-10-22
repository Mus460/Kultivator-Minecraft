package dev.kultivator.sekte.seragam;

import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.TemplateExtensions;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The flagpoles of the sect banners (DESIGN 36.2.8, contract C36.S3), two template components added to all nine
 * templates through {@link TemplateExtensions} (DESIGN 36.3.5), so they are billed from deposited materials, scheduled
 * and built by the engine like every other component; a site already past their tier builds them as "Tambahan":
 * <ul>
 * <li>{@code sx_seragam_panji} (tier 1): two poles flanking the approach in module S's plot of the front band
 * (4 ≤ |x| ≤ 8), outside the outermost gate where the template leaves room (T3, T6-T9) or just inside it (T1, T2, T4,
 * T5); anchors {@code panji_gerbang_0} (east) and {@code panji_gerbang_1} (west).</li>
 * <li>{@code sx_seragam_panji_formasi} (tier 4): four poles standing just outside the Bagua formation disc's inlay
 * (two mirrored pairs, radius r+1..r+4 from the lodestone); anchors {@code panji_formasi_0..3}.</li>
 * </ul>
 * A pole is one block wide: a carved stone foot, five lengths of red-lacquered cinnabar wall and a gilt cap, 7 blocks in
 * all (11 blocks of material). The anchor is the gilt cap; the cloth, the spear-tip finial and the tassels are drawn by
 * the client ({@code client.sekte.seragam.BenderaRenderer}) from the hall's emblem, never as blocks.
 * <p>
 * Every cell was chosen with the dry-run harness (all components of the template emitted at design levels): the whole
 * pole column (standing level .. +8) is free of every other component's blocks and air ops, it is not one of the four duty
 * posts, it lies inside a zone (the pole stands on that zone's fitted level) and inside module S's plot. Re-check with
 * {@code /kultivasi uji sekte audit <n>} and {@code uji sekte pamer <n> tingkat <t>} in game.
 */
public final class PanjiKomponen {

	public static final String GATE_ID = SekteIds.componentId(SekteIds.S, "panji");
	public static final String DISC_ID = SekteIds.componentId(SekteIds.S, "panji_formasi");
	public static final int GATE_TIER = 1;
	public static final int DISC_TIER = 4;
	/** Anchor prefixes (the anchor index follows). */
	public static final String GATE_ANCHOR = "panji_gerbang_";
	public static final String DISC_ANCHOR = "panji_formasi_";
	/** Anchors of module T's arena banners (C36.T4), drawn by S when they exist. */
	public static final String ARENA_ANCHOR = "panji_arena_";
	public static final int GATE_POLES = 2;
	public static final int DISC_POLES = 4;
	public static final int ARENA_POLES = 8;
	/** Pole height in blocks (foot, five shaft lengths, cap). */
	public static final int HEIGHT = 7;

	/** One pole: template-local (x, z) on the level of {@code zone} (null = plaza). */
	private record Spot(int x, int z, String zone) {
	}

	/** The gate pair of template 1..9 (east pole first; the west one mirrors it). */
	private static final Spot[] GATE = {
			new Spot(4, 6, null),       // T1: the court is full up to the fence; the pair frames the front of the disc
			new Spot(5, 9, null),       // T2: just inside the bamboo gate (z 12)
			new Spot(8, 17, null),      // T3: outside the gate hall (z 15), before the footprint edge
			new Spot(7, 16, null),      // T4: between the inner gate (z 14) and the red palace wall (z 20)
			new Spot(7, 15, null),      // T5: the open court before the red palace wall (z 20)
			new Spot(8, 33, null),      // T6: between the gate hall (z 25) and the white paifang (z 35)
			new Spot(8, 43, "court"),   // T7: outside the white paifang (z 41)
			new Spot(8, 51, "approach"), // T8: outside the red paifang (z 49)
			new Spot(8, 59, "approach") // T9: outside the great paifang (z 56), on the sunken approach
	};

	/** The two mirrored pairs around the formation disc of template 1..9 (offsets from the stele, east side). */
	private static final Spot[][] DISC = {
			{new Spot(4, -2, null), new Spot(5, 2, null)},     // T1 d2 r4
			{new Spot(3, 0, null), new Spot(6, 4, null)},      // T2 d6 r4
			{new Spot(4, 2, null), new Spot(4, 10, null)},     // T3 d6 r5
			{new Spot(4, 2, null), new Spot(4, 12, null)},     // T4 d6 r5
			{new Spot(8, 3, null), new Spot(6, 12, null)},     // T5 d6 r5
			{new Spot(9, 1, null), new Spot(5, 15, null)},     // T6 d6 r7
			{new Spot(7, -3, null), new Spot(7, 15, null)},    // T7 d6 r8
			{new Spot(6, -3, null), new Spot(6, 15, null)},    // T8 d6 r8
			{new Spot(9, -3, null), new Spot(10, 13, null)}    // T9 d6 r9
	};

	private static boolean registered;

	/** From {@code Seragam.init()} (and the harness): adds both components to every template. Safe to call twice. */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		TemplateExtensions.register("seragam", 0, PanjiKomponen::extend);
	}

	private static void extend(TemplateSpec.Builder builder) {
		int t = builder.template();
		if (t < 1 || t > 9) {
			return;
		}
		if (!builder.hasComponent(GATE_ID)) {
			Spot spot = GATE[t - 1];
			Lv level = level(builder, spot.zone());
			if (level != null) {
				builder.add(Component.of(GATE_ID, GATE_TIER).kind("panji").name("sekte_seragam_panji").group(Component.Group.ORNAMENT)
						.add(new Pole(GATE_ANCHOR + 0, spot.x(), spot.z(), level, false))
						.add(new Pole(GATE_ANCHOR + 1, -spot.x(), spot.z(), level, false)));
			}
		}
		if (!builder.hasComponent(DISC_ID)) {
			Component.Builder disc = Component.of(DISC_ID, DISC_TIER).kind("panji").name("sekte_seragam_panji_formasi").group(Component.Group.ORNAMENT);
			int n = 0;
			for (Spot spot : DISC[t - 1]) {
				Lv level = level(builder, spot.zone());
				if (level == null) {
					continue;
				}
				disc.add(new Pole(DISC_ANCHOR + n++, spot.x(), spot.z(), level, true));
				disc.add(new Pole(DISC_ANCHOR + n++, -spot.x(), spot.z(), level, true));
			}
			if (n > 0) {
				builder.add(disc);
			}
		}
	}

	private static Lv level(TemplateSpec.Builder builder, String zone) {
		if (zone == null) {
			return Lv.PLAZA;
		}
		return builder.zoneSoFar(zone) == null ? null : Lv.zone(zone);
	}

	/** True when {@code id} is one of S's banner anchors (gate, formation) or T's arena banners. */
	public static boolean isBannerAnchor(String id) {
		return id != null && (id.startsWith(GATE_ANCHOR) || id.startsWith(DISC_ANCHOR) || id.startsWith(ARENA_ANCHOR));
	}

	/** One flagpole: foot, shaft and gilt cap; the anchor names the cap cell. */
	private static final class Pole implements Part {
		private final String anchor;
		private final int x;
		private final int z;
		private final Lv level;
		private final boolean formation;

		Pole(String anchor, int x, int z, Lv level, boolean formation) {
			this.anchor = anchor;
			this.x = x;
			this.z = z;
			this.level = level;
			this.formation = formation;
		}

		@Override
		public BoundingBox bounds(Ctx ctx) {
			int y = ctx.level(level);
			return new BoundingBox(x, y, z, x, y + HEIGHT - 1, z);
		}

		@Override
		public String kind() {
			return "panji";
		}

		@Override
		public void emit(Ctx ctx, Sink sink) {
			int y = ctx.level(level);
			Phase previous = sink.phase();
			sink.phase(Phase.ORNAMENT);
			// the foot: carved stone at the gate, chiseled tuff bricks (the disc's own rim stone) at the formation
			Block foot = formation ? Palette.STONE_FRET : Palette.STONE_PILASTER;
			sink.set(x, y, z, Palette.st(foot));
			for (int i = 1; i < HEIGHT - 1; i++) {
				sink.set(x, y + i, z, Palette.st(Palette.PILLAR_THIN));
			}
			sink.set(x, y + HEIGHT - 1, z, Palette.st(Palette.GILT_CAP));
			sink.anchor(anchor, x, y + HEIGHT - 1, z);
			sink.phase(previous);
		}
	}

	private PanjiKomponen() {
	}
}
