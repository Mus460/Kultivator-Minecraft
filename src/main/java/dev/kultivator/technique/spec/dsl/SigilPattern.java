package dev.kultivator.technique.spec.dsl;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Sigil Bumi patterns (DESIGN 28.9.1): pure geometry, plain common code that the server (Mode Kehancuran trenches,
 * 28.9.3) and the client (the glowing lines, 28.9.2) both call, so both get the same points from the same inputs.
 * <p>
 * Convention (C10.1): {@code centre} = the {@code tx,ty,tz} of the cast's last CAST event (the resolved aim; a charged
 * cast's release CAST), {@code yawDeg} = {@link #yawFor(Vec3, Vec3, long)} of that CAST's {@code x,y,z} and
 * {@code tx,ty,tz} with the TechFx {@code seed}. Every point lies at {@code centre.y}; the client lifts each vertex onto
 * its heightmap (+0.04), the server's trenches find the surface per column. Yaw follows Minecraft: 0 faces +Z, forward is
 * {@code (-sin yaw, 0, cos yaw)}, right is {@code (-cos yaw, 0, -sin yaw)}.
 * <p>
 * {@link Segment#node()} is the node whose ignition lights the line (28.9.2: one node and its incoming lines per event of
 * {@code Dampak.sigilIgnite}); -1 = a structure line that lights with the climax. {@link Segment#major()} marks the
 * straight lines Mode Kehancuran may burn as trenches.
 */
public enum SigilPattern {
	NONE,
	/** The seven Dipper stars plus the hidden attendants Zuofu and Youbi, joined in order. */
	BEIDOU_NINE,
	/** The 3 × 3 palaces of the Luo Shu and the path 1 → 9. */
	LUOSHU,
	/** Five nodes: the generating cycle as a pentagon, the overcoming cycle as a pentagram. */
	WUXING,
	/** An octagon with the eight trigram bars around a taiji circle. */
	BAGUA,
	/** Nine radial blade marks around the aim. */
	SWORD_RING,
	/** A square, its two diagonals and a glyph at each corner (誅 戮 陷 絕). */
	ZHUXIAN_SQUARE,
	/** Sixteen petal outlines in two rings. */
	LOTUS16,
	/** Six arms with branch ticks. */
	SNOWFLAKE,
	/** Nine concentric rings. */
	NINE_RINGS,
	/** Twelve hour ticks and two hands. */
	CLOCK12,
	/** Square cells of 2-3 blocks over the area. */
	GRID,
	/** Eight branching root lines from the centre. */
	ROOTS,
	/** An eight-pointed star joining the eight 封 glyphs. */
	OCTAGRAM_SEAL,
	/** A circle split by an S-curve. */
	LIFE_DEATH,
	/** No fixed geometry: the client joins the cast's own event positions in order. */
	EVENT_CHAIN,
	/** Feather chevrons along the dash line (from {@code radius} behind the centre to the centre). */
	WIND_PATH,
	/** The Dao's glyph ring (own arts). */
	DAO_CIRCLE;

	/** One line of a pattern in world coordinates. */
	public record Segment(Vec3 from, Vec3 to, int node, boolean major) {
		public double length() {
			return from.distanceTo(to);
		}
	}

	/** The glyphs drawn on the nodes (one character per node, cycling), or "" for none. */
	public String glyphs() {
		return switch (this) {
			case ZHUXIAN_SQUARE -> "誅戮陷絕";
			case OCTAGRAM_SEAL -> "封";
			case WUXING -> "木火土金水";
			default -> "";
		};
	}

	/** True when the pattern has star impacts on the climax (28.9.2: a pillar, a small ring and a crystal per node). */
	public boolean starImpacts() {
		return this == BEIDOU_NINE || this == WUXING || this == LUOSHU;
	}

	/** {@link #nodes(SigilPattern, Vec3, double, double, long)} of this pattern. */
	public List<Vec3> nodes(Vec3 centre, double yawDeg, double radius, long seed) {
		return nodes(this, centre, yawDeg, radius, seed);
	}

	/** {@link #segments(SigilPattern, Vec3, double, double, long)} of this pattern. */
	public List<Segment> segments(Vec3 centre, double yawDeg, double radius, long seed) {
		return segments(this, centre, yawDeg, radius, seed);
	}

	/**
	 * The yaw of a sigil: the horizontal angle of {@code aim − castPos} in Minecraft degrees, or {@code seed mod 360}
	 * when the two coincide horizontally (a caster-centred domain, a zone whose CAST sits on its centre).
	 */
	public static double yawFor(Vec3 castPos, Vec3 aim, long seed) {
		double dx = aim.x - castPos.x;
		double dz = aim.z - castPos.z;
		if (dx * dx + dz * dz < 0.25D) {
			return Math.floorMod(seed, 360L);
		}
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** The nodes (stars, palaces, corners, tips) in world coordinates, in ignition order. */
	public static List<Vec3> nodes(SigilPattern pattern, Vec3 centre, double yawDeg, double radius, long seed) {
		Frame frame = new Frame(centre, yawDeg, radius);
		List<Vec3> out = new ArrayList<>();
		for (double[] p : localNodes(pattern, radius)) {
			out.add(frame.at(p[0], p[1]));
		}
		return out;
	}

	/** Every line in world coordinates (at most a few hundred; the client caps by quality, 28.9.2). */
	public static List<Segment> segments(SigilPattern pattern, Vec3 centre, double yawDeg, double radius, long seed) {
		Frame frame = new Frame(centre, yawDeg, radius);
		List<Segment> out = new ArrayList<>();
		for (double[] s : localSegments(pattern, radius)) {
			out.add(new Segment(frame.at(s[0], s[1]), frame.at(s[2], s[3]), (int) s[4], s[5] > 0.5D));
		}
		return out;
	}

	/** The major (straight, trench-worthy) lines, at most {@code max}, nearest the centre first. */
	public static List<Segment> majorSegments(SigilPattern pattern, Vec3 centre, double yawDeg, double radius, long seed, int max) {
		List<Segment> all = new ArrayList<>();
		for (Segment segment : segments(pattern, centre, yawDeg, radius, seed)) {
			if (segment.major() && segment.length() >= 1.0D) {
				all.add(segment);
			}
		}
		all.sort(java.util.Comparator.comparingDouble(s -> s.from().add(s.to()).scale(0.5D).distanceToSqr(centre)));
		return all.size() > max ? List.copyOf(all.subList(0, Math.max(0, max))) : all;
	}

	// ================================================================== local geometry (unit radius; x right, z forward)

	private record Frame(Vec3 centre, double fx, double fz, double rx, double rz, double radius) {
		Frame(Vec3 centre, double yawDeg, double radius) {
			this(centre, -Math.sin(Math.toRadians(yawDeg)), Math.cos(Math.toRadians(yawDeg)), -Math.cos(Math.toRadians(yawDeg)),
					-Math.sin(Math.toRadians(yawDeg)), Math.max(0.0D, radius));
		}

		Vec3 at(double lx, double lz) {
			return new Vec3(centre.x + (rx * lx + fx * lz) * radius, centre.y, centre.z + (rz * lx + fz * lz) * radius);
		}
	}

	/** The Dipper (x, z), Dubhe first, then Zuofu and Youbi. */
	private static final double[][] BEIDOU = {
			{-0.95D, 0.30D}, {-0.88D, -0.18D}, {-0.38D, -0.28D}, {-0.33D, 0.12D}, {0.05D, 0.24D}, {0.45D, 0.30D}, {0.95D, 0.08D},
			{0.52D, 0.50D}, {0.12D, 0.52D}};
	/** Luo Shu palace of the numbers 1-9 (x, z): 4 9 2 / 3 5 7 / 8 1 6, north (far) on top. */
	private static final double[][] LUOSHU_AT = {
			{0.0D, -1.0D}, {1.0D, 1.0D}, {-1.0D, 0.0D}, {-1.0D, 1.0D}, {0.0D, 0.0D}, {1.0D, -1.0D}, {1.0D, 0.0D}, {-1.0D, -1.0D}, {0.0D, 1.0D}};
	/** Trigram lines bottom to top (1 solid, 0 broken) around the circle: Qian, Xun, Kan, Gen, Kun, Zhen, Li, Dui. */
	private static final int[][] TRIGRAMS = {{1, 1, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {1, 1, 0}};

	private static double[] polar(double r, double degrees) {
		double a = Math.toRadians(degrees);
		return new double[]{Math.sin(a) * r, Math.cos(a) * r};
	}

	private static List<double[]> localNodes(SigilPattern pattern, double radius) {
		List<double[]> out = new ArrayList<>();
		switch (pattern) {
			case BEIDOU_NINE -> {
				for (double[] p : BEIDOU) {
					out.add(p);
				}
			}
			case LUOSHU -> {
				for (double[] p : LUOSHU_AT) {
					out.add(new double[]{p[0] * 0.7D, p[1] * 0.7D});
				}
			}
			case WUXING -> {
				for (int i = 0; i < 5; i++) {
					out.add(polar(0.9D, i * 72.0D));
				}
			}
			case BAGUA, OCTAGRAM_SEAL, ROOTS, DAO_CIRCLE -> {
				double r = pattern == DAO_CIRCLE ? 0.9D : 1.0D;
				for (int i = 0; i < 8; i++) {
					out.add(polar(r, i * 45.0D));
				}
			}
			case SWORD_RING -> {
				for (int i = 0; i < 9; i++) {
					out.add(polar(1.0D, i * 40.0D));
				}
			}
			case ZHUXIAN_SQUARE -> {
				for (int i = 0; i < 4; i++) {
					out.add(polar(1.0D, 45.0D + i * 90.0D));
				}
			}
			case LOTUS16 -> {
				for (int k = 0; k < 16; k++) {
					out.add(polar(k % 2 == 0 ? 1.0D : 0.62D, k * 22.5D));
				}
			}
			case SNOWFLAKE -> {
				for (int i = 0; i < 6; i++) {
					out.add(polar(1.0D, i * 60.0D));
				}
			}
			case NINE_RINGS -> {
				for (int k = 0; k < 9; k++) {
					out.add(polar((k + 1) / 9.0D, k * 40.0D));
				}
			}
			case CLOCK12 -> {
				for (int h = 0; h < 12; h++) {
					out.add(polar(0.92D, h * 30.0D));
				}
			}
			case GRID -> {
				double cell = gridCell(radius);
				int n = Math.max(1, (int) Math.floor(radius / cell));
				for (int i = -n; i <= n; i++) {
					for (int j = -n; j <= n; j++) {
						if (i % 2 == 0 && j % 2 == 0) {
							out.add(new double[]{i * cell / Math.max(1.0E-6D, radius), j * cell / Math.max(1.0E-6D, radius)});
						}
					}
				}
			}
			case LIFE_DEATH -> {
				out.add(new double[]{0.0D, 0.5D});
				out.add(new double[]{0.0D, -0.5D});
			}
			case WIND_PATH -> {
				for (int i = 1; i <= 9; i++) {
					out.add(new double[]{0.0D, -1.0D + i * 0.1D});
				}
			}
			default -> {
			}
		}
		return out;
	}

	/** {x0, z0, x1, z1, node, major} rows. */
	private static List<double[]> localSegments(SigilPattern pattern, double radius) {
		List<double[]> out = new ArrayList<>();
		switch (pattern) {
			case BEIDOU_NINE -> {
				for (int i = 0; i < 6; i++) {
					line(out, BEIDOU[i], BEIDOU[i + 1], i + 1, true);
				}
				line(out, BEIDOU[3], BEIDOU[0], 3, true);
				line(out, BEIDOU[5], BEIDOU[7], 7, true);
				line(out, BEIDOU[4], BEIDOU[8], 8, true);
			}
			case LUOSHU -> {
				double s = 0.7D;
				double e = 1.05D;
				// the palace grid (structure), then the path 1 → 9
				for (double v : new double[]{-e, -s / 2.0D, s / 2.0D, e}) {
					out.add(new double[]{-e, v, e, v, -1, 0});
					out.add(new double[]{v, -e, v, e, -1, 0});
				}
				for (int k = 0; k < 8; k++) {
					double[] a = LUOSHU_AT[k];
					double[] b = LUOSHU_AT[k + 1];
					out.add(new double[]{a[0] * s, a[1] * s, b[0] * s, b[1] * s, k + 1, 1});
				}
			}
			case WUXING -> {
				for (int i = 0; i < 5; i++) {
					line(out, polar(0.9D, i * 72.0D), polar(0.9D, (i + 1) * 72.0D), (i + 1) % 5, true);
				}
				for (int i = 0; i < 5; i++) {
					line(out, polar(0.9D, i * 72.0D), polar(0.9D, (i + 2) * 72.0D), (i + 2) % 5, true);
				}
			}
			case BAGUA -> {
				for (int i = 0; i < 8; i++) {
					line(out, polar(1.0D, i * 45.0D), polar(1.0D, (i + 1) * 45.0D), (i + 1) % 8, true);
				}
				for (int i = 0; i < 8; i++) {
					for (int l = 0; l < 3; l++) {
						double r = 0.58D + l * 0.08D;
						double[] mid = polar(r, i * 45.0D);
						double tx = Math.cos(Math.toRadians(i * 45.0D));
						double tz = -Math.sin(Math.toRadians(i * 45.0D));
						double half = 0.13D;
						if (TRIGRAMS[i][l] == 1) {
							out.add(new double[]{mid[0] - tx * half, mid[1] - tz * half, mid[0] + tx * half, mid[1] + tz * half, i, 0});
						} else {
							out.add(new double[]{mid[0] - tx * half, mid[1] - tz * half, mid[0] - tx * 0.03D, mid[1] - tz * 0.03D, i, 0});
							out.add(new double[]{mid[0] + tx * 0.03D, mid[1] + tz * 0.03D, mid[0] + tx * half, mid[1] + tz * half, i, 0});
						}
					}
				}
				circle(out, 0.0D, 0.0D, 0.3D, 24, -1);
				arc(out, 0.0D, 0.15D, 0.15D, 0.0D, 180.0D, 6, -1);
				arc(out, 0.0D, -0.15D, 0.15D, 180.0D, 360.0D, 6, -1);
			}
			case SWORD_RING -> {
				for (int i = 0; i < 9; i++) {
					double angle = i * 40.0D;
					line(out, polar(0.42D, angle), polar(1.0D, angle), i, true);
					double[] guard = polar(0.52D, angle);
					double tx = Math.cos(Math.toRadians(angle)) * 0.08D;
					double tz = -Math.sin(Math.toRadians(angle)) * 0.08D;
					out.add(new double[]{guard[0] - tx, guard[1] - tz, guard[0] + tx, guard[1] + tz, i, 0});
				}
			}
			case ZHUXIAN_SQUARE -> {
				for (int i = 0; i < 4; i++) {
					line(out, polar(1.0D, 45.0D + i * 90.0D), polar(1.0D, 45.0D + (i + 1) * 90.0D), (i + 1) % 4, true);
				}
				line(out, polar(1.0D, 45.0D), polar(1.0D, 225.0D), -1, true);
				line(out, polar(1.0D, 135.0D), polar(1.0D, 315.0D), -1, true);
			}
			case LOTUS16 -> {
				for (int k = 0; k < 16; k++) {
					boolean outer = k % 2 == 0;
					petal(out, k * 22.5D, outer ? 0.45D : 0.18D, outer ? 1.0D : 0.62D, outer ? 0.16D : 0.12D, k);
				}
			}
			case SNOWFLAKE -> {
				for (int i = 0; i < 6; i++) {
					double angle = i * 60.0D;
					line(out, polar(0.08D, angle), polar(1.0D, angle), i, true);
					for (double r : new double[]{0.4D, 0.7D}) {
						double[] base = polar(r, angle);
						double[] left = polar(0.24D, angle - 45.0D);
						double[] right = polar(0.24D, angle + 45.0D);
						out.add(new double[]{base[0], base[1], base[0] + left[0], base[1] + left[1], i, 0});
						out.add(new double[]{base[0], base[1], base[0] + right[0], base[1] + right[1], i, 0});
					}
				}
			}
			case NINE_RINGS -> {
				for (int k = 0; k < 9; k++) {
					double r = (k + 1) / 9.0D;
					circle(out, 0.0D, 0.0D, r, 8 + (int) Math.round(32.0D * r), k);
				}
			}
			case CLOCK12 -> {
				circle(out, 0.0D, 0.0D, 1.0D, 48, -1);
				for (int h = 0; h < 12; h++) {
					line(out, polar(h % 3 == 0 ? 0.78D : 0.85D, h * 30.0D), polar(1.0D, h * 30.0D), h, false);
				}
				line(out, new double[]{0.0D, 0.0D}, polar(0.5D, 300.0D), -1, false);
				line(out, new double[]{0.0D, 0.0D}, polar(0.8D, 0.0D), -1, false);
			}
			case GRID -> {
				double cell = gridCell(radius);
				int n = Math.max(1, (int) Math.floor(radius / cell));
				double unit = cell / Math.max(1.0E-6D, radius);
				double edge = n * unit;
				for (int i = -n; i <= n; i++) {
					int node = Math.abs(i);
					out.add(new double[]{i * unit, -edge, i * unit, edge, node, 1});
					out.add(new double[]{-edge, i * unit, edge, i * unit, node, 1});
				}
			}
			case ROOTS -> {
				for (int i = 0; i < 8; i++) {
					double angle = i * 45.0D;
					double bend = i % 2 == 0 ? 8.0D : -8.0D;
					double[] mid = polar(0.55D, angle + bend);
					line(out, polar(0.08D, angle), mid, i, true);
					line(out, mid, polar(1.0D, angle - bend * 0.5D), i, true);
					double[] branch = polar(0.32D, angle + bend + (i % 2 == 0 ? 35.0D : -35.0D));
					out.add(new double[]{mid[0], mid[1], mid[0] + branch[0], mid[1] + branch[1], i, 0});
				}
			}
			case OCTAGRAM_SEAL -> {
				for (int i = 0; i < 8; i++) {
					line(out, polar(1.0D, i * 45.0D), polar(1.0D, (i + 3) * 45.0D), (i + 3) % 8, true);
				}
				for (int i = 0; i < 8; i++) {
					line(out, polar(1.0D, i * 45.0D), polar(1.0D, (i + 1) * 45.0D), -1, false);
				}
			}
			case LIFE_DEATH -> {
				circle(out, 0.0D, 0.0D, 1.0D, 36, -1);
				arc(out, 0.0D, 0.5D, 0.5D, 0.0D, 180.0D, 10, 0);
				arc(out, 0.0D, -0.5D, 0.5D, 180.0D, 360.0D, 10, 1);
			}
			case WIND_PATH -> {
				out.add(new double[]{0.0D, -1.0D, 0.0D, 0.0D, -1, 0});
				for (int i = 1; i <= 9; i++) {
					double t = -1.0D + i * 0.1D;
					out.add(new double[]{-0.06D, t - 0.05D, 0.0D, t, i - 1, 0});
					out.add(new double[]{0.06D, t - 0.05D, 0.0D, t, i - 1, 0});
				}
			}
			case DAO_CIRCLE -> {
				circle(out, 0.0D, 0.0D, 1.0D, 40, -1);
				circle(out, 0.0D, 0.0D, 0.8D, 32, -1);
			}
			default -> {
			}
		}
		return out;
	}

	/** Grid cells of 2 blocks up to radius 10, then 3 (28.9.1: 2-3 b). */
	private static double gridCell(double radius) {
		return radius <= 10.0D ? 2.0D : 3.0D;
	}

	private static void line(List<double[]> out, double[] a, double[] b, int node, boolean major) {
		out.add(new double[]{a[0], a[1], b[0], b[1], node, major ? 1 : 0});
	}

	private static void circle(List<double[]> out, double cx, double cz, double r, int segments, int node) {
		arc(out, cx, cz, r, 0.0D, 360.0D, segments, node);
	}

	private static void arc(List<double[]> out, double cx, double cz, double r, double fromDeg, double toDeg, int segments, int node) {
		int n = Math.max(3, segments);
		double[] last = null;
		for (int i = 0; i <= n; i++) {
			double angle = fromDeg + (toDeg - fromDeg) * i / n;
			double[] p = polar(r, angle);
			double[] point = {cx + p[0], cz + p[1]};
			if (last != null) {
				out.add(new double[]{last[0], last[1], point[0], point[1], node, 0});
			}
			last = point;
		}
	}

	/** A lens-shaped petal outline from {@code r0} to {@code r1} along {@code angle}, half width {@code w} (8 lines). */
	private static void petal(List<double[]> out, double angle, double r0, double r1, double w, int node) {
		double a = Math.toRadians(angle);
		double ax = Math.sin(a);
		double az = Math.cos(a);
		double sx = Math.cos(a);
		double sz = -Math.sin(a);
		for (int side = -1; side <= 1; side += 2) {
			double[] last = {ax * r0, az * r0};
			for (int i = 1; i <= 4; i++) {
				double t = i / 4.0D;
				double r = r0 + (r1 - r0) * t;
				double bulge = Math.sin(Math.PI * t) * w * side;
				double[] point = {ax * r + sx * bulge, az * r + sz * bulge};
				out.add(new double[]{last[0], last[1], point[0], point[1], node, 0});
				last = point;
			}
		}
	}
}
