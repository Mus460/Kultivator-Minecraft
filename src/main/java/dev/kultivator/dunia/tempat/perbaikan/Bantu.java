package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * "Bantu Tukang" (DESIGN 38.R.6): a player stays near a working crew for two minutes (no channel, no {@code Sibuk}
 * needed: walking away more than 10 blocks for 10 seconds ends it). While he helps the crew works x1.5; finished, the
 * place gives Nama Baik +2 and 10 help points. At most three times a world day per place. 100 help points make him
 * "Sahabat {place}" with a dish of the village and the advancement {@code tempat/tukang_sukarela}. Delivering materials
 * ({@code antar}) and donations add help points too.
 */
public final class Bantu {

	/** Ticks of help that finish one session (2 minutes). */
	public static final int DURASI = 2400;
	public static final int MAKS_PER_HARI = 3;
	public static final int POIN_SESI = 10;
	public static final int POIN_SAHABAT = 100;
	private static final double JARAK = 10.0D;
	private static final int PERGI_MAKS = 200;

	private static final class Sesi {
		final long gugusId;
		final long situsId;
		final long mulai;
		int pergi;

		Sesi(long gugusId, long situsId, long mulai) {
			this.gugusId = gugusId;
			this.situsId = situsId;
			this.mulai = mulai;
		}
	}

	private static final Map<UUID, Sesi> SESI = new HashMap<>();

	/** Starts helping the crew of {@code g}; false (with a message) when the daily limit is reached or he already helps. */
	static boolean mulai(ServerPlayer player, TempatRawat t, Gugus g) {
		MinecraftServer server = player.level().getServer();
		if (SESI.containsKey(player.getUUID())) {
			Msg.actionBar(player, Component.translatable("kultivator.tempat_perbaikan.bantu.sudah").withStyle(ChatFormatting.GRAY));
			return false;
		}
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.PemainTempat pt = data.pemain(player.getUUID(), t.situsId);
		long day = Kota.day(server);
		if (pt.hariBantu != day) {
			pt.hariBantu = day;
			pt.bantuHariIni = 0;
		}
		if (pt.bantuHariIni >= MAKS_PER_HARI) {
			Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.bantu.cukup", MAKS_PER_HARI).withColor(0xE8C78A));
			return false;
		}
		pt.bantuHariIni++;
		data.kotor();
		SESI.put(player.getUUID(), new Sesi(g.id, t.situsId, server.getTickCount()));
		Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.bantu.mulai").withColor(0xC9E7A8));
		return true;
	}

	/** True while somebody is helping the crew of {@code g} (x1.5). */
	static boolean membantu(Gugus g) {
		for (Sesi s : SESI.values()) {
			if (s.gugusId == g.id && s.pergi == 0) {
				return true;
			}
		}
		return false;
	}

	/** Every 20 ticks: progress, leaving and finishing. */
	static void tick(MinecraftServer server) {
		if (SESI.isEmpty()) {
			return;
		}
		PerbaikanData data = PerbaikanData.get(server);
		long now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Sesi>> it = SESI.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Sesi> e = it.next();
			Sesi s = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Gugus g = data.gugus.get(s.gugusId);
			if (player == null || g == null) {
				it.remove();
				continue;
			}
			double dx = Math.max(0, Math.max(g.kotak.minX() - player.getX(), player.getX() - g.kotak.maxX() - 1));
			double dz = Math.max(0, Math.max(g.kotak.minZ() - player.getZ(), player.getZ() - g.kotak.maxZ() - 1));
			if (dx * dx + dz * dz > JARAK * JARAK) {
				s.pergi += 20;
				if (s.pergi >= PERGI_MAKS) {
					it.remove();
					Msg.actionBar(player, Component.translatable("kultivator.tempat_perbaikan.bantu.batal").withStyle(ChatFormatting.GRAY));
				} else {
					Msg.actionBar(player, Component.translatable("kultivator.tempat_perbaikan.bantu.kembali").withColor(0xE8C78A));
				}
				continue;
			}
			s.pergi = 0;
			long done = now - s.mulai;
			if (done >= DURASI || g.tuntas()) {
				it.remove();
				selesai(player, s, data);
				continue;
			}
			int pct = (int) (done * 100L / DURASI);
			Msg.actionBar(player, Component.translatable("kultivator.tempat_perbaikan.bantu.jalan", bar(pct), pct).withColor(0xC9E7A8));
		}
	}

	private static String bar(int pct) {
		StringBuilder b = new StringBuilder();
		int filled = Math.max(0, Math.min(10, pct / 10));
		for (int i = 0; i < 10; i++) {
			b.append(i < filled ? '■' : '□');
		}
		return b.toString();
	}

	private static void selesai(ServerPlayer player, Sesi s, PerbaikanData data) {
		Penagihan.namaBaik(player, s.situsId, 2);
		tambahPoin(player, s.situsId, POIN_SESI);
		Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.bantu.selesai", POIN_SESI).withColor(0xC9E7A8));
		data.kotor();
	}

	/** Adds help points (sessions, deliveries, donations); 100 make the player a friend of the place. */
	static void tambahPoin(ServerPlayer player, long situsId, int points) {
		if (points <= 0) {
			return;
		}
		PerbaikanData data = PerbaikanData.get(player.level().getServer());
		PerbaikanData.PemainTempat pt = data.pemain(player.getUUID(), situsId);
		pt.poinBantu += points;
		data.kotor();
		if (!pt.sahabat && pt.poinBantu >= POIN_SAHABAT) {
			pt.sahabat = true;
			TempatRawat t = Rekam.bySitus(situsId);
			Component place = t == null ? Component.translatable("kultivator.tempat_perbaikan.jenis.umum") : Penagihan.namaTempat(t);
			Msg.title(player, Component.translatable("kultivator.tempat_perbaikan.sahabat.judul").withColor(0xF2D27A),
					Component.translatable("kultivator.tempat_perbaikan.sahabat.sub", place), 10, 70, 20);
			Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.sahabat.hidangan", place).withColor(0xC9E7A8));
			player.getInventory().placeItemBackInInventory(new ItemStack(SectEventItems.SPIRIT_MEAL, 4), Prediction.SERVER_ONLY);
			Advancements.grant(player, "tempat/tukang_sukarela");
			if (player.level() instanceof ServerLevel level) {
				dev.kultivator.dunia.tempat.cerita.CeritaApi.kabar(level, situsId, "perbaikan.sahabat", player.getName().getString());
			}
		}
	}

	/** {sessions running} for {@code uji tempat gugus}. */
	static int jumlah() {
		return SESI.size();
	}

	static void lupa() {
		SESI.clear();
	}

	private Bantu() {
	}
}
