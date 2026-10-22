package dev.kultivator.sekte.paviliun;

import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Module A's own payloads (DESIGN 36.4: registered here with {@code PayloadTypeRegistry}, never in
 * {@code network/Payloads}). Server to client: {@link View} (open or refresh the Rak Kitab or Makam Leluhur screen with
 * its whole page) and {@link Places} (what the client draws in the world for its own sect: the names over the
 * tombstones, the spirit tablets on the altar, the incense smoke and the running rites). Client to server: {@link Act}
 * (a choice on a screen or a hub card), checked again on the server.
 */
public final class PaviliunPayloads {

	public static final int PAGE_RAK = 0;
	public static final int PAGE_MAKAM = 1;

	public static final String OP_OPEN_RAK = "buka_rak";
	public static final String OP_OPEN_MAKAM = "buka_makam";
	/** Deposit the book or slip in the main hand. */
	public static final String OP_DEPOSIT = "setor";
	/** Take rack entry {@code arg} (its id) back. */
	public static final String OP_WITHDRAW = "ambil";
	/** Enshrine the grave of soul {@code arg}. */
	public static final String OP_ENSHRINE = "abadikan";
	/** Start the Qingming (when due). */
	public static final String OP_QINGMING = "qingming";

	/** Open ({@code open} true) or refresh page {@code page} ({@link #PAGE_RAK}, {@link #PAGE_MAKAM}). */
	public record View(int page, boolean open, CompoundTag data) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(Kultivator.id("sekte_paviliun_view"));
		public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.page);
					buf.writeBoolean(p.open);
					ByteBufCodecs.COMPOUND_TAG.encode(buf, p.data);
				},
				buf -> new View(buf.readVarInt(), buf.readBoolean(), ByteBufCodecs.COMPOUND_TAG.decode(buf)));

		@Override
		public Type<View> type() {
			return TYPE;
		}
	}

	/** The world picture of the viewer's own sect (an empty tag clears it). */
	public record Places(CompoundTag data) implements CustomPacketPayload {
		public static final Type<Places> TYPE = new Type<>(Kultivator.id("sekte_paviliun_places"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Places> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, p.data),
				buf -> new Places(ByteBufCodecs.COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Places> type() {
			return TYPE;
		}
	}

	/** A choice: {@code op} and its argument. */
	public record Act(String op, String arg) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(Kultivator.id("sekte_paviliun_aksi"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.op, 32);
					buf.writeUtf(p.arg, 128);
				},
				buf -> new Act(buf.readUtf(32), buf.readUtf(128)));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Places.TYPE, Places.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> PaviliunView.onAct(context.player(), payload.op(), payload.arg()));
	}

	static void view(ServerPlayer player, int page, boolean open, CompoundTag data) {
		Net.send(player, new View(page, open, data));
	}

	static void places(ServerPlayer player, CompoundTag data) {
		Net.send(player, new Places(data));
	}

	private PaviliunPayloads() {
	}
}
