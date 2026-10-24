package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.tema.bersama.TemaIrama;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.util.Advancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Rumah Sutra Su (DESIGN 38.T.5.4), screen {@code tema_sutra} of Nyonya Su Jin (tabs {@code toko}, {@code jubah},
 * {@code tenun}; keys {@code TemaLayar.Sutra}) and the weaving hall:
 * <ul>
 * <li><b>toko</b>: Sutra Awan (the trade good, bought through inti's {@code Dagang}: origin price, daily stock, profit
 * book) and the Kipas Sutra (cosmetic fan, {@link #HARGA_KIPAS} NB).</li>
 * <li><b>jubah</b>: an ordered silk robe ({@link #HARGA_JUBAH} NB, ready after {@link #HARI_JUBAH} world days) in a dye
 * colour of {@link #WARNA} with a motif of {@link #MOTIF}: item {@code tema/jubah_sutra} (chest slot, no attributes) with
 * the dye as {@code dyed_color}, the motif as custom model data string and a lore line.</li>
 * <li><b>tenun</b>: the apprenticeship at a loom, once a world day: the {@code tema_irama} weaving pattern (20 s); a pass
 * gives 1-3 Sutra Awan by score.</li>
 * </ul>
 * The six looms ({@code alat_tenun} points of the hall) are made while a player is within 40 blocks: they weave 06-18
 * ({@code KEADAAN_TENUN}, cloth roll {@code nilai} = the day's progress), stand still at night. The red loom
 * ({@code varian 1}) is where Su Jin'er sits; in chain stage {@code kain} of {@code tema_air_benang} the player weaves the
 * Kain Xiu Niang there with her: three rounds, then Sutra Awan x6, Daun Teh Embun x10 and Air Suci x1 become the cloth.
 * After the chain the house gives 15 % off (flag {@code tema.air.diskon_sutra}).
 */
public final class RumahSutra {

	public static final long HARGA_KIPAS = 20L;
	public static final long HARGA_JUBAH = 80L;
	public static final int HARI_JUBAH = 2;
	public static final int HADIAH_MAKS = 3;
	public static final double DISKON_SELESAI = 0.15D;
	public static final String QUEST = "tema_air_benang";
	public static final String TAHAP_KAIN = "kain";
	public static final List<String> MOTIF = List.of("awan", "bangau", "teratai", "naga");
	public static final List<DyeColor> WARNA = List.of(DyeColor.RED, DyeColor.WHITE, DyeColor.LIGHT_BLUE, DyeColor.PINK,
			DyeColor.YELLOW, DyeColor.LIME, DyeColor.PURPLE, DyeColor.BLACK, DyeColor.CYAN, DyeColor.ORANGE);
	public static final int KAIN_SUTRA = 6;
	public static final int KAIN_DAUN = 10;
	public static final int KAIN_AIR_SUCI = 1;

	static void init() {
		TemaLayar.daftar(TemaLayar.Sutra.LAYAR, RumahSutra::bangun, RumahSutra::aksi);
	}

	// ================================================================== the looms

	/** Every 20 ticks a player is near the silk-house anchor: looms and Su Jin'er. */
	static void dekat(ServerLevel level, AirKota.Hidup kota, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		AirAnchors h = AirAnchors.HANDLER;
		double r = AirAnchors.jarakTenun();
		boolean near = !within.isEmpty() && within.getFirst().distanceToSqr(anchor) <= r * r;
		List<Titik> looms = AirAnchors.titikDekat(level, kota, "alat_tenun", anchor.blockPosition(), r + 8.0D);
		if (!near) {
			for (Titik t : looms) {
				h.hapus(anchor, "tenun_" + t.angka("urut", 0));
			}
			h.hapus(anchor, AirTokoh.SU_JINER);
			return;
		}
		int jam = AirKota.jamHari(level);
		boolean kerja = jam >= 6 && jam < 18;
		float kemajuan = kerja ? (float) Math.min(1.0D, (AirKota.jam(level) % 24000L) / 12000.0D) : 1.0F;
		for (Titik t : looms) {
			int urut = t.angka("urut", 0);
			Vec3 pos = Vec3.atBottomCenterOf(t.pos());
			AlatTenunEntity loom = h.mekanisme(level, kota, anchor, "tenun_" + urut, TemaEntities.ALAT_TENUN, pos, t.hadap().toYRot(), t.pos());
			if (loom == null) {
				continue;
			}
			int varian = t.angka("varian", 0);
			loom.setVarian(varian == 1 ? AlatTenunEntity.VARIAN_MERAH : AlatTenunEntity.VARIAN_BIASA);
			loom.setKeadaan(kerja ? AlatTenunEntity.KEADAAN_TENUN : AlatTenunEntity.KEADAAN_DIAM);
			loom.setNilai(kemajuan);
			if (varian == 1 && h.buatan(anchor, AirTokoh.SU_JINER) == null) {
				BlockPos seat = t.pos().relative(t.hadap(), 2);
				KultivatorNpc jiner = AirTokoh.panggil(level, kota.situs, AirTokoh.SU_JINER, seat, t.hadap().getOpposite().toYRot());
				if (jiner != null) {
					jiner.memory().putBoolean("hold", true);
					if (kota.town != 0L) {
						jiner.setTown(kota.town);
					}
					h.simpan(anchor, AirTokoh.SU_JINER, jiner);
				}
			}
		}
	}

	/** The anchor unloaded (bodies are removed by {@link AirAnchors}). */
	static void lepas(DuniaAnchorEntity anchor) {
	}

	/** A right-click on a loom; true when handled. */
	static boolean klikTenun(AlatTenunEntity loom, ServerPlayer player) {
		if (loom.varian() == AlatTenunEntity.VARIAN_MERAH && tahapKain(player)) {
			return mulaiKain(player, loom);
		}
		return mulaiMagang(player, -1, loom.blockPosition());
	}

	// ================================================================== the screen

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = AirLayar.npc(player, npcId);
		if (npc == null || !AirTokoh.adalah(npc, AirTokoh.SU_JIN) && !AirTokoh.adalah(npc, AirTokoh.SU_JINER)) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		AirKota.Hidup kota = AirKota.di(player);
		CompoundTag tag = AirLayar.kepala(player, "kultivator.kota_tema.air.sutra.judul", LembarKunci.GAYA_KAIN, npc);
		double diskon = diskon(player);
		if (diskon > 0.0D) {
			tag.putFloat(LembarKunci.DISKON, (float) diskon);
		}
		var reg = player.level().registryAccess();
		// toko
		ListTag toko = new ListTag();
		DagangItems.Barang sutra = DagangItems.barang("sutra_awan");
		if (sutra != null && kota != null) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, "sutra_awan");
			LembarKunci.teks(e, LembarKunci.NAMA, new ItemStack(DagangItems.SUTRA_AWAN).getHoverName());
			LembarKunci.barang(e, new ItemStack(DagangItems.SUTRA_AWAN), reg);
			long harga = hargaSutra(player, kota, sutra);
			int stok = Dagang.sisaStok(player, "sutra_awan");
			e.putLong(LembarKunci.HARGA, harga);
			e.putInt(LembarKunci.STOK, stok);
			e.putString(LembarKunci.KEADAAN, harga < 0L ? LembarKunci.K_TUTUP : stok <= 0 ? LembarKunci.K_HABIS
					: NilaiBatu.total(player) < harga ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK);
			LembarKunci.teks(e, LembarKunci.CATATAN, Component.translatable("kultivator.kota_tema.air.sutra.awan.catatan"));
			toko.add(e);
		}
		CompoundTag kipas = new CompoundTag();
		kipas.putString(LembarKunci.ID, "kipas_sutra");
		LembarKunci.teks(kipas, LembarKunci.NAMA, new ItemStack(TemaItems.KIPAS_SUTRA).getHoverName());
		LembarKunci.barang(kipas, new ItemStack(TemaItems.KIPAS_SUTRA), reg);
		long hk = harga(HARGA_KIPAS, diskon);
		kipas.putLong(LembarKunci.HARGA, hk);
		kipas.putInt(LembarKunci.STOK, -1);
		kipas.putString(LembarKunci.KEADAAN, NilaiBatu.total(player) < hk ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK);
		LembarKunci.teks(kipas, LembarKunci.CATATAN, Component.translatable("kultivator.kota_tema.air.sutra.kipas.catatan"));
		toko.add(kipas);
		tag.put(TemaLayar.Sutra.TOKO, toko);
		// jubah
		CompoundTag jubah = new CompoundTag();
		jubah.putLong(LembarKunci.HARGA, harga(HARGA_JUBAH, diskon));
		jubah.putInt(LembarKunci.HARI, HARI_JUBAH);
		int[] warna = new int[WARNA.size()];
		for (int i = 0; i < warna.length; i++) {
			warna[i] = WARNA.get(i).getId();
		}
		jubah.putIntArray(TemaLayar.Sutra.WARNA, warna);
		ListTag motif = new ListTag();
		for (String m : MOTIF) {
			motif.add(StringTag.valueOf(m));
		}
		jubah.put(TemaLayar.Sutra.MOTIF, motif);
		CompoundTag me = AirKota.pemain(server, player.getUUID());
		CompoundTag order = me.getCompoundOrEmpty("jubah");
		if (!order.isEmpty()) {
			CompoundTag p = new CompoundTag();
			p.putInt(TemaLayar.Sutra.WARNA, order.getIntOr("warna", 0));
			p.putString(TemaLayar.Sutra.MOTIF, order.getStringOr("motif", MOTIF.getFirst()));
			long sisa = order.getLongOr("siap", 0L) - AirKota.hari(server.overworld());
			p.putInt(LembarKunci.SISA_HARI, (int) Math.max(0L, sisa));
			p.putBoolean(TemaLayar.Sutra.SIAP, sisa <= 0L);
			jubah.put(TemaLayar.Sutra.PESANAN, p);
		}
		tag.put(TemaLayar.Sutra.JUBAH, jubah);
		// tenun
		CompoundTag tenun = new CompoundTag();
		boolean hariIni = me.getLongOr("magang_hari", -1L) == AirKota.hari(server.overworld());
		tenun.putBoolean(LembarKunci.BISA, !hariIni);
		if (hariIni) {
			LembarKunci.teks(tenun, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.air.sutra.tenun.besok"));
		}
		tenun.putInt(TemaLayar.Sutra.HADIAH_MAKS, HADIAH_MAKS);
		tag.put(TemaLayar.Sutra.TENUN, tenun);
		if (tahapKain(player)) {
			LembarKunci.teks(tag, LembarKunci.SUBJUDUL, Component.translatable("kultivator.kota_tema.air.sutra.kain.petunjuk"));
		}
		return tag;
	}

	static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = LembarKunci.verb(op);
		AirKota.Hidup kota = AirKota.di(player);
		if (kota == null) {
			return;
		}
		switch (verb) {
			case TemaLayar.Sutra.OP_BELI -> beli(player, kota, LembarKunci.arg(parts, 1), Math.max(1, LembarKunci.argInt(parts, 2, 1)));
			case TemaLayar.Sutra.OP_PESAN_JUBAH -> pesan(player, LembarKunci.argInt(parts, 1, 0), LembarKunci.arg(parts, 2));
			case TemaLayar.Sutra.OP_AMBIL_JUBAH -> ambil(player);
			case TemaLayar.Sutra.OP_TENUN -> mulaiMagang(player, npcId, null);
			default -> {
			}
		}
	}

	private static void beli(ServerPlayer player, AirKota.Hidup kota, String id, int n) {
		if ("kipas_sutra".equals(id)) {
			long price = harga(HARGA_KIPAS, diskon(player));
			if (!NilaiBatu.pay(player, price)) {
				AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.uang_kurang", NilaiBatu.text(price)), false);
				return;
			}
			Barang.beri(player, new ItemStack(TemaItems.KIPAS_SUTRA));
			AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.sutra.beli", new ItemStack(TemaItems.KIPAS_SUTRA).getHoverName(),
					NilaiBatu.text(price)), true);
			return;
		}
		DagangItems.Barang sutra = DagangItems.barang("sutra_awan");
		if (!"sutra_awan".equals(id) || sutra == null) {
			return;
		}
		Dagang.Hasil hasil = Dagang.beli(player, kota.situs, sutra, Math.min(16, n));
		AirLayar.pesan(player, hasil.pesan(), hasil.ok());
		if (hasil.ok()) {
			AirCerita.peristiwa(player, "air_beli_sutra", Integer.toString(hasil.jumlah()));
		}
	}

	private static void pesan(ServerPlayer player, int warna, String motif) {
		MinecraftServer server = player.level().getServer();
		CompoundTag me = AirKota.pemain(server, player.getUUID());
		if (!me.getCompoundOrEmpty("jubah").isEmpty()) {
			AirLayar.peringatan(player, Component.translatable("kultivator.kota_tema.air.sutra.jubah.ada"));
			return;
		}
		DyeColor dye = DyeColor.byId(warna);
		if (!WARNA.contains(dye) || !MOTIF.contains(motif)) {
			return;
		}
		long price = harga(HARGA_JUBAH, diskon(player));
		if (!NilaiBatu.pay(player, price)) {
			AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.uang_kurang", NilaiBatu.text(price)), false);
			return;
		}
		CompoundTag order = new CompoundTag();
		order.putInt("warna", dye.getId());
		order.putString("motif", motif);
		order.putLong("siap", AirKota.hari(server.overworld()) + HARI_JUBAH);
		me.put("jubah", order);
		AirKota.ubah(server);
		AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.sutra.jubah.dipesan",
				Component.translatable("kultivator.kota_tema.air.motif." + motif), HARI_JUBAH), true);
	}

	private static void ambil(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag me = AirKota.pemain(server, player.getUUID());
		CompoundTag order = me.getCompoundOrEmpty("jubah");
		if (order.isEmpty()) {
			return;
		}
		if (order.getLongOr("siap", 0L) > AirKota.hari(server.overworld())) {
			AirLayar.peringatan(player, Component.translatable("kultivator.kota_tema.air.sutra.jubah.belum"));
			return;
		}
		ItemStack robe = jubah(TemaItems.JUBAH_SUTRA, DyeColor.byId(order.getIntOr("warna", 0)), order.getStringOr("motif", "awan"));
		Barang.beri(player, robe);
		me.remove("jubah");
		AirKota.ubah(server);
		player.level().playSound(null, player.blockPosition(), SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
		AirLayar.pesan(player, Component.translatable("kultivator.kota_tema.air.sutra.jubah.ambil"), true);
		Advancements.grant(player, "tempat/tema_air_jubah");
	}

	/** A silk robe of {@code item} in colour {@code dye} with motif {@code motif} (made only when given). */
	public static ItemStack jubah(Item item, DyeColor dye, String motif) {
		ItemStack robe = new ItemStack(item);
		robe.set(DataComponents.DYED_COLOR, new DyedItemColor(dye.getTextureDiffuseColor() & 0xFFFFFF));
		robe.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(motif), List.of()));
		robe.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_tema.air.jubah.lore",
				Component.translatable("kultivator.kota_tema.air.motif." + motif), Component.translatable("color.minecraft." + dye.getName())))));
		return robe;
	}

	// ================================================================== weaving

	private static boolean mulaiMagang(ServerPlayer player, int npcId, @Nullable BlockPos loom) {
		MinecraftServer server = player.level().getServer();
		CompoundTag me = AirKota.pemain(server, player.getUUID());
		long hari = AirKota.hari(server.overworld());
		if (me.getLongOr("magang_hari", -1L) == hari) {
			AirLayar.peringatan(player, Component.translatable("kultivator.kota_tema.air.sutra.tenun.besok"));
			return true;
		}
		if (TemaIrama.bermain(player)) {
			return true;
		}
		return TemaIrama.mulai(player, npcId, loom, TemaLayar.Irama.POLA_TENUN, 1, 1, (p, skor, lulus, babak) -> {
			CompoundTag mine = AirKota.pemain(p.level().getServer(), p.getUUID());
			mine.putLong("magang_hari", AirKota.hari(p.level().getServer().overworld()));
			AirKota.ubah(p.level().getServer());
			if (!lulus) {
				AirLayar.peringatan(p, Component.translatable("kultivator.kota_tema.air.sutra.tenun.gagal", skor));
				return;
			}
			int dapat = Math.max(1, Math.min(HADIAH_MAKS, 1 + (skor - TemaIrama.LULUS) / 12));
			Barang.beri(p, new ItemStack(DagangItems.SUTRA_AWAN, dapat));
			p.level().playSound(null, p.blockPosition(), SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.1F);
			AirLayar.pesan(p, Component.translatable("kultivator.kota_tema.air.sutra.tenun.hadiah", skor, dapat), true);
			AirCerita.peristiwa(p, "air_tenun", Integer.toString(skor));
		});
	}

	/** True while the player stands at chain stage {@code kain} of {@code tema_air_benang}. */
	static boolean tahapKain(ServerPlayer player) {
		return QuestTracker.aktif(player, QUEST) && TAHAP_KAIN.equals(QuestTracker.simpul(player, QUEST));
	}

	private static boolean mulaiKain(ServerPlayer player, AlatTenunEntity loom) {
		if (TemaIrama.bermain(player)) {
			return true;
		}
		if (!punya(player, DagangItems.SUTRA_AWAN, KAIN_SUTRA) || !punya(player, TemaItems.DAUN_TEH_EMBUN, KAIN_DAUN)
				|| !punya(player, TemaItems.AIR_SUCI, KAIN_AIR_SUCI)) {
			AirLayar.peringatan(player, Component.translatable("kultivator.kota_tema.air.sutra.kain.bahan", KAIN_SUTRA, KAIN_DAUN, KAIN_AIR_SUCI));
			return true;
		}
		return babakKain(player, loom.blockPosition(), 1);
	}

	private static boolean babakKain(ServerPlayer player, BlockPos loom, int babak) {
		return TemaIrama.mulai(player, -1, loom, TemaLayar.Irama.POLA_TENUN, babak, 3, (p, skor, lulus, b) -> {
			if (!lulus) {
				AirLayar.peringatan(p, Component.translatable("kultivator.kota_tema.air.sutra.kain.ulang", b, skor));
				return;
			}
			if (b < 3) {
				AirLayar.pesan(p, Component.translatable("kultivator.kota_tema.air.sutra.kain.babak", b, skor), true);
				babakKain(p, loom, b + 1);
				return;
			}
			if (!ambil(p, DagangItems.SUTRA_AWAN, KAIN_SUTRA) || !ambil(p, TemaItems.DAUN_TEH_EMBUN, KAIN_DAUN)
					|| !ambil(p, TemaItems.AIR_SUCI, KAIN_AIR_SUCI)) {
				AirLayar.peringatan(p, Component.translatable("kultivator.kota_tema.air.sutra.kain.bahan", KAIN_SUTRA, KAIN_DAUN, KAIN_AIR_SUCI));
				return;
			}
			Barang.beri(p, new ItemStack(TemaItems.KAIN_XIU_NIANG));
			p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
			AirLayar.pesan(p, Component.translatable("kultivator.kota_tema.air.sutra.kain.jadi"), true);
			AirCerita.peristiwa(p, "air_kain", "jadi");
		});
	}

	// ================================================================== helpers

	/** The house discount (15 % after the chain). */
	static double diskon(ServerPlayer player) {
		return CeritaApi.bendera(player, "tema.air.diskon_sutra") ? DISKON_SELESAI : 0.0D;
	}

	private static long harga(long base, double diskon) {
		return Math.max(1L, Math.round(base * (1.0D - diskon)));
	}

	private static long hargaSutra(ServerPlayer player, AirKota.Hidup kota, DagangItems.Barang sutra) {
		// Dagang already applies the place discounts and charges this very price
		return Dagang.hargaBeli(player, kota.situs, sutra);
	}

	static boolean punya(ServerPlayer player, Item item, int n) {
		int have = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				have += s.getCount();
			}
		}
		return have >= n;
	}

	static boolean ambil(ServerPlayer player, Item item, int n) {
		if (!punya(player, item, n)) {
			return false;
		}
		int left = n;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
		return true;
	}

	private RumahSutra() {
	}
}
