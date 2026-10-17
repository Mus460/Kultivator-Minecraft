package dev.kultivator.client.disciple;

import dev.kultivator.network.Payloads;

/** STUB — owned by P5-disciple-client. Disciple renderer registration, screen and detail cache (DESIGN 18). */
public final class DiscipleClient {

	public static void init() {
	}

	/** Details for a disciple's screen arrived. */
	public static void onInfo(Payloads.DiscipleInfo info) {
	}

	/** The server asks to open the screen of the disciple with this entity id. */
	public static void openScreen(int entityId) {
	}

	private DiscipleClient() {
	}
}
