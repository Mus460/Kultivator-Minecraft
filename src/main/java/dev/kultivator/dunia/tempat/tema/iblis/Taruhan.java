package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Betting on the arena (DESIGN 38.T.7.5 "Taruhan") and the {@code tema_taruhan} screen of Tu Gang and the bookie (both
 * tabs: {@code taruhan}, {@code tangga}).
 * <ul>
 * <li>Malam Pertandingan: NPC against NPC every 20 world minutes ({@value #JEDA} ticks) from 19:00 to 23:00 (time of
 * day {@value #DARI}-{@value #SAMPAI}), twelve bouts a night. The fighters, their tier, style and odds are a pure
 * function of (town, day, bout): the same for every player, no storage.</li>
 * <li>Chance of the left fighter {@code p = 1 / (1 + e^-(0.55 x (tierL - tierR) + style))}, odds {@code EV / p} with
 * {@code temaIblis.taruhanEv} (0.90), stakes at most {@code 20 x (band + 1)} NB, 6 bets a night. A bet closes when its bout
 * starts; the result is drawn from the same seed when it ends, winnings come out of the city purse (stakes go in).</li>
 * <li>Live: with a player near the pit the two fighters stand in the sand and spar (clips {@code tanding_a/b}); the loser
 * kneels, then both leave. Otherwise the bout is data only.</li>
 * </ul>
 */
public final class Taruhan {

	public static final String ACARA = "malam_pertandingan";
	public static final int DARI = 13000;
	public static final int SAMPAI = 17000;
	public static final int JEDA = 333;
	public static final int LAGA_MALAM = 12;
	public static final int TARUHAN_MALAM = 6;
	public static final int GAYA = 6;

	/** One bout of a night (pure function of town, day and index). */
	public record Laga(int indeks, long mulai, int lookKiri, int lookKanan, int tierKiri, int tierKanan, int gayaKiri, int gayaKanan,
			double peluangKiri) {

		public double odds(boolean kiri) {
			double p = kiri ? peluangKiri : 1.0D - peluangKiri;
			return TemaConfig.iblis().taruhanEv / Math.max(0.05D, p);
		}

		/** The drawn winner (true = left), known once the bout ended. */
		public boolean kiriMenang(long situs, long hari) {
			long seed = situs * 0x9E3779B97F4A7C15L ^ hari * 0xC2B2AE3D27D4EB4FL ^ (indeks + 1L) * 0x165667B19E3779F9L;
			double roll = ((seed >>> 11) & 0xFFFFFFL) / (double) 0x1000000L;
			return roll < peluangKiri;
		}
	}

	private record Tontonan(KultivatorNpc kiri, KultivatorNpc kanan, Laga laga, long hari) {
	}

	private static final Map<Long, Tontonan> LIVE = new HashMap<>();

	private Taruhan() {
	}

	static void init() {
		TemaKalender.daftar(IblisData.TEMA, ACARA, DARI, SAMPAI, TemaKalender.selalu(), new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				IblisKota.timpa(level, situsId, "tema." + ACARA, SAMPAI - DARI);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				bubarkan(situsId);
				IblisKota.lepasTimpa(level, situsId, "tema." + ACARA);
			}
		});
		TemaLayar.daftar(TemaLayar.Taruhan.LAYAR, Taruhan::bangun, Taruhan::aksi);
	}

	static void clear() {
		LIVE.clear();
	}

	// ================================================================== the night's card

	/** Bout {@code i} of world day {@code hari} in town {@code situs}. */
	public static Laga laga(long situs, long hari, int i) {
		long seed = situs * 31L + hari * 1_000_003L + i * 97L;
		int lookL = (int) (seed * 0x2545F4914F6CDD1DL >>> 32);
		int lookR = (int) ((seed + 7L) * 0x2545F4914F6CDD1DL >>> 32);
		int tierL = 1 + Math.floorMod(lookL >>> 3, 6);
		int tierR = 1 + Math.floorMod(lookR >>> 3, 6);
		int gayaL = Math.floorMod(lookL >>> 9, GAYA);
		int gayaR = Math.floorMod(lookR >>> 9, GAYA);
		double style = GAYA_BOBOT[gayaL] - GAYA_BOBOT[gayaR];
		double p = 1.0D / (1.0D + Math.exp(-(0.55D * (tierL - tierR) + style)));
		p = Mth.clamp(p, 0.08D, 0.92D);
		return new Laga(i, DARI + (long) i * JEDA, lookL, lookR, tierL, tierR, gayaL, gayaR, p);
	}

	/** Style weights: ganas, licik, tenang, liar, bertahan, nekat. */
	private static final double[] GAYA_BOBOT = {0.25D, 0.15D, 0.10D, 0.05D, -0.05D, -0.20D};

	/** The bout running or next at time of day {@code jam}, or null after the last. */
	public static @Nullable Laga berikut(long situs, long hari, long jam) {
		for (int i = 0; i < LAGA_MALAM; i++) {
			Laga l = laga(situs, hari, i);
			if (jam < l.mulai() + JEDA) {
				return l;
			}
		}
		return null;
	}

	// ================================================================== live bouts and results

	/** Anchor {@code kota_tema/taruhan}: brings the two fighters of the running bout into the pit while players watch. */
	static void nearby(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		long jam = IblisKota.jam(level);
		long hari = IblisKota.hari(level);
		if (jam < DARI || jam >= SAMPAI) {
			bubarkan(kota.situsId());
			return;
		}
		Laga l = berikut(kota.situsId(), hari, jam);
		Tontonan t = LIVE.get(kota.situsId());
		if (t != null && (l == null || t.laga().indeks() != l.indeks() || t.hari() != hari)) {
			bubarkan(kota.situsId());
			t = null;
		}
		if (l == null || jam < l.mulai()) {
			return;
		}
		if (ArenaDarah.adaLaga(kota.situsId())) {
			bubarkan(kota.situsId());
			return;
		}
		if (t == null) {
			BlockPos kiri = IblisKota.blok(kota, ArenaDarah.DISTRIK, 42, 1, 48);
			BlockPos kanan = IblisKota.blok(kota, ArenaDarah.DISTRIK, 55, 1, 48);
			if (kiri == null || kanan == null) {
				return;
			}
			KultivatorNpc a = petarung(level, kota, kiri, l.lookKiri(), l.tierKiri(), IblisKota.yaw(kota, 270.0F));
			KultivatorNpc b = a == null ? null : petarung(level, kota, kanan, l.lookKanan(), l.tierKanan(), IblisKota.yaw(kota, 90.0F));
			if (a == null || b == null) {
				if (a != null) {
					a.vanish();
				}
				return;
			}
			t = new Tontonan(a, b, l, hari);
			LIVE.put(kota.situsId(), t);
			IblisAnchors.simpan(anchor, a);
			IblisAnchors.simpan(anchor, b);
		}
		long dalam = jam - l.mulai();
		if (dalam < JEDA - 60) {
			HidupApi.tatap(t.kiri(), t.kanan());
			HidupApi.tatap(t.kanan(), t.kiri());
			if (level.getGameTime() % 40L < 20L) {
				HidupApi.klip(t.kiri(), GerakId.TANDING_A, 0, 40);
				HidupApi.klip(t.kanan(), GerakId.TANDING_B, 0, 40);
			}
		} else {
			boolean kiriMenang = l.kiriMenang(kota.situsId(), hari);
			HidupApi.klip(kiriMenang ? t.kanan() : t.kiri(), GerakId.BERLUTUT, 0, 80);
			HidupApi.klip(kiriMenang ? t.kiri() : t.kanan(), GerakId.TEBAS_UDARA, 0, 40);
		}
	}

	private static @Nullable KultivatorNpc petarung(ServerLevel level, TemaKota.Kota kota, BlockPos pos, int look, int tier, float yaw) {
		KultivatorNpc npc = HidupApi.tubuh(level, kota.situsId(), NpcRole.GLADIATOR_IBLIS, pos, yaw, look);
		if (npc == null) {
			return null;
		}
		npc.setPlate(ArenaDarah.namaGladiator(look));
		npc.memory().putBoolean("hold", true);
		npc.memory().putBoolean("iblis_arena", true);
		npc.memory().putInt("tier_laga", tier);
		npc.setDiamDiTitik(true);
		npc.arm(true);
		return npc;
	}

	private static void bubarkan(long situs) {
		Tontonan t = LIVE.remove(situs);
		if (t != null) {
			t.kiri().vanish();
			t.kanan().vanish();
		}
	}

	/** Every 20 ticks: pays the bets of bouts that ended, for online players (offline winnings wait in their compound). */
	static void bayarTaruhan(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			CompoundTag mine = IblisData.pemain(server, p.getUUID());
			ListTag bets = mine.getListOrEmpty("taruhan");
			if (bets.isEmpty()) {
				continue;
			}
			ServerLevel level = p.level();
			long hari = IblisKota.hari(level);
			long jam = IblisKota.jam(level);
			ListTag keep = new ListTag();
			for (Tag t : bets) {
				if (!(t instanceof CompoundTag b)) {
					continue;
				}
				long situs = b.getLongOr("situs", 0L);
				long dayOf = b.getLongOr("hari", hari);
				Laga l = laga(situs, dayOf, b.getIntOr("laga", 0));
				boolean ended = dayOf < hari || jam >= l.mulai() + JEDA;
				if (!ended) {
					keep.add(b);
					continue;
				}
				boolean kiri = "kiri".equals(b.getStringOr("sisi", "kiri"));
				long nb = b.getLongOr("nb", 0L);
				if (dayOf == hari) {
					IblisData.hitungHariIni(server, situs, p.getUUID(), "taruhan_selesai", dayOf);
				}
				if (l.kiriMenang(situs, dayOf) == kiri) {
					long want = Math.round(nb * l.odds(kiri));
					long got = IblisData.ambilKas(server, situs, want);
					NilaiBatu.give(p, got);
					p.sendSystemMessage(Component.translatable(got < want ? "kultivator.kota_tema.iblis.taruhan.menang_kurang" : "kultivator.kota_tema.iblis.taruhan.menang",
							NilaiBatu.text(got), ArenaDarah.namaGladiator(kiri ? l.lookKiri() : l.lookKanan())).withStyle(net.minecraft.ChatFormatting.GOLD));
				} else {
					p.sendSystemMessage(Component.translatable("kultivator.kota_tema.iblis.taruhan.kalah", NilaiBatu.text(nb),
							ArenaDarah.namaGladiator(kiri ? l.lookKanan() : l.lookKiri())).withStyle(net.minecraft.ChatFormatting.GRAY));
				}
			}
			mine.put("taruhan", keep);
			IblisData.ubah(server);
		}
	}

	/** Bets placed tonight in this town. */
	static int taruhanMalamIni(ServerPlayer p, long situs, long hari) {
		int n = 0;
		for (Tag t : IblisData.pemain(p.level().getServer(), p.getUUID()).getListOrEmpty("taruhan")) {
			if (t instanceof CompoundTag b && b.getLongOr("situs", 0L) == situs && b.getLongOr("hari", -1L) == hari) {
				n++;
			}
		}
		return n + IblisData.hariIni(p.level().getServer(), situs, p.getUUID(), "taruhan_selesai", hari);
	}

	public static long maks(ServerPlayer p) {
		return 20L * (IblisKota.band(p) + 1L);
	}

	// ================================================================== the screen

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId) {
		KultivatorNpc npc = IblisAnchors.npc(p, npcId);
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return null;
		}
		CompoundTag tag = IblisLayar.kepala(p, "kultivator.kota_tema.iblis.layar.taruhan", LembarKunci.GAYA_BATU, npc);
		ServerLevel level = p.level();
		long hari = IblisKota.hari(level);
		long jam = IblisKota.jam(level);
		boolean tu = TokohIblis.adalah(npc, TokohIblis.TU_GANG);
		tag.putString(LembarKunci.TAB, tu ? TemaLayar.Taruhan.TAB_TANGGA : TemaLayar.Taruhan.TAB_TARUHAN);
		// --- tonight's bout
		Laga l = jam >= DARI && jam < SAMPAI ? berikut(kota.situsId(), hari, jam) : laga(kota.situsId(), jam >= SAMPAI ? hari + 1 : hari, 0);
		if (l != null) {
			CompoundTag laga = new CompoundTag();
			long start = l.mulai() + (jam >= SAMPAI ? 24000L - jam : -jam);
			laga.putLong(LembarKunci.SISA_TICK, Math.max(0L, start));
			Tontonan t = LIVE.get(kota.situsId());
			laga.put(TemaLayar.Taruhan.KIRI, petarungTag(l, true, t != null && t.laga().indeks() == l.indeks() ? t.kiri().getId() : -1));
			laga.put(TemaLayar.Taruhan.KANAN, petarungTag(l, false, t != null && t.laga().indeks() == l.indeks() ? t.kanan().getId() : -1));
			tag.put(TemaLayar.Taruhan.LAGA, laga);
		}
		CompoundTag bet = new CompoundTag();
		bet.putLong(LembarKunci.MAKS, maks(p));
		bet.putInt(TemaLayar.Taruhan.SISA, Math.max(0, TARUHAN_MALAM - taruhanMalamIni(p, kota.situsId(), hari)));
		ListTag mine = new ListTag();
		for (Tag t : IblisData.pemain(p.level().getServer(), p.getUUID()).getListOrEmpty("taruhan")) {
			if (t instanceof CompoundTag b && b.getLongOr("situs", 0L) == kota.situsId() && b.getLongOr("hari", -1L) == hari) {
				CompoundTag one = new CompoundTag();
				one.putString(TemaLayar.Taruhan.SISI, b.getStringOr("sisi", "kiri"));
				one.putLong(LembarKunci.NB, b.getLongOr("nb", 0L));
				one.putInt(LembarKunci.NO, b.getIntOr("laga", 0) + 1);
				mine.add(one);
			}
		}
		bet.put(TemaLayar.Taruhan.PASANG, mine);
		tag.put(TemaLayar.Taruhan.TARUHAN, bet);
		// --- the ladder
		CompoundTag tangga = new CompoundTag();
		int rank = ArenaDarah.peringkat(p, kota.situsId());
		tangga.putInt(TemaLayar.Taruhan.PERINGKAT, rank);
		long fee = ArenaDarah.biaya(p);
		tangga.putLong(LembarKunci.BIAYA, fee);
		tangga.putLong(TemaLayar.Taruhan.HADIAH, ArenaDarah.hadiah(fee));
		CompoundTag lawan = new CompoundTag();
		int tier = ArenaDarah.tierLawan(p, rank, false);
		boolean beast = rank == 3 || rank == 6;
		LembarKunci.teks(lawan, LembarKunci.NAMA, beast ? Component.translatable("kultivator.kota_tema.iblis.arena.binatang", tier)
				: Component.translatable("kultivator.kota_tema.iblis.arena.lawan_tangga", rank));
		LembarKunci.teks(lawan, TemaLayar.Taruhan.JULUKAN, Component.translatable(beast ? "kultivator.kota_tema.iblis.arena.lawan.binatang"
				: "kultivator.kota_tema.iblis.arena.lawan.gladiator"));
		lawan.putInt(TemaLayar.Taruhan.TIER, tier);
		tangga.put(TemaLayar.Taruhan.LAWAN, lawan);
		tangga.putInt(TemaLayar.Taruhan.SISA, ArenaDarah.sisaHariIni(p, kota.situsId()));
		Component no = ArenaDarah.alasan(p, kota);
		tangga.putBoolean(LembarKunci.BISA, no == null);
		if (no != null) {
			LembarKunci.teks(tangga, LembarKunci.ALASAN, no);
		}
		tag.put(TemaLayar.Taruhan.TANGGA, tangga);
		return tag;
	}

	private static CompoundTag petarungTag(Laga l, boolean kiri, int potret) {
		CompoundTag t = new CompoundTag();
		int look = kiri ? l.lookKiri() : l.lookKanan();
		LembarKunci.teks(t, LembarKunci.NAMA, ArenaDarah.namaGladiator(look));
		LembarKunci.teks(t, TemaLayar.Taruhan.JULUKAN, Component.translatable("kultivator.kota_tema.iblis.julukan." + Math.floorMod(look >>> 13, 16)));
		t.putInt(TemaLayar.Taruhan.TIER, kiri ? l.tierKiri() : l.tierKanan());
		LembarKunci.teks(t, TemaLayar.Taruhan.WATAK, Component.translatable("kultivator.kota_tema.iblis.gaya." + (kiri ? l.gayaKiri() : l.gayaKanan())));
		t.putFloat(LembarKunci.PELUANG, (float) (kiri ? l.peluangKiri() : 1.0D - l.peluangKiri()));
		t.putFloat(TemaLayar.Taruhan.ODDS, (float) l.odds(kiri));
		t.putInt(LembarKunci.POTRET, potret);
		return t;
	}

	private static void aksi(ServerPlayer p, int npcId, String op) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case TemaLayar.Taruhan.OP_PASANG -> pasang(p, kota, parts.length > 1 ? parts[1] : "kiri", IblisLayar.angka(parts, 2, 0L));
			case TemaLayar.Taruhan.OP_TANTANG -> {
				Component no = ArenaDarah.mulai(p, kota, ArenaDarah.JENIS_TANGGA, -1);
				if (no != null) {
					IblisLayar.pesan(p, no, false);
				} else {
					dev.kultivator.dunia.tempat.ui.TempatLayar.tutup(p);
				}
			}
			case "binatang" -> {
				Component no = ArenaDarah.mulai(p, kota, ArenaDarah.JENIS_BINATANG, -1);
				if (no != null) {
					IblisLayar.pesan(p, no, false);
				} else {
					dev.kultivator.dunia.tempat.ui.TempatLayar.tutup(p);
				}
			}
			case TemaLayar.Taruhan.OP_TEBUSAN_DARAH -> {
				Component no = KandangTawanan.tebusanDarah(p, kota, (int) IblisLayar.angka(parts, 1, -1L));
				if (no != null) {
					IblisLayar.pesan(p, no, false);
				} else {
					dev.kultivator.dunia.tempat.ui.TempatLayar.tutup(p);
				}
			}
			default -> {
			}
		}
	}

	private static void pasang(ServerPlayer p, TemaKota.Kota kota, String sisi, long nb) {
		ServerLevel level = p.level();
		long hari = IblisKota.hari(level);
		long jam = IblisKota.jam(level);
		if (!"kiri".equals(sisi) && !"kanan".equals(sisi)) {
			return;
		}
		if (jam < DARI - 2000 || jam >= SAMPAI) {
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.taruhan.tutup"), false);
			return;
		}
		Laga l = berikut(kota.situsId(), hari, jam);
		if (l == null || jam >= l.mulai()) {
			Laga next = l == null ? null : l.indeks() + 1 < LAGA_MALAM ? laga(kota.situsId(), hari, l.indeks() + 1) : null;
			if (next == null) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.taruhan.tutup"), false);
				return;
			}
			l = next;
		}
		if (nb <= 0L || nb > maks(p)) {
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.taruhan.batas", NilaiBatu.text(maks(p))), false);
			return;
		}
		if (taruhanMalamIni(p, kota.situsId(), hari) >= TARUHAN_MALAM) {
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.taruhan.habis", TARUHAN_MALAM), false);
			return;
		}
		if (!NilaiBatu.pay(p, nb)) {
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(nb)), false);
			return;
		}
		MinecraftServer server = p.level().getServer();
		IblisData.tambahKas(server, kota.situsId(), nb);
		CompoundTag b = new CompoundTag();
		b.putLong("situs", kota.situsId());
		b.putLong("hari", hari);
		b.putInt("laga", l.indeks());
		b.putString("sisi", sisi);
		b.putLong("nb", nb);
		IblisData.daftar(IblisData.pemain(server, p.getUUID()), "taruhan").add(b);
		IblisData.ubah(server);
		IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.taruhan.pasang", NilaiBatu.text(nb),
				ArenaDarah.namaGladiator("kiri".equals(sisi) ? l.lookKiri() : l.lookKanan()), String.format(java.util.Locale.ROOT, "%.2f", l.odds("kiri".equals(sisi)))), true);
	}

	/** Test helper ({@code uji tema taruhan}): tonight's card as chat lines. */
	static List<Component> kartu(ServerLevel level, long situs) {
		List<Component> out = new ArrayList<>();
		long hari = IblisKota.hari(level);
		for (int i = 0; i < LAGA_MALAM; i++) {
			Laga l = laga(situs, hari, i);
			out.add(Component.translatable("kultivator.kota_tema.iblis.taruhan.baris", i + 1, ArenaDarah.namaGladiator(l.lookKiri()), l.tierKiri(),
					String.format(java.util.Locale.ROOT, "%.2f", l.odds(true)), ArenaDarah.namaGladiator(l.lookKanan()), l.tierKanan(),
					String.format(java.util.Locale.ROOT, "%.2f", l.odds(false)), l.kiriMenang(situs, hari) ? "<" : ">"));
		}
		return out;
	}
}
