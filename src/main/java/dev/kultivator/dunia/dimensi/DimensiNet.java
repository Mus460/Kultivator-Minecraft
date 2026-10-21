package dev.kultivator.dunia.dimensi;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Module D's payloads (DESIGN 34.6 D), registered with {@code PayloadTypeRegistry} in {@link Dimensi#init()} (the
 * {@code aura/AuraPreviews} pattern; {@code network/Payloads} and {@code Net} are not touched).
 * <ul>
 * <li>{@link Open} (S2C): open one of the dialogue scrolls ({@code SCREEN_*}) with its data.</li>
 * <li>{@link State} (S2C): the soul state of this player (HUD timer, the homesick compass) and the sea flags.</li>
 * <li>{@link Fx} (S2C): a world moment ({@code FX_*}) for the clients near it.</li>
 * <li>{@link Action} (C2S): a button of a scroll ({@code entity} the NPC or mark, {@code action} the choice).</li>
 * </ul>
 */
public final class DimensiNet {

	public static final int SCREEN_MENG_PO = 1;
	public static final int SCREEN_COURT = 2;
	public static final int SCREEN_BOOK = 3;
	public static final int SCREEN_GHOST = 4;
	public static final int SCREEN_KING = 5;
	public static final int SCREEN_MERCHANT = 6;
	public static final int SCREEN_HERMIT = 7;
	public static final int SCREEN_PENGLAI = 8;
	public static final int SCREEN_GUARD = 9;

	public static final int FX_ARRIVE_BAWAH = 1;
	public static final int FX_ARRIVE_LAUT = 2;
	public static final int FX_SOUL_LEAVE = 3;
	public static final int FX_SOUL_RETURN = 4;
	public static final int FX_SPOUT = 5;
	public static final int FX_JUDGED = 6;
	public static final int FX_SOUP = 7;
	public static final int FX_BOOK = 8;
	public static final int FX_HARVEST = 9;
	public static final int FX_WHIRL = 10;
	public static final int FX_BLESS = 11;

	public record Open(int screen, int entity, CompoundTag data) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(Kultivator.id("dimensi_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.screen);
					buf.writeVarInt(p.entity);
					ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data);
				},
				buf -> new Open(buf.readVarInt(), buf.readVarInt(), ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	public record State(CompoundTag data) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(Kultivator.id("dimensi_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new State(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	public record Fx(int kind, double x, double y, double z, int arg) implements CustomPacketPayload {
		public static final Type<Fx> TYPE = new Type<>(Kultivator.id("dimensi_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.kind);
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					buf.writeVarInt(p.arg);
				},
				buf -> new Fx(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt()));

		@Override
		public Type<Fx> type() {
			return TYPE;
		}
	}

	public record Action(int entity, String action) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Kultivator.id("dimensi_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.entity);
					buf.writeUtf(p.action == null ? "" : p.action, 128);
				},
				buf -> new Action(buf.readVarInt(), buf.readUtf(128)));

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Fx.TYPE, Fx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> DimensiActions.handle(context.player(), payload));
	}

	/** Sends {@code payload} when the player's client knows it. */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void open(ServerPlayer player, int screen, int entity, CompoundTag data) {
		send(player, new Open(screen, entity, data));
	}

	/** A world moment for every player of {@code level} within {@code radius} of {@code at}. */
	public static void fx(ServerLevel level, int kind, Vec3 at, int arg, double radius) {
		Fx payload = new Fx(kind, at.x, at.y, at.z, arg);
		double r2 = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) <= r2) {
				send(player, payload);
			}
		}
	}

	private DimensiNet() {
	}
}
