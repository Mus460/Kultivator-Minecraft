package dev.kultivator.dunia.tempat.ui;

import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client {@code kultivator:tempat_layar} (DESIGN 38.U.1, contract C38.UI1): open or refresh one screen of
 * the "Pasar Giok" kit with the sheet ({@code lembar}) a gameplay module built for it. Registered by
 * {@link TempatLayar#init()} (module 38UI's own registration, rule C2: nothing in {@code network/Payloads}).
 *
 * <ul>
 * <li>{@code layar}: screen id, one of the {@code LembarKunci.*.LAYAR} constants or a {@code tema_*} id of 38TK
 * (at most {@link TempatLayar#LAYAR_MAKS} chars). The empty string means "close the open tempat screen".</li>
 * <li>{@code npcId}: the entity the screen talks to (NPC body or service anchor entity); {@code -1} for a hub screen or a
 * screen opened at a service anchor block ({@link TempatLayar#bukaDi}).</li>
 * <li>{@code buka}: {@code true} opens the screen (or replaces the open one); {@code false} only refreshes it when the
 * same {@code layar}/{@code npcId} is already open (the live auction, a running countdown). A refresh for a screen
 * that is not open is dropped by the client.</li>
 * <li>{@code lembar}: the whole sheet; keys per screen in {@link LembarKunci}. Budget 32 KB (38.D.7), checked by the
 * server before sending.</li>
 * </ul>
 */
public record TempatLayarPayload(String layar, int npcId, boolean buka, CompoundTag lembar) implements CustomPacketPayload {

	public static final Type<TempatLayarPayload> TYPE = new Type<>(Kultivator.id("tempat_layar"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TempatLayarPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeUtf(p.layar, TempatLayar.LAYAR_MAKS);
				buf.writeVarInt(p.npcId);
				buf.writeBoolean(p.buka);
				ByteBufCodecs.COMPOUND_TAG.encode(buf, p.lembar);
			},
			buf -> new TempatLayarPayload(buf.readUtf(TempatLayar.LAYAR_MAKS), buf.readVarInt(), buf.readBoolean(),
					ByteBufCodecs.COMPOUND_TAG.decode(buf)));

	public TempatLayarPayload {
		layar = layar == null ? "" : layar;
		lembar = lembar == null ? new CompoundTag() : lembar;
	}

	/** The "close the open tempat screen" message ({@link TempatLayar#tutup}). */
	public static TempatLayarPayload tutup() {
		return new TempatLayarPayload("", -1, false, new CompoundTag());
	}

	/** True for the close message. */
	public boolean tutupLayar() {
		return layar.isEmpty();
	}

	@Override
	public Type<TempatLayarPayload> type() {
		return TYPE;
	}
}
