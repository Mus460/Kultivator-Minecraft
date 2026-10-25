package dev.kultivator.client.dunia.kota42.render;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.kota42.model.KotaMesh;
import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiBlocks;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiEntities;
import dev.kultivator.dunia.kota42.sekte.KotaSekteEntities;
import dev.kultivator.dunia.kota42.tumbuh.KotaTumbuhEntities;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * Client init of 42V-M (DESIGN 42.12.4), called once from {@code KotaHidupClient.init}: the model layers of every
 * generated mesh ({@link KotaMesh}), the renderers of the twelve non-animal 42 entity types (market and festival tents,
 * Ghost Market stall, travelling stage, small fire, spilled goods, coffin bier, banquet table, dice table, shadow screen,
 * root-testing stone, refugee tent) and the three block entity renderers (shop rack items and prices, shop sign name,
 * sect branch emblem), then {@code render.hewan.HewanRenderClient.init()} (42V-H: the seven animals and the lion dance).
 * Renderers are registered whatever the config says: the types are always registered and a type without a renderer
 * would crash the client the moment it spawns. Client only.
 */
public final class KotaRenderClient {

	public static final ModelLayerLocation TENDA = lapis("tenda");
	public static final ModelLayerLocation TENDA_WARNA = lapis("tenda_warna");
	public static final ModelLayerLocation LAPAK_HANTU = lapis("lapak_hantu");
	public static final ModelLayerLocation PANGGUNG = lapis("panggung");
	public static final ModelLayerLocation API_BAHAN = lapis("api_bahan");
	public static final ModelLayerLocation API_NYALA = lapis("api_nyala");
	public static final ModelLayerLocation BARANG = lapis("barang");
	public static final ModelLayerLocation USUNGAN = lapis("usungan");
	public static final ModelLayerLocation JAMUAN = lapis("jamuan");
	public static final ModelLayerLocation DADU = lapis("dadu");
	public static final ModelLayerLocation LAYAR = lapis("layar");
	public static final ModelLayerLocation BATU_UJI = lapis("batu_uji");
	public static final ModelLayerLocation BATU_UJI_GLIF = lapis("batu_uji_glif");
	public static final ModelLayerLocation PENGUNGSI = lapis("pengungsi");

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		daftar(TENDA, KotaMesh::tenda);
		daftar(TENDA_WARNA, KotaMesh::tendaWarna);
		daftar(LAPAK_HANTU, KotaMesh::lapakHantu);
		daftar(PANGGUNG, KotaMesh::panggung);
		daftar(API_BAHAN, KotaMesh::apiBahan);
		daftar(API_NYALA, KotaMesh::apiNyala);
		daftar(BARANG, KotaMesh::barang);
		daftar(USUNGAN, KotaMesh::usungan);
		daftar(JAMUAN, KotaMesh::jamuan);
		daftar(DADU, KotaMesh::dadu);
		daftar(LAYAR, KotaMesh::layar);
		daftar(BATU_UJI, KotaMesh::batuUji);
		daftar(BATU_UJI_GLIF, KotaMesh::batuUjiGlif);
		daftar(PENGUNGSI, KotaMesh::pengungsi);
		EntityRenderers.register(KotaAcaraEntities.TENDA_PASAR, TendaRenderer.Pasar::new);
		EntityRenderers.register(KotaEkonomiEntities.TENDA_LAPAK, TendaRenderer.Lapak::new);
		EntityRenderers.register(KotaEkonomiEntities.LAPAK_HANTU, LapakHantuRenderer::new);
		EntityRenderers.register(KotaAcaraEntities.PANGGUNG_KELILING, PanggungRenderer::new);
		EntityRenderers.register(KotaAcaraEntities.API_KECIL, ApiKecilRenderer::new);
		EntityRenderers.register(KotaAcaraEntities.BARANG_TERCECER, BarangTercecerRenderer::new);
		EntityRenderers.register(KotaAcaraEntities.USUNGAN_PETI, UsunganPetiRenderer::new);
		EntityRenderers.register(KotaAcaraEntities.MEJA_JAMUAN, MejaJamuanRenderer::new);
		EntityRenderers.register(KotaAcaraEntities.MEJA_DADU, MejaDaduRenderer::new);
		EntityRenderers.register(KotaAcaraEntities.LAYAR_BAYANG, LayarBayangRenderer::new);
		EntityRenderers.register(KotaSekteEntities.BATU_UJI, BatuUjiRenderer::new);
		EntityRenderers.register(KotaTumbuhEntities.TENDA_PENGUNGSI, TendaPengungsiRenderer::new);
		BlockEntityRenderers.register(KotaEkonomiBlocks.RAK_DAGANG_BE, RakDagangRenderer::new);
		BlockEntityRenderers.register(KotaEkonomiBlocks.PAPAN_NAMA_TOKO_BE, PapanNamaTokoRenderer::new);
		BlockEntityRenderers.register(KotaEkonomiBlocks.PAPAN_CABANG_BE, PapanCabangRenderer::new);
		dev.kultivator.client.dunia.kota42.render.hewan.HewanRenderClient.init();
	}

	private static ModelLayerLocation lapis(String mesh) {
		return new ModelLayerLocation(Kultivator.id("kota42_" + mesh), "main");
	}

	private static void daftar(ModelLayerLocation layer, Supplier<LayerDefinition> mesh) {
		ModelLayerRegistry.registerModelLayer(layer, mesh::get);
	}

	/** {@code textures/entity/kota42/<name>.png}. */
	static Identifier tekstur(String name) {
		return Kultivator.id("textures/entity/kota42/" + name + ".png");
	}

	/**
	 * How dark it is for lamps, 0 (day) .. 1 (night): lamps are lit from dusk (day time 12000) to dawn (23500) with a
	 * 600-tick ramp at both ends. Reads the client level's day time; never changes it.
	 */
	public static float malam(Level level) {
		long t = level.getOverworldClockTime() % 24000L;
		float f = t < 12000L ? 0.0F : t < 12600L ? (t - 12000L) / 600.0F : t < 22900L ? 1.0F : t < 23500L ? (23500L - t) / 600.0F : 0.0F;
		return Mth.clamp(f, 0.0F, 1.0F);
	}

	/** A candle flicker 0.9 .. 1.0 of game time {@code waktu} (ticks with partial) and a stable phase. */
	public static float kedip(float waktu, float fase) {
		return 0.95F + 0.03F * Mth.sin(waktu * 0.61F + fase) + 0.02F * Mth.sin(waktu * 1.37F + fase * 2.3F);
	}

	private KotaRenderClient() {
	}
}
