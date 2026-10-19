package dev.kultivator.fx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Options of the emissive qi particles (polish pass S5): a three-tone colour ramp {@code core → body → rim} over the
 * particle's life, a size curve from {@code size} to {@code endSize}, an exact lifetime in ticks, an optional own velocity
 * (used instead of the spawn velocity when not zero) and the behaviour {@code flags}. One options class serves every qi
 * particle type ({@link QiParticles}); the type decides the sprite (soft mote, streak, smoke, ground ring, flame, shard,
 * petal, snowflake, feather). Pure data with no client reference: the client draws it, the server never sends it.
 */
public final class QiParticleOptions implements ParticleOptions {

	/** Full-bright (emissive). Smoke and ash leave it off. */
	public static final byte GLOW = 1;
	/** No gravity (otherwise the type's own: shards fall, smoke rises). */
	public static final byte NO_GRAVITY = 2;
	/** Strong air drag (bursts that stop and hang). */
	public static final byte DRAG = 4;
	/** Fades in over the first third instead of the first tenth (gathering qi). */
	public static final byte FADE_IN = 8;
	/** Turns while it lives. */
	public static final byte SPIN = 16;
	/** Brighter, whiter centre look (core sparks). */
	public static final byte ADDITIVE_LOOK = 32;
	/** Lies flat on the ground (rings, scorch and frost decals). */
	public static final byte GROUND_HUG = 64;
	/** The star glint sprite instead of the soft mote (qi type only). */
	public static final byte STAR = (byte) 0x80;

	private final ParticleType<QiParticleOptions> type;
	private final int core;
	private final int body;
	private final int rim;
	private final float size;
	private final float endSize;
	private final int life;
	private final float vx;
	private final float vy;
	private final float vz;
	private final byte flags;

	/** A {@code kultivator:qi} mote. */
	public QiParticleOptions(int core, int body, int rim, float size, float endSize, int life, float vx, float vy, float vz, byte flags) {
		this(QiParticles.QI, core, body, rim, size, endSize, life, vx, vy, vz, flags);
	}

	public QiParticleOptions(ParticleType<QiParticleOptions> type, int core, int body, int rim, float size, float endSize, int life, float vx,
			float vy, float vz, byte flags) {
		this.type = type;
		this.core = core & 0xFFFFFF;
		this.body = body & 0xFFFFFF;
		this.rim = rim & 0xFFFFFF;
		this.size = sane(size, 0.02F, 8.0F, 0.25F);
		this.endSize = sane(endSize, 0.0F, 8.0F, this.size);
		this.life = Math.max(1, Math.min(600, life));
		this.vx = sane(vx, -4.0F, 4.0F, 0.0F);
		this.vy = sane(vy, -4.0F, 4.0F, 0.0F);
		this.vz = sane(vz, -4.0F, 4.0F, 0.0F);
		this.flags = flags;
	}

	private static float sane(float value, float min, float max, float fallback) {
		return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}

	@Override
	public ParticleType<QiParticleOptions> getType() {
		return type;
	}

	public int core() {
		return core;
	}

	public int body() {
		return body;
	}

	public int rim() {
		return rim;
	}

	public float size() {
		return size;
	}

	public float endSize() {
		return endSize;
	}

	public int life() {
		return life;
	}

	public float vx() {
		return vx;
	}

	public float vy() {
		return vy;
	}

	public float vz() {
		return vz;
	}

	public byte flags() {
		return flags;
	}

	public boolean has(byte flag) {
		return (flags & flag) != 0;
	}

	/** The same particle as another qi type (a streak or smoke version of a mote). */
	public QiParticleOptions as(ParticleType<QiParticleOptions> other) {
		return new QiParticleOptions(other, core, body, rim, size, endSize, life, vx, vy, vz, flags);
	}

	// ================================================================== codecs (one pair per type)

	static MapCodec<QiParticleOptions> codec(ParticleType<QiParticleOptions> type) {
		return RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.fieldOf("core").forGetter(QiParticleOptions::core),
				Codec.INT.fieldOf("body").forGetter(QiParticleOptions::body),
				Codec.INT.fieldOf("rim").forGetter(QiParticleOptions::rim),
				Codec.FLOAT.optionalFieldOf("size", 0.25F).forGetter(QiParticleOptions::size),
				Codec.FLOAT.optionalFieldOf("end_size", 0.0F).forGetter(QiParticleOptions::endSize),
				Codec.INT.optionalFieldOf("life", 20).forGetter(QiParticleOptions::life),
				Codec.FLOAT.optionalFieldOf("vx", 0.0F).forGetter(QiParticleOptions::vx),
				Codec.FLOAT.optionalFieldOf("vy", 0.0F).forGetter(QiParticleOptions::vy),
				Codec.FLOAT.optionalFieldOf("vz", 0.0F).forGetter(QiParticleOptions::vz),
				Codec.BYTE.optionalFieldOf("flags", GLOW).forGetter(QiParticleOptions::flags))
				.apply(i, (core, body, rim, size, endSize, life, vx, vy, vz, flags) ->
						new QiParticleOptions(type, core, body, rim, size, endSize, life, vx, vy, vz, flags)));
	}

	static StreamCodec<RegistryFriendlyByteBuf, QiParticleOptions> streamCodec(ParticleType<QiParticleOptions> type) {
		return StreamCodec.of((buf, o) -> {
			buf.writeInt(o.core);
			buf.writeInt(o.body);
			buf.writeInt(o.rim);
			buf.writeFloat(o.size);
			buf.writeFloat(o.endSize);
			buf.writeVarInt(o.life);
			buf.writeFloat(o.vx);
			buf.writeFloat(o.vy);
			buf.writeFloat(o.vz);
			buf.writeByte(o.flags);
		}, buf -> new QiParticleOptions(type, buf.readInt(), buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte()));
	}
}
