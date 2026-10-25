package dev.kultivator.sekte.gabung;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
 * The world registry of Gabung Sekte (DESIGN 41.12.1), SavedData {@code kultivator:gabung} in the overworld's data
 * storage, one per world: every player's {@link Keanggotaan} and, per NPC sect, the membership state the sect itself
 * keeps ({@link DataSekteGabung}). The sect's own existence, tier and standing stay in Diplomasi (one list, T12); the
 * elders live in {@code kultivator:gabung_tokoh} (41W). New worlds only: there is no migration.
 * <p>
 * Limits (41.12.1): at most 12 sects x 7 elders, at most 9 board missions per sect, 20 history lines per player.
 * Server thread only.
 */
public final class GabungData extends SavedData {

	public static final String ID = "gabung";
	public static final Codec<GabungData> CODEC = CompoundTag.CODEC.xmap(GabungData::fromTag, GabungData::toTag);
	public static final SavedDataType<GabungData> TYPE = new SavedDataType<>(Kultivator.id(ID), GabungData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Board missions kept per sect. */
	public static final int PAPAN_MAKS = 9;

	private final Map<UUID, Keanggotaan> anggota = new LinkedHashMap<>();
	private final Map<String, DataSekteGabung> sekte = new LinkedHashMap<>();

	/**
	 * The membership state of one NPC sect ({@code GabungData.sekte.<s>}). Plain fields like {@link Keanggotaan}; the
	 * owner of each field is named in its comment. {@link #bagian} gives every module a free compound.
	 */
	public static final class DataSekteGabung {
		/** 41C: the day the board was drawn and its missions (compounds of {@code JenisMisiGabung}, at most 9). */
		public long papanHari = -1L;
		public final List<CompoundTag> papan = new ArrayList<>();
		/** 41D: rank id -> seat holders (player UUID strings or tokoh ids), for ranks with limited seats. */
		public final Map<String, List<String>> kursi = new LinkedHashMap<>();
		/** 41D: the heir and the vice master (UUID string or tokoh id; "" = vacant / NPC default). */
		public String pewaris = "";
		public String wakil = "";
		/** 41D: the player who became master through succession, or null. */
		public @Nullable UUID ketuaPemain;
		/** 41D: the running NPC war {lawan, skor, sampai, misi[]} or empty. */
		public CompoundTag perang = new CompoundTag();
		/** 41C: the day the secret-realm quota was last shared out. */
		public long kuotaHari = -1L;
		/** 41D: donations this month (player UUID -> PK value) and the month they count for. */
		public final Map<UUID, Integer> kas = new LinkedHashMap<>();
		public long kasBulan = -1L;
		/** 41D: MEROSOT state (K4) and the day it began; the last Bangkit day (once per 60 days). */
		public boolean merosot;
		public long merosotHari = -1L;
		public long bangkitHari = -1L;
		/** 41D: absorbed into a player hall (Serap) while true. */
		public boolean lebur;
		/** 41C: the disciple tournament's day and its bracket (compounds). */
		public long turnamenHari = -1L;
		public final List<CompoundTag> bracket = new ArrayList<>();
		/** 41D: the day of the last compound raid. */
		public long serbuanHari = -1L;
		/** 41D: player halls that are this sect's branches (owner UUIDs). */
		public final List<UUID> cabang = new ArrayList<>();
		/** 41A: the next sect handled by the daily pass ({@code GabungHarian}) records its own day here. */
		public long harianHari = -1L;
		private CompoundTag bagian = new CompoundTag();

		/** The free compound of module {@code modul}. */
		public CompoundTag bagian(String modul) {
			CompoundTag tag = bagian.getCompoundOrEmpty(modul);
			if (!bagian.contains(modul)) {
				bagian.put(modul, tag);
			}
			return tag;
		}

		/** The seat holders of rank {@code pangkatId} (live list). */
		public List<String> kursi(String pangkatId) {
			return kursi.computeIfAbsent(pangkatId, key -> new ArrayList<>());
		}

		CompoundTag simpan() {
			CompoundTag t = new CompoundTag();
			t.putLong("papanHari", papanHari);
			ListTag p = new ListTag();
			for (CompoundTag m : papan) {
				p.add(m.copy());
			}
			t.put("papan", p);
			CompoundTag k = new CompoundTag();
			kursi.forEach((rank, holders) -> {
				ListTag list = new ListTag();
				for (String h : holders) {
					list.add(StringTag.valueOf(h));
				}
				k.put(rank, list);
			});
			t.put("kursi", k);
			t.putString("pewaris", pewaris);
			t.putString("wakil", wakil);
			if (ketuaPemain != null) {
				t.putString("ketuaPemain", ketuaPemain.toString());
			}
			t.put("perang", perang.copy());
			t.putLong("kuotaHari", kuotaHari);
			CompoundTag ks = new CompoundTag();
			kas.forEach((uuid, v) -> ks.putInt(uuid.toString(), v));
			t.put("kas", ks);
			t.putLong("kasBulan", kasBulan);
			t.putBoolean("merosot", merosot);
			t.putLong("merosotHari", merosotHari);
			t.putLong("bangkitHari", bangkitHari);
			t.putBoolean("lebur", lebur);
			t.putLong("turnamenHari", turnamenHari);
			ListTag b = new ListTag();
			for (CompoundTag m : bracket) {
				b.add(m.copy());
			}
			t.put("bracket", b);
			t.putLong("serbuanHari", serbuanHari);
			ListTag c = new ListTag();
			for (UUID owner : cabang) {
				c.add(StringTag.valueOf(owner.toString()));
			}
			t.put("cabang", c);
			t.putLong("harianHari", harianHari);
			t.put("bagian", bagian.copy());
			return t;
		}

		static DataSekteGabung muat(CompoundTag t) {
			DataSekteGabung d = new DataSekteGabung();
			d.papanHari = t.getLongOr("papanHari", -1L);
			ListTag p = t.getListOrEmpty("papan");
			for (int i = 0; i < p.size() && d.papan.size() < PAPAN_MAKS; i++) {
				d.papan.add(p.getCompoundOrEmpty(i).copy());
			}
			CompoundTag k = t.getCompoundOrEmpty("kursi");
			for (String rank : k.keySet()) {
				ListTag list = k.getListOrEmpty(rank);
				List<String> holders = new ArrayList<>();
				for (int i = 0; i < list.size(); i++) {
					String h = list.getStringOr(i, "");
					if (!h.isEmpty()) {
						holders.add(h);
					}
				}
				d.kursi.put(rank, holders);
			}
			d.pewaris = t.getStringOr("pewaris", "");
			d.wakil = t.getStringOr("wakil", "");
			d.ketuaPemain = uuid(t.getStringOr("ketuaPemain", ""));
			d.perang = t.getCompoundOrEmpty("perang").copy();
			d.kuotaHari = t.getLongOr("kuotaHari", -1L);
			CompoundTag ks = t.getCompoundOrEmpty("kas");
			for (String key : ks.keySet()) {
				UUID id = uuid(key);
				if (id != null) {
					d.kas.put(id, ks.getIntOr(key, 0));
				}
			}
			d.kasBulan = t.getLongOr("kasBulan", -1L);
			d.merosot = t.getBooleanOr("merosot", false);
			d.merosotHari = t.getLongOr("merosotHari", -1L);
			d.bangkitHari = t.getLongOr("bangkitHari", -1L);
			d.lebur = t.getBooleanOr("lebur", false);
			d.turnamenHari = t.getLongOr("turnamenHari", -1L);
			ListTag b = t.getListOrEmpty("bracket");
			for (int i = 0; i < b.size(); i++) {
				d.bracket.add(b.getCompoundOrEmpty(i).copy());
			}
			d.serbuanHari = t.getLongOr("serbuanHari", -1L);
			ListTag c = t.getListOrEmpty("cabang");
			for (int i = 0; i < c.size(); i++) {
				UUID owner = uuid(c.getStringOr(i, ""));
				if (owner != null && !d.cabang.contains(owner)) {
					d.cabang.add(owner);
				}
			}
			d.harianHari = t.getLongOr("harianHari", -1L);
			d.bagian = t.getCompoundOrEmpty("bagian").copy();
			return d;
		}
	}

	public GabungData() {
	}

	public static GabungData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The player's record, created (BEBAS) on first use. */
	public Keanggotaan anggota(UUID player) {
		return anggota.computeIfAbsent(player, key -> {
			setDirty();
			return new Keanggotaan();
		});
	}

	/** The player's record or null when the player never touched 41. */
	public @Nullable Keanggotaan peek(UUID player) {
		return anggota.get(player);
	}

	/** Every record (read-only view; members of a sect are filtered by the caller). */
	public Map<UUID, Keanggotaan> semuaAnggota() {
		return java.util.Collections.unmodifiableMap(anggota);
	}

	/** The players who belong to {@code sekteId} right now. */
	public List<UUID> anggotaSekte(String sekteId) {
		List<UUID> out = new ArrayList<>();
		anggota.forEach((uuid, k) -> {
			if (k.anggotaDi(sekteId)) {
				out.add(uuid);
			}
		});
		return out;
	}

	/** The sect's state, created on first use. */
	public DataSekteGabung sekte(String sekteId) {
		return sekte.computeIfAbsent(sekteId, key -> {
			setDirty();
			return new DataSekteGabung();
		});
	}

	public @Nullable DataSekteGabung peekSekte(String sekteId) {
		return sekte.get(sekteId);
	}

	/** Drops one player's record ({@code uji gabung reset}). */
	public void hapus(UUID player) {
		if (anggota.remove(player) != null) {
			setDirty();
		}
	}

	/** Drops everything ({@code uji gabung reset semua}). */
	public void hapusSemua() {
		anggota.clear();
		sekte.clear();
		setDirty();
	}

	private static @Nullable UUID uuid(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ================================================================== save / load

	private static GabungData fromTag(CompoundTag tag) {
		GabungData data = new GabungData();
		CompoundTag a = tag.getCompoundOrEmpty("anggota");
		for (String key : a.keySet()) {
			UUID id = uuid(key);
			if (id != null) {
				data.anggota.put(id, Keanggotaan.muat(a.getCompoundOrEmpty(key)));
			}
		}
		CompoundTag s = tag.getCompoundOrEmpty("sekte");
		for (String key : s.keySet()) {
			data.sekte.put(key, DataSekteGabung.muat(s.getCompoundOrEmpty(key)));
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag a = new CompoundTag();
		anggota.forEach((uuid, k) -> a.put(uuid.toString(), k.simpan()));
		tag.put("anggota", a);
		CompoundTag s = new CompoundTag();
		sekte.forEach((id, d) -> s.put(id, d.simpan()));
		tag.put("sekte", s);
		return tag;
	}
}
