package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.kota42.ekonomi.TokoPemain;
import dev.kultivator.dunia.kota42.ekonomi.UsahaWarga;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.Migrasi;
import dev.kultivator.dunia.kota42.warga.NamaWarga;
import dev.kultivator.dunia.kota42.warga.Pekerjaan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Builder of {@code warga_kartu} (DESIGN 42.11.2, keys {@link KotaLembar.Kartu}): the townsperson card. Tabs Diri (who
 * the person is: age, job, temperament, habits, mood, birth, roots, zhuazhou), Keluarga (parents, spouse, children,
 * siblings, grandparents, grandchildren, in-laws; the dead and the departed from the archive), Teman (friends, lovers,
 * enemies, rivals, master/apprentice with strength and where they are now), Ingatan (every memory of the player) and
 * Pohon (a jump to {@code warga_pohon}). Buttons by state: give, employ (house help or shop clerk, only when the player
 * has a house or shop here and the person is out of work), invest (when the person runs a business), lend, invite a
 * gifted child into the player's sect.
 * <p>
 * Which person: the selection of the screen ({@link LembarKotaDasar#pilih}, set by {@code kartu:WARGA}, a Buku Kota row,
 * the family tree or {@link #buka}), else the NPC of the session.
 */
public final class LembarWarga {

	private static final String L = KotaLembar.Kartu.LAYAR;

	static void init() {
		KotaLayar.daftar(L, LembarWarga::bangun);
		KotaLayar.daftarTab(L, KotaLembar.Kartu.TAB_POHON, (player, npcId, situs, op) -> {
			Warga w = warga(player, npcId);
			if (w == null) {
				return false;
			}
			LembarKotaDasar.setPilih(player, KotaLembar.Pohon.LAYAR, Integer.toString(w.id));
			return bukaSama(player, KotaLembar.Pohon.LAYAR, npcId);
		});
		KotaLayar.bawaan(L, KotaLembar.Kartu.OP_POHON, (player, npcId, situs, op) -> {
			Warga w = warga(player, npcId);
			if (w != null) {
				LembarKotaDasar.setPilih(player, KotaLembar.Pohon.LAYAR, Integer.toString(w.id));
				bukaSama(player, KotaLembar.Pohon.LAYAR, npcId);
			}
		});
		KotaLayar.bawaan(L, KotaLembar.Kartu.OP_KARTU, (player, npcId, situs, op) -> {
			int id = LembarKunci.argInt(op, 1, 0);
			if (id > 0 && WargaApi.byId(id) != null) {
				LembarKotaDasar.setPilih(player, L, Integer.toString(id));
			}
		});
		KotaLayar.bawaan(L, KotaLembar.Kartu.OP_BERI, (player, npcId, situs, op) ->
				KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "kartu.beri_lewat_bicara"), LembarKunci.PESAN_PERINGATAN));
		KotaLayar.bawaan(L, KotaLembar.Kartu.OP_PEKERJAKAN, LembarWarga::pekerjakan);
		KotaLayar.bawaan(L, KotaLembar.Kartu.OP_TANAM, (player, npcId, situs, op) -> {
			Warga w = warga(player, npcId);
			long s = w == null ? 0L : situsDari(w);
			UsahaWarga.Usaha u = w == null || s == 0L ? null : UsahaWarga.milikWarga(s, w.id);
			if (u == null) {
				KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "kartu.tanpa_usaha"), LembarKunci.PESAN_PERINGATAN);
				return;
			}
			LembarKotaDasar.setPilih(player, KotaLembar.Investasi.LAYAR, s + ":" + u.id());
			LembarKotaDasar.pilihSitus(player, s);
			KotaLayar.bukaJauh(player, KotaLembar.Investasi.LAYAR, KotaLembar.Investasi.TAB_PELUANG);
		});
		KotaLayar.bawaan(L, KotaLembar.Kartu.OP_AJAK_SEKTE, LembarWarga::ajakSekte);
	}

	/** Opens the card of resident {@code wargaId} for {@code npcId} (-1: remote, read only). */
	public static boolean buka(ServerPlayer player, int npcId, int wargaId) {
		LembarKotaDasar.setPilih(player, L, Integer.toString(wargaId));
		return bukaSama(player, L, npcId);
	}

	/** Opens the card of the selected person on tab {@code tab}, same kind of session as the current one. */
	static boolean bukaTab(ServerPlayer player, int npcId, String tab) {
		if (npcId < 0 || player.level().getEntity(npcId) == null) {
			return KotaLayar.bukaJauh(player, L, tab);
		}
		CompoundTag tag = KotaLayar.bangun(L, player, npcId);
		if (tag == null) {
			return false;
		}
		tag.putString(LembarKunci.TAB, tab);
		dev.kultivator.dunia.tempat.ui.TempatLayar.buka(player, L, npcId, tag);
		return true;
	}

	/** Opens {@code layar} with the same kind of session as the current one (NPC, else remote). */
	static boolean bukaSama(ServerPlayer player, String layar, int npcId) {
		return npcId >= 0 ? KotaLayar.buka(player, layar, npcId) : KotaLayar.bukaJauh(player, layar, null);
	}

	/** The person shown: the selection, else the session's NPC. */
	static @Nullable Warga warga(ServerPlayer player, int npcId) {
		String pilih = LembarKotaDasar.pilih(player, L);
		if (!pilih.isEmpty()) {
			try {
				Warga w = WargaApi.byId(Integer.parseInt(pilih));
				if (w != null) {
					return w;
				}
			} catch (NumberFormatException ignored) {
				// not a resident id
			}
		}
		KultivatorNpc npc = KotaLayar.npc(player, npcId);
		return npc == null ? null : WargaApi.dari(npc);
	}

	static long situsDari(Warga w) {
		MinecraftServer server = KotaHidup.server();
		return server == null ? 0L : WargaData.get(server).situsDari(w.id);
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situsSesi) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, null, LembarKunci.GAYA_KERTAS);
		Warga w = warga(player, npcId);
		if (w == null) {
			LembarKotaDasar.kepala(tag, player, situsSesi);
			return LembarKotaDasar.kunci(tag, Component.translatable(KotaLayar.K + "kartu.tidak_ada"));
		}
		long situs = situsDari(w);
		LembarKotaDasar.kepala(tag, player, situs != 0L ? situs : situsSesi);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(KotaLayar.K + "kartu.judul", WargaApi.nama(w)));
		tag.putInt(KotaLembar.WARGA, w.id);
		long hari = KotaHidup.hari(server);
		WargaData data = WargaData.get(server);
		tag.put(KotaLembar.Kartu.DIRI, diri(server, data, player, w, situs, hari));
		Ingatan ing = WargaApi.ingatan(w, player.getUUID());
		tag.put(KotaLembar.Kartu.PEMAIN, pemain(ing));
		tag.put(KotaLembar.AKSI, aksi(server, player, npcId, w, situs, ing));
		tag.put(KotaLembar.Kartu.KELUARGA, keluarga(data, player, w));
		tag.put(KotaLembar.Kartu.TEMAN, teman(data, w, hari));
		tag.put(KotaLembar.Kartu.INGATAN, ingatan(ing));
		ListTag sembunyi = new ListTag();
		if (w.status == Warga.Status.MATI) {
			sembunyi.add(net.minecraft.nbt.StringTag.valueOf(KotaLembar.Kartu.TAB_INGATAN));
		}
		if (!sembunyi.isEmpty()) {
			tag.put(LembarKunci.TAB_SEMBUNYI, sembunyi);
		}
		return tag;
	}

	// ================================================================== tab Diri

	private static CompoundTag diri(MinecraftServer server, WargaData data, ServerPlayer player, Warga w, long situs, long hari) {
		CompoundTag d = LembarKotaDasar.warga(w, player, true);
		LembarKunci.teks(d, KotaLembar.NAMA_HAN, Component.literal(NamaWarga.hanzi(w)));
		d.putInt(KotaLembar.SUASANA, Mth.clamp(w.suasana, -100, 100));
		if (w.watak >= 0 && w.watak < Tokoh.WATAK.size()) {
			LembarKunci.teks(d, KotaLembar.WATAK_NAMA, Component.translatable("kultivator.kota42.watak." + Tokoh.WATAK.get(w.watak)));
		}
		NpcRole peran = w.peranNpc();
		Component tempat = situs == 0L ? Component.empty() : KotaNames.town(situs);
		UUID majikan = WargaApi.majikan(w);
		Component kerja;
		if (majikan != null && majikan.equals(player.getUUID())) {
			kerja = Component.translatable(KotaLayar.K + "kartu.kerja_untukmu", Component.translatable(peran.titleKey()));
		} else if (Pekerjaan.bekerja(w) || peran != NpcRole.WARGA) {
			kerja = Component.translatable(KotaLayar.K + "kartu.kerja", Component.translatable(peran.titleKey()), tempat);
		} else {
			kerja = Component.translatable(KotaLayar.K + "kartu.menganggur");
		}
		LembarKunci.teks(d, KotaLembar.KERJA, kerja);
		LembarKunci.teks(d, KotaLembar.Kartu.TEMPAT_KERJA, tempat);
		CompoundTag lahir = new CompoundTag();
		lahir.putLong(LembarKunci.HARI, hariLahir(w.lahir));
		long asal = Migrasi.asal(w);
		LembarKunci.teks(lahir, LembarKunci.TEMPAT, asal != 0L ? KotaNames.town(asal) : tempat);
		d.put(KotaLembar.Kartu.LAHIR, lahir);
		if (w.datang >= 0L && asal != 0L && asal != situs) {
			LembarKunci.teks(d, KotaLembar.Kartu.ASAL, Component.translatable(KotaLayar.K + "kartu.pendatang", KotaNames.town(asal), w.datang));
		}
		d.putInt(KotaLembar.Kartu.KESEHATAN, Mth.clamp(w.kesehatan, 0, 100));
		d.putBoolean(KotaLembar.Kartu.SAKIT, w.kesehatan < 35);
		if (w.zhuazhou > 0 && w.zhuazhou < Pekerjaan.ZHUAZHOU.length) {
			CompoundTag z = new CompoundTag();
			z.putString(LembarKunci.ID, Pekerjaan.ZHUAZHOU[w.zhuazhou]);
			LembarKunci.teks(z, LembarKunci.NAMA, Component.translatable(KotaLayar.K + "zhuazhou." + Pekerjaan.ZHUAZHOU[w.zhuazhou]));
			d.put(KotaLembar.Kartu.ZHUAZHOU, z);
		}
		ListTag kebiasaan = new ListTag();
		for (int k : new int[] {w.kebiasaan1, w.kebiasaan2}) {
			if (k > 0) {
				CompoundTag h = new CompoundTag();
				h.putString(LembarKunci.ID, Kebiasaan.id(k));
				LembarKunci.teks(h, LembarKunci.NAMA, Kebiasaan.nama(k));
				kebiasaan.add(h);
			}
		}
		if (!kebiasaan.isEmpty()) {
			d.put(KotaLembar.KEBIASAAN, kebiasaan);
		}
		d.putInt(LembarKunci.POTRET, tubuh(w, situs));
		d.putInt(KotaLembar.AKAR, w.akar);
		d.putBoolean(KotaLembar.AKAR_PASTI, w.akar > 0 && dev.kultivator.dunia.kota42.warga.Berbakat.tampak(w));
		String status = switch (w.status) {
			case HIDUP -> KotaLembar.ST_HIDUP;
			case JALAN -> KotaLembar.ST_JALAN;
			case SEKTE -> KotaLembar.ST_SEKTE;
			case MATI -> KotaLembar.ST_ARSIP;
		};
		d.putString(KotaLembar.STATUS, status);
		if (w.status == Warga.Status.JALAN && w.tujuanSitus != 0L) {
			LembarKunci.teks(d, KotaLembar.LOKASI, Component.translatable(KotaLayar.K + "lokasi.jalan", KotaNames.town(w.tujuanSitus)));
		} else if (situs != 0L) {
			LembarKunci.teks(d, KotaLembar.LOKASI, Component.translatable(KotaLayar.K + "lokasi.di", tempat));
		}
		return d;
	}

	/** The world day of a birth stored in tenths of a day ({@code Warga.lahir}, {@code ArsipWarga.Entri.lahir}). */
	static long hariLahir(long lahirPersepuluh) {
		return Math.floorDiv(lahirPersepuluh, 10L);
	}

	/** The entity id of the person's live body near the player, else -1. */
	static int tubuh(Warga w, long situs) {
		if (w.slot == Warga.TANPA_SLOT || situs == 0L) {
			return -1;
		}
		TempatHuni huni = Pemanggil.huni(situs);
		KultivatorNpc npc = huni == null ? null : huni.tubuhDari(w.slot);
		return npc == null || npc.isRemoved() ? -1 : npc.getId();
	}

	// ================================================================== "Tentang kamu" and memories

	private static CompoundTag pemain(@Nullable Ingatan ing) {
		CompoundTag p = new CompoundTag();
		if (ing == null) {
			return p;
		}
		p.putInt(KotaLembar.KENAL, ing.kenal);
		p.putInt(KotaLembar.KESAN, ing.kesan);
		p.putBoolean(KotaLembar.TAHU_NAMA, ing.nama);
		p.putInt(KotaLembar.Kartu.BANTU, ing.bantu);
		p.putLong(KotaLembar.Kartu.UTANG, ing.utang);
		p.putLong(KotaLembar.Kartu.BERIKUT, ing.hadiahHari + Ingatan.HADIAH_JEDA);
		ListTag kenangan = new ListTag();
		for (CompoundTag k : kenangan(ing)) {
			if (kenangan.size() >= 4) {
				break;
			}
			kenangan.add(k);
		}
		p.put(KotaLembar.Kartu.KENANGAN, kenangan);
		return p;
	}

	/** The remembered events, strongest first. */
	private static List<CompoundTag> kenangan(Ingatan ing) {
		List<int[]> slot = new ArrayList<>();
		for (int i = 0; i < Ingatan.PERISTIWA; i++) {
			Kenangan k = ing.kenangan(i);
			if (k != null) {
				slot.add(new int[] {i, Math.abs(k.delta), ing.hariKenangan(i)});
			}
		}
		slot.sort((a, b) -> a[1] != b[1] ? Integer.compare(b[1], a[1]) : Integer.compare(b[2], a[2]));
		List<CompoundTag> out = new ArrayList<>();
		for (int[] s : slot) {
			Kenangan k = ing.kenangan(s[0]);
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, k.id());
			LembarKunci.teks(t, LembarKunci.NAMA, k.nama());
			t.putLong(LembarKunci.HARI, s[2]);
			t.putBoolean(KotaLembar.Kartu.KUAT, k.kuat);
			out.add(t);
		}
		return out;
	}

	private static ListTag ingatan(@Nullable Ingatan ing) {
		ListTag list = new ListTag();
		if (ing == null) {
			return list;
		}
		for (CompoundTag k : kenangan(ing)) {
			Kenangan jenis = Kenangan.byId(k.getStringOr(LembarKunci.ID, ""));
			if (jenis != null) {
				LembarKunci.teks(k, LembarKunci.TEKS, Component.translatable(KotaLayar.K + "ingatan." + jenis.id()));
				k.putInt(KotaLembar.KESAN, jenis.delta);
			}
			list.add(k);
		}
		if (ing.bantu > 0) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, "bantu");
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(KotaLayar.K + "ingatan.bantu_n", ing.bantu));
			LembarKunci.teks(t, LembarKunci.TEKS, Component.translatable(KotaLayar.K + "ingatan.bantu_teks"));
			t.putLong(LembarKunci.HARI, ing.temuHari);
			list.add(t);
		}
		if (ing.utang != 0) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, "utang");
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(ing.utang > 0 ? KotaLayar.K + "ingatan.utang_dia" : KotaLayar.K + "ingatan.utang_kamu",
					Math.abs(ing.utang)));
			LembarKunci.teks(t, LembarKunci.TEKS, Component.translatable(KotaLayar.K + "ingatan.utang_teks"));
			t.putLong(LembarKunci.HARI, ing.temuHari);
			list.add(t);
		}
		return list;
	}

	// ================================================================== buttons

	private static ListTag aksi(MinecraftServer server, ServerPlayer player, int npcId, Warga w, long situs, @Nullable Ingatan ing) {
		ListTag list = new ListTag();
		if (w.status != Warga.Status.HIDUP) {
			return list;
		}
		boolean dekat = npcId >= 0 && KotaLayar.npc(player, npcId) instanceof KultivatorNpc npc && WargaApi.dari(npc) == w;
		Component jauh = dekat ? null : Component.translatable(KotaLayar.K + "kartu.harus_dekat");
		list.add(LembarKotaDasar.tombol(KotaLembar.Kartu.A_BERI, Component.translatable(KotaLayar.K + "kartu.a.beri"), jauh));
		int umur = WargaApi.umur(w);
		if (umur >= 16 && !Pekerjaan.bekerja(w) && w.peranNpc() == NpcRole.WARGA) {
			String tugas = tugasKerja(server, player, situs);
			Component alasan = tugas == null ? Component.translatable(KotaLayar.K + "kartu.tanpa_tempat_kerja") : jauh;
			CompoundTag t = LembarKotaDasar.tombol(KotaLembar.Kartu.A_PEKERJAKAN, Component.translatable(KotaLayar.K + "kartu.a.pekerjakan"), alasan);
			if (tugas != null) {
				t.putString(LembarKunci.OP, LembarKunci.op(KotaLembar.Kartu.OP_PEKERJAKAN, tugas));
			}
			list.add(t);
		} else if (umur >= 16) {
			list.add(LembarKotaDasar.tombol(KotaLembar.Kartu.A_PEKERJAKAN, Component.translatable(KotaLayar.K + "kartu.a.pekerjakan"),
					Component.translatable(KotaLayar.K + "kartu.bekerja")));
		}
		if (situs != 0L && UsahaWarga.milikWarga(situs, w.id) != null) {
			list.add(LembarKotaDasar.tombol(KotaLembar.Kartu.A_TANAM, Component.translatable(KotaLayar.K + "kartu.a.tanam"), null));
		}
		for (String verb : new String[] {KotaLembar.Kartu.A_PINJAM, KotaLembar.Kartu.A_PINJAMKAN}) {
			if (KotaLayar.adaModul(L, verb)) {
				int kesan = ing == null ? 0 : ing.kesan;
				Component alasan = kesan < 20 ? Component.translatable(KotaLayar.K + "kartu.kurang_akrab", 20) : jauh;
				list.add(LembarKotaDasar.tombol(verb, Component.translatable(KotaLayar.K + "kartu.a." + verb), alasan));
			}
		}
		if (w.akar >= 2 && umur >= 6 && umur <= 18) {
			Component alasan = Halls.hallOf(player.getUUID()) == null ? Component.translatable(KotaLayar.K + "kartu.tanpa_aula")
					: dev.kultivator.dunia.kota42.warga.Berbakat.perluRestu(w) ? Component.translatable(KotaLayar.K + "kartu.perlu_restu") : jauh;
			list.add(LembarKotaDasar.tombol(KotaLembar.Kartu.A_AJAK_SEKTE, Component.translatable(KotaLayar.K + "kartu.a.ajak_sekte"), alasan));
		}
		return list;
	}

	/** {@code pembantu:RUMAH} for a house of the player here, else {@code pegawai:TOKO} for a shop without clerk, else null. */
	private static @Nullable String tugasKerja(MinecraftServer server, ServerPlayer player, long situs) {
		if (situs == 0L) {
			return null;
		}
		RumahKota.Info rumah = RumahKota.milikDiTempat(server, situs, player.getUUID());
		if (rumah != null && rumah.pembantu() == 0) {
			return KotaLembar.Kartu.TUGAS_PEMBANTU + ":" + rumah.id();
		}
		for (TokoPemain.Toko toko : TokoPemain.milik(server, player.getUUID())) {
			if (toko.situs() == situs) {
				CompoundTag t = TokoPemain.data(server, toko);
				if (t != null && t.getIntOr("pg", 0) == 0 && !t.contains("pgm")) {
					return KotaLembar.Kartu.TUGAS_PEGAWAI + ":" + LembarToko.idToko(toko);
				}
			}
		}
		return null;
	}

	private static void pekerjakan(ServerPlayer player, int npcId, long situsSesi, String[] op) {
		Warga w = warga(player, npcId);
		if (w == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		long situs = situsDari(w);
		String jenis = LembarKunci.arg(op, 1);
		if (KotaLembar.Kartu.TUGAS_PEMBANTU.equals(jenis)) {
			RumahKota.Hasil h = RumahKota.pekerjakan(player, situs, LembarKunci.argInt(op, 2, -1), w.id);
			KotaLayar.pesan(player, h.pesan(), h.ok() ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA);
			return;
		}
		if (KotaLembar.Kartu.TUGAS_PEGAWAI.equals(jenis)) {
			TokoPemain.Toko toko = LembarToko.tokoDariId(server, player.getUUID(), LembarKunci.arg(op, 2));
			if (toko != null) {
				TokoPemain.pekerjakan(player, toko, w.id);
				KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "kartu.dipekerjakan", WargaApi.nama(w), toko.nama()), LembarKunci.PESAN_OK);
			}
			return;
		}
		KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "kartu.tanpa_tempat_kerja"), LembarKunci.PESAN_PERINGATAN);
	}

	private static void ajakSekte(ServerPlayer player, int npcId, long situsSesi, String[] op) {
		Warga w = warga(player, npcId);
		if (w == null) {
			return;
		}
		if (Halls.hallOf(player.getUUID()) == null) {
			KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "kartu.tanpa_aula"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		long situs = situsDari(w);
		Halls.CandidateSpec spec = WargaApi.jadikanCalon(w, player.getUUID(), "kota:" + situs);
		if (spec == null) {
			KotaLayar.pesan(player, Component.translatable(dev.kultivator.dunia.kota42.warga.Berbakat.perluRestu(w) ? KotaLayar.K + "kartu.perlu_restu"
					: KotaLayar.K + "kartu.ajak_ditolak", WargaApi.nama(w)), LembarKunci.PESAN_BAHAYA);
			return;
		}
		Halls.addCandidates(player.getUUID(), List.of(spec));
		WargaApi.ubahKesan(w, player, Kenangan.ANAK_DIREKRUT.delta, "ajak_sekte", Kenangan.ANAK_DIREKRUT);
		KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "kartu.ajak_ok", WargaApi.nama(w)), LembarKunci.PESAN_OK);
	}

	// ================================================================== tabs Keluarga and Teman

	private static ListTag keluarga(WargaData data, ServerPlayer player, Warga w) {
		Map<Integer, String> ikatan = new LinkedHashMap<>();
		int[] induk = induk(data, w.id);
		tambah(ikatan, induk[0], KotaLembar.Kartu.KEL_AYAH);
		tambah(ikatan, induk[1], KotaLembar.Kartu.KEL_IBU);
		tambah(ikatan, w.pasangan, KotaLembar.Kartu.KEL_PASANGAN);
		for (int c : w.anak) {
			tambah(ikatan, c, KotaLembar.Kartu.KEL_ANAK);
		}
		for (int p : induk) {
			for (int c : anakDari(data, p)) {
				if (c != w.id) {
					tambah(ikatan, c, KotaLembar.Kartu.KEL_SAUDARA);
				}
			}
		}
		for (int p : induk) {
			int[] gp = induk(data, p);
			tambah(ikatan, gp[0], KotaLembar.Kartu.KEL_KAKEK);
			tambah(ikatan, gp[1], KotaLembar.Kartu.KEL_NENEK);
		}
		if (w.pasangan != Warga.TANPA) {
			int[] mertua = induk(data, w.pasangan);
			tambah(ikatan, mertua[0], KotaLembar.Kartu.KEL_MERTUA);
			tambah(ikatan, mertua[1], KotaLembar.Kartu.KEL_MERTUA);
		}
		for (int c : w.anak) {
			Warga anak = data.byId(c);
			if (anak != null && anak.pasangan != Warga.TANPA) {
				tambah(ikatan, anak.pasangan, KotaLembar.Kartu.KEL_MENANTU);
			}
			for (int g : anakDari(data, c)) {
				tambah(ikatan, g, KotaLembar.Kartu.KEL_CUCU);
			}
		}
		ListTag list = new ListTag();
		for (Map.Entry<Integer, String> e : ikatan.entrySet()) {
			if (list.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag row = orang(data, player, e.getKey());
			if (row != null) {
				row.putString(KotaLembar.Kartu.IKATAN, e.getValue());
				list.add(row);
			}
		}
		return list;
	}

	private static void tambah(Map<Integer, String> map, int id, String ikatan) {
		if (id != Warga.TANPA) {
			map.putIfAbsent(id, ikatan);
		}
	}

	/** A family row: a living resident's row, else the archive entry (arsip, wafat). */
	static @Nullable CompoundTag orang(WargaData data, @Nullable ServerPlayer player, int id) {
		Warga w = data.byId(id);
		if (w != null) {
			CompoundTag row = LembarKotaDasar.warga(w, player, false);
			row.putString(KotaLembar.STATUS, switch (w.status) {
				case HIDUP -> KotaLembar.ST_HIDUP;
				case JALAN -> KotaLembar.ST_JALAN;
				case SEKTE -> KotaLembar.ST_SEKTE;
				case MATI -> KotaLembar.ST_ARSIP;
			});
			long situs = data.situsDari(w.id);
			if (situs != 0L) {
				LembarKunci.teks(row, KotaLembar.LOKASI, Component.translatable(KotaLayar.K + "lokasi.di", KotaNames.town(situs)));
			}
			row.putInt(LembarKunci.POTRET, tubuh(w, situs));
			return row;
		}
		ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, id);
		if (e == null) {
			return null;
		}
		return arsip(e);
	}

	/** The row of an archived person (the dead, the moved away, the disciples). */
	static CompoundTag arsip(ArsipWarga.Entri e) {
		Warga ghost = new Warga(e.id());
		ghost.marga = e.marga();
		ghost.nama = e.nama();
		ghost.generasi = e.generasi();
		ghost.kelamin = e.wanita() ? Warga.WANITA : Warga.PRIA;
		CompoundTag row = new CompoundTag();
		row.putInt(KotaLembar.WARGA, e.id());
		LembarKunci.teks(row, LembarKunci.NAMA, NamaWarga.komponen(ghost));
		// birth days are stored in tenths of a world day (SiklusHidup.umurPada), the archive end in whole days
		int umur = (int) Math.max(0L, (e.akhir() * 10L - e.lahir()) / (10L * dev.kultivator.dunia.kota42.KotaConfig.warga().hariPerTahun));
		row.putInt(KotaLembar.UMUR, umur);
		row.putString(KotaLembar.TAHAP, LembarKotaDasar.tahap(umur));
		row.putString(LembarKunci.PERAN, peranNama(e.peran()));
		row.putInt(KotaLembar.KELAMIN, e.wanita() ? KotaLembar.WANITA : KotaLembar.PRIA);
		row.putBoolean(KotaLembar.Kartu.ARSIP, true);
		row.putInt(LembarKunci.POTRET, -1);
		row.putInt(KotaLembar.RUPA, e.id() * 31 + e.marga());
		if (e.wafat()) {
			row.putLong(KotaLembar.Kartu.WAFAT, e.akhir());
			row.putString(KotaLembar.STATUS, KotaLembar.ST_ARSIP);
		} else {
			row.putString(KotaLembar.STATUS, e.sebab().startsWith(ArsipWarga.SEBAB_SEKTE) || e.sebab().startsWith(ArsipWarga.SEBAB_MURID)
					? KotaLembar.ST_SEKTE : KotaLembar.ST_PINDAH);
		}
		return row;
	}

	private static String peranNama(String id) {
		for (NpcRole r : NpcRole.values()) {
			if (r.id().equals(id)) {
				return r.name();
			}
		}
		return NpcRole.WARGA.name();
	}

	static int[] induk(WargaData data, int id) {
		if (id == Warga.TANPA) {
			return new int[] {Warga.TANPA, Warga.TANPA};
		}
		Warga w = data.byId(id);
		if (w != null) {
			return new int[] {w.ayah, w.ibu};
		}
		ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, id);
		return e == null ? new int[] {Warga.TANPA, Warga.TANPA} : new int[] {e.ayah(), e.ibu()};
	}

	static int[] anakDari(WargaData data, int id) {
		if (id == Warga.TANPA) {
			return new int[0];
		}
		Warga w = data.byId(id);
		if (w != null) {
			return w.anak;
		}
		ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, id);
		return e == null ? new int[0] : e.anak();
	}

	static int pasanganDari(WargaData data, int id) {
		if (id == Warga.TANPA) {
			return Warga.TANPA;
		}
		Warga w = data.byId(id);
		if (w != null) {
			return w.pasangan;
		}
		ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, id);
		return e == null ? Warga.TANPA : e.pasangan();
	}

	private static ListTag teman(WargaData data, Warga w, long hari) {
		List<long[]> bonds = new ArrayList<>();
		for (long b : w.hubungan) {
			JenisHubungan j = JenisHubungan.jenis(b);
			if (j != null && j != JenisHubungan.KENALAN) {
				bonds.add(new long[] {b, Math.abs(JenisHubungan.kekuatan(b))});
			}
		}
		bonds.sort((a, c) -> Long.compare(c[1], a[1]));
		ListTag list = new ListTag();
		for (long[] bond : bonds) {
			if (list.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			long b = bond[0];
			JenisHubungan j = JenisHubungan.jenis(b);
			int lain = JenisHubungan.idLain(b);
			CompoundTag row = new CompoundTag();
			Warga o = data.byId(lain);
			if (o != null) {
				row.putInt(KotaLembar.WARGA, o.id);
				LembarKunci.teks(row, LembarKunci.NAMA, WargaApi.nama(o));
				row.putInt(KotaLembar.RUPA, o.look);
				long situs = data.situsDari(o.id);
				row.putInt(LembarKunci.POTRET, tubuh(o, situs));
				row.putString(KotaLembar.STATUS, o.status == Warga.Status.HIDUP ? KotaLembar.ST_HIDUP : o.status.name().toLowerCase(Locale.ROOT));
				if (o.status == Warga.Status.JALAN && o.tujuanSitus != 0L) {
					LembarKunci.teks(row, KotaLembar.LOKASI, Component.translatable(KotaLayar.K + "lokasi.jalan", KotaNames.town(o.tujuanSitus)));
				} else if (situs != 0L) {
					LembarKunci.teks(row, KotaLembar.LOKASI, Component.translatable(KotaLayar.K + "lokasi.di", KotaNames.town(situs)));
				}
			} else {
				ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, lain);
				if (e == null) {
					continue;
				}
				row = arsip(e);
				LembarKunci.teks(row, KotaLembar.LOKASI, Component.translatable(e.wafat() ? KotaLayar.K + "lokasi.wafat" : KotaLayar.K + "lokasi.pergi"));
			}
			row.putString(KotaLembar.Kartu.JENIS, j.id());
			LembarKunci.teks(row, KotaLembar.Kartu.JENIS + "_nama", j.nama());
			row.putInt(KotaLembar.Kartu.KEKUATAN, Mth.clamp(JenisHubungan.kekuatan(b), -100, 100));
			row.putLong(KotaLembar.Kartu.SEJAK, JenisHubungan.sejak(b));
			list.add(row);
		}
		return list;
	}

	private LembarWarga() {
	}
}
