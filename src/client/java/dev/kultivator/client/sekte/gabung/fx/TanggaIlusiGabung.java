package dev.kultivator.client.sekte.gabung.fx;

import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.sekte.gabung.GabungPayloads;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The illusory stair of the Tangga Ujian (DESIGN 41.5.4 stage 1, 41V; the pattern of 36G's illusion stair): while the
 * local player climbs a sect's test stair, steps of jade light lie over the real steps already climbed and go on ahead
 * of the candidate, past the real top gate and up into the sky, as if the mountain had no summit.
 * <ul>
 * <li>Path: the real steps are the candidate's own trail (a point every 0.6 blocks walked or 0.45 risen, from the foot
 * the server set the candidate on). Ahead the stair heads for the top gate when the test HUD carries the stair's ends
 * (bytes 3-26, see {@link #ujung}), else along the trail's own heading and slope; beyond the top it keeps climbing at a
 * gentler slope.</li>
 * <li>Steps: 120 / 400 / 1000 drawn steps for EffectQuality Minimal / Normal / Maksimal (36G's numbers), nothing past
 * 96 blocks from the camera (fading from 64), halved until the 7b frame budget holds them. A wave of light climbs the
 * steps; the colour turns from jade to gold up the stair and toward red as Mountain Pressure rises; past pressure 70
 * the steps flicker.</li>
 * <li>Each of the three illusions (the HUD's illusion count rising): for two seconds the heart demon's face hangs before
 * the candidate (36G's {@code face.png}), a gust of light streaks past, the stair dims to violet.</li>
 * <li>Mist at the foot (Normal 8, Maksimal 16 puffs; none on Minimal) and side rails of light (Normal and up), a turning
 * rune ring and a glow at the top gate when its place is known.</li>
 * </ul>
 * Client only; reads the test HUD ({@code GabungClient.ujian()}, stage 1 = data byte 0 {@code 0xFF}); switch
 * {@code gabung.tanggaIlusi}. Fades out over two seconds when the stage ends.
 */
public final class TanggaIlusiGabung {

	static final int[] LOD = {120, 400, 1000};
	static final double JANGKAU = 96.0D;
	static final double PUDAR = 64.0D;
	static final double LARI = 0.5D;
	static final float SETENGAH_LEBAR = 1.2F;
	private static final int JEJAK_MAKS = 512;
	private static final int[] KABUT = {0, 8, 16};
	private static final long ILUSI_TICKS = 40L;
	private static final int JADE = 0x5FE3C0;
	private static final int JADE_DEEP = 0x2FB89A;
	private static final int GOLD = 0xFFD27A;
	private static final int RED = 0xFF5040;
	private static final int DEMON = 0xB060FF;

	/** The candidate's trail (x, y, z per point). */
	private static final double[] JEJAK = new double[JEJAK_MAKS * 3];
	private static int jejak;
	private static boolean aktif;
	private static float tampak;
	private static float tekanan;
	private static int ilusi;
	private static long ilusiSejak = -1000L;
	private static double @Nullable [] atas;
	private static double @Nullable [] bawah;

	/** Drawn stair nodes: position and heading (reused every frame). */
	private static final int NODE_MAKS = 1100;
	private static final double[] NX = new double[NODE_MAKS];
	private static final double[] NY = new double[NODE_MAKS];
	private static final double[] NZ = new double[NODE_MAKS];
	private static final float[] HX = new float[NODE_MAKS];
	private static final float[] HZ = new float[NODE_MAKS];

	static boolean any() {
		return aktif || tampak > 0.0F;
	}

	static void clear() {
		aktif = false;
		tampak = 0.0F;
		jejak = 0;
		atas = null;
		bawah = null;
	}

	/**
	 * The stair's foot and top from the stage-1 HUD bytes, when the sender appends them: bytes 3-14 the foot x, y, z and
	 * bytes 15-26 the top x, y, z, each a big-endian int (block coordinates). Null when absent.
	 */
	static double @Nullable [] ujung(byte[] data, int at) {
		if (data == null || data.length < at + 12) {
			return null;
		}
		double[] p = new double[3];
		for (int k = 0; k < 3; k++) {
			int o = at + k * 4;
			p[k] = ((data[o] & 0xFF) << 24 | (data[o + 1] & 0xFF) << 16 | (data[o + 2] & 0xFF) << 8 | (data[o + 3] & 0xFF)) + (k == 1 ? 0.0D : 0.5D);
		}
		return p;
	}

	static void tick(Player player, long ticks) {
		GabungPayloads.Ujian u = GabungClient.ujian();
		byte[] data = u == null ? null : u.data();
		int jenis = data != null && data.length >= 1 ? data[0] & 0xFF : -1;
		// TanggaUjian sends [0xFF, illusions, slowness]; GabungLembar.HudUjian documents [J_TANGGA 1, .., [5] illusions]
		boolean tangga = u != null && u.tahap() == 1 && (jenis == 0xFF || jenis == 1) && GabungFxClient.saklar(GabungFxClient.TANGGA_ILUSI);
		if (!tangga) {
			if (aktif) {
				aktif = false;
			}
			if (tampak > 0.0F) {
				tampak = Math.max(0.0F, tampak - 0.025F);
				if (tampak <= 0.0F) {
					clear();
				}
			}
			return;
		}
		int at = jenis == 0xFF ? 1 : 5;
		int n = data.length > at ? data[at] & 0xFF : 0;
		if (!aktif) {
			aktif = true;
			jejak = 0;
			ilusi = n;
			ilusiSejak = -1000L;
			tambah(player.getX(), player.getY(), player.getZ());
		}
		bawah = jenis == 0xFF ? ujung(data, 3) : null;
		atas = jenis == 0xFF ? ujung(data, 15) : null;
		tekanan = u.tekanan();
		if (n > ilusi) {
			ilusiSejak = ticks;
		}
		ilusi = n;
		tampak = Math.min(1.0F, tampak + 0.05F);
		int last = (jejak - 1) * 3;
		double dx = player.getX() - JEJAK[last];
		double dy = player.getY() - JEJAK[last + 1];
		double dz = player.getZ() - JEJAK[last + 2];
		double h = Math.sqrt(dx * dx + dz * dz);
		if (h > 8.0D || Math.abs(dy) > 6.0D) {
			// set somewhere else (the server's teleport to the foot): the trail starts again
			jejak = 0;
			tambah(player.getX(), player.getY(), player.getZ());
		} else if ((h >= 0.6D || Math.abs(dy) >= 0.45D) && player.onGround()) {
			tambah(player.getX(), player.getY(), player.getZ());
		}
	}

	private static void tambah(double x, double y, double z) {
		if (jejak >= JEJAK_MAKS) {
			// keep the newest half: the stair ahead matters more than its oldest steps
			System.arraycopy(JEJAK, JEJAK_MAKS / 2 * 3, JEJAK, 0, JEJAK_MAKS / 2 * 3);
			jejak = JEJAK_MAKS / 2;
		}
		JEJAK[jejak * 3] = x;
		JEJAK[jejak * 3 + 1] = y;
		JEJAK[jejak * 3 + 2] = z;
		jejak++;
	}

	// ================================================================== drawing

	static void draw(ClientLevel level) {
		if (!any() || jejak == 0) {
			return;
		}
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int mutu = GabungFxClient.mutu();
		int want = LOD[mutu];
		Vec3 here = player.getPosition(GabungFxClient.partial);
		int nodes = path(here, want);
		if (nodes < 2) {
			return;
		}
		float vis = GabungFxClient.smooth(tampak);
		long since = GabungFxClient.ticks() - ilusiSejak;
		float demon = since >= 0L && since < ILUSI_TICKS ? (float) Math.sin(Math.PI * since / ILUSI_TICKS) : 0.0F;
		boolean rails = mutu >= 1;
		int mist = KABUT[mutu];
		int steps = nodes - 1;
		int stride = 1;
		while (!EpicBudget.takeQuads(steps / stride * 2 + (rails ? steps / stride / 2 : 0) + mist + 16)) {
			if (steps / stride <= 30) {
				return;
			}
			stride *= 2;
		}
		float pressure = GabungFxClient.smooth((tekanan - 30.0F) / 70.0F);
		double now = GabungFxClient.now;
		double wave = now * 3.0D % (steps + 80) - 40.0D;
		for (int i = 0; i + stride < nodes; i += stride) {
			int j = i + stride;
			double d = GabungFxClient.jarak(NX[i], NY[i], NZ[i]);
			if (d > JANGKAU) {
				continue;
			}
			float fade = 1.0F - GabungFxClient.smooth((float) ((d - PUDAR) / (JANGKAU - PUDAR)));
			float up = (float) i / Math.max(1, nodes - 1);
			double w = Math.exp(-Math.pow((i - wave) / 40.0D, 2.0D));
			int rgb = GabungFxClient.mix(JADE, GOLD, GabungFxClient.smooth((up - 0.55F) / 0.45F));
			rgb = GabungFxClient.mix(rgb, RED, pressure * 0.8F);
			rgb = GabungFxClient.mix(rgb, DEMON, demon * 0.7F);
			rgb = GabungFxClient.mix(rgb, 0xFFFFFF, (float) (0.45D * w));
			float flicker = tekanan > 70.0F ? 0.7F + 0.3F * (float) Math.sin(now * 0.9D + i * 0.37D) : 1.0F;
			float base = vis * fade * flicker * (1.0F - 0.45F * demon);
			int tread = GabungFxClient.argb((0.5F + 0.35F * (float) w) * base, rgb);
			int riser = GabungFxClient.argb((0.34F + 0.3F * (float) w) * base, GabungFxClient.mix(rgb, JADE_DEEP, 0.35F));
			step(i, j, tread, riser);
			if (rails && (i / stride) % 4 == 0) {
				int rail = GabungFxClient.argb((0.55F + 0.4F * (float) w) * base, GabungFxClient.mix(rgb, 0xFFFFFF, 0.3F));
				int k = Math.min(nodes - 1, i + stride * 4);
				for (int side = -1; side <= 1; side += 2) {
					float sx = -HZ[i] * SETENGAH_LEBAR * side;
					float sz = HX[i] * SETENGAH_LEBAR * side;
					float kx = -HZ[k] * SETENGAH_LEBAR * side;
					float kz = HX[k] * SETENGAH_LEBAR * side;
					GabungFxClient.beam(GabungFxClient.CAHAYA, NX[i] + sx, NY[i] + 0.14D, NZ[i] + sz, NX[k] + kx, NY[k] + 0.14D, NZ[k] + kz, 0.14F, rail,
							rail);
				}
			}
		}
		if (mist > 0) {
			mist(mist, vis, now);
		}
		if (atas != null) {
			double d = GabungFxClient.jarak(atas[0], atas[1], atas[2]);
			if (d < JANGKAU) {
				float pulse = (float) (0.5D + 0.5D * Math.sin(now * 0.12D));
				GabungFxClient.sprite(GabungFxClient.LUNAK, atas[0], atas[1] + 1.6D, atas[2], 2.6F, GabungFxClient.argb((0.35F + 0.2F * pulse) * vis, GOLD),
						GabungFxClient.SEL_GLOW, 0.0F);
				GabungFxClient.datar(GabungFxClient.SPRITE, atas[0], atas[1] + 0.08D, atas[2], 2.4F, GabungFxClient.argb(0.75F * vis, GOLD),
						GabungFxClient.SEL_CINCIN, now * 0.02D);
			}
		}
		if (demon > 0.0F) {
			illusion(player, demon, vis, since);
		}
	}

	/** One tread (flat, at the lower node's height) and its riser (facing down the stair) from node i to node j. */
	private static void step(int i, int j, int tread, int riser) {
		double cx = GabungFxClient.camX;
		double cy = GabungFxClient.camY;
		double cz = GabungFxClient.camZ;
		float sx0 = -HZ[i] * SETENGAH_LEBAR;
		float sz0 = HX[i] * SETENGAH_LEBAR;
		float sx1 = -HZ[j] * SETENGAH_LEBAR;
		float sz1 = HX[j] * SETENGAH_LEBAR;
		float y0 = (float) (NY[i] - cy);
		float y1 = (float) (NY[j] - cy);
		float ax = (float) (NX[i] - cx);
		float az = (float) (NZ[i] - cz);
		float bx = (float) (NX[j] - cx);
		float bz = (float) (NZ[j] - cz);
		// the tread lies at the upper node's height, from this node to the next (the riser closes the gap below it)
		GabungFxClient.TAPAK.vertex(ax - sx0, y1 + 0.04F, az - sz0, 0.0F, 0.0F, tread, 0.0F, 1.0F, 0.0F);
		GabungFxClient.TAPAK.vertex(ax + sx0, y1 + 0.04F, az + sz0, 1.0F, 0.0F, tread, 0.0F, 1.0F, 0.0F);
		GabungFxClient.TAPAK.vertex(bx + sx1, y1 + 0.04F, bz + sz1, 1.0F, 1.0F, tread, 0.0F, 1.0F, 0.0F);
		GabungFxClient.TAPAK.vertex(bx - sx1, y1 + 0.04F, bz - sz1, 0.0F, 1.0F, tread, 0.0F, 1.0F, 0.0F);
		if (y1 - y0 > 0.02F) {
			GabungFxClient.TEGAK.vertex(ax - sx0, y0 + 0.04F, az - sz0, 0.0F, 1.0F, riser, -HX[i], 0.0F, -HZ[i]);
			GabungFxClient.TEGAK.vertex(ax + sx0, y0 + 0.04F, az + sz0, 1.0F, 1.0F, riser, -HX[i], 0.0F, -HZ[i]);
			GabungFxClient.TEGAK.vertex(ax + sx0, y1 + 0.04F, az + sz0, 1.0F, 0.0F, riser, -HX[i], 0.0F, -HZ[i]);
			GabungFxClient.TEGAK.vertex(ax - sx0, y1 + 0.04F, az - sz0, 0.0F, 0.0F, riser, -HX[i], 0.0F, -HZ[i]);
		}
	}

	/**
	 * Lays the stair nodes: the trail (resampled every {@link #LARI} blocks, height in half-block steps), then the stair
	 * ahead from the candidate toward the top gate (or along the trail's heading) and on into the sky. Returns the count.
	 */
	static int path(Vec3 here, int want) {
		int n = 0;
		double carry = 0.0D;
		double px = JEJAK[0];
		double py = JEJAK[1];
		double pz = JEJAK[2];
		// the trail, then the candidate's position now as its last point
		for (int p = 1; p <= jejak && n < NODE_MAKS - 1 && n < want; p++) {
			double qx = p < jejak ? JEJAK[p * 3] : here.x;
			double qy = p < jejak ? JEJAK[p * 3 + 1] : here.y;
			double qz = p < jejak ? JEJAK[p * 3 + 2] : here.z;
			double dx = qx - px;
			double dz = qz - pz;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1.0E-3D) {
				py = Math.max(py, qy);
				continue;
			}
			float hx = (float) (dx / len);
			float hz = (float) (dz / len);
			double s = carry;
			while (s < len && n < NODE_MAKS - 1 && n < want) {
				double t = s / len;
				NX[n] = px + dx * t;
				NZ[n] = pz + dz * t;
				NY[n] = Math.floor((py + (qy - py) * t) * 2.0D + 0.25D) / 2.0D;
				HX[n] = hx;
				HZ[n] = hz;
				n++;
				s += LARI;
			}
			carry = s - len;
			px = qx;
			py = qy;
			pz = qz;
		}
		if (n == 0) {
			return 0;
		}
		// heading and slope ahead
		double lx = here.x;
		double ly = here.y;
		double lz = here.z;
		double hx;
		double hz;
		double slope;
		double toTop = Double.MAX_VALUE;
		if (atas != null && Math.hypot(atas[0] - lx, atas[2] - lz) > 1.5D) {
			double dx = atas[0] - lx;
			double dz = atas[2] - lz;
			toTop = Math.sqrt(dx * dx + dz * dz);
			hx = dx / toTop;
			hz = dz / toTop;
			slope = Math.max(0.25D, Math.min(1.4D, (atas[1] - ly) / toTop));
		} else {
			int back = Math.max(0, n - 9);
			double dx = NX[n - 1] - NX[back];
			double dz = NZ[n - 1] - NZ[back];
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 0.5D) {
				hx = HX[n - 1];
				hz = HZ[n - 1];
				slope = 0.6D;
			} else {
				hx = dx / len;
				hz = dz / len;
				slope = Math.max(0.35D, Math.min(0.9D, (NY[n - 1] - NY[back]) / len));
			}
		}
		double y = NY[n - 1];
		double s = 0.0D;
		while (n < want && n < NODE_MAKS) {
			s += LARI;
			double k = s <= toTop ? slope : 0.55D;
			y += LARI * k;
			NX[n] = lx + hx * s;
			NZ[n] = lz + hz * s;
			NY[n] = y;
			HX[n] = (float) hx;
			HZ[n] = (float) hz;
			if (GabungFxClient.jarak(NX[n], NY[n], NZ[n]) > JANGKAU) {
				n++;
				break;
			}
			n++;
		}
		return n;
	}

	/** Mist puffs drifting round the foot of the stair. */
	private static void mist(int count, float vis, double now) {
		double fx = bawah != null ? bawah[0] : JEJAK[0];
		double fy = bawah != null ? bawah[1] : JEJAK[1];
		double fz = bawah != null ? bawah[2] : JEJAK[2];
		if (GabungFxClient.jarak(fx, fy, fz) > JANGKAU) {
			return;
		}
		for (int i = 0; i < count; i++) {
			double a = i * 2.399D + now * 0.004D;
			double r = 1.5D + (i % 5) * 0.9D;
			double x = fx + Math.cos(a) * r;
			double z = fz + Math.sin(a) * r;
			double y = fy + 0.3D + (i % 3) * 0.35D + Math.sin(now * 0.02D + i) * 0.15D;
			GabungFxClient.sprite(GabungFxClient.LUNAK, x, y, z, 1.6F + (i % 4) * 0.35F, GabungFxClient.argb(0.32F * vis, 0xDDF5EE), GabungFxClient.SEL_KABUT,
					(float) (now * 0.003D + i));
		}
	}

	/** The heart demon's face before the candidate and a gust of light streaking past (the two seconds of an illusion). */
	private static void illusion(net.minecraft.client.player.LocalPlayer player, float demon, float vis, long since) {
		Vec3 eye = player.getEyePosition(GabungFxClient.partial);
		Vec3 look = player.getViewVector(GabungFxClient.partial);
		double lx = look.x;
		double lz = look.z;
		double len = Math.max(1.0E-3D, Math.sqrt(lx * lx + lz * lz));
		lx /= len;
		lz /= len;
		float wobble = (float) Math.sin(GabungFxClient.now * 0.7D) * 0.06F;
		float size = 1.15F + (float) since / ILUSI_TICKS * 0.35F;
		GabungFxClient.billboard(GabungFxClient.WAJAH, eye.x + lx * 2.2D, eye.y + 0.1D + wobble, eye.z + lz * 2.2D, size, size,
				GabungFxClient.argb(0.85F * demon * vis, DEMON), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F);
		// the gust: twelve streaks crossing in front of the candidate, right to left, over the first 24 ticks
		float t = Math.min(1.0F, since / 24.0F);
		double sx = -lz;
		double sz = lx;
		for (int i = 0; i < 12; i++) {
			double off = (t * 9.0D - 4.5D) - (i % 4) * 0.8D;
			double fwd = 1.2D + (i % 3) * 0.9D;
			double y = eye.y - 0.9D + (i % 6) * 0.35D;
			double x0 = eye.x + lx * fwd - sx * off;
			double z0 = eye.z + lz * fwd - sz * off;
			int c = GabungFxClient.argb(0.5F * (1.0F - t) * vis, 0xE8DDFF);
			GabungFxClient.beam(GabungFxClient.CAHAYA, x0, y, z0, x0 - sx * 1.6D, y + 0.05D, z0 - sz * 1.6D, 0.06F, 0, c);
		}
	}

	private TanggaIlusiGabung() {
	}
}
