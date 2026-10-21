package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.dimensi.DimensiEntities;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code kultivator:penyu_ao}, Pulau Penyu Ao (DESIGN 34.5.14; Liezi, Tang Wen: the fifteen giant turtles, ao, that carry
 * the isles of the immortals on their backs): a giant turtle carrying a small island, drifting slowly over the sea. One
 * per dimension near players ({@code SeaMonsters}). The island is drawn by the client renderer from block models (60 /
 * 150 / 300 blocks by Effect Quality); nothing in the world changes.
 * <p>
 * Players stand on its back on four deck colliders ({@link PenyuDekEntity}, the {@code HappyGhast} rule: collidable only
 * from above); like the happy ghast it stops drifting while someone stands on it, so a standing player never slides.
 * Those on its back receive the turtle's blessing (Conduit Power, Regeneration). It cannot be hurt, is never saved
 * and leaves when no player has been within 192 b for a minute.
 */
public class PenyuAoEntity extends Entity {

	public static final String TAG = "kultivator.penyu_ao";
	/** The shell top above the entity's feet (where the deck colliders' tops are). */
	public static final double DECK = 3.4D;
	public static final double SPEED = 0.04D;
	private static final double[][] DECK_OFFSETS = {{-2.5D, -2.5D}, {2.5D, -2.5D}, {-2.5D, 2.5D}, {2.5D, 2.5D}};

	private static final EntityDataAccessor<Boolean> DATA_RESTING = SynchedEntityData.defineId(PenyuAoEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(PenyuAoEntity.class, EntityDataSerializers.INT);

	private final List<PenyuDekEntity> decks = new ArrayList<>();
	private float heading;
	private int lonely;
	private int turnCooldown;

	public PenyuAoEntity(EntityType<? extends PenyuAoEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.addTag(TAG);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_RESTING, false);
		builder.define(DATA_SEED, 0);
	}

	public void setup(int seed, float yaw) {
		this.entityData.set(DATA_SEED, seed);
		this.heading = yaw;
		this.setYRot(yaw);
	}

	public boolean resting() {
		return this.entityData.get(DATA_RESTING);
	}

	public int seed() {
		return this.entityData.get(DATA_SEED);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (decks.isEmpty() && this.tickCount > 1) {
			spawnDecks(level);
		}
		List<Player> aboard = aboard(level);
		boolean rest = !aboard.isEmpty();
		if (rest != resting()) {
			this.entityData.set(DATA_RESTING, rest);
		}
		if (!rest) {
			drift(level);
		}
		placeDecks();
		if (this.tickCount % 40 == 0) {
			for (Player player : aboard) {
				player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 220, 0, true, true), this);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, true), this);
				if (player instanceof ServerPlayer serverPlayer) {
					SeaPeople.onTurtle(serverPlayer);
				}
			}
			if (level.getNearestPlayer(this, 192.0D) == null) {
				if (++lonely >= 30) {
					removeAll();
				}
			} else {
				lonely = 0;
			}
		}
	}

	private void drift(ServerLevel level) {
		if (turnCooldown > 0) {
			turnCooldown--;
		}
		heading += (this.random.nextFloat() - 0.5F) * 0.6F;
		double rad = Math.toRadians(heading);
		Vec3 step = new Vec3(-Math.sin(rad) * SPEED, 0.0D, Math.cos(rad) * SPEED);
		// turn away before an island: test the column 9 blocks ahead at the waterline
		Vec3 ahead = this.position().add(step.normalize().scale(9.0D));
		BlockPos probe = BlockPos.containing(ahead.x, DimensiLevels.LAUT_SEA_TOP + 1, ahead.z);
		if (turnCooldown == 0 && (!level.getBlockState(probe).isAir() || !level.getBlockState(probe.above()).isAir())) {
			heading += 90.0F + this.random.nextFloat() * 90.0F;
			turnCooldown = 100;
			return;
		}
		double y = DimensiLevels.isLaut(level) ? DimensiLevels.LAUT_SEA_TOP + 0.4D : this.getY();
		this.setPos(this.getX() + step.x, y, this.getZ() + step.z);
		this.setYRot(Mth.rotLerp(0.05F, this.getYRot(), heading));
	}

	private List<Player> aboard(ServerLevel level) {
		AABB deck = new AABB(this.getX() - 6.0D, this.getY() + DECK - 0.5D, this.getZ() - 6.0D,
				this.getX() + 6.0D, this.getY() + DECK + 3.0D, this.getZ() + 6.0D);
		return level.getEntitiesOfClass(Player.class, deck, player -> player.isAlive() && !player.isSpectator());
	}

	private void spawnDecks(ServerLevel level) {
		for (double[] offset : DECK_OFFSETS) {
			PenyuDekEntity deck = DimensiEntities.PENYU_DEK.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (deck == null) {
				continue;
			}
			deck.setOwner(this.getId());
			deck.snapTo(this.getX() + offset[0], this.getY() + DECK - PenyuDekEntity.HEIGHT, this.getZ() + offset[1]);
			if (level.addFreshEntity(deck)) {
				decks.add(deck);
				DuniaBudget.track(DuniaBudget.PART, deck);
			}
		}
	}

	private void placeDecks() {
		double rad = Math.toRadians(this.getYRot());
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		for (int i = 0; i < decks.size() && i < DECK_OFFSETS.length; i++) {
			PenyuDekEntity deck = decks.get(i);
			if (deck.isRemoved()) {
				continue;
			}
			double ox = DECK_OFFSETS[i][0];
			double oz = DECK_OFFSETS[i][1];
			double x = this.getX() + ox * cos - oz * sin;
			double z = this.getZ() + ox * sin + oz * cos;
			double y = this.getY() + DECK - PenyuDekEntity.HEIGHT;
			if (deck.getX() != x || deck.getY() != y || deck.getZ() != z) {
				deck.setPos(x, y, z);
			}
		}
	}

	/** Removes the turtle and its decks. */
	public void removeAll() {
		for (PenyuDekEntity deck : decks) {
			deck.discard();
		}
		decks.clear();
		this.discard();
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		for (PenyuDekEntity deck : decks) {
			if (!deck.isRemoved()) {
				deck.discard();
			}
		}
		super.remove(reason);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}
}
