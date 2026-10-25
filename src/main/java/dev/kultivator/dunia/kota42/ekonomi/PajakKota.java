package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Town tax and the town ledger (DESIGN 42.6.0, 42.6.6), and the root of module 42C-2: {@link #init()} starts every sub-module
 * of investments, warehouses, contracts, offices, cases and the Ghost Market, owns the one server tick of the module and
 * runs the daily and weekly passes for every raised place (a place whose residents 42A has stored).
 * <p>
 * <b>Tax</b>: rate 5-20 % per place (default {@code kotaEkonomi.pajakBawaan} 8 %). The weekly income of the Kas Kota is the
 * rate times the week's profit of every resident business ({@link UsahaWarga}) plus what flows in live during the week
 * through {@link KasKota#pajak}: property tax 1 % and player shop tax 5 % (42C), customs (38.T), stall and warehouse rent
 * ({@link GudangKota}). Automatic spending each week: the town guard ({@code PENJAGA}/{@code PENJAGA_MALAM} residents, 3 NB
 * each), the festival budget set by a Wali Kota, office wages ({@link Jabatan#gajiMingguan}); 42F pays plot material and
 * rebuilding through {@link KasKota#bayar}. Never negative: unpaid items wait for next week.
 * <p>
 * <b>Effects</b>: a rate above 10 % lowers the place mood by one per point (place layer of {@code Suasana}); below 6 % gives
 * prosperity +1 a week ({@link #bonusMakmur}, read by 42F's {@code Kemakmuran}).
 * <p>
 * State in {@code KotaEkonomiData.bagian("pajak")}: {@code hari}/{@code minggu} last passes; per place
 * ({@code situs("pajak", id)}): {@code t} rate, {@code m}/{@code k} income and spending long[8] (newest first), {@code u}
 * business tax long[8], {@code fb} festival budget NB per week (Wali Kota), {@code tu} day the rate last changed,
 * {@code oleh} who changed it, {@code pj} guard wages owed.
 */
public final class PajakKota {

	public static final int TARIF_MIN = 5;
	public static final int TARIF_MAKS = 20;
	/** Above this rate the mood falls one per point (42.6.6). */
	public static final int TARIF_MURUNG = 10;
	/** Below this rate prosperity rises +1 a week. */
	public static final int TARIF_MAKMUR = 6;
	/** Weekly wage of one guard resident, paid by the Kas Kota. */
	public static final long GAJI_PENJAGA = 3L;
	/** Days a Wakil Wali Kota must wait between two rate changes. */
	public static final int JEDA_UBAH_HARI = 7;
	/** Most a Wali Kota may set aside for festivals per week. */
	public static final long FESTIVAL_MAKS = 400L;
	public static final int RIWAYAT = 8;

	/** Kinds of place this module distinguishes (42.6.6 offices, 42.6.7 Ghost Market). */
	public static final String JT_KAMPUNG = "kampung";
	public static final String JT_POS = "pos";
	public static final String JT_KOTA = "kota";
	public static final String JT_TEMA = "tema";
	public static final String JT_IBU_KOTA = "ibu_kota";
	public static final String JT_LAIN = "lain";

	private static boolean initialised;

	/** From {@code KotaEkonomi.init} (42C). Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		UsahaWarga.init();
		Investasi.init();
		Jabatan.init();
		Perkara.init();
		GudangKota.init();
		KontrakDagang.init();
		RumahDagang.init();
		PasarHantu.init();
		Suasana.addPengaruhTempat(PajakKota::pengaruhSuasana);
		ServerTickEvents.END_SERVER_TICK.register(PajakKota::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PasarHantu.lupakan();
			GudangKota.lupakan();
			Perkara.lupakan();
		});
		KotaHidupCommands.daftar("investasi", Investasi::perintah);
		KotaHidupCommands.daftar("gudang", GudangKota::perintah);
		KotaHidupCommands.daftar("kontrak", KontrakDagang::perintah);
		KotaHidupCommands.daftar("jabatan", Jabatan::perintah);
		KotaHidupCommands.daftar("perkara", Perkara::perintah);
		KotaHidupCommands.daftar("pasar_hantu", PasarHantu::perintah);
		KotaHidupCommands.daftar("laci", Investasi::perintahLaci);
		KotaUji.daftar("ekonomi", PajakKota::uji);
		KotaUji.chips("kota_hidup ekonomi kas", "kota_hidup ekonomi usaha", "kota_hidup ekonomi minggu");
	}

	public static boolean aktif() {
		return KotaConfig.ekonomi().aktif && KotaConfig.ekonomi().pajak;
	}

	// ================================================================== the one tick of 42C-2

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 20 != 0 || !KotaConfig.ekonomi().aktif || KotaHidup.server() != server) {
			return;
		}
		try {
			KotaEkonomiData data = KotaEkonomiData.get(server);
			CompoundTag umum = data.bagian(KotaEkonomiData.PAJAK);
			long hari = KotaHidup.hari(server);
			long last = umum.getLongOr("hari", -1L);
			if (last != hari) {
				umum.putLong("hari", hari);
				data.ubah();
				if (last >= 0L) {
					harian(server, hari);
				}
			}
			long minggu = Math.floorDiv(hari, 7L);
			long lastWeek = umum.getLongOr("minggu", -1L);
			if (lastWeek != minggu) {
				umum.putLong("minggu", minggu);
				data.ubah();
				if (lastWeek >= 0L) {
					mingguan(server, minggu);
				}
			}
			GudangKota.tick(server, tick);
			PasarHantu.tick(server, tick);
			Perkara.tick(server, tick);
			if (tick % 200 == 0) {
				Jabatan.tickHadir(server);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kota ekonomi (42C-2) tick failed", exception);
		}
	}

	private static void harian(MinecraftServer server, long hari) {
		for (long situs : tempatHidup(server)) {
			UsahaWarga.harian(server, situs, hari);
			Perkara.harian(server, situs, hari);
		}
		GudangKota.harian(server, hari);
		KontrakDagang.harian(server, hari);
		RumahDagang.harian(server, hari);
		Investasi.harian(server, hari);
	}

	private static void mingguan(MinecraftServer server, long minggu) {
		boolean on = aktif();
		for (long situs : tempatHidup(server)) {
			long labaUsaha = UsahaWarga.mingguan(server, situs, minggu);
			if (!on) {
				continue;
			}
			CompoundTag t = KotaEkonomiData.get(server).situs(KotaEkonomiData.PAJAK, situs);
			long pajakUsaha = Math.max(0L, Math.round(Math.max(0L, labaUsaha) * tarif(server, situs) / 100.0D));
			KasKota.pajak(server, situs, pajakUsaha, "usaha");
			geser(t, "u", pajakUsaha);
			// automatic spending: guards first, then the festival purse, then office wages
			long owed = t.getLongOr("pj", 0L) + penjaga(situs) * GAJI_PENJAGA;
			if (owed > 0L && KasKota.bayar(server, situs, owed, "penjaga")) {
				owed = 0L;
			}
			t.putLong("pj", Math.min(owed, 500L));
			long festival = t.getLongOr("fb", 0L);
			if (festival > 0L) {
				KasKota.bayar(server, situs, Math.min(festival, KasKota.saldo(server, situs)), "festival");
			}
			long masukMinggu = jumlah(KasKota.buku(server, situs, minggu - 1L, false));
			Jabatan.gajiMingguan(server, situs, masukMinggu);
			geser(t, "m", masukMinggu);
			geser(t, "k", jumlah(KasKota.buku(server, situs, minggu - 1L, true)));
			KotaEkonomiData.get(server).ubah();
		}
		Investasi.mingguan(server, minggu);
		KontrakDagang.mingguan(server, minggu);
		Jabatan.mingguan(server, minggu);
	}

	private static long jumlah(Map<String, Long> map) {
		long n = 0L;
		for (long v : map.values()) {
			n += v;
		}
		return n;
	}

	private static void geser(CompoundTag t, String key, long value) {
		long[] old = t.getLongArray(key).orElse(new long[0]);
		long[] next = new long[RIWAYAT];
		next[0] = value;
		System.arraycopy(old, 0, next, 1, Math.min(old.length, RIWAYAT - 1));
		t.putLongArray(key, next);
	}

	private static int penjaga(long situs) {
		int n = 0;
		for (Warga w : WargaApi.wargaTempat(situs)) {
			NpcRole role = w.peranNpc();
			if (w.hidup() && (role == NpcRole.PENJAGA || role == NpcRole.PENJAGA_MALAM || role == NpcRole.PENJAGA_GERBANG)) {
				n++;
			}
		}
		return n;
	}

	// ================================================================== places

	/** Every place whose residents are stored and were raised (Hari Warga ran at least once). */
	public static List<Long> tempatHidup(MinecraftServer server) {
		List<Long> out = new ArrayList<>();
		for (WargaData.Situs s : WargaData.get(server).semuaSitus()) {
			if (s.hariTerakhir >= 0L && !s.warga.isEmpty()) {
				out.add(s.id);
			}
		}
		return out;
	}

	/** The kind of place {@code situs}: {@link #JT_KAMPUNG}, {@link #JT_POS}, {@link #JT_KOTA}, {@link #JT_TEMA}, ... */
	public static String jenisTempat(MinecraftServer server, long situs) {
		if (TemaKota.dari(server, situs) != null) {
			return JT_TEMA;
		}
		DuniaSites.Site site = DuniaSites.byId(server, situs);
		String kind = site != null ? site.kind() : Towns.known(server).contains(situs) ? Towns.jenis(server, situs) : "";
		if (Towns.JENIS_KOTA_BESAR.equals(kind)) {
			return JT_IBU_KOTA;
		}
		if (Towns.JENIS_KOTA_TEMA.equals(kind) || kind.startsWith("kota_tema/")) {
			return JT_TEMA;
		}
		if (kind.startsWith("kota/")) {
			return JT_KOTA;
		}
		if (kind.startsWith("kampung/")) {
			return JT_KAMPUNG;
		}
		if (kind.startsWith("pos/")) {
			return JT_POS;
		}
		return JT_LAIN;
	}

	/** Town class 1 kampung/pos, 2 kota, 3 kota tema, 4 ibu kota (KotaLembar {@code kelas_kota}). */
	public static int kelas(MinecraftServer server, long situs) {
		return switch (jenisTempat(server, situs)) {
			case JT_IBU_KOTA -> 4;
			case JT_TEMA -> 3;
			case JT_KOTA -> 2;
			default -> 1;
		};
	}

	/** True for Kota Iblis (it keeps its own black market: no Ghost Market). */
	public static boolean kotaIblis(MinecraftServer server, long situs) {
		TemaKota.Kota kota = TemaKota.dari(server, situs);
		return kota != null && kota.tema() == Tema.IBLIS;
	}

	/** The place's name ("Kota Teratai Hijau"). */
	public static Component nama(MinecraftServer server, long situs) {
		return Dagang.namaSitus(server, situs);
	}

	/** The place a player stands in or is nearest (160 blocks, overworld), or null. */
	public static @Nullable TempatInfo tempat(ServerPlayer player) {
		return KotaHidupCommands.tempatPemain(player);
	}

	// ================================================================== rate

	/** The tax rate of a place in % (5-20). */
	public static int tarif(MinecraftServer server, long situs) {
		CompoundTag t = KotaEkonomiData.get(server).situs(KotaEkonomiData.PAJAK, situs);
		return Mth.clamp(t.getIntOr("t", KotaConfig.ekonomi().pajakBawaan), TARIF_MIN, TARIF_MAKS);
	}

	/** The limits a player of office rank {@code pangkat} may set ({@code [min, max]}; equal = cannot change). */
	public static int[] batasUbah(MinecraftServer server, long situs, int pangkat) {
		int now = tarif(server, situs);
		if (pangkat >= Jabatan.WALI_KOTA) {
			return new int[] {TARIF_MIN, TARIF_MAKS};
		}
		if (pangkat >= Jabatan.WAKIL) {
			int base = KotaConfig.ekonomi().pajakBawaan;
			return new int[] {Math.max(TARIF_MIN, base - 2), Math.min(TARIF_MAKS, base + 2)};
		}
		return new int[] {now, now};
	}

	/**
	 * Sets the rate on behalf of {@code player} (rank 4: default +-2 once a week; rank 5: 5-20); returns null on success or
	 * the refusal.
	 */
	public static @Nullable Component ubahTarif(ServerPlayer player, long situs, int tarif) {
		MinecraftServer server = player.level().getServer();
		if (!aktif()) {
			return Component.translatable("kultivator.kota42.pajak.mati");
		}
		int rank = Jabatan.pangkat(player.getUUID(), situs);
		int[] range = batasUbah(server, situs, rank);
		if (range[0] == range[1]) {
			return Component.translatable("kultivator.kota42.pajak.tak_berhak");
		}
		if (tarif < range[0] || tarif > range[1]) {
			return Component.translatable("kultivator.kota42.pajak.luar_batas", range[0], range[1]);
		}
		CompoundTag t = KotaEkonomiData.get(server).situs(KotaEkonomiData.PAJAK, situs);
		long hari = KotaHidup.hari(server);
		if (rank < Jabatan.WALI_KOTA && hari - t.getLongOr("tu", -100L) < JEDA_UBAH_HARI) {
			return Component.translatable("kultivator.kota42.pajak.jeda", JEDA_UBAH_HARI - (hari - t.getLongOr("tu", -100L)));
		}
		setTarif(server, situs, tarif, player.getScoreboardName());
		return null;
	}

	/** Sets the rate (tests, Wali Kota); clamps to 5-20. */
	public static void setTarif(MinecraftServer server, long situs, int tarif, String oleh) {
		CompoundTag t = KotaEkonomiData.get(server).situs(KotaEkonomiData.PAJAK, situs);
		t.putInt("t", Mth.clamp(tarif, TARIF_MIN, TARIF_MAKS));
		t.putLong("tu", KotaHidup.hari(server));
		t.putString("oleh", oleh == null ? "" : oleh);
		KotaEkonomiData.get(server).ubah();
	}

	/** The festival budget of a place, NB per week (set by a Wali Kota). */
	public static long anggaranFestival(MinecraftServer server, long situs) {
		return KotaEkonomiData.get(server).situs(KotaEkonomiData.PAJAK, situs).getLongOr("fb", 0L);
	}

	/** Wali Kota only: the weekly festival budget 0-{@link #FESTIVAL_MAKS}; returns null on success or the refusal. */
	public static @Nullable Component setAnggaranFestival(ServerPlayer player, long situs, long nb) {
		if (Jabatan.pangkat(player.getUUID(), situs) < Jabatan.WALI_KOTA) {
			return Component.translatable("kultivator.kota42.pajak.tak_berhak");
		}
		CompoundTag t = KotaEkonomiData.get(player.level().getServer()).situs(KotaEkonomiData.PAJAK, situs);
		t.putLong("fb", Math.max(0L, Math.min(FESTIVAL_MAKS, nb)));
		KotaEkonomiData.get(player.level().getServer()).ubah();
		return null;
	}

	/** Income ({@code keluar} false) or spending of the last weeks, newest first (8). */
	public static long[] riwayat(MinecraftServer server, long situs, boolean keluar) {
		return KotaEkonomiData.get(server).situs(KotaEkonomiData.PAJAK, situs).getLongArray(keluar ? "k" : "m").orElse(new long[0]);
	}

	/** Mood change of the place layer from the tax (-1 per point above 10 %). */
	public static int pengaruhSuasana(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !aktif()) {
			return 0;
		}
		int rate = tarif(server, situs);
		return rate > TARIF_MURUNG ? -(rate - TARIF_MURUNG) : 0;
	}

	/** Prosperity bonus per week from a low rate (+1 below 6 %), for 42F's {@code Kemakmuran}. */
	public static int bonusMakmur(long situs) {
		MinecraftServer server = KotaHidup.server();
		return server != null && aktif() && tarif(server, situs) < TARIF_MAKMUR ? 1 : 0;
	}

	/** The effect line of a rate ("tarif > 10 %: suasana -1 per poin"). */
	public static Component efek(int tarif) {
		if (tarif > TARIF_MURUNG) {
			return Component.translatable("kultivator.kota42.pajak.efek_murung", tarif - TARIF_MURUNG);
		}
		if (tarif < TARIF_MAKMUR) {
			return Component.translatable("kultivator.kota42.pajak.efek_makmur");
		}
		return Component.translatable("kultivator.kota42.pajak.efek_wajar");
	}

	// ================================================================== uji kota_hidup ekonomi (42C-2 subs)

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("ekonomi")
				.then(Commands.literal("pajak").executes(ctx -> lihatPajak(ctx.getSource()))
						.then(Commands.argument("tarif", IntegerArgumentType.integer(TARIF_MIN, TARIF_MAKS))
								.executes(ctx -> ujiTarif(ctx, IntegerArgumentType.getInteger(ctx, "tarif")))))
				.then(Commands.literal("kas").executes(ctx -> lihatPajak(ctx.getSource())))
				.then(Commands.literal("minggu").executes(ctx -> {
					MinecraftServer server = ctx.getSource().getServer();
					long minggu = Math.floorDiv(KotaHidup.hari(server), 7L);
					mingguan(server, minggu);
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.uji.minggu_jalan"), true);
					return 1;
				}))
				.then(UsahaWarga.uji())
				.then(Investasi.uji())
				.then(GudangKota.uji())
				.then(KontrakDagang.uji())
				.then(Jabatan.uji())
				.then(Perkara.uji())
				.then(PasarHantu.uji());
	}

	private static int ujiTarif(CommandContext<CommandSourceStack> ctx, int tarif) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo tempat = tempat(player);
		if (tempat == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		setTarif(ctx.getSource().getServer(), tempat.situsId(), tarif, "uji");
		return lihatPajak(ctx.getSource());
	}

	/** Prints the tax and the town ledger of the place the source stands in. */
	static int lihatPajak(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TempatInfo tempat = tempat(player);
		if (tempat == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		MinecraftServer server = source.getServer();
		long situs = tempat.situsId();
		int rate = tarif(server, situs);
		long minggu = Math.floorDiv(KotaHidup.hari(server), 7L);
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.pajak.judul", nama(server, situs)).withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.pajak.baris", rate, KasKota.saldo(server, situs), efek(rate),
				anggaranFestival(server, situs)), false);
		for (boolean keluar : new boolean[] {false, true}) {
			Map<String, Long> buku = KasKota.buku(server, situs, minggu, keluar);
			for (Map.Entry<String, Long> e : buku.entrySet()) {
				source.sendSuccess(() -> Component.translatable(keluar ? "kultivator.kota42.pajak.keluar" : "kultivator.kota42.pajak.masuk",
						Component.translatable("kultivator.kota42.pajak.sumber." + e.getKey()), e.getValue()), false);
			}
		}
		return 1;
	}

	private PajakKota() {
	}
}
