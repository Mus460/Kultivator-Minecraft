package dev.kultivator.client.tempat.ui;

import dev.kultivator.Kultivator;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatAksiPayload;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.dunia.tempat.ui.TempatLayarPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * The client half of the 38 screens (DESIGN 38.U.1): a registry from screen id to screen factory, the receiver of
 * {@link TempatLayarPayload}, and the action sender. 38UI's kit screens ({@code client/tempat/ui/layar/**}, W1) and the
 * theme screens of 38TV-U ({@code client/tempat/ui/tema/**}, W2) register here with {@link #daftar}; nothing else edits
 * this class's callers.
 *
 * <h2>Receiving</h2>
 * <ul>
 * <li>empty screen id: closes the open tempat screen (server {@link TempatLayar#tutup});</li>
 * <li>the open screen is a {@link LayarTempat} with the same id and entity: {@link LayarTempat#perbarui} (live refresh;
 * with {@code buka} true the screen may also jump to the sheet's {@link LembarKunci#TAB});</li>
 * <li>otherwise, {@code buka} true: the registered {@link Pembuat} builds the screen; it opens as a hub child
 * ({@link HubNav#openChild}) when it answers a {@link #lihatDariHub} request of the last 3 s, else as a world screen;</li>
 * <li>a refresh for a screen that is not open is dropped.</li>
 * </ul>
 * A screen id with no factory is logged once and ignored (the server keeps working).
 *
 * <h2>Sending</h2>
 * Screens send ops with {@link #kirim} ({@link LembarKunci#op} builds them) and call {@link #ditutup} from
 * {@code Screen.removed()} so the server ends the session.
 *
 * <p>Wired by one line {@code UiTempatClient.init()} in {@code TempatClient.init()} (38W).
 */
public final class UiTempatClient {

	/** How long a hub request waits for its screen to count as a hub child (ms). */
	private static final long HUB_TUNGGU_MS = 3000L;

	/** A tempat screen: the receiver matches it by id and entity, and refreshes it in place. */
	public interface LayarTempat {
		/** Screen id ({@code LembarKunci.*.LAYAR}). */
		String layar();

		/** Entity the screen talks to; -1 for hub/anchor screens. */
		int npcId();

		/**
		 * A new sheet for the open screen. {@code buka} is true when the server (re)opened it (jump to
		 * {@link LembarKunci#TAB} if present), false for a live refresh (keep tab, scroll and focus).
		 */
		void perbarui(CompoundTag lembar, boolean buka);
	}

	/** Builds the screen for a sheet; null refuses (the sheet is unusable). */
	@FunctionalInterface
	public interface Pembuat {
		@Nullable
		Screen buat(int npcId, CompoundTag lembar);
	}

	private static final Map<String, Pembuat> PEMBUAT = new HashMap<>();
	private static final Set<String> TANPA_PEMBUAT = new HashSet<>();
	private static @Nullable String hubLayar;
	private static int hubBagian;
	private static long hubSampai;
	private static boolean siap;

	/** Registers the receiver and the disconnect cleanup. Idempotent. */
	public static void init() {
		if (siap) {
			return;
		}
		siap = true;
		ClientPlayNetworking.registerGlobalReceiver(TempatLayarPayload.TYPE, (payload, context) -> terima(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			hubLayar = null;
			TANPA_PEMBUAT.clear();
		});
		dev.kultivator.client.tempat.ui.layar.SemuaLayar.daftar();
		UiHub.init();
		TeksDunia.init();
	}

	/** Registers the factory of screen {@code layar} (38UI kit screens, 38TV-U theme screens). */
	public static void daftar(String layar, Pembuat pembuat) {
		if (!TempatLayar.idSah(layar) || pembuat == null) {
			throw new IllegalArgumentException("bad tempat screen id or factory: " + layar);
		}
		if (PEMBUAT.put(layar, pembuat) != null) {
			Kultivator.LOGGER.warn("Tempat screen factory {} registered twice; the last one wins", layar);
		}
	}

	public static boolean terdaftar(String layar) {
		return PEMBUAT.containsKey(layar);
	}

	/** Sends op {@code op} for screen {@code layar} / entity {@code npcId}; false when not connected to a mod server. */
	public static boolean kirim(String layar, int npcId, String op) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null || !TempatLayar.idSah(layar) || op == null || op.isEmpty()
				|| !ClientPlayNetworking.canSend(TempatAksiPayload.TYPE)) {
			return false;
		}
		ClientPlayNetworking.send(new TempatAksiPayload(layar, npcId, op));
		return true;
	}

	/**
	 * A hub card asks the server for screen {@code layar} ({@code npcId -1}, read only; the server registered it with
	 * {@code TempatLayar.daftarHub}); the answer opens as a child of hub section {@code bagian} ({@code HubNav.DUNIA},
	 * {@code HubNav.SEKTE}, ...).
	 */
	public static void lihatDariHub(String layar, int bagian) {
		if (kirim(layar, -1, LembarKunci.OP_LIHAT)) {
			hubLayar = layar;
			hubBagian = bagian;
			hubSampai = System.currentTimeMillis() + HUB_TUNGGU_MS;
		}
	}

	/** Call from the screen's {@code removed()}: ends the server session of this screen. */
	public static void ditutup(LayarTempat layar) {
		kirim(layar.layar(), layar.npcId(), LembarKunci.OP_TUTUP);
	}

	private static void terima(TempatLayarPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen current = minecraft.gui.screen();
		if (payload.tutupLayar()) {
			if (current instanceof LayarTempat) {
				minecraft.gui.setScreen(null);
			}
			return;
		}
		if (current instanceof LayarTempat open && open.layar().equals(payload.layar()) && open.npcId() == payload.npcId()) {
			open.perbarui(payload.lembar(), payload.buka());
			return;
		}
		if (!payload.buka()) {
			return;
		}
		Pembuat pembuat = PEMBUAT.get(payload.layar());
		if (pembuat == null) {
			if (TANPA_PEMBUAT.add(payload.layar())) {
				Kultivator.LOGGER.warn("No client screen registered for tempat screen {}", payload.layar());
			}
			return;
		}
		Screen screen;
		try {
			screen = pembuat.buat(payload.npcId(), payload.lembar());
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Tempat screen {} failed to build", payload.layar(), exception);
			return;
		}
		if (screen == null) {
			return;
		}
		boolean dariHub = payload.npcId() < 0 && payload.layar().equals(hubLayar) && System.currentTimeMillis() <= hubSampai;
		if (dariHub) {
			hubLayar = null;
			HubNav.openChild(screen, hubBagian);
		} else {
			minecraft.gui.setScreen(screen);
		}
	}

	private UiTempatClient() {
	}
}
