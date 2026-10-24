package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server to client {@code kultivator:perancah} (DESIGN 38.R.3): the repair jobs near a player, so his client draws the
 * scaffolding around them (bamboo poles, planks, lashings, a tarp, the "Mohon Bantuan" notice) as quads: never blocks.
 * Every 40 ticks each player within 96 blocks of a running job gets the list of jobs within 96 blocks (at most 16); a
 * player who had jobs and has none now gets one empty list (the client lets the scaffolds fade). Registered always
 * (38.0 rule 17).
 * <pre>
 * j  list of { id L, b IA [minX, minY, minZ, maxX, maxY, maxZ], p F progress 0..1, f B flags }
 * flags: 1 hired crew, 2 afraid (slow), 4 help wanted (notice), 8 fire (smoke-blackened poles)
 * </pre>
 */
public final class PerancahNet {

	public static final int F_SEWA = 1;
	public static final int F_TAKUT = 2;
	public static final int F_BANTUAN = 4;
	public static final int F_API = 8;
	public static final double JARAK = 96.0D;
	public static final int MAKS = 16;

	public record Payload(CompoundTag data) implements CustomPacketPayload {
		public static final Type<Payload> TYPE = new Type<>(Kultivator.id("perancah"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, p.data), buf -> new Payload(ByteBufCodecs.COMPOUND_TAG.decode(buf)));

		public Payload {
			data = data == null ? new CompoundTag() : data;
		}

		@Override
		public Type<Payload> type() {
			return TYPE;
		}
	}

	private static final Set<UUID> PUNYA = new HashSet<>();

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
	}

	/** Every 40 ticks: the scaffold lists. */
	static void kirim(MinecraftServer server) {
		PerbaikanData data = PerbaikanData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ListTag list = new ListTag();
			BlockPos at = player.blockPosition();
			for (Gugus g : data.gugus.values()) {
				if (!g.dilaporkan || g.tuntas() || list.size() >= MAKS) {
					continue;
				}
				TempatRawat t = data.tempat.get(g.kunci);
				if (t == null || !t.dimensi.equals(player.level().dimension()) || g.kotak.getCenter().distSqr(at) > JARAK * JARAK) {
					continue;
				}
				CompoundTag job = new CompoundTag();
				job.putLong("id", g.id);
				job.put("b", new IntArrayTag(new int[]{g.kotak.minX(), g.kotak.minY(), g.kotak.minZ(), g.kotak.maxX(), g.kotak.maxY(), g.kotak.maxZ()}));
				job.putFloat("p", g.persen());
				int flags = (g.sewa ? F_SEWA : 0) | (g.takut ? F_TAKUT : 0) | (g.mohonBantuan ? F_BANTUAN : 0)
						| (Gugus.API.equals(g.sebabUtama()) ? F_API : 0);
				job.putByte("f", (byte) flags);
				list.add(job);
			}
			boolean had = PUNYA.contains(player.getUUID());
			if (list.isEmpty() && !had) {
				continue;
			}
			CompoundTag tag = new CompoundTag();
			tag.put("j", list);
			Net.send(player, new Payload(tag));
			if (list.isEmpty()) {
				PUNYA.remove(player.getUUID());
			} else {
				PUNYA.add(player.getUUID());
			}
		}
	}

	static void lupa() {
		PUNYA.clear();
	}

	private PerancahNet() {
	}
}
