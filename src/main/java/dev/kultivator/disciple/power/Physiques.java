package dev.kultivator.disciple.power;

import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.DaoTechniques;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fisik Langka in effect (DESIGN 20.3.5, all monsters-only): Woeful Poison Body (BTTH), Taixu Ancient Dragon (BTTH), Dou Di
 * Bloodline (BTTH), Proud Golden Body (Martial Peak), Devouring Ancestral Symbol (Martial Universe), Supreme Bone and
 * Double Pupils (Perfect World) and the Attributeless Soul Realm (TDG). The Heretic God Veins live in {@link HereticGates}.
 */
final class Physiques {

	// Woeful Poison Body
	public static final int POISON_MELEE_TICKS = 80;
	public static final int POISON_MELEE_AMPLIFIER = 1;
	public static final double POISON_AURA_RADIUS = 4.0D;
	public static final double POISON_AURA_U = 0.3D;
	public static final int TOXIN_MAX = 100;
	public static final int RAMPAGE_TICKS = 1200;
	public static final double CLOUD_RADIUS = 6.0D;
	public static final int CLOUD_TICKS = 160;
	public static final int CLOUD_COOLDOWN = 900;
	// Taixu Ancient Dragon
	public static final double TAIXU_PILL_XP = 1.5D;
	public static final double TAIXU_PILL_EFFECT = 1.5D;
	public static final double TAIXU_PILL_COOLDOWN = 0.5D;
	public static final int DRAGON_TICKS = 400;
	public static final int DRAGON_COOLDOWN = 2400;
	public static final double DRAGON_SCALE = 0.4D;
	public static final double DRAGON_DAMAGE = 1.5D;
	public static final double BREATH_RANGE = 6.0D;
	/** 1.2 U per second, as two pulses. */
	public static final double BREATH_U = 0.6D;
	public static final float BREATH_FIRE_SECONDS = 3.0F;
	// Dou Di Bloodline
	public static final double DOU_DI_QUALITY = 0.12D;
	public static final double DOU_DI_STATS = 0.15D;
	// Proud Golden Body
	public static final double GOLD_DAMAGE_PER_STACK = 10.0D;
	public static final int GOLD_MAX_STACKS = 40;
	public static final int GOLD_DECAY_SECONDS = 30;
	public static final double GOLD_BONUS = 0.015D;
	public static final double GOLD_GUARD = 0.005D;
	public static final double GOLD_HP_EVERY = 500.0D;
	public static final double GOLD_HP_STEP = 0.5D;
	public static final double GOLD_HP_MAX = 30.0D;
	// Devouring Ancestral Symbol
	public static final double DEVOUR_XP = 1.25D;
	public static final double DEVOUR_HEAL = 0.05D;
	// Supreme Bone
	public static final double BONE_POWER = 1.5D;
	public static final double BONE_SHED_POWER = 2.25D;
	public static final double BONE_XP = 1.25D;
	public static final int BONE_SHED_STARS = 9;
	// Double Pupils
	public static final double PUPILS_DODGE = 0.25D;
	public static final double PUPILS_TECHNIQUE = 1.2D;
	public static final int EYE_INTERVAL_SECONDS = 10;
	public static final double EYE_RANGE = 16.0D;
	// Attributeless Soul Realm
	public static final double ATTRIBUTELESS_EARLY = 0.8D;
	public static final double ATTRIBUTELESS_LATE = 1.4D;

	private static final Identifier DOU_DI_HEALTH = DisciplePowers.id("dou_di_health");
	private static final Identifier DOU_DI_ATTACK = DisciplePowers.id("dou_di_attack");
	private static final Identifier DOU_DI_SPEED = DisciplePowers.id("dou_di_speed");
	private static final Identifier DOU_DI_ARMOR = DisciplePowers.id("dou_di_armor");
	private static final Identifier GOLD_HEALTH = DisciplePowers.id("golden_body_health");
	private static final Identifier DRAGON_SCALE_ID = DisciplePowers.id("dragon_form_scale");
	private static final Identifier DRAGON_STEADY_ID = DisciplePowers.id("dragon_form_steady");

	static void register() {
		DiscipleHooks.addDamageFactor(Physiques::damageFactor);
		// Taixu blood: pill effects x1.5 (the XP part already rides on xpFactor with TAIXU_PILL_XP).
		DiscipleHooks.addPillEffectFactor((entity, stack) -> PowerCore.physique(entity.data()) == PowerIds.PHYSIQUE_TAIXU ? TAIXU_PILL_EFFECT : 1.0D);
		DiscipleHooks.addHurtFactor((entity, source) -> {
			if (!PowerCore.powered(entity) || PowerCore.physique(entity.data()) != PowerIds.PHYSIQUE_GOLDEN_BODY) {
				return 1.0D;
			}
			return Math.max(0.5D, 1.0D - GOLD_GUARD * goldStacks(entity.data()));
		});
		DiscipleHooks.addOnHurt(Physiques::onHurt);
		DiscipleHooks.addStats((entity, sink) -> {
			if (!PowerCore.powered(entity)) {
				return;
			}
			DiscipleData data = entity.data();
			int physique = PowerCore.physique(data);
			if (physique == PowerIds.PHYSIQUE_DOU_DI && PowerCore.tag(data).getBooleanOr("dou_di", false)) {
				AttributeModifier.Operation total = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
				sink.add(Attributes.MAX_HEALTH, DOU_DI_HEALTH, DOU_DI_STATS, total);
				sink.add(Attributes.ATTACK_DAMAGE, DOU_DI_ATTACK, DOU_DI_STATS, total);
				sink.add(Attributes.MOVEMENT_SPEED, DOU_DI_SPEED, DOU_DI_STATS, total);
				sink.add(Attributes.ARMOR, DOU_DI_ARMOR, DOU_DI_STATS, total);
			}
			if (physique == PowerIds.PHYSIQUE_GOLDEN_BODY) {
				sink.add(Attributes.MAX_HEALTH, GOLD_HEALTH, PowerCore.tag(data).getDoubleOr("gold_hp", 0.0D), AttributeModifier.Operation.ADD_VALUE);
			}
		});
		DiscipleHooks.addOnKill(Physiques::onKill);
		DiscipleHooks.addQualityBonus((entity, target) -> PowerCore.physique(entity.data()) == PowerIds.PHYSIQUE_DOU_DI ? DOU_DI_QUALITY : 0.0D);
		DiscipleHooks.addDaoBypass(data -> PowerCore.physique(data) == PowerIds.PHYSIQUE_ATTRIBUTELESS);
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onEnd(DiscipleEntity entity, boolean success, dev.kultivator.disciple.DiscipleBreakthrough.Kind kind) {
				if (success && PowerCore.physique(entity.data()) == PowerIds.PHYSIQUE_DOU_DI && entity.data().realm().atLeast(Realm.DAO_UNION)) {
					awakenDouDi(entity, true);
				}
			}
		});
	}

	// ================================================================== hooks

	private static double damageFactor(DiscipleEntity entity, LivingEntity target, boolean technique) {
		if (!PowerCore.powered(entity)) {
			return 1.0D;
		}
		DiscipleData data = entity.data();
		double factor = 1.0D;
		switch (PowerCore.physique(data)) {
			case PowerIds.PHYSIQUE_GOLDEN_BODY -> factor *= 1.0D + GOLD_BONUS * goldStacks(data);
			case PowerIds.PHYSIQUE_DOUBLE_PUPILS -> factor *= technique ? PUPILS_TECHNIQUE : 1.0D;
			case PowerIds.PHYSIQUE_TAIXU -> factor *= DisciplePowers.live(entity).dragonUntil > PowerCore.now(entity) ? DRAGON_DAMAGE : 1.0D;
			default -> {
			}
		}
		return factor;
	}

	private static void onHurt(DiscipleEntity entity, DamageSource source, float damage) {
		if (!PowerCore.isDisciple(entity) || damage <= 0.0F || PowerCore.physique(entity.data()) != PowerIds.PHYSIQUE_GOLDEN_BODY) {
			return;
		}
		// Yang Kai's body grows with the beatings it takes.
		DiscipleData data = entity.data();
		CompoundTag tag = PowerCore.edit(data);
		double before = tag.getDoubleOr("gold_dmg", 0.0D);
		double after = Math.min(1.0E9D, before + damage);
		tag.putDouble("gold_dmg", after);
		int gained = (int) (Math.floor(after / GOLD_DAMAGE_PER_STACK) - Math.floor(before / GOLD_DAMAGE_PER_STACK));
		if (gained > 0) {
			tag.putInt("gold_stacks", Math.min(GOLD_MAX_STACKS, goldStacks(data) + gained));
		}
		int milestones = (int) (Math.floor(after / GOLD_HP_EVERY) - Math.floor(before / GOLD_HP_EVERY));
		double hp = tag.getDoubleOr("gold_hp", 0.0D);
		if (milestones > 0 && hp < GOLD_HP_MAX) {
			tag.putDouble("gold_hp", Math.min(GOLD_HP_MAX, hp + GOLD_HP_STEP * milestones));
			entity.refreshSynced();
			if (entity.level() instanceof ServerLevel level) {
				PowerCore.fx(level, entity.position(), PowerIds.FX_PHYSIQUE, PowerIds.PHYSIQUE_GOLDEN_BODY, 1.0F);
				PowerCore.sound(level, entity.position(), SoundEvents.ANVIL_USE, 0.5F, 1.6F);
			}
		}
	}

	private static void onKill(DiscipleEntity entity, LivingEntity victim) {
		DiscipleData data = entity.data();
		if (PowerCore.physique(data) != PowerIds.PHYSIQUE_DEVOURING || !(victim instanceof net.minecraft.world.entity.monster.Enemy)) {
			return;
		}
		// Lin Dong's symbol devours the fallen one's energy.
		entity.heal((float) (victim.getMaxHealth() * DEVOUR_HEAL));
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D), entity.position().add(0.0D, 1.0D, 0.0D),
					PowerIds.FX_DEVOUR, 0, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.SCULK_CATALYST_BLOOM, 0.6F, 0.8F);
		}
	}

	/** XP factors of the physiques (see TalentEffects.xpFactor). */
	static double xpFactor(DiscipleData data, XpSource source) {
		double factor = 1.0D;
		switch (PowerCore.physique(data)) {
			case PowerIds.PHYSIQUE_TAIXU -> factor *= source == XpSource.PILL ? TAIXU_PILL_XP : 1.0D;
			case PowerIds.PHYSIQUE_DEVOURING -> factor *= source == XpSource.COMBAT ? DEVOUR_XP : 1.0D;
			case PowerIds.PHYSIQUE_SUPREME_BONE -> factor *= source == XpSource.COMBAT ? BONE_XP : 1.0D;
			case PowerIds.PHYSIQUE_ATTRIBUTELESS -> {
				if (!data.realm().atLeast(Realm.GOLDEN_CORE)) {
					factor *= ATTRIBUTELESS_EARLY;
				} else if (data.realm().atLeast(Realm.DAO_UNION)) {
					factor *= ATTRIBUTELESS_LATE;
				}
			}
			default -> {
			}
		}
		return factor;
	}

	/** Multiplies the pill cooldown after a feeding (Taixu: half). */
	static double pillCooldownFactor(DiscipleData data) {
		return PowerCore.physique(data) == PowerIds.PHYSIQUE_TAIXU ? TAIXU_PILL_COOLDOWN : 1.0D;
	}

	static int goldStacks(DiscipleData data) {
		return Math.max(0, Math.min(GOLD_MAX_STACKS, PowerCore.tag(data).getIntOr("gold_stacks", 0)));
	}

	static int toxin(DiscipleData data) {
		return Math.max(0, Math.min(TOXIN_MAX, PowerCore.tag(data).getIntOr("toxic", 0)));
	}

	/** The Healing Pill (the Poison Pill stand-in) empties the toxic meter. */
	static void resetToxin(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (PowerCore.physique(data) != PowerIds.PHYSIQUE_POISON || toxin(data) <= 0) {
			return;
		}
		PowerCore.edit(data).putInt("toxic", 0);
		PowerCore.tell(entity, PowerCore.key("poison.calmed", PowerCore.name(entity)).withColor(0x9FE07A));
	}

	/** True while the Poison Body rages (60 s after the meter filled). */
	static boolean rampaging(DiscipleEntity entity) {
		return PowerCore.physique(entity.data()) == PowerIds.PHYSIQUE_POISON
				&& !PowerCore.ready(entity.data(), "rampage", PowerCore.now(entity));
	}

	/** The poison body is immune to Poison and Wither (the effects' damage). */
	static boolean immuneTo(DiscipleEntity entity, DamageSource source) {
		if (PowerCore.physique(entity.data()) != PowerIds.PHYSIQUE_POISON) {
			return false;
		}
		return source.is(DamageTypes.WITHER) || source.is(DamageTypes.MAGIC) && source.getEntity() == null && entity.hasEffect(MobEffects.POISON);
	}

	/** On a melee hit: the Poison Body poisons the struck monster. */
	static void onMeleeHit(DiscipleEntity entity, LivingEntity target) {
		if (PowerCore.physique(entity.data()) == PowerIds.PHYSIQUE_POISON && target.isAlive()) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_MELEE_TICKS, POISON_MELEE_AMPLIFIER), entity);
		}
	}

	/** The innate Supreme Bone technique of a root's main element. */
	static Technique boneTechnique(int rootElements) {
		List<Element> elements = Element.fromMask(rootElements);
		Element main = elements.isEmpty() ? Element.METAL : elements.get(0);
		return switch (main) {
			case METAL -> Technique.SWORD_QI;
			case WOOD -> Technique.LIFE_DRAIN;
			case WATER, ICE -> Technique.FROST_LOTUS;
			case FIRE -> Technique.FLAME_PALM;
			case LIGHTNING -> Technique.THUNDER_PALM;
			case EARTH -> Technique.MOUNTAIN_STOMP;
			case WIND -> Technique.GALE;
			case SPACE -> Technique.SPACE_PRISON;
		};
	}

	// ================================================================== once a second

	static void tick(DiscipleEntity entity, DisciplePowers.Live live) {
		DiscipleData data = entity.data();
		int physique = PowerCore.physique(data);
		if (physique <= 0) {
			return;
		}
		ServerLevel level = (ServerLevel) entity.level();
		long now = level.getGameTime();
		ServerPlayer master = entity.owner();
		switch (physique) {
			case PowerIds.PHYSIQUE_POISON -> tickPoison(entity, live, data, level, master, now);
			case PowerIds.PHYSIQUE_GOLDEN_BODY -> {
				if (goldStacks(data) > 0 && ++live.goldSeconds >= GOLD_DECAY_SECONDS) {
					live.goldSeconds = 0;
					PowerCore.edit(data).putInt("gold_stacks", goldStacks(data) - 1);
				}
			}
			case PowerIds.PHYSIQUE_DOUBLE_PUPILS -> {
				if (++live.eyeSeconds >= EYE_INTERVAL_SECONDS) {
					live.eyeSeconds = 0;
					markMonsters(entity, master, level);
				}
			}
			case PowerIds.PHYSIQUE_DOU_DI -> {
				if (data.realm().atLeast(Realm.DAO_UNION) && !PowerCore.tag(data).getBooleanOr("dou_di", false)) {
					awakenDouDi(entity, false);
				}
			}
			case PowerIds.PHYSIQUE_SUPREME_BONE -> {
				if (!PowerCore.tag(data).getBooleanOr("bone_shed", false) && DisciplePaths.stars(data) >= BONE_SHED_STARS) {
					PowerCore.edit(data).putBoolean("bone_shed", true);
					PowerCore.fx(level, entity.position(), PowerIds.FX_PHYSIQUE, physique, 2.0F);
					PowerCore.sound(level, entity.position(), SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.6F);
					PowerCore.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 0.8F, 1.4F);
					PowerCore.tell(entity, PowerCore.key("bone.shed", PowerCore.name(entity)).withColor(0xF2F0E0));
				}
			}
			default -> {
			}
		}
	}

	private static void tickPoison(DiscipleEntity entity, DisciplePowers.Live live, DiscipleData data, ServerLevel level,
			@Nullable ServerPlayer master, long now) {
		// Immune: the poison the body makes never harms it.
		if (entity.hasEffect(MobEffects.POISON)) {
			entity.removeEffect(MobEffects.POISON);
		}
		if (entity.hasEffect(MobEffects.WITHER)) {
			entity.removeEffect(MobEffects.WITHER);
		}
		if (!data.realm().atLeast(Realm.FOUNDATION) || master == null) {
			return;
		}
		boolean fighting = entity.getTarget() != null && entity.getTarget().isAlive();
		boolean rampage = rampaging(entity);
		if (fighting && !rampage) {
			if (++live.fightSeconds >= 60) {
				live.fightSeconds = 0;
				int toxin = toxin(data) + 1;
				PowerCore.edit(data).putInt("toxic", Math.min(TOXIN_MAX, toxin));
				if (toxin >= TOXIN_MAX) {
					// The body's poison grows out of control (the Little Fairy Doctor before the Poison Pill).
					PowerCore.setCooldown(data, "rampage", now + RAMPAGE_TICKS);
					PowerCore.edit(data).putInt("toxic", 0);
					PowerCore.fx(level, entity.position(), PowerIds.FX_PHYSIQUE, PowerIds.PHYSIQUE_POISON, 3.0F);
					PowerCore.sound(level, entity.position(), SoundEvents.WITCH_CELEBRATE, 0.8F, 0.6F);
					PowerCore.tell(entity, PowerCore.key("poison.rampage", PowerCore.name(entity)).withStyle(ChatFormatting.DARK_PURPLE));
					rampage = true;
				}
			}
		}
		if (!fighting && !rampage) {
			return;
		}
		// The aura: 0.3 U a second within 4 blocks (twice that while raging, even when only following); one query.
		double amount = DiscipleTechniques.power(data) * POISON_AURA_U * (rampage ? 2.0D : 1.0D);
		for (LivingEntity target : PowerCore.hostiles(entity, master, entity.position(), POISON_AURA_RADIUS)) {
			PowerCore.hurt(entity, master, target, amount, PowerCore.HIT_AREA);
		}
	}

	/** Every 10 s: monsters within 16 get an eye glyph that only the master sees (client FX, no Glowing effect). */
	private static void markMonsters(DiscipleEntity entity, @Nullable ServerPlayer master, ServerLevel level) {
		if (master == null || master.level() != level || master.distanceToSqr(entity) > 48.0D * 48.0D) {
			return;
		}
		List<LivingEntity> seen = PowerCore.hostiles(entity, master, entity.position(), EYE_RANGE);
		int count = Math.min(8, seen.size());
		for (int i = 0; i < count; i++) {
			LivingEntity monster = seen.get(i);
			Vec3 above = monster.position().add(0.0D, monster.getBbHeight() + 0.6D, 0.0D);
			Fx.sendTo(master, above, above, FxType.DISCIPLE_POWER, PowerIds.FX_EYE_MARK, monster.getId(), 4.0F);
		}
	}

	/** The Dou Di bloodline awakens at Dao Union: +15% to every stat and, after a breakthrough, one stage. */
	private static void awakenDouDi(DiscipleEntity entity, boolean stage) {
		DiscipleData data = entity.data();
		CompoundTag tag = PowerCore.edit(data);
		if (tag.getBooleanOr("dou_di", false)) {
			return;
		}
		tag.putBoolean("dou_di", true);
		Rank rank = data.rank();
		if (stage && !rank.isBottleneck() && !rank.isMax()) {
			data.setRank(rank.next());
			data.setXp(0.0D);
		}
		entity.refreshSynced();
		entity.setHealth(entity.getMaxHealth());
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, entity.position(), PowerIds.FX_REVEAL, 3, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
			PowerCore.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.8F);
		}
		PowerCore.tell(entity, PowerCore.key("dou_di.awaken", PowerCore.name(entity)).withColor(0xFFB43A));
	}

	// ================================================================== combat casts

	/** The physique's own casts in a fight: dragon form, the seven-coloured cloud, the Supreme Bone technique. */
	static boolean combatCast(DiscipleEntity entity, LivingEntity target) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		if (master == null || !(entity.level() instanceof ServerLevel level) || !PowerCore.isDisciple(entity)) {
			return false;
		}
		long now = level.getGameTime();
		return switch (PowerCore.physique(data)) {
			case PowerIds.PHYSIQUE_TAIXU -> data.realm().atLeast(Realm.NASCENT_SOUL) && PowerCore.ready(data, "dragon", now)
					&& entity.distanceToSqr(target) <= 64.0D && dragonForm(entity, level, now);
			case PowerIds.PHYSIQUE_POISON -> data.realm().atLeast(Realm.DEITY_TRANSFORMATION) && PowerCore.ready(data, "cloud", now)
					&& poisonCloud(entity, master, level, target, now);
			case PowerIds.PHYSIQUE_SUPREME_BONE -> data.realm().atLeast(Realm.FOUNDATION) && PowerCore.ready(data, "bone", now)
					&& boneCast(entity, data, target, now);
			default -> false;
		};
	}

	/** Zi Yan's dragon form: larger (if there is head room), +50% damage, steady, breathing fire for 20 s. */
	private static boolean dragonForm(DiscipleEntity entity, ServerLevel level, long now) {
		DiscipleData data = entity.data();
		PowerCore.setCooldown(data, "dragon", now + DRAGON_COOLDOWN);
		DisciplePowers.Live live = DisciplePowers.live(entity);
		live.dragonUntil = now + DRAGON_TICKS;
		boolean room = level.noCollision(entity, entity.getBoundingBox().expandTowards(0.0D, entity.getBbHeight() * DRAGON_SCALE + 0.2D, 0.0D)
				.inflate(entity.getBbWidth() * DRAGON_SCALE * 0.5D, 0.0D, entity.getBbWidth() * DRAGON_SCALE * 0.5D));
		AttributeInstance scale = entity.getAttribute(Attributes.SCALE);
		if (room && scale != null) {
			scale.addOrUpdateTransientModifier(new AttributeModifier(DRAGON_SCALE_ID, DRAGON_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		AttributeInstance steady = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (steady != null) {
			steady.addOrUpdateTransientModifier(new AttributeModifier(DRAGON_STEADY_ID, 1.0D, AttributeModifier.Operation.ADD_VALUE));
		}
		PowerCore.fx(level, entity.position(), PowerIds.FX_DRAGON, entity.getId(), DRAGON_TICKS);
		PowerCore.sound(level, entity.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 1.5F);
		PowerCore.sound(level, entity.position(), SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F);
		DisciplePowers.syncPower(entity);
		PowerPulses.start(entity, 10, 10, DRAGON_TICKS / 10, (e, lvl, index) -> {
			breathe(e, lvl);
			return DisciplePowers.live(e).dragonUntil > lvl.getGameTime();
		}, e -> {
			AttributeInstance s = e.getAttribute(Attributes.SCALE);
			if (s != null) {
				s.removeModifier(DRAGON_SCALE_ID);
			}
			AttributeInstance k = e.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (k != null) {
				k.removeModifier(DRAGON_STEADY_ID);
			}
			DisciplePowers.live(e).dragonUntil = 0L;
			if (!e.isRemoved()) {
				DisciplePowers.syncPower(e);
			}
		});
		return true;
	}

	/** A 6-block cone of dragon flame towards the target: 0.6 U and 3 s of burning (entities only). */
	private static void breathe(DiscipleEntity entity, ServerLevel level) {
		ServerPlayer master = entity.owner();
		LivingEntity target = entity.getTarget();
		if (master == null || target == null || !target.isAlive()) {
			return;
		}
		Vec3 eye = entity.getEyePosition();
		Vec3 dir = target.getBoundingBox().getCenter().subtract(eye);
		if (dir.lengthSqr() < 1.0E-4D || dir.length() > BREATH_RANGE + 2.0D) {
			return;
		}
		dir = dir.normalize();
		double cos = Math.cos(Math.toRadians(30.0D));
		double amount = DiscipleTechniques.power(entity.data()) * BREATH_U;
		boolean any = false;
		for (LivingEntity other : PowerCore.hostiles(entity, master, eye, BREATH_RANGE + 1.0D)) {
			Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > BREATH_RANGE + other.getBbWidth() * 0.5D || distance > 1.0E-3D && to.scale(1.0D / distance).dot(dir) < cos
					|| !entity.hasLineOfSight(other)) {
				continue;
			}
			if (PowerCore.hurt(entity, master, other, amount, PowerCore.HIT_AREA) && other.isAlive()) {
				DaoTechniques.ignite(other, BREATH_FIRE_SECONDS);
			}
			any = true;
		}
		if (any) {
			PowerCore.fx(level, eye, eye.add(dir.scale(BREATH_RANGE)), PowerIds.FX_DRAGON_BREATH, 0, 0.0F);
			PowerCore.sound(level, eye, SoundEvents.BLAZE_SHOOT, 0.5F, 0.7F);
		}
	}

	/** Kabut Racun Tujuh Warna: a 6-block cloud at the target for 8 s, Poison III and Wither II on monsters inside. */
	private static boolean poisonCloud(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity target, long now) {
		Vec3 centre = target.position();
		if (entity.distanceToSqr(target) > 16.0D * 16.0D || !PowerCore.safeCentre(level, master, centre)
				|| PowerCore.hostiles(entity, master, centre, CLOUD_RADIUS).size() < 2 && !PowerCore.isBoss(target)) {
			return false;
		}
		PowerCore.setCooldown(entity.data(), "cloud", now + CLOUD_COOLDOWN);
		PowerCore.fx(level, centre, PowerIds.FX_POISON_CLOUD, CLOUD_TICKS, (float) CLOUD_RADIUS);
		PowerCore.sound(level, centre, SoundEvents.WITCH_THROW, 1.0F, 0.6F);
		PowerCore.sound(level, centre, SoundEvents.BREWING_STAND_BREW, 1.0F, 0.6F);
		PowerPulses.start(entity, 0, 20, CLOUD_TICKS / 20, (e, lvl, index) -> {
			ServerPlayer owner = e.owner();
			if (owner == null) {
				return false;
			}
			for (LivingEntity victim : PowerCore.hostiles(e, owner, centre, CLOUD_RADIUS)) {
				victim.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 2), e);
				victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), e);
				victim.setLastHurtByMob(e);
			}
			return true;
		}, null);
		return true;
	}

	/** The innate bone technique: x1.5 D (x2.25 once the bone shed at star 9), its own cooldown and qi. */
	private static boolean boneCast(DiscipleEntity entity, DiscipleData data, LivingEntity target, long now) {
		Technique technique = boneTechnique(data.rootElements());
		double cost = DiscipleTechniques.qiCost(technique);
		if (data.qi() + 1.0E-6D < cost) {
			return false;
		}
		double power = PowerCore.tag(data).getBooleanOr("bone_shed", false) ? BONE_SHED_POWER : BONE_POWER;
		if (!DiscipleTechniques.castInnate(entity, technique, target, power)) {
			return false;
		}
		data.setQi(data.qi() - cost);
		PowerCore.setCooldown(data, "bone", now + technique.cooldownTicks());
		return true;
	}

	private Physiques() {
	}
}
