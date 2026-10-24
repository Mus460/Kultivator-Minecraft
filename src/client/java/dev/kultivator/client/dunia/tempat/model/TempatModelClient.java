package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.dunia.tempat.ekonomi.EkonomiEntities;
import dev.kultivator.dunia.tempat.gua.GuaEntities;
import dev.kultivator.dunia.tempat.jalan.JalanEntities;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * Client init of 38MD models and textures (DESIGN 38.O): the entity models and renderers of every 38 creature and
 * mechanism (38.O.2), the festival and world decorations (38.O.3, {@link HiasanDunia}), the weapon racks
 * ({@link RakSenjata}), the model showcase ({@link PamerModel}) and the client test command {@code /hiasan}
 * ({@link HiasPerintah}). The hand props
 * {@code kultivator:fx/tempat/<id>} (38.O.4, PropId 1-40) and the city weapons {@code kultivator:senjata/<form>_<grade>}
 * are item models only (tools/gen_tempat_models.py); they need no code here. Called once from
 * {@code TempatClient.init()}. Every visual obeys {@code EffectQuality} (Minimal / Normal / Maximal), draws nothing far
 * away, and uses vanilla render types only (no post chain: Sodium and Iris safe). Client code only.
 *
 * <p>Owners of the entities set the animation readers of {@link KaitModel} from their own client init; 38UI registers
 * {@link PapanHidupRenderer} for its notice-board entity type. Owners of places call {@link HiasanDunia#pasang} /
 * {@link HiasanDunia#festival} and {@link RakSenjata#pasang} when their sync arrives.
 */
public final class TempatModelClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		PenjagaKristalRenderer.register();
		BonekaPenjagaRenderer.register();
		TikusRohRenderer.register();
		SangkarRenderer.register();
		LempengGlifRenderer.register();
		PintuTebingRenderer.register();
		GerbangKotaRenderer.register();
		GerobakRenderer.register();
		RakitRenderer.register();
		PapanHidupRenderer.register();
		EntityRenderers.register(GuaEntities.PENJAGA_KRISTAL, PenjagaKristalRenderer::new);
		EntityRenderers.register(GuaEntities.BONEKA_PENJAGA, BonekaPenjagaRenderer::new);
		EntityRenderers.register(GuaEntities.TIKUS_ROH, TikusRohRenderer::new);
		EntityRenderers.register(GuaEntities.SANGKAR, SangkarRenderer::new);
		EntityRenderers.register(GuaEntities.LEMPENG_GLIF, LempengGlifRenderer::new);
		EntityRenderers.register(GuaEntities.PINTU_TEBING, PintuTebingRenderer::new);
		EntityRenderers.register(KotaBesarEntities.GERBANG_KOTA, GerbangKotaRenderer::new);
		EntityRenderers.register(EkonomiEntities.GEROBAK, GerobakRenderer::new);
		EntityRenderers.register(JalanEntities.RAKIT, RakitRenderer::new);
		HiasanDunia.init();
		RakSenjata.init();
		PamerModel.init();
		HiasPerintah.init();
	}

	private TempatModelClient() {
	}
}
