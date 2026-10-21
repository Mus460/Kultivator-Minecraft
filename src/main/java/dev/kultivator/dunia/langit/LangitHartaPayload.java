package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One treasure birth (DESIGN 34.5.9), server to client, to every player of its dimension: the rainbow pillar is drawn from
 * this alone (also beyond the render distance, sky-projected); the treasure itself is the {@code harta_lahir} entity.
 *
 * @param phase {@link #TREMOR} (30 s before the birth: only the shaking), {@link #LIVE}, {@link #CLAIMED} (a player won;
 *              the burst), {@link #RIVAL} (a rival group took it), {@link #FADED} (it ended unclaimed)
 * @param born  game time of the birth; {@code ends} the game time it fades
 */
public record LangitHartaPayload(long id, int phase, int x, int y, int z, int tier, String kind, long born, long ends)
		implements CustomPacketPayload {

	public static final int TREMOR = 0;
	public static final int LIVE = 1;
	public static final int CLAIMED = 2;
	public static final int RIVAL = 3;
	public static final int FADED = 4;

	public static final Type<LangitHartaPayload> TYPE = new Type<>(Kultivator.id("langit_harta"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LangitHartaPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeLong(p.id);
				buf.writeVarInt(p.phase);
				buf.writeVarInt(p.x);
				buf.writeVarInt(p.y);
				buf.writeVarInt(p.z);
				buf.writeVarInt(p.tier);
				buf.writeUtf(p.kind == null ? "" : p.kind, 16);
				buf.writeVarLong(Math.max(0L, p.born));
				buf.writeVarLong(Math.max(0L, p.ends));
			},
			buf -> new LangitHartaPayload(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readUtf(16), buf.readVarLong(), buf.readVarLong()));

	/** True for the phases that end the birth on the client. */
	public boolean over() {
		return phase >= CLAIMED;
	}

	@Override
	public Type<LangitHartaPayload> type() {
		return TYPE;
	}
}
