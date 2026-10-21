package dev.kultivator.client.dunia.reruntuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import dev.kultivator.dunia.reruntuhan.menara.PenjagaLantaiEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Client entry of module R, Reruntuhan, Penjara &amp; Menara (DESIGN 34.6 R), wired by its one line in
 * {@code DuniaContentClient.init()}: the renderers of every entity of the module (puzzle parts, sealed doors and phantom
 * walls, the stone guardian, the mirror shadow, the remnant soul, the demon kings and lesser demons, the floor
 * guardians), the receiver of {@link RNet.Sync} (effects, the tower floor's scenery, the lobby's gates, the pagoda's
 * cloud ring, the ranking steles, the talisman warning, the Ujian Hati riddles, the tower card's numbers), the world
 * render of the effects (one {@code COLLECT_SUBMITS} listener), the client tick, and the hub card "Menara Seratus" in
 * the Dunia section. Every visual follows EffectQuality, the 7b budgets and camera rules; nothing touches a block.
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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			RFxClient.clear();
			TowerScenery.reset();
			TowerSights.clear();
			LobbyState.clear();
			UjianScreen.pending = null;
			RModels.clear();
		});
		HubCards.register(HubNav.DUNIA, "menara_seratus", Glyphs.HALL, null, () -> {
			HubNav.openChild(new MenaraScreen(null), HubNav.DUNIA);
			MenaraScreen.request();
		});
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
					UjianScreen.pending = null;
					if (minecraft.gui.screen() instanceof UjianScreen) {
						minecraft.gui.setScreen(null);
					}
				}
				case "lobby" -> LobbyState.update(data, ticks);
				case "luar" -> TowerSights.onPagoda(data);
				case "stele" -> TowerSights.onStele(data);
				case "riddle" -> {
					UjianScreen.pending = data.copy();
					minecraft.gui.setScreen(new UjianScreen(data));
				}
				case "riddle_end" -> {
					UjianScreen.pending = null;
					if (minecraft.gui.screen() instanceof UjianScreen) {
						minecraft.gui.setScreen(null);
					}
				}
				case "warn" -> {
					if (minecraft.gui.screen() == null) {
						minecraft.gui.setScreen(new JimatScreen(data));
					}
				}
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
		// an Ujian Hati question hidden with Esc comes back after three seconds while it waits
		CompoundTag pending = UjianScreen.pending;
		if (pending != null && minecraft.gui.screen() == null && minecraft.player != null && Util.getMillis() - UjianScreen.hiddenAt > 3000L) {
			minecraft.gui.setScreen(new UjianScreen(pending));
		}
	}

	private ReruntuhanClient() {
	}
}
