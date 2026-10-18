package dev.kultivator.talisman;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * {@code kultivator:talisman {power, inscriber, uses}} (DESIGN 19.9): the power a talisman was inscribed with (the
 * inscriber's realm, +1/+2 from a matching beast core, 1-9), who wrote it, and the uses left.
 */
public record TalismanData(int power, String inscriber, int uses) {

	public static final int MAX_POWER = 9;

	public static final Codec<TalismanData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ExtraCodecs.intRange(0, MAX_POWER).fieldOf("power").forGetter(TalismanData::power),
			Codec.STRING.optionalFieldOf("inscriber", "").forGetter(TalismanData::inscriber),
			ExtraCodecs.intRange(1, 9).optionalFieldOf("uses", 1).forGetter(TalismanData::uses)
	).apply(instance, TalismanData::new));

	public static final StreamCodec<ByteBuf, TalismanData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TalismanData::power,
			ByteBufCodecs.STRING_UTF8, TalismanData::inscriber,
			ByteBufCodecs.VAR_INT, TalismanData::uses,
			TalismanData::new);

	public TalismanData {
		power = Math.max(0, Math.min(MAX_POWER, power));
		inscriber = inscriber == null ? "" : inscriber;
		uses = Math.max(1, Math.min(9, uses));
	}

	/** The same talisman with {@code value} uses left. */
	public TalismanData withUses(int value) {
		return new TalismanData(power, inscriber, value);
	}
}
