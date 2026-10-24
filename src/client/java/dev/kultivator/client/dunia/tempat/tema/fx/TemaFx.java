package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.kosmik.Kosmik3D;
import dev.kultivator.client.epic.kosmik.KosmikTypes;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.nbt.CompoundTag;

/**
 * The frame driver of the themed-town client effects (DESIGN 38.T.9.4, module 38TV-F): one
 * {@code LevelRenderEvents.COLLECT_SUBMITS} pass that lets every effect write camera-relative quads into a few shared
 * lists (one per texture and blend: cutout, translucent emissive, additive) and submits them with vanilla entity render
 * types, one client tick that spreads the entity scans and runs the sounds, one HUD element ({@link TemaHud}) and the
 * client preview command ({@link TemaFxPerintah}).
 * <p>
 * Weak-laptop rules: nothing draws in a shader pack's shadow pass, under a Film Kosmik stage or at {@code EpicBudget}
 * tier −1; the whole pass is capped at {@link #QUADS} quads per tier (× 0.6 with a shader pack), the streams of many
 * small quads (sand streaks, lanterns) also take from the shared 7b budget ({@code EpicBudget.takeQuads}), and the pass
 * returns at once when no effect is live. The "heavy" look (fog, beams, glories) is faked with layered additive and
 * translucent quads plus HUD tints: no post chain, no fog or weather or time change (Sodium and Iris safe).
 */
public final class TemaFx {

	/** Quad cap of the whole themed pass per tier (Minimal, Normal, Maximal). */
	static final int[] QUADS = {600, 2200, 4200};

	static FxQuads fxCut;
	static FxQuads fxGlow;
	static FxQuads fxAdd;
	static FxQuads awan;
	static FxQuads kabut;
	static FxQuads pasir;
	static FxQuads dinding;
	static FxQuads ombak;
	static FxQuads dayung;
	static FxQuads mural;

	private static FxQuads[] semua;
	private static boolean initialised;
	private static boolean warned;
	private static int cap;
	private static int quadsLast;

	private TemaFx() {
	}

	/** Once from {@code TemaClient.init()}. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(TemaFx::collect);
		ClientTickEvents.END_CLIENT_TICK.register(TemaFx::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Kultivator.id("tema_fx_hud"), new TemaHud());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> bersihkan());
		TemaFxPerintah.init();
	}

	/**
	 * One {@code TemaNet.TemaFxPayload} ({@code kultivator:tema_fx}, C38.T4) from {@code TemaClient}: kind {@code jenis}
	 * ({@code TemaNet.BADAI} 0, {@code FESTIVAL} 1, {@code SPLINE} 2, {@code CAHAYA} 3; {@code KAPAL_JADWAL} 4 is data
	 * for the hub and screens only) for town {@code situsId}. Unknown kinds and light names are ignored, so 38TK may
	 * append kinds without breaking an older client.
	 */
	public static void terima(int jenis, long situsId, CompoundTag data) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
		CompoundTag d = data == null ? new CompoundTag() : data;
		try {
			switch (jenis) {
				case TemaNet.BADAI -> BadaiPasirFx.terima(situsId, d);
				case TemaNet.FESTIVAL -> TemaFestival.terima(situsId, d);
				case TemaNet.SPLINE -> TemaJalur.terima(situsId, d);
				case TemaNet.CAHAYA -> {
					switch (d.getStringOr("apa", "")) {
						case "budha" -> CahayaBudhaFx.terima(situsId, d, now);
						case "suar" -> SinarSuarFx.terima(situsId, d, now);
						case "awan" -> LautAwanFx.terima(situsId, d, now);
						case "kabut" -> KabutGunungFx.terima(situsId, d, now);
						case "riak" -> RiakDarahFx.terima(situsId, d);
						default -> {
						}
					}
				}
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Kultivator: themed-town effect payload {} for site {} ignored", jenis, situsId, exception);
			}
		}
	}

	/** Forgets every themed effect (disconnect, or the preview's {@code /temafx mati}). */
	public static void bersihkan() {
		BadaiPasirFx.bersihkan();
		LenteraAirFx.bersihkan();
		CahayaBudhaFx.bersihkan();
		LautAwanFx.bersihkan();
		KabutGunungFx.bersihkan();
		SinarSuarFx.bersihkan();
		RiakDarahFx.bersihkan();
		OmbakFx.bersihkan();
		TaliGantungRenderer.bersihkan();
		MuralBillboard.bersihkan();
		PendayungImpostor.bersihkan();
		TemaFestival.bersihkan();
		TemaJalur.bersihkan();
		TemaHud.bersihkan();
	}

	/** Quads drawn by the themed pass last frame (the {@code /temafx info} report). */
	public static int quadsLastFrame() {
		return quadsLast;
	}

	/** True while {@code n} more quads fit this frame's themed cap. */
	static boolean muat(int n) {
		return dipakai() + n <= cap;
	}

	private static int dipakai() {
		int total = 0;
		for (FxQuads q : semua) {
			total += q.quads();
		}
		return total;
	}

	private static boolean adaYangHidup() {
		return BadaiPasirFx.aktif() || LenteraAirFx.aktif() || CahayaBudhaFx.aktif() || LautAwanFx.aktif() || KabutGunungFx.aktif()
				|| SinarSuarFx.aktif() || RiakDarahFx.aktif() || OmbakFx.aktif() || TaliGantungRenderer.aktif() || MuralBillboard.aktif()
				|| PendayungImpostor.aktif();
	}

	private static void siapkan() {
		if (fxCut != null) {
			return;
		}
		fxCut = new FxQuads(KosmikTypes.cutout(FxTekstur.FX), false);
		fxGlow = new FxQuads(KosmikTypes.glow(FxTekstur.FX), false);
		fxAdd = new FxQuads(KosmikTypes.add(FxTekstur.FX), true);
		awan = new FxQuads(KosmikTypes.glow(FxTekstur.AWAN), false);
		kabut = new FxQuads(KosmikTypes.glow(FxTekstur.KABUT), false);
		pasir = new FxQuads(KosmikTypes.glow(FxTekstur.PASIR), false);
		dinding = new FxQuads(KosmikTypes.glow(FxTekstur.DINDING_PASIR), false);
		ombak = new FxQuads(RenderTypes.entityTranslucent(FxTekstur.OMBAK), false);
		dayung = new FxQuads(KosmikTypes.cutout(FxTekstur.PENDAYUNG), false);
		mural = new FxQuads(KosmikTypes.cutout(FxTekstur.MURAL), false);
		// submit order: lit cutout bodies, translucent sheets far to near, then the additive light that writes depth
		semua = new FxQuads[] {fxCut, dayung, mural, awan, kabut, ombak, dinding, pasir, fxGlow, fxAdd};
	}

	private static void collect(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || !adaYangHidup() || ShaderCompat.shadowPass() || Kosmik3D.covering()) {
			quadsLast = 0;
			return;
		}
		long start = System.nanoTime();
		try {
			EpicBudget.beginFrame();
			int tier = EpicBudget.tier();
			if (tier < 0) {
				quadsLast = 0;
				return;
			}
			siapkan();
			for (FxQuads q : semua) {
				q.reset();
			}
			CameraRenderState camera = context.levelState().cameraRenderState;
			Bingkai.isi(level, camera.pos, camera.orientation, context.levelState().worldPartialTicks);
			Bingkai.tier = Math.min(2, tier);
			cap = Math.round(QUADS[Bingkai.tier] * (ShaderCompat.packOn() ? EpicBudget.PACK_QUADS : 1.0F));
			// far, big and cheap first so a full frame drops the small near stuff, never the town's big pictures
			LautAwanFx.gambar();
			KabutGunungFx.gambar();
			BadaiPasirFx.gambar();
			SinarSuarFx.gambar();
			CahayaBudhaFx.gambar();
			TaliGantungRenderer.gambar();
			MuralBillboard.gambar();
			PendayungImpostor.gambar();
			OmbakFx.gambar();
			RiakDarahFx.gambar();
			LenteraAirFx.gambar();
			var collector = context.submitNodeCollector();
			var stack = context.poseStack();
			for (FxQuads q : semua) {
				q.submit(collector, stack);
			}
			quadsLast = dipakai();
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Kultivator: themed-town effects skipped a frame", exception);
			}
		} finally {
			EpicBudget.addCpu(System.nanoTime() - start);
		}
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		long now = level.getGameTime();
		BadaiPasirFx.tick(minecraft, now);
		LenteraAirFx.tick(now);
		CahayaBudhaFx.tick(now);
		LautAwanFx.tick(now);
		KabutGunungFx.tick(minecraft, now);
		RiakDarahFx.tick(now);
		MuralBillboard.tick(now);
		TemaFestival.tick(minecraft, now);
		// entity scans, spread over the second so no tick pays for all of them
		int slot = (int) (now % 20L);
		if (slot == 0) {
			SinarSuarFx.pindai(level, minecraft.player.position());
		} else if (slot == 5) {
			TaliGantungRenderer.pindai(level, minecraft.player.position());
		} else if (slot == 10) {
			PendayungImpostor.pindai(level, minecraft.player.position());
		}
		OmbakFx.tick(minecraft, now);
		PendayungImpostor.tick(minecraft, now);
	}
}
