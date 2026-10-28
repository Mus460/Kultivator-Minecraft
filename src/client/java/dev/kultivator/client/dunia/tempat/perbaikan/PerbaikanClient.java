package dev.kultivator.client.dunia.tempat.perbaikan;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.dunia.tempat.perbaikan.PerancahNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;

import java.util.Collection;

/**
 * Client init of 38PR repair (DESIGN 38.R.3): the scaffolding drawn around repair jobs ({@link Perancah}), client
 * only, never blocks. Called once from {@code TempatClient.init()}: the {@code kultivator:perancah} receiver, the client
 * tick (rise and fall of each scaffold, light sampling), one {@code COLLECT_SUBMITS} listener and the clean-up on
 * disconnect. Every visual obeys {@code EffectQuality} (Minimal / Normal / Maximal) and a per-frame quad cap, with no
 * post chain (Sodium and Iris safe, nothing in the shadow pass); client code only, never referenced by server code.
 */
public final class PerbaikanClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(PerancahNet.Payload.TYPE, (payload, context) -> Perancah.onPayload(payload.data()));
		ClientTickEvents.END_CLIENT_TICK.register(Perancah::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(Perancah::render);
		ClientDisconnect.register((handler, client) -> client.execute(() -> Perancah.clear()));
	}

	/** A plot of Kota yang Hidup under construction: the box to put the scaffold round and the building progress 0..1. */
	public record Lahan(long id, BlockPos min, BlockPos max, float progres) {
	}

	/** The plot scaffolds near this player (the full set each call; see {@code KotaHidupClient}); client thread only. */
	public static void perancahKavling(Collection<Lahan> lahan) {
		Perancah.kavling(lahan);
	}

	private PerbaikanClient() {
	}
}
