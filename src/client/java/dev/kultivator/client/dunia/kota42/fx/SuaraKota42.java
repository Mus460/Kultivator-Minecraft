package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.SettingsRows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

/**
 * The sounds of 42V-F, played locally on the client only. The events ({@code kultivator:kota42.erhu.0 .. 11}, {@code .suona.N},
 * {@code .pipa.N}, {@code .dizi.N}, {@code .gong.0-2}, {@code .kentongan.0-2}, {@code .genderang_kecil}, {@code .balok_kayu},
 * {@code .petasan}, {@code .merpati_peluit}, and the beds {@code ambient.kota.malam_meriah} / {@code .upacara}) are made by
 * {@code tools/gen_kota_hidup_suara.py} and listed in {@code sounds.json} by the finish agent; the {@link SoundEvent}s here are
 * never registered (the server never plays them, so they cost no traffic and no registry id): the sound manager finds a sound by
 * its identifier in {@code sounds.json}. Until that entry exists the engine logs a missing sound and plays nothing, so the fx run
 * silently. Melodies obey the "Musik jalanan" setting ({@link SettingsRows#kota42Melodi}, 0-100 %). Client thread only.
 */
final class SuaraKota42 {

	private static final Map<String, SoundEvent> CACHE = new HashMap<>();

	/** The event {@code kultivator:<nama>} (for example {@code kota42.erhu.3}). */
	static SoundEvent acara(String nama) {
		return CACHE.computeIfAbsent(nama, k -> SoundEvent.createVariableRangeEvent(Kultivator.id(k)));
	}

	/** A melodic note of {@code instrumen} (erhu, suona, pipa, dizi), degree 0-11. */
	static SoundEvent nada(String instrumen, int derajat) {
		return acara("kota42." + instrumen + "." + Math.floorMod(derajat, 12));
	}

	/** The street music volume factor 0-1 of the setting. */
	static float musik() {
		return SettingsRows.kota42Melodi() / 100.0F;
	}

	/** True when {@code sounds.json} knows the event (so a missing sound is skipped quietly instead of logged on every note). */
	static boolean ada(SoundEvent e) {
		return Minecraft.getInstance().getSoundManager().getSoundEvent(e.location()) != null;
	}

	/** Plays {@code nama} at a point as street music (volume scaled by the setting; nothing at 0 %). */
	static void musik(ClientLevel level, double x, double y, double z, SoundEvent e, float volume, float pitch) {
		float v = volume * musik();
		if (v > 0.002F && ada(e)) {
			level.playLocalSound(x, y, z, e, SoundSource.RECORDS, v, pitch, false);
		}
	}

	/** Plays a festival or ambient sound at a point (not scaled by the music setting). */
	static void suara(ClientLevel level, double x, double y, double z, SoundEvent e, float volume, float pitch) {
		if (!ada(e)) {
			return;
		}
		level.playLocalSound(x, y, z, e, SoundSource.AMBIENT, volume, pitch, false);
	}

	private SuaraKota42() {
	}
}
