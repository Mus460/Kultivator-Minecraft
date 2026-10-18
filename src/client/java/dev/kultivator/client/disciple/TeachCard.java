package dev.kultivator.client.disciple;

import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One of the master's techniques as the disciple's Teknik tab sees it (DESIGN 22.13.6), and how its seal card looks in
 * teach mode: bright when it can be taught, stamped with a round red seal when the disciple already masters it, grey
 * with a padlock when it cannot be taught; the slot number when it sits in the disciple's combat slots.
 */
final class TeachCard {

	/**
	 * The server's word on one art: can it be taught now, does the disciple know it, why not (a translation key or text),
	 * the disciple's own qi cost and cooldown (−1 when unknown) and its mastery level.
	 */
	record Entry(TechniqueSpec spec, boolean ok, boolean learned, @Nullable Component reason, double qiCost, int cooldown, int mastery) {
	}

	static SealCard.Look look(@Nullable Entry entry, int slot, boolean selected) {
		if (entry == null) {
			return SealCard.Look.LOCKED;
		}
		boolean bright = entry.learned() || entry.ok();
		return new SealCard.Look(bright, !bright, selected, 0L, 0, entry.learned() ? entry.mastery() : 0, slot, false, entry.learned());
	}

	private TeachCard() {
	}
}
