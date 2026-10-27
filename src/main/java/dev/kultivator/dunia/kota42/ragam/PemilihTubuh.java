package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.PendudukSnapshot;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Who gets a body (DESIGN 42.4A.2, contract C42.S1, K16 proportional), registered through the 38NL hook
 * {@code Pemanggil.addPemilihTubuh}:
 * <ol>
 *   <li>staff and stationary roles first (the audit rule of {@code audit-npc-tempat.md}), nearest first;</li>
 *   <li>the remaining body budget shared between the visible zones (zones with a candidate inside {@code PANGGIL}) in
 *       proportion to the residents whose goal lies there (the data count of {@link Penyeimbang.Hunian}, not only the
 *       candidates), by the largest remainder, at least one body per inhabited zone within 96 blocks;</li>
 *   <li>inside a zone the nearest to a player first; the list interleaves the zones by how far each is from its quota, so
 *       whatever cap of the caller ends the cycle, the bodies made so far are already spread.</li>
 * </ol>
 * The far crowd (impostors) uses the same split per player ({@link #pilihJauh}), so the edge of sight stays inhabited.
 * {@code BARU_PER_SIKLUS}, {@code LEPAS}, {@code JAUH} and the {@code npc_hidup} cap are unchanged (the caller applies them).
 */
public final class PemilihTubuh {

	/** Inhabited zones within this distance of a player get at least one body. */
	static final double DEKAT_MINIMAL = 96.0D;

	/** The 38NL hook. */
	static final Pemanggil.PemilihTubuh HOOK = new Pemanggil.PemilihTubuh() {
		@Override
		public @Nullable List<Pemanggil.Calon> pilih(TempatHuni huni, List<Pemanggil.Calon> calon, int anggaran) {
			if (!KotaConfig.warga().sebaranAktif) {
				return null;
			}
			PetaZona peta = PetaZona.ada(huni.situsId);
			Penyeimbang.Hunian h = Penyeimbang.ada(huni.situsId);
			if (peta == null || h == null || peta.jumlah() == 0) {
				return null;
			}
			return urut(peta, h, calon, anggaran, c -> c.p().peran().stationary, c -> peta.zonaDi(c.tujuan().pos()), Pemanggil.Calon::jarak);
		}

		@Override
		public @Nullable List<PendudukSnapshot.Entri> pilihJauh(TempatHuni huni, ServerPlayer player, List<PendudukSnapshot.Entri> entri, int maks) {
			if (!KotaConfig.warga().sebaranAktif) {
				return null;
			}
			PetaZona peta = PetaZona.ada(huni.situsId);
			Penyeimbang.Hunian h = Penyeimbang.ada(huni.situsId);
			if (peta == null || h == null || peta.jumlah() == 0) {
				return null;
			}
			BlockPos anchor = huni.pusat();
			Vec3 at = player.position();
			ToIntFunction<PendudukSnapshot.Entri> zona = e -> peta.zonaDi(posisi(anchor, e));
			ToDoubleFunction<PendudukSnapshot.Entri> jarak = e -> Vec3.atBottomCenterOf(posisi(anchor, e)).distanceTo(at);
			List<PendudukSnapshot.Entri> out = sebar(peta, h, entri, maks, zona, jarak);
			return out.size() > maks ? List.copyOf(out.subList(0, maks)) : out;
		}
	};

	/**
	 * The order of all candidates: keepers (staff and stationary roles) first, nearest first, as the audit rule wants; when the
	 * keepers alone outnumber the body budget (the capital: most residents are staff) they are shared between the visible
	 * zones like everyone else, so the bodies do not all pile up where the staff happens to be thickest.
	 */
	static <T> List<T> urut(PetaZona peta, Penyeimbang.Hunian h, List<T> items, int anggaran, java.util.function.Predicate<T> penjaga,
			ToIntFunction<T> zonaDari, ToDoubleFunction<T> jarakDari) {
		List<T> keepers = new ArrayList<>();
		List<T> rest = new ArrayList<>();
		for (T item : items) {
			(penjaga.test(item) ? keepers : rest).add(item);
		}
		List<T> out = new ArrayList<>(items.size());
		if (keepers.size() > anggaran) {
			out.addAll(sebar(peta, h, keepers, anggaran, zonaDari, jarakDari));
		} else {
			keepers.sort(Comparator.comparingDouble(jarakDari));
			out.addAll(keepers);
		}
		out.addAll(sebar(peta, h, rest, anggaran, zonaDari, jarakDari));
		return out;
	}

	/** The block of a far-crowd entry (half-block offsets from the place anchor). */
	static BlockPos posisi(BlockPos anchor, PendudukSnapshot.Entri e) {
		return anchor.offset(Math.floorDiv(e.dx(), 2), Math.floorDiv(e.dy(), 2), Math.floorDiv(e.dz(), 2));
	}

	/**
	 * The proportional order of {@code items}: quota per visible zone by the largest remainder of {@code anggaran} (weight =
	 * data residents of the zone, at least the candidates there; minimum one for a zone with a candidate within
	 * {@value #DEKAT_MINIMAL} blocks), nearest first inside a zone, zones interleaved by fill; items beyond the quotas follow
	 * by distance (the caller's other limits may still use them).
	 */
	static <T> List<T> sebar(PetaZona peta, Penyeimbang.Hunian h, List<T> items, int anggaran, ToIntFunction<T> zonaDari, ToDoubleFunction<T> jarakDari) {
		int n = peta.jumlah();
		if (items.isEmpty()) {
			return List.of();
		}
		List<List<T>> perZona = new ArrayList<>(n + 1);
		for (int z = 0; z <= n; z++) {
			perZona.add(new ArrayList<>());
		}
		for (T item : items) {
			int z = zonaDari.applyAsInt(item);
			perZona.get(z >= 0 && z < n ? z : n).add(item);
		}
		double[] bobot = new double[n + 1];
		boolean[] dekat = new boolean[n + 1];
		for (int z = 0; z <= n; z++) {
			List<T> list = perZona.get(z);
			if (list.isEmpty()) {
				continue;
			}
			list.sort(Comparator.comparingDouble(jarakDari));
			bobot[z] = Math.max(list.size(), z < n ? h.luar(z) : 0);
			dekat[z] = jarakDari.applyAsDouble(list.getFirst()) <= DEKAT_MINIMAL;
		}
		int[] maks = new int[n + 1];
		for (int z = 0; z <= n; z++) {
			maks[z] = perZona.get(z).size();
		}
		int[] kuota = kuota(bobot, dekat, anggaran, maks);
		int[] diberi = new int[n + 1];
		List<T> out = new ArrayList<>(items.size());
		int total = 0;
		for (int q : kuota) {
			total += q;
		}
		for (int step = 0; step < total; step++) {
			int best = -1;
			double bestFill = Double.MAX_VALUE;
			for (int z = 0; z <= n; z++) {
				if (diberi[z] >= kuota[z] || diberi[z] >= perZona.get(z).size()) {
					continue;
				}
				double fill = diberi[z] / (double) kuota[z];
				if (fill < bestFill) {
					bestFill = fill;
					best = z;
				}
			}
			if (best < 0) {
				break;
			}
			out.add(perZona.get(best).get(diberi[best]++));
		}
		List<T> sisa = new ArrayList<>();
		for (int z = 0; z <= n; z++) {
			List<T> list = perZona.get(z);
			for (int i = diberi[z]; i < list.size(); i++) {
				sisa.add(list.get(i));
			}
		}
		sisa.sort(Comparator.comparingDouble(jarakDari));
		out.addAll(sisa);
		return out;
	}

	/**
	 * Quotas by the largest remainder of {@code anggaran} over the weights, one first for every zone marked {@code dekat}
	 * (while the budget lasts, the heaviest zones first), never more than a zone's items would need is left to the caller.
	 */
	static int[] kuota(double[] bobot, boolean[] dekat, int anggaran) {
		return kuota(bobot, dekat, anggaran, null);
	}

	/**
	 * As above, but no zone is given more than {@code maks} (the bodies it could actually have): the share a small zone cannot
	 * use goes to the other zones by their weights, not to whoever stands nearest.
	 */
	static int[] kuota(double[] bobot, boolean[] dekat, int anggaran, int @Nullable [] maks) {
		double[] w = bobot.clone();
		int[] out = kuota1(w, dekat, anggaran);
		if (maks == null) {
			return out;
		}
		for (int round = 0; round < bobot.length; round++) {
			int free = 0;
			boolean cut = false;
			for (int i = 0; i < out.length; i++) {
				if (out[i] > maks[i]) {
					free += out[i] - maks[i];
					out[i] = maks[i];
					w[i] = 0.0D;
					cut = true;
				} else if (maks[i] <= out[i]) {
					w[i] = 0.0D;
				}
			}
			if (!cut || free <= 0) {
				break;
			}
			int[] more = Kapasitas.sisaTerbesar(w, free, 0);
			for (int i = 0; i < out.length; i++) {
				out[i] += more[i];
			}
		}
		return out;
	}

	private static int[] kuota1(double[] bobot, boolean[] dekat, int anggaran) {
		int n = bobot.length;
		int[] out = new int[n];
		if (anggaran <= 0) {
			return out;
		}
		int left = anggaran;
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Double.compare(bobot[b], bobot[a]));
		for (int i : order) {
			if (left <= 0) {
				break;
			}
			if (dekat[i] && bobot[i] > 0.0D) {
				out[i] = 1;
				left--;
			}
		}
		int[] more = Kapasitas.sisaTerbesar(bobot, left, 0);
		for (int i = 0; i < n; i++) {
			out[i] += more[i];
		}
		return out;
	}

	/**
	 * C42.S1 convenience: the residents of {@code calon} (any order) that get one of {@code anggaran} bodies, by the same
	 * split over the zones of their current goals (the balancer's picks and the data pass); residents without a known zone
	 * and the stationary keepers come first and last as in 38 (keepers first).
	 */
	public static List<Penduduk> pilih(TempatHuni huni, List<Penduduk> calon, int anggaran) {
		if (anggaran <= 0 || calon.isEmpty()) {
			return List.of();
		}
		PetaZona peta = PetaZona.ada(huni.situsId);
		Penyeimbang.Hunian h = Penyeimbang.ada(huni.situsId);
		if (peta == null || h == null || peta.jumlah() == 0 || !KotaConfig.warga().sebaranAktif) {
			return List.copyOf(calon.subList(0, Math.min(anggaran, calon.size())));
		}
		long now = KotaRagam.sekarang();
		List<Penduduk> keepers = new ArrayList<>();
		List<Penduduk> rest = new ArrayList<>();
		for (Penduduk p : calon) {
			(p.peran().stationary ? keepers : rest).add(p);
		}
		List<Penduduk> out = new ArrayList<>(keepers);
		int left = Math.max(0, anggaran - keepers.size());
		List<Penduduk> spread = sebar(peta, h, rest, left, p -> h.zonaWarga(p.id(), now), p -> p.id());
		out.addAll(spread.subList(0, Math.min(left, spread.size())));
		return List.copyOf(out.subList(0, Math.min(anggaran, out.size())));
	}

	private PemilihTubuh() {
	}
}
