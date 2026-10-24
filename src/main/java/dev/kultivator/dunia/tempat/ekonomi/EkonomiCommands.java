package dev.kultivator.dunia.tempat.ekonomi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.PapanMisi;
import dev.kultivator.dunia.kota.Towns;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Commands of 38EK (DESIGN 38.M.6, 38.D.10): the op test node {@code /kultivasi uji ekonomi ...} and the player commands
 * {@code /kultivasi kas} (the sect ledger, read only) and {@code /kultivasi misi pilih <id> <0|1>} (the moral twist of a
 * guild mission, clicked in chat). Test lines print translated text; numbers are the live formulas, never copies.
 */
public final class EkonomiCommands {

	/** Income targets per band of 38.M.1 (NB a world day, low and high). */
	static final long[][] SASARAN = {{20, 30}, {40, 70}, {100, 160}, {250, 400}, {600, 900}, {1200, 1800}, {2000, 4000}, {2000, 4000}, {2000, 4000}};

	static void init() {
		DuniaCommands.test("ekonomi", EkonomiCommands::node);
		DuniaCommands.player("kas", () -> Commands.literal("kas").executes(ctx -> {
			BukuKas.bukaHub(ctx.getSource().getPlayerOrException());
			return 1;
		}));
		DuniaCommands.player("misi", () -> Commands.literal("misi")
				.then(Commands.literal("pilih")
						.then(Commands.argument("id", LongArgumentType.longArg())
								.then(Commands.argument("pilihan", IntegerArgumentType.integer(0, 1))
										.executes(ctx -> {
											MisiGilda.pilih(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "id"),
													IntegerArgumentType.getInteger(ctx, "pilihan"));
											return 1;
										})))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("ekonomi")
				.executes(ctx -> bantuan(ctx.getSource()))
				.then(Commands.literal("simulasi")
						.then(Commands.literal("semua")
								.then(Commands.argument("hari", IntegerArgumentType.integer(1, 60))
										.executes(ctx -> simulasiSemua(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "hari")))))
						.then(Commands.argument("pita", IntegerArgumentType.integer(0, 8))
								.then(Commands.argument("hari", IntegerArgumentType.integer(1, 60))
										.executes(ctx -> simulasi(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "pita"),
												IntegerArgumentType.getInteger(ctx, "hari"), IntegerArgumentType.getInteger(ctx, "hari") <= 10)))))
				.then(Commands.literal("judi")
						.then(Commands.argument("n", IntegerArgumentType.integer(100, 1_000_000))
								.executes(ctx -> judi(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("lapak")
						.executes(ctx -> lapak(ctx, 0))
						.then(Commands.argument("hari", IntegerArgumentType.integer(1, 30))
								.executes(ctx -> lapak(ctx, IntegerArgumentType.getInteger(ctx, "hari")))))
				.then(Commands.literal("kafilah")
						.executes(EkonomiCommands::kafilahTiba)
						.then(Commands.literal("permintaan").executes(EkonomiCommands::kafilahPermintaan)))
				.then(Commands.literal("kas").executes(EkonomiCommands::kas))
				.then(Commands.literal("kurs").executes(ctx -> kurs(ctx.getSource())))
				.then(Commands.literal("stok").then(Commands.literal("ulang").executes(ctx -> stokUlang(ctx.getSource()))))
				.then(Commands.literal("senjata")
						.then(Commands.argument("bentuk", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(SenjataKota.Bentuk.values()).map(b -> b.id), builder))
								.then(Commands.argument("mutu", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(SenjataKota.Mutu.values()).map(m -> m.id), builder))
										.executes(EkonomiCommands::senjata))))
				.then(Commands.literal("pesanan").then(Commands.literal("selesai").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					int n = PesananTempa.selesaikan(player);
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.pesanan", n), false);
					return n;
				})))
				.then(Commands.literal("lencana")
						.then(Commands.argument("bintang", IntegerArgumentType.integer(0, 9)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							int star = IntegerArgumentType.getInteger(ctx, "bintang");
							Lencana.set(player, star);
							ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.lencana", star, Lencana.poin(player)), false);
							return 1;
						})))
				.then(Commands.literal("misi")
						.then(Commands.argument("pangkat", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("e", "d", "c", "b", "a", "s"), builder))
								.executes(EkonomiCommands::misi)))
				.then(Commands.literal("bank").executes(EkonomiCommands::bank))
				.then(Commands.literal("lelang")
						.executes(ctx -> lelang(ctx, true))
						.then(Commands.literal("agung").executes(ctx -> lelang(ctx, true)))
						.then(Commands.literal("kota").executes(ctx -> lelang(ctx, false))));
	}

	private static int bantuan(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.bantuan").withStyle(ChatFormatting.GOLD), false);
		for (String line : List.of("simulasi <pita> <hari>", "simulasi semua <hari>", "judi <n>", "lapak [hari]", "kafilah [permintaan]", "kas", "kurs", "stok ulang",
				"senjata <bentuk> <mutu>", "pesanan selesai", "lencana <0-9>", "misi <e..s>", "bank", "lelang [agung|kota]")) {
			source.sendSuccess(() -> Component.literal("  /kultivasi uji ekonomi " + line).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	/** Town bounties of the simulated active player a day (of {@code kota.bountiesPerDay}; each one costs travel time). */
	static final int SIM_BOUNTY = 2;
	/** Tenths of durability a simulated day of fighting costs the band's weapon (repaired at the smith every evening). */
	static final int SIM_AUS_PER_10 = 1;
	/** Upgrade days allowed by 38.M.1 ("one meaningful upgrade = 1-3 days of income"), widened by the ±25 % of 38.M.6. */
	static final double UPGRADE_MIN = 0.5D;
	static final double UPGRADE_MAX = 3.75D;

	/**
	 * {@code simulasi <pita> <hari>} (38.M.6): an active player of that band over n days, with the live formulas only.
	 * Every day he reads three real boards (the capital guild E-S, a town clerk E-C, a road post's copy E-D, rolled by
	 * {@link MisiGilda#pilihJenis}/{@link MisiGilda#pangkatTawaran} from the registered kinds, 38TK's included), takes the
	 * best offers his realm may take up to the {@link Lencana} cap of his simulated stars (S from 7 stars; exams assumed
	 * passed), and turns in {@link #SIM_BOUNTY} town bounties ({@link PapanMisi#reward}). Costs: inn, two meals of the band,
	 * one teleport, the evening repair of the band's weapon. Prints the sources, the mean against the 38.M.1 target ±25 %
	 * and how many days of net income the band's main purchase costs.
	 */
	private static int simulasi(CommandSourceStack source, int pitaArg, int days, boolean harian) {
		int band = Math.max(0, Math.min(8, pitaArg));
		RandomSource random = RandomSource.create(0x38E1L + band * 31L + days);
		int[] kelas = {MisiGilda.KELAS_GILDA, MisiGilda.KELAS_KOTA, MisiGilda.KELAS_POS};
		List<List<JenisMisi>> kinds = new ArrayList<>();
		int[] totals = new int[kelas.length];
		for (int i = 0; i < kelas.length; i++) {
			kinds.add(MisiGilda.jenisPapan(kelas[i]));
			totals[i] = MisiGilda.bobot(kinds.get(i));
		}
		int points = 0;
		long guild = 0L;
		long bounty = 0L;
		long wares = 0L;
		long spend = 0L;
		int missions = 0;
		int[] perRank = new int[6];
		long upgrade = Barang.harga(utamaPita(band), 1.0D);
		long repair = Math.round(Math.max(senjataPita(band), 10L) * TokoSenjata.PERBAIKI_PER_10 * SIM_AUS_PER_10 * Barang.skala());
		int bounties = Math.min(SIM_BOUNTY, Kota.config().bountiesPerDay);
		int allExams = Lencana.UJIAN[Lencana.UJIAN.length - 1];
		for (int d = 0; d < days; d++) {
			int stars = Lencana.bintangDari(points, allExams);
			List<Integer> offers = new ArrayList<>();
			for (int i = 0; i < kelas.length; i++) {
				if (totals[i] <= 0) {
					continue;
				}
				int maks = MisiGilda.pangkatMaks(kelas[i]);
				for (int slot = 0; slot < MisiGilda.ukuranPapan(kelas[i]); slot++) {
					int rank = MisiGilda.pangkatTawaran(MisiGilda.pilihJenis(kinds.get(i), totals[i], random), maks, random);
					if (MisiGilda.PITA_MIN[rank] <= band && (rank < JenisMisi.S || stars >= Lencana.BINTANG_S)) {
						offers.add(rank);
					}
				}
			}
			offers.sort((x, y) -> Integer.compare(y, x));
			long day = 0L;
			int cap = Math.min(offers.size(), Lencana.misiPerHariBintang(stars));
			for (int m = 0; m < cap; m++) {
				int rank = offers.get(m);
				long stones = MisiGilda.imbalan(band, rank, random);
				day += stones;
				guild += stones;
				points += MisiGilda.POIN[rank];
				perRank[rank]++;
				missions++;
				if (random.nextDouble() < MisiGilda.PELUANG_BARANG) {
					wares += Math.round(nilaiBarangMisi(rank, random) * 0.5D);
				}
			}
			for (int t = 0; t < bounties; t++) {
				long stones = PapanMisi.reward(Math.max(1, band), random);
				day += stones;
				bounty += stones;
				points += MisiGilda.POIN[PapanMisi.pangkat(band)];
			}
			Hidangan.Jenis meal = band <= 1 ? Hidangan.Jenis.BAKPAO : band <= 3 ? Hidangan.Jenis.SUP : band <= 5 ? Hidangan.Jenis.MI : Hidangan.Jenis.ARAK;
			long cost = Barang.harga(Penginapan.KAMAR, 1.0D) + 2L * Barang.harga(meal.harga, 1.0D)
					+ Barang.harga(Math.min(120L, (long) Math.ceil(2.0D + 800.0D / 250.0D)), 1.0D) + repair;
			spend += cost;
			if (harian) {
				long dayFinal = day;
				long costFinal = cost;
				int dayNo = d + 1;
				source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.simulasi.hari", dayNo, dayFinal, costFinal), false);
			}
		}
		long mean = (guild + bounty) / days;
		long meanSpend = spend / days;
		long[] target = SASARAN[band];
		boolean ok = mean >= Math.round(target[0] * 0.75D) && mean <= Math.round(target[1] * 1.25D);
		StringBuilder ranks = new StringBuilder();
		for (int r = 0; r < perRank.length; r++) {
			if (perRank[r] > 0) {
				ranks.append(ranks.isEmpty() ? "" : " ").append(JenisMisi.HURUF.charAt(r)).append('x').append(perRank[r]);
			}
		}
		String perDay = String.format(Locale.ROOT, "%.1f", missions / (double) days);
		int finalStars = Lencana.bintangDari(points, allExams);
		long guildMean = guild / days;
		long bountyMean = bounty / days;
		long waresMean = wares / days;
		String rankText = ranks.toString();
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.simulasi.sumber", guildMean, perDay, rankText, bountyMean,
				bounties, waresMean, finalStars).withStyle(ChatFormatting.GRAY), false);
		long net = Math.max(1L, mean - meanSpend);
		double upgradeDays = upgrade / (double) net;
		boolean upgradeOk = upgradeDays >= UPGRADE_MIN && upgradeDays <= UPGRADE_MAX;
		String upgradeText = String.format(Locale.ROOT, "%.1f", upgradeDays);
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.simulasi.belanja",
				Component.translatable("kultivator.tempat_ekonomi.uji.simulasi.utama." + Math.min(6, band)), upgrade, upgradeText,
				Component.translatable(upgradeOk ? "kultivator.tempat_ekonomi.uji.ok" : "kultivator.tempat_ekonomi.uji.luar"))
				.withStyle(upgradeOk ? ChatFormatting.GRAY : ChatFormatting.YELLOW), false);
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.simulasi.hasil", band, mean, meanSpend, target[0], target[1],
				Component.translatable(ok ? "kultivator.tempat_ekonomi.uji.ok" : "kultivator.tempat_ekonomi.uji.luar"))
				.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		return ok ? 1 : 0;
	}

	/** {@code simulasi semua <hari>}: every band 0-8 in turn (sources, purchase and verdict lines; no day lines). */
	private static int simulasiSemua(CommandSourceStack source, int days) {
		int ok = 0;
		for (int band = 0; band <= 8; band++) {
			ok += simulasi(source, band, days, false);
		}
		int passed = ok;
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.simulasi.semua", passed, 9)
				.withStyle(passed == 9 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		return passed;
	}

	/** Base NB of the band's main purchase (38.M.1 table): weapon or armour set up to Nascent Soul, then forged artefacts. */
	static long utamaPita(int band) {
		return switch (band) {
			case 0 -> SenjataKota.Bentuk.JIAN.harga(SenjataKota.Mutu.FANA);
			case 1 -> 60L;
			case 2 -> SenjataKota.Bentuk.JIAN.harga(SenjataKota.Mutu.ROH_RENDAH);
			case 3 -> SenjataKota.Bentuk.JIAN.harga(SenjataKota.Mutu.ROH_MENENGAH);
			case 4 -> SenjataKota.Bentuk.JIAN.harga(SenjataKota.Mutu.ROH_TINGGI);
			case 5 -> Math.round(Harga.ARTIFACT[4] * TokoSenjata.HARGA_PUSAKA);
			default -> Math.round(Harga.ARTIFACT[5] * TokoSenjata.HARGA_PUSAKA);
		};
	}

	/** Base NB of the weapon the band fights with (repaired every evening in the simulation). */
	static long senjataPita(int band) {
		SenjataKota.Mutu mutu = band >= 4 ? SenjataKota.Mutu.ROH_TINGGI : band == 3 ? SenjataKota.Mutu.ROH_MENENGAH
				: band == 2 ? SenjataKota.Mutu.ROH_RENDAH : SenjataKota.Mutu.FANA;
		return SenjataKota.Bentuk.JIAN.harga(mutu);
	}

	/** Harga value of the ware a mission may add (the same three-way roll as {@code MisiGilda.bayar}). */
	private static long nilaiBarangMisi(int rank, RandomSource random) {
		return switch (random.nextInt(3)) {
			case 0 -> Harga.base("jade_slip");
			case 1 -> Harga.base("catatan_pengembara");
			default -> Harga.base(rank >= JenisMisi.B ? "heavenvoid_fragment" : "jade_slip");
		};
	}

	private static int judi(CommandSourceStack source, int n) {
		double[] ev = JudiBatu.simulasi(source.getServer(), n);
		double best = Math.max(ev[0], Math.max(ev[1], Math.max(ev[2], ev[3])));
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.judi", n, String.format(Locale.ROOT, "%.3f", ev[0]),
				String.format(Locale.ROOT, "%.3f", ev[1]), String.format(Locale.ROOT, "%.2f", EkonomiConfig.get().judiEv))
				.withStyle(best <= 1.02D ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.judi_warna", String.format(Locale.ROOT, "%.3f", ev[2]),
				String.format(Locale.ROOT, "%.3f", ev[3]), String.format(Locale.ROOT, "%.3f", best),
				Component.translatable(best <= 1.02D ? "kultivator.tempat_ekonomi.uji.ok" : "kultivator.tempat_ekonomi.uji.luar"))
				.withStyle(ChatFormatting.GRAY), false);
		return best <= 1.02D ? 1 : 0;
	}

	private static int lapak(CommandContext<CommandSourceStack> ctx, int days) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		long hari = Barang.hari(server);
		for (int i = 1; i <= days; i++) {
			CompoundTag lapak = EkonomiData.get(server).pemain(player.getUUID()).getCompoundOrEmpty("lapak");
			lapak.remove("dijual");
			LapakSekte.hariBaru(server, hari);
		}
		CompoundTag sheet = LapakSekte.lembar(player);
		long kas = sheet.getLongOr(dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas.KAS, 0L);
		int left = sheet.getListOrEmpty(dev.kultivator.dunia.tempat.ui.LembarKunci.DAFTAR).size();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.lapak", days, left, NilaiBatu.text(kas),
				LapakSekte.volume(Barang.pita(player), dev.kultivator.sect.hall.Halls.level(player.getUUID()))), false);
		return 1;
	}

	private static int kafilahTiba(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		long now = Barang.waktu(server);
		int n = 0;
		for (Tag each : EkonomiData.get(server).pemain(player.getUUID()).getListOrEmpty(KafilahSekte.KUNCI)) {
			if (each instanceof CompoundTag caravan && !caravan.getBooleanOr("tiba", false)) {
				caravan.putLong("tiba_pada", now);
				caravan.remove("hari_dicek");
				n++;
			}
		}
		KafilahSekte.hariBaru(server, Barang.hari(server));
		int count = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.kafilah", count), false);
		return n;
	}

	private static int kafilahPermintaan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		ItemStack hand = player.getMainHandItem();
		if (hand.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.tempat_ekonomi.lelang.titip.pegang"));
			return 0;
		}
		int shown = 0;
		for (long town : Towns.known(server)) {
			BlockPos centre = Towns.centre(server, town);
			if (centre == null || shown >= 12) {
				continue;
			}
			double demand = KafilahSekte.permintaan(server, town, hand);
			String kind = KafilahSekte.jenisSitus(server, town);
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.permintaan", KotaNames.town(town), kind,
					String.format(Locale.ROOT, "%.2f", demand)), false);
			shown++;
		}
		return shown;
	}

	private static int kas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		long hari = Barang.hari(server);
		long pk = 0L;
		long nb = 0L;
		for (int d = 0; d < 7; d++) {
			long[] net = BukuKas.bersih(server, player.getUUID(), hari - d);
			pk += net[0];
			nb += net[1];
		}
		long weekPk = pk;
		long weekNb = nb;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.kas", BukuKas.baris(server, player.getUUID()).size(), weekPk,
				NilaiBatu.text(weekNb)), false);
		List<Long> sites = new ArrayList<>(EkonomiData.get(server).situsIds());
		for (long site : sites) {
			long saldo = KasKota.saldo(server, site);
			if (saldo > 0L) {
				long[] flow = KasKota.riwayat(server, site);
				ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.kas_kota", KotaNames.town(site), NilaiBatu.text(saldo),
						flow[0], flow[1]), false);
			}
		}
		BukuKas.bukaHub(player);
		return 1;
	}

	private static int kurs(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.kurs", NilaiBatu.VALUE[0], NilaiBatu.VALUE[1], NilaiBatu.VALUE[2],
				NilaiBatu.VALUE[3], 2, RumahUang.ASAL_BELI, RumahUang.ASAL_JUAL, String.format(Locale.ROOT, "%.2f", Barang.skala())), false);
		return 1;
	}

	private static int stokUlang(CommandSourceStack source) {
		EkonomiData data = EkonomiData.get(source.getServer());
		int n = 0;
		for (long site : data.situsIds()) {
			CompoundTag tag = data.situs(site);
			for (String key : List.of("senjata_terjual", "gilda_tempa_terjual", "gilda_alkemis_terjual", "binatang_hari", "judi_rak", "makan_hari",
					"penginapan_hari")) {
				if (tag.contains(key)) {
					tag.remove(key);
					n++;
				}
			}
		}
		for (UUID id : data.pemainIds()) {
			data.pemain(id).remove("harian");
			data.pemain(id).remove("binatang_anak");
		}
		data.touch();
		int count = n;
		source.sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.stok_ulang", count), false);
		return 1;
	}

	private static int senjata(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SenjataKota.Bentuk bentuk = SenjataKota.Bentuk.byId(StringArgumentType.getString(ctx, "bentuk"));
		SenjataKota.Mutu mutu = SenjataKota.Mutu.byId(StringArgumentType.getString(ctx, "mutu"));
		if (bentuk == null || mutu == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.tempat_ekonomi.uji.senjata_tidak_ada"));
			return 0;
		}
		ItemStack stack = SenjataKota.stack(bentuk, mutu);
		Barang.beri(player, stack);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.senjata", stack.getHoverName(),
				String.format(Locale.ROOT, "%.1f", TokoSenjata.serangan(stack)), String.format(Locale.ROOT, "%.1f", TokoSenjata.kecepatan(stack)),
				NilaiBatu.text(SenjataKota.harga(stack))), false);
		return 1;
	}

	private static int misi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int rank = JenisMisi.HURUF.indexOf(StringArgumentType.getString(ctx, "pangkat").toUpperCase(Locale.ROOT));
		if (rank < 0) {
			return 0;
		}
		List<JenisMisi> kinds = new ArrayList<>();
		for (JenisMisi kind : EkonomiApi.jenisMisi()) {
			if (kind.boleh(rank) && !"kafilah".equals(kind.id())) {
				kinds.add(kind);
			}
		}
		if (kinds.isEmpty()) {
			return 0;
		}
		RandomSource random = player.getRandom();
		JenisMisi kind = kinds.get(random.nextInt(kinds.size()));
		BlockPos at = player.blockPosition().offset(random.nextBoolean() ? 220 : -220, 0, random.nextBoolean() ? 160 : -160);
		long town = Towns.near((net.minecraft.server.level.ServerLevel) player.level(), player.blockPosition(), 4000);
		MisiGilda.Tawaran offer = new MisiGilda.Tawaran(Math.abs(random.nextLong() % 1_000_000_000L) + 1L, kind, rank, at.getX(), at.getZ(), random.nextInt(3));
		MisiGilda.terima(player, null, offer, town, town);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.misi", Component.translatable("kultivator.misi." + kind.id()
				+ ".judul"), JenisMisi.HURUF.charAt(rank)), false);
		return 1;
	}

	private static int bank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		long saldo = RumahUang.saldo(server, player.getUUID());
		int pawns = Gadai.daftar(player).size();
		int vault = Brankas.kapasitas(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.bank", NilaiBatu.text(saldo), pawns, vault), false);
		return 1;
	}

	private static int lelang(CommandContext<CommandSourceStack> ctx, boolean agung) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!LelangAgung.uji(player, agung)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.tempat_ekonomi.uji.lelang_tanpa_juru"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.tempat_ekonomi.uji.lelang", Component.translatable(agung
				? "kultivator.tempat_ekonomi.lelang.judul.agung" : "kultivator.tempat_ekonomi.lelang.judul.kota")), false);
		return 1;
	}

	private EkonomiCommands() {
	}
}
