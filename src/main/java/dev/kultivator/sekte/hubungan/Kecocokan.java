package dev.kultivator.sekte.hubungan;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Kecocokan, how well two disciples get along (DESIGN 36.2.2), and the rules that turn a pair's affinity and history
 * into its kind of tie. Pure functions over {@code DiscipleData} and the traits module P hands out through
 * {@code SekteApi.hati()} (none while P is off, so only the Dao and the spiritual roots count then).
 * <p>
 * Sumber: the sworn brothers of the Peach Garden (<i>Romance of the Three Kingdoms</i> ch. 1) chose each other for one
 * shared aim; Wang Lin and Li Muwan (<i>Renegade Immortal</i>) became Dao companions; the senior and junior rivalries of
 * <i>A Will Eternal</i>.
 */
final class Kecocokan {

	/** Affinity a friendship needs (and keeps down to {@link #FRIEND_KEEP}). */
	static final int FRIEND = 40;
	static final int FRIEND_KEEP = 30;
	/** Friends this close may swear brotherhood (the master approves). */
	static final int OATH = 75;
	/** Friends this close may fall in love (a chance per tea meeting). */
	static final int LOVE = 70;
	static final int LOVE_KEEP = 50;
	/** Lovers this close may become Dao companions with the master's blessing. */
	static final int DAO = 90;
	static final int RIVAL = -30;
	static final int RIVAL_KEEP = -20;
	static final int GRUDGE = -60;
	static final int GRUDGE_KEEP = -40;
	/** A single event at or below this weight is a heavy wrong (a grudge may grow from it). */
	static final int WOUND = -6;
	/** Bouts against each other in tournaments that make two disciples rivals. */
	static final int RIVAL_BOUTS = 3;

	/**
	 * The compatibility of two disciples, about -6..+8: shared traits +1 each, opposite ends of one axis -1 each, a
	 * Penyayang +1 with anyone, two Sombong -2, the same Dao +2, a shared root element +1.
	 */
	static int compatibility(DiscipleData a, DiscipleData b) {
		int score = 0;
		List<Sifat> ta = SekteApi.hati().traits(a);
		List<Sifat> tb = SekteApi.hati().traits(b);
		for (Sifat sifat : ta) {
			if (tb.contains(sifat)) {
				score += 1;
			}
			if (tb.contains(sifat.opposite())) {
				score -= 1;
			}
		}
		if (ta.contains(Sifat.PENYAYANG)) {
			score += 1;
		}
		if (tb.contains(Sifat.PENYAYANG)) {
			score += 1;
		}
		if (ta.contains(Sifat.SOMBONG) && tb.contains(Sifat.SOMBONG)) {
			score -= 2;
		}
		Dao da = a.dao();
		if (da != null && da == b.dao()) {
			score += 2;
		}
		if ((a.rootElements() & b.rootElements()) != 0) {
			score += 1;
		}
		return score;
	}

	/**
	 * Scales a change by the two disciples' traits (P's table): friendship x1.25 with a Rendah Hati, rivalry x1.5 with a
	 * Sombong, a heavy wrong x0.5 with a Penyayang and x2 with a Pendendam. Either data may be null (away: no factor).
	 */
	static int traitScaled(int delta, @Nullable DiscipleData a, @Nullable DiscipleData b) {
		if (delta == 0) {
			return 0;
		}
		double d = delta;
		if (delta > 0 && (has(a, Sifat.RENDAH_HATI) || has(b, Sifat.RENDAH_HATI))) {
			d *= 1.25D;
		}
		if (delta < 0 && (has(a, Sifat.SOMBONG) || has(b, Sifat.SOMBONG))) {
			d *= 1.5D;
		}
		if (delta <= WOUND) {
			if (has(a, Sifat.PENYAYANG) || has(b, Sifat.PENYAYANG)) {
				d *= 0.5D;
			}
			if (has(a, Sifat.PENDENDAM) || has(b, Sifat.PENDENDAM)) {
				d *= 2.0D;
			}
		}
		int out = (int) Math.round(d);
		return out == 0 ? Integer.signum(delta) : out;
	}

	static boolean has(@Nullable DiscipleData data, Sifat sifat) {
		return data != null && SekteApi.hati().has(data, sifat);
	}

	/**
	 * The kind a pair has now, from its current kind (ties hold a little past the line that made them), its affinity,
	 * tournament bouts and heavy wrongs. Kekasih, Saudara Angkat and Pasangan Dao are never reached here (a meeting roll
	 * and the master's ceremonies make them); they only end here when the affinity falls.
	 */
	static Ikatan derive(Ikatan current, int affinity, int bouts, boolean wounded, boolean known, boolean grudges) {
		if ((current == Ikatan.PASANGAN_DAO || current == Ikatan.SAUDARA_ANGKAT) && affinity >= 0) {
			return current;
		}
		if (current == Ikatan.KEKASIH && affinity >= LOVE_KEEP) {
			return current;
		}
		if (grudges && (current == Ikatan.DENDAM ? affinity <= GRUDGE_KEEP : affinity <= GRUDGE && wounded)) {
			return Ikatan.DENDAM;
		}
		boolean rivalBouts = bouts >= RIVAL_BOUTS && affinity < FRIEND;
		if (rivalBouts || (current == Ikatan.SAINGAN || current == Ikatan.DENDAM ? affinity <= RIVAL_KEEP : affinity <= RIVAL)) {
			return Ikatan.SAINGAN;
		}
		boolean wasClose = current == Ikatan.SAHABAT || current == Ikatan.KEKASIH || current == Ikatan.SAUDARA_ANGKAT
				|| current == Ikatan.PASANGAN_DAO;
		if (affinity >= (wasClose ? FRIEND_KEEP : FRIEND)) {
			return Ikatan.SAHABAT;
		}
		return known ? Ikatan.KENALAN : Ikatan.NONE;
	}

	/** Both at Foundation or higher (love needs it). */
	static boolean loveRealm(@Nullable DiscipleData a, @Nullable DiscipleData b) {
		return a != null && b != null && a.realm().ordinal() >= Realm.FOUNDATION.ordinal() && b.realm().ordinal() >= Realm.FOUNDATION.ordinal();
	}

	/** Both at Golden Core or higher (a Dao companionship needs it). */
	static boolean daoRealm(@Nullable DiscipleData a, @Nullable DiscipleData b) {
		return a != null && b != null && a.realm().ordinal() >= Realm.GOLDEN_CORE.ordinal()
				&& b.realm().ordinal() >= Realm.GOLDEN_CORE.ordinal();
	}

	/** The pull of one tie on a disciple's contentment (P reads the sum as {@code socialBalance}). */
	static int balanceOf(Ikatan kind, int affinity, boolean proud) {
		return switch (kind) {
			case SAHABAT -> 10;
			case SAUDARA_ANGKAT -> 16;
			case KEKASIH -> 14;
			case PASANGAN_DAO -> 20;
			case KENALAN -> affinity >= 20 ? 2 : 0;
			case SAINGAN -> proud ? -20 : -6;
			case DENDAM -> -40;
			case NONE -> 0;
		};
	}

	/** The order ties win the single bond effect of a disciple (36.2.2: only the strongest counts). */
	static int effectRank(Ikatan kind) {
		return switch (kind) {
			case PASANGAN_DAO -> 6;
			case SAUDARA_ANGKAT -> 5;
			case KEKASIH -> 4;
			case SAHABAT -> 3;
			case DENDAM -> 2;
			case SAINGAN -> 1;
			default -> 0;
		};
	}

	private Kecocokan() {
	}
}
