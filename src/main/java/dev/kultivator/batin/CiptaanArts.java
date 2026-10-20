package dev.kultivator.batin;

import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.minecraft.sounds.SoundEvents;

import static dev.kultivator.data.TechniqueSpec.Flag.NO_ECHO;
import static dev.kultivator.data.TechniqueSpec.Flag.NO_TEACH;
import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Jurus Ciptaan Sendiri (DESIGN 26.7.2, owned by P9-batin): the three fixed specs {@code own_art_1..3} in the net-id block
 * 1100-1199 ({@code TechniqueRegistry.module("CiptaanArts", 1100, 1199, CiptaanArts::register)}, before {@code freeze()}).
 * Each is a Dewa PAMUNGKAS with no Dao, no route and no loot ({@code NO_TEACH}); the player learns one only by creating it
 * ({@code data.learn("own_art_i")}). The spec's own timeline is an empty keep-alive step of {@link #KEEP_ALIVE_TICKS};
 * the special handler {@link OwnArt} builds the stored composition at cast time through {@code CastContext}.
 * <p>
 * Filled by P9-batin: ids, net ids and flags are those of the spine (DESIGN 26.14 item 15); the composition is run by
 * {@link OwnArt} and drawn in the Dao colours by {@code client/batin/CiptaanFx}.
 * Lang keys ({@code .desc}, {@code .source}, {@code .lore}) are in {@code lang-src/arts_ciptaan.*}.
 */
public final class CiptaanArts {

	/** The first net id of the block. */
	public static final int NET_FIRST = 1100;
	/** The three spec ids, in slot order (slot 0 = {@code own_art_1}). */
	public static final String[] IDS = {"own_art_1", "own_art_2", "own_art_3"};
	public static final int SLOTS = IDS.length;
	public static final int KEEP_ALIVE_TICKS = 40;
	public static final double QI_PCT = 70.0D;
	public static final double COOLDOWN_SECONDS = 480.0D;

	private static final int GOLD = 0xFFD54A;
	private static final int WHITE = 0xF4F4FF;

	public static void register(TechniqueRegistry.Sink sink) {
		sink.special(OwnArt.ID, OwnArt.STUB);
		for (int i = 0; i < SLOTS; i++) {
			sink.add(art(IDS[i], NET_FIRST + i, null)
					.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
					.special(OwnArt.ID)
					.flag(NO_TEACH, NO_ECHO)
					.qiPct(QI_PCT).cooldown(COOLDOWN_SECONDS)
					// the body of each form (DESIGN 27.2): a summoning raise, then the form's own strike
					.pose(Pose.SUMMON_RAISE)
					.pose(Phase.extra(OwnArt.PHASE_CUT), Pose.SWORD_SLASH).pose(Phase.extra(OwnArt.PHASE_BURST), Pose.TWIN_PALM)
					.pose(Phase.extra(OwnArt.PHASE_RAIN), Pose.SKY_RAISE).pose(Phase.extra(OwnArt.PHASE_WAVE), Pose.STOMP)
					.pose(Phase.extra(OwnArt.PHASE_DOMAIN), Pose.ARMS_SPREAD).pose(Phase.extra(OwnArt.PHASE_SEAL), Pose.SEAL)
					.climax(Phase.extra(1))
					.step(at(KEEP_ALIVE_TICKS))
					// The real shapes and colours come from the stored composition (client/batin/CiptaanFx); these layers keep
					// the audit's CAST FX and SHAKE rules and give a cast without a composition a visible answer.
					.fx(Phase.CAST, runeCircle(GOLD).count(24).ticks(20), shell(WHITE).onCaster().count(16).ticks(10))
					// EXTRA1: the Daos' domain pulses and the pair row burst at the aim (OwnArt.burst): the climax, one shake.
					.fx(Phase.extra(1), impact(WHITE).count(16).ticks(8), shockRing(GOLD).radius(16.0F).count(60).ticks(16), flash(WHITE, 0.2F, 5),
							shake(0.8F, 20))
					// the forms (the client paints them in the stored Daos' colours: FLAG_TWO_DAO and CiptaanFx)
					.fx(Phase.extra(OwnArt.PHASE_CUT), beamCore(GOLD).count(60).ticks(10), crescentFly(WHITE).vertical().radius(4.0F).count(30).ticks(8),
							shake(0.3F, 8))
					.fx(Phase.extra(OwnArt.PHASE_BURST), shell(GOLD).radius(12.0F).count(80).ticks(12), shockRing(WHITE).radius(12.0F).count(40).ticks(12),
							shake(0.6F, 14))
					.fx(Phase.extra(OwnArt.PHASE_RAIN), impact(GOLD).count(10).ticks(8), column(WHITE).count(8).ticks(6).detail())
					.fx(Phase.extra(OwnArt.PHASE_WAVE), crescentFly(GOLD).vertical().radius(8.0F).count(60).ticks(16), shake(0.4F, 10))
					.fx(Phase.extra(OwnArt.PHASE_DOMAIN), dome(GOLD).count(30).ticks(16), shockRing(WHITE).count(24).ticks(10).detail())
					.fx(Phase.extra(OwnArt.PHASE_SEAL), chains(GOLD).count(24).ticks(20), glyph("封").scale(2.0F).onTarget().above(1.5F).ticks(30),
							runeCircle(GOLD).onTarget().radius(2.0F).count(20).ticks(30).detail())
					.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.8F)
					.sound(Phase.extra(0), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 0.6F)
					// Phase 10 (DESIGN 28.12): heaven marks the aim in the first Dao's colour; the Dao circle and its signature prop
					.dampak(Dampak.builder().mark(Dampak.Mark.PILLAR).ride(Dampak.Ride.SHORT).vista(Dampak.Vista.BY_DAO)
							.frames(Dampak.DAO, Dampak.DAO2, 0xFFFFFF).silence(6).debris(Dampak.Debris.BURST)
							.sigil(SigilPattern.DAO_CIRCLE, 12.0F)
							.monument(Dampak.MONUMENT_BY_DAO, 6.0F, 60, Dampak.MonumentPose.UPRIGHT).build())
					// Mode Kehancuran only (the existing switch): the runner carves this bowl where a form strikes (caps and undo apply).
					.kehancuran(DShape.bowl(4))
					.build());
		}
	}

	/** The slot (0-2) of an own-art spec id, or -1. */
	public static int slotOf(String specId) {
		for (int i = 0; i < SLOTS; i++) {
			if (IDS[i].equals(specId)) {
				return i;
			}
		}
		return -1;
	}

	private CiptaanArts() {
	}
}
