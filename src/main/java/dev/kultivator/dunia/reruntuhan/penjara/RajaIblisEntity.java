package dev.kultivator.dunia.reruntuhan.penjara;

import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * A demon king of the Penjara Iblis Kuno (DESIGN 34.5.6), drawn by the client from cuboid item models (several parts,
 * posed per frame): kind {@link #XIANGLIU} (the nine-headed minister of Gonggong, Shan Hai Jing 8 and 17),
 * {@link #BANTENG} (the Bull Demon King, Journey to the West 59-61) or {@link #CHIYOU} (who warred with the Yellow
 * Emperor, Shan Hai Jing 17). Tags {@code kultivator.boss} and {@code kultivator.abadi_foe}; its intake is clamped per
 * second; every skill is an entity effect telegraphed on the ground ({@link KingBrain}).
 */
public class RajaIblisEntity extends RCreature {

	public static final int XIANGLIU = 0;
	public static final int BANTENG = 1;
	public static final int CHIYOU = 2;
	public static final String[] IDS = {"xiangliu", "banteng", "chiyou"};

	public RajaIblisEntity(EntityType<? extends RajaIblisEntity> type, Level level) {
		super(type, level);
		markBoss();
		this.addTag(TechniquePower.TAG_ABADI_FOE);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 2000.0D)
				.add(Attributes.ATTACK_DAMAGE, 18.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.26D)
				.add(Attributes.FOLLOW_RANGE, 48.0D)
				.add(Attributes.ARMOR, 16.0D)
				.add(Attributes.ARMOR_TOUGHNESS, 8.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
				.add(Attributes.STEP_HEIGHT, 1.5D);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return switch (kind()) {
			case XIANGLIU -> EntityDimensions.scalable(4.0F, 4.6F);
			case BANTENG -> EntityDimensions.scalable(2.2F, 4.4F);
			default -> EntityDimensions.scalable(2.0F, 4.0F);
		};
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.kultivator.raja_iblis." + IDS[Math.max(0, Math.min(2, kind()))]);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return switch (kind()) {
			case XIANGLIU -> SoundEvents.ENDER_DRAGON_GROWL;
			case BANTENG -> SoundEvents.RAVAGER_AMBIENT;
			default -> SoundEvents.WITHER_AMBIENT;
		};
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return kind() == BANTENG ? SoundEvents.RAVAGER_HURT : SoundEvents.ENDER_DRAGON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return kind() == BANTENG ? SoundEvents.RAVAGER_DEATH : SoundEvents.WITHER_SKELETON_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.5F;
	}
}
