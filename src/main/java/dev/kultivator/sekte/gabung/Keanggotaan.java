package dev.kultivator.sekte.gabung;

import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One player's membership record (DESIGN 41.12.1, {@code GabungData.anggota.<uuid>}). Server thread only. A plain data
 * holder: the modules of 41 read and write its fields directly and then call {@code GabungData.setDirty()} (or go
 * through {@code GabungApi}, which does both). PK changes always pass {@code GabungApi.tambahPk}/{@code bayarPk} (one
 * door, the history of 20 lines, the PK bus event).
 * <p>
 * Lazy upkeep (41.12.3): nothing ticks per member. {@link #sentuh} runs the registered {@link Penyentuh}s when the player
 * logs in, enters a compound or opens a screen (allowance, rent, decay of the violation record, hunters), at most once
 * per world day per player.
 * <p>
 * {@link #bagian} is a free compound per module ({@code bagian("hidup")}, {@code bagian("politik")}) so a module of a
 * later wave stores its own extras without editing this file.
 */
public final class Keanggotaan {

	/** Path ids of {@link #jalur}. */
	public static final String JALUR_MURID = "murid";
	public static final String JALUR_TAMU = "tamu";
	/** Lines kept in {@link #riwayat}. */
	public static final int RIWAYAT_MAKS = 20;
	/** Active board missions per player. */
	public static final int MISI_MAKS = 5;
	/** Player disciples registered as wards (41.8.7: at most 1 + rank/2, never more than 5). */
	public static final int TITIPAN_MAKS = 5;

	/** A past membership of one sect: how it ended, the day, and the PK frozen at an honourable leave. */
	public record Riwayat(StatusKeluar status, long hari, int pkBeku) {
	}

	/** One history line: world day, a lang key and its string arguments. */
	public record Catatan(long hari, String kunci, List<String> isian) {
	}

	/** Lazy daily upkeep of one module (41C allowance and rent, 41A decay and hunters...). */
	@FunctionalInterface
	public interface Penyentuh {
		void sentuh(ServerPlayer player, Keanggotaan k, long hari);
	}

	private static final List<Penyentuh> PENYENTUH = new CopyOnWriteArrayList<>();

	/** The sect id ({@code s<i>}) or "" (also the real sect while {@link StatusAnggota#MENYAMAR}). */
	public String sekte = "";
	public StatusAnggota status = StatusAnggota.BEBAS;
	public Pangkat pangkat = Pangkat.PELAYAN;
	/** {@link #JALUR_MURID} or {@link #JALUR_TAMU}. */
	public String jalur = JALUR_MURID;
	/** Spendable personal PK (the same unit as the hall pool, 1 PK = 2 NB, never exchanged). */
	public int pk;
	/** All PK ever earned (never falls; the Papan Jasa ranking). */
	public long pkTotal;
	/** PK earned in month {@link #bulan} (world day / 30). */
	public int pkBulan;
	public long bulan = -1L;
	public long hariMasuk = -1L;
	public long hariPangkat = -1L;
	/** Shifu tokoh id ({@code <s>#t<n>}) or "". */
	public String shifu = "";
	/** Bond with the Shifu, 0-100 (start 20). */
	public int ikatanShifu;
	/** Roster id of the main rival and the eldest senior (39F roster). */
	public String saingan = "";
	public String kakak = "";
	/** Violation record 0-100 ({@link Aturan}). */
	public int pelanggaran;
	/** The last world day the record decayed. */
	public long hariSusut = -1L;
	/** Game tick the Gua Renungan confinement ends (0 = none). */
	public long kurunganSampai;
	/** Residence spot id ({@code bilik}, {@code gua_kecil:<n>} ...) or "" and the paid-until day. */
	public String kediaman = "";
	public long sewaSampai;
	/** Daily chores: the day counted, chores done that day, all chores ever (advancement {@code gabung_tugas}). */
	public long tugasHari = -1L;
	public int tugasJumlah;
	public int tugasTotal;
	/** Active board mission ids (at most {@link #MISI_MAKS}) and all missions completed ({@code gabung_misi_10}). */
	public final List<String> misi = new ArrayList<>();
	public int misiTotal;
	/** World day of the last weekly allowance. */
	public long jatahHari = -1L;
	/** The Teknik Warisan spec id held, or "" (only this spec may be wiped, 41.7.4). */
	public String warisan = "";
	/** Player disciples registered as wards (souls). */
	public final List<UUID> muridTitipan = new ArrayList<>();
	/** Directory bitset by sect index (bit i = sect {@code s<i>} known). */
	public long dikenal;
	/** sect id -> world day an application is allowed again. */
	public final Map<String, Long> jedaLamar = new LinkedHashMap<>();
	/** sect id -> how the player left it. */
	public final Map<String, Riwayat> riwayatSekte = new LinkedHashMap<>();
	/** The last {@link #RIWAYAT_MAKS} events (newest last). */
	public final List<Catatan> riwayat = new ArrayList<>();
	/** Deserter hunt: world day it ends and the hunter squads beaten (3 = the sect gives up). */
	public long pemburuSampai;
	public int pemburuMenang;
	/** MENYAMAR: the second sect and the day the disguise ends. */
	public String samaranSekte = "";
	public long samaranSampai;
	/** CALON: the door, the next Test Day and the day the application lapses. */
	public Pintu pintu = Pintu.GERBANG;
	public long hariUjian = -1L;
	public long calonSampai = -1L;
	/** The last day {@link #sentuh} ran. */
	public long hariSentuh = -1L;
	/** Free per-module compounds. */
	private CompoundTag bagian = new CompoundTag();

	/** Registers a module's lazy daily upkeep (call from the module's init). */
	public static void addPenyentuh(Penyentuh penyentuh) {
		if (penyentuh != null) {
			PENYENTUH.add(penyentuh);
		}
	}

	/** Runs every upkeep once per world day; true when it ran (the caller marks the data dirty). */
	public boolean sentuh(ServerPlayer player, long hari) {
		if (hariSentuh == hari) {
			return false;
		}
		hariSentuh = hari;
		for (Penyentuh p : PENYENTUH) {
			try {
				p.sentuh(player, this, hari);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Gabung upkeep failed for {}", player.getName().getString(), exception);
			}
		}
		return true;
	}

	/** True while the player belongs to {@code sekteId} (Anggota or, for the real sect, Menyamar). */
	public boolean anggotaDi(@Nullable String sekteId) {
		return status.anggota() && sekteId != null && sekteId.equals(sekte);
	}

	/** A sect the player belongs to right now, or null. */
	public @Nullable String sekteAktif() {
		return status.anggota() && !sekte.isEmpty() ? sekte : null;
	}

	/** True when sect index {@code index} is in the directory. */
	public boolean kenal(int index) {
		return index >= 0 && index < 64 && (dikenal & (1L << index)) != 0L;
	}

	/** Adds sect index {@code index} to the directory; true when it was new. */
	public boolean tandaiKenal(int index) {
		if (index < 0 || index >= 64 || kenal(index)) {
			return false;
		}
		dikenal |= 1L << index;
		return true;
	}

	/** World days of membership on day {@code hari} (0 when not a member). */
	public long umurAnggota(long hari) {
		return status.anggota() && hariMasuk >= 0L ? Math.max(0L, hari - hariMasuk) : 0L;
	}

	/** Appends a history line, dropping the oldest beyond {@link #RIWAYAT_MAKS}. */
	public void catat(long hari, String kunci, String... isian) {
		riwayat.add(new Catatan(hari, kunci, isian == null ? List.of() : List.of(isian)));
		while (riwayat.size() > RIWAYAT_MAKS) {
			riwayat.removeFirst();
		}
	}

	/** Rolls the monthly PK counter to month {@code hari / 30}. */
	public void gulirBulan(long hari) {
		long b = Math.floorDiv(hari, 30L);
		if (b != bulan) {
			bulan = b;
			pkBulan = 0;
		}
	}

	/** The free compound of module {@code modul} (created on first use, saved with the record). */
	public CompoundTag bagian(String modul) {
		CompoundTag tag = bagian.getCompoundOrEmpty(modul);
		if (!bagian.contains(modul)) {
			bagian.put(modul, tag);
		}
		return tag;
	}

	/** Clears membership after leaving or a reset: keeps the directory, the past memberships and the history. */
	public void lepas() {
		sekte = "";
		status = StatusAnggota.BEBAS;
		pangkat = Pangkat.PELAYAN;
		jalur = JALUR_MURID;
		pk = 0;
		hariMasuk = -1L;
		hariPangkat = -1L;
		shifu = "";
		ikatanShifu = 0;
		saingan = "";
		kakak = "";
		pelanggaran = 0;
		kurunganSampai = 0L;
		kediaman = "";
		sewaSampai = 0L;
		misi.clear();
		warisan = "";
		muridTitipan.clear();
		samaranSekte = "";
		samaranSampai = 0L;
		pintu = Pintu.GERBANG;
		hariUjian = -1L;
		calonSampai = -1L;
	}

	// ================================================================== save / load

	CompoundTag simpan() {
		CompoundTag t = new CompoundTag();
		t.putString("sekte", sekte);
		t.putString("status", status.id());
		t.putInt("pangkat", pangkat.ordinal());
		t.putString("jalur", jalur);
		t.putInt("pk", pk);
		t.putLong("pkTotal", pkTotal);
		t.putInt("pkBulan", pkBulan);
		t.putLong("bulan", bulan);
		t.putLong("hariMasuk", hariMasuk);
		t.putLong("hariPangkat", hariPangkat);
		t.putString("shifu", shifu);
		t.putInt("ikatanShifu", ikatanShifu);
		t.putString("saingan", saingan);
		t.putString("kakak", kakak);
		t.putInt("pelanggaran", pelanggaran);
		t.putLong("hariSusut", hariSusut);
		t.putLong("kurunganSampai", kurunganSampai);
		t.putString("kediaman", kediaman);
		t.putLong("sewaSampai", sewaSampai);
		t.putLong("tugasHari", tugasHari);
		t.putInt("tugasJumlah", tugasJumlah);
		t.putInt("tugasTotal", tugasTotal);
		ListTag m = new ListTag();
		for (String id : misi) {
			m.add(StringTag.valueOf(id));
		}
		t.put("misi", m);
		t.putInt("misiTotal", misiTotal);
		t.putLong("jatahHari", jatahHari);
		t.putString("warisan", warisan);
		ListTag w = new ListTag();
		for (UUID soul : muridTitipan) {
			w.add(StringTag.valueOf(soul.toString()));
		}
		t.put("muridTitipan", w);
		t.putLong("dikenal", dikenal);
		CompoundTag jeda = new CompoundTag();
		jedaLamar.forEach(jeda::putLong);
		t.put("jedaLamar", jeda);
		CompoundTag rs = new CompoundTag();
		riwayatSekte.forEach((s, r) -> {
			CompoundTag e = new CompoundTag();
			e.putString("status", r.status().id());
			e.putLong("hari", r.hari());
			e.putInt("pkBeku", r.pkBeku());
			rs.put(s, e);
		});
		t.put("riwayatSekte", rs);
		ListTag rw = new ListTag();
		for (Catatan c : riwayat) {
			CompoundTag e = new CompoundTag();
			e.putLong("hari", c.hari());
			e.putString("kunci", c.kunci());
			ListTag isi = new ListTag();
			for (String s : c.isian()) {
				isi.add(StringTag.valueOf(s));
			}
			e.put("isian", isi);
			rw.add(e);
		}
		t.put("riwayat", rw);
		t.putLong("pemburuSampai", pemburuSampai);
		t.putInt("pemburuMenang", pemburuMenang);
		t.putString("samaranSekte", samaranSekte);
		t.putLong("samaranSampai", samaranSampai);
		t.putString("pintu", pintu.id());
		t.putLong("hariUjian", hariUjian);
		t.putLong("calonSampai", calonSampai);
		t.putLong("hariSentuh", hariSentuh);
		t.put("bagian", bagian.copy());
		return t;
	}

	static Keanggotaan muat(CompoundTag t) {
		Keanggotaan k = new Keanggotaan();
		k.sekte = t.getStringOr("sekte", "");
		k.status = StatusAnggota.byId(t.getStringOr("status", StatusAnggota.BEBAS.id()));
		k.pangkat = Pangkat.byOrdinal(t.getIntOr("pangkat", 0));
		k.jalur = JALUR_TAMU.equals(t.getStringOr("jalur", JALUR_MURID)) ? JALUR_TAMU : JALUR_MURID;
		k.pk = Math.max(0, t.getIntOr("pk", 0));
		k.pkTotal = Math.max(0L, t.getLongOr("pkTotal", 0L));
		k.pkBulan = Math.max(0, t.getIntOr("pkBulan", 0));
		k.bulan = t.getLongOr("bulan", -1L);
		k.hariMasuk = t.getLongOr("hariMasuk", -1L);
		k.hariPangkat = t.getLongOr("hariPangkat", -1L);
		k.shifu = t.getStringOr("shifu", "");
		k.ikatanShifu = Math.max(0, Math.min(100, t.getIntOr("ikatanShifu", 0)));
		k.saingan = t.getStringOr("saingan", "");
		k.kakak = t.getStringOr("kakak", "");
		k.pelanggaran = Math.max(0, Math.min(Aturan.MAKS, t.getIntOr("pelanggaran", 0)));
		k.hariSusut = t.getLongOr("hariSusut", -1L);
		k.kurunganSampai = t.getLongOr("kurunganSampai", 0L);
		k.kediaman = t.getStringOr("kediaman", "");
		k.sewaSampai = t.getLongOr("sewaSampai", 0L);
		k.tugasHari = t.getLongOr("tugasHari", -1L);
		k.tugasJumlah = t.getIntOr("tugasJumlah", 0);
		k.tugasTotal = t.getIntOr("tugasTotal", 0);
		ListTag m = t.getListOrEmpty("misi");
		for (int i = 0; i < m.size() && k.misi.size() < MISI_MAKS; i++) {
			String id = m.getStringOr(i, "");
			if (!id.isEmpty()) {
				k.misi.add(id);
			}
		}
		k.misiTotal = t.getIntOr("misiTotal", 0);
		k.jatahHari = t.getLongOr("jatahHari", -1L);
		k.warisan = t.getStringOr("warisan", "");
		ListTag w = t.getListOrEmpty("muridTitipan");
		for (int i = 0; i < w.size() && k.muridTitipan.size() < TITIPAN_MAKS; i++) {
			try {
				k.muridTitipan.add(UUID.fromString(w.getStringOr(i, "")));
			} catch (IllegalArgumentException ignored) {
				// a damaged entry is dropped
			}
		}
		k.dikenal = t.getLongOr("dikenal", 0L);
		CompoundTag jeda = t.getCompoundOrEmpty("jedaLamar");
		for (String s : jeda.keySet()) {
			k.jedaLamar.put(s, jeda.getLongOr(s, 0L));
		}
		CompoundTag rs = t.getCompoundOrEmpty("riwayatSekte");
		for (String s : rs.keySet()) {
			CompoundTag e = rs.getCompoundOrEmpty(s);
			k.riwayatSekte.put(s, new Riwayat(StatusKeluar.byId(e.getStringOr("status", ""), StatusKeluar.PAMIT),
					e.getLongOr("hari", 0L), e.getIntOr("pkBeku", 0)));
		}
		ListTag rw = t.getListOrEmpty("riwayat");
		for (int i = 0; i < rw.size(); i++) {
			CompoundTag e = rw.getCompoundOrEmpty(i);
			ListTag isi = e.getListOrEmpty("isian");
			List<String> args = new ArrayList<>();
			for (int j = 0; j < isi.size(); j++) {
				args.add(isi.getStringOr(j, ""));
			}
			k.riwayat.add(new Catatan(e.getLongOr("hari", 0L), e.getStringOr("kunci", ""), List.copyOf(args)));
		}
		while (k.riwayat.size() > RIWAYAT_MAKS) {
			k.riwayat.removeFirst();
		}
		k.pemburuSampai = t.getLongOr("pemburuSampai", 0L);
		k.pemburuMenang = t.getIntOr("pemburuMenang", 0);
		k.samaranSekte = t.getStringOr("samaranSekte", "");
		k.samaranSampai = t.getLongOr("samaranSampai", 0L);
		k.pintu = Pintu.byId(t.getStringOr("pintu", Pintu.GERBANG.id()), Pintu.GERBANG);
		k.hariUjian = t.getLongOr("hariUjian", -1L);
		k.calonSampai = t.getLongOr("calonSampai", -1L);
		k.hariSentuh = t.getLongOr("hariSentuh", -1L);
		k.bagian = t.getCompoundOrEmpty("bagian").copy();
		return k;
	}
}
