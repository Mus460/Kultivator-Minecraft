package dev.kultivator.sekte.kepribadian;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Module P's own payloads (DESIGN 36.4, registered here with {@code PayloadTypeRegistry}; nothing in
 * {@code network/Payloads} or {@code Net}):
 * <ul>
 * <li>{@link Aksi} (client to server): a button of the Hati tab or the hub card: {@code entityId} the disciple (-1 for
 * the hub list), {@code action} {@code A_*}.</li>
 * <li>{@link Suasana} (server to client): the moods of the disciples near a player (thought bubbles, at most
 * {@link #MAX_MOODS}), every 2 s while something changed or every 10 s.</li>
 * <li>{@link Letup} (server to client): a short bubble over one disciple (a heart after a gift, a tear after a blow).</li>
 * <li>{@link Daftar} (server to client): the hub card's list: every disciple's heart and the ones that left.</li>
 * </ul>
 */
public final class KepribadianPayloads {

	public static final int A_VIEW = 0;
	public static final int A_TALK = 1;
	public static final int A_GIFT = 2;
	public static final int A_APOLOGY = 3;
	public static final int A_EXPEL = 4;
	public static final int A_LIST = 5;

	public static final int ICON_NONE = 0;
	public static final int ICON_HEART = 1;
	public static final int ICON_CLOUD = 2;
	public static final int ICON_STORM = 3;
	public static final int ICON_ALERT = 4;
	public static final int ICON_TEAR = 5;
	public static final int ICON_STAR = 6;
	public static final int ICON_BUNDLE = 7;
	public static final int ICONS = 8;

	public static final int MAX_MOODS = 12;
	public static final int MAX_ROWS = 40;
	/** Players within this distance see a short bubble. */
	public static final double POP_RANGE = 32.0D;

	// ================================================================== client to server

	public record Aksi(int entityId, int action, String arg) implements CustomPacketPayload {
		public static final Type<Aksi> TYPE = new Type<>(Kultivator.id("sekte_hati_aksi"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Aksi> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.entityId + 1);
					buf.writeByte(p.action);
					buf.writeUtf(p.arg == null ? "" : p.arg, 64);
				},
				buf -> new Aksi(buf.readVarInt() - 1, buf.readUnsignedByte(), buf.readUtf(64)));

		@Override
		public Type<Aksi> type() {
			return TYPE;
		}
	}

	// ================================================================== server to client

	/** {@code ids} and {@code icons} have the same length (at most {@link #MAX_MOODS}). */
	public record Suasana(int[] ids, int[] icons) implements CustomPacketPayload {
		public static final Type<Suasana> TYPE = new Type<>(Kultivator.id("sekte_hati_suasana"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Suasana> CODEC = StreamCodec.of(
				(buf, p) -> {
					int n = Math.min(MAX_MOODS, Math.min(p.ids.length, p.icons.length));
					buf.writeByte(n);
					for (int i = 0; i < n; i++) {
						buf.writeVarInt(p.ids[i]);
						buf.writeByte(p.icons[i]);
					}
				},
				buf -> {
					int n = Math.min(MAX_MOODS, buf.readUnsignedByte());
					int[] ids = new int[n];
					int[] icons = new int[n];
					for (int i = 0; i < n; i++) {
						ids[i] = buf.readVarInt();
						icons[i] = buf.readUnsignedByte();
					}
					return new Suasana(ids, icons);
				});

		@Override
		public Type<Suasana> type() {
			return TYPE;
		}
	}

	public record Letup(int entityId, int icon) implements CustomPacketPayload {
		public static final Type<Letup> TYPE = new Type<>(Kultivator.id("sekte_hati_letup"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Letup> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.entityId);
					buf.writeByte(p.icon);
				},
				buf -> new Letup(buf.readVarInt(), buf.readUnsignedByte()));

		@Override
		public Type<Letup> type() {
			return TYPE;
		}
	}

	/**
	 * One disciple of the hub list: its name, state ordinal, loyalty, contentment, days left (-1), trait ids joined by
	 * commas, flags ({@code F_*}) and its entity id (-1 when not loaded).
	 */
	public record Baris(Component name, int state, int loyalty, int contentment, int daysLeft, String traits, int flags, int entityId) {
	}

	/** One that left: name, the way it left ({@code Kepergian} id) and the day. */
	public record Pergi(Component name, String how, long day) {
	}

	public static final int F_SEALED = 1;
	public static final int F_HEIR = 2;
	public static final int F_AWAY = 4;
	public static final int F_PENDING = 8;

	public record Daftar(List<Baris> rows, List<Pergi> gone, long today) implements CustomPacketPayload {
		public static final Type<Daftar> TYPE = new Type<>(Kultivator.id("sekte_hati_daftar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Daftar> CODEC = StreamCodec.of(
				(buf, p) -> {
					int n = Math.min(MAX_ROWS, p.rows.size());
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						Baris b = p.rows.get(i);
						ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, b.name());
						buf.writeByte(b.state());
						buf.writeByte(b.loyalty());
						buf.writeByte(b.contentment());
						buf.writeByte(b.daysLeft() + 1);
						buf.writeUtf(b.traits(), 128);
						buf.writeByte(b.flags());
						buf.writeVarInt(b.entityId() + 1);
					}
					int g = Math.min(YangPergi.MAX, p.gone.size());
					buf.writeVarInt(g);
					for (int i = 0; i < g; i++) {
						Pergi q = p.gone.get(i);
						ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, q.name());
						buf.writeUtf(q.how(), 32);
						buf.writeVarLong(Math.max(0L, q.day()));
					}
					buf.writeVarLong(Math.max(0L, p.today));
				},
				buf -> {
					int n = Math.min(MAX_ROWS, buf.readVarInt());
					List<Baris> rows = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						rows.add(new Baris(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readUnsignedByte(), buf.readUnsignedByte(),
								buf.readUnsignedByte(), buf.readUnsignedByte() - 1, buf.readUtf(128), buf.readUnsignedByte(), buf.readVarInt() - 1));
					}
					int g = Math.min(YangPergi.MAX, buf.readVarInt());
					List<Pergi> gone = new ArrayList<>(g);
					for (int i = 0; i < g; i++) {
						gone.add(new Pergi(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readUtf(32), buf.readVarLong()));
					}
					return new Daftar(rows, gone, buf.readVarLong());
				});

		@Override
		public Type<Daftar> type() {
			return TYPE;
		}
	}

	// ================================================================== registration and sending

	private static boolean done;

	/** Once, from {@link Kepribadian#init()} (both physical sides: the client needs the types too). */
	static void init() {
		if (done) {
			return;
		}
		done = true;
		PayloadTypeRegistry.serverboundPlay().register(Aksi.TYPE, Aksi.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Suasana.TYPE, Suasana.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Letup.TYPE, Letup.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Daftar.TYPE, Daftar.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Aksi.TYPE, (payload, context) -> Kepribadian.onAction(context.player(), payload));
	}

	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** A short bubble over {@code e} for everyone within {@link #POP_RANGE}. */
	static void pop(DiscipleEntity e, int icon) {
		if (!(e.level() instanceof ServerLevel level) || icon <= ICON_NONE || icon >= ICONS) {
			return;
		}
		Letup payload = new Letup(e.getId(), icon);
		for (ServerPlayer player : PlayerLookup.around(level, e.position(), POP_RANGE)) {
			send(player, payload);
		}
	}

	private KepribadianPayloads() {
	}
}
