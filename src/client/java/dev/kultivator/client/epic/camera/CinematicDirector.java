package dev.kultivator.client.epic.camera;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.sound.EpicSound;
import dev.kultivator.epic.EpicIds;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The camera of the protagonist's shots (DESIGN 23.7), read by the cinematic mixins every frame. Only the local
 * player's own scenes direct it; the player's real position, rotation and inputs never change (the pose is applied inside
 * {@code Camera.update} after {@code alignWithEntity}, so the frustum follows it and nothing lingers). Every key keeps
 * working and ends the shot; only mouse look is paused, and a motion above 8° ends the shot. Shots enter with an 8-tick
 * blend from the eye, exit with a 10-tick blend, snap back instantly on a danger abort, use sine easing and the speed caps
 * of {@link Shots}, one {@code level.clip} per frame, and never start or continue past a hard gate ({@link ShotGuard}).
 * Every mixin entry costs one boolean while no shot runs.
 * <p>
 * Film Kosmik 3D (DESIGN 32.1.1, 32.1.5): a shot that carries a stage binding stands still at the stage's pocket while
 * the stage covers the world ({@link #stageBinding()}). It is never left by the ease back (that would show the world
 * from the pocket): a skip, a key, a mouse look or the shot's end raise the 2-tick black dip (full cover at once) and
 * snap back to the eye under it; a danger abort snaps at once as always.
 */
public final class CinematicDirector {

	/** A directed camera pose: world position and the vanilla yaw and pitch ({@code xRot}, negative looks up). */
	public record Pose(Vec3 pos, float yaw, float pitch) {
	}

	// Camera kinds for the cooldowns of 23.6.
	public static final int KIND_TRIBULATION = 1;
	public static final int KIND_BREAKTHROUGH = 2;
	public static final int KIND_ASCENSION = 3;
	public static final int KIND_HEART_DEMON = 4;
	public static final int KIND_DISCIPLE = 5;
	public static final int KIND_SECT = 6;
	public static final int KIND_KEHANCURAN = 7;
	public static final int KIND_ULTIMATE = 8;
	public static final int KIND_BOSS = 9;
	public static final int KIND_DHARMA = 10;
	public static final int KIND_EXTRA = 11;

	public static final int ENTER = 8;
	public static final int EXIT = 10;
	public static final float SKIP_DEGREES = 8.0F;

	private static boolean active;
	private static Shots.Shot shot;
	private static @Nullable Scene owner;
	private static int priority = -1;
	private static int kind;
	private static float age;
	private static boolean exiting;
	private static float exitAge;
	private static @Nullable Pose last;
	private static @Nullable Pose blendFrom;
	private static Vec3 anchor = Vec3.ZERO;
	private static float yaw0;
	private static float subjectHeight = 1.8F;
	private static boolean mirrored;
	private static boolean resultSafe;
	private static float mouseDegrees;
	/** Phase 10 (28.3.2): the running segment started with a masked cut (no enter blend). */
	private static boolean snapEnter;
	private static final float[] OUT = new float[5];
	// Phase 30 (DESIGN 30.2.3): the FOV factor and roll of the running film shot, and the skip presses of the last tick
	private static float filmFov = 1.0F;
	private static float filmRoll;
	private static int lastPresses;

	private static long ticks;
	private static long lastCameraEnd = -100_000L;
	private static @Nullable Scene lastScene;
	private static final Map<Integer, Long> KIND_LAST = new HashMap<>();
	private static final Map<Scene, Integer> SCENE_TICKS = new WeakHashMap<>();

	// SNAP (FOV punch, any camera mode, never with "Kurangi gerak").
	private static float snapFrom;
	private static float snapTo;
	private static long snapStart;
	private static long snapIn;
	private static long snapHold;
	private static final long SNAP_OUT = 400L;
	// Roll (Dutch modifier, or the heart demon's 3° in Penuh).
	private static float rollNow;
	private static float rollTarget;
	private static int rollLeft;

	// ================================================================ requests

	/**
	 * Asks for a shot of {@code scene}. {@code safeBeat} needs the server's SAFE flag (a danger micro-shot instead needs
	 * Penuh). Returns true when the shot started (or chained onto the running one).
	 */
	public static boolean shoot(Scene scene, Shots.Shot request, int cameraKind, int prio, boolean safeBeat) {
		if (!scene.own || !scene.has(EpicIds.FLAG_CAMERA_OK)) {
			return false;
		}
		if (request.type == Shots.Type.FILM) {
			// Phase 30: film shots enter only through shootFilm
			return false;
		}
		if (dev.kultivator.client.epic.film.FilmDirector.holdsCamera() && !request.danger) {
			// Phase 30 (30.2.2): while a Film Spektakuler is armed, the 7b and Phase 10 shots of that moment yield to it
			return false;
		}
		EpicSettings.Camera mode = mode();
		if (mode == EpicSettings.Camera.MATI) {
			return false;
		}
		boolean penuh = mode == EpicSettings.Camera.PENUH;
		if (request.danger && !penuh) {
			return false;
		}
		boolean result = request.result && scene.has(EpicIds.FLAG_RESULT_SAFE);
		if (safeBeat && !request.danger && !scene.has(EpicIds.FLAG_SAFE) && !result) {
			return false;
		}
		if (active && !exiting && prio < priority && owner != scene) {
			return false;
		}
		boolean test = scene.test();
		boolean sameScene = owner == scene || lastScene == scene;
		if (!test && !sameScene) {
			if (ticks - lastCameraEnd < 400L) {
				return false;
			}
			Long kindTime = KIND_LAST.get(cameraKind);
			long kindGap = cameraKind == KIND_ULTIMATE ? 1200L : cameraKind == KIND_DHARMA ? 6000L : 600L;
			if (kindTime != null && ticks - kindTime < kindGap && cameraKind != KIND_TRIBULATION) {
				return false;
			}
		}
		int sceneCap = penuh ? 400 : 240;
		int used = SCENE_TICKS.getOrDefault(scene, 0);
		int shotCap = request.type == Shots.Type.FLYOVER ? 160 : penuh ? 120 : 100;
		if (!request.danger && used >= sceneCap) {
			return false;
		}
		Vec3 feet = scene.feet(1.0F);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}
		double reach = request.ceremony ? 48.0D : 32.0D;
		if (feet.distanceTo(minecraft.player.position()) > reach) {
			return false;
		}
		ShotGuard.scanNow(penuh);
		String blocked = ShotGuard.blocked(penuh, result, scene.has(EpicIds.FLAG_LETHAL_NEXT), telegraph(scene));
		if (blocked != null) {
			return false;
		}
		float subjectYaw = scene.yaw();
		Vec3 eye = feet.add(0.0D, scene.height() * 0.9D, 0.0D);
		int remaining = request.danger ? shotCap : Math.min(shotCap, sceneCap - used);
		request.validate(feet, eye, subjectYaw, Math.max(20, remaining));
		if (request.hold > 0) {
			// Phase 10: the held last pose (R3 waiting for a late climax) comes after the validated path
			request.ticks = Math.min(request.ticks + request.hold, Math.max(request.ticks, remaining));
		}
		if (active && owner != scene) {
			// Another scene takes over the running shot: it counts as ended for the cooldowns.
			KIND_LAST.put(kind, ticks);
			lastCameraEnd = ticks;
		}
		// Blend from the pose shown last frame whenever a shot runs (also when another scene takes over), never snap.
		blendFrom = active && last != null ? last : null;
		// Phase 10 (28.3.2): a masked cut jumps straight to the new segment, only while the cloud wipe or an impact frame
		// hides the jump; otherwise it blends like any other shot.
		boolean masked = request.cut && (dev.kultivator.client.epic.screen.CloudWipe.cover() >= 0.8F
				|| dev.kultivator.client.epic.screen.ImpactFrame.showing());
		boolean fromStage = active && !exiting && shot != null && shot.stage != null && shot.stage != request.stage;
		if ((request.stage != null || fromStage) && !masked) {
			// Film Kosmik 3D (32.1.1, 32.1.5): a stage segment never blends in from elsewhere (the dome would cross the
			// terrain) and a shot after one never blends out of the pocket; both jump under the black dip
			dev.kultivator.client.epic.lens.LensLayers.dip();
			masked = true;
		}
		if (masked) {
			blendFrom = null;
		}
		snapEnter = masked;
		shot = request;
		owner = scene;
		priority = prio;
		kind = cameraKind;
		age = 0.0F;
		exiting = false;
		exitAge = 0.0F;
		anchor = feet;
		yaw0 = subjectYaw;
		subjectHeight = scene.height();
		mirrored = false;
		resultSafe = result;
		mouseDegrees = 0.0F;
		active = true;
		lastScene = scene;
		SkipKey.arm();
		if (request.roll != 0.0F) {
			roll(request.roll, request.ticks);
		}
		if (!masked) {
			EpicSound.whoosh(Math.min(40, request.ticks));
		}
		return true;
	}

	/**
	 * Phase 30 (DESIGN 30.2.2, 30.2.3): starts one Film Spektakuler shot of {@code scene}. Only {@code FilmDirector} calls
	 * it, after its own gates; this re-checks the local scene, CAMERA_OK, Kamera Spektakuler, every hard gate of 23.7.4
	 * ({@link ShotGuard#blocked} with the Penuh floor) and the scene's 400-tick camera cap. {@code masked} = a hard cut
	 * hidden by a mask (black dip, cloud wipe, impact frame or a whip smear ≥ 0.8): no enter blend. A running shot (of
	 * the film or not) is taken over with a blend from the pose shown last.
	 */
	public static boolean shootFilm(Scene scene, Shots.Shot request, boolean masked) {
		if (request.type != Shots.Type.FILM || request.film == null || !scene.own || !scene.has(EpicIds.FLAG_CAMERA_OK)) {
			return false;
		}
		if (mode() != EpicSettings.Camera.PENUH || !EpicSettings.spektakuler()) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}
		int used = SCENE_TICKS.getOrDefault(scene, 0);
		if (used >= 400) {
			return false;
		}
		ShotGuard.scanNow(true);
		if (ShotGuard.blocked(true, false, scene.has(EpicIds.FLAG_LETHAL_NEXT), telegraph(scene)) != null) {
			return false;
		}
		request.ticks = Math.min(request.ticks + request.hold, Math.max(request.ticks, 400 - used));
		if (active && owner != scene) {
			KIND_LAST.put(kind, ticks);
			lastCameraEnd = ticks;
		}
		blendFrom = active && last != null && !masked ? last : null;
		snapEnter = masked;
		shot = request;
		owner = scene;
		priority = Math.max(priority, scene.priority);
		kind = KIND_ULTIMATE;
		age = 0.0F;
		exiting = false;
		exitAge = 0.0F;
		anchor = scene.feet(1.0F);
		yaw0 = scene.yaw();
		subjectHeight = scene.height();
		mirrored = false;
		resultSafe = false;
		mouseDegrees = 0.0F;
		active = true;
		lastScene = scene;
		SkipKey.arm();
		return true;
	}

	/**
	 * Film Kosmik 3D (32.1.4): the stage binding of the running shot, non-null only while a stage shot runs and is not
	 * exiting. One boolean while no shot runs.
	 */
	public static dev.kultivator.client.epic.kosmik.Kosmik3D.@Nullable Binding stageBinding() {
		if (!active || exiting) {
			return null;
		}
		Shots.Shot running = shot;
		return running != null ? running.stage : null;
	}

	/** Phase 30: true while a film shot runs and is not easing back. */
	public static boolean filmShooting() {
		return active && !exiting && shot != null && shot.type == Shots.Type.FILM;
	}

	/** Phase 30: the skip-key presses of the last tick (0, 1, or 2 for a double press). */
	public static int lastPresses() {
		return lastPresses;
	}

	/** Phase 30: the film clock rate (1, or 0 while a hit-stop holds; bullet time never slows the film camera). */
	private static float filmRate() {
		return EpicClock.hitStopped() ? 0.0F : 1.0F;
	}

	/**
	 * Phase 10 (28.3.2): the sky ride's exit on the climax: an instant cut back to the eye while an impact frame or the
	 * cloud wipe hides it, else the normal 10-tick blend.
	 */
	public static void cutToEye() {
		if (!active) {
			return;
		}
		if (dev.kultivator.client.epic.screen.ImpactFrame.showing() || dev.kultivator.client.epic.screen.CloudWipe.cover() >= 0.8F) {
			abort();
		} else {
			skip();
		}
	}

	/** Ends the shot with the 10-tick ease back (skip key, inputs, the shot's end). */
	public static void skip() {
		if (active && !exiting) {
			if (shot != null && shot.stage != null) {
				// Film Kosmik 3D (32.1.5): never ease back from the pocket; the dip covers at once, the camera snaps to the eye
				dev.kultivator.client.epic.lens.LensLayers.dip();
				abort();
				return;
			}
			exiting = true;
			exitAge = 0.0F;
			rollTarget = 0.0F;
		}
	}

	/** Ends the shot at once with an instant snap (danger, screen, death, dimension change). */
	public static void abort() {
		if (active) {
			finish();
		}
		rollTarget = 0.0F;
		rollNow = 0.0F;
	}

	/** Stops everything (disconnect, level change). */
	public static void clear() {
		abort();
		snapTo = 0.0F;
		snapFrom = 0.0F;
		snapStart = 0L;
		owner = null;
		lastScene = null;
	}

	/** True while a shot (or its ease back) owns the view. */
	public static boolean active() {
		return active;
	}

	/** True while a shot runs that the given scene started. */
	public static boolean ownedBy(Scene scene) {
		return active && owner == scene;
	}

	/**
	 * Phase 10b (29.4): true while a shot of {@code scene} runs and is not easing back yet. A chained film segment starts
	 * only then, so a Backspace skip, a movement key or the end of the shot is never undone by the next segment.
	 */
	public static boolean shooting(Scene scene) {
		return active && !exiting && owner == scene;
	}

	/** An FOV punch to {@code percent} (±12 % × fovEffectScale) over {@code inTicks}, held {@code holdTicks}, then back. */
	public static void snap(float percent, int inTicks, int holdTicks) {
		if (EffectQuality.reduceMotion()) {
			return;
		}
		long now = Util.getMillis();
		snapFrom = currentSnap(now);
		snapTo = Mth.clamp(percent, -12.0F, 12.0F) / 100.0F;
		snapStart = now;
		snapIn = Math.max(1, inTicks) * 50L;
		snapHold = Math.max(0, holdTicks) * 50L;
	}

	/** A roll of {@code degrees} for {@code ticks} (eased at ≤ 5°/s; none under "Kurangi gerak"). */
	public static void roll(float degrees, int ticks) {
		if (EffectQuality.reduceMotion()) {
			return;
		}
		rollTarget = Mth.clamp(degrees, -8.0F, 8.0F);
		rollLeft = Math.max(1, ticks);
	}

	// ================================================================ mixin entries (one boolean while idle)

	/** The pose of the running shot for this frame, or null when no shot owns the view. */
	public static @Nullable Pose pose(Camera camera, DeltaTracker deltaTracker) {
		if (!active) {
			return null;
		}
		return compute(camera, deltaTracker);
	}

	/** The FOV multiplier (1 = unchanged). */
	public static float fovScale() {
		if (active && shot != null && shot.type == Shots.Type.FILM && filmFov != 1.0F) {
			// Phase 30 (30.2.3): the film's factor k in 0.55-1.45, applied as 1 + (k - 1) x fovEffectScale
			float effect = (float) (double) Minecraft.getInstance().options.fovEffectScale().get();
			return 1.0F + (Mth.clamp(filmFov, 0.55F, 1.45F) - 1.0F) * effect;
		}
		if (snapStart == 0L) {
			return 1.0F;
		}
		long now = Util.getMillis();
		float value = currentSnap(now);
		if (value == 0.0F && now - snapStart > snapIn + snapHold + SNAP_OUT) {
			snapStart = 0L;
			return 1.0F;
		}
		float effect = (float) (double) Minecraft.getInstance().options.fovEffectScale().get();
		return 1.0F + value * effect;
	}

	/** The Dutch roll in degrees (0 = none). */
	public static float roll() {
		if (active && shot != null && shot.type == Shots.Type.FILM) {
			// Phase 30 (30.2.3): the film roll, ≤ 20° × damageTiltStrength (rate-limited by the film path)
			float tilt = (float) (double) Minecraft.getInstance().options.damageTiltStrength().get();
			return Mth.clamp(filmRoll, -20.0F, 20.0F) * tilt;
		}
		if (rollNow == 0.0F) {
			return 0.0F;
		}
		float tilt = (float) (double) Minecraft.getInstance().options.damageTiltStrength().get();
		return rollNow * tilt;
	}

	/** True while a shot runs and the vanilla first-person hand must be hidden. */
	public static boolean hideHand() {
		return active;
	}

	/**
	 * Mouse look while a shot runs: true pauses this frame's look ({@code dx}, {@code dy} are the accumulated counts); a
	 * motion above 8° (vanilla's formula) ends the shot and returns false.
	 */
	public static boolean consumeMouse(double dx, double dy) {
		if (!active || exiting) {
			return false;
		}
		if (dx == 0.0D && dy == 0.0D) {
			return true;
		}
		double s = Minecraft.getInstance().options.sensitivity().get() * 0.6D + 0.2D;
		double degrees = Math.sqrt(dx * dx + dy * dy) * s * s * s * 8.0D * 0.15D;
		mouseDegrees += (float) degrees;
		if (mouseDegrees > SKIP_DEGREES) {
			mouseDegrees = 0.0F;
			skip();
			return false;
		}
		return true;
	}

	// ================================================================ per tick

	/** Once per client tick (from the director). Returns the skip-key presses (0, 1 or 2). */
	public static int tick() {
		ticks++;
		int presses = SkipKey.presses();
		lastPresses = presses;
		if (rollLeft > 0 && --rollLeft == 0) {
			rollTarget = 0.0F;
		}
		rollNow += Mth.clamp(rollTarget - rollNow, -0.25F, 0.25F);
		if (Math.abs(rollNow) < 0.01F && rollTarget == 0.0F) {
			rollNow = 0.0F;
		}
		mouseDegrees *= 0.7F;
		if (!active) {
			return presses;
		}
		if (presses > 0) {
			skip();
		}
		if (exiting) {
			exitAge++;
			if (exitAge >= EXIT + 1) {
				finish();
			}
			return presses;
		}
		Shots.Shot running = shot;
		Scene scene = owner;
		if (running == null || scene == null) {
			finish();
			return presses;
		}
		boolean film = running.type == Shots.Type.FILM;
		age += film ? filmRate() : EpicClock.sigma();
		if (!running.danger) {
			SCENE_TICKS.merge(scene, 1, Integer::sum);
		}
		if (film && !EpicSettings.spektakuler()) {
			abort();
			return presses;
		}
		boolean penuh = mode() == EpicSettings.Camera.PENUH;
		boolean hurtOk = ShotGuard.tick(true, penuh);
		String blocked = ShotGuard.blocked(penuh, resultSafe, scene.has(EpicIds.FLAG_LETHAL_NEXT) && !resultSafe, telegraph(scene));
		if (!hurtOk || blocked != null || mode() == EpicSettings.Camera.MATI) {
			abort();
			return presses;
		}
		if (SkipKey.playerInput()) {
			skip();
			return presses;
		}
		// a film stage shot gets one more tick: the film cuts to its next shot in this tick (no dip at a planned cut)
		if (age >= running.ticks + (film && running.stage != null ? 1 : 0)) {
			skip();
		}
		return presses;
	}

	// ================================================================ internals

	private static Pose compute(Camera camera, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		Shots.Shot running = shot;
		if (level == null || minecraft.player == null || running == null) {
			finish();
			return null;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		Vec3 eyePos = camera.position();
		Pose eye = new Pose(eyePos, camera.yRot(), camera.xRot());
		if (exiting) {
			float t = Math.min(1.0F, (exitAge + partial) / EXIT);
			if (t >= 1.0F || last == null) {
				return null;
			}
			if (running.type == Shots.Type.FILM) {
				filmFov = 1.0F + (filmFov - 1.0F) * (1.0F - ease(t) * 0.5F);
				filmRoll *= 1.0F - ease(t) * 0.5F;
			}
			return blend(last, eye, ease(t));
		}
		if (running.type == Shots.Type.FILM && running.film != null) {
			return filmPose(level, minecraft, running, eye, partial);
		}
		Scene scene = owner;
		Vec3 feet = scene != null ? scene.feet(partial) : anchor;
		Vec3 subjectEye = feet.add(0.0D, subjectHeight * 0.9D, 0.0D);
		float sigma = EpicClock.sigma();
		float progress = Mth.clamp((age + partial * sigma) / Math.max(1, running.ticks - running.hold), 0.0F, 1.0F);
		running.evaluate(ease(progress), feet, subjectEye, yaw0, OUT);
		if (running.stage != null) {
			// Film Kosmik 3D (32.5.6): Kamera Penuh's calm beat; the virtual eye follows the shot's eased progress
			running.stage.follow(ease(progress), age + partial * sigma);
		}
		double ox = OUT[0];
		double oz = OUT[2];
		if (mirrored) {
			// Reflect the lateral offset across the subject's facing axis.
			double yaw = Math.toRadians(yaw0);
			double rx = -Math.cos(yaw);
			double rz = -Math.sin(yaw);
			double lateral = ox * rx + oz * rz;
			ox -= 2.0D * lateral * rx;
			oz -= 2.0D * lateral * rz;
		}
		Vec3 target = new Vec3(feet.x + ox, feet.y + OUT[1], feet.z + oz);
		float yaw = OUT[3];
		float pitch = OUT[4];
		if (mirrored) {
			yaw = (float) (Mth.atan2(subjectEye.z - target.z, subjectEye.x - target.x) * Mth.RAD_TO_DEG) - 90.0F;
		}
		// One clip per frame from the subject's eye; stop 0.3 b before a hit, try the mirrored side, else skip.
		BlockHitResult hit = level.clip(new ClipContext(subjectEye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, minecraft.player));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 dir = target.subtract(subjectEye);
			double length = dir.length();
			double reach = Math.max(0.0D, subjectEye.distanceTo(hit.getLocation()) - 0.3D);
			if (reach < 1.2D) {
				if (!mirrored) {
					mirrored = true;
				} else {
					skip();
				}
			}
			if (length > 0.001D) {
				target = subjectEye.add(dir.scale(reach / length));
			}
		}
		double minY = level.getMinY() + 1.0D;
		if (target.y < minY) {
			target = new Vec3(target.x, minY, target.z);
		}
		Pose shotPose = new Pose(target, yaw, pitch);
		float enter = snapEnter ? 1.0F : Math.min(1.0F, (age + partial * sigma) / ENTER);
		Pose from = blendFrom != null ? blendFrom : eye;
		Pose pose = enter >= 1.0F ? shotPose : blend(from, shotPose, ease(enter));
		last = pose;
		return pose;
	}

	/** Phase 30: one frame of a film shot (the path, then one clip, the floor, the enter blend). */
	private static Pose filmPose(ClientLevel level, Minecraft minecraft, Shots.Shot running, Pose eye, float partial) {
		float rate = filmRate();
		float filmAge = age + partial * rate;
		dev.kultivator.client.epic.film.FilmPath path = running.film;
		path.evaluate(Math.min(filmAge, path.ticks()), partial);
		double[] o = path.out;
		Vec3 target = new Vec3(o[0], o[1], o[2]);
		boolean first = last == null || snapEnter && age < 1.0F;
		if (!first) {
			// one clip per frame from the pose shown last: the camera never passes into a block
			Vec3 from = last.pos();
			BlockHitResult hit = level.clip(new ClipContext(from, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player));
			if (hit.getType() != HitResult.Type.MISS) {
				Vec3 dir = target.subtract(from);
				double length = dir.length();
				double reach = Math.max(0.0D, from.distanceTo(hit.getLocation()) - 0.3D);
				if (length > 0.001D) {
					target = from.add(dir.scale(reach / length));
				}
			}
		} else {
			net.minecraft.core.BlockPos cell = net.minecraft.core.BlockPos.containing(target);
			if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
				// a masked jump into a block: stand on the surface of that column instead
				int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, cell.getX(), cell.getZ());
				target = new Vec3(target.x, surface + 0.3D, target.z);
			}
		}
		double minY = level.getMinY() + 1.0D;
		if (target.y < minY) {
			target = new Vec3(target.x, minY, target.z);
		}
		Pose shotPose = new Pose(target, (float) o[3], (float) o[4]);
		float enter = snapEnter ? 1.0F : Math.min(1.0F, filmAge / ENTER);
		float e = ease(enter);
		filmFov = (float) (1.0D + (o[5] - 1.0D) * e);
		filmRoll = (float) o[6] * e;
		Pose from = blendFrom != null ? blendFrom : eye;
		Pose pose = enter >= 1.0F ? shotPose : blend(from, shotPose, e);
		last = pose;
		return pose;
	}

	private static void finish() {
		filmFov = 1.0F;
		filmRoll = 0.0F;
		if (active) {
			lastCameraEnd = ticks;
			KIND_LAST.put(kind, ticks);
		}
		active = false;
		exiting = false;
		shot = null;
		last = null;
		blendFrom = null;
		snapEnter = false;
		priority = -1;
		rollTarget = 0.0F;
	}

	private static Pose blend(Pose a, Pose b, float t) {
		Vec3 pos = a.pos().lerp(b.pos(), t);
		float yaw = a.yaw() + Mth.wrapDegrees(b.yaw() - a.yaw()) * t;
		float pitch = Mth.lerp(t, a.pitch(), b.pitch());
		return new Pose(pos, yaw, pitch);
	}

	/** Sine in-out. */
	private static float ease(float t) {
		return (float) ((1.0D - Math.cos(Math.PI * Mth.clamp(t, 0.0F, 1.0F))) * 0.5D);
	}

	private static float currentSnap(long now) {
		if (snapStart == 0L) {
			return 0.0F;
		}
		long elapsed = now - snapStart;
		if (elapsed < snapIn) {
			return Mth.lerp(elapsed / (float) snapIn, snapFrom, snapTo);
		}
		if (elapsed < snapIn + snapHold) {
			return snapTo;
		}
		long out = elapsed - snapIn - snapHold;
		if (out < SNAP_OUT) {
			return Mth.lerp(out / (float) SNAP_OUT, snapTo, 0.0F);
		}
		return 0.0F;
	}

	private static boolean telegraph(Scene scene) {
		return dev.kultivator.client.epic.EpicDirector.telegraphOpen();
	}

	/** The camera mode in effect: "Kurangi gerak" forces Mati. */
	public static EpicSettings.Camera mode() {
		return EffectQuality.reduceMotion() ? EpicSettings.Camera.MATI : EpicSettings.camera();
	}

	private CinematicDirector() {
	}
}
