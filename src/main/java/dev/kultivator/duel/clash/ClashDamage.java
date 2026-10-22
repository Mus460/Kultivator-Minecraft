package dev.kultivator.duel.clash;

import dev.kultivator.Kultivator;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.util.DamageHooks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Health damage of a resolved HIT (DESIGN 35.3.5). BEBAS and HANDICAP: a player's cut is
 * {@code ATTACK_DAMAGE × kind}, the weapon's enchantments ({@code EnchantmentHelper.modifyDamage}), ×1.5 for a falling
 * critical (vanilla's rule), the underdog bonus of BEBAS, then {@code hurtServer} with {@code playerAttack}, the
 * post-attack enchantment effects and one point of durability; never {@code Player.attack}, so no vanilla sweep hits
 * the spectators. Mobs, disciples and NPCs hit through their own {@code doHurtTarget} with a transient
 * {@code kultivator:duel_kind} attack modifier for that one blow. TEKAN_RANAH: a share of the victim's max health of
 * type {@code kultivator:duel} (armour-bypassing). Every resolution hit is marked {@link #resolving()} so the vanilla-hit
 * filter of the clash layer lets it through; the {@code DuelRules} 10% clamp still applies to all of them.
 * <p>
 * Reductions of vanilla hits (reactive guard ×0.4, technique deflect ×0.5...) ride on a one-shot factor read by a
 * {@code DamageHooks} DR modifier ({@link #scaleNext}); increases are never done there, so the clamp's estimate stays an
 * upper bound and nothing can kill a duellist.
 */
public final class ClashDamage {

	/** {@code kultivator:duel}: Realm Suppression damage, in {@code #minecraft:bypasses_armor}. */
	public static final ResourceKey<DamageType> DUEL = ResourceKey.create(Registries.DAMAGE_TYPE, Kultivator.id("duel"));
	/** The one-blow attack modifier of a mob's cut. */
	public static final Identifier KIND_ID = Kultivator.id("duel_kind");
	/** BEBAS underdog bonus per realm of difference, and its cap (like {@code DuelArena}). */
	public static final float UNDERDOG_PER_REALM = 0.15F;
	public static final float UNDERDOG_MAX = 0.45F;
	/** The Sword Qi Wave in TEKAN_RANAH. */
	public static final float WAVE_SHARE = 0.08F;

	private static final ThreadLocal<int[]> RESOLVING = ThreadLocal.withInitial(() -> new int[1]);

	/** The one-shot reduction for the next damage of one victim (set in ALLOW_DAMAGE, read by the DR hook). */
	private static @Nullable LivingEntity scaleVictim;
	private static @Nullable DamageSource scaleSource;
	private static float scaleFactor = 1.0F;
	private static long scaleTick;

	/** From {@code Duel.init}: the DR hook of {@link #scaleNext}. */
	public static void init() {
		DamageHooks.register(DamageHooks.Stage.DR, ClashDamage::applyScale);
	}

	/** True while the clash layer itself deals damage (its hits pass the vanilla-hit filter). */
	public static boolean resolving() {
		return RESOLVING.get()[0] > 0;
	}

	/** Runs {@code action} marked as a resolution hit. */
	public static <T> T resolving(java.util.function.Supplier<T> action) {
		int[] depth = RESOLVING.get();
		depth[0]++;
		try {
			return action.get();
		} finally {
			depth[0]--;
		}
	}

	/**
	 * Reduces the next damage {@code victim} takes from {@code source} this tick to {@code factor} (0..1) after armour.
	 * Called from ALLOW_DAMAGE when the blow is let through but guarded or partly deflected.
	 */
	public static void scaleNext(LivingEntity victim, DamageSource source, float factor) {
		scaleVictim = victim;
		scaleSource = source;
		scaleFactor = Math.max(0.0F, Math.min(1.0F, factor));
		scaleTick = ClashClock.now();
	}

	private static float applyScale(LivingEntity entity, DamageSource source, float amount) {
		LivingEntity victim = scaleVictim;
		if (victim == null) {
			return amount;
		}
		if (victim != entity || scaleSource != source || ClashClock.now() != scaleTick) {
			if (ClashClock.now() != scaleTick) {
				scaleVictim = null;
				scaleSource = null;
			}
			return amount;
		}
		scaleVictim = null;
		scaleSource = null;
		return amount * scaleFactor;
	}

	/** A {@code kultivator:duel} source caused by {@code attacker}. */
	public static DamageSource duelSource(ServerLevel level, LivingEntity attacker) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DUEL), attacker);
	}

	/**
	 * Deals the HIT of {@code swing} to {@code victim}; {@code extra} multiplies it (2.5 for a Deathblow without the
	 * Deathblow rule, the boss caps are the caller's). Returns the health (and absorption) the victim lost.
	 */
	public static float apply(Swing swing, LivingEntity victim, @Nullable DuelRuleSet rules, float extra) {
		LivingEntity attacker = swing.owner.entity;
		if (!(attacker.level() instanceof ServerLevel level) || victim.level() != level || !victim.isAlive()) {
			return 0.0F;
		}
		float before = victim.getHealth() + victim.getAbsorptionAmount();
		if (victim.damageCooldownTime > 10) {
			// Duel cuts come 3+ ticks apart: vanilla's half-second cooldown must not swallow them.
			victim.damageCooldownTime = 10;
		}
		Boolean landed = resolving(() -> {
			if (rules != null && rules.realm() == DuelRuleSet.RealmRule.TEKAN_RANAH) {
				float share = swing.kind.share * extra;
				return victim.hurtServer(level, duelSource(level, attacker), share * victim.getMaxHealth());
			}
			float kindMul = swing.kind.damage * extra * underdog(attacker, victim, rules);
			if (swing.qi() && swing.kind != SwingKind.QI) {
				kindMul *= 1.25F;
			}
			if (attacker instanceof ServerPlayer player) {
				return playerCut(level, player, victim, kindMul);
			}
			return mobCut(level, attacker, victim, kindMul);
		});
		float after = victim.getHealth() + victim.getAbsorptionAmount();
		return landed != null && landed ? Math.max(0.0F, before - after) : 0.0F;
	}

	/** A fixed share of {@code victim}'s max health from {@code attacker} (Qi Wave in TEKAN, Companion Sword). */
	public static boolean share(LivingEntity attacker, LivingEntity victim, float share) {
		if (!(attacker.level() instanceof ServerLevel level) || !victim.isAlive()) {
			return false;
		}
		if (victim.damageCooldownTime > 10) {
			victim.damageCooldownTime = 10;
		}
		return resolving(() -> victim.hurtServer(level, duelSource(level, attacker), share * victim.getMaxHealth()));
	}

	/** A flat amount of ordinary attack damage from {@code attacker} (Qi Wave in BEBAS). */
	public static boolean flat(LivingEntity attacker, LivingEntity victim, float amount) {
		if (!(attacker.level() instanceof ServerLevel level) || !victim.isAlive() || !(amount > 0.0F)) {
			return false;
		}
		if (victim.damageCooldownTime > 10) {
			victim.damageCooldownTime = 10;
		}
		DamageSource source = attacker instanceof ServerPlayer player ? level.damageSources().playerAttack(player)
				: level.damageSources().mobAttack(attacker);
		return resolving(() -> victim.hurtServer(level, source, amount));
	}

	private static boolean playerCut(ServerLevel level, ServerPlayer player, LivingEntity victim, float kindMul) {
		ItemStack weapon = player.getMainHandItem();
		DamageSource source = level.damageSources().playerAttack(player);
		float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * kindMul;
		float amount = EnchantmentHelper.modifyDamage(level, weapon, victim, source, base);
		boolean critical = player.fallDistance > 0.0D && !player.onGround() && !player.onClimbable() && !player.isInWater()
				&& !player.isPassenger() && !player.isSprinting();
		if (critical) {
			amount *= 1.5F;
		}
		if (!(amount > 0.0F)) {
			return false;
		}
		boolean hurt = victim.hurtServer(level, source, amount);
		if (hurt) {
			EnchantmentHelper.doPostAttackEffects(level, victim, source);
			if (!weapon.isEmpty()) {
				weapon.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
			}
			player.setLastHurtMob(victim);
			player.causeFoodExhaustion(0.1F);
		}
		return hurt;
	}

	private static boolean mobCut(ServerLevel level, LivingEntity attacker, LivingEntity victim, float kindMul) {
		AttributeInstance attack = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null && kindMul != 1.0F) {
			attack.addOrUpdateTransientModifier(new AttributeModifier(KIND_ID, kindMul - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		try {
			if (attacker instanceof Mob mob) {
				mob.swingForAttack(InteractionHand.MAIN_HAND);
				return mob.doHurtTarget(level, victim);
			}
			return attacker.doHurtTarget(level, victim);
		} finally {
			if (attack != null) {
				attack.removeModifier(KIND_ID);
			}
		}
	}

	/** The BEBAS underdog bonus: the lower realm deals ×(1 + 0.15 per realm), at most ×1.45 (1 otherwise). */
	public static float underdog(LivingEntity attacker, LivingEntity victim, @Nullable DuelRuleSet rules) {
		if (rules == null || rules.realm() != DuelRuleSet.RealmRule.BEBAS) {
			return 1.0F;
		}
		int gap = Posture.realm(victim) - Posture.realm(attacker);
		return gap <= 0 ? 1.0F : 1.0F + Math.min(UNDERDOG_MAX, UNDERDOG_PER_REALM * gap);
	}

	/** The outcome of the realm rule HANDICAP on posture the higher side deals: ×(1 − 0.08 per realm), at least ×0.6. */
	public static float handicapPosture(LivingEntity attacker, LivingEntity victim, @Nullable DuelRuleSet rules) {
		if (rules == null || rules.realm() != DuelRuleSet.RealmRule.HANDICAP) {
			return 1.0F;
		}
		int gap = Posture.realm(attacker) - Posture.realm(victim);
		return gap <= 0 ? 1.0F : Math.max(0.6F, 1.0F - 0.08F * gap);
	}

	/** HANDICAP: the higher side's deflect window is 1 tick narrower per 2 realms (never below 3; Perfect stays 2). */
	public static int handicapWindow(LivingEntity defender, @Nullable LivingEntity attacker, @Nullable DuelRuleSet rules) {
		if (rules == null || attacker == null || rules.realm() != DuelRuleSet.RealmRule.HANDICAP) {
			return 0;
		}
		int gap = Posture.realm(defender) - Posture.realm(attacker);
		return gap <= 0 ? 0 : -(gap / 2);
	}

	/** {@code DuelIds} outcome name for logs. */
	public static String outcomeName(int outcome) {
		return switch (outcome) {
			case DuelIds.OUT_HIT -> "HIT";
			case DuelIds.OUT_WHIFF -> "WHIFF";
			case DuelIds.OUT_BLOCK -> "BLOCK";
			case DuelIds.OUT_DEFLECT -> "DEFLECT";
			case DuelIds.OUT_PERFECT -> "PERFECT";
			case DuelIds.OUT_CLASH -> "CLASH";
			case DuelIds.OUT_CLASH_MENANG -> "CLASH_MENANG";
			case DuelIds.OUT_BIND -> "BIND";
			case DuelIds.OUT_BIND_MENANG_A -> "BIND_MENANG_A";
			case DuelIds.OUT_BIND_MENANG_B -> "BIND_MENANG_B";
			case DuelIds.OUT_BIND_LEPAS -> "BIND_LEPAS";
			case DuelIds.OUT_BIND_RELEASE_TRICK -> "BIND_RELEASE_TRICK";
			case DuelIds.OUT_GUARD_BREAK -> "GUARD_BREAK";
			case DuelIds.OUT_POSTURE_BREAK -> "POSTURE_BREAK";
			case DuelIds.OUT_DEATHBLOW -> "DEATHBLOW";
			case DuelIds.OUT_EVADE -> "EVADE";
			case DuelIds.OUT_MIKIRI -> "MIKIRI";
			case DuelIds.OUT_QI_DEFLECT -> "QI_DEFLECT";
			case DuelIds.OUT_QI_REFLECT -> "QI_REFLECT";
			case DuelIds.OUT_COMPANION_DEFLECT -> "COMPANION_DEFLECT";
			default -> "?";
		};
	}

	private ClashDamage() {
	}
}
