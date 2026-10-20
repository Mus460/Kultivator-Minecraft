package dev.kultivator.client.aura.realm;

import dev.kultivator.client.aura.AuraColors;
import dev.kultivator.client.aura.AuraDraw;
import dev.kultivator.client.aura.AuraHum;
import dev.kultivator.client.aura.AuraParticles;
import dev.kultivator.client.aura.AuraRealmDriver;
import dev.kultivator.client.aura.AuraRig;
import dev.kultivator.client.aura.AuraRng;
import dev.kultivator.client.aura.AuraSubject;
import net.minecraft.world.phys.Vec3;

/**
 * Mortal (Manusia Fana), DESIGN 31.4.1: a mortal practising a breathing art. A faint warm glow in the belly swells and
 * dims with a slow 100-tick breath (alpha 0.10 to 0.22, never a size change) and every eighth tick one pale mote drifts in
 * from 1.2 blocks away to the dantian, in the root's colour once the root is known. No decal, no props, no sound: the
 * quietest aura, so that the first real realm feels like a step.
 * <p>
 * Sumber: A Record of a Mortal's Journey · 长春功 Seni Musim Semi Abadi (the breath and the warmth are game design).
 */
public final class MortalAura implements AuraRealmDriver {

	private static final int WARM = 0xF2E8D8;

	@Override
	public void start(AuraSubject s, AuraRig rig) {
	}

	@Override
	public void tick(AuraSubject s, AuraRig rig) {
		if (rig.age() % 8 != 0) {
			return;
		}
		long t = rig.clock();
		int id = s.entityId();
		double angle = AuraRng.of(id, t, 1) * Math.PI * 2.0D;
		double lift = (AuraRng.of(id, t, 2) - 0.3D) * 0.7D * s.scale();
		Vec3 dantian = s.dantian();
		Vec3 from = dantian.add(Math.cos(angle) * s.r(1.2F), lift, Math.sin(angle) * s.r(1.2F));
		int rgb = AuraColors.lighten(s.colors().inflow(), 0.25F);
		AuraParticles.mote(s, from, AuraParticles.toward(from, dantian, 30, AuraParticles.MOTE_FRICTION), rgb, 0.07F * (float) s.scale(), 30);
	}

	@Override
	public void render(AuraSubject s, AuraRig rig, AuraDraw draw, float partial) {
		float breath = AuraKit.breath(rig.time(), 100.0F);
		float alpha = 0.10F + 0.12F * breath;
		Vec3 dantian = s.dantian();
		draw.glowFront(dantian, s.r(0.25F), WARM, alpha, 0.3F);
		draw.glowFront(dantian, s.r(0.09F), 0xFFF8EE, Math.min(1.0F, alpha * 1.8F), 0.32F);
	}

	@Override
	public void stop(AuraSubject s, AuraRig rig) {
		rig.endProps(12);
	}

	@Override
	public AuraHum.Spec hum(AuraSubject s) {
		return AuraHum.Spec.NONE;
	}

	@Override
	public float reach(AuraSubject s) {
		return 0.6F;
	}
}
