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

		if (!alive) {
			return;
		}
		float newMax = player.getMaxHealth();
		if (Math.abs(newMax - oldMax) > 1.0E-4F && newMax > 0.0F) {
			float fraction = oldMax > 0.0F ? Math.max(0.0F, Math.min(1.0F, oldHealth / oldMax)) : 1.0F;
			player.setHealth(Math.max(1.0F, Math.min(newMax, fraction * newMax)));
		}
	}

	/** Max health: {@code realm x 4 + stage + layer x 2} (+60 more for True Immortal) x quality factor. */
	public static double healthBonus(CultivationData data) {
		Rank rank = data.rank();
		double bonus = rank.realm().ordinal() * 4.0D + rank.stage() + rank.layer() * 2.0D;
		if (rank.realm() == Realm.TRUE_IMMORTAL) {
			bonus += 60.0D;
		}
		return bonus * data.qualityFactor();
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
