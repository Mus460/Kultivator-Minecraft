package dev.kultivator.dunia.kota42.cakap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Anti-repetition memory (DESIGN 42.5A.8): every listener keeps a ring of the frame keys it heard recently, with who said
 * them and when (per player 256, per warga 64). A frame's weight is multiplied by {@code 0.1} when the same speaker said it
 * to the same listener within one world day and by {@code 0.5} when the listener heard it from anyone within one world
 * hour (both: 0.05).
 * <p>
 * The rings here live for the server session; 42K persists them in {@code kultivator:kota_cakap} through
 * {@link Cincin#simpan}/{@link Cincin#muat} (two longs per entry). Server thread only.
 */
public final class AntiUlang {

	public static final int PEMAIN = 256;
	public static final int WARGA = 64;
	/** One world hour / day in ticks. */
	public static final long JAM = 1000L, HARI = 24000L;
	public static final double SAMA_PEMBICARA = 0.1D, SIAPA_PUN = 0.5D;

	/** A fixed-size ring of (key hash, speaker id, game time); the oldest entry is overwritten. */
	public static final class Cincin {
		private final int[] kunci;
		private final int[] pembicara;
		private final long[] waktu;
		private int kepala;
		private int isi;

		public Cincin(int ukuran) {
			kunci = new int[Math.max(1, ukuran)];
			pembicara = new int[kunci.length];
			waktu = new long[kunci.length];
		}

		public int ukuran() {
			return kunci.length;
		}

		public int isi() {
			return isi;
		}

		/** Records that {@code pembicaraId} said {@code kunciLang} to this listener at {@code now}. */
		public void catat(String kunciLang, int pembicaraId, long now) {
			kunci[kepala] = kunciLang.hashCode();
			pembicara[kepala] = pembicaraId;
			waktu[kepala] = now;
			kepala = (kepala + 1) % kunci.length;
			isi = Math.min(isi + 1, kunci.length);
		}

		/** The weight factor of a frame key said by {@code pembicaraId} now (1 = fresh). */
		public double faktor(String kunciLang, int pembicaraId, long now) {
			int h = kunciLang.hashCode();
			boolean sama = false;
			boolean baru = false;
			for (int i = 0; i < isi; i++) {
				if (kunci[i] != h) {
					continue;
				}
				long umur = now - waktu[i];
				if (umur < 0L) {
					continue;
				}
				sama |= pembicara[i] == pembicaraId && umur <= HARI;
				baru |= umur <= JAM;
			}
			return (sama ? SAMA_PEMBICARA : 1.0D) * (baru ? SIAPA_PUN : 1.0D);
		}

		/** Saved form, oldest first: per entry {@code (hash << 32 | speaker)} and the game time. */
		public long[] simpan() {
			long[] out = new long[isi * 2];
			int start = isi < kunci.length ? 0 : kepala;
			for (int i = 0; i < isi; i++) {
				int j = (start + i) % kunci.length;
				out[i * 2] = (long) kunci[j] << 32 | (pembicara[j] & 0xFFFFFFFFL);
				out[i * 2 + 1] = waktu[j];
			}
			return out;
		}

		/** Rebuilds a ring from {@link #simpan}; extra old entries beyond the size are dropped. */
		public static Cincin muat(long[] data, int ukuran) {
			Cincin c = new Cincin(ukuran);
			for (int i = 0; i + 1 < data.length; i += 2) {
				int j = c.kepala;
				c.kunci[j] = (int) (data[i] >> 32);
				c.pembicara[j] = (int) data[i];
				c.waktu[j] = data[i + 1];
				c.kepala = (j + 1) % c.kunci.length;
				c.isi = Math.min(c.isi + 1, c.kunci.length);
			}
			return c;
		}
	}

	private static final Map<UUID, Cincin> PEMAIN_MAP = new LinkedHashMap<>();
	private static final Map<Integer, Cincin> WARGA_MAP = new LinkedHashMap<>();

	/** The ring of a listening player (created on first use). */
	public static Cincin pemain(UUID id) {
		return PEMAIN_MAP.computeIfAbsent(id, k -> new Cincin(PEMAIN));
	}

	/** The ring of a listening warga (NPC-NPC talk). */
	public static Cincin warga(int id) {
		return WARGA_MAP.computeIfAbsent(id, k -> new Cincin(WARGA));
	}

	/** Puts a loaded ring in place (42K load). */
	public static void pasangPemain(UUID id, Cincin c) {
		PEMAIN_MAP.put(id, c);
	}

	public static void pasangWarga(int id, Cincin c) {
		WARGA_MAP.put(id, c);
	}

	/** Forgets everything (server stop). */
	public static void lupakan() {
		PEMAIN_MAP.clear();
		WARGA_MAP.clear();
	}

	private AntiUlang() {
	}
}
