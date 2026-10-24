package dev.kultivator.dunia.tempat.perbaikan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.kehancuran.WorldBreaker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /kultivasi uji tempat} repair sub-commands (DESIGN 38.R.7, 38.D.10; op through {@code DuniaCommands.test}) and
 * the alias under {@code uji kota_besar}; player command {@code /kultivasi sitaan [ambil]}.
 * <ul>
 *   <li>{@code rusak <n> [pemain|monster|ledakan]}: test damage of {@code n} blocks of the place where the caller looks,
 *       made through {@code WorldBreaker.submitRestore} (air where the blueprint and the world agree, never a block
 *       entity, a fluid or a claim), attributed as chosen; the cluster reports at once so the bill or the help notice
 *       follows within seconds;</li>
 *   <li>{@code perbaiki [cepat|batal]}: status, work everything off now, or drop the jobs;</li>
 *   <li>{@code tagihan [lihat|buat|bayar|tolak]}: the caller's bills, send the collector now, answer directly;</li>
 *   <li>{@code takut <0-100>}, {@code cetak [info|rekam|bandingkan]}, {@code sitaan}, {@code gugus}.</li>
 * </ul>
 */
public final class PerbaikanCommands {

	private static final String U = "kultivator.tempat_perbaikan.uji.";

	static void init() {
		DuniaCommands.test("tempat", () -> sub(Commands.literal("tempat"), true));
		DuniaCommands.test("kota_besar", () -> sub(Commands.literal("kota_besar"), false));
		DuniaCommands.player("sitaan", () -> Commands.literal("sitaan")
				.executes(ctx -> sitaanPemain(ctx.getSource(), false))
				.then(Commands.literal("ambil").executes(ctx -> sitaanPemain(ctx.getSource(), true))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> sub(LiteralArgumentBuilder<CommandSourceStack> root, boolean all) {
		root.then(Commands.literal("rusak")
				.then(Commands.argument("n", IntegerArgumentType.integer(1, 200))
						.executes(ctx -> rusak(ctx, IntegerArgumentType.getInteger(ctx, "n"), Gugus.PECAH))
						.then(Commands.literal("pemain").executes(ctx -> rusak(ctx, IntegerArgumentType.getInteger(ctx, "n"), Gugus.PECAH)))
						.then(Commands.literal("monster").executes(ctx -> rusak(ctx, IntegerArgumentType.getInteger(ctx, "n"), Gugus.MONSTER)))
						.then(Commands.literal("ledakan").executes(ctx -> rusak(ctx, IntegerArgumentType.getInteger(ctx, "n"), Gugus.TNT)))));
		root.then(Commands.literal("perbaiki")
				.executes(ctx -> perbaiki(ctx.getSource(), ""))
				.then(Commands.literal("cepat").executes(ctx -> perbaiki(ctx.getSource(), "cepat")))
				.then(Commands.literal("semua").executes(ctx -> perbaiki(ctx.getSource(), "semua")))
				.then(Commands.literal("batal").executes(ctx -> perbaiki(ctx.getSource(), "batal"))));
		root.then(Commands.literal("tagihan")
				.executes(ctx -> tagihan(ctx.getSource(), "lihat"))
				.then(Commands.literal("lihat").executes(ctx -> tagihan(ctx.getSource(), "lihat")))
				.then(Commands.literal("buat").executes(ctx -> tagihan(ctx.getSource(), "buat")))
				.then(Commands.literal("bayar").executes(ctx -> tagihan(ctx.getSource(), "bayar")))
				.then(Commands.literal("tolak").executes(ctx -> tagihan(ctx.getSource(), "tolak"))));
		if (all) {
			root.then(Commands.literal("takut")
					.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100))
							.executes(ctx -> takut(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "nilai")))));
			root.then(Commands.literal("cetak")
					.executes(ctx -> cetak(ctx.getSource(), "info"))
					.then(Commands.literal("info").executes(ctx -> cetak(ctx.getSource(), "info")))
					.then(Commands.literal("rekam").executes(ctx -> cetak(ctx.getSource(), "rekam")))
					.then(Commands.literal("bandingkan").executes(ctx -> cetak(ctx.getSource(), "bandingkan"))));
			root.then(Commands.literal("sitaan").executes(ctx -> sitaan(ctx.getSource())));
			root.then(Commands.literal("gugus").executes(ctx -> gugus(ctx.getSource())));
		}
		return root;
	}

	// ================================================================== helpers

	private static @Nullable TempatRawat tempat(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos pos = BlockPos.containing(source.getPosition());
		TempatRawat t = Rekam.tempatDi(level, pos);
		if (t == null) {
			t = Rekam.terdekat(level, pos, 48.0D);
		}
		if (t == null) {
			source.sendFailure(Component.translatable(U + "tanpa_tempat"));
		}
		return t;
	}

	private static Component nama(MinecraftServer server, @Nullable UUID who) {
		if (who == null) {
			return Component.translatable(U + "tanpa_pelaku");
		}
		ServerPlayer p = server.getPlayerList().getPlayer(who);
		return p == null ? Component.literal(who.toString().substring(0, 8)) : p.getName();
	}

	// ================================================================== rusak

	private static int rusak(CommandContext<CommandSourceStack> ctx, int n, String sebab) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		ServerLevel level = source.getLevel();
		BlockPos centre = BlockPos.containing(source.getPosition());
		if (player != null) {
			HitResult hit = player.pick(24.0D, 0.0F, false);
			if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
				centre = block.getBlockPos();
			}
		}
		TempatRawat t = Rekam.tempatDi(level, centre);
		if (t == null) {
			t = tempat(source);
			if (t == null) {
				return 0;
			}
		}
		List<BlockPos> picks = pilihBlok(level, t, centre, n);
		if (picks.isEmpty()) {
			source.sendFailure(Component.translatable(U + "rusak_kosong"));
			return 0;
		}
		long[] positions = new long[picks.size()];
		BlockState[] targets = new BlockState[picks.size()];
		BlockState[] expected = new BlockState[picks.size()];
		BoundingBox box = new BoundingBox(picks.get(0));
		for (int i = 0; i < picks.size(); i++) {
			BlockPos p = picks.get(i);
			positions[i] = p.asLong();
			targets[i] = Blocks.AIR.defaultBlockState();
			expected[i] = level.getBlockState(p);
			box = BoundingBox.encapsulating(box, new BoundingBox(p));
		}
		UUID who = Gugus.MONSTER.equals(sebab) || player == null ? null : player.getUUID();
		long now = level.getGameTime();
		LogAtribusi.catat(t.kunci, now, Gugus.MONSTER.equals(sebab) ? Gugus.MONSTER : sebab, who, box.inflatedBy(1));
		final TempatRawat place = t;
		final BoundingBox damaged = box;
		boolean queued = WorldBreaker.submitRestore(new WorldBreaker.RestoreRequest(level.dimension(), positions, targets, expected, null, "uji_rusak",
				result -> {
					PindaiTempat.prioritas(place, damaged.inflatedBy(1));
					Perbaikan.nanti(level.getServer(), 5, () -> PekerjaanPerbaikan.segerakan(level.getServer(), place.kunci, now));
				}));
		if (!queued) {
			source.sendFailure(Component.translatable(U + "antre_penuh"));
			return 0;
		}
		int count = picks.size();
		source.sendSuccess(() -> Component.translatable(U + "rusak", count, Component.translatable("kultivator.tempat_perbaikan.sebab." + sebab),
				Penagihan.namaTempat(place)), false);
		return count;
	}

	/** Up to {@code n} solid recorded blocks nearest to {@code centre} that the world still holds as recorded. */
	private static List<BlockPos> pilihBlok(ServerLevel level, TempatRawat t, BlockPos centre, int n) {
		List<BlockPos> found = new ArrayList<>();
		for (int r = 0; r <= 8 && found.size() < n * 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) {
							continue;
						}
						BlockPos p = centre.offset(dx, dy, dz);
						if (!t.berisi(p) || !level.isLoaded(p)) {
							continue;
						}
						Cetak c = Rekam.cetak(t, ChunkPos.pack(p));
						BlockState rec = c == null ? null : c.di(p);
						BlockState now = level.getBlockState(p);
						if (rec == null || rec != now || now.isAir() || now.hasBlockEntity() || !now.getFluidState().isEmpty()
								|| now.getDestroySpeed(level, p) < 0.0F || KlaimPemain.diklaim(level, p)) {
							continue;
						}
						found.add(p);
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		return found.size() > n ? new ArrayList<>(found.subList(0, n)) : found;
	}

	// ================================================================== perbaiki, tagihan, takut

	private static int perbaiki(CommandSourceStack source, String mode) {
		TempatRawat t = tempat(source);
		if (t == null) {
			return 0;
		}
		MinecraftServer server = source.getServer();
		switch (mode) {
			case "cepat", "semua" -> {
				int n = PekerjaanPerbaikan.cepat(server, t);
				source.sendSuccess(() -> Component.translatable(U + "perbaiki_cepat", n, WorldBreaker.restoreQueue()), false);
				return n;
			}
			case "batal" -> {
				int n = PekerjaanPerbaikan.batal(server, t);
				source.sendSuccess(() -> Component.translatable(U + "perbaiki_batal", n), false);
				return n;
			}
			default -> {
				int jobs = 0;
				int open = 0;
				for (Gugus g : PerbaikanData.get(server).gugus.values()) {
					if (g.kunci == t.kunci) {
						jobs++;
						open += g.sisa();
					}
				}
				int finalJobs = jobs;
				int finalOpen = open;
				source.sendSuccess(() -> Component.translatable(U + "perbaiki_status", Penagihan.namaTempat(t), finalJobs, finalOpen,
						PekerjaanPerbaikan.dipulihkan(), WorldBreaker.restoreQueue()), false);
				return jobs;
			}
		}
	}

	private static int tagihan(CommandSourceStack source, String mode) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		MinecraftServer server = source.getServer();
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.TagihanData bill = Penagihan.terbuka(server, player.getUUID());
		switch (mode) {
			case "buat" -> {
				TempatRawat t = bill == null ? tempat(source) : data.tempat.get(bill.kunci);
				if (t == null) {
					return 0;
				}
				if (bill == null) {
					bill = contoh(server, t, player);
				}
				boolean sent = Penagih.paksa(player, t, bill);
				long total = bill.jumlah;
				source.sendSuccess(() -> Component.translatable(sent ? U + "tagihan_dikirim" : U + "tagihan_gagal", total), false);
				return sent ? 1 : 0;
			}
			case "bayar", "tolak" -> {
				if (bill == null) {
					source.sendFailure(Component.translatable(U + "tagihan_tidak_ada"));
					return 0;
				}
				TempatRawat t = data.tempat.get(bill.kunci);
				if (t == null) {
					return 0;
				}
				if (bill.status == PerbaikanData.T_MENUNGGU) {
					bill.status = PerbaikanData.T_TERBUKA;
					bill.ditunjukkan = player.level().getGameTime();
				}
				if (mode.equals("bayar")) {
					Penagihan.bayar(player, t, bill);
				} else {
					Penagihan.tolak(player, t, bill);
				}
				return 1;
			}
			default -> {
				int shown = 0;
				for (PerbaikanData.TagihanData b : data.tagihan.values()) {
					if (!b.pembayar.equals(player.getUUID())) {
						continue;
					}
					shown++;
					source.sendSuccess(() -> Component.translatable(U + "tagihan_baris", b.id, b.jumlah,
							Component.translatable("kultivator.tempat_perbaikan.status." + b.status), b.hariJatuhTempo, b.gaya), false);
				}
				if (shown == 0) {
					source.sendSuccess(() -> Component.translatable(U + "tagihan_tidak_ada").withStyle(ChatFormatting.GRAY), false);
				}
				return shown;
			}
		}
	}

	/** A sample bill for {@code uji tempat tagihan buat} (24 bricks, 40 planks, 6 glass), like the wireframe of 38.U.3.7. */
	private static PerbaikanData.TagihanData contoh(MinecraftServer server, TempatRawat t, ServerPlayer player) {
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.TagihanData bill = new PerbaikanData.TagihanData(data.id(), t.situsId, t.kunci, player.getUUID(), t.profil().gaya(), Kota.day(server));
		bill.faktor = t.profil().faktor();
		bill.nama = player.getName().getString();
		bill.rincian.put(NilaiBlok.BATA, 24 * 0.4D);
		bill.jumlahBlok.put(NilaiBlok.BATA, 24);
		bill.rincian.put(NilaiBlok.KAYU, 40 * 0.2D);
		bill.jumlahBlok.put(NilaiBlok.KAYU, 40);
		bill.rincian.put(NilaiBlok.KACA, 6 * 0.3D);
		bill.jumlahBlok.put(NilaiBlok.KACA, 6);
		Penagihan.hitung(bill);
		data.tagihan.put(bill.id, bill);
		data.kotor();
		return bill;
	}

	private static int takut(CommandSourceStack source, int value) {
		TempatRawat t = tempat(source);
		if (t == null) {
			return 0;
		}
		Takut.set(source.getServer(), t.situsId, value);
		source.sendSuccess(() -> Component.translatable(U + "takut", Penagihan.namaTempat(t), value), false);
		return value;
	}

	// ================================================================== cetak, sitaan, gugus

	private static int cetak(CommandSourceStack source, String mode) {
		MinecraftServer server = source.getServer();
		if (mode.equals("info")) {
			int[] r = Rekam.statistik();
			int[] s = CetakStore.statistik();
			source.sendSuccess(() -> Component.translatable(U + "cetak_info", r[0], r[1], r[2], s[0], s[1], PindaiTempat.baca()), false);
			BlockPos here = BlockPos.containing(source.getPosition());
			if (Rekam.tempatDi(source.getLevel(), here) == null && Rekam.terdekat(source.getLevel(), here, 48.0D) == null) {
				return r[0];
			}
		}
		TempatRawat t = tempat(source);
		if (t == null) {
			return 0;
		}
		ServerLevel level = source.getLevel();
		switch (mode) {
			case "rekam" -> {
				int n = Rekam.rekamUlang(level, t);
				source.sendSuccess(() -> Component.translatable(U + "cetak_rekam", n), false);
				return n;
			}
			case "bandingkan" -> {
				int[] diff = bandingkan(level, t);
				source.sendSuccess(() -> Component.translatable(U + "cetak_banding", diff[0], diff[1], diff[2]), false);
				return diff[1];
			}
			default -> {
				source.sendSuccess(() -> Component.translatable(U + "cetak_tempat", t.struktur.toString(), t.jenis, Long.toHexString(t.situsId),
						t.terekam.size(), t.chunks.length, t.potongan.size(), Takut.kekuatan(level, t)), false);
				return 1;
			}
		}
	}

	/** {recorded blocks read, damage differences, other differences} over the loaded recorded chunks (capped). */
	private static int[] bandingkan(ServerLevel level, TempatRawat t) {
		int read = 0;
		int damage = 0;
		int other = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (long key : t.chunks) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
			Cetak c = chunk == null ? null : Rekam.cetak(t, key);
			if (c == null) {
				continue;
			}
			for (int i = 0; i < c.ukuran() && read < 400_000; i++) {
				BlockState rec = c.voxel(i);
				if (rec == null) {
					continue;
				}
				read++;
				c.posisi(i, at);
				BlockState now = chunk.getBlockState(at);
				if (now == rec) {
					continue;
				}
				if (Banding.rusak(rec, now, c, at.immutable(), t) != null) {
					damage++;
				} else {
					other++;
				}
			}
		}
		return new int[]{read, damage, other};
	}

	private static int sitaan(CommandSourceStack source) {
		TempatRawat t = tempat(source);
		if (t == null) {
			return 0;
		}
		MinecraftServer server = source.getServer();
		List<PerbaikanData.SitaanItem> list = PerbaikanData.get(server).sitaan.getOrDefault(t.situsId, List.of());
		source.sendSuccess(() -> Component.translatable(U + "sitaan", Penagihan.namaTempat(t), list.size()), false);
		long day = Kota.day(server);
		for (PerbaikanData.SitaanItem e : list) {
			source.sendSuccess(() -> Component.translatable(U + "sitaan_baris", e.item(), e.jumlah(), nama(server, e.pemilik()),
					Math.max(0L, Sitaan.HARI_SIMPAN - (day - e.hari()))), false);
		}
		return list.size();
	}

	private static int gugus(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		PerbaikanData data = PerbaikanData.get(server);
		int[] crews = Tukang.statistik();
		source.sendSuccess(() -> Component.translatable(U + "gugus_judul", data.gugus.size(), crews[0], crews[1], Bantu.jumlah()), false);
		List<Gugus> list = new ArrayList<>(data.gugus.values());
		list.sort(Comparator.comparingLong(g -> -g.id));
		int shown = 0;
		for (Gugus g : list) {
			if (shown++ >= 12) {
				break;
			}
			TempatRawat t = data.tempat.get(g.kunci);
			Component place = t == null ? Component.literal("?") : Penagihan.namaTempat(t);
			StringBuilder culprits = new StringBuilder();
			for (Map.Entry<UUID, Integer> e : g.pelakuSemua().entrySet()) {
				if (!culprits.isEmpty()) {
					culprits.append(", ");
				}
				culprits.append(nama(server, e.getKey()).getString()).append(" x").append(e.getValue());
			}
			String flags = (g.dilaporkan ? "L" : "-") + (g.sewa ? "S" : "-") + (g.takut ? "T" : "-") + (g.mohonBantuan ? "B" : "-");
			int pct = Math.round(g.persen() * 100.0F);
			String who = culprits.isEmpty() ? Component.translatable(U + "tanpa_pelaku").getString() : culprits.toString();
			source.sendSuccess(() -> Component.translatable(U + "gugus_baris", g.id, place, g.sisa(), g.ops.size(), pct,
					Component.translatable("kultivator.tempat_perbaikan.sebab." + g.sebabUtama()), who, flags,
					g.kotak.getCenter().toShortString()), false);
		}
		return data.gugus.size();
	}

	// ================================================================== player: /kultivasi sitaan [ambil]

	private static int sitaanPemain(CommandSourceStack source, boolean take) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		MinecraftServer server = source.getServer();
		PerbaikanData data = PerbaikanData.get(server);
		if (take) {
			TempatRawat t = Rekam.terdekat(player.level(), player.blockPosition(), Penagih.JARAK_TAGIH);
			if (t == null) {
				source.sendFailure(Component.translatable("kultivator.tempat_perbaikan.sitaan.jauh"));
				return 0;
			}
			int n = Sitaan.kembalikan(player, t.situsId);
			if (n == 0) {
				source.sendFailure(Component.translatable("kultivator.tempat_perbaikan.sitaan.kosong"));
			}
			return n;
		}
		int lines = 0;
		long day = Kota.day(server);
		for (Map.Entry<Long, List<PerbaikanData.SitaanItem>> e : data.sitaan.entrySet()) {
			TempatRawat t = Rekam.bySitus(e.getKey());
			for (PerbaikanData.SitaanItem item : e.getValue()) {
				if (!player.getUUID().equals(item.pemilik())) {
					continue;
				}
				lines++;
				Component place = t == null ? Component.literal("?") : Penagihan.namaTempat(t);
				BlockPos at = t == null ? BlockPos.ZERO : t.pusat();
				source.sendSuccess(() -> Component.translatable("kultivator.tempat_perbaikan.sitaan.baris", item.item(), item.jumlah(), place,
						at.getX(), at.getZ(), Math.max(0L, Sitaan.HARI_SIMPAN - (day - item.hari()))), false);
			}
		}
		if (lines == 0) {
			source.sendSuccess(() -> Component.translatable("kultivator.tempat_perbaikan.sitaan.kosong").withStyle(ChatFormatting.GRAY), false);
		}
		return lines;
	}

	private PerbaikanCommands() {
	}
}
