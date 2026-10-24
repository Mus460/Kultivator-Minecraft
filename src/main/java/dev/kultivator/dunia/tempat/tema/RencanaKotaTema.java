package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.Rencana;
import dev.kultivator.dunia.tempat.RencanaKotaBesar;
import dev.kultivator.dunia.tempat.RencanaRegistry;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.dunia.tempat.ZonaRencana;
import dev.kultivator.dunia.tempat.jalan.RencanaJalan;
import dev.kultivator.dunia.tempat.jalan.SimpulJalan;
import dev.kultivator.dunia.tempat.worldgen.TataLetakStructure;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The themed-town plans {@code kota_tema_<tema>} (DESIGN 38.T.2.1; owner 38TK inti). One instance per {@link Tema},
 * registered in {@code RencanaRegistry} by {@link KotaTema#init()}; all five share one per-seed plan because their
 * spacing rule crosses themes.
 * <p>
 * <b>Base candidates</b> ({@link Calon}) per theme, in evaluation order:
 * <ol>
 * <li>the first ring: {@code Tema.kandidatCincin()} candidates at angle {@code turn(tema) + i x 360 / n}, radius
 * {@code cincinMin + hash x cincinLebar} (1400 + 2800; iblis 3500 + 2500); the first one whose final verdict is
 * suitable is the guaranteed first town of the theme, the others of the ring fall;</li>
 * <li>far cells: a grid of {@code tema<Tema>.ukuranSel} shifted per theme by {@code hash mod ukuranSel}, one candidate at a
 * hashed point in the middle 60 % of each cell, alive with chance {@code tema<Tema>.peluangSel}, cells in spiral order out
 * to about 26 000 blocks.</li>
 * </ol>
 * Pure-seed spacing: 700 blocks from every NPC sect gate ({@link GerbangPlan}) and 400 from every sky palace
 * ({@link LangitNpcPlan}); a colliding candidate moves 240 blocks away (at most three times) or is dropped. Verdict
 * spacing ({@link #akhir}): 1200 ({@code kotaTema.jarakDariIbuKota}) from every suitable capital and 900
 * ({@code kotaTema.jarakAntarTema}) from every earlier suitable themed town; the global order is capitals, then themes
 * by {@link Tema#prioritas()} (iblis, gunung, pelabuhan, oasis, air), then ring before cells; the earlier one wins.
 * <p>
 * <b>Rotation variants.</b> Oasis, air and iblis turn by {@code hash & 3}. Pelabuhan (sea side to grid north) and gunung
 * (uphill to grid north) turn by the terrain, which a placement cannot see; so each base candidate is listed as four
 * {@link Kandidat} variants ({@code index = base x 4 + putar}) with the SAME centre (same site id and town id) whose
 * districts are shifted by {@code ((putar & 1) x 16, (putar >> 1) x 16)} so their start chunks differ. The verdict makes
 * only the variant whose rotation matches the terrain suitable; the others never generate, protect or connect a road.
 * <p>
 * Districts: 3 x 3 cells of {@link Tema#SEL_DISTRIK} 96 (oasis adds the caravan field at grid z = +2), each piece within
 * +-{@link #SETENGAH_DISTRIK} of its district centre; gunung rows have {@code Distrik.dy} {@link #DY_GUNUNG}. Safe ground
 * ({@link #zonaAman}): every district of the four peaceful themes; of the Kota Iblis only its Zona Perjanjian districts
 * {@code pasar_gelap} and {@code kedai_racun} (38.T.7.8).
 */
public final class RencanaKotaTema implements Rencana {

	/** Half extent of one district's pieces (cell 96 / 2 + wall 8 + tower 8 + 8). */
	public static final int SETENGAH_DISTRIK = 72;
	/** Keep-out margin around the districts (3 chunks, 38.D.4). */
	public static final int MARGIN = 48;
	/** Extra floor height of the gunung district rows, indexed by grid z + 1 (top -1: 36, middle 0: 18, bottom +1: 0). */
	public static final int[] DY_GUNUNG = {36, 18, 0};
	/** Least distance to an NPC sect gate and to a sky palace (blocks). */
	public static final int JAUH_GERBANG = 700;
	public static final int JAUH_LANGIT = 400;
	/** One shift step away from an offender (blocks) and the most shifts. */
	public static final int GESER = 240;
	public static final int GESER_MAKS = 3;
	/** Far cells reach about this far from the world centre (blocks). */
	public static final int JANGKAUAN = 26000;
	/** Road ends (before rotation, from the town centre): 12 blocks outside the south gate. */
	private static final int GERBANG_Z = Tema.SEL_DISTRIK + Tema.SEL_DISTRIK / 2 + 8 + 12;
	private static final int GERBANG_Z_OASIS = 2 * Tema.SEL_DISTRIK + Tema.SEL_DISTRIK / 2 + 8 + 12;

	/**
	 * One base candidate of a theme (before rotation variants).
	 *
	 * @param tema    the theme
	 * @param index   index inside the theme (ring first)
	 * @param x       block x of the centre
	 * @param z       block z of the centre
	 * @param putar   the seed rotation (used by themes without terrain rotation)
	 * @param pertama first-ring candidate
	 * @param urut    position in the global evaluation order
	 */
	public record Calon(Tema tema, int index, int x, int z, int putar, boolean pertama, int urut) {
	}

	/** The plan of one seed (all themes). */
	public record Plan(long seed, Map<Tema, List<Calon>> calon, Map<Tema, List<Kandidat>> kandidat, Map<Tema, List<Distrik>> distrik,
			Map<Tema, Map<Long, Distrik>> mulai, Map<Long, int[]> tetangga, List<Calon> urutan) {

		public @Nullable Calon calon(Tema tema, int index) {
			List<Calon> list = calon.get(tema);
			return index >= 0 && index < list.size() ? list.get(index) : null;
		}
	}

	private static final Map<Tema, RencanaKotaTema> INSTANCES = new EnumMap<>(Tema.class);
	private static volatile @Nullable Plan cached;
	private static volatile long akhirSeed = Long.MIN_VALUE;
	private static final Map<Long, Putusan> AKHIR = new ConcurrentHashMap<>();

	private final Tema tema;

	private RencanaKotaTema(Tema tema) {
		this.tema = tema;
	}

	/** The plan instance of {@code tema}. */
	public static synchronized RencanaKotaTema of(Tema tema) {
		return INSTANCES.computeIfAbsent(tema, RencanaKotaTema::new);
	}

	/** Registers the five plans (from {@link KotaTema#init()}). */
	static void daftar() {
		for (Tema t : Tema.values()) {
			RencanaRegistry.register(t.idRencana(), of(t));
		}
	}

	public Tema tema() {
		return tema;
	}

	// ================================================================================================ the plan

	/** The plan of {@code seed} (cached per seed). */
	public static Plan plan(long seed) {
		Plan p = cached;
		if (p != null && p.seed() == seed) {
			return p;
		}
		synchronized (RencanaKotaTema.class) {
			p = cached;
			if (p != null && p.seed() == seed) {
				return p;
			}
			p = build(seed);
			cached = p;
			return p;
		}
	}

	/** Forgets the plan and every cached verdict (config reload, seed change, {@code uji tema rencana segar}). */
	public static void lupakan() {
		synchronized (RencanaKotaTema.class) {
			cached = null;
			AKHIR.clear();
			akhirSeed = Long.MIN_VALUE;
		}
		PutusanTema.lupakan();
		RencanaPosPemburu.lupakan();
		try {
			RencanaJalan.lupakan();
			TempatZones.lupakanRencana();
		} catch (RuntimeException ignored) {
			// spine not initialised yet (unit use)
		}
	}

	private static Plan build(long seed) {
		List<int[]> offenders = offenders(seed);
		Map<Tema, List<int[]>> raw = new EnumMap<>(Tema.class);
		for (Tema t : Tema.values()) {
			raw.put(t, hitung(seed, t, offenders));
		}
		// global order: themes by priority, ring before cells (already in list order)
		List<Tema> order = new ArrayList<>(List.of(Tema.values()));
		order.sort(Comparator.comparingInt(Tema::prioritas));
		Map<Tema, List<Calon>> calon = new EnumMap<>(Tema.class);
		List<Calon> urutan = new ArrayList<>();
		for (Tema t : order) {
			List<Calon> list = new ArrayList<>();
			int i = 0;
			for (int[] c : raw.get(t)) {
				Calon k = new Calon(t, i, c[0], c[1], c[2], c[3] == 1, urutan.size());
				list.add(k);
				urutan.add(k);
				i++;
			}
			calon.put(t, List.copyOf(list));
		}
		// earlier neighbours within the themed spacing (by global order)
		Map<Long, int[]> tetangga = new HashMap<>();
		long limit = (long) TemaConfig.inti().jarakAntarTema * TemaConfig.inti().jarakAntarTema;
		for (int i = 0; i < urutan.size(); i++) {
			Calon k = urutan.get(i);
			List<Integer> near = new ArrayList<>();
			for (int j = 0; j < i; j++) {
				Calon o = urutan.get(j);
				long dx = o.x() - k.x();
				long dz = o.z() - k.z();
				if (dx * dx + dz * dz < limit) {
					near.add(j);
				}
			}
			tetangga.put(kunci(k.tema(), k.index()), near.stream().mapToInt(Integer::intValue).toArray());
		}
		Map<Tema, List<Kandidat>> kandidat = new EnumMap<>(Tema.class);
		Map<Tema, List<Distrik>> distrik = new EnumMap<>(Tema.class);
		Map<Tema, Map<Long, Distrik>> mulai = new EnumMap<>(Tema.class);
		for (Tema t : Tema.values()) {
			List<Kandidat> ks = new ArrayList<>();
			List<Distrik> ds = new ArrayList<>();
			Map<Long, Distrik> starts = new HashMap<>();
			for (Calon c : calon.get(t)) {
				for (Kandidat k : varian(c)) {
					ks.add(k);
					for (Distrik d : distrikOf(t, k)) {
						ds.add(d);
						starts.putIfAbsent(chunkKey(d.chunkX(), d.chunkZ()), d);
					}
				}
			}
			kandidat.put(t, List.copyOf(ks));
			distrik.put(t, List.copyOf(ds));
			mulai.put(t, Map.copyOf(starts));
		}
		return new Plan(seed, Map.copyOf(calon), Map.copyOf(kandidat), Map.copyOf(distrik), Map.copyOf(mulai), Map.copyOf(tetangga), List.copyOf(urutan));
	}

	/** Base candidates of one theme as {x, z, seedRotation, ring 1/0}. */
	private static List<int[]> hitung(long seed, Tema t, List<int[]> offenders) {
		List<int[]> out = new ArrayList<>();
		long salt = 0x7E3A0000L + t.ordinal() * 0x1F1FL;
		double turn = unit(seed, salt ^ 0x51A7L, 0, 0) * Math.PI * 2.0D;
		int n = t.kandidatCincin();
		for (int i = 0; i < n; i++) {
			double angle = turn + Math.PI * 2.0D * i / n;
			double r = t.cincinMin() + unit(seed, salt ^ 0xC1C1L, i, 0) * t.cincinLebar();
			int[] pos = geser(offenders, (int) Math.round(Math.cos(angle) * r), (int) Math.round(Math.sin(angle) * r), seed, i);
			if (pos != null) {
				out.add(new int[]{pos[0], pos[1], (int) (hash(seed, salt ^ 0x9071L, i, 0) & 3), 1});
			}
		}
		int size = TemaConfig.ukuranSel(t);
		double chance = TemaConfig.peluangSel(t);
		int ofsX = (int) Math.floorMod(hash(seed, salt ^ 0x0F5EL, 1, 0), (long) size);
		int ofsZ = (int) Math.floorMod(hash(seed, salt ^ 0x0F5EL, 0, 1), (long) size);
		int range = Math.max(4, Math.round((float) JANGKAUAN / size));
		int ringEnd = t.cincinMin() + t.cincinLebar();
		List<int[]> cells = new ArrayList<>();
		cells.add(new int[]{0, 0});
		cells.addAll(RencanaKotaBesar.spiral(range));
		for (int[] cell : cells) {
			int cx = cell[0];
			int cz = cell[1];
			if (unit(seed, salt ^ 0xCE11L, cx, cz) >= chance) {
				continue;
			}
			int x = ofsX + cx * size + size / 2 + (int) Math.round((unit(seed, salt ^ 0xA0A0L, cx, cz) - 0.5D) * 0.6D * size);
			int z = ofsZ + cz * size + size / 2 + (int) Math.round((unit(seed, salt ^ 0xB0B0L, cx, cz) - 0.5D) * 0.6D * size);
			if (Math.hypot(x, z) < ringEnd) {
				continue;
			}
			int[] pos = geser(offenders, x, z, seed, 1000 + out.size());
			if (pos != null) {
				out.add(new int[]{pos[0], pos[1], (int) (hash(seed, salt ^ 0x9072L, cx, cz) & 3), 0});
			}
		}
		return out;
	}

	/** Sect gates (700) and sky palaces (400) as {x, z, limit}. */
	private static List<int[]> offenders(long seed) {
		List<int[]> out = new ArrayList<>();
		try {
			for (GerbangPlan.Entry e : GerbangPlan.of(seed).entries()) {
				out.add(new int[]{e.blockX(), e.blockZ(), JAUH_GERBANG});
			}
			for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(seed)) {
				out.add(new int[]{istana.column().getX(), istana.column().getZ(), JAUH_LANGIT});
			}
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Themed town plan could not read the sect gates", e);
		}
		return out;
	}

	private static int @Nullable [] geser(List<int[]> offenders, int x, int z, long seed, int salt) {
		for (int step = 0; step <= GESER_MAKS; step++) {
			int[] worst = null;
			double worstD = Double.MAX_VALUE;
			for (int[] o : offenders) {
				double over = Math.hypot(x - o[0], z - o[1]) - o[2];
				if (over < 0.0D && over < worstD) {
					worstD = over;
					worst = o;
				}
			}
			if (worst == null) {
				return new int[]{x, z};
			}
			if (step == GESER_MAKS) {
				return null;
			}
			double dx = x - worst[0];
			double dz = z - worst[1];
			double len = Math.hypot(dx, dz);
			if (len < 1.0D) {
				double a = unit(seed, 0x6E5EL, salt, step) * Math.PI * 2.0D;
				dx = Math.cos(a);
				dz = Math.sin(a);
				len = 1.0D;
			}
			x += (int) Math.round(dx / len * GESER);
			z += (int) Math.round(dz / len * GESER);
		}
		return null;
	}

	/** True for the themes whose rotation comes from the terrain (pelabuhan, gunung). */
	public static boolean putarMedan(Tema t) {
		return t == Tema.PELABUHAN || t == Tema.GUNUNG;
	}

	/** The rotation variants of a base candidate ({@code index = base x 4 + putar}). */
	public static List<Kandidat> varian(Calon c) {
		if (!putarMedan(c.tema())) {
			return List.of(new Kandidat(c.tema().idRencana(), c.index() * 4 + c.putar(), c.x(), c.z(), c.putar(), c.pertama()));
		}
		List<Kandidat> out = new ArrayList<>(4);
		for (int r = 0; r < 4; r++) {
			out.add(new Kandidat(c.tema().idRencana(), c.index() * 4 + r, c.x(), c.z(), r, c.pertama()));
		}
		return out;
	}

	/** The district-grid shift of a rotation variant: {x, z}. */
	public static int[] geserVarian(Tema t, int putar) {
		return putarMedan(t) ? new int[]{(putar & 1) * 16, (putar >> 1) * 16} : new int[]{0, 0};
	}

	/** World centre of the town of variant {@code k} (the central district's centre). */
	public static int[] pusat(Tema t, Kandidat k) {
		int[] o = geserVarian(t, k.putar());
		return new int[]{k.pusatX() + o[0], k.pusatZ() + o[1]};
	}

	/** World position of grid offset {@code (gx, gz)} blocks (before rotation) from the centre of variant {@code k}. */
	public static int[] dunia(Tema t, Kandidat k, int gx, int gz) {
		int[] c = pusat(t, k);
		int[] o = TataLetakStructure.rotate(Rotation.values()[k.putar()], gx, gz);
		return new int[]{c[0] + o[0], c[1] + o[1]};
	}

	/** The districts of variant {@code k}. */
	public static List<Distrik> distrikOf(Tema t, Kandidat k) {
		List<Distrik> out = new ArrayList<>(t.distrik().size());
		for (Tema.Distrik d : t.distrik()) {
			int[] w = dunia(t, k, d.x() * Tema.SEL_DISTRIK, d.z() * Tema.SEL_DISTRIK);
			int dy = t == Tema.GUNUNG ? DY_GUNUNG[Math.clamp(d.z() + 1, 0, 2)] : 0;
			out.add(new Distrik(k, d.id(), w[0], w[1], dy));
		}
		return out;
	}

	/** The base candidate of variant {@code k}. */
	public static @Nullable Calon calonOf(long seed, Kandidat k) {
		Tema t = Tema.dari(k.rencana().startsWith("kota_tema_") ? k.rencana().substring("kota_tema_".length()) : null);
		return t == null ? null : plan(seed).calon(t, k.index() >> 2);
	}

	/** The variant of base candidate {@code c} with rotation {@code putar} (or the seed rotation), or null. */
	public static @Nullable Kandidat kandidat(Calon c, int putar) {
		for (Kandidat k : varian(c)) {
			if (k.putar() == putar) {
				return k;
			}
		}
		return null;
	}

	/** The whole-town box (districts + walls, no margin) of variant {@code k}. */
	public static BoundingBox kotakKota(Tema t, Kandidat k) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (Distrik d : distrikOf(t, k)) {
			minX = Math.min(minX, d.pusatX() - SETENGAH_DISTRIK);
			minZ = Math.min(minZ, d.pusatZ() - SETENGAH_DISTRIK);
			maxX = Math.max(maxX, d.pusatX() + SETENGAH_DISTRIK);
			maxZ = Math.max(maxZ, d.pusatZ() + SETENGAH_DISTRIK);
		}
		return new BoundingBox(minX, -64, minZ, maxX, 320, maxZ);
	}

	/** The land-road end outside the main (south) gate of variant {@code k}. */
	public static int[] gerbangJalan(Tema t, Kandidat k) {
		return dunia(t, k, 0, t == Tema.OASIS ? GERBANG_Z_OASIS : GERBANG_Z);
	}

	// ================================================================================================ verdicts

	/** The raw terrain verdict of a base candidate (cached), switch respected. */
	public static PutusanTema.Hasil mentah(PutusanTanah.Sumber sumber, Calon c) {
		if (!TemaConfig.worldgen(c.tema())) {
			return new PutusanTema.Hasil(false, 0, -1, "mati", "");
		}
		return PutusanTema.of(sumber, c.tema(), c.index(), c.x(), c.z());
	}

	/** The final verdict of a base candidate: terrain, capitals, earlier themed towns, ring rule. Cached per seed. */
	public static Putusan akhirCalon(PutusanTanah.Sumber sumber, Calon c) {
		if (akhirSeed != sumber.seed()) {
			synchronized (RencanaKotaTema.class) {
				if (akhirSeed != sumber.seed()) {
					AKHIR.clear();
					akhirSeed = sumber.seed();
				}
			}
		}
		long key = kunci(c.tema(), c.index());
		Putusan known = AKHIR.get(key);
		if (known != null) {
			return known;
		}
		Putusan result = hitungAkhir(sumber, c);
		AKHIR.put(key, result);
		return result;
	}

	private static Putusan hitungAkhir(PutusanTanah.Sumber sumber, Calon c) {
		PutusanTema.Hasil raw = mentah(sumber, c);
		if (!raw.layak()) {
			return Putusan.tidak(raw.alasan());
		}
		long capLimit = (long) TemaConfig.inti().jarakDariIbuKota * TemaConfig.inti().jarakDariIbuKota;
		try {
			for (Kandidat kb : RencanaKotaBesar.of(sumber.seed()).kandidat()) {
				long dx = kb.pusatX() - c.x();
				long dz = kb.pusatZ() - c.z();
				if (dx * dx + dz * dz < capLimit && RencanaKotaBesar.akhir(sumber, kb).layak()) {
					return Putusan.tidak("ibu kota #" + kb.index());
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Themed town plan could not ask the capital plan", exception);
		}
		Plan plan = plan(sumber.seed());
		for (int j : plan.tetangga().getOrDefault(kunci(c.tema(), c.index()), new int[0])) {
			Calon o = plan.urutan().get(j);
			if (akhirCalon(sumber, o).layak()) {
				return Putusan.tidak("dekat " + o.tema().id() + " #" + o.index());
			}
		}
		if (c.pertama()) {
			for (Calon o : plan.calon().get(c.tema())) {
				if (!o.pertama() || o.index() >= c.index()) {
					break;
				}
				if (akhirCalon(sumber, o).layak()) {
					return Putusan.tidak("cincin #" + o.index());
				}
			}
		}
		return new Putusan(true, raw.dasarY(), raw.alasan());
	}

	/** The final verdict of rotation variant {@code k}: its base's verdict, and the rotation must match the terrain. */
	public static Putusan akhir(PutusanTanah.Sumber sumber, Kandidat k) {
		Calon c = calonOf(sumber.seed(), k);
		if (c == null) {
			return Putusan.TIDAK;
		}
		Putusan base = akhirCalon(sumber, c);
		if (!base.layak()) {
			return base;
		}
		if (putarMedan(c.tema())) {
			PutusanTema.Hasil raw = mentah(sumber, c);
			if (raw.putar() != k.putar()) {
				return Putusan.tidak("varian " + PutusanTema.arah(k.putar()));
			}
		}
		return base;
	}

	/** The suitable variant of base candidate {@code c}, or null. */
	public static @Nullable Kandidat layak(PutusanTanah.Sumber sumber, Calon c) {
		if (!akhirCalon(sumber, c).layak()) {
			return null;
		}
		int putar = putarMedan(c.tema()) ? mentah(sumber, c).putar() : c.putar();
		return kandidat(c, putar);
	}

	// ================================================================================================ Rencana

	@Override
	public boolean isStart(long seed, int cx, int cz, String distrik) {
		Distrik d = distrikDi(seed, cx, cz);
		return d != null && d.id().equals(distrik);
	}

	@Override
	public @Nullable Distrik distrikDi(long seed, int cx, int cz) {
		return plan(seed).mulai().get(tema).get(chunkKey(cx, cz));
	}

	@Override
	public List<BoundingBox> zona(long seed) {
		List<BoundingBox> out = new ArrayList<>();
		for (ZonaRencana z : zonaDistrik(seed)) {
			out.add(z.kotak());
		}
		return out;
	}

	@Override
	public List<BoundingBox> zonaAman(long seed) {
		List<BoundingBox> out = new ArrayList<>();
		for (ZonaRencana z : zonaDistrik(seed)) {
			if (z.aman() != null) {
				out.add(z.aman());
			}
		}
		return out;
	}

	@Override
	public List<ZonaRencana> zonaDistrik(long seed) {
		List<ZonaRencana> out = new ArrayList<>();
		for (Distrik d : plan(seed).distrik().get(tema)) {
			int h = SETENGAH_DISTRIK + MARGIN;
			int a = SETENGAH_DISTRIK;
			BoundingBox aman = aman(tema, d.id())
					? new BoundingBox(d.pusatX() - a, -64, d.pusatZ() - a, d.pusatX() + a, 320, d.pusatZ() + a) : null;
			out.add(new ZonaRencana(new BoundingBox(d.pusatX() - h, -64, d.pusatZ() - h, d.pusatX() + h, 320, d.pusatZ() + h), aman, d));
		}
		return out;
	}

	/** True when district {@code distrikId} of {@code t} is safe ground (peaceful towns: all; iblis: the Zona Perjanjian). */
	public static boolean aman(Tema t, String distrikId) {
		return t.damai() || "pasar_gelap".equals(distrikId) || "kedai_racun".equals(distrikId);
	}

	@Override
	public List<SimpulJalan> simpul(long seed, int selX, int selZ) {
		List<SimpulJalan> out = new ArrayList<>();
		for (Kandidat k : plan(seed).kandidat().get(tema)) {
			if (Math.floorDiv(k.pusatX(), RencanaJalan.SEL) == selX && Math.floorDiv(k.pusatZ(), RencanaJalan.SEL) == selZ) {
				int[] g = gerbangJalan(tema, k);
				out.add(new SimpulJalan(g[0], g[1], "kota_tema", SimpulJalan.DESA, ((long) tema.ordinal() << 40) | (k.index() & 0xFFFFFFFFL)));
			}
		}
		return out;
	}

	@Override
	public boolean simpulSah(PutusanTanah.Sumber sumber, SimpulJalan simpul) {
		if (!"kota_tema".equals(simpul.jenis()) || (int) (simpul.kunci() >>> 40) != tema.ordinal()) {
			return false;
		}
		int index = (int) (simpul.kunci() & 0xFFFFFFFFL);
		for (Kandidat k : plan(sumber.seed()).kandidat().get(tema)) {
			if (k.index() == index) {
				return akhir(sumber, k).layak();
			}
		}
		return false;
	}

	@Override
	public List<Distrik> semuaDistrik(long seed) {
		return plan(seed).distrik().get(tema);
	}

	@Override
	public List<Kandidat> kandidat(long seed) {
		return plan(seed).kandidat().get(tema);
	}

	@Override
	public Putusan putusan(PutusanTanah.Sumber sumber, Distrik distrik) {
		Putusan p = akhir(sumber, distrik.kandidat());
		return p;
	}

	// ================================================================================================ hashing

	static long kunci(Tema t, int index) {
		return ((long) t.ordinal() << 32) | (index & 0xFFFFFFFFL);
	}

	static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	static long hash(long seed, long salt, int a, int b) {
		long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
		h ^= a * 0x632BE59BD9B4E019L;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h ^= b * 0x85157AF5L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		return h ^ (h >>> 31);
	}

	static double unit(long seed, long salt, int a, int b) {
		return (hash(seed, salt, a, b) >>> 11) * 0x1.0p-53;
	}
}
