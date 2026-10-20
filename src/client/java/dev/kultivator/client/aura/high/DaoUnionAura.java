package dev.kultivator.client.aura.high;

import dev.kultivator.client.aura.AuraColors;
import dev.kultivator.client.aura.AuraDraw;
import dev.kultivator.client.aura.AuraHum;
import dev.kultivator.client.aura.AuraParticles;
import dev.kultivator.client.aura.AuraRealmDriver;
import dev.kultivator.client.aura.AuraRig;
import dev.kultivator.client.aura.AuraRng;
import dev.kultivator.client.aura.AuraSubject;
import dev.kultivator.data.Element;
import dev.kultivator.fx.FilmParticles;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dao Union (DESIGN 31.4.7): the Dao and the self become one. Dao runes (道 一 二 三 天 地 人 合) orbit the cultivator on
 * up to three tilted rings, in the colours of their root's elements, and slowly fuse toward the Dao colour; a light and a
 * dark comet chase each other round the waist (yin and yang); a taiji disc turns just above a five-element decal whose
 * generating cycle turns around it. Stages: Awal one ring of three runes and the comets; Tengah two rings (five runes),
 * the taiji prop and the decal; Akhir three rings (eight runes), the bronze rune-ring prop, runes 25 % fused; Puncak 60 %
 * fused, and every 60 ticks one rune (in a fixed order) flies into the core with a soft flash and comes back.
 * <p>
 * Sumber: Daodejing, bab 42; Taijitu Shuo (kultivator.aura.realm.dao_union.source).
 */
final class DaoUnionAura implements AuraRealmDriver {

	private static final String[] RUNE_NAMES = {"dao", "yi", "er", "san", "tian", "di", "ren", "he"};
	private static final Identifier[] RUNES = new Identifier[RUNE_NAMES.length];
	private static final Identifier WUXING = HighKit.tex("decal_wuxing");
	private static final int YANG = 0xF6FFF9;
	private static final int YIN = 0x16201C;
	/** The three rune rings: radius (blocks at scale 1), tilt, the side the tilt leans to (degrees from the body's facing), turn rate. */
	private static final float[] RING_R = {1.1F, 1.35F, 1.6F};
	private static final float[] RING_TILT = {18.0F, -24.0F, 30.0F};
	private static final float[] RING_LEAN = {0.0F, 60.0F, 120.0F};
	private static final float[] RING_SPEED = {1.2F, -0.9F, 0.7F};
	/** Runes on each ring by stage, and which rune sits in each slot (the Daodejing order). */
	private static final int[][] RING_COUNT = {{3, 0, 0}, {3, 2, 0}, {3, 3, 2}, {3, 3, 2}};
	private static final int[][] RING_RUNES = {{0, 1, 2}, {3, 4, 5}, {6, 7}};
	/** Fusion of the rune colours toward the Dao colour by stage. */
	private static final float[] FUSION = {0.0F, 0.0F, 0.25F, 0.6F};
	/** The generating cycle of the five elements, in order round the decal (wood feeds fire feeds earth...). */
	private static final Element[] SHENG = {Element.WOOD, Element.FIRE, Element.EARTH, Element.METAL, Element.WATER};
	/** Puncak: one rune flies into the core every this many ticks. */
	private static final int FLIGHT = 60;
	private static final String TAIJI = "du.taiji";
	private static final String RUNE_RING = "du.ring";

	static {
		for (int i = 0; i < RUNE_NAMES.length; i++) {
			RUNES[i] = HighKit.tex("du_rune_" + RUNE_NAMES[i]);
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
			hum = new AuraHum.Spec(SoundEvents.CONDUIT_AMBIENT, 0.07F, 0.7F, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 90);
		}
		return hum;
	}

	@Override
	public float reach(AuraSubject s) {
		int rings = s.stage() >= 2 ? 3 : s.stage() == 1 ? 2 : 1;
		return RING_R[rings - 1] + 0.15F;
	}

	// ================================================================== props

	private void props(AuraSubject s, AuraRig rig) {
		int stage = s.stage();
		boolean show = !s.essentials() && !s.firstPersonSelf() && s.entity() != null;
		rig.sweepBegin();
		if (show && stage >= 1) {
			// the taiji disc just above the decal (its lowest point is 0.2 px under the anchor)
			float scale = s.r(1.6F);
			Vec3 local = new Vec3(0.0D, s.r(0.06F) + scale * 0.0125F, 0.0D);
			rig.prop(TAIJI, () -> HighKit.follow(s, "aura_du_taiji", scale, local, 0.0F, 0.0F, 0.0F, HighKit.spin(0.8F, 0)));
			rig.propScale(TAIJI, scale);
		}
		if (show && stage >= 2) {
			// the bronze ring of eight rune plates at the waist, turning the other way
			float scale = s.r(1.8F);
			Vec3 local = new Vec3(0.0D, HighKit.above(s, s.dantian()) + s.r(0.1F), 0.0D);
			rig.prop(RUNE_RING, () -> HighKit.follow(s, "aura_du_rune_ring", scale, local, 0.0F, 0.0F, 0.0F, HighKit.spin(-0.5F, 0)));
			rig.propScale(RUNE_RING, scale);
			HighKit.keepOffset(rig, RUNE_RING, s, local);
		}
		rig.sweepEnd(10);
	}

	// ================================================================== shared shapes

	/** The centre of the rune rings (the waist). */
	private static Vec3 waist(AuraSubject s) {
		return s.dantian().add(0.0D, s.r(0.15F), 0.0D);
	}

	/** The plane of rune ring {@code k}. */
	private static Vec3[] plane(AuraSubject s, int k) {
		return HighKit.basis(HighKit.tilted(HighKit.dir(s.bodyYaw() + RING_LEAN[k]), RING_TILT[k]));
	}

	/** The colour of rune slot {@code slot} (its element, fused toward the Dao colour by {@code fusion}). */
	private static int runeColor(AuraColors.Palette p, int slot, float fusion) {
		int[] elements = p.elements();
		int el = elements.length == 0 ? p.tint() : elements[slot % elements.length];
		return AuraColors.mix(el, p.tint(), fusion);
	}

	/** The colour of the fused core: the elements averaged, then mixed half toward the Dao colour. */
	private static int fused(AuraColors.Palette p) {
		int[] elements = p.elements();
		if (elements.length == 0) {
			return p.tint();
		}
		int r = 0;
		int g = 0;
		int b = 0;
		for (int c : elements) {
			r += (c >> 16) & 0xFF;
			g += (c >> 8) & 0xFF;
			b += c & 0xFF;
		}
		int n = elements.length;
		int avg = ((r / n) << 16) | ((g / n) << 8) | (b / n);
		return AuraColors.mix(avg, p.tint(), 0.5F);
	}

	// ================================================================== particles

	private void particles(AuraSubject s, AuraRig rig) {
		if (s.firstPersonSelf()) {
			return;
		}
		long clock = rig.clock();
		int id = s.entityId();
		AuraColors.Palette p = s.colors();
		Vec3 core = s.dantian();
		int motes = 1 + s.stage() / 2;
		for (int i = 0; i < motes; i++) {
			// glyph motes in the rune colours drifting into the core
			double angle = AuraRng.of(id, clock, 41 + i) * 360.0D;
			Vec3 from = s.around(angle, s.r(1.6F), HighKit.above(s, core) + s.r(0.6F) * AuraRng.signed(id, clock, 51 + i));
			int rgb = runeColor(p, (int) (AuraRng.of(id, clock, 61 + i) * 8.0F), FUSION[s.stage()]);
			Vec3 vel = core.subtract(from).scale(1.0D / 30.0D);
			if (!AuraParticles.film(s, FilmParticles.SYMBOL, from, vel, AuraColors.lighten(rgb, 0.2F), s.r(0.2F), 30)) {
				break;
			}
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
		float grow = rig.ease("du.grow", s.stageGrow(), HighKit.EASE);
		float fusion = rig.ease("du.fusion", FUSION[stage], HighKit.EASE);
		float decalA = HighKit.appear(rig, "du.decal", s, 1);
		float breath = HighKit.wave(t, 90.0F, 0.0F);
		int coreRgb = fused(p);
		Vec3 core = s.dantian();

		// the five-element decal and its generating cycle turning round it (Tengah on)
		float decalR = s.r(1.6F);
		if (decalA > 0.01F) {
			draw.decal(decalR, t * 0.15F, WUXING, p.ring(), 0.55F * decalA);
			if (s.onGround() && !s.reduced()) {
				Vec3 floor = s.feet().add(0.0D, 0.06D, 0.0D);
				for (int k = 0; k < SHENG.length; k++) {
					double a = Math.toRadians(t * 0.5F + k * 72.0F);
					Vec3 at = floor.add(Math.cos(a) * decalR * 0.62D, 0.0D, Math.sin(a) * decalR * 0.62D);
					draw.glow(at, s.r(0.16F), SHENG[k].color() & 0xFFFFFF, 0.5F * decalA);
				}
			}
		}
		// the fused core at the dantian (the identity at reduced LOD)
		if (draw.large()) {
			draw.glow(s.chest(), s.r(0.6F + 0.35F * grow), p.glow(), 0.16F + 0.05F * breath);
		}
		draw.glowFront(core, s.r(0.16F + 0.05F * stage), coreRgb, 0.5F + 0.15F * breath, 0.3F);
		if (s.reduced()) {
			return;
		}
		// yin and yang: a light and a dark comet chasing each other round the waist
		Vec3 waist = waist(s);
		double cometR = s.r(0.9F);
		for (int fish = 0; fish < 2; fish++) {
			for (int tail = 0; tail < 4; tail++) {
				double a = Math.toRadians((t - tail * 1.6F) * 6.3F) + fish * Math.PI;
				Vec3 at = waist.add(Math.cos(a) * cometR, Math.sin(a) * s.r(0.3F), Math.sin(a) * cometR);
				float size = s.r(tail == 0 ? 0.13F : 0.11F - 0.02F * tail);
				draw.glow(at, size, fish == 0 ? YANG : YIN, (fish == 0 ? 0.85F : 0.7F) * (1.0F - tail * 0.22F));
			}
		}
		// the rune rings
		int flying = stage == 3 ? Mth.floor(t / FLIGHT) % 8 : -1;
		float flightP = (t % FLIGHT) / FLIGHT;
		boolean trails = !s.essentials();
		int slot = 0;
		for (int k = 0; k < 3; k++) {
			float count = rig.ease("du.n" + k, RING_COUNT[stage][k], HighKit.EASE);
			int shown = Mth.ceil(count);
			if (shown <= 0) {
				slot += RING_RUNES[k].length;
				continue;
			}
			Vec3[] plane = plane(s, k);
			double r = s.r(RING_R[k]);
			if (!s.essentials() && draw.left() > 90) {
				draw.ring(waist, HighKit.tilted(HighKit.dir(s.bodyYaw() + RING_LEAN[k]), RING_TILT[k]), (float) r - s.r(0.012F),
						(float) r + s.r(0.012F), 0.0F, AuraDraw.BAND, p.ring(), 0.2F * Math.min(1.0F, count));
			}
			for (int i = 0; i < shown && i < RING_RUNES[k].length; i++) {
				int rune = RING_RUNES[k][i];
				float a = Mth.clamp(count - i, 0.0F, 1.0F);
				double angle = Math.toRadians(t * RING_SPEED[k] + i * 360.0F / Math.max(1.0F, count));
				Vec3 at = HighKit.onCircle(waist, plane, r, angle);
				int rgb = runeColor(p, slot + i, fusion);
				if (rune == flying) {
					// Puncak: this rune flies into the core, flashes, and comes back to its place
					if (flightP < 0.4F) {
						at = at.lerp(core, HighKit.easeIn(flightP / 0.4F));
					} else if (flightP < 0.55F) {
						a = 0.0F;
					} else {
						a *= (flightP - 0.55F) / 0.45F;
					}
					float flash = Math.max(0.0F, 1.0F - Math.abs(flightP - 0.42F) / 0.12F);
					if (flash > 0.0F) {
						draw.glowFront(core, s.r(0.45F), AuraColors.lighten(coreRgb, 0.5F), 0.6F * flash, 0.3F);
					}
				}
				if (a <= 0.01F) {
					continue;
				}
				draw.glow(at, s.r(0.24F), rgb, 0.3F * a);
				if (!draw.billboard(at, s.r(0.15F), 0.0F, RUNES[rune], AuraColors.lighten(rgb, 0.35F), 0.9F * a)) {
					return;
				}
				if (trails && rune != flying) {
					// a short trail behind the rune along its orbit
					List<Vec3> points = new ArrayList<>(6);
					double back = Math.signum(RING_SPEED[k]) * 0.08D;
					for (int j = 0; j < 6; j++) {
						points.add(HighKit.onCircle(waist, plane, r, angle - back * j));
					}
					trails = draw.ribbon(points, s.r(0.05F), s.r(0.01F), rgb, p.body(), 0.55F * a);
				}
			}
			slot += RING_RUNES[k].length;
		}
	}
}
