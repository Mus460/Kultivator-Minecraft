package dev.kultivator.client.hub;

import dev.kultivator.client.Keys;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.pusakalangit.TitahSettings;
import dev.kultivator.sect.hall.Halls;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The hub navigation of UI/UX Terpadu (DESIGN 33.3, 33.4.2, contract C33.N1/N2): the jade scroll ({@link CultivationScreen})
 * is the one way in, with eight sections in play order. This class opens it on a section, remembers the last section and
 * sub-coin for the session (plain statics, no file), and gives every screen opened from it the same back behaviour without
 * editing those screens:
 * <ul>
 * <li>a screen of the mod that replaces the hub (or replaces such a child, e.g. a confirm dialog of the codex) is a hub
 * child; so is a screen opened by {@link #openChild} or one of the type announced by {@link #expectChild} arriving within
 * 3 s (server-opened screens);</li>
 * <li>Esc in a child only marks the press; on the next client tick, if the child closed (no screen left), the mark is at
 * most 2 ticks old and no camera shot runs, the hub reopens on the section the child came from;</li>
 * <li>the {@code quick} key (V) or the {@code info} key closes the child and everything under it in one press, unless the
 * child used the key itself (a focused text field).</li>
 * </ul>
 * Screens opened from the world (the stele, M, a command) are not children: Esc simply closes them.
 */
public final class HubNav {

	public static final int DIRI = 0;
	public static final int TEKNIK = 1;
	public static final int MURID = 2;
	public static final int SEKTE = 3;
	public static final int DUNIA = 4;
	public static final int KEABADIAN = 5;
	public static final int PANDUAN = 6;
	public static final int PENGATURAN = 7;
	/** The number of hub sections (number keys 1-8). */
	public static final int SECTIONS = 8;

	/** Pengaturan groups (33.5), the sub-coins of section {@link #PENGATURAN}. */
	public static final int GROUP_TAMPILAN = 0;
	public static final int GROUP_FILM = 1;
	public static final int GROUP_SUARA = 2;
	public static final int GROUP_HUD = 3;
	public static final int GROUP_KONTROL = 4;

	/** Sub-coins of section {@link #PANDUAN}: the Kitab Panduan card, then the Ensiklopedia. */
	public static final int SUB_KITAB = 0;
	public static final int SUB_ENSIKLOPEDIA = 1;

	private static final long EXPECT_MILLIS = 3000L;
	private static final int ESC_TICKS = 2;

	private static int lastSection = DIRI;
	private static final int[] LAST_SUB = new int[SECTIONS];

	private static long ticks;
	private static @Nullable Screen child;
	private static int childSection;
	private static long escTick = Long.MIN_VALUE / 2;
	/** Set when the hub or a child is removed; the next screen of the mod initialised in the same tick inherits it. */
	private static boolean handoff;
	private static int handoffSection;
	private static @Nullable Screen pendingChild;
	private static int pendingSection;
	private static @Nullable Class<? extends Screen> expectType;
	private static int expectSection;
	private static long expectAt;
	private static @Nullable KeyEvent lastEvent;

	/** Registers the screen and tick hooks (called once from {@code KultivatorClient}). */
	public static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> afterInit(screen));
		ClientTickEvents.END_CLIENT_TICK.register(HubNav::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	// ================================================================== the hub

	/** The section the hub shows when opened without one (the last one this session). */
	public static int lastSection() {
		return lastSection;
	}

	/** The last sub-coin of {@code section} this session. */
	public static int lastSub(int section) {
		return section >= 0 && section < SECTIONS ? LAST_SUB[section] : 0;
	}

	/** Called by the hub whenever its section or sub-coin changes. */
	public static void remember(int section, int sub) {
		if (section >= 0 && section < SECTIONS) {
			lastSection = section;
			LAST_SUB[section] = Math.max(0, sub);
		}
	}

	/** Opens the hub on {@code section} (its last sub-coin), or switches the open hub to it. */
	public static void openHub(int section) {
		openHub(section, lastSub(section));
	}

	/**
	 * Opens the hub on {@code section} and sub-coin {@code sub}, or switches the open hub. A new hub never opens while a
	 * camera shot runs (33.0, the 7b camera rules).
	 */
	public static void openHub(int section, int sub) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		int s = Math.max(0, Math.min(SECTIONS - 1, section));
		if (minecraft.gui.screen() instanceof CultivationScreen hub) {
			hub.showSection(s, sub);
			return;
		}
		if (CinematicDirector.active()) {
			return;
		}
		QuickWheel.close();
		minecraft.gui.setScreen(CultivationScreen.hub(s, sub));
	}

	/** Opens Pengaturan on {@code group} ({@code GROUP_*}). */
	public static void openSettings(int group) {
		openHub(PENGATURAN, group);
	}

	/** Opens Panduan › Ensiklopedia on {@code topic} (the "?" seal of every page and screen). */
	public static void help(String topic) {
		openHub(PANDUAN, SUB_ENSIKLOPEDIA);
		if (Minecraft.getInstance().gui.screen() instanceof CultivationScreen hub) {
			hub.showHelp(topic == null || topic.isEmpty() ? "jalan" : topic);
		}
	}

	/**
	 * Opens the player's own Sect Hall on tablet {@code page} ({@code Halls.PAGE_*}) from anywhere (C33.N4,
	 * {@code Halls.OP_OPEN}). Without an own hall the server answers with an action-bar line and nothing opens.
	 */
	public static void openHall(int page) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen current = minecraft.gui.screen();
		if (current instanceof CultivationScreen) {
			expectChild(dev.kultivator.client.hall.HallScreen.class, SEKTE);
		} else if (current != null && current == child) {
			expectChild(dev.kultivator.client.hall.HallScreen.class, childSection);
		}
		Keys.send(new Payloads.Action(Actions.HALL, Halls.OP_OPEN, String.valueOf(page)));
	}

	/** The Titah Langit menu (C33.N4, {@code TitahSettings.OP_OPEN}); the server checks the seal like {@code /kultivasi titah}. */
	public static void openTitah() {
		Keys.send(new Payloads.Action(Actions.TITAH, TitahSettings.OP_OPEN << 16, ""));
	}

	/** Shows {@code screen} as a child of the hub's {@code section}: Esc in it returns there. */
	public static void openChild(Screen screen, int section) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		pendingChild = screen;
		pendingSection = section;
		minecraft.gui.setScreen(screen);
	}

	/** A screen of {@code type} that the server opens within 3 s counts as a child of {@code section}. */
	public static void expectChild(Class<? extends Screen> type, int section) {
		expectType = type;
		expectSection = section;
		expectAt = Util.getMillis();
	}

	/** True when {@code screen} was opened from the hub (Esc returns there). */
	public static boolean fromHub(@Nullable Screen screen) {
		return screen != null && screen == child;
	}

	/** Called by the hub when it is removed (it closed, or another screen replaced it). */
	public static void hubRemoved(int section) {
		handoff = true;
		handoffSection = section;
	}

	// ================================================================== children

	private static void afterInit(Screen screen) {
		if (screen instanceof CultivationScreen) {
			child = null;
			handoff = false;
			pendingChild = null;
			return;
		}
		if (screen == child) {
			listen(screen);
			handoff = false;
			return;
		}
		boolean ours = screen.getClass().getName().startsWith("dev.kultivator.");
		int section = -1;
		if (screen == pendingChild) {
			section = pendingSection;
		} else if (ours && handoff) {
			section = handoffSection;
		} else if (ours && expectType != null && expectType.isInstance(screen) && Util.getMillis() - expectAt <= EXPECT_MILLIS) {
			section = expectSection;
		}
		handoff = false;
		pendingChild = null;
		if (section >= 0) {
			child = screen;
			childSection = section;
			expectType = null;
			listen(screen);
		}
	}

	/** The per-screen listeners (Fabric recreates a screen's events on every init, so this runs after each one). */
	private static void listen(Screen screen) {
		ScreenKeyboardEvents.allowKeyPress(screen).register(HubNav::childKey);
		ScreenEvents.remove(screen).register(removed -> {
			if (removed == child) {
				handoff = true;
				handoffSection = childSection;
			}
		});
	}

	private static boolean childKey(Screen screen, KeyEvent event) {
		if (screen != child || event == lastEvent) {
			return true;
		}
		lastEvent = event;
		if (event.isEscape()) {
			escTick = ticks;
			return true;
		}
		if (Keys.QUICK.matches(event) || Keys.INFO.matches(event)) {
			// A focused text field (codex search, hall name) keeps the letter: the screen sees the key first.
			if (screen.keyPressed(event)) {
				return false;
			}
			closeAll();
			return false;
		}
		return true;
	}

	/** Closes the current screen and everything under it (a container screen also tells the server). */
	public static void closeAll() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen current = minecraft.gui.screen();
		child = null;
		handoff = false;
		pendingChild = null;
		QuickWheel.suppress();
		if (current instanceof AbstractContainerScreen<?> && minecraft.player != null) {
			minecraft.player.closeContainer();
		} else {
			minecraft.gui.setScreen(null);
		}
		handoff = false;
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		handoff = false;
		if (child != null && minecraft.gui.screen() == null) {
			boolean esc = ticks - escTick <= ESC_TICKS;
			int section = childSection;
			child = null;
			if (esc && minecraft.player != null && !CinematicDirector.active()) {
				openHub(section);
			}
		}
		if (expectType != null && Util.getMillis() - expectAt > EXPECT_MILLIS) {
			expectType = null;
		}
	}

	private static void reset() {
		child = null;
		handoff = false;
		pendingChild = null;
		expectType = null;
		lastEvent = null;
	}

	private HubNav() {
	}
}
