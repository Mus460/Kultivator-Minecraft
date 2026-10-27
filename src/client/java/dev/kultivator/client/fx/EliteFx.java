package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.data.Element;
import dev.kultivator.elite.CombatFxIds;
import dev.kultivator.elite.EliteData;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Effects of {@code FxType.ELITE} (sub-ids in {@code elite.CombatFxIds}) and the continuous elite auras (DESIGN 19.3.1,
 * 19.3.8): element-coloured motes on the elites within 32 blocks (4 / 8 / 16 per second by tier × Effect Quality, at most
 * 12 elites, through {@link FxBudget}), the skill telegraphs and their impacts, the tribulation lightning rings (a
 * glowing sigil ring with a shrinking timing ring), graze arcs and the Hu Fa gold threads. Particles only; the server
 * plays every sound.
 */
public final class EliteFx {

	public static final double AURA_RANGE = 32.0D;
	public static final int MAX_ELITES = 12;
	private static final int[] MOTES_PER_SECOND = {0, 4, 8, 16};
	private static final int MAX_TELEGRAPHS = 32;
	private static final double DRAW_RANGE = 64.0D;
	private static final int GOLD = 0xF7DC7E;
	private static final int[] RING_COLORS = {0xB57CFF, 0xFF7A1A, 0x8A4FE0};
	private static final int[] RING_INNER = {0xF8F4FF, 0xFFD04A, 0xE6D2FF};

	/** One telegraph drawn for its lifetime. */
	private static final class Telegraph {
		final int kind;
		final Vec3 centre;
		final Vec3 tip;
		final float radius;
		final int color;
		final int inner;
		final int life;
		int age;

		Telegraph(int kind, Vec3 centre, Vec3 tip, float radius, int color, int inner, int life) {
			this.kind = kind;
			this.centre = centre;
			this.tip = tip;
			this.radius = radius;
			this.color = color;
			this.inner = inner;
			this.life = Math.max(1, life);
		}
	}

	private static final List<Telegraph> TELEGRAPHS = new ArrayList<>();
	private static final List<Mob> ELITES = new ArrayList<>();
	private static ClientLevel lastLevel;
	private static int scanTimer;
	private static float carry;

	/** One {@code FxType.ELITE} packet. */
	public static void play(FxType type, Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		if (level == null || type != FxType.ELITE) {
			return;
		}
		sync(level);
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.position().distanceToSqr(pos) > DRAW_RANGE * DRAW_RANGE * 2.25D) {
			return;
		}
		int a = fx.a();
		if (a >= CombatFxIds.TRIB_RING && a <= CombatFxIds.TRIB_RING + 2) {
			int variant = a - CombatFxIds.TRIB_RING;
			add(new Telegraph(CombatFxIds.TRIB_RING, pos, pos, Math.max(0.5F, fx.f()), RING_COLORS[variant], RING_INNER[variant], CombatFxIds.RING_TICKS));
			return;
		}
		int color = eliteColor(level, fx.b());
		switch (a) {
			case CombatFxIds.MARK -> add(new Telegraph(a, pos, pos, fx.f(), color, 0xFFFFFF, CombatFxIds.MARK_TICKS));
			case CombatFxIds.BREATH -> add(new Telegraph(a, pos, target, fx.f(), color, 0xE8FBFF, CombatFxIds.WINDUP_TICKS));
			case CombatFxIds.STOMP, CombatFxIds.THORNS -> add(new Telegraph(a, pos, pos, fx.f(), color, 0xFFFFFF, CombatFxIds.WINDUP_TICKS));
			case CombatFxIds.BLINK -> {
				add(new Telegraph(a, pos, pos, Math.max(0.6F, fx.f()), color, 0xD8C8FF, CombatFxIds.BLINK_TICKS));
				CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), color, 1.4F, 14, 0.0F);
			}
			case CombatFxIds.MARK_HIT -> markHit(level, pos, fx.f(), color, element(level, fx.b()));
			case CombatFxIds.BREATH_HIT -> breathHit(pos, target, color);
			case CombatFxIds.STOMP_HIT -> stompHit(pos, fx.f(), color);
			case CombatFxIds.THORNS_HIT -> thornsHit(pos, fx.f(), color);
			case CombatFxIds.BLINK_HIT -> blinkHit(pos, target, color);
			case CombatFxIds.BOLT -> CommonFx.bolt(pos, target, color, 5);
			case CombatFxIds.BIRTH -> {
				CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), color, 1.6F, 10 + 8 * Math.max(1, Math.round(fx.f())), 0.0F);
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), color, 12, 1.2F);
			}
			case CombatFxIds.GRAZE -> {
				int variant = Math.max(0, Math.min(2, fx.b()));
				CommonFx.bolt(pos, target, RING_INNER[variant], 4);
				CommonFx.burst(target, RING_COLORS[variant], 8, 0.6F);
			}
			case CombatFxIds.THREAD -> thread(pos, target);
			case CombatFxIds.GUARD_JOIN -> {
				CommonFx.burst(pos, GOLD, 18, 1.0F);
				CommonFx.ring(pos.add(0.0D, -0.9D, 0.0D), GOLD, 1.2F, 24);
			}
			default -> {
			}
		}
	}

	/** Every client tick (from {@code FxDispatcher.tick}). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		sync(level);
		if (--scanTimer <= 0) {
			scanTimer = 10;
			rescan(level, minecraft.player.position());
		}
		RandomSource random = level.getRandom();
		auras(random, minecraft.player.position());
		Iterator<Telegraph> iterator = TELEGRAPHS.iterator();
		while (iterator.hasNext()) {
			Telegraph telegraph = iterator.next();
			telegraph.age++;
			if (telegraph.age > telegraph.life) {
				iterator.remove();
				continue;
			}
			draw(telegraph, random);
		}
	}

	/** The elites within 32 blocks seen at the last scan (nearest first, at most 12; the name plate reads it). */
	public static List<Mob> elites() {
		return ELITES;
	}

	/** Forgets everything (world change). */
	public static void clear() {
		TELEGRAPHS.clear();
		ELITES.clear();
	}

	/** Disconnect: also lets go of the old world (the next world's first tick would only do it after it loads). */
	public static void forget() {
		lastLevel = null;
		clear();
	}

	// ================================================================== auras

	private static void rescan(ClientLevel level, Vec3 center) {
		ELITES.clear();
		AABB box = new AABB(center, center).inflate(AURA_RANGE);
		List<Mob> found = level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && mob.hasAttached(EliteData.TYPE)
				&& mob.distanceToSqr(center) <= AURA_RANGE * AURA_RANGE);
		found.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(center)));
		for (int i = 0; i < Math.min(MAX_ELITES, found.size()); i++) {
			ELITES.add(found.get(i));
		}
	}

	private static void auras(RandomSource random, Vec3 center) {
		if (ELITES.isEmpty()) {
			return;
		}
		float quality = EffectQuality.level().factor();
		for (Mob mob : ELITES) {
			if (mob.isRemoved() || !mob.isAlive() || mob.isInvisible() || mob.distanceToSqr(center) > AURA_RANGE * AURA_RANGE) {
				continue;
			}
			EliteData data = EliteData.of(mob);
			if (data == null) {
				continue;
			}
			float perTick = MOTES_PER_SECOND[data.tier()] * quality / 20.0F;
			int wanted = (int) perTick;
			carry += perTick - wanted;
			if (carry >= 1.0F) {
				carry -= 1.0F;
				wanted++;
			}
			int motes = FxBudget.take(wanted, FxBudget.Priority.DETAIL);
			Element element = data.elementValue();
			double x = mob.getX();
			double y = mob.getY();
			double z = mob.getZ();
			float width = mob.getBbWidth();
			float height = mob.getBbHeight();
			for (int i = 0; i < motes; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double r = width * (0.45D + random.nextDouble() * 0.35D);
				Vec3 at = new Vec3(x + Math.cos(angle) * r, y + random.nextDouble() * height, z + Math.sin(angle) * r);
				CommonFx.add(CommonFx.dust(element.color(), 0.6F + 0.15F * data.tier() + random.nextFloat() * 0.3F), at,
						new Vec3(0.0D, 0.02D + random.nextDouble() * 0.02D, 0.0D), false);
			}
			if (data.tier() >= 3 && EffectQuality.chance(random, 0.12F)) {
				ParticleOptions accent = accent(element);
				CommonFx.add(accent, new Vec3(x + (random.nextDouble() - 0.5D) * width, y + height * (0.3D + random.nextDouble() * 0.7D),
						z + (random.nextDouble() - 0.5D) * width), new Vec3(0.0D, 0.03D, 0.0D), false);
			}
		}
	}

	private static ParticleOptions accent(Element element) {
		return switch (element) {
			case FIRE -> ParticleTypes.FLAME;
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case ICE -> ParticleTypes.SNOWFLAKE;
			case WATER -> ParticleTypes.BUBBLE_POP;
			case WOOD -> ParticleTypes.HAPPY_VILLAGER;
			case EARTH -> ParticleTypes.ASH;
			case METAL -> ParticleTypes.END_ROD;
			case WIND -> ParticleTypes.CLOUD;
			case SPACE -> ParticleTypes.REVERSE_PORTAL;
		};
	}

	// ================================================================== telegraphs

	private static void draw(Telegraph t, RandomSource random) {
		float progress = (float) t.age / t.life;
		switch (t.kind) {
			case CombatFxIds.TRIB_RING -> {
				if (t.age % 2 == 0) {
					ringPoints(t.centre, t.color, t.radius, 28, 1.3F);
					// The timing ring closes in on the sigil; it meets the edge when the strike falls.
					ringPoints(t.centre, t.inner, t.radius * (1.0F - progress) + 0.1F, 14, 0.9F);
				}
				// Six sigil points turning slowly.
				int sigils = EffectQuality.minimal() ? 3 : 6;
				for (int i = 0; i < sigils; i++) {
					double angle = t.age * 0.08D + Math.PI * 2.0D * i / sigils;
					CommonFx.add(CommonFx.dust(t.inner, 1.1F), new Vec3(t.centre.x + Math.cos(angle) * t.radius * 0.6D, t.centre.y + 0.08D,
							t.centre.z + Math.sin(angle) * t.radius * 0.6D), Vec3.ZERO, true);
				}
				if (progress > 0.6F && EffectQuality.chance(random, 0.5F)) {
					CommonFx.add(ParticleTypes.ELECTRIC_SPARK, t.centre.add((random.nextDouble() - 0.5D) * t.radius * 2.0D, 0.1D,
							(random.nextDouble() - 0.5D) * t.radius * 2.0D), new Vec3(0.0D, 0.25D, 0.0D), true);
				}
			}
			case CombatFxIds.MARK -> {
				if (t.age % 2 == 0) {
					ringPoints(t.centre, t.color, t.radius, 20, 1.2F);
					ringPoints(t.centre, t.inner, t.radius * (1.0F - progress) + 0.1F, 10, 0.8F);
				}
				if (progress > 0.5F && EffectQuality.chance(random, 0.4F)) {
					CommonFx.add(CommonFx.dust(t.color, 1.0F), t.centre.add((random.nextDouble() - 0.5D) * t.radius, 2.5D + random.nextDouble(),
							(random.nextDouble() - 0.5D) * t.radius), new Vec3(0.0D, -0.2D, 0.0D), true);
				}
			}
			case CombatFxIds.STOMP, CombatFxIds.THORNS -> {
				if (t.age % 2 == 0) {
					ringPoints(t.centre, t.color, t.radius, 30, 1.1F);
					ringPoints(t.centre, t.inner, t.radius * progress, 12, 0.7F);
				}
				if (t.kind == CombatFxIds.THORNS && EffectQuality.chance(random, 0.3F)) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double r = random.nextDouble() * t.radius;
					CommonFx.add(CommonFx.dust(0x3A7A2A, 0.8F), new Vec3(t.centre.x + Math.cos(angle) * r, t.centre.y + 0.1D,
							t.centre.z + Math.sin(angle) * r), new Vec3(0.0D, 0.05D, 0.0D), true);
				}
			}
			case CombatFxIds.BREATH -> {
				if (t.age % 2 == 0) {
					cone(t.centre, t.tip, t.color);
				}
			}
			case CombatFxIds.BLINK -> {
				if (EffectQuality.chance(random, 0.6F)) {
					CommonFx.add(ParticleTypes.PORTAL, t.centre.add((random.nextDouble() - 0.5D) * t.radius * 2.0D, random.nextDouble() * 2.0D,
							(random.nextDouble() - 0.5D) * t.radius * 2.0D), new Vec3(0.0D, -0.1D, 0.0D), false);
				}
			}
			default -> {
			}
		}
	}

	/** A horizontal ring of always-shown motes ({@code points} at Normal, never fewer than 8: a telegraph must be seen). */
	private static void ringPoints(Vec3 centre, int color, float radius, int points, float size) {
		int n = Math.max(8, EffectQuality.scale(points));
		DustParticleOptions dust = CommonFx.dust(color, size);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			CommonFx.add(dust, new Vec3(centre.x + Math.cos(angle) * radius, centre.y + 0.06D, centre.z + Math.sin(angle) * radius), Vec3.ZERO, true);
		}
	}

	/** The outline of Napas Es's cone: two edges and the far arc. */
	private static void cone(Vec3 from, Vec3 tip, int color) {
		Vec3 axis = tip.subtract(from);
		double length = axis.length();
		if (length < 0.1D) {
			return;
		}
		Vec3 dir = axis.scale(1.0D / length);
		double half = Math.acos(0.82D);
		DustParticleOptions dust = CommonFx.dust(color, 1.0F);
		int steps = Math.max(5, EffectQuality.scale(8));
		for (int side = -1; side <= 1; side += 2) {
			Vec3 edge = rotateY(dir, side * half);
			for (int i = 1; i <= steps; i++) {
				Vec3 at = from.add(edge.scale(length * i / steps));
				CommonFx.add(dust, at, Vec3.ZERO, true);
			}
		}
		int arc = Math.max(5, EffectQuality.scale(10));
		for (int i = 0; i <= arc; i++) {
			double angle = -half + 2.0D * half * i / arc;
			CommonFx.add(dust, from.add(rotateY(dir, angle).scale(length)), Vec3.ZERO, true);
		}
	}

	private static Vec3 rotateY(Vec3 v, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
	}

	// ================================================================== impacts

	private static void markHit(ClientLevel level, Vec3 centre, float radius, int color, @Nullable Element element) {
		RandomSource random = level.getRandom();
		if (element == Element.FIRE) {
			int n = EffectQuality.scale(30);
			for (int i = 0; i < n; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double r = random.nextDouble() * radius;
				CommonFx.add(ParticleTypes.FLAME, new Vec3(centre.x + Math.cos(angle) * r, centre.y + random.nextDouble() * 3.0D,
						centre.z + Math.sin(angle) * r), new Vec3(0.0D, 0.12D, 0.0D), false);
			}
		} else {
			CommonFx.bolt(centre.add(0.0D, 9.0D, 0.0D), centre, color, 8);
			ColorParticleOption flash = ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color);
			CommonFx.add(flash, centre.add(0.0D, 0.6D, 0.0D), Vec3.ZERO, true);
		}
		CommonFx.ring(centre.add(0.0D, 0.05D, 0.0D), color, radius, 24);
		CommonFx.burst(centre.add(0.0D, 0.4D, 0.0D), color, 14, radius);
	}

	private static void breathHit(Vec3 from, Vec3 tip, int color) {
		RandomSource random = CommonFx.random();
		Vec3 axis = tip.subtract(from);
		int n = EffectQuality.scale(36);
		for (int i = 0; i < n; i++) {
			Vec3 dir = rotateY(axis.normalize(), (random.nextDouble() - 0.5D) * 1.2D);
			Vec3 velocity = dir.scale(0.25D + random.nextDouble() * 0.25D);
			CommonFx.add(random.nextBoolean() ? ParticleTypes.SNOWFLAKE : CommonFx.dust(color, 1.0F), from, velocity, false);
		}
	}

	private static void stompHit(Vec3 centre, float radius, int color) {
		CommonFx.ring(centre.add(0.0D, 0.1D, 0.0D), color, radius, 40);
		CommonFx.ring(centre.add(0.0D, 0.1D, 0.0D), color, radius * 0.5F, 24);
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(20);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.CLOUD, new Vec3(centre.x, centre.y + 0.2D, centre.z),
					new Vec3(Math.cos(angle) * 0.3D, 0.05D, Math.sin(angle) * 0.3D), false);
		}
	}

	private static void thornsHit(Vec3 centre, float radius, int color) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(28);
		DustParticleOptions dust = CommonFx.dust(color, 1.0F);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = random.nextDouble() * radius;
			Vec3 at = new Vec3(centre.x + Math.cos(angle) * r, centre.y + 0.1D, centre.z + Math.sin(angle) * r);
			CommonFx.add(dust, at, new Vec3(0.0D, 0.18D + random.nextDouble() * 0.1D, 0.0D), false);
		}
		CommonFx.ring(centre.add(0.0D, 0.1D, 0.0D), 0x3A7A2A, radius, 32);
	}

	private static void blinkHit(Vec3 from, Vec3 to, int color) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(16);
		for (int i = 0; i < n; i++) {
			CommonFx.add(ParticleTypes.PORTAL, from.add((random.nextDouble() - 0.5D), random.nextDouble() * 2.0D, (random.nextDouble() - 0.5D)),
					Vec3.ZERO, false);
			CommonFx.add(ParticleTypes.REVERSE_PORTAL, to.add((random.nextDouble() - 0.5D), random.nextDouble() * 2.0D, (random.nextDouble() - 0.5D)),
					Vec3.ZERO, false);
		}
		CommonFx.beam(from.add(0.0D, 1.0D, 0.0D), to.add(0.0D, 1.0D, 0.0D), color, 0.1F);
	}

	/** A gold qi thread sagging a little between guardian and cultivator. */
	private static void thread(Vec3 from, Vec3 to) {
		int n = Math.max(6, EffectQuality.scale(24));
		DustParticleOptions dust = CommonFx.dust(GOLD, 0.8F);
		Vec3 delta = to.subtract(from);
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			double sag = Math.sin(t * Math.PI) * 0.6D;
			CommonFx.add(dust, from.add(delta.scale(t)).add(0.0D, sag, 0.0D), Vec3.ZERO, true);
		}
		if (EffectQuality.maximal()) {
			CommonFx.add(ParticleTypes.END_ROD, to, new Vec3(0.0D, 0.05D, 0.0D), true);
		}
	}

	// ================================================================== helpers

	private static void add(Telegraph telegraph) {
		if (TELEGRAPHS.size() >= MAX_TELEGRAPHS) {
			TELEGRAPHS.remove(0);
		}
		TELEGRAPHS.add(telegraph);
	}

	private static int eliteColor(ClientLevel level, int entityId) {
		Element element = element(level, entityId);
		return element == null ? 0xFFD54A : element.color();
	}

	private static @Nullable Element element(ClientLevel level, int entityId) {
		Entity entity = level.getEntity(entityId);
		EliteData data = EliteData.of(entity);
		return data == null ? null : data.elementValue();
	}

	private static void sync(ClientLevel level) {
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
	}

	private EliteFx() {
	}
}
