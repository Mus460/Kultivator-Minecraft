package dev.kultivator.tempa;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.elite.ElementRelations;
import dev.kultivator.elite.MobElements;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Artifact power at work (DESIGN 25.5.5, 25.5.6, 25.5.7): one effect per form, from a Pedang in the main hand, another form
 * in the offhand, or the natal treasure (always, ×1.25); when two share a form only the highest AP applies. Pedang: a real
 * sword plus a caster factor (the spine clamps the product to 1.30; never in arenas, duels or against players). Payung:
 * Absorption for the wielder and allies within 4. Wajan: damage reduction against monsters (at most 0.25) through
 * {@code DamageHooks}. Lonceng: Slowness II (and Weakness I from Abadi) on hostiles near each technique hit, never on
 * players, ×0.25 on bosses. The natal sword's melee bonus works only with no weapon in the main hand, is capped at +8
 * against players and is off in duels. Weapon-spirit Sahutan strikes go only to {@code Targeting.hostilesAround} targets.
 */
public final class ArtifactEffects {

	public static final double SWORD_DAO_PER_AP = 0.02D;
	public static final double OTHER_DAO_PER_AP = 0.01D;
	public static final double ELEMENT_MATCH = 0.05D;
	public static final double WAJAN_PER_AP = 0.015D;
	public static final double WAJAN_CAP = 0.25D;
	public static final double WAJAN_OVERCOME = 0.03D;
	public static final int WAJAN_BLOCK_TICKS = 600;
	public static final int PAYUNG_BASE_SECONDS = 60;
	public static final int PAYUNG_STEP_SECONDS = 4;
	public static final int PAYUNG_TICKS = 200;
	public static final int PAYUNG_SPIRIT_TICKS = 300;
	public static final double PAYUNG_RADIUS = 4.0D;
	public static final int LONCENG_TICKS = 40;
	public static final double BOSS_CONTROL = 0.25D;
	public static final int LONCENG_COOLDOWN = 20;
	public static final int LONCENG_CHIME_TICKS = 120;
	public static final double NATAL_PVP_CAP = 8.0D;
	public static final double FIST = 1.0D;
	public static final int SAHUTAN_SWORD_TICKS = 80;
	public static final int SAHUTAN_SWORD_COMPLETE_TICKS = 60;
	public static final double SAHUTAN_SWORD_SHARE = 0.3D;
	public static final double SAHUTAN_SWORD_COMPLETE_SHARE = 0.5D;
	public static final double SAHUTAN_RANGE = 16.0D;
	public static final double SAHUTAN_CHAIN = 6.0D;
	public static final double LOW_HEALTH = 0.3D;
	public static final int LOW_HEALTH_COOLDOWN = 1200;

	public static final Identifier NATAL_SWORD_ID = Kultivator.id("natal_sword");
	public static final Identifier NATAL_WAJAN_ID = Kultivator.id("natal_wajan");

	/** The artifact that drives one form for a player: its record, AP (natal factor included) and whether it is the natal treasure. */
	public record Active(CompoundTag art, double ap, boolean natal) {
	}

	/** Per-player timers (game time): Payung ready, Payung renew, Wajan block, Lonceng hit, chime, sword strike, low-health. */
	private static final class Timers {
		long payung;
		long payungRenew;
		long wajanBlock;
		long bellHit;
		long chime;
		long strike;
		long lowHealth;
	}

	private static final Map<UUID, Timers> TIMERS = new HashMap<>();

	private static Timers timers(ServerPlayer player) {
		return TIMERS.computeIfAbsent(player.getUUID(), key -> new Timers());
	}

	static void forget(ServerPlayer player) {
		TIMERS.remove(player.getUUID());
	}

	// ================================================================== numbers

	public static int payungInterval(int grade) {
		return PAYUNG_BASE_SECONDS - PAYUNG_STEP_SECONDS * grade;
	}

	public static int payungAmplifier(int grade) {
		return Math.min(2, grade / 2);
	}

	public static double wajanReduction(double ap) {
		return Math.min(WAJAN_CAP, WAJAN_PER_AP * ap);
	}

	public static double loncengRadius(int grade) {
		return 4.0D + grade;
	}

	// ================================================================== lookup

	/** The best artifact of {@code form} working for the player now, or null. */
	public static @Nullable Active best(ServerPlayer player, String form) {
		Active best = null;
		CompoundTag natal = NatalTreasureSelf.record(player);
		if (natal != null && form.equals(ArtifactItem.form(natal))) {
			best = new Active(natal, ArtifactItem.powerOf(natal) * ArtifactItem.NATAL_FACTOR, true);
		}
		ItemStack held = ArtifactItem.PEDANG.equals(form) ? player.getMainHandItem() : player.getOffhandItem();
		if (ArtifactItem.is(held)) {
			CompoundTag art = ArtifactItem.data(held);
			if (form.equals(ArtifactItem.form(art)) && NatalTreasureSelf.usable(player, art)) {
				double ap = ArtifactItem.powerOf(art);
				if (best == null || ap > best.ap) {
					best = new Active(art, ap, false);
				}
			}
		}
		return best;
	}

	/** True for a main-hand item that counts as a weapon (the natal sword's melee bonus then stays off). */
	static boolean isWeapon(ItemStack stack) {
		return !stack.isEmpty() && (stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL) || ArtifactItem.is(stack)
				|| stack.is(net.minecraft.tags.ItemTags.SWORDS));
	}

	// ================================================================== registration

	static void init() {
		TechniquePower.addCasterFactor((attacker, spec, target) -> {
			if (!(attacker instanceof ServerPlayer player) || target instanceof Player) {
				return 1.0D;
			}
			Active sword = best(player, ArtifactItem.PEDANG);
			if (sword == null) {
				return 1.0D;
			}
			Dao dao = spec == null ? null : spec.dao();
			double factor = 1.0D + (dao == Dao.SWORD ? SWORD_DAO_PER_AP : OTHER_DAO_PER_AP) * sword.ap;
			Element element = ArtifactItem.element(sword.art);
			if (element != null && dao != null && dao.affinity() == element) {
				factor += ELEMENT_MATCH;
			}
			return factor;
		});
		DamageHooks.register(DamageHooks.Stage.DR, ArtifactEffects::reduce);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && DamageHooks.isTechnique(source) && source.getEntity() instanceof ServerPlayer player && entity != player) {
				Timers t = timers(player);
				long now = player.level().getGameTime();
				if (now >= t.bellHit) {
					Active bell = best(player, ArtifactItem.LONCENG);
					if (bell != null) {
						t.bellHit = now + LONCENG_COOLDOWN;
						bell(player, entity.position(), bell);
					}
				}
			}
		});
	}

	/** Wajan damage reduction and its spirit block for players hit by monsters; the natal sword's PvP cap. */
	private static float reduce(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer victim) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer striker && striker != victim && source.getDirectEntity() == striker) {
			AttributeInstance attack = striker.getAttribute(Attributes.ATTACK_DAMAGE);
			AttributeModifier natal = attack == null ? null : attack.getModifier(NATAL_SWORD_ID);
			if (natal != null) {
				double bonus = natal.amount();
				double excess = Targeting.sparring.test(striker, victim) ? bonus : Math.max(0.0D, bonus - NATAL_PVP_CAP);
				return (float) Math.max(0.0D, amount - excess);
			}
			return amount;
		}
		if (!(attacker instanceof Enemy) || !(attacker instanceof LivingEntity monster)) {
			return amount;
		}
		Active wok = best(victim, ArtifactItem.WAJAN);
		if (wok == null) {
			return amount;
		}
		long now = victim.level().getGameTime();
		if (wok.natal && ArtifactItem.spiritStage(wok.art) >= 2) {
			Timers t = timers(victim);
			if (now >= t.wajanBlock) {
				// Sahutan: the spirit turns one monster blow aside.
				t.wajanBlock = now + WAJAN_BLOCK_TICKS;
				Fx.send(victim.level(), victim.position().add(0.0D, 1.0D, 0.0D), FxType.TEMPA, TempaFxIds.WAJAN_BLOCK, 0, 1.0F);
				victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
				return 0.0F;
			}
		}
		double reduction = wajanReduction(wok.ap);
		Element element = ArtifactItem.element(wok.art);
		Element foe = MobElements.of(monster);
		if (element != null && foe != null && ElementRelations.overcomes(element, foe)) {
			reduction += WAJAN_OVERCOME;
		}
		return (float) (amount * (1.0D - Math.min(WAJAN_CAP + WAJAN_OVERCOME, reduction)));
	}

	// ================================================================== per player (every 10 ticks)

	static void tick(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		refreshAttributes(player, data);
		if (player.tickCount % 20 != 0) {
			return;
		}
		long now = player.level().getGameTime();
		Timers t = timers(player);
		Active umbrella = best(player, ArtifactItem.PAYUNG);
		if (umbrella != null) {
			int grade = ArtifactItem.grade(umbrella.art);
			boolean spirit = umbrella.natal && ArtifactItem.spiritStage(umbrella.art) >= 2;
			boolean renew = spirit && now >= t.payungRenew && player.getHealth() < player.getMaxHealth() * 0.5F;
			if (now >= t.payung || renew) {
				shelter(player, grade, spirit);
				t.payung = now + payungInterval(grade) * 20L;
				if (renew) {
					t.payungRenew = now + payungInterval(grade) * 20L;
				}
			}
		}
		CompoundTag natal = NatalTreasureSelf.record(player);
		if (natal == null) {
			return;
		}
		int stage = ArtifactItem.spiritStage(natal);
		if (stage < 2) {
			return;
		}
		String form = ArtifactItem.form(natal);
		if (ArtifactItem.PEDANG.equals(form)) {
			if (now >= t.strike && strike(player, data, stage)) {
				t.strike = now + (stage >= 3 ? SAHUTAN_SWORD_COMPLETE_TICKS : SAHUTAN_SWORD_TICKS);
			}
		} else if (ArtifactItem.LONCENG.equals(form) && now >= t.chime) {
			Active bell = best(player, ArtifactItem.LONCENG);
			if (bell != null && !Targeting.hostilesAround(player, player.position(), loncengRadius(ArtifactItem.grade(bell.art)), false).isEmpty()) {
				t.chime = now + LONCENG_CHIME_TICKS;
				bell(player, player.position(), bell);
			}
		}
		// Roh Lengkap: below 30% health the treasure acts by itself once a minute.
		if (stage >= 3 && now >= t.lowHealth && player.getHealth() < player.getMaxHealth() * LOW_HEALTH) {
			t.lowHealth = now + LOW_HEALTH_COOLDOWN;
			switch (form) {
				case ArtifactItem.PAYUNG -> shelter(player, ArtifactItem.grade(natal), true);
				case ArtifactItem.WAJAN -> t.wajanBlock = 0L;
				case ArtifactItem.LONCENG -> {
					Active bell = best(player, ArtifactItem.LONCENG);
					if (bell != null) {
						bell(player, player.position(), bell);
					}
				}
				default -> strike(player, data, stage);
			}
		}
	}

	/** The natal sword's melee bonus (only with no weapon in hand, off in duels) and the natal Wajan's knockback resistance. */
	private static void refreshAttributes(ServerPlayer player, CultivationData data) {
		CompoundTag natal = NatalTreasureSelf.record(player);
		AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			double bonus = 0.0D;
			if (natal != null && ArtifactItem.PEDANG.equals(ArtifactItem.form(natal)) && !isWeapon(player.getMainHandItem()) && data.sparringWith() < 0) {
				bonus = ArtifactItem.swordDamage(ArtifactItem.powerOf(natal) * ArtifactItem.NATAL_FACTOR) - FIST;
			}
			setModifier(attack, NATAL_SWORD_ID, bonus);
		}
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			double resist = natal != null && ArtifactItem.WAJAN.equals(ArtifactItem.form(natal))
					? ArtifactItem.WAJAN_KNOCKBACK_PER_GRADE * (ArtifactItem.grade(natal) + 1) : 0.0D;
			setModifier(knockback, NATAL_WAJAN_ID, resist);
		}
	}

	private static void setModifier(AttributeInstance instance, Identifier id, double amount) {
		AttributeModifier current = instance.getModifier(id);
		if (amount <= 0.0D) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current == null || Math.abs(current.amount() - amount) > 1.0E-6D) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Clears the transient natal modifiers (logout, unbind). */
	static void clearAttributes(ServerPlayer player) {
		AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.removeModifier(NATAL_SWORD_ID);
		}
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			knockback.removeModifier(NATAL_WAJAN_ID);
		}
	}

	// ================================================================== form effects

	/** Payung: Absorption for the wielder and allies within 4 (players, own disciples, pets). */
	private static void shelter(ServerPlayer player, int grade, boolean spirit) {
		int amplifier = payungAmplifier(grade);
		int ticks = spirit ? PAYUNG_SPIRIT_TICKS : PAYUNG_TICKS;
		for (LivingEntity ally : Targeting.alliesAround(player, PAYUNG_RADIUS)) {
			ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, amplifier, true, true, true));
		}
		Fx.send(player.level(), player.position(), FxType.TEMPA, TempaFxIds.PAYUNG_SHELL, grade, (float) PAYUNG_RADIUS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
	}

	/** Lonceng: Slowness II for 2 s (and Weakness I from Abadi) on hostiles within {@code 4 + grade}; never players. */
	private static void bell(ServerPlayer player, Vec3 center, Active bell) {
		int grade = ArtifactItem.grade(bell.art);
		double radius = loncengRadius(grade);
		List<LivingEntity> targets = Targeting.hostilesAround(player, center, radius, false);
		for (LivingEntity target : targets) {
			if (target instanceof Player) {
				continue;
			}
			int ticks = TechniquePower.isBoss(target) ? (int) Math.round(LONCENG_TICKS * BOSS_CONTROL) : LONCENG_TICKS;
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1), player);
			if (grade >= 4) {
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), player);
			}
		}
		Fx.send(player.level(), center, FxType.TEMPA, TempaFxIds.BELL_CHIME, grade, (float) radius);
		player.level().playSound(null, center.x, center.y, center.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.3F);
	}

	/** Pedang Sahutan: the spirit strikes the player's target by itself (two chained strikes at Roh Lengkap). */
	private static boolean strike(ServerPlayer player, CultivationData data, int stage) {
		LivingEntity target = player.getLastHurtMob();
		if (target == null || !target.isAlive() || target.level() != player.level() || player.tickCount - player.getLastHurtMobTimestamp() > 200
				|| target.distanceToSqr(player) > SAHUTAN_RANGE * SAHUTAN_RANGE || target instanceof Player
				|| !Targeting.isHostileTo(target, player)) {
			return false;
		}
		double share = stage >= 3 ? SAHUTAN_SWORD_COMPLETE_SHARE : SAHUTAN_SWORD_SHARE;
		float damage = (float) (share * Techniques.power(data));
		ServerLevel level = player.level();
		Vec3 from = player.position().add(0.0D, 1.6D, 0.0D);
		hit(player, target, damage, from);
		if (stage >= 3) {
			for (LivingEntity next : Targeting.hostilesAround(player, target.position(), SAHUTAN_CHAIN, false)) {
				if (next != target && !(next instanceof Player)) {
					hit(player, next, damage, target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D));
					break;
				}
			}
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
		return true;
	}

	private static void hit(ServerPlayer player, LivingEntity target, float damage, Vec3 from) {
		Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
		Fx.send(player.level(), from, to, FxType.TEMPA, TempaFxIds.SPIRIT_STRIKE, 0, 1.0F);
		Techniques.hurt(player, target, damage);
	}

	private ArtifactEffects() {
	}
}
