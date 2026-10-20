package dev.kultivator.kehancuran;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mode Kehancuran's records (DESIGN 20.3.21): every destructive cast and restore is written to the server log
 * ({@code [Kultivator] Kehancuran: master/disciple shape dim x y z -> N blocks}), the hall's Catatan tablet
 * ({@code Halls.log}) and the hall's {@code kehancuran} section, and a global list in the overworld data
 * {@code kultivator:kehancuran}. It also remembers where every Sect Hall stele was seen, so the 64-block hall zone holds
 * for halls whose owners are offline. Undo buffers are never saved (they live in {@link WorldBreaker}).
 */
public final class KehancuranLog extends SavedData {

	public static final int MAX_HALL_LOG = 50;
	public static final int MAX_GLOBAL_LOG = 100;

	public static final Codec<KehancuranLog> CODEC = CompoundTag.CODEC.xmap(KehancuranLog::new, KehancuranLog::write);
	public static final SavedDataType<KehancuranLog> TYPE = new SavedDataType<>(Kultivator.id("kehancuran"), KehancuranLog::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, GlobalPos> halls = new HashMap<>();
	private final Set<GlobalPos> steles = new LinkedHashSet<>();
	private final ListTag log = new ListTag();

	public KehancuranLog() {
	}

	private KehancuranLog(CompoundTag tag) {
		for (Tag entry : tag.getListOrEmpty("halls")) {
			if (!(entry instanceof CompoundTag hall)) {
				continue;
			}
			try {
				UUID owner = UUID.fromString(hall.getStringOr("owner", ""));
				halls.put(owner, readPos(hall));
			} catch (RuntimeException ignored) {
				// A broken entry is dropped.
			}
		}
		for (Tag entry : tag.getListOrEmpty("steles")) {
			if (entry instanceof CompoundTag stele) {
				try {
					steles.add(readPos(stele));
				} catch (RuntimeException ignored) {
					// A broken entry is dropped.
				}
			}
		}
		for (Tag entry : tag.getListOrEmpty("log")) {
			if (entry instanceof CompoundTag line) {
				log.add(line.copy());
			}
		}
	}

	private static GlobalPos readPos(CompoundTag tag) {
		ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("dim", "minecraft:overworld")));
		return GlobalPos.of(dim, new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0)));
	}

	private static CompoundTag writePos(GlobalPos pos) {
		CompoundTag tag = new CompoundTag();
		tag.putString("dim", pos.dimension().identifier().toString());
		tag.putInt("x", pos.pos().getX());
		tag.putInt("y", pos.pos().getY());
		tag.putInt("z", pos.pos().getZ());
		return tag;
	}

	private CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		halls.forEach((owner, pos) -> {
			CompoundTag hall = writePos(pos);
			hall.putString("owner", owner.toString());
			list.add(hall);
		});
		tag.put("halls", list);
		ListTag steleList = new ListTag();
		for (GlobalPos pos : steles) {
			steleList.add(writePos(pos));
		}
		tag.put("steles", steleList);
		tag.put("log", log.copy());
		return tag;
	}

	public static KehancuranLog get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	void noteHall(UUID owner, GlobalPos pos) {
		if (!pos.equals(halls.get(owner))) {
			halls.put(owner, pos);
			setDirty();
		}
	}

	/** A Sect Hall stele was placed (any player, even an inert second stele): its zone holds while it stands. */
	void noteStele(GlobalPos pos) {
		if (steles.add(pos)) {
			setDirty();
		}
	}

	/** A stele was removed: its own zone ends (a hall's remembered position stays in {@link #halls}). */
	void forgetStele(GlobalPos pos) {
		if (steles.remove(pos)) {
			setDirty();
		}
	}

	/**
	 * Every known hall stele (active or not): the current hall of every owner in {@code HallData} (online or not, and noted
	 * here so it survives a move), every position remembered here, and every stele ever placed that still stands.
	 */
	static List<GlobalPos> knownHalls(MinecraftServer server) {
		KehancuranLog data = get(server);
		Set<GlobalPos> known = new LinkedHashSet<>();
		for (UUID owner : HallData.get(server).owners()) {
			Halls.HallView view = Halls.hallOf(owner);
			if (view != null && view.pos() != null && view.dim() != null) {
				GlobalPos pos = GlobalPos.of(view.dim(), view.pos());
				data.noteHall(owner, pos);
				known.add(pos);
			}
		}
		known.addAll(data.halls.values());
		known.addAll(data.steles);
		return new ArrayList<>(known);
	}

	/** Writes one finished cast or restore everywhere it belongs. */
	static void record(MinecraftServer server, UUID owner, String ownerName, String disciple, String shape, ResourceKey<Level> dim, BlockPos pos,
			int blocks, boolean restore) {
		String dimension = dim.identifier().toString();
		Kultivator.LOGGER.info("[Kultivator] Kehancuran: {}/{} {} {} {} {} {} -> {} blocks{}", ownerName, disciple, shape, dimension,
				pos.getX(), pos.getY(), pos.getZ(), blocks, restore ? " (restored)" : "");
		long day = server.overworld().getOverworldClockTime() / 24000L;
		CompoundTag line = new CompoundTag();
		line.putString("owner", owner.toString());
		line.putString("disciple", disciple);
		line.putString("shape", shape);
		line.putString("dim", dimension);
		line.putInt("x", pos.getX());
		line.putInt("y", pos.getY());
		line.putInt("z", pos.getZ());
		line.putInt("blocks", blocks);
		line.putBoolean("restore", restore);
		line.putLong("day", day);
		Halls.log(owner, Component.translatable(restore ? "kultivator.kehancuran.log.restore" : "kultivator.kehancuran.log.cast", disciple,
				Component.translatable("kultivator.kehancuran.shape." + shape), pos.getX(), pos.getY(), pos.getZ(), blocks));
		CompoundTag section = Halls.section(owner, "kehancuran");
		ListTag hallLog = section.getListOrEmpty("log").copy();
		hallLog.add(line.copy());
		while (hallLog.size() > MAX_HALL_LOG) {
			hallLog.remove(0);
		}
		section.put("log", hallLog);
		Halls.setDirty();
		KehancuranLog data = get(server);
		data.log.add(line);
		while (data.log.size() > MAX_GLOBAL_LOG) {
			data.log.remove(0);
		}
		data.setDirty();
	}

	/**
	 * Writes one finished self cast (DESIGN 22.11.7): the server log line
	 * {@code [Kultivator] Kehancuran: <player> self:<spec> <dim> x y z -> N blocks}, the Catatan tablet and the global log
	 * (with {@code self} and {@code spec}, so the Kitab Teknik's Kehancuran page can list it).
	 */
	static void recordSelf(MinecraftServer server, UUID owner, String ownerName, String specId, String shape, ResourceKey<Level> dim, BlockPos pos,
			int blocks) {
		String dimension = dim.identifier().toString();
		Kultivator.LOGGER.info("[Kultivator] Kehancuran: {} self:{} {} {} {} {} -> {} blocks", ownerName, specId, dimension, pos.getX(), pos.getY(),
				pos.getZ(), blocks);
		long day = server.overworld().getOverworldClockTime() / 24000L;
		CompoundTag line = new CompoundTag();
		line.putString("owner", owner.toString());
		line.putString("disciple", ownerName);
		line.putString("shape", shape);
		line.putBoolean("self", true);
		line.putString("spec", specId);
		line.putString("dim", dimension);
		line.putInt("x", pos.getX());
		line.putInt("y", pos.getY());
		line.putInt("z", pos.getZ());
		line.putInt("blocks", blocks);
		line.putBoolean("restore", false);
		line.putLong("day", day);
		dev.kultivator.data.TechniqueSpec spec = dev.kultivator.data.TechniqueRegistry.byId(specId);
		Halls.log(owner, Component.translatable("kultivator.kehancuran_diri.log.cast", spec == null ? Component.literal(specId) : spec.displayName(),
				pos.getX(), pos.getY(), pos.getZ(), blocks));
		CompoundTag section = Halls.section(owner, "kehancuran");
		ListTag hallLog = section.getListOrEmpty("log").copy();
		hallLog.add(line.copy());
		while (hallLog.size() > MAX_HALL_LOG) {
			hallLog.remove(0);
		}
		section.put("log", hallLog);
		Halls.setDirty();
		KehancuranLog data = get(server);
		data.log.add(line);
		while (data.log.size() > MAX_GLOBAL_LOG) {
			data.log.remove(0);
		}
		data.setDirty();
	}

	/**
	 * Phase 10b (DESIGN 29.16.7 "Finish", 29.16.9): a permanent Titah carve (Tingkat 6-9) that Pulihkan cannot restore. One
	 * WARN line in the server log, a Catatan tablet line, and {@code perm: true} (with the Tingkat and radius) on the saved
	 * line {@link #recordSelf} just wrote for it (a new saved line when there is none), so
	 * {@code /kultivasi kehancuran status} can list it.
	 */
	static void recordPermanent(MinecraftServer server, UUID owner, String ownerName, String specId, int tier, ResourceKey<Level> dim, BlockPos pos,
			int radius, long blocks) {
		String dimension = dim.identifier().toString();
		Kultivator.LOGGER.warn("[Kultivator] Kehancuran PERMANENT: {} self:{} tingkat {} {} {} {} {} r {} -> {} blocks (cannot be restored)", ownerName,
				specId, tier, dimension, pos.getX(), pos.getY(), pos.getZ(), radius, blocks);
		int count = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, blocks));
		Halls.log(owner, Component.translatable("kultivator.kehancuran.bulk.log.permanent", tier, pos.getX(), pos.getY(), pos.getZ(), count));
		KehancuranLog data = get(server);
		String id = owner.toString();
		for (int i = data.log.size() - 1; i >= 0; i--) {
			if (data.log.get(i) instanceof CompoundTag line && id.equals(line.getStringOr("owner", "")) && specId.equals(line.getStringOr("spec", ""))
					&& line.getIntOr("x", 0) == pos.getX() && line.getIntOr("y", 0) == pos.getY() && line.getIntOr("z", 0) == pos.getZ()
					&& !line.getBooleanOr("perm", false)) {
				line.putBoolean("perm", true);
				line.putInt("tier", tier);
				line.putInt("radius", radius);
				data.setDirty();
				return;
			}
		}
		CompoundTag line = new CompoundTag();
		line.putString("owner", id);
		line.putString("disciple", ownerName);
		line.putString("shape", "bowl");
		line.putBoolean("self", true);
		line.putString("spec", specId);
		line.putString("dim", dimension);
		line.putInt("x", pos.getX());
		line.putInt("y", pos.getY());
		line.putInt("z", pos.getZ());
		line.putInt("blocks", count);
		line.putBoolean("restore", false);
		line.putBoolean("perm", true);
		line.putInt("tier", tier);
		line.putInt("radius", radius);
		line.putLong("day", server.overworld().getOverworldClockTime() / 24000L);
		data.log.add(line);
		while (data.log.size() > MAX_GLOBAL_LOG) {
			data.log.remove(0);
		}
		data.setDirty();
	}

	/** The owner's last {@code max} permanent carves ({@code perm: true}), newest first. */
	static List<CompoundTag> permanent(MinecraftServer server, UUID owner, int max) {
		List<CompoundTag> list = new ArrayList<>();
		ListTag source = get(server).log;
		String id = owner.toString();
		for (int i = source.size() - 1; i >= 0 && list.size() < max; i--) {
			if (source.get(i) instanceof CompoundTag line && id.equals(line.getStringOr("owner", "")) && line.getBooleanOr("perm", false)) {
				list.add(line);
			}
		}
		return list;
	}

	/** The owner's last {@code max} records, newest first. */
	static List<CompoundTag> recent(MinecraftServer server, UUID owner, int max) {
		List<CompoundTag> list = new ArrayList<>();
		ListTag source = get(server).log;
		String id = owner.toString();
		for (int i = source.size() - 1; i >= 0 && list.size() < max; i--) {
			if (source.get(i) instanceof CompoundTag line && id.equals(line.getStringOr("owner", ""))) {
				list.add(line);
			}
		}
		return list;
	}
}
