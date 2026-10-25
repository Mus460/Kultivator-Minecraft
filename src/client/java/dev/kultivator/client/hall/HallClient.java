package dev.kultivator.client.hall;

import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.client.screen.ui.Kronik;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.Halls;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client side of the Sect Hall (DESIGN 20.3.1, 43.16.2 M05): keeps the last {@code HallInfo}, registers the page
 * Sekte › Ikhtisar ({@link IkhtisarPage}), routes the server's {@code OpenScreen.HALL} to the Gulungan
 * ({@code HubNav.tujuanAula}), ticks {@link HallFx} and sends the hall's actions ({@code Actions.HALL} with a sub-op of
 * {@code Halls.OP_*}). While the Gulungan shows the Sekte section the details are asked for every 4 s (43.0.12: only while
 * shown, the old interval of the hall scroll), so every Sekte page reads them live from {@link #info()}; when the
 * Gulungan closes the server stops treating us as a viewer ({@code OP_CLOSE}).
 * <p>
 * Two small registries for other modules (43.11 G37, G44): {@link #sumberCatatan} adds a chronicle to Sekte › Ikhtisar ›
 * Catatan (kabar murid, langit, diplomasi, formasi), {@link #papanMandiri} hands over the {@code mandiri_papan} data whose
 * hall policy is edited in Sekte › Ikhtisar › Identitas.
 */
public final class HallClient {

	/** The Sekte pages ask for fresh details this often while shown (countdowns, arrivals, the inventory's offers). */
	private static final long REFRESH_MILLIS = 4000L;

	/** A chronicle of another module shown in Catatan: kind id (filter chip), its name, and its entries (oldest first). */
	public record SumberCatatan(String jenis, Component nama, Supplier<List<Kronik.Entri>> entri) {
	}

	private static final List<SumberCatatan> SUMBER = new ArrayList<>();
	private static CompoundTag info = new CompoundTag();
	private static long version;
	private static long receivedAt;
	private static long askedAt = Long.MIN_VALUE / 2;
	/** True after we asked the server while the Gulungan was open (it may hold us as a viewer of a hall). */
	private static boolean viewing;
	private static @Nullable CompoundTag papan;
	private static long papanVersi;

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(HallFx::tick);
		ClientTickEvents.END_CLIENT_TICK.register(HallClient::tick);
		HubPages.daftar(HubNav.SEKTE, Urutan.ID_IKHTISAR, Urutan.IKHTISAR, IkhtisarPage::new);
		// a gabung_kartu sheet from the world (command, directory) lands on Sekte › Ikhtisar, on its own tab
		HubPages.daftarRumah(dev.kultivator.sekte.gabung.ui.GabungLembar.Kartu.LAYAR, IkhtisarPage.TAB_KARTU, HubNav.SEKTE, Urutan.ID_IKHTISAR, "", null);
	}

	/** New details of the hall arrived (the shown pages pick them up by {@link #version()}). */
	public static void onInfo(Payloads.HallInfo payload) {
		info = payload.data() == null ? new CompoundTag() : payload.data();
		version++;
		receivedAt = Util.getMillis();
	}

	/**
	 * The server asks to show the hall on page {@code arg} ({@code Halls.PAGE_*}: 0 Aula, 1 Bangun, 2 Calon, ...): the
	 * Gulungan opens on that page (43.12.3). Also the old opener of {@code HubNav.cadangan}.
	 */
	public static void openScreen(int arg) {
		HubNav.buka(HubNav.tujuanAula(arg));
	}

	/** Asks for fresh details now unless that was done in the last second (pages call it in {@code onShow}). */
	public static void minta() {
		long now = Util.getMillis();
		if (now - askedAt >= 1000L) {
			askedAt = now;
			viewing = true;
			send(Halls.OP_REFRESH, "");
		}
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		if (minecraft.gui.screen() instanceof CultivationScreen hub) {
			if (hub.section() == HubNav.SEKTE && Util.getMillis() - askedAt >= REFRESH_MILLIS) {
				minta();
			}
		} else if (viewing) {
			// The Gulungan closed (or a container screen replaced it): end the viewer session of someone else's hall.
			viewing = false;
			send(Halls.OP_CLOSE, "");
		}
	}

	/** The last hall details (never null). */
	public static CompoundTag info() {
		return info;
	}

	/** The hall's name in {@code info} ("Sekte &lt;owner&gt;" when unnamed), else the hall title. */
	public static Component hallName(CompoundTag info) {
		Component shown = info.read("name_shown", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(null);
		if (shown != null) {
			return shown;
		}
		String name = info.getStringOr("name", "");
		return name.isEmpty() ? Component.translatable("kultivator.spine6.hall.title") : Component.literal(name);
	}

	/** Grows with every HallInfo. */
	public static long version() {
		return version;
	}

	/** When the last HallInfo arrived (milliseconds, for countdowns). */
	public static long receivedAt() {
		return receivedAt;
	}

	/** True when the details name a hall (own, or the one viewed read-only). */
	public static boolean adaAula() {
		return info.getBooleanOr("has", false);
	}

	/** True when the details are of the player's own hall (editable). */
	public static boolean aulaSendiri() {
		return adaAula() && !info.getBooleanOr("readonly", false);
	}

	/** Ticks of a server countdown from the last HallInfo that are still left now. */
	public static long ticksLeft(long ticksThen) {
		return Math.max(0L, ticksThen - (Util.getMillis() - receivedAt) / 50L);
	}

	/** Sends a hall action ({@code Halls.OP_*}) with its text parameter. */
	public static void send(int op, String text) {
		Keys.send(new Payloads.Action(Actions.HALL, op, text == null ? "" : text));
	}

	// ================================================================== registries for other modules

	/**
	 * Adds chronicle {@code jenis} to Sekte › Ikhtisar › Catatan (one {@code Kronik} with a filter chip per kind; G37). A
	 * second call with the same kind replaces the first. Client thread; call from the module's client init.
	 */
	public static void sumberCatatan(String jenis, Component nama, Supplier<List<Kronik.Entri>> entri) {
		if (jenis == null || jenis.isEmpty() || nama == null || entri == null) {
			return;
		}
		SUMBER.removeIf(s -> s.jenis().equals(jenis));
		SUMBER.add(new SumberCatatan(jenis, nama, entri));
	}

	/** The chronicles of other modules (a copy). */
	public static List<SumberCatatan> sumberCatatan() {
		return List.copyOf(SUMBER);
	}

	/** The newest {@code mandiri_papan} data (hall policy "kb", flag "own"), handed over by the module that receives it. */
	public static void papanMandiri(@Nullable CompoundTag data) {
		papan = data;
		papanVersi++;
	}

	/** The last {@code mandiri_papan} data, or null before the first. */
	public static @Nullable CompoundTag papanMandiri() {
		return papan;
	}

	public static long papanVersi() {
		return papanVersi;
	}

	private static void clear() {
		info = new CompoundTag();
		version = 0L;
		viewing = false;
		askedAt = Long.MIN_VALUE / 2;
		papan = null;
		papanVersi++;
	}

	private HallClient() {
	}
}
