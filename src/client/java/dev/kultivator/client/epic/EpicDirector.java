package dev.kultivator.client.epic;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.ShotGuard;
import dev.kultivator.client.epic.fx.EpicDisplays;
import dev.kultivator.client.epic.screen.EpicScreen;
import dev.kultivator.client.epic.screen.Letterbox;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.EpicSound;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.VisualState;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runs the 7b scenes (DESIGN 23.6): starts, merges and ends them, grants geometry inside the budget, and answers the
 * hooks of other client code. Each (moment, beat) starts or extends a scene from {@link SceneCatalog}, keyed by
 * (moment, subject or position). A scene fades out after 120 s without a beat, on ABORT / STOP, a level change,
 * disconnect or the protagonist's death; everything (sky, sound gains, music, camera) is restored at once. The higher
 * priority wins the camera, the sky grade and the music; lower scenes keep their VFX inside the budget. Unknown moment
 * or beat ids are ignored, so a newer server never breaks an older client. Idle cost: a few booleans per tick and the
 * visual-state edge scan every 5 ticks.
 */
public final class EpicDirector {

	private static final List<Scene> SCENES = new ArrayList<>();
	private static final List<Scene> BY_PRIORITY = new ArrayList<>();
	private static final Map<Integer, Integer> VISUALS = new HashMap<>();
	private static final Map<Integer, Long> FAILS = new HashMap<>();
	private static final Map<String, Long> TECH_SEEN = new HashMap<>();
	private static boolean geometry;
	private static boolean sorted = true;
	private static long ticks;
	private static long telegraphUntil;
	private static @Nullable ClientLevel lastLevel;
	private static boolean wasDead;
	private static int warnings;
	private static long lastDeviationPulse;

	// ================================================================ entries

	/** A server cue (client thread). */
	static void onCue(Payloads.EpicMoment cue) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || !EpicIds.known(cue.moment())) {
			return;
		}
		boolean own = cue.viewer() == minecraft.player.getId();
		if (cue.beat() == EpicIds.BEAT_STOP_ALL && own && (cue.flags() & EpicIds.FLAG_TEST) != 0) {
			stopAll();
			return;
		}
		if (cue.beat() < 0 || cue.beat() > EpicIds.maxBeat(cue.moment())) {
			return;
		}
		// Others' scenes follow "Efek pemain lain" (the Kehancuran safety banner is the one exception).
		if (!own && EffectQuality.othersScale() <= 0.0F && cue.moment() != EpicIds.KEHANCURAN) {
			return;
		}
		long delay = cue.at() - minecraft.level.getGameTime();
		int d = (int) Math.max(EpicIds.SCHEDULE_MIN, Math.min(EpicIds.SCHEDULE_MAX, delay));
		try {
			Scene scene = sceneFor(cue.moment(), cue.subject(), cue.viewer(), own, new Vec3(cue.x(), cue.y(), cue.z()), cue.seed(), false);
			scene.flags = cue.flags();
			scene.s = Math.max(0, Math.min(EpicIds.SCALE_MAX, cue.s()));
			scene.a = cue.a();
			scene.b = cue.b();
			scene.f = cue.f();
			scene.pos = new Vec3(cue.x(), cue.y(), cue.z());
			scene.beat = cue.beat();
			scene.idle = 0;
			EpicBudget.sceneStarted();
			SceneCatalog.beat(scene, cue.moment(), cue.beat(), Math.max(0, d));
		} catch (RuntimeException exception) {
			warn("cue " + EpicIds.momentName(cue.moment()) + "/" + cue.beat(), exception);
		}
	}

	/** Called by {@code SpecFx.play} for every technique cue; starts TECH_ULTIMATE or KEHANCURAN_SELF. Never throws. */
	public static void onTechFx(Payloads.TechFx payload) {
		// Phase 30 (DESIGN 30.2): Film Spektakuler sees every event first (arming, the climax, event anchors; never throws)
		dev.kultivator.client.epic.film.FilmDirector.onTechFx(payload);
		try {
			techFx(payload);
		} catch (RuntimeException | LinkageError exception) {
			warn("techFx", exception);
		}
	}

	private static void techFx(Payloads.TechFx payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || !ClientCultivation.serverEpic()) {
			return;
		}
		TechniqueSpec spec = TechniqueRegistry.byNet(payload.spec());
		if (spec == null) {
			return;
		}
		Phase phase = Phase.byOrdinal(payload.phase());
		Entity caster = minecraft.level.getEntity(payload.caster());
		boolean own = caster == minecraft.player;
		if (!own && EffectQuality.othersScale() <= 0.0F) {
			return;
		}
		Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
		Vec3 target = new Vec3(payload.tx(), payload.ty(), payload.tz());
		boolean kehancuran = (payload.flags() & Payloads.TechFx.FLAG_KEHANCURAN) != 0;
		if (kehancuran && (phase == Phase.IMPACT || phase == Phase.CAST && spec.fx(Phase.IMPACT).isEmpty() && spec.hasDestruction())) {
			Vec3 impact = phase == Phase.CAST && target.lengthSqr() > 0.0D ? target : at;
			Scene scene = sceneFor(EpicIds.KEHANCURAN_SELF, payload.caster(), own ? minecraft.player.getId() : -1, own, impact, payload.seed(), true);
			scene.flags = localFlags(own);
			scene.f = Math.max(1.0F, payload.radius());
			scene.idle = 0;
			SceneCatalog.kehancuranSelf(scene, spec);
		}
		boolean ultimate = spec.pamungkas() && spec.grade().ordinal() >= TechniqueGrade.ABADI.ordinal() || spec.dewa();
		if (!ultimate || (payload.flags() & Payloads.TechFx.FLAG_ECHO) != 0) {
			return;
		}
		String cast = payload.caster() + ":" + spec.id();
		if (phase == Phase.ABORT) {
			// polish S8: a cancelled or replaced ultimate ends its moment at once (sky, shot, card)
			Scene scene = find(EpicIds.TECH_ULTIMATE, payload.caster());
			TECH_SEEN.remove(cast + ":cast");
			TECH_SEEN.remove(cast + ":climax");
			if (scene != null) {
				SceneCatalog.ultimate(scene, spec, EpicIds.TECH_END, target, false);
				if (own) {
					CinematicDirector.skip();
				}
			}
			dev.kultivator.client.fx.spec.AnimBridge.stopPose(payload.caster());
			return;
		}
		// polish S12: CAST of an uncharged ultimate (or the release of a charged one) opens the moment; only the event the
		// server marks FLAG_CLIMAX is the climax, once per cast; PULSE and MASS_HIT never are.
		boolean charged = spec.charge() != null;
		int beat;
		if (phase == Phase.CHARGE) {
			return;
		} else if (phase == Phase.CAST && !charged || charged && phase != Phase.CAST && !recent(cast + ":cast", 200L)) {
			beat = EpicIds.TECH_CAST;
		} else if ((payload.flags() & Payloads.TechFx.FLAG_CLIMAX) != 0 && phase != Phase.PULSE && phase != Phase.MASS_HIT) {
			beat = EpicIds.TECH_CLIMAX;
		} else if (phase == Phase.END) {
			beat = EpicIds.TECH_END;
		} else {
			return;
		}
		String key = beat == EpicIds.TECH_CAST ? cast + ":cast" : beat == EpicIds.TECH_CLIMAX ? cast + ":climax" : cast + ":end";
		Long seen = TECH_SEEN.get(key);
		long window = beat == EpicIds.TECH_CLIMAX ? Math.max(40L, payload.duration()) : 20L;
		if (seen != null && ticks - seen < window) {
			return;
		}
		TECH_SEEN.put(key, ticks);
		if (beat == EpicIds.TECH_END) {
			TECH_SEEN.remove(cast + ":cast");
			TECH_SEEN.remove(cast + ":climax");
		}
		Scene scene = sceneFor(EpicIds.TECH_ULTIMATE, payload.caster(), own ? minecraft.player.getId() : -1, own, at, payload.seed(), true);
		scene.flags = localFlags(own);
		scene.idle = 0;
		scene.pos = at;
		EpicBudget.sceneStarted();
		SceneCatalogPower.ultimate(scene, spec, beat, target, false, payload.duration());
		if (beat == EpicIds.TECH_CAST && (payload.flags() & Payloads.TechFx.FLAG_CLIMAX) != 0 && !recent(cast + ":climax", 40L)) {
			// the opening event is also the climax (a spec whose only layers are CAST): the climax beat follows, ≥ 8 ticks on
			TECH_SEEN.put(cast + ":climax", ticks);
			SceneCatalogPower.ultimate(scene, spec, EpicIds.TECH_CLIMAX, target, false, payload.duration());
		}
	}

	/** True when {@code key} was seen within the last {@code window} ticks. */
	private static boolean recent(String key, long window) {
		Long seen = TECH_SEEN.get(key);
		return seen != null && ticks - seen < window;
	}

	// ================================================================ Phase 10 hooks (DESIGN 28.2, 28.3)

	/** The live (not ending) TECH_ULTIMATE scene of this caster, or null. */
	public static @Nullable Scene ultimateScene(int casterId) {
		for (Scene scene : SCENES) {
			if (scene.moment == EpicIds.TECH_ULTIMATE && scene.subjectId == casterId && !scene.ending()) {
				return scene;
			}
		}
		return null;
	}

	/**
	 * The scene that carries a cast's Phase 10 marks and rides: the caster's live TECH_ULTIMATE scene, or a new one (a
	 * charge before its release, a lite row that is no ultimate, a server without 7b cues). A scene made here ends by
	 * itself {@code life} ticks later unless an ultimate beat took it over meanwhile. Null without a level.
	 */
	public static @Nullable Scene dampakScene(int casterId, boolean own, Vec3 pos, int seed, int life) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return null;
		}
		Scene scene = ultimateScene(casterId);
		if (scene != null) {
			return scene;
		}
		scene = sceneFor(EpicIds.TECH_ULTIMATE, casterId, own ? minecraft.player.getId() : -1, own, pos, seed, true);
		scene.flags = localFlags(own);
		scene.idle = 0;
		scene.pos = pos;
		EpicBudget.sceneStarted();
		Scene made = scene;
		made.at(Math.max(20, life), () -> {
			if (made.data == null) {
				made.end(20);
			}
		});
		return scene;
	}

	/** Re-reads the local SAFE, RESULT_SAFE and CAMERA_OK flags of an own scene (a timed shot checks the moment it starts). */
	public static void refreshFlags(Scene scene) {
		int local = EpicIds.FLAG_SAFE | EpicIds.FLAG_RESULT_SAFE | EpicIds.FLAG_CAMERA_OK;
		scene.flags = (scene.flags & ~local) | localFlags(scene.own);
	}

	/** A test scene of the local player ({@code /sinematik dampak}: FLAG_TEST skips the cooldowns, never the gates). */
	public static @Nullable Scene testScene() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return null;
		}
		Scene scene = clientScene(EpicIds.TECH_ULTIMATE, minecraft.player, true);
		scene.flags |= EpicIds.FLAG_TEST;
		Scene made = scene;
		made.at(400, () -> {
			if (made.data == null) {
				made.end(20);
			}
		});
		return scene;
	}

	/** The seal glyph of an ultimate (its title card), for the marks of Phase 10. */
	public static String seal(TechniqueSpec spec) {
		return SceneCatalogPower.sealOf(spec.id(), spec.dewa() || spec.grade() == TechniqueGrade.DEWA);
	}

	/** True while any 7b scene owns the camera, the bars or a FULL card. */
	public static boolean cinematicActive() {
		return CinematicDirector.active() || Letterbox.amount() > 0.0F || TitleCard.fullInSafeBeat();
	}

	/** True while Mode Rekam hides the vanilla HUD (a safe beat of the local player's own moment). */
	public static boolean recordHide() {
		if (!EpicSettings.record()) {
			return false;
		}
		return CinematicDirector.active() || TitleCard.fullInSafeBeat();
	}

	/** True while a strike's telegraph window is open (no shot may start or continue). */
	public static boolean telegraphOpen() {
		return ticks < telegraphUntil;
	}

	/** True while any granted element may draw geometry (the renderer's one boolean). */
	public static boolean anyGeometry() {
		return geometry;
	}

	/** Every live scene, highest priority first (the renderer and the budget). */
	public static List<Scene> scenesByPriority() {
		if (!sorted) {
			BY_PRIORITY.clear();
			BY_PRIORITY.addAll(SCENES);
			BY_PRIORITY.sort((x, y) -> {
				if (x.own != y.own) {
					return x.own ? -1 : 1;
				}
				return Integer.compare(y.priority, x.priority);
			});
			sorted = true;
		}
		return BY_PRIORITY;
	}

	/** The client tick counter of the director. */
	public static long ticks() {
		return ticks;
	}

	// ================================================================ helpers for the catalog

	/** Opens the telegraph window for {@code ticks} from now (PRE → STRIKE). */
	static void telegraph(int duration) {
		telegraphUntil = Math.max(telegraphUntil, ticks + duration);
	}

	static void closeTelegraph() {
		telegraphUntil = 0L;
	}

	static void markFail(int subject) {
		FAILS.put(subject, ticks);
	}

	static boolean recentFail(int subject, int window) {
		Long at = FAILS.get(subject);
		return at != null && ticks - at <= window;
	}

	/** Finds a live scene of {@code moment} for {@code subject} (or null). */
	static @Nullable Scene find(int moment, int subject) {
		for (Scene scene : SCENES) {
			if (scene.moment == moment && scene.subjectId == subject) {
				return scene;
			}
		}
		return null;
	}

	/** Starts a client-derived scene (Dharma, Domain, Enlightenment, Deviation, tests of 20-25). */
	static Scene clientScene(int moment, Entity subject, boolean own) {
		Minecraft minecraft = Minecraft.getInstance();
		int viewer = own && minecraft.player != null ? minecraft.player.getId() : -1;
		Scene scene = sceneFor(moment, subject.getId(), viewer, own, subject.position(), subject.getId() * 31 + (int) ticks, true);
		scene.flags = localFlags(own);
		scene.idle = 0;
		scene.pos = subject.position();
		EpicBudget.sceneStarted();
		return scene;
	}

	/**
	 * Flags of a client-derived scene: CAMERA_OK for one's own, SAFE and RESULT_SAFE from a local check (no mob targeting the
	 * player within 16 b, no enemy within 24 b, not burning, falling or in fluid).
	 */
	static int localFlags(boolean own) {
		if (!own) {
			return 0;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return 0;
		}
		int flags = ClientCultivation.serverEpicCamera() ? EpicIds.FLAG_CAMERA_OK : 0;
		boolean calm = !player.isOnFire() && player.fallDistance <= 2.0D && !player.isInWater() && !player.isInLava() && !player.isPassenger();
		boolean targeted = false;
		boolean enemy = false;
		for (Entity entity : player.level().getEntities(player, new AABB(player.blockPosition()).inflate(24.0D),
				e -> e instanceof Mob || e instanceof Enemy)) {
			if (!entity.isAlive()) {
				continue;
			}
			if (entity instanceof Enemy) {
				enemy = true;
			}
			if (entity instanceof Mob mob && mob.getTarget() == player && entity.distanceTo(player) <= 16.0F) {
				targeted = true;
			}
		}
		if (calm && !targeted) {
			flags |= EpicIds.FLAG_SAFE;
		}
		if (calm && !enemy) {
			flags |= EpicIds.FLAG_RESULT_SAFE;
		}
		return flags;
	}

	private static Scene sceneFor(int moment, int subject, int viewer, boolean own, Vec3 pos, int seed, boolean client) {
		int key = moment == EpicIds.HEART_DEMON ? viewer : subject;
		for (Scene scene : SCENES) {
			if (scene.moment != moment || scene.ending) {
				continue;
			}
			int other = moment == EpicIds.HEART_DEMON ? scene.viewerId : scene.subjectId;
			if (key >= 0 ? other == key : scene.pos.distanceToSqr(pos) < 16.0D * 16.0D) {
				return scene;
			}
		}
		Scene scene = new Scene(moment, key, own, viewer, priority(moment), client, pos, seed);
		SCENES.add(scene);
		sorted = false;
		return scene;
	}

	/** The priority of 23.6 (higher wins the camera, the sky and the music). */
	static int priority(int moment) {
		return switch (moment) {
			case EpicIds.CHEAT_DEATH -> 11;
			case EpicIds.TRIBULATION, EpicIds.HEART_DEMON -> 10;
			case EpicIds.BREAKTHROUGH, EpicIds.DEVIATION -> 9;
			case EpicIds.ASCENSION -> 8;
			case EpicIds.CALAMITY -> 7;
			case EpicIds.KEHANCURAN, EpicIds.KEHANCURAN_SELF -> 6;
			case EpicIds.TECH_ULTIMATE -> 4;
			case EpicIds.SECT, EpicIds.RECRUIT, EpicIds.TOURNAMENT, EpicIds.DISCIPLE_BT, EpicIds.SURPASS -> 4;
			case EpicIds.BOSS -> 2;
			case EpicIds.DHARMA, EpicIds.DOMAIN, EpicIds.ENLIGHTEN -> 1;
			default -> 0;
		};
	}

	// ================================================================ tick

	/** Once per client tick (END_CLIENT_TICK). */
	static void tick(Minecraft minecraft) {
		ticks++;
		ShaderCompat.tick();
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			clearAll();
			VISUALS.clear();
			return;
		}
		if (level == null || minecraft.player == null) {
			return;
		}
		boolean dead = minecraft.player.isDeadOrDying();
		if (dead && !wasDead) {
			for (Scene scene : SCENES) {
				if (scene.own) {
					scene.end(10);
				}
			}
			CinematicDirector.abort();
			EpicSound.restore();
			MusicCue.clear();
			EpicClock.reset();
			SkyDirector.releaseOwn(20);
			TitleCard.clear();
			Letterbox.clear();
		}
		wasDead = dead;
		if (ticks % 5 == 0) {
			scanVisuals(minecraft, level);
		}
		int presses = CinematicDirector.tick();
		if (presses > 0) {
			onSkip(presses >= 2);
		}
		if (minecraft.gui.screen() != null && CinematicDirector.active()) {
			CinematicDirector.abort();
			EpicSound.restore();
		}
		EpicDisplays.tick();
		if (SCENES.isEmpty()) {
			geometry = false;
			if (EpicSound.busy()) {
				EpicSound.restore();
			}
			if (EpicClock.slowed() || EpicClock.frozen()) {
				EpicClock.reset();
			}
			SkyDirector.tick();
			MusicCue.tick();
			return;
		}
		EpicClock.tick();
		SkyDirector.tick();
		MusicCue.tick();
		EpicSound.tick();
		boolean changed = false;
		for (Iterator<Scene> it = SCENES.iterator(); it.hasNext(); ) {
			Scene scene = it.next();
			if (scene.idle > Scene.WATCHDOG && !scene.ending) {
				scene.end(40);
			}
			boolean alive;
			SkyDirector.context(scene);
			try {
				alive = scene.tick();
			} catch (RuntimeException exception) {
				warn("scene", exception);
				alive = false;
				scene.kill();
			} finally {
				SkyDirector.context(null);
			}
			if (!alive) {
				it.remove();
				Letterbox.sceneEnded(scene);
				SkyDirector.sceneEnded(scene);
				changed = true;
			}
		}
		if (changed) {
			sorted = false;
			if (SCENES.isEmpty()) {
				EpicBudget.sceneEnded();
			}
		}
		allocate(minecraft);
		tickDeviationReminder(minecraft);
	}

	/** Grants geometry and the large-layer slots for this tick (own scenes first, by priority, then by keep rank). */
	private static void allocate(Minecraft minecraft) {
		List<Scene> scenes = scenesByPriority();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		Scene nearestOther = null;
		double best = Double.MAX_VALUE;
		for (Scene scene : scenes) {
			scene.geometryAllowed = scene.own;
			if (!scene.own) {
				double distance = scene.pos.distanceToSqr(camera);
				if (distance < best) {
					best = distance;
					nearestOther = scene;
				}
				double range = scene.has(EpicIds.FLAG_SKY_SCALE) ? 192.0D : 96.0D;
				scene.falloff = (float) Math.max(0.0D, Math.min(1.0D, 1.0D - (Math.sqrt(distance) - 48.0D) / (range - 48.0D)));
			} else {
				scene.falloff = 1.0F;
			}
		}
		if (nearestOther != null) {
			nearestOther.geometryAllowed = true;
		}
		int cap = EpicBudget.largeCap();
		int used = 0;
		boolean soft = EpicBudget.softGeometry();
		boolean thin = EpicBudget.thinGeometry();
		boolean any = false;
		for (Scene scene : scenes) {
			int sceneLarge = 0;
			int sceneCap = scene.own ? cap : 1;
			for (Element element : scene.elements) {
				if (!element.geometry()) {
					continue;
				}
				element.geo = scene.geometryAllowed && element.largeRank() == 0 && (element.soft() ? soft : thin);
				if (element.geo) {
					any = true;
				}
			}
			for (int rank = 1; rank <= 9; rank++) {
				for (Element element : scene.elements) {
					if (!element.geometry() || element.largeRank() != rank) {
						continue;
					}
					boolean allowed = scene.geometryAllowed && soft && used < cap && sceneLarge < sceneCap;
					element.geo = allowed;
					if (allowed) {
						used++;
						sceneLarge++;
						any = true;
					}
				}
			}
		}
		geometry = any;
	}

	/** The visual-state edge scan: Dharma, Domain and Deviation of players within 64 b; Enlightenment of the local player. */
	private static void scanVisuals(Minecraft minecraft, ClientLevel level) {
		LocalPlayer self = minecraft.player;
		boolean enabled = ClientCultivation.serverEpic();
		for (AbstractClientPlayer player : level.players()) {
			if (player.distanceToSqr(self) > 64.0D * 64.0D) {
				continue;
			}
			VisualState visual = ClientCultivation.visual(player);
			int flags = visual.flags();
			Integer previous = VISUALS.put(player.getId(), flags);
			if (previous == null) {
				continue;
			}
			int rising = flags & ~previous;
			if (rising == 0 || !enabled) {
				continue;
			}
			boolean own = player == self;
			try {
				if ((rising & VisualState.DHARMA) != 0) {
					SceneCatalog.dharma(clientScene(EpicIds.DHARMA, player, own), visual);
				}
				if ((rising & VisualState.DOMAIN) != 0) {
					SceneCatalog.domain(clientScene(EpicIds.DOMAIN, player, own), visual.dao(), visual);
				}
				if ((rising & VisualState.DEVIATION) != 0) {
					boolean chained = recentFail(player.getId(), 40);
					SceneCatalog.deviation(clientScene(EpicIds.DEVIATION, player, own), chained);
				}
				if (own && (rising & VisualState.ENLIGHTENED) != 0) {
					SceneCatalog.enlighten(clientScene(EpicIds.ENLIGHTEN, player, true));
				}
			} catch (RuntimeException exception) {
				warn("visual edge", exception);
			}
		}
		if (VISUALS.size() > 64) {
			VISUALS.keySet().removeIf(id -> level.getEntity(id) == null);
		}
		if (TECH_SEEN.size() > 64) {
			TECH_SEEN.values().removeIf(at -> ticks - at > 200L);
		}
	}

	/** A 0.5-s red edge pulse at most every 30 s while the local player deviates (C1). */
	private static void tickDeviationReminder(Minecraft minecraft) {
		if (minecraft.player != null && ClientCultivation.visual(minecraft.player).has(VisualState.DEVIATION) && ticks - lastDeviationPulse > 600L) {
			lastDeviationPulse = ticks;
			EpicScreen.inkEdge(0xB01010, 0.35F, 10);
		}
	}

	/** The skip key: ends the shot, clears the card queue, releases the music cue; twice also hides the bars. */
	static void onSkip(boolean twice) {
		CinematicDirector.skip();
		TitleCard.skip();
		MusicCue.release();
		EpicSound.restore();
		if (twice) {
			Scene own = null;
			for (Scene scene : SCENES) {
				if (scene.own) {
					own = scene;
					break;
				}
			}
			// Only an own scene can hold the bars; a double press with nothing playing changes nothing.
			if (own != null) {
				Letterbox.suppress(own);
			}
		}
	}

	/** Stops every test scene of the local player ({@code uji momen berhenti} also sends ABORTs; this is the client side). */
	public static void stopAll() {
		clearAll();
	}

	/** Ends everything at once and restores sky, sound gains, music and camera (disconnect, level change, stop). */
	public static void clearAll() {
		for (Scene scene : SCENES) {
			scene.kill();
		}
		SCENES.clear();
		BY_PRIORITY.clear();
		sorted = true;
		geometry = false;
		telegraphUntil = 0L;
		EpicBudget.sceneEnded();
		dev.kultivator.client.epic.film.FilmDirector.clear();
		CinematicDirector.clear();
		SkyDirector.clear();
		MusicCue.clear();
		EpicSound.restore();
		EpicClock.reset();
		TitleCard.clear();
		EpicScreen.clear();
		Letterbox.clear();
		EpicDisplays.clear();
		ShotGuard.strikeShown();
	}

	/** Logs at most a few warnings per session (presentation never breaks the game). */
	public static void warn(String where, Throwable exception) {
		if (warnings++ < 8) {
			Kultivator.LOGGER.warn("Kultivator: Momen Epik error in {}", where, exception);
		}
	}

	/** Number of live scenes (for {@code /sinematik info}). */
	public static int sceneCount() {
		return SCENES.size();
	}

	private EpicDirector() {
	}
}
