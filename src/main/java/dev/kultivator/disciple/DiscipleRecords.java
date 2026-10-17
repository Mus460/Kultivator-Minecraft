package dev.kultivator.disciple;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the disciple module remembers across restarts, in the overworld's data storage (id {@code kultivator:disciple_records}):
 * the souls that fell while their master was offline (marked fallen in the master's list when they join), and the souls
 * a master forgot with {@code murid lupakan} (a body of theirs that loads again takes its leave, a tablet of theirs is
 * only an echo).
 */
public final class DiscipleRecords extends SavedData {

	/** Forgotten souls beyond this many are dropped, oldest first. */
	private static final int MAX_FORGOTTEN = 64;

	public static final Codec<DiscipleRecords> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.CODEC.listOf()).optionalFieldOf("pending_deaths", Map.of())
					.forGetter(records -> records.pendingDeaths),
			UUIDUtil.CODEC.listOf().optionalFieldOf("forgotten", List.of()).forGetter(records -> List.copyOf(records.forgotten))
	).apply(instance, (pending, forgotten) -> new DiscipleRecords(pending, forgotten)));

	public static final SavedDataType<DiscipleRecords> TYPE = new SavedDataType<>(Kultivator.id("disciple_records"), DiscipleRecords::new,
			CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Master UUID -> souls that fell while the master was offline. */
	private final Map<UUID, List<UUID>> pendingDeaths = new HashMap<>();
	/** Souls forgotten by their master, oldest first. */
	private final Set<UUID> forgotten = new LinkedHashSet<>();

	public DiscipleRecords() {
	}

	private DiscipleRecords(Map<UUID, List<UUID>> pending, List<UUID> forgotten) {
		pending.forEach((owner, souls) -> pendingDeaths.put(owner, new ArrayList<>(souls)));
		this.forgotten.addAll(forgotten);
	}

	public static DiscipleRecords get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Remembers that {@code soul} fell while its master {@code owner} was offline. */
	void addPendingDeath(UUID owner, UUID soul) {
		List<UUID> souls = pendingDeaths.computeIfAbsent(owner, id -> new ArrayList<>());
		if (!souls.contains(soul)) {
			souls.add(soul);
		}
		setDirty();
	}

	/** The souls of {@code owner} that fell while they were away (removed from the record), or null. */
	@Nullable List<UUID> takePendingDeaths(UUID owner) {
		List<UUID> souls = pendingDeaths.remove(owner);
		if (souls != null) {
			setDirty();
		}
		return souls;
	}

	/** The master forgot this soul for good. */
	void forget(UUID soul) {
		forgotten.remove(soul);
		forgotten.add(soul);
		for (Iterator<UUID> it = forgotten.iterator(); forgotten.size() > MAX_FORGOTTEN && it.hasNext(); ) {
			it.next();
			it.remove();
		}
		setDirty();
	}

	boolean isForgotten(UUID soul) {
		return forgotten.contains(soul);
	}
}
