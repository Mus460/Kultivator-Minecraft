package dev.kultivator.client.aura.realm;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.aura.AuraColors;
import dev.kultivator.client.aura.AuraDraw;
import dev.kultivator.client.aura.AuraHum;
import dev.kultivator.client.aura.AuraParticles;
import dev.kultivator.client.aura.AuraRealmDriver;
import dev.kultivator.client.aura.AuraRig;
import dev.kultivator.client.aura.AuraRng;
import dev.kultivator.client.aura.AuraSubject;
import dev.kultivator.fx.FilmParticles;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Foundation Establishment (Pembentukan Fondasi), DESIGN 31.4.3: the Dao Foundation. A flawless octagonal platform of
 * sky-cyan light under the seat and slim pillars of light standing on its rim: one behind (Awal), left and right
 * (Tengah, when the eight-trigram array opens on the ground), a triangle with a second rim on the platform (Akhir), four
 * pillars joined at their tops by a faint ring of light (Puncak). The pillars are 3D props ({@code aura_fe_pillar}) where
 * the budget allows and crossed glow cards otherwise; qi motes climb them and, at Maximal, sparkles crown their capitals.
 * Pillars glide to their new places once when the stage changes. Crack lines appear only as the bottleneck warning.
 * <p>
 * Sumber: I Shall Seal the Heavens, ch. 76 and 100 and A Will Eternal (the platform and the trigram array are game design).
 */
public final class FoundationAura implements AuraRealmDriver {

	static final Identifier PLATFORM = AuraDraw.tex("fe_platform");
	static final Identifier PILLAR = AuraDraw.tex("fe_pillar");
	static final Identifier BAGUA = AuraDraw.tex("decal_bagua");
	static final String PILLAR_MODEL = "aura_fe_pillar";
	private static final AuraHum.Spec HUM = AuraHum.Spec.loop(SoundEvents.BEACON_AMBIENT, 0.06F, 1.2F);
	/** Pillar angles from the front (degrees, positive toward the right) for 1..4 pillars; an index keeps its pillar. */
	private static final float[][] ANGLES = {{180.0F}, {90.0F, -90.0F}, {60.0F, -60.0F, 180.0F}, {45.0F, -45.0F, 135.0F, -135.0F}};
	/** The pillar card (31.4.3: 0.25 × 1.6 b) and the prop's height (22 px at scale 1). */
	private static final float CARD_W = 0.25F;
	private static final float CARD_H = 1.6F;
	private static final float PROP_H = 22.0F / 16.0F;

	@Override
	public void start(AuraSubject s, AuraRig rig) {
		props(s, rig);
	}

	@Override
	public void tick(AuraSubject s, AuraRig rig) {
		props(s, rig);
		AuraColors.Palette p = s.colors();
		int id = s.entityId();
		long t = rig.clock();
		int count = s.stage() + 1;
		float radius = platformRadius(s, rig);
		for (int i = 0; i < count; i++) {
			Vec3 base = s.around(rig.ease("angle" + i, ANGLES[count - 1][i], 20), radius, s.r(0.06F));
			// qi climbing each pillar
			if ((rig.age() + i * 3) % 4 == 0) {
				Vec3 from = base.add(AuraRng.signed(id, t, 60 + i) * 0.05D * s.scale(), AuraRng.of(id, t, 70 + i) * 0.4D * s.scale(),
						AuraRng.signed(id, t, 80 + i) * 0.05D * s.scale());
				AuraParticles.mote(s, from, new Vec3(0.0D, 0.05D * s.scale(), 0.0D), AuraColors.lighten(p.ring(), 0.35F), 0.07F * (float) s.scale(), 26);
			}
			// sparkles crowning the capitals (Maximal)
			if (EffectQuality.maximal() && (rig.age() + i * 7) % 16 == 0) {
				Vec3 top = base.add(0.0D, s.r(CARD_H * 0.95F), 0.0D);
				AuraParticles.film(s, FilmParticles.MAGIC, top, new Vec3(0.0D, 0.01D, 0.0D), AuraColors.lighten(p.ring(), 0.5F), 0.22F * (float) s.scale(), 18);
			}
		}
	}

	/** Asks for the pillar props of this stage and keeps them gliding with their eased places. */
	private static void props(AuraSubject s, AuraRig rig) {
		rig.sweepBegin();
		int count = s.stage() + 1;
		float radius = platformRadius(s, rig);
		for (int i = 0; i < count; i++) {
			String key = "pillar" + i;
			Vec3 base = s.around(rig.ease("angle" + i, ANGLES[count - 1][i], 20), radius, s.r(0.06F));
			ModelHandle handle = rig.prop(key, () -> AuraKit.riding(s, PILLAR_MODEL, base, (float) s.scale(), true));
			AuraKit.ride(handle, s, base, true);
			rig.propScale(key, (float) s.scale());
		}
		rig.sweepEnd(12);
	}

	private static float platformRadius(AuraSubject s, AuraRig rig) {
		return s.r(rig.ease("platform", 0.9F + 0.1F * s.stage(), 20));
	}

	@Override
	public void render(AuraSubject s, AuraRig rig, AuraDraw draw, float partial) {
		AuraColors.Palette p = s.colors();
		float t = rig.time();
		int stage = s.stage();
		float radius = platformRadius(s, rig);
		int pillarColor = p.ring();

		// the platform of light under the seat (the realm's identity) and, from Akhir, its second rim
		if (s.onGround()) {
			Vec3 plate = s.feet().add(0.0D, 0.06D, 0.0D);
			draw.flat(plate, radius * 1.08F, t * 0.15F, PLATFORM, p.body(), 0.6F);
			float rim = rig.easeFrom("rim", 0.0F, stage >= 2 ? 1.0F : 0.0F, 20);
			if (rim > 0.01F && !s.essentials()) {
				draw.ring(plate.add(0.0D, 0.005D, 0.0D), AuraKit.UP, radius * 1.1F, radius * 1.2F, -t * 0.3F, AuraDraw.BAND,
						AuraColors.lighten(pillarColor, 0.3F), 0.55F * rim);
			}
		}
		// the eight trigrams on the ground (Tengah and up)
		float bagua = rig.easeFrom("bagua", 0.0F, stage >= 1 ? 1.0F : 0.0F, 20);
		draw.decal(s.r(1.5F + 0.08F * stage), -t * 0.25F, BAGUA, pillarColor, 0.48F * bagua);

		// the cultivator in cool foundation light
		if (draw.large()) {
			draw.glow(s.chest(), s.r(0.75F + 0.08F * stage), p.glow(), 0.14F);
		}
		draw.glowFront(s.dantian(), s.r(0.2F), p.core(), 0.3F + 0.1F * AuraKit.breath(t, 70.0F), 0.3F);
		if (s.essentials()) {
			return;
		}

		// the pillars: a prop with a soft halo card, or a crossed glow card standing in for it
		int count = stage + 1;
		for (int i = 0; i < 4; i++) {
			float shown = rig.easeFrom("pillarShown" + i, 0.0F, i < count ? 1.0F : 0.0F, 20);
			if (shown <= 0.01F) {
				continue;
			}
			float angle = rig.ease("angle" + i, i < count ? ANGLES[count - 1][i] : ANGLES[3][i], 20);
			Vec3 base = s.around(angle, radius, s.r(0.06F));
			boolean prop = rig.hasProp("pillar" + i);
			float face = s.bodyYaw() + angle;
			float glowPulse = 0.85F + 0.15F * AuraKit.breath(t + i * 13.0F, 50.0F);
			if (prop) {
				draw.card(base, s.r(0.6F), s.r(PROP_H + 0.25F), face, true, PILLAR, pillarColor, 0.22F * shown * glowPulse);
			} else {
				draw.card(base, s.r(CARD_W), s.r(CARD_H), face, true, PILLAR, pillarColor, 0.85F * shown * glowPulse);
			}
			float top = prop ? PROP_H : CARD_H;
			draw.glow(base.add(0.0D, s.r(top - 0.05F), 0.0D), s.r(0.16F), p.core(), 0.55F * shown * glowPulse);
			draw.glow(base.add(0.0D, s.r(0.06F), 0.0D), s.r(0.22F), pillarColor, 0.3F * shown);
		}
		// Puncak: a faint ring of light joining the tops of the four pillars
		float peak = rig.easeFrom("peak", 0.0F, stage >= 3 ? 1.0F : 0.0F, 20);
		if (peak > 0.01F) {
			float top = rig.hasProp("pillar0") ? PROP_H : CARD_H;
			Vec3 ringCenter = s.feet().add(0.0D, s.r(top), 0.0D);
			draw.ring(ringCenter, AuraKit.UP, radius - s.r(0.04F), radius + s.r(0.04F), t * 0.5F, AuraDraw.BAND,
					AuraColors.lighten(pillarColor, 0.3F), 0.3F * peak);
		}
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
		return 1.5F;
	}
}
