package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Module R's own payloads (DESIGN 34.6, rule C2: registered here, never in {@code network/Payloads}):
 * <ul>
 * <li>{@link Sync} (server to client): one compound with an {@code op} ({@code fx}, {@code scene}, {@code scene_end},
 * {@code lobby}, {@code luar}, {@code stele}, {@code riddle}, {@code warn}, {@code ujian}); presentation only;</li>
 * <li>{@link Action} (client to server): {@code op} plus two ints (a riddle answer, a confirmed talisman pull).</li>
 * </ul>
 */
public final class RNet {

	/** Wire budget of one sync (vanilla allows 1 MiB; ours stay far below). */
	public static final int MAX_BYTES = 32768;

	public record Sync(CompoundTag data) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(Kultivator.id("reruntuhan_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new Sync(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	public record Action(String op, int a, int b) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Kultivator.id("reruntuhan_aksi"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.op, 32);
					buf.writeVarInt(p.a);
					buf.writeVarInt(p.b);
				},
				buf -> new Action(readOp(buf), buf.readVarInt(), buf.readVarInt()));

		private static String readOp(FriendlyByteBuf buf) {
			return buf.readUtf(32);
		}

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	private static boolean registered;

	/** From {@link Reruntuhan#init()} on both physical sides. */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> Reruntuhan.onAction(context.player(), payload));
	}

	/** Sends {@code data} to one player (skipped for clients without the mod). */
	public static void send(ServerPlayer player, CompoundTag data) {
		if (player == null || data == null) {
			return;
		}
		Net.send(player, new Sync(data));
	}

	/** Sends {@code data} to every player of {@code level} within {@code radius} of {@code at}. */
	public static void near(ServerLevel level, Vec3 at, double radius, CompoundTag data) {
		double r2 = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) <= r2) {
				send(player, data);
			}
		}
	}

	/** A one-shot client effect ({@link RFx} ids) at {@code at} for everyone within {@code radius}. */
	public static void fx(ServerLevel level, Vec3 at, double radius, int kind, int a, int b, float f) {
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "fx");
		tag.putInt("k", kind);
		tag.putDouble("x", at.x);
		tag.putDouble("y", at.y);
		tag.putDouble("z", at.z);
		tag.putInt("a", a);
		tag.putInt("b", b);
		tag.putFloat("f", f);
		near(level, at, radius, tag);
	}

	private RNet() {
	}
}
