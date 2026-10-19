package dev.kultivator.alam;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A relic of a secret realm chamber (DESIGN 25.3.6): the glyph pad of the Formasi Teleportasi, a maze exit (true or
 * phantom, they look the same), a stone gate or an ice crystal. A plain invisible entity: no gravity, no physics, never
 * hurt, never saved ({@code noSave} plus the {@code ALLOW_LOAD} refusal of {@code RealmGuard}). The client draws it with
 * Display scenery ({@code client.alam.RealmScenery}); only an unopened stone gate can be clicked. Touches no block.
 */
public class RealmRelicEntity extends Entity {

	public static final int KIND_PAD = 0;
	public static final int KIND_EXIT = 1;
	public static final int KIND_GATE = 2;
	public static final int KIND_CRYSTAL = 3;

	/** Tag every relic carries (tracking and the load refusal). */
	public static final String TAG = "kultivator.realm_relic";

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(RealmRelicEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(RealmRelicEntity.class, EntityDataSerializers.INT);

	public RealmRelicEntity(EntityType<? extends RealmRelicEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_KIND, KIND_PAD);
		entityData.define(DATA_STATE, 0);
	}

	/** One of the {@code KIND_*} constants. */
	public int kind() {
		return entityData.get(DATA_KIND);
	}

	public void setKind(int kind) {
		entityData.set(DATA_KIND, kind);
	}

	/** 0 closed / idle; 1 opened (gates) or absorbed (crystals); the crystal channel uses 2-100 as its progress. */
	public int state() {
		return entityData.get(DATA_STATE);
	}

	public void setState(int state) {
		entityData.set(DATA_STATE, state);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setKind(input.getIntOr("kind", KIND_PAD));
		setState(input.getIntOr("state", 0));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("kind", kind());
		output.putInt("state", state());
	}

	@Override
	public void tick() {
	}

	@Override
	public boolean isPickable() {
		return kind() == KIND_GATE && state() == 0;
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
	public boolean skipAttackInteraction(Entity source) {
		if (source instanceof ServerPlayer player && isPickable()) {
			RealmRun.relicUsed(player, this);
		}
		return true;
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!isPickable()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
			RealmRun.relicUsed(serverPlayer, this);
		}
		return InteractionResult.SUCCESS;
	}
}
