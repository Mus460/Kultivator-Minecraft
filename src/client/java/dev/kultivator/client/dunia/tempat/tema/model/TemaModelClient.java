package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.Set;

/**
 * Model part of the themed towns (DESIGN 38.T.9, module 38TV-M), called by {@code TemaClient.init()}: registers the
 * cuboid model layer and the renderer of every type in {@link TemaEntities} (ships and boats 38.T.9.1, creatures
 * 38.T.9.2, mechanisms 38.T.9.3). Renderers read only the synced {@code TemaAnimasi} state (varian, keadaan, mulai,
 * nilai) and never change behaviour; LOD per 38.T.9: beyond 64 blocks no small animation, beyond 128 not drawn (ships at
 * sea 192). The hand props {@code kultivator:fx/tempat/tema/<id>} (PropId 41-62) and the item models of
 * {@code kultivator:tema/*} and {@code kultivator:dagang/*} are model files only (tools/gen_tempat_tema_models.py).
 * <p>
 * A type without a renderer here would crash the client when it spawns, so {@link #init()} checks the whole list at the
 * end and gives any type still missing a {@link NoopRenderer} with a warning (none is expected).
 * The client check command {@code /temamodel} ({@link TemaModelPerintah}) reports render counts, LOD and missing assets.
 */
public final class TemaModelClient {

	private static boolean ready;
	private static final Set<EntityType<?>> TERDAFTAR = new HashSet<>();

	/** From {@code TemaClient.init()}. Safe to call twice. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		// ---------------------------------------------------------------- Pelabuhan (38.T.3)
		JungRenderer.register();
		PerahuNelayanRenderer.register();
		ApiSuarRenderer.register();
		DerekDermagaRenderer.register();
		KapalGalanganRenderer.register();
		pasang(TemaEntities.KAPAL_DAGANG, JungRenderer::dagang);
		pasang(TemaEntities.KAPAL_BAJAK_LAUT, JungRenderer::bajak);
		pasang(TemaEntities.PERAHU_NELAYAN, PerahuNelayanRenderer::new);
		pasang(TemaEntities.API_SUAR, ApiSuarRenderer::new);
		pasang(TemaEntities.DEREK_DERMAGA, DerekDermagaRenderer::new);
		pasang(TemaEntities.KAPAL_GALANGAN, KapalGalanganRenderer::new);
		// ---------------------------------------------------------------- Oasis (38.T.4)
		UntaKafilahRenderer.register();
		KalajengkingRajaRenderer.register();
		pasang(TemaEntities.UNTA_KAFILAH, UntaKafilahRenderer::new);
		pasang(TemaEntities.KALAJENGKING_RAJA, KalajengkingRajaRenderer::new);
		TimbaSumurRenderer.register();
		TemuanPasirRenderer.register();
		PintuPasirRenderer.register();
		PilarRuneRenderer.register();
		BatuUkurAirRenderer.register();
		pasang(TemaEntities.TIMBA_SUMUR, TimbaSumurRenderer::new);
		pasang(TemaEntities.TEMUAN_PASIR, TemuanPasirRenderer::new);
		pasang(TemaEntities.PINTU_PASIR, PintuPasirRenderer::new);
		pasang(TemaEntities.PILAR_RUNE, PilarRuneRenderer::new);
		pasang(TemaEntities.BATU_UKUR_AIR, BatuUkurAirRenderer::new);
		// ---------------------------------------------------------------- Air (38.T.5)
		PerahuKanalRenderer.register();
		PerahuNagaRenderer.register();
		AlatTenunRenderer.register();
		pasang(TemaEntities.PERAHU_KANAL, PerahuKanalRenderer::new);
		pasang(TemaEntities.PERAHU_NAGA, PerahuNagaRenderer::new);
		pasang(TemaEntities.ALAT_TENUN, AlatTenunRenderer::new);
		// ---------------------------------------------------------------- Gunung (38.T.6)
		KeretaGantungRenderer.register();
		KatrolRenderer.register();
		RodaKatrolRenderer.register();
		BatuRuntuhRenderer.register();
		HerbaTebingRenderer.register();
		PalangRenderer.register();
		TaliTurunRenderer.register();
		JeratRenderer.register();
		KeraGunungRenderer.register();
		pasang(TemaEntities.KERETA_GANTUNG, KeretaGantungRenderer::new);
		pasang(TemaEntities.KATROL, KatrolRenderer::new);
		pasang(TemaEntities.RODA_KATROL, RodaKatrolRenderer::new);
		pasang(TemaEntities.BATU_RUNTUH, BatuRuntuhRenderer::new);
		pasang(TemaEntities.HERBA_TEBING, HerbaTebingRenderer::new);
		pasang(TemaEntities.PALANG, PalangRenderer::new);
		pasang(TemaEntities.TALI_TURUN, TaliTurunRenderer::new);
		pasang(TemaEntities.JERAT, JeratRenderer::new);
		pasang(TemaEntities.KERA_GUNUNG, KeraGunungRenderer::new);
		// ---------------------------------------------------------------- Iblis (38.T.7)
		GerbangTengkorakRenderer.register();
		PintuArenaRenderer.register();
		SelTawananRenderer.register();
		SegelPintuRenderer.register();
		TongMinyakRenderer.register();
		TuasGerbangRenderer.register();
		pasang(TemaEntities.GERBANG_TENGKORAK, GerbangTengkorakRenderer::new);
		pasang(TemaEntities.PINTU_ARENA, PintuArenaRenderer::new);
		pasang(TemaEntities.SEL_TAWANAN, SelTawananRenderer::new);
		pasang(TemaEntities.SEGEL_PINTU, SegelPintuRenderer::new);
		pasang(TemaEntities.TONG_MINYAK, TongMinyakRenderer::new);
		pasang(TemaEntities.TUAS_GERBANG, TuasGerbangRenderer::new);
		// ---------------------------------------------------------------- Bersama (38.T.9.3)
		TanduRenderer.register();
		PintuAirRenderer.register();
		pasang(TemaEntities.TANDU, TanduRenderer::new);
		pasang(TemaEntities.PINTU_AIR, PintuAirRenderer::new);
		// ---------------------------------------------------------------- the safety net (no type may stay without a renderer)
		for (EntityType<?> type : TemaEntities.semua()) {
			if (!TERDAFTAR.contains(type)) {
				Kultivator.LOGGER.warn("[Tema] no model for {}, drawn as nothing", BuiltInRegistries.ENTITY_TYPE.getKey(type));
				kosong(type);
			}
		}
		TemaModelPerintah.init();
	}

	/** How many themed entity types got a real renderer here (for {@code /temamodel info}). */
	static int terdaftar() {
		return TERDAFTAR.size();
	}

	private static <T extends Entity> void pasang(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
		EntityRenderers.register(type, provider);
		TERDAFTAR.add(type);
	}

	private static <T extends Entity> void kosong(EntityType<T> type) {
		EntityRenderers.register(type, NoopRenderer::new);
	}

	private TemaModelClient() {
	}
}
