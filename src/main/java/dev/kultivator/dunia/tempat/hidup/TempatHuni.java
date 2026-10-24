package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * One inhabited place while a player is near it (DESIGN 38.N.1): its residents, its life points by type (read from the
 * loaded pieces, refreshed every 20 s while chunks load), the bodies standing for residents, and the titik locks (one
 * person per chair, stall or bed; 38.N.4 butir 1). Built and dropped by {@link Pemanggil}; never saved.
 *
 * <p>Slots: a titik belongs to the resident whose slot equals {@code hash(pos, site) mod n} ({@link #slot}), so a
 * resident's bed, house and work spot are the same whichever chunk loaded first; a titik nobody owns stays free for the
 * shared uses (chairs, tables, chat corners), a resident whose titik is not loaded yet simply has none for now.
 */
public final class TempatHuni {

	/** Where a resident goes: the block it stands in, the facing at the goal and whether it is a real titik. */
	public record Tujuan(BlockPos pos, Direction hadap, String jenis, boolean nyata) {
	}

	/** Titik refresh period while the place is active (ticks). */
	private static final long SEGAR = 400L;

	public final long situsId;
	public final String jenis;
	public final BoundingBox kotak;
	public final int kekuatan;
	public final ResourceKey<Level> dimensi;
	private final List<Penduduk> asli;
	private List<Penduduk> penduduk;
	private final Map<String, Integer> modKerja;
	private final int rumahN;

	private final Map<String, List<Titik>> titik = new HashMap<>();
	private int titikN;
	private long titikT = Long.MIN_VALUE;
	/** Seats of the taverns, tea houses and eating houses (near a tea kettle, or a stove with four seats or more). */
	private List<Titik> kursiKedai = List.of();
	private final Map<Integer, Titik> kasurMilik = new HashMap<>();
	private final Map<Integer, Titik> rumahMilik = new HashMap<>();
	private final Map<String, Map<Integer, Titik>> kerjaMilik = new HashMap<>();

	/** Bodies by resident id (only live, loaded ones). */
	final Map<Integer, KultivatorNpc> tubuh = new LinkedHashMap<>();
	/** Locked titik: block pos -> resident id. */
	private final Map<Long, Integer> kunci = new HashMap<>();

	/** Weather and sky of the place, refreshed by {@link Pemanggil} every cycle. */
	Jadwal.Cuaca cuaca = Jadwal.Cuaca.CERAH;
	/** Game time of the last cycle with a player near (dropped after a minute without). */
	long aktifT;
	/** Inter-NPC sales counted for the vendors' talk (38.N.4 butir 3, "laris"). */
	int laris;
	/** The night drum (22:00) sounded: the night market closes early until this game time (38.N.4 butir 12). */
	long tutupSampai;
	/** The temple bell last rang here (game time; the town bows toward it, 38.N.4 butir 12). */
	long loncengT = Long.MIN_VALUE;

	/** Life states that are not {@link PendudukData#SEHAT} (resident id -> state), refreshed every {@link Pemanggil} cycle. */
	final Map<Integer, Integer> keadaan = new HashMap<>();

	/** The crowd override the watchers below were picked for ({@code tonton:<x,z>}, C39.K11). */
	private String tontonAcara = "";
	/** Resident id -> place in the ring (0..{@link HidupApi#TONTON_MAKS}-1). */
	private final Map<Integer, Integer> tonton = new HashMap<>();

	/** Street nodes ({@code jalan} titik) for the L1 walk, their links, and the titik count they were built from. */
	private List<BlockPos> simpul = List.of();
	private int[][] tautan = new int[0][];
	private int simpulDari = -1;

	TempatHuni(TempatInfo info, ResourceKey<Level> dimensi, List<Penduduk> penduduk) {
		this.situsId = info.situsId();
		this.jenis = info.jenis();
		this.kotak = info.kotak();
		this.kekuatan = info.kekuatan();
		this.dimensi = dimensi;
		this.asli = penduduk;
		this.penduduk = penduduk;
		this.modKerja = PendudukGen.hitungKerja(penduduk);
		int max = 0;
		for (Penduduk p : penduduk) {
			max = Math.max(max, p.rumah());
		}
		this.rumahN = Math.max(1, max);
	}

	/** The residents as they are now (newcomers applied, {@link PendudukData#terapkan}). */
	public List<Penduduk> penduduk() {
		return penduduk;
	}

	/** Re-applies the saved changes (newcomers) to the generated residents. */
	void terapkan(PendudukData data) {
		List<Penduduk> now = new ArrayList<>(asli.size());
		for (Penduduk p : asli) {
			now.add(data.terapkan(situsId, p));
		}
		penduduk = List.copyOf(now);
	}

	public @Nullable Penduduk orang(int id) {
		return id >= 0 && id < penduduk.size() ? penduduk.get(id) : null;
	}

	/** The number of titik read so far. */
	public int jumlahTitik() {
		return titikN;
	}

	/** Titik of one type (empty when none loaded). */
	public List<Titik> titik(String type) {
		return titik.getOrDefault(type, List.of());
	}

	/** Every titik type with its count (commands). */
	public Map<String, Integer> ringkasTitik() {
		Map<String, Integer> out = new java.util.TreeMap<>();
		titik.forEach((k, v) -> out.put(k, v.size()));
		return out;
	}

	// ================================================================== titik refresh and slots

	/** Re-reads the titik from loaded pieces when due (or forced). */
	void segarkan(ServerLevel level, long now, boolean paksa) {
		if (!paksa && now - titikT < SEGAR) {
			return;
		}
		titikT = now;
		List<Titik> all = TempatApi.titik(level, situsId, "");
		if (all.size() == titikN && !paksa) {
			return;
		}
		titik.clear();
		for (Titik t : all) {
			titik.computeIfAbsent(t.jenis(), k -> new ArrayList<>()).add(t);
		}
		for (List<Titik> list : titik.values()) {
			list.sort(Comparator.comparingLong(t -> t.pos().asLong()));
		}
		titikN = all.size();
		kursiKedai = cariKursiKedai();
		kasurMilik.clear();
		rumahMilik.clear();
		kerjaMilik.clear();
		int n = Math.max(1, asli.size());
		for (Titik t : titik("kasur")) {
			kasurMilik.putIfAbsent(Math.floorMod(slot(t), n), t);
		}
		for (Titik t : titik("bangunan")) {
			rumahMilik.putIfAbsent(1 + Math.floorMod(slot(t), rumahN), t);
		}
		for (Map.Entry<String, Integer> e : modKerja.entrySet()) {
			Map<Integer, Titik> owners = new HashMap<>();
			for (Titik t : titik(e.getKey())) {
				owners.putIfAbsent(Math.floorMod(slot(t), Math.max(1, e.getValue())), t);
			}
			kerjaMilik.put(e.getKey(), owners);
		}
	}

	/** Seat titik within 10 blocks of a tea kettle, or of a stove that has at least four seats round it (not a home kitchen). */
	private List<Titik> cariKursiKedai() {
		List<Titik> seats = new ArrayList<>();
		for (String type : List.of("kursi", "meja_makan", "kursi_lantai")) {
			seats.addAll(titik(type));
		}
		List<Titik> hearths = new ArrayList<>(titik("kerja:teko"));
		for (Titik stove : titik("kerja:kuali")) {
			int n = 0;
			for (Titik seat : seats) {
				if (dekatKedai(stove, seat) && ++n >= 4) {
					hearths.add(stove);
					break;
				}
			}
		}
		List<Titik> out = new ArrayList<>();
		for (Titik seat : seats) {
			for (Titik h : hearths) {
				if (dekatKedai(h, seat)) {
					out.add(seat);
					break;
				}
			}
		}
		return out;
	}

	private static boolean dekatKedai(Titik a, Titik b) {
		int dx = a.pos().getX() - b.pos().getX();
		int dz = a.pos().getZ() - b.pos().getZ();
		return dx * dx + dz * dz <= 100 && Math.abs(a.pos().getY() - b.pos().getY()) <= 4;
	}

	/** True when the place has a market stall. */
	boolean adaPasar() {
		return !titik("lapak").isEmpty() || !titik("kerja:kios").isEmpty();
	}

	/** True when the place has a tavern, tea house or eating house. */
	boolean adaKedai() {
		return !kursiKedai.isEmpty();
	}

	/** The slot hash of a titik in this place. */
	int slot(Titik t) {
		long h = PendudukGen.mixSeed(t.pos().asLong() ^ situsId * 0xC2B2AE3D27D4EB4FL);
		return (int) (h ^ h >>> 32) & 0x7FFFFFFF;
	}

	/** The resident's own bed, or null. */
	public @Nullable Titik kasur(Penduduk p) {
		return p.kasur() == Penduduk.TANPA ? null : kasurMilik.get(p.kasur());
	}

	/** The resident's house box, or null. */
	public @Nullable Titik rumah(Penduduk p) {
		return p.rumah() <= 0 ? null : rumahMilik.get(p.rumah());
	}

	/**
	 * The resident's own work titik, or a shared one of its work type (or of the type's twin, {@link #kembar}), or null. A
	 * titik built for one role (data {@code peran}, e.g. the guild clerks' desks) goes to that role first and never to
	 * another while others are free.
	 */
	public @Nullable Titik kerja(Penduduk p) {
		String type = KerjaPeran.untuk(p.peran(), p.benih()).titik();
		List<Titik> tagged = bertanda(type, p.peran());
		if (!tagged.isEmpty()) {
			return tagged.get((int) Math.floorMod(p.benih() >>> 11, (long) tagged.size()));
		}
		Map<Integer, Titik> owners = kerjaMilik.get(type);
		if (owners != null && p.kerja() != Penduduk.TANPA) {
			Titik own = owners.get(p.kerja());
			if (own != null && untuk(own, p.peran())) {
				return own;
			}
		}
		List<Titik> shared = bebasUntuk(titik(type), p.peran());
		if (shared.isEmpty()) {
			shared = bebasUntuk(titik(kembar(type)), p.peran());
		}
		if (!shared.isEmpty()) {
			return shared.get((int) Math.floorMod(p.benih() >>> 11, (long) shared.size()));
		}
		return null;
	}

	/**
	 * The twin titik type: the same use written two ways by different generators (a desk as {@code meja_tulis} or
	 * {@code kerja:meja_tulis}, an altar as {@code dupa} or {@code kerja:dupa}, a stall as {@code lapak} or
	 * {@code kerja:kios}, a stove as {@code kerja:tungku} or {@code kerja:kipas}, a mortar as {@code kerja:lumpang} or
	 * {@code kerja:lesung}, the oar spot as {@code jalur_perahu} or {@code kerja:dayung}); the type itself otherwise.
	 */
	static String kembar(String type) {
		return switch (type) {
			case "meja_tulis" -> "kerja:meja_tulis";
			case "kerja:meja_tulis" -> "meja_tulis";
			case "dupa" -> "kerja:dupa";
			case "kerja:dupa" -> "dupa";
			case "lapak" -> "kerja:kios";
			case "kerja:kios" -> "lapak";
			case "kerja:tungku" -> "kerja:kipas";
			case "kerja:kipas" -> "kerja:tungku";
			case "kerja:lumpang" -> "kerja:lesung";
			case "kerja:lesung" -> "kerja:lumpang";
			case "jalur_perahu" -> "kerja:dayung";
			case "kerja:dayung" -> "jalur_perahu";
			case "kursi_lantai" -> "kursi";
			default -> type;
		};
	}

	/** True when titik {@code t} may serve {@code peran}: it names no role, or names this one. */
	static boolean untuk(Titik t, NpcRole peran) {
		String tag = t.data().get("peran");
		return tag == null || tag.isEmpty() || tag.equalsIgnoreCase(peran.name()) || tag.equalsIgnoreCase(peran.id());
	}

	/** Titik of {@code type} (or its twin) built for {@code peran} by name. */
	private List<Titik> bertanda(String type, NpcRole peran) {
		List<Titik> out = new ArrayList<>();
		for (String each : type.equals(kembar(type)) ? List.of(type) : List.of(type, kembar(type))) {
			for (Titik t : titik(each)) {
				String tag = t.data().get("peran");
				if (tag != null && !tag.isEmpty() && untuk(t, peran)) {
					out.add(t);
				}
			}
		}
		return out;
	}

	/** {@code list} without the titik built for other roles (the list itself when that would leave none). */
	private static List<Titik> bebasUntuk(List<Titik> list, NpcRole peran) {
		if (list.isEmpty()) {
			return list;
		}
		List<Titik> out = new ArrayList<>(list.size());
		for (Titik t : list) {
			if (untuk(t, peran)) {
				out.add(t);
			}
		}
		return out.isEmpty() ? list : out;
	}

	/** Where the resident lives: the house's door, its bed or the place centre (data positions). */
	public BlockPos rumahPos(Penduduk p) {
		Titik house = rumah(p);
		if (house != null) {
			BoundingBox box = house.kotak();
			BlockPos centre = new BlockPos((box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2);
			Titik door = dekat(titik("pintu"), centre, 14);
			return door != null ? door.pos() : centre;
		}
		Titik bed = kasur(p);
		if (bed != null) {
			return bed.pos();
		}
		List<Titik> doors = titik("pintu");
		if (!doors.isEmpty()) {
			return doors.get((int) Math.floorMod(p.benih() >>> 13, (long) doors.size())).pos();
		}
		return pusat();
	}

	/** The centre of the place at the height of its first titik (a data position of last resort). */
	public BlockPos pusat() {
		BlockPos c = kotak.getCenter();
		int y = c.getY();
		for (List<Titik> list : titik.values()) {
			if (!list.isEmpty()) {
				y = list.getFirst().pos().getY();
				break;
			}
		}
		return new BlockPos(c.getX(), y, c.getZ());
	}

	private static @Nullable Titik dekat(List<Titik> list, BlockPos at, int max) {
		Titik best = null;
		double bestD = (double) max * max;
		for (Titik t : list) {
			double d = t.pos().distSqr(at);
			if (d <= bestD) {
				bestD = d;
				best = t;
			}
		}
		return best;
	}

	// ================================================================== goals

	/**
	 * The goal of {@code p} doing {@code k} at hour {@code jam}; with {@code kunciUntuk} a titik already locked by someone
	 * else is skipped (bodies), without it any matching titik serves (data positions). Null: stay where it stands.
	 */
	public @Nullable Tujuan tujuan(ServerLevel level, Penduduk p, Kegiatan k, int jam, boolean kunciUntuk) {
		String to = k.tujuan();
		if (Kegiatan.TETAP.equals(to)) {
			return null;
		}
		if (to.startsWith(Kegiatan.XZ)) {
			return xz(level, p, to.substring(Kegiatan.XZ.length()));
		}
		if (Kegiatan.KASUR.equals(to)) {
			Titik bed = kasur(p);
			return bed != null ? new Tujuan(bed.pos(), bed.hadap(), "kasur", true) : new Tujuan(rumahPos(p), Direction.SOUTH, "rumah", false);
		}
		if (Kegiatan.RUMAH.equals(to)) {
			return new Tujuan(rumahPos(p), Direction.SOUTH, "rumah", rumah(p) != null);
		}
		if (Kegiatan.KEDAI.equals(to)) {
			if (!kursiKedai.isEmpty()) {
				return pilih(level, p, kursiKedai, jam, kunciUntuk);
			}
			to = "kursi";
		}
		if (to.startsWith(Kegiatan.DEPAN_LAPAK)) {
			Tujuan front = depanLapak(level, p, giliran(to, Kegiatan.DEPAN_LAPAK));
			if (front != null) {
				return front;
			}
			to = "obrol";
		}
		if (to.startsWith(Kegiatan.JALAN)) {
			return jalanJalan(level, p, giliran(to, Kegiatan.JALAN));
		}
		if (Kegiatan.KERJA.equals(to)) {
			Titik own = kerja(p);
			if (own != null) {
				return new Tujuan(own.pos(), own.hadap(), own.jenis(), true);
			}
			to = cadangan(p.peran(), KerjaPeran.untuk(p.peran(), p.benih()).titik());
		}
		List<Titik> list = titik(to);
		if (list.isEmpty()) {
			list = titik(kembar(to));
		}
		if (list.isEmpty()) {
			String alt = cadangan(p.peran(), to);
			list = titik(alt);
			if (list.isEmpty()) {
				list = titik(kembar(alt));
			}
			if (list.isEmpty()) {
				return new Tujuan(rumahPos(p), Direction.SOUTH, "rumah", false);
			}
		}
		return pilih(level, p, bebasUntuk(list, p.peran()), jam, kunciUntuk);
	}

	/** The turn number after a goal prefix ({@code @jalan:17} -> 17; 0 when missing). */
	private static int giliran(String tujuan, String prefix) {
		try {
			return Integer.parseInt(tujuan.substring(prefix.length()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * A shopper's spot in front of one of the market stalls (the turn number picks the stall; it changes every twenty minutes): two blocks out from the
	 * stall along its facing, shifted sideways by the resident so a crowd stands side by side, facing the vendor.
	 */
	private @Nullable Tujuan depanLapak(ServerLevel level, Penduduk p, int turn) {
		List<Titik> stalls = new ArrayList<>(titik("lapak"));
		stalls.addAll(titik("kerja:kios"));
		if (stalls.isEmpty()) {
			return null;
		}
		Titik stall = stalls.get((int) Math.floorMod(p.benih() + turn * 7L, (long) stalls.size()));
		Direction face = stall.hadap();
		Direction side = face.getClockWise();
		int shift = Math.floorMod(p.id(), 3) - 1;
		for (int out : new int[]{2, 3, 1}) {
			BlockPos at = berdiri(level, stall.pos().relative(face, out).relative(side, shift));
			if (at != null) {
				return new Tujuan(at, face.getOpposite(), "lapak", false);
			}
		}
		return null;
	}

	/**
	 * A street spot across the place for a stroll (the turn number picks it; it changes every twenty minutes): a street node, else the free block
	 * before a door, else a chat corner, a viewpoint or a play spot; home when the place has none of them.
	 */
	private Tujuan jalanJalan(ServerLevel level, Penduduk p, int turn) {
		for (String type : List.of("jalan", "pintu", "obrol", "pandang", "main")) {
			List<Titik> list = titik(type);
			if (list.isEmpty()) {
				continue;
			}
			Titik t = list.get((int) Math.floorMod(p.benih() * 31L + turn * 13L, (long) list.size()));
			BlockPos at = type.equals("pintu") ? berdiri(level, t.pos().relative(t.hadap(), 2)) : t.pos();
			if (at != null) {
				return new Tujuan(at, t.hadap(), type, false);
			}
		}
		return new Tujuan(rumahPos(p), Direction.SOUTH, "rumah", false);
	}

	/**
	 * A block a person can stand in at {@code at} or one block up or down (sturdy floor, two free blocks, no fluid), or
	 * null. Never searches far: a spot in a wall or over a drop is simply not used.
	 */
	static @Nullable BlockPos berdiri(ServerLevel level, BlockPos at) {
		for (int dy : new int[]{0, 1, -1}) {
			BlockPos feet = at.above(dy);
			if (!level.isLoaded(feet)) {
				return null;
			}
			BlockPos floor = feet.below();
			if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() && level.getFluidState(feet).isEmpty()) {
				return feet;
			}
		}
		return null;
	}

	/** One of the four titik of the list nearest the resident's home, by seed and hour, skipping locked ones. */
	private Tujuan pilih(ServerLevel level, Penduduk p, List<Titik> list, int jam, boolean kunciUntuk) {
		BlockPos home = rumahPos(p);
		List<Titik> near = new ArrayList<>(list);
		if (near.size() > 4) {
			near.sort(Comparator.comparingDouble(t -> t.pos().distSqr(home)));
			near = near.subList(0, Math.max(4, near.size() / 6));
		}
		int start = (int) Math.floorMod(p.benih() + jam / 2, (long) near.size());
		for (int i = 0; i < near.size(); i++) {
			Titik t = near.get((start + i) % near.size());
			Integer owner = kunci.get(t.pos().asLong());
			if (!kunciUntuk || owner == null || owner == p.id()) {
				return new Tujuan(t.pos(), t.hadap(), t.jenis(), true);
			}
		}
		// all taken: stand beside one of them (each latecomer at its own free block round the seats, facing in)
		for (int i = 0; i < near.size(); i++) {
			Titik t = near.get((start + i) % near.size());
			for (int k = 0; k < 8; k++) {
				int ring = (p.id() + k) % 8;
				int r = 2 + (p.id() / 8 + k / 4) % 2;
				BlockPos at = berdiri(level, t.pos().offset(SEKITAR[ring][0] * r, 0, SEKITAR[ring][1] * r));
				if (at != null && !kunci.containsKey(at.asLong())) {
					Direction face = Direction.fromYRot(Math.toDegrees(Math.atan2(-(t.pos().getX() - at.getX()), t.pos().getZ() - at.getZ())));
					return new Tujuan(at, face, t.jenis(), false);
				}
			}
		}
		Titik t = near.get(start);
		BlockPos queue = t.pos().relative(t.hadap(), 3);
		return new Tujuan(queue, t.hadap().getOpposite(), t.jenis(), false);
	}

	/** The eight directions round a titik (x, z). */
	private static final int[][] SEKITAR = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

	private @Nullable Tujuan xz(ServerLevel level, Penduduk p, String text) {
		String[] parts = text.split(",");
		if (parts.length < 2) {
			return null;
		}
		try {
			int x = Integer.parseInt(parts[0].trim());
			int z = Integer.parseInt(parts[1].trim());
			// a ring of watchers around the scene: the picked watchers spread evenly (C39.K11, at most 8), others by seed
			int seat = tontonAcara.isEmpty() ? -1 : tonton.getOrDefault(p.id(), -1);
			double offset = (PendudukGen.mixSeed(x * 31L + z) & 0xFFFF) / 65536.0D * Math.PI * 2.0D;
			double a = seat >= 0 ? offset + seat * (Math.PI * 2.0D / HidupApi.TONTON_MAKS) : (p.benih() & 0xFFFF) / 65536.0D * Math.PI * 2.0D;
			double r = seat >= 0 ? 8.0D + (seat % 2) * 1.5D : 5.0D + (p.id() % 4);
			int wx = x + (int) Math.round(Math.cos(a) * r);
			int wz = z + (int) Math.round(Math.sin(a) * r);
			BlockPos ground = TempatApi.groundSpot(level, wx, wz, rumahPos(p).getY());
			if (ground == null) {
				return null;
			}
			Direction face = Direction.fromYRot(Math.toDegrees(Math.atan2(-(x - wx), z - wz)));
			return new Tujuan(ground, face, "tonton", false);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** The fallback titik type when a place has none of {@code type} (old towns, small posts). */
	private static String cadangan(NpcRole peran, String type) {
		return switch (peran.kelompok) {
			case PEDAGANG -> type.equals("lapak") ? "obrol" : "lapak";
			case PENJAGA -> type.equals("jaga") ? "pintu" : "jaga";
			case KULTIVATOR -> type.equals("latihan") ? "meditasi" : "latihan";
			case CENDEKIA, RAHIB, PEJABAT -> type.equals("meja_tulis") ? "kursi" : "meja_tulis";
			case ANAK -> "main";
			default -> type.startsWith("kerja:") ? "obrol" : type.equals("obrol") ? "kursi" : "obrol";
		};
	}

	// ================================================================== the schedule as this place lives it

	private static final Kegiatan TUTUP_MALAM = new Kegiatan("tutup_kios", Kegiatan.RUMAH, GerakId.ANGKAT_BAWA, 0, PropId.PETI, 0, 0,
			GerakId.JALAN_BAWA, false);

	/**
	 * What {@code p} does at overworld time {@code waktu} (game time {@code now}): {@link Jadwal#kegiatan} with the
	 * place's weather and event, filtered by the place itself:
	 * <ul>
	 *   <li>a crowd override {@code tonton:<x,z>} (39F feud, C39.K11) draws only the {@link HidupApi#TONTON_MAKS} nearest
	 *       free residents; the rest keep their day;</li>
	 *   <li>after the night drum the night market packs up early ({@link #tutupSampai}).</li>
	 * </ul>
	 */
	public Kegiatan kegiatan(Penduduk p, long waktu, long now) {
		String acara = Pemanggil.acara(this, now);
		if (acara != null && acara.startsWith("tonton:") && kursiTonton(p, acara) < 0) {
			acara = null;
		}
		Kegiatan k = Jadwal.kegiatan(p, jenis, waktu, cuaca, acara);
		if (acara == null && keadaan.getOrDefault(p.id(), PendudukData.SEHAT) == PendudukData.SEHAT && !p.tamu()) {
			// markets, taverns and streets full of people living their day (Jadwal.ramai)
			Kegiatan ramai = Jadwal.ramai(p, Jadwal.saat(p, jenis, waktu), k, cuaca, adaPasar(), adaKedai());
			if (ramai != null) {
				k = ramai;
			}
		}
		if (now < tutupSampai && p.peran().kelompok == NpcRole.Kelompok.PEDAGANG && (k.kunci().equals("pasar_malam") || k.kunci().equals("pasar_bulan"))) {
			return TUTUP_MALAM;
		}
		int state = keadaan.getOrDefault(p.id(), PendudukData.SEHAT);
		if (state == PendudukData.SEHAT || k.dalam() || p.peran().kelompok == NpcRole.Kelompok.KHUSUS) {
			return k;
		}
		// mourners keep to their grief through any event; the hurt still come to watch a scene; a newcomer joins events
		boolean tonton = acara != null && acara.startsWith("tonton:");
		boolean ikut = switch (state) {
			case PendudukData.DUKA -> true;
			case PendudukData.PULIH -> !tonton;
			default -> acara == null;
		};
		return ikut ? hidupnya(p, k, state, Jadwal.saat(p, jenis, waktu)) : k;
	}

	/** The life state of resident {@code id} ({@link PendudukData#SEHAT} when nothing special). */
	public int keadaanDari(int id) {
		return keadaan.getOrDefault(id, PendudukData.SEHAT);
	}

	/** Meals, the bed and the walk home stay; the rest of the day bends to the resident's state. */
	private static boolean pokok(Kegiatan k) {
		return switch (k.kunci()) {
			case "makan", "basuh_muka", "pulang", "tutup_kios", "berlindung", "pergi" -> true;
			default -> false;
		};
	}

	/**
	 * The day of a resident in a life state (detail of 38.N.1/38.N.4):
	 * <ul>
	 *   <li>{@link PendudukData#PULIH}: limps everywhere, an arm in a sling, sits on its porch or a near bench instead of
	 *       working; children too;</li>
	 *   <li>{@link PendudukData#DUKA}: mourns a housemate at home with tears or joined palms, and burns incense at the
	 *       place's altar at dusk; meals still happen, slowly;</li>
	 *   <li>{@link PendudukData#DATANG}: the newcomer carries its bundle home and unpacks in the morning, walks the
	 *       streets looking at everything in the afternoon.</li>
	 * </ul>
	 */
	private static Kegiatan hidupnya(Penduduk p, Kegiatan k, int state, Jadwal.Saat s) {
		int h = s.jam();
		return switch (state) {
			case PendudukData.PULIH -> {
				if (pokok(k)) {
					yield k.berjalan(GerakId.PINCANG);
				}
				yield (h + (int) (p.benih() & 1L)) % 3 == 0
						? new Kegiatan("pulih", "kursi", GerakId.LENGAN_DIGENDONG, 0, 0, 0, 0, GerakId.PINCANG, false)
						: new Kegiatan("pulih", Kegiatan.RUMAH, GerakId.LENGAN_DIGENDONG, 0, 0, 0, 0, GerakId.PINCANG, false);
			}
			case PendudukData.DUKA -> {
				if (pokok(k)) {
					yield k.berjalan(GerakId.JALAN_TUA);
				}
				if (h >= 17 && h < 20) {
					yield new Kegiatan("berkabung", "dupa", GerakId.SUJUD_DUPA, 0, PropId.DUPA, 0, 0, GerakId.JALAN_TUA, false);
				}
				boolean tangis = Math.floorMod(p.benih() >>> 21, 3L) == 0L || p.peran().anak();
				yield new Kegiatan("berkabung", Kegiatan.RUMAH, tangis ? GerakId.MENANGIS : GerakId.SIKAP_DOA, 0, 0, 0, 0, GerakId.JALAN_TUA, false);
			}
			case PendudukData.DATANG -> {
				if (pokok(k) || h < 6 || h >= 18) {
					yield k;
				}
				if (h < 11) {
					yield new Kegiatan("datang", Kegiatan.RUMAH, GerakId.BERKEMAS, 0, 0, 0, PropId.KANTONG_QIANKUN, GerakId.JALAN_BAWA, false);
				}
				yield (h % 2 == 0)
						? new Kegiatan("lihat_kota", "obrol", GerakId.MEMANDANG, 0, 0, 0, 0, GerakId.JALAN_SANTAI, false)
						: new Kegiatan("lihat_kota", "lapak", GerakId.LIHAT_BARANG, 0, 0, 0, 0, GerakId.JALAN_SANTAI, false);
			}
			default -> k;
		};
	}

	/**
	 * The ring place of {@code p} for the crowd override {@code acara} ({@code tonton:<x,z>}), or -1 when it is not one of
	 * the watchers. The watchers are picked once per override: residents not on guard duty, not away, nearest the scene
	 * first (bodies by where they stand, the others by their cached goal or their home).
	 */
	int kursiTonton(Penduduk p, String acara) {
		if (!acara.equals(tontonAcara)) {
			pilihPenonton(acara);
		}
		Integer seat = tonton.get(p.id());
		return seat == null ? -1 : seat;
	}

	private void pilihPenonton(String acara) {
		tontonAcara = acara;
		tonton.clear();
		int[] xz = Jadwal.xz(acara.substring("tonton:".length()));
		if (xz == null) {
			return;
		}
		BlockPos scene = new BlockPos(xz[0], pusat().getY(), xz[1]);
		List<Penduduk> calon = new ArrayList<>();
		Map<Integer, Double> jarak = new HashMap<>();
		for (Penduduk p : penduduk) {
			NpcRole.Kelompok g = p.peran().kelompok;
			if (g == NpcRole.Kelompok.PENJAGA || g == NpcRole.Kelompok.KHUSUS || p.tamu()) {
				continue;
			}
			KultivatorNpc body = tubuh.get(p.id());
			BlockPos at;
			double bonus = 0.0D;
			if (body != null && !body.isRemoved()) {
				at = body.blockPosition();
				// a body already standing there is a watcher the player sees walk over
				bonus = -64.0D;
			} else {
				Tujuan t = Pemanggil.posisi(situsId, p.id());
				at = t != null ? t.pos() : rumahPos(p);
			}
			calon.add(p);
			jarak.put(p.id(), Math.sqrt(at.distSqr(scene)) + bonus);
		}
		calon.sort(Comparator.comparingDouble(p -> jarak.getOrDefault(p.id(), Double.MAX_VALUE)));
		for (int i = 0; i < calon.size() && i < HidupApi.TONTON_MAKS; i++) {
			tonton.put(calon.get(i).id(), i);
		}
	}

	// ================================================================== street nodes (L1 walk, 38.N.1 "Gerak")

	/** Links of the street-node graph: a path's next/previous node within this many blocks, and any node this near. */
	private static final double TAUT_JALUR = 24.0D;
	private static final double TAUT_DEKAT = 7.5D;
	private static final int TAUT_MAKS = 8;
	private static final int SIMPUL_MAKS = 2048;
	/** Search budget of one route (nodes expanded). */
	private static final int RUTE_MAKS = 512;

	private void bangunSimpul() {
		if (simpulDari == titikN) {
			return;
		}
		simpulDari = titikN;
		List<Titik> nodes = titik("jalan");
		int n = Math.min(nodes.size(), SIMPUL_MAKS);
		List<BlockPos> pos = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			pos.add(nodes.get(i).pos());
		}
		// spatial buckets of 24 blocks: each node only looks at the 3 x 3 buckets around it
		Map<Long, List<Integer>> sel = new HashMap<>();
		for (int i = 0; i < n; i++) {
			BlockPos p = pos.get(i);
			sel.computeIfAbsent(selKunci(Math.floorDiv(p.getX(), 24), Math.floorDiv(p.getZ(), 24)), k -> new ArrayList<>()).add(i);
		}
		int[][] links = new int[n][];
		List<int[]> found = new ArrayList<>();
		List<Integer> near = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Titik a = nodes.get(i);
			String jalurA = a.data().getOrDefault("jalur", "");
			int urutA = a.angka("urut", Integer.MIN_VALUE / 2);
			found.clear();
			near.clear();
			int cx = Math.floorDiv(a.pos().getX(), 24);
			int cz = Math.floorDiv(a.pos().getZ(), 24);
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					List<Integer> bucket = sel.get(selKunci(cx + ox, cz + oz));
					if (bucket != null) {
						near.addAll(bucket);
					}
				}
			}
			for (int j : near) {
				if (i == j) {
					continue;
				}
				Titik b = nodes.get(j);
				int dx = a.pos().getX() - b.pos().getX();
				int dz = a.pos().getZ() - b.pos().getZ();
				int dy = Math.abs(a.pos().getY() - b.pos().getY());
				if (Math.abs(dx) > TAUT_JALUR || Math.abs(dz) > TAUT_JALUR || dy > 4) {
					continue;
				}
				int d2 = dx * dx + dz * dz;
				boolean jalur = jalurA.equals(b.data().getOrDefault("jalur", "-")) && Math.abs(urutA - b.angka("urut", Integer.MAX_VALUE / 2)) == 1
						&& d2 <= TAUT_JALUR * TAUT_JALUR;
				boolean dekat = d2 <= TAUT_DEKAT * TAUT_DEKAT && dy <= 3;
				if (jalur || dekat) {
					found.add(new int[]{j, d2});
				}
			}
			found.sort(Comparator.comparingInt(e -> e[1]));
			int m = Math.min(found.size(), TAUT_MAKS);
			links[i] = new int[m];
			for (int k = 0; k < m; k++) {
				links[i][k] = found.get(k)[0];
			}
		}
		simpul = pos;
		tautan = links;
	}

	/**
	 * The street route from {@code dari} to {@code ke} as packed block positions ({@code BlockPos.asLong}), the goal not
	 * included: the nearest node to each end, then A* over the node graph (at most {@value #RUTE_MAKS} nodes looked at).
	 * Empty (walk straight) when the trip is short, the place has no street nodes near both ends, or no route joins them.
	 */
	long[] rute(BlockPos dari, BlockPos ke) {
		bangunSimpul();
		int n = simpul.size();
		if (n == 0 || dari.distSqr(ke) < 12.0D * 12.0D) {
			return new long[0];
		}
		int a = terdekatSimpul(dari, 16.0D);
		int b = terdekatSimpul(ke, 16.0D);
		if (a < 0 || b < 0 || a == b) {
			return new long[0];
		}
		double[] g = new double[n];
		int[] dariMana = new int[n];
		Arrays.fill(g, Double.MAX_VALUE);
		Arrays.fill(dariMana, -1);
		PriorityQueue<double[]> open = new PriorityQueue<>(Comparator.comparingDouble(e -> e[0]));
		g[a] = 0.0D;
		open.add(new double[]{Math.sqrt(simpul.get(a).distSqr(simpul.get(b))), a});
		int expanded = 0;
		boolean found = false;
		while (!open.isEmpty() && expanded < RUTE_MAKS) {
			int i = (int) open.poll()[1];
			if (i == b) {
				found = true;
				break;
			}
			expanded++;
			for (int j : tautan[i]) {
				double cost = g[i] + Math.sqrt(simpul.get(i).distSqr(simpul.get(j)));
				if (cost < g[j]) {
					g[j] = cost;
					dariMana[j] = i;
					open.add(new double[]{cost + Math.sqrt(simpul.get(j).distSqr(simpul.get(b))), j});
				}
			}
		}
		if (!found) {
			return new long[0];
		}
		List<Long> back = new ArrayList<>();
		for (int i = b; i >= 0 && back.size() <= n; i = dariMana[i]) {
			back.add(simpul.get(i).asLong());
			if (i == a) {
				break;
			}
		}
		long[] out = new long[back.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = back.get(back.size() - 1 - i);
		}
		return out;
	}

	private static long selKunci(int x, int z) {
		return (long) x << 32 | (z & 0xFFFFFFFFL);
	}

	private int terdekatSimpul(BlockPos at, double max) {
		int best = -1;
		double bestD = max * max;
		for (int i = 0; i < simpul.size(); i++) {
			BlockPos p = simpul.get(i);
			if (Math.abs(p.getY() - at.getY()) > 6) {
				continue;
			}
			double d = p.distSqr(at);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	/** Street nodes known (commands). */
	public int jumlahSimpul() {
		bangunSimpul();
		return simpul.size();
	}

	// ================================================================== locks

	/** Locks {@code pos} for resident {@code id} (releasing its previous lock). */
	void kunci(int id, BlockPos pos) {
		lepas(id);
		kunci.put(pos.asLong(), id);
	}

	/** Releases every lock of resident {@code id}. */
	void lepas(int id) {
		kunci.values().removeIf(owner -> owner == id);
	}

	/** Bodies alive now (commands, snapshot). */
	public int jumlahTubuh() {
		tubuh.values().removeIf(npc -> npc.isRemoved());
		return tubuh.size();
	}
}
