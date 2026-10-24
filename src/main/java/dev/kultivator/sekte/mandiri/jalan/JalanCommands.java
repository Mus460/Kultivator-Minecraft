package dev.kultivator.sekte.mandiri.jalan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.MandiriCommands;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The commands of 39C (DESIGN 39.10.4), hung into {@code MandiriCommands}:
 * <ul>
 *   <li>player {@code /kultivasi perguruan mandiri jelmaan <aksi> <jiwa>}: the chat buttons of a town body
 *       ({@code belanja} the list, purse and bag; {@code uang} the stones in the master's hand into the purse;
 *       {@code pulang} take the disciple home now; {@code lanjut}) and of a captive's notice ({@code tebus}, {@code lacak});</li>
 *   <li>op {@code uji perguruan mandiri kirim <murid> <jenis> [tujuan]} (start a journey now, with the farewell scene),
 *       {@code percepat <pelancong> [tahap|pulang]} (move the timeline), {@code jelmaan <pelancong>} (show the town body
 *       here), {@code perjalanan [pelancong]} (the journeys and their scroll), {@code tawan <pelancong>} (capture now, to
 *       test the rescue).</li>
 * </ul>
 * The purse test {@code dompet} and the bag test {@code tas} of 39.10.4 are 39A's ({@code MandiriUji}); 39C's money goes
 * through the same {@code MandiriApi.ubahDompet}.
 */
final class JalanCommands {

	private static final SuggestionProvider<CommandSourceStack> MURID = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		if (player != null) {
			for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
				if (e.role() == DiscipleEntity.Role.DISCIPLE) {
					names.add(e.getName().getString().replace(' ', '_'));
				}
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> PELANCONG = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		if (player != null) {
			JalanData data = JalanData.get(player.level().getServer());
			for (UUID soul : data.jiwaAktif()) {
				CompoundTag tag = data.perjalanan(soul);
				if (tag != null && player.getUUID().toString().equals(tag.getStringOr(Rencana.OWNER, ""))) {
					names.add(tag.getStringOr(Rencana.NAMA, soul.toString()).replace(' ', '_'));
				}
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> JENIS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(JenisJalan.values()).map(JenisJalan::id), builder);

	private static final SuggestionProvider<CommandSourceStack> AKSI = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("belanja", "uang", "pulang", "lanjut", "tebus", "lacak"), builder);

	private static boolean installed;

	static void init() {
		if (installed) {
			return;
		}
		installed = true;
		MandiriCommands.pemain("jelmaan", () -> Commands.literal("jelmaan")
				.then(Commands.argument("aksi", StringArgumentType.word()).suggests(AKSI)
						.then(Commands.argument("jiwa", StringArgumentType.word()).executes(JalanCommands::aksi))));
		MandiriCommands.uji("kirim", () -> Commands.literal("kirim").then(murid()
				.then(Commands.argument("jenis", StringArgumentType.word()).suggests(JENIS)
						.executes(ctx -> kirim(ctx, null))
						.then(Commands.argument("tujuan", StringArgumentType.greedyString())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("terdekat", "langsung"), b))
								.executes(ctx -> kirim(ctx, StringArgumentType.getString(ctx, "tujuan")))))));
		MandiriCommands.uji("percepat", () -> Commands.literal("percepat").then(pelancong()
				.executes(ctx -> percepat(ctx, -1))
				.then(Commands.literal("pulang").executes(ctx -> percepat(ctx, Integer.MAX_VALUE)))
				.then(Commands.argument("tahap", IntegerArgumentType.integer(0, 200)).executes(ctx -> percepat(ctx,
						IntegerArgumentType.getInteger(ctx, "tahap"))))));
		MandiriCommands.uji("jelmaan", () -> Commands.literal("jelmaan").then(pelancong().executes(JalanCommands::jelmaan)));
		MandiriCommands.uji("perjalanan", () -> Commands.literal("perjalanan")
				.executes(JalanCommands::daftar)
				.then(pelancong().executes(JalanCommands::rinci)));
		MandiriCommands.uji("tawan", () -> Commands.literal("tawan").then(pelancong().executes(JalanCommands::tawan)));
		MandiriCommands.chips("perguruan mandiri kirim", "perguruan mandiri percepat", "perguruan mandiri jelmaan",
				"perguruan mandiri perjalanan", "perguruan mandiri tawan");
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> murid() {
		return Commands.argument("murid", StringArgumentType.word()).suggests(MURID);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> pelancong() {
		return Commands.argument("pelancong", StringArgumentType.word()).suggests(PELANCONG);
	}

	private static void baris(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}

	private static @Nullable DiscipleEntity cariMurid(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "murid");
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() == DiscipleEntity.Role.DISCIPLE && e.getName().getString().replace(' ', '_').equalsIgnoreCase(name)) {
				return e;
			}
		}
		ctx.getSource().sendFailure(JalanIds.t("cmd.tidak_ada", name));
		return null;
	}

	/** The travelling soul of the player named {@code pelancong}, or null (failure sent). */
	private static @Nullable UUID cariPelancong(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "pelancong");
		JalanData data = JalanData.get(player.level().getServer());
		for (UUID soul : data.jiwaAktif()) {
			CompoundTag tag = data.perjalanan(soul);
			if (tag != null && player.getUUID().toString().equals(tag.getStringOr(Rencana.OWNER, ""))
					&& tag.getStringOr(Rencana.NAMA, "").replace(' ', '_').equalsIgnoreCase(name)) {
				return soul;
			}
		}
		ctx.getSource().sendFailure(JalanIds.t("cmd.tidak_pergi", name));
		return null;
	}

	// ================================================================== uji

	private static int kirim(CommandContext<CommandSourceStack> ctx, @Nullable String tujuan) throws CommandSyntaxException {
		DiscipleEntity murid = cariMurid(ctx);
		if (murid == null || !(murid.level() instanceof ServerLevel level)) {
			return 0;
		}
		JenisJalan jenis = JenisJalan.byId(StringArgumentType.getString(ctx, "jenis"));
		if (jenis == null) {
			ctx.getSource().sendFailure(JalanIds.t("cmd.jenis_salah", StringArgumentType.getString(ctx, "jenis")));
			return 0;
		}
		if (Perjalanan.rencana(level.getServer(), murid.soulId()) != null) {
			ctx.getSource().sendFailure(JalanIds.t("cmd.sudah_pergi", murid.getName()));
			return 0;
		}
		boolean langsung = "langsung".equals(tujuan);
		PelaksanaJalan.Pilihan p = PelaksanaJalan.paksa(murid, jenis, langsung ? null : tujuan);
		if (p == null) {
			ctx.getSource().sendFailure(JalanIds.t("cmd.tanpa_tujuan", murid.getName(), Component.translatable(jenis.kunciNama())));
			return 0;
		}
		Component where = p.tujuan().town() != 0L ? JalanIds.namaKota(p.tujuan().town()) : Component.literal(p.tujuan().id());
		long jarak = Math.round(Tujuan.jarak(murid.blockPosition(), p.tujuan().pos()));
		if (!langsung && MandiriApi.mulaiKegiatan(murid, Kegiatan.PERJALANAN, -1, null, null)) {
			baris(ctx.getSource(), JalanIds.t("cmd.kirim.adegan", murid.getName(), Component.translatable(jenis.kunciNama()), where, jarak)
					.withStyle(ChatFormatting.GREEN));
			return 1;
		}
		Perjalanan.berangkat(level, List.of(murid), jenis, p.tujuan());
		Rencana r = Perjalanan.rencana(level.getServer(), murid.soulId());
		if (r == null) {
			ctx.getSource().sendFailure(JalanIds.t("cmd.kirim.gagal", murid.getName()));
			return 0;
		}
		baris(ctx.getSource(), JalanIds.t("cmd.kirim.langsung", murid.getName(), Component.translatable(jenis.kunciNama()), where, jarak,
				r.jumlahTahap(), Perjalanan.menit(r.pulang() - level.getGameTime())).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int percepat(CommandContext<CommandSourceStack> ctx, int tahap) throws CommandSyntaxException {
		UUID soul = cariPelancong(ctx);
		if (soul == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		Rencana r = Perjalanan.rencana(server, soul);
		if (r == null) {
			return 0;
		}
		long now = server.overworld().getGameTime();
		if (tahap == Integer.MAX_VALUE) {
			// straight home: every stage resolves now, the return is due
			Perjalanan.majukanKe(server, r, r.jumlahTahap() - 1, now);
			Tahap.majukan(server, r, Long.MAX_VALUE / 4L);
			r.setPulang(now);
			Kepulangan.aturPulang(server, r);
			baris(ctx.getSource(), JalanIds.t("cmd.percepat.pulang", r.nama()).withStyle(ChatFormatting.GREEN));
			return 1;
		}
		int target = tahap >= 0 ? tahap : Math.max(r.berikut(), r.tahapPada(now) + 1);
		Perjalanan.majukanKe(server, r, target, now);
		int i = Math.max(0, Math.min(r.jumlahTahap() - 1, r.tahapPada(now)));
		String kind = r.jumlahTahap() == 0 ? "-" : r.tahap(i).getStringOr("jenis", "-");
		baris(ctx.getSource(), JalanIds.t("cmd.percepat.ok", r.nama(), i, r.jumlahTahap(), JalanIds.t("jenis_tahap." + kind))
				.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int jelmaan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		UUID soul = cariPelancong(ctx);
		if (soul == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!Jelmaan.paksa(player, soul)) {
			ctx.getSource().sendFailure(JalanIds.t("cmd.jelmaan.gagal"));
			return 0;
		}
		baris(ctx.getSource(), JalanIds.t("cmd.jelmaan.ok").withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int daftar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		JalanData data = JalanData.get(server);
		long now = server.overworld().getGameTime();
		baris(ctx.getSource(), JalanIds.t("cmd.daftar.judul", data.jumlahAktif(), Jelmaan.jumlah()).withStyle(ChatFormatting.GOLD));
		int n = 0;
		for (UUID soul : data.jiwaAktif()) {
			CompoundTag tag = data.perjalanan(soul);
			if (tag == null || !player.getUUID().toString().equals(tag.getStringOr(Rencana.OWNER, ""))
					|| !tag.getStringOr(Rencana.IKUT, "").isEmpty()) {
				continue;
			}
			Rencana r = new Rencana(tag);
			baris(ctx.getSource(), ringkas(r, now));
			n++;
		}
		if (n == 0) {
			baris(ctx.getSource(), JalanIds.t("cmd.daftar.kosong").withStyle(ChatFormatting.GRAY));
		}
		return n;
	}

	private static MutableComponent ringkas(Rencana r, long now) {
		int i = r.tahapPada(now);
		String kind = i < 0 ? (now >= r.pulang() ? "tiba" : "-") : r.tahap(i).getStringOr("jenis", "-");
		Component where = r.town() != 0L ? JalanIds.namaKota(r.town()) : Component.translatable(r.jenis().kunciNama());
		return JalanIds.t("cmd.daftar.baris", Component.literal(r.nama()).withColor(JalanIds.WARNA_NAMA), where,
				Component.translatable(r.jenis().kunciNama()), r.rombongan().size() - 1, Math.max(0, i), r.jumlahTahap(),
				JalanIds.t("jenis_tahap." + kind), Perjalanan.menit(Math.max(0L, r.pulang() - now)))
				.withColor(r.tertawan() ? JalanIds.WARNA_MERAH : JalanIds.WARNA_GIOK);
	}

	private static int rinci(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		UUID soul = cariPelancong(ctx);
		if (soul == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		Rencana r = Perjalanan.rencana(server, soul);
		if (r == null) {
			return 0;
		}
		long now = server.overworld().getGameTime();
		baris(ctx.getSource(), ringkas(r, now));
		baris(ctx.getSource(), JalanIds.t("cmd.rinci.moda", JalanIds.t("moda." + r.moda()), Math.round(r.kekuatan() * 100.0D) / 100.0D,
				r.sisaUang(r.soul()), r.pertemuan()).withStyle(ChatFormatting.GRAY));
		for (int i = 0; i < r.jumlahTahap(); i++) {
			CompoundTag t = r.tahap(i);
			String stop = t.getStringOr("stop", "");
			MutableComponent line = Component.literal((i < r.berikut() ? " ✔ " : i == r.tahapPada(now) ? " ▶ " : " · ") + i + " ")
					.append(JalanIds.t("jenis_tahap." + t.getStringOr("jenis", "-")));
			if (!stop.isEmpty()) {
				line.append(" ").append(JalanIds.t("stop." + stop));
			}
			line.append(Component.literal("  " + Perjalanan.menit(Math.max(0L, t.getLongOr("mulai", now) - now)) + "'"));
			baris(ctx.getSource(), line.withStyle(i < r.berikut() ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
		}
		ListTag scroll = r.hasil().getListOrEmpty("tahap");
		for (int i = 0; i < scroll.size(); i++) {
			baris(ctx.getSource(), Component.literal(" ~ ").append(JalanIds.teksTahap(scroll.getStringOr(i, ""))).withColor(0xE6E0D0));
		}
		return 1;
	}

	private static int tawan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		UUID soul = cariPelancong(ctx);
		if (soul == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		Rencana r = Perjalanan.rencana(server, soul);
		ServerLevel level = r == null ? null : Tahap.level(server, r);
		if (r == null || level == null || r.tertawan() || r.selesai()) {
			ctx.getSource().sendFailure(JalanIds.t("cmd.tawan.gagal"));
			return 0;
		}
		long now = level.getGameTime();
		int i = Math.max(r.berikut(), Math.max(0, r.tahapPada(now)));
		i = Math.min(i, r.jumlahTahap() - 1);
		CompoundTag t = r.tahap(i);
		t.putLong("sampai", Math.max(t.getLongOr("mulai", now) + 20L, now));
		Tahap.Konteks k = new Tahap.Konteks(server, level, r, i, t, RandomSource.create(r.benih() ^ i), Tahap.hariPada(level, now),
				Tahap.jamPada(level, now));
		r.setBerikut(i + 1);
		Tawanan.tangkap(k, "begal", BlockPos.of(t.getLongOr("pos", 0L)));
		Tahap.simpanCermin(server, r);
		JalanData.get(server).setDirty();
		baris(ctx.getSource(), JalanIds.t("cmd.tawan.ok", r.nama()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	// ================================================================== the master's buttons

	private static int aksi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer master = ctx.getSource().getPlayerOrException();
		UUID soul = Rencana.uuid(StringArgumentType.getString(ctx, "jiwa"));
		MinecraftServer server = ctx.getSource().getServer();
		Rencana r = soul == null ? null : Perjalanan.rencana(server, soul);
		if (r == null) {
			ctx.getSource().sendFailure(JalanIds.t("temu.sudah_pulang"));
			return 0;
		}
		if (!master.getUUID().equals(r.owner())) {
			ctx.getSource().sendFailure(JalanIds.t("temu.bukan_guru"));
			return 0;
		}
		return switch (StringArgumentType.getString(ctx, "aksi")) {
			case "belanja" -> belanja(master, r, soul);
			case "uang" -> uang(master, r, soul);
			case "pulang" -> pulang(master, r, soul);
			case "lanjut" -> lanjut(master, soul);
			case "tebus" -> tebus(master, r);
			case "lacak" -> lacak(master, r);
			default -> 0;
		};
	}

	/** The stored tablet data of a travelling soul (read only), or null. */
	private static @Nullable DiscipleData data(Rencana r, UUID soul) {
		CompoundTag rec = Tahap.rekamAway(r.owner(), soul);
		return rec == null ? null : DiscipleData.load(rec.getCompoundOrEmpty("stored").getCompoundOrEmpty("disciple"));
	}

	/** [Lihat belanja]: the list still to buy, what was bought, the purse and the bag. */
	private static int belanja(ServerPlayer master, Rencana r, UUID soul) {
		DiscipleData data = data(r, soul);
		String nama = r.anggota(soul).getStringOr("nama", r.nama());
		Msg.chat(master, JalanIds.t("belanja.judul", Component.literal(nama).withColor(JalanIds.WARNA_NAMA)).withColor(JalanIds.WARNA_EMAS));
		ListTag bought = r.daftarBarang();
		int n = 0;
		for (int i = 0; i < bought.size(); i++) {
			CompoundTag one = bought.getCompoundOrEmpty(i);
			if (soul.toString().equals(one.getStringOr("untuk", "")) && one.getBooleanOr("beli", false)) {
				Msg.chat(master, JalanIds.t("belanja.dibeli", one.getIntOr("n", 1), Component.translatable(JalanIds.kunciBarang(one.getStringOr("item", ""))),
						one.getLongOr("harga", 0L), JalanIds.t("ke." + keJenis(one.getStringOr("ke", DaftarBelanja.KE_TAS)))).withColor(0x9FE3A0));
				n++;
			}
		}
		if (n == 0) {
			Msg.chat(master, JalanIds.t("belanja.belum").withColor(JalanIds.WARNA_ABU));
		}
		if (data != null) {
			for (DaftarBelanja.Baris b : DaftarBelanja.baca(data)) {
				if (KunjunganKota.sudahDibeli(r, soul, b.item(), b.ke()) >= b.n()) {
					continue;
				}
				Msg.chat(master, JalanIds.t("belanja.daftar", b.n(), Component.translatable(JalanIds.kunciBarang(b.item())),
						Component.translatableWithFallback(JalanIds.k("alasan." + b.alasan()), b.alasan()), b.maksHarga(), b.prio())
						.withColor(b.prio() == 1 ? JalanIds.WARNA_EMAS : 0xD8D0C0));
			}
			long dompet = MandiriApi.dompet(data) + r.uang(soul) + r.tambahan(soul);
			Msg.chat(master, JalanIds.t("belanja.dompet", Math.max(0L, dompet), MandiriApi.dompetMaks(data)).withColor(JalanIds.WARNA_EMAS));
			List<ItemStack> bag = MandiriApi.tas(data, master.level().registryAccess());
			MutableComponent list = Component.literal("");
			for (ItemStack s : bag) {
				if (!list.getSiblings().isEmpty()) {
					list.append(", ");
				}
				list.append(s.getCount() + "x ").append(s.getHoverName());
			}
			Msg.chat(master, JalanIds.t("belanja.tas", bag.size(), bag.isEmpty() ? JalanIds.t("kosong") : list).withColor(0xC8C0B0));
		}
		return 1;
	}

	private static String keJenis(String ke) {
		return ke.startsWith(DaftarBelanja.KE_HADIAH) ? "hadiah" : ke;
	}

	/** [Tambah uang]: the spirit stones in the master's hand go into the purse (up to its limit). */
	private static int uang(ServerPlayer master, Rencana r, UUID soul) {
		ItemStack hand = master.getItemInHand(InteractionHand.MAIN_HAND);
		int grade = SpiritStones.grade(hand);
		DiscipleData data = data(r, soul);
		if (grade < 0 || data == null) {
			Msg.chat(master, JalanIds.t("uang.tanpa_batu").withColor(JalanIds.WARNA_ABU));
			return 0;
		}
		long value = NilaiBatu.VALUE[grade];
		long room = MandiriApi.dompetMaks(data) - (MandiriApi.dompet(data) + r.uang(soul) + r.tambahan(soul));
		if (room < value) {
			Msg.chat(master, JalanIds.t("uang.penuh").withColor(JalanIds.WARNA_ABU));
			return 0;
		}
		int count = (int) Math.min(hand.getCount(), room / value);
		hand.shrink(count);
		long nb = count * value;
		r.tambahUangGuru(soul, nb);
		JalanData.get(master.level().getServer()).setDirty();
		DiscipleEntity body = Jelmaan.tubuh(soul);
		if (body != null) {
			SosokApi.gerak(body, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 40);
			SekteSuara.body(body, "thanks", "thanks");
			PelaksanaJalan.Msg2.dekat(body, JalanIds.t("ucap.terima_uang", nb), 12.0D);
		}
		Msg.chat(master, JalanIds.t("uang.ok", nb, Component.literal(r.anggota(soul).getStringOr("nama", r.nama()))).withColor(JalanIds.WARNA_EMAS));
		long hari = dev.kultivator.dunia.kota.Kota.day(master.level().getServer());
		MandiriApi.catat(r.owner(), soul, new EntriHarian(hari, dev.kultivator.sekte.mandiri.Irama.jam(master.level()), "jalan",
				JalanIds.k("harian.diberi_uang"), List.of(r.anggota(soul).getStringOr("nama", r.nama()), String.valueOf(nb)),
				EntriHarian.Suasana.SENANG, false));
		return 1;
	}

	/** [Ikut Guru pulang]: the party comes home now, appearing where the town body stands, and follows the master. */
	private static int pulang(ServerPlayer master, Rencana r, UUID soul) {
		DiscipleEntity body = Jelmaan.tubuh(soul);
		if (body == null || body.level() != master.level() || body.distanceToSqr(master) > 16.0D * 16.0D) {
			Msg.chat(master, JalanIds.t("pulang.jauh").withColor(JalanIds.WARNA_ABU));
			return 0;
		}
		MinecraftServer server = master.level().getServer();
		long now = server.overworld().getGameTime();
		// the rest of the visit is dropped; what was bought stays bought
		r.potong(r.berikut() - 1);
		r.panggil(body.blockPosition());
		r.setPulang(now);
		r.kisah(JalanIds.k("kisah.dipanggil"), List.of(r.nama()));
		Kepulangan.aturPulang(server, r);
		Tahap.simpanCermin(server, r);
		SosokApi.gerak(body, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 40);
		SekteSuara.body(body, "greet", "greet");
		PelaksanaJalan.Msg2.dekat(body, JalanIds.t("ucap.ikut_pulang", master.getName()), 12.0D);
		return 1;
	}

	/** [Lanjutkan]: a bow and back to the stalls. */
	private static int lanjut(ServerPlayer master, UUID soul) {
		DiscipleEntity body = Jelmaan.tubuh(soul);
		if (body != null) {
			SosokApi.gerak(body, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 30);
			PelaksanaJalan.Msg2.dekat(body, JalanIds.t("ucap.lanjut", master.getName()), 12.0D);
		}
		return 1;
	}

	/** [Tebus]: the ransom from the master's stones. */
	private static int tebus(ServerPlayer master, Rencana r) {
		if (!r.tertawan()) {
			Msg.chat(master, JalanIds.t("tebus.tidak_perlu").withColor(JalanIds.WARNA_ABU));
			return 0;
		}
		long price = r.tawanan().getLongOr("tebusan", 0L);
		if (!Tawanan.tebus(master, r)) {
			Msg.chat(master, JalanIds.t("tebus.kurang", price, NilaiBatu.total(master)).withColor(JalanIds.WARNA_MERAH));
			return 0;
		}
		Msg.chat(master, JalanIds.t("tebus.ok", Component.literal(r.nama()).withColor(JalanIds.WARNA_NAMA), price).withColor(JalanIds.WARNA_EMAS));
		return 1;
	}

	/** [Lacak]: where the captives are held, how far and which way. */
	private static int lacak(ServerPlayer master, Rencana r) {
		if (!r.tertawan()) {
			Msg.chat(master, JalanIds.t("tebus.tidak_perlu").withColor(JalanIds.WARNA_ABU));
			return 0;
		}
		BlockPos pos = BlockPos.of(r.tawanan().getLongOr("pos", 0L));
		double dx = pos.getX() + 0.5D - master.getX();
		double dz = pos.getZ() + 0.5D - master.getZ();
		long jarak = Math.round(Math.hypot(dx, dz));
		int arah = (int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8L);
		long sisa = Math.max(0L, r.tawanan().getLongOr("sampai", 0L) - master.level().getGameTime());
		Msg.chat(master, JalanIds.t("lacak", Component.literal(r.nama()).withColor(JalanIds.WARNA_NAMA), pos.getX(), pos.getY(), pos.getZ(), jarak,
				JalanIds.t("arah." + arah), Perjalanan.menit(sisa)).withColor(JalanIds.WARNA_GIOK));
		return 1;
	}

	private JalanCommands() {
	}
}
