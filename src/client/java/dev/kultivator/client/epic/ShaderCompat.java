package dev.kultivator.client.epic;

import dev.kultivator.Kultivator;

import java.lang.reflect.Method;

/**
 * Iris detection by reflection only (DESIGN 23.14.3), so there is no compile dependency:
 * {@code net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()} chooses the shader-pack profile, refreshed
 * every 100 ticks and after a resource reload; {@code isRenderingShadowPass()} is checked by the renderer. A missing class
 * means the vanilla profile; any reflection error means the vanilla profile and one log line.
 */
public final class ShaderCompat {

	private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
	private static final int REFRESH_TICKS = 100;

	private static boolean looked;
	private static boolean broken;
	private static Object api;
	private static Method packInUse;
	private static Method shadowPass;
	private static boolean pack;
	private static int ticks;

	/** True while a shader pack is in use (the user's main path). */
	public static boolean packOn() {
		look();
		return pack;
	}

	/** True when Iris is installed at all. */
	public static boolean irisFound() {
		look();
		return api != null;
	}

	/** True while Iris draws its shadow pass (7b never draws there). */
	public static boolean shadowPass() {
		if (api == null || shadowPass == null || broken) {
			return false;
		}
		try {
			return (Boolean) shadowPass.invoke(api);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			fail(exception);
			return false;
		}
	}

	/** Re-reads the pack state (every 100 ticks from the director, and after a resource reload). */
	public static void refresh() {
		look();
		if (api == null || packInUse == null || broken) {
			pack = false;
			return;
		}
		try {
			pack = (Boolean) packInUse.invoke(api);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			fail(exception);
		}
	}

	/** Once per client tick (cheap counter). */
	static void tick() {
		if (++ticks >= REFRESH_TICKS) {
			ticks = 0;
			refresh();
		}
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		try {
			Class<?> type = Class.forName(API);
			api = type.getMethod("getInstance").invoke(null);
			packInUse = type.getMethod("isShaderPackInUse");
			shadowPass = type.getMethod("isRenderingShadowPass");
			pack = (Boolean) packInUse.invoke(api);
		} catch (ClassNotFoundException missing) {
			api = null;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			api = null;
			Kultivator.LOGGER.info("Kultivator: Iris API not usable ({}); Momen Epik uses the vanilla profile", exception.toString());
		}
	}

	private static void fail(Throwable exception) {
		if (!broken) {
			broken = true;
			pack = false;
			Kultivator.LOGGER.info("Kultivator: Iris API call failed ({}); Momen Epik uses the vanilla profile", exception.toString());
		}
	}

	private ShaderCompat() {
	}
}
