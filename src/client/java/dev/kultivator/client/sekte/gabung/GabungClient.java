package dev.kultivator.client.sekte.gabung;

import dev.kultivator.client.sekte.gabung.fx.GabungFxClient;
import dev.kultivator.client.sekte.gabung.gerak.GabungPoses;
import dev.kultivator.client.sekte.gabung.model.GabungModelClient;
import dev.kultivator.client.sekte.gabung.ui.GabungUiClient;
import dev.kultivator.sekte.gabung.GabungPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gabung Sekte on the client (DESIGN 41): the one client entry of the seven modules, called from
 * {@code SekteContentClient.init}. It receives the four payloads of {@link GabungPayloads} into small caches that every
 * client module of 41 reads (never a server class beyond the payload records and {@code sekte.gabung.api} constants):
 * <ul>
 * <li>{@link #lencana}: other players' (and the own) sect badge: sect, rank, ink colour, emblem, robe shown — the member
 * robe layer and rank name plate of 41V;</li>
 * <li>{@link #kelas}: the known sects' class, tier, alignment, style, colour, MEROSOT — directory, class visuals;</li>
 * <li>{@link #ujian}: the running entrance test HUD (41U);</li>
 * <li>{@link #klip}: players posed with a 41 clip (the {@code PlayerModelMixin} hook of 41V-G).</li>
 * </ul>
 * Then the client inits of the other modules: {@link GabungUiClient} (41U screens, hub card, test HUD),
 * {@link GabungFxClient} (41V class visuals, illusion stair, test effects), {@link GabungPoses} (41V clips 300-349 and
 * props 150-169) and {@link GabungModelClient} (41V entity renderers and the member robe layer). All caches clear on
 * disconnect.
 */
public final class GabungClient {

	/** Test HUD data older than this many milliseconds is treated as ended (the server sends every 5 ticks). */
	public static final long UJIAN_BASI_MS = 3000L;

	private static final Map<UUID, GabungPayloads.EntriLencana> LENCANA = new ConcurrentHashMap<>();
	private static final Map<String, GabungPayloads.EntriKelas> KELAS = new ConcurrentHashMap<>();
	private static final Map<UUID, GabungPayloads.Klip> KLIP = new ConcurrentHashMap<>();
	private static volatile @Nullable GabungPayloads.Ujian ujian;
	private static volatile long ujianMs;
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(GabungPayloads.Lencana.TYPE, (payload, context) -> {
			for (GabungPayloads.EntriLencana e : payload.entri()) {
				if (e.sekte().isEmpty()) {
					LENCANA.remove(e.pemain());
				} else {
					LENCANA.put(e.pemain(), e);
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(GabungPayloads.Kelas.TYPE, (payload, context) -> {
			for (GabungPayloads.EntriKelas e : payload.entri()) {
				KELAS.put(e.sekte(), e);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(GabungPayloads.Ujian.TYPE, (payload, context) -> {
			ujian = payload.tahap() <= 0 ? null : payload;
			ujianMs = System.currentTimeMillis();
		});
		ClientPlayNetworking.registerGlobalReceiver(GabungPayloads.Klip.TYPE, (payload, context) -> {
			if (payload.klip() <= 0) {
				KLIP.remove(payload.pemain());
			} else {
				KLIP.put(payload.pemain(), payload);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hapus());
		// 41U Layar:
		GabungUiClient.init();
		// 41V Visual (class visuals, illusion stair, test effects):
		GabungFxClient.init();
		// 41V-G clips 300-349, props 150-169:
		GabungPoses.init();
		// 41V-M entity renderers, item models, the member robe layer:
		GabungModelClient.init();
	}

	/** The sect badge of player {@code pemain}, or null when not a member (or not in sync range). */
	public static @Nullable GabungPayloads.EntriLencana lencana(UUID pemain) {
		return pemain == null ? null : LENCANA.get(pemain);
	}

	/** The local player's own badge, or null. */
	public static @Nullable GabungPayloads.EntriLencana lencanaSendiri() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null ? null : LENCANA.get(minecraft.player.getUUID());
	}

	/** The class line of known sect {@code sekte}, or null. */
	public static @Nullable GabungPayloads.EntriKelas kelas(String sekte) {
		return sekte == null ? null : KELAS.get(sekte);
	}

	/** Every known sect's class line. */
	public static Collection<GabungPayloads.EntriKelas> semuaKelas() {
		return Collections.unmodifiableCollection(KELAS.values());
	}

	/** The running entrance test's HUD state, or null when none (or the last update is stale). */
	public static @Nullable GabungPayloads.Ujian ujian() {
		GabungPayloads.Ujian u = ujian;
		return u != null && System.currentTimeMillis() - ujianMs <= UJIAN_BASI_MS ? u : null;
	}

	/** The 41 clip player {@code pemain} is posed with, or null. */
	public static @Nullable GabungPayloads.Klip klip(UUID pemain) {
		return pemain == null ? null : KLIP.get(pemain);
	}

	private static void hapus() {
		LENCANA.clear();
		KELAS.clear();
		KLIP.clear();
		ujian = null;
	}

	private GabungClient() {
	}
}
