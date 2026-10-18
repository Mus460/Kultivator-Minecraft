package dev.kultivator.sect.build.api;

import net.minecraft.core.BlockPos;

/**
 * A Titik Formasi of a built sect plan (DESIGN 24.6.2): a flat, paved, building-free square plot with the carved centre
 * tile where a Mata Formasi goes. {@code id} is {@code formasi_<n>}, {@code radius} the plot's half size and {@code tier}
 * the template tier that builds it.
 */
public record FormasiAnchor(String id, BlockPos centre, int radius, int tier) {
}
