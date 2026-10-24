package dev.kultivator.client.dunia.tempat.jalan;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kultivator.client.dunia.peta.PetaPainter;
import dev.kultivator.dunia.tempat.jalan.JalanNet;

import java.util.List;

/**
 * The road layer of the world map (DESIGN 38.D.12, owner 38W), painted into the map bake through
 * {@link PetaPainter#addOverlay}: every road the player knows (an edge whose milestone he touched) as an ink line by
 * class, and the 3 x 3 district plan of every known capital at the close zoom.
 * <ul>
 *   <li>imperial road (class 0): a double stroke, dark ink edge and an ochre core, 2 px at 16 b/px, 1 px further out;</li>
 *   <li>village road (class 1): one brown stroke (1 px), faded at 64 b/px, hidden at 256 b/px;</li>
 *   <li>footpath (class 2): a dotted line, only at 16 b/px;</li>
 *   <li>capital: at 16 b/px each district box gets a pale wash in its own colour (palace gold, market red, scholars
 *       blue, ...) and a thin ink outline, the whole city a 2 px wall line; at 64 b/px only the wall line.</li>
 * </ul>
 * Weak laptop: painted once per bake (only when the map view moves or the data changes), never per frame; at most
 * 256 lines of 48 points and 64 boxes.
 */
public final class JalanPeta {

	private static final int TINTA = 0x23231F;
	private static final int OKER = 0xB08A4A;
	private static final int COKLAT = 0x6E5236;
	private static final int TEMBOK = 0x4A3A2A;

	private static volatile List<JalanNet.Garis> garis = List.of();
	private static volatile List<JalanNet.Denah> denah = List.of();
	private static boolean registered;

	/** From {@code JalanClient.init()}. */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		PetaPainter.addOverlay(JalanPeta::paint);
	}

	/** The server's road map payload replaces the known lines and plans. */
	static void terima(JalanNet.Peta payload) {
		garis = payload.garis();
		denah = payload.denah();
		PetaPainter.invalidate();
	}

	/** Disconnect: forget everything. */
	static void lupakan() {
		garis = List.of();
		denah = List.of();
	}

	/** The known roads (for the hub card and tests). */
	public static List<JalanNet.Garis> garis() {
		return garis;
	}

	private static void paint(NativeImage img, int x0, int z0, int bpp) {
		paintDenah(img, x0, z0, bpp);
		for (JalanNet.Garis g : garis) {
			switch (g.kelas()) {
				case 0 -> {
					if (bpp <= 16) {
						polyline(img, g, x0, z0, bpp, TINTA, 0.85D, 2, 0);
						polyline(img, g, x0, z0, bpp, OKER, 0.9D, 1, 0);
					} else {
						polyline(img, g, x0, z0, bpp, TINTA, bpp >= 256 ? 0.55D : 0.8D, 1, 0);
					}
				}
				case 1 -> {
					if (bpp <= 64) {
						polyline(img, g, x0, z0, bpp, COKLAT, bpp <= 16 ? 0.8D : 0.5D, 1, 0);
					}
				}
				default -> {
					if (bpp <= 16) {
						polyline(img, g, x0, z0, bpp, COKLAT, 0.65D, 1, 3);
					}
				}
			}
		}
	}

	private static void paintDenah(NativeImage img, int x0, int z0, int bpp) {
		if (bpp > 64 || denah.isEmpty()) {
			return;
		}
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (JalanNet.Denah d : denah) {
			if (bpp <= 16) {
				int px0 = Math.floorDiv(d.x0() - x0, bpp);
				int pz0 = Math.floorDiv(d.z0() - z0, bpp);
				int px1 = Math.floorDiv(d.x1() - x0, bpp);
				int pz1 = Math.floorDiv(d.z1() - z0, bpp);
				int wash = warna(d.distrik());
				for (int py = Math.max(0, pz0); py <= Math.min(PetaPainter.SIZE - 1, pz1); py++) {
					for (int px = Math.max(0, px0); px <= Math.min(PetaPainter.SIZE - 1, px1); px++) {
						boolean edge = px == px0 || px == px1 || py == pz0 || py == pz1;
						PetaPainter.ink(img, px, py, edge ? TINTA : wash, edge ? 0.45D : 0.28D);
					}
				}
			}
			minX = Math.min(minX, d.x0());
			minZ = Math.min(minZ, d.z0());
			maxX = Math.max(maxX, d.x1());
			maxZ = Math.max(maxZ, d.z1());
		}
		// the city wall around the districts that form one capital (plans of several capitals each get their own box
		// when they are far apart: the boxes are grouped by distance)
		for (int[] box : kelompok()) {
			int px0 = Math.floorDiv(box[0] - x0, bpp) - 1;
			int pz0 = Math.floorDiv(box[1] - z0, bpp) - 1;
			int px1 = Math.floorDiv(box[2] - x0, bpp) + 1;
			int pz1 = Math.floorDiv(box[3] - z0, bpp) + 1;
			int w = bpp <= 16 ? 2 : 1;
			for (int t = 0; t < w; t++) {
				for (int px = px0 - t; px <= px1 + t; px++) {
					PetaPainter.ink(img, px, pz0 - t, TEMBOK, 0.85D);
					PetaPainter.ink(img, px, pz1 + t, TEMBOK, 0.85D);
				}
				for (int py = pz0 - t; py <= pz1 + t; py++) {
					PetaPainter.ink(img, px0 - t, py, TEMBOK, 0.85D);
					PetaPainter.ink(img, px1 + t, py, TEMBOK, 0.85D);
				}
			}
		}
	}

	/** District boxes grouped into capitals (boxes closer than 400 blocks belong together). */
	private static List<int[]> kelompok() {
		java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
		for (JalanNet.Denah d : denah) {
			int[] hit = null;
			for (int[] b : out) {
				if (d.x0() <= b[2] + 400 && d.x1() >= b[0] - 400 && d.z0() <= b[3] + 400 && d.z1() >= b[1] - 400) {
					hit = b;
					break;
				}
			}
			if (hit == null) {
				out.add(new int[]{d.x0(), d.z0(), d.x1(), d.z1()});
			} else {
				hit[0] = Math.min(hit[0], d.x0());
				hit[1] = Math.min(hit[1], d.z0());
				hit[2] = Math.max(hit[2], d.x1());
				hit[3] = Math.max(hit[3], d.z1());
			}
		}
		return out;
	}

	/** The pale wash of a capital district (DESIGN 38.B.3 district colours). */
	static int warna(String distrik) {
		return switch (distrik) {
			case "istana" -> 0xE8C060;
			case "cendekia" -> 0x7FA0C8;
			case "awan" -> 0xC8D8F0;
			case "gilda" -> 0xA08060;
			case "pusat" -> 0xE0D0A0;
			case "pasar" -> 0xD07050;
			case "hunian_barat" -> 0xB8B090;
			case "arena" -> 0xC05040;
			case "gerbang" -> 0x909080;
			case "kumuh" -> 0x8A7A60;
			default -> 0xA8A890;
		};
	}

	/**
	 * Draws a polyline: {@code width} 1 or 2 px, {@code gap} > 0 makes it dotted (one pixel in every {@code gap}).
	 * Points are sampled every half pixel along each segment.
	 */
	private static void polyline(NativeImage img, JalanNet.Garis g, int x0, int z0, int bpp, int rgb, double alpha, int width, int gap) {
		int[] xs = g.xs();
		int[] zs = g.zs();
		int n = Math.min(xs.length, zs.length);
		int lastX = Integer.MIN_VALUE;
		int lastY = Integer.MIN_VALUE;
		int count = 0;
		for (int i = 1; i < n; i++) {
			double ax = (xs[i - 1] - x0) / (double) bpp;
			double ay = (zs[i - 1] - z0) / (double) bpp;
			double bx = (xs[i] - x0) / (double) bpp;
			double by = (zs[i] - z0) / (double) bpp;
			// skip segments wholly outside the bake
			if (Math.max(ax, bx) < -2 || Math.min(ax, bx) > PetaPainter.SIZE + 2 || Math.max(ay, by) < -2
					|| Math.min(ay, by) > PetaPainter.SIZE + 2) {
				continue;
			}
			int steps = (int) Math.ceil(Math.max(Math.abs(bx - ax), Math.abs(by - ay)) * 2.0D) + 1;
			for (int s = 0; s <= steps; s++) {
				double t = s / (double) steps;
				int px = (int) Math.floor(ax + (bx - ax) * t);
				int py = (int) Math.floor(ay + (by - ay) * t);
				if (px == lastX && py == lastY) {
					continue;
				}
				lastX = px;
				lastY = py;
				count++;
				if (gap > 0 && count % gap != 0) {
					continue;
				}
				PetaPainter.ink(img, px, py, rgb, alpha);
				if (width >= 2) {
					PetaPainter.ink(img, px + 1, py, rgb, alpha * 0.8D);
					PetaPainter.ink(img, px, py + 1, rgb, alpha * 0.8D);
				}
			}
		}
	}

	private JalanPeta() {
	}
}
