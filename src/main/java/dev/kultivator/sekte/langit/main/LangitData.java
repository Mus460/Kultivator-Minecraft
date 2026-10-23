package dev.kultivator.sekte.langit.main;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The saved state of module 37G (DESIGN 37.8.7), {@code kultivator:sekte_langit} in the overworld's data storage. New data
 * only (old worlds simply start empty): per sky hall owner the event clock (last event day, next cloud pirate day, the
 * festival nights), the Heaven Court order ("Perintah Istana Langit", never called "titah", audit 4.9) and the refusal
 * count, the pirate chronicle and the NPC sky sects already met; per player the tribulation witness count of the day.
 * Gate and invitations belong to the build module (37B) and are not kept here.
 */
public final class LangitData extends SavedData {

	/** Lines of the hall's sky chronicle (pirates, envoys, guests). */
	public static final int CHRONICLE = 10;

	public static final Codec<LangitData> CODEC = CompoundTag.CODEC.xmap(LangitData::fromTag, LangitData::toTag);
	public static final SavedDataType<LangitData> TYPE = new SavedDataType<>(Kultivator.id("sekte_langit"), LangitData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** owner → the hall's sky state. */
	private final Map<UUID, CompoundTag> halls = new LinkedHashMap<>();
	/** player → the player's own sky state (witness count). */
	private final Map<UUID, CompoundTag> players = new LinkedHashMap<>();

	public LangitData() {
	}

	public static LangitData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== access

	/** The hall's own tag (created empty); call {@link #setDirty()} after a change. */
	public CompoundTag hall(UUID owner) {
		return halls.computeIfAbsent(owner, key -> new CompoundTag());
	}

	/** The hall's tag or null when it never had sky state. */
	public @Nullable CompoundTag peekHall(UUID owner) {
		return halls.get(owner);
	}

	public CompoundTag player(UUID player) {
		return players.computeIfAbsent(player, key -> new CompoundTag());
	}

	/** Adds a line to the hall's sky chronicle (newest first, {@link #CHRONICLE} kept). */
	public void chronicle(UUID owner, long day, Component line) {
		CompoundTag hall = hall(owner);
		ListTag list = hall.getListOrEmpty("kronik");
		ListTag next = new ListTag();
		CompoundTag entry = new CompoundTag();
		entry.putLong("day", day);
		entry.store("text", ComponentSerialization.CODEC, line);
		next.add(entry);
		for (int i = 0; i < list.size() && next.size() < CHRONICLE; i++) {
			next.add(list.get(i));
		}
		hall.put("kronik", next);
		setDirty();
	}

	/** The chronicle lines (newest first) as {day, text} tags. */
	public List<CompoundTag> chronicle(UUID owner) {
		CompoundTag hall = halls.get(owner);
		if (hall == null) {
			return List.of();
		}
		List<CompoundTag> out = new ArrayList<>();
		ListTag list = hall.getListOrEmpty("kronik");
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag tag) {
				out.add(tag);
			}
		}
		return out;
	}

	/** The text of a chronicle entry (empty when unreadable). */
	public static Component text(CompoundTag entry) {
		return entry.read("text", ComponentSerialization.CODEC).orElse(Component.empty());
	}

	/** Every owner with sky state (a copy). */
	public List<UUID> owners() {
		return new ArrayList<>(halls.keySet());
	}

	// ================================================================== codec

	private static LangitData fromTag(CompoundTag tag) {
		LangitData data = new LangitData();
		read(tag.getCompoundOrEmpty("halls"), data.halls);
		read(tag.getCompoundOrEmpty("players"), data.players);
		return data;
	}

	private static void read(CompoundTag from, Map<UUID, CompoundTag> into) {
		for (String key : from.keySet()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException exception) {
				continue;
			}
			Tag value = from.get(key);
			if (value instanceof CompoundTag compound) {
				into.put(id, compound.copy());
			}
		}
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("v", 1);
		tag.put("halls", write(halls));
		tag.put("players", write(players));
		return tag;
	}

	private static CompoundTag write(Map<UUID, CompoundTag> map) {
		CompoundTag out = new CompoundTag();
		for (Map.Entry<UUID, CompoundTag> entry : map.entrySet()) {
			out.put(entry.getKey().toString(), entry.getValue().copy());
		}
		return out;
	}
}
