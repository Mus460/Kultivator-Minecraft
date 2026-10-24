package dev.kultivator.dunia.tempat.ui;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server {@code kultivator:tempat_aksi} (DESIGN 38.U.1, contract C38.UI1): one choice on a tempat screen.
 * {@code op} is {@code "verb[:arg[:arg...]]"} ({@link LembarKunci#op}), at most {@link #OP_MAKS} chars; the server checks
 * it in {@link TempatLayar} (open session, reach to the NPC or anchor, rate limit, read-only hub) before the screen's
 * {@link TempatLayar.Penangan} sees it.
 */
public record TempatAksiPayload(String layar, int npcId, String op) implements CustomPacketPayload {

	/** Longest op (C38.UI1: op at most 256). */
	public static final int OP_MAKS = 256;
	public static final Type<TempatAksiPayload> TYPE = new Type<>(Kultivator.id("tempat_aksi"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TempatAksiPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeUtf(p.layar, TempatLayar.LAYAR_MAKS);
				buf.writeVarInt(p.npcId);
				buf.writeUtf(p.op, OP_MAKS);
			},
			buf -> new TempatAksiPayload(buf.readUtf(TempatLayar.LAYAR_MAKS), buf.readVarInt(), buf.readUtf(OP_MAKS)));

	public TempatAksiPayload {
		layar = layar == null ? "" : layar;
		op = op == null ? "" : op.length() > OP_MAKS ? op.substring(0, OP_MAKS) : op;
	}

	@Override
	public Type<TempatAksiPayload> type() {
		return TYPE;
	}
}
