package dev.kultivator.client.hub;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Extra launcher cards of the hub sections (DESIGN 34.6 T, contract C34.T1): modules of phase 34 add their card here from
 * their client init, and {@code HubCardsPage} puts them after the built-in cards of the section. A card's texts follow the
 * page's key format, {@code kultivator.hub.card.<id>} and {@code kultivator.hub.card.<id>.tip} (in the module's own lang
 * fragment). At most one card per module (T "Peta Dunia" and R "Menara Seratus" in DUNIA, K "Kota Abadi" in KEABADIAN).
 * <p>
 * Section {@link HubNav#SEKTE} (audit M5, T9) has two sub-coins: "Aula" holds the hall's built-in cards and any extra card
 * whose id does not start with {@code sekte_}; "Perguruan" holds every card whose id starts with {@code sekte_} (Sekte yang
 * Hidup 36, the {@code sekte_langit} card of 37). A {@code sekte_*} card in SEKTE always gets the hall lock ("butuh Prasasti
 * Aula Sekte") before its own lock, so its module passes {@code null} or only its extra condition (for example a hall
 * level). Each sub-coin is meant for at most 9 cards: the grid then fits 320 × 240 and GUI scale 4 on 1366 × 768; more
 * cards still work (the grid adds a column, then scrolls) but should get their own sub-coin. Client thread only.
 */
public final class HubCards {

	/**
	 * One card: {@code section} a {@link HubNav} section, {@code glyph} a {@code Glyphs} index, {@code lock} the lock line
	 * (null or a supplier of null = open), {@code action} what a click does.
	 */
	public record Extra(int section, String id, int glyph, @Nullable Supplier<Component> lock, Runnable action) {
	}

	private static final List<Extra> EXTRAS = new ArrayList<>();

	/** Adds a card to {@code section}; a second card with the same id replaces the first. */
	public static synchronized void register(int section, String id, int glyph, @Nullable Supplier<Component> lock, Runnable action) {
		if (id == null || id.isEmpty() || action == null || section < 0 || section >= HubNav.SECTIONS) {
			return;
		}
		EXTRAS.removeIf(extra -> extra.id().equals(id));
		EXTRAS.add(new Extra(section, id, glyph, lock, action));
	}

	/** The extra cards of {@code section} in registration order (a copy). */
	public static synchronized List<Extra> of(int section) {
		if (EXTRAS.isEmpty()) {
			return Collections.emptyList();
		}
		List<Extra> list = new ArrayList<>();
		for (Extra extra : EXTRAS) {
			if (extra.section() == section) {
				list.add(extra);
			}
		}
		return list;
	}

	private HubCards() {
	}
}
