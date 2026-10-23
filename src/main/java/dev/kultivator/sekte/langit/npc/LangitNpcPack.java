package dev.kultivator.sekte.langit.npc;

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
 * The floating palaces of the NPC sky sects in world generation (DESIGN 37.7.6, pattern {@code sekte.diplomasi.GerbangSekte}).
 * The jigsaw structure {@code kultivator:sekte_langit/istana_langit} and its templates (made by
 * {@code tools/gen_sekte_langit.py}) live in the base data; its two structure sets {@code sekte_langit/istana_langit_0/1}
 * use the placement {@code kultivator:sekte_langit} ({@link LangitSektePlacement}) and live in the built-in pack
 * {@code kultivator:sekte_langit} ({@code resourcepacks/sekte_langit}), registered only while Sekte Langit and
 * {@code sekteLangit.strukturNpc} are on, so a world never meets a set whose placement type is unknown. The placement type
 * itself is always registered (K1). The palace anchors ({@code sektelangit/*}) are handled by {@link LangitNpcAnchors}.
 * No runtime code places a block of these palaces.
 */
public final class LangitNpcPack {

	public static final Identifier PLACEMENT = Kultivator.id("sekte_langit");
	public static final Identifier PACK = Kultivator.id("sekte_langit");

	private static boolean registered;
	private static boolean running;

	/** The placement type exists whatever the switches say. */
	public static void registerContent() {
		if (registered) {
			return;
		}
		registered = true;
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, PLACEMENT, LangitSektePlacement.CODEC);
	}

	/** The palace anchors and, with {@code structures}, the world generation pack. */
	public static void register(boolean structures) {
		registerContent();
		if (running) {
			return;
		}
		running = true;
		DuniaAnchors.register(LangitNpcAnchors.MODULE, LangitNpcAnchors.HANDLER);
		if (!structures) {
			return;
		}
		Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(Kultivator.MOD_ID);
		boolean ok = false;
		if (mod.isPresent()) {
			try {
				ok = ResourceLoader.registerBuiltinPack(PACK, mod.get(), Component.translatable("kultivator.sekte_langit.npc.pack"),
						PackActivationType.DEFAULT_ENABLED);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Sekte Langit world generation pack could not be registered", exception);
			}
		}
		if (!ok) {
			Kultivator.LOGGER.warn("Sekte Langit world generation pack sekte_langit is missing; NPC sky palaces will not generate");
		}
	}

	private LangitNpcPack() {
	}
}
