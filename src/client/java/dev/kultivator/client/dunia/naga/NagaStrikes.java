package dev.kultivator.client.dunia.naga;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.dunia.naga.NagaKind;
import dev.kultivator.dunia.naga.NagaPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The world-space telegraphs of the Jiao kind skills (detail pass of DESIGN 34.5.11), drawn where they happen rather
 * than on the dragon: Pusaran Samudra (three spiral arms of water light turning over a closing ring, then a churning
 * vortex), Hujan Petir Langit (a ring of light closes on each mark, a glyph turns in it, then a jagged bolt of light falls
 * from the sky with a ground flash) and Tirai Awan (a billowing sphere of cloud). Layered additive quads of the
 * {@code lightning} render type plus vanilla particles: the "heavy shader" look faked on a weak laptop (rule V2), no post
 * chain, no core shader; never in a shader pack's shadow pass. Budgets: quads from module B's share of the 7b frame cap
 * (whirl 48 / 80 / 112, mark 24 + 16 per bolt), particles scaled by EffectQuality and others' skills by
 * {@code othersScale}; at most 4 whirls, 16 marks, 4 veils. Client only; nothing touches the world.
 */
final class NagaStrikes {

	private static final RenderType GLOW = RenderTypes.lightning();
	private static final int MAX_WHIRLS = 4;
	private static final int MAX_MARKS = 16;
	private static final int MAX_VEILS = 4;
	/** A bolt lives this long after it falls. */
	private static final int BOLT_TICKS = 8;
	private static final int TELEGRAPH = 30;

	private static final class Whirl {
		final Vec3 pos;
		final float radius;
		final int life;
		final int rgb;
		final boolean own;
		int age;

		Whirl(Vec3 pos, float radius, int life, int rgb, boolean own) {
			this.pos = pos;
			this.radius = radius;
			this.life = life;
			this.rgb = rgb;
			this.own = own;
		}
	}

	private static final class Mark {
		final Vec3 pos;
		final float radius;
		final int strikeAt;
		final int rgb;
		final long seed;
		final boolean own;
		int age;

		Mark(Vec3 pos, float radius, int strikeAt, int rgb, long seed, boolean own) {
			this.pos = pos;
			this.radius = radius;
			this.strikeAt = strikeAt;
			this.rgb = rgb;
			this.seed = seed;
			this.own = own;
		}
	}

	private static final class Veil {
		final Vec3 pos;
		final float radius;
		final int life;
		final boolean own;
		int age;

		Veil(Vec3 pos, float radius, int life, boolean own) {
			this.pos = pos;
			this.radius = radius;
			this.life = life;
			this.own = own;
		}
	}

	private static final List<Whirl> WHIRLS = new ArrayList<>();
	private static final List<Mark> MARKS = new ArrayList<>();
	private static final List<Veil> VEILS = new ArrayList<>();
	private static @Nullable ClientLevel lastLevel;
	private static boolean warned;

	// ================================================================== from the moments

	static void whirl(NagaPayloads.Moment moment) {
		if (WHIRLS.size() >= MAX_WHIRLS) {
			WHIRLS.remove(0);
		}
		NagaKind kind = NagaKind.byOrdinal(moment.kind());
		WHIRLS.add(new Whirl(moment.pos(), Math.max(2.0F, moment.tier() / 10.0F), Math.max(TELEGRAPH + 10, moment.seed()), kind.glow,
				moment.own()));
	}

	static void mark(NagaPayloads.Moment moment) {
		if (MARKS.size() >= MAX_MARKS) {
			MARKS.remove(0);
		}
		NagaKind kind = NagaKind.byOrdinal(moment.kind());
		long seed = Double.doubleToLongBits(moment.x()) * 31L + Double.doubleToLongBits(moment.z());
		MARKS.add(new Mark(moment.pos(), Math.max(1.0F, moment.tier() / 10.0F), Math.max(1, moment.seed()), kind.glow, seed, moment.own()));
	}

	static void veil(NagaPayloads.Moment moment) {
		if (VEILS.size() >= MAX_VEILS) {
			VEILS.remove(0);
		}
		VEILS.add(new Veil(moment.pos(), Math.max(4.0F, moment.tier()), Math.max(20, moment.seed()), moment.own()));
	}

	static void clear() {
		WHIRLS.clear();
		MARKS.clear();
		VEILS.clear();
	}

	// ================================================================== the HUD's question

	/**
	 * Where the nearest telegraphed danger to {@code at} is (a thunder mark not yet fallen or a whirl about to pull, that
	 * {@code at} stands in or near), or null. Read by {@link NagaHud} for its warning.
	 */
	static @Nullable Vec3 threat(Vec3 at) {
		Vec3 best = null;
		double bestSq = Double.MAX_VALUE;
		for (Mark mark : MARKS) {
			if (mark.age >= mark.strikeAt) {
				continue;
			}
			double dx = at.x - mark.pos.x;
			double dz = at.z - mark.pos.z;
			double d = dx * dx + dz * dz;
			double r = mark.radius + 1.0D;
			if (d <= r * r && Math.abs(at.y - mark.pos.y) < 4.0D && d < bestSq) {
				bestSq = d;
				best = mark.pos;
			}
		}
		for (Whirl whirl : WHIRLS) {
			if (whirl.age >= TELEGRAPH) {
				continue;
			}
			double dx = at.x - whirl.pos.x;
			double dz = at.z - whirl.pos.z;
			double d = dx * dx + dz * dz;
			double r = whirl.radius + 1.0D;
			if (d <= r * r && Math.abs(at.y - whirl.pos.y) < 6.0D && d < bestSq) {
				bestSq = d;
				best = whirl.pos;
			}
		}
		return best;
	}

	// ================================================================== the client tick: ages and particles

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
		if (level == null || minecraft.isPaused() || WHIRLS.isEmpty() && MARKS.isEmpty() && VEILS.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		Iterator<Whirl> whirls = WHIRLS.iterator();
		while (whirls.hasNext()) {
			Whirl whirl = whirls.next();
			if (++whirl.age > whirl.life) {
				whirls.remove();
				continue;
			}
			float others = whirl.own ? 1.0F : EffectQuality.othersScale();
			if (whirl.age >= TELEGRAPH && others > 0.0F) {
				int n = Math.max(1, Math.round(EffectQuality.scale(4) * others));
				for (int i = 0; i < n; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double r = whirl.radius * (0.2D + 0.8D * random.nextDouble());
					Vec3 p = whirl.pos.add(Math.cos(angle) * r, 0.1D + random.nextDouble() * 0.5D, Math.sin(angle) * r);
					Vec3 swirl = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)).scale(0.25D).add(whirl.pos.subtract(p).scale(0.04D));
					CommonFx.add(random.nextInt(3) == 0 ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE_POP, p, swirl.add(0.0D, 0.08D, 0.0D), false);
				}
				if (EffectQuality.chance(random, 0.4F * others)) {
					CommonFx.add(CommonFx.glow(whirl.rgb, 1.2F, 16), whirl.pos.add(0.0D, 0.3D + random.nextDouble() * 2.5D, 0.0D),
							new Vec3(0.0D, 0.12D, 0.0D), false);
				}
			}
		}
		Iterator<Mark> marks = MARKS.iterator();
		while (marks.hasNext()) {
			Mark mark = marks.next();
			mark.age++;
			if (mark.age > mark.strikeAt + BOLT_TICKS) {
				marks.remove();
				continue;
			}
			float others = mark.own ? 1.0F : EffectQuality.othersScale();
			if (others <= 0.0F) {
				continue;
			}
			if (mark.age < mark.strikeAt) {
				if (EffectQuality.chance(random, 0.35F * others)) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					Vec3 p = mark.pos.add(Math.cos(angle) * mark.radius, 0.15D, Math.sin(angle) * mark.radius);
					CommonFx.add(ParticleTypes.ELECTRIC_SPARK, p, new Vec3(0.0D, 0.05D, 0.0D), false);
				}
			} else if (mark.age == mark.strikeAt) {
				CommonFx.burst(mark.pos.add(0.0D, 0.4D, 0.0D), mark.rgb, Math.round(18 * others), 1.8F);
				for (int i = 0; i < EffectQuality.scale(Math.round(10 * others)); i++) {
					CommonFx.add(ParticleTypes.ELECTRIC_SPARK, mark.pos.x, mark.pos.y + 0.2D, mark.pos.z, random.nextGaussian() * 0.35D,
							random.nextFloat() * 0.4D, random.nextGaussian() * 0.35D);
				}
			}
		}
		Iterator<Veil> veils = VEILS.iterator();
		while (veils.hasNext()) {
			Veil veil = veils.next();
			if (++veil.age > veil.life) {
				veils.remove();
				continue;
			}
			float others = veil.own ? 1.0F : EffectQuality.othersScale();
			if (others <= 0.0F) {
				continue;
			}
			// billowing out for a second, then a thinning haze
			float grow = Mth.clamp(veil.age / 20.0F, 0.0F, 1.0F);
			int n = veil.age <= 20 ? EffectQuality.minimal() ? 3 : EffectQuality.maximal() ? 14 : 8 : EffectQuality.minimal() ? 0 : 2;
			n = Math.round(n * others);
			for (int i = 0; i < n; i++) {
				Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6D, random.nextGaussian());
				if (dir.lengthSqr() < 1.0E-4D) {
					continue;
				}
				dir = dir.normalize();
				Vec3 p = veil.pos.add(dir.scale(veil.radius * (0.25D + 0.75D * grow) * (0.6D + 0.4D * random.nextDouble())));
				CommonFx.add(random.nextBoolean() ? ParticleTypes.CLOUD : CommonFx.glow(0xF4FFF8, 2.2F, 30), p, dir.scale(0.04D), false);
			}
		}
	}

	// ================================================================== the frame (Fabric LevelRenderEvents.COLLECT_SUBMITS)

	static void collect(LevelRenderContext context) {
		if (WHIRLS.isEmpty() && MARKS.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		long start = System.nanoTime();
		try {
			Vec3 cam = context.levelState().cameraRenderState.pos;
			float partial = context.levelState().worldPartialTicks;
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			int quality = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1;
			for (Whirl whirl : WHIRLS) {
				if (whirl.pos.distanceToSqr(cam) > 128.0D * 128.0D) {
					continue;
				}
				drawWhirl(poseStack, collector, whirl, cam, whirl.age + partial, quality);
			}
			for (Mark mark : MARKS) {
				if (mark.pos.distanceToSqr(cam) > 128.0D * 128.0D) {
					continue;
				}
				drawMark(poseStack, collector, mark, cam, mark.age + partial, quality);
			}
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Jiao skill telegraph skipped a frame", exception);
			}
		}
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	/** Three spiral arms over a ring (telegraph: faint, the ring closing; active: bright and turning fast, a core glow). */
	private static void drawWhirl(PoseStack poseStack, SubmitNodeCollector collector, Whirl whirl, Vec3 cam, float age, int quality) {
		int segments = quality == 0 ? 8 : quality == 2 ? 20 : 14;
		int ringSides = quality == 0 ? 16 : 24;
		int arms = 3;
		if (!NagaVisuals.takeQuads(arms * segments + ringSides * 2)) {
			return;
		}
		boolean active = age >= TELEGRAPH;
		float fadeIn = Mth.clamp(age / 8.0F, 0.0F, 1.0F);
		float fadeOut = Mth.clamp((whirl.life - age) / 12.0F, 0.0F, 1.0F);
		float strength = fadeIn * fadeOut * (active ? 1.0F : 0.45F + 0.35F * age / TELEGRAPH);
		float radius = whirl.radius;
		float ring = active ? radius : radius * (1.25F - 0.25F * age / TELEGRAPH);
		float spin = active ? age * 0.32F : age * 0.08F;
		Vec3 c = whirl.pos.subtract(cam).add(0.0D, 0.12D, 0.0D);
		int rgb = whirl.rgb;
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			for (int a = 0; a < arms; a++) {
				double base = spin + a * Math.PI * 2.0D / arms;
				for (int s = 0; s < segments; s++) {
					float t0 = s / (float) segments;
					float t1 = (s + 1) / (float) segments;
					double r0 = 0.5D + t0 * radius;
					double r1 = 0.5D + t1 * radius;
					double a0 = base + t0 * 2.6D;
					double a1 = base + t1 * 2.6D;
					double w0 = 0.25D + 0.55D * t0;
					double w1 = 0.25D + 0.55D * t1;
					double y0 = active ? 0.6D * (1.0D - t0) : 0.0D;
					double y1 = active ? 0.6D * (1.0D - t1) : 0.0D;
					int c0 = ARGB.color(Math.round(200 * strength * (1.0F - t0 * 0.6F)), rgb);
					int c1 = ARGB.color(Math.round(200 * strength * (1.0F - t1 * 0.6F)), rgb);
					Vec3 p0 = c.add(Math.cos(a0) * r0, y0, Math.sin(a0) * r0);
					Vec3 p1 = c.add(Math.cos(a1) * r1, y1, Math.sin(a1) * r1);
					Vec3 n0 = new Vec3(Math.cos(a0), 0.0D, Math.sin(a0)).scale(w0);
					Vec3 n1 = new Vec3(Math.cos(a1), 0.0D, Math.sin(a1)).scale(w1);
					NagaRenderer.vertex(pose, buffer, p0.subtract(n0), c0 & 0x00FFFFFF);
					NagaRenderer.vertex(pose, buffer, p0.add(n0), c0);
					NagaRenderer.vertex(pose, buffer, p1.add(n1), c1);
					NagaRenderer.vertex(pose, buffer, p1.subtract(n1), c1 & 0x00FFFFFF);
				}
			}
			ringQuads(pose, buffer, c, ring, active ? 0.5F : 0.3F, ringSides, ARGB.color(Math.round(230 * strength), rgb));
		});
		if (active && NagaVisuals.takeQuads(3)) {
			NagaRenderer.orb(poseStack, collector, c.add(0.0D, 0.8D, 0.0D), 1.4F, ARGB.color(Math.round(120 * strength), 0xE8FFFF));
		}
	}

	/** A ring closing on the mark with a turning glyph; when it falls, a jagged bolt from the sky and a ground flash. */
	private static void drawMark(PoseStack poseStack, SubmitNodeCollector collector, Mark mark, Vec3 cam, float age, int quality) {
		Vec3 c = mark.pos.subtract(cam).add(0.0D, 0.08D, 0.0D);
		int rgb = mark.rgb;
		float r = mark.radius;
		if (age < mark.strikeAt) {
			int sides = quality == 0 ? 12 : 20;
			if (!NagaVisuals.takeQuads(sides * 2 + 2)) {
				return;
			}
			float p = Mth.clamp(age / mark.strikeAt, 0.0F, 1.0F);
			float ring = r * (1.7F - 0.7F * p);
			int color = ARGB.color(Math.round(90 + 160 * p), rgb);
			float spin = age * 0.15F;
			collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
				ringQuads(pose, buffer, c, ring, 0.18F + 0.2F * p, sides, color);
				// the glyph: two crossed bars turning in the ring
				for (int k = 0; k < 2; k++) {
					double a = spin + k * Math.PI / 2.0D;
					Vec3 along = new Vec3(Math.cos(a), 0.0D, Math.sin(a)).scale(r * 0.8D);
					Vec3 across = new Vec3(-Math.sin(a), 0.0D, Math.cos(a)).scale(0.12D);
					int g = ARGB.color(Math.round(70 + 150 * p), 0xFFFFFF);
					NagaRenderer.vertex(pose, buffer, c.subtract(along).subtract(across), g);
					NagaRenderer.vertex(pose, buffer, c.subtract(along).add(across), g);
					NagaRenderer.vertex(pose, buffer, c.add(along).add(across), g);
					NagaRenderer.vertex(pose, buffer, c.add(along).subtract(across), g);
				}
			});
			return;
		}
		float since = age - mark.strikeAt;
		float fade = Mth.clamp(1.0F - since / BOLT_TICKS, 0.0F, 1.0F);
		int segments = quality == 0 ? 6 : 9;
		if (!NagaVisuals.takeQuads(segments * 4 + 24)) {
			return;
		}
		// the jagged path is fixed per mark (its seed) and flickers a little per tick
		RandomSource random = RandomSource.create(mark.seed + (long) Math.floor(since / 2.0F));
		Vec3[] points = new Vec3[segments + 1];
		Vec3 top = c.add(0.0D, 30.0D, 0.0D);
		for (int i = 0; i <= segments; i++) {
			double t = i / (double) segments;
			double jitter = i == 0 || i == segments ? 0.0D : 1.4D * (1.0D - t * 0.5D);
			points[i] = top.add(c.subtract(top).scale(t)).add((random.nextFloat() - 0.5F) * jitter * 2.0D, 0.0D,
					(random.nextFloat() - 0.5F) * jitter * 2.0D);
		}
		int glow = ARGB.color(Math.round(200 * fade), rgb);
		int core = ARGB.color(Math.round(255 * fade), 0xFFFFFF);
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			for (int i = 0; i < segments; i++) {
				ribbon(pose, buffer, points[i], points[i + 1], 0.9F, glow);
				ribbon(pose, buffer, points[i], points[i + 1], 0.28F, core);
			}
			ringQuads(pose, buffer, c, r * (1.0F + 0.6F * (1.0F - fade)), r * 0.9F, 24, ARGB.color(Math.round(180 * fade), rgb));
		});
	}

	/** Two crossed vertical quads from {@code a} to {@code b}, {@code width} wide, bright in the middle. */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, float width, int color) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-6D) {
			return;
		}
		Vec3 n = dir.normalize();
		Vec3 up = Math.abs(n.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 s1 = n.cross(up).normalize().scale(width * 0.5D);
		Vec3 s2 = n.cross(s1).normalize().scale(width * 0.5D);
		int edge = color & 0x00FFFFFF;
		for (Vec3 side : new Vec3[]{s1, s2}) {
			NagaRenderer.vertex(pose, buffer, a.subtract(side), edge);
			NagaRenderer.vertex(pose, buffer, a, color);
			NagaRenderer.vertex(pose, buffer, b, color);
			NagaRenderer.vertex(pose, buffer, b.subtract(side), edge);
			NagaRenderer.vertex(pose, buffer, a, color);
			NagaRenderer.vertex(pose, buffer, a.add(side), edge);
			NagaRenderer.vertex(pose, buffer, b.add(side), edge);
			NagaRenderer.vertex(pose, buffer, b, color);
		}
	}

	/** A flat ring band (soft both sides) at {@code center}. */
	private static void ringQuads(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, float radius, float band, int sides, int color) {
		float inner = Math.max(0.0F, radius - band * 0.5F);
		float outer = radius + band * 0.5F;
		int clear = color & 0x00FFFFFF;
		for (int s = 0; s < sides; s++) {
			double t0 = s / (double) sides * Math.PI * 2.0D;
			double t1 = (s + 1) / (double) sides * Math.PI * 2.0D;
			NagaRenderer.vertex(pose, buffer, center.add(Math.cos(t0) * inner, 0.0D, Math.sin(t0) * inner), clear);
			NagaRenderer.vertex(pose, buffer, center.add(Math.cos(t1) * inner, 0.0D, Math.sin(t1) * inner), clear);
			NagaRenderer.vertex(pose, buffer, center.add(Math.cos(t1) * radius, 0.0D, Math.sin(t1) * radius), color);
			NagaRenderer.vertex(pose, buffer, center.add(Math.cos(t0) * radius, 0.0D, Math.sin(t0) * radius), color);
			NagaRenderer.vertex(pose, buffer, center.add(Math.cos(t0) * radius, 0.0D, Math.sin(t0) * radius), color);
			NagaRenderer.vertex(pose, buffer, center.add(Math.cos(t1) * radius, 0.0D, Math.sin(t1) * radius), color);
			NagaRenderer.vertex(pose, buffer, center.add(Math.cos(t1) * outer, 0.0D, Math.sin(t1) * outer), clear);
			NagaRenderer.vertex(pose, buffer, center.add(Math.cos(t0) * outer, 0.0D, Math.sin(t0) * outer), clear);
		}
	}

	private NagaStrikes() {
	}
}
