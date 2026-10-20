package dev.kultivator.client.aura.high;

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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Deity Transformation (DESIGN 31.4.6): the Dharma Idol. An enormous translucent seated figure of the cultivator rises
 * behind them (two crossed cards, the aura's one large layer) with a flaming head halo, while rings of divine sense ripple
 * out over the ground from a dharma-wheel decal. Stages: Awal the ripples, the crown flame halo and a faint 3.5 b idol;
 * Tengah a 4.3 b idol, the flaming halo wheel prop and the decal; Akhir a 5.2 b idol, the idol's hands in the meditation
 * mudra and quicker ripples; Puncak a 6 b idol with a rim glow, two interleaved ripple trains and the halo turning.
 * Hidden while the subject's own Dharma Idol technique is up (no double giant).
 * <p>
 * Sumber: I Shall Seal the Heavens, bab 805; A Record of a Mortal's Journey, bab 1269 (kultivator.aura.realm.
 * deity_transformation.source).
 */
final class DeityAura implements AuraRealmDriver {

	private static final Identifier IDOL = HighKit.tex("dt_idol");
	private static final Identifier IDOL_RIM = HighKit.tex("dt_idol_rim");
	private static final Identifier RIPPLE = HighKit.tex("dt_ripple");
	private static final Identifier FLAME_HALO = HighKit.tex("dt_flame_halo");
	private static final Identifier WHEEL = HighKit.tex("decal_dharma_wheel");
	/** The idol's rim colour. */
	private static final int RIM = 0xFFF0FA;
	/** Idol height (blocks at scale 1) and alpha by stage. */
	private static final float[] IDOL_H = {3.5F, 4.3F, 5.2F, 6.0F};
	private static final float[] IDOL_A = {0.12F, 0.2F, 0.26F, 0.32F};
	/** The halo wheel prop by stage (Tengah on; its model is 24 px across, so 1.0-2.0 here is 31.4.6's 1.5-3 of a 16 px one) and
	 * the mudra hands (Akhir on; 20 px across). */
	private static final float[] HALO_SCALE = {1.0F, 1.0F, 1.5F, 2.0F};
	private static final float[] HANDS_SCALE = {1.2F, 1.2F, 1.2F, 1.5F};
	/** The idol stands this far behind the body (blocks at scale 1). */
	private static final float BACK = 0.35F;
	/** A divine-sense ripple lives this long, rolling from r 0.5 to r 6. */
	private static final float RIPPLE_LIFE = 70.0F;
	/** Where the idol's head and lap are on its card (fractions of its height from the ground, from the texture). */
	private static final float HEAD = 0.745F;
	private static final float LAP = 0.31F;
	private static final String HALO = "dt.halo";
	private static final String HANDS = "dt.hands";

	private @Nullable AuraHum.Spec hum;

	/** Per-aura scratch state. */
	private static final class State {
		boolean spinning;
	}

	@Override
	public void start(AuraSubject s, AuraRig rig) {
		props(s, rig);
	}

	@Override
	public void tick(AuraSubject s, AuraRig rig) {
		props(s, rig);
		particles(s, rig);
	}

	@Override
	public void stop(AuraSubject s, AuraRig rig) {
		rig.endProps(12);
		rig.data("dt", State::new).spinning = false;
	}

	@Override
	public AuraHum.Spec hum(AuraSubject s) {
		if (hum == null) {
			hum = AuraHum.Spec.loop(SoundEvents.RESPAWN_ANCHOR_AMBIENT, 0.06F, 0.7F);
		}
		return hum;
	}

	@Override
	public float reach(AuraSubject s) {
		return 1.4F + 0.2F * s.stage();
	}

	// ================================================================== props

	private void props(AuraSubject s, AuraRig rig) {
		State st = rig.data("dt", State::new);
		int stage = s.stage();
		float h = s.r(rig.ease("dt.h", IDOL_H[stage], HighKit.EASE));
		boolean show = !s.essentials() && !s.dharma() && !s.firstPersonSelf() && s.entity() != null;
		if (stage < 3 && st.spinning) {
			// left Puncak: the turning halo goes and a still one grows back
			rig.endProp(HALO, 10);
			st.spinning = false;
		}
		rig.sweepBegin();
		if (show && stage >= 1) {
			// the flaming halo wheel behind the idol's head
			float scale = s.r(HALO_SCALE[stage]);
			Vec3 local = new Vec3(0.0D, h * HEAD, -s.r(BACK + 0.08F));
			boolean fresh = !rig.hasProp(HALO);
			ModelHandle halo = rig.prop(HALO, () -> HighKit.follow(s, "aura_dt_halo", scale, local, 0.0F, 0.0F, 0.0F));
			if (halo != null) {
				if (fresh) {
					st.spinning = false;
				}
				rig.propScale(HALO, scale);
				HighKit.keepOffset(rig, HALO, s, local);
				if (stage == 3 && !st.spinning) {
					// Puncak: the halo starts to turn (0.4 degrees a tick, in its own plane)
					halo.play(HighKit.spin(0.4F, 2));
					st.spinning = true;
				}
			}
		}
		if (show && stage >= 2) {
			// the idol's hands in the meditation mudra, resting in its lap above the cultivator
			float scale = s.r(HANDS_SCALE[stage]);
			Vec3 local = new Vec3(0.0D, h * LAP, -s.r(BACK - 0.15F));
			rig.prop(HANDS, () -> HighKit.follow(s, "aura_dt_hands", scale, local, 0.0F, 0.0F, 0.0F));
			rig.propScale(HANDS, scale);
			HighKit.keepOffset(rig, HANDS, s, local);
		}
		rig.sweepEnd(10);
	}

	// ================================================================== particles

	private void particles(AuraSubject s, AuraRig rig) {
		if (s.firstPersonSelf()) {
			return;
		}
		int stage = s.stage();
		long clock = rig.clock();
		int id = s.entityId();
		AuraColors.Palette p = s.colors();
		float h = s.r(rig.ease("dt.h", IDOL_H[stage], HighKit.EASE));
		Vec3 fwd = s.forward();
		Vec3 right = s.right();
		if (!s.dharma()) {
			// flame tongues licking upward along the idol's outline
			Vec3 centre = s.feet().subtract(fwd.scale(s.r(BACK))).add(0.0D, h * 0.5D, 0.0D);
			int flames = 1 + (stage + 1) / 2;
			for (int i = 0; i < flames; i++) {
				double a = AuraRng.of(id, clock, 11 + i) * Math.PI * 2.0D;
				double x = Math.cos(a) * h * 0.36D;
				double y = Math.sin(a) * h * 0.46D;
				Vec3 at = centre.add(right.scale(x)).add(0.0D, y, 0.0D);
				int rgb = AuraColors.mix(p.body(), RIM, AuraRng.of(id, clock, 21 + i) * 0.5F);
				if (!AuraParticles.film(s, FilmParticles.FLAME, at, new Vec3(0.0D, 0.035D * s.scale(), 0.0D), rgb, s.r(0.2F) + h * 0.05F, 16)) {
					break;
				}
			}
		}
		if (clock % 3L == 0L) {
			// rose motes drifting up around the body
			double angle = AuraRng.of(id, clock, 31) * 360.0D;
			Vec3 at = s.around(angle, s.r(0.45F + 0.25F * AuraRng.of(id, clock, 32)), s.height() * AuraRng.of(id, clock, 33));
			AuraParticles.mote(s, at, new Vec3(0.0D, 0.025D * s.scale(), 0.0D), AuraColors.lighten(p.body(), 0.3F), s.r(0.12F), 30);
		}
	}

	// ================================================================== frame

	@Override
	public void render(AuraSubject s, AuraRig rig, AuraDraw draw, float partial) {
		if (s.firstPersonSelf()) {
			return;
		}
		AuraColors.Palette p = s.colors();
		int stage = s.stage();
		float t = rig.time();
		float grow = rig.ease("dt.grow", s.stageGrow(), HighKit.EASE);
		float h = s.r(rig.ease("dt.h", IDOL_H[stage], HighKit.EASE));
		float idolA = rig.ease("dt.a", IDOL_A[stage], HighKit.EASE);
		float decalA = HighKit.appear(rig, "dt.decal", s, 1);
		float rimA = HighKit.appear(rig, "dt.rim", s, 3);
		int fill = AuraColors.mix(p.tint(), p.body(), 0.5F);
		int ring = p.ring();
		float breath = HighKit.wave(t, 120.0F, 0.0F);
		Vec3 fwd = s.forward();

		// the dharma wheel decal under the seat (Tengah on)
		if (decalA > 0.01F) {
			draw.decal(s.r(1.3F + 0.3F * stage), t * 0.2F, WHEEL, ring, 0.55F * decalA);
		}
		// the Dharma Idol: two crossed cards rising behind the body (the aura's one large layer)
		if (!s.dharma()) {
			Vec3 base = s.feet().subtract(fwd.scale(s.r(BACK)));
			float a = s.reduced() ? 0.15F : idolA + 0.03F * (breath * 2.0F - 1.0F);
			if (draw.large()) {
				draw.card(base, h, h, s.bodyYaw(), true, IDOL, fill, a);
			}
			if (!s.essentials() && rimA > 0.01F && !draw.pack()) {
				draw.card(base, h, h, s.bodyYaw(), true, IDOL_RIM, RIM, (0.35F + 0.15F * breath) * rimA);
			}
		}
		// a soft rose glow about the body
		draw.glow(s.chest(), s.r(0.55F + 0.3F * grow), p.glow(), 0.18F + 0.06F * breath);
		if (s.reduced()) {
			return;
		}
		// the crown flame halo behind the head
		Vec3 head = s.crown().subtract(0.0D, s.r(0.3F), 0.0D).subtract(fwd.scale(s.r(0.22F)));
		draw.billboard(head, s.r(0.3F + 0.06F * stage), t * 0.5F, FLAME_HALO, AuraColors.lighten(p.body(), 0.25F), 0.5F + 0.1F * breath);
		// divine-sense ripples rolling out over the ground
		int period = stage >= 2 ? 40 : 60;
		int trains = stage == 3 && !s.simple() ? 2 : 1;
		for (int train = 0; train < trains; train++) {
			float tt = t + train * period * 0.5F;
			float k = Mth.floor(tt / period);
			for (int j = 0; j < 2; j++) {
				float age = tt - (k - j) * period;
				if (age < 0.0F || age >= RIPPLE_LIFE) {
					continue;
				}
				float q = age / RIPPLE_LIFE;
				float r = s.r(0.5F + 5.5F * HighKit.easeOut(q));
				float a = 0.5F * (float) Math.pow(1.0F - q, 1.6F) * (train == 0 ? 1.0F : 0.7F) * Math.min(1.0F, age / 4.0F);
				if (!draw.decal(r, 0.0F, RIPPLE, ring, a)) {
					return;
				}
			}
		}
	}
}
