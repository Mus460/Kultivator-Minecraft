package dev.kultivator.dunia.kota;

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
 * Module K's own payloads (DESIGN 34.4, rule C2: registered here with {@code PayloadTypeRegistry}, nothing in
 * {@code network/Payloads}). Server to client: {@link Screen} (open or refresh a town screen or the wanderer meeting)
 * and {@link Fx} (world visuals the client draws: lantern rows, stage light, pond qi, a rival's sword descent, cloud
 * skirts). Client to server: {@link Action} (a choice on a screen, checked against the NPC it names: alive, the right
 * role, within reach).
 */
public final class KotaNet {

	// Screens
	public static final int SCREEN_KOTA = 0;
	public static final int SCREEN_ABADI = 1;
	public static final int SCREEN_PERTEMUAN = 2;
	/** The rival groups page of the DUNIA hub card "Saingan" (no NPC: {@code npcId} -1). */
	public static final int SCREEN_SAINGAN = 3;
	// Tabs of the town screen
	public static final int TAB_TOKO = 0;
	public static final int TAB_LELANG = 1;
	public static final int TAB_TEH = 2;
	public static final int TAB_GELAP = 3;
	public static final int TAB_MISI = 4;
	// Tabs of the Immortal City screen
	public static final int TAB_PASAR_ABADI = 10;
	public static final int TAB_KULIAH = 11;
	public static final int TAB_PERSIK = 12;
	public static final int TAB_TITAH = 13;
	/** Opens the existing Lelang Abadi tab of the Keabadian screen. */
	public static final int TAB_LELANG_ABADI = 20;
	/** The wanderer meeting. */
	public static final int TAB_PERTEMUAN = 30;
	/**
	 * The rival groups (integration audit S21). Data: {@code on}, {@code band}, {@code groups} (list of {@code id},
	 * {@code name} and {@code style} translation keys, {@code color}, {@code grudge} 0-100, {@code beaten}, {@code wins},
	 * {@code losses}, and {@code known} with {@code x}/{@code z} of the home region), {@code ambush_today}/{@code ambush_cap},
	 * {@code pressure_today}/{@code pressure_cap} (the shared enemy pressure) and {@code pending}.
	 */
	public static final int TAB_SAINGAN = 40;
	/** Reach for screen actions (blocks from the NPC). */
	public static final double REACH = 8.0D;

	// World FX kinds
	public static final int FX_LANTERNS = 0;
	public static final int FX_STAGE = 1;
	public static final int FX_POND = 2;
	public static final int FX_DESCENT = 3;
	public static final int FX_CLOUDS = 4;
	public static final int FX_PETALS = 5;
	public static final int FX_SWIRL = 6;
	public static final int FX_BANNER = 7;

	/** Open ({@code open} true) or refresh a screen with {@code data} (the server's whole view for that NPC). */
	public record Screen(boolean open, int screen, int tab, int npcId, CompoundTag data) implements CustomPacketPayload {
		public static final Type<Screen> TYPE = new Type<>(Kultivator.id("kota_screen"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Screen> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeBoolean(p.open);
					buf.writeVarInt(p.screen);
					buf.writeVarInt(p.tab);
					buf.writeVarInt(p.npcId);
					ByteBufCodecs.COMPOUND_TAG.encode(buf, p.data);
				},
				buf -> new Screen(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), ByteBufCodecs.COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Screen> type() {
			return TYPE;
		}
	}

	/**
	 * A world visual: {@code kind} ({@code FX_*}), a position, two ints and a point list (relative block offsets, 3
	 * ints each) and its life in ticks.
	 */
	public record Fx(int kind, double x, double y, double z, int a, int b, int life, int[] points) implements CustomPacketPayload {
		public static final int MAX_POINTS = 64 * 3;
		public static final Type<Fx> TYPE = new Type<>(Kultivator.id("kota_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.kind);
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					buf.writeInt(p.a);
					buf.writeInt(p.b);
					buf.writeVarInt(p.life);
					int n = Math.min(MAX_POINTS, p.points.length - p.points.length % 3);
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						buf.writeInt(p.points[i]);
					}
				},
				buf -> {
					int kind = buf.readVarInt();
					double x = buf.readDouble();
					double y = buf.readDouble();
					double z = buf.readDouble();
					int a = buf.readInt();
					int b = buf.readInt();
					int life = buf.readVarInt();
					int n = Math.min(MAX_POINTS, Math.max(0, buf.readVarInt()));
					int[] points = new int[n];
					for (int i = 0; i < n; i++) {
						points[i] = buf.readInt();
					}
					return new Fx(kind, x, y, z, a, b, life, points);
				});

		@Override
		public Type<Fx> type() {
			return TYPE;
		}
	}

	/** A screen choice: {@code op} is "verb[:arg[:arg]]", checked on the server against NPC {@code npcId}. */
	public record Action(int npcId, String op) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(Kultivator.id("kota_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.npcId);
					buf.writeUtf(p.op, 256);
				},
				buf -> new Action(buf.readVarInt(), buf.readUtf(256)));

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	/**
	 * Client to server: "show me the rival groups" (the DUNIA hub card "Saingan"); answered with a {@link Screen} of
	 * {@link #SCREEN_SAINGAN} / {@link #TAB_SAINGAN}, {@code open} as asked.
	 */
	public record SainganAsk(boolean open) implements CustomPacketPayload {
		public static final Type<SainganAsk> TYPE = new Type<>(Kultivator.id("kota_saingan_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SainganAsk> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeBoolean(p.open),
				buf -> new SainganAsk(buf.readBoolean()));

		@Override
		public Type<SainganAsk> type() {
			return TYPE;
		}
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Screen.TYPE, Screen.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Fx.TYPE, Fx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SainganAsk.TYPE, SainganAsk.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> Kota.onAction(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(SainganAsk.TYPE, (payload, context) -> screen(context.player(), payload.open(), SCREEN_SAINGAN,
				TAB_SAINGAN, -1, Saingan.view(context.player())));
	}

	public static void screen(ServerPlayer player, boolean open, int screen, int tab, int npcId, CompoundTag data) {
		Net.send(player, new Screen(open, screen, tab, npcId, data));
	}

	public static void fx(ServerPlayer player, int kind, double x, double y, double z, int a, int b, int life, int[] points) {
		Net.send(player, new Fx(kind, x, y, z, a, b, life, points == null ? new int[0] : points));
	}

	private KotaNet() {
	}
}
