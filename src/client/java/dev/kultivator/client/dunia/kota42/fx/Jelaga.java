package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.client.screen.SettingsRows;
import dev.kultivator.dunia.kota42.acara.ApiKecilEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Soot marks (DESIGN 42.12.6, decision K7): where a small fire of the town ({@code ApiKecilEntity}) was put out the client leaves a
 * dark translucent smear, flat on the ground under it and standing on every wall within a block of it, drawn from
 * {@code textures/fx/kota42/cahaya.png} (two ragged smears, one streaked upward, one dripping). No block changes and nothing is
 * sent: the client notices the fire entity being removed for good (killed or discarded, not unloaded with its chunk), and the
 * mark fades for three world days (the residents scrub it) unless the session ends first. Budget 0 / 32 / 64 marks within 40
 * blocks, switch {@code kota42.jelaga}. Client thread only.
 */
final class Jelaga {

	/** Three world days of ticks. */
	private static final long UMUR = 72000L;
	private static final int SIMPAN = 96;

	/** One mark: centre, wall normal (0, 0 on the ground), size in blocks, last tick, texture variant 0-1. */
	private record Bekas(double x, double y, double z, int nx, int nz, float ukuran, long sampai, int variasi) {
	}

	private static final List<Bekas> BEKAS = new ArrayList<>();

	static boolean ada() {
		return !BEKAS.isEmpty();
	}

	static void bersihkan() {
		BEKAS.clear();
	}

	/** {@code ClientEntityEvents.ENTITY_UNLOAD}: remember where a small fire ended. */
	static void hilang(Entity e) {
		if (!(e instanceof ApiKecilEntity api) || !SettingsRows.kota42("kota42.jelaga") || Bingkai.level == null) {
			return;
		}
		var alasan = api.getRemovalReason();
		if (alasan == null || !alasan.shouldDestroy() || api.tickCount < 60) {
			return;
		}
		float ukuran = Math.max(0.5F, Math.min(1.6F, 0.6F + api.nilai()));
		long sampai = Bingkai.waktu + UMUR;
		int v = e.getId() & 1;
		tambah(new Bekas(e.getX(), e.getY() + 0.02D, e.getZ(), 0, 0, ukuran * 1.5F, sampai, v));
		var level = Bingkai.level;
		BlockPos at = e.blockPosition();
		int[][] arah = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] a : arah) {
			BlockPos w = at.offset(a[0], 0, a[1]);
			BlockState st = level.getBlockState(w);
			if (!st.getCollisionShape(level, w).isEmpty()) {
				// the face of the wall toward the fire: its plane is half a block from the wall block's middle
				tambah(new Bekas(w.getX() + 0.5D - a[0] * 0.5D, e.getY() + 0.5D + ukuran * 0.45D, w.getZ() + 0.5D - a[1] * 0.5D, -a[0], -a[1], ukuran * 1.6F, sampai, (v + 1) & 1));
			}
		}
	}

	private static void tambah(Bekas b) {
		BEKAS.add(b);
		while (BEKAS.size() > SIMPAN) {
			BEKAS.remove(0);
		}
	}

	static void tik() {
		if (!BEKAS.isEmpty() && Bingkai.waktu % 200L == 0L) {
			BEKAS.removeIf(b -> b.sampai() < Bingkai.waktu);
		}
	}

	static void gambar() {
		if (BEKAS.isEmpty() || Bingkai.tier == 0 || !SettingsRows.kota42("kota42.jelaga")) {
			return;
		}
		var cam = Bingkai.kamera;
		int maks = Bingkai.hitung(0, 32, 64);
		List<Bekas> dekat = new ArrayList<>();
		for (Bekas b : BEKAS) {
			double dx = b.x() - cam.x;
			double dz = b.z() - cam.z;
			if (dx * dx + dz * dz < 40.0D * 40.0D) {
				dekat.add(b);
			}
		}
		dekat.sort(Comparator.comparingDouble(b -> (b.x() - cam.x) * (b.x() - cam.x) + (b.z() - cam.z) * (b.z() - cam.z)));
		Geo.Sink s = Geo.tipe(Geo.CAHAYA, Geo.Jenis.LENGKUNG);
		for (int i = 0; i < Math.min(maks, dekat.size()); i++) {
			Bekas b = dekat.get(i);
			float cx = (float) (b.x() - cam.x);
			float cy = (float) (b.y() - cam.y);
			float cz = (float) (b.z() - cam.z);
			float h = b.ukuran() * 0.5F;
			float u0 = b.variasi() == 0 ? 0.0F : 0.5F;
			float memudar = Math.min(1.0F, (b.sampai() - Bingkai.waktu) / 6000.0F);
			int warna = Geo.argb(0.85F * memudar, 0xFFFFFF);
			int light = Bingkai.cahaya(b.x() + b.nx() * 0.3D, b.y() + 0.3D, b.z() + b.nz() * 0.3D);
			if (b.nx() == 0 && b.nz() == 0) {
				s.quad(cx - h, cy, cz - h, cx - h, cy, cz + h, cx + h, cy, cz + h, cx + h, cy, cz - h, u0, 0.5F, u0 + 0.5F, 1.0F, warna, light, false);
			} else {
				// vertical, facing out of the wall; the right edge as a viewer in front sees it is (nz, -nx)
				float rx = b.nz() * h;
				float rz = -b.nx() * h;
				float e = 0.012F;
				float ox = b.nx() * e;
				float oz = b.nz() * e;
				s.quad(cx - rx + ox, cy + h, cz - rz + oz, cx - rx + ox, cy - h, cz - rz + oz, cx + rx + ox, cy - h, cz + rz + oz, cx + rx + ox, cy + h, cz + rz + oz,
						u0, 0.5F, u0 + 0.5F, 1.0F, warna, light, false);
			}
		}
	}

	private Jelaga() {
	}
}
