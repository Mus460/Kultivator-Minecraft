package dev.kultivator.duel.voice;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.clash.Fighter;
import dev.kultivator.duel.clash.Fighters;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceDisciples;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The voices of the sword duel on the server (DESIGN 35.6.2, 35.6.3, contract C35.S4). Module S calls these; they only
 * send voice lines through the existing {@link Voice} API (which every receiving client plays on the "Voice/Speech"
 * slider, never {@code MASTER}, and mutes with "Suara bicara"). Nothing here touches gameplay.
 * <ul>
 * <li>{@link #kiai}: the shout of a heavy cut released, a Deathblow, a won Blade Lock or a Blade Step
 * ({@code grunt.*.attack}), and the gasp of a broken posture ({@code grunt.*.hurt}). A player only shouts when his
 * client chose a kiai voice ({@code DuelPrefs.kiai}, read from his {@code Fighter}); a disciple uses its own grunt; an
 * NPC the {@code voiceKiai} of its profile. One shout per 1.5 s per fighter.</li>
 * <li>{@link #cheer}: one or two seated disciples of the sect arena cheer ({@code disciple.<voice>.cheer}), and the whole
 * seated crowd claps or cheers as a Sosok body clip (the one place here that moves a body, still no gameplay).</li>
 * <li>{@link #npcLine}: a disciple's or NPC's spoken line at the opening, victory or defeat of a duel.</li>
 * </ul>
 */
public final class DuelVoices {

	/** Ticks between two kiai of the same fighter (35.6.2: {@code Voice.ready(uuid, "duel_kiai", 30)}). */
	private static final int KIAI_GAP = 30;
	/** Cooldown of a spectator's cheer line. */
	private static final int CHEER_GAP = 160;
	/** Cooldown of an NPC's duel line. */
	private static final int LINE_GAP = 200;
	private static final RandomSource RANDOM = RandomSource.create();

	/**
	 * {@code entity} shouts for {@code what} ({@code DuelIds.VOICE_HEAVY}, {@code VOICE_DEATHBLOW}, {@code VOICE_BIND_WIN},
	 * {@code VOICE_MIKIRI} attack shouts; {@code VOICE_HURT} the posture-break gasp).
	 */
	public static void kiai(@Nullable LivingEntity entity, int what) {
		if (entity == null || entity.isRemoved() || !(entity.level() instanceof ServerLevel)) {
			return;
		}
		boolean hurt = what == DuelIds.VOICE_HURT;
		try {
			if (entity instanceof ServerPlayer player) {
				Fighter fighter = Fighters.get(player);
				int kiai = fighter == null ? DuelIds.KIAI_OFF : fighter.kiai();
				if (kiai == DuelIds.KIAI_OFF || !Voice.ready(player.getUUID(), "duel_kiai", KIAI_GAP)) {
					return;
				}
				String voice = kiai == DuelIds.KIAI_FEMALE ? "grunt.female." : "grunt.male.";
				Voice.say(player, voice + (hurt ? "hurt" : "attack"), "", 0.96F + RANDOM.nextFloat() * 0.08F);
				return;
			}
			if (entity instanceof DiscipleEntity disciple) {
				// VoiceDisciples has its own 40-tick grunt gap.
				VoiceDisciples.grunt(disciple, hurt ? "hurt" : "attack");
				return;
			}
			if (!Voice.ready(entity.getUUID(), "duel_kiai", KIAI_GAP)) {
				return;
			}
			DuelistProfile profile = DuelistRegistry.profile(entity);
			String key = profile == null || profile.voiceKiai() == null || profile.voiceKiai().isEmpty() ? "grunt.male.attack" : profile.voiceKiai();
			if (hurt) {
				key = key.contains("female") ? "grunt.female.hurt" : key.startsWith("grunt.") ? "grunt.male.hurt" : key;
			}
			if (VoiceEvents.exists(key)) {
				Voice.say(entity, key, "", 0.94F + RANDOM.nextFloat() * 0.1F);
			}
		} catch (RuntimeException | LinkageError error) {
			dev.kultivator.Kultivator.LOGGER.debug("Kultivator duel: kiai skipped", error);
		}
	}

	/**
	 * The seated disciples of {@code session} cheer for {@code moment} ({@code DuelIds.CHEER_CHAIN}, {@code CHEER_PERFECT},
	 * {@code CHEER_GUARD_BREAK}, {@code CHEER_END}): one or two of them (two at the end), picked at random among the
	 * spectators within the ring + 6 blocks.
	 */
	public static void cheer(@Nullable DuelSession session, int moment) {
		if (session == null) {
			return;
		}
		try {
			LivingEntity a = session.a();
			LivingEntity b = session.b();
			LivingEntity any = a != null ? a : b;
			if (any == null || !(any.level() instanceof ServerLevel level)) {
				return;
			}
			Vec3 center = session.ring() != null ? session.ring().center() : any.position();
			double radius = (session.ring() != null ? session.ring().radius() : 9.0F) + 6.0D;
			List<DiscipleEntity> crowd = new ArrayList<>();
			for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, new AABB(center, center).inflate(radius, 4.0D, radius))) {
				if (disciple != a && disciple != b && disciple.isAlive() && disciple.activity() == DiscipleEntity.ACTIVITY_SPECTATOR) {
					crowd.add(disciple);
				}
			}
			if (crowd.isEmpty()) {
				return;
			}
			// the whole crowd applauds from its seats (a win: they cheer with a raised arm); the voices below stay one or two
			boolean end = moment == DuelIds.CHEER_END;
			for (DiscipleEntity disciple : crowd) {
				SosokApi.gerak(disciple, end ? GerakId.SORAK_ARENA : GerakId.TEPUK_TANGAN, 0, end ? 80 : 30);
			}
			int count = end ? 2 : 1 + RANDOM.nextInt(2);
			for (int i = 0; i < count && !crowd.isEmpty(); i++) {
				DiscipleEntity disciple = crowd.remove(RANDOM.nextInt(crowd.size()));
				VoiceDisciples.line(disciple, "cheer", CHEER_GAP);
			}
		} catch (RuntimeException | LinkageError error) {
			dev.kultivator.Kultivator.LOGGER.debug("Kultivator duel: cheer skipped", error);
		}
	}

	/**
	 * {@code npc} says {@code line}: a disciple line name ({@code DuelIds.LINE_OPEN} "attack", {@code LINE_OPEN_ALT},
	 * {@code LINE_VICTORY} "victory", {@code LINE_DEFEAT} "injured") or a full voice key (e.g. {@code kota.rival_claim}).
	 * A rogue cultivator answers with its own lines ({@code rogue.ambush} at the opening, {@code rogue.defeat} on a defeat).
	 */
	public static void npcLine(@Nullable LivingEntity npc, @Nullable String line) {
		if (npc == null || line == null || line.isEmpty() || npc.isRemoved() || !(npc.level() instanceof ServerLevel)) {
			return;
		}
		try {
			if (npc instanceof DiscipleEntity disciple) {
				if (VoiceEvents.DISCIPLE.contains(line)) {
					VoiceDisciples.line(disciple, line, LINE_GAP);
				}
				return;
			}
			if (npc instanceof ServerPlayer) {
				return;
			}
			if (!Voice.ready(npc.getUUID(), "duel_line:" + line, LINE_GAP)) {
				return;
			}
			if (npc instanceof RogueCultivatorEntity) {
				String key = switch (line) {
					case DuelIds.LINE_OPEN, DuelIds.LINE_OPEN_ALT -> "rogue.ambush";
					case DuelIds.LINE_DEFEAT -> "rogue.defeat";
					default -> null;
				};
				if (key != null && VoiceEvents.exists(key)) {
					Voice.say(npc, key, null);
				}
				return;
			}
			if (VoiceEvents.exists(line)) {
				Voice.say(npc, line, null);
				return;
			}
			// A wandering cultivator of the Dunia (or any other NPC duellist): the phase-34 Japanese lines when this build
			// has them (kota.rival_claim at the opening, kota.wanderer_dao for the second opening line, kota.immortal_scorn
			// for a victory), else a reused line with the duel's own caption.
			String own = switch (line) {
				case DuelIds.LINE_OPEN -> "kota.rival_claim";
				case DuelIds.LINE_OPEN_ALT -> "kota.wanderer_dao";
				case DuelIds.LINE_VICTORY -> "kota.immortal_scorn";
				default -> null;
			};
			if (own != null && VoiceEvents.exists(own)) {
				Voice.say(npc, own, null);
				return;
			}
			String[] reused = switch (line) {
				case DuelIds.LINE_OPEN -> new String[]{"boss.taunt", "kultivator.duel.ui.voice.open"};
				case DuelIds.LINE_OPEN_ALT -> new String[]{"elder.advice", "kultivator.duel.ui.voice.open_alt"};
				case DuelIds.LINE_VICTORY -> new String[]{"boss.taunt", "kultivator.duel.ui.voice.victory"};
				case DuelIds.LINE_DEFEAT -> new String[]{"rogue.defeat", "kultivator.duel.ui.voice.defeat"};
				default -> null;
			};
			if (reused != null && VoiceEvents.exists(reused[0])) {
				Voice.say(npc, reused[0], reused[1]);
			}
		} catch (RuntimeException | LinkageError error) {
			dev.kultivator.Kultivator.LOGGER.debug("Kultivator duel: line skipped", error);
		}
	}

	private DuelVoices() {
	}
}
