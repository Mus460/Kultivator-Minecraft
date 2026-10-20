package dev.kultivator.client.epic.film;

import dev.kultivator.technique.spec.dsl.Dampak;

import java.util.Map;

import static dev.kultivator.client.epic.film.FilmSpace.A;
import static dev.kultivator.client.epic.film.FilmSpace.P;
import static dev.kultivator.client.epic.film.FilmSpace.S;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.BELL;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.CRUNCH;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.GLASS;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.GONG;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.HUM_ENERGY;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.PUNCH;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.RISER_LONG;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.RISER_SHORT;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.ROCK;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.SUB_BOOM;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.TWINKLE;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.WHOOSH;
import static dev.kultivator.client.epic.sound.FilmSound.Cue.ZAP;

/**
 * The films of the four Titah decrees (DESIGN 30.3.1-30.3.4). Each is one continuous journey: a build-up act that ends on
 * the server's climax (re-timed by Kecepatan: a longer build-up starts it later, a shorter one shortens the cosmic shot
 * first) and the impact act on the climax event, joined under the impact frame with no player camera between. Every shot
 * ≤ 4 s, every film ≤ 12 s (29.16). Props the Titah world pieces do not track fall back to their aim-space keys.
 */
final class FilmCatalogTitah {

	private static final int GOLD = 0xFFD54A;
	private static final int EMBER = 0xFF7A1A;
	private static final int VIOLET = 0xB57CFF;
	private static final int JADE = 0x5FD6A0;

	static void register(Map<String, Film> map) {
		FilmCatalog.put(map, pedang());
		FilmCatalog.put(map, tiang());
		FilmCatalog.put(map, bintang());
		FilmCatalog.put(map, gunung());
	}

	/** Titah Pedang Pemisah Langit (K = 100 at Kecepatan 1). */
	private static Film pedang() {
		return Film.of("titah_pedang").prop("sword", 0.0D, 30.0D, 0.0D).titah().k(100).tint(GOLD)
				.act(FilmAct.cast(0).climb()
						// 1: at the caster's boots, up past the raised arm to the falling pillar; FL on the pillar; lock-on
						.shot(FilmShot.of(18).worm(0.3F).key(0.0F, S, 0.8D, 0.3D, 1.2D).key(1.0F, S, 1.3D, 0.3D, 1.9D).look(A, 0.0D, 30.0D, 0.0D)
								.fov(0.0F, 1.0F).fov(1.0F, 1.2F).flare(A, null, 0.0D, 40.0D, 0.0D).sound(0.0F, RISER_SHORT))
						// 2: the climb: creeps, then rockets up the pillar (an ease-in speed ramp); speed lines; the clouds close over its end
						.shot(FilmShot.of(22).key(0.0F, S, 2.0D, 2.0D, -1.0D).key(1.0F, A, 3.0D, 40.0D, -4.0D).ease(Ease.IN)
								.look(A, 0.0D, 50.0D, 0.0D).lens(FilmLens.SPEED_LINES).sound(0.0F, RISER_LONG).sound(0.5F, WHOOSH))
						// 3: Langit Kosmik: the Lingxiao gate opens above, the sword is drawn from it; slow push, roll to 6°
						.cut(FilmShot.Mask.CLOUD_WIPE)
						.shot(FilmShot.of(36).key(0.0F, A, 0.0D, 30.0D, -9.0D).key(1.0F, A, 0.0D, 31.0D, -5.0D).lookSky(0.0F, 68.0F, 0.0F, 72.0F)
								.roll(0.0F, 0.0F).roll(1.0F, 6.0F).cosmic(Dampak.Vista.ISTANA_LANGIT).card()
								.lens(FilmLens.ANAMORPHIC, FilmLens.LIGHT_LEAK).tint(GOLD).flare(A, null, 0.0D, 90.0D, 6.0D).sound(0.3F, BELL))
						// 4: match cut to riding the falling sword point-first down the pillar (keys when it is not tracked)
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(24).pov("sword", 3.0F, 0.6F).key(0.0F, A, 0.6D, 36.0D, -2.0D).key(1.0F, A, 0.6D, 7.0D, -2.0D)
								.ease(Ease.IN).look(A, 0.0D, 0.0D, 0.0D).fov(0.0F, 1.3F)
								.lens(FilmLens.SMEAR, FilmLens.SPEED_LINES, FilmLens.FRINGE).sound(0.0F, WHOOSH).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: under the impact frame: low, 12 b out, bullet time circling the planted sword and the frozen ring
						.shot(FilmCatalog.impactOrbit(20, 8, 12.0D, 40.0F, 0.05F, 18))
						// 6: the cleave runs both ways: a whip one way, a dip, a whip back the other way
						.shot(FilmShot.of(12).key(0.0F, A, 5.0D, 2.0D, -3.0D).key(1.0F, A, 5.0D, 2.0D, -2.5D).look(A, 0.0D, 0.8D, 0.0D)
								.whip(0.15F, 90.0F, 5).sound(0.15F, WHOOSH).sound(0.3F, ROCK))
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(12).key(0.0F, A, -5.0D, 2.0D, -3.0D).key(1.0F, A, -5.0D, 2.0D, -2.5D).look(A, 0.0D, 0.8D, 0.0D)
								.whip(0.1F, -180.0F, 6).sound(0.1F, WHOOSH, 0.9F))
						// 7: the aerial pullback over the split land, the sword planted at its centre
						.shot(FilmCatalog.aftermath(40, 6.0D, 40.0D)));
	}

	/** Titah Tiang Penenang Samudra (K = 120). */
	private static Film tiang() {
		return Film.of("titah_tiang").prop("pillar", 0.0D, 20.0D, 0.0D).titah().k(120).tint(EMBER)
				.act(FilmAct.cast(0).climb()
						// 1: a low dolly behind the caster through the javelin throw
						.shot(FilmShot.of(20).key(0.0F, S, -2.5D, 0.6D, -3.0D).key(1.0F, S, -1.5D, 0.9D, -1.5D).look(S, 0.6D, 1.8D, 1.5D)
								.sound(0.0F, RISER_SHORT))
						// 2: tracking up beside the rising pillar, 4 b aside, looking up its length; ember trail
						.shot(FilmShot.of(30).pov("pillar", 0.0F, -2.0F).key(0.0F, A, 4.0D, 4.0D, -1.0D).key(1.0F, A, 4.0D, 34.0D, -1.0D)
								.ease(Ease.IN).look(A, 0.0D, 46.0D, 0.0D).lens(FilmLens.HEAT_HAZE, FilmLens.SPEED_LINES).sound(0.0F, WHOOSH)
								.sound(0.1F, RISER_LONG))
						// 3: from below the sea of clouds: the camera pulls back while the FOV narrows (vertigo) as the pillar grows
						.cut(FilmShot.Mask.CLOUD_WIPE)
						.shot(FilmShot.of(40).key(0.0F, A, 6.0D, 26.0D, -6.0D).key(1.0F, A, 9.0D, 28.0D, -14.0D).ease(Ease.IN_OUT)
								.look(A, 0.0D, 40.0D, 0.0D).dolly(FilmShot.Target.AIM).fov(0.0F, 1.3F).fov(1.0F, 0.75F)
								.cosmic(Dampak.Vista.LAUT_TIMUR).card().lens(FilmLens.ANAMORPHIC).tint(0xFFB040).flare(A, null, 0.0D, 44.0D, 0.0D)
								.sound(0.4F, GONG))
						// 4: a wide static 30 b out at the horizon: the white-hot streak crosses from the top of the frame
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(24).key(0.0F, A, -26.0D, 3.0D, -14.0D).key(1.0F, A, -25.0D, 3.0D, -13.5D).look(A, 0.0D, 8.0D, 0.0D)
								.lens(FilmLens.HEAT_HAZE, FilmLens.FRINGE).sound(0.2F, ZAP).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: a crash zoom onto the impact under the frames, the silence holding
						.shot(FilmShot.of(10).key(0.0F, A, 0.0D, 6.0D, -16.0D).key(1.0F, A, 0.0D, 5.5D, -14.0D).look(A, 0.0D, 1.0D, 0.0D).crash(0.6F, 3)
								.lens(FilmLens.IMPACT))
						// 6: high and wide in slow motion as the flattening wave blows the grass outward; the delayed boom
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(30).aerial(A, null, 22.0D, 30.0D, 16.0D, 22.0D).bullet(0.1F, 20).lens(FilmLens.LIGHT_LEAK).tint(EMBER)
								.soundTick(2, SUB_BOOM, 1.0F).soundAtAim(0.0F, CRUNCH, 0.8F).shake(FilmShake.Profile.QUAKE, 0.3F, 20))
						// 7: a low orbit round the glowing hoops of the planted pillar, steam haze
						.shot(FilmShot.of(40).orbit(A, null, 7.0D, 7.0D, 1.0D, 1.4D, 20.0F, 50.0F).look(A, 0.0D, 3.5D, 0.0D)
								.lens(FilmLens.HEAT_HAZE).sound(0.0F, HUM_ENERGY, 0.8F)));
	}

	/** Titah Tujuh Bintang Biduk Utara (K = 140). */
	private static Film bintang() {
		Film film = Film.of("titah_bintang").prop("crystal7", 0.0D, 1.0D, 0.0D).titah().k(140).tint(VIOLET);
		FilmAct build = FilmAct.cast(0).climb()
				// 1: the telescope: from the pointing finger the look tilts up to the seven stars; FOV narrows
				.shot(FilmShot.of(24).key(0.0F, S, 0.5D, 1.8D, -1.0D).key(1.0F, S, 0.5D, 1.9D, -0.8D).lookSky(0.0F, 15.0F, 0.0F, 60.0F)
						.fov(0.0F, 1.0F).fov(1.0F, 0.7F).flare(S, null, 0.0D, 60.0D, 30.0D).tint(0xE8F0FF).sound(0.3F, TWINKLE))
				// 2: Langit Kosmik with the Dipper: drifting through the star river while the nodes light and their lines draw
				.cut(FilmShot.Mask.BLACK_DIP)
				.shot(FilmShot.of(36).key(0.0F, S, 0.0D, 3.0D, -4.0D).key(1.0F, S, 1.5D, 3.5D, -2.0D).lookSky(-10.0F, 60.0F, 10.0F, 66.0F)
						.roll(0.0F, 0.0F).roll(1.0F, 10.0F).cosmic(Dampak.Vista.BIDUK_UTARA).card().lens(FilmLens.ANAMORPHIC).tint(VIOLET)
						.sound(0.1F, TWINKLE, 0.9F).sound(0.4F, TWINKLE, 1.0F).sound(0.7F, TWINKLE, 1.1F));
		// 3: the montage of the falling stars: four angles chosen from the seven of the shot list (10-tick cuts)
		build.cut(FilmShot.Mask.BLACK_DIP).shot(FilmShot.of(10).worm(0.3F).key(0.0F, A, 6.0D, 0.3D, -6.0D).key(1.0F, A, 5.5D, 0.3D, -5.5D)
				.lookSky(20.0F, 45.0F, 15.0F, 30.0F).soundAtAim(0.6F, CRUNCH, 1.0F));
		build.cut(FilmShot.Mask.BLACK_DIP).shot(FilmShot.of(10).topDown(A, null, 36.0D, 10.0F).soundAtAim(0.5F, CRUNCH, 1.1F));
		build.cut(FilmShot.Mask.BLACK_DIP).shot(FilmShot.of(10).key(0.0F, A, -12.0D, 2.0D, 0.0D).key(1.0F, A, -12.0D, 2.0D, 1.0D)
				.look(A, 0.0D, 4.0D, 0.0D).sound(0.4F, GLASS));
		build.cut(FilmShot.Mask.BLACK_DIP).shot(FilmShot.of(10).key(0.0F, S, 0.7D, 1.9D, -1.4D).key(1.0F, S, 0.7D, 1.9D, -1.2D)
				.look(A, 0.0D, 2.0D, 0.0D).soundAtAim(0.5F, CRUNCH, 0.9F));
		// 4: Yaoguang, the seventh: bullet time while the camera circles the growing crystal
		build.shot(FilmShot.of(20).orbit(P, "crystal7", 5.0D, 5.0D, 1.0D, 1.6D, 0.0F, 90.0F).bullet(0.05F, 24).lens(FilmLens.FLARE)
				.sound(0.2F, TWINKLE, 1.2F).holdForClimax());
		film.act(build);
		film.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
				// 5: top-down 48 b turning 30° as the lines ignite into burning trenches: the constellation from above
				.shot(FilmShot.of(40).topDown(A, null, 46.0D, 30.0F).lens(FilmLens.IMPACT, FilmLens.LIGHT_LEAK).tint(VIOLET)
						.soundTick(6, SUB_BOOM, 1.0F))
				// 6: a crane down to the caster's silhouette against the burning lines
				.shot(FilmShot.of(30).key(0.0F, S, 0.0D, 14.0D, -10.0D).key(1.0F, S, 0.0D, 2.0D, -5.0D).ease(Ease.IN_OUT).look(S, 0.0D, 1.6D, 0.0D)
						.sound(0.0F, HUM_ENERGY, 0.8F)));
		return film;
	}

	/** Titah Pemindah Gunung (K = 90). */
	private static Film gunung() {
		return Film.of("titah_gunung").titah().k(90).tint(JADE)
				.act(FilmAct.cast(0).climb()
						// 1: hero worm on the jade seal leaving the hand and rising
						.shot(FilmShot.of(20).worm(0.4F).key(0.0F, S, 1.2D, 0.4D, 2.6D).key(1.0F, S, 1.0D, 0.4D, 2.2D).look(S, 0.0D, 3.5D, 0.5D)
								.flare(S, null, 0.0D, 4.0D, 0.5D).tint(JADE).sound(0.0F, RISER_SHORT))
						// 2: top-down as the light grid draws cell by cell
						.shot(FilmShot.of(26).topDown(A, null, 38.0D, 20.0F).sound(0.2F, TWINKLE).sound(0.5F, TWINKLE, 1.1F).sound(0.8F, TWINKLE, 1.2F))
						// 3: Langit Kosmik between the stooping giants' arms, looking up at the seal
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(24).key(0.0F, A, 0.0D, 6.0D, -8.0D).key(1.0F, A, 0.0D, 7.0D, -6.0D).lookSky(0.0F, 50.0F, 0.0F, 58.0F)
								.cosmic(Dampak.Vista.KUA_E).card().lens(FilmLens.ANAMORPHIC).tint(GOLD).sound(0.3F, GONG))
						// 4: inside the square under the descending seal: its shadow covers the lens, FOV widening
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(20).worm(0.3F).key(0.0F, A, 1.5D, 0.3D, -1.5D).key(1.0F, A, 1.0D, 0.3D, -1.0D).lookSky(0.0F, 70.0F, 0.0F, 80.0F)
								.fov(0.0F, 1.0F).fov(1.0F, 1.3F).vignette(0x081810).sound(0.0F, RISER_LONG).holdForClimax()))
				.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
						// 5: a spiral crane rising among the torn cells in slow motion while the slabs turn
						.shot(FilmShot.of(30).orbit(A, null, 10.0D, 16.0D, 2.0D, 34.0D, 0.0F, 120.0F).look(A, 0.0D, 6.0D, 0.0D).bullet(0.3F, 20)
								.lens(FilmLens.IMPACT, FilmLens.SMEAR).soundTick(6, SUB_BOOM, 1.0F).sound(0.3F, ROCK).sound(0.6F, ROCK, 0.9F))
						// 6: over the shoulder as the levitated foes slam down
						.cut(FilmShot.Mask.BLACK_DIP)
						.shot(FilmShot.of(12).key(0.0F, S, 0.7D, 2.0D, -1.6D).key(1.0F, S, 0.7D, 1.9D, -1.4D).look(A, 0.0D, 1.0D, 0.0D)
								.sound(0.3F, PUNCH))
						// 7: looking down into the stepped glowing pit as the slabs drift away
						.shot(FilmShot.of(24).key(0.0F, A, 0.0D, 14.0D, -6.0D).key(1.0F, A, 0.0D, 22.0D, -8.0D).look(A, 0.0D, -6.0D, 0.0D)
								.lens(FilmLens.LIGHT_LEAK).tint(VIOLET).sound(0.0F, HUM_ENERGY, 0.7F)));
	}

	private FilmCatalogTitah() {
	}
}
