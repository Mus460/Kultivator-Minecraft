package dev.kultivator.dunia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:dunia_anchor} (DESIGN 34.4.1, contract C34.S1): the marker a structure template carries where a
 * module wants NPCs, puzzles, seals, gates or a treasure. It is never sent to clients (client tracking range 0), has no
 * physics, no gravity, takes no damage, does nothing in {@link #tick()} and is saved with its chunk, so it stays until its
 * module consumes it. Players cannot summon it.
 * <p>
 * NBT: {@code kind} ({@code "<module>/<what>"}, e.g. {@code kota/lelang}), {@code data} (the template's compound,
 * read-only by convention), {@code state} (owned by the module, persisted: change it through {@link #state()} and the
 * chunk saves it), {@code site} (the site id, see {@link #site()}), {@code origin} (the packed block position of the
 * structure start, when the anchor sits in a {@code kultivator:*} structure) and {@code structure} (that structure's id).
 * <p>
 * Anchor data keys every module may use (all optional): {@code protect} (int, blocks of Mode Kehancuran protection
 * around the anchor, default 8, 0 = none; a town's footprint anchor publishes 72), {@code situs} (string: for a
 * {@code <module>/situs} anchor, the site kind it publishes, default {@code <module>/<module>}), {@code tier} (int, the
 * site's tier, default 1).
 */
public class DuniaAnchorEntity extends Entity {

	public static final int DEFAULT_PROTECT = 8;

	private String kind = "";
	private CompoundTag data = new CompoundTag();
	private CompoundTag state = new CompoundTag();
	private long site;
	private long origin = Long.MIN_VALUE;
	private String structure = "";
	/** Server-only bookkeeping of the spine (not saved): handler told loaded, last nearby pass. */
	boolean announced;

	public DuniaAnchorEntity(EntityType<? extends DuniaAnchorEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
	}

	@Override
	public void tick() {
		// Nothing: the spine walks loaded anchors itself (DuniaAnchors), at most 64 per server tick.
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.kind = input.getStringOr("kind", "");
		this.data = input.read("data", CompoundTag.CODEC).orElseGet(CompoundTag::new);
		this.state = input.read("state", CompoundTag.CODEC).orElseGet(CompoundTag::new);
		this.site = input.getLongOr("site", 0L);
		this.origin = input.getLongOr("origin", Long.MIN_VALUE);
		this.structure = input.getStringOr("structure", "");
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("kind", kind);
		output.store("data", CompoundTag.CODEC, data);
		output.store("state", CompoundTag.CODEC, state);
		output.putLong("site", site);
		if (origin != Long.MIN_VALUE) {
			output.putLong("origin", origin);
		}
		if (!structure.isEmpty()) {
			output.putString("structure", structure);
		}
	}

	// ================================================================== API

	/** {@code "<module>/<what>"}. */
	public String kind() {
		return kind;
	}

	/** The part of {@link #kind()} before the slash ("kota"). */
	public String module() {
		int slash = kind.indexOf('/');
		return slash < 0 ? kind : kind.substring(0, slash);
	}

	/** The part of {@link #kind()} after the slash ("lelang"). */
	public String what() {
		int slash = kind.indexOf('/');
		return slash < 0 ? "" : kind.substring(slash + 1);
	}

	/** The template's data (do not change it). */
	public CompoundTag data() {
		return data;
	}

	/** The module's persisted state (change it in place; the chunk saves it). */
	public CompoundTag state() {
		return state;
	}

	/**
	 * The site id: {@code hash(dimension, structure start or own block position, kind)}, the same for every load of this
	 * anchor. Two anchors of one structure share {@link #origin()} but differ by kind.
	 */
	public long site() {
		return site;
	}

	/** The site id of the structure this anchor belongs to, for {@code kind} (what a {@code situs} anchor publishes). */
	public long siteOf(String siteKind) {
		BlockPos at = origin() != null ? origin() : this.blockPosition();
		return DuniaSites.idOf(this.level().dimension(), at, siteKind);
	}

	/** The block position of the structure start (the same for all anchors of one town or ruin), or null outside one. */
	public @Nullable BlockPos origin() {
		return origin == Long.MIN_VALUE ? null : BlockPos.of(origin);
	}

	/** The structure id ("kultivator:kota/kota_kultivator"), or "" when unknown. */
	public String structure() {
		return structure;
	}

	/** Mode Kehancuran protection radius around this anchor (data {@code protect}, default 8). */
	public int protectRadius() {
		return Math.max(0, Math.min(128, data.getIntOr("protect", DEFAULT_PROTECT)));
	}

	/** For tests and modules that place anchors on their own entities' behalf (never in a template): sets the identity. */
	public void setup(String kind, CompoundTag data) {
		this.kind = kind == null ? "" : kind;
		this.data = data == null ? new CompoundTag() : data.copy();
	}

	/** On the first load: finds the structure start around the anchor and fixes {@link #site()} (spine only). */
	void resolve(ServerLevel level) {
		if (site != 0L) {
			return;
		}
		try {
			StructureStart start = level.structureManager().getStructureWithPieceAt(this.blockPosition(),
					(Holder<Structure> holder) -> holder.is(key -> "kultivator".equals(key.identifier().getNamespace())));
			if (start.isValid()) {
				BlockPos center = start.getBoundingBox().getCenter();
				origin = new BlockPos(start.getChunkPos().getMiddleBlockX(), center.getY(), start.getChunkPos().getMiddleBlockZ()).asLong();
				structure = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
						.getResourceKey(start.getStructure()).map(key -> key.identifier().toString()).orElse("");
			}
		} catch (RuntimeException ignored) {
			// no structure data: the anchor's own position names the site
		}
		BlockPos at = origin() != null ? origin() : this.blockPosition();
		site = DuniaSites.idOf(level.dimension(), at, kind);
		if (site == 0L) {
			site = 1L;
		}
	}

	// ================================================================== inert

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
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
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	protected boolean couldAcceptPassenger() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return !this.isRemoved() && !kind.isEmpty() && super.shouldBeSaved();
	}
}
