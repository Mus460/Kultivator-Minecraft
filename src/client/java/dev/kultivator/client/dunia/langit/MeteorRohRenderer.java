package dev.kultivator.client.dunia.langit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.dunia.langit.MeteorRohEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws a Meteor Roh (DESIGN 34.5.8): a tumbling lump of star iron (a lumpy cuboid with the glowing-seam texture, full
 * bright) inside a white-hot head glow, with a tapering trail of two layered strips behind it along its flight. The
 * spark tail comes from {@link LangitParticles}. A Meteor Agung is 2.4 times larger, sheds two shards behind it and wears a
 * wide green-white wake. No block, no light source: the glow is emissive quads.
 */
public class MeteorRohRenderer extends EntityRenderer<MeteorRohEntity, MeteorRohRenderer.State> {

	public static class State extends EntityRenderState {
		public float spin;
		public float dirX;
		public float dirY = -1.0F;
		public float dirZ;
		public float progress;
		public int seed;
		public boolean great;
	}

	public MeteorRohRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected int getBlockLightLevel(MeteorRohEntity entity, BlockPos blockPos) {
		return 15;
	}

	@Override
	protected AABB getBoundingBoxForCulling(MeteorRohEntity entity, float partialTicks) {
		Vec3 dir = entity.direction();
		AABB box = entity.getBoundingBox();
		double reach = entity.great() ? 40.0D : 16.0D;
		return box.minmax(box.move(-dir.x * reach, -dir.y * reach, -dir.z * reach)).inflate(entity.great() ? 6.0D : 2.0D);
	}

	@Override
	public void extractRenderState(MeteorRohEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		double age = entity.age(partialTicks);
		state.spin = (float) age * 11.0F;
		Vec3 dir = entity.direction();
		state.dirX = (float) dir.x;
		state.dirY = (float) dir.y;
		state.dirZ = (float) dir.z;
		state.progress = (float) entity.progress(age);
		state.seed = entity.seed();
		state.great = entity.great();
		LangitParticles.track(entity);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		// camera-relative frame of the entity (the pose stack is at the entity's feet)
		Vector3f right = camera.orientation.transform(1.0F, 0.0F, 0.0F, new Vector3f());
		Vector3f up = camera.orientation.transform(0.0F, 1.0F, 0.0F, new Vector3f());
		float ex = (float) (state.x - camera.pos.x);
		float ey = (float) (state.y - camera.pos.y) + 0.5F;
		float ez = (float) (state.z - camera.pos.z);

		// the rock: a tumbling lump, full bright (a Meteor Agung: 2.4 times larger with two shards breaking off behind it)
		float g = state.great ? 2.4F : 1.0F;
		LangitSink rock = new LangitSink(LangitSink.ROCK);
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.5F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, state.spin);
		poseStack.rotateDegrees(Axis.XP, state.spin * 0.7F + state.seed % 90);
		poseStack.scale(g, g, g);
		lump(rock, state.seed);
		rock.submit(collector, poseStack);
		poseStack.popPose();
		if (state.great) {
			for (int i = 1; i <= 2; i++) {
				LangitSink shard = new LangitSink(LangitSink.ROCK);
				float back = 2.6F * i + 0.4F * Mth.sin(state.spin * 0.05F + i);
				float side = (i == 1 ? 0.9F : -0.7F) * (1.0F + 0.2F * Mth.sin(state.spin * 0.03F));
				poseStack.pushPose();
				poseStack.translate(-state.dirX * back + side * state.dirZ, 0.5F - state.dirY * back, -state.dirZ * back - side * state.dirX);
				poseStack.rotateDegrees(Axis.ZP, state.spin * (1.3F + i * 0.4F));
				poseStack.rotateDegrees(Axis.XP, state.spin * 0.9F + i * 40.0F);
				float k = 0.55F - 0.12F * i;
				poseStack.scale(k, k, k);
				lump(shard, state.seed * 31 + i);
				shard.submit(collector, poseStack);
				poseStack.popPose();
			}
		}

		if (!EpicBudget.takeQuads(state.great ? 10 : 6)) {
			return;
		}
		// glows and the trail, in the entity's frame but facing the camera
		LangitSink glow = new LangitSink(LangitSink.HEAD);
		LangitSink trail = new LangitSink(LangitSink.STREAK);
		float hx = 0.0F;
		float hy = 0.5F;
		float hz = 0.0F;
		float length = (10.0F + 6.0F * (1.0F - state.progress)) * (state.great ? 2.2F : 1.0F);
		float tx = hx - state.dirX * length;
		float ty = hy - state.dirY * length;
		float tz = hz - state.dirZ * length;
		trailStrip(trail, tx, ty, tz, hx, hy, hz, ex, ey - 0.5F, ez, 0.25F * g, 2.4F * g, LangitRenderer.argb(0.0F, 0x7FD8FF),
				LangitRenderer.argb(0.95F, 0xE8FAFF));
		trailStrip(trail, tx * 0.7F, ty * 0.7F + 0.15F, tz * 0.7F, hx, hy, hz, ex, ey - 0.5F, ez, 0.6F * g, 5.0F * g,
				LangitRenderer.argb(0.0F, 0x5FB8FF), LangitRenderer.argb(0.35F, 0x9FE0FF));
		billboard(glow, hx, hy, hz, 1.9F * g, LangitRenderer.argb(0.95F, 0xFFFFFF), right, up);
		billboard(glow, hx, hy, hz, 4.2F * g, LangitRenderer.argb(0.4F, 0x8FD8FF), right, up);
		if (state.great) {
			// a wide smoke-and-fire wake and a second, greener sheath (burning star iron)
			trailStrip(trail, tx * 1.3F, ty * 1.3F, tz * 1.3F, hx, hy, hz, ex, ey - 0.5F, ez, 4.0F, 12.0F,
					LangitRenderer.argb(0.0F, 0x506070), LangitRenderer.argb(0.22F, 0xB8F0D8));
			billboard(glow, hx, hy, hz, 16.0F, LangitRenderer.argb(0.18F, 0xA8FFD0), right, up);
		}
		glow.submit(collector, poseStack);
		trail.submit(collector, poseStack);
		super.submit(state, poseStack, collector, camera);
	}

	/** A lumpy rock: a cube whose corners are pushed in and out by the seed (8 corners, 6 faces of 2 triangles as quads). */
	private static void lump(LangitSink sink, int seed) {
		float[][] corners = new float[8][3];
		for (int i = 0; i < 8; i++) {
			float jitter = 0.78F + ((seed >>> (i * 3)) & 7) / 7.0F * 0.32F;
			corners[i][0] = ((i & 1) == 0 ? -0.55F : 0.55F) * jitter;
			corners[i][1] = ((i & 2) == 0 ? -0.55F : 0.55F) * jitter;
			corners[i][2] = ((i & 4) == 0 ? -0.55F : 0.55F) * jitter;
		}
		int color = 0xFFFFFFFF;
		int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
		float[][] normals = {{0, 0, -1}, {0, 0, 1}, {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}};
		for (int f = 0; f < faces.length; f++) {
			int[] face = faces[f];
			float[] n = normals[f];
			float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
			for (int k = 0; k < 4; k++) {
				float[] c = corners[face[k]];
				sink.vertex(c[0], c[1], c[2], uv[k][0], uv[k][1], color, n[0], n[1], n[2]);
			}
		}
	}

	/** A strip from tail (t) to head (h) facing the camera at {@code cam} (entity-relative camera offset negated). */
	private static void trailStrip(LangitSink sink, float tx, float ty, float tz, float hx, float hy, float hz, float ex, float ey, float ez,
								   float tailWidth, float headWidth, int tailColor, int headColor) {
		// positions relative to the camera, for the side vector
		float ax = tx + ex;
		float ay = ty + ey;
		float az = tz + ez;
		float bx = hx + ex;
		float by = hy + ey;
		float bz = hz + ez;
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float sx = dy * az - dz * ay;
		float sy = dz * ax - dx * az;
		float sz = dx * ay - dy * ax;
		float length = Mth.sqrt(sx * sx + sy * sy + sz * sz);
		if (length < 1.0E-5F) {
			return;
		}
		sx /= length;
		sy /= length;
		sz /= length;
		float ht = tailWidth * 0.5F;
		float hh = headWidth * 0.5F;
		sink.vertex(tx - sx * ht, ty - sy * ht, tz - sz * ht, 0.0F, 0.0F, tailColor, 0.0F, 1.0F, 0.0F);
		sink.vertex(hx - sx * hh, hy - sy * hh, hz - sz * hh, 1.0F, 0.0F, headColor, 0.0F, 1.0F, 0.0F);
		sink.vertex(hx + sx * hh, hy + sy * hh, hz + sz * hh, 1.0F, 1.0F, headColor, 0.0F, 1.0F, 0.0F);
		sink.vertex(tx + sx * ht, ty + sy * ht, tz + sz * ht, 0.0F, 1.0F, tailColor, 0.0F, 1.0F, 0.0F);
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
}
