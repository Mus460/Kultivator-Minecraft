package dev.kultivator.client.duel.anim;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The animation module of the sword duel (DESIGN 35.5, 35.9 module A, client side). {@link #init} is called once by
 * {@code DuelClient.init} (contract C35.U1): it puts {@link DuelPoses} on the duel bus, counts the rendered frames for
 * {@link BladeTracker}, draws the {@code uji duel bilah} lines ({@link BladeDebug}) and ticks the stances, ceremonies and
 * the pose preview. The blade probe itself is registered by {@code AnimClient} on every PlayerModel renderer, the poses
 * reach the model through {@code PoseRenderData.extract}, and the first-person view through {@code FirstPersonCast}.
 */
public final class DuelAnim {

	private static boolean initialised;
	private static int pruneTicks;
	private static @Nullable ClientLevel lastLevel;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DuelPoses.register();
		LevelRenderEvents.START_MAIN.register(context -> BladeTracker.beginFrame());
		LevelRenderEvents.COLLECT_SUBMITS.register(BladeDebug::collect);
		ClientTickEvents.END_CLIENT_TICK.register(DuelAnim::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(DuelAnim::clear));
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
		if (level == null) {
			return;
		}
		DuelPoses.tick(minecraft);
		BladeDebug.tick(minecraft);
		if (++pruneTicks >= 20) {
			pruneTicks = 0;
			BladeTracker.prune();
		}
	}

	private static void clear() {
		BladeTracker.clear();
		BladeProbeLayer.clear();
		DuelPoses.clear();
	}

	private DuelAnim() {
	}
}
