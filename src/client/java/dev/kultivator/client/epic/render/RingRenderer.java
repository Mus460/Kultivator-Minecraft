package dev.kultivator.client.epic.render;

import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * G_RING (DESIGN 23.10.4): a flat annulus growing from {@code r0} to {@code r1} with an alpha fade
 * ({@code ring_soft.png}), or a turning rune circle ({@code dao_circle.png}); optional dust crown of the ground block.
 * A ring wider than 16 b is a large layer (rank 6). Fallback: a particle ring.
 */
public final class RingRenderer extends Element {

	private final Vec3 center;
	private final float r0;
	private final float r1;
	private final float width;
	private final float growTicks;
	private final int rgb;
	private final float alpha;
	private boolean rune;
	private float spin;
	private int crown;
	private boolean firedFallback;
	private float rise;

	/** A ring at {@code center} growing r0 → r1 over {@code growTicks}, band {@code width} wide (0 = a full disc band). */
	public RingRenderer(int delay, float life, Vec3 center, float r0, float r1, float width, float growTicks, int rgb, float alpha) {
		super(delay, life);
		this.center = center;
		this.r0 = r0;
		this.r1 = Math.min(96.0F, r1);
		this.width = width;
		this.growTicks = Math.max(1.0F, growTicks);
		this.rgb = rgb & 0xFFFFFF;
		this.alpha = alpha;
		this.largeRank = this.r1 > 16.0F ? 6 : 0;
	}

	/** Draws the turning rune circle instead of the soft band. */
	public RingRenderer rune(float degreesPerTick) {
		this.rune = true;
		this.spin = degreesPerTick;
		return this;
	}

	/** Adds a dust crown of {@code n} ground particles at the start. */
	public RingRenderer crown(int n) {
		this.crown = n;
		return this;
	}

	/** The ring climbs {@code blocks} over its life (Heaven Trampling steps). */
	public RingRenderer rise(float blocks) {
		this.rise = blocks;
		return this;
	}

	@Override
	protected void start() {
		if (crown > 0) {
			EpicParticles.dustCrown(center, Math.max(1.0F, r1 * 0.5F), crown);
		}
	}

	@Override
	public boolean geometry() {
		return true;
	}

	@Override
	protected void fallback() {
		if (!firedFallback || (int) age % 6 == 0) {
			firedFallback = true;
			float t = Math.min(1.0F, age / growTicks);
			float r = r0 + (r1 - r0) * t;
			EpicParticles.ring(center.add(0.0D, rise * progress(age), 0.0D), r, Math.min(64, 12 + Math.round(r * 2.0F)),
					EpicParticles.dust(rgb, 1.2F), 0.02F);
		}
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		float t = Math.min(1.0F, at / growTicks);
		float eased = 1.0F - (1.0F - t) * (1.0F - t);
		float r = r0 + (r1 - r0) * eased;
		float a = alpha * fade(at, 3.0F, Math.min(20.0F, life * 0.5F));
		if (a <= 0.01F || r <= 0.05F) {
			return;
		}
		float x = frame.rx(center.x);
		float y = frame.ry(center.y + 0.06D + rise * progress(at));
		float z = frame.rz(center.z);
		int color = ARGB.color(Math.round(a * 255.0F), rgb);
		if (rune) {
			if (!EpicBudget.takeQuads(1)) {
				return;
			}
			frame.flat(frame.rune, x, y, z, r, (float) Math.toRadians(at * spin), color);
			return;
		}
		int segments = r > 24.0F ? 48 : r > 8.0F ? 32 : 20;
		if (!EpicBudget.takeQuads(segments)) {
			return;
		}
		float inner = width <= 0.0F ? 0.0F : Math.max(0.0F, r - width);
		annulus(frame, frame.ring, x, y, z, inner, r, segments, ARGB.color(Math.round(a * 120.0F), rgb), color, (float) (frame.clock * 0.01D));
	}

	/** A flat annulus (normal up) from {@code inner} to {@code outer}, inner and outer colours, u around the ring. */
	public static void annulus(EpicRenderer.Frame frame, EpicRenderer.Sink sink, float x, float y, float z, float inner, float outer, int segments,
							   int innerColor, int outerColor, float uOffset) {
		float step = (float) (Math.PI * 2.0D / segments);
		float cos0 = 1.0F;
		float sin0 = 0.0F;
		float uScale = Math.max(1.0F, Math.round(outer / 4.0F));
		for (int i = 0; i < segments; i++) {
			float angle = (i + 1) * step;
			float cos1 = (float) Math.cos(angle);
			float sin1 = (float) Math.sin(angle);
			float u0 = i / (float) segments * uScale + uOffset;
			float u1 = (i + 1) / (float) segments * uScale + uOffset;
			sink.vertex(x + cos0 * inner, y, z + sin0 * inner, u0, 0.0F, innerColor, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + cos0 * outer, y, z + sin0 * outer, u0, 1.0F, outerColor, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + cos1 * outer, y, z + sin1 * outer, u1, 1.0F, outerColor, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + cos1 * inner, y, z + sin1 * inner, u1, 0.0F, innerColor, 0.0F, 1.0F, 0.0F);
			cos0 = cos1;
			sin0 = sin1;
		}
	}
}
