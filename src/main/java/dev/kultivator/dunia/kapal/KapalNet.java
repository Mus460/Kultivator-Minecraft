package dev.kultivator.dunia.kapal;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The ship payloads (DESIGN 34.5.15), registered by {@link Kapal#init()} with {@code PayloadTypeRegistry} (the
 * {@code aura/AuraPreviews} pattern; {@code network/Payloads} is not touched). Client to server: the captain's keys and
 * the helm buttons. Server to client: moments (leap wipes, arrival, launch, pirates) and the helm screen's data.
 */
public final class KapalNet {

	/** The captain's keys, sent every 2 ticks while seated at the helm: each −1, 0 or 1. */
	public record Input(int ship, byte forward, byte turn, byte vertical) implements CustomPacketPayload {

		public static final Type<Input> TYPE = new Type<>(Kultivator.id("kapal_input"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.ship);
					buf.writeByte(p.forward);
					buf.writeByte(p.turn);
					buf.writeByte(p.vertical);
				},
				buf -> new Input(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte()));

		@Override
		public Type<Input> type() {
			return TYPE;
		}
	}

	/** A helm button: {@code op} one of the {@code OP_*} of {@link KapalRohEntity}, with an int and a text argument. */
	public record Helm(int ship, int op, int arg, String text) implements CustomPacketPayload {

		public static final Type<Helm> TYPE = new Type<>(Kultivator.id("kapal_helm"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Helm> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.ship);
					buf.writeVarInt(p.op);
					buf.writeVarInt(p.arg);
					buf.writeUtf(p.text == null ? "" : p.text, 256);
				},
				buf -> new Helm(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256)));

		@Override
		public Type<Helm> type() {
			return TYPE;
		}
	}

	/** A moment of ship {@code ship} for this client: {@code kind} one of {@code EV_*}, a place and an argument. */
	public record Event(int ship, int kind, double x, double y, double z, int arg) implements CustomPacketPayload {

		public static final int EV_LEAP_IN = 1;
		public static final int EV_LEAP_OUT = 2;
		public static final int EV_ARRIVE = 3;
		public static final int EV_LAUNCH = 4;
		public static final int EV_PIRATES = 5;
		public static final int EV_BROKEN = 6;
		public static final int EV_STOWED = 7;

		public static final Type<Event> TYPE = new Type<>(Kultivator.id("kapal_event"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Event> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.ship);
					buf.writeVarInt(p.kind);
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					buf.writeVarInt(p.arg);
				},
				buf -> new Event(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt()));

		@Override
		public Type<Event> type() {
			return TYPE;
		}
	}

	/** The helm screen's data of ship {@code ship} ({@code open} true opens the screen, false only refreshes it). */
	public record Info(int ship, boolean open, CompoundTag info) implements CustomPacketPayload {

		public static final Type<Info> TYPE = new Type<>(Kultivator.id("kapal_info"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Info> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.ship);
					buf.writeBoolean(p.open);
					buf.writeNbt(p.info);
				},
				buf -> {
					int ship = buf.readVarInt();
					boolean open = buf.readBoolean();
					CompoundTag tag = buf.readNbt();
					return new Info(ship, open, tag == null ? new CompoundTag() : tag);
				});

		@Override
		public Type<Info> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Input.TYPE, Input.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Helm.TYPE, Helm.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Event.TYPE, Event.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Info.TYPE, Info.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Input.TYPE, (payload, context) -> KapalRohEntity.onInput(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Helm.TYPE, (payload, context) -> KapalRohEntity.onHelm(context.player(), payload));
	}

	/** Sends {@code payload} when the player's client knows it (a vanilla client never gets it). */
	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private KapalNet() {
	}
}
