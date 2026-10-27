package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.kota42.sekte.CabangKota;
import dev.kultivator.dunia.kota42.sekte.KotaSekte;
import dev.kultivator.dunia.kota42.sekte.PaktaLindung;
import dev.kultivator.dunia.kota42.sekte.TugasKotaMurid;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.kultivator.dunia.kota42.ui.KotaLembar.Cabang.*;

/**
 * {@code cabang} (DESIGN 42.11.12, keys {@link KotaLembar.Cabang}) from 42E's {@link CabangKota}: the player sect's branch in
 * a house of the town ("Sekte Awan Ungu · Cabang Kota Teratai"): the three kinds (Toko Pil, Balai Latihan, Klinik) each with
 * what it needs inside the house (counted live when the house is loaded) and what it gives, the keeper, the effects now in
 * force and the weekly reports. Without a branch the top-level {@code syarat} list shows what opening one takes. Drawn as
 * the Cabang page of the 41 sect-politics screen ({@code LayarPolitik}).
 * <p>
 * Ops: {@code jenis:CJ} (every 7 days), {@code tugaskan:SOUL} (a disciple keeps the branch), {@code tutup} (asks to
 * confirm) then {@code tutup_ya}, {@code rumah} (opens {@code rumah} on the branch's building).
 */
public final class LembarCabang {

	private static final String K = LembarKotaDasar.K + "cabang.";

	static void init() {
		String layar = LAYAR;
		LembarKotaDasar.daftar(layar, LembarCabang::bangun);
		LembarKotaDasar.bawaan(layar, OP_JENIS, (p, npc, situs, op) -> jawab(p, CabangKota.pilihJenis(p, situs, LembarKunci.arg(op, 1))));
		LembarKotaDasar.bawaan(layar, OP_TUGASKAN, (p, npc, situs, op) -> {
			String soul = LembarKunci.arg(op, 1);
			try {
				jawab(p, TugasKotaMurid.tugaskan(p, situs, soul == null || soul.isEmpty() ? null : UUID.fromString(soul), TugasKotaMurid.KERJA_KOTA));
			} catch (IllegalArgumentException e) {
				LembarKotaDasar.pesan(p, Component.translatable(K + "tanpa_murid"), LembarKunci.PESAN_PERINGATAN);
			}
		});
		LembarKotaDasar.bawaan(layar, OP_TUTUP, (p, npc, situs, op) -> LembarKotaDasar.pesan(p, Component.translatable(K + "tutup_tanya"), LembarKunci.PESAN_PERINGATAN));
		LembarKotaDasar.bawaan(layar, OP_TUTUP_YA, (p, npc, situs, op) -> jawab(p, CabangKota.tutup(p, situs)));
		LembarKotaDasar.bawaan(layar, OP_RUMAH, (p, npc, situs, op) -> {
			CabangKota.Cabang c = CabangKota.cabang(situs);
			BlockPos papan = c == null ? null : CabangKota.papan(p.level().getServer(), situs);
			if (c == null || papan == null || !c.pemilik().equals(p.getUUID())) {
				LembarKotaDasar.pesan(p, Component.translatable(K + "tanpa_cabang"), LembarKunci.PESAN_PERINGATAN);
				return;
			}
			LembarKotaDasar.setPilih(p, KotaLembar.Rumah.LAYAR, situs + "_" + c.rumah());
			KotaLayar.bukaDi(p, KotaLembar.Rumah.LAYAR, papan, null);
		});
	}

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
			// a remote session shows the player's first branch
			List<CabangKota.Cabang> milik = CabangKota.milik(server, id);
			if (milik.isEmpty()) {
				return LembarKotaDasar.kunci(tag, Component.translatable(K + "tanpa_tempat"));
			}
			situs = milik.get(0).situs();
			LembarKotaDasar.kepala(tag, player, situs);
		}
		CabangKota.Cabang c = CabangKota.cabang(situs);
		CompoundTag cab = new CompoundTag();
		cab.putBoolean(ADA, c != null);
		if (c == null) {
			tag.put(CABANG, cab);
			tag.put(LembarKunci.SYARAT, syaratBuka(player, situs));
			tag.put(LembarKunci.JENIS, jenis(null, List.of(), null));
			return tag;
		}
		boolean milikku = c.pemilik().equals(id);
		LembarKunci.teks(cab, LembarKunci.NAMA, CabangKota.nama(server, c));
		cab.putString(JENIS, c.jenis());
		cab.putLong(GANTI, CabangKota.gantiBoleh(server, situs));
		cab.putInt(RENOWN, 2);
		cab.putString(RUMAH, Integer.toString(c.rumah()));
		List<CabangKota.Isi> isi = List.of();
		RumahKota.Info rumah = RumahKota.info(server, situs, c.rumah());
		if (milikku && rumah != null && player.level() instanceof ServerLevel level && level.isLoaded(rumah.kotak().getCenter())) {
			CabangKota.periksa(level, situs);
			isi = CabangKota.isi(level, rumah.kotak(), c.jenis());
		}
		boolean bekerja = CabangKota.bekerja(server, situs);
		List<CompoundTag> baris = new ArrayList<>();
		for (CabangKota.Isi i : isi) {
			CompoundTag t = new CompoundTag();
			LembarKunci.teks(t, LembarKunci.TEKS, i.teks());
			t.putInt(PUNYA, i.punya());
			t.putInt(PERLU, i.perlu());
			t.putBoolean(LembarKunci.OK, i.ok());
			baris.add(t);
		}
		cab.put(ISI, LembarKotaDasar.daftar(baris));

		List<CompoundTag> penjaga = new ArrayList<>();
		TugasKotaMurid.Tugas jaga = TugasKotaMurid.penjagaDi(situs, TugasKotaMurid.SASARAN_CABANG, c.pemilik());
		if (jaga != null) {
			CompoundTag g = new CompoundTag();
			LembarKunci.teks(g, LembarKunci.NAMA, Component.literal(jaga.nama()));
			g.putString(KotaLembar.SOUL, jaga.soul().toString());
			penjaga.add(g);
		}
		cab.put(PENJAGA, LembarKotaDasar.daftar(penjaga));

		List<CompoundTag> efek = new ArrayList<>();
		efek.add(LembarKotaDasar.baris(Component.translatable(K + (bekerja ? "efek.bekerja" : "efek.belum"))));
		if (bekerja) {
			efek.add(LembarKotaDasar.baris(Component.translatable(K + "efek." + c.jenis())));
			if (c.pemilik().equals(PaktaLindung.pelindung(situs))) {
				efek.add(LembarKotaDasar.baris(Component.translatable(K + "efek.pakta")));
			}
		}
		cab.put(EFEK, LembarKotaDasar.daftar(efek));

		List<CompoundTag> laporan = new ArrayList<>();
		ListTag lap = CabangKota.laporan(server, situs);
		for (int i = lap.size() - 1; i >= 0; i--) {
			CompoundTag e = lap.getCompoundOrEmpty(i);
			CompoundTag t = new CompoundTag();
			t.putLong(LembarKunci.HARI, e.getLongOr("d", 0L));
			LembarKunci.teks(t, LembarKunci.TEKS, Component.translatable(e.getStringOr("k", K + "lap.kosong")));
			t.putLong("nb", e.getLongOr("nb", 0L));
			laporan.add(t);
		}
		cab.put(LAPORAN, LembarKotaDasar.daftar(laporan));
		tag.put(CABANG, cab);
		tag.put(LembarKunci.JENIS, jenis(c.jenis(), isi, rumah));
		return tag;
	}

	/** The three kinds with what each needs; the contents are only counted for the kind in force. */
	private static ListTag jenis(@Nullable String kini, List<CabangKota.Isi> isiKini, @Nullable RumahKota.Info rumah) {
		List<CompoundTag> out = new ArrayList<>();
		for (String j : SEMUA_JENIS) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, j);
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(K + "jenis." + j));
			LembarKunci.teks(t, LembarKunci.TEKS, Component.translatable(K + "jenis." + j + ".teks"));
			t.putBoolean(KotaLembar.PILIH, j.equals(kini));
			List<CompoundTag> syarat = new ArrayList<>();
			if (j.equals(kini)) {
				for (CabangKota.Isi i : isiKini) {
					CompoundTag s = new CompoundTag();
					s.putBoolean(LembarKunci.OK, i.ok());
					LembarKunci.teks(s, LembarKunci.TEKS, i.teks());
					syarat.add(s);
				}
			} else {
				for (String n : NEED.getOrDefault(j, List.of())) {
					CompoundTag s = new CompoundTag();
					s.putBoolean(LembarKunci.OK, false);
					LembarKunci.teks(s, LembarKunci.TEKS, Component.translatable(K + "perlu." + n));
					syarat.add(s);
				}
			}
			t.put(LembarKunci.SYARAT, LembarKotaDasar.daftar(syarat));
			out.add(t);
		}
		return LembarKotaDasar.daftar(out);
	}

	/** What each kind needs, as lang ids (the same lines 42E counts: racks, counter, dummies, cauldron, beds). */
	private static final java.util.Map<String, List<String>> NEED = java.util.Map.of(
			CJ_TOKO_PIL, List.of("rak", "kasir"),
			CJ_BALAI_LATIHAN, List.of("boneka"),
			CJ_KLINIK, List.of("kuali", "kasur"));

	/** No branch: the requirements to open one (a house of the player, hall level, pact or Nama Baik). */
	private static ListTag syaratBuka(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		UUID id = player.getUUID();
		List<CompoundTag> out = new ArrayList<>();
		int level = Halls.level(id);
		out.add(LembarKotaDasar.syarat(level >= CabangKota.AULA_MIN, Component.translatable(K + "syarat.aula", CabangKota.AULA_MIN),
				Component.literal(Integer.toString(level))));
		RumahKota.Info rumah = situs == 0L ? null : RumahKota.milikDiTempat(server, situs, id);
		out.add(LembarKotaDasar.syarat(rumah != null, Component.translatable(K + "syarat.rumah"),
				rumah == null ? Component.translatable(K + "syarat.tiada") : RumahKota.nama(server, rumah)));
		int nb = situs == 0L ? 0 : KotaSekte.namaBaik(server, situs, id);
		boolean pakta = id.equals(PaktaLindung.pelindung(situs));
		out.add(LembarKotaDasar.syarat(pakta || nb >= CabangKota.NAMA_BAIK_MIN, Component.translatable(K + "syarat.pakta", CabangKota.NAMA_BAIK_MIN),
				Component.translatable(pakta ? K + "syarat.pakta_ada" : K + "syarat.nama_baik", nb)));
		return LembarKotaDasar.daftar(out);
	}

	private LembarCabang() {
	}
}
