package dev.kultivator.dunia;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the ghost preview of a structure template ({@code /kultivasi uji struktur lihat}, DESIGN 34.4.6
 * rule W6). The client draws {@code boxes} as client-only block displays at {@code (x, y, z)} for {@code ticks} ticks and
 * never touches a block. Each box is 7 ints: local x, y, z, size x, y, z, block state id ({@code Block.getId}). An empty
 * box list clears the preview.
 */
public record DuniaGhostPayload(double x, double y, double z, int ticks, int[] boxes) implements CustomPacketPayload {

	public static final int INTS_PER_BOX = 7;
	public static final int MAX_BOXES = 256;

	public static final Type<DuniaGhostPayload> TYPE = new Type<>(Kultivator.id("dunia_ghost"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DuniaGhostPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeVarInt(p.ticks);
				int n = Math.min(MAX_BOXES * INTS_PER_BOX, p.boxes.length - p.boxes.length % INTS_PER_BOX);
				buf.writeVarInt(n);
				for (int i = 0; i < n; i++) {
					buf.writeVarInt(p.boxes[i]);
				}
			},
			buf -> {
				double x = buf.readDouble();
				double y = buf.readDouble();
				double z = buf.readDouble();
				int ticks = buf.readVarInt();
				int n = Math.min(MAX_BOXES * INTS_PER_BOX, Math.max(0, buf.readVarInt()));
				int[] boxes = new int[n];
				for (int i = 0; i < n; i++) {
					boxes[i] = buf.readVarInt();
				}
				return new DuniaGhostPayload(x, y, z, ticks, boxes);
			});

	public int count() {
		return boxes.length / INTS_PER_BOX;
	}

	@Override
	public Type<DuniaGhostPayload> type() {
		return TYPE;
	}
}
