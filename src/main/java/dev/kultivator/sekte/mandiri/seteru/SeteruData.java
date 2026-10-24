package dev.kultivator.sekte.mandiri.seteru;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
 * The world registry of 39F (DESIGN 39.F.1), {@code kultivator:mandiri_seteru} in the overworld's data storage. Not per
 * hall, because the disciples of the other sects are the same people for every player: the roster of each NPC sect
 * ({@code 6 + tier} named members, at most 10, so at most 80 records of about 300 bytes) and, per player hall, its feuds,
 * the week's standing losses per sect, the patrol gang on its road, its cross-sect friends and lovers and the effects
 * waiting for disciples whose bodies are not loaded. Server thread only.
 */
public final class SeteruData extends SavedData {

	/** Ended feuds kept per hall for the screen (the oldest dropped). */
	static final int AKHIR_MAKS = 8;
	/** Waiting effects per soul. */
	static final int ANTRE_MAKS = 10;
	/** Cross-sect friends and lovers per hall. */
	static final int SAHABAT_MAKS = 8;

	public static final Codec<SeteruData> CODEC = CompoundTag.CODEC.xmap(SeteruData::fromTag, SeteruData::toTag);
	public static final SavedDataType<SeteruData> TYPE = new SavedDataType<>(Kultivator.id(MandiriIds.DATA_SETERU), SeteruData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** sect id -> its named disciples. */
	final Map<String, List<TokohSekte>> roster = new LinkedHashMap<>();
	/** owner -> the hall's feud state. */
	final Map<UUID, Aula> aula = new LinkedHashMap<>();
	int nextId = 1;

	/** One hall's share of the registry. */
	static final class Aula {
		final List<Perseteruan> seteru = new ArrayList<>();
		/** sect id -> {minggu, jumlah}: standing lost through feuds this week (39.F.3 T6 cap). */
		final CompoundTag standingMinggu = new CompoundTag();
		/** The patrol gang (39.F.6): {seteru, sekte, ax, az, bx, bz, tokoh[], usir} or empty. */
		CompoundTag patroli = new CompoundTag();
		/** soul -> waiting effects ({@link Antrean}). */
		final Map<UUID, List<CompoundTag>> antre = new LinkedHashMap<>();
		/** Cross-sect friends {soul, tokoh, hari, bantu} (39.F.3). */
		final List<CompoundTag> sahabat = new ArrayList<>();
		/** Forbidden lovers {soul, tokoh, mulai, rahasia, ketahuan}. */
		final List<CompoundTag> kekasih = new ArrayList<>();
		/** World day of the last daily step (heat decay, patrol end, elders back). */
		long hari = -1L;

		List<Perseteruan> aktif() {
			List<Perseteruan> out = new ArrayList<>();
			for (Perseteruan f : seteru) {
				if (f.aktif()) {
					out.add(f);
				}
			}
			return out;
		}

		@Nullable Perseteruan byId(String id) {
			for (Perseteruan f : seteru) {
				if (f.id.equals(id)) {
					return f;
				}
			}
			return null;
		}

		/** The running feud (status AKTIF, not a forbidden love) {@code soul} takes part in, or null (one per disciple). */
		@Nullable Perseteruan milik(UUID soul) {
			for (Perseteruan f : seteru) {
				if (SeteruIds.AKTIF.equals(f.status) && f.melibatkan(soul)) {
					return f;
				}
			}
			return null;
		}

		/** The running feud (status AKTIF) with {@code sekte}, or null. */
		@Nullable Perseteruan dengan(String sekte) {
			for (Perseteruan f : seteru) {
				if (SeteruIds.AKTIF.equals(f.status) && f.sekte.equals(sekte)) {
					return f;
				}
			}
			return null;
		}

		/** Drops ended feuds beyond {@link #AKHIR_MAKS}, oldest first. */
		void rapikan() {
			int ended = 0;
			for (Perseteruan f : seteru) {
				if (!f.aktif()) {
					ended++;
				}
			}
			for (int i = 0; i < seteru.size() && ended > AKHIR_MAKS; ) {
				if (!seteru.get(i).aktif()) {
					seteru.remove(i);
					ended--;
				} else {
					i++;
				}
			}
		}

		@Nullable CompoundTag sahabatDari(UUID soul, String tokoh) {
			for (CompoundTag t : sahabat) {
				if (soul.toString().equals(t.getStringOr("soul", "")) && tokoh.equals(t.getStringOr("tokoh", ""))) {
					return t;
				}
			}
			return null;
		}

		@Nullable CompoundTag kekasihDari(UUID soul) {
			for (CompoundTag t : kekasih) {
				if (soul.toString().equals(t.getStringOr("soul", ""))) {
					return t;
				}
			}
			return null;
		}
	}

	public SeteruData() {
	}

	public static SeteruData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	Aula aula(UUID owner) {
		return aula.computeIfAbsent(owner, key -> new Aula());
	}

	@Nullable Aula peek(UUID owner) {
		return aula.get(owner);
	}

	String idBaru() {
		String id = "f" + nextId++;
		setDirty();
		return id;
	}

	List<TokohSekte> anggota(String sekte) {
		return roster.computeIfAbsent(sekte, key -> new ArrayList<>());
	}

	@Nullable TokohSekte tokoh(@Nullable String id) {
		if (id == null) {
			return null;
		}
		int hash = id.indexOf('#');
		if (hash <= 0) {
			return null;
		}
		List<TokohSekte> list = roster.get(id.substring(0, hash));
		if (list == null) {
			return null;
		}
		for (TokohSekte t : list) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}

	// ================================================================== save / load

	private static SeteruData fromTag(CompoundTag tag) {
		SeteruData data = new SeteruData();
		data.nextId = Math.max(1, tag.getIntOr("nextId", 1));
		CompoundTag roster = tag.getCompoundOrEmpty("roster");
		for (String sekte : roster.keySet()) {
			ListTag list = roster.getListOrEmpty(sekte);
			List<TokohSekte> members = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				members.add(TokohSekte.load(list.getCompoundOrEmpty(i)));
			}
			data.roster.put(sekte, members);
		}
		ListTag halls = tag.getListOrEmpty("aula");
		for (int i = 0; i < halls.size(); i++) {
			CompoundTag h = halls.getCompoundOrEmpty(i);
			UUID owner = h.read("owner", UUIDUtil.CODEC).orElse(null);
			if (owner == null) {
				continue;
			}
			Aula a = new Aula();
			ListTag feuds = h.getListOrEmpty("seteru");
			for (int j = 0; j < feuds.size(); j++) {
				Perseteruan f = Perseteruan.load(feuds.getCompoundOrEmpty(j));
				if (f != null) {
					a.seteru.add(f);
				}
			}
			CompoundTag weeks = h.getCompoundOrEmpty("standing");
			for (String key : weeks.keySet()) {
				a.standingMinggu.put(key, weeks.getCompoundOrEmpty(key).copy());
			}
			a.patroli = h.getCompoundOrEmpty("patroli").copy();
			CompoundTag queue = h.getCompoundOrEmpty("antre");
			for (String key : queue.keySet()) {
				try {
					UUID soul = UUID.fromString(key);
					ListTag list = queue.getListOrEmpty(key);
					List<CompoundTag> effects = new ArrayList<>();
					for (int j = 0; j < list.size(); j++) {
						effects.add(list.getCompoundOrEmpty(j));
					}
					if (!effects.isEmpty()) {
						a.antre.put(soul, effects);
					}
				} catch (IllegalArgumentException ignored) {
					// a key that is no UUID: skip it
				}
			}
			ListTag friends = h.getListOrEmpty("sahabat");
			for (int j = 0; j < friends.size(); j++) {
				a.sahabat.add(friends.getCompoundOrEmpty(j));
			}
			ListTag lovers = h.getListOrEmpty("kekasih");
			for (int j = 0; j < lovers.size(); j++) {
				a.kekasih.add(lovers.getCompoundOrEmpty(j));
			}
			a.hari = h.getLongOr("hari", -1L);
			data.aula.put(owner, a);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("nextId", nextId);
		CompoundTag rosterTag = new CompoundTag();
		for (Map.Entry<String, List<TokohSekte>> e : roster.entrySet()) {
			ListTag list = new ListTag();
			for (TokohSekte t : e.getValue()) {
				list.add(t.save());
			}
			rosterTag.put(e.getKey(), list);
		}
		tag.put("roster", rosterTag);
		ListTag halls = new ListTag();
		for (Map.Entry<UUID, Aula> e : aula.entrySet()) {
			Aula a = e.getValue();
			CompoundTag h = new CompoundTag();
			h.store("owner", UUIDUtil.CODEC, e.getKey());
			ListTag feuds = new ListTag();
			for (Perseteruan f : a.seteru) {
				feuds.add(f.save());
			}
			h.put("seteru", feuds);
			h.put("standing", a.standingMinggu.copy());
			if (!a.patroli.isEmpty()) {
				h.put("patroli", a.patroli.copy());
			}
			CompoundTag queue = new CompoundTag();
			for (Map.Entry<UUID, List<CompoundTag>> q : a.antre.entrySet()) {
				ListTag list = new ListTag();
				list.addAll(q.getValue());
				queue.put(q.getKey().toString(), list);
			}
			h.put("antre", queue);
			ListTag friends = new ListTag();
			friends.addAll(a.sahabat);
			h.put("sahabat", friends);
			ListTag lovers = new ListTag();
			lovers.addAll(a.kekasih);
			h.put("kekasih", lovers);
			h.putLong("hari", a.hari);
			halls.add(h);
		}
		tag.put("aula", halls);
		return tag;
	}
}
