package dev.kultivator.sect.build.api;

import net.minecraft.core.BlockPos;

/**
 * A Titik Formasi of a built sect plan (DESIGN 24.6.2): a flat, paved, building-free square plot with the carved centre
 * tile where a Mata Formasi goes. {@code id} is {@code formasi_<n>}, {@code centre} the carved centre tile itself (the Mata
 * Formasi stands on it, one block above, and the pattern is read around that cell), {@code radius} the plot's half size and
 * {@code tier} the template tier that builds it. {@code pola} is the spec id of the pattern requested here
 * ({@code requestPattern}) or empty, and {@code state} its component state ({@link ComponentView#STATE_QUEUED} ...
 * {@link ComponentView#STATE_DONE}, -1 without a request).
 */
public record FormasiAnchor(String id, BlockPos centre, int radius, int tier, String pola, int state) {

	public FormasiAnchor(String id, BlockPos centre, int radius, int tier) {
		this(id, centre, radius, tier, "", -1);
	}

	public FormasiAnchor {
		pola = pola == null ? "" : pola;
	}
}
