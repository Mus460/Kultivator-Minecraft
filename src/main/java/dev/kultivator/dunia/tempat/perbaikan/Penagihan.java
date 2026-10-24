package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.desa.Desa;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Damage bills (DESIGN 38.R.4, 38.R.6, 38.T.7.8 rule 6). A reported cluster with culprits gets one bill per culprit for
 * his share: {@code ceil(sum of block worth x place factor x 1.3 wage)}, at least 5 NB; Kota Iblis asks the Blood Price
 * (x2). A culprit's new damage at the same place joins his bill while it waits. Monster and raid damage asks for help
 * instead (donations, materials, a hand). The collector brings the bill the next time the culprit is within 48 blocks,
 * for three world days; a shown bill not answered within a world day counts as refused.
 * <ul>
 *   <li><b>Bayar</b>: the crew is hired (x4, three more craftsmen), Nama Baik +1 when paid within a day of the damage,
 *       a good rumour, the money to the place's purse, advancement {@code tempat/jujur}.</li>
 *   <li><b>Tawar</b> (once): Nama Baik 20+ takes 20 % off; otherwise even odds of 20 % off or a vexed collector (+10 %).</li>
 *   <li><b>Tolak</b>: Nama Baik -{@code min(30, ceil(bill / 5))}, prices +10 % at the place for 7 days; the capital also
 *       records crime points ({@code bill / 10}); a pledged village loses 10 loyalty; an NPC sect compound strains
 *       relations by 5; Kota Iblis: the enforcers turn hostile for a day and Gentar drops 10 (no Nama Baik there). The
 *       repair goes on at the base rate.</li>
 * </ul>
 */
public final class Penagihan {

	/** The wage on top of the materials (38.R.4). */
	public static final double UPAH = 1.3D;
	public static final long MINIMUM = 5L;
	/** Worth of taking a foreign block away (labour only). */
	public static final double NILAI_BUANG = 0.05D;
	/** Ticks a shown bill waits for an answer (a world day). */
	public static final long TUNGGU_JAWAB = 24000L;

	// ================================================================== making bills

	/** After a cluster is reported: bills for its culprits, or the "help wanted" notice for monster damage. */
	static void putuskan(ServerLevel level, TempatRawat t, Gugus g) {
		MinecraftServer server = level.getServer();
		PerbaikanData data = PerbaikanData.get(server);
		if (g.tanpaPelaku()) {
			g.mohonBantuan = true;
			g.tagihanId = -1L;
			if (g.ops.size() >= 6) {
				CeritaApi.kabar(level, t.situsId, "perbaikan.mohon_bantuan");
			}
			data.kotor();
			return;
		}
		if (!PerbaikanConfig.get().tagihanAktif) {
			g.tagihanId = -1L;
			data.kotor();
			return;
		}
		long last = -1L;
		for (Map.Entry<UUID, Integer> e : g.pelakuSemua().entrySet()) {
			PerbaikanData.TagihanData bill = buat(server, t, g, e.getKey());
			if (bill != null) {
				last = bill.id;
			}
		}
		g.tagihanId = last;
		data.kotor();
	}

	private static @Nullable PerbaikanData.TagihanData buat(MinecraftServer server, TempatRawat t, Gugus g, UUID who) {
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.TagihanData bill = null;
		for (PerbaikanData.TagihanData b : data.tagihan.values()) {
			if (b.pembayar.equals(who) && b.situsId == t.situsId && b.status == PerbaikanData.T_MENUNGGU) {
				bill = b;
				break;
			}
		}
		JenisTempat.Profil profil = t.profil();
		if (bill == null) {
			bill = new PerbaikanData.TagihanData(data.id(), t.situsId, t.kunci, who, profil.gaya(), Kota.day(server));
			bill.faktor = profil.faktor();
			ServerPlayer online = server.getPlayerList().getPlayer(who);
			bill.nama = online == null ? "" : online.getName().getString();
			data.tagihan.put(bill.id, bill);
		}
		int lines = 0;
		for (Gugus.Op op : g.ops) {
			if (!who.equals(op.pelaku)) {
				continue;
			}
			BlockState worthOf = op.target.isAir() ? op.ditemukan : op.target;
			double nb = op.target.isAir() ? NILAI_BUANG : NilaiBlok.nilai(worthOf);
			String group = op.target.isAir() ? "buang" : NilaiBlok.kelompok(worthOf);
			bill.rincian.merge(group, nb, Double::sum);
			bill.jumlahBlok.merge(group, 1, Integer::sum);
			lines++;
		}
		if (lines == 0) {
			return null;
		}
		if (!bill.gugus.contains(g.id)) {
			bill.gugus.add(g.id);
		}
		hitung(bill);
		data.kotor();
		return bill;
	}

	/** Recomputes the total of a bill from its lines, factor, wage, style and haggle. */
	static void hitung(PerbaikanData.TagihanData bill) {
		double base = bill.nilaiBlok() * bill.faktor * UPAH;
		long total = Math.max(MINIMUM, (long) Math.ceil(base));
		if (JenisTempat.GAYA_IBLIS.equals(bill.gaya)) {
			total *= 2L;
		}
		if (bill.tawarPersen != 0) {
			total = Math.max(MINIMUM, Math.round(total * (100 + bill.tawarPersen) / 100.0D));
		}
		bill.jumlah = total;
	}

	/** The wage part of the bill in NB (for the sheet's line "Upah 30 %"). */
	static double upah(PerbaikanData.TagihanData bill) {
		return bill.nilaiBlok() * bill.faktor * (UPAH - 1.0D);
	}

	// ================================================================== answers

	static void bayar(ServerPlayer player, TempatRawat t, PerbaikanData.TagihanData bill) {
		MinecraftServer server = player.level().getServer();
		if (bill.status != PerbaikanData.T_TERBUKA && bill.status != PerbaikanData.T_MENUNGGU) {
			return;
		}
		if (!EkonomiApi.bayar(player, bill.jumlah, "tagihan")) {
			Msg.actionBar(player, Component.translatable("kultivator.tempat_perbaikan.uang_kurang", bill.jumlah).withStyle(ChatFormatting.RED));
			return;
		}
		bill.status = PerbaikanData.T_LUNAS;
		EkonomiApi.kasKota(t.situsId, bill.jumlah);
		PerbaikanData data = PerbaikanData.get(server);
		for (long id : bill.gugus) {
			Gugus g = data.gugus.get(id);
			if (g != null) {
				g.sewa = true;
			}
		}
		if (Kota.day(server) - bill.hariDibuat <= 1L) {
			namaBaik(player, t.situsId, 1);
		}
		data.kotor();
		ServerLevel level = (ServerLevel) player.level();
		CeritaApi.kabar(level, t.situsId, "perbaikan.tagihan_dibayar", player.getName().getString());
		TempatEvents.fireTagihan(player, t.situsId, bill.jumlah, true);
		Advancements.grant(player, "tempat/jujur");
		Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.bayar.ok", bill.jumlah).withColor(0xC9E7A8));
	}

	/** One haggle per bill; returns the new total (or the old one when no haggle is left). */
	static void tawar(ServerPlayer player, TempatRawat t, PerbaikanData.TagihanData bill) {
		if (bill.sudahTawar || bill.status != PerbaikanData.T_TERBUKA) {
			return;
		}
		bill.sudahTawar = true;
		int rep = JenisTempat.GAYA_IBLIS.equals(bill.gaya) ? 0 : KotaData.get(player.level().getServer()).reputation(t.situsId, player.getUUID());
		boolean good = rep >= 20 || player.getRandom().nextBoolean();
		bill.tawarPersen = good ? -20 : 10;
		hitung(bill);
		PerbaikanData.get(player.level().getServer()).kotor();
		Penagih.tawarDijawab(player, good);
	}

	static void tolak(ServerPlayer player, TempatRawat t, PerbaikanData.TagihanData bill) {
		if (bill.status != PerbaikanData.T_TERBUKA && bill.status != PerbaikanData.T_MENUNGGU) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		bill.status = PerbaikanData.T_DITOLAK;
		PerbaikanData data = PerbaikanData.get(server);
		ServerLevel level = (ServerLevel) player.level();
		if (JenisTempat.GAYA_IBLIS.equals(bill.gaya)) {
			TemaApi.tolakTagihanIblis(player, t.situsId);
		} else {
			int loss = (int) Math.min(30L, (bill.jumlah + 4L) / 5L);
			namaBaik(player, t.situsId, -loss);
			data.pemain(player.getUUID(), t.situsId).hargaNaikSampai = Kota.day(server) + 7L;
			if (t.profil().ibuKota()) {
				KotaBesarApi.kejahatan(player, t.situsId, "tolak_tagihan", (int) Math.max(1L, bill.jumlah / 10L));
			}
			if (t.jenis.startsWith("kampung")) {
				Desa.loyaltyAt(level, t.pusat(), -10);
			}
			if (JenisTempat.GAYA_SEKTE.equals(bill.gaya)) {
				String sect = KompleksApi.sekteDi(t.situsId);
				if (sect != null) {
					SekteApi.diplomasi().adjust(player.getUUID(), sect, -5, "tolak_tagihan");
				}
			}
		}
		data.kotor();
		CeritaApi.kabar(level, t.situsId, "perbaikan.tagihan_ditolak", player.getName().getString());
		TempatEvents.fireTagihan(player, t.situsId, bill.jumlah, false);
		Msg.chat(player, Component.translatable(JenisTempat.GAYA_IBLIS.equals(bill.gaya) ? "kultivator.tempat_perbaikan.tolak.iblis"
				: "kultivator.tempat_perbaikan.tolak.ok").withColor(0xE89A7A));
	}

	/** Fear variant: pays the last would-be bill although nobody asked (fear -30, Nama Baik +5, a good rumour). */
	static void bayarRela(ServerPlayer player, TempatRawat t) {
		MinecraftServer server = player.level().getServer();
		PerbaikanData.TakutData f = PerbaikanData.get(server).takut.get(t.situsId);
		long amount = f == null ? MINIMUM : Math.max(MINIMUM, f.tagihanTerakhir);
		if (!EkonomiApi.bayar(player, amount, "tagihan_rela")) {
			Msg.actionBar(player, Component.translatable("kultivator.tempat_perbaikan.uang_kurang", amount).withStyle(ChatFormatting.RED));
			return;
		}
		EkonomiApi.kasKota(t.situsId, amount);
		Takut.turun(server, t.situsId, 30);
		namaBaik(player, t.situsId, 5);
		if (f != null) {
			f.tagihanTerakhir = 0L;
		}
		PerbaikanData data = PerbaikanData.get(server);
		for (Gugus g : data.gugus.values()) {
			if (g.kunci == t.kunci && g.takut) {
				g.takut = false;
				g.sewa = true;
			}
		}
		data.kotor();
		CeritaApi.kabar((ServerLevel) player.level(), t.situsId, "perbaikan.rendah_hati", player.getName().getString());
		TempatEvents.fireTagihan(player, t.situsId, amount, true);
		Advancements.grant(player, "tempat/jujur");
		Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.rela.ok", amount).withColor(0xC9E7A8));
	}

	/** Donation (38.R.6): repairs x2 for a day, Nama Baik +1 per 20 NB (at most +10 a day). */
	static void donasi(ServerPlayer player, TempatRawat t, long nb) {
		if (nb < MINIMUM) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (!EkonomiApi.bayar(player, nb, "donasi_perbaikan")) {
			Msg.actionBar(player, Component.translatable("kultivator.tempat_perbaikan.uang_kurang", nb).withStyle(ChatFormatting.RED));
			return;
		}
		EkonomiApi.kasKota(t.situsId, nb);
		PerbaikanData data = PerbaikanData.get(server);
		long now = server.overworld().getGameTime();
		data.donasiSampai.merge(t.situsId, now + 24000L, Math::max);
		PerbaikanData.PemainTempat pt = data.pemain(player.getUUID(), t.situsId);
		long day = Kota.day(server);
		if (pt.hariDonasi != day) {
			pt.hariDonasi = day;
			pt.namaBaikDonasiHariIni = 0;
		}
		int rep = (int) Math.min(10 - pt.namaBaikDonasiHariIni, nb / 20L);
		if (rep > 0) {
			pt.namaBaikDonasiHariIni += rep;
			namaBaik(player, t.situsId, rep);
		}
		data.kotor();
		Bantu.tambahPoin(player, t.situsId, (int) Math.min(20L, nb / 10L));
		Msg.chat(player, Component.translatable("kultivator.tempat_perbaikan.donasi.ok", nb).withColor(0xC9E7A8));
	}

	// ================================================================== helpers

	/** Nama Baik at the place (Kota Iblis has none: Gentar and Infami are its own). */
	static void namaBaik(ServerPlayer player, long situsId, int delta) {
		TempatRawat t = Rekam.bySitus(situsId);
		if (delta == 0 || t != null && JenisTempat.GAYA_IBLIS.equals(t.profil().gaya())) {
			return;
		}
		KotaData.get(player.level().getServer()).addReputation(situsId, player.getUUID(), delta);
	}

	/** The place word of {@code t} ("Kota", "Desa", "Kompleks Sekte" ...). */
	static Component namaTempat(TempatRawat t) {
		return Component.translatable(t.profil().namaKunci());
	}

	/** The open (waiting or shown) bill of {@code who}, newest first, or null. */
	static @Nullable PerbaikanData.TagihanData terbuka(MinecraftServer server, UUID who) {
		PerbaikanData.TagihanData best = null;
		for (PerbaikanData.TagihanData b : PerbaikanData.get(server).tagihan.values()) {
			if (b.pembayar.equals(who) && (b.status == PerbaikanData.T_MENUNGGU || b.status == PerbaikanData.T_TERBUKA)
					&& (best == null || b.id > best.id)) {
				best = b;
			}
		}
		return best;
	}

	/** Daily: waiting bills past their three days lapse; settled bills older than 30 days are forgotten. */
	static void harian(MinecraftServer server) {
		long day = Kota.day(server);
		PerbaikanData data = PerbaikanData.get(server);
		boolean changed = false;
		for (PerbaikanData.TagihanData b : data.tagihan.values()) {
			if (b.status == PerbaikanData.T_MENUNGGU && day > b.hariJatuhTempo) {
				b.status = PerbaikanData.T_KEDALUWARSA;
				changed = true;
			}
		}
		changed |= data.tagihan.values().removeIf(b -> b.status != PerbaikanData.T_MENUNGGU && b.status != PerbaikanData.T_TERBUKA
				&& day - b.hariDibuat > 30L);
		if (changed) {
			data.kotor();
		}
	}

	private Penagihan() {
	}
}
