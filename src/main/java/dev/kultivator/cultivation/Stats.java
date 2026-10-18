package dev.kultivator.cultivation;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Permanent attribute bonuses from cultivation (DESIGN section 2), with fixed modifier ids so they are replaced, never
 * stacked. The bonus formulas are public and pure so screens can show them.
 */
public final class Stats {

	public static final Identifier HEALTH_ID = Kultivator.id("realm_health");
	public static final Identifier ATTACK_ID = Kultivator.id("realm_attack");
	public static final Identifier SPEED_ID = Kultivator.id("realm_speed");
	public static final Identifier ARMOR_ID = Kultivator.id("realm_armor");
	public static final Identifier SAFE_FALL_ID = Kultivator.id("realm_safe_fall");
	public static final Identifier MINING_ID = Kultivator.id("realm_mining");
	/** Player stars (DESIGN 19.3.11): +{@link #HEALTH_PER_STAR} max health each. */
	public static final Identifier STAR_HEALTH_ID = Kultivator.id("star_health");
	public static final double HEALTH_PER_STAR = 2.0D;
	/** Anugerah Kenaikan attribute boons (DESIGN 19.3.11), ids {@code kultivator:perk_*}. */
	public static final Identifier PERK_OXYGEN_ID = Kultivator.id("perk_oxygen");
	public static final Identifier PERK_BURNING_ID = Kultivator.id("perk_burning");
	public static final Identifier PERK_STEP_ID = Kultivator.id("perk_step_height");
	public static final Identifier PERK_WATER_ID = Kultivator.id("perk_water_movement");
	public static final Identifier PERK_TOUGHNESS_ID = Kultivator.id("perk_toughness");
	public static final Identifier PERK_KNOCKBACK_ID = Kultivator.id("perk_knockback");
	public static final Identifier PERK_ENTITY_REACH_ID = Kultivator.id("perk_entity_reach");
	public static final Identifier PERK_BLOCK_REACH_ID = Kultivator.id("perk_block_reach");
	/** L1 Napas Abadi: +3 oxygen bonus, burning time −50%. */
	public static final double PERK_OXYGEN = 3.0D;
	public static final double PERK_BURNING = -0.5D;
	/** L2 Langkah Ringan: +0.5 step height, +0.5 water movement efficiency. */
	public static final double PERK_STEP = 0.5D;
	public static final double PERK_WATER = 0.5D;
	/** L3 Kulit Giok: +4 armor toughness, +0.25 knockback resistance. */
	public static final double PERK_TOUGHNESS = 4.0D;
	public static final double PERK_KNOCKBACK = 0.25D;
	/** L5 Tangan Langit: +1.5 entity and +1.0 block interaction range. */
	public static final double PERK_ENTITY_REACH = 1.5D;
	public static final double PERK_BLOCK_REACH = 1.0D;
	/** Health curve (DESIGN 19.3.11): per Immortal Ascension layer, and the flat True Immortal bonus. */
	public static final double HEALTH_PER_LAYER = 4.0D;
	public static final double TRUE_IMMORTAL_HEALTH = 40.0D;

	public static void init() {
		// Applied on join, respawn and every rank / quality / Dao change by the callers.
	}

	/**
	 * Recomputes every cultivation attribute modifier of the player. When the maximum health changes, the current
	 * health keeps its fraction (at least 1 while alive); dead players' health is never touched.
	 */
	public static void apply(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		boolean alive = !player.isDeadOrDying() && !player.isRemoved();
		float oldMax = player.getMaxHealth();
		float oldHealth = player.getHealth();

		set(player, Attributes.MAX_HEALTH, HEALTH_ID, healthBonus(data), AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, attackBonus(data), AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, speedBonus(data), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.ARMOR, ARMOR_ID, armorBonus(data), AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, safeFallBonus(data), AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.BLOCK_BREAK_SPEED, MINING_ID, miningBonus(data), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.MAX_HEALTH, STAR_HEALTH_ID, starHealthBonus(data), AttributeModifier.Operation.ADD_VALUE);
		applyBoons(player, data);

		if (!alive) {
			return;
		}
		float newMax = player.getMaxHealth();
		if (Math.abs(newMax - oldMax) > 1.0E-4F && newMax > 0.0F) {
			float fraction = oldMax > 0.0F ? Math.max(0.0F, Math.min(1.0F, oldHealth / oldMax)) : 1.0F;
			player.setHealth(Math.max(1.0F, Math.min(newMax, fraction * newMax)));
		}
	}

	/**
	 * Max health: {@code realm x 4 + stage + layer x 4} (+40 more for True Immortal) x quality factor (DESIGN 19.3.11:
	 * IA L9 Peak +63, True Immortal +72 to +75 before quality).
	 */
	public static double healthBonus(CultivationData data) {
		Rank rank = data.rank();
		double bonus = rank.realm().ordinal() * 4.0D + rank.stage() + rank.layer() * HEALTH_PER_LAYER;
		if (rank.realm() == Realm.TRUE_IMMORTAL) {
			bonus += TRUE_IMMORTAL_HEALTH;
		}
		return bonus * data.qualityFactor();
	}

	/** Max health from the player's stars: {@link #HEALTH_PER_STAR} per star. */
	public static double starHealthBonus(CultivationData data) {
		return ImmortalPerks.stars(data) * HEALTH_PER_STAR;
	}

	/** The attribute boons of Anugerah Kenaikan (L1, L2, L3, L5); L4, L6 to L9 act in their own places. */
	private static void applyBoons(ServerPlayer player, CultivationData data) {
		boolean breath = ImmortalPerks.boonActive(data, 1);
		boolean step = ImmortalPerks.boonActive(data, 2);
		boolean jade = ImmortalPerks.boonActive(data, 3);
		boolean reach = ImmortalPerks.boonActive(data, 5);
		set(player, Attributes.OXYGEN_BONUS, PERK_OXYGEN_ID, breath ? PERK_OXYGEN : 0.0D, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.BURNING_TIME, PERK_BURNING_ID, breath ? PERK_BURNING : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.STEP_HEIGHT, PERK_STEP_ID, step ? PERK_STEP : 0.0D, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.WATER_MOVEMENT_EFFICIENCY, PERK_WATER_ID, step ? PERK_WATER : 0.0D, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ARMOR_TOUGHNESS, PERK_TOUGHNESS_ID, jade ? PERK_TOUGHNESS : 0.0D, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.KNOCKBACK_RESISTANCE, PERK_KNOCKBACK_ID, jade ? PERK_KNOCKBACK : 0.0D, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ENTITY_INTERACTION_RANGE, PERK_ENTITY_REACH_ID, reach ? PERK_ENTITY_REACH : 0.0D,
				AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.BLOCK_INTERACTION_RANGE, PERK_BLOCK_REACH_ID, reach ? PERK_BLOCK_REACH : 0.0D,
				AttributeModifier.Operation.ADD_VALUE);
	}

	/** Attack damage: {@code realm x 0.6 + stage x 0.15 + layer x 0.3}. */
	public static double attackBonus(CultivationData data) {
		Rank rank = data.rank();
		return rank.realm().ordinal() * 0.6D + rank.stage() * 0.15D + rank.layer() * 0.3D;
	}

	/** Movement speed, multiplied base: {@code min(0.25, realm x 0.025)}. */
	public static double speedBonus(CultivationData data) {
		return Math.min(0.25D, data.realm().ordinal() * 0.025D);
	}

	/** Armor: {@code realm}, x1.5 on the Body Dao. */
	public static double armorBonus(CultivationData data) {
		double armor = data.realm().ordinal();
		return data.dao() == Dao.BODY ? armor * 1.5D : armor;
	}

	/** Safe fall distance: {@code realm x 1.5} blocks. */
	public static double safeFallBonus(CultivationData data) {
		return data.realm().ordinal() * 1.5D;
	}

	/** Block break speed, multiplied base: {@code realm x 0.05}. */
	public static double miningBonus(CultivationData data) {
		return data.realm().ordinal() * 0.05D;
	}

	/** Adds or replaces the modifier, or removes it when the bonus is 0. Skips the work when nothing changed. */
	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (!Double.isFinite(amount) || Math.abs(amount) < 1.0E-9D) {
			instance.removeModifier(id);
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (current != null && current.operation() == operation && Math.abs(current.amount() - amount) < 1.0E-9D) {
			return;
		}
		instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
	}

	private Stats() {
	}
}
