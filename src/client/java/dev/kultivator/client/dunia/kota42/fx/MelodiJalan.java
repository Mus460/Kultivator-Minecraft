package dev.kultivator.client.dunia.kota42.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The street music of the towns (DESIGN 42.12.6 "Melodi prosedural"): tunes from {@code data/kultivator/kota/melodi/*.json}
 * (pentatonic gong-shang-jue-zhi-yu on D, written by {@code tools/gen_kota_hidup_suara.py}) played note by note on the instrument
 * samples of {@link SuaraKota42}. A tune has tempo, and voices ("lapis"): a melodic voice is a list of [degree 0-11, beats] on one
 * of erhu, suona, pipa, dizi (degree -1 a rest); a rhythm voice is a pattern of {@code x} (hit), {@code X} (strong hit) and
 * {@code .} on drums, gong, wood block or the night watchman's slit drum, repeated over the whole tune. Emitters ({@link #mulai})
 * are started by the fx that know when and where music is heard (a busker's bow, a wedding or funeral procession, the night
 * district, the lion dance, the night watch): each plays its tune at a moving point, stops when the point is gone or far, and may
 * repeat after a pause. Notes are scheduled on the render clock so a 16th note is not rounded to a tick. At most four emitters
 * play at once. The volume follows the "Musik jalanan" setting. Client thread only.
 */
final class MelodiJalan {

	/** One voice of a tune. */
	record Lapis(boolean melodi, String instrumen, float oktaf, float volume, float[] mulai, int[] derajat, float unit, String pola, int ukuran) {
	}

	/** A tune: tempo in beats a minute, voices, total length in beats. */
	record Lagu(String id, int tempo, List<Lapis> lapis, float total) {
	}

	/** One running emitter. */
	private static final class Pemain {
		final String kunci;
		final Lagu lagu;
		final Supplier<@Nullable Vec3> posisi;
		final float volume;
		final boolean ulang;
		final int jedaMin;
		final int jedaMaks;
		double mulai;
		int[] indeks;
		double tunggu = -1.0D;

		Pemain(String kunci, Lagu lagu, Supplier<@Nullable Vec3> posisi, float volume, boolean ulang, int jedaMin, int jedaMaks) {
			this.kunci = kunci;
			this.lagu = lagu;
			this.posisi = posisi;
			this.volume = volume;
			this.ulang = ulang;
			this.jedaMin = jedaMin;
			this.jedaMaks = jedaMaks;
			this.indeks = new int[lagu.lapis().size()];
		}
	}

	private static final String[] DAFTAR = {"jembatan", "bulan_sabit", "perahu_pulang", "arak_pengantin", "ratap_pagi", "pesta_lentera", "malam_teh", "dizi_sungai",
			"gembira_pasar", "barongsai", "ronda"};
	private static final int MAKS = 4;
	private static final Map<String, Lagu> LAGU = new HashMap<>();
	private static final Map<String, Pemain> PEMAIN = new HashMap<>();
	private static boolean dimuat;

	/** Reads the tunes from the mod jar (once, lazily). */
	static void muat() {
		if (dimuat) {
			return;
		}
		dimuat = true;
		List<String> ids = new ArrayList<>();
		try {
			var kontainer = FabricLoader.getInstance().getModContainer(Kultivator.MOD_ID);
			if (kontainer.isPresent()) {
				var dir = kontainer.get().findPath("data/kultivator/kota/melodi");
				if (dir.isPresent()) {
					try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir.get(), "*.json")) {
						for (Path p : ds) {
							String n = p.getFileName().toString();
							ids.add(n.substring(0, n.length() - 5));
						}
					}
				}
			}
		} catch (IOException | RuntimeException | LinkageError exception) {
			Kultivator.LOGGER.warn("Kultivator: street tunes not listed, using the built-in names", exception);
		}
		if (ids.isEmpty()) {
			ids.addAll(List.of(DAFTAR));
		}
		for (String id : ids) {
			try (InputStream in = MelodiJalan.class.getResourceAsStream("/data/kultivator/kota/melodi/" + id + ".json")) {
				if (in == null) {
					continue;
				}
				try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
					Lagu l = baca(id, JsonParser.parseReader(r).getAsJsonObject());
					if (l != null) {
						LAGU.put(id, l);
					}
				}
			} catch (IOException | RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: street tune {} is unreadable", id, exception);
			}
		}
	}

	private static @Nullable Lagu baca(String id, JsonObject o) {
		int tempo = Mth.clamp(o.get("tempo").getAsInt(), 30, 220);
		List<Lapis> lapis = new ArrayList<>();
		float total = 0.0F;
		for (JsonElement e : o.getAsJsonArray("lapis")) {
			JsonObject l = e.getAsJsonObject();
			boolean melodi = "melodi".equals(l.get("jenis").getAsString());
			String inst = l.get("instrumen").getAsString();
			float vol = l.has("vol") ? l.get("vol").getAsFloat() : 1.0F;
			float oktaf = l.has("oktaf") ? l.get("oktaf").getAsFloat() : 0.0F;
			if (melodi) {
				JsonArray c = l.getAsJsonArray("catatan");
				float[] mulai = new float[c.size()];
				int[] derajat = new int[c.size()];
				float t = 0.0F;
				for (int i = 0; i < c.size(); i++) {
					JsonArray n = c.get(i).getAsJsonArray();
					derajat[i] = n.get(0).getAsInt();
					mulai[i] = t;
					t += n.get(1).getAsFloat();
				}
				total = Math.max(total, t);
				lapis.add(new Lapis(true, inst, oktaf, vol, mulai, derajat, 0.0F, "", 0));
			} else {
				float unit = l.get("unit").getAsFloat();
				lapis.add(new Lapis(false, inst, oktaf, vol, new float[0], new int[0], unit, l.get("pola").getAsString(), l.has("ukuran") ? l.get("ukuran").getAsInt() : 0));
			}
		}
		if (lapis.isEmpty()) {
			return null;
		}
		if (total <= 0.0F) {
			// only rhythm voices: eight rounds of the longest pattern
			for (Lapis l : lapis) {
				total = Math.max(total, l.pola().length() * l.unit() * 8.0F);
			}
		}
		return new Lagu(id, tempo, List.copyOf(lapis), total);
	}

	static @Nullable Lagu lagu(String id) {
		muat();
		return LAGU.get(id);
	}

	/** True when a tune of this id is loaded. */
	static boolean ada(String id) {
		return lagu(id) != null;
	}

	/** The emitter {@code kunci} plays now (even while it waits to repeat). */
	static boolean main(String kunci) {
		return PEMAIN.containsKey(kunci);
	}

	/** Number of emitters running. */
	static int jumlah() {
		return PEMAIN.size();
	}

	/**
	 * Starts (or keeps) the emitter {@code kunci}; false when the tune is missing or four emitters already play. {@code posisi}
	 * returns the point the music comes from (null stops it); {@code volume} scales every note; when {@code ulang} the tune plays
	 * again after a pause of {@code jedaMin..jedaMaks} ticks.
	 */
	static boolean mulai(String kunci, String idLagu, Supplier<@Nullable Vec3> posisi, float volume, boolean ulang, int jedaMin, int jedaMaks) {
		if (PEMAIN.containsKey(kunci)) {
			return true;
		}
		Lagu l = lagu(idLagu);
		if (l == null || PEMAIN.size() >= MAKS) {
			return false;
		}
		Pemain p = new Pemain(kunci, l, posisi, volume, ulang, jedaMin, jedaMaks);
		p.mulai = Bingkai.waktuF;
		PEMAIN.put(kunci, p);
		return true;
	}

	static void henti(String kunci) {
		PEMAIN.remove(kunci);
	}

	static void bersihkan() {
		PEMAIN.clear();
	}

	/** One client tick: every emitter plays the notes that fall due. */
	static void tik() {
		ClientLevel level = Bingkai.level;
		var pemain = Bingkai.pemain;
		if (level == null || pemain == null || PEMAIN.isEmpty()) {
			return;
		}
		if (SuaraKota42.musik() <= 0.0F) {
			PEMAIN.clear();
			return;
		}
		float sekarang = Bingkai.waktu;
		for (Iterator<Pemain> it = PEMAIN.values().iterator(); it.hasNext(); ) {
			Pemain p = it.next();
			Vec3 at = p.posisi.get();
			if (at == null || at.distanceToSqr(pemain.position()) > 56.0D * 56.0D) {
				it.remove();
				continue;
			}
			if (p.tunggu >= 0.0D) {
				if (sekarang < p.tunggu) {
					continue;
				}
				p.tunggu = -1.0D;
				p.mulai = sekarang;
				java.util.Arrays.fill(p.indeks, 0);
			}
			double beat = (sekarang - p.mulai) / 20.0D * p.lagu.tempo() / 60.0D;
			boolean selesai = beat >= p.lagu.total();
			for (int i = 0; i < p.lagu.lapis().size(); i++) {
				Lapis l = p.lagu.lapis().get(i);
				if (l.melodi()) {
					while (p.indeks[i] < l.mulai().length && l.mulai()[p.indeks[i]] <= beat) {
						int d = l.derajat()[p.indeks[i]++];
						if (d >= 0 && beat - l.mulai()[p.indeks[i] - 1] < 1.5D) {
							nada(level, at, l, d, p.volume);
						}
					}
				} else {
					double langkah = beat / l.unit();
					int n = (int) Math.floor(langkah);
					int panjang = l.pola().length();
					while (p.indeks[i] <= n && p.indeks[i] * l.unit() < p.lagu.total()) {
						int k = p.indeks[i]++;
						char c = l.pola().charAt(k % panjang);
						if ((c == 'x' || c == 'X') && n - k < 3) {
							ketuk(level, at, l, c == 'X', k, p.volume);
						}
					}
				}
			}
			if (selesai) {
				if (p.ulang) {
					p.tunggu = sekarang + p.jedaMin + (p.jedaMaks > p.jedaMin ? level.getRandom().nextInt(p.jedaMaks - p.jedaMin) : 0);
				} else {
					it.remove();
				}
			}
		}
	}

	private static void nada(ClientLevel level, Vec3 at, Lapis l, int derajat, float volume) {
		float pitch = (float) Math.pow(2.0D, l.oktaf());
		SuaraKota42.musik(level, at.x, at.y, at.z, SuaraKota42.nada(l.instrumen(), derajat), 1.15F * l.volume() * volume, pitch);
	}

	private static void ketuk(ClientLevel level, Vec3 at, Lapis l, boolean kuat, int k, float volume) {
		float v = (kuat ? 1.2F : 0.85F) * l.volume() * volume;
		var e = switch (l.instrumen()) {
			case "gong" -> SuaraKota42.acara("kota42.gong." + Mth.clamp(l.ukuran(), 0, 2));
			case "kentongan" -> SuaraKota42.acara("kota42.kentongan." + (kuat ? 2 : k % 2));
			case "genderang_kecil" -> SuaraKota42.acara("kota42.genderang_kecil");
			default -> SuaraKota42.acara("kota42.balok_kayu");
		};
		SuaraKota42.musik(level, at.x, at.y, at.z, e, v, 1.0F);
	}

	private MelodiJalan() {
	}
}
