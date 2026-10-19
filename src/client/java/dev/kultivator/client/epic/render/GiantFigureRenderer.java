package dev.kultivator.client.epic.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.fx.PointClouds;
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

/**
 * F_FIGURE (DESIGN 23.10.4): a giant translucent figure built from the vanilla {@code ModelLayers.PLAYER} parts baked by
 * this class (never through an entity renderer, so model mods leave it alone [verify]), posed and drawn with
 * {@code entityTranslucentCull} at full bright: the thunder deity in the vortex rim, the Pintu figure, a disciple's clone
 * climbing the nine steps, the cosmic giant, the gold avatar behind a subject. Drawn from {@code COLLECT_SUBMITS}, so it
 * is never culled by a small hitbox and never drawn in Iris's shadow pass. 0 / 1 / 2 alive by tier; each is a large layer
 * (rank 3). Two private baked models (one per slot) keep two figures' poses apart. Minimal: a particle outline.
 */
public final class GiantFigureRenderer extends Element {

	private static final ModelPart[] POOL = new ModelPart[2];

	private Vec3 feet;
	private Vec3 moveFrom;
	private Vec3 moveTo;
	private float moveTicks;
	private float moveAge;
	private final float yaw;
	private final float height;
	private final Identifier skin;
	private final int tint;
	private final float alpha;
	private float armSwing;
	private float armTarget;
	private boolean bow;
	private float frameAge;
	private dev.kultivator.technique.spec.dsl.Pose castPose;
	private int castHold;
	private float castAge;

	/** A figure standing at {@code feet}, {@code height} blocks tall, facing {@code yaw} (degrees). */
	public GiantFigureRenderer(int delay, float life, Vec3 feet, float yaw, float height, Identifier skin, int tint, float alpha) {
		super(delay, life);
		this.feet = feet;
		this.yaw = yaw;
		this.height = Math.max(1.0F, Math.min(40.0F, height));
		this.skin = skin;
		this.tint = tint & 0xFFFFFF;
		this.alpha = alpha;
		this.largeRank = 3;
	}

	/** The skin of a tracked player or disciple (its own skin tinted), or the fallback texture. */
	public static Identifier skinOf(Entity entity, Identifier fallback) {
		if (entity instanceof AbstractClientPlayer player) {
			return player.getSkin().body().texturePath();
		}
		if (entity instanceof dev.kultivator.disciple.DiscipleEntity disciple) {
			return dev.kultivator.client.disciple.DiscipleLook.skin(disciple).body().texturePath();
		}
		return fallback;
	}

	/** Poses the figure with a cast pose of the pose library (polish 2.4 / finding 230), replacing the default stance. */
	public GiantFigureRenderer pose(dev.kultivator.technique.spec.dsl.Pose pose, int hold) {
		this.castPose = pose;
		this.castHold = Math.max(4, hold);
		this.castAge = 0.0F;
		return this;
	}

	/** Moves the feet to {@code to} over {@code ticks} (the clone's climb). */
	public GiantFigureRenderer moveTo(Vec3 to, float ticks) {
		this.moveFrom = feet;
		this.moveTo = to;
		this.moveTicks = Math.max(1.0F, ticks);
		this.moveAge = 0.0F;
		return this;
	}

	/** Swings the right arm down (the thunder deity's strike) or raises it. */
	public void arm(float radians) {
		this.armTarget = radians;
	}

	/** Bows (the silhouettes bow at L ≥ 7). */
	public void bow() {
		this.bow = true;
	}

	@Override
	public boolean geometry() {
		return true;
	}

	@Override
	protected void tick() {
		castAge++;
		armSwing += (armTarget - armSwing) * 0.35F;
		if (moveTo != null) {
			moveAge++;
			float t = Math.min(1.0F, moveAge / moveTicks);
			feet = moveFrom.lerp(moveTo, t);
		}
	}

	@Override
	protected void fallback() {
		if ((int) age % 2 == 0) {
			Vec3 side = new Vec3(Math.cos(Math.toRadians(yaw)), 0.0D, Math.sin(Math.toRadians(yaw)));
			EpicParticles.outline(PointClouds.humanoid(), feet, side, height, 14, EpicParticles.dust(tint, Math.min(4.0F, 1.0F + height / 10.0F)),
					height > 12.0F);
		}
	}

	@Override
	public void render(EpicRenderer.Frame frame, float at) {
		if (EpicBudget.tier() < 1 || !EpicBudget.takeFigure()) {
			return;
		}
		if (!EpicBudget.takeQuads(72)) {
			return;
		}
		frameAge = at;
		frame.figure(this);
	}

	/** Submits the posed model (called by the frame after the pillars). */
	void submit(EpicRenderer.Frame frame, int index) {
		ModelPart root = part(index);
		if (root == null) {
			return;
		}
		float a = alpha * fade(frameAge, 30.0F, 20.0F);
		if (a <= 0.01F) {
			return;
		}
		root.resetPose();
		pose(root);
		PoseStack poseStack = frame.poseStack;
		poseStack.pushPose();
		poseStack.translate(feet.x - frame.camX, feet.y - frame.camY, feet.z - frame.camZ);
		poseStack.rotateDegrees(Axis.YP, 180.0F - yaw);
		float scale = height / 2.0F;
		poseStack.scale(-scale, -scale, scale);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		int color = ARGB.color(Math.round(a * 255.0F), tint);
		frame.collector.order(2).submitModelPart(root, poseStack, RenderTypes.entityTranslucentCull(skin), LightCoordsUtil.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, null, color);
		poseStack.popPose();
	}

	private void pose(ModelPart root) {
		if (castPose != null) {
			dev.kultivator.client.anim.PoseAngles a = dev.kultivator.client.fx.spec.AnimBridge.sample(castPose, Math.min(1.0F, castAge / castHold), frameAge);
			if (a != null) {
				apply(child(root, "head"), a.headX(), a.headY(), 0.0F);
				apply(child(root, "body"), a.bodyX(), a.bodyY(), 0.0F);
				apply(child(root, "right_arm"), a.rArmX(), a.rArmY(), a.rArmZ());
				apply(child(root, "left_arm"), a.lArmX(), a.lArmY(), a.lArmZ());
				apply(child(root, "right_leg"), a.rLegX(), 0.0F, a.rLegZ());
				apply(child(root, "left_leg"), a.lLegX(), 0.0F, a.lLegZ());
				return;
			}
		}
		ModelPart rightArm = child(root, "right_arm");
		ModelPart leftArm = child(root, "left_arm");
		ModelPart head = child(root, "head");
		ModelPart body = child(root, "body");
		if (rightArm != null) {
			rightArm.xRot = -2.6F + armSwing;
			rightArm.zRot = 0.15F;
		}
		if (leftArm != null) {
			leftArm.xRot = -0.3F;
			leftArm.zRot = -0.35F;
		}
		if (bow) {
			if (body != null) {
				body.xRot = 0.5F;
			}
			if (head != null) {
				head.xRot = 0.6F;
			}
		} else if (head != null) {
			head.xRot = Mth.sin(frameAge * 0.02F) * 0.05F;
		}
	}

	private static void apply(ModelPart part, float x, float y, float z) {
		if (part != null) {
			part.xRot = x;
			part.yRot = y;
			part.zRot = z;
		}
	}

	private static ModelPart child(ModelPart root, String name) {
		try {
			return root.getChild(name);
		} catch (RuntimeException missing) {
			return null;
		}
	}

	private static ModelPart part(int index) {
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
