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

	/**
	 * The real encoded size of {@code tag} on the wire (what {@link ByteBufCodecs#TRUSTED_COMPOUND_TAG} writes). Use this
	 * for every byte budget: {@code CompoundTag.sizeInBytes()} is a heap estimate, about 7x the wire size, and wrongly
	 * blocked the Abadi, Batin and Desa syncs.
	 */
	public static int wireBytes(CompoundTag tag) {
		if (tag == null) {
			return 1;
		}
		io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer(256);
		try {
			ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, tag);
			return buf.readableBytes();
		} finally {
			buf.release();
		}
	}

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

		/** Phase 10b (DESIGN 29.2.2): the Titah Langit jade-scroll menu; its data comes first in a {@link TitahSync}. */
		public static final int TITAH = 9;

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
	 * One visual event of a technique spec (DESIGN 22.4.1, v2 in 27.4): every client draws the spec's FX layers of
	 * {@code phase} from the registry with its own quality and budget; one event per phase occurrence, never per particle.
	 * {@code spec} = net id; {@code x,y,z} the event position, {@code tx,ty,tz} the second point; {@code tier} = caster
	 * grade tier 0-5 | mastery level << 3; {@code flags} = the {@code FLAG_*} bits; {@code seed} draws the same shape on
	 * every client; {@code caster} = entity id auras and forms attach to.
	 * <p>
	 * v2: {@code target} = the entity id of the victim, target or bound entity (-1 none); {@code index} / {@code of} = this
	 * event's place in a multi-point or per-projectile group ({@code of} 1 = single; {@code index} -1 with {@code of} > 1 =
	 * "the client places {@code of} copies on a ring of radius {@code aux / 100}"); {@code aux} by flag or phase
	 * ({@link #FLAG_STAGE} stage index, {@link #FLAG_TWO_DAO} second Dao RGB, {@link #FLAG_VOLLEY} spread mode, PHANTOM the
	 * phantom kind, a second linked entity id for karma pairs, charges left...). The server never sends NaN or infinite
	 * floats. CAST of a charge or channel carries the charge or channel ticks as {@code duration}; HIT has pos = the
	 * victim's middle, to = the caster's hand, target = the victim.
	 */
	public record TechFx(int spec, byte phase, double x, double y, double z, double tx, double ty, double tz, float radius,
			float length, byte tier, short flags, short count, int seed, int caster, short duration, int target, short index, short of,
			int aux) implements CustomPacketPayload {
		/** A Kehancuran cast (CRACKS layer, BLOCK_CRUMBLE at the impact). */
		public static final int FLAG_KEHANCURAN = 1;
		public static final int FLAG_ECHO = 2;
		/** Server under load: draw x0.5 and no displays. */
		public static final int FLAG_LOW_LOAD = 4;
		public static final int FLAG_BOSS_HIT = 8;
		public static final int FLAG_DISCIPLE = 16;
		public static final int FLAG_ALLY = 32;
		public static final int FLAG_SHOWCASE = 64;
		/** The cast's climax event (once per cast, on {@code TechniqueSpec.climaxPhase()}). */
		public static final int FLAG_CLIMAX = 128;
		/** A falling visual (meteor, seal, palm, sun): {@code duration} = ticks until it lands at {@code tx,ty,tz}. */
		public static final int FLAG_PRE_IMPACT = 256;
		/** A lasting area that follows the caster: POS layers attach to the caster. */
		public static final int FLAG_FOLLOW = 512;
		/** A DOT tick hit: light layers only. */
		public static final int FLAG_DOT = 1024;
		/** A stage recast: {@code aux & 0xFF} = the stage index. */
		public static final int FLAG_STAGE = 2048;
		/** A movement (dash, blink, leap) TRAVEL: pos = from, to = to. */
		public static final int FLAG_BLINK = 4096;
		/** A two-Dao art: {@code aux} = the second Dao's RGB. */
		public static final int FLAG_TWO_DAO = 8192;
		/** One projectile of a volley: {@code aux} = the spread mode, {@code count} = projectiles, index / of set. */
		public static final int FLAG_VOLLEY = 16384;

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
					buf.writeShort(p.flags);
					buf.writeShort(p.count);
					buf.writeInt(p.seed);
					buf.writeVarInt(p.caster);
					buf.writeShort(p.duration);
					buf.writeVarInt(Math.max(0, p.target + 1));
					buf.writeShort(p.index);
					buf.writeShort(p.of);
					buf.writeInt(p.aux);
				},
				buf -> new TechFx(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
						buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readShort(), buf.readShort(),
						buf.readInt(), buf.readVarInt(), buf.readShort(), buf.readVarInt() - 1, buf.readShort(), buf.readShort(), buf.readInt()));

		/** A v1-shaped event (no target, single, no aux). */
		public TechFx(int spec, byte phase, double x, double y, double z, double tx, double ty, double tz, float radius, float length,
				byte tier, short flags, short count, int seed, int caster, short duration) {
			this(spec, phase, x, y, z, tx, ty, tz, radius, length, tier, flags, count, seed, caster, duration, -1, (short) 0, (short) 1, 0);
		}

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

		/** True when the event names a target entity. */
		public boolean hasTarget() {
			return target >= 0;
		}

		/** The stage index of a {@link #FLAG_STAGE} event (0 otherwise). */
		public int stageIndex() {
			return has(FLAG_STAGE) ? aux & 0xFF : 0;
		}

		/** True for one event of a group the client should spread itself ({@code index} -1, {@code of} > 1). */
		public boolean clientRing() {
			return index < 0 && of > 1;
		}

		@Override
		public Type<TechFx> type() {
			return TYPE;
		}
	}

	/**
	 * The phantoms of one cast (DESIGN 27.4): sent on spawn, every 10 ticks while any is alive and once empty when they end,
	 * to the players tracking the caster. The server keeps phantoms virtual; clients draw each {@link Entry} by its
	 * {@code PhantomKind} and lerp between syncs. {@code spec} = net id.
	 */
	public record PhantomSync(int caster, int spec, List<Entry> entries) implements CustomPacketPayload {
		public static final int MAX = 32;

		/**
		 * One phantom: {@code slot} its index in the cast, {@code kind} the {@code PhantomKind} ordinal, the position and yaw,
		 * {@code cling} the entity it clings to (-1 none), {@code mode} FOLLOW 0 / ANCHOR 1 / CLING 2, {@code ticksLeft}.
		 */
		public record Entry(byte slot, byte kind, float x, float y, float z, float yaw, int cling, byte mode, short ticksLeft) {
		}

		public static final Type<PhantomSync> TYPE = typeOf("phantom_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, PhantomSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.caster);
					buf.writeVarInt(p.spec);
					int count = Math.min(MAX, p.entries.size());
					buf.writeVarInt(count);
					for (int i = 0; i < count; i++) {
						Entry e = p.entries.get(i);
						buf.writeByte(e.slot);
						buf.writeByte(e.kind);
						buf.writeFloat(e.x);
						buf.writeFloat(e.y);
						buf.writeFloat(e.z);
						buf.writeFloat(e.yaw);
						buf.writeVarInt(Math.max(0, e.cling + 1));
						buf.writeByte(e.mode);
						buf.writeShort(e.ticksLeft);
					}
				},
				buf -> {
					int caster = buf.readVarInt();
					int spec = buf.readVarInt();
					int count = Math.min(MAX, buf.readVarInt());
					List<Entry> entries = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						entries.add(new Entry(buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
								buf.readVarInt() - 1, buf.readByte(), buf.readShort()));
					}
					return new PhantomSync(caster, spec, entries);
				});

		@Override
		public Type<PhantomSync> type() {
			return TYPE;
		}
	}

	/**
	 * A TRANSFORM form of a player or disciple (DESIGN 27.4): {@code form} the form id ("" = none, the form ended),
	 * {@code ticks} its remaining duration, {@code stage} a form-specific stage (heaven fire 1-3, ancient god stomps...).
	 * Sent to the entity's trackers and the entity itself on start, stage change and end.
	 */
	public record FormSync(int entity, String form, short ticks, byte stage) implements CustomPacketPayload {
		public static final Type<FormSync> TYPE = typeOf("form_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, FormSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.entity);
					buf.writeUtf(p.form, 64);
					buf.writeShort(p.ticks);
					buf.writeByte(p.stage);
				},
				buf -> new FormSync(buf.readVarInt(), buf.readUtf(64), buf.readShort(), buf.readByte()));

		public boolean ended() {
			return form.isEmpty();
		}

		@Override
		public Type<FormSync> type() {
			return TYPE;
		}
	}

	/**
	 * A visual bind on an entity (DESIGN 27.4): {@code kind} the {@code BindKind} ordinal (vines, rope, chains, seal, frost,
	 * time stop, suspension, sleep, karmic link), {@code ticks} the remaining duration (0 = ended), {@code caster} the
	 * binder's entity id, {@code spec} the spec net id (-1 none). Sent to the bound entity's trackers.
	 */
	public record BindSync(int entity, byte kind, short ticks, int caster, int spec) implements CustomPacketPayload {
		public static final Type<BindSync> TYPE = typeOf("bind_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, BindSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.entity);
					buf.writeByte(p.kind);
					buf.writeShort(p.ticks);
					buf.writeVarInt(Math.max(0, p.caster + 1));
					buf.writeVarInt(Math.max(0, p.spec + 1));
				},
				buf -> new BindSync(buf.readVarInt(), buf.readByte(), buf.readShort(), buf.readVarInt() - 1, buf.readVarInt() - 1));

		public boolean ended() {
			return ticks <= 0;
		}

		@Override
		public Type<BindSync> type() {
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
		/** The size budget of one sync (DESIGN 25.13), measured on the wire with {@link Payloads#wireBytes}; vanilla allows 1 MiB. */
		public static final int MAX_BYTES = 65536;
		/** One over-budget warning per channel per server run, so a big sync does not spam the log. */
		private static final boolean[] WARNED = new boolean[CHANNELS];

		public static final Type<DuniaSync> TYPE = typeOf("dunia_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, DuniaSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.channel);
					ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data);
				},
				buf -> new DuniaSync(buf.readVarInt(), ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		/**
		 * Sends {@code data} on {@code channel} to the player (skipped for vanilla clients). Data over {@link #MAX_BYTES} is
		 * not sent: a warning is logged (once per channel) and the client gets {@code {too_big: true}} instead; the client
		 * keeps its previous data and shows a "data too big" line.
		 */
		public static void send(net.minecraft.server.level.ServerPlayer player, int channel, CompoundTag data) {
			CompoundTag out = data == null ? new CompoundTag() : data;
			int bytes = wireBytes(out);
			if (bytes > MAX_BYTES) {
				if (channel < 0 || channel >= CHANNELS || !WARNED[channel]) {
					if (channel >= 0 && channel < CHANNELS) {
						WARNED[channel] = true;
					}
					Kultivator.LOGGER.warn("DuniaSync channel {} is {} bytes, over the {} byte budget; not sent", channel, bytes, MAX_BYTES);
				}
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

	/**
	 * Phase 10 (DESIGN 28.6.1, 28.13.2): the blocks one Mode Kehancuran job changed this tick, so the clients near it throw
	 * real debris, light a molten floor or close the crater with a shock ring. At most one per running job per tick, at
	 * most {@link #MAX} entries, only to players within 96 blocks of the batch in the job's dimension; restores send
	 * nothing. {@code job} groups the batches of one job (a per-server counter), {@code caster} is the caster's entity id
	 * (-1 when absent), {@code spec} the spec's net id (-1 for Destroyer Arts and plain jobs), {@code cx,cy,cz} the job
	 * centre, {@code total} the entries' count before sampling (DONE: the blocks changed), {@code flags} the
	 * {@code FLAG_*} bits, {@code offsets} the packed {@link #pack} offsets relative to {@code BlockPos.containing(cx, cy,
	 * cz)} and {@code states} the {@code Block.getId(state)} ids (the OLD state for CARVE, CONVERT and TEST, the NEW state
	 * for SURFACE).
	 */
	public record DampakDebris(int job, byte kind, int caster, int spec, double cx, double cy, double cz, short total, short flags,
			int[] offsets, int[] states) implements CustomPacketPayload {
		public static final int MAX = 64;
		/** Blocks removed. */
		public static final byte CARVE = 0;
		/** Blocks withered or stripped (no displays: ash and leaves). */
		public static final byte CONVERT = 1;
		/** The molten surface pass (Kawah Lebur): states are the new states. */
		public static final byte SURFACE = 2;
		/** The job finished: {@code total} = blocks changed; no entries. */
		public static final byte DONE = 3;
		/** {@code uji dampak puing / angkat}: nothing changed. */
		public static final byte TEST = 4;
		/** Clear the Phase 10 visuals: of this job, or all when {@code job} = -1. */
		public static final byte STOP = 5;

		public static final int FLAG_SELF = 1;
		public static final int FLAG_DISCIPLE = 2;
		public static final int FLAG_LIFT = 4;
		public static final int FLAG_IMPLODE = 8;
		public static final int FLAG_SUSPEND = 16;
		public static final int FLAG_WAKE = 32;
		public static final int FLAG_LOW_LOAD = 64;

		public static final Type<DampakDebris> TYPE = typeOf("dampak_debris");
		public static final StreamCodec<RegistryFriendlyByteBuf, DampakDebris> CODEC = StreamCodec.of(
				(buf, p) -> {
					int count = Math.min(MAX, Math.min(p.offsets.length, p.states.length));
					buf.writeVarInt(p.job + 1);
					buf.writeByte(p.kind);
					buf.writeVarInt(p.caster + 1);
					buf.writeVarInt(p.spec + 1);
					buf.writeDouble(p.cx);
					buf.writeDouble(p.cy);
					buf.writeDouble(p.cz);
					buf.writeShort(p.total);
					buf.writeShort(p.flags);
					buf.writeVarInt(count);
					for (int i = 0; i < count; i++) {
						buf.writeInt(p.offsets[i]);
						buf.writeVarInt(p.states[i]);
					}
				},
				buf -> {
					int job = buf.readVarInt() - 1;
					byte kind = buf.readByte();
					int caster = buf.readVarInt() - 1;
					int spec = buf.readVarInt() - 1;
					double cx = buf.readDouble();
					double cy = buf.readDouble();
					double cz = buf.readDouble();
					short total = buf.readShort();
					short flags = buf.readShort();
					int count = buf.readVarInt();
					if (count < 0 || count > MAX) {
						throw new IllegalArgumentException("DampakDebris with " + count + " entries (max " + MAX + ")");
					}
					int[] offsets = new int[count];
					int[] states = new int[count];
					for (int i = 0; i < count; i++) {
						offsets[i] = buf.readInt();
						states[i] = buf.readVarInt();
					}
					return new DampakDebris(job, kind, caster, spec, cx, cy, cz, total, flags, offsets, states);
				});

		/** Packs an offset of at most ±511 per axis: {@code (dx + 512) << 20 | (dy + 512) << 10 | (dz + 512)}. */
		public static int pack(int dx, int dy, int dz) {
			return (clamp(dx) + 512) << 20 | (clamp(dy) + 512) << 10 | (clamp(dz) + 512);
		}

		private static int clamp(int value) {
			return Math.max(-511, Math.min(511, value));
		}

		public static int dx(int packed) {
			return ((packed >> 20) & 0x3FF) - 512;
		}

		public static int dy(int packed) {
			return ((packed >> 10) & 0x3FF) - 512;
		}

		public static int dz(int packed) {
			return (packed & 0x3FF) - 512;
		}

		/** Entries in this batch. */
		public int size() {
			return Math.min(offsets.length, states.length);
		}

		/** The block of entry {@code i}: {@code BlockPos.containing(cx, cy, cz)} plus its offset. */
		public net.minecraft.core.BlockPos pos(int i) {
			net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(cx, cy, cz);
			int packed = offsets[i];
			return base.offset(dx(packed), dy(packed), dz(packed));
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<DampakDebris> type() {
			return TYPE;
		}
	}

	/**
	 * Phase 10b (DESIGN 29.10 C10b.5): the Titah Langit menu's data, sent when the menu opens or a value changes (at most
	 * {@link #MAX_BYTES}). Keys: {@code selected} (0-3), {@code kehancuranOn}, {@code kawahOn}, {@code bypass},
	 * {@code recover} (ticks of the shared seal recovery left), {@code skills} (a list of 4 compounds in Skill order:
	 * {@code unlocked}, {@code why} (a lang key when locked, else ""), {@code cdLeft} and {@code cdMax} (ticks),
	 * {@code k}, {@code d} (0-100), {@code v}, {@code h}, {@code m}, {@code kMax}, {@code readout} (a plain line of
	 * numbers) and {@code ro} {rVis, rDmg, blocks, cdSec, qiPct, carveR}). Phase 10b Tingkat (DESIGN 29.16.3, contract
	 * C10b.8) adds per skill {@code t} (the used Tingkat), {@code tMax}, {@code tWhy} (a lang key or ""), {@code perm}
	 * (Tingkat ≥ 6 and this cast would really carve) and in {@code ro} {@code tier}, {@code dmgR}, {@code weapon} (the
	 * planted sword or pillar length in blocks, 0 for Bintang and Gunung) and {@code permanent}; {@code rVis} and
	 * {@code carveR} are the scaled values. Routed to {@code client.pusakalangit.TitahClient.onSync}.
	 */
	public record TitahSync(CompoundTag data) implements CustomPacketPayload {
		public static final int MAX_BYTES = 65536; // measured on the wire (Payloads.wireBytes); vanilla allows 1 MiB
		private static boolean warned;
		public static final Type<TitahSync> TYPE = typeOf("titah_sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, TitahSync> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new TitahSync(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		/** Sends {@code data} to the player (skipped for vanilla clients); over {@link #MAX_BYTES}: a warning and {too_big}. */
		public static void send(net.minecraft.server.level.ServerPlayer player, CompoundTag data) {
			CompoundTag out = data == null ? new CompoundTag() : data;
			int bytes = wireBytes(out);
			if (bytes > MAX_BYTES) {
				if (!warned) {
					warned = true;
					Kultivator.LOGGER.warn("TitahSync is {} bytes, over the {} byte budget; not sent", bytes, MAX_BYTES);
				}
				out = new CompoundTag();
				out.putBoolean("too_big", true);
			}
			Net.send(player, new TitahSync(out));
		}

		@Override
		public Type<TitahSync> type() {
			return TYPE;
		}
	}

	/**
	 * Phase 10b Tingkat (DESIGN 29.16.11, contract C10b.9): the live planted Titah weapons of the receiver's dimension, so
	 * a client can draw giant monuments beyond its entity tracking range ({@code client.pusakalangit.world.FarRelic}).
	 * Keys: {@code dim} (the dimension id) and {@code m}, a list of at most 24 {@code {id:int, x:double, y:double,
	 * z:double, model:byte, scale:float, yaw:float, tilt:float, state:byte, born:long, tier:byte}} (newest first). Sent by
	 * {@code TitahMonuments} to every player of the dimension on create, state change, removal and expiry, and to a player
	 * whose dimension differs from the last one sent; an empty {@code m} clears the client's list. At most
	 * {@link #MAX_BYTES} (the oldest entries are dropped to fit).
	 */
	public record TitahFar(CompoundTag data) implements CustomPacketPayload {
		/** Measured on the wire ({@link Payloads#wireBytes}): one entry is about 120 bytes, so all 24 fit with room. */
		public static final int MAX_BYTES = 8192;
		public static final int MAX_ENTRIES = 24;
		public static final Type<TitahFar> TYPE = typeOf("titah_far");
		public static final StreamCodec<RegistryFriendlyByteBuf, TitahFar> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new TitahFar(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		/** Sends {@code data} to the player (skipped for vanilla clients), trimmed to {@link #MAX_BYTES}. */
		public static void send(net.minecraft.server.level.ServerPlayer player, CompoundTag data) {
			CompoundTag out = data == null ? new CompoundTag() : data.copy();
			net.minecraft.nbt.ListTag list = out.getListOrEmpty("m").copy();
			while (list.size() > MAX_ENTRIES) {
				list.remove(list.size() - 1);
			}
			out.put("m", list);
			while (!list.isEmpty() && wireBytes(out) > MAX_BYTES) {
				list.remove(list.size() - 1);
				out.put("m", list);
			}
			Net.send(player, new TitahFar(out));
		}

		@Override
		public Type<TitahFar> type() {
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
