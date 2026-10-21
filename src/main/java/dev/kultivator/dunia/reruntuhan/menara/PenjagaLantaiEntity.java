package dev.kultivator.dunia.reruntuhan.menara;

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
 * A Penjaga Lantai, the guardian of a band of ten floors of the Menara Seratus Lantai (DESIGN 34.5.7): kind 0-8 =
 * Lembu Besi, Ular Giok, Bangau Api, Kura-kura Batu, Harimau Petir, Rubah Ilusi, Kilin Emas, Bangau Abadi, Naga
 * Bayangan (floor 100's Bayangan Diri is the mirror shadow at full numbers). Drawn from cuboid item models by the client,
 * its skills played by {@link FloorBrain} as entity effects; a boss for the technique rules.
 */
public class PenjagaLantaiEntity extends RCreature {

	public static final String[] IDS = {"lembu_besi", "ular_giok", "bangau_api", "kura_batu", "harimau_petir", "rubah_ilusi",
			"kilin_emas", "bangau_abadi", "naga_bayangan"};
	public static final int KINDS = IDS.length;

	public PenjagaLantaiEntity(EntityType<? extends PenjagaLantaiEntity> type, Level level) {
		super(type, level);
		markBoss();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 200.0D)
				.add(Attributes.ATTACK_DAMAGE, 10.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.3D)
				.add(Attributes.FLYING_SPEED, 0.4D)
				.add(Attributes.FOLLOW_RANGE, 48.0D)
				.add(Attributes.ARMOR, 8.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		return switch (kind()) {
			case 0 -> EntityDimensions.scalable(2.4F, 2.6F);   // iron ox
			case 1 -> EntityDimensions.scalable(2.0F, 1.6F);   // jade serpent (the coils)
			case 2, 7 -> EntityDimensions.scalable(1.4F, 2.8F); // cranes
			case 3 -> EntityDimensions.scalable(3.0F, 1.8F);   // stone turtle
			case 4 -> EntityDimensions.scalable(1.8F, 1.8F);   // thunder tiger
			case 5 -> EntityDimensions.scalable(1.2F, 1.4F);   // illusion fox
			case 6 -> EntityDimensions.scalable(2.0F, 2.6F);   // golden qilin
			default -> EntityDimensions.scalable(2.4F, 2.4F);  // shadow dragon (head and coils)
		};
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.kultivator.penjaga_lantai." + IDS[Math.max(0, Math.min(KINDS - 1, kind()))]);
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
			case 0 -> SoundEvents.RAVAGER_AMBIENT;
			case 1, 8 -> SoundEvents.ENDER_DRAGON_GROWL;
			case 2, 7 -> SoundEvents.PARROT_AMBIENT;
			case 3 -> SoundEvents.TURTLE_AMBIENT_LAND;
			case 4 -> SoundEvents.POLAR_BEAR_WARNING;
			case 5 -> SoundEvents.FOX_SCREECH;
			default -> SoundEvents.HOGLIN_AMBIENT;
		};
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return switch (kind()) {
			case 0 -> SoundEvents.RAVAGER_HURT;
			case 3 -> SoundEvents.TURTLE_HURT;
			case 4 -> SoundEvents.POLAR_BEAR_HURT;
			case 5 -> SoundEvents.FOX_HURT;
			case 6 -> SoundEvents.HOGLIN_HURT;
			default -> SoundEvents.PHANTOM_HURT;
		};
	}

	@Override
	protected SoundEvent getDeathSound() {
		return switch (kind()) {
			case 0 -> SoundEvents.RAVAGER_DEATH;
			case 3 -> SoundEvents.TURTLE_DEATH;
			case 4 -> SoundEvents.POLAR_BEAR_DEATH;
			case 5 -> SoundEvents.FOX_DEATH;
			case 6 -> SoundEvents.HOGLIN_DEATH;
			default -> SoundEvents.PHANTOM_DEATH;
		};
	}

	@Override
	protected float getSoundVolume() {
		return 1.6F;
	}
}
