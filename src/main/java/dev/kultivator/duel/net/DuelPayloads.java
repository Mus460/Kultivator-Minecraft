package dev.kultivator.duel.net;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Every payload of the sword duel (DESIGN 35.8.1, contract C35.S3), registered by {@link DuelNet#init()} with
 * {@code PayloadTypeRegistry}; no edit to {@code network/Payloads} or {@code Net}.
 * <p>
 * Wire format: the fields in the order of the design's tables with the table's widths (byte, short, int, float);
 * entity ids as {@code varint(id + 1)} like {@code VoicePayload} so −1 (none) is one byte; Components with
 * {@code ComponentSerialization.TRUSTED_STREAM_CODEC} (server to client only). On the Java side every integral field is an
 * {@code int} (widened when read; bytes and shorts are cut to their width when written), so callers can pass
 * {@code DuelIds} constants directly. Unsigned fields ({@code seq}, {@code clientTick}, {@code power}) read back as
 * 0..65535 / 0..255; signed ones ({@code pressure}, {@code swingB}, {@code seed}, {@code honourDelta}) keep their sign.
 * The codes of the byte fields are in {@code duel.DuelIds}.
 */
public final class DuelPayloads {

	// ================================================================ client to server

	/**
	 * One duel input (8 B). {@code kind} {@code DuelIds.IN_*}, {@code dir} a {@code SwingDir} ordinal, {@code flags}
	 * {@code DuelIds.INF_*}, {@code seq} rising per player (mod 65536, duplicates dropped), {@code clientTick} the low 16 bits
	 * of the client's estimate of the server tick (diagnostics only), {@code arg} the push count of BIND_PUSH, the windup of
	 * BC_SWING, the dodge direction of DODGE.
	 */
	public record DuelInput(int kind, int dir, int flags, int seq, int clientTick, int arg) implements CustomPacketPayload {
		public static final Type<DuelInput> TYPE = new Type<>(Kultivator.id("duel_input"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelInput> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeByte(p.kind);
					buf.writeByte(p.dir);
					buf.writeByte(p.flags);
					buf.writeShort(p.seq);
					buf.writeShort(p.clientTick);
					buf.writeByte(p.arg);
				},
				buf -> new DuelInput(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedShort(),
						buf.readUnsignedShort(), buf.readUnsignedByte()));

		@Override
		public Type<DuelInput> type() {
			return TYPE;
		}
	}

	/**
	 * A challenge (≤ 90 B): {@code targetId} the challenged entity, {@code secondId} the second disciple of a
	 * {@code PAIR_TONTON} (−1 otherwise), the rules, the stake, {@code slipSpec} the technique id of the challenger's jade
	 * slip ("" none, ≤ 64), {@code flags} {@code DuelIds.RF_*} (bit0 intro film, bit1 crowd).
	 */
	public record DuelChallenge(int targetId, int secondId, int realmRule, int jurusRule, int winRule, int radius, int timeLimitTicks,
								int stakeKind, int stakeGrade, int stakeCount, String slipSpec, int flags) implements CustomPacketPayload {
		public static final Type<DuelChallenge> TYPE = new Type<>(Kultivator.id("duel_challenge"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelChallenge> CODEC = StreamCodec.of(
				(buf, p) -> {
					writeId(buf, p.targetId);
					writeId(buf, p.secondId);
					buf.writeByte(p.realmRule);
					buf.writeByte(p.jurusRule);
					buf.writeByte(p.winRule);
					buf.writeByte(p.radius);
					buf.writeInt(p.timeLimitTicks);
					buf.writeByte(p.stakeKind);
					buf.writeByte(p.stakeGrade);
					buf.writeShort(p.stakeCount);
					buf.writeUtf(p.slipSpec == null ? "" : p.slipSpec, 64);
					buf.writeByte(p.flags);
				},
				buf -> new DuelChallenge(readId(buf), readId(buf), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
						buf.readUnsignedByte(), buf.readInt(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedShort(),
						buf.readUtf(64), buf.readUnsignedByte()));

		@Override
		public Type<DuelChallenge> type() {
			return TYPE;
		}
	}

	/** An answer to a challenge or a running duel (6 B): {@code answer} {@code DuelIds.ANSWER_*}. */
	public record DuelAnswer(int challengeId, int answer, int arg) implements CustomPacketPayload {
		public static final Type<DuelAnswer> TYPE = new Type<>(Kultivator.id("duel_answer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelAnswer> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeInt(p.challengeId);
					buf.writeByte(p.answer);
					buf.writeByte(p.arg);
				},
				buf -> new DuelAnswer(buf.readInt(), buf.readUnsignedByte(), buf.readUnsignedByte()));

		@Override
		public Type<DuelAnswer> type() {
			return TYPE;
		}
	}

	/**
	 * The client's duel settings (3 B), sent on join and whenever they change: {@code kiai} {@code DuelIds.KIAI_*},
	 * {@code inputMode} the {@code DuelIds.INPUT_*} in use (OTOMATIS already resolved), {@code flags} {@code DuelIds.PREF_*}.
	 */
	public record DuelPrefs(int kiai, int inputMode, int flags) implements CustomPacketPayload {
		public static final Type<DuelPrefs> TYPE = new Type<>(Kultivator.id("duel_prefs"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelPrefs> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeByte(p.kiai);
					buf.writeByte(p.inputMode);
					buf.writeByte(p.flags);
				},
				buf -> new DuelPrefs(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte()));

		@Override
		public Type<DuelPrefs> type() {
			return TYPE;
		}
	}

	// ================================================================ server to client

	/**
	 * The five rule bytes shared by the offer and the session sync: {@code realmRule} {@code DuelIds.REALM_*},
	 * {@code jurusRule} {@code JURUS_*}, {@code winRule} {@code WIN_*}, {@code radius} the ring radius (6/9/12),
	 * {@code flags} {@code DuelIds.RF_*}.
	 */
	public record RuleBytes(int realmRule, int jurusRule, int winRule, int radius, int flags) {
		public static final RuleBytes DEFAULT = new RuleBytes(0, 0, 1, 9, 1);

		static void write(RegistryFriendlyByteBuf buf, RuleBytes rules) {
			buf.writeByte(rules.realmRule);
			buf.writeByte(rules.jurusRule);
			buf.writeByte(rules.winRule);
			buf.writeByte(rules.radius);
			buf.writeByte(rules.flags);
		}

		static RuleBytes read(RegistryFriendlyByteBuf buf) {
			return new RuleBytes(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
					buf.readUnsignedByte());
		}
	}

	/**
	 * A challenge to answer (≤ 320 B, once): who, their realm and title line, the rules, the stake ({@code slipName} the
	 * challenger's slip technique, empty without one), {@code expiresTicks} until it lapses, {@code pair}
	 * {@code DuelIds.PAIR_*}.
	 */
	public record DuelChallengeOffer(int challengeId, int challengerId, Component name, Component rankLine, RuleBytes rules, int stakeKind,
									 int stakeGrade, int stakeCount, Component slipName, int expiresTicks, int pair) implements CustomPacketPayload {
		public static final Type<DuelChallengeOffer> TYPE = new Type<>(Kultivator.id("duel_offer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelChallengeOffer> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeInt(p.challengeId);
					writeId(buf, p.challengerId);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.name);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.rankLine);
					RuleBytes.write(buf, p.rules);
					buf.writeByte(p.stakeKind);
					buf.writeByte(p.stakeGrade);
					buf.writeShort(p.stakeCount);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.slipName);
					buf.writeShort(p.expiresTicks);
					buf.writeByte(p.pair);
				},
				buf -> new DuelChallengeOffer(buf.readInt(), readId(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
						ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), RuleBytes.read(buf), buf.readUnsignedByte(),
						buf.readUnsignedByte(), buf.readUnsignedShort(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
						buf.readUnsignedShort(), buf.readUnsignedByte()));

		@Override
		public Type<DuelChallengeOffer> type() {
			return TYPE;
		}
	}

	/**
	 * A duel session for its participants and every player within 64 blocks (≤ 300 B): on each phase change, every 40
	 * ticks and when a player comes into range. {@code phase} {@code DuelIds.PHASE_*} (ENDED once, then forgotten);
	 * {@code cx, cy, cz} the ring centre, {@code mark*} the two start marks (x, z; same y as the centre);
	 * {@code phaseTicksLeft} of the current phase, {@code fightTicksLeft} of the time limit; {@code serverTick} for
	 * {@code DuelClock}; names, rank lines ("realm · title") and colours of A and B; {@code flags} {@code DuelIds.SF_*}.
	 */
	public record DuelSessionSync(int sessionId, int phase, int pair, int aId, int bId, float cx, float cy, float cz, int radius,
								  float markAx, float markAz, float markBx, float markBz, RuleBytes rules, int phaseTicksLeft,
								  int fightTicksLeft, int serverTick, Component aName, Component bName, Component aRank, Component bRank,
								  int aRgb, int bRgb, int flags) implements CustomPacketPayload {
		public static final Type<DuelSessionSync> TYPE = new Type<>(Kultivator.id("duel_session"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelSessionSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeInt(p.sessionId);
					buf.writeByte(p.phase);
					buf.writeByte(p.pair);
					writeId(buf, p.aId);
					writeId(buf, p.bId);
					buf.writeFloat(p.cx);
					buf.writeFloat(p.cy);
					buf.writeFloat(p.cz);
					buf.writeByte(p.radius);
					buf.writeFloat(p.markAx);
					buf.writeFloat(p.markAz);
					buf.writeFloat(p.markBx);
					buf.writeFloat(p.markBz);
					RuleBytes.write(buf, p.rules);
					buf.writeInt(p.phaseTicksLeft);
					buf.writeInt(p.fightTicksLeft);
					buf.writeInt(p.serverTick);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.aName);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.bName);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.aRank);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.bRank);
					buf.writeInt(p.aRgb);
					buf.writeInt(p.bRgb);
					buf.writeByte(p.flags);
				},
				buf -> new DuelSessionSync(buf.readInt(), buf.readUnsignedByte(), buf.readUnsignedByte(), readId(buf), readId(buf),
						buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUnsignedByte(), buf.readFloat(), buf.readFloat(),
						buf.readFloat(), buf.readFloat(), RuleBytes.read(buf), buf.readInt(), buf.readInt(), buf.readInt(),
						ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
						ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
						buf.readInt(), buf.readInt(), buf.readUnsignedByte()));

		@Override
		public Type<DuelSessionSync> type() {
			return TYPE;
		}
	}

	/**
	 * A fighter's posture and state (13 B, at most 10 Hz while it changes; also for mobs with posture within 32 blocks):
	 * posture and max ×10, {@code state} {@code DuelIds.ST_*}, {@code flags} {@code DuelIds.FF_*}, the exchange chain,
	 * the {@code WeaponClass} ordinal.
	 */
	public record DuelFighterSync(int entityId, int postureX10, int postureMaxX10, int state, int flags, int chain, int weaponClass)
			implements CustomPacketPayload {
		public static final Type<DuelFighterSync> TYPE = new Type<>(Kultivator.id("duel_fighter"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelFighterSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					writeId(buf, p.entityId);
					buf.writeShort(p.postureX10);
					buf.writeShort(p.postureMaxX10);
					buf.writeByte(p.state);
					buf.writeByte(p.flags);
					buf.writeByte(p.chain);
					buf.writeByte(p.weaponClass);
				},
				buf -> new DuelFighterSync(readId(buf), buf.readShort(), buf.readShort(), buf.readUnsignedByte(), buf.readUnsignedByte(),
						buf.readUnsignedByte(), buf.readUnsignedByte()));

		@Override
		public Type<DuelFighterSync> type() {
			return TYPE;
		}
	}

	/**
	 * One swing (18 B): {@code swingId} per fighter (0..32767), {@code kind} a {@code SwingKind} ordinal, {@code dir} a
	 * {@code SwingDir} ordinal, {@code weaponClass}, {@code windup} in ticks (0 = a heavy charge being held, see
	 * {@code DuelIds.WINDUP_CHARGING}), {@code startTick} the server tick the windup began (impact = start + windup),
	 * {@code flags} {@code DuelIds.SW_*}, {@code qiRgb} the qi colour of a QI swing (0 otherwise). A swing is sent again
	 * with the same id when it changes (charge released, feint cancelled, rejected).
	 */
	public record DuelSwing(int entityId, int swingId, int kind, int dir, int weaponClass, int windup, int startTick, int flags, int qiRgb)
			implements CustomPacketPayload {
		public static final Type<DuelSwing> TYPE = new Type<>(Kultivator.id("duel_swing"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelSwing> CODEC = StreamCodec.of(
				(buf, p) -> {
					writeId(buf, p.entityId);
					buf.writeShort(p.swingId);
					buf.writeByte(p.kind);
					buf.writeByte(p.dir);
					buf.writeByte(p.weaponClass);
					buf.writeByte(p.windup);
					buf.writeInt(p.startTick);
					buf.writeShort(p.flags);
					buf.writeInt(p.qiRgb);
				},
				buf -> new DuelSwing(readId(buf), buf.readShort(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
						buf.readUnsignedByte(), buf.readInt(), buf.readUnsignedShort(), buf.readInt()));

		@Override
		public Type<DuelSwing> type() {
			return TYPE;
		}
	}

	/**
	 * One result (42 B): {@code aId} the attacker (or the first party), {@code bId} the defender, their swing ids
	 * ({@code swingB} −1 without one), {@code outcome} {@code DuelIds.OUT_*}, {@code x, y, z} the server's contact anchor
	 * (from the shared blade arcs), the two {@code SwingDir} ordinals ({@code dirB} 255 without a swing), {@code power}
	 * 0..255, the two spark/qi colours, {@code serverTick} the resolution tick, {@code seed} for the visuals,
	 * {@code flags} {@code DuelIds.CF_*} (DECISIVE: this result ended the duel).
	 */
	public record DuelClash(int aId, int bId, int swingA, int swingB, int outcome, float x, float y, float z, int dirA, int dirB, int power,
							int rgbA, int rgbB, int serverTick, int seed, int flags) implements CustomPacketPayload {
		public static final Type<DuelClash> TYPE = new Type<>(Kultivator.id("duel_clash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelClash> CODEC = StreamCodec.of(
				(buf, p) -> {
					writeId(buf, p.aId);
					writeId(buf, p.bId);
					buf.writeShort(p.swingA);
					buf.writeShort(p.swingB);
					buf.writeByte(p.outcome);
					buf.writeFloat(p.x);
					buf.writeFloat(p.y);
					buf.writeFloat(p.z);
					buf.writeByte(p.dirA);
					buf.writeByte(p.dirB);
					buf.writeByte(p.power);
					buf.writeInt(p.rgbA);
					buf.writeInt(p.rgbB);
					buf.writeInt(p.serverTick);
					buf.writeShort(p.seed);
					buf.writeByte(p.flags);
				},
				buf -> new DuelClash(readId(buf), readId(buf), buf.readShort(), buf.readShort(), buf.readUnsignedByte(), buf.readFloat(),
						buf.readFloat(), buf.readFloat(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
						buf.readInt(), buf.readInt(), buf.readInt(), buf.readShort(), buf.readUnsignedByte()));

		@Override
		public Type<DuelClash> type() {
			return TYPE;
		}
	}

	/**
	 * A blade lock (11 B, every 2 ticks while it lasts): {@code pressure} −100..100 (positive: A ahead),
	 * {@code ticksLeft} until it breaks by itself, {@code flags} {@code DuelIds.BF_*}.
	 */
	public record DuelBindSync(int aId, int bId, int pressure, int ticksLeft, int flags) implements CustomPacketPayload {
		public static final Type<DuelBindSync> TYPE = new Type<>(Kultivator.id("duel_bind"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelBindSync> CODEC = StreamCodec.of(
				(buf, p) -> {
					writeId(buf, p.aId);
					writeId(buf, p.bId);
					buf.writeByte(p.pressure);
					buf.writeByte(p.ticksLeft);
					buf.writeByte(p.flags);
				},
				buf -> new DuelBindSync(readId(buf), readId(buf), buf.readByte(), buf.readUnsignedByte(), buf.readUnsignedByte()));

		@Override
		public Type<DuelBindSync> type() {
			return TYPE;
		}
	}

	/**
	 * The end of a duel (≤ 240 B, once): winner and loser entity ids (−1 for a draw or a cancel), {@code reason}
	 * {@code DuelIds.RES_*}, the hit and Perfect counts of A and B, the longest chain, the length of the fight,
	 * {@code honourDelta} of the receiving player (0 when none), the result card title and the stake line.
	 */
	public record DuelResult(int sessionId, int winnerId, int loserId, int reason, int hitsA, int hitsB, int perfectA, int perfectB,
							 int maxChain, int durationTicks, int honourDelta, Component title, Component stakeLine) implements CustomPacketPayload {
		public static final Type<DuelResult> TYPE = new Type<>(Kultivator.id("duel_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelResult> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeInt(p.sessionId);
					writeId(buf, p.winnerId);
					writeId(buf, p.loserId);
					buf.writeByte(p.reason);
					buf.writeShort(p.hitsA);
					buf.writeShort(p.hitsB);
					buf.writeShort(p.perfectA);
					buf.writeShort(p.perfectB);
					buf.writeByte(p.maxChain);
					buf.writeInt(p.durationTicks);
					buf.writeShort(p.honourDelta);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.title);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.stakeLine);
				},
				buf -> new DuelResult(buf.readInt(), readId(buf), readId(buf), buf.readUnsignedByte(), buf.readUnsignedShort(),
						buf.readUnsignedShort(), buf.readUnsignedShort(), buf.readUnsignedShort(), buf.readUnsignedByte(), buf.readInt(),
						buf.readShort(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf)));

		@Override
		public Type<DuelResult> type() {
			return TYPE;
		}
	}

	/** A test preview for one player (13 B, {@code uji duel ...}): {@code kind} {@code DuelIds.PV_*}, an argument, a float, a colour. */
	public record DuelPreview(int kind, int arg, float f, int rgb) implements CustomPacketPayload {
		public static final Type<DuelPreview> TYPE = new Type<>(Kultivator.id("duel_preview"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DuelPreview> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeByte(p.kind);
					buf.writeInt(p.arg);
					buf.writeFloat(p.f);
					buf.writeInt(p.rgb);
				},
				buf -> new DuelPreview(buf.readUnsignedByte(), buf.readInt(), buf.readFloat(), buf.readInt()));

		@Override
		public Type<DuelPreview> type() {
			return TYPE;
		}
	}

	// ================================================================ helpers

	/** An entity id as {@code varint(id + 1)}: −1 (none) costs one byte. */
	static void writeId(RegistryFriendlyByteBuf buf, int id) {
		buf.writeVarInt(id + 1);
	}

	static int readId(RegistryFriendlyByteBuf buf) {
		return buf.readVarInt() - 1;
	}

	private DuelPayloads() {
	}
}
