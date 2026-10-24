package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.harta.SpiritStones;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Screen {@code tema_kapal} of the harbour (DESIGN 38.T.3.4, 38.T.10.2, sheet keys {@link TemaLayar.Kapal}) and the
 * service list the harbour NPCs show in their talk ({@code CeritaApi.daftarJasa}). One screen, five counters:
 * <ul>
 * <li><b>Ship's cargo</b> ({@link Jenis#KAPAL}): the NAKHODA_LAUT on the pier of a moored junk sells the 4-6 goods of
 * that cycle ({@link #muatanKapal}); goods of the cycle's origin ({@code selatan} Negeri Rempah Selatan, {@code mutiara}
 * Kepulauan Mutiara, {@code barat} Negeri Barat, {@code kota} another harbour carrying inland goods) at their base price,
 * 10 % off for the first 2 days after arrival (38.T.10.1); stock per ship and cycle, and at most
 * {@link #HARIAN_DAGANG} units of one trade good per player per world day.</li>
 * <li><b>Kapal Lintas Laut</b> ({@link Jenis#LINTAS}, Kapten Gao Fan; tab {@code lintas}): the ticket of
 * {@link LintasLaut}.</li>
 * <li><b>Morning Fish Market</b> ({@link Jenis#IKAN}, Bibi Yu and the fish sellers; open 05:00-09:00 only): Ikan Roh 6,
 * Sup Ikan Pagi 3, Ikan Asin Roh at its harbour price.</li>
 * <li><b>Pasar Seberang</b> ({@link Jenis#SEBERANG}, the foreign kiosk): spices, celadon, sea pearls, the Kompas Pasang
 * (150, band 2+, one per player) and the Mutiara Penunjuk Laut (300, band 3+, one in the town every 3 days); the title
 * "Penjaga Pelita" takes 10 % off here.</li>
 * <li><b>Fuel desk</b> ({@link Jenis#BAHAN_BAKAR}, Menara Tambat Langit): spirit stones at their own value, no
 * discount (a flying ship's fuel).</li>
 * <li><b>Boat hire</b> ({@link Jenis#PERAHU}): {@link PerahuSewa}.</li>
 * </ul>
 * The schedule list {@code jadwal} (three moorings) and the storm flag go on every sheet of the screen.
 */
public final class TokoPelabuhan {

	/** NPC memory: the mooring a ship's captain serves. */
	public static final String KAPAL_NPC = "pelabuhan_kapal";
	/** NPC memory: the harbour town id. */
	public static final String SITUS_NPC = "pelabuhan_situs";
	/** NPC memory: the counter ({@code ikan}, {@code toko_seberang}, {@code bahan_bakar}, {@code perahu_sewa}, ...). */
	public static final String LAYANAN_NPC = "pelabuhan_layanan";

	public static final int HARIAN_DAGANG = 16;
	public static final long HARGA_KOMPAS = 150L;
	public static final long HARGA_MUTIARA_PENUNJUK = 300L;
	public static final long HARGA_IKAN_ROH = 6L;
	public static final long HARGA_SUP = 3L;

	/** Which counter a sheet shows. */
	public enum Jenis {
		KAPAL, LINTAS, IKAN, SEBERANG, BAHAN_BAKAR, PERAHU
	}

	/** One row for sale: {@code stok} -1 = unlimited, {@code harian} -1 = no daily cap. */
	record Jual(String id, Supplier<ItemStack> barang, long harga, int stok, int harian, @Nullable Component alasan) {
	}

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaLayar.daftar(TemaLayar.Kapal.LAYAR, TokoPelabuhan::bangun, TokoPelabuhan::aksi);
		CeritaApi.daftarJasa(TokoPelabuhan::jasa);
	}

	// ================================================================== who serves what

	/** The counter of an NPC, or null when it serves none. */
	static @Nullable Jenis jenis(@Nullable KultivatorNpc npc) {
		if (npc == null) {
			return null;
		}
		if (npc.memory().contains(KAPAL_NPC)) {
			return Jenis.KAPAL;
		}
		if (PelabuhanTokoh.adalah(npc, PelabuhanTokoh.GAO_FAN)) {
			return Jenis.LINTAS;
		}
		if (PelabuhanTokoh.adalah(npc, PelabuhanTokoh.BIBI_YU)) {
			return Jenis.IKAN;
		}
		return switch (npc.memory().getStringOr(LAYANAN_NPC, "")) {
			case "ikan" -> Jenis.IKAN;
			case "toko_seberang" -> Jenis.SEBERANG;
			case "bahan_bakar" -> Jenis.BAHAN_BAKAR;
			case "perahu_sewa" -> Jenis.PERAHU;
			default -> npc.role() == NpcRole.PENJUAL_IKAN && npc.memory().contains(SITUS_NPC) ? Jenis.IKAN : null;
		};
	}

	/** The harbour town of a speaker (its tag, else where the player stands). */
	static long situs(ServerPlayer player, @Nullable KultivatorNpc npc) {
		if (npc != null && npc.memory().contains(SITUS_NPC)) {
			return npc.memory().getLongOr(SITUS_NPC, 0L);
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.di(player);
		return kota == null ? 0L : kota.situs;
	}

	/** The "Jasa" tab rows of the talk sheet. */
	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		Jenis j = jenis(npc);
		if (j != null) {
			String key = "kultivator.kota_tema.pelabuhan.jasa." + j.name().toLowerCase(java.util.Locale.ROOT);
			Component alasan = j == Jenis.IKAN && !pasarIkanBuka(player) ? Component.translatable("kultivator.kota_tema.pelabuhan.ikan.tutup") : null;
			out.add(new TempatDialog.JasaEntri(TemaLayar.Kapal.LAYAR, Component.translatable(key),
					j == Jenis.KAPAL || j == Jenis.LINTAS ? LembarKunci.Ikon.DERMAGA : LembarKunci.Ikon.TIMBANGAN,
					null, alasan != null, alasan));
		}
		if (PelabuhanTokoh.adalah(npc, PelabuhanTokoh.GONG_SHUMU)) {
			out.add(new TempatDialog.JasaEntri(TemaLayar.Galangan.LAYAR, Component.translatable("kultivator.kota_tema.pelabuhan.jasa.galangan"),
					LembarKunci.Ikon.DERMAGA, null, false, null));
		}
		return out;
	}

	/** Opens the cargo screen of a captain. */
	static void bukaKapal(ServerPlayer player, KultivatorNpc nakhoda) {
		TemaLayar.buka(player, TemaLayar.Kapal.LAYAR, nakhoda.getId());
	}

	/** Opens the Kapal Lintas Laut tab at Kapten Gao Fan. */
	static void bukaLintas(ServerPlayer player, KultivatorNpc gao) {
		TemaLayar.buka(player, TemaLayar.Kapal.LAYAR, gao.getId());
	}

	// ================================================================== sheet

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = PelabuhanLayar.npc(player, npcId);
		Jenis j = jenis(npc);
		if (j == null) {
			return null;
		}
		long situs = situs(player, npc);
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = PelabuhanLayar.kepala(player, "kultivator.kota_tema.pelabuhan.layar." + j.name().toLowerCase(java.util.Locale.ROOT),
				j == Jenis.IKAN ? LembarKunci.GAYA_KAIN : LembarKunci.GAYA_KAYU, npc);
		boolean badai = PelabuhanKota.badai(player.level());
		tag.putBoolean(TemaLayar.Kapal.BADAI, badai);
		jadwal(tag, server, situs);
		if (j == Jenis.LINTAS) {
			tag.putString(LembarKunci.TAB, TemaLayar.Kapal.TAB_LINTAS);
			tag.put(TemaLayar.Kapal.LINTAS, LintasLaut.lembar(player, situs));
			ListTag hide = new ListTag();
			hide.add(net.minecraft.nbt.StringTag.valueOf(TemaLayar.Kapal.TAB_MUATAN));
			tag.put(LembarKunci.TAB_SEMBUNYI, hide);
			return tag;
		}
		ListTag hide = new ListTag();
		hide.add(net.minecraft.nbt.StringTag.valueOf(TemaLayar.Kapal.TAB_LINTAS));
		tag.put(LembarKunci.TAB_SEMBUNYI, hide);
		tag.putString(LembarKunci.TAB, TemaLayar.Kapal.TAB_MUATAN);
		List<Jual> rows;
		if (j == Jenis.KAPAL) {
			int n = npc.memory().getIntOr(KAPAL_NPC, 0);
			AKTIF_KAPAL.put(player.getUUID(), n);
			long c = JadwalKapal.siklus(JadwalKapal.waktu(server), n);
			String asal = JadwalKapal.asal(situs, n, c);
			CompoundTag kapal = new CompoundTag();
			LembarKunci.teks(kapal, LembarKunci.NAMA, Component.translatable("kultivator.kota_tema.pelabuhan.kapal.nama." + namaKapal(situs, n, c)));
			LembarKunci.teks(kapal, TemaLayar.Kapal.ASAL, Component.translatable("kultivator.kota_tema.pelabuhan.asal." + asal));
			kapal.putInt(TemaLayar.Kapal.TAMBATAN, n);
			kapal.putString(LembarKunci.ENTITAS, "kultivator:kapal_dagang");
			kapal.putLong(TemaLayar.Kapal.BERANGKAT_TICK, JadwalKapal.tickBerangkat(server, n));
			tag.put(TemaLayar.Kapal.KAPAL, kapal);
			rows = JadwalKapal.tertambat(server, situs, n) ? muatanKapal(player, situs, n, c) : List.of();
			if (rows.isEmpty()) {
				kosong(tag, "kultivator.kota_tema.pelabuhan.kapal.belum");
			}
		} else {
			rows = kios(player, situs, j);
			if (j == Jenis.SEBERANG && seberangTutup(player, situs)) {
				PelabuhanLayar.kunci(tag, Component.translatable("kultivator.kota_tema.pelabuhan.seberang.tutup"), null);
			}
			if (j == Jenis.IKAN && !pasarIkanBuka(player)) {
				PelabuhanLayar.kunci(tag, Component.translatable("kultivator.kota_tema.pelabuhan.ikan.tutup"),
						Component.translatable("kultivator.kota_tema.pelabuhan.ikan.jam"));
			}
		}
		ListTag list = new ListTag();
		for (Jual row : rows) {
			list.add(baris(player, situs, j, row));
		}
		tag.put(TemaLayar.Kapal.MUATAN, list);
		return tag;
	}

	private static void kosong(CompoundTag tag, String key) {
		CompoundTag k = new CompoundTag();
		LembarKunci.teks(k, LembarKunci.TEKS, Component.translatable(key));
		tag.put(LembarKunci.KOSONG, k);
	}

	private static CompoundTag baris(ServerPlayer player, long situs, Jenis j, Jual row) {
		CompoundTag e = new CompoundTag();
		e.putString(LembarKunci.ID, row.id());
		ItemStack shown = row.barang().get();
		LembarKunci.barang(e, shown, player.level().registryAccess());
		long price = harga(player, j, row.harga());
		e.putLong(LembarKunci.HARGA, price);
		int left = sisa(player, situs, j, row);
		e.putInt(LembarKunci.STOK, left);
		String state;
		if (row.alasan() != null) {
			state = LembarKunci.K_TERKUNCI;
			LembarKunci.teks(e, LembarKunci.ALASAN, row.alasan());
		} else if (left == 0) {
			state = row.harian() >= 0 && dibeliHariIni(player, row.id()) >= row.harian() ? LembarKunci.K_BATAS : LembarKunci.K_HABIS;
		} else if (NilaiBatu.total(player) < price) {
			state = LembarKunci.K_KURANG_UANG;
		} else {
			state = LembarKunci.K_OK;
		}
		e.putString(LembarKunci.KEADAAN, state);
		return e;
	}

	/** The schedule rows of the three moorings. */
	private static void jadwal(CompoundTag tag, MinecraftServer server, long situs) {
		ListTag list = new ListTag();
		long t = JadwalKapal.waktu(server);
		for (int n = 0; n < JadwalKapal.DAGANG; n++) {
			CompoundTag e = new CompoundTag();
			e.putInt(TemaLayar.Kapal.TAMBATAN, n);
			String state = JadwalKapal.keadaanData(server, situs, n);
			long c = JadwalKapal.siklus(t, n);
			if (state.equals("kosong")) {
				c++;
			}
			LembarKunci.teks(e, TemaLayar.Kapal.ASAL, Component.translatable("kultivator.kota_tema.pelabuhan.asal." + JadwalKapal.asal(situs, n, c)));
			e.putInt(TemaLayar.Kapal.DATANG_HARI, (int) JadwalKapal.hariDatang(server, situs, n));
			e.putString(LembarKunci.KEADAAN, state);
			list.add(e);
		}
		tag.put(TemaLayar.Kapal.JADWAL, list);
	}

	// ================================================================== goods

	/** The cargo of one ship cycle: 4-6 goods of its origin (deterministic per town, mooring and cycle). */
	static List<Jual> muatanKapal(ServerPlayer player, long situs, int n, long c) {
		String asal = JadwalKapal.asal(situs, n, c);
		List<String> pool = switch (asal) {
			case "mutiara" -> List.of("mutiara_laut", "ikan_asin_roh", "kompas_pasang", "rempah_seberang", "ikan_roh", "porselen_hijau");
			case "barat" -> List.of("kaca_warna", "giok_barat", "permadani_gurun", "rempah_seberang", "kurma_emas", "porselen_hijau");
			case "kota" -> List.of("sutra_awan", "teh_bi_luo", "arak_beras", "dupa_gunung", "herba_tebing_kering", "teh_awan_gunung");
			default -> List.of("rempah_seberang", "porselen_hijau", "ikan_asin_roh", "kaca_warna", "mutiara_laut", "kompas_pasang");
		};
		long h = PelabuhanKota.hash(situs, c * 4L + n, 380403L);
		int count = 4 + (int) Math.floorMod(h, 3L);
		List<Jual> out = new ArrayList<>();
		long arrival = JadwalKapal.awalSiklus(c, n) + JadwalKapal.tunda(situs, n, c);
		boolean fresh = JadwalKapal.waktu(player.level().getServer()) - arrival < 48000L;
		for (int i = 0; i < count; i++) {
			String id = pool.get((int) Math.floorMod(h + i * 7L, (long) pool.size()));
			if (contains(out, id)) {
				id = pool.get((int) Math.floorMod(h + i * 7L + 1L, (long) pool.size()));
				if (contains(out, id)) {
					continue;
				}
			}
			int stok = 6 + (int) Math.floorMod(PelabuhanKota.hash(h, i, 380404L), 11L);
			Jual row = barisBarang(player, id, stok, fresh);
			if (row != null) {
				out.add(row);
			}
		}
		return out;
	}

	private static boolean contains(List<Jual> rows, String id) {
		for (Jual r : rows) {
			if (r.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** One good by id: a trade good of {@link DagangItems} or one of the harbour's own items. */
	private static @Nullable Jual barisBarang(ServerPlayer player, String id, int stok, boolean fresh) {
		if (id.equals("kompas_pasang")) {
			return kompas(player);
		}
		if (id.equals("ikan_roh")) {
			return new Jual(id, () -> new ItemStack(TemaItems.IKAN_ROH), HARGA_IKAN_ROH, stok, 12, null);
		}
		DagangItems.Barang b = DagangItems.barang(id);
		Item item = DagangItems.item(id);
		if (b == null || item == null || b.selundupan()) {
			return null;
		}
		long price = fresh ? (long) Math.ceil(b.dasar() * 0.9D) : b.dasar();
		return new Jual(id, () -> new ItemStack(item), price, stok, HARIAN_DAGANG, null);
	}

	private static Jual kompas(ServerPlayer player) {
		Component why = null;
		if (Barang.pita(player) < 2) {
			why = Component.translatable("kultivator.kota_tema.pelabuhan.pita", 2);
		} else if (PelabuhanKota.pemain(player.level().getServer(), player.getUUID()).getBooleanOr("kompas", false)) {
			why = Component.translatable("kultivator.kota_tema.pelabuhan.kompas.sudah");
		}
		return new Jual("kompas_pasang", () -> new ItemStack(TemaItems.KOMPAS_PASANG), HARGA_KOMPAS, 1, -1, why);
	}

	/** The rows of a kiosk. */
	static List<Jual> kios(ServerPlayer player, long situs, Jenis j) {
		List<Jual> out = new ArrayList<>();
		MinecraftServer server = player.level().getServer();
		switch (j) {
			case IKAN -> {
				out.add(new Jual("ikan_roh", () -> new ItemStack(TemaItems.IKAN_ROH), HARGA_IKAN_ROH, -1, 12, null));
				out.add(new Jual("sup_ikan_pagi", () -> new ItemStack(TemaItems.SUP_IKAN_PAGI), HARGA_SUP, -1, 4, null));
				Jual asin = barisBarang(player, "ikan_asin_roh", -1, false);
				if (asin != null) {
					out.add(asin);
				}
			}
			case SEBERANG -> {
				for (String id : List.of("rempah_seberang", "porselen_hijau", "mutiara_laut")) {
					Jual row = barisBarang(player, id, -1, false);
					if (row != null) {
						out.add(row);
					}
				}
				out.add(kompas(player));
				Component why = null;
				if (Barang.pita(player) < 3) {
					why = Component.translatable("kultivator.kota_tema.pelabuhan.pita", 3);
				}
				long last = PelabuhanKota.kota(server, situs).getLongOr("mutiara_penunjuk", Long.MIN_VALUE / 2);
				int stok = PelabuhanKota.hari(player.level()) - last >= 3L ? 1 : 0;
				out.add(new Jual("mutiara_penunjuk_laut", () -> Dimensi.itemById("kultivator:mutiara_penunjuk_laut", 1), HARGA_MUTIARA_PENUNJUK,
						stok, -1, why));
			}
			case BAHAN_BAKAR -> {
				for (int grade = 0; grade < 3; grade++) {
					int g = grade;
					int count = g == 0 ? 16 : g == 1 ? 4 : 1;
					out.add(new Jual("batu_" + g, () -> SpiritStones.stack(g, count), NilaiBatu.VALUE[g] * count, -1, -1, null));
				}
			}
			case PERAHU -> out.addAll(PerahuSewa.baris(player, situs));
			default -> {
			}
		}
		return out;
	}

	/** True on the day after a lost pirate raid (the Pasar Seberang stays shut). */
	static boolean seberangTutup(ServerPlayer player, long situs) {
		return PelabuhanKota.kota(player.level().getServer(), situs).getLongOr("seberang_tutup", Long.MIN_VALUE) >= PelabuhanKota.hari(player.level()) - 1L
				&& PelabuhanKota.kota(player.level().getServer(), situs).getLongOr("seberang_tutup", Long.MIN_VALUE) != Long.MIN_VALUE;
	}

	/** True between 05:00 and 09:00 (the Morning Fish Market). */
	static boolean pasarIkanBuka(ServerPlayer player) {
		int h = PelabuhanKota.jamHari(player.level());
		return h >= 5 && h < 9;
	}

	private static long harga(ServerPlayer player, Jenis j, long base) {
		if (j == Jenis.SEBERANG && CeritaApi.bendera(player, "gelar.penjaga_pelita")) {
			return Math.max(1L, Math.round(base * 0.9D));
		}
		return base;
	}

	// ================================================================== stock

	private static CompoundTag hariIni(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag p = PelabuhanKota.pemain(server, player.getUUID());
		String key = "beli_" + PelabuhanKota.hari(player.level());
		if (!p.contains(key)) {
			for (String old : new ArrayList<>(p.keySet())) {
				if (old.startsWith("beli_")) {
					p.remove(old);
				}
			}
		}
		return PelabuhanKota.anak(p, key);
	}

	private static int dibeliHariIni(ServerPlayer player, String id) {
		return hariIni(player).getIntOr(id, 0);
	}

	private static CompoundTag terjual(MinecraftServer server, long situs, int n, long c) {
		CompoundTag kota = PelabuhanKota.kota(server, situs);
		CompoundTag all = PelabuhanKota.anak(kota, "muatan");
		String key = n + "_" + c;
		if (!all.contains(key)) {
			for (String old : new ArrayList<>(all.keySet())) {
				if (old.startsWith(n + "_")) {
					all.remove(old);
				}
			}
		}
		return PelabuhanKota.anak(all, key);
	}

	/** Units the player may still buy of a row (-1 unlimited). */
	private static int sisa(ServerPlayer player, long situs, Jenis j, Jual row) {
		int left = row.stok();
		if (j == Jenis.KAPAL && row.stok() >= 0) {
			left = Math.max(0, row.stok() - shipSold(player, situs, row.id()));
		}
		if (row.harian() >= 0) {
			int day = Math.max(0, row.harian() - dibeliHariIni(player, row.id()));
			left = left < 0 ? day : Math.min(left, day);
		}
		return left;
	}

	/** Units of a good the ship the player talks to has sold in its cycle (all players). */
	private static int shipSold(ServerPlayer player, long situs, String id) {
		int n = AKTIF_KAPAL.getOrDefault(player.getUUID(), 0);
		long c = JadwalKapal.siklus(JadwalKapal.waktu(player.level().getServer()), n);
		return terjual(player.level().getServer(), situs, n, c).getIntOr(id, 0);
	}

	/** The mooring of the captain each player last opened (for the stock lookups of one build). */
	private static final java.util.Map<java.util.UUID, Integer> AKTIF_KAPAL = new java.util.HashMap<>();

	// ================================================================== actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = PelabuhanLayar.npc(player, npcId);
		Jenis j = jenis(npc);
		if (j == null) {
			return;
		}
		long situs = situs(player, npc);
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		if (j == Jenis.KAPAL) {
			AKTIF_KAPAL.put(player.getUUID(), npc.memory().getIntOr(KAPAL_NPC, 0));
		}
		if (TemaLayar.Kapal.OP_LINTAS.equals(verb) && j == Jenis.LINTAS) {
			Component result = LintasLaut.pesan(player, situs, LembarKunci.argInt(parts, 1, 0));
			PelabuhanLayar.pesan(player, result, LintasLaut.punyaTiket(player, situs));
			return;
		}
		if (!TemaLayar.Kapal.OP_BELI.equals(verb)) {
			return;
		}
		String id = LembarKunci.arg(parts, 1);
		int want = Math.max(1, Math.min(64, LembarKunci.argInt(parts, 2, 1)));
		if (j == Jenis.PERAHU) {
			Component result = PerahuSewa.sewa(player, situs, id, npc);
			PelabuhanLayar.pesan(player, result, PerahuSewa.berhasil(result));
			return;
		}
		if (j == Jenis.IKAN && !pasarIkanBuka(player)) {
			PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.ikan.tutup"), false);
			return;
		}
		if (j == Jenis.SEBERANG && seberangTutup(player, situs)) {
			PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.seberang.tutup"), false);
			return;
		}
		List<Jual> rows;
		long c = 0L;
		int n = 0;
		if (j == Jenis.KAPAL) {
			n = npc.memory().getIntOr(KAPAL_NPC, 0);
			c = JadwalKapal.siklus(JadwalKapal.waktu(player.level().getServer()), n);
			if (!JadwalKapal.tertambat(player.level().getServer(), situs, n)) {
				PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.kapal.belum"), false);
				return;
			}
			rows = muatanKapal(player, situs, n, c);
		} else {
			rows = kios(player, situs, j);
		}
		Jual row = null;
		for (Jual r : rows) {
			if (r.id().equals(id)) {
				row = r;
			}
		}
		if (row == null) {
			return;
		}
		if (row.alasan() != null) {
			PelabuhanLayar.pesan(player, row.alasan(), false);
			return;
		}
		int left = sisa(player, situs, j, row);
		int n2 = left < 0 ? want : Math.min(want, left);
		if (n2 <= 0) {
			PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.habis"), false);
			return;
		}
		long unit = harga(player, j, row.harga());
		if (!EkonomiApi.bayar(player, unit * n2, "pelabuhan." + id)) {
			PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.kurang_uang"), false);
			return;
		}
		MinecraftServer server = player.level().getServer();
		for (int i = 0; i < n2; i++) {
			PelabuhanKota.beri(player, row.barang().get());
		}
		CompoundTag day = hariIni(player);
		day.putInt(row.id(), day.getIntOr(row.id(), 0) + n2);
		if (j == Jenis.KAPAL) {
			CompoundTag sold = terjual(server, situs, n, c);
			sold.putInt(row.id(), sold.getIntOr(row.id(), 0) + n2);
		}
		if (row.id().equals("kompas_pasang")) {
			PelabuhanKota.pemain(server, player.getUUID()).putBoolean("kompas", true);
		}
		if (row.id().equals("mutiara_penunjuk_laut")) {
			PelabuhanKota.kota(server, situs).putLong("mutiara_penunjuk", PelabuhanKota.hari(player.level()));
		}
		PelabuhanKota.ubah(server);
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6F, 1.1F);
		CeritaApi.peristiwa(player, "beli", BuiltInRegistries.ITEM.getKey(row.barang().get().getItem()).toString());
		PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.dibeli", n2, row.barang().get().getHoverName(),
				NilaiBatu.text(unit * n2)), true);
	}

	/** A pleasant, stable ship name per cycle (lang {@code kultivator.kota_tema.pelabuhan.kapal.nama.<i>}, 12 names). */
	static int namaKapal(long situs, int n, long c) {
		return (int) Math.floorMod(PelabuhanKota.hash(situs, c * 4L + n, 380405L), 12L);
	}

	static ItemStack ikonPerahu() {
		return new ItemStack(Items.OAK_BOAT);
	}

	private TokoPelabuhan() {
	}
}
