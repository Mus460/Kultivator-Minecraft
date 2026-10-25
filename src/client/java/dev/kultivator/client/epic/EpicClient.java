package dev.kultivator.client.epic;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.SkipKey;
import dev.kultivator.client.epic.render.EpicRenderer;
import dev.kultivator.client.epic.screen.EpicScreen;
import dev.kultivator.client.epic.screen.HudHider;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Client entry of the 7b epic moments (DESIGN 23.6, 23.17.3): registers the director tick, the skip key, the HUD layers
 * ({@code kultivator:epic_screen} after {@code MISC_OVERLAYS}, {@code kultivator:epic_card} before {@code CHAT}, the
 * {@link HudHider} wrappers, all from {@code CLIENT_STARTED}), the renderer on {@code COLLECT_SUBMITS}, the block-outline
 * gate during shots, {@code /sinematik} and the disconnect cleanup; and receives {@link Payloads.EpicMoment} cues. Every
 * cue is optional: a missing or unknown cue leaves today's FX as they are.
 */
public final class EpicClient {

	private static boolean hudRegistered;

	/** Called once from {@code KultivatorClient.onInitializeClient}. */
	public static void init() {
		SkipKey.init();
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			try {
				EpicDirector.tick(minecraft);
			} catch (RuntimeException exception) {
				EpicDirector.warn("tick", exception);
			}
		});
		// Phase 30 (DESIGN 30.2): the film engine ticks after the 7b director (it reads the skip presses of this tick)
		ClientTickEvents.END_CLIENT_TICK.register(dev.kultivator.client.epic.film.FilmDirector::tick);
		dev.kultivator.client.epic.film.FilmParticleProviders.init();
		// Film Kosmik 3D (DESIGN 32.1): creates nothing, registers the disconnect release
		dev.kultivator.client.epic.kosmik.Kosmik3D.init();
		// The Pengaturan pages (DESIGN 43.5 [8]) with the motion and photosensitivity warnings as a seal over Kamera & Film
		// (43.10.1); after CultivationScreen.daftarBawaan, whose single Pengaturan page they replace.
		dev.kultivator.client.screen.SettingsPage.daftar();
		LevelRenderEvents.COLLECT_SUBMITS.register(EpicRenderer::collect);
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !CinematicDirector.active());
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> registerHud());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> EpicClientCommands.register(dispatcher));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EpicDirector.clearAll());
	}

	private static void registerHud() {
		if (hudRegistered) {
			return;
		}
		hudRegistered = true;
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Kultivator.id("epic_screen"), new EpicScreen());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("epic_card"), new TitleCard.Hud());
		HudHider.register();
	}

	/** One cue from the server (network thread hand-off is done by Fabric: this runs on the client thread). */
	public static void onPayload(Payloads.EpicMoment payload, ClientPlayNetworking.Context context) {
		try {
			EpicDirector.onCue(payload);
		} catch (RuntimeException exception) {
			EpicDirector.warn("payload", exception);
		}
	}

	private EpicClient() {
	}
}
