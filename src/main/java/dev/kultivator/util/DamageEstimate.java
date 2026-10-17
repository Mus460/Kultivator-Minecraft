package dev.kultivator.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * What a hit will at most take from a living entity, read inside {@code ALLOW_DAMAGE} (before vanilla applies its own
 * reductions): armour unless the type bypasses it, then Resistance and Protection enchantments, in the order of
 * {@code LivingEntity.actuallyHurt}. Shield blocking and the short invulnerability after a hit only lower the real damage
 * further, so a rule that refuses a hit whose estimate would be lethal never lets a lethal hit through. When a worn armour
 * piece could break on this very hit (its armour and enchantments would then be gone), armour and enchantments are left
 * out, so the estimate stays an upper bound. Read-only.
 */
public final class DamageEstimate {

	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

	/** An upper bound of the health (absorption included) {@code victim} loses to {@code amount} of {@code source}. */
	public static float upperBound(LivingEntity victim, DamageSource source, float amount) {
		float damage = Math.max(0.0F, amount);
		if (!(damage > 0.0F)) {
			return 0.0F;
		}
		boolean fragile = mayBreakArmor(victim, damage);
		if (!fragile && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			damage = CombatRules.getDamageAfterAbsorb(victim, damage, source, (float) victim.getArmorValue(),
					(float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		}
		if (source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
			return damage;
		}
		MobEffectInstance resistance = victim.getEffect(MobEffects.RESISTANCE);
		if (resistance != null && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
			damage = Math.max(0.0F, damage * (25 - (resistance.getAmplifier() + 1) * 5) / 25.0F);
		}
		if (!fragile && damage > 0.0F && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && victim.level() instanceof ServerLevel level) {
			float protection = EnchantmentHelper.getDamageProtection(level, victim, source);
			if (protection > 0.0F) {
				damage = CombatRules.getDamageAfterMagicAbsorb(damage, protection);
			}
		}
		return damage;
	}

	/** True when a worn armour piece has no more durability left than this hit takes from it (it may break now). */
	private static boolean mayBreakArmor(LivingEntity victim, float damage) {
		int durabilityDamage = (int) Math.max(1.0F, damage / 4.0F);
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = victim.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= durabilityDamage) {
				return true;
			}
		}
		return false;
	}

	private DamageEstimate() {
	}
}
