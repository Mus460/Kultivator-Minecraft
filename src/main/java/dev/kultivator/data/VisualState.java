package dev.kultivator.data;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What every client needs to draw a cultivator (aura, pose, robe, back sword, eyes, Dao circle, flying sword). Synced
 * to everyone through the {@code kultivator:visual} attachment; recomputed by {@code Cultivators.refreshVisual}.
 * Immutable: a new instance is set whenever something changes, which triggers the sync.
 *
 * @param rootElements the spiritual root's element mask ({@code Element} bits, 0 when untested); used by the Aura
 *                     Kultivasi (DESIGN 31.3) for the Dao Union runes and the True Immortal star wheel
 * @param swordItem    registry id of the sword drawn on the back / under the feet, "" for none
 */
public record VisualState(int realm, int layer, int stage, int dao, int rootColor, int rootElements, int flags,
		String swordItem) {

	public static final int MEDITATING = 1;
	public static final int FLYING_SWORD = 1 << 1;
	public static final int DOMAIN = 1 << 2;
	public static final int DHARMA = 1 << 3;
	public static final int TRIBULATION = 1 << 4;
	public static final int DEVIATION = 1 << 5;
	public static final int ASCENDED = 1 << 6;
	public static final int SHOW_AURA = 1 << 7;
	public static final int SHOW_ROBE = 1 << 8;
	public static final int SHOW_BACK_SWORD = 1 << 9;
	public static final int SHOW_EYES = 1 << 10;
	public static final int SHOW_DAO_CIRCLE = 1 << 11;
	public static final int ENLIGHTENED = 1 << 12;
	public static final int BREAKING_THROUGH = 1 << 13;
	/** The root is tested and of the Heavenly grade: the Aura Kultivasi gains its gilded accent (DESIGN 31.3). */
	public static final int HEAVENLY_ROOT = 1 << 14;
	/** At the bottleneck with a full bar (and not the maximum rank): the aura's amber warning pulse (DESIGN 31.3). */
	public static final int BOTTLENECK_READY = 1 << 15;

	public static final VisualState NONE = new VisualState(0, 0, 0, -1, 0xCFE8FF, 0, "");

	/** The constructor of before DESIGN 31 (no root element mask). */
	public VisualState(int realm, int layer, int stage, int dao, int rootColor, int flags, String swordItem) {
		this(realm, layer, stage, dao, rootColor, 0, flags, swordItem);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, VisualState> STREAM_CODEC = StreamCodec.of(
			(buf, v) -> {
				buf.writeVarInt(v.realm);
				buf.writeVarInt(v.layer);
				buf.writeVarInt(v.stage);
				buf.writeVarInt(v.dao + 1);
				buf.writeInt(v.rootColor);
				buf.writeVarInt(v.rootElements);
				buf.writeVarInt(v.flags);
				buf.writeUtf(v.swordItem, 128);
			},
			buf -> new VisualState(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1, buf.readInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readUtf(128)));

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public Realm realmEnum() {
		return Realm.byOrdinal(realm);
	}

	public Rank rank() {
		return new Rank(realmEnum(), layer, stage);
	}

	public Dao daoEnum() {
		return Dao.byOrdinal(dao);
	}

	/** Linear 0..1 measure of power for scaling effects (0 Mortal, 1 True Immortal Peak). */
	public float power() {
		return (float) rank().step() / Rank.MAX_STEP;
	}
}
