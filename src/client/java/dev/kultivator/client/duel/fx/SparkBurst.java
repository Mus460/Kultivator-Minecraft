package dev.kultivator.client.duel.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.film.FilmLens;
import dev.kultivator.client.epic.film.FilmParticleProviders;
import dev.kultivator.client.epic.lens.LensLayers;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.fx.FilmParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The burst of one clash result (DESIGN 35.4.2-35.4.7): sparks and embers at the contact solved by
 * {@link ContactSolver}, the metal flash, glints, star rings, shock rings, scorch decals, dust, hit-stop, slow motion,
 * camera shake, impact frames and the lens flare, all by the table of 35.4.2 at Normal; Minimal × 0.25 (at least 4 core
 * sparks), Maximal × 2, never above the caps of 35.4.12 (sparks per event 6 / 40 / 80, per tick over every duel
 * 30 / 160 / 400, embers 0 / 10 / 24). The first 8 sparks take {@code FxBudget} CORE tokens, the rest DETAIL; refused ones
 * are dropped silently. "Partikel" Minimal keeps only the 4 core sparks, Kurang halves the rest; "Percikan duel" Ringan
 * halves, Mati draws none; other players' duels × "Efek pemain lain". Every viewer builds the same burst from the
 * payload's seed.
 * <p>
 * Spark velocity: the attacker's tip velocity reflected about the contact normal, × 0.25-0.6 b/tick, in a 35° cone; a
 * deflect sprays back toward the attacker, a clash and a blade lock throw sparks to both sides of the blades, a break
 * bursts all around. 20 % of the sparks of a qi swing start in the qi colour (mixed 30 %).
 */
public final class SparkBurst {

	public static final int GOLD = 0xFFD86A;
	public static final int STEEL = 0xE8F0FF;

	/** Spark spray modes. */
	enum Spray { REFLECT, TO_ATTACKER, BOTH_SIDES, RADIAL }

	private static int tickSpent;
	private static final Map<Integer, int[]> PERFECTS = new HashMap<>();
	private static long lastDance = -1000L;

	/** Once per client tick: the per-tick spark cap refills. */
	static void tick() {
		tickSpent = 0;
	}

	static void clear() {
		tickSpent = 0;
		PERFECTS.clear();
	}

	// ================================================================ clash results

	/** Plays the burst of one clash result (from {@link DuelFx}). */
	static void play(DuelFx.ClashEvent e) {
		play(e, null);
	}

	/** Plays the burst with a given contact ({@link DuelPreviews}: two virtual blades) instead of the solved one. */
	static void play(DuelFx.ClashEvent e, ContactSolver.@Nullable Contact forced) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		boolean involved = DuelFx.involved(e.aId(), e.bId());
		if (!involved && minecraft.player.position().distanceToSqr(e.anchor()) > 64.0D * 64.0D) {
			return;
		}
		float others = involved ? 1.0F : EffectQuality.othersScale();
		Entity attacker = DuelFx.entity(e.aId());
		Entity defender = DuelFx.entity(e.bId());
		DuelFx.SwingView swing = DuelFx.swing(e.aId(), e.swingA());
		float partial = DuelTrailRenderer.lastPartial();
		ContactSolver.Contact c = forced != null ? forced : ContactSolver.solve(e, attacker, defender, swing, partial);
		Vec3 at = c.pos();
		int weapon = weaponOf(e.aId(), swing);
		int base = DuelCodes.sparkRgb(weapon);
		boolean qi = swing != null && swing.has(DuelCodes.SWING_QI);
		int qiRgb = qi ? (swing.qiRgb != 0 ? swing.qiRgb : e.rgbA()) : -1;
		boolean heavy = e.power() >= 170 || swing != null && swing.has(DuelCodes.SWING_HEAVY);
		RandomSource random = RandomSource.create(e.seed() * 0x5DEECE66DL + e.outcome());
		int group = e.group();
		float power = 0.85F + 0.4F * e.power01();
		boolean fist = weapon == DuelCodes.W_TANGAN_KOSONG;
		int self = DuelFx.localId();
		Vec3 attackerAt = attacker != null ? attacker.position().add(0.0D, attacker.getBbHeight() * 0.7D, 0.0D) : at.subtract(c.velocity());
		switch (e.outcome()) {
			case DuelCodes.CLASH, DuelCodes.CLASH_MENANG -> {
				if (fist) {
					poof(level, at, 6, random);
					break;
				}
				sparks(c, attackerAt, 24, Spray.BOTH_SIDES, power, 6.0F, 14.0F, qiRgb, random, group, others);
				embers(at, 6, base, random, group, others);
				glints(level, at, 6, base, random);
				film(FilmParticles.FLARE, at, Vec3.ZERO, base, 0.9F, 5);
				MetalFlash.add(at, 0.6F, base, 3);
				if (heavy) {
					DuelTime.hitStop(2, e.aId(), e.bId(), at);
				}
			}
			case DuelCodes.DEFLECT -> {
				if (fist) {
					poof(level, at, 4, random);
					break;
				}
				sparks(c, attackerAt, 16, Spray.TO_ATTACKER, power, 6.0F, 12.0F, qiRgb, random, group, others);
				embers(at, 4, base, random, group, others);
				MetalFlash.add(at, 0.5F, base, 3);
			}
			case DuelCodes.PERFECT, DuelCodes.MIKIRI -> {
				sparks(c, attackerAt, 40, Spray.TO_ATTACKER, power * 1.1F, 8.0F, 18.0F, qiRgb, random, group, others);
				embers(at, 10, GOLD, random, group, others);
				starRing(at, c.normal(), 12, GOLD, random);
				MetalFlash.add(at, 1.2F, GOLD, 5);
				ShockRing.vertical(at, lineOf(attacker, defender, c), GOLD, 0.3F, heavy ? 2.2F : 1.6F, 6);
				DuelTime.hitStop(3, e.aId(), e.bId(), at);
				if (defender != null && defender.getId() == self) {
					DuelShake.perfectFlash();
					lensFlare(at);
				}
				if (e.outcome() == DuelCodes.MIKIRI && defender != null) {
					// the stomp on the thrusting blade
					DuelDust.puff(defender.position(), 10, true);
				}
				perfectStreak(e, at);
			}
			case DuelCodes.BIND -> {
				sparks(c, attackerAt, 30, Spray.BOTH_SIDES, power, 6.0F, 14.0F, qiRgb, random, group, others);
				embers(at, 8, base, random, group, others);
				MetalFlash.add(at, 0.6F, base, 3);
			}
			case DuelCodes.BIND_MENANG_A, DuelCodes.BIND_MENANG_B, DuelCodes.BIND_LEPAS, DuelCodes.BIND_RELEASE_TRICK -> {
				int count = e.outcome() == DuelCodes.BIND_RELEASE_TRICK ? 24 : 36;
				sparks(c, attackerAt, count, Spray.RADIAL, power * 1.1F, 7.0F, 16.0F, qiRgb, random, group, others);
				embers(at, 10, base, random, group, others);
				MetalFlash.add(at, 0.8F, base, 4);
				Vec3 line = lineOf(attacker, defender, c);
				ShockRing.ground(groundUnder(at), base, 0.5F, 4.5F, 8);
				if (e.outcome() != DuelCodes.BIND_RELEASE_TRICK) {
					ShockRing.vertical(at, line, base, 0.3F, 2.2F, 6);
				}
				if (attacker != null) {
					DuelDust.puff(attacker.position(), 12, true);
				}
				if (defender != null) {
					DuelDust.puff(defender.position(), 12, true);
				}
				if (e.outcome() == DuelCodes.BIND_MENANG_A || e.outcome() == DuelCodes.BIND_MENANG_B) {
					DuelTime.hitStop(2, e.aId(), e.bId(), at);
				}
			}
			case DuelCodes.GUARD_BREAK, DuelCodes.POSTURE_BREAK -> {
				sparks(c, attackerAt, 50, Spray.RADIAL, power * 1.2F, 8.0F, 18.0F, qiRgb, random, group, others);
				embers(at, 16, base, random, group, others);
				smokeAndMuzzle(at, 10, base, random);
				MetalFlash.add(at, 1.1F, base, 4);
				ShockRing.ground(groundUnder(at), base, 0.5F, 4.5F, 8);
				ShockRing.vertical(at, lineOf(attacker, defender, c), base, 0.3F, 2.2F, 6);
				if (defender != null) {
					ScorchDecal.add(defender.position(), c.velocity(), 2.2F, 0.9F);
					DuelDust.puff(defender.position(), 12, true);
				}
				DuelShake.impactFrame(false, e.rgbA() != 0 ? e.rgbA() : base, involved, e.seed());
				DuelTime.hitStop(4, e.aId(), e.bId(), at);
				if (e.outcome() == DuelCodes.GUARD_BREAK) {
					DuelTime.slowmo(12, e.aId(), e.bId(), at);
				}
			}
			case DuelCodes.DEATHBLOW -> {
				sparks(c, attackerAt, 50, Spray.RADIAL, power * 1.25F, 10.0F, 20.0F, GOLD, random, group, others);
				embers(at, 16, GOLD, random, group, others);
				starRing(at, c.normal(), 12, GOLD, random);
				smokeAndMuzzle(at, 6, GOLD, random);
				MetalFlash.add(at, 1.4F, GOLD, 5);
				ShockRing.ground(groundUnder(at), GOLD, 0.5F, 5.0F, 9);
				ShockRing.vertical(at, lineOf(attacker, defender, c), GOLD, 0.3F, 2.6F, 7);
				if (defender != null) {
					ScorchDecal.add(defender.position(), c.velocity(), 2.8F, 1.0F);
				}
				DuelShake.impactFrame(true, e.rgbA() != 0 ? e.rgbA() : GOLD, involved, e.seed());
				DuelTime.hitStop(5, e.aId(), e.bId(), at);
			}
			case DuelCodes.HIT -> {
				if (fist) {
					poof(level, at, 8, random);
				} else {
					qiShards(level, at, c.velocity(), e.rgbA() != 0 ? e.rgbA() : STEEL, 8, random);
				}
				crits(level, at, 6, random);
			}
			case DuelCodes.BLOCK -> {
				if (fist) {
					poof(level, at, 4, random);
					break;
				}
				sparks(c, attackerAt, 10, Spray.REFLECT, power * 0.75F, 4.0F, 9.0F, -1, random, group, others);
				embers(at, 2, base, random, group, others);
				MetalFlash.add(at, 0.4F, base, 3);
			}
			case DuelCodes.QI_DEFLECT, DuelCodes.QI_REFLECT -> {
				int ca = e.rgbA() != 0 ? e.rgbA() : base;
				int cb = e.rgbB() != 0 ? e.rgbB() : STEEL;
				sparks(c, attackerAt, 15, Spray.BOTH_SIDES, power, 6.0F, 14.0F, ca, random, group, others);
				sparks(c, attackerAt, 15, Spray.TO_ATTACKER, power, 6.0F, 14.0F, cb, random, group, others);
				embers(at, 8, ca, random, group, others);
				MetalFlash.add(at, 0.9F, DuelGeo.mix(ca, cb, 0.5F), 4);
				if (e.outcome() == DuelCodes.QI_REFLECT) {
					ShockRing.vertical(at, lineOf(attacker, defender, c), cb, 0.3F, 2.0F, 6);
				}
			}
			case DuelCodes.COMPANION_DEFLECT -> {
				sparks(c, attackerAt, 14, Spray.REFLECT, power, 5.0F, 12.0F, qiRgb, random, group, others);
				embers(at, 4, base, random, group, others);
				MetalFlash.add(at, 0.4F, base, 3);
			}
			case DuelCodes.EVADE -> {
				if (defender != null) {
					DuelDust.puff(defender.position(), 4, false);
				}
			}
			default -> {
			}
		}
		boolean selfHit = e.outcome() == DuelCodes.HIT && defender != null && defender.getId() == self;
		if (involved || minecraft.player.position().distanceToSqr(at) < 24.0D * 24.0D) {
			DuelShake.outcome(e.outcome(), at, involved, selfHit, e.decisive());
		}
	}

	/** The fighter's state changed: the Sword Dance slow motion at chain 4 (35.4.6). */
	static void onFighter(DuelFx.FighterView f, int oldState, int oldChain, int oldFlags) {
		if (f.chain >= 4 && oldChain < 4) {
			long now = DuelFx.ticks();
			if (now - lastDance < 120L) {
				return;
			}
			DuelFx.SessionView s = DuelFx.sessionOf(f.entityId);
			Entity entity = DuelFx.entity(f.entityId);
			if (s == null || entity == null) {
				return;
			}
			lastDance = now;
			Vec3 mid = midpoint(s);
			if (DuelTime.slowmo(20, s.aId, s.bId, mid)) {
				// a swirl of petals round the pair: the dance
				petalSwirl(mid, 10, s.aRgb, s.bRgb);
			}
		}
	}

	// ================================================================ blade lock

	/** Every tick of a running lock: grinding sparks at the sliding contact, an ember every other tick, the rumble. */
	static void bindTick(DuelFx.BindView b, long tick) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Vec3 at = ContactSolver.bindContact(b, 1.0F, DuelTime.now());
		if (at == null) {
			return;
		}
		boolean involved = DuelFx.involved(b.aId, b.bId);
		if (!involved && minecraft.player.position().distanceToSqr(at) > 48.0D * 48.0D) {
			return;
		}
		if (DuelTime.frozen()) {
			return;
		}
		float others = involved ? 1.0F : EffectQuality.othersScale();
		Entity a = DuelFx.entity(b.aId);
		Entity bb = DuelFx.entity(b.bId);
		RandomSource random = RandomSource.create(tick * 31L + b.aId);
		Vec3 line = a != null && bb != null ? bb.position().subtract(a.position()) : new Vec3(1.0D, 0.0D, 0.0D);
		Vec3 side = line.cross(new Vec3(0.0D, 1.0D, 0.0D));
		side = side.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
		int base = DuelCodes.sparkRgb(weaponOf(b.aId, null));
		// 3 small, fast grinding sparks a tick, thrown out sideways (the pitch of the scrape is U's)
		float grind = 0.6F + Math.abs(b.pressure) / 100.0F * 0.6F;
		int n = budget(3, others, false);
		for (int i = 0; i < n; i++) {
			double sign = random.nextBoolean() ? 1.0D : -1.0D;
			Vec3 dir = cone(side.scale(sign).add(0.0D, 0.45D, 0.0D).normalize(), 40.0F, random);
			Vec3 velocity = dir.scale((0.18D + random.nextDouble() * 0.22D) * grind);
			if (!DuelSparkParticle.spawn(at, velocity, 0.08F + random.nextFloat() * 0.05F, 4.0F + random.nextFloat() * 5.0F, -1, 0.0F, 0,
					i == 0 ? FxBudget.Priority.CORE : FxBudget.Priority.DETAIL)) {
				break;
			}
			tickSpent++;
		}
		if ((tick & 1L) == 0L && embersAllowed() > 0) {
			Vec3 velocity = side.scale(random.nextBoolean() ? 0.04D : -0.04D).add(0.0D, 0.03D, 0.0D);
			DuelEmberParticle.spawn(at, velocity, 0.05F + random.nextFloat() * 0.03F, 24.0F + random.nextFloat() * 12.0F, base, 0);
		}
		if (involved || minecraft.player.position().distanceToSqr(at) < 16.0D * 16.0D) {
			DuelShake.bindRumble(at, tick);
		}
		DuelRingVisual.tremble(at, 0.04F);
	}

	/** The lock ended (its last sync or a timeout). */
	static void bindEnded(DuelFx.BindView b) {
		// the burst of the end comes with the BIND_* clash result; nothing to do without one
	}

	/** The pulsing hot spot of every running lock (render pass). */
	static void renderBinds(DuelGeo frame) {
		for (DuelFx.BindView b : DuelFx.binds()) {
			Vec3 at = ContactSolver.bindContact(b, frame.partial, frame.time);
			if (at == null || frame.dist2(at) > 64.0D * 64.0D || !frame.take(3)) {
				continue;
			}
			float pulse = 0.75F + 0.25F * Mth.sin((float) frame.time * 1.3F);
			float heat = 0.6F + Math.abs(b.pressure) / 100.0F * 0.4F;
			frame.billboard(frame.glow, at, 0.32F * pulse * heat + 0.1F, 0.0F, DuelGeo.argb(0.75F * heat, 0xFF8A30));
			frame.billboard(frame.glow, at, 0.12F * pulse + 0.05F, 0.0F, DuelGeo.argb(0.95F, 0xFFF0C0));
			frame.star(at, 0.22F + 0.1F * pulse * heat, (float) frame.time * 0.35F, 0xFFE0A0, 0.6F * heat);
		}
	}

	// ================================================================ sparks

	/** The number of sparks of an event after the quality, setting, "Partikel" and "Efek pemain lain" scaling and caps. */
	private static int budget(int normalCount, float others, boolean event) {
		float setting = DuelFxSettings.sparks();
		if (setting <= 0.0F || normalCount <= 0 || others <= 0.0F) {
			return 0;
		}
		int tier = DuelFxSettings.tier();
		float factor = tier == 0 ? 0.25F : tier == 1 ? 1.0F : 2.0F;
		int n = Math.round(normalCount * factor * setting * others);
		if (tier == 0 && event) {
			n = Math.max(4, n);
		}
		int option = Minecraft.getInstance().options.particles().get().ordinal();
		if (option == 2) {
			n = Math.min(n, 4);
		} else if (option == 1) {
			n = Math.max(Math.min(n, 4), n / 2);
		}
		if (event) {
			n = Math.min(n, DuelFxSettings.pick(6, 40, 80));
		}
		int perTick = DuelFxSettings.pick(30, 160, 400);
		return Math.max(0, Math.min(n, perTick - tickSpent));
	}

	private static int embersAllowed() {
		int option = Minecraft.getInstance().options.particles().get().ordinal();
		return option == 2 || DuelFxSettings.sparks() <= 0.0F ? 0 : DuelFxSettings.pick(0, 10, 24);
	}

	/** Spawns the sparks of one result. */
	private static void sparks(ContactSolver.Contact c, Vec3 attackerAt, int normalCount, Spray spray, float power, float lifeMin, float lifeMax,
			int qiTint, RandomSource random, int group, float others) {
		int n = budget(normalCount, others, true);
		if (n <= 0) {
			return;
		}
		Vec3 at = c.pos();
		Vec3 normal = c.normal();
		Vec3 incoming = c.velocity().lengthSqr() > 1.0E-6D ? c.velocity().normalize() : normal;
		// reflect(v, n) = v - 2 (v·n) n
		Vec3 reflected = incoming.subtract(normal.scale(2.0D * incoming.dot(normal))).normalize();
		Vec3 toAttacker = attackerAt.subtract(at);
		toAttacker = toAttacker.lengthSqr() < 1.0E-6D ? reflected : toAttacker.normalize();
		for (int i = 0; i < n; i++) {
			Vec3 axis = switch (spray) {
				case REFLECT -> reflected;
				case TO_ATTACKER -> toAttacker.scale(0.65D).add(reflected.scale(0.35D)).normalize();
				case BOTH_SIDES -> normal.scale((i & 1) == 0 ? 1.0D : -1.0D).add(reflected.scale(0.5D)).add(0.0D, 0.25D, 0.0D).normalize();
				case RADIAL -> randomUnit(random).add(0.0D, 0.35D, 0.0D).normalize();
			};
			float coneDeg = spray == Spray.RADIAL ? 10.0F : 35.0F;
			Vec3 dir = cone(axis, coneDeg, random);
			double speed = (0.25D + random.nextDouble() * 0.35D) * power;
			float life = lifeMin + random.nextFloat() * (lifeMax - lifeMin);
			float width = 0.11F + random.nextFloat() * 0.07F;
			boolean tinted = qiTint >= 0 && (random.nextFloat() < 0.2F || qiTint == GOLD);
			FxBudget.Priority priority = i < 8 ? FxBudget.Priority.CORE : FxBudget.Priority.DETAIL;
			if (!DuelSparkParticle.spawn(at, dir.scale(speed), width, life, tinted ? qiTint : -1, tinted ? (qiTint == GOLD ? 0.55F : 0.3F) : 0.0F, group,
					priority)) {
				if (i < 8) {
					// a refused core spark: the film spark stands in (types missing, an older server jar)
					FilmParticleProviders.spawnAmbient(FilmParticles.SPARK, at, dir.scale(speed), 0xFFE7B0, width * 1.6F, Math.round(life), priority);
				}
				continue;
			}
			tickSpent++;
		}
	}

	private static void embers(Vec3 at, int normalCount, int rgb, RandomSource random, int group, float others) {
		int cap = embersAllowed();
		if (cap <= 0 || others <= 0.0F) {
			return;
		}
		int tier = DuelFxSettings.tier();
		float factor = tier == 1 ? 1.0F : 2.0F;
		int n = Math.min(cap, Math.round(normalCount * factor * DuelFxSettings.sparks() * others));
		for (int i = 0; i < n; i++) {
			Vec3 velocity = randomUnit(random).multiply(0.09D, 0.0D, 0.09D).add(0.0D, 0.05D + random.nextDouble() * 0.06D, 0.0D);
			if (!DuelEmberParticle.spawn(at, velocity, 0.05F + random.nextFloat() * 0.04F, 26.0F + random.nextFloat() * 18.0F, rgb, group)) {
				return;
			}
		}
	}

	// ================================================================ extras

	private static void glints(ClientLevel level, Vec3 at, int normalCount, int rgb, RandomSource random) {
		int n = EffectQuality.scale(normalCount);
		for (int i = 0; i < n && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0; i++) {
			Vec3 v = randomUnit(random).scale(0.04D);
			level.addParticle(Qi.glint(rgb, 0.18F + random.nextFloat() * 0.1F, 6 + random.nextInt(5)), at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	private static void starRing(Vec3 at, Vec3 normal, int normalCount, int rgb, RandomSource random) {
		int n = EffectQuality.scale(normalCount);
		Vec3 u = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (u.lengthSqr() < 1.0E-4D) {
			u = new Vec3(1.0D, 0.0D, 0.0D);
		}
		u = u.normalize();
		Vec3 v = normal.cross(u).normalize();
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n + random.nextDouble() * 0.2D;
			Vec3 dir = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
			if (!film(FilmParticles.STAR, at.add(dir.scale(0.15D)), dir.scale(0.14D), rgb, 0.22F, 12)) {
				return;
			}
		}
	}

	private static void smokeAndMuzzle(Vec3 at, int normalCount, int rgb, RandomSource random) {
		int n = EffectQuality.scale(normalCount);
		for (int i = 0; i < n; i++) {
			Vec3 dir = randomUnit(random);
			film(FilmParticles.MUZZLE, at, dir.scale(0.08D), DuelGeo.mix(rgb, 0xFFFFFF, 0.4F), 0.6F + random.nextFloat() * 0.4F, 5);
			film(FilmParticles.SMOKE, at.add(dir.scale(0.2D)), dir.scale(0.05D).add(0.0D, 0.02D, 0.0D), 0x8A8078, 0.9F + random.nextFloat() * 0.6F,
					22 + random.nextInt(12));
		}
	}

	private static void qiShards(ClientLevel level, Vec3 at, Vec3 velocity, int rgb, int normalCount, RandomSource random) {
		int n = EffectQuality.scale(normalCount);
		Vec3 axis = velocity.lengthSqr() > 1.0E-6D ? velocity.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
		for (int i = 0; i < n && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0; i++) {
			Vec3 v = cone(axis, 45.0F, random).scale(0.18D + random.nextDouble() * 0.18D);
			ParticleOptions options = Qi.spark(rgb, 0.14F + random.nextFloat() * 0.08F, 7 + random.nextInt(5));
			level.addParticle(options, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	private static void crits(ClientLevel level, Vec3 at, int normalCount, RandomSource random) {
		int n = EffectQuality.scale(normalCount);
		for (int i = 0; i < n && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0; i++) {
			Vec3 v = randomUnit(random).scale(0.3D);
			level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, v.x, v.y + 0.1D, v.z);
		}
	}

	private static void poof(ClientLevel level, Vec3 at, int normalCount, RandomSource random) {
		int n = EffectQuality.scale(normalCount);
		for (int i = 0; i < n && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0; i++) {
			Vec3 v = randomUnit(random).scale(0.06D);
			level.addParticle(ParticleTypes.POOF, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	private static void petalSwirl(Vec3 mid, int normalCount, int rgbA, int rgbB) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		int n = EffectQuality.scale(normalCount);
		for (int i = 0; i < n && FxBudget.take(2, FxBudget.Priority.EXTRA) > 0; i++) {
			double angle = Math.PI * 2.0D * i / n;
			double r = 2.2D;
			Vec3 p = mid.add(Math.cos(angle) * r, 0.4D + (i % 3) * 0.5D, Math.sin(angle) * r);
			Vec3 v = new Vec3(-Math.sin(angle) * 0.12D, 0.02D, Math.cos(angle) * 0.12D);
			level.addParticle(Qi.petal((i & 1) == 0 ? rgbA : rgbB, 0.2F, 40), p.x, p.y, p.z, v.x, v.y, v.z);
		}
	}

	private static boolean film(net.minecraft.core.particles.SimpleParticleType type, Vec3 at, Vec3 velocity, int rgb, float size, int life) {
		return FilmParticleProviders.spawnAmbient(type, at, velocity, rgb, size, life, FxBudget.Priority.DETAIL);
	}

	/** Maximal + "Lapisan lensa" Nyala: a 6-tick flare and anamorphic streak on one's own perfect deflect (no film running). */
	private static void lensFlare(Vec3 at) {
		if (!EffectQuality.maximal() || EpicSettings.lens() != LensLayers.Setting.NYALA || LensLayers.active()) {
			return;
		}
		int mask = 1 << FilmLens.FLARE.ordinal() | 1 << FilmLens.ANAMORPHIC.ordinal();
		LensLayers.shot(mask, GOLD, Util.getMillis(), at);
		MetalFlash.lensUntil(DuelFx.ticks() + 6);
	}

	/** Three perfect deflects by the same defender within 4 s: a 16-tick slow motion. */
	private static void perfectStreak(DuelFx.ClashEvent e, Vec3 at) {
		long now = DuelFx.ticks();
		int[] streak = PERFECTS.computeIfAbsent(e.bId(), id -> new int[]{0, 0});
		if (now - streak[1] > 80) {
			streak[0] = 0;
		}
		streak[0]++;
		streak[1] = (int) now;
		if (streak[0] >= 3) {
			streak[0] = 0;
			DuelTime.slowmo(16, e.aId(), e.bId(), at);
		}
	}

	// ================================================================ helpers

	static int weaponOf(int entityId, @Nullable DuelFx.SwingView swing) {
		if (swing != null) {
			return swing.weapon;
		}
		DuelFx.FighterView f = DuelFx.fighter(entityId);
		return f != null ? f.weapon : DuelCodes.W_PEDANG;
	}

	static Vec3 midpoint(DuelFx.SessionView s) {
		Entity a = DuelFx.entity(s.aId);
		Entity b = DuelFx.entity(s.bId);
		if (a != null && b != null) {
			return a.position().lerp(b.position(), 0.5D);
		}
		return s.center;
	}

	/** The horizontal duel line (A to B) of a result, for the vertical shock rings. */
	private static Vec3 lineOf(@Nullable Entity attacker, @Nullable Entity defender, ContactSolver.Contact c) {
		Vec3 line = attacker != null && defender != null ? defender.position().subtract(attacker.position()) : c.velocity();
		line = new Vec3(line.x, 0.0D, line.z);
		return line.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : line.normalize();
	}

	private static Vec3 groundUnder(Vec3 at) {
		Vec3 ground = ScorchDecal.groundBelow(at, 3.0D);
		return ground != null ? ground : at.subtract(0.0D, 1.2D, 0.0D);
	}

	static Vec3 randomUnit(RandomSource random) {
		double z = random.nextDouble() * 2.0D - 1.0D;
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double r = Math.sqrt(1.0D - z * z);
		return new Vec3(r * Math.cos(angle), z, r * Math.sin(angle));
	}

	/** A random unit direction within {@code degrees} of {@code axis} (uniform over the cap). */
	static Vec3 cone(Vec3 axis, float degrees, RandomSource random) {
		Vec3 a = axis.lengthSqr() < 1.0E-9D ? new Vec3(0.0D, 1.0D, 0.0D) : axis.normalize();
		double cosMax = Math.cos(Math.toRadians(degrees));
		double cos = 1.0D - random.nextDouble() * (1.0D - cosMax);
		double sin = Math.sqrt(Math.max(0.0D, 1.0D - cos * cos));
		double phi = random.nextDouble() * Math.PI * 2.0D;
		Vec3 u = Math.abs(a.y) < 0.95D ? a.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize() : a.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
		Vec3 v = a.cross(u);
		return a.scale(cos).add(u.scale(sin * Math.cos(phi))).add(v.scale(sin * Math.sin(phi)));
	}

	private SparkBurst() {
	}
}
