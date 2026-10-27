package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.Isian;
import dev.kultivator.dunia.tempat.ekonomi.BukuKas;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lapak Kota (DESIGN 42.6.2, 42.11.14): the rented market stalls of a town, one per {@code lapak_sewa_<n>} titik of 42W
 * (2-8 per market), each under a {@code tenda_lapak} entity re-made here whenever a player is near (never saved). Rent
 * {@code 6 x (b+1)} NB per 7 days (b = the renter's realm band), 1-4 weeks ahead; Nama Baik 0 or more and not wanted;
 * one stall per player per place. A rented stall is a small player claim ({@link #RADIUS} blocks around the titik, 4 high)
 * so the racks, counter and sign the renter puts there are his own blocks (38.R never removes or bills them); its shop is
 * a {@link TokoPemain} of kind {@code lapak}. Clicking the tent: the renter manages his shop, others read whose it is.
 * <p>
 * State: {@code KotaEkonomiData.situs("toko", situs)["lapak"]}: no -> {@code p} pos, {@code h} facing, {@code d} district,
 * {@code o} renter, {@code s} paid to day, {@code b} band at renting.
 */
public final class LapakKota {

	/** Half size of a stall's claim box (5 x 5 around the titik). */
	public static final int RADIUS = 2;
	public static final int TINGGI = 4;
	public static final int MINGGU_MAKS = 4;
	/** Blocks within which the tents stand for a player. */
	public static final double JARAK_TENDA = 64.0D;

	/** One stall. */
	public record Lapak(long situs, int no, BlockPos pos, Direction hadap, String distrik, @Nullable UUID penyewa, long sampai) {

		public BoundingBox kotak() {
			return new BoundingBox(pos.getX() - RADIUS, pos.getY() - 1, pos.getZ() - RADIUS, pos.getX() + RADIUS, pos.getY() + TINGGI - 1,
					pos.getZ() + RADIUS);
		}

		public boolean disewa() {
			return penyewa != null;
		}
	}

	private record Klaim(long situs, int no, BoundingBox kotak) {
	}

	private static volatile @Nullable List<Klaim> klaim;
	/** Tents standing: "situs:no" -> entity. */
	private static final Map<String, TendaLapakEntity> TENDA = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ProtectedZones.addClaimProvider(LapakKota::diklaim);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof TendaLapakEntity tenda) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			return klik(sp, tenda) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	static void lupakan() {
		klaim = null;
		TENDA.values().forEach(t -> t.discard());
		TENDA.clear();
	}

	// ================================================================== reading

	/** The rent of a stall per 7 days for realm band {@code band}. */
	public static long harga(int band) {
		return 6L * (Math.max(0, band) + 1);
	}

	/** Every stall of a place (the titik of loaded pieces are read in first). */
	public static List<Lapak> daftar(MinecraftServer server, long situs) {
		pindai(server.overworld(), situs);
		List<Lapak> out = new ArrayList<>();
		CompoundTag all = lapakTag(server, situs);
		for (String key : all.keySet()) {
			out.add(baca(situs, all.getCompoundOrEmpty(key)));
		}
		out.sort((a, b) -> Integer.compare(a.no(), b.no()));
		return out;
	}

	/** One stall, or null. */
	public static @Nullable Lapak lapak(MinecraftServer server, long situs, int no) {
		CompoundTag all = lapakTag(server, situs);
		String key = Integer.toString(no);
		return all.contains(key) ? baca(situs, all.getCompoundOrEmpty(key)) : null;
	}

	/** The stall {@code penyewa} rents in {@code situs}, or null. */
	public static @Nullable Lapak sewaan(MinecraftServer server, long situs, UUID penyewa) {
		CompoundTag all = lapakTag(server, situs);
		for (String key : all.keySet()) {
			Lapak l = baca(situs, all.getCompoundOrEmpty(key));
			if (penyewa.equals(l.penyewa())) {
				return l;
			}
		}
		return null;
	}

	/** Every stall {@code penyewa} rents (all places). */
	public static List<Lapak> semuaSewaan(MinecraftServer server, UUID penyewa) {
		List<Lapak> out = new ArrayList<>();
		for (Lapak l : semua(server)) {
			if (penyewa.equals(l.penyewa())) {
				out.add(l);
			}
		}
		return out;
	}

	/** The rented stall whose claim box holds {@code pos}, or null. */
	public static @Nullable Lapak di(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		for (Klaim k : klaim(level.getServer())) {
			if (k.kotak().isInside(pos)) {
				return lapak(level.getServer(), k.situs(), k.no());
			}
		}
		return null;
	}

	/** Claim provider: a rented stall's box. */
	public static boolean diklaim(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		for (Klaim k : klaim(level.getServer())) {
			if (k.kotak().isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== actions

	/** {@code sewa:ID:MINGGU}. */
	public static RumahKota.Hasil sewa(ServerPlayer player, long situs, int no, int minggu) {
		MinecraftServer server = player.level().getServer();
		KotaConfig.Ekonomi cfg = KotaConfig.ekonomi();
		Lapak l = lapak(server, situs, no);
		if (!cfg.aktif || !cfg.toko) {
			return kirim(player, gagal("mati"));
		}
		if (l == null || l.disewa()) {
			return kirim(player, gagal("tidak_bebas"));
		}
		if (KotaBesarApi.buronan(player, situs) || KotaData.get(server).reputation(situs, player.getUUID()) < 0) {
			return kirim(player, gagal("ditolak"));
		}
		if (sewaan(server, situs, player.getUUID()) != null) {
			return kirim(player, gagal("satu"));
		}
		int band = Isian.pita(player);
		int w = Math.max(1, Math.min(MINGGU_MAKS, minggu));
		long biaya = harga(band) * w;
		if (!EkonomiApi.bayar(player, biaya, "lapak_kota")) {
			return kirim(player, gagal("uang", NilaiBatu.text(biaya)));
		}
		CompoundTag t = lapakTag(server, situs).getCompoundOrEmpty(Integer.toString(no));
		t.store("o", UUIDUtil.CODEC, player.getUUID());
		t.putLong("s", KotaHidup.hari(server) + 7L * w);
		t.putInt("b", band);
		KasKota.pajak(server, situs, biaya, "lapak");
		BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_SEWA, 0, -biaya, "kultivator.kota42.lapak.buku.sewa", no, w);
		berubah(server);
		TokoPemain.bukaLapak(server, situs, no, player);
		return kirim(player, new RumahKota.Hasil(true, Component.translatable("kultivator.kota42.lapak.disewa", no, w, NilaiBatu.text(biaya),
				KotaHidup.hari(server) + 7L * w)));
	}

	/** {@code perpanjang:ID:MINGGU} (at most 4 weeks ahead of today). */
	public static RumahKota.Hasil perpanjang(ServerPlayer player, long situs, int no, int minggu) {
		MinecraftServer server = player.level().getServer();
		Lapak l = lapak(server, situs, no);
		if (l == null || !player.getUUID().equals(l.penyewa())) {
			return kirim(player, gagal("bukan_milik"));
		}
		long hari = KotaHidup.hari(server);
		long dari = Math.max(hari, l.sampai());
		int w = Math.max(1, Math.min(minggu, (int) ((hari + 7L * MINGGU_MAKS - dari) / 7L)));
		if (dari + 7L * w > hari + 7L * MINGGU_MAKS) {
			return kirim(player, gagal("terlalu_jauh", MINGGU_MAKS));
		}
		long biaya = harga(Isian.pita(player)) * w;
		if (!EkonomiApi.bayar(player, biaya, "lapak_kota")) {
			return kirim(player, gagal("uang", NilaiBatu.text(biaya)));
		}
		lapakTag(server, situs).getCompoundOrEmpty(Integer.toString(no)).putLong("s", dari + 7L * w);
		KasKota.pajak(server, situs, biaya, "lapak");
		BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_SEWA, 0, -biaya, "kultivator.kota42.lapak.buku.sewa", no, w);
		berubah(server);
		return kirim(player, new RumahKota.Hasil(true, Component.translatable("kultivator.kota42.lapak.diperpanjang", no, dari + 7L * w)));
	}

	/** {@code lepas:ID}: gives the stall up now (no refund; the shop closes, the racks stay the player's blocks). */
	public static RumahKota.Hasil lepas(ServerPlayer player, long situs, int no) {
		MinecraftServer server = player.level().getServer();
		Lapak l = lapak(server, situs, no);
		if (l == null || !player.getUUID().equals(l.penyewa())) {
			return kirim(player, gagal("bukan_milik"));
		}
		bebaskan(server, situs, no);
		return kirim(player, new RumahKota.Hasil(true, Component.translatable("kultivator.kota42.lapak.dilepas", no)));
	}

	/** The tent was clicked: the renter gets his shop's summary, anyone else the stall's state. */
	static boolean klik(ServerPlayer player, TendaLapakEntity tenda) {
		MinecraftServer server = player.level().getServer();
		long situs = tenda.situsId();
		int no = (int) tenda.jangkar();
		Lapak l = situs == 0L ? null : lapak(server, situs, no);
		if (l == null) {
			return false;
		}
		if (player.getUUID().equals(l.penyewa())) {
			TokoPemain.Toko toko = TokoPemain.tokoLapak(server, situs, no);
			if (toko != null) {
				TokoPemain.ringkas(player, toko);
			}
			return true;
		}
		if (l.disewa()) {
			ServerPlayer renter = server.getPlayerList().getPlayer(l.penyewa());
			Component who = renter != null ? renter.getName() : Component.translatable("kultivator.kota42.lapak.penyewa_jauh");
			Msg.chat(player, Component.translatable("kultivator.kota42.lapak.milik", no, who).withStyle(ChatFormatting.GRAY));
		} else {
			Msg.chat(player, Component.translatable("kultivator.kota42.lapak.bebas", no, NilaiBatu.text(harga(Isian.pita(player))), situs, no)
					.withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	// ================================================================== days and tents

	/** A new world day: leases run out. */
	static void hariBaru(MinecraftServer server, long hari) {
		for (Lapak l : semua(server)) {
			if (!l.disewa() || l.sampai() >= hari) {
				continue;
			}
			ServerPlayer online = server.getPlayerList().getPlayer(l.penyewa());
			bebaskan(server, l.situs(), l.no());
			if (online != null) {
				Msg.chat(online, Component.translatable("kultivator.kota42.lapak.habis", l.no()).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void bebaskan(MinecraftServer server, long situs, int no) {
		CompoundTag t = lapakTag(server, situs).getCompoundOrEmpty(Integer.toString(no));
		t.remove("o");
		t.remove("s");
		TokoPemain.tutupLapak(server, situs, no);
		berubah(server);
	}

	/** Every 2 s: tents stand on the stalls of places with a player within {@link #JARAK_TENDA} blocks. */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Set<String> wanted = new HashSet<>();
		if (KotaConfig.ekonomi().aktif && KotaConfig.ekonomi().toko) {
			Set<Long> places = new HashSet<>();
			for (ServerPlayer p : level.players()) {
				TempatInfo t = TempatApi.tempatDi(level, p.blockPosition());
				if (t != null) {
					places.add(t.situsId());
				}
			}
			for (long situs : places) {
				for (Lapak l : daftar(server, situs)) {
					if (!level.isLoaded(l.pos()) || level.getNearestPlayer(l.pos().getX() + 0.5D, l.pos().getY(), l.pos().getZ() + 0.5D, JARAK_TENDA, false) == null) {
						continue;
					}
					String key = situs + ":" + l.no();
					wanted.add(key);
					TendaLapakEntity tenda = TENDA.get(key);
					if (tenda == null || tenda.isRemoved()) {
						if (DuniaBudget.count(KotaAcaraEntities.ANGGARAN) >= DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
							continue;
						}
						tenda = KotaEkonomiEntities.TENDA_LAPAK.create(level, EntitySpawnReason.EVENT);
						if (tenda == null) {
							continue;
						}
						tenda.ikat(situs, l.no());
						tenda.pindah(Vec3.atBottomCenterOf(l.pos()), l.hadap().toYRot());
						tenda.setKeadaan(TendaLapakEntity.KEADAAN_DIPASANG);
						level.addFreshEntity(tenda);
						TENDA.put(key, tenda);
					}
					hias(tenda, l);
				}
			}
		}
		TENDA.entrySet().removeIf(e -> {
			if (!wanted.contains(e.getKey()) || e.getValue().isRemoved()) {
				e.getValue().discard();
				return true;
			}
			return false;
		});
	}

	/** The tent's look: the renter's stripe colour and goods, or bare faded cloth when free. */
	private static void hias(TendaLapakEntity tenda, Lapak l) {
		if (tenda.keadaan() == TendaLapakEntity.KEADAAN_DIPASANG && tenda.nilai() >= 1.0F) {
			tenda.setKeadaan(TendaLapakEntity.KEADAAN_BERDIRI);
		} else if (tenda.keadaan() == TendaLapakEntity.KEADAAN_DIPASANG) {
			tenda.setNilai(Math.min(1.0F, tenda.nilai() + 40.0F / TendaLapakEntity.RAKIT_TICK));
		}
		if (l.disewa()) {
			int h = l.penyewa().hashCode();
			int[] warna = {0xB0413E, 0x2F5D8A, 0x3E7A3A, 0xC78A1E, 0x6B3E8C, 0x1E7A78};
			tenda.setWarna(warna[Math.floorMod(h, warna.length)]);
			tenda.setVarian(Math.floorMod(h >>> 8, TendaLapakEntity.VARIAN_JUMLAH));
		} else {
			tenda.setWarna(0x9A8F7A);
			tenda.setVarian(TendaLapakEntity.VARIAN_GERABAH);
		}
	}

	// ================================================================== discovery and helpers

	private static void pindai(ServerLevel level, long situs) {
		MinecraftServer server = level.getServer();
		CompoundTag d = KotaEkonomiData.get(server).situs(KotaEkonomiData.TOKO, situs);
		long now = server.getTickCount();
		if (now - d.getLongOr("pindai_lapak", -100000L) < 1200L || TempatApi.potongan(level, situs).isEmpty()) {
			return;
		}
		d.putLong("pindai_lapak", now);
		CompoundTag all = lapakTag(server, situs);
		for (Titik t : KotaTitik.bernomor(level, situs, KotaTitik.LAPAK_SEWA)) {
			int no = KotaTitik.nomor(t.jenis());
			String key = Integer.toString(no);
			CompoundTag e = all.getCompoundOrEmpty(key);
			e.putInt("no", no);
			e.putLong("p", t.pos().asLong());
			e.putInt("h", t.hadap().get2DDataValue());
			TempatInfo info = TempatApi.tempatDi(level, t.pos());
			e.putString("d", info == null || info.distrik() == null ? LembarKunci.DISTRIK_PASAR : info.distrik());
			all.put(key, e);
		}
		KotaEkonomiData.get(server).ubah();
	}

	private static List<Lapak> semua(MinecraftServer server) {
		List<Lapak> out = new ArrayList<>();
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.TOKO);
		for (String key : part.keySet()) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			long situs;
			try {
				situs = Long.parseLong(key.substring(5));
			} catch (NumberFormatException e) {
				continue;
			}
			CompoundTag all = part.getCompoundOrEmpty(key).getCompoundOrEmpty("lapak");
			for (String no : all.keySet()) {
				out.add(baca(situs, all.getCompoundOrEmpty(no)));
			}
		}
		return out;
	}

	private static Lapak baca(long situs, CompoundTag t) {
		return new Lapak(situs, t.getIntOr("no", 0), BlockPos.of(t.getLongOr("p", 0L)), Direction.from2DDataValue(t.getIntOr("h", 0)),
				t.getStringOr("d", LembarKunci.DISTRIK_PASAR), t.read("o", UUIDUtil.CODEC).orElse(null), t.getLongOr("s", 0L));
	}

	private static CompoundTag lapakTag(MinecraftServer server, long situs) {
		CompoundTag d = KotaEkonomiData.get(server).situs(KotaEkonomiData.TOKO, situs);
		CompoundTag all = d.getCompoundOrEmpty("lapak");
		if (!d.contains("lapak")) {
			d.put("lapak", all);
		}
		// stalls carry their number in the key; copy it into each entry for baca()
		for (String key : all.keySet()) {
			CompoundTag e = all.getCompoundOrEmpty(key);
			if (!e.contains("no")) {
				try {
					e.putInt("no", Integer.parseInt(key));
				} catch (NumberFormatException ignored) {
					// a foreign key: left alone
				}
			}
		}
		return all;
	}

	private static List<Klaim> klaim(MinecraftServer server) {
		List<Klaim> now = klaim;
		if (now != null) {
			return now;
		}
		List<Klaim> out = new ArrayList<>();
		if (server != null) {
			for (Lapak l : semua(server)) {
				if (l.disewa()) {
					out.add(new Klaim(l.situs(), l.no(), l.kotak()));
				}
			}
		}
		klaim = List.copyOf(out);
		return klaim;
	}

	private static void berubah(MinecraftServer server) {
		KotaEkonomiData.get(server).ubah();
		klaim = null;
	}

	private static RumahKota.Hasil gagal(String key, Object... args) {
		return new RumahKota.Hasil(false, Component.translatable("kultivator.kota42.lapak.gagal." + key, args));
	}

	private static RumahKota.Hasil kirim(ServerPlayer player, RumahKota.Hasil hasil) {
		Msg.chat(player, hasil.pesan().copy().withStyle(hasil.ok() ? ChatFormatting.GOLD : ChatFormatting.RED));
		return hasil;
	}

	private LapakKota() {
	}
}
