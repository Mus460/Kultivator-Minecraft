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
