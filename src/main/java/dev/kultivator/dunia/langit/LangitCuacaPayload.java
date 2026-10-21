package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The spirit weather around the receiver (DESIGN 34.5.10), server to client: the 3 × 3 cells centred on cell
 * ({@code cx}, {@code cz}) of size {@code cellSize}, row by row from the north-west ({@code kinds[(dz + 1) * 3 + dx + 1]},
 * each a {@code SpiritWeather} ordinal). Sent when the player's cell or a kind in the view changes; {@code cellSize 0}
 * clears it (weather off, another dimension).
 */
public record LangitCuacaPayload(int cellSize, int cx, int cz, byte[] kinds) implements CustomPacketPayload {

	public static final Type<LangitCuacaPayload> TYPE = new Type<>(Kultivator.id("langit_cuaca"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LangitCuacaPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.cellSize);
				buf.writeVarInt(p.cx);
				buf.writeVarInt(p.cz);
				buf.writeByteArray(p.kinds);
			},
			buf -> new LangitCuacaPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray()));

	public static LangitCuacaPayload clear() {
		return new LangitCuacaPayload(0, 0, 0, new byte[9]);
	}

	/** The kind ordinal of the view cell at offset (dx, dz) in -1..1, or 0 (clear) outside the view. */
	public int kindAt(int dx, int dz) {
		if (dx < -1 || dx > 1 || dz < -1 || dz > 1 || kinds.length < 9) {
			return 0;
		}
		return kinds[(dz + 1) * 3 + dx + 1];
	}

	@Override
	public Type<LangitCuacaPayload> type() {
		return TYPE;
	}
}
