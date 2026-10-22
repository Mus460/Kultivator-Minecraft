package dev.kultivator.sekte.diplomasi;

import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * Two-part sect names (DESIGN 36.2.9): a pattern ("Sekte %s %s", "Lembah %s %s", ...) filled with a noun A ("Pedang",
 * "Racun") and a modifier B ("Awan Ungu", "Seribu Jiwa"), all lang keys so each language orders them its own way
 * ("Sekte Pedang Awan Ungu" / "Purple Cloud Sword Sect"). The word pools lean by alignment: the righteous take swords,
 * lotuses and clouds, the demonic blood, poison and bone. Sect masters get a pinyin surname and given name (the same in
 * every language).
 */
public final class GeneratorNama {

	public static final String KEY = "kultivator.sekte.diplomasi.";
	/** Name patterns ({@code nama.pola.<n>}). */
	public static final int POLA = 6;
	/** Nouns A ({@code nama.a.<n>}): 0-9 righteous, 10-14 neutral, 15-19 demonic. */
	public static final int KATA_A = 20;
	/** Modifiers B ({@code nama.b.<n>}): the same split. */
	public static final int KATA_B = 20;

	public static final String[] MARGA = {"Mo", "Han", "Lin", "Xiao", "Yun", "Bai", "Gu", "Qin", "Ye", "Su", "Lu", "Feng", "Zhao", "Ling",
			"Duan", "Tang"};
	public static final String[] NAMA = {"Tianhe", "Wuji", "Qingxuan", "Changkong", "Jueyun", "Mingyue", "Xuanji", "Haoran", "Zhenyuan",
			"Feixue", "Yanxu", "Shuangjian", "Lingfeng", "Zixiao", "Cangming", "Huaiyu"};

	static int kataA(int aliran, Random random) {
		return pick(aliran, random);
	}

	static int kataB(int aliran, Random random) {
		return pick(aliran, random);
	}

	private static int pick(int aliran, Random random) {
		int roll = random.nextInt(100);
		return switch (aliran) {
			case SekteNpc.LURUS -> roll < 80 ? random.nextInt(10) : 10 + random.nextInt(5);
			case SekteNpc.IBLIS -> roll < 80 ? 15 + random.nextInt(5) : 10 + random.nextInt(5);
			default -> roll < 50 ? 10 + random.nextInt(5) : random.nextInt(10);
		};
	}

	/** The sect's name. */
	public static Component sect(int pola, int a, int b) {
		return Component.translatable(KEY + "nama.pola." + Math.floorMod(pola, POLA),
				Component.translatable(KEY + "nama.a." + Math.floorMod(a, KATA_A)),
				Component.translatable(KEY + "nama.b." + Math.floorMod(b, KATA_B)));
	}

	/** "Ketua Mo Tianhe". */
	public static Component master(int marga, int nama) {
		return Component.translatable(KEY + "ketua.gelar", MARGA[Math.floorMod(marga, MARGA.length)] + " " + NAMA[Math.floorMod(nama, NAMA.length)]);
	}

	/** A member's plain name ("Murid Lin dari ..."): a surname and given name from the sect's seed and the slot. */
	public static String member(long seed, int slot) {
		long h = seed * 0x9E3779B97F4A7C15L + slot * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		return MARGA[(int) Math.floorMod(h, MARGA.length)] + " " + NAMA[(int) Math.floorMod(h >>> 11, NAMA.length)];
	}

	private GeneratorNama() {
	}
}
