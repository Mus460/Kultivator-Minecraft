package dev.kultivator.client.dunia;

import dev.kultivator.network.Payloads;
import net.minecraft.nbt.CompoundTag;

/**
 * The spine's client side of the phase-8/9 Dunia data (DESIGN 25.2 items 2 and 18, 25.10): routes each {@code DuniaSync}
 * channel to its module's client class and remembers which channels the server refused as too big ({@link #tooBig}).
 * Opening the old Dunia/Keabadian tabs is {@code HubNav.tujuanDunia/tujuanAbadi} (DESIGN 43.4.4).
 */
public final class DuniaClient {

	/** Channels whose last sync was refused by the server as too big (the module keeps its previous data). */
	private static final boolean[] TOO_BIG = new boolean[Payloads.DuniaSync.CHANNELS];
	/** One client warning per channel per session. */
	private static final boolean[] WARNED = new boolean[Payloads.DuniaSync.CHANNELS];

	/** Routes one sync to its module ({@code KultivatorClient} calls this on the client thread). */
	public static void onSync(Payloads.DuniaSync payload) {
		CompoundTag data = payload.data();
		int channel = payload.channel();
		if (channel < 0 || channel >= Payloads.DuniaSync.CHANNELS) {
			return;
		}
		if (data != null && data.getBooleanOr("too_big", false)) {
			// The server refused an over-budget sync: keep the module's previous data and let the page say so.
			TOO_BIG[channel] = true;
			if (!WARNED[channel]) {
				WARNED[channel] = true;
				dev.kultivator.Kultivator.LOGGER.warn("DuniaSync channel {} was too big for the server budget; keeping the previous data", channel);
			}
			return;
		}
		TOO_BIG[channel] = false;
		switch (channel) {
			case Payloads.DuniaSync.CH_ALAM -> dev.kultivator.client.alam.AlamClient.onSync(data);
			case Payloads.DuniaSync.CH_MUSUH -> dev.kultivator.client.musuh.MusuhClient.onSync(data);
			case Payloads.DuniaSync.CH_TEMPA -> dev.kultivator.client.tempa.TempaClient.onSync(data);
			case Payloads.DuniaSync.CH_DESA -> dev.kultivator.client.desa.DesaClient.onSync(data);
			// Phase 9 (DESIGN 26.14 item 2).
			case Payloads.DuniaSync.CH_ABADI -> dev.kultivator.client.abadi.AbadiClient.onSync(data);
			case Payloads.DuniaSync.CH_PURBA -> dev.kultivator.client.purba.PurbaClient.onSync(data);
			case Payloads.DuniaSync.CH_BATIN -> dev.kultivator.client.batin.BatinClient.onSync(data);
			default -> {
			}
		}
	}

	/** True while the last sync of {@code channel} was refused as too big (the page shows its previous data and a warning line). */
	public static boolean tooBig(int channel) {
		return channel >= 0 && channel < TOO_BIG.length && TOO_BIG[channel];
	}

	private DuniaClient() {
	}
}
