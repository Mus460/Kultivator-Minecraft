package dev.kultivator.client.duel.fx;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where two blades meet (DESIGN 35.4.1). The server sends a contact anchor computed from the shared blade arcs; the client
 * refines it with the blades actually drawn ({@link DuelLinks#drawn}, recorded by module A's probe layer after every
 * animation mixin, so a Better Combat swing is measured too):
 * <ol>
 * <li>both blades known: the closest points of the two segments (classic clamped segment-segment solution); closer than
 * 0.35 b → the midpoint of that pair; otherwise the point of the defender's blade nearest the attacker's tip;</li>
 * <li>a refined point more than 0.6 b from the anchor (an invisible blade, an entity outside render distance, a third-party
 * model) → the anchor;</li>
 * <li>the contact normal is the cross of the two blade directions (nearly parallel blades: the attacker's swing direction
 * × up); the attacker's tip velocity comes from the trail history ({@link DuelTrailRenderer#tipVelocity}) or, without
 * one, from the swing direction.</li>
 * </ol>
 * During a blade lock the contact is solved again every frame and slides along the blades with the pressure
 * ({@link #bindContact}): steel grinding on steel.
 */
public final class ContactSolver {

	/** The solved contact: point, unit normal, the attacker's tip velocity (b/tick), both blade directions. */
	public record Contact(Vec3 pos, Vec3 normal, Vec3 velocity, Vec3 dirA, Vec3 dirB, boolean refined) {
	}

	public static final double MEET = 0.35D;
	public static final double TRUST = 0.6D;

	/** Solves the contact of a clash result. */
	public static Contact solve(DuelFx.ClashEvent e, @Nullable Entity attacker, @Nullable Entity defender, @Nullable DuelFx.SwingView swing,
			float partial) {
		Vec3 anchor = e.anchor();
		DuelLinks.Blade a = blade(attacker, swing, partial);
		DuelLinks.Blade b = blade(defender, defender == null ? null : DuelFx.latestSwing(defender.getId()), partial);
		Vec3 point = anchor;
		boolean refined = false;
		if (a != null && b != null) {
			Vec3[] pair = closest(a, b);
			Vec3 candidate = pair[0].distanceTo(pair[1]) < MEET ? pair[0].add(pair[1]).scale(0.5D) : nearest(b, a.tip());
			if (candidate.distanceTo(anchor) <= TRUST) {
				point = candidate;
				refined = true;
			}
		} else if (b != null) {
			Vec3 candidate = nearest(b, anchor);
			if (candidate.distanceTo(anchor) <= TRUST) {
				point = candidate;
				refined = true;
			}
		} else if (a != null) {
			Vec3 candidate = nearest(a, anchor);
			if (candidate.distanceTo(anchor) <= TRUST) {
				point = candidate;
				refined = true;
			}
		}
		Vec3 swingDir = swingVector(attacker, defender, swing != null ? swing.dir : e.dirA(), anchor);
		Vec3 dirA = a != null ? a.dir() : swingDir.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
		Vec3 dirB = b != null ? b.dir() : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 normal = dirA.cross(dirB);
		if (normal.lengthSqr() < 0.04D) {
			normal = swingDir.cross(new Vec3(0.0D, 1.0D, 0.0D));
		}
		if (normal.lengthSqr() < 1.0E-6D) {
			normal = new Vec3(1.0D, 0.0D, 0.0D);
		}
		normal = normal.normalize();
		Vec3 velocity = attacker == null ? null : DuelTrailRenderer.tipVelocity(attacker.getId());
		if (velocity == null || velocity.lengthSqr() < 0.0025D) {
			velocity = swingDir.scale(0.8D);
		}
		return new Contact(point, normal, velocity, dirA, dirB, refined);
	}

	/**
	 * The contact of a running blade lock this frame: the closest points of the two blades (or the midpoint of the two
	 * fighters at chest height), slid along A's blade by the pressure and a slow grind.
	 */
	public static @Nullable Vec3 bindContact(DuelFx.BindView bind, float partial, double time) {
		Entity a = DuelFx.entity(bind.aId);
		Entity b = DuelFx.entity(bind.bId);
		if (a == null || b == null) {
			return null;
		}
		DuelLinks.Blade ba = DuelLinks.drawn(bind.aId, partial);
		DuelLinks.Blade bb = DuelLinks.drawn(bind.bId, partial);
		float slide = Mth.clamp(0.55F + bind.pressure / 100.0F * 0.3F + 0.08F * Mth.sin((float) time * 1.7F), 0.15F, 0.95F);
		if (ba != null && bb != null) {
			Vec3[] pair = closest(ba, bb);
			if (pair[0].distanceTo(pair[1]) < 0.8D) {
				// the lock point grinds along A's blade with the pressure
				Vec3 along = ba.at(slide);
				return pair[0].add(pair[1]).scale(0.5D).lerp(along, 0.35D);
			}
		}
		Vec3 pa = a.getPosition(partial);
		Vec3 pb = b.getPosition(partial);
		Vec3 mid = pa.lerp(pb, 0.5D + bind.pressure / 100.0D * 0.15D);
		return mid.add(0.0D, Math.min(a.getBbHeight(), b.getBbHeight()) * 0.72D, 0.0D);
	}

	/** The drawn blade of {@code entity}, else the pose-table blade of its swing near the impact, else null. */
	static @Nullable DuelLinks.Blade blade(@Nullable Entity entity, @Nullable DuelFx.SwingView swing, float partial) {
		if (entity == null) {
			return null;
		}
		DuelLinks.Blade drawn = DuelLinks.drawn(entity.getId(), partial);
		if (drawn != null) {
			return drawn;
		}
		if (swing == null) {
			return null;
		}
		double now = DuelLinks.serverTick(partial);
		float t01 = swing.charging() ? 0.0F : (float) ((now - (swing.impact() - 2.0D)) / 3.0D);
		return DuelLinks.arc(entity, swing.weapon, swing.kind, swing.dir, t01);
	}

	/**
	 * The world direction a swing of code {@code dir} moves the blade, seen from the attacker: "from the right" moves to the
	 * attacker's left, "from above" moves down, a thrust moves forward.
	 */
	static Vec3 swingVector(@Nullable Entity attacker, @Nullable Entity defender, int dir, Vec3 fallbackAt) {
		Vec3 forward;
		if (attacker != null && defender != null) {
			forward = defender.position().subtract(attacker.position());
			forward = new Vec3(forward.x, 0.0D, forward.z);
		} else if (attacker != null) {
			float yaw = attacker instanceof LivingEntity living ? living.yBodyRot : attacker.getYRot();
			double rad = Math.toRadians(yaw);
			forward = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
		} else {
			forward = new Vec3(0.0D, 0.0D, 1.0D);
		}
		if (forward.lengthSqr() < 1.0E-6D) {
			forward = new Vec3(0.0D, 0.0D, 1.0D);
		}
		forward = forward.normalize();
		// the attacker's right (Minecraft yaw: right = forward rotated -90° about y)
		Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
		Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 v = switch (dir) {
			case DuelCodes.DIR_ATAS -> up.scale(-1.0D);
			case DuelCodes.DIR_ATAS_KANAN -> up.scale(-0.7D).add(right.scale(-0.7D));
			case DuelCodes.DIR_KANAN -> right.scale(-1.0D);
			case DuelCodes.DIR_BAWAH_KANAN -> up.scale(0.7D).add(right.scale(-0.7D));
			case DuelCodes.DIR_BAWAH -> up;
			case DuelCodes.DIR_BAWAH_KIRI -> up.scale(0.7D).add(right.scale(0.7D));
			case DuelCodes.DIR_KIRI -> right;
			case DuelCodes.DIR_ATAS_KIRI -> up.scale(-0.7D).add(right.scale(0.7D));
			case DuelCodes.DIR_PUTAR -> right.scale(-1.0D);
			default -> forward;
		};
		return v.normalize();
	}

	/** The closest pair of points of two blades (clamped segment-segment). */
	static Vec3[] closest(DuelLinks.Blade a, DuelLinks.Blade b) {
		Vec3 d1 = a.tip().subtract(a.grip());
		Vec3 d2 = b.tip().subtract(b.grip());
		Vec3 r = a.grip().subtract(b.grip());
		double aa = d1.dot(d1);
		double ee = d2.dot(d2);
		double ff = d2.dot(r);
		double s;
		double t;
		double eps = 1.0E-9D;
		if (aa <= eps && ee <= eps) {
			s = 0.0D;
			t = 0.0D;
		} else if (aa <= eps) {
			s = 0.0D;
			t = clamp01(ff / ee);
		} else {
			double c = d1.dot(r);
			if (ee <= eps) {
				t = 0.0D;
				s = clamp01(-c / aa);
			} else {
				double bb = d1.dot(d2);
				double denom = aa * ee - bb * bb;
				s = denom > eps ? clamp01((bb * ff - c * ee) / denom) : 0.0D;
				t = (bb * s + ff) / ee;
				if (t < 0.0D) {
					t = 0.0D;
					s = clamp01(-c / aa);
				} else if (t > 1.0D) {
					t = 1.0D;
					s = clamp01((bb - c) / aa);
				}
			}
		}
		return new Vec3[]{a.at(s), b.at(t)};
	}

	/** The point of a blade nearest {@code point}. */
	static Vec3 nearest(DuelLinks.Blade blade, Vec3 point) {
		Vec3 d = blade.tip().subtract(blade.grip());
		double len2 = d.dot(d);
		if (len2 < 1.0E-9D) {
			return blade.grip();
		}
		return blade.at(clamp01(point.subtract(blade.grip()).dot(d) / len2));
	}

	private static double clamp01(double v) {
		return v < 0.0D ? 0.0D : v > 1.0D ? 1.0D : v;
	}

	private ContactSolver() {
	}
}
