package dev.kultivator.dunia.kota;

import dev.kultivator.dunia.DuniaAnchorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The towns the server has seen (DESIGN 34.5.1, 34.5.4): a town is named by its site id (the {@code kota/kota} or
 * {@code kota/kota_abadi} site of its structure start, the same for every anchor of one town), its centre is the square's
 * anchor (else the structure start), and its record lives in {@link KotaData} ({@code pos}, {@code dim}, {@code abadi}).
 * Towns are safe ground: no rival ambush starts within {@link #SAFE_RADIUS} blocks.
 */
public final class Towns {

	public static final int SAFE_RADIUS = 80;

	/** The town an anchor belongs to: its id (0 when the anchor is no town's). */
	public static long townOf(DuniaAnchorEntity anchor) {
		boolean abadi = anchor.what().startsWith("abadi") || anchor.structure().endsWith("kota_abadi");
		return anchor.siteOf(abadi ? "kota/kota_abadi" : "kota/kota");
	}

	public static boolean isAbadi(DuniaAnchorEntity anchor) {
		return anchor.what().startsWith("abadi") || anchor.structure().endsWith("kota_abadi");
	}

	/** Records the town (first anchor seen) and, for the square, its centre. */
	static void remember(ServerLevel level, DuniaAnchorEntity anchor, long town) {
		KotaData data = KotaData.get(level.getServer());
		CompoundTag tag = data.town(town);
		boolean changed = false;
		if (!tag.contains("dim")) {
			tag.putString("dim", level.dimension().identifier().toString());
			tag.putBoolean("abadi", isAbadi(anchor));
			BlockPos origin = anchor.origin() != null ? anchor.origin() : anchor.blockPosition();
			tag.putLong("pos", origin.asLong());
			changed = true;
		}
		if (("alun".equals(anchor.what()) || "abadi_gerbang".equals(anchor.what())) && !tag.getBooleanOr("centred", false)) {
			tag.putLong("pos", anchor.blockPosition().asLong());
			tag.putBoolean("centred", true);
			changed = true;
		}
		if (changed) {
			data.touch();
		}
	}

	/** The centre of a known town, or null. */
	public static @Nullable BlockPos centre(MinecraftServer server, long town) {
		CompoundTag tag = KotaData.get(server).town(town);
		return tag.contains("pos") ? BlockPos.of(tag.getLongOr("pos", 0L)) : null;
	}

	public static @Nullable ResourceKey<Level> dimension(MinecraftServer server, long town) {
		CompoundTag tag = KotaData.get(server).town(town);
		Identifier id = Identifier.tryParse(tag.getStringOr("dim", ""));
		return id == null || !tag.contains("dim") ? null : ResourceKey.create(Registries.DIMENSION, id);
	}

	/** The id of the known town whose centre is within {@code radius} of {@code pos} in {@code level}, or 0. */
	public static long near(ServerLevel level, BlockPos pos, int radius) {
		long best = 0L;
		double bestSq = (double) radius * radius;
		for (long id : known(level.getServer())) {
			CompoundTag tag = KotaData.get(level.getServer()).town(id);
			if (!level.dimension().identifier().toString().equals(tag.getStringOr("dim", ""))) {
				continue;
			}
			BlockPos centre = BlockPos.of(tag.getLongOr("pos", 0L));
			double dx = centre.getX() - pos.getX();
			double dz = centre.getZ() - pos.getZ();
			double d = dx * dx + dz * dz;
			if (d <= bestSq) {
				bestSq = d;
				best = id;
			}
		}
		return best;
	}

	/** True when {@code pos} lies inside a town (safe ground for ambushes). */
	public static boolean inTown(ServerLevel level, BlockPos pos) {
		return near(level, pos, SAFE_RADIUS) != 0L;
	}

	/** Every known town id. */
	public static List<Long> known(MinecraftServer server) {
		return new ArrayList<>(KotaData.get(server).townIds());
	}

	private Towns() {
	}
}
