package dev.kultivator.client.sekte.langit;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * The frame of the sky-sect visuals (DESIGN 37.6): one {@code LevelRenderEvents.COLLECT_SUBMITS} listener that, when a
 * synced sky hall is within 384 blocks or a sky effect is alive, fills {@link LangitFrame}, starts the sky's share of
 * the 7b frame budget ({@link LangitBudget}) and asks the layers in priority order (cloud sea > effects > falls > dome >
 * dawn > stars > cranes > petals > lanterns > halo), then submits the sinks far to near with the additive ones last.
 * Never in a shader pack's shadow pass, never while a Film Kosmik 3D stage covers the frame; idle cost: two list checks.
 * The client tick drives the sounds, the falls' mist motes, the tether motes and the test timers.
 */
final class LangitVisuals {

	private static boolean warned;
	private static int quadsLast;

	static void collect(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || !LangitSkyState.any() && !LangitFxClient.any()) {
			return;
		}
		if (ShaderCompat.shadowPass() || dev.kultivator.client.epic.kosmik.Kosmik3D.covering()) {
			return;
		}
		long start = System.nanoTime();
		try {
			EpicBudget.beginFrame();
			LangitFrame f = LangitFrame.INSTANCE;
			f.setup(minecraft, level, context.levelState().cameraRenderState, context.levelState().worldPartialTicks);
			if (f.tier < 0) {
				return;
			}
			LangitBudget.begin(f.tier);
			resetAll();
			LangitSkyState.Hall hall = LangitSkyState.nearest(f.camX, f.camZ);
			if (hall != null) {
				f.at(hall);
				LautAwanRenderer.draw(f);
			}
			LangitFxClient.draw(f);
			if (hall != null) {
				AirTerjunKabut.draw(f);
				KubahFormasi.draw(f);
				CahayaFajar.draw(f);
				SungaiBintang.draw(f);
				KawananBangau.draw(f);
				KelopakJatuh.draw(f);
				LenteraLangit.draw(f);
				HaloPelangi.draw(f);
			} else {
				KawananBangau.nearest = Float.MAX_VALUE;
			}
			submitAll(context.submitNodeCollector(), context.poseStack(), f.tier);
			quadsLast = LangitBudget.used();
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Sekte Langit visuals skipped a frame", exception);
			}
		} finally {
			EpicBudget.addCpu(System.nanoTime() - start);
		}
	}

	private static void resetAll() {
		SungaiBintang.river().reset();
		SungaiBintang.stars().reset();
		LautAwanRenderer.reset();
		HaloPelangi.sink().reset();
		AirTerjunKabut.fall().reset();
		AirTerjunKabut.mist().reset();
		KubahFormasi.ring().reset();
		KubahFormasi.net().reset();
		KubahFormasi.pillar().reset();
		KawananBangau.sink().reset();
		KelopakJatuh.sink().reset();
		LenteraLangit.glow().reset();
		LenteraLangit.lanterns().reset();
		CahayaFajar.sink().reset();
		for (AwanSink sink : LangitFxClient.SINKS) {
			sink.reset();
		}
		for (AwanSink sink : LangitFxClient.ADDITIVE) {
			sink.reset();
		}
	}

	/** Far to near: the sky, the sea and what lies on it, the island's own layers, the air round it, additive last. */
	private static void submitAll(SubmitNodeCollector collector, PoseStack stack, int tier) {
		SungaiBintang.river().submit(collector, stack);
		SungaiBintang.stars().submit(collector, stack);
		LautAwanRenderer.sink(tier).submit(collector, stack);
		LangitFxClient.SINKS[0].submit(collector, stack);
		HaloPelangi.sink().submit(collector, stack);
		KubahFormasi.net().submit(collector, stack);
		AirTerjunKabut.fall().submit(collector, stack);
		AirTerjunKabut.mist().submit(collector, stack);
		KubahFormasi.pillar().submit(collector, stack);
		KubahFormasi.ring().submit(collector, stack);
		for (int i = 1; i < LangitFxClient.SINKS.length; i++) {
			LangitFxClient.SINKS[i].submit(collector, stack);
		}
		KawananBangau.sink().submit(collector, stack);
		KelopakJatuh.sink().submit(collector, stack);
		LenteraLangit.glow().submit(collector, stack);
		LenteraLangit.lanterns().submit(collector, stack);
		CahayaFajar.sink().submit(collector, stack);
		for (AwanSink sink : LangitFxClient.ADDITIVE) {
			sink.submit(collector, stack);
		}
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		HaloPelangi.tick();
		LenteraLangit.tick();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		LangitFxClient.tick(level, camera.x, camera.y, camera.z);
		LangitSkyState.Hall hall = LangitSkyState.nearest(camera.x, camera.z);
		if (hall == null) {
			return;
		}
		int tier = Math.max(0, LangitBudget.tier());
		AirTerjunKabut.tick(level, camera.x, camera.y, camera.z, hall, LangitBudget.particleTier(minecraft, tier));
		long clock = level.getOverworldClockTime();
		LangitSuara.tick(level, camera.x, camera.y, camera.z, hall, Math.floorMod(clock, 24000L), Math.floorDiv(clock, 24000L));
	}

	static int quadsLastFrame() {
		return quadsLast;
	}

	static void clear() {
		LangitSkyState.clear();
		LangitFxClient.clear();
		HaloPelangi.clear();
		LenteraLangit.clear();
		LangitSuara.clear();
		LangitPoses.clear();
		quadsLast = 0;
	}

	private LangitVisuals() {
	}
}
