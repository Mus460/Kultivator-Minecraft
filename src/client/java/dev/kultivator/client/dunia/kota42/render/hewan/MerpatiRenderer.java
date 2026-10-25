package dev.kultivator.client.dunia.kota42.render.hewan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.dunia.kota42.hewan.KawananMerpatiEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * {@code kultivator:kawanan_merpati} (DESIGN 42.7.5, 42.12.2, 42.12.4, 42.12.6): one entity, a whole flock of pigeons drawn
 * by a client boid. Each bird is the generated {@code merpati} mesh (2 x 2 x 4 body, head with beak and cere, iridescent
 * neck, hinged wings, fan tail) in grey, white or brown ({@link KawananMerpatiEntity#VARIAN_CAMPUR} mixes them per bird),
 * textures {@code hewan/merpati_<abu|putih|coklat>.png}.
 * <p>
 * The flock ({@link Kawanan}) is simulated here, on the client, in fixed 20 Hz steps from the synced seed
 * ({@link KawananMerpatiEntity#benih()}), relative to the entity (which may drift):
 * <ul>
 * <li>{@link KawananMerpatiEntity#KEADAAN_MENDARAT}: birds walk about the ground within 1.8 blocks, nodding as they step,
 * stopping to peck; birds still in the air glide down and land;</li>
 * <li>{@link KawananMerpatiEntity#KEADAAN_TERBANG}: all burst up and away, then fly as a boid (cohesion 0.02, separation
 * 0.6 blocks, alignment 0.05) climbing towards six blocks above, wings beating every 6 ticks, gliding when sinking;</li>
 * <li>{@link KawananMerpatiEntity#KEADAAN_BERPUTAR}: the same boid wheeling round a circle of {@code nilai()} blocks
 * (4 when unset) five blocks up.</li>
 * </ul>
 * Birds drawn per flock follow {@code EffectQuality}: 4 on Minimal, 8 normally, 16 on Maximal (never more than the
 * flock's {@link KawananMerpatiEntity#jumlah()}). Beyond 64 blocks wings and heads stop moving; beyond 96 nothing is drawn.
 */
public class MerpatiRenderer extends EntityRenderer<KawananMerpatiEntity, MerpatiRenderer.State> {

	static final String[] WARNA = {"abu", "putih", "coklat"};
	static final double BATAS = 96.0D;
	static final double JAUH = 64.0D;
	/** Model pixels from the feet to the middle of the body (the pitch pivot). */
	static final float TENGAH = 22.3F / 16.0F;

	/** The pose of one bird (one model submit). */
	public static class Burung extends EntityRenderState {
		public boolean terbang;
		/** Wing beat phase (radians); gliding birds hold {@link #luncur}. */
		public float kepak;
		public boolean luncur;
		/** Head nod while walking, -1..1. */
		public float angguk;
		/** Pecking 0..1. */
		public float patuk;
		public boolean diam;
		/** Position relative to the entity (blocks), heading and pitch (degrees), texture index. */
		public float x;
		public float y;
		public float z;
		public float arah;
		public float dongak;
		public int warna;
	}

	/** The flock state of one frame. */
	public static class State extends EntityRenderState {
		public int jumlah;
		public final Burung[] burung = new Burung[KawananMerpatiEntity.JUMLAH_MAKS];
	}

	private final KotaModel<Burung> model;
	private final RenderType[] tipe = new RenderType[WARNA.length];
	private final Map<KawananMerpatiEntity, Kawanan> kawanan = new WeakHashMap<>();

	public MerpatiRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new KotaModel<>(context.bakeLayer(HewanRenderClient.MERPATI), MerpatiRenderer::pose);
		Identifier[] tex = HewanRenderClient.tekstur("merpati", WARNA);
		for (int i = 0; i < tex.length; i++) {
			tipe[i] = RenderTypes.entityCutout(tex[i]);
		}
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(KawananMerpatiEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		double max = BATAS * (EffectQuality.minimal() ? 0.75D : 1.0D);
		if (entity.distanceToSqr(camX, camY, camZ) > max * max) {
			return false;
		}
		return super.shouldRender(entity, culler, camX, camY, camZ, partialTicks);
	}

	@Override
	protected AABB getBoundingBoxForCulling(KawananMerpatiEntity entity, float partialTicks) {
		// the flock wheels up to ~8 blocks round and above its entity
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(9.0D, 0.0D, 9.0D).expandTowards(0.0D, 9.0D, 0.0D);
	}

	@Override
	public void extractRenderState(KawananMerpatiEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		Kawanan k = kawanan.computeIfAbsent(entity, e -> new Kawanan(e.benih(), Mth.clamp(e.jumlah(), KawananMerpatiEntity.JUMLAH_MIN,
				KawananMerpatiEntity.JUMLAH_MAKS), e.varian()));
		long now = entity.level().getGameTime();
		float radius = entity.nilai() > 0.5F ? entity.nilai() : 4.0F;
		k.majuKe(now, entity.keadaan(), radius);
		int cap = EffectQuality.minimal() ? 4 : EffectQuality.maximal() ? 16 : 8;
		int n = Math.min(k.n, cap);
		boolean jauh = state.distanceToCameraSq > JAUH * JAUH;
		float waktu = (float) (now % 240000L) + partialTicks;
		state.jumlah = n;
		for (int i = 0; i < n; i++) {
			Burung b = new Burung();
			k.isi(i, partialTicks, waktu, jauh, b);
			b.lightCoords = state.lightCoords;
			b.outlineColor = state.outlineColor;
			state.burung[i] = b;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < state.jumlah; i++) {
			Burung b = state.burung[i];
			if (b == null) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(b.x, b.y, b.z);
			poseStack.rotateDegrees(Axis.YP, 180.0F - b.arah);
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			if (b.dongak != 0.0F) {
				poseStack.translate(0.0F, TENGAH, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -b.dongak);
				poseStack.translate(0.0F, -TENGAH, 0.0F);
			}
			collector.submitModel(model, b, poseStack, tipe[Mth.clamp(b.warna, 0, tipe.length - 1)], b.lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					b.outlineColor);
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}

	static void pose(KotaModel<Burung> m, Burung b) {
		ModelPart sk = m.p("sayap_k");
		ModelPart skn = m.p("sayap_kn");
		ModelPart kepala = m.p("kepala");
		if (b.terbang) {
			float f = b.diam || b.luncur ? 0.08F : Mth.sin(b.kepak) * 0.95F;
			sk.zRot = f;
			skn.zRot = -f;
			m.p("kaki_k").xRot = 1.3F;
			m.p("kaki_kn").xRot = 1.3F;
			m.p("ekor").xRot -= 0.15F;
		} else {
			// folded along the flanks
			sk.zRot = -1.65F;
			skn.zRot = 1.65F;
			sk.xScale = 0.62F;
			skn.xScale = 0.62F;
			sk.xRot = -0.08F;
			skn.xRot = -0.08F;
			if (!b.diam) {
				kepala.z -= b.angguk * 0.35F;
				kepala.xRot = b.patuk * 1.0F;
				kepala.y += b.patuk * 0.5F;
			}
		}
	}

	// ================================================================== the flock

	/**
	 * The client boid of one flock: positions and velocities relative to the entity (blocks), stepped at 20 Hz from the
	 * flock's seed. A gap of more than 100 ticks (chunk reload, pause) re-seats the birds for the current state.
	 */
	static final class Kawanan {
		static final double PISAH = 0.6D;
		static final double KOHESI = 0.02D;
		static final double SELARAS = 0.05D;
		static final double DARAT_R = 1.8D;

		final int n;
		final int varian;
		final Random acak;
		final double[] x;
		final double[] y;
		final double[] z;
		final double[] px;
		final double[] py;
		final double[] pz;
		final double[] vx;
		final double[] vy;
		final double[] vz;
		final double[] tx;
		final double[] tz;
		final int[] tunggu;
		final float[] fase;
		final float[] langkah;
		final int[] warna;
		long tick = Long.MIN_VALUE;
		int keadaan = -1;
		long t;

		Kawanan(int benih, int n, int varian) {
			this.n = n;
			this.varian = varian;
			this.acak = new Random(benih * 0x5DEECE66DL + 11L);
			x = new double[n];
			y = new double[n];
			z = new double[n];
			px = new double[n];
			py = new double[n];
			pz = new double[n];
			vx = new double[n];
			vy = new double[n];
			vz = new double[n];
			tx = new double[n];
			tz = new double[n];
			tunggu = new int[n];
			fase = new float[n];
			langkah = new float[n];
			warna = new int[n];
			for (int i = 0; i < n; i++) {
				fase[i] = acak.nextFloat() * Mth.TWO_PI;
				warna[i] = switch (varian) {
					case KawananMerpatiEntity.VARIAN_PUTIH -> acak.nextInt(6) == 0 ? 0 : 1;
					case KawananMerpatiEntity.VARIAN_CAMPUR -> acak.nextInt(3);
					default -> acak.nextInt(7) == 0 ? 2 : 0;
				};
			}
		}

		/** Steps the flock up to game tick {@code now}. */
		void majuKe(long now, int keadaanBaru, float radius) {
			if (tick == Long.MIN_VALUE || now - tick > 100L || now < tick) {
				duduk(keadaanBaru, radius);
				tick = now;
				keadaan = keadaanBaru;
				return;
			}
			while (tick < now) {
				langkahSatu(keadaanBaru, radius);
				tick++;
			}
		}

		/** Places every bird for a state without history (first sight or after a gap). */
		void duduk(int k, float radius) {
			for (int i = 0; i < n; i++) {
				double a = acak.nextDouble() * Math.PI * 2.0D;
				double r = acak.nextDouble() * (k == KawananMerpatiEntity.KEADAAN_MENDARAT ? DARAT_R : 2.5D);
				x[i] = Math.cos(a) * r;
				z[i] = Math.sin(a) * r;
				y[i] = k == KawananMerpatiEntity.KEADAAN_MENDARAT ? 0.0D : 5.0D + acak.nextDouble() * 1.5D;
				double h = acak.nextDouble() * Math.PI * 2.0D;
				vx[i] = k == KawananMerpatiEntity.KEADAAN_MENDARAT ? 0.0D : Math.cos(h) * 0.25D;
				vz[i] = k == KawananMerpatiEntity.KEADAAN_MENDARAT ? 0.0D : Math.sin(h) * 0.25D;
				vy[i] = 0.0D;
				tx[i] = x[i];
				tz[i] = z[i];
				tunggu[i] = acak.nextInt(30);
				px[i] = x[i];
				py[i] = y[i];
				pz[i] = z[i];
			}
		}

		void langkahSatu(int k, float radius) {
			t++;
			for (int i = 0; i < n; i++) {
				px[i] = x[i];
				py[i] = y[i];
				pz[i] = z[i];
			}
			if (k != keadaan) {
				if (k != KawananMerpatiEntity.KEADAAN_MENDARAT && keadaan == KawananMerpatiEntity.KEADAAN_MENDARAT) {
					// take-off burst: up and away from the middle
					for (int i = 0; i < n; i++) {
						double d = Math.max(0.2D, Math.sqrt(x[i] * x[i] + z[i] * z[i]));
						vx[i] = x[i] / d * (0.08D + acak.nextDouble() * 0.08D);
						vz[i] = z[i] / d * (0.08D + acak.nextDouble() * 0.08D);
						vy[i] = 0.28D + acak.nextDouble() * 0.1D;
					}
				}
				if (k == KawananMerpatiEntity.KEADAAN_MENDARAT) {
					for (int i = 0; i < n; i++) {
						double a = acak.nextDouble() * Math.PI * 2.0D;
						double r = Math.sqrt(acak.nextDouble()) * DARAT_R;
						tx[i] = Math.cos(a) * r;
						tz[i] = Math.sin(a) * r;
						tunggu[i] = 0;
					}
				}
				keadaan = k;
			}
			if (k == KawananMerpatiEntity.KEADAAN_MENDARAT) {
				darat();
			} else {
				terbang(k, radius);
			}
		}

		/** On the ground: walk to a spot, peck a while, pick another; birds still airborne glide in to land. */
		void darat() {
			for (int i = 0; i < n; i++) {
				if (y[i] > 0.02D) {
					double dx = tx[i] - x[i];
					double dz = tz[i] - z[i];
					double d = Math.sqrt(dx * dx + dz * dz);
					double s = Math.min(0.16D, d * 0.08D + 0.02D);
					vx[i] = d > 1.0E-4D ? dx / d * s : 0.0D;
					vz[i] = d > 1.0E-4D ? dz / d * s : 0.0D;
					vy[i] = Math.max(-0.16D, -y[i] * 0.12D - 0.02D);
					x[i] += vx[i];
					y[i] = Math.max(0.0D, y[i] + vy[i]);
					z[i] += vz[i];
					continue;
				}
				y[i] = 0.0D;
				vy[i] = 0.0D;
				if (tunggu[i] > 0) {
					tunggu[i]--;
					vx[i] = 0.0D;
					vz[i] = 0.0D;
					continue;
				}
				double dx = tx[i] - x[i];
				double dz = tz[i] - z[i];
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < 0.05D) {
					tunggu[i] = 10 + acak.nextInt(40);
					double a = acak.nextDouble() * Math.PI * 2.0D;
					double r = Math.sqrt(acak.nextDouble()) * DARAT_R;
					tx[i] = Math.cos(a) * r;
					tz[i] = Math.sin(a) * r;
					continue;
				}
				double s = 0.035D;
				vx[i] = dx / d * s;
				vz[i] = dz / d * s;
				// keep a little room on the ground (0.25 blocks)
				for (int j = 0; j < n; j++) {
					if (j == i) {
						continue;
					}
					double ex = x[i] - x[j];
					double ez = z[i] - z[j];
					double e2 = ex * ex + ez * ez;
					if (e2 < 0.0625D && e2 > 1.0E-6D) {
						double e = Math.sqrt(e2);
						vx[i] += ex / e * 0.01D;
						vz[i] += ez / e * 0.01D;
					}
				}
				x[i] += vx[i];
				z[i] += vz[i];
				langkah[i] += 0.9F;
			}
		}

		/** In the air: the boid (cohesion, separation, alignment) plus a goal above the roost or on the wheel. */
		void terbang(int k, float radius) {
			double cx = 0.0D;
			double cy = 0.0D;
			double cz = 0.0D;
			double ax = 0.0D;
			double ay = 0.0D;
			double az = 0.0D;
			for (int i = 0; i < n; i++) {
				cx += x[i];
				cy += y[i];
				cz += z[i];
				ax += vx[i];
				ay += vy[i];
				az += vz[i];
			}
			cx /= n;
			cy /= n;
			cz /= n;
			ax /= n;
			ay /= n;
			az /= n;
			double gx;
			double gy;
			double gz;
			if (k == KawananMerpatiEntity.KEADAAN_BERPUTAR) {
				double w = t * 0.05D;
				gx = Math.cos(w) * radius;
				gy = 5.0D;
				gz = Math.sin(w) * radius;
			} else {
				gx = Math.sin(t * 0.021D) * 3.0D;
				gy = 6.0D + Math.sin(t * 0.013D);
				gz = Math.cos(t * 0.017D) * 3.0D;
			}
			double tarik = k == KawananMerpatiEntity.KEADAAN_BERPUTAR ? 0.012D : 0.005D;
			for (int i = 0; i < n; i++) {
				double nx = vx[i] + (cx - x[i]) * KOHESI + (ax - vx[i]) * SELARAS + (gx - x[i]) * tarik;
				double ny = vy[i] + (cy - y[i]) * KOHESI + (ay - vy[i]) * SELARAS + (gy - y[i]) * tarik;
				double nzz = vz[i] + (cz - z[i]) * KOHESI + (az - vz[i]) * SELARAS + (gz - z[i]) * tarik;
				for (int j = 0; j < n; j++) {
					if (j == i) {
						continue;
					}
					double ex = x[i] - x[j];
					double ey = y[i] - y[j];
					double ez = z[i] - z[j];
					double e2 = ex * ex + ey * ey + ez * ez;
					if (e2 < PISAH * PISAH && e2 > 1.0E-6D) {
						double e = Math.sqrt(e2);
						double f = (PISAH - e) / PISAH * 0.06D;
						nx += ex / e * f;
						ny += ey / e * f;
						nzz += ez / e * f;
					}
				}
				if (y[i] < 1.0D) {
					ny += 0.03D;  // never skim the ground
				}
				double s = Math.sqrt(nx * nx + ny * ny + nzz * nzz);
				double lo = 0.16D;
				double hi = 0.38D;
				if (s > hi) {
					nx *= hi / s;
					ny *= hi / s;
					nzz *= hi / s;
				} else if (s < lo && s > 1.0E-6D) {
					nx *= lo / s;
					ny *= lo / s;
					nzz *= lo / s;
				}
				vx[i] = nx;
				vy[i] = ny;
				vz[i] = nzz;
				x[i] += vx[i];
				y[i] += vy[i];
				z[i] += vz[i];
			}
		}

		/** Fills bird {@code i}'s pose for this frame. */
		void isi(int i, float partial, float waktu, boolean jauh, Burung b) {
			b.x = (float) Mth.lerp(partial, px[i], x[i]);
			b.y = (float) Mth.lerp(partial, py[i], y[i]);
			b.z = (float) Mth.lerp(partial, pz[i], z[i]);
			b.warna = warna[i];
			b.diam = jauh;
			double hs = Math.sqrt(vx[i] * vx[i] + vz[i] * vz[i]);
			if (hs > 1.0E-4D) {
				b.arah = (float) (Mth.atan2(-vx[i], vz[i]) * Mth.RAD_TO_DEG);
			} else {
				// standing: turn now and then on the spot
				b.arah = (fase[i] * Mth.RAD_TO_DEG + Mth.floor((waktu + fase[i] * 40.0F) / 50.0F) * 67.0F) % 360.0F;
			}
			boolean udara = keadaan != KawananMerpatiEntity.KEADAAN_MENDARAT || y[i] > 0.05D;
			b.terbang = udara;
			if (udara) {
				b.dongak = (float) (Mth.atan2(vy[i], Math.max(hs, 1.0E-3D)) * Mth.RAD_TO_DEG) * 0.6F;
				b.luncur = vy[i] < -0.06D;
				b.kepak = waktu * 1.05F + fase[i];
			} else {
				b.dongak = 0.0F;
				boolean jalan = hs > 1.0E-4D;
				b.angguk = jalan ? Mth.sin(langkah[i] + partial * 0.9F) : 0.0F;
				b.patuk = !jalan && tunggu[i] > 0 ? HewanRenderer.denyut(waktu + fase[i] * 10.0F, 14.0F, 5.0F) : 0.0F;
			}
		}
	}
}
