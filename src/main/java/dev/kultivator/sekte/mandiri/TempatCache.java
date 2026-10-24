package dev.kultivator.sekte.mandiri;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hall's point cache (DESIGN 39.4.6): where each activity happens, built once per {@link #UMUR} ticks per hall from the
 * named anchors of the built sect plan ({@code SectBuild.anchor}: {@code meditasi_<i>}, {@code meditation},
 * {@code library}, {@code arena}, {@code post_*}, {@code kebun_rawat_<n>}, {@code tambang_mulut}, {@code asrama_<n>},
 * {@code kursi_<n>}, {@code darat_<i>}, {@code waterfall_*}, {@code arrival}, {@code gate_altar_*},
 * {@code panggung_pedang} ...) plus eight courtyard points round the hall stele read from the height map of loaded
 * chunks. Never a block scan. A hall without a built plan still has its stele ring, so every hall can meditate, rest,
 * eat and play in its courtyard.
 * <p>
 * Seats are reserved per soul while an activity uses them ({@link #pesan}, released by {@link #lepas} or when the
 * reservation runs out), so two disciples never sit on one cushion.
 */
public final class TempatCache {

	/** Rebuild interval per hall (ticks). */
	public static final int UMUR = 1200;

	/** Kinds of point. */
	public enum Titik {
		/** Meditation cushions, Qi seats, the formation disc, the sky plateau's seats. */
		MEDITASI,
		/** Edges with a view: waterfalls, the sky ring's landing points, graves, the stair top. */
		RENUNG,
		/** Dormitories and benches. */
		ISTIRAHAT,
		/** Kitchen and the main hall's tables. */
		MAKAN,
		/** Tea seats and the main hall's courtyard. */
		SOSIAL,
		/** The open court round the stele and the arrival square. */
		HALAMAN,
		/** The gate: arrival, gate altars, the sky gate. */
		GERBANG,
		/** The Pavilion of Scriptures. */
		PAVILIUN,
		/** Arena, tournament centre, sword stage. */
		ARENA,
		/** Kitchen post and the alchemy room. */
		DAPUR,
		/** Garden keepers' curbs and the garden post. */
		KEBUN,
		/** The spirit-mine mouth. */
		TAMBANG,
		/** The formation disc. */
		FORMASI,
		/** The four duty posts. */
		POS,
		/** The treasury (Lumbung). */
		LUMBUNG,
		/** A secluded room: the grotto, a dorm, the meditation room. */
		TUTUP,
		/** The sword stage and the arrival square (departures). */
		TERBANG
	}

	private record Tempat(BlockPos pos, int kursi) {
	}

	private static final class Aula {
		final EnumMap<Titik, List<Tempat>> titik = new EnumMap<>(Titik.class);
		long dibangun;
	}

	private static final Map<UUID, Aula> AULA = new ConcurrentHashMap<>();
	/** Reserved slot (packed pos) -> soul and end time, per hall. */
	private static final Map<UUID, Map<Long, Pesanan>> PESAN = new ConcurrentHashMap<>();

	private record Pesanan(UUID soul, long sampai) {
	}

	/** Small offsets for the slots of open places (the anchor itself first). */
	private static final int[][] SLOT = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};

	// ================================================================== building

	/** The answer for a disciple outside its hall's dimension: no places at all. */
	private static final Aula KOSONG = kosong();

	private static Aula kosong() {
		Aula a = new Aula();
		for (Titik t : Titik.values()) {
			a.titik.put(t, List.of());
		}
		return a;
	}

	private static Aula aula(ServerLevel level, UUID owner) {
		Halls.HallView view = Halls.hallOf(owner);
		if (view == null || view.dim() == null || !view.dim().equals(level.dimension())) {
			return KOSONG;
		}
		long now = level.getGameTime();
		Aula cached = AULA.get(owner);
		if (cached != null && now - cached.dibangun < UMUR) {
			return cached;
		}
		Aula fresh = bangun(level, owner);
		fresh.dibangun = now;
		AULA.put(owner, fresh);
		return fresh;
	}

	private static Aula bangun(ServerLevel level, UUID owner) {
		Aula a = new Aula();
		for (Titik t : Titik.values()) {
			a.titik.put(t, new ArrayList<>());
		}
		// Seats: one disciple each.
		for (int i = 0; i < 8; i++) {
			add(a, owner, Titik.MEDITASI, "meditasi_" + i, 1);
			add(a, owner, Titik.ISTIRAHAT, "asrama_" + i, 2);
			add(a, owner, Titik.TUTUP, "asrama_" + i, 1);
			add(a, owner, Titik.SOSIAL, "kursi_" + i, 2);
			add(a, owner, Titik.ISTIRAHAT, "kursi_" + i, 1);
			add(a, owner, Titik.KEBUN, "kebun_rawat_" + (i + 1), 1);
			add(a, owner, Titik.RENUNG, "darat_" + i, 1);
			add(a, owner, Titik.RENUNG, "makam_" + i, 1);
			add(a, owner, Titik.RENUNG, "tangga_rehat_" + i, 1);
		}
		add(a, owner, Titik.MEDITASI, "meditation", 3);
		add(a, owner, Titik.MEDITASI, "kursi_qi", 3);
		add(a, owner, Titik.MEDITASI, "aula_dao_langit", 4);
		add(a, owner, Titik.MEDITASI, "formation", 4);
		add(a, owner, Titik.TUTUP, "grotto", 2);
		add(a, owner, Titik.TUTUP, "meditation", 1);
		add(a, owner, Titik.RENUNG, "waterfall_kolam", 3);
		add(a, owner, Titik.RENUNG, "waterfall_lotus", 3);
		add(a, owner, Titik.RENUNG, "makam_pusat", 3);
		add(a, owner, Titik.RENUNG, "makam_tugu", 2);
		add(a, owner, Titik.RENUNG, "leluhur", 2);
		add(a, owner, Titik.RENUNG, "tangga_puncak", 3);
		add(a, owner, Titik.MAKAN, "post_kitchen", 4);
		add(a, owner, Titik.MAKAN, "main_hall", 6);
		add(a, owner, Titik.SOSIAL, "main_hall", 6);
		add(a, owner, Titik.HALAMAN, "main_hall", 9);
		add(a, owner, Titik.HALAMAN, "arrival", 9);
		add(a, owner, Titik.HALAMAN, "turnamen_pusat", 9);
		add(a, owner, Titik.GERBANG, "arrival", 4);
		add(a, owner, Titik.GERBANG, "arrival_kaki", 3);
		add(a, owner, Titik.GERBANG, "gate_altar_e", 2);
		add(a, owner, Titik.GERBANG, "gate_altar_w", 2);
		add(a, owner, Titik.GERBANG, "gerbang_langit", 3);
		add(a, owner, Titik.PAVILIUN, "library", 5);
		add(a, owner, Titik.ARENA, "arena", 9);
		add(a, owner, Titik.ARENA, "turnamen_pusat", 9);
		add(a, owner, Titik.ARENA, "panggung_pedang", 5);
		add(a, owner, Titik.DAPUR, "alkimia", 3);
		add(a, owner, Titik.DAPUR, "alchemy", 3);
		add(a, owner, Titik.DAPUR, "post_kitchen", 2);
		add(a, owner, Titik.KEBUN, "post_garden", 2);
		add(a, owner, Titik.TAMBANG, "tambang_mulut", 3);
		add(a, owner, Titik.FORMASI, "formation", 4);
		add(a, owner, Titik.POS, "post_water", 1);
		add(a, owner, Titik.POS, "post_garden", 1);
		add(a, owner, Titik.POS, "post_kitchen", 1);
		add(a, owner, Titik.POS, "post_farm", 1);
		add(a, owner, Titik.LUMBUNG, "treasury", 3);
		add(a, owner, Titik.TERBANG, "panggung_pedang", 3);
		add(a, owner, Titik.TERBANG, "arrival", 3);

		// The stele ring: eight courtyard points 6-9 blocks round the hall stele, on the surface of loaded chunks.
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall != null && hall.dim() != null && hall.dim().equals(level.dimension())) {
			BlockPos c = hall.pos();
			for (int i = 0; i < 8; i++) {
				double angle = Math.PI * 2.0D * i / 8.0D + 0.3D;
				int r = 6 + (i % 2) * 3;
				int x = c.getX() + (int) Math.round(Math.cos(angle) * r);
				int z = c.getZ() + (int) Math.round(Math.sin(angle) * r);
				BlockPos column = new BlockPos(x, c.getY(), z);
				if (!level.hasChunkAt(column)) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (Math.abs(y - c.getY()) > 6) {
					continue;
				}
				BlockPos spot = new BlockPos(x, y, z);
				a.titik.get(Titik.HALAMAN).add(new Tempat(spot, 1));
				a.titik.get(Titik.ISTIRAHAT).add(new Tempat(spot, 1));
				a.titik.get(Titik.MEDITASI).add(new Tempat(spot, 1));
				a.titik.get(Titik.SOSIAL).add(new Tempat(spot, 2));
				if (i % 2 == 0) {
					a.titik.get(Titik.RENUNG).add(new Tempat(spot, 1));
				}
			}
			// The stele itself: the gate fallback and the last courtyard place.
			BlockPos front = c.south(2);
			a.titik.get(Titik.GERBANG).add(new Tempat(front, 2));
			a.titik.get(Titik.HALAMAN).add(new Tempat(front, 6));
		}
		return a;
	}

	private static void add(Aula a, UUID owner, Titik titik, String anchor, int kursi) {
		BlockPos pos = SectBuild.anchor(owner, anchor);
		if (pos != null) {
			a.titik.get(titik).add(new Tempat(pos.immutable(), kursi));
		}
	}

	/** Forgets a hall's cache (rebuilt at the next question), e.g. after the sect plan moved. */
	public static void lupakan(UUID owner) {
		AULA.remove(owner);
	}

	// ================================================================== questions

	/** The kinds of point that serve {@code k}, best first (empty: the activity needs no place of the hall). */
	public static Titik[] untuk(Kegiatan k) {
		return switch (k) {
			case MEDITASI, SERAP_BATU -> new Titik[]{Titik.MEDITASI, Titik.HALAMAN};
			case TUTUP_DIRI -> new Titik[]{Titik.TUTUP, Titik.MEDITASI};
			case LATIH_JURUS, TANDING -> new Titik[]{Titik.ARENA, Titik.HALAMAN};
			case BELAJAR -> new Titik[]{Titik.PAVILIUN};
			case MENGAJAR -> new Titik[]{Titik.PAVILIUN, Titik.ARENA, Titik.HALAMAN};
			case MERAMU -> new Titik[]{Titik.DAPUR};
			case JIMAT -> new Titik[]{Titik.PAVILIUN, Titik.MAKAN, Titik.HALAMAN};
			case KEBUN -> new Titik[]{Titik.KEBUN};
			case TAMBANG -> new Titik[]{Titik.TAMBANG};
			case POS_TUGAS -> new Titik[]{Titik.POS};
			case JAGA_FORMASI -> new Titik[]{Titik.FORMASI};
			case JAGA_GERBANG -> new Titik[]{Titik.GERBANG};
			case SOSIAL -> new Titik[]{Titik.SOSIAL, Titik.HALAMAN};
			case MAKAN -> new Titik[]{Titik.MAKAN, Titik.HALAMAN};
			case ISTIRAHAT -> new Titik[]{Titik.ISTIRAHAT, Titik.HALAMAN};
			case MERENUNG -> new Titik[]{Titik.RENUNG, Titik.HALAMAN};
			case BERMAIN -> new Titik[]{Titik.HALAMAN};
			case BERKEMAS, TIBA -> new Titik[]{Titik.LUMBUNG, Titik.HALAMAN};
			case BERANGKAT -> new Titik[]{Titik.TERBANG, Titik.GERBANG};
			default -> new Titik[0];
		};
	}

	/** True when the hall has at least one point of {@code titik} in the disciple's dimension. */
	public static boolean ada(ServerLevel level, UUID owner, Titik titik) {
		return !aula(level, owner).titik.get(titik).isEmpty();
	}

	/** Every point of a kind (copies; for the test command and 39 executors that look for a free station). */
	public static List<BlockPos> semua(ServerLevel level, UUID owner, Titik titik) {
		List<BlockPos> out = new ArrayList<>();
		for (Tempat t : aula(level, owner).titik.get(titik)) {
			out.add(t.pos());
		}
		return out;
	}

	/**
	 * A free slot for {@code k} near the disciple: the kinds of {@link #untuk} in order; inside a kind the nearest place
	 * with a free slot, ties spread by the soul. Null when the hall has no such place (the activity then scores 0) or
	 * every slot is taken.
	 */
	public static @Nullable BlockPos cari(DiscipleEntity murid, Kegiatan k) {
		if (!(murid.level() instanceof ServerLevel level) || murid.ownerId() == null) {
			return null;
		}
		UUID owner = murid.ownerId();
		Titik[] kinds = untuk(k);
		if (kinds.length == 0) {
			return murid.blockPosition();
		}
		Aula a = aula(level, owner);
		Map<Long, Pesanan> taken = pesanan(owner, level.getGameTime());
		UUID soul = murid.soulId();
		for (Titik kind : kinds) {
			BlockPos best = null;
			double bestScore = Double.MAX_VALUE;
			for (Tempat t : a.titik.get(kind)) {
				int slots = Math.max(1, Math.min(SLOT.length, t.kursi()));
				for (int s = 0; s < slots; s++) {
					BlockPos slot = t.pos().offset(SLOT[s][0], 0, SLOT[s][1]);
					Pesanan p = taken.get(slot.asLong());
					if (p != null && !p.soul().equals(soul)) {
						continue;
					}
					double score = murid.distanceToSqr(slot.getX() + 0.5D, slot.getY(), slot.getZ() + 0.5D)
							+ Math.floorMod(soul.hashCode() + slot.hashCode(), 17) * 4.0D;
					if (score < bestScore) {
						bestScore = score;
						best = slot;
					}
					break;
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	/** Reserves {@code pos} for the soul until {@code sampai} (game time). */
	public static void pesan(UUID owner, UUID soul, BlockPos pos, long sampai) {
		if (owner == null || soul == null || pos == null) {
			return;
		}
		PESAN.computeIfAbsent(owner, key -> new HashMap<>()).put(pos.asLong(), new Pesanan(soul, sampai));
	}

	/** Releases every reservation of the soul. */
	public static void lepas(UUID owner, UUID soul) {
		Map<Long, Pesanan> map = owner == null ? null : PESAN.get(owner);
		if (map != null && soul != null) {
			map.values().removeIf(p -> p.soul().equals(soul));
		}
	}

	private static Map<Long, Pesanan> pesanan(UUID owner, long now) {
		Map<Long, Pesanan> map = PESAN.computeIfAbsent(owner, key -> new HashMap<>());
		for (Iterator<Pesanan> it = map.values().iterator(); it.hasNext(); ) {
			if (it.next().sampai() < now) {
				it.remove();
			}
		}
		return map;
	}

	/** Seats reserved in the hall now (for {@code uji ... anggaran}). */
	public static int jumlahPesanan(UUID owner) {
		Map<Long, Pesanan> map = PESAN.get(owner);
		return map == null ? 0 : map.size();
	}

	/** Clears everything (server stop). */
	static void kosongkan() {
		AULA.clear();
		PESAN.clear();
	}

	private TempatCache() {
	}
}
