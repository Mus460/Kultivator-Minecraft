package dev.kultivator.sound;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * The crowd ambience of inhabited places ("Kota yang Hidup", docs/progres/suara-kota.md): one seamless stereo loop per
 * kind of place under {@code assets/kultivator/sounds/ambient/kota/}, event {@code kultivator:ambient.kota.<suasana>},
 * built by {@code tools/gen_riuh_kota.py} (VOICEVOX walla, Kenney and OpenGameArt CC0 foley; see docs/CREDITS.md).
 * Registered on both sides from the main initializer; only the client plays them ({@code client/dunia/tempat/hidup/RiuhKota}),
 * never the server, so they cost no traffic. The order is the client's {@code Suasana} order.
 */
public final class SuaraKota {

	public static final SoundEvent PASAR = buat("pasar");
	public static final SoundEvent KEDAI = buat("kedai");
	public static final SoundEvent PELABUHAN = buat("pelabuhan");
	public static final SoundEvent JALAN = buat("jalan");
	public static final SoundEvent KUIL = buat("kuil");

	/** Every loop, in the client's {@code Suasana} order. */
	public static final List<SoundEvent> RIUH = List.of(PASAR, KEDAI, PELABUHAN, JALAN, KUIL);

	private static boolean registered;

	private static SoundEvent buat(String id) {
		return SoundEvent.createVariableRangeEvent(Kultivator.id("ambient.kota." + id));
	}

	/** Registers every event once (called by {@code Kultivator.onInitialize}). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		for (SoundEvent event : RIUH) {
			Registry.register(BuiltInRegistries.SOUND_EVENT, event.location(), event);
		}
	}

	private SuaraKota() {
	}
}
