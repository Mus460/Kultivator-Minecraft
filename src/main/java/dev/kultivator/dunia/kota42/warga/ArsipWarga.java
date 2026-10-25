package dev.kultivator.dunia.kota42.warga;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The archive of a place (DESIGN 42.4.4) and the small per-resident records of 42A-2.
 * <p>
 * <b>Archive</b>: residents who died, left the map or joined a sect are kept as one compact entry in
 * {@code WargaData.Situs.arsip} (raw compounds), so the family tree (42.11.3) can show three generations up and down and
 * the gravestones (titik {@code nisan_<n>}) carry a name. Keys: {@code i} id, {@code m} family name, {@code n} given name,
 * {@code g} generation, {@code k} sex, {@code l} birth (world day x 10), {@code w} the day it ended, {@code a}/{@code b}
 * father/mother, {@code p} spouse, {@code c} children, {@code h} gravestone number (-1 none), {@code j} last role,
 * {@code s} last slot, {@code q} spirit root, {@code t} cause ({@code usia}, {@code sakit}, {@code pindah_jauh},
 * {@code sekte:<id>}, {@code murid:<owner uuid>}), {@code tk} story character id. At most {@code kotaWarga.arsipMaks} per
 * place: the oldest entry without a living descendant goes first.
 * <p>
 * <b>Records</b> ({@link Catatan}, SavedData {@code kultivator:kota_warga_catatan}): what the stored person itself has no
 * field for: mood events of {@link Suasana}, "unemployed since" and "sad since" for {@link Migrasi}, the path a child chose
 * and its master ({@link Pekerjaan}), the place it came from ({@link Migrasi}), the recruit timers of {@link Berbakat}, and
 * per place the raid flag and the last ceremony. New worlds only; no migration.
 */
public final class ArsipWarga {

	/** {@code t} causes. */
	public static final String SEBAB_USIA = "usia";
	public static final String SEBAB_SAKIT = "sakit";
	public static final String SEBAB_PINDAH_JAUH = "pindah_jauh";
	public static final String SEBAB_SEKTE = "sekte:";
	public static final String SEBAB_MURID = "murid:";

	/** Stat key of the rotating gravestone counter. */
	private static final String NISAN_N = "a2nisan";

	private static boolean ready;

	// ================================================================== 42A-2 entry points (called by 42A)

	/**
	 * Starts every 42A-2 part: moods ({@link Suasana}), habits data ({@link Kebiasaan}), the schedule overrider
	 * ({@link PenimpaWarga}), extra slots ({@link PendudukTambahan}), moves ({@link Migrasi}) and travellers on the road
	 * ({@link PerjalananWarga}). From {@code KotaWarga.init} (both sides at mod init); safe twice.
	 */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		Suasana.init();
		Kebiasaan.init();
		PenimpaWarga.init();
		PendudukTambahan.init();
		Migrasi.init();
		PerjalananWarga.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Suasana.lupakan();
			Kebiasaan.lupakan();
			Pekerjaan.lupakan();
			Migrasi.lupakan();
		});
	}

	/**
	 * The 42A-2 Hari Warga steps of one place for world day {@code hari}, in order: habits (birthday picks, yearly health,
	 * group friendships, today's bonuses), moods, work (paths, apprentices, skill, vacant slots), moves (arrivals,
	 * departures, decisions, newcomers), gifted children, memories drifting. From {@code HariWarga} once per place per day
	 * (also for caught-up full days; not for summarised days).
	 */
	public static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		if (!KotaConfig.warga().aktif) {
			return;
		}
		Kebiasaan.harian(server, s, hari);
		Suasana.harian(server, s, hari);
		Pekerjaan.harian(server, s, hari);
		Migrasi.harian(server, s, hari);
		Berbakat.harian(server, s, hari);
		Ingatan.harian(s, hari);
	}

	/** A read-only view of one archive entry (family tree, gravestones, commands). */
	public record Entri(int id, int marga, int nama, int generasi, boolean wanita, long lahir, long akhir, int ayah, int ibu,
			int pasangan, int[] anak, int nisan, String peran, int slot, int akar, String sebab, String tokoh) {

		/** True for the dead (not for those who moved away or joined a sect). */
		public boolean wafat() {
			return sebab.equals(SEBAB_USIA) || sebab.equals(SEBAB_SAKIT) || sebab.startsWith("mati");
		}

		/** True while the person still lives somewhere else (a sect, far lands). */
		public boolean masihHidup() {
			return !wafat();
		}
	}

	// ================================================================== archive

	/**
	 * Archives {@code w} in place {@code situs} ({@code sebab}: one of the {@code SEBAB_*}; {@code nisan} the gravestone
	 * number or -1) and drops the oldest entries without living descendants beyond {@code arsipMaks}. The caller removes
	 * the resident from {@code WargaData} afterwards when it is no longer a living resident.
	 */
	public static CompoundTag arsipkan(WargaData data, long situs, Warga w, String sebab, long hari, int nisan) {
		WargaData.Situs s = data.situs(situs);
		CompoundTag t = new CompoundTag();
		t.putInt("i", w.id);
		t.putByte("m", (byte) w.marga);
		t.putShort("n", (short) w.nama);
		t.putByte("g", (byte) w.generasi);
		t.putByte("k", w.kelamin);
		t.putLong("l", w.lahir);
		t.putLong("w", hari);
		if (w.ayah != Warga.TANPA) {
			t.putInt("a", w.ayah);
		}
		if (w.ibu != Warga.TANPA) {
			t.putInt("b", w.ibu);
		}
		if (w.pasangan != Warga.TANPA) {
			t.putInt("p", w.pasangan);
		}
		if (w.anak.length > 0) {
			t.putIntArray("c", w.anak.clone());
		}
		t.putInt("h", nisan);
		t.putString("j", w.peran);
		t.putInt("s", w.slot);
		if (w.akar != 0) {
			t.putByte("q", (byte) w.akar);
		}
		t.putString("t", sebab == null ? SEBAB_USIA : sebab);
		if (!w.tokoh.isEmpty()) {
			t.putString("tk", w.tokoh);
		}
		// one entry per person: a later archive (sect member who died) replaces the earlier one
		for (int i = s.arsip.size() - 1; i >= 0; i--) {
			if (s.arsip.getCompoundOrEmpty(i).getIntOr("i", 0) == w.id) {
				s.arsip.remove(i);
			}
		}
		s.arsip.add(t);
		pangkas(data, s, KotaConfig.warga().arsipMaks);
		data.ubah();
		return t;
	}

	/** Drops the oldest entries without a living descendant until at most {@code maks} remain (entries are oldest first). */
	static void pangkas(WargaData data, WargaData.Situs s, int maks) {
		int guard = s.arsip.size();
		while (s.arsip.size() > maks && guard-- > 0) {
			int drop = -1;
			for (int i = 0; i < s.arsip.size(); i++) {
				if (!adaKeturunanHidup(data, s, s.arsip.getCompoundOrEmpty(i), 2)) {
					drop = i;
					break;
				}
			}
			s.arsip.remove(drop >= 0 ? drop : 0);
		}
	}

	/** True when a child (or, {@code depth} 2, a grandchild) of the archived person still lives in the data. */
	private static boolean adaKeturunanHidup(WargaData data, WargaData.Situs s, CompoundTag entry, int depth) {
		if (depth <= 0) {
			return false;
		}
		for (int c : entry.getIntArray("c").orElse(new int[0])) {
			Warga child = data.byId(c);
			if (child != null && child.hidup()) {
				return true;
			}
			CompoundTag archived = cariTag(s, c);
			if (archived != null && adaKeturunanHidup(data, s, archived, depth - 1)) {
				return true;
			}
		}
		return false;
	}

	/** The raw archive entry of {@code id} in place {@code s}, or null. */
	public static @Nullable CompoundTag cariTag(WargaData.Situs s, int id) {
		for (int i = s.arsip.size() - 1; i >= 0; i--) {
			CompoundTag t = s.arsip.getCompoundOrEmpty(i);
			if (t.getIntOr("i", 0) == id) {
				return t;
			}
		}
		return null;
	}

	/** The archive entry of {@code id} in place {@code s}, or null. */
	public static @Nullable Entri cari(WargaData.Situs s, int id) {
		CompoundTag t = cariTag(s, id);
		return t == null ? null : lihat(t);
	}

	/** The archive entry of {@code id} in any place (family that moved), or null. */
	public static @Nullable Entri cariDiMana(WargaData data, int id) {
		for (WargaData.Situs s : data.semuaSitus()) {
			Entri e = cari(s, id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	/** All entries of a place, oldest first (a copy). */
	public static List<Entri> semua(WargaData.Situs s) {
		List<Entri> out = new ArrayList<>(s.arsip.size());
		for (int i = 0; i < s.arsip.size(); i++) {
			out.add(lihat(s.arsip.getCompoundOrEmpty(i)));
		}
		return out;
	}

	/** The newest entry that last held slot {@code slot} (the previous job holder, for {@link Pekerjaan}), or null. */
	public static @Nullable Entri terakhirDiSlot(WargaData.Situs s, int slot) {
		for (int i = s.arsip.size() - 1; i >= 0; i--) {
			CompoundTag t = s.arsip.getCompoundOrEmpty(i);
			if (t.getIntOr("s", Warga.TANPA_SLOT) == slot) {
				return lihat(t);
			}
		}
		return null;
	}

	/** The entry resting under gravestone {@code nisan} (the newest), or null. */
	public static @Nullable Entri diNisan(WargaData.Situs s, int nisan) {
		for (int i = s.arsip.size() - 1; i >= 0; i--) {
			CompoundTag t = s.arsip.getCompoundOrEmpty(i);
			if (t.getIntOr("h", -1) == nisan) {
				return lihat(t);
			}
		}
		return null;
	}

	/**
	 * The next gravestone of a place with {@code jumlah} gravestones (titik {@code nisan_1..n}): stones in turn, so the
	 * newest grave replaces the oldest once the hill is full; -1 when the place has none (the name lives on in the tree).
	 */
	public static int nisanBerikut(WargaData data, WargaData.Situs s, int jumlah) {
		if (jumlah <= 0) {
			return -1;
		}
		int n = s.stat.getIntOr(NISAN_N, 0);
		s.stat.putInt(NISAN_N, n + 1);
		data.ubah();
		return 1 + Math.floorMod(n, jumlah);
	}

	public static Entri lihat(CompoundTag t) {
		return new Entri(t.getIntOr("i", 0), t.getByteOr("m", (byte) 0) & 0xFF, t.getShortOr("n", (short) 0) & 0xFFFF,
				t.getByteOr("g", (byte) 0) & 0xFF, t.getByteOr("k", Warga.PRIA) == Warga.WANITA, t.getLongOr("l", 0L),
				t.getLongOr("w", 0L), t.getIntOr("a", Warga.TANPA), t.getIntOr("b", Warga.TANPA), t.getIntOr("p", Warga.TANPA),
				t.getIntArray("c").orElse(new int[0]), t.getIntOr("h", -1), t.getStringOr("j", ""), t.getIntOr("s", Warga.TANPA_SLOT),
				t.getByteOr("q", (byte) 0), t.getStringOr("t", SEBAB_USIA), t.getStringOr("tk", ""));
	}

	// ================================================================== records

	/** Per-resident and per-place records of 42A-2 (SavedData {@code kultivator:kota_warga_catatan}, overworld). */
	public static final class Catatan extends SavedData {

		public static final Codec<Catatan> CODEC = CompoundTag.CODEC.xmap(Catatan::fromTag, Catatan::toTag);
		public static final SavedDataType<Catatan> TYPE = new SavedDataType<>(Kultivator.id("kota_warga_catatan"), Catatan::new, CODEC,
				DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		private final Map<Integer, CompoundTag> warga = new HashMap<>();
		private final Map<Long, CompoundTag> tempat = new HashMap<>();

		public Catatan() {
		}

		/** The mutable record of resident {@code id} (created empty); call {@link #ubah()} after a change. */
		public CompoundTag warga(int id) {
			return warga.computeIfAbsent(id, k -> new CompoundTag());
		}

		public @Nullable CompoundTag peekWarga(int id) {
			return warga.get(id);
		}

		/** The mutable record of place {@code situs} (created empty). */
		public CompoundTag tempat(long situs) {
			return tempat.computeIfAbsent(situs, k -> new CompoundTag());
		}

		/** Forgets a resident (archived for good). */
		public void lupakan(int id) {
			if (warga.remove(id) != null) {
				setDirty();
			}
		}

		public void ubah() {
			setDirty();
		}

		private static Catatan fromTag(CompoundTag tag) {
			Catatan c = new Catatan();
			CompoundTag ws = tag.getCompoundOrEmpty("warga");
			for (String key : ws.keySet()) {
				try {
					c.warga.put(Integer.parseInt(key), ws.getCompoundOrEmpty(key).copy());
				} catch (NumberFormatException ignored) {
					// a hand-edited key; skip it
				}
			}
			CompoundTag ts = tag.getCompoundOrEmpty("tempat");
			for (String key : ts.keySet()) {
				try {
					c.tempat.put(Long.parseLong(key), ts.getCompoundOrEmpty(key).copy());
				} catch (NumberFormatException ignored) {
					// skip
				}
			}
			return c;
		}

		private CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			CompoundTag ws = new CompoundTag();
			for (Map.Entry<Integer, CompoundTag> e : warga.entrySet()) {
				if (!e.getValue().isEmpty()) {
					ws.put(Integer.toString(e.getKey()), e.getValue().copy());
				}
			}
			tag.put("warga", ws);
			CompoundTag ts = new CompoundTag();
			for (Map.Entry<Long, CompoundTag> e : tempat.entrySet()) {
				if (!e.getValue().isEmpty()) {
					ts.put(Long.toString(e.getKey()), e.getValue().copy());
				}
			}
			tag.put("tempat", ts);
			return tag;
		}
	}

	/** The records of the running world, or null without a server. */
	public static @Nullable Catatan catatan() {
		MinecraftServer server = KotaHidup.server();
		return server == null ? null : catatan(server);
	}

	public static Catatan catatan(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(Catatan.TYPE);
	}

	private ArsipWarga() {
	}
}
