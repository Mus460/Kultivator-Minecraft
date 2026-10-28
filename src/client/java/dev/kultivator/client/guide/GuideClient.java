package dev.kultivator.client.guide;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

/**
 * Client side of the Kitab Panduan (the in-game guide): the last {@code GuideSync} (done steps, manual ticks, the
 * current chapter), the registration of Panduan › Kitab ({@link GuideScreen}, DESIGN 43.5), the requests to the server
 * ({@code GuideAction} "open" and "tick <id>") and the "Bab selesai" toast. The chapters and steps themselves are shared
 * static data ({@link GuideBook}). The guide key (J) is read by {@code Keys} and opens Panduan › Kitab (43.10.3).
 * <p>
 * A quiet background sync is asked for a few seconds after joining (and every five minutes as a safety net; the server
 * also pushes a sync whenever the progress changes), so the guide tracker's "Panduan: Bab N" line and the chapter toast
 * stay current without opening the book. The server never sets the open flag in an answer to a GuideAction; the client
 * ignores it for such background requests anyway, so the book never pops up by itself.
 */
public final class GuideClient {

	/** Ticks after joining before the first background sync. */
	private static final int FIRST_REFRESH = 100;
	/**
	 * Ticks between background syncs (one tiny packet every five minutes): only a safety net, since the server pushes a
	 * GuideSync by itself whenever the progress changes.
	 */
	private static final int REFRESH = 6000;
	/** How long the answer to a background request may take before an open flag counts again. */
	private static final long BACKGROUND_MILLIS = 4000L;

	private static BitSet done = new BitSet();
	private static BitSet manual = new BitSet();
	private static int current;
	private static boolean synced;
	private static int version;
	private static int refreshIn = FIRST_REFRESH;
	private static long backgroundUntil;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.GuideSync.TYPE, (payload, context) -> onSync(payload));
		HubPages.daftar(HubNav.PANDUAN, Urutan.ID_KITAB, Urutan.KITAB, GuideScreen::new);
		ClientTickEvents.END_CLIENT_TICK.register(GuideClient::tick);
		ClientDisconnect.register((handler, client) -> client.execute(() -> clear()));
	}

	private static void clear() {
		done = new BitSet();
		manual = new BitSet();
		current = 0;
		synced = false;
		refreshIn = FIRST_REFRESH;
		backgroundUntil = 0L;
		version++;
	}

	// ================================================================ state

	/** True once a {@code GuideSync} arrived in this world. */
	public static boolean synced() {
		return synced;
	}

	/** Changes with every sync and every local tick (screens rebuild what depends on it). */
	public static int version() {
		return version;
	}

	/**
	 * The current chapter as a display position (0-based; "Bab N" is this + 1): the server picks it by realm, hall and the
	 * core steps left (GuideProgress.currentChapter) and sends its save index, mapped here to the book's order.
	 */
	public static int current() {
		return Math.max(0, Math.min(GuideBook.chapterCount() - 1, GuideBook.positionOf(current)));
	}

	/**
	 * The next step of the current chapter for the HUD tracker when no Langkah chain is active: its first open core step,
	 * else its first open step; null before the first sync, when everything is done or the chapter has nothing open.
	 */
	public static @Nullable GuideBook.Step nextStep() {
		if (!synced || allDone()) {
			return null;
		}
		GuideBook.Step any = null;
		for (GuideBook.Step step : GuideBook.chapter(current()).steps()) {
			if (done(step.index())) {
				continue;
			}
			if (step.core()) {
				return step;
			}
			if (any == null) {
				any = step;
			}
		}
		return any;
	}

	/** True when every step of every chapter is done. */
	public static boolean allDone() {
		return synced && doneCount() >= GuideBook.stepCount();
	}

	/** Whether the step at {@code index} (GuideChapters order) is done: by its check, or by the player's own tick. */
	public static boolean done(int index) {
		return index >= 0 && (done.get(index) || ticked(index));
	}

	/** Whether the player ticked this manual step. */
	public static boolean ticked(int index) {
		return index >= 0 && manual.get(index) && GuideBook.isManual(index);
	}

	public static int doneIn(int chapter) {
		int count = 0;
		for (GuideBook.Step step : GuideBook.chapter(chapter).steps()) {
			if (done(step.index())) {
				count++;
			}
		}
		return count;
	}

	public static boolean chapterDone(int chapter) {
		return synced && doneIn(chapter) >= GuideBook.chapter(chapter).steps().size();
	}

	public static int doneCount() {
		int count = 0;
		for (int i = 0; i < GuideBook.stepCount(); i++) {
			if (done(i)) {
				count++;
			}
		}
		return count;
	}

	// ================================================================ network

	private static void onSync(Payloads.GuideSync payload) {
		BitSet newDone = GuidePayloads.done(payload);
		BitSet newManual = GuidePayloads.manual(payload);
		boolean wasSynced = synced;
		boolean[] before = new boolean[GuideBook.chapterCount()];
		for (int i = 0; i < before.length; i++) {
			before[i] = chapterDone(i);
		}
		done = newDone;
		manual = newManual;
		current = GuidePayloads.current(payload);
		synced = true;
		version++;
		Minecraft minecraft = Minecraft.getInstance();
		if (wasSynced) {
			for (int i = 0; i < before.length; i++) {
				if (!before[i] && chapterDone(i)) {
					chapterToast(minecraft, i);
				}
			}
		}
		boolean background = Util.getMillis() < backgroundUntil;
		backgroundUntil = 0L;
		if (GuidePayloads.open(payload) && !background && minecraft.player != null && !GuideScreen.terbuka) {
			open(null);
		}
	}

	/**
	 * Opens Panduan › Kitab › Bab in the Gulungan Utama (43.4.4). {@code parent} is ignored: Esc steps back inside the
	 * Gulungan. The page asks the server for a fresh sync itself when it shows, so no request is sent here.
	 */
	public static void open(@Nullable Screen parent) {
		HubNav.buka(Tujuan.of(HubNav.PANDUAN, Urutan.ID_KITAB).sub(GuideScreen.SUB_BAB));
	}

	/** Asks for a fresh sync while the book is shown (the page calls this when it shows). */
	static void requestSync() {
		send(Payloads.GuideAction.OPEN);
	}

	/** Ticks or unticks a manual step; shown at once, the server's sync confirms it. */
	static void toggle(GuideBook.Step step) {
		if (!step.manual() || !connected()) {
			return;
		}
		manual.set(step.index(), !manual.get(step.index()));
		version++;
		send(Payloads.GuideAction.TICK + " " + step.id());
	}

	static boolean connected() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.getConnection() != null && ClientPlayNetworking.canSend(Payloads.GuideAction.TYPE);
	}

	private static void send(String action) {
		if (connected()) {
			ClientPlayNetworking.send(GuidePayloads.action(action));
		}
	}

	// ================================================================ tick

	private static void tick(Minecraft minecraft) {
		boolean inWorld = minecraft.player != null && Keys.connected();
		if (!inWorld || !ClientCultivation.received()) {
			return;
		}
		if (--refreshIn <= 0) {
			refreshIn = REFRESH;
			if (!GuideScreen.terbuka && connected()) {
				backgroundUntil = Util.getMillis() + BACKGROUND_MILLIS;
				send(Payloads.GuideAction.OPEN);
			}
		}
	}

	private static void chapterToast(Minecraft minecraft, int chapter) {
		dev.kultivator.client.hud.Kabar.kirim(Component.translatable("kultivator.guide_client.toast.title"),
				Component.translatable("kultivator.guide_client.toast.body", chapter + 1, GuideBook.chapter(chapter).title()),
				dev.kultivator.client.hud.Kabar.glyph(dev.kultivator.client.screen.ui.Glyphs.JALAN), false);
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.8F));
	}

	private GuideClient() {
	}
}
