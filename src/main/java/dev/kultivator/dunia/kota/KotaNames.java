package dev.kultivator.dunia.kota;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Seeded names (DESIGN 34.5.1): a town's name from its site id ({@code kultivator.kota.nama.<0-23>} +
 * {@code kultivator.kota.akhiran.<0-7>}: "Kota Batu Giok", "Kota Awan Hijau"), and a person's name from a look seed
 * (surname {@code kultivator.kota.marga.<0-23>} + given name {@code kultivator.kota.nama_diri.<0-31>}).
 */
public final class KotaNames {

	public static final int NAMES = 24;
	public static final int SUFFIXES = 8;
	public static final int SURNAMES = 24;
	public static final int GIVEN = 32;

	/** "Kota Batu Giok". */
	public static MutableComponent town(long townId) {
		int seed = seed(townId);
		return Component.translatable("kultivator.kota.nama_kota", Component.translatable("kultivator.kota.nama." + Math.floorMod(seed, NAMES)),
				Component.translatable("kultivator.kota.akhiran." + Math.floorMod(seed / NAMES, SUFFIXES)));
	}

	/** The Immortal City district's name ("Kota Abadi Awan Emas"). */
	public static MutableComponent immortalCity(long townId) {
		int seed = seed(townId);
		return Component.translatable("kultivator.kota.nama_kota_abadi", Component.translatable("kultivator.kota.nama." + Math.floorMod(seed, NAMES)),
				Component.translatable("kultivator.kota.akhiran." + Math.floorMod(seed / NAMES, SUFFIXES)));
	}

	/** "Lin Feng". */
	public static MutableComponent person(int look) {
		int seed = Math.abs(look * 0x9E3779B1);
		return Component.translatable("kultivator.kota.nama_orang", Component.translatable("kultivator.kota.marga." + Math.floorMod(seed, SURNAMES)),
				Component.translatable("kultivator.kota.nama_diri." + Math.floorMod(seed / SURNAMES, GIVEN)));
	}

	/** "Lin Feng · Juru Lelang". */
	public static MutableComponent plate(int look, NpcRole role) {
		return Component.translatable("kultivator.kota.papan_nama", person(look), Component.translatable(role.titleKey()));
	}

	static int seed(long id) {
		long z = id * 0x9E3779B97F4A7C15L;
		z ^= z >>> 31;
		return (int) (z & 0x7FFFFFFF);
	}

	private KotaNames() {
	}
}
