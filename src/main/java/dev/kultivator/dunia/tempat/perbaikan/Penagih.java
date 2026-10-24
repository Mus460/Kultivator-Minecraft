package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The collector's visit (DESIGN 38.R.4, 38.R.5): when a culprit with an open bill is within 48 blocks of the place (for
 * three world days after the damage), the place's collector (Kepala Desa, Kapten Penjaga, Tetua Sekte, the post's
 * keeper, a themed city's officer, an enforcer of Kota Iblis) walks up to him, bows a little and hands over the bill
 * ({@code tagihan} sheet). If the culprit is overwhelming (38.R.5) the collector kneels instead and asks for nothing: the
 * bill becomes a fear visit. Seized goods of the player are handed back at the same visit. Never while the player is
 * busy ({@code Sibuk}: a duel, a scene, a jail cell) or has another screen open; at most one visit every two minutes per
 * player. A shown bill left unanswered for a world day counts as refused.
 */
public final class Penagih {

	public static final double JARAK_TAGIH = 48.0D;
	/** Ticks between two visits to the same player. */
	public static final long JEDA = 2400L;
	private static final int JALAN_MAKS = 600;
	private static final int TUNGGU_MAKS = 900;

	private enum Fase {
		JALAN,
		TUNGGU,
		PULANG
	}

	private static final class Kunjungan {
		final UUID pemain;
		final long tagihanId;
		final long kunci;
		final KultivatorNpc npc;
		final boolean takut;
		Fase fase = Fase.JALAN;
		long mulai;

		Kunjungan(UUID pemain, long tagihanId, long kunci, KultivatorNpc npc, boolean takut, long mulai) {
			this.pemain = pemain;
			this.tagihanId = tagihanId;
			this.kunci = kunci;
			this.npc = npc;
			this.takut = takut;
			this.mulai = mulai;
		}
	}

	private static final Map<UUID, Kunjungan> KUNJUNGAN = new HashMap<>();
	private static final Map<UUID, Long> JEDA_PEMAIN = new HashMap<>();

	/** Every 10 ticks: visits walk, wait and leave; every 40 ticks new visits start and ignored bills lapse. */
	static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (Iterator<Kunjungan> it = KUNJUNGAN.values().iterator(); it.hasNext(); ) {
			Kunjungan k = it.next();
			if (!langkah(server, k, now)) {
				it.remove();
			}
		}
		if (now % 40L != 0L) {
			return;
		}
		PerbaikanData data = PerbaikanData.get(server);
		long game = server.overworld().getGameTime();
		for (PerbaikanData.TagihanData bill : data.tagihan.values()) {
			if (bill.status != PerbaikanData.T_MENUNGGU && bill.status != PerbaikanData.T_TERBUKA) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(bill.pembayar);
			if (player == null) {
				continue;
			}
			TempatRawat t = data.tempat.get(bill.kunci);
			if (t == null) {
				continue;
			}
			if (bill.status == PerbaikanData.T_TERBUKA && bill.ditunjukkan > 0L && game - bill.ditunjukkan > Penagihan.TUNGGU_JAWAB) {
				Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.diabaikan").withStyle(ChatFormatting.GRAY));
				Penagihan.tolak(player, t, bill);
				continue;
			}
			if (KUNJUNGAN.containsKey(player.getUUID()) || now < JEDA_PEMAIN.getOrDefault(player.getUUID(), 0L)
					|| !player.level().dimension().equals(t.dimensi) || player.isSpectator()
					|| t.jarakKotakSqr(player.blockPosition()) > JARAK_TAGIH * JARAK_TAGIH
					|| Sibuk.why(player) != null || TempatLayar.layarTerbuka(player) != null) {
				continue;
			}
			mulai(player, t, bill);
		}
	}

	/** Starts a visit: a collector body near the player walks to him. */
	static boolean mulai(ServerPlayer player, TempatRawat t, PerbaikanData.TagihanData bill) {
		ServerLevel level = (ServerLevel) player.level();
		MinecraftServer server = level.getServer();
		JEDA_PEMAIN.put(player.getUUID(), (long) server.getTickCount() + JEDA);
		BlockPos spot = null;
		for (int i = 0; i < 10 && spot == null; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2.0D;
			int r = 7 + level.getRandom().nextInt(5);
			spot = Tukang.pijak(level, player.getBlockX() + (int) Math.round(Math.cos(a) * r), player.getBlockZ() + (int) Math.round(Math.sin(a) * r),
					player.getBlockY());
		}
		if (spot == null) {
			spot = Tukang.berdiri(level, player.blockPosition());
		}
		if (spot == null) {
			return false;
		}
		KultivatorNpc npc = HidupApi.tubuh(level, t.situsId, t.profil().penagih(), spot, 0.0F);
		if (npc == null) {
			return false;
		}
		npc.memory().putBoolean("hold", true);
		npc.memory().putBoolean("perbaikan", true);
		npc.setHome(player.blockPosition());
		boolean fear = Takut.berlaku(player, t);
		KUNJUNGAN.put(player.getUUID(), new Kunjungan(player.getUUID(), bill.id, t.kunci, npc, fear, server.getTickCount()));
		HidupApi.klip(npc, GerakId.JALAN_TERGESA, 0, 0);
		return true;
	}

	/** One step of a visit; false when it is over. */
	private static boolean langkah(MinecraftServer server, Kunjungan k, long now) {
		ServerPlayer player = server.getPlayerList().getPlayer(k.pemain);
		KultivatorNpc npc = k.npc;
		if (!npc.isAlive()) {
			return false;
		}
		if (player == null || player.level() != npc.level() || player.distanceToSqr(npc) > 64.0D * 64.0D) {
			npc.vanish();
			return false;
		}
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.TagihanData bill = data.tagihan.get(k.tagihanId);
		TempatRawat t = data.tempat.get(k.kunci);
		switch (k.fase) {
			case JALAN -> {
				if (bill == null || t == null) {
					pulang(k, now);
					return true;
				}
				Vec3 offset = npc.position().subtract(player.position());
				Vec3 dir = offset.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : offset.normalize();
				npc.setHome(BlockPos.containing(player.position().add(dir.scale(2.0D))));
				HidupApi.tatap(npc, player);
				if (npc.distanceToSqr(player) <= 3.5D * 3.5D) {
					sampai(player, k, bill, t, now);
				} else if (now - k.mulai > JALAN_MAKS) {
					pulang(k, now);
				}
			}
			case TUNGGU -> {
				npc.setHome(npc.blockPosition());
				HidupApi.tatap(npc, player);
				boolean viewing = TempatLayar.sedangMelihat(player, LembarKunci.Tagihan.LAYAR) && TempatLayar.npcTerbuka(player) == npc.getId();
				if (!viewing && now - k.mulai > 100L || now - k.mulai > TUNGGU_MAKS) {
					pulang(k, now);
				}
			}
			case PULANG -> {
				if (now - k.mulai > 80L) {
					npc.vanish();
					return false;
				}
			}
		}
		return true;
	}

	private static void sampai(ServerPlayer player, Kunjungan k, PerbaikanData.TagihanData bill, TempatRawat t, long now) {
		ServerLevel level = (ServerLevel) player.level();
		KultivatorNpc npc = k.npc;
		k.fase = Fase.TUNGGU;
		k.mulai = now;
		npc.getNavigation().stop();
		npc.setHome(npc.blockPosition());
		HidupApi.tatap(npc, player);
		PerbaikanData data = PerbaikanData.get(level.getServer());
		if (k.takut) {
			if (bill.status == PerbaikanData.T_MENUNGGU || bill.status == PerbaikanData.T_TERBUKA) {
				bill.status = PerbaikanData.T_TAKUT;
				Takut.naik(level, t.situsId, bill.jumlah, player);
				for (long id : bill.gugus) {
					Gugus g = data.gugus.get(id);
					if (g != null) {
						g.takut = true;
					}
				}
				data.kotor();
			}
			HidupApi.klip(npc, GerakId.BERLUTUT, 0, 0);
			Voice.say(npc, "tempat.village_fear", null);
		} else {
			if (bill.status == PerbaikanData.T_MENUNGGU) {
				bill.status = PerbaikanData.T_TERBUKA;
				bill.ditunjukkan = level.getGameTime();
				if (bill.nama.isEmpty()) {
					bill.nama = player.getName().getString();
				}
				data.kotor();
				TempatEvents.fireTagihan(player, t.situsId, bill.jumlah, false);
			}
			HidupApi.klip(npc, GerakId.HORMAT, 0, 30);
			Voice.say(npc, "tempat.bill_request", null);
		}
		Sitaan.kembalikan(player, t.situsId);
		CompoundTag sheet = PerbaikanLayar.lembarTagihan(player, t, bill, npc, k.takut, null);
		TempatLayar.buka(player, LembarKunci.Tagihan.LAYAR, npc.getId(), sheet);
	}

	private static void pulang(Kunjungan k, long now) {
		k.fase = Fase.PULANG;
		k.mulai = now;
		KultivatorNpc npc = k.npc;
		BlockPos away = npc.blockPosition().offset(npc.getRandom().nextInt(17) - 8, 0, npc.getRandom().nextInt(17) - 8);
		npc.setHome(away);
		HidupApi.klip(npc, GerakId.JALAN_SANTAI, 0, 0);
	}

	/** The collector reacts to a haggle (a little bow, or an angry finger). */
	static void tawarDijawab(ServerPlayer player, boolean good) {
		Kunjungan k = KUNJUNGAN.get(player.getUUID());
		if (k == null) {
			return;
		}
		HidupApi.klip(k.npc, good ? GerakId.HORMAT : GerakId.MARAH_TUNJUK, 0, 40);
		if (!good) {
			Voice.say(k.npc, "tempat.bill_refused", null);
		}
	}

	/** The visit of {@code player} ends after an answer: paid (the collector thanks him), refused (he leaves angry). */
	static void selesai(ServerPlayer player, boolean dibayar) {
		Kunjungan k = KUNJUNGAN.get(player.getUUID());
		if (k == null) {
			return;
		}
		if (dibayar) {
			HidupApi.klip(k.npc, GerakId.HORMAT, 0, 30);
			Voice.say(k.npc, "tempat.bill_paid", null);
		} else {
			HidupApi.klip(k.npc, GerakId.MARAH_TUNJUK, 0, 40);
			Voice.say(k.npc, "tempat.bill_refused", null);
		}
		pulang(k, player.level().getServer().getTickCount());
	}

	/** The visit's bill id of the collector body {@code npcId} for {@code player}, or 0. */
	static long tagihanDari(ServerPlayer player, int npcId) {
		Kunjungan k = KUNJUNGAN.get(player.getUUID());
		return k != null && k.npc.getId() == npcId ? k.tagihanId : 0L;
	}

	/** True when the visit of {@code player} is a fear visit. */
	static boolean takut(ServerPlayer player) {
		Kunjungan k = KUNJUNGAN.get(player.getUUID());
		return k != null && k.takut;
	}

	/** {@code uji tempat tagihan buat}: sends the collector now (no pause, no Sibuk check). */
	static boolean paksa(ServerPlayer player, TempatRawat t, PerbaikanData.TagihanData bill) {
		Kunjungan old = KUNJUNGAN.remove(player.getUUID());
		if (old != null && old.npc.isAlive()) {
			old.npc.vanish();
		}
		JEDA_PEMAIN.remove(player.getUUID());
		return mulai(player, t, bill);
	}

	static @Nullable KultivatorNpc npc(ServerPlayer player) {
		Kunjungan k = KUNJUNGAN.get(player.getUUID());
		return k == null ? null : k.npc;
	}

	static void lupa() {
		for (Kunjungan k : KUNJUNGAN.values()) {
			if (k.npc.isAlive()) {
				k.npc.vanish();
			}
		}
		KUNJUNGAN.clear();
		JEDA_PEMAIN.clear();
	}

	private Penagih() {
	}
}
