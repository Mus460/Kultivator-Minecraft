package dev.kultivator.sekte.tetua;

import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
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
 * Module E's own payloads (DESIGN 36.4: registered here with {@code PayloadTypeRegistry}, never in
 * {@code network/Payloads}). Server to client: {@link Balai} (open or refresh the Balai Tetua screen with the hall's
 * whole view), {@link Report} (the morning report card) and {@link Ceremony} (which bodies bow and kneel at a succession,
 * for the poses). Client to server: {@link Act} (a choice on the screen: {@code buka}, {@code angkat:<office>:<soul>},
 * {@code copot:<office>}, {@code laporan}, {@code takhta}), checked again on the server.
 */
public final class TetuaPayloads {

	public static final String OP_OPEN = "buka";
	public static final String OP_APPOINT = "angkat";
	public static final String OP_DISMISS = "copot";
	public static final String OP_REPORT = "laporan";
	public static final String OP_THRONE = "takhta";

	/** Open ({@code open} true) or refresh the Balai Tetua screen. */
	public record Balai(boolean open, CompoundTag data) implements CustomPacketPayload {
		public static final Type<Balai> TYPE = new Type<>(Kultivator.id("sekte_tetua_balai"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Balai> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeBoolean(p.open);
					ByteBufCodecs.COMPOUND_TAG.encode(buf, p.data);
				},
				buf -> new Balai(buf.readBoolean(), ByteBufCodecs.COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Balai> type() {
			return TYPE;
		}
	}

	/** The morning report: day, groups of lines by module, the day's totals, who brought it. */
	public record Report(CompoundTag report) implements CustomPacketPayload {
		public static final Type<Report> TYPE = new Type<>(Kultivator.id("sekte_tetua_laporan"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Report> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, p.report),
				buf -> new Report(ByteBufCodecs.COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Report> type() {
			return TYPE;
		}
	}

	/**
	 * A succession ceremony near the client: the bowing rows ({@code ids}), the heir, the phase ({@code 0} gathering,
	 * {@code 1} kneeling, {@code 2} risen, {@code 3} over) and how long the poses may last without news (ticks).
	 */
	public record Ceremony(double x, double y, double z, int[] ids, int heirId, int phase, int life) implements CustomPacketPayload {
		public static final int MAX_IDS = 32;
		public static final Type<Ceremony> TYPE = new Type<>(Kultivator.id("sekte_tetua_upacara"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ceremony> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					int n = Math.min(MAX_IDS, p.ids.length);
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						buf.writeVarInt(p.ids[i]);
					}
					buf.writeVarInt(p.heirId);
					buf.writeVarInt(p.phase);
					buf.writeVarInt(p.life);
				},
				buf -> {
					double x = buf.readDouble();
					double y = buf.readDouble();
					double z = buf.readDouble();
					int n = Math.min(MAX_IDS, Math.max(0, buf.readVarInt()));
					int[] ids = new int[n];
					for (int i = 0; i < n; i++) {
						ids[i] = buf.readVarInt();
					}
					return new Ceremony(x, y, z, ids, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
				});

		@Override
		public Type<Ceremony> type() {
			return TYPE;
		}
	}

	/** A choice on the Balai Tetua screen: {@code op} and its argument. */
	public record Act(String op, String arg) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(Kultivator.id("sekte_tetua_aksi"));
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
		PayloadTypeRegistry.clientboundPlay().register(Balai.TYPE, Balai.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Report.TYPE, Report.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Ceremony.TYPE, Ceremony.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> BalaiTetua.onAct(context.player(), payload.op(), payload.arg()));
	}

	static void balai(ServerPlayer player, boolean open, CompoundTag data) {
		Net.send(player, new Balai(open, data));
	}

	static void report(ServerPlayer player, CompoundTag report) {
		Net.send(player, new Report(report));
	}

	static void ceremony(ServerLevel level, Vec3 centre, int[] ids, int heirId, int phase, int life) {
		Ceremony payload = new Ceremony(centre.x, centre.y, centre.z, ids, heirId, phase, life);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceTo(centre) <= 96.0D) {
				Net.send(player, payload);
			}
		}
	}

	private TetuaPayloads() {
	}
}
