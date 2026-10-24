package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.dunia.tempat.tema.air.PerahuNagaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The crews of the dragon boats of the Kota Air (DESIGN 38.T.6.4 Lomba Perahu Naga, 38.T.9.1 "pendayung = impostor
 * klien"): client-only cuboid figures on every {@link PerahuNagaEntity}, no entities. Ten pairs of paddlers face the
 * bow, five rows before and five behind the drum amidships; the drummer sits at the drum with an old face, the
 * helmsman stands at the stern on his long sweep oar.
 * <p>
 * In a race ({@code KEADAAN_LOMBA}) the crew paddles in unison, stroke rate 0.6-1.8 per second from the boat's
 * {@code nilai}: lean forward, the catch, the pull with the blade in the water, the recovery with the blade up; the
 * drummer's sticks fall on every catch and the drum is heard within 48 blocks (with the paddles' splash). Idle, they
 * rest the paddles across the gunwale and breathe. Tunics by boat: red (Merah), green (Hijau), blue (Biru), dark (Hitam).
 * <p>
 * Skin {@code pendayung.png} (4 × 4 cells of 16 px, see the generator). Detail by distance: within 40 blocks
 * full figures (head, torso, two arms, paddle shaft and blade), to 96 head + torso + paddle, past that nothing; Minimal
 * draws only the near boat's crew at the middle detail. Cuboid geometry in one cutout batch, lit by the world light.
 */
final class PendayungImpostor {

	static final double JANGKAU = 96.0D;
	/** Seat top above the boat entity (PerahuNagaRenderer, 38TV-M). */
	static final float DUDUK = 0.45F;
	/** Half the beam between the two paddler columns. */
	static final float LAJUR = 0.55F;
	static final float[] BARIS = {1.5F, 2.75F, 4.0F, 5.25F, 6.5F, -1.5F, -2.75F, -4.0F, -5.25F, -6.5F};

	private static final List<PerahuNagaEntity> PERAHU = new ArrayList<>();
	private static final Quaternionf BOAT = new Quaternionf();
	private static final Quaternionf PART = new Quaternionf();
	private static final Quaternionf FULL = new Quaternionf();
	private static final Quaternionf KEEP = new Quaternionf();
	private static final Vector3f V = new Vector3f();
	// uv per face (north, south, west, east, up, down) as 6 x {u0, v0, u1, v1}
	private static final float[] KEPALA = new float[24];
	private static final float[] KEPALA_TUA = new float[24];
	private static final float[] BADAN_MERAH = new float[24];
	private static final float[] BADAN_KUNING = new float[24];
	private static final float[] LENGAN_MERAH = new float[24];
	private static final float[] LENGAN_KUNING = new float[24];
	private static final float[] KAYU = new float[24];
	private static final float[] CELANA = new float[24];
	private static boolean uvSiap;
	private static long ketukBerikut;
	private static double ujiX = Double.NaN;
	private static double ujiY;
	private static double ujiZ;
	private static float ujiYaw;
	private static long ujiSampai;

	private PendayungImpostor() {
	}

	static boolean aktif() {
		return !PERAHU.isEmpty() || !Double.isNaN(ujiX);
	}

	static void bersihkan() {
		PERAHU.clear();
		ujiX = Double.NaN;
	}

	/** Preview: a racing crew (no hull) in front of the player for {@code panjang} ticks. */
	static void uji(double x, double y, double z, float yawDeg, long now, int panjang) {
		ujiX = x;
		ujiY = y;
		ujiZ = z;
		ujiYaw = yawDeg;
		ujiSampai = now + panjang;
	}

	static void pindai(ClientLevel level, Vec3 at) {
		PERAHU.clear();
		for (PerahuNagaEntity e : level.getEntitiesOfClass(PerahuNagaEntity.class, new AABB(at, at).inflate(JANGKAU, 48.0D, JANGKAU))) {
			if (PERAHU.size() < 6) {
				PERAHU.add(e);
			}
		}
	}

	/** Stroke rate in strokes per tick for a boat's {@code nilai}. */
	private static float laju(float nilai) {
		return (0.6F + 1.2F * Mth.clamp(nilai, 0.0F, 1.0F)) / 20.0F;
	}

	static void tick(Minecraft minecraft, long now) {
		if (!Double.isNaN(ujiX) && now > ujiSampai) {
			ujiX = Double.NaN;
		}
		if (minecraft.player == null || now < ketukBerikut) {
			return;
		}
		// the drum of the nearest racing boat within 48 blocks, on each catch
		PerahuNagaEntity near = null;
		double best = 48.0D * 48.0D;
		for (PerahuNagaEntity e : PERAHU) {
			if (!e.isRemoved() && e.keadaan() == PerahuNagaEntity.KEADAAN_LOMBA) {
				double d = e.distanceToSqr(minecraft.player);
				if (d < best) {
					best = d;
					near = e;
				}
			}
		}
		if (near == null) {
			ketukBerikut = now + 10L;
			return;
		}
		float rate = laju(near.nilai());
		int period = Math.max(8, Math.round(1.0F / rate));
		ketukBerikut = now + period;
		float vol = 0.5F * (1.0F - (float) (Math.sqrt(best) / 48.0D));
		TemaSuara.sekali(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), vol, 0.7F, near.getX(), near.getY() + 1.0D, near.getZ());
		TemaSuara.sekali(SoundEvents.BOAT_PADDLE_WATER, vol * 0.6F, 0.8F + 0.2F * (float) Math.random(), near.getX(), near.getY(), near.getZ());
	}

	// ================================================================ drawing

	static void gambar() {
		siapkanUv();
		float pt = Bingkai.pt;
		int tier = Bingkai.tier;
		int drawn = 0;
		if (!Double.isNaN(ujiX)) {
			kru(ujiX, ujiY, ujiZ, ujiYaw, PerahuNagaEntity.VARIAN_MERAH, true, 0.7F, tier == 0 ? 1 : 2, 0);
		}
		for (PerahuNagaEntity e : PERAHU) {
			if (e.isRemoved()) {
				continue;
			}
			Vec3 p = e.getPosition(pt);
			double d = Math.sqrt(Bingkai.jarak2(p.x, p.y, p.z));
			if (d > JANGKAU) {
				continue;
			}
			int detail = d < 40.0D ? 2 : 1;
			if (tier == 0) {
				if (drawn > 0 || d > 48.0D) {
					continue;
				}
				detail = 1;
			}
			boolean lomba = e.keadaan() == PerahuNagaEntity.KEADAAN_LOMBA;
			kru(p.x, p.y, p.z, e.getYRot(pt), e.varian(), lomba, e.nilai(), detail, e.getId());
			drawn++;
		}
	}

	/** One crew at boat position (x, y, z) heading {@code yawDeg}. */
	private static void kru(double x, double y, double z, float yawDeg, int varian, boolean lomba, float nilai, int detail, int salt) {
		BOAT.rotationY(-yawDeg * Mth.DEG_TO_RAD);
		float bx = (float) (x - Bingkai.camX);
		float by = (float) (y - Bingkai.camY);
		float bz = (float) (z - Bingkai.camZ);
		int light = Bingkai.cahaya(x, y + 1.0D, z);
		boolean merah = varian == PerahuNagaEntity.VARIAN_MERAH || varian == PerahuNagaEntity.VARIAN_HITAM;
		int tint = switch (varian) {
			case PerahuNagaEntity.VARIAN_HIJAU -> 0xFFA8F0A0;
			case PerahuNagaEntity.VARIAN_BIRU -> 0xFF9CB8FF;
			case PerahuNagaEntity.VARIAN_HITAM -> 0xFF6A6266;
			default -> 0xFFFFFFFF;
		};
		float[] badan = merah ? BADAN_MERAH : BADAN_KUNING;
		float[] lengan = merah ? LENGAN_MERAH : LENGAN_KUNING;
		float t = Bingkai.t;
		float rate = laju(nilai);
		float phase = lomba ? Mth.positiveModulo(t * rate + salt * 0.013F, 1.0F) : 0.0F;
		for (int row = 0; row < BARIS.length; row++) {
			for (int side = -1; side <= 1; side += 2) {
				int need = detail == 2 ? 34 : 16;
				if (!TemaFx.muat(need)) {
					return;
				}
				// a ripple of a few hundredths of a stroke down the boat: crews are never quite one machine
				float ph = lomba ? Mth.positiveModulo(phase - row * 0.004F, 1.0F) : 0.0F;
				float breathe = lomba ? 0.0F : Mth.sin(t * 0.05F + row * 0.9F + side) * 0.03F;
				pendayung(bx, by, bz, side * LAJUR, BARIS[row], side, ph, lomba, breathe, badan, lengan, tint, light, detail);
			}
		}
		if (!TemaFx.muat(24)) {
			return;
		}
		// drummer at the drum amidships, facing the crew of the bow half
		float beat = lomba ? phase : 0.5F;
		float hit = lomba ? (float) Math.pow(1.0F - Math.min(1.0F, beat * 4.0F), 2.0D) : 0.0F;
		penabuh(bx, by, bz, hit, light, tint, badan, lengan);
		// helmsman standing at the stern
		juruMudi(bx, by, bz, t, light, tint, badan, lengan, detail);
	}

	/**
	 * One paddler at seat (sx, sz) of the boat frame (local +z = bow, +x = the boat's left). {@code ph} 0..1 of the
	 * stroke: 0..0.5 the pull (blade in the water, swept from the catch forward to the hip), 0.5..1 the recovery.
	 */
	private static void pendayung(float bx, float by, float bz, float sx, float sz, int side, float ph, boolean lomba, float breathe,
			float[] badan, float[] lengan, int tint, int light, int detail) {
		float lean;
		float sweep;
		float lift;
		if (lomba) {
			if (ph < 0.5F) {
				float k = ph / 0.5F;
				lean = Mth.lerp(k, 0.42F, -0.08F);
				sweep = Mth.lerp(Bingkai.halus(k), 0.95F, -0.55F);
				lift = 0.0F;
			} else {
				float k = (ph - 0.5F) / 0.5F;
				lean = Mth.lerp(k, -0.08F, 0.42F);
				sweep = Mth.lerp(Bingkai.halus(k), -0.55F, 0.95F);
				lift = Mth.sin(k * Mth.PI) * 0.35F;
			}
		} else {
			lean = 0.05F + breathe;
			sweep = 0.0F;
			lift = 0.25F;
		}
		float seatY = DUDUK;
		// torso: pivot at the seat, leaning toward the bow (rotation about the local x axis)
		PART.identity().rotateX(lean);
		bagian(bx, by, bz, sx, seatY, sz, 0.0F, 0.32F, 0.0F, 0.25F, 0.32F, 0.15F, badan, tint, light, true);
		bagian(bx, by, bz, sx, seatY, sz, 0.0F, 0.89F, 0.02F, 0.25F, 0.25F, 0.25F, KEPALA, 0xFFFFFFFF, light, true);
		// the paddle: on the outboard side, pivoting at the upper hand; swept fore and aft, lifted on the recovery
		float ox = side * 0.42F;
		PART.identity().rotateX(lean * 0.5F + sweep).rotateZ(side * (0.32F - lift * 0.4F));
		float hy = seatY + 0.55F + lift * 0.4F;
		if (detail == 2) {
			// arms reach from the shoulders to the shaft
			KEEP.set(PART);
			PART.identity().rotateX(lean + 0.9F + sweep * 0.5F);
			bagian(bx, by, bz, sx + side * 0.18F, seatY + 0.55F, sz, 0.0F, -0.22F, 0.0F, 0.08F, 0.25F, 0.08F, lengan, tint, light, false);
			bagian(bx, by, bz, sx - side * 0.12F, seatY + 0.55F, sz, 0.0F, -0.22F, 0.0F, 0.08F, 0.25F, 0.08F, lengan, tint, light, false);
			PART.set(KEEP);
			bagian(bx, by, bz, sx + ox, hy, sz + 0.25F, 0.0F, -0.62F, 0.0F, 0.03F, 0.68F, 0.03F, KAYU, 0xFFFFFFFF, light, false);
			bagian(bx, by, bz, sx + ox, hy, sz + 0.25F, 0.0F, -1.4F, 0.0F, 0.02F, 0.22F, 0.12F, KAYU, 0xFFE0C8A8, light, false);
		} else {
			bagian(bx, by, bz, sx + ox, hy, sz + 0.25F, 0.0F, -0.85F, 0.0F, 0.035F, 0.85F, 0.05F, KAYU, 0xFFFFFFFF, light, true);
		}
	}

	private static void penabuh(float bx, float by, float bz, float hit, int light, int tint, float[] badan, float[] lengan) {
		float sz = 0.55F;
		PART.identity().rotateY(Mth.PI).rotateX(0.1F + hit * 0.15F);
		bagian(bx, by, bz, 0.0F, DUDUK + 0.1F, sz, 0.0F, 0.32F, 0.0F, 0.25F, 0.32F, 0.15F, badan, tint, light, true);
		bagian(bx, by, bz, 0.0F, DUDUK + 0.1F, sz, 0.0F, 0.89F, 0.02F, 0.25F, 0.25F, 0.25F, KEPALA_TUA, 0xFFFFFFFF, light, true);
		// both sticks rise and fall together on the beat
		float arm = -1.9F + hit * 1.3F;
		PART.identity().rotateY(Mth.PI).rotateX(arm);
		bagian(bx, by, bz, 0.2F, DUDUK + 0.68F, sz, 0.0F, -0.25F, 0.0F, 0.08F, 0.26F, 0.08F, lengan, tint, light, false);
		bagian(bx, by, bz, -0.2F, DUDUK + 0.68F, sz, 0.0F, -0.25F, 0.0F, 0.08F, 0.26F, 0.08F, lengan, tint, light, false);
		bagian(bx, by, bz, 0.2F, DUDUK + 0.68F, sz, 0.0F, -0.62F, 0.0F, 0.025F, 0.18F, 0.025F, KAYU, 0xFFFFFFFF, light, false);
		bagian(bx, by, bz, -0.2F, DUDUK + 0.68F, sz, 0.0F, -0.62F, 0.0F, 0.025F, 0.18F, 0.025F, KAYU, 0xFFFFFFFF, light, false);
	}

	private static void juruMudi(float bx, float by, float bz, float t, int light, int tint, float[] badan, float[] lengan, int detail) {
		float sz = -8.0F;
		float sway = Mth.sin(t * 0.04F) * 0.12F;
		PART.identity().rotateY(sway);
		bagian(bx, by, bz, 0.0F, DUDUK - 0.1F, sz, 0.0F, 0.38F, 0.0F, 0.12F, 0.38F, 0.12F, CELANA, 0xFFFFFFFF, light, true);
		bagian(bx, by, bz, 0.0F, DUDUK - 0.1F, sz, 0.0F, 1.08F, 0.0F, 0.25F, 0.32F, 0.15F, badan, tint, light, true);
		bagian(bx, by, bz, 0.0F, DUDUK - 0.1F, sz, 0.0F, 1.65F, 0.02F, 0.25F, 0.25F, 0.25F, KEPALA_TUA, 0xFFFFFFFF, light, true);
		if (detail < 2) {
			return;
		}
		// the long sweep oar over the stern, held in both hands
		PART.identity().rotateY(sway * 2.0F).rotateX(-1.15F);
		bagian(bx, by, bz, 0.25F, DUDUK + 1.0F, sz, 0.0F, -1.6F, 0.0F, 0.04F, 1.6F, 0.04F, KAYU, 0xFFFFFFFF, light, false);
		PART.identity().rotateY(sway).rotateX(-0.6F);
		bagian(bx, by, bz, 0.18F, DUDUK + 1.25F, sz, 0.0F, -0.22F, 0.0F, 0.08F, 0.25F, 0.08F, lengan, tint, light, false);
	}

	/**
	 * A box of half sizes (hx, hy, hz) whose centre sits at {@code off} (in the part frame {@link #PART}) from the pivot
	 * (px, py, pz) of the boat frame; the boat frame is {@link #BOAT} at camera-relative (bx, by, bz).
	 */
	private static void bagian(float bx, float by, float bz, float px, float py, float pz, float offX, float offY, float offZ, float hx, float hy,
			float hz, float[] uv, int argb, int light, boolean skipDown) {
		V.set(offX, offY, offZ);
		PART.transform(V);
		V.add(px, py, pz);
		BOAT.transform(V);
		FULL.set(BOAT).mul(PART);
		TemaFx.dayung.kotak(bx + V.x, by + V.y, bz + V.z, hx, hy, hz, FULL, uv, argb, light, skipDown);
	}

	// ================================================================ skin cells

	private static void siapkanUv() {
		if (uvSiap) {
			return;
		}
		uvSiap = true;
		// face order: north (back of a figure facing +z), south (front), west, east, up, down
		isi(KEPALA, 2, 0, 0, 0, 1, 0, 1, 0, 3, 0, 3, 0);
		isi(KEPALA_TUA, 2, 0, 0, 3, 1, 0, 1, 0, 3, 0, 3, 0);
		isi(BADAN_MERAH, 2, 1, 0, 1, 1, 1, 1, 1, 3, 1, 3, 1);
		isi(BADAN_KUNING, 3, 2, 2, 2, 3, 2, 3, 2, 3, 2, 3, 3);
		isi(LENGAN_MERAH, 1, 2, 0, 2, 1, 2, 0, 2, 1, 1, 0, 2);
		isi(LENGAN_KUNING, 2, 3, 2, 3, 2, 3, 2, 3, 3, 2, 2, 3);
		isi(KAYU, 1, 3, 1, 3, 1, 3, 1, 3, 1, 3, 1, 3);
		isi(CELANA, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3);
	}

	/** Six (column, row) cells of the 4 × 4 skin into {@code uv}. */
	private static void isi(float[] uv, int... cells) {
		float inset = 0.5F / 64.0F;
		for (int f = 0; f < 6; f++) {
			int col = cells[f * 2];
			int row = cells[f * 2 + 1];
			uv[f * 4] = col * 0.25F + inset;
			uv[f * 4 + 1] = row * 0.25F + inset;
			uv[f * 4 + 2] = col * 0.25F + 0.25F - inset;
			uv[f * 4 + 3] = row * 0.25F + 0.25F - inset;
		}
	}
}
