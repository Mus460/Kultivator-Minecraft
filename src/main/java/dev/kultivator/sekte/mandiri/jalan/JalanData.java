package dev.kultivator.sekte.mandiri.jalan;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The journeys of every hall (DESIGN 39.6.2, 39.6.11), global saved data {@code kultivator:mandiri_jalan}: the one
 * authoritative record of each running journey by soul ({@link Rencana} plus its resolved stages), the arrivals waiting
 * for their body (the TIBA scene), disciples' purchases per town and day (shared stock of all halls, 39.6.5) and the loot
 * of each hall per week (39.6.9). {@code MandiriData.jalan.<soul>} only mirrors a summary for the board. Small: a journey
 * is a few hundred bytes; arrivals live until the body arrives; stock is kept for today only.
 */
public final class JalanData extends SavedData {

	public static final Codec<JalanData> CODEC = CompoundTag.CODEC.xmap(JalanData::fromTag, JalanData::toTag);
	public static final SavedDataType<JalanData> TYPE = new SavedDataType<>(Kultivator.id(JalanIds.DATA), JalanData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** soul -> journey. */
	private final CompoundTag aktif;
	/** soul -> arrival waiting for the body. */
	private final CompoundTag tiba;
	/** town (decimal) -> {hari, n: {item: count}}. */
	private final CompoundTag stok;
	/** owner -> list of {hari, n}. */
	private final CompoundTag rampasan;
	/** town (decimal) -> stop -> the keeper disciples know there {a, b (name keys), jumpa}. */
	private final CompoundTag pedagang;

	public JalanData() {
		this(new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag(), new CompoundTag());
	}

	private JalanData(CompoundTag aktif, CompoundTag tiba, CompoundTag stok, CompoundTag rampasan, CompoundTag pedagang) {
		this.aktif = aktif;
		this.tiba = tiba;
		this.stok = stok;
		this.rampasan = rampasan;
		this.pedagang = pedagang;
	}

	private static JalanData fromTag(CompoundTag tag) {
		return new JalanData(tag.getCompoundOrEmpty("aktif").copy(), tag.getCompoundOrEmpty("tiba").copy(),
				tag.getCompoundOrEmpty("stok").copy(), tag.getCompoundOrEmpty("rampasan").copy(), tag.getCompoundOrEmpty("pedagang").copy());
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.put("aktif", aktif.copy());
		tag.put("tiba", tiba.copy());
		tag.put("stok", stok.copy());
		tag.put("rampasan", rampasan.copy());
		tag.put("pedagang", pedagang.copy());
		return tag;
	}

	public static JalanData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== journeys

	/** The live journey tag of {@code soul}, or null. */
	public @Nullable CompoundTag perjalanan(UUID soul) {
		return soul == null ? null : aktif.getCompound(soul.toString()).orElse(null);
	}

	/** Every travelling soul (copy of the key set). */
	public List<UUID> jiwaAktif() {
		List<UUID> out = new ArrayList<>(aktif.size());
		for (String key : aktif.keySet()) {
			try {
				out.add(UUID.fromString(key));
			} catch (IllegalArgumentException ignored) {
				// a broken key is dropped at the next write
			}
		}
		return out;
	}

	public int jumlahAktif() {
		return aktif.size();
	}

	public void simpan(UUID soul, CompoundTag journey) {
		aktif.put(soul.toString(), journey);
		setDirty();
	}

	public void hapus(UUID soul) {
		if (aktif.contains(soul.toString())) {
			aktif.remove(soul.toString());
			setDirty();
		}
	}

	// ================================================================== arrivals (TIBA scene)

	public @Nullable CompoundTag tiba(UUID soul) {
		return soul == null ? null : tiba.getCompound(soul.toString()).orElse(null);
	}

	public boolean adaTiba(UUID soul) {
		return soul != null && tiba.contains(soul.toString());
	}

	public void simpanTiba(UUID soul, CompoundTag arrival) {
		tiba.put(soul.toString(), arrival);
		setDirty();
	}

	public void hapusTiba(UUID soul) {
		if (tiba.contains(soul.toString())) {
			tiba.remove(soul.toString());
			setDirty();
		}
	}

	// ================================================================== shared town stock (39.6.5)

	/** Units of {@code itemId} disciples bought in {@code town} on {@code hari}. */
	public int terbeli(long town, String itemId, long hari) {
		CompoundTag t = stok.getCompoundOrEmpty(Long.toString(town));
		return t.getLongOr("hari", -1L) == hari ? t.getCompoundOrEmpty("n").getIntOr(itemId, 0) : 0;
	}

	/** Adds {@code n} bought units (the day's record replaces an older one). */
	public void tambahTerbeli(long town, String itemId, int n, long hari) {
		String key = Long.toString(town);
		CompoundTag t = stok.getCompound(key).orElse(null);
		if (t == null || t.getLongOr("hari", -1L) != hari) {
			t = new CompoundTag();
			t.putLong("hari", hari);
			t.put("n", new CompoundTag());
			stok.put(key, t);
		}
		CompoundTag counts = t.getCompound("n").orElse(null);
		if (counts == null) {
			counts = new CompoundTag();
			t.put("n", counts);
		}
		counts.putInt(itemId, counts.getIntOr(itemId, 0) + Math.max(0, n));
		setDirty();
	}

	/** Drops stock records older than {@code hari} (called once a day). */
	public void bersihkanStok(long hari) {
		List<String> old = new ArrayList<>();
		for (String key : stok.keySet()) {
			if (stok.getCompoundOrEmpty(key).getLongOr("hari", -1L) < hari) {
				old.add(key);
			}
		}
		for (String key : old) {
			stok.remove(key);
		}
		if (!old.isEmpty()) {
			setDirty();
		}
	}

	// ================================================================== hall loot per week (39.6.9)

	/** Loot stacks the hall's journeys brought in the 7 days up to {@code hari}. */
	public int rampasanMinggu(UUID owner, long hari) {
		ListTag list = rampasan.getListOrEmpty(owner.toString());
		int sum = 0;
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = list.getCompoundOrEmpty(i);
			if (one.getLongOr("hari", -100L) > hari - 7L) {
				sum += one.getIntOr("n", 0);
			}
		}
		return sum;
	}

	public void tambahRampasan(UUID owner, long hari, int n) {
		ListTag list = rampasan.getListOrEmpty(owner.toString()).copy();
		while (!list.isEmpty() && list.getCompoundOrEmpty(0).getLongOr("hari", hari) <= hari - 7L) {
			list.remove(0);
		}
		CompoundTag one = new CompoundTag();
		one.putLong("hari", hari);
		one.putInt("n", n);
		list.add(one);
		rampasan.put(owner.toString(), list);
		setDirty();
	}

	// ================================================================== the keepers disciples know (39.8.3)

	/**
	 * The keeper of {@code stop} in {@code town} as disciples know it: its two name keys and how often disciples met it
	 * ({@code jumpa}). Created with {@code bawaan} the first time; a town body that meets the real keeper replaces the
	 * names ({@link #kenalPedagang}).
	 */
	public CompoundTag pedagang(long town, String stop, java.util.List<String> bawaan) {
		CompoundTag t = pedagang.getCompound(Long.toString(town)).orElse(null);
		if (t == null) {
			t = new CompoundTag();
			pedagang.put(Long.toString(town), t);
		}
		CompoundTag one = t.getCompound(stop).orElse(null);
		if (one == null) {
			one = new CompoundTag();
			one.putString("a", bawaan.get(0));
			one.putString("b", bawaan.get(1));
			t.put(stop, one);
			setDirty();
		}
		return one;
	}

	/** One more meeting with the keeper of {@code stop}; returns the count. */
	public int jumpaPedagang(long town, String stop) {
		CompoundTag one = pedagang.getCompoundOrEmpty(Long.toString(town)).getCompound(stop).orElse(null);
		if (one == null) {
			return 0;
		}
		int n = one.getIntOr("jumpa", 0) + 1;
		one.putInt("jumpa", n);
		setDirty();
		return n;
	}

	/** The town body met the real keeper: its shown name keys become the ones disciples remember. */
	public void kenalPedagang(long town, String stop, java.util.List<String> nama) {
		CompoundTag one = pedagang(town, stop, nama);
		if (!one.getBooleanOr("asli", false)) {
			one.putString("a", nama.get(0));
			one.putString("b", nama.get(1));
			one.putBoolean("asli", true);
			setDirty();
		}
	}
}
