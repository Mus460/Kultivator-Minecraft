package dev.kultivator.client.epic.render;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * G_AURORA (DESIGN 23.10.4): three rainbow ribbons spiralling up a pillar, radius {@code 2 + r_p}, 40 b × (1 + 0.1 S)
 * tall; the hues drift unless "Kurangi gerak" is on. Normal and up; one large layer (rank 8). Fallback: a rainbow dust
 * helix.
 */
public final class AuroraRenderer extends Element {

	private static final int[] RAINBOW = {0xFF3B3B, 0xFF8A1A, 0xFFD54A, 0x3BDB7A, 0x3BE0E0, 0x4A7BFF, 0xA65BFF};
	private static final int STEPS = 20;

	private final Vec3 base;
	private final float radius;
	private final float height;

	public AuroraRenderer(int delay, float life, Vec3 base, float radius, float height) {
		super(delay, life);
		this.base = base;
		this.radius = radius;
		this.height = height;
		this.largeRank = 8;
	}

	@Override
	public boolean geometry() {
		return true;
	}

	@Override
	protected void fallback() {
		if ((int) age % 3 == 0) {
			int rgb = RAINBOW[(int) (age / 3) % RAINBOW.length];
			EpicParticles.helix(base, Math.min(40.0F, height), radius, 12, age * 0.2D, EpicParticles.dust(rgb, 1.5F));
		}
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		if (EpicBudget.tier() < 1) {
			return;
		}
		if (!EpicBudget.takeQuads(STEPS * 3)) {
			return;
		}
		float a = fade(at, 20.0F, 30.0F) * 0.55F;
		if (a <= 0.01F) {
			return;
		}
		boolean still = EffectQuality.reduceMotion();
		double turn = still ? 0.0D : at * 0.05D;
		float drift = still ? 0.0F : at * 0.08F;
		float bx = frame.rx(base.x);
		float by = frame.ry(base.y);
		float bz = frame.rz(base.z);
		for (int ribbon = 0; ribbon < 3; ribbon++) {
			double phase = turn + ribbon * Math.PI * 2.0D / 3.0D;
			float px = 0.0F;
			float py = 0.0F;
			float pz = 0.0F;
			int pc = 0;
			for (int i = 0; i <= STEPS; i++) {
				float t = i / (float) STEPS;
				double angle = phase + t * Math.PI * 4.0D;
				float r = radius * (1.0F + 0.3F * t);
				float x = bx + (float) Math.cos(angle) * r;
				float y = by + t * height;
				float z = bz + (float) Math.sin(angle) * r;
				int index = Math.floorMod((int) (t * 7.0F + drift + ribbon * 2), RAINBOW.length);
				int color = ARGB.color(Math.round(a * 255.0F * Mth.sin((float) (t * Math.PI))), RAINBOW[index]);
				if (i > 0) {
					frame.segment(frame.glow, px, py, pz, x, y, z, 1.6F, 1.6F, pc, color, (i - 1) / (float) STEPS, t);
				}
				px = x;
				py = y;
				pz = z;
				pc = color;
			}
		}
	}
}
