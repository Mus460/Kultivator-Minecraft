package dev.kultivator.client.dunia.tempat.jalan;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.dunia.tempat.jalan.JalanNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client init of 38W roads (DESIGN 38.C.6, 38.D.12): the road map payload {@code kultivator:jalan_peta} feeds
 * {@link JalanPeta}, the ink layer of known roads and capital plans on the world map (through
 * {@code PetaPainter.addOverlay}); the milestone payload {@code kultivator:jalan_papan} feeds {@link JalanPapan}, the
 * carved stone board over a stela (world text through {@code TeksDunia}, within its budget). The raft renderer belongs
 * to 38MD ({@code TempatModelClient}). Called once from {@code TempatClient.init()}. No post chain (Sodium and Iris
 * safe); client code only, never referenced by server code.
 */
public final class JalanClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		JalanPeta.init();
		JalanPapan.init();
		ClientPlayNetworking.registerGlobalReceiver(JalanNet.Peta.TYPE, (payload, context) -> JalanPeta.terima(payload));
		ClientPlayNetworking.registerGlobalReceiver(JalanNet.Papan.TYPE, (payload, context) -> JalanPapan.terima(payload));
		ClientDisconnect.register((handler, client) -> client.execute(() -> {
			JalanPeta.lupakan();
			JalanPapan.lupakan();
		}));
	}

	private JalanClient() {
	}
}
