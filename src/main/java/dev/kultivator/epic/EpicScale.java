package dev.kultivator.epic;

import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;

/**
 * The scene scale S 0-10 of DESIGN 23.10.1: how big the sky, the vortex and the bolts of a scene are. Pure lookups;
 * disciple scenes use the S of the disciple's target (the client shrinks radius and height itself).
 */
public final class EpicScale {

	/** Disciples with this many Bintang Abadi stars or more get S 10. */
	public static final int STAR_SCALE_FROM = 9;

	/** S of a scene whose subject targets (or holds) {@code rank}. */
	public static int of(Rank rank) {
		Realm realm = rank.realm();
		return switch (realm) {
			case MORTAL, QI_REFINING -> 0;
			case FOUNDATION -> 1;
			case GOLDEN_CORE -> 2;
			case NASCENT_SOUL -> 3;
			case DEITY_TRANSFORMATION -> 4;
			case DAO_UNION -> 5;
			case IMMORTAL_ASCENSION -> rank.layer() < 3 ? 6 : rank.layer() < 6 ? 7 : 8;
			case TRUE_IMMORTAL -> 9;
		};
	}

	/** S of a disciple scene: the disciple's target rank (the next realm at a bottleneck), 10 from ★9. */
	public static int ofDisciple(DiscipleEntity disciple) {
		if (DisciplePaths.stars(disciple.data()) >= STAR_SCALE_FROM) {
			return EpicIds.SCALE_MAX;
		}
		Rank rank = disciple.data().rank();
		if (rank.nextIsBreakthrough()) {
			rank = rank.next();
		}
		return of(rank);
	}

	private EpicScale() {
	}
}
