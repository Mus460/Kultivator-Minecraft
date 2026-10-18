package dev.kultivator.client.hall;

import dev.kultivator.client.Keys;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.Halls;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;

/**
 * Client side of the Sect Hall (DESIGN 20.3.1): keeps the last {@code HallInfo}, opens the {@link HallScreen} when the
 * server asks ({@code OpenScreen.HALL}, {@code arg} = the page to show first), ticks {@link HallFx} and sends the hall's
 * actions ({@code Actions.HALL} with a sub-op of {@code Halls.OP_*}).
 */
public final class HallClient {

	/** How long an open request waits for the hall's details. */
	private static final long PENDING_MILLIS = 5000L;

	private static CompoundTag info = new CompoundTag();
	private static long version;
	private static long receivedAt;
	private static boolean pendingOpen;
	private static long pendingSince;
	private static int pendingPage;

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(HallFx::tick);
	}

	/** New details of the hall arrived (the open screen picks them up by {@link #version()}). */
	public static void onInfo(Payloads.HallInfo payload) {
		info = payload.data() == null ? new CompoundTag() : payload.data();
		version++;
		receivedAt = Util.getMillis();
		if (pendingOpen) {
			pendingOpen = false;
			if (Util.getMillis() - pendingSince <= PENDING_MILLIS) {
				open(pendingPage);
			}
		}
	}

	/** The server asks to open the hall screen on page {@code arg} ({@code Halls.PAGE_*}: 0 Aula, 1 Bangun, 2 Calon, ...). */
	public static void openScreen(int arg) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof HallScreen screen) {
			screen.showPage(arg);
			return;
		}
		if (version > 0L) {
			open(arg);
		} else {
			pendingOpen = true;
			pendingSince = Util.getMillis();
			pendingPage = arg;
		}
	}

	private static void open(int page) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.gui.setScreen(new HallScreen(page));
		}
	}

	/** The last hall details (never null). */
	public static CompoundTag info() {
		return info;
	}

	/** Grows with every HallInfo. */
	public static long version() {
		return version;
	}

	/** When the last HallInfo arrived (milliseconds, for countdowns). */
	public static long receivedAt() {
		return receivedAt;
	}

	/** Ticks of a server countdown from the last HallInfo that are still left now. */
	public static long ticksLeft(long ticksThen) {
		return Math.max(0L, ticksThen - (Util.getMillis() - receivedAt) / 50L);
	}

	/** Sends a hall action ({@code Halls.OP_*}) with its text parameter. */
	public static void send(int op, String text) {
		Keys.send(new Payloads.Action(Actions.HALL, op, text == null ? "" : text));
	}

	/** The screen closed: the server stops treating us as a viewer. */
	static void closed() {
		send(Halls.OP_CLOSE, "");
	}

	private static void clear() {
		info = new CompoundTag();
		version = 0L;
		pendingOpen = false;
	}

	private HallClient() {
	}
}
