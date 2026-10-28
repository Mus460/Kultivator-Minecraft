package dev.kultivator.client.dunia.kota42;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.dunia.kota42.cakap.UjaranClient;
import dev.kultivator.client.dunia.kota42.gerak.KotaPoses;
import dev.kultivator.client.dunia.kota42.ragam.PetaPanasDebug;
import dev.kultivator.client.dunia.kota42.render.KotaRenderClient;
import dev.kultivator.client.dunia.kota42.ui.KotaUiClient;
import dev.kultivator.client.dunia.tempat.perbaikan.PerbaikanClient;
import dev.kultivator.dunia.kota42.KotaPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kota yang Hidup on the client (DESIGN 42): the one client entry of the eleven modules, called from
 * {@code KultivatorClient}. It receives the payloads of {@link KotaPayloads} into small caches every 42 client module reads
 * (never a server class beyond the payload records and {@code dunia.kota42.api} constants):
 * <ul>
 * <li>{@link #suasana(long)}: a place's atmosphere (lanterns, banners, soot, street music; 42V-F);</li>
 * <li>{@link #titik(long)}: visual titik per chunk (lanterns, chimneys, banners, gravestone and street names; 42V-F);</li>
 * <li>{@link #upacara()}: running ceremonies (paths and participants; 42V-F fx, 42V-G poses);</li>
 * <li>{@link #kavling()}: plots under construction (scaffolds, plan sign; 42V-M);</li>
 * <li>{@link #kejar()}: the pickpocket chase HUD (42U-1);</li>
 * <li>{@link #ujaran(int)}: the latest spoken line per speaker (speech bubbles of 42K's {@code GelembungUjaran});</li>
 * <li>{@link #petaPanas()}: the op heat map of {@code uji kota_hidup sebaran peta} (42S).</li>
 * </ul>
 * Rhythm beats arrive through {@link #irama()} and go back with {@link #kirimIrama}. Then the client inits of the other
 * modules: {@link KotaUiClient} (42U), {@link KotaSuasanaClient} (42V-F), {@link KotaPoses} (42V-G clips 350-469, props
 * 170-239), {@link KotaRenderClient} (42V-M/42V-H renderers), {@link UjaranClient} (42K bubbles), {@link PetaPanasDebug}
 * (42S). All caches clear on disconnect.
 */
public final class KotaHidupClient {

	/** A spoken line older than this many milliseconds past its duration is dropped. */
	public static final long UJARAN_SISA_MS = 500L;
	/** The chase HUD hides when no update came for this long (the server sends at 4 Hz). */
	public static final long KEJAR_BASI_MS = 1500L;

	/** A received line and the client time it arrived. */
	public record UjaranMasuk(KotaPayloads.Ujaran ujaran, long ms) {

		/** True while the line should still show ({@code durasi} ticks of 50 ms plus a short tail). */
		public boolean hidup(long now) {
			return now - ms <= ujaran.durasi() * 50L + UJARAN_SISA_MS;
		}
	}

	private static final Map<Long, KotaPayloads.Suasana> SUASANA = new ConcurrentHashMap<>();
	private static final Map<Long, KotaPayloads.TitikVisual> TITIK = new ConcurrentHashMap<>();
	private static final Map<Integer, KotaPayloads.Upacara> UPACARA = new ConcurrentHashMap<>();
	private static final Map<Long, KotaPayloads.Kavling> KAVLING = new ConcurrentHashMap<>();
	private static final Map<Integer, UjaranMasuk> UJARAN = new ConcurrentHashMap<>();
	private static volatile @Nullable KotaPayloads.Kejar kejar;
	private static volatile long kejarMs;
	private static volatile @Nullable KotaPayloads.Irama irama;
	private static volatile @Nullable KotaPayloads.PetaPanas petaPanas;
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(KotaPayloads.Suasana.TYPE, (payload, context) -> SUASANA.put(payload.situs(), payload));
		ClientPlayNetworking.registerGlobalReceiver(KotaPayloads.TitikVisual.TYPE, (payload, context) -> TITIK.put(payload.chunk(), payload));
		ClientPlayNetworking.registerGlobalReceiver(KotaPayloads.Upacara.TYPE, (payload, context) -> {
			if (payload.fase() < 0) {
				UPACARA.remove(payload.id());
			} else {
				UPACARA.put(payload.id(), payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(KotaPayloads.Kavling.TYPE, (payload, context) -> {
			KAVLING.put(payload.situs() * 31L + payload.kavling(), payload);
			perancahKavling();
		});
		ClientPlayNetworking.registerGlobalReceiver(KotaPayloads.Kejar.TYPE, (payload, context) -> {
			kejar = payload.copet() < 0 || payload.sisa() <= 0 ? null : payload;
			kejarMs = System.currentTimeMillis();
		});
		ClientPlayNetworking.registerGlobalReceiver(KotaPayloads.Irama.TYPE, (payload, context) -> irama = payload);
		ClientPlayNetworking.registerGlobalReceiver(KotaPayloads.Ujaran.TYPE, (payload, context) ->
				UJARAN.put(payload.pembicara(), new UjaranMasuk(payload, System.currentTimeMillis())));
		ClientPlayNetworking.registerGlobalReceiver(KotaPayloads.PetaPanas.TYPE, (payload, context) -> petaPanas = payload);
		ClientDisconnect.register((handler, client) -> hapus());
		// 42U Layar (screens, hub cards, HUDs):
		KotaUiClient.init();
		// 42V-F atmosphere and fx (lanterns, smoke, banners, firecrackers, soot, shadow theatre, street music):
		KotaSuasanaClient.init();
		// 42V-G clips 350-469 and props 170-239:
		KotaPoses.init();
		// 42V-M / 42V-H entity and block renderers:
		KotaRenderClient.init();
		// 42K speech bubbles of NPC-NPC chats:
		UjaranClient.init();
		// 42S debug heat map:
		PetaPanasDebug.init();
	}

	/** Stages 2-4 of a plot (frame, walls, roof) stand in a scaffold that rises with the stage (42.9.4). */
	private static void perancahKavling() {
		java.util.List<PerbaikanClient.Lahan> lahan = new java.util.ArrayList<>();
		for (KotaPayloads.Kavling kv : KAVLING.values()) {
			if (kv.tahap() < 2 || kv.tahap() > 4 || kv.kotak().length < 6) {
				continue;
			}
			net.minecraft.core.BlockPos a = net.minecraft.core.BlockPos.of(kv.asal());
			short[] k = kv.kotak();
			int tinggi = Math.min(k[4], 4 + 4 * kv.tahap());
			lahan.add(new PerbaikanClient.Lahan(kv.situs() * 31L + kv.kavling(), a.offset(k[0], k[1], k[2]),
					a.offset(k[3], tinggi, k[5]), (kv.tahap() - 1) / 4.0F));
		}
		PerbaikanClient.perancahKavling(lahan);
	}

	// ================================================================== caches

	public static @Nullable KotaPayloads.Suasana suasana(long situs) {
		return SUASANA.get(situs);
	}

	public static Collection<KotaPayloads.Suasana> semuaSuasana() {
		return Collections.unmodifiableCollection(SUASANA.values());
	}

	/** The visual titik of chunk {@code chunk} ({@code ChunkPos.toLong}), or null when not sent yet. */
	public static @Nullable KotaPayloads.TitikVisual titik(long chunk) {
		return TITIK.get(chunk);
	}

	public static Collection<KotaPayloads.TitikVisual> semuaTitik() {
		return Collections.unmodifiableCollection(TITIK.values());
	}

	public static Collection<KotaPayloads.Upacara> upacara() {
		return Collections.unmodifiableCollection(UPACARA.values());
	}

	public static Collection<KotaPayloads.Kavling> kavling() {
		return Collections.unmodifiableCollection(KAVLING.values());
	}

	/** The chase HUD, or null when none runs (or the last update is stale). */
	public static @Nullable KotaPayloads.Kejar kejar() {
		KotaPayloads.Kejar k = kejar;
		return k != null && System.currentTimeMillis() - kejarMs <= KEJAR_BASI_MS ? k : null;
	}

	public static @Nullable KotaPayloads.Irama irama() {
		return irama;
	}

	/** The line {@code pembicara} is saying now, or null. */
	public static @Nullable UjaranMasuk ujaran(int pembicara) {
		UjaranMasuk u = UJARAN.get(pembicara);
		if (u != null && !u.hidup(System.currentTimeMillis())) {
			UJARAN.remove(pembicara, u);
			return null;
		}
		return u;
	}

	public static @Nullable KotaPayloads.PetaPanas petaPanas() {
		return petaPanas;
	}

	/** Sends one rhythm beat to the server (dropped when the server has no 42). */
	public static void kirimIrama(KotaPayloads.Irama beat) {
		if (ClientPlayNetworking.canSend(KotaPayloads.Irama.TYPE)) {
			ClientPlayNetworking.send(beat);
		}
	}

	private static void hapus() {
		SUASANA.clear();
		TITIK.clear();
		UPACARA.clear();
		KAVLING.clear();
		UJARAN.clear();
		kejar = null;
		irama = null;
		petaPanas = null;
	}

	private KotaHidupClient() {
	}
}
