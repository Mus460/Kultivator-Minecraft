package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Proyeksi laju, the projection of DESIGN 39.5.1 for one real disciple ({@code uji perguruan mandiri proyeksi <murid>}):
 * world days per realm from where it stands to True Immortal (Abadi Sejati Awal), computed from the code's own formulas
 * ({@code Rank.xpRequired}, {@code Meditation.baseRate} through {@code meditationBaseTanpaMode}, the disciple's real XP
 * multiplier, its best seat and its path's average K) in four columns:
 * <ul>
 *   <li>growing alone at {@code skalaXp} (the config, 1.5 by the approved default);</li>
 *   <li>the same at 1.0 (the old balance);</li>
 *   <li>off-screen only ({@code x skalaLuarLayar}, 0.75);</li>
 *   <li>the old comparison: the master's Meditasi order 24/7 (the order's x1.5 every hour, K 36 a day, no {@code skalaXp}).</li>
 * </ul>
 * The rank factor grows with the sect rank (Luar 0.60 ... Tetua 0.90), so a real disciple ends between the rows; the
 * waiting at the bottlenecks (the master, the pills, the Heart Trial) is not counted. 1 world day = 20 minutes.
 */
public final class Proyeksi {

	/** One realm of the table. */
	public record Baris(Realm realm, double sendiri, double asli, double luarLayar, double diperintah) {
	}

	/** The whole projection. */
	public record Hasil(List<Baris> baris, double kBar, double tempat, double pengali, double skala, @Nullable JalanHidup jalan) {
		public double total() {
			double sum = 0.0D;
			for (Baris row : baris) {
				sum += row.sendiri();
			}
			return sum;
		}

		public double totalAsli() {
			double sum = 0.0D;
			for (Baris row : baris) {
				sum += row.asli();
			}
			return sum;
		}

		public double totalLuarLayar() {
			double sum = 0.0D;
			for (Baris row : baris) {
				sum += row.luarLayar();
			}
			return sum;
		}

		public double totalDiperintah() {
			double sum = 0.0D;
			for (Baris row : baris) {
				sum += row.diperintah();
			}
			return sum;
		}

		/** Real play hours for {@code days} world days (20 minutes each). */
		public static double jamMain(double days) {
			return days * 20.0D / 60.0D;
		}
	}

	/** Pure: the projection of {@code data} (the first True Immortal stage ends it). */
	public static Hasil hitung(DiscipleData data) {
		MandiriConfig config = MandiriConfig.get();
		JalanHidup path = MandiriApi.jalan(data);
		double kBar = LajuMandiri.kRataRata(path);
		double seat = LajuMandiri.faktorTempat(data);
		double multiplier = DiscipleCultivation.xpMultiplier(null, data, XpSource.MEDITATION);
		double scale = config.skalaXp;
		List<Baris> rows = new ArrayList<>();
		Rank rank = data.rank();
		double firstLeft = Math.max(0.0D, rank.xpRequired() - data.xp());
		Realm current = null;
		double alone = 0.0D;
		double old = 0.0D;
		double off = 0.0D;
		double ordered = 0.0D;
		boolean first = true;
		while (rank.realm() != Realm.TRUE_IMMORTAL) {
			if (current != null && rank.realm() != current) {
				rows.add(new Baris(current, alone, old, off, ordered));
				alone = old = off = ordered = 0.0D;
			}
			current = rank.realm();
			double xp = first ? firstLeft : rank.xpRequired();
			double base = Math.max(0.4D + 0.2D * rank.realm().ordinal(), Meditation.baseRate(rank) * DiscipleCultivation.DISCIPLE_BASE_SHARE);
			double perDayOne = LajuMandiri.DETIK_PER_JAM * 24.0D * base * kBar * multiplier * Math.max(0.1D, seat);
			double perDayOrder = LajuMandiri.DETIK_PER_JAM * 24.0D * base * LajuMandiri.MODE_MEDITASI * multiplier * Math.max(0.1D, seat);
			if (perDayOne > 0.0D) {
				alone += xp / (perDayOne * scale);
				old += xp / perDayOne;
				off += xp / (perDayOne * scale * config.skalaLuarLayar);
			}
			if (perDayOrder > 0.0D) {
				ordered += xp / perDayOrder;
			}
			first = false;
			if (rank.isMax()) {
				break;
			}
			rank = rank.next();
		}
		if (current != null && (alone > 0.0D || old > 0.0D)) {
			rows.add(new Baris(current, alone, old, off, ordered));
		}
		return new Hasil(rows, kBar, seat, multiplier, scale, path);
	}

	private Proyeksi() {
	}
}
