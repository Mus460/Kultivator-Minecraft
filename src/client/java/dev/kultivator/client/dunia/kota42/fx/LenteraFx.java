package dev.kultivator.client.dunia.kota42.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.client.dunia.kota42.model.LenteraKota;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.acara.KalenderKota;
import dev.kultivator.dunia.kota42.acara.MusimKota;
import dev.kultivator.sekte.api.Lambang;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The town's paper lanterns lit at night (DESIGN 42.9.2 {@code LenteraKota}): every {@code lentera_gantung} titik the server sent
 * becomes the cuboid lantern of {@link LenteraKota} hanging from its hook. Daytime they hang dark (paper texture); at 18:00 they
 * light in a wave that runs from the edge of the place (the gate) in toward the middle over five seconds and go out in the same wave
 * at 06:00 (festival eve from 17:00); the lighting is computed from the world clock, so every client sees the same wave and nothing
 * is sent. The mode of {@code kota_suasana} sets the colour and kind: mourning turns them long and white, the Ghost Market makes
 * every third one a green gourd, the Mid-Autumn night hangs rabbit lanterns, a protected place shows its protector's colour on the
 * 'sekte' lanterns; a lit lantern gets a soft halo billboard and a pool of light on the street below (decal, no block light).
 * Budgets of 42.12.6: lanterns 16 / 128 / 512 within 64 blocks, halos 0 / 64 / 256, ground patches 0 / 32 / 128. Client thread only.
 */
final class LenteraFx {

	/** Ticks the wave takes from the gate to the middle. */
	private static final float GELOMBANG = 100.0F;
	/** Ticks a lantern takes to light or go out. */
	private static final float SEBENTAR = 14.0F;

	private record Pilihan(TitikPeta.Lentera l, float jarak2, float nyala, int rgb, int jenis) {
	}

	private static final List<Pilihan> PILIH = new ArrayList<>();
	private static List<TitikPeta.Lentera> dekat = List.of();
	private static final Map<Long, float[]> RAMP = new HashMap<>();
	private static final Map<Long, Float> TANAH = new HashMap<>();
	private static int detak;

	/** Nearest candidates, refreshed every 8 ticks; the ramp of every visible place toward its target. */
	static void tik() {
		if (Bingkai.pemain == null) {
			return;
		}
		if (detak++ % 8 == 0) {
			int maks = Bingkai.hitung(16, 128, 512);
			double r = 64.0D;
			var p = Bingkai.pemain.position();
			List<TitikPeta.Lentera> semua = new ArrayList<>();
			for (TitikPeta.Lentera l : TitikPeta.lentera) {
				double dx = l.x() - p.x;
				double dz = l.z() - p.z;
				if (dx * dx + dz * dz <= r * r && Math.abs(l.y() - p.y) < 40.0D) {
					semua.add(l);
				}
			}
			double px = p.x;
			double pz = p.z;
			semua.sort(Comparator.comparingDouble(l -> (l.x() - px) * (l.x() - px) + (l.z() - pz) * (l.z() - pz)));
			dekat = semua.size() > maks ? new ArrayList<>(semua.subList(0, maks)) : semua;
		}
		for (TitikPeta.Lentera l : dekat) {
			float[] r = RAMP.computeIfAbsent(l.situs(), k -> new float[] {Float.NaN});
			KotaPayloads.Suasana s = KotaHidupClient.suasana(l.situs());
			float target = mode(s) != KalenderKota.LENTERA_MATI ? 1.0F : 0.0F;
			r[0] = Float.isNaN(r[0]) ? target : r[0] + Mth.clamp(target - r[0], -0.02F, 0.02F);
		}
	}

	static void bersihkan() {
		dekat = List.of();
		RAMP.clear();
		TANAH.clear();
		PILIH.clear();
	}

	/** The lantern mode: the server's, or the clock's when the atmosphere has not arrived yet. */
	private static int mode(KotaPayloads.@Nullable Suasana s) {
		if (s != null) {
			return s.lentera();
		}
		return Bingkai.antara(18.0D, 6.0D) ? KalenderKota.LENTERA_MALAM : KalenderKota.LENTERA_MATI;
	}

	/** How lit the lantern is by the clock: 0 out, 1 lit, with the wave from the gate. */
	private static float jadwal(TitikPeta.Lentera l, boolean raya) {
		double mulai = raya ? 17.0D : 18.0D;
		double malamLen = Mth.positiveModulo(6.0D - mulai, 24.0D) * 1000.0D;
		double sejak = Bingkai.sejak(mulai) * 1000.0D;
		float tepi = TitikPeta.tepi(l.situs(), l.x(), l.z());
		float tunda = (1.0F - tepi) * GELOMBANG;
		if (sejak < malamLen) {
			return Mth.clamp((float) (sejak - tunda) / SEBENTAR, 0.0F, 1.0F);
		}
		double fajar = sejak - malamLen;
		return 1.0F - Mth.clamp((float) (fajar - tunda) / SEBENTAR, 0.0F, 1.0F);
	}

	static void gambar(PoseStack pose, SubmitNodeCollector collector) {
		if (dekat.isEmpty()) {
			return;
		}
		PILIH.clear();
		var cam = Bingkai.kamera;
		for (TitikPeta.Lentera l : dekat) {
			KotaPayloads.Suasana s = KotaHidupClient.suasana(l.situs());
			int mode = mode(s);
			int bits = s == null ? 0 : s.acara();
			int raya = s == null ? 0 : s.hariRaya();
			float ramp = RAMP.getOrDefault(l.situs(), new float[] {1.0F})[0];
			float nyala = Math.min(jadwal(l, mode == KalenderKota.LENTERA_PESTA && raya != 0), Float.isNaN(ramp) ? 1.0F : ramp);
			if (mode == KalenderKota.LENTERA_DUKA) {
				nyala = Math.max(nyala, 0.2F * (Float.isNaN(ramp) ? 1.0F : ramp));
			}
			int rgb = switch (l.warna()) {
				case TitikKode.WARNA_PUTIH -> LenteraKota.PUTIH;
				case TitikKode.WARNA_HIJAU -> LenteraKota.HIJAU;
				case TitikKode.WARNA_BIRU -> LenteraKota.BIRU;
				case TitikKode.WARNA_SEKTE -> s != null && s.lambang() >= 0 ? Lambang.unpack(s.lambang()).fieldRgb() : LenteraKota.MERAH;
				default -> LenteraKota.MERAH;
			};
			// the kit writes no kind, so the colour picks the shape (white long, green gourd, blue and sect hexagonal) and a red one is sometimes long
			int jenis = l.jenis() != 0 ? l.jenis() : switch (l.warna()) {
				case TitikKode.WARNA_PUTIH -> LenteraKota.PANJANG;
				case TitikKode.WARNA_HIJAU -> LenteraKota.LABU;
				case TitikKode.WARNA_BIRU, TitikKode.WARNA_SEKTE -> LenteraKota.HEKSA;
				default -> l.hash() % 7 == 0 ? LenteraKota.PANJANG : LenteraKota.BULAT;
			};
			if (mode == KalenderKota.LENTERA_DUKA) {
				rgb = LenteraKota.PUTIH;
				jenis = LenteraKota.PANJANG;
			} else if ((bits & KalenderKota.BIT_PASAR_HANTU) != 0 && l.hash() % 3 == 0) {
				rgb = LenteraKota.HIJAU;
				jenis = LenteraKota.LABU;
			} else if (raya == MusimKota.HR_PERTENGAHAN_GUGUR && (l.hash() & 3) == 0) {
				jenis = LenteraKota.KELINCI;
			}
			double dx = l.x() - cam.x;
			double dy = l.y() + 1.0D - cam.y;
			double dz = l.z() - cam.z;
			float j2 = (float) (dx * dx + dy * dy + dz * dz);
			PILIH.add(new Pilihan(l, j2, nyala, rgb, jenis));
		}
		float t = Bingkai.waktuF;
		for (Pilihan p : PILIH) {
			TitikPeta.Lentera l = p.l();
			double lx = l.x() - cam.x;
			double ly = l.y() + 1.0D - cam.y;
			double lz = l.z() - cam.z;
			int light = Bingkai.cahaya(l.x(), l.y() + 0.5D, l.z());
			pose.pushPose();
			pose.translate(lx, ly, lz);
			LenteraKota.gambar(pose, collector, p.jenis(), p.rgb(), p.nyala(), light, t, l.fase(), p.jarak2() > 32.0F * 32.0F);
			pose.popPose();
		}
		if (Bingkai.minimal()) {
			return;
		}
		// the halos and the pools of light on the street, nearest first
		int halo = Bingkai.hitung(0, 64, 256);
		int tanah = Bingkai.hitung(0, 32, 128);
		Geo.Sink pancar = Geo.tipe(Geo.CAHAYA, Geo.Jenis.PANCAR);
		for (Pilihan p : PILIH) {
			if (p.nyala() < 0.04F) {
				continue;
			}
			TitikPeta.Lentera l = p.l();
			float flick = 0.88F + 0.08F * Mth.sin(t * 0.37F + l.fase()) + 0.04F * Mth.sin(t * 1.13F + l.fase() * 2.1F);
			if (halo > 0 && p.jarak2() < 48.0F * 48.0F) {
				halo--;
				float fade = p.jarak2() < 24.0F * 24.0F ? 1.0F : 1.0F - (Mth.sqrt(p.jarak2()) - 24.0F) / 24.0F;
				float cx = (float) (l.x() - cam.x);
				float cy = (float) (l.y() + 0.45D - cam.y);
				float cz = (float) (l.z() - cam.z);
				float h = (0.55F + 0.45F * p.nyala()) * (0.9F + 0.1F * flick);
				billboard(pancar, cx, cy, cz, h, Geo.argb(0.5F * p.nyala() * fade * flick, haloWarna(p.rgb())));
			}
			if (tanah > 0 && p.jarak2() < 32.0F * 32.0F) {
				Float gy = tanahY(l);
				if (gy != null && l.y() - gy < 10.0F) {
					tanah--;
					float fade = p.jarak2() < 16.0F * 16.0F ? 1.0F : 1.0F - (Mth.sqrt(p.jarak2()) - 16.0F) / 16.0F;
					float cx = (float) (l.x() - cam.x);
					float cy = (float) (gy + 0.03D - cam.y);
					float cz = (float) (l.z() - cam.z);
					float r = (1.8F + 0.9F * Mth.clamp((l.y() - gy) / 5.0F, 0.0F, 1.0F)) * (0.95F + 0.05F * flick);
					pancar.quad(cx - r, cy, cz - r, cx - r, cy, cz + r, cx + r, cy, cz + r, cx + r, cy, cz - r, 0.5F, 0.0F, 1.0F, 0.5F,
							Geo.argb(0.34F * p.nyala() * fade * flick, haloWarna(p.rgb())), Geo.TERANG, false);
				}
			}
		}
	}

	/** The glow of a lantern is its paper colour pushed toward warm white. */
	private static int haloWarna(int rgb) {
		int r = (rgb >> 16 & 255);
		int g = (rgb >> 8 & 255);
		int b = (rgb & 255);
		return (Math.min(255, r + (255 - r) / 4) << 16) | (Math.min(255, g + (255 - g) / 3) << 8) | Math.min(255, b + (255 - b) / 3);
	}

	/** Billboard quad of half-size h at the (camera-relative) point showing the halo cell (top-left quarter) of the light atlas. */
	private static void billboard(Geo.Sink s, float cx, float cy, float cz, float h, int argb) {
		var r = Bingkai.KANAN;
		var u = Bingkai.ATAS;
		float rx = r.x * h;
		float ry = r.y * h;
		float rz = r.z * h;
		float ux = u.x * h;
		float uy = u.y * h;
		float uz = u.z * h;
		s.quad(cx - rx + ux, cy - ry + uy, cz - rz + uz, cx - rx - ux, cy - ry - uy, cz - rz - uz, cx + rx - ux, cy + ry - uy, cz + rz - uz,
				cx + rx + ux, cy + ry + uy, cz + rz + uz, 0.0F, 0.0F, 0.5F, 0.5F, argb, Geo.TERANG, false);
	}

	/** The street level below a lantern (cached; null when the chunk below is not loaded or there is nothing within 14 blocks). */
	private static Float tanahY(TitikPeta.Lentera l) {
		var level = Bingkai.level;
		if (level == null) {
			return null;
		}
		long key = BlockPos.asLong(Math.round(l.x() - 0.5F), (int) l.y(), Math.round(l.z() - 0.5F));
		Float ada = TANAH.get(key);
		if (ada != null) {
			return ada.isNaN() ? null : ada;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy < 14; dy++) {
			pos.set(Math.round(l.x() - 0.5F), (int) l.y() - dy, Math.round(l.z() - 0.5F));
			if (!level.hasChunkAt(pos)) {
				return null;
			}
			BlockState st = level.getBlockState(pos);
			if (!st.getCollisionShape(level, pos).isEmpty()) {
				float gy = pos.getY() + 1.0F;
				TANAH.put(key, gy);
				return gy;
			}
		}
		TANAH.put(key, Float.NaN);
		return null;
	}

	private LenteraFx() {
	}
}
