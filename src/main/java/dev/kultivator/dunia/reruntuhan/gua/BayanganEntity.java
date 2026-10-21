package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.RCreature;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Bayangan Cermin Hati, the mirror shadow of a player (DESIGN 34.5.5 P5 and the floor-100 Bayangan Diri of 34.5.7):
 * drawn on the disciple's player model with the mirrored player's own skin (the client looks the skin up by the synced
 * owner id), dark and see-through. Its numbers are a share of the mirrored player's ({@code 60 %} in a ruin, 100 % on
 * floor 100), set by {@link ShadowBrain}; it heals when the player heals (the mirror reflects it).
 */
public class BayanganEntity extends RCreature {

	public static final int DASH = 1;
	public static final int PALM = 2;
	public static final int BOLT = 3;

	private static final EntityDataAccessor<String> DATA_OWNER = SynchedEntityData.defineId(BayanganEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Boolean> DATA_SLIM = SynchedEntityData.defineId(BayanganEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<String> DATA_NAME = SynchedEntityData.defineId(BayanganEntity.class, EntityDataSerializers.STRING);

	public BayanganEntity(EntityType<? extends BayanganEntity> type, Level level) {
		super(type, level);
		this.setFlag(FLAG_GHOST, true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 40.0D)
				.add(Attributes.ATTACK_DAMAGE, 6.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.32D)
				.add(Attributes.FOLLOW_RANGE, 32.0D)
				.add(Attributes.ARMOR, 4.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.4D)
				.add(Attributes.STEP_HEIGHT, 1.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OWNER, "");
		builder.define(DATA_SLIM, false);
		builder.define(DATA_NAME, "");
	}

	/** Mirrors {@code owner} (its skin and name on the client). */
	public void mirror(UUID owner, String name, boolean slim) {
		entityData.set(DATA_OWNER, owner.toString());
		entityData.set(DATA_NAME, name == null ? "" : name);
		entityData.set(DATA_SLIM, slim);
	}

	public @Nullable UUID owner() {
		try {
			String id = entityData.get(DATA_OWNER);
			return id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	public String ownerName() {
		return entityData.get(DATA_NAME);
	}

	public boolean slim() {
		return entityData.get(DATA_SLIM);
	}

	@Override
	protected Component getTypeName() {
		String name = ownerName();
		return name.isEmpty() ? Component.translatable("entity.kultivator.bayangan")
				: Component.translatable("entity.kultivator.bayangan.of", name);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VEX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ILLUSIONER_MIRROR_MOVE;
	}
}
