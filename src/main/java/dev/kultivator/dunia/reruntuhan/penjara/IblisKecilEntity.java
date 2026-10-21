package dev.kultivator.dunia.reruntuhan.penjara;

import dev.kultivator.dunia.reruntuhan.RCreature;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Iblis Kecil, a lesser demon sealed in the prison's cells (DESIGN 34.5.6): it rises with its king (3-6) and, while the
 * seal is broken, a few roam within 48 blocks of the shrine at night. Drawn on the player model with the module's
 * generated demon skins (kind 0-2: red, ash, jade), horns and glowing eyes. Melee and a short leap, nothing else; its
 * {@link LesserBrain} keeps it near its home.
 */
public class IblisKecilEntity extends RCreature {

	public static final int LEAP = 1;

	public IblisKecilEntity(EntityType<? extends IblisKecilEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 80.0D)
				.add(Attributes.ATTACK_DAMAGE, 9.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.31D)
				.add(Attributes.FOLLOW_RANGE, 32.0D)
				.add(Attributes.ARMOR, 8.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.kultivator.iblis_kecil");
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITHER_SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITHER_SKELETON_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F + this.random.nextFloat() * 0.2F;
	}
}
