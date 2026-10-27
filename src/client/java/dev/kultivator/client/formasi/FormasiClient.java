package dev.kultivator.client.formasi;

import dev.kultivator.Kultivator;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client entry of the sect formations (DESIGN 24.13 item 5, owned by P7c-katalog-ui): keeps the last {@code FormasiSync}
 * (cores within 96 blocks and the conceal list), feeds the concealed-name set, the formation visuals and the hologram, and
 * registers the alarm HUD and the Sekte › Formasi page (DESIGN 43). Nothing here is saved.
 */
public final class FormasiClient {

	/** One synced core: eye, rotation, radius, state ordinal, whether we are a member, and the handler's extra fields. */
	public record SyncCore(int id, int net, BlockPos eye, int rot, double radius, int state, boolean member, CompoundTag ext) {
		public @Nullable FormationSpec spec() {
			return FormationRegistry.byNet(net);
		}

		/** Berdaya or Dijaga (the handler pulses). */
		public boolean running() {
			return state == 4 || state == 5;
		}
	}

	private static CompoundTag last = new CompoundTag();
	private static List<SyncCore> cores = List.of();
	private static int pendingNodeMap = -1;
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			last = new CompoundTag();
			cores = List.of();
			pendingNodeMap = -1;
			ConcealClient.clear();
			PatternHologram.hide();
			AlarmHud.clear();
			FormasiFx.clear();
		}));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			PatternHologram.tick(minecraft);
			AlarmHud.tick(minecraft);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("formasi_alarm"), new AlarmHud());
		// Sekte › Formasi of the Gulungan Utama (DESIGN 43.5, 43.12.2).
		HubPages.daftar(HubNav.SEKTE, Urutan.ID_FORMASI, Urutan.FORMASI, FormasiPage::new);
		// The formation log in the one chronicle of Sekte › Ikhtisar › Catatan (43.11 G37).
		HallClient.sumberCatatan("formasi", Component.translatable("kultivator.spine7c.tablet"),
				() -> LogPanel.entri(HallClient.info().getCompoundOrEmpty("formasi")));
	}

	/** A {@code FormasiSync} arrived (cores within 96 blocks and the conceal list). */
	public static void onSync(CompoundTag data) {
		last = data == null ? new CompoundTag() : data;
		ConcealClient.update(last);
		List<SyncCore> list = new ArrayList<>();
		for (Tag tag : last.getListOrEmpty("cores")) {
			if (tag instanceof CompoundTag core) {
				list.add(new SyncCore(core.getIntOr("id", -1), core.getIntOr("net", -1),
						new BlockPos(core.getIntOr("x", 0), core.getIntOr("y", 0), core.getIntOr("z", 0)), core.getIntOr("rot", 0),
						core.getDoubleOr("r", 8.0D), core.getIntOr("st", 0), core.getBooleanOr("mem", false), core.getCompoundOrEmpty("ext")));
			}
		}
		cores = Collections.unmodifiableList(list);
	}

	/** The last sync (never null). */
	public static CompoundTag last() {
		return last;
	}

	/** The synced cores near us (never null). */
	public static List<SyncCore> cores() {
		return cores;
	}

	/** The synced core whose eye is at {@code pos}, or null. */
	public static @Nullable SyncCore coreAt(BlockPos pos) {
		for (SyncCore core : cores) {
			if (core.eye().equals(pos)) {
				return core;
			}
		}
		return null;
	}

	/** The server asked to open the node map of core {@code id} (Sekte › Formasi follows and takes it). */
	static void requestNodeMap(int id) {
		pendingNodeMap = id;
		// The Gulungan the server opens next lands on the Simpul sub, where the map is that sub's detail view.
		HubNav.ingatSub(Urutan.ID_FORMASI, "simpul");
	}

	/** Takes the pending node-map request (−1 when none). */
	static int takeNodeMap() {
		int id = pendingNodeMap;
		pendingNodeMap = -1;
		return id;
	}

	/** Sends one {@code Actions.FORMASI} text op ({@code toggle:<id>}, {@code travel:<from>:<to>} ...). */
	public static void send(String text) {
		Keys.send(new Payloads.Action(Actions.FORMASI, 0, text == null ? "" : text));
	}

	private FormasiClient() {
	}
}
