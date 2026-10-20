package dev.kultivator.client.epic.film;

import dev.kultivator.client.epic.lens.LensLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Evaluates one film shot per frame (DESIGN 30.2.4, 30.2.5): the keys resolved in their spaces, joined by a centripetal
 * Catmull-Rom spline with per-segment eases, the look (a point, a prop, a sky direction or a POV), the whip, the FOV curve
 * (keys, crash zoom, dolly), the Dutch roll, the low-angle floor and the shake, then the film caps of 30.2.3:
 * <ul>
 * <li>the path is shortened toward its first key when its average speed would pass 24 b/s or a sampled peak 48 b/s
 * (2.4 b per tick), sampled with every space frozen at one instant, so the subject's own motion is never counted;</li>
 * <li>the look turns at most 12° per tick (240°/s), a whip 45° per tick; the roll at most 3° per tick, ≤ 20°;</li>
 * <li>the camera stays ≤ 48 b above the subject's feet and ≤ min(64, render distance × 16 − 24) b from it.</li>
 * </ul>
 * The collision clip and the blends are {@code CinematicDirector}'s. Client thread only.
 */
public final class FilmPath {

	public static final double MAX_AVG_SPEED = 24.0D;
	public static final double MAX_PEAK_SPEED = 48.0D;
	public static final float MAX_TURN_TICK = 12.0F;
	public static final float MAX_WHIP_TICK = 45.0F;
	public static final float MAX_ROLL_TICK = 3.0F;
	public static final double MAX_HEIGHT = 48.0D;

	private final FilmShot shot;
	private final FilmStage stage;
	private final long seed;
	/** The shot's length after re-timing (≥ 4 ticks). */
	private final int ticks;
	private double shrink = 1.0D;
	private boolean evaluated;
	private float lastAge;
	private float lastYaw;
	private float lastPitch;
	private float lastRoll;
	private double dolly0 = -1.0D;
	private final float[] shake = new float[5];
	private final Vec3[] points;
	/** The last output: x, y, z, yaw, pitch, FOV factor, roll. */
	public final double[] out = new double[7];

	public FilmPath(FilmShot shot, FilmStage stage, long seed, int ticks) {
		this.shot = shot;
		this.stage = stage;
		this.seed = seed;
		this.ticks = Math.max(4, ticks);
		this.points = new Vec3[Math.max(2, shot.keys.size())];
	}

	public FilmShot shot() {
		return shot;
	}

	public int ticks() {
		return ticks;
	}

	/** Samples the path with the spaces frozen now and shortens it to the speed caps (once, at the shot start). */
	public void validate() {
		if (shot.keys.size() < 2 || pov()) {
			shrink = 1.0D;
			return;
		}
		int samples = 24;
		Vec3 previous = position(0.0F, 1.0F);
		double distance = 0.0D;
		double peak = 0.0D;
		double dt = ticks / 20.0D / samples;
		for (int i = 1; i <= samples; i++) {
			Vec3 p = position(i / (float) samples, 1.0F);
			double step = p.distanceTo(previous);
			distance += step;
			peak = Math.max(peak, step / dt);
			previous = p;
		}
		double seconds = ticks / 20.0D;
		double average = distance / seconds;
		double k = 1.0D;
		if (average > MAX_AVG_SPEED) {
			k = Math.min(k, MAX_AVG_SPEED / average);
		}
		if (peak > MAX_PEAK_SPEED) {
			k = Math.min(k, MAX_PEAK_SPEED / peak);
		}
		shrink = k;
	}

	/** The shortening factor validation applied (1 = none), for {@code /sinematik film info}. */
	public double shrink() {
		return shrink;
	}

	/** The pose at {@code age} presentation ticks into the shot; writes {@link #out}. */
	public void evaluate(float age, float partial) {
		float t = Mth.clamp(age / Math.max(1, ticks), 0.0F, 1.0F);
		Vec3 pos;
		Vec3 look;
		if (pov()) {
			Vec3 origin;
			Vec3 dir;
			FilmDirector.Track track = stage.track(shot.povProp);
			if (shot.povSubject || track == null) {
				origin = stage.eye(partial);
				dir = stage.look(partial);
			} else {
				origin = track.pos(partial);
				double yaw = Math.toRadians(track.yaw(partial));
				double pitch = Math.toRadians(track.pitch(partial));
				dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
			}
			if (dir.lengthSqr() < 1.0E-6D) {
				dir = stage.forward;
			}
			dir = dir.normalize();
			pos = origin.subtract(dir.scale(shot.povBack)).add(0.0D, shot.povUp, 0.0D);
			look = origin.add(dir.scale(16.0D));
		} else {
			pos = position(t, partial);
			if (shrink < 1.0D) {
				Vec3 first = points[0];
				pos = first.add(pos.subtract(first).scale(shrink));
			}
			look = lookAt(t, pos, partial);
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (shot.worm > 0.0F && level != null) {
			int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
			pos = new Vec3(pos.x, surface + shot.worm, pos.z);
		}
		// caps: height above the subject's feet and distance from it
		Vec3 feet = stage.feet(partial);
		if (pos.y - feet.y > MAX_HEIGHT) {
			pos = new Vec3(pos.x, feet.y + MAX_HEIGHT, pos.z);
		}
		double reach = FilmStage.reach();
		Vec3 offset = pos.subtract(feet);
		double length = offset.length();
		if (length > reach) {
			pos = feet.add(offset.scale(reach / length));
		}
		// the look
		double dx = look.x - pos.x;
		double dy = look.y - pos.y;
		double dz = look.z - pos.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
		boolean whipping = false;
		if (shot.whipAt >= 0.0F) {
			float start = shot.whipAt * ticks;
			float u = (age - start) / shot.whipTicks;
			if (u > 0.0F) {
				float e = Ease.IN_OUT.apply(Math.min(1.0F, u));
				yaw += shot.whipDeg * e;
				pitch += shot.whipPitch * e;
				whipping = u < 1.0F;
			}
		}
		// shake after the path (the collision clip runs after it, in the director)
		if (shot.shake != null) {
			FilmShake.sample(shot.shake, shot.shakeAmp, age, shot.shakeTicks, seed, shake);
			pos = pos.add(shake[0], shake[1], shake[2]);
			yaw += shake[3];
			pitch += shake[4];
		}
		pitch = Mth.clamp(pitch, -89.0F, 89.0F);
		float roll = curve(shot.roll, t, 0.0F);
		// the per-frame turn limits
		if (evaluated) {
			float dt = Math.max(0.0F, age - lastAge);
			float maxTurn = (whipping ? MAX_WHIP_TICK : MAX_TURN_TICK) * Math.max(dt, 0.0F);
			float dYaw = Mth.wrapDegrees(yaw - lastYaw);
			float dPitch = pitch - lastPitch;
			if (dt <= 0.0F) {
				yaw = lastYaw;
				pitch = lastPitch;
				roll = lastRoll;
			} else {
				yaw = lastYaw + Mth.clamp(dYaw, -maxTurn, maxTurn);
				pitch = lastPitch + Mth.clamp(dPitch, -maxTurn, maxTurn);
				float maxRoll = MAX_ROLL_TICK * dt;
				roll = lastRoll + Mth.clamp(roll - lastRoll, -maxRoll, maxRoll);
				LensLayers.motion(Mth.wrapDegrees(yaw - lastYaw) / dt, (pitch - lastPitch) / dt, whipping);
			}
		}
		evaluated = true;
		lastAge = age;
		lastYaw = yaw;
		lastPitch = pitch;
		lastRoll = roll;
		out[0] = pos.x;
		out[1] = pos.y;
		out[2] = pos.z;
		out[3] = yaw;
		out[4] = pitch;
		out[5] = fov(age, t, pos, partial);
		out[6] = roll;
	}

	// ================================================================ internals

	/** A POV ride: the subject's, or a prop's while it is tracked (else the shot's keys are its fallback path). */
	private boolean pov() {
		return shot.povSubject || shot.povProp != null && (stage.track(shot.povProp) != null || shot.keys.isEmpty());
	}

	/** The spline position at shot fraction {@code t}. */
	private Vec3 position(float t, float partial) {
		int n = shot.keys.size();
		if (n == 0) {
			Vec3 p = stage.resolve(FilmSpace.S, null, 0.0D, 2.0D, -4.0D, partial);
			points[0] = p;
			return p;
		}
		for (int i = 0; i < n; i++) {
			FilmKey key = shot.keys.get(i);
			points[i] = stage.resolve(key.space(), key.prop(), key.x(), key.y(), key.z(), partial);
		}
		if (n == 1) {
			return points[0];
		}
		FilmKey first = shot.keys.get(0);
		if (t <= first.t()) {
			return points[0];
		}
		FilmKey last = shot.keys.get(n - 1);
		if (t >= last.t()) {
			return points[n - 1];
		}
		int i = 0;
		while (i < n - 2 && t > shot.keys.get(i + 1).t()) {
			i++;
		}
		FilmKey a = shot.keys.get(i);
		FilmKey b = shot.keys.get(i + 1);
		float span = Math.max(1.0E-4F, b.t() - a.t());
		float u = a.ease().apply((t - a.t()) / span);
		Vec3 p1 = points[i];
		Vec3 p2 = points[i + 1];
		Vec3 p0 = i > 0 ? points[i - 1] : p1.add(p1.subtract(p2));
		Vec3 p3 = i + 2 < n ? points[i + 2] : p2.add(p2.subtract(p1));
		return centripetal(p0, p1, p2, p3, u);
	}

	/** Centripetal Catmull-Rom (α = 0.5, Barry-Goldman) between p1 and p2. */
	static Vec3 centripetal(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u) {
		double t0 = 0.0D;
		double t1 = t0 + Math.max(1.0E-3D, Math.sqrt(p1.distanceTo(p0)));
		double t2 = t1 + Math.max(1.0E-3D, Math.sqrt(p2.distanceTo(p1)));
		double t3 = t2 + Math.max(1.0E-3D, Math.sqrt(p3.distanceTo(p2)));
		double t = t1 + (t2 - t1) * u;
		Vec3 a1 = mix(p0, p1, (t - t0) / (t1 - t0));
		Vec3 a2 = mix(p1, p2, (t - t1) / (t2 - t1));
		Vec3 a3 = mix(p2, p3, (t - t2) / (t3 - t2));
		Vec3 b1 = mix(a1, a2, (t - t0) / (t2 - t0));
		Vec3 b2 = mix(a2, a3, (t - t1) / (t3 - t1));
		return mix(b1, b2, (t - t1) / (t2 - t1));
	}

	private static Vec3 mix(Vec3 a, Vec3 b, double k) {
		return new Vec3(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.z + (b.z - a.z) * k);
	}

	private Vec3 lookAt(float t, Vec3 pos, float partial) {
		if (!Float.isNaN(shot.skyYaw0)) {
			float e = Ease.IN_OUT.apply(t);
			double yaw = Math.toRadians(stage.yaw0 + Mth.lerp(e, shot.skyYaw0, shot.skyYaw1));
			double pitch = Math.toRadians(Mth.lerp(e, shot.skyPitch0, shot.skyPitch1));
			Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
			return pos.add(dir.scale(64.0D));
		}
		if (shot.lookSpace == null) {
			return stage.eye(partial);
		}
		Vec3 start = stage.resolve(shot.lookSpace, shot.lookProp, shot.lx, shot.ly, shot.lz, partial);
		if (shot.lookEndSpace == null) {
			return start;
		}
		Vec3 end = stage.resolve(shot.lookEndSpace, shot.lookEndProp, shot.lex, shot.ley, shot.lez, partial);
		// lerp the directions (a short-arc blend seen from the camera), not the far points
		Vec3 d0 = start.subtract(pos);
		Vec3 d1 = end.subtract(pos);
		double l0 = Math.max(1.0E-3D, d0.length());
		double l1 = Math.max(1.0E-3D, d1.length());
		float e = Ease.IN_OUT.apply(t);
		Vec3 dir = d0.scale(1.0D / l0).scale(1.0F - e).add(d1.scale(1.0D / l1).scale(e));
		if (dir.lengthSqr() < 1.0E-6D) {
			dir = d1;
		}
		return pos.add(dir.normalize().scale(Mth.lerp(e, l0, l1)));
	}

	private double fov(float age, float t, Vec3 pos, float partial) {
		double k = curve(shot.fov, t, 1.0F);
		if (shot.crashK > 0.0F) {
			float u = Math.min(1.0F, age / shot.crashTicks);
			k *= Mth.lerp(Ease.EXPO_OUT.apply(u), 1.0F, shot.crashK);
		}
		if (shot.dolly != null) {
			Vec3 target = switch (shot.dolly) {
				case SUBJECT -> stage.eye(partial);
				case AIM -> stage.aim;
				case PROP -> {
					FilmDirector.Track track = stage.track(shot.dollyProp);
					yield track != null ? track.pos(partial) : stage.aim;
				}
			};
			double d = Math.max(0.5D, pos.distanceTo(target));
			if (dolly0 < 0.0D) {
				dolly0 = d;
			}
			double base = Math.toRadians(Minecraft.getInstance().options.fov().get() * k);
			double wanted = 2.0D * Math.atan(Math.tan(base * 0.5D) * dolly0 / d);
			k *= wanted / Math.max(1.0E-3D, base);
		}
		return Mth.clamp(k, 0.55D, 1.45D);
	}

	private static float curve(java.util.List<FilmShot.Curve> keys, float t, float fallback) {
		if (keys.isEmpty()) {
			return fallback;
		}
		FilmShot.Curve first = keys.get(0);
		if (t <= first.t()) {
			return keys.size() == 1 && first.t() > 0.0F ? Mth.lerp(t / first.t(), fallback, first.value()) : first.value();
		}
		for (int i = 0; i < keys.size() - 1; i++) {
			FilmShot.Curve a = keys.get(i);
			FilmShot.Curve b = keys.get(i + 1);
			if (t <= b.t()) {
				float u = (t - a.t()) / Math.max(1.0E-4F, b.t() - a.t());
				return Mth.lerp(Ease.IN_OUT.apply(u), a.value(), b.value());
			}
		}
		return keys.get(keys.size() - 1).value();
	}
}
