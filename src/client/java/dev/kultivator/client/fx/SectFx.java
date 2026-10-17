package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sparring (Latih Tanding) effects. {@code SPARRING} with b = 1: the duel starts; pos = player A, target = player B,
 * a = A's colour, f = B's colour (an RGB value is exact in a float). Two vertical rings face each other in front of the
 * duelists, their colours clash in sparks at the middle and a vertical streak of light rises between them.
 * b = 0: the duel ends; a = the winner's colour with pos = winner (a burst and a rising ring), or a = -1 without a
 * winner (a soft grey ring at both players).
 */
public final class SectFx {

	private static final int START_TICKS = 30;
	private static final int END_TICKS = 24;

	private enum Kind {
		START, VICTORY, DRAW
	}

	private static final class Active {
		final Kind kind;
		final Vec3 a;
		final Vec3 b;
		final int colorA;
		final int colorB;
		int age;

		Active(Kind kind, Vec3 a, Vec3 b, int colorA, int colorB) {
			this.kind = kind;
			this.a = a;
			this.b = b;
			this.colorA = colorA;
			this.colorB = colorB;
		}
	}

	private static final List<Active> ACTIVE = new ArrayList<>();
	private static long lastTickTime = Long.MIN_VALUE;
	private static ClientLevel lastLevel;
	private static boolean hooked;

	public static void play(FxType type, Payloads.Fx fx) {
		hook();
		ClientLevel level = CommonFx.level();
		resetIfNewLevel(level);
		if (type != FxType.SPARRING || level == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		if (fx.b() == 1) {
			int colorA = fx.a() & 0xFFFFFF;
			int colorB = clash(colorA, ((int) fx.f()) & 0xFFFFFF);
			startBurst(pos, target, colorA, colorB);
			ACTIVE.add(new Active(Kind.START, pos, target, colorA, colorB));
		} else if (fx.a() >= 0) {
			int color = fx.a() & 0xFFFFFF;
			victoryBurst(pos, color);
			ACTIVE.add(new Active(Kind.VICTORY, pos, target, color, color));
		} else {
			drawPuff(pos, target);
			ACTIVE.add(new Active(Kind.DRAW, pos, target, 0xC8C8C8, 0xC8C8C8));
		}
	}

	/** Every client tick (only while in a world and not paused). Safe to call more than once per tick. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		resetIfNewLevel(level);
		if (level == null || ACTIVE.isEmpty() || minecraft.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		if (now == lastTickTime) {
			return;
		}
		lastTickTime = now;
		Iterator<Active> iterator = ACTIVE.iterator();
		while (iterator.hasNext()) {
			Active active = iterator.next();
			active.age++;
			switch (active.kind) {
				case START -> tickStart(active);
				case VICTORY -> tickVictory(active);
				case DRAW -> tickDraw(active);
			}
			if (active.age >= (active.kind == Kind.START ? START_TICKS : END_TICKS)) {
				iterator.remove();
			}
		}
	}

	/** The dispatcher does not tick this class yet, so it hooks itself in on first use (the same-tick guard stops doubles). */
	private static void hook() {
		if (!hooked) {
			hooked = true;
			ClientTickEvents.END_CLIENT_TICK.register(SectFx::tick);
		}
	}

	private static void resetIfNewLevel(ClientLevel level) {
		if (level != lastLevel) {
			lastLevel = level;
			ACTIVE.clear();
			lastTickTime = Long.MIN_VALUE;
		}
	}

	// ================================================================== duel start

	private static void startBurst(Vec3 a, Vec3 b, int colorA, int colorB) {
		RandomSource random = CommonFx.random();
		Vec3 mid = middle(a, b);
		// Sparks where the two auras meet.
		int sparks = EffectQuality.scale(24);
		for (int i = 0; i < sparks; i++) {
			Vec3 velocity = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6D, random.nextGaussian()).scale(0.18D);
			CommonFx.add(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT, mid, velocity, true);
		}
		CommonFx.burst(mid, colorA, 14, 1.2F);
		CommonFx.burst(mid, colorB, 14, 1.2F);
		// A ring on the ground under each duelist.
		CommonFx.ring(a, colorA, 1.2F, 28);
		CommonFx.ring(b, colorB, 1.2F, 28);
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 10; i++) {
				CommonFx.add(ParticleTypes.FIREWORK, mid, new Vec3(random.nextGaussian(), random.nextFloat(), random.nextGaussian()).scale(0.12D), true);
			}
		}
	}

	private static void tickStart(Active active) {
		float progress = Math.min(1.0F, active.age / 10.0F);
		Vec3 dir = horizontal(active.b.subtract(active.a));
		Vec3 back = dir.scale(-1.0D);
		// Facing rings: vertical circles in front of each duelist, opening up over the first half second.
		if (active.age % 2 == 0) {
			float radius = 0.35F + 0.75F * progress;
			verticalRing(active.a.add(dir.scale(0.9D)).add(0.0D, 1.1D, 0.0D), dir, active.colorA, radius, 18);
			verticalRing(active.b.add(back.scale(0.9D)).add(0.0D, 1.1D, 0.0D), back, active.colorB, radius, 18);
		}
		// The streak between them: a vertical column alternating both colours, brightest at the start.
		Vec3 mid = middle(active.a, active.b);
		if (active.age <= 16) {
			RandomSource random = CommonFx.random();
			float height = 2.0F + 3.0F * progress;
			int points = EffectQuality.scale(10);
			DustParticleOptions dustA = CommonFx.dust(active.colorA, 1.3F);
			DustParticleOptions dustB = CommonFx.dust(active.colorB, 1.3F);
			for (int i = 0; i < points; i++) {
				double y = mid.y - 1.0D + random.nextFloat() * height;
				CommonFx.add(i % 2 == 0 ? dustA : dustB, mid.x + (random.nextFloat() - 0.5F) * 0.15F, y,
						mid.z + (random.nextFloat() - 0.5F) * 0.15F, 0.0D, 0.02D, 0.0D);
			}
			if (active.age % 3 == 0) {
				CommonFx.add(ParticleTypes.END_ROD, new Vec3(mid.x, mid.y - 1.0D + random.nextFloat() * height, mid.z), new Vec3(0.0D, 0.05D, 0.0D), true);
			}
			if (EffectQuality.chance(random, 0.5F)) {
				CommonFx.add(ParticleTypes.ELECTRIC_SPARK, mid, new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.12D), true);
			}
		}
	}

	// ================================================================== duel end

	private static void victoryBurst(Vec3 pos, int color) {
		RandomSource random = CommonFx.random();
		Vec3 chest = pos.add(0.0D, 1.0D, 0.0D);
		CommonFx.burst(chest, color, 40, 2.5F);
		CommonFx.pillar(pos, color, 3.0F);
		int totems = EffectQuality.scale(16);
		for (int i = 0; i < totems; i++) {
			CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, chest, new Vec3(random.nextGaussian() * 0.25D, 0.25D + random.nextFloat() * 0.3D,
					random.nextGaussian() * 0.25D), true);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 12; i++) {
				CommonFx.add(ParticleTypes.FIREWORK, chest, new Vec3(random.nextGaussian(), random.nextFloat() * 1.5D, random.nextGaussian()).scale(0.15D), true);
			}
		}
	}

	private static void tickVictory(Active active) {
		if (active.age % 2 != 0) {
			return;
		}
		// A ring rising around the winner, widening as it goes.
		float progress = active.age / (float) END_TICKS;
		CommonFx.ring(active.a.add(0.0D, progress * 2.2D, 0.0D), active.colorA, 0.6F + progress * 1.4F, 20);
		RandomSource random = CommonFx.random();
		if (EffectQuality.chance(random, 0.6F)) {
			CommonFx.add(ParticleTypes.END_ROD, active.a.add((random.nextFloat() - 0.5F) * 1.2F, 0.2D, (random.nextFloat() - 0.5F) * 1.2F),
					new Vec3(0.0D, 0.12D, 0.0D), false);
		}
	}

	private static void drawPuff(Vec3 a, Vec3 b) {
		RandomSource random = CommonFx.random();
		Vec3 mid = middle(a, b);
		int puffs = EffectQuality.scale(8);
		for (int i = 0; i < puffs; i++) {
			CommonFx.add(ParticleTypes.CLOUD, mid, new Vec3(random.nextGaussian(), random.nextFloat(), random.nextGaussian()).scale(0.05D), false);
		}
	}

	private static void tickDraw(Active active) {
		if (active.age % 4 != 0 || active.age > 12) {
			return;
		}
		float radius = 0.5F + active.age / 12.0F;
		CommonFx.ring(active.a, active.colorA, radius, 16);
		if (active.b.distanceToSqr(active.a) > 0.25D) {
			CommonFx.ring(active.b, active.colorB, radius, 16);
		}
	}

	// ================================================================== helpers

	/** A circle standing upright, facing {@code normal} (horizontal). */
	private static void verticalRing(Vec3 center, Vec3 normal, int rgb, float radius, int points) {
		int n = EffectQuality.scale(points);
		DustParticleOptions dust = CommonFx.dust(rgb, 0.9F);
		Vec3 side = new Vec3(-normal.z, 0.0D, normal.x);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			double c = Math.cos(angle) * radius;
			double s = Math.sin(angle) * radius;
			CommonFx.add(dust, center.x + side.x * c, center.y + s, center.z + side.z * c, normal.x * 0.01D, 0.0D, normal.z * 0.01D);
		}
	}

	/** Horizontal unit direction (east when the two points are on top of each other). */
	private static Vec3 horizontal(Vec3 delta) {
		Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
		return flat.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize();
	}

	private static Vec3 middle(Vec3 a, Vec3 b) {
		return a.add(b).scale(0.5D).add(0.0D, 1.0D, 0.0D);
	}

	/** Keeps the two duel colours apart: a colour too close to the first one is replaced by its opposite. */
	private static int clash(int first, int second) {
		int dr = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
		int dg = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
		int db = (first & 0xFF) - (second & 0xFF);
		if (dr * dr + dg * dg + db * db >= 90 * 90) {
			return second;
		}
		int opposite = ~first & 0xFFFFFF;
		// Never a near-black opposite: lift it towards white.
		int r = Math.max(70, (opposite >> 16) & 0xFF);
		int g = Math.max(70, (opposite >> 8) & 0xFF);
		int b = Math.max(70, opposite & 0xFF);
		return (r << 16) | (g << 8) | b;
	}

	private SectFx() {
	}
}
