package dev.kultivator.aura;

import dev.kultivator.Kultivator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Aura Kultivasi preview (DESIGN 31.6, C31.5), server to client: draw the aura of {@code realm} (a {@code Realm}
 * ordinal), {@code layer} and {@code stage} (both 0-based, like {@code Rank}) on entity {@code entityId} as if it were
 * cultivating, for {@code ticks} more ticks. {@code ticks == 0} ends the preview of that entity at once. The server
 * re-sends a live preview every 20 ticks with the ticks left, so a viewer who starts tracking the entity late catches up.
 * The Dao, root colour, root elements and the real accent still come from the subject's own synced data; nothing about
 * the subject's real rank changes. Registered by {@link AuraPreviews#init()}.
 *
 * @param flags {@link #FLAG_ACCENT}, {@link #FLAG_BOTTLENECK}, {@link #FLAG_CYCLE}
 */
public record AuraPreviewPayload(int entityId, int realm, int layer, int stage, int flags, int ticks)
		implements CustomPacketPayload {

	/** Force the accent (Heavenly gold, {@code RootGrade.HEAVENLY.color()}) even without a Heavenly root. */
	public static final int FLAG_ACCENT = 1;
	/** Show the breakthrough-ready glow (amber pulse, decal cracks, bell) as if the bottleneck were full. */
	public static final int FLAG_BOTTLENECK = 2;
	/** This preview is one step of {@code /kultivasi uji aura semua}. */
	public static final int FLAG_CYCLE = 4;

	public static final Type<AuraPreviewPayload> TYPE = new Type<>(Kultivator.id("aura_preview"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AuraPreviewPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.entityId);
				buf.writeVarInt(p.realm);
				buf.writeVarInt(p.layer);
				buf.writeVarInt(p.stage);
				buf.writeVarInt(p.flags);
				buf.writeVarInt(p.ticks);
			},
			buf -> new AuraPreviewPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt()));

	/** The "end the preview of this entity" message. */
	public static AuraPreviewPayload stop(int entityId) {
		return new AuraPreviewPayload(entityId, 0, 0, 0, 0, 0);
	}

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	/** True for the stop message ({@code ticks == 0}). */
	public boolean isStop() {
		return ticks <= 0;
	}

	@Override
	public Type<AuraPreviewPayload> type() {
		return TYPE;
	}
}
