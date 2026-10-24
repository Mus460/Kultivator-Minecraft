package dev.kultivator.dunia.kota;

import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokGen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Seeded names (DESIGN 34.5.1): a town's name from its site id ({@code kultivator.kota.nama.<0-23>} +
 * {@code kultivator.kota.akhiran.<0-7>}: "Kota Batu Giok", "Kota Awan Hijau"), and a person's name from a look seed:
 * surname {@code kultivator.kota.marga.<0-23>} + a given name of the person's sex (DESIGN 40.2.5):
 * {@code kultivator.kota.nama_diri_l.<0-23>} (Feng, Hao, Jian ...) or {@code kultivator.kota.nama_diri_p.<0-23>} (Xue,
 * Lan, Mei ...). The old sexless keys {@code kultivator.kota.nama_diri.*} stay in the language files but are no longer
 * used.
 */
public final class KotaNames {

	public static final int NAMES = 24;
	public static final int SUFFIXES = 8;
	public static final int SURNAMES = 24;
	/** Given names in each sex's pool. */
	public static final int GIVEN = 24;

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

	/**
	 * "Lin Feng" for a look seed whose role is not known: the sex of a half-and-half role ({@code SosokGen} rule:
	 * look bits {@code (look >>> 24) & 0x7F < 64} is a woman). Callers that know the role use {@link #person(int, NpcRole)}.
	 */
	public static MutableComponent person(int look) {
		return person(look, (look >>> 24 & 0x7F) < 64);
	}

	/** "Lin Feng" / "Lin Xue": the name of a person of {@code role} with this look (sex from {@link SosokGen#kelamin}). */
	public static MutableComponent person(int look, NpcRole role) {
		return person(look, SosokGen.kelamin(role, look) == Sosok.PEREMPUAN);
	}

	/** "Lin Feng" (a man) or "Lin Xue" (a woman): surname and given name from the look seed. */
	public static MutableComponent person(int look, boolean perempuan) {
		int seed = Math.abs(look * 0x9E3779B1);
		String pool = perempuan ? "kultivator.kota.nama_diri_p." : "kultivator.kota.nama_diri_l.";
		return Component.translatable("kultivator.kota.nama_orang", Component.translatable("kultivator.kota.marga." + Math.floorMod(seed, SURNAMES)),
				Component.translatable(pool + Math.floorMod(seed / SURNAMES, GIVEN)));
	}

	/** "Lin Feng · Juru Lelang" (the name of the role's sex for this look). */
	public static MutableComponent plate(int look, NpcRole role) {
		return Component.translatable("kultivator.kota.papan_nama", person(look, role), Component.translatable(role.titleKey()));
	}

	static int seed(long id) {
		long z = id * 0x9E3779B97F4A7C15L;
		z ^= z >>> 31;
		return (int) (z & 0x7FFFFFFF);
	}

	private KotaNames() {
	}
}
