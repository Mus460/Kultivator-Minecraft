package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.PenimpaJadwalKota;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.bicara.PermintaanWarga;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.ekonomi.UsahaWarga;
import dev.kultivator.dunia.kota42.hewan.AyamKotaEntity;
import dev.kultivator.dunia.kota42.hewan.HewanKota;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.GerobakEntity;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Random street events (DESIGN 42.7.4, contract C42.D1): {@code copet} (a pickpocket on a player, spirit stones only, K2),
 * {@code copet_warga} (a pickpocket on a resident), {@code perkelahian} / {@code tawuran} (a brawl, nobody dies),
 * {@code gerobak} (an overturned cart, {@code GerobakEntity} TERGULING), {@code api} (a small fire entity with a bucket
 * chain), {@code ayam} (loose chickens, villages only) and {@code anak_tersesat} (a lost child).
 * <p>
 * The scheduler checks every {@value #SELANG} ticks each active town ({@code Pemanggil.semua()}, overworld, residents
 * raised by 42A) with a player inside: 2-4 events per town per world day ({@code kotaAcara.peristiwaPerHari} +-1 by hash),
 * at most {@value #MAKS_SERENTAK} at once, each kind once a day; the player pickpocket has its own trigger (3 % a minute
 * while the player stands or strolls in a crowd of 4+ residents, not busy, once a day per town). Towns with no player
 * near get their events as data and gossip once a day (K14): a brawl between two residents, a small fire (a quarter of
 * them burn a stall's week), a resident robbed.
 * <p>
 * Shared machinery used by {@link Copet}, {@link Perkelahian}, {@link GerobakTerguling} and {@link Upacara}: the resident
 * task overrider ({@link #tugas}: urutan 100 among the 42 overriders, so a resident goes where an event or ceremony needs
 * it and plays the clip there), stand-in bodies ({@link #wakil}: a staff body wearing a resident's look, name and age for
 * processions), interaction routing (right-click, shift-right-click and attack on event NPCs, chickens and the event
 * entities: no new screen), speech with the 42V voice keys when registered, and safe standing spots.
 * <p>
 * Events never change a block and never kill (42.0, 42.7.4): fire is an {@link ApiKecilEntity}, wounds are a kneel.
 */
public final class PeristiwaJalan {

	public static final String COPET = "copet";
	public static final String COPET_WARGA = "copet_warga";
	public static final String PERKELAHIAN = "perkelahian";
	/** Alias of {@link #PERKELAHIAN} (42K {@code ObrolanWarga} tries both names). */
	public static final String TAWURAN = "tawuran";
	public static final String GEROBAK = "gerobak";
	public static final String API = "api";
	public static final String AYAM = "ayam";
	public static final String ANAK = "anak_tersesat";
	/** Every event kind ({@code uji kota_hidup acara peristiwa <jenis>}). */
	public static final List<String> JENIS = List.of(COPET, COPET_WARGA, PERKELAHIAN, GEROBAK, API, AYAM, ANAK);

	/** Lang prefix of this module ({@code lang-src/kota_hidup_peristiwa}). */
	static final String L = "kultivator.kota42.peristiwa.";
	/** Scheduler period in ticks. */
	static final int SELANG = 200;
	/** Chance of a new event per check while a player is in town (about 3-4 a full day, capped by the quota). */
	static final double PELUANG = 0.03D;
	/** Pickpocket chance per check in a crowd: 0.5 % per 10 s = 3 % a minute. */
	static final double PELUANG_COPET = 0.005D;
	static final int MAKS_SERENTAK = 2;
	/** Urutan of the task overrider (ceremony band of {@code PenimpaJadwalKota}). */
	static final int URUT_TUGAS = 100;
	/** Tag of every stand-in body ({@link #wakil}). */
	static final String TAG_WAKIL = "kultivator_wakil_upacara";
	/** Data section of street-event cooldowns ({@code KotaAcaraData.PERISTIWA}). */
	static final String BAGIAN = KotaAcaraData.PERISTIWA;

	/** A running event of one town. */
	abstract static class Kejadian {
		final String jenis;
		final long situs;
		final TempatHuni huni;
		final long mulai;

		Kejadian(String jenis, TempatHuni huni, long mulai) {
			this.jenis = jenis;
			this.situs = huni.situsId;
			this.huni = huni;
			this.mulai = mulai;
		}

		/** Every server tick; true when the event is over ({@link #akhiri} follows). */
		abstract boolean tik(ServerLevel level, long now);

		/** Removes what the event made (bodies, entities, tasks); called once at the end and on server stop. */
		void akhiri(ServerLevel level) {
		}

		/** A right-click of {@code player} on {@code e}; true when the event used it. */
		boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
			return false;
		}

		/** An attack of {@code player} on {@code e}; true when the event used it (the hit does no damage). */
		boolean pukul(ServerLevel level, ServerPlayer player, Entity e) {
			return false;
		}
	}

	/** A resident's event task until game time {@code sampai}. */
	private record Tugas(Kegiatan kegiatan, long sampai, long situs) {
	}

	private static final Map<Long, List<Kejadian>> JALAN = new HashMap<>();
	private static final Map<Integer, Tugas> TUGAS = new HashMap<>();
	/** player -> game time of its last routed click (one click is routed once). */
	private static final Map<UUID, Long> KLIK_T = new HashMap<>();
	private static boolean initialised;

	private PeristiwaJalan() {
	}

	// ================================================================== init

	/** From {@code KotaAcara.init} (42D's init class); also starts {@link Upacara}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(PeristiwaJalan::tik);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JALAN.clear();
			TUGAS.clear();
			KLIK_T.clear();
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer sp)
					|| !(level instanceof ServerLevel sl) || !(entity instanceof KultivatorNpc)) {
				return InteractionResult.PASS;
			}
			return klik(sl, sp, entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
				return InteractionResult.PASS;
			}
			for (List<Kejadian> list : JALAN.values()) {
				for (Kejadian k : List.copyOf(list)) {
					if (k.pukul(sl, sp, entity)) {
						return InteractionResult.SUCCESS;
					}
				}
			}
			return Upacara.pukul(sl, sp, entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		WargaApi.daftarPenimpa(new PenimpaJadwalKota() {
			@Override
			public @Nullable Kegiatan timpa(TempatHuni huni, Warga warga, Penduduk p, long waktu, int jam, long now) {
				Tugas t = TUGAS.get(warga.id);
				if (t == null) {
					return null;
				}
				if (now >= t.sampai() || t.situs() != huni.situsId) {
					TUGAS.remove(warga.id);
					return null;
				}
				return t.kegiatan();
			}

			@Override
			public int urutan() {
				return URUT_TUGAS;
			}
		});
		GerobakEntity.setPenangan((cart, player) -> player.level() instanceof ServerLevel level && klikEntitas(level, player, cart));
		HewanKota.addPenangkapAyam(PeristiwaJalan::ayamTertangkap);
		Suasana.addPengaruhTempat(PeristiwaJalan::pengaruhTempat);
		KotaUji.daftar("acara", PeristiwaJalan::uji);
		KotaUji.chips("kota_hidup acara copet_warga", "kota_hidup acara peristiwa <jenis>", "kota_hidup acara sebulan",
				"kota_hidup acara <upacara> data", "kota_hidup acara upacara");
		Upacara.init();
	}

	// ================================================================== test commands

	/**
	 * {@code uji kota_hidup acara copet|copet_warga|tawuran|gerobak|api|ayam|anak_tersesat} and the same under
	 * {@code acara peristiwa <jenis>}: starts the event in the tester's place now (merged with 42D's {@code acara} node).
	 */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("acara");
		LiteralArgumentBuilder<CommandSourceStack> grup = Commands.literal("peristiwa");
		for (String jenis : List.of(COPET, COPET_WARGA, PERKELAHIAN, TAWURAN, GEROBAK, API, AYAM, ANAK)) {
			root.then(Commands.literal(jenis).executes(ctx -> ujiPicu(ctx.getSource(), jenis)));
			grup.then(Commands.literal(jenis).executes(ctx -> ujiPicu(ctx.getSource(), jenis)));
		}
		return root.then(grup);
	}

	private static int ujiPicu(CommandSourceStack source, String jenis) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TempatInfo info = dev.kultivator.dunia.kota42.KotaHidupCommands.tempatPemain(player);
		if (info == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.acara.uji.tanpa_tempat"));
			return 0;
		}
		boolean ok = picu(source.getLevel(), info.situsId(), jenis);
		source.sendSuccess(() -> Component.translatable(L + (ok ? "uji.picu" : "uji.gagal"), jenis).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
		return ok ? 1 : 0;
	}

	/** True while street events run ({@code kotaAcara.aktif}, {@code peristiwa}, residents on). */
	static boolean boleh() {
		KotaConfig.Acara c = KotaConfig.acara();
		return c.aktif && c.peristiwa && WargaApi.aktif();
	}

	private static boolean jenisBoleh(String jenis) {
		KotaConfig.Acara c = KotaConfig.acara();
		return switch (jenis) {
			case COPET, COPET_WARGA -> c.copet;
			case PERKELAHIAN, TAWURAN -> c.perkelahian;
			case API -> c.apiKecil;
			default -> true;
		};
	}

	// ================================================================== contract C42.D1

	/**
	 * Starts event {@code jenis} in place {@code situs} now; false when it cannot (switch off, unknown kind, the place not
	 * active, no titik or bodies for it, the same kind already running). With no player within {@code kotaAcara.jarakTampil}
	 * blocks of the place it happens as data and gossip (true).
	 */
	public static boolean picu(ServerLevel level, long situs, String jenis) {
		if (level == null || jenis == null || !boleh() || !KotaHidup.duniaKota(level)) {
			return false;
		}
		String j = TAWURAN.equals(jenis) ? PERKELAHIAN : jenis;
		if (!JENIS.contains(j) || !jenisBoleh(j) || berjalan(situs, j)) {
			return false;
		}
		TempatHuni huni = Pemanggil.huni(situs);
		ServerPlayer player = huni == null ? null : pemainDi(level, huni, KotaConfig.acara().jarakTampil);
		if (huni == null || player == null) {
			return COPET.equals(j) ? false : dataSaja(level, situs, j, level.getRandom());
		}
		return mulai(level, huni, j, player, level.getGameTime());
	}

	/** True while an event of kind {@code jenis} runs in place {@code situs}. */
	public static boolean berjalan(long situs, String jenis) {
		List<Kejadian> list = JALAN.get(situs);
		if (list == null) {
			return false;
		}
		for (Kejadian k : list) {
			if (k.jenis.equals(jenis)) {
				return true;
			}
		}
		return false;
	}

	/** The kinds running in a place (debug, Buku Kota). */
	public static List<String> berjalan(long situs) {
		List<String> out = new ArrayList<>();
		for (Kejadian k : JALAN.getOrDefault(situs, List.of())) {
			out.add(k.jenis);
		}
		return out;
	}

	private static boolean mulai(ServerLevel level, TempatHuni huni, String jenis, ServerPlayer player, long now) {
		Kejadian k;
		try {
			k = switch (jenis) {
				case COPET -> Copet.padaPemain(level, huni, player, now);
				case COPET_WARGA -> Copet.padaWarga(level, huni, player, now);
				case PERKELAHIAN -> Perkelahian.mulai(level, huni, player, now);
				case GEROBAK -> GerobakTerguling.mulai(level, huni, player, now);
				case API -> Kebakaran.mulai(level, huni, player, now);
				case AYAM -> AyamLepas.mulai(level, huni, player, now);
				case ANAK -> AnakTersesat.mulai(level, huni, player, now);
				default -> null;
			};
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.warn("Street event {} failed to start in {}", jenis, huni.situsId, exception);
			k = null;
		}
		if (k == null) {
			return false;
		}
		JALAN.computeIfAbsent(huni.situsId, key -> new ArrayList<>()).add(k);
		CompoundTag t = data(level.getServer(), huni.situsId);
		long hari = KotaHidup.hari(level.getServer());
		gulirHari(t, huni.situsId, hari);
		t.putInt("n", t.getIntOr("n", 0) + 1);
		t.putLong("d_" + jenis, hari);
		KotaAcaraData.get(level.getServer()).ubah();
		return true;
	}

	// ================================================================== scheduler

	private static void tik(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		for (Iterator<Map.Entry<Long, List<Kejadian>>> it = JALAN.entrySet().iterator(); it.hasNext(); ) {
			List<Kejadian> list = it.next().getValue();
			for (Kejadian k : List.copyOf(list)) {
				boolean done;
				try {
					done = k.tik(level, now);
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.warn("Street event {} failed", k.jenis, exception);
					done = true;
				}
				if (done) {
					list.remove(k);
					try {
						k.akhiri(level);
					} catch (RuntimeException exception) {
						dev.kultivator.Kultivator.LOGGER.warn("Street event {} cleanup failed", k.jenis, exception);
					}
				}
			}
			if (list.isEmpty()) {
				it.remove();
			}
		}
		if (now % 100L == 0L) {
			TUGAS.values().removeIf(t -> now >= t.sampai());
			KLIK_T.values().removeIf(t -> now - t > 100L);
		}
		if (now % SELANG != 0L || !boleh()) {
			return;
		}
		long hari = KotaHidup.hari(server);
		int jam = jam(server);
		for (TempatHuni huni : Pemanggil.semua()) {
			if (Level.OVERWORLD.equals(huni.dimensi) && kota(server, huni.situsId)) {
				periksa(level, huni, now, hari, jam);
			}
		}
		if (now % 1200L == 0L) {
			hariTanpaPemain(level, hari);
		}
	}

	/** True for a 42 town: residents raised by 42A (not a sect compound). */
	static boolean kota(MinecraftServer server, long situs) {
		WargaData.Situs s = WargaData.get(server).peek(situs);
		return s != null && s.bangkit();
	}

	private static void periksa(ServerLevel level, TempatHuni huni, long now, long hari, int jam) {
		ServerPlayer player = pemainDi(level, huni, 0);
		if (player == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		CompoundTag t = data(server, huni.situsId);
		gulirHari(t, huni.situsId, hari);
		RandomSource r = level.getRandom();
		List<Kejadian> jalan = JALAN.getOrDefault(huni.situsId, List.of());
		// the pickpocket on a player: its own chance, once a day per town
		if (KotaConfig.acara().copet && t.getLongOr("d_" + COPET, -1L) != hari && jam >= 7 && jam < 21 && !berjalan(huni.situsId, COPET)
				&& dev.kultivator.util.Sibuk.why(player) == null && player.getDeltaMovement().horizontalDistanceSqr() < 0.02D
				&& keramaian(level, player.blockPosition(), huni.situsId) >= 4 && r.nextDouble() < PELUANG_COPET * DistrikMalam.faktorCopet(level.getServer(), huni.situsId)) {
			mulai(level, huni, COPET, player, now);
			return;
		}
		if (t.getIntOr("n", 0) >= t.getIntOr("kuota", 3) || jalan.size() >= MAKS_SERENTAK || r.nextDouble() >= PELUANG) {
			return;
		}
		String jenis = pilihJenis(huni, t, hari, jam, r);
		if (jenis != null) {
			mulai(level, huni, jenis, player, now);
		}
	}

	/** Weighted pick by the hour (carts by day, fires twice as likely at night, chickens in villages at dawn). */
	private static @Nullable String pilihJenis(TempatHuni huni, CompoundTag t, long hari, int jam, RandomSource r) {
		boolean malam = jam >= 19 || jam < 6;
		boolean kampung = huni.jenis.startsWith("kampung/");
		Map<String, Integer> bobot = new HashMap<>();
		if (jam >= 8 && jam < 17) {
			bobot.put(GEROBAK, 3);
		}
		if (jam >= 6 || jam < 2) {
			bobot.put(API, malam ? 4 : 2);
		}
		if (jam >= 11 || jam < 1) {
			bobot.put(PERKELAHIAN, (int) Math.round((malam ? 4 : 2) * (malam ? DistrikMalam.faktorPerkelahian(KotaHidup.server(), huni.situsId) : 1.0D)));
		}
		if (jam >= 7 && jam < 19 && !kampung) {
			bobot.put(COPET_WARGA, 2);
		}
		if (kampung && jam >= 5 && jam < 10) {
			bobot.put(AYAM, 4);
		}
		if (jam >= 15 && jam < 19) {
			bobot.put(ANAK, 1);
		}
		int total = 0;
		for (Iterator<Map.Entry<String, Integer>> it = bobot.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Integer> e = it.next();
			if (!jenisBoleh(e.getKey()) || t.getLongOr("d_" + e.getKey(), -1L) == hari || berjalan(huni.situsId, e.getKey())) {
				it.remove();
			} else {
				total += e.getValue();
			}
		}
		if (total <= 0) {
			return null;
		}
		int pick = r.nextInt(total);
		for (String jenis : JENIS) {
			Integer w = bobot.get(jenis);
			if (w != null) {
				pick -= w;
				if (pick < 0) {
					return jenis;
				}
			}
		}
		return null;
	}

	/** Starts a new world day in a town's event record: count 0 and the day's quota (2-4 by default, by hash). */
	private static void gulirHari(CompoundTag t, long situs, long hari) {
		if (t.getLongOr("hari", -1L) == hari) {
			return;
		}
		int dasar = KotaConfig.acara().peristiwaPerHari;
		int geser = (int) Math.floorMod(acak(situs, hari, 0x5EEDL), 3L) - 1;
		t.putLong("hari", hari);
		t.putInt("n", 0);
		t.putInt("kuota", Mth.clamp(dasar + geser, 0, 8));
	}

	/** Resident bodies of {@code situs} within 6 blocks of {@code at} (the crowd of the pickpocket rule). */
	static int keramaian(ServerLevel level, BlockPos at, long situs) {
		return level.getEntitiesOfClass(KultivatorNpc.class, new AABB(at).inflate(6.0D),
				n -> n.isAlive() && n.penduduk() && n.situsPenduduk() == situs).size();
	}

	// ================================================================== off-screen events (K14)

	/**
	 * Once a world day per raised town no player is near: half the days one event happens as data and gossip only. A brawl
	 * between two adults (enemies first), a small fire (a quarter burn a business's week and darken the town's mood), or a
	 * resident robbed in the market.
	 */
	private static void hariTanpaPemain(ServerLevel level, long hari) {
		MinecraftServer server = level.getServer();
		for (WargaData.Situs s : WargaData.get(server).semuaSitus()) {
			if (!s.bangkit() || s.warga.size() < 6) {
				continue;
			}
			TempatHuni huni = Pemanggil.huni(s.id);
			if (huni != null && pemainDi(level, huni, KotaConfig.acara().jarakTampil) != null) {
				continue;
			}
			CompoundTag t = data(server, s.id);
			if (t.getLongOr("hantu", -1L) == hari) {
				continue;
			}
			t.putLong("hantu", hari);
			KotaAcaraData.get(server).ubah();
			RandomSource r = RandomSource.create(acak(s.id, hari, 0xD47AL));
			if (r.nextBoolean()) {
				int roll = r.nextInt(100);
				dataSaja(level, s.id, roll < 55 ? PERKELAHIAN : roll < 80 ? API : COPET_WARGA, r);
			}
		}
	}

	/** An event that happens without a player: its data consequence and the gossip residents spread (true when done). */
	static boolean dataSaja(ServerLevel level, long situs, String jenis, RandomSource r) {
		MinecraftServer server = level.getServer();
		List<Warga> dewasa = new ArrayList<>();
		for (Warga w : WargaApi.wargaTempat(situs)) {
			if (w.status == Warga.Status.HIDUP && WargaApi.umur(w) >= 16) {
				dewasa.add(w);
			}
		}
		if (dewasa.size() < 2) {
			return false;
		}
		switch (jenis) {
			case PERKELAHIAN -> {
				Warga a = dewasa.get(r.nextInt(dewasa.size()));
				Warga b = musuhDari(a);
				if (b == null) {
					b = dewasa.get(r.nextInt(dewasa.size()));
				}
				if (b == a) {
					return false;
				}
				Suasana.marah(server, a, level.getGameTime());
				Suasana.marah(server, b, level.getGameTime());
				GosipWarga.lahir(level, situs, "BERKELAHI", Fakta.Rujuk.warga(a.id), Fakta.Rujuk.warga(b.id));
			}
			case API -> {
				List<UsahaWarga.Usaha> usaha = UsahaWarga.usaha(situs);
				boolean hangus = r.nextInt(4) == 0;
				if (hangus && !usaha.isEmpty()) {
					UsahaWarga.Usaha u = usaha.get(r.nextInt(usaha.size()));
					UsahaWarga.kebakaran(server, situs, u.pemilikWarga());
					catatSuasanaTempat(server, situs, -10);
				}
				GosipWarga.lahir(level, situs, "BAHAYA", 0, Fakta.Kebenaran.BENAR, List.of(), null, Fakta.Rujuk.tempat(situs),
						Fakta.Rujuk.kata(hangus ? "kebakaran" : "api_kecil"));
			}
			case COPET_WARGA, COPET -> {
				Warga korban = dewasa.get(r.nextInt(dewasa.size()));
				Suasana.catat(server, korban, Suasana.Peristiwa.DIRAMPOK);
				GosipWarga.lahir(level, situs, "BAHAYA", 0, Fakta.Kebenaran.BENAR, List.of(), null, Fakta.Rujuk.tempat(situs),
						Fakta.Rujuk.kata("copet"));
			}
			default -> {
				GosipWarga.lahir(level, situs, "TEMPAT_ADA", Fakta.Rujuk.tempat(situs));
			}
		}
		return true;
	}

	/** The resident's enemy ({@code MUSUH} bond) still living, or null. */
	static @Nullable Warga musuhDari(Warga w) {
		for (long bond : w.hubungan) {
			if (JenisHubungan.jenis(bond) == JenisHubungan.MUSUH) {
				Warga o = WargaApi.byId(JenisHubungan.idLain(bond));
				if (o != null && o.status == Warga.Status.HIDUP) {
					return o;
				}
			}
		}
		return null;
	}

	// ================================================================== town mood layer

	/** A mood change for the whole town for one world day (a burnt stall -10, a calmed brawl +5). */
	static void catatSuasanaTempat(MinecraftServer server, long situs, int nilai) {
		CompoundTag t = data(server, situs);
		long hari = KotaHidup.hari(server);
		int lama = t.getLongOr("moodHari", -9L) == hari ? t.getIntOr("moodNilai", 0) : 0;
		t.putLong("moodHari", hari);
		t.putInt("moodNilai", Mth.clamp(lama + nilai, -20, 20));
		KotaAcaraData.get(server).ubah();
	}

	private static int pengaruhTempat(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return 0;
		}
		CompoundTag t = KotaAcaraData.get(server).bagian(BAGIAN).getCompoundOrEmpty("kota_" + situs);
		return t.getLongOr("moodHari", -9L) == KotaHidup.hari(server) ? t.getIntOr("moodNilai", 0) : 0;
	}

	// ================================================================== resident tasks

	/** Sends resident {@code w} of {@code situs} to do {@code kegiatan} until game time {@code sampai} (overrides its day). */
	public static void tugas(Warga w, long situs, Kegiatan kegiatan, long sampai) {
		if (w != null && kegiatan != null) {
			TUGAS.put(w.id, new Tugas(kegiatan, sampai, situs));
		}
	}

	/** Ends resident {@code w}'s event task. */
	public static void lepas(@Nullable Warga w) {
		if (w != null) {
			TUGAS.remove(w.id);
		}
	}

	/** True while resident {@code w} has an event or ceremony task. */
	public static boolean bertugas(Warga w) {
		return w != null && TUGAS.containsKey(w.id);
	}

	/** An activity at a world position ({@code @xz:x,z}) with a clip and a right-hand prop. */
	static Kegiatan di(String kunci, BlockPos pos, int klip, int prop) {
		return Kegiatan.di(kunci, Kegiatan.XZ + pos.getX() + "," + pos.getZ(), klip, prop);
	}

	/** The body of resident {@code w} in an active place, or null. */
	static @Nullable KultivatorNpc tubuh(TempatHuni huni, @Nullable Warga w) {
		return w == null || w.slot == Warga.TANPA_SLOT ? null : huni.tubuhDari(w.slot);
	}

	/** The house of resident {@code w}: its own slot's house, else a household member's, else the place centre. */
	static BlockPos rumah(TempatHuni huni, @Nullable Warga w) {
		if (w != null) {
			Penduduk p = w.slot == Warga.TANPA_SLOT ? null : huni.orang(w.slot);
			if (p != null) {
				return huni.rumahPos(p);
			}
			MinecraftServer server = KotaHidup.server();
			WargaData.Situs s = server == null ? null : WargaData.get(server).peek(huni.situsId);
			if (s != null && w.rumahTangga > 0) {
				for (Warga o : s.rumahTangga(w.rumahTangga)) {
					Penduduk q = o.slot == Warga.TANPA_SLOT ? null : huni.orang(o.slot);
					if (q != null) {
						return huni.rumahPos(q);
					}
				}
			}
		}
		return huni.pusat();
	}

	/**
	 * A stand-in body for resident {@code w}: a staff body of the resident's role with its look seed, name plate, sex and
	 * age (the same face and voice players know), held in place; {@code keadaan} "pengantin" / "berkabung" dresses it in
	 * the 42V-B look rows. Null when it cannot be made.
	 */
	static @Nullable KultivatorNpc wakil(ServerLevel level, TempatHuni huni, Warga w, BlockPos pos, float yaw, @Nullable String keadaan) {
		NpcRole role = w.peranNpc();
		KultivatorNpc npc = HidupApi.tubuhStaf(level, huni.situsId, role, pos, yaw, w.look);
		if (npc == null) {
			return null;
		}
		npc.memory().putBoolean("hold", true);
		npc.setPlate(Component.translatable("kultivator.kota.papan_nama", WargaApi.nama(w), Component.translatable(role.titleKey())));
		Sosok sosok = SosokApi.sosok(npc).withKelamin(w.pria() ? Sosok.LAKI : Sosok.PEREMPUAN).withUsia(WargaApi.usiaSosok(w));
		SosokApi.setSosok(npc, sosok);
		if (keadaan != null) {
			SosokApi.setRupa(npc, role.id() + "." + keadaan);
		}
		npc.addTag(TAG_WAKIL);
		return npc;
	}

	/** A staff body of {@code role} at {@code pos} held there (musicians, bearers, a guard), or null. */
	static @Nullable KultivatorNpc staf(ServerLevel level, long situs, NpcRole role, BlockPos pos, float yaw, int look) {
		KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, role, pos, yaw, look);
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
			npc.addTag(TAG_WAKIL);
		}
		return npc;
	}

	/** Removes a body made for an event (never a resident body). */
	static void hilangkan(@Nullable KultivatorNpc npc) {
		if (npc != null && !npc.isRemoved() && !npc.penduduk()) {
			npc.vanish();
		}
	}

	// ================================================================== interaction routing

	private static boolean klik(ServerLevel level, ServerPlayer player, Entity e) {
		long now = level.getGameTime();
		Long last = KLIK_T.get(player.getUUID());
		if (last != null && now - last < 4L) {
			return true;
		}
		boolean shift = player.isShiftKeyDown();
		for (List<Kejadian> list : JALAN.values()) {
			for (Kejadian k : List.copyOf(list)) {
				if (k.klik(level, player, e, shift)) {
					KLIK_T.put(player.getUUID(), now);
					return true;
				}
			}
		}
		if (Upacara.klik(level, player, e, shift)) {
			KLIK_T.put(player.getUUID(), now);
			return true;
		}
		return false;
	}

	/** A click on an event entity ({@link ApiKecilEntity}, {@link BarangTercecerEntity}, an overturned cart). */
	static boolean klikEntitas(ServerLevel level, ServerPlayer player, Entity e) {
		return klik(level, player, e);
	}

	// ================================================================== small helpers

	/** The town clock hour 0-23 (dawn 06:00 = tick 0). */
	static int jam(MinecraftServer server) {
		return (int) ((Kota.timeOfDay(server) / 1000L + 6L) % 24L);
	}

	/** A non-spectator player inside the place's box (inflated by {@code lebih}), or null. */
	static @Nullable ServerPlayer pemainDi(ServerLevel level, TempatHuni huni, int lebih) {
		AABB box = AABB.of(huni.kotak).inflate(8 + lebih);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && box.contains(p.position())) {
				return p;
			}
		}
		return null;
	}

	/** Players within {@code r} blocks of {@code at}. */
	static List<ServerPlayer> pemainDekat(ServerLevel level, Vec3 at, double r) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.position().distanceToSqr(at) <= r * r) {
				out.add(p);
			}
		}
		return out;
	}

	/** Chat (or action bar) {@code msg} to every player within {@code r} blocks. */
	static void kabar(ServerLevel level, Vec3 at, double r, Component msg, boolean bar) {
		for (ServerPlayer p : pemainDekat(level, at, r)) {
			if (bar) {
				Msg.actionBar(p, msg);
			} else {
				Msg.chat(p, msg);
			}
		}
	}

	/** Speech: the bubble from this module's lang always, the 42V voice line too while it is registered. */
	static void seru(@Nullable KultivatorNpc npc, @Nullable String suara, String kunci, Object... isian) {
		if (npc == null || npc.isRemoved()) {
			return;
		}
		npc.say(Component.translatable(L + kunci, isian), 80);
		if (suara != null && VoiceEvents.exists(suara)) {
			Voice.say(npc, suara, "");
		}
	}

	/** Gives a stack (inventory first, the rest dropped at the player). */
	static void beri(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.spawnAtLocation(player.level(), stack);
		}
	}

	/** Nama Baik in the town ({@code KotaData} reputation of the site id). */
	static void namaBaik(ServerPlayer player, long situs, int delta) {
		KotaData.get(player.level().getServer()).addReputation(situs, player.getUUID(), delta);
	}

	/** A spot a person can stand on at or within 3 blocks of {@code at} (sturdy floor, two free blocks, no fluid), or null. */
	static @Nullable BlockPos berdiri(ServerLevel level, BlockPos at) {
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy : new int[]{0, 1, -1, 2, -2}) {
						BlockPos feet = at.offset(dx, dy, dz);
						if (!level.isLoaded(feet)) {
							return null;
						}
						BlockPos floor = feet.below();
						if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
								&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
								&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
								&& level.getFluidState(feet).isEmpty()) {
							return feet;
						}
					}
				}
			}
		}
		return null;
	}

	/** The titik of several kinds of an active place. */
	static List<Titik> titik(TempatHuni huni, String... jenis) {
		List<Titik> out = new ArrayList<>();
		for (String j : jenis) {
			out.addAll(huni.titik(j));
		}
		return out;
	}

	/** The adult residents of a place (16+, living there). */
	static List<Warga> dewasa(long situs) {
		List<Warga> out = new ArrayList<>();
		for (Warga w : WargaApi.wargaTempat(situs)) {
			if (w.status == Warga.Status.HIDUP && WargaApi.umur(w) >= 16) {
				out.add(w);
			}
		}
		return out;
	}

	/** Resident bodies of {@code situs} within {@code r} blocks of {@code at}, nearest first. */
	static List<KultivatorNpc> tubuhDekat(ServerLevel level, long situs, Vec3 at, double r) {
		List<KultivatorNpc> out = level.getEntitiesOfClass(KultivatorNpc.class, new AABB(BlockPos.containing(at)).inflate(r),
				n -> n.isAlive() && n.penduduk() && n.situsPenduduk() == situs);
		out.sort(Comparator.comparingDouble(n -> n.position().distanceToSqr(at)));
		return out;
	}

	/** A stable 64-bit mix (deterministic per place, day and salt). */
	static long acak(long a, long b, long salt) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h;
	}

	/** The street-event record of a town ({@code KotaAcaraData.bagian("peristiwa")["kota_<situs>"]}). */
	static CompoundTag data(MinecraftServer server, long situs) {
		return KotaAcaraData.get(server).situs(BAGIAN, situs);
	}

	/** Index of a player in the resident memory tables (gossip subjects). */
	static int indeks(ServerPlayer player) {
		return WargaData.get(player.level().getServer()).indeksPemain(player.getUUID());
	}

	// ================================================================== small fire (42.7.4 "api kecil")

	/**
	 * A small fire on a kitchen, eating-stall or market lot: an {@link ApiKecilEntity} (never a block) that grows, spreads
	 * every 60 s to the next work titik (at most 3 fires), while residents form a bucket chain from the nearest well
	 * (clip 390 {@code oper_ember}) and the night watch rings an alarm. Players put it out with a water bucket (or by
	 * passing buckets along the chain): Nama Baik +3, the owner remembers being saved. Not out after 3 minutes it burns
	 * itself out: the business loses its week ({@code UsahaWarga.kebakaran}), the town mood -10, nobody is hurt.
	 */
	static final class Kebakaran extends Kejadian {
		static final int LAMA = 3600;
		static final int JALAR = 1200;
		static final int MAKS_API = 3;
		static final float SIRAM_EMBER = 0.45F;
		static final float SIRAM_OPER = 0.15F;
		/** Size growth per second while burning. */
		static final float TUMBUH = 0.006F;
		/** Douse per second per bucket-chain member near the fire. */
		static final float RANTAI = 0.0025F;

		final List<ApiKecilEntity> api = new ArrayList<>();
		final List<BlockPos> calon = new ArrayList<>();
		final List<Warga> rantai = new ArrayList<>();
		/** Set once the fire is out: the scrubbing tasks of {@link #padam} then outlive the event (they expire by themselves). */
		boolean menggosok;
		final List<BlockPos> rantaiPos = new ArrayList<>();
		final Set<UUID> penolong = new HashSet<>();
		final Map<UUID, Long> oper = new HashMap<>();
		@Nullable Warga pemilik;
		@Nullable BlockPos sumur;
		long jalarT;
		boolean teriak;

		private Kebakaran(TempatHuni huni, long now) {
			super(API, huni, now);
			this.jalarT = now;
		}

		static @Nullable Kebakaran mulai(ServerLevel level, TempatHuni huni, ServerPlayer player, long now) {
			List<Titik> spots = titik(huni, "kerja:kuali", "kerja:tungku", "lapak", "kerja:kios", "kerja:teko");
			Vec3 near = player.position();
			spots.removeIf(t -> t.pos().distSqr(BlockPos.containing(near)) > 64.0D * 64.0D);
			if (spots.isEmpty()) {
				return null;
			}
			RandomSource r = level.getRandom();
			Titik first = spots.get(r.nextInt(spots.size()));
			BlockPos at = berdiri(level, first.pos().relative(first.hadap()));
			if (at == null) {
				at = berdiri(level, first.pos());
			}
			if (at == null) {
				return null;
			}
			Kebakaran k = new Kebakaran(huni, now);
			ApiKecilEntity fire = k.nyalakan(level, at, varian(first.jenis()));
			if (fire == null) {
				return null;
			}
			BlockPos origin = first.pos();
			for (Titik t : spots) {
				if (t != first && t.pos().distSqr(origin) <= 14.0D * 14.0D) {
					k.calon.add(t.pos().relative(t.hadap()));
				}
			}
			k.calon.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
			k.pemilik = pemilikDekat(level, huni, at);
			Titik well = null;
			double best = 48.0D * 48.0D;
			for (Titik t : titik(huni, "sumur", "sumur_timba")) {
				double d = t.pos().distSqr(at);
				if (d < best) {
					best = d;
					well = t;
				}
			}
			k.sumur = well == null ? null : berdiri(level, well.pos());
			k.bentukRantai(level, at, now);
			if (k.pemilik != null) {
				tugas(k.pemilik, k.situs, di("api_meratap", at.offset(3, 0, 1), KotaGerakId.MERATAP, 0), now + LAMA + 200L);
			}
			level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.2F, 1.5F);
			Component where = k.pemilik != null ? Component.translatable(L + "api.di_milik", WargaApi.nama(k.pemilik))
					: Component.translatable(L + "api.di_lapak");
			kabar(level, Vec3.atCenterOf(at), 64.0D, Component.translatable(L + "api.mulai", where).withStyle(ChatFormatting.RED), false);
			kabar(level, Vec3.atCenterOf(at), 24.0D, Component.translatable(L + "api.petunjuk"), true);
			return k;
		}

		private static int varian(String jenis) {
			if (jenis.endsWith("kuali") || jenis.endsWith("teko")) {
				return ApiKecilEntity.VARIAN_MINYAK;
			}
			if (jenis.equals("lapak") || jenis.endsWith("kios")) {
				return ApiKecilEntity.VARIAN_JERAMI;
			}
			return ApiKecilEntity.VARIAN_KAYU;
		}

		/** The owner of the burning lot: the business owner whose body works nearest, else the nearest adult resident body. */
		private static @Nullable Warga pemilikDekat(ServerLevel level, TempatHuni huni, BlockPos at) {
			Set<Integer> pemilikUsaha = new HashSet<>();
			for (UsahaWarga.Usaha u : UsahaWarga.usaha(huni.situsId)) {
				pemilikUsaha.add(u.pemilikWarga());
			}
			Warga fallback = null;
			for (KultivatorNpc npc : tubuhDekat(level, huni.situsId, Vec3.atCenterOf(at), 20.0D)) {
				Warga w = WargaApi.dari(npc);
				if (w == null || WargaApi.umur(w) < 16) {
					continue;
				}
				if (pemilikUsaha.contains(w.id)) {
					return w;
				}
				if (fallback == null) {
					fallback = w;
				}
			}
			if (fallback == null && !pemilikUsaha.isEmpty()) {
				fallback = WargaApi.byId(pemilikUsaha.iterator().next());
			}
			return fallback;
		}

		private @Nullable ApiKecilEntity nyalakan(ServerLevel level, BlockPos at, int varian) {
			if (!dev.kultivator.dunia.DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
				return null;
			}
			ApiKecilEntity fire = KotaAcaraEntities.API_KECIL.create(level, EntitySpawnReason.EVENT);
			if (fire == null) {
				return null;
			}
			fire.snapTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
			fire.ikat(situs, at.asLong());
			fire.setVarian(varian);
			fire.setNilai(0.35F);
			fire.setKeadaan(ApiKecilEntity.KEADAAN_MENYALA);
			fire.pegang(level.getGameTime() + 200L);
			if (!level.addFreshEntity(fire)) {
				return null;
			}
			api.add(fire);
			return fire;
		}

		/** Up to six adult residents near the fire line up between the well and the fire, passing buckets. */
		private void bentukRantai(ServerLevel level, BlockPos fire, long now) {
			if (sumur == null) {
				return;
			}
			List<KultivatorNpc> near = tubuhDekat(level, situs, Vec3.atCenterOf(fire), 40.0D);
			int n = 0;
			for (KultivatorNpc npc : near) {
				Warga w = WargaApi.dari(npc);
				if (w == null || w == pemilik || WargaApi.umur(w) < 14 || bertugas(w)) {
					continue;
				}
				n++;
				rantai.add(w);
				if (n >= 6) {
					break;
				}
			}
			for (int i = 0; i < rantai.size(); i++) {
				double f = (i + 1.0D) / (rantai.size() + 1.0D);
				BlockPos spot = BlockPos.containing(Vec3.atCenterOf(sumur).lerp(Vec3.atCenterOf(fire), f));
				rantaiPos.add(spot);
				tugas(rantai.get(i), situs, di("rantai_ember", spot, KotaGerakId.OPER_EMBER, KotaGerakId.PROP_EMBER_API), now + LAMA + 100L);
			}
		}

		@Override
		boolean tik(ServerLevel level, long now) {
			api.removeIf(e -> e.isRemoved());
			boolean semuaPadam = true;
			for (ApiKecilEntity e : api) {
				if (e.keadaan() != ApiKecilEntity.KEADAAN_PADAM) {
					semuaPadam = false;
				}
				e.pegang(now + 200L);
			}
			if (api.isEmpty() || semuaPadam) {
				padam(level);
				return true;
			}
			if (now - mulai >= LAMA) {
				habis(level);
				return true;
			}
			if (!teriak && now - mulai >= 20L) {
				teriak = true;
				List<KultivatorNpc> near = tubuhDekat(level, situs, api.getFirst().position(), 24.0D);
				if (!near.isEmpty()) {
					HidupApi.tatap(near.getFirst(), api.getFirst());
					HidupApi.klip(near.getFirst(), GerakId.TUNJUK, 0, 60);
					seru(near.getFirst(), "kota42.kebakaran", "api.teriak");
				}
			}
			if ((now - mulai) % 20L == 0L) {
				int jamNow = jam(level.getServer());
				boolean malam = jamNow >= 19 || jamNow < 6;
				for (ApiKecilEntity e : api) {
					if (e.keadaan() == ApiKecilEntity.KEADAAN_PADAM) {
						continue;
					}
					int hadir = 0;
					for (int i = 0; i < rantai.size(); i++) {
						KultivatorNpc body = tubuh(huni, rantai.get(i));
						if (body != null && body.position().distanceToSqr(Vec3.atCenterOf(rantaiPos.get(i))) < 9.0D) {
							hadir++;
						}
					}
					float size = e.nilai() + TUMBUH - RANTAI * hadir / Math.max(1, api.size());
					if (size <= 0.0F) {
						e.padamkan();
					} else {
						e.setNilai(Math.min(1.0F, size));
					}
				}
				if ((now - mulai) % 40L == 0L) {
					ApiKecilEntity e = api.getFirst();
					level.playSound(null, e.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
					// the one at the head of the chain, next to the flames, flings his bucket at them (the others keep passing)
					KultivatorNpc depan = rantai.isEmpty() ? null : tubuh(huni, rantai.getLast());
					if (depan != null && depan.position().distanceToSqr(Vec3.atCenterOf(rantaiPos.getLast())) < 9.0D) {
						HidupApi.tatap(depan, e);
						HidupApi.klip(depan, KotaGerakId.SIRAM_API, KotaGerakId.PROP_EMBER_API, 24);
						level.sendParticles(ParticleTypes.SPLASH, e.getX(), e.getY() + 0.9D, e.getZ(), 10, 0.35D, 0.3D, 0.35D, 0.12D);
					}
					if (malam && (now - mulai) % 80L == 0L) {
						// the night watch beats the alarm fast (the gong of 42D's ronda when one is near)
						level.playSound(null, e.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.8F, 1.7F);
					}
				}
			}
			if (now - jalarT >= JALAR && api.size() < MAKS_API && !calon.isEmpty()) {
				jalarT = now;
				ApiKecilEntity parent = api.getLast();
				parent.setKeadaan(ApiKecilEntity.KEADAAN_MENJALAR);
				BlockPos next = null;
				while (next == null && !calon.isEmpty()) {
					next = berdiri(level, calon.removeFirst());
				}
				if (next != null && nyalakan(level, next, parent.varian()) != null) {
					kabar(level, Vec3.atCenterOf(next), 48.0D, Component.translatable(L + "api.menjalar").withStyle(ChatFormatting.RED), false);
				}
			}
			return false;
		}

		@Override
		boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
			if (!(e instanceof ApiKecilEntity fire) || !api.contains(fire) || fire.keadaan() == ApiKecilEntity.KEADAAN_PADAM) {
				return false;
			}
			ItemStack hand = player.getMainHandItem();
			long now = level.getGameTime();
			if (hand.is(Items.WATER_BUCKET)) {
				if (!player.hasInfiniteMaterials()) {
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
				}
				siram(level, fire, SIRAM_EMBER);
				penolong.add(player.getUUID());
				Msg.actionBar(player, Component.translatable(L + "api.siram"));
				return true;
			}
			if (!rantai.isEmpty()) {
				Long last = oper.get(player.getUUID());
				if (last == null || now - last >= 20L) {
					oper.put(player.getUUID(), now);
					siram(level, fire, SIRAM_OPER);
					penolong.add(player.getUUID());
					Msg.actionBar(player, Component.translatable(L + "api.oper"));
				}
				return true;
			}
			Msg.actionBar(player, Component.translatable(L + "api.butuh_ember"));
			return true;
		}

		private void siram(ServerLevel level, ApiKecilEntity fire, float jumlah) {
			BlockPos at = fire.blockPosition();
			level.playSound(null, at, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
			level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.2F);
			level.sendParticles(ParticleTypes.SPLASH, fire.getX(), fire.getY() + 0.6D, fire.getZ(), 24, 0.4D, 0.3D, 0.4D, 0.1D);
			level.sendParticles(ParticleTypes.CLOUD, fire.getX(), fire.getY() + 1.0D, fire.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.02D);
			float size = fire.nilai() - jumlah;
			if (size <= 0.0F) {
				fire.padamkan();
			} else {
				fire.setNilai(size);
			}
		}

		private void padam(ServerLevel level) {
			MinecraftServer server = level.getServer();
			Vec3 at = api.isEmpty() ? Vec3.atCenterOf(huni.pusat()) : api.getFirst().position();
			KultivatorNpc owner = tubuh(huni, pemilik);
			if (owner != null) {
				HidupApi.klip(owner, GerakId.HORMAT_DALAM, 0, 60);
				seru(owner, "kota42.api_padam", "api.terima_kasih");
			}
			// the fire is out: the owner and up to two of the bucket chain scrub the soot off the scorched wall for a minute
			long sekarang = level.getGameTime();
			BlockPos dinding = BlockPos.containing(at);
			menggosok = true;
			List<Warga> penggosok = new ArrayList<>();
			if (pemilik != null) {
				penggosok.add(pemilik);
			}
			for (int i = 0; i < rantai.size() && penggosok.size() < 3; i++) {
				penggosok.add(rantai.get(i));
			}
			for (int i = 0; i < penggosok.size(); i++) {
				BlockPos spot = berdiri(level, dinding.offset(i * 2 - 2, 0, 0));
				tugas(penggosok.get(i), situs, di("gosok_jelaga", spot == null ? dinding : spot, KotaGerakId.GOSOK_DINDING, 0), sekarang + 1200L);
			}
			for (Warga w : rantai) {
				if (!penggosok.contains(w)) {
					lepas(w); // the rest of the bucket chain has nothing left to pass
				}
			}
			for (UUID id : penolong) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p == null) {
					continue;
				}
				namaBaik(p, situs, 3);
				if (pemilik != null) {
					WargaApi.peristiwaPemain(p, situs, "selamat", pemilik);
				}
				Advancements.grant(p, "tempat/kota42_api");
				Msg.chat(p, Component.translatable(L + "api.padam_pemain").withStyle(ChatFormatting.GOLD));
			}
			if (penolong.isEmpty()) {
				kabar(level, at, 48.0D, Component.translatable(L + "api.padam_warga"), false);
			}
		}

		private void habis(ServerLevel level) {
			MinecraftServer server = level.getServer();
			Vec3 at = api.isEmpty() ? Vec3.atCenterOf(huni.pusat()) : api.getFirst().position();
			for (ApiKecilEntity e : api) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.8D, e.getZ(), 20, 0.5D, 0.5D, 0.5D, 0.02D);
				e.padamkan();
			}
			if (pemilik != null) {
				UsahaWarga.kebakaran(server, situs, pemilik.id);
				KultivatorNpc owner = tubuh(huni, pemilik);
				if (owner != null) {
					HidupApi.klip(owner, GerakId.MENANGIS, 0, 200);
				}
			}
			catatSuasanaTempat(server, situs, -10);
			GosipWarga.lahir(level, situs, "BAHAYA", 0, Fakta.Kebenaran.BENAR, List.of(), null, Fakta.Rujuk.tempat(situs),
					Fakta.Rujuk.kata("kebakaran"));
			kabar(level, at, 64.0D, Component.translatable(L + "api.habis").withStyle(ChatFormatting.GRAY), false);
		}

		@Override
		void akhiri(ServerLevel level) {
			for (ApiKecilEntity e : api) {
				if (!e.isRemoved() && e.keadaan() != ApiKecilEntity.KEADAAN_PADAM) {
					e.padamkan();
				}
			}
			if (menggosok) {
				return;
			}
			for (Warga w : rantai) {
				lepas(w);
			}
			lepas(pemilik);
		}
	}

	// ================================================================== loose chickens (villages)

	/** 42D-H's catch listener: a loose chicken caught by {@code player} counts toward its event. */
	private static void ayamTertangkap(ServerPlayer player, AyamKotaEntity ayam) {
		for (List<Kejadian> list : JALAN.values()) {
			for (Kejadian k : list) {
				if (k instanceof AyamLepas a && a.tangkap(player, ayam)) {
					return;
				}
			}
		}
	}

	/**
	 * Loose chickens (42.7.4 "ayam lepas", villages, mornings): 3-5 {@code ayam_kota} burst from the coop and scatter
	 * ({@code HewanKota.ayamLepas} of 42D-H: they run from anyone near and a click catches one); the farmer chases them
	 * (clip 434 {@code kejar_ayam}). A player who catches three gets two eggs and the farmer's thanks (+5). Leftovers go
	 * home by themselves after 5 minutes.
	 */
	static final class AyamLepas extends Kejadian {
		static final int LAMA = 6000;
		static final int TANGKAP = 3;

		final List<AyamKotaEntity> ayam = new ArrayList<>();
		final Map<UUID, Integer> tangkap = new HashMap<>();
		final BlockPos pusat;
		@Nullable Warga petani;

		private AyamLepas(TempatHuni huni, long now, BlockPos pusat) {
			super(AYAM, huni, now);
			this.pusat = pusat;
		}

		static @Nullable AyamLepas mulai(ServerLevel level, TempatHuni huni, ServerPlayer player, long now) {
			if (!huni.jenis.startsWith("kampung/")) {
				return null;
			}
			List<Titik> kandang = titik(huni, dev.kultivator.dunia.kota42.KotaTitik.KANDANG);
			BlockPos base = kandang.isEmpty() ? player.blockPosition().offset(6, 0, 6) : kandang.getFirst().pos();
			BlockPos pusat = berdiri(level, base);
			if (pusat == null) {
				return null;
			}
			RandomSource r = level.getRandom();
			List<AyamKotaEntity> made = HewanKota.ayamLepas(level, huni.situsId, pusat, 3 + r.nextInt(3), LAMA / 20);
			if (made.size() < TANGKAP) {
				for (AyamKotaEntity c : made) {
					c.discard();
				}
				return null;
			}
			AyamLepas k = new AyamLepas(huni, now, pusat);
			k.ayam.addAll(made);
			for (Warga w : dewasa(huni.situsId)) {
				if (w.peranNpc() == NpcRole.PETANI && !bertugas(w)) {
					k.petani = w;
					break;
				}
			}
			if (k.petani == null) {
				List<Warga> all = dewasa(huni.situsId);
				all.removeIf(PeristiwaJalan::bertugas);
				k.petani = all.isEmpty() ? null : all.get(r.nextInt(all.size()));
			}
			if (k.petani != null) {
				tugas(k.petani, huni.situsId, di("kejar_ayam", pusat, KotaGerakId.KEJAR_AYAM, 0).berjalan(GerakId.LARI), now + LAMA);
			}
			level.playSound(null, pusat, SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 0.8F, 1.3F);
			Component siapa = k.petani == null ? Component.translatable(L + "ayam.seorang_petani") : WargaApi.nama(k.petani);
			kabar(level, Vec3.atCenterOf(pusat), 48.0D, Component.translatable(L + "ayam.mulai", siapa), false);
			kabar(level, Vec3.atCenterOf(pusat), 24.0D, Component.translatable(L + "ayam.petunjuk"), true);
			return k;
		}

		@Override
		boolean tik(ServerLevel level, long now) {
			ayam.removeIf(Entity::isRemoved);
			if (ayam.isEmpty()) {
				return true;
			}
			if (now - mulai >= LAMA) {
				kabar(level, Vec3.atCenterOf(pusat), 48.0D, Component.translatable(L + "ayam.pulang"), false);
				return true;
			}
			KultivatorNpc farmer = tubuh(huni, petani);
			if (farmer != null && (now - mulai) % 100L == 0L) {
				RandomSource r = level.getRandom();
				AyamKotaEntity target = ayam.get(r.nextInt(ayam.size()));
				tugas(petani, situs, di("kejar_ayam", target.blockPosition(), KotaGerakId.KEJAR_AYAM, 0).berjalan(GerakId.LARI), mulai + LAMA);
				if (r.nextInt(3) == 0) {
					seru(farmer, null, "ayam.petani." + r.nextInt(3));
				}
			}
			return false;
		}

		/** One of this event's chickens was caught by {@code player}; true when it was ours. */
		boolean tangkap(ServerPlayer player, AyamKotaEntity c) {
			if (!ayam.remove(c)) {
				return false;
			}
			int n = tangkap.merge(player.getUUID(), 1, Integer::sum);
			Msg.actionBar(player, Component.translatable(L + "ayam.tertangkap", Math.min(n, TANGKAP), TANGKAP));
			if (n == TANGKAP) {
				beri(player, new ItemStack(Items.EGG, 2));
				if (petani != null) {
					WargaApi.ubahKesan(petani, player, 5, "ayam", null);
					KultivatorNpc petaniTubuh = tubuh(huni, petani);
					if (petaniTubuh != null) {
						// the farmer takes the hens home in the bamboo basket
						HidupApi.tatap(petaniTubuh, player);
						HidupApi.klip(petaniTubuh, KotaGerakId.DORONG_SANGKAR_AYAM, 0, 160);
					}
					seru(tubuh(huni, petani), "kota42.terima_kasih", "ayam.terima_kasih");
				}
				Msg.chat(player, Component.translatable(L + "ayam.hadiah").withStyle(ChatFormatting.GOLD));
			}
			return true;
		}

		@Override
		void akhiri(ServerLevel level) {
			for (AyamKotaEntity c : ayam) {
				if (!c.isRemoved()) {
					c.discard();
				}
			}
			lepas(petani);
		}
	}

	// ================================================================== lost child

	/**
	 * A lost child (42.7.4 "anak tersesat", afternoons): a child of the town (4-10) cries at the far edge of the streets
	 * (a stand-in body with its own face and name; its real body stays indoors meanwhile). A right-click and it follows the
	 * player; brought within 6 blocks of its house, a parent hugs it and the family remembers the help (+15, gossip
	 * MENOLONG). 42B's personal request {@code anak_hilang} is opened for it too. Unfound for 5 minutes, a parent comes
	 * looking and takes it home.
	 */
	static final class AnakTersesat extends Kejadian {
		static final int LAMA = 6000;

		final Warga anak;
		final BlockPos tepi;
		final BlockPos rumah;
		@Nullable KultivatorNpc tubuh;
		@Nullable UUID pengantar;
		@Nullable Warga orangTua;

		private AnakTersesat(TempatHuni huni, long now, Warga anak, BlockPos tepi, BlockPos rumah) {
			super(ANAK, huni, now);
			this.anak = anak;
			this.tepi = tepi;
			this.rumah = rumah;
		}

		static @Nullable AnakTersesat mulai(ServerLevel level, TempatHuni huni, ServerPlayer player, long now) {
			List<Warga> anak = new ArrayList<>();
			for (Warga w : WargaApi.wargaTempat(huni.situsId)) {
				int umur = WargaApi.umur(w);
				if (w.status == Warga.Status.HIDUP && umur >= 4 && umur <= 10 && !bertugas(w)) {
					anak.add(w);
				}
			}
			if (anak.isEmpty()) {
				return null;
			}
			RandomSource r = level.getRandom();
			Warga child = anak.get(r.nextInt(anak.size()));
			BlockPos home = rumah(huni, child);
			// the street spot farthest from the child's house, within 64 blocks of the player
			BlockPos far = null;
			double best = -1.0D;
			for (Titik t : huni.titik("jalan")) {
				if (t.pos().distSqr(player.blockPosition()) > 64.0D * 64.0D) {
					continue;
				}
				double d = t.pos().distSqr(home);
				if (d > best) {
					best = d;
					far = t.pos();
				}
			}
			BlockPos edge = far == null ? null : berdiri(level, far);
			if (edge == null || best < 20.0D * 20.0D) {
				return null;
			}
			AnakTersesat k = new AnakTersesat(huni, now, child, edge, home);
			k.tubuh = wakil(level, huni, child, edge, r.nextFloat() * 360.0F, null);
			if (k.tubuh == null) {
				return null;
			}
			k.tubuh.setHome(edge);
			HidupApi.klip(k.tubuh, GerakId.MENANGIS, 0, 0);
			tugas(child, huni.situsId, Kegiatan.dalam("tersesat"), now + LAMA + 200L);
			Warga ibu = child.ibu == Warga.TANPA ? null : WargaApi.byId(child.ibu);
			Warga ayah = child.ayah == Warga.TANPA ? null : WargaApi.byId(child.ayah);
			k.orangTua = ibu != null && ibu.hidup() ? ibu : ayah != null && ayah.hidup() ? ayah : null;
			PermintaanWarga.buat(child, "anak_hilang");
			seru(k.tubuh, null, "anak.tangis." + r.nextInt(3));
			kabar(level, Vec3.atCenterOf(edge), 32.0D, Component.translatable(L + "anak.dengar"), true);
			return k;
		}

		@Override
		boolean tik(ServerLevel level, long now) {
			if (tubuh == null || tubuh.isRemoved()) {
				return true;
			}
			if (now - mulai >= LAMA && pengantar == null) {
				kabar(level, tubuh.position(), 48.0D, Component.translatable(L + "anak.dijemput", WargaApi.nama(anak)), false);
				return true;
			}
			if ((now - mulai) % 20L != 0L) {
				return false;
			}
			ServerPlayer guide = pengantar == null ? null : level.getServer().getPlayerList().getPlayer(pengantar);
			if (guide == null || guide.level() != level || guide.distanceToSqr(tubuh) > 32.0D * 32.0D) {
				if (pengantar != null) {
					pengantar = null;
					tubuh.getNavigation().stop();
					tubuh.setHome(tubuh.blockPosition());
					HidupApi.klip(tubuh, GerakId.MENANGIS, 0, 0);
					seru(tubuh, null, "anak.ditinggal");
				} else if ((now - mulai) % 200L == 0L) {
					seru(tubuh, null, "anak.tangis." + level.getRandom().nextInt(3));
				}
				return false;
			}
			if (tubuh.distanceToSqr(guide) > 2.5D * 2.5D) {
				tubuh.setHome(guide.blockPosition());
				tubuh.getNavigation().moveTo(guide, 1.0D);
			}
			if (tubuh.blockPosition().distSqr(rumah) <= 6.0D * 6.0D) {
				pulang(level, guide);
				return true;
			}
			return false;
		}

		private void pulang(ServerLevel level, ServerPlayer guide) {
			KultivatorNpc parent = tubuh(huni, orangTua);
			if (parent != null) {
				HidupApi.tatap(parent, tubuh);
				HidupApi.klip(parent, KotaGerakId.PELUK, 0, 80);
				seru(parent, "kota42.terima_kasih", "anak.orang_tua");
			}
			if (tubuh != null) {
				HidupApi.klip(tubuh, KotaGerakId.PELUK, 0, 60);
			}
			if (orangTua != null) {
				WargaApi.peristiwaPemain(guide, situs, "bantu", orangTua);
			}
			Msg.chat(guide, Component.translatable(L + "anak.selesai", WargaApi.nama(anak)).withStyle(ChatFormatting.GOLD));
		}

		@Override
		boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
			if (e != tubuh) {
				return false;
			}
			if (pengantar == null || !pengantar.equals(player.getUUID())) {
				pengantar = player.getUUID();
				HidupApi.tatap(tubuh, player);
				HidupApi.klip(tubuh, GerakId.LAMBAI, 0, 40);
				seru(tubuh, null, "anak.ikut");
				Msg.actionBar(player, Component.translatable(L + "anak.antar", WargaApi.nama(anak)));
			} else {
				seru(tubuh, null, "anak.tanya_jalan");
			}
			return true;
		}

		@Override
		void akhiri(ServerLevel level) {
			lepas(anak);
			if (tubuh != null && !tubuh.isRemoved()) {
				tubuh.vanish();
			}
		}
	}
}
