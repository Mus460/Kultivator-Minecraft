package dev.kultivator.client.dunia.kapal;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.screen.CloudWipe;
import dev.kultivator.client.epic.screen.EpicScreen;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalEntities;
import dev.kultivator.dunia.kapal.KapalNet;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Module T's client entry (DESIGN 34.6 T), wired by its one line in {@code DuniaContentClient}: the ship and raider
 * renderers, the
 * ship payload receivers (cloud wipes, arrival and launch moments, the helm screen), the captain's keys (W/S speed, A/D
 * rudder, jump up, sprint down: an input payload every 2 ticks while seated at the helm; Sneak leaves the seat as on any
 * vehicle), the ship HUD and the per-tick looks ({@link KapalFx}); then the world map client ({@code PetaClient}).
 */
public final class KapalClient {

	private static byte sentForward;
	private static byte sentTurn;
	private static byte sentVertical;
	private static int sinceSent;
	private static boolean wasCaptain;
	/** The cloud leap's plain fade (Kamera sinematik Mati): fade start (millis) and whether it is clearing. */
	static long fadeStart;
	static boolean fadeOut;
	static long fadeOutStart;

	public static void init() {
		EntityRenderers.register(KapalEntities.ROH, KapalRenderer::new);
		EntityRenderers.register(KapalEntities.DEK, NoopRenderer::new);
		EntityRenderers.register(KapalEntities.BAJAK, KapalBajakRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(KapalNet.Event.TYPE, (payload, context) -> onEvent(payload));
		ClientPlayNetworking.registerGlobalReceiver(KapalNet.Info.TYPE, (payload, context) -> HelmScreen.onInfo(payload));
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> KapalFx.loaded(entity));
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> KapalFx.unloaded(entity));
		ClientTickEvents.END_CLIENT_TICK.register(KapalClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			KapalFx.clear();
			KapalModel.clear();
			fadeStart = 0L;
			fadeOut = false;
		}));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("kapal_hud"), new KapalHud());
		dev.kultivator.client.dunia.peta.PetaClient.init();
	}

	/** The ship the local player rides, or null. */
	static @Nullable KapalRohEntity riding() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getVehicle() instanceof KapalRohEntity ship ? ship : null;
	}

	/** True when the local player sits at the helm of {@code ship}. */
	static boolean atHelm(KapalRohEntity ship) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && ship.helmRider() == player;
	}

	private static void tick(Minecraft minecraft) {
		KapalFx.tick(minecraft);
		KapalRohEntity ship = riding();
		boolean captain = ship != null && atHelm(ship) && minecraft.gui.screen() == null;
		if (!captain) {
			if (wasCaptain && ship != null) {
				send(ship, (byte) 0, (byte) 0, (byte) 0);
			}
			wasCaptain = false;
			return;
		}
		wasCaptain = true;
		var options = minecraft.options;
		byte forward = (byte) ((options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0));
		byte turn = (byte) ((options.keyRight.isDown() ? 1 : 0) - (options.keyLeft.isDown() ? 1 : 0));
		byte vertical = (byte) ((options.keyJump.isDown() ? 1 : 0) - (options.keySprint.isDown() ? 1 : 0));
		sinceSent++;
		boolean changed = forward != sentForward || turn != sentTurn || vertical != sentVertical;
		if ((changed && sinceSent >= 2) || sinceSent >= 10) {
			send(ship, forward, turn, vertical);
		}
	}

	private static void send(KapalRohEntity ship, byte forward, byte turn, byte vertical) {
		if (!ClientPlayNetworking.canSend(KapalNet.Input.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new KapalNet.Input(ship.getId(), forward, turn, vertical));
		sentForward = forward;
		sentTurn = turn;
		sentVertical = vertical;
		sinceSent = 0;
	}

	/** A helm button. */
	static void helm(int ship, int op, int arg, String text) {
		if (ClientPlayNetworking.canSend(KapalNet.Helm.TYPE)) {
			ClientPlayNetworking.send(new KapalNet.Helm(ship, op, arg, text == null ? "" : text));
		}
	}

	// ================================================================== moments

	private static void onEvent(KapalNet.Event event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 at = new Vec3(event.x(), event.y(), event.z());
		switch (event.kind()) {
			case KapalNet.Event.EV_LEAP_IN -> {
				int hold = Math.max(10, event.arg() + 8);
				if (CinematicDirector.mode() == EpicSettings.Camera.MATI || !CloudWipe.start(10, hold, 14, 0xFFFFFF)) {
					fadeStart = System.currentTimeMillis();
					fadeOut = false;
				}
			}
			case KapalNet.Event.EV_LEAP_OUT -> {
				CloudWipe.release(14);
				if (fadeStart != 0L && !fadeOut) {
					fadeOut = true;
					fadeOutStart = System.currentTimeMillis();
				}
			}
			case KapalNet.Event.EV_ARRIVE -> {
				KapalRohEntity ship = minecraft.level.getEntity(event.ship()) instanceof KapalRohEntity s ? s : null;
				Component name = Component.translatable("kultivator.kapal.moment.arrive");
				if (event.arg() == 1 && CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
					Scene scene = DuniaScenes.world("kapal/tiba", at, event.ship(), 60, true);
					if (scene != null) {
						float r = ship == null ? 8.0F : ship.kind().radius();
						DuniaScenes.pullback(scene, 0, r * 0.6F, r * 1.6F, 3.0F, r * 0.5F + 4.0F, 2.0F);
					}
				}
				DuniaScenes.banner(name);
			}
			case KapalNet.Event.EV_LAUNCH -> {
				KapalClass kind = KapalClass.byOrdinal(event.arg());
				DuniaScenes.banner(Component.translatable("kultivator.kapal.moment.launch", Component.translatable("kultivator.kapal.class."
						+ kind.model())));
			}
			case KapalNet.Event.EV_PIRATES -> {
				EpicScreen.vignette(0xB3281D, 0.35F, 60);
				EpicScreen.inkEdge(0x1A0806, 0.4F, 50);
				DuniaScenes.banner(Component.translatable("kultivator.kapal.moment.raider"));
			}
			case KapalNet.Event.EV_BROKEN -> EpicScreen.grey(0.35F, 40);
			case KapalNet.Event.EV_METEOR -> {
				// a falling star caught in the cloud net: a shower of gold sparks over the deck
				DuniaScenes.banner(Component.translatable("kultivator.kapal.moment.meteor"));
				burst(minecraft, at, 0xFFC66A, 0xFFF4C8, 10, 0.18D, 0.08D);
				EpicScreen.vignette(0xFFC66A, 0.15F, 30);
			}
			case KapalNet.Event.EV_SPOILS -> {
				// the pirates' chest bursts: gold and jade motes fountain from the deck
				DuniaScenes.banner(Component.translatable("kultivator.kapal.moment.spoils"));
				burst(minecraft, at, 0xFFD27A, 0x8CFFE0, 8 + 4 * Math.max(1, Math.min(4, event.arg())), 0.12D, 0.22D);
			}
			default -> {
			}
		}
	}

	/**
	 * A budgeted burst of qi motes at {@code at}: {@code base} motes on Normal (half on Minimal, double on Maximal, scaled
	 * by the screen-effect setting through {@code EffectQuality}), alternating two colours, thrown outward and up.
	 */
	private static void burst(Minecraft minecraft, Vec3 at, int rgbA, int rgbB, int base, double spread, double lift) {
		if (minecraft.level == null) {
			return;
		}
		int n = EffectQuality.minimal() ? base / 2 : EffectQuality.maximal() ? base * 2 : base;
		var random = minecraft.level.getRandom();
		for (int i = 0; i < n; i++) {
			double a = random.nextDouble() * Math.PI * 2.0D;
			double s = spread * (0.4D + random.nextDouble());
			minecraft.level.addParticle(Qi.mote(i % 2 == 0 ? rgbA : rgbB, 0.16F + random.nextFloat() * 0.14F, 22 + random.nextInt(16)), at.x,
					at.y + 0.6D, at.z, Math.cos(a) * s, lift + random.nextDouble() * lift, Math.sin(a) * s);
		}
	}

	/** 0..1 of the plain leap fade (Kamera Mati, or a refused wipe). */
	static float fade() {
		if (fadeStart == 0L) {
			return 0.0F;
		}
		long now = System.currentTimeMillis();
		if (fadeOut) {
			float t = (now - fadeOutStart) / 700.0F;
			if (t >= 1.0F) {
				fadeStart = 0L;
				fadeOut = false;
				return 0.0F;
			}
			return 1.0F - t;
		}
		if (now - fadeStart > 8000L) {
			// the server never answered: clear by itself
			fadeOut = true;
			fadeOutStart = now;
		}
		return Math.min(1.0F, (now - fadeStart) / 500.0F);
	}

	private KapalClient() {
	}
}
