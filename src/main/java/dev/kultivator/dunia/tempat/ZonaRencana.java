package dev.kultivator.dunia.tempat;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The boxes of one planned district (DESIGN 38.D.4, 38.D.5): {@code kotak} the keep-out box (district pieces plus the
 * plan's margin), {@code aman} the safe-ground box (null when the district is not safe ground, e.g. the Kota Iblis
 * streets outside its Zona Perjanjian), and the district itself so callers can ask the shared terrain verdict.
 */
public record ZonaRencana(BoundingBox kotak, @Nullable BoundingBox aman, Distrik distrik) {
}
