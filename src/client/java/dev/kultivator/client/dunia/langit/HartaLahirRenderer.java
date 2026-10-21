package dev.kultivator.client.dunia.langit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.langit.HartaLahirEntity;
import dev.kultivator.dunia.langit.TreasureRewards;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/**
 * Draws the born treasure (DESIGN 34.5.9): its item turning and bobbing above a jade lotus of three petal rings (8 outer,
 * 8 inner, 5 heart petals; each petal two curved quads of the generated petal texture, emissive), a soft glow of the
 * tier's colour behind the item and on the ground, and the channel ring that fills while a player claims it. The lotus
 * slowly turns and opens as the channel grows. Petal rings drop to two at Minimal.
 */
public class HartaLahirRenderer extends EntityRenderer<HartaLahirEntity, HartaLahirRenderer.State> {

	private final ItemModelResolver itemModelResolver;

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float age;
		public int tier = 1;
		public float channel;
	}

	public HartaLahirRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected int getBlockLightLevel(HartaLahirEntity entity, BlockPos blockPos) {
		return 15;
	}

	@Override
	public void extractRenderState(HartaLahirEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		itemModelResolver.updateForNonLiving(state.item, entity.display(), ItemDisplayContext.GROUND, entity);
		state.age = entity.tickCount + partialTicks;
		state.tier = entity.tier();
		state.channel = entity.channel() / 100.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int rgb = TreasureRewards.tierColor(state.tier);
		float open = state.channel;
		float spin = state.age * 0.6F;

		// the lotus
		LangitSink lotus = new LangitSink(LangitSink.LOTUS);
		boolean rich = !EffectQuality.minimal();
		int jade = 0xF0FFFFFF;
		int heart = LangitRenderer.argb(0.95F, LangitRenderer.lerpRgb(0xFFFFFF, rgb, 0.45F));
		petals(lotus, 8, 0.16F, 0.82F, 22.0F - 12.0F * open, 58.0F - 18.0F * open, 0.36F, spin * Mth.DEG_TO_RAD, jade);
		if (rich) {
			petals(lotus, 8, 0.11F, 0.62F, 46.0F - 14.0F * open, 78.0F - 14.0F * open, 0.3F, (spin + 22.5F) * Mth.DEG_TO_RAD, jade);
		}
		petals(lotus, 5, 0.04F, 0.36F, 66.0F - 10.0F * open, 86.0F - 8.0F * open, 0.2F, (-spin * 1.5F) * Mth.DEG_TO_RAD, heart);
		lotus.submit(collector, poseStack);

		// glows and the channel ring
		LangitSink glow = new LangitSink(LangitSink.GLOW);
		LangitSink ring = new LangitSink(LangitSink.RING);
		Vector3f right = camera.orientation.transform(1.0F, 0.0F, 0.0F, new Vector3f());
		Vector3f up = camera.orientation.transform(0.0F, 1.0F, 0.0F, new Vector3f());
		float pulse = 0.8F + 0.2F * Mth.sin(state.age * 0.12F);
		float itemY = 0.78F + 0.07F * Mth.sin(state.age * 0.09F);
		billboard(glow, 0.0F, itemY, 0.0F, 0.75F + 0.25F * open, LangitRenderer.argb(0.55F * pulse + 0.3F * open, rgb), right, up);
		flat(glow, 0.0F, 0.03F, 0.0F, 1.5F, LangitRenderer.argb(0.55F * pulse, rgb));
		if (open > 0.0F) {
			arc(ring, 0.0F, 0.04F, 0.0F, 1.15F, 0.28F, open, LangitRenderer.argb(0.9F, 0xFFF0C0));
			arc(ring, 0.0F, 0.05F, 0.0F, 1.15F, 0.6F, open, LangitRenderer.argb(0.3F, rgb));
		}
		glow.submit(collector, poseStack);
		ring.submit(collector, poseStack);

		// the treasure itself
		poseStack.pushPose();
		poseStack.translate(0.0F, itemY, 0.0F);
		poseStack.rotateDegrees(Axis.YP, state.age * 2.2F);
		float scale = 1.15F + 0.25F * open;
		poseStack.scale(scale, scale, scale);
		poseStack.translate(0.0F, -0.15F, 0.0F);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/**
	 * A ring of {@code count} petals: base at radius {@code r0}, length {@code length}; the lower half rises at {@code a1}
	 * degrees, the upper half at {@code a2} (so each petal curves up like a real lotus petal), widest {@code width}.
	 */
	private static void petals(LangitSink sink, int count, float r0, float length, float a1, float a2, float width, float offset, int color) {
		float c1 = Mth.cos(a1 * Mth.DEG_TO_RAD);
		float s1 = Mth.sin(a1 * Mth.DEG_TO_RAD);
		float c2 = Mth.cos(a2 * Mth.DEG_TO_RAD);
		float s2 = Mth.sin(a2 * Mth.DEG_TO_RAD);
		float half = length * 0.5F;
		for (int i = 0; i < count; i++) {
			float phi = offset + Mth.TWO_PI * i / count;
			float rx = Mth.cos(phi);
			float rz = Mth.sin(phi);
			float tx = -rz;
			float tz = rx;
			// base, middle and tip points along the petal's spine
			float bx = rx * r0;
			float by = 0.02F;
			float bz = rz * r0;
			float mx = bx + rx * c1 * half;
			float my = by + s1 * half;
			float mz = bz + rz * c1 * half;
			float px = mx + rx * c2 * half;
			float py = my + s2 * half;
			float pz = mz + rz * c2 * half;
			float w0 = width * 0.25F;
			float w1 = width * 0.5F;
			float w2 = width * 0.06F;
			// v: 1 at the base, 0 at the tip (the texture's layout); u across
			sink.vertex(bx - tx * w0, by, bz - tz * w0, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(bx + tx * w0, by, bz + tz * w0, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(mx + tx * w1, my, mz + tz * w1, 1.0F, 0.5F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(mx - tx * w1, my, mz - tz * w1, 0.0F, 0.5F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(mx - tx * w1, my, mz - tz * w1, 0.0F, 0.5F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(mx + tx * w1, my, mz + tz * w1, 1.0F, 0.5F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(px + tx * w2, py, pz + tz * w2, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(px - tx * w2, py, pz - tz * w2, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		}
	}

	private static void billboard(LangitSink sink, float x, float y, float z, float half, int color, Vector3f right, Vector3f up) {
		float rx = right.x * half;
		float ry = right.y * half;
		float rz = right.z * half;
		float ux = up.x * half;
		float uy = up.y * half;
		float uz = up.z * half;
		sink.vertex(x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 1.0F, color, 0.0F, 0.0F, 1.0F);
		sink.vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 1.0F, color, 0.0F, 0.0F, 1.0F);
		sink.vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 0.0F, color, 0.0F, 0.0F, 1.0F);
		sink.vertex(x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 0.0F, color, 0.0F, 0.0F, 1.0F);
	}

	private static void flat(LangitSink sink, float x, float y, float z, float half, int color) {
		sink.vertex(x - half, y, z - half, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x - half, y, z + half, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + half, y, z + half, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		sink.vertex(x + half, y, z - half, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
	}

	/** A flat ring arc from north, clockwise, covering {@code fraction} of the circle (24 segments for a full ring). */
	private static void arc(LangitSink sink, float x, float y, float z, float r, float width, float fraction, int color) {
		int segments = Math.max(1, Math.round(24 * Mth.clamp(fraction, 0.0F, 1.0F)));
		float r0 = r - width * 0.5F;
		float r1 = r + width * 0.5F;
		float total = Mth.TWO_PI * Mth.clamp(fraction, 0.0F, 1.0F);
		for (int i = 0; i < segments; i++) {
			float a0 = -Mth.HALF_PI + total * i / segments;
			float a1 = -Mth.HALF_PI + total * (i + 1) / segments;
			float c0 = Mth.cos(a0);
			float s0 = Mth.sin(a0);
			float c1 = Mth.cos(a1);
			float s1 = Mth.sin(a1);
			sink.vertex(x + c0 * r0, y, z + s0 * r0, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c0 * r1, y, z + s0 * r1, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c1 * r1, y, z + s1 * r1, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c1 * r0, y, z + s1 * r0, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		}
	}
}
