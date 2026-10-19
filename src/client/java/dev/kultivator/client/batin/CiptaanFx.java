package dev.kultivator.client.batin;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.data.Dao;
import dev.kultivator.network.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws a self-created art in its two Dao colours (DESIGN 26.7.3; {@code BatinFxIds.OWN_ART}): the cut's double beam, the
 * burst's twin rings, the rain's falling motes, the wave's travelling wall, the domain's ten pulsing rings and the seal's
 * chains, on top of the spec's own layers (rune circle, shock ring, SHAKE). Particles only; at most {@link #MAX} running.
 */
public final class CiptaanFx {

	private static final int MAX = 8;
	private static final int GOLD = 0xFFD54A;
	private static final List<Anim> ANIMS = new ArrayList<>();

	private static final class Anim {
		final int form;
		final Vec3 point;
		final Vec3 eye;
		final int colorA;
		final int colorB;
		final float size;
		final int life;
		int age;

		Anim(int form, Vec3 point, Vec3 eye, int colorA, int colorB, float size, int life) {
			this.form = form;
			this.point = point;
			this.eye = eye;
			this.colorA = colorA;
			this.colorB = colorB;
			this.size = size;
			this.life = life;
		}
	}

	/** The Dao colour of a packed {@code (ordinal + 1)} nibble, gold for none. */
	static int daoColor(int packed) {
		Dao dao = Dao.byOrdinal(packed - 1);
		return dao == null ? GOLD : dao.color();
	}

	static void play(Payloads.Fx fx) {
		int form = fx.b() & 0xF;
		int colorA = daoColor((fx.b() >> 4) & 0xF);
		int colorB = daoColor((fx.b() >> 8) & 0xF);
		Vec3 point = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 eye = new Vec3(fx.tx(), fx.ty(), fx.tz());
		float size = Math.max(1.0F, fx.f());
		switch (form) {
			case 0 -> {
				// Tebasan: a beam of both colours from the eye.
				CommonFx.beam(eye, point, colorA, 0.9F);
				CommonFx.beam(eye.add(0.0D, 0.25D, 0.0D), point.add(0.0D, 0.25D, 0.0D), colorB, 0.4F);
			}
			case 1 -> {
				CommonFx.burst(point, colorA, 80, size);
				CommonFx.ring(point, colorA, size, 72);
				CommonFx.ring(point.add(0.0D, 0.6D, 0.0D), colorB, size * 0.7F, 56);
			}
			case 5 -> {
				CommonFx.ring(point.add(0.0D, -0.8D, 0.0D), colorA, 1.6F, 24);
				for (int i = 0; i < 4; i++) {
					double angle = Math.PI * 0.5D * i;
					Vec3 from = point.add(Math.cos(angle) * 2.5D, 2.5D, Math.sin(angle) * 2.5D);
					CommonFx.beam(from, point, i % 2 == 0 ? colorA : colorB, 0.15F);
				}
			}
			default -> {
			}
		}
		int life = switch (form) {
			case 2 -> 60;
			case 3 -> 16;
			case 4 -> 160;
			case 5 -> 60;
			default -> 0;
		};
		if (life > 0 && ANIMS.size() < MAX) {
			ANIMS.add(new Anim(form, point, eye, colorA, colorB, size, life));
		}
	}

	static void tick() {
		if (ANIMS.isEmpty()) {
			return;
		}
		if (CommonFx.level() == null) {
			ANIMS.clear();
			return;
		}
		RandomSource random = CommonFx.random();
		for (Iterator<Anim> it = ANIMS.iterator(); it.hasNext(); ) {
			Anim anim = it.next();
			anim.age++;
			switch (anim.form) {
				case 2 -> {
					// Hujan: motes falling inside the radius.
					int n = Math.max(1, EffectQuality.scale(6));
					for (int i = 0; i < n; i++) {
						double angle = random.nextDouble() * Math.PI * 2.0D;
						double r = Math.sqrt(random.nextDouble()) * anim.size;
						Vec3 at = anim.point.add(Math.cos(angle) * r, 10.0D + random.nextDouble() * 4.0D, Math.sin(angle) * r);
						CommonFx.add(CommonFx.dust(i % 2 == 0 ? anim.colorA : anim.colorB, 1.6F), at, new Vec3(0.0D, -1.2D, 0.0D), false);
					}
				}
				case 3 -> {
					// Gelombang: a wall advancing from the caster to the far end.
					Vec3 start = new Vec3(anim.eye.x, anim.point.y, anim.eye.z);
					Vec3 dir = anim.point.subtract(start);
					double length = dir.length();
					if (length > 1.0E-3D) {
						Vec3 unit = dir.scale(1.0D / length);
						Vec3 side = new Vec3(-unit.z, 0.0D, unit.x);
						Vec3 front = start.add(unit.scale(length * anim.age / (double) anim.life));
						int n = Math.max(4, EffectQuality.scale(24));
						for (int i = 0; i < n; i++) {
							double offset = (i / (double) (n - 1) - 0.5D) * 16.0D;
							Vec3 at = front.add(side.scale(offset)).add(0.0D, random.nextDouble() * 6.0D, 0.0D);
							CommonFx.add(CommonFx.dust(i % 2 == 0 ? anim.colorA : anim.colorB, 1.8F), at, Vec3.ZERO, false);
						}
					}
				}
				case 4 -> {
					// Wilayah: a ring every 16 ticks, ten pulses.
					if (anim.age % 16 == 1) {
						CommonFx.ring(anim.point, anim.colorA, anim.size, 64);
						CommonFx.ring(anim.point.add(0.0D, 0.4D, 0.0D), anim.colorB, anim.size * 0.85F, 48);
					}
					if (anim.age % 4 == 0) {
						CommonFx.spiralIn(anim.point.add(0.0D, 1.0D, 0.0D), anim.colorB, anim.size * 0.6F, 8, anim.age);
					}
				}
				case 5 -> {
					// Segel: the chains hold for the root, then the strike.
					if (anim.age % 10 == 0) {
						CommonFx.ring(anim.point.add(0.0D, -0.8D, 0.0D), anim.age % 20 == 0 ? anim.colorA : anim.colorB, 1.4F, 20);
					}
					if (anim.age == anim.life - 1) {
						CommonFx.bolt(anim.point.add(0.0D, 12.0D, 0.0D), anim.point, anim.colorA, 8);
						for (int i = 0; i < EffectQuality.scale(10); i++) {
							CommonFx.add(ParticleTypes.END_ROD, anim.point.x, anim.point.y, anim.point.z, random.nextGaussian() * 0.2D,
									random.nextGaussian() * 0.2D, random.nextGaussian() * 0.2D);
						}
					}
				}
				default -> {
				}
			}
			if (anim.age >= anim.life) {
				it.remove();
			}
		}
	}

	private CiptaanFx() {
	}
}
