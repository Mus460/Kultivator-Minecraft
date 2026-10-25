package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ekonomi.Perkara;
import static dev.kultivator.dunia.kota42.ui.KotaLembar.Jabatan.*;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code jabatan} (DESIGN 42.11.9, keys {@link KotaLembar.Jabatan}) from 42C-2's {@code Jabatan}, {@code PajakKota} and
 * {@code Perkara}: the office card (rank seal, title, last wage against the band's cap, this week's duties, attendance and
 * the absence warning, rights so far, the next rank with every requirement), tab Pajak (rate slider within the rank's
 * limits, the last week's income and spending, the festival budget of a Wali Kota), tab Perkara (open and decided cases;
 * rank 3+) and tab Kavling (the town's growth lots; rank 4+). Shown on the client as tabs of the Yamen screen (one court
 * screen, jadwal 0.1 "No GUI twice").
 * <p>
 * The place: the NPC's town, else where the player stands, else the player's first office (remote from
 * {@code kota_milikku}). Extra keys (not in KotaLembar): {@link #PENJAGA} / {@link #PENJAGA_MAKS} in {@code jabatan}
 * (guards a Wali Kota hired), {@link #BOLEH_PERIKSA}. Extra ops: {@link #OP_PERIKSA} (rank 2: inspect the scales of the
 * nearest trader), {@link #OP_PENJAGA} (rank 5: hire one guard).
 */
public final class LembarJabatan {

	/** In {@code jabatan}: I extra guards hired / I their cap. */
	public static final String PENJAGA = "penjaga";
	public static final String PENJAGA_MAKS = "penjaga_maks";
	/** In {@code jabatan}: B the player may inspect scales now (rank 2+, tries left this week). */
	public static final String BOLEH_PERIKSA = "boleh_periksa";
	public static final String OP_PERIKSA = "periksa";
	public static final String OP_PENJAGA = "penjaga";

	private static final String K = LembarKotaDasar.K + "jabatan.";

	static void init() {
		String layar = LAYAR;
		LembarKotaDasar.daftar(layar, LembarJabatan::bangun);
		LembarKotaDasar.bawaan(layar, OP_AJUKAN, (p, npc, situs, op) -> ajukan(p, situs));
		LembarKotaDasar.bawaan(layar, OP_PAJAK, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				PajakKota.ubahTarif(p, situs, LembarKunci.argInt(op, 1, -1)), Component.translatable(K + "pajak_ok", LembarKunci.argInt(op, 1, 0))));
		LembarKotaDasar.bawaan(layar, OP_FESTIVAL, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				PajakKota.setAnggaranFestival(p, situs, LembarKunci.argLong(op, 1, 0L)), Component.translatable(K + "festival_ok")));
		LembarKotaDasar.bawaan(layar, OP_PERKARA, (p, npc, situs, op) -> {
			LembarKotaDasar.setPilih(p, KotaLembar.Perkara.LAYAR, LembarKunci.arg(op, 1));
			LembarKotaDasar.buka(p, KotaLembar.Perkara.LAYAR, npc);
		});
		LembarKotaDasar.bawaan(layar, OP_KAVLING, (p, npc, situs, op) -> {
			LembarKotaDasar.setPilih(p, KotaLembar.Kavling.LAYAR, LembarKunci.arg(op, 1));
			LembarKotaDasar.buka(p, KotaLembar.Kavling.LAYAR, npc);
		});
		LembarKotaDasar.bawaan(layar, OP_PERIKSA, (p, npc, situs, op) -> LembarKotaDasar.hasil(p, Jabatan.periksa(p), Component.translatable(K + "periksa_ok")));
		LembarKotaDasar.bawaan(layar, OP_PENJAGA, (p, npc, situs, op) -> LembarKotaDasar.hasil(p, Jabatan.rekrutPenjaga(p, situs),
				Component.translatable(K + "penjaga_ok")));
	}

	private static void ajukan(ServerPlayer p, long situs) {
		int sebelum = Jabatan.pangkat(p.getUUID(), situs);
		Component jawab = Jabatan.ajukan(p, situs);
		if (Jabatan.pangkat(p.getUUID(), situs) > sebelum) {
			LembarKotaDasar.pesan(p, Component.translatable(K + "naik_ok", Jabatan.gelar(p.level().getServer(), situs, sebelum + 1)), LembarKunci.PESAN_OK);
		} else if (jawab != null) {
			LembarKotaDasar.pesan(p, jawab, LembarKunci.PESAN_PERINGATAN);
		}
	}

	/** The place of the office screen: the resolved place, else the player's first office. */
	static long tempatJabatan(ServerPlayer p, long situs) {
		if (situs != 0L) {
			return situs;
		}
		Map<Long, Integer> semua = Jabatan.semua(p.level().getServer(), p.getUUID());
		return semua.isEmpty() ? 0L : semua.keySet().iterator().next();
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situsAsal) {
		MinecraftServer server = player.level().getServer();
		long situs = tempatJabatan(player, situsAsal);
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(K + "judul"), LembarKunci.GAYA_BATU);
		if (situs == 0L) {
			return LembarKotaDasar.kunci(tag, Component.translatable(K + "tanpa_tempat"));
		}
		LembarKotaDasar.kepala(tag, player, situs);
		if (!Jabatan.aktif()) {
			return LembarKotaDasar.kunci(tag, Component.translatable("kultivator.kota42.jabatan.mati"));
		}
		UUID id = player.getUUID();
		int pangkat = Jabatan.pangkat(id, situs);
		int maks = Jabatan.pangkatMaks(server, situs);
		long hari = KotaHidup.hari(server);

		CompoundTag j = new CompoundTag();
		j.putInt(PANGKAT, pangkat);
		LembarKunci.teks(j, GELAR, Jabatan.gelar(server, situs, pangkat));
		j.putLong(GAJI_MAKS, Jabatan.gajiMaks(Math.max(1, pangkat), Barang.pita(player)));
		if (pangkat > 0) {
			j.putLong(GAJI, Jabatan.gajiTerakhir(server, situs, id));
			j.putLong(HADIR, Jabatan.hadir(server, situs, id));
			Component warn = Jabatan.peringatan(server, situs, id);
			if (warn != null) {
				LembarKunci.teks(j, PERINGATAN, warn);
			}
		}
		j.put(TUGAS, tugas(server, situs, id, pangkat));
		j.put(HAK, hak(pangkat, maks));
		if (pangkat < maks) {
			CompoundTag b = new CompoundTag();
			b.putInt(PANGKAT, pangkat + 1);
			LembarKunci.teks(b, GELAR, Jabatan.gelar(server, situs, pangkat + 1));
			List<CompoundTag> rows = new ArrayList<>();
			for (Jabatan.Syarat s : Jabatan.syarat(player, situs, pangkat + 1)) {
				rows.add(LembarKotaDasar.syarat(s.ok(), s.teks(), s.punya()));
			}
			b.put(LembarKunci.SYARAT, LembarKotaDasar.daftar(rows));
			Component why = Jabatan.alasanNaik(player, situs);
			b.putBoolean(LembarKunci.BISA, why == null);
			if (why != null) {
				LembarKunci.teks(b, LembarKunci.ALASAN, why);
			}
			j.put(BERIKUT, b);
		}
		j.putInt(TEMPAT_TINGGI, Jabatan.tempatTinggi(server, id));
		j.putInt(TEMPAT_TINGGI_MAKS, Jabatan.TEMPAT_TINGGI_MAKS);
		j.putInt(PENJAGA, Jabatan.penjagaTambahan(server, situs));
		j.putInt(PENJAGA_MAKS, Jabatan.PENJAGA_MAKS);
		j.putBoolean(BOLEH_PERIKSA, pangkat >= Jabatan.PENGAWAS_PASAR
				&& Jabatan.tugasMinggu(server, situs, id, "periksa") < Jabatan.PERIKSA_MAKS);
		tag.put(JABATAN, j);

		tag.put(PAJAK, pajak(server, situs, pangkat));

		CompoundTag kunciTab = new CompoundTag();
		if (pangkat >= Jabatan.HAKIM_PEMBANTU) {
			tag.put(PERKARA, perkara(server, situs, hari));
		} else {
			LembarKunci.teks(kunciTab, TAB_PERKARA, Component.translatable(K + "kunci_perkara", Jabatan.gelar(server, situs, Jabatan.HAKIM_PEMBANTU)));
		}
		if (pangkat >= Jabatan.WAKIL) {
			tag.put(TAB_KAVLING, LembarKavling.barisKota(situs));
		} else {
			LembarKunci.teks(kunciTab, TAB_KAVLING, Component.translatable(K + "kunci_kavling", Jabatan.gelar(server, situs, Jabatan.WAKIL)));
		}
		if (maks < Jabatan.HAKIM_PEMBANTU) {
			// villages and posts have no court and no lots to choose
			ListTag hide = new ListTag();
			hide.add(StringTag.valueOf(TAB_PERKARA));
			hide.add(StringTag.valueOf(TAB_KAVLING));
			tag.put(LembarKunci.TAB_SEMBUNYI, hide);
		}
		if (!kunciTab.isEmpty()) {
			tag.put(LembarKunci.TAB_KUNCI, kunciTab);
		}
		int[] sidang = Jabatan.catatanSidang(server, id);
		CompoundTag c = new CompoundTag();
		c.putInt(DIPUTUS, sidang[0]);
		c.putInt(SUAP, sidang[1]);
		c.putInt(AUDIT, sidang[2]);
		tag.put(CATATAN, c);
		return tag;
	}

	private static ListTag tugas(MinecraftServer server, long situs, UUID id, int pangkat) {
		List<CompoundTag> rows = new ArrayList<>();
		if (pangkat <= 0) {
			rows.add(tugasBaris(Component.translatable(K + "tugas.calon"), 0, 0));
		}
		if (pangkat >= Jabatan.JURU_TULIS) {
			rows.add(tugasBaris(Component.translatable(K + "tugas.bantu"), 0, 0));
		}
		if (pangkat >= Jabatan.PENGAWAS_PASAR) {
			rows.add(tugasBaris(Component.translatable(K + "tugas.periksa"), Jabatan.tugasMinggu(server, situs, id, "periksa"), Jabatan.PERIKSA_MINGGU));
		}
		if (pangkat >= Jabatan.HAKIM_PEMBANTU) {
			rows.add(tugasBaris(Component.translatable(K + "tugas.perkara"), Jabatan.tugasMinggu(server, situs, id, "perkara"), Jabatan.PERKARA_MINGGU));
		}
		if (pangkat >= Jabatan.WAKIL) {
			rows.add(tugasBaris(Component.translatable(K + "tugas.kavling"), 0, 0));
		}
		if (pangkat >= Jabatan.WALI_KOTA) {
			rows.add(tugasBaris(Component.translatable(K + "tugas.wali"), 0, 0));
		}
		rows.add(tugasBaris(Component.translatable(K + "tugas.hadir", Jabatan.PERINGATAN_HARI), 0, 0));
		return LembarKotaDasar.daftar(rows);
	}

	private static CompoundTag tugasBaris(Component teks, int kemajuan, int maks) {
		CompoundTag t = LembarKotaDasar.baris(teks);
		if (maks > 0) {
			t.putInt(LembarKunci.KEMAJUAN, Math.min(kemajuan, maks));
			t.putInt(LembarKunci.MAKS, maks);
			t.putBoolean(SELESAI, kemajuan >= maks);
		}
		return t;
	}

	/** Rights of every rank up to {@code pangkat}; the ranks above are listed greyed by the client (I {@code pangkat}). */
	private static ListTag hak(int pangkat, int maks) {
		List<CompoundTag> rows = new ArrayList<>();
		for (int r = 1; r <= maks; r++) {
			for (int n = 0; n < (r == 5 ? 3 : 2); n++) {
				CompoundTag t = LembarKotaDasar.baris(Component.translatable(K + "hak." + r + "." + n));
				t.putInt(PANGKAT, r);
				t.putBoolean(LembarKunci.OK, r <= pangkat);
				rows.add(t);
			}
		}
		return LembarKotaDasar.daftar(rows);
	}

	private static CompoundTag pajak(MinecraftServer server, long situs, int pangkat) {
		CompoundTag p = new CompoundTag();
		int tarif = PajakKota.tarif(server, situs);
		int[] batas = PajakKota.batasUbah(server, situs, pangkat);
		p.putInt(TARIF, tarif);
		p.putInt(MIN, batas[0]);
		p.putInt(LembarKunci.MAKS, batas[1]);
		boolean bisa = PajakKota.aktif() && batas[0] != batas[1];
		p.putBoolean(LembarKunci.BISA, bisa);
		if (!bisa) {
			LembarKunci.teks(p, LembarKunci.ALASAN, Component.translatable(PajakKota.aktif() ? K + "pajak_hak" : "kultivator.kota42.pajak.mati"));
		}
		p.put(MASUK, riwayat(PajakKota.riwayat(server, situs, false)));
		p.put(KELUAR, riwayat(PajakKota.riwayat(server, situs, true)));
		LembarKunci.teks(p, EFEK, PajakKota.efek(tarif));
		if (pangkat >= Jabatan.WALI_KOTA) {
			p.putLong(FESTIVAL, PajakKota.anggaranFestival(server, situs));
			p.putLong(LembarKunci.HARGA, PajakKota.FESTIVAL_MAKS);
		}
		return p;
	}

	private static ListTag riwayat(long[] minggu) {
		List<CompoundTag> rows = new ArrayList<>();
		for (int i = 0; i < minggu.length && i < PajakKota.RIWAYAT; i++) {
			CompoundTag t = LembarKotaDasar.baris(Component.translatable(i == 0 ? K + "minggu_lalu" : K + "minggu_ke", i + 1));
			t.putLong(LembarKunci.HARGA, minggu[i]);
			rows.add(t);
		}
		return LembarKotaDasar.daftar(rows);
	}

	private static ListTag perkara(MinecraftServer server, long situs, long hari) {
		List<CompoundTag> rows = new ArrayList<>();
		for (Perkara.Kasus k : Perkara.kasus(server, situs)) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, Integer.toString(k.id()));
			LembarKunci.teks(t, LembarKunci.NAMA, Perkara.nama(k));
			t.putInt(SAKSI, k.saksi().length);
			t.putInt(BUKTI, Perkara.bukti(server, k).size());
			t.putInt(LembarKunci.SISA_HARI, (int) Math.max(0L, Perkara.BUKA_HARI - (hari - k.hari())));
			t.putBoolean(SELESAI, k.selesai());
			if (k.selesai()) {
				LembarKunci.teks(t, PUTUSAN, Perkara.putusanTeks(k, k.putusan())[0]);
			}
			rows.add(t);
		}
		// open cases first, then the newest
		rows.sort((a, b) -> Boolean.compare(a.getBooleanOr(SELESAI, false), b.getBooleanOr(SELESAI, false)));
		return LembarKotaDasar.daftar(rows);
	}

	private LembarJabatan() {
	}
}
