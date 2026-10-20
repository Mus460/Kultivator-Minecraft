package dev.kultivator.client.aura;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The parts every aura shares, drawn by the engine after the realm driver (DESIGN 31.2.2, 31.3), so the drivers of both
 * modules get them alike:
 * <ul>
 * <li>the accent (a Heavenly root, a disciple's special physique, a forced preview accent): a thin outer ring at
 * 1.15 × the realm's reach turning 0.6°/t, three sparks orbiting the dantian at r 0.5 (period 60 t), and for a Heavenly
 * root a second, gilded outer line on the ground;</li>
 * <li>breakthrough ready: an amber rim glow whose alpha follows {@code 0.25 + 0.2 × (1 − cos(2πt/30)) / 2} (never a size
 * change) and thin red crack lines fading in on the ground;</li>
 * <li>a stage or layer change: one ring of light rolling out of the decal over 20 ticks (the only "swell").</li>
 * </ul>
 */
final class AuraOverlays {

	static final Identifier CRACK = AuraDraw.tex("decal_crack");
	private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

	static void draw(AuraSubject s, AuraRig rig, AuraDraw draw, float reachBlocks) {
		float t = rig.time();
		float reach = s.r(Math.max(0.4F, reachBlocks));
		AuraColors.Palette palette = s.colors();
		Vec3 ground = s.feet().add(0.0D, 0.05D, 0.0D);

		// a stage (or layer) change: one ring of light rolls out of the decal
		int ago = rig.stageChangedAgo();
		if (ago < 20 && !s.reduced()) {
			float p = Math.min(1.0F, (ago + rig.partial()) / 20.0F);
			float eased = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
			float r = s.r(0.3F) + (reach * 1.35F - s.r(0.3F)) * eased;
			float alpha = 0.75F * (1.0F - p) * (1.0F - p);
			draw.ring(ground.add(0.0D, 0.01D, 0.0D), UP, r * 0.82F, r, 0.0F, AuraDraw.BAND, AuraColors.lighten(palette.ring(), 0.45F), alpha);
			draw.glow(s.dantian(), s.r(0.55F) * (1.0F - 0.4F * p), AuraColors.lighten(palette.glow(), 0.5F), 0.35F * (1.0F - p));
		}

		// breakthrough ready: the amber warning pulse (brightness only) and crack lines on the ground
		float crack = rig.ease("~warn", s.bottleneck() ? 1.0F : 0.0F, 30);
		if (crack > 0.01F) {
			double wave = (1.0D - Math.cos(Math.PI * 2.0D * (t % 30.0F) / 30.0D)) / 2.0D;
			float pulse = (float) wave;
			float alpha = (0.25F + 0.2F * pulse) * crack;
			int warn = palette.warn(pulse);
			if (draw.large()) {
				draw.glow(s.chest(), reach * 1.05F, warn, alpha * 0.55F);
			}
			draw.glowFront(s.dantian(), s.r(0.42F), warn, alpha, 0.3F);
			if (!s.essentials()) {
				draw.ring(ground, UP, reach * 0.92F, reach * 1.02F, -t * 0.4F, AuraDraw.BAND, warn, alpha * 0.9F);
			}
			draw.decal(reach * 0.95F, 17.0F, CRACK, AuraColors.mix(0xFF3A2A, warn, 0.3F), 0.55F * crack * (0.75F + 0.25F * pulse));
		}

		// the accent: a thin turning outer ring, three sparks round the dantian, a gilded second line for Heavenly roots
		if (palette.hasAccent() && !s.essentials()) {
			int accent = palette.accent();
			float r = reach * 1.15F;
			draw.ring(ground, UP, r - s.r(0.05F), r + s.r(0.05F), t * 0.6F, AuraDraw.BAND, accent, 0.45F);
			if (palette.heavenly()) {
				float r2 = r + s.r(0.16F);
				draw.ring(ground, UP, r2 - s.r(0.03F), r2 + s.r(0.03F), -t * 0.3F, AuraDraw.BAND, AuraColors.lighten(accent, 0.25F), 0.35F);
			}
			Vec3 dantian = s.dantian();
			for (int i = 0; i < 3; i++) {
				double angle = Math.PI * 2.0D * ((t % 60.0F) / 60.0D + i / 3.0D);
				double bob = Math.sin(angle * 2.0D) * 0.08D * s.scale();
				Vec3 at = dantian.add(Math.cos(angle) * s.r(0.5F), bob, Math.sin(angle) * s.r(0.5F));
				draw.glow(at, s.r(0.1F), accent, 0.7F);
				draw.glow(at, s.r(0.035F), 0xFFFFFF, 0.9F);
			}
		}
	}

	private AuraOverlays() {
	}
}
