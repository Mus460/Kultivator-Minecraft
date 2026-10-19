package dev.kultivator.client.tempa;

import dev.kultivator.tempa.TempaMenus;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.nbt.CompoundTag;

/**
 * The client side of forging, the natal treasure and storage rings (DESIGN 25.15, owned by P8-tempa): the forge and ring
 * screens, the natal treasure render layer on every avatar, and the last {@code DuniaSync} of its channel (routed by
 * {@code client.dunia.DuniaClient}), which also carries the open forge's preview card.
 */
public final class TempaClient {

	private static CompoundTag data = new CompoundTag();

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		MenuScreens.register(TempaMenus.FORGE, ForgeScreen::new);
		MenuScreens.register(TempaMenus.RING, RingScreen::new);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new NatalRenderLayer(avatar, context));
			}
		});
	}

	/** The module's last {@code DuniaSync} data arrived. */
	public static void onSync(CompoundTag tag) {
		data = tag == null ? new CompoundTag() : tag;
	}

	/** The last synced data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	/** The open forge's preview card (empty when none arrived). */
	public static CompoundTag forge() {
		return data.getCompoundOrEmpty("forge");
	}

	private TempaClient() {
	}
}
