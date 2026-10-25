package dev.kultivator.dunia.kota42.warga;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:kota_warga} in the overworld (DESIGN 42.13.1, owner 42A): every stored person of every place
 * that was ever active. New worlds only: no migration, no version field.
 * <ul>
 * <li>{@code pemain}: the player UUID table; memories store the index ({@link #indeksPemain}).</li>
 * <li>per place ({@link Situs}): {@code warga[]} (compound keys of {@link Warga}), {@code arsip[]} (the dead and the sect
 * archive, at most {@code kotaWarga.arsipMaks}, raw compounds of 42A's {@code ArsipWarga}), {@code gosip[]} (at most
 * {@code kotaWarga.gosipMaks}, raw compounds; 42K's {@code GosipWarga} format), {@code stat} (population, 14-day
 * prosperity, mean mood) and {@code hariTerakhir} (last Hari Warga run, for the catch-up).</li>
 * <li>{@code nextId}: the global resident id counter (ids are never reused).</li>
 * </ul>
 * Skeleton of 42A0: storage, lookup and codec only. 42A fills it ({@code WargaGen.bangkitkan}, {@code HariWarga}).
 */
public final class WargaData extends SavedData {

	public static final Codec<WargaData> CODEC = CompoundTag.CODEC.xmap(WargaData::fromTag, WargaData::toTag);
	public static final SavedDataType<WargaData> TYPE = new SavedDataType<>(Kultivator.id("kota_warga"), WargaData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** The stored people of one place. */
	public static final class Situs {
		public final long id;
		/** Living (and travelling / sect) residents in insertion order. */
		public final List<Warga> warga = new ArrayList<>();
		/** Archive entries (raw compounds of {@code ArsipWarga}). */
		public final ListTag arsip = new ListTag();
		/** Gossip entries of this place (raw compounds of {@code GosipWarga}). */
		public final ListTag gosip = new ListTag();
		/** Place statistics (population, prosperity 14 days, mean mood). */
		public CompoundTag stat = new CompoundTag();
		/** World day of the last Hari Warga pass (-1 = never: the place has not been raised yet). */
		public long hariTerakhir = -1L;

		Situs(long id) {
			this.id = id;
		}

		/** slot -> occupant, rebuilt lazily (at most once per server tick on a miss); memory only. */
		private final Map<Integer, Warga> indeksSlot = new HashMap<>();
		private long indeksTick = Long.MIN_VALUE;
		private boolean indeksBasi = true;

		/** Marks the slot index stale (a resident joined or left this place). */
		void basi() {
			indeksBasi = true;
		}

		/**
		 * The Warga occupying slot {@code slot}, or null, through the slot index: a hit is checked against the person's own
		 * slot and status; a miss rebuilds the index at most once per server tick {@code tick} (38's place cycle asks for
		 * every slot several times per cycle, so the plain scan of {@link #diSlot} would be O(n²)).
		 */
		public @Nullable Warga diSlotCepat(int slot, long tick) {
			Warga w = indeksBasi ? null : indeksSlot.get(slot);
			if (w != null && w.slot == slot && w.status == Warga.Status.HIDUP) {
				return w;
			}
			if (!indeksBasi && indeksTick == tick) {
				return null;
			}
			indeksSlot.clear();
			for (Warga each : warga) {
				if (each.slot != Warga.TANPA_SLOT && each.status == Warga.Status.HIDUP) {
					indeksSlot.put(each.slot, each);
				}
			}
			indeksTick = tick;
			indeksBasi = false;
			w = indeksSlot.get(slot);
			return w != null && w.slot == slot && w.status == Warga.Status.HIDUP ? w : null;
		}

		/** The Warga occupying slot {@code slot}, or null (a plain scan). */
		public @Nullable Warga diSlot(int slot) {
			for (Warga w : warga) {
				if (w.slot == slot && w.status == Warga.Status.HIDUP) {
					return w;
				}
			}
			return null;
		}

		/** True once {@code WargaGen.bangkitkan} filled the place. */
		public boolean bangkit() {
			return hariTerakhir >= 0L;
		}

		/** A new household id of this place (after the generated ones). */
		public int rumahTanggaBaru() {
			int next = Math.max(stat.getIntOr("rtNext", 1), 1);
			stat.putInt("rtNext", next + 1);
			return next;
		}

		/** Remembers a death (resident id, world day) for mourning; kept 8 days. */
		public void catatWafat(int id, long hari) {
			long[] old = stat.getLongArray("wafat").orElse(new long[0]);
			long[] next = new long[Math.min(old.length, 63) + 1];
			int n = 0;
			for (long e : old) {
				if (hari - (e & 0xFFFFFFFFL) <= 8L && n < next.length - 1) {
					next[n++] = e;
				}
			}
			next[n++] = (long) id << 32 | (hari & 0xFFFFFFFFL);
			stat.putLongArray("wafat", java.util.Arrays.copyOf(next, n));
		}

		/** The world day resident {@code id} died if within the last 8 days, else -1. */
		public long hariWafat(int id) {
			for (long e : stat.getLongArray("wafat").orElse(new long[0])) {
				if ((int) (e >>> 32) == id) {
					return e & 0xFFFFFFFFL;
				}
			}
			return -1L;
		}

		/** The household mourning since world day (latest death of a member in the last 8 days), else -1. */
		public long dukaRumahTangga(int rumahTangga) {
			return rumahTangga <= 0 ? -1L : stat.getCompoundOrEmpty("duka").getLongOr(Integer.toString(rumahTangga), -1L);
		}

		public void catatDuka(int rumahTangga, long hari) {
			if (rumahTangga > 0) {
				CompoundTag duka = stat.getCompoundOrEmpty("duka");
				duka.putLong(Integer.toString(rumahTangga), hari);
				stat.put("duka", duka);
			}
		}

		/** Living residents of household {@code rumahTangga}. */
		public List<Warga> rumahTangga(int rumahTangga) {
			List<Warga> out = new ArrayList<>();
			for (Warga w : warga) {
				if (w.rumahTangga == rumahTangga && w.status == Warga.Status.HIDUP) {
					out.add(w);
				}
			}
			return out;
		}
	}

	private final List<UUID> pemain = new ArrayList<>();
	private final Map<UUID, Integer> indeksPemain = new HashMap<>();
	private final Map<Long, Situs> situs = new LinkedHashMap<>();
	/** id -> resident (rebuilt on load, kept on add/remove). */
	private final Map<Integer, Warga> byId = new HashMap<>();
	/** id -> place id. */
	private final Map<Integer, Long> situsDari = new HashMap<>();
	private int nextId = 1;
	/** Disciple soul -> resident id (residents who joined a player's sect, {@code WargaApi.catatMurid}). */
	private final Map<UUID, Integer> murid = new HashMap<>();

	public WargaData() {
	}

	/** The world's data (overworld storage), created on first use. */
	public static WargaData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ------------------------------------------------------------------ players

	/** The table index of {@code player}, added on first use. */
	public int indeksPemain(UUID player) {
		Integer i = indeksPemain.get(player);
		if (i != null) {
			return i;
		}
		int next = pemain.size();
		pemain.add(player);
		indeksPemain.put(player, next);
		setDirty();
		return next;
	}

	/** The table index of {@code player} or -1 when the player was never remembered. */
	public int cariPemain(UUID player) {
		Integer i = indeksPemain.get(player);
		return i == null ? -1 : i;
	}

	public @Nullable UUID pemain(int index) {
		return index >= 0 && index < pemain.size() ? pemain.get(index) : null;
	}

	/** Links a disciple soul to the resident it was (42.4.9 {@code keluargaMurid}). */
	public void catatMurid(UUID soul, int wargaId) {
		murid.put(soul, wargaId);
		setDirty();
	}

	/** The resident a disciple soul was, or 0. */
	public int muridDari(UUID soul) {
		Integer id = murid.get(soul);
		return id == null ? 0 : id;
	}

	// ------------------------------------------------------------------ places and residents

	/** The place {@code situsId}, created empty on first use. */
	public Situs situs(long situsId) {
		return situs.computeIfAbsent(situsId, Situs::new);
	}

	public @Nullable Situs peek(long situsId) {
		return situs.get(situsId);
	}

	public Collection<Situs> semuaSitus() {
		return situs.values();
	}

	public @Nullable Warga byId(int id) {
		return byId.get(id);
	}

	/** The place id of resident {@code id}, or 0 when unknown. */
	public long situsDari(int id) {
		Long s = situsDari.get(id);
		return s == null ? 0L : s;
	}

	/** A new global id (never reused). */
	public int idBaru() {
		setDirty();
		return nextId++;
	}

	/** Adds (or moves) {@code w} to place {@code situsId}. */
	public void tambah(long situsId, Warga w) {
		Long old = situsDari.get(w.id);
		if (old != null && old != situsId) {
			Situs from = situs.get(old);
			if (from != null) {
				from.warga.remove(w);
				from.basi();
			}
		}
		Situs s = situs(situsId);
		if (!s.warga.contains(w)) {
			s.warga.add(w);
		}
		s.basi();
		byId.put(w.id, w);
		situsDari.put(w.id, situsId);
		if (w.id >= nextId) {
			nextId = w.id + 1;
		}
		setDirty();
	}

	/** Removes resident {@code id} from its place (42A moves it to the archive first). */
	public void buang(int id) {
		Warga w = byId.remove(id);
		Long s = situsDari.remove(id);
		if (w != null && s != null) {
			Situs place = situs.get(s);
			if (place != null) {
				place.warga.remove(w);
				place.basi();
			}
		}
		setDirty();
	}

	/** Forgets one place ({@code uji kota_hidup reset warga <situs>}). */
	public void hapusSitus(long situsId) {
		Situs s = situs.remove(situsId);
		if (s != null) {
			for (Warga w : s.warga) {
				byId.remove(w.id);
				situsDari.remove(w.id);
			}
		}
		setDirty();
	}

	/** Forgets everything except the id counter and the player table ({@code uji kota_hidup reset warga}). */
	public void hapusSemua() {
		situs.clear();
		byId.clear();
		situsDari.clear();
		setDirty();
	}

	/** Number of stored residents in every place. */
	public int jumlah() {
		return byId.size();
	}

	public void ubah() {
		setDirty();
	}

	// ------------------------------------------------------------------ codec

	private static WargaData fromTag(CompoundTag tag) {
		WargaData data = new WargaData();
		data.nextId = Math.max(1, tag.getIntOr("nextId", 1));
		ListTag players = tag.getListOrEmpty("pemain");
		for (int i = 0; i < players.size(); i++) {
			try {
				UUID id = UUID.fromString(players.getStringOr(i, ""));
				data.indeksPemain.put(id, data.pemain.size());
				data.pemain.add(id);
			} catch (IllegalArgumentException exception) {
				// keep the index stable with a placeholder that never matches a real player
				data.pemain.add(new UUID(0L, i));
			}
		}
		CompoundTag souls = tag.getCompoundOrEmpty("murid");
		for (String key : souls.keySet()) {
			try {
				data.murid.put(UUID.fromString(key), souls.getIntOr(key, 0));
			} catch (IllegalArgumentException ignored) {
				// not a soul id
			}
		}
		CompoundTag places = tag.getCompoundOrEmpty("situs");
		for (String key : places.keySet()) {
			long id;
			try {
				id = Long.parseLong(key);
			} catch (NumberFormatException exception) {
				continue;
			}
			CompoundTag p = places.getCompoundOrEmpty(key);
			Situs s = data.situs(id);
			ListTag ws = p.getListOrEmpty("warga");
			for (int i = 0; i < ws.size(); i++) {
				Warga w = Warga.muat(ws.getCompoundOrEmpty(i));
				if (w.id > 0) {
					s.warga.add(w);
					data.byId.put(w.id, w);
					data.situsDari.put(w.id, id);
					data.nextId = Math.max(data.nextId, w.id + 1);
				}
			}
			copy(p.getListOrEmpty("arsip"), s.arsip);
			copy(p.getListOrEmpty("gosip"), s.gosip);
			s.stat = p.getCompoundOrEmpty("stat").copy();
			s.hariTerakhir = p.getLongOr("hariTerakhir", -1L);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("nextId", nextId);
		ListTag players = new ListTag();
		for (UUID id : pemain) {
			players.add(StringTag.valueOf(id.toString()));
		}
		tag.put("pemain", players);
		CompoundTag souls = new CompoundTag();
		for (Map.Entry<UUID, Integer> e : murid.entrySet()) {
			souls.putInt(e.getKey().toString(), e.getValue());
		}
		tag.put("murid", souls);
		CompoundTag places = new CompoundTag();
		for (Situs s : situs.values()) {
			CompoundTag p = new CompoundTag();
			ListTag ws = new ListTag();
			for (Warga w : s.warga) {
				ws.add(w.simpan());
			}
			p.put("warga", ws);
			p.put("arsip", s.arsip.copy());
			p.put("gosip", s.gosip.copy());
			p.put("stat", s.stat.copy());
			p.putLong("hariTerakhir", s.hariTerakhir);
			places.put(Long.toString(s.id), p);
		}
		tag.put("situs", places);
		return tag;
	}

	private static void copy(ListTag from, ListTag to) {
		for (Tag t : from) {
			to.add(t.copy());
		}
	}
}
