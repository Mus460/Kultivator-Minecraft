package dev.kultivator.client.dunia.kota;

import dev.kultivator.dunia.kota.KotaEntities;
import dev.kultivator.dunia.kota.KotaNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;

/**
 * The client side of module K (DESIGN 34.5.1-34.5.4): the NPC and door renderers, the town views (Kota, Kota Abadi, the
 * wanderer meeting) fed by {@link KotaNet.Screen} and shown as one Di Sini page of the Gulungan Utama through
 * {@link KotaJembatan} (DESIGN 43.8.4), the world visuals of {@link KotaFx}, and the action sender. Module K has no hub
 * card any more (43.11 G03, R5): Kota Abadi lives in the world, the rival list is Dunia › Musuh › Saingan.
 * Wired by one line in {@code DuniaContentClient}.
 */
public final class KotaClient {

	private static CompoundTag data = new CompoundTag();
	private static int npcId = -1;
	private static int screenKind = -1;
	private static long version;
	private static long received;

	public static void init() {
		KotaGear.register();
		EntityRenderers.register(KotaEntities.NPC, NpcRenderer::new);
		EntityRenderers.register(KotaEntities.PINTU, PintuRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(KotaNet.Screen.TYPE, (payload, context) -> onScreen(payload));
		ClientPlayNetworking.registerGlobalReceiver(KotaNet.Fx.TYPE, (payload, context) -> KotaFx.onFx(payload));
		KotaFx.init();
		KotaLelang.init();
		ClientTickEvents.END_CLIENT_TICK.register(KotaFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = new CompoundTag();
			npcId = -1;
			screenKind = -1;
			KotaLelang.reset();
			KotaFx.clear();
			NpcPoses.clear();
		});
	}

	private static void onScreen(KotaNet.Screen payload) {
		if (payload.screen() == SainganScreen.SCREEN) {
			// the rival list of Dunia › Musuh (no NPC): not a town view
			SainganScreen.onData(payload.open(), payload.data() == null ? new CompoundTag() : payload.data());
			return;
		}
		KotaJembatan.terima(payload);
	}

	/** Keeps the data of the shown K page (called by {@link KotaJembatan} for payloads it accepts). */
	static void simpan(KotaNet.Screen payload) {
		data = payload.data() == null ? new CompoundTag() : payload.data();
		npcId = payload.npcId();
		screenKind = payload.screen();
		version++;
		received = Util.getMillis();
	}

	/** The last data of the shown page (never null). */
	public static CompoundTag data() {
		return data;
	}

	/** One tab's part of the data (empty when absent). */
	public static CompoundTag tab(String key) {
		return data.getCompoundOrEmpty(key);
	}

	public static int npcId() {
		return npcId;
	}

	public static int screenKind() {
		return screenKind;
	}

	/** Bumped on every screen data that arrived. */
	public static long version() {
		return version;
	}

	/** When the last data arrived (ms, {@code Util.getMillis}); countdowns run from here. */
	public static long received() {
		return received;
	}

	/** Sends a screen action about the open NPC. */
	public static void send(String op) {
		if (npcId >= 0) {
			ClientPlayNetworking.send(new KotaNet.Action(npcId, op));
		}
	}

	private KotaClient() {
	}
}
