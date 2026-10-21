package dev.kultivator.dunia.dimensi;

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
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * {@code kultivator:tanda_dimensi}: the visible, clickable mark an anchor of module D stands up while a player is near
 * (DESIGN 34.0 W2: gates, books and whirlpools are entities, never blocks). The anchor itself is never sent to clients,
 * so the client draws this instead ({@code TandaRenderer}: additive quads, no texture, budgeted). Never saved (the type
 * is {@code noSave}); its anchor re-makes it on load and removes it on unload. No physics, no gravity, no damage.
 * <p>
 * Kinds: {@link #PUSARAN} the sea-gate whirlpool on the water, {@link #GERBANG} a return gate (Jalan Pulang, Gerbang
 * Pulang), {@link #TERAS} the arrival ring of Teras Wangxiang, {@link #BUKU} the Book of Life and Death, {@link #ALTAR}
 * the incense altar of a Kuil Gerbang Hantu, {@link #HERBA} a harvestable herb bed, {@link #MATA_AIR} the Penglai spring,
 * {@link #DIZANG} Dizang's shrine, {@link #CERMIN} the Mirror of Retribution.
 */
public class TandaEntity extends Entity {

	public static final int PUSARAN = 0;
	public static final int GERBANG = 1;
	public static final int TERAS = 2;
	public static final int BUKU = 3;
	public static final int ALTAR = 4;
	public static final int HERBA = 5;
	public static final int MATA_AIR = 6;
	public static final int DIZANG = 7;
	public static final int CERMIN = 8;
	public static final int KINDS = 9;

	/** Tag of every mark (the load refusal and the cleanup). */
	public static final String TAG = "kultivator.tanda_dimensi";

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(TandaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(TandaEntity.class, EntityDataSerializers.INT);

	/** The anchor that made this mark (server only). */
	private @Nullable UUID anchor;
	private int lonely;

	public TandaEntity(EntityType<? extends TandaEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.addTag(TAG);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_KIND, GERBANG);
		entityData.define(DATA_STATE, 0);
	}

	public int kind() {
		return Math.floorMod(entityData.get(DATA_KIND), KINDS);
	}

	public void setKind(int kind) {
		entityData.set(DATA_KIND, Math.floorMod(kind, KINDS));
	}

	/** A small per-kind state (a spent herb bed, a closed gate) the renderer dims. */
	public int state() {
		return entityData.get(DATA_STATE);
	}

	public void setState(int state) {
		if (entityData.get(DATA_STATE) != state) {
			entityData.set(DATA_STATE, state);
		}
	}

	public @Nullable UUID anchor() {
		return anchor;
	}

	public void setAnchor(@Nullable UUID anchor) {
		this.anchor = anchor;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setKind(input.getIntOr("kind", GERBANG));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("kind", kind());
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || this.tickCount % 40 != 0) {
			return;
		}
		// A mark whose anchor went away (or that outlived every player) removes itself.
		if (level.getNearestPlayer(this, 96.0D) == null) {
			if (++lonely >= 15) {
				this.discard();
			}
		} else {
			lonely = 0;
		}
	}

	@Override
	public boolean isPickable() {
		return kind() != PUSARAN && kind() != TERAS;
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
			Dimensi.useTanda(player, this);
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
			Dimensi.useTanda(serverPlayer, this);
		}
		return InteractionResult.SUCCESS;
	}
}
