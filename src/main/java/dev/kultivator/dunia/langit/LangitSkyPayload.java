package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code LangitSync} of DESIGN 34.5.8, server to client: the sky event of the receiver's dimension. Sent to every player of
 * the dimension when it is announced, starts or ends, and to one player on join and on a dimension change.
 *
 * @param kind       a {@code SkyKind} ordinal, or -1 for none
 * @param state      {@link #NONE}, {@link #OMEN} (announced at sunset, starts at nightfall) or {@link #ACTIVE}
 * @param seed       the event's seed (the client's streak and moon layout)
 * @param startClock overworld clock tick the event starts (the HUD chip and the fade-in)
 * @param endClock   overworld clock tick it ends
 */
public record LangitSkyPayload(int kind, int state, int seed, long startClock, long endClock) implements CustomPacketPayload {

	public static final int NONE = 0;
	public static final int OMEN = 1;
	public static final int ACTIVE = 2;

	public static final Type<LangitSkyPayload> TYPE = new Type<>(Kultivator.id("langit_sky"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LangitSkyPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.kind + 1);
				buf.writeVarInt(p.state);
				buf.writeInt(p.seed);
				buf.writeVarLong(Math.max(0L, p.startClock));
				buf.writeVarLong(Math.max(0L, p.endClock));
			},
			buf -> new LangitSkyPayload(buf.readVarInt() - 1, buf.readVarInt(), buf.readInt(), buf.readVarLong(), buf.readVarLong()));

	public static LangitSkyPayload none() {
		return new LangitSkyPayload(-1, NONE, 0, 0L, 0L);
	}

	@Override
	public Type<LangitSkyPayload> type() {
		return TYPE;
	}
}
