package dev.kultivator.duel.compat;

import dev.kultivator.Kultivator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Combat Roll 3.0.1 ({@code combat_roll}) by reflection only (DESIGN 35.3.14, contract C35.U4): no compile dependency and
 * no mixin into its classes. When the mod is present, a {@link Proxy} for
 * {@code net.combat_roll.api.event.ServerSideRollEvents$PlayerStartRolling} is registered on
 * {@code ServerSideRollEvents.PLAYER_START_ROLLING} ({@code Event.register(T)}, verified with javap on the 3.0.1 jar);
 * every roll then reaches {@code DuelHooks.onRoll(player, direction)} (module S), which gives the roller the 6 dodge ticks
 * of an Evade. Any reflection failure logs one line and turns the integration off; the duel itself keeps working (the
 * Shadow Step dodge of 35.3.2 needs no other mod).
 */
public final class CombatRollCompat {

	private static final String EVENTS = "net.combat_roll.api.event.ServerSideRollEvents";
	private static final String LISTENER = "net.combat_roll.api.event.ServerSideRollEvents$PlayerStartRolling";
	private static final String CALLBACK = "onPlayerStartedRolling";

	private static boolean tried;
	private static boolean available;
	private static boolean failedOnce;

	/** True once the roll listener is registered. */
	public static boolean available() {
		return available;
	}

	/** Registers the roll listener once when Combat Roll is installed (called by {@link DuelCompat#init}). */
	static void init() {
		if (tried) {
			return;
		}
		tried = true;
		Class<?> events;
		try {
			events = Class.forName(EVENTS);
		} catch (ClassNotFoundException | LinkageError missing) {
			return;
		}
		try {
			Class<?> listener = Class.forName(LISTENER);
			Field field = events.getField("PLAYER_START_ROLLING");
			Object event = field.get(null);
			if (event == null) {
				throw new IllegalStateException("PLAYER_START_ROLLING is null");
			}
			InvocationHandler handler = CombatRollCompat::invoke;
			Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, handler);
			Method register = findRegister(event.getClass());
			register.invoke(event, proxy);
			available = true;
			Kultivator.LOGGER.info("Kultivator duel: Combat Roll found, rolls count as duel dodges");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			available = false;
			Kultivator.LOGGER.info("Kultivator duel: Combat Roll API not usable ({}); rolls are ignored by the duel", exception.toString());
		}
	}

	/** {@code Event.register(Object)}: public on the abstract base and on {@code Event$Proxy}. */
	private static Method findRegister(Class<?> type) throws NoSuchMethodException {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			for (Method method : c.getDeclaredMethods()) {
				if (method.getName().equals("register") && method.getParameterCount() == 1 && java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
					method.setAccessible(true);
					return method;
				}
			}
		}
		return type.getMethod("register", Object.class);
	}

	private static Object invoke(Object proxy, Method method, Object[] args) {
		String name = method.getName();
		if (name.equals(CALLBACK) && args != null && args.length == 2) {
			try {
				if (args[0] instanceof ServerPlayer player) {
					Vec3 direction = args[1] instanceof Vec3 vec ? vec : Vec3.ZERO;
					dev.kultivator.duel.DuelHooks.onRoll(player, direction);
				}
			} catch (RuntimeException | LinkageError exception) {
				if (!failedOnce) {
					failedOnce = true;
					Kultivator.LOGGER.warn("Kultivator duel: a Combat Roll event was skipped", exception);
				}
			}
			return null;
		}
		// Object methods of the proxy itself.
		return switch (name) {
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> args != null && args.length == 1 && proxy == args[0];
			case "toString" -> "Kultivator duel roll listener";
			default -> null;
		};
	}

	private CombatRollCompat() {
	}
}
