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
 * <p>
 * DESIGN 41.6.12: the public readers {@link #daftar}, {@link #tokoh}, {@link #sikap}, {@link #ubahSikap}, {@link #lukai}
 * and {@link #ikutPergi} let Gabung Sekte use the same people as seniors and rivals of a player member (a player's UUID is
 * the {@code sikap.<uuid>} key beside our disciples' souls). The roster record itself stays package-private.
 */
public final class Roster {

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

	// ================================================================== public readers (DESIGN 41.6.12, Gabung Sekte)

	/**
	 * A read-only view of one roster member: id {@code <sect>#<slot>}, name key (and nickname key or ""), role id
	 * ({@code luar|dalam|inti|kakak|tetua}), the rank on {@code hari}, two trait ids, Dao id, sex, voice, weapon, body
	 * posture/seed, wounded until (world day) and status ({@code AKTIF|MEMBELOT|PERGI}).
	 */
	public record Tokoh(String id, String sekte, int slot, String kunciNama, String kunciJulukan, String peran, int peranNomor,
			dev.kultivator.data.Rank rank, List<Sifat> sifat, String dao, boolean perempuan, String suara, int senjata, int postur,
			int benih, long lukaSampai, String status) {

		/** The shown name ("Name 'Nickname'" once earned). */
		public net.minecraft.network.chat.MutableComponent nama() {
			net.minecraft.network.chat.MutableComponent name = net.minecraft.network.chat.Component.translatable(kunciNama);
			return kunciJulukan.isEmpty() ? name : net.minecraft.network.chat.Component.translatable(SeteruIds.k("nama_julukan"), name,
					net.minecraft.network.chat.Component.translatable(kunciJulukan));
		}

		public boolean aktif() {
			return SeteruIds.TOKOH_AKTIF.equals(status);
		}

		public boolean luka(long hari) {
			return hari < lukaSampai;
		}

		public boolean punya(Sifat s) {
			return sifat.contains(s);
		}
	}

	/** Every member of {@code sekte} (filled up to the sect's size first), as views; empty when D does not know the sect. */
	public static List<Tokoh> daftar(MinecraftServer server, String sekte) {
		List<Tokoh> out = new ArrayList<>();
		if (server == null || sekte == null) {
			return out;
		}
		long hari = Ingatan.today(server);
		int tier = SekteApi.diplomasi().tier(sekte);
		for (TokohSekte t : anggota(server, sekte)) {
			out.add(view(t, hari, tier));
		}
		return out;
	}

	/** The member {@code id} ({@code <sect>#<slot>}) as a view, or null. */
	public static @Nullable Tokoh tokoh(MinecraftServer server, String id) {
		TokohSekte t = server == null ? null : SeteruData.get(server).tokoh(id);
		return t == null ? null : view(t, Ingatan.today(server), SekteApi.diplomasi().tier(t.sekte));
	}

	/** The member's attitude toward {@code soul} (a player UUID or a disciple soul), -100..100; 0 when unknown. */
	public static int sikap(MinecraftServer server, String id, UUID soul) {
		TokohSekte t = server == null || soul == null ? null : SeteruData.get(server).tokoh(id);
		return t == null ? 0 : t.sikap(soul);
	}

	/** Changes the member's attitude toward {@code soul} by {@code delta} (clamped -100..100); false when unknown. */
	public static boolean ubahSikap(MinecraftServer server, String id, UUID soul, int delta) {
		SeteruData data = server == null || soul == null ? null : SeteruData.get(server);
		TokohSekte t = data == null ? null : data.tokoh(id);
		if (t == null) {
			return false;
		}
		t.ubahSikap(soul, delta);
		data.setDirty();
		return true;
	}

	/** "Terluka" (DESIGN 41.0 item 5): the member is not met for {@code hari} world days; nobody dies. */
	public static void lukai(MinecraftServer server, String id, int hari) {
		SeteruData data = server == null ? null : SeteruData.get(server);
		TokohSekte t = data == null ? null : data.tokoh(id);
		if (t != null && hari > 0) {
			t.lukaSampai = Math.max(t.lukaSampai, Ingatan.today(server) + hari);
			data.setDirty();
		}
	}

	/**
	 * The member follows a player who left the sect (DESIGN 41.7.4, "ikut kakaknya"): status {@code MEMBELOT}, replaced when
	 * the sect grows. False when unknown or not active.
	 */
	public static boolean ikutPergi(MinecraftServer server, String id) {
		SeteruData data = server == null ? null : SeteruData.get(server);
		TokohSekte t = data == null ? null : data.tokoh(id);
		if (t == null || !t.aktif()) {
			return false;
		}
		t.status = SeteruIds.TOKOH_MEMBELOT;
		data.setDirty();
		return true;
	}

	private static Tokoh view(TokohSekte t, long hari, int tier) {
		return new Tokoh(t.id, t.sekte, t.slot, t.kunciNama(), t.julukan >= 0 ? SeteruIds.k("julukan." + t.julukan) : "",
				TokohSekte.PERAN[Math.max(0, Math.min(TokohSekte.PERAN.length - 1, t.peran))], t.peran, t.rank(hari, Math.max(1, tier)),
				List.copyOf(t.sifat), t.dao, t.perempuan(), t.suara, t.senjata, t.postur, t.benih, t.lukaSampai, t.status);
	}

	private Roster() {
	}
}
