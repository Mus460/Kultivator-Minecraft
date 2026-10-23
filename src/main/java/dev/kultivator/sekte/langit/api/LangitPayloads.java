package dev.kultivator.sekte.langit.api;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * The payloads of Sekte Langit (DESIGN 37.6.1, 37.9.1), registered once from {@code LangitContent.init()} on both
 * physical sides whatever the config says (K1). {@link Sync} and {@link Fx} go to the client (sent by 37G and 37M/37B),
 * {@link Saw} comes back (the client saw the halo); its receiver is registered here and dispatches to {@link #onSaw}
 * listeners, so no two modules register the same receiver. Server code never touches a client class.
 */
public final class LangitPayloads {

	/** At most this many halls in one {@link Sync}. */
	public static final int MAX_HALLS = 4;
	/** At most this many anchors per hall. */
	public static final int MAX_ANCHORS = 48;

	/** A visual anchor of a sky hall ({@code airterjun_*}, {@code jaring_*}, {@code gerbang_langit}, {@code dermaga_*}...). */
	public record Anchor(String id, BlockPos pos) {
	}

	/**
	 * One sky hall for the client. {@code color} is the emblem colour (0xRRGGBB), {@code flags} the bits of
	 * {@link LangitIds} {@code FLAG_*}, {@code domeRadius} the Grand Formation dome radius (0 = none).
	 */
	public record SkyHall(UUID owner, BlockPos origin, int yP, int skyFloorY, BoundingBox box, int tier, int color, int domeRadius,
			int flags, List<Anchor> anchors) {
	}

	/** Server → client: every sky hall near the player (replaces the client's list). */
	public record Sync(List<SkyHall> halls) implements CustomPacketPayload {

		public static final Type<Sync> TYPE = new Type<>(Kultivator.id("sekte_langit_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of(LangitPayloads::writeSync, LangitPayloads::readSync);

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** Server → client: a transient effect {@code op} ({@link LangitIds} {@code FX_*}) between {@code a} and {@code b}. */
	public record Fx(int op, BlockPos a, BlockPos b) implements CustomPacketPayload {

		public static final Type<Fx> TYPE = new Type<>(Kultivator.id("sekte_langit_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.op);
					buf.writeBlockPos(p.a);
					buf.writeBlockPos(p.b);
				},
				buf -> new Fx(buf.readVarInt(), buf.readBlockPos(), buf.readBlockPos()));

		@Override
		public Type<Fx> type() {
			return TYPE;
		}
	}

	/** Client → server: the client saw {@code what} ({@link LangitIds#SAW_HALO}); the server checks time and place. */
	public record Saw(int what) implements CustomPacketPayload {

		public static final Type<Saw> TYPE = new Type<>(Kultivator.id("sekte_langit_saw"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Saw> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeVarInt(p.what),
				buf -> new Saw(buf.readVarInt()));

		@Override
		public Type<Saw> type() {
			return TYPE;
		}
	}

	private static final List<BiConsumer<ServerPlayer, Integer>> SAW = new CopyOnWriteArrayList<>();
	private static boolean registered;

	/** From {@code LangitContent.init()} (both sides, always). */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Fx.TYPE, Fx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Saw.TYPE, Saw.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Saw.TYPE, (payload, context) -> {
			if (!LangitConfig.on()) {
				return;
			}
			ServerPlayer player = context.player();
			for (BiConsumer<ServerPlayer, Integer> listener : SAW) {
				listener.accept(player, payload.what());
			}
		});
	}

	/** Adds a {@link Saw} handler (37G checks time and position, then grants the advancement). */
	public static void onSaw(BiConsumer<ServerPlayer, Integer> listener) {
		if (listener != null) {
			SAW.add(listener);
		}
	}

	/** Sends {@code payload} when the client can receive it (a vanilla client never gets it). */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** A transient effect to every player of {@code level} within {@code range} blocks of {@code a}. */
	public static void fxNear(net.minecraft.server.level.ServerLevel level, int op, BlockPos a, BlockPos b, double range) {
		Fx fx = new Fx(op, a, b);
		double r2 = range * range;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(a.getX() + 0.5D, a.getY() + 0.5D, a.getZ() + 0.5D) <= r2) {
				send(player, fx);
			}
		}
	}

	// ================================================================== codec

	private static void writeSync(RegistryFriendlyByteBuf buf, Sync sync) {
		List<SkyHall> halls = sync.halls == null ? List.of() : sync.halls;
		int n = Math.min(MAX_HALLS, halls.size());
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			SkyHall hall = halls.get(i);
			buf.writeUUID(hall.owner);
			buf.writeBlockPos(hall.origin);
			buf.writeVarInt(hall.yP);
			buf.writeVarInt(hall.skyFloorY);
			BoundingBox box = hall.box;
			buf.writeVarInt(box.minX());
			buf.writeVarInt(box.minY());
			buf.writeVarInt(box.minZ());
			buf.writeVarInt(box.maxX());
			buf.writeVarInt(box.maxY());
			buf.writeVarInt(box.maxZ());
			buf.writeVarInt(hall.tier);
			buf.writeInt(hall.color);
			buf.writeVarInt(hall.domeRadius);
			buf.writeVarInt(hall.flags);
			List<Anchor> anchors = hall.anchors == null ? List.of() : hall.anchors;
			int m = Math.min(MAX_ANCHORS, anchors.size());
			buf.writeVarInt(m);
			for (int k = 0; k < m; k++) {
				buf.writeUtf(anchors.get(k).id(), 48);
				buf.writeBlockPos(anchors.get(k).pos());
			}
		}
	}

	private static Sync readSync(RegistryFriendlyByteBuf buf) {
		int n = Math.min(MAX_HALLS, buf.readVarInt());
		List<SkyHall> halls = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			UUID owner = buf.readUUID();
			BlockPos origin = buf.readBlockPos();
			int yP = buf.readVarInt();
			int floor = buf.readVarInt();
			BoundingBox box = new BoundingBox(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
			int tier = buf.readVarInt();
			int color = buf.readInt();
			int dome = buf.readVarInt();
			int flags = buf.readVarInt();
			int m = Math.min(MAX_ANCHORS, buf.readVarInt());
			List<Anchor> anchors = new ArrayList<>(m);
			for (int k = 0; k < m; k++) {
				anchors.add(new Anchor(buf.readUtf(48), buf.readBlockPos()));
			}
			halls.add(new SkyHall(owner, origin, yP, floor, box, tier, color, dome, flags, List.copyOf(anchors)));
		}
		return new Sync(List.copyOf(halls));
	}

	private LangitPayloads() {
	}
}
