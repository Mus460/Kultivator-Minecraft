package dev.kultivator.client.sekte.gabung.model;

import dev.kultivator.client.sekte.gabung.JubahSekteLayer;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

/**
 * Client entry of 41V-M's models (DESIGN 41.11.3-41.11.5), called once from {@code GabungClient.init}:
 * <ul>
 * <li>the renderers of the four test entities of {@link GabungEntities}: the straw dummy ({@link BonekaUjianRenderer}),
 * the maze mist and trigrams ({@link DindingIlusiRenderer}), the five-element pillar ({@link PilarQiRenderer}) and the
 * bronze cup ({@link CawanUjianRenderer}), registered whatever the config says (the types are always registered, and a
 * type without a renderer would crash the client the moment a test spawns one);</li>
 * <li>the Lencana Anggota item model hooks ({@link LencanaModel}: rank frame property and sect colour tint);</li>
 * <li>the member robe layer {@link JubahSekteLayer} on every avatar renderer (players, wide and slim).</li>
 * </ul>
 * The item models, prop models 150-156 and every texture are data written by {@code tools/gen_gabung_assets.py}; the
 * props are registered into {@code PropKatalog} by 41V-G's {@code GabungPoses}.
 */
public final class GabungModelClient {

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LencanaModel.register();
		BonekaUjianRenderer.register();
		PilarQiRenderer.register();
		CawanUjianRenderer.register();
		EntityRenderers.register(GabungEntities.BONEKA_UJIAN, BonekaUjianRenderer::new);
		EntityRenderers.register(GabungEntities.DINDING_ILUSI, DindingIlusiRenderer::new);
		EntityRenderers.register(GabungEntities.PILAR_QI, PilarQiRenderer::new);
		EntityRenderers.register(GabungEntities.CAWAN_UJIAN, CawanUjianRenderer::new);
		JubahSekteLayer.daftarLapisan();
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new JubahSekteLayer(avatar, context));
			}
		});
	}

	private GabungModelClient() {
	}
}
