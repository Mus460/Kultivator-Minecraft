package dev.kultivator.technique.spec.dsl;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One way to learn a spec (DESIGN 22.8). The codex shows the routes as a checklist; every spec except Dewa,
 * disciple-only and {@code fusion_pair} can also come from a jade slip (not listed as a route).
 * <ul>
 * <li>{@code REALM}, {@code DAO}, {@code COMP n}: automatic ({@code Unlocks.refresh});</li>
 * <li>{@code BOOK}: the enchantment ids of its book pools ({@code minecraft:sharpness});</li>
 * <li>{@code PAV}: the Paviliun Yue Lu; {@code FEAT}; {@code ITEM key}; {@code SCR n} pages of "Kitab Penyegel Iblis";
 * {@code INH theme} (a Dao id or {@code universal}); {@code ENLIGHT};</li>
 * <li>{@code PAIR n}: bond of at least n with an own disciple (fusion pair); {@code KNOWN n}: n known arts;
 * {@code SECOND_DAO n}: any second Dao's comprehension at least n.</li>
 * </ul>
 */
public record Route(Kind kind, int n, String key, List<String> keys, @Nullable Feat feat) {

	public enum Kind {
		REALM, DAO, COMP, BOOK, PAV, FEAT, ITEM, SCR, INH, ENLIGHT, PAIR, KNOWN, SECOND_DAO
	}

	public static Route realm() {
		return new Route(Kind.REALM, 0, "", List.of(), null);
	}

	public static Route dao() {
		return new Route(Kind.DAO, 0, "", List.of(), null);
	}

	public static Route comp(int comprehension) {
		return new Route(Kind.COMP, comprehension, "", List.of(), null);
	}

	/** The book pools of these enchantments ({@code minecraft:sharpness}, or bare {@code sharpness}). */
	public static Route book(String... enchantments) {
		return new Route(Kind.BOOK, 0, "", List.of(enchantments), null);
	}

	public static Route pav() {
		return new Route(Kind.PAV, 0, "", List.of(), null);
	}

	public static Route feat(Feat feat) {
		return new Route(Kind.FEAT, feat.goal(), feat.key(), List.of(), feat);
	}

	/** An item route, e.g. {@code heavenly_flame_seed:bone_chilling}. */
	public static Route item(String key) {
		return new Route(Kind.ITEM, 0, key, List.of(), null);
	}

	/** {@code pages} pages of the lost scripture set 4 "Kitab Penyegel Iblis". */
	public static Route scr(int pages) {
		return new Route(Kind.SCR, pages, "", List.of(), null);
	}

	/** An inheritance trial of {@code theme}: a Dao id or {@code universal}. */
	public static Route inh(String theme) {
		return new Route(Kind.INH, 0, theme, List.of(), null);
	}

	public static Route enlight() {
		return new Route(Kind.ENLIGHT, 0, "", List.of(), null);
	}

	/** Cast together with an own disciple bonded at least {@code bond} (fusion pair). */
	public static Route pair(int bond) {
		return new Route(Kind.PAIR, bond, "", List.of(), null);
	}

	/** At least {@code count} known arts. */
	public static Route known(int count) {
		return new Route(Kind.KNOWN, count, "", List.of(), null);
	}

	/** Any second Dao (other than the main one) with comprehension at least {@code comprehension}. */
	public static Route secondDao(int comprehension) {
		return new Route(Kind.SECOND_DAO, comprehension, "", List.of(), null);
	}

	/** Learned without any action (REALM, DAO, COMP). */
	public boolean automatic() {
		return kind == Kind.REALM || kind == Kind.DAO || kind == Kind.COMP;
	}
}
