package dev.kultivator.dunia.tempat;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * A place of DESIGN 38 as the server knows it (contract C38.W1): the published site id, its kind
 * ({@code kota/kota_besar}, {@code kampung/desa}, {@code sekte/kompleks}, {@code gua/pertapa}, {@code pos/singgah},
 * {@code kota_tema/pelabuhan}, ...), its box, its strength (protection / defence tier used by 38.R and 38.T.11) and
 * the district at the asked position (null outside districted places).
 */
public record TempatInfo(long situsId, String jenis, BoundingBox kotak, int kekuatan, @Nullable String distrik) {
}
