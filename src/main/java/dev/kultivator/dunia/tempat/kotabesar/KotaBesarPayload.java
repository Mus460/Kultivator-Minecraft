package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client {@code kultivator:kota_besar}: the capital's pictures (DESIGN 38.B.6, 38.O.3), drawn only by
 * {@code KotaBesarClient} under {@code EffectQuality} / {@code EpicBudget} (no post chain; layered quads).
 *
 * <pre>
 * jenis       data keys
 * festival    aktif B, x z D (city centre), y I, sampai L (game time), lentera IA (lantern street points x,y,z ...)
 * kembang     x y z D (burst centre), warna I (0-5), besar B, tunda I (ticks; the firework window 15000-16000 is open)
 * pasar_malam aktif B, town L, lapak IA (night market stall points x,y,z ...)
 * teleport    x y z D (pillar foot), detik I (channel), jenis S ("cakram" | "awan"), tiba B (arrival flash), pemain I
 *             (entity id of the traveller: his own client plays the cloud scene / flash)
 * panji       x z D (arena centre), y I, aktif B (tournament banners up), warna IA (sect colours)
 * dupa        x y z D (temple incense burner), aktif B
 * pertahanan  aktif B, x z D (gate under attack), y I, gelombang I, total I, sisa I (monsters left), roboh B,
 *             gerbang F (gate health 0..1)
 * </pre>
 */
public record KotaBesarPayload(String jenis, CompoundTag data) implements CustomPacketPayload {

	public static final Type<KotaBesarPayload> TYPE = new Type<>(Kultivator.id("kota_besar"));
	public static final StreamCodec<RegistryFriendlyByteBuf, KotaBesarPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeUtf(p.jenis, 24);
				ByteBufCodecs.COMPOUND_TAG.encode(buf, p.data);
			},
			buf -> new KotaBesarPayload(buf.readUtf(24), ByteBufCodecs.COMPOUND_TAG.decode(buf)));

	/** How far players receive a capital picture (blocks). */
	public static final double JANGKAU = 320.0D;

	private static boolean registered;

	public KotaBesarPayload {
		jenis = jenis == null ? "" : jenis;
		data = data == null ? new CompoundTag() : data;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void kirim(ServerPlayer player, String jenis, CompoundTag data) {
		Net.send(player, new KotaBesarPayload(jenis, data));
	}

	/** Sends to every player of {@code level} within {@link #JANGKAU} of {@code at}. */
	public static void sekitar(ServerLevel level, Vec3 at, String jenis, CompoundTag data) {
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) <= JANGKAU * JANGKAU) {
				Net.send(p, new KotaBesarPayload(jenis, data));
			}
		}
	}
}
