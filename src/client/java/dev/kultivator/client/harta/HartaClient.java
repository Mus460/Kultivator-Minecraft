package dev.kultivator.client.harta;

/**
 * Client side of P7-harta (DESIGN 19.3.2-19.3.7), called once from {@code KultivatorClient}. Nothing needs registering
 * here: the item tooltips are written by the items themselves, the "[Shift] Bahan alkimia" lines by
 * {@code client.PillTooltips}, the core tints come from the item definitions ({@code minecraft:custom_model_data}),
 * the effects from {@code client.fx.HartaFx} (through {@code FxDispatcher}) and the treasure pings from
 * {@code client.fx.OrePingFx}. Kept as the module's client entry point for later hooks.
 */
public final class HartaClient {

	public static void init() {
	}

	private HartaClient() {
	}
}
