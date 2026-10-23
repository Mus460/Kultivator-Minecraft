package dev.kultivator.sect.build.api;

import dev.kultivator.sect.build.blueprint.Glaze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A confirmed construction site as the flow and the UI see it (DESIGN 21.20 {@code build_site}): whose it is, where, which
 * template, rotation and glaze, the footprint box (world coordinates, the whole height envelope), the saved state, why it
 * is or is not building right now ({@code status}), the completed tier, the open component and the current rate.
 */
public record SiteView(UUID owner, ResourceKey<Level> dim, BlockPos stele, int template, int rotation, Glaze glaze, int plazaY,
		BoundingBox footprint, int state, int status, int tier, int blocked, @Nullable String openId, double rate, long bank, boolean test,
		boolean sky, boolean tethered, int skyFloorY) {

	/** A ground site (the shape every site had before Sekte Langit). */
	public SiteView(UUID owner, ResourceKey<Level> dim, BlockPos stele, int template, int rotation, Glaze glaze, int plazaY,
			BoundingBox footprint, int state, int status, int tier, int blocked, @Nullable String openId, double rate, long bank, boolean test) {
		this(owner, dim, stele, template, rotation, glaze, plazaY, footprint, state, status, tier, blocked, openId, rate, bank, test, false, false,
				footprint.minY());
	}

	// ---- state (saved, DESIGN 21.20)
	public static final int STATE_NONE = 0;
	public static final int STATE_ACTIVE = 1;
	public static final int STATE_PAUSED = 2;
	public static final int STATE_DECONSTRUCTING = 3;
	public static final int STATE_RELOCATING = 4;
	public static final int STATE_UPGRADING = 5;

	// ---- status (live, the Bangun tablet's status line, DESIGN 21.21)
	/** Building now ("Berjalan"). */
	public static final int STATUS_RUNNING = 0;
	/** The next component's bill is not covered ("Menunggu bahan"). */
	public static final int STATUS_WAITING_FUNDS = 1;
	/** No owner or sect mate within range ("Jeda: pemilik jauh"); banked time grows. */
	public static final int STATUS_OWNER_AWAY = 2;
	/** Protected or player-made cells block the open component ("Terhalang"). */
	public static final int STATUS_BLOCKED = 3;
	/** The target chunk is not loaded ("Menunggu: belum dimuat"). */
	public static final int STATUS_UNLOADED = 4;
	/** Average tick time above {@code sectBuildMsptPause} ("Jeda: server sibuk"). */
	public static final int STATUS_SERVER_BUSY = 5;
	/** Paused by the owner ("Jeda"). */
	public static final int STATUS_PAUSED = 6;
	/** The stele is missing or not on its anchor cell (the hall is dormant or moved). */
	public static final int STATUS_STELE = 7;
	/** A Mode Kehancuran job is running. */
	public static final int STATUS_KEHANCURAN = 8;
	/** Every tier is complete. */
	public static final int STATUS_COMPLETE = 9;
	/** The plan is being emitted off the server thread (a moment). */
	public static final int STATUS_PREPARING = 10;
	/** Sekte Langit (DESIGN 37.3.5): a tethered sky site finished tier 1; it waits for the stele to be carried up. */
	public static final int STATUS_AWAIT_ASCENT = 11;

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("owner", owner.toString());
		tag.putString("dim", dim.identifier().toString());
		tag.putIntArray("stele", new int[]{stele.getX(), stele.getY(), stele.getZ()});
		tag.putInt("t", template);
		tag.putInt("rot", rotation);
		tag.putString("glaze", glaze.id());
		tag.putInt("plaza_y", plazaY);
		tag.putIntArray("fp", new int[]{footprint.minX(), footprint.minY(), footprint.minZ(), footprint.maxX(), footprint.maxY(), footprint.maxZ()});
		tag.putInt("state", state);
		tag.putInt("status", status);
		tag.putInt("tier", tier);
		tag.putInt("blocked", blocked);
		if (openId != null) {
			tag.putString("open", openId);
		}
		tag.putDouble("rate", rate);
		tag.putLong("bank", bank);
		tag.putBoolean("test", test);
		if (sky) {
			tag.putBoolean("sky", true);
			tag.putBoolean("teth", tethered);
			tag.putInt("floor", skyFloorY);
		}
		return tag;
	}

	/** The site in {@code tag}, or null when it is broken. */
	public static @Nullable SiteView fromTag(CompoundTag tag) {
		UUID owner;
		try {
			owner = UUID.fromString(tag.getStringOr("owner", ""));
		} catch (IllegalArgumentException exception) {
			return null;
		}
		Identifier dim = Identifier.tryParse(tag.getStringOr("dim", ""));
		int[] stele = tag.getIntArray("stele").orElse(new int[0]);
		int[] fp = tag.getIntArray("fp").orElse(new int[0]);
		if (dim == null || stele.length != 3 || fp.length != 6) {
			return null;
		}
		String open = tag.getStringOr("open", "");
		Glaze glaze = Glaze.byId(tag.getStringOr("glaze", ""));
		return new SiteView(owner, ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(stele[0], stele[1], stele[2]), tag.getIntOr("t", 0),
				tag.getIntOr("rot", 0), glaze == null ? Glaze.TINTA : glaze, tag.getIntOr("plaza_y", stele[1] - 1), new BoundingBox(fp[0], fp[1], fp[2], fp[3], fp[4], fp[5]), tag.getIntOr("state", STATE_NONE),
				tag.getIntOr("status", STATUS_RUNNING), tag.getIntOr("tier", 0), tag.getIntOr("blocked", 0), open.isEmpty() ? null : open,
				tag.getDoubleOr("rate", 0.0D), tag.getLongOr("bank", 0L), tag.getBooleanOr("test", false), tag.getBooleanOr("sky", false),
				tag.getBooleanOr("teth", false), tag.getIntOr("floor", fp[1]));
	}
}
