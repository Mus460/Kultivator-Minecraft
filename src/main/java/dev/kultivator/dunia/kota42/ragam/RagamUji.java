package dev.kultivator.dunia.kota42.ragam;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.HidupConfig;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The op test subs of 42S (DESIGN 42.4A.8) under {@code /kultivasi uji kota_hidup}:
 * <ul>
 *   <li>{@code sebaran peta}: toggles the heat-map layer for the tester (payload {@code kota_peta_panas}, 16 x 16 cells of
 *       the place, occupancy / target per cell, 1 Hz, at most 1936 cells = 44 x 44 around the tester);</li>
 *   <li>{@code sebaran zona}: the zone table (K, hour multiplier, target, cap, floor, data occupancy, bodies);</li>
 *   <li>{@code sebaran titik}: 15 s of coloured dust over every reserved titik (colour by holder);</li>
 *   <li>{@code sebaran simulasi <hari>}: 1-7 days without bodies from tomorrow 06:00, one step per 12 world minutes, the
 *       metrics per hour, per day and the pass/fail table (full numbers also in the server log);</li>
 *   <li>{@code ragam <warga>}: a resident's plan today (42S-R), its activity now, three days of history, its last
 *       micro-break;</li>
 *   <li>{@code ragam laporan [hari]}: every metric of 42.4A.8 for today or {@code hari} days ago, pass/fail.</li>
 * </ul>
 */
public final class RagamUji {

	/** Heat-map subscribers: player -> place. */
	private static final Map<UUID, Long> PETA = new HashMap<>();
	/** Titik dust: player -> {place, until game time}. */
	private static final Map<UUID, long[]> TITIK = new HashMap<>();
	private static final int SEL_SISI_MAKS = 44;
	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		KotaUji.daftar("sebaran", RagamUji::sebaran);
		KotaUji.daftar("ragam", RagamUji::ragam);
	}

	static void lupakan() {
		PETA.clear();
		TITIK.clear();
	}

	private static LiteralArgumentBuilder<CommandSourceStack> sebaran() {
		return Commands.literal("sebaran")
				.then(Commands.literal("peta").executes(RagamUji::peta))
				.then(Commands.literal("zona").executes(RagamUji::zona))
				.then(Commands.literal("titik").executes(RagamUji::titik))
				.then(Commands.literal("simulasi").executes(ctx -> simulasi(ctx, 7))
						.then(Commands.argument("hari", IntegerArgumentType.integer(1, 7))
								.executes(ctx -> simulasi(ctx, IntegerArgumentType.getInteger(ctx, "hari")))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> ragam() {
		return Commands.literal("ragam")
				.then(Commands.literal("laporan").executes(ctx -> laporan(ctx, 0))
						.then(Commands.argument("hari", IntegerArgumentType.integer(0, RagamData.METRIK_HARI - 1))
								.executes(ctx -> laporan(ctx, IntegerArgumentType.getInteger(ctx, "hari")))))
				.then(Commands.argument("warga", IntegerArgumentType.integer(0)).executes(RagamUji::warga));
	}

	// ================================================================== place of the tester

	private record Tempat(ServerPlayer player, ServerLevel level, TempatHuni huni, PetaZona peta, Penyeimbang.Hunian h) {
	}

	private static @Nullable Tempat tempat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo info = KotaHidupCommands.tempatPemain(player);
		if (info == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return null;
		}
		TempatHuni huni = Pemanggil.huni(info.situsId());
		ServerLevel level = player.level();
		if (huni == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.ragam.tidak_aktif", info.jenis()));
			return null;
		}
		PetaZona peta = PetaZona.untuk(level, huni);
		if (peta.jumlah() == 0) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.ragam.tanpa_zona", info.jenis()));
			return null;
		}
		Penyeimbang.Hunian h = Penyeimbang.hunian(huni.situsId);
		long now = level.getGameTime();
		Penyeimbang.siapkan(h, huni, peta, KotaRagam.jam(huni, level.getServer().overworld().getOverworldClockTime()), now);
		return new Tempat(player, level, huni, peta, h);
	}

	// ================================================================== sebaran peta / titik (1 Hz)

	private static int peta(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		UUID id = t.player().getUUID();
		if (PETA.remove(id) != null) {
			ServerPlayNetworking.send(t.player(), new KotaPayloads.PetaPanas(t.huni().situsId, 0, 0, 0, 0, new byte[0], new byte[0]));
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.peta_mati"), false);
			return 1;
		}
		PETA.put(id, t.huni().situsId);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.peta_nyala", t.peta().jumlah()), false);
		return 1;
	}

	private static int titik(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		long now = t.level().getGameTime();
		TITIK.put(t.player().getUUID(), new long[]{t.huni().situsId, now + 300L});
		int n = t.h().pesananTitik(now).size();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.titik", n, t.h().jumlahPilihan(now)), false);
		return Math.max(1, n);
	}

	/** Every tick from {@code KotaRagam}: heat maps and titik dust once a second. */
	static void tick(MinecraftServer server, long now) {
		if (now % 20L != 0L || PETA.isEmpty() && TITIK.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Long> e : List.copyOf(PETA.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			TempatHuni huni = Pemanggil.huni(e.getValue());
			PetaZona peta = PetaZona.ada(e.getValue());
			Penyeimbang.Hunian h = Penyeimbang.ada(e.getValue());
			if (player == null || huni == null) {
				PETA.remove(e.getKey());
				continue;
			}
			if (peta != null && h != null && ServerPlayNetworking.canSend(player, KotaPayloads.PetaPanas.TYPE)) {
				ServerPlayNetworking.send(player, petaPanas(huni, peta, h, player.blockPosition(), now));
			}
		}
		for (Map.Entry<UUID, long[]> e : List.copyOf(TITIK.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Penyeimbang.Hunian h = Penyeimbang.ada(e.getValue()[0]);
			if (player == null || h == null || now > e.getValue()[1]) {
				TITIK.remove(e.getKey());
				continue;
			}
			ServerLevel level = player.level();
			int shown = 0;
			for (Map.Entry<Long, Integer> r : h.pesananTitik(now).entrySet()) {
				BlockPos at = BlockPos.of(r.getKey());
				if (at.distSqr(player.blockPosition()) > 96.0D * 96.0D || shown++ > 400) {
					continue;
				}
				int colour = warnaPemesan(r.getValue());
				level.sendParticles(player, new DustParticleOptions(colour, 1.2F), true, false, at.getX() + 0.5D, at.getY() + 1.4D, at.getZ() + 0.5D, 3,
						0.15D, 0.25D, 0.15D, 0.0D);
			}
		}
	}

	/** A bright colour per holder (golden-angle hue). */
	static int warnaPemesan(int id) {
		float hue = (id * 0.61803398875F) % 1.0F;
		return net.minecraft.util.Mth.hsvToRgb(hue, 0.85F, 1.0F) & 0xFFFFFF;
	}

	/** The heat map around {@code at}: per 16 x 16 cell the zone's occupancy and target, shared over the zone's cells (x4). */
	static KotaPayloads.PetaPanas petaPanas(TempatHuni huni, PetaZona peta, Penyeimbang.Hunian h, BlockPos at, long now) {
		int minCx = Math.floorDiv(huni.kotak.minX(), 16);
		int minCz = Math.floorDiv(huni.kotak.minZ(), 16);
		int maxCx = Math.floorDiv(huni.kotak.maxX(), 16);
		int maxCz = Math.floorDiv(huni.kotak.maxZ(), 16);
		if (maxCx - minCx + 1 > SEL_SISI_MAKS) {
			int c = Math.floorDiv(at.getX(), 16);
			minCx = Math.max(minCx, Math.min(c - SEL_SISI_MAKS / 2, maxCx - SEL_SISI_MAKS + 1));
			maxCx = minCx + SEL_SISI_MAKS - 1;
		}
		if (maxCz - minCz + 1 > SEL_SISI_MAKS) {
			int c = Math.floorDiv(at.getZ(), 16);
			minCz = Math.max(minCz, Math.min(c - SEL_SISI_MAKS / 2, maxCz - SEL_SISI_MAKS + 1));
			maxCz = minCz + SEL_SISI_MAKS - 1;
		}
		int w = maxCx - minCx + 1;
		int d = maxCz - minCz + 1;
		int[] zone = new int[w * d];
		int[] cells = new int[peta.jumlah()];
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				int zi = peta.zonaSel(minCx + x, minCz + z);
				zone[x + z * w] = zi;
				if (zi >= 0) {
					cells[zi]++;
				}
			}
		}
		byte[] occ = new byte[w * d];
		byte[] target = new byte[w * d];
		Kapasitas.Sasaran s = h.sasaran();
		for (int i = 0; i < zone.length; i++) {
			int zi = zone[i];
			if (zi < 0 || zi >= s.jumlah() || cells[zi] == 0) {
				continue;
			}
			occ[i] = (byte) Math.min(255, Math.round(4.0D * h.occ(zi) / cells[zi]));
			target[i] = (byte) Math.min(255, Math.max(s.target()[zi] > 0.0D ? 1 : 0, Math.round(4.0D * s.target()[zi] / cells[zi])));
		}
		return new KotaPayloads.PetaPanas(huni.situsId, minCx * 16, minCz * 16, w, d, occ, target);
	}

	// ================================================================== sebaran zona

	private static int zona(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		long now = t.level().getGameTime();
		Penyeimbang.hitung(t.h(), t.peta(), t.h().jam, now);
		Kapasitas.Sasaran s = t.h().sasaran();
		int[] bodies = new int[t.peta().jumlah()];
		for (var npc : KotaRagam.tubuh(t.huni())) {
			int z = t.peta().zonaDi(npc.blockPosition());
			if (z >= 0) {
				bodies[z]++;
			}
		}
		CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.zona.judul", t.huni().jenis, t.peta().jumlah(), t.peta().jumlahUnit(),
				t.h().jam, s.bebas()).withStyle(ChatFormatting.GOLD), false);
		for (int z = 0; z < t.peta().jumlah() && z < s.jumlah(); z++) {
			PetaZona.Zona zona = t.peta().zona(z);
			int occ = t.h().occ(z);
			ChatFormatting colour = occ > s.topi()[z] ? ChatFormatting.RED : zona.kapasitas() >= 4 && s.target()[z] >= 1.0D && occ == 0 ? ChatFormatting.AQUA
					: occ < s.lantai()[z] ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
			String line = String.format(Locale.ROOT, "%-22s K=%-3d m=%.2f T=%.1f C=%d L=%d", zona.id(), zona.kapasitas(), s.pengali()[z], s.target()[z],
					s.topi()[z], s.lantai()[z]);
			int b = bodies[z];
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.zona.baris", line, occ, b).withStyle(colour), false);
		}
		MetrikRagam.Zona m = MetrikRagam.zona(t.peta(), t.h());
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.zona.ringkas", angka(m.gini()), angka(m.kosong())), false);
		return t.peta().jumlah();
	}

	// ================================================================== ragam laporan

	private static int laporan(CommandContext<CommandSourceStack> ctx, int mundur) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo info = KotaHidupCommands.tempatPemain(player);
		if (info == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		long hari = KotaRagam.hari(ctx.getSource().getServer()) - mundur;
		MetrikRagam.Laporan l = MetrikRagam.laporan(info.situsId(), hari);
		cetak(ctx.getSource(), Component.translatable("kultivator.kota42.ragam.laporan.judul", info.jenis(), hari, l.sampel(), l.tubuh()), l);
		return l.lulus() ? 1 : 0;
	}

	/** Prints a report: title, one row per metric with pass/fail, verdict. */
	static void cetak(CommandSourceStack src, Component judul, MetrikRagam.Laporan l) {
		src.sendSuccess(() -> judul.copy().withStyle(ChatFormatting.GOLD), false);
		for (MetrikRagam.Baris b : l.baris()) {
			ChatFormatting colour = !b.diukur() ? ChatFormatting.GRAY : b.lulus() ? ChatFormatting.GREEN : ChatFormatting.RED;
			Component state = Component.translatable(!b.diukur() ? "kultivator.kota42.ragam.metrik.tak_diukur"
					: b.lulus() ? "kultivator.kota42.ragam.metrik.lulus" : "kultivator.kota42.ragam.metrik.gagal");
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.metrik.baris", Component.translatable("kultivator.kota42.ragam.metrik." + b.id()),
					b.diukur() ? angka(b.nilai()) : "-", b.sasaran(), state).withStyle(colour), false);
		}
		boolean ok = l.lulus();
		src.sendSuccess(() -> Component.translatable(ok ? "kultivator.kota42.ragam.laporan.lulus" : "kultivator.kota42.ragam.laporan.gagal")
				.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD), false);
	}

	static String angka(double v) {
		return v < 0.0D ? "-" : String.format(Locale.ROOT, "%.2f", v);
	}

	// ================================================================== ragam <warga>

	private static int warga(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		int id = IntegerArgumentType.getInteger(ctx, "warga");
		Penduduk p = t.huni().orang(id);
		if (p == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.ragam.warga.tidak_ada", id, t.huni().penduduk().size()));
			return 0;
		}
		CommandSourceStack src = ctx.getSource();
		MinecraftServer server = src.getServer();
		long waktu = server.overworld().getOverworldClockTime();
		long now = t.level().getGameTime();
		long hari = Math.floorDiv(waktu, 24000L);
		Warga w = WargaApi.warga(t.huni().situsId, id);
		Component nama = WargaApi.namaPapan(t.huni().situsId, p);
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.warga.judul", nama, id, p.peran().id(), p.peran().stationary ? "staf" : "bebas")
				.withStyle(ChatFormatting.GOLD), false);
		Kegiatan k = t.huni().kegiatan(p, waktu, now);
		int zona = t.h().zonaWarga(id, now);
		String zonaId = zona >= 0 ? t.peta().zona(zona).id() : "-";
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.warga.kini", Component.translatable(k.kunciLang()), k.tujuan(), zonaId), false);
		RencanaHarian.Rencana plan = w == null ? null : RencanaHarian.hari(w, hari);
		if (plan == null || plan.langkah().isEmpty()) {
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.warga.tanpa_rencana").withStyle(ChatFormatting.GRAY), false);
		} else {
			for (RencanaHarian.Langkah step : plan.langkah()) {
				String time = RencanaHarian.jam(step.mulai()) + "-" + RencanaHarian.jam(step.akhir()); // minutes after 06:00
				src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.warga.langkah", time, step.kegiatan(), step.titik()), false);
			}
		}
		int[][] r = RagamData.riwayat(server, t.huni().situsId, id, hari);
		for (int d = 0; d < 3; d++) {
			int[] day = r[d];
			List<String> names = new ArrayList<>();
			for (int i = 1; i < day.length && names.size() < 10; i++) {
				names.add(MetrikRagam.nama(day[i]));
			}
			int back = d;
			int samples = day.length > 0 ? day[0] : 0;
			int beda = MetrikRagam.beda(day);
			int ulang = MetrikRagam.ulang(day);
			String list = String.join(", ", names);
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.warga.riwayat", back, samples, beda, ulang, list), false);
		}
		Object[] last = MetrikRagam.selinganAkhir(t.huni().situsId, id);
		if (last != null) {
			long ago = (now - (Long) last[1]) / 20L;
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.warga.selingan", String.valueOf(last[0]), ago), false);
		}
		return 1;
	}

	// ================================================================== sebaran simulasi

	/** One simulated hour bucket: sums over the days. */
	private static final class Jam {
		int n;
		double bebas;
		double gini;
		int giniN;
		double kosong;
		int kosongN;
		double bagian;
		int bagianN;
	}

	private static int simulasi(CommandContext<CommandSourceStack> ctx, int hariN) throws CommandSyntaxException {
		Tempat t = tempat(ctx);
		if (t == null) {
			return 0;
		}
		CommandSourceStack src = ctx.getSource();
		long started = System.nanoTime();
		TempatHuni huni = t.huni();
		PetaZona peta = t.peta();
		Penyeimbang.Hunian h = Penyeimbang.simulasi(huni.situsId);
		long now0 = t.level().getGameTime();
		long waktu0 = t.level().getServer().overworld().getOverworldClockTime();
		long base = (Math.floorDiv(waktu0, 24000L) + 1L) * 24000L;
		int langkah = (int) (24000L / KotaRagam.LINTAS);
		List<Penduduk> residents = huni.penduduk();
		Map<Integer, int[]> today = new HashMap<>();
		Map<Integer, int[]> yesterday = new HashMap<>();
		Map<Integer, String> lastKey = new HashMap<>();
		Jam[] perJam = new Jam[24];
		for (int i = 0; i < 24; i++) {
			perJam[i] = new Jam();
		}
		MetrikRagam.Jumlah total = new MetrikRagam.Jumlah();
		double tR = 0.0D;
		double tB = 0.0D;
		int tN = 0;
		int t4 = 0;
		long pairs = 0L;
		long bad = 0L;
		int belokan = 0;
		BlockPos centre = huni.pusat();
		int anggaran = HidupConfig.get().tubuhUntuk(huni.jenis);
		double min = dev.kultivator.dunia.kota42.KotaConfig.warga().jarakMin;
		for (int d = 0; d < hariN; d++) {
			MetrikRagam.Jumlah dayTotal = new MetrikRagam.Jumlah();
			int dayBelokan = 0;
			for (int s = 0; s < langkah; s++) {
				long waktu = base + d * 24000L + s * KotaRagam.LINTAS;
				long now = now0 + (waktu - waktu0);
				int jam = KotaRagam.jam(huni, waktu);
				h.hari = Math.floorDiv(waktu, 24000L);
				Penyeimbang.siapkan(h, huni, peta, jam, now);
				List<int[]> calon = new ArrayList<>();
				for (Penduduk p : residents) {
					Kegiatan k = huni.kegiatan(p, waktu, now);
					boolean umum = Kapasitas.umum(k.tujuan());
					int zona;
					BlockPos at = null;
					boolean titik = false;
					if (umum) {
						TempatHuni.Tujuan goal = Penyeimbang.pilih(h, huni, null, peta, p, k, jam, false, now, Penyeimbang.sampai(p, huni.jenis, waktu, now));
						if (goal == null) {
							Penyeimbang.catatData(h, p.id(), false, peta.zonaDi(huni.rumahPos(p)));
							zona = h.zonaWarga(p.id(), now);
						} else {
							zona = peta.zonaDi(goal.pos());
							at = goal.pos();
							titik = goal.nyata();
						}
					} else {
						zona = Penyeimbang.zonaTetap(huni, peta, p, k);
						Penyeimbang.catatData(h, p.id(), false, zona);
					}
					int sig = MetrikRagam.tanda(k);
					int[] day = today.computeIfAbsent(p.id(), x -> new int[]{0});
					if (day.length <= 1 || day[day.length - 1] != sig) {
						if (day.length - 1 < RagamData.SEGMEN_MAKS) {
							day = java.util.Arrays.copyOf(day, day.length + 1);
							day[day.length - 1] = sig;
						}
						if (k.kunci().startsWith("belok") && !k.kunci().equals(lastKey.get(p.id()))) {
							dayBelokan++;
						}
					}
					day[0]++;
					today.put(p.id(), day);
					lastKey.put(p.id(), k.kunci());
					if (!k.dalam() && zona >= 0) {
						BlockPos where = at != null ? at : peta.zona(zona).pusat();
						double dist = Math.sqrt(where.distSqr(centre));
						if (dist <= 128.0D) {
							calon.add(new int[]{p.id(), zona, (int) dist, p.peran().stationary ? 1 : 0, titik ? 1 : 0, where.getX(), where.getY(), where.getZ()});
						}
					}
				}
				Penyeimbang.hitung(h, peta, jam, now);
				// metrics of this step
				Jam bucket = perJam[jam];
				bucket.n++;
				bucket.bebas += h.bebas();
				MetrikRagam.Zona zm = MetrikRagam.zona(peta, h);
				if (zm.gini() >= 0) {
					bucket.gini += zm.gini();
					bucket.giniN++;
				}
				if (zm.kosong() >= 0) {
					bucket.kosong += zm.kosong();
					bucket.kosongN++;
				}
				// bodies as the picker would make them for a player standing at the place centre
				List<int[]> keepers = new ArrayList<>();
				List<int[]> rest = new ArrayList<>();
				for (int[] c : calon) {
					(c[3] == 1 ? keepers : rest).add(c);
				}
				List<int[]> order = new ArrayList<>(keepers);
				order.addAll(PemilihTubuh.sebar(peta, h, rest, anggaran, c -> c[1], c -> c[2]));
				List<int[]> made = order.subList(0, Math.min(order.size(), anggaran));
				int[] perZona = new int[peta.jumlah()];
				List<Integer> visible = new ArrayList<>();
				for (int[] c : made) {
					perZona[c[1]]++;
				}
				for (int z = 0; z < peta.jumlah(); z++) {
					if ((h.occ(z) > 0 || perZona[z] > 0) && Math.sqrt(peta.zona(z).pusat().distSqr(centre)) <= 128.0D) {
						visible.add(z);
					}
				}
				MetrikRagam.Tubuh tm = MetrikRagam.tubuhDari(visible, perZona, h.occ, -1);
				if (tm != null) {
					tN++;
					tR += tm.rasio();
					if (tm.empatZona()) {
						t4++;
						tB += tm.bagian();
						bucket.bagian += tm.bagian();
						bucket.bagianN++;
					}
				}
				for (int i = 0; i < made.size(); i++) {
					int[] a = made.get(i);
					for (int j = i + 1; j < made.size(); j++) {
						int[] b = made.get(j);
						pairs++;
						double dx = a[5] - b[5];
						double dz = a[7] - b[7];
						if (dx * dx + dz * dz < min * min && Math.abs(a[6] - b[6]) < 2 && !(a[4] == 1 && b[4] == 1)) {
							bad++;
						}
					}
				}
			}
			for (Penduduk p : residents) {
				int[] day = today.getOrDefault(p.id(), new int[]{0});
				int[] before = yesterday.get(p.id());
				total.tambah(day, before, p.peran().stationary);
				dayTotal.tambah(day, before, p.peran().stationary);
			}
			belokan += dayBelokan;
			int dayN = d;
			double belokanHari = residents.isEmpty() ? 0.0D : dayBelokan / (double) residents.size();
			MetrikRagam.Jumlah dj = dayTotal;
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.sim.hari", dayN + 1, angka(dj.bebasN > 0 ? dj.bebasS / dj.bebasN : -1),
					dj.bebasN > 0 ? dj.bebasMin : 0, angka(dj.stafN > 0 ? dj.stafS / dj.stafN : -1), angka(dj.bebasN > 0 ? dj.ulangS / dj.bebasN : -1),
					angka(dj.jacN > 0 ? dj.jacS / dj.jacN : -1), angka(belokanHari)), false);
			yesterday = today;
			today = new HashMap<>();
		}
		StringBuilder log = new StringBuilder("42S simulasi ").append(huni.jenis).append(' ').append(huni.situsId).append(" (").append(hariN)
				.append(" hari)\njam;n;bebas;gini;kosong;bagian\n");
		for (int j = 0; j < 24; j++) {
			int hour = (j + 6) % 24;
			Jam b = perJam[hour];
			if (b.n == 0) {
				continue;
			}
			String line = String.format(Locale.ROOT, "%02d;%d;%.1f;%s;%s;%s", hour, b.n, b.bebas / b.n, angka(b.giniN > 0 ? b.gini / b.giniN : -1),
					angka(b.kosongN > 0 ? b.kosong / b.kosongN : -1), angka(b.bagianN > 0 ? b.bagian / b.bagianN : -1));
			log.append(line).append('\n');
			String[] f = line.split(";");
			src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.sim.jam", f[0], f[2], f[3], f[4], f[5]), false);
		}
		double giniAll = 0.0D;
		int giniN = 0;
		double kosongAll = 0.0D;
		int kosongN = 0;
		for (Jam b : perJam) {
			giniAll += b.gini;
			giniN += b.giniN;
			kosongAll += b.kosong;
			kosongN += b.kosongN;
		}
		MetrikRagam.Laporan l = new MetrikRagam.Laporan(huni.situsId, Math.floorDiv(base, 24000L),
				total.bebasN > 0 ? total.bebasS / total.bebasN : -1,
				total.bebasN > 0 ? total.bebasMin : -1,
				total.stafN > 0 ? total.stafS / total.stafN : -1,
				total.bebasN > 0 ? total.ulangS / total.bebasN : -1,
				total.jacN > 0 ? total.jacS / total.jacN : -1,
				-1, -1,
				belokan > 0 ? belokan / (double) Math.max(1, residents.size() * hariN) : -1,
				t4 > 0 ? tB / t4 : -1,
				tN > 0 ? tR / tN : -1,
				giniN > 0 ? giniAll / giniN : -1,
				kosongN > 0 ? kosongAll / kosongN : -1,
				pairs > 0 ? bad / (double) pairs : -1,
				residents.size(), tN);
		for (MetrikRagam.Baris b : l.baris()) {
			log.append(b.id()).append('=').append(angka(b.nilai())).append(" (").append(b.sasaran()).append(") ").append(b.lulus() ? "ok" : "GAGAL").append('\n');
		}
		Kultivator.LOGGER.info(log.toString());
		long ms = (System.nanoTime() - started) / 1_000_000L;
		cetak(src, Component.translatable("kultivator.kota42.ragam.sim.judul", huni.jenis, hariN, residents.size(), peta.jumlah(), ms), l);
		src.sendSuccess(() -> Component.translatable("kultivator.kota42.ragam.sim.catatan").withStyle(ChatFormatting.GRAY), false);
		return l.lulus() ? 1 : 0;
	}

	private RagamUji() {
	}
}
