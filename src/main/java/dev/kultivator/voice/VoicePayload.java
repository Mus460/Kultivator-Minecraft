package dev.kultivator.voice;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;

/**
 * One spoken line for one client: the client plays {@code event} itself (so its "Suara bicara" setting can mute it) on
 * {@code SoundSource.VOICE} and shows the caption. {@code positional} lines follow entity {@code entityId} when the client
 * knows it, else sound at {@code x, y, z}; the others (the voice of Heaven, the heart demon, the ancient ones) play
 * non-positional. {@code caption} is a translation key ("" for none: grunts, laughs, roars); {@code speaker} the name
 * shown over the caption (empty: the default name of {@code kind}).
 */
public record VoicePayload(String event, String caption, Optional<Component> speaker, int kind, boolean positional, int entityId, double x,
						   double y, double z, float volume, float pitch) implements CustomPacketPayload {

	public static final int KIND_HEAVEN = 0;
	public static final int KIND_DEMON = 1;
	public static final int KIND_BOSS = 2;
	public static final int KIND_ELDER = 3;
	public static final int KIND_ROGUE = 4;
	public static final int KIND_DISCIPLE = 5;
	public static final int KIND_GRUNT = 6;
	/** An old village head (the elder voice with another name). */
	public static final int KIND_VILLAGE = 7;

	public static final Type<VoicePayload> TYPE = new Type<>(Kultivator.id("voice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VoicePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeUtf(p.event, 128);
				buf.writeUtf(p.caption, 128);
				ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(buf, p.speaker);
				buf.writeVarInt(p.kind);
				buf.writeBoolean(p.positional);
				buf.writeVarInt(p.entityId + 1);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeFloat(p.volume);
				buf.writeFloat(p.pitch);
			},
			buf -> new VoicePayload(buf.readUtf(128), buf.readUtf(128), ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(buf),
					buf.readVarInt(), buf.readBoolean(), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readFloat(), buf.readFloat()));

	@Override
	public Type<VoicePayload> type() {
		return TYPE;
	}
}
