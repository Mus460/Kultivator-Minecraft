package dev.kultivator.sekte.paviliun;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module A's saved data (DESIGN 36.2.5). Per hall, in the hall section {@code sekte_paviliun}
 * ({@code Halls.section(owner, SekteIds.hallSection(SekteIds.A))}):
 * <ul>
 * <li>{@code rak}: the Rak Kitab, one entry per deposited book or jade slip ({@link Kitab}): its id, kind, the name the
 * player sees, the technique ids it can teach (resolved on deposit) and the item itself (registry-aware, so it can be
 * taken back exactly as it came);</li>
 * <li>{@code makam}: the graves of the cemetery ({@link Grave}), newest first; {@code silsilah}: the Kitab Silsilah, the
 * graves moved out of a full cemetery (never lost; beyond {@code silsilahMaks} the oldest are folded into a count);</li>
 * <li>the rites: {@code rite_day} (the last rite of any kind, for the blessing's freshness), {@code incense_day},
 * {@code qingming_day}; and the pavilion's tallies.</li>
 * </ul>
 * Per disciple, in {@code DiscipleData.extra().kitab} ({@link SekteIds#EXTRA_A}): the technique being studied, its
 * points, the rack entry it reads and the day it last learned (one technique a day). Server thread only.
 */
final class PaviliunData {

	static final String SECTION = SekteIds.hallSection(SekteIds.A);

	static final String RAK = "rak";
	static final String NEXT_ID = "next_id";
	static final String MAKAM = "makam";
	static final String SILSILAH = "silsilah";
	static final String SILSILAH_FOLDED = "silsilah_lipat";
	static final String RITE_DAY = "rite_day";
	static final String INCENSE_DAY = "incense_day";
	static final String QINGMING_DAY = "qingming_day";
	static final String FOUNDED_DAY = "mulai_day";
	static final String LEARNED = "learned";
	static final String BURIED = "buried";

	// disciple extra
	static final String SPEC = "spec";
	static final String POINTS = "pts";
	static final String BOOK = "book";
	static final String DAY_DONE = "day_done";
	static final String TOTAL = "total";

	/** A rack entry: {@code kind} {@code buku} (an enchanted book) or {@code giok} (a jade slip). */
	record Kitab(int id, String kind, Component name, List<String> specs, CompoundTag item, String by, long day) {
	}

	/** One deed engraved on a grave: a memory's lang key, its argument, its weight. */
	record Deed(String text, String arg, int weight) {
	}

	/**
	 * A grave: the soul and its name, the rank step at death, the day, the cause ({@code cause} a lang key suffix,
	 * {@code causeArg} plain text such as the killer's name), up to three deeds, enshrined on the altar, the tombstone
	 * slot ({@code -1} without a built cemetery), its life number (a revived and fallen-again soul keeps one grave).
	 */
	record Grave(UUID soul, Component name, int step, long day, String cause, String causeArg, List<Deed> deeds, boolean enshrined, int slot,
			int life) {

		Grave withSlot(int newSlot) {
			return new Grave(soul, name, step, day, cause, causeArg, deeds, enshrined, newSlot, life);
		}

		Grave enshrine() {
			return new Grave(soul, name, step, day, cause, causeArg, deeds, true, slot, life);
		}
	}

	// ================================================================== hall

	static CompoundTag hall(UUID owner) {
		return Halls.section(owner, SECTION);
	}

	static void dirty() {
		Halls.setDirty();
	}

	static long getLong(UUID owner, String key, long fallback) {
		return hall(owner).getLongOr(key, fallback);
	}

	static void putLong(UUID owner, String key, long value) {
		hall(owner).putLong(key, value);
		dirty();
	}

	static int count(UUID owner, String key) {
		return hall(owner).getIntOr(key, 0);
	}

	static void bump(UUID owner, String key) {
		CompoundTag tag = hall(owner);
		tag.putInt(key, tag.getIntOr(key, 0) + 1);
		dirty();
	}

	// ================================================================== rack

	static List<Kitab> rack(UUID owner) {
		List<Kitab> list = new ArrayList<>();
		for (Tag tag : hall(owner).getListOrEmpty(RAK)) {
			if (tag instanceof CompoundTag e) {
				List<String> specs = new ArrayList<>();
				for (Tag s : e.getListOrEmpty("specs")) {
					s.asString().ifPresent(specs::add);
				}
				Component name = e.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				list.add(new Kitab(e.getIntOr("id", 0), e.getStringOr("kind", "buku"), name, specs, e.getCompoundOrEmpty("item"),
						e.getStringOr("by", ""), e.getLongOr("day", 0L)));
			}
		}
		return list;
	}

	static @Nullable Kitab book(UUID owner, int id) {
		for (Kitab k : rack(owner)) {
			if (k.id() == id) {
				return k;
			}
		}
		return null;
	}

	static int addBook(UUID owner, String kind, Component name, List<String> specs, CompoundTag item, String by, long day) {
		CompoundTag hall = hall(owner);
		int id = hall.getIntOr(NEXT_ID, 1);
		hall.putInt(NEXT_ID, id + 1);
		ListTag list = hall.getListOrEmpty(RAK).copy();
		CompoundTag e = new CompoundTag();
		e.putInt("id", id);
		e.putString("kind", kind);
		e.store("name", ComponentSerialization.CODEC, name);
		ListTag s = new ListTag();
		for (String spec : specs) {
			s.add(StringTag.valueOf(spec));
		}
		e.put("specs", s);
		e.put("item", item);
		e.putString("by", by == null ? "" : by);
		e.putLong("day", day);
		list.add(e);
		hall.put(RAK, list);
		dirty();
		return id;
	}

	static @Nullable CompoundTag removeBook(UUID owner, int id) {
		CompoundTag hall = hall(owner);
		ListTag list = hall.getListOrEmpty(RAK).copy();
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag e && e.getIntOr("id", 0) == id) {
				list.remove(i);
				hall.put(RAK, list);
				dirty();
				return e.getCompoundOrEmpty("item");
			}
		}
		return null;
	}

	// ================================================================== graves

	static List<Grave> graves(UUID owner) {
		return read(hall(owner).getListOrEmpty(MAKAM));
	}

	static List<Grave> lineage(UUID owner) {
		return read(hall(owner).getListOrEmpty(SILSILAH));
	}

	static void setGraves(UUID owner, List<Grave> graves) {
		hall(owner).put(MAKAM, write(graves));
		dirty();
	}

	static void setLineage(UUID owner, List<Grave> lineage) {
		hall(owner).put(SILSILAH, write(lineage));
		dirty();
	}

	static int folded(UUID owner) {
		return hall(owner).getIntOr(SILSILAH_FOLDED, 0);
	}

	static void addFolded(UUID owner, int n) {
		CompoundTag hall = hall(owner);
		hall.putInt(SILSILAH_FOLDED, hall.getIntOr(SILSILAH_FOLDED, 0) + n);
		dirty();
	}

	private static List<Grave> read(ListTag tags) {
		List<Grave> list = new ArrayList<>(tags.size());
		for (Tag tag : tags) {
			if (tag instanceof CompoundTag e) {
				UUID soul = e.read("soul", UUIDUtil.CODEC).orElse(null);
				if (soul == null) {
					continue;
				}
				List<Deed> deeds = new ArrayList<>();
				for (Tag d : e.getListOrEmpty("deeds")) {
					if (d instanceof CompoundTag deed) {
						deeds.add(new Deed(deed.getStringOr("t", ""), deed.getStringOr("a", ""), deed.getIntOr("w", 0)));
					}
				}
				list.add(new Grave(soul, e.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?")), e.getIntOr("step", 0),
						e.getLongOr("day", 0L), e.getStringOr("cause", "lain"), e.getStringOr("cause_arg", ""), deeds,
						e.getBooleanOr("enshrined", false), e.getIntOr("slot", -1), e.getIntOr("life", 0)));
			}
		}
		return list;
	}

	private static ListTag write(List<Grave> graves) {
		ListTag list = new ListTag();
		for (Grave g : graves) {
			list.add(toTag(g));
		}
		return list;
	}

	/** One grave as a tag (also what the screens and the world labels receive). */
	static CompoundTag toTag(Grave g) {
		CompoundTag e = new CompoundTag();
		e.store("soul", UUIDUtil.CODEC, g.soul());
		e.store("name", ComponentSerialization.CODEC, g.name());
		e.putInt("step", g.step());
		e.putLong("day", g.day());
		e.putString("cause", g.cause());
		if (!g.causeArg().isEmpty()) {
			e.putString("cause_arg", g.causeArg());
		}
		ListTag deeds = new ListTag();
		for (Deed d : g.deeds()) {
			CompoundTag deed = new CompoundTag();
			deed.putString("t", d.text());
			if (!d.arg().isEmpty()) {
				deed.putString("a", d.arg());
			}
			deed.putInt("w", d.weight());
			deeds.add(deed);
		}
		e.put("deeds", deeds);
		e.putBoolean("enshrined", g.enshrined());
		e.putInt("slot", g.slot());
		e.putInt("life", g.life());
		return e;
	}

	// ================================================================== disciple study record

	static CompoundTag study(DiscipleData data) {
		return data.extraSection(SekteIds.EXTRA_A);
	}

	static String studying(DiscipleData data) {
		CompoundTag tag = data.extra().getCompound(SekteIds.EXTRA_A).orElse(null);
		return tag == null ? "" : tag.getStringOr(SPEC, "");
	}

	private PaviliunData() {
	}
}
