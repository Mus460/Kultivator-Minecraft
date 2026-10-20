package dev.kultivator.client.aura.realm;

import dev.kultivator.client.aura.AuraColors;
import dev.kultivator.client.aura.AuraDraw;
import dev.kultivator.client.aura.AuraHum;
import dev.kultivator.client.aura.AuraParticles;
import dev.kultivator.client.aura.AuraRealmDriver;
import dev.kultivator.client.aura.AuraRig;
import dev.kultivator.client.aura.AuraRng;
import dev.kultivator.client.aura.AuraSubject;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Qi Refining (Pemurnian Qi), DESIGN 31.4.2: the qi of heaven and earth drawn into the body. Motes in the root's colour
 * flow in along turning spiral arms from 1.6 blocks to the dantian (2 / 3 / 4 / 4 arms by stage), pale jade-white mist
 * wraps the seated body, and above the head a flat qi vortex turns 2°/t (r 0.5 / 0.8 / 1.1 / 1.4), as if the body had
 * become a sink devouring the energy around it. Tengah adds a mist ribbon orbiting the waist, Akhir a second ribbon and
 * the double mist ring on the ground, Puncak a counter-turning inner swirl and a thin column of light from the vortex
 * down to the crown. No props: a light realm leaves the display budget to others.
 * <p>
 * Sumber: Battle Through the Heavens, ch. 27 and 159; the vortex above the head is game design.
 */
public final class QiRefiningAura implements AuraRealmDriver {

	static final Identifier VORTEX = AuraDraw.tex("qi_vortex");
	static final Identifier MIST_RING = AuraDraw.tex("decal_mist_ring");
	private static final float[] VORTEX_R = {0.5F, 0.8F, 1.1F, 1.4F};
	private static final int[] ARMS = {2, 3, 4, 4};
	/** How fast the spiral arms turn (rad/t) and how much they wind per block. */
	private static final double SPIN = 0.06D;
	private static final double TWIST = 2.2D;
	private static final AuraHum.Spec HUM = AuraHum.Spec.loop(SoundEvents.CONDUIT_AMBIENT, 0.05F, 1.6F);

	@Override
	public void start(AuraSubject s, AuraRig rig) {
	}

	@Override
	public void tick(AuraSubject s, AuraRig rig) {
		AuraColors.Palette p = s.colors();
		int id = s.entityId();
		long t = rig.clock();
		int stage = s.stage();
		int arms = ARMS[stage];
		Vec3 dantian = s.dantian();
		double outer = 1.6D * s.scale();
		// spiral arms: each tick one mote per arm at a cycling radius, aimed further in along its own (turning) arm
		for (int k = 0; k < arms; k++) {
			double cycle = (rig.age() * 0.37D + k * 0.29D) % 1.0D;
			double r = outer * (0.25D + 0.75D * (1.0D - cycle));
			double angle = armAngle(t, k, arms, r, s.scale());
			double lift = AuraRng.signed(id, t, 20 + k) * 0.22D * s.scale();
			Vec3 from = dantian.add(Math.cos(angle) * r, lift, Math.sin(angle) * r);
			int life = 24 + (int) (AuraRng.of(id, t, 30 + k) * 7.0F);
			double r2 = Math.max(0.0D, r - 0.045D * life * s.scale());
			Vec3 to;
			if (r2 < 0.3D * s.scale()) {
				to = dantian;
			} else {
				double a2 = armAngle(t + life, k, arms, r2, s.scale());
				to = dantian.add(Math.cos(a2) * r2, lift * 0.3D, Math.sin(a2) * r2);
			}
			int rgb = AuraColors.mix(p.inflow(), 0xFFFFFF, 0.15F + 0.25F * AuraRng.of(id, t, 40 + k));
			if (!AuraParticles.mote(s, from, AuraParticles.toward(from, to, life, AuraParticles.MOTE_FRICTION), rgb, 0.085F * (float) s.scale(),
					life)) {
				break;
			}
		}
		// the airflow wrapping the body: a wisp of glowing mist now and then
		if (rig.age() % 5 == 0) {
			double angle = AuraRng.of(id, t, 50) * Math.PI * 2.0D;
			double radius = (0.42D + 0.2D * AuraRng.of(id, t, 51)) * s.scale();
			Vec3 at = s.feet().add(Math.cos(angle) * radius, (0.15D + AuraRng.of(id, t, 52) * 0.75D) * s.height(), Math.sin(angle) * radius);
			Vec3 swirl = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)).scale(0.012D * s.scale()).add(0.0D, 0.008D, 0.0D);
			AuraParticles.mist(s, at, swirl, AuraColors.lighten(p.glow(), 0.25F), 0.32F * (float) s.scale(), 40);
		}
	}

	/** The angle of arm {@code k} at radius {@code r} at time {@code t}: turning with time, winding inward. */
	private static double armAngle(long t, int k, int arms, double r, double scale) {
		return Math.floorMod(t, 6283L) * SPIN + k * Math.PI * 2.0D / arms + (1.6D * scale - r) / scale * TWIST;
	}

	@Override
	public void render(AuraSubject s, AuraRig rig, AuraDraw draw, float partial) {
		AuraColors.Palette p = s.colors();
		float t = rig.time();
		int stage = s.stage();
		Vec3 crown = s.crown();
		float vortexR = s.r(rig.ease("vortex", VORTEX_R[stage], 20));
		Vec3 eye = crown.add(0.0D, s.r(0.38F), 0.0D);
		int swirl = p.ring();

		// the double mist ring on the ground (Akhir and up), r 1.0 → 1.6 by stage
		float decal = rig.easeFrom("decal", 0.0F, stage >= 2 ? 1.0F : 0.0F, 20);
		float decalR = s.r(rig.ease("decalR", 1.0F + 0.2F * stage, 20));
		draw.decal(decalR, t * 0.4F, MIST_RING, swirl, 0.5F * decal);

		// the body wrapped in pale jade-white light, qi gathering in the dantian
		if (draw.large()) {
			draw.glow(s.chest(), s.r(0.6F) * (0.9F + 0.15F * s.stageGrow()), p.glow(), 0.15F);
		}
		draw.glowFront(s.dantian(), s.r(0.2F), AuraColors.mix(p.inflow(), 0xFFFFFF, 0.5F), 0.32F + 0.1F * AuraKit.breath(t, 60.0F), 0.3F);

		// the vortex above the head (the identity of the realm)
		draw.flat(eye, vortexR, t * 2.0F, VORTEX, swirl, 0.6F);
		draw.glow(eye, vortexR * 0.32F, 0xFFFFFF, 0.35F);
		if (s.essentials()) {
			return;
		}
		// a second, looser layer turning slower gives the cloud some depth
		draw.flat(eye.add(0.0D, s.r(0.05F), 0.0D), vortexR * 1.15F, t * 1.3F + 40.0F, VORTEX, AuraColors.lighten(swirl, 0.3F), 0.22F);

		// Puncak: the counter-turning inner swirl and the column of light down to the crown
		float peak = rig.easeFrom("peak", 0.0F, stage >= 3 ? 1.0F : 0.0F, 20);
		if (peak > 0.01F) {
			draw.flat(eye.add(0.0D, s.r(0.09F), 0.0D), vortexR * 0.45F, -t * 3.0F, VORTEX, 0xFFFFFF, 0.55F * peak);
			draw.beam(eye, crown, s.r(0.12F), AuraColors.lighten(swirl, 0.4F), 0.25F * peak);
		}

		// mist ribbons orbiting the waist (Tengah: one, Akhir and up: two)
		for (int i = 0; i < 2; i++) {
			float shown = rig.easeFrom("ribbon" + i, 0.0F, stage >= 1 + i ? 1.0F : 0.0F, 20);
			if (shown > 0.01F) {
				draw.ribbon(orbit(s, t, i), s.r(0.085F), s.r(0.015F), AuraColors.lighten(swirl, 0.55F), swirl, 0.45F * shown);
			}
		}
	}

	/** One mist ribbon: an arc of 220° round the waist (r 0.7), the second one higher and turning the other way. */
	private static List<Vec3> orbit(AuraSubject s, float t, int index) {
		List<Vec3> points = new ArrayList<>(15);
		Vec3 center = s.dantian().add(0.0D, s.r(index == 0 ? 0.12F : 0.42F), 0.0D);
		double dir = index == 0 ? 1.0D : -1.0D;
		double head = dir * t * 0.07D + index * Math.PI;
		double arc = Math.toRadians(220.0D);
		for (int i = 0; i < 15; i++) {
			double f = i / 14.0D;
			double angle = head - dir * f * arc;
			double radius = s.r(0.7F) * (1.0D + 0.06D * Math.sin(angle * 3.0D + t * 0.03D));
			double y = Math.sin(angle * 2.0D + t * 0.05D) * 0.08D * s.scale();
			points.add(center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius));
		}
		return points;
	}

	@Override
	public void stop(AuraSubject s, AuraRig rig) {
		rig.endProps(12);
	}

	@Override
	public AuraHum.Spec hum(AuraSubject s) {
		return HUM;
	}

	@Override
	public float reach(AuraSubject s) {
		return 1.6F;
	}
}
