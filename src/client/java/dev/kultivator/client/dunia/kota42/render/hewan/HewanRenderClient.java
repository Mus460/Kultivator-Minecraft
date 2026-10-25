package dev.kultivator.client.dunia.kota42.render.hewan;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.kota42.model.hewan.HewanMesh;
import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import dev.kultivator.dunia.kota42.hewan.HewanEntities;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * Client init of 42V-H (DESIGN 42.12.4 animals), called once from {@code KotaRenderClient.init}: the model layers of the
 * eight generated meshes ({@link HewanMesh}, made with their atlases by {@code tools/gen_kota_hidup_hewan.py}) and the
 * renderers of the seven town animals and the lion dance: {@link KucingRenderer}, {@link AnjingRenderer},
 * {@link AyamRenderer}, {@link MerpatiRenderer} (the client boid flock), {@link KudaRenderer}, {@link UntaRenderer},
 * {@link KeledaiRenderer}, {@link BarongsaiRenderer}. Full animation within 32 blocks, the clock in whole ticks to 64, the
 * base pose beyond (42.13.3). Renderers are registered whatever the config says (the types are always registered and a
 * type without a renderer would crash the client the moment it spawns). Client only.
 */
public final class HewanRenderClient {

	public static final ModelLayerLocation KUCING = lapis("kucing");
	public static final ModelLayerLocation ANJING = lapis("anjing");
	public static final ModelLayerLocation AYAM = lapis("ayam");
	public static final ModelLayerLocation MERPATI = lapis("merpati");
	public static final ModelLayerLocation KUDA = lapis("kuda");
	public static final ModelLayerLocation KELEDAI = lapis("keledai");
	public static final ModelLayerLocation UNTA = lapis("unta");
	public static final ModelLayerLocation BARONGSAI = lapis("barongsai");

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		daftar(KUCING, HewanMesh::kucing);
		daftar(ANJING, HewanMesh::anjing);
		daftar(AYAM, HewanMesh::ayam);
		daftar(MERPATI, HewanMesh::merpati);
		daftar(KUDA, HewanMesh::kuda);
		daftar(KELEDAI, HewanMesh::keledai);
		daftar(UNTA, HewanMesh::unta);
		daftar(BARONGSAI, HewanMesh::barongsai);
		EntityRenderers.register(HewanEntities.KUCING_KOTA, KucingRenderer::new);
		EntityRenderers.register(HewanEntities.ANJING_KOTA, AnjingRenderer::new);
		EntityRenderers.register(HewanEntities.AYAM_KOTA, AyamRenderer::new);
		EntityRenderers.register(HewanEntities.KAWANAN_MERPATI, MerpatiRenderer::new);
		EntityRenderers.register(HewanEntities.KUDA_TAMBAT, KudaRenderer::new);
		EntityRenderers.register(HewanEntities.UNTA_TAMBAT, UntaRenderer::new);
		EntityRenderers.register(HewanEntities.KELEDAI_KOTA, KeledaiRenderer::new);
		EntityRenderers.register(KotaAcaraEntities.BARONGSAI, BarongsaiRenderer::new);
	}

	private static ModelLayerLocation lapis(String mesh) {
		return new ModelLayerLocation(Kultivator.id("kota42_hewan_" + mesh), "main");
	}

	private static void daftar(ModelLayerLocation layer, Supplier<LayerDefinition> mesh) {
		ModelLayerRegistry.registerModelLayer(layer, mesh::get);
	}

	/** {@code textures/entity/kota42/hewan/<mesh>_<look>.png} for every look, in variant order. */
	static Identifier[] tekstur(String mesh, String[] looks) {
		Identifier[] out = new Identifier[looks.length];
		for (int i = 0; i < looks.length; i++) {
			out[i] = Kultivator.id("textures/entity/kota42/hewan/" + mesh + "_" + looks[i] + ".png");
		}
		return out;
	}

	private HewanRenderClient() {
	}
}
