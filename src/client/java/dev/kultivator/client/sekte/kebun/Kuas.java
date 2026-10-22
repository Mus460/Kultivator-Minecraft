package dev.kultivator.client.sekte.kebun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;

/**
 * Module W's quad brush: the camera-relative, full-bright vertices of one vanilla render type for this frame, submitted
 * once from {@code LevelRenderEvents.COLLECT_SUBMITS} (the pattern of the duel's and the Lei Tai's visuals: no post chain,
 * standard {@code RenderTypes}, so Sodium and Iris stay happy). The glow of the spirit beds and the gold dust of the mine
 * fake a heavy shader with these layered additive-looking quads.
 */
final class Kuas {

	private static final int STRIDE = 8;
	private final RenderType type;
	private float[] data = new float[STRIDE * 256];
	private int[] colours = new int[256];
	private int count;
	private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

	Kuas(RenderType type) {
		this.type = type;
	}

	void reset() {
		count = 0;
	}

	void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
		if (count >= colours.length) {
			int size = colours.length * 2;
			float[] more = new float[size * STRIDE];
			System.arraycopy(data, 0, more, 0, data.length);
			data = more;
			int[] moreColours = new int[size];
			System.arraycopy(colours, 0, moreColours, 0, colours.length);
			colours = moreColours;
		}
		int o = count * STRIDE;
		data[o] = x;
		data[o + 1] = y;
		data[o + 2] = z;
		data[o + 3] = u;
		data[o + 4] = v;
		data[o + 5] = nx;
		data[o + 6] = ny;
		data[o + 7] = nz;
		colours[count] = argb;
		count++;
	}

	/** A flat square of half size {@code half} lying on y at a camera-relative point. */
	void ground(double x, double y, double z, float half, int argb) {
		float px = (float) x;
		float py = (float) y;
		float pz = (float) z;
		vertex(px - half, py, pz - half, 0.0F, 0.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px - half, py, pz + half, 0.0F, 1.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px + half, py, pz + half, 1.0F, 1.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px + half, py, pz - half, 1.0F, 0.0F, argb, 0.0F, 1.0F, 0.0F);
	}

	/** A camera-facing square of half size {@code half} at a camera-relative point ({@code right}/{@code up} of the camera). */
	void billboard(double x, double y, double z, float half, int argb, Vector3f right, Vector3f up) {
		float rx = right.x * half;
		float ry = right.y * half;
		float rz = right.z * half;
		float ux = up.x * half;
		float uy = up.y * half;
		float uz = up.z * half;
		float px = (float) x;
		float py = (float) y;
		float pz = (float) z;
		vertex(px - rx - ux, py - ry - uy, pz - rz - uz, 0.0F, 1.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px + rx - ux, py + ry - uy, pz + rz - uz, 1.0F, 1.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px + rx + ux, py + ry + uy, pz + rz + uz, 1.0F, 0.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px - rx + ux, py - ry + uy, pz - rz + uz, 0.0F, 0.0F, argb, 0.0F, 1.0F, 0.0F);
	}

	/**
	 * An upright ray of half width {@code half} and height {@code height} from a camera-relative foot, turned about y to face
	 * the camera (the texture's bright end at the foot), seen from both sides.
	 */
	void ray(double x, double y, double z, float half, float height, int argb, Vector3f right) {
		float len = (float) Math.sqrt(right.x * right.x + right.z * right.z);
		float rx = len < 1.0E-4F ? half : right.x / len * half;
		float rz = len < 1.0E-4F ? 0.0F : right.z / len * half;
		float px = (float) x;
		float py = (float) y;
		float pz = (float) z;
		vertex(px - rx, py, pz - rz, 0.0F, 1.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px + rx, py, pz + rz, 1.0F, 1.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px + rx, py + height, pz + rz, 1.0F, 0.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px - rx, py + height, pz - rz, 0.0F, 0.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px - rx, py + height, pz - rz, 0.0F, 0.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px + rx, py + height, pz + rz, 1.0F, 0.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px + rx, py, pz + rz, 1.0F, 1.0F, argb, 0.0F, 1.0F, 0.0F);
		vertex(px - rx, py, pz - rz, 0.0F, 1.0F, argb, 0.0F, 1.0F, 0.0F);
	}

	void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
		if (count >= 4) {
			collector.submitCustomGeometry(poseStack, type, writer);
		}
	}

	private void write(PoseStack.Pose pose, VertexConsumer buffer) {
		int vertices = count / 4 * 4;
		for (int i = 0; i < vertices; i++) {
			int o = i * STRIDE;
			buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
					.setColor(colours[i])
					.setUv(data[o + 3], data[o + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightCoordsUtil.FULL_BRIGHT)
					.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
		}
	}
}
