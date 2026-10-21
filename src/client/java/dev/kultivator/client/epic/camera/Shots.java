package dev.kultivator.client.epic.camera;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The shot library (DESIGN 23.7.2). Positions are relative to the subject's interpolated feet; yaw 0 is the subject's
 * body yaw when the shot starts; pitch uses {@code xRot} (negative looks up). "Look" points at the subject's eye unless a
 * shot says otherwise. {@link Shot#validate} enforces the speed caps of 23.7.3 (average ≤ 7.6 b/s and ≤ 25°/s, so peaks
 * stay ≤ 12 b/s and ≤ 40°/s with sine easing): it stretches the duration, and if that breaks the shot's maximum it
 * shortens the path.
 */
public final class Shots {

	public enum Type {
		CRANE_UP(100), CRANE_DOWN(80), ORBIT(120), PUSH(80), PULLBACK(100), SKYPAN(70), LOWHERO(60), OTS(50), TWO_SHOT(80), STATIC(100),
		FLYOVER(160), KILLCAM(80),
		/** Phase 10 (DESIGN 28.3): one world-anchored segment of Perjalanan Langit (a ceremony, 48 b reach). */
		SKY_RIDE(80),
		/**
		 * Phase 10b (DESIGN 29.4): one world-anchored segment of Film Pusaka. The per-segment maximum stays 80 ticks; only
		 * the whole film (several validated segments joined by masked cuts) may run 240.
		 */
		PUSAKA_FILM(80),
		/**
		 * Phase 30 (DESIGN 30.2.3): one shot of a Film Spektakuler, evaluated per frame by its {@code FilmPath} with the
		 * film caps (≤ 6 s; the path is validated by the film, not by {@link Shot#validate}). Built only by
		 * {@code FilmDirector} through {@link Shots#film}; {@code CinematicDirector} accepts it only while Kamera Spektakuler
		 * is in effect.
		 */
		FILM(120);

		final int max;

		Type(int max) {
			this.max = max;
		}
	}

	public static final float MAX_SPEED = 7.6F;
	public static final float MAX_TURN = 25.0F;
	public static final float CRANE_UP_LIMIT = 24.0F;

	/** One shot: type, duration in ticks and its parameters. Built by the factories below, then validated. */
	public static final class Shot {
		public final Type type;
		public int ticks;
		float r0;
		float r1;
		float h0;
		float h1;
		float p0;
		float p1;
		float sweep;
		float side;
		float yawSweep;
		@Nullable Vec3 world;
		@Nullable Vec3 second;
		@Nullable Vec3[] path;
		@Nullable Vec3 look;
		float roll;
		boolean explicitPitch;
		float shrink = 1.0F;
		private float s0x;
		private float s0y;
		private float s0z;
		/** A failure result shot: may run under the health floor with RESULT_SAFE (no roll). */
		public boolean result;
		/** A danger micro-shot (Penuh only, ≤ 1.5 s). */
		public boolean danger;
		/** Ceremonies may go to 48 b instead of 32. */
		public boolean ceremony;
		/**
		 * Phase 10 (28.3.2): a segment start that may skip the enter blend (a masked cut). The director honours it only
		 * while the cloud wipe covers the screen or an impact frame shows; otherwise it blends as before.
		 */
		public boolean cut;
		/** Ticks the last pose is held after the path ends (R3 waits for a late climax), added after validation. */
		public int hold;
		/** The look target at the end of a SKY_RIDE or PUSAKA_FILM segment ({@code look} is the start). */
		@Nullable Vec3 lookEnd;
		/** Phase 30: the per-frame evaluator of a FILM shot. */
		public dev.kultivator.client.epic.film.@Nullable FilmPath film;
		/**
		 * Film Kosmik 3D (DESIGN 32.1, 32.5.6): the stage this shot shows (a FILM stage shot, or Kamera Penuh's calm F3
		 * segment of Film Pusaka). While it runs, {@code CinematicDirector.stageBinding()} returns it and the stage covers
		 * the world; it is only ever left under a mask (32.1.5).
		 */
		public dev.kultivator.client.epic.kosmik.Kosmik3D.@Nullable Binding stage;

		Shot(Type type, float seconds) {
			this.type = type;
			this.ticks = Math.max(10, Math.round(seconds * 20.0F));
		}

		/** Adds a Dutch roll (≤ 8° × damageTiltStrength, never with an orbit). */
		public Shot dutch(float degrees) {
			if (type != Type.ORBIT && type != Type.KILLCAM && !result) {
				this.roll = Mth.clamp(degrees, -8.0F, 8.0F);
			}
			return this;
		}

		/** Looks at a world point instead of the subject's eye. */
		public Shot lookAt(Vec3 target) {
			this.look = target;
			return this;
		}

		public Shot asResult() {
			this.result = true;
			this.roll = 0.0F;
			return this;
		}

		public Shot asDanger() {
			this.danger = true;
			this.ticks = Math.min(ticks, 30);
			return this;
		}

		public Shot asCeremony() {
			this.ceremony = true;
			return this;
		}

		/** Marks a masked cut (28.3.2): no enter blend while the cloud wipe or an impact frame hides the jump. */
		public Shot cut() {
			this.cut = true;
			return this;
		}

		/** Shows stage {@code binding} while this shot runs (32.1). */
		public Shot stage(dev.kultivator.client.epic.kosmik.Kosmik3D.@Nullable Binding binding) {
			this.stage = binding;
			return this;
		}

		/** Holds the last pose {@code ticks} more (≤ 20) once the path is done. */
		public Shot hold(int ticks) {
			this.hold = Math.max(0, Math.min(20, ticks));
			return this;
		}

		/** The camera position and look target at progress {@code t} (0..1, eased by the caller). */
		void evaluate(float t, Vec3 feet, Vec3 eye, float yaw0, float[] out) {
			double yaw = Math.toRadians(yaw0);
			double fx = -Math.sin(yaw);
			double fz = Math.cos(yaw);
			double rx = -Math.cos(yaw);
			double rz = -Math.sin(yaw);
			double px;
			double py;
			double pz;
			Vec3 target = look != null ? look : eye;
			float pitch = Float.NaN;
			float yawOut = Float.NaN;
			switch (type) {
				case CRANE_UP, CRANE_DOWN -> {
					float tt = type == Type.CRANE_DOWN ? 1.0F - t : t;
					float r = Mth.lerp(tt, r0, r1);
					float h = Math.min(CRANE_UP_LIMIT, Mth.lerp(tt, h0, h1));
					px = feet.x - fx * r + rx * side;
					py = feet.y + h;
					pz = feet.z - fz * r + rz * side;
					if (explicitPitch) {
						pitch = Mth.lerp(tt, p0, p1);
						yawOut = yaw0;
					}
				}
				case ORBIT, KILLCAM -> {
					Vec3 center = world != null ? world : feet;
					double angle = yaw + Math.PI + Math.toRadians(sweep * t);
					px = center.x - Math.sin(angle) * r0;
					py = center.y + h0;
					pz = center.z + Math.cos(angle) * r0;
					if (world != null && look == null) {
						target = world.add(0.0D, 1.2D, 0.0D);
					}
					if (explicitPitch) {
						pitch = p0;
					}
				}
				case PUSH -> {
					float r = Mth.lerp(t, r0, r1);
					px = feet.x + fx * r + rx * side;
					py = feet.y + h0;
					pz = feet.z + fz * r + rz * side;
					if (explicitPitch) {
						pitch = Mth.lerp(t, p0, p1);
					}
				}
				case PULLBACK -> {
					float r = Mth.lerp(t, r0, r1);
					float h = Math.min(CRANE_UP_LIMIT, Mth.lerp(t, h0, h1));
					// In front, 30° to the side, revealing the scene behind the subject.
					double a = yaw + Math.toRadians(30.0D);
					px = feet.x - Math.sin(a) * r;
					py = feet.y + h;
					pz = feet.z + Math.cos(a) * r;
				}
				case SKYPAN -> {
					px = eye.x - fx * 0.4D;
					py = eye.y + 0.05D;
					pz = eye.z - fz * 0.4D;
					pitch = Mth.lerp(t, p0, p1);
					yawOut = yaw0 + yawSweep * t;
				}
				case LOWHERO -> {
					float r = Mth.lerp(t, r0, r1);
					px = feet.x + fx * r;
					py = feet.y + h0;
					pz = feet.z + fz * r;
					if (explicitPitch) {
						pitch = p0;
						yawOut = yaw0 + 180.0F;
					}
				}
				case OTS -> {
					px = eye.x - fx * 1.6D + rx * 0.6D;
					py = eye.y + 0.3D;
					pz = eye.z - fz * 1.6D + rz * 0.6D;
				}
				case TWO_SHOT -> {
					Vec3 b = second != null ? second : feet.add(fx * 6.0D, 0.0D, fz * 6.0D);
					Vec3 mid = feet.lerp(b, 0.5D);
					double dx = b.x - feet.x;
					double dz = b.z - feet.z;
					double len = Math.max(0.01D, Math.sqrt(dx * dx + dz * dz));
					double nx = -dz / len;
					double nz = dx / len;
					float d = Mth.lerp(t, r0, r1);
					px = mid.x + nx * d;
					py = mid.y + h0;
					pz = mid.z + nz * d;
					target = mid.add(0.0D, 1.2D, 0.0D);
				}
				case STATIC -> {
					Vec3 center = world != null ? world : feet;
					float r = Mth.lerp(t, r0, r1);
					double a = yaw + Math.toRadians(90.0D + side);
					px = center.x - Math.sin(a) * r;
					py = center.y + h0;
					pz = center.z + Math.cos(a) * r;
				}
				case SKY_RIDE, PUSAKA_FILM -> {
					Vec3 a = world != null ? world : feet;
					Vec3 b = second != null ? second : a;
					px = a.x + (b.x - a.x) * t;
					py = a.y + (b.y - a.y) * t;
					pz = a.z + (b.z - a.z) * t;
					Vec3 l0 = look != null ? look : eye;
					Vec3 l1 = lookEnd != null ? lookEnd : l0;
					target = l0.lerp(l1, t);
				}
				case FLYOVER -> {
					Vec3[] points = path;
					if (points == null || points.length < 2) {
						px = feet.x;
						py = feet.y + 8.0D;
						pz = feet.z;
					} else {
						Vec3 p = catmull(points, t);
						px = p.x;
						py = p.y;
						pz = p.z;
						if (look == null) {
							Vec3 ahead = catmull(points, Math.min(1.0F, t + 0.08F));
							target = ahead.add(ahead.subtract(p).scale(3.0D)).add(0.0D, -2.0D, 0.0D);
						}
					}
				}
				default -> {
					px = feet.x;
					py = feet.y + 2.0D;
					pz = feet.z;
				}
			}
			out[0] = (float) (px - feet.x);
			out[1] = (float) (py - feet.y);
			out[2] = (float) (pz - feet.z);
			if (shrink < 1.0F) {
				out[0] = s0x + (out[0] - s0x) * shrink;
				out[1] = s0y + (out[1] - s0y) * shrink;
				out[2] = s0z + (out[2] - s0z) * shrink;
				px = feet.x + out[0];
				py = feet.y + out[1];
				pz = feet.z + out[2];
			}
			double dx = target.x - px;
			double dy = target.y - py;
			double dz = target.z - pz;
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			out[3] = Float.isNaN(yawOut) ? (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F : yawOut;
			out[4] = Float.isNaN(pitch) ? (float) (-(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG)) : pitch;
			out[4] = Mth.clamp(out[4], -85.0F, 85.0F);
		}

		/** Stretches the duration for the speed caps; shortens the path when that would break the shot's maximum. */
		public Shot validate(Vec3 feet, Vec3 eye, float yaw0, int maxTicks) {
			int cap = Math.min(maxTicks, type.max);
			float[] a = new float[5];
			float[] b = new float[5];
			shrink = 1.0F;
			evaluate(0.0F, feet, eye, yaw0, a);
			s0x = a[0];
			s0y = a[1];
			s0z = a[2];
			double distance = 0.0D;
			double turn = 0.0D;
			for (int i = 1; i <= 16; i++) {
				evaluate(i / 16.0F, feet, eye, yaw0, b);
				double dx = b[0] - a[0];
				double dy = b[1] - a[1];
				double dz = b[2] - a[2];
				distance += Math.sqrt(dx * dx + dy * dy + dz * dz);
				turn += Math.abs(Mth.wrapDegrees(b[3] - a[3])) + Math.abs(b[4] - a[4]);
				System.arraycopy(b, 0, a, 0, 5);
			}
			int need = (int) Math.ceil(Math.max(distance / MAX_SPEED, turn / MAX_TURN) * 20.0D);
			ticks = Math.max(ticks, need);
			if (ticks > cap) {
				ticks = cap;
				double allowed = cap / 20.0D * MAX_SPEED;
				if (distance > allowed && distance > 0.01D) {
					shrink = (float) (allowed / distance);
				}
			}
			return this;
		}

		private static Vec3 catmull(Vec3[] p, float t) {
			int segments = p.length - 1;
			float scaled = Mth.clamp(t, 0.0F, 1.0F) * segments;
			int i = Math.min(segments - 1, (int) scaled);
			float u = scaled - i;
			Vec3 p0 = p[Math.max(0, i - 1)];
			Vec3 p1 = p[i];
			Vec3 p2 = p[i + 1];
			Vec3 p3 = p[Math.min(segments, i + 2)];
			double u2 = u * u;
			double u3 = u2 * u;
			return new Vec3(
					0.5D * (2 * p1.x + (-p0.x + p2.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * u3),
					0.5D * (2 * p1.y + (-p0.y + p2.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * u3),
					0.5D * (2 * p1.z + (-p0.z + p2.z) * u + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * u2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * u3));
		}
	}

	// ================================================================ factories (23.7.2 defaults)

	/** Rises from (back r0, up h0) to (back r1, up h1), pitch p0 → p1 (explicit), {@code seconds} long. */
	public static Shot craneUp(float r0, float h0, float r1, float h1, float p0, float p1, float seconds) {
		Shot shot = new Shot(Type.CRANE_UP, seconds);
		shot.r0 = r0;
		shot.h0 = h0;
		shot.r1 = r1;
		shot.h1 = h1;
		shot.p0 = p0;
		shot.p1 = p1;
		shot.explicitPitch = true;
		return shot;
	}

	/** A crane that keeps looking at the subject (or a look target) instead of an explicit pitch. */
	public static Shot craneLook(float r0, float h0, float r1, float h1, float seconds) {
		Shot shot = new Shot(Type.CRANE_UP, seconds);
		shot.r0 = r0;
		shot.h0 = h0;
		shot.r1 = r1;
		shot.h1 = h1;
		return shot;
	}

	public static Shot orbit(float r, float h, float sweepDegrees, float seconds) {
		Shot shot = new Shot(Type.ORBIT, seconds);
		shot.r0 = r;
		shot.h0 = h;
		shot.sweep = sweepDegrees;
		return shot;
	}

	public static Shot push(float r0, float r1, float h, float seconds) {
		Shot shot = new Shot(Type.PUSH, seconds);
		shot.r0 = r0;
		shot.r1 = r1;
		shot.h0 = h;
		return shot;
	}

	public static Shot pullback(float r0, float r1, float h0, float h1, float seconds) {
		Shot shot = new Shot(Type.PULLBACK, seconds);
		shot.r0 = r0;
		shot.r1 = r1;
		shot.h0 = h0;
		shot.h1 = h1;
		return shot;
	}

	public static Shot skypan(float p0, float p1, float yawSweep, float seconds) {
		Shot shot = new Shot(Type.SKYPAN, seconds);
		shot.p0 = p0;
		shot.p1 = p1;
		shot.yawSweep = Mth.clamp(yawSweep, -30.0F, 30.0F);
		return shot;
	}

	/** In front at r (≥ 4), height h, looking up {@code up} degrees, pushing in by ≤ 1.5 b. */
	public static Shot lowHero(float r, float h, float up, float push, float seconds) {
		Shot shot = new Shot(Type.LOWHERO, seconds);
		shot.r0 = Math.max(4.0F, r);
		shot.r1 = shot.r0 - Mth.clamp(push, 0.0F, 1.5F);
		shot.h0 = Mth.clamp(h, 0.3F, 0.5F);
		shot.p0 = -Mth.clamp(up, 20.0F, 30.0F);
		return shot;
	}

	/** Over the shoulder toward {@code target}. */
	public static Shot ots(Vec3 target, float seconds) {
		Shot shot = new Shot(Type.OTS, seconds);
		shot.look = target;
		return shot;
	}

	public static Shot twoShot(Vec3 second, float d0, float d1, float h, float seconds) {
		Shot shot = new Shot(Type.TWO_SHOT, seconds);
		shot.second = second;
		shot.r0 = d0;
		shot.r1 = d1;
		shot.h0 = h;
		return shot;
	}

	/** A locked framing {@code r0} to the side (pushing to r1, ≤ 1 b), height h. */
	public static Shot still(float r0, float r1, float h, float seconds) {
		Shot shot = new Shot(Type.STATIC, seconds);
		shot.r0 = r0;
		shot.r1 = Math.max(r0 - 1.0F, Math.min(r0, r1));
		shot.h0 = h;
		return shot;
	}

	/** A static framing of a world point from a given offset direction (degrees from the subject's side). */
	public static Shot stillAt(Vec3 center, float r, float h, float sideDegrees, float seconds) {
		Shot shot = still(r, r, h, seconds);
		shot.world = center;
		shot.side = sideDegrees;
		return shot;
	}

	/** A Catmull-Rom flight over ≤ 6 points (ceremonies only). */
	public static Shot flyover(Vec3[] points, float seconds) {
		Shot shot = new Shot(Type.FLYOVER, seconds);
		shot.path = points.length > 6 ? java.util.Arrays.copyOf(points, 6) : points;
		shot.ceremony = true;
		return shot;
	}

	/**
	 * Phase 10 (DESIGN 28.3.2): one segment of Perjalanan Langit, flying in a straight line from {@code from} to
	 * {@code to} (world points) while the look moves from {@code look0} to {@code look1}; {@code ticks} long before the
	 * speed caps (validation may stretch it, then shrink the path). A ceremony (48 b reach).
	 */
	public static Shot skyRide(Vec3 from, Vec3 to, Vec3 look0, Vec3 look1, int ticks) {
		Shot shot = new Shot(Type.SKY_RIDE, Math.max(10, ticks) / 20.0F);
		shot.world = from;
		shot.second = to;
		shot.look = look0;
		shot.lookEnd = look1;
		shot.ceremony = true;
		return shot;
	}

	/**
	 * Phase 10b (DESIGN 29.4): one segment of Film Pusaka, the same straight world-anchored flight as {@link #skyRide}
	 * (from {@code from} to {@code to}, the look from {@code look0} to {@code look1}) but of type {@link Type#PUSAKA_FILM}:
	 * at most 80 ticks, validated against the speed caps like every shot. A ceremony (48 b reach).
	 */
	public static Shot pusakaFilm(Vec3 from, Vec3 to, Vec3 look0, Vec3 look1, int ticks) {
		Shot shot = new Shot(Type.PUSAKA_FILM, Math.max(10, Math.min(Type.PUSAKA_FILM.max, ticks)) / 20.0F);
		shot.world = from;
		shot.second = to;
		shot.look = look0;
		shot.lookEnd = look1;
		shot.ceremony = true;
		return shot;
	}

	/**
	 * Phase 30 (DESIGN 30.2.3): a Film Spektakuler shot of {@code path} (its validated length); only {@code FilmDirector}
	 * builds these, and only {@code CinematicDirector.shootFilm} plays them.
	 */
	public static Shot film(dev.kultivator.client.epic.film.FilmPath path) {
		Shot shot = new Shot(Type.FILM, path.ticks() / 20.0F);
		shot.ticks = Math.max(4, Math.min(Type.FILM.max, path.ticks()));
		shot.film = path;
		shot.ceremony = true;
		// Film Kosmik 3D: a stage shot carries its binding (32.4)
		shot.stage = path.binding();
		return shot;
	}

	/** An orbit around a world point (the dead boss). */
	public static Shot killcam(Vec3 center, float r, float sweepDegrees, float seconds) {
		Shot shot = new Shot(Type.KILLCAM, seconds);
		shot.world = center;
		shot.r0 = r;
		shot.h0 = 2.5F;
		shot.sweep = sweepDegrees;
		return shot;
	}

	private Shots() {
	}
}
