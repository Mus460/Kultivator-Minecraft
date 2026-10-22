package dev.kultivator.sekte.tetua;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Jabatan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where module E keeps its state (DESIGN 36.2.6). Per hall, the section {@code sekte_tetua} of the owner's
 * {@code HallData} ({@code Halls.section}): the six offices ({@code jabatan}: holder soul, name, realm snapshot, deputy),
 * the report buffer fed by {@code SekteBus.SECT_LOG} ({@code log}), the last morning report, the heir and the lineage of
 * Sect Masters ({@code silsilah}), the Founding Patriarch's renown watch, envoys waiting to be welcomed and once-only
 * marks. Per disciple, {@code DiscipleData.extra().jabatan} ({@code SekteIds.EXTRA_E}): its office, whether it is a
 * deputy, the day it took office and the order a running ceremony replaced. Server thread only; every writer calls
 * {@link #dirty()}.
 */
final class TetuaData {

	static final String OFFICES = "jabatan";
	static final String LOG = "log";
	static final String REPORT = "laporan";
	static final String REPORT_DAY = "laporan_hari";
	static final String HEIR = "pewaris";
	static final String HEIR_NAME = "pewaris_nama";
	static final String MASTER = "ketua";
	static final String MASTER_NAME = "ketua_nama";
	static final String GENERATION = "generasi";
	static final String LINEAGE = "silsilah";
	static final String FOUNDER = "leluhur";
	static final String RENOWN_SEEN = "renown_lihat";
	static final String RENOWN_REST = "renown_sisa";
	static final String UNFAIR = "tidak_adil";
	static final String GREETED = "disapa";
	static final String ENVOYS = "utusan";
	static final String LAW = "hukum";
	static final String AUTO_DAY = "terima_hari";
	static final String FIRST_DAY = "hari_pertama";
	static final String WORK = "kerja";
	static final String LAST_OFFICE = "urusan_akhir";

	/** Per-disciple keys inside {@code extra.jabatan}. */
	static final String D_OFFICE = "id";
	static final String D_DEPUTY = "wakil";
	static final String D_SINCE = "sejak";
	static final String D_CEREMONY_PREV = "upacara_mode";
	static final String D_GOLD = "emas";

	/** Report buffer: lines older than one world day are dropped, at most this many kept. */
	static final int LOG_MAX = 48;

	static CompoundTag hall(UUID owner) {
		return Halls.section(owner, SekteIds.hallSection(SekteIds.E));
	}

	static void dirty() {
		Halls.setDirty();
	}

	static CompoundTag child(CompoundTag parent, String key) {
		CompoundTag tag = parent.getCompound(key).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			parent.put(key, tag);
		}
		return tag;
	}

	static ListTag list(CompoundTag parent, String key) {
		ListTag list = parent.getList(key).orElse(null);
		if (list == null) {
			list = new ListTag();
			parent.put(key, list);
		}
		return list;
	}

	static @Nullable UUID uuid(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	static Component name(CompoundTag tag, String key) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(Component.literal("?"));
	}

	static void putName(CompoundTag tag, String key, @Nullable Component name) {
		tag.store(key, ComponentSerialization.CODEC, name == null ? Component.literal("?") : name);
	}

	// ================================================================== offices

	/** The office entry of {@code office} (empty compound when vacant). */
	static CompoundTag office(UUID owner, Jabatan office) {
		return hall(owner).getCompoundOrEmpty(OFFICES).getCompoundOrEmpty(office.id());
	}

	static @Nullable UUID holder(UUID owner, Jabatan office) {
		return uuid(office(owner, office).getStringOr("soul", ""));
	}

	/** Every filled office and its holder. */
	static Map<Jabatan, UUID> offices(UUID owner) {
		Map<Jabatan, UUID> map = new EnumMap<>(Jabatan.class);
		CompoundTag offices = hall(owner).getCompoundOrEmpty(OFFICES);
		for (Jabatan jabatan : Jabatan.values()) {
			UUID soul = uuid(offices.getCompoundOrEmpty(jabatan.id()).getStringOr("soul", ""));
			if (soul != null) {
				map.put(jabatan, soul);
			}
		}
		return map;
	}

	/** The office {@code soul} holds in the hall, or null. */
	static @Nullable Jabatan officeOf(UUID owner, UUID soul) {
		CompoundTag offices = hall(owner).getCompoundOrEmpty(OFFICES);
		String key = soul.toString();
		for (Jabatan jabatan : Jabatan.values()) {
			if (key.equals(offices.getCompoundOrEmpty(jabatan.id()).getStringOr("soul", ""))) {
				return jabatan;
			}
		}
		return null;
	}

	static void setOffice(UUID owner, Jabatan office, UUID soul, Component name, int realm, boolean deputy, long day, boolean test) {
		CompoundTag entry = new CompoundTag();
		if (test) {
			// set by "uji perguruan tetua angkat" past the rank rule: the daily check leaves it be
			entry.putBoolean("uji", true);
		}
		entry.putString("soul", soul.toString());
		putName(entry, "name", name);
		entry.putInt("realm", realm);
		entry.putBoolean("wakil", deputy);
		entry.putLong("sejak", day);
		entry.putInt("urusan", 0);
		child(hall(owner), OFFICES).put(office.id(), entry);
		dirty();
	}

	static void clearOffice(UUID owner, Jabatan office) {
		CompoundTag offices = hall(owner).getCompound(OFFICES).orElse(null);
		if (offices != null && offices.contains(office.id())) {
			offices.remove(office.id());
			dirty();
		}
	}

	// ================================================================== per disciple

	static CompoundTag disciple(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(SekteIds.EXTRA_E);
	}

	static CompoundTag discipleSection(DiscipleData data) {
		return data.extraSection(SekteIds.EXTRA_E);
	}

	static @Nullable Jabatan discipleOffice(DiscipleData data) {
		return Jabatan.byId(disciple(data).getStringOr(D_OFFICE, ""));
	}

	// ================================================================== small lists

	static boolean listHas(CompoundTag parent, String key, String value) {
		for (Tag tag : parent.getListOrEmpty(key)) {
			if (value.equals(tag.asString().orElse(""))) {
				return true;
			}
		}
		return false;
	}

	/** Adds {@code value} once; the oldest are dropped past {@code max}. */
	static void listAdd(CompoundTag parent, String key, String value, int max) {
		if (listHas(parent, key, value)) {
			return;
		}
		ListTag list = list(parent, key);
		list.add(StringTag.valueOf(value));
		while (list.size() > max) {
			list.remove(0);
		}
		dirty();
	}

	/** The overworld clock now (day time, counting up across days). */
	static long clock(@Nullable MinecraftServer server) {
		return server == null ? 0L : server.overworld().getOverworldClockTime();
	}

	/** World hour 0-23 (the overworld's tick 0 is 06:00). */
	static int hour(@Nullable MinecraftServer server) {
		long time = Math.floorMod(clock(server), 24000L);
		return (int) ((time / 1000L + 6L) % 24L);
	}

	/** True between {@code from} and {@code to} o'clock (wrapping past midnight when {@code to < from}). */
	static boolean within(int hour, int from, int to) {
		return from < to ? hour >= from && hour < to : hour >= from || hour < to;
	}

	private TetuaData() {
	}
}
