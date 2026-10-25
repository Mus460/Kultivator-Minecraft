package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Rencana;
import dev.kultivator.dunia.tempat.RencanaRegistry;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The zone map of one active place (DESIGN 42.4A.1), built from its titik and rebuilt whenever the titik count changes
 * (chunks loading, a finished kavling of 42F, a player's house vetoed by 42C).
 * <ul>
 *   <li><b>District</b>: for a planned place (capital: 11, themed city: 9, sect compound rows) the nearest district start of
 *       its {@link Rencana}; for the others a geometric split: {@code pusat} (the middle fifth) and the four compass
 *       sectors (a 34 town's main street, side streets and gate fall into them; a village's centre, houses, fields and
 *       water side become zone kinds).</li>
 *   <li><b>Unit</b> inside a district: a building ({@code bangunan} box; kind by its name, else by the titik inside it), a
 *       street segment ({@code jalan} nodes in a 32 x 32 cell, about +-16 blocks) or an open field (other titik in a 24 x 24
 *       cell, kind by the titik: square, market, field / water side, gate).</li>
 *   <li><b>Zone</b> = district x zone kind (the 12 kinds of the 42.4A.1 table); its capacity {@code K} is the sum of its
 *       units: one per seat, table, bed and work spot, two per stall and chat corner, three per play spot, five per chess
 *       board, four per well, twelve per stage; a street segment {@code length / 6}; a field {@code area / 25} when larger.</li>
 * </ul>
 * Memory only (the summary for commands lives in {@code RagamData.ZONA}).
 */
public final class PetaZona {

	public enum Bentuk {
		BANGUNAN, RUAS, LAPANGAN
	}

	/** One building, street segment or field. */
	public record Unit(int indeks, int zona, Bentuk bentuk, String nama, BoundingBox kotak, int kapasitas, List<Titik> titik) {
	}

	/** One zone: a district's share of one zone kind. */
	public record Zona(int indeks, String distrik, String jenis, BoundingBox kotak, BlockPos pusat, int kapasitas, int[] unit) {
		public String id() {
			return distrik + "/" + jenis;
		}
	}

	private static final Map<Long, PetaZona> CACHE = new HashMap<>();

	public final long situs;
	/** {@code TempatHuni.jumlahTitik()} the map was built from. */
	public final int dariTitik;
	private final List<Zona> zona;
	private final List<Unit> unit;
	private final Map<Long, Integer> unitTitik;
	private final Map<Long, Integer> sel;
	private final Map<Long, List<Integer>> selBangunan;
	/** Seats of the taverns, tea houses and eating houses (near a tea kettle, or a stove with four seats round it). */
	public final List<Titik> kursiKedai;
	/** Market stalls ({@code lapak}, {@code kerja:kios}). */
	public final List<Titik> lapak;

	private PetaZona(long situs, int dariTitik, List<Zona> zona, List<Unit> unit, Map<Long, Integer> unitTitik, Map<Long, Integer> sel,
			Map<Long, List<Integer>> selBangunan, List<Titik> kursiKedai, List<Titik> lapak) {
		this.situs = situs;
		this.dariTitik = dariTitik;
		this.zona = zona;
		this.unit = unit;
		this.unitTitik = unitTitik;
		this.sel = sel;
		this.selBangunan = selBangunan;
		this.kursiKedai = kursiKedai;
		this.lapak = lapak;
	}

	/** The zone map of {@code huni}, rebuilt when its titik changed. */
	public static PetaZona untuk(ServerLevel level, TempatHuni huni) {
		PetaZona peta = CACHE.get(huni.situsId);
		if (peta == null || peta.dariTitik != huni.jumlahTitik()) {
			peta = bangun(level, huni);
			CACHE.put(huni.situsId, peta);
			RagamData.simpanZona(level.getServer(), peta);
		}
		return peta;
	}

	/** The cached map of a place, or null. */
	public static @Nullable PetaZona ada(long situs) {
		return CACHE.get(situs);
	}

	static void lupakan(long situs) {
		CACHE.remove(situs);
	}

	static void lupakanSemua() {
		CACHE.clear();
	}

	public List<Zona> semua() {
		return zona;
	}

	public int jumlah() {
		return zona.size();
	}

	public Zona zona(int i) {
		return zona.get(i);
	}

	public Unit unit(int i) {
		return unit.get(i);
	}

	public int jumlahUnit() {
		return unit.size();
	}

	/** The unit of the titik at {@code pos}, or -1. */
	public int unitTitik(BlockPos pos) {
		return unitTitik.getOrDefault(pos.asLong(), -1);
	}

	/** True when {@code pos} is a titik of this place. */
	public boolean titikDi(BlockPos pos) {
		return unitTitik.containsKey(pos.asLong());
	}

	/** The zone of any block: its titik's unit, the smallest building holding it, the cell vote, a neighbour cell, the nearest zone. */
	public int zonaDi(BlockPos pos) {
		if (zona.isEmpty()) {
			return -1;
		}
		Integer u = unitTitik.get(pos.asLong());
		if (u != null) {
			return unit.get(u).zona();
		}
		int b = bangunanDi(pos);
		if (b >= 0) {
			return unit.get(b).zona();
		}
		int cx = Math.floorDiv(pos.getX(), 16);
		int cz = Math.floorDiv(pos.getZ(), 16);
		Integer z = sel.get(kunci(cx, cz));
		if (z != null) {
			return z;
		}
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				z = sel.get(kunci(cx + ox, cz + oz));
				if (z != null) {
					return z;
				}
			}
		}
		int best = 0;
		double bestD = Double.MAX_VALUE;
		for (Zona each : zona) {
			double d = each.pusat().distSqr(pos);
			if (d < bestD) {
				bestD = d;
				best = each.indeks();
			}
		}
		return best;
	}

	/** The unit of {@code pos}: its titik's unit or the smallest building holding it; -1 otherwise. */
	public int unitDi(BlockPos pos) {
		Integer u = unitTitik.get(pos.asLong());
		return u != null ? u : bangunanDi(pos);
	}

	/** The zone of a 16 x 16 cell (heat map), or -1 when the cell has no titik and no building. */
	public int zonaSel(int cx, int cz) {
		return sel.getOrDefault(kunci(cx, cz), -1);
	}

	private int bangunanDi(BlockPos pos) {
		List<Integer> list = selBangunan.get(kunci(Math.floorDiv(pos.getX(), 32), Math.floorDiv(pos.getZ(), 32)));
		if (list == null) {
			return -1;
		}
		for (int b : list) {
			if (unit.get(b).kotak().isInside(pos)) {
				return b;
			}
		}
		return -1;
	}

	private static long kunci(int x, int z) {
		return (long) x << 32 | (z & 0xFFFFFFFFL);
	}

	// ================================================================== build

	private record Bakal(Bentuk bentuk, String nama, String jenis, BoundingBox kotak, List<Titik> titik) {
	}

	static PetaZona bangun(ServerLevel level, TempatHuni huni) {
		Map<String, Integer> jenisTitik = huni.ringkasTitik();
		List<Titik> semua = new ArrayList<>();
		for (String type : jenisTitik.keySet()) {
			semua.addAll(huni.titik(type));
		}
		// buildings, smallest first so a room inside a courtyard house wins
		List<Titik> gedung = new ArrayList<>(huni.titik("bangunan"));
		gedung.sort(Comparator.comparingLong(t -> volume(t.kotak())));
		List<Bakal> bakal = new ArrayList<>();
		Map<Long, List<Integer>> gridGedung = new HashMap<>();
		for (Titik g : gedung) {
			int i = bakal.size();
			BoundingBox box = g.kotak();
			bakal.add(new Bakal(Bentuk.BANGUNAN, g.data().getOrDefault("nama", ""), "", box, new ArrayList<>()));
			for (int x = Math.floorDiv(box.minX(), 32); x <= Math.floorDiv(box.maxX(), 32); x++) {
				for (int z = Math.floorDiv(box.minZ(), 32); z <= Math.floorDiv(box.maxZ(), 32); z++) {
					gridGedung.computeIfAbsent(kunci(x, z), k -> new ArrayList<>()).add(i);
				}
			}
		}
		Map<String, List<Titik>> ruas = new TreeMap<>();
		Map<String, List<Titik>> lapangan = new TreeMap<>();
		for (Titik t : semua) {
			String type = t.jenis();
			if (type.equals("bangunan") || type.equals("pintu") || type.equals("kanal") || type.equals("zona_perjanjian")) {
				continue;
			}
			int in = -1;
			List<Integer> near = gridGedung.get(kunci(Math.floorDiv(t.pos().getX(), 32), Math.floorDiv(t.pos().getZ(), 32)));
			if (near != null) {
				for (int b : near) {
					if (bakal.get(b).kotak().isInside(t.pos())) {
						in = b;
						break;
					}
				}
			}
			if (in >= 0 && !type.equals("jalan")) {
				bakal.get(in).titik().add(t);
				continue;
			}
			String kind = type.equals("jalan") ? "ruas" : Kapasitas.jenisTitik(type);
			if (kind == null) {
				continue;
			}
			if (kind.equals("ruas")) {
				ruas.computeIfAbsent(Math.floorDiv(t.pos().getX(), 32) + "," + Math.floorDiv(t.pos().getZ(), 32), k -> new ArrayList<>()).add(t);
			} else {
				lapangan.computeIfAbsent(Math.floorDiv(t.pos().getX(), 24) + "," + Math.floorDiv(t.pos().getZ(), 24) + "," + kind, k -> new ArrayList<>()).add(t);
			}
		}
		List<Bakal> semuaBakal = new ArrayList<>();
		for (Bakal b : bakal) {
			semuaBakal.add(new Bakal(b.bentuk(), b.nama(), jenisGedung(b), b.kotak(), b.titik()));
		}
		for (Map.Entry<String, List<Titik>> e : ruas.entrySet()) {
			semuaBakal.add(new Bakal(Bentuk.RUAS, "ruas_" + e.getKey(), "ruas", kotak(e.getValue()), e.getValue()));
		}
		for (Map.Entry<String, List<Titik>> e : lapangan.entrySet()) {
			String kind = e.getKey().substring(e.getKey().lastIndexOf(',') + 1);
			semuaBakal.add(new Bakal(Bentuk.LAPANGAN, "lapangan_" + e.getKey(), kind, kotak(e.getValue()), e.getValue()));
		}
		// districts
		List<Distrik> distrik = distrik(level, huni);
		BlockPos tengah = huni.kotak.getCenter();
		double setengah = Math.max(huni.kotak.getXSpan(), huni.kotak.getZSpan()) / 2.0D;
		Map<String, List<Integer>> perZona = new LinkedHashMap<>();
		List<String> distrikBakal = new ArrayList<>();
		for (int i = 0; i < semuaBakal.size(); i++) {
			Bakal b = semuaBakal.get(i);
			BlockPos c = b.kotak().getCenter();
			String d = namaDistrik(distrik, tengah, setengah, c);
			distrikBakal.add(d);
			perZona.computeIfAbsent(d + "/" + b.jenis(), k -> new ArrayList<>()).add(i);
		}
		List<Zona> zona = new ArrayList<>();
		List<Unit> unit = new ArrayList<>();
		Map<Long, Integer> unitTitik = new HashMap<>();
		Map<Long, List<Integer>> selBangunan = new HashMap<>();
		Map<Long, Map<Integer, Integer>> suara = new HashMap<>();
		for (Map.Entry<String, List<Integer>> e : perZona.entrySet()) {
			int zi = zona.size();
			int[] units = new int[e.getValue().size()];
			int kap = 0;
			BoundingBox box = null;
			long ySum = 0L;
			int yN = 0;
			for (int k = 0; k < units.length; k++) {
				Bakal b = semuaBakal.get(e.getValue().get(k));
				int ui = unit.size();
				int ku = kapasitas(b);
				unit.add(new Unit(ui, zi, b.bentuk(), b.nama(), b.kotak(), ku, List.copyOf(b.titik())));
				units[k] = ui;
				kap += ku;
				box = box == null ? b.kotak() : gabung(box, b.kotak());
				for (Titik t : b.titik()) {
					unitTitik.putIfAbsent(t.pos().asLong(), ui);
					ySum += t.pos().getY();
					yN++;
					suara.computeIfAbsent(kunci(Math.floorDiv(t.pos().getX(), 16), Math.floorDiv(t.pos().getZ(), 16)), x -> new HashMap<>())
							.merge(zi, 1, Integer::sum);
				}
				if (b.bentuk() == Bentuk.BANGUNAN) {
					BoundingBox bb = b.kotak();
					for (int x = Math.floorDiv(bb.minX(), 32); x <= Math.floorDiv(bb.maxX(), 32); x++) {
						for (int z = Math.floorDiv(bb.minZ(), 32); z <= Math.floorDiv(bb.maxZ(), 32); z++) {
							selBangunan.computeIfAbsent(kunci(x, z), q -> new ArrayList<>()).add(ui);
						}
					}
					for (int x = Math.floorDiv(bb.minX(), 16); x <= Math.floorDiv(bb.maxX(), 16); x++) {
						for (int z = Math.floorDiv(bb.minZ(), 16); z <= Math.floorDiv(bb.maxZ(), 16); z++) {
							suara.computeIfAbsent(kunci(x, z), q -> new HashMap<>()).merge(zi, 2, Integer::sum);
						}
					}
				}
			}
			String[] dj = e.getKey().split("/", 2);
			BoundingBox z0 = box == null ? huni.kotak : box;
			BlockPos c = z0.getCenter();
			int y = yN > 0 ? (int) (ySum / yN) : c.getY();
			zona.add(new Zona(zi, dj[0], dj[1], z0, new BlockPos(c.getX(), y, c.getZ()), Math.max(1, kap), units));
		}
		// buildings were added in size order inside each zone; keep the smallest first per grid cell
		for (List<Integer> list : selBangunan.values()) {
			list.sort(Comparator.comparingLong(i -> volume(unit.get(i).kotak())));
		}
		Map<Long, Integer> sel = new HashMap<>();
		for (Map.Entry<Long, Map<Integer, Integer>> e : suara.entrySet()) {
			int best = -1;
			int bestN = -1;
			for (Map.Entry<Integer, Integer> v : e.getValue().entrySet()) {
				if (v.getValue() > bestN || v.getValue() == bestN && v.getKey() < best) {
					best = v.getKey();
					bestN = v.getValue();
				}
			}
			sel.put(e.getKey(), best);
		}
		List<Titik> stalls = new ArrayList<>(huni.titik("lapak"));
		stalls.addAll(huni.titik("kerja:kios"));
		return new PetaZona(huni.situsId, huni.jumlahTitik(), List.copyOf(zona), List.copyOf(unit), unitTitik, sel, selBangunan,
				kursiKedai(huni), List.copyOf(stalls));
	}

	private static long volume(BoundingBox b) {
		return (long) b.getXSpan() * b.getYSpan() * b.getZSpan();
	}

	private static BoundingBox gabung(BoundingBox a, BoundingBox b) {
		return new BoundingBox(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()), Math.max(a.maxX(), b.maxX()),
				Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
	}

	private static BoundingBox kotak(List<Titik> list) {
		BoundingBox box = null;
		for (Titik t : list) {
			BoundingBox one = new BoundingBox(t.pos());
			box = box == null ? one : gabung(box, one);
		}
		return box == null ? new BoundingBox(BlockPos.ZERO) : box;
	}

	/** A building's zone kind: its name, else the titik inside it by vote (beds count half), else a home. */
	private static String jenisGedung(Bakal b) {
		String byName = Kapasitas.jenisNama(b.nama());
		if (byName != null) {
			return byName;
		}
		Map<String, Double> vote = new HashMap<>();
		int seats = 0;
		boolean teko = false;
		boolean kuali = false;
		for (Titik t : b.titik()) {
			String kind = Kapasitas.jenisTitik(t.jenis());
			if (t.jenis().equals("kursi") || t.jenis().equals("meja_makan")) {
				seats++;
			}
			teko |= t.jenis().equals("kerja:teko");
			kuali |= t.jenis().equals("kerja:kuali");
			if (kind == null || kind.equals("alun") || kind.equals("ruas")) {
				continue;
			}
			vote.merge(kind, kind.equals("rumah") ? 0.5D : 1.0D, Double::sum);
		}
		if (teko) {
			return "teh";
		}
		if (kuali && seats >= 4) {
			return "makan";
		}
		String best = "rumah";
		double bestV = 0.0D;
		for (Map.Entry<String, Double> e : vote.entrySet()) {
			if (e.getValue() > bestV || e.getValue() == bestV && e.getKey().compareTo(best) < 0) {
				best = e.getKey();
				bestV = e.getValue();
			}
		}
		return best;
	}

	private static int kapasitas(Bakal b) {
		Kapasitas.Aturan a = Kapasitas.aturan();
		int sum = 0;
		for (Titik t : b.titik()) {
			sum += Kapasitas.kapasitasTitik(t.jenis());
		}
		return switch (b.bentuk()) {
			case BANGUNAN -> Math.max(1, sum > 0 ? sum : (int) (b.kotak().getXSpan() * (long) b.kotak().getZSpan() / (a.luasPerOrang() * 2.0D)));
			case RUAS -> Math.max(1, sum + (int) Math.round(panjang(b.titik()) / a.ruasPerPejalan()));
			case LAPANGAN -> Math.max(1, Math.max(sum, (int) (b.kotak().getXSpan() * (long) b.kotak().getZSpan() / a.luasPerOrang())));
		};
	}

	/** Length of a street segment: each node adds its distance to the nearest other node (at least 6, at most 16 blocks). */
	private static double panjang(List<Titik> nodes) {
		double sum = 0.0D;
		for (Titik a : nodes) {
			if (!a.jenis().equals("jalan")) {
				continue;
			}
			double best = 16.0D * 16.0D;
			for (Titik b : nodes) {
				if (a != b && b.jenis().equals("jalan")) {
					best = Math.min(best, a.pos().distSqr(b.pos()));
				}
			}
			sum += Math.max(6.0D, Math.sqrt(best));
		}
		return sum;
	}

	/** The districts of a planned place (nearest-start split), empty for the others. */
	private static List<Distrik> distrik(ServerLevel level, TempatHuni huni) {
		long seed = level.getSeed();
		try {
			for (String id : RencanaRegistry.ids()) {
				String jenis = TempatApi.jenisRencana(id);
				if (!jenis.equals(huni.jenis)) {
					continue;
				}
				Rencana plan = RencanaRegistry.get(id);
				for (Kandidat k : plan.kandidat(seed)) {
					if (TempatApi.situsRencana(k, jenis) != huni.situsId && !huni.kotak.isInside(new BlockPos(k.pusatX(), huni.kotak.getCenter().getY(), k.pusatZ()))) {
						continue;
					}
					List<Distrik> out = new ArrayList<>();
					for (Distrik d : plan.semuaDistrik(seed)) {
						if (d.kandidat().equals(k)) {
							out.add(d);
						}
					}
					return out;
				}
			}
		} catch (RuntimeException e) {
			// a plan that cannot answer: the geometric split
		}
		return List.of();
	}

	private static String namaDistrik(List<Distrik> distrik, BlockPos tengah, double setengah, BlockPos at) {
		if (!distrik.isEmpty()) {
			Distrik best = distrik.getFirst();
			long bestD = Long.MAX_VALUE;
			for (Distrik d : distrik) {
				long dx = d.pusatX() - at.getX();
				long dz = d.pusatZ() - at.getZ();
				long dd = dx * dx + dz * dz;
				if (dd < bestD) {
					bestD = dd;
					best = d;
				}
			}
			return best.id();
		}
		double dx = at.getX() - tengah.getX();
		double dz = at.getZ() - tengah.getZ();
		if (Math.sqrt(dx * dx + dz * dz) <= Math.max(16.0D, setengah * 0.22D)) {
			return "pusat";
		}
		// north = -z
		double sudut = Math.toDegrees(Math.atan2(dx, -dz));
		if (sudut >= -45.0D && sudut < 45.0D) {
			return "utara";
		}
		if (sudut >= 45.0D && sudut < 135.0D) {
			return "timur";
		}
		if (sudut >= -135.0D && sudut < -45.0D) {
			return "barat";
		}
		return "selatan";
	}

	/** Seat titik within 10 blocks of a tea kettle, or of a stove with four seats round it (the 38NL tavern rule). */
	private static List<Titik> kursiKedai(TempatHuni huni) {
		List<Titik> seats = new ArrayList<>();
		for (String type : List.of("kursi", "meja_makan", "kursi_lantai")) {
			seats.addAll(huni.titik(type));
		}
		List<Titik> hearths = new ArrayList<>(huni.titik("kerja:teko"));
		for (Titik stove : huni.titik("kerja:kuali")) {
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
		return List.copyOf(out);
	}

	private static boolean dekatKedai(Titik a, Titik b) {
		int dx = a.pos().getX() - b.pos().getX();
		int dz = a.pos().getZ() - b.pos().getZ();
		return dx * dx + dz * dz <= 100 && Math.abs(a.pos().getY() - b.pos().getY()) <= 4;
	}
}
