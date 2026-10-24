package dev.kultivator.dunia.tempat.tema;

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
 * Contract C38.T4 (DESIGN 38.T.14.4): the one themed-town payload, server to client, {@code kultivator:tema_fx}
 * {@code (int jenis, long situsId, CompoundTag data)}, at most {@link #MAKS_BYTE} (8 KB) of NBT per packet. Presentation
 * only: the client ({@code TemaClient}, 38TV-F) draws it scaled by {@code EffectQuality} and {@code EpicBudget}; nothing in
 * the world changes and the world weather/time are never touched (a sandstorm is local 38TK state plus client drawing).
 * <p>
 * Data keys per {@code jenis} (all optional unless marked; positions as ints in block coordinates):
 * <ul>
 * <li>{@link #BADAI} sandstorm of one oasis town: {@code mulai} (boolean, required: true start / false end),
 * {@code x}, {@code z} (centre), {@code r} (radius, 600), {@code tick} (game tick it began; the 60 s omen wall starts
 * {@code 1200} ticks before), {@code panjang} (duration, 2400), {@code kuat} (0..1 strength).</li>
 * <li>{@link #FESTIVAL} a festival of the town: {@code acara} (string, required: {@code dewi_laut}, {@code tari_putar},
 * {@code lentera_air}, {@code perahu_naga}, {@code ziarah}, {@code pesta_darah}, ...), {@code mulai} (boolean),
 * {@code tick}, {@code akhir} (game tick it ends).</li>
 * <li>{@link #SPLINE} canal/sea splines, sent once per town per player on entering: {@code jalur} (list of compounds
 * {@code {id: string, kind: "perahu"|"kapal"|"lentera", p: int array x0,y0,z0,x1,y1,z1,...}}); lanterns drift along
 * {@code kind = "lentera"} lanes.</li>
 * <li>{@link #CAHAYA} light phenomena: {@code apa} (string, required: {@code budha} ring, {@code suar} lighthouse beam,
 * {@code awan} cloud sea, {@code kabut} mountain mist, {@code riak} blood-pool ripples), {@code x}, {@code y},
 * {@code z}, {@code tick}, {@code panjang}, {@code nyala} (boolean).</li>
 * <li>{@link #KAPAL_JADWAL} harbour ship schedule for the hub/screen and the arrival scene: {@code kapal} (list of
 * {@code {tambatan: int, asal: string key, datang: long tick, pergi: long tick, keadaan: int}}).</li>
 * </ul>
 * New kinds are appended by 38TK inti only (numbers never reused).
 */
public final class TemaNet {

	/** Sandstorm start/end (38.T.4.6). */
	public static final int BADAI = 0;
	/** Festival start/end (38.T.3.4, 38.T.4.5, 38.T.5.4, 38.T.6.4, 38.T.7.10). */
	public static final int FESTIVAL = 1;
	/** Canal and sea splines of one town (sent once on entering). */
	public static final int SPLINE = 2;
	/** Light phenomena: Buddha ring, lighthouse beam, cloud sea, mist, ripples. */
	public static final int CAHAYA = 3;
	/** Harbour ship schedule. */
	public static final int KAPAL_JADWAL = 4;

	/** Largest NBT body of one packet in bytes ({@code CompoundTag.sizeInBytes()}). */
	public static final int MAKS_BYTE = 8192;

	/** The payload. {@code data} is never null on the wire (an empty compound instead). */
	public record TemaFxPayload(int jenis, long situsId, CompoundTag data) implements CustomPacketPayload {

		public static final Type<TemaFxPayload> TYPE = new Type<>(Kultivator.id("tema_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TemaFxPayload> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.jenis);
					buf.writeLong(p.situsId);
					ByteBufCodecs.COMPOUND_TAG.encode(buf, p.data == null ? new CompoundTag() : p.data);
				},
				buf -> new TemaFxPayload(buf.readVarInt(), buf.readLong(), ByteBufCodecs.COMPOUND_TAG.decode(buf)));

		@Override
		public Type<TemaFxPayload> type() {
			return TYPE;
		}
	}

	private static boolean initialised;

	/** Registers the payload type (both physical sides); from {@link KotaTema#init()}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		PayloadTypeRegistry.clientboundPlay().register(TemaFxPayload.TYPE, TemaFxPayload.CODEC);
	}

	/** True when {@code data} fits one packet ({@link #MAKS_BYTE}). */
	public static boolean muat(CompoundTag data) {
		return data == null || data.sizeInBytes() <= MAKS_BYTE;
	}

	/** Sends to one player; false (nothing sent) when the data is too big or the player cannot receive it. */
	public static boolean kirim(ServerPlayer player, int jenis, long situsId, CompoundTag data) {
		if (player == null || !muat(data) || !ServerPlayNetworking.canSend(player, TemaFxPayload.TYPE)) {
			if (data != null && !muat(data)) {
				Kultivator.LOGGER.warn("tema_fx kind {} for site {} is {} bytes (max {}), not sent", jenis, situsId,
						data.sizeInBytes(), MAKS_BYTE);
			}
			return false;
		}
		ServerPlayNetworking.send(player, new TemaFxPayload(jenis, situsId, data == null ? new CompoundTag() : data));
		return true;
	}

	/** Sends to every player of {@code level} within {@code radius} blocks of {@code centre}; returns how many got it. */
	public static int kirimSekitar(ServerLevel level, Vec3 centre, double radius, int jenis, long situsId, CompoundTag data) {
		if (level == null || centre == null || !muat(data)) {
			return 0;
		}
		double r2 = radius * radius;
		int sent = 0;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(centre) <= r2 && kirim(player, jenis, situsId, data)) {
				sent++;
			}
		}
		return sent;
	}

	private TemaNet() {
	}
}
