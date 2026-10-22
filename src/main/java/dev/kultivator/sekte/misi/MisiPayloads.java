package dev.kultivator.sekte.misi;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Module X's own payloads (DESIGN 36.4: registered from the module's init with {@code PayloadTypeRegistry}, never in
 * {@code network/Payloads} or {@code Net}):
 * <ul>
 * <li>{@link Aksi} client to server: an action of the story tabs ({@code buka}, {@code cek}, {@code kirim},
 * {@code tarik}, {@code tinggal}, {@code baca});</li>
 * <li>{@link Keadaan} server to client: the board, the running missions, the history and the last preview;</li>
 * <li>{@link Laporan} server to client: one report to show in the report screen.</li>
 * </ul>
 * The state and the reports are NBT (bounded: at most 6 offers, 4 missions, 8 reports of a few hundred bytes).
 */
public final class MisiPayloads {

	/** {@code op} the action, {@code a} an index or id, {@code text} the rest (souls, stance). */
	public record Aksi(String op, int a, String text) implements CustomPacketPayload {
		public static final Type<Aksi> TYPE = new Type<>(Kultivator.id("sekte_misi_aksi"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Aksi> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.op == null ? "" : p.op, 16);
					buf.writeVarInt(p.a);
					buf.writeUtf(p.text == null ? "" : p.text, 512);
				},
				buf -> new Aksi(buf.readUtf(16), buf.readVarInt(), buf.readUtf(512)));

		@Override
		public Type<Aksi> type() {
			return TYPE;
		}
	}

	public record Keadaan(CompoundTag state) implements CustomPacketPayload {
		public static final Type<Keadaan> TYPE = new Type<>(Kultivator.id("sekte_misi_keadaan"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Keadaan> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.state),
				buf -> new Keadaan(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Keadaan> type() {
			return TYPE;
		}
	}

	public record Laporan(CompoundTag report) implements CustomPacketPayload {
		public static final Type<Laporan> TYPE = new Type<>(Kultivator.id("sekte_misi_laporan"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Laporan> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.report),
				buf -> new Laporan(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Laporan> type() {
			return TYPE;
		}
	}

	private static boolean registered;

	/** Once from {@code Misi.init} (both physical sides: the client needs the types too). */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.serverboundPlay().register(Aksi.TYPE, Aksi.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Keadaan.TYPE, Keadaan.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Laporan.TYPE, Laporan.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Aksi.TYPE, (payload, context) -> Misi.onAction(context.player(), payload));
	}

	static void sendState(ServerPlayer player, CompoundTag state) {
		if (ServerPlayNetworking.canSend(player, Keadaan.TYPE)) {
			ServerPlayNetworking.send(player, new Keadaan(state));
		}
	}

	static void sendReport(ServerPlayer player, CompoundTag report) {
		if (ServerPlayNetworking.canSend(player, Laporan.TYPE)) {
			ServerPlayNetworking.send(player, new Laporan(report));
		}
	}

	private MisiPayloads() {
	}
}
