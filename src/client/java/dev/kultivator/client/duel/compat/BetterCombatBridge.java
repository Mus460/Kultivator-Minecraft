package dev.kultivator.client.duel.compat;

import dev.kultivator.Kultivator;
import dev.kultivator.duel.clash.SwingDir;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Better Combat 3.2.2 by reflection only (DESIGN 35.3.14, contract C35.U4; the API verified with javap on the jar):
 * <ul>
 * <li>{@code net.bettercombat.api.client.BetterCombatClientEvents.ATTACK_START} is a {@code Publisher<PlayerAttackStart>};
 * {@code Publisher.register(T)} gets a {@link Proxy} whose {@code onPlayerAttackStart(LocalPlayer, AttackHand)} reads the
 * record {@code AttackHand(attack, combo, isOffHand, attributes, itemStack)}: {@code attack().animation()} (e.g.
 * {@code bettercombat:one_handed_slash_horizontal_right}), {@code combo().current()/total()}, {@code upswingRate()},
 * {@code attributes().isTwoHanded()/category()}.</li>
 * <li>{@code net.bettercombat.api.MinecraftClient_BetterCombat} is mixed into {@code Minecraft};
 * {@link #swingInProgress()} asks its {@code isWeaponSwingInProgress()} (the animation module's "arms yield" rule).</li>
 * </ul>
 * Each swing becomes a {@code DuelInput BC_SWING} (through the listener set by {@code DuelInput}) with the direction of
 * the table below, the windup {@code round(upswingRate × attack strength delay)} kept within ±2 ticks of the weapon
 * class, heavy on the last attack of the combo or for two-handed weapons. Better Combat keeps its own animation, trail
 * and sound; nothing of it is cancelled. Any reflection failure logs one line and sets {@link #available()} false; the
 * duel then treats Better Combat hits as vanilla hits with the reactive deflect.
 */
public final class BetterCombatBridge {

	/** What a swing hands to the duel input. */
	public interface SwingListener {
		void swing(SwingDir dir, int windup, boolean heavy, boolean offhand, String animation);
	}

	private static final String EVENTS = "net.bettercombat.api.client.BetterCombatClientEvents";
	private static final String START = "net.bettercombat.api.client.BetterCombatClientEvents$PlayerAttackStart";
	private static final String CLIENT = "net.bettercombat.api.MinecraftClient_BetterCombat";

	/** 35.3.14: Better Combat animation (without namespace) → direction. */
	private static final Map<String, SwingDir> TABLE = new HashMap<>();

	static {
		for (String a : new String[]{"one_handed_slash_horizontal_right", "two_handed_slash_horizontal_right", "one_handed_swipe_horizontal_right",
				"one_handed_slash_switch_blade_right"}) {
			TABLE.put(a, SwingDir.KANAN);
		}
		for (String a : new String[]{"one_handed_slash_horizontal_left", "two_handed_slash_horizontal_left", "one_handed_slash_switch_blade_left"}) {
			TABLE.put(a, SwingDir.KIRI);
		}
		TABLE.put("two_handed_slash_vertical_right", SwingDir.ATAS_KANAN);
		TABLE.put("dual_handed_slash_cross", SwingDir.ATAS_KANAN);
		TABLE.put("two_handed_slash_vertical_left", SwingDir.ATAS_KIRI);
		for (String a : new String[]{"one_handed_slam", "one_handed_slam_instant", "two_handed_slam", "two_handed_slam_heavy"}) {
			TABLE.put(a, SwingDir.ATAS);
		}
		TABLE.put("one_handed_uppercut_right", SwingDir.BAWAH_KANAN);
		TABLE.put("dual_handed_slash_uncross", SwingDir.BAWAH_KANAN);
		for (String a : new String[]{"one_handed_stab", "one_handed_stab_mounted", "two_handed_stab_left", "two_handed_stab_right", "dual_handed_stab",
				"one_handed_punch"}) {
			TABLE.put(a, SwingDir.TUSUK);
		}
		TABLE.put("two_handed_spin", SwingDir.PUTAR);
	}

	private static boolean tried;
	private static boolean available;
	private static boolean failed;
	private static @Nullable SwingListener listener;

	private static @Nullable Method attackOf;
	private static @Nullable Method comboOf;
	private static @Nullable Method offHandOf;
	private static @Nullable Method upswingRateOf;
	private static @Nullable Method attributesOf;
	private static @Nullable Method animationOf;
	private static @Nullable Method currentOf;
	private static @Nullable Method totalOf;
	private static @Nullable Method twoHandedOf;
	private static @Nullable Method categoryOf;
	private static @Nullable Class<?> clientInterface;
	private static @Nullable Method swingInProgress;

	/** The last swing seen (for {@code /kultivasi uji duel bc}). */
	private static String lastAnimation = "";
	private static @Nullable SwingDir lastDir;
	private static boolean lastKnown;
	private static int lastWindup;
	private static boolean lastHeavy;
	private static String lastCategory = "";

	/** True when the Better Combat mod is loaded (whether or not the bridge works). */
	public static boolean installed() {
		try {
			return FabricLoader.getInstance().isModLoaded("bettercombat");
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** True once the ATTACK_START listener is registered. */
	public static boolean available() {
		return available;
	}

	/** Sets who receives the swings ({@code DuelInput}). */
	public static void listen(@Nullable SwingListener target) {
		listener = target;
	}

	/** Registers the listener once (from {@code DuelClient.init}); does nothing without Better Combat. */
	public static void init() {
		if (tried) {
			return;
		}
		tried = true;
		if (!installed()) {
			return;
		}
		try {
			Class<?> events = Class.forName(EVENTS);
			Class<?> startType = Class.forName(START);
			Field field = events.getField("ATTACK_START");
			Object publisher = field.get(null);
			if (publisher == null) {
				throw new IllegalStateException("ATTACK_START is null");
			}
			Method register = publisher.getClass().getMethod("register", Object.class);
			InvocationHandler handler = BetterCombatBridge::invoke;
			Object proxy = Proxy.newProxyInstance(startType.getClassLoader(), new Class<?>[]{startType}, handler);
			register.invoke(publisher, proxy);
			try {
				clientInterface = Class.forName(CLIENT);
				swingInProgress = clientInterface.getMethod("isWeaponSwingInProgress");
			} catch (ReflectiveOperationException | LinkageError optional) {
				clientInterface = null;
				swingInProgress = null;
			}
			available = true;
			Kultivator.LOGGER.info("Kultivator duel: Better Combat found, its swings feed the duel (IKUTI_BC)");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			available = false;
			Kultivator.LOGGER.info("Kultivator duel: Better Combat API not usable ({}); its hits count as vanilla hits", exception.toString());
		}
	}

	/** True while the local player's Better Combat swing animation runs (false without Better Combat). */
	public static boolean swingInProgress() {
		Method method = swingInProgress;
		Class<?> type = clientInterface;
		if (!available || method == null || type == null || failed) {
			return false;
		}
		try {
			Minecraft minecraft = Minecraft.getInstance();
			return type.isInstance(minecraft) && Boolean.TRUE.equals(method.invoke(minecraft));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			fail(exception);
			return false;
		}
	}

	/** The direction of a Better Combat animation id (with or without namespace), or null when unknown. */
	public static @Nullable SwingDir direction(@Nullable String animation) {
		if (animation == null || animation.isEmpty()) {
			return null;
		}
		String path = animation.toLowerCase(Locale.ROOT);
		int colon = path.indexOf(':');
		if (colon >= 0) {
			path = path.substring(colon + 1);
		}
		return TABLE.get(path);
	}

	/** The chat lines of {@code /kultivasi uji duel bc}: the last animation, its direction, windup, weight. */
	public static Component info() {
		if (!installed()) {
			return Component.translatable("kultivator.duel.ui.bc.none");
		}
		if (!available) {
			return Component.translatable("kultivator.duel.ui.bc.broken");
		}
		if (lastAnimation.isEmpty()) {
			return Component.translatable("kultivator.duel.ui.bc.nothing");
		}
		Component dir = lastDir == null ? Component.literal("?")
				: Component.translatable("kultivator.duel.ui.dir." + lastDir.name().toLowerCase(Locale.ROOT));
		return Component.translatable(lastKnown ? "kultivator.duel.ui.bc.last" : "kultivator.duel.ui.bc.last_unknown", lastAnimation, dir,
				lastWindup, Component.translatable(lastHeavy ? "kultivator.duel.ui.bc.heavy" : "kultivator.duel.ui.bc.light"),
				lastCategory.isEmpty() ? "-" : lastCategory);
	}

	// ================================================================== the proxy

	private static Object invoke(Object proxy, Method method, Object[] args) {
		String name = method.getName();
		if (name.equals("onPlayerAttackStart") && args != null && args.length == 2) {
			try {
				if (args[0] instanceof LocalPlayer player && args[1] != null) {
					onStart(player, args[1]);
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
				fail(exception);
			}
			return null;
		}
		return switch (name) {
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> args != null && args.length == 1 && proxy == args[0];
			case "toString" -> "Kultivator duel Better Combat listener";
			default -> null;
		};
	}

	private static void onStart(LocalPlayer player, Object hand) throws ReflectiveOperationException {
		if (failed) {
			return;
		}
		resolve(hand.getClass());
		Object attack = attackOf.invoke(hand);
		Object combo = comboOf.invoke(hand);
		boolean offhand = Boolean.TRUE.equals(offHandOf.invoke(hand));
		double upswingRate = ((Number) upswingRateOf.invoke(hand)).doubleValue();
		Object attributes = attributesOf.invoke(hand);
		String animation = attack == null ? "" : String.valueOf(animationOf(attack));
		int current = combo == null ? 1 : ((Number) currentOf(combo, "current")).intValue();
		int total = combo == null ? 1 : ((Number) currentOf(combo, "total")).intValue();
		boolean twoHanded = attributes != null && Boolean.TRUE.equals(call(attributes, "isTwoHanded"));
		Object category = attributes == null ? null : call(attributes, "category");

		SwingDir dir = direction(animation);
		lastKnown = dir != null;
		lastAnimation = animation;
		lastDir = dir;
		lastCategory = category == null ? "" : category.toString();
		// The last attack of a combo is the heavy one (35.3.2); a two-handed weapon's finisher is heavy by the same rule.
		boolean heavy = total > 1 && current >= total;
		lastCategory = (twoHanded ? "two_handed " : "") + lastCategory;
		int windup = (int) Math.round(upswingRate * player.getCurrentItemAttackStrengthDelay());
		lastWindup = windup;
		lastHeavy = heavy;
		SwingListener target = listener;
		if (target != null) {
			target.swing(dir, windup, heavy, offhand, animation);
		}
	}

	private static void resolve(Class<?> handType) throws ReflectiveOperationException {
		if (attackOf != null) {
			return;
		}
		attackOf = handType.getMethod("attack");
		comboOf = handType.getMethod("combo");
		offHandOf = handType.getMethod("isOffHand");
		upswingRateOf = handType.getMethod("upswingRate");
		attributesOf = handType.getMethod("attributes");
	}

	private static Object animationOf(Object attack) throws ReflectiveOperationException {
		if (animationOf == null) {
			animationOf = attack.getClass().getMethod("animation");
		}
		return animationOf.invoke(attack);
	}

	private static Object currentOf(Object combo, String which) throws ReflectiveOperationException {
		if (currentOf == null) {
			currentOf = combo.getClass().getMethod("current");
			totalOf = combo.getClass().getMethod("total");
		}
		return ("total".equals(which) ? totalOf : currentOf).invoke(combo);
	}

	private static @Nullable Object call(Object target, String name) throws ReflectiveOperationException {
		Method method;
		if ("isTwoHanded".equals(name)) {
			if (twoHandedOf == null) {
				twoHandedOf = target.getClass().getMethod(name);
			}
			method = twoHandedOf;
		} else {
			if (categoryOf == null) {
				categoryOf = target.getClass().getMethod(name);
			}
			method = categoryOf;
		}
		return method.invoke(target);
	}

	private static void fail(Throwable exception) {
		if (!failed) {
			failed = true;
			available = false;
			Kultivator.LOGGER.info("Kultivator duel: Better Combat call failed ({}); the bridge is off for this session", exception.toString());
		}
	}

	private BetterCombatBridge() {
	}
}
