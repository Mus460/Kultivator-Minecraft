package dev.kultivator.dunia.kota42.sekte;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * The commands of 42E (DESIGN 42.14.2, 42.14.3). Player subs of {@code /kultivasi kotaku}: {@code kantor}, {@code pakta},
 * {@code cabang}, {@code murid}, each running the same action as its button on the 42U-2 screens (one handler: the APIs of
 * {@link KantorRekrut}, {@link PaktaLindung}, {@link SerbuanKota}, {@link TugasKotaMurid}, {@link CabangKota}); without an
 * argument they print the state of the place the player stands in. Test sub {@code /kultivasi uji kota_hidup sekte}:
 * {@code kantor [buat|pelamar <n>|ujian]}, {@code pakta [buat|serbuan|gagal]}, {@code murid <tugas>}, {@code cabang <jenis>}.
 */
final class KotaSekteUji {

	private static boolean registered;

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		KotaHidupCommands.daftar("kantor", KotaSekteUji::kantor);
		KotaHidupCommands.daftar("pakta", KotaSekteUji::pakta);
		KotaHidupCommands.daftar("cabang", KotaSekteUji::cabang);
		KotaHidupCommands.daftar("murid", KotaSekteUji::murid);
		KotaUji.daftar("sekte", KotaSekteUji::uji);
	}

	private interface Aksi {
		KotaSekte.Hasil jalan(ServerPlayer player, long situs);
	}

	/** Runs {@code aksi} at the place the player stands in (the action sends its own message). */
	private static int diSini(CommandContext<CommandSourceStack> ctx, Aksi aksi) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo tempat = KotaSekte.tempat(player);
		if (tempat == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		return aksi.jalan(player, tempat.situsId()).ok() ? 1 : 0;
	}

	private static void baris(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}

	private static void judul(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
	}

	// ================================================================== kotaku kantor

	private static LiteralArgumentBuilder<CommandSourceStack> kantor() {
		return Commands.literal("kantor").executes(ctx -> diSini(ctx, KotaSekteUji::kantorLihat))
				.then(Commands.literal("sewa").executes(ctx -> diSini(ctx, KantorRekrut::sewaKantor)))
				.then(Commands.literal("perpanjang").executes(ctx -> diSini(ctx, KantorRekrut::perpanjang)))
				.then(Commands.literal("tutup").then(Commands.literal("ya").executes(ctx -> diSini(ctx, KantorRekrut::tutup))))
				.then(Commands.literal("ujian").executes(ctx -> diSini(ctx, KantorRekrut::adakanUjian)))
				.then(wargaArg("uji", KantorRekrut::uji))
				.then(wargaArg("terima", KantorRekrut::terima))
				.then(wargaArg("tolak", KantorRekrut::tolak))
				.then(Commands.literal("juru")
						.then(Commands.literal("lepas").executes(ctx -> diSini(ctx, (p, s) -> KantorRekrut.juruTulis(p, s, 0))))
						.then(Commands.argument("warga", IntegerArgumentType.integer(1))
								.executes(ctx -> diSini(ctx, (p, s) -> KantorRekrut.juruTulis(p, s, IntegerArgumentType.getInteger(ctx, "warga"))))));
	}

	private interface AksiWarga {
		KotaSekte.Hasil jalan(ServerPlayer player, long situs, int warga);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> wargaArg(String nama, AksiWarga aksi) {
		return Commands.literal(nama).then(Commands.argument("warga", IntegerArgumentType.integer(1))
				.executes(ctx -> diSini(ctx, (p, s) -> aksi.jalan(p, s, IntegerArgumentType.getInteger(ctx, "warga")))));
	}

	private static KotaSekte.Hasil kantorLihat(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CommandSourceStack source = player.createCommandSourceStack();
		UUID owner = player.getUUID();
		KantorRekrut.Kantor k = KantorRekrut.kantor(situs, owner);
		judul(source, Component.translatable("kultivator.kota42.sekte.cmd.kantor.judul", KotaSekte.namaTempat(server, situs)));
		if (k == null) {
			baris(source, Component.translatable("kultivator.kota42.sekte.cmd.kantor.sewa", Barang.nb(KantorRekrut.sewa(server, situs)),
					KantorRekrut.bilik(server, situs)));
			for (KantorRekrut.Syarat s : KantorRekrut.syarat(player, situs)) {
				baris(source, centang(s.ok(), s.teks(), s.punya()));
			}
			return new KotaSekte.Hasil(true, Component.empty());
		}
		int[] st = KantorRekrut.statistik(server, situs, owner);
		baris(source, Component.translatable("kultivator.kota42.sekte.cmd.kantor.status", k.sewaSampai(),
				KantorRekrut.diterimaMinggu(server, situs, owner), KantorRekrut.TERIMA_MAKS, st[0], st[1], st[2]));
		List<KantorRekrut.Pelamar> pel = KantorRekrut.pelamar(server, situs, owner);
		if (pel.isEmpty()) {
			baris(source, Component.translatable("kultivator.kota42.sekte.cmd.kantor.kosong").withStyle(ChatFormatting.GRAY));
		}
		for (KantorRekrut.Pelamar p : pel) {
			Component akar = KantorRekrut.namaAkar(p.akarTampil()).copy().append(p.diuji() ? "" : "?");
			Component restu = p.restu() < 0L ? Component.translatable("kultivator.kota42.sekte.cmd.kantor.restu_tolak")
					: p.restu() == 0L ? Component.translatable("kultivator.kota42.sekte.cmd.kantor.restu_ok")
					: Component.translatable("kultivator.kota42.sekte.cmd.kantor.restu_uang", Barang.nb(p.restu()));
			baris(source, Component.translatable("kultivator.kota42.sekte.cmd.kantor.pelamar", p.warga().id, WargaApi.nama(p.warga()),
					WargaApi.umur(p.warga()), akar, restu).withStyle(p.istimewa() ? ChatFormatting.AQUA : ChatFormatting.WHITE));
		}
		long berikut = KantorRekrut.ujianBerikut(server, situs, owner);
		baris(source, Component.translatable("kultivator.kota42.sekte.cmd.kantor.ujian", Math.max(berikut, KotaSekte.hari(server)),
				Barang.nb(KantorRekrut.BIAYA_UJIAN)).withStyle(ChatFormatting.GRAY));
		return new KotaSekte.Hasil(true, Component.empty());
	}

	private static Component centang(boolean ok, Component teks, Component punya) {
		return Component.translatable(ok ? "kultivator.kota42.sekte.cmd.ok" : "kultivator.kota42.sekte.cmd.belum", teks, punya)
				.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	// ================================================================== kotaku pakta

	private static LiteralArgumentBuilder<CommandSourceStack> pakta() {
		LiteralArgumentBuilder<CommandSourceStack> rebut = Commands.literal("rebut");
		for (String cara : List.of("dialog", "tanding", "dipilih")) {
			rebut.then(Commands.literal(cara).executes(ctx -> diSini(ctx, (p, s) -> PaktaLindung.rebut(p, s, cara))));
		}
		return Commands.literal("pakta").executes(ctx -> diSini(ctx, KotaSekteUji::paktaLihat))
				.then(Commands.literal("buat").executes(ctx -> diSini(ctx, PaktaLindung::buat)))
				.then(rebut)
				.then(Commands.literal("upeti").executes(ctx -> diSini(ctx, PaktaLindung::ambilUpeti)))
				.then(Commands.literal("bela")
						.then(Commands.literal(SerbuanKota.CARA_DATANG).executes(ctx -> diSini(ctx, (p, s) -> SerbuanKota.jawab(p, s, SerbuanKota.CARA_DATANG))))
						.then(Commands.literal(SerbuanKota.CARA_KIRIM).executes(ctx -> diSini(ctx, (p, s) -> SerbuanKota.jawab(p, s, SerbuanKota.CARA_KIRIM)))))
				.then(Commands.literal("putus").then(Commands.literal("ya").executes(ctx -> diSini(ctx, PaktaLindung::putus))));
	}

	private static KotaSekte.Hasil paktaLihat(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CommandSourceStack source = player.createCommandSourceStack();
		judul(source, Component.translatable("kultivator.kota42.sekte.cmd.pakta.judul", KotaSekte.namaTempat(server, situs)));
		UUID pelindung = PaktaLindung.pelindung(situs);
		if (pelindung == null) {
			TempatInfo info = KotaSekte.tempat(player);
			String lama = PaktaLindung.sekteLama(server, situs, info == null ? player.blockPosition() : info.kotak().getCenter());
			if (lama != null) {
				Component nama = SekteApi.diplomasi().name(lama);
				baris(source, Component.translatable("kultivator.kota42.sekte.cmd.pakta.lama", nama == null ? Component.literal(lama) : nama));
				for (PaktaLindung.Cara c : PaktaLindung.cara(player, situs, lama)) {
					baris(source, centang(c.bisa(), Component.translatable("kultivator.kota42.sekte.pakta.cara_nama." + c.id()), c.alasan()));
				}
			} else {
				baris(source, Component.translatable("kultivator.kota42.sekte.cmd.pakta.bebas"));
			}
			for (KantorRekrut.Syarat s : PaktaLindung.syarat(player, situs)) {
				baris(source, centang(s.ok(), s.teks(), s.punya()));
			}
			return new KotaSekte.Hasil(true, Component.empty());
		}
		baris(source, Component.translatable("kultivator.kota42.sekte.cmd.pakta.pelindung", KotaSekte.namaSekte(pelindung),
				PaktaLindung.sejak(server, situs)));
		if (pelindung.equals(player.getUUID())) {
			baris(source, Component.translatable("kultivator.kota42.sekte.cmd.pakta.upeti", Barang.nb(PaktaLindung.upetiNb(server, situs,
					Barang.pita(player))), PaktaLindung.upetiSiap(server, situs)));
			SerbuanKota.Bela b = SerbuanKota.bela(server, situs);
			baris(source, Component.translatable("kultivator.kota42.sekte.cmd.pakta.bela." + b.keadaan(),
					Component.translatable("kultivator.kota42.sekte.bela.jenis." + b.jenis()), b.kekuatan(),
					SerbuanKota.kekuatanKita(server, situs, player.getUUID(), player), b.sisaTick() / 1200L, b.berikut()));
			baris(source, Component.translatable("kultivator.kota42.sekte.cmd.pakta.gagal", PaktaLindung.gagal(server, situs), PaktaLindung.GAGAL_MAKS));
			for (TugasKotaMurid.Tugas t : TugasKotaMurid.penjagaKota(server, situs, player.getUUID())) {
				baris(source, Component.translatable("kultivator.kota42.sekte.cmd.murid.baris", t.nama(),
						Component.translatable("kultivator.kota42.sekte.murid.tugas." + t.tugas()), t.sampai()).withStyle(ChatFormatting.GRAY));
			}
		}
		return new KotaSekte.Hasil(true, Component.empty());
	}

	// ================================================================== kotaku murid

	private static LiteralArgumentBuilder<CommandSourceStack> murid() {
		return Commands.literal("murid").executes(KotaSekteUji::muridLihat)
				.then(Commands.literal("jaga").executes(ctx -> diSini(ctx, (p, s) -> TugasKotaMurid.tugaskan(p, s, null, TugasKotaMurid.JAGA_KOTA))))
				.then(Commands.literal("kerja").executes(ctx -> diSini(ctx, (p, s) -> TugasKotaMurid.tugaskan(p, s, null, TugasKotaMurid.KERJA_KOTA))))
				.then(Commands.literal("tarik").then(Commands.argument("no", IntegerArgumentType.integer(1)).executes(KotaSekteUji::muridTarik)));
	}

	private static int muridLihat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		List<TugasKotaMurid.Tugas> list = TugasKotaMurid.milik(server, player.getUUID());
		judul(ctx.getSource(), Component.translatable("kultivator.kota42.sekte.cmd.murid.judul"));
		if (list.isEmpty()) {
			baris(ctx.getSource(), Component.translatable("kultivator.kota42.sekte.cmd.murid.kosong").withStyle(ChatFormatting.GRAY));
		}
		int no = 1;
		for (TugasKotaMurid.Tugas t : list) {
			baris(ctx.getSource(), Component.translatable("kultivator.kota42.sekte.cmd.murid.daftar", no++, t.nama(),
					Component.translatable("kultivator.kota42.sekte.murid.tugas." + t.tugas()), KotaSekte.namaTempat(server, t.situs()), t.sampai()));
		}
		return 1;
	}

	private static int muridTarik(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<TugasKotaMurid.Tugas> list = TugasKotaMurid.milik(player.level().getServer(), player.getUUID());
		int no = IntegerArgumentType.getInteger(ctx, "no");
		if (no > list.size()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.sekte.gagal.murid.tidak_bertugas"));
			return 0;
		}
		return TugasKotaMurid.tarik(player, list.get(no - 1).soul()).ok() ? 1 : 0;
	}

	// ================================================================== kotaku cabang

	private static LiteralArgumentBuilder<CommandSourceStack> cabang() {
		LiteralArgumentBuilder<CommandSourceStack> jenis = Commands.literal("jenis");
		for (String id : CabangKota.JENIS) {
			jenis.then(Commands.literal(id).executes(ctx -> diSini(ctx, (p, s) -> CabangKota.pilihJenis(p, s, id))));
		}
		return Commands.literal("cabang").executes(ctx -> diSini(ctx, KotaSekteUji::cabangLihat))
				.then(jenis)
				.then(Commands.literal("tutup").then(Commands.literal("ya").executes(ctx -> diSini(ctx, CabangKota::tutup))));
	}

	private static KotaSekte.Hasil cabangLihat(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CommandSourceStack source = player.createCommandSourceStack();
		CabangKota.Cabang c = CabangKota.cabang(situs);
		if (c == null) {
			judul(source, Component.translatable("kultivator.kota42.sekte.cmd.cabang.judul_kosong", KotaSekte.namaTempat(server, situs)));
			baris(source, Component.translatable("kultivator.kota42.sekte.cmd.cabang.cara", CabangKota.AULA_MIN, CabangKota.NAMA_BAIK_MIN));
			return new KotaSekte.Hasil(true, Component.empty());
		}
		judul(source, CabangKota.nama(server, c));
		baris(source, Component.translatable("kultivator.kota42.sekte.cmd.cabang.jenis",
				Component.translatable("kultivator.kota42.sekte.cabang.jenis." + c.jenis()), c.sejak(), CabangKota.gantiBoleh(server, situs)));
		if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			CabangKota.periksa(level, situs);
			dev.kultivator.dunia.kota42.ekonomi.RumahKota.Info info = dev.kultivator.dunia.kota42.ekonomi.RumahKota.info(server, situs, c.rumah());
			if (info != null && level.isLoaded(info.kotak().getCenter())) {
				for (CabangKota.Isi isi : CabangKota.isi(level, info.kotak(), c.jenis())) {
					baris(source, centang(isi.ok(), isi.teks(), Component.literal(isi.punya() + "/" + isi.perlu())));
				}
			}
		}
		baris(source, Component.translatable(CabangKota.bekerja(server, situs) ? "kultivator.kota42.sekte.cmd.cabang.bekerja"
				: "kultivator.kota42.sekte.cmd.cabang.belum"));
		return new KotaSekte.Hasil(true, Component.empty());
	}

	// ================================================================== uji kota_hidup sekte

	private static LiteralArgumentBuilder<CommandSourceStack> uji() {
		LiteralArgumentBuilder<CommandSourceStack> murid = Commands.literal("murid");
		for (String tugas : List.of(TugasKotaMurid.JAGA_KOTA, TugasKotaMurid.KERJA_KOTA, TugasKotaMurid.BELANJA)) {
			murid.then(Commands.literal(tugas).executes(ctx -> diSini(ctx, (p, s) -> TugasKotaMurid.tugaskan(p, s, null, tugas))));
		}
		LiteralArgumentBuilder<CommandSourceStack> cabang = Commands.literal("cabang");
		for (String id : CabangKota.JENIS) {
			cabang.then(Commands.literal(id).executes(ctx -> diSini(ctx, (p, s) -> CabangKota.paksa(p, id))));
		}
		return Commands.literal("sekte")
				.then(Commands.literal("kantor").executes(ctx -> diSini(ctx, KotaSekteUji::kantorLihat))
						.then(Commands.literal("buat").executes(ctx -> diSini(ctx, KantorRekrut::paksaSewa)))
						.then(Commands.literal("pelamar").then(Commands.argument("n", IntegerArgumentType.integer(1, KantorRekrut.PELAMAR_MAKS))
								.executes(ctx -> diSini(ctx, (p, s) -> KantorRekrut.paksaPelamar(p, s, IntegerArgumentType.getInteger(ctx, "n"))))))
						.then(Commands.literal("ujian").executes(ctx -> diSini(ctx, KantorRekrut::paksaUjian))))
				.then(Commands.literal("pakta").executes(ctx -> diSini(ctx, KotaSekteUji::paktaLihat))
						.then(Commands.literal("buat").executes(ctx -> diSini(ctx, KotaSekteUji::paksaPakta)))
						.then(Commands.literal("serbuan").executes(ctx -> diSini(ctx, (p, s) -> hasilUji(p, SerbuanKota.paksa(p.level().getServer(), s), "serbuan"))))
						.then(Commands.literal("gagal").executes(ctx -> diSini(ctx, (p, s) -> hasilUji(p, SerbuanKota.paksaGagal(p.level().getServer(), s), "gagal")))))
				.then(murid)
				.then(cabang);
	}

	private static KotaSekte.Hasil paksaPakta(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		UUID ada = PaktaLindung.pelindung(situs);
		if (ada != null) {
			PaktaLindung.putuskan(server, situs, "diputus");
		}
		PaktaLindung.jadikan(server, situs, player.getUUID(), player);
		return KotaSekte.Hasil.berhasil("uji.pakta", KotaSekte.namaTempat(server, situs)).kirim(player);
	}

	private static KotaSekte.Hasil hasilUji(ServerPlayer player, boolean ok, String apa) {
		return (ok ? KotaSekte.Hasil.berhasil("uji." + apa) : KotaSekte.Hasil.gagal("pakta.bukan_milik")).kirim(player);
	}

	private KotaSekteUji() {
	}
}
