package dev.kultivator.pusakalangit;

import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import dev.kultivator.technique.spec.dsl.SoundCue;
import net.minecraft.sounds.SoundEvents;

import static dev.kultivator.data.TechniqueSpec.Flag.NO_ECHO;
import static dev.kultivator.data.TechniqueSpec.Flag.NO_TEACH;
import static dev.kultivator.data.TechniqueSpec.Flag.TITAH;
import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * Phase 10b "Pusaka Langit" (DESIGN 29.3.1, 29.5.5): the four Titah Langit decrees in the net-id block 1200-1299
 * ({@code TechniqueRegistry.module("TitahArts", 1200, 1299, TitahArts::register)}, after CiptaanArts, before
 * {@code freeze()}). Each is a Dewa PAMUNGKAS with no Dao, flags {@code NO_TEACH}, {@code NO_ECHO} and {@code TITAH},
 * the special {@link TitahSkill#ID} and an empty keep-alive step; {@link TitahSkill} builds the timeline at cast time
 * from the per-cast {@link Titah.Settings} snapshot. Shared, plain data: the client reads the Dampak rows and the
 * TechFx {@code aux} helpers below, never hard-coded ids.
 * <p>
 * TechFx encoding of a Titah event (29.3.1): {@code radius} = R_vis (already clamped), {@code length} = the line length
 * (Pedang) or the square's half side (Gunung), {@code duration} = ticks to the climax on the CAST and ticks to land on a
 * pre-impact TRAVEL ({@code FLAG_PRE_IMPACT}), {@code count} = stars (7) or cells per side, {@code index}/{@code of} =
 * the star order of Bintang, {@code aux} = {@link #packAux}. Every Titah CAST carries the resolved crosshair point in
 * {@code tx,ty,tz}.
 * <p>
 * Lang keys ({@code kultivator.technique.<id>}, {@code .desc}, {@code .source}, {@code .lore}) are in
 * {@code lang-src/pusaka_langit.*}.
 */
public final class TitahArts {

	/** The first net id of the block. */
	public static final int NET_FIRST = 1200;
	/** The four spec ids in {@link Titah.Skill} order. */
	public static final String[] IDS = {"titah_pedang", "titah_tiang", "titah_bintang", "titah_gunung"};
	/** The spec timeline's own keep-alive step (the scheduled decree keeps the cast alive itself). */
	public static final int KEEP_ALIVE_TICKS = 40;
	/** qi of every decree: 85 % of max (the Dewa ultimate band 70-90). */
	public static final double QI_PCT = 85.0D;
	/** Authored cooldowns in seconds (Dewa band 300-900), before x sqrt(max(1, Kekuatan)). */
	public static final double[] COOLDOWN_SECONDS = {600.0D, 600.0D, 900.0D, 900.0D};
	/** Authored climax ticks at Kecepatan 1 (29.5). */
	public static final int[] CLIMAX = {100, 120, 140, 90};
	/** Base R_vis at Kekuatan 1 (29.3.2): Pedang ring / half line 48, Tiang ring 64, Bintang sigil 48, Gunung half side 32. */
	public static final float[] BASE_RADIUS = {48.0F, 64.0F, 48.0F, 32.0F};
	/** The decree glyphs 劍 柱 斗 山 (menu cards, title cards). */
	public static final String[] GLYPHS = {"劍", "柱", "斗", "山"};
	/** The planted weapon fx models of Pedang and Tiang ({@code kultivator:fx/<id>}, M3 assets). */
	public static final String SWORD_MODEL = "heaven_splitting_sword";
	public static final String PILLAR_MODEL = "dinghai_pillar";
	public static final String STAR_MODEL = "star_core";
	public static final String SEAL_MODEL = "yishan_seal";

	// ---- TechFx aux of a Titah event (29.3.1)
	/** Bits 0-9: Kecepatan × 100 (30-300). */
	public static final int AUX_SPEED_MASK = 0x3FF;
	/** Bit 10: a planted weapon (monument entity) is planned. */
	public static final int AUX_MONUMENT = 1 << 10;
	/** Bit 11: this cast really carves (Mode Kehancuran on and "Hancurkan tanah" = Ikut). */
	public static final int AUX_REAL = 1 << 11;
	/** Bits 12-13: the decree (Skill ordinal). */
	public static final int AUX_SKILL_SHIFT = 12;

	private static final int GOLD = 0xFFD54A;
	private static final int WHITE_GOLD = 0xFFF4D0;
	private static final int BLOOD = 0xFF2A1A;
	private static final int EMBER = 0xFF7A1A;
	private static final int WHITE = 0xFFFFFF;
	private static final int VIOLET = 0xB57CFF;
	private static final int NIGHT = 0x2A1050;
	private static final int JADE = 0x7CF0C0;
	private static final int SEAL_GOLD = 0xFFE8A0;
	private static final int SEAL_VIOLET = 0x6A4AFF;

	/** The {@code aux} of every Titah event. */
	public static int packAux(float speed, boolean monument, boolean real, int skill) {
		int v = Math.max(30, Math.min(300, Math.round(speed * 100.0F)));
		return v & AUX_SPEED_MASK | (monument ? AUX_MONUMENT : 0) | (real ? AUX_REAL : 0) | (Math.max(0, Math.min(3, skill)) << AUX_SKILL_SHIFT);
	}

	/** Kecepatan of an event's {@code aux} (0.3-3; 1 when unset). */
	public static float speed(int aux) {
		int v = aux & AUX_SPEED_MASK;
		return v <= 0 ? 1.0F : v / 100.0F;
	}

	public static boolean monument(int aux) {
		return (aux & AUX_MONUMENT) != 0;
	}

	public static boolean real(int aux) {
		return (aux & AUX_REAL) != 0;
	}

	/** The decree (Skill ordinal 0-3) of an event's {@code aux}. */
	public static int skill(int aux) {
		return aux >>> AUX_SKILL_SHIFT & 3;
	}

	/** The decree index (0-3) of a spec id, or -1. */
	public static int skillOf(String specId) {
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(specId)) {
				return i;
			}
		}
		return -1;
	}

	public static void register(TechniqueRegistry.Sink sink) {
		sink.special(TitahSkill.ID, TitahSkill.INSTANCE);
		sink.add(base(0)
				.pose(Pose.SWORD_SKY).pose(Phase.EXTRA3, Pose.SWORD_SLASH)
				.climax(Phase.IMPACT)
				// CAST: heaven's white-gold line along the aim, a pillar narrowing onto the impact (the mark itself is the client's)
				.fx(Phase.CAST, sky(0xFFE7B0), column(WHITE_GOLD).count(24).ticks(30))
				// EXTRA1: the gate opens above the impact (the gold ring tear is drawn by the client)
				.fx(Phase.EXTRA1, runeCircle(GOLD).radius(6.0F).count(32).ticks(40), glyph("劍").scale(4.0F).above(6.0F).ticks(40))
				// EXTRA2: the sword appears in the tear
				.fx(Phase.EXTRA2, skyTear(WHITE_GOLD).length(32.0F).ticks(40))
				// TRAVEL (pre-impact): the sword falls point-first down the pillar, a widening trail behind it
				.fx(Phase.TRAVEL, trail(WHITE_GOLD).count(40))
				// IMPACT: the climax (frames, silence, ring and debris come from the Dampak row)
				.fx(Phase.IMPACT, impact(WHITE).count(24).ticks(10), shards(WHITE_GOLD).count(30).ticks(20), shake(1.2F, 30))
				// EXTRA3: "Bumi terbelah", the cleaving front runs both ways along the aim
				.fx(Phase.EXTRA3, cracksAlways(BLOOD).count(40).ticks(60), mist(0x3A2A1A).count(24).ticks(30), shake(0.6F, 16))
				// EXTRA4: the second ring and the dust wall; the planted sword hums
				.fx(Phase.EXTRA4, shockRing(WHITE_GOLD).radius(16.0F).count(48).ticks(20), mist(0x5A4A3A).count(30).ticks(40))
				.sound(Phase.CAST, SoundEvents.BEACON_ACTIVATE, 1.6F, 0.6F)
				.sound(Phase.CAST, SoundCue.of(SoundEvents.BELL_BLOCK, 1.5F, 0.5F).delay(10))
				.sound(Phase.EXTRA1, SoundEvents.END_PORTAL_SPAWN, 1.2F, 1.4F)
				.sound(Phase.EXTRA2, SoundEvents.BELL_BLOCK, 1.6F, 0.7F)
				.sound(Phase.IMPACT, SoundEvents.TRIDENT_THUNDER, 2.0F, 0.6F)
				.sound(Phase.IMPACT, SoundEvents.ANVIL_LAND, 1.6F, 0.5F)
				.sound(Phase.EXTRA3, SoundEvents.DEEPSLATE_BREAK, 2.0F, 0.5F)
				.sound(Phase.EXTRA4, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.5F)
				.dampak(Dampak.builder().mark(Dampak.Mark.PATH).film(CLIMAX[0], Dampak.Vista.ISTANA_LANGIT).climaxAt(CLIMAX[0])
						.frames(Dampak.INV, WHITE_GOLD, BLOOD).silence(8).debris(Dampak.Debris.WAKE).ring(Dampak.Ring.GOLD)
						.palette(Dampak.Palette.SWORD).sigil(SigilPattern.CLEAVE_LINE, BASE_RADIUS[0]).build())
				.build());
		sink.add(base(1)
				.pose(Pose.CHARGE_OVERHEAD).pose(Phase.EXTRA0, Pose.JAVELIN_THROW)
				.climax(Phase.IMPACT)
				.fx(Phase.CAST, sky(0x3A0A05), column(EMBER).count(24).ticks(30), glyph("定").scale(4.0F).above(2.0F).ticks(60))
				// EXTRA0: the pillar leaves the hand and streaks straight up (ember trail)
				.fx(Phase.EXTRA0, trail(EMBER).count(40), motesTo(EMBER).count(20).ticks(20))
				// EXTRA1: a red star overhead (the sea of clouds is the film's)
				.fx(Phase.EXTRA1, impact(BLOOD).count(8).ticks(30))
				.fx(Phase.EXTRA2, column(WHITE).count(16).ticks(30))
				// TRAVEL (pre-impact): a thin white-hot streak with a heat-haze trail
				.fx(Phase.TRAVEL, trail(WHITE).count(40))
				// IMPACT: lands mute (silence 12), then the boom
				.fx(Phase.IMPACT, impact(EMBER).count(30).ticks(12), shards(EMBER).count(30).ticks(24), shake(1.5F, 36))
				// EXTRA3: the flattening wave, grass and leaves blown outward
				.fx(Phase.EXTRA3, shockRing(EMBER).radius(20.0F).count(60).ticks(24), mist(0x2A1A10).count(30).ticks(30), shake(0.6F, 14))
				// EXTRA4: the pillar's hoops glow as it cools
				.fx(Phase.EXTRA4, motesTo(EMBER).count(12).ticks(40))
				.sound(Phase.CAST, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.4F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.FIRECHARGE_USE, 1.6F, 0.6F)
				.sound(Phase.EXTRA2, SoundEvents.BLAZE_SHOOT, 1.6F, 0.4F)
				.sound(Phase.IMPACT, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.5F)
				.sound(Phase.IMPACT, SoundEvents.ANVIL_LAND, 1.6F, 0.4F)
				.sound(Phase.EXTRA3, SoundEvents.DEEPSLATE_BREAK, 2.0F, 0.4F)
				.sound(Phase.EXTRA4, SoundEvents.LAVA_EXTINGUISH, 1.0F, 0.6F)
				.dampak(Dampak.builder().mark(Dampak.Mark.PILLAR).film(CLIMAX[1], Dampak.Vista.LAUT_TIMUR).climaxAt(CLIMAX[1])
						.frames(WHITE, EMBER, 0x1A0A05).silence(12).debris(Dampak.Debris.BURST).ring(Dampak.Ring.EMBER)
						.palette(Dampak.Palette.FIRE).sigil(SigilPattern.DINGHAI_RINGS, BASE_RADIUS[1]).build())
				.build());
		sink.add(base(2)
				.pose(Pose.PRAY).pose(Phase.EXTRA1, Pose.FINGER_POINT)
				.climax(Phase.IMPACT)
				// CAST: night-blue sky with the stars brighter (presentation only, never a time change)
				.fx(Phase.CAST, sky(0x10183A), starfield(0xE8E0FF).count(40).ticks(140))
				// EXTRA1 x7: each node lights and its incoming line draws from star to star
				.fx(Phase.EXTRA1, impact(0xE8E0FF).count(6).ticks(10))
				.fx(Phase.EXTRA2, starfield(VIOLET).count(30).ticks(60))
				// TRAVEL x7 (pre-impact): a star falls wrapped in white fire
				.fx(Phase.TRAVEL, trail(WHITE).count(24))
				// EXTRA0 x7: a star lands; the client grows its crystal
				.fx(Phase.EXTRA0, impact(VIOLET).count(16).ticks(10), shards(0xE8E0FF).count(12).ticks(16), shake(0.4F, 8))
				// IMPACT: the climax, every line ignites as a burning trench of light
				.fx(Phase.IMPACT, column(VIOLET).count(30).ticks(20), mist(NIGHT).count(24).ticks(40), shake(1.0F, 24))
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.5F)
				.sound(Phase.EXTRA1, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1.2F)
				.sound(Phase.EXTRA2, SoundEvents.ILLUSIONER_CAST_SPELL, 1.4F, 0.6F)
				.sound(Phase.EXTRA0, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6F, 0.6F)
				.sound(Phase.IMPACT, SoundEvents.WARDEN_SONIC_BOOM, 1.6F, 0.7F)
				.sound(Phase.IMPACT, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.5F)
				.dampak(Dampak.builder().mark(Dampak.Mark.RING_ONLY).film(CLIMAX[2], Dampak.Vista.BIDUK_UTARA).climaxAt(CLIMAX[2])
						.frames(Dampak.INV, VIOLET, NIGHT).silence(6).debris(Dampak.Debris.BURST).ring(Dampak.Ring.VOID)
						.palette(Dampak.Palette.LIGHTNING).sigil(SigilPattern.BEIDOU_SEVEN, BASE_RADIUS[2]).ignite(Phase.EXTRA1).trench()
						.monumentSeconds(120).build())
				.build());
		sink.add(base(3)
				.pose(Pose.HOLD_ITEM_UP).pose(Phase.IMPACT, Pose.SEAL)
				.climax(Phase.IMPACT)
				.fx(Phase.CAST, sky(0x2A2410), glyph("山").scale(4.0F).above(3.0F).ticks(60), motesTo(SEAL_GOLD).count(16).ticks(30))
				// EXTRA1: the light grid over the square
				.fx(Phase.EXTRA1, runeCircle(SEAL_GOLD).radius(4.0F).count(24).ticks(40))
				// EXTRA2: the seal's square shadow grows
				.fx(Phase.EXTRA2, mist(0x1A1408).count(24).ticks(30))
				.fx(Phase.TRAVEL, trail(JADE).count(30))
				// IMPACT: the seal stamps, every foe in the square is lifted
				.fx(Phase.IMPACT, impact(SEAL_GOLD).count(30).ticks(12), shards(SEAL_VIOLET).count(24).ticks(20), shake(1.3F, 30))
				// EXTRA3: the cells are torn out and carried into the sky (the slabs are the client's)
				.fx(Phase.EXTRA3, mist(0x4A3A20).count(30).ticks(40), shake(0.5F, 40))
				// EXTRA4: the lifted foes slam down
				.fx(Phase.EXTRA4, impact(SEAL_VIOLET).count(20).ticks(10), shake(0.8F, 14))
				.sound(Phase.CAST, SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.7F)
				.sound(Phase.EXTRA1, SoundEvents.CONDUIT_ACTIVATE, 1.4F, 0.8F)
				.sound(Phase.EXTRA2, SoundEvents.BELL_BLOCK, 1.6F, 0.5F)
				.sound(Phase.IMPACT, SoundEvents.ANVIL_LAND, 2.0F, 0.4F)
				.sound(Phase.IMPACT, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.6F)
				.sound(Phase.EXTRA3, SoundEvents.WITHER_BREAK_BLOCK, 1.2F, 0.5F)
				.sound(Phase.EXTRA4, SoundEvents.DEEPSLATE_BREAK, 2.0F, 0.6F)
				.dampak(Dampak.builder().mark(Dampak.Mark.SHADOW).film(CLIMAX[3], Dampak.Vista.KUA_E).climaxAt(CLIMAX[3])
						.frames(Dampak.INV, SEAL_GOLD, SEAL_VIOLET).silence(6).debris(Dampak.Debris.LIFT).ring(Dampak.Ring.DUST)
						.palette(Dampak.Palette.EARTH).sigil(SigilPattern.GRID, BASE_RADIUS[3]).build())
				.build());
	}

	/** The common part of every decree (29.3.1). */
	private static dev.kultivator.data.TechniqueSpec.Builder base(int skill) {
		return art(IDS[skill], NET_FIRST + skill, null)
				.grade(TechniqueGrade.DEWA, 0).type(TechniqueType.PAMUNGKAS).realm(Realm.TRUE_IMMORTAL)
				.special(TitahSkill.ID)
				.flag(NO_TEACH, NO_ECHO, TITAH)
				.qiPct(QI_PCT).cooldown(COOLDOWN_SECONDS[skill])
				.step(at(KEEP_ALIVE_TICKS));
	}

	private TitahArts() {
	}
}
