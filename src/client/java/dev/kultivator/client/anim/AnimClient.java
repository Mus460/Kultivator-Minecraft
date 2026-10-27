package dev.kultivator.client.anim;

import dev.kultivator.client.anim.form.FormLayer;
import dev.kultivator.client.anim.model.FireUltimates;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.jetbrains.annotations.Nullable;

/**
 * The animation and model module (DESIGN 27, polish plan M3): cast poses, 3D technique props, transformation forms and
 * bind visuals. {@link #init} (called once from {@code KultivatorClient} through M2's bridge) registers the FormSync and
 * BindSync receivers, the form layer on every avatar renderer (disciples add it themselves), and the client tick that
 * drives the model props and prunes the stores.
 */
public final class AnimClient {

	private static long ticks;
	private static int pruneTicks;
	private static @Nullable ClientLevel lastLevel;
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(Payloads.FormSync.TYPE, (payload, context) -> ClientForms.onSync(payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.BindSync.TYPE, (payload, context) -> ClientBinds.onSync(payload));
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new FormLayer<>(avatar, context));
			}
			// DESIGN 35.5.4: the duel's blade probe on every PlayerModel renderer (players, disciples, rogues, Dunia NPCs)
			dev.kultivator.client.duel.anim.BladeProbeLayer probe = dev.kultivator.client.duel.anim.BladeProbeLayer.forRenderer(renderer);
			if (probe != null) {
				helper.register(probe);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(AnimClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(AnimClient::clearAll));
	}

	/** Client ticks since start (the clock of forms, binds and props). */
	public static long ticks() {
		return ticks;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			clearAll();
		}
		if (level == null) {
			return;
		}
		if (!minecraft.isPaused()) {
			ticks++;
			ModelFx.tick(level);
			FireUltimates.tick(level);
			// Phase 30 (DESIGN 30.5 C30.1): the new ultimate drivers tick after FireUltimates
			dev.kultivator.client.anim.model.UltimatesA.tick(level);
			dev.kultivator.client.anim.model.UltimatesB.tick(level);
		}
		if (++pruneTicks >= 20) {
			pruneTicks = 0;
			CastPoses.prune(level);
			ClientForms.prune(level);
			ClientBinds.prune(level);
			BindVisuals.prune(level);
			dev.kultivator.client.duel.anim.DuelPoses.prune(level);
		}
	}

	private static void clearAll() {
		CastPoses.clear();
		dev.kultivator.client.duel.anim.DuelPoses.clear();
		ClientForms.clear();
		ClientBinds.clear();
		BindVisuals.clear();
		ModelFx.clear();
		FireUltimates.clear();
		dev.kultivator.client.anim.model.UltimatesA.clear();
		dev.kultivator.client.anim.model.UltimatesB.clear();
	}

	private AnimClient() {
	}
}
