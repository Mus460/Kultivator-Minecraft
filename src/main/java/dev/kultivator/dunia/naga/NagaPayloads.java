package dev.kultivator.dunia.naga;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The payloads of module B (DESIGN 34.6 B, rule C2: registered here with {@link PayloadTypeRegistry}, never in
 * {@code network/Payloads}). Everything else the client draws comes from the synced entity data of
 * {@link NagaLiarEntity} and {@link KesengsaraanEntity}.
 */
public final class NagaPayloads {

	/**
	 * Server to client: the Pertarungan Kehendak of {@code session} against dragon {@code dragonId}. {@code state}
	 * {@link #CONTEST_START} (draw the ring for {@code ticks}; {@code seed} and {@code kind} drive the dragon's wandering
	 * will, {@code value} its arc width in degrees), {@link #CONTEST_WON} / {@link #CONTEST_LOST} ({@code value} = the
	 * overlap in permille) or {@link #CONTEST_ABORT}.
	 */
	public record Contest(int session, int dragonId, int kind, int seed, int ticks, int state, int value) implements CustomPacketPayload {
		public static final Type<Contest> TYPE = new Type<>(Kultivator.id("naga_contest"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Contest> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.session);
					buf.writeVarInt(p.dragonId);
					buf.writeVarInt(p.kind);
					buf.writeInt(p.seed);
					buf.writeVarInt(p.ticks);
					buf.writeVarInt(p.state);
					buf.writeVarInt(p.value);
				},
				buf -> new Contest(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(),
						buf.readVarInt()));

		@Override
		public Type<Contest> type() {
			return TYPE;
		}
	}

	public static final int CONTEST_START = 0;
	public static final int CONTEST_WON = 1;
	public static final int CONTEST_LOST = 2;
	public static final int CONTEST_ABORT = 3;

	/**
	 * Client to server: during contest {@code session} the player's arc lay on the dragon's will for {@code permille}
	 * of the last {@code samples} client ticks (sent every 10 ticks; the server checks the session, time and distance).
	 */
	public record Report(int session, int permille, int samples) implements CustomPacketPayload {
		public static final Type<Report> TYPE = new Type<>(Kultivator.id("naga_report"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Report> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.session);
					buf.writeVarInt(p.permille);
					buf.writeVarInt(p.samples);
				},
				buf -> new Report(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Report> type() {
			return TYPE;
		}
	}

	/**
	 * Server to client: a world moment of module B at {@code x, y, z} for entity {@code entityId}: {@code what} one of
	 * {@link #MOMENT_SIGHTED} (a wild Jiao appears: a distant roar, its shadow), {@link #MOMENT_SUBDUED} (beat 42),
	 * {@link #MOMENT_EVOLVED} (beat 43; {@code tier}, {@code line} 0 wolf / 1 fox / 2 jiao), {@link #MOMENT_FAILED} (a
	 * failed tribulation), {@link #MOMENT_SUMMON} / {@link #MOMENT_DISMISS} (the pearl), {@link #MOMENT_HOWL} (Lolongan
	 * Bulan), {@link #MOMENT_FOXFIRE} (Api Rubah Langit, {@code tier} = target count), {@link #MOMENT_ROAR}, and the detail
	 * pass moments {@link #MOMENT_ENRAGED}, {@link #MOMENT_WHIRL}, {@link #MOMENT_VEIL}, {@link #MOMENT_THUNDER_MARK},
	 * {@link #MOMENT_TRIB_START} and {@link #MOMENT_GUARD}. {@code own}
	 * is true for the player the moment belongs to (their camera may move, the others only see it).
	 */
	public record Moment(int what, int entityId, double x, double y, double z, int kind, int tier, int line, int seed, boolean own)
			implements CustomPacketPayload {
		public static final Type<Moment> TYPE = new Type<>(Kultivator.id("naga_moment"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Moment> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.what);
					buf.writeVarInt(p.entityId);
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					buf.writeVarInt(p.kind);
					buf.writeVarInt(p.tier);
					buf.writeVarInt(p.line);
					buf.writeInt(p.seed);
					buf.writeBoolean(p.own);
				},
				buf -> new Moment(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(),
						buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readBoolean()));

		public Vec3 pos() {
			return new Vec3(x, y, z);
		}

		@Override
		public Type<Moment> type() {
			return TYPE;
		}
	}

	/**
	 * Server to client, once a second per wild Jiao for players 48-640 blocks away: where it flies ({@code x, y, z}, its
	 * velocity in blocks per tick), so the client can draw a sky-projected silhouette where the entity itself is not
	 * tracked or lies beyond the fog (DESIGN 34.5.11 level of detail). {@code flags} are the entity's synced flags.
	 */
	public record Far(int entityId, int kind, float x, float y, float z, float vx, float vy, float vz, int flags) implements CustomPacketPayload {
		public static final Type<Far> TYPE = new Type<>(Kultivator.id("naga_far"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Far> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.entityId);
					buf.writeVarInt(p.kind);
					buf.writeFloat(p.x);
					buf.writeFloat(p.y);
					buf.writeFloat(p.z);
					buf.writeFloat(p.vx);
					buf.writeFloat(p.vy);
					buf.writeFloat(p.vz);
					buf.writeVarInt(p.flags);
				},
				buf -> new Far(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
						buf.readFloat(), buf.readFloat(), buf.readVarInt()));

		@Override
		public Type<Far> type() {
			return TYPE;
		}
	}

	public static final int MOMENT_SIGHTED = 1;
	public static final int MOMENT_SUBDUED = 2;
	public static final int MOMENT_EVOLVED = 3;
	public static final int MOMENT_FAILED = 4;
	public static final int MOMENT_SUMMON = 5;
	public static final int MOMENT_DISMISS = 6;
	public static final int MOMENT_HOWL = 7;
	public static final int MOMENT_FOXFIRE = 8;
	public static final int MOMENT_ROAR = 9;
	/** Amukan Naga: a wild Jiao at half health (a stormy sky grade near it, a banner, its eyes burn). */
	public static final int MOMENT_ENRAGED = 10;
	/** Pusaran Samudra at {@code x, y, z}: the whirl lives {@code seed} ticks, {@code tier} = its radius in tenths. */
	public static final int MOMENT_WHIRL = 11;
	/** Tirai Awan: the cloud veil bursts from {@code x, y, z} ({@code tier} = its radius). */
	public static final int MOMENT_VEIL = 12;
	/** Hujan Petir Langit: a strike mark at {@code x, y, z} that falls in {@code seed} ticks ({@code tier} = radius in tenths). */
	public static final int MOMENT_THUNDER_MARK = 13;
	/** A beast tribulation begins over {@code x, y, z} and lasts about {@code seed} ticks (the sky darkens near it). */
	public static final int MOMENT_TRIB_START = 14;
	/** Penjaga Raga: the owner's beasts gather at the body at {@code x, y, z} ({@code tier} = how many guard). */
	public static final int MOMENT_GUARD = 15;

	/** Registers the payload types (both physical sides) and the server receiver. */
	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Contest.TYPE, Contest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Moment.TYPE, Moment.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Far.TYPE, Far.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Report.TYPE, Report.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Report.TYPE, (payload, context) -> WillContest.onReport(context.player(), payload));
	}

	/** Sends {@code payload} to {@code player} when the client can take it (an unmodded client is skipped). */
	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Sends a moment to every player within {@code radius} of {@code pos} in the player's level; {@code owner} gets own = true. */
	static void moment(net.minecraft.server.level.ServerLevel level, int what, int entityId, Vec3 pos, int kind, int tier, int line, int seed,
			@org.jetbrains.annotations.Nullable ServerPlayer owner, double radius) {
		double r2 = radius * radius;
		for (ServerPlayer player : java.util.List.copyOf(level.players())) {
			if (player.position().distanceToSqr(pos) <= r2 || player == owner) {
				send(player, new Moment(what, entityId, pos.x, pos.y, pos.z, kind, tier, line, seed, player == owner));
			}
		}
	}

	private NagaPayloads() {
	}
}
