package dev.kultivator.client.hub;

import net.minecraft.network.chat.Component;

/**
 * A live world context of the Gulungan Utama (DESIGN 43.8.1): the sheet {@code layar} a click in the world opened, the
 * entity it talks to ({@code npcId}, -1 for an anchor block, a prompt or a read-only hub view; the server keeps the anchor),
 * the Di Sini title ("Toko Senjata · Paman Li") and its glyph.
 */
public record Konteks(String layar, int npcId, Component judul, int glyph) {

	/** True when this context is the server session {@code layar} / {@code npcId}. */
	public boolean sama(String layar, int npcId) {
		return this.layar.equals(layar) && this.npcId == npcId;
	}
}
