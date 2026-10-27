package dev.kultivator.dunia.kota42.acara;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tunes of the town (DESIGN 42.7.1 "3 lagu erhu", 42.9.6 night music), played by the server with the vanilla note-block
 * voices (flute for the erhu and dizi, guitar for the pipa, bit for the suona, bells and drums) until the client melody player
 * of 42V-F exists. Every tune is written as layers of pentatonic degrees ({@code 1 2 3 4 5}, {@code '} an octave up,
 * {@code ,} an octave down, {@code .} a rest, {@code -} the previous note held, {@code |} a bar line, {@code *} a drum or
 * clapper hit); one token is one beat. The scale is G A B D E (note-block notes 1, 3, 5, 8, 10).
 * <p>
 * {@link #mainkan} plays the notes whose start tick falls in a window, so a caller that ticks every few ticks plays a whole
 * tune without a queue; a tune is a pure function of its start tick.
 */
final class Lagu {

	/** Instruments of a layer. */
	static final int SERULING = 0;
	static final int PIPA = 1;
	static final int LONCENG = 2;
	static final int BEL = 3;
	static final int BAS = 4;
	static final int GENDANG = 5;
	static final int KENTONGAN = 6;
	static final int SUONA = 7;
	static final int XILO = 8;
	static final int CIMBAL = 9;

	private static final int[] PENTA = {1, 3, 5, 8, 10};

	/** One note: start tick, note-block note 0-24, instrument, volume 0-1. */
	record Nada(int tik, int nada, int alat, float suara) {
	}

	/** A tune: id, length in ticks and its notes sorted by start. */
	record Melodi(String id, int panjang, List<Nada> nada) {
	}

	private static final Map<String, Melodi> SEMUA = new LinkedHashMap<>();

	// ---- Ah Qin's three songs (42.7.1: "Lagu di Bawah Jembatan" is the title of his quest)
	static final Melodi JEMBATAN = tulis("jembatan", 12,
			lapis(SERULING, 0.9F, "5 - 3 2 | 3 - - . | 5 - 6' 5 | 3 - - . | 2 - 3 5 | 3 2 1 2 | 3 - 2 1 | 1, - - . | "
					+ "5 - 3 2 | 3 - - . | 5 - 4 3 | 2 - - . | 1 2 3 5 | 4 3 2 1 | 2 - 3 2 | 1 - - . |"),
			lapis(PIPA, 0.5F, ". . 1 . | . . 3 . | . . 1 . | . . 5 . | . . 3 . | . . 5 . | . . 2 . | . . 1 . | "
					+ ". . 1 . | . . 3 . | . . 2 . | . . 4 . | . . 1 . | . . 3 . | . . 2 . | . . 1 . |"));
	static final Melodi BULAN_SUNGAI = tulis("bulan_sungai", 12,
			lapis(SERULING, 0.9F, "3 - 5 - | 4 - 3 - | 2 - 3 5 | 2 - - . | 1 - 2 - | 3 - 5 3 | 2 - 1 - | 5, - - . | "
					+ "3 - 5 - | 4 3 2 - | 5 - 4 3 | 2 - - . | 3 2 1 2 | 3 - 5 - | 4 3 2 1 | 1 - - - |"),
			lapis(BEL, 0.4F, "1 . . . | 5, . . . | 3 . . . | 2 . . . | 1 . . . | 3 . . . | 5, . . . | 1 . . . | "
					+ "1 . . . | 4 . . . | 5, . . . | 2 . . . | 1 . . . | 3 . . . | 4 . . . | 1 . . . |"));
	static final Melodi PULANG_GUGUR = tulis("pulang_gugur", 10,
			lapis(SERULING, 0.9F, "1 2 3 - | 5 - 3 - | 2 - 1 - | 3 - - . | 5 4 3 - | 2 3 5 - | 3 2 1 - | 5, - - . | "
					+ "1 2 3 5 | 4 - 3 - | 2 3 2 1 | 3 - - . | 5' - 3 5 | 2 - 3 - | 2 1 2 3 | 1 - - - |"),
			lapis(PIPA, 0.5F, "1 . 5, . | 1 . 5, . | 5, . 2 . | 3 . 1 . | 3 . 5 . | 2 . 5, . | 3 . 1 . | 5, . 1 . | "
					+ "1 . 5, . | 4 . 3 . | 2 . 5, . | 3 . 1 . | 5 . 3 . | 2 . 3 . | 2 . 1 . | 1 . . . |"),
			lapis(KENTONGAN, 0.3F, "* . . . | * . . . | * . . . | * . . . | * . . . | * . . . | * . . . | * . . . | "
					+ "* . . . | * . . . | * . . . | * . . . | * . . . | * . . . | * . . . | * . . . |"));

	// ---- the Crescent Moon troupe (acrobats and lion dance): bright and quick
	static final Melodi PENTAS = tulis("pentas", 5,
			lapis(SERULING, 0.8F, "5 5 3 5 | 3 5 6' 5 | 3 2 3 5 | 2 - - . | 5 5 3 5 | 3 5 6' 5 | 3 2 1 2 | 1 - - . | "
					+ "3 3 5 3 | 2 3 5 - | 3 5 3 2 | 1 2 3 - | 5 3 5 6' | 5 3 2 3 | 5 3 2 1 | 1 - - - |"),
			lapis(XILO, 0.55F, "1 . 1 . | 3 . 3 . | 5 . 5 . | 2 . 2 . | 1 . 1 . | 3 . 3 . | 4 . 4 . | 1 . 1 . | "
					+ "3 . 3 . | 2 . 2 . | 5, . 5, . | 1 . 1 . | 3 . 3 . | 5 . 5 . | 4 . 4 . | 1 . 1 . |"),
			lapis(GENDANG, 0.7F, "* . * . | * . * . | * . * . | * * * . | * . * . | * . * . | * . * . | * * * . | "
					+ "* . * . | * . * . | * . * . | * * * . | * . * . | * . * . | * . * . | * * * * |"),
			lapis(CIMBAL, 0.35F, ". * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * | "
					+ ". * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * |"));
	/** Drums for the lion's step (a heavy beat and a quick roll). */
	static final Melodi SINGA = tulis("singa", 5,
			lapis(GENDANG, 0.9F, "* . * * | * . * * | * . * * | * * * . | * . * * | * . * * | * * * * | * . . . |"),
			lapis(CIMBAL, 0.5F, ". * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * | * . . . |"),
			lapis(SUONA, 0.45F, "5 . 3 . | 5 . 3 . | 5 3 2 3 | 5 - - . | 5 . 3 . | 5 . 3 . | 2 3 5 3 | 1 - - . |"));

	// ---- suona: a loud, reedy wedding and market air
	static final Melodi SUONA_MELODI = tulis("suona", 7,
			lapis(SUONA, 0.8F, "3 3 5 - | 3 2 1 - | 3 3 5 6' | 5 - - . | 5 3 2 3 | 5 - 3 - | 2 3 5 3 | 2 1 1 - | "
					+ "3 3 5 - | 3 2 1 - | 5 5 6' 5 | 3 - - . | 2 3 5 3 | 2 1 2 3 | 5 3 2 1 | 1 - - - |"),
			lapis(GENDANG, 0.6F, "* . . * | * . . * | * . . * | * . * . | * . . * | * . . * | * . . * | * * * . | "
					+ "* . . * | * . . * | * . . * | * . * . | * . . * | * . . * | * . . * | * * * * |"),
			lapis(CIMBAL, 0.3F, ". * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * | "
					+ ". * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * | . * . * |"));
	/** The duet of grief: the same suona, slow, with a single drum. */
	static final Melodi SUONA_DUKA = tulis("suona_duka", 14,
			lapis(SUONA, 0.7F, "5 - - - | 3 - 2 - | 1 - - - | 2 - 3 - | 5 - - - | 3 - 2 - | 1 - 2 1 | 5, - - - |"),
			lapis(GENDANG, 0.5F, "* . . . | . . . . | * . . . | . . . . | * . . . | . . . . | * . . . | * . . . |"));

	// ---- night: the pipa and the erhu of the lantern streets
	static final Melodi MALAM = tulis("malam", 9,
			lapis(PIPA, 0.7F, "1 3 5 3 | 2 3 2 1 | 5, 1 2 3 | 5 3 2 - | 1 3 5 3 | 4 3 2 1 | 5, 1 2 1 | 1 - - . | "
					+ "3 5 3 2 | 3 5 3 - | 5 4 3 2 | 3 - 1 - | 1 3 5 3 | 2 3 2 1 | 5, 1 2 1 | 1 - - - |"),
			lapis(SERULING, 0.45F, "5 - - - | 3 - - - | 2 - - - | 1 - - - | 5 - - - | 4 - - - | 3 - 2 - | 1 - - - | "
					+ "5 - - - | 5 - - - | 4 - - - | 3 - - - | 5 - - - | 3 - - - | 2 - 1 - | 1 - - - |"));
	/** The theatre: slow gong-and-drum air with the erhu on top. */
	static final Melodi WAYANG = tulis("wayang", 10,
			lapis(SERULING, 0.8F, "5 - 3 - | 2 - 3 - | 5 - 6' 5 | 3 - - . | 2 - 3 - | 5 3 2 - | 1 2 3 5 | 3 - - . |"),
			lapis(GENDANG, 0.6F, "* . . . | * . . . | * . . . | * . * . | * . . . | * . . . | * . . * | * . . . |"),
			lapis(BEL, 0.5F, "1 . . . | . . . . | 5, . . . | . . . . | 1 . . . | . . . . | 5, . . . | 1 . . . |"));

	private record Lapis(int alat, float suara, String isi) {
	}

	private static Lapis lapis(int alat, float suara, String isi) {
		return new Lapis(alat, suara, isi);
	}

	private static Melodi tulis(String id, int tpb, Lapis... lapis) {
		List<Nada> out = new ArrayList<>();
		int panjang = 0;
		for (Lapis l : lapis) {
			int tik = 0;
			for (String tok : l.isi().split("\\s+")) {
				if (tok.isEmpty() || tok.equals("|")) {
					continue;
				}
				if (tok.equals("*")) {
					out.add(new Nada(tik * tpb, 12, l.alat(), l.suara()));
				} else if (!tok.equals(".") && !tok.equals("-")) {
					int degree = tok.charAt(0) - '1';
					if (degree >= 0 && degree < PENTA.length) {
						int note = PENTA[degree];
						if (tok.endsWith("'")) {
							note += 12;
						} else if (tok.endsWith(",")) {
							note -= 12;
						}
						out.add(new Nada(tik * tpb, Math.max(0, Math.min(24, note)), l.alat(), l.suara()));
					}
				}
				tik++;
			}
			panjang = Math.max(panjang, tik * tpb);
		}
		out.sort((a, b) -> Integer.compare(a.tik(), b.tik()));
		Melodi m = new Melodi(id, Math.max(1, panjang), List.copyOf(out));
		SEMUA.put(id, m);
		return m;
	}

	/** A tune by id, or null. */
	static Melodi cari(String id) {
		return SEMUA.get(id);
	}

	/** The three songs of Ah Qin in the order he plays them. */
	static final Melodi[] LAGU_AH_QIN = {JEMBATAN, BULAN_SUNGAI, PULANG_GUGUR};

	private static SoundEvent suara(int alat) {
		return switch (alat) {
			case SERULING -> SoundEvents.NOTE_BLOCK_FLUTE.value();
			case PIPA -> SoundEvents.NOTE_BLOCK_GUITAR.value();
			case LONCENG -> SoundEvents.NOTE_BLOCK_CHIME.value();
			case BEL -> SoundEvents.NOTE_BLOCK_BELL.value();
			case BAS -> SoundEvents.NOTE_BLOCK_BASS.value();
			case GENDANG -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
			case KENTONGAN -> SoundEvents.NOTE_BLOCK_SNARE.value();
			case SUONA -> SoundEvents.NOTE_BLOCK_BIT.value();
			case XILO -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
			default -> SoundEvents.NOTE_BLOCK_HAT.value();
		};
	}

	/**
	 * Plays the notes of {@code m} that start in {@code [dari, sampai)} ticks of the tune (the tune loops: both are taken
	 * modulo its length), at {@code at}, loud enough to carry {@code jangkauan} blocks (1.0 volume = 16 blocks).
	 */
	static void mainkan(ServerLevel level, BlockPos at, Melodi m, long dari, long sampai, double jangkauan) {
		if (sampai <= dari) {
			return;
		}
		float vol = (float) Math.max(0.5D, jangkauan / 16.0D);
		long ulang = Math.floorDiv(dari, (long) m.panjang());
		long a = dari - ulang * m.panjang();
		long b = sampai - ulang * m.panjang();
		for (Nada n : m.nada()) {
			boolean pas = n.tik() >= a && n.tik() < b || b > m.panjang() && n.tik() + m.panjang() >= a && n.tik() + m.panjang() < b;
			if (pas) {
				float pitch = (float) Math.pow(2.0D, (n.nada() - 12) / 12.0D);
				level.playSound(null, at, suara(n.alat()), SoundSource.RECORDS, vol * n.suara(), pitch);
			}
		}
	}

	private Lagu() {
	}
}
