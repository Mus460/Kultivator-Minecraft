package dev.kultivator.client.aura.realm;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.AnimStep;
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
 * Golden Core (Inti Emas), DESIGN 31.4.4: the golden core. A small sun of golden light shines in the dantian through the
 * body (a faceted golden pearl, {@code aura_gc_core}, breathing by light every 80 ticks, with layered glows pushed toward
 * the viewer so the body never hides it), golden halo rings turn around it (one at Awal, two tilted ±25° with a turning
 * fan of twelve rays at Tengah, three and the sun array on the ground with a soft golden column at Akhir), and at Puncak
 * the core sun grows to r 0.3 and a great golden halo ({@code aura_gc_halo}, r 1.2) turns 0.5°/t with nine golden beads
 * riding it. Golden glints fall inward to the core; the warm wide glow fills the space like the light of a pill furnace.
 * <p>
 * Sumber: I Shall Seal the Heavens, ch. 337-339; 金丹 in Daoist inner alchemy (the sun and the halo rings are game design).
 */
public final class GoldenCoreAura implements AuraRealmDriver {

	static final Identifier RAYS = AuraDraw.tex("gc_rays");
	static final Identifier HALO = AuraDraw.tex("gc_halo");
	static final Identifier SUN_ARRAY = AuraDraw.tex("decal_sun_array");
	static final String CORE_MODEL = "aura_gc_core";
	static final String HALO_MODEL = "aura_gc_halo";
	private static final int RIM = 0xE0A020;
	/** The tilt (degrees from flat) of the inner halo rings for one, two (±25°) and three rings; the third lies flat. */
	private static final float[][] TILT = {{10.0F, 25.0F, -25.0F}, {25.0F, -25.0F, 0.0F}, {25.0F, -25.0F, 0.0F}};
	private static final float[] AZIMUTH = {0.0F, 60.0F, 120.0F};

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
		Vec3 core = core(s);
		// golden glints falling inward to the core
		if (rig.age() % 3 == 0) {
			double angle = AuraRng.of(id, t, 90) * Math.PI * 2.0D;
			double radius = (0.9D + 0.5D * AuraRng.of(id, t, 91)) * s.scale();
			double lift = (0.2D + 0.9D * AuraRng.of(id, t, 92)) * s.scale();
			Vec3 from = core.add(Math.cos(angle) * radius, lift, Math.sin(angle) * radius);
			AuraParticles.glint(s, from, AuraParticles.toward(from, core, 22, AuraParticles.MOTE_FRICTION), AuraColors.lighten(p.body(), 0.2F),
					0.09F * (float) s.scale(), 22);
		}
		// film flares on the halo beads (Maximal, Puncak)
		if (s.stage() >= 3 && EffectQuality.maximal() && rig.age() % 9 == 0) {
			int bead = (rig.age() / 9) % 9;
			Vec3 at = bead(s, rig.time(), bead);
			AuraParticles.film(s, FilmParticles.FLARE, at, Vec3.ZERO, AuraColors.lighten(p.body(), 0.35F), 0.3F * (float) s.scale(), 10);
		}
	}

	/** The core pearl (always) and the great halo (Puncak). */
	private static void props(AuraSubject s, AuraRig rig) {
		rig.sweepBegin();
		Vec3 core = core(s);
		ModelHandle pearl = rig.prop("core", () -> AuraKit.riding(s, CORE_MODEL, core, 0.6F * (float) s.scale(), true));
		if (pearl != null && rig.propAge("core") == 0) {
			pearl.lightCycle(11, 80, 0);
		}
		AuraKit.ride(pearl, s, core, true);
		if (s.stage() >= 3) {
			Vec3 center = s.dantian();
			ModelHandle halo = rig.prop("halo", () -> AuraKit.riding(s, HALO_MODEL, center, 1.2F * (float) s.scale(), false,
					AnimStep.of(Anim.SPIN, 0, AuraKit.PROP_LIFE, 0.5F, 0.0F)));
			AuraKit.ride(halo, s, center, false);
		}
		rig.sweepEnd(12);
	}

	/** The core sun: in front of the belly at the dantian, so it floats above the hands resting in the lap. */
	private static Vec3 core(AuraSubject s) {
		return s.dantian().add(s.forward().scale(s.r(0.28F)));
	}

	/** Bead {@code i} of the nine on the great halo (r 1.2 round the dantian), turning 0.5°/t. */
	private static Vec3 bead(AuraSubject s, float t, int i) {
		double angle = Math.toRadians(t * 0.5F + i * 40.0F);
		return AuraKit.circle(s.dantian(), s.r(1.2F), angle, 0.0D);
	}

	@Override
	public void render(AuraSubject s, AuraRig rig, AuraDraw draw, float partial) {
		AuraColors.Palette p = s.colors();
		float t = rig.time();
		int stage = s.stage();
		Vec3 core = core(s);
		Vec3 dantian = s.dantian();
		float coreR = s.r(rig.ease("core", stage >= 3 ? 0.3F : 0.18F, 20));
		float breath = AuraKit.breath(t, 80.0F);

		// the sun array on the ground (Akhir and up)
		float array = rig.easeFrom("array", 0.0F, stage >= 2 ? 1.0F : 0.0F, 20);
		draw.decal(s.r(1.25F), t * 0.2F, SUN_ARRAY, p.ring(), 0.5F * array);

		// the warm, wide light of the pill furnace
		if (draw.large()) {
			draw.glow(s.chest(), s.r(1.0F + 0.12F * stage), p.glow(), 0.12F + 0.03F * breath);
		}
		// the core sun shining through the body (the realm's identity)
		draw.glowFront(core, coreR * 2.8F, p.body(), 0.24F + 0.06F * breath, 0.12F);
		draw.glowFront(core, coreR * 1.35F, AuraColors.lighten(p.body(), 0.35F), 0.6F + 0.1F * breath, 0.14F);
		draw.glowFront(core, coreR * 0.6F, p.core(), 0.95F, 0.16F);
		if (s.essentials()) {
			return;
		}

		// the fan of twelve rays (Tengah and up), turning 1°/t
		float rays = rig.easeFrom("rays", 0.0F, stage >= 1 ? 1.0F : 0.0F, 20);
		if (rays > 0.01F) {
			draw.billboard(draw.front(core, 0.13F), coreR * 4.4F, t, RAYS, AuraColors.lighten(p.body(), 0.2F), 0.45F * rays);
		}

		// the halo rings round the dantian: one, two (±25°), three; slowly precessing like a gyroscope
		int rings = Math.min(3, stage + 1);
		int ringColor = AuraColors.lighten(p.glow(), 0.15F);
		for (int i = 0; i < 3; i++) {
			float shown = rig.easeFrom("ring" + i, 0.0F, i < rings ? 1.0F : 0.0F, 20);
			if (shown <= 0.01F) {
				continue;
			}
			float tilt = rig.ease("tilt" + i, TILT[rings - 1][i], 20);
			Vec3 axis = AuraKit.tilted(tilt, AZIMUTH[i] + t * 0.6F);
			float r = s.r(0.55F + 0.05F * i);
			draw.ring(dantian, axis, r - s.r(0.04F), r + s.r(0.04F), (i % 2 == 0 ? 2.0F : -2.0F) * t, HALO, ringColor, 0.75F * shown);
		}

		// a soft golden column from the array to the crown (Akhir and up)
		float column = rig.easeFrom("column", 0.0F, stage >= 2 ? 1.0F : 0.0F, 20);
		if (column > 0.01F) {
			draw.beam(s.feet().add(0.0D, 0.05D, 0.0D), s.crown(), s.r(0.32F), p.body(), 0.18F * column);
		}

		// Puncak: the great halo and the nine golden beads riding it
		float peak = rig.easeFrom("peak", 0.0F, stage >= 3 ? 1.0F : 0.0F, 20);
		if (peak > 0.01F) {
			if (rig.hasProp("halo")) {
				draw.ring(dantian, AuraKit.UP, s.r(1.1F), s.r(1.3F), t * 0.5F, AuraDraw.BAND, p.glow(), 0.22F * peak);
			} else {
				draw.ring(dantian, AuraKit.UP, s.r(1.15F), s.r(1.25F), t * 0.5F, HALO, AuraColors.mix(p.body(), RIM, 0.3F), 0.7F * peak);
			}
			for (int i = 0; i < 9; i++) {
				Vec3 at = bead(s, t, i);
				draw.glow(at, s.r(0.11F), AuraColors.lighten(p.body(), 0.25F), 0.8F * peak);
				draw.glow(at, s.r(0.04F), 0xFFFFFF, 0.9F * peak);
			}
		}
	}

	@Override
	public void stop(AuraSubject s, AuraRig rig) {
		rig.endProps(12);
	}

	@Override
	public AuraHum.Spec hum(AuraSubject s) {
		return new AuraHum.Spec(SoundEvents.BEACON_AMBIENT, 0.07F, 0.8F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 100);
	}

	@Override
	public float reach(AuraSubject s) {
		return s.stage() >= 3 ? 1.35F : 1.25F;
	}
}
