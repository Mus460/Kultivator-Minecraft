package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomi;
import dev.kultivator.dunia.kota42.tumbuh.BahanKota;
import dev.kultivator.dunia.kota42.tumbuh.Kavling;
import dev.kultivator.dunia.kota42.tumbuh.Kemakmuran;
import dev.kultivator.dunia.kota42.tumbuh.PekerjaanTumbuh;
import dev.kultivator.dunia.kota42.tumbuh.RencanaTumbuh;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.kultivator.dunia.kota42.ui.KotaLembar.Kavling.*;

/**
 * {@code kavling} (DESIGN 42.11.13, keys {@link KotaLembar.Kavling}) from 42F's growth system ({@link Kavling#rinci},
 * {@link RencanaTumbuh}, {@link BahanKota}, {@link PekerjaanTumbuh}, {@link Kemakmuran}); drawn as the client's
 * {@code LayarKavling} (style giok): the lot card with stage x/5, its progress and the crew at work (the crew job of 42F,
 * {@link PerbaikanApi#sisa}), this stage's materials against the town stock and the bag, a top-down silhouette of the
 * finished stage 5, the requirements (prosperity, Kas Kota, blocked: the reason 42F wrote), tab Pilih (rank 4+: every plan
 * of the lot's style and class with the need it answers; picks through 42C-2 {@code Jabatan.pilihRencana}) and tab Dana
 * (donors and their share of the cost; 25 % puts the name on the finished sign).
 * <p>
 * Ops: {@code jual:BH:N} and {@code sumbang:BH:N} (materials from the bag, {@link BahanKota#jual} /
 * {@link BahanKota#sumbang}), {@code sumbang_nb:NB} ({@link Kavling#sumbangNb}), {@code rencana:ID} (the deputy's standing
 * choice, read by 42F when it picks the next plan).
 */
public final class LembarKavling {

	private static final String K = LembarKotaDasar.K + "kavling.";
	/** Share of the cost that puts a donor's name on the finished sign. */
	private static final double PAPAN_BAGIAN = 0.25D;

	private static final Map<String, CompoundTag> SILUET_CACHE = new HashMap<>();
	private static @Nullable MinecraftServer siluetServer;

	/** Prosperity a lot needs before its next stage (42.9.4); the world's {@code kotaTumbuh.ambang}. */
	public static int makmurMin() {
		return KotaConfig.tumbuh().ambang;
	}

	static void init() {
		// The cache key would keep the stopped server (levels, chunks, entities) alive until someone opens a plot sheet again.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			siluetServer = null;
			SILUET_CACHE.clear();
		});
		LembarKotaDasar.daftar(LAYAR, LembarKavling::bangun);
		LembarKotaDasar.bawaan(LAYAR, OP_RENCANA, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				Jabatan.pilihRencana(p, situs, LembarKunci.arg(op, 1)), Component.translatable(K + "rencana_ok")));
		LembarKotaDasar.bawaan(LAYAR, OP_JUAL, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				BahanKota.jual(p, situs, BahanKota.JENIS.indexOf(LembarKunci.arg(op, 1)), LembarKunci.argInt(op, 2, 1)),
				Component.translatable(K + "jual_ok")));
		LembarKotaDasar.bawaan(LAYAR, OP_SUMBANG, (p, npc, situs, op) -> LembarKotaDasar.hasil(p,
				BahanKota.sumbang(p, situs, BahanKota.JENIS.indexOf(LembarKunci.arg(op, 1)), LembarKunci.argInt(op, 2, 1)),
				Component.translatable(K + "sumbang_ok")));
		LembarKotaDasar.bawaan(LAYAR, OP_SUMBANG_NB, (p, npc, situs, op) -> {
			Kavling.Info k = pilihan(p, situs, Kavling.daftar(situs));
			if (k == null) {
				LembarKotaDasar.pesan(p, Component.translatable(K + "kosong"), LembarKunci.PESAN_PERINGATAN);
				return;
			}
			LembarKotaDasar.hasil(p, Kavling.sumbangNb(p, situs, k.id(), LembarKunci.argLong(op, 1, 0L)), Component.translatable(K + "sumbang_nb_ok"));
		});
	}

	// ================================================================== rows for other screens

	/** The {@code kavling} rows of {@code kota_buku}/{@code jabatan}: id I, nama T, rencana T, tahap I, tahap_total I, keadaan S, x I, z I. */
	public static ListTag barisKota(long situs) {
		List<CompoundTag> rows = new ArrayList<>();
		MinecraftServer server = dev.kultivator.dunia.kota42.KotaHidup.server();
		for (Kavling.Info k : Kavling.daftar(situs)) {
			Kavling.Rinci r = server == null ? null : Kavling.rinci(server, situs, k.id());
			CompoundTag t = new CompoundTag();
			t.putInt(LembarKunci.ID, k.id());
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(K + "nama", k.id()));
			LembarKunci.teks(t, RENCANA, namaRencana(k.gaya(), k.jenis()));
			t.putInt(KotaLembar.Kavling.TAHAP_TOTAL, Kavling.SELESAI);
			t.putInt("tahap", Math.max(0, k.tahap()));
			t.putString(LembarKunci.KEADAAN, r != null ? r.keadaan() : keadaan(k));
			t.putInt(LembarKunci.X, k.pos().getX());
			t.putInt(LembarKunci.Z, k.pos().getZ());
			rows.add(t);
		}
		return LembarKotaDasar.daftar(rows);
	}

	private static String keadaan(Kavling.Info k) {
		if (!k.layak()) {
			return KV_TIDAK_LAYAK;
		}
		if (k.selesai()) {
			return KV_SELESAI;
		}
		if (k.jenis().isEmpty()) {
			return KV_KOSONG;
		}
		return k.tahap() > 0 ? KV_DIBANGUN : KV_MENUNGGU;
	}

	static Component namaRencana(String gaya, String jenis) {
		if (jenis.isEmpty()) {
			return Component.translatable(K + "rencana_belum");
		}
		return Component.translatable(K + "rencana_gaya", Component.translatable(K + "jenis." + jenis), Component.translatable(K + "gaya." + gaya));
	}

	// ================================================================== the sheet

	private static @Nullable Kavling.Info pilihan(ServerPlayer p, long situs, List<Kavling.Info> semua) {
		String pilih = LembarKotaDasar.pilih(p, LAYAR);
		for (Kavling.Info k : semua) {
			if (Integer.toString(k.id()).equals(pilih)) {
				return k;
			}
		}
		Kavling.Info dekat = null;
		double best = Double.MAX_VALUE;
		for (Kavling.Info k : semua) {
			double d = k.pos().distSqr(p.blockPosition());
			if (d < best) {
				best = d;
				dekat = k;
			}
		}
		return dekat;
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(K + "judul"), LembarKunci.GAYA_GIOK);
		LembarKotaDasar.kepala(tag, player, situs);
		if (situs == 0L) {
			return LembarKotaDasar.kunci(tag, Component.translatable(LembarKotaDasar.K + "jabatan.tanpa_tempat"));
		}
		Kavling.Info k = pilihan(player, situs, Kavling.daftar(situs));
		Kavling.Rinci rinci = k == null ? null : Kavling.rinci(server, situs, k.id());
		if (k == null || rinci == null) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable(K + "kosong"));
			tag.put(LembarKunci.KOSONG, kosong);
			return tag;
		}
		RencanaTumbuh.Rencana plan = rinci.rencana().isEmpty() ? null : RencanaTumbuh.get(server, rinci.rencana());
		int tahap = Math.max(0, k.tahap());
		int berikut = Math.min(Kavling.SELESAI, tahap + 1);
		int makmur = Kemakmuran.nilai(situs);
		int ambang = makmurMin();
		long kas = KotaEkonomi.kasKota(server, situs);
		String keadaan = rinci.keadaan();

		CompoundTag c = new CompoundTag();
		c.putInt(LembarKunci.ID, k.id());
		LembarKunci.teks(c, LembarKunci.NAMA, Component.translatable(K + "nama", k.id()));
		LembarKunci.teks(c, RENCANA, namaRencana(k.gaya(), k.jenis()));
		c.putString(RENCANA_ID, rinci.rencana());
		c.putString(GAYA_KAVLING, k.gaya());
		c.putString(JENIS, k.jenis());
		c.putInt("tahap", tahap);
		c.putInt(TAHAP_TOTAL, Kavling.SELESAI);
		LembarKunci.teks(c, TAHAP_NAMA, Component.translatable(K + "tahap." + (k.selesai() ? Kavling.SELESAI + 1 : berikut)));
		// the crew job of the running stage: finished operations out of all of them
		int[] sisa = rinci.job() == 0L ? new int[3] : PerbaikanApi.sisa(rinci.job());
		int semua = sisa[0] + sisa[1] + sisa[2];
		c.putInt(KEMAJUAN, k.selesai() ? 100 : semua == 0 ? 0 : Math.min(100, sisa[1] * 100 / semua));
		c.putInt(REGU, rinci.job() == 0L ? 0 : PerbaikanApi.regu(rinci.job()));
		c.putInt(LembarKunci.SISA_HARI, -1);
		c.putString(LembarKunci.KEADAAN, keadaan);
		c.putInt(LembarKunci.X, k.pos().getX());
		c.putInt(LembarKunci.Z, k.pos().getZ());
		Component halang = null;
		if (!rinci.alasan().isEmpty()) {
			halang = Component.translatable("kultivator.kota42.tumbuh.alasan." + rinci.alasan());
		} else if (KV_TIDAK_LAYAK.equals(keadaan)) {
			halang = Component.translatable(K + "halang.tidak_layak");
		} else if (!k.selesai() && makmur < ambang) {
			halang = Component.translatable(K + "halang.makmur", makmur, ambang);
		}
		if (halang != null) {
			LembarKunci.teks(c, TERHALANG, halang);
		}
		tag.put(KAVLING, c);

		if (plan != null) {
			CompoundTag s = siluet(server, plan);
			if (s != null) {
				tag.put(KotaLembar.Kavling.SILUET, s); // the cache map shadows the static-imported key
			}
		}

		// materials of the next stage against the town stock and the bag
		int[] stok = BahanKota.stok(server, situs);
		RencanaTumbuh.Tahap stage = plan == null ? null : plan.tahap(berikut);
		List<CompoundTag> bahan = new ArrayList<>();
		for (int i = 0; i < BahanKota.JENIS.size(); i++) {
			int perlu = stage == null ? 0 : stage.bahan()[i];
			int ada = stok[i];
			if (perlu <= 0 && ada <= 0) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, BahanKota.JENIS.get(i));
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(K + "bahan." + BahanKota.JENIS.get(i)));
			t.putInt(TERKUMPUL, ada);
			t.putInt(PERLU, perlu);
			t.putInt(TAS, BahanKota.tas(player, i));
			t.putLong(LembarKunci.HARGA, Math.max(1L, Math.round(BahanKota.harga(server, situs, i) * 1.1D)));
			t.putBoolean(LembarKunci.OK, ada >= perlu);
			bahan.add(t);
		}
		tag.put(BAHAN, LembarKotaDasar.daftar(bahan));

		List<CompoundTag> syarat = new ArrayList<>();
		syarat.add(LembarKotaDasar.syarat(makmur >= ambang, Component.translatable(K + "syarat.makmur", ambang), Component.literal(Integer.toString(makmur))));
		long biayaTahap = plan == null ? 0L : PekerjaanTumbuh.biayaTahap(server, situs, plan, berikut);
		syarat.add(LembarKotaDasar.syarat(kas >= biayaTahap, Component.translatable(K + "syarat.kas", biayaTahap), Component.literal(Long.toString(kas))));
		syarat.add(LembarKotaDasar.syarat(halang == null, Component.translatable(K + "syarat.halang"),
				halang == null ? Component.translatable(K + "syarat.halang_tidak") : halang));
		tag.put(LembarKunci.SYARAT, LembarKotaDasar.daftar(syarat));

		int pangkat = Jabatan.pangkat(player.getUUID(), situs);
		if (pangkat >= Jabatan.WAKIL && !k.selesai()) {
			String dipilih = Jabatan.pilihanRencana(server, situs);
			List<CompoundTag> pilihan = new ArrayList<>();
			for (RencanaTumbuh.Rencana r : RencanaTumbuh.untuk(server, k.gaya(), rinci.kelas())) {
				CompoundTag t = new CompoundTag();
				t.putString(LembarKunci.ID, r.id());
				LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(K + "jenis." + r.jenis()));
				LembarKunci.teks(t, LembarKunci.TEKS, kebutuhan(r));
				t.putLong(LembarKunci.HARGA, PekerjaanTumbuh.biaya(server, situs, r));
				t.putBoolean(KotaLembar.PILIH, r.id().equals(dipilih));
				t.putBoolean(LembarKunci.BISA, true);
				pilihan.add(t);
			}
			tag.put(PILIHAN, LembarKotaDasar.daftar(pilihan));
		} else {
			CompoundTag kunciTab = new CompoundTag();
			LembarKunci.teks(kunciTab, TAB_PILIH, k.selesai() ? Component.translatable(K + "kunci_selesai")
					: Component.translatable(K + "kunci_pilih", Jabatan.gelar(server, situs, Jabatan.WAKIL)));
			tag.put(LembarKunci.TAB_KUNCI, kunciTab);
		}

		long biayaTotal = plan == null ? 0L : PekerjaanTumbuh.biaya(server, situs, plan);
		long danaTotal = 0L;
		List<CompoundTag> dana = new ArrayList<>();
		boolean papan = false;
		for (Map.Entry<UUID, Long> e : rinci.dana().entrySet()) {
			long nb = e.getValue();
			danaTotal += nb;
			boolean anda = e.getKey().equals(player.getUUID());
			CompoundTag t = new CompoundTag();
			LembarKunci.teks(t, LembarKunci.NAMA, Component.literal(rinci.namaDana().getOrDefault(e.getKey(), "?")));
			t.putBoolean(KotaLembar.PEMAIN, true);
			t.putBoolean(LembarKunci.ANDA, anda);
			t.putLong(LembarKunci.NB, nb);
			t.putLong(LembarKunci.HARGA, nb); // the client reads the donation as the row's price
			t.putInt(PERSEN, biayaTotal <= 0L ? 0 : (int) Math.min(100L, nb * 100L / biayaTotal));
			papan |= anda && biayaTotal > 0L && nb >= PAPAN_BAGIAN * biayaTotal;
			dana.add(t);
		}
		dana.sort((a, b) -> Long.compare(b.getLongOr(LembarKunci.NB, 0L), a.getLongOr(LembarKunci.NB, 0L)));
		tag.put(DANA, LembarKotaDasar.daftar(dana));
		tag.putLong(DANA_TOTAL, danaTotal);
		tag.putLong(BIAYA_TOTAL, biayaTotal);
		tag.putBoolean(PAPAN_NAMA, papan);
		return tag;
	}

	private static Component kebutuhan(RencanaTumbuh.Rencana plan) {
		Component out = null;
		for (String need : plan.kebutuhan()) {
			Component c = Component.translatable(K + "butuh." + need);
			out = out == null ? c : out.copy().append(" · ").append(c);
		}
		return out == null ? Component.translatable(K + "butuh.umum") : out;
	}

	/** Column heights and top map colours of the plan's last stage (42F reads the template; cached per server). */
	private static @Nullable CompoundTag siluet(MinecraftServer server, RencanaTumbuh.Rencana plan) {
		if (siluetServer != server) {
			siluetServer = server;
			SILUET_CACHE.clear();
		}
		CompoundTag cached = SILUET_CACHE.get(plan.id());
		if (cached != null) {
			return cached;
		}
		int[] raw = RencanaTumbuh.siluet(server, plan);
		int w = raw[0];
		int d = raw[1];
		if (w <= 0 || d <= 0 || raw.length < 2 + 2 * w * d) {
			return null;
		}
		int[] tinggi = new int[w * d];
		int[] warna = new int[w * d];
		for (int i = 0; i < w * d; i++) {
			tinggi[i] = raw[2 + i];
			int rgb = raw[2 + w * d + i];
			warna[i] = tinggi[i] > 0 ? 0xFF000000 | rgb : 0;
		}
		CompoundTag s = new CompoundTag();
		s.putInt(KotaLembar.LEBAR, w);
		s.putInt(KotaLembar.PANJANG, d);
		s.put(TINGGI, new IntArrayTag(tinggi));
		s.put(KotaLembar.WARNA, new IntArrayTag(warna));
		SILUET_CACHE.put(plan.id(), s);
		return s;
	}

	private LembarKavling() {
	}
}
