package dev.kultivator.alchemy;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/** Data components of the alchemy module. Loaded (and so registered) by {@code Alchemy.init()} before the pill items. */
public final class AlchemyComponents {

	/** {@code kultivator:pill_quality}: the {@link PillQuality} ordinal 0..3 (saved and synced). Pill items default to Mid. */
	public static final DataComponentType<Integer> PILL_QUALITY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("pill_quality"), DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.intRange(0, PillQuality.values().length - 1))
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** Loads the class so the components above are registered. */
	public static void init() {
	}

	/** The quality of {@code stack} (Mid when it has none). */
	public static PillQuality quality(ItemStack stack) {
		return PillQuality.byOrdinal(stack.getOrDefault(PILL_QUALITY, PillQuality.MID.ordinal()));
	}

	/** Sets the quality and returns the same stack. */
	public static ItemStack withQuality(ItemStack stack, PillQuality quality) {
		stack.set(PILL_QUALITY, quality.ordinal());
		return stack;
	}

	private AlchemyComponents() {
	}
}
