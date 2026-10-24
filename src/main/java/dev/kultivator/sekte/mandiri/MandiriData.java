package dev.kultivator.sekte.mandiri;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
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
import java.util.List;
import java.util.UUID;

/**
 * The hall records of Murid Mandiri (DESIGN 39.2.2), global saved data {@code kultivator:mandiri}: one compound per hall
 * owner (UUID string) with the sub-compounds of {@code MandiriIds.A_*}. Only 39A changes this schema (contract C39.A4);
 * each key is written by the module named at it, through {@code MandiriApi.aula}/{@code simpanHarian}/{@code kabar}.
 * Everything lives on as data while the disciples' bodies are unloaded or away. Server thread only.
 * <p>
 * Sizes stay small (39.9): a diary ring of {@link #HARIAN_MAKS} per soul (ordinary entries are dropped before
 * important ones), {@link #KABAR_MAKS} news lines per hall, the money book of the last {@link #KAS_HARI} days, and a
 * bounded event queue per unloaded soul.
 */
public final class MandiriData extends SavedData {

	/** Diary entries kept per soul. */
	public static final int HARIAN_MAKS = 40;
	/** News lines kept per hall. */
	public static final int KABAR_MAKS = 30;
	/** Days the money book keeps. */
	public static final int KAS_HARI = 7;
	/** Money-book lines kept per hall at most (about 7 days of a full hall). */
	public static final int KAS_MAKS = 240;
	/** Waiting events per unloaded soul. */
	public static final int ANTRE_MAKS = 12;

	public static final Codec<MandiriData> CODEC = CompoundTag.CODEC.xmap(MandiriData::fromTag, MandiriData::toTag);
	public static final SavedDataType<MandiriData> TYPE = new SavedDataType<>(Kultivator.id(MandiriIds.DATA_MANDIRI), MandiriData::new,
			CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** owner UUID string -> hall record. */
	private final CompoundTag halls;

	public MandiriData() {
		this.halls = new CompoundTag();
	}

	private MandiriData(CompoundTag halls) {
		this.halls = halls;
	}

	private static MandiriData fromTag(CompoundTag tag) {
		return new MandiriData(tag.getCompoundOrEmpty("aula").copy());
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.put("aula", halls.copy());
		return tag;
	}

	public static MandiriData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== records

	/** The live hall record of {@code owner} (created when missing). */
	public CompoundTag aula(UUID owner) {
		return child(halls, owner.toString());
	}

	/** The live sub-compound {@code key} of the hall record (created when missing). */
	public CompoundTag bagian(UUID owner, String key) {
		return child(aula(owner), key);
	}

	/** True when {@code owner} has a record at all (the board lists only real halls). */
	public boolean ada(UUID owner) {
		return owner != null && halls.contains(owner.toString());
	}

	// ================================================================== diary (39.8.1)

	public void tambahHarian(UUID owner, UUID soul, EntriHarian entri) {
		CompoundTag rings = bagian(owner, MandiriIds.A_HARIAN);
		ListTag list = rings.getListOrEmpty(soul.toString()).copy();
		list.add(entri.toTag());
		while (list.size() > HARIAN_MAKS) {
			int drop = 0;
			for (int i = 0; i < list.size() - 1; i++) {
				if (!list.getCompoundOrEmpty(i).getBooleanOr("penting", false)) {
					drop = i;
					break;
				}
			}
			list.remove(drop);
		}
		rings.put(soul.toString(), list);
		setDirty();
	}

	/** The soul's diary entries, oldest first. */
	public List<EntriHarian> harian(UUID owner, UUID soul) {
		ListTag list = aula(owner).getCompoundOrEmpty(MandiriIds.A_HARIAN).getListOrEmpty(soul.toString());
		List<EntriHarian> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			out.add(EntriHarian.fromTag(list.getCompoundOrEmpty(i)));
		}
		return out;
	}

	/** Forgets one soul's diary (the reset command; a disciple that left keeps its book until the hall is reset). */
	public void lupakanHarian(UUID owner, UUID soul) {
		bagian(owner, MandiriIds.A_HARIAN).remove(soul.toString());
		setDirty();
	}

	// ================================================================== news feed (39.7.4)

	public void tambahKabar(MinecraftServer server, UUID owner, @Nullable UUID soul, String kunci, boolean penting, String... arg) {
		CompoundTag record = aula(owner);
		ListTag list = record.getListOrEmpty(MandiriIds.A_KABAR).copy();
		CompoundTag line = new CompoundTag();
		line.putLong("hari", Ingatan.today(server));
		line.putInt("jam", Irama.jam(server.overworld()));
		if (soul != null) {
			line.putString("soul", soul.toString());
		}
		line.putString("kunci", kunci);
		ListTag args = new ListTag();
		if (arg != null) {
			for (String value : arg) {
				args.add(StringTag.valueOf(value == null ? "" : value));
			}
		}
		line.put("arg", args);
		if (penting) {
			line.putBoolean("penting", true);
		}
		list.add(line);
		while (list.size() > KABAR_MAKS) {
			list.remove(0);
		}
		record.put(MandiriIds.A_KABAR, list);
		setDirty();
	}

	/** The news feed, oldest first (live tags; read only). */
	public ListTag kabar(UUID owner) {
		return aula(owner).getListOrEmpty(MandiriIds.A_KABAR);
	}

	// ================================================================== money book (39.6.4, 39.6.11)

	/** Records a purse or pocket-money movement of {@code jumlah} NB ({@code sebab}: {@code saku}, {@code beli}, ...). */
	public void catatKas(UUID owner, UUID soul, long hari, long jumlah, String sebab) {
		CompoundTag record = aula(owner);
		ListTag list = record.getListOrEmpty(MandiriIds.A_KAS).copy();
		CompoundTag line = new CompoundTag();
		line.putLong("hari", hari);
		line.putString("soul", soul.toString());
		line.putLong("jumlah", jumlah);
		line.putString("sebab", sebab == null ? "" : sebab);
		list.add(line);
		while (!list.isEmpty() && (list.size() > KAS_MAKS || list.getCompoundOrEmpty(0).getLongOr("hari", hari) < hari - KAS_HARI)) {
			list.remove(0);
		}
		record.put(MandiriIds.A_KAS, list);
		setDirty();
	}

	/** Pocket money paid out by the hall in the last {@link #KAS_HARI} days (NB). */
	public long sakuMinggu(UUID owner) {
		ListTag list = aula(owner).getListOrEmpty(MandiriIds.A_KAS);
		long sum = 0L;
		for (int i = 0; i < list.size(); i++) {
			CompoundTag line = list.getCompoundOrEmpty(i);
			if ("saku".equals(line.getStringOr("sebab", ""))) {
				sum += line.getLongOr("jumlah", 0L);
			}
		}
		return sum;
	}

	// ================================================================== events for unloaded souls (39A)

	/** Queues {@code event} ({@code {jenis, ...}}) for {@code soul}; applied at its next load by {@code CitaCita.terapkanAntre}. */
	public void antre(UUID owner, UUID soul, CompoundTag event) {
		CompoundTag queues = bagian(owner, MandiriIds.A_ANTRE);
		ListTag list = queues.getListOrEmpty(soul.toString()).copy();
		list.add(event);
		while (list.size() > ANTRE_MAKS) {
			list.remove(0);
		}
		queues.put(soul.toString(), list);
		setDirty();
	}

	/** Takes (and clears) the soul's waiting events. */
	public List<CompoundTag> ambilAntre(UUID owner, UUID soul) {
		CompoundTag queues = aula(owner).getCompoundOrEmpty(MandiriIds.A_ANTRE);
		ListTag list = queues.getListOrEmpty(soul.toString());
		if (list.isEmpty()) {
			return List.of();
		}
		List<CompoundTag> out = new ArrayList<>(list.size());
		for (Tag tag : list) {
			if (tag instanceof CompoundTag compound) {
				out.add(compound.copy());
			}
		}
		bagian(owner, MandiriIds.A_ANTRE).remove(soul.toString());
		setDirty();
		return out;
	}

	// ================================================================== reset

	/** Forgets every trace of one soul in the hall record (the {@code reset} test command). */
	public void lupakan(UUID owner, UUID soul) {
		CompoundTag record = aula(owner);
		String key = soul.toString();
		for (String section : new String[]{MandiriIds.A_HARIAN, MandiriIds.A_JALAN, MandiriIds.A_PENDING, MandiriIds.A_ANTRE,
				MandiriIds.A_TUMPANG}) {
			CompoundTag tag = record.getCompound(section).orElse(null);
			if (tag != null) {
				tag.remove(key);
			}
		}
		setDirty();
	}

	private static CompoundTag child(CompoundTag parent, String key) {
		CompoundTag tag = parent.getCompound(key).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			parent.put(key, tag);
		}
		return tag;
	}
}
