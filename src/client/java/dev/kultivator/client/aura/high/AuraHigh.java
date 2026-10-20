package dev.kultivator.client.aura.high;

import dev.kultivator.client.aura.AuraEngine;
import dev.kultivator.data.Realm;

/**
 * Module H of the Aura Kultivasi (DESIGN 31.4.6-31.4.9, 31.7): the auras of the four high realms, registered with the
 * engine from {@code AuraClient.init()}.
 * <ul>
 * <li>{@link DeityAura}: the Dharma Idol and the divine-sense ripples (Transformasi Dewa);</li>
 * <li>{@link DaoUnionAura}: the orbiting Dao runes, the yin-yang comets, the taiji and the five elements (Penyatuan Dao);</li>
 * <li>{@link AscensionAura}: the Immortal Gate, its light, the purple qi from the east and the falling flowers, growing
 * with the nine layers (Kenaikan Abadi);</li>
 * <li>{@link TrueImmortalAura}: the star wheel, the domain of radiance and the nine-grade lotus throne (Abadi Sejati).</li>
 * </ul>
 * Quad textures are {@code textures/misc/aura_high/*}; props are {@code kultivator:fx/aura_dt_*, aura_du_*, aura_ia_*,
 * aura_ti_*} (no FxModels row: they use the default info and each driver passes its own scale). Everything is made by
 * {@code tools/gen_aura_high_assets.py}; the text keys are {@code lang-src/aura_high.*}.
 */
public final class AuraHigh {

	private static boolean done;

	/** Registers the drivers of realms 6-9 (once). */
	public static void init() {
		if (done) {
			return;
		}
		done = true;
		AuraEngine.register(Realm.DEITY_TRANSFORMATION, new DeityAura());
		AuraEngine.register(Realm.DAO_UNION, new DaoUnionAura());
		AuraEngine.register(Realm.IMMORTAL_ASCENSION, new AscensionAura());
		AuraEngine.register(Realm.TRUE_IMMORTAL, new TrueImmortalAura());
	}

	private AuraHigh() {
	}
}
