package dev.kultivator.sekte.tangga;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module G's one payload (DESIGN 36.2.4), registered from {@link Tangga#init()} with {@code PayloadTypeRegistry} (never
 * {@code network/Payloads}): the state of one illusory stair, server to the clients near it. The client draws the
 * ribbon from {@link JalurTangga} and the frame here, and the step numbers over the climbers from {@link Climber}.
 * Sent when a phase starts and every 10 ticks while the climbers move; {@link #PHASE_OFF} removes the stair.
 */
public final class TanggaPayloads {

	public static final int PHASE_OFF = 0;
	/** The projection unfurls from the projector (the gathering). */
	public static final int PHASE_FORMING = 1;
	public static final int PHASE_CLIMB = 2;
	/** The last climbers reach their limit; the peak shines if someone stands on it. */
	public static final int PHASE_PEAK = 3;
	/** Results are read out one by one. */
	public static final int PHASE_ANNOUNCE = 4;
	/** The stair fades and the climbers come down. */
	public static final int PHASE_FADE = 5;

	/** Only the projection (uji perguruan tangga proyeksi), no climbers. */
	public static final int FLAG_PREVIEW = 1;
	/** One disciple's Ujian Hati re-climb. */
	public static final int FLAG_HEART = 2;

	/** A climber still moving up. */
	public static final int C_CLIMB = 0;
	/** Kneeling under an illusion (step 300, 600, 900). */
	public static final int C_KNEEL = 1;
	/** Stopped at its limit, standing. */
	public static final int C_STOP = 2;
	/** On the peak (step 1000). */
	public static final int C_PEAK = 3;
	/** Gave up: turned back (verdict hati 1). */
	public static final int C_GAVE_UP = 4;

	/** One climber: entity id, lane 0-5, current step, the step it stops at (shown at the end), state, verdict 0-3. */
	public record Climber(int entityId, int lane, int step, int limit, int state, int verdict) {
	}

	/**
	 * The stair of hall {@code owner}: projector {@code (ox, oy, oz)}, forward {@code (fx, fz)}, {@code phase} and the
	 * ticks since the trial began, {@code lod} (-1 the client's EffectQuality, 0-2 forced), {@code flags}.
	 */
	public record State(UUID owner, double ox, double oy, double oz, float fx, float fz, int phase, int ticks, int lod, int flags,
			List<Climber> climbers) implements CustomPacketPayload {

		public static final Type<State> TYPE = new Type<>(Kultivator.id("sekte_tangga_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(State::write, State::read);

		public State {
			climbers = climbers == null ? List.of() : List.copyOf(climbers.subList(0, Math.min(JalurTangga.LANES, climbers.size())));
		}

		/** The "remove this stair" message. */
		public static State off(UUID owner) {
			return new State(owner, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, PHASE_OFF, 0, -1, 0, List.of());
		}

		private static void write(RegistryFriendlyByteBuf buf, State s) {
			UUIDUtil.STREAM_CODEC.encode(buf, s.owner);
			buf.writeDouble(s.ox);
			buf.writeDouble(s.oy);
			buf.writeDouble(s.oz);
			buf.writeFloat(s.fx);
			buf.writeFloat(s.fz);
			buf.writeByte(s.phase);
			buf.writeVarInt(Math.max(0, s.ticks));
			buf.writeByte(s.lod);
			buf.writeByte(s.flags);
			buf.writeByte(s.climbers.size());
			for (Climber c : s.climbers) {
				buf.writeVarInt(c.entityId());
				buf.writeByte(c.lane());
				buf.writeShort(c.step());
				buf.writeShort(c.limit());
				buf.writeByte(c.state());
				buf.writeByte(c.verdict());
			}
		}

		private static State read(RegistryFriendlyByteBuf buf) {
			UUID owner = UUIDUtil.STREAM_CODEC.decode(buf);
			double ox = buf.readDouble();
			double oy = buf.readDouble();
			double oz = buf.readDouble();
			float fx = buf.readFloat();
			float fz = buf.readFloat();
			int phase = buf.readByte();
			int ticks = buf.readVarInt();
			int lod = buf.readByte();
			int flags = buf.readByte();
			int n = Math.min(JalurTangga.LANES, buf.readByte());
			List<Climber> climbers = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				climbers.add(new Climber(buf.readVarInt(), buf.readByte(), buf.readShort(), buf.readShort(), buf.readByte(), buf.readByte()));
			}
			return new State(owner, ox, oy, oz, fx, fz, phase, ticks, lod, flags, climbers);
		}

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
	}

	private TanggaPayloads() {
	}
}
