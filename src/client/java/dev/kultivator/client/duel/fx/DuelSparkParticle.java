package dev.kultivator.client.duel.fx;

import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.fx.DuelParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * {@code kultivator:duel_spark} (DESIGN 35.4.2): one spark struck from steel. A hot streak drawn along its screen-space
 * velocity (a bright head and up to three fading tail quads behind it, so a fast spark reads as a line of fire and a slow
 * one as a dot), under gravity 0.6, with light air drag. It bounces once on the top of a solid block (damping 0.35, the
 * sideways speed kept at 55 %), then skids and dies. Its colour cools over its life: white-hot {@code 0xFFF8E0} → yellow
 * {@code 0xFFC850} → orange {@code 0xFF7A20} → dark red {@code 0x802010}; a qi spark starts in the swing's qi colour.
 * Everything ages by the presentation clock σ ({@link DuelTime}): a hit-stop freezes the sparks in the air with their
 * streaks, slow motion stretches them. Collision reads one block state per tick and never changes anything. A spark of a
 * client-predicted deflect that the server then corrected fades out in 2 ticks ({@link DuelFx#groupCancelled}).
 */
public final class DuelSparkParticle extends SingleQuadParticle {

	private static final int WHITE_HOT = 0xFFF8E0;
	private static final int YELLOW = 0xFFC850;
	private static final int ORANGE = 0xFF7A20;
	private static final int DARK_RED = 0x802010;
	/** Vanilla's per-tick gravity step (0.04) × the spark's gravity 0.6. */
	private static final double GRAVITY = 0.04D * 0.6D;
	private static final double DRAG = 0.955D;

	private final SpriteSet sprites;
	private float ageF;
	private float lifeF = 10.0F;
	private float size = 0.12F;
	private float drawSize;
	private int tint = -1;
	private float tintMix;
	private int group;
	private boolean bounced;
	private boolean resting;
	private float fadeFrom = -1.0F;

	DuelSparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.hasPhysics = false;
		this.gravity = 0.0F;
		this.friction = 1.0F;
		this.lifetime = 10;
		setSprite(sprites.get(random));
	}

	/** Size (full width in blocks), life in presentation ticks, an optional qi tint ({@code -1} none), the prediction group. */
	void configure(float width, float life, int qiTint, float qiMix, int predictionGroup) {
		this.size = Math.max(0.02F, width);
		this.lifeF = Math.max(2.0F, life);
		this.lifetime = Math.max(2, Math.round(life * 4.0F));
		this.tint = qiTint;
		this.tintMix = qiMix;
		this.group = predictionGroup;
		this.quadSize = size * 0.5F;
		colour(0.0F);
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		oRoll = roll;
		if (removed) {
			return;
		}
		if (group != 0 && fadeFrom < 0.0F && DuelFx.groupCancelled(group)) {
			fadeFrom = ageF;
			lifeF = Math.min(lifeF, ageF + 2.0F);
		}
		float s = DuelTime.frozen() ? 0.0F : DuelTime.sigma();
		if (s <= 0.0F) {
			return;
		}
		ageF += s;
		if (ageF >= lifeF) {
			remove();
			return;
		}
		if (!resting) {
			step(s);
		}
		float t = ageF / lifeF;
		colour(t);
		alpha = t < 0.7F ? 1.0F : Math.max(0.0F, 1.0F - (t - 0.7F) / 0.3F);
		if (fadeFrom >= 0.0F) {
			alpha *= Math.max(0.0F, 1.0F - (ageF - fadeFrom) / 2.0F);
		}
	}

	/** One σ-scaled step of motion with the single bounce. */
	private void step(float s) {
		yd -= GRAVITY * s;
		double drag = Math.pow(DRAG, s);
		xd *= drag;
		yd *= drag;
		zd *= drag;
		double nx = x + xd * s;
		double ny = y + yd * s;
		double nz = z + zd * s;
		BlockPos cell = BlockPos.containing(nx, ny, nz);
		BlockState state = level.getBlockState(cell);
		if (!state.isAir()) {
			VoxelShape shape = state.getCollisionShape(level, cell);
			if (!shape.isEmpty()) {
				double top = cell.getY() + shape.max(Direction.Axis.Y);
				if (yd < 0.0D && y >= top - 0.05D && ny < top) {
					ny = top + 0.01D;
					if (!bounced) {
						bounced = true;
						yd = -yd * 0.35D;
						xd *= 0.55D;
						zd *= 0.55D;
					} else {
						resting = true;
						yd = 0.0D;
						xd *= 0.25D;
						zd *= 0.25D;
					}
				} else if (ny < top && y < top - 0.05D) {
					// a wall: stop the sideways motion, slide down along it
					nx = x;
					nz = z;
					xd *= -0.15D;
					zd *= -0.15D;
				}
			}
		}
		x = nx;
		y = ny;
		z = nz;
	}

	private void colour(float t) {
		int rgb;
		if (t < 0.25F) {
			rgb = DuelGeo.mix(WHITE_HOT, YELLOW, t / 0.25F);
		} else if (t < 0.55F) {
			rgb = DuelGeo.mix(YELLOW, ORANGE, (t - 0.25F) / 0.30F);
		} else {
			rgb = DuelGeo.mix(ORANGE, DARK_RED, (t - 0.55F) / 0.45F);
		}
		if (tint >= 0 && tintMix > 0.0F) {
			rgb = DuelGeo.mix(rgb, tint, tintMix * Math.max(0.0F, 1.0F - t * 1.5F));
		}
		setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
	}

	@Override
	public float getQuadSize(float partial) {
		return drawSize > 0.0F ? drawSize : quadSize;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partial) {
		return LightCoordsUtil.FULL_BRIGHT;
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partial) {
		Vec3 cam = camera.position();
		float p = DuelTime.frozen() ? 1.0F : partial;
		float px = (float) (Mth.lerp(p, xo, x) - cam.x);
		float py = (float) (Mth.lerp(p, yo, y) - cam.y);
		float pz = (float) (Mth.lerp(p, zo, z) - cam.z);
		Vector3f velocity = new Vector3f((float) xd, (float) yd, (float) zd);
		float speed = velocity.length();
		Quaternionf rotation = new Quaternionf(camera.rotation());
		if (speed > 1.0E-4F) {
			Vector3f screen = new Vector3f(velocity);
			new Quaternionf(camera.rotation()).conjugate().transform(screen);
			rotation.rotateZ((float) Math.atan2(screen.y, screen.x));
		}
		float t = Mth.clamp(ageF / lifeF, 0.0F, 1.0F);
		float head = size * 0.5F * (1.0F - 0.35F * t);
		float baseAlpha = alpha;
		drawSize = head;
		extractRotatedQuad(state, rotation, px, py, pz, partial);
		// the streak: up to three tail quads back along the velocity, smaller and fainter
		int tails = resting ? 0 : Math.min(3, (int) (speed * 9.0F));
		if (tails > 0) {
			float step = head * 0.75F / speed;
			for (int i = 1; i <= tails; i++) {
				drawSize = head * (1.0F - 0.18F * i);
				alpha = baseAlpha * (1.0F - 0.26F * i);
				extractRotatedQuad(state, rotation, px - velocity.x * step * i, py - velocity.y * step * i, pz - velocity.z * step * i, partial);
			}
		}
		alpha = baseAlpha;
		drawSize = 0.0F;
	}

	// ================================================================ spawning

	private static boolean ready;

	static void markReady() {
		ready = true;
	}

	/**
	 * Spawns one spark; false when refused (types missing, the vanilla "Particles" setting, the token budget). The caller
	 * has already applied the per-event and per-tick caps of 35.4.12.
	 */
	static boolean spawn(Vec3 pos, Vec3 velocity, float width, float life, int qiTint, float qiMix, int group, FxBudget.Priority priority) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ready || minecraft.level == null) {
			return false;
		}
		int granted = FxBudget.take(1, priority);
		if (granted < 1) {
			return false;
		}
		Particle particle = minecraft.particleEngine.createParticle(DuelParticles.SPARK, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		if (particle instanceof DuelSparkParticle spark) {
			spark.configure(width, life, qiTint, qiMix, group);
			return true;
		}
		FxBudget.refund(granted);
		return false;
	}

	static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public @Nullable Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux,
				double zAux, RandomSource random) {
			return new DuelSparkParticle(level, x, y, z, xAux, yAux, zAux, sprites, random);
		}
	}
}
