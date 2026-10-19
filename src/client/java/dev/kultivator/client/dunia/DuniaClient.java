package dev.kultivator.client.dunia;

import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/**
 * The spine's client side of the phase-8 Dunia screen (DESIGN 25.2 items 2 and 18, 25.10): routes each {@code DuniaSync}
 * channel to its module's client class, counts a version per channel so an open screen notices new data, and opens the
 * screen (asking every module for its data first).
 */
public final class DuniaClient {

	private static final long[] VERSIONS = new long[Payloads.DuniaSync.CHANNELS];

	/** Routes one sync to its module ({@code KultivatorClient} calls this on the client thread). */
	public static void onSync(Payloads.DuniaSync payload) {
		CompoundTag data = payload.data();
		switch (payload.channel()) {
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

	/** Opens the Dunia screen on {@code tab} ({@code Payloads.OpenScreen.DUNIA_*}), or switches the open one to it. */
	public static void open(int tab) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof DuniaScreen screen && screen.getClass() == DuniaScreen.class) {
			screen.showTab(tab);
			return;
		}
		minecraft.gui.setScreen(new DuniaScreen(tab));
	}

	/** Opens the Keabadian screen on {@code tab} ({@code Payloads.OpenScreen.ABADI_*}), or switches the open one to it. */
	public static void openAbadi(int tab) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof dev.kultivator.client.keabadian.KeabadianScreen screen) {
			screen.showTab(tab);
			return;
		}
		minecraft.gui.setScreen(new dev.kultivator.client.keabadian.KeabadianScreen(tab));
	}

	private DuniaClient() {
	}
}
