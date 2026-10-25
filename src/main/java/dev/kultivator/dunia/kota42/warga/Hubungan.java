package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bonds between residents (DESIGN 42.4.5). Every resident keeps at most {@code kotaWarga.hubunganMaks} packed bonds
 * ({@link JenisHubungan#kemas}); family is read from the tree and never stored. A bond is kept on both sides with the same
 * strength. Each Hari Warga every resident has two meetings with residents who share a context with it (neighbours, the
 * same work, the same habit, children at play, the temple); the strength moves by the temperament match
 * {@link #KECOCOKAN} + {@code random(-2..+3)} + 2 per shared habit - 2 for a trade rival, and the bond kind follows the
 * strength: {@code SAHABAT} >= 40, {@code KEKASIH} >= 60 (one single man and one single woman 16-45 whose temperaments do not
 * clash, 42.16 K9), {@code MUSUH} <= -40, {@code SAINGAN} <= -20 (same trade), {@code KENALAN} in between.
 * {@code PASANGAN} (marriage) and {@code GURU_MURID} (apprenticeship) are set by events, never by strength.
 */
public final class Hubungan {

	/** Temperament match (row = A, column = B) in {@code Tokoh.WATAK} order: ramah, curiga, serakah, bijak, penakut, sombong, jujur, licik. */
	public static final int[][] KECOCOKAN = {
			{3, 0, -1, 2, 2, -1, 2, -1},
			{0, 1, -1, 1, 1, -2, 1, -3},
			{-1, -1, 0, -2, 1, 0, -3, 2},
			{2, 1, -2, 2, 1, -2, 3, -1},
			{2, 1, 1, 1, 1, -3, 1, -1},
			{-1, -2, 0, -2, -3, -2, -1, 1},
			{2, 1, -3, 3, 1, -1, 2, -3},
			{-1, -3, 2, -1, -1, 1, -3, 1}};

	/** Meetings per resident per Hari Warga. */
	public static final int TEMU = 2;

	public static int cocok(Warga a, Warga b) {
		return KECOCOKAN[Math.floorMod(a.watak, 8)][Math.floorMod(b.watak, 8)];
	}

	/** True for parent, child, spouse, sibling, grandparent or grandchild (bonds are never stored for them). */
	public static boolean keluarga(WargaData data, Warga a, Warga b) {
		if (a.id == b.id || a.pasangan == b.id || b.pasangan == a.id || a.ayah == b.id || a.ibu == b.id || b.ayah == a.id || b.ibu == a.id) {
			return true;
		}
		if (a.ayah != Warga.TANPA && a.ayah == b.ayah || a.ibu != Warga.TANPA && a.ibu == b.ibu) {
			return true;
		}
		for (int parent : new int[]{a.ayah, a.ibu}) {
			Warga p = parent == Warga.TANPA ? null : data.byId(parent);
			if (p != null && (p.ayah == b.id || p.ibu == b.id)) {
				return true;
			}
		}
		for (int parent : new int[]{b.ayah, b.ibu}) {
			Warga p = parent == Warga.TANPA ? null : data.byId(parent);
			if (p != null && (p.ayah == a.id || p.ibu == a.id)) {
				return true;
			}
		}
		return false;
	}

	/** The bond strength of {@code a} toward {@code b} (0 when none). */
	public static int kekuatan(Warga a, Warga b) {
		long bond = a.hubunganDengan(b.id);
		return bond == 0L ? 0 : JenisHubungan.kekuatan(bond);
	}

	public static @Nullable JenisHubungan jenis(Warga a, Warga b) {
		long bond = a.hubunganDengan(b.id);
		return bond == 0L ? null : JenisHubungan.jenis(bond);
	}

	/** The id of {@code w}'s lover ({@code KEKASIH}), or {@link Warga#TANPA}. */
	public static int kekasih(Warga w) {
		for (long b : w.hubungan) {
			if (JenisHubungan.jenis(b) == JenisHubungan.KEKASIH) {
				return JenisHubungan.idLain(b);
			}
		}
		return Warga.TANPA;
	}

	/** True when both may become lovers: one man and one woman, single, 16-45, temperaments not clashing (42.16 K9). */
	public static boolean bolehKekasih(Warga a, Warga b, int umurA, int umurB) {
		return a.kelamin != b.kelamin && a.lajang() && b.lajang() && umurA >= 16 && umurA <= 45 && umurB >= 16 && umurB <= 45
				&& cocok(a, b) >= -2 && cocok(b, a) >= -2;
	}

	/**
	 * Sets the bond between {@code a} and {@code b} on both sides to {@code kekuatan}, choosing the kind from the strength
	 * (an event kind {@code PASANGAN}/{@code GURU_MURID} already there stays). Family never gets a bond.
	 */
	public static void setel(WargaData data, Warga a, Warga b, int kekuatan, long hari, int umurA, int umurB) {
		if (keluarga(data, a, b)) {
			return;
		}
		JenisHubungan now = jenis(a, b);
		JenisHubungan kind = jenisDari(a, b, now, kekuatan, umurA, umurB);
		long since = now == kind && a.hubunganDengan(b.id) != 0L ? JenisHubungan.sejak(a.hubunganDengan(b.id)) : hari;
		int maks = KotaConfig.warga().hubunganMaks;
		a.setHubungan(JenisHubungan.kemas(b.id, kind, kekuatan, since), maks);
		b.setHubungan(JenisHubungan.kemas(a.id, kind, kekuatan, since), maks);
	}

	/** Changes the bond strength between two residents by {@code delta} on both sides. */
	public static void ubah(WargaData data, Warga a, Warga b, int delta, long hari, int umurA, int umurB) {
		setel(data, a, b, kekuatan(a, b) + delta, hari, umurA, umurB);
	}

	/** Event bonds (marriage, apprenticeship): set on both sides at strength {@code kekuatan}. */
	public static void ikat(Warga a, Warga b, JenisHubungan jenis, int kekuatan, long hari) {
		int maks = KotaConfig.warga().hubunganMaks;
		a.setHubungan(JenisHubungan.kemas(b.id, jenis, kekuatan, hari), maks);
		b.setHubungan(JenisHubungan.kemas(a.id, jenis, kekuatan, hari), maks);
	}

	private static JenisHubungan jenisDari(Warga a, Warga b, @Nullable JenisHubungan now, int k, int umurA, int umurB) {
		if (now == JenisHubungan.PASANGAN || now == JenisHubungan.GURU_MURID) {
			return now;
		}
		if (now == JenisHubungan.KEKASIH && k > 30 && a.kelamin != b.kelamin) {
			return JenisHubungan.KEKASIH;
		}
		if (k >= JenisHubungan.KEKASIH.ambang && bolehKekasih(a, b, umurA, umurB) && kekasih(a) == Warga.TANPA && kekasih(b) == Warga.TANPA) {
			return JenisHubungan.KEKASIH;
		}
		if (k >= JenisHubungan.SAHABAT.ambang) {
			return JenisHubungan.SAHABAT;
		}
		if (k <= JenisHubungan.MUSUH.ambang) {
			return JenisHubungan.MUSUH;
		}
		if (k <= JenisHubungan.SAINGAN.ambang && a.peran.equals(b.peran)) {
			return JenisHubungan.SAINGAN;
		}
		return JenisHubungan.KENALAN;
	}

	/** True when {@code a} and {@code b} compete in one trade in one place (merchants of the same role). */
	public static boolean saingan(Warga a, Warga b) {
		return a.peran.equals(b.peran) && a.peranNpc().kelompok == NpcRole.Kelompok.PEDAGANG;
	}

	private static boolean kebiasaanSama(Warga a, Warga b) {
		return a.kebiasaan1 != 0 && (a.kebiasaan1 == b.kebiasaan1 || a.kebiasaan1 == b.kebiasaan2)
				|| a.kebiasaan2 != 0 && (a.kebiasaan2 == b.kebiasaan1 || a.kebiasaan2 == b.kebiasaan2);
	}

	// ================================================================== the daily meetings (Hari Warga)

	/**
	 * Two meetings per resident with residents who share a context (42.4.5); O(n) buckets, deterministic for the random.
	 *
	 * @param umur the age of each resident (by id) on this day
	 */
	public static void temuHarian(WargaData data, List<Warga> warga, Map<Integer, Integer> umur, long hari, RandomSource random) {
		List<Warga> hidup = new ArrayList<>(warga.size());
		Map<String, List<Warga>> perPeran = new HashMap<>();
		Map<Integer, List<Warga>> perKebiasaan = new HashMap<>();
		List<Warga> anak = new ArrayList<>();
		List<Warga> kuil = new ArrayList<>();
		for (Warga w : warga) {
			int u = umur.getOrDefault(w.id, 0);
			if (w.status != Warga.Status.HIDUP || u < 3) {
				continue;
			}
			hidup.add(w);
			perPeran.computeIfAbsent(w.peran, k -> new ArrayList<>()).add(w);
			if (w.kebiasaan1 != 0) {
				perKebiasaan.computeIfAbsent(w.kebiasaan1, k -> new ArrayList<>()).add(w);
			}
			if (w.kebiasaan2 != 0) {
				perKebiasaan.computeIfAbsent(w.kebiasaan2, k -> new ArrayList<>()).add(w);
			}
			if (u <= 12) {
				anak.add(w);
			}
			if (w.peranNpc().kelompok == NpcRole.Kelompok.RAHIB || w.kebiasaan1 == 10 || w.kebiasaan2 == 10) {
				kuil.add(w);
			}
		}
		if (hidup.size() < 2) {
			return;
		}
		// neighbours: residents of the households next door (by household id order, which follows the generated houses)
		hidup.sort((x, y) -> Integer.compare(x.rumahTangga, y.rumahTangga));
		Map<Integer, Integer> posisi = new HashMap<>();
		for (int i = 0; i < hidup.size(); i++) {
			posisi.put(hidup.get(i).id, i);
		}
		for (Warga w : hidup) {
			int uw = umur.getOrDefault(w.id, 0);
			for (int t = 0; t < TEMU; t++) {
				Warga other = pasangan(w, uw, hidup, posisi, perPeran, perKebiasaan, anak, kuil, random);
				if (other == null || other == w || keluarga(data, w, other)) {
					continue;
				}
				int uo = umur.getOrDefault(other.id, 0);
				int delta = cocok(w, other) + random.nextInt(6) - 2 + (kebiasaanSama(w, other) ? 2 : 0) - (saingan(w, other) ? 2 : 0);
				ubah(data, w, other, delta, hari, uw, uo);
			}
		}
	}

	/** One meeting partner for {@code w}: a neighbour, a co-worker, a habit mate, a playmate or a temple goer. */
	private static @Nullable Warga pasangan(Warga w, int umur, List<Warga> hidup, Map<Integer, Integer> posisi, Map<String, List<Warga>> perPeran,
			Map<Integer, List<Warga>> perKebiasaan, List<Warga> anak, List<Warga> kuil, RandomSource random) {
		int roll = random.nextInt(100);
		if (umur <= 12 && roll < 60 && anak.size() > 1) {
			return anak.get(random.nextInt(anak.size()));
		}
		if (roll < 35) {
			int at = posisi.getOrDefault(w.id, 0);
			int j = Math.floorMod(at + random.nextInt(9) - 4, hidup.size());
			return hidup.get(j);
		}
		if (roll < 65) {
			List<Warga> same = perPeran.get(w.peran);
			if (same != null && same.size() > 1) {
				return same.get(random.nextInt(same.size()));
			}
		}
		if (roll < 85) {
			int habit = random.nextBoolean() ? w.kebiasaan1 : w.kebiasaan2;
			List<Warga> mates = habit == 0 ? null : perKebiasaan.get(habit);
			if (mates != null && mates.size() > 1) {
				return mates.get(random.nextInt(mates.size()));
			}
		}
		if (roll < 92 && kuil.contains(w) && kuil.size() > 1) {
			return kuil.get(random.nextInt(kuil.size()));
		}
		return hidup.get(random.nextInt(hidup.size()));
	}

	/**
	 * The first bonds of a raised place (42.4.13): 2-4 bonds per resident aged 6 or more, weighted toward neighbours, the
	 * same work and the same age; strength from the temperament match and luck.
	 */
	public static void awal(WargaData data, List<Warga> warga, Map<Integer, Integer> umur, long hari, RandomSource random) {
		List<Warga> calon = new ArrayList<>();
		for (Warga w : warga) {
			if (w.status == Warga.Status.HIDUP && umur.getOrDefault(w.id, 0) >= 6) {
				calon.add(w);
			}
		}
		if (calon.size() < 3) {
			return;
		}
		for (Warga w : calon) {
			int uw = umur.getOrDefault(w.id, 0);
			int want = 2 + random.nextInt(3);
			for (int tries = 0; tries < want * 6 && w.hubungan.length < want; tries++) {
				Warga o = calon.get(random.nextInt(calon.size()));
				int uo = umur.getOrDefault(o.id, 0);
				int weight = 1 + (Math.abs(o.rumahTangga - w.rumahTangga) <= 2 ? 3 : 0) + (o.peran.equals(w.peran) ? 3 : 0) + (Math.abs(uo - uw) <= 10 ? 2 : 0);
				if (o == w || random.nextInt(9) >= weight || keluarga(data, w, o) || w.hubunganDengan(o.id) != 0L) {
					continue;
				}
				int k = cocok(w, o) * 6 + random.nextInt(41) - 15;
				setel(data, w, o, k, hari - random.nextInt(60), uw, uo);
			}
		}
	}

	private Hubungan() {
	}
}
