package dev.kultivator.dunia.tempat.ui;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Acara;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Bank;
import dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Dermaga;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Dialog;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Gilda;
import dev.kultivator.dunia.tempat.ui.LembarKunci.GildaMisi;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Ikon;
import dev.kultivator.dunia.tempat.ui.LembarKunci.JudiBatu;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Jurnal;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Kafilah;
import dev.kultivator.dunia.tempat.ui.LembarKunci.LapakRekrut;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Layanan;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Lelang;
import dev.kultivator.dunia.tempat.ui.LembarKunci.PasarBinatang;
import dev.kultivator.dunia.tempat.ui.LembarKunci.PetaKota;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Tagihan;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Teleport;
import dev.kultivator.dunia.tempat.ui.LembarKunci.TokoSenjata;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Yamen;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Test sheets of the 38UI kit (DESIGN 38.U.2: "tidak ada teks di bawah 0.6, tidak ada tumpang tindih (diuji uji ui skala
 * 2|3|4)"): {@code /kultivasi uji ui layar <id> [isi|kosong|kunci|takut|donasi]} opens screen {@code id} with a full sample
 * sheet (named characters, real goods, every card state), its empty state or its locked state;
 * {@code /kultivasi uji ui skala <2|3|4> [id]} opens it laid out as GUI scale N would lay it out on this window. The sheets
 * are opened like a hub screen ({@code npcId -1}, no anchor), so the server drops every write op: the kit can be checked
 * without touching any game state. Op only. Texts are lang keys {@code kultivator.tempat_ui.uji.*}.
 */
public final class UjiUi {

	private static final String K = "kultivator.tempat_ui.uji.";
	private static final List<String> KEADAAN = List.of("isi", "kosong", "kunci", "takut", "donasi", "panjang", "istana");
	private static final SuggestionProvider<CommandSourceStack> LAYAR = (ctx, b) -> SharedSuggestionProvider.suggest(LembarKunci.SEMUA_LAYAR, b);
	private static final SuggestionProvider<CommandSourceStack> KEADAAN_SARAN = (ctx, b) -> SharedSuggestionProvider.suggest(KEADAAN, b);

	public static void init() {
		DuniaCommands.test("ui", UjiUi::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("ui")
				.then(Commands.literal("layar").then(Commands.argument("id", StringArgumentType.word()).suggests(LAYAR)
						.executes(ctx -> buka(ctx, "isi", 0))
						.then(Commands.argument("keadaan", StringArgumentType.word()).suggests(KEADAAN_SARAN)
								.executes(ctx -> buka(ctx, StringArgumentType.getString(ctx, "keadaan"), 0)))))
				.then(Commands.literal("skala").then(Commands.argument("n", IntegerArgumentType.integer(1, 6))
						.executes(ctx -> skala(ctx, TokoSenjata.LAYAR))
						.then(Commands.argument("id", StringArgumentType.word()).suggests(LAYAR)
								.executes(ctx -> skala(ctx, StringArgumentType.getString(ctx, "id"))))));
	}

	private static int buka(CommandContext<CommandSourceStack> ctx, String keadaan, int skala) throws CommandSyntaxException {
		return bukaLayar(ctx.getSource(), ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"), keadaan, skala);
	}

	private static int skala(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
		return bukaLayar(ctx.getSource(), ctx.getSource().getPlayerOrException(), id, "isi", IntegerArgumentType.getInteger(ctx, "n"));
	}

	private static int bukaLayar(CommandSourceStack src, ServerPlayer p, String id, String keadaan, int skala) {
		if (!LembarKunci.SEMUA_LAYAR.contains(id)) {
			src.sendFailure(Component.translatable(K + "tak_dikenal", id));
			return 0;
		}
		CompoundTag lembar = sampel(id, keadaan, p.registryAccess());
		lembar.putBoolean("uji", true);
		if (skala > 0) {
			lembar.putInt("uji_skala", skala);
		}
		TempatLayar.buka(p, id, -1, lembar);
		src.sendSuccess(() -> Component.translatable(K + "dibuka", id, keadaan, skala), false);
		return 1;
	}

	// =================================================================== helpers

	private static Component t(String k, Object... a) {
		return Component.translatable(K + k, a);
	}

	private static void teks(CompoundTag tag, String kunci, String k, Object... a) {
		LembarKunci.teks(tag, kunci, t(k, a));
	}

	private static CompoundTag entri(String id, String namaKunci) {
		CompoundTag e = new CompoundTag();
		e.putString(LembarKunci.ID, id);
		teks(e, LembarKunci.NAMA, namaKunci);
		return e;
	}

	private static void barang(CompoundTag e, Item item, HolderLookup.Provider r) {
		LembarKunci.barang(e, new ItemStack(item), r);
	}

	private static void npc(CompoundTag sheet, String nama, String gelar, int ikon) {
		CompoundTag n = new CompoundTag();
		teks(n, LembarKunci.NAMA, nama);
		teks(n, LembarKunci.GELAR, gelar);
		n.putInt(LembarKunci.POTRET, -1);
		n.putInt(LembarKunci.IKON, ikon);
		sheet.put(LembarKunci.NPC, n);
	}

	private static void kepala(CompoundTag s, String judul, String gaya, String distrik) {
		teks(s, LembarKunci.JUDUL, judul);
		teks(s, LembarKunci.TEMPAT, "tempat.kota_awan");
		s.putString(LembarKunci.GAYA, gaya);
		s.putString(LembarKunci.DISTRIK, distrik);
		s.putLong(LembarKunci.UANG, 1234L);
		s.put(LembarKunci.BATU, new IntArrayTag(new int[] {12, 5, 3, 0}));
		s.putInt(LembarKunci.NAMA_BAIK, 34);
		s.putInt(LembarKunci.PANGKAT_NB, LembarKunci.PANGKAT_WARGA);
	}

	private static ListTag daftar(CompoundTag... entri) {
		ListTag list = new ListTag();
		for (CompoundTag e : entri) {
			list.add(e);
		}
		return list;
	}

	private static CompoundTag kosong(String k) {
		CompoundTag c = new CompoundTag();
		teks(c, LembarKunci.TEKS, k);
		return c;
	}

	// =================================================================== sheets

	static CompoundTag sampel(String id, String keadaan, HolderLookup.Provider r) {
		CompoundTag s = switch (id) {
			case Dialog.LAYAR -> "panjang".equals(keadaan) ? dialogPanjang() : dialog(r);
			case TokoSenjata.LAYAR -> toko(r);
			case Lelang.LAYAR -> lelang(r);
			case GildaMisi.LAYAR -> gildaMisi();
			case Bank.LAYAR -> bank(r);
			case BukuKas.LAYAR -> bukuKas(r);
			case Tagihan.LAYAR -> tagihan(keadaan, r);
			case PetaKota.LAYAR -> petaKota();
			case Teleport.LAYAR, Dermaga.LAYAR -> teleport(Dermaga.LAYAR.equals(id));
			case PasarBinatang.LAYAR -> pasarBinatang(r);
			case JudiBatu.LAYAR -> judiBatu(r);
			case Layanan.LAYAR_PENGINAPAN, Layanan.LAYAR_RUMAH_MAKAN, Layanan.LAYAR_JASA -> layanan(id, r);
			case Yamen.LAYAR -> yamen(r);
			case Acara.LAYAR -> "istana".equals(keadaan) ? acaraIstana() : acara();
			case LapakRekrut.LAYAR -> rekrut();
			case Jurnal.LAYAR -> jurnal();
			case Gilda.LAYAR_ALKEMIS, Gilda.LAYAR_TEMPA -> gilda(Gilda.LAYAR_TEMPA.equals(id), r);
			case Kafilah.LAYAR -> kafilah();
			default -> new CompoundTag();
		};
		if ("kosong".equals(keadaan)) {
			for (String k : List.copyOf(s.keySet())) {
				if (s.get(k) instanceof ListTag) {
					s.put(k, new ListTag());
				}
			}
			s.put(LembarKunci.KOSONG, kosong("kosong_contoh"));
		} else if ("kunci".equals(keadaan)) {
			CompoundTag k = new CompoundTag();
			teks(k, LembarKunci.ALASAN, "kunci_alasan");
			teks(k, LembarKunci.SYARAT, "kunci_syarat");
			s.put(LembarKunci.KUNCI_LAYAR, k);
		}
		return s;
	}

	private static CompoundTag dialog(HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "dialog.judul", LembarKunci.GAYA_KERTAS, LembarKunci.DISTRIK_HUNIAN);
		npc(s, "dialog.nama", "dialog.gelar", Ikon.PENGINAPAN);
		teks(s, LembarKunci.TEKS, "dialog.teks");
		CompoundTag p1 = new CompoundTag();
		p1.putInt(LembarKunci.NO, 1);
		teks(p1, LembarKunci.TEKS, "dialog.p1");
		CompoundTag p2 = new CompoundTag();
		p2.putInt(LembarKunci.NO, 2);
		teks(p2, LembarKunci.TEKS, "dialog.p2");
		CompoundTag p3 = new CompoundTag();
		p3.putInt(LembarKunci.NO, 3);
		teks(p3, LembarKunci.TEKS, "dialog.p3");
		CompoundTag p4 = new CompoundTag();
		p4.putInt(LembarKunci.NO, 4);
		teks(p4, LembarKunci.TEKS, "dialog.p4");
		p4.putBoolean(LembarKunci.TERKUNCI, true);
		teks(p4, LembarKunci.ALASAN, "dialog.p4_alasan");
		s.put(Dialog.PILIHAN, daftar(p1, p2, p3, p4));
		CompoundTag j1 = entri(Layanan.LAYAR_PENGINAPAN, "dialog.jasa_kamar");
		j1.putInt(LembarKunci.IKON, Ikon.PENGINAPAN);
		teks(j1, LembarKunci.CATATAN, "dialog.jasa_kamar_jam");
		CompoundTag j2 = entri(Layanan.LAYAR_RUMAH_MAKAN, "dialog.jasa_makan");
		j2.putInt(LembarKunci.IKON, Ikon.RUMAH_MAKAN);
		teks(j2, LembarKunci.CATATAN, "dialog.jasa_makan_jam");
		s.put(Dialog.JASA, daftar(j1, j2));
		CompoundTag q = entri("bibi_hong_1", "dialog.quest_nama");
		teks(q, LembarKunci.TEKS, "dialog.quest_teks");
		q.putBoolean(LembarKunci.BARU, true);
		s.put(Dialog.QUEST, daftar(q));
		return s;
	}

	/**
	 * "panjang": a long line and six choices (two locked), to check the bubble's smaller text, its pages and the two
	 * choice columns at GUI scale 4 ({@code uji ui skala 4 dialog} then {@code uji ui layar dialog panjang}).
	 */
	private static CompoundTag dialogPanjang() {
		CompoundTag s = new CompoundTag();
		kepala(s, "panjang.judul", LembarKunci.GAYA_KERTAS, LembarKunci.DISTRIK_CENDEKIA);
		npc(s, "panjang.nama", "panjang.gelar", Ikon.PERPUSTAKAAN);
		teks(s, LembarKunci.TEKS, "panjang.teks");
		ListTag pilihan = new ListTag();
		for (int i = 1; i <= 6; i++) {
			CompoundTag p = new CompoundTag();
			p.putInt(LembarKunci.NO, i);
			teks(p, LembarKunci.TEKS, "panjang.p" + i);
			if (i == 4 || i == 6) {
				p.putBoolean(LembarKunci.TERKUNCI, true);
				teks(p, LembarKunci.ALASAN, "panjang.p" + i + "_alasan");
			}
			pilihan.add(p);
		}
		s.put(Dialog.PILIHAN, pilihan);
		return s;
	}

	private static CompoundTag senjata(String id, String tab, Item item, long harga, int mutu, String keadaan, HolderLookup.Provider r) {
		CompoundTag e = entri(id, "toko." + id);
		e.putString(TokoSenjata.TAB_ENTRI, tab);
		barang(e, item, r);
		e.putLong(LembarKunci.HARGA, harga);
		e.putInt(LembarKunci.STOK, mutu >= 3 ? 1 : 4);
		e.putInt(LembarKunci.MUTU, mutu);
		e.putString(LembarKunci.KEADAAN, keadaan);
		e.putString(TokoSenjata.KELAS_DUEL, "PEDANG");
		ListTag stat = new ListTag();
		CompoundTag a = new CompoundTag();
		a.putString(TokoSenjata.STAT_KUNCI, TokoSenjata.STAT_SERANGAN);
		a.putFloat(LembarKunci.NILAI, 6.0F + mutu * 2.5F);
		a.putFloat(TokoSenjata.BANDING, mutu * 2.5F - 1.0F);
		stat.add(a);
		CompoundTag b = new CompoundTag();
		b.putString(TokoSenjata.STAT_KUNCI, TokoSenjata.STAT_KECEPATAN);
		b.putFloat(LembarKunci.NILAI, 1.6F);
		b.putFloat(TokoSenjata.BANDING, 0.0F);
		stat.add(b);
		CompoundTag c = new CompoundTag();
		c.putString(TokoSenjata.STAT_KUNCI, TokoSenjata.STAT_DAYA_TAHAN);
		c.putFloat(LembarKunci.NILAI, 600.0F + mutu * 300.0F);
		c.putFloat(TokoSenjata.BANDING, mutu * 300.0F - 150.0F);
		stat.add(c);
		e.put(TokoSenjata.STAT, stat);
		if (LembarKunci.K_TERKUNCI.equals(keadaan)) {
			teks(e, LembarKunci.ALASAN, "toko.butuh_inti_emas");
		}
		return e;
	}

	private static CompoundTag toko(HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "toko.judul", LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_PASAR);
		s.putFloat(LembarKunci.DISKON, 0.05F);
		CompoundTag langka = senjata("jian_mutu_2", TokoSenjata.TAB_JIAN, Items.DIAMOND_SWORD, 980L, 2, LembarKunci.K_OK, r);
		langka.putBoolean(LembarKunci.LANGKA, true);
		s.put(LembarKunci.DAFTAR, daftar(senjata("jian_besi", TokoSenjata.TAB_JIAN, Items.IRON_SWORD, 260L, 1, LembarKunci.K_OK, r), langka,
				senjata("jian_tinggi", TokoSenjata.TAB_JIAN, Items.DIAMOND_SWORD, 1020L, 3, LembarKunci.K_TERKUNCI, r),
				senjata("jian_pusaka", TokoSenjata.TAB_JIAN, Items.NETHERITE_SWORD, 4800L, 4, LembarKunci.K_KURANG_UANG, r),
				senjata("jian_habis", TokoSenjata.TAB_JIAN, Items.GOLDEN_SWORD, 180L, 0, LembarKunci.K_HABIS, r),
				senjata("kapak_besi", TokoSenjata.TAB_KAPAK, Items.IRON_AXE, 280L, 1, LembarKunci.K_OK, r),
				senjata("gada_besi", TokoSenjata.TAB_GADA, Items.MACE, 640L, 2, LembarKunci.K_OK, r),
				senjata("qiang_trisula", TokoSenjata.TAB_QIANG, Items.TRIDENT, 900L, 2, LembarKunci.K_OK, r),
				senjata("zirah_besi", TokoSenjata.TAB_ZIRAH, Items.IRON_CHESTPLATE, 420L, 1, LembarKunci.K_OK, r)));
		s.put(LembarKunci.TAB_SEMBUNYI, daftarString(TokoSenjata.TAB_DAO, TokoSenjata.TAB_KIPAS_BESI, TokoSenjata.TAB_KEBUT));
		CompoundTag tangan = new CompoundTag();
		teks(tangan, LembarKunci.NAMA, "toko.tangan");
		s.put(LembarKunci.TANGAN, tangan);
		CompoundTag layanan = new CompoundTag();
		layanan.putLong(TokoSenjata.PERBAIKI, 24L);
		layanan.putInt(TokoSenjata.NILAI_SISA, 2);
		s.put(TokoSenjata.LAYANAN, layanan);
		CompoundTag pesan = new CompoundTag();
		CompoundTag b1 = entri("jian", "toko.bentuk_jian");
		b1.putInt(LembarKunci.IKON, Ikon.JIAN);
		CompoundTag b2 = entri("dao", "toko.bentuk_dao");
		b2.putInt(LembarKunci.IKON, Ikon.DAO);
		pesan.put(TokoSenjata.BENTUK, daftar(b1, b2));
		pesan.putInt(TokoSenjata.MUTU_MAKS, 3);
		pesan.putInt(TokoSenjata.PESANAN_MAKS, 2);
		CompoundTag kut = new CompoundTag();
		kut.putString(LembarKunci.ID, "jian");
		kut.putInt(LembarKunci.MUTU, 2);
		CompoundTag m1 = entri("besi", "toko.bahan_besi");
		barang(m1, Items.IRON_INGOT, r);
		m1.putInt(TokoSenjata.PERLU, 8);
		m1.putInt(TokoSenjata.PUNYA, 11);
		CompoundTag m2 = entri("emas", "toko.bahan_emas");
		barang(m2, Items.GOLD_INGOT, r);
		m2.putInt(TokoSenjata.PERLU, 2);
		m2.putInt(TokoSenjata.PUNYA, 0);
		kut.put(TokoSenjata.BAHAN, daftar(m1, m2));
		kut.putLong(TokoSenjata.ONGKOS, 140L);
		kut.putInt(TokoSenjata.HARI_JADI, 2);
		kut.putBoolean(LembarKunci.BISA, false);
		teks(kut, LembarKunci.ALASAN, "toko.bahan_kurang");
		pesan.put(TokoSenjata.KUTIPAN, kut);
		CompoundTag o = entri("o1", "toko.pesanan_dao");
		barang(o, Items.IRON_SWORD, r);
		o.putInt(LembarKunci.MUTU, 1);
		o.putLong(LembarKunci.SISA_TICK, 9000L);
		pesan.put(TokoSenjata.PESANAN, daftar(o));
		s.put(TokoSenjata.PESAN_TEMPA, pesan);
		return s;
	}

	private static ListTag daftarString(String... ids) {
		ListTag list = new ListTag();
		for (String id : ids) {
			list.add(StringTag.valueOf(id));
		}
		return list;
	}

	private static CompoundTag tawaran(String nama, long nb, boolean anda, boolean baru) {
		CompoundTag e = new CompoundTag();
		teks(e, LembarKunci.NAMA, nama);
		e.putLong(LembarKunci.NB, nb);
		e.putBoolean(LembarKunci.ANDA, anda);
		e.putBoolean(LembarKunci.BARU, baru);
		return e;
	}

	private static CompoundTag lelang(HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "lelang.judul", LembarKunci.GAYA_KAIN, LembarKunci.DISTRIK_PASAR);
		s.putLong(LembarKunci.UANG, 3100L);
		s.putString(Lelang.PROFIL, Lelang.PROFIL_AGUNG);
		s.putString(LembarKunci.STATUS, Lelang.S_BERJALAN);
		s.putLong(LembarKunci.SISA_TICK, 120L);
		CompoundTag lot = new CompoundTag();
		lot.putInt(LembarKunci.NO, 3);
		lot.putInt(Lelang.TOTAL, 8);
		teks(lot, LembarKunci.NAMA, "lelang.lot");
		barang(lot, Items.TRIDENT, r);
		lot.putInt(LembarKunci.MUTU, 3);
		teks(lot, Lelang.PENJUAL, "lelang.penjual");
		lot.putLong(Lelang.DASAR, 2200L);
		lot.putLong(Lelang.KINI, 2640L);
		teks(lot, Lelang.PEMIMPIN, "lelang.gu_lianshan");
		lot.putLong(LembarKunci.SISA_TICK, 120L);
		s.put(Lelang.LOT, lot);
		s.put(Lelang.TAWARAN, daftar(tawaran("lelang.gu_lianshan", 2640L, false, true), tawaran("lelang.kabut_hitam", 2520L, false, false),
				tawaran("lelang.anda", 2400L, true, false), tawaran("lelang.saudagar_lin", 2300L, false, false)));
		s.put(Lelang.LANGKAH, new IntArrayTag(new int[] {5, 10, 25}));
		s.putLong(Lelang.TAWAR_MIN, 2772L);
		s.putFloat(Lelang.KOMISI, 0.10F);
		CompoundTag titip = new CompoundTag();
		titip.putInt(LembarKunci.MAKS, 3);
		titip.putBoolean(LembarKunci.BISA, true);
		CompoundTag tangan = new CompoundTag();
		teks(tangan, LembarKunci.NAMA, "lelang.tangan");
		barang(tangan, Items.GOLDEN_APPLE, r);
		tangan.putLong(Lelang.PERKIRAAN, 600L);
		titip.put(LembarKunci.TANGAN, tangan);
		CompoundTag t1 = entri("t1", "lelang.titip_1");
		barang(t1, Items.EMERALD, r);
		t1.putLong(Lelang.DASAR, 300L);
		t1.putString(LembarKunci.STATUS, Lelang.T_ANTRE);
		CompoundTag t2 = entri("t2", "lelang.titip_2");
		barang(t2, Items.AMETHYST_SHARD, r);
		t2.putLong(Lelang.DASAR, 120L);
		t2.putString(LembarKunci.STATUS, Lelang.T_TERJUAL);
		t2.putLong(Lelang.HASIL, 168L);
		titip.put(LembarKunci.DAFTAR, daftar(t1, t2));
		s.put(Lelang.TITIP, titip);
		CompoundTag h1 = new CompoundTag();
		teks(h1, LembarKunci.NAMA, "lelang.titip_2");
		h1.putLong(LembarKunci.NB, 168L);
		h1.putString(LembarKunci.JENIS, Lelang.H_TITIP);
		h1.putInt(LembarKunci.HARI, 41);
		s.put(Lelang.HASIL, daftar(h1));
		s.putLong(Lelang.UTANG, 168L);
		return s;
	}

	private static CompoundTag misi(String id, String pangkat, String jenis, long nb, boolean kunci) {
		CompoundTag e = new CompoundTag();
		e.putString(LembarKunci.ID, id);
		e.putString(GildaMisi.PANGKAT, pangkat);
		e.putString(LembarKunci.JENIS, jenis);
		teks(e, LembarKunci.JUDUL, "misi." + id);
		teks(e, GildaMisi.CERITA, "misi." + id + ".cerita");
		teks(e, LembarKunci.SYARAT, "misi.syarat_" + pangkat.toLowerCase(java.util.Locale.ROOT));
		e.putLong(GildaMisi.IMBALAN_NB, nb);
		e.putInt(GildaMisi.IMBALAN_NAMA_BAIK, 3);
		e.putInt(GildaMisi.IMBALAN_POIN, 4);
		e.putFloat(GildaMisi.PELUANG_BARANG, 0.15F);
		e.putInt(GildaMisi.WAKTU_HARI, 2);
		teks(e, GildaMisi.ARAH, "misi.arah_barat");
		e.putInt(LembarKunci.X, 640);
		e.putInt(LembarKunci.Z, -20);
		if (kunci) {
			e.putBoolean(LembarKunci.TERKUNCI, true);
			teks(e, LembarKunci.ALASAN, "misi.butuh_dewa");
		}
		return e;
	}

	private static CompoundTag gildaMisi() {
		CompoundTag s = new CompoundTag();
		kepala(s, "misi.judul", LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_GILDA);
		s.putInt(GildaMisi.LENCANA, 4);
		s.putInt(GildaMisi.POIN, 142);
		s.putInt(GildaMisi.POIN_BERIKUT, 160);
		s.putInt(GildaMisi.MISI_HARI_INI, 2);
		s.putInt(GildaMisi.MISI_MAKS, 4);
		s.put(GildaMisi.PAPAN, daftar(misi("begal", "C", "begal", 60L, false), misi("sarang", "B", "sarang", 140L, false),
				misi("surat", "E", "kurir", 12L, false), misi("naga_muda", "S", "naga_muda", 2000L, true)));
		CompoundTag a = misi("kafilah_garam", "D", "kawal", 40L, false);
		teks(a, LembarKunci.TEKS, "misi.kafilah_garam.tahap");
		a.putInt(LembarKunci.TAHAP, 1);
		a.putInt(LembarKunci.TAHAP_TOTAL, 3);
		a.putLong(LembarKunci.SISA_TICK, 20000L);
		s.put(LembarKunci.AKTIF, daftar(a));
		ListTag naga = new ListTag();
		String[] nama = {"naga.1", "naga.2", "naga.3", "naga.anda", "naga.5", "naga.6"};
		for (int i = 0; i < nama.length; i++) {
			CompoundTag n = new CompoundTag();
			n.putInt(GildaMisi.PERINGKAT, i + 1);
			teks(n, LembarKunci.NAMA, nama[i]);
			teks(n, LembarKunci.SEKTE, "naga.sekte_" + (i % 3));
			n.putInt(LembarKunci.SKOR, 980 - i * 85);
			n.putBoolean(LembarKunci.ANDA, i == 3);
			naga.add(n);
		}
		s.put(GildaMisi.NAGA, naga);
		s.putInt(GildaMisi.NAGA_ANDA, 4);
		CompoundTag u = new CompoundTag();
		u.putInt(LembarKunci.TARGET, 6);
		CompoundTag s1 = new CompoundTag();
		teks(s1, LembarKunci.TEKS, "misi.ujian_s1");
		s1.putBoolean(LembarKunci.OK, true);
		CompoundTag s2 = new CompoundTag();
		teks(s2, LembarKunci.TEKS, "misi.ujian_s2");
		s2.putBoolean(LembarKunci.OK, false);
		u.put(LembarKunci.SYARAT, daftar(s1, s2));
		u.putLong(LembarKunci.BIAYA, 180L);
		teks(u, GildaMisi.PENGUJI, "misi.penguji");
		u.putBoolean(LembarKunci.BISA, false);
		teks(u, LembarKunci.ALASAN, "misi.ujian_s2");
		s.put(LembarKunci.UJIAN, u);
		return s;
	}

	private static CompoundTag bank(HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "bank.judul", LembarKunci.GAYA_KAIN, LembarKunci.DISTRIK_PASAR);
		s.putLong(Bank.SALDO, 12480L);
		s.putFloat(Bank.BIAYA_TARIK, 0.01F);
		ListTag riwayat = new ListTag();
		long[] nb = {500L, -120L, 2000L, -45L};
		for (int i = 0; i < nb.length; i++) {
			CompoundTag e = new CompoundTag();
			e.putInt(LembarKunci.HARI, 41 - i);
			e.putLong(LembarKunci.NB, nb[i]);
			teks(e, LembarKunci.CATATAN, "bank.riwayat_" + i);
			riwayat.add(e);
		}
		s.put(LembarKunci.RIWAYAT, riwayat);
		CompoundTag wesel = new CompoundTag();
		wesel.putFloat(LembarKunci.BIAYA, 0.02F);
		s.put(Bank.WESEL, wesel);
		CompoundTag gadai = new CompoundTag();
		gadai.putFloat(Bank.PINJAM, 0.40F);
		gadai.putFloat(Bank.TEBUS, 0.46F);
		gadai.putInt(LembarKunci.HARI, 7);
		CompoundTag tangan = new CompoundTag();
		teks(tangan, LembarKunci.NAMA, "bank.tangan");
		barang(tangan, Items.DIAMOND_SWORD, r);
		tangan.putLong(LembarKunci.NILAI, 980L);
		tangan.putLong(Bank.PINJAMAN, 392L);
		gadai.put(LembarKunci.TANGAN, tangan);
		CompoundTag g1 = entri("g1", "bank.digadai_1");
		barang(g1, Items.GOLDEN_APPLE, r);
		g1.putLong(Bank.PINJAMAN, 120L);
		g1.putLong(Bank.TEBUS_NB, 138L);
		g1.putInt(LembarKunci.SISA_HARI, 1);
		gadai.put(LembarKunci.DAFTAR, daftar(g1));
		s.put(Bank.GADAI, gadai);
		CompoundTag tukar = new CompoundTag();
		tukar.putLong(Bank.BELI, 180L);
		tukar.putLong(Bank.JUAL, 90L);
		tukar.putInt(Bank.BELI_SISA, 3);
		tukar.putInt(Bank.JUAL_SISA, 5);
		tukar.putBoolean(Bank.BOLEH_BELI, true);
		tukar.putInt(Bank.BATU_ASAL, 2);
		s.put(Bank.TUKAR, tukar);
		CompoundTag brankas = new CompoundTag();
		brankas.putInt(LembarKunci.SLOT, 18);
		CompoundTag b1 = new CompoundTag();
		b1.putInt(LembarKunci.SLOT, 0);
		teks(b1, LembarKunci.NAMA, "bank.brankas_1");
		barang(b1, Items.EMERALD, r);
		CompoundTag b2 = new CompoundTag();
		b2.putInt(LembarKunci.SLOT, 4);
		teks(b2, LembarKunci.NAMA, "bank.brankas_2");
		barang(b2, Items.BLAZE_POWDER, r);
		brankas.put(LembarKunci.DAFTAR, daftar(b1, b2));
		s.put(Bank.BRANKAS, brankas);
		return s;
	}

	private static CompoundTag baris(String sumber, int pk, long nb, String catatan) {
		CompoundTag e = new CompoundTag();
		e.putString(BukuKas.SUMBER, sumber);
		e.putInt(LembarKunci.PK, pk);
		e.putLong(LembarKunci.NB, nb);
		if (catatan != null) {
			teks(e, LembarKunci.CATATAN, catatan);
		}
		return e;
	}

	private static CompoundTag bukuKas(HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "kas.judul", LembarKunci.GAYA_KERTAS, LembarKunci.DISTRIK_GILDA);
		s.remove(LembarKunci.NAMA_BAIK);
		s.putInt(LembarKunci.HARI, 52);
		s.put(BukuKas.BARIS, daftar(baris(BukuKas.SUMBER_TAMBANG, 38, 0L, null), baris(BukuKas.SUMBER_LAPAK, 0, 214L, "kas.pajak"),
				baris(BukuKas.SUMBER_KAFILAH, 0, 0L, "kas.berjalan"), baris(BukuKas.SUMBER_UPETI, 12, 0L, "kas.barang"),
				baris(BukuKas.SUMBER_SEWA, 0, -60L, null)));
		s.putInt(BukuKas.BERSIH_PK, 50);
		s.putLong(BukuKas.BERSIH_NB, 143L);
		s.put(BukuKas.GRAFIK_PK, new IntArrayTag(new int[] {20, 34, 41, 28, 55, 61, 50}));
		s.put(BukuKas.GRAFIK_NB, new LongArrayTag(new long[] {40L, 90L, 120L, 60L, 180L, 210L, 143L}));
		ListTag minggu = new ListTag();
		int[] pk = {20, 34, 41, 28, 55, 61, 50};
		long[] nb = {40L, 90L, -20L, 60L, 180L, 210L, 143L};
		for (int i = 0; i < 7; i++) {
			CompoundTag m = new CompoundTag();
			m.putInt(LembarKunci.HARI, 46 + i);
			m.putInt(LembarKunci.PK, pk[i]);
			m.putLong(LembarKunci.NB, nb[i]);
			minggu.add(m);
		}
		s.put(BukuKas.MINGGU, minggu);
		CompoundTag sb = baris(BukuKas.SUMBER_LAPAK, 0, 980L, "kas.batas_lapak");
		s.put(BukuKas.SUMBER_DAFTAR, daftar(baris(BukuKas.SUMBER_TAMBANG, 266, 0L, null), sb, baris(BukuKas.SUMBER_GAJI, -40, -120L, null)));
		CompoundTag lapak = new CompoundTag();
		lapak.putBoolean(BukuKas.ADA, true);
		teks(lapak, BukuKas.KIOS, "kas.kios");
		lapak.putLong(BukuKas.SEWA, 60L);
		lapak.putInt(BukuKas.SEWA_SISA_HARI, 5);
		teks(lapak, BukuKas.PENJAGA, "kas.murid_penjaga");
		lapak.putLong(BukuKas.KAS, 214L);
		lapak.putLong(BukuKas.VOLUME_MAKS, 240L);
		ListTag slots = new ListTag();
		Item[] items = {Items.BREAD, Items.HONEY_BOTTLE, Items.FEATHER, Items.SWEET_BERRIES, Items.PAPER};
		int[] peluang = {80, 50, 20, 80, 0};
		for (int i = 0; i < items.length; i++) {
			CompoundTag e = new CompoundTag();
			e.putInt(LembarKunci.SLOT, i * 2);
			teks(e, LembarKunci.NAMA, "kas.slot_" + i);
			barang(e, items[i], r);
			e.putLong(LembarKunci.HARGA, 6L + i * 4L);
			e.putLong(LembarKunci.NILAI, 6L + i * 3L);
			e.putInt(LembarKunci.PELUANG, peluang[i]);
			slots.add(e);
		}
		lapak.put(LembarKunci.DAFTAR, slots);
		s.put(BukuKas.LAPAK, lapak);
		CompoundTag kafilah = new CompoundTag();
		kafilah.putInt(LembarKunci.MAKS, 2);
		kafilah.putLong(BukuKas.LABA_MINGGU, 320L);
		kafilah.putLong(BukuKas.LABA_BATAS, 1200L);
		CompoundTag k1 = new CompoundTag();
		k1.putString(LembarKunci.ID, "k1");
		teks(k1, LembarKunci.TUJUAN, "kas.ke_kota_awan");
		CompoundTag pw = new CompoundTag();
		teks(pw, LembarKunci.NAMA, "kas.pengawal_1");
		k1.put(BukuKas.PENGAWAL, daftar(pw));
		k1.putInt(LembarKunci.RISIKO, 12);
		k1.putLong(BukuKas.LABA_KIRA, 210L);
		k1.putInt(LembarKunci.SISA_HARI, 1);
		k1.putFloat(LembarKunci.KEMAJUAN, 0.55F);
		kafilah.put(LembarKunci.DAFTAR, daftar(k1));
		CompoundTag rencana = new CompoundTag();
		CompoundTag tj = entri("kota_awan", "kas.tujuan_kota_awan");
		tj.putInt(LembarKunci.JARAK, 1400);
		tj.putInt(LembarKunci.HARI, 2);
		tj.putFloat(BukuKas.PERMINTAAN, 1.2F);
		CompoundTag tj2 = entri("dusun_batu", "kas.tujuan_dusun_batu");
		tj2.putInt(LembarKunci.JARAK, 600);
		tj2.putInt(LembarKunci.HARI, 1);
		tj2.putFloat(BukuKas.PERMINTAAN, 0.8F);
		rencana.put(LembarKunci.TUJUAN, daftar(tj, tj2));
		CompoundTag md = entri("m1", "kas.pengawal_1");
		teks(md, BukuKas.RANAH, "kas.ranah_fondasi");
		CompoundTag md2 = entri("m2", "kas.pengawal_2");
		teks(md2, BukuKas.RANAH, "kas.ranah_qi");
		rencana.put(BukuKas.MURID, daftar(md, md2));
		CompoundTag mu = entri("pil", "kas.muatan_pil");
		CompoundTag mu2 = entri("herba", "kas.muatan_herba");
		rencana.put(BukuKas.MUATAN, daftar(mu, mu2));
		kafilah.put(BukuKas.RENCANA, rencana);
		s.put(BukuKas.KAFILAH, kafilah);
		return s;
	}

	private static CompoundTag tagihan(String keadaan, HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "tagihan.judul", LembarKunci.GAYA_KERTAS, LembarKunci.DISTRIK_HUNIAN);
		s.remove(LembarKunci.DISTRIK);
		npc(s, "tagihan.kepala_desa", "tagihan.gelar", Ikon.CAP_MERAH);
		s.putString(Tagihan.TAGIHAN_ID, "uji");
		teks(s, Tagihan.DARI, "tagihan.dari");
		String varian = "takut".equals(keadaan) ? Tagihan.V_TAKUT : "donasi".equals(keadaan) ? Tagihan.V_DONASI : Tagihan.V_BIASA;
		s.putString(Tagihan.VARIAN, varian);
		switch (varian) {
			case Tagihan.V_TAKUT -> {
				teks(s, Tagihan.KALIMAT, "tagihan.kalimat_takut");
				s.putInt(Tagihan.TAKUT_METER, 72);
				s.getCompoundOrEmpty(LembarKunci.NPC).putBoolean(LembarKunci.TAKUT, true);
			}
			case Tagihan.V_DONASI -> {
				teks(s, Tagihan.KALIMAT, "tagihan.kalimat_donasi");
				CompoundTag d = new CompoundTag();
				d.putFloat(LembarKunci.KEMAJUAN, 0.35F);
				CompoundTag a = entri("bata", "tagihan.bata");
				barang(a, Items.BRICKS, r);
				a.putInt(LembarKunci.JUMLAH, 24);
				a.putInt(Tagihan.ADA, 10);
				CompoundTag b = entri("papan", "tagihan.papan");
				barang(b, Items.OAK_PLANKS, r);
				b.putInt(LembarKunci.JUMLAH, 40);
				b.putInt(Tagihan.ADA, 40);
				d.put(Tagihan.PERLU, daftar(a, b));
				d.putLong(Tagihan.MIN, 10L);
				d.putBoolean(Tagihan.BANTU, true);
				s.put(Tagihan.DONASI, d);
			}
			default -> {
				teks(s, Tagihan.KALIMAT, "tagihan.kalimat");
				CompoundTag a = entri("bata", "tagihan.bata");
				barang(a, Items.BRICKS, r);
				a.putInt(LembarKunci.JUMLAH, 24);
				a.putFloat(Tagihan.SUBTOTAL, 9.6F);
				CompoundTag b = entri("papan", "tagihan.papan");
				barang(b, Items.OAK_PLANKS, r);
				b.putInt(LembarKunci.JUMLAH, 40);
				b.putFloat(Tagihan.SUBTOTAL, 8.0F);
				CompoundTag c = entri("kaca", "tagihan.kaca");
				barang(c, Items.GLASS, r);
				c.putInt(LembarKunci.JUMLAH, 6);
				c.putFloat(Tagihan.SUBTOTAL, 1.8F);
				s.put(Tagihan.RINCIAN, daftar(a, b, c));
				s.putFloat(Tagihan.UPAH, 5.9F);
				s.putInt(Tagihan.UPAH_PERSEN, 30);
				s.putLong(Tagihan.TOTAL, 26L);
				teks(s, Tagihan.AKIBAT_BAYAR, "tagihan.akibat_bayar");
				teks(s, Tagihan.AKIBAT_TOLAK, "tagihan.akibat_tolak");
			}
		}
		return s;
	}

	private static CompoundTag distrik(String id, int gx, int gz, String... layanan) {
		CompoundTag d = new CompoundTag();
		d.putString(LembarKunci.ID, id);
		teks(d, LembarKunci.NAMA, "peta.distrik." + id);
		d.putInt(PetaKota.GX, gx);
		d.putInt(PetaKota.GZ, gz);
		d.putBoolean(PetaKota.LUAR, gx < 0);
		ListTag l = new ListTag();
		for (String la : layanan) {
			CompoundTag e = new CompoundTag();
			teks(e, LembarKunci.NAMA, "peta.layanan." + la);
			e.putInt(LembarKunci.IKON, Ikon.layanan(la));
			l.add(e);
		}
		d.put(PetaKota.LAYANAN, l);
		return d;
	}

	private static CompoundTag petaKota() {
		CompoundTag s = new CompoundTag();
		kepala(s, "peta.judul", LembarKunci.GAYA_GIOK, LembarKunci.DISTRIK_PUSAT);
		s.put(PetaKota.DISTRIK_DAFTAR, daftar(distrik(LembarKunci.DISTRIK_ISTANA, 1, 0), distrik(LembarKunci.DISTRIK_CENDEKIA, 0, 0),
				distrik(LembarKunci.DISTRIK_AWAN, 2, 0, Dermaga.LAYAR), distrik(LembarKunci.DISTRIK_GILDA, 0, 1, GildaMisi.LAYAR, Gilda.LAYAR_TEMPA),
				distrik(LembarKunci.DISTRIK_PUSAT, 1, 1, Teleport.LAYAR), distrik(LembarKunci.DISTRIK_PASAR, 2, 1, Lelang.LAYAR, Bank.LAYAR,
						JudiBatu.LAYAR, PasarBinatang.LAYAR), distrik(LembarKunci.DISTRIK_HUNIAN, 0, 2, Layanan.LAYAR_PENGINAPAN),
				distrik(LembarKunci.DISTRIK_ARENA, 1, 2), distrik(LembarKunci.DISTRIK_GERBANG, 2, 2, Yamen.LAYAR),
				distrik(LembarKunci.DISTRIK_KUMUH, -1, -1), distrik(LembarKunci.DISTRIK_LUAR, -1, -1)));
		s.putFloat(PetaKota.POS_X, 2.4F);
		s.putFloat(PetaKota.POS_Z, 1.6F);
		s.putString(PetaKota.DISTRIK_PEMAIN, LembarKunci.DISTRIK_PASAR);
		CompoundTag l1 = entri(Lelang.LAYAR, "peta.layanan." + Lelang.LAYAR);
		l1.putInt(LembarKunci.IKON, Ikon.LELANG);
		l1.putString(LembarKunci.DISTRIK, LembarKunci.DISTRIK_PASAR);
		teks(l1, LembarKunci.CATATAN, "peta.jam_lelang");
		l1.putBoolean(PetaKota.BUKA, true);
		l1.putInt(LembarKunci.X, 120);
		l1.putInt(LembarKunci.Z, 40);
		CompoundTag l2 = entri(Yamen.LAYAR, "peta.layanan." + Yamen.LAYAR);
		l2.putInt(LembarKunci.IKON, Ikon.YAMEN);
		l2.putString(LembarKunci.DISTRIK, LembarKunci.DISTRIK_GERBANG);
		teks(l2, LembarKunci.CATATAN, "peta.jam_yamen");
		l2.putBoolean(PetaKota.BUKA, false);
		s.put(PetaKota.LAYANAN, daftar(l1, l2));
		CompoundTag tk = entri("gu_lianshan", "lelang.gu_lianshan");
		teks(tk, LembarKunci.GELAR, "peta.gelar_gu");
		teks(tk, LembarKunci.CATATAN, "peta.di_mana_gu");
		tk.putString(LembarKunci.DISTRIK, LembarKunci.DISTRIK_PASAR);
		tk.putBoolean(PetaKota.KENAL, true);
		CompoundTag tk2 = entri("tak_dikenal", "lelang.gu_lianshan");
		teks(tk2, LembarKunci.CATATAN, "peta.di_mana_asing");
		tk2.putString(LembarKunci.DISTRIK, LembarKunci.DISTRIK_KUMUH);
		s.put(PetaKota.TOKOH, daftar(tk, tk2));
		CompoundTag ac = entri(Acara.ACARA_LELANG_AGUNG, "peta.acara_lelang");
		ac.putInt(LembarKunci.HARI, 55);
		ac.putLong(LembarKunci.SISA_TICK, 48000L);
		teks(ac, LembarKunci.CATATAN, "peta.jam_lelang");
		CompoundTag ac2 = entri(Acara.ACARA_PASAR_MALAM, "peta.acara_pasar_malam");
		ac2.putInt(LembarKunci.HARI, 52);
		ac2.putBoolean(LembarKunci.AKTIF, true);
		s.put(PetaKota.ACARA, daftar(ac2, ac));
		return s;
	}

	private static CompoundTag tujuan(String id, int jarak, long harga, int x, int z, boolean kunci) {
		CompoundTag e = entri(id, "teleport." + id);
		e.putString(LembarKunci.JENIS, "kota");
		e.putInt(LembarKunci.JARAK, jarak);
		e.putLong(LembarKunci.HARGA, harga);
		e.putInt(LembarKunci.X, x);
		e.putInt(LembarKunci.Z, z);
		if (kunci) {
			e.putBoolean(LembarKunci.TERKUNCI, true);
			teks(e, LembarKunci.ALASAN, "teleport.temukan_dulu");
		}
		return e;
	}

	private static CompoundTag teleport(boolean dermaga) {
		CompoundTag s = new CompoundTag();
		kepala(s, dermaga ? "teleport.judul_dermaga" : "teleport.judul", LembarKunci.GAYA_GIOK, dermaga ? LembarKunci.DISTRIK_AWAN
				: LembarKunci.DISTRIK_PUSAT);
		CompoundTag a = tujuan("kota_awan", 1400, 40L, 1400, 200, false);
		CompoundTag b = tujuan("dusun_batu", 600, 15L, -500, 320, false);
		CompoundTag c = tujuan("lembah_kabut", 3200, 90L, 2600, -1800, true);
		if (dermaga) {
			a.putInt(Dermaga.DURASI, 12);
			b.putInt(Dermaga.DURASI, 8);
			b.putBoolean(Dermaga.LANGIT, true);
			s.putBoolean(Dermaga.BUKA, true);
			s.putString(LembarKunci.ENTITAS, "minecraft:oak_boat");
			CompoundTag tamu = new CompoundTag();
			teks(tamu, LembarKunci.NAMA, "teleport.tamu_langit");
			tamu.putInt(LembarKunci.HARI, 54);
			s.put(Dermaga.TAMU_LANGIT, daftar(tamu));
		}
		s.put(LembarKunci.TUJUAN, daftar(a, b, c));
		s.putInt(Teleport.PENGIKUT, 2);
		s.putLong(Teleport.HARGA_PENGIKUT, 10L);
		s.putInt(Teleport.KANAL_DETIK, 3);
		return s;
	}

	private static CompoundTag pasarBinatang(HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "pasar.judul", LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_PASAR);
		CompoundTag k1 = entri("rubah", "pasar.anak_rubah");
		k1.putString(LembarKunci.ENTITAS, "minecraft:fox");
		k1.putLong(LembarKunci.HARGA, 480L);
		k1.putInt(LembarKunci.STOK, 1);
		k1.putInt(LembarKunci.SISA_HARI, 2);
		teks(k1, LembarKunci.CATATAN, "pasar.rubah_cerita");
		CompoundTag k2 = entri("kucing", "pasar.anak_harimau");
		k2.putString(LembarKunci.ENTITAS, "minecraft:ocelot");
		k2.putLong(LembarKunci.HARGA, 2400L);
		k2.putInt(LembarKunci.STOK, 1);
		k2.putString(LembarKunci.KEADAAN, LembarKunci.K_KURANG_UANG);
		s.put(PasarBinatang.KANDANG, daftar(k1, k2));
		CompoundTag sj = entri("buah", "pasar.sesaji_buah");
		barang(sj, Items.SWEET_BERRIES, r);
		sj.putLong(LembarKunci.HARGA, 8L);
		sj.putInt(LembarKunci.STOK, -1);
		s.put(PasarBinatang.SESAJI, daftar(sj));
		CompoundTag bn = entri("uuid-uji", "pasar.binatang_anda");
		bn.putInt(PasarBinatang.TIER, 2);
		bn.putLong(LembarKunci.HARGA, 60L);
		s.put(PasarBinatang.BINATANG, daftar(bn));
		CompoundTag in = entri("inti", "pasar.inti_serigala");
		barang(in, Items.AMETHYST_SHARD, r);
		in.putLong(LembarKunci.HARGA, 140L);
		in.putBoolean(PasarBinatang.JUAL, true);
		s.put(PasarBinatang.INTI, daftar(in));
		return s;
	}

	private static CompoundTag batu(String id, String tingkat, long harga, String warna, HolderLookup.Provider r) {
		CompoundTag e = entri(id, "judi.batu_" + tingkat);
		e.putString(JudiBatu.TINGKAT, tingkat);
		barang(e, JudiBatu.TINGKAT_KERIKIL.equals(tingkat) ? Items.COBBLESTONE : Items.RAW_IRON, r);
		e.putLong(LembarKunci.HARGA, harga);
		if (warna != null) {
			e.putBoolean(JudiBatu.DIPERIKSA, true);
			e.putString(JudiBatu.WARNA, warna);
		}
		return e;
	}

	private static CompoundTag judiBatu(HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "judi.judul", LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_PASAR);
		s.put(JudiBatu.RAK, daftar(batu("b1", JudiBatu.TINGKAT_KERIKIL, 20L, null, r), batu("b2", JudiBatu.TINGKAT_BONGKAH, 120L, JudiBatu.WARNA_BIRU, r),
				batu("b3", JudiBatu.TINGKAT_GUNUNG, 600L, JudiBatu.WARNA_EMAS, r), batu("b4", JudiBatu.TINGKAT_PURBA, 3000L, null, r)));
		s.putInt(JudiBatu.SISA, 7);
		s.putInt(JudiBatu.PURBA_SISA, 1);
		s.putInt(JudiBatu.MATA_BATIN_SISA, 2);
		s.putBoolean(JudiBatu.MATA_BATIN_BOLEH, true);
		CompoundTag h = new CompoundTag();
		teks(h, LembarKunci.NAMA, "judi.batu_bongkah");
		h.putString(JudiBatu.HASIL, JudiBatu.HASIL_SEDANG);
		h.putLong(LembarKunci.NILAI, 160L);
		CompoundTag h2 = new CompoundTag();
		teks(h2, LembarKunci.NAMA, "judi.batu_kerikil");
		h2.putString(JudiBatu.HASIL, JudiBatu.HASIL_KOSONG);
		s.put(LembarKunci.RIWAYAT, daftar(h, h2));
		CompoundTag potong = new CompoundTag();
		potong.putString(LembarKunci.ID, "potong-uji");
		potong.putString(JudiBatu.HASIL, JudiBatu.HASIL_JACKPOT);
		teks(potong, LembarKunci.NAMA, "judi.giok_kaisar");
		barang(potong, Items.EMERALD, r);
		potong.putLong(LembarKunci.NILAI, 4200L);
		s.put(JudiBatu.POTONG, potong);
		return s;
	}

	private static CompoundTag layanan(String id, HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		boolean jasa = Layanan.LAYAR_JASA.equals(id);
		kepala(s, "layanan.judul_" + id, jasa ? LembarKunci.GAYA_GIOK : LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_HUNIAN);
		if (jasa) {
			s.putString(LembarKunci.JENIS, Layanan.JENIS_KUIL);
		}
		CompoundTag m1 = entri("m1", "layanan." + id + ".1");
		m1.putInt(LembarKunci.IKON, Ikon.layanan(id));
		m1.putLong(LembarKunci.HARGA, 12L);
		teks(m1, Layanan.EFEK, "layanan." + id + ".1.efek");
		CompoundTag m2 = entri("m2", "layanan." + id + ".2");
		if (Layanan.LAYAR_RUMAH_MAKAN.equals(id)) {
			barang(m2, Items.COOKED_BEEF, r);
		} else {
			m2.putInt(LembarKunci.IKON, Ikon.layanan(id));
		}
		m2.putLong(LembarKunci.HARGA, 40L);
		teks(m2, Layanan.EFEK, "layanan." + id + ".2.efek");
		CompoundTag m3 = entri("m3", "layanan." + id + ".3");
		m3.putInt(LembarKunci.IKON, Ikon.layanan(id));
		m3.putLong(LembarKunci.HARGA, 2400L);
		teks(m3, Layanan.EFEK, "layanan." + id + ".3.efek");
		m3.putString(LembarKunci.KEADAAN, LembarKunci.K_KURANG_UANG);
		s.put(Layanan.MENU, daftar(m1, m2, m3));
		CompoundTag a = new CompoundTag();
		teks(a, LembarKunci.NAMA, "layanan.efek_aktif");
		a.putLong(LembarKunci.SISA_TICK, 6000L);
		s.put(LembarKunci.AKTIF, daftar(a));
		s.putBoolean(Layanan.SUDAH_MENGINAP, Layanan.LAYAR_PENGINAPAN.equals(id));
		return s;
	}

	private static CompoundTag yamen(HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, "yamen.judul", LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_GERBANG);
		s.putInt(Yamen.POIN, 115);
		s.putString(LembarKunci.STATUS, Yamen.ST_BURONAN);
		s.putLong(Yamen.DENDA, 230L);
		teks(s, LembarKunci.CATATAN, "yamen.catatan");
		CompoundTag e = entri("sitaan1", "yamen.sitaan_1");
		barang(e, Items.IRON_SWORD, r);
		e.putLong(LembarKunci.HARGA, 45L);
		s.put(Yamen.SITAAN, daftar(e));
		return s;
	}

	private static CompoundTag skor(String nama, int skor, boolean anda) {
		CompoundTag e = new CompoundTag();
		teks(e, LembarKunci.NAMA, nama);
		e.putInt(LembarKunci.SKOR, skor);
		e.putBoolean(LembarKunci.ANDA, anda);
		return e;
	}

	private static CompoundTag acara() {
		CompoundTag s = new CompoundTag();
		kepala(s, "acara.judul", LembarKunci.GAYA_KAIN, LembarKunci.DISTRIK_PUSAT);
		s.putString(Acara.ACARA_ID, Acara.ACARA_PERTEMUAN_PIL);
		s.putString(LembarKunci.JENIS, Acara.ACARA_PERTEMUAN_PIL);
		s.putString(LembarKunci.STATUS, Lelang.S_BERJALAN);
		s.putLong(LembarKunci.SISA_TICK, 2400L);
		s.putLong(LembarKunci.BIAYA, 30L);
		s.putBoolean(Acara.TERDAFTAR, true);
		s.put(LembarKunci.SKOR, daftar(skor("acara.peserta_1", 88, false), skor("acara.peserta_anda", 81, true), skor("acara.peserta_2", 77, false),
				skor("acara.peserta_3", 64, false)));
		CompoundTag teka = new CompoundTag();
		teka.putString(LembarKunci.ID, "teka1");
		teks(teka, Acara.SOAL, "acara.soal");
		CompoundTag j1 = new CompoundTag();
		teks(j1, LembarKunci.TEKS, "acara.jawab_1");
		CompoundTag j2 = new CompoundTag();
		teks(j2, LembarKunci.TEKS, "acara.jawab_2");
		CompoundTag j3 = new CompoundTag();
		teks(j3, LembarKunci.TEKS, "acara.jawab_3");
		teka.put(Acara.JAWABAN, daftar(j1, j2, j3));
		teka.putInt(Acara.SISA, 2);
		s.put(Acara.TEKA, teka);
		CompoundTag jd = new CompoundTag();
		teks(jd, LembarKunci.NAMA, "acara.final");
		jd.putInt(LembarKunci.HARI, 53);
		teks(jd, LembarKunci.CATATAN, "acara.jam_final");
		s.put(Acara.JADWAL, daftar(jd));
		return s;
	}

	/** "istana": the palace audience as 38KS builds it (no status, speaker card, the hall's note, court orders). */
	private static CompoundTag acaraIstana() {
		CompoundTag s = new CompoundTag();
		kepala(s, "istana.judul", LembarKunci.GAYA_KAIN, LembarKunci.DISTRIK_ISTANA);
		teks(s, LembarKunci.SUBJUDUL, "istana.pangeran");
		s.putString(Acara.ACARA_ID, Acara.ACARA_ISTANA);
		npc(s, "istana.menteri", "istana.menteri_gelar", Ikon.ISTANA);
		CompoundTag teka = new CompoundTag();
		teka.putString(LembarKunci.ID, "perintah");
		teks(teka, Acara.SOAL, "istana.soal");
		ListTag jawab = new ListTag();
		for (String a : List.of("utusan", "segarkan", "pamit")) {
			CompoundTag j = new CompoundTag();
			teks(j, LembarKunci.TEKS, "istana.jawab_" + a);
			jawab.add(j);
		}
		teka.put(Acara.JAWABAN, jawab);
		teka.putInt(Acara.SISA, 1);
		s.put(Acara.TEKA, teka);
		ListTag tugas = new ListTag();
		for (String t : List.of("sarang", "kafilah", "buronan")) {
			CompoundTag row = new CompoundTag();
			teks(row, LembarKunci.NAMA, "istana.tugas_" + t);
			row.putInt(LembarKunci.HARI, 56);
			teks(row, LembarKunci.CATATAN, "istana.status_" + t);
			tugas.add(row);
		}
		s.put(Acara.JADWAL, tugas);
		teks(s, LembarKunci.CATATAN, "istana.catatan");
		return s;
	}

	private static CompoundTag rekrut() {
		CompoundTag s = new CompoundTag();
		kepala(s, "rekrut.judul", LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_PUSAT);
		s.putBoolean(LapakRekrut.SUDAH_SEWA, true);
		s.putInt(LapakRekrut.UNDANG_SISA, 2);
		s.putLong(LapakRekrut.SEWA, 80L);
		ListTag calon = new ListTag();
		for (int i = 0; i < 3; i++) {
			CompoundTag c = entri("c" + i, "rekrut.calon_" + i);
			c.putInt(LembarKunci.POTRET, -1);
			teks(c, LapakRekrut.AKAR, "rekrut.akar_" + i);
			teks(c, LapakRekrut.BAKAT, "rekrut.bakat_" + i);
			c.putInt(LembarKunci.PELUANG, 70 - i * 25);
			calon.add(c);
		}
		s.put(LapakRekrut.CALON, calon);
		return s;
	}

	private static CompoundTag jurnal() {
		CompoundTag s = new CompoundTag();
		kepala(s, "jurnal.judul", LembarKunci.GAYA_KERTAS, LembarKunci.DISTRIK_CENDEKIA);
		s.remove(LembarKunci.UANG);
		CompoundTag a = entri("bibi_hong_1", "dialog.quest_nama");
		teks(a, LembarKunci.TEMPAT, "tempat.kota_awan");
		teks(a, LembarKunci.TEKS, "jurnal.tahap_bibi");
		a.putInt(LembarKunci.TAHAP, 2);
		a.putInt(LembarKunci.TAHAP_TOTAL, 4);
		a.putInt(LembarKunci.X, 120);
		a.putInt(LembarKunci.Z, 40);
		a.putBoolean(Jurnal.ADA_ARAH, true);
		a.putLong(LembarKunci.SISA_TICK, 30000L);
		s.put(LembarKunci.AKTIF, daftar(a));
		s.putString(Jurnal.LACAK, "bibi_hong_1");
		CompoundTag sl = entri("surat", "misi.surat");
		teks(sl, LembarKunci.TEMPAT, "tempat.dusun_batu");
		sl.putInt(LembarKunci.HARI, 48);
		teks(sl, Jurnal.HASIL, "jurnal.hasil_surat");
		s.put(Jurnal.SELESAI, daftar(sl));
		ListTag alur = new ListTag();
		for (int i = 1; i <= 3; i++) {
			CompoundTag b = new CompoundTag();
			b.putInt(Jurnal.BAB, i);
			teks(b, LembarKunci.JUDUL, "jurnal.bab_" + i);
			teks(b, Jurnal.RINGKASAN, "jurnal.bab_" + i + ".ringkasan");
			b.putBoolean(Jurnal.TERBUKA, i < 3);
			alur.add(b);
		}
		s.put(Jurnal.ALUR, alur);
		return s;
	}

	private static CompoundTag gilda(boolean tempa, HolderLookup.Provider r) {
		CompoundTag s = new CompoundTag();
		kepala(s, tempa ? "gilda.judul_tempa" : "gilda.judul_alkemis", LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_GILDA);
		s.putInt(Gilda.TINGKAT, 2);
		CompoundTag u = new CompoundTag();
		u.putInt(LembarKunci.TARGET, 3);
		CompoundTag s1 = new CompoundTag();
		teks(s1, LembarKunci.TEKS, tempa ? "gilda.syarat_tempa" : "gilda.syarat_alkemis");
		s1.putBoolean(LembarKunci.OK, true);
		u.put(LembarKunci.SYARAT, daftar(s1));
		u.putLong(LembarKunci.BIAYA, 45L);
		u.putBoolean(LembarKunci.BISA, true);
		s.put(LembarKunci.UJIAN, u);
		CompoundTag t1 = entri("t1", tempa ? "gilda.toko_tempa_1" : "gilda.toko_alkemis_1");
		barang(t1, tempa ? Items.IRON_INGOT : Items.BLAZE_POWDER, r);
		t1.putLong(LembarKunci.HARGA, 30L);
		t1.putInt(LembarKunci.STOK, 12);
		CompoundTag t2 = entri("t2", tempa ? "gilda.toko_tempa_2" : "gilda.toko_alkemis_2");
		barang(t2, tempa ? Items.GOLD_INGOT : Items.GOLDEN_APPLE, r);
		t2.putLong(LembarKunci.HARGA, 260L);
		t2.putBoolean(LembarKunci.TERKUNCI, true);
		teks(t2, LembarKunci.ALASAN, "gilda.butuh_tingkat_3");
		s.put(Gilda.TOKO, daftar(t1, t2));
		CompoundTag p = entri("p1", tempa ? "gilda.pesanan_tempa" : "gilda.pesanan_alkemis");
		barang(p, tempa ? Items.IRON_SWORD : Items.HONEY_BOTTLE, r);
		p.putBoolean(TokoSenjata.SIAP, true);
		s.put(Gilda.PESANAN, daftar(p));
		s.putInt(Gilda.NILAI_SISA, 1);
		return s;
	}

	private static CompoundTag kafilah() {
		CompoundTag s = new CompoundTag();
		kepala(s, "kafilah.judul", LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_GERBANG);
		CompoundTag a = entri("garam", "kafilah.garam");
		teks(a, Kafilah.ASAL, "tempat.dusun_batu");
		teks(a, LembarKunci.TUJUAN, "tempat.kota_awan");
		a.putFloat(LembarKunci.KEMAJUAN, 0.4F);
		a.putInt(LembarKunci.RISIKO, 30);
		a.putLong(Kafilah.IMBALAN, 70L);
		a.putBoolean(LembarKunci.BISA, true);
		CompoundTag b = entri("sutra", "kafilah.sutra");
		teks(b, Kafilah.ASAL, "tempat.kota_awan");
		teks(b, LembarKunci.TUJUAN, "tempat.lembah_kabut");
		b.putFloat(LembarKunci.KEMAJUAN, 0.1F);
		b.putInt(LembarKunci.RISIKO, 8);
		b.putLong(Kafilah.IMBALAN, 35L);
		b.putBoolean(LembarKunci.BISA, false);
		teks(b, LembarKunci.ALASAN, "kafilah.sudah_dikawal");
		s.put(LembarKunci.DAFTAR, daftar(a, b));
		return s;
	}

	private UjiUi() {
	}
}
