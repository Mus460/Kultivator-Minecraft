package dev.kultivator.client.fx.quad;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.anim.PoseAngles;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.fx.spec.AnimBridge;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The giant avatar of polish S21 (AVATAR with scale above 2, cosmic giant, Dharma colossus, emperor fist, two-Dao avatars):
 * a translucent full-bright copy of the caster's own model built from the vanilla {@code ModelLayers.PLAYER} parts (like
 * the 7b {@code GiantFigureRenderer}, never through an entity renderer), drawn at world level from COLLECT_SUBMITS so it
 * is never culled by the caster's small hitbox and shows in first person too. It stands {@code back} blocks behind the
 * caster and follows its turn, poses with the cast-pose library ({@code CastPoses.sample}) and plays accents: a punch, a
 * stomp, a kneeling slam. Two baked models (one per slot) keep two figures apart; 0 / 1 / 2 alive by quality.
 */
public final class GiantAvatar extends QuadFx.Quad {

	private static final ModelPart[] POOL = new ModelPart[2];
	private static final Identifier FALLBACK = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

	private final int entityId;
	private final float height;
	private final int tint;
	private final int rim;
	private final float alpha;
	private final float back;
	private final Identifier skin;
	private Vec3 feet;
	private float yaw;
	private Pose pose;
	private float poseAge;
	private int poseHold;
	private float grow;
	private @Nullable Vec3 walkTo;
	private float walkTicks;
	private float walkAge;
	private Vec3 walkFrom;

	public GiantAvatar(int entityId, float height, int tint, int rim, float alpha, float back, Pose pose, int life) {
		super(QuadFx.Kind.FIGURE, life);
		this.entityId = entityId;
		this.height = Math.max(2.0F, Math.min(40.0F, height));
		this.tint = tint & 0xFFFFFF;
		this.rim = rim & 0xFFFFFF;
		this.alpha = Math.max(0.1F, Math.min(0.85F, alpha));
		this.back = back;
		this.pose = pose == null ? Pose.ARMS_SPREAD : pose;
		this.poseHold = 40;
		Entity entity = entity(entityId);
		this.skin = skinOf(entity);
		this.feet = entity == null ? Vec3.ZERO : stand(entity);
		this.walkFrom = feet;
		this.yaw = entity == null ? 0.0F : bodyYaw(entity);
		this.casterId = entityId;
	}

	/** The skin of a player or a disciple, else Steve. */
	public static Identifier skinOf(@Nullable Entity entity) {
		if (entity instanceof AbstractClientPlayer player) {
			return player.getSkin().body().texturePath();
		}
		if (entity instanceof DiscipleEntity disciple) {
			return DiscipleLook.skin(disciple).body().texturePath();
		}
		return FALLBACK;
	}

	private static float bodyYaw(Entity entity) {
		return entity instanceof net.minecraft.world.entity.LivingEntity living ? living.yBodyRot : entity.getYRot();
	}

	private Vec3 stand(Entity entity) {
		double yawRad = Math.toRadians(bodyYaw(entity));
		Vec3 look = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
		return entity.position().subtract(look.scale(back));
	}

	/** Plays {@code pose} from its start, held {@code hold} ticks (punch per melee HIT, STOMP per stomp, KNEEL_STRIKE slam). */
	public void play(Pose next, int hold) {
		this.pose = next;
		this.poseAge = 0.0F;
		this.poseHold = Math.max(4, hold);
	}

	/** Walks the feet to {@code to} over {@code ticks} (the cosmic giant striding to a stomp point). */
	public void walkTo(Vec3 to, int ticks) {
		this.walkFrom = feet;
		this.walkTo = to;
		this.walkTicks = Math.max(1, ticks);
		this.walkAge = 0.0F;
	}

	public int entityId() {
		return entityId;
	}

	@Override
	protected void tick() {
		if (age < 0) {
			return;
		}
		poseAge++;
		grow = Math.min(1.0F, grow + 0.08F);
		Entity entity = entity(entityId);
		if (walkTo != null) {
			walkAge++;
			float t = Math.min(1.0F, walkAge / walkTicks);
			feet = walkFrom.lerp(walkTo, t * t * (3.0F - 2.0F * t));
			if (t >= 1.0F) {
				walkTo = null;
			}
		} else if (entity != null && !entity.isRemoved()) {
			Vec3 want = stand(entity);
			feet = feet.lerp(want, 0.25D);
			float target = bodyYaw(entity);
			yaw += Mth.wrapDegrees(target - yaw) * 0.2F;
		}
	}

	@Override
	protected void render(QuadFx.Frame frame, float at) {
		if (frame.figures.size() >= POOL.length || !frame.take(80)) {
			return;
		}
		frame.figures.add(this);
	}

	/** Submits the posed model (called by the frame after the soft layers). */
	void submit(QuadFx.Frame frame, int index) {
		ModelPart root = part(index);
		if (root == null) {
			return;
		}
		float at = Math.max(0.0F, age + frame.partial);
		float a = alpha * fade(at, 8.0F, 14.0F);
		if (a <= 0.01F) {
			return;
		}
		root.resetPose();
		float lift = pose(root, at);
		float scale = height / 1.8F * (0.35F + 0.65F * easeOutBack(grow));
		PoseStack poseStack = frame.poseStack;
		poseStack.pushPose();
		poseStack.translate(feet.x - frame.camX, feet.y - frame.camY + lift * scale, feet.z - frame.camZ);
		poseStack.rotateDegrees(Axis.YP, 180.0F - yaw);
		poseStack.scale(-scale, -scale, scale);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		int color = ARGB.color(Math.round(a * 255.0F), tint);
		frame.collector.order(2).submitModelPart(root, poseStack, RenderTypes.entityTranslucentEmissive(skin), LightCoordsUtil.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, null, color);
		// a slightly larger rim copy in the second colour reads as an aura edge
		poseStack.scale(1.04F, 1.02F, 1.04F);
		poseStack.translate(0.0F, -0.02F, 0.0F);
		frame.collector.order(2).submitModelPart(root, poseStack, RenderTypes.entityTranslucentEmissive(skin), LightCoordsUtil.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, null, ARGB.color(Math.round(a * 0.45F * 255.0F), rim));
		poseStack.popPose();
	}

	/** Applies the current pose to the baked parts; returns the lift in blocks of a normal-sized body (× the figure scale). */
	private float pose(ModelPart root, float at) {
		ModelPart head = child(root, "head");
		ModelPart body = child(root, "body");
		ModelPart rightArm = child(root, "right_arm");
		ModelPart leftArm = child(root, "left_arm");
		ModelPart rightLeg = child(root, "right_leg");
		ModelPart leftLeg = child(root, "left_leg");
		float t = Math.min(1.0F, poseAge / Math.max(1.0F, poseHold));
		PoseAngles angles = AnimBridge.sample(pose, t, at);
		float breathe = Mth.sin(at * 0.08F) * 0.04F;
		if (angles == null) {
			// stand-in pose: arms spread a little, slow breathing
			if (rightArm != null) {
				rightArm.zRot = 0.35F + breathe;
				rightArm.xRot = -0.2F;
			}
			if (leftArm != null) {
				leftArm.zRot = -0.35F - breathe;
				leftArm.xRot = -0.2F;
			}
			if (head != null) {
				head.xRot = -0.1F;
			}
			return 0.0F;
		}
		if (head != null) {
			head.xRot = angles.headX();
			head.yRot = angles.headY();
		}
		if (body != null) {
			body.xRot = angles.bodyX();
			body.yRot = angles.bodyY();
		}
		if (rightArm != null) {
			rightArm.xRot = angles.rArmX();
			rightArm.yRot = angles.rArmY();
			rightArm.zRot = angles.rArmZ() + breathe;
			if (body != null && body.yRot != 0.0F) {
				// arms are root children: a body twist moves their pivots (vanilla setupAttackAnimation)
				rightArm.z = Mth.sin(body.yRot) * 5.0F;
				rightArm.x = -Mth.cos(body.yRot) * 5.0F;
			}
		}
		if (leftArm != null) {
			leftArm.xRot = angles.lArmX();
			leftArm.yRot = angles.lArmY();
			leftArm.zRot = angles.lArmZ() - breathe;
			if (body != null && body.yRot != 0.0F) {
				leftArm.z = -Mth.sin(body.yRot) * 5.0F;
				leftArm.x = Mth.cos(body.yRot) * 5.0F;
			}
		}
		if (rightLeg != null) {
			rightLeg.xRot = angles.rLegX();
			rightLeg.zRot = angles.rLegZ();
		}
		if (leftLeg != null) {
			leftLeg.xRot = angles.lLegX();
			leftLeg.zRot = angles.lLegZ();
		}
		return angles.lift();
	}

	private static float easeOutBack(float x) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float k = x - 1.0F;
		return 1.0F + c3 * k * k * k + c1 * k * k;
	}

	private static @Nullable ModelPart child(ModelPart root, String name) {
		try {
			return root.getChild(name);
		} catch (RuntimeException missing) {
			return null;
		}
	}

	private static @Nullable ModelPart part(int index) {
		int slot = Math.max(0, Math.min(POOL.length - 1, index));
		if (POOL[slot] == null) {
			try {
				POOL[slot] = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER);
			} catch (RuntimeException exception) {
				return null;
			}
		}
		return POOL[slot];
	}
}
