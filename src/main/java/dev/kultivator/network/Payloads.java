package dev.kultivator.network;

import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Every custom packet of the mod. */
public final class Payloads {

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
		return new CustomPacketPayload.Type<>(Kultivator.id(name));
	}

	// ================================================================ server -> client

	/**
	 * The player's own cultivation: {@code CultivationData.saveForClient()} plus the server game time it was taken at
	 * (to turn the saved "ready at" cooldown times into "ticks left").
	 */
	public record SyncSelf(CompoundTag data, long gameTime) implements CustomPacketPayload {
		public static final Type<SyncSelf> TYPE = typeOf("sync_self");
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncSelf> CODEC = StreamCodec.of(
				(buf, p) -> {
					ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data);
					buf.writeLong(p.gameTime);
				},
				buf -> new SyncSelf(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf), buf.readLong()));

		@Override
		public Type<SyncSelf> type() {
			return TYPE;
		}
	}

	/**
	 * Play effect {@code kind} (an {@code FxType} ordinal) at {@code x,y,z}. {@code tx,ty,tz} is a second point (target,
	 * direction or sky origin) and {@code a}, {@code b}, {@code f} are effect specific (see {@code FxType}).
	 */
	public record Fx(int kind, double x, double y, double z, double tx, double ty, double tz, int a, int b, float f)
			implements CustomPacketPayload {
		public static final Type<Fx> TYPE = typeOf("fx");
		public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.kind);
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					buf.writeDouble(p.tx);
					buf.writeDouble(p.ty);
					buf.writeDouble(p.tz);
					buf.writeInt(p.a);
					buf.writeInt(p.b);
					buf.writeFloat(p.f);
				},
				buf -> new Fx(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
						buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readInt(), buf.readFloat()));

		@Override
		public Type<Fx> type() {
			return TYPE;
		}
	}

	/** Divine Sense: ore positions to outline for {@code ticks} ticks, with an RGB colour per position. */
	public record OrePings(List<BlockPos> positions, List<Integer> colors, int ticks) implements CustomPacketPayload {
		public static final int MAX = 256;
		public static final Type<OrePings> TYPE = typeOf("ore_pings");
		public static final StreamCodec<RegistryFriendlyByteBuf, OrePings> CODEC = StreamCodec.of(
				(buf, p) -> {
					int count = Math.min(MAX, Math.min(p.positions.size(), p.colors.size()));
					buf.writeVarInt(count);
					for (int i = 0; i < count; i++) {
						buf.writeBlockPos(p.positions.get(i));
						buf.writeInt(p.colors.get(i));
					}
					buf.writeVarInt(p.ticks);
				},
				buf -> {
					int count = Math.min(MAX, buf.readVarInt());
					List<BlockPos> positions = new ArrayList<>(count);
					List<Integer> colors = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						positions.add(buf.readBlockPos());
						colors.add(buf.readInt());
					}
					return new OrePings(positions, colors, buf.readVarInt());
				});

		@Override
		public Type<OrePings> type() {
			return TYPE;
		}
	}

	/** Opens a client screen ({@code screen} is one of the constants, {@code arg} screen specific). */
	public record OpenScreen(int screen, int arg) implements CustomPacketPayload {
		/** The cultivation info screen on tab {@code arg}. */
		public static final int INFO = 0;
		/** The Dao choice screen. */
		public static final int CHOOSE_DAO = 1;
		/** A disciple's screen; {@code arg} = its entity id (details follow in a DiscipleInfo). */
		public static final int DISCIPLE = 2;
		/** The Sect Hall screen ({@code arg} 0); its details come first in a {@link HallInfo}. */
		public static final int HALL = 3;
		/** The Mode Kehancuran warning: {@code arg} 0 = the player's own oath, otherwise the disciple's entity id. */
		public static final int KEHANCURAN_CONFIRM = 4;
		/** The sect template screen (DESIGN 21.12); {@code arg} = the template to select first (0 = none). */
		public static final int SECT_TEMPLATE = 5;
		/** The Kitab Teknik (DESIGN 22.12.1); {@code arg} = the net id to focus, or −1. */
		public static final int CODEX = 6;
		/** The Dunia screen (DESIGN 25.10); {@code arg} = the tab: 0 Alam, 1 Pusaka, 2 Musuh, 3 Desa. */
		public static final int DUNIA = 7;

		/** Tabs of {@link #DUNIA}. */
		public static final int DUNIA_ALAM = 0;
		public static final int DUNIA_PUSAKA = 1;
		public static final int DUNIA_MUSUH = 2;
		public static final int DUNIA_DESA = 3;

		/**
		 * The Keabadian screen (DESIGN 26.10, phase 9); {@code arg} = the tab: 0 Alam, 1 Purba, 2 Bencana, 3 Batin, 4 Ciptaan,
		 * 5 Lelang.
		 */
		public static final int ABADI = 8;

		/** Tabs of {@link #ABADI}. */
		public static final int ABADI_ALAM = 0;
		public static final int ABADI_PURBA = 1;
		public static final int ABADI_BENCANA = 2;
		public static final int ABADI_BATIN = 3;
		public static final int ABADI_CIPTAAN = 4;
		public static final int ABADI_LELANG = 5;

		/** Tab {@code arg} of {@link #INFO}: the Jalan tablet (DESIGN 19.2.2, {@code /kultivasi jalan}). Same as {@code CultivationScreen.TAB_JALAN}. */
		public static final int TAB_JALAN = 5;
		/** Tab {@code arg} of {@link #INFO}: the Kitab Alkimia (DESIGN 19.2.2, {@code /kultivasi resep}). Same as {@code CultivationScreen.TAB_ALKIMIA}. */
		public static final int TAB_ALKIMIA = 6;

		public static final Type<OpenScreen> TYPE = typeOf("open_screen");
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreen> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.screen);
					buf.writeVarInt(p.arg);
				},
				buf -> new OpenScreen(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<OpenScreen> type() {
			return TYPE;
		}
	}

	/** Details of one disciple for its screen: {@code data} is the disciple's saved data plus live values. */
	public record DiscipleInfo(int entityId, CompoundTag data) implements CustomPacketPayload {
		public static final Type<DiscipleInfo> TYPE = typeOf("disciple_info");
		public static final StreamCodec<RegistryFriendlyByteBuf, DiscipleInfo> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.entityId);
					ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data);
				},
				buf -> new DiscipleInfo(buf.readVarInt(), ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<DiscipleInfo> type() {
			return TYPE;
		}
	}

	/**
	 * Everything the Sect Hall screen shows (DESIGN 20.2.2): composed by {@code sect.hall.Halls} plus the sections of the
	 * other modules ({@code Halls.addInfoProvider}); {@code cfg.kehancuran} decides whether the Segel Merah tablet is shown.
	 */
	public record HallInfo(CompoundTag data) implements CustomPacketPayload {
		public static final Type<HallInfo> TYPE = typeOf("hall_info");
		public static final StreamCodec<RegistryFriendlyByteBuf, HallInfo> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new HallInfo(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<HallInfo> type() {
			return TYPE;
		}
	}

	/**
	 * A compiled sect footprint preview for the hologram and the template screen (DESIGN 21.12, 21.24): the
	 * {@code PreviewData.toTag()} of the engine with the keys {@code t, rot, glaze, fp, axis, comps, zones, cut, obst, warn,
	 * snap, bill, exact, ops}. An empty tag clears the hologram. Read by {@code client.sectbuild.BuildClient}.
	 */
	public record BuildPreview(CompoundTag data) implements CustomPacketPayload {
		public static final Type<BuildPreview> TYPE = typeOf("build_preview");
		public static final StreamCodec<RegistryFriendlyByteBuf, BuildPreview> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new BuildPreview(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<BuildPreview> type() {
			return TYPE;
		}
	}

	/**
	 * One visual event of a technique spec (DESIGN 22.4.1): every client draws the spec's FX layers of {@code phase} from
	 * the registry with its own quality and budget; one event per phase occurrence, never per particle.
	 * {@code spec} = net id; {@code x,y,z} the event position, {@code tx,ty,tz} the second point; {@code tier} = caster
	 * grade tier 0-5 | mastery level << 3; {@code flags} = the {@code FLAG_*} bits; {@code seed} draws the same shape on
	 * every client; {@code caster} = entity id auras and forms attach to.
	 */
	public record TechFx(int spec, byte phase, double x, double y, double z, double tx, double ty, double tz, float radius,
			float length, byte tier, byte flags, short count, int seed, int caster, short duration) implements CustomPacketPayload {
		/** A Kehancuran cast (CRACKS layer, BLOCK_CRUMBLE at the impact). */
		public static final int FLAG_KEHANCURAN = 1;
		public static final int FLAG_ECHO = 2;
		/** Server under load: draw ×0.5 and no displays. */
		public static final int FLAG_LOW_LOAD = 4;
		public static final int FLAG_BOSS_HIT = 8;
		public static final int FLAG_DISCIPLE = 16;
		public static final int FLAG_ALLY = 32;
		public static final int FLAG_SHOWCASE = 64;

		public static final Type<TechFx> TYPE = typeOf("tech_fx");
		public static final StreamCodec<RegistryFriendlyByteBuf, TechFx> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.spec);
					buf.writeByte(p.phase);
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					buf.writeDouble(p.tx);
					buf.writeDouble(p.ty);
					buf.writeDouble(p.tz);
					buf.writeFloat(p.radius);
					buf.writeFloat(p.length);
					buf.writeByte(p.tier);
					buf.writeByte(p.flags);
					buf.writeShort(p.count);
					buf.writeInt(p.seed);
					buf.writeVarInt(p.caster);
					buf.writeShort(p.duration);
				},
				buf -> new TechFx(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
						buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readByte(), buf.readShort(),
						buf.readInt(), buf.readVarInt(), buf.readShort()));

		/** The caster grade tier 0-5. */
		public int gradeTier() {
			return tier & 7;
		}

		/** The caster's mastery level 0-4 of this spec. */
		public int mastery() {
			return (tier >> 3) & 7;
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<TechFx> type() {
			return TYPE;
		}
	}

	/**
	 * One beat of an epic moment (DESIGN 23.4.1): {@code moment} and {@code beat} from {@code epic.EpicIds};
	 * {@code subject} the entity id the scene centres on (−1 for a place) and {@code viewer} the entity id of the only
	 * player who may get camera shots, bars, FULL cards and music (−1 for none). Clients always position from
	 * {@code x,y,z}. {@code at} is the server game time the beat lands, {@code seed} builds the same shapes everywhere,
	 * {@code a}, {@code b}, {@code f} are beat specific, {@code s} the scene scale 0-10, {@code flags} the
	 * {@code EpicIds.FLAG_*} bits. Unknown moments or beats are ignored by the client.
	 */
	public record EpicMoment(int moment, int beat, int subject, int viewer, double x, double y, double z, long at, int seed,
			int a, int b, float f, int s, int flags) implements CustomPacketPayload {
		public static final Type<EpicMoment> TYPE = typeOf("epic_moment");
		public static final StreamCodec<RegistryFriendlyByteBuf, EpicMoment> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.moment);
					buf.writeByte(p.beat);
					buf.writeVarInt(p.subject + 1);
					buf.writeVarInt(p.viewer + 1);
					buf.writeDouble(p.x);
					buf.writeDouble(p.y);
					buf.writeDouble(p.z);
					buf.writeVarLong(p.at);
					buf.writeInt(p.seed);
					buf.writeVarInt(p.a);
					buf.writeVarInt(p.b);
					buf.writeFloat(p.f);
					buf.writeByte(p.s);
					buf.writeVarInt(p.flags);
				},
				buf -> new EpicMoment(buf.readVarInt(), buf.readUnsignedByte(), buf.readVarInt() - 1, buf.readVarInt() - 1,
						buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarLong(), buf.readInt(), buf.readVarInt(),
						buf.readVarInt(), buf.readFloat(), buf.readUnsignedByte(), buf.readVarInt()));

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<EpicMoment> type() {
			return TYPE;
		}
	}

	/**
	 * The sect formations near one player (DESIGN 24.12): {@code cores [{id, net, x, y, z, rot, r, st, mem, ext}]} within 96
	 * blocks of the same dimension and {@code conceal [entityId...]}. Sent only on change, at most once per second; read by
	 * {@code client.formasi.FormasiClient.onSync}.
	 */
	public record FormasiSync(CompoundTag data) implements CustomPacketPayload {
		public static final Type<FormasiSync> TYPE = typeOf("formasi_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, FormasiSync> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new FormasiSync(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<FormasiSync> type() {
			return TYPE;
		}
	}

	/**
	 * One phase-8 module's data for the Dunia screen and its pages (DESIGN 25.2 item 2): sent only when a screen opens
	 * (the module answers its "sync" action) or the data changes, at most {@link #MAX_BYTES}. {@code KultivatorClient}
	 * routes {@code channel} to {@code client.alam.AlamClient}, {@code client.musuh.MusuhClient},
	 * {@code client.tempa.TempaClient} or {@code client.desa.DesaClient}{@code .onSync(data)}. Send it with {@link #send}.
	 */
	public record DuniaSync(int channel, CompoundTag data) implements CustomPacketPayload {
		public static final int CH_ALAM = 0;
		public static final int CH_MUSUH = 1;
		public static final int CH_TEMPA = 2;
		public static final int CH_DESA = 3;
		/** Phase 9 (DESIGN 26.14 item 2): routed to {@code client.abadi.AbadiClient}, {@code client.purba.PurbaClient}, {@code client.batin.BatinClient}. */
		public static final int CH_ABADI = 4;
		public static final int CH_PURBA = 5;
		public static final int CH_BATIN = 6;
		/** The number of channels (the client keeps one version counter per channel). */
		public static final int CHANNELS = 7;
		/** The size budget of one sync (DESIGN 25.13). */
		public static final int MAX_BYTES = 8192;

		public static final Type<DuniaSync> TYPE = typeOf("dunia_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, DuniaSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.channel);
					ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data);
				},
				buf -> new DuniaSync(buf.readVarInt(), ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		/**
		 * Sends {@code data} on {@code channel} to the player (skipped for vanilla clients). Data over {@link #MAX_BYTES} is
		 * not sent: a warning is logged and the client gets {@code {too_big: true}} instead, so the budget bug is visible.
		 */
		public static void send(net.minecraft.server.level.ServerPlayer player, int channel, CompoundTag data) {
			CompoundTag out = data == null ? new CompoundTag() : data;
			if (out.sizeInBytes() > MAX_BYTES) {
				Kultivator.LOGGER.warn("DuniaSync channel {} is {} bytes, over the {} byte budget; not sent", channel, out.sizeInBytes(), MAX_BYTES);
				out = new CompoundTag();
				out.putBoolean("too_big", true);
			}
			Net.send(player, new DuniaSync(channel, out));
		}

		@Override
		public Type<DuniaSync> type() {
			return TYPE;
		}
	}

	// ================================================================ client -> server

	/** A key press or screen button: {@code action} from {@link Actions}, plus an int and a short text argument. */
	public record Action(int action, int arg, String text) implements CustomPacketPayload {
		public static final Type<Action> TYPE = typeOf("action");
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.action);
					buf.writeVarInt(p.arg);
					buf.writeUtf(p.text, 64);
				},
				buf -> new Action(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));

		public static Action of(int action) {
			return new Action(action, 0, "");
		}

		public static Action of(int action, int arg) {
			return new Action(action, arg, "");
		}

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	private Payloads() {
	}
}
