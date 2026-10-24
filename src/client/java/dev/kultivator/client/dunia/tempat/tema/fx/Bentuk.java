package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Shared shapes of the themed-town effects, all camera-relative and written into one {@link FxQuads}: the haze shell
 * (a ring of vertical band quads around the camera, the fake "fog" of the sandstorm and the mountain mist), a tiled
 * horizontal sheet with soft edges (the cloud sea, the storm-bay surface) and a camera-facing ring. Render thread only.
 */
final class Bentuk {

	private static final Vector3f A = new Vector3f();
	private static final Vector3f B = new Vector3f();
	private static final Vector3f C = new Vector3f();
	private static final Vector3f D = new Vector3f();

	private Bentuk() {
	}

	/**
	 * A ring of {@code segmen} vertical quads of radius {@code r} around the camera from height {@code y0} to {@code y1}
	 * (camera-relative), the texture repeated {@code ulang} times around it and shifted by {@code geser} (scroll). The
	 * faces look inward. Returns the quads written (0 when the cap is full).
	 */
	static int cangkang(FxQuads q, float r, float y0, float y1, int segmen, float ulang, float geser, int warnaAtas, int warnaBawah,
			int light) {
		if (!TemaFx.muat(segmen)) {
			return 0;
		}
		float step = Mth.TWO_PI / segmen;
		float du = ulang / segmen;
		for (int i = 0; i < segmen; i++) {
			float a0 = i * step;
			float a1 = a0 + step;
			float x0 = Mth.cos(a0) * r;
			float z0 = Mth.sin(a0) * r;
			float x1 = Mth.cos(a1) * r;
			float z1 = Mth.sin(a1) * r;
			float u0 = geser + i * du;
			float u1 = u0 + du;
			A.set(x0, y1, z0);
			B.set(x0, y0, z0);
			C.set(x1, y0, z1);
			D.set(x1, y1, z1);
			q.quadUv(A, B, C, D, u0, 0.0F, u0, 1.0F, u1, 1.0F, u1, 0.0F, warnaAtas, warnaBawah, warnaBawah, warnaAtas, light);
		}
		return segmen;
	}

	/**
	 * A flat sheet at height {@code y} (camera-relative), centred on (cx, cz) camera-relative, {@code n × n} tiles of
	 * {@code tile} blocks; texture coordinates follow the world (wx0, wz0 = world position of the sheet centre) divided by
	 * {@code skala} plus the scroll (su, sv), so the pattern stays put when the camera moves. Alpha falls from
	 * {@code alpha} in the middle to 0 at the rim (and is multiplied by {@code dekat(dx, dz)} when given). Returns quads.
	 */
	static int lembar(FxQuads q, float cx, float y, float cz, double wx0, double wz0, int n, float tile, float skala, float su,
			float sv, int rgb, float alpha, float rimMulai, int light) {
		int total = n * n;
		if (!TemaFx.muat(total)) {
			return 0;
		}
		float half = n * tile * 0.5F;
		for (int ix = 0; ix < n; ix++) {
			for (int iz = 0; iz < n; iz++) {
				float x0 = cx - half + ix * tile;
				float z0 = cz - half + iz * tile;
				float x1 = x0 + tile;
				float z1 = z0 + tile;
				int c00 = Bingkai.argb(alpha * pinggir(x0 - cx, z0 - cz, half, rimMulai), rgb);
				int c01 = Bingkai.argb(alpha * pinggir(x0 - cx, z1 - cz, half, rimMulai), rgb);
				int c11 = Bingkai.argb(alpha * pinggir(x1 - cx, z1 - cz, half, rimMulai), rgb);
				int c10 = Bingkai.argb(alpha * pinggir(x1 - cx, z0 - cz, half, rimMulai), rgb);
				if ((c00 | c01 | c11 | c10) >>> 24 == 0) {
					continue;
				}
				float u0 = (float) ((wx0 + (x0 - cx)) / skala) + su;
				float u1 = (float) ((wx0 + (x1 - cx)) / skala) + su;
				float v0 = (float) ((wz0 + (z0 - cz)) / skala) + sv;
				float v1 = (float) ((wz0 + (z1 - cz)) / skala) + sv;
				// wrap the big world numbers near zero so floats keep their precision (the texture repeats every 1)
				float wu = (float) Math.floor(u0);
				float wv = (float) Math.floor(v0);
				u0 -= wu;
				u1 -= wu;
				v0 -= wv;
				v1 -= wv;
				A.set(x0, y, z0);
				B.set(x0, y, z1);
				C.set(x1, y, z1);
				D.set(x1, y, z0);
				q.quadUv(A, B, C, D, u0, v0, u0, v1, u1, v1, u1, v0, c00, c01, c11, c10, light);
			}
		}
		return total;
	}

	/** 1 in the middle of a square sheet of half size {@code half}, falling smoothly to 0 at the rim from {@code mulai}. */
	static float pinggir(float dx, float dz, float half, float mulai) {
		float d = Mth.sqrt(dx * dx + dz * dz) / half;
		return 1.0F - Bingkai.halus((d - mulai) / Math.max(0.01F, 1.0F - mulai));
	}
}
