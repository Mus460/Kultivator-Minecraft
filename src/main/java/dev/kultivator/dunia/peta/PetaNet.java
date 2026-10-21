package dev.kultivator.dunia.peta;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The world map payloads (DESIGN 34.5.16), registered by {@link Peta#init()} with {@code PayloadTypeRegistry}. One
 * request from the map screen (open, close, regions, markers, waypoints, "Berlayar ke sini"); regions of 32 × 32 cells
 * (≈ 1 KB, unexplored cells already blanked by the server) and the marker sheet back.
 */
public final class PetaNet {

	public static final int OP_OPEN = 0;
	public static final int OP_CLOSE = 1;
	public static final int OP_REGIONS = 2;
	public static final int OP_INFO = 3;
	public static final int OP_WAYPOINT_ADD = 4;
	public static final int OP_WAYPOINT_DELETE = 5;
	public static final int OP_SAIL = 6;
	public static final int OP_WAYPOINT_RENAME = 7;

	/** Most regions one request may ask for. */
	public static final int MAX_REGIONS = 64;

	/** The map screen asks: {@code op}, a block position, a text and region keys. */
	public record Request(int op, int x, int z, String text, long[] keys) implements CustomPacketPayload {

		public static final Type<Request> TYPE = new Type<>(Kultivator.id("peta_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.op);
					buf.writeVarInt(p.x);
					buf.writeVarInt(p.z);
					buf.writeUtf(p.text == null ? "" : p.text, 128);
					long[] keys = p.keys == null ? new long[0] : p.keys;
					int n = Math.min(MAX_REGIONS, keys.length);
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						buf.writeLong(keys[i]);
					}
				},
				buf -> {
					int op = buf.readVarInt();
					int x = buf.readVarInt();
					int z = buf.readVarInt();
					String text = buf.readUtf(128);
					int n = Math.max(0, Math.min(MAX_REGIONS, buf.readVarInt()));
					long[] keys = new long[n];
					for (int i = 0; i < n; i++) {
						keys[i] = buf.readLong();
					}
					return new Request(op, x, z, text, keys);
				});

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** One region of {@code dim}: 1 024 cell bytes ({@link PetaInk}), 0 where unexplored or not sampled yet. */
	public record Region(String dim, int rx, int rz, int cellSize, byte[] cells) implements CustomPacketPayload {

		public static final Type<Region> TYPE = new Type<>(Kultivator.id("peta_region"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Region> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.dim, 128);
					buf.writeVarInt(p.rx);
					buf.writeVarInt(p.rz);
					buf.writeVarInt(p.cellSize);
					buf.writeByteArray(p.cells);
				},
				buf -> new Region(buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PetaData.CELLS)));

		@Override
		public Type<Region> type() {
			return TYPE;
		}
	}

	/** The marker sheet ({@code open}: also opens the map screen). */
	public record Info(boolean open, CompoundTag info) implements CustomPacketPayload {

		public static final Type<Info> TYPE = new Type<>(Kultivator.id("peta_info"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Info> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeBoolean(p.open);
					buf.writeNbt(p.info);
				},
				buf -> {
					boolean open = buf.readBoolean();
					CompoundTag tag = buf.readNbt();
					return new Info(open, tag == null ? new CompoundTag() : tag);
				});

		@Override
		public Type<Info> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Region.TYPE, Region.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Info.TYPE, Info.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> Peta.onRequest(context.player(), payload));
	}

	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private PetaNet() {
	}
}
