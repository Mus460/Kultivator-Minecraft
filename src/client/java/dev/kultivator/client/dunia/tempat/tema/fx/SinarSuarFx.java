package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.dunia.tempat.tema.pelabuhan.ApiSuarEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sinar Mercusuar, the beams of the Pelita Angin lamp of the Kota Pelabuhan (DESIGN 38.T.3, 38.T.9.3, 38.T.9.4): two
 * additive beams 4 × 64 blocks turning with the lens of every lit {@link ApiSuarEntity} (state {@code KEADAAN_MENYALA},
 * which 38TK lights 12500-23500 and in a sea storm), in step with the lens of {@code ApiSuarRenderer}
 * ({@code (game % 240000 + partial) × 3°} plus the entity's yaw).
 * <p>
 * A beam is a camera-turned ribbon ({@code sinar}: bright core, fading along its length) widening from 0.8 to 4 blocks,
 * tilted 3 degrees down to the sea; Maximal adds a wide soft halo ribbon around each. The lens glows, and when a beam
 * sweeps across the watcher a six-ray flare ({@code kilau}) blooms on the lamp. Rain and dusk make the beams stronger,
 * noon all but hides them. The cracked lens ({@code VARIAN_RETAK}, the start of "Pelita yang Padam") gives a dim amber
 * flickering light; the Mutiara Fajar lens ({@code VARIAN_FAJAR}) a blue core with a gold rim. Minimal: only the point
 * of light and its flare. {@code TemaNet.CAHAYA apa=suar} ({@code x, y, z, tick, panjang}) lights the lamp near that
 * point for a scene whatever its state (the 4 s re-lighting of quest step 7 sweeps blue-gold).
 */
final class SinarSuarFx {

	static final float PANJANG = 64.0F;
	static final double JANGKAU = 256.0D;

	private record Paksa(double x, double y, double z, long dari, long sampai) {
	}

	private static final List<ApiSuarEntity> LAMPU = new ArrayList<>();
	private static final Map<Long, Paksa> PAKSA = new HashMap<>();

	private SinarSuarFx() {
	}

	static void terima(long situs, CompoundTag d, long now) {
		if (!d.getBooleanOr("nyala", true)) {
			PAKSA.remove(situs);
			return;
		}
		long dari = d.getLongOr("tick", now);
		PAKSA.put(situs, new Paksa(d.getIntOr("x", 0) + 0.5D, d.getIntOr("y", 0), d.getIntOr("z", 0) + 0.5D, dari, dari + Math.max(20, d.getIntOr("panjang", 80))));
	}

	/** Preview: a lamp scene at (x, y, z) for {@code panjang} ticks (also drawn without an entity). */
	static void uji(double x, double y, double z, long now, int panjang) {
		PAKSA.put(-3L, new Paksa(x, y, z, now, now + panjang));
	}

	static boolean aktif() {
		return !LAMPU.isEmpty() || !PAKSA.isEmpty();
	}

	static void bersihkan() {
		LAMPU.clear();
		PAKSA.clear();
	}

	/** Every 20 ticks: the lamps within 256 blocks (at most 4). */
	static void pindai(ClientLevel level, Vec3 at) {
		LAMPU.clear();
		long now = level.getGameTime();
		PAKSA.values().removeIf(p -> p.sampai() < now);
		List<ApiSuarEntity> found = level.getEntitiesOfClass(ApiSuarEntity.class, new AABB(at, at).inflate(JANGKAU, 128.0D, JANGKAU));
		for (ApiSuarEntity e : found) {
			if (LAMPU.size() < 4) {
				LAMPU.add(e);
			}
		}
	}

	private static float paksa(double x, double y, double z, double now) {
		float best = 0.0F;
		for (Paksa p : PAKSA.values()) {
			if (Mth.square(p.x() - x) + Mth.square(p.z() - z) < 24.0D * 24.0D && Math.abs(p.y() - y) < 24.0D) {
				best = Math.max(best, Bingkai.selubung((float) (now - p.dari()), 0.0F, p.sampai() - p.dari(), 10.0F, 16.0F));
			}
		}
		return best;
	}

	static void gambar() {
		double now = Bingkai.sekarang();
		float waktu = (float) (Bingkai.game % 240000L) + Bingkai.pt;
		for (ApiSuarEntity e : LAMPU) {
			if (e.isRemoved()) {
				continue;
			}
			Vec3 p = e.getPosition(Bingkai.pt);
			float force = paksa(p.x, p.y, p.z, now);
			boolean lit = e.keadaan() == ApiSuarEntity.KEADAAN_MENYALA;
			if (!lit && force <= 0.0F) {
				continue;
			}
			float strength = Mth.clamp(e.nilai(), 0.0F, 1.0F);
			if (strength <= 0.0F) {
				strength = 1.0F;
			}
			int varian = force > 0.0F ? ApiSuarEntity.VARIAN_FAJAR : e.varian();
			float yaw = e.getYRot(Bingkai.pt) * Mth.DEG_TO_RAD;
			lampu(p.x, p.y + 0.9D, p.z, waktu * 3.0F * Mth.DEG_TO_RAD + yaw, varian, Math.max(lit ? strength : 0.0F, force));
		}
		// a scene with no lamp entity loaded (or the preview): draw the light at the scene's point
		for (Paksa p : PAKSA.values()) {
			boolean adaLampu = false;
			for (ApiSuarEntity e : LAMPU) {
				if (!e.isRemoved() && e.distanceToSqr(p.x(), p.y(), p.z()) < 24.0D * 24.0D) {
					adaLampu = true;
					break;
				}
			}
			if (!adaLampu) {
				float f = Bingkai.selubung((float) (now - p.dari()), 0.0F, p.sampai() - p.dari(), 10.0F, 16.0F);
				if (f > 0.0F) {
					lampu(p.x(), p.y() + 0.9D, p.z(), waktu * 3.0F * Mth.DEG_TO_RAD, ApiSuarEntity.VARIAN_FAJAR, f);
				}
			}
		}
	}

	private static void lampu(double wx, double wy, double wz, float sudut, int varian, float strength) {
		if (Bingkai.jarak2(wx, wy, wz) > JANGKAU * JANGKAU) {
			return;
		}
		float x = (float) (wx - Bingkai.camX);
		float y = (float) (wy - Bingkai.camY);
		float z = (float) (wz - Bingkai.camZ);
		float t = Bingkai.t;
		boolean retak = varian == ApiSuarEntity.VARIAN_RETAK;
		boolean fajar = varian == ApiSuarEntity.VARIAN_FAJAR;
		float flicker = retak ? 0.55F + 0.45F * Math.abs(Mth.sin(t * 0.7F) * Mth.sin(t * 0.23F + 1.0F)) : 1.0F;
		float s = strength * (retak ? 0.45F : 1.0F) * flicker;
		// dusk, night and rain show a beam; noon nearly hides it
		float sky = Mth.clamp(0.25F + 0.75F * (1.0F - Bingkai.terang) + 0.35F * Bingkai.hujan, 0.2F, 1.0F);
		int core = fajar ? 0xA8D4FF : retak ? 0xFFB060 : 0xFFF2CC;
		int rim = fajar ? 0xFFD27A : retak ? 0xFF8A3A : 0xFFE0A0;
		int light = LightCoordsUtil.FULL_BRIGHT;
		float[] sinar = FxTekstur.sel(FxTekstur.SINAR);
		float[] glow = FxTekstur.sel(FxTekstur.GLOW);
		float[] kilau = FxTekstur.sel(FxTekstur.KILAU);
		int tier = Bingkai.tier;
		float dist = Mth.sqrt(x * x + y * y + z * z);
		// the lens glow: a point of light that stays a few pixels wide far away
		float glowSize = Math.max(1.1F, dist * 0.012F);
		if (!TemaFx.muat(2)) {
			return;
		}
		TemaFx.fxAdd.billboard(x, y, z, glowSize, glowSize, 0.0F, Bingkai.KANAN, Bingkai.ATAS, glow[0], glow[1], glow[2], glow[3],
				Bingkai.argb(0.9F * s, core), light);
		// flare when a beam faces the watcher
		float flare = 0.0F;
		for (int beam = 0; beam < 2; beam++) {
			float a = sudut + beam * Mth.PI;
			float dx = Mth.cos(a);
			float dz = Mth.sin(a);
			float toCam = dist < 1.0E-3F ? 0.0F : (-x * dx - z * dz) / dist;
			flare = Math.max(flare, toCam);
		}
		flare = (float) Math.pow(Math.max(0.0F, flare), 10.0D);
		float flareSize = glowSize * (1.6F + 2.4F * flare);
		TemaFx.fxAdd.billboard(x, y, z, flareSize, flareSize, t * 0.004F, Bingkai.KANAN, Bingkai.ATAS, kilau[0], kilau[1], kilau[2], kilau[3],
				Bingkai.argb((0.25F + 0.75F * flare) * s * (tier == 0 ? 1.0F : 0.85F), rim), light);
		if (tier == 0) {
			return;
		}
		float drop = Mth.sin(-3.0F * Mth.DEG_TO_RAD);
		int need = tier >= 2 ? 4 : 2;
		if (!TemaFx.muat(need)) {
			return;
		}
		for (int beam = 0; beam < 2; beam++) {
			float a = sudut + beam * Mth.PI;
			float dx = Mth.cos(a);
			float dz = Mth.sin(a);
			float ex = x + dx * PANJANG;
			float ey = y + drop * PANJANG;
			float ez = z + dz * PANJANG;
			if (tier >= 2) {
				TemaFx.fxAdd.pita(x, y, z, ex, ey, ez, 1.6F, 8.0F, sinar[0], sinar[1], sinar[2], sinar[3], Bingkai.argb(0.32F * s * sky, rim),
						Bingkai.argb(0.0F, rim), light);
			}
			TemaFx.fxAdd.pita(x, y, z, ex, ey, ez, 0.8F, 4.0F, sinar[0], sinar[1], sinar[2], sinar[3], Bingkai.argb(0.78F * s * sky, core),
					Bingkai.argb(0.05F * s * sky, rim), light);
		}
	}
}
