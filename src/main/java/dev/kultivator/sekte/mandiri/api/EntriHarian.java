package dev.kultivator.sekte.mandiri.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One diary entry (Buku Harian, DESIGN 39.8.1). The server keeps only keys and arguments; the client (39E
 * {@code HarianTeks}) composes 3-4 sentences in the reader's language: an opener by context, the core
 * {@code kultivator.sekte.mandiri.harian.<jenis>.<hasil>.<n>} (or a full key of any fragment, e.g. 39F's
 * {@code kultivator.sekte.mandiri.seteru.*}), a reflection by the strongest trait and, half of the time, a closing line by
 * the priority-1 goal. Variants are picked with the seed {@code (soul, hari, jenis)} so an entry always reads the same.
 * Written only through {@code MandiriApi.catat}; the ring keeps 40 per disciple and drops ordinary entries first.
 *
 * @param hari     world day ({@code clock / 24000})
 * @param jam      hour 0-23 ({@code (dayTime / 1000 + 6) mod 24})
 * @param jenis    kind ({@code belanja}, {@code tembus}, {@code jalan.begal}, {@code seteru.duel} ...)
 * @param kunci    core key, short ({@code belanja.tawar_menang}) or full ({@code kultivator.sekte.mandiri.seteru.x})
 * @param arg      real arguments: town, character, item id, prices, friend, realm, technique, day number
 * @param suasana  mood icon
 * @param penting  red ribbon: breakthrough, Dao, captured, redeemed, goal done, a friend's death; kept in the ring
 */
public record EntriHarian(long hari, int jam, String jenis, String kunci, List<String> arg, Suasana suasana, boolean penting) {

	/** The seven moods (icons in {@code textures/sekte/mandiri/}). */
	public enum Suasana {
		SENANG, TENANG, LELAH, KESAL, SEDIH, BANGGA, TERGODA;

		public static Suasana byName(@Nullable String name, Suasana fallback) {
			if (name != null) {
				for (Suasana value : values()) {
					if (value.name().equalsIgnoreCase(name)) {
						return value;
					}
				}
			}
			return fallback;
		}
	}

	/** At most this many arguments are kept (the composer never needs more). */
	public static final int ARG_MAKS = 8;

	public EntriHarian {
		jam = Math.floorMod(jam, 24);
		jenis = jenis == null ? "" : jenis;
		kunci = kunci == null ? "" : kunci;
		arg = arg == null ? List.of() : List.copyOf(arg.size() > ARG_MAKS ? arg.subList(0, ARG_MAKS) : arg);
		suasana = suasana == null ? Suasana.TENANG : suasana;
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("hari", hari);
		tag.putInt("jam", jam);
		tag.putString("jenis", jenis);
		tag.putString("kunci", kunci);
		ListTag list = new ListTag();
		for (String value : arg) {
			list.add(StringTag.valueOf(value == null ? "" : value));
		}
		tag.put("arg", list);
		tag.putString("suasana", suasana.name());
		if (penting) {
			tag.putBoolean("penting", true);
		}
		return tag;
	}

	public static EntriHarian fromTag(CompoundTag tag) {
		ListTag list = tag.getListOrEmpty("arg");
		List<String> args = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			args.add(list.getStringOr(i, ""));
		}
		return new EntriHarian(tag.getLongOr("hari", 0L), tag.getIntOr("jam", 0), tag.getStringOr("jenis", ""),
				tag.getStringOr("kunci", ""), args, Suasana.byName(tag.getStringOr("suasana", ""), Suasana.TENANG),
				tag.getBooleanOr("penting", false));
	}
}
