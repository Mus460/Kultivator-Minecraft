package dev.kultivator.elite;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lima Unsur, the five elements in combat (DESIGN 19.3.12). The overcoming cycle Wood > Earth > Water > Fire > Metal >
 * Wood and the generating cycle Wood → Fire → Earth → Metal → Water → Wood are classical Wuxing; the mutated elements
 * have parents (RMJ ch. 127: Lightning = Earth + Water, Ice = Water + Metal); Wind and Space have none and stay neutral.
 * Every factor is game design. Common code: the codex detail slip reads {@link #strongAgainst}; the target factor is
 * registered on the server by {@link EliteMonsters#init()}.
 */
public final class ElementRelations {

	/** Some attacker parent overcomes some target parent and nothing overcomes back. */
	public static final double STRONG = 1.3D;
	/** The target overcomes the attacker and not the other way round. */
	public static final double WEAK = 0.8D;
	/** Attacker and target share a parent. */
	public static final double KIN = 0.9D;
	/** A Space technique against everything (instead of the relation). */
	public static final double SPACE_FACTOR = 1.05D;
	/** The technique element is in the attacker's root. */
	public static final double RESONANCE = 1.1D;
	/** A root element generates the technique element (or one of its parents), RMJ-IR ch. 1049. */
	public static final double NOURISH = 1.05D;

	private static final Map<Element, Element> OVERCOMES = new EnumMap<>(Element.class);
	private static final Map<Element, Element> GENERATES = new EnumMap<>(Element.class);

	static {
		OVERCOMES.put(Element.WOOD, Element.EARTH);
		OVERCOMES.put(Element.EARTH, Element.WATER);
		OVERCOMES.put(Element.WATER, Element.FIRE);
		OVERCOMES.put(Element.FIRE, Element.METAL);
		OVERCOMES.put(Element.METAL, Element.WOOD);
		GENERATES.put(Element.WOOD, Element.FIRE);
		GENERATES.put(Element.FIRE, Element.EARTH);
		GENERATES.put(Element.EARTH, Element.METAL);
		GENERATES.put(Element.METAL, Element.WATER);
		GENERATES.put(Element.WATER, Element.WOOD);
	}

	/** The damage factor of an {@code attacker} element against a {@code target} element (1 when either is null). */
	public static double factor(@Nullable Element attacker, @Nullable Element target) {
		if (attacker == null) {
			return 1.0D;
		}
		if (attacker == Element.SPACE) {
			return SPACE_FACTOR;
		}
		if (target == null) {
			return 1.0D;
		}
		Set<Element> a = parents(attacker);
		Set<Element> t = parents(target);
		if (a.isEmpty() || t.isEmpty()) {
			return 1.0D;
		}
		boolean forward = overcomes(attacker, target);
		boolean back = overcomes(target, attacker);
		if (forward && !back) {
			return STRONG;
		}
		if (back && !forward) {
			return WEAK;
		}
		if (forward) {
			return 1.0D;
		}
		for (Element each : a) {
			if (t.contains(each)) {
				return KIN;
			}
		}
		return 1.0D;
	}

	/** True when {@code a} overcomes {@code b} in the Wuxing overcoming cycle (through their parents). */
	public static boolean overcomes(Element a, Element b) {
		if (a == null || b == null) {
			return false;
		}
		for (Element pa : parents(a)) {
			Element beaten = OVERCOMES.get(pa);
			if (beaten != null && parents(b).contains(beaten)) {
				return true;
			}
		}
		return false;
	}

	/** True when {@code a} generates {@code b} in the generating cycle (through their parents). */
	public static boolean generates(Element a, Element b) {
		if (a == null || b == null) {
			return false;
		}
		Set<Element> target = parents(b);
		for (Element pa : parents(a)) {
			Element made = GENERATES.get(pa);
			if (made != null && target.contains(made)) {
				return true;
			}
		}
		return false;
	}

	/** The parent elements: a base element itself, Lightning {Earth, Water}, Ice {Water, Metal}; Wind and Space none. */
	public static Set<Element> parents(Element element) {
		if (element == null) {
			return EnumSet.noneOf(Element.class);
		}
		return switch (element) {
			case METAL, WOOD, WATER, FIRE, EARTH -> EnumSet.of(element);
			case LIGHTNING -> EnumSet.of(Element.EARTH, Element.WATER);
			case ICE -> EnumSet.of(Element.WATER, Element.METAL);
			case WIND, SPACE -> EnumSet.noneOf(Element.class);
		};
	}

	/**
	 * The target elements a technique of {@code element} is strong against (×{@link #STRONG}, for "Unggul melawan: ...").
	 * Space's flat ×1.05 is no relation, so Space returns an empty list.
	 */
	public static List<Element> strongAgainst(Element element) {
		if (element == null) {
			return List.of();
		}
		List<Element> list = new ArrayList<>();
		for (Element target : Element.values()) {
			if (factor(element, target) >= STRONG) {
				list.add(target);
			}
		}
		return Collections.unmodifiableList(list);
	}

	/** The root resonance of a technique of {@code technique} for a root of {@code rootMask}: ×1.1, ×1.05 or ×1. */
	public static double resonance(int rootMask, @Nullable Element technique) {
		if (technique == null || rootMask == 0) {
			return 1.0D;
		}
		if ((rootMask & technique.bit()) != 0) {
			return RESONANCE;
		}
		for (Element root : Element.fromMask(rootMask)) {
			if (generates(root, technique)) {
				return NOURISH;
			}
		}
		return 1.0D;
	}

	/** The element of a cast: the spec's Dao element, else the caster's chosen Dao's element, else the root's main one. */
	public static @Nullable Element techniqueElement(LivingEntity attacker, @Nullable TechniqueSpec spec) {
		if (spec != null && spec.dao() != null) {
			return spec.dao().affinity();
		}
		if (attacker instanceof ServerPlayer player) {
			return techniqueElement(Cultivators.get(player));
		}
		if (attacker instanceof DiscipleEntity disciple) {
			return techniqueElement(disciple.data());
		}
		return null;
	}

	/** The player's universal technique element: the chosen Dao's element, else the root's main element, else null. */
	public static @Nullable Element techniqueElement(CultivationData data) {
		if (data == null) {
			return null;
		}
		Dao dao = data.dao();
		if (dao != null) {
			return dao.affinity();
		}
		return data.rootTested() ? mainElement(data.rootElements()) : null;
	}

	/** The disciple's universal technique element: the chosen Dao's element, else the root's main element, else null. */
	public static @Nullable Element techniqueElement(DiscipleData data) {
		if (data == null) {
			return null;
		}
		Dao dao = data.dao();
		if (dao != null) {
			return dao.affinity();
		}
		return mainElement(data.rootElements());
	}

	/** The root of a player (once tested) or a disciple, 0 for anything else. */
	public static int rootMask(LivingEntity attacker) {
		if (attacker instanceof ServerPlayer player) {
			CultivationData data = Cultivators.get(player);
			return data.rootTested() ? data.rootElements() : 0;
		}
		if (attacker instanceof DiscipleEntity disciple) {
			return disciple.data().rootElements();
		}
		return 0;
	}

	/**
	 * The element part of a technique hit (a {@code TechniquePower} target factor): the relation of the technique element
	 * to the target's element times the attacker's root resonance; 1 without a technique element.
	 */
	public static double targetFactor(LivingEntity attacker, LivingEntity target, @Nullable TechniqueSpec spec) {
		Element technique = techniqueElement(attacker, spec);
		if (technique == null) {
			return 1.0D;
		}
		return factor(technique, MobElements.of(target)) * resonance(rootMask(attacker), technique);
	}

	private static @Nullable Element mainElement(int mask) {
		List<Element> elements = Element.fromMask(mask);
		return elements.isEmpty() ? null : elements.get(0);
	}

	private ElementRelations() {
	}
}
