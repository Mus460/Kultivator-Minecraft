package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.sekte.KantorRekrut;
import dev.kultivator.dunia.kota42.sekte.KotaSekte;
import dev.kultivator.dunia.kota42.sekte.TugasKotaMurid;
import dev.kultivator.dunia.kota42.warga.Berbakat;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.Pekerjaan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.kultivator.dunia.kota42.ui.KotaLembar.Kantor.*;

/**
 * {@code kantor_rekrut} (DESIGN 42.11.11, keys {@link KotaLembar.Kantor}) from 42E's {@link KantorRekrut}: the player's sect
 * recruitment office in a town. Without a hall the whole screen is locked; without an office the {@code sewa} block (rent,
 * the checklist) replaces the lists; with one: tab Pelamar (rooted residents with the root shown "MID?" until the Batu Uji
 * tested it, family and their bond to the player, the stance and blessing money, a rival recruiter), tab Ujian (the open
 * test day) and tab Kantor (keeper, statistics, sect gossip). Drawn as the 42 mode of the recruitment screen
 * ({@code LayarLapakRekrut}).
 * <p>
 * The true root never travels before the stone tested it: an untested applicant carries the guess of
 * {@link KantorRekrut.Pelamar#akarTampil()}. Ops: {@code sewa}, {@code perpanjang}, {@code uji:W}, {@code terima:W},
 * {@code tolak:W}, {@code adakan_ujian}, {@code pilih:W}. Each op answers with the result line of 42E.
 */
public final class LembarKantor {

	private static final String K = LembarKotaDasar.K + "kantor.";

	static void init() {
		String layar = LAYAR;
		LembarKotaDasar.daftar(layar, LembarKantor::bangun);
		LembarKotaDasar.bawaan(layar, OP_SEWA, (p, npc, situs, op) -> jawab(p, KantorRekrut.sewaKantor(p, situs)));
		LembarKotaDasar.bawaan(layar, OP_PERPANJANG, (p, npc, situs, op) -> jawab(p, KantorRekrut.perpanjang(p, situs)));
		LembarKotaDasar.bawaan(layar, OP_UJI, (p, npc, situs, op) -> jawab(p, KantorRekrut.uji(p, situs, LembarKunci.argInt(op, 1, -1))));
		LembarKotaDasar.bawaan(layar, OP_TERIMA, (p, npc, situs, op) -> jawab(p, KantorRekrut.terima(p, situs, LembarKunci.argInt(op, 1, -1))));
		LembarKotaDasar.bawaan(layar, OP_TOLAK, (p, npc, situs, op) -> jawab(p, KantorRekrut.tolak(p, situs, LembarKunci.argInt(op, 1, -1))));
		LembarKotaDasar.bawaan(layar, OP_ADAKAN_UJIAN, (p, npc, situs, op) -> jawab(p, KantorRekrut.adakanUjian(p, situs)));
		LembarKotaDasar.bawaan(layar, "pilih", (p, npc, situs, op) -> LembarKotaDasar.setPilih(p, layar, LembarKunci.arg(op, 1)));
	}

	/** 42E already wrote its line to the chat; the sheet keeps it as its result line. */
	private static void jawab(ServerPlayer p, KotaSekte.Hasil h) {
		LembarKotaDasar.pesan(p, h.pesan(), h.ok() ? LembarKunci.PESAN_OK : LembarKunci.PESAN_PERINGATAN);
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		UUID id = player.getUUID();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(K + "judul"), LembarKunci.GAYA_GIOK);
		LembarKotaDasar.kepala(tag, player, situs);
		if (!KotaSekte.aktif()) {
			return LembarKotaDasar.kunci(tag, Component.translatable(K + "mati"));
		}
		if (Halls.hallOf(id) == null) {
			return LembarKotaDasar.kunci(tag, Component.translatable(K + "tanpa_aula"));
		}
		if (situs == 0L) {
			// a remote session (the sect card) shows the player's first office
			List<KantorRekrut.Kantor> milik = KantorRekrut.milik(server, id);
			if (milik.isEmpty()) {
				return LembarKotaDasar.kunci(tag, Component.translatable(K + "tanpa_tempat"));
			}
			situs = milik.get(0).situs();
			LembarKotaDasar.kepala(tag, player, situs);
		}
		if (KantorRekrut.bilik(server, situs) <= 0) {
			return LembarKotaDasar.kunci(tag, Component.translatable(K + "tanpa_bilik"));
		}
		KantorRekrut.Kantor kantor = KantorRekrut.kantor(situs, id);
		if (kantor == null) {
			tag.put(SEWA, sewa(player, situs));
			return tag;
		}
		long hari = KotaSekte.hari(server);
		int diterima = KantorRekrut.diterimaMinggu(server, situs, id);

		CompoundTag k = new CompoundTag();
		k.putString(LembarKunci.ID, Long.toString(situs));
		LembarKunci.teks(k, LembarKunci.NAMA, Component.translatable(K + "nama", KotaSekte.namaSekte(id), KotaSekte.namaTempat(server, situs)));
		k.putLong(KotaLembar.SAMPAI, kantor.sewaSampai());
		k.putLong(SEWA, KantorRekrut.sewa(server, situs));
		k.putInt(DITERIMA, diterima);
		k.putInt(DITERIMA_MAKS, KantorRekrut.TERIMA_MAKS);
		Component penjaga = penjaga(server, situs, id, kantor);
		if (penjaga != null) {
			LembarKunci.teks(k, PENJAGA, penjaga);
		}
		k.putBoolean(PANJI, true);
		tag.put(KANTOR, k);

		boolean kuota = diterima < KantorRekrut.TERIMA_MAKS;
		List<CompoundTag> rows = new ArrayList<>();
		for (KantorRekrut.Pelamar p : KantorRekrut.pelamar(server, situs, id)) {
			rows.add(pelamar(player, p, kuota));
		}
		tag.put(PELAMAR, LembarKotaDasar.daftar(rows));

		tag.put(UJIAN, ujian(server, situs, id, hari));

		CompoundTag s = new CompoundTag();
		int[] st = KantorRekrut.statistik(server, situs, id);
		s.putInt(DITERIMA_TOTAL, st[0]);
		s.putInt(DITOLAK, st[1]);
		s.putInt(PELAMAR_MINGGU, st[2]);
		Halls.HallView hall = Halls.hallOf(id);
		s.putInt(RENOWN, hall == null ? 0 : (int) Math.min(Integer.MAX_VALUE, hall.renown()));
		List<CompoundTag> gosip = new ArrayList<>();
		for (Component c : KantorRekrut.kabar(server, situs, id)) {
			gosip.add(LembarKotaDasar.baris(c));
			if (gosip.size() >= 8) {
				break;
			}
		}
		s.put(GOSIP, LembarKotaDasar.daftar(gosip));
		tag.put(STATISTIK, s);
		return tag;
	}

	/** The keeper: a disciple on {@code kerja_kota} duty first, else the hired resident clerk. */
	private static @Nullable Component penjaga(MinecraftServer server, long situs, UUID id, KantorRekrut.Kantor kantor) {
		TugasKotaMurid.Tugas t = TugasKotaMurid.penjagaDi(situs, TugasKotaMurid.SASARAN_KANTOR, id);
		if (t != null) {
			return Component.translatable(K + "penjaga.murid", t.nama());
		}
		Warga juru = kantor.penjaga() > 0 ? WargaApi.byId(kantor.penjaga()) : null;
		return juru == null ? null : Component.translatable(K + "penjaga.juru", WargaApi.nama(juru));
	}

	/** No office here: the rent, whether it can be paid now and the checklist. */
	private static CompoundTag sewa(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag s = new CompoundTag();
		s.putLong(LembarKunci.HARGA, KantorRekrut.sewa(server, situs));
		List<CompoundTag> syarat = new ArrayList<>();
		Component alasan = null;
		for (KantorRekrut.Syarat r : KantorRekrut.syarat(player, situs)) {
			syarat.add(LembarKotaDasar.syarat(r.ok(), r.teks(), r.punya()));
			if (!r.ok() && alasan == null) {
				alasan = r.teks();
			}
		}
		s.put(LembarKunci.SYARAT, LembarKotaDasar.daftar(syarat));
		s.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(s, LembarKunci.ALASAN, alasan);
		}
		return s;
	}

	private static CompoundTag pelamar(ServerPlayer player, KantorRekrut.Pelamar p, boolean kuota) {
		Warga w = p.warga();
		CompoundTag t = LembarKotaDasar.warga(w, player, false);
		t.putInt(KotaLembar.AKAR, p.akarTampil());
		t.putBoolean(KotaLembar.AKAR_PASTI, p.diuji());
		if (w.watak >= 0 && w.watak < Tokoh.WATAK.size()) {
			t.putString(KotaLembar.WATAK, Tokoh.WATAK.get(w.watak));
		}
		ListTag kebiasaan = new ListTag();
		for (int n : new int[] {w.kebiasaan1, w.kebiasaan2}) {
			if (n > 0) {
				CompoundTag h = new CompoundTag();
				h.putString(LembarKunci.ID, Kebiasaan.id(n));
				LembarKunci.teks(h, LembarKunci.NAMA, Kebiasaan.nama(n));
				kebiasaan.add(h);
			}
		}
		t.put(KotaLembar.KEBIASAAN, kebiasaan);
		if (w.zhuazhou > 0 && w.zhuazhou < Pekerjaan.ZHUAZHOU.length) {
			LembarKunci.teks(t, ZHUAZHOU, Component.translatable(K + "zhuazhou." + Pekerjaan.ZHUAZHOU[w.zhuazhou]));
		}
		Warga ayah = w.ayah == Warga.TANPA ? null : WargaApi.byId(w.ayah);
		if (ayah != null) {
			LembarKunci.teks(t, AYAH, WargaApi.nama(ayah));
			t.putInt(AYAH_WARGA, ayah.id);
			t.putString(AKRAB_KELUARGA, LembarKotaDasar.warga(ayah, player, false).getStringOr(KotaLembar.AKRAB, KotaLembar.AKRAB_ASING));
		}
		Warga ibu = w.ibu == Warga.TANPA ? null : WargaApi.byId(w.ibu);
		if (ibu != null) {
			LembarKunci.teks(t, IBU, WargaApi.nama(ibu));
		}
		String sikap = switch (w.sikapKeluarga) {
			case Berbakat.BANGGA -> SK_BANGGA;
			case Berbakat.RAGU -> SK_RAGU;
			case Berbakat.MENOLAK -> SK_MENOLAK;
			default -> "";
		};
		if (!sikap.isEmpty()) {
			t.putString(SIKAP, sikap);
		}
		t.putLong(RESTU, p.restu());
		t.putBoolean(ISTIMEWA, p.istimewa());
		if (p.saingan()) {
			LembarKunci.teks(t, SAINGAN, Component.translatable(K + "saingan_nama"));
			t.putInt(SAINGAN_HARI, Berbakat.SAINGAN_HARI);
		}
		t.putBoolean(LembarKunci.BISA, kuota);
		if (!kuota) {
			LembarKunci.teks(t, LembarKunci.ALASAN, Component.translatable(K + "kuota_habis", KantorRekrut.TERIMA_MAKS));
		}
		return t;
	}

	private static CompoundTag ujian(MinecraftServer server, long situs, UUID id, long hari) {
		CompoundTag u = new CompoundTag();
		long berikut = Math.max(hari, KantorRekrut.ujianBerikut(server, situs, id));
		boolean jalan = KantorRekrut.ujianBerlangsung(situs, id);
		u.putLong(BERIKUT, berikut);
		u.putLong(LembarKunci.BIAYA, KantorRekrut.BIAYA_UJIAN);
		u.putBoolean(BERLANGSUNG, jalan);
		Component alasan = jalan ? Component.translatable(K + "ujian.sedang")
				: berikut > hari ? Component.translatable(K + "ujian.tunggu", berikut) : null;
		u.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(u, LembarKunci.ALASAN, alasan);
		}
		List<CompoundTag> jadwal = new ArrayList<>();
		CompoundTag j = new CompoundTag();
		j.putLong(LembarKunci.HARI, berikut);
		LembarKunci.teks(j, LembarKunci.TEKS, Component.translatable(K + "ujian.jadwal", KantorRekrut.BIAYA_UJIAN));
		jadwal.add(j);
		u.put(JADWAL, LembarKotaDasar.daftar(jadwal));
		return u;
	}

	private LembarKantor() {
	}
}
