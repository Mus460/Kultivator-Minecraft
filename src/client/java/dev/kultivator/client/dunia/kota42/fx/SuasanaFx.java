package dev.kultivator.client.dunia.kota42.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.screen.SettingsRows;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * The one entry of the town fx of 42V-F (DESIGN 42.9.2-42.9.3, 42.12.6), started by {@code KotaSuasanaClient.init}: it owns the
 * client tick and the level-render hook and calls the fx in order (lanterns, kitchen smoke, banners, firecrackers, soot, bodies and
 * street music, ceremonies, pigeons, the particle pool). Every fx reads the same things: the visual titik of
 * {@code kota_titik} ({@link TitikPeta}), the atmosphere of {@code kota_suasana} and the clock ({@link Bingkai}); the switches
 * {@code kota42.lentera / asap / panji / petasan / merpati / jelaga} and the street-music volume come from
 * {@code SettingsRows}. Nothing is drawn in a shader pack's shadow pass, every count follows the quality tier and a failing frame
 * is logged once and skipped, so a bad payload can never take the render down. Client thread only.
 */
public final class SuasanaFx {

	private static boolean siap;
	private static boolean lentera = true;
	private static boolean asap = true;
	private static boolean panji = true;
	private static boolean gagal;
	private static int detak;

	/** Idempotent. */
	public static void init() {
		if (siap) {
			return;
		}
		siap = true;
		ClientTickEvents.END_CLIENT_TICK.register(SuasanaFx::tik);
		LevelRenderEvents.COLLECT_SUBMITS.register(SuasanaFx::kumpul);
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Jelaga.hilang(entity));
		ClientDisconnect.register((handler, client) -> client.execute(SuasanaFx::bersihkan));
	}

	/**
	 * Blocks from the player to the edge of place {@code situs} (its titik's middle and radius), 0 inside; {@link Double#MAX_VALUE}
	 * when no titik of the place arrived. For {@code RiuhKota}'s night-district bed.
	 */
	public static double jarakKeTepi(long situs) {
		var pusat = TitikPeta.pusat(situs);
		var pemain = Bingkai.pemain;
		if (pusat == null || pemain == null) {
			return Double.MAX_VALUE;
		}
		double dx = pusat.x() - pemain.getX();
		double dz = pusat.z() - pemain.getZ();
		return Math.max(0.0D, Math.sqrt(dx * dx + dz * dz) - pusat.radius());
	}

	/** Blocks from the player to the nearest running wedding or funeral procession, {@link Double#MAX_VALUE} when none. For {@code RiuhKota}. */
	public static double jarakUpacara() {
		var pemain = Bingkai.pemain;
		if (pemain == null) {
			return Double.MAX_VALUE;
		}
		double terdekat = Double.MAX_VALUE;
		for (var u : dev.kultivator.client.dunia.kota42.KotaHidupClient.upacara()) {
			if (u.fase() < 0 || u.jenis() != dev.kultivator.dunia.kota42.api.JenisUpacara.NIKAH.ordinal() && u.jenis() != dev.kultivator.dunia.kota42.api.JenisUpacara.MAKAM.ordinal()) {
				continue;
			}
			var c = TubuhFx.pusatUpacara(u);
			if (c != null) {
				terdekat = Math.min(terdekat, Math.sqrt(c.distanceToSqr(pemain.position())));
			}
		}
		return terdekat;
	}

	/** Everything the fx remember is dropped (leaving the world). */
	private static void bersihkan() {
		TitikPeta.bersihkan();
		LenteraFx.bersihkan();
		AsapDapur.bersihkan();
		PanjiKota.bersihkan();
		Petasan.bersihkan();
		Jelaga.bersihkan();
		TubuhFx.bersihkan();
		UpacaraFx.bersihkan();
		MerpatiFx.bersihkan();
		MelodiJalan.bersihkan();
		PartikelKota.hapus();
	}

	private static void tik(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		if (!Bingkai.tik(minecraft)) {
			return;
		}
		if (detak++ % 20 == 0) {
			lentera = SettingsRows.kota42("kota42.lentera");
			asap = SettingsRows.kota42("kota42.asap");
			panji = SettingsRows.kota42("kota42.panji");
			PartikelKota.angin(Bingkai.hari, Bingkai.waktu);
		}
		if (detak % 10 == 1) {
			TitikPeta.segarkan();
			Petasan.picuan();
		}
		try {
			if (lentera) {
				LenteraFx.tik();
			}
			if (asap) {
				AsapDapur.tik();
			}
			if (panji) {
				PanjiKota.tik();
			}
			Petasan.tik();
			Jelaga.tik();
			TubuhFx.tik();
			UpacaraFx.tik();
			MerpatiFx.tik();
			MelodiJalan.tik();
			PartikelKota.tik();
		} catch (RuntimeException exception) {
			if (!gagal) {
				gagal = true;
				Kultivator.LOGGER.warn("Kultivator: the town fx skipped a tick", exception);
			}
		}
	}

	private static void kumpul(LevelRenderContext context) {
		if (Bingkai.level == null || Bingkai.level.dimension() != Level.OVERWORLD || ShaderCompat.shadowPass()) {
			return;
		}
		if (TitikPeta.lentera.isEmpty() && TitikPeta.kain.isEmpty() && TitikPeta.teks.isEmpty() && PartikelKota.jumlah() == 0 && !Petasan.ada() && !Jelaga.ada()) {
			return;
		}
		long mulai = System.nanoTime();
		try {
			Bingkai.bingkai(context);
			Geo.reset();
			PoseStack pose = context.poseStack();
			var collector = context.submitNodeCollector();
			if (lentera) {
				LenteraFx.gambar(pose, collector);
			}
			if (panji) {
				PanjiKota.gambar();
			}
			Petasan.gambar();
			Jelaga.gambar();
			PartikelKota.gambar(Bingkai.tier == 0 ? 48.0D : 96.0D);
			Geo.kirim(collector, pose);
			PapanTitik.gambar(pose, collector, context.levelState().cameraRenderState);
		} catch (RuntimeException exception) {
			if (!gagal) {
				gagal = true;
				Kultivator.LOGGER.warn("Kultivator: the town fx skipped a frame", exception);
			}
		}
		EpicBudget.addCpu(System.nanoTime() - mulai);
	}

	private SuasanaFx() {
	}
}
