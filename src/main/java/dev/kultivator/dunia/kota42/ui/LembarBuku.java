package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Cakap;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.ekonomi.GudangKota;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.ekonomi.Investasi;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.kota42.ekonomi.KontrakDagang;
import dev.kultivator.dunia.kota42.ekonomi.LapakKota;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ekonomi.PasarHantu;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.kota42.ekonomi.TokoPemain;
import dev.kultivator.dunia.kota42.ekonomi.UsahaWarga;
import dev.kultivator.dunia.kota42.tumbuh.Kavling;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Migrasi;
import dev.kultivator.dunia.kota42.warga.NamaWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.cerita.Isian;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Builders of {@code kota_buku} (DESIGN 42.11.4, keys {@link KotaLembar.Buku}) and {@code kota_milikku}
 * ({@link KotaLembar.Milikku}).
 * <p>
 * <b>Buku Kota</b>: Ringkas (population against beds, births / deaths / arrivals / departures of the last 7 days, mayor,
 * the player's office, residents who know the player, the next three events, the night market hint), Warga (every
 * resident with paging, a filter and a search; a row opens the card), Kavling (growth plots and their stage), Kabar
 * (what the player overheard or was told about this town, with who said it) and three jump tabs ("No GUI twice", jadwal
 * 0.1): Acara opens the 38 event board ({@code acara}) with the 14-day calendar, Harga opens the one trade ledger
 * ({@code gudang} tab Harga), Milikku opens the asset list.
 * <p>
 * <b>Rumah &amp; Usahaku</b>: one row per house, shop, stall, stake, warehouse, contract and office the player holds in
 * any town, each a jump to its screen; this week's money from the 42 faucets against their caps (42.6.0).
 * <p>
 * Calendar sources: weddings of engaged residents (read here) plus every {@link SumberAcara} another module registers
 * (42D ceremonies, troupes, festivals, ronda; 42E open trials; 42F inaugurations).
 */
public final class LembarBuku {

	private static final String L = KotaLembar.Buku.LAYAR;
	private static final String M = KotaLembar.Milikku.LAYAR;
	/** Rows per page of tab Warga. */
	private static final int HALAMAN = 40;
	/** Days of the calendar. */
	public static final int KALENDER_HARI = 14;

	/** One calendar row (KotaLembar.Buku acara keys). */
	public record Acara(String id, String jenis, Component nama, long hari, int jam, int menit, @Nullable Component tempat, int x, int z,
			boolean undangan) {
	}

	/** A module lists the events of place {@code situs} from day {@code dari} to {@code sampai} (inclusive). */
	@FunctionalInterface
	public interface SumberAcara {
		List<Acara> acara(MinecraftServer server, ServerPlayer player, long situs, long dari, long sampai);
	}

	private static final List<SumberAcara> SUMBER = new CopyOnWriteArrayList<>();
	/** Player -> {page, filter, search} of tab Warga. */
	private static final Map<UUID, String[]> HALAMAN_PILIH = new HashMap<>();

	/** Adds a calendar source (42D, 42E, 42F). */
	public static void addSumberAcara(SumberAcara sumber) {
		if (sumber != null) {
			SUMBER.add(sumber);
		}
	}

	static void init() {
		KotaLayar.daftar(L, LembarBuku::bangunBuku);
		KotaLayar.daftar(M, LembarBuku::bangunMilikku);
		KotaLayar.bawaan(L, LembarKunci.OP_HALAMAN, (player, npcId, situs, op) -> HALAMAN_PILIH.put(player.getUUID(),
				new String[] {LembarKunci.arg(op, 1), LembarKunci.arg(op, 2), LembarKunci.arg(op, 3)}));
		KotaLayar.daftarTab(L, KotaLembar.Buku.TAB_ACARA, (player, npcId, situs, op) -> {
			kalender(player, npcId, situs);
			return true;
		});
		KotaLayar.daftarTab(L, KotaLembar.Buku.TAB_HARGA, (player, npcId, situs, op) -> {
			LembarKotaDasar.pilihSitus(player, situs);
			return KotaLayar.bukaJauh(player, KotaLembar.Gudang.LAYAR, KotaLembar.Gudang.TAB_HARGA);
		});
		KotaLayar.daftarTab(L, KotaLembar.Buku.TAB_MILIKKU, (player, npcId, situs, op) -> KotaLayar.bukaJauh(player, M, null));
		KotaLayar.daftarTab(L, KotaLembar.Buku.TAB_WARGA, (player, npcId, situs, op) -> {
			int id = LembarKunci.argInt(op, 2, 0);
			if (id <= 0) {
				return false;
			}
			return LembarWarga.buka(player, npcId, id);
		});
		KotaLayar.daftarTab(L, KotaLembar.Buku.TAB_KAVLING, (player, npcId, situs, op) -> {
			String id = LembarKunci.arg(op, 2);
			if (id.isEmpty()) {
				return false;
			}
			LembarKotaDasar.setPilih(player, KotaLembar.Kavling.LAYAR, id);
			LembarKotaDasar.pilihSitus(player, situs);
			return KotaLayar.bukaJauh(player, KotaLembar.Kavling.LAYAR, null);
		});
		KotaLayar.daftarTab(M, "daftar", (player, npcId, situs, op) -> lompatAset(player, LembarKunci.arg(op, 2)));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				HALAMAN_PILIH.remove(handler.getPlayer().getUUID()));
	}

	// ================================================================== Buku Kota

	private static @Nullable CompoundTag bangunBuku(ServerPlayer player, int npcId, long situs) {
		if (npcId >= 0 && !KotaLayar.melayani(player, npcId, L)) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, null, LembarKunci.GAYA_KERTAS);
		if (situs == 0L) {
			return LembarKotaDasar.kunci(tag, Component.translatable(KotaLayar.K + "buku.tanpa_kota"));
		}
		LembarKotaDasar.kepala(tag, player, situs);
		LembarKotaDasar.pilihSitus(player, situs);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(KotaLayar.K + "buku.judul", KotaNames.town(situs)));
		tag.putInt(KotaLembar.TREN, trenMakmur(server, situs));
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(situs);
		long hari = KotaHidup.hari(server);
		List<Acara> acara = acara(server, player, situs, hari, hari + KALENDER_HARI - 1);
		tag.put(KotaLembar.Buku.RINGKAS, ringkas(server, data, s, player, situs, hari, acara));
		tag.put(LembarKunci.DAFTAR, daftarWarga(tag, data, s, player, hari));
		ListTag rowsAcara = new ListTag();
		for (Acara a : acara) {
			if (rowsAcara.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			rowsAcara.add(acaraTag(a, hari, server));
		}
		tag.put(KotaLembar.Buku.ACARA, rowsAcara);
		tag.put(KotaLembar.Buku.HARGA, harga(server, situs));
		tag.put(KotaLembar.Buku.KAVLING, kavling(situs));
		tag.put(KotaLembar.Buku.KABAR, kabar(server, player, data, situs));
		return tag;
	}

	private static int trenMakmur(MinecraftServer server, long situs) {
		int[] riwayat = riwayatSuasana(server, situs);
		if (riwayat.length < 2) {
			return 0;
		}
		int d = riwayat[riwayat.length - 1] - riwayat[0];
		return d > 3 ? 1 : d < -3 ? -1 : 0;
	}

	private static int[] riwayatSuasana(MinecraftServer server, long situs) {
		WargaData.Situs s = WargaData.get(server).peek(situs);
		return s == null ? new int[0] : s.stat.getIntArray("riwayat").orElse(new int[0]);
	}

	private static CompoundTag ringkas(MinecraftServer server, WargaData data, @Nullable WargaData.Situs s, ServerPlayer player, long situs, long hari,
			List<Acara> acara) {
		CompoundTag r = new CompoundTag();
		int penduduk = 0;
		int lahir = 0;
		int masuk = 0;
		int kenal = 0;
		int idx = data.cariPemain(player.getUUID());
		if (s != null) {
			for (Warga w : s.warga) {
				if (w.status != Warga.Status.HIDUP) {
					continue;
				}
				penduduk++;
				if (LembarWarga.hariLahir(w.lahir) >= hari - 7L) {
					lahir++;
				}
				if (w.datang >= hari - 7L && w.datang >= 0L && Migrasi.asal(w) != 0L) {
					masuk++;
				}
				if (idx >= 0 && w.ingatanDari(idx) != null) {
					kenal++;
				}
			}
		}
		int wafat = 0;
		int keluar = 0;
		if (s != null) {
			for (ArsipWarga.Entri e : ArsipWarga.semua(s)) {
				if (e.akhir() < hari - 7L) {
					continue;
				}
				if (e.wafat()) {
					wafat++;
				} else {
					keluar++;
				}
			}
		}
		r.putInt(KotaLembar.Buku.PENDUDUK, penduduk);
		r.putInt(KotaLembar.Buku.KASUR, s == null ? penduduk : penduduk + Math.max(0, Migrasi.kasurBebas(server, s)));
		r.putInt(KotaLembar.Buku.LAHIR, lahir);
		r.putInt(KotaLembar.Buku.WAFAT, wafat);
		r.putInt(KotaLembar.Buku.MASUK, masuk);
		r.putInt(KotaLembar.Buku.KELUAR, keluar);
		String wali = Jabatan.waliKota(server, situs);
		if (wali != null && !wali.isEmpty()) {
			LembarKunci.teks(r, KotaLembar.Buku.WALI, Component.literal(wali));
		}
		int pangkat = Jabatan.pangkat(player.getUUID(), situs);
		if (pangkat > Jabatan.TANPA) {
			LembarKunci.teks(r, KotaLembar.Buku.JABATAN, Jabatan.gelar(server, situs, pangkat));
		}
		r.putInt(KotaLembar.Buku.KENAL_WARGA, kenal);
		List<Kavling.Info> plot = Kavling.daftar(situs);
		boolean penuh = !plot.isEmpty();
		for (Kavling.Info k : plot) {
			if (k.layak() && !k.selesai()) {
				penuh = false;
				break;
			}
		}
		r.putBoolean(KotaLembar.Buku.PENUH, penuh);
		ListTag next = new ListTag();
		for (Acara a : acara) {
			if (next.size() >= 3) {
				break;
			}
			next.add(acaraTag(a, hari, server));
		}
		r.put(KotaLembar.Buku.ACARA, next);
		if (PasarHantu.aktif() && PasarHantu.adaPasar(server, situs)) {
			LembarKunci.teks(r, KotaLembar.Buku.MALAM, Component.translatable(PasarHantu.malam(server) ? KotaLayar.K + "buku.malam_buka"
					: KotaLayar.K + "buku.malam_nanti"));
		}
		return r;
	}

	// ------------------------------------------------------------------ tab Warga

	private static ListTag daftarWarga(CompoundTag tag, WargaData data, @Nullable WargaData.Situs s, ServerPlayer player, long hari) {
		String[] pilih = HALAMAN_PILIH.getOrDefault(player.getUUID(), new String[] {"0", KotaLembar.SR_SEMUA, ""});
		int halaman = 0;
		try {
			halaman = Math.max(0, Integer.parseInt(pilih[0]));
		} catch (NumberFormatException ignored) {
			// first page
		}
		String saring = KotaLembar.SEMUA_SARING.contains(pilih[1]) ? pilih[1] : KotaLembar.SR_SEMUA;
		String cari = pilih[2] == null ? "" : pilih[2].trim().toLowerCase(Locale.ROOT);
		int idx = data.cariPemain(player.getUUID());
		List<CompoundTag> rows = new ArrayList<>();
		if (s != null) {
			List<Warga> semua = new ArrayList<>(s.warga);
			semua.sort(Comparator.comparing(NamaWarga::lengkap));
			for (Warga w : semua) {
				if (!cocok(w, saring, idx) || !cari.isEmpty() && !NamaWarga.lengkap(w).toLowerCase(Locale.ROOT).contains(cari)) {
					continue;
				}
				CompoundTag row = LembarKotaDasar.warga(w, player, false);
				row.putBoolean(KotaLembar.KENAL, idx >= 0 && w.ingatanDari(idx) != null);
				row.putInt(KotaLembar.AKAR, w.akar);
				row.putBoolean(KotaLembar.AKAR_PASTI, w.akar > 0 && dev.kultivator.dunia.kota42.warga.Berbakat.tampak(w));
				rows.add(row);
			}
			if (KotaLembar.SR_ARSIP.equals(saring)) {
				for (ArsipWarga.Entri e : ArsipWarga.semua(s)) {
					CompoundTag row = LembarWarga.arsip(e);
					if (!cari.isEmpty() && !LembarKunci.teks(row, LembarKunci.NAMA).getString().toLowerCase(Locale.ROOT).contains(cari)) {
						continue;
					}
					rows.add(row);
				}
			}
		}
		int maks = Math.max(0, (rows.size() - 1) / HALAMAN);
		halaman = Math.min(halaman, maks);
		ListTag list = new ListTag();
		for (int i = halaman * HALAMAN; i < rows.size() && list.size() < HALAMAN; i++) {
			list.add(rows.get(i));
		}
		tag.putInt(KotaLembar.Buku.HALAMAN, halaman);
		tag.putInt(KotaLembar.Buku.HALAMAN_MAKS, maks);
		tag.putInt(LembarKunci.JUMLAH, rows.size());
		tag.putString(KotaLembar.Buku.SARING, saring);
		LembarKunci.teks(tag, KotaLembar.Buku.CARI, Component.literal(pilih[2] == null ? "" : pilih[2]));
		return list;
	}

	private static boolean cocok(Warga w, String saring, int idx) {
		if (KotaLembar.SR_ARSIP.equals(saring)) {
			return false;
		}
		if (w.status != Warga.Status.HIDUP && w.status != Warga.Status.JALAN) {
			return false;
		}
		int umur = WargaApi.umur(w);
		return switch (saring) {
			case KotaLembar.SR_ANAK -> umur < 16;
			case KotaLembar.SR_DEWASA -> umur >= 16 && umur < 55;
			case KotaLembar.SR_TUA -> umur >= 55;
			case KotaLembar.SR_BERAKAR -> w.akar > 0 && dev.kultivator.dunia.kota42.warga.Berbakat.tampak(w);
			case KotaLembar.SR_KENAL -> idx >= 0 && w.ingatanDari(idx) != null;
			case KotaLembar.SR_KERJA -> dev.kultivator.dunia.kota42.warga.Pekerjaan.bekerja(w);
			case KotaLembar.SR_MENGANGGUR -> umur >= 16 && !dev.kultivator.dunia.kota42.warga.Pekerjaan.bekerja(w);
			default -> true;
		};
	}

	// ------------------------------------------------------------------ calendar

	/** Every event of {@code situs} between the two days, sorted by day and hour. */
	public static List<Acara> acara(MinecraftServer server, ServerPlayer player, long situs, long dari, long sampai) {
		List<Acara> out = new ArrayList<>();
		WargaData.Situs s = WargaData.get(server).peek(situs);
		if (s != null) {
			for (Warga w : s.warga) {
				// one row per couple: the groom's side lists it
				if (w.tunangan == Warga.TANPA || !w.pria() || w.hariNikah < dari || w.hariNikah > sampai) {
					continue;
				}
				Warga dia = WargaApi.byId(w.tunangan);
				Component nama = Component.translatable(KotaLayar.K + "acara.nikah", WargaApi.nama(w), dia == null ? Component.literal("?") : WargaApi.nama(dia));
				Ingatan a = WargaApi.ingatan(w, player.getUUID());
				boolean undang = a != null && a.kesan >= 20;
				out.add(new Acara("nikah:" + w.id, KotaLembar.Buku.AC_NIKAH, nama, w.hariNikah, 9, 0, null, 0, 0, undang));
			}
		}
		for (SumberAcara sumber : SUMBER) {
			try {
				out.addAll(sumber.acara(server, player, situs, dari, sampai));
			} catch (RuntimeException ex) {
				dev.kultivator.Kultivator.LOGGER.warn("A Buku Kota calendar source failed", ex);
			}
		}
		out.sort(Comparator.comparingLong(Acara::hari).thenComparingInt(Acara::jam).thenComparingInt(Acara::menit));
		return out;
	}

	private static CompoundTag acaraTag(Acara a, long hari, MinecraftServer server) {
		CompoundTag t = new CompoundTag();
		t.putString(LembarKunci.ID, a.id());
		t.putString(LembarKunci.JENIS, a.jenis());
		LembarKunci.teks(t, LembarKunci.NAMA, a.nama());
		LembarKunci.teks(t, LembarKunci.TEKS, a.nama());
		t.putLong(LembarKunci.HARI, a.hari());
		t.putInt(KotaLembar.JAM, a.jam());
		t.putInt(KotaLembar.MENIT, a.menit());
		if (a.tempat() != null) {
			LembarKunci.teks(t, LembarKunci.TEMPAT, a.tempat());
		}
		if (a.x() != 0 || a.z() != 0) {
			t.putInt(LembarKunci.X, a.x());
			t.putInt(LembarKunci.Z, a.z());
		}
		t.putBoolean(KotaLembar.Buku.UNDANGAN, a.undangan());
		if (a.hari() == hari) {
			long now = Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
			// world time 0 = 06:00
			long mulai = Math.floorMod((a.jam() - 6) * 1000L + a.menit() * 1000L / 60L, 24000L);
			if (mulai > now) {
				t.putLong(LembarKunci.SISA_TICK, mulai - now);
			}
		}
		return t;
	}

	/** Tab Acara: the 38 event board shows the 14-day calendar ("No GUI twice": one events screen). */
	private static void kalender(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		long hari = KotaHidup.hari(server);
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(KotaLayar.K + "kalender.judul", KotaNames.town(situs)),
				LembarKunci.GAYA_KAIN);
		LembarKunci.teks(tag, LembarKunci.TEMPAT, KotaNames.town(situs));
		List<Acara> acara = acara(server, player, situs, hari, hari + KALENDER_HARI - 1);
		ListTag jadwal = new ListTag();
		for (Acara a : acara) {
			if (jadwal.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag j = new CompoundTag();
			j.putString(LembarKunci.ID, a.id());
			LembarKunci.teks(j, LembarKunci.NAMA, a.undangan() ? Component.translatable(KotaLayar.K + "kalender.diundang", a.nama()) : a.nama());
			j.putInt(LembarKunci.HARI, (int) a.hari());
			Component waktu = Component.translatable(KotaLayar.K + "kalender.jam", String.format(Locale.ROOT, "%02d:%02d", a.jam(), a.menit()));
			LembarKunci.teks(j, LembarKunci.CATATAN, a.tempat() == null ? waktu : Component.translatable(KotaLayar.K + "kalender.jam_tempat", waktu, a.tempat()));
			jadwal.add(j);
		}
		tag.put(LembarKunci.Acara.JADWAL, jadwal);
		Component catatan;
		if (acara.isEmpty()) {
			catatan = Component.translatable(KotaLayar.K + "kalender.kosong", KALENDER_HARI);
		} else {
			Acara a = acara.get(0);
			long dalam = a.hari() - hari;
			catatan = Component.translatable(KotaLayar.K + "kalender.ringkas", acara.size(), KALENDER_HARI, a.nama(),
					dalam <= 0 ? Component.translatable(KotaLayar.K + "kalender.hari_ini") : Component.translatable(KotaLayar.K + "kalender.lagi", dalam));
		}
		LembarKunci.teks(tag, LembarKunci.CATATAN, catatan);
		TempatLayar.buka(player, LembarKunci.Acara.LAYAR, npcId, tag);
	}

	// ------------------------------------------------------------------ prices, plots, gossip

	/** The 12 categories of {@code situs} (also tab Harga of the trade ledger). */
	static ListTag harga(MinecraftServer server, long situs) {
		ListTag list = new ListTag();
		if (!HargaKota.aktif()) {
			return list;
		}
		for (Kategori k : Kategori.values()) {
			HargaKota.Rincian r = HargaKota.rincian(server, situs, k);
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, k.name().toLowerCase(Locale.ROOT));
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(KotaLayar.K + "kategori." + k.name().toLowerCase(Locale.ROOT)));
			t.putFloat(KotaLembar.Buku.FAKTOR, (float) r.faktor());
			t.putInt(KotaLembar.TREN, r.tren());
			t.putIntArray(KotaLembar.GRAFIK, grafik(r.faktor(), r.tren()));
			t.putInt(LembarKunci.STOK, Mth.clamp(r.stok(), 0, 200));
			ListTag sebab = new ListTag();
			for (HargaKota.Pengali p : r.sebab()) {
				if (sebab.size() >= 6) {
					break;
				}
				CompoundTag sb = new CompoundTag();
				LembarKunci.teks(sb, LembarKunci.TEKS, p.sebab());
				sb.putInt(LembarKunci.NILAI, (int) Math.round((p.kali() - 1.0D) * 100.0D));
				sebab.add(sb);
			}
			t.put(KotaLembar.SEBAB, sebab);
			list.add(t);
		}
		return list;
	}

	/**
	 * A 7-day line ending at today's factor (x100); HargaKota keeps the trend direction, not the daily history, so the
	 * earlier days are reconstructed as a smooth walk toward today.
	 * shortcut: synthetic history from the trend sign; upgrade when HargaKota stores 7 daily factors.
	 */
	private static int[] grafik(double faktor, int tren) {
		int[] g = new int[7];
		double step = tren == 0 ? 0.0D : tren * 0.02D;
		for (int i = 0; i < 7; i++) {
			g[i] = (int) Math.round((faktor - step * (6 - i)) * 100.0D);
		}
		return g;
	}

	private static ListTag kavling(long situs) {
		ListTag list = new ListTag();
		for (Kavling.Info k : Kavling.daftar(situs)) {
			if (list.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag t = new CompoundTag();
			t.putInt(LembarKunci.ID, k.id());
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(KotaLayar.K + "kavling.nama", k.id()));
			LembarKunci.teks(t, KotaLembar.Buku.RENCANA, Component.translatableWithFallback(KotaLayar.K + "rencana." + k.jenis(), k.jenis()));
			t.putInt(LembarKunci.TAHAP, Math.max(0, k.tahap()));
			t.putInt(LembarKunci.TAHAP_TOTAL, Kavling.SELESAI);
			t.putString(LembarKunci.KEADAAN, !k.layak() ? "tidak_layak" : k.selesai() ? "selesai" : k.tahap() > 0 ? "bangun" : "kosong");
			t.putInt(LembarKunci.X, k.pos().getX());
			t.putInt(LembarKunci.Z, k.pos().getZ());
			list.add(t);
		}
		return list;
	}

	private static ListTag kabar(MinecraftServer server, ServerPlayer player, WargaData data, long situs) {
		List<Fakta> semua = Cakap.catatan(player);
		ListTag list = new ListTag();
		for (int i = semua.size() - 1; i >= 0 && list.size() < LembarKunci.DAFTAR_MAKS; i--) {
			Fakta f = semua.get(i);
			if (!tentang(f, data, situs)) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, f.predikat().toLowerCase(Locale.ROOT) + ":" + f.hariTahu() + ":" + i);
			LembarKunci.teks(t, LembarKunci.TEKS, kalimat(server, f));
			if (f.asal().sumber() == Fakta.Sumber.DENGAR && f.asal().dari() > 0) {
				Warga dari = data.byId(f.asal().dari());
				LembarKunci.teks(t, KotaLembar.DARI, dari != null ? WargaApi.nama(dari) : Component.translatable(KotaLayar.K + "kabar.seseorang"));
				t.putInt(KotaLembar.Buku.DARI_WARGA, f.asal().dari());
			} else {
				LembarKunci.teks(t, KotaLembar.DARI, Component.translatable(KotaLayar.K + "kabar.sumber." + f.asal().sumber().name().toLowerCase(Locale.ROOT)));
			}
			t.putLong(LembarKunci.HARI, f.hariTahu());
			t.putInt(KotaLembar.Buku.PANAS, Mth.clamp(Math.round(f.kekuatan() * 100.0F / (Fakta.KEKUATAN_DENGAR * 2.0F)), 0, 100));
			t.putString(LembarKunci.JENIS, f.kebenaran().name().toLowerCase(Locale.ROOT));
			list.add(t);
		}
		return list;
	}

	/** True when a fact is about this place or one of its residents. */
	private static boolean tentang(Fakta f, WargaData data, long situs) {
		for (Fakta.Rujuk r : new Fakta.Rujuk[] {f.subjek(), f.objek()}) {
			if (r.jenis() == Fakta.Jenis.TEMPAT && r.id() == situs) {
				return true;
			}
			if (r.jenis() == Fakta.Jenis.WARGA && data.situsDari((int) r.id()) == situs) {
				return true;
			}
		}
		return f.asal().sumber() == Fakta.Sumber.DENGAR && data.situsDari(f.asal().dari()) == situs;
	}

	/** One gossip line from the fact's predicate template ({@code kultivator.kota42.ui.kabar.p.<predikat>}). */
	static Component kalimat(MinecraftServer server, Fakta f) {
		String nilai = Integer.toString(f.nilai());
		return Component.translatableWithFallback(KotaLayar.K + "kabar.p." + f.predikat().toLowerCase(Locale.ROOT), f.predikat(),
				rujuk(server, f.subjek()), rujuk(server, f.objek()), nilai);
	}

	static Component rujuk(MinecraftServer server, Fakta.Rujuk r) {
		return switch (r.jenis()) {
			case WARGA -> {
				Warga w = WargaApi.byId((int) r.id());
				if (w != null) {
					yield WargaApi.nama(w);
				}
				ArsipWarga.Entri e = ArsipWarga.cariDiMana(WargaData.get(server), (int) r.id());
				yield e == null ? Component.translatable(KotaLayar.K + "kabar.seseorang") : LembarKunci.teks(LembarWarga.arsip(e), LembarKunci.NAMA);
			}
			case PEMAIN -> {
				UUID id = WargaData.get(server).pemain((int) r.id());
				ServerPlayer p = id == null ? null : server.getPlayerList().getPlayer(id);
				yield p != null ? p.getName() : Component.translatable(KotaLayar.K + "kabar.pendekar");
			}
			case TEMPAT -> KotaNames.town(r.id());
			case SEKTE -> dev.kultivator.sekte.gabung.ui.GabungLayar.namaSekte(r.kunci());
			case BARANG -> {
				Identifier id = Identifier.tryParse(r.kunci());
				yield id == null ? Component.literal(r.kunci()) : Component.translatable(BuiltInRegistries.ITEM.getValue(id).getDescriptionId());
			}
			case ACARA -> Component.translatableWithFallback(KotaLayar.K + "acara.jenis." + r.kunci(), r.kunci());
			case KATA -> Component.translatableWithFallback("kultivator.kota42.cakap.hal." + r.kunci(), r.kunci());
			case TIADA -> Component.empty();
		};
	}

	// ================================================================== Rumah & Usahaku

	private static @Nullable CompoundTag bangunMilikku(ServerPlayer player, int npcId, long situsSesi) {
		MinecraftServer server = player.level().getServer();
		UUID me = player.getUUID();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(KotaLayar.K + "milikku.judul"), LembarKunci.GAYA_KAYU);
		List<CompoundTag> rows = new ArrayList<>();
		long hari = KotaHidup.hari(server);
		int nRumah = 0;
		for (RumahKota.Rumah r : RumahKota.milik(me)) {
			RumahKota.Info info = RumahKota.info(server, r.situs(), r.id());
			if (info == null) {
				continue;
			}
			nRumah++;
			Component ringkas = switch (info.status()) {
				case RumahKota.ST_SEWA -> Component.translatable(KotaLayar.K + "milikku.sewa_sampai", info.sewaSampai());
				case RumahKota.ST_SEGEL -> Component.translatable(KotaLayar.K + "milikku.segel_sampai", info.segelSampai());
				default -> Component.translatable(KotaLayar.K + "milikku.milik", info.kasur());
			};
			Component warn = info.tunggak() > 0 ? Component.translatable(KotaLayar.K + "milikku.tunggak", info.tunggak())
					: RumahKota.ST_SEWA.equals(info.status()) && info.sewaSampai() - hari <= 1 ? Component.translatable(KotaLayar.K + "milikku.sewa_habis") : null;
			rows.add(aset(KotaLembar.Milikku.MJ_RUMAH, r.situs() + ":" + r.id(), KotaLembar.Rumah.LAYAR, RumahKota.nama(server, info), r.situs(), ringkas, warn,
					info.pintu().getX(), info.pintu().getZ()));
		}
		int nToko = 0;
		for (TokoPemain.Toko toko : TokoPemain.milik(server, me)) {
			CompoundTag t = TokoPemain.data(server, toko);
			nToko++;
			long laci = t == null ? 0L : t.getLongOr("laci", 0L);
			Component ringkas = Component.translatable(KotaLayar.K + "milikku.laci", laci);
			Component warn = t != null && !t.getBooleanOr("buka", true) ? Component.translatable(KotaLayar.K + "milikku.toko_tutup") : null;
			rows.add(aset(KotaLembar.Milikku.MJ_TOKO, LembarToko.idToko(toko), KotaLembar.Toko.LAYAR, Component.literal(toko.nama().isEmpty()
					? Component.translatable(KotaLayar.K + "toko.tanpa_nama").getString() : toko.nama()), toko.situs(), ringkas, warn, toko.pos().getX(), toko.pos().getZ()));
		}
		for (LapakKota.Lapak l : LapakKota.semuaSewaan(server, me)) {
			rows.add(aset(KotaLembar.Milikku.MJ_LAPAK, l.situs() + ":" + l.no(), KotaLembar.LapakSewa.LAYAR,
					Component.translatable(KotaLayar.K + "lapak.nama", l.no(), Component.translatable(KotaLayar.K + "distrik." + l.distrik())), l.situs(),
					Component.translatable(KotaLayar.K + "milikku.sewa_sampai", l.sampai()),
					l.sampai() - hari <= 1 ? Component.translatable(KotaLayar.K + "milikku.sewa_habis") : null, l.pos().getX(), l.pos().getZ()));
		}
		int nUsaha = 0;
		for (Investasi.Saham sh : Investasi.milik(server, me)) {
			UsahaWarga.Usaha u = UsahaWarga.usaha(sh.situs(), sh.usaha());
			nUsaha++;
			Component nama = u == null ? Component.translatable(KotaLayar.K + "investasi.usaha_hilang") : UsahaWarga.nama(u);
			Component ringkas = Component.translatable(KotaLayar.K + "milikku.saham", Math.round(sh.bagian() * 100.0D), TeksNb.bertanda(sh.labaMinggu()));
			Component warn = UsahaWarga.peringatan(server, sh.situs(), sh.usaha());
			rows.add(aset(KotaLembar.Milikku.MJ_USAHA, sh.situs() + ":" + sh.usaha(), KotaLembar.Investasi.LAYAR, nama, sh.situs(), ringkas, warn, 0, 0));
		}
		int nGudang = 0;
		for (GudangKota.Gudang g : GudangKota.semua(server, me)) {
			nGudang++;
			rows.add(aset(KotaLembar.Milikku.MJ_GUDANG, Long.toString(g.situs()), KotaLembar.Gudang.LAYAR,
					Component.translatable(KotaLayar.K + "gudang.nama", KotaNames.town(g.situs())), g.situs(),
					Component.translatable(KotaLayar.K + "milikku.gudang", g.terpakai(), g.ukuran(), g.sampai()),
					!GudangKota.berlaku(server, g) ? Component.translatable(KotaLayar.K + "milikku.gudang_habis") : null, 0, 0));
		}
		int nKontrak = 0;
		for (KontrakDagang.Kontrak k : KontrakDagang.kontrak(server, me)) {
			nKontrak++;
			rows.add(aset(KotaLembar.Milikku.MJ_KONTRAK, Integer.toString(k.id()), KotaLembar.Gudang.LAYAR, LembarGudang.namaKontrak(k), k.asal(),
					Component.translatable(KotaLayar.K + "milikku.kontrak", k.kali(), k.kaliMaks()), null, 0, 0));
		}
		int nJabatan = 0;
		for (Map.Entry<Long, Integer> e : Jabatan.semua(server, me).entrySet()) {
			if (e.getValue() <= Jabatan.TANPA) {
				continue;
			}
			nJabatan++;
			Component warn = Jabatan.peringatan(server, e.getKey(), me);
			rows.add(aset(KotaLembar.Milikku.MJ_JABATAN, "jabatan:" + e.getKey(), KotaLembar.Jabatan.LAYAR, Jabatan.gelar(server, e.getKey(), e.getValue()),
					e.getKey(), Component.translatable(KotaLayar.K + "milikku.gaji", Jabatan.gajiTerakhir(server, e.getKey(), me)), warn, 0, 0));
		}
		ListTag daftar = new ListTag();
		for (CompoundTag r : rows) {
			if (daftar.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			daftar.add(r);
		}
		tag.put(LembarKunci.DAFTAR, daftar);
		CompoundTag jumlah = new CompoundTag();
		jumlah.putInt(KotaLembar.Milikku.MJ_RUMAH, nRumah);
		jumlah.putInt(KotaLembar.Milikku.MAKS_RUMAH, KotaConfig.ekonomi().rumahMaks);
		jumlah.putInt(KotaLembar.Milikku.MJ_TOKO, nToko);
		jumlah.putInt(KotaLembar.Milikku.MJ_USAHA, nUsaha);
		jumlah.putInt(KotaLembar.Milikku.MAKS_USAHA, Investasi.MAKS_TOTAL);
		jumlah.putInt(KotaLembar.Milikku.MJ_GUDANG, nGudang);
		jumlah.putInt(KotaLembar.Milikku.MJ_KONTRAK, nKontrak);
		jumlah.putInt(KotaLembar.Milikku.MJ_JABATAN, nJabatan);
		tag.put(KotaLembar.Milikku.JUMLAH, jumlah);
		CompoundTag minggu = new CompoundTag();
		CompoundTag batas = new CompoundTag();
		int pita = Isian.pita(player);
		for (String k : new String[] {Investasi.K_TOKO, Investasi.K_INVESTASI, Investasi.K_KONTRAK, Investasi.K_GAJI, Investasi.K_UPETI, Investasi.K_BUNGA}) {
			minggu.putLong(k, Investasi.keranMinggu(server, me, k));
		}
		batas.putLong(Investasi.K_INVESTASI, Investasi.maks(player));
		batas.putLong(Investasi.K_TOKO, (long) KotaConfig.ekonomi().tokoBatas * (pita + 1) * 7L * Math.max(1, nToko));
		batas.putLong(Investasi.K_BUNGA, 10L * (pita + 1));
		minggu.put(KotaLembar.Milikku.BATAS, batas);
		tag.put(KotaLembar.Milikku.MINGGU, minggu);
		if (daftar.isEmpty()) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable(KotaLayar.K + "milikku.kosong"));
			tag.put(LembarKunci.KOSONG, kosong);
		}
		return tag;
	}

	private static CompoundTag aset(String jenis, String id, String layar, Component nama, long situs, Component ringkas, @Nullable Component warn, int x,
			int z) {
		CompoundTag t = new CompoundTag();
		t.putString(LembarKunci.ID, jenis + "|" + id);
		t.putString(LembarKunci.JENIS, jenis);
		t.putString(KotaLembar.Milikku.LAYAR_TUJUAN, layar);
		LembarKunci.teks(t, LembarKunci.NAMA, nama);
		LembarKunci.teks(t, LembarKunci.TEMPAT, situs == 0L ? Component.empty() : KotaNames.town(situs));
		t.putLong(LembarKunci.SITUS, situs);
		LembarKunci.teks(t, KotaLembar.Milikku.RINGKAS, ringkas);
		if (warn != null) {
			LembarKunci.teks(t, KotaLembar.Milikku.PERINGATAN, warn);
		}
		if (x != 0 || z != 0) {
			t.putInt(LembarKunci.X, x);
			t.putInt(LembarKunci.Z, z);
		}
		return t;
	}

	/** {@code tab:daftar:JENIS|ID}: selects the asset in its screen and opens that screen (remote, read only). */
	private static boolean lompatAset(ServerPlayer player, String arg) {
		int bar = arg.indexOf('|');
		if (bar <= 0) {
			return false;
		}
		String jenis = arg.substring(0, bar);
		String id = arg.substring(bar + 1);
		String layar;
		String tab = null;
		switch (jenis) {
			case KotaLembar.Milikku.MJ_RUMAH -> {
				layar = KotaLembar.Rumah.LAYAR;
				tab = KotaLembar.Rumah.TAB_MILIKKU;
			}
			case KotaLembar.Milikku.MJ_TOKO -> layar = KotaLembar.Toko.LAYAR;
			case KotaLembar.Milikku.MJ_LAPAK -> layar = KotaLembar.LapakSewa.LAYAR;
			case KotaLembar.Milikku.MJ_USAHA -> {
				layar = KotaLembar.Investasi.LAYAR;
				tab = KotaLembar.Investasi.TAB_PORTOFOLIO;
			}
			case KotaLembar.Milikku.MJ_GUDANG -> {
				layar = KotaLembar.Gudang.LAYAR;
				tab = KotaLembar.Gudang.TAB_GUDANG;
			}
			case KotaLembar.Milikku.MJ_KONTRAK -> {
				layar = KotaLembar.Gudang.LAYAR;
				tab = KotaLembar.Gudang.TAB_KONTRAK;
			}
			case KotaLembar.Milikku.MJ_JABATAN -> layar = KotaLembar.Jabatan.LAYAR;
			default -> {
				return false;
			}
		}
		long situs = situsDariId(id);
		if (situs != 0L) {
			LembarKotaDasar.pilihSitus(player, situs);
		}
		LembarKotaDasar.setPilih(player, layar, id);
		return KotaLayar.bukaJauh(player, layar, tab);
	}

	/** The place of an asset id ({@code SITUS:N}, {@code SITUS}, {@code jabatan:SITUS}); 0 when none. */
	static long situsDariId(String id) {
		String s = id.startsWith("jabatan:") ? id.substring(8) : id;
		int colon = s.indexOf(':');
		String head = colon < 0 ? s : s.substring(0, colon);
		try {
			return Long.parseLong(head);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	/** Signed NB text helper ("+28", "-4"). */
	static final class TeksNb {
		static String bertanda(long n) {
			return n > 0 ? "+" + n : Long.toString(n);
		}

		private TeksNb() {
		}
	}

	private LembarBuku() {
	}
}
