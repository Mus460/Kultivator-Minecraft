package dev.kultivator.formasi.spec;

import dev.kultivator.formasi.stack.Stacking;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.List;

/**
 * One sect formation (DESIGN 24.2.2), immutable and common to both sides so the client knows every blueprint without a
 * sync. {@code id} and {@code netId} are saved and never renamed; netId blocks: 0-19 legacy mirrors, 100-399 module A,
 * 400-799 module B. Text keys: {@code kultivator.formasi.spec.<id>.name|desc|hint|source}.
 */
public record FormationSpec(String id, int netId, Family family, Grade grade, Scope scope, int radius, int radiusPerLevel,
		int radiusMax, int minHallLevel, int pulseTicks, double upkeep, Pattern pattern, String effect, Params params,
		List<String> requires, EnumSet<Flag> flags, int color, int fx) {

	public FormationSpec {
		requires = List.copyOf(requires);
		flags = flags == null ? EnumSet.noneOf(Flag.class) : EnumSet.copyOf(flags);
		pulseTicks = Math.max(20, pulseTicks);
		minHallLevel = Math.max(grade.gate(), minHallLevel);
	}

	/** The card radius at {@code hallLevel}, before amplifier bonuses: {@code min(radiusMax, RADIUS_CAP, radius + perLevel × (L − gate))}. */
	public double radiusAt(int hallLevel) {
		int grown = radius + radiusPerLevel * Math.max(0, hallLevel - minHallLevel);
		return Math.min(Math.min(radiusMax, Stacking.RADIUS_CAP), grown);
	}

	public boolean has(Flag flag) {
		return flags.contains(flag);
	}

	/** The translation key prefix of this spec. */
	public String key() {
		return "kultivator.formasi.spec." + id;
	}

	public Component name() {
		return Component.translatable(key() + ".name");
	}

	public Component desc() {
		return Component.translatable(key() + ".desc");
	}

	public Component hint() {
		return Component.translatable(key() + ".hint");
	}

	public Component source() {
		return Component.translatable(key() + ".source");
	}

	/** A copy of the flags (the record's own set must not be changed). */
	@Override
	public EnumSet<Flag> flags() {
		return EnumSet.copyOf(flags);
	}
}
