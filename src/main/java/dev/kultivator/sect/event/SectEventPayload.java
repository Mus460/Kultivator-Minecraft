package dev.kultivator.sect.event;

import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The sect-activity part of the Sect Hall details (the same tag {@link SectEvents} adds to every {@code HallInfo} under
 * {@code "sectevent"}), sent on its own after each Tugas / Misi / Arena action so the open hall screen refreshes without
 * a full {@code HallInfo} (DESIGN 20.4.2). Module-private S2C payload, registered by {@link SectEvents#init()}.
 */
public record SectEventPayload(CompoundTag data) implements CustomPacketPayload {

	public static final Type<SectEventPayload> TYPE = new Type<>(Kultivator.id("sect_event_info"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SectEventPayload> CODEC = StreamCodec.of(
			(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
			buf -> new SectEventPayload(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

	@Override
	public Type<SectEventPayload> type() {
		return TYPE;
	}
}
