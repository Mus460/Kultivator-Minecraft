package dev.kultivator.client.duel.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Scorched slash marks on the ground (DESIGN 35.4.5): a flat quad lying 0.015 b above the top face of a solid block
 * ({@code isFaceSturdy(UP)}, found with a read-only {@code level.clip} down to 3 b), turned along the swing, textured with
 * {@code scorch_slash.png}. It glows orange for its first 20 ticks (an emissive copy on top), then stays as dark char and
 * fades over the last 60 of its 300 ticks. Made by sword qi waves (every 1.5 b of their path), guard breaks, deathblows
 * and low sweeps. Caps 6 / 12 / 24 (35.4.12; the oldest leaves first). It never touches a block: the quad only lies on
 * top, never over a liquid, glass or leaves; the light is the world's light at the mark so it sits in the scene.
 */
public final class ScorchDecal {

	public static final int LIFE = 300;
	public static final int HOT = 20;
	public static final int FADE = 60;

	private record Mark(Vec3 at, Vec3 along, Vec3 across, float length, float width, double born, int light) {
	}

	private static final List<Mark> LIVE = new ArrayList<>();

	/**
	 * Adds a mark under {@code near} (searched down 3 b), along {@code direction} (horizontal part), {@code length} by
	 * {@code width} blocks. False when the ground there takes no mark.
	 */
	public static boolean add(Vec3 near, Vec3 direction, float length, float width) {
		Vec3 ground = groundBelow(near.add(0.0D, 0.6D, 0.0D), 3.6D);
		if (ground == null) {
			return false;
		}
		Vec3 along = new Vec3(direction.x, 0.0D, direction.z);
		if (along.lengthSqr() < 1.0E-6D) {
			along = new Vec3(1.0D, 0.0D, 0.0D);
		}
		along = along.normalize();
		Vec3 across = new Vec3(-along.z, 0.0D, along.x);
		int cap = DuelFxSettings.pick(6, 12, 24);
		while (LIVE.size() >= cap && !LIVE.isEmpty()) {
			LIVE.remove(0);
		}
		ClientLevel level = Minecraft.getInstance().level;
		int light = level == null ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.getLightCoords(level, BlockPos.containing(ground.add(0.0D, 0.5D, 0.0D)));
		LIVE.add(new Mark(ground.add(0.0D, 0.015D, 0.0D), along, across, length, width, DuelTime.now(), light));
		return true;
	}

	/**
	 * The top of the solid ground below {@code from} within {@code depth} blocks (read only), or null over a liquid, glass,
	 * leaves, a face that is not sturdy, or nothing.
	 */
	public static @Nullable Vec3 groundBelow(Vec3 from, double depth) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return null;
		}
		Vec3 to = from.subtract(0.0D, depth, 0.0D);
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, minecraft.player));
		if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES) || state.is(BlockTags.IMPERMEABLE)
				|| !state.isFaceSturdy(level, pos, Direction.UP)) {
			return null;
		}
		return hit.getLocation();
	}

	static void tick() {
		if (LIVE.isEmpty()) {
			return;
		}
		double now = DuelTime.now();
		LIVE.removeIf(mark -> now - mark.born > LIFE);
	}

	static void render(DuelGeo frame) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Mark mark : LIVE) {
			float age = (float) (frame.time - mark.born);
			if (age < 0.0F || age > LIFE || frame.dist2(mark.at) > 64.0D * 64.0D || !frame.take(2)) {
				continue;
			}
			float fade = age > LIFE - FADE ? (LIFE - age) / FADE : 1.0F;
			float grow = Math.min(1.0F, 0.35F + age / 3.0F);
			float half = mark.length * 0.5F * grow;
			frame.plane(frame.scorch, mark.at, mark.along, mark.across, half, mark.width * 0.5F, DuelGeo.argb(0.85F * fade, 0x1A1410), mark.light,
					false);
			if (age < HOT) {
				float heat = 1.0F - age / HOT;
				int colour = DuelGeo.mix(0xFFE0A0, 0xFF5A10, 1.0F - heat);
				frame.plane(frame.scorchHot, mark.at.add(0.0D, 0.004D, 0.0D), mark.along, mark.across, half * 0.92F, mark.width * 0.36F,
						DuelGeo.argb(0.9F * heat, colour), LightCoordsUtil.FULL_BRIGHT, false);
			}
		}
	}

	static boolean idle() {
		return LIVE.isEmpty();
	}

	static void clear() {
		LIVE.clear();
	}

	private ScorchDecal() {
	}
}
