package dev.kultivator.client.dunia;

import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.nbt.CompoundTag;

/**
 * The spine's client side of the phase-8 Dunia data (DESIGN 25.2 items 2 and 18, 25.10): routes each {@code DuniaSync}
 * channel to its module's client class and counts a version per channel so a shown page notices new data. The old Dunia
 * tabs open as places in the Gulungan ({@link #open}, DESIGN 43.4.4).
 */
public final class DuniaClient {

	private static final long[] VERSIONS = new long[Payloads.DuniaSync.CHANNELS];
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
			VERSIONS[channel]++;
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
				return;
			}
		}
		VERSIONS[payload.channel()]++;
	}

	/** True while the last sync of {@code channel} was refused as too big (the page shows its previous data and a warning line). */
	public static boolean tooBig(int channel) {
		return channel >= 0 && channel < TOO_BIG.length && TOO_BIG[channel];
	}

	/** The number of syncs received on {@code channel} (0-6) so far. */
	public static long version(int channel) {
		return channel >= 0 && channel < VERSIONS.length ? VERSIONS[channel] : 0L;
	}

	/** The sum of every channel's version (any change). */
	public static long version() {
		long sum = 0L;
		for (long each : VERSIONS) {
			sum += each;
		}
		return sum;
	}

	/** Asks every phase-8 module for its data (the "sync" op of actions 38-41). */
	public static void requestAll() {
		dev.kultivator.client.Keys.send(new Payloads.Action(Actions.ALAM, 0, "sync"));
		dev.kultivator.client.Keys.send(new Payloads.Action(Actions.MUSUH, 0, "sync"));
		dev.kultivator.client.Keys.send(new Payloads.Action(Actions.TEMPA, 0, "sync"));
		dev.kultivator.client.Keys.send(new Payloads.Action(Actions.DESA, 0, "sync"));
	}

	/** Asks every phase-9 module for its data (the "sync" op of actions 42-44; the Keabadian screen). */
	public static void requestAbadi() {
		dev.kultivator.client.Keys.send(new Payloads.Action(Actions.ABADI, 0, "sync"));
		dev.kultivator.client.Keys.send(new Payloads.Action(Actions.PURBA, 0, "sync"));
		dev.kultivator.client.Keys.send(new Payloads.Action(Actions.BATIN, 0, "sync"));
	}

	/**
	 * Opens the Gulungan on old Dunia tab {@code tab} ({@code Payloads.OpenScreen.DUNIA_*}, DESIGN 43.4.4): Dunia › Alam,
	 * Diri › Pusaka, Dunia › Musuh, Sekte › Wilayah › Desa & Pakta, Dunia › Duel. The Dunia screen itself is gone.
	 */
	public static void open(int tab) {
		dev.kultivator.client.hub.HubNav.buka(dev.kultivator.client.hub.HubNav.tujuanDunia(tab));
	}

	/**
	 * Opens the Gulungan on old Keabadian tab {@code tab} ({@code Payloads.OpenScreen.ABADI_*}, DESIGN 43.4.4): its pages
	 * live in the Keabadian section (M16), so no screen of its own is opened (and nothing here depends on that screen).
	 */
	public static void openAbadi(int tab) {
		dev.kultivator.client.hub.HubNav.buka(dev.kultivator.client.hub.HubNav.tujuanAbadi(tab));
	}

	private DuniaClient() {
	}
}
