package dev.kultivator.client.aura.high;

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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * True Immortal (DESIGN 31.4.9): a celestial wheel of stars turns round the immortal like the Great Heavenly Circuit, a
 * domain of immortal radiance spreads over the ground and the immortal sits on a lotus throne of three tiers of nine
 * petals. Stages: Awal the throne and the wheel (9 stars); Tengah 18 stars, the domain (r 3) and the star-chart decal;
 * Akhir 28 stars with the seven of the Northern Dipper bright, the domain r 4.5, the armillary prop; Puncak the domain
 * r 6, a second wheel crossing the first at 60 degrees, the nine-grade throne and a column of light. An ascended immortal
 * shifts through a slow rainbow (240 ticks) and petals drift down. True Immortal Puncak is the highest rank, so the
 * breakthrough-ready glow never shows here (the engine checks it).
 * <p>
 * Sumber: 大周天; Beidou and the 28 lunar mansions; Renegade Immortal, bab 826, 851; 观无量寿经 九品莲台
 * (kultivator.aura.realm.true_immortal.source).
 */
final class TrueImmortalAura implements AuraRealmDriver {

	private static final Identifier STAR = HighKit.tex("ti_star");
	private static final Identifier DOMAIN = HighKit.tex("ti_domain");
	private static final Identifier STAR_CHART = HighKit.tex("decal_star_chart");
	private static final int STAR_RGB = 0xE8F0FF;
	private static final int LOTUS_GOLD = 0xFFD54A;
	/** The star wheel: radius (blocks at scale 1), tilt toward the body's front, turn rate; the second wheel leans back. */
	private static final float WHEEL_R = 2.4F;
	private static final float WHEEL_TILT = 22.0F;
	private static final float WHEEL2_TILT = -38.0F;
	private static final float TURN = 0.3F;
	/** Star slots on the wheel and the order they light up in (9, then 18, then all 28, evenly spread at every count). */
	private static final int SLOTS = 28;
	private static final int[] RANK = new int[SLOTS];
	/** The seven stars of the Northern Dipper (consecutive slots, bright from Akhir). */
	private static final int BEIDOU_FROM = 4;
	private static final int BEIDOU = 7;
	private static final float[] STARS = {9.0F, 18.0F, 28.0F, 28.0F};
	private static final float[] DOMAIN_R = {0.0F, 3.0F, 4.5F, 6.0F};
	/** The throne's seat top is 4.2 px up its model, whose anchor is at 8 px. */
	private static final float SEAT_PX = 4.2F;
	private static final String LOTUS = "ti.lotus";
	private static final String ARMILLARY = "ti.wheel";

	static {
		Set<Integer> order = new LinkedHashSet<>();
		for (int i = 0; i < 9; i++) {
			order.add(Math.round(i * SLOTS / 9.0F) % SLOTS);
		}
		for (int i = 0; i < 18; i++) {
			order.add(Math.round(i * SLOTS / 18.0F) % SLOTS);
		}
		for (int i = 0; i < SLOTS; i++) {
			order.add(i);
		}
		int rank = 0;
		for (int slot : order) {
			RANK[slot] = rank++;
		}
	}

	private @Nullable AuraHum.Spec hum;

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
	}

	@Override
	public AuraHum.Spec hum(AuraSubject s) {
		if (hum == null) {
			hum = new AuraHum.Spec(SoundEvents.BEACON_AMBIENT, 0.08F, 0.6F, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 140);
		}
		return hum;
	}

	@Override
	public float reach(AuraSubject s) {
		return WHEEL_R;
	}

	// ================================================================== shapes

	/** The centre of the star wheels (a little above the dantian). */
	private static Vec3 hub(AuraSubject s) {
		return s.dantian().add(0.0D, s.r(0.35F), 0.0D);
	}

	private static Vec3 normal(AuraSubject s, float tilt) {
		return HighKit.tilted(s.forward(), tilt);
	}

	/** The colour of the wheel: the root's elements fused with the Dao colour (a slow rainbow once ascended). */
	private static int wheelColor(AuraSubject s, AuraColors.Palette p, float t) {
		if (s.ascended()) {
			return HighKit.hue(t, 240.0F, 0.0F, 0.35F);
		}
		int[] el = p.elements();
		int c = el.length == 0 ? p.tint() : el[0];
		for (int i = 1; i < el.length; i++) {
			c = AuraColors.mix(c, el[i], 1.0F / (i + 1));
		}
		return AuraColors.mix(AuraColors.mix(c, p.tint(), 0.35F), STAR_RGB, 0.3F);
	}

	private static int starColor(AuraSubject s, AuraColors.Palette p, int slot, float t) {
		if (s.ascended()) {
			return HighKit.hue(t, 240.0F, slot * 240.0F / SLOTS, 0.3F);
		}
		int[] el = p.elements();
		int c = el.length == 0 ? p.tint() : el[slot % el.length];
		return AuraColors.mix(STAR_RGB, AuraColors.mix(c, p.tint(), 0.35F), 0.35F);
	}

	private static boolean beidou(int slot) {
		return slot >= BEIDOU_FROM && slot < BEIDOU_FROM + BEIDOU;
	}

	// ================================================================== props

	private void props(AuraSubject s, AuraRig rig) {
		int stage = s.stage();
		boolean show = !s.essentials() && !s.firstPersonSelf() && s.entity() != null;
		rig.sweepBegin();
		if (show) {
			// the lotus throne: its seat a hair above the ground, the cultivator sitting in its petals
			float scale = s.r(1.2F);
			String model = stage >= 3 ? "aura_ti_lotus9" : "aura_ti_lotus";
			Vec3 local = new Vec3(0.0D, s.r(0.03F) + (8.0F - SEAT_PX) / 16.0F * scale, 0.0D);
			rig.prop(LOTUS, () -> HighKit.follow(s, model, scale, local, 0.0F, 0.0F, 0.0F));
			rig.propModel(LOTUS, model);
			rig.propScale(LOTUS, scale);
		}
		if (show && stage >= 2) {
			// the armillary ring on the star wheel's plane (pitch + leans its front down, as the wheel does), turning
			float scale = s.r(WHEEL_R);
			Vec3 local = new Vec3(0.0D, HighKit.above(s, hub(s)), 0.0D);
			rig.prop(ARMILLARY, () -> HighKit.follow(s, "aura_ti_wheel", scale, local, 0.0F, WHEEL_TILT, 0.0F, HighKit.spin(TURN, 0)));
			HighKit.keepOffset(rig, ARMILLARY, s, local);
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
		float t = rig.time();
		// star points riding the wheel
		int count = (int) STARS[stage];
		int slot = (int) (AuraRng.of(id, clock, 81) * SLOTS) % SLOTS;
		if (RANK[slot] < count) {
			Vec3[] plane = HighKit.basis(normal(s, WHEEL_TILT));
			double angle = Math.toRadians(t * TURN + slot * 360.0F / SLOTS);
			Vec3 at = HighKit.onCircle(hub(s), plane, s.r(WHEEL_R), angle);
			Vec3 ahead = HighKit.onCircle(hub(s), plane, s.r(WHEEL_R), angle + 0.05D);
			AuraParticles.film(s, FilmParticles.STAR, at, ahead.subtract(at).scale(0.3D), starColor(s, p, slot, t), s.r(0.3F), 12);
		}
		if (stage >= 3) {
			// golden glints rising in the column of light
			double a = AuraRng.of(id, clock, 82) * Math.PI * 2.0D;
			double r = s.r(0.25F) * AuraRng.of(id, clock, 83);
			Vec3 at = s.feet().add(Math.cos(a) * r, s.r(0.2F), Math.sin(a) * r);
			AuraParticles.glint(s, at, new Vec3(0.0D, 0.09D * s.scale(), 0.0D), LOTUS_GOLD, s.r(0.15F), 30);
		}
		if (s.ascended() && clock % 2L == 0L) {
			// the ascension's gift: petals in the shifting rainbow
			double a = AuraRng.of(id, clock, 84) * 360.0D;
			Vec3 at = s.around(a, s.r(0.4F + 1.2F * AuraRng.of(id, clock, 85)), s.height() + s.r(0.3F + 0.8F * AuraRng.of(id, clock, 86)));
			AuraParticles.petal(s, at, new Vec3(0.0D, -0.02D * s.scale(), 0.0D), HighKit.hue(t, 240.0F, (float) a, 0.45F), s.r(0.15F), 60);
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
		float grow = rig.ease("ti.grow", s.stageGrow(), HighKit.EASE);
		float domainR = rig.ease("ti.domain", DOMAIN_R[stage], HighKit.EASE);
		float decalA = HighKit.appear(rig, "ti.decal", s, 1);
		float beidouA = HighKit.appear(rig, "ti.beidou", s, 2);
		float crossA = HighKit.appear(rig, "ti.cross", s, 3);
		float breath = HighKit.wave(t, 120.0F, 0.0F);
		int body = s.ascended() ? AuraColors.mix(p.body(), HighKit.hue(t, 240.0F, 0.0F, 0.4F), 0.5F) : p.body();
		int wheel = wheelColor(s, p, t);

		// the domain of immortal radiance over the ground (the aura's large layer)
		if (domainR > 0.05F && draw.large()) {
			draw.decal(s.r(domainR), t * 0.05F, DOMAIN, body, 0.2F * Math.min(1.0F, domainR / 3.0F));
		}
		// the star chart under the seat (Tengah on)
		if (decalA > 0.01F) {
			draw.decal(s.r(1.9F), -t * 0.08F, STAR_CHART, p.ring(), 0.6F * decalA);
		}
		// the immortal's glow
		draw.glow(s.chest(), s.r(0.6F + 0.4F * grow), AuraColors.mix(body, p.tint(), 0.2F), 0.2F + 0.06F * breath);
		Vec3 hub = hub(s);
		Vec3 n1 = normal(s, WHEEL_TILT);
		Vec3[] plane = HighKit.basis(n1);
		float wheelR = s.r(WHEEL_R);
		if (s.reduced()) {
			// the identity at a distance: the nine brightest stars of the wheel
			for (int slot = 0; slot < SLOTS; slot++) {
				if (RANK[slot] < 9) {
					Vec3 at = HighKit.onCircle(hub, plane, wheelR, Math.toRadians(t * TURN + slot * 360.0F / SLOTS));
					if (!draw.billboard(at, s.r(0.16F), 0.0F, STAR, starColor(s, p, slot, t), 0.85F)) {
						return;
					}
				}
			}
			return;
		}
		// the column of light (Puncak)
		if (crossA > 0.01F) {
			draw.beam(s.feet(), s.feet().add(0.0D, s.r(7.0F), 0.0D), s.r(0.55F), body, (0.2F + 0.08F * breath) * crossA);
		}
		// the lotus throne's stand-in while its prop is not shown (Sederhana, a refused prop)
		if (!rig.hasProp(LOTUS) && s.onGround()) {
			Vec3 seat = s.feet().add(0.0D, 0.08D, 0.0D);
			draw.flat(seat, s.r(0.75F), t * 0.2F, AuraDraw.GLOW, LOTUS_GOLD, 0.35F);
			for (int k = 0; k < 9; k++) {
				double a = Math.toRadians(k * 40.0F + t * 0.2F);
				draw.glow(seat.add(Math.cos(a) * s.r(0.6F), s.r(0.12F), Math.sin(a) * s.r(0.6F)), s.r(0.13F), 0xFFF4D8, 0.5F);
			}
		}
		// the star wheel
		float stars = rig.ease("ti.stars", STARS[stage], HighKit.EASE);
		if (!s.simple() && draw.left() > 70) {
			draw.ring(hub, n1, wheelR - s.r(0.015F), wheelR + s.r(0.015F), 0.0F, AuraDraw.BAND, wheel, 0.3F);
		}
		Vec3 prev = null;
		for (int slot = 0; slot < SLOTS; slot++) {
			float a = Mth.clamp(stars - RANK[slot], 0.0F, 1.0F);
			if (a <= 0.01F) {
				prev = null;
				continue;
			}
			double angle = Math.toRadians(t * TURN + slot * 360.0F / SLOTS);
			Vec3 at = HighKit.onCircle(hub, plane, wheelR, angle);
			int rgb = starColor(s, p, slot, t);
			boolean bright = beidou(slot) && beidouA > 0.01F;
			float twinkle = HighKit.wave(t, 50.0F + slot % 5 * 7.0F, slot * 13.0F);
			float half = s.r(0.11F + (bright ? 0.1F * beidouA : 0.0F));
			if (!draw.billboard(at, half, bright ? t * 0.6F : 0.0F, STAR, rgb, (0.7F + 0.25F * twinkle) * a)) {
				return;
			}
			if (bright) {
				draw.glow(at, s.r(0.3F), AuraColors.lighten(rgb, 0.3F), 0.4F * beidouA * a);
				if (prev != null && !s.simple()) {
					// the Dipper's stars joined by a faint line
					draw.beam(prev, at, s.r(0.025F), rgb, 0.3F * beidouA);
				}
				prev = at;
			} else {
				prev = null;
			}
		}
		// the second wheel crossing the first at 60 degrees (Puncak)
		if (crossA > 0.01F && !s.simple()) {
			Vec3 n2 = normal(s, WHEEL2_TILT);
			Vec3[] plane2 = HighKit.basis(n2);
			float r2 = wheelR * 0.92F;
			if (draw.left() > 60) {
				draw.ring(hub, n2, r2 - s.r(0.012F), r2 + s.r(0.012F), 0.0F, AuraDraw.BAND, wheel, 0.22F * crossA);
			}
			for (int k = 0; k < SLOTS; k += 2) {
				Vec3 at = HighKit.onCircle(hub, plane2, r2, Math.toRadians(-t * TURN + k * 360.0F / SLOTS));
				if (!draw.billboard(at, s.r(0.09F), 0.0F, STAR, starColor(s, p, k + 1, t), 0.65F * crossA)) {
					return;
				}
			}
		}
	}
}
