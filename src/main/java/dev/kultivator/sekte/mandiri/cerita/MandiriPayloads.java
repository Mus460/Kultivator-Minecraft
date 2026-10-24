package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.Kultivator;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The five payloads of module 39E (names reserved in {@code MandiriIds.P_*}, contract C39.E4), registered here with
 * {@code PayloadTypeRegistry} and never in {@code network/Payloads} or {@code Net}. Both physical sides call
 * {@link #init()} from {@code Cerita.registerContent} (K1: payload types always exist); only the server registers the
 * receiver ({@link #initServer()}).
 * <ul>
 * <li>{@link Harian} (S2C): one disciple's diary page on request: the entries, the traits and goal the composer needs, two
 * days of hour strips, the purse and the nine bag slots ({@code BukuHarian.halaman}).</li>
 * <li>{@link Papan} (S2C): the "Jalan Hidup Murid" board: every disciple of the hall (loaded, away and unloaded), the news
 * feed, the hall policy and the money book ({@code PapanMandiri}).</li>
 * <li>{@link Aksi} (C2S): every button of the tab and the board ({@code AksiMandiri}; the server checks everything again).</li>
 * <li>{@link Pikir} (S2C): a thought bubble over a disciple ({@code Pengamat}); the client draws at most six within 32 blocks.</li>
 * <li>{@link Toast} (S2C): the small top-right card of a notice ({@code Pemberitahuan}); the client merges bursts.</li>
 * </ul>
 */
public final class MandiriPayloads {

	/**
	 * Bubble icons, the order of the client atlas {@code textures/sekte/mandiri/pikir.png} (8 x 3 cells of 32 px: the 16
	 * icons, then the cloud and the violet cloud the client draws behind them).
	 */
	public static final int PIKIR_QI = 0;
	public static final int PIKIR_PEDANG = 1;
	public static final int PIKIR_KUALI = 2;
	public static final int PIKIR_JIMAT = 3;
	public static final int PIKIR_BATU = 4;
	public static final int PIKIR_TEH = 5;
	public static final int PIKIR_MANGKUK = 6;
	public static final int PIKIR_ZZZ = 7;
	public static final int PIKIR_GULUNGAN = 8;
	public static final int PIKIR_BUNTALAN = 9;
	public static final int PIKIR_KOTA = 10;
	public static final int PIKIR_BULAN = 11;
	public static final int PIKIR_IBLIS = 12;
	public static final int PIKIR_HATI = 13;
	public static final int PIKIR_BINTANG = 14;
	public static final int PIKIR_SEGEL = 15;
	public static final int PIKIR_IKON = 16;

	/** Toast kinds ({@code Kebijakan.Pemberitahuan} ordinal, or {@link #TOAST_RINGKAS} for the "while you were away" card). */
	public static final int TOAST_RINGKAS = 31;

	/** Bubbles reach players this close to the disciple (DESIGN 39.9: at most six drawn within 32 blocks). */
	public static final double PIKIR_JARAK = 32.0D;

	/** {@code data}: see {@code BukuHarian.halaman}; {@code tas}: the bag, up to nine stacks (empty slots dropped). */
	public record Harian(CompoundTag data, List<ItemStack> tas) implements CustomPacketPayload {
		public static final Type<Harian> TYPE = new Type<>(Kultivator.id(MandiriIds.P_HARIAN));
		public static final StreamCodec<RegistryFriendlyByteBuf, Harian> CODEC = StreamCodec.of(
				(buf, p) -> {
					ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data);
					ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, p.tas);
				},
				buf -> new Harian(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf)));

		@Override
		public Type<Harian> type() {
			return TYPE;
		}
	}

	/** The board data, see {@code PapanMandiri.bangun}. */
	public record Papan(CompoundTag data) implements CustomPacketPayload {
		public static final Type<Papan> TYPE = new Type<>(Kultivator.id(MandiriIds.P_PAPAN));
		public static final StreamCodec<RegistryFriendlyByteBuf, Papan> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new Papan(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Papan> type() {
			return TYPE;
		}
	}

	/**
	 * A button press: {@code op} (see {@code AksiMandiri}), the disciple's soul ({@code ""} for hall-wide ops), one text
	 * argument and one number (an entity id for the loaded-body ops, or an amount).
	 */
	public record Aksi(String op, String soul, String arg, int n) implements CustomPacketPayload {
		public static final Type<Aksi> TYPE = new Type<>(Kultivator.id(MandiriIds.P_AKSI));
		public static final StreamCodec<RegistryFriendlyByteBuf, Aksi> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.op == null ? "" : p.op, 24);
					buf.writeUtf(p.soul == null ? "" : p.soul, 40);
					buf.writeUtf(p.arg == null ? "" : p.arg, 96);
					buf.writeVarInt(p.n);
				},
				buf -> new Aksi(buf.readUtf(24), buf.readUtf(40), buf.readUtf(96), buf.readVarInt()));

		@Override
		public Type<Aksi> type() {
			return TYPE;
		}
	}

	/** A bubble over entity {@code id} showing {@code ikon} for {@code ticks}; {@code kunci} an optional caption key ("" none). */
	public record Pikir(int id, int ikon, String kunci, int ticks) implements CustomPacketPayload {
		public static final Type<Pikir> TYPE = new Type<>(Kultivator.id(MandiriIds.P_PIKIR));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pikir> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.id);
					buf.writeByte(p.ikon);
					buf.writeUtf(p.kunci == null ? "" : p.kunci, 96);
					buf.writeVarInt(p.ticks);
				},
				buf -> new Pikir(buf.readVarInt(), buf.readUnsignedByte(), buf.readUtf(96), buf.readVarInt()));

		@Override
		public Type<Pikir> type() {
			return TYPE;
		}
	}

	/** A notice card: its kind ({@code Pemberitahuan} ordinal or {@link #TOAST_RINGKAS}), the text, whether it is important. */
	public record Toast(int jenis, Component teks, boolean penting) implements CustomPacketPayload {
		public static final Type<Toast> TYPE = new Type<>(Kultivator.id(MandiriIds.P_TOAST));
		public static final StreamCodec<RegistryFriendlyByteBuf, Toast> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeByte(p.jenis);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.teks);
					buf.writeBoolean(p.penting);
				},
				buf -> new Toast(buf.readUnsignedByte(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean()));

		@Override
		public Type<Toast> type() {
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
		PayloadTypeRegistry.clientboundPlay().register(Harian.TYPE, Harian.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Papan.TYPE, Papan.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Aksi.TYPE, Aksi.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Pikir.TYPE, Pikir.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Toast.TYPE, Toast.CODEC);
	}

	/** The server receiver (registered once from {@code Cerita.init}, the module switch on). */
	static void initServer() {
		if (receivers) {
			return;
		}
		receivers = true;
		ServerPlayNetworking.registerGlobalReceiver(Aksi.TYPE, (payload, context) -> AksiMandiri.terima(context.player(), payload));
	}

	static void kirim(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** A bubble over {@code entity} for every player within {@link #PIKIR_JARAK}. */
	static void pikir(Entity entity, int ikon, String kunci, int ticks) {
		if (entity == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Pikir payload = new Pikir(entity.getId(), Math.max(0, Math.min(PIKIR_IKON - 1, ikon)), kunci == null ? "" : kunci,
				Math.max(20, Math.min(400, ticks)));
		for (ServerPlayer player : PlayerLookup.around(level, entity.position(), PIKIR_JARAK)) {
			kirim(player, payload);
		}
	}

	private MandiriPayloads() {
	}
}
