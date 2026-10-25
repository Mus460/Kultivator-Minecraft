package dev.kultivator.client.dunia.kota42;

/**
 * Client init of 42V-F (DESIGN 42.9.2-42.9.3, 42.12.6), called once from {@code KotaHidupClient.init}. Skeleton of 42A0 (wave 42-G0); the owner
 * module fills it with the place atmosphere read from {@code KotaHidupClient.suasana}/{@code titik}: lit lanterns at night, kitchen smoke, banners and spanduk that change with events (囍 / 奠 / new year), firecrackers, soot marks (K7), light spots, shadow-theatre silhouettes, street melodies; all client-only, obeying {@code EffectQuality}/{@code EpicBudget}, never drawn in {@code ShaderCompat.shadowPass()}; toggles {@code kota42.*} in {@code config/kultivator-client.json}.
 */
public final class KotaSuasanaClient {

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
	}

	private KotaSuasanaClient() {
	}
}
