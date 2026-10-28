package dev.kultivator.client.dunia.tempat.tema;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.dunia.tempat.tema.fx.TemaFx;
import dev.kultivator.client.dunia.tempat.tema.model.TemaModelClient;
import dev.kultivator.client.tempat.ui.tema.TemaUiClient;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client entry of the themed towns (DESIGN 38.T, module 38TV), called once by {@code TempatClient.init()} (38W): the
 * receiver of {@link TemaNet.TemaFxPayload} ({@code kultivator:tema_fx}, contract C38.T4) and the inits of the three
 * 38TV parts: {@link TemaFx} (38TV-F: the effects of 38.T.9.4, the HUD tints and banners, the {@code /temafx} preview),
 * {@link TemaModelClient} (38TV-M: entity renderers, item models), {@link TemaUiClient} (38TV-U: the {@code tema_*}
 * screens on the 38UI kit), and the hub card {@link TemaHub} "Kota Bertema".
 * <p>
 * Every payload goes to {@link TemaFx#terima} (sandstorm, festivals, lanes, light phenomena) and its latest copy per
 * {@code (jenis, situsId)} is kept for other readers ({@link #terakhir}; the harbour ship schedule
 * {@code KAPAL_JADWAL} is only data). Everything is forgotten on disconnect. Every effect obeys {@code EffectQuality}
 * (Minimal / Normal / Maximal) and {@code EpicBudget}; no post chain (Sodium/Iris safe); the "heavy" look is layered
 * additive quads, glow textures and HUD overlays; the world's weather, time and blocks are only ever read.
 */
public final class TemaClient {

	private static final int MAKS_SIMPAN = 256;
	private static final Map<Long, CompoundTag> TERAKHIR = new LinkedHashMap<>(64, 0.75F, true);
	private static boolean ready;

	/** From {@code TempatClient.init()}. Safe to call twice. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ClientPlayNetworking.registerGlobalReceiver(TemaNet.TemaFxPayload.TYPE, (payload, context) -> terima(payload));
		ClientDisconnect.register((handler, client) -> client.execute(() -> TERAKHIR.clear()));
		TemaFx.init();
		TemaModelClient.init();
		TemaUiClient.init();
		TemaHub.init();
	}

	/** The latest data of kind {@code jenis} ({@code TemaNet.BADAI} ...) for town {@code situsId}, or null. */
	public static @Nullable CompoundTag terakhir(int jenis, long situsId) {
		return TERAKHIR.get(kunci(jenis, situsId));
	}

	private static void terima(TemaNet.TemaFxPayload payload) {
		CompoundTag data = payload.data() == null ? new CompoundTag() : payload.data();
		TERAKHIR.put(kunci(payload.jenis(), payload.situsId()), data);
		if (TERAKHIR.size() > MAKS_SIMPAN) {
			// drop the least recently used entry (access order)
			Long eldest = TERAKHIR.keySet().iterator().next();
			TERAKHIR.remove(eldest);
		}
		TemaFx.terima(payload.jenis(), payload.situsId(), data);
	}

	private static long kunci(int jenis, long situsId) {
		return situsId * 31L + jenis;
	}

	private TemaClient() {
	}
}
