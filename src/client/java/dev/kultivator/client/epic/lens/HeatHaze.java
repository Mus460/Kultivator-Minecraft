package dev.kultivator.client.epic.lens;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.epic.render.EpicRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Heat haze (DESIGN 30.2.7 HEAT_HAZE): <b>not refraction</b> (no post chain) but world-space shimmer quads standing over
 * hot ground: our scrolling {@code haze.png} at α 0.08 on {@code entityTranslucentEmissive}, turned about the vertical
 * axis to face the camera, plus a little rising vanilla white ash. Normal 2 quads, Maximal 4, Minimal none. Drawn from
 * {@code EpicRenderer} on {@code COLLECT_SUBMITS} (never in a shadow pass). Client thread only.
 */
public final class HeatHaze {

	private static final Identifier TEXTURE = Kultivator.id("textures/misc/film/haze.png");
	private static final EpicRenderer.Sink SINK = new EpicRenderer.Sink(RenderTypes.entityTranslucentEmissive(TEXTURE), true);
	private static final int ALPHA = 20;

	private static Vec3 at = Vec3.ZERO;
	private static float radius = 4.0F;
	private static int rgb = 0xFFD8A0;
	private static int left;
	private static long seed;

	/** Haze over {@code centre} (radius {@code r}) for {@code ticks}. */
	public static void show(Vec3 centre, float r, int ticks, int tint, long hazeSeed) {
		if (EpicBudget.tierOrMinimal() < 1) {
			return;
		}
		at = centre;
		radius = Math.max(2.0F, Math.min(12.0F, r));
		rgb = tint & 0xFFFFFF;
		left = Math.max(left, Math.min(200, ticks));
		seed = hazeSeed;
	}

	public static boolean active() {
		return left > 0;
	}

	public static void clear() {
		left = 0;
	}

	/** Once per client tick: the ash and the countdown. */
	public static void tick() {
		if (left <= 0) {
			return;
		}
		left--;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || EpicClock.frozen()) {
			return;
		}
		if ((left & 1) == 0) {
			long mix = (seed + left) * 0x9E3779B97F4A7C15L;
			double a = ((mix >>> 40) & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
			double r = ((mix >>> 24) & 0xFF) / 255.0D * radius;
			level.addParticle(ParticleTypes.WHITE_ASH, at.x + Math.cos(a) * r, at.y + 0.2D, at.z + Math.sin(a) * r, 0.0D, 0.04D, 0.0D);
		}
	}

	/** Writes and submits the quads (after the 7b sinks). */
	public static void render(EpicRenderer.Frame frame) {
		if (left <= 0) {
			return;
		}
		SINK.reset();
		int quads = EpicBudget.tierOrMinimal() >= 2 ? 4 : 2;
		// a ping-pong window over the texture keeps every v inside 0..1 (no wrap needed)
		float clock = (float) frame.clock;
		float fade = Math.min(1.0F, left / 20.0F);
		int color = (Math.round(ALPHA * fade) << 24) | rgb;
		for (int i = 0; i < quads; i++) {
			double a = Math.PI * 2.0D * i / quads + (seed & 7);
			double cx = at.x + Math.cos(a) * radius * 0.45D;
			double cz = at.z + Math.sin(a) * radius * 0.45D;
			float x = frame.rx(cx);
			float y = frame.ry(at.y);
			float z = frame.rz(cz);
			// face the camera about the vertical axis
			float dx = -x;
			float dz = -z;
			float len = (float) Math.sqrt(dx * dx + dz * dz);
			if (len < 0.01F) {
				continue;
			}
			float sx = -dz / len * radius * 0.6F;
			float sz = dx / len * radius * 0.6F;
			float h = 2.5F + radius * 0.25F;
			float v0 = 0.25F * (1.0F + (float) Math.sin(clock * 0.09F + i * 1.7F));
			SINK.vertex(x - sx, y, z - sz, 0.0F, v0 + 0.5F, color, 0.0F, 1.0F, 0.0F);
			SINK.vertex(x + sx, y, z + sz, 1.0F, v0 + 0.5F, color, 0.0F, 1.0F, 0.0F);
			SINK.vertex(x + sx, y + h, z + sz, 1.0F, v0, color, 0.0F, 1.0F, 0.0F);
			SINK.vertex(x - sx, y + h, z - sz, 0.0F, v0, color, 0.0F, 1.0F, 0.0F);
		}
		SINK.submit(frame.collector);
	}

	private HeatHaze() {
	}
}
