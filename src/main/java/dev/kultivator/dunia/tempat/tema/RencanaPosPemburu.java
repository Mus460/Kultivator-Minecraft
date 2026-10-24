package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.Rencana;
import dev.kultivator.dunia.tempat.RencanaRegistry;
import dev.kultivator.dunia.tempat.ZonaRencana;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.diplomasi.SekteNpc;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The planned satellite {@code kota_tema_pos_pemburu} (DESIGN 38.T.2.5, 38.T.2.6, 38.T.7.9; site kind
 * {@code kota_tema/pos_pemburu}, structure {@code kultivator:kota_tema/pos_pemburu}, salt {@link Tema#SALT_POS_PEMBURU}):
 * the righteous hunters' camp of Han Qingshuang, one per Kota Iblis, 500-700 blocks from its Skull Gate toward the
 * nearest righteous NPC sect (the gate's outward direction when the world has none).
 * <p>
 * Three positions per Kota Iblis candidate (520, 600, 680 blocks; {@code index = calon x 4 + v}); the first one whose
 * ground is camp-worthy (5 x 5 samples 16 apart: at most 2 water, land spread at most 10) is born, and only while its
 * Kota Iblis is. One district {@code pos} whose box is safe ground ({@code TempatZones.safe}).
 */
public final class RencanaPosPemburu implements Rencana {

	public static final String ID = "kota_tema_pos_pemburu";
	public static final String DISTRIK = "pos";
	public static final int[] JARAK = {520, 600, 680};
	/** Half extent of the camp's pieces. */
	public static final int SETENGAH = 56;
	public static final int MARGIN = 48;

	public static final RencanaPosPemburu INSTANCE = new RencanaPosPemburu();

	private record Plan(long seed, List<Kandidat> kandidat, List<Distrik> distrik, Map<Long, Distrik> mulai) {
	}

	private static volatile @Nullable Plan cached;
	private static final Map<Long, Putusan> AKHIR = new ConcurrentHashMap<>();
	private static volatile long akhirSeed = Long.MIN_VALUE;

	/** Registers the plan (from {@link KotaTema#init()}). */
	static void daftar() {
		RencanaRegistry.register(ID, INSTANCE);
	}

	static void lupakan() {
		synchronized (RencanaPosPemburu.class) {
			cached = null;
			AKHIR.clear();
			akhirSeed = Long.MIN_VALUE;
		}
	}

	private static Plan plan(long seed) {
		Plan p = cached;
		if (p != null && p.seed() == seed) {
			return p;
		}
		synchronized (RencanaPosPemburu.class) {
			p = cached;
			if (p != null && p.seed() == seed) {
				return p;
			}
			List<Kandidat> ks = new ArrayList<>();
			List<Distrik> ds = new ArrayList<>();
			Map<Long, Distrik> starts = new HashMap<>();
			GerbangPlan.Plan gates = null;
			try {
				gates = GerbangPlan.of(seed);
			} catch (RuntimeException ignored) {
				// no sect list: outward direction
			}
			for (RencanaKotaTema.Calon c : RencanaKotaTema.plan(seed).calon().get(Tema.IBLIS)) {
				Kandidat town = RencanaKotaTema.kandidat(c, c.putar());
				if (town == null) {
					continue;
				}
				int[] gate = RencanaKotaTema.dunia(Tema.IBLIS, town, 0, Tema.SEL_DISTRIK + Tema.SEL_DISTRIK / 2 + 8);
				int[] centre = RencanaKotaTema.pusat(Tema.IBLIS, town);
				double dx = gate[0] - centre[0];
				double dz = gate[1] - centre[1];
				GerbangPlan.Entry lurus = gates == null ? null : gates.nearest(gate[0], gate[1], SekteNpc.LURUS);
				if (lurus != null) {
					dx = lurus.blockX() - gate[0];
					dz = lurus.blockZ() - gate[1];
				}
				double len = Math.max(1.0D, Math.hypot(dx, dz));
				for (int v = 0; v < JARAK.length; v++) {
					int x = gate[0] + (int) Math.round(dx / len * JARAK[v]);
					int z = gate[1] + (int) Math.round(dz / len * JARAK[v]);
					int putar = (int) (RencanaKotaTema.hash(seed, 0x9055L, c.index(), v) & 3);
					Kandidat k = new Kandidat(ID, c.index() * 4 + v, x, z, putar, false);
					ks.add(k);
					Distrik d = new Distrik(k, DISTRIK, x, z, 0);
					ds.add(d);
					starts.putIfAbsent(RencanaKotaTema.chunkKey(d.chunkX(), d.chunkZ()), d);
				}
			}
			p = new Plan(seed, List.copyOf(ks), List.copyOf(ds), new Long2ObjectOpenHashMap<>(starts));
			cached = p;
			return p;
		}
	}

	/** The final verdict of camp variant {@code k}: its Kota Iblis is born, its ground fits, no earlier variant fits. */
	public static Putusan akhir(PutusanTanah.Sumber sumber, Kandidat k) {
		if (akhirSeed != sumber.seed()) {
			synchronized (RencanaPosPemburu.class) {
				if (akhirSeed != sumber.seed()) {
					AKHIR.clear();
					akhirSeed = sumber.seed();
				}
			}
		}
		Putusan known = AKHIR.get((long) k.index());
		if (known != null) {
			return known;
		}
		Putusan result = hitung(sumber, k);
		AKHIR.put((long) k.index(), result);
		return result;
	}

	private static Putusan hitung(PutusanTanah.Sumber sumber, Kandidat k) {
		RencanaKotaTema.Calon c = RencanaKotaTema.plan(sumber.seed()).calon(Tema.IBLIS, k.index() >> 2);
		if (c == null || !TemaConfig.worldgen(Tema.IBLIS)) {
			return Putusan.TIDAK;
		}
		if (RencanaKotaTema.layak(sumber, c) == null) {
			return Putusan.tidak("kota iblis");
		}
		int v = k.index() & 3;
		for (Kandidat o : plan(sumber.seed()).kandidat()) {
			if (o.index() >> 2 == c.index() && (o.index() & 3) < v && tanah(sumber, o).layak()) {
				return Putusan.tidak("varian #" + (o.index() & 3));
			}
		}
		return tanah(sumber, k);
	}

	private static Putusan tanah(PutusanTanah.Sumber sumber, Kandidat k) {
		int sea = sumber.generator().getSeaLevel();
		int[] h = new int[25];
		int n = 0;
		int water = 0;
		for (int ix = -2; ix <= 2; ix++) {
			for (int iz = -2; iz <= 2; iz++) {
				int x = k.pusatX() + ix * 16;
				int z = k.pusatZ() + iz * 16;
				int surface = sumber.surface(x, z);
				int floor = sumber.floor(x, z);
				if (surface > floor) {
					water++;
				} else {
					h[n++] = floor;
				}
			}
		}
		if (water > 2 || n == 0) {
			return Putusan.tidak("air " + water);
		}
		Arrays.sort(h, 0, n);
		int spread = h[n - 1] - h[0];
		int median = h[n / 2];
		if (spread > 10) {
			return Putusan.tidak("selisih " + spread);
		}
		if (median < sea + 1 || median > sea + 60) {
			return Putusan.tidak("tinggi " + (median - sea));
		}
		return Putusan.ya(median);
	}

	@Override
	public boolean isStart(long seed, int cx, int cz, String distrik) {
		Distrik d = distrikDi(seed, cx, cz);
		return d != null && d.id().equals(distrik);
	}

	@Override
	public @Nullable Distrik distrikDi(long seed, int cx, int cz) {
		return plan(seed).mulai().get(RencanaKotaTema.chunkKey(cx, cz));
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
			out.add(z.aman());
		}
		return out;
	}

	@Override
	public List<ZonaRencana> zonaDistrik(long seed) {
		List<ZonaRencana> out = new ArrayList<>();
		for (Distrik d : plan(seed).distrik()) {
			int h = SETENGAH + MARGIN;
			out.add(new ZonaRencana(new BoundingBox(d.pusatX() - h, -64, d.pusatZ() - h, d.pusatX() + h, 320, d.pusatZ() + h),
					new BoundingBox(d.pusatX() - SETENGAH, -64, d.pusatZ() - SETENGAH, d.pusatX() + SETENGAH, 320, d.pusatZ() + SETENGAH), d));
		}
		return out;
	}

	@Override
	public List<Distrik> semuaDistrik(long seed) {
		return plan(seed).distrik();
	}

	@Override
	public List<Kandidat> kandidat(long seed) {
		return plan(seed).kandidat();
	}

	@Override
	public Putusan putusan(PutusanTanah.Sumber sumber, Distrik distrik) {
		return akhir(sumber, distrik.kandidat());
	}

	/** The born camp of the Kota Iblis base candidate {@code calonIndex}, or null. */
	public static @Nullable Kandidat lahir(PutusanTanah.Sumber sumber, int calonIndex) {
		for (Kandidat k : plan(sumber.seed()).kandidat()) {
			if (k.index() >> 2 == calonIndex && akhir(sumber, k).layak()) {
				return k;
			}
		}
		return null;
	}

	private RencanaPosPemburu() {
	}
}
