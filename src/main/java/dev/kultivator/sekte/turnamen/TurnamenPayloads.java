package dev.kultivator.sekte.turnamen;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Module T's payloads (DESIGN 36.2.3), registered from {@link Turnamen#init} on both sides (never in
 * {@code network/Payloads}).
 * <ul>
 * <li>{@link Lingkar} (server → client): the Lingkar Lei Tai of a running tournament for players within 96 blocks of the
 * stage: its centre, ring radius, kind, season, the pair now fighting (empty names between bouts), the bout number; an
 * inactive one removes the ring.</li>
 * <li>{@link Papan} (server → client): the "Turnamen" hub card's data (season board, roll of champions, the live bracket
 * and schedule of the old tournament, the inter-sect record and a waiting challenge).</li>
 * <li>{@link Minta} (client → server): asks for a {@link Papan}; {@code action} 0 just ask, 1 accept the waiting
 * inter-sect challenge, 2 refuse it.</li>
 * </ul>
 */
public final class TurnamenPayloads {

	public static final int KIND_NORMAL = 0;
	public static final int KIND_ELITE = 1;
	public static final int KIND_GUESTS = 2;

	public static final int FLAG_FIGHTING = 1;
	public static final int FLAG_FINAL = 2;
	public static final int FLAG_STAGE = 4;
	public static final int FLAG_CHAMPION = 8;

	public record Lingkar(boolean active, BlockPos centre, float radius, int kind, int season, int bout, int flags, Component nameA,
			Component nameB) implements CustomPacketPayload {

		public static final Type<Lingkar> TYPE = new Type<>(Kultivator.id("sekte_turnamen_lingkar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Lingkar> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeBoolean(p.active);
					buf.writeBlockPos(p.centre);
					buf.writeFloat(p.radius);
					buf.writeVarInt(p.kind);
					buf.writeVarInt(p.season);
					buf.writeVarInt(p.bout);
					buf.writeVarInt(p.flags);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.nameA);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.nameB);
				},
				buf -> new Lingkar(buf.readBoolean(), buf.readBlockPos(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
						buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
						ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)));

		public static Lingkar off(BlockPos centre) {
			return new Lingkar(false, centre, 0.0F, 0, 0, 0, 0, Component.empty(), Component.empty());
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<Lingkar> type() {
			return TYPE;
		}
	}

	public record Papan(CompoundTag data) implements CustomPacketPayload {

		public static final Type<Papan> TYPE = new Type<>(Kultivator.id("sekte_turnamen_papan"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Papan> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new Papan(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Papan> type() {
			return TYPE;
		}
	}

	public record Minta(int action) implements CustomPacketPayload {

		public static final int ASK = 0;
		public static final int ACCEPT = 1;
		public static final int REFUSE = 2;

		public static final Type<Minta> TYPE = new Type<>(Kultivator.id("sekte_turnamen_minta"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Minta> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeVarInt(p.action),
				buf -> new Minta(buf.readVarInt()));

		@Override
		public Type<Minta> type() {
			return TYPE;
		}
	}

	private static boolean done;

	static void init() {
		if (done) {
			return;
		}
		done = true;
		PayloadTypeRegistry.clientboundPlay().register(Lingkar.TYPE, Lingkar.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Papan.TYPE, Papan.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Minta.TYPE, Minta.CODEC);
	}

	private TurnamenPayloads() {
	}
}
