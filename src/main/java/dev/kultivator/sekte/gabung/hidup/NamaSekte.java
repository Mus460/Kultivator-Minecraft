package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The protection of a sect's name (DESIGN 41.4.5): nobody on the roads wants trouble with a great sect.
 * <ul>
 * <li>{@link #faktorSergap}: the ambush/bandit chance factor of M2 for a member of rank Luar and up within the class's
 * radius of the sect's gate (Kecil x0.90 within 512 blocks ... Abadi x0.55 within 2048); registered with
 * {@code Ambushes.addChanceFactor} (41A's hook) by {@link HidupAnggota}.</li>
 * <li>"Penjaga datang": a member attacked within {@link #RADIUS_PENJAGA} blocks of the gate of a Besar sect or greater is
 * helped by 2 inner disciples (Agung and Abadi: 3 and an elder) for {@link #DETIK} seconds, once a world day. They are
 * ordinary noSave bodies of 38 ({@code HidupApi.tubuh}) in the sect's uniform, who attack the attacker and leave.</li>
 * </ul>
 */
public final class NamaSekte {

	public static final int RADIUS_PENJAGA = 256;
	public static final int DETIK = 60;
	static final String K_PENJAGA = "penjaga_hari";

	/** The ambush chance factor for {@code player} (1.0 = no protection). */
	public static double faktorSergap(ServerPlayer player) {
		if (!GabungApi.aktif()) {
			return 1.0D;
		}
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungData.get(server).peek(player.getUUID());
		String sekte = k == null ? null : k.sekteAktif();
		if (sekte == null || !k.pangkat.minimal(Pangkat.LUAR) || player.level().dimension() != Level.OVERWORLD) {
			return 1.0D;
		}
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		BlockPos gate = JenisMisiGabung.pusat(sekte);
		if (gate == null) {
			return 1.0D;
		}
		double dx = gate.getX() + 0.5D - player.getX();
		double dz = gate.getZ() + 0.5D - player.getZ();
		return dx * dx + dz * dz <= (double) kelas.radiusSergap() * kelas.radiusSergap() ? kelas.faktorSergap() : 1.0D;
	}

	/** {@code ServerLivingEntityEvents.AFTER_DAMAGE}: a member hurt by a foe near the gate calls the guards. */
	static void diserang(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof ServerPlayer player) || !GabungApi.aktif() || player.level().dimension() != Level.OVERWORLD) {
			return;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity foe) || foe == player || !foe.isAlive()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		String sekte = k == null ? null : k.sekteAktif();
		if (sekte == null) {
			return;
		}
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		if (!kelas.penjagaDatang()) {
			return;
		}
		if (foe instanceof ServerPlayer other && GabungApi.anggota(other.getUUID(), sekte)) {
			// a quarrel between members is the Hall of Law's business, not the guards'
			return;
		}
		if (foe instanceof KultivatorNpc npc && isSekte(npc)) {
			return;
		}
		BlockPos gate = JenisMisiGabung.pusat(sekte);
		if (gate == null || gate.distSqr(player.blockPosition().atY(gate.getY())) > (double) RADIUS_PENJAGA * RADIUS_PENJAGA) {
			return;
		}
		long hari = Kota.day(server);
		if (k.bagian(PapanMisiSekte.BAGIAN).getLongOr(K_PENJAGA, -1L) == hari) {
			return;
		}
		k.bagian(PapanMisiSekte.BAGIAN).putLong(K_PENJAGA, hari);
		data.setDirty();
		panggil(player, sekte, kelas, foe);
	}

	private static boolean isSekte(KultivatorNpc npc) {
		NpcRole r = npc.role();
		return r == NpcRole.MURID_SEKTE_LUAR || r == NpcRole.MURID_SEKTE_DALAM || r == NpcRole.TETUA_SEKTE || r == NpcRole.PELAYAN_SEKTE;
	}

	/** Spawns the helping disciples around {@code player}; they turn on {@code foe} and leave after {@link #DETIK} s. */
	static int panggil(ServerPlayer player, String sekte, KelasSekte kelas, @Nullable LivingEntity foe) {
		ServerLevel level = player.level();
		long situs = situs(level, player, sekte);
		boolean agung = kelas.nomor() >= KelasSekte.AGUNG.nomor();
		List<NpcRole> roles = new ArrayList<>(List.of(NpcRole.MURID_SEKTE_DALAM, NpcRole.MURID_SEKTE_DALAM));
		if (agung) {
			roles.add(NpcRole.MURID_SEKTE_DALAM);
			roles.add(NpcRole.TETUA_SEKTE);
		}
		List<UUID> spawned = new ArrayList<>();
		for (int i = 0; i < roles.size(); i++) {
			double a = i * Math.PI * 2.0D / roles.size();
			int x = (int) Math.floor(player.getX() + Math.cos(a) * 4.0D);
			int z = (int) Math.floor(player.getZ() + Math.sin(a) * 4.0D);
			BlockPos ground = TempatApi.groundSpot(level, x, z, player.getBlockY());
			BlockPos at = ground != null ? ground : player.blockPosition();
			NpcRole role = roles.get(i);
			KultivatorNpc npc = HidupApi.tubuh(level, situs, role, at, (float) Math.toDegrees(a) + 90.0F);
			if (npc == null) {
				continue;
			}
			int rank = role == NpcRole.TETUA_SEKTE ? 4 : 2;
			SosokApi.setRupa(npc, RupaKunci.muridSekteNpc(rank, sekte).format());
			if (foe != null) {
				npc.setTarget(foe);
			}
			spawned.add(npc.getUUID());
		}
		if (spawned.isEmpty()) {
			return 0;
		}
		Msg.chat(player, Component.translatable("kultivator.gabung.penjaga.datang", GabungLayar.namaSekte(sekte), spawned.size())
				.withStyle(ChatFormatting.AQUA));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7F, 1.0F);
		dev.kultivator.voice.Voice.later(DETIK * 20, () -> {
			for (UUID id : spawned) {
				Entity e = level.getEntity(id);
				if (e != null && e.isAlive()) {
					e.discard();
				}
			}
		});
		return spawned.size();
	}

	/** The site the helpers belong to: the place the player stands in, else the nearest sect site near the gate. */
	private static long situs(ServerLevel level, ServerPlayer player, String sekte) {
		var info = TempatApi.tempatDi(level, player.blockPosition());
		if (info != null) {
			return info.situsId();
		}
		BlockPos gate = SekteApi.diplomasi().gate(sekte);
		BlockPos near = gate != null ? gate : player.blockPosition();
		DuniaSites.Site best = null;
		double bestSq = Double.MAX_VALUE;
		for (DuniaSites.Site s : DuniaSites.near(level, near, 320, "sekte/")) {
			double sq = s.pos().distSqr(near);
			if (sq < bestSq) {
				bestSq = sq;
				best = s;
			}
		}
		return best == null ? 0L : best.id();
	}

	private NamaSekte() {
	}
}
