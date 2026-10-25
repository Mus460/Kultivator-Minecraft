package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.ekonomi.LapakKota;
import dev.kultivator.dunia.kota42.ekonomi.RakDagangBlockEntity;
import dev.kultivator.dunia.kota42.ekonomi.TokoPemain;
import dev.kultivator.dunia.kota42.warga.Pekerjaan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.cerita.Isian;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builders of {@code toko_pemain} (DESIGN 42.11.6, keys {@link KotaLembar.Toko}) and {@code lapak_sewa} (42.11.14,
 * {@link KotaLembar.LapakSewa}): the player's own shop and the town stalls to rent. Drawn by the 38 ledger
 * ({@code LayarBukuKas}) in its shop and stall modes ("No GUI twice": one shop/stall manager).
 * <p>
 * Shop: racks and shelves with the asking price against the town price ({@code h}), the sale chance per busy hour, the
 * till against the daily cap, the clerk (resident or disciple) and the candidates, the 7-day report and the week's best
 * goods. Every write is a 42C {@code TokoPemain} method; the shop opens from its cash counter or sign (anchor, F42-2
 * wiring), the command or the ledger (remote, read only), or when the player stands inside it (anchor at the player).
 * <p>
 * Shop ids: {@code SITUS_rN} (rumah toko N) or {@code SITUS_lN} (stall N).
 */
public final class LembarToko {

	private static final String T = KotaLembar.Toko.LAYAR;
	private static final String S = KotaLembar.LapakSewa.LAYAR;

	static void init() {
		KotaLayar.daftar(T, LembarToko::bangunToko);
		KotaLayar.daftar(S, LembarToko::bangunLapak);
		KotaLayar.bawaan(T, KotaLembar.OP_PILIH, (player, npcId, situs, op) ->
				LembarKotaDasar.setPilih(player, T + ".rak", LembarKunci.argInt(op, 1, 0) + ":" + LembarKunci.argInt(op, 2, 0)));
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_HARGA, (player, npcId, situs, op) -> rak(player, op, (toko, rak, slot) ->
				TokoPemain.harga(player, rak, slot, Math.max(1L, LembarKunci.argLong(op, 3, 1L)))));
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_HARGA_PASAR, (player, npcId, situs, op) -> rak(player, op, (toko, rak, slot) ->
				TokoPemain.hargaPasar(player, rak, slot)));
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_AMBIL_LACI, (player, npcId, situs, op) -> {
			TokoPemain.Toko toko = tokoTerpilih(player);
			if (toko != null) {
				TokoPemain.ambilLaci(player, toko);
			}
		});
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_BUKA, (player, npcId, situs, op) -> {
			TokoPemain.Toko toko = tokoTerpilih(player);
			if (toko != null) {
				TokoPemain.buka(player, toko, LembarKunci.argInt(op, 1, 1) != 0);
			}
		});
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_NAMA, (player, npcId, situs, op) -> {
			TokoPemain.Toko toko = tokoTerpilih(player);
			// the name may contain ':' (split by the op parser): join the rest back
			StringBuilder nama = new StringBuilder();
			for (int i = 1; i < op.length; i++) {
				if (i > 1) {
					nama.append(':');
				}
				nama.append(op[i]);
			}
			if (toko != null && !nama.toString().isBlank()) {
				TokoPemain.nama(player, toko, nama.toString());
			}
		});
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_PEKERJAKAN, (player, npcId, situs, op) -> {
			TokoPemain.Toko toko = tokoTerpilih(player);
			if (toko != null) {
				TokoPemain.pekerjakan(player, toko, LembarKunci.argInt(op, 1, 0));
			}
		});
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_PEKERJAKAN_MURID, (player, npcId, situs, op) -> {
			TokoPemain.Toko toko = tokoTerpilih(player);
			if (toko != null) {
				TokoPemain.pekerjakanMurid(player, toko);
			}
		});
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_BERHENTIKAN, (player, npcId, situs, op) -> {
			TokoPemain.Toko toko = tokoTerpilih(player);
			if (toko != null) {
				TokoPemain.berhentikan(player, toko);
			}
		});
		KotaLayar.bawaan(T, KotaLembar.Toko.OP_KE_BANK, (player, npcId, situs, op) ->
				KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "toko.ke_bank_belum"), LembarKunci.PESAN_PERINGATAN));
		KotaLayar.bawaan(S, KotaLembar.LapakSewa.OP_SEWA, (player, npcId, situs, op) -> lapak(player, op, (s, no) ->
				LapakKota.sewa(player, s, no, Math.max(1, Math.min(LapakKota.MINGGU_MAKS, LembarKunci.argInt(op, 2, 1))))));
		KotaLayar.bawaan(S, KotaLembar.LapakSewa.OP_PERPANJANG, (player, npcId, situs, op) -> lapak(player, op, (s, no) ->
				LapakKota.perpanjang(player, s, no, Math.max(1, Math.min(LapakKota.MINGGU_MAKS, LembarKunci.argInt(op, 2, 1))))));
		KotaLayar.bawaan(S, KotaLembar.LapakSewa.OP_LEPAS, (player, npcId, situs, op) -> lapak(player, op, (s, no) -> LapakKota.lepas(player, s, no)));
		KotaLayar.bawaan(S, KotaLembar.LapakSewa.OP_TOKO, (player, npcId, situs, op) -> {
			long[] id = LembarRumah.id(LembarKunci.arg(op, 1));
			MinecraftServer server = player.level().getServer();
			TokoPemain.Toko toko = id == null ? null : TokoPemain.tokoLapak(server, id[0], (int) id[1]);
			if (toko == null) {
				KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "lapak.tanpa_toko"), LembarKunci.PESAN_PERINGATAN);
				return;
			}
			LembarKotaDasar.setPilih(player, T, idToko(toko));
			if (npcId >= 0) {
				KotaLayar.buka(player, T, npcId);
			} else {
				KotaLayar.bukaJauh(player, T, null);
			}
		});
	}

	// ================================================================== ids

	/** {@code SITUS_rN} / {@code SITUS_lN} of a shop (the 42C shop id behind the place). */
	static String idToko(TokoPemain.Toko toko) {
		MinecraftServer server = KotaHidup.server();
		CompoundTag t = server == null ? null : TokoPemain.data(server, toko);
		String id = t == null ? "" : t.getStringOr("id", "");
		return toko.situs() + "_" + id;
	}

	/** The player's shop of id {@code id} ({@link #idToko}), or null. */
	static @Nullable TokoPemain.Toko tokoDariId(MinecraftServer server, UUID pemilik, String id) {
		for (TokoPemain.Toko toko : TokoPemain.milik(server, pemilik)) {
			if (idToko(toko).equals(id)) {
				return toko;
			}
		}
		return null;
	}

	/** The selected shop, else the shop the player stands in, else the player's first shop. */
	static @Nullable TokoPemain.Toko tokoTerpilih(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		TokoPemain.Toko toko = tokoDariId(server, player.getUUID(), LembarKotaDasar.pilih(player, T));
		if (toko != null) {
			return toko;
		}
		TokoPemain.Toko di = TokoPemain.di(player.level(), player.blockPosition());
		if (di != null && player.getUUID().equals(di.pemilik())) {
			return di;
		}
		List<TokoPemain.Toko> semua = TokoPemain.milik(server, player.getUUID());
		return semua.isEmpty() ? null : semua.get(0);
	}

	/**
	 * Opens the shop screen of the player's shop at {@code pos} as an anchor session (the cash counter, the sign, or the
	 * player inside the shop); for F42-2's wiring of {@code TokoPemain.kasirDipakai} and the stall tent.
	 */
	public static boolean buka(ServerPlayer player, TokoPemain.Toko toko, BlockPos pos) {
		LembarKotaDasar.setPilih(player, T, idToko(toko));
		return KotaLayar.bukaDi(player, T, pos, null);
	}

	@FunctionalInterface
	private interface DiRak {
		void jalan(TokoPemain.Toko toko, RakDagangBlockEntity rak, int slot);
	}

	private static void rak(ServerPlayer player, String[] op, DiRak aksi) {
		TokoPemain.Toko toko = tokoTerpilih(player);
		if (toko == null) {
			return;
		}
		List<RakDagangBlockEntity> racks = TokoPemain.rak(player.level(), toko);
		int r = LembarKunci.argInt(op, 1, -1);
		int slot = LembarKunci.argInt(op, 2, -1);
		if (r < 0 || r >= racks.size() || slot < 0 || slot >= RakDagangBlockEntity.SLOT) {
			return;
		}
		LembarKotaDasar.setPilih(player, T + ".rak", r + ":" + slot);
		aksi.jalan(toko, racks.get(r), slot);
	}

	@FunctionalInterface
	private interface DiLapak {
		dev.kultivator.dunia.kota42.ekonomi.RumahKota.Hasil jalan(long situs, int no);
	}

	private static void lapak(ServerPlayer player, String[] op, DiLapak aksi) {
		long[] id = LembarRumah.id(LembarKunci.arg(op, 1));
		if (id == null) {
			return;
		}
		dev.kultivator.dunia.kota42.ekonomi.RumahKota.Hasil h = aksi.jalan(id[0], (int) id[1]);
		KotaLayar.pesan(player, h.pesan(), h.ok() ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA);
	}

	// ================================================================== shop

	private static @Nullable CompoundTag bangunToko(ServerPlayer player, int npcId, long situsSesi) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, null, LembarKunci.GAYA_KAYU);
		TokoPemain.Toko toko = tokoTerpilih(player);
		if (toko == null) {
			LembarKotaDasar.kepala(tag, player, situsSesi);
			return LembarKotaDasar.kunci(tag, Component.translatable(KotaLayar.K + "toko.belum_punya"));
		}
		CompoundTag t = TokoPemain.data(server, toko);
		long situs = toko.situs();
		LembarKotaDasar.kepala(tag, player, situs);
		Component nama = toko.nama().isEmpty() ? Component.translatable(KotaLayar.K + "toko.tanpa_nama") : Component.literal(toko.nama());
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(KotaLayar.K + "toko.judul", nama));
		CompoundTag info = new CompoundTag();
		info.putString(LembarKunci.ID, idToko(toko));
		LembarKunci.teks(info, LembarKunci.NAMA, nama);
		String jenis = t == null ? TokoPemain.TJ_RUMAH_TOKO : t.getStringOr("j", TokoPemain.TJ_RUMAH_TOKO);
		info.putString(LembarKunci.JENIS, jenis);
		info.putInt(KotaLembar.Toko.REPUTASI, t == null ? TokoPemain.REP_AWAL : t.getIntOr("rep", TokoPemain.REP_AWAL));
		boolean buka = t == null || t.getBooleanOr("buka", true);
		info.putBoolean(KotaLembar.Toko.BUKA, buka);
		if (!buka) {
			LembarKunci.teks(info, KotaLembar.Toko.TUTUP, Component.translatable(KotaLayar.K + "toko.ditutup_pemilik"));
		} else if (t != null && t.getIntOr("pg", 0) == 0 && !t.contains("pgm")) {
			LembarKunci.teks(info, KotaLembar.Toko.TUTUP, Component.translatable(KotaLayar.K + "toko.tanpa_penjaga", (int) TokoPemain.HADIR_JARAK));
		}
		info.putInt(KotaLembar.PAJAK, (int) Math.round(TokoPemain.PAJAK * 100.0D));
		info.putLong(KotaLembar.Toko.LACI, t == null ? 0L : t.getLongOr("laci", 0L));
		info.putBoolean(KotaLembar.Toko.KE_BANK, false);
		info.putLong(KotaLembar.BATAS, t == null ? 0L : TokoPemain.batasHarian(t));
		info.putLong(KotaLembar.TERPAKAI, t == null ? 0L : t.getLongOr("tp", 0L));
		info.putInt(KotaLembar.Toko.RAK_MAKS, TokoPemain.RAK_MAKS);
		tag.put(KotaLembar.Toko.TOKO, info);
		List<RakDagangBlockEntity> racks = TokoPemain.rak(player.level(), toko);
		ListTag rak = new ListTag();
		int[] pilih = pilihRak(player);
		CompoundTag rinci = null;
		for (int i = 0; i < racks.size(); i++) {
			RakDagangBlockEntity r = racks.get(i);
			CompoundTag row = new CompoundTag();
			row.putInt(LembarKunci.NO, i);
			BlockPos p = r.getBlockPos();
			row.putInt(LembarKunci.X, p.getX());
			row.putInt(KotaLembar.Toko.Y, p.getY());
			row.putInt(LembarKunci.Z, p.getZ());
			ListTag isi = new ListTag();
			for (int slot = 0; slot < RakDagangBlockEntity.SLOT; slot++) {
				ItemStack stack = r.barang(slot);
				if (stack.isEmpty()) {
					continue;
				}
				CompoundTag s = rakSlot(server, situs, stack, slot, r.harga(slot), player);
				isi.add(s);
				if (i == pilih[0] && slot == pilih[1]) {
					rinci = s.copy();
					rinci.putInt(KotaLembar.Toko.RAK, i);
					rinci.putInt(LembarKunci.SLOT, slot);
					rinci.putInt(KotaLembar.TREN, HargaKota.tren(server, situs, Kategori.dari(stack)));
					rinci.putLong(KotaLembar.Toko.HARGA_PASAR, TokoPemain.hargaKota(server, situs, stack));
					rinci.putLong(KotaLembar.Toko.HARGA_MAKS, TokoPemain.hargaMaks(stack));
				}
			}
			row.put(KotaLembar.Toko.ISI, isi);
			rak.add(row);
		}
		tag.put(KotaLembar.Toko.RAK, rak);
		if (rinci != null) {
			tag.put(KotaLembar.RINCI, rinci);
		}
		tag.put(KotaLembar.Toko.PEGAWAI, pegawai(server, player, toko, t, situs));
		tag.put(KotaLembar.Toko.LAPORAN, laporan(server, t));
		return tag;
	}

	private static int[] pilihRak(ServerPlayer player) {
		String s = LembarKotaDasar.pilih(player, T + ".rak");
		int colon = s.indexOf(':');
		if (colon <= 0) {
			return new int[] {0, 0};
		}
		try {
			return new int[] {Integer.parseInt(s.substring(0, colon)), Integer.parseInt(s.substring(colon + 1))};
		} catch (NumberFormatException e) {
			return new int[] {0, 0};
		}
	}

	private static CompoundTag rakSlot(MinecraftServer server, long situs, ItemStack stack, int slot, int harga, ServerPlayer player) {
		CompoundTag s = new CompoundTag();
		s.putInt(LembarKunci.SLOT, slot);
		LembarKunci.barang(s, stack, server.registryAccess());
		s.putInt(LembarKunci.JUMLAH, stack.getCount());
		long nilai = Math.max(1L, EkonomiApi.harga(stack.copyWithCount(1)));
		long pasar = TokoPemain.hargaKota(server, situs, stack);
		long minta = harga > 0 ? harga : pasar;
		s.putLong(LembarKunci.HARGA, minta);
		s.putLong(LembarKunci.NILAI, nilai);
		s.putFloat(KotaLembar.Toko.FAKTOR, (float) HargaKota.faktor(server, situs, Kategori.dari(stack)));
		s.putFloat(KotaLembar.Toko.H, (float) (minta / (double) pasar));
		s.putInt(KotaLembar.Toko.LAKU, TokoPemain.peluang(server, situs, stack, (int) Math.min(Integer.MAX_VALUE, minta)));
		return s;
	}

	private static ListTag pegawai(MinecraftServer server, ServerPlayer player, TokoPemain.Toko toko, @Nullable CompoundTag t, long situs) {
		ListTag list = new ListTag();
		long gaji = TokoPemain.gaji(server, toko);
		if (t != null && t.contains("pgm")) {
			CompoundTag row = new CompoundTag();
			row.putString(KotaLembar.SOUL, t.getStringOr("pgm", ""));
			row.putBoolean(KotaLembar.Toko.MURID, true);
			LembarKunci.teks(row, LembarKunci.NAMA, Component.literal(t.getStringOr("pgn", "")));
			row.putString(KotaLembar.WATAK, Tokoh.WATAK.get(Math.floorMod(t.getIntOr("wt", 0), Tokoh.WATAK.size())));
			row.putLong(KotaLembar.Toko.GAJI, 0L);
			row.putBoolean(KotaLembar.Toko.DIPEKERJAKAN, true);
			list.add(row);
		} else if (t != null && t.getIntOr("pg", 0) != 0) {
			Warga w = WargaApi.byId(t.getIntOr("pg", 0));
			if (w != null) {
				CompoundTag row = LembarKotaDasar.warga(w, player, true);
				row.putLong(KotaLembar.Toko.GAJI, gaji);
				row.putBoolean(KotaLembar.Toko.DIPEKERJAKAN, true);
				list.add(row);
			}
		}
		if (!list.isEmpty()) {
			return list;
		}
		for (Warga w : WargaApi.wargaTempat(situs)) {
			if (list.size() >= 16) {
				break;
			}
			if (w.status != Warga.Status.HIDUP || WargaApi.umur(w) < 16 || Pekerjaan.bekerja(w) || w.peranNpc() != NpcRole.WARGA) {
				continue;
			}
			CompoundTag row = LembarKotaDasar.warga(w, player, true);
			row.putLong(KotaLembar.Toko.GAJI, gaji);
			row.putBoolean(KotaLembar.Toko.DIPEKERJAKAN, false);
			list.add(row);
		}
		return list;
	}

	private static CompoundTag laporan(MinecraftServer server, @Nullable CompoundTag t) {
		CompoundTag lap = new CompoundTag();
		if (t == null) {
			return lap;
		}
		ListTag hari = new ListTag();
		for (Tag each : t.getListOrEmpty("lap")) {
			if (each instanceof CompoundTag c) {
				CompoundTag h = new CompoundTag();
				h.putLong(LembarKunci.HARI, c.getLongOr("h", 0L));
				h.putInt(KotaLembar.Toko.PEMBELI, c.getIntOr("pb", 0));
				h.putInt(KotaLembar.Toko.TERJUAL, c.getIntOr("tj", 0));
				h.putLong(LembarKunci.NB, c.getLongOr("nb", 0L));
				hari.add(h);
			}
		}
		lap.put(LembarKunci.HARI, hari);
		List<Object[]> top = new ArrayList<>();
		CompoundTag topTag = t.getCompoundOrEmpty("top");
		for (String key : topTag.keySet()) {
			int[] row = topTag.getIntArray(key).orElse(new int[2]);
			if (row.length >= 2) {
				top.add(new Object[] {key, row[0], row[1]});
			}
		}
		top.sort((a, b) -> Integer.compare((Integer) b[2], (Integer) a[2]));
		ListTag barang = new ListTag();
		for (Object[] row : top) {
			if (barang.size() >= 8) {
				break;
			}
			Identifier id = Identifier.tryParse((String) row[0]);
			if (id == null) {
				continue;
			}
			CompoundTag b = new CompoundTag();
			LembarKunci.barang(b, new ItemStack(BuiltInRegistries.ITEM.getValue(id)), server.registryAccess());
			b.putInt(KotaLembar.Toko.TERJUAL, (Integer) row[1]);
			b.putLong(LembarKunci.NB, (Integer) row[2]);
			barang.add(b);
		}
		lap.put(LembarKunci.BARANG, barang);
		lap.putInt(KotaLembar.Toko.KELUHAN, t.getIntOr("keluhan", 0) + t.getIntOr("keluhan_baru", 0));
		lap.putInt(KotaLembar.Toko.PENCURIAN, t.getIntOr("cur", 0));
		return lap;
	}

	// ================================================================== stalls

	private static @Nullable CompoundTag bangunLapak(ServerPlayer player, int npcId, long situs) {
		if (npcId >= 0 && !KotaLayar.melayani(player, npcId, S)) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, null, LembarKunci.GAYA_KAYU);
		if (situs == 0L) {
			return LembarKotaDasar.kunci(tag, Component.translatable(KotaLayar.K + "buku.tanpa_kota"));
		}
		LembarKotaDasar.kepala(tag, player, situs);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(KotaLayar.K + "lapak.judul", KotaNames.town(situs)));
		long harga = LapakKota.harga(Isian.pita(player));
		long hari = KotaHidup.hari(server);
		ListTag daftar = new ListTag();
		for (LapakKota.Lapak l : LapakKota.daftar(server, situs)) {
			if (daftar.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, l.situs() + "_" + l.no());
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable(KotaLayar.K + "lapak.nama", l.no(),
					Component.translatable(KotaLayar.K + "distrik." + l.distrik())));
			row.putString(KotaLembar.LapakSewa.DISTRIK, l.distrik());
			row.putLong(LembarKunci.HARGA, harga);
			boolean anda = player.getUUID().equals(l.penyewa());
			row.putBoolean(LembarKunci.ANDA, anda);
			if (l.penyewa() != null && l.sampai() >= hari) {
				row.putString(LembarKunci.KEADAAN, anda ? LembarKunci.K_OK : LembarKunci.K_HABIS);
				ServerPlayer p = server.getPlayerList().getPlayer(l.penyewa());
				LembarKunci.teks(row, KotaLembar.LapakSewa.PENYEWA, anda ? Component.translatable(KotaLayar.K + "lapak.anda")
						: p != null ? p.getName() : Component.translatable(KotaLayar.K + "lapak.penyewa_jauh"));
				row.putLong(KotaLembar.SAMPAI, l.sampai());
			} else {
				long uang = tag.getLongOr(LembarKunci.UANG, 0L);
				row.putString(LembarKunci.KEADAAN, uang < harga ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK);
			}
			row.putInt(LembarKunci.X, l.pos().getX());
			row.putInt(LembarKunci.Z, l.pos().getZ());
			daftar.add(row);
		}
		tag.put(LembarKunci.DAFTAR, daftar);
		if (daftar.isEmpty()) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable(KotaLayar.K + "lapak.kosong"));
			tag.put(LembarKunci.KOSONG, kosong);
		}
		return tag;
	}

	private LembarToko() {
	}
}
