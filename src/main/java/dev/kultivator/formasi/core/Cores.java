package dev.kultivator.formasi.core;

import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read access to registered and virtual cores (DESIGN 24.14.3), backed by {@link CoreStore}. Views are snapshots; only
 * {@link #setExt} writes (module B's extension fields, saved with the record).
 */
public final class Cores {

	/** Every core of {@code owner} in all loaded dimensions. */
	public static List<CoreView> of(UUID owner) {
		List<CoreView> list = new ArrayList<>();
		for (CoreStore store : CoreTicker.stores()) {
			for (CoreRecord record : store.all()) {
				if (record.owner.equals(owner)) {
					list.add(CoreTicker.view(record));
				}
			}
		}
		return list;
	}

	/** The core whose eye is at {@code eye}, or null. */
	public static @Nullable CoreView at(ServerLevel level, BlockPos eye) {
		CoreRecord record = CoreTicker.store(level).atEye(eye);
		return record == null ? null : CoreTicker.view(record);
	}

	public static @Nullable CoreView byId(ResourceKey<Level> dim, int id) {
		CoreRecord record = CoreTicker.record(dim, id);
		return record == null ? null : CoreTicker.view(record);
	}

	/** Running cores whose field covers {@code pos}, optionally of one family. */
	public static List<CoreView> covering(ServerLevel level, Vec3 pos, @Nullable Family family) {
		List<CoreView> list = new ArrayList<>();
		for (CoreRecord record : CoreTicker.store(level).all()) {
			if (!record.state.running()) {
				continue;
			}
			FormationSpec spec = FormationRegistry.byId(record.spec);
			if (spec != null && (family == null || spec.family() == family) && CoreTicker.covers(level, record, spec, pos)) {
				list.add(CoreTicker.view(record));
			}
		}
		return list;
	}

	/** Replaces a core's module-B extension fields (saved with the record). */
	public static void setExt(ResourceKey<Level> dim, int id, CompoundTag ext) {
		ServerLevel level = CoreTicker.level(dim);
		if (level == null) {
			return;
		}
		CoreRecord record = CoreTicker.store(level).byId(id);
		if (record != null) {
			record.ext = ext == null ? new CompoundTag() : ext.copy();
			CoreTicker.markDirty(level);
		}
	}

	private Cores() {
	}
}
