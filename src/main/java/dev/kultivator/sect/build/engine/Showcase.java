package dev.kultivator.sect.build.engine;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Showcases (DESIGN 21.22 {@code uji sekte pamer}), SavedData {@code kultivator:sect_showcases}: at most
 * {@link #MAX_SHOWCASES} test builds placed anywhere (not halls), each a {@link Site} keyed by a showcase id with its own
 * {@link Undo} log, so {@code pamer hapus} restores the world exactly. They count as sites for overlap checks and the
 * Kehancuran zones, never as halls.
 */
public final class Showcase extends SavedData {

	public static final int MAX_SHOWCASES = 2;

	public static final Codec<Showcase> CODEC = CompoundTag.CODEC.xmap(Showcase::fromTag, Showcase::toTag);
	public static final SavedDataType<Showcase> TYPE = new SavedDataType<>(Kultivator.id("sect_showcases"), Showcase::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Showcase id -> saved site tag (plus the player who placed it under "by"). */
	final Map<UUID, CompoundTag> sites = new LinkedHashMap<>();
	/**
	 * Relocation orphans ("Pindah &amp; bongkar", DESIGN 21.18): orphan id -> saved site tag. The old site keeps coming down
	 * whenever its player is near, also after a restart; its cells belong to the orphan id, its refunds to the player.
	 */
	final Map<UUID, CompoundTag> orphans = new LinkedHashMap<>();

	public Showcase() {
	}

	public static Showcase get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	void put(UUID id, CompoundTag tag) {
		sites.put(id, tag);
		setDirty();
	}

	void remove(UUID id) {
		if (sites.remove(id) != null) {
			setDirty();
		}
	}

	void putOrphan(UUID id, CompoundTag tag) {
		orphans.put(id, tag);
		setDirty();
	}

	void removeOrphan(UUID id) {
		if (orphans.remove(id) != null) {
			setDirty();
		}
	}

	private static Showcase fromTag(CompoundTag tag) {
		Showcase data = new Showcase();
		read(tag.getCompoundOrEmpty("showcases"), data.sites);
		read(tag.getCompoundOrEmpty("orphans"), data.orphans);
		return data;
	}

	private static void read(CompoundTag all, Map<UUID, CompoundTag> into) {
		for (String key : all.keySet()) {
			try {
				into.put(UUID.fromString(key), all.getCompoundOrEmpty(key).copy());
			} catch (IllegalArgumentException exception) {
				Kultivator.LOGGER.warn("Skipping a sect showcase or orphan site with a broken id: {}", key);
			}
		}
	}

	private CompoundTag toTag() {
		CompoundTag all = new CompoundTag();
		sites.forEach((id, tag) -> all.put(id.toString(), tag.copy()));
		CompoundTag orphanTag = new CompoundTag();
		orphans.forEach((id, tag) -> orphanTag.put(id.toString(), tag.copy()));
		CompoundTag tag = new CompoundTag();
		tag.put("showcases", all);
		tag.put("orphans", orphanTag);
		return tag;
	}
}
