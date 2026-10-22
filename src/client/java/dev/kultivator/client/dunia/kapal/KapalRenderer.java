package dev.kultivator.client.dunia.kapal;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.kapal.KapalStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws {@code kultivator:kapal_roh} (DESIGN 34.5.15) through {@link KapalDraw}: the hull from its display list in the
 * ship's livery, sails, banner and pennants, rigging, qi oars, the formation under the keel, lanterns and night windows,
 * the engine and the cloud wake (LOD and budgets in {@link KapalDraw}). A ship its owner named shows a nameplate (its name
 * and class) above the mastheads within 48 blocks. No block is placed: the hull is geometry of this entity only.
 */
public class KapalRenderer extends EntityRenderer<KapalRohEntity, KapalRenderer.State> {

	/** Within this distance a named ship shows its nameplate. */
	private static final double NAME_RANGE = 48.0D;

	private final KapalDraw draw;

	/** The render state: the hull, its pose and the extras' strength. */
	public static class State extends EntityRenderState {
		public KapalClass kind = KapalClass.PERAHU;
		public float yaw;
		public float roll;
		public float pitch;
		public float bob;
		public byte shipState;
		public boolean docked;
		public float speed;
		public int color = KapalRohEntity.DEFAULT_COLOR;
		public float hull = 1.0F;
		public double distance;
		public int quality;
		public float time;
		public boolean leaping;
		public float camX;
		public float camY;
		public float camZ;
		/** Wake puffs relative to the entity: x, y, z, age 0..1 per puff. */
		public float[] wake = new float[0];
		public int wakeCount;
		/** The display list drawn (null: the class's own). */
		public KapalModel.Hull model;
		public KapalStyle style = KapalStyle.KAYU_HITAM;
		/** The sky pirates' raider (dark sails, blackstone silhouette, red engine). */
		public boolean raider;
		/** Night or a dark sky: the cabin windows are lit. */
		public boolean night;
	}

	public KapalRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.draw = new KapalDraw(context.getBlockModelResolver());
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(KapalRohEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		double r = entity.kind().radius() + 6.0D;
		if (entity.distanceToSqr(camX, camY, camZ) > (256.0D + r) * (256.0D + r)) {
			return false;
		}
		AABB box = getBoundingBoxForCulling(entity, partialTicks);
		return box.hasNaN() || culler.isVisible(box);
	}

	@Override
	protected AABB getBoundingBoxForCulling(KapalRohEntity entity, float partialTicks) {
		KapalClass kind = entity.kind();
		double r = kind.radius() + 2.0D;
		double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
		double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
		double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
		return new AABB(x - r, y - 2.0D, z - r, x + r, y + kind.height() + 3.0D, z + r);
	}

	@Override
	public void extractRenderState(KapalRohEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.model = KapalModel.of(state.kind);
		state.style = entity.style();
		state.raider = false;
		state.night = entity.level().isDarkOutside();
		state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		float turn = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
		float climb = (float) (entity.getY() - entity.yOld);
		state.roll = Mth.clamp(-turn * 2.6F, -9.0F, 9.0F);
		state.pitch = Mth.clamp(-climb * 18.0F, -6.0F, 6.0F);
		state.shipState = entity.state();
		state.docked = entity.docked();
		state.speed = entity.speed();
		state.color = entity.color();
		state.hull = entity.kind().hull() <= 0 ? 1.0F : Mth.clamp(entity.hull() / (float) entity.kind().hull(), 0.0F, 1.0F);
		state.time = state.ageInTicks;
		state.bob = state.docked ? 0.05F * Mth.sin(state.time * 0.08F) : 0.12F * Mth.sin(state.time * 0.11F) * Math.min(1.0F,
				0.3F + Math.abs(state.speed) * 2.0F);
		if (state.shipState == KapalRohEntity.STATE_BROKEN) {
			state.roll = 7.0F;
			state.pitch = -3.0F;
			state.bob = 0.0F;
		}
		state.leaping = state.shipState == KapalRohEntity.STATE_LEAP;
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		state.camX = (float) (camera.x - state.x);
		state.camY = (float) (camera.y - state.y);
		state.camZ = (float) (camera.z - state.z);
		state.distance = Math.sqrt(state.camX * state.camX + state.camY * state.camY + state.camZ * state.camZ);
		state.quality = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1;
		KapalFx.Trail trail = KapalFx.trail(entity.getId());
		int count = trail == null ? 0 : trail.count();
		if (state.wake.length < count * 4) {
			state.wake = new float[count * 4];
		}
		state.wakeCount = 0;
		if (trail != null) {
			float now = entity.tickCount + partialTicks;
			for (int i = 0; i < count; i++) {
				double[] p = trail.at(i);
				float age = (float) ((now - p[3]) / KapalFx.WAKE_LIFE);
				if (age < 0.0F || age > 1.0F) {
					continue;
				}
				int o = state.wakeCount * 4;
				state.wake[o] = (float) (p[0] - state.x);
				state.wake[o + 1] = (float) (p[1] - state.y);
				state.wake[o + 2] = (float) (p[2] - state.z);
				state.wake[o + 3] = age;
				state.wakeCount++;
			}
		}
		if (state.nameTag != null) {
			// above the mastheads, not at the root's small box
			state.nameTagAttachment = new Vec3(0.0D, state.model.height + 0.8D, 0.0D);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		draw.submit(state, poseStack, collector);
		super.submit(state, poseStack, collector, camera);
	}

	// ================================================================== the nameplate

	@Override
	protected boolean shouldShowName(KapalRohEntity entity, double distanceToCameraSq) {
		return !entity.shipName().isEmpty() && distanceToCameraSq < NAME_RANGE * NAME_RANGE;
	}

	@Override
	protected @Nullable Component getNameTag(KapalRohEntity entity) {
		String name = entity.shipName();
		if (name.isEmpty()) {
			return null;
		}
		return Component.translatable("kultivator.kapal.nameplate", Component.literal(name).withColor(0xFFE9A8),
				Component.translatable("kultivator.kapal.class." + entity.kind().model()).withColor(0xBFD8CC));
	}

	// ================================================================== colour helpers (kept for KapalFx)

	static int argb(float alpha, int rgb) {
		return KapalDraw.argb(alpha, rgb);
	}

	static int mixToWhite(int rgb, float t) {
		return KapalDraw.mixToWhite(rgb, t);
	}
}
