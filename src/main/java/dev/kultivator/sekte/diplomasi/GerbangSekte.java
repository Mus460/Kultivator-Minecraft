package dev.kultivator.sekte.diplomasi;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaAnchors;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * The mountain gates of the NPC sects (DESIGN 36.2.9, contract C36.D4): world generation only, in new chunks. Three
 * data-driven jigsaw structures {@code kultivator:sekte_gerbang_kecil/sedang/besar} (templates in
 * {@code data/kultivator/structure/sekte/}, made by {@code tools/gen_sekte_gerbang.py} with {@code tools/nbt.py} and
 * {@code tools/structgen.py}) live in the base data; their three structure sets, which use the placement
 * {@code kultivator:gerbang_sekte} ({@link GerbangSektePlacement}), live in the built-in pack
 * {@code kultivator:sekte_gerbang} ({@code resourcepacks/sekte_gerbang}), registered only while the module and
 * {@code diplomasi.strukturAktif} are on, so a world never meets a structure set whose placement type is not registered.
 * The gate's anchors are handled by {@link AnggotaSekte} through the Dunia anchor spine ({@code DuniaAnchors}).
 * No runtime code here or anywhere in the module places a block.
 */
final class GerbangSekte {

	public static final Identifier PLACEMENT = Kultivator.id("gerbang_sekte");
	public static final Identifier PACK = Kultivator.id("sekte_gerbang");

	private static boolean registered;

	static void register(boolean structures) {
		if (registered) {
			return;
		}
		registered = true;
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, PLACEMENT, GerbangSektePlacement.CODEC);
		DuniaAnchors.register(AnggotaSekte.MODULE, AnggotaSekte.HANDLER);
		if (!structures) {
			return;
		}
		Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(Kultivator.MOD_ID);
		boolean ok = false;
		if (mod.isPresent()) {
			try {
				ok = ResourceLoader.registerBuiltinPack(PACK, mod.get(), Component.translatable(GeneratorNama.KEY + "pack"),
						PackActivationType.DEFAULT_ENABLED);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Sekte gate world generation pack could not be registered", exception);
			}
		}
		if (!ok) {
			Kultivator.LOGGER.warn("Sekte gate world generation pack sekte_gerbang is missing; NPC sect gates will not generate");
		}
	}

	private GerbangSekte() {
	}
}
