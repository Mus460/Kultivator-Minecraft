package dev.kultivator.client.duel.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.duel.blade.BladeArcs;
import dev.kultivator.duel.blade.BladeSegment;
import dev.kultivator.duel.clash.WeaponClass;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /kultivasi uji duel bilah} (DESIGN 35.8.4, module A): grip-to-tip lines of every blade in range, to tune
 * {@code BladeShape} against the drawn models and to check that sparks land on the blades. The blade as drawn
 * ({@link BladeTracker}, from {@link BladeProbeLayer}) is a line from green (the base) to gold (the point) with a small
 * gold cross at the point; the shared geometry the server tests ({@link BladeArcs} on the same pose) is a thin red line
 * beside it. Where both agree they overlap. Additive POSITION_COLOR quads facing the camera, drawn from
 * {@code LevelRenderEvents.COLLECT_SUBMITS}, never in the shadow pass. Off by default, client only, costs nothing off.
 */
public final class BladeDebug {

	private static final int GRIP_RGB = 0x40FF60;
	private static final int TIP_RGB = 0xFFD040;
	private static final int ARC_RGB = 0xFF3030;
	private static final float WIDTH = 0.025F;

	private static boolean on;
	private static int messageTicks;

	/** A quad strip to draw this frame (camera-relative). */
	private record Line(float ax, float ay, float az, float bx, float by, float bz, int rgbA, int rgbB, float width) {
	}

	private static final List<Line> LINES = new ArrayList<>();

	static void set(boolean enabled) {
		on = enabled;
		messageTicks = 0;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(Component.translatable(enabled ? "kultivator.duel.pose.debug_on" : "kultivator.duel.pose.debug_off"));
		}
	}

	public static boolean on() {
		return on;
	}

	/** Once per client tick: the counts on the action bar every second while on. */
	static void tick(Minecraft minecraft) {
		if (!on || minecraft.player == null) {
			return;
		}
		if (++messageTicks >= 20) {
			messageTicks = 0;
			minecraft.player.sendOverlayMessage(Component.translatable("kultivator.duel.pose.debug_count", BladeTracker.drawnCount(),
					BladeTracker.fallbackCount()));
		}
	}

	static void collect(LevelRenderContext context) {
		if (!on || ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		float partial = context.levelState().worldPartialTicks;
		LINES.clear();
		for (int id : BladeTracker.drawnIds()) {
			BladeSegment drawn = BladeTracker.drawn(id);
			if (drawn == null) {
				continue;
			}
			line(drawn.grip(), drawn.tip(), cam, GRIP_RGB, TIP_RGB, WIDTH);
			Vec3 axis = drawn.axis();
			Vec3 side = axis.cross(drawn.tip().subtract(cam));
			if (side.lengthSqr() > 1.0E-8D) {
				side = side.normalize().scale(0.06D);
				line(drawn.tip().add(side), drawn.tip().subtract(side), cam, TIP_RGB, TIP_RGB, WIDTH);
				Vec3 along = axis.normalize().scale(0.06D);
				line(drawn.tip().add(along), drawn.tip().subtract(along), cam, TIP_RGB, TIP_RGB, WIDTH);
			}
			Entity entity = level.getEntity(id);
			float[] channels = DuelPoses.channels(id);
			if (entity instanceof LivingEntity living && channels != null) {
				WeaponClass weapon = BladeTracker.weaponOf(living);
				float yaw = BladeTracker.lerpDegrees(partial, living.yBodyRotO, living.yBodyRot);
				BladeSegment arc = BladeArcs.fromChannels(channels, weapon, living.getPosition(partial), yaw, living.getScale(),
						BladeTracker.slim(living), living.getMainArm() == HumanoidArm.LEFT);
				line(arc.grip(), arc.tip(), cam, ARC_RGB, ARC_RGB, WIDTH * 0.5F);
			}
		}
		if (LINES.isEmpty()) {
			return;
		}
		PoseStack poseStack = context.poseStack();
		List<Line> lines = List.copyOf(LINES);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			for (Line l : lines) {
				write(pose, buffer, l);
			}
		});
	}

	private static void line(Vec3 a, Vec3 b, Vec3 cam, int rgbA, int rgbB, float width) {
		LINES.add(new Line((float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z), (float) (b.x - cam.x), (float) (b.y - cam.y),
				(float) (b.z - cam.z), rgbA, rgbB, width));
	}

	/** One camera-facing quad from a to b (the camera sits at the origin of the camera-relative frame). */
	private static void write(PoseStack.Pose pose, VertexConsumer buffer, Line l) {
		float dx = l.bx - l.ax;
		float dy = l.by - l.ay;
		float dz = l.bz - l.az;
		// side = direction × (midpoint − camera)
		float mx = (l.ax + l.bx) * 0.5F;
		float my = (l.ay + l.by) * 0.5F;
		float mz = (l.az + l.bz) * 0.5F;
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1.0E-6F) {
			return;
		}
		float k = l.width / len;
		sx *= k;
		sy *= k;
		sz *= k;
		int a = 0xFF000000 | l.rgbA;
		int b = 0xFF000000 | l.rgbB;
		buffer.addVertex(pose, l.ax - sx, l.ay - sy, l.az - sz).setColor(a);
		buffer.addVertex(pose, l.ax + sx, l.ay + sy, l.az + sz).setColor(a);
		buffer.addVertex(pose, l.bx + sx, l.by + sy, l.bz + sz).setColor(b);
		buffer.addVertex(pose, l.bx - sx, l.by - sy, l.bz - sz).setColor(b);
	}

	private BladeDebug() {
	}
}
