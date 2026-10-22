package dev.kultivator.client.sekte.seragam;

import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Whose disciple a render state belongs to (DESIGN 36.2.8): {@code DiscipleRobeLayer} only sees the
 * {@code DiscipleRenderState}, which carries no entity, so every 10 client ticks this keeps the disciples within
 * {@link #RANGE} blocks of the player (at most {@link #MAX}) with their synced master ({@code DiscipleEntity.ownerId()}),
 * and {@link #ownerAt} matches a render state's position to the nearest of them (within 1.5 blocks; the state is
 * interpolated between two ticks, never further than one tick of movement away). O(n) per robe, n ≤ 48.
 */
public final class SeragamOwners {

	private static final double RANGE = 64.0D;
	private static final int MAX = 48;
	private static final double MATCH2 = 1.5D * 1.5D;

	private record Known(DiscipleEntity entity, UUID owner) {
	}

	private static final List<Known> KNOWN = new ArrayList<>();

	static void tick() {
		KNOWN.clear();
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		for (DiscipleEntity disciple : mc.level.getEntitiesOfClass(DiscipleEntity.class, player.getBoundingBox().inflate(RANGE))) {
			UUID owner = disciple.ownerId();
			if (owner != null && disciple.role() == DiscipleEntity.Role.DISCIPLE) {
				KNOWN.add(new Known(disciple, owner));
				if (KNOWN.size() >= MAX) {
					break;
				}
			}
		}
	}

	/** The master of the disciple drawn at (x, y, z), or null. */
	public static @Nullable UUID ownerAt(double x, double y, double z) {
		Known best = null;
		double bestD = MATCH2;
		for (Known known : KNOWN) {
			DiscipleEntity e = known.entity();
			if (e.isRemoved()) {
				continue;
			}
			double dx = e.getX() - x;
			double dy = e.getY() - y;
			double dz = e.getZ() - z;
			double d = dx * dx + dy * dy + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = known;
			}
		}
		return best == null ? null : best.owner();
	}

	static void clear() {
		KNOWN.clear();
	}

	private SeragamOwners() {
	}
}
