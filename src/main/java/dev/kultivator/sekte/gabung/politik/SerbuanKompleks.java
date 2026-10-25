package dev.kultivator.sekte.gabung.politik;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Serbuan ke Kompleks (DESIGN 41.6.10, module 41D-P): a righteous or neutral sect with player members is raided by a cult
 * ({@code kultivator.musuh.sect.*}) or by the demonic sect of D nearest its gate, on average every
 * {@code gabungSekte.serbuanHari} (12) days.
 * <p>
 * The raid is <b>live</b> only when a member stands within {@link #JARAK_LIVE} blocks of the compound gate when the omen
 * comes and nothing holds them ({@code Sibuk.why}, the shared enemy pressure {@code Sibuk.enemyRefusal}): the omen scene
 * of 25 ({@code EpicIds.EXTRA_RAID_OMEN}, the raid horn, a 60 s countdown bar), then 2-3 waves of the raiders' band
 * ({@code RogueCultivators}, tier by class + 1, at most 6 alive) march on the gate and channel a breach seal. Holding all
 * waves repels the raid (sect resources +8, 50 PK per wave held to every member who stood by, advancement
 * {@code gabung_bela_gerbang}); a full seal sacks it (resources -15). Nobody named dies and no block changes. Otherwise, or
 * when every member walks away for a minute, the raid is settled as data:
 * {@code P = clamp(0.5 + 0.1 x (tier - tierPenyerbu), 0.2, 0.9)}.
 */
public final class SerbuanKompleks {

	/** Omen length (60 s). */
	public static final int OMEN_TICKS = 1200;
	/** A member this close to the gate at the omen makes the raid live. */
	public static final double JARAK_LIVE = 256.0D;
	/** Members farther than this for {@link #KOSONG_TICKS} abandon the live raid to data. */
	public static final double JARAK_LEPAS = 160.0D;
	public static final long KOSONG_TICKS = 1200L;
	/** Members within this of the gate share the PK of the waves held. */
	public static final double JARAK_IKUT = 96.0D;
	public static final int PK_PER_GELOMBANG = 50;
	public static final double SEGEL_MAKS = 100.0D;
	/** The enemy-pressure id of a live raid (shared daily cap of {@code Sibuk}). */
	public static final String SIBUK_SERBUAN = "gabung.serbuan";

	/** sect id -> its live raid. */
	private static final Map<String, Serbuan> AKTIF = new HashMap<>();
	private static long hariTerakhir = Long.MIN_VALUE;
	private static boolean initialised;

	/** The raiders: a shown name, a tier 1-9, D's sect id ("" for a cult) and the cult index of the omen scene. */
	record Penyerbu(Component nama, int tier, String sekte, int kultus) {
	}

	/** One live raid (session state). */
	static final class Serbuan {
		final String sekte;
		final Penyerbu penyerbu;
		final ServerLevel level;
		final BlockPos gerbang;
		final int gelombangTotal;
		final long omenSampai;
		final Set<UUID> ikut = new HashSet<>();
		final List<Entity> sementara = new ArrayList<>();
		final RandomSource random;
		final ServerBossEvent bar;
		final ServerBossEvent barSegel;
		MisiPerang.@Nullable Regu regu;
		int gelombang;
		int ditahan;
		double segel;
		long jedaSampai = -1L;
		long kosongSejak = -1L;

		Serbuan(String sekte, Penyerbu penyerbu, ServerLevel level, BlockPos gerbang, int gelombangTotal, long now) {
			this.sekte = sekte;
			this.penyerbu = penyerbu;
			this.level = level;
			this.gerbang = gerbang;
			this.gelombangTotal = gelombangTotal;
			this.omenSampai = now + OMEN_TICKS;
			this.random = RandomSource.create(now ^ sekte.hashCode());
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.gabung.serbuan.bar.omen", penyerbu.nama(), 60),
					BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
			this.barSegel = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.gabung.serbuan.bar.segel", 0),
					BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
			this.barSegel.setProgress(0.0F);
			this.barSegel.setVisible(false);
		}

		Vec3 pusat() {
			return Vec3.atBottomCenterOf(gerbang);
		}

		void bersihkan() {
			for (Entity e : sementara) {
				if (!e.isRemoved() && e instanceof RogueCultivatorEntity rogue) {
					rogue.vanish(false);
				}
			}
			sementara.clear();
			bar.removeAllPlayers();
			barSegel.removeAllPlayers();
		}
	}

	/** From {@link PanggilanPerang#init()}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Sibuk.registerEnemy(SIBUK_SERBUAN, player -> {
			for (Serbuan s : AKTIF.values()) {
				if (s.ikut.contains(player.getUUID())) {
					return true;
				}
			}
			return false;
		});
		ServerTickEvents.END_SERVER_TICK.register(SerbuanKompleks::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Serbuan s : List.copyOf(AKTIF.values())) {
				s.bersihkan();
			}
			AKTIF.clear();
			hariTerakhir = Long.MIN_VALUE;
		});
		GabungApi.daftarUji("serbuan", SerbuanKompleks::nodeUji);
	}

	/** True while {@code sekte}'s compound is under a live raid. */
	public static boolean berlangsung(String sekte) {
		return AKTIF.containsKey(sekte);
	}

	// ================================================================== scheduling

	private static void tick(MinecraftServer server) {
		if (!Gabung.aktif()) {
			return;
		}
		int t = server.getTickCount();
		if (t % 100 == 67) {
			long hari = GabungApi.hari(server);
			if (hari != hariTerakhir) {
				boolean first = hariTerakhir == Long.MIN_VALUE;
				hariTerakhir = hari;
				if (!first) {
					harian(server, hari);
				}
			}
		}
		if (t % 10 == 2 && !AKTIF.isEmpty()) {
			for (Serbuan s : List.copyOf(AKTIF.values())) {
				try {
					detik(server, s);
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.error("Gabung compound raid on {} failed", s.sekte, exception);
					selesai(server, s, false, true);
				}
			}
		}
	}

	private static void harian(MinecraftServer server, long hari) {
		if (!GabungConfig.get().serbuanKompleks) {
			return;
		}
		GabungData data = GabungData.get(server);
		for (String sekte : SekteApi.diplomasi().sects()) {
			if (!layak(server, sekte) || AKTIF.containsKey(sekte)) {
				continue;
			}
			GabungData.DataSekteGabung d = data.sekte(sekte);
			if (d.serbuanHari < 0L) {
				// the first sight of a member starts the clock (no raid on the day one joins)
				d.serbuanHari = hari;
				data.setDirty();
				continue;
			}
			if (hari - d.serbuanHari >= jeda(server, sekte, d.serbuanHari)) {
				mulai(server, sekte, false);
			}
		}
	}

	/** Righteous or neutral, known to D, with at least one player member, not absorbed. */
	static boolean layak(MinecraftServer server, String sekte) {
		DiplomasiService d = SekteApi.diplomasi();
		int aliran = d.aliran(sekte);
		if (aliran != 0 && aliran != 1 || d.tier(sekte) <= 0 || DiplomasiGabung.lebur(server, sekte)) {
			return false;
		}
		return !GabungData.get(server).anggotaSekte(sekte).isEmpty();
	}

	/** Days until the next raid after {@code dari}: {@code serbuanHari} x 0.75-1.25, fixed for that start day. */
	private static long jeda(MinecraftServer server, String sekte, long dari) {
		RandomSource random = RandomSource.create(server.overworld().getSeed() ^ dari * 92821L ^ sekte.hashCode() * 31L);
		int base = GabungConfig.get().serbuanHari;
		return Math.max(2L, Math.round(base * (0.75D + random.nextDouble() * 0.5D)));
	}

	/**
	 * Raids {@code sekte} now: live when a member is near and free (or {@code paksaLive} with the tester near), else data.
	 * Returns a short reason when nothing happened.
	 */
	static @Nullable Component mulai(MinecraftServer server, String sekte, boolean paksaLive) {
		if (AKTIF.containsKey(sekte)) {
			return Component.translatable("kultivator.gabung.serbuan.alasan.sudah");
		}
		GabungData data = GabungData.get(server);
		long hari = GabungApi.hari(server);
		data.sekte(sekte).serbuanHari = hari;
		data.setDirty();
		Penyerbu penyerbu = penyerbu(server, sekte, hari);
		BlockPos gerbang = GabungApi.titik(sekte, TitikGabung.GERBANG_KOMPLEKS);
		ServerLevel level = server.overworld();
		List<ServerPlayer> hadir = new ArrayList<>();
		if (gerbang != null) {
			for (UUID uuid : data.anggotaSekte(sekte)) {
				ServerPlayer p = server.getPlayerList().getPlayer(uuid);
				if (p == null || p.level() != level || p.isSpectator() || !p.isAlive()
						|| p.position().distanceToSqr(Vec3.atCenterOf(gerbang)) > JARAK_LIVE * JARAK_LIVE) {
					continue;
				}
				if (!paksaLive && (Sibuk.why(p) != null || Sibuk.enemyRefusal(p) != null)) {
					continue;
				}
				hadir.add(p);
			}
		}
		if (hadir.isEmpty()) {
			hasilData(server, sekte, penyerbu);
			return null;
		}
		int tier = GabungApi.tier(sekte);
		Serbuan s = new Serbuan(sekte, penyerbu, level, gerbang, penyerbu.tier() >= tier ? 3 : 2, level.getGameTime());
		AKTIF.put(sekte, s);
		Vec3 at = s.pusat();
		for (ServerPlayer p : hadir) {
			s.ikut.add(p.getUUID());
			Sibuk.enemyTaken(p, "gabung");
			s.bar.addPlayer(p);
			s.barSegel.addPlayer(p);
			Msg.title(p, Component.translatable("kultivator.gabung.serbuan.omen.judul").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable("kultivator.gabung.serbuan.omen.sub", penyerbu.nama()).withStyle(ChatFormatting.RED), 10, 70, 20);
			p.sendSystemMessage(Component.translatable("kultivator.gabung.serbuan.omen", penyerbu.nama(), PanggilanPerang.nama(sekte), OMEN_TICKS / 20)
					.withStyle(ChatFormatting.DARK_RED));
			double angle = s.random.nextDouble() * Math.PI * 2.0D;
			Vec3 sky = at.add(Math.cos(angle) * 48.0D, 6.0D, Math.sin(angle) * 48.0D);
			EpicHooks.extra(level, EpicIds.EXTRA_RAID_OMEN, p, null, sky, Math.max(0, penyerbu.kultus()), 0);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.7F, 0.6F);
			dev.kultivator.voice.Voice.inner(p, "gabung.panggilan_perang", PanggilanPerang.nama(sekte));
		}
		dev.kultivator.Kultivator.LOGGER.info("Gabung: live raid of {} on {}", penyerbu.sekte().isEmpty() ? "cult " + penyerbu.kultus() : penyerbu.sekte(), sekte);
		return null;
	}

	/** Who raids: the demonic war enemy, else the demonic sect nearest the gate (half the time), else a cult. */
	static Penyerbu penyerbu(MinecraftServer server, String sekte, long hari) {
		DiplomasiService d = SekteApi.diplomasi();
		RandomSource random = RandomSource.create(server.overworld().getSeed() ^ hari * 7349L ^ sekte.hashCode());
		String lawan = PanggilanPerang.lawan(server, sekte);
		String iblis = null;
		if (lawan != null && d.aliran(lawan) == 2) {
			iblis = lawan;
		} else if (random.nextBoolean()) {
			BlockPos gate = PanggilanPerang.gerbang(sekte);
			String near = gate == null ? null : d.nearest(gate, 2);
			if (near != null && !near.equals(sekte)) {
				iblis = near;
			}
		}
		if (iblis != null) {
			Component name = d.name(iblis);
			return new Penyerbu(name == null ? Component.literal(iblis) : name, Math.max(1, d.tier(iblis)), iblis, random.nextInt(RogueCultivatorEntity.SECTS));
		}
		int kultus = random.nextInt(RogueCultivatorEntity.SECTS);
		int tier = Math.max(1, Math.min(9, GabungApi.tier(sekte) + random.nextInt(3) - 1));
		return new Penyerbu(Component.translatable("kultivator.musuh.sect." + kultus), tier, "", kultus);
	}

	/** The raid settled as data (no member near, or the live raid abandoned). */
	static void hasilData(MinecraftServer server, String sekte, Penyerbu penyerbu) {
		int tier = GabungApi.tier(sekte);
		double peluang = Math.max(0.2D, Math.min(0.9D, 0.5D + 0.1D * (tier - penyerbu.tier())));
		RandomSource random = RandomSource.create(server.overworld().getSeed() ^ GabungApi.hari(server) * 4219L ^ sekte.hashCode() * 13L);
		boolean tahan = random.nextDouble() < peluang;
		DiplomasiGabung.ubahSumber(server, sekte, tahan ? 5 : -10, tahan ? "serbuan_tahan" : "serbuan_jebol");
		kabar(server, sekte, penyerbu, tahan, false);
	}

	/** History line for every member; chat for those online. */
	private static void kabar(MinecraftServer server, String sekte, Penyerbu penyerbu, boolean tahan, boolean live) {
		GabungData data = GabungData.get(server);
		long hari = GabungApi.hari(server);
		String key = "kultivator.gabung.serbuan." + (live ? "live." : "data.") + (tahan ? "tahan" : "jebol");
		for (UUID uuid : data.anggotaSekte(sekte)) {
			Keanggotaan k = data.peek(uuid);
			if (k != null) {
				k.catat(hari, "kultivator.gabung.riwayat.serbuan_" + (tahan ? "tahan" : "jebol"), penyerbu.sekte().isEmpty()
						? "musuh." + penyerbu.kultus() : penyerbu.sekte());
			}
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			if (p != null) {
				p.sendSystemMessage(Component.translatable(key, penyerbu.nama(), PanggilanPerang.nama(sekte))
						.withStyle(tahan ? ChatFormatting.GREEN : ChatFormatting.DARK_RED));
			}
		}
		data.setDirty();
	}

	// ================================================================== the live raid

	private static void detik(MinecraftServer server, Serbuan s) {
		long now = s.level.getGameTime();
		List<ServerPlayer> dekat = new ArrayList<>();
		for (UUID uuid : GabungData.get(server).anggotaSekte(s.sekte)) {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			if (p != null && p.level() == s.level && p.isAlive() && p.position().distanceToSqr(s.pusat()) <= JARAK_LEPAS * JARAK_LEPAS) {
				dekat.add(p);
				if (s.ikut.add(p.getUUID())) {
					s.bar.addPlayer(p);
					s.barSegel.addPlayer(p);
				}
			}
		}
		if (dekat.isEmpty()) {
			if (s.kosongSejak < 0L) {
				s.kosongSejak = now;
			} else if (now - s.kosongSejak > KOSONG_TICKS) {
				AKTIF.remove(s.sekte);
				s.bersihkan();
				hasilData(server, s.sekte, s.penyerbu);
				return;
			}
		} else {
			s.kosongSejak = -1L;
		}
		if (now < s.omenSampai) {
			int left = (int) ((s.omenSampai - now) / 20L);
			s.bar.setName(Component.translatable("kultivator.gabung.serbuan.bar.omen", s.penyerbu.nama(), left));
			s.bar.setProgress((float) (s.omenSampai - now) / OMEN_TICKS);
			return;
		}
		if (s.regu == null) {
			int realm = 1;
			for (ServerPlayer p : dekat) {
				realm = Math.max(realm, dev.kultivator.data.Cultivators.get(p).realm().ordinal());
			}
			realm = Math.max(1, Math.min(8, realm + Integer.signum(s.penyerbu.tier() - GabungApi.tier(s.sekte))));
			int variant = s.penyerbu.sekte().isEmpty() || SekteApi.diplomasi().aliran(s.penyerbu.sekte()) == 2
					? RogueCultivators.VARIANT_DEMONIC : RogueCultivators.VARIANT_ROGUE;
			s.regu = new MisiPerang.Regu(s.level, realm, variant, RogueCultivators.ROLE_QUEST, s.gerbang, s.penyerbu.sekte());
			s.barSegel.setVisible(true);
			gelombangBerikut(s, dekat);
		}
		MisiPerang.Regu r = s.regu;
		if (s.jedaSampai > 0L) {
			if (now >= s.jedaSampai) {
				s.jedaSampai = -1L;
				gelombangBerikut(s, dekat);
			}
			return;
		}
		BlockPos asal = s.penyerbu.sekte().isEmpty() ? null : PanggilanPerang.gerbang(s.penyerbu.sekte());
		double arah = asal == null ? s.random.nextDouble() * Math.PI * 2.0D : Math.atan2(asal.getZ() - s.gerbang.getZ(), asal.getX() - s.gerbang.getX());
		r.tick(s.pusat(), 26.0D, 34.0D, arah, Math.PI / 2.5D, s.random, s.sementara);
		if (r.gagalTotal()) {
			AKTIF.remove(s.sekte);
			s.bersihkan();
			hasilData(server, s.sekte, s.penyerbu);
			return;
		}
		for (Mob mob : r.hidup) {
			if (mob instanceof RogueCultivatorEntity rogue && rogue.isAlive() && rogue.isChanneling()
					&& rogue.position().distanceToSqr(s.pusat()) <= 100.0D) {
				s.segel = Math.min(SEGEL_MAKS, s.segel + 0.5D * rogue.tier());
				s.level.sendParticles(ParticleTypes.ENCHANTED_HIT, rogue.getX(), rogue.getY() + 1.2D, rogue.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.01D);
			}
		}
		s.barSegel.setProgress((float) (s.segel / SEGEL_MAKS));
		s.barSegel.setName(Component.translatable("kultivator.gabung.serbuan.bar.segel", (int) s.segel));
		s.bar.setName(Component.translatable("kultivator.gabung.serbuan.bar.gelombang", s.penyerbu.nama(), s.gelombang, s.gelombangTotal, r.sisa()));
		s.bar.setProgress(Math.max(0.0F, Math.min(1.0F, ((s.gelombang - 1) + (r.dimunculkan() == 0 ? 0.0F
				: 1.0F - (float) r.sisa() / Math.max(1, r.dimunculkan()))) / s.gelombangTotal)));
		if (s.segel >= SEGEL_MAKS) {
			selesai(server, s, false, false);
			return;
		}
		if (r.habis()) {
			s.ditahan = s.gelombang;
			if (s.gelombang >= s.gelombangTotal) {
				selesai(server, s, true, false);
				return;
			}
			s.jedaSampai = now + 240L;
			for (ServerPlayer p : dekat) {
				p.sendSystemMessage(Component.translatable("kultivator.gabung.serbuan.tahan_gelombang", s.gelombang).withStyle(ChatFormatting.GREEN));
			}
		}
	}

	private static void gelombangBerikut(Serbuan s, List<ServerPlayer> dekat) {
		s.gelombang++;
		int k = GabungApi.kelas(s.sekte) + 1;
		int[] tiers = switch (s.gelombang) {
			case 1 -> k >= 3 ? new int[]{1, 1, 1, 2} : new int[]{1, 1, 1};
			case 2 -> k >= 4 ? new int[]{1, 2, 2, 2} : new int[]{1, 1, 2, 2};
			default -> k >= 5 ? new int[]{2, 2, 3, 4} : k >= 3 ? new int[]{1, 2, 2, 3} : new int[]{1, 2, 2, 2};
		};
		if (s.regu != null) {
			s.regu.isi(tiers);
		}
		for (ServerPlayer p : dekat) {
			Msg.title(p, Component.translatable("kultivator.gabung.misi_perang.gelombang", s.gelombang, s.gelombangTotal).withStyle(ChatFormatting.RED),
					Component.translatable("kultivator.gabung.misi_perang.gelombang.sub", s.penyerbu.nama()).withStyle(ChatFormatting.GRAY), 5, 40, 10);
		}
		s.level.playSound(null, s.gerbang, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.9F, 0.8F + 0.1F * s.gelombang);
	}

	/** Ends a live raid: repelled ({@code tahan}) or sacked; {@code galat}: aborted without result. */
	private static void selesai(MinecraftServer server, Serbuan s, boolean tahan, boolean galat) {
		AKTIF.remove(s.sekte);
		s.bersihkan();
		if (galat) {
			return;
		}
		DiplomasiGabung.ubahSumber(server, s.sekte, tahan ? 8 : -15, tahan ? "serbuan_tahan" : "serbuan_jebol");
		int pk = PK_PER_GELOMBANG * (tahan ? s.gelombangTotal : s.ditahan);
		for (UUID uuid : s.ikut) {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			if (p == null || p.level() != s.level || p.position().distanceToSqr(s.pusat()) > JARAK_IKUT * JARAK_IKUT) {
				continue;
			}
			if (pk > 0) {
				GabungApi.tambahPk(p, pk, "gabung.serbuan");
			}
			if (tahan) {
				Advancements.grant(p, "sekte/gabung_bela_gerbang");
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.0F);
			}
			Msg.title(p, Component.translatable(tahan ? "kultivator.gabung.serbuan.judul.tahan" : "kultivator.gabung.serbuan.judul.jebol")
							.withStyle(tahan ? ChatFormatting.GOLD : ChatFormatting.DARK_RED),
					Component.translatable("kultivator.gabung.serbuan.pk", pk).withStyle(ChatFormatting.GRAY), 10, 60, 20);
		}
		kabar(server, s.sekte, s.penyerbu, tahan, true);
	}

	// ================================================================== test command

	/** {@code uji gabung serbuan [live]}: a raid on the tester's sect now (data, or live with the tester as defender). */
	private static LiteralArgumentBuilder<CommandSourceStack> nodeUji() {
		return Commands.literal("serbuan")
				.executes(ctx -> uji(ctx.getSource(), false))
				.then(Commands.literal("live").executes(ctx -> uji(ctx.getSource(), true)));
	}

	private static int uji(CommandSourceStack source, boolean live) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		String sekte = GabungApi.sekte(player.getUUID());
		if (sekte == null) {
			source.sendFailure(Component.translatable("kultivator.gabung.perang.status.bukan"));
			return 0;
		}
		MinecraftServer server = source.getServer();
		if (!live) {
			Penyerbu p = penyerbu(server, sekte, GabungApi.hari(server));
			hasilData(server, sekte, p);
			return 1;
		}
		if (GabungApi.titik(sekte, TitikGabung.GERBANG_KOMPLEKS) == null) {
			source.sendFailure(Component.translatable("kultivator.gabung.misi_perang.alasan.gerbang"));
			return 0;
		}
		Component why = mulai(server, sekte, true);
		if (why != null) {
			source.sendFailure(why);
			return 0;
		}
		if (!AKTIF.containsKey(sekte)) {
			source.sendFailure(Component.translatable("kultivator.gabung.serbuan.alasan.jauh", (int) JARAK_LIVE));
			return 0;
		}
		return 1;
	}

	private SerbuanKompleks() {
	}
}
