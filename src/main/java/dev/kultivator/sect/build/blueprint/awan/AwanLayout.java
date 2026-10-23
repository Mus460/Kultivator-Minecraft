package dev.kultivator.sect.build.blueprint.awan;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole archipelago of one Awan template as a pure function of its {@link AwanTabel} row (DESIGN 37.4.1-37.4.4):
 * the main island's growth rings, the satellites, the jade bridges, the docks, the stair foot, the rim features and the
 * building plots. No world, no seed: the island shapes ({@link AwanShape}) are seeded by the template and island ids, so
 * every part and the island terrain read exactly the same rims. Built once per template, safe on any thread.
 *
 * <h2>Growth (template-local, stele at (0,0), +z toward the gate)</h2>
 * The main island is centred on (0, {@link #cz}); a cell belongs to ring k when its normalised radius
 * {@code ρ' = ρ / rim(θ)} is at most {@code ring[k]}:
 * <ul>
 * <li>ring 0, tier 1 "Inti Awan": the plaza, the Aula Pendiri, the gate, the Cloud Transfer node;</li>
 * <li>ring 1, tier 2 "Dataran": the dorm, kitchen, ancestral altar and (T3) the library;</li>
 * <li>ring 2, tier 4 "Pelebaran": the main hall (T6), the pagoda hill (T8), the mine (T4), the drill ground (T7)...;</li>
 * <li>ring 3, tier 6 "Tepi": the terraces that step down to the rim (1-2 levels), flat in the front band so the axis runs
 * level to the gate; the docks, the sword terrace, the ferry stop and the meditation pavilions stand on its rim.</li>
 * </ul>
 * The plateau (rings 0-2) is level 0 except the pagoda hill. Satellites stand in two columns east and west, each joined by
 * an axis-aligned jade bridge that leaves the plateau rim existing at its tier; the docks lie at the back.
 */
public final class AwanLayout {

	/** The satellite functions in the order they appear (DESIGN 37.4.3). */
	public enum Fungsi {
		KEBUN(2, "kebun", 1, 0, 4),
		ALKIMIA(4, "alkimia", -1, 0, -4),
		LELUHUR(8, "leluhur", 1, 1, 8),
		ARENA(5, "arena", -1, 1, -6),
		ASRAMA(8, "asrama", 1, 2, 0),
		TAMBANG(9, "tambang", -1, 2, -8);

		/** The tier the satellite (and its bridge) is built. */
		public final int tier;
		public final String id;
		/** +1 east, -1 west. */
		public final int side;
		/** Slot in its side column, front to back. */
		public final int slot;
		/** Standing level wished (clamped by the bridge slope). */
		public final int level;

		Fungsi(int tier, String id, int side, int slot, int level) {
			this.tier = tier;
			this.id = id;
			this.side = side;
			this.slot = slot;
			this.level = level;
		}
	}

	/** An inclusive template-local rectangle. */
	public record Box(int x0, int z0, int x1, int z1) {
		public Box {
			int ax = Math.min(x0, x1);
			int bx = Math.max(x0, x1);
			int az = Math.min(z0, z1);
			int bz = Math.max(z0, z1);
			x0 = ax;
			x1 = bx;
			z0 = az;
			z1 = bz;
		}

		public static Box around(int x, int z, int hx, int hz) {
			return new Box(x - hx, z - hz, x + hx, z + hz);
		}

		public boolean overlaps(Box o, int margin) {
			return x0 - margin <= o.x1 && o.x0 <= x1 + margin && z0 - margin <= o.z1 && o.z0 <= z1 + margin;
		}

		public boolean contains(int x, int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}

		public int cx() {
			return (x0 + x1) / 2;
		}

		public int cz() {
			return (z0 + z1) / 2;
		}
	}

	/**
	 * One island: its standing level, its growth rings (nominal radii with their tiers, each ring a disc around its own
	 * centre (cx, ringCz[k]); a cell belongs to the first disc that holds it), belly depth and shape.
	 */
	public static final class Pulau {
		public final String id;
		public final int cx;
		/** The centre of the outermost ring (the belly's centre). */
		public final int cz;
		public final int level;
		public final double[] rings;
		public final int[] ringCz;
		public final int[] tiers;
		public final int depth;
		public final AwanShape shape;
		public final boolean main;

		Pulau(String id, int cx, int[] ringCz, int level, double[] rings, int[] tiers, int depth, AwanShape shape, boolean main) {
			this.id = id;
			this.cx = cx;
			this.ringCz = ringCz;
			this.cz = ringCz[ringCz.length - 1];
			this.level = level;
			this.rings = rings;
			this.tiers = tiers;
			this.depth = depth;
			this.shape = shape;
			this.main = main;
		}

		Pulau(String id, int cx, int cz, int level, double r, int tier, int depth, AwanShape shape) {
			this(id, cx, new int[]{cz}, level, new double[]{r}, new int[]{tier}, depth, shape, false);
		}

		/** The outer nominal radius. */
		public double radius() {
			return rings[rings.length - 1];
		}

		/** ρ' of a cell relative to the outermost ring's centre. */
		public double norm(int x, int z) {
			return shape.norm(x - cx, z - cz);
		}

		/** ρ' of a cell relative to ring k's centre. */
		public double norm(int k, int x, int z) {
			return shape.norm(x - cx, z - ringCz[k]);
		}

		/** The ring index of a cell, -1 outside the island. */
		public int ring(int x, int z) {
			for (int k = 0; k < rings.length; k++) {
				if (norm(k, x, z) <= rings[k]) {
					return k;
				}
			}
			return -1;
		}

		/** The real distance of the ring-k rim from ring k's centre along angle theta. */
		public double rimAt(int k, double theta) {
			return rings[k] * shape.rim(theta);
		}

		/** The lowest y of the belly (template-local, plaza floor = -1), from the centre column. */
		public int deepest() {
			return level - 1 - (int) Math.ceil(depth * 1.15D + 2.0D);
		}
	}

	/** A satellite: its function, island and display name key suffix. */
	public record Satelit(Fungsi fungsi, Pulau pulau, int r) {
		public int tier() {
			return fungsi.tier;
		}

		/** The direction from the satellite toward the main island (where its bridge lands). */
		public Direction toMain() {
			return fungsi.side > 0 ? Direction.WEST : Direction.EAST;
		}
	}

	/**
	 * An axis-aligned jade bridge from the plateau rim (xA, zA) at level 0 to the satellite rim (xB, zB) at
	 * {@code levelB}; the deck stays level until {@code flat} cells from A (past the outer terraces), then slopes 1:2.
	 */
	public record Jembatan(String id, int tier, int xA, int zA, int xB, int zB, int levelA, int levelB, int flat) {
		public boolean alongX() {
			return zA == zB;
		}

		public int length() {
			return Math.abs(xB - xA) + Math.abs(zB - zA);
		}

		/** True when (x, z) lies within {@code half} cells of the bridge's centre line. */
		public boolean covers(int x, int z, int half) {
			if (alongX()) {
				return Math.abs(z - zA) <= half && x >= Math.min(xA, xB) && x <= Math.max(xA, xB);
			}
			return Math.abs(x - xA) <= half && z >= Math.min(zA, zB) && z <= Math.max(zA, zB);
		}

		/**
		 * The deck surface at step i (0..length) in half blocks above y 0: level until {@code flat}, then one half block per
		 * cell toward {@code levelB} (1:2), then level again.
		 */
		public int halfBlocks(int i) {
			int d = levelB - levelA;
			int k = Math.max(0, Math.min(i - flat, Math.abs(d) * 2));
			return 2 * levelA + Integer.signum(d) * k;
		}
	}

	/** A dock (DESIGN 37.5.4): {@code kelas} 0 PERAHU, 1 SEKTE, 2 BAHTERA; the deck x0..x1 × z0..z1 at {@code level}. */
	public record Dermaga(int index, int kelas, Box deck, Direction out, int level) {
		/** The anchor id {@code dermaga_<i>_<p|s|b>_<dir>} (template-local direction letter). */
		public String anchor() {
			return "dermaga_" + index + "_" + "psb".charAt(kelas) + "_" + letter(out);
		}
	}

	/** A rim feature: where it stands, the direction out of the island, its level. */
	public record Titik(String id, int x, int z, int level, Direction out) {
	}

	// ================================================================== fields

	public final AwanTabel row;
	public final int kelas;
	/** Plaza half width and front depth (the plaza is x -P..P, z -5..Pf). */
	public final int plaza;
	public final int plazaFront;
	public final int cz;
	public final Pulau main;
	public final List<Satelit> satellites = new ArrayList<>();
	public final List<Jembatan> bridges = new ArrayList<>();
	public final List<Dermaga> docks = new ArrayList<>();
	/** Waterfall clefts: the lip cell on the ring-2 rim, facing out. */
	public final List<Titik> takik = new ArrayList<>();
	public final List<Titik> meditasi = new ArrayList<>();
	/** The four Cloud Net poles on the ring-2 rim. */
	public final List<Titik> jaring = new ArrayList<>();
	public final Titik panggung;
	public final Titik halte;
	/** Kept free by the solver but not built on (the axis approach). */
	private final List<Box> reserved = new ArrayList<>();
	/** Building plots by name (template-local, the eave outline). */
	public final Map<String, Box> plots = new LinkedHashMap<>();
	/** A1 has no separate main hall (its Aula Pendiri is gilded at T6) and no pagoda. */
	public final boolean hasMainHall;
	public final boolean hasPagoda;
	/** The pagoda hill: centre, radius and height (0 = none). */
	public final int hillX;
	public final int hillZ;
	public final int hillR;
	public final int hillPad;
	/** The stair helix axis and the 9×9 foot square. */
	public final int stairX;
	public final int stairZ;
	public final Box kaki;
	/** The gate line (the T1 wooden gate) and the final rim front. */
	public final int gateZ;
	public final int frontZ;
	/** Half width of the level front band. */
	public final int frontBand;
	/** Terrace levels of ring 3 (outer part last). */
	public final int[] terraces;
	/** Where ring 3 splits into its two terraces (lapis 3), as a normalised radius. */
	public final double terraceSplit;
	public final int x0;
	public final int x1;
	public final int z0;
	public final int z1;
	public final int above;
	public final int below;
	/** The template-local y of the lowest planned block (belly, roots, crystals). */
	public final int skyFloor;
	public final int territory;

	private static final AwanLayout[] CACHE = new AwanLayout[9];

	/** The layout of sky template 11-19 (or sibling 1-9), built once. */
	public static AwanLayout of(int template) {
		AwanTabel row = AwanTabel.of(template);
		int i = row.n() - 1;
		AwanLayout layout = CACHE[i];
		if (layout == null) {
			synchronized (CACHE) {
				if (CACHE[i] == null) {
					CACHE[i] = new AwanLayout(row);
				}
				layout = CACHE[i];
			}
		}
		return layout;
	}

	// ================================================================== construction

	private AwanLayout(AwanTabel row) {
		this.row = row;
		this.kelas = row.kelas();
		this.plaza = switch (kelas) {
			case 0 -> 5;
			case 1 -> 8;
			default -> 9;
		};
		this.plazaFront = Math.max(kelas == 0 ? 7 : 11, row.discD() + row.discR() + 1);
		this.frontBand = plaza + 3;
		this.gateZ = plazaFront + 2;
		this.terraces = switch (row.lapis()) {
			case 1 -> new int[]{-1};
			case 2 -> new int[]{-2};
			default -> new int[]{-2, -4};
		};

		// ---- fixed plots (eave outlines), template-local
		int p = plaza;
		plots.put("plaza", new Box(-p, -5, p, plazaFront));
		plots.put("gate", new Box(-4, gateZ - 1, 4, gateZ + 1));
		this.hasMainHall = row.n() >= 2;
		this.hasPagoda = row.n() >= 2;
		switch (kelas) {
			case 0 -> {
				// A courtyard hermitage: the Aula Pendiri between the dorm and the library, all facing the gate.
				plots.put("aula", new Box(-6, -17, 6, -6));
				plots.put("asrama", new Box(-19, -15, -7, -3));
				plots.put("kitab", new Box(7, -16, 19, -2));
				plots.put("dapur", new Box(8, -1, 18, 9));
				plots.put("altar", new Box(-17, -21, -11, -17));
				if (hasMainHall) {
					plots.put("main_hall", new Box(-7, -35, 7, -19));
				}
			}
			case 1 -> {
				plots.put("aula", new Box(-9, -19, 9, -5));
				plots.put("asrama", new Box(-23, -10, -11, 4));
				plots.put("kitab", new Box(9, -10, 23, 4));
				plots.put("dapur", new Box(10, -24, 24, -12));
				plots.put("altar", new Box(-21, -21, -13, -14));
				plots.put("main_hall", new Box(-15, -45, 15, -21));
			}
			default -> {
				plots.put("aula", new Box(-12, -19, 12, -5));
				plots.put("asrama", new Box(-27, -11, -13, 5));
				plots.put("kitab", new Box(9, -12, 23, 6));
				plots.put("dapur", new Box(13, -27, 27, -14));
				plots.put("altar", new Box(-24, -24, -15, -16));
				plots.put("main_hall", new Box(-18, -54, 18, -21));
			}
		}
		// ---- flexible plots: the solver keeps them clear of every other plot and of the axis approach
		reserved.add(new Box(-4, gateZ - 1, 4, gateZ + 60));
		int nodeZ = plazaFront - 2;
		place("formasi_awan", -(p + 4), nodeZ, 2, 2, 1);
		place("jaring_mata", kelas == 0 ? 9 : p + 5, kelas == 0 ? plazaFront + 3 : nodeZ, 3, 3, 1);
		// The Cloud Stair's exit pavilion (T3): its helix comes up through the belly below it (S: behind the dorm).
		if (kelas == 0) {
			place("tangga", -12, -26, 4, 4, 1);
		} else {
			place("tangga", -(p + 11), plazaFront - 4, 5, 5, 1);
		}
		// What stands behind the Aula Pendiri: the main hall (or, for A1, the Aula itself, gilded at T6).
		Box hall = hasMainHall ? plots.get("main_hall") : plots.get("aula");
		// The pagoda hill (or the plain pagoda court) west of the main hall.
		int pagodaHalf = row.pagodaFloors() >= 7 ? 9 : row.pagodaFloors() >= 5 ? 8 : 6;
		int hillHalf = pagodaHalf + (row.bukit() > 0 ? 2 + row.bukit() / 4 : 0);
		if (hasPagoda) {
			place("pagoda", hall.x0() - hillHalf - 1, hall.cz() + 2, hillHalf, hillHalf, 1);
			Box pagoda = plots.get("pagoda");
			this.hillX = pagoda.cx();
			this.hillZ = pagoda.cz();
		} else {
			this.hillX = 0;
			this.hillZ = 0;
		}
		this.hillR = hasPagoda && row.bukit() > 0 ? hillHalf : 0;
		this.hillPad = pagodaHalf;
		if (hillR > 0 && row.n() >= 4) {
			// The garden pond at the foot of the hill, fed by a small real waterfall from a rock (contained).
			place("kolam", hillX + hillHalf + 4, hillZ + hillHalf - 3, 4, 3, 1);
		}
		if (kelas < 2) {
			place("tambang", hall.x1() + 6, hall.cz(), 3, 3, 1);
		}
		place("ding_0", hall.x0() - 3, hall.z1() - 2, 1, 1, 1);
		place("ding_1", hall.x1() + 3, hall.z1() - 2, 1, 1, 1);
		if (kelas == 0) {
			place("latihan", 14, hall.z0() + 4, 5, 4, 1);
		} else {
			place("latihan", p + 10, plazaFront - 2, kelas == 1 ? 6 : 8, 4, 1);
		}
		if (row.bell()) {
			place("lonceng", -(p + 9), plazaFront - 2, 3, 3, 1);
		}
		if (row.seat() > 0) {
			place("kursi_qi", hall.x1() + 5, hall.z0() + 3, 3, 3, 1);
		}
		for (int i = 0; i < row.gateAltars(); i++) {
			// The gate altars (13×13 daises) beside the approach beyond the gate, in pairs.
			place("altar_gerbang_" + i, (i % 2 == 0 ? -1 : 1) * 10, gateZ + 9 + (i / 2) * 15, 6, 6, 1);
		}

		// ---- rings of the main island: nested discs fitted around their plots, normalised by the lowest rim factor
		AwanShape mainShape = new AwanShape(seedOf(row), "main", 0.93D, 1.10D);
		double lowest = 0.93D;
		double[] fit0 = fit(lowest, 1, new String[]{"plaza", "gate", "aula", "formasi_awan"});
		double[] fit1 = fit(lowest, 1, new String[]{"plaza", "gate", "aula", "formasi_awan", "asrama", "kitab", "dapur", "altar", "tangga"});
		double[] fit2 = fit(lowest, 2, plots.keySet().toArray(new String[0]));
		// Nested: each disc holds the previous one (the island grows outward, never leaves a ring behind).
		fit1[1] = Math.max(fit1[1], Math.abs(fit1[0] - fit0[0]) + fit0[1] - 3.0D);
		fit2[1] = Math.max(fit2[1], Math.abs(fit2[0] - fit1[0]) + fit1[1] - 3.0D);
		double wTerrace = kelas == 0 ? 3.0D : kelas == 1 ? 4.0D : 6.0D;
		this.cz = (int) fit2[0];
		double rF = fit2[1] + wTerrace;
		this.terraceSplit = fit2[1] + wTerrace / 2.0D;
		int depth = (int) Math.round(0.45D * rF) + 4;
		this.main = new Pulau("main", 0, new int[]{(int) fit0[0], (int) fit1[0], cz, cz}, 0, new double[]{fit0[1], fit1[1], fit2[1], rF},
				new int[]{1, 2, 4, 6}, depth, mainShape, true);
		this.frontZ = (int) Math.floor(cz + main.rimAt(3, 0.0D));

		// ---- the stair helix: under the exit pavilion, inside ring 1 (it is tier 3)
		Box stair = plots.get("tangga");
		this.stairX = stair.cx();
		this.stairZ = stair.cz();
		int footHalf = kelas == 0 ? 4 : 5;
		this.kaki = Box.around(stairX, stairZ, footHalf, footHalf);

		// ---- satellites in two columns, bridges, docks
		buildSatellites(rF);
		buildDocks(rF);

		// ---- rim features
		this.panggung = rimFeature("panggung_pedang", Math.toRadians(32.0D), 3, kelas == 0 ? 5 : 7);
		this.halte = rimFeature("feri_atas", Math.toRadians(328.0D), 3, 5);
		for (int i = 0; i < row.meditasi(); i++) {
			Titik spot = freeRim("meditasi_" + i, 3, 5, 6.0D);
			if (spot != null) {
				meditasi.add(spot);
			}
		}
		for (int i = 0; i < row.airTerjun(); i++) {
			Titik lip = freeRim("airterjun_" + i, 2, 3, 7.0D);
			if (lip != null) {
				takik.add(lip);
			}
		}
		double[] poles = {45.0D, 135.0D, 225.0D, 315.0D};
		for (int i = 0; i < 4; i++) {
			jaring.add(nearestRim("jaring_" + i, Math.toRadians(poles[i]), 2, 2));
		}

		// ---- the lowest planned block (belly bottoms, then roots and crystals hang up to 9 below)
		int floor = 0;
		for (Pulau island : islands()) {
			int reach = (int) Math.ceil(island.radius() * 1.16D) + 1;
			for (int x = island.cx - reach; x <= island.cx + reach; x++) {
				for (int z = island.cz - reach; z <= island.cz + reach; z++) {
					if (PulauAwan.exists(this, island, x, z, 3)) {
						int top = PulauAwan.levelOf(this, island, x, z) - 1;
						floor = Math.min(floor, PulauAwan.bottomOf(island, x, z, top));
					}
				}
			}
		}
		this.skyFloor = floor - 9;

		// ---- footprint, envelope, territory
		int fx0 = Integer.MAX_VALUE;
		int fx1 = Integer.MIN_VALUE;
		int fz0 = Integer.MAX_VALUE;
		int fz1 = Integer.MIN_VALUE;
		List<Pulau> islands = islands();
		for (Pulau island : islands) {
			int reach = (int) Math.ceil(island.radius() * 1.16D) + 2;
			fx0 = Math.min(fx0, island.cx - reach);
			fx1 = Math.max(fx1, island.cx + reach);
			fz0 = Math.min(fz0, island.cz - reach);
			fz1 = Math.max(fz1, island.cz + reach);
		}
		for (Dermaga dock : docks) {
			fx0 = Math.min(fx0, dock.deck().x0() - 3);
			fx1 = Math.max(fx1, dock.deck().x1() + 3);
			fz0 = Math.min(fz0, dock.deck().z0() - 3);
			fz1 = Math.max(fz1, dock.deck().z1() + 3);
		}
		// The Gate of the Nine Heavens' terrace reaches 6 beyond the front rim; the sword terrace and the ferry stop 9.
		fz1 = Math.max(fz1, frontZ + 10);
		for (Titik t : new Titik[]{panggung, halte}) {
			fx0 = Math.min(fx0, t.x() - 12);
			fx1 = Math.max(fx1, t.x() + 12);
			fz1 = Math.max(fz1, t.z() + 12);
		}
		this.x0 = fx0;
		this.x1 = fx1;
		this.z0 = fz0;
		this.z1 = fz1;
		int deepest = 0;
		for (Pulau island : islands) {
			deepest = Math.min(deepest, island.deepest());
		}
		// Hanging roots, crystals and the turtle head reach below the deepest belly.
		this.below = Math.max(row.below(), Math.max(-deepest + 10, -skyFloor));
		int hillTop = row.bukit() + 5 * row.pagodaFloors() + 12;
		this.above = Math.max(row.above(), Math.max(hillTop, GerbangSembilanLangit.height(kelas) + 2));
		double reach = 0.0D;
		for (Pulau island : islands) {
			reach = Math.max(reach, Math.hypot(island.cx, island.cz) + island.radius() * 1.1D);
		}
		this.territory = Math.max(row.territory(), (int) Math.ceil(reach) + 4);
	}

	/** The fixed shape seed of a template (every island of a template has the same shape). */
	private static long seedOf(AwanTabel row) {
		return 0x5EC7A0A1L * 31L + row.id().hashCode();
	}

	/** Every island, the main one first. */
	public List<Pulau> islands() {
		List<Pulau> list = new ArrayList<>();
		list.add(main);
		for (Satelit s : satellites) {
			list.add(s.pulau());
		}
		return list;
	}

	public @Nullable Satelit satellite(Fungsi fungsi) {
		for (Satelit s : satellites) {
			if (s.fungsi() == fungsi) {
				return s;
			}
		}
		return null;
	}

	public @Nullable Jembatan bridge(Fungsi fungsi) {
		for (Jembatan b : bridges) {
			if (b.id().equals("jembatan_" + fungsi.id)) {
				return b;
			}
		}
		return null;
	}

	public Box plot(String name) {
		return plots.get(name);
	}

	/** The main island's standing level at (x, z) for its full plan (0 plateau, the hill, the terraces); no island = MIN. */
	public int mainLevel(int x, int z) {
		int ring = main.ring(x, z);
		if (ring < 0 || cleft(x, z) && ring >= 2) {
			return Integer.MIN_VALUE;
		}
		return levelInRing(x, z, ring);
	}

	/** The standing level of a main-island cell known to lie in {@code ring}. */
	public int levelInRing(int x, int z, int ring) {
		int level = 0;
		if (ring == 3 && !frontBand(x, z)) {
			double n = main.norm(x, z);
			level = terraces.length == 1 || n <= terraceSplit ? terraces[0] : terraces[1];
		}
		if (hillR > 0) {
			double d = Math.hypot(x - hillX, z - hillZ);
			if (d < hillR) {
				int h = d <= hillPad ? row.bukit() : (int) Math.round(row.bukit() * smooth(1.0D - (d - hillPad) / (hillR - hillPad)));
				level = Math.max(level, h);
			}
		}
		return level;
	}

	/** True for the level front band of the terrace ring (the axis runs flat from the plaza to the gate). */
	public boolean frontBand(int x, int z) {
		return Math.abs(x) <= frontBand && z > cz;
	}

	/** True inside a waterfall cleft (no island in rings 2-3 there). */
	public boolean cleft(int x, int z) {
		for (Titik lip : takik) {
			int dx = x - lip.x();
			int dz = z - lip.z();
			Direction out = lip.out();
			int along = dx * out.getStepX() + dz * out.getStepZ();
			int lateral = Math.abs(dx * out.getStepZ() - dz * out.getStepX());
			if (along >= 1 && lateral <= 1) {
				return true;
			}
		}
		return false;
	}

	/** True when (x, z) lies under the deck of a bridge built before {@code tier} (ring surfaces skip its top cell). */
	public boolean corridor(int x, int z, int tier) {
		for (Jembatan b : bridges) {
			if (b.tier() < tier && b.covers(x, z, 2)) {
				return true;
			}
		}
		return false;
	}

	/** The template-local direction letter of an anchor suffix. */
	public static char letter(Direction d) {
		return switch (d) {
			case NORTH -> 'n';
			case EAST -> 'e';
			case WEST -> 'w';
			default -> 's';
		};
	}

	// ================================================================== solver

	/** Places a plot near (x, z) with half sizes (hx, hz), clear of every plot by {@code margin}; spirals outward. */
	private void place(String name, int x, int z, int hx, int hz, int margin) {
		for (int ring = 0; ring <= 40; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					Box box = Box.around(x + dx, z + dz, hx, hz);
					if (free(box, margin)) {
						plots.put(name, box);
						return;
					}
				}
			}
		}
		plots.put(name, Box.around(x, z, hx, hz));
	}

	private boolean free(Box box, int margin) {
		for (Box other : plots.values()) {
			if (box.overlaps(other, margin)) {
				return false;
			}
		}
		for (Box other : reserved) {
			if (box.overlaps(other, margin)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The disc (centre z on the axis x = 0, nominal radius) holding the named plots with {@code margin}: the centre that
	 * minimises the farthest plot corner, the radius divided by the shape's lowest rim factor.
	 */
	private double[] fit(double lowest, int margin, String[] names) {
		int zMin = Integer.MAX_VALUE;
		int zMax = Integer.MIN_VALUE;
		for (String name : names) {
			Box box = plots.get(name);
			if (box != null) {
				zMin = Math.min(zMin, box.z0());
				zMax = Math.max(zMax, box.z1());
			}
		}
		double bestR = Double.MAX_VALUE;
		int bestZ = 0;
		for (int z = zMin; z <= zMax; z++) {
			double max = 0.0D;
			for (String name : names) {
				Box box = plots.get(name);
				if (box == null) {
					continue;
				}
				for (int[] c : new int[][]{{box.x0(), box.z0()}, {box.x1(), box.z0()}, {box.x0(), box.z1()}, {box.x1(), box.z1()}}) {
					max = Math.max(max, Math.hypot(c[0], c[1] - z) + margin + 0.5D);
				}
			}
			if (max < bestR) {
				bestR = max;
				bestZ = z;
			}
		}
		return new double[]{bestZ, bestR / lowest};
	}

	private void buildSatellites(double rF) {
		List<Fungsi> wanted = new ArrayList<>();
		for (Fungsi f : Fungsi.values()) {
			if (wanted.size() < row.satelit()) {
				wanted.add(f);
			}
		}
		for (int side : new int[]{1, -1}) {
			List<Fungsi> column = new ArrayList<>();
			for (Fungsi f : wanted) {
				if (f.side == side) {
					column.add(f);
				}
			}
			column.sort((a, b) -> Integer.compare(a.slot, b.slot));
			// The column is centred a little behind the island centre (the front holds the sword terrace and the ferry).
			int[] radii = new int[column.size()];
			double span = 0.0D;
			for (int i = 0; i < column.size(); i++) {
				radii[i] = radiusOf(column.get(i));
				span += radii[i] * 2.3D;
			}
			span += 6.0D * Math.max(0, column.size() - 1);
			double z = cz + span / 2.0D - rF * 0.12D;
			for (int i = 0; i < column.size(); i++) {
				Fungsi f = column.get(i);
				int r = radii[i];
				z -= r * 1.15D;
				int sz = (int) Math.round(z);
				z -= r * 1.15D + 6.0D;
				// The main rim's reach east or west along this satellite's band.
				double rimX = 0.0D;
				for (int dz = -r; dz <= r; dz++) {
					rimX = Math.max(rimX, rimReachX(sz + dz, side, 3));
				}
				int gap = 6 + 4 * (f.ordinal() % 2) + Math.max(0, Math.abs(f.level) - 4);
				int sx = side * (int) Math.round(rimX + gap + r * 1.12D);
				AwanShape shape = new AwanShape(seedOf(row), "sat_" + f.id, 0.88D, 1.12D);
				int plateauRing = f.tier < 4 ? 1 : 2;
				int startX = side * (int) Math.floor(rimReachX(sz, side, plateauRing));
				int endX = sx - side * ((int) Math.floor(r * shape.rim(side > 0 ? Math.PI * 1.5D : Math.PI * 0.5D)) - 1);
				int length = Math.abs(endX - startX);
				int flat = (int) Math.ceil(rimReachX(sz, side, 3)) + 1 - Math.abs(startX);
				int room = Math.max(0, length - Math.max(0, flat) - 1);
				int wish = kelas == 0 ? f.level / 2 : f.level;
				// The deck slopes 1:2 only past the outer terraces (it must clear them), so the room left caps the level.
				int level = Math.max(-room / 2, Math.min(room / 2, wish));
				int depth = (int) Math.round(1.3D * r) + 3;
				Pulau island = new Pulau("sat_" + f.id, sx, sz, level, r, f.tier, depth, shape);
				satellites.add(new Satelit(f, island, r));
				bridges.add(new Jembatan("jembatan_" + f.id, f.tier, startX, sz, endX, sz, 0, level, Math.max(0, flat)));
			}
		}
	}

	private int radiusOf(Fungsi f) {
		return switch (f) {
			case KEBUN -> new int[]{7, 9, 11}[kelas];
			case ALKIMIA -> new int[]{6, 8, 9}[kelas];
			case ARENA -> new int[]{9, 11, 13}[kelas];
			case LELUHUR -> new int[]{7, 9, 12}[kelas];
			case ASRAMA -> new int[]{8, 10, 11}[kelas];
			case TAMBANG -> new int[]{6, 7, 8}[kelas];
		};
	}

	/** How far ring {@code k} of the main island reaches toward {@code side} (x) along row z. */
	private double rimReachX(int z, int side, int k) {
		double reach = 0.0D;
		for (int x = 0; x <= (int) (main.radius() * 1.2D) + 2; x++) {
			if (main.ring(side * x, z) >= 0 && main.ring(side * x, z) <= k) {
				reach = x;
			}
		}
		return reach;
	}

	private void buildDocks(double rF) {
		int[][] sizes = {{9, 15}, {13, 27}, {19, 39}};
		int count = row.dermaga();
		int total = 0;
		for (int i = 0; i < count; i++) {
			total += sizes[i][0];
		}
		total += 4 * (count - 1);
		int x = -total / 2;
		// BAHTERA in the middle, the others beside it.
		int[] order = count == 3 ? new int[]{1, 2, 0} : count == 2 ? new int[]{0, 1} : new int[]{0};
		for (int k : order) {
			int w = sizes[k][0];
			int len = sizes[k][1];
			int cx = x + w / 2;
			int rimZ = (int) Math.ceil(rimReachMinusZ(cx, w));
			int zTop = rimZ + 2;
			int level = terraces[terraces.length - 1];
			docks.add(new Dermaga(docks.size(), k, new Box(x, zTop - len + 1, x + w - 1, zTop), Direction.NORTH, level));
			x += w + 4;
		}
	}

	/** The z of the main rim's back edge under a deck of width w centred on cx (the shallowest point, so the deck rests). */
	private double rimReachMinusZ(int cx, int w) {
		double best = Double.NEGATIVE_INFINITY;
		for (int x = cx - w / 2; x <= cx + w / 2; x++) {
			for (int z = cz; z >= cz - (int) (main.radius() * 1.2D) - 2; z--) {
				if (main.ring(x, z) < 0) {
					best = Math.max(best, z + 1);
					break;
				}
			}
		}
		return best;
	}

	/** A rim feature at angle theta on ring k (the outermost cell of that ring along the ray), facing out. */
	private Titik rimFeature(String id, double theta, int k, int clear) {
		return nearestRim(id, theta, k, clear);
	}

	private Titik nearestRim(String id, double theta, int k, int clear) {
		for (int step = 0; step < 60; step++) {
			for (int sign : new int[]{1, -1}) {
				double t = theta + sign * Math.toRadians(step * 2.0D);
				Titik spot = rimSpot(id, t, k);
				if (spot != null && clearOf(spot, clear)) {
					return spot;
				}
			}
		}
		Titik spot = rimSpot(id, theta, k);
		return spot != null ? spot : new Titik(id, 0, cz, 0, Direction.SOUTH);
	}

	private @Nullable Titik freeRim(String id, int k, int clear, double minGap) {
		double bestScore = -1.0D;
		Titik best = null;
		for (int deg = 0; deg < 360; deg += 3) {
			Titik spot = rimSpot(id, Math.toRadians(deg), k);
			if (spot == null || !clearOf(spot, clear)) {
				continue;
			}
			double score = minDistance(spot);
			if (score >= minGap && score > bestScore) {
				bestScore = score;
				best = spot;
			}
		}
		return best;
	}

	private @Nullable Titik rimSpot(String id, double theta, int k) {
		int centre = main.ringCz[k];
		double rho = main.rimAt(k, theta) - 0.5D;
		int x = (int) Math.round(Math.sin(theta) * rho);
		int z = centre + (int) Math.round(Math.cos(theta) * rho);
		for (int i = 0; i < 4 && main.ring(x, z) > k; i++) {
			rho -= 1.0D;
			x = (int) Math.round(Math.sin(theta) * rho);
			z = centre + (int) Math.round(Math.cos(theta) * rho);
		}
		if (main.ring(x, z) < 0 || main.ring(x, z) > k) {
			return null;
		}
		Direction out = Math.abs(Math.sin(theta)) > Math.abs(Math.cos(theta)) ? (Math.sin(theta) > 0 ? Direction.EAST : Direction.WEST)
				: (Math.cos(theta) > 0 ? Direction.SOUTH : Direction.NORTH);
		int level = k == 3 ? levelInRing(x, z, 3) : 0;
		return new Titik(id, x, z, level, out);
	}

	/** Clear of the front band, the bridges, the docks, the stair and every other rim feature. */
	private boolean clearOf(Titik spot, int clear) {
		if (Math.abs(spot.x()) <= frontBand + clear + 2 && spot.z() > cz) {
			return false;
		}
		for (Jembatan b : bridges) {
			if (Math.abs(spot.z() - b.zA()) <= clear + 3 && Integer.signum(spot.x()) == Integer.signum(b.xA()) && Math.abs(spot.x()) > Math.abs(b.xA()) - 8) {
				return false;
			}
		}
		for (Dermaga d : docks) {
			if (d.deck().overlaps(Box.around(spot.x(), spot.z(), clear, clear), 4)) {
				return false;
			}
		}
		if (Math.abs(spot.x() - stairX) <= 6 && Math.abs(spot.z() - stairZ) <= 6) {
			return false;
		}
		return minDistance(spot) >= clear * 2 + 3;
	}

	private double minDistance(Titik spot) {
		double min = Double.MAX_VALUE;
		List<Titik> all = new ArrayList<>(meditasi);
		all.addAll(takik);
		all.addAll(jaring);
		if (panggungSet()) {
			all.add(panggung);
		}
		if (halteSet()) {
			all.add(halte);
		}
		for (Titik t : all) {
			if (t != spot) {
				min = Math.min(min, Math.hypot(t.x() - spot.x(), t.z() - spot.z()));
			}
		}
		return min;
	}

	private boolean panggungSet() {
		return panggung != null;
	}

	private boolean halteSet() {
		return halte != null;
	}

	private static double smooth(double t) {
		double c = Math.max(0.0D, Math.min(1.0D, t));
		return c * c * (3.0D - 2.0D * c);
	}

	/** Every plot name (for the audit). */
	public List<String> plotNames() {
		return Collections.unmodifiableList(new ArrayList<>(plots.keySet()));
	}
}
