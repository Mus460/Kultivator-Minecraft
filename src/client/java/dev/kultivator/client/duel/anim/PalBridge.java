package dev.kultivator.client.duel.anim;

import dev.kultivator.Kultivator;
import dev.kultivator.client.duel.ui.DuelSettings;
import dev.kultivator.duel.blade.DuelPose;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Optional Player Animation Library playback of the duel poses for players (DESIGN 35.5.6), setting {@code duel_pal}, off
 * by default and never required: the duel's own poses ({@link DuelPoses}) always play.
 * <p>
 * Everything is reflection (the pattern of {@code client/epic/ShaderCompat}): no compile dependency, no mixin into another
 * mod. The bridge looks for {@code com.zigythebird.playeranim.api.PlayerAnimationAccess.getPlayerAnimationLayer(Avatar,
 * Identifier)} and a controller on the layer {@code kultivator:duel} with {@code triggerAnimation(Identifier)}; it plays
 * {@code kultivator:duel_<pose>} only when that animation file is shipped in
 * {@code assets/kultivator/player_animations/}. Any failure (the library absent, a changed API, no layer, no file) logs one
 * line and turns the bridge off for the session; the pose system is untouched by it.
 */
public final class PalBridge {

	private static final String MOD_ID = "player_animation_library";
	private static final String ACCESS = "com.zigythebird.playeranim.api.PlayerAnimationAccess";
	private static final Identifier LAYER = Kultivator.id("duel");

	private static boolean probed;
	private static boolean usable;
	private static boolean logged;
	private static @Nullable Method getLayer;
	private static @Nullable Method trigger;

	/** True when the setting is on and the library answered the probe. */
	public static boolean active() {
		if (!DuelSettings.pal()) {
			return false;
		}
		if (!probed) {
			probe();
		}
		return usable;
	}

	/**
	 * Asks Player Animation Library to play the pose on a player, if it can; returns true when it did (the caller still
	 * plays its own pose underneath: the library's arms win where it animates).
	 */
	public static boolean play(AbstractClientPlayer player, DuelPose pose) {
		if (!active() || player == null || pose == null) {
			return false;
		}
		Identifier animation = Kultivator.id("duel_" + pose.name().toLowerCase(Locale.ROOT));
		if (Minecraft.getInstance().getResourceManager().getResource(Kultivator.id("player_animations/" + animation.getPath() + ".json")).isEmpty()) {
			return false;
		}
		try {
			Object layer = getLayer.invoke(null, player, LAYER);
			if (layer == null) {
				return false;
			}
			Method method = trigger;
			if (method == null) {
				method = layer.getClass().getMethod("triggerAnimation", Identifier.class);
				trigger = method;
			}
			method.invoke(layer, animation);
			return true;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			fail(error);
			return false;
		}
	}

	private static void probe() {
		probed = true;
		try {
			if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
				return;
			}
			Class<?> access = Class.forName(ACCESS);
			Class<?> avatar = Class.forName("net.minecraft.world.entity.Avatar");
			getLayer = access.getMethod("getPlayerAnimationLayer", avatar, Identifier.class);
			usable = true;
			Kultivator.LOGGER.info("Kultivator duel: Player Animation Library found; duel animations play through it when shipped");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			fail(error);
		}
	}

	private static void fail(Throwable error) {
		if (!logged) {
			logged = true;
			Kultivator.LOGGER.info("Kultivator duel: Player Animation Library bridge off ({}); the duel uses its own poses", error.toString());
		}
		usable = false;
		probed = true;
	}

	private PalBridge() {
	}
}
