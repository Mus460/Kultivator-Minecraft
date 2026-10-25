package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.ekonomi.GudangKota;
import dev.kultivator.dunia.kota42.ekonomi.KontrakDagang;
import dev.kultivator.dunia.kota42.ekonomi.RumahDagang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builder of {@code gudang} (DESIGN 42.11.8, keys {@link KotaLembar.Gudang}): the one trade ledger of 42, drawn by the
 * 38 Buku Dagang ({@code LayarBukuDagang} in its warehouse mode, "No GUI twice"). Harga (the 12 price categories of the
 * header town, the same rows as the Buku Kota price tab), Gudang (the player's warehouses in every town, the selected
 * one's slots, the sizes for rent here), Kirim (caravans between the player's warehouses on the road: route, days,
 * risk, escort), Kontrak (running delivery contracts with stock against need and the next departure; offers of the Rumah
 * Dagang here) and Pesanan (city orders of 42F with what the player carries). Writes are 42C-2's
 * {@code GudangKota}/{@code KontrakDagang} methods and need the keeper, a porter or the Rumah Dagang clerk.
 */
public final class LembarGudang {

	private static final String L = KotaLembar.Gudang.LAYAR;

	static void init() {
		KotaLayar.daftar(L, LembarGudang::bangun);
		KotaLayar.bawaan(L, KotaLembar.OP_PILIH, (player, npcId, situs, op) -> LembarKotaDasar.setPilih(player, L, LembarKunci.arg(op, 1)));
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_SEWA, (player, npcId, situs, op) -> {
			long[] id = LembarRumah.id(LembarKunci.arg(op, 1));
			if (id != null) {
				int minggu = Math.max(1, Math.min(GudangKota.MINGGU_MAKS, LembarKunci.argInt(op, 2, 1)));
				KotaLayar.hasil(player, GudangKota.sewa(player, id[0], (int) id[1], minggu),
						Component.translatable(KotaLayar.K + "gudang.disewa", GudangKota.UKURAN[(int) Math.max(0, Math.min(2, id[1]))], minggu));
			}
		});
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_PERPANJANG, (player, npcId, situs, op) -> {
			long s = situsArg(op, 1);
			MinecraftServer server = player.level().getServer();
			GudangKota.Gudang g = s == 0L ? null : GudangKota.gudang(server, s, player.getUUID());
			if (g != null) {
				int idx = ukuranIdx(g.ukuran());
				KotaLayar.hasil(player, GudangKota.sewa(player, s, idx, 1), Component.translatable(KotaLayar.K + "gudang.diperpanjang"));
			}
		});
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_TARUH, (player, npcId, situs, op) -> {
			long s = situsArg(op, 1);
			KotaLayar.hasil(player, GudangKota.simpan(player, s == 0L ? situs : s), Component.translatable(KotaLayar.K + "gudang.disimpan"));
		});
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_AMBIL, (player, npcId, situs, op) -> {
			long s = situsArg(op, 1);
			KotaLayar.hasil(player, GudangKota.ambil(player, s == 0L ? situs : s, LembarKunci.argInt(op, 2, -1)),
					Component.translatable(KotaLayar.K + "gudang.diambil"));
		});
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_KIRIM, (player, npcId, situs, op) -> {
			long asal = situsArg(op, 1);
			long tujuan = situsArg(op, 2);
			int slot = LembarKunci.argInt(op, 3, -1);
			List<Integer> slots = slot < 0 ? List.of() : List.of(slot);
			KotaLayar.hasil(player, GudangKota.kirim(player, asal, tujuan, slots), Component.translatable(KotaLayar.K + "gudang.dikirim",
					KotaNames.town(tujuan)));
		});
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_KAWAL, (player, npcId, situs, op) ->
				KotaLayar.hasil(player, GudangKota.kawal(player, LembarKunci.argLong(op, 1, 0L)), Component.translatable(KotaLayar.K + "gudang.dikawal")));
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_AMBIL_KONTRAK, (player, npcId, situs, op) ->
				KotaLayar.hasil(player, KontrakDagang.ambil(player, situs, LembarKunci.argInt(op, 1, -1)), Component.translatable(KotaLayar.K + "kontrak.diambil")));
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_BATAL_KONTRAK, (player, npcId, situs, op) ->
				KotaLayar.hasil(player, KontrakDagang.batal(player, LembarKunci.argInt(op, 1, -1)), Component.translatable(KotaLayar.K + "kontrak.dibatal")));
		KotaLayar.bawaan(L, KotaLembar.Gudang.OP_SERAHKAN, (player, npcId, situs, op) ->
				KotaLayar.hasil(player, KontrakDagang.serahkan(player, situs, LembarKunci.arg(op, 1), Math.max(1, LembarKunci.argInt(op, 2, 1))),
						Component.translatable(KotaLayar.K + "pesanan.diserahkan")));
	}

	private static long situsArg(String[] op, int i) {
		return LembarKunci.argLong(op, i, 0L);
	}

	private static int ukuranIdx(int ukuran) {
		for (int i = 0; i < GudangKota.UKURAN.length; i++) {
			if (GudangKota.UKURAN[i] == ukuran) {
				return i;
			}
		}
		return 0;
	}

	/** "Kontrak II: 64 Teh Awan → Ibu Kota". */
	static Component namaKontrak(KontrakDagang.Kontrak k) {
		Item item = k.item();
		Component barang = item == null ? Component.literal(k.barang()) : Component.translatable(item.getDescriptionId());
		return Component.translatable(KotaLayar.K + "kontrak.nama", romawi(k.tingkat()), k.jumlah(), barang, KotaNames.town(k.tujuan()));
	}

	static String romawi(int n) {
		return switch (Math.max(1, Math.min(5, n))) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> "V";
		};
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		if (npcId >= 0 && !KotaLayar.melayani(player, npcId, L)) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, null, LembarKunci.GAYA_KAYU);
		LembarKotaDasar.kepala(tag, player, situs);
		LembarKunci.teks(tag, LembarKunci.JUDUL, situs == 0L ? Component.translatable(KotaLayar.K + "gudang.judul_umum")
				: Component.translatable(KotaLayar.K + "gudang.judul", KotaNames.town(situs)));
		tag.put(KotaLembar.Gudang.HARGA, situs == 0L ? new ListTag() : LembarBuku.harga(server, situs));
		List<GudangKota.Gudang> semua = GudangKota.semua(server, player.getUUID());
		ListTag gudang = new ListTag();
		for (GudangKota.Gudang g : semua) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, Long.toString(g.situs()));
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(KotaLayar.K + "gudang.nama", KotaNames.town(g.situs())));
			LembarKunci.teks(t, LembarKunci.TEMPAT, KotaNames.town(g.situs()));
			t.putInt(LembarKunci.SLOT, g.terpakai());
			t.putInt(KotaLembar.Gudang.SLOT_MAKS, g.ukuran());
			t.putLong(KotaLembar.SAMPAI, g.sampai());
			t.putLong(KotaLembar.Gudang.SEWA, GudangKota.SEWA[ukuranIdx(g.ukuran())]);
			t.putBoolean(LembarKunci.AKTIF, GudangKota.berlaku(server, g));
			gudang.add(t);
		}
		tag.put(KotaLembar.Gudang.GUDANG, gudang);
		long pilih = 0L;
		try {
			pilih = Long.parseLong(LembarKotaDasar.pilih(player, L));
		} catch (NumberFormatException ignored) {
			// none
		}
		GudangKota.Gudang dipilih = null;
		for (GudangKota.Gudang g : semua) {
			if (g.situs() == pilih || dipilih == null && g.situs() == situs) {
				dipilih = g;
			}
		}
		if (dipilih == null && !semua.isEmpty()) {
			dipilih = semua.get(0);
		}
		if (dipilih != null) {
			tag.putString(KotaLembar.PILIH, Long.toString(dipilih.situs()));
			ListTag isi = new ListTag();
			for (Map.Entry<Integer, ItemStack> e : GudangKota.isi(server, dipilih.situs(), player.getUUID()).entrySet()) {
				if (e.getValue().isEmpty()) {
					continue;
				}
				CompoundTag s = new CompoundTag();
				s.putInt(LembarKunci.SLOT, e.getKey());
				LembarKunci.barang(s, e.getValue(), server.registryAccess());
				s.putInt(LembarKunci.JUMLAH, e.getValue().getCount());
				isi.add(s);
				if (isi.size() >= 108) {
					break;
				}
			}
			tag.put(KotaLembar.Gudang.ISI, isi);
		}
		ListTag tawaranSewa = new ListTag();
		if (situs != 0L && GudangKota.adaGudang(server, situs)) {
			GudangKota.Gudang punya = GudangKota.gudang(server, situs, player.getUUID());
			long uang = tag.getLongOr(LembarKunci.UANG, 0L);
			for (int i = 0; i < GudangKota.UKURAN.length; i++) {
				CompoundTag t = new CompoundTag();
				t.putString(LembarKunci.ID, situs + "_" + i);
				LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(KotaLayar.K + "gudang.ukuran." + i));
				t.putInt(KotaLembar.Gudang.SLOT_MAKS, GudangKota.UKURAN[i]);
				t.putLong(LembarKunci.HARGA, GudangKota.SEWA[i]);
				Component alasan = punya != null && punya.ukuran() > GudangKota.UKURAN[i] ? Component.translatable(KotaLayar.K + "gudang.lebih_kecil")
						: uang < GudangKota.SEWA[i] ? Component.translatable(KotaLayar.K + "harga.kurang", GudangKota.SEWA[i] - uang) : null;
				t.putBoolean(LembarKunci.BISA, alasan == null);
				if (alasan != null) {
					LembarKunci.teks(t, LembarKunci.ALASAN, alasan);
				}
				tawaranSewa.add(t);
			}
		}
		tag.put(KotaLembar.Gudang.TAWARAN_SEWA, tawaranSewa);
		tag.put(KotaLembar.Gudang.KIRIM, kiriman(server, player));
		tag.put(KotaLembar.Gudang.KONTRAK, kontrak(server, player));
		tag.put(KotaLembar.Gudang.TAWARAN, tawaran(server, player, situs));
		tag.putInt(KotaLembar.Gudang.NAMA_DAGANG, KontrakDagang.namaDagang(server, player.getUUID()));
		tag.putInt(KotaLembar.Gudang.TINGKAT, KontrakDagang.tingkat(server, player.getUUID()));
		tag.putInt(KotaLembar.Gudang.KONTRAK_MAKS, KontrakDagang.kontrakMaks(server, player.getUUID()));
		tag.put(KotaLembar.Gudang.PESANAN, pesanan(server, player, situs));
		return tag;
	}

	private static ListTag kiriman(MinecraftServer server, ServerPlayer player) {
		ListTag list = new ListTag();
		for (GudangKota.Kiriman k : GudangKota.kiriman(server, player.getUUID())) {
			if (list.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, Long.toString(k.id()));
			LembarKunci.teks(t, KotaLembar.DARI, KotaNames.town(k.asal()));
			LembarKunci.teks(t, KotaLembar.Gudang.KE, KotaNames.town(k.tujuan()));
			ListTag isi = new ListTag();
			for (ItemStack s : k.isi()) {
				if (s.isEmpty() || isi.size() >= 12) {
					continue;
				}
				CompoundTag b = new CompoundTag();
				LembarKunci.barang(b, s, server.registryAccess());
				b.putInt(LembarKunci.JUMLAH, s.getCount());
				isi.add(b);
			}
			t.put(KotaLembar.Gudang.ISI, isi);
			t.putInt(LembarKunci.SISA_HARI, k.sisaHari());
			t.putInt(KotaLembar.Gudang.RISIKO, k.risiko());
			t.putBoolean(KotaLembar.Gudang.KAWAL, k.kawal());
			t.putLong(KotaLembar.Gudang.ONGKOS, k.ongkos());
			t.putFloat(LembarKunci.KEMAJUAN, (float) Math.max(0.0D, Math.min(1.0D, k.kemajuan())));
			list.add(t);
		}
		return list;
	}

	private static CompoundTag kontrakTag(MinecraftServer server, KontrakDagang.Kontrak k) {
		CompoundTag t = new CompoundTag();
		t.putString(LembarKunci.ID, Integer.toString(k.id()));
		LembarKunci.teks(t, LembarKunci.NAMA, namaKontrak(k));
		Item item = k.item();
		if (item != null) {
			LembarKunci.barang(t, new ItemStack(item), server.registryAccess());
		}
		t.putInt(LembarKunci.JUMLAH, k.jumlah());
		LembarKunci.teks(t, KotaLembar.Gudang.ASAL, KotaNames.town(k.asal()));
		LembarKunci.teks(t, LembarKunci.TUJUAN, KotaNames.town(k.tujuan()));
		t.putInt(KotaLembar.Gudang.TIAP, k.tiap());
		t.putInt(KotaLembar.Gudang.KALI, k.kali());
		t.putInt(KotaLembar.Gudang.KALI_MAKS, k.kaliMaks());
		t.putFloat(KotaLembar.Gudang.HARGA_SATUAN, (float) k.harga());
		t.putInt(KotaLembar.Gudang.DENDA, k.denda());
		t.putInt(KotaLembar.Gudang.TINGKAT, k.tingkat());
		return t;
	}

	private static ListTag kontrak(MinecraftServer server, ServerPlayer player) {
		ListTag list = new ListTag();
		for (KontrakDagang.Kontrak k : KontrakDagang.kontrak(server, player.getUUID())) {
			if (list.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag t = kontrakTag(server, k);
			int stok = KontrakDagang.stok(server, player.getUUID(), k);
			t.putInt(LembarKunci.STOK, stok);
			t.putBoolean(KotaLembar.Gudang.CUKUP, stok >= k.jumlah());
			t.putLong(KotaLembar.Gudang.BERANGKAT, k.berikut());
			t.putString(LembarKunci.KEADAAN, k.jalan() ? "jalan" : stok >= k.jumlah() ? LembarKunci.K_OK : "kurang");
			list.add(t);
		}
		return list;
	}

	private static ListTag tawaran(MinecraftServer server, ServerPlayer player, long situs) {
		ListTag list = new ListTag();
		if (situs == 0L || !RumahDagang.ada(server, situs)) {
			return list;
		}
		for (RumahDagang.Tawaran w : RumahDagang.tawaran(server, situs)) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, Integer.toString(w.id()));
			Identifier id = Identifier.tryParse(w.barang());
			Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
			Component barang = item == null ? Component.literal(w.barang()) : Component.translatable(item.getDescriptionId());
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(KotaLayar.K + "kontrak.nama", romawi(w.tingkat()), w.jumlah(), barang,
					KotaNames.town(w.tujuan())));
			if (item != null) {
				LembarKunci.barang(t, new ItemStack(item), server.registryAccess());
			}
			t.putInt(LembarKunci.JUMLAH, w.jumlah());
			LembarKunci.teks(t, KotaLembar.Gudang.ASAL, KotaNames.town(w.asal()));
			LembarKunci.teks(t, LembarKunci.TUJUAN, KotaNames.town(w.tujuan()));
			t.putInt(KotaLembar.Gudang.TIAP, w.tiap());
			t.putInt(KotaLembar.Gudang.KALI_MAKS, w.kali());
			t.putFloat(KotaLembar.Gudang.HARGA_SATUAN, (float) w.harga());
			t.putInt(KotaLembar.Gudang.DENDA, w.denda());
			t.putInt(KotaLembar.Gudang.TINGKAT, w.tingkat());
			Component alasan = KontrakDagang.alasan(player, situs, w.id());
			t.putBoolean(LembarKunci.BISA, alasan == null);
			if (alasan != null) {
				LembarKunci.teks(t, LembarKunci.ALASAN, alasan);
			}
			list.add(t);
		}
		return list;
	}

	private static ListTag pesanan(MinecraftServer server, ServerPlayer player, long situs) {
		ListTag list = new ListTag();
		if (situs == 0L) {
			return list;
		}
		for (KontrakDagang.Pesanan p : KontrakDagang.pesanan(server, situs)) {
			if (list.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, p.id());
			LembarKunci.barang(t, new ItemStack(p.item()), server.registryAccess());
			t.putInt(KotaLembar.Gudang.PERLU, p.perlu());
			t.putInt(KotaLembar.Gudang.TERKUMPUL, p.terkumpul());
			t.putInt(KotaLembar.Gudang.PUNYA, punya(player, p.item()));
			t.putLong(LembarKunci.HARGA, KontrakDagang.hargaPesanan(server, situs, p.item()));
			t.putInt(LembarKunci.NAMA_BAIK, Math.max(1, p.perlu() / 32));
			list.add(t);
		}
		return list;
	}

	private static int punya(ServerPlayer player, Item item) {
		int n = 0;
		List<ItemStack> tas = new ArrayList<>(player.getInventory().getNonEquipmentItems());
		for (ItemStack s : tas) {
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	static long hari() {
		MinecraftServer server = KotaHidup.server();
		return server == null ? 0L : KotaHidup.hari(server);
	}

	private LembarGudang() {
	}
}
