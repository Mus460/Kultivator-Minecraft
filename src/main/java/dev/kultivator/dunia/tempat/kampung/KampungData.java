package dev.kultivator.dunia.tempat.kampung;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the villages remember ({@code kultivator:kampung}, overworld storage; DESIGN 38.C.2, owner 38DS). One record per
 * village site, written when its {@code kampung/situs} anchor first loads:
 * <ul>
 *   <li>size ({@code dusun}, {@code desa}, {@code desa_besar}, {@code klan}) and style ({@code sedang}, {@code salju}, ...),
 *       the site anchor and the bell, so {@code KampungApi} and the tribute bonus answer while the village is unloaded;</li>
 *   <li>the day of the last Harvest Festival and of the last talented child;</li>
 *   <li>for a clan: each visitor's regard (-100..100, {@code kampung.klan.<site>.sikap} of 38.C.2.3), who beat the
 *       patriarch, who beat or yielded to the young master, the day of the patriarch's last invitation.</li>
 * </ul>
 * New worlds only: no version field, no migration.
 */
public final class KampungData extends SavedData {

	public static final Codec<KampungData> CODEC = CompoundTag.CODEC.xmap(KampungData::fromTag, KampungData::toTag);
	public static final SavedDataType<KampungData> TYPE = new SavedDataType<>(Kultivator.id("kampung"), KampungData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** One village. Mutable; call {@link KampungData#setDirty()} after a change (the helpers here do). */
	public static final class Desa {
		public final long situs;
		public String ukuran = "desa";
		public String gaya = "sedang";
		public BlockPos pusat = BlockPos.ZERO;
		public BlockPos lonceng = BlockPos.ZERO;
		public long hariPanen = -9999L;
		public long hariAnak = -9999L;
		final Map<UUID, Integer> sikap = new HashMap<>();
		final Set<UUID> patriarkKalah = new HashSet<>();
		final Set<UUID> tuanMudaKalah = new HashSet<>();
		final Set<UUID> mengalah = new HashSet<>();
		final Map<UUID, Long> undangan = new HashMap<>();

		Desa(long situs) {
			this.situs = situs;
		}

		public boolean klan() {
			return ukuran.equals("klan");
		}
	}

	private final Map<Long, Desa> desa = new HashMap<>();

	public KampungData() {
	}

	public static KampungData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The record of village {@code situs}, created when missing. */
	public Desa desa(long situs) {
		return desa.computeIfAbsent(situs, id -> {
			setDirty();
			return new Desa(id);
		});
	}

	public @Nullable Desa cari(long situs) {
		return desa.get(situs);
	}

	public Collection<Desa> semua() {
		return desa.values();
	}

	/** The village whose bell (or centre) lies within {@code range} of {@code pos}, nearest first, or null. */
	public @Nullable Desa dekat(BlockPos pos, double range) {
		Desa best = null;
		double bestD = range * range;
		for (Desa d : desa.values()) {
			BlockPos at = d.lonceng.equals(BlockPos.ZERO) ? d.pusat : d.lonceng;
			double dist = at.distSqr(pos);
			if (dist <= bestD) {
				bestD = dist;
				best = d;
			}
		}
		return best;
	}

	/** Sets the village's identity (no-op when unchanged). */
	public void kenal(long situs, String ukuran, String gaya, BlockPos pusat) {
		Desa d = desa(situs);
		if (!d.ukuran.equals(ukuran) || !d.gaya.equals(gaya) || !d.pusat.equals(pusat)) {
			d.ukuran = ukuran;
			d.gaya = gaya;
			d.pusat = pusat.immutable();
			setDirty();
		}
	}

	public void lonceng(long situs, BlockPos pos) {
		Desa d = desa(situs);
		if (!d.lonceng.equals(pos)) {
			d.lonceng = pos.immutable();
			setDirty();
		}
	}

	public void hariPanen(Desa d, long day) {
		d.hariPanen = day;
		setDirty();
	}

	public void hariAnak(Desa d, long day) {
		d.hariAnak = day;
		setDirty();
	}

	// ------------------------------------------------------------------ clan regard

	public int sikap(Desa d, UUID player) {
		return d.sikap.getOrDefault(player, 0);
	}

	/** Changes a visitor's regard of clan {@code d} (clamped to -100..100); returns the new value. */
	public int ubahSikap(Desa d, UUID player, int delta) {
		int v = Mth.clamp(sikap(d, player) + delta, -100, 100);
		d.sikap.put(player, v);
		setDirty();
		return v;
	}

	public boolean patriarkKalah(Desa d, UUID player) {
		return d.patriarkKalah.contains(player);
	}

	public void setPatriarkKalah(Desa d, UUID player) {
		if (d.patriarkKalah.add(player)) {
			setDirty();
		}
	}

	public boolean tuanMudaKalah(Desa d, UUID player) {
		return d.tuanMudaKalah.contains(player);
	}

	public void setTuanMudaKalah(Desa d, UUID player) {
		d.mengalah.remove(player);
		if (d.tuanMudaKalah.add(player)) {
			setDirty();
		}
	}

	public boolean mengalah(Desa d, UUID player) {
		return d.mengalah.contains(player);
	}

	public void setMengalah(Desa d, UUID player) {
		if (d.mengalah.add(player)) {
			setDirty();
		}
	}

	/** True (and remembered) when the patriarch may invite {@code player} on {@code day}. */
	public boolean ambilUndangan(Desa d, UUID player, long day) {
		Long last = d.undangan.get(player);
		if (last != null && last >= day) {
			return false;
		}
		d.undangan.put(player, day);
		setDirty();
		return true;
	}

	// ------------------------------------------------------------------ storage

	private static KampungData fromTag(CompoundTag tag) {
		KampungData data = new KampungData();
		CompoundTag all = tag.getCompoundOrEmpty("desa");
		for (String key : all.keySet()) {
			long id;
			try {
				id = Long.parseLong(key);
			} catch (NumberFormatException e) {
				continue;
			}
			CompoundTag t = all.getCompoundOrEmpty(key);
			Desa d = new Desa(id);
			d.ukuran = t.getStringOr("ukuran", "desa");
			d.gaya = t.getStringOr("gaya", "sedang");
			d.pusat = BlockPos.of(t.getLongOr("pusat", 0L));
			d.lonceng = BlockPos.of(t.getLongOr("lonceng", 0L));
			d.hariPanen = t.getLongOr("panen", -9999L);
			d.hariAnak = t.getLongOr("anak", -9999L);
			CompoundTag sikap = t.getCompoundOrEmpty("sikap");
			for (String u : sikap.keySet()) {
				UUID uuid = uuid(u);
				if (uuid != null) {
					d.sikap.put(uuid, Mth.clamp(sikap.getIntOr(u, 0), -100, 100));
				}
			}
			CompoundTag undangan = t.getCompoundOrEmpty("undangan");
			for (String u : undangan.keySet()) {
				UUID uuid = uuid(u);
				if (uuid != null) {
					d.undangan.put(uuid, undangan.getLongOr(u, 0L));
				}
			}
			readSet(t.getCompoundOrEmpty("patriark"), d.patriarkKalah);
			readSet(t.getCompoundOrEmpty("tuan_muda"), d.tuanMudaKalah);
			readSet(t.getCompoundOrEmpty("mengalah"), d.mengalah);
			data.desa.put(id, d);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag all = new CompoundTag();
		for (Desa d : desa.values()) {
			CompoundTag t = new CompoundTag();
			t.putString("ukuran", d.ukuran);
			t.putString("gaya", d.gaya);
			t.putLong("pusat", d.pusat.asLong());
			t.putLong("lonceng", d.lonceng.asLong());
			t.putLong("panen", d.hariPanen);
			t.putLong("anak", d.hariAnak);
			CompoundTag sikap = new CompoundTag();
			d.sikap.forEach((u, v) -> sikap.putInt(u.toString(), v));
			t.put("sikap", sikap);
			CompoundTag undangan = new CompoundTag();
			d.undangan.forEach((u, v) -> undangan.putLong(u.toString(), v));
			t.put("undangan", undangan);
			t.put("patriark", writeSet(d.patriarkKalah));
			t.put("tuan_muda", writeSet(d.tuanMudaKalah));
			t.put("mengalah", writeSet(d.mengalah));
			all.put(Long.toString(d.situs), t);
		}
		tag.put("desa", all);
		return tag;
	}

	private static void readSet(CompoundTag t, Set<UUID> out) {
		for (String u : t.keySet()) {
			UUID uuid = uuid(u);
			if (uuid != null) {
				out.add(uuid);
			}
		}
	}

	private static CompoundTag writeSet(Set<UUID> set) {
		CompoundTag t = new CompoundTag();
		for (UUID u : set) {
			t.putBoolean(u.toString(), true);
		}
		return t;
	}

	private static @Nullable UUID uuid(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
