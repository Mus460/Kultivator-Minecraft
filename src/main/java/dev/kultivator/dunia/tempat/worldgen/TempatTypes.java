package dev.kultivator.dunia.tempat.worldgen;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.jalan.JalanPlacement;
import dev.kultivator.dunia.tempat.jalan.JalanStructure;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The world-generation types of DESIGN 38 (38.D.1), registered ALWAYS from {@code TempatSpine.init()} (38.0 rule 17):
 * config switches only turn the built-in worldgen packs on or off, never these registry entries.
 * <ul>
 *   <li>structure types {@code kultivator:tata_letak} ({@link TataLetakStructure}) and {@code kultivator:jalan}
 *       ({@link JalanStructure});</li>
 *   <li>placements {@code kultivator:cincin_tempat} ({@link CincinPlacement}), {@code kultivator:rencana_tempat}
 *       ({@link RencanaPlacement}) and {@code kultivator:jalan} ({@link JalanPlacement}).</li>
 * </ul>
 * Registration pattern verified against 26.3: {@code StructureType} is a functional interface returning its
 * {@code MapCodec} ({@code Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id, () -> codec)}); the placement
 * registry holds the {@code MapCodec} itself (as {@code JauhDariPusatPlacement}).
 */
public final class TempatTypes {

	public static final StructureType<TataLetakStructure> TATA_LETAK = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			Kultivator.id("tata_letak"), () -> TataLetakStructure.CODEC);
	public static final StructureType<JalanStructure> JALAN = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			Kultivator.id("jalan"), () -> JalanStructure.CODEC);

	private static boolean registered;

	/** From {@code TempatSpine.init()}; safe to call twice. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Kultivator.id("cincin_tempat"), CincinPlacement.CODEC);
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Kultivator.id("rencana_tempat"), RencanaPlacement.CODEC);
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, Kultivator.id("jalan"), JalanPlacement.CODEC);
	}

	private TempatTypes() {
	}
}
