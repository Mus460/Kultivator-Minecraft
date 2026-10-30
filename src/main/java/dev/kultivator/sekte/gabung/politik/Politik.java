package dev.kultivator.sekte.gabung.politik;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.diplomasi.Peristiwa;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungCommands;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungUji;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module 41D "Politik &amp; Perang" (DESIGN 41.4.2, 41.4.7, 41.6.10, 41.7.3 hunters, 41.8, 41.9 politics): seats and
 * challenges ({@link Kursi}), the succession "Jubah dan Mangkuk" ({@link Suksesi}, {@link PemilihanPewaris}), the call to
 * war and war missions ({@code PanggilanPerang}, {@code MisiPerang}), raids on the compound ({@code SerbuanKompleks}),
 * sect hunters of defectors ({@code PemburuSekte}), branch halls ({@link Cabang}), guest elders ({@link TetuaTamu}),
 * absorb and merge ({@link Penyatuan}), the sect ranking ({@link PeringkatSekte}) and the sect treasury
 * ({@link KasSekte}). The bridge into module D is {@code sekte.diplomasi.DiplomasiGabung}. Entry of the package
 * {@code sekte.gabung.politik}, called by {@code Gabung.init()} while 41 is on (no registry content here).
 * <p>
 * Owners: 41S0 wrote the skeleton (wave 41-G0); 41D the politics, 41D-P war, raids and hunters. Wars and raids never
 * change a block and never kill a named character ("Terluka"); every player-facing event goes through
 * {@code dev.kultivator.util.Sibuk} and {@code Sibuk.enemyTaken(player, "gabung")}.
 * <p>
 * Runtime of 41D: one server tick handler. Every second at tick 13 one sect gets its daily politics once per world day
 * (O(sects), 41.12.3): {@code DiplomasiGabung.harian} (class, war record, tier fall), the ranking, the treasury graph and
 * the master's share, the heir election, the succession vote and the master's duty, branch tribute and candidates, guest
 * duties, the absorbed mountain; the same second ages 41D's duels and beats the succession ceremony. Once a minute the
 * online masters are checked for presence. Also: rumours ({@link #kabar}), members' messages ({@link #kabarAnggota}), the
 * course of a player master ({@link #arah}), the Politik screen's ops ({@link #aksi}), the disciple wards of 41.8.7
 * ({@link #daftarkanMurid}), the test subs {@code uji gabung sebaran|kelas|merosot|bangkit|kursi|pewaris|suksesi|ketua|
 * tamu|cabang|serap|lebur|pisah|peringkat|kas} (41D-P adds {@code perang}, {@code serbuan}, {@code pemburu}) and the player
 * subs {@code tantang|politik|sumbang|suara|pewaris|suksesi|
 * cabang|tamu|ketua}.
 */
public final class Politik {

	/** The course a player master sets (41.8.5 "Arah sekte"); same ids as {@code GabungLembar.Politik.ARAH_*}. */
	public static final String ARAH_NETRAL = GabungLembar.Politik.ARAH_NETRAL;
	public static final String ARAH_DAMAI = GabungLembar.Politik.ARAH_DAMAI;
	public static final String ARAH_AGRESIF = GabungLembar.Politik.ARAH_AGRESIF;
	/** Daily chance (percent) that an aggressive master's sect picks a war on its own (still once per 30 days). */
	public static final int AGRESIF_PERSEN = 6;

	private static final String KEY = "kultivator.gabung.politik.";

	private static boolean initialised;
	private static int cursor;

	/** Runtime wiring of module 41D/41D-P. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DiplomasiGabung.init();
		Kursi.init();
		Suksesi.init();
		Cabang.init();
		// F41-1: R2 [Ajukan Cabang] of a dormant hall goes to the branch request
		dev.kultivator.sekte.gabung.Keluar.setPenanganCabang((player, sekte, stele) -> {
			net.minecraft.network.chat.Component why = Cabang.ajukan(player);
			if (why != null) {
				player.sendSystemMessage(why.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			return why == null;
		});
		TetuaTamu.init();
		// 41D-P: War Call, war missions, compound raids and deserter hunters (it wires its own parts).
		PanggilanPerang.init();
		ServerTickEvents.END_SERVER_TICK.register(Politik::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Kursi.clear();
			Suksesi.clear();
			PeringkatSekte.clear();
			cursor = 0;
		});
		daftarUji();
		daftarPerintah();
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		if (!Gabung.aktif()) {
			return;
		}
		int t = server.getTickCount();
		try {
			if (t % 20 == 13) {
				harianBerikut(server);
				Kursi.detik(server);
				Suksesi.detik(server);
			}
			if (t % 1200 == 77) {
				Suksesi.periksaHadir(server);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Gabung politik tick failed", exception);
		}
	}

	/** One sect's daily politics, once per world day per sect. */
	private static void harianBerikut(MinecraftServer server) {
		List<String> sects = DiplomasiGabung.sekte(server);
		if (sects.isEmpty()) {
			return;
		}
		if (cursor >= sects.size()) {
			cursor = 0;
		}
		String sekte = sects.get(cursor++);
		long hari = GabungApi.hari(server);
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		if (b.getLongOr("hariPolitik", -1L) >= hari) {
			return;
		}
		b.putLong("hariPolitik", hari);
		GabungData.get(server).setDirty();
		PeringkatSekte.daftar(server);
		jalan("diplomasi", () -> DiplomasiGabung.harian(server, sekte, hari));
		jalan("kas", () -> KasSekte.harian(server, sekte, hari));
		jalan("pewaris", () -> PemilihanPewaris.harian(server, sekte, hari));
		jalan("suksesi", () -> Suksesi.harian(server, sekte, hari));
		jalan("cabang", () -> Cabang.harian(server, sekte, hari));
		jalan("tamu", () -> TetuaTamu.harian(server, sekte, hari));
		jalan("serap", () -> Penyatuan.harian(server, sekte, hari));
		jalan("arah", () -> arahHarian(server, sekte));
	}

	private static void jalan(String apa, Runnable r) {
		try {
			r.run();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Gabung politik daily {} failed", apa, exception);
		}
	}

	private static void arahHarian(MinecraftServer server, String sekte) {
		if (ARAH_AGRESIF.equals(arah(server, sekte)) && DiplomasiGabung.perang(server, sekte) == null
				&& server.overworld().getRandom().nextInt(100) < AGRESIF_PERSEN) {
			DiplomasiGabung.apply(server, sekte, Peristiwa.PERANG_NPC);
		}
	}

	// ================================================================== messages

	/** Spreads the world rumour {@code templat} (41W's {@code tempat/kabar/gabung_*.json}; ignored when missing). */
	public static void kabar(MinecraftServer server, String templat, Object... isian) {
		if (server == null) {
			return;
		}
		try {
			CeritaApi.kabar(server.overworld(), null, templat, isian);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.debug("Gabung rumour {} skipped", templat, exception);
		}
	}

	/** Tells every online member of {@code sekte}. */
	public static void kabarAnggota(MinecraftServer server, String sekte, Component line, ChatFormatting warna) {
		if (server == null || sekte == null) {
			return;
		}
		for (UUID id : GabungData.get(server).anggotaSekte(sekte)) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				p.sendSystemMessage(line.copy().withStyle(warna));
			}
		}
	}

	// ================================================================== the master's course

	/** The course of a player master ({@code ARAH_*}); {@link #ARAH_NETRAL} for NPC-led sects. */
	public static String arah(MinecraftServer server, String sekte) {
		if (server == null || sekte == null) {
			return ARAH_NETRAL;
		}
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		if (d == null || d.ketuaPemain == null) {
			return ARAH_NETRAL;
		}
		return d.bagian(DiplomasiGabung.BAGIAN).getStringOr("arah", ARAH_NETRAL);
	}

	/** The player master sets the course; null when done. Peaceful: the sect starts no feud; aggressive: it seeks one. */
	public static @Nullable Component setArah(ServerPlayer player, String arah) {
		String sekte = sekteKetua(player);
		if (sekte == null) {
			return Component.translatable(KEY + "bukan_ketua");
		}
		if (!ARAH_NETRAL.equals(arah) && !ARAH_DAMAI.equals(arah) && !ARAH_AGRESIF.equals(arah)) {
			return Component.translatable(KEY + "arah_salah");
		}
		MinecraftServer server = player.level().getServer();
		DiplomasiGabung.bagian(server, sekte).putString("arah", arah);
		GabungData.get(server).setDirty();
		kabarAnggota(server, sekte, Component.translatable(KEY + "arah." + arah, player.getName()), ChatFormatting.YELLOW);
		return null;
	}

	/** The sect {@code player} leads as master, or null. */
	public static @Nullable String sekteKetua(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || k.pangkat != Pangkat.KETUA) {
			return null;
		}
		GabungData.DataSekteGabung d = GabungData.get(player.level().getServer()).peekSekte(sekte);
		return d != null && player.getUUID().equals(d.ketuaPemain) ? sekte : null;
	}

	/** True when the player opted in to Duel Juara against other players during a war (41.9; 41D-P reads it). */
	public static boolean ikutDuel(MinecraftServer server, UUID player) {
		Keanggotaan k = server == null ? null : GabungData.get(server).peek(player);
		return k != null && k.bagian("politik").getBooleanOr("ikutDuel", false);
	}

	// ================================================================== the Politik screen's ops (41.10.8)

	/**
	 * One op of the Politik screen (41U-2's handler passes it through): {@code suara:ID} (heir vote by finalist id, or
	 * {@code suara:ya|tidak} on a succession), {@code usul:ID} (propose an elder to the master), {@code sumbang} (the stack in
	 * hand), {@code tunjuk:JABATAN:ID} (master: {@code TETUA|WAKIL|PEWARIS}), {@code arah:ARAH}, {@code ikut_duel:0|1},
	 * {@code serap}/{@code serap_ya}, {@code lebur}/{@code lebur_ya} (the first asks to confirm). Null when done (or a
	 * confirmation was asked), else why not.
	 */
	public static @Nullable Component aksi(ServerPlayer player, String op) {
		if (op == null || !Gabung.aktif()) {
			return Component.translatable("kultivator.gabung.cmd.mati");
		}
		MinecraftServer server = player.level().getServer();
		String[] p = op.split(":");
		String sekte = GabungApi.sekte(player.getUUID());
		switch (p[0]) {
			case GabungLembar.Politik.OP_SUARA -> {
				if (p.length < 2 || sekte == null) {
					return Component.translatable(KEY + "op_salah");
				}
				if ("ya".equals(p[1]) || "tidak".equals(p[1])) {
					return Suksesi.pilih(player, "ya".equals(p[1]));
				}
				List<String> fin = PemilihanPewaris.finalis(server, sekte);
				return PemilihanPewaris.pilih(player, fin.indexOf(p[1]) + 1);
			}
			case GabungLembar.Politik.OP_USUL -> {
				return usul(player, p.length > 1 ? p[1] : "");
			}
			case GabungLembar.Politik.OP_SUMBANG -> {
				return KasSekte.sumbangTangan(player);
			}
			case GabungLembar.Politik.OP_TUNJUK -> {
				return p.length < 3 ? Component.translatable(KEY + "op_salah") : tunjuk(player, p[1], p[2]);
			}
			case GabungLembar.Politik.OP_ARAH -> {
				return p.length < 2 ? Component.translatable(KEY + "op_salah") : setArah(player, p[1]);
			}
			case GabungLembar.Politik.OP_IKUT_DUEL -> {
				GabungApi.anggota(player).bagian("politik").putBoolean("ikutDuel", p.length > 1 && "1".equals(p[1]));
				GabungData.get(server).setDirty();
				return null;
			}
			case GabungLembar.Politik.OP_SERAP, GabungLembar.Politik.OP_LEBUR -> {
				Component why = Penyatuan.syarat(player);
				if (why != null) {
					return why;
				}
				player.sendSystemMessage(Component.translatable(KEY + "konfirmasi." + p[0]).withStyle(ChatFormatting.GOLD));
				return null;
			}
			case GabungLembar.Politik.OP_SERAP_YA -> {
				return Penyatuan.serap(player);
			}
			case GabungLembar.Politik.OP_LEBUR_YA -> {
				return Penyatuan.lebur(player);
			}
			default -> {
				return Component.translatable(KEY + "op_salah");
			}
		}
	}

	/** A member proposes {@code calon} (a member's UUID) as elder; the master is told. */
	private static @Nullable Component usul(ServerPlayer player, String calon) {
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		UUID id = Kursi.uuid(calon);
		if (sekte == null || id == null || !GabungApi.anggota(id, sekte)) {
			return Component.translatable(KEY + "usul_salah");
		}
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		String list = b.getStringOr("usul", "");
		if (!list.contains(calon)) {
			b.putString("usul", list.isEmpty() ? calon : list + "," + calon);
			GabungData.get(server).setDirty();
		}
		GabungData.DataSekteGabung d = GabungData.get(server).sekte(sekte);
		ServerPlayer ketua = d.ketuaPemain == null ? null : server.getPlayerList().getPlayer(d.ketuaPemain);
		if (ketua != null) {
			ketua.sendSystemMessage(Component.translatable(KEY + "usul", player.getName(), Kursi.namaPemegang(server, calon))
					.withStyle(ChatFormatting.YELLOW));
		}
		player.sendSystemMessage(Component.translatable(KEY + "usul_terkirim").withStyle(ChatFormatting.GREEN));
		return null;
	}

	/**
	 * The player master appoints {@code id} (a member's UUID, or a roster id for the heir) to {@code jabatan}
	 * ({@code TETUA}: a Core player, an NPC elder makes room; {@code WAKIL}: an elder player; {@code PEWARIS}: the next heir).
	 */
	public static @Nullable Component tunjuk(ServerPlayer player, String jabatan, String id) {
		String sekte = sekteKetua(player);
		if (sekte == null) {
			return Component.translatable(KEY + "bukan_ketua");
		}
		MinecraftServer server = player.level().getServer();
		UUID uuid = Kursi.uuid(id);
		ServerPlayer target = uuid == null ? null : server.getPlayerList().getPlayer(uuid);
		Keanggotaan k = uuid == null ? null : GabungData.get(server).peek(uuid);
		switch (jabatan.toUpperCase(java.util.Locale.ROOT)) {
			case "PEWARIS" -> {
				if (uuid != null && (k == null || !k.anggotaDi(sekte) || k.pangkat != Pangkat.INTI)) {
					return Component.translatable(KEY + "tunjuk_pangkat", Pangkat.INTI.nama());
				}
				PemilihanPewaris.tetapkan(server, sekte, id);
			}
			case "TETUA", "WAKIL" -> {
				Pangkat baru = "TETUA".equalsIgnoreCase(jabatan) ? Pangkat.TETUA : Pangkat.WAKIL;
				Pangkat perlu = baru == Pangkat.TETUA ? Pangkat.INTI : Pangkat.TETUA;
				if (target == null || k == null || !k.anggotaDi(sekte) || k.pangkat != perlu) {
					return Component.translatable(KEY + "tunjuk_pangkat", perlu.nama());
				}
				if (!Kursi.bebas(server, sekte, baru)) {
					List<String> holders = Kursi.pemegang(server, sekte, baru);
					String npc = null;
					for (int i = holders.size() - 1; i >= 0 && npc == null; i--) {
						if (Kursi.uuid(holders.get(i)) == null) {
							npc = holders.get(i);
						}
					}
					if (npc == null) {
						return Component.translatable(KEY + "tunjuk_penuh");
					}
					CompoundTag b = DiplomasiGabung.bagian(server, sekte);
					String list = b.getStringOr("tergeser", "");
					b.putString("tergeser", list.isEmpty() ? npc : list + "," + npc);
				}
				GabungApi.setPangkat(target, baru, "gabung.tunjuk");
			}
			default -> {
				return Component.translatable(KEY + "op_salah");
			}
		}
		GabungData.get(server).setDirty();
		kabarAnggota(server, sekte, Component.translatable(KEY + "tunjuk", player.getName(), Kursi.namaPemegang(server, id),
				Component.translatable(KEY + "jabatan." + jabatan.toLowerCase(java.util.Locale.ROOT))), ChatFormatting.GOLD);
		return null;
	}

	// ================================================================== disciple wards (41.8.7)

	/** At most {@code 1 + rank/2} wards (rank ordinal, Luar or higher), never more than 5. */
	public static int titipanMaks(Pangkat pangkat) {
		return pangkat.minimal(Pangkat.LUAR) ? Math.min(Keanggotaan.TITIPAN_MAKS, 1 + pangkat.tingkat() / 2) : 0;
	}

	/**
	 * Registers {@code murid} (one of the player's own disciples, loaded) as a ward of the sect for {@code 50 x kf} PK: free
	 * entry to the compound, the pools, the disciple tournament and lectures; the disciple stays the player's. Null when
	 * done, else why not.
	 */
	public static @Nullable Component daftarkanMurid(ServerPlayer player, DiscipleEntity murid) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || murid == null) {
			return Component.translatable(KEY + "titipan.bukan_anggota");
		}
		if (!Disciples.loaded(player.getUUID()).contains(murid)) {
			return Component.translatable(KEY + "titipan.bukan_murid");
		}
		if (k.muridTitipan.contains(murid.soulId())) {
			return Component.translatable(KEY + "titipan.sudah");
		}
		int maks = titipanMaks(k.pangkat);
		if (k.muridTitipan.size() >= maks) {
			return Component.translatable(KEY + "titipan.penuh", maks);
		}
		int biaya = GabungApi.biaya(sekte, 50);
		if (!GabungApi.bayarPk(player, biaya, "gabung.titipan")) {
			return Component.translatable(KEY + "titipan.pk", biaya);
		}
		k.muridTitipan.add(murid.soulId());
		dev.kultivator.sekte.gabung.hidup.MuridTitipan.didaftar(player, sekte);
		GabungData.get(player.level().getServer()).setDirty();
		player.sendSystemMessage(Component.translatable(KEY + "titipan.terdaftar", murid.getName(), DiplomasiGabung.nama(player.level().getServer(), sekte))
				.withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** Ends the ward status of {@code soul}; false when it was not a ward. */
	public static boolean lepaskanMurid(ServerPlayer player, UUID soul) {
		Keanggotaan k = GabungApi.anggota(player);
		boolean removed = k.muridTitipan.remove(soul);
		if (removed) {
			GabungData.get(player.level().getServer()).setDirty();
		}
		return removed;
	}

	/** The sect a player disciple is a ward of (its master's sect), or null (39 travel, 41C pools read it). */
	public static @Nullable String titipan(MinecraftServer server, UUID master, UUID soul) {
		Keanggotaan k = server == null ? null : GabungData.get(server).peek(master);
		return k != null && k.status.anggota() && k.muridTitipan.contains(soul) ? k.sekte : null;
	}

	// ================================================================== player commands (41.13.2)

	private static final SuggestionProvider<CommandSourceStack> PEMEGANG = (ctx, builder) -> {
		List<String> out = new ArrayList<>();
		ServerPlayer p = ctx.getSource().getPlayer();
		String sekte = p == null ? null : GabungApi.sekte(p.getUUID());
		if (sekte != null) {
			for (Pangkat r : Pangkat.values()) {
				out.addAll(Kursi.pemegang(ctx.getSource().getServer(), sekte, r));
			}
		}
		return SharedSuggestionProvider.suggest(out, builder);
	};

	private static void daftarPerintah() {
		GabungCommands.daftar("tantang", () -> Commands.literal("tantang")
				.executes(ctx -> kursiDaftar(ctx))
				.then(Commands.argument("pemegang", StringArgumentType.word()).suggests(PEMEGANG)
						.executes(ctx -> balas(ctx, Kursi.tantang(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "pemegang"))))));
		GabungCommands.daftar("politik", () -> Commands.literal("politik").executes(Politik::politik));
		GabungCommands.daftar("sumbang", () -> Commands.literal("sumbang")
				.then(Commands.literal("tangan").executes(ctx -> balas(ctx, KasSekte.sumbangTangan(ctx.getSource().getPlayerOrException()))))
				.then(Commands.argument("nb", LongArgumentType.longArg(1L))
						.executes(ctx -> balas(ctx, KasSekte.sumbangNb(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "nb"))))));
		GabungCommands.daftar("suara", () -> Commands.literal("suara")
				.then(Commands.literal("pewaris").then(Commands.argument("nomor", IntegerArgumentType.integer(1, 2))
						.executes(ctx -> balas(ctx, PemilihanPewaris.pilih(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "nomor"))))))
				.then(Commands.literal("suksesi")
						.then(Commands.literal("ya").executes(ctx -> balas(ctx, Suksesi.pilih(ctx.getSource().getPlayerOrException(), true))))
						.then(Commands.literal("tidak").executes(ctx -> balas(ctx, Suksesi.pilih(ctx.getSource().getPlayerOrException(), false))))));
		GabungCommands.daftar("pewaris", () -> Commands.literal("pewaris")
				.then(Commands.literal("daftar").executes(ctx -> balas(ctx, PemilihanPewaris.daftar(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("tanding").executes(ctx -> balas(ctx, PemilihanPewaris.tanding(ctx.getSource().getPlayerOrException())))));
		GabungCommands.daftar("suksesi", () -> Commands.literal("suksesi")
				.executes(ctx -> balas(ctx, Suksesi.lanjut(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("tentang").executes(ctx -> balas(ctx, Suksesi.tentang(ctx.getSource().getPlayerOrException())))));
		GabungCommands.daftar("cabang", () -> Commands.literal("cabang")
				.then(Commands.literal("persembahan").executes(ctx -> balas(ctx, Cabang.persembahan(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("ajukan").executes(ctx -> balas(ctx, Cabang.ajukan(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("merdeka").executes(ctx -> balas(ctx, Cabang.merdeka(ctx.getSource().getPlayerOrException())))));
		GabungCommands.daftar("tamu", () -> Commands.literal("tamu")
				.then(Commands.literal("ujian").then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.executes(ctx -> balas(ctx, TetuaTamu.mulaiUjian(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "sekte"))))))
				.then(Commands.literal("karya").then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.executes(ctx -> balas(ctx, TetuaTamu.persembahan(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "sekte"))))))
				.then(Commands.literal("lepas").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					TetuaTamu.lepas(ctx.getSource().getServer(), p.getUUID(), -10, "sendiri");
					return 1;
				})));
		GabungCommands.daftar("ketua", () -> Commands.literal("ketua")
				.then(Commands.literal("turun")
						.executes(ctx -> balas(ctx, Suksesi.turunTakhta(ctx.getSource().getPlayerOrException(), null)))
						.then(Commands.argument("pengganti", EntityArgument.player())
								.executes(ctx -> balas(ctx, Suksesi.turunTakhta(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "pengganti"))))))
				.then(Commands.literal("arah").then(Commands.argument("arah", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(ARAH_NETRAL, ARAH_DAMAI, ARAH_AGRESIF), b))
						.executes(ctx -> balas(ctx, setArah(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "arah"))))))
				.then(Commands.literal("serap").executes(ctx -> balas(ctx, Penyatuan.serap(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("lebur").executes(ctx -> balas(ctx, Penyatuan.lebur(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("aksi").then(Commands.argument("peristiwa", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("utusan", "hadiah", "pakta", "perang_npc"), b))
						.then(Commands.argument("sasaran", StringArgumentType.word()).suggests(GabungUji.SEKTE)
								.executes(Politik::ketuaAksi)))));
	}

	private static int ketuaAksi(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String sekte = sekteKetua(player);
		if (sekte == null) {
			return balas(ctx, Component.translatable(KEY + "bukan_ketua"));
		}
		Peristiwa p = Peristiwa.byId(StringArgumentType.getString(ctx, "peristiwa"));
		String sasaran = StringArgumentType.getString(ctx, "sasaran");
		if (p == null || !DiplomasiGabung.apply(ctx.getSource().getServer(), sekte, p, sasaran)) {
			return balas(ctx, Component.translatable(KEY + "aksi_gagal"));
		}
		ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "aksi_ok", Component.translatable(KEY + "peristiwa." + p.id()),
				DiplomasiGabung.nama(ctx.getSource().getServer(), sasaran)).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	/** {@code /kultivasi gabung tantang}: the seats of the rank above the player, with their holders. */
	private static int kursiDaftar(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			return balas(ctx, Component.translatable(KEY + "kursi.bukan_anggota"));
		}
		MinecraftServer server = ctx.getSource().getServer();
		for (Pangkat r : Pangkat.values()) {
			if (r.kursi(GabungApi.kelasSekte(sekte)) <= 0 || r.turun() != k.pangkat || r == Pangkat.KETUA) {
				continue;
			}
			MutableComponent line = Component.translatable(KEY + "kursi.daftar", r.nama(), r.kursi(GabungApi.kelasSekte(sekte)));
			for (String h : Kursi.pemegang(server, sekte, r)) {
				line.append(Component.literal(" · ")).append(Kursi.namaPemegang(server, h).copy().withStyle(ChatFormatting.AQUA))
						.append(Component.literal(" [" + h + "]").withStyle(ChatFormatting.DARK_GRAY));
			}
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	/** {@code /kultivasi gabung politik}: the Politik screen when 41U registered it, else a chat summary. */
	private static int politik(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String sekte = GabungApi.sekte(player.getUUID());
		if (sekte == null) {
			return balas(ctx, Component.translatable(KEY + "kursi.bukan_anggota"));
		}
		if (TempatLayar.terdaftar(GabungLembar.Politik.LAYAR) && TempatLayar.bukaDari(player, GabungLembar.Politik.LAYAR, -1)) {
			return 1;
		}
		ringkas(ctx.getSource(), sekte);
		return 1;
	}

	/** The council, war, treasury and branches in chat (the Politik screen's content without the screen). */
	private static void ringkas(CommandSourceStack source, String sekte) {
		MinecraftServer server = source.getServer();
		source.sendSuccess(() -> Component.translatable(KEY + "ringkas.judul", DiplomasiGabung.nama(server, sekte),
				GabungApi.kelasSekte(sekte).nama(), PeringkatSekte.peringkat(server, sekte)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (Pangkat r : new Pangkat[]{Pangkat.KETUA, Pangkat.WAKIL, Pangkat.PEWARIS, Pangkat.TETUA, Pangkat.INTI, Pangkat.TAMU}) {
			List<String> h = Kursi.pemegang(server, sekte, r);
			MutableComponent line = Component.literal("  ").append(r.nama()).append(Component.literal(": "));
			if (h.isEmpty()) {
				line.append(Component.translatable(KEY + "ringkas.kosong").withStyle(ChatFormatting.DARK_GRAY));
			}
			for (int i = 0; i < h.size(); i++) {
				line.append(Component.literal(i == 0 ? "" : ", ")).append(Kursi.namaPemegang(server, h.get(i)));
			}
			source.sendSuccess(() -> line, false);
		}
		source.sendSuccess(() -> Component.translatable(KEY + "ringkas.sumber", DiplomasiGabung.sumber(server, sekte),
				DiplomasiGabung.sumberNaik(server, sekte), Component.translatable(KEY + "arah." + arah(server, sekte) + ".nama"))
				.withStyle(ChatFormatting.GRAY), false);
		DiplomasiGabung.Perang perang = DiplomasiGabung.perang(server, sekte);
		if (perang != null) {
			source.sendSuccess(() -> Component.translatable(KEY + "ringkas.perang", DiplomasiGabung.nama(server, perang.lawan()), perang.skor(),
					perang.sisa(GabungApi.hari(server))).withStyle(ChatFormatting.RED), false);
		}
		Map<UUID, Integer> kas = KasSekte.sumbangan(server, sekte);
		if (!kas.isEmpty()) {
			MutableComponent line = Component.translatable(KEY + "ringkas.kas");
			kas.entrySet().stream().limit(5).forEach(e -> line.append(Component.literal(" · "))
					.append(Kursi.namaPemegang(server, e.getKey().toString())).append(Component.literal(" " + e.getValue())));
			source.sendSuccess(() -> line.withStyle(ChatFormatting.GREEN), false);
		}
		List<UUID> cabang = Cabang.daftar(server, sekte);
		if (!cabang.isEmpty()) {
			MutableComponent line = Component.translatable(KEY + "ringkas.cabang");
			cabang.forEach(o -> line.append(Component.literal(" · ")).append(Kursi.namaPemegang(server, o.toString())));
			source.sendSuccess(() -> line.withStyle(ChatFormatting.AQUA), false);
		}
		if (DiplomasiGabung.merosot(server, sekte)) {
			source.sendSuccess(() -> Component.translatable(KEY + "ringkas.merosot").withStyle(ChatFormatting.DARK_RED), false);
		}
	}

	private static int balas(CommandContext<CommandSourceStack> ctx, @Nullable Component why) {
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	// ================================================================== test commands (41.13.3)

	private static final SuggestionProvider<CommandSourceStack> SEBARAN = (ctx, builder) -> SharedSuggestionProvider.suggest(GabungConfig.SEBARAN, builder);
	private static final SuggestionProvider<CommandSourceStack> PANGKAT = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		for (Pangkat p : Pangkat.values()) {
			ids.add(p.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static void daftarUji() {
		GabungUji.chips("gabung sebaran", "gabung peringkat", "gabung kursi", "gabung pewaris mulai", "gabung suksesi",
				"gabung ketua", "gabung tamu", "gabung cabang", "gabung serap", "gabung merosot", "gabung bangkit", "gabung kas");
		GabungApi.daftarUji("sebaran", () -> Commands.literal("sebaran")
				.executes(ctx -> sebaran(ctx.getSource(), GabungConfig.get().sebaran))
				.then(Commands.argument("preset", StringArgumentType.word()).suggests(SEBARAN)
						.executes(ctx -> sebaran(ctx.getSource(), StringArgumentType.getString(ctx, "preset")))));
		GabungApi.daftarUji("kelas", () -> Commands.literal("kelas")
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE)
						.then(Commands.argument("tier", IntegerArgumentType.integer(1, 9)).executes(ctx -> {
							String s = StringArgumentType.getString(ctx, "sekte");
							DiplomasiGabung.setTier(ctx.getSource().getServer(), s, IntegerArgumentType.getInteger(ctx, "tier"));
							ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "uji.kelas", DiplomasiGabung.nama(ctx.getSource().getServer(), s),
									GabungApi.tier(s), GabungApi.kelasSekte(s).nama()), true);
							return 1;
						}))));
		GabungApi.daftarUji("merosot", () -> Commands.literal("merosot").executes(ctx -> {
			DiplomasiGabung.setMerosot(ctx.getSource().getServer(), sekteUji(ctx), true);
			return 1;
		}));
		GabungApi.daftarUji("bangkit", () -> Commands.literal("bangkit").executes(ctx -> {
			String s = sekteUji(ctx);
			MinecraftServer server = ctx.getSource().getServer();
			GabungData.get(server).sekte(s).bangkitHari = -1L;
			boolean ok = DiplomasiGabung.bangkit(server, s, ctx.getSource().getPlayer());
			ctx.getSource().sendSuccess(() -> Component.translatable(KEY + (ok ? "uji.bangkit" : "uji.bangkit_tidak")), true);
			return ok ? 1 : 0;
		}));
		GabungApi.daftarUji("peringkat", () -> Commands.literal("peringkat").executes(ctx -> {
			MinecraftServer server = ctx.getSource().getServer();
			PeringkatSekte.hitung(server, GabungApi.hari(server));
			int i = 1;
			for (PeringkatSekte.Baris b : PeringkatSekte.daftar(server)) {
				int n = i++;
				ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "uji.peringkat", n, DiplomasiGabung.nama(server, b.sekte()), b.sekte(),
						b.kekuatan(), b.tier(), KelasSekte.byNomor(b.kelas()).nama()), false);
			}
			return 1;
		}));
		GabungApi.daftarUji("kursi", () -> Commands.literal("kursi")
				.then(Commands.literal("pulih").executes(ctx -> {
					Kursi.pulih(ctx.getSource().getServer(), sekteUji(ctx));
					return 1;
				}))
				.then(Commands.argument("pangkat", StringArgumentType.word()).suggests(PANGKAT).executes(ctx -> {
					String s = sekteUji(ctx);
					MinecraftServer server = ctx.getSource().getServer();
					Pangkat p = pangkat(StringArgumentType.getString(ctx, "pangkat"));
					List<String> h = Kursi.pemegang(server, s, p);
					ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "uji.kursi", p.nama(), h.size(), p.kursi(GabungApi.kelasSekte(s))), false);
					for (String id : h) {
						ctx.getSource().sendSuccess(() -> Component.literal("  " + id + " ").append(Kursi.namaPemegang(server, id)), false);
					}
					return 1;
				})));
		GabungApi.daftarUji("pewaris", () -> Commands.literal("pewaris")
				.then(Commands.literal("mulai").executes(ctx -> {
					PemilihanPewaris.mulai(ctx.getSource().getServer(), sekteUji(ctx));
					return 1;
				}))
				.then(Commands.literal("putus").executes(ctx -> {
					String s = sekteUji(ctx);
					MinecraftServer server = ctx.getSource().getServer();
					PemilihanPewaris.harian(server, s, GabungApi.hari(server) + PemilihanPewaris.JENDELA_HARI + 2);
					PemilihanPewaris.putus(server, s);
					return 1;
				})));
		GabungApi.daftarUji("suksesi", () -> Commands.literal("suksesi")
				.executes(ctx -> balas(ctx, Suksesi.lanjut(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("sidang").executes(ctx -> {
					Suksesi.tutup(ctx.getSource().getServer(), sekteUji(ctx));
					return 1;
				}))
				.then(Commands.literal("bab").then(Commands.argument("bab", IntegerArgumentType.integer(0, 3)).executes(ctx -> {
					Suksesi.paksaBab(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "bab"));
					return 1;
				}))));
		GabungApi.daftarUji("ketua", () -> Commands.literal("ketua").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			Suksesi.jadikanKetua(p, sekteUji(ctx), false);
			return 1;
		}));
		GabungApi.daftarUji("tamu", () -> Commands.literal("tamu")
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(GabungUji.SEKTE).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					TetuaTamu.terima(p, StringArgumentType.getString(ctx, "sekte"), "uji");
					return 1;
				})));
		GabungApi.daftarUji("cabang", () -> Commands.literal("cabang").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			Cabang.jadikan(ctx.getSource().getServer(), p.getUUID(), sekteUji(ctx), false);
			return 1;
		}));
		GabungApi.daftarUji("serap", () -> Commands.literal("serap").executes(ctx -> balas(ctx, Penyatuan.serap(ctx.getSource().getPlayerOrException()))));
		GabungApi.daftarUji("lebur", () -> Commands.literal("lebur").executes(ctx -> balas(ctx, Penyatuan.lebur(ctx.getSource().getPlayerOrException()))));
		GabungApi.daftarUji("pisah", () -> Commands.literal("pisah").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			Penyatuan.pisah(ctx.getSource().getServer(), sekteUji(ctx), p.getUUID());
			return 1;
		}));
		GabungApi.daftarUji("kas", () -> Commands.literal("kas").executes(ctx -> {
			String s = sekteUji(ctx);
			MinecraftServer server = ctx.getSource().getServer();
			int[] g = KasSekte.grafik(server, s);
			ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "uji.kas", DiplomasiGabung.sumber(server, s), KasSekte.kasNb(server, s),
					KasSekte.kasKetua(server, s), java.util.Arrays.toString(g)), false);
			return 1;
		}));
	}

	/** The tester's own sect (member), else the nearest sect's plan entry. */
	private static String sekteUji(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		String s = GabungApi.sekte(p.getUUID());
		if (s != null) {
			return s;
		}
		String near = SekteApi.diplomasi().nearest(p.blockPosition(), -1);
		if (near == null) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable(KEY + "uji.tanpa_sekte")).create();
		}
		return near;
	}

	private static Pangkat pangkat(String id) {
		for (Pangkat p : Pangkat.values()) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return Pangkat.INTI;
	}

	/**
	 * {@code uji gabung sebaran [preset]}: the start tier every plan entry gets with {@code preset} on this seed (rank,
	 * plan tier, preset tier, class) and the proof that ranks 0 and 1 are the sects of {@code LangitNpcPlan.of(seed)}.
	 */
	private static int sebaran(CommandSourceStack source, String preset) {
		MinecraftServer server = source.getServer();
		long seed = server.overworld().getSeed();
		GerbangPlan.Plan plan = GerbangPlan.of(seed);
		List<GerbangPlan.Entry> order = KelasSekte.urutan(plan);
		source.sendSuccess(() -> Component.translatable(KEY + "uji.sebaran.judul", preset, order.size()).withStyle(ChatFormatting.GOLD), false);
		for (int r = 0; r < order.size(); r++) {
			GerbangPlan.Entry e = order.get(r);
			int t = DiplomasiGabung.tingkatPreset(plan, e, preset, GabungConfig.get().tingkatDatar);
			int rank = r;
			source.sendSuccess(() -> Component.translatable(KEY + "uji.sebaran.baris", rank, e.id(), e.name(), e.tier(), t,
					KelasSekte.dariTier(t).nama(), SekteApi.diplomasi().tier(e.id())), false);
		}
		List<LangitNpcPlan.Istana> istana = LangitNpcPlan.of(seed);
		boolean sama = true;
		for (int i = 0; i < istana.size(); i++) {
			sama &= i < order.size() && istana.get(i).gate().index() == order.get(i).index();
		}
		boolean ok = sama;
		source.sendSuccess(() -> Component.translatable(KEY + (istana.isEmpty() ? "uji.sebaran.tanpa_istana" : ok ? "uji.sebaran.sama" : "uji.sebaran.beda"),
				istana.size()).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		return ok ? 1 : 0;
	}

	private Politik() {
	}
}
