package dev.kultivator.client.sekte.langit;

import dev.kultivator.client.disciple.DiscipleRenderer;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Sekte Langit on the client (DESIGN 37, the client entry of the four sky-sect modules), called once from
 * {@code KultivatorClient.onInitializeClient}. The visuals and sounds are module 37V's; each other module owns exactly
 * ONE line below and replaces its own commented line with the real call.
 * <p>
 * 37V registers: the {@link LangitPayloads.Sync} and {@link LangitPayloads.Fx} receivers (the payload types themselves
 * are registered on both sides by 37B's {@code LangitPayloads.register}, whatever the config says), the sky layers in
 * {@code LevelRenderEvents.COLLECT_SUBMITS} ({@link LangitVisuals}), the client tick (sounds, mist motes, test timers),
 * the patrol-sword layer on the disciple renderer, the client switches ({@link LangitClientConfig}) and the cleanup on a
 * disconnect. The weather mixins and the pose mixin call {@link #weatherSheltered} and {@link LangitPoses#apply}.
 */
public final class LangitClient {

	private static boolean ready;

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		// 37V: atmosphere, effects, sounds, poses.
		LangitClientConfig.load();
		ClientPlayNetworking.registerGlobalReceiver(LangitPayloads.Sync.TYPE, (payload, context) -> LangitSkyState.onSync(payload));
		ClientPlayNetworking.registerGlobalReceiver(LangitPayloads.Fx.TYPE, (payload, context) -> LangitFxClient.onFx(payload));
		LevelRenderEvents.COLLECT_SUBMITS.register(LangitVisuals::collect);
		ClientTickEvents.END_CLIENT_TICK.register(LangitVisuals::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LangitVisuals.clear());
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof DiscipleRenderer disciple) {
				// the disciple model is a PlayerModel (an EntityModel of AvatarRenderState), which the helper's generic bound
				// cannot express for DiscipleRenderState; the layer only ever sees the disciple renderer's own states
				helper.register((net.minecraft.client.renderer.entity.layers.RenderLayer) new LangitPoses.SwordLayer(disciple));
			}
		});
		// 37M: the Cloud Ferry renderer.
		dev.kultivator.client.sekte.langit.gerak.LangitGerakClient.init();
		// 37G: the board data of Sekte › Wilayah › Langit, the story beats of the sky events.
		dev.kultivator.client.sekte.langit.ui.LangitUiClient.init();
	}

	/**
	 * True when this client should not draw or hear the weather at (x, y, z): above the cloud sea of a synced standing
	 * sky hall, with the switch "Kebal cuaca" on (the weather mixins; cheap: a few box checks).
	 */
	public static boolean weatherSheltered(double x, double y, double z) {
		return ready && LangitClientConfig.on(LangitClientConfig.Toggle.CUACA_KEBAL) && LangitSkyState.shelter(x, y, z) != null;
	}

	/**
	 * The sky view settings (the "Pemandangan" talisman of Sekte › Wilayah › Langit): the rows of
	 * {@link LangitClientConfig#rows()} in Pengaturan › Tampilan &amp; Efek (DESIGN 43.10.1).
	 */
	public static void openSettings() {
		net.minecraft.nbt.CompoundTag fokus = new net.minecraft.nbt.CompoundTag();
		fokus.putString(dev.kultivator.client.screen.SettingsPage.FOKUS_BARIS, "kultivator.gui43.m10.langit.pengaturan");
		HubNav.buka(Tujuan.of(HubNav.PENGATURAN, Urutan.ID_TAMPILAN).fokus(fokus));
	}

	private LangitClient() {
	}
}
