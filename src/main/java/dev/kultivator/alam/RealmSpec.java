package dev.kultivator.alam;

import dev.kultivator.data.Realm;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * One secret realm (DESIGN 25.3.2): who may enter, the time limit, the cooldown and the range the guard formula's R is
 * clamped into (25.3.5). Text keys: {@code kultivator.alam.realm.<id>} (name), {@code .source}, {@code .reward}.
 *
 * @param id          the realm id ({@code blood_fire_trial}, ...)
 * @param ordinal     1-4
 * @param minRealm    the lowest player realm allowed
 * @param maxRealm    the highest player realm allowed (null: no upper bound)
 * @param timeTicks   the run's time limit (realm 4: per layer)
 * @param perLayer    true when {@link #timeTicks} is per layer (realm 4)
 * @param cooldown    ticks of the cooldown after a run (0 = none)
 * @param minR        lowest R of the guard formula
 * @param maxR        highest R before the realm 4 additions
 * @param color       the realm's colour (UI, bars, effects)
 * @param keyNeed     how many keys a gate needs in the inventory
 */
public record RealmSpec(String id, int ordinal, Realm minRealm, @Nullable Realm maxRealm, int timeTicks, boolean perLayer, long cooldown,
						int minR, int maxR, int color, int keyNeed) {

	/** The realm's name, in its colour. */
	public MutableComponent displayName() {
		return Component.translatable("kultivator.alam.realm." + id).withColor(color);
	}

	/** True for the endless Makam Langit. */
	public boolean endless() {
		return perLayer;
	}
}
