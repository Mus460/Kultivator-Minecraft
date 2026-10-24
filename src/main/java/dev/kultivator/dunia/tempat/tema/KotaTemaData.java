package dev.kultivator.dunia.tempat.tema;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:kota_tema} in the overworld (DESIGN 38.T.14.2): the persistent state of every themed town.
 * New worlds only: no migration, no version field.
 * <p>
 * Ownership inside the file (matrix row 23 of {@code jadwal-38-39-40.md}): theme agents never edit this class; each
 * keeps its state in {@link #bagian(String) bagian(tema)} (one {@link CompoundTag} per theme id, e.g. pending keels and
 * ship cycles for {@code pelabuhan}, murals and storm days for {@code oasis}, robe orders for {@code air}, porter
 * deposits for {@code gunung}, arena ladders, captives and the city purse for {@code iblis}) and calls {@link #ubah()}
 * after changing it. Per-town state goes under {@link #kota(String, long) kota(tema, situsId)}. 38TK inti owns
 * {@link #inti()} (trade prices seen, saturation, weekly profit, cargo contracts, bounties, world quest state) and the
 * two meters below.
 * <p>
 * Meters: Infami (0-100, per player, global, 38.T.7.8 butir 4) and Gentar (0-100, per player per Kota Iblis site,
 * butir 3). Decay and sources are 38TK inti's (W2); this class only stores and clamps.
 */
public final class KotaTemaData extends SavedData {

	public static final Codec<KotaTemaData> CODEC = CompoundTag.CODEC.xmap(KotaTemaData::fromTag, KotaTemaData::toTag);
	public static final SavedDataType<KotaTemaData> TYPE = new SavedDataType<>(Kultivator.id("kota_tema"), KotaTemaData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** The {@link #inti()} section key. */
	public static final String INTI = "inti";

	private final Map<String, CompoundTag> bagian = new HashMap<>();
	private final Map<UUID, Integer> infami = new HashMap<>();
	private final Map<UUID, Map<Long, Integer>> gentar = new HashMap<>();

	public KotaTemaData() {
	}

	/** The world's data (overworld storage), created on first use. */
	public static KotaTemaData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * The live state compound of one theme ({@code pelabuhan}, {@code oasis}, {@code air}, {@code gunung}, {@code iblis};
	 * also {@code bersama} for the shared bersama package). Created empty on first use. Change it in place, then call
	 * {@link #ubah()}.
	 */
	public CompoundTag bagian(String tema) {
		String key = tema == null || tema.isEmpty() ? "bersama" : tema;
		return bagian.computeIfAbsent(key, k -> new CompoundTag());
	}

	/** The live state compound of one town of a theme: {@code bagian(tema)["kota_<situsId>"]}, created on first use. */
	public CompoundTag kota(String tema, long situsId) {
		CompoundTag part = bagian(tema);
		String key = "kota_" + situsId;
		CompoundTag town = part.getCompoundOrEmpty(key);
		if (!part.contains(key)) {
			part.put(key, town);
		}
		return town;
	}

	/** 38TK inti's own section (trade book, contracts, bounties, world quest state). */
	public CompoundTag inti() {
		return bagian(INTI);
	}

	/** Marks the data dirty after a change to any compound returned above. */
	public void ubah() {
		setDirty();
	}

	// ------------------------------------------------------------------ meters

	/** Infami 0-100 of a player (0 when never set). */
	public int infami(UUID player) {
		return infami.getOrDefault(player, 0);
	}

	/** Sets Infami (clamped 0-100); returns the stored value. */
	public int setInfami(UUID player, int value) {
		int v = Mth.clamp(value, 0, 100);
		if (v == 0) {
			infami.remove(player);
		} else {
			infami.put(player, v);
		}
		setDirty();
		return v;
	}

	/** Gentar 0-100 of a player in the Kota Iblis {@code situsId} (0 when never set). */
	public int gentar(UUID player, long situsId) {
		Map<Long, Integer> sites = gentar.get(player);
		return sites == null ? 0 : sites.getOrDefault(situsId, 0);
	}

	/** Sets Gentar in one Kota Iblis (clamped 0-100); returns the stored value. */
	public int setGentar(UUID player, long situsId, int value) {
		int v = Mth.clamp(value, 0, 100);
		Map<Long, Integer> sites = gentar.computeIfAbsent(player, k -> new HashMap<>());
		if (v == 0) {
			sites.remove(situsId);
			if (sites.isEmpty()) {
				gentar.remove(player);
			}
		} else {
			sites.put(situsId, v);
		}
		setDirty();
		return v;
	}

	// ------------------------------------------------------------------ codec

	private static KotaTemaData fromTag(CompoundTag tag) {
		KotaTemaData data = new KotaTemaData();
		CompoundTag parts = tag.getCompoundOrEmpty("bagian");
		for (String key : parts.keySet()) {
			data.bagian.put(key, parts.getCompoundOrEmpty(key).copy());
		}
		CompoundTag inf = tag.getCompoundOrEmpty("infami");
		for (String key : inf.keySet()) {
			UUID id = uuid(key);
			if (id != null) {
				data.infami.put(id, Mth.clamp(inf.getIntOr(key, 0), 0, 100));
			}
		}
		CompoundTag gen = tag.getCompoundOrEmpty("gentar");
		for (String key : gen.keySet()) {
			UUID id = uuid(key);
			if (id == null) {
				continue;
			}
			CompoundTag sites = gen.getCompoundOrEmpty(key);
			Map<Long, Integer> map = new HashMap<>();
			for (String site : sites.keySet()) {
				try {
					map.put(Long.parseLong(site), Mth.clamp(sites.getIntOr(site, 0), 0, 100));
				} catch (NumberFormatException ignored) {
					// not a site id
				}
			}
			if (!map.isEmpty()) {
				data.gentar.put(id, map);
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag parts = new CompoundTag();
		for (Map.Entry<String, CompoundTag> entry : bagian.entrySet()) {
			parts.put(entry.getKey(), entry.getValue().copy());
		}
		tag.put("bagian", parts);
		CompoundTag inf = new CompoundTag();
		for (Map.Entry<UUID, Integer> entry : infami.entrySet()) {
			inf.putInt(entry.getKey().toString(), entry.getValue());
		}
		tag.put("infami", inf);
		CompoundTag gen = new CompoundTag();
		for (Map.Entry<UUID, Map<Long, Integer>> entry : gentar.entrySet()) {
			CompoundTag sites = new CompoundTag();
			for (Map.Entry<Long, Integer> site : entry.getValue().entrySet()) {
				sites.putInt(Long.toString(site.getKey()), site.getValue());
			}
			gen.put(entry.getKey().toString(), sites);
		}
		tag.put("gentar", gen);
		return tag;
	}

	private static UUID uuid(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
