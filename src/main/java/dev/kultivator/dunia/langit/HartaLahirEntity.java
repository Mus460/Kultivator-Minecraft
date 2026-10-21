package dev.kultivator.dunia.langit;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Harta Lahir (DESIGN 34.5.9): the born treasure at the foot of the rainbow pillar, drawn by the client as the treasure's
 * item turning inside a jade lotus. It exists only while a player is within 64 blocks (spawned and removed by
 * {@link TreasureBirths}), has no collision, is never hurt and never saved. Right-click starts the 5-second claim channel;
 * the channel's progress and the channelling player are synced so the lotus and the HUD can show them.
 */
public class HartaLahirEntity extends Entity {

	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(HartaLahirEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(HartaLahirEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CHANNEL = SynchedEntityData.defineId(HartaLahirEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CHANNELER = SynchedEntityData.defineId(HartaLahirEntity.class, EntityDataSerializers.INT);

	/** The birth this treasure belongs to (0 = none: discarded on its next tick). */
	private long birthId;

	public HartaLahirEntity(EntityType<? extends HartaLahirEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_ITEM, ItemStack.EMPTY);
		entityData.define(DATA_TIER, 1);
		entityData.define(DATA_CHANNEL, 0);
		entityData.define(DATA_CHANNELER, -1);
	}

	void setup(long birth, ItemStack display, int tier) {
		this.birthId = birth;
		entityData.set(DATA_ITEM, display.copyWithCount(1));
		entityData.set(DATA_TIER, Math.max(1, Math.min(4, tier)));
	}

	public long birthId() {
		return birthId;
	}

	/** The treasure shown inside the lotus. */
	public ItemStack display() {
		return entityData.get(DATA_ITEM);
	}

	/** Tier 1-4 (lotus colour and glow). */
	public int tier() {
		return entityData.get(DATA_TIER);
	}

	/** Channel progress 0-100 (0 = nobody channels). */
	public int channel() {
		return entityData.get(DATA_CHANNEL);
	}

	/** Entity id of the channelling player, -1 for none. */
	public int channeler() {
		return entityData.get(DATA_CHANNELER);
	}

	void setChannel(int progress, int playerId) {
		entityData.set(DATA_CHANNEL, Math.max(0, Math.min(100, progress)));
		entityData.set(DATA_CHANNELER, playerId);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel serverLevel && tickCount % 20 == 0 && !TreasureBirths.owns(serverLevel, this)) {
			discard();
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && level() instanceof ServerLevel serverLevel) {
			TreasureBirths.startChannel(serverLevel, serverPlayer, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean skipAttackInteraction(Entity source) {
		return true;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0D * 96.0D;
	}
}
