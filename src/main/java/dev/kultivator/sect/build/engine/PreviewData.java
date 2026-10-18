package dev.kultivator.sect.build.engine;

import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.blueprint.Glaze;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/**
 * One compiled footprint preview (DESIGN 21.12): the ticket, whose it is, template, rotation and glaze, and the
 * {@code BuildPreview} payload tag with the keys of DESIGN 21.24 ({@code t, rot, glaze, fp, axis, comps, zones, cut, obst,
 * warn, snap, bill, exact, ops}). Spine stub owned by the engine module (P6b-cetak); the public methods here are the
 * contract the flow module uses.
 */
public final class PreviewData {

	/** {@code warn[].sev}: a note, an amber warning, a red refusal. */
	public static final int SEV_NOTE = 0;
	public static final int SEV_AMBER = 1;
	public static final int SEV_RED = 2;

	private final long ticket;
	private final UUID owner;
	private final int template;
	private final int rotation;
	private final Glaze glaze;
	private final CompoundTag tag;

	public PreviewData(long ticket, UUID owner, int template, int rotation, Glaze glaze, CompoundTag tag) {
		this.ticket = ticket;
		this.owner = owner;
		this.template = template;
		this.rotation = rotation;
		this.glaze = glaze;
		this.tag = tag == null ? new CompoundTag() : tag;
	}

	public long ticket() {
		return ticket;
	}

	public UUID owner() {
		return owner;
	}

	public int template() {
		return template;
	}

	public int rotation() {
		return rotation;
	}

	public Glaze glaze() {
		return glaze;
	}

	/** The {@code BuildPreview} payload tag (a copy). */
	public CompoundTag toTag() {
		return tag.copy();
	}

	/** How much of the chosen rotation's columns are snapshotted, 0-100 ({@code snap}). */
	public int snapshotPercent() {
		return tag.getIntOr("snap", 0);
	}

	/** True when a red warning refuses the confirmation. */
	public boolean hasRed() {
		for (Tag each : tag.getListOrEmpty("warn")) {
			if (each instanceof CompoundTag warning && warning.getIntOr("sev", SEV_NOTE) >= SEV_RED) {
				return true;
			}
		}
		return false;
	}

	/** Nothing red and the snapshot is complete. */
	public boolean confirmable() {
		return snapshotPercent() >= 100 && !hasRed();
	}

	/** The whole template's bill as previewed ({@code bill} / {@code exact}). */
	public Bill bill() {
		return Bill.readFrom(tag);
	}
}
