package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
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
 * The live side of one 38GU anchor (DESIGN 38.C.4, 38.C.5): what exists while a player is near a cave room or a site.
 * Made when the spine reports a player near the anchor ({@code AnchorHandler.nearby}, every 20 ticks), ticked every
 * server tick by {@link GuaRuns}, taken down (every entity it made discarded) when nobody came near for
 * {@link #IDLE_TICKS} or the anchor unloads. Persistent facts live in the anchor's {@code state()} or in
 * {@link GuaData}. Template offsets are turned with the module R {@link Frame} (the same rotation as the blocks).
 * Nothing here ever places or breaks a block (38.0 rule 1).
 */
public abstract class GuaRun {

	/** Taken down after this long without a player near (the spine calls {@code nearby} every 20 ticks). */
	public static final int IDLE_TICKS = 120;

	public final DuniaAnchorEntity anchor;
	public final ServerLevel level;
	public final Frame frame;
	long lastSeen;
	private boolean spawned;
	protected final List<Entity> owned = new ArrayList<>();

	protected GuaRun(DuniaAnchorEntity anchor, ServerLevel level) {
		this.anchor = anchor;
		this.level = level;
		this.frame = Frame.of(anchor);
	}

	/** The anchor's template data (read only). */
	public CompoundTag data() {
		return anchor.data();
	}

	/** The anchor's persisted state (saved with its chunk). */
	public CompoundTag state() {
		return anchor.state();
	}

	public long now() {
		return level.getGameTime();
	}

	/** The world day (24 000 ticks of the overworld clock). */
	public long today() {
		return GuaAnchors.today(level);
	}

	public Vec3 centre() {
		return anchor.position();
	}

	/** The site id of the structure this anchor belongs to (the id its {@code situs} anchor published). */
	public long situs() {
		return anchor.siteOf(GuaAnchors.situsOf(anchor));
	}

	/** Spawns what the run shows (called once, on the first tick with a player near). */
	protected abstract void spawn();

	/** Every server tick while alive. */
	protected abstract void tick(long now);

	/** Every 20 ticks with the players the spine found near the anchor (nearest first). */
	protected void nearby(List<ServerPlayer> within) {
	}

	/** The radius this run counts as "at the place" (factor checks, {@link #inside}). */
	public double radius() {
		return 16.0D;
	}

	/** True when {@code player} stands at this run's place. */
	public boolean inside(ServerPlayer player) {
		return player.level() == level && player.position().distanceToSqr(centre()) <= radius() * radius();
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

	/** Creates an entity of {@code type} at {@code at} (tagged {@link NoGrief#TAG}); null on failure. Not added yet. */
	public <T extends Entity> @Nullable T make(EntityType<? extends T> type, Vec3 at, float yaw) {
		T entity = type.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		entity.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		entity.setYHeadRot(yaw);
		entity.addTag(NoGrief.TAG);
		return entity;
	}

	/** Adds a prepared entity to the level and remembers it; false when the level refused it. */
	public boolean add(@Nullable Entity entity) {
		if (entity == null || !level.addFreshEntity(entity)) {
			return false;
		}
		owned.add(entity);
		return true;
	}

	/** Remembers an entity made elsewhere (a body from {@code HidupApi.tubuh}, a rogue band member). */
	public void own(@Nullable Entity entity) {
		if (entity != null && !owned.contains(entity)) {
			owned.add(entity);
		}
	}

	/** A template block offset ({@code x, y, z} from the anchor's block, template frame) in the world. */
	public BlockPos block(int dx, int dy, int dz) {
		return frame.block(dx, dy, dz);
	}

	/** The bottom centre of {@link #block}. */
	public Vec3 spot(int dx, int dy, int dz) {
		return frame.bottom(dx, dy, dz);
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

	boolean started() {
		return spawned;
	}
}
