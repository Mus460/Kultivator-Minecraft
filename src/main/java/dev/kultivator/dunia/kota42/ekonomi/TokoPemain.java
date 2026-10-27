package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.cerita.Isian;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.BukuKas;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KafilahSekte;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.ekonomi.LapakSekte;
import dev.kultivator.dunia.tempat.ekonomi.RumahUang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A player's own shop (DESIGN 42.6.2, contract C42.C1). A shop lives in an owned or rented <b>rumah toko</b> (opened by
 * using a {@code meja_kasir} inside it) or on a rented {@link LapakKota} stall (opened with the lease). Its goods stand on
 * the owner's {@code rak_dagang} racks inside the house box / stall claim (at most 12 racks counted), one asking price per
 * shelf (per item; default the town price {@code HargaKota.harga}, at most 1.5 x value, 38.M.5).
 * <p>
 * <b>Buyers</b>: every world hour the shop is open (8-20, a lazy clerk shuts 2 h early) the town sends
 * {@code penduduk x district traffic (pasar 1.0, main street 0.7, housing 0.3) x hour curve (peaks 9-11, 17-19) x
 * (1 + mean mood / 200) x reputation / 50} buyers (scaled to a shop); each picks a shelf weighted by the place's demand
 * ({@code KafilahSekte.permintaan}) and buys 1-3 items with the 38.M.4.1 chance of {@code h = asking / town price}
 * ({@code LapakSekte.peluangLaku}: 80 / 50 / 20 %), the clerk's temperament (ramah +10 %, licik +5 %) and skill (+0.2 % per
 * point) added. Sales stop at {@code tokoBatas x (b+1)} NB a day (rumah toko x1.5); a 5 % market tax goes to the town
 * purse; the rest waits in the till ({@link #ambilLaci}). A shop without a clerk at a counter sells only while its owner is
 * within 64 blocks. Hours missed while the shop's chunk was unloaded are caught up (at most a day) when it loads again.
 * <p>
 * <b>Clerk</b>: an unemployed resident (8/10/12/15 NB a week by place kind) or one of the owner's disciples (no NB wage; a
 * disciple also guards against the 2 %-a-week night theft). Temperament ({@code Tokoh.WATAK}): ramah +10 % sales, licik
 * +5 % but 5 % a week of goods vanish, serakah +5 %, jujur loses nothing, sombong (the design's "malas") closes 2 h
 * early; skill +4 a week. A service body stands at the counter while a
 * player is near ({@code HidupApi.tubuhStaf}).
 * <p>
 * <b>Reputation</b> 0-100 (start 40): fair prices (mean h &lt;= 1.1) +1 a day, greed (&gt; 1.35) -1, variety of 6+ goods +1,
 * a friendly clerk +1, festival lanterns during a festival +1, three days without a sale -1.
 * <p>
 * State: {@code KotaEkonomiData.situs("toko", situs)["t"]}: shop id ({@code r<house>} / {@code l<stall>}) -> owner, kind,
 * name, counter, reputation, till, band, today's sales, last hour run, clerk, 7-day report, weekly top goods.
 */
public final class TokoPemain {

	/** One shop: owner, place, its anchor (the cashier table, or the stall titik) and its name. */
	public record Toko(UUID pemilik, long situs, BlockPos pos, String nama) {
	}

	/** Racks counted per shop. */
	public static final int RAK_MAKS = 12;
	public static final int NAMA_MAKS = 24;
	public static final double HARGA_MAKS = LapakSekte.HARGA_MAKS;
	public static final double PAJAK = LapakSekte.PAJAK;
	public static final int REP_AWAL = 40;
	/** Blocks within which the owner counts as minding his shop himself. */
	public static final double HADIR_JARAK = 64.0D;
	/** Kinds ({@code KotaLembar.Toko.TJ_*}). */
	public static final String TJ_RUMAH_TOKO = "rumah_toko";
	public static final String TJ_LAPAK = "lapak";
	/** Price presets the owner cycles with sneak + empty hand on a shelf (x town price). */
	private static final double[] PRESET = {0.8D, 0.9D, 1.0D, 1.1D, 1.25D, 1.5D};
	private static final String MURID_ALASAN = "toko_kota";

	/** Clerk bodies at counters: "situs:id" -> body. */
	private static final Map<String, KultivatorNpc> TUBUH = new HashMap<>();
	/** Fronts of open shops with a player near, per place (embodied shoppers go there; rebuilt every 5 s). */
	private static volatile Map<Long, List<BlockPos>> DEPAN = Map.of();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Halls.addReturnHandler(MURID_ALASAN, (level, owner, stored, info) -> null);
	}

	static void lupakan() {
		TUBUH.values().forEach(KultivatorNpc::vanish);
		TUBUH.clear();
		DEPAN = Map.of();
	}

	/** The fronts of the open shops of {@code situs} with a player near (where embodied shoppers stand and look). */
	public static List<BlockPos> depanToko(long situs) {
		return DEPAN.getOrDefault(situs, List.of());
	}

	// ================================================================== finding shops

	/** The player shop whose house or stall contains {@code pos}, or null. */
	public static @Nullable Toko di(ServerLevel level, BlockPos pos) {
		CompoundTag t = tagDi(level, pos);
		return t == null ? null : toko(t);
	}

	/** The shop of stall {@code no}, or null. */
	public static @Nullable Toko tokoLapak(MinecraftServer server, long situs, int no) {
		CompoundTag t = tag(server, situs, "l" + no);
		return t == null ? null : toko(t);
	}

	/** Every shop {@code pemilik} has (all places). */
	public static List<Toko> milik(MinecraftServer server, UUID pemilik) {
		List<Toko> out = new ArrayList<>();
		for (CompoundTag t : semua(server)) {
			if (pemilik.equals(pemilikDari(t))) {
				out.add(toko(t));
			}
		}
		return out;
	}

	/** The raw state of a shop for the screen builder (read only; ids as in the class comment). */
	public static @Nullable CompoundTag data(MinecraftServer server, Toko toko) {
		CompoundTag t = tagDi(server.overworld(), toko.pos());
		return t == null ? null : t.copy();
	}

	/** The box goods are counted in: the house box or the stall claim (null when the house/stall is gone). */
	public static @Nullable BoundingBox kotak(MinecraftServer server, long situs, String id) {
		if (id.startsWith("r")) {
			RumahKota.Info info = RumahKota.info(server, situs, angka(id));
			return info == null ? null : info.kotak();
		}
		LapakKota.Lapak l = LapakKota.lapak(server, situs, angka(id));
		return l == null ? null : l.kotak();
	}

	/** The owner's racks of a shop (loaded chunks only), at most {@link #RAK_MAKS}. */
	public static List<RakDagangBlockEntity> rak(ServerLevel level, Toko toko) {
		CompoundTag t = tagDi(level, toko.pos());
		return t == null ? List.of() : rak(level, t);
	}

	private static List<RakDagangBlockEntity> rak(ServerLevel level, CompoundTag t) {
		BoundingBox box = kotak(level.getServer(), t.getLongOr("s", 0L), t.getStringOr("id", ""));
		UUID owner = pemilikDari(t);
		List<RakDagangBlockEntity> out = new ArrayList<>();
		if (box == null || owner == null || !dimuat(level, box)) {
			return out;
		}
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockEntity(pos) instanceof RakDagangBlockEntity rak && owner.equals(rak.pemilik())) {
				out.add(rak);
				if (out.size() >= RAK_MAKS) {
					break;
				}
			}
		}
		return out;
	}

	// ================================================================== opening and closing

	/** The owner used a cashier counter inside his rumah toko: opens the shop there (or shows it). */
	static void kasirDipakai(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		CompoundTag t = tagDi(level, pos);
		if (t != null) {
			if (player.getUUID().equals(pemilikDari(t))) {
				t.putLong("kp", pos.asLong());
				t.putInt("b", Isian.pita(player));
				KotaEkonomiData.get(server).ubah();
				if (player.isShiftKeyDown()) {
					ambilLaci(player, toko(t));
				} else {
					ringkas(player, toko(t));
				}
			} else {
				ServerPlayer owner = server.getPlayerList().getPlayer(pemilikDari(t));
				Msg.chat(player, Component.translatable("kultivator.kota42.toko.milik_orang", t.getStringOr("nm", ""),
						owner != null ? owner.getName() : Component.translatable("kultivator.kota42.toko.pemilik_jauh")).withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (!KotaConfig.ekonomi().aktif || !KotaConfig.ekonomi().toko) {
			gagal(player, "mati");
			return;
		}
		RumahKota.Rumah rumah = RumahKota.rumahDi(level, pos);
		if (rumah == null || !player.getUUID().equals(rumah.pemilik()) || "segel".equals(rumah.jenis())) {
			gagal(player, "bukan_rumah");
			return;
		}
		RumahKota.Info info = RumahKota.info(server, rumah.situs(), rumah.id());
		if (info == null || !TJ_RUMAH_TOKO.equals(info.ukuran())) {
			gagal(player, "bukan_rumah_toko");
			return;
		}
		CompoundTag baru = buat(server, rumah.situs(), "r" + rumah.id(), TJ_RUMAH_TOKO, player);
		baru.putLong("kp", pos.asLong());
		KotaEkonomiData.get(server).ubah();
		Advancements.grant(player, "tempat/kota42_toko");
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.dibuka", baru.getStringOr("nm", "")).withStyle(ChatFormatting.GOLD));
	}

	/** A stall was rented: its shop opens with it. */
	static void bukaLapak(MinecraftServer server, long situs, int no, ServerPlayer player) {
		if (tag(server, situs, "l" + no) == null) {
			buat(server, situs, "l" + no, TJ_LAPAK, player);
			KotaEkonomiData.get(server).ubah();
		}
	}

	/** A stall lease ended: its shop closes (racks stay where they are; the till is paid out to the owner when online). */
	static void tutupLapak(MinecraftServer server, long situs, int no) {
		tutup(server, situs, "l" + no);
	}

	/** A house was released: its shop closes. */
	static void rumahDilepas(MinecraftServer server, long situs, int rumah) {
		tutup(server, situs, "r" + rumah);
	}

	/** A house was sealed (rent ran out, sold, tax unpaid): its shop stops selling until the owner opens it again. */
	static void rumahDisegel(MinecraftServer server, long situs, int rumah) {
		CompoundTag t = tag(server, situs, "r" + rumah);
		if (t != null && t.getBooleanOr("buka", true)) {
			t.putBoolean("buka", false);
			KotaEkonomiData.get(server).ubah();
		}
	}

	private static void tutup(MinecraftServer server, long situs, String id) {
		CompoundTag all = semuaTag(server, situs);
		if (!all.contains(id)) {
			return;
		}
		CompoundTag t = all.getCompoundOrEmpty(id);
		UUID owner = pemilikDari(t);
		long laci = t.getLongOr("laci", 0L);
		if (laci > 0L && owner != null) {
			// the till is never lost: it waits in the owner's ledger purse (paid out when he next opens any shop screen)
			CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.TOKO);
			CompoundTag tunda = part.getCompoundOrEmpty("tunda");
			tunda.putLong(owner.toString(), tunda.getLongOr(owner.toString(), 0L) + laci);
			part.put("tunda", tunda);
		}
		KultivatorNpc body = TUBUH.remove(situs + ":" + id);
		if (body != null) {
			body.vanish();
		}
		all.remove(id);
		KotaEkonomiData.get(server).ubah();
	}

	private static CompoundTag buat(MinecraftServer server, long situs, String id, String jenis, ServerPlayer player) {
		CompoundTag all = semuaTag(server, situs);
		CompoundTag t = new CompoundTag();
		t.putString("id", id);
		t.putLong("s", situs);
		t.store("o", UUIDUtil.CODEC, player.getUUID());
		t.putString("j", jenis);
		t.putString("nm", potong(Component.translatable("kultivator.kota42.toko.nama_bawaan", player.getName()).getString()));
		t.putInt("rep", REP_AWAL);
		t.putBoolean("buka", true);
		t.putInt("b", Isian.pita(player));
		t.putLong("jam", StokKota.jamDunia(server));
		t.putLong("hr", KotaHidup.hari(server));
		all.put(id, t);
		return t;
	}

	// ================================================================== the owner's actions

	/** {@code ambil_laci}: the till (and any till of a closed shop) goes to the owner. */
	public static void ambilLaci(ServerPlayer player, Toko toko) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = tagDi(server.overworld(), toko.pos());
		long laci = t == null || !player.getUUID().equals(pemilikDari(t)) ? 0L : t.getLongOr("laci", 0L);
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.TOKO);
		CompoundTag tunda = part.getCompoundOrEmpty("tunda");
		laci += tunda.getLongOr(player.getUUID().toString(), 0L);
		if (laci <= 0L) {
			gagal(player, "laci_kosong");
			return;
		}
		if (t != null) {
			t.putLong("laci", 0L);
		}
		tunda.remove(player.getUUID().toString());
		part.put("tunda", tunda);
		KotaEkonomiData.get(server).ubah();
		EkonomiApi.beri(player, laci, "toko_laci");
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.PLAYERS, 0.8F, 1.4F);
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.laci", NilaiBatu.text(laci)).withStyle(ChatFormatting.GOLD));
	}

	/** The Tukang's list prices of the three shop blocks in NB (42.6.2): rack, cashier counter, name board. */
	public static final long PERABOT_RAK = 20L;
	public static final long PERABOT_KASIR = 30L;
	public static final long PERABOT_PAPAN = 15L;

	/**
	 * {@code perabot:rak|kasir|papan}: a Tukang within 8 blocks sells one shop block at its list price times the town's
	 * building-material price ({@code HargaKota.faktor(BAHAN)}); the money goes to the town purse (a sink). The same blocks
	 * can be crafted (data recipes), so this is the paid shortcut.
	 */
	public static void beliPerabot(ServerPlayer player, String jenis) {
		net.minecraft.world.item.Item item;
		long dasar;
		switch (jenis) {
			case "rak" -> {
				item = KotaEkonomiBlocks.RAK_DAGANG_ITEM;
				dasar = PERABOT_RAK;
			}
			case "kasir" -> {
				item = KotaEkonomiBlocks.MEJA_KASIR_ITEM;
				dasar = PERABOT_KASIR;
			}
			case "papan" -> {
				item = KotaEkonomiBlocks.PAPAN_NAMA_TOKO_ITEM;
				dasar = PERABOT_PAPAN;
			}
			default -> {
				return;
			}
		}
		MinecraftServer server = player.level().getServer();
		KultivatorNpc tukang = null;
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(8.0D), KultivatorNpc::isAlive)) {
			if (npc.role() == NpcRole.TUKANG) {
				tukang = npc;
				break;
			}
		}
		if (tukang == null) {
			gagal(player, "tanpa_tukang");
			return;
		}
		long situs = HargaKota.situs(player, tukang);
		long harga = Barang.harga(dasar, HargaKota.faktor(server, situs, Kategori.BAHAN));
		if (!EkonomiApi.bayar(player, harga, "toko_perabot")) {
			gagal(player, "uang", NilaiBatu.text(harga));
			return;
		}
		if (situs != 0L) {
			KasKota.tambah(server, situs, harga);
		}
		ItemStack barang = new ItemStack(item);
		Barang.beri(player, barang);
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.perabot", barang.getHoverName(), NilaiBatu.text(harga)).withStyle(ChatFormatting.GOLD));
	}

	/** Whether the shop's takings go straight into the owner's Rumah Uang deposit (screen field {@code ke_bank}). */
	public static boolean keBank(MinecraftServer server, Toko toko) {
		CompoundTag t = tagDi(server.overworld(), toko.pos());
		return t != null && t.getBooleanOr("kb", false);
	}

	/**
	 * {@code ke_bank:0|1}: from now on sales are paid into the Rumah Uang deposit (no fee, even while the owner is away);
	 * what already waits in the till stays there. The deposit is the same at every Rumah Uang (38.M.2.2).
	 */
	public static void keBank(ServerPlayer player, Toko toko, boolean ya) {
		CompoundTag t = milikTag(player, toko);
		if (t == null) {
			return;
		}
		t.putBoolean("kb", ya);
		KotaEkonomiData.get(player.level().getServer()).ubah();
		Msg.chat(player, Component.translatable(ya ? "kultivator.kota42.toko.ke_bank_ya" : "kultivator.kota42.toko.ke_bank_tidak").withStyle(ChatFormatting.GOLD));
	}

	/** {@code buka:0|1}. */
	public static void buka(ServerPlayer player, Toko toko, boolean buka) {
		CompoundTag t = milikTag(player, toko);
		if (t != null) {
			if (buka && RumahKota.rumahDi(player.level(), toko.pos()) instanceof RumahKota.Rumah rumah && RumahKota.ST_SEGEL.equals(rumah.jenis())) {
				gagal(player, "segel");
				return;
			}
			t.putBoolean("buka", buka);
			KotaEkonomiData.get(player.level().getServer()).ubah();
			Msg.chat(player, Component.translatable(buka ? "kultivator.kota42.toko.buka" : "kultivator.kota42.toko.tutup").withStyle(ChatFormatting.GOLD));
		}
	}

	/** {@code nama:TEKS} (at most 24 characters; the shop signs in the box show the first 16). */
	public static void nama(ServerPlayer player, Toko toko, String nama) {
		CompoundTag t = milikTag(player, toko);
		String bersih = potong(nama);
		if (t == null || bersih.isBlank()) {
			return;
		}
		t.putString("nm", bersih);
		KotaEkonomiData.get(player.level().getServer()).ubah();
		BoundingBox box = kotak(player.level().getServer(), toko.situs(), t.getStringOr("id", ""));
		ServerLevel level = player.level();
		if (box != null && dimuat(level, box)) {
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY() - 1, box.minZ(), box.maxX(), box.maxY() + 1, box.maxZ())) {
				if (level.getBlockEntity(pos) instanceof PapanNamaTokoBlock.Isi papan && player.getUUID().equals(papan.pemilik())) {
					papan.setNama(bersih);
				}
			}
		}
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.nama", bersih).withStyle(ChatFormatting.GOLD));
	}

	/** {@code harga:RAK:SLOT:NB}: sets one shelf's asking price per item (capped at 1.5 x value). */
	public static void harga(ServerPlayer player, RakDagangBlockEntity rak, int slot, long nb) {
		if (!player.getUUID().equals(rak.pemilik())) {
			return;
		}
		ItemStack stack = rak.barang(slot);
		if (stack.isEmpty()) {
			return;
		}
		long maks = hargaMaks(stack);
		long set = Mth.clamp(nb, 1L, maks);
		rak.setHarga(slot, (int) Math.min(RakDagangBlockEntity.HARGA_MAKS, set));
		Msg.actionBar(player, Component.translatable(set < nb ? "kultivator.kota42.toko.harga_maks" : "kultivator.kota42.toko.harga",
				stack.getHoverName(), NilaiBatu.text(set)).withStyle(ChatFormatting.GOLD));
	}

	/** {@code harga_pasar:RAK:SLOT}: the shelf's price becomes the town price. */
	public static void hargaPasar(ServerPlayer player, RakDagangBlockEntity rak, int slot) {
		ItemStack stack = rak.barang(slot);
		if (!stack.isEmpty()) {
			harga(player, rak, slot, hargaKota(player.level().getServer(), situsDari(rak), stack));
		}
	}

	/** {@code pekerjakan:WARGA}: hires an unemployed resident as clerk (first week paid now). */
	public static void pekerjakan(ServerPlayer player, Toko toko, int wargaId) {
		CompoundTag t = milikTag(player, toko);
		MinecraftServer server = player.level().getServer();
		if (t == null) {
			return;
		}
		Warga w = WargaApi.byId(wargaId);
		if (w == null || !w.hidup() || w.staf != 0 || !RumahKota.menganggur(w) || w.tujuanSitus != 0L && w.tujuanSitus != toko.situs()) {
			gagal(player, "bukan_calon");
			return;
		}
		if (t.getIntOr("pg", 0) != 0 || t.contains("pgm")) {
			gagal(player, "sudah_ada");
			return;
		}
		long gaji = gaji(server, toko);
		if (!EkonomiApi.bayar(player, gaji, "toko_pegawai")) {
			gagal(player, "uang", NilaiBatu.text(gaji));
			return;
		}
		t.putInt("pg", wargaId);
		t.putInt("ahli", w.ahli);
		t.putInt("wt", Math.floorMod(w.watak, Tokoh.WATAK.size()));
		t.putLong("pgs", KotaHidup.hari(server) + 7L);
		KotaEkonomiData.get(server).ubah();
		WargaApi.pekerjakan(w, player.getUUID(), "toko");
		BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_GAJI, 0, -gaji, "kultivator.kota42.toko.buku.gaji", t.getStringOr("nm", ""));
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.pegawai", WargaApi.nama(w), Component.translatable(
				"kultivator.kota42.watak." + Tokoh.WATAK.get(t.getIntOr("wt", 0))), NilaiBatu.text(gaji)).withStyle(ChatFormatting.GOLD));
	}

	/** {@code pekerjakan_murid:SOUL}: the nearest following disciple minds the shop (no NB wage; guards at night). */
	public static void pekerjakanMurid(ServerPlayer player, Toko toko) {
		CompoundTag t = milikTag(player, toko);
		if (t == null) {
			return;
		}
		if (t.getIntOr("pg", 0) != 0 || t.contains("pgm")) {
			gagal(player, "sudah_ada");
			return;
		}
		DiscipleEntity best = null;
		double bestSq = Disciples.ACTION_RANGE * Disciples.ACTION_RANGE;
		for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
			double sq = d.distanceToSqr(player);
			if (d.role() == DiscipleEntity.Role.DISCIPLE && d.level() == player.level() && sq < bestSq) {
				bestSq = sq;
				best = d;
			}
		}
		if (best == null) {
			gagal(player, "tanpa_murid");
			return;
		}
		MinecraftServer server = player.level().getServer();
		t.store("pgm", UUIDUtil.CODEC, best.soulId());
		t.putString("pgn", best.getDisplayName().getString());
		t.putInt("ahli", 30);
		t.putInt("wt", Tokoh.WATAK.indexOf("jujur"));
		t.putLong("pgs", KotaHidup.hari(server) + 7L);
		KotaEkonomiData.get(server).ubah();
		CompoundTag info = new CompoundTag();
		info.putString("toko", t.getStringOr("id", ""));
		Halls.storeAway(player.getUUID(), best, Barang.waktu(server) + 7L * 24000L, MURID_ALASAN, info);
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.murid", t.getStringOr("pgn", "")).withStyle(ChatFormatting.GOLD));
	}

	/** {@code berhentikan:ID}: the clerk goes (a disciple comes home when his week ends). */
	public static void berhentikan(ServerPlayer player, Toko toko) {
		CompoundTag t = milikTag(player, toko);
		if (t == null || t.getIntOr("pg", 0) == 0 && !t.contains("pgm")) {
			gagal(player, "tanpa_pegawai");
			return;
		}
		lepasPegawai(player.level().getServer(), t);
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.pegawai_pergi").withStyle(ChatFormatting.GRAY));
	}

	private static void lepasPegawai(MinecraftServer server, CompoundTag t) {
		t.remove("pg");
		t.remove("pgm");
		t.remove("pgn");
		t.remove("pgs");
		t.remove("wt");
		KultivatorNpc body = TUBUH.remove(t.getLongOr("s", 0L) + ":" + t.getStringOr("id", ""));
		if (body != null) {
			body.vanish();
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** Weekly clerk wage by place kind (as household help: 8/10/12/15 NB). */
	public static long gaji(MinecraftServer server, Toko toko) {
		TempatInfo t = TempatApi.tempatDi(server.overworld(), toko.pos());
		double f = t == null ? 1.0D : RumahKota.faktorJenis(t.jenis(), t.distrik() == null ? "" : t.distrik());
		long nb = f <= 0.5D ? 8L : f <= 0.6D ? 10L : f < 2.0D ? 12L : 15L;
		return Math.max(1L, Math.round(nb * KotaConfig.ekonomi().gajiSkala));
	}

	// ================================================================== racks in the world

	/**
	 * A rack was used. Owner: an item in hand goes on the aimed shelf (price = town price), empty hand takes the shelf's
	 * stack back, sneaking with an empty hand cycles the price 80-150 % of the town price. Anyone else buys one item.
	 */
	static void rakDipakai(ServerPlayer player, RakDagangBlockEntity rak, int slot, ItemStack tangan) {
		MinecraftServer server = player.level().getServer();
		if (rak.pemilik() == null) {
			rak.setPemilik(player.getUUID());
		}
		if (player.getUUID().equals(rak.pemilik())) {
			ItemStack ada = rak.barang(slot);
			if (!tangan.isEmpty()) {
				if (!ada.isEmpty() && !ItemStack.isSameItemSameComponents(ada, tangan)) {
					gagal(player, "rak_terisi");
					return;
				}
				if (!Barang.bolehDijual(tangan) || EkonomiApi.harga(tangan) <= 0L) {
					gagal(player, "tak_laku");
					return;
				}
				if (Barang.baruDibeli(tangan, Barang.hari(server))) {
					Msg.actionBar(player, Component.translatable("kultivator.tempat_ekonomi.asal_toko", Barang.ASAL_HARI).withStyle(ChatFormatting.RED));
					return;
				}
				int room = ada.isEmpty() ? tangan.getCount() : Math.min(tangan.getCount(), ada.getMaxStackSize() - ada.getCount());
				if (room <= 0) {
					return;
				}
				ItemStack baru = ada.isEmpty() ? tangan.copyWithCount(room) : ada.copyWithCount(ada.getCount() + room);
				tangan.shrink(room);
				rak.taruh(slot, baru);
				if (ada.isEmpty() || rak.harga(slot) <= 0) {
					rak.setHarga(slot, (int) Math.min(RakDagangBlockEntity.HARGA_MAKS, hargaKota(server, situsDari(rak), baru)));
				}
				Msg.actionBar(player, Component.translatable("kultivator.kota42.toko.ditaruh", baru.getHoverName(), baru.getCount(),
						NilaiBatu.text(rak.harga(slot))).withStyle(ChatFormatting.GOLD));
				return;
			}
			if (ada.isEmpty()) {
				return;
			}
			if (player.isShiftKeyDown()) {
				long pasar = hargaKota(server, situsDari(rak), ada);
				int now = 0;
				for (int i = 0; i < PRESET.length; i++) {
					if (Math.round(pasar * PRESET[i]) <= rak.harga(slot)) {
						now = i;
					}
				}
				long next = Math.max(1L, Math.round(pasar * PRESET[(now + 1) % PRESET.length]));
				harga(player, rak, slot, next);
				return;
			}
			Barang.beri(player, rak.ambil(slot));
			rak.setHarga(slot, 0);
			return;
		}
		beliLangsung(player, rak, slot);
	}

	/** Another player buys one item off a shelf: the price goes to the owner's till (5 % tax to the town purse). */
	private static void beliLangsung(ServerPlayer player, RakDagangBlockEntity rak, int slot) {
		ItemStack ada = rak.barang(slot);
		int harga = rak.harga(slot);
		if (ada.isEmpty() || harga <= 0) {
			return;
		}
		ServerLevel level = player.level();
		CompoundTag t = tagDi(level, rak.getBlockPos());
		if (t == null || !t.getBooleanOr("buka", true)) {
			gagal(player, "tutup");
			return;
		}
		if (!EkonomiApi.bayar(player, harga, "toko_pemain")) {
			gagal(player, "uang", NilaiBatu.text(harga));
			return;
		}
		ItemStack satu = ada.copyWithCount(1);
		rak.taruh(slot, ada.copyWithCount(ada.getCount() - 1));
		Barang.beri(player, satu);
		catatJual(level.getServer(), t, satu, harga, 1, false, KotaHidup.hari(level.getServer()));
		level.playSound(null, rak.getBlockPos(), SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.BLOCKS, 0.7F, 1.6F);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.toko.dibeli", satu.getHoverName(), NilaiBatu.text(harga), t.getStringOr("nm", ""))
				.withStyle(ChatFormatting.GOLD));
	}

	/** The highest asking price of one item of {@code stack} (1.5 x value). */
	public static long hargaMaks(ItemStack stack) {
		return Math.max(1L, (long) Math.floor(EkonomiApi.harga(stack.copyWithCount(1)) * HARGA_MAKS));
	}

	/** The town price of one item of {@code stack} at {@code situs}. */
	public static long hargaKota(MinecraftServer server, long situs, ItemStack stack) {
		return situs == 0L ? Math.max(1L, EkonomiApi.harga(stack.copyWithCount(1))) : Math.max(1L, HargaKota.harga(server, situs, stack.copyWithCount(1)));
	}

	/** The 38.M.4.1 sale chance (%) of one shelf now: h = asking / town price. */
	public static int peluang(MinecraftServer server, long situs, ItemStack stack, int harga) {
		long pasar = hargaKota(server, situs, stack);
		return LapakSekte.peluangLaku(harga / (double) pasar);
	}

	private static long situsDari(RakDagangBlockEntity rak) {
		if (!(rak.getLevel() instanceof ServerLevel level)) {
			return 0L;
		}
		TempatInfo t = TempatApi.tempatDi(level, rak.getBlockPos());
		return t == null ? 0L : t.situsId();
	}

	// ================================================================== the hourly market

	/** From {@code KotaEkonomi} every 5 s: shops whose box is loaded run the world hours they missed (<= 24). */
	static void tick(MinecraftServer server) {
		if (!KotaConfig.ekonomi().aktif || !KotaConfig.ekonomi().toko) {
			return;
		}
		ServerLevel level = server.overworld();
		long jam = StokKota.jamDunia(server);
		Set<String> wanted = new HashSet<>();
		Map<Long, List<BlockPos>> depan = new HashMap<>();
		for (CompoundTag t : semua(server)) {
			long situs = t.getLongOr("s", 0L);
			String id = t.getStringOr("id", "");
			BoundingBox box = kotak(server, situs, id);
			if (box == null) {
				continue;
			}
			if (dimuat(level, box)) {
				long last = t.getLongOr("jam", jam);
				if (jam > last) {
					for (long h = Math.max(last + 1L, jam - 24L); h <= jam; h++) {
						satuJam(level, t, box, h, h == jam);
					}
					t.putLong("jam", jam);
					KotaEkonomiData.get(server).ubah();
				}
				boolean dekat = level.getNearestPlayer(box.getCenter().getX(), box.getCenter().getY(), box.getCenter().getZ(), HADIR_JARAK, false) != null;
				if (dekat && pegawaiAda(t)) {
					wanted.add(situs + ":" + id);
					tubuh(level, t, situs, id);
				}
				if (dekat && t.getBooleanOr("buka", true)) {
					depan.computeIfAbsent(situs, k -> new ArrayList<>()).add(depan(level, t, situs, id));
				}
			}
		}
		TUBUH.entrySet().removeIf(e -> {
			if (!wanted.contains(e.getKey()) || e.getValue().isRemoved()) {
				e.getValue().vanish();
				return true;
			}
			return false;
		});
		DEPAN = Map.copyOf(depan);
	}

	/** Where a shopper stands: two blocks before the counter (customer side), else before the stall. */
	private static BlockPos depan(ServerLevel level, CompoundTag t, long situs, String id) {
		long kp = t.getLongOr("kp", 0L);
		if (kp != 0L) {
			BlockPos counter = BlockPos.of(kp);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(counter);
			return state.getBlock() instanceof MejaKasirBlock ? counter.relative(state.getValue(MejaKasirBlock.FACING), 2) : counter;
		}
		LapakKota.Lapak l = LapakKota.lapak(level.getServer(), situs, angka(id));
		return l == null ? BlockPos.ZERO : l.pos().relative(l.hadap(), 2);
	}

	private static void satuJam(ServerLevel level, CompoundTag t, BoundingBox box, long jamDunia, boolean sekarang) {
		MinecraftServer server = level.getServer();
		long situs = t.getLongOr("s", 0L);
		long hari = Math.floorDiv(jamDunia, 24L);
		if (hari != t.getLongOr("hr", hari)) {
			hariToko(server, level, t, box, t.getLongOr("hr", hari));
			t.putLong("hr", hari);
		}
		int jam = (int) Math.floorMod(jamDunia + 6L, 24L);
		String watak = watak(t);
		int tutup = "sombong".equals(watak) ? 18 : 20;
		if (!t.getBooleanOr("buka", true) || jam < 8 || jam >= tutup) {
			return;
		}
		UUID owner = pemilikDari(t);
		boolean hadir = sekarang && owner != null && level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p && p.level() == level
				&& p.blockPosition().closerThan(box.getCenter(), HADIR_JARAK);
		boolean kasir = t.getLongOr("kp", 0L) != 0L || TJ_LAPAK.equals(t.getStringOr("j", ""));
		if (!hadir && !(kasir && pegawaiAda(t))) {
			return;
		}
		List<RakDagangBlockEntity> racks = rak(level, t);
		if (racks.isEmpty()) {
			return;
		}
		RandomSource random = RandomSource.create(situs * 341873128712L ^ jamDunia * 0x9E3779B97F4A7C15L ^ t.getStringOr("id", "").hashCode());
		int pembeli = pembeli(server, level, t, box, jam, random);
		if (pembeli <= 0) {
			return;
		}
		long batas = batasHarian(t);
		int bonus = switch (watak) {
			case "ramah" -> 10;
			case "licik", "serakah" -> 5;
			default -> 0;
		} + (pegawaiAda(t) ? t.getIntOr("ahli", 0) / 5 : 0);
		lapor(t, hari, pembeli, 0, 0L);
		for (int b = 0; b < pembeli; b++) {
			if (t.getLongOr("tp", 0L) >= batas) {
				break;
			}
			// the buyer looks over the shelves and picks by what the town wants
			List<int[]> pilihan = new ArrayList<>();
			double total = 0.0D;
			double[] bobot = new double[racks.size() * RakDagangBlockEntity.SLOT];
			for (int r = 0; r < racks.size(); r++) {
				for (int s = 0; s < RakDagangBlockEntity.SLOT; s++) {
					ItemStack st = racks.get(r).barang(s);
					if (!st.isEmpty() && racks.get(r).harga(s) > 0) {
						double w = KafilahSekte.permintaan(server, situs, st);
						bobot[r * RakDagangBlockEntity.SLOT + s] = w;
						total += w;
						pilihan.add(new int[] {r, s});
					}
				}
			}
			if (pilihan.isEmpty() || total <= 0.0D) {
				break;
			}
			double roll = random.nextDouble() * total;
			int[] pick = pilihan.get(pilihan.size() - 1);
			for (int[] p : pilihan) {
				roll -= bobot[p[0] * RakDagangBlockEntity.SLOT + p[1]];
				if (roll <= 0.0D) {
					pick = p;
					break;
				}
			}
			RakDagangBlockEntity rak = racks.get(pick[0]);
			ItemStack st = rak.barang(pick[1]);
			int harga = rak.harga(pick[1]);
			int chance = peluang(server, situs, st, harga);
			if (chance <= 0 || random.nextInt(100) >= Math.min(95, chance + bonus)) {
				continue;
			}
			int qty = Math.min(st.getCount(), 1 + (random.nextInt(4) == 0 ? random.nextInt(3) : 0));
			while (qty > 1 && t.getLongOr("tp", 0L) + (long) harga * qty > batas) {
				qty--;
			}
			if (t.getLongOr("tp", 0L) + harga > batas) {
				break;
			}
			ItemStack sold = st.copyWithCount(qty);
			rak.taruh(pick[1], st.copyWithCount(st.getCount() - qty));
			catatJual(server, t, sold, (long) harga * qty, qty, true, hari);
			if (sekarang) {
				level.playSound(null, rak.getBlockPos(), SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.BLOCKS, 0.5F, 1.5F);
			}
		}
	}

	/** Buyers this hour (42.6.2 formula, scaled to one shop: a quarter-full market sends about 2-4 an hour). */
	private static int pembeli(MinecraftServer server, ServerLevel level, CompoundTag t, BoundingBox box, int jam, RandomSource random) {
		long situs = t.getLongOr("s", 0L);
		int pop = Math.max(StokKota.penduduk(server, situs), WargaApi.wargaTempat(situs).size());
		if (pop <= 0) {
			pop = 12;
		}
		TempatInfo info = TempatApi.tempatDi(level, box.getCenter());
		String distrik = info == null || info.distrik() == null ? "" : info.distrik();
		double lalu = switch (distrik) {
			case LembarKunci.DISTRIK_PASAR -> 1.0D;
			case LembarKunci.DISTRIK_PUSAT, LembarKunci.DISTRIK_GERBANG, LembarKunci.DISTRIK_GILDA -> 0.7D;
			case LembarKunci.DISTRIK_HUNIAN, LembarKunci.DISTRIK_KUMUH, LembarKunci.DISTRIK_LUAR -> 0.3D;
			default -> TJ_LAPAK.equals(t.getStringOr("j", "")) ? 1.0D : 0.6D;
		};
		double kurva = jam >= 9 && jam <= 11 || jam >= 17 && jam <= 19 ? 1.0D : 0.55D;
		double suasana = 1.0D + WargaApi.suasanaTempat(situs) / 200.0D;
		double rep = t.getIntOr("rep", REP_AWAL) / 50.0D;
		double n = pop * lalu * kurva * suasana * rep * 0.06D;
		int whole = (int) n;
		return Math.min(12, whole + (random.nextDouble() < n - whole ? 1 : 0));
	}

	/** Daily cap of a shop: {@code tokoBatas x (b+1)} NB (rumah toko x1.5). */
	public static long batasHarian(CompoundTag t) {
		long cap = (long) KotaConfig.ekonomi().tokoBatas * (t.getIntOr("b", 0) + 1);
		return TJ_RUMAH_TOKO.equals(t.getStringOr("j", "")) ? Math.round(cap * 1.5D) : cap;
	}

	private static void catatJual(MinecraftServer server, CompoundTag t, ItemStack sold, long gross, int qty, boolean warga, long hari) {
		long situs = t.getLongOr("s", 0L);
		long tax = Math.max(1L, Math.round(gross * PAJAK));
		long net = gross - tax;
		UUID owner = pemilikDari(t);
		t.putLong("tp", t.getLongOr("tp", 0L) + gross);
		if (owner != null && t.getBooleanOr("kb", false)) {
			RumahUang.masukkan(server, owner, net, "kultivator.kota42.bank.riwayat.toko", t.getStringOr("nm", ""));
		} else {
			t.putLong("laci", t.getLongOr("laci", 0L) + net);
		}
		if (warga && owner != null) {
			// resident buyers pay out of thin air: that is the 42.6.0 faucet "penjualan toko" (a player buying is only a transfer)
			Investasi.catatKeran(server, owner, Investasi.K_TOKO, net);
		}
		t.putLong("dnb", t.getLongOr("dnb", 0L) + net);
		t.putLong("dtx", t.getLongOr("dtx", 0L) + tax);
		t.putInt("djl", t.getIntOr("djl", 0) + qty);
		t.putInt("total", t.getIntOr("total", 0) + qty);
		long minggu = Math.floorDiv(hari, 7L);
		if (t.getLongOr("mg", minggu) != minggu) {
			t.putLong("mgnb", 0L);
			t.put("top", new CompoundTag());
		}
		t.putLong("mg", minggu);
		t.putLong("mgnb", t.getLongOr("mgnb", 0L) + net);
		CompoundTag top = t.getCompoundOrEmpty("top");
		String key = BuiltInRegistries.ITEM.getKey(sold.getItem()).toString();
		int[] row = top.getIntArray(key).orElse(new int[2]);
		if (row.length < 2) {
			row = new int[2];
		}
		row[0] += qty;
		row[1] = (int) Math.min(Integer.MAX_VALUE, row[1] + net);
		top.putIntArray(key, row);
		t.put("top", top);
		lapor(t, hari, 0, qty, net);
		KasKota.pajak(server, situs, tax, "toko");
		if (warga) {
			HargaKota.catatJual(server, situs, Kategori.dari(sold), qty);
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** Adds to today's report row (7 rows kept). */
	private static void lapor(CompoundTag t, long hari, int pembeli, int terjual, long nb) {
		ListTag lap = t.getListOrEmpty("lap");
		CompoundTag row = null;
		for (Tag each : lap) {
			if (each instanceof CompoundTag c && c.getLongOr("h", -1L) == hari) {
				row = c;
			}
		}
		if (row == null) {
			row = new CompoundTag();
			row.putLong("h", hari);
			lap.add(row);
			while (lap.size() > 7) {
				lap.remove(0);
			}
		}
		row.putInt("pb", row.getIntOr("pb", 0) + pembeli);
		row.putInt("tj", row.getIntOr("tj", 0) + terjual);
		row.putLong("nb", row.getLongOr("nb", 0L) + nb);
		t.put("lap", lap);
	}

	/** The end of a shop day: ledger line, reputation, wages, theft and skill. */
	private static void hariToko(MinecraftServer server, ServerLevel level, CompoundTag t, BoundingBox box, long hari) {
		UUID owner = pemilikDari(t);
		long situs = t.getLongOr("s", 0L);
		long net = t.getLongOr("dnb", 0L);
		int sold = t.getIntOr("djl", 0);
		if (owner != null && net > 0L) {
			BukuKas.catat(server, owner, LembarKunci.BukuKas.SUMBER_LAPAK, 0, net, "kultivator.kota42.toko.buku.hari", t.getStringOr("nm", ""), sold,
					t.getLongOr("dtx", 0L));
			ServerPlayer online = server.getPlayerList().getPlayer(owner);
			if (online != null) {
				Msg.chat(online, Component.translatable("kultivator.kota42.toko.laku_hari", t.getStringOr("nm", ""), sold, NilaiBatu.text(net))
						.withStyle(ChatFormatting.GOLD));
				if (t.getIntOr("total", 0) >= 50) {
					Advancements.grant(online, "tempat/kota42_toko");
				}
			}
		}
		t.putInt("sepi", sold > 0 ? 0 : t.getIntOr("sepi", 0) + 1);
		t.putLong("dnb", 0L);
		t.putLong("dtx", 0L);
		t.putInt("djl", 0);
		t.putLong("tp", 0L);
		reputasi(server, level, t, box);
		RandomSource random = RandomSource.create(situs ^ hari * 0x5DEECE66DL ^ t.getStringOr("id", "").hashCode());
		List<RakDagangBlockEntity> racks = rak(level, t);
		boolean penjaga = t.contains("pgm") || owner != null && dev.kultivator.dunia.kota42.hewan.HewanKota.dijaga(server, situs, owner);
		if (!penjaga && random.nextInt(350) == 0) {
			hilang(racks, random, t, true);
		}
		if ("licik".equals(watak(t)) && random.nextInt(140) == 0) {
			hilang(racks, random, t, false);
		}
		// due by day, not by weekday: a shop whose chunk slept through the weekday still pays its clerk
		if (pegawaiAda(t) && hari >= t.getLongOr("pgs", 0L)) {
			t.putInt("ahli", Math.min(100, t.getIntOr("ahli", 0) + 4));
			bayarPegawai(server, t, hari);
		}
		if (owner != null) {
			// the sales cap follows the owner's realm (an absent owner keeps the band last seen)
			t.putInt("b", server.getPlayerList().getPlayer(owner) instanceof ServerPlayer op ? Isian.pita(op) : Math.max(t.getIntOr("b", 0), Investasi.pitaTersimpan(server, owner)));
		}
		if (Math.floorMod(hari, 7L) == 0L) {
			t.putInt("cur", 0);
		}
	}

	private static void hilang(List<RakDagangBlockEntity> racks, RandomSource random, CompoundTag t, boolean seluruh) {
		List<int[]> full = new ArrayList<>();
		for (int r = 0; r < racks.size(); r++) {
			for (int s = 0; s < RakDagangBlockEntity.SLOT; s++) {
				if (!racks.get(r).barang(s).isEmpty()) {
					full.add(new int[] {r, s});
				}
			}
		}
		if (full.isEmpty()) {
			return;
		}
		int[] p = full.get(random.nextInt(full.size()));
		RakDagangBlockEntity rak = racks.get(p[0]);
		ItemStack st = rak.barang(p[1]);
		rak.taruh(p[1], seluruh ? ItemStack.EMPTY : st.copyWithCount(st.getCount() - 1));
		t.putInt("cur", t.getIntOr("cur", 0) + 1);
	}

	private static void reputasi(MinecraftServer server, ServerLevel level, CompoundTag t, BoundingBox box) {
		long situs = t.getLongOr("s", 0L);
		int rep = t.getIntOr("rep", REP_AWAL);
		double sumH = 0.0D;
		int n = 0;
		Set<String> ragam = new HashSet<>();
		for (RakDagangBlockEntity rak : rak(level, t)) {
			for (int s = 0; s < RakDagangBlockEntity.SLOT; s++) {
				ItemStack st = rak.barang(s);
				if (!st.isEmpty() && rak.harga(s) > 0) {
					sumH += rak.harga(s) / (double) hargaKota(server, situs, st);
					n++;
					ragam.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
				}
			}
		}
		if (n > 0) {
			double h = sumH / n;
			rep += h <= 1.1D ? 1 : h > 1.35D ? -1 : 0;
		}
		if (ragam.size() >= 6) {
			rep++;
		}
		if ("ramah".equals(watak(t))) {
			rep++;
		}
		String acara = HargaKota.acara38(situs);
		if (acara != null && acara.startsWith("festival") && lentera(level, box) >= 2) {
			rep++;
		}
		if (t.getIntOr("sepi", 0) >= 3) {
			rep--;
		}
		rep -= 10 * t.getIntOr("keluhan_baru", 0);
		t.putInt("keluhan", t.getIntOr("keluhan", 0) + t.getIntOr("keluhan_baru", 0));
		t.putInt("keluhan_baru", 0);
		t.putInt("rep", Mth.clamp(rep, 0, 100));
	}

	private static int lentera(ServerLevel level, BoundingBox box) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + 1, box.maxZ())) {
			if (level.getBlockState(pos).is(Blocks.LANTERN) || level.getBlockState(pos).is(Blocks.SOUL_LANTERN)) {
				n++;
			}
		}
		return n;
	}

	/** A buyer complains (fake or poisoned goods, 42B/42D): reputation -10 at the day's end. */
	public static void keluhan(MinecraftServer server, Toko toko) {
		CompoundTag t = tagDi(server.overworld(), toko.pos());
		if (t != null) {
			t.putInt("keluhan_baru", t.getIntOr("keluhan_baru", 0) + 1);
			KotaEkonomiData.get(server).ubah();
		}
	}

	private static void bayarPegawai(MinecraftServer server, CompoundTag t, long hari) {
		if (t.contains("pgm")) {
			t.putLong("pgs", hari + 7L);
			return;
		}
		UUID owner = pemilikDari(t);
		ServerPlayer online = owner == null ? null : server.getPlayerList().getPlayer(owner);
		Toko toko = toko(t);
		long gaji = gaji(server, toko);
		long laci = t.getLongOr("laci", 0L);
		Warga w = WargaApi.byId(t.getIntOr("pg", 0));
		boolean paid = false;
		if (w != null && w.hidup()) {
			if (laci >= gaji) {
				t.putLong("laci", laci - gaji);
				paid = true;
			} else if (online != null && EkonomiApi.bayar(online, gaji, "toko_pegawai")
					|| owner != null && Investasi.bayarLuring(server, owner, gaji, "kultivator.kota42.bank.riwayat.gaji", t.getStringOr("nm", ""))) {
				paid = true;
			}
		}
		if (paid) {
			t.putLong("pgs", hari + 7L);
			if (owner != null) {
				BukuKas.catat(server, owner, LembarKunci.BukuKas.SUMBER_GAJI, 0, -gaji, "kultivator.kota42.toko.buku.gaji", t.getStringOr("nm", ""));
			}
			return;
		}
		lepasPegawai(server, t);
		if (online != null) {
			Msg.chat(online, Component.translatable("kultivator.kota42.toko.pegawai_pergi").withStyle(ChatFormatting.GRAY));
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** The clerk's body stands behind the counter and calls out or counts on the abacus. */
	private static void tubuh(ServerLevel level, CompoundTag t, long situs, String id) {
		String key = situs + ":" + id;
		KultivatorNpc body = TUBUH.get(key);
		long kp = t.getLongOr("kp", 0L);
		BlockPos at;
		float yaw = 0.0F;
		if (kp != 0L) {
			BlockPos counter = BlockPos.of(kp);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(counter);
			if (state.getBlock() instanceof MejaKasirBlock) {
				net.minecraft.core.Direction face = state.getValue(MejaKasirBlock.FACING);
				at = counter.relative(face.getOpposite());
				yaw = face.toYRot();
			} else {
				at = counter;
			}
		} else {
			LapakKota.Lapak l = LapakKota.lapak(level.getServer(), situs, angka(id));
			if (l == null) {
				return;
			}
			at = l.pos();
			yaw = l.hadap().toYRot();
		}
		if (body == null || body.isRemoved()) {
			body = HidupApi.tubuhStaf(level, situs, NpcRole.PEDAGANG_KELILING, at, yaw);
			if (body == null) {
				return;
			}
			body.memory().putBoolean("hold", true);
			String nama = t.contains("pgm") ? t.getStringOr("pgn", "") : "";
			Warga w = WargaApi.byId(t.getIntOr("pg", 0));
			body.setCustomName(Component.translatable("kultivator.kota42.toko.nama_pegawai", w != null ? WargaApi.nama(w) : Component.literal(nama),
					t.getStringOr("nm", "")));
			TUBUH.put(key, body);
		}
		if (level.getRandom().nextInt(6) == 0) {
			boolean call = level.getRandom().nextBoolean();
			SosokApi.gerak(body, call ? GerakId.SERU_DAGANG : GerakId.HITUNG_SEMPOA, 0, 60, call ? PropId.TIDAK_ADA : PropId.SEMPOA, PropId.TIDAK_ADA,
					PropId.TIDAK_ADA);
		}
	}

	// ================================================================== text summary (until the 42U screen)

	/** Chat summary of a shop for its owner (the 42U {@code toko_pemain} screen shows the same numbers). */
	public static void ringkas(ServerPlayer player, Toko toko) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = tagDi(server.overworld(), toko.pos());
		if (t == null) {
			return;
		}
		Component pegawai = t.contains("pgm") ? Component.literal(t.getStringOr("pgn", ""))
				: WargaApi.byId(t.getIntOr("pg", 0)) instanceof Warga w ? WargaApi.nama(w) : Component.translatable("kultivator.kota42.toko.tanpa_pegawai");
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.ringkas.judul", t.getStringOr("nm", ""),
				Component.translatable(t.getBooleanOr("buka", true) ? "kultivator.kota42.toko.buka_label" : "kultivator.kota42.toko.tutup_label"))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.ringkas.isi", t.getIntOr("rep", REP_AWAL), NilaiBatu.text(t.getLongOr("laci", 0L)),
				NilaiBatu.text(t.getLongOr("tp", 0L)), NilaiBatu.text(batasHarian(t)), pegawai, rak(player.level(), t).size(), RAK_MAKS)
				.withStyle(ChatFormatting.GRAY));
		if (t.getBooleanOr("kb", false)) {
			Msg.chat(player, Component.translatable("kultivator.kota42.toko.ringkas.bank", NilaiBatu.text(RumahUang.saldo(server, player.getUUID())))
					.withStyle(ChatFormatting.GRAY));
		}
	}

	// ================================================================== helpers

	private static boolean pegawaiAda(CompoundTag t) {
		return t.getIntOr("pg", 0) != 0 || t.contains("pgm");
	}

	private static String watak(CompoundTag t) {
		if (!pegawaiAda(t)) {
			return "";
		}
		return Tokoh.WATAK.get(Math.floorMod(t.getIntOr("wt", 0), Tokoh.WATAK.size()));
	}

	private static boolean dimuat(ServerLevel level, BoundingBox box) {
		return level.isLoaded(new BlockPos(box.minX(), box.minY(), box.minZ())) && level.isLoaded(new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
	}

	private static @Nullable CompoundTag tagDi(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		MinecraftServer server = level.getServer();
		RumahKota.Rumah rumah = RumahKota.rumahDi(level, pos);
		if (rumah != null) {
			CompoundTag t = tag(server, rumah.situs(), "r" + rumah.id());
			if (t != null) {
				return t;
			}
		}
		LapakKota.Lapak l = LapakKota.di(level, pos);
		return l == null ? null : tag(server, l.situs(), "l" + l.no());
	}

	private static @Nullable CompoundTag milikTag(ServerPlayer player, Toko toko) {
		CompoundTag t = tagDi(player.level().getServer().overworld(), toko.pos());
		return t != null && player.getUUID().equals(pemilikDari(t)) ? t : null;
	}

	private static Toko toko(CompoundTag t) {
		long situs = t.getLongOr("s", 0L);
		long kp = t.getLongOr("kp", 0L);
		BlockPos pos;
		String id = t.getStringOr("id", "");
		if (kp != 0L) {
			pos = BlockPos.of(kp);
		} else {
			MinecraftServer server = KotaHidup.server();
			BoundingBox box = server == null ? null : kotak(server, situs, id);
			pos = box == null ? BlockPos.ZERO : id.startsWith("l") && server != null && LapakKota.lapak(server, situs, angka(id)) instanceof LapakKota.Lapak l
					? l.pos() : box.getCenter();
		}
		UUID owner = pemilikDari(t);
		return new Toko(owner == null ? new UUID(0L, 0L) : owner, situs, pos, t.getStringOr("nm", ""));
	}

	private static List<CompoundTag> semua(MinecraftServer server) {
		List<CompoundTag> out = new ArrayList<>();
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.TOKO);
		for (String key : part.keySet()) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			CompoundTag all = part.getCompoundOrEmpty(key).getCompoundOrEmpty("t");
			for (String id : all.keySet()) {
				out.add(all.getCompoundOrEmpty(id));
			}
		}
		return out;
	}

	private static CompoundTag semuaTag(MinecraftServer server, long situs) {
		CompoundTag d = KotaEkonomiData.get(server).situs(KotaEkonomiData.TOKO, situs);
		CompoundTag all = d.getCompoundOrEmpty("t");
		if (!d.contains("t")) {
			d.put("t", all);
		}
		return all;
	}

	private static @Nullable CompoundTag tag(MinecraftServer server, long situs, String id) {
		CompoundTag all = semuaTag(server, situs);
		return all.contains(id) ? all.getCompoundOrEmpty(id) : null;
	}

	private static @Nullable UUID pemilikDari(CompoundTag t) {
		return t.read("o", UUIDUtil.CODEC).orElse(null);
	}

	private static int angka(String id) {
		try {
			return Integer.parseInt(id.substring(1));
		} catch (RuntimeException e) {
			return -1;
		}
	}

	private static String potong(String nama) {
		String bersih = nama == null ? "" : nama.replace('\n', ' ').replace('\r', ' ').strip();
		return bersih.length() > NAMA_MAKS ? bersih.substring(0, NAMA_MAKS) : bersih;
	}

	private static void gagal(ServerPlayer player, String key, Object... args) {
		Msg.chat(player, Component.translatable("kultivator.kota42.toko.gagal." + key, args).withStyle(ChatFormatting.RED));
	}

	private TokoPemain() {
	}
}
