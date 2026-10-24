package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.RencanaKotaTema;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The fixed geometry of every Kota Gunung (DESIGN 38.T.6.2), mirrored from {@code tools/gen_tempat_tema_gunung.py}: the
 * points the mechanisms move between, given in <b>canvas</b> coordinates of a district (the 96 x 72 x 96 drawing the
 * generator cuts into four lots; x east, z south before rotation, y the canvas height). One canvas point maps to the
 * world as
 * <pre>
 *   y = dasarY - 5 + Distrik.dy + canvasY          (template y 4 = street floor at dasarY - 1, 38.T.2.2)
 *   xz = town centre + rotate(putar, district grid * 96 + canvas - 48)
 * </pre>
 * with the continuous rotation that matches {@code StructureTemplate}'s block rotation (a block centre stays a block
 * centre). Because the plan rotates the whole town, the cable line, the pulleys and the rockfall lanes keep their shape in
 * every rotation, and every point is computed without loading a chunk (the mechanisms of a town work from its plan).
 * <p>
 * Numbers that must change together with the generator are marked "gen:" with the Python name.
 */
public final class GunungTata {

	/** District cell edge (Tema.SEL_DISTRIK). */
	public static final int SEL = 96;

	// ------------------------------------------------------------------ the cable car (gen: stasiun_gantung, menara_tiang)
	/** Cabin stop at the lower station (kaki_gerbang lot l00): the 5-wide gap of the platform, standing level. */
	public static final Titik KABIN_BAWAH = new Titik("kaki_gerbang", 24.5D, 19.0D, 25.0D);
	/** Cabin under the guide wheel of the Tiang Tengah (aula_herba): wheel centre (24.5, 37.5, 80.5). */
	public static final Titik KABIN_TIANG = new Titik("aula_herba", 24.5D, 35.2D, 80.5D);
	/** Cabin stop at the upper station (puncak_emas lot l01), gap on the south edge. */
	public static final Titik KABIN_ATAS = new Titik("puncak_emas", 24.5D, 13.0D, 91.0D);
	/** The rope rides this far above the cabin's feet (hanger arm + trolley). */
	public static final double TALI_DI_ATAS = 3.6D;
	/** Sag of the long span at its middle (blocks); the short steep span sags {@link #SAG_PENDEK}. */
	public static final double SAG_PANJANG = 3.0D;
	public static final double SAG_PENDEK = 0.8D;

	// ------------------------------------------------------------------ the nine pilgrim landings (gen: frame bordes=)
	public static final List<Titik> BORDES = List.of(
			new Titik("kaki_gerbang", 48.5D, 5.0D, 56.5D), new Titik("kaki_gerbang", 48.5D, 11.0D, 24.5D),
			new Titik("kaki_gerbang", 48.5D, 17.0D, 7.5D), new Titik("aula_herba", 48.5D, 5.0D, 56.5D),
			new Titik("aula_herba", 48.5D, 11.0D, 24.5D), new Titik("aula_herba", 48.5D, 17.0D, 7.5D),
			new Titik("puncak_emas", 48.5D, 5.0D, 56.5D), new Titik("puncak_emas", 48.5D, 11.0D, 46.5D),
			new Titik("puncak_emas", 48.5D, 11.0D, 23.5D));
	/** The Gerbang Kaki gate arch (start of the climb) and the pilgrims' registration desk of the Kuil Kaki. */
	public static final Titik GERBANG_KAKI = new Titik("kaki_gerbang", 24.5D, 5.0D, 95.5D);
	public static final Titik DAFTAR_ZIARAH = new Titik("penginapan_peziarah", 64.5D, 11.0D, 33.5D);
	/** The terrace of the Aula Emas (meditation site and arrival of the pilgrimage): centre and radius. */
	public static final Titik AULA_EMAS = new Titik("puncak_emas", 48.5D, 11.0D, 30.5D);
	public static final double AULA_EMAS_JARI = 30.0D;
	/** The four viewing points of the Aula Emas balustrade (Fajar Cahaya Budha; gen: titik pandang no 1-4). */
	public static final List<Titik> PANDANG = List.of(
			new Titik("puncak_emas", 10.5D, 11.0D, 45.5D), new Titik("puncak_emas", 30.5D, 11.0D, 45.5D),
			new Titik("puncak_emas", 66.5D, 11.0D, 45.5D), new Titik("puncak_emas", 86.5D, 11.0D, 45.5D));
	/** Where the Buddha's-light ring floats: above the cloud sea south of the summit. */
	public static final Titik CAHAYA = new Titik("puncak_emas", 48.5D, 4.0D, 150.0D);
	/** The great bell of the Menara Lonceng Awan (gen: menara_lonceng). */
	public static final Titik LONCENG = new Titik("puncak_emas", 70.5D, 17.0D, 79.5D);
	/** The night watch post of quest stage 2 at the foot of the Tiang Tengah. */
	public static final Titik JAGA_TIANG = new Titik("aula_herba", 26.5D, 5.0D, 74.5D);

	// ------------------------------------------------------------------ rockfall lanes (gen: tebing_buatan, puncak cliff)
	/** Lanes of the rolling rocks: polylines in canvas space, the first point on a cliff crest. */
	public static final List<Lajur> LONGSOR = List.of(
			new Lajur("tebing_papan", new double[][]{{6.5D, 41.0D, 62.5D}, {10.5D, 16.0D, 62.5D}, {14.5D, 6.0D, 63.5D}, {40.5D, 5.0D, 66.5D}}),
			new Lajur("tebing_papan", new double[][]{{6.5D, 40.0D, 34.5D}, {10.5D, 25.0D, 34.5D}, {16.5D, 11.0D, 36.5D}, {40.5D, 11.0D, 38.5D}}),
			new Lajur("puncak_emas", new double[][]{{63.5D, 24.0D, 1.5D}, {63.5D, 11.0D, 6.5D}, {62.5D, 11.0D, 44.5D}, {50.5D, 8.0D, 50.5D},
					{49.5D, 5.0D, 60.5D}}));

	/** A point of one district canvas. */
	public record Titik(String distrik, double x, double y, double z) {
	}

	/** A rockfall lane (canvas polyline of one district). */
	public record Lajur(String distrik, double[][] titik) {
	}

	private GunungTata() {
	}

	// ================================================================================================ transforms

	/** Grid cell of a district of the gunung theme, or null. */
	public static @Nullable Tema.Distrik distrik(String id) {
		for (Tema.Distrik d : Tema.GUNUNG.distrik()) {
			if (d.id().equals(id)) {
				return d;
			}
		}
		return null;
	}

	/** Distrik.dy of a grid row (RencanaKotaTema.DY_GUNUNG). */
	public static int dy(Tema.Distrik d) {
		return RencanaKotaTema.DY_GUNUNG[Math.clamp(d.z() + 1, 0, 2)];
	}

	/** The world position of a canvas point of town {@code kota}. */
	public static Vec3 dunia(TemaKota.Kota kota, String distrik, double cx, double cy, double cz) {
		Tema.Distrik d = distrik(distrik);
		if (d == null) {
			return Vec3.atBottomCenterOf(kota.pusat());
		}
		double ox = d.x() * SEL + cx - 48.0D;
		double oz = d.z() * SEL + cz - 48.0D;
		BlockPos c = kota.dunia(0, 0, 0);
		double[] r = putar(kota.putar(), ox, oz);
		double y = kota.dasarY() - 5 + dy(d) + cy;
		return new Vec3(c.getX() + r[0], y, c.getZ() + r[1]);
	}

	public static Vec3 dunia(TemaKota.Kota kota, Titik t) {
		return dunia(kota, t.distrik(), t.x(), t.y(), t.z());
	}

	/**
	 * Continuous rotation by {@code putar} quarter turns clockwise that keeps block centres on block centres
	 * ({@code TataLetakStructure.rotate} on blocks): CW90 (x, z) -> (1 - z, x), 180 -> (1 - x, 1 - z), CCW90 -> (z, 1 - x).
	 */
	public static double[] putar(int putar, double x, double z) {
		return switch (putar & 3) {
			case 1 -> new double[]{1.0D - z, x};
			case 2 -> new double[]{1.0D - x, 1.0D - z};
			case 3 -> new double[]{z, 1.0D - x};
			default -> new double[]{x, z};
		};
	}

	/** A canvas yaw (degrees, Minecraft convention: south 0, west 90, north 180) turned with the town. */
	public static float yaw(TemaKota.Kota kota, float yawKanvas) {
		return net.minecraft.util.Mth.wrapDegrees(yawKanvas + 90.0F * (kota.putar() & 3));
	}

	// ================================================================================================ lookups

	/** The Kota Gunung holding {@code pos}, or null. */
	public static @Nullable TemaKota.Kota kotaDi(ServerLevel level, BlockPos pos) {
		TemaKota.Kota kota = TemaKota.di(level, pos);
		return kota != null && kota.tema() == Tema.GUNUNG ? kota : null;
	}

	/** The nearest Kota Gunung within {@code radius} blocks, or null. */
	public static @Nullable TemaKota.Kota terdekat(ServerLevel level, BlockPos pos, int radius) {
		return TemaKota.terdekat(level, pos, Tema.GUNUNG, radius);
	}

	/** The number (1-9) of the pilgrim landing within {@code jari} blocks of {@code at}, or 0. */
	public static int bordesDekat(TemaKota.Kota kota, Vec3 at, double jari) {
		double r2 = jari * jari;
		for (int i = 0; i < BORDES.size(); i++) {
			if (dunia(kota, BORDES.get(i)).distanceToSqr(at) <= r2) {
				return i + 1;
			}
		}
		return 0;
	}

	/** True when {@code at} stands on the terrace of the Aula Emas of {@code kota}. */
	public static boolean diAulaEmas(TemaKota.Kota kota, Vec3 at) {
		Vec3 c = dunia(kota, AULA_EMAS);
		return Math.abs(at.y - c.y) < 12.0D && Math.hypot(at.x - c.x, at.z - c.z) <= AULA_EMAS_JARI;
	}
}
