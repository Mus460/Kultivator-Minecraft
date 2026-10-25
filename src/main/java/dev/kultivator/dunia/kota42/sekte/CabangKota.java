package dev.kultivator.dunia.kota42.sekte;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiBlocks;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.seragam.LambangData;
import dev.kultivator.sekte.seragam.SeragamData;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player sect's branch in a town (DESIGN 42.8.4, contract C42.E1): "<Sect> · Cabang Kota <place>" (not the Sekte Cabang
 * of 41.8.2). Opened by placing a {@code kultivator:papan_cabang} inside a building the player owns or rents
 * ({@code RumahKota}: large house, shop house or pavilion) with hall level &ge; 4 and either the hall's pact here or Nama
 * Baik &ge; 60. One branch per place.
 * <ul>
 * <li><b>Toko Pil Sekte</b> (&ge; 4 {@code rak_dagang}, 1 {@code meja_kasir}): the player's shop there sells as a sect brand
 * ({@link #faktorLaku} x1.15, its day cap x1.5, 42C).</li>
 * <li><b>Balai Latihan</b> (&ge; 3 armour stands as training dummies): 2-6 town youths train; their fee {@code 3 x class} NB
 * each a week from the town purse; the recruitment office draws x1.2 ({@link #bonusPelamar}); the defence x1.1
 * ({@link #bonusBela}).</li>
 * <li><b>Klinik Sekte</b> (a brewing stand or cauldron and 2 beds): the sick die less ({@link #klinik}, 42A), residents'
 * impression +1 a week while the master visits.</li>
 * </ul>
 * Every week a working branch gives renown +2 and Nama Baik +1. The kind changes at most every 7 days. Selling, losing or
 * having the house sealed, or breaking the sign, closes the branch.
 * State: {@code KotaSekteData.situs("cabang", situs)}: {@code o}, {@code rumah}, {@code jenis}, {@code sejak},
 * {@code ganti}, the sign {@code x/y/z/h}, {@code ok} (contents complete at the last check), {@code minggu}, {@code lap}.
 */
public final class CabangKota {

	public static final String TOKO_PIL = "toko_pil";
	public static final String BALAI_LATIHAN = "balai_latihan";
	public static final String KLINIK = "klinik";
	public static final List<String> JENIS = List.of(TOKO_PIL, BALAI_LATIHAN, KLINIK);
	public static final int AULA_MIN = 4;
	public static final int NAMA_BAIK_MIN = 60;
	public static final int JEDA_GANTI = 7;
	private static final List<String> UKURAN = List.of("besar", "rumah_toko", "paviliun");

	/** One branch: owner, place, house id ({@code RumahKota.Rumah.id}), kind ({@link #JENIS}), opened on world day. */
	public record Cabang(UUID pemilik, long situs, int rumah, String jenis, long sejak) {
	}

	/** One required content line of a kind: text, how many there are, how many are needed. */
	public record Isi(Component teks, int punya, int perlu) {
		public boolean ok() {
			return punya >= perlu;
		}
	}

	static void lupakan() {
	}

	// ================================================================== reading (C42.E1)

	/** The branch in place {@code situs}, or null. */
	public static @Nullable Cabang cabang(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !KotaConfig.sekte().aktif || !KotaConfig.sekte().cabang) {
			return null;
		}
		CompoundTag t = peek(server, situs);
		UUID o = t == null ? null : t.read("o", UUIDUtil.CODEC).orElse(null);
		return o == null ? null : new Cabang(o, situs, t.getIntOr("rumah", 0), t.getStringOr("jenis", TOKO_PIL), t.getLongOr("sejak", 0L));
	}

	/** Every branch of {@code owner}. */
	public static List<Cabang> milik(MinecraftServer server, UUID owner) {
		List<Cabang> out = new ArrayList<>();
		for (long situs : situsSemua(server)) {
			Cabang c = cabang(situs);
			if (c != null && c.pemilik().equals(owner)) {
				out.add(c);
			}
		}
		return out;
	}

	/** The branch's shown name: "<Sect> · Cabang Kota <place>". */
	public static Component nama(MinecraftServer server, Cabang c) {
		return Component.translatable("kultivator.kota42.sekte.cabang.nama", KotaSekte.namaSekte(c.pemilik()), KotaSekte.namaTempat(server, c.situs()));
	}

	/** The sign's position, or null. */
	public static @Nullable BlockPos papan(MinecraftServer server, long situs) {
		CompoundTag t = peek(server, situs);
		return t == null || !t.contains("o") ? null : new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0));
	}

	/** Where the branch's keeper stands: one step in front of the sign. */
	public static @Nullable BlockPos posPenjaga(MinecraftServer server, long situs) {
		CompoundTag t = peek(server, situs);
		BlockPos p = papan(server, situs);
		return p == null ? null : p.relative(Direction.from2DDataValue(t.getIntOr("h", Direction.NORTH.get2DDataValue())));
	}

	/** True when the branch's required contents were complete at the last check. */
	public static boolean bekerja(MinecraftServer server, long situs) {
		CompoundTag t = peek(server, situs);
		return t != null && t.contains("o") && t.getBooleanOr("ok", false);
	}

	/** The day the kind may change again. */
	public static long gantiBoleh(MinecraftServer server, long situs) {
		CompoundTag t = peek(server, situs);
		return t == null ? 0L : t.getLongOr("ganti", 0L);
	}

	/** The weekly reports (newest last): day, lang key, NB. */
	public static ListTag laporan(MinecraftServer server, long situs) {
		CompoundTag t = peek(server, situs);
		return t == null ? new ListTag() : t.getListOrEmpty("lap").copy();
	}

	/** Recruitment office pull of {@code owner} at {@code situs}: x1.2 with a working training hall there. */
	public static double bonusPelamar(MinecraftServer server, long situs, UUID owner) {
		Cabang c = cabang(situs);
		return c != null && c.pemilik().equals(owner) && BALAI_LATIHAN.equals(c.jenis()) && bekerja(server, situs) ? 1.2D : 1.0D;
	}

	/** Defence strength of {@code owner} at {@code situs}: x1.1 with a working training hall there. */
	public static double bonusBela(MinecraftServer server, long situs, UUID owner) {
		Cabang c = cabang(situs);
		return c != null && c.pemilik().equals(owner) && BALAI_LATIHAN.equals(c.jenis()) && bekerja(server, situs) ? 1.1D : 1.0D;
	}

	/** 42C: sales of the shop in house {@code rumah} at {@code situs}: x1.15 when it is a working sect pill shop (cap x1.5). */
	public static double faktorLaku(long situs, int rumah) {
		MinecraftServer server = KotaHidup.server();
		Cabang c = server == null ? null : cabang(situs);
		return c != null && c.rumah() == rumah && TOKO_PIL.equals(c.jenis()) && bekerja(server, situs) ? 1.15D : 1.0D;
	}

	/** 42A: true when a working sect clinic stands in {@code situs} (deaths by illness x0.8). */
	public static boolean klinik(long situs) {
		MinecraftServer server = KotaHidup.server();
		Cabang c = server == null ? null : cabang(situs);
		return c != null && KLINIK.equals(c.jenis()) && bekerja(server, situs);
	}

	/** Town youths training at a working training hall: 2-6 by the place's size. */
	public static int muridKota(MinecraftServer server, long situs) {
		return Math.min(6, 1 + PajakKota.kelas(server, situs) + (int) Math.floorMod(situs >>> 7, 2L));
	}

	/** The required contents of kind {@code jenis} inside house {@code kotak}. */
	public static List<Isi> isi(ServerLevel level, BoundingBox kotak, String jenis) {
		List<Isi> out = new ArrayList<>();
		switch (jenis) {
			case TOKO_PIL -> {
				out.add(new Isi(Component.translatable("kultivator.kota42.sekte.cabang.isi.rak"), hitung(level, kotak, s -> s.is(KotaEkonomiBlocks.RAK_DAGANG)), 4));
				out.add(new Isi(Component.translatable("kultivator.kota42.sekte.cabang.isi.kasir"), hitung(level, kotak, s -> s.is(KotaEkonomiBlocks.MEJA_KASIR)), 1));
			}
			case BALAI_LATIHAN -> {
				AABB box = AABB.of(kotak);
				int boneka = level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::isAlive).size();
				out.add(new Isi(Component.translatable("kultivator.kota42.sekte.cabang.isi.boneka"), boneka, 3));
			}
			default -> {
				out.add(new Isi(Component.translatable("kultivator.kota42.sekte.cabang.isi.kuali"),
						hitung(level, kotak, s -> s.is(Blocks.BREWING_STAND) || s.is(BlockTags.CAULDRONS)), 1));
				out.add(new Isi(Component.translatable("kultivator.kota42.sekte.cabang.isi.kasur"), hitung(level, kotak,
						s -> s.is(BlockTags.BEDS) && s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD), 2));
			}
		}
		return out;
	}

	private static int hitung(ServerLevel level, BoundingBox kotak, java.util.function.Predicate<BlockState> cocok) {
		int n = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		// a house box is at most a few thousand blocks; counted only on a check (sign placed, kind chosen, screen, daily)
		for (int x = kotak.minX(); x <= kotak.maxX(); x++) {
			for (int y = kotak.minY(); y <= kotak.maxY(); y++) {
				for (int z = kotak.minZ(); z <= kotak.maxZ(); z++) {
					p.set(x, y, z);
					if (cocok.test(level.getBlockState(p))) {
						n++;
					}
				}
			}
		}
		return n;
	}

	/** Re-checks the contents of the branch at {@code situs} (when its house is loaded); returns the complete flag. */
	public static boolean periksa(ServerLevel level, long situs) {
		MinecraftServer server = level.getServer();
		CompoundTag t = peek(server, situs);
		if (t == null || !t.contains("o")) {
			return false;
		}
		RumahKota.Info info = RumahKota.info(server, situs, t.getIntOr("rumah", 0));
		if (info == null || !level.isLoaded(info.kotak().getCenter())) {
			return t.getBooleanOr("ok", false);
		}
		boolean ok = true;
		for (Isi i : isi(level, info.kotak(), t.getStringOr("jenis", TOKO_PIL))) {
			ok &= i.ok();
		}
		t.putBoolean("ok", ok);
		KotaSekteData.get(server).ubah();
		return ok;
	}

	/** Why {@code player} may not open a branch at {@code situs} in house {@code info} (null = he may). */
	static @Nullable KotaSekte.Hasil syarat(ServerPlayer player, long situs, @Nullable RumahKota.Rumah rumah) {
		KotaSekte.Hasil h = KotaSekte.syaratUmum(player, KotaConfig.sekte().cabang);
		if (h != null) {
			return h;
		}
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		if (rumah == null || !owner.equals(rumah.pemilik()) || RumahKota.ST_SEGEL.equals(rumah.jenis()) || "segel".equals(rumah.jenis())) {
			return KotaSekte.Hasil.gagal("cabang.bukan_rumah");
		}
		RumahKota.Info info = RumahKota.info(server, situs, rumah.id());
		if (info == null || !UKURAN.contains(info.ukuran())) {
			return KotaSekte.Hasil.gagal("cabang.ukuran");
		}
		if (Halls.level(owner) < AULA_MIN) {
			return KotaSekte.Hasil.gagal("aula", AULA_MIN);
		}
		int nb = KotaSekte.namaBaik(server, situs, owner);
		if (!owner.equals(PaktaLindung.pelindung(situs)) && nb < NAMA_BAIK_MIN) {
			return KotaSekte.Hasil.gagal("cabang.syarat", NAMA_BAIK_MIN, nb);
		}
		Cabang ada = cabang(situs);
		if (ada != null) {
			return KotaSekte.Hasil.gagal("cabang.sudah", KotaSekte.namaSekte(ada.pemilik()));
		}
		return null;
	}

	// ================================================================== the sign (PapanCabangBlock)

	/** A {@code papan_cabang} was placed: opens the branch when everything holds, else the board stays blank. */
	public static void pasang(ServerLevel level, BlockPos pos, @Nullable LivingEntity by) {
		if (!(by instanceof ServerPlayer player) || !KotaHidup.duniaKota(level)) {
			return;
		}
		RumahKota.Rumah rumah = RumahKota.rumahDi(level, pos);
		long situs = rumah == null ? 0L : rumah.situs();
		KotaSekte.Hasil h = syarat(player, situs, rumah);
		if (h != null) {
			h.kirim(player);
			return;
		}
		MinecraftServer server = level.getServer();
		UUID owner = player.getUUID();
		long hari = KotaSekte.hari(server);
		CompoundTag t = KotaSekteData.get(server).situs(KotaSekteData.CABANG, situs);
		for (String key : List.copyOf(t.keySet())) {
			t.remove(key);
		}
		t.store("o", UUIDUtil.CODEC, owner);
		t.putInt("rumah", rumah.id());
		t.putString("jenis", TOKO_PIL);
		t.putLong("sejak", hari);
		t.putLong("ganti", hari);
		t.putLong("minggu", hari);
		t.putInt("x", pos.getX());
		t.putInt("y", pos.getY());
		t.putInt("z", pos.getZ());
		BlockState state = level.getBlockState(pos);
		Direction hadap = state.hasProperty(PapanCabangBlock.FACING) ? state.getValue(PapanCabangBlock.FACING) : Direction.NORTH;
		t.putInt("h", hadap.get2DDataValue());
		KotaSekteData.get(server).ubah();
		ikatPapan(level, pos, owner);
		periksa(level, situs);
		Advancements.grant(player, "tempat/kota42_cabang");
		Halls.log(owner, Component.translatable("kultivator.kota42.sekte.cabang.log", KotaSekte.namaTempat(server, situs)));
		Cabang c = cabang(situs);
		KotaSekte.Hasil.berhasil("cabang.dibuka", c == null ? Component.empty() : nama(server, c)).kirim(player);
	}

	/** Writes the sect's emblem, name and ink on the sign. */
	private static void ikatPapan(ServerLevel level, BlockPos pos, UUID owner) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof PapanCabangBlock.Isi isi) {
			SeragamData seragam = LambangData.get(owner);
			isi.ikat(owner, seragam.lambang(), KotaSekte.namaSekte(owner).getString(), seragam.trimRgb(0) == 0 ? PapanCabangBlock.Isi.WARNA_BAWAAN
					: seragam.trimRgb(0));
		}
	}

	/** The sign at {@code pos} is gone: its branch closes. */
	public static void papanHilang(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		if (level.getBlockState(pos).is(KotaEkonomiBlocks.PAPAN_CABANG)) {
			return;
		}
		for (long situs : situsSemua(server)) {
			BlockPos p = papan(server, situs);
			if (pos.equals(p)) {
				tutupCabang(server, situs, "papan");
			}
		}
	}

	/** Test: a branch of kind {@code jenis} in the house the player stands in, ignoring hall level and Nama Baik. */
	static KotaSekte.Hasil paksa(ServerPlayer player, String jenis) {
		if (!(player.level() instanceof ServerLevel level)) {
			return KotaSekte.Hasil.gagal("dunia").kirim(player);
		}
		RumahKota.Rumah rumah = RumahKota.rumahDi(level, player.blockPosition());
		if (rumah == null) {
			return KotaSekte.Hasil.gagal("cabang.bukan_rumah").kirim(player);
		}
		MinecraftServer server = level.getServer();
		long hari = KotaSekte.hari(server);
		CompoundTag t = KotaSekteData.get(server).situs(KotaSekteData.CABANG, rumah.situs());
		t.store("o", UUIDUtil.CODEC, player.getUUID());
		t.putInt("rumah", rumah.id());
		t.putString("jenis", JENIS.contains(jenis) ? jenis : TOKO_PIL);
		t.putLong("sejak", hari);
		t.putLong("ganti", hari);
		t.putLong("minggu", hari);
		BlockPos pos = player.blockPosition();
		t.putInt("x", pos.getX());
		t.putInt("y", pos.getY());
		t.putInt("z", pos.getZ());
		t.putInt("h", player.getDirection().get2DDataValue());
		KotaSekteData.get(server).ubah();
		boolean ok = periksa(level, rumah.situs());
		return KotaSekte.Hasil.berhasil("uji.cabang", Component.translatable("kultivator.kota42.sekte.cabang.jenis." + t.getStringOr("jenis", TOKO_PIL)),
				Component.translatable(ok ? "kultivator.kota42.sekte.ya" : "kultivator.kota42.sekte.tidak")).kirim(player);
	}

	// ================================================================== actions

	/** {@code jenis:CJ}: changes the kind (every 7 days at most). */
	public static KotaSekte.Hasil pilihJenis(ServerPlayer player, long situs, String jenis) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = peek(server, situs);
		if (t == null || !player.getUUID().equals(t.read("o", UUIDUtil.CODEC).orElse(null))) {
			return KotaSekte.Hasil.gagal("cabang.bukan_milik").kirim(player);
		}
		if (!JENIS.contains(jenis)) {
			return KotaSekte.Hasil.gagal("cabang.jenis").kirim(player);
		}
		if (jenis.equals(t.getStringOr("jenis", ""))) {
			return KotaSekte.Hasil.gagal("cabang.jenis_sama").kirim(player);
		}
		long hari = KotaSekte.hari(server);
		long boleh = t.getLongOr("ganti", 0L);
		if (hari < boleh) {
			return KotaSekte.Hasil.gagal("cabang.jeda", boleh).kirim(player);
		}
		t.putString("jenis", jenis);
		t.putLong("ganti", hari + JEDA_GANTI);
		KotaSekteData.get(server).ubah();
		if (player.level() instanceof ServerLevel level) {
			periksa(level, situs);
		}
		return KotaSekte.Hasil.berhasil("cabang.jenis_dipilih", Component.translatable("kultivator.kota42.sekte.cabang.jenis." + jenis)).kirim(player);
	}

	/** {@code tutup_ya}: closes the branch (the sign stays as a blank board). */
	public static KotaSekte.Hasil tutup(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = peek(server, situs);
		if (t == null || !player.getUUID().equals(t.read("o", UUIDUtil.CODEC).orElse(null))) {
			return KotaSekte.Hasil.gagal("cabang.bukan_milik").kirim(player);
		}
		tutupCabang(server, situs, "ditutup");
		return KotaSekte.Hasil.berhasil("cabang.ditutup", KotaSekte.namaTempat(server, situs)).kirim(player);
	}

	static void tutupCabang(MinecraftServer server, long situs, String sebab) {
		CompoundTag t = peek(server, situs);
		if (t == null || !t.contains("o")) {
			return;
		}
		UUID owner = t.read("o", UUIDUtil.CODEC).orElse(null);
		BlockPos pos = papan(server, situs);
		ServerLevel level = server.overworld();
		if (pos != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PapanCabangBlock.Isi isi) {
			isi.kosongkan();
		}
		KotaSekteData.get(server).bagian(KotaSekteData.CABANG).remove("kota_" + situs);
		KotaSekteData.get(server).ubah();
		TugasKotaMurid.sasaranHilang(server, situs, TugasKotaMurid.SASARAN_CABANG, owner);
		ServerPlayer p = owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (p != null && !"ditutup".equals(sebab)) {
			Msg.chat(p, Component.translatable("kultivator.kota42.sekte.cabang.tutup." + sebab, KotaSekte.namaTempat(server, situs))
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	/** {@code KotaBus.RUMAH_BERUBAH}: the branch's house changed hands (sold, sealed, lost): the branch closes. */
	static void rumahBerubah(MinecraftServer server, long situs, int rumah, @Nullable UUID pemilik) {
		CompoundTag t = peek(server, situs);
		if (t == null || !t.contains("o") || t.getIntOr("rumah", -1) != rumah) {
			return;
		}
		UUID owner = t.read("o", UUIDUtil.CODEC).orElse(null);
		RumahKota.Info info = RumahKota.info(server, situs, rumah);
		boolean segel = info != null && RumahKota.ST_SEGEL.equals(info.status());
		if (pemilik == null || !pemilik.equals(owner) || segel) {
			tutupCabang(server, situs, segel ? "segel" : "rumah");
		}
	}

	// ================================================================== weekly

	/** Day step: contents check, then once a week renown, Nama Baik, the training fees and the clinic's good will. */
	static void harian(MinecraftServer server, long hari) {
		if (!KotaConfig.sekte().cabang) {
			return;
		}
		ServerLevel level = server.overworld();
		for (long situs : situsSemua(server)) {
			CompoundTag t = peek(server, situs);
			UUID owner = t == null ? null : t.read("o", UUIDUtil.CODEC).orElse(null);
			if (owner == null) {
				continue;
			}
			RumahKota.Info info = RumahKota.info(server, situs, t.getIntOr("rumah", 0));
			if (info == null || !owner.equals(info.pemilik())) {
				tutupCabang(server, situs, "rumah");
				continue;
			}
			periksa(level, situs);
			long minggu = t.getLongOr("minggu", hari);
			if (hari - minggu < 7L) {
				continue;
			}
			t.putLong("minggu", minggu + 7L);
			if (!t.getBooleanOr("ok", false)) {
				lapor(t, hari, "kultivator.kota42.sekte.cabang.lap.kurang", 0L);
				continue;
			}
			Halls.addRenown(owner, 2, "kultivator.kota42.sekte.renown.cabang");
			KotaSekte.tambahNamaBaik(server, situs, owner, 1);
			String jenis = t.getStringOr("jenis", TOKO_PIL);
			ServerPlayer p = server.getPlayerList().getPlayer(owner);
			switch (jenis) {
				case BALAI_LATIHAN -> {
					int murid = muridKota(server, situs);
					long iuran = KasKota.ambil(server, situs, 3L * Math.min(3, PajakKota.kelas(server, situs)) * murid);
					long laci = t.getLongOr("laci", 0L) + iuran;
					if (p != null && laci > 0L) {
						EkonomiApi.beri(p, laci, "kota42.cabang.iuran");
						EkonomiApi.kas(owner, LembarKunci.BukuKas.SUMBER_LAPAK, false, laci, "kultivator.kota42.sekte.buku.iuran");
						laci = 0L;
					}
					t.putLong("laci", laci);
					lapor(t, hari, "kultivator.kota42.sekte.cabang.lap.balai", iuran);
				}
				case KLINIK -> {
					if (p != null) {
						int n = 0;
						for (dev.kultivator.dunia.kota42.warga.Warga w : dev.kultivator.dunia.kota42.api.WargaApi.wargaTempat(situs)) {
							if (w.status == dev.kultivator.dunia.kota42.warga.Warga.Status.HIDUP && n < 24) {
								dev.kultivator.dunia.kota42.api.WargaApi.ubahKesan(w, p, 1, "klinik_sekte", null);
								n++;
							}
						}
					}
					lapor(t, hari, "kultivator.kota42.sekte.cabang.lap.klinik", 0L);
				}
				default -> lapor(t, hari, "kultivator.kota42.sekte.cabang.lap.toko", 0L);
			}
		}
		KotaSekteData.get(server).ubah();
	}

	private static void lapor(CompoundTag t, long hari, String key, long nb) {
		ListTag lap = t.getListOrEmpty("lap");
		CompoundTag c = new CompoundTag();
		c.putLong("d", hari);
		c.putString("k", key);
		c.putLong("nb", nb);
		lap.add(c);
		while (lap.size() > 8) {
			lap.remove(0);
		}
		t.put("lap", lap);
	}

	/** The NB text of a value (screens). */
	public static Component nb(long nilai) {
		return Barang.nb(nilai);
	}

	// ================================================================== storage

	private static @Nullable CompoundTag peek(MinecraftServer server, long situs) {
		CompoundTag part = KotaSekteData.get(server).bagian(KotaSekteData.CABANG);
		String key = "kota_" + situs;
		return part.contains(key) ? part.getCompoundOrEmpty(key) : null;
	}

	private static List<Long> situsSemua(MinecraftServer server) {
		List<Long> out = new ArrayList<>();
		for (String key : KotaSekteData.get(server).bagian(KotaSekteData.CABANG).keySet()) {
			if (key.startsWith("kota_")) {
				try {
					out.add(Long.parseLong(key.substring(5)));
				} catch (NumberFormatException ignored) {
					// not a place key
				}
			}
		}
		return out;
	}

	private CabangKota() {
	}
}
