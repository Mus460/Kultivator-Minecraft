package dev.kultivator.dunia.tempat.hidup;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:tempat_penduduk} in the overworld (DESIGN 38.N.1): only what changed about generated
 * residents, per place (site id) and resident id:
 * <ul>
 *   <li>{@code luka}: wounded until this game time (stays indoors, no body);</li>
 *   <li>{@code mati}: died at this game time; after three world days a newcomer takes the place ({@code gen} grows, so
 *       the look, voice and seed change while role and household stay);</li>
 *   <li>{@code kenal}: per player, how many times they met (a resident greets a well-known player by name);</li>
 *   <li>{@code tanda}: free markers of other modules (38ST quest giver, 38PR fear), set through {@link #tanda}.</li>
 * </ul>
 * Everything else is regenerated from the seed by {@link PendudukGen}, so a place of 650 residents costs nothing to save.
 */
public final class PendudukData extends SavedData {

	public static final Codec<PendudukData> CODEC = CompoundTag.CODEC.xmap(PendudukData::fromTag, PendudukData::toTag);
	public static final SavedDataType<PendudukData> TYPE = new SavedDataType<>(Kultivator.id("tempat_penduduk"), PendudukData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Three world days before a newcomer replaces a dead resident (38.N.1). */
	public static final long PENGGANTI = 3L * 24000L;

	/** Life state (detail of 38.N.1): nothing special. */
	public static final int SEHAT = 0;
	/** Back from a bad wound: limps with an arm in a sling for {@link #MASA_PULIH}, no work. */
	public static final int PULIH = 1;
	/** A housemate died: the household mourns for {@link #MASA_DUKA} (incense at home, tears, no market calls). */
	public static final int DUKA = 2;
	/** A newcomer's first day in the dead resident's place: unpacks, looks around, introduces itself. */
	public static final int DATANG = 3;
	public static final long MASA_PULIH = 24000L;
	public static final long MASA_DUKA = 2L * 24000L;
	public static final long MASA_DATANG = 24000L;
	/** Place-level key of the households in mourning (household id -> game time of the death). */
	private static final String DUKA_KEY = "_duka";

	private final Map<Long, CompoundTag> places = new HashMap<>();

	public PendudukData() {
	}

	public static PendudukData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private CompoundTag orang(long situs, int id) {
		CompoundTag place = places.computeIfAbsent(situs, key -> new CompoundTag());
		String key = Integer.toString(id);
		CompoundTag one = place.getCompoundOrEmpty(key);
		if (!place.contains(key)) {
			place.put(key, one);
		}
		return one;
	}

	private CompoundTag lihat(long situs, int id) {
		CompoundTag place = places.get(situs);
		return place == null ? new CompoundTag() : place.getCompoundOrEmpty(Integer.toString(id));
	}

	/** The resident as it is now: a newcomer's look and seed after a death, unchanged otherwise. */
	public Penduduk terapkan(long situs, Penduduk p) {
		int gen = lihat(situs, p.id()).getIntOr("gen", 0);
		if (gen == 0) {
			return p;
		}
		long benih = PendudukGen.mixSeed(p.benih() + gen * 0x2545F4914F6CDD1DL);
		int look = (int) (benih ^ benih >>> 32);
		return new Penduduk(p.id(), p.peran(), look, (int) Math.floorMod(benih >>> 40, 6L), p.rumah(), p.kasur(), p.kerja(), p.kelompok(), benih);
	}

	/** True while the resident is away: wounded, or dead and not yet replaced. Replaces the dead when their time came. */
	public boolean absen(long situs, int id, long now) {
		CompoundTag t = lihat(situs, id);
		if (t.isEmpty()) {
			return false;
		}
		long mati = t.getLongOr("mati", 0L);
		if (mati != 0L) {
			if (now - mati < PENGGANTI) {
				return true;
			}
			CompoundTag w = orang(situs, id);
			w.remove("mati");
			w.remove("luka");
			w.putInt("gen", w.getIntOr("gen", 0) + 1);
			w.putLong("datang", now);
			setDirty();
			return false;
		}
		return now < t.getLongOr("luka", 0L);
	}

	/** The resident was hurt badly: indoors until {@code sampai}. */
	public void luka(long situs, int id, long sampai) {
		CompoundTag t = orang(situs, id);
		if (t.getLongOr("luka", 0L) < sampai) {
			t.putLong("luka", sampai);
			setDirty();
		}
	}

	/** The resident died at {@code now} (a newcomer in three days). */
	public void mati(long situs, int id, long now) {
		mati(situs, id, 0, now);
	}

	/** The resident of household {@code rumahTangga} died at {@code now}: its housemates mourn (0 = lived alone). */
	public void mati(long situs, int id, int rumahTangga, long now) {
		orang(situs, id).putLong("mati", now);
		if (rumahTangga > 0) {
			CompoundTag place = places.computeIfAbsent(situs, key -> new CompoundTag());
			CompoundTag duka = place.getCompoundOrEmpty(DUKA_KEY);
			duka.putLong(Integer.toString(rumahTangga), now);
			place.put(DUKA_KEY, duka);
		}
		setDirty();
	}

	/**
	 * The life state of resident {@code id} of household {@code rumahTangga} at game time {@code now}: {@link #DATANG},
	 * {@link #PULIH}, {@link #DUKA} or {@link #SEHAT}. Read once per place cycle by {@link Pemanggil}.
	 */
	public int keadaan(long situs, int id, int rumahTangga, long now) {
		CompoundTag place = places.get(situs);
		if (place == null) {
			return SEHAT;
		}
		CompoundTag t = place.getCompoundOrEmpty(Integer.toString(id));
		long datang = t.getLongOr("datang", 0L);
		if (datang != 0L && now >= datang && now - datang < MASA_DATANG) {
			return DATANG;
		}
		long luka = t.getLongOr("luka", 0L);
		if (luka != 0L && now >= luka && now - luka < MASA_PULIH && t.getLongOr("mati", 0L) == 0L) {
			return PULIH;
		}
		if (rumahTangga > 0) {
			long mati = place.getCompoundOrEmpty(DUKA_KEY).getLongOr(Integer.toString(rumahTangga), 0L);
			if (mati != 0L && now >= mati && now - mati < MASA_DUKA) {
				return DUKA;
			}
		}
		return SEHAT;
	}

	/** Sets a life state for a preview (commands): the state starts now and lasts its usual time. */
	public void ujiKeadaan(long situs, int id, int rumahTangga, int keadaan, long now) {
		CompoundTag t = orang(situs, id);
		t.remove("datang");
		if (keadaan != PULIH && t.getLongOr("luka", 0L) <= now) {
			t.remove("luka");
		}
		switch (keadaan) {
			case DATANG -> t.putLong("datang", now);
			case PULIH -> t.putLong("luka", now);
			case DUKA -> {
				if (rumahTangga > 0) {
					CompoundTag place = places.computeIfAbsent(situs, key -> new CompoundTag());
					CompoundTag duka = place.getCompoundOrEmpty(DUKA_KEY);
					duka.putLong(Integer.toString(rumahTangga), now);
					place.put(DUKA_KEY, duka);
				}
			}
			default -> {
				CompoundTag place = places.get(situs);
				if (place != null && rumahTangga > 0) {
					place.getCompoundOrEmpty(DUKA_KEY).remove(Integer.toString(rumahTangga));
				}
			}
		}
		setDirty();
	}

	/** One more meeting with {@code player}; returns the new count (capped at 99). */
	public int kenal(long situs, int id, UUID player) {
		CompoundTag t = orang(situs, id);
		CompoundTag kenal = t.getCompoundOrEmpty("kenal");
		int n = Math.min(99, kenal.getIntOr(player.toString(), 0) + 1);
		kenal.putInt(player.toString(), n);
		t.put("kenal", kenal);
		setDirty();
		return n;
	}

	/** How many times {@code player} met the resident. */
	public int kenalan(long situs, int id, UUID player) {
		return lihat(situs, id).getCompoundOrEmpty("kenal").getIntOr(player.toString(), 0);
	}

	/** Sets marker {@code kunci} of a resident (0 removes it); for 38ST quest givers and 38PR. */
	public void tanda(long situs, int id, String kunci, int nilai) {
		CompoundTag t = orang(situs, id).getCompoundOrEmpty("tanda");
		if (nilai == 0) {
			t.remove(kunci);
		} else {
			t.putInt(kunci, nilai);
		}
		orang(situs, id).put("tanda", t);
		setDirty();
	}

	/** Marker {@code kunci} of a resident (0 when unset). */
	public int tandaDari(long situs, int id, String kunci) {
		return lihat(situs, id).getCompoundOrEmpty("tanda").getIntOr(kunci, 0);
	}

	/** Residents with any record at place {@code situs} (commands). */
	public int tercatat(long situs) {
		CompoundTag place = places.get(situs);
		return place == null ? 0 : place.size() - (place.contains(DUKA_KEY) ? 1 : 0);
	}

	private static PendudukData fromTag(CompoundTag tag) {
		PendudukData data = new PendudukData();
		CompoundTag all = tag.getCompoundOrEmpty("tempat");
		for (String key : all.keySet()) {
			try {
				data.places.put(Long.parseLong(key), all.getCompoundOrEmpty(key).copy());
			} catch (NumberFormatException ignored) {
				// not a site id
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag all = new CompoundTag();
		for (Map.Entry<Long, CompoundTag> e : places.entrySet()) {
			if (!e.getValue().isEmpty()) {
				all.put(Long.toString(e.getKey()), e.getValue().copy());
			}
		}
		CompoundTag tag = new CompoundTag();
		tag.put("tempat", all);
		return tag;
	}
}
