package dev.kultivator.dunia.tempat.kotabesar;

import net.minecraft.network.chat.Component;

/**
 * Names of a capital (DESIGN 38.B.1): "Kota &lt;A&gt; &lt;B&gt;" from ten emblems and eight epithets
 * ({@code kultivator.kota_besar.nama.a.<i>} / {@code .b.<j>}), its ruler the Prince Warden of the City (the city belongs to
 * the Paviliun Pengawal Kota, the neutral league of the sects, never to an emperor) and its founder on the square's
 * statue. All deterministic from the world seed and the capital's plan index, so every server tells the same story.
 */
public final class KotaBesarNames {

	public static final int A = 10;
	public static final int B = 8;

	/** Surnames of the Warden houses (proper names: the same in every language). */
	private static final String[] MARGA = {"Long", "Bai", "Lan", "Jin", "Yun", "Shen", "Qin", "Han", "Lu", "Xiao", "Mu", "Ye"};
	/** Given names of wardens and founders. */
	private static final String[] NAMA = {"Tianyu", "Wuxin", "Changfeng", "Qingyun", "Xuanhe", "Mingyuan", "Jinghong", "Haoran",
			"Shaoyang", "Yichen", "Zhiyuan", "Boyan", "Lingfeng", "Chengzhi"};

	private static int pick(long seed, int index, int salt, int n) {
		long h = seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) Math.floorMod(h, (long) n);
	}

	/** The capital's name ("Kota Naga Langit Agung"). */
	public static Component nama(long seed, int index) {
		int a = pick(seed, index, 1, A);
		int b = pick(seed, index, 2, B);
		return Component.translatable("kultivator.kota_besar.nama", Component.translatable("kultivator.kota_besar.nama.a." + a),
				Component.translatable("kultivator.kota_besar.nama.b." + b));
	}

	/** The reigning Prince Warden's personal name ("Long Tianyu"). */
	public static String namaPangeran(long seed, int index) {
		return MARGA[pick(seed, index, 3, MARGA.length)] + " " + NAMA[pick(seed, index, 4, NAMA.length)];
	}

	/** "Pangeran Penjaga Kota Long Tianyu". */
	public static Component pangeran(long seed, int index) {
		return Component.translatable("kultivator.kota_besar.pangeran", namaPangeran(seed, index));
	}

	/** The founder carved on the Tugu Pendiri (another house, centuries ago). */
	public static Component pendiri(long seed, int index) {
		String nama = MARGA[pick(seed, index, 5, MARGA.length)] + " " + NAMA[pick(seed, index, 6, NAMA.length)];
		return Component.translatable("kultivator.kota_besar.pendiri", nama);
	}

	/** The magistrate of the Yamen ("Hakim Qin Haoran"). */
	public static Component hakim(long seed, int index) {
		String nama = MARGA[pick(seed, index, 7, MARGA.length)] + " " + NAMA[pick(seed, index, 8, NAMA.length)];
		return Component.translatable("kultivator.kota_besar.hakim", nama);
	}

	private KotaBesarNames() {
	}
}
