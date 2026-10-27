package dev.kultivator.sekte.gabung;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the great halls of an NPC sect stand, for the client's class visuals (DESIGN 41.4.6, D-41V): the dome of light
 * over the main hall ({@code kompleks/aula}) and the crown over the summit hall ({@code kompleks/puncak}) that
 * {@code client.sekte.gabung.fx.VisualKelas} draws. The client has no compound data of its own, so a player within
 * {@value #JANGKAUAN} blocks of a sect's gate is told the two anchors once (again when an anchor moves, e.g. the planned
 * district centre becomes the real titik after the compound generated). Positions only; nothing here touches the world.
 */
public final class GabungTitik {

	/** Gate distance within which a sect's anchors are sent (horizontal blocks). */
	public static final int JANGKAUAN = 448;
	private static final int JEDA = 100;
	private static final int SEKTE_MAKS = 24;

	/** One sect's anchors; a null anchor is unknown. */
	public record Entri(String sekte, @Nullable BlockPos aula, @Nullable BlockPos puncak) {
	}

	/** The anchors of the sects near the player (S2C). */
	public record TitikKelas(List<Entri> entri) implements CustomPacketPayload {

		public static final Type<TitikKelas> TYPE = new Type<>(Kultivator.id("gabung_titik"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TitikKelas> CODEC = StreamCodec.of(
				(buf, p) -> {
					int n = Math.min(SEKTE_MAKS, p.entri.size());
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						Entri e = p.entri.get(i);
						buf.writeUtf(e.sekte, 16);
						tulis(buf, e.aula);
						tulis(buf, e.puncak);
					}
				},
				buf -> {
					int n = Math.min(SEKTE_MAKS, buf.readVarInt());
					List<Entri> list = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						list.add(new Entri(buf.readUtf(16), baca(buf), baca(buf)));
					}
					return new TitikKelas(List.copyOf(list));
				});

		@Override
		public Type<TitikKelas> type() {
			return TYPE;
		}
	}

	private static void tulis(RegistryFriendlyByteBuf buf, @Nullable BlockPos pos) {
		buf.writeBoolean(pos != null);
		if (pos != null) {
			buf.writeBlockPos(pos);
		}
	}

	private static @Nullable BlockPos baca(RegistryFriendlyByteBuf buf) {
		return buf.readBoolean() ? buf.readBlockPos() : null;
	}

	/** What each player was told: sect -> the two anchors as one packed key. */
	private static final Map<UUID, Map<String, Long>> DIKIRIM = new ConcurrentHashMap<>();
	private static boolean jenis;
	private static boolean berjalan;

	/** K1: the payload type, always, on both sides (from {@code Gabung.registerContent}). */
	static void daftarJenis() {
		if (jenis) {
			return;
		}
		jenis = true;
		PayloadTypeRegistry.clientboundPlay().register(TitikKelas.TYPE, TitikKelas.CODEC);
	}

	/** The server side (module on): a sweep every {@value #JEDA} ticks. */
	static void init() {
		if (berjalan) {
			return;
		}
		berjalan = true;
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DIKIRIM.remove(handler.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % JEDA != 0 || !Gabung.aktif()) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				sapu(server, p);
			}
		});
	}

	private static void sapu(MinecraftServer server, ServerPlayer p) {
		if (!ServerPlayNetworking.canSend(p, TitikKelas.TYPE)) {
			return;
		}
		Map<String, Long> sudah = DIKIRIM.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		List<Entri> baru = new ArrayList<>();
		for (GerbangPlan.Entry e : GerbangPlan.of(server.overworld().getSeed()).entries()) {
			if (baru.size() >= SEKTE_MAKS) {
				break;
			}
			double dx = p.getX() - e.blockX();
			double dz = p.getZ() - e.blockZ();
			if (dx * dx + dz * dz > (double) JANGKAUAN * JANGKAUAN) {
				continue;
			}
			BlockPos aula = KompleksApi.spot(e.id(), TitikGabung.AULA);
			BlockPos puncak = KompleksApi.spot(e.id(), TitikGabung.PUNCAK);
			if (aula == null && puncak == null) {
				continue;
			}
			long kunci = (aula == null ? 0L : aula.asLong()) * 31L + (puncak == null ? 0L : puncak.asLong());
			Long lama = sudah.get(e.id());
			if (lama == null || lama != kunci) {
				sudah.put(e.id(), kunci);
				baru.add(new Entri(e.id(), aula, puncak));
			}
		}
		if (!baru.isEmpty()) {
			ServerPlayNetworking.send(p, new TitikKelas(List.copyOf(baru)));
		}
	}

	private GabungTitik() {
	}
}
