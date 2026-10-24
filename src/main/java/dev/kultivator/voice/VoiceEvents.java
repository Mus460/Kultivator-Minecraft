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
	/** Phase 34 (Dunia Kultivasi yang Luas) lines, one group per module. */
	public static final List<String> KOTA = List.of("auction_open", "auction_sold", "tea_welcome", "tea_news", "black_whisper", "guard_halt",
			"wanderer_dao", "rival_claim", "immortal_scorn", "lecture", "envoy_pleased", "peach_keeper");
	public static final List<String> PENJARA = List.of("free_at_last", "resealed");
	public static final List<String> MENARA = List.of("next_floor", "shadow_self");
	public static final List<String> GUA = List.of("ancestor_trial");
	public static final List<String> HEAVEN_LANGIT = List.of("meteor_shower", "twin_moons", "treasure_born", "blood_eclipse");
	public static final List<String> NAGA = List.of("subdued", "tribulation", "evolved", "trib_failed", "jiao_roar");
	public static final List<String> BAWAH = List.of("meng_po_offer", "meng_po_refuse", "yama_judgement", "yama_book", "ox_head", "ghost_plea");
	public static final List<String> LAUT = List.of("dragon_king", "gate_guard", "penglai_trial");
	public static final List<String> KAPAL = List.of("launch", "cloud_leap", "arrive", "sky_pirates", "hull_broken");
	public static final List<String> PETA = List.of("site_found");
	/** Sekte yang Hidup (DESIGN 36.6, docs/progres/sekte-voices.md): the disciples' new moments of the ten modules. */
	public static final List<String> DISCIPLE_SEKTE = List.of("sulk", "rebel", "submit", "leave_night", "oath", "tea", "grudge", "beloved",
			"leitai_salam", "leitai_juara", "leitai_kalah", "climb_peak", "climb_illusion", "mourn", "study", "qingming", "report", "depart",
			"mission_report", "abandoned", "robe", "envoy_return", "harvest", "ore_found");
	/** Sekte yang Hidup: the elder's tournament, banner and envoy lines. */
	public static final List<String> ELDER_SEKTE = List.of("leitai_buka", "banner", "envoy");
	/** Sekte yang Hidup: the visiting sect, the NPC sect master and his defeat. */
	public static final List<String> BOSS_SEKTE = List.of("tamu_tantang", "sect_master", "sect_master_defeat");
	/** DESIGN 38.B.8 (38ST): the capital's gates, law, bank, gambling house, guilds, inn, temple, dock and events. */
	public static final List<String> KOTABESAR = List.of("gate_welcome", "curfew", "suspect", "wanted", "fine_paid", "jail", "bank_welcome",
			"bank_note", "gamble_cut", "gamble_win", "gamble_lose", "grand_auction", "alchemy_start", "alchemy_winner", "smith_master",
			"guild_bounty", "guild_rank", "recruit_call", "beast_market", "inn_welcome", "library_hush", "temple_bell", "teleport_ready",
			"dock_depart", "palace_audience", "lantern_festival", "riddle_right", "beast_tide", "defense_won", "tournament_open");
	/** DESIGN 38.S.6 (38ST): villages, clans, hermits, tombs, lairs, roads, sect compounds and repairs. */
	public static final List<String> TEMPAT = List.of("village_elder", "village_fear", "bill_request", "bill_paid", "bill_refused",
			"repair_work", "clan_patriarch", "young_master", "hermit", "hermit_koan", "inheritance", "inheritance_pass", "demon_hideout",
			"captive_saved", "waystation", "peddler", "bandit", "tribulation_peak", "sect_visit", "child_play");
	/** DESIGN 38.S.6 (38ST): the capital arc "Bayangan di Balik Takhta". */
	public static final List<String> CERITA = List.of("ruyi_thanks", "moying_reveal", "bao_verdict", "coronation");
	/** DESIGN 38.T.12 (38ST): the themed cities, one group per theme ({@code tema.<tema>.<line>}). */
	public static final List<String> TEMA_PELABUHAN = List.of("welcome", "fish_call", "ship_arrive", "ship_depart", "lighthouse", "pirates",
			"black_shark", "sea_crossing", "goddess_day", "shipwright", "lamp_lit");
	public static final List<String> TEMA_OASIS = List.of("welcome", "bazaar", "haggle_no", "haggle_yes", "caravan", "sandstorm",
			"water_priestess", "lord", "scorpion", "dance");
	public static final List<String> TEMA_AIR = List.of("welcome", "boat_song", "silk", "tea", "thief", "lantern_night", "ghost", "ghost_rest",
			"dragon_boat", "poet");
	public static final List<String> TEMA_GUNUNG = List.of("welcome", "porter", "lift", "lift_stuck", "monkey", "rockfall", "abbot", "pilgrim",
			"buddha_light", "cloud_bell");
	public static final List<String> TEMA_IBLIS = List.of("gate_toll", "gate_weak", "pact", "hunt_hour", "arena_open", "arena_win", "broker",
			"forbidden", "captive", "lord", "hunter", "raid", "dawn");
	public static final List<String> TEMA_DAGANG = List.of("sold", "saturated");
	/** DESIGN 39.8.4 (39E): autonomous disciples (town trips, seclusion, self-made breakthroughs, captivity, the demonic path). */
	public static final List<String> DISCIPLE_MANDIRI = List.of("pamit", "pulang_belanja", "tawar", "tawar_sepakat", "lihat_lapak", "copet",
			"makan", "latih", "salam_pagi", "tutup_masuk", "tutup_keluar", "hufa_minta", "hufa_jaga", "tembus_sendiri", "hati_lulus", "dao_pilih",
			"belajar_sendiri", "serap_batu", "hadiah", "tertawan", "diselamatkan", "iblis_bisik", "ditebus", "jalan_ganti");
	/** DESIGN 39.F.7 (39F, scripted by 39E): inter-sect feuds; rival sect bodies speak with the same six voices. */
	public static final List<String> DISCIPLE_SETERU = List.of("seteru_ejek", "seteru_ejek_guru", "seteru_balas", "seteru_tantang",
			"seteru_terima", "seteru_menang", "seteru_kalah", "seteru_keroyok", "seteru_sergap", "seteru_lindungi", "seteru_curi", "seteru_maaf",
			"seteru_sumpah", "seteru_damai", "seteru_saingan", "seteru_kekasih");
	/** DESIGN 39.F.7: the elder who breaks up a feud and pays for the damage. */
	public static final List<String> ELDER_SETERU = List.of("seteru_lerai", "seteru_ganti_rugi");
	/** DESIGN 37 (docs/progres/langit-voices.md): Heaven's line when a sect rises above the clouds. */
	public static final List<String> HEAVEN_SEKTE_LANGIT = List.of("langit_naik");
	/** DESIGN 37: the cloud pirate captain, the Longbo giant, the guest elder and the Heavenly Court envoy. */
	public static final List<String> SEKTE_LANGIT = List.of("bajak_datang", "longbo_datang", "longbo_kalah", "tamu_salam", "utusan_salam",
			"utusan_tantang");
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
			group("disciple." + voice, DISCIPLE_SEKTE);
			group("disciple." + voice, DISCIPLE_MANDIRI);
			group("disciple." + voice, DISCIPLE_SETERU);
		}
		group("elder", ELDER_SEKTE);
		group("elder", ELDER_SETERU);
		group("heaven", HEAVEN_SEKTE_LANGIT);
		group("sekte_langit", SEKTE_LANGIT);
		group("boss", BOSS_SEKTE);
		group("grunt", GRUNTS);
		group("kota", KOTA);
		group("penjara", PENJARA);
		group("menara", MENARA);
		group("gua", GUA);
		group("heaven.langit", HEAVEN_LANGIT);
		group("naga", NAGA);
		group("bawah", BAWAH);
		group("laut", LAUT);
		group("kapal", KAPAL);
		group("peta", PETA);
		group("kotabesar", KOTABESAR);
		group("tempat", TEMPAT);
		group("cerita", CERITA);
		group("tema.pelabuhan", TEMA_PELABUHAN);
		group("tema.oasis", TEMA_OASIS);
		group("tema.air", TEMA_AIR);
		group("tema.gunung", TEMA_GUNUNG);
		group("tema.iblis", TEMA_IBLIS);
		group("tema.dagang", TEMA_DAGANG);
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
