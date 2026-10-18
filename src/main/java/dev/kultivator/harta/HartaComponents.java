package dev.kultivator.harta;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

/**
 * Data components of P7-harta (DESIGN 19.9). Registered by {@code Harta.init()} before the items that carry them.
 */
public final class HartaComponents {

	/** Highest age a herb may carry (years). */
	public static final int MAX_HERB_AGE = 100_000;

	/** {@code kultivator:herb_age}: a spirit herb's age in years (saved and synced). */
	public static final DataComponentType<Integer> HERB_AGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("herb_age"), DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.intRange(0, MAX_HERB_AGE))
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** {@code kultivator:core_element}: the {@code Element} id of a beast core (saved and synced). */
	public static final DataComponentType<String> CORE_ELEMENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("core_element"), DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8)
					.build());

	/** Loads the class so the components above are registered. */
	public static void init() {
	}

	private HartaComponents() {
	}
}
