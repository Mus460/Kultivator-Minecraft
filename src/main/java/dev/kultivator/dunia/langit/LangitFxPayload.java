package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A one-off visual of module L (DESIGN 34.5.8-34.5.10), server to client. Presentation only: the client draws it scaled by
 * Effect Quality; nothing in the world changes.
 *
 * @param kind one of the {@code KIND_*} constants
 * @param a    a small parameter (tier, strength, distance)
 * @param ids  entity ids ({@link #KIND_MARKS}: the eclipse-touched monsters near the receiver)
 */
public record LangitFxPayload(int kind, double x, double y, double z, int a, int[] ids) implements CustomPacketPayload {

	/** A meteor landed here. */
	public static final int KIND_IMPACT = 0;
	/** The treasure tremor: shake and rumble, {@code a} = distance in blocks. */
	public static final int KIND_TREMOR = 1;
	/** The receiver claimed the treasure here ({@code a} = tier): the burst and the beat-40 scene. */
	public static final int KIND_CLAIM = 2;
	/** Someone else claimed it ({@code a} = tier): the burst only. */
	public static final int KIND_CLAIM_OTHER = 3;
	/** Eclipse marks: {@link #ids} glow red (replaces the previous list). */
	public static final int KIND_MARKS = 4;
	/** Dew gathered here (a sparkle). */
	public static final int KIND_DEW = 5;
	/** A meteor stone absorbed by the receiver. */
	public static final int KIND_ABSORB = 6;
	/** The channel at the treasure broke (hurt or moved away). */
	public static final int KIND_CHANNEL_BROKEN = 7;
	/** A weak demon prison seal near the receiver ({@code a} = felt strength 1-100): the ash-red omen sky (SealOmens). */
	public static final int KIND_SEAL_OMEN = 8;

	private static final int[] NO_IDS = new int[0];

	public static final Type<LangitFxPayload> TYPE = new Type<>(Kultivator.id("langit_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LangitFxPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.kind);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeVarInt(p.a);
				buf.writeVarIntArray(p.ids == null ? NO_IDS : p.ids);
			},
			buf -> new LangitFxPayload(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(),
					buf.readVarIntArray()));

	public static LangitFxPayload at(int kind, double x, double y, double z, int a) {
		return new LangitFxPayload(kind, x, y, z, a, NO_IDS);
	}

	@Override
	public Type<LangitFxPayload> type() {
		return TYPE;
	}
}
