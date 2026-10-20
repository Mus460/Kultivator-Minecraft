package dev.kultivator.voice;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every {@code kultivator:voice.*} sound event of the Japanese voice lines (VOICEVOX, see {@code docs/CREDITS.md}) and the
 * non-verbal vocal clips. The list matches the {@code voice.*} entries of {@code assets/kultivator/sounds.json} written by
 * {@code tools/gen_voices.py}; keys here are the event id without the {@code voice.} prefix ({@code heaven.realm},
 * {@code disciple.f2.greet}, {@code grunt.male.hurt}). Registered on both sides from {@link Voices#init}.
 */
public final class VoiceEvents {

	public static final List<String> HEAVEN = List.of("breakthrough", "realm", "tribulation_begin", "tribulation_pass", "tribulation_fail",
			"immortal_ascend", "true_immortal", "dao_insight", "sect_founded", "witness", "decree", "decree_sword", "decree_spear", "decree_stars",
			"decree_lift", "wrath", "calamity", "secret_realm");
	public static final List<String> DEMON = List.of("waver", "tempt", "win", "lose", "whisper", "laugh", "growl", "death");
	public static final List<String> BOSS = List.of("awaken", "taunt", "enraged", "defeat", "roar");
	public static final List<String> ELDER = List.of("welcome", "advice", "inheritance");
	public static final List<String> ROGUE = List.of("ambush", "flee", "defeat");
	/** The six disciple voices: three male, three female. */
	public static final List<String> DISCIPLE_VOICES = List.of("m1", "m2", "m3", "f1", "f2", "f3");
	public static final List<String> DISCIPLE = List.of("greet", "greet_alt", "thanks", "breakthrough", "cheer", "careful", "protect", "attack",
			"attack_alt", "technique", "victory", "fear", "tired", "cultivate", "loyal", "formation", "injured", "farewell");
	public static final List<String> GRUNTS = List.of("male.attack", "male.hurt", "male.death", "female.attack", "female.hurt");
	/** Lines without words (no caption): laughs, growls, roars and grunts. */
	private static final List<String> NON_VERBAL = List.of("demon.laugh", "demon.growl", "demon.death", "boss.roar");

	private static final Map<String, SoundEvent> EVENTS = new LinkedHashMap<>();
	private static boolean registered;

	static {
		group("heaven", HEAVEN);
		group("demon", DEMON);
		group("boss", BOSS);
		group("elder", ELDER);
		group("rogue", ROGUE);
		for (String voice : DISCIPLE_VOICES) {
			group("disciple." + voice, DISCIPLE);
		}
		group("grunt", GRUNTS);
	}

	private static void group(String prefix, List<String> keys) {
		for (String key : keys) {
			String id = prefix + "." + key;
			EVENTS.put(id, SoundEvent.createVariableRangeEvent(Kultivator.id("voice." + id)));
		}
	}

	/** Registers every event once (both physical sides). */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		for (SoundEvent event : EVENTS.values()) {
			Registry.register(BuiltInRegistries.SOUND_EVENT, event.location(), event);
		}
	}

	/** The key without the {@code voice.} prefix (both forms are accepted). */
	public static String normalize(String key) {
		if (key == null) {
			return "";
		}
		String k = key;
		if (k.startsWith("kultivator:")) {
			k = k.substring("kultivator:".length());
		}
		return k.startsWith("voice.") ? k.substring("voice.".length()) : k;
	}

	/** True when {@code key} (with or without {@code voice.}) names a registered voice event. */
	public static boolean exists(String key) {
		return EVENTS.containsKey(normalize(key));
	}

	public static @Nullable SoundEvent get(String key) {
		return EVENTS.get(normalize(key));
	}

	/** The sound event id, e.g. {@code kultivator:voice.heaven.realm}. */
	public static Identifier id(String key) {
		return Kultivator.id("voice." + normalize(key));
	}

	/** The caption key of a worded line ({@code kultivator.voice.heaven.realm}, {@code kultivator.voice.disciple.greet}); null for grunts. */
	public static @Nullable String captionOf(String key) {
		String k = normalize(key);
		if (k.startsWith("grunt.") || NON_VERBAL.contains(k)) {
			return null;
		}
		if (k.startsWith("disciple.")) {
			int dot = k.indexOf('.', "disciple.".length());
			return dot < 0 ? null : "kultivator.voice.disciple." + k.substring(dot + 1);
		}
		return "kultivator.voice." + k;
	}

	/** Every key, in registration order. */
	public static List<String> keys() {
		return Collections.unmodifiableList(new ArrayList<>(EVENTS.keySet()));
	}

	private VoiceEvents() {
	}
}
