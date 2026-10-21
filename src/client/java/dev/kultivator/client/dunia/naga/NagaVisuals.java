package dev.kultivator.client.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.SegmentChain;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.dunia.naga.NagaKind;
import dev.kultivator.dunia.naga.NagaLiarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The client state of every Jiao in view (DESIGN 34.5.11): its body chain (the spine's {@link SegmentChain}, pushed once
 * per client tick with the head's pose, so the body follows the path the head flew), the breath, Lemah and flight
 * particles. Budgets: body segments 8 / 14 / 20 by effect quality (the chain itself halves them with distance), breath
 * particles 4 / 10 / 18 per tick, nothing particle-wise beyond 96 blocks; other players' Jiao are scaled by
 * {@code othersScale}. Nothing here is sent to the server.
 */
public final class NagaVisuals {

	/** Blocks between two body segments. */
	public static final float SPACING = 1.0F;
	private static final int[] SEGMENTS = {8, 14, 24};
	private static final int[] BREATH_PARTICLES = {4, 10, 18};
	private static final double RANGE = 256.0D;

	/** The client side of one Jiao. */
	public static final class Visual {
		final SegmentChain chain;
		final int capacity;
		Vec3 last;
		long seenTick;
		/** Smoothed jaw opening 0..1. */
		float jaw;
		float jawBefore;

		Visual(int capacity) {
			this.capacity = capacity;
			this.chain = new SegmentChain(capacity, SPACING);
		}

		public SegmentChain chain() {
			return chain;
		}

		public float jaw(float partial) {
			return Mth.lerp(partial, jawBefore, jaw);
		}
	}

	private static final Map<Integer, Visual> VISUALS = new HashMap<>();
	/** Additive quads module B may still draw this frame (half the 7b cap; reset at the start of every level frame). */
	private static int quadsLeft;

	/** Start of a level frame (Fabric {@code LevelRenderEvents.START_MAIN}). */
	static void beginFrame() {
		quadsLeft = Math.max(0, dev.kultivator.client.epic.EpicBudget.quadCap() / 2);
	}

	/** Takes {@code quads} from this frame's budget; false (nothing drawn) when it would overflow. */
	public static boolean takeQuads(int quads) {
		if (quads > quadsLeft) {
			return false;
		}
		quadsLeft -= quads;
		return true;
	}
	private static @Nullable ClientLevel lastLevel;
	private static long ticks;

	/** The body segments drawn at the current quality (8 / 14 / up to the server's {@code segmentsMax}, default 20). */
	public static int capacity(NagaLiarEntity dragon) {
		return Math.min(dragon.segmentsMax(), SEGMENTS[EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1]);
	}

	public static @Nullable Visual of(NagaLiarEntity dragon) {
		return VISUALS.get(dragon.getId());
	}

	static void clear() {
		VISUALS.clear();
	}

	/** End of every client tick. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			VISUALS.clear();
		}
		if (level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		ticks++;
		Vec3 eye = minecraft.player.position();
		for (NagaLiarEntity dragon : level.getEntitiesOfClass(NagaLiarEntity.class, new AABB(eye, eye).inflate(RANGE), Entity::isAlive)) {
			int capacity = capacity(dragon);
			Visual visual = VISUALS.get(dragon.getId());
			if (visual == null || visual.capacity != capacity) {
				visual = new Visual(capacity);
				VISUALS.put(dragon.getId(), visual);
			}
			Vec3 head = dragon.position();
			if (visual.last != null && visual.last.distanceToSqr(head) > 64.0D) {
				// a summon or a teleport: lay the body out again instead of a 60-block streak
				visual.chain.reset();
			}
			visual.last = head;
			visual.seenTick = ticks;
			visual.chain.push(head.add(0.0D, 0.6D, 0.0D), dragon.getYRot(), dragon.getXRot());
			visual.jawBefore = visual.jaw;
			visual.jaw = Mth.approach(visual.jaw, jawTarget(dragon), 0.18F);
			double distanceSq = dragon.distanceToSqr(minecraft.player);
			if (distanceSq < 96.0D * 96.0D) {
				particles(level, dragon, distanceSq);
			}
		}
		Iterator<Map.Entry<Integer, Visual>> iterator = VISUALS.entrySet().iterator();
		while (iterator.hasNext()) {
			if (ticks - iterator.next().getValue().seenTick > 40) {
				iterator.remove();
			}
		}
	}

	/** How far the jaw opens: the breath (gathering, then wide), the roar, the bite, a hurt Lemah pant. */
	static float jawTarget(NagaLiarEntity dragon) {
		int skill = dragon.skill();
		int t = dragon.skillTicks();
		if (skill == NagaLiarEntity.SKILL_BREATH) {
			return t < NagaLiarEntity.BREATH_TELEGRAPH ? 0.35F + 0.4F * t / NagaLiarEntity.BREATH_TELEGRAPH : 1.0F;
		}
		if (skill == NagaLiarEntity.SKILL_ROAR) {
			return t < NagaLiarEntity.ROAR_TELEGRAPH ? 0.3F : 1.0F;
		}
		if (skill == NagaLiarEntity.SKILL_BITE) {
			return 0.8F;
		}
		if (dragon.hasFlag(NagaLiarEntity.FLAG_LEMAH)) {
			return 0.25F + 0.15F * Mth.sin(dragon.tickCount * 0.2F);
		}
		return 0.05F;
	}

	private static void particles(ClientLevel level, NagaLiarEntity dragon, double distanceSq) {
		RandomSource random = level.getRandom();
		boolean own = dragon.getControllingPassenger() == Minecraft.getInstance().player
				|| Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(dragon.ownerId());
		float others = own ? 1.0F : EffectQuality.othersScale();
		if (others <= 0.0F) {
			return;
		}
		NagaKind kind = dragon.kind();
		Vec3 mouth = dragon.mouth();
		int skill = dragon.skill();
		int t = dragon.skillTicks();
		int quality = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1;
		if (skill == NagaLiarEntity.SKILL_BREATH) {
			if (t < NagaLiarEntity.BREATH_TELEGRAPH) {
				// light gathers at the mouth
				if (EffectQuality.chance(random, 0.6F * others)) {
					CommonFx.spiralIn(mouth, kind.glow, 1.4F, 4, dragon.tickCount);
				}
			} else if (t < NagaLiarEntity.BREATH_TELEGRAPH + NagaLiarEntity.BREATH_ACTIVE) {
				Vec3 dir = breathDir(level, dragon, mouth);
				int n = Math.max(1, Math.round(BREATH_PARTICLES[quality] * others));
				for (int i = 0; i < n; i++) {
					double along = random.nextDouble() * NagaLiarEntity.BREATH_LENGTH;
					double spread = along * 0.42D;
					Vec3 at = mouth.add(dir.scale(along)).add((random.nextDouble() - 0.5D) * spread, (random.nextDouble() - 0.5D) * spread,
							(random.nextDouble() - 0.5D) * spread);
					Vec3 velocity = dir.scale(0.35D + random.nextDouble() * 0.25D);
					CommonFx.add(breathParticle(kind, random), at, velocity, false);
				}
				if (kind == NagaKind.PETIR && EffectQuality.chance(random, 0.35F * others)) {
					Vec3 end = mouth.add(dir.scale(4.0D + random.nextDouble() * 9.0D));
					CommonFx.bolt(mouth, end, kind.glow, 5);
				}
			}
		}
		if (dragon.hasFlag(NagaLiarEntity.FLAG_LEMAH) && EffectQuality.chance(random, 0.25F * others)) {
			// heavy breath from the nostrils
			CommonFx.add(ParticleTypes.CLOUD, mouth.x, mouth.y + 0.2D, mouth.z, (random.nextDouble() - 0.5D) * 0.05D, 0.03D,
					(random.nextDouble() - 0.5D) * 0.05D);
		}
		if (dragon.hasFlag(NagaLiarEntity.FLAG_CONTEST) && EffectQuality.chance(random, 0.5F * others)) {
			CommonFx.ring(dragon.position().add(0.0D, 0.6D, 0.0D), 0xFFD27A, 2.4F, 6);
		}
		if (distanceSq < 48.0D * 48.0D && quality > 0 && EffectQuality.chance(random, 0.12F * others)) {
			// the scale glint and the element trail along the body
			Visual visual = VISUALS.get(dragon.getId());
			if (visual != null && visual.chain.segments() > 2) {
				int index = 1 + random.nextInt(Math.max(1, visual.chain.capacity() - 1));
				Vec3 at = visual.chain.pose(index, 1.0F).pos();
				CommonFx.add(trailParticle(kind), at.x, at.y, at.z, 0.0D, kind == NagaKind.AWAN ? 0.01D : -0.01D, 0.0D);
			}
		}
		if (dragon.isTamed() && dragon.tier() >= BeastData.TOP_TIER && EffectQuality.chance(random, 0.2F * others)) {
			CommonFx.add(CommonFx.glow(0xFFF4C8, 0.9F, 20), mouth, new Vec3(0.0D, 0.02D, 0.0D), false);
		}
	}

	/** The breath direction: at its synced aim when it has one, else straight ahead. */
	static Vec3 breathDir(ClientLevel level, NagaLiarEntity dragon, Vec3 mouth) {
		Entity aim = dragon.aimId() >= 0 ? level.getEntity(dragon.aimId()) : null;
		Vec3 dir;
		if (aim != null) {
			dir = aim.getBoundingBox().getCenter().subtract(mouth);
		} else {
			float yaw = dragon.getYRot() * Mth.DEG_TO_RAD;
			float pitch = dragon.getXRot() * Mth.DEG_TO_RAD;
			dir = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		}
		return dir.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : dir.normalize();
	}

	private static ParticleOptions breathParticle(NagaKind kind, RandomSource random) {
		return switch (kind) {
			case AIR -> random.nextBoolean() ? ParticleTypes.SPLASH : CommonFx.glow(kind.glow, 1.1F, 12);
			case AWAN -> random.nextInt(3) == 0 ? ParticleTypes.CLOUD : CommonFx.glow(0xFFFFFF, 1.0F, 10);
			case PETIR -> random.nextBoolean() ? ParticleTypes.ELECTRIC_SPARK : CommonFx.glow(kind.glow, 1.0F, 8);
		};
	}

	private static ParticleOptions trailParticle(NagaKind kind) {
		return switch (kind) {
			case AIR -> ParticleTypes.DRIPPING_WATER;
			case AWAN -> CommonFx.glow(0xF4FFF8, 0.7F, 30);
			case PETIR -> ParticleTypes.ELECTRIC_SPARK;
		};
	}

	private NagaVisuals() {
	}
}
