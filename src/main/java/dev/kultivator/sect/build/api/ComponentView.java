package dev.kultivator.sect.build.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One component of a site as the flow and the UI see it (DESIGN 21.9, 21.21): its stable id, kind (a component class id
 * such as "hall", "pagoda", "pond"), tier, display name, world box, fitted floor level, state and progress in ops, the
 * protected cells blocking it, and its bill (the whole bill, or what is still reserved while it is open).
 */
public record ComponentView(String id, String kind, int tier, Component name, BoundingBox box, int y, int state, int placed, int total,
		int blocked, Bill bill) {

	/** A component of a tier after the current one (drawn dotted). */
	public static final int STATE_FUTURE = 0;
	/** Waiting in the current tier's queue. */
	public static final int STATE_QUEUED = 1;
	/** Funded and building. */
	public static final int STATE_OPEN = 2;
	public static final int STATE_DONE = 3;
	/** Waiting because protected or player-made cells are in the way. */
	public static final int STATE_BLOCKED = 4;
	/** Skipped by the owner ("Lewati bagian ini"). */
	public static final int STATE_SKIPPED = 5;
	/** Its chunk is not loaded; the reservation is kept. */
	public static final int STATE_SUSPENDED = 6;

	public ComponentView {
		id = id == null ? "" : id;
		kind = kind == null ? "" : kind;
		name = name == null ? Component.literal(id) : name;
		box = box == null ? new BoundingBox(0, 0, 0, 0, 0, 0) : box;
		bill = bill == null ? Bill.EMPTY : bill;
	}

	/** 0..1 of the ops placed (1 when done). */
	public double progress() {
		if (state == STATE_DONE) {
			return 1.0D;
		}
		return total <= 0 ? 0.0D : Math.max(0.0D, Math.min(1.0D, placed / (double) total));
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		tag.putString("kind", kind);
		tag.putInt("tier", tier);
		tag.store("name", ComponentSerialization.CODEC, name);
		tag.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
		tag.putInt("y", y);
		tag.putInt("state", state);
		tag.putInt("placed", placed);
		tag.putInt("total", total);
		tag.putInt("blocked", blocked);
		bill.writeTo(tag);
		return tag;
	}

	public static ComponentView fromTag(CompoundTag tag) {
		int[] box = tag.getIntArray("box").orElse(new int[6]);
		BoundingBox bounds = box.length == 6 ? new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]) : new BoundingBox(0, 0, 0, 0, 0, 0);
		String id = tag.getStringOr("id", "");
		return new ComponentView(id, tag.getStringOr("kind", ""), tag.getIntOr("tier", 1),
				tag.read("name", ComponentSerialization.CODEC).orElse(Component.literal(id)), bounds, tag.getIntOr("y", 0),
				tag.getIntOr("state", STATE_QUEUED), tag.getIntOr("placed", 0), tag.getIntOr("total", 0), tag.getIntOr("blocked", 0),
				Bill.readFrom(tag));
	}
}
