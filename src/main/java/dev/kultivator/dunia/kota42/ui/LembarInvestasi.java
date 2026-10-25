package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.Investasi;
import dev.kultivator.dunia.kota42.ekonomi.UsahaWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Builder of {@code investasi} (DESIGN 42.11.7, keys {@link KotaLembar.Investasi}): stakes in residents' businesses.
 * Portofolio (every stake in every town: share, paid in, value, this week's profit, 7-week line, the owner and the
 * warnings of 42C-2 such as a sick owner or a rival), Peluang (the businesses of the header town open to money: capital,
 * daily turnover, margin, the owner's temperament, why not), Tarik (the running 7-day withdrawals). Writes go through
 * 42C-2's {@code Investasi}; they need the broker (Juragan Shen Wan) or a banker as the session NPC.
 * <p>
 * Ids: {@code SITUS_USAHA}.
 */
public final class LembarInvestasi {

	private static final String L = KotaLembar.Investasi.LAYAR;

	static void init() {
		KotaLayar.daftar(L, LembarInvestasi::bangun);
		KotaLayar.bawaan(L, KotaLembar.OP_PILIH, (player, npcId, situs, op) -> LembarKotaDasar.setPilih(player, L, LembarKunci.arg(op, 1)));
		KotaLayar.bawaan(L, KotaLembar.Investasi.OP_TANAM, (player, npcId, situs, op) -> usaha(player, op, (s, id) -> {
			long nb = Math.max(Investasi.MIN, LembarKunci.argLong(op, 2, Investasi.MIN));
			KotaLayar.hasil(player, Investasi.tanam(player, s, id, nb), Component.translatable(KotaLayar.K + "investasi.ditanam", nb));
		}));
		KotaLayar.bawaan(L, KotaLembar.Investasi.OP_TARIK, (player, npcId, situs, op) -> usaha(player, op, (s, id) ->
				KotaLayar.hasil(player, Investasi.tarik(player, s, id), Component.translatable(KotaLayar.K + "investasi.tarik_mulai", Investasi.TARIK_HARI))));
		KotaLayar.bawaan(L, KotaLembar.Investasi.OP_BATAL_TARIK, (player, npcId, situs, op) -> usaha(player, op, (s, id) ->
				KotaLayar.hasil(player, Investasi.batalTarik(player, s, id), Component.translatable(KotaLayar.K + "investasi.tarik_batal"))));
		KotaLayar.bawaan(L, KotaLembar.Investasi.OP_PENGELOLA, (player, npcId, situs, op) -> usaha(player, op, (s, id) -> {
			UUID soul;
			try {
				soul = UUID.fromString(LembarKunci.arg(op, 2));
			} catch (IllegalArgumentException e) {
				return;
			}
			KotaLayar.hasil(player, Investasi.pengelola(player, s, id, soul), Component.translatable(KotaLayar.K + "investasi.pengelola_ok"));
		}));
		KotaLayar.bawaan(L, KotaLembar.Investasi.OP_LEPAS_PENGELOLA, (player, npcId, situs, op) -> usaha(player, op, (s, id) ->
				KotaLayar.hasil(player, Investasi.pengelola(player, s, id, null), Component.translatable(KotaLayar.K + "investasi.pengelola_lepas"))));
	}

	@FunctionalInterface
	private interface DiUsaha {
		void jalan(long situs, int id);
	}

	private static void usaha(ServerPlayer player, String[] op, DiUsaha aksi) {
		long[] id = LembarRumah.id(LembarKunci.arg(op, 1));
		if (id == null) {
			return;
		}
		LembarKotaDasar.setPilih(player, L, id[0] + "_" + id[1]);
		aksi.jalan(id[0], (int) id[1]);
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		if (npcId >= 0 && !KotaLayar.melayani(player, npcId, L)) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(KotaLayar.K + "investasi.judul"), LembarKunci.GAYA_KAYU);
		LembarKotaDasar.kepala(tag, player, situs);
		List<Investasi.Saham> milik = Investasi.milik(server, player.getUUID());
		long ditanam = 0L;
		long nilai = 0L;
		long minggu = 0L;
		ListTag daftar = new ListTag();
		for (Investasi.Saham sh : milik) {
			ditanam += sh.setor();
			nilai += sh.nilai();
			minggu += sh.labaMinggu();
			if (daftar.size() < LembarKunci.DAFTAR_MAKS) {
				daftar.add(saham(server, player, sh));
			}
		}
		CompoundTag ringkas = new CompoundTag();
		ringkas.putLong(KotaLembar.Investasi.DITANAM, ditanam);
		ringkas.putLong(LembarKunci.NILAI, nilai);
		ringkas.putLong(KotaLembar.Investasi.MINGGU_INI, minggu);
		ringkas.putLong(KotaLembar.BATAS, Investasi.maks(player));
		ringkas.putLong(KotaLembar.TERPAKAI, Investasi.keranMinggu(server, player.getUUID(), Investasi.K_INVESTASI));
		ringkas.putInt(KotaLembar.Investasi.USAHA, milik.size());
		ringkas.putInt(KotaLembar.Investasi.USAHA_MAKS, Investasi.MAKS_TOTAL);
		ringkas.putInt(KotaLembar.Investasi.PER_TEMPAT_MAKS, Investasi.MAKS_PER_TEMPAT);
		tag.put(KotaLembar.Investasi.RINGKAS, ringkas);
		tag.put(LembarKunci.DAFTAR, daftar);
		ListTag peluang = new ListTag();
		if (situs != 0L && UsahaWarga.aktif()) {
			for (UsahaWarga.Usaha u : UsahaWarga.usaha(situs)) {
				if (peluang.size() >= LembarKunci.DAFTAR_MAKS) {
					break;
				}
				peluang.add(peluang(server, player, situs, u));
			}
		}
		tag.put(KotaLembar.Investasi.PELUANG, peluang);
		String pilih = LembarKotaDasar.pilih(player, L);
		if (!pilih.isEmpty()) {
			tag.putString(KotaLembar.PILIH, pilih.replace(':', '_'));
		}
		if (!Investasi.aktif()) {
			LembarKotaDasar.kunci(tag, Component.translatable(KotaLayar.K + "investasi.mati"));
		}
		return tag;
	}

	private static CompoundTag saham(MinecraftServer server, ServerPlayer player, Investasi.Saham sh) {
		CompoundTag t = new CompoundTag();
		t.putString(LembarKunci.ID, sh.situs() + "_" + sh.usaha());
		UsahaWarga.Usaha u = UsahaWarga.usaha(sh.situs(), sh.usaha());
		LembarKunci.teks(t, LembarKunci.NAMA, u == null ? Component.translatable(KotaLayar.K + "investasi.usaha_hilang") : UsahaWarga.nama(u));
		LembarKunci.teks(t, LembarKunci.TEMPAT, KotaNames.town(sh.situs()));
		t.putFloat(KotaLembar.Investasi.SAHAM, (float) sh.bagian());
		t.putLong(KotaLembar.Investasi.SETOR, sh.setor());
		t.putLong(LembarKunci.NILAI, sh.nilai());
		t.putLong(KotaLembar.Investasi.LABA_MINGGU, sh.labaMinggu());
		long[] g = sh.grafik() == null ? new long[0] : sh.grafik();
		int[] grafik = new int[g.length];
		for (int i = 0; i < g.length; i++) {
			grafik[i] = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, g[i]));
		}
		t.putIntArray(KotaLembar.GRAFIK, grafik);
		if (u != null) {
			Warga pemilik = WargaApi.byId(u.pemilikWarga());
			if (pemilik != null) {
				LembarKunci.teks(t, KotaLembar.Investasi.PEMILIK, WargaApi.nama(pemilik));
				t.putInt(KotaLembar.Investasi.PEMILIK_WARGA, pemilik.id);
			}
			Component warn = UsahaWarga.peringatan(server, sh.situs(), sh.usaha());
			if (warn != null) {
				LembarKunci.teks(t, KotaLembar.Investasi.PERINGATAN, warn);
			}
		}
		if (sh.tarikHari() > 0L) {
			t.putLong(KotaLembar.Investasi.TARIK_HARI, sh.tarikHari());
		}
		if (sh.pengelola() != null && !sh.pengelola().isEmpty()) {
			LembarKunci.teks(t, KotaLembar.Investasi.PENGELOLA, Component.translatable(KotaLayar.K + "investasi.dikelola_murid"));
		}
		return t;
	}

	private static CompoundTag peluang(MinecraftServer server, ServerPlayer player, long situs, UsahaWarga.Usaha u) {
		CompoundTag t = new CompoundTag();
		t.putString(LembarKunci.ID, situs + "_" + u.id());
		LembarKunci.teks(t, LembarKunci.NAMA, UsahaWarga.nama(u));
		LembarKunci.teks(t, LembarKunci.TEMPAT, KotaNames.town(situs));
		t.putString(LembarKunci.JENIS, u.jenis());
		t.putLong(KotaLembar.Investasi.MODAL, u.modal());
		t.putLong(KotaLembar.Investasi.OMZET, UsahaWarga.omzet(server, situs, u.id()));
		t.putInt(KotaLembar.Investasi.MARGIN, UsahaWarga.marginPersen(u));
		Warga pemilik = WargaApi.byId(u.pemilikWarga());
		if (pemilik != null) {
			LembarKunci.teks(t, KotaLembar.Investasi.PEMILIK, WargaApi.nama(pemilik));
			t.putInt(KotaLembar.Investasi.PEMILIK_WARGA, pemilik.id);
			if (pemilik.watak >= 0 && pemilik.watak < Tokoh.WATAK.size()) {
				t.putString(KotaLembar.WATAK, Tokoh.WATAK.get(pemilik.watak));
			}
		}
		t.putLong(KotaLembar.Investasi.MIN, Investasi.MIN);
		t.putLong(KotaLembar.Investasi.MAKS, Math.max(Investasi.MIN, Investasi.maks(player)));
		Component alasan = Investasi.alasan(player, situs, u.id(), Investasi.MIN);
		t.putBoolean(LembarKunci.BISA, alasan == null);
		if (alasan != null) {
			LembarKunci.teks(t, LembarKunci.ALASAN, alasan);
		}
		Component warn = UsahaWarga.peringatan(server, situs, u.id());
		if (warn != null) {
			LembarKunci.teks(t, KotaLembar.Investasi.PERINGATAN, warn);
		}
		return t;
	}

	private LembarInvestasi() {
	}
}
