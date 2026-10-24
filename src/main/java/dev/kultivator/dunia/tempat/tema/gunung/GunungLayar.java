package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The three screens of the Kota Gunung built by 38TK-G (keys of {@link TemaLayar.Herba}, {@link TemaLayar.Ziarah},
 * {@link TemaLayar.Lift} plus the common {@link LembarKunci} header; drawn by 38TV-U on the "Pasar Giok" kit) and the
 * "Jasa" entries of the 38 dialog that open them:
 * <ul>
 * <li>{@code tema_herba} Aula Herba Gunung (Nenek Yao): buy mountain herbs (six offers a day per town, aged 60-300, one
 * each), Tali Panjat (15 NB), Dupa Ziarah (2 NB), the Rumput Awan Seribu Tahun (120 NB, one per 7 days); sell herbs at
 * 0.5 x value (38.M.5); free age appraisal of the held herb; the Pemetik Tebing rank (1-5, discount 0/0/5/10/15 %, extra
 * herb spots 0/2/5/5/5). Gou Tie's monopoly raises the prices 20 %.</li>
 * <li>{@code tema_ziarah} the Kuil Kaki desk: status, the nine landings, the rules, the Restu days; ops daftar,
 * berhenti.</li>
 * <li>{@code tema_lift} a station of the cable car: fare, direction, schedule, the line schema; ops naik, turun.</li>
 * </ul>
 */
final class GunungLayar {

	public static final long HARGA_TALI_PANJAT = 15L;
	public static final long HARGA_DUPA = 2L;
	public static final long HARGA_RUMPUT_AWAN = 120L;
	public static final int TAWARAN_HARIAN = 6;

	private static final Map<UUID, Object[]> PESAN = new HashMap<>();

	static void init() {
		TemaLayar.daftar(TemaLayar.Herba.LAYAR, GunungLayar::bangunHerba, GunungLayar::aksiHerba);
		TemaLayar.daftar(TemaLayar.Ziarah.LAYAR, GunungLayar::bangunZiarah, GunungLayar::aksiZiarah);
		TemaLayar.daftar(TemaLayar.Lift.LAYAR, GunungLayar::bangunLift, GunungLayar::aksiLift);
		CeritaApi.daftarJasa(GunungLayar::jasa);
	}

	static void reset() {
		PESAN.clear();
	}

	// ================================================================================================ dialog services

	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		String jangkar = npc.memory().getStringOr("gunung_jangkar", "");
		String tokoh = GunungTokoh.dari(npc);
		List<TempatDialog.JasaEntri> out = new ArrayList<>(1);
		if (GunungTokoh.YAO_PO.equals(tokoh) || GunungAnchors.AULA_HERBA.equals(jangkar)) {
			out.add(new TempatDialog.JasaEntri(TemaLayar.Herba.LAYAR, Component.translatable("kultivator.kota_tema.gunung.layar.herba"),
					LembarKunci.Ikon.HERBA, Component.translatable("kultivator.kota_tema.gunung.layar.herba.catatan"), false, null));
		}
		if (GunungAnchors.ZIARAH.equals(jangkar)) {
			out.add(new TempatDialog.JasaEntri(TemaLayar.Ziarah.LAYAR, Component.translatable("kultivator.kota_tema.gunung.layar.ziarah"),
					LembarKunci.Ikon.LONCENG, Component.translatable("kultivator.kota_tema.gunung.layar.ziarah.catatan"), false, null));
		}
		if (GunungTokoh.TIE_SUO.equals(tokoh) || GunungAnchors.LIFT.equals(jangkar)) {
			out.add(new TempatDialog.JasaEntri(TemaLayar.Lift.LAYAR, Component.translatable("kultivator.kota_tema.gunung.layar.lift"),
					LembarKunci.Ikon.JAM_PASIR, Component.translatable("kultivator.kota_tema.gunung.layar.lift.catatan"), false, null));
		}
		return out;
	}

	// ================================================================================================ common header

	private static @Nullable KultivatorNpc npc(ServerPlayer player, int npcId) {
		Entity e = player.level().getEntity(npcId);
		return e instanceof KultivatorNpc n ? n : null;
	}

	private static CompoundTag kepala(ServerPlayer player, String judulKunci, String gaya, @Nullable KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(judulKunci));
		tag.putString(LembarKunci.GAYA, gaya);
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		tag.putIntArray(LembarKunci.BATU, Barang.batu(player));
		tag.putInt(LembarKunci.PITA, Barang.pita(player));
		TemaKota.Kota kota = GunungTata.terdekat((ServerLevel) player.level(), player.blockPosition(), 200);
		if (kota != null) {
			tag.putLong(LembarKunci.SITUS, kota.situsId());
			LembarKunci.teks(tag, LembarKunci.TEMPAT, kota.nama(((ServerLevel) player.level()).getSeed()));
		}
		if (npc != null) {
			CompoundTag card = new CompoundTag();
			LembarKunci.teks(card, LembarKunci.NAMA, npc.getDisplayName());
			LembarKunci.teks(card, LembarKunci.GELAR, Component.translatable(npc.role().titleKey()));
			card.putString(LembarKunci.PERAN, npc.role().name());
			card.putInt(LembarKunci.POTRET, npc.getId());
			String tokoh = GunungTokoh.dari(npc);
			if (!tokoh.isEmpty()) {
				card.putString(LembarKunci.TOKOH_ID, tokoh);
			}
			tag.put(LembarKunci.NPC, card);
			if (npc.town() != 0L) {
				tag.putInt(LembarKunci.NAMA_BAIK, KotaData.get(player.level().getServer()).reputation(npc.town(), player.getUUID()));
			}
		}
		Object[] pesan = PESAN.remove(player.getUUID());
		if (pesan != null) {
			LembarKunci.teks(tag, LembarKunci.PESAN, (Component) pesan[0]);
			tag.putString(LembarKunci.PESAN_JENIS, (String) pesan[1]);
		}
		return tag;
	}

	private static void pesan(ServerPlayer player, Component teks, boolean ok) {
		PESAN.put(player.getUUID(), new Object[]{teks, ok ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA});
		Msg.actionBar(player, teks.copy().withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	// ================================================================================================ tema_herba

	/** Rank discount: rank 3 5 %, 4 10 %, 5 15 %. */
	static double diskon(int pangkat) {
		return pangkat >= 5 ? 0.15D : pangkat == 4 ? 0.10D : pangkat == 3 ? 0.05D : 0.0D;
	}

	/** Extra herb spots opened by the rank (spots 8-9 at rank 2, 10-12 at rank 3). */
	static int titikTambahan(int pangkat) {
		return pangkat >= 3 ? 5 : pangkat == 2 ? 2 : 0;
	}

	private record Tawaran(String id, ItemStack barang, long harga, int stok) {
	}

	/** Today's offers of the hall of town {@code situsId}. */
	private static List<Tawaran> tawaran(ServerPlayer player, long situsId) {
		ServerLevel level = (ServerLevel) player.level();
		long hari = TemaKalender.hari(level);
		int pangkat = GunungData.pangkatPemetik(GunungData.poinPemetik(player));
		double faktor = (1.0D - diskon(pangkat)) * (GunungData.monopoli(level.getServer(), situsId) ? 1.2D : 1.0D);
		CompoundTag kota = GunungData.kota(level.getServer(), situsId);
		int dibeli = kota.getLongOr("bh", -1L) == hari ? kota.getIntOr("bb", 0) : 0;
		List<Tawaran> out = new ArrayList<>();
		RandomSource rng = RandomSource.create(hari * 0x9E3779B97F4A7C15L ^ situsId);
		for (int i = 0; i < TAWARAN_HARIAN; i++) {
			ItemStack h = SpiritHerbs.randomFor(null, rng, HerbaTebingEntity.UMUR_MIN, HerbaTebingEntity.UMUR_MAKS);
			long base = Math.max(1L, EkonomiApi.harga(h));
			long harga = Math.max(1L, Math.round(base * 1.1D * faktor));
			out.add(new Tawaran("h" + i, h, harga, (dibeli & (1 << i)) != 0 ? 0 : 1));
		}
		out.add(new Tawaran("tali", new ItemStack(TemaItems.TALI_PANJAT), Math.max(1L, Math.round(HARGA_TALI_PANJAT * faktor)), -1));
		out.add(new Tawaran("dupa", new ItemStack(TemaItems.DUPA_ZIARAH), HARGA_DUPA, -1));
		boolean rumput = kota.getLongOr("ra", 0L) <= hari;
		out.add(new Tawaran("rumput", new ItemStack(TemaItems.RUMPUT_AWAN_SERIBU_TAHUN), Math.round(HARGA_RUMPUT_AWAN * faktor), rumput ? 1 : 0));
		return out;
	}

	private static @Nullable CompoundTag bangunHerba(ServerPlayer player, int npcId) {
		KultivatorNpc npc = npc(player, npcId);
		TemaKota.Kota kota = GunungTata.terdekat((ServerLevel) player.level(), player.blockPosition(), 200);
		if (kota == null) {
			return null;
		}
		CompoundTag tag = kepala(player, "kultivator.kota_tema.gunung.layar.herba", LembarKunci.GAYA_KAYU, npc);
		var reg = player.level().registryAccess();
		int poin = GunungData.poinPemetik(player);
		int pangkat = GunungData.pangkatPemetik(poin);
		tag.putFloat(LembarKunci.DISKON, (float) diskon(pangkat));
		ListTag beli = new ListTag();
		for (Tawaran t : tawaran(player, kota.situsId())) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, t.id());
			LembarKunci.teks(e, LembarKunci.NAMA, t.barang().getHoverName());
			LembarKunci.barang(e, t.barang(), reg);
			int umur = SpiritHerbs.isHerb(t.barang()) ? SpiritHerbs.age(t.barang()) : t.id().equals("rumput") ? 300 : 0;
			if (umur > 0) {
				e.putInt(TemaLayar.Herba.UMUR, umur);
			}
			e.putLong(LembarKunci.HARGA, t.harga());
			e.putInt(LembarKunci.STOK, t.stok());
			e.putString(LembarKunci.KEADAAN, t.stok() == 0 ? LembarKunci.K_HABIS : NilaiBatu.total(player) < t.harga()
					? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK);
			beli.add(e);
		}
		tag.put(TemaLayar.Herba.BELI, beli);
		ListTag jual = new ListTag();
		for (int slot = 0; slot < player.getInventory().getContainerSize() && jual.size() < LembarKunci.DAFTAR_MAKS; slot++) {
			ItemStack s = player.getInventory().getItem(slot);
			if (!SpiritHerbs.isHerb(s) && !s.is(TemaItems.RUMPUT_AWAN_SERIBU_TAHUN)) {
				continue;
			}
			CompoundTag e = new CompoundTag();
			e.putInt(LembarKunci.SLOT, slot);
			LembarKunci.teks(e, LembarKunci.NAMA, s.getHoverName());
			LembarKunci.barang(e, s, reg);
			e.putInt(TemaLayar.Herba.UMUR, s.is(TemaItems.RUMPUT_AWAN_SERIBU_TAHUN) ? 300 : SpiritHerbs.age(s));
			e.putLong(LembarKunci.HARGA, hargaJual(s));
			jual.add(e);
		}
		tag.put(TemaLayar.Herba.JUAL, jual);
		CompoundTag nilai = new CompoundTag();
		ItemStack tangan = player.getMainHandItem();
		if (SpiritHerbs.isHerb(tangan) || tangan.is(TemaItems.RUMPUT_AWAN_SERIBU_TAHUN)) {
			CompoundTag t = new CompoundTag();
			LembarKunci.teks(t, LembarKunci.NAMA, tangan.getHoverName());
			LembarKunci.barang(t, tangan, reg);
			int umur = tangan.is(TemaItems.RUMPUT_AWAN_SERIBU_TAHUN) ? 300 : SpiritHerbs.age(tangan);
			t.putInt(TemaLayar.Herba.UMUR, umur);
			t.putInt(LembarKunci.MUTU, Math.min(4, umur / 75));
			t.putLong(LembarKunci.NILAI, EkonomiApi.harga(tangan));
			nilai.put("tangan", t);
		}
		tag.put(TemaLayar.Herba.NILAI_HERBA, nilai);
		CompoundTag rank = new CompoundTag();
		rank.putInt(TemaLayar.Herba.PANGKAT, pangkat);
		rank.putInt(TemaLayar.Herba.POIN, poin);
		rank.putInt(TemaLayar.Herba.POIN_NAIK, GunungData.poinNaik(poin));
		rank.putInt(TemaLayar.Herba.TITIK_TAMBAHAN, titikTambahan(pangkat));
		rank.putFloat(LembarKunci.DISKON, (float) diskon(pangkat));
		tag.put(TemaLayar.Herba.PERINGKAT, rank);
		return tag;
	}

	static long hargaJual(ItemStack s) {
		long value = s.is(TemaItems.RUMPUT_AWAN_SERIBU_TAHUN) ? HARGA_RUMPUT_AWAN : EkonomiApi.harga(s);
		return Math.max(1L, value / 2L) * s.getCount();
	}

	private static void aksiHerba(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		ServerLevel level = (ServerLevel) player.level();
		TemaKota.Kota kota = GunungTata.terdekat(level, player.blockPosition(), 200);
		if (kota == null) {
			return;
		}
		switch (parts[0]) {
			case TemaLayar.Herba.OP_BELI -> {
				String id = parts.length > 1 ? parts[1] : "";
				for (Tawaran t : tawaran(player, kota.situsId())) {
					if (!t.id().equals(id)) {
						continue;
					}
					if (t.stok() == 0) {
						pesan(player, Component.translatable("kultivator.kota_tema.gunung.herba.habis"), false);
						return;
					}
					if (!EkonomiApi.bayar(player, t.harga(), "aula_herba")) {
						pesan(player, Component.translatable("kultivator.kota_tema.gunung.lift.uang"), false);
						return;
					}
					Barang.beri(player, t.barang().copy());
					CompoundTag k = GunungData.kota(level.getServer(), kota.situsId());
					long hari = TemaKalender.hari(level);
					if (id.startsWith("h")) {
						int bits = k.getLongOr("bh", -1L) == hari ? k.getIntOr("bb", 0) : 0;
						k.putLong("bh", hari);
						k.putInt("bb", bits | 1 << LembarKunci.argInt(LembarKunci.urai("h:" + id.substring(1)), 1, 0));
					} else if (id.equals("rumput")) {
						k.putLong("ra", hari + 7);
					}
					GunungData.ubah(level.getServer());
					pesan(player, Component.translatable("kultivator.kota_tema.gunung.herba.beli", t.barang().getHoverName(), t.harga()), true);
					return;
				}
			}
			case TemaLayar.Herba.OP_JUAL -> {
				int slot = LembarKunci.argInt(parts, 1, -1);
				if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
					return;
				}
				ItemStack s = player.getInventory().getItem(slot);
				if (!SpiritHerbs.isHerb(s) && !s.is(TemaItems.RUMPUT_AWAN_SERIBU_TAHUN)) {
					return;
				}
				long harga = hargaJual(s);
				Component nama = s.getHoverName();
				player.getInventory().setItem(slot, ItemStack.EMPTY);
				EkonomiApi.beri(player, harga, "aula_herba");
				pesan(player, Component.translatable("kultivator.kota_tema.gunung.herba.jual", nama, harga), true);
			}
			case TemaLayar.Herba.OP_NILAI -> {
				ItemStack tangan = player.getMainHandItem();
				if (!SpiritHerbs.isHerb(tangan)) {
					pesan(player, Component.translatable("kultivator.kota_tema.gunung.herba.nilai.kosong"), false);
					return;
				}
				pesan(player, Component.translatable("kultivator.kota_tema.gunung.herba.nilai", tangan.getHoverName(), SpiritHerbs.age(tangan)), true);
			}
			default -> {
			}
		}
	}

	// ================================================================================================ tema_ziarah

	private static @Nullable CompoundTag bangunZiarah(ServerPlayer player, int npcId) {
		TemaKota.Kota kota = GunungTata.terdekat((ServerLevel) player.level(), player.blockPosition(), 200);
		if (kota == null) {
			return null;
		}
		CompoundTag tag = kepala(player, "kultivator.kota_tema.gunung.layar.ziarah", LembarKunci.GAYA_BATU, npc(player, npcId));
		int st = Ziarah.status(player);
		tag.putString(LembarKunci.STATUS, switch (st) {
			case GunungData.Z_BERJALAN -> TemaLayar.Ziarah.S_BERJALAN;
			case GunungData.Z_SELESAI -> TemaLayar.Ziarah.S_SELESAI;
			case GunungData.Z_BATAL -> TemaLayar.Ziarah.S_BATAL;
			default -> TemaLayar.Ziarah.S_BELUM;
		});
		int bits = Ziarah.bordesMenyala(player);
		ListTag bordes = new ListTag();
		for (int i = 0; i < GunungTata.BORDES.size(); i++) {
			CompoundTag e = new CompoundTag();
			e.putInt("no", i + 1);
			LembarKunci.teks(e, LembarKunci.NAMA, Component.translatable("kultivator.kota_tema.gunung.ziarah.bordes." + (i + 1)));
			e.putBoolean(TemaLayar.Ziarah.DINYALAKAN, st == GunungData.Z_BERJALAN && (bits & (1 << i)) != 0);
			Vec3 at = GunungTata.dunia(kota, GunungTata.BORDES.get(i));
			e.putInt(LembarKunci.X, (int) Math.floor(at.x));
			e.putInt(LembarKunci.Z, (int) Math.floor(at.z));
			bordes.add(e);
		}
		tag.put(TemaLayar.Ziarah.BORDES, bordes);
		ListTag syarat = new ListTag();
		for (String s : Ziarah.SYARAT) {
			syarat.add(StringTag.valueOf(s));
		}
		tag.put(TemaLayar.Ziarah.SYARAT_ZIARAH, syarat);
		String alasan = GunungData.pemain(player).getStringOr("zw", "");
		if (st == GunungData.Z_BATAL && !alasan.isEmpty()) {
			LembarKunci.teks(tag, TemaLayar.Ziarah.BATAL, Component.translatable("kultivator.kota_tema.gunung.ziarah.batal." + alasan));
		}
		CompoundTag restu = new CompoundTag();
		restu.putInt(LembarKunci.SISA_HARI, Ziarah.sisaHariRestu(player));
		restu.putInt(TemaLayar.Ziarah.JEDA_HARI, Ziarah.jedaHari(player));
		tag.put(TemaLayar.Ziarah.RESTU, restu);
		return tag;
	}

	private static void aksiZiarah(ServerPlayer player, int npcId, String op) {
		String verb = LembarKunci.verb(op);
		ServerLevel level = (ServerLevel) player.level();
		if (TemaLayar.Ziarah.OP_DAFTAR.equals(verb)) {
			TemaKota.Kota kota = GunungTata.terdekat(level, player.blockPosition(), 200);
			boolean ok = Ziarah.daftar(player, kota);
			pesan(player, Component.translatable(ok ? "kultivator.kota_tema.gunung.ziarah.daftar" : "kultivator.kota_tema.gunung.ziarah.sedang"), ok);
		} else if (TemaLayar.Ziarah.OP_BERHENTI.equals(verb)) {
			Ziarah.batal(player, Ziarah.BATAL_BERHENTI);
			pesan(player, Component.translatable("kultivator.kota_tema.gunung.ziarah.batal.berhenti"), false);
		}
	}

	// ================================================================================================ tema_lift

	private static boolean diAtas(ServerPlayer player) {
		TemaKota.Kota kota = GunungTata.terdekat((ServerLevel) player.level(), player.blockPosition(), 200);
		if (kota == null) {
			return false;
		}
		Vec3 atas = GunungTata.dunia(kota, GunungTata.KABIN_ATAS);
		Vec3 bawah = GunungTata.dunia(kota, GunungTata.KABIN_BAWAH);
		return player.distanceToSqr(atas) < player.distanceToSqr(bawah);
	}

	private static @Nullable CompoundTag bangunLift(ServerPlayer player, int npcId) {
		ServerLevel level = (ServerLevel) player.level();
		TemaKota.Kota kota = GunungTata.terdekat(level, player.blockPosition(), 200);
		if (kota == null) {
			return null;
		}
		CompoundTag tag = kepala(player, "kultivator.kota_tema.gunung.layar.lift", LembarKunci.GAYA_KAYU, npc(player, npcId));
		boolean atas = diAtas(player);
		tag.putLong(TemaLayar.Lift.ONGKOS, atas || GunungData.liftGratis(player) ? 0L : KeretaGantung.ONGKOS_NAIK);
		tag.putString(TemaLayar.Lift.ARAH, atas ? "turun" : "naik");
		KeretaGantung.Info info = KeretaGantung.info(kota.situsId());
		CompoundTag jadwal = new CompoundTag();
		boolean disini = info.ada() && (atas ? info.fase() == KeretaGantung.DI_ATAS : info.fase() == KeretaGantung.DI_BAWAH);
		jadwal.putLong(TemaLayar.Lift.BERANGKAT_TICK, disini ? KeretaGantung.TUNGGU_PINTU : -1L);
		LembarKunci.teks(jadwal, TemaLayar.Lift.JAM_BUKA, Component.literal("05.00-18.00"));
		jadwal.putBoolean(TemaLayar.Lift.TUTUP, !KeretaGantung.buka(level));
		tag.put(TemaLayar.Lift.JADWAL, jadwal);
		CompoundTag skema = new CompoundTag();
		skema.putFloat(TemaLayar.Lift.KABIN, info.kabin());
		skema.putFloat(TemaLayar.Lift.TIANG, info.tiang());
		skema.putBoolean(TemaLayar.Lift.MACET, info.macet());
		skema.putInt(TemaLayar.Lift.PENUMPANG, info.penumpang());
		skema.putInt(TemaLayar.Lift.KURSI, info.kursi());
		tag.put(TemaLayar.Lift.SKEMA, skema);
		return tag;
	}

	private static void aksiLift(ServerPlayer player, int npcId, String op) {
		String verb = LembarKunci.verb(op);
		if (!TemaLayar.Lift.OP_NAIK.equals(verb) && !TemaLayar.Lift.OP_TURUN.equals(verb)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		TemaKota.Kota kota = GunungTata.terdekat(level, player.blockPosition(), 200);
		if (kota == null) {
			return;
		}
		KeretaGantung.Jalur j = KeretaGantung.jalur(kota.situsId());
		boolean atas = diAtas(player);
		if (j == null || j.kabin == null || j.kabin.isRemoved() || (atas ? j.fase != KeretaGantung.DI_ATAS : j.fase != KeretaGantung.DI_BAWAH)) {
			pesan(player, Component.translatable("kultivator.kota_tema.gunung.lift.tunggu"), false);
			return;
		}
		if (player.distanceToSqr(j.kabin) > 12.0D * 12.0D) {
			pesan(player, Component.translatable("kultivator.kota_tema.gunung.lift.dekati"), false);
			return;
		}
		boolean ok = KeretaGantung.naik(player, j.kabin);
		if (ok) {
			dev.kultivator.dunia.tempat.ui.TempatLayar.tutup(player);
		}
	}

	private GunungLayar() {
	}
}
