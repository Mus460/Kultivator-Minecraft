package dev.kultivator.sekte.gabung.hidup;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.alam.Alam;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.musuh.Ambushes;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungCommands;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungUji;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module 41C "Hidup Anggota" (DESIGN 41.6.2-41.6.9, 41.6.14): life as a member of an NPC sect. The sect mission board
 * ({@link PapanMisiSekte}, kinds in {@link JenisMisiGabung}), daily duties ({@link TugasHarian}), the Paviliun Jasa
 * ({@link PaviliunJasa}, stock in {@link StokPaviliun}), the weekly allowance ({@link Jatah}), the secret-realm quota
 * ({@link KuotaAlam}) and the protection of the sect's name ({@link NamaSekte}); 41C-K's dwellings, spirit pools,
 * tournament and lessons are wired by {@link SitusGabung#init()}. Entry of the package {@code sekte.gabung.hidup}, called
 * by {@code Gabung.init()} while 41 is on (this package registers no registry content).
 * <p>
 * Runtime wiring here: the lazy daily upkeep ({@code Keanggotaan.addPenyentuh}), one server tick handler (chores every
 * 2 ticks, missions and the visible daily life once a second only for members on line, the quota day of one sect per
 * second in turn), the death/damage/feed events, the 41U screen ops ({@code GabungLayar.daftarAksi}), the services in
 * the dialog of the sect's own people ({@code CeritaApi.daftarJasa}), the ambush factor ({@code Ambushes}), the realm
 * clear listener ({@code Alam}), the player commands {@code /kultivasi gabung papan|tugas|jatah|paviliun|lapor} and the
 * test subs {@code uji gabung misi|tugas|jatah|kuota|paviliun|pesanan|nama}. No tick per member: everything updates
 * lazily per world day or when the player interacts (41.12.3).
 * <p>
 * 41.6.14 "Hidup sehari-hari yang terlihat": inside the own compound the sect's disciples greet a member by rank
 * ({@code gabung.sapa_adik} / {@code gabung.sapa_kakak}, captioned until the voice exists, at most once a minute) and
 * the lower-ranked bow ({@code salam_sekte} 304) when a member of Inti and above passes (at most once per 10 s).
 */
public final class HidupAnggota {

	private static boolean initialised;
	private static int giliran;
	/** player -> game tick of the last bow / greeting near them. */
	private static final Map<UUID, Long> SALAM = new HashMap<>();
	private static final Map<UUID, Long> SAPA = new HashMap<>();
	public static final int SALAM_JEDA = 200;
	public static final int SAPA_JEDA = 1200;

	/** Runtime wiring of module 41C/41C-K. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// 41C-K: dwellings, spirit pools, tournament, lessons, the SpiritSites provider and their commands
		SitusGabung.init();
		PapanMisiSekte.daftarTujuanQuest();
		Keanggotaan.addPenyentuh(HidupAnggota::sentuh);
		GabungBus.DITERIMA.register((player, sekte, pangkat) -> {
			if (pangkat == Pangkat.PELAYAN || pangkat == Pangkat.TERCATAT) {
				CeritaApi.mulaiQuest(player, TugasHarian.QUEST_PERTAMA, Math.max(0L, TugasHarian.situs(player, sekte)));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(HidupAnggota::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			PapanMisiSekte.reset();
			TugasHarian.reset();
			SALAM.clear();
			SAPA.clear();
			giliran = 0;
		});
		ServerPlayerEvents.JOIN.register(HidupAnggota::masuk);
		ServerPlayerEvents.LEAVE.register(player -> {
			TugasHarian.berhenti(player, "kultivator.gabung.tugas.henti.pergi");
			SALAM.remove(player.getUUID());
			SAPA.remove(player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			PapanMisiSekte.onMati(victim, source);
			TugasHarian.onMati(victim);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F) {
				NamaSekte.diserang(entity, source);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && GabungApi.aktif()) {
				TugasHarian.beriMakan(sp, entity);
			}
			return InteractionResult.PASS;
		});
		Ambushes.addChanceFactor(NamaSekte::faktorSergap);
		Alam.addClearListener(KuotaAlam::onClear);
		CeritaApi.daftarJasa(HidupAnggota::jasa);
		daftarAksi();
		daftarPerintah();
		daftarUji();
	}

	// ================================================================== upkeep and tick

	private static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		if (k.sekteAktif() == null) {
			return;
		}
		PapanMisiSekte.sentuh(player, k, hari);
		TugasHarian.sentuh(player, k, hari);
		Jatah.sentuh(player, k, hari);
		if ((k.pangkat == Pangkat.PELAYAN || k.pangkat == Pangkat.TERCATAT) && k.tugasTotal == 0 && k.hariMasuk >= 0L && hari > k.hariMasuk) {
			CeritaApi.mulaiQuest(player, TugasHarian.QUEST_PERTAMA, Math.max(0L, TugasHarian.situs(player, k.sekte)));
		}
	}

	private static void masuk(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null || !GabungApi.aktif()) {
			return;
		}
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k != null) {
			KuotaAlam.kirimTertunda(player, k);
			data.setDirty();
		}
	}

	private static void tick(MinecraftServer server) {
		if (!GabungApi.aktif()) {
			return;
		}
		long tick = server.overworld().getGameTime();
		if ((tick & 1L) == 0L) {
			TugasHarian.tick(server, tick);
		}
		if (tick % 20L != 0L) {
			return;
		}
		long hari = Kota.day(server);
		GabungData data = GabungData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Keanggotaan k = data.peek(player.getUUID());
			if (k == null || k.sekteAktif() == null) {
				continue;
			}
			PapanMisiSekte.detik(player, k, hari, tick);
			if ((tick / 20L) % 2L == 0L) {
				hidupTerlihat(player, k, tick);
			}
		}
		// the daily pass of 41C: one sect per second in turn (the quota day; the board is drawn lazily when viewed)
		List<String> sects = SekteApi.diplomasi().sects();
		if (!sects.isEmpty()) {
			giliran = Math.floorMod(giliran + 1, sects.size());
			KuotaAlam.bagi(server, sects.get(giliran));
		}
	}

	/** One day of the sect for the daily pass of 41A ({@code GabungHarian} may call it; idempotent per day). */
	public static void harian(MinecraftServer server, String sekte) {
		KuotaAlam.bagi(server, sekte);
	}

	// ================================================================== 41.6.14 the visible daily life

	private static void hidupTerlihat(ServerPlayer player, Keanggotaan k, long tick) {
		if (TugasHarian.situs(player, k.sekte) < 0L) {
			return;
		}
		int mine = k.pangkat.tingkat();
		AABB box = player.getBoundingBox().inflate(6.0D, 2.0D, 6.0D);
		boolean bow = mine >= Pangkat.INTI.tingkat() && tick - SALAM.getOrDefault(player.getUUID(), -SALAM_JEDA * 2L) >= SALAM_JEDA;
		boolean greet = tick - SAPA.getOrDefault(player.getUUID(), -SAPA_JEDA * 2L) >= SAPA_JEDA;
		if (!bow && !greet) {
			return;
		}
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, box, Entity::isAlive)) {
			int theirs = tingkatPeran(npc.role());
			if (theirs < 0) {
				continue;
			}
			if (bow && theirs < mine) {
				HidupApi.tatap(npc, player);
				HidupApi.klip(npc, GabungGerakId.SALAM_SEKTE, 0, 40);
				SALAM.put(player.getUUID(), tick);
				bow = false;
			}
			if (greet && theirs != mine && npc.role() != NpcRole.TETUA_SEKTE) {
				sapa(npc, player, theirs > mine);
				SAPA.put(player.getUUID(), tick);
				greet = false;
			}
			if (!bow && !greet) {
				break;
			}
		}
	}

	/** The rank height of a sect body (Pelayan 0, Luar 2, Dalam 3, Tetua 6), -1 for anyone else. */
	static int tingkatPeran(NpcRole role) {
		return switch (role) {
			case PELAYAN_SEKTE -> Pangkat.PELAYAN.tingkat();
			case MURID_SEKTE_LUAR -> Pangkat.LUAR.tingkat();
			case MURID_SEKTE_DALAM -> Pangkat.DALAM.tingkat();
			case TETUA_SEKTE -> Pangkat.TETUA.tingkat();
			default -> -1;
		};
	}

	/** A senior greets a junior ("Adik seperguruan!") or a junior a senior ("Kakak senior!"). */
	private static void sapa(KultivatorNpc npc, ServerPlayer player, boolean seniorKeJunior) {
		String key = seniorKeJunior ? "gabung.sapa_adik" : "gabung.sapa_kakak";
		String caption = "kultivator.gabung.hidup.sapa." + (seniorKeJunior ? "adik" : "kakak");
		HidupApi.tatap(npc, player);
		if (VoiceEvents.exists(key)) {
			Voice.say(npc, key, caption);
		} else {
			Msg.actionBar(player, Component.empty().append(npc.getDisplayName()).append(": ").append(Component.translatable(caption))
					.withStyle(ChatFormatting.GRAY));
		}
	}

	// ================================================================== services in the dialog of the sect's people

	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		if (!GabungApi.aktif() || tingkatPeran(npc.role()) < 0 && npc.role() != NpcRole.PENJAGA_GERBANG) {
			return List.of();
		}
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			return List.of();
		}
		boolean milik = TugasHarian.situs(player, sekte) >= 0L;
		if (!milik) {
			var gate = JenisMisiGabung.pusat(sekte);
			milik = gate != null && npc.distanceToSqr(gate.getX() + 0.5D, npc.getY(), gate.getZ() + 0.5D) <= 96.0D * 96.0D;
		}
		if (!milik) {
			return List.of();
		}
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		if (TempatLayar.terdaftar(GabungLembar.Paviliun.LAYAR)) {
			out.add(new TempatDialog.JasaEntri(GabungLembar.Paviliun.LAYAR, Component.translatable("kultivator.gabung.paviliun.judul"),
					LembarKunci.Ikon.PK, Component.translatable("kultivator.gabung.jasa.pk", k.pk), false, null));
		}
		if (TempatLayar.terdaftar(GabungLembar.Papan.LAYAR)) {
			long hari = Kota.day(player.level().getServer());
			out.add(new TempatDialog.JasaEntri(GabungLembar.Papan.LAYAR, Component.translatable("kultivator.gabung.misi.papan"),
					LembarKunci.Ikon.GILDA_MISI, Component.translatable("kultivator.gabung.jasa.misi", PapanMisiSekte.diambilHariIni(k, hari),
					PapanMisiSekte.batasHarian(k.pangkat)), false, null));
		}
		return out;
	}

	// ================================================================== 41U screen ops

	private static void daftarAksi() {
		String pav = GabungLembar.Paviliun.LAYAR;
		GabungLayar.daftarAksi(pav, GabungLembar.Paviliun.OP_TUKAR, (p, npc, op) -> hasil(p,
				PaviliunJasa.tukar(p, sekte(p), LembarKunci.arg(op, 1), LembarKunci.argInt(op, 2, 1))));
		GabungLayar.daftarAksi(pav, GabungLembar.Paviliun.OP_SERAHKAN, (p, npc, op) -> hasil(p,
				PaviliunJasa.serahkan(p, sekte(p), LembarKunci.arg(op, 1))));
		GabungLayar.daftarAksi(pav, GabungLembar.Paviliun.OP_CERAMAH, (p, npc, op) -> hasil(p,
				PaviliunJasa.ceramah(p, sekte(p), LembarKunci.arg(op, 1))));
		// "sumbang" (donate the main-hand stack) is not in the frozen sheet contract yet; the op works once 41U-2 draws a button
		GabungLayar.daftarAksi(pav, "sumbang", (p, npc, op) -> hasil(p, PaviliunJasa.sumbang(p)));
		String papan = GabungLembar.Papan.LAYAR;
		GabungLayar.daftarAksi(papan, GabungLembar.Papan.OP_TERIMA, (p, npc, op) -> hasilOk(p,
				PapanMisiSekte.terima(p, LembarKunci.arg(op, 1)), "kultivator.gabung.misi.ok_terima"));
		GabungLayar.daftarAksi(papan, GabungLembar.Papan.OP_GABUNG, (p, npc, op) -> hasilOk(p,
				PapanMisiSekte.gabung(p, LembarKunci.arg(op, 1)), "kultivator.gabung.misi.ok_gabung"));
		GabungLayar.daftarAksi(papan, GabungLembar.Papan.OP_BATAL, (p, npc, op) -> hasilOk(p,
				PapanMisiSekte.batal(p, LembarKunci.arg(op, 1)), "kultivator.gabung.misi.ok_batal"));
		GabungLayar.daftarAksi(papan, GabungLembar.Papan.OP_TUGAS, (p, npc, op) -> hasilOk(p,
				TugasHarian.mulai(p, LembarKunci.arg(op, 1)), "kultivator.gabung.tugas.ok_mulai"));
		GabungLayar.daftarAksi(GabungLembar.Kartu.LAYAR, GabungLembar.Kartu.OP_JATAH, (p, npc, op) -> hasil(p, Jatah.ambil(p)));
	}

	private static @Nullable String sekte(ServerPlayer player) {
		return GabungApi.anggota(player).sekteAktif();
	}

	/** A refused action: the reason as the sheet's warning line (a success already wrote its own line). */
	private static void hasil(ServerPlayer player, @Nullable Component alasan) {
		if (alasan != null) {
			GabungLayar.pesan(player, alasan, LembarKunci.PESAN_PERINGATAN);
		}
	}

	private static void hasilOk(ServerPlayer player, @Nullable Component alasan, String okKey) {
		if (alasan != null) {
			GabungLayar.pesan(player, alasan, LembarKunci.PESAN_PERINGATAN);
		} else {
			GabungLayar.pesan(player, Component.translatable(okKey), LembarKunci.PESAN_OK);
		}
	}

	// ================================================================== player commands (41.13.2: the same actions as the buttons)

	private static void daftarPerintah() {
		GabungCommands.daftar("papan", () -> Commands.literal("papan")
				.executes(ctx -> chat(ctx, PapanMisiSekte::tulisChat))
				.then(Commands.literal("terima").then(Commands.argument("id", StringArgumentType.word())
						.executes(ctx -> balas(ctx, PapanMisiSekte.terima(pemain(ctx), StringArgumentType.getString(ctx, "id"))))))
				.then(Commands.literal("gabung").then(Commands.argument("id", StringArgumentType.word())
						.executes(ctx -> balas(ctx, PapanMisiSekte.gabung(pemain(ctx), StringArgumentType.getString(ctx, "id"))))))
				.then(Commands.literal("batal").then(Commands.argument("id", StringArgumentType.word())
						.executes(ctx -> balas(ctx, PapanMisiSekte.batal(pemain(ctx), StringArgumentType.getString(ctx, "id")))))));
		GabungCommands.daftar("tugas", () -> Commands.literal("tugas")
				.executes(ctx -> chat(ctx, TugasHarian::tulisChat))
				.then(Commands.literal("pilih").then(Commands.argument("n", IntegerArgumentType.integer(0, 9)).executes(ctx -> {
					TugasHarian.pilih(pemain(ctx), IntegerArgumentType.getInteger(ctx, "n"));
					return 1;
				})))
				.then(Commands.literal("henti").executes(ctx -> {
					TugasHarian.berhenti(pemain(ctx), "kultivator.gabung.tugas.henti.sendiri");
					return 1;
				}))
				.then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, b) -> {
					for (TugasHarian.Tugas t : TugasHarian.Tugas.values()) {
						b.suggest(t.id());
					}
					return b.buildFuture();
				}).executes(ctx -> balas(ctx, TugasHarian.mulai(pemain(ctx), StringArgumentType.getString(ctx, "id"))))));
		GabungCommands.daftar("jatah", () -> Commands.literal("jatah").executes(ctx -> balas(ctx, Jatah.ambil(pemain(ctx)))));
		GabungCommands.daftar("sumbang", () -> Commands.literal("sumbang").executes(ctx -> balas(ctx, PaviliunJasa.sumbang(pemain(ctx)))));
		GabungCommands.daftar("lapor", () -> Commands.literal("lapor").executes(ctx -> balas(ctx, KuotaAlam.lapor(pemain(ctx)))));
		GabungCommands.daftar("paviliun", () -> Commands.literal("paviliun")
				.executes(ctx -> chat(ctx, p -> PaviliunJasa.tulisChat(p, null)))
				.then(Commands.literal("tukar").then(Commands.argument("id", StringArgumentType.word())
						.then(Commands.argument("n", IntegerArgumentType.integer(1, PaviliunJasa.TUKAR_MAKS)).executes(ctx -> {
							ServerPlayer p = pemain(ctx);
							return balas(ctx, PaviliunJasa.tukar(p, sekte(p), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "n")));
						}))))
				.then(Commands.literal("serahkan").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
					ServerPlayer p = pemain(ctx);
					return balas(ctx, PaviliunJasa.serahkan(p, sekte(p), StringArgumentType.getString(ctx, "id")));
				})))
				.then(Commands.argument("tab", StringArgumentType.word()).suggests((ctx, b) -> {
					GabungLembar.Paviliun.TABS.forEach(b::suggest);
					return b.buildFuture();
				}).executes(ctx -> chat(ctx, p -> PaviliunJasa.tulisChat(p, StringArgumentType.getString(ctx, "tab"))))));
	}

	private static ServerPlayer pemain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	@FunctionalInterface
	private interface Tulis {
		void tulis(ServerPlayer player);
	}

	private static int chat(CommandContext<CommandSourceStack> ctx, Tulis tulis) throws CommandSyntaxException {
		if (!GabungApi.aktif()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.cmd.mati"));
			return 0;
		}
		tulis.tulis(pemain(ctx));
		return 1;
	}

	/** Null = done (the action wrote its own lines); else the reason as a failure. */
	private static int balas(CommandContext<CommandSourceStack> ctx, @Nullable Component alasan) {
		if (alasan != null) {
			ctx.getSource().sendFailure(alasan);
			return 0;
		}
		return 1;
	}

	// ================================================================== test subs (41.13.3, op 2)

	private static void daftarUji() {
		GabungUji.chips("gabung paviliun [tab]", "gabung pesanan", "gabung nama [penjaga]", "gabung lapor");
		GabungUji.daftar("misi", () -> Commands.literal("misi")
				.executes(ctx -> chat(ctx, PapanMisiSekte::tulisChat))
				.then(Commands.literal("isi").executes(ctx -> {
					ServerPlayer p = pemain(ctx);
					String s = sekte(p);
					if (s == null) {
						return balas(ctx, Component.translatable("kultivator.gabung.hidup.bukan_anggota"));
					}
					GabungData.get(ctx.getSource().getServer()).sekte(s).papanHari = -1L;
					PapanMisiSekte.tulisChat(p);
					return 1;
				}))
				.then(Commands.literal("jenis").executes(ctx -> {
					// every kind drawn once at its lowest rank: the target, the goal and the waypoints it would get in this world
					ServerPlayer p = pemain(ctx);
					String s = sekte(p);
					if (s == null) {
						return balas(ctx, Component.translatable("kultivator.gabung.hidup.bukan_anggota"));
					}
					MinecraftServer server = ctx.getSource().getServer();
					int n = 0;
					for (JenisMisiGabung.Jenis j : JenisMisiGabung.semua()) {
						var m = JenisMisiGabung.susun(server, s, Kota.day(server), 90 + n++, j, j.misi().pangkatMin(), net.minecraft.util.RandomSource.create(n * 31L));
						Component line = m == null ? Component.translatable("kultivator.gabung.uji.jenis_tidak", j.judul())
								: Component.translatable("kultivator.gabung.uji.jenis_baris", j.judul(), JenisMisiGabung.huruf(j.misi().pangkatMin()),
										m.getIntOr(JenisMisiGabung.X, 0) + ", " + m.getIntOr(JenisMisiGabung.Z, 0), JenisMisiGabung.tujuanBaris(m),
										m.getIntArray(JenisMisiGabung.TITIK).map(a -> a.length / 2).orElse(0));
						ctx.getSource().sendSuccess(() -> line, false);
					}
					return 1;
				}))
				.then(Commands.literal("selesai").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
					ServerPlayer p = pemain(ctx);
					Keanggotaan k = GabungApi.anggota(p);
					var m = PapanMisiSekte.misiAktif(k).getCompoundOrEmpty(StringArgumentType.getString(ctx, "id"));
					if (m.isEmpty()) {
						return balas(ctx, Component.translatable("kultivator.gabung.misi.alasan.hilang"));
					}
					PapanMisiSekte.selesai(p, k, m);
					return 1;
				}))));
		GabungUji.daftar("tugas", () -> Commands.literal("tugas")
				.then(Commands.literal("reset").executes(ctx -> {
					Keanggotaan k = GabungApi.anggota(pemain(ctx));
					k.tugasHari = -1L;
					k.tugasJumlah = 0;
					k.bagian(PapanMisiSekte.BAGIAN).remove(TugasHarian.K_SELESAI_HARI);
					GabungData.get(ctx.getSource().getServer()).setDirty();
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.tugas_reset"), false);
					return 1;
				}))
				.then(Commands.argument("id", StringArgumentType.word()).suggests((ctx, b) -> {
					for (TugasHarian.Tugas t : TugasHarian.Tugas.values()) {
						b.suggest(t.id());
					}
					return b.buildFuture();
				}).executes(ctx -> balas(ctx, TugasHarian.mulai(pemain(ctx), StringArgumentType.getString(ctx, "id"))))));
		GabungUji.daftar("jatah", () -> Commands.literal("jatah").executes(ctx -> {
			ServerPlayer p = pemain(ctx);
			Keanggotaan k = GabungApi.anggota(p);
			k.jatahHari = Kota.day(ctx.getSource().getServer()) - Jatah.HARI;
			return balas(ctx, Jatah.ambil(p, true));
		}));
		GabungUji.daftar("kuota", () -> Commands.literal("kuota").then(Commands.literal("bagi").executes(ctx -> {
			ServerPlayer p = pemain(ctx);
			String s = sekte(p);
			if (s == null) {
				return balas(ctx, Component.translatable("kultivator.gabung.hidup.bukan_anggota"));
			}
			GabungData.get(ctx.getSource().getServer()).sekte(s).kuotaHari = -1L;
			int n = KuotaAlam.bagiSekarang(ctx.getSource().getServer(), s);
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.kuota", n), false);
			return 1;
		})));
		GabungUji.daftar("paviliun", () -> Commands.literal("paviliun")
				.executes(ctx -> chat(ctx, p -> PaviliunJasa.tulisChat(p, null)))
				.then(Commands.argument("tab", StringArgumentType.word()).suggests((ctx, b) -> {
					GabungLembar.Paviliun.TABS.forEach(b::suggest);
					return b.buildFuture();
				}).executes(ctx -> chat(ctx, p -> PaviliunJasa.tulisChat(p, StringArgumentType.getString(ctx, "tab"))))));
		GabungUji.daftar("pesanan", () -> Commands.literal("pesanan")
				.executes(ctx -> chat(ctx, p -> PaviliunJasa.tulisChat(p, GabungLembar.Paviliun.TAB_PESANAN))));
		GabungUji.daftar("nama", () -> namaUji());
		GabungUji.daftar("lapor", () -> Commands.literal("lapor").executes(ctx -> {
			ServerPlayer p = pemain(ctx);
			Keanggotaan k = GabungApi.anggota(p);
			KuotaAlam.catatTerima(k, Kota.day(ctx.getSource().getServer()));
			KuotaAlam.onClear(p, dev.kultivator.alam.RealmSpecs.BLOOD_FIRE, 0, false);
			return 1;
		}));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> namaUji() {
		return Commands.literal("nama").executes(ctx -> {
			ServerPlayer p = pemain(ctx);
			double f = NamaSekte.faktorSergap(p);
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.nama", String.format(java.util.Locale.ROOT, "%.2f", f)), false);
			return 1;
		}).then(Commands.literal("penjaga").executes(ctx -> {
			ServerPlayer p = pemain(ctx);
			String s = sekte(p);
			if (s == null) {
				return balas(ctx, Component.translatable("kultivator.gabung.hidup.bukan_anggota"));
			}
			int n = NamaSekte.panggil(p, s, GabungApi.kelasSekte(s), null);
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.uji.penjaga", n), false);
			return 1;
		}));
	}

	private HidupAnggota() {
	}
}
