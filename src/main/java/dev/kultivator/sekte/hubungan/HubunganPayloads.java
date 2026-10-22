package dev.kultivator.sekte.hubungan;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The payloads of module R (DESIGN 36.4, registered here with {@code PayloadTypeRegistry}, never in
 * {@code network/Payloads} or {@code Net}). Both physical sides call {@link #init()} from {@code Hubungan.init}, so the
 * types exist on the client too; only the server registers the receiver.
 * <ul>
 * <li>{@link Aksi} (client to server): the Hubungan tab and the Jaring screen ask for the network or act on a pair
 * (swear an oath, bless, forbid, call an honour duel, answer a request). The server checks everything again.</li>
 * <li>{@link Gelembung} (server to client): a thought bubble over one or two disciples (tea, heart, lightning ...).</li>
 * <li>{@link Pasangan} (server to client): the lover and Dao-companion bodies near the player (their pink motes).</li>
 * <li>{@link Jaring} (server to client): the whole network of the hall for the Jaring Hubungan screen.</li>
 * </ul>
 */
public final class HubunganPayloads {

	/** Bubble icons (the client's atlas order, {@code textures/sekte/hubungan/ikon.png}). */
	public static final int ICON_TEH = 0;
	public static final int ICON_HATI = 1;
	public static final int ICON_PETIR = 2;
	public static final int ICON_PATAH = 3;
	public static final int ICON_PEDANG = 4;
	public static final int ICON_SIMPUL = 5;
	public static final int ICON_TERATAI = 6;
	public static final int ICON_BINTANG = 7;
	public static final int ICONS = 8;

	/** Bubbles reach players this close to the disciple. */
	static final double BUBBLE_RANGE = 32.0D;

	/** A request of the client: {@code op} (see the class comment), the two souls (or ""), a number (a request id). */
	public record Aksi(String op, String a, String b, int n) implements CustomPacketPayload {
		public static final Type<Aksi> TYPE = new Type<>(Kultivator.id("sekte_hubungan_aksi"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Aksi> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.op == null ? "" : p.op, 16);
					buf.writeUtf(p.a == null ? "" : p.a, 40);
					buf.writeUtf(p.b == null ? "" : p.b, 40);
					buf.writeVarInt(p.n);
				},
				buf -> new Aksi(buf.readUtf(16), buf.readUtf(40), buf.readUtf(40), buf.readVarInt()));

		@Override
		public Type<Aksi> type() {
			return TYPE;
		}
	}

	/** A bubble over entity {@code a} (and {@code b}, -1 for none) showing {@code icon} for {@code ticks}. */
	public record Gelembung(int a, int b, int icon, int ticks) implements CustomPacketPayload {
		public static final Type<Gelembung> TYPE = new Type<>(Kultivator.id("sekte_hubungan_gelembung"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Gelembung> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.a + 1);
					buf.writeVarInt(p.b + 1);
					buf.writeByte(p.icon);
					buf.writeVarInt(p.ticks);
				},
				buf -> new Gelembung(buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readUnsignedByte(), buf.readVarInt()));

		@Override
		public Type<Gelembung> type() {
			return TYPE;
		}
	}

	/** Pairs of entity ids ({@code ids[2i]}, {@code ids[2i+1]}) and their kind ({@code kinds[i]}: 0 lovers, 1 Dao companions). */
	public record Pasangan(int[] ids, int[] kinds) implements CustomPacketPayload {
		public static final Type<Pasangan> TYPE = new Type<>(Kultivator.id("sekte_hubungan_pasangan"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pasangan> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarIntArray(p.ids);
					buf.writeVarIntArray(p.kinds);
				},
				buf -> new Pasangan(buf.readVarIntArray(64), buf.readVarIntArray(32)));

		@Override
		public Type<Pasangan> type() {
			return TYPE;
		}
	}

	/** The network of the hall (see {@code Hubungan.jaring}). */
	public record Jaring(CompoundTag data) implements CustomPacketPayload {
		public static final Type<Jaring> TYPE = new Type<>(Kultivator.id("sekte_hubungan_jaring"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Jaring> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new Jaring(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Jaring> type() {
			return TYPE;
		}
	}

	private static boolean done;
	private static boolean receivers;

	/** Both sides (the types must exist on the client too). */
	static void init() {
		if (done) {
			return;
		}
		done = true;
		PayloadTypeRegistry.serverboundPlay().register(Aksi.TYPE, Aksi.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Gelembung.TYPE, Gelembung.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Pasangan.TYPE, Pasangan.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Jaring.TYPE, Jaring.CODEC);
	}

	/** The server receiver (registered once from {@code Hubungan.init}). */
	static void initServer() {
		if (receivers) {
			return;
		}
		receivers = true;
		ServerPlayNetworking.registerGlobalReceiver(Aksi.TYPE, (payload, context) -> Hubungan.onAction(context.player(), payload));
	}

	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** A bubble over {@code a} (and {@code b}) for every player within 32 blocks. */
	static void bubble(@Nullable DiscipleEntity a, @Nullable DiscipleEntity b, int icon, int ticks) {
		DiscipleEntity anchor = a != null ? a : b;
		if (anchor == null || !(anchor.level() instanceof ServerLevel level)) {
			return;
		}
		Gelembung payload = new Gelembung(a == null ? -1 : a.getId(), b == null || b == a ? -1 : b.getId(),
				Math.max(0, Math.min(ICONS - 1, icon)), Math.max(20, Math.min(1200, ticks)));
		for (ServerPlayer player : PlayerLookup.around(level, anchor.position(), BUBBLE_RANGE)) {
			send(player, payload);
		}
	}

	private HubunganPayloads() {
	}
}
