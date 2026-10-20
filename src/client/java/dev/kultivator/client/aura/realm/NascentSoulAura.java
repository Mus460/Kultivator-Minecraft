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
 * Nascent Soul (Jiwa Baru Lahir), DESIGN 31.4.5: a small infant with the cultivator's own face sits cross-legged on a
 * lotus above the head, meditating, a tiny halo behind its head, its faint violet aura echoing the body's. Awal shows the
 * infant only as a faint glow card; Tengah brings the glass infant ({@code aura_ns_infant}) and its eight-petal lotus seat
 * ({@code aura_ns_lotus}) and the halo; Akhir opens the lotus on the ground, brightens the infant (its light breathes
 * every 90 ticks) and draws a thread of light from the crown to the seat; Puncak opens the sixteen-petal lotus (the seat
 * swaps once to {@code aura_ns_lotus16}), adds a second halo ring and a faint echo of the infant in the dantian. Violet
 * motes rise from the body into the infant. The little figure bobs by position only, never by size.
 * <p>
 * Sumber: A Record of a Mortal's Journey, ch. 1077 and 1262 (the lotus seat is game design).
 */
public final class NascentSoulAura implements AuraRealmDriver {

	static final Identifier INFANT = AuraDraw.tex("ns_infant");
	static final Identifier LOTUS8 = AuraDraw.tex("decal_lotus8");
	static final Identifier LOTUS16 = AuraDraw.tex("decal_lotus16");
	static final String INFANT_MODEL = "aura_ns_infant";
	static final String LOTUS_MODEL = "aura_ns_lotus";
	static final String LOTUS16_MODEL = "aura_ns_lotus16";
	private static final int SOUL = 0xE9DCFF;
	private static final int LOTUS = 0xF6C8E8;
	private static final int HALO = 0xFFFFFF;
	/** The infant prop is 10 px tall (scale 0.45), the lotus 12 px across (scale 0.5). */
	private static final float INFANT_SCALE = 0.45F;
	private static final float LOTUS_SCALE = 0.5F;
	private static final float INFANT_H = 10.0F / 16.0F * INFANT_SCALE;

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
		Vec3 heart = infantCenter(s, rig.time());
		// violet motes rising from the body into the infant
		if (rig.age() % 3 == 0) {
			double angle = AuraRng.of(id, t, 100) * Math.PI * 2.0D;
			double radius = (0.25D + 0.15D * AuraRng.of(id, t, 101)) * s.scale();
			Vec3 from = s.feet().add(Math.cos(angle) * radius, s.height() * (0.45D + 0.4D * AuraRng.of(id, t, 102)), Math.sin(angle) * radius);
			AuraParticles.mote(s, from, AuraParticles.toward(from, heart, 28, AuraParticles.MOTE_FRICTION), AuraColors.lighten(p.body(), 0.3F),
					0.07F * (float) s.scale(), 28);
		}
		// starlight twinkling round the halo (Maximal, Tengah and up)
		if (s.stage() >= 1 && EffectQuality.maximal() && rig.age() % 12 == 0) {
			double angle = AuraRng.of(id, t, 110) * Math.PI * 2.0D;
			Vec3 at = head(s, rig.time()).add(Math.cos(angle) * s.r(0.22F), AuraRng.signed(id, t, 111) * s.r(0.12F), Math.sin(angle) * s.r(0.22F));
			AuraParticles.film(s, FilmParticles.STAR, at, Vec3.ZERO, HALO, 0.14F * (float) s.scale(), 16);
		}
	}

	/** The seat and the infant (Tengah and up), riding the crown and bobbing gently with the body's turn. */
	private static void props(AuraSubject s, AuraRig rig) {
		rig.sweepBegin();
		if (s.stage() >= 1) {
			float t = rig.time();
			Vec3 seat = seat(s, t);
			String lotusModel = s.stage() >= 3 ? LOTUS16_MODEL : LOTUS_MODEL;
			ModelHandle lotus = rig.prop("lotus", () -> AuraKit.riding(s, lotusModel, seat, LOTUS_SCALE * (float) s.scale(), true));
			rig.propModel("lotus", lotusModel);
			AuraKit.ride(lotus, s, seat, true);
			Vec3 sit = seat.add(0.0D, s.r(0.05F), 0.0D);
			ModelHandle infant = rig.prop("infant", () -> AuraKit.riding(s, INFANT_MODEL, sit, INFANT_SCALE * (float) s.scale(), true));
			AuraKit.ride(infant, s, sit, true);
			if (infant != null) {
				// Tengah: a soft steady light; Akhir and up: brighter, breathing every 90 ticks
				int light = s.stage() >= 2 ? 2 : 1;
				int shown = rig.data("infantLight", () -> new int[]{0})[0];
				if (shown != light || rig.propAge("infant") == 0) {
					rig.data("infantLight", () -> new int[]{0})[0] = light;
					if (light == 2) {
						infant.lightCycle(12, 90, 0);
					} else {
						infant.lightTo(11, 20);
					}
				}
			}
		}
		rig.sweepEnd(12);
	}

	/** The lotus seat: 0.35 above the crown, bobbing ±0.03 over 80 ticks. */
	private static Vec3 seat(AuraSubject s, float t) {
		double bob = Math.sin(Math.PI * 2.0D * (t % 80.0F) / 80.0F) * 0.03D * s.scale();
		return s.crown().add(0.0D, s.r(0.35F) + bob, 0.0D);
	}

	private static Vec3 infantCenter(AuraSubject s, float t) {
		return seat(s, t).add(0.0D, s.r(0.05F + INFANT_H * 0.5F), 0.0D);
	}

	private static Vec3 head(AuraSubject s, float t) {
		return seat(s, t).add(0.0D, s.r(0.05F + INFANT_H * 0.8F), 0.0D);
	}

	@Override
	public void render(AuraSubject s, AuraRig rig, AuraDraw draw, float partial) {
		AuraColors.Palette p = s.colors();
		float t = rig.time();
		int stage = s.stage();
		Vec3 seat = seat(s, t);
		Vec3 heart = infantCenter(s, t);
		float breath = AuraKit.breath(t, 90.0F);
		int lotusColor = AuraColors.mix(LOTUS, p.tint(), 0.2F);

		// the lotus opening on the ground: eight petals (Akhir), sixteen (Puncak), cross-faded
		float lotus8 = rig.easeFrom("lotus8", 0.0F, stage == 2 ? 1.0F : 0.0F, 20);
		float lotus16 = rig.easeFrom("lotus16", 0.0F, stage >= 3 ? 1.0F : 0.0F, 20);
		draw.decal(s.r(1.3F), t * 0.2F, LOTUS8, lotusColor, 0.5F * lotus8);
		draw.decal(s.r(1.4F), t * 0.2F, LOTUS16, lotusColor, 0.52F * lotus16);

		// a soft violet body glow
		if (draw.large()) {
			draw.glow(s.chest(), s.r(0.85F + 0.1F * stage), p.glow(), 0.13F + 0.03F * breath);
		}

		// the infant: a glow card alone at Awal (or when no prop is shown), a soul glow round the prop from Tengah
		boolean prop = rig.hasProp("infant");
		float soul = rig.ease("soul", stage == 0 ? 0.35F : stage == 1 ? 0.55F : 0.8F, 20);
		if (!prop || s.essentials()) {
			draw.standee(seat.add(0.0D, s.r(0.02F), 0.0D), s.r(0.42F), s.r(0.5F), INFANT, SOUL, soul * (0.85F + 0.15F * breath));
		}
		draw.glow(heart, s.r(0.34F), AuraColors.mix(SOUL, p.body(), 0.35F), (prop ? 0.32F : 0.22F) * soul / 0.8F);
		if (s.essentials()) {
			return;
		}
		if (!rig.hasProp("lotus") && stage >= 1) {
			draw.flat(seat.add(0.0D, s.r(0.01F), 0.0D), s.r(0.24F), t * 0.5F, LOTUS8, lotusColor, 0.6F);
		}

		// the halo behind its head (Tengah: r 0.18; Puncak: r 0.26 and a second ring)
		float halo = rig.easeFrom("halo", 0.0F, stage >= 1 ? 1.0F : 0.0F, 20);
		if (halo > 0.01F) {
			float r = s.r(rig.ease("haloR", stage >= 3 ? 0.26F : 0.18F, 20));
			Vec3 behind = head(s, t).subtract(s.forward().scale(s.r(0.07F)));
			draw.ring(behind, s.forward(), r * 0.72F, r, t, AuraDraw.BAND, HALO, 0.75F * halo);
			draw.glow(behind, r * 0.9F, SOUL, 0.25F * halo);
			float second = rig.easeFrom("halo2", 0.0F, stage >= 3 ? 1.0F : 0.0F, 20);
			if (second > 0.01F) {
				draw.ring(behind, s.forward(), r * 1.28F, r * 1.4F, -t * 0.7F, AuraDraw.BAND, AuraColors.mix(HALO, p.body(), 0.3F), 0.45F * second);
			}
		}
		// the thread of light from the crown up to the seat (Akhir and up)
		float thread = rig.easeFrom("thread", 0.0F, stage >= 2 ? 1.0F : 0.0F, 20);
		if (thread > 0.01F) {
			draw.beam(s.crown(), seat, s.r(0.035F), SOUL, (0.22F + 0.1F * breath) * thread);
		}
		// Puncak: a faint echo of the infant's glow in the dantian
		float echo = rig.easeFrom("echo", 0.0F, stage >= 3 ? 1.0F : 0.0F, 20);
		if (echo > 0.01F) {
			draw.glowFront(s.dantian(), s.r(0.2F), SOUL, (0.22F + 0.1F * breath) * echo, 0.3F);
		}
	}

	@Override
	public void stop(AuraSubject s, AuraRig rig) {
		rig.endProps(12);
	}

	@Override
	public AuraHum.Spec hum(AuraSubject s) {
		return new AuraHum.Spec(SoundEvents.CONDUIT_AMBIENT, 0.06F, 1.0F, SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0F, 120);
	}

	@Override
	public float reach(AuraSubject s) {
		return 1.4F;
	}
}
