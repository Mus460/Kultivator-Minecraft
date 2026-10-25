package dev.kultivator.dunia.kota42.warga;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * Names of residents (DESIGN 42.4.2), read once from {@code data/kultivator/kota/nama.json} in the mod jar: 100 family names
 * of the <i>Bai Jia Xing</i> by frequency (index 0-99 = {@link Warga#marga}), 120 given-name syllables per sex and one
 * eight-syllable <i>zibei</i> generation poem per family. Names are pinyin and never lang keys (they read the same in every
 * language); the hanzi ride along for the Kartu Warga.
 * <p>
 * {@link Warga#nama} packs the given name: bits 0-6 the second syllable (sex list), bits 7-13 the first syllable when it
 * is not a generation syllable, {@link #ZIBEI} = the first syllable is {@code puisi[marga][generasi % 8]} (siblings share it),
 * {@link #TUNGGAL} = a one-syllable given name. A married woman keeps her own family name (formal address
 * {@link #sapaanFormal}: "Nyonya Zhang" after her husband).
 */
public final class NamaWarga {

	/** Syllables per sex list. */
	public static final int SUKU = 120;
	/** Family names. */
	public static final int MARGA = 100;
	/** {@link Warga#nama}: the first syllable is the family's generation syllable. */
	public static final int ZIBEI = 0x4000;
	/** {@link Warga#nama}: a one-syllable given name (only the second syllable). */
	public static final int TUNGGAL = 0x8000;

	private record Suku(String py, String hz) {
	}

	private static volatile Tabel tabel;

	private record Tabel(Suku[] marga, Suku[] pria, Suku[] wanita, Suku[][] puisi) {
	}

	private static Tabel tabel() {
		Tabel t = tabel;
		if (t == null) {
			synchronized (NamaWarga.class) {
				t = tabel;
				if (t == null) {
					t = muat();
					tabel = t;
				}
			}
		}
		return t;
	}

	private static Tabel muat() {
		try (InputStream in = NamaWarga.class.getResourceAsStream("/data/kultivator/kota/nama.json")) {
			if (in == null) {
				throw new IllegalStateException("data/kultivator/kota/nama.json missing");
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				Suku[] marga = suku(root.getAsJsonArray("marga"));
				Suku[] pria = suku(root.getAsJsonArray("suku_pria"));
				Suku[] wanita = suku(root.getAsJsonArray("suku_wanita"));
				JsonArray poems = root.getAsJsonArray("puisi");
				Suku[][] puisi = new Suku[poems.size()][];
				for (int i = 0; i < poems.size(); i++) {
					puisi[i] = suku(poems.get(i).getAsJsonArray());
				}
				if (marga.length < MARGA || pria.length < SUKU || wanita.length < SUKU || puisi.length < MARGA) {
					throw new IllegalStateException("nama.json too short");
				}
				return new Tabel(marga, pria, wanita, puisi);
			}
		} catch (Exception exception) {
			Kultivator.LOGGER.error("Resident names could not be read; using one stand-in name", exception);
			Suku[] one = {new Suku("Wang", "王")};
			Suku[] syll = {new Suku("An", "安")};
			Suku[][] poem = {{new Suku("Wen", "文")}};
			return new Tabel(one, syll, syll, poem);
		}
	}

	private static Suku[] suku(JsonArray array) {
		Suku[] out = new Suku[array.size()];
		for (int i = 0; i < out.length; i++) {
			JsonElement e = array.get(i);
			JsonObject o = e.getAsJsonObject();
			out[i] = new Suku(o.get("py").getAsString(), o.get("hz").getAsString());
		}
		return out;
	}

	// ================================================================== reading

	/** "Zhang" (pinyin family name of index {@code marga}). */
	public static String marga(int marga) {
		Suku[] list = tabel().marga();
		return list[Math.floorMod(marga, list.length)].py();
	}

	/** "Wenbao" / "Ming'an" (the given name). */
	public static String diri(Warga w) {
		Suku[] first = suku1(w);
		Suku second = suku2(w);
		if (first == null) {
			return second.py();
		}
		String a = first[0].py();
		String b = second.py().toLowerCase(Locale.ROOT);
		// pinyin writes an apostrophe before a syllable that starts with a, e or o (Ming'an, Xi'e)
		boolean apos = !b.isEmpty() && "aeo".indexOf(b.charAt(0)) >= 0;
		return a + (apos ? "'" : "") + b;
	}

	/** "Zhang Wenbao". */
	public static String lengkap(Warga w) {
		return marga(w.marga) + " " + diri(w);
	}

	/** "张文宝". */
	public static String hanzi(Warga w) {
		Suku[] list = tabel().marga();
		Suku[] first = suku1(w);
		return list[Math.floorMod(w.marga, list.length)].hz() + (first == null ? "" : first[0].hz()) + suku2(w).hz();
	}

	/** The full name as a chat component (literal: pinyin reads the same in every language). */
	public static Component komponen(Warga w) {
		return Component.literal(lengkap(w));
	}

	/**
	 * The formal address of a resident: "Tuan Zhang" for a man, "Nyonya Zhang" (her husband's family name) for a married
	 * woman, "Nona Liu" for an unmarried one ({@code kultivator.kota42.sapa.*}).
	 */
	public static Component sapaanFormal(Warga w, int margaSuami) {
		if (w.pria()) {
			return Component.translatable("kultivator.kota42.sapa.tuan", marga(w.marga));
		}
		return margaSuami >= 0 ? Component.translatable("kultivator.kota42.sapa.nyonya", marga(margaSuami))
				: Component.translatable("kultivator.kota42.sapa.nona", marga(w.marga));
	}

	/** The generation syllable of family {@code marga} at generation {@code generasi}. */
	public static String zibei(int marga, int generasi) {
		Suku[] poem = puisi(marga);
		return poem[Math.floorMod(generasi, poem.length)].py();
	}

	private static Suku[] puisi(int marga) {
		Suku[][] all = tabel().puisi();
		return all[Math.floorMod(marga, all.length)];
	}

	private static Suku[] daftar(boolean pria) {
		return pria ? tabel().pria() : tabel().wanita();
	}

	/** The first syllable (as a one-element array) or null for a one-syllable name. */
	private static Suku[] suku1(Warga w) {
		if ((w.nama & TUNGGAL) != 0) {
			return null;
		}
		if ((w.nama & ZIBEI) != 0) {
			Suku[] poem = puisi(w.marga);
			return new Suku[]{poem[Math.floorMod(w.generasi, poem.length)]};
		}
		Suku[] list = daftar(w.pria());
		return new Suku[]{list[Math.floorMod(w.nama >>> 7 & 0x7F, list.length)]};
	}

	private static Suku suku2(Warga w) {
		Suku[] list = daftar(w.pria());
		return list[Math.floorMod(w.nama & 0x7F, list.length)];
	}

	// ================================================================== naming

	/**
	 * A new given name for a person of this sex: with the family's generation syllable ({@code zibei}) or two free
	 * syllables, 8 % one syllable; never one {@code dipakai} rejects (siblings' second syllables), at most 24 tries.
	 */
	public static int acak(boolean pria, boolean zibei, RandomSource random, IntPredicate dipakai) {
		int value = 0;
		for (int i = 0; i < 24; i++) {
			int second = random.nextInt(SUKU);
			if (zibei) {
				value = ZIBEI | second;
			} else if (random.nextInt(100) < 8) {
				value = TUNGGAL | second;
			} else {
				int first = random.nextInt(SUKU);
				if (first == second) {
					continue;
				}
				value = first << 7 | second;
			}
			if (!dipakai.test(second)) {
				return value;
			}
		}
		return value;
	}

	/**
	 * A family name drawn by frequency (the first names of the Bai Jia Xing are far more common: weight {@code 1 / (i + 6)}).
	 */
	public static int margaAcak(RandomSource random) {
		double total = 0.0D;
		for (int i = 0; i < MARGA; i++) {
			total += 1.0D / (i + 6);
		}
		double pick = random.nextDouble() * total;
		for (int i = 0; i < MARGA; i++) {
			pick -= 1.0D / (i + 6);
			if (pick < 0.0D) {
				return i;
			}
		}
		return 0;
	}

	private NamaWarga() {
	}
}
