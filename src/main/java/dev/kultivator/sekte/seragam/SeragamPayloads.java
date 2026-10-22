package dev.kultivator.sekte.seragam;

import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The payloads of module S (DESIGN 36.2.8, 36.4), registered by {@link Seragam#init()} with {@code PayloadTypeRegistry};
 * nothing edits {@code network/Payloads} or {@code Net}.
 * <p>
 * Keys name whose design a payload carries: {@code u:<owner uuid>} a player's hall, {@code npc:<sect id>} one of module
 * D's NPC sects, {@code uji:<player uuid>} a test banner. Server to client: {@link LambangSync} (a design, its banners
 * and decals), {@link LambangForget}, {@link LambangOpen} (open the designer) and {@link SeragamDebug}. Client to server:
 * {@link LambangSet} (save a design) and {@link LambangRequest} (the hub card asks for the designer).
 */
public final class SeragamPayloads {

	/** Banner kinds: on S's gate poles, on S's formation poles, on T's arena poles, a free-standing standard (own pole). */
	public static final int BANNER_GATE = 0;
	public static final int BANNER_FORMATION = 1;
	public static final int BANNER_ARENA = 2;
	public static final int BANNER_STANDARD = 3;
	/** Wire caps. */
	public static final int MAX_BANNERS = 16;
	public static final int MAX_DECALS = 8;

	/**
	 * A banner: {@code pos} the pole's gilt cap (or, for a standard, the ground cell it stands on), {@code yaw} the
	 * direction the arm and cloth hang toward in 1/256 turns (0 = south, +z; 64 = west), {@code kind} a {@code BANNER_*}.
	 */
	public record Banner(BlockPos pos, int yaw, int kind) {
	}

	/** An emblem decal on the ground at {@code pos} (the block above the floor), {@code size} blocks across (1-9). */
	public record Decal(BlockPos pos, int size) {
	}

	// ================================================================ server to client

	/**
	 * A design and where it shows: {@code version} rises with every change (the client rebuilds the textures),
	 * {@code dim} the dimension id of the banners and decals, {@code maxBanners} and {@code animRange} the server's config,
	 * {@code ttl} ticks before the client drops it by itself (0 = keep until forgotten).
	 */
	public record LambangSync(String key, int version, String dim, SeragamData data, List<Banner> banners, List<Decal> decals, int maxBanners,
							  int animRange, int ttl) implements CustomPacketPayload {
		public static final Type<LambangSync> TYPE = new Type<>(Kultivator.id("sekte_seragam_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LambangSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.key, 96);
					buf.writeVarInt(p.version);
					buf.writeUtf(p.dim, 128);
					p.data.write(buf);
					int nb = Math.min(MAX_BANNERS, p.banners.size());
					buf.writeByte(nb);
					for (int i = 0; i < nb; i++) {
						Banner b = p.banners.get(i);
						buf.writeBlockPos(b.pos());
						buf.writeByte(b.yaw());
						buf.writeByte(b.kind());
					}
					int nd = Math.min(MAX_DECALS, p.decals.size());
					buf.writeByte(nd);
					for (int i = 0; i < nd; i++) {
						Decal d = p.decals.get(i);
						buf.writeBlockPos(d.pos());
						buf.writeByte(d.size());
					}
					buf.writeByte(p.maxBanners);
					buf.writeByte(p.animRange);
					buf.writeVarInt(p.ttl);
				},
				buf -> {
					String key = buf.readUtf(96);
					int version = buf.readVarInt();
					String dim = buf.readUtf(128);
					SeragamData data = SeragamData.read(buf);
					int nb = Math.min(MAX_BANNERS, buf.readUnsignedByte());
					List<Banner> banners = new ArrayList<>(nb);
					for (int i = 0; i < nb; i++) {
						banners.add(new Banner(buf.readBlockPos(), buf.readUnsignedByte(), buf.readUnsignedByte()));
					}
					int nd = Math.min(MAX_DECALS, buf.readUnsignedByte());
					List<Decal> decals = new ArrayList<>(nd);
					for (int i = 0; i < nd; i++) {
						decals.add(new Decal(buf.readBlockPos(), buf.readUnsignedByte()));
					}
					return new LambangSync(key, version, dim, data, banners, decals, buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readVarInt());
				});

		@Override
		public Type<LambangSync> type() {
			return TYPE;
		}
	}

	/** The player left the range of {@code key}'s hall: drop its banners and decals (the robe colours stay cached). */
	public record LambangForget(String key) implements CustomPacketPayload {
		public static final Type<LambangForget> TYPE = new Type<>(Kultivator.id("sekte_seragam_forget"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LambangForget> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeUtf(p.key, 96), buf -> new LambangForget(buf.readUtf(96)));

		@Override
		public Type<LambangForget> type() {
			return TYPE;
		}
	}

	/**
	 * Opens the designer for {@code key} (the {@link LambangSync} came just before): {@code freeLeft} free changes left
	 * today, {@code cost} PK of a further change, {@code pool} PK in the hall pool, {@code flags} {@link #OPEN_EDIT} (the
	 * viewer is the master) and {@link #OPEN_HALL} (a hall exists).
	 */
	public record LambangOpen(String key, int freeLeft, int cost, int pool, int flags) implements CustomPacketPayload {
		public static final int OPEN_EDIT = 1;
		public static final int OPEN_HALL = 2;
		public static final Type<LambangOpen> TYPE = new Type<>(Kultivator.id("sekte_seragam_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LambangOpen> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUtf(p.key, 96);
					buf.writeVarInt(p.freeLeft);
					buf.writeVarInt(p.cost);
					buf.writeVarInt(p.pool);
					buf.writeByte(p.flags);
				},
				buf -> new LambangOpen(buf.readUtf(96), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUnsignedByte()));

		@Override
		public Type<LambangOpen> type() {
			return TYPE;
		}
	}

	/** Test commands for the client: {@link #DEBUG_ATLAS} writes every live emblem texture to {@code screenshots/}. */
	public record SeragamDebug(int action) implements CustomPacketPayload {
		public static final int DEBUG_ATLAS = 1;
		public static final Type<SeragamDebug> TYPE = new Type<>(Kultivator.id("sekte_seragam_debug"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SeragamDebug> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeByte(p.action), buf -> new SeragamDebug(buf.readUnsignedByte()));

		@Override
		public Type<SeragamDebug> type() {
			return TYPE;
		}
	}

	// ================================================================ client to server

	/** The master saves a design from the designer (checked, clamped and charged on the server). */
	public record LambangSet(SeragamData data) implements CustomPacketPayload {
		public static final Type<LambangSet> TYPE = new Type<>(Kultivator.id("sekte_seragam_set"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LambangSet> CODEC = StreamCodec.of(
				(buf, p) -> p.data.write(buf), buf -> new LambangSet(SeragamData.read(buf)));

		@Override
		public Type<LambangSet> type() {
			return TYPE;
		}
	}

	/** The hub card asks for the designer of the player's own hall. */
	public record LambangRequest(int unused) implements CustomPacketPayload {
		public static final Type<LambangRequest> TYPE = new Type<>(Kultivator.id("sekte_seragam_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LambangRequest> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeByte(p.unused), buf -> new LambangRequest(buf.readUnsignedByte()));

		@Override
		public Type<LambangRequest> type() {
			return TYPE;
		}
	}

	private SeragamPayloads() {
	}
}
