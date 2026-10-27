package dev.kultivator.sekte.gabung.politik;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungCommands;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pemburu Sekte (DESIGN 41.7.3, module 41D-P): the squads a sect sends after a deserter (status {@code PEMBELOT}).
 * <p>
 * For {@code gabungSekte.pemburuHari} (30) world days after the desertion, at most once every {@link #JEDA_HARI} days,
 * outside safe ground ({@code DuniaApi.safeGround}), in the overworld, and only through the one busy door and the shared
 * enemy pressure ({@code Sibuk.why}, {@code Sibuk.enemyRefusal}, {@code Sibuk.enemyTaken(player, "gabung")}): a squad of
 * 2-4 of the sect's people appears around the player (D's sect bodies, rank about the player's own, members of the 39F
 * roster where it has them), led by the Tetua Hukum when the sect is tier 5 or more. They speak first; the player answers
 * in chat within 30 s:
 * <ul>
 * <li><b>Menyerah</b>: the "fine of repentance" {@code 50 x (band + 1)} NB, the stolen Teknik Warisan wiped (K3,
 * {@code hapusWarisanTertangkap}), and {@code DIUSIR} replaces {@code PEMBELOT} (the hunt ends).</li>
 * <li><b>Lawan</b>: the hunters step up one by one for a Duel Pedang (35) that stops at 10 % health; beating them all counts a
 * victory, and after {@link #MENANG_BERHENTI} victories the sect gives up ("too strong to chase"; still {@code PEMBELOT}).
 * Losing is being caught: carried to the sect's Gua Renungan for 10 real minutes, then as Menyerah.</li>
 * <li><b>Lari</b>: 40 s to put {@link #LARI_JARAK} blocks between oneself and the squad; caught up, it is Lawan.</li>
 * </ul>
 * Silence for 30 s means Lawan. Beaten hunters are wounded ({@code Roster.lukai}), never killed. Pardon (41.7.5): while
 * {@code PEMBELOT}, a player whose realm is two above the sect master's may receive an envoy of peace (15 % every 10 days);
 * accepting turns the status into {@code DIUSIR} and ends the hunt.
 */
public final class PemburuSekte {

	/** World days between two squads. */
	public static final int JEDA_HARI = 5;
	/** Squads beaten before the sect gives up. */
	public static final int MENANG_BERHENTI = 3;
	public static final int REGU_MIN = 2;
	public static final int REGU_MAKS = 4;
	/** Ticks to answer the squad. */
	public static final int PILIH_TICKS = 600;
	/** Ticks of a flight attempt and the distance that ends it. */
	public static final int LARI_TICKS = 800;
	public static final double LARI_JARAK = 48.0D;
	/** Confinement after being caught (10 real minutes). */
	public static final int KURUNGAN_TICKS = 12000;
	/** One chance in this many per 10 s scan once a squad may come (about once in 15 minutes outside). */
	public static final int PELUANG = 90;
	/** Fine of repentance per band step (NB). */
	public static final int DENDA_DASAR = 50;
	/** Pardon envoy: days between rolls and the chance. */
	public static final int UTUSAN_HARI = 10;
	public static final double UTUSAN_PELUANG = 0.15D;

	static final String PEMBURU_HARI = "pemburuHari";
	static final String REGU_TAMBAH = "reguTambah";
	static final String UTUSAN_HARI_KEY = "utusanHari";
	static final String UTUSAN = "utusan";
	static final String KURUNG_SEKTE = "kurungSekte";
	/** The Teknik Warisan a deserter carries away (41A's Keluar copies {@code Keanggotaan.warisan} here before leaving). */
	public static final String WARISAN_CURI = "warisanCuri";

	enum Tahap { DIALOG, DUEL, LARI }

	/** One squad on one player (session state). */
	static final class Buru {
		final UUID pemain;
		final String sekte;
		final ServerLevel level;
		final List<DiscipleEntity> regu = new ArrayList<>();
		final List<String> roster = new ArrayList<>();
		Tahap tahap = Tahap.DIALOG;
		long sampai;
		Vec3 pusat;
		int giliran;
		long duelPada = -1L;
		long duelMulai;
		@Nullable DiscipleEntity lawanDuel;
		boolean bertarung;
		boolean uji;

		Buru(UUID pemain, String sekte, ServerLevel level, Vec3 pusat) {
			this.pemain = pemain;
			this.sekte = sekte;
			this.level = level;
			this.pusat = pusat;
		}

		@Nullable DiscipleEntity pemimpin() {
			return regu.isEmpty() ? null : regu.get(regu.size() - 1);
		}

		void bubar(long delay) {
			for (DiscipleEntity body : List.copyOf(regu)) {
				if (delay <= 0L) {
					DiplomasiMandiri.hilang(body);
				} else {
					Voice.later((int) delay, () -> DiplomasiMandiri.hilang(body));
				}
			}
		}
	}

	/** A caught deserter in the Gua Renungan (session state; the saved {@code kurunganSampai} survives a restart). */
	record Kurung(UUID pemain, String sekte, BlockPos gua, long sampai) {
	}

	private static final Map<UUID, Buru> BURU = new HashMap<>();
	private static final Map<UUID, Kurung> KURUNG = new HashMap<>();
	private static boolean initialised;

	/** From {@link PanggilanPerang#init()}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(PemburuSekte::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Buru b : BURU.values()) {
				b.bubar(0L);
			}
			BURU.clear();
			KURUNG.clear();
		});
		DuelEndListeners.add(PemburuSekte::duelUsai);
		Keanggotaan.addPenyentuh(PemburuSekte::sentuh);
		ServerPlayerEvents.JOIN.register(PemburuSekte::pulihkanKurung);
		GabungBus.KELUAR.register((player, sekte, status) -> {
			if (status == StatusKeluar.PEMBELOT) {
				MinecraftServer server = player.level().getServer();
				GabungData data = GabungData.get(server);
				Keanggotaan k = data.anggota(player.getUUID());
				// the first squad may come from the next day on, then every JEDA_HARI days
				k.bagian(PanggilanPerang.BAGIAN).putLong(PEMBURU_HARI, GabungApi.hari(server) - JEDA_HARI + 1);
				data.setDirty();
			}
		});
		GabungCommands.daftar("pemburu", PemburuSekte::nodePemain);
		GabungApi.daftarUji("pemburu", PemburuSekte::nodeUji);
	}

	// ================================================================== reads and hooks

	/** The sect hunting {@code k} on day {@code hari} (its latest desertion with the hunt window open), or null. */
	public static @Nullable String sekteBuru(Keanggotaan k, long hari) {
		if (!GabungConfig.get().pemburu || k == null || k.pemburuSampai <= hari || k.pemburuMenang >= MENANG_BERHENTI) {
			return null;
		}
		String best = null;
		long bestDay = Long.MIN_VALUE;
		for (Map.Entry<String, Keanggotaan.Riwayat> e : k.riwayatSekte.entrySet()) {
			if (e.getValue().status() == StatusKeluar.PEMBELOT && e.getValue().hari() > bestDay && GabungApi.tier(e.getKey()) > 0) {
				bestDay = e.getValue().hari();
				best = e.getKey();
			}
		}
		return best;
	}

	/** 41.7.4: a deserter taught the stolen Warisan; the next squads bring one more hunter (at most +2). */
	public static void tambahRegu(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		CompoundTag mine = data.anggota(player.getUUID()).bagian(PanggilanPerang.BAGIAN);
		mine.putInt(REGU_TAMBAH, Math.min(2, mine.getIntOr(REGU_TAMBAH, 0) + 1));
		data.setDirty();
	}

	/** True while a squad faces {@code player}. */
	public static boolean diburu(ServerPlayer player) {
		return BURU.containsKey(player.getUUID());
	}

	/** The fine of repentance for {@code player}: {@code 50 x (band + 1)} NB, band = realm 0-8. */
	public static long denda(ServerPlayer player) {
		int pita = Mth.clamp(Cultivators.get(player).realm().ordinal(), 0, 8);
		return (long) DENDA_DASAR * (pita + 1);
	}

	// ================================================================== the scan

	private static void tick(MinecraftServer server) {
		if (!Gabung.aktif()) {
			return;
		}
		int t = server.getTickCount();
		if (t % 200 == 113 && GabungConfig.get().pemburu) {
			scan(server);
		}
		if (t % 10 == 7 && !BURU.isEmpty()) {
			for (Buru b : List.copyOf(BURU.values())) {
				try {
					detik(server, b);
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.error("Gabung hunters on {} failed", b.pemain, exception);
					akhiri(server, b, null);
				}
			}
		}
		if (t % 20 == 13 && !KURUNG.isEmpty()) {
			for (Kurung k : List.copyOf(KURUNG.values())) {
				jagaKurung(server, k);
			}
		}
	}

	private static void scan(MinecraftServer server) {
		GabungData data = GabungData.get(server);
		long hari = GabungApi.hari(server);
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (BURU.containsKey(player.getUUID()) || KURUNG.containsKey(player.getUUID())) {
				continue;
			}
			Keanggotaan k = data.peek(player.getUUID());
			String sekte = k == null ? null : sekteBuru(k, hari);
			if (sekte == null) {
				continue;
			}
			CompoundTag mine = k.bagian(PanggilanPerang.BAGIAN);
			if (hari - mine.getLongOr(PEMBURU_HARI, hari - JEDA_HARI) < JEDA_HARI) {
				continue;
			}
			if (!bolehMuncul(player) || player.getRandom().nextInt(PELUANG) != 0) {
				continue;
			}
			muncul(player, sekte, false);
		}
	}

	/** Overworld, on open ground, not safe ground, not flying, swimming or riding, free and under the enemy cap. */
	private static boolean bolehMuncul(ServerPlayer player) {
		if (player.level().dimension() != Level.OVERWORLD || player.isSpectator() || player.isCreative() || !player.isAlive()) {
			return false;
		}
		if (player.getAbilities().flying || player.isInWater() || player.isPassenger() || !player.onGround()) {
			return false;
		}
		if (DuniaApi.safeGround(player.level(), player.blockPosition())) {
			return false;
		}
		return Sibuk.why(player) == null && Sibuk.enemyRefusal(player) == null;
	}

	/** A squad appears around {@code player}; false when no body could be placed. */
	static boolean muncul(ServerPlayer player, String sekte, boolean uji) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		CompoundTag mine = k.bagian(PanggilanPerang.BAGIAN);
		int tier = Math.max(1, GabungApi.tier(sekte));
		int n = Mth.clamp(REGU_MIN + (tier >= 4 ? 1 : 0) + (tier >= 7 ? 1 : 0) + mine.getIntOr(REGU_TAMBAH, 0), REGU_MIN, REGU_MAKS);
		int step = Cultivators.get(player).rank().step();
		RandomSource random = player.getRandom();
		long hari = GabungApi.hari(server);
		List<Roster.Tokoh> pool = new ArrayList<>();
		for (Roster.Tokoh t : Roster.daftar(server, sekte)) {
			if (t.aktif() && !t.luka(hari) && ("dalam".equals(t.peran()) || "inti".equals(t.peran()) || "kakak".equals(t.peran()))) {
				pool.add(t);
			}
		}
		Buru b = new Buru(player.getUUID(), sekte, level, player.position());
		b.uji = uji;
		boolean tetua = tier >= 5;
		TokohSekte.Tokoh hukum = tetua ? TokohSekte.jabatan(server, sekte, Jabatan.TETUA_HUKUM) : null;
		float yaw = player.getYRot();
		for (int i = 0; i < n; i++) {
			if (!DuniaBudget.mayAdd(level, GabungEntities.ANGGARAN)) {
				break;
			}
			boolean lead = i == n - 1;
			double a = Math.toRadians(yaw + 90.0D) + (i - (n - 1) / 2.0D) * 0.55D;
			double r = 8.0D + random.nextDouble() * 3.0D;
			double x = player.getX() + Math.cos(a) * r;
			double z = player.getZ() + Math.sin(a) * r;
			BlockPos ground = BlockPos.containing(x, player.getY(), z);
			if (!level.isPositionEntityTicking(ground)) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(MisiPerang.tanah(level, ground.getX(), ground.getZ()));
			Component name;
			int s;
			String rosterId = "";
			if (lead && tetua) {
				name = hukum != null ? Component.literal(hukum.nama())
						: Component.translatable("kultivator.gabung.pemburu.tetua_hukum", PanggilanPerang.nama(sekte));
				s = step + 6;
			} else if (!pool.isEmpty()) {
				Roster.Tokoh t = pool.remove(random.nextInt(pool.size()));
				name = t.nama();
				s = Math.max(step - 3, Math.min(step + 3, t.rank().step()));
				rosterId = t.id();
			} else {
				name = Component.translatable("kultivator.gabung.pemburu.nama", PanggilanPerang.nama(sekte));
				s = step + random.nextInt(7) - 3;
			}
			DiscipleEntity body = DiplomasiMandiri.tubuh(level, at, sekte, Math.max(1, s), 40 + i, name, BlockPos.containing(at), lead);
			if (body == null) {
				continue;
			}
			DuniaBudget.track(GabungEntities.ANGGARAN, body);
			TechniquePower.markRealmFoe(body);
			SosokApi.tatap(body, player);
			// blocks the way, fists on the hips, until the player has chosen (pilih releases it)
			SosokApi.gerak(body, GerakId.SIKAP_BERKACAK, 0, 0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.8D, at.z, 10, 0.3D, 0.5D, 0.3D, 0.02D);
			b.regu.add(body);
			b.roster.add(rosterId);
		}
		if (b.regu.isEmpty()) {
			return false;
		}
		// the weakest step up first, the leader last
		BURU.put(player.getUUID(), b);
		b.sampai = level.getGameTime() + PILIH_TICKS;
		mine.putLong(PEMBURU_HARI, hari);
		data.setDirty();
		GabungApi.pemburuHidup(player, true);
		if (!uji) {
			Sibuk.enemyTaken(player, "gabung");
		}
		DiscipleEntity lead = b.pemimpin();
		if (lead != null) {
			SosokApi.gerak(lead, tetua ? GabungGerakId.TETUA_MENUNJUK_ATURAN : GerakId.MARAH_TUNJUK, 0, 60);
			Voice.say(lead, "gabung.pemburu", null);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.4F, 1.4F);
		Msg.title(player, Component.translatable("kultivator.gabung.pemburu.judul").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable("kultivator.gabung.pemburu.sub", PanggilanPerang.nama(sekte)).withStyle(ChatFormatting.RED), 10, 60, 20);
		Component leadName = lead == null ? PanggilanPerang.nama(sekte) : lead.getName();
		player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.seru", leadName, PanggilanPerang.nama(sekte))
				.withStyle(ChatFormatting.RED));
		player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.tawar", b.regu.size(), NilaiBatu.text(denda(player)))
				.withStyle(ChatFormatting.GRAY));
		MutableComponent pilihan = Component.literal("  ")
				.append(PanggilanPerang.tombol("kultivator.gabung.pemburu.tombol.menyerah", "/kultivasi gabung pemburu menyerah",
						"kultivator.gabung.pemburu.tombol.menyerah.tip", ChatFormatting.YELLOW))
				.append("  ")
				.append(PanggilanPerang.tombol("kultivator.gabung.pemburu.tombol.lawan", "/kultivasi gabung pemburu lawan",
						"kultivator.gabung.pemburu.tombol.lawan.tip", ChatFormatting.RED))
				.append("  ")
				.append(PanggilanPerang.tombol("kultivator.gabung.pemburu.tombol.lari", "/kultivasi gabung pemburu lari",
						"kultivator.gabung.pemburu.tombol.lari.tip", ChatFormatting.AQUA));
		player.sendSystemMessage(pilihan);
		return true;
	}

	// ================================================================== the choice

	/** The player's answer ({@code menyerah}, {@code lawan}, {@code lari}); null when taken, else why not. */
	static @Nullable Component pilih(ServerPlayer player, String pilihan) {
		Buru b = BURU.get(player.getUUID());
		if (b == null) {
			return Component.translatable("kultivator.gabung.pemburu.alasan.tidak_ada");
		}
		if (b.tahap != Tahap.DIALOG && !("lawan".equals(pilihan) && b.tahap == Tahap.LARI)) {
			return Component.translatable("kultivator.gabung.pemburu.alasan.sudah");
		}
		MinecraftServer server = player.level().getServer();
		switch (pilihan) {
			case "menyerah" -> {
				DiscipleEntity lead = b.pemimpin();
				if (lead != null) {
					SosokApi.gerak(lead, GerakId.TUNJUK, 0, 40);
				}
				player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.menyerah.terima",
						lead == null ? PanggilanPerang.nama(b.sekte) : lead.getName()).withStyle(ChatFormatting.GRAY));
				akhiri(server, b, null);
				serah(player, b.sekte, true);
			}
			case "lawan" -> {
				b.tahap = Tahap.DUEL;
				b.duelPada = b.level.getGameTime() + 40L;
				for (DiscipleEntity body : b.regu) {
					SosokApi.henti(body);
				}
				player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.lawan").withStyle(ChatFormatting.RED));
			}
			case "lari" -> {
				b.tahap = Tahap.LARI;
				b.sampai = b.level.getGameTime() + LARI_TICKS;
				b.pusat = player.position();
				player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.lari", (int) LARI_JARAK, LARI_TICKS / 20)
						.withStyle(ChatFormatting.AQUA));
				for (DiscipleEntity body : b.regu) {
					SosokApi.henti(body);
					SosokApi.gerak(body, GerakId.MARAH_TUNJUK, 0, 30);
				}
			}
			default -> {
				return Component.translatable("kultivator.gabung.pemburu.alasan.pilihan");
			}
		}
		return null;
	}

	private static void detik(MinecraftServer server, Buru b) {
		ServerPlayer player = server.getPlayerList().getPlayer(b.pemain);
		if (player == null || !player.isAlive() || player.level() != b.level) {
			akhiri(server, b, null);
			return;
		}
		b.regu.removeIf(body -> body.isRemoved());
		if (b.regu.isEmpty() && b.tahap != Tahap.DUEL) {
			akhiri(server, b, null);
			return;
		}
		long now = b.level.getGameTime();
		switch (b.tahap) {
			case DIALOG -> {
				for (DiscipleEntity body : b.regu) {
					SosokApi.tatap(body, player);
				}
				if (now >= b.sampai) {
					player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.diam").withStyle(ChatFormatting.RED));
					pilih(player, "lawan");
				}
			}
			case LARI -> {
				double d2 = player.position().distanceToSqr(b.pusat);
				if (d2 > LARI_JARAK * LARI_JARAK) {
					player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.lolos").withStyle(ChatFormatting.AQUA));
					akhiri(server, b, null);
					return;
				}
				if (now % 20L < 10L) {
					for (DiscipleEntity body : b.regu) {
						body.getNavigation().moveTo(player, 1.35D);
					}
					Msg.actionBar(player, Component.translatable("kultivator.gabung.pemburu.lari.bar", (int) Math.sqrt(d2), (int) LARI_JARAK,
							(int) Math.max(0L, (b.sampai - now) / 20L)).withStyle(ChatFormatting.AQUA));
				}
				if (now >= b.sampai) {
					player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.terkejar").withStyle(ChatFormatting.RED));
					b.tahap = Tahap.DIALOG;
					pilih(player, "lawan");
				}
			}
			case DUEL -> {
				if (b.lawanDuel == null && b.duelPada > 0L && now >= b.duelPada) {
					b.duelPada = -1L;
					DiscipleEntity next = null;
					while (b.giliran < b.regu.size()) {
						DiscipleEntity c = b.regu.get(b.giliran);
						if (!c.isRemoved()) {
							next = c;
							break;
						}
						b.giliran++;
					}
					if (next == null) {
						menang(server, player, b);
						return;
					}
					int gap = Cultivators.get(player).realm().ordinal() - next.data().realm().ordinal();
					DuelRuleSet rules = DuelRuleSet.suggested(gap).withoutStake().withCrowd(false).withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN);
					Component why = DuelChallenges.startDirect(player, player, next, DuelIds.PAIR_NPC, rules);
					if (why != null) {
						player.sendSystemMessage(why.copy().withStyle(ChatFormatting.GRAY));
						player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.mundur").withStyle(ChatFormatting.GRAY));
						akhiri(server, b, null);
						return;
					}
					b.lawanDuel = next;
					b.bertarung = true;
					b.duelMulai = now;
					player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.maju", next.getName(), b.giliran + 1, b.regu.size())
							.withStyle(ChatFormatting.RED));
				}
				if (b.lawanDuel != null && DuelSessions.byEntity(b.lawanDuel) == null && b.bertarung && now - b.duelMulai > 200L) {
					// the session vanished without a result (cancelled): the squad withdraws
					akhiri(server, b, null);
				}
			}
		}
	}

	private static void duelUsai(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (Buru b : List.copyOf(BURU.values())) {
			DiscipleEntity body = b.lawanDuel;
			if (body != null && session.involves(body)) {
				// handled on the next tick: the session is still settling while listeners run (no teleport inside it)
				b.lawanDuel = null;
				Voice.later(1, () -> hasilDuel(b, body, winner));
				return;
			}
		}
	}

	private static void hasilDuel(Buru b, DiscipleEntity body, @Nullable LivingEntity winner) {
		if (BURU.get(b.pemain) != b) {
			return;
		}
		MinecraftServer server = b.level.getServer();
		ServerPlayer player = server.getPlayerList().getPlayer(b.pemain);
		if (player == null || winner == null) {
			akhiri(server, b, null);
			return;
		}
		if (winner == player) {
			int i = b.regu.indexOf(body);
			String rosterId = i >= 0 && i < b.roster.size() ? b.roster.get(i) : "";
			if (!rosterId.isEmpty()) {
				Roster.lukai(server, rosterId, 5);
			}
			SosokApi.gerak(body, GerakId.SIKAP_TUNDUK, 0, 40);
			Voice.later(40, () -> DiplomasiMandiri.hilang(body));
			b.giliran++;
			b.duelPada = b.level.getGameTime() + 80L;
			player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.tumbang", body.getName()).withStyle(ChatFormatting.GOLD));
		} else {
			tertangkap(server, player, b);
		}
	}

	/** Every hunter beaten: one victory counted; three and the sect gives up. */
	private static void menang(MinecraftServer server, ServerPlayer player, Buru b) {
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		if (!b.uji) {
			k.pemburuMenang++;
		}
		boolean berhenti = k.pemburuMenang >= MENANG_BERHENTI;
		if (berhenti) {
			k.pemburuSampai = 0L;
		}
		k.catat(GabungApi.hari(server), "kultivator.gabung.riwayat.pemburu_menang", b.sekte, Integer.toString(k.pemburuMenang));
		data.setDirty();
		player.sendSystemMessage(Component.translatable(berhenti ? "kultivator.gabung.pemburu.berhenti" : "kultivator.gabung.pemburu.menang",
				PanggilanPerang.nama(b.sekte), k.pemburuMenang, MENANG_BERHENTI).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.8F);
		akhiri(server, b, null);
	}

	/** Lost a hunter's duel: carried to the Gua Renungan for 10 minutes, then as Menyerah. */
	private static void tertangkap(MinecraftServer server, ServerPlayer player, Buru b) {
		DiscipleEntity lead = b.pemimpin();
		player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.tertangkap", lead == null ? PanggilanPerang.nama(b.sekte) : lead.getName())
				.withStyle(ChatFormatting.DARK_RED));
		akhiri(server, b, null);
		BlockPos gua = GabungApi.titik(b.sekte, TitikGabung.GUA_RENUNGAN);
		if (gua == null) {
			gua = GabungApi.titik(b.sekte, TitikGabung.HUKUM);
		}
		if (gua == null || b.uji) {
			serah(player, b.sekte, true);
			return;
		}
		ServerLevel over = server.overworld();
		player.teleportTo(over, gua.getX() + 0.5D, gua.getY(), gua.getZ() + 0.5D, Set.of(), player.getYRot(), 0.0F, true);
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		long sampai = over.getGameTime() + KURUNGAN_TICKS;
		k.kurunganSampai = sampai;
		k.bagian(PanggilanPerang.BAGIAN).putString(KURUNG_SEKTE, b.sekte);
		data.setDirty();
		KURUNG.put(player.getUUID(), new Kurung(player.getUUID(), b.sekte, gua, sampai));
		Msg.title(player, Component.translatable("kultivator.gabung.pemburu.kurung.judul").withStyle(ChatFormatting.DARK_RED),
				Component.translatable("kultivator.gabung.pemburu.kurung.sub", PanggilanPerang.nama(b.sekte), KURUNGAN_TICKS / 1200).withStyle(ChatFormatting.GRAY),
				10, 80, 20);
		GabungPayloads.kirimKlip(player, GabungGerakId.RENUNGAN, 0);
		Voice.inner(player, "gabung.hukum_kurung", PanggilanPerang.nama(b.sekte));
	}

	/** Keeps a caught deserter in the cave; at the end, the Menyerah terms. */
	private static void jagaKurung(MinecraftServer server, Kurung k) {
		ServerPlayer player = server.getPlayerList().getPlayer(k.pemain());
		if (player == null) {
			KURUNG.remove(k.pemain());
			return;
		}
		ServerLevel over = server.overworld();
		if (over.getGameTime() >= k.sampai()) {
			KURUNG.remove(k.pemain());
			lepasKurung(player, k.sekte());
			return;
		}
		if (player.level() != over || player.position().distanceToSqr(Vec3.atBottomCenterOf(k.gua())) > 49.0D) {
			player.teleportTo(over, k.gua().getX() + 0.5D, k.gua().getY(), k.gua().getZ() + 0.5D, Set.of(), player.getYRot(), 0.0F, true);
			Msg.actionBar(player, Component.translatable("kultivator.gabung.pemburu.kurung.tarik").withStyle(ChatFormatting.RED));
		} else if (server.getTickCount() % 400 == 13) {
			Msg.actionBar(player, Component.translatable("kultivator.gabung.pemburu.kurung.sisa", (int) ((k.sampai() - over.getGameTime()) / 20L))
					.withStyle(ChatFormatting.GRAY));
		}
	}

	private static void lepasKurung(ServerPlayer player, String sekte) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		k.kurunganSampai = 0L;
		k.bagian(PanggilanPerang.BAGIAN).remove(KURUNG_SEKTE);
		data.setDirty();
		GabungPayloads.kirimKlip(player, 0, 0);
		player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.kurung.bebas", PanggilanPerang.nama(sekte)).withStyle(ChatFormatting.GRAY));
		serah(player, sekte, true);
	}

	/**
	 * The terms of surrender (Menyerah, or after the cave): the fine of repentance (what the player carries, up to it), the
	 * stolen Teknik Warisan wiped (K3), {@code DIUSIR} instead of {@code PEMBELOT}, the hunt over.
	 */
	static void serah(ServerPlayer player, String sekte, boolean denda) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		long hari = GabungApi.hari(server);
		if (denda) {
			long fine = denda(player);
			long bayar = Math.min(fine, NilaiBatu.total(player));
			if (bayar > 0L && NilaiBatu.pay(player, bayar)) {
				player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.denda", NilaiBatu.text(bayar)).withStyle(ChatFormatting.GRAY));
			}
		}
		CompoundTag mine = k.bagian(PanggilanPerang.BAGIAN);
		String warisan = mine.getStringOr(WARISAN_CURI, k.warisan);
		if (GabungConfig.get().hapusWarisanTertangkap && warisan != null && !warisan.isEmpty()) {
			CultivationData cd = Cultivators.get(player);
			if (cd.learnedIds().contains(warisan)) {
				cd.forget(warisan);
				Cultivators.dirty(player);
				player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.warisan").withStyle(ChatFormatting.DARK_PURPLE));
			}
			mine.remove(WARISAN_CURI);
			if (warisan.equals(k.warisan)) {
				k.warisan = "";
			}
		}
		k.riwayatSekte.put(sekte, new Keanggotaan.Riwayat(StatusKeluar.DIUSIR, hari, 0));
		k.pemburuSampai = 0L;
		k.pemburuMenang = 0;
		k.catat(hari, "kultivator.gabung.riwayat.pemburu_serah", sekte);
		data.setDirty();
		GabungPayloads.kirimKlip(player, GabungGerakId.DIUSIR_TUNDUK, 0);
		GabungPayloads.lepasKlipNanti(player, 80);
		player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.serah", PanggilanPerang.nama(sekte)).withStyle(ChatFormatting.YELLOW));
	}

	/** Ends an encounter: the squad leaves in a puff of cloud, the enemy mark goes. */
	private static void akhiri(MinecraftServer server, Buru b, @Nullable String pesan) {
		BURU.remove(b.pemain);
		ServerPlayer player = server.getPlayerList().getPlayer(b.pemain);
		// a squad leaving mid-duel takes its duellist along: the duel then ends as cancelled (no result)
		b.lawanDuel = null;
		b.bubar(30L);
		if (player != null) {
			GabungApi.pemburuHidup(player, false);
			if (pesan != null) {
				player.sendSystemMessage(Component.translatable(pesan).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	// ================================================================== lazy upkeep: confinement after a restart, the pardon envoy

	/** A caught deserter who logged out in the cave goes back into it (or out, when the time passed). */
	private static void pulihkanKurung(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = server == null || !Gabung.aktif() ? null : GabungData.get(server).peek(player.getUUID());
		if (k == null) {
			return;
		}
		String kurung = k.bagian(PanggilanPerang.BAGIAN).getStringOr(KURUNG_SEKTE, "");
		if (kurung.isEmpty() || KURUNG.containsKey(player.getUUID())) {
			return;
		}
		long now = server.overworld().getGameTime();
		BlockPos gua = GabungApi.titik(kurung, TitikGabung.GUA_RENUNGAN);
		if (gua == null) {
			gua = GabungApi.titik(kurung, TitikGabung.HUKUM);
		}
		if (k.kurunganSampai > now && gua != null) {
			KURUNG.put(player.getUUID(), new Kurung(player.getUUID(), kurung, gua, k.kurunganSampai));
		} else {
			lepasKurung(player, kurung);
		}
	}

	private static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		CompoundTag mine = k.bagian(PanggilanPerang.BAGIAN);
		pulihkanKurung(player);
		String sekte = null;
		long day = Long.MIN_VALUE;
		for (Map.Entry<String, Keanggotaan.Riwayat> e : k.riwayatSekte.entrySet()) {
			if (e.getValue().status() == StatusKeluar.PEMBELOT && e.getValue().hari() > day) {
				day = e.getValue().hari();
				sekte = e.getKey();
			}
		}
		if (sekte == null || GabungApi.tier(sekte) <= 0) {
			return;
		}
		if (hari - mine.getLongOr(UTUSAN_HARI_KEY, day) < UTUSAN_HARI) {
			return;
		}
		int ranah = Cultivators.get(player).realm().ordinal();
		if (ranah < KelasSekte.ranahKetua(GabungApi.tier(sekte)).ordinal() + 2) {
			return;
		}
		mine.putLong(UTUSAN_HARI_KEY, hari);
		RandomSource random = RandomSource.create(player.getUUID().getMostSignificantBits() ^ hari * 2654435761L);
		if (random.nextDouble() >= UTUSAN_PELUANG) {
			return;
		}
		mine.putString(UTUSAN, sekte);
		player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.utusan", PanggilanPerang.nama(sekte)).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.literal("  ")
				.append(PanggilanPerang.tombol("kultivator.gabung.pemburu.tombol.damai", "/kultivasi gabung pemburu damai terima",
						"kultivator.gabung.pemburu.tombol.damai.tip", ChatFormatting.GREEN))
				.append("  ")
				.append(PanggilanPerang.tombol("kultivator.gabung.pemburu.tombol.tolak", "/kultivasi gabung pemburu damai tolak",
						"kultivator.gabung.pemburu.tombol.tolak.tip", ChatFormatting.GRAY)));
	}

	private static int damai(CommandContext<CommandSourceStack> ctx, boolean terima) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		CompoundTag mine = k.bagian(PanggilanPerang.BAGIAN);
		String sekte = mine.getStringOr(UTUSAN, "");
		if (sekte.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.pemburu.alasan.utusan"));
			return 0;
		}
		mine.remove(UTUSAN);
		if (terima) {
			long hari = GabungApi.hari(server);
			k.riwayatSekte.put(sekte, new Keanggotaan.Riwayat(StatusKeluar.DIUSIR, hari, 0));
			k.pemburuSampai = 0L;
			k.catat(hari, "kultivator.gabung.riwayat.pemburu_ampun", sekte);
			player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.ampun", PanggilanPerang.nama(sekte)).withStyle(ChatFormatting.GREEN));
		} else {
			player.sendSystemMessage(Component.translatable("kultivator.gabung.pemburu.tolak", PanggilanPerang.nama(sekte)).withStyle(ChatFormatting.GRAY));
		}
		data.setDirty();
		return 1;
	}

	// ================================================================== commands

	/** {@code /kultivasi gabung pemburu menyerah|lawan|lari}, {@code ... pemburu damai terima|tolak}. */
	private static LiteralArgumentBuilder<CommandSourceStack> nodePemain() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pemburu");
		for (String p : List.of("menyerah", "lawan", "lari")) {
			root.then(Commands.literal(p).executes(ctx -> {
				Component why = pilih(ctx.getSource().getPlayerOrException(), p);
				if (why != null) {
					ctx.getSource().sendFailure(why);
					return 0;
				}
				return 1;
			}));
		}
		root.then(Commands.literal("damai")
				.then(Commands.literal("terima").executes(ctx -> damai(ctx, true)))
				.then(Commands.literal("tolak").executes(ctx -> damai(ctx, false))));
		return root;
	}

	/** {@code uji gabung pemburu}: a squad now beside the tester (its hunting sect, else its own, else the first sect). */
	private static LiteralArgumentBuilder<CommandSourceStack> nodeUji() {
		return Commands.literal("pemburu").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			MinecraftServer server = ctx.getSource().getServer();
			if (BURU.containsKey(player.getUUID())) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.pemburu.alasan.sudah"));
				return 0;
			}
			Keanggotaan k = GabungData.get(server).anggota(player.getUUID());
			String sekte = sekteBuru(k, GabungApi.hari(server));
			if (sekte == null) {
				sekte = GabungApi.sekte(player.getUUID());
			}
			if (sekte == null && !SekteApi.diplomasi().sects().isEmpty()) {
				sekte = SekteApi.diplomasi().sects().getFirst();
			}
			if (sekte == null || !muncul(player, sekte, true)) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.pemburu.alasan.tempat"));
				return 0;
			}
			return 1;
		});
	}

	private PemburuSekte() {
	}
}
