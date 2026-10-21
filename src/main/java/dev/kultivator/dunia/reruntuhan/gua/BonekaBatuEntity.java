package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.RCreature;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Boneka Batu Penjaga, the stone guardian of a Gua Leluhur (DESIGN 34.5.5): one hitbox that the client draws as 6-12
 * block Displays (head, chest, arms, fists, legs; moss and glyph stones by tier) posed through {@code DisplayKey}.
 * Kind 0-2 = the ruin tier (Kecil, Agung, Kuno): the body grows and its stones change. Skills (slam, spin, pebble
 * ring) are entity effects played by {@link GuardianBrain}; nothing it does touches a block.
 */
public class BonekaBatuEntity extends RCreature {

	public static final int SLAM = 1;
	public static final int SPIN = 2;
	public static final int PEBBLES = 3;
	public static final int RISE = 4;

	public BonekaBatuEntity(EntityType<? extends BonekaBatuEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 160.0D)
				.add(Attributes.ATTACK_DAMAGE, 8.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.24D)
				.add(Attributes.FOLLOW_RANGE, 32.0D)
				.add(Attributes.ARMOR, 10.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.95D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		float s = 1.0F + 0.15F * Math.max(0, Math.min(2, kind()));
		return EntityDimensions.scalable(1.5F * s, 3.0F * s);
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.kultivator.boneka_batu.tier." + Math.max(0, Math.min(2, kind())));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.2F, 0.6F);
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
