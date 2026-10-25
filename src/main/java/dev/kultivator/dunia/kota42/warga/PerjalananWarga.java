package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Movers on the road (DESIGN 42.4.8 "Perjalanan"): residents with status {@code JALAN} exist as data travelling in a
 * straight line from their old town's centre to the new one's ({@link Migrasi#JARAK_HARI} blocks a day). While a player is
 * within {@value #TUBUH_JARAK} blocks of a family's place on the road, bodies appear there ({@link HidupApi#tubuh}: the
 * old role, the resident's own look and name, a bundle on the back, children with the child's walk) and walk in a file
 * behind the leader; they leave with the player and are never saved, as in {@code KafilahJalan}. Right-clicking one
 * greets it: "Kami pindah ke Kota Air, Tuan. Di sana ada kerja." Bandits never target them (they are no enemy).
 */
public final class PerjalananWarga {

	public static final double TUBUH_JARAK = 64.0D;
	/** NPC memory key holding the traveller's resident id. */
	public static final String MEMORI = "kota42_pelancong";
	/** Spacing of a family walking in file. */
	private static final double JARAK_BARIS = 1.8D;
	/** Ticks between two greetings of one player. */
	private static final int SAPA_JEDA = 40;

	private static final Map<Integer, KultivatorNpc> TUBUH = new HashMap<>();
	private static final Map<UUID, Long> SAPA = new HashMap<>();
	private static boolean ready;

	/** Registers the tick, the greeting and the cleanup. From {@code ArsipWarga.init}; safe twice. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 7) {
				tick(server);
			}
		});
		UseEntityCallback.EVENT.register(PerjalananWarga::sapa);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			hapusSemua();
			SAPA.clear();
		});
	}

	/** The traveller a body stands for, or null. */
	public static @Nullable Warga pelancong(Entity entity) {
		if (!(entity instanceof KultivatorNpc npc) || !npc.memory().contains(MEMORI)) {
			return null;
		}
		return WargaApi.byId(npc.memory().getIntOr(MEMORI, 0));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!WargaApi.aktif() || level.players().isEmpty()) {
			hapusSemua();
			return;
		}
		WargaData data = WargaData.get(server);
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		long jam = level.getOverworldClockTime();
		double hari = Math.floorDiv(jam, 24000L) + Math.floorMod(jam, 24000L) / 24000.0D;
		Map<Integer, List<Warga>> rombongan = new HashMap<>();
		for (WargaData.Situs s : data.semuaSitus()) {
			for (Warga w : s.warga) {
				if (w.status == Warga.Status.JALAN) {
					CompoundTag t = c.peekWarga(w.id);
					int pemimpin = t == null ? w.id : t.getIntOr(Migrasi.K_ROMBONGAN, w.id);
					rombongan.computeIfAbsent(pemimpin, k -> new ArrayList<>()).add(w);
				}
			}
		}
		Set<Integer> tampil = new HashSet<>();
		for (List<Warga> r : rombongan.values()) {
			// adults lead the file, children walk between and behind them
			r.sort(Comparator.comparingInt((Warga a) -> WargaApi.umur(a) >= 16 ? 0 : 1).thenComparingInt(a -> a.id));
			Warga pertama = r.getFirst();
			WargaData.Situs asal = data.peek(data.situsDari(pertama.id));
			WargaData.Situs tujuan = data.peek(pertama.tujuanSitus);
			if (asal == null || tujuan == null || !asal.stat.contains(Pekerjaan.S_X) || !tujuan.stat.contains(Pekerjaan.S_X)) {
				continue;
			}
			double ax = asal.stat.getIntOr(Pekerjaan.S_X, 0) + 0.5D;
			double az = asal.stat.getIntOr(Pekerjaan.S_Z, 0) + 0.5D;
			double tx = tujuan.stat.getIntOr(Pekerjaan.S_X, 0) + 0.5D;
			double tz = tujuan.stat.getIntOr(Pekerjaan.S_Z, 0) + 0.5D;
			double t = Mth.clamp((hari - pertama.hariStatus) / Math.max(1.0D, pertama.tiba - pertama.hariStatus), 0.0D, 1.0D);
			double px = ax + (tx - ax) * t;
			double pz = az + (tz - az) * t;
			double panjang = Math.max(1.0E-3D, Math.hypot(tx - ax, tz - az));
			double ux = (tx - ax) / panjang;
			double uz = (tz - az) / panjang;
			if (!dekatPemain(level, px, pz)) {
				continue;
			}
			float yaw = (float) (Math.atan2(uz, ux) * Mth.RAD_TO_DEG) - 90.0F;
			for (int i = 0; i < r.size(); i++) {
				Warga w = r.get(i);
				double x = px - ux * JARAK_BARIS * i;
				double z = pz - uz * JARAK_BARIS * i;
				BlockPos kolom = BlockPos.containing(x, 0, z);
				if (!level.isPositionEntityTicking(kolom.atY(level.getSeaLevel()))) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, kolom.getX(), kolom.getZ());
				KultivatorNpc npc = TUBUH.get(w.id);
				if (npc == null || npc.isRemoved()) {
					npc = buat(level, asal.id, w, new BlockPos(kolom.getX(), y, kolom.getZ()), yaw);
					if (npc == null) {
						continue;
					}
					TUBUH.put(w.id, npc);
				} else if (npc.distanceToSqr(x, y, z) > 64.0D) {
					npc.snapTo(x, y, z, yaw, 0.0F);
				} else if (npc.distanceToSqr(x, y, z) > 2.0D) {
					npc.getNavigation().moveTo(x, y, z, 0.7D);
				}
				tampil.add(w.id);
			}
		}
		for (Iterator<Map.Entry<Integer, KultivatorNpc>> it = TUBUH.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, KultivatorNpc> e = it.next();
			if (!tampil.contains(e.getKey()) || e.getValue().isRemoved()) {
				if (!e.getValue().isRemoved()) {
					e.getValue().vanish();
				}
				it.remove();
			}
		}
	}

	private static boolean dekatPemain(ServerLevel level, double x, double z) {
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - x;
			double dz = player.getZ() - z;
			if (!player.isSpectator() && dx * dx + dz * dz < TUBUH_JARAK * TUBUH_JARAK) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable KultivatorNpc buat(ServerLevel level, long asal, Warga w, BlockPos pos, float yaw) {
		boolean anak = WargaApi.umur(w) < 13;
		NpcRole peran = anak ? NpcRole.ANAK : w.peranNpc();
		KultivatorNpc npc = HidupApi.tubuh(level, asal, peran, pos, yaw, w.look);
		if (npc == null) {
			return null;
		}
		npc.setPlate(WargaApi.nama(w));
		npc.setLonely(TUBUH_JARAK, 200);
		npc.memory().putInt(MEMORI, w.id);
		npc.memory().putBoolean("hold", false);
		SosokApi.setUsia(npc, WargaApi.usiaSosok(w));
		if (anak) {
			SosokApi.gerak(npc, GerakId.JALAN_ANAK, 0, 0);
		} else {
			SosokApi.gerak(npc, GerakId.JALAN_BAWA, GerakId.V_JALAN_BAWA_PUNGGUNG, 0, 0, 0, KotaGerakId.PROP_BUNTELAN);
		}
		return npc;
	}

	private static InteractionResult sapa(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		Warga w = pelancong(entity);
		if (w == null || w.status != Warga.Status.JALAN) {
			return InteractionResult.PASS;
		}
		long now = level.getGameTime();
		Long last = SAPA.get(sp.getUUID());
		if (last != null && now - last < SAPA_JEDA) {
			return InteractionResult.SUCCESS;
		}
		SAPA.put(sp.getUUID(), now);
		if (entity instanceof KultivatorNpc npc) {
			HidupApi.tatap(npc, sp);
			HidupApi.klip(npc, GerakId.HORMAT, 0, 30);
		}
		sp.sendSystemMessage(Component.translatable("kultivator.kota42.pindah.bicara", WargaApi.nama(w), Migrasi.ucapan(w)));
		MinecraftServer server = KotaHidup.server();
		if (server != null) {
			Ingatan.peristiwa(sp, WargaData.get(server).situsDari(w.id), w, "sapa");
		}
		return InteractionResult.SUCCESS;
	}

	private static void hapusSemua() {
		for (KultivatorNpc npc : TUBUH.values()) {
			if (!npc.isRemoved()) {
				npc.vanish();
			}
		}
		TUBUH.clear();
	}

	private PerjalananWarga() {
	}
}
