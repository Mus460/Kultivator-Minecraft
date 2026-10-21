package dev.kultivator.client.dunia.naga;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import dev.kultivator.dunia.naga.BeastTribulation;
import dev.kultivator.dunia.naga.KesengsaraanEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a Kesengsaraan Binatang Roh (DESIGN 34.5.12) around its host entity: the cloud vortex (two counter-turning
 * discs of the 7b {@code cloud_swirl} texture and a glowing eye, growing over the 10 s omen and flashing at each strike),
 * the telegraph ring closing on the beast in the last 30 ticks before a strike, the Hu Fa's gold link from the owner to
 * the beast, and on success a column of light descending onto the beast. Quads come from the 7b budget (vortex 24 / 48 /
 * 96, ring 16 / 32 / 48); under a shader pack the counts drop with {@code EpicBudget}. The bolts themselves are the
 * player tribulation's particle bolts ({@code TribulationFx}); never a LightningBolt.
 */
public class KesengsaraanRenderer extends EntityRenderer<KesengsaraanEntity, KesengsaraanRenderer.State> {

	private static final RenderType GLOW = RenderTypes.lightning();
	private static final RenderType SWIRL = EpicRenderTypes.SWIRL;
	private static final int[] VORTEX_SIDES = {12, 24, 32};
	private static final int[] RING_SIDES = {16, 32, 48};

	public static class State extends EntityRenderState {
		public int phase;
		public float phaseTicks;
		public int next;
		public int done;
		public int total;
		public int color;
		public @Nullable Vec3 beast;
		public float beastHeight;
		public @Nullable Vec3 guard;
	}

	public KesengsaraanRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(KesengsaraanEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(14.0D, 18.0D, 14.0D);
	}

	@Override
	public void extractRenderState(KesengsaraanEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.phase = entity.phase();
		state.phaseTicks = entity.phaseTicks() + partialTicks;
		state.next = entity.nextStrike();
		state.done = entity.strikesDone();
		state.total = entity.strikesTotal();
		state.color = entity.color();
		Entity beast = entity.level().getEntity(entity.beastId());
		state.beast = beast == null ? null : beast.getPosition(partialTicks);
		state.beastHeight = beast == null ? 1.0F : beast.getBbHeight();
		Entity guard = entity.guardId() >= 0 ? entity.level().getEntity(entity.guardId()) : null;
		state.guard = guard == null ? null : guard.getPosition(partialTicks).add(0.0D, guard.getBbHeight() * 0.6D, 0.0D);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int quality = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1;
		float age = state.ageInTicks;
		Vec3 origin = new Vec3(state.x, state.y, state.z);
		// the vortex: grows through the omen, full during the strikes, opens and fades after
		float radius;
		float alpha;
		switch (state.phase) {
			case KesengsaraanEntity.PHASE_OMEN -> {
				float t = Mth.clamp(state.phaseTicks / BeastTribulation.OMEN_TICKS, 0.0F, 1.0F);
				radius = 2.5F + 8.5F * t;
				alpha = 0.45F + 0.5F * t;
			}
			case KesengsaraanEntity.PHASE_STRIKES -> {
				radius = 11.0F;
				alpha = 0.95F;
			}
			case KesengsaraanEntity.PHASE_SUCCESS -> {
				float t = Mth.clamp(state.phaseTicks / BeastTribulation.SUCCESS_TICKS, 0.0F, 1.0F);
				radius = 11.0F + 6.0F * t;
				alpha = 0.95F * (1.0F - t);
			}
			default -> {
				float t = Mth.clamp(state.phaseTicks / BeastTribulation.FAILED_TICKS, 0.0F, 1.0F);
				radius = 11.0F;
				alpha = 0.95F * (1.0F - t);
			}
		}
		float flash = state.phase == KesengsaraanEntity.PHASE_STRIKES && state.next >= 0 && state.next > BeastTribulation.STRIKE_EVERY - 6
				? (state.next - (BeastTribulation.STRIKE_EVERY - 6)) / 6.0F : 0.0F;
		int sides = VORTEX_SIDES[quality];
		if (alpha > 0.02F && NagaVisuals.takeQuads(sides * 2)) {
			disc(poseStack, collector, 0.0F, radius, age * 1.6F, ARGB.color(Math.round(200 * alpha), 0x2A2238), sides);
			disc(poseStack, collector, -0.6F, radius * 0.72F, -age * 2.4F, ARGB.color(Math.round(170 * alpha), 0x4A3A6A), sides);
		}
		// the eye of the vortex
		int eyeRgb = lerpRgb(0x8F7AFF, 0xFFFFFF, flash);
		if (alpha > 0.02F) {
			NagaRenderer.orb(poseStack, collector, new Vec3(0.0D, -1.0D, 0.0D), 0.9F + 1.6F * flash, ARGB.color(Math.round((120 + 120 * flash) * alpha),
					eyeRgb));
		}
		Vec3 beast = state.beast == null ? null : state.beast.subtract(origin);
		if (beast != null && state.phase == KesengsaraanEntity.PHASE_STRIKES && state.next >= 0 && state.next <= BeastTribulation.TELEGRAPH) {
			// the ring closes on the beast before the strike, a thin thread of light joins the cloud and the beast
			float t = 1.0F - state.next / (float) BeastTribulation.TELEGRAPH;
			int ring = RING_SIDES[quality];
			if (NagaVisuals.takeQuads(ring * 2 + 2)) {
				NagaRenderer.ring(poseStack, collector, beast.add(0.0D, 0.05D, 0.0D), 4.0F - 3.2F * t, 0.25F + 0.2F * t, ring,
						ARGB.color(Math.round(120 + 130 * t), state.color));
				NagaRenderer.ring(poseStack, collector, beast.add(0.0D, 0.05D, 0.0D), 0.8F, 0.1F, ring / 2, ARGB.color(Math.round(200 * t), 0xFFFFFF));
				if (t > 0.6F) {
					column(poseStack, collector, beast, new Vec3(0.0D, -1.0D, 0.0D), 0.05F + 0.1F * t, ARGB.color(Math.round(160 * t), 0xE8E0FF));
				}
			}
		}
		if (beast != null && state.guard != null && state.phase <= KesengsaraanEntity.PHASE_STRIKES && NagaVisuals.takeQuads(2)) {
			column(poseStack, collector, state.guard.subtract(origin), beast.add(0.0D, state.beastHeight * 0.6D, 0.0D), 0.08F,
					ARGB.color(150 + Math.round(60 * Mth.sin(age * 0.4F)), 0xFFE08A));
		}
		if (beast != null && state.phase == KesengsaraanEntity.PHASE_SUCCESS && NagaVisuals.takeQuads(6)) {
			// the light pillar descends onto the beast and narrows away
			float t = Mth.clamp(state.phaseTicks / BeastTribulation.SUCCESS_TICKS, 0.0F, 1.0F);
			float down = Mth.clamp(state.phaseTicks / 12.0F, 0.0F, 1.0F);
			Vec3 top = new Vec3(0.0D, 6.0D, 0.0D);
			Vec3 bottom = top.add(beast.subtract(top).scale(down));
			float width = 1.4F * (1.0F - t * 0.8F);
			column(poseStack, collector, top, bottom, width, ARGB.color(Math.round(200 * (1.0F - t)), state.color));
			column(poseStack, collector, top, bottom, width * 0.35F, ARGB.color(Math.round(230 * (1.0F - t)), 0xFFFFFF));
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** A horizontal textured disc of the cloud swirl at height {@code y}, turned by {@code spin} degrees. */
	private static void disc(PoseStack poseStack, SubmitNodeCollector collector, float y, float radius, float spin, int color, int sides) {
		int rim = ARGB.color(ARGB.alpha(color) / 3, color & 0xFFFFFF);
		collector.submitCustomGeometry(poseStack, SWIRL, (pose, buffer) -> {
			double turn = Math.toRadians(spin);
			for (int s = 0; s < sides; s++) {
				double a0 = s / (double) sides * Math.PI * 2.0D + turn;
				double a1 = (s + 1) / (double) sides * Math.PI * 2.0D + turn;
				float u0 = 0.5F + 0.5F * (float) Math.cos(a0 - turn);
				float v0 = 0.5F + 0.5F * (float) Math.sin(a0 - turn);
				float u1 = 0.5F + 0.5F * (float) Math.cos(a1 - turn);
				float v1 = 0.5F + 0.5F * (float) Math.sin(a1 - turn);
				textured(pose, buffer, 0.0F, y - 0.4F, 0.0F, 0.5F, 0.5F, color);
				textured(pose, buffer, (float) (Math.cos(a0) * radius), y, (float) (Math.sin(a0) * radius), u0, v0, rim);
				textured(pose, buffer, (float) (Math.cos(a1) * radius), y, (float) (Math.sin(a1) * radius), u1, v1, rim);
				textured(pose, buffer, 0.0F, y - 0.4F, 0.0F, 0.5F, 0.5F, color);
			}
		});
	}

	private static void textured(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color) {
		buffer.addVertex(pose, x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT)
				.setNormal(pose, 0.0F, -1.0F, 0.0F);
	}

	/** A camera-independent cross of two quads from {@code a} to {@code b}, {@code width} wide (a beam of light). */
	static void column(PoseStack poseStack, SubmitNodeCollector collector, Vec3 a, Vec3 b, float width, int color) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-4D) {
			return;
		}
		Vec3 n = dir.normalize();
		Vec3 up = Math.abs(n.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 s1 = n.cross(up).normalize().scale(width * 0.5D);
		Vec3 s2 = n.cross(s1).normalize().scale(width * 0.5D);
		collector.submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
			int edge = color & 0x00FFFFFF;
			for (Vec3 side : new Vec3[]{s1, s2}) {
				NagaRenderer.vertex(pose, buffer, a.subtract(side), edge);
				NagaRenderer.vertex(pose, buffer, a, color);
				NagaRenderer.vertex(pose, buffer, b, color);
				NagaRenderer.vertex(pose, buffer, b.subtract(side), edge);
				NagaRenderer.vertex(pose, buffer, a, color);
				NagaRenderer.vertex(pose, buffer, a.add(side), edge);
				NagaRenderer.vertex(pose, buffer, b.add(side), edge);
				NagaRenderer.vertex(pose, buffer, b, color);
			}
		});
	}

	private static int lerpRgb(int a, int b, float t) {
		float k = Mth.clamp(t, 0.0F, 1.0F);
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return (r << 16) | (g << 8) | bl;
	}
}
