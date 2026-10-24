package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleNames;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sosok.Sosok;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The named disciples of the other sects (DESIGN 39.F.1). One list per NPC sect of T12's single sect list
 * ({@code DiplomasiService.sects()}): {@code 6 + tier} members (at most 10), seeded from {@code (world seed, sect, slot)}
 * so the same world always has the same people. Slot 0 is the Eldest Senior (one per sect), slot 1 a Young Elder once
 * the sect reaches tier 6, then core, inner and outer disciples. Traits are weighted by the sect's path (demonic: cunning,
 * vengeful, proud; righteous: loyal, brave, humble), the Dao follows the sect's ink colour when one is close, the voice
 * and the body follow the name's sex. A sect that grows a tier gains members; members who defected or left are replaced
 * when it grows. Built lazily on first use, never on a tick.
 */
final class Roster {

	static final int MAKS = 10;

	/** The disciples of {@code sekte}, filled up to the sect's size first (empty when D does not know it). */
	static List<TokohSekte> anggota(MinecraftServer server, String sekte) {
		SeteruData data = SeteruData.get(server);
		List<TokohSekte> list = data.anggota(sekte);
		pastikan(server, data, sekte, list);
		return list;
	}

	/** Active members (not defected, not gone) of {@code sekte}. */
	static List<TokohSekte> aktif(MinecraftServer server, String sekte) {
		List<TokohSekte> out = new ArrayList<>();
		for (TokohSekte t : anggota(server, sekte)) {
			if (t.aktif()) {
				out.add(t);
			}
		}
		return out;
	}

	/** The sect's size: {@code 6 + tier}, at most {@link #MAKS}. */
	static int ukuran(int tier) {
		return Math.max(6, Math.min(MAKS, 6 + Math.max(1, tier)));
	}

	private static void pastikan(MinecraftServer server, SeteruData data, String sekte, List<TokohSekte> list) {
		DiplomasiService d = SekteApi.diplomasi();
		int tier = d.tier(sekte);
		if (tier <= 0) {
			return;
		}
		int want = ukuran(tier);
		int active = 0;
		for (TokohSekte t : list) {
			if (t.aktif()) {
				active++;
			}
		}
		if (active >= want) {
			return;
		}
		long today = Ingatan.today(server);
		long seed = server.overworld().getSeed();
		Set<Integer> taken = new HashSet<>();
		int nextSlot = 0;
		for (TokohSekte t : list) {
			taken.add(t.nama);
			nextSlot = Math.max(nextSlot, t.slot + 1);
		}
		boolean hasKakak = false;
		boolean hasTetua = false;
		for (TokohSekte t : list) {
			if (t.aktif() && t.peran == TokohSekte.PERAN_KAKAK) {
				hasKakak = true;
			}
			if (t.aktif() && t.peran == TokohSekte.PERAN_TETUA) {
				hasTetua = true;
			}
		}
		int ketua = DiplomasiMandiri.langkahKetua(server, sekte);
		int aliran = d.aliran(sekte);
		boolean awal = list.isEmpty();
		while (active < want) {
			int slot = nextSlot++;
			RandomSource rnd = RandomSource.create(seed ^ (long) sekte.hashCode() * 0x9E3779B97F4A7C15L ^ slot * 0xC2B2AE3D27D4EB4FL);
			TokohSekte t = new TokohSekte(sekte, slot);
			if (!hasKakak) {
				t.peran = TokohSekte.PERAN_KAKAK;
				hasKakak = true;
			} else if (!hasTetua && tier >= 6) {
				t.peran = TokohSekte.PERAN_TETUA;
				hasTetua = true;
			} else {
				int roll = rnd.nextInt(10);
				t.peran = roll < 2 ? TokohSekte.PERAN_INTI : roll < 5 ? TokohSekte.PERAN_DALAM : TokohSekte.PERAN_LUAR;
			}
			int kelamin = rnd.nextInt(100) < 45 ? Sosok.PEREMPUAN : Sosok.LAKI;
			t.nama = nama(rnd, taken, kelamin);
			taken.add(t.nama);
			// 39.F.1: realm about tier - 1 (+-1 stage), the role sets the band under the sect master
			int below = switch (t.peran) {
				case TokohSekte.PERAN_TETUA -> 3;
				case TokohSekte.PERAN_KAKAK -> 4;
				case TokohSekte.PERAN_INTI -> 5;
				case TokohSekte.PERAN_DALAM -> 6;
				default -> 7;
			};
			t.langkahAwal = Math.max(2, ketua - below + rnd.nextInt(3) - 1);
			t.hariLahir = awal ? 0L : today;
			sifat(t, rnd, aliran);
			t.dao = dao(rnd, d.color(sekte));
			t.senjata = rnd.nextInt(TokohSekte.SENJATA);
			t.suara = (t.perempuan() ? "f" : "m") + (1 + rnd.nextInt(3));
			t.postur = rnd.nextInt(Sosok.POSTUR_MAKS + 1);
			t.benih = rnd.nextInt(Sosok.BENIH_MASK);
			list.add(t);
			active++;
		}
		data.setDirty();
	}

	/** A free name index of sex {@code kelamin} (any of that sex when all are taken). */
	private static int nama(RandomSource rnd, Set<Integer> taken, int kelamin) {
		List<Integer> free = new ArrayList<>();
		List<Integer> all = new ArrayList<>();
		for (int i = 0; i < DiscipleNames.TOTAL; i++) {
			if (DiscipleNames.kelamin(i) == kelamin) {
				all.add(i);
				if (!taken.contains(i)) {
					free.add(i);
				}
			}
		}
		List<Integer> pool = free.isEmpty() ? all : free;
		return pool.get(rnd.nextInt(pool.size()));
	}

	/** Two traits of different axes, weighted by the path (39.F.1). */
	private static void sifat(TokohSekte t, RandomSource rnd, int aliran) {
		List<Sifat> weighted = new ArrayList<>();
		for (Sifat s : Sifat.values()) {
			int w = 2;
			if (aliran == 2 && (s == Sifat.LICIK || s == Sifat.PENDENDAM || s == Sifat.SOMBONG)) {
				w = 7;
			} else if (aliran == 0 && (s == Sifat.SETIA || s == Sifat.BERANI || s == Sifat.RENDAH_HATI)) {
				w = 7;
			} else if (aliran == 1 && (s == Sifat.AMBISIUS || s == Sifat.TENANG)) {
				w = 4;
			}
			for (int i = 0; i < w; i++) {
				weighted.add(s);
			}
		}
		while (t.sifat.size() < 2 && !weighted.isEmpty()) {
			Sifat pick = weighted.get(rnd.nextInt(weighted.size()));
			boolean clash = false;
			for (Sifat have : t.sifat) {
				if (have.axis() == pick.axis()) {
					clash = true;
				}
			}
			if (!clash) {
				t.sifat.add(pick);
			}
			weighted.removeIf(s -> s == pick);
		}
	}

	/** The Dao nearest the sect's ink colour when it is close enough, else a seeded one. */
	private static String dao(RandomSource rnd, int rgb) {
		Dao best = null;
		long bestD = Long.MAX_VALUE;
		for (Dao dao : Dao.values()) {
			int c = dao.color();
			long dr = (c >> 16 & 0xFF) - (rgb >> 16 & 0xFF);
			long dg = (c >> 8 & 0xFF) - (rgb >> 8 & 0xFF);
			long db = (c & 0xFF) - (rgb & 0xFF);
			long dist = dr * dr + dg * dg + db * db;
			if (dist < bestD) {
				bestD = dist;
				best = dao;
			}
		}
		if (best != null && bestD < 14000L && rnd.nextInt(3) != 0) {
			return best.id();
		}
		Dao[] all = Dao.values();
		return all[rnd.nextInt(all.length)].id();
	}

	// ================================================================== picking who is met

	/**
	 * A member of {@code sekte} to meet: not wounded, not gone; one that already holds a grudge against {@code soul} or has
	 * met it before is preferred (recurring characters), otherwise a seeded pick favouring the proud and the cunning
	 * for trouble ({@code cariMasalah}) or anyone.
	 */
	static @Nullable TokohSekte pilih(MinecraftServer server, String sekte, @Nullable UUID soul, long hari, RandomSource rnd, boolean cariMasalah) {
		List<TokohSekte> list = aktif(server, sekte);
		list.removeIf(t -> t.luka(hari));
		if (list.isEmpty()) {
			return null;
		}
		if (soul != null) {
			for (TokohSekte t : list) {
				if (t.benci(soul) && rnd.nextInt(3) != 0) {
					return t;
				}
			}
			for (TokohSekte t : list) {
				if (t.sikap.containsKey(soul.toString()) && rnd.nextInt(2) == 0) {
					return t;
				}
			}
		}
		List<TokohSekte> weighted = new ArrayList<>();
		for (TokohSekte t : list) {
			int w = 2;
			if (cariMasalah && (t.punya(Sifat.SOMBONG) || t.punya(Sifat.LICIK) || t.punya(Sifat.PENDENDAM))) {
				w = 5;
			}
			if (t.peran == TokohSekte.PERAN_TETUA) {
				w = 1;
			}
			for (int i = 0; i < w; i++) {
				weighted.add(t);
			}
		}
		return weighted.get(rnd.nextInt(weighted.size()));
	}

	/** The highest-ranked active member (the Young Elder, else the Eldest Senior), the one who speaks for the sect. */
	static @Nullable TokohSekte tetua(MinecraftServer server, String sekte) {
		TokohSekte best = null;
		for (TokohSekte t : aktif(server, sekte)) {
			if (best == null || t.peran > best.peran) {
				best = t;
			}
		}
		return best;
	}

	private Roster() {
	}
}
