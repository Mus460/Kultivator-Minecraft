package dev.kultivator.client.epic.kosmik;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The pocket of a stage shot (DESIGN 32.1.1): a point C near the caster with a sphere of radius R_d that holds only air,
 * where the real camera stands still while the virtual eye moves in the stage. Searched once per stage shot start
 * (≤ 0.3 ms: at most 25 candidates of 49 heightmap samples each, early-out on the first failure):
 * <ul>
 * <li>R_d 12 b (Normal, Maximal), 10 b (Minimal), 8 b (Kamera Penuh); at least 6 b (Penuh 5 b); fog: the last frame's fog
 * start must be ≥ 2.5 R_d, else R_d shrinks to fog / 2.5 while that stays above the minimum;</li>
 * <li>candidates: straight above the caster's feet at {@code R_d + 3}, then +4 b steps up to 46 b (Penuh 24 b), then the
 * same heights 8 b to each of the four sides; each ≤ 64 b and ≤ render distance × 16 − 24 b from the caster;</li>
 * <li>a candidate passes when C − R_d − 2 is above the highest {@code WORLD_SURFACE} of 7 × 7 samples over the square of
 * side 2 R_d + 4, C + R_d + 2 is below the build limit, every sampled chunk is loaded, no entity but the caster is within
 * R_d + 2, the vanilla cloud layer stays out of it and (Penuh, whose segment clips from the eye) the line from the caster's
 * eye to C is clear;</li>
 * <li>never with Blindness or Darkness.</li>
 * </ul>
 * No candidate: null and the shot plays its fallback (the film never waits).
 */
public final class StagePocket {

	/** A found pocket. */
	public record Found(Vec3 c, float radius) {
	}

	private static String lastReason = "-";
	private static double lastHeight = -1.0D;
	private static float lastRadius;

	/** Searches a pocket around the caster {@code player}; null (with {@link #lastReason}) when none. */
	public static @Nullable Found find(LocalPlayer player, int quality, boolean calm, float fogStart) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || player == null) {
			return refuse("level");
		}
		if (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS)) {
			return refuse("blind");
		}
		float target = calm ? 8.0F : quality <= 0 ? 10.0F : 12.0F;
		float minimum = calm ? 5.0F : 6.0F;
		float rd = target;
		if (fogStart > 0.0F && fogStart < 2.5F * rd) {
			rd = fogStart / 2.5F;
			if (rd < minimum) {
				return refuse(String.format(Locale.ROOT, "fog %.0f", fogStart));
			}
		}
		Vec3 feet = player.position();
		Vec3 eye = player.getEyePosition();
		int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
		double reach = Math.max(16.0D, Math.min(64.0D, chunks * 16.0D - 24.0D));
		double top = calm ? 24.0D : 46.0D;
		double[][] shifts = {{0.0D, 0.0D}, {8.0D, 0.0D}, {-8.0D, 0.0D}, {0.0D, 8.0D}, {0.0D, -8.0D}};
		String reason = "space";
		for (double[] shift : shifts) {
			if (calm && (shift[0] != 0.0D || shift[1] != 0.0D)) {
				// the calm segment clips from the eye: only straight above
				break;
			}
			for (double h = rd + 3.0D; h <= top + 1.0E-6D; h += 4.0D) {
				Vec3 c = new Vec3(feet.x + shift[0], feet.y + h, feet.z + shift[1]);
				if (c.distanceTo(feet) > reach) {
					reason = "reach";
					break;
				}
				String fail = test(level, player, c, rd, calm, eye);
				if (fail == null) {
					lastReason = "-";
					lastHeight = h;
					lastRadius = rd;
					return new Found(c, rd);
				}
				reason = fail;
				if ("chunk".equals(fail) || "limit".equals(fail)) {
					break;
				}
			}
		}
		return refuse(reason);
	}

	/** Null when the pocket at C holds. */
	private static @Nullable String test(ClientLevel level, LocalPlayer player, Vec3 c, float rd, boolean calm, Vec3 eye) {
		if (c.y + rd + 2.0D > level.getMaxY()) {
			return "limit";
		}
		double half = rd + 2.0D;
		double floor = c.y - rd - 2.0D;
		for (int i = 0; i < 7; i++) {
			int x = Mth.floor(c.x - half + 2.0D * half * i / 6.0D);
			for (int j = 0; j < 7; j++) {
				int z = Mth.floor(c.z - half + 2.0D * half * j / 6.0D);
				if (!level.hasChunkAt(x, z)) {
					return "chunk";
				}
				if (level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) > floor) {
					return "terrain";
				}
			}
		}
		// the vanilla cloud layer (4 b thick) must not cross the pocket: it would show inside the dome
		double cloud = cloudHeight(level, c);
		if (Double.isFinite(cloud) && c.y + rd + 2.0D > cloud - 2.0D && c.y - rd - 2.0D < cloud + 6.0D) {
			return "clouds";
		}
		double r = rd + 2.0D;
		List<Entity> near = level.getEntities(player, new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r),
				entity -> entity != player && !entity.isSpectator() && entity.position().distanceTo(c) <= r + entity.getBbWidth());
		if (!near.isEmpty()) {
			return "entity";
		}
		if (calm) {
			HitResult hit = level.clip(new ClipContext(eye, c, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, player));
			if (hit.getType() != HitResult.Type.MISS) {
				return "line";
			}
		}
		return null;
	}

	/** The cloud layer's height at C (an environment attribute), NaN when unknown. */
	private static double cloudHeight(ClientLevel level, Vec3 c) {
		try {
			Float height = level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, c);
			return height == null ? Double.NaN : height;
		} catch (RuntimeException exception) {
			return Double.NaN;
		}
	}

	private static @Nullable Found refuse(String reason) {
		lastReason = reason;
		lastHeight = -1.0D;
		return null;
	}

	/** The last search for {@code /sinematik kosmik3d info}. */
	public static String describe() {
		return lastHeight < 0.0D ? "pocket refused: " + lastReason
				: String.format(Locale.ROOT, "pocket C +%.0f b, R_d %.1f b", lastHeight, lastRadius);
	}

	public static String lastReason() {
		return lastReason;
	}

	private StagePocket() {
	}
}
