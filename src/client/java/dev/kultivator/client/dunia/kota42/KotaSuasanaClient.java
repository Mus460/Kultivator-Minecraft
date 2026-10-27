package dev.kultivator.client.dunia.kota42;

import dev.kultivator.client.dunia.kota42.fx.SuasanaFx;

/**
 * Client init of 42V-F (DESIGN 42.9.2-42.9.3, 42.12.6), called once from {@code KotaHidupClient.init}. It starts
 * {@link SuasanaFx}, which reads the place atmosphere and titik of {@code KotaHidupClient.suasana}/{@code titik}/{@code upacara}
 * and draws and plays the town's night and festival dressing: lit lanterns with halos and pools of light, kitchen smoke and steam,
 * banners and street bunting and door papers that change with events (囍 / 奠 / New Year couplets / the protector's emblem),
 * firecrackers, soot marks (K7), petals, paper and leaves, pigeon whistles, street music (erhu, suona, pipa, dizi, gong,
 * drums) and the engraved names. All client-only, obeying {@code EffectQuality}/{@code EpicBudget}, never drawn in
 * {@code ShaderCompat.shadowPass()}; switches {@code kota42.*} in {@code config/kultivator-client.json} (Pengaturan).
 */
public final class KotaSuasanaClient {

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		SuasanaFx.init();
	}

	private KotaSuasanaClient() {
	}
}
