package dev.kultivator.client.formasi;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.effect.b.FxB;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Formation visuals (DESIGN 24.8, owned by P7c-katalog-ui). Continuous visuals come from {@code FormasiSync}: the
 * nearest 8 running cores within 48 blocks get family motes (2 / 6 / 12 per core per second by Effect Quality), a
 * ground ring at their edge, and their signature (sword ring, mist banks, sand whirls, fire ring, red sheet, rune ring);
 * one sky element at a time (the B2 cloud disc, the L2 river, the A1 Taiji disc) from Normal up within 48 blocks; none of
 * it in F1. One-off events ({@code FxType.FORMASI}, sub-ids of {@code FormasiFxIds} and {@code FxB}) are bolts, sword
 * streaks, sweeps, rings, trips, marks and slot markers. Every particle batch goes through {@code FxBudget}; nothing is a
 * particle sphere, a LightningBolt or a block change.
 */
public final class FormasiFx {

	public static final double VIEW = 48.0D;
	public static final int MAX_CORES = 8;
	public static final double PARTICLE_RANGE = 32.0D;
	public static final int[] MOTES_PER_SECOND = {2, 6, 12};
	public static final int MARK_MILLIS = 30_000;
	public static final int MAX_MARKS = 48;
	public static final int MAX_ANIMS = 48;

	private static final int JADE = 0x5FE3A1;
	private static final int GOLD = 0xFFD54A;
	private static final int RED = 0xFF4A3A;
	private static final int GREEN = 0x5CE07A;
	private static final int BLOOD = 0xB0101A;
	private static final int PURPLE = 0xD8C8FF;
	private static final int[] ELEMENTS = {0x3A5A8A, 0x4FCB5A, 0xE0342C, 0xE8D27A, 0xEDEDED};

	private record Mark(BlockPos pos, int kind, long until) {
	}

	private static final class Anim {
		final int kind;
		final Vec3 pos;
		final Vec3 target;
		final int b;
		final float f;
		final int life;
		int age;

		Anim(int kind, Vec3 pos, Vec3 target, int b, float f, int life) {
			this.kind = kind;
			this.pos = pos;
			this.target = target;
			this.b = b;
			this.f = f;
			this.life = life;
		}
	}

	private static final List<Mark> MARKS = new ArrayList<>();
	private static final List<Anim> ANIMS = new ArrayList<>();
	private static ClientLevel lastLevel;
	private static long clock;

	static void clear() {
		MARKS.clear();
		ANIMS.clear();
	}

	// ================================================================== events

	/** One {@code FxType.FORMASI} event. */
	public static void play(Payloads.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		RandomSource random = minecraft.level.getRandom();
		switch (fx.a()) {
			case FormasiFxIds.SLOT_MARK -> {
				if (MARKS.size() >= MAX_MARKS) {
					MARKS.remove(0);
				}
				MARKS.add(new Mark(BlockPos.containing(pos), fx.b(), Util.getMillis() + MARK_MILLIS));
			}
			case FormasiFxIds.PATTERN_SHOW -> {
				FormationSpec spec = FormationRegistry.byNet(fx.b() & 0xFFF);
				if (fx.f() <= 0.0F || spec == null) {
					PatternHologram.hide();
				} else {
					int rot = fx.b() >> 12 & 0xF;
					PatternHologram.show(spec, BlockPos.containing(pos), rot >= 4 ? -1 : rot, Math.round(fx.f()));
				}
			}
			case FormasiFxIds.AWAKEN -> animate(new Anim(FormasiFxIds.AWAKEN, pos, target, fx.b(), fx.f(), 40));
			case FormasiFxIds.STATE -> CommonFx.burst(pos.add(0.0D, 0.6D, 0.0D), stateColor(fx.b()), 10, 1.0F);
			case FormasiFxIds.PULSE -> CommonFx.ring(pos, familyColor(fx.b()), Math.max(1.0F, Math.min(6.0F, fx.f() * 0.25F)), 16);
			case FormasiFxIds.HIT -> CommonFx.burst(pos.add(0.0D, 0.8D, 0.0D), RED, 6, 0.8F);
			case FormasiFxIds.RIPPLE -> ripple(pos, fx.b() == 0 ? JADE : fx.b() & 0xFFFFFF);
			case FormasiFxIds.MARK_PILLAR -> animate(new Anim(FormasiFxIds.MARK_PILLAR, pos, target, fx.b(), fx.f(), 200));
			case FxB.BOLT -> bolt(pos, fx.b() == 0 ? 0xFFF2C0 : fx.b(), fx.f() <= 0.0F ? 24.0F : fx.f());
			case FxB.SWORD -> sword(pos, target, fx.b() == 0 ? 0x7FE0C8 : fx.b());
			case FxB.SWEEP -> sweep(pos, Math.max(1.0F, fx.f()));
			case FxB.SHIFT -> shift(pos, Math.max(4.0F, fx.f()), fx.b());
			case FxB.ALARM -> {
				AlarmHud.ping(pos, fx.b() & 0xF, Math.max(1, fx.b() >> 4), fx.f());
				if (minecraft.player != null) {
					minecraft.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, (fx.b() & 0xF) == 0 ? 1.4F : 1.8F);
				}
			}
			case FxB.ALARM_RING -> alarmRing(pos, target, fx.b(), fx.f());
			case FxB.JUMP_OUT -> jump(pos, fx.b(), true, random);
			case FxB.JUMP_IN -> jump(pos, fx.b(), false, random);
			case FxB.ROOT -> animate(new Anim(FxB.ROOT, pos, target, fx.b(), Math.max(1.0F, fx.f()), 30));
			case FxB.CHANNEL -> channel(minecraft, pos, fx.b(), fx.f());
			case FxB.ASH -> ash(pos, random);
			case FxB.FIRE -> fire(pos, fx.b(), Math.max(0.6F, fx.f()), random);
			case FxB.WATER -> water(pos, Math.max(0.6F, fx.f()), random);
			case FxB.SAND -> sand(pos, Math.max(0.6F, fx.f()), random);
			case FxB.OUTLINE -> animate(new Anim(FxB.OUTLINE, pos, target, fx.b(), fx.f(), Math.round(Math.max(1.0F, fx.f()) * 20.0F)));
			case FxB.NODE_MAP -> FormasiClient.requestNodeMap(fx.b());
			case FxB.STRIP -> petals(pos, Math.max(0.6F, fx.f()), random);
			case FxB.GRAND -> grand(pos, fx.b(), fx.f());
			case FxB.THUNDER -> thunder(pos, Math.max(0.6F, fx.f()), random);
			default -> {
			}
		}
	}

	private static void animate(Anim anim) {
		if (ANIMS.size() >= MAX_ANIMS) {
			ANIMS.remove(0);
		}
		ANIMS.add(anim);
	}

	// ================================================================== the tick

	/** Every client tick (not while paused). */
	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
		clock++;
		RandomSource random = level.getRandom();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		if (!minecraft.gui.hud.isHidden()) {
			drawCores(level, camera, random);
		}
		Iterator<Anim> it = ANIMS.iterator();
		while (it.hasNext()) {
			Anim anim = it.next();
			drawAnim(level, anim, random);
			if (++anim.age > anim.life) {
				it.remove();
			}
		}
		drawMarks();
	}

	private static void drawCores(ClientLevel level, Vec3 camera, RandomSource random) {
		List<FormasiClient.SyncCore> near = new ArrayList<>();
		for (FormasiClient.SyncCore core : FormasiClient.cores()) {
			if (core.running() && core.spec() != null && Vec3.atCenterOf(core.eye()).distanceToSqr(camera) <= VIEW * VIEW) {
				near.add(core);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		near.sort(Comparator.comparingDouble(core -> Vec3.atCenterOf(core.eye()).distanceToSqr(camera)));
		int quality = EffectQuality.level().ordinal();
		float moteChance = MOTES_PER_SECOND[Math.min(2, quality)] / 20.0F;
		boolean sky = !EffectQuality.minimal();
		for (int i = 0; i < near.size() && i < MAX_CORES; i++) {
			FormasiClient.SyncCore core = near.get(i);
			FormationSpec spec = core.spec();
			Vec3 eye = Vec3.atCenterOf(core.eye());
			if (random.nextFloat() < moteChance && FxBudget.take(1, FxBudget.Priority.EXTRA) > 0) {
				mote(spec, eye, core, random);
			}
			if ((clock + core.id() * 7L) % 20L == 0L) {
				edgeRing(spec, eye, core.radius(), camera, i < (quality == 0 ? 0 : quality == 1 ? 3 : 6));
			}
			signature(spec, core, eye, camera, random);
			if (sky && skyElement(spec, core)) {
				sky = false;
				drawSky(spec, core, eye, random);
			}
		}
	}

	/** One family mote near the eye. */
	private static void mote(FormationSpec spec, Vec3 eye, FormasiClient.SyncCore core, RandomSource random) {
		int color = spec.color();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double r = 1.0D + random.nextDouble() * 2.5D;
		Vec3 at = eye.add(Math.cos(angle) * r, random.nextDouble() * 1.5D, Math.sin(angle) * r);
		switch (spec.family()) {
			case PENGUMPUL -> CommonFx.add(CommonFx.dust(color, 0.8F), at, eye.subtract(at).scale(0.06D), false);
			case PENEMPAAN -> CommonFx.add(ParticleTypes.SMALL_FLAME, eye.x + (random.nextFloat() - 0.5F), eye.y + 0.6D, eye.z + (random.nextFloat() - 0.5F),
					0.0D, 0.03D, 0.0D);
			case PENYEMBUNYI -> CommonFx.add(CommonFx.dust(0x8FA890, 1.6F), at.x, at.y, at.z, 0.0D, 0.002D, 0.0D);
			case PERPINDAHAN -> CommonFx.add(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 0.0D, 0.02D, 0.0D);
			case PEMBUNUH -> CommonFx.add(CommonFx.dust(color, 0.7F), at.x, at.y, at.z, 0.0D, 0.01D, 0.0D);
			default -> CommonFx.add(CommonFx.dust(color, 0.7F), at.x, at.y, at.z, 0.0D, 0.015D, 0.0D);
		}
	}

	/** A ground ring at the field edge near the camera (no particle domes); thin at Minimal. */
	private static void edgeRing(FormationSpec spec, Vec3 eye, double radius, Vec3 camera, boolean full) {
		if (radius < 2.0D) {
			return;
		}
		int points = full ? 32 : 12;
		int granted = FxBudget.take(points, FxBudget.Priority.EXTRA);
		if (granted <= 0) {
			return;
		}
		DustParticleOptions dust = CommonFx.dust(spec.color(), full ? 0.9F : 0.7F);
		double phase = clock * 0.01D;
		double ground = eye.y - 0.4D;
		for (int i = 0; i < granted; i++) {
			double angle = phase + Math.PI * 2.0D * i / granted;
			double x = eye.x + Math.cos(angle) * radius;
			double z = eye.z + Math.sin(angle) * radius;
			if (camera.distanceToSqr(x, ground, z) <= PARTICLE_RANGE * PARTICLE_RANGE) {
				CommonFx.add(dust, x, ground, z, 0.0D, 0.004D, 0.0D);
			}
		}
	}

	/** The continuous signature of a formation. */
	private static void signature(FormationSpec spec, FormasiClient.SyncCore core, Vec3 eye, Vec3 camera, RandomSource random) {
		String id = spec.id();
		double r = core.radius();
		switch (id) {
			case "great_geng_sword" -> {
				// Twelve azure-green flying swords orbit 6 blocks up (none at Minimal).
				if (EffectQuality.minimal() || clock % 2L != 0L) {
					return;
				}
				int swords = FxBudget.take(12, FxBudget.Priority.EXTRA);
				DustParticleOptions blade = CommonFx.dust(0x7FE0C8, 0.9F);
				for (int i = 0; i < swords; i++) {
					double angle = clock * 0.05D + Math.PI * 2.0D * i / 12.0D;
					Vec3 at = eye.add(Math.cos(angle) * 4.0D, 6.0D, Math.sin(angle) * 4.0D);
					Vec3 tail = eye.add(Math.cos(angle - 0.15D) * 4.0D, 6.0D, Math.sin(angle - 0.15D) * 4.0D);
					CommonFx.add(blade, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
					CommonFx.add(blade, (at.x + tail.x) / 2.0D, at.y, (at.z + tail.z) / 2.0D, 0.0D, 0.0D, 0.0D);
				}
			}
			case "five_elements_reversal" -> mistBanks(eye, r, core.ext().getIntOr("d", 1) > 0 ? 1 : -1, random);
			case "red_sand" -> {
				if (clock % 4L == 0L && FxBudget.take(6, FxBudget.Priority.EXTRA) > 0) {
					for (int ring = 1; ring <= 3; ring++) {
						double angle = clock * 0.04D * ring + ring;
						double rr = r * ring / 3.0D;
						CommonFx.add(CommonFx.dust(0xC0603A, 1.2F), eye.x + Math.cos(angle) * rr, eye.y - 0.2D, eye.z + Math.sin(angle) * rr, 0.0D, 0.05D, 0.0D);
						CommonFx.add(CommonFx.dust(0xE08A50, 0.9F), eye.x - Math.cos(angle) * rr, eye.y, eye.z - Math.sin(angle) * rr, 0.0D, 0.04D, 0.0D);
					}
				}
			}
			case "blazing_flame" -> {
				if (clock % 3L == 0L) {
					int n = FxBudget.take(EffectQuality.minimal() ? 2 : 6, FxBudget.Priority.EXTRA);
					for (int i = 0; i < n; i++) {
						double angle = clock * 0.08D + Math.PI * 2.0D * i / Math.max(1, n);
						double x = eye.x + Math.cos(angle) * r;
						double z = eye.z + Math.sin(angle) * r;
						if (camera.distanceToSqr(x, eye.y, z) <= PARTICLE_RANGE * PARTICLE_RANGE) {
							CommonFx.add(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, eye.y + random.nextDouble() * 3.0D, z,
									-Math.sin(angle) * 0.05D, 0.08D, Math.cos(angle) * 0.05D);
						}
					}
				}
			}
			case "red_water" -> {
				if (clock % 5L == 0L) {
					int n = FxBudget.take(4, FxBudget.Priority.EXTRA);
					for (int i = 0; i < n; i++) {
						double angle = random.nextDouble() * Math.PI * 2.0D;
						double rr = Math.sqrt(random.nextDouble()) * r;
						CommonFx.add(CommonFx.dust(0xB01020, 1.4F), eye.x + Math.cos(angle) * rr, eye.y - 0.35D, eye.z + Math.sin(angle) * rr, 0.0D, 0.0D, 0.0D);
					}
				}
			}
			case "earth_fierce" -> {
				if (random.nextInt(6) == 0) {
					CommonFx.add(ParticleTypes.LAVA, eye.x + (random.nextFloat() - 0.5F) * r, eye.y - 0.3D, eye.z + (random.nextFloat() - 0.5F) * r, 0.0D,
							0.0D, 0.0D);
				}
			}
			case "sect_transfer", "ancient_transfer" -> {
				if (clock % 3L == 0L) {
					int n = FxBudget.take(4, FxBudget.Priority.EXTRA);
					double rr = core.ext().getIntOr("t", 0) > 0 ? 3.0D : 2.0D;
					for (int i = 0; i < n; i++) {
						double angle = clock * 0.07D + Math.PI * 2.0D * i / Math.max(1, n);
						CommonFx.add(CommonFx.dust(PURPLE, 0.8F), eye.x + Math.cos(angle) * rr, eye.y + 0.55D, eye.z + Math.sin(angle) * rr, 0.0D, 0.0D, 0.0D);
					}
				}
			}
			case "alarm_restriction" -> {
				if (clock % 6L == 0L) {
					double angle = clock * 0.05D;
					CommonFx.add(CommonFx.dust(0xD08A50, 0.8F), eye.x + Math.cos(angle) * 3.0D, eye.y + 1.6D, eye.z + Math.sin(angle) * 3.0D, 0.0D, 0.01D, 0.0D);
				}
			}
			case "ten_absolute" -> {
				int n = core.ext().getIntOr("n", 0);
				if (n >= 3 && clock % 4L == 0L) {
					int pts = FxBudget.take(8, FxBudget.Priority.EXTRA);
					for (int i = 0; i < pts; i++) {
						double angle = -clock * 0.03D + Math.PI * 2.0D * i / 8.0D;
						CommonFx.add(CommonFx.dust(i % 2 == 0 ? 0xE03030 : GOLD, 1.0F), eye.x + Math.cos(angle) * 2.5D, eye.y + 2.5D, eye.z + 5.0D
								+ Math.sin(angle) * 2.5D, 0.0D, 0.0D, 0.0D);
					}
				}
			}
			default -> {
			}
		}
	}

	/** Five coloured mist banks drifting around the ring (M1), reversing at each shift. */
	private static void mistBanks(Vec3 eye, double r, int dir, RandomSource random) {
		if (clock % 5L != 0L) {
			return;
		}
		int n = FxBudget.take(EffectQuality.minimal() ? 2 : 5, FxBudget.Priority.EXTRA);
		for (int i = 0; i < n; i++) {
			double angle = dir * clock * 0.02D + Math.PI * 2.0D * i / 5.0D;
			double rr = r * (0.75D + random.nextDouble() * 0.2D);
			CommonFx.add(CommonFx.dust(ELEMENTS[i % 5], 2.2F), eye.x + Math.cos(angle) * rr, eye.y + random.nextDouble(), eye.z + Math.sin(angle) * rr,
					-Math.sin(angle) * 0.02D * dir, 0.0D, Math.cos(angle) * 0.02D * dir);
		}
	}

	private static boolean skyElement(FormationSpec spec, FormasiClient.SyncCore core) {
		return switch (spec.id()) {
			case "heaven_absolute", "nine_bend_yellow_river" -> true;
			case "ten_thousand_immortals" -> (core.ext().getIntOr("p", 0) & 16) != 0;
			default -> false;
		};
	}

	/** The one sky-level element drawn this tick. */
	private static void drawSky(FormationSpec spec, FormasiClient.SyncCore core, Vec3 eye, RandomSource random) {
		if (clock % 4L != 0L) {
			return;
		}
		switch (spec.id()) {
			case "heaven_absolute" -> {
				int n = FxBudget.take(10, FxBudget.Priority.EXTRA);
				for (int i = 0; i < n; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double rr = Math.sqrt(random.nextDouble()) * 9.0D;
					CommonFx.add(CommonFx.dust(0x2A2A34, 3.0F), eye.x + Math.cos(angle) * rr, eye.y + 24.0D + random.nextDouble(), eye.z + Math.sin(angle) * rr,
							0.0D, 0.0D, 0.0D);
				}
			}
			case "nine_bend_yellow_river" -> {
				int n = FxBudget.take(9, FxBudget.Priority.EXTRA);
				double r = core.radius();
				for (int i = 0; i < n; i++) {
					// Nine bends: a serpentine of yellow mist across the zone.
					double t = (clock * 0.01D + i / 9.0D) % 1.0D;
					double along = (t * 2.0D - 1.0D) * r;
					double across = Math.sin(t * Math.PI * 9.0D) * r * 0.35D;
					CommonFx.add(CommonFx.dust(0xE8C860, 2.0F), eye.x + along, eye.y + 0.8D, eye.z + across, 0.0D, 0.0D, 0.0D);
				}
			}
			case "ten_thousand_immortals" -> {
				int n = FxBudget.take(16, FxBudget.Priority.EXTRA);
				double spin = clock * 0.01D;
				for (int i = 0; i < n; i++) {
					double angle = spin + Math.PI * 2.0D * i / 16.0D;
					boolean yang = Math.sin(angle - spin) > 0.0D;
					CommonFx.add(CommonFx.dust(yang ? 0xFFF4D0 : 0x202028, 1.6F), eye.x + Math.cos(angle) * 8.0D, eye.y + 20.0D, eye.z
							+ Math.sin(angle) * 8.0D, 0.0D, 0.0D, 0.0D);
					if (i % 4 == 0) {
						CommonFx.add(CommonFx.dust(GOLD, 1.0F), eye.x + Math.cos(-angle) * 10.0D, eye.y + 20.0D, eye.z + Math.sin(-angle) * 10.0D, 0.0D, 0.0D, 0.0D);
					}
				}
			}
			default -> {
			}
		}
	}

	// ================================================================== one-off shapes

	private static void drawAnim(ClientLevel level, Anim anim, RandomSource random) {
		switch (anim.kind) {
			case FormasiFxIds.AWAKEN -> {
				if (anim.age < 30) {
					CommonFx.spiralIn(anim.pos.add(0.0D, 0.6D, 0.0D), BLOOD, 2.0F - anim.age / 20.0F, 4, anim.age);
				} else if (anim.age == 30) {
					CommonFx.ring(anim.pos, 0xFF7A90, 3.0F, 32);
					CommonFx.pillar(anim.pos.add(0.0D, 0.5D, 0.0D), JADE, 4.0F);
				}
			}
			case FormasiFxIds.MARK_PILLAR -> {
				if (anim.age % 4 == 0) {
					CommonFx.pillar(anim.pos.add(0.0D, 0.5D, 0.0D), GOLD, 12.0F);
				}
			}
			case FxB.ROOT -> {
				if (anim.age % 3 == 0) {
					CommonFx.ring(anim.pos.add(0.0D, anim.f * (1.0D - anim.age / 30.0D) * 0.5D, 0.0D), anim.b == 1 ? 0xE8C0FF : 0xC0603A, 0.6F, 10);
				}
			}
			case FxB.OUTLINE -> {
				if (anim.age % 10 == 0) {
					Entity entity = level.getEntity(anim.b);
					if (entity != null && entity.isAlive()) {
						outline(entity);
					}
				}
			}
			default -> {
			}
		}
	}

	private static void outline(Entity entity) {
		Vec3 feet = entity.position();
		double half = entity.getBbWidth() / 2.0D + 0.15D;
		DustParticleOptions gold = CommonFx.dust(GOLD, 0.6F);
		int steps = Math.max(2, EffectQuality.scale(3));
		for (int corner = 0; corner < 4; corner++) {
			double cx = feet.x + (corner < 2 ? -half : half);
			double cz = feet.z + (corner % 2 == 0 ? -half : half);
			for (int i = 0; i <= steps; i++) {
				CommonFx.add(gold, cx, feet.y + entity.getBbHeight() * i / steps, cz, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	private static void ripple(Vec3 pos, int color) {
		CommonFx.ring(pos, color, 0.8F, 12);
		CommonFx.ring(pos, color, 1.6F, 18);
	}

	private static void bolt(Vec3 ground, int color, float height) {
		Vec3 top = ground.add(0.0D, height, 0.0D);
		CommonFx.bolt(top, ground.add(0.0D, 0.2D, 0.0D), color, EffectQuality.minimal() ? 4 : 8);
		CommonFx.burst(ground.add(0.0D, 0.3D, 0.0D), color, 8, 1.0F);
	}

	private static void sword(Vec3 from, Vec3 to, int color) {
		CommonFx.beam(from, to, color, 0.05F);
		CommonFx.add(ParticleTypes.CRIT, to, Vec3.ZERO, false);
	}

	private static void sweep(Vec3 pos, float radius) {
		int n = FxBudget.take(EffectQuality.minimal() ? 12 : 36, FxBudget.Priority.DETAIL);
		DustParticleOptions blade = CommonFx.dust(0x7FE0C8, 1.0F);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / Math.max(1, n);
			CommonFx.add(blade, pos.x + Math.cos(angle) * radius, pos.y + 1.0D, pos.z + Math.sin(angle) * radius, -Math.cos(angle) * 0.25D, 0.0D,
					-Math.sin(angle) * 0.25D);
		}
		CommonFx.add(ParticleTypes.SWEEP_ATTACK, pos.add(0.0D, 1.0D, 0.0D), Vec3.ZERO, false);
	}

	private static void shift(Vec3 pos, float radius, int dir) {
		int n = FxBudget.take(20, FxBudget.Priority.DETAIL);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / Math.max(1, n);
			int color = ELEMENTS[i % 5];
			double speed = (dir > 0 ? 1 : -1) * 0.12D;
			CommonFx.add(CommonFx.dust(color, 1.8F), pos.x + Math.cos(angle) * radius, pos.y + 0.8D, pos.z + Math.sin(angle) * radius,
					-Math.sin(angle) * speed, 0.0D, Math.cos(angle) * speed);
		}
	}

	private static void alarmRing(Vec3 edge, Vec3 eye, int kind, float radius) {
		CommonFx.ring(edge, RED, 1.2F, 16);
		CommonFx.ring(edge.add(0.0D, 0.5D, 0.0D), RED, 0.7F, 10);
		// The golden sound wave from the bell.
		CommonFx.ring(eye.add(0.0D, 1.0D, 0.0D), GOLD, Math.min(6.0F, Math.max(2.0F, radius * 0.2F)), 24);
	}

	private static void jump(Vec3 pos, int style, boolean out, RandomSource random) {
		int color = style == 1 ? 0xF0E8FF : PURPLE;
		if (out) {
			int n = FxBudget.take(30, FxBudget.Priority.DETAIL);
			for (int i = 0; i < n; i++) {
				CommonFx.add(ParticleTypes.END_ROD, pos.x + random.nextGaussian() * 0.3D, pos.y + random.nextDouble() * 2.0D, pos.z + random.nextGaussian() * 0.3D,
						0.0D, 0.12D + random.nextDouble() * 0.1D, 0.0D);
			}
			if (style == 1) {
				CommonFx.pillar(pos, color, 10.0F);
			}
		} else {
			CommonFx.ring(pos, color, 1.5F, 24);
			CommonFx.ring(pos, color, 3.0F, 32);
			CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), color, 16, 1.5F);
		}
	}

	private static void channel(Minecraft minecraft, Vec3 pos, int done, float length) {
		float progress = length <= 0.0F ? 1.0F : Math.min(1.0F, done / length);
		CommonFx.ring(pos, PURPLE, 1.0F + progress * 1.5F, 12);
		if (minecraft.player != null && minecraft.player.position().distanceToSqr(pos) <= 9.0D) {
			AlarmHud.channel(done, length);
		}
	}

	private static void ash(Vec3 pos, RandomSource random) {
		for (int i = 0; i < EffectQuality.scale(12); i++) {
			CommonFx.add(ParticleTypes.ASH, pos.x + random.nextGaussian() * 0.4D, pos.y + random.nextDouble() * 1.8D, pos.z + random.nextGaussian() * 0.4D,
					0.0D, 0.02D, 0.0D);
		}
		CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.add(0.0D, 0.8D, 0.0D), new Vec3(0.0D, 0.05D, 0.0D), false);
	}

	private static void fire(Vec3 pos, int kind, float height, RandomSource random) {
		int n = EffectQuality.scale(4);
		for (int i = 0; i < n; i++) {
			double x = pos.x + random.nextGaussian() * 0.3D;
			double y = pos.y + random.nextDouble() * height;
			double z = pos.z + random.nextGaussian() * 0.3D;
			switch (kind) {
				case 0 -> CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0D, 0.04D, 0.0D);
				case 2 -> CommonFx.add(CommonFx.dust(0xD02010, 1.2F), x, pos.y + 0.1D, z, 0.0D, 0.02D, 0.0D);
				case 3 -> CommonFx.add(ParticleTypes.LAVA, x, pos.y + 0.2D, z, 0.0D, 0.0D, 0.0D);
				default -> CommonFx.add(ParticleTypes.FLAME, x, y, z, 0.0D, 0.04D, 0.0D);
			}
		}
	}

	private static void water(Vec3 pos, float height, RandomSource random) {
		for (int i = 0; i < EffectQuality.scale(5); i++) {
			CommonFx.add(CommonFx.dust(0xB01020, 1.0F), pos.x + random.nextGaussian() * 0.3D, pos.y + random.nextDouble() * height,
					pos.z + random.nextGaussian() * 0.3D, 0.0D, -0.05D, 0.0D);
		}
	}

	private static void sand(Vec3 pos, float height, RandomSource random) {
		for (int i = 0; i < EffectQuality.scale(6); i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(CommonFx.dust(0xC0603A, 0.9F), pos.x + Math.cos(angle) * 0.6D, pos.y + random.nextDouble() * height, pos.z + Math.sin(angle) * 0.6D,
					-Math.sin(angle) * 0.15D, 0.02D, Math.cos(angle) * 0.15D);
		}
	}

	private static void petals(Vec3 pos, float height, RandomSource random) {
		for (int i = 0; i < EffectQuality.scale(6); i++) {
			CommonFx.add(ParticleTypes.CHERRY_LEAVES, pos.x + random.nextGaussian() * 0.3D, pos.y + height * 0.7D + random.nextDouble() * 0.4D,
					pos.z + random.nextGaussian() * 0.3D, 0.0D, -0.02D, 0.0D);
		}
	}

	private static void thunder(Vec3 pos, float height, RandomSource random) {
		CommonFx.bolt(pos.add(0.0D, height + 3.0D, 0.0D), pos.add(0.0D, height * 0.5D, 0.0D), 0xFFF2C0, 3);
	}

	private static void grand(Vec3 pos, int which, float f) {
		if (which == 0) {
			CommonFx.ring(pos, 0xFFF0C0, 6.0F, 48);
			CommonFx.pillar(pos.add(0.0D, 1.0D, 0.0D), JADE, 16.0F);
		} else {
			CommonFx.ring(pos.add(0.0D, 2.5D, 5.0D), 0xE03030, 3.0F, 32);
			CommonFx.burst(pos.add(0.0D, 2.5D, 5.0D), GOLD, 20, 2.0F);
		}
	}

	// ================================================================== slot markers (gizmos)

	private static void drawMarks() {
		if (MARKS.isEmpty()) {
			return;
		}
		long now = Util.getMillis();
		MARKS.removeIf(mark -> mark.until() < now);
		float pulse = EffectQuality.reduceMotion() ? 0.8F : 0.55F + 0.45F * (float) Math.sin(now / 260.0D);
		try {
			for (Mark mark : MARKS) {
				AABB box = new AABB(mark.pos());
				switch (mark.kind()) {
					case 0 -> {
						double y = mark.pos().getY() + 1.02D;
						double x0 = mark.pos().getX();
						double z0 = mark.pos().getZ();
						Gizmos.line(new Vec3(x0, y, z0), new Vec3(x0 + 1.0D, y, z0 + 1.0D), argb(240, RED), 3.0F);
						Gizmos.line(new Vec3(x0 + 1.0D, y, z0), new Vec3(x0, y, z0 + 1.0D), argb(240, RED), 3.0F);
						Gizmos.cuboid(box.inflate(0.01D), GizmoStyle.stroke(argb(140, RED), 1.2F));
					}
					case 1 -> Gizmos.cuboid(box.deflate(0.22D), GizmoStyle.strokeAndFill(argb(Math.round(230 * pulse), GOLD), 1.5F,
							argb(Math.round(90 * pulse), GOLD)));
					default -> Gizmos.cuboid(box.inflate(0.01D), GizmoStyle.stroke(argb(220, GREEN), 2.0F));
				}
			}
		} catch (IllegalStateException exception) {
			// No gizmo collector in this phase of the tick.
		}
	}

	private static int stateColor(int state) {
		return switch (state) {
			case 1 -> RED;
			case 2 -> 0x3A3A3A;
			case 3 -> 0xFFB040;
			case 4 -> GOLD;
			case 5 -> 0xB07CFF;
			default -> 0x9AA4A0;
		};
	}

	private static int familyColor(int ordinal) {
		Family[] families = Family.values();
		return ordinal >= 0 && ordinal < families.length ? families[ordinal].color() : JADE;
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private FormasiFx() {
	}
}
