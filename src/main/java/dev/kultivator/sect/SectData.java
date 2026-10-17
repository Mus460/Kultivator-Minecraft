package dev.kultivator.sect;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every sect of the world, saved in the overworld's data storage (id {@code kultivator:sects}). Names are unique ignoring
 * case and a player belongs to at most one sect; a member index is rebuilt on load.
 */
public final class SectData extends SavedData {

	public static final Codec<SectData> CODEC = Sect.CODEC.listOf().fieldOf("sects").codec()
			.xmap(SectData::fromList, SectData::toList);

	public static final SavedDataType<SectData> TYPE = new SavedDataType<>(Kultivator.id("sects"), SectData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Lower-case name -> sect, in founding order. */
	private final Map<String, Sect> sects = new LinkedHashMap<>();
	/** Member -> lower-case name of their sect (not saved, rebuilt). */
	private final Map<UUID, String> memberIndex = new HashMap<>();

	public SectData() {
	}

	public static SectData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private static SectData fromList(List<Sect> list) {
		SectData data = new SectData();
		for (Sect sect : list) {
			if (!Sect.validName(sect.name()) || data.sects.containsKey(sect.key())) {
				Kultivator.LOGGER.warn("Skipping a saved sect with an invalid or duplicate name: {}", sect.name());
				continue;
			}
			// One sect per player: a player listed twice stays only in the first sect (keeps the founder at least).
			List<UUID> clash = new ArrayList<>();
			for (Sect.Member member : sect.members()) {
				if (data.memberIndex.containsKey(member.id())) {
					clash.add(member.id());
				}
			}
			if (clash.contains(sect.master())) {
				Kultivator.LOGGER.warn("Skipping saved sect {}: its master already leads or serves another sect", sect.name());
				continue;
			}
			for (UUID id : clash) {
				sect.remove(id);
			}
			data.sects.put(sect.key(), sect);
			for (Sect.Member member : sect.members()) {
				data.memberIndex.put(member.id(), sect.key());
			}
		}
		return data;
	}

	private List<Sect> toList() {
		return new ArrayList<>(sects.values());
	}

	// ================================================================== reading

	public Collection<Sect> all() {
		return Collections.unmodifiableCollection(sects.values());
	}

	public @Nullable Sect byName(String name) {
		return sects.get(Sect.key(name));
	}

	public @Nullable Sect sectOf(UUID player) {
		String key = memberIndex.get(player);
		return key == null ? null : sects.get(key);
	}

	public boolean exists(String name) {
		return sects.containsKey(Sect.key(name));
	}

	// ================================================================== changes

	/** Founds a sect (the caller checked the name and that the founder has no sect). */
	public Sect create(String name, UUID founder, String founderName, long now) {
		Sect sect = Sect.found(name, founder, founderName, now);
		sects.put(sect.key(), sect);
		memberIndex.put(founder, sect.key());
		setDirty();
		return sect;
	}

	public void addMember(Sect sect, UUID id, String name, long now) {
		sect.add(id, name, now);
		memberIndex.put(id, sect.key());
		setDirty();
	}

	/** Removes a disciple (never the master: use {@link #leave}). */
	public void removeMember(Sect sect, UUID id) {
		if (sect.isMaster(id)) {
			return;
		}
		sect.remove(id);
		memberIndex.remove(id);
		setDirty();
	}

	/** What happened when someone left. */
	public record LeaveResult(boolean disbanded, @Nullable Sect.Member newMaster) {
	}

	/**
	 * {@code id} leaves {@code sect}. A master leaving with members passes the role to the oldest member; a master alone
	 * disbands the sect.
	 */
	public LeaveResult leave(Sect sect, UUID id) {
		if (!sect.isMaster(id)) {
			removeMember(sect, id);
			return new LeaveResult(false, null);
		}
		Sect.Member heir = sect.passMaster();
		if (heir == null) {
			disband(sect);
			return new LeaveResult(true, null);
		}
		memberIndex.remove(id);
		setDirty();
		return new LeaveResult(false, heir);
	}

	public void disband(Sect sect) {
		for (Sect.Member member : sect.members()) {
			memberIndex.remove(member.id());
		}
		sects.remove(sect.key());
		setDirty();
	}

	/** Keeps a member's stored name up to date after a rename. */
	public void rename(UUID id, String name) {
		Sect sect = sectOf(id);
		if (sect != null && sect.rename(id, name)) {
			setDirty();
		}
	}
}
