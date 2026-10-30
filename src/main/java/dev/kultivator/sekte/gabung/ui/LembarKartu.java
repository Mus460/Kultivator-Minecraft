package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.gabung.dunia.TokohAnchors;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.masuk.PilihShifu;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Kartu;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Kartu Anggota ({@code gabung_kartu}, DESIGN 41.10.3, keys {@link Kartu}), the hub card "Perguruanku": the member's
 * jade card (portrait, rank with its edge colour, days, PK and Papan Jasa place, Shifu and bond, eldest senior and rival,
 * violation record, chores today, the weekly allowance, the next rank and what it still needs) and the tabs Shifu (the
 * teacher's card, next lesson, gift, changing teacher), Hukum (offences heard, a running confinement, a hearing due),
 * Murid (the player's disciples registered as wards, 41.8.7) and Keluar (the honourable leave's conditions, the blessing,
 * the deserter's warning).
 * <p>
 * Defaults of the ops (a module may replace each with {@link GabungLayar#daftarAksi}: 41A {@code pamit}/{@code belot}
 * with its ceremonies, 41C {@code jatah} at the Balai Jasa, 41D {@code daftar_murid}): the screen jumps ({@code pangkat},
 * {@code papan}, {@code paviliun}, {@code kediaman}, {@code politik}, {@code hukum}) open the registered 41 screens.
 */
public final class LembarKartu {

	/** Chores a day (41.6.4: Pelayan/Tercatat must do one, three count). */
	public static final int TUGAS_MAKS = 3;
	/** Days between two changes of teacher and the base PK cost (41.5.7). */
	public static final int GANTI_SHIFU_HARI = 30;
	public static final int GANTI_SHIFU_BIAYA = 100;
	/** PK per registered ward (41.8.7, times kf). */
	public static final int BIAYA_MURID = 50;
	/** Shifu bond for the Restu Guru (41.7.1). */
	public static final int IKATAN_RESTU = 50;

	static void init() {
		GabungLayar.daftar(Kartu.LAYAR, LembarKartu::bangun);
		lompat(Kartu.OP_PANGKAT, GabungLembar.PohonPangkat.LAYAR);
		lompat(Kartu.OP_PAPAN, GabungLembar.Papan.LAYAR);
		lompat(Kartu.OP_PAVILIUN, GabungLembar.Paviliun.LAYAR);
		lompat(Kartu.OP_KEDIAMAN, GabungLembar.Kediaman.LAYAR);
		lompat(Kartu.OP_POLITIK, GabungLembar.Politik.LAYAR);
		lompat(Kartu.OP_HUKUM, GabungLembar.Hukum.LAYAR);
		GabungLayar.bawaan(Kartu.LAYAR, Kartu.OP_JATAH, (p, npcId, op) -> GabungLayar.pesan(p,
				Component.translatable("kultivator.gabung.ui.kartu.jatah.di_balai"), LembarKunci.PESAN_PERINGATAN));
		GabungLayar.bawaan(Kartu.LAYAR, Kartu.OP_HADIAH, (p, npcId, op) -> hadiah(p));
		GabungLayar.bawaan(Kartu.LAYAR, Kartu.OP_GANTI_SHIFU, (p, npcId, op) -> gantiShifu(p));
		GabungLayar.bawaan(Kartu.LAYAR, Kartu.OP_DAFTAR_MURID, (p, npcId, op) -> murid(p, LembarKunci.arg(op, 1), true));
		GabungLayar.bawaan(Kartu.LAYAR, Kartu.OP_LEPAS_MURID, (p, npcId, op) -> murid(p, LembarKunci.arg(op, 1), false));
		GabungLayar.bawaan(Kartu.LAYAR, Kartu.OP_PAMIT, (p, npcId, op) -> keluar(p, StatusKeluar.PAMIT));
		GabungLayar.bawaan(Kartu.LAYAR, Kartu.OP_PAMIT_PAKSA, (p, npcId, op) -> keluar(p, StatusKeluar.PAMIT_PAKSA));
		GabungLayar.bawaan(Kartu.LAYAR, Kartu.OP_BELOT, (p, npcId, op) -> keluar(p, StatusKeluar.PEMBELOT));
	}

	private static void lompat(String verb, String layar) {
		GabungLayar.bawaan(Kartu.LAYAR, verb, (p, npcId, op) -> GabungLayar.bukaLayar(p, layar, npcId, null));
	}

	static CompoundTag bangun(ServerPlayer player, int npcId) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		CompoundTag tag = GabungLayar.kepala(player, sekte, Component.translatable("kultivator.gabung.ui.kartu.judul"));
		if (sekte == null) {
			CompoundTag kunci = new CompoundTag();
			LembarKunci.teks(kunci, LembarKunci.ALASAN, k.status == StatusAnggota.CALON
					? Component.translatable("kultivator.gabung.ui.kartu.calon", GabungLayar.namaSekte(k.sekte), k.hariUjian)
					: Component.translatable("kultivator.gabung.ui.bukan_anggota"));
			LembarKunci.teks(kunci, LembarKunci.SYARAT, Component.translatable("kultivator.gabung.ui.bukan_anggota.syarat"));
			tag.put(LembarKunci.KUNCI_LAYAR, kunci);
			return tag;
		}
		MinecraftServer server = player.level().getServer();
		long hari = GabungApi.hari(server);
		tag.put(Kartu.KARTU, kartu(player, k, sekte, hari));
		CompoundTag shifu = shifu(player, k, sekte, hari);
		if (shifu != null) {
			tag.put(Kartu.SHIFU, shifu);
		}
		tag.put(Kartu.HUKUM, hukum(player, k));
		tag.put(Kartu.MURID, murid(player, k, sekte));
		tag.put(Kartu.KELUAR, keluar(server, k, sekte, hari));
		if (!k.pangkat.minimal(Pangkat.LUAR)) {
			CompoundTag kunci = new CompoundTag();
			LembarKunci.teks(kunci, Kartu.TAB_MURID, Component.translatable("kultivator.gabung.ui.kartu.murid.kunci"));
			tag.put(LembarKunci.TAB_KUNCI, kunci);
		}
		return tag;
	}

	// ================================================================== tab Kartu

	private static CompoundTag kartu(ServerPlayer player, Keanggotaan k, String sekte, long hari) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = new CompoundTag();
		c.putInt(GabungLembar.POTRET, player.getId());
		LembarKunci.teks(c, LembarKunci.NAMA, player.getName());
		c.putInt(GabungLembar.PANGKAT, k.pangkat.ordinal());
		c.putInt(GabungLembar.WARNA, k.pangkat.warna());
		c.putLong(Kartu.HARI_MASUK, k.hariMasuk);
		c.putInt(Kartu.HARI_ANGGOTA, (int) k.umurAnggota(hari));
		c.putInt(LembarKunci.PK, k.pk);
		c.putLong(GabungLembar.PK_TOTAL, k.pkTotal);
		c.putInt(Kartu.PERINGKAT, peringkatJasa(server, k, sekte, hari));
		if (!k.shifu.isEmpty()) {
			TokohSekte.Tokoh t = TokohSekte.tokoh(server, k.shifu);
			CompoundTag s = new CompoundTag();
			LembarKunci.teks(s, LembarKunci.NAMA, t != null ? Component.literal(t.nama()) : GabungLayar.namaPemegang(server, k.shifu));
			s.putString(GabungLembar.TOKOH, k.shifu);
			LembarKunci.teks(s, GabungLembar.JABATAN, jabatan(t));
			s.putInt(GabungLembar.IKATAN, k.ikatanShifu);
			c.put(Kartu.SHIFU, s);
		}
		if (!k.kakak.isEmpty()) {
			CompoundTag s = new CompoundTag();
			LembarKunci.teks(s, LembarKunci.NAMA, GabungLayar.namaPemegang(server, k.kakak));
			s.putString(GabungLembar.TOKOH, k.kakak);
			c.put(Kartu.KAKAK, s);
		}
		if (!k.saingan.isEmpty()) {
			CompoundTag s = new CompoundTag();
			LembarKunci.teks(s, LembarKunci.NAMA, GabungLayar.namaPemegang(server, k.saingan));
			s.putString(GabungLembar.TOKOH, k.saingan);
			s.putInt(GabungLembar.SIKAP, Roster.sikap(server, k.saingan, player.getUUID()));
			c.put(Kartu.SAINGAN, s);
		}
		c.putInt(Kartu.PELANGGARAN, k.pelanggaran);
		c.putInt(Kartu.ATURAN, dev.kultivator.sekte.gabung.PahamAturan.sisaHari(k, hari));
		c.putInt(Kartu.TUGAS, k.tugasHari == hari ? k.tugasJumlah : 0);
		c.putInt(Kartu.TUGAS_MAKS, TUGAS_MAKS);
		c.putBoolean(Kartu.JATAH, k.jatahHari < 0L || hari - k.jatahHari >= 7L);
		if (k.status == StatusAnggota.MENYAMAR && !k.samaranSekte.isEmpty()) {
			LembarKunci.teks(c, Kartu.SAMARAN, GabungLayar.namaSekte(k.samaranSekte));
		}
		Pangkat berikut = LembarPangkat.berikutBawaan(k.pangkat);
		if (berikut != null) {
			CompoundTag b = new CompoundTag();
			b.putInt(GabungLembar.PANGKAT, berikut.ordinal());
			LembarKunci.teks(b, LembarKunci.NAMA, berikut.nama());
			ListTag syarat = new ListTag();
			LembarPangkat.syaratNaik(player, k, sekte, berikut, null).forEach(syarat::add);
			b.put(LembarKunci.SYARAT, syarat);
			c.put(Kartu.BERIKUT, b);
		}
		return c;
	}

	/** The member's place on the sect's Papan Jasa this month among the sect's players (1-based; 0 none). */
	private static int peringkatJasa(MinecraftServer server, Keanggotaan k, String sekte, long hari) {
		long bulan = Math.floorDiv(hari, 30L);
		int saya = k.bulan == bulan ? k.pkBulan : 0;
		int tempat = 1;
		GabungData data = GabungData.get(server);
		for (UUID uuid : data.anggotaSekte(sekte)) {
			Keanggotaan m = data.peek(uuid);
			if (m != null && m != k && (m.bulan == bulan ? m.pkBulan : 0) > saya) {
				tempat++;
			}
		}
		return tempat;
	}

	private static Component jabatan(@Nullable TokohSekte.Tokoh t) {
		if (t == null) {
			return Component.translatable("kultivator.gabung.ui.kartu.shifu.tetua");
		}
		if (t.ketua()) {
			return Component.translatable("kultivator.gabung.ui.kartu.shifu.ketua");
		}
		if (t.jabatan() != null) {
			return Component.translatable(t.jabatan().key());
		}
		return Component.translatable(t.penerimaan() ? "kultivator.gabung.ui.kartu.shifu.penerimaan" : "kultivator.gabung.ui.kartu.shifu.tetua");
	}

	// ================================================================== tab Shifu

	private static @Nullable CompoundTag shifu(ServerPlayer player, Keanggotaan k, String sekte, long hari) {
		if (k.shifu.isEmpty()) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		TokohSekte.Tokoh t = TokohSekte.tokoh(server, k.shifu);
		CompoundTag s = new CompoundTag();
		s.putString(GabungLembar.TOKOH, k.shifu);
		KultivatorNpc tubuh = TokohAnchors.tubuh(player.level(), sekte, k.shifu);
		s.putInt(GabungLembar.POTRET, tubuh != null && tubuh.distanceToSqr(player) < 64.0D * 64.0D ? tubuh.getId() : -1);
		LembarKunci.teks(s, LembarKunci.NAMA, t != null ? Component.literal(t.nama()) : GabungLayar.namaPemegang(server, k.shifu));
		LembarKunci.teks(s, GabungLembar.JABATAN, jabatan(t));
		if (t != null) {
			s.putInt(GabungLembar.RANAH, t.ranah().ordinal());
			s.putString(GabungLembar.DAO, t.dao().id());
			MutableComponent watak = Component.empty();
			for (int i = 0; i < t.sifat().size(); i++) {
				Sifat sf = t.sifat().get(i);
				if (i > 0) {
					watak.append(", ");
				}
				watak.append(Component.translatable(sf.key()));
			}
			LembarKunci.teks(s, GabungLembar.WATAK, watak);
		}
		s.putInt(GabungLembar.IKATAN, k.ikatanShifu);
		// weekly lessons from the joining day (41.5.7; 41C-K's Pelajaran may override the key with its own schedule)
		long awal = Math.max(0L, k.hariMasuk);
		long jarak = k.ikatanShifu >= 60 ? 4L : 7L;
		long berikut = awal + ((Math.max(0L, hari - awal) + jarak - 1L) / jarak) * jarak;
		s.putLong(Kartu.PELAJARAN, berikut);
		s.put(Kartu.MISI, new ListTag());
		CompoundTag ui = k.bagian(GabungLayar.BAGIAN_UI);
		s.putBoolean(Kartu.HADIAH, ui.getLongOr("hadiah_hari", -1L) != hari);
		CompoundTag ganti = new CompoundTag();
		int biaya = GabungApi.biaya(sekte, GANTI_SHIFU_BIAYA);
		ganti.putInt(LembarKunci.BIAYA, biaya);
		Component alasan = alasanGanti(k, sekte, hari);
		ganti.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(ganti, LembarKunci.ALASAN, alasan);
		}
		s.put(Kartu.GANTI, ganti);
		return s;
	}

	private static @Nullable Component alasanGanti(Keanggotaan k, String sekte, long hari) {
		long lalu = k.bagian(GabungLayar.BAGIAN_UI).getLongOr("ganti_shifu_hari", -1L);
		if (lalu >= 0L && hari - lalu < GANTI_SHIFU_HARI) {
			return Component.translatable("kultivator.gabung.ui.kartu.shifu.ganti_tunggu", GANTI_SHIFU_HARI - (hari - lalu));
		}
		int biaya = GabungApi.biaya(sekte, GANTI_SHIFU_BIAYA);
		if (k.pk < biaya) {
			return Component.translatable("kultivator.gabung.ui.kurang_pk", biaya);
		}
		return null;
	}

	// ================================================================== tab Hukum

	private static CompoundTag hukum(ServerPlayer player, Keanggotaan k) {
		CompoundTag h = new CompoundTag();
		h.putInt(Kartu.PELANGGARAN, k.pelanggaran);
		ListTag catatan = new ListTag();
		for (int i = k.riwayat.size() - 1; i >= 0 && catatan.size() < 12; i--) {
			Keanggotaan.Catatan c = k.riwayat.get(i);
			if (!"kultivator.gabung.riwayat.langgar".equals(c.kunci())) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putLong(LembarKunci.HARI, c.hari());
			String aturan = c.isian().isEmpty() ? "" : c.isian().getFirst();
			LembarKunci.teks(row, LembarKunci.TEKS, Component.translatable("kultivator.gabung.aturan." + aturan.toLowerCase(java.util.Locale.ROOT)));
			int poin = 0;
			if (c.isian().size() > 1) {
				try {
					poin = Integer.parseInt(c.isian().get(1));
				} catch (NumberFormatException ignored) {
					poin = 0;
				}
			}
			row.putInt(Kartu.POIN, poin);
			catatan.add(row);
		}
		h.put(LembarKunci.CATATAN, catatan);
		long sisa = k.kurunganSampai - player.level().getGameTime();
		if (k.kurunganSampai > 0L && sisa > 0L) {
			h.putLong(Kartu.KURUNGAN, sisa);
		}
		h.putBoolean(Kartu.SIDANG, dev.kultivator.sekte.gabung.BalaiHukum.sidang(player) != null);
		return h;
	}

	// ================================================================== tab Murid

	private static CompoundTag murid(ServerPlayer player, Keanggotaan k, String sekte) {
		CompoundTag m = new CompoundTag();
		int maks = maksMurid(k);
		int biaya = GabungApi.biaya(sekte, BIAYA_MURID);
		m.putInt(LembarKunci.MAKS, maks);
		m.putInt(LembarKunci.BIAYA, biaya);
		ListTag daftar = new ListTag();
		for (CompoundTag e : muridPemain(player)) {
			if (daftar.size() >= Keanggotaan.TITIPAN_MAKS * 2) {
				break;
			}
			UUID soul;
			try {
				soul = UUID.fromString(e.getStringOr("id", ""));
			} catch (IllegalArgumentException exception) {
				continue;
			}
			boolean terdaftar = k.muridTitipan.contains(soul);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, soul.toString());
			LembarKunci.teks(row, LembarKunci.NAMA, e.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?")));
			row.putInt(GabungLembar.RANAH, Rank.ofStep(e.getIntOr("step", 0)).realm().ordinal());
			row.putBoolean(Kartu.TERDAFTAR, terdaftar);
			Component alasan = terdaftar ? null : alasanDaftar(k, maks, biaya);
			row.putBoolean(LembarKunci.BISA, alasan == null);
			if (alasan != null) {
				LembarKunci.teks(row, LembarKunci.ALASAN, alasan);
			}
			daftar.add(row);
		}
		m.put(LembarKunci.DAFTAR, daftar);
		return m;
	}

	/** Wards allowed (41.8.7: Luar and above, {@code 1 + rank/2}, at most 5). */
	private static int maksMurid(Keanggotaan k) {
		return k.pangkat.minimal(Pangkat.LUAR) ? Math.min(Keanggotaan.TITIPAN_MAKS, 1 + k.pangkat.tingkat() / 2) : 0;
	}

	private static @Nullable Component alasanDaftar(Keanggotaan k, int maks, int biaya) {
		if (maks <= 0) {
			return Component.translatable("kultivator.gabung.ui.kartu.murid.kunci");
		}
		if (k.muridTitipan.size() >= maks) {
			return Component.translatable("kultivator.gabung.ui.kartu.murid.penuh", maks);
		}
		if (k.pk < biaya) {
			return Component.translatable("kultivator.gabung.ui.kurang_pk", biaya);
		}
		return null;
	}

	/** The player's living disciples as listed by {@code Disciples} (read only: id, name, step). */
	private static List<CompoundTag> muridPemain(ServerPlayer player) {
		List<CompoundTag> out = new ArrayList<>();
		CompoundTag root = Cultivators.get(player).extra().getCompoundOrEmpty(Disciples.EXTRA);
		// "list" is Disciples' own private key of the master's list (read only here)
		for (Tag tag : root.getListOrEmpty("list")) {
			if (tag instanceof CompoundTag e && !e.getBooleanOr("dead", false)) {
				out.add(e);
			}
		}
		return out;
	}

	// ================================================================== tab Keluar

	private static CompoundTag keluar(MinecraftServer server, Keanggotaan k, String sekte, long hari) {
		CompoundTag kel = new CompoundTag();
		List<CompoundTag> syarat = syaratPamit(server, k, sekte, hari);
		ListTag list = new ListTag();
		boolean bisa = true;
		for (CompoundTag s : syarat) {
			list.add(s);
			bisa &= s.getBooleanOr(LembarKunci.OK, false);
		}
		kel.put(LembarKunci.SYARAT, list);
		kel.putBoolean(LembarKunci.BISA, bisa);
		kel.putBoolean(Kartu.RESTU, bisa && !k.shifu.isEmpty() && k.ikatanShifu >= IKATAN_RESTU);
		LembarKunci.teks(kel, Kartu.PERINGATAN, Component.translatable(perangWajib(server, k, sekte)
				? "kultivator.gabung.ui.kartu.keluar.peringatan_perang" : "kultivator.gabung.ui.kartu.keluar.peringatan"));
		return kel;
	}

	/** The conditions of an honourable leave (41.7.1). */
	static List<CompoundTag> syaratPamit(MinecraftServer server, Keanggotaan k, String sekte, long hari) {
		List<CompoundTag> out = new ArrayList<>();
		out.add(GabungLayar.syarat(k.pangkat != Pangkat.PELAYAN, Component.translatable("kultivator.gabung.ui.pamit.pangkat"), null, null));
		long umur = k.umurAnggota(hari);
		out.add(GabungLayar.syarat(umur >= 5L, Component.translatable("kultivator.gabung.ui.pamit.hari", 5),
				Component.translatable("kultivator.gabung.ui.punya.hari", umur), null));
		out.add(GabungLayar.syarat(k.pelanggaran < 30, Component.translatable("kultivator.gabung.ui.pamit.pelanggaran", 30),
				Component.literal(Integer.toString(k.pelanggaran)), null));
		out.add(GabungLayar.syarat(!perangWajib(server, k, sekte), Component.translatable("kultivator.gabung.ui.pamit.perang"), null, null));
		out.add(GabungLayar.syarat(k.kediaman.isEmpty() || k.sewaSampai >= hari, Component.translatable("kultivator.gabung.ui.pamit.sewa"), null, null));
		out.add(GabungLayar.syarat(k.pangkat != Pangkat.KETUA, Component.translatable("kultivator.gabung.ui.pamit.ketua"), null, null));
		return out;
	}

	/** A running war call that binds this member (Dalam and above, 41.6.10). */
	private static boolean perangWajib(MinecraftServer server, Keanggotaan k, String sekte) {
		GabungData.DataSekteGabung ds = GabungData.get(server).peekSekte(sekte);
		return ds != null && !ds.perang.isEmpty() && k.pangkat.minimal(Pangkat.DALAM);
	}

	// ================================================================== defaults of the ops

	/** {@code hadiah}: the item in the main hand to the Shifu, bond +2, once a day (41.5.7). */
	static void hadiah(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		if (k.sekteAktif() == null || k.shifu.isEmpty()) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kartu.shifu.belum"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		long hari = GabungApi.hari(server);
		CompoundTag ui = k.bagian(GabungLayar.BAGIAN_UI);
		if (ui.getLongOr("hadiah_hari", -1L) == hari) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kartu.shifu.hadiah_sudah"), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		ItemStack tangan = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (tangan.isEmpty()) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kartu.shifu.hadiah_kosong"), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		Component barang = tangan.getHoverName();
		tangan.shrink(1);
		ui.putLong("hadiah_hari", hari);
		k.ikatanShifu = Math.min(100, k.ikatanShifu + 2);
		TokohSekte.ubahSikap(server, k.shifu, player.getUUID(), 1);
		GabungData.get(server).setDirty();
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kartu.shifu.hadiah_ok", GabungLayar.namaPemegang(server, k.shifu),
				barang, k.ikatanShifu), LembarKunci.PESAN_OK);
	}

	/** {@code ganti_shifu}: once per 30 days, 100 PK x kf, the old teacher's attitude -15, then a new choosing (41.5.7). */
	static void gantiShifu(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || k.shifu.isEmpty()) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kartu.shifu.belum"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		long hari = GabungApi.hari(server);
		Component alasan = alasanGanti(k, sekte, hari);
		int biaya = GabungApi.biaya(sekte, GANTI_SHIFU_BIAYA);
		if (alasan != null || !GabungApi.bayarPk(player, biaya, "gabung.ganti_shifu")) {
			GabungLayar.pesan(player, alasan != null ? alasan : Component.translatable("kultivator.gabung.ui.kurang_pk", biaya), LembarKunci.PESAN_BAHAYA);
			return;
		}
		String lama = k.shifu;
		Component namaLama = GabungLayar.namaPemegang(server, lama);
		TokohSekte.ubahSikap(server, lama, player.getUUID(), -15);
		k.shifu = "";
		k.ikatanShifu = PilihShifu.IKATAN_AWAL;
		k.bagian(GabungLayar.BAGIAN_UI).putLong("ganti_shifu_hari", hari);
		k.catat(hari, "kultivator.gabung.ui.riwayat.ganti_shifu", lama);
		GabungData.get(server).setDirty();
		boolean upacara = PilihShifu.mulai(player);
		GabungLayar.pesan(player, Component.translatable(upacara ? "kultivator.gabung.ui.kartu.shifu.ganti_ok"
				: "kultivator.gabung.ui.kartu.shifu.ganti_tunggu_upacara", namaLama), LembarKunci.PESAN_OK);
	}

	/** {@code daftar_murid:SOUL} / {@code lepas_murid:SOUL}: a disciple as the sect's ward (41.8.7). */
	static void murid(ServerPlayer player, String id, boolean daftar) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		UUID soul;
		try {
			soul = UUID.fromString(id);
		} catch (IllegalArgumentException exception) {
			return;
		}
		if (sekte == null) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.bukan_anggota"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		CompoundTag entri = null;
		for (CompoundTag e : muridPemain(player)) {
			if (id.equals(e.getStringOr("id", ""))) {
				entri = e;
			}
		}
		Component nama = entri == null ? Component.literal("?") : entri.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		long hari = GabungApi.hari(server);
		if (!daftar) {
			if (k.muridTitipan.remove(soul)) {
				k.catat(hari, "kultivator.gabung.ui.riwayat.lepas_murid", nama.getString());
				GabungData.get(server).setDirty();
				GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kartu.murid.dilepas", nama), LembarKunci.PESAN_PERINGATAN);
			}
			return;
		}
		if (entri == null || k.muridTitipan.contains(soul)) {
			return;
		}
		int biaya = GabungApi.biaya(sekte, BIAYA_MURID);
		Component alasan = alasanDaftar(k, maksMurid(k), biaya);
		if (alasan != null || !GabungApi.bayarPk(player, biaya, "gabung.murid_titipan")) {
			GabungLayar.pesan(player, alasan != null ? alasan : Component.translatable("kultivator.gabung.ui.kurang_pk", biaya), LembarKunci.PESAN_BAHAYA);
			return;
		}
		k.muridTitipan.add(soul);
		dev.kultivator.sekte.gabung.hidup.MuridTitipan.didaftar(player, sekte);
		k.catat(hari, "kultivator.gabung.ui.riwayat.daftar_murid", nama.getString());
		GabungData.get(server).setDirty();
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kartu.murid.didaftar", nama, GabungLayar.namaSekte(sekte)),
				LembarKunci.PESAN_OK);
	}

	/**
	 * {@code pamit} / {@code pamit_paksa} / {@code belot}: the data part of 41.7 through {@code GabungApi.keluar} (41A's
	 * {@code Keluar} replaces these with the ceremonies). An honourable leave needs every condition; leaving without leave
	 * is always possible; deserting only while a war call binds the member (41.7.3), otherwise it is a leave without leave.
	 */
	static void keluar(ServerPlayer player, StatusKeluar status) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.bukan_anggota"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		long hari = GabungApi.hari(server);
		StatusKeluar jadi = status;
		if (status == StatusKeluar.PAMIT) {
			for (CompoundTag s : syaratPamit(server, k, sekte, hari)) {
				if (!s.getBooleanOr(LembarKunci.OK, false)) {
					GabungLayar.pesan(player, LembarKunci.teks(s, LembarKunci.TEKS), LembarKunci.PESAN_BAHAYA);
					return;
				}
			}
		} else if (status == StatusKeluar.PEMBELOT && !perangWajib(server, k, sekte)) {
			jadi = StatusKeluar.PAMIT_PAKSA;
		}
		if (k.pangkat == Pangkat.KETUA) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.pamit.ketua"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		Component nama = GabungLayar.namaSekte(sekte);
		GabungApi.keluar(player, jadi);
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.kartu.keluar." + jadi.id(), nama),
				jadi == StatusKeluar.PAMIT ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA);
	}

	private LembarKartu() {
	}
}
