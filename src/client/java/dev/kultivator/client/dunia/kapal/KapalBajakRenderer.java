package dev.kultivator.client.dunia.kapal;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.kapal.KapalBajakEntity;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.kapal.KapalStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws {@code kultivator:kapal_bajak}, the sky pirates' raider (detail pass of DESIGN 34.5.15), with the ships' own
 * {@link KapalDraw}: the {@code perahu_bajak} display list (black hull, chained ram, ragged tarred sails with the red claw,
 * soul lanterns), a blood-red banner and pennant, a red engine and formation, a grey wake. It heels hard into its turns
 * and pitches its bow down as it closes in; fleeing, it climbs away with the sails full.
 */
public class KapalBajakRenderer extends EntityRenderer<KapalBajakEntity, KapalRenderer.State> {

	/** The raiders' banner (dried blood). */
	public static final int COLOR = 0x7A1414;

	private final KapalDraw draw;

	public KapalBajakRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.draw = new KapalDraw(context.getBlockModelResolver());
		this.shadowRadius = 0.0F;
	}

	@Override
	public KapalRenderer.State createRenderState() {
		return new KapalRenderer.State();
	}

	@Override
	public boolean shouldRender(KapalBajakEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		if (entity.distanceToSqr(camX, camY, camZ) > 256.0D * 256.0D) {
			return false;
		}
		AABB box = getBoundingBoxForCulling(entity, partialTicks);
		return box.hasNaN() || culler.isVisible(box);
	}

	@Override
	protected AABB getBoundingBoxForCulling(KapalBajakEntity entity, float partialTicks) {
		double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
		double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
		double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
		return new AABB(x - 9.0D, y - 2.0D, z - 9.0D, x + 9.0D, y + 14.0D, z + 9.0D);
	}

	@Override
	public void extractRenderState(KapalBajakEntity entity, KapalRenderer.State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = KapalClass.PERAHU;
		state.model = KapalModel.of(KapalModel.RAIDER, KapalClass.PERAHU);
		state.style = KapalStyle.KAYU_HITAM;
		state.raider = true;
		state.night = entity.level().isDarkOutside();
		state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		float turn = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
		float climb = (float) (entity.getY() - entity.yOld);
		byte phase = entity.phase();
		state.roll = Mth.clamp(-turn * 4.0F, -14.0F, 14.0F);
		state.pitch = Mth.clamp(-climb * 22.0F + (phase == KapalBajakEntity.PHASE_APPROACH ? 3.0F : 0.0F), -9.0F, 9.0F);
		state.shipState = KapalRohEntity.STATE_FLY;
		state.docked = false;
		state.speed = entity.speed();
		state.color = COLOR;
		state.hull = 1.0F;
		state.time = state.ageInTicks;
		state.bob = 0.16F * Mth.sin(state.time * 0.14F);
		state.leaping = false;
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
	}

	@Override
	public void submit(KapalRenderer.State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		draw.submit(state, poseStack, collector);
		super.submit(state, poseStack, collector, camera);
	}
}
