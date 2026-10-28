package dev.kultivator.client.dunia.reruntuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import dev.kultivator.dunia.reruntuhan.menara.PenjagaLantaiEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Client entry of module R, Reruntuhan, Penjara &amp; Menara (DESIGN 34.6 R), wired by its one line in
 * {@code DuniaContentClient.init()}: the renderers of every entity of the module (puzzle parts, sealed doors and phantom
 * walls, the stone guardian, the mirror shadow, the remnant soul, the demon kings and lesser demons, the floor
 * guardians), the receiver of {@link RNet.Sync} (effects, the tower floor's scenery, the lobby's gates, the pagoda's
 * cloud ring, the ranking steles, the talisman warning and the Ujian Hati riddles as Di Sini prompts (DESIGN 43.8.2), the
 * numbers of Dunia › Alam › Menara), the world render of the effects (one {@code COLLECT_SUBMITS} listener) and the client
 * tick. Every visual follows EffectQuality, the 7b budgets and camera rules; nothing touches a block.
 */
public final class ReruntuhanClient {

	private static boolean initialised;
	private static long ticks;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		EntityRenderers.register(REntities.TEKA_PLAT, PartRenderer::new);
		EntityRenderers.register(REntities.TEKA_LAMPU, PartRenderer::new);
		EntityRenderers.register(REntities.TEKA_CINCIN, PartRenderer::new);
		EntityRenderers.register(REntities.TEKA_TIMBANGAN, PartRenderer::new);
		EntityRenderers.register(REntities.SEGEL_JIMAT, PartRenderer::new);
		EntityRenderers.register(REntities.TEKA_DINDING, ColliderRenderer::new);
		EntityRenderers.register(REntities.GERBANG_SEGEL, ColliderRenderer::new);
		EntityRenderers.register(REntities.BONEKA_BATU, GolemRenderer::new);
		EntityRenderers.register(REntities.BAYANGAN, ShadowRenderer::new);
		EntityRenderers.register(REntities.SISA_JIWA, SoulRenderer::new);
		EntityRenderers.register(REntities.IBLIS_KECIL, DemonRenderer::new);
		EntityRenderers.<RajaIblisEntity>register(REntities.RAJA_IBLIS, context -> new RigRenderer<>(context, true));
		EntityRenderers.<PenjagaLantaiEntity>register(REntities.PENJAGA_LANTAI, context -> new RigRenderer<>(context, false));
		ClientPlayNetworking.registerGlobalReceiver(RNet.Sync.TYPE, (payload, context) -> onSync(payload.data()));
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			RFxClient.render(context);
			TowerScenery.render(context);
			TowerSights.render(context);
		});
		ClientTickEvents.END_CLIENT_TICK.register(ReruntuhanClient::tick);
		ClientDisconnect.register((handler, client) -> client.execute(() -> {
			RFxClient.clear();
			TowerScenery.reset();
			TowerSights.clear();
			LobbyState.clear();
			UjianScreen.reset();
			MenaraScreen.reset();
			RModels.clear();
		}));
		// a seal breaks rarely: one shot per prison moment and minute at most; milestones every 10 floors are rarer still
		DuniaScenes.cooldown("segel/pecah", 1200);
		DuniaScenes.cooldown("segel/buka", 200);
		DuniaScenes.cooldown("segel/warisan", 600);
	}

	private static void onSync(CompoundTag data) {
		if (data == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		try {
			switch (data.getStringOr("op", "")) {
				case "fx" -> RFxClient.onFx(data);
				case "scene" -> TowerScenery.setScene(data);
				case "scene_end" -> {
					TowerScenery.endScene();
					UjianScreen.akhir();
				}
				case "lobby" -> LobbyState.update(data, ticks);
				case "luar" -> TowerSights.onPagoda(data);
				case "stele" -> TowerSights.onStele(data);
				case "riddle" -> UjianScreen.tampil(data.copy());
				case "riddle_end" -> UjianScreen.akhir();
				case "warn" -> JimatScreen.tampil(data);
				case "seal" -> {
					if (minecraft.player != null) {
						boolean broken = data.getBooleanOr("broken", false);
						minecraft.player.sendOverlayMessage(broken
								? Component.translatable("kultivator.reruntuhan.penjara.core.label_broken").withStyle(ChatFormatting.RED)
								: Component.translatable("kultivator.reruntuhan.penjara.core.label", data.getIntOr("integrity", 0)).withStyle(ChatFormatting.GOLD));
					}
				}
				case "info" -> MenaraScreen.onInfo(data);
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: a ruin sync {} failed", data.getStringOr("op", "?"), exception);
		}
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		RFxClient.tick(minecraft);
		TowerScenery.tick(minecraft);
		TowerSights.tick();
	}

	private ReruntuhanClient() {
	}
}
