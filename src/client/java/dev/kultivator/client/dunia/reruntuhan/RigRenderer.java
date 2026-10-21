package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws the demon kings of the Penjara Iblis Kuno and the ten Penjaga Lantai of the Menara Seratus Lantai (DESIGN
 * 34.5.6, 34.5.7) from the module's cuboid part models ({@link RModels}), posed per frame on one hitbox:
 * <ul>
 * <li>Xiangliu: the coiled body and nine necks fanned over it, each swaying on its own, rearing back before the poison
 * lines and striking, rising for the coil, looking down for the pools;</li>
 * <li>the Bull Demon King: legs, torso, a bull head with sweeping horns, arms, the iron club raised and brought down for
 * Gada Besi, the charge leaning in, the roar thrown back;</li>
 * <li>Chiyou: the bronze war god with four arms (two ge halberds), the war banner on his back waving in his storm;</li>
 * <li>the quadruped guardians (Lembu Besi, Kura-kura Batu with its domed shell, Harimau Petir, Rubah Ilusi with three
 * tails, Kilin Emas) on a diagonal gait; the two cranes (Bangau Api, Bangau Abadi) hovering on flapping wings; the
 * serpents (Ular Giok on the ground, Naga Bayangan floating) as a chain of segments slithering behind the head.</li>
 * </ul>
 * Rising out of the pit or the floor, the death collapse and a red pulse while enraged come from the synced flags. Chains
 * of segments shorten on Minimal effects and beyond 48 blocks (rule V3). Entity models only: nothing touches a block.
 */
public class RigRenderer<T extends RCreature> extends EntityRenderer<T, RigRenderer.State> {

	private static final double FAR_SQ = 48.0D * 48.0D;
	private final boolean king;

	public static final class State extends EntityRenderState {
		int kind;
		float bodyYaw;
		float headYaw;
		float limb;
		float limbAmount;
		float swing;
		int skill;
		float skillAge;
		int flags;
		boolean hurt;
		float death;
		int id;
	}

	public RigRenderer(EntityRendererProvider.Context context, boolean king) {
		super(context);
		this.king = king;
		this.shadowRadius = king ? 1.6F : 1.0F;
		// a resource reload rebuilds the renderers: the resolved part models are stale now
		RModels.clear();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
		return entity.getBoundingBox().inflate(king ? 5.0D : 3.5D);
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
		state.headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot) - state.bodyYaw), -50.0F, 50.0F);
		state.limb = entity.walkAnimation.position(partialTicks);
		state.limbAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
		state.swing = entity.getSwingAnimation(partialTicks);
		state.skill = entity.skill();
		state.skillAge = entity.skillAge() + partialTicks;
		state.flags = entity.flags();
		state.hurt = entity.hurtTime > 0;
		state.death = entity.deathTime > 0 ? (entity.deathTime + partialTicks) / 20.0F : 0.0F;
		state.id = entity.getId();
	}

	/** What a pose call needs. */
	private static final class Ctx {
		final PoseStack pose;
		final SubmitNodeCollector collector;
		final int light;
		final int overlay;
		final State s;
		final boolean far;

		Ctx(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, State s, boolean far) {
			this.pose = pose;
			this.collector = collector;
			this.light = light;
			this.overlay = overlay;
			this.s = s;
			this.far = far;
		}

		/** Part {@code id} at (x, y, z) of the current frame, turned yaw / pitch / roll (degrees, applied in that order). */
		void part(String id, float x, float y, float z, float yaw, float pitch, float roll) {
			pose.pushPose();
			pose.translate(x, y, z);
			turn(yaw, pitch, roll);
			RModels.submit(id, pose, collector, light, overlay);
			pose.popPose();
		}

		void push(float x, float y, float z, float yaw, float pitch, float roll) {
			pose.pushPose();
			pose.translate(x, y, z);
			turn(yaw, pitch, roll);
		}

		void pop() {
			pose.popPose();
		}

		private void turn(float yaw, float pitch, float roll) {
			if (yaw != 0.0F) {
				pose.rotate(Axis.YP.rotationDegrees(yaw));
			}
			if (pitch != 0.0F) {
				pose.rotate(Axis.XP.rotationDegrees(pitch));
			}
			if (roll != 0.0F) {
				pose.rotate(Axis.ZP.rotationDegrees(roll));
			}
		}
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean enraged = (s.flags & RCreature.FLAG_ENRAGED) != 0;
		boolean telegraph = (s.flags & RCreature.FLAG_TELEGRAPH) != 0;
		// the red overlay flashes while enraged (twice a second) or while a skill is being called (a short pulse)
		boolean red = s.hurt || enraged && Mth.sin(s.ageInTicks * 0.6F) > 0.55F || telegraph && Mth.sin(s.ageInTicks * 1.2F) > 0.85F;
		int overlay = OverlayTexture.pack(0.0F, red);
		Ctx c = new Ctx(pose, collector, s.lightCoords, overlay, s, s.distanceToCameraSq > FAR_SQ || EffectQuality.minimal());
		pose.pushPose();
		pose.rotate(Axis.YP.rotationDegrees(-s.bodyYaw));
		// the part models face -z; the entity's front is +z after the body yaw
		pose.rotate(Axis.YP.rotationDegrees(180.0F));
		if ((s.flags & RCreature.FLAG_RISING) != 0) {
			float rise = king ? Mth.clamp(s.skillAge / 60.0F, 0.0F, 1.0F) : 1.0F;
			pose.translate(0.0F, -(1.0F - rise) * (1.0F - rise) * 5.0F, 0.0F);
		}
		if (s.death > 0.0F) {
			float d = Math.min(1.0F, s.death);
			pose.translate(0.0F, -d * 0.8F, 0.0F);
			pose.rotate(Axis.ZP.rotationDegrees(d * 30.0F));
		}
		if (king) {
			switch (s.kind) {
				case RajaIblisEntity.XIANGLIU -> xiangliu(c);
				case RajaIblisEntity.BANTENG -> banteng(c);
				default -> chiyou(c);
			}
		} else {
			switch (s.kind) {
				case 0 -> quadruped(c, "lembu_besi", 1.3F, 16, 14, 28, 0.0F, 1);
				case 1 -> serpent(c, "ular_giok", 1.2F, false);
				case 2 -> crane(c, "bangau_api", 0.85F);
				case 3 -> quadruped(c, "kura_batu", 1.35F, 18, 8, 22, 35.0F, 1);
				case 4 -> quadruped(c, "harimau_petir", 1.15F, 12, 11, 26, 0.0F, 1);
				case 5 -> quadruped(c, "rubah_ilusi", 0.75F, 9, 8, 20, 0.0F, 3);
				case 6 -> quadruped(c, "kilin_emas", 1.25F, 12, 12, 24, 0.0F, 1);
				case 7 -> crane(c, "bangau_abadi", 0.9F);
				default -> serpent(c, "naga_bayangan", 1.25F, true);
			}
		}
		pose.popPose();
		super.submit(s, pose, collector, camera);
	}

	// ================================================================== the demon kings

	private static float walk(State s, float speed) {
		return Mth.cos(s.limb * speed) * s.limbAmount;
	}

	private static void xiangliu(Ctx c) {
		State s = c.s;
		float scale = 1.7F;
		c.pose.scale(scale, scale, scale);
		float t = s.ageInTicks;
		c.part("xiangliu_coil", 0.0F, 0.75F, 0.0F, 4.0F * Mth.sin(t * 0.03F), 0.0F, 0.0F);
		int heads = c.far ? 5 : 9;
		for (int n = 0; n < heads; n++) {
			int i = heads == 9 ? n : n * 2;
			float fan = (i - 4) * 15.0F + 6.0F * Mth.sin(t * 0.05F + i);
			float tilt = 22.0F + Math.abs(i - 4) * 6.0F + 5.0F * Mth.sin(t * 0.07F + i * 1.3F);
			float look = -12.0F;
			switch (s.skill) {
				case 1 -> {
					// rear back, fan narrow, then snap forward on tick 30
					fan *= 0.7F;
					tilt = s.skillAge < 30.0F ? -8.0F + 4.0F * Mth.sin(t * 0.5F + i) : 48.0F;
					look = s.skillAge < 30.0F ? 5.0F : -20.0F;
				}
				case 2 -> {
					tilt = s.skillAge < 28.0F ? 0.0F : 70.0F;
					look = s.skillAge < 28.0F ? 15.0F : -40.0F;
				}
				case 3 -> {
					tilt = 40.0F;
					look = -45.0F;
				}
				default -> {
				}
			}
			float fanRad = fan * Mth.DEG_TO_RAD;
			float tiltRad = tilt * Mth.DEG_TO_RAD;
			float rx = Mth.sin(fanRad) * -0.3F;
			float rz = Mth.cos(fanRad) * -0.3F;
			float ry = 1.45F;
			c.part("xiangliu_neck", rx, ry, rz, fan, -tilt, 0.0F);
			// the neck's tip: R_y(fan) R_x(-tilt) (0, 1.5, 0)
			float ny = 1.5F * Mth.cos(tiltRad);
			float nzl = -1.5F * Mth.sin(tiltRad);
			float hx = rx + nzl * Mth.sin(fanRad);
			float hz = rz + nzl * Mth.cos(fanRad);
			c.part("xiangliu_head", hx, ry + ny, hz, fan + (i == 4 ? s.headYaw * 0.5F : 0.0F), look, 0.0F);
		}
	}

	private static void banteng(Ctx c) {
		State s = c.s;
		float scale = 1.05F;
		c.pose.scale(scale, scale, scale);
		float walk = walk(s, 0.5F);
		float lean = 0.0F;
		float head = 0.0F;
		float armL = walk * 25.0F;
		float armR = -walk * 25.0F;
		float rollL = 0.0F;
		float rollR = 0.0F;
		float legSpeed = 1.0F;
		armR += Mth.sin(s.swing * Mth.PI) * 110.0F;
		switch (s.skill) {
			case 1 -> {
				lean = s.skillAge < 24.0F ? 25.0F : 35.0F;
				head = -30.0F;
				legSpeed = s.skillAge >= 24.0F ? 2.2F : 0.0F;
			}
			case 2 -> {
				float raise = Mth.clamp(s.skillAge / 24.0F, 0.0F, 1.0F);
				float hit = Mth.clamp((s.skillAge - 24.0F) / 3.0F, 0.0F, 1.0F);
				armR = 170.0F * raise - 130.0F * hit;
				armL = 120.0F * raise - 100.0F * hit;
				lean = -8.0F * raise + 20.0F * hit;
			}
			case 3 -> {
				float roar = s.skillAge < 20.0F ? Mth.clamp(s.skillAge / 20.0F, 0.0F, 1.0F) : 1.0F;
				head = 25.0F * roar + (s.skillAge >= 20.0F ? 10.0F : 0.0F);
				rollL = -50.0F * roar;
				rollR = 50.0F * roar;
				lean = -10.0F * roar;
			}
			default -> {
			}
		}
		float stride = legSpeed == 0.0F ? 0.0F : Mth.cos(s.limb * 0.5F * legSpeed) * Math.max(s.limbAmount, legSpeed > 1.0F ? 1.0F : 0.0F);
		c.part("banteng_leg", -0.32F, 1.5F, 0.0F, 0.0F, stride * 35.0F, 0.0F);
		c.part("banteng_leg", 0.32F, 1.5F, 0.0F, 0.0F, -stride * 35.0F, 0.0F);
		c.push(0.0F, 1.5F, 0.0F, 0.0F, -lean, 0.0F);
		c.part("banteng_torso", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		c.part("banteng_head", 0.0F, 1.55F, -0.05F, s.headYaw, head, 0.0F);
		c.part("banteng_arm", 0.72F, 1.45F, 0.0F, 0.0F, armL, rollL);
		c.push(-0.72F, 1.45F, 0.0F, 0.0F, armR, rollR);
		c.part("banteng_arm", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		c.part("banteng_club", 0.0F, -1.35F, -0.05F, 0.0F, 90.0F, 0.0F);
		c.pop();
		c.pop();
	}

	private static void chiyou(Ctx c) {
		State s = c.s;
		float scale = 1.0F;
		c.pose.scale(scale, scale, scale);
		float walk = walk(s, 0.5F);
		float t = s.ageInTicks;
		float upper = walk * 20.0F + Mth.sin(s.swing * Mth.PI) * 100.0F;
		float lower = -walk * 15.0F + 20.0F;
		float headPitch = 0.0F;
		float banner = 5.0F * Mth.sin(t * 0.08F);
		switch (s.skill) {
			case 1 -> {
				upper = 170.0F * Mth.clamp(s.skillAge / 20.0F, 0.0F, 1.0F);
				lower = 150.0F * Mth.clamp(s.skillAge / 24.0F, 0.0F, 1.0F);
				headPitch = 20.0F;
			}
			case 2 -> {
				upper = s.skillAge < 24.0F ? 120.0F * Mth.clamp(s.skillAge / 16.0F, 0.0F, 1.0F) : 30.0F;
				banner = 25.0F * Mth.sin(t * 0.5F);
			}
			case 3 -> {
				upper = 150.0F;
				lower = 110.0F;
				headPitch = 30.0F;
				banner = 18.0F * Mth.sin(t * 0.35F);
			}
			default -> {
			}
		}
		c.part("chiyou_leg", -0.3F, 1.5F, 0.0F, 0.0F, walk * 32.0F, 0.0F);
		c.part("chiyou_leg", 0.3F, 1.5F, 0.0F, 0.0F, -walk * 32.0F, 0.0F);
		c.push(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F);
		c.part("chiyou_torso", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		c.part("chiyou_head", 0.0F, 1.55F, 0.05F, s.headYaw, headPitch, 0.0F);
		c.part("chiyou_banner", 0.0F, 1.0F, 0.55F, 0.0F, -8.0F, banner);
		for (int side = -1; side <= 1; side += 2) {
			c.push(side * 0.62F, 1.35F, 0.0F, 0.0F, upper, side * 15.0F);
			c.part("chiyou_arm", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
			c.part("chiyou_ge", 0.0F, -1.35F, -0.05F, 0.0F, 0.0F, 0.0F);
			c.pop();
			if (!c.far) {
				c.part("chiyou_arm", side * 0.56F, 0.85F, 0.05F, 0.0F, lower, side * 35.0F);
			}
		}
		c.pop();
	}

	// ================================================================== the floor guardians

	/**
	 * A four-legged guardian; {@code w}, {@code h}, {@code l} are its body size in model units (from the generator),
	 * {@code splay} turns the legs outward (the turtle), {@code tails} fans that many tails (the fox has three).
	 */
	private static void quadruped(Ctx c, String id, float scale, int w, int h, int l, float splay, int tails) {
		State s = c.s;
		c.pose.scale(scale, scale, scale);
		float legLen = 1.5F * Mth.cos(splay * Mth.DEG_TO_RAD);
		float hipY = legLen - 0.05F;
		float bodyY = hipY + 0.15F;
		float hx = w / 32.0F - 0.19F;
		float hz = l / 32.0F - 0.25F;
		float walk = walk(s, 0.6F) * 32.0F;
		float bodyPitch = 0.0F;
		float headPitch = 0.0F;
		float tailWag = 15.0F * Mth.sin(s.ageInTicks * 0.15F);
		float bob = Math.abs(Mth.sin(s.limb * 0.6F)) * 0.05F * s.limbAmount;
		switch (s.skill) {
			case 1 -> headPitch = s.skillAge < 22.0F ? -25.0F : -10.0F;
			case 2 -> {
				// rear up, then stomp on tick 24
				bodyPitch = s.skillAge < 24.0F ? 18.0F * Mth.clamp(s.skillAge / 16.0F, 0.0F, 1.0F) : -6.0F;
				headPitch = 15.0F;
			}
			case 3 -> {
				headPitch = 30.0F;
				tailWag = 35.0F * Mth.sin(s.ageInTicks * 0.6F);
			}
			default -> {
			}
		}
		c.push(0.0F, bob, 0.0F, 0.0F, bodyPitch, 0.0F);
		// diagonal gait: front-left with back-right
		c.part(id + "_leg", -hx, hipY, -hz, 0.0F, walk, -splay);
		c.part(id + "_leg", hx, hipY, hz, 0.0F, walk, splay);
		c.part(id + "_leg", hx, hipY, -hz, 0.0F, -walk, splay);
		c.part(id + "_leg", -hx, hipY, hz, 0.0F, -walk, -splay);
		c.part(id + "_body", 0.0F, bodyY, 0.0F, 0.0F, 0.0F, 0.0F);
		if ("kura_batu".equals(id)) {
			c.part("kura_batu_shell", 0.0F, bodyY, 0.0F, 0.0F, 0.0F, 0.0F);
		}
		c.part(id + "_head", 0.0F, bodyY + h / 32.0F * 0.4F, -l / 32.0F, s.headYaw, headPitch, 0.0F);
		for (int k = 0; k < tails; k++) {
			float fan = tails == 1 ? 0.0F : (k - (tails - 1) / 2.0F) * 28.0F;
			c.part(id + "_tail", 0.0F, bodyY + h / 32.0F * 0.5F, l / 32.0F - 0.5F, fan + tailWag * (k % 2 == 0 ? 1.0F : -0.7F), tails > 1 ? -20.0F : 0.0F,
					0.0F);
		}
		c.pop();
	}

	/** A crane guardian: hovering a little, wings beating, the long neck bobbing. */
	private static void crane(Ctx c, String id, float scale) {
		State s = c.s;
		c.pose.scale(scale, scale, scale);
		float t = s.ageInTicks;
		float hover = 0.3F + 0.15F * Mth.sin(t * 0.1F);
		float flap = 14.0F * Mth.sin(t * 0.25F) + 8.0F;
		float neck = 6.0F * Mth.sin(t * 0.09F);
		float walk = walk(s, 0.7F) * 25.0F;
		switch (s.skill) {
			case 1 -> {
				neck = s.skillAge < 22.0F ? 18.0F : -28.0F;
				flap = 35.0F;
			}
			case 2 -> flap = 50.0F * Mth.sin(t * 0.9F) + 20.0F;
			case 3 -> {
				neck = 22.0F;
				flap = 40.0F + 10.0F * Mth.sin(t * 0.6F);
				hover += 0.4F;
			}
			default -> {
			}
		}
		c.push(0.0F, hover, 0.0F, 0.0F, 0.0F, 0.0F);
		c.part(id + "_leg", -0.18F, 1.5F, 0.05F, 0.0F, walk, 0.0F);
		c.part(id + "_leg", 0.18F, 1.5F, 0.05F, 0.0F, -walk, 0.0F);
		c.part(id + "_body", 0.0F, 1.75F, 0.0F, 0.0F, 0.0F, 0.0F);
		c.part(id + "_neck", 0.0F, 2.05F, -0.35F, s.headYaw * 0.6F, neck, 0.0F);
		c.part(id + "_tail", 0.0F, 1.85F, 0.5F, 0.0F, 0.0F, 0.0F);
		c.part(id + "_wing", 0.38F, 2.15F, 0.0F, 0.0F, 0.0F, flap);
		// the other wing: the same model turned round (its z centre moves by a quarter block, shifted back)
		c.part(id + "_wing", -0.38F, 2.15F, 0.25F, 180.0F, 0.0F, -flap);
		c.pop();
	}

	/** A serpent: the head, a chain of body segments slithering behind it, the tail; the shadow dragon floats. */
	private static void serpent(Ctx c, String id, float scale, boolean floating) {
		State s = c.s;
		c.pose.scale(scale, scale, scale);
		float t = s.ageInTicks * (0.12F + 0.1F * s.limbAmount);
		int segments = floating ? 12 : 10;
		if (c.far) {
			segments /= 2;
		}
		float spacing = (floating ? 0.9F : 0.85F) * (c.far ? 2.0F : 1.0F);
		float amp = floating ? 0.6F : 0.45F;
		float headLift = floating ? 1.8F + 0.2F * Mth.sin(s.ageInTicks * 0.06F) : 1.0F;
		float headPitch = -8.0F;
		switch (s.skill) {
			case 1 -> {
				headLift += s.skillAge < 22.0F ? 0.4F : -0.2F;
				headPitch = s.skillAge < 22.0F ? 15.0F : -20.0F;
			}
			case 2 -> amp = floating ? 1.2F : 1.0F;
			case 3 -> {
				headLift += 0.6F;
				headPitch = 25.0F;
			}
			default -> {
			}
		}
		float[] xs = new float[segments + 2];
		float[] ys = new float[segments + 2];
		float[] zs = new float[segments + 2];
		for (int i = 0; i <= segments + 1; i++) {
			float d = i * spacing;
			xs[i] = amp * Mth.sin(t - d * 1.1F) * Math.min(1.0F, d);
			zs[i] = -0.4F + d;
			if (floating) {
				ys[i] = headLift - 0.15F * d + 0.4F * Mth.sin(t * 0.7F - d * 0.9F);
				ys[i] = Math.max(0.8F, ys[i]);
			} else {
				ys[i] = Math.max(0.32F, headLift - d * 0.35F);
			}
		}
		c.part(id + "_head", xs[0], ys[0], zs[0], s.headYaw, headPitch, 0.0F);
		for (int i = 1; i <= segments; i++) {
			float dx = xs[i + 1] - xs[i - 1];
			float dy = ys[i + 1] - ys[i - 1];
			float dz = zs[i + 1] - zs[i - 1];
			float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			boolean last = i == segments;
			c.part(last ? id + "_tail" : id + "_seg", xs[i], ys[i], zs[i], yaw, pitch, 0.0F);
			if (floating && !c.far && (i == 2 || i == 7)) {
				float a = yaw * Mth.DEG_TO_RAD;
				float ox = Mth.cos(a) * 0.4F;
				float oz = -Mth.sin(a) * 0.4F;
				float claw = 15.0F * Mth.sin(s.ageInTicks * 0.2F + i);
				c.part("naga_bayangan_claw", xs[i] + ox, ys[i] - 0.2F, zs[i] + oz, yaw, claw, -20.0F);
				c.part("naga_bayangan_claw", xs[i] - ox, ys[i] - 0.2F, zs[i] - oz, yaw, claw, 20.0F);
			}
		}
	}
}
