package dev.kultivator.client.epic.film;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.cosmic.CosmicSky;
import dev.kultivator.client.epic.lens.HeatHaze;
import dev.kultivator.client.epic.lens.LensLayers;
import dev.kultivator.client.epic.screen.CloudWipe;
import dev.kultivator.client.epic.screen.EpicScreen;
import dev.kultivator.client.epic.screen.ImpactFrame;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.EpicSound;
import dev.kultivator.client.epic.sound.FilmSound;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Film Spektakuler (Phase 30, DESIGN 30.2): the public film API (contract C30.2) and the engine that arms, gates, plays
 * and re-times the films of {@link FilmCatalog}. A film belongs to the local player's own cast only; it never delays or
 * changes anything on the server: when the server's climax comes the film cuts to it.
 * <ul>
 * <li><b>Arming</b> ({@link #play}): Kamera Spektakuler, the server's CAMERA_OK, no server load flag, the ultimate camera
 * gap (1200 ticks, never for Titah or tests). An armed film {@link #claims} its spec, so Perjalanan Langit, Film Pusaka
 * and the 7b shots of that moment yield to it; a refused film leaves them exactly as today.</li>
 * <li><b>Acts</b>: each act re-checks every gate at its start (the scene's SAFE flag, no enemy or targeting mob within
 * 24 b, health ≥ 50 %, every 23.7.4 hard gate through {@link CinematicDirector#shootFilm}); a failed gate skips that act
 * and the player's own camera stays. "Perjalanan langit" decides the climb acts (Pertama: the first cast of each spec per
 * session; Selalu; Mati).</li>
 * <li><b>Timing</b> (30.2.6): act starts come from the arming tick and the planned climax ({@code climaxIn}); the film
 * clock advances one tick per tick (a hit-stop holds it; bullet time never slows the camera). An early climax ends the
 * running pre-climax act at once and starts the climax acts on the event; a late one is covered by the last shot's
 * 20-tick hold; a climax that never comes (60 ticks late) drops the climax acts. Titah films shorten their cosmic shots
 * first when Kecepatan makes the build-up shorter.</li>
 * <li><b>Skips</b>: Backspace skips the act (the camera eases back), a double press the whole film; any movement key, a
 * big mouse motion or a foreign hurt ends the shot as in 23.7.4, and the next act re-checks the gates.</li>
 * </ul>
 * Client thread only. Idle cost: one null check per tick and per TechFx event.
 */
public final class FilmDirector {

	public record Ctx(int casterId, String specId, Vec3 origin, Vec3 aim, float yaw, long seed, int climaxIn, int duration) {
	}

	/** A prop the drivers report for {@code P(key)} spaces (30.5 C30.2). */
	public interface Track {
		Vec3 pos(float partial);

		float yaw(float partial);

		float pitch(float partial);

		boolean alive();
	}

	/** The KIND_ULTIMATE camera gap of 23.6 for films (Titah and tests skip it). */
	public static final int ULTIMATE_GAP = 1200;
	/** How long the climax acts wait past the planned climax before they are dropped. */
	public static final int CLIMAX_WAIT = 60;
	/** Hard cuts at least this far apart (30.2.3). */
	public static final int CUT_GAP = 10;

	private static final Map<Integer, Map<String, Track>> TRACKS = new HashMap<>();
	private static final Set<String> CLIMBED = new HashSet<>();
	private static @Nullable Run current;
	private static long now;
	private static long lastUltimateFilm = -100_000L;
	private static long lastCut = -100L;
	private static String lastRefusal = "";
	private static final List<String> LOG = new ArrayList<>();
	private static boolean lowLoad;
	/** The 7b shots of the moment keep yielding this long after a film ended (no second camera move on the same cast). */
	private static long holdUntil;

	private FilmDirector() {
	}

	// ================================================================ the run

	private static final int PENDING = 0;
	private static final int RUNNING = 1;
	private static final int DONE = 2;

	private static final class ActRun {
		final FilmAct act;
		final int[] ticks;
		long start = -1L;
		int state = PENDING;
		int shot = -1;
		long shotStart;
		@Nullable FilmPath path;
		int sound;
		boolean holding;

		ActRun(FilmAct act) {
			this.act = act;
			this.ticks = new int[act.shots.size()];
			for (int i = 0; i < ticks.length; i++) {
				ticks[i] = act.shots.get(i).ticks;
			}
		}

		int length() {
			int total = 0;
			for (int t : ticks) {
				total += t;
			}
			return total;
		}

		boolean preClimax() {
			return act.anchor == FilmAct.Anchor.CLIMAX && act.offset < 0 || act.shots.stream().anyMatch(s -> s.climaxHold);
		}
	}

	private static final class Run {
		final Film film;
		final Ctx ctx;
		final Scene scene;
		final FilmStage stage;
		final @Nullable TechniqueSpec spec;
		final long armedAt;
		final boolean test;
		final int tint;
		long climaxAt;
		boolean climaxSeen;
		boolean carded;
		int eventActs;
		int camera;
		boolean ended;
		final int[] events = new int[Phase.values().length];
		final List<ActRun> acts = new ArrayList<>();

		Run(Film film, Ctx ctx, Scene scene, @Nullable TechniqueSpec spec, boolean test) {
			this.film = film;
			this.ctx = ctx;
			this.scene = scene;
			this.spec = spec;
			this.test = test;
			this.armedAt = now;
			this.stage = new FilmStage(scene, ctx.casterId(), ctx.aim(), ctx.origin(), ctx.yaw(),
					TRACKS.computeIfAbsent(ctx.casterId(), id -> new HashMap<>()));
			this.stage.fallbacks = film.props;
			Dao dao = spec == null ? null : spec.dao();
			this.tint = dao != null ? dao.color() & 0xFFFFFF : film.tint;
			this.climaxAt = ctx.climaxIn() >= 0 ? now + ctx.climaxIn() : -1L;
			for (FilmAct act : film.acts) {
				acts.add(new ActRun(act));
			}
		}
	}

	// ================================================================ API (C30.2)

	/**
	 * Arms the film {@code filmId} for the local player's own cast. False (and nothing changes) when the setting, a gate
	 * or a missing film refuses it; then the Penuh path of today runs. A second call for the same cast is a no-op (true).
	 */
	public static boolean play(String filmId, Ctx ctx) {
		try {
			return arm(filmId, ctx, false);
		} catch (RuntimeException exception) {
			warn("play", exception);
			return false;
		}
	}

	/** A driver beat ("release", "impact", "dive", "step", …): starts BEAT acts and nudges the flare's light point. */
	public static void cue(int casterId, String beat, Vec3 at) {
		Run run = current;
		if (run == null || run.ctx.casterId() != casterId || beat == null) {
			return;
		}
		for (ActRun ar : run.acts) {
			if (ar.state == PENDING && ar.start < 0L && ar.act.anchor == FilmAct.Anchor.BEAT && beat.equals(ar.act.beat)) {
				ar.start = now + Math.max(0, ar.act.offset);
			}
		}
		if ("impact".equals(beat) && !run.climaxSeen && run.film.playerTimed) {
			onClimax(run);
		}
	}

	/** Registers (or with null removes) a tracked prop for {@code P(key)} spaces. */
	public static void track(int casterId, String key, @Nullable Track track) {
		if (key == null) {
			return;
		}
		if (track == null) {
			Map<String, Track> map = TRACKS.get(casterId);
			if (map != null) {
				map.remove(key);
				if (map.isEmpty() && (current == null || current.ctx.casterId() != casterId)) {
					TRACKS.remove(casterId);
				}
			}
			return;
		}
		TRACKS.computeIfAbsent(casterId, id -> new HashMap<>()).put(key, track);
	}

	/** Ends the film of that caster (ABORT, a driver's abort) and forgets its props. */
	public static void abort(int casterId) {
		Run run = current;
		if (run != null && run.ctx.casterId() == casterId) {
			end(run, true);
		}
		TRACKS.remove(casterId);
	}

	/** True while a film of that spec is armed or playing. */
	public static boolean claims(String specId) {
		Run run = current;
		return run != null && !run.ended && run.ctx.specId().equals(specId);
	}

	/** True while a film shot owns the camera. */
	public static boolean playing() {
		Run run = current;
		return run != null && !run.ended && CinematicDirector.filmShooting();
	}

	/** True while a film is armed: the other cinematic shots of the moment yield to it. */
	public static boolean holdsCamera() {
		Run run = current;
		return run != null && !run.ended || now < holdUntil;
	}

	// ================================================================ hooks

	/** Every technique event (from {@code EpicDirector.onTechFx}, before the Phase 10 cinema). Never throws. */
	public static void onTechFx(Payloads.TechFx p) {
		try {
			techFx(p);
		} catch (RuntimeException | LinkageError exception) {
			warn("techFx", exception);
		}
	}

	private static void techFx(Payloads.TechFx p) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || p.caster() != player.getId() || p.has(Payloads.TechFx.FLAG_DISCIPLE) || p.has(Payloads.TechFx.FLAG_ECHO)) {
			return;
		}
		TechniqueSpec spec = TechniqueRegistry.byNet(p.spec());
		if (spec == null) {
			return;
		}
		Phase phase = Phase.byOrdinal(p.phase());
		lowLoad = p.has(Payloads.TechFx.FLAG_LOW_LOAD);
		Run run = current;
		if (run != null && run.ctx.specId().equals(spec.id())) {
			if (phase == Phase.ABORT) {
				end(run, true);
				return;
			}
			if (phase == Phase.END) {
				return;
			}
			boolean climax = p.has(Payloads.TechFx.FLAG_CLIMAX) && phase != Phase.PULSE && phase != Phase.MASS_HIT;
			if (climax && !run.climaxSeen) {
				run.stage.aim = p.tx() != 0.0D || p.ty() != 0.0D || p.tz() != 0.0D ? new Vec3(p.tx(), p.ty(), p.tz()) : run.stage.aim;
				onClimax(run);
			}
			int count = ++run.events[phase.ordinal()];
			for (ActRun ar : run.acts) {
				if (ar.state == PENDING && ar.start < 0L && ar.act.anchor == FilmAct.Anchor.EVENT && ar.act.phase == phase && ar.act.index == count) {
					if (run.eventActs < run.film.maxEventActs) {
						run.eventActs++;
						ar.start = now + Math.max(0, ar.act.offset);
					} else {
						ar.state = DONE;
					}
				}
			}
			return;
		}
		Film film = FilmCatalog.get(spec.id());
		if (film == null) {
			return;
		}
		Phase arm = film.charged ? Phase.CHARGE : Phase.CAST;
		if (phase != arm) {
			return;
		}
		boolean hasTo = p.tx() != 0.0D || p.ty() != 0.0D || p.tz() != 0.0D;
		Vec3 aim = hasTo ? new Vec3(p.tx(), p.ty(), p.tz()) : new Vec3(p.x(), p.y(), p.z());
		Dampak d = spec.dampak();
		int climaxIn;
		if (film.titah) {
			climaxIn = p.duration() > 0 ? p.duration() : film.k;
		} else if (film.playerTimed) {
			climaxIn = -1;
		} else if (d != null && d.climaxAt() > 0) {
			climaxIn = d.climaxAt();
		} else {
			climaxIn = film.k;
		}
		play(spec.id(), new Ctx(p.caster(), spec.id(), player.position(), aim, player.yBodyRot, p.seed(), climaxIn, p.duration()));
	}

	/** {@code KEH_CRATER} of an own carving job: the Spektakuler crater reveal (30.3.9). True = it plays (no 7b crane). */
	public static boolean crater(Vec3 zone, int blocks) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || blocks < 512 || !EpicSettings.spektakuler()) {
				return false;
			}
			double distance = Math.sqrt(minecraft.player.position().distanceToSqr(zone.x, minecraft.player.getY(), zone.z));
			if (distance > 16.0D) {
				return false;
			}
			Run run = current;
			Film kawah = FilmCatalog.get(FilmCatalog.KAWAH);
			if (kawah == null) {
				return false;
			}
			if (run != null && !run.ended) {
				// the aftermath of a running film adds the reveal after its last act
				long end = now;
				for (ActRun ar : run.acts) {
					if (ar.start >= 0L) {
						end = Math.max(end, ar.start + ar.length());
					}
				}
				for (FilmAct act : kawah.acts) {
					ActRun ar = new ActRun(act);
					ar.start = end + 2;
					run.acts.add(ar);
				}
				run.stage.aim = zone;
				return true;
			}
			Ctx ctx = new Ctx(minecraft.player.getId(), FilmCatalog.KAWAH, minecraft.player.position(), zone, minecraft.player.yBodyRot,
					(long) zone.hashCode() * 31L + blocks, 0, 120);
			return arm(FilmCatalog.KAWAH, ctx, false);
		} catch (RuntimeException exception) {
			warn("crater", exception);
			return false;
		}
	}

	/**
	 * {@code /sinematik film <id> [act n]}: the film around the player with its aim 16 b ahead and a fake timeline (the
	 * climax at the film's planned time), still under every gate. Null = started, else the refusal reason.
	 */
	public static @Nullable String test(String filmId, int onlyAct) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return "player";
		}
		Film film = FilmCatalog.get(filmId);
		if (film == null) {
			return "film";
		}
		if (!EpicSettings.spektakuler()) {
			return "camera";
		}
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 aim = player.position().add(flat.scale(16.0D));
		int k = film.k > 0 ? film.k : 60;
		Ctx ctx = new Ctx(player.getId(), filmId, player.position(), aim, player.yBodyRot, filmId.hashCode() * 7919L, k, k + 120);
		if (current != null) {
			end(current, true);
		}
		if (!arm(filmId, ctx, true)) {
			return lastRefusal.isEmpty() ? "gate" : lastRefusal;
		}
		Run run = current;
		if (run != null && onlyAct >= 0) {
			for (int i = 0; i < run.acts.size(); i++) {
				if (i != onlyAct) {
					run.acts.get(i).state = DONE;
				} else {
					ActRun ar = run.acts.get(i);
					if (ar.start > now + 2) {
						ar.start = now + 2;
					}
				}
			}
		}
		return null;
	}

	/** Ends everything (skip-all, dimension change, disconnect). */
	public static void clear() {
		if (current != null) {
			end(current, true);
		}
		current = null;
		holdUntil = 0L;
		TRACKS.clear();
		LensLayers.clear();
		CosmicSky.clear();
		HeatHaze.clear();
		FilmSound.stopAll();
	}

	/** {@code /sinematik film info}: settings, the gate that would refuse a film now, the last film's shots. */
	public static List<String> info() {
		List<String> lines = new ArrayList<>();
		lines.add("camera=" + EpicSettings.cameraValue() + " spektakuler=" + EpicSettings.spektakuler() + " lens="
				+ EpicSettings.lens().name().toLowerCase(Locale.ROOT) + " cosmic=" + EpicSettings.cosmic() + " bullet=" + EpicSettings.bullet()
				+ " skyride=" + EpicSettings.skyRide().id());
		Run run = current;
		lines.add("running=" + (run == null ? "-" : run.film.id + " t=" + (now - run.armedAt) + " climax=" + (run.climaxAt < 0 ? "?" : run.climaxAt - run.armedAt)
				+ (run.climaxSeen ? " (seen)" : "")));
		lines.add("gate now=" + (run != null ? String.valueOf(gate(run)) : "(no film)") + " last refusal=" + (lastRefusal.isEmpty() ? "-" : lastRefusal));
		int from = Math.max(0, LOG.size() - 12);
		lines.addAll(LOG.subList(from, LOG.size()));
		return lines;
	}

	// ================================================================ per tick (after the 7b director)

	/** Once per client tick, after {@code EpicDirector.tick}. */
	public static void tick(Minecraft minecraft) {
		try {
			FilmCommands.tick();
			FilmSound.tick();
			LensLayers.tick();
			CosmicSky.tick();
			HeatHaze.tick();
		} catch (RuntimeException exception) {
			warn("layers", exception);
		}
		if (minecraft.isPaused()) {
			return;
		}
		if (!dev.kultivator.client.epic.EpicClock.hitStopped()) {
			now++;
		}
		Run run = current;
		if (run == null) {
			return;
		}
		try {
			step(run, minecraft);
		} catch (RuntimeException exception) {
			warn("tick", exception);
			end(run, true);
		}
	}

	private static void step(Run run, Minecraft minecraft) {
		if (minecraft.player == null || minecraft.level == null || run.scene.subject() == null && !run.test) {
			end(run, true);
			return;
		}
		int presses = CinematicDirector.lastPresses();
		if (presses >= 2) {
			// a double press skips the whole film
			end(run, true);
			return;
		}
		// the fake climax of a test film; the climax acts of a late (or missing) climax
		if (!run.climaxSeen && run.climaxAt >= 0L) {
			if (run.test && now >= run.climaxAt) {
				fakeClimax(run);
			} else if (now > run.climaxAt + CLIMAX_WAIT) {
				for (ActRun ar : run.acts) {
					if (ar.state == PENDING && ar.act.anchor == FilmAct.Anchor.CLIMAX && ar.act.offset >= 0) {
						ar.state = DONE;
					}
				}
			}
		}
		ActRun running = null;
		for (ActRun ar : run.acts) {
			if (ar.state == RUNNING) {
				running = ar;
				break;
			}
		}
		if (running != null) {
			advance(run, running, presses);
		}
		if (running == null || running.state != RUNNING) {
			for (ActRun ar : run.acts) {
				if (ar.state == PENDING && ar.start >= 0L && now >= ar.start) {
					startAct(run, ar);
					if (ar.state == RUNNING) {
						break;
					}
				}
			}
		}
		// the film ends when nothing is running or waiting any more
		boolean waiting = false;
		for (ActRun ar : run.acts) {
			if (ar.state == RUNNING || ar.state == PENDING && (ar.start >= 0L || waitsForEvent(run, ar))) {
				waiting = true;
				break;
			}
		}
		if (!waiting || now - run.armedAt > 20 * 60) {
			end(run, false);
		}
	}

	private static boolean waitsForEvent(Run run, ActRun ar) {
		return switch (ar.act.anchor) {
			case CLIMAX -> !run.climaxSeen && (run.climaxAt < 0L ? now - run.armedAt < Math.max(200, run.ctx.duration() + 100) : now <= run.climaxAt + CLIMAX_WAIT);
			case EVENT, BEAT -> now - run.armedAt < Math.max(200, run.ctx.duration() + 60);
			default -> false;
		};
	}

	private static void advance(Run run, ActRun ar, int presses) {
		long age = now - ar.shotStart;
		int length = ar.ticks[ar.shot];
		FilmShot shot = ar.act.shots.get(ar.shot);
		// sounds at their moments
		playSounds(run, ar, shot, age, length);
		// the flare follows its light point
		if (ar.path != null && FilmLens.has(shot.lens, FilmLens.FLARE)) {
			LensLayers.light(lightPoint(run, shot));
		}
		// a cloud-wipe cut: the wipe closes over the last 5 ticks of this shot (one luminance event; else a black dip)
		if (age == length - 5 && ar.shot + 1 < ar.act.shots.size() && ar.act.shots.get(ar.shot + 1).mask == FilmShot.Mask.CLOUD_WIPE) {
			CloudWipe.start(4, 3, 6, 0xF4F6FF);
		}
		if (age >= length) {
			if (shot.climaxHold && !run.climaxSeen && age < length + 20 && CinematicDirector.filmShooting()) {
				ar.holding = true;
				return;
			}
			if (ar.shot + 1 < ar.act.shots.size()) {
				startShot(run, ar, ar.shot + 1);
			} else {
				finishAct(run, ar, false);
			}
			return;
		}
		if (presses == 1 || !CinematicDirector.filmShooting()) {
			// Backspace, a key, a hurt or a gate ended the shot: the rest of this act is skipped
			log(run.film.id + " act " + run.acts.indexOf(ar) + " interrupted");
			finishAct(run, ar, true);
		}
	}

	private static void playSounds(Run run, ActRun ar, FilmShot shot, long age, int length) {
		while (ar.sound < shot.sounds.size()) {
			FilmShot.SoundAt cue = shot.sounds.get(ar.sound);
			long at = cue.tick() >= 0 ? cue.tick() : Math.round(cue.at() * length);
			if (age < at) {
				return;
			}
			ar.sound++;
			if (cue.cue() == FilmSound.Cue.RISER_LONG || cue.cue() == FilmSound.Cue.RISER_SHORT) {
				FilmSound.riser(cue.cue(), Math.max(10, length - (int) at), 0.8F * cue.volume(), 0.5F * cue.pitch(), 1.6F * cue.pitch());
			} else if (cue.cue() == FilmSound.Cue.HUM_ENERGY || cue.cue() == FilmSound.Cue.HUM_VORTEX) {
				FilmSound.hum(cue.cue(), Math.max(20, length - (int) at + 10), 0.7F * cue.volume(), cue.pitch());
			} else if (cue.positioned()) {
				FilmSound.at(cue.cue(), run.stage.aim, 1.5F * cue.volume(), cue.pitch());
			} else {
				FilmSound.own(cue.cue(), 0.9F * cue.volume(), cue.pitch());
			}
		}
	}

	// ================================================================ arming and acts

	private static boolean arm(String filmId, Ctx ctx, boolean test) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Film film = FilmCatalog.get(filmId);
		if (player == null || film == null || ctx.casterId() != player.getId()) {
			return refuse(film == null ? "film" : "not own");
		}
		Run old = current;
		if (old != null && !old.ended && old.ctx.casterId() == ctx.casterId() && old.ctx.specId().equals(ctx.specId()) && now - old.armedAt <= 1) {
			return true;
		}
		if (!EpicSettings.spektakuler()) {
			return refuse("setting");
		}
		if (!ClientCultivation.serverEpicCamera()) {
			return refuse("server camera");
		}
		if (lowLoad && !test) {
			return refuse("server load");
		}
		if (film.titah && !test) {
			// the decree's own Film choice (29.2.2): only Penuh gives the film; Singkat and Mati leave Film Pusaka its say
			for (int i = 0; i < EpicSettings.TITAH_SKILLS.length; i++) {
				if (film.id.equals("titah_" + EpicSettings.TITAH_SKILLS[i]) && EpicSettings.titahFilm(i) != EpicSettings.TitahFilm.PENUH) {
					return refuse("decree film setting");
				}
			}
		}
		boolean gapFree = test || film.titah || FilmCatalog.KAWAH.equals(film.id);
		if (!gapFree && now - lastUltimateFilm < ULTIMATE_GAP) {
			return refuse("ultimate gap");
		}
		TechniqueSpec spec = TechniqueRegistry.byId(ctx.specId());
		Scene scene = test ? EpicDirector.testScene()
				: EpicDirector.dampakScene(ctx.casterId(), true, ctx.origin(), (int) ctx.seed(),
						Math.max(Math.max(200, ctx.duration() + 200), Math.max(0, ctx.climaxIn()) + 300));
		if (scene == null || !scene.own) {
			return refuse("scene");
		}
		if (old != null) {
			end(old, true);
		}
		Run run = new Run(film, ctx, scene, spec, test);
		plan(run);
		current = run;
		if (!gapFree) {
			lastUltimateFilm = now;
		}
		lastCut = -100L;
		log("armed " + film.id + " climaxIn=" + ctx.climaxIn() + (test ? " (test)" : ""));
		return true;
	}

	private static boolean refuse(String reason) {
		lastRefusal = reason;
		return false;
	}

	/** Plans the start of every act from the arming tick and the planned climax; trims overlaps and the camera cap. */
	private static void plan(Run run) {
		for (ActRun ar : run.acts) {
			switch (ar.act.anchor) {
				case CAST -> ar.start = run.armedAt + Math.max(0, ar.act.offset);
				case CLIMAX -> {
					if (ar.act.offset < 0) {
						ar.start = run.climaxAt >= 0L ? run.climaxAt + ar.act.offset : -1L;
						if (ar.start < 0L) {
							ar.state = DONE;
						}
					}
				}
				default -> {
				}
			}
		}
		if (run.film.titah) {
			retimeTitah(run);
		}
		// an act that would start before the previous one ends loses the previous act's tail
		ActRun previous = null;
		for (ActRun ar : run.acts) {
			if (ar.state != PENDING || ar.start < 0L) {
				continue;
			}
			if (previous != null && previous.start + previous.length() > ar.start) {
				trim(previous, (int) Math.max(0L, ar.start - previous.start));
			}
			previous = ar;
		}
		// pre-climax acts that would start in the past (a short climaxIn) start now, trimmed to end on the climax
		for (ActRun ar : run.acts) {
			if (ar.state == PENDING && ar.start >= 0L && ar.start < run.armedAt) {
				int lost = (int) (run.armedAt - ar.start);
				ar.start = run.armedAt;
				trim(ar, Math.max(0, ar.length() - lost));
			}
		}
		// the whole film's camera time
		int total = 0;
		for (ActRun ar : run.acts) {
			if (ar.state == DONE) {
				continue;
			}
			int len = ar.length();
			if (total + len > Film.MAX_CAMERA) {
				trim(ar, Film.MAX_CAMERA - total);
			}
			total += ar.length();
		}
	}

	/** Shortens an act to {@code budget} ticks: shots past it go, the last kept one shrinks (≥ 10 ticks). */
	private static void trim(ActRun ar, int budget) {
		int used = 0;
		for (int i = 0; i < ar.ticks.length; i++) {
			if (ar.ticks[i] == 0) {
				continue;
			}
			if (used + ar.ticks[i] <= budget) {
				used += ar.ticks[i];
				continue;
			}
			int left = budget - used;
			ar.ticks[i] = left >= 10 ? left : 0;
			used += ar.ticks[i];
		}
		if (used == 0) {
			ar.state = DONE;
		}
	}

	/**
	 * Titah (30.2.6, 29.4): the pre-climax act ends on the server's climax. With a longer build-up it starts later; with a
	 * shorter one the cosmic shots shorten first (down to 10 ticks), then the others proportionally.
	 */
	private static void retimeTitah(Run run) {
		if (run.climaxAt < 0L) {
			return;
		}
		int k = (int) (run.climaxAt - run.armedAt);
		for (ActRun ar : run.acts) {
			if (ar.act.anchor != FilmAct.Anchor.CAST || !ar.preClimax()) {
				continue;
			}
			int planned = ar.act.offset + ar.length();
			if (k >= planned) {
				ar.start = run.armedAt + (k - ar.length());
				continue;
			}
			int over = planned - k;
			for (int i = 0; i < ar.ticks.length && over > 0; i++) {
				if (ar.act.shots.get(i).cosmic) {
					int cut = Math.min(over, ar.ticks[i] - 10);
					if (cut > 0) {
						ar.ticks[i] -= cut;
						over -= cut;
					}
				}
			}
			if (over > 0) {
				int len = ar.length();
				float keep = Math.max(0.0F, (len - over) / (float) Math.max(1, len));
				for (int i = 0; i < ar.ticks.length; i++) {
					ar.ticks[i] = Math.max(10, Math.round(ar.ticks[i] * keep));
				}
			}
			ar.start = run.armedAt + Math.max(0, k - ar.length());
		}
	}

	private static void onClimax(Run run) {
		run.climaxSeen = true;
		run.climaxAt = now;
		// the climax acts start on the event (they join the running shot under the impact mask)
		for (ActRun ar : run.acts) {
			if (ar.state == PENDING && ar.act.anchor == FilmAct.Anchor.CLIMAX) {
				if (ar.act.offset >= 0) {
					ar.start = now + ar.act.offset;
				} else {
					ar.state = DONE;
				}
			} else if (ar.state == PENDING && ar.act.anchor == FilmAct.Anchor.CAST) {
				// build-up acts that did not start yet are over: the impact cuts on the event
				ar.state = DONE;
			}
		}
		for (ActRun ar : run.acts) {
			if (ar.state == RUNNING) {
				// an early (or exact) climax: the running act ends now and the impact cut follows at once
				finishAct(run, ar, false);
			}
		}
	}

	private static void fakeClimax(Run run) {
		TechniqueSpec spec = run.spec;
		Dampak d = spec == null ? null : spec.dampak();
		if (spec != null && d != null) {
			try {
				dev.kultivator.client.dampak.DampakClient.testClimax(spec, d, run.stage.aim, true);
			} catch (RuntimeException | LinkageError exception) {
				warn("test climax", exception);
			}
		}
		onClimax(run);
	}

	/** The per-act gates of 30.2.2 (null = the act may play). */
	private static @Nullable String gate(Run run) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return "player";
		}
		if (!EpicSettings.spektakuler()) {
			return "setting";
		}
		if (!ClientCultivation.serverEpicCamera()) {
			return "server camera";
		}
		EpicDirector.refreshFlags(run.scene);
		if (!run.scene.has(EpicIds.FLAG_CAMERA_OK)) {
			return "camera flag";
		}
		if (!run.scene.has(EpicIds.FLAG_SAFE)) {
			return "unsafe";
		}
		float health = (player.getHealth() + player.getAbsorptionAmount()) / Math.max(1.0F, player.getMaxHealth());
		if (health < 0.5F) {
			return "health";
		}
		for (Entity entity : player.level().getEntities(player, new AABB(player.blockPosition()).inflate(24.0D),
				e -> e instanceof Enemy || e instanceof Mob)) {
			if (!entity.isAlive() || entity.distanceTo(player) > 24.0F) {
				continue;
			}
			if (entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() == player) {
				return "hostile";
			}
		}
		return null;
	}

	private static void startAct(Run run, ActRun ar) {
		String reason = gate(run);
		if (reason == null && ar.act.climb) {
			EpicSettings.SkyRide setting = EpicSettings.skyRide();
			String spec = run.ctx.specId();
			// "Pertama" acts as "Selalu" for Titah (29.4)
			if (setting == EpicSettings.SkyRide.MATI || setting == EpicSettings.SkyRide.PERTAMA && CLIMBED.contains(spec) && !run.test && !run.film.titah) {
				reason = "perjalanan langit";
			}
		}
		if (reason != null) {
			ar.state = DONE;
			lastRefusal = reason;
			log(run.film.id + " act " + run.acts.indexOf(ar) + " skipped: " + reason);
			return;
		}
		if (ar.act.climb) {
			CLIMBED.add(run.ctx.specId());
		}
		int first = 0;
		while (first < ar.ticks.length && ar.ticks[first] == 0) {
			first++;
		}
		if (first >= ar.ticks.length) {
			ar.state = DONE;
			return;
		}
		boolean cutFromFilm = CinematicDirector.filmShooting();
		run.stage.actStart();
		ar.state = RUNNING;
		startShot(run, ar, first);
		if (ar.state == RUNNING && cutFromFilm) {
			log(run.film.id + " act " + run.acts.indexOf(ar) + " joined");
		}
	}

	private static void startShot(Run run, ActRun ar, int index) {
		while (index < ar.ticks.length && ar.ticks[index] == 0) {
			index++;
		}
		if (index >= ar.ticks.length) {
			finishAct(run, ar, false);
			return;
		}
		FilmShot shot = ar.act.shots.get(index);
		int length = ar.ticks[index];
		long seed = run.ctx.seed() * 31L + run.acts.indexOf(ar) * 977L + index;
		FilmPath path = new FilmPath(shot, run.stage, seed, length);
		path.validate();
		boolean masked = mask(shot.mask, run);
		Shots.Shot camera = Shots.film(path);
		if (shot.climaxHold) {
			camera.hold(20);
		}
		if (!CinematicDirector.shootFilm(run.scene, camera, masked)) {
			lastRefusal = "guard";
			log(run.film.id + " shot refused by the guard");
			finishAct(run, ar, true);
			return;
		}
		ar.shot = index;
		ar.shotStart = now;
		ar.path = path;
		ar.sound = 0;
		ar.holding = false;
		run.camera += length;
		shotEffects(run, shot, length, seed);
		log(String.format(Locale.ROOT, "%s act %d shot %d: %d t%s%s shrink %.2f", run.film.id, run.acts.indexOf(ar), index, length,
				masked ? " cut" : "", shot.cosmic ? " cosmic" : "", path.shrink()));
	}

	/** Raises the mask of a hard cut; true when the jump may skip the blend (≥ 10 ticks after the last cut). */
	private static boolean mask(FilmShot.Mask mask, Run run) {
		if (mask == FilmShot.Mask.NONE) {
			return false;
		}
		if (now - lastCut < CUT_GAP) {
			return false;
		}
		boolean covered = switch (mask) {
			case IMPACT -> ImpactFrame.showing();
			case CLOUD_WIPE -> CloudWipe.cover() >= 0.8F;
			case SMEAR -> LensLayers.smear() >= 0.8F;
			default -> false;
		};
		if (!covered) {
			// the fallback mask of every hard cut: a 2-tick black dip (not a luminance event)
			LensLayers.dip();
		}
		lastCut = now;
		return true;
	}

	/** The layers, sky, sound and time effects of one shot's start. */
	private static void shotEffects(Run run, FilmShot shot, int length, long seed) {
		int tint = shot.tint != 0xFFE8B0 ? shot.tint : run.tint;
		LensLayers.shot(shot.lens, tint, seed, lightPoint(run, shot));
		FilmParticleProviders.newShot();
		if (FilmLens.has(shot.lens, FilmLens.VIGNETTE)) {
			EpicScreen.vignette(shot.vignetteRgb, shot.vignetteRgb == 0 ? 0.28F : 0.32F, length);
		}
		if (shot.grey > 0.0F) {
			EpicScreen.grey(shot.grey, Math.min(40, length));
		}
		if (FilmLens.has(shot.lens, FilmLens.SPEED_LINES)) {
			ScreenFlash.speedLines(tint, Math.min(20, length));
		}
		if (FilmLens.has(shot.lens, FilmLens.FRINGE)) {
			EpicScreen.chroma(6);
			LensLayers.fringe(4);
		}
		if (FilmLens.has(shot.lens, FilmLens.HEAT_HAZE)) {
			float radius = 6.0F;
			if (run.spec != null && run.spec.dampak() != null) {
				radius = Math.max(4.0F, Math.min(12.0F, run.ctx.duration() > 0 ? 8.0F : 6.0F));
			}
			HeatHaze.show(run.stage.aim, radius, length + 10, tint, seed);
		}
		if (shot.card && !run.carded && run.spec != null) {
			run.carded = true;
			Component title = run.spec.displayName();
			TitleCard.show(TitleCard.full(title, null, EpicDirector.seal(run.spec), tint, 70).safeBeat(true));
		}
		if (shot.cosmic) {
			cosmic(run, shot, length, seed);
		}
		if (shot.quiet > 0.0F) {
			EpicSound.duck(Math.min(EpicSound.MAX_DUCK, length));
			if (!shot.cosmic) {
				FilmSound.hum(FilmSound.Cue.HUM_ENERGY, length + 10, 0.55F, shot.quiet);
			}
		}
		if (shot.bulletTicks > 0) {
			BulletTime.start(shot.bulletSigma, shot.bulletTicks);
		}
		accents(run, shot, tint, seed);
	}

	/**
	 * The film's own Kenney particle accents at a shot's start (own client, inside {@link FilmParticleProviders}' budgets):
	 * the impact cut throws sparks, dirt, a smoke ring and an expanding circle at the aim; a cosmic shot drifts stars and
	 * flares around the eye; heat haze adds a few flame tongues. Deterministic from the shot seed.
	 */
	private static void accents(Run run, FilmShot shot, int tint, long seed) {
		Vec3 aim = run.stage.aim;
		if (FilmLens.has(shot.lens, FilmLens.IMPACT)) {
			FilmParticleProviders.burst(dev.kultivator.fx.FilmParticles.SPARK, aim.add(0.0D, 0.6D, 0.0D), 28, 0.55D, tint, 0.5F, 18, seed);
			FilmParticleProviders.burst(dev.kultivator.fx.FilmParticles.DIRT, aim.add(0.0D, 0.3D, 0.0D), 14, 0.35D, 0x6A5844, 0.35F, 30, seed + 1);
			FilmParticleProviders.ring(dev.kultivator.fx.FilmParticles.SMOKE, aim, 2.0D, 16, 0.25D, 0xBFB8B0, 2.4F, 40);
			FilmParticleProviders.spawn(dev.kultivator.fx.FilmParticles.CIRCLE, aim.add(0.0D, 0.2D, 0.0D), Vec3.ZERO, tint, 9.0F, 16);
			FilmParticleProviders.spawn(dev.kultivator.fx.FilmParticles.MUZZLE, aim.add(0.0D, 1.2D, 0.0D), Vec3.ZERO, 0xFFFFFF, 4.0F, 6);
		}
		if (shot.cosmic) {
			Vec3 eye = run.stage.eye(1.0F);
			long mix = seed * 0x9E3779B97F4A7C15L;
			for (int i = 0; i < 14; i++) {
				mix = mix * 6364136223846793005L + 1442695040888963407L;
				double a = ((mix >>> 33) & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
				double r = 4.0D + ((mix >>> 17) & 0xFF) / 255.0D * 6.0D;
				double h = 2.0D + ((mix >>> 9) & 0xFF) / 255.0D * 6.0D;
				Vec3 at = eye.add(Math.cos(a) * r, h, Math.sin(a) * r);
				FilmParticleProviders.spawn(i % 3 == 0 ? dev.kultivator.fx.FilmParticles.FLARE : dev.kultivator.fx.FilmParticles.STAR, at,
						new Vec3(0.0D, 0.01D, 0.0D), i % 2 == 0 ? tint : 0xFFFFFF, 0.6F + (i % 4) * 0.15F, 40);
			}
		}
		if (FilmLens.has(shot.lens, FilmLens.HEAT_HAZE)) {
			for (int i = 0; i < 6; i++) {
				double a = Math.PI * 2.0D * i / 6.0D + (seed & 7);
				FilmParticleProviders.spawn(dev.kultivator.fx.FilmParticles.FLAME, aim.add(Math.cos(a) * 2.5D, 0.2D, Math.sin(a) * 2.5D),
						new Vec3(0.0D, 0.06D, 0.0D), 0xFF8A3A, 1.0F, 24);
			}
		}
	}

	private static void cosmic(Run run, FilmShot shot, int length, long seed) {
		CosmicSky.Palette palette = paletteOf(run);
		float yaw = run.stage.yaw0;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			yaw = minecraft.player.getYRot();
		}
		CosmicSky.show(length + 8, palette, seed, yaw, true, palette != CosmicSky.Palette.EMBER);
		Scene scene = run.scene;
		SkyDirector.grade(SkyDirector.Grade.SK_STARS, 0.9F, 0.65F, 10, scene.priority, scene.pos, true, 1.0F, scene.pos.y);
		scene.at(Math.max(10, length), () -> SkyDirector.release(20, scene.priority));
		EpicSound.duck(Math.min(EpicSound.MAX_DUCK, length));
		FilmSound.hum(FilmSound.Cue.HUM_ENERGY, length + 10, 0.6F, shot.quiet > 0.0F ? shot.quiet : 0.6F);
		TechniqueSpec spec = run.spec;
		if (spec != null) {
			Dampak d = spec.dampak();
			Dampak.Vista vista = shot.vista != null ? shot.vista : d != null ? d.vista() : Dampak.Vista.NONE;
			if (vista != Dampak.Vista.NONE) {
				Vec3 eye = run.stage.eye(1.0F);
				Vec3 look = minecraft.player != null ? minecraft.player.getViewVector(1.0F) : run.stage.forward;
				try {
					dev.kultivator.client.dampak.cinema.Vistas.test(scene, vista, spec, eye, look, length + 6, run.ctx.casterId());
				} catch (RuntimeException | LinkageError exception) {
					warn("vista", exception);
				}
			}
		}
	}

	private static CosmicSky.Palette paletteOf(Run run) {
		TechniqueSpec spec = run.spec;
		Dao dao = spec == null ? null : spec.dao();
		if (dao == null) {
			return run.film.titah ? CosmicSky.Palette.EMBER : CosmicSky.Palette.VIOLET;
		}
		return switch (dao) {
			case FIRE -> CosmicSky.Palette.EMBER;
			case ICE -> CosmicSky.Palette.FROST;
			case LIFE -> CosmicSky.Palette.JADE;
			case SHADOW -> CosmicSky.Palette.RED;
			case SWORD, BODY -> CosmicSky.Palette.EMBER;
			default -> CosmicSky.Palette.VIOLET;
		};
	}

	private static @Nullable Vec3 lightPoint(Run run, FilmShot shot) {
		if (shot.flareSpace != null) {
			return run.stage.resolve(shot.flareSpace, shot.flareProp, shot.fx, shot.fy, shot.fz, 1.0F);
		}
		if (shot.lookSpace != null) {
			return run.stage.resolve(shot.lookSpace, shot.lookProp, shot.lx, shot.ly, shot.lz, 1.0F);
		}
		return null;
	}

	private static void finishAct(Run run, ActRun ar, boolean interrupted) {
		ar.state = DONE;
		ar.path = null;
		// the next act starting right now joins without an exit; otherwise the camera eases back to the eye
		boolean next = false;
		for (ActRun other : run.acts) {
			if (other != ar && other.state == PENDING && other.start >= 0L && other.start <= now + 1) {
				next = true;
				break;
			}
		}
		if (!next || interrupted) {
			if (CinematicDirector.filmShooting()) {
				if (ImpactFrame.showing() || LensLayers.dipCover() > 0.9F) {
					CinematicDirector.abort();
				} else {
					CinematicDirector.skip();
				}
			}
			LensLayers.off();
			CosmicSky.hide(10);
		}
	}

	private static void end(Run run, boolean cut) {
		if (run.ended) {
			return;
		}
		run.ended = true;
		holdUntil = now + 100L;
		for (ActRun ar : run.acts) {
			if (ar.state == RUNNING && cut && CinematicDirector.filmShooting()) {
				CinematicDirector.skip();
			}
			ar.state = DONE;
		}
		if (current == run) {
			current = null;
		}
		LensLayers.off();
		CosmicSky.hide(10);
		FilmSound.stopAll();
		log("ended " + run.film.id + " camera " + run.camera + " t");
	}

	private static void log(String line) {
		LOG.add(line);
		if (LOG.size() > 40) {
			LOG.remove(0);
		}
	}

	private static int warnings;

	private static void warn(String where, Throwable exception) {
		if (warnings++ < 5) {
			Kultivator.LOGGER.warn("Kultivator: film {} failed", where, exception);
		}
	}
}
