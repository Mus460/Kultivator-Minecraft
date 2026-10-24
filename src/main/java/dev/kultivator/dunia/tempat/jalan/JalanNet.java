package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The road map payload of DESIGN 38.D.12 (owner 38W): the roads a player knows (each edge whose milestone he touched)
 * as ink polylines for the world map ({@code client/dunia/tempat/jalan/JalanPeta}, drawn through
 * {@code PetaPainter.addOverlay}). Sent on join and after every new milestone; at most {@link #MAKS} edges of at most
 * {@link #MAKS_TITIK} points (well under 32 KB). It also carries the district plan of every capital the player knows
 * ({@link Denah}, at most {@link #MAKS_DENAH} boxes) so the map shows the 3 x 3 districts at close zoom.
 *
 * <p>{@link Papan} ({@code kultivator:jalan_papan}) is the carved text of one milestone (DESIGN 38.C.6.3, 38.U.4): the
 * road's name, the places both ways with their distance in li and the road's heading toward each, the next rest post
 * and the stela's carved couplet. Sent by {@link PapanTonggak} when a player walks up to a milestone; the client draws
 * it as a stone billboard over the stela with arrows turned to the viewer ({@code client/dunia/tempat/jalan/JalanPapan}).
 */
public final class JalanNet {

	public static final int MAKS = 256;
	public static final int MAKS_TITIK = 48;
	public static final int MAKS_DENAH = 64;

	/** One known road: class (0 imperial, 1 village, 2 footpath) and its polyline in block coordinates. */
	public record Garis(int kelas, int[] xs, int[] zs) {
	}

	/** One district box of a known capital (block coordinates, inclusive) with its district id ({@code pasar}, ...). */
	public record Denah(String distrik, int x0, int z0, int x1, int z1) {
	}

	/** Every known road and capital district of the receiving player (replaces the client's lists). */
	public record Peta(List<Garis> garis, List<Denah> denah) implements CustomPacketPayload {

		public static final Type<Peta> TYPE = new Type<>(Kultivator.id("jalan_peta"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Peta> CODEC = StreamCodec.of(
				(buf, p) -> {
					int n = Math.min(MAKS, p.garis.size());
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						Garis g = p.garis.get(i);
						buf.writeVarInt(g.kelas());
						int m = Math.min(MAKS_TITIK, Math.min(g.xs().length, g.zs().length));
						buf.writeVarInt(m);
						for (int j = 0; j < m; j++) {
							buf.writeVarInt(g.xs()[j]);
							buf.writeVarInt(g.zs()[j]);
						}
					}
					int d = Math.min(MAKS_DENAH, p.denah.size());
					buf.writeVarInt(d);
					for (int i = 0; i < d; i++) {
						Denah k = p.denah.get(i);
						buf.writeUtf(k.distrik(), 32);
						buf.writeVarInt(k.x0());
						buf.writeVarInt(k.z0());
						buf.writeVarInt(k.x1());
						buf.writeVarInt(k.z1());
					}
				},
				buf -> {
					int n = Math.max(0, Math.min(MAKS, buf.readVarInt()));
					List<Garis> list = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						int kelas = buf.readVarInt();
						int m = Math.max(0, Math.min(MAKS_TITIK, buf.readVarInt()));
						int[] xs = new int[m];
						int[] zs = new int[m];
						for (int j = 0; j < m; j++) {
							xs[j] = buf.readVarInt();
							zs[j] = buf.readVarInt();
						}
						list.add(new Garis(kelas, xs, zs));
					}
					int d = Math.max(0, Math.min(MAKS_DENAH, buf.readVarInt()));
					List<Denah> plans = new ArrayList<>(d);
					for (int i = 0; i < d; i++) {
						plans.add(new Denah(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
					}
					return new Peta(List.copyOf(list), List.copyOf(plans));
				});

		@Override
		public Type<Peta> type() {
			return TYPE;
		}
	}

	/**
	 * One direction carved on a milestone: a place ({@code nama}), its distance in li along the road and the road's
	 * heading toward it at the stela ({@code sudut}, radians of atan2(dz, dx)); {@code pos} true for the next rest post.
	 */
	public record Arah(Component nama, float li, float sudut, boolean pos) {
	}

	/** The carved text of the milestone whose anchor stands at {@code at}; the board floats {@code tinggi} blocks above it. */
	public record Papan(BlockPos at, float tinggi, int kelas, Component judul, List<Arah> arah, Component ukiran)
			implements CustomPacketPayload {

		public static final int MAKS_ARAH = 4;
		public static final Type<Papan> TYPE = new Type<>(Kultivator.id("jalan_papan"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Papan> CODEC = StreamCodec.of(
				(buf, p) -> {
					BlockPos.STREAM_CODEC.encode(buf, p.at);
					buf.writeFloat(p.tinggi);
					buf.writeVarInt(p.kelas);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.judul);
					int n = Math.min(MAKS_ARAH, p.arah.size());
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						Arah a = p.arah.get(i);
						ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, a.nama());
						buf.writeFloat(a.li());
						buf.writeFloat(a.sudut());
						buf.writeBoolean(a.pos());
					}
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.ukiran);
				},
				buf -> {
					BlockPos at = BlockPos.STREAM_CODEC.decode(buf);
					float tinggi = buf.readFloat();
					int kelas = buf.readVarInt();
					Component judul = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
					int n = Math.max(0, Math.min(MAKS_ARAH, buf.readVarInt()));
					List<Arah> arah = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						arah.add(new Arah(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
					}
					Component ukiran = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
					return new Papan(at, tinggi, kelas, judul, List.copyOf(arah), ukiran);
				});

		@Override
		public Type<Papan> type() {
			return TYPE;
		}
	}

	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(Peta.TYPE, Peta.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Papan.TYPE, Papan.CODEC);
	}

	static void send(ServerPlayer player, List<Garis> garis, List<Denah> denah) {
		Peta payload = new Peta(garis, denah);
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	static void send(ServerPlayer player, Papan payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private JalanNet() {
	}
}
