package dev.kultivator.dunia.langit;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * SavedData {@code kultivator:dunia_langit} in the overworld (DESIGN 34.5.8-34.5.10, module L): per dimension the sky
 * schedule ({@code roll} the last day a sunset was rolled, {@code last} the last day an event ran) and the event that is
 * announced or running ({@code kind}, {@code start} and {@code end} in overworld clock ticks, {@code seed}), so a restart
 * in the middle of the night keeps the night; and the next treasure-birth day ({@code harta}). Treasure births
 * themselves and the spirit weather are never saved (births are memory-only live sites; the weather is a pure function of
 * the seed, the cell and the period).
 */
public final class LangitData extends SavedData {

	public static final Codec<LangitData> CODEC = CompoundTag.CODEC.xmap(LangitData::fromTag, LangitData::toTag);
	public static final SavedDataType<LangitData> TYPE = new SavedDataType<>(Kultivator.id("dunia_langit"), LangitData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** The saved sky state of one dimension. */
	static final class Dim {
		long rollDay = -1L;
		long lastEventDay = -100L;
		long nextBirthDay = -1L;
		/** -1 none, else a {@code SkyKind} ordinal. */
		int kind = -1;
		long start;
		long end;
		int seed;
		boolean forced;

		CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("roll", rollDay);
			tag.putLong("last", lastEventDay);
			tag.putLong("harta", nextBirthDay);
			tag.putInt("kind", kind);
			tag.putLong("start", start);
			tag.putLong("end", end);
			tag.putInt("seed", seed);
			tag.putBoolean("forced", forced);
			return tag;
		}

		static Dim fromTag(CompoundTag tag) {
			Dim dim = new Dim();
			dim.rollDay = tag.getLongOr("roll", -1L);
			dim.lastEventDay = tag.getLongOr("last", -100L);
			dim.nextBirthDay = tag.getLongOr("harta", -1L);
			dim.kind = tag.getIntOr("kind", -1);
			dim.start = tag.getLongOr("start", 0L);
			dim.end = tag.getLongOr("end", 0L);
			dim.seed = tag.getIntOr("seed", 0);
			dim.forced = tag.getBooleanOr("forced", false);
			return dim;
		}
	}

	private final Map<String, Dim> dims = new HashMap<>();

	public LangitData() {
	}

	static LangitData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The state of dimension {@code key} (its identifier string), created on first use. */
	Dim dim(String key) {
		return dims.computeIfAbsent(key, k -> new Dim());
	}

	@Nullable Dim peek(String key) {
		return dims.get(key);
	}

	void changed() {
		setDirty();
	}

	private static LangitData fromTag(CompoundTag tag) {
		LangitData data = new LangitData();
		CompoundTag all = tag.getCompoundOrEmpty("dims");
		for (String key : all.keySet()) {
			data.dims.put(key, Dim.fromTag(all.getCompoundOrEmpty(key)));
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag all = new CompoundTag();
		for (Map.Entry<String, Dim> entry : dims.entrySet()) {
			all.put(entry.getKey(), entry.getValue().toTag());
		}
		tag.put("dims", all);
		return tag;
	}
}
