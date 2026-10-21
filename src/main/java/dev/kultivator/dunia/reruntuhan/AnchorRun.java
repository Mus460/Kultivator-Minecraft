package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.util.NoGrief;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The live side of one anchor of module R (a puzzle room, a gate, a guardian chamber, an inheritance hall, a prison
 * shrine or core, the tower gate or lobby): what exists while a player is near. It is made when the spine reports a
 * player near the anchor ({@code AnchorHandler.nearby}, every 20 ticks), ticks every server tick from then on, and is
 * taken down (every entity it spawned discarded) when nobody came near for {@link #IDLE_TICKS} or the anchor unloads.
 * Persistent facts live in {@link DuniaAnchorEntity#state()} only.
 */
public abstract class AnchorRun {

	/** Taken down after this long without a player near (the spine calls {@code nearby} every 20 ticks). */
	public static final int IDLE_TICKS = 120;

	public final DuniaAnchorEntity anchor;
	public final ServerLevel level;
	public final Frame frame;
	protected long lastSeen;
	protected boolean spawned;
	protected final List<Entity> owned = new ArrayList<>();

	protected AnchorRun(DuniaAnchorEntity anchor, ServerLevel level) {
		this.anchor = anchor;
		this.level = level;
		this.frame = Frame.of(anchor);
	}

	/** The anchor's template data (read only). */
	public CompoundTag data() {
		return anchor.data();
	}

	/** The anchor's persisted state. */
	public CompoundTag state() {
		return anchor.state();
	}

	public long now() {
		return level.getGameTime();
	}

	public Vec3 centre() {
		return anchor.position();
	}

	/** Spawns what the run shows (called once, on the first tick with a player near). */
	protected abstract void spawn();

	/** Every server tick while alive. */
	protected abstract void tick(long now);

	/** The radius the run counts players in (default: the anchor handler's 48). */
	public double radius() {
		return 40.0D;
	}

	/** Survival and adventure players within {@code r} of the anchor. */
	public List<ServerPlayer> playersWithin(double r) {
		return DuniaAnchors.playersNear(level, centre(), r);
	}

	/** Takes the run down: every owned entity goes. */
	protected void despawn() {
		for (Entity entity : owned) {
			if (!entity.isRemoved()) {
				entity.discard();
			}
		}
		owned.clear();
		spawned = false;
	}

	/** Creates an entity of {@code type} at {@code at} with the module's tags, adds it and remembers it; null on failure. */
	public <T extends Entity> @Nullable T make(EntityType<T> type, Vec3 at, float yaw) {
		T entity = type.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		entity.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		entity.addTag(NoGrief.TAG);
		if (entity instanceof TekaPartEntity) {
			entity.addTag(TekaPartEntity.TAG);
		}
		if (entity instanceof ColliderEntity) {
			entity.addTag(ColliderEntity.TAG);
		}
		return entity;
	}

	/** Adds a prepared entity to the level and remembers it; false when the level refused it. */
	public boolean add(Entity entity) {
		if (entity == null || !level.addFreshEntity(entity)) {
			return false;
		}
		owned.add(entity);
		return true;
	}

	/** Forgets removed entities (deaths, discards). */
	protected void prune() {
		owned.removeIf(Entity::isRemoved);
	}

	void touch(long now) {
		lastSeen = now;
	}

	boolean idle(long now) {
		return now - lastSeen > IDLE_TICKS || anchor.isRemoved() || anchor.level() != level;
	}

	void start() {
		if (!spawned) {
			spawned = true;
			spawn();
		}
	}
}
