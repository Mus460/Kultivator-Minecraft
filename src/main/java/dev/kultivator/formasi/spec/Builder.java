package dev.kultivator.formasi.spec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The spec DSL of DESIGN 24.2.2, started by {@link Specs#spec}. Defaults: grade Kuning, scope CORE, radius 8 with no
 * growth, the grade's hall gate and upkeep, pulse every 20 ticks, the family colour. Bumi and up get
 * {@link Flag#STONE_ONLY} automatically. {@link #build()} never throws: a broken pattern keeps its error and the registry
 * refuses the spec.
 */
public final class Builder {

	private final String id;
	private final int netId;
	private final Family family;
	private Grade grade = Grade.KUNING;
	private Scope scope = Scope.CORE;
	private int radius = 8;
	private int radiusPerLevel;
	private int radiusMax = -1;
	private int minHallLevel = -1;
	private int pulseTicks = 20;
	private double upkeep = -1.0D;
	private Pattern pattern = Pattern.empty();
	private String effect = "";
	private final Map<String, Double> params = new LinkedHashMap<>();
	private final List<String> requires = new ArrayList<>();
	private final EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
	private int color = -1;
	private int fx;

	Builder(String id, int netId, Family family) {
		this.id = id;
		this.netId = netId;
		this.family = family;
	}

	public Builder grade(Grade grade) {
		this.grade = grade;
		return this;
	}

	public Builder scope(Scope scope) {
		this.scope = scope;
		return this;
	}

	/** Base radius (blocks, horizontal). */
	public Builder radius(int radius) {
		this.radius = radius;
		return this;
	}

	/** Base radius, growth per hall level above the gate and the maximum. */
	public Builder radius(int radius, int perLevel, int max) {
		this.radius = radius;
		this.radiusPerLevel = perLevel;
		this.radiusMax = max;
		return this;
	}

	public Builder radiusPerLevel(int perLevel) {
		this.radiusPerLevel = perLevel;
		return this;
	}

	public Builder radiusMax(int max) {
		this.radiusMax = max;
		return this;
	}

	/** Raises the hall gate above the grade's (never lowers it). */
	public Builder minHallLevel(int level) {
		this.minHallLevel = level;
		return this;
	}

	public Builder pulseTicks(int ticks) {
		this.pulseTicks = ticks;
		return this;
	}

	/** Daya per hour, when it differs from the grade default. */
	public Builder upkeep(double perHour) {
		this.upkeep = perHour;
		return this;
	}

	/** The pattern, written with the {@link Pattern.Builder} DSL. */
	public Builder pattern(Consumer<Pattern.Builder> layout) {
		Pattern.Builder builder = Pattern.builder();
		layout.accept(builder);
		this.pattern = builder.build();
		return this;
	}

	/** A ready pattern (legacy mirrors). */
	public Builder pattern(Pattern pattern) {
		this.pattern = pattern;
		return this;
	}

	/** The handler id registered through {@code EffectSink}. */
	public Builder effect(String effect) {
		this.effect = effect;
		return this;
	}

	public Builder param(String key, double value) {
		this.params.put(key, value);
		return this;
	}

	/** Spec ids or {@code family:<name>} that must run with the same owner and territory. */
	public Builder requires(String... ids) {
		this.requires.addAll(List.of(ids));
		return this;
	}

	public Builder flag(Flag... flags) {
		this.flags.addAll(List.of(flags));
		return this;
	}

	public Builder color(int color) {
		this.color = color;
		return this;
	}

	/** The FX style sub-id. */
	public Builder fx(int fx) {
		this.fx = fx;
		return this;
	}

	public FormationSpec build() {
		EnumSet<Flag> all = EnumSet.copyOf(flags);
		if (grade.stoneOnly()) {
			all.add(Flag.STONE_ONLY);
		}
		int max = radiusMax < 0 ? radius : radiusMax;
		return new FormationSpec(id, netId, family, grade, scope, radius, radiusPerLevel, max,
				minHallLevel < 0 ? grade.gate() : minHallLevel, pulseTicks, upkeep < 0.0D ? grade.upkeep() : upkeep, pattern, effect,
				new Params(params), requires, all, color < 0 ? family.color() : color, fx);
	}
}
