package dev.kultivator.client.disciple.power;

import dev.kultivator.client.ClientDisconnect;

/**
 * Client side of module C (DESIGN 20.4.3): the Bakat and Pusaka tabs ({@link TalentTab}, {@link TreasureTab}), the power
 * effects ({@link PowerFx}, ticked by {@code FxDispatcher}) and the looks the disciple renderer reads
 * ({@link PhysiqueVisuals}, {@link NatalTreasureLayer}, {@link DutyPoses}). Called once from {@code KultivatorClient}.
 */
public final class PowerClient {

	public static void init() {
		ClientDisconnect.register((handler, client) -> client.execute(() -> PowerFx.clear()));
	}

	private PowerClient() {
	}
}
