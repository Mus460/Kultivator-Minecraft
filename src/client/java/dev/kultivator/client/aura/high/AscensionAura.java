package dev.kultivator.client.aura.high;

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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immortal Ascension (DESIGN 31.4.8): the Immortal Gate opens 2.2 b above the cultivator and Immortal light pours down on
 * them; purple qi comes from the east (world +x) along the ground and rises into the dantian; heavenly flowers fall.
 * Layers: pillars of light round the body = 1 + (layer + 1) / 2 (1..5), one more lit stud on the gate's lintel per layer
 * (models {@code aura_ia_gate_l1..l9}, swapped once on a layer change) and a wider purple ribbon (× (1 + layer / 12)).
 * Stages: Awal the beam of light and the pillars; Tengah the purple-qi ribbon and the cloud-seal decal; Akhir the gate
 * prop and its halo ring; Puncak the gate opens (its doors become light), the auspicious cloud under it, petals fall.
 * <p>
 * Sumber: I Shall Seal the Heavens, bab 969; 紫气东来 (Liexian Zhuan); Sutra Vimalakirti, bab 7
 * (kultivator.aura.realm.immortal_ascension.source).
 */
final class AscensionAura implements AuraRealmDriver {

	private static final Identifier GATE_HALO = HighKit.tex("ia_gate");
	private static final Identifier CLOUD_SEAL = HighKit.tex("decal_cloud_seal");
	private static final int PURPLE = 0xB07CFF;
	private static final int PURPLE_LIGHT = 0xE2C8FF;
	private static final int GATE_RIM = 0xFFE9A8;
	private static final int PETAL = 0xFFD8EC;
	/** The gate floats this far above the crown (blocks at scale 1). */
	private static final float GATE_UP = 2.2F;
	/** The gate model's middle is 1.9 px above its anchor. */
	private static final float GATE_MID_PX = 1.9F;
	/** The gate's lowest point (-5 px) and the cloud's highest (10.8 px), from the generator. */
	private static final float GATE_LOW_PX = -5.0F;
	private static final float CLOUD_TOP_PX = 10.8F;
	private static final String GATE = "ia.gate";
	private static final String CLOUD = "ia.cloud";

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
			hum = new AuraHum.Spec(SoundEvents.BEACON_AMBIENT, 0.07F, 1.5F, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.8F, 100);
		}
		return hum;
	}

	@Override
	public float reach(AuraSubject s) {
		return 1.5F + 0.05F * s.stage();
	}

	// ================================================================== shapes

	/** Pillars of light for this layer (0-based): 1 + (layer + 1) / 2. */
	private static int pillars(AuraSubject s) {
		return 1 + (Mth.clamp(s.layer(), 0, 8) + 1) / 2;
	}

	/** The middle of the gate above the crown. */
	private static Vec3 gateCentre(AuraSubject s) {
		return s.crown().add(0.0D, s.r(GATE_UP), 0.0D);
	}

	/**
	 * The gate prop's scale: 1.1 at Akhir, 1.35 at Puncak, + 0.35 over the nine layers. The model is 28 px wide, so this is
	 * 31.4.8's 1.4 → 2.4 of a 20 px gate (1.5 b to 3 b across).
	 */
	private static float gateScale(AuraSubject s) {
		return s.r((s.stage() >= 3 ? 1.35F : 1.1F) + 0.35F * s.layerGrow());
	}

	/** The purple qi ribbon from the east: head at the dantian, then down to the ground and away toward +x. */
	private static List<Vec3> ribbon(AuraSubject s, float t, float swing, float phase) {
		int n = 12;
		List<Vec3> points = new ArrayList<>(n);
		Vec3 feet = s.feet();
		double top = s.dantian().y;
		double floor = feet.y + s.r(0.18F);
		double cos = Math.cos(Math.toRadians(swing));
		double sin = Math.sin(Math.toRadians(swing));
		for (int i = 0; i < n; i++) {
			float q = i / (float) (n - 1);
			double out = s.r(0.12F + 4.2F * (float) Math.pow(q, 1.25D));
			// a wave travelling inward along the stream (constant phase moves toward q = 0)
			double side = s.r(0.35F) * q * Math.sin(q * 5.0D + t * 0.08D + phase);
			double x = out * cos - side * sin;
			double z = out * sin + side * cos;
			double y = Mth.lerp(HighKit.smooth(q / 0.35F), top, floor) + s.r(0.06F) * Math.sin(q * 9.0D + t * 0.05D + phase);
			points.add(new Vec3(feet.x + x, y, feet.z + z));
		}
		return points;
	}

	// ================================================================== props

	private void props(AuraSubject s, AuraRig rig) {
		int stage = s.stage();
		boolean show = !s.essentials() && !s.firstPersonSelf() && s.entity() != null;
		rig.sweepBegin();
		if (show && stage >= 2) {
			int studs = Mth.clamp(s.layer(), 0, 8) + 1;
			String model = (stage >= 3 ? "aura_ia_gate_open_l" : "aura_ia_gate_l") + studs;
			float scale = gateScale(s);
			double anchor = HighKit.above(s, gateCentre(s)) - GATE_MID_PX / 16.0F * scale;
			Vec3 local = new Vec3(0.0D, anchor, 0.0D);
			rig.prop(GATE, () -> HighKit.follow(s, model, scale, local, 0.0F, 0.0F, 0.0F));
			rig.propModel(GATE, model);
			rig.propScale(GATE, scale);
			HighKit.keepOffset(rig, GATE, s, local);
			if (stage >= 3) {
				// the auspicious cloud under the open gate
				float cs = s.r(1.5F);
				double bottom = anchor + (GATE_LOW_PX - 8.0F) / 16.0F * scale;
				Vec3 cloudAt = new Vec3(0.0D, bottom - (CLOUD_TOP_PX - 8.0F) / 16.0F * cs + s.r(0.08F), 0.0D);
				rig.prop(CLOUD, () -> HighKit.follow(s, "aura_ia_cloud", cs, cloudAt, 0.0F, 0.0F, 0.0F));
				HighKit.keepOffset(rig, CLOUD, s, cloudAt);
			}
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
		// glints rising in the pillars of light
		int n = pillars(s);
		int k = (int) (AuraRng.of(id, clock, 71) * n) % n;
		double angle = Math.toRadians(t * 0.75F + k * 360.0F / n);
		double pr = s.r(1.3F + 0.05F * stage);
		Vec3 base = s.feet().add(Math.cos(angle) * pr, s.r(0.1F), Math.sin(angle) * pr);
		AuraParticles.glint(s, base, new Vec3(0.0D, 0.07D * s.scale(), 0.0D), AuraColors.lighten(p.body(), 0.3F), s.r(0.14F), 26);
		if (stage >= 1 && clock % 2L == 0L) {
			// purple mist along the stream from the east
			List<Vec3> stream = ribbon(s, t, 0.0F, 0.0F);
			int i = 2 + (int) (AuraRng.of(id, clock, 72) * (stream.size() - 2));
			Vec3 at = stream.get(Math.min(stream.size() - 1, i));
			Vec3 vel = stream.get(i - 1).subtract(at).scale(0.12D);
			AuraParticles.mist(s, at, vel, AuraColors.mix(PURPLE, PURPLE_LIGHT, AuraRng.of(id, clock, 73)), s.r(0.35F), 30);
		}
		if (stage >= 3) {
			// heavenly flowers falling round the body
			double a = AuraRng.of(id, clock, 74) * 360.0D;
			double r = s.r(0.3F + 1.3F * AuraRng.of(id, clock, 75));
			Vec3 at = s.around(a, r, s.height() + s.r(0.6F + 0.8F * AuraRng.of(id, clock, 76)));
			Vec3 vel = new Vec3(AuraRng.signed(id, clock, 77) * 0.01D, -0.025D * s.scale(), AuraRng.signed(id, clock, 78) * 0.01D);
			AuraParticles.petal(s, at, vel, PETAL, s.r(0.16F), 60);
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
		float grow = rig.ease("ia.grow", s.stageGrow(), HighKit.EASE);
		float layers = rig.ease("ia.layer", s.layerGrow(), HighKit.EASE);
		float decalA = HighKit.appear(rig, "ia.decal", s, 1);
		float streamA = HighKit.appear(rig, "ia.stream", s, 1);
		float gateA = HighKit.appear(rig, "ia.gate", s, 2);
		float breath = HighKit.wave(t, 100.0F, 0.0F);
		int light = p.body();
		int gateRim = AuraColors.mix(GATE_RIM, p.tint(), 0.2F);
		Vec3 gate = gateCentre(s);

		// the cloud seal under the seat (Tengah on)
		if (decalA > 0.01F) {
			draw.decal(s.r(1.8F), t * 0.1F, CLOUD_SEAL, p.ring(), 0.5F * decalA);
		}
		// the immortal light pouring from the gate onto the body (the identity, alpha 0.2-0.35)
		draw.beam(gate, s.feet(), s.r(0.4F + 0.08F * stage), light, 0.2F + 0.15F * breath);
		draw.glow(s.chest(), s.r(0.6F + 0.35F * grow), AuraColors.lighten(p.glow(), 0.2F), 0.2F + 0.05F * breath);
		if (s.reduced()) {
			return;
		}
		// the gate halo (Akhir on): a ring of light behind the gate, always facing the viewer
		if (gateA > 0.01F && draw.large()) {
			draw.billboard(gate, s.r(1.0F + 0.25F * Math.max(0, stage - 2) + 0.15F * layers), t * 0.3F, GATE_HALO, gateRim, 0.55F * gateA);
		}
		if (s.simple()) {
			return;
		}
		// pillars of light circling the body, one more for every second layer
		float count = rig.ease("ia.pillars", pillars(s), HighKit.EASE);
		int shown = Mth.ceil(count);
		double pr = s.r(1.3F + 0.05F * stage);
		double ph = s.r(2.2F) + s.r(0.15F) * Mth.clamp(s.layer(), 0, 8);
		for (int k = 0; k < shown; k++) {
			float a = Mth.clamp(count - k, 0.0F, 1.0F);
			double angle = Math.toRadians(t * 0.75F + k * 360.0F / Math.max(1.0F, count));
			Vec3 base = s.feet().add(Math.cos(angle) * pr, 0.05D, Math.sin(angle) * pr);
			float pulse = HighKit.wave(t, 80.0F, k * 16.0F);
			if (!draw.beam(base, base.add(0.0D, ph, 0.0D), s.r(0.12F), light, (0.25F + 0.1F * pulse) * a)) {
				return;
			}
			if (s.onGround()) {
				draw.glow(base, s.r(0.3F), light, 0.3F * a);
			}
		}
		// the purple qi coming from the east, along the ground and up into the dantian (Tengah on)
		if (streamA > 0.01F) {
			float width = s.r(0.2F) * (1.0F + Mth.clamp(s.layer(), 0, 8) / 12.0F);
			if (draw.ribbon(ribbon(s, t, 0.0F, 0.0F), width, width * 0.35F, PURPLE_LIGHT, PURPLE, 0.7F * streamA)) {
				// a second, thinner stream from a little north of east where the ribbon budget allows
				draw.ribbon(ribbon(s, t, -25.0F, 2.1F), width * 0.6F, width * 0.2F, PURPLE_LIGHT, PURPLE, 0.45F * streamA);
			}
		}
	}
}
