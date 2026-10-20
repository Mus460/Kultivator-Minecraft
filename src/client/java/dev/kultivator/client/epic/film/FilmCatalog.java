package dev.kultivator.client.epic.film;

import dev.kultivator.client.epic.sound.FilmSound;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.kultivator.client.epic.film.FilmSpace.A;
import static dev.kultivator.client.epic.film.FilmSpace.S;

/**
 * Every Film Spektakuler (DESIGN 30.3): the four Titah decrees ({@link FilmCatalogTitah}), the Dewa and Abadi ultimates
 * ({@link FilmCatalogUltimate}), the own-art template "ciptaan" (30.3.14) and the Kehancuran crater reveal "kawah"
 * (30.3.9). Camera work only; the props and beats come from the drivers. Built once, lazily; every number is inside the
 * film caps of 30.2.3 (each shot ≤ 6 s, each act ≤ 8 s or a one-act film ≤ 12 s, each film ≤ 20 s of camera time; Titah
 * shots ≤ 4 s and films ≤ 12 s). Hard cuts stay ≥ 10 ticks apart (a montage of the shot lists uses 10-tick shots).
 */
public final class FilmCatalog {

	/** The crater reveal (30.3.9), not a spec. */
	public static final String KAWAH = "kawah";
	/** The own-art template shared by {@code own_art_1..3}. */
	public static final String CIPTAAN = "ciptaan";

	private static @Nullable Map<String, Film> films;

	private FilmCatalog() {
	}

	/** The film of a spec id (or {@link #KAWAH}); null when the spec has none (karma_domain has none, 30.3.12). */
	public static @Nullable Film get(@Nullable String id) {
		if (id == null) {
			return null;
		}
		Map<String, Film> map = all();
		Film film = map.get(id);
		if (film == null && id.startsWith("own_art_")) {
			film = map.get(CIPTAAN);
		}
		return film;
	}

	public static boolean has(@Nullable String id) {
		return get(id) != null;
	}

	/** Every film id with its acts and camera length ({@code /sinematik film daftar}). */
	public static List<String> list() {
		List<String> lines = new ArrayList<>();
		for (Film film : all().values()) {
			StringBuilder line = new StringBuilder(film.id).append(": ");
			for (int i = 0; i < film.acts.size(); i++) {
				FilmAct act = film.acts.get(i);
				if (i > 0) {
					line.append(" | ");
				}
				line.append(act.anchor.name().charAt(0)).append(act.offset >= 0 ? "+" : "").append(act.offset).append(' ')
						.append(act.shots.size()).append("s/").append(act.length()).append('t');
			}
			line.append(" = ").append(film.cameraTicks()).append(" t");
			lines.add(line.toString());
		}
		return lines;
	}

	public static synchronized Map<String, Film> all() {
		if (films == null) {
			Map<String, Film> map = new LinkedHashMap<>();
			FilmCatalogTitah.register(map);
			FilmCatalogUltimate.register(map);
			put(map, kawah());
			put(map, ciptaan());
			films = map;
		}
		return films;
	}

	static void put(Map<String, Film> map, Film film) {
		map.put(film.id, film);
	}

	// ================================================================ shared shot builders

	/**
	 * The impact cut of most films: a low worm angle near the impact in bullet time while the camera circles the frozen
	 * shock ring; the delayed boom after {@code silence} ticks (the silence of 28.4.3 is never filled before it).
	 */
	static FilmShot impactOrbit(int ticks, int silence, double radius, float sweep, float sigma, int bulletTicks) {
		return FilmShot.of(ticks).worm(0.4F).orbit(A, null, radius, radius, 0.4D, 0.6D, -30.0F, sweep).look(A, 0.0D, 2.5D, 0.0D)
				.bullet(sigma, bulletTicks).lens(FilmLens.IMPACT, FilmLens.HEAT_HAZE).soundAtAim(0.0F, FilmSound.Cue.CRUNCH, 0.9F)
				.soundTick(silence, FilmSound.Cue.SUB_BOOM, 1.0F);
	}

	/** The aftermath pullback over the aim: height {@code h0} → {@code h1}, distance growing with it; a light leak. */
	static FilmShot aftermath(int ticks, double h0, double h1) {
		return FilmShot.of(ticks).aerial(A, null, h0, h1, 4.0D + h0 * 0.6D, 8.0D + h1 * 0.5D).lens(FilmLens.LIGHT_LEAK)
				.sound(0.0F, FilmSound.Cue.HUM_ENERGY, 0.7F);
	}

	/** A top-down aerial over the aim at height {@code h}, the frame turning {@code turn}°. */
	static FilmShot topDown(int ticks, double h, float turn) {
		return FilmShot.of(ticks).topDown(A, null, h, turn);
	}

	// ================================================================ the crater reveal (30.3.9)

	/**
	 * Kawah: for an own cast that removed ≥ 512 blocks within 16 b: an aerial rising over the crater looking down 70°
	 * (3 s), then an orbit at 14 b, 18 b up, 60° (2 s); red light leak; rock, then hum. With Hancur Total and a fluid edge
	 * the orbit frames the water walls of the rim.
	 */
	private static Film kawah() {
		return Film.of(KAWAH).k(0).tint(0xFF5A3A).act(FilmAct.cast(0)
				.shot(FilmShot.of(60).key(0.0F, A, 0.0D, 6.0D, -3.0D).key(1.0F, A, 0.0D, 30.0D, -11.0D).ease(Ease.OUT).look(A, 0.0D, 0.0D, 0.0D)
						.lens(FilmLens.LIGHT_LEAK).tint(0xFF5A3A).sound(0.0F, FilmSound.Cue.ROCK, 0.8F).shake(FilmShake.Profile.QUAKE, 0.25F, 30))
				.shot(FilmShot.of(40).orbit(A, null, 14.0D, 14.0D, 18.0D, 18.0D, 0.0F, 60.0F).look(A, 0.0D, -2.0D, 0.0D)
						.sound(0.0F, FilmSound.Cue.HUM_ENERGY, 0.7F)));
	}

	// ================================================================ the own-art template (30.3.14)

	/**
	 * Ciptaan: a hero worm shot in the first Dao's colour at the cast; then up to three form shots on the form events in
	 * order (CUT a whip along the cut, BURST a crash zoom, RAIN a worm looking up, WAVE a lateral track, DOMAIN a turning
	 * top-down, SEAL a macro on the seal); the climax (EXTRA1) cuts under the impact frames to an aerial pullback. One
	 * cosmic beat (the Dao vista) when the composition runs long. ≤ 6 shots.
	 */
	private static Film ciptaan() {
		Film film = Film.of(CIPTAAN).playerTimed(60).maxEventActs(3);
		film.act(FilmAct.cast(0).shot(FilmShot.of(24).worm(0.4F).key(0.0F, S, 1.6D, 0.4D, 3.2D).key(1.0F, S, 1.0D, 0.4D, 2.4D)
				.look(S, 0.0D, 1.7D, 0.0D).fov(0.0F, 1.0F).fov(1.0F, 1.15F).lens(FilmLens.LIGHT_LEAK, FilmLens.FLARE, FilmLens.TITLE).card()
				.sound(0.0F, FilmSound.Cue.RISER_SHORT, 1.0F)));
		// CUT
		film.act(FilmAct.event(Phase.extra(2), 1, 0).shot(FilmShot.of(14).key(0.0F, S, -1.2D, 1.7D, -2.2D).key(1.0F, S, -0.8D, 1.6D, -1.6D)
				.look(A, 0.0D, 1.2D, 0.0D).whip(0.2F, 50.0F, 5).sound(0.2F, FilmSound.Cue.WHOOSH, 1.1F)));
		// BURST
		film.act(FilmAct.event(Phase.extra(3), 1, 0).shot(FilmShot.of(14).key(0.0F, A, 3.0D, 2.0D, -9.0D).key(1.0F, A, 2.5D, 2.0D, -7.5D)
				.look(A, 0.0D, 1.0D, 0.0D).crash(0.65F, 3).soundAtAim(0.0F, FilmSound.Cue.CRUNCH, 1.0F)));
		// RAIN
		film.act(FilmAct.event(Phase.extra(4), 1, 0).shot(FilmShot.of(16).worm(0.3F).key(0.0F, A, -3.0D, 0.3D, -4.0D).key(1.0F, A, -2.0D, 0.3D, -3.0D)
				.lookSky(10.0F, 40.0F, 0.0F, 55.0F).sound(0.0F, FilmSound.Cue.TWINKLE, 0.9F)));
		// WAVE
		film.act(FilmAct.event(Phase.extra(5), 1, 0).shot(FilmShot.of(16).key(0.0F, A, -8.0D, 1.5D, -6.0D).key(1.0F, A, -8.0D, 1.5D, 4.0D)
				.ease(Ease.LINEAR).look(A, 0.0D, 1.0D, 0.0D).sound(0.0F, FilmSound.Cue.WHOOSH, 0.8F)));
		// DOMAIN
		film.act(FilmAct.event(Phase.extra(6), 1, 0).shot(topDown(20, 22.0D, 40.0F).sound(0.0F, FilmSound.Cue.HUM_ENERGY, 1.0F)));
		// SEAL
		film.act(FilmAct.event(Phase.extra(7), 1, 0).shot(FilmShot.of(16).key(0.0F, A, 1.5D, 2.2D, -2.6D).key(1.0F, A, 1.0D, 2.0D, -2.0D)
				.look(A, 0.0D, 1.5D, 0.0D).lens(FilmLens.FLARE).sound(0.0F, FilmSound.Cue.BELL, 0.9F)));
		// one cosmic beat with the Dao vista right after the hero shot ("Perjalanan langit" decides it)
		film.act(FilmAct.cast(24).climb().cut(FilmShot.Mask.BLACK_DIP)
				.shot(FilmShot.of(20).key(0.0F, S, 0.0D, 3.0D, -4.0D).key(1.0F, S, 0.0D, 3.5D, -3.0D).lookSky(0.0F, 55.0F, 0.0F, 62.0F)
						.cosmic(Dampak.Vista.BY_DAO).lens(FilmLens.ANAMORPHIC)));
		// the climax (EXTRA1): the impact frames in the Dao colours, then the aerial pullback
		film.act(FilmAct.climax(0).cut(FilmShot.Mask.IMPACT)
				.shot(impactOrbit(20, 6, 9.0D, 60.0F, 0.06F, 16).lens(FilmLens.FRINGE))
				.shot(aftermath(36, 4.0D, 26.0D)));
		return film;
	}
}
