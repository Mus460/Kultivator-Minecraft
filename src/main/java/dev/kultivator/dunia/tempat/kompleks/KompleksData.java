package dev.kultivator.dunia.tempat.kompleks;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the NPC sect compounds remember ({@code kultivator:kompleks}, overworld storage; DESIGN 38.C.3, owner 38DS):
 * <ul>
 *   <li>named spots per sect (the anchors' real block positions once their chunks loaded: {@code puncak},
 *       {@code arena}, {@code gerbang}, {@code aula}, {@code dalam}, {@code dermaga_awan}, {@code pasar}), so
 *       {@code TempatApi.spot} answers for a compound that is not loaded right now;</li>
 *   <li>the day each player last earned standing from a friendly bout with each sect (one per day, 38.C.3.3).</li>
 * </ul>
 * New worlds only: no version field, no migration.
 */
public final class KompleksData extends SavedData {

	public static final Codec<KompleksData> CODEC = CompoundTag.CODEC.xmap(KompleksData::fromTag, KompleksData::toTag);
	public static final SavedDataType<KompleksData> TYPE = new SavedDataType<>(Kultivator.id("kompleks"), KompleksData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** sect id -> spot name -> packed block position. */
	private final Map<String, Map<String, Long>> spots = new HashMap<>();
	/** "uuid|sect" -> world day of the last standing earned by sparring. */
	private final Map<String, Long> tanding = new HashMap<>();

	public KompleksData() {
	}

	public static KompleksData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Remembers spot {@code nama} of sect {@code sekte} (no-op when unchanged). */
	public void spot(String sekte, String nama, BlockPos pos) {
		Map<String, Long> of = spots.computeIfAbsent(sekte, k -> new HashMap<>());
		Long old = of.put(nama, pos.asLong());
		if (old == null || old != pos.asLong()) {
			setDirty();
		}
	}

	public @Nullable BlockPos spot(String sekte, String nama) {
		Map<String, Long> of = spots.get(sekte);
		Long packed = of == null ? null : of.get(nama);
		return packed == null ? null : BlockPos.of(packed);
	}

	/** True (and remembered) when {@code player} may still earn sparring standing with {@code sekte} on {@code day}. */
	public boolean ambilTanding(UUID player, String sekte, long day) {
		String key = player + "|" + sekte;
		Long last = tanding.get(key);
		if (last != null && last >= day) {
			return false;
		}
		tanding.put(key, day);
		setDirty();
		return true;
	}

	private static KompleksData fromTag(CompoundTag tag) {
		KompleksData data = new KompleksData();
		CompoundTag s = tag.getCompoundOrEmpty("spots");
		for (String sekte : s.keySet()) {
			CompoundTag of = s.getCompoundOrEmpty(sekte);
			Map<String, Long> map = new HashMap<>();
			for (String nama : of.keySet()) {
				map.put(nama, of.getLongOr(nama, 0L));
			}
			data.spots.put(sekte, map);
		}
		CompoundTag t = tag.getCompoundOrEmpty("tanding");
		for (String key : t.keySet()) {
			data.tanding.put(key, t.getLongOr(key, 0L));
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag s = new CompoundTag();
		spots.forEach((sekte, of) -> {
			CompoundTag each = new CompoundTag();
			of.forEach(each::putLong);
			s.put(sekte, each);
		});
		tag.put("spots", s);
		CompoundTag t = new CompoundTag();
		tanding.forEach(t::putLong);
		tag.put("tanding", t);
		return tag;
	}
}
