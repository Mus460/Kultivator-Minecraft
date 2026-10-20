package dev.kultivator.client.pusakalangit.cinema;

import dev.kultivator.client.dampak.DampakClient;
import dev.kultivator.client.dampak.cinema.TargetMark;
import dev.kultivator.client.dampak.cinema.Vistas;
import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.screen.CloudWipe;
import dev.kultivator.client.epic.screen.EpicScreen;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Film Pusaka (Phase 10b, DESIGN 29.4): the long sky journey of a Titah decree, told inside the 7b speed caps. Our
 * cultivation re-telling of a sky-strike film (presentation ideas only): the camera never travels kilometres; it plays
 * short world-anchored segments ({@link Shots#pusakaFilm}, each ≤ 80 ticks and validated: ≤ 7.6 b/s, ≤ 25°/s) joined by
 * masked cuts under the {@link CloudWipe}, and the height is sold by a sky grade, the vista and speed lines.
 * <ol>
 * <li>F1 <b>Tanda</b> (0-0.15): over the shoulder onto the mark while the Tanda Langit pillar and ring lock on;</li>
 * <li>F2 <b>Naik</b> (0.15-0.35): rising beside the pillar while qi motes stream up; the clouds close in;</li>
 * <li>cut: masked by the cloud wipe;</li>
 * <li>F3 <b>Istana</b> (0.35-0.65): high above the aim, a slow 15° turn, the decree's vista ({@link Vistas}) and its jade
 * title card;</li>
 * <li>cut: a second wipe, at least 14 ticks after the first (one luminance event each);</li>
 * <li>F4 <b>Turun</b> (0.65-1): behind and above the falling weapon, riding it down with speed lines, a vignette and an
 * FOV punch; the last segment holds up to 20 ticks for a late climax;</li>
 * <li>impact: an instant cut under the impact frame, either to the eye or to</li>
 * <li>F5 <b>Akibat</b> (40 ticks, only while still safe): a low crane circling the planted weapon or the pit by ≤ 20°,
 * then the normal 10-tick blend back to the eye.</li>
 * </ol>
 * The film times itself to the server: the CAST event's {@code duration} is the number of ticks to the climax. A film
 * longer than 240 ticks (Kecepatan below 1) starts at {@code climax − 240}; below 120 ticks F3 shortens first, then F2 is
 * dropped; below 40 ticks the 28.3 Singkat crane plays instead. The camera never moves faster than the caps.
 * <p>
 * Settings (29.4): Kamera <b>Penuh</b> plus the decree's Film <b>Penuh</b> give the film; Kamera Singkat or the decree's
 * Film Singkat give the 28.3 crane; Mati gives nothing. "Kurangi gerak" (camera Mati) and the global "Perjalanan langit:
 * Mati" always win (the latter falls back to the crane, like every Phase 10 ride); "Pertama" acts as "Selalu" for Titah.
 * Gates at every act: the local player's own cast, FLAG_SAFE, no enemy within 24 b, health ≥ 50 %, the 23.7.4 guards
 * through {@link CinematicDirector#shoot}. Any movement key ends it, Backspace skips it, a hurt snaps back; a chained
 * segment only starts while the previous one still runs ({@link CinematicDirector#shooting}), so a skip is never undone.
 * The server never waits for the camera: the strike lands when the server says. Client thread only.
 */
public final class PusakaFilm {

	private static final String K = "kultivator.titah_client.";
	/** The longest film (29.0: only the whole film may run 240 ticks; every segment stays ≤ 80). */
	public static final int MAX_FILM = 240;
	/** Below this the film falls back to the Singkat crane. */
	public static final int MIN_FILM = 40;
	/** The aftermath crane after the climax (F5). */
	public static final int AFTERMATH = 40;
	private static final int SEGMENT = 80;
	private static final String[] GLYPHS = {"劍", "柱", "斗", "山"};
	private static final int JADE = 0x2E8963;

	private static @Nullable Film current;
	private static String lastRefusal = "";
	/** When each decree's jade title card last showed (the EXTRA_TITAH beat or the film), so it never shows twice. */
	private static final long[] CARD_AT = new long[4];

	private PusakaFilm() {
	}

	/** One planned or running film of the local player's decree. */
	private static final class Film {
		final Scene scene;
		final TechniqueSpec spec;
		final Dampak d;
		final long key;
		final Vec3 aim;
		final int rgb;
		final int casterId;
		final int skill;
		final float radius;
		final boolean test;
		boolean full;
		boolean running;
		boolean cancelled;
		boolean climaxed;
		boolean revealOnClimax;
		boolean graded;

		Film(Scene scene, TechniqueSpec spec, Dampak d, long key, Vec3 aim, int rgb, int casterId, int skill, float radius, boolean test) {
			this.scene = scene;
			this.spec = spec;
			this.d = d;
			this.key = key;
			this.aim = aim;
			this.rgb = rgb;
			this.casterId = casterId;
			this.skill = skill;
			this.radius = radius;
			this.test = test;
		}
	}

	// ================================================================ the TechFx stream (from SkyRide, own casts only)

	/** One event of the local player's own decree (a row with {@code Dampak.Ride.FILM}); never a disciple's or an echo. */
	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Dampak d, Phase phase, Vec3 aim) {
		long key = ((long) p.caster() << 32) | (spec.netId() & 0xFFFFFFFFL);
		Film film = current;
		if (phase == Phase.ABORT) {
			if (film != null && film.key == key) {
				abandon(film);
			}
			return;
		}
		if (phase == Phase.END) {
			// a decree's own schedule may outlive the spec's keep-alive step: END never cuts the film (every segment ends by
			// itself within its validated length, the last one 20 ticks after its path at most)
			return;
		}
		boolean climax = p.has(Payloads.TechFx.FLAG_CLIMAX) && phase != Phase.PULSE && phase != Phase.MASS_HIT;
		if (climax) {
			if (film != null && film.key == key) {
				onClimax(film);
			}
			return;
		}
		if (phase != Phase.CAST) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		int total = p.duration() > 0 ? p.duration() : d.climaxAt() > 0 ? d.climaxAt() : 100;
		Scene scene = EpicDirector.dampakScene(p.caster(), true, minecraft.player.position(), p.seed(), Math.max(80, total + AFTERMATH + 60));
		if (scene == null || !scene.own) {
			return;
		}
		if (film != null) {
			abandon(film);
		}
		Film fresh = new Film(scene, spec, d, key, TargetMark.ground(aim), colour(spec, d), p.caster(), skillOf(spec, p), p.radius(), false);
		current = fresh;
		plan(fresh, total, p.has(Payloads.TechFx.FLAG_LOW_LOAD));
	}

	/**
	 * {@code /sinematik pusaka film <skill>}: the film of a decree alone, its climax {@code ticks} from now, still under every
	 * gate (only the cooldowns of a test scene are skipped). Null = started, else the refusal reason.
	 */
	public static @Nullable String test(TechniqueSpec spec, int skill, Vec3 aim, int ticks) {
		Dampak d = spec.dampak();
		if (d == null) {
			return "dampak";
		}
		if (CinematicDirector.mode() != EpicSettings.Camera.PENUH) {
			return "camera";
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return "player";
		}
		Scene scene = EpicDirector.testScene();
		if (scene == null) {
			return "scene";
		}
		if (current != null) {
			abandon(current);
		}
		int length = Math.max(MIN_FILM, Math.min(MAX_FILM, ticks));
		Film film = new Film(scene, spec, d, -1L, TargetMark.ground(aim), colour(spec, d), minecraft.player.getId(), skill, 48.0F, true);
		current = film;
		film.full = true;
		lastRefusal = "";
		startFilm(film, length);
		if (!film.running || current != film || film.cancelled) {
			// a refused gate: no crane either for a test
			abandon(film);
			return lastRefusal.isEmpty() ? "gate" : lastRefusal;
		}
		scene.at(length, () -> {
			if (current == film && !film.cancelled) {
				DampakClient.testClimax(spec, d, film.aim, true);
				onClimax(film);
			}
		});
		return null;
	}

	/** Ends every film (skip, stop, dimension change, disconnect). */
	public static void clear() {
		Film film = current;
		if (film != null) {
			film.cancelled = true;
		}
		current = null;
	}

	/** True while a film is planned or running. */
	public static boolean active() {
		return current != null;
	}

	/** True while a full film is planned or running: it shows the decree's title card itself in F3. */
	public static boolean cardPlanned() {
		Film film = current;
		return film != null && film.full && !film.cancelled && !film.climaxed;
	}

	/** The decree's title card just showed elsewhere (the EXTRA_TITAH beat): the film leaves it out. */
	public static void noteCard(int skill) {
		CARD_AT[Math.max(0, Math.min(3, skill))] = Util.getMillis();
	}

	/** Why the last film fell back (for {@code /sinematik pusaka info}); empty when none did. */
	public static String lastRefusal() {
		return lastRefusal;
	}

	// ================================================================ planning

	private static void plan(Film film, int total, boolean lowLoad) {
		EpicSettings.Camera camera = CinematicDirector.mode();
		EpicSettings.TitahFilm choice = EpicSettings.titahFilm(film.skill);
		if (camera == EpicSettings.Camera.MATI || choice == EpicSettings.TitahFilm.MATI) {
			// "Kurangi gerak", the global camera Mati or the decree's own Mati: no camera at all
			lastRefusal = camera == EpicSettings.Camera.MATI ? "camera" : "decree";
			current = null;
			return;
		}
		boolean full = camera == EpicSettings.Camera.PENUH && choice == EpicSettings.TitahFilm.PENUH
				&& EpicSettings.skyRide() != EpicSettings.SkyRide.MATI && !lowLoad && total >= MIN_FILM;
		if (!full) {
			lastRefusal = lowLoad ? "load" : total < MIN_FILM ? "short" : EpicSettings.skyRide() == EpicSettings.SkyRide.MATI ? "skyride" : "singkat";
			planCrane(film, total, 0);
			return;
		}
		int length = Math.min(MAX_FILM, total);
		film.full = true;
		film.scene.at(Math.max(0, total - length), () -> {
			if (alive(film)) {
				startFilm(film, length);
			}
		});
	}

	/** The 28.3 Singkat crane: 2 s looking up at the falling weapon, ending on the climax (a reveal after it when shorter). */
	private static void planCrane(Film film, int climaxIn, int elapsed) {
		film.full = false;
		if (CinematicDirector.mode() == EpicSettings.Camera.MATI) {
			current = null;
			return;
		}
		if (climaxIn - elapsed >= 40) {
			film.scene.at(climaxIn - elapsed - 40, () -> {
				if (alive(film)) {
					crane(film, Shots.craneLook(3.0F, 1.6F, 6.0F, 9.0F, 2.0F).lookAt(film.aim.add(0.0D, 10.0D, 0.0D)));
				}
			});
		} else {
			film.revealOnClimax = true;
		}
	}

	private static void crane(Film film, Shots.Shot shot) {
		EpicDirector.refreshFlags(film.scene);
		CinematicDirector.shoot(film.scene, shot, CinematicDirector.KIND_ULTIMATE, film.scene.priority, true);
	}

	/**
	 * The act lengths {F1, F2, F3, F4} of a film of {@code length} ticks (29.4): the shares 0.15 / 0.20 / 0.30 / 0.35 from
	 * 120 ticks up; below that F3 shrinks with the rest until it would go under 16 ticks, then F2 is dropped (0).
	 */
	static int[] acts(int length) {
		int f1 = Math.max(10, Math.round(length * 0.15F));
		int f4 = Math.max(16, Math.round(length * 0.35F));
		int rest = Math.max(0, length - f1 - f4);
		int f2 = Math.max(10, Math.round(length * 0.20F));
		int f3;
		if (rest >= f2 + 16) {
			f3 = rest - f2;
		} else {
			f2 = 0;
			f3 = rest;
		}
		return new int[]{f1, f2, f3, f4};
	}

	// ================================================================ the film

	private static void startFilm(Film film, int length) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Scene scene = film.scene;
		if (player == null) {
			abandon(film);
			return;
		}
		String unsafe = unsafe(film);
		if (unsafe != null) {
			// a failed gate falls back to the Singkat crane, then to nothing
			lastRefusal = unsafe;
			planCrane(film, length, 0);
			return;
		}
		Vec3 feet = player.position();
		Vec3 eye = player.getEyePosition();
		Vec3 aim = film.aim;
		Vec3 flat = new Vec3(aim.x - feet.x, 0.0D, aim.z - feet.z);
		double distance = flat.length();
		Vec3 h;
		if (distance < 1.0D) {
			Vec3 look = player.getViewVector(1.0F);
			h = new Vec3(look.x, 0.0D, look.z);
			h = h.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : h.normalize();
		} else {
			h = flat.scale(1.0D / distance);
		}
		Vec3 side = new Vec3(-h.z, 0.0D, h.x);
		Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
		int[] acts = acts(length);
		int f1 = acts[0];
		int f2 = acts[1];
		int f3 = acts[2];
		int f4 = acts[3];
		int t2 = f1;
		int cut1 = f1 + f2;
		int cut2 = cut1 + f3;

		// F1 Tanda: over the shoulder onto the mark while it locks on
		Vec3 f1From = eye.subtract(h.scale(1.8D)).add(side.scale(0.7D)).add(0.0D, 0.4D, 0.0D);
		Vec3 f1To = eye.subtract(h.scale(2.6D)).add(side.scale(1.0D)).add(0.0D, 1.0D, 0.0D);
		if (!act(film, f1From, f1To, aim.add(0.0D, 2.0D, 0.0D), aim.add(0.0D, f2 > 0 ? 8.0D : 4.0D, 0.0D), f1, false, true)) {
			lastRefusal = "shot";
			planCrane(film, length, 0);
			return;
		}
		film.running = true;
		lastRefusal = "";

		// F2 Naik: up beside the pillar, qi motes streaming up it (Bintang's mark has no pillar: lend it one)
		Vec3 f2To = feet.add(h.scale(Math.min(10.0D, distance * 0.35D))).add(side.scale(4.0D)).add(0.0D, 14.0D, 0.0D);
		if (f2 > 0) {
			scene.at(t2, () -> {
				if (!chained(film)) {
					return;
				}
				if (!act(film, f1To, f2To, aim.add(0.0D, 8.0D, 0.0D), aim.add(0.0D, 44.0D, 0.0D), f2, false, true)) {
					abandon(film);
				}
			});
			if (film.d.mark() == Dampak.Mark.RING_ONLY) {
				scene.at(t2, () -> {
					if (alive(film)) {
						TargetMark.ridePillar(scene, aim, film.rgb, f2 + 10);
					}
				});
			}
			scene.add(Element.every(t2, f2, (tick, self) -> {
				if (alive(film) && tick % 2 == 0) {
					motes(aim, film.rgb);
				}
			}));
		}

		// the clouds close in for the first cut (we rise into them); the sky turns to the vista's grade behind them
		scene.at(Math.max(0, cut1 - 8), () -> {
			if (alive(film)) {
				film.graded = true;
				SkyDirector.grade(grade(film.d.vista()), 0.9F, 0.6F, 20, scene.priority, scene.pos, true, 1.0F, scene.pos.y);
			}
		});
		scene.at(Math.max(0, cut1 - 6), () -> {
			if (alive(film)) {
				CloudWipe.start(4, 4, 6, 0xF4F6FF);
			}
		});

		// F3 Istana: above the aim, a slow 15° turn, the vista and the decree's title card
		boolean low = Vistas.lowVista(film.d.vista());
		Vec3 v0 = aim.add(side.scale(8.0D)).add(0.0D, 26.0D, 0.0D);
		double turn = Math.toRadians(15.0D);
		Vec3 turned = side.scale(Math.cos(turn) * 8.0D).add(h.scale(Math.sin(turn) * 8.0D));
		Vec3 v1 = aim.add(turned).add(0.0D, 28.0D, 0.0D);
		Vec3 look0;
		Vec3 look1;
		Vec3 vistaAt;
		if (low) {
			// across the cloud sea (the East Sea, the Kua'e giants): level, a little down
			look0 = v0.add(h.scale(50.0D)).add(0.0D, -9.0D, 0.0D);
			look1 = v1.add(h.scale(50.0D)).add(0.0D, -6.0D, 0.0D);
			vistaAt = v0.add(h.scale(Math.cos(Math.toRadians(-8.0D)) * 110.0D)).add(0.0D, Math.sin(Math.toRadians(-8.0D)) * 110.0D, 0.0D);
		} else {
			Vec3 lookUp55 = h.scale(Math.cos(Math.toRadians(55.0D))).add(up.scale(Math.sin(Math.toRadians(55.0D))));
			Vec3 lookUp40 = h.scale(Math.cos(Math.toRadians(40.0D))).add(up.scale(Math.sin(Math.toRadians(40.0D))));
			look0 = v0.add(lookUp55.scale(50.0D));
			look1 = v1.add(lookUp40.scale(50.0D));
			vistaAt = v0.add(h.scale(Math.cos(Math.toRadians(47.0D)) * 110.0D)).add(0.0D, Math.sin(Math.toRadians(47.0D)) * 110.0D, 0.0D);
		}
		scene.at(Math.max(0, cut1 - 2), () -> {
			if (alive(film)) {
				Vistas.spawn(scene, film.d.vista(), film.spec, vistaAt, v0, f3 + 10, film.rgb, null, 1.0F, film.casterId);
			}
		});
		scene.at(cut1, () -> {
			if (!chained(film)) {
				return;
			}
			if (!act(film, v0, v1, look0, look1, f3, true, true)) {
				abandon(film);
				return;
			}
			card(film);
		});

		// the second wipe hides the cut down to the strike
		scene.at(Math.max(cut1 + 10, cut2 - 6), () -> {
			if (alive(film) && CinematicDirector.shooting(scene)) {
				CloudWipe.start(4, 4, 6, 0xE8ECFF);
			}
		});

		// F4 Turun: behind and above the falling weapon, riding it down; holds up to 20 ticks for a late climax
		double lift = 24.0D + Math.min(28.0D, distance * 0.25D);
		Vec3 r3From = aim.subtract(h.scale(8.0D)).add(0.0D, lift, 0.0D);
		Vec3 r3To = aim.subtract(h.scale(5.0D)).add(0.0D, 10.0D, 0.0D);
		scene.at(cut2, () -> {
			if (!chained(film)) {
				return;
			}
			if (film.graded) {
				SkyDirector.release(20, scene.priority);
				film.graded = false;
			}
			if (!act(film, r3From, r3To, aim.add(0.0D, 6.0D, 0.0D), aim, f4, true, false)) {
				abandon(film);
				return;
			}
			ScreenFlash.speedLines(film.rgb, Math.min(20, f4));
			CinematicDirector.snap(-8.0F, 4, f4);
			EpicScreen.vignette(film.rgb, 0.3F, f4);
		});
	}

	/**
	 * One act from {@code from} to {@code to} (the look from {@code look0} to {@code look1}) over {@code ticks}, split into
	 * segments of at most 80 ticks that continue each other (scheduled on the film's scene). {@code cut} marks the first
	 * segment as a masked cut; {@code chain} holds the last segment 4 ticks so the next act takes it over while it still
	 * runs, otherwise it holds 20 ticks for a late climax. False when the first segment was refused.
	 */
	private static boolean act(Film film, Vec3 from, Vec3 to, Vec3 look0, Vec3 look1, int ticks, boolean cut, boolean chain) {
		int parts = Math.max(1, (ticks + SEGMENT - 1) / SEGMENT);
		int each = Math.max(10, ticks / parts);
		for (int i = 0; i < parts; i++) {
			double a = i / (double) parts;
			double b = (i + 1) / (double) parts;
			Vec3 p0 = from.lerp(to, a);
			Vec3 p1 = from.lerp(to, b);
			Vec3 l0 = look0.lerp(look1, a);
			Vec3 l1 = look0.lerp(look1, b);
			boolean last = i == parts - 1;
			int hold = last ? (chain ? 4 : 20) : 4;
			boolean first = i == 0;
			if (first) {
				if (!segment(film, p0, p1, l0, l1, each, cut, hold)) {
					return false;
				}
			} else {
				film.scene.at(each * i, () -> {
					if (chained(film) && !segment(film, p0, p1, l0, l1, each, false, hold)) {
						abandon(film);
					}
				});
			}
		}
		return true;
	}

	private static boolean segment(Film film, Vec3 from, Vec3 to, Vec3 look0, Vec3 look1, int ticks, boolean cut, int hold) {
		Shots.Shot shot = Shots.pusakaFilm(from, to, look0, look1, ticks);
		if (cut) {
			shot.cut();
		}
		if (hold > 0) {
			shot.hold(hold);
		}
		EpicDirector.refreshFlags(film.scene);
		return CinematicDirector.shoot(film.scene, shot, CinematicDirector.KIND_ULTIMATE, film.scene.priority, true);
	}

	/** True when the next segment may start: the film lives and its previous segment still runs (no skip, no abort). */
	private static boolean chained(Film film) {
		if (!alive(film)) {
			return false;
		}
		if (!CinematicDirector.shooting(film.scene)) {
			abandon(film);
			return false;
		}
		return true;
	}

	/** The decree's jade title card at the start of F3 (7b TitleCard, gold rim, the decree glyph on a jade seal). */
	private static void card(Film film) {
		long now = Util.getMillis();
		if (now - CARD_AT[film.skill] < 15_000L) {
			return;
		}
		CARD_AT[film.skill] = now;
		String name = film.spec.displayName().getString().toUpperCase(Locale.ROOT);
		TitleCard.Card card = TitleCard.full(Component.literal(name), Component.translatable(K + "film.sub"), GLYPHS[film.skill], JADE, 70)
				.goldRim().safeBeat(true);
		TitleCard.show(card);
	}

	/** The qi motes streaming up the pillar during F2. */
	private static void motes(Vec3 aim, int rgb) {
		int tier = EpicBudget.tierOrMinimal();
		int n = EpicParticles.grant(tier == 0 ? 2 : tier == 1 ? 5 : 9, false);
		if (n <= 0) {
			return;
		}
		ParticleOptions mote = Qi.on() ? Qi.mote(rgb, 0.3F, 40) : EpicParticles.dust(rgb, 1.2F);
		EpicParticles.column(aim, 40.0F, 1.4F, n, mote, 0.45F);
	}

	/** The sky grade behind the first cut (presentation only, never the weather or the time of day). */
	private static SkyDirector.Grade grade(Dampak.Vista vista) {
		if (vista == Dampak.Vista.ISTANA_LANGIT || vista == Dampak.Vista.KUA_E) {
			return SkyDirector.Grade.SK_GOLD;
		}
		return SkyDirector.Grade.SK_STARS;
	}

	// ================================================================ the climax and the aftermath

	/** The climax: the cut under the impact frame, to F5 when still safe, else to the eye; or the Singkat reveal. */
	private static void onClimax(Film film) {
		film.climaxed = true;
		if (current == film) {
			current = null;
		}
		if (film.graded) {
			SkyDirector.release(20, film.scene.priority);
			film.graded = false;
		}
		if (film.full && film.running) {
			if (CinematicDirector.ownedBy(film.scene)) {
				if (!film.cancelled && unsafe(film) == null && aftermath(film)) {
					CloudWipe.release(3);
					return;
				}
				CinematicDirector.cutToEye();
			}
			CloudWipe.release(3);
		} else if (film.revealOnClimax && !film.cancelled) {
			crane(film, Shots.craneLook(2.5F, 1.8F, 7.0F, 12.0F, 2.0F).lookAt(film.aim.add(0.0D, 2.0D, 0.0D)));
		}
	}

	/**
	 * F5 Akibat: a low crane circling the planted weapon or the pit by 20° over 40 ticks (from the caster's side, so the
	 * shock ring's second wave, the debris and the molten glow read), cut in under the impact frame; it ends with the
	 * normal 10-tick blend to the eye.
	 */
	private static boolean aftermath(Film film) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}
		Vec3 aim = film.aim;
		Vec3 back = new Vec3(minecraft.player.getX() - aim.x, 0.0D, minecraft.player.getZ() - aim.z);
		back = back.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, -1.0D) : back.normalize();
		double r = 12.0D + Math.min(12.0D, Math.max(0.0F, film.radius) / 8.0D);
		double a0 = Math.atan2(back.z, back.x) - Math.toRadians(10.0D);
		double a1 = a0 + Math.toRadians(20.0D);
		Vec3 from = aim.add(Math.cos(a0) * r, 3.0D, Math.sin(a0) * r);
		Vec3 to = aim.add(Math.cos(a1) * r, 6.0D, Math.sin(a1) * r);
		Shots.Shot shot = Shots.pusakaFilm(from, to, aim.add(0.0D, 4.0D, 0.0D), aim.add(0.0D, 5.0D, 0.0D), AFTERMATH).cut();
		EpicDirector.refreshFlags(film.scene);
		return CinematicDirector.shoot(film.scene, shot, CinematicDirector.KIND_ULTIMATE, film.scene.priority, true);
	}

	// ================================================================ gates and helpers

	/**
	 * Null when the film may run now (29.1.3, 28.3.4): Kamera Penuh, the scene's local SAFE and RESULT_SAFE flags, no enemy
	 * within 24 b, health ≥ 50 %; else a short reason.
	 */
	private static @Nullable String unsafe(Film film) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return "player";
		}
		if (CinematicDirector.mode() != EpicSettings.Camera.PENUH) {
			return "camera";
		}
		EpicDirector.refreshFlags(film.scene);
		if (!film.scene.has(EpicIds.FLAG_SAFE) || !film.scene.has(EpicIds.FLAG_RESULT_SAFE)) {
			return "unsafe";
		}
		float health = (player.getHealth() + player.getAbsorptionAmount()) / Math.max(1.0F, player.getMaxHealth());
		if (health < 0.5F) {
			return "health";
		}
		boolean enemy = !player.level().getEntities(player, new AABB(player.blockPosition()).inflate(24.0D),
				entity -> entity instanceof Enemy && entity.isAlive()).isEmpty();
		return enemy ? "enemy" : null;
	}

	private static boolean alive(Film film) {
		return current == film && !film.cancelled && !film.climaxed;
	}

	private static void abandon(Film film) {
		film.cancelled = true;
		if (film.running && CinematicDirector.ownedBy(film.scene)) {
			CinematicDirector.skip();
		}
		if (film.running) {
			CloudWipe.release(4);
		}
		if (film.graded) {
			SkyDirector.release(20, film.scene.priority);
			film.graded = false;
		}
		if (current == film) {
			current = null;
		}
	}

	/** The decree (0 Pedang, 1 Tiang, 2 Bintang, 3 Gunung): from the spec id, else bits 12-13 of {@code aux} (29.3.1). */
	public static int skillOf(TechniqueSpec spec, @Nullable Payloads.TechFx p) {
		String id = spec.id();
		for (int i = 0; i < EpicSettings.TITAH_SKILLS.length; i++) {
			if (id.equals("titah_" + EpicSettings.TITAH_SKILLS[i])) {
				return i;
			}
		}
		return p == null ? 0 : (p.aux() >> 12) & 3;
	}

	/** The film's colour: the decree's first real frame colour (white-gold, ember, violet, gold), else gold. */
	private static int colour(TechniqueSpec spec, Dampak d) {
		for (int frame : new int[]{d.frame2(), d.frame1(), d.frame3()}) {
			if (frame >= 0 && Qi.luma(frame) > 0.18F) {
				return frame & 0xFFFFFF;
			}
		}
		return spec.dao() != null ? spec.dao().color() & 0xFFFFFF : 0xFFD54A;
	}
}
