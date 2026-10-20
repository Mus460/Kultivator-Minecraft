package dev.kultivator.client.epic.film;

import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;

import java.util.Map;

import static dev.kultivator.client.epic.film.FilmSpace.A;
import static dev.kultivator.client.epic.film.FilmSpace.P;
import static dev.kultivator.client.epic.film.FilmSpace.S;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.BELL;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.CLANG;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.CRUNCH;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.GLASS;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.GONG;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.HUM_ENERGY;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.HUM_VORTEX;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.PUNCH;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.RISER_LONG;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.RISER_SHORT;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.ROCK;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.SUB_BOOM;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.THUD;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.TWINKLE;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.WHOOSH;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.ZAP;

/**
 * The films of the Dewa and Abadi ultimates (DESIGN 30.3.5-30.3.13). Acts follow the shot lists: a build-up at the cast,
 * the pre-climax act ending on the climax (its last shot holds for a late one), the impact act under the impact frames
 * and the aftermath; between acts the player's own camera. Prop spaces use the drivers' track keys (30.4; the Fire props
 * are tracked by the {@code ArtModels} dispatch); a prop that is not tracked falls back to the aim space. Where the shot
 * list asks for more than the film caps allow (a 360° orbit in 1.5 s, 6-tick montage cuts, a 40 b/s track), the shot
 * keeps the look of it inside the caps (a shorter arc with a whip, 10-tick cuts, the path shortened to 24 b/s).
 */
final class FilmCatalogUltimate {

	private static final int GOLD = 0xFFD54A;
	private static final int RED = 0xFF3A4A;
	private static final int VIOLET = 0xB57CFF;
	private static final int CYAN = 0x9FF3FF;
	private static final int EMBER = 0xFF7A1A;
	private static final int TEAL = 0x5FE0D0;
	private static final int WHITE = 0xF4F6FF;
	private static final int SILVER = 0xD8E6FF;

	static void register(Map<String, Film> map) {
		FilmCatalog.put(map, zhuxian());
		FilmCatalog.put(map, heavenPunishingSword());
		FilmCatalog.put(map, thunderEmperorArray());
		FilmCatalog.put(map, nineHeavensThunder());
		FilmCatalog.put(map, icePhoenix());
		FilmCatalog.put(map, freezeMountQi());
		FilmCatalog.put(map, voidMirror());
		FilmCatalog.put(map, timeSpiritDomain());
		FilmCatalog.put(map, stellarBlackHole());
		FilmCatalog.put(map, emperorFlame());
		FilmCatalog.put(map, nineSuns());
		FilmCatalog.put(map, cosmicGiant());
		FilmCatalog.put(map, ancientGodTrample());
		FilmCatalog.put(map, guanyinSweetDew());
		FilmCatalog.put(map, lifeDeathDomain());
		FilmCatalog.put(map, bodhisattvaTree());
		FilmCatalog.put(map, stygianTigerSeal());
		FilmCatalog.put(map, kunpeng());
		FilmCatalog.put(map, ninthHex());
		FilmCatalog.put(map, fiveElementsMountain());
		FilmCatalog.put(map, taiChiDiagram());
		FilmCatalog.put(map, heavenTrampling());
	}

	// ================================================================ Sword Dao (30.3.5)

	/** Formasi Pedang Zhuxian (Abadi, K = 240). */
	private static Film zhuxian() {
		FilmAct opening = FilmAct.cast(0);
		// 1: the four corner swords land on their plinths: three 10-tick worm shots, black-dip cuts, one clang each
		for (int i = 0; i < 3; i++) {
			if (i > 0) {
				opening.cut(FilmShot.Mask.BLACK_DIP);
			}
			String corner = "corner_" + i;
			opening.shot(FilmShot.of(10).worm(0.3F).keyP(0.0F, corner, 2.2D, 0.3D, -3.0D).keyP(1.0F, corner, 1.9D, 0.3D, -2.6D)
					.lookProp(corner, 3.0D).lens(FilmLens.SMEAR).sound(0.2F, CLANG, 0.9F + i * 0.1F));
		}
		// 2: straight up over the centre: the square, the map rails and the four glyphs light
		opening.shot(FilmShot.of(40).aerial(A, null, 8.0D, 44.0D, 2.0D, 6.0D).lens(FilmLens.LIGHT_LEAK).tint(RED).sound(0.0F, RISER_LONG));
		return Film.of("zhuxian_sword_formation").prop("sky_sword", 0.0D, 30.0D, 0.0D).prop("corner_0", -10.0D, 0.0D, -10.0D).prop("corner_1", 10.0D, 0.0D, -10.0D).prop("corner_2", 10.0D, 0.0D, 10.0D).prop("corner_3", -10.0D, 0.0D, 10.0D).k(240).tint(RED)
				.act(opening)
				.act(FilmAct.climax(-80)
						// 3: around the four corners under a red sky (150° keeps the arc under 24 b/s), a whip between corners
						.shot(FilmShot.of(50).orbit(A, null, 22.0D, 22.0D, 6.0D, 7.0D, -40.0F, 150.0F).look(A, 0.0D, 2.0D, 0.0D)
								.whip(0.55F, 40.0F, 5).lens(FilmLens.FRINGE).sound(0.1F, WHOOSH).sound(0.55F, WHOOSH, 1.1F))
						// 4: Langit Kosmik (red nebula): the giant sky sword emerging from the clouds, a vertigo dolly
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(22).key(0.0F, A, 0.0D, 10.0D, -14.0D).key(1.0F, A, 0.0D, 12.0D, -18.0D).lookProp("sky_sword", 0.0D)
								.dollyProp("sky_sword").cosmic().lens(FilmLens.ANAMORPHIC).tint(RED).sound(0.0F, RISER_SHORT))
						// 5: riding down the blade toward the formation centre
						.shot(FilmShot.of(8).pov("sky_sword", 4.0F, 1.0F).key(0.0F, A, 1.0D, 30.0D, -2.0D).key(1.0F, A, 1.0D, 14.0D, -2.0D)
								.look(A, 0.0D, 0.0D, 0.0D).fov(0.0F, 1.35F).lens(FilmLens.SPEED_LINES, FilmLens.SMEAR).sound(0.0F, WHOOSH)
								.holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 6: under the frames: the corner swords tilt inward, the shock ring freezes
						.shot(FilmCatalog.impactOrbit(30, 3, 10.0D, 70.0F, 0.05F, 20).lens(FilmLens.FRINGE))
						// 7: the reveal: the planted sky sword in the crater, the corner swords fading
						.shot(FilmCatalog.aftermath(40, 4.0D, 30.0D)));
	}

	/** Seni Pedang Hukuman Langit (Dewa, K = 60, one act). */
	private static Film heavenPunishingSword() {
		return Film.of("heaven_punishing_sword").prop("gate", 0.0D, 42.0D, 0.0D).prop("sword", 0.0D, 22.0D, 0.0D).k(60).tint(0xFFF4D0)
				.act(FilmAct.cast(0)
						// 1: straight up at the gate opening in the sky
						.shot(FilmShot.of(20).worm(0.3F).key(0.0F, S, 1.5D, 0.3D, 2.0D).key(1.0F, S, 2.0D, 0.4D, 3.0D).lookProp("gate", 0.0D)
								.fov(0.0F, 1.0F).fov(1.0F, 1.25F).flare(P, "gate", 0.0D, 0.0D, 0.0D).tint(0xFFF4D0).sound(0.0F, RISER_LONG))
						// 2: the stun nova's dark ring passes the caster (vertigo)
						.shot(FilmShot.of(15).key(0.0F, S, 0.0D, 1.6D, 5.0D).key(1.0F, S, 0.0D, 1.6D, 11.0D).look(S, 0.0D, 1.6D, 0.0D)
								.dolly(FilmShot.Target.SUBJECT).vignette(0x050308).lens(FilmLens.FRINGE))
						// 3: Langit Kosmik: the sword slides out through the gate
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(15).key(0.0F, A, 0.0D, 18.0D, -12.0D).key(1.0F, A, 0.0D, 19.0D, -10.0D).lookProp("sword", 0.0D)
								.cosmic(Dampak.Vista.GERBANG_LANGIT).card().lens(FilmLens.ANAMORPHIC, FilmLens.LIGHT_LEAK).sound(0.2F, BELL))
						// 4: a crash zoom on the blade tip, then a whip down along the beam's path
						.shot(FilmShot.of(10).key(0.0F, A, 2.0D, 16.0D, -6.0D).key(1.0F, A, 2.0D, 15.5D, -5.5D).lookProp("sword", -1.0D)
								.crash(0.65F, 3).whipPitch(0.45F, 60.0F, 5).sound(0.45F, WHOOSH).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: tracking beside the travelling beam, 6 b from its axis (shortened to the 24 b/s cap)
						.shot(FilmShot.of(30).key(0.0F, S, 6.0D, 2.0D, 0.0D).key(1.0F, S, 6.0D, 2.5D, 60.0D).ease(Ease.LINEAR)
								.look(S, 0.0D, 1.5D, 90.0D).lens(FilmLens.IMPACT, FilmLens.SPEED_LINES).soundTick(6, SUB_BOOM, 1.0F)
								.sound(0.0F, ZAP))
						// 6: along the trench to the sword planted at its end
						.shot(FilmShot.of(40).aerial(P, "sword", 10.0D, 36.0D, 12.0D, 22.0D).lens(FilmLens.LIGHT_LEAK)
								.sound(0.0F, HUM_ENERGY, 0.8F)));
	}

	// ================================================================ Lightning Dao (30.3.6)

	/** Formasi Kaisar Petir Kesengsaraan (Abadi, K = 198; strikes EXTRA0 at 22 + 22n, the 9th is EXTRA1). */
	private static Film thunderEmperorArray() {
		FilmAct strobe = FilmAct.event(Phase.EXTRA0, 2, 0);
		// 2: a hard cut on each of three strikes to the lit palace drum (the strike's own flash hides it, else a dip)
		for (int i = 0; i < 3; i++) {
			if (i > 0) {
				strobe.cut(FilmShot.Mask.BLACK_DIP);
			}
			String drum = "drum_" + (i + 2);
			strobe.shot(FilmShot.of(22).keyP(0.0F, drum, 3.0D, 1.5D, -3.5D).keyP(1.0F, drum, 2.6D, 1.4D, -3.0D).lookProp(drum, 1.0D)
					.lens(FilmLens.FRINGE).sound(0.0F, GONG, 0.9F + i * 0.1F));
		}
		return Film.of("tribulation_thunder_emperor_array").prop("drum_2", 0.0D, 0.0D, -6.0D).prop("drum_3", 6.0D, 0.0D, -6.0D).prop("drum_4", -6.0D, 0.0D, 0.0D).k(198).tint(VIOLET)
				.act(FilmAct.cast(0)
						// 1: at the centre palace looking straight up into the opening whirlpool, a slow Dutch roll
						.shot(FilmShot.of(40).worm(0.3F).key(0.0F, A, 0.8D, 0.3D, -0.8D).key(1.0F, A, 0.5D, 0.3D, -0.5D)
								.lookSky(0.0F, 75.0F, 20.0F, 82.0F).roll(0.0F, 0.0F).roll(1.0F, 8.0F).lens(FilmLens.FRINGE)
								.sound(0.0F, HUM_VORTEX).sound(0.1F, RISER_LONG)))
				.act(strobe)
				.act(FilmAct.climax(-60)
						// 3: around the caster in the array's centre, Dutch 15°, the silhouettes in the storm
						.shot(FilmShot.of(46).orbit(S, null, 6.0D, 6.0D, 2.0D, 2.4D, 0.0F, 150.0F).look(S, 0.0D, 1.6D, 0.0D).roll(0.0F, 15.0F)
								.lens(FilmLens.ANAMORPHIC).tint(VIOLET).sound(0.0F, HUM_VORTEX))
						// 4: a crash zoom up into the vortex eye
						.shot(FilmShot.of(14).key(0.0F, S, 0.6D, 1.0D, -0.8D).key(1.0F, S, 0.5D, 1.1D, -0.6D).lookSky(0.0F, 80.0F, 0.0F, 84.0F)
								.crash(0.6F, 4).sound(0.0F, RISER_SHORT).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: the 9th strike frozen mid-strike at peak brightness
						.shot(FilmShot.of(24).orbit(A, null, 10.0D, 10.0D, 3.0D, 3.5D, 0.0F, 120.0F).look(A, 0.0D, 6.0D, 0.0D).bullet(0.05F, 24)
								.lens(FilmLens.IMPACT).soundTick(3, SUB_BOOM, 1.0F).sound(0.0F, ZAP))
						// 6: the Luo Shu path burned into the land
						.shot(FilmCatalog.aftermath(40, 6.0D, 40.0D).tint(VIOLET)));
	}

	/** Tianzun Guntur Sembilan Langit (Dewa, K = 400). */
	private static Film nineHeavensThunder() {
		FilmAct whips = FilmAct.event(Phase.EXTRA0, 4, 0);
		// 3: three whips between the striking deities
		for (int i = 0; i < 3; i++) {
			if (i > 0) {
				whips.cut(FilmShot.Mask.SMEAR);
			}
			String deity = "deity_" + (i * 3 + 1);
			whips.shot(FilmShot.of(10).key(0.0F, S, -2.0D + i * 2.0D, 2.5D, -5.0D).key(1.0F, S, -2.0D + i * 2.0D, 2.6D, -4.6D)
					.lookProp(deity, 2.0D).whip(0.4F, i % 2 == 0 ? 60.0F : -60.0F, 5).sound(0.4F, WHOOSH).sound(0.0F, ZAP, 0.8F + i * 0.15F));
		}
		return Film.of("nine_heavens_thunder_venerable").prop("qilin", 0.0D, 3.0D, -12.0D).prop("deity_1", -12.0D, 18.0D, 6.0D).prop("deity_4", 0.0D, 22.0D, 10.0D).prop("deity_7", 12.0D, 18.0D, 6.0D).k(400).tint(VIOLET)
				.act(FilmAct.cast(0)
						// 1: hero worm dolly-in on the transforming caster, the black qilin rearing behind
						.shot(FilmShot.of(24).worm(0.4F).key(0.0F, S, 1.4D, 0.4D, 4.6D).key(1.0F, S, 1.0D, 0.4D, 3.8D).look(S, 0.0D, 1.9D, 0.0D)
								.flare(P, "qilin", 0.0D, 3.0D, 0.0D).sound(0.0F, RISER_SHORT).sound(0.2F, GONG)))
				.act(FilmAct.cast(24).climb()
						// 2: the ring tunnel: rising through the nine stacked sky rings, each passing the lens
						.shot(FilmShot.of(40).key(0.0F, S, 0.0D, 3.0D, -3.0D).key(0.5F, S, 0.4D, 22.0D, -1.0D).key(1.0F, S, 0.0D, 42.0D, 0.0D)
								.ease(Ease.IN).lookSky(0.0F, 70.0F, 30.0F, 84.0F).lens(FilmLens.SMEAR, FilmLens.SPEED_LINES).sound(0.0F, RISER_LONG)
								.sound(0.3F, WHOOSH).sound(0.65F, WHOOSH, 1.1F))
						// ... and the Nine Heavens at the top
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(16).key(0.0F, S, 0.0D, 42.0D, 0.0D).key(1.0F, S, 0.5D, 43.0D, 0.5D).lookSky(30.0F, 70.0F, 40.0F, 72.0F)
								.cosmic(Dampak.Vista.SEMBILAN_LANGIT).card().lens(FilmLens.ANAMORPHIC)))
				.act(whips)
				.act(FilmAct.climax(-40)
						// 4: a slow push on the caster raising the golden whip, the FOV narrowing, the rumble rising
						.shot(FilmShot.of(40).key(0.0F, S, 0.6D, 1.6D, 5.0D).key(1.0F, S, 0.4D, 1.7D, 3.6D).look(S, 0.0D, 2.0D, 0.0D)
								.fov(0.0F, 1.0F).fov(1.0F, 0.8F).shake(FilmShake.Profile.RUMBLE, 0.4F, 40).sound(0.0F, HUM_VORTEX).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: top-down 48 b: the bolt storm strikes everywhere, frozen
						.shot(FilmShot.of(30).topDown(A, null, 46.0D, 20.0F).bullet(0.05F, 16).lens(FilmLens.IMPACT).soundTick(6, SUB_BOOM, 1.0F))
						// 6: under the dissolving rings, looking up
						.shot(FilmShot.of(40).worm(0.3F).key(0.0F, A, 0.0D, 0.3D, -2.0D).key(1.0F, A, 0.5D, 0.3D, -1.5D).lookSky(0.0F, 70.0F, 15.0F, 80.0F)
								.lens(FilmLens.LIGHT_LEAK).sound(0.0F, HUM_ENERGY, 0.8F)));
	}

	// ================================================================ Ice Dao (30.3.7)

	/** Turunnya Phoenix Es (Abadi, K = 240; dives at 60/120/180). */
	private static Film icePhoenix() {
		return Film.of("ice_phoenix_descent").prop("phoenix", 0.0D, 12.0D, 0.0D).k(240).tint(CYAN)
				.act(FilmAct.cast(0)
						// 1: a rising spiral as the phoenix forms from snow and unfolds its wings
						.shot(FilmShot.of(50).orbit(P, "phoenix", 14.0D, 10.0D, 2.0D, 18.0D, 0.0F, 200.0F).lookProp("phoenix", 0.0D)
								.lens(FilmLens.ANAMORPHIC).tint(CYAN).sound(0.0F, RISER_LONG).sound(0.5F, GLASS)))
				.act(FilmAct.cast(60)
						// 2: riding its back down onto the densest point
						.shot(FilmShot.of(20).pov("phoenix", 5.0F, 1.5F).key(0.0F, A, 0.0D, 20.0D, -12.0D).key(1.0F, A, 0.0D, 4.0D, -4.0D)
								.ease(Ease.IN).look(A, 0.0D, 0.0D, 0.0D).fov(0.0F, 1.35F).lens(FilmLens.SMEAR, FilmLens.SPEED_LINES)
								.sound(0.0F, WHOOSH))
						// 3: top-down on the six-fold frost ring blooming
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(16).topDown(A, null, 28.0D, 15.0F).sound(0.1F, GLASS)))
				.act(FilmAct.climax(-50)
						// 4: wide: the phoenix rising to full wingspan against a pale aurora
						.shot(FilmShot.of(46).key(0.0F, A, -14.0D, 6.0D, -18.0D).key(1.0F, A, -16.0D, 9.0D, -21.0D).lookProp("phoenix", 0.0D)
								.lens(FilmLens.ANAMORPHIC, FilmLens.LIGHT_LEAK).tint(CYAN).sound(0.0F, HUM_ENERGY, 1.1F))
						// 5: a crash zoom into the phoenix's eye
						.shot(FilmShot.of(4).keyP(0.0F, "phoenix", 0.0D, 1.0D, 6.0D).keyP(1.0F, "phoenix", 0.0D, 1.0D, 5.0D)
								.lookProp("phoenix", 0.8D).crash(0.6F, 4).sound(0.0F, RISER_SHORT).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 6: through the frozen shards of the shattered phoenix
						.shot(FilmShot.of(30).orbit(A, null, 8.0D, 8.0D, 4.0D, 4.5D, 0.0F, 100.0F).look(A, 0.0D, 3.0D, 0.0D).bullet(0.05F, 24)
								.lens(FilmLens.IMPACT).soundTick(3, SUB_BOOM, 1.0F).sound(0.1F, GLASS))
						// 7: the pullback over the frost rings and the planted ice quill
						.shot(FilmCatalog.aftermath(34, 3.0D, 26.0D).tint(CYAN)));
	}

	/** Membekukan Gunung Qi (Dewa, K = 400; EXTRA0 @200 the mass freeze). */
	private static Film freezeMountQi() {
		return Film.of("freeze_mount_qi").prop("peak_1", 9.0D, 0.0D, 0.0D).k(400).tint(CYAN)
				.act(FilmAct.cast(0)
						// 1: the whiteout floods outward (vertigo), frost on the lens edges
						.shot(FilmShot.of(30).key(0.0F, S, 0.0D, 1.7D, 3.0D).key(1.0F, S, 0.0D, 2.4D, 9.0D).look(S, 0.0D, 1.6D, 0.0D)
								.dolly(FilmShot.Target.SUBJECT).vignette(0x9FD8FF).sound(0.0F, RISER_LONG))
						// 2: the frost crown and the six ice peaks rising one per pulse on the rim
						.shot(FilmShot.of(50).aerial(A, null, 4.0D, 32.0D, 6.0D, 18.0D).sound(0.2F, ROCK).sound(0.5F, GLASS)))
				.act(FilmAct.event(Phase.EXTRA0, 1, 0)
						// 3: freeze frame: a slow lateral track past the frozen foes in their ice coffins, time all but stopped
						.shot(FilmShot.of(40).key(0.0F, A, -9.0D, 1.6D, -5.0D).key(1.0F, A, 9.0D, 1.6D, -5.0D).ease(Ease.LINEAR)
								.look(A, 0.0D, 1.2D, 0.0D).bullet(0.1F, 30).grey(0.15F).quiet(0.5F).lens(FilmLens.FRINGE).sound(0.0F, GLASS)))
				.act(FilmAct.climax(-40)
						// 4: looking up at the cracking ice peaks
						.shot(FilmShot.of(36).worm(0.3F).key(0.0F, A, 3.0D, 0.3D, -6.0D).key(1.0F, A, 2.5D, 0.3D, -5.0D).lookProp("peak_1", 8.0D)
								.sound(0.2F, ROCK).sound(0.6F, ROCK, 0.85F).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: the recall shatter: the peaks explode inward; an orbit with a whip
						.shot(FilmShot.of(30).orbit(S, null, 10.0D, 10.0D, 4.0D, 4.0D, 0.0F, 120.0F).look(S, 0.0D, 2.0D, 0.0D)
								.whip(0.55F, 80.0F, 6).lens(FilmLens.IMPACT).soundTick(6, SUB_BOOM, 1.0F).sound(0.1F, GLASS).sound(0.6F, GLASS, 0.8F))
						// 6: a crane down onto the ice lotus monument in the snowfall
						.shot(FilmShot.of(40).key(0.0F, A, 0.0D, 14.0D, -9.0D).key(1.0F, A, 0.0D, 3.0D, -4.5D).ease(Ease.IN_OUT)
								.look(A, 0.0D, 1.5D, 0.0D).lens(FilmLens.LIGHT_LEAK).tint(WHITE).sound(0.0F, HUM_ENERGY, 0.9F)));
	}

	// ================================================================ Space Dao (30.3.8)

	/** Cermin Hampa (Abadi, K = the release EXTRA0, player-timed). */
	private static Film voidMirror() {
		return Film.of("void_mirror").prop("mirror", 0.0D, 1.8D, -12.0D).playerTimed(120).tint(VIOLET)
				.act(FilmAct.cast(0)
						// 1: a macro 1.2 b from the mirror face, a slow orbit while stars drift inside the glass
						.shot(FilmShot.of(40).orbit(P, "mirror", 1.6D, 1.4D, 0.0D, 0.1D, -30.0F, 60.0F).lookProp("mirror", 0.0D)
								.flare(P, "mirror", 0.0D, 0.0D, 0.0D).tint(VIOLET).sound(0.0F, HUM_ENERGY, 0.8F)))
				.act(FilmAct.event(Phase.EXTRA6, 1, 0)
						// 2: one snap close-up of the ripple on the glass
						.shot(FilmShot.of(10).keyP(0.0F, "mirror", 0.4D, 0.2D, 1.3D).keyP(1.0F, "mirror", 0.3D, 0.2D, 1.1D).lookProp("mirror", 0.0D)
								.lens(FilmLens.FRINGE).sound(0.0F, GLASS)))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 3: over the shoulder behind the mirror, a crash zoom along the black beam
						.shot(FilmShot.of(6).keyP(0.0F, "mirror", 0.6D, 0.6D, -2.2D).keyP(1.0F, "mirror", 0.6D, 0.6D, -2.0D).look(A, 0.0D, 1.0D, 0.0D)
								.crash(0.6F, 3).lens(FilmLens.IMPACT).soundTick(3, ZAP, 1.0F))
						// 4: reverse tracking: rushing backward ahead of the beam, facing the caster, the beam chasing the lens
						.shot(FilmShot.of(24).key(0.0F, S, 0.0D, 1.8D, 3.0D).key(1.0F, S, 0.0D, 2.6D, 28.0D).ease(Ease.OUT).look(S, 0.0D, 1.6D, 0.0D)
								.lens(FilmLens.SPEED_LINES, FilmLens.SMEAR).sound(0.0F, WHOOSH).soundTick(2, SUB_BOOM, 1.0F))
						// 5: along the strip as the cells lift and break into portal motes
						.shot(FilmCatalog.aftermath(40, 4.0D, 28.0D).tint(VIOLET)));
	}

	/** Wilayah Roh Waktu (Dewa, K = 160). */
	private static Film timeSpiritDomain() {
		FilmShot stopped = FilmShot.of(110).orbit(S, null, 12.0D, 12.0D, 2.0D, 6.0D, 0.0F, 180.0F).look(S, 0.0D, 1.6D, 0.0D)
				.bullet(0.03F, 30).grey(0.2F).quiet(0.5F).lens(FilmLens.GRAIN);
		for (int i = 1; i <= 5; i++) {
			// the tick-tock of the great clock at half pitch, every 20 ticks
			stopped.soundTick(i * 20, TWINKLE, 0.5F);
		}
		return Film.of("time_spirit_domain").prop("clock", 0.0D, 4.0D, -10.0D).k(160).tint(0xE8E0C8)
				.act(FilmAct.cast(0)
						// 1: a crash zoom on the caster's eyes as the great clock unfolds behind
						.shot(FilmShot.of(10).key(0.0F, S, 0.0D, 1.7D, 2.6D).key(1.0F, S, 0.0D, 1.7D, 2.2D).look(S, 0.0D, 1.65D, 0.0D)
								.crash(0.6F, 4).lens(FilmLens.FRINGE).sound(0.0F, RISER_SHORT))
						// 2: stopped time: a long orbit through the still battlefield, droplets and shards hanging, grey grade
						.shot(stopped))
				.act(FilmAct.climax(-30)
						// 3: a macro push along the clock hands
						.shot(FilmShot.of(24).keyP(0.0F, "clock", 1.5D, 0.5D, -3.0D).keyP(1.0F, "clock", 0.8D, 0.3D, -1.6D).lookProp("clock", 0.0D)
								.sound(0.3F, TWINKLE, 0.7F))
						// 4: the vertigo on the caster
						.shot(FilmShot.of(6).key(0.0F, S, 0.0D, 1.7D, 3.0D).key(1.0F, S, 0.0D, 1.7D, 5.0D).look(S, 0.0D, 1.6D, 0.0D)
								.dolly(FilmShot.Target.SUBJECT).sound(0.0F, RISER_SHORT).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: time resumes: two whips as every frozen thing snaps forward
						.shot(FilmShot.of(10).key(0.0F, S, 3.0D, 2.0D, -4.0D).key(1.0F, S, 3.0D, 2.0D, -3.6D).look(A, 0.0D, 1.0D, 0.0D)
								.whip(0.2F, 70.0F, 5).lens(FilmLens.IMPACT).soundTick(10, SUB_BOOM, 1.0F))
						.cut(FilmShot.Mask.SMEAR)
						.shot(FilmShot.of(10).key(0.0F, S, -3.0D, 2.0D, -4.0D).key(1.0F, S, -3.0D, 2.0D, -3.6D).look(A, 0.0D, 1.0D, 0.0D)
								.whip(0.2F, -70.0F, 5).sound(0.2F, WHOOSH))
						// 6: the pullback to the clock monument
						.shot(FilmCatalog.aftermath(36, 4.0D, 24.0D)));
	}

	/** Lubang Hitam (Dewa, K = 120). */
	private static Film stellarBlackHole() {
		return Film.of("stellar_black_hole").prop("core", 0.0D, 6.0D, 0.0D).k(120).tint(0xFF9A3A)
				.act(FilmAct.cast(0)
						// 1: a macro on the star igniting, a crash zoom as it collapses into the core
						.shot(FilmShot.of(20).keyP(0.0F, "core", 0.0D, 0.5D, -5.0D).keyP(1.0F, "core", 0.0D, 0.5D, -4.0D).lookProp("core", 0.0D)
								.crash(0.6F, 4).flare(P, "core", 0.0D, 0.0D, 0.0D).tint(WHITE).lens(FilmLens.FRINGE).sound(0.0F, RISER_SHORT))
						// 2: in the accretion plane, Dutch 20°, the photon ring's streak
						.shot(FilmShot.of(50).orbit(P, "core", 14.0D, 14.0D, 0.0D, 0.3D, 0.0F, 90.0F).lookProp("core", 0.0D).roll(0.0F, 20.0F)
								.lens(FilmLens.ANAMORPHIC).tint(0xFF9A3A).sound(0.0F, HUM_VORTEX)))
				.act(FilmAct.climax(-40)
						// 3: spiralling in toward the core with the debris, accelerating, the FOV opening, the smear growing
						.shot(FilmShot.of(30).keyP(0.0F, "core", 12.0D, 2.0D, -8.0D).keyP(0.5F, "core", 6.0D, 1.0D, -3.0D)
								.keyP(1.0F, "core", 2.5D, 0.5D, -1.5D).ease(Ease.IN).lookProp("core", 0.0D).fov(0.0F, 1.0F).fov(1.0F, 1.4F)
								.lens(FilmLens.SMEAR, FilmLens.SPEED_LINES).sound(0.0F, RISER_LONG).sound(0.6F, WHOOSH))
						// 4: "inside": stars on every side, the world gone
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(10).key(0.0F, A, 0.0D, 10.0D, -6.0D).key(1.0F, A, 0.0D, 10.5D, -5.5D).lookSky(0.0F, 60.0F, 20.0F, 65.0F)
								.cosmic().quiet(0.4F).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: out to an aerial 48 b as the jets fire up and down
						.shot(FilmShot.of(30).aerial(A, null, 38.0D, 46.0D, 18.0D, 22.0D).lens(FilmLens.IMPACT).soundTick(6, SUB_BOOM, 1.0F))
						// 6: a slow pullback while the core shrinks to its hovering monument
						.shot(FilmCatalog.aftermath(40, 6.0D, 22.0D).tint(VIOLET)));
	}

	// ================================================================ Fire Dao (30.3.9)

	/** Api Kaisar (Dewa, K = 400): props by {@code FireUltimates}; the lotus centre is tracked by the dispatch. */
	private static Film emperorFlame() {
		return Film.of("emperor_flame").prop("lotus", 0.0D, 0.0D, 0.0D).k(400).tint(0xC060FF)
				.act(FilmAct.cast(0)
						// 1: a macro on the closed bud rising, the glyph 帝 forming above it
						.shot(FilmShot.of(30).keyP(0.0F, "lotus", 1.5D, 1.0D, -3.0D).keyP(1.0F, "lotus", 1.0D, 1.6D, -2.2D).lookProp("lotus", 1.5D)
								.flare(P, "lotus", 0.0D, 3.0D, 0.0D).tint(0xC060FF).sound(0.0F, RISER_SHORT))
						// 2: top-down 30 b turning 90° as the petals unfold (the kaleidoscope)
						.shot(FilmShot.of(50).topDown(P, "lotus", 28.0D, 90.0F).lens(FilmLens.ANAMORPHIC).sound(0.0F, RISER_LONG)))
				.act(FilmAct.climax(-60)
						// 3: a low orbit through the flame wisps
						.shot(FilmShot.of(46).orbit(A, null, 18.0D, 18.0D, 1.5D, 1.8D, 0.0F, 120.0F).look(A, 0.0D, 3.0D, 0.0D)
								.lens(FilmLens.HEAT_HAZE, FilmLens.LIGHT_LEAK).tint(EMBER).sound(0.0F, HUM_ENERGY, 0.9F))
						// 4: a crash zoom on the heart as the petals fold
						.shot(FilmShot.of(14).keyP(0.0F, "lotus", 0.0D, 2.0D, -6.0D).keyP(1.0F, "lotus", 0.0D, 2.0D, -5.0D).lookProp("lotus", 1.5D)
								.crash(0.6F, 4).sound(0.0F, RISER_SHORT).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: the white-out, then low on the nova's two rings
						.shot(FilmCatalog.impactOrbit(24, 6, 10.0D, 50.0F, 0.05F, 18))
						// 6: over the ring of petal shards
						.shot(FilmCatalog.aftermath(40, 4.0D, 36.0D).tint(EMBER)));
	}

	/** Murka Langit Sembilan Matahari (Abadi, charged 60, K = 60 after the charge). */
	private static Film nineSuns() {
		FilmAct falls = FilmAct.event(Phase.TRAVEL, 1, 0);
		// 3: three of the falling suns' impacts (10-tick cuts)
		for (int i = 0; i < 3; i++) {
			if (i > 0) {
				falls.cut(FilmShot.Mask.BLACK_DIP);
			}
			double side = (i - 1) * 9.0D;
			falls.shot(FilmShot.of(10).key(0.0F, A, side, 3.0D, -12.0D).key(1.0F, A, side * 0.9D, 3.0D, -11.0D).look(A, side * 0.5D, 1.0D, 0.0D)
					.lens(FilmLens.SMEAR, FilmLens.HEAT_HAZE).soundAtAim(0.5F, CRUNCH, 0.8F + i * 0.1F));
		}
		return Film.of("nine_suns_heavens_fury").prop("crow", 4.0D, 24.0D, 0.0D).prop("sun", 0.0D, 24.0D, 0.0D).charged().k(60).tint(GOLD)
				.act(FilmAct.cast(0)
						// 1: looking up as the nine suns kindle one by one, a slow pan across them
						.shot(FilmShot.of(30).worm(0.3F).key(0.0F, S, 0.8D, 0.3D, 1.2D).key(1.0F, S, 1.0D, 0.3D, 1.5D).lookSky(-30.0F, 45.0F, 30.0F, 48.0F)
								.flare(A, null, 0.0D, 24.0D, 0.0D).lens(FilmLens.HEAT_HAZE).tint(GOLD).sound(0.0F, RISER_LONG).sound(0.1F, TWINKLE)
								.sound(0.5F, TWINKLE, 1.1F).sound(0.9F, TWINKLE, 1.2F))
						// 2: a chase shot following the golden crow round the middle sun
						.shot(FilmShot.of(30).pov("crow", 4.0F, 1.0F).orbit(A, null, 9.0D, 9.0D, 22.0D, 24.0D, 0.0F, 120.0F).look(A, 0.0D, 24.0D, 0.0D)
								.lens(FilmLens.ANAMORPHIC).sound(0.0F, WHOOSH).holdForClimax()))
				.act(falls)
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 4: a cosmic flash of the great sun bursting over the aim, under the frames
						.shot(FilmShot.of(24).key(0.0F, A, 0.0D, 14.0D, -16.0D).key(1.0F, A, 0.0D, 15.0D, -14.0D).look(A, 0.0D, 20.0D, 0.0D)
								.cosmic(Dampak.Vista.MATAHARI_KAISAR).lens(FilmLens.IMPACT).soundTick(3, SUB_BOOM, 1.0F))
						// 5: over the crater field
						.shot(FilmCatalog.aftermath(40, 8.0D, 34.0D).lens(FilmLens.HEAT_HAZE).tint(EMBER)));
	}

	// ================================================================ Body Dao (30.3.10)

	/** Wujud Kosmik Langit-Bumi (Abadi, K = 400). */
	private static Film cosmicGiant() {
		return Film.of("cosmic_giant_form").prop("giant", 0.0D, 0.0D, -8.0D).prop("foot_l", -3.0D, 0.0D, -4.0D).k(400).tint(GOLD)
				.act(FilmAct.cast(0)
						// 1: the tilt-up reveal: from the giant's feet a crane rising 40 b along its front to its burning eyes
						.shot(FilmShot.of(50).keyP(0.0F, "giant", 0.0D, 0.5D, 8.0D).keyP(1.0F, "giant", 0.0D, 40.0D, 12.0D).ease(Ease.IN_OUT)
								.lookProp("giant", 2.0D).lookEnd(P, "giant", 0.0D, 42.0D, 0.0D).flare(P, "giant", 0.0D, 44.0D, 1.0D).tint(GOLD)
								.sound(0.0F, RISER_LONG).sound(0.1F, THUD)))
				.act(FilmAct.event(Phase.PHANTOM, 1, 0)
						// 2: under the lifted foot: its shadow covers the lens, a dip on the stomp
						.shot(FilmShot.of(14).worm(0.3F).keyP(0.0F, "foot_l", 3.0D, 0.3D, -3.0D).keyP(1.0F, "foot_l", 2.5D, 0.3D, -2.5D)
								.lookSky(0.0F, 60.0F, 0.0F, 70.0F).vignette(0x0A0806).sound(0.3F, THUD).sound(0.8F, PUNCH)))
				.act(FilmAct.climax(-60)
						// 3: over the giant's shoulder, 30 b up, looking down at the field
						.shot(FilmShot.of(60).keyP(0.0F, "giant", 4.0D, 30.0D, -6.0D).keyP(1.0F, "giant", 5.0D, 30.0D, -4.0D).look(A, 0.0D, 0.0D, 0.0D)
								.lens(FilmLens.ANAMORPHIC).sound(0.0F, HUM_ENERGY, 0.8F).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 4: the kneel punch: a low bullet-time orbit around the fist on the ground
						.shot(FilmCatalog.impactOrbit(24, 3, 8.0D, 70.0F, 0.05F, 20).sound(0.1F, PUNCH))
						// 5: the pullback as the giant dissolves upward into gold dust
						.shot(FilmCatalog.aftermath(40, 4.0D, 30.0D).tint(GOLD)));
	}

	/** Injakan Dewa Kuno (Dewa, K = 180; steps EXTRA0 at 20 + 20n). */
	private static Film ancientGodTrample() {
		return Film.of("ancient_god_trample").prop("sky_foot", 0.0D, 32.0D, 0.0D).k(180).tint(SILVER)
				.act(FilmAct.cast(0)
						// 1: a macro on the brow: nine dim star marks
						.shot(FilmShot.of(20).key(0.0F, S, 0.3D, 1.75D, 0.9D).key(1.0F, S, 0.2D, 1.75D, 0.7D).look(S, 0.0D, 1.75D, 0.0D)
								.flare(S, null, 0.0D, 1.85D, 0.1D).tint(SILVER).sound(0.2F, TWINKLE)))
				.act(FilmAct.event(Phase.EXTRA0, 1, 0)
						// 2: a low tracking dolly beside the footprints, a jolt on each step
						.shot(FilmShot.of(40).key(0.0F, S, 2.5D, 0.5D, -1.0D).key(1.0F, S, 2.5D, 0.5D, 6.0D).ease(Ease.LINEAR)
								.look(S, 0.0D, 1.0D, 2.0D).shake(FilmShake.Profile.JOLT, 0.5F, 12).sound(0.0F, THUD).sound(0.5F, THUD, 0.9F)))
				.act(FilmAct.climax(-30)
						// 3: Langit Kosmik with the star river: the god foot forming in the sky tear
						.shot(FilmShot.of(20).key(0.0F, A, 0.0D, 8.0D, -12.0D).key(1.0F, A, 0.0D, 9.0D, -10.0D).lookProp("sky_foot", 0.0D)
								.cosmic(Dampak.Vista.SUNGAI_BINTANG).card().lens(FilmLens.ANAMORPHIC).tint(GOLD).sound(0.0F, HUM_ENERGY, 0.7F)
								.sound(0.5F, RISER_SHORT))
						// 4: under the descending foot
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(10).worm(0.3F).key(0.0F, A, 2.0D, 0.3D, -2.0D).key(1.0F, A, 1.6D, 0.3D, -1.6D).lookSky(0.0F, 72.0F, 0.0F, 80.0F)
								.vignette(0x080604).sound(0.0F, RISER_LONG).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: the impact, then a whip up toward the ninth star mark
						.shot(FilmShot.of(16).worm(0.4F).key(0.0F, A, 6.0D, 0.4D, -8.0D).key(1.0F, A, 5.5D, 0.4D, -7.0D).look(A, 0.0D, 1.5D, 0.0D)
								.whipPitch(0.5F, -50.0F, 5).lens(FilmLens.IMPACT).soundTick(6, SUB_BOOM, 1.0F).sound(0.1F, PUNCH))
						// 6: the nine footprints joined by the silver line
						.shot(FilmCatalog.aftermath(40, 4.0D, 34.0D).tint(SILVER)));
	}

	// ================================================================ Life Dao (30.3.11)

	/** Embun Manis Guanyin (Abadi, K = 200; gentle: no shake, blends instead of hard cuts). */
	private static Film guanyinSweetDew() {
		return Film.of("guanyin_sweet_dew").prop("vase", 0.0D, 2.5D, -10.0D).k(200).tint(WHITE)
				.act(FilmAct.cast(0)
						// 1: a slow crane beside the floating vase on its lotus seat
						.shot(FilmShot.of(50).keyP(0.0F, "vase", 2.5D, 0.0D, -3.0D).keyP(1.0F, "vase", 2.0D, 2.5D, -2.5D).lookProp("vase", 0.3D)
								.lens(FilmLens.LIGHT_LEAK).tint(WHITE).sound(0.1F, BELL, 1.2F)))
				.act(FilmAct.cast(60)
						// 2: a macro on the willow sprig flicking dew, the drops slowed
						.shot(FilmShot.of(30).keyP(0.0F, "vase", 0.8D, 1.2D, -1.4D).keyP(1.0F, "vase", 0.6D, 1.3D, -1.1D).lookProp("vase", 1.0D)
								.bullet(0.1F, 20).flare(P, "vase", 0.0D, 1.2D, 0.0D).sound(0.2F, TWINKLE, 1.3F)))
				.act(FilmAct.climax(-40)
						// 3: top-down over the healing circle and its dew rings
						.shot(FilmShot.of(36).topDown(A, null, 22.0D, 20.0F).sound(0.0F, HUM_ENERGY, 1.2F).holdForClimax()))
				.act(FilmAct.climax(0)
						// 4: low through the arc of the pour: a rainbow light leak
						.shot(FilmShot.of(30).key(0.0F, A, 3.0D, 0.6D, -4.0D).key(1.0F, A, 2.4D, 0.7D, -3.4D).look(A, 0.0D, 3.0D, 0.0D)
								.lens(FilmLens.LIGHT_LEAK).tint(0xFFC8F0).sound(0.1F, BELL))
						// 5: the pullback; the vase lingers
						.shot(FilmShot.of(30).aerial(P, "vase", 2.0D, 10.0D, 4.0D, 10.0D)));
	}

	/** Wilayah Hidup dan Mati (Abadi, K = 400). */
	private static Film lifeDeathDomain() {
		return Film.of("life_death_domain").prop("lantern_0", 3.0D, 1.0D, 0.0D).k(400).tint(0x6CFF8A)
				.act(FilmAct.cast(0)
						// 1: top-down turning 60° as the split disc unfolds (half green, half grey)
						.shot(FilmShot.of(40).topDown(A, null, 28.0D, 60.0F).sound(0.0F, RISER_LONG))
						// 2: on the dividing line, rolling: half the frame life, half death
						.shot(FilmShot.of(40).key(0.0F, A, 0.0D, 2.0D, -10.0D).key(1.0F, A, 0.0D, 2.0D, -6.0D).look(A, 0.0D, 1.5D, 0.0D)
								.roll(0.0F, 0.0F).roll(1.0F, 15.0F).lens(FilmLens.GRAIN).sound(0.0F, HUM_ENERGY, 0.9F)))
				.act(FilmAct.climax(-60)
						// 3: tracking along the soul river, following one lantern
						.shot(FilmShot.of(60).keyP(0.0F, "lantern_0", -3.0D, 1.0D, -4.0D).keyP(1.0F, "lantern_0", -3.0D, 1.2D, -3.0D)
								.lookProp("lantern_0", 0.0D).lens(FilmLens.LIGHT_LEAK).tint(TEAL).sound(0.2F, TWINKLE).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 4: the disc flips: a whip across the dividing line, green to grey
						.shot(FilmShot.of(20).key(0.0F, A, -2.0D, 2.0D, -8.0D).key(1.0F, A, -1.5D, 2.0D, -7.0D).look(A, 0.0D, 1.0D, 0.0D)
								.whip(0.25F, 180.0F, 6).lens(FilmLens.IMPACT).soundTick(4, SUB_BOOM, 1.0F))
						// 5: a macro on the half-withered lotus monument
						.shot(FilmShot.of(40).key(0.0F, A, 1.6D, 1.2D, -2.6D).key(1.0F, A, 1.2D, 1.4D, -2.0D).look(A, 0.0D, 1.0D, 0.0D)
								.sound(0.0F, HUM_ENERGY, 0.8F)));
	}

	/** Pohon Kuno Bodhisattva (Dewa, K = 600; EXTRA0 novas from 100). */
	private static Film bodhisattvaTree() {
		return Film.of("bodhisattva_tree").prop("tree", 0.0D, 0.0D, -6.0D).prop("canopy", 0.0D, 20.0D, -6.0D).k(600).tint(0x9AE070)
				.act(FilmAct.cast(0)
						// 1: time-lapse growth: the camera rises with the trunk, the canopy tiers opening
						.shot(FilmShot.of(60).keyP(0.0F, "tree", 4.0D, 1.0D, -6.0D).keyP(1.0F, "tree", 5.0D, 30.0D, -8.0D).ease(Ease.IN_OUT)
								.lookProp("tree", 2.0D).lookEnd(P, "tree", 0.0D, 28.0D, 0.0D).lens(FilmLens.LIGHT_LEAK).tint(0xC8E070)
								.sound(0.0F, RISER_LONG)))
				.act(FilmAct.event(Phase.EXTRA0, 1, 0)
						// 2: top-down as the first root arm extends
						.shot(FilmShot.of(30).topDown(A, null, 28.0D, 20.0F).sound(0.2F, ROCK)))
				.act(FilmAct.climax(-80)
						// 3: the fly-through: a spline through the three canopy tiers, leaves smearing past, god rays
						.shot(FilmShot.of(56).keyP(0.0F, "tree", -10.0D, 10.0D, -10.0D).keyP(0.35F, "tree", -3.0D, 16.0D, -2.0D)
								.keyP(0.7F, "tree", 3.0D, 22.0D, 4.0D).keyP(1.0F, "tree", 10.0D, 26.0D, 10.0D).lookProp("tree", 18.0D)
								.lens(FilmLens.SMEAR, FilmLens.ANAMORPHIC).tint(GOLD).sound(0.1F, WHOOSH).sound(0.6F, WHOOSH, 1.1F))
						// 4: Langit Kosmik with the tree of light above the canopy
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(20).keyP(0.0F, "tree", 0.0D, 34.0D, -6.0D).keyP(1.0F, "tree", 0.0D, 35.0D, -5.0D).lookSky(0.0F, 55.0F, 0.0F, 62.0F)
								.cosmic(Dampak.Vista.POHON_CAHAYA).card().flare(P, "tree", 0.0D, 60.0D, 0.0D).sound(0.3F, BELL).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: the fruits burst into light, frozen
						.shot(FilmShot.of(24).orbit(P, "canopy", 8.0D, 8.0D, 0.0D, 0.5D, 0.0F, 80.0F).lookProp("canopy", 0.0D).bullet(0.05F, 20)
								.lens(FilmLens.IMPACT).soundTick(4, SUB_BOOM, 1.0F).sound(0.1F, BELL, 1.1F))
						// 6: the pullback to the bodhi sapling
						.shot(FilmCatalog.aftermath(40, 4.0D, 26.0D).tint(GOLD)));
	}

	// ================================================================ Shadow Dao (30.3.12; karma_domain has no film)

	/** Segel Harimau Alam Baka (Abadi, K = 100). */
	private static Film stygianTigerSeal() {
		return Film.of("stygian_tiger_seal").prop("tally", 0.0D, 2.2D, -12.0D).k(100).tint(0xC01828)
				.act(FilmAct.cast(0)
						// 1: a macro on the flute and its red tassel, Dutch 15°, as the tiger tally rises and splits
						.shot(FilmShot.of(24).key(0.0F, S, 0.5D, 1.5D, 0.9D).key(1.0F, S, 0.4D, 1.6D, 0.7D).look(S, 0.3D, 1.4D, 0.3D).roll(0.0F, 15.0F)
								.vignette(0x200008).lens(FilmLens.GRAIN).sound(0.0F, RISER_SHORT))
						// 2: low as the corpses claw out of the ground around the caster
						.shot(FilmShot.of(40).worm(0.2F).orbit(S, null, 4.0D, 4.5D, 0.2D, 0.2D, 0.0F, 60.0F).look(S, 0.0D, 1.2D, 0.0D)
								.lens(FilmLens.FRINGE).sound(0.2F, THUD).sound(0.6F, THUD, 0.85F)))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.BLACK_DIP)
						// 3: a whip between the two tally halves as they clap together
						.shot(FilmShot.of(16).keyP(0.0F, "tally", 2.0D, 0.5D, -3.0D).keyP(1.0F, "tally", 1.8D, 0.5D, -2.6D).lookProp("tally", 0.0D)
								.whip(0.3F, 70.0F, 5).sound(0.3F, PUNCH).sound(0.4F, BELL, 0.5F))
						// 4: the pullback
						.shot(FilmShot.of(30).aerial(S, null, 2.0D, 12.0D, 4.0D, 12.0D)));
	}

	/** Teknik Pusaka Kunpeng (Dewa, charged 20, K = the release). */
	private static Film kunpeng() {
		return Film.of("kunpeng_art").charged().playerTimed(20).tint(WHITE)
				.act(FilmAct.cast(0).climb()
						// 1: Langit Kosmik at cloud-sea level: the Kun breaches and turns into the Peng, a crash zoom at the turn
						.shot(FilmShot.of(20).key(0.0F, S, 0.0D, 3.0D, -6.0D).key(1.0F, S, 0.0D, 3.5D, -5.0D).lookSky(0.0F, 10.0F, 0.0F, 20.0F)
								.cosmic(Dampak.Vista.LAUT_UTARA).crash(0.7F, 4).lens(FilmLens.ANAMORPHIC).tint(WHITE).sound(0.0F, RISER_LONG)
								.sound(0.5F, WHOOSH)))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.BLACK_DIP)
						// 2: riding on the Peng's head with the dash (the subject's own motion is not counted)
						.shot(FilmShot.of(10).povSubject(1.0F, 1.4F).fov(0.0F, 1.4F).lens(FilmLens.SPEED_LINES, FilmLens.SMEAR).sound(0.0F, WHOOSH))
						// 3: wide behind as the wake erupts and feathers drift
						.shot(FilmShot.of(30).key(0.0F, S, 0.0D, 8.0D, -14.0D).key(1.0F, S, 0.0D, 12.0D, -20.0D).look(S, 0.0D, 1.0D, 0.0D)
								.lens(FilmLens.LIGHT_LEAK).soundAtAim(0.2F, CRUNCH, 1.0F).sound(0.3F, HUM_ENERGY, 0.9F))
						// 4: the pullback
						.shot(FilmShot.of(24).aerial(S, null, 12.0D, 20.0D, 20.0D, 26.0D)));
	}

	// ================================================================ immortal arts (30.3.13)

	/** Segel Kesembilan (Abadi, K = the channel's end, ≤ 160). */
	private static Film ninthHex() {
		return Film.of("ninth_hex").prop("seal", 0.0D, 14.0D, 0.0D).k(160).tint(GOLD)
				.act(FilmAct.cast(0)
						// 1: top-down turning 45° as the first pillars rise on the octagram points
						.shot(FilmShot.of(40).topDown(A, null, 26.0D, 45.0F).sound(0.1F, BELL)))
				.act(FilmAct.climax(-40)
						// 2: inside the domain among the sealed foes, grey grade
						.shot(FilmShot.of(30).orbit(S, null, 10.0D, 10.0D, 1.2D, 1.4D, 0.0F, 60.0F).look(S, 0.0D, 1.5D, 0.0D).grey(0.12F)
								.vignette(0x202020).sound(0.0F, HUM_ENERGY, 0.8F))
						// 3: looking up at the descending octagram seal
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(10).key(0.0F, A, 0.0D, 2.0D, -4.0D).key(1.0F, A, 0.0D, 2.2D, -3.6D).lookProp("seal", 0.0D).cosmic()
								.flare(P, "seal", 0.0D, 0.0D, 0.0D).sound(0.0F, RISER_SHORT).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 4: just under the seal looking down as the ground rushes up
						.shot(FilmShot.of(8).pov("seal", 0.0F, -1.0F).key(0.0F, A, 0.0D, 12.0D, 0.3D).key(1.0F, A, 0.0D, 4.0D, 0.3D).ease(Ease.IN)
								.look(A, 0.0D, 0.0D, 0.0D).lens(FilmLens.SPEED_LINES).sound(0.0F, WHOOSH))
						// 5: an aerial over the stamped seal
						.shot(FilmCatalog.aftermath(40, 6.0D, 28.0D).soundTick(3, SUB_BOOM, 1.0F).sound(0.1F, BELL)));
	}

	/** Gunung Lima Unsur (Abadi, K = 40). */
	private static Film fiveElementsMountain() {
		FilmAct impact = FilmAct.climax(0).cut(FilmShot.Mask.IMPACT);
		// 3: the five-element mountains rise in generating order: whips from peak to peak (three 10-tick shots)
		for (int i = 0; i < 3; i++) {
			if (i > 0) {
				impact.cut(FilmShot.Mask.SMEAR);
			}
			String peak = "peak_" + (i * 2 + 1);
			impact.shot(FilmShot.of(10).key(0.0F, A, -6.0D + i * 6.0D, 3.0D, -14.0D).key(1.0F, A, -5.5D + i * 6.0D, 3.0D, -13.0D)
					.lookProp(peak, 4.0D).whip(0.5F, i % 2 == 0 ? 45.0F : -45.0F, 5).lens(i == 0 ? FilmLens.IMPACT : FilmLens.SMEAR)
					.sound(0.1F, GONG, 0.8F + i * 0.15F));
		}
		// 4: the Wuxing star, the talisman descending onto the circle
		impact.shot(FilmCatalog.aftermath(40, 6.0D, 34.0D).tint(GOLD).sound(0.3F, BELL));
		return Film.of("five_elements_mountain").prop("palm", 0.0D, 32.0D, 0.0D).prop("peak_1", -6.0D, 0.0D, 4.0D).prop("peak_3", 0.0D, 0.0D, 6.0D).prop("peak_5", 6.0D, 0.0D, 4.0D).k(40).tint(GOLD)
				.act(FilmAct.cast(0)
						// 1: looking up as the clouds part and the palm descends
						.shot(FilmShot.of(25).worm(0.3F).key(0.0F, S, 0.8D, 0.3D, 1.5D).key(1.0F, S, 1.0D, 0.3D, 1.8D).lookProp("palm", 0.0D)
								.fov(0.0F, 1.35F).flare(P, "palm", 0.0D, 0.0D, 0.0D).tint(GOLD).sound(0.0F, RISER_LONG))
						// 2: the vertigo on the targets under the growing shadow
						.shot(FilmShot.of(15).key(0.0F, A, 0.0D, 3.0D, -10.0D).key(1.0F, A, 0.0D, 3.5D, -16.0D).look(A, 0.0D, 1.0D, 0.0D)
								.dolly(FilmShot.Target.AIM).vignette(0x0A0806).sound(0.0F, RISER_SHORT).holdForClimax()))
				.act(impact);
	}

	/** Diagram Taiji (Abadi, charged; K = the release, player-timed). */
	private static Film taiChiDiagram() {
		return Film.of("tai_chi_diagram").prop("disc", 0.0D, 1.4D, -12.0D).charged().playerTimed(40).tint(WHITE)
				.act(FilmAct.cast(0)
						// 1: a top-down macro on the yin-yang and the bagua ring spinning between the palms
						.shot(FilmShot.of(30).key(0.0F, S, 0.1D, 3.4D, 0.9D).key(1.0F, S, -0.1D, 3.4D, 0.9D).look(S, 0.0D, 1.2D, 0.9D)
								.roll(0.0F, 0.0F).roll(1.0F, 12.0F).sound(0.0F, HUM_ENERGY, 1.0F)))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.BLACK_DIP)
						// 2: following behind the flying disc as it unrolls into the black-and-white planks ahead
						.shot(FilmShot.of(24).pov("disc", 3.0F, 1.0F).key(0.0F, S, 0.0D, 2.0D, -3.0D).key(1.0F, S, 0.0D, 2.0D, 14.0D).ease(Ease.OUT)
								.look(A, 0.0D, 1.0D, 0.0D).lens(FilmLens.SPEED_LINES).sound(0.0F, WHOOSH))
						// 3: lateral wide along the twin bands, the cells flipping
						.shot(FilmShot.of(30).key(0.0F, A, -10.0D, 3.0D, -8.0D).key(1.0F, A, -10.0D, 3.0D, 4.0D).ease(Ease.LINEAR)
								.look(A, 0.0D, 1.0D, -2.0D).lens(FilmLens.ANAMORPHIC).sound(0.2F, TWINKLE))
						// 4: the disc stands upright; a match cut from its circle to the sun of a short cosmic sky
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(30).key(0.0F, A, 0.0D, 3.0D, -8.0D).key(1.0F, A, 0.0D, 3.5D, -7.0D).lookSky(0.0F, 30.0F, 0.0F, 38.0F)
								.cosmic().lens(FilmLens.LIGHT_LEAK).sound(0.2F, BELL)));
	}

	/** Langkah Penginjak Langit (Dewa, K = 30). */
	private static Film heavenTrampling() {
		FilmShot steps = FilmShot.of(24).orbit(S, null, 6.0D, 6.0D, 1.0D, 1.6D, 0.0F, 170.0F).look(S, 0.0D, 1.6D, 0.0D)
				.lens(FilmLens.SPEED_LINES);
		for (int i = 0; i < 8; i++) {
			// a ring node lit per step, the thud rising in pitch
			steps.soundTick(i * 3, THUD, 0.8F + i * 0.06F);
		}
		return Film.of("heaven_trampling").k(30).tint(GOLD)
				.act(FilmAct.cast(0)
						// 1: climbing with the caster through the nine steps (the subject's own motion is not counted)
						.shot(steps)
						// 2: the apex beat: the caster against the star river
						.shot(FilmShot.of(6).key(0.0F, S, 0.0D, 0.5D, 3.0D).key(1.0F, S, 0.0D, 0.6D, 2.8D).lookSky(0.0F, 35.0F, 0.0F, 38.0F).cosmic()
								.soundTick(0, THUD, 1.3F).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 3: a whip down from the caster to the aim, a crash zoom
						.shot(FilmShot.of(12).key(0.0F, S, 0.0D, 2.5D, -3.0D).key(1.0F, S, 0.0D, 2.4D, -2.6D).look(S, 0.0D, 1.6D, 3.0D)
								.whipPitch(0.1F, 50.0F, 5).crash(0.6F, 3).lens(FilmLens.IMPACT).soundTick(6, SUB_BOOM, 1.0F))
						// 4: an aerial over the three waves
						.shot(FilmCatalog.aftermath(40, 4.0D, 26.0D).soundAtAim(0.1F, CRUNCH, 0.9F).soundAtAim(0.4F, CRUNCH, 0.8F)
								.soundAtAim(0.7F, CRUNCH, 0.7F)));
	}

	private FilmCatalogUltimate() {
	}
}
