package dev.kultivator.dunia.tempat;

import net.minecraft.network.chat.Component;

/**
 * Stable names of the small places of DESIGN 38 that have no owner module of their own (owner 38W): road-side posts
 * (rest posts, great rest posts, caravanserais, road shrines, ferries, milestones) and roads. A name is a hash of the
 * site id into two word lists ({@code kultivator.tempat.nama.a.<0-15>} "Tiga Pinus", "Batu Hijau", ... and
 * {@code kultivator.tempat.nama.b.<0-7>} "Utara", "Lama", ...) put into the kind's pattern
 * ({@code kultivator.tempat.nama.pos.<jenis>}: "Pos Singgah %s", "Karavanserai %s %s", ...). The capital's names are
 * 38KS's ({@code KotaBesarNames}); villages and compounds have theirs (38DS).
 */
public final class TempatNames {

	public static final int KATA_A = 16;
	public static final int KATA_B = 8;

	/** The name of a road-side post of kind {@code jenis} ({@code singgah}, {@code dagang}, ...) with site id {@code situsId}. */
	public static Component pos(String jenis, long situsId) {
		long h = mix(situsId ^ jenis.hashCode());
		Component a = Component.translatable("kultivator.tempat.nama.a." + Math.floorMod(h, KATA_A));
		Component b = Component.translatable("kultivator.tempat.nama.b." + Math.floorMod(h >>> 20, KATA_B));
		return Component.translatable("kultivator.tempat.nama.pos." + jenis, a, b);
	}

	/** The name of a road (edge id): "Jalan <A>" for village roads, "Jalan Kekaisaran <A>" for imperial ones. */
	public static Component jalan(long sisiId, int kelas) {
		long h = mix(sisiId);
		Component a = Component.translatable("kultivator.tempat.nama.a." + Math.floorMod(h, KATA_A));
		return Component.translatable("kultivator.tempat.nama.jalan." + Math.clamp(kelas, 0, 2), a);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private TempatNames() {
	}
}
