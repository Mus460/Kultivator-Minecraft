package dev.kultivator.sekte.gabung.politik;

import com.mojang.math.Transformation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiEntities;
import dev.kultivator.dunia.tempat.ekonomi.GerobakEntity;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The five war missions of DESIGN 41.6.10 (module 41D-P), on the board's war tab ({@code gabung_papan}, tab
 * {@code perang}) and in chat ({@code /kultivasi gabung perang}):
 * <table>
 * <tr><th>Mission</th><th>Place</th><th>Content</th><th>Score</th><th>PK</th></tr>
 * <tr><td>Bela Gerbang</td><td>own compound gate</td><td>3 waves of the enemy's band march on the gate and seal it (at most 6
 * alive); the gate seal at 100 % loses</td><td>+15 / -10</td><td>50 x wave</td></tr>
 * <tr><td>Serbu Pos Depan</td><td>gate-to-gate line, 40 % from the enemy gate</td><td>a camp (tent, fire, stores, banner pole:
 * display entities) with 4 guards; beat them and pull the banner (hold 3 s)</td><td>+20</td><td>200</td></tr>
 * <tr><td>Duel Juara</td><td>own compound arena</td><td>Duel Pedang (35) against the enemy's Eldest Senior or Young Elder of the
 * 39F roster, stopping at 10 %; a beaten champion is wounded, never killed</td><td>+25 / -15</td><td>300</td></tr>
 * <tr><td>Jemput Tawanan</td><td>enemy camp, 25 % from the enemy gate</td><td>1-2 own disciples kneel in cages behind 2-3 guards;
 * beat the guards, open the cages</td><td>+10</td><td>150</td></tr>
 * <tr><td>Kawal Bekal</td><td>road from the compound toward the nearest town</td><td>escort a supply cart ({@code kultivator:gerobak})
 * 140 blocks through two ambushes</td><td>+10</td><td>100</td></tr>
 * </table>
 * One mission at a time per player, each kind once per world day; accepting goes through the busy door and holds the player
 * in {@code gabung.perang} while it runs. Bands are rogue cultivators ({@code RogueCultivators.spawn}, the enemy's banner in
 * the off hand); bodies are D's sect bodies ({@code DiplomasiMandiri.tubuh}, tracked under {@code DuniaBudget} kind
 * {@code gabung}); camps are vanilla display and interaction entities tagged {@link #TAG}, never saved (refused when a chunk
 * loads them again). No block ever changes and no named character dies. Everything here is session state: a restart ends
 * a running mission without score.
 */
public final class MisiPerang {

	/** The five mission kinds with their score (win / loss) and PK. */
	public enum Jenis {
		BELA_GERBANG("bela_gerbang", 15, -10, 50, "C"),
		SERBU_POS("serbu_pos", 20, 0, 200, "B"),
		DUEL_JUARA("duel_juara", 25, -15, 300, "A"),
		JEMPUT_TAWANAN("jemput_tawanan", 10, 0, 150, "C"),
		KAWAL_BEKAL("kawal_bekal", 10, 0, 100, "D");

		private final String id;
		private final int skorMenang;
		private final int skorKalah;
		private final int pk;
		private final String mutu;

		Jenis(String id, int skorMenang, int skorKalah, int pk, String mutu) {
			this.id = id;
			this.skorMenang = skorMenang;
			this.skorKalah = skorKalah;
			this.pk = pk;
			this.mutu = mutu;
		}

		public String id() {
			return id;
		}

		/** War score on a win. */
		public int skorMenang() {
			return skorMenang;
		}

		/** War score on a loss (0: a failed attempt costs nothing). */
		public int skorKalah() {
			return skorKalah;
		}

		/** PK of a win (Bela Gerbang: per wave held). */
		public int pk() {
			return pk;
		}

		/** The board's mission rank letter. */
		public String mutu() {
			return mutu;
		}

		public MutableComponent nama() {
			return Component.translatable("kultivator.gabung.misi_perang." + id);
		}

		public MutableComponent teks() {
			return Component.translatable("kultivator.gabung.misi_perang." + id + ".teks");
		}

		public static @Nullable Jenis byId(@Nullable String id) {
			for (Jenis j : values()) {
				if (j.id.equals(id)) {
					return j;
				}
			}
			return null;
		}

		public static List<String> ids() {
			List<String> out = new ArrayList<>();
			for (Jenis j : values()) {
				out.add(j.id);
			}
			return out;
		}
	}

	/** Scoreboard tag of every display/interaction entity of 41D-P (refused when loaded again from a chunk). */
	public static final String TAG = "kultivator_gabung_perang";
	/** Living band members at once (41.12.3: at most 6 squads alive per gate). */
	public static final int REGU_MAKS = 6;
	/** A mission not begun within a world day lapses. */
	public static final long KEDALUWARSA = 24000L;
	/** Ticks the player may stay away from a running mission before it is lost. */
	public static final long JAUH_TICKS = 600L;

	/** Player bagian keys. */
	static final String AKTIF = "aktif";
	static final String HARI = "hari";
	static final String TANDA = "tanda";
	static final String JUMLAH = "jumlah";

	private static final Map<UUID, Jalan> JALAN = new HashMap<>();
	private static boolean initialised;

	/** From {@link PanggilanPerang#init()}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(MisiPerang::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Jalan j : List.copyOf(JALAN.values())) {
				j.bersihkan();
			}
			JALAN.clear();
		});
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD || !entity.entityTags().contains(TAG));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !entity.entityTags().contains(TAG)) {
				return InteractionResult.PASS;
			}
			Jalan j = JALAN.get(sp.getUUID());
			if (j != null && j.sentuh(sp, entity)) {
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
		DuelEndListeners.add(MisiPerang::duelUsai);
		GabungBus.KELUAR.register((player, sekte, status) -> batalPemain(player, "kultivator.gabung.misi_perang.batal.keluar"));
	}

	// ================================================================== accepting

	/** Accepts war mission {@code id} (op {@code terima:ID} of the board, the chat button). Null when accepted, else why not. */
	public static @Nullable Component terima(ServerPlayer player, String id) {
		Jenis jenis = Jenis.byId(id);
		if (jenis == null) {
			return Component.translatable("kultivator.gabung.misi_perang.alasan.jenis");
		}
		Component why = alasan(player, jenis);
		if (why != null) {
			return why;
		}
		String sibuk = Sibuk.why(player);
		if (sibuk != null) {
			return Sibuk.text(sibuk);
		}
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		String lawan = PanggilanPerang.lawan(server, sekte);
		Jalan j = buat(player, jenis, sekte, lawan, false);
		if (j == null) {
			return Component.translatable("kultivator.gabung.misi_perang.alasan.tempat");
		}
		mulaiJalan(player, j);
		return null;
	}

	/** Gives up the running war mission (no score, no PK; the kind stays done for today). */
	public static @Nullable Component batal(ServerPlayer player) {
		Jalan j = JALAN.get(player.getUUID());
		if (j == null) {
			return Component.translatable("kultivator.gabung.misi_perang.alasan.tidak_ada");
		}
		akhiri(player.level().getServer(), j, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.sendiri");
		return null;
	}

	/** Test command: mission {@code id} right next to the tester (no walk; the sect must be at war). */
	static @Nullable Component ujiMulai(ServerPlayer player, String id) {
		Jenis jenis = Jenis.byId(id);
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		String lawan = PanggilanPerang.lawan(server, sekte);
		if (jenis == null || sekte == null || lawan == null) {
			return Component.translatable("kultivator.gabung.perang.status.damai");
		}
		Jalan old = JALAN.get(player.getUUID());
		if (old != null) {
			akhiri(server, old, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.sendiri");
		}
		Jalan j = buat(player, jenis, sekte, lawan, true);
		if (j == null) {
			return Component.translatable("kultivator.gabung.misi_perang.alasan.tempat");
		}
		mulaiJalan(player, j);
		return null;
	}

	private static void mulaiJalan(ServerPlayer player, Jalan j) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		CompoundTag mine = k.bagian(PanggilanPerang.BAGIAN);
		mine.putString(AKTIF, j.jenis.id);
		data.setDirty();
		JALAN.put(player.getUUID(), j);
		player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.terima", j.jenis.nama(), PanggilanPerang.nama(j.lawan))
				.withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(j.jenis.teks().withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.tuju", j.titik.getX(), j.titik.getZ(),
				(int) Math.sqrt(player.blockPosition().distSqr(j.titik))).withStyle(ChatFormatting.YELLOW));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7F, 1.0F);
	}

	/** Why {@code player} may not take {@code jenis} now, or null. */
	static @Nullable Component alasan(ServerPlayer player, Jenis jenis) {
		MinecraftServer server = player.level().getServer();
		if (!Gabung.aktif()) {
			return Component.translatable("kultivator.gabung.cmd.mati");
		}
		String sekte = GabungApi.sekte(player.getUUID());
		if (sekte == null) {
			return Component.translatable("kultivator.gabung.perang.status.bukan");
		}
		String lawan = PanggilanPerang.lawan(server, sekte);
		if (lawan == null) {
			return Component.translatable("kultivator.gabung.perang.status.damai");
		}
		if (JALAN.containsKey(player.getUUID())) {
			return Component.translatable("kultivator.gabung.misi_perang.alasan.berjalan");
		}
		Keanggotaan k = GabungData.get(server).peek(player.getUUID());
		if (k != null && k.bagian(PanggilanPerang.BAGIAN).getCompoundOrEmpty(HARI).getLongOr(jenis.id, -1L) == GabungApi.hari(server)) {
			return Component.translatable("kultivator.gabung.misi_perang.alasan.hari_ini");
		}
		switch (jenis) {
			case BELA_GERBANG, KAWAL_BEKAL -> {
				if (GabungApi.titik(sekte, TitikGabung.GERBANG_KOMPLEKS) == null) {
					return Component.translatable("kultivator.gabung.misi_perang.alasan.gerbang");
				}
			}
			case SERBU_POS -> {
				if (PanggilanPerang.gerbang(sekte) == null || PanggilanPerang.gerbang(lawan) == null) {
					return Component.translatable("kultivator.gabung.misi_perang.alasan.garis");
				}
			}
			case JEMPUT_TAWANAN -> {
				if (PanggilanPerang.gerbang(sekte) == null || PanggilanPerang.gerbang(lawan) == null) {
					return Component.translatable("kultivator.gabung.misi_perang.alasan.garis");
				}
				if (PanggilanPerang.tawanan(server, sekte) <= 0) {
					return Component.translatable("kultivator.gabung.misi_perang.alasan.tawanan");
				}
			}
			case DUEL_JUARA -> {
				if (juara(server, lawan) == null) {
					return Component.translatable("kultivator.gabung.misi_perang.alasan.juara");
				}
				if (!DuelSessions.room()) {
					return Component.translatable("kultivator.duel.reason.full");
				}
			}
		}
		return null;
	}

	/** The enemy champion for Duel Juara: its Eldest Senior (else Young Elder, else Core) of the 39F roster, healthy. */
	static Roster.@Nullable Tokoh juara(MinecraftServer server, String lawan) {
		long hari = GabungApi.hari(server);
		Roster.Tokoh best = null;
		int bestRank = -1;
		for (Roster.Tokoh t : Roster.daftar(server, lawan)) {
			if (!t.aktif() || t.luka(hari)) {
				continue;
			}
			int prio = switch (t.peran()) {
				case "kakak" -> 3;
				case "tetua" -> 2;
				case "inti" -> 1;
				default -> -1;
			};
			if (prio < 0) {
				continue;
			}
			int rank = prio * 1000 + t.rank().step();
			if (rank > bestRank) {
				bestRank = rank;
				best = t;
			}
		}
		return best;
	}

	/** Builds the mission run with its place; null when the place cannot be found. */
	private static @Nullable Jalan buat(ServerPlayer player, Jenis jenis, String sekte, String lawan, boolean uji) {
		ServerLevel level = player.level().getServer().overworld();
		if (uji) {
			level = player.level();
		}
		BlockPos here = player.blockPosition();
		Vec3 depan = Vec3.directionFromRotation(0.0F, player.getYRot());
		BlockPos ahead = BlockPos.containing(player.getX() + depan.x * 14.0D, player.getY(), player.getZ() + depan.z * 14.0D);
		return switch (jenis) {
			case BELA_GERBANG -> {
				BlockPos t = uji ? here : GabungApi.titik(sekte, TitikGabung.GERBANG_KOMPLEKS);
				yield t == null ? null : new BelaGerbang(player, sekte, lawan, level, t, uji);
			}
			case SERBU_POS -> {
				BlockPos t = uji ? ahead : garis(sekte, lawan, 0.60D);
				yield t == null ? null : new SerbuPos(player, sekte, lawan, level, t, uji);
			}
			case DUEL_JUARA -> {
				BlockPos t = uji ? here : GabungApi.titik(sekte, TitikGabung.ARENA);
				Roster.Tokoh champ = juara(player.level().getServer(), lawan);
				yield t == null || champ == null ? null : new DuelJuara(player, sekte, lawan, level, t, uji, champ);
			}
			case JEMPUT_TAWANAN -> {
				BlockPos t = uji ? ahead : garis(sekte, lawan, 0.75D);
				int n = Math.max(1, Math.min(PanggilanPerang.TAWANAN_MAKS, PanggilanPerang.tawanan(player.level().getServer(), sekte)));
				yield t == null ? null : new JemputTawanan(player, sekte, lawan, level, t, uji, n);
			}
			case KAWAL_BEKAL -> {
				BlockPos t = uji ? here : GabungApi.titik(sekte, TitikGabung.GERBANG_KOMPLEKS);
				yield t == null ? null : new KawalBekal(player, sekte, lawan, level, t, uji);
			}
		};
	}

	/** The point at {@code f} (0 = own gate, 1 = enemy gate) on the gate-to-gate line, or null. */
	static @Nullable BlockPos garis(String sekte, String lawan, double f) {
		BlockPos a = PanggilanPerang.gerbang(sekte);
		BlockPos b = PanggilanPerang.gerbang(lawan);
		if (a == null || b == null) {
			return null;
		}
		return new BlockPos(Mth.floor(Mth.lerp(f, a.getX(), b.getX())), 64, Mth.floor(Mth.lerp(f, a.getZ(), b.getZ())));
	}

	// ================================================================== the running missions

	enum Tahap { MENUJU, BERLANGSUNG }

	enum Hasil { MENANG, KALAH, BATAL }

	/** One running war mission of one player (session state). */
	abstract static class Jalan {
		final UUID pemain;
		final String sekte;
		final String lawan;
		final Jenis jenis;
		final ServerLevel level;
		BlockPos titik;
		final boolean uji;
		long dibuat;
		final RandomSource random;
		final List<Entity> sementara = new ArrayList<>();
		Tahap tahap = Tahap.MENUJU;
		long jauhSejak = -1L;
		long petunjuk;
		@Nullable ServerBossEvent bar;

		Jalan(ServerPlayer player, Jenis jenis, String sekte, String lawan, ServerLevel level, BlockPos titik, boolean uji) {
			this.pemain = player.getUUID();
			this.jenis = jenis;
			this.sekte = sekte;
			this.lawan = lawan;
			this.level = level;
			this.titik = titik;
			this.uji = uji;
			this.dibuat = level.getGameTime();
			this.random = RandomSource.create(level.getGameTime() ^ pemain.getLeastSignificantBits());
		}

		/** Blocks from {@link #titik} at which the mission begins. */
		double tiba() {
			return 48.0D;
		}

		/** Blocks from {@link #titik} the player may stray while it runs. */
		double lepas() {
			return 96.0D;
		}

		/** Ticks a running mission may last. */
		long batasWaktu() {
			return 20L * 60L * 10L;
		}

		/** The player arrived: set the scene. False when it could not (then retried). */
		abstract boolean mulai(ServerPlayer player);

		/** Every 10 ticks while running. */
		abstract void detik(ServerPlayer player, long now);

		/** The player used one of this run's tagged entities; true when it was handled. */
		boolean sentuh(ServerPlayer player, Entity entity) {
			return false;
		}

		/** A duel of this run ended (Duel Juara). */
		void duel(DuelEndInfo info) {
		}

		/** PK of the result (default: the kind's PK on a win). */
		int pk(Hasil hasil) {
			return hasil == Hasil.MENANG ? jenis.pk : 0;
		}

		/** Removes every entity of the run (bands vanish in a puff, displays and bodies go). */
		void bersihkan() {
			for (Entity e : sementara) {
				if (!e.isRemoved()) {
					if (e instanceof DiscipleEntity body) {
						DiplomasiMandiri.hilang(body);
					} else if (e instanceof RogueCultivatorEntity rogue) {
						rogue.vanish(false);
					} else {
						if (e.level() instanceof ServerLevel l) {
							l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5D, e.getZ(), 3, 0.3D, 0.3D, 0.3D, 0.01D);
						}
						e.discard();
					}
				}
			}
			sementara.clear();
			if (bar != null) {
				bar.removeAllPlayers();
				bar = null;
			}
		}

		ServerBossEvent bar(ServerPlayer player, Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
			if (bar == null) {
				bar = new ServerBossEvent(UUID.randomUUID(), name, color, overlay);
				bar.addPlayer(player);
			}
			return bar;
		}

		Vec3 pusat() {
			return Vec3.atBottomCenterOf(titik);
		}
	}

	/** A duel's end as heard from {@link DuelEndListeners}. */
	record DuelEndInfo(@Nullable LivingEntity winner, @Nullable LivingEntity loser) {
	}

	private static void tick(MinecraftServer server) {
		if (JALAN.isEmpty() || server.getTickCount() % 10 != 5) {
			return;
		}
		for (Jalan j : List.copyOf(JALAN.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(j.pemain);
			if (player == null) {
				akhiri(server, j, Hasil.BATAL, null);
				continue;
			}
			long now = j.level.getGameTime();
			try {
				if (j.tahap == Tahap.MENUJU) {
					menuju(server, player, j, now);
				} else {
					berlangsung(server, player, j, now);
				}
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Gabung war mission {} failed", j.jenis.id, exception);
				akhiri(server, j, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.galat");
			}
		}
	}

	private static void menuju(MinecraftServer server, ServerPlayer player, Jalan j, long now) {
		if (now - j.dibuat > KEDALUWARSA) {
			akhiri(server, j, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.lewat");
			return;
		}
		double dx = player.getX() - (j.titik.getX() + 0.5D);
		double dz = player.getZ() - (j.titik.getZ() + 0.5D);
		double jarak = Math.sqrt(dx * dx + dz * dz);
		if (player.level() != j.level || jarak > j.tiba()) {
			if (now >= j.petunjuk) {
				j.petunjuk = now + 1200L;
				Msg.actionBar(player, Component.translatable("kultivator.gabung.misi_perang.petunjuk", j.jenis.nama(), (int) jarak, arah(-dx, -dz))
						.withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		if (!j.level.isPositionEntityTicking(j.titik.atY(player.getBlockY()))) {
			return;
		}
		String sibuk = Sibuk.whyExcept(player, Gabung.SIBUK_PERANG);
		if (sibuk != null) {
			if (now >= j.petunjuk) {
				j.petunjuk = now + Sibuk.RETRY_TICKS;
				Msg.actionBar(player, Sibuk.text(sibuk).copy().withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (!j.mulai(player)) {
			return;
		}
		j.tahap = Tahap.BERLANGSUNG;
		j.dibuat = now;
		GabungApi.sibuk(player, Gabung.SIBUK_PERANG, (int) Math.min(Integer.MAX_VALUE, j.batasWaktu() + 200L));
		Msg.title(player, j.jenis.nama().withStyle(ChatFormatting.GOLD), Component.translatable("kultivator.gabung.misi_perang.mulai",
				PanggilanPerang.nama(j.lawan)).withStyle(ChatFormatting.YELLOW), 10, 50, 15);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.5F, 1.1F);
	}

	private static void berlangsung(MinecraftServer server, ServerPlayer player, Jalan j, long now) {
		if (!player.isAlive()) {
			akhiri(server, j, Hasil.KALAH, "kultivator.gabung.misi_perang.kalah.roboh");
			return;
		}
		if (now - j.dibuat > j.batasWaktu()) {
			akhiri(server, j, Hasil.KALAH, "kultivator.gabung.misi_perang.kalah.waktu");
			return;
		}
		double r = j.lepas();
		boolean jauh = player.level() != j.level || player.position().distanceToSqr(j.pusat()) > r * r;
		if (jauh) {
			if (j.jauhSejak < 0L) {
				j.jauhSejak = now;
				Msg.actionBar(player, Component.translatable("kultivator.gabung.misi_perang.jauh").withStyle(ChatFormatting.RED));
			} else if (now - j.jauhSejak > JAUH_TICKS) {
				akhiri(server, j, Hasil.KALAH, "kultivator.gabung.misi_perang.kalah.lari");
				return;
			}
		} else {
			j.jauhSejak = -1L;
		}
		j.detik(player, now);
	}

	/** Ends a run: score, PK, history, the day's lock, the busy state, cleanup. */
	static void akhiri(MinecraftServer server, Jalan j, Hasil hasil, @Nullable String pesan) {
		if (JALAN.get(j.pemain) != j) {
			j.bersihkan();
			return;
		}
		JALAN.remove(j.pemain);
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(j.pemain);
		long hari = GabungApi.hari(server);
		if (k != null) {
			CompoundTag mine = k.bagian(PanggilanPerang.BAGIAN);
			mine.remove(AKTIF);
			if (j.tahap == Tahap.BERLANGSUNG || hasil != Hasil.BATAL) {
				CompoundTag days = mine.getCompoundOrEmpty(HARI);
				days.putLong(j.jenis.id, hari);
				mine.put(HARI, days);
			}
			if (hasil != Hasil.BATAL) {
				CompoundTag p = PanggilanPerang.perang(server, j.sekte);
				String tanda = p == null ? "" : j.sekte + ":" + p.getLongOr(PanggilanPerang.MULAI, 0L);
				if (!tanda.equals(mine.getStringOr(TANDA, ""))) {
					mine.putString(TANDA, tanda);
					mine.put(JUMLAH, new CompoundTag());
				}
				CompoundTag n = mine.getCompoundOrEmpty(JUMLAH);
				n.putInt(j.jenis.id, n.getIntOr(j.jenis.id, 0) + 1);
				mine.put(JUMLAH, n);
				k.catat(hari, "kultivator.gabung.riwayat.misi_perang", j.jenis.id, hasil == Hasil.MENANG ? "menang" : "kalah");
			}
			data.setDirty();
		}
		ServerPlayer player = server.getPlayerList().getPlayer(j.pemain);
		if (player != null) {
			GabungApi.lepasSibuk(player, Gabung.SIBUK_PERANG);
		}
		if (hasil != Hasil.BATAL) {
			int skor = hasil == Hasil.MENANG ? j.jenis.skorMenang : j.jenis.skorKalah;
			int pk = j.pk(hasil);
			PanggilanPerang.ikutMisi(server, j.sekte, j.pemain);
			if (skor != 0) {
				PanggilanPerang.skor(server, j.sekte, skor, j.jenis.id);
			}
			if (player != null) {
				if (pk > 0) {
					GabungApi.tambahPk(player, pk, "gabung.perang." + j.jenis.id);
				}
				boolean menang = hasil == Hasil.MENANG;
				Msg.title(player, Component.translatable(menang ? "kultivator.gabung.misi_perang.judul.menang" : "kultivator.gabung.misi_perang.judul.kalah")
								.withStyle(menang ? ChatFormatting.GOLD : ChatFormatting.DARK_RED),
						j.jenis.nama().withStyle(ChatFormatting.GRAY), 10, 60, 20);
				player.sendSystemMessage(Component.translatable(menang ? "kultivator.gabung.misi_perang.hasil.menang" : "kultivator.gabung.misi_perang.hasil.kalah",
						j.jenis.nama(), skor, pk).withStyle(menang ? ChatFormatting.GOLD : ChatFormatting.RED));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						menang ? SoundEvents.PLAYER_LEVELUP : SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7F, menang ? 1.1F : 0.6F);
				if (menang && j.jenis == Jenis.BELA_GERBANG) {
					Advancements.grant(player, "sekte/gabung_bela_gerbang");
				}
				String cerita = j.jenis == Jenis.BELA_GERBANG ? "bela" : j.jenis == Jenis.SERBU_POS ? "pos" : j.jenis == Jenis.DUEL_JUARA ? "duel" : null;
				if (menang && cerita != null) {
					dev.kultivator.sekte.gabung.dunia.CeritaGabung.peristiwa(player, "perang:" + cerita); // 41W chain steps
				}
			}
		}
		if (pesan != null && player != null) {
			player.sendSystemMessage(Component.translatable(pesan, j.jenis.nama()).withStyle(hasil == Hasil.BATAL ? ChatFormatting.GRAY : ChatFormatting.RED));
		}
		j.bersihkan();
	}

	/** Ends every run of {@code sekte} (the war ended): no score. */
	static void batalSekte(MinecraftServer server, String sekte) {
		for (Jalan j : List.copyOf(JALAN.values())) {
			if (j.sekte.equals(sekte)) {
				akhiri(server, j, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.usai");
			}
		}
	}

	/** Ends the player's run without score (left the sect). */
	static void batalPemain(ServerPlayer player, String pesan) {
		Jalan j = JALAN.get(player.getUUID());
		if (j != null) {
			akhiri(player.level().getServer(), j, Hasil.BATAL, pesan);
		}
	}

	/** True while {@code player} runs a war mission. */
	public static boolean berjalan(ServerPlayer player) {
		return JALAN.containsKey(player.getUUID());
	}

	/** Lazy upkeep: a mission mark left by a restart is dropped. */
	static void bersihkanBasi(ServerPlayer player, Keanggotaan k) {
		CompoundTag mine = k.bagian(PanggilanPerang.BAGIAN);
		if (mine.contains(AKTIF) && !JALAN.containsKey(player.getUUID())) {
			mine.remove(AKTIF);
		}
	}

	private static void duelUsai(dev.kultivator.duel.session.DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (Jalan j : List.copyOf(JALAN.values())) {
			if (j instanceof DuelJuara d && d.body != null && session.involves(d.body)) {
				// handled on the next tick: the session is still settling while listeners run
				DuelEndInfo info = new DuelEndInfo(winner, loser);
				d.bertarung = false;
				dev.kultivator.voice.Voice.later(1, () -> d.duel(info));
				return;
			}
		}
	}

	// ================================================================== Bela Gerbang

	/** Three waves march on the own gate; the gate seal fills while they channel by it. */
	static final class BelaGerbang extends Jalan {
		static final int GELOMBANG = 3;
		static final double SEGEL_MAKS = 100.0D;
		static final double JANGKAU_SEGEL = 5.0D;
		@Nullable Regu regu;
		int gelombang;
		int bertahan;
		double segel;
		long jedaSampai = -1L;
		@Nullable ServerBossEvent barSegel;

		BelaGerbang(ServerPlayer player, String sekte, String lawan, ServerLevel level, BlockPos titik, boolean uji) {
			super(player, Jenis.BELA_GERBANG, sekte, lawan, level, titik, uji);
		}

		@Override
		boolean mulai(ServerPlayer player) {
			titik = tanah(level, titik.getX(), titik.getZ());
			regu = new Regu(level, ranah(player, sekte, lawan), varian(lawan), RogueCultivators.ROLE_QUEST, titik, lawan);
			gelombang = 0;
			bar(player, Component.translatable("kultivator.gabung.misi_perang.bar.gelombang", 1, GELOMBANG, 0), BossEvent.BossBarColor.RED,
					BossEvent.BossBarOverlay.NOTCHED_10);
			barSegel = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.gabung.misi_perang.bar.segel", 0),
					BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
			barSegel.setProgress(0.0F);
			barSegel.addPlayer(player);
			gelombangBerikut(player);
			return true;
		}

		private void gelombangBerikut(ServerPlayer player) {
			gelombang++;
			int k = GabungApi.kelas(lawan);
			int[] tiers = switch (gelombang) {
				case 1 -> k >= 3 ? new int[]{1, 1, 1, 1} : new int[]{1, 1, 1};
				case 2 -> new int[]{1, 1, 2, 2};
				default -> k >= 3 ? new int[]{1, 2, 2, 3} : new int[]{1, 2, 2, 2};
			};
			regu.isi(tiers);
			Msg.title(player, Component.translatable("kultivator.gabung.misi_perang.gelombang", gelombang, GELOMBANG).withStyle(ChatFormatting.RED),
					Component.translatable("kultivator.gabung.misi_perang.gelombang.sub", PanggilanPerang.nama(lawan)).withStyle(ChatFormatting.GRAY),
					5, 40, 10);
			level.playSound(null, titik, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.8F, 0.8F + 0.1F * gelombang);
		}

		@Override
		void detik(ServerPlayer player, long now) {
			Regu r = regu;
			if (r == null) {
				return;
			}
			if (jedaSampai > 0L) {
				if (now >= jedaSampai) {
					jedaSampai = -1L;
					gelombangBerikut(player);
				}
				return;
			}
			BlockPos musuh = PanggilanPerang.gerbang(lawan);
			double arah = musuh == null ? random.nextDouble() * Math.PI * 2.0D : Math.atan2(musuh.getZ() - titik.getZ(), musuh.getX() - titik.getX());
			r.tick(pusat(), 22.0D, 30.0D, arah, Math.PI / 3.0D, random, sementara);
			if (r.gagalTotal()) {
				akhiri(level.getServer(), this, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.tak_datang");
				return;
			}
			for (Mob mob : r.hidup) {
				if (mob instanceof RogueCultivatorEntity rogue && rogue.isAlive() && rogue.isChanneling()
						&& rogue.position().distanceToSqr(pusat()) <= JANGKAU_SEGEL * JANGKAU_SEGEL * 4.0D) {
					segel = Math.min(SEGEL_MAKS, segel + 0.6D * rogue.tier());
					level.sendParticles(ParticleTypes.ENCHANTED_HIT, rogue.getX(), rogue.getY() + 1.2D, rogue.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.01D);
				}
			}
			if (barSegel != null) {
				barSegel.setProgress((float) (segel / SEGEL_MAKS));
				barSegel.setName(Component.translatable("kultivator.gabung.misi_perang.bar.segel", (int) segel));
			}
			if (bar != null) {
				bar.setName(Component.translatable("kultivator.gabung.misi_perang.bar.gelombang", gelombang, GELOMBANG, r.sisa()));
				bar.setProgress(Math.max(0.0F, Math.min(1.0F, ((gelombang - 1) + (r.dimunculkan() == 0 ? 0.0F
						: 1.0F - (float) r.sisa() / Math.max(1, r.dimunculkan()))) / GELOMBANG)));
			}
			if (segel >= SEGEL_MAKS) {
				akhiri(level.getServer(), this, Hasil.KALAH, "kultivator.gabung.misi_perang.kalah.segel");
				return;
			}
			if (r.habis()) {
				bertahan = gelombang;
				if (gelombang >= GELOMBANG) {
					akhiri(level.getServer(), this, Hasil.MENANG, null);
					return;
				}
				jedaSampai = now + 200L;
				player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.gelombang.tahan", gelombang).withStyle(ChatFormatting.GREEN));
			}
		}

		@Override
		int pk(Hasil hasil) {
			return jenis.pk() * (hasil == Hasil.MENANG ? GELOMBANG : bertahan);
		}

		@Override
		void bersihkan() {
			super.bersihkan();
			if (barSegel != null) {
				barSegel.removeAllPlayers();
				barSegel = null;
			}
		}
	}

	// ================================================================== Serbu Pos Depan

	/** A forward camp with 4 guards; the banner is pulled once they are gone. */
	static final class SerbuPos extends Jalan {
		static final int TARIK_TICK = 60;
		@Nullable Regu regu;
		@Nullable Kemah kemah;
		boolean bebas;
		long tarikMulai = -1L;

		SerbuPos(ServerPlayer player, String sekte, String lawan, ServerLevel level, BlockPos titik, boolean uji) {
			super(player, Jenis.SERBU_POS, sekte, lawan, level, titik, uji);
		}

		@Override
		double tiba() {
			return 56.0D;
		}

		@Override
		double lepas() {
			return 128.0D;
		}

		@Override
		boolean mulai(ServerPlayer player) {
			titik = tanah(level, titik.getX(), titik.getZ());
			BlockPos kita = PanggilanPerang.gerbang(sekte);
			float yaw = kita == null ? player.getYRot() : (float) (Mth.atan2(kita.getZ() - titik.getZ(), kita.getX() - titik.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			kemah = Kemah.bangun(level, titik, yaw, lawan, 0, sementara);
			regu = new Regu(level, ranah(player, sekte, lawan), varian(lawan), RogueCultivators.ROLE_CAMP, titik, lawan);
			regu.isi(GabungApi.kelas(lawan) >= 4 ? new int[]{1, 2, 2, 3} : new int[]{1, 1, 2, 2});
			bar(player, Component.translatable("kultivator.gabung.misi_perang.bar.penjaga", 4), BossEvent.BossBarColor.YELLOW,
					BossEvent.BossBarOverlay.NOTCHED_6);
			return true;
		}

		@Override
		void detik(ServerPlayer player, long now) {
			if (kemah != null && now % 40L < 10L) {
				kemah.asap(level);
			}
			Regu r = regu;
			if (r == null) {
				return;
			}
			if (!bebas) {
				r.tick(pusat(), 4.0D, 7.0D, 0.0D, Math.PI, random, sementara);
				if (r.gagalTotal()) {
					akhiri(level.getServer(), this, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.tak_datang");
					return;
				}
				if (bar != null) {
					bar.setName(Component.translatable("kultivator.gabung.misi_perang.bar.penjaga", r.sisa()));
					bar.setProgress(r.dimunculkan() == 0 ? 1.0F : (float) r.sisa() / Math.max(1, r.dimunculkan()));
				}
				if (r.habis()) {
					bebas = true;
					if (bar != null) {
						bar.setName(Component.translatable("kultivator.gabung.misi_perang.bar.panji"));
						bar.setColor(BossEvent.BossBarColor.GREEN);
						bar.setProgress(0.0F);
					}
					player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.panji.bebas").withStyle(ChatFormatting.GREEN));
				}
				return;
			}
			if (tarikMulai >= 0L && kemah != null && kemah.tiang != null) {
				if (player.position().distanceToSqr(kemah.tiang) > 9.0D) {
					tarikMulai = -1L;
					Msg.actionBar(player, Component.translatable("kultivator.gabung.misi_perang.panji.lepas").withStyle(ChatFormatting.RED));
					return;
				}
				float f = Math.min(1.0F, (now - tarikMulai) / (float) TARIK_TICK);
				if (bar != null) {
					bar.setProgress(f);
				}
				Msg.actionBar(player, Component.translatable("kultivator.gabung.misi_perang.panji.tarik", (int) (f * 100)).withStyle(ChatFormatting.GOLD));
				level.sendParticles(ParticleTypes.CRIT, kemah.tiang.x, kemah.tiang.y + 2.6D, kemah.tiang.z, 3, 0.2D, 0.4D, 0.2D, 0.05D);
				if (f >= 1.0F) {
					ItemStack trofi = DiplomasiMandiri.panji(level, lawan);
					kemah.cabut(level);
					if (!trofi.isEmpty()) {
						PanggilanPerang.beri(player, trofi);
					}
					level.playSound(null, BlockPos.containing(kemah.tiang), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
					akhiri(level.getServer(), this, Hasil.MENANG, null);
				}
			}
		}

		@Override
		boolean sentuh(ServerPlayer player, Entity entity) {
			if (kemah == null || entity != kemah.pegangan) {
				return false;
			}
			if (!bebas) {
				Msg.actionBar(player, Component.translatable("kultivator.gabung.misi_perang.panji.dijaga").withStyle(ChatFormatting.RED));
				return true;
			}
			if (tarikMulai < 0L) {
				tarikMulai = level.getGameTime();
				SosokApi.tatap(player, entity);
			}
			return true;
		}
	}

	// ================================================================== Duel Juara

	/** Duel Pedang against the enemy's champion at the own arena. */
	static final class DuelJuara extends Jalan {
		final Roster.Tokoh juara;
		@Nullable DiscipleEntity body;
		long duelPada = -1L;
		boolean bertarung;

		DuelJuara(ServerPlayer player, String sekte, String lawan, ServerLevel level, BlockPos titik, boolean uji, Roster.Tokoh juara) {
			super(player, Jenis.DUEL_JUARA, sekte, lawan, level, titik, uji);
			this.juara = juara;
		}

		@Override
		double tiba() {
			return 12.0D;
		}

		@Override
		double lepas() {
			return 48.0D;
		}

		@Override
		long batasWaktu() {
			return 20L * 60L * 6L;
		}

		@Override
		boolean mulai(ServerPlayer player) {
			if (!DuniaBudget.mayAdd(level, GabungEntities.ANGGARAN)) {
				return false;
			}
			Vec3 at = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(6.0D));
			Vec3 spot = Vec3.atBottomCenterOf(tanah(level, Mth.floor(at.x), Mth.floor(at.z)));
			body = DiplomasiMandiri.tubuh(level, spot, lawan, juara.rank().step(), juara.slot(), juara.nama(), BlockPos.containing(spot), true);
			if (body == null) {
				return false;
			}
			DuniaBudget.track(GabungEntities.ANGGARAN, body);
			sementara.add(body);
			SosokApi.tatap(body, player);
			SosokApi.gerak(body, GerakId.HORMAT, 0, 50);
			player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.juara.datang", juara.nama(), PanggilanPerang.nama(lawan))
					.withStyle(ChatFormatting.GOLD));
			duelPada = level.getGameTime() + 60L;
			return true;
		}

		@Override
		void detik(ServerPlayer player, long now) {
			if (body == null || body.isRemoved()) {
				akhiri(level.getServer(), this, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.tak_datang");
				return;
			}
			if (duelPada > 0L && now >= duelPada) {
				duelPada = -1L;
				int gap = Cultivators.get(player).realm().ordinal() - body.data().realm().ordinal();
				DuelRuleSet rules = DuelRuleSet.suggested(gap).withoutStake().withCrowd(true).withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN);
				Component why = DuelChallenges.startDirect(player, player, body, DuelIds.PAIR_NPC, rules);
				if (why != null) {
					player.sendSystemMessage(why.copy().withStyle(ChatFormatting.RED));
					akhiri(level.getServer(), this, Hasil.BATAL, null);
					return;
				}
				bertarung = true;
			}
			if (bertarung && DuelSessions.byEntity(body) == null && now - dibuat > 200L) {
				// the session ended without a listener call (cancelled before the fight)
				akhiri(level.getServer(), this, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.duel");
			}
		}

		@Override
		void duel(DuelEndInfo info) {
			if (JALAN.get(pemain) != this) {
				return;
			}
			MinecraftServer server = level.getServer();
			ServerPlayer player = server.getPlayerList().getPlayer(pemain);
			if (info.winner() == null || player == null) {
				akhiri(server, this, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.duel");
				return;
			}
			boolean menang = info.winner() == player;
			if (menang) {
				Roster.lukai(server, juara.id(), 3);
				if (body != null) {
					SosokApi.gerak(body, GerakId.HORMAT, 0, 60);
				}
				player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.juara.kalah", juara.nama()).withStyle(ChatFormatting.GOLD));
			} else if (body != null) {
				SosokApi.gerak(body, GabungGerakId.SALAM_SEKTE, 0, 60);
				player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.juara.menang", juara.nama()).withStyle(ChatFormatting.RED));
			}
			akhiri(server, this, menang ? Hasil.MENANG : Hasil.KALAH, null);
		}
	}

	// ================================================================== Jemput Tawanan

	/** An enemy camp with caged disciples of the own sect behind 2-3 guards. */
	static final class JemputTawanan extends Jalan {
		final int jumlah;
		@Nullable Regu regu;
		@Nullable Kemah kemah;
		final List<DiscipleEntity> tawanan = new ArrayList<>();
		final List<String> rosterId = new ArrayList<>();
		int bebas;
		boolean aman;

		JemputTawanan(ServerPlayer player, String sekte, String lawan, ServerLevel level, BlockPos titik, boolean uji, int jumlah) {
			super(player, Jenis.JEMPUT_TAWANAN, sekte, lawan, level, titik, uji);
			this.jumlah = jumlah;
		}

		@Override
		double tiba() {
			return 56.0D;
		}

		@Override
		double lepas() {
			return 128.0D;
		}

		@Override
		boolean mulai(ServerPlayer player) {
			titik = tanah(level, titik.getX(), titik.getZ());
			BlockPos musuh = PanggilanPerang.gerbang(lawan);
			float yaw = musuh == null ? player.getYRot() + 180.0F
					: (float) (Mth.atan2(musuh.getZ() - titik.getZ(), musuh.getX() - titik.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			kemah = Kemah.bangun(level, titik, yaw, lawan, jumlah, sementara);
			MinecraftServer server = level.getServer();
			List<Roster.Tokoh> murid = new ArrayList<>();
			for (Roster.Tokoh t : Roster.daftar(server, sekte)) {
				if (t.aktif() && ("luar".equals(t.peran()) || "dalam".equals(t.peran()))) {
					murid.add(t);
				}
			}
			for (int i = 0; i < jumlah && i < kemah.sel.size(); i++) {
				if (!DuniaBudget.mayAdd(level, GabungEntities.ANGGARAN)) {
					break;
				}
				Vec3 at = kemah.sel.get(i);
				Roster.Tokoh t = murid.isEmpty() ? null : murid.get(Math.floorMod(i + (int) (GabungApi.hari(server) % 7L), murid.size()));
				Component name = t != null ? t.nama() : Component.translatable("kultivator.gabung.misi_perang.tawanan.nama", PanggilanPerang.nama(sekte));
				int step = t != null ? t.rank().step() : 6 + random.nextInt(6);
				DiscipleEntity body = DiplomasiMandiri.tubuh(level, at, sekte, step, 20 + i, name, BlockPos.containing(at), false);
				if (body == null) {
					continue;
				}
				body.setNoAi(true);
				DuniaBudget.track(GabungEntities.ANGGARAN, body);
				SosokApi.gerak(body, GabungGerakId.LELAH_BERLUTUT, 0, 0);
				sementara.add(body);
				tawanan.add(body);
				rosterId.add(t == null ? "" : t.id());
			}
			if (tawanan.isEmpty()) {
				return false;
			}
			regu = new Regu(level, ranah(player, sekte, lawan), varian(lawan), RogueCultivators.ROLE_CAMP, titik, lawan);
			regu.isi(GabungApi.kelas(lawan) >= 3 ? new int[]{1, 2, 2} : new int[]{1, 1, 2});
			bar(player, Component.translatable("kultivator.gabung.misi_perang.bar.penjaga", regu.sisa()), BossEvent.BossBarColor.YELLOW,
					BossEvent.BossBarOverlay.NOTCHED_6);
			return true;
		}

		@Override
		void detik(ServerPlayer player, long now) {
			if (kemah != null && now % 40L < 10L) {
				kemah.asap(level);
			}
			Regu r = regu;
			if (r == null) {
				return;
			}
			if (!aman) {
				r.tick(pusat(), 4.0D, 8.0D, 0.0D, Math.PI, random, sementara);
				if (r.gagalTotal()) {
					akhiri(level.getServer(), this, Hasil.BATAL, "kultivator.gabung.misi_perang.batal.tak_datang");
					return;
				}
				if (bar != null) {
					bar.setName(Component.translatable("kultivator.gabung.misi_perang.bar.penjaga", r.sisa()));
					bar.setProgress(r.dimunculkan() == 0 ? 1.0F : (float) r.sisa() / Math.max(1, r.dimunculkan()));
				}
				if (r.habis()) {
					aman = true;
					if (bar != null) {
						bar.setName(Component.translatable("kultivator.gabung.misi_perang.bar.sel", 0, tawanan.size()));
						bar.setColor(BossEvent.BossBarColor.GREEN);
						bar.setProgress(0.0F);
					}
					player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.tawanan.aman").withStyle(ChatFormatting.GREEN));
				}
				return;
			}
			MinecraftServer server = level.getServer();
			for (int i = 0; i < tawanan.size(); i++) {
				DiscipleEntity body = tawanan.get(i);
				if (body.isRemoved() || body.entityTags().contains("bebas") || player.distanceToSqr(body) > 9.0D) {
					continue;
				}
				body.addTag("bebas");
				if (kemah != null) {
					kemah.bukaSel(level, i);
				}
				body.setNoAi(false);
				SosokApi.henti(body);
				SosokApi.gerak(body, GerakId.HORMAT, 0, 50);
				SosokApi.tatap(body, player);
				player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.tawanan.terima_kasih", body.getName()).withStyle(ChatFormatting.AQUA));
				level.playSound(null, body.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 0.8F, 1.0F);
				String id = rosterId.get(i);
				if (!id.isEmpty()) {
					Roster.ubahSikap(server, id, pemain, 10);
				}
				bebas++;
				dev.kultivator.voice.Voice.later(70, () -> DiplomasiMandiri.hilang(body));
			}
			if (bar != null) {
				bar.setName(Component.translatable("kultivator.gabung.misi_perang.bar.sel", bebas, tawanan.size()));
				bar.setProgress((float) bebas / Math.max(1, tawanan.size()));
			}
			if (bebas >= tawanan.size()) {
				PanggilanPerang.bebaskanTawanan(server, sekte, bebas);
				akhiri(server, this, Hasil.MENANG, null);
			}
		}
	}

	// ================================================================== Kawal Bekal

	/** A supply cart rolls 140 blocks from the compound toward the nearest town; two ambushes stop it. */
	static final class KawalBekal extends Jalan {
		static final double PANJANG = 140.0D;
		static final double TERTINGGAL = 20.0D;
		@Nullable GerobakEntity gerobak;
		Vec3 asal = Vec3.ZERO;
		Vec3 tujuan = Vec3.ZERO;
		@Nullable Regu regu;
		int sergapan;
		boolean tertinggal;

		KawalBekal(ServerPlayer player, String sekte, String lawan, ServerLevel level, BlockPos titik, boolean uji) {
			super(player, Jenis.KAWAL_BEKAL, sekte, lawan, level, titik, uji);
		}

		@Override
		double tiba() {
			return 24.0D;
		}

		@Override
		double lepas() {
			return 200.0D;
		}

		@Override
		long batasWaktu() {
			return 20L * 60L * 9L;
		}

		@Override
		Vec3 pusat() {
			return gerobak != null && !gerobak.isRemoved() ? gerobak.position() : super.pusat();
		}

		@Override
		boolean mulai(ServerPlayer player) {
			BlockPos start = tanah(level, titik.getX(), titik.getZ());
			asal = Vec3.atBottomCenterOf(start);
			Vec3 dir = arahKota(start);
			double tx = asal.x + dir.x * (uji ? 60.0D : PANJANG);
			double tz = asal.z + dir.z * (uji ? 60.0D : PANJANG);
			tujuan = new Vec3(tx, asal.y, tz);
			GerobakEntity cart = EkonomiEntities.GEROBAK.create(level, EntitySpawnReason.EVENT);
			if (cart == null) {
				return false;
			}
			cart.setMuatan(1);
			cart.snapTo(asal.x, asal.y, asal.z, (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F, 0.0F);
			cart.ikat(0L, tujuan);
			if (!level.addFreshEntity(cart)) {
				return false;
			}
			gerobak = cart;
			sementara.add(cart);
			regu = new Regu(level, ranah(player, sekte, lawan), varian(lawan), RogueCultivators.ROLE_AMBUSH, null, lawan);
			bar(player, Component.translatable("kultivator.gabung.misi_perang.bar.kawal", 0), BossEvent.BossBarColor.BLUE,
					BossEvent.BossBarOverlay.PROGRESS);
			player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.kawal.berangkat", (int) tujuan.x, (int) tujuan.z)
					.withStyle(ChatFormatting.YELLOW));
			return true;
		}

		/** Toward the nearest town (any place kind starting with "kota"), else away from the enemy gate. */
		private Vec3 arahKota(BlockPos start) {
			try {
				for (TempatInfo info : TempatApi.dekat(level, start, 1500, "kota")) {
					BlockPos c = info.kotak().getCenter();
					double dx = c.getX() - start.getX();
					double dz = c.getZ() - start.getZ();
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > PANJANG + 20.0D) {
						return new Vec3(dx / d, 0.0D, dz / d);
					}
				}
			} catch (RuntimeException ignored) {
				// no place index (switched off): the fallback below
			}
			BlockPos musuh = PanggilanPerang.gerbang(lawan);
			if (musuh != null) {
				double dx = start.getX() - musuh.getX();
				double dz = start.getZ() - musuh.getZ();
				double d = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
				return new Vec3(dx / d, 0.0D, dz / d);
			}
			double a = random.nextDouble() * Math.PI * 2.0D;
			return new Vec3(Math.cos(a), 0.0D, Math.sin(a));
		}

		@Override
		void detik(ServerPlayer player, long now) {
			GerobakEntity cart = gerobak;
			Regu r = regu;
			if (cart == null || cart.isRemoved() || r == null) {
				akhiri(level.getServer(), this, Hasil.KALAH, "kultivator.gabung.misi_perang.kalah.gerobak");
				return;
			}
			double total = Math.max(1.0D, Math.sqrt(Math.pow(tujuan.x - asal.x, 2) + Math.pow(tujuan.z - asal.z, 2)));
			double jalan = Math.sqrt(Math.pow(cart.getX() - asal.x, 2) + Math.pow(cart.getZ() - asal.z, 2));
			float f = (float) Math.min(1.0D, jalan / total);
			if (bar != null) {
				bar.setProgress(f);
				bar.setName(Component.translatable(r.sisa() > 0 ? "kultivator.gabung.misi_perang.bar.sergap" : "kultivator.gabung.misi_perang.bar.kawal",
						(int) (f * 100), r.sisa()));
			}
			if (Math.pow(cart.getX() - tujuan.x, 2) + Math.pow(cart.getZ() - tujuan.z, 2) < 9.0D) {
				player.sendSystemMessage(Component.translatable("kultivator.gabung.misi_perang.kawal.tiba").withStyle(ChatFormatting.GREEN));
				akhiri(level.getServer(), this, Hasil.MENANG, null);
				return;
			}
			if (sergapan < 2 && f >= (sergapan == 0 ? 0.35F : 0.70F) && r.sisa() == 0) {
				sergapan++;
				r.isi(GabungApi.kelas(lawan) >= 3 ? new int[]{1, 1, 2, 2} : new int[]{1, 1, 2});
				Msg.title(player, Component.translatable("kultivator.gabung.misi_perang.kawal.sergap").withStyle(ChatFormatting.RED),
						Component.translatable("kultivator.gabung.misi_perang.gelombang.sub", PanggilanPerang.nama(lawan)).withStyle(ChatFormatting.GRAY),
						5, 40, 10);
				level.playSound(null, cart.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 1.2F);
			}
			if (r.sisa() > 0) {
				double heading = Math.atan2(tujuan.z - cart.getZ(), tujuan.x - cart.getX());
				r.tick(cart.position(), 10.0D, 14.0D, heading, Math.PI / 2.0D, random, sementara);
				if (r.gagalTotal()) {
					r.lupakan();
				}
			}
			boolean jauh = player.distanceToSqr(cart) > TERTINGGAL * TERTINGGAL;
			if (jauh != tertinggal) {
				tertinggal = jauh;
				if (jauh) {
					Msg.actionBar(player, Component.translatable("kultivator.gabung.misi_perang.kawal.tunggu").withStyle(ChatFormatting.YELLOW));
				}
			}
			cart.setBerhenti(r.sisa() > 0 || tertinggal);
		}
	}

	// ================================================================== a rogue band of the enemy

	/** A queue of rogue tiers spawned on a ring up to {@link #REGU_MAKS} alive; counts the fallen and the fled alike. */
	static final class Regu {
		final ServerLevel level;
		final int realm;
		final int variant;
		final int role;
		final @Nullable BlockPos home;
		final String lawan;
		final ArrayDeque<Integer> antre = new ArrayDeque<>();
		final List<Mob> hidup = new ArrayList<>();
		int muncul;
		int gagal;
		int dimunculkanGelombang;

		Regu(ServerLevel level, int realm, int variant, int role, @Nullable BlockPos home, String lawan) {
			this.level = level;
			this.realm = realm;
			this.variant = variant;
			this.role = role;
			this.home = home;
			this.lawan = lawan;
		}

		/** Queues one wave. */
		void isi(int[] tiers) {
			for (int t : tiers) {
				antre.add(t);
			}
			dimunculkanGelombang = tiers.length;
			gagal = 0;
		}

		/** Spawns what the cap allows on the ring {@code rMin..rMax} around {@code pusat}, within {@code sebar} of angle {@code arah}. */
		void tick(Vec3 pusat, double rMin, double rMax, double arah, double sebar, RandomSource random, List<Entity> sementara) {
			hidup.removeIf(mob -> mob.isRemoved() || !mob.isAlive());
			int tries = 0;
			while (!antre.isEmpty() && hidup.size() < REGU_MAKS && tries++ < 3) {
				double a = arah + (random.nextDouble() * 2.0D - 1.0D) * sebar;
				double r = rMin + random.nextDouble() * (rMax - rMin);
				double x = pusat.x + Math.cos(a) * r;
				double z = pusat.z + Math.sin(a) * r;
				BlockPos ground = BlockPos.containing(x, pusat.y, z);
				if (!level.hasChunk(ground.getX() >> 4, ground.getZ() >> 4) || !level.isPositionEntityTicking(ground)) {
					gagal++;
					continue;
				}
				Vec3 at = Vec3.atBottomCenterOf(tanah(level, ground.getX(), ground.getZ()));
				Mob mob = RogueCultivators.spawn(level, at, antre.peek(), realm, variant, role, home);
				if (mob == null) {
					gagal++;
					continue;
				}
				antre.poll();
				muncul++;
				ItemStack panji = DiplomasiMandiri.panji(level, lawan);
				if (!panji.isEmpty()) {
					mob.setItemSlot(EquipmentSlot.OFFHAND, panji);
					mob.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
				}
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.6D, at.z, 8, 0.3D, 0.4D, 0.3D, 0.01D);
				hidup.add(mob);
				sementara.add(mob);
			}
		}

		/** Alive plus still queued. */
		int sisa() {
			hidup.removeIf(mob -> mob.isRemoved() || !mob.isAlive());
			return hidup.size() + antre.size();
		}

		boolean habis() {
			return sisa() == 0;
		}

		/** Size of the current wave (for the bar). */
		int dimunculkan() {
			return dimunculkanGelombang;
		}

		/** Thirty failed tries without a single arrival: the band cannot come (water, Peaceful, the world cap). */
		boolean gagalTotal() {
			return gagal > 30 && !antre.isEmpty() && hidup.isEmpty();
		}

		/** Drops what is still queued. */
		void lupakan() {
			antre.clear();
			gagal = 0;
		}
	}

	// ================================================================== the camp (display entities)

	/**
	 * A war camp drawn with vanilla display entities (never a block): an A-frame tent in the enemy's colour on poles, a
	 * campfire with smoke, stores (barrels, hay), a weapon rack, a tall banner pole carrying the enemy banner (an item
	 * display) with an interaction entity to pull it, and for Jemput Tawanan one iron-barred cage per captive.
	 */
	static final class Kemah {
		final List<Entity> bagian;
		final List<Vec3> sel = new ArrayList<>();
		final List<List<Entity>> jeruji = new ArrayList<>();
		@Nullable Interaction pegangan;
		@Nullable Display.ItemDisplay bendera;
		@Nullable Vec3 tiang;
		@Nullable Vec3 api;

		private Kemah(List<Entity> bagian) {
			this.bagian = bagian;
		}

		static Kemah bangun(ServerLevel level, BlockPos c, float yaw, String lawan, int sel, List<Entity> into) {
			Kemah k = new Kemah(into);
			Vec3 o = Vec3.atBottomCenterOf(c);
			BlockState kain = Blocks.WOOL.pick(warna(lawan)).defaultBlockState();
			BlockState tiangKayu = Blocks.SPRUCE_FENCE.defaultBlockState();
			Quaternionf none = new Quaternionf();
			// tent: two cloth panels leaning on a ridge, poles at both ends, a ground sheet
			k.balok(level, o, yaw, kain, new Vector3f(-2.2F, 0.0F, -1.25F), new Quaternionf().rotationX((float) Math.toRadians(-45.0D)),
					new Vector3f(4.4F, 0.06F, 1.77F));
			k.balok(level, o, yaw, kain, new Vector3f(-2.2F, 1.25F, 0.0F), new Quaternionf().rotationX((float) Math.toRadians(45.0D)),
					new Vector3f(4.4F, 0.06F, 1.77F));
			k.balok(level, o, yaw, tiangKayu, new Vector3f(-2.35F, 0.0F, -0.15F), none, new Vector3f(0.3F, 1.35F, 0.3F));
			k.balok(level, o, yaw, tiangKayu, new Vector3f(2.05F, 0.0F, -0.15F), none, new Vector3f(0.3F, 1.35F, 0.3F));
			k.balok(level, o, yaw, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), new Vector3f(-2.4F, 1.22F, -0.07F), none,
					new Vector3f(4.8F, 0.14F, 0.14F));
			k.balok(level, o, yaw, Blocks.CARPET.pick(DyeColor.BROWN).defaultBlockState(), new Vector3f(-2.0F, 0.0F, -1.1F), none, new Vector3f(4.0F, 1.0F, 2.2F));
			// campfire in front of the tent
			k.balok(level, o, yaw, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), new Vector3f(-0.5F, 0.0F, 2.3F), none,
					new Vector3f(1.0F, 1.0F, 1.0F));
			k.api = lokal(o, yaw, 0.0D, 2.8D);
			// stores
			k.balok(level, o, yaw, Blocks.BARREL.defaultBlockState(), new Vector3f(2.7F, 0.0F, -1.0F), none, new Vector3f(0.85F, 0.95F, 0.85F));
			k.balok(level, o, yaw, Blocks.BARREL.defaultBlockState(), new Vector3f(2.75F, 0.95F, -0.9F), none, new Vector3f(0.7F, 0.75F, 0.7F));
			k.balok(level, o, yaw, Blocks.HAY_BLOCK.defaultBlockState(), new Vector3f(2.6F, 0.0F, 0.1F), none, new Vector3f(0.9F, 0.7F, 0.9F));
			// weapon rack with two swords
			k.balok(level, o, yaw, Blocks.DARK_OAK_FENCE.defaultBlockState(), new Vector3f(-3.6F, 0.0F, 0.6F), none, new Vector3f(0.25F, 1.2F, 0.25F));
			k.balok(level, o, yaw, Blocks.DARK_OAK_FENCE.defaultBlockState(), new Vector3f(-3.6F, 0.0F, 1.8F), none, new Vector3f(0.25F, 1.2F, 0.25F));
			k.balok(level, o, yaw, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), new Vector3f(-3.62F, 1.05F, 0.6F), none, new Vector3f(0.2F, 0.12F, 1.45F));
			k.barang(level, o, yaw, new ItemStack(Items.IRON_SWORD), -3.45D, 0.75D, 0.95D, 0.7F, 90.0F);
			k.barang(level, o, yaw, new ItemStack(Items.IRON_SWORD), -3.45D, 0.75D, 1.45D, 0.7F, 90.0F);
			// banner pole behind the tent
			k.balok(level, o, yaw, Blocks.DARK_OAK_FENCE.defaultBlockState(), new Vector3f(-0.125F, 0.0F, -3.125F), none, new Vector3f(0.25F, 3.4F, 0.25F));
			k.balok(level, o, yaw, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), new Vector3f(-0.6F, 3.3F, -3.07F), none, new Vector3f(1.2F, 0.13F, 0.13F));
			k.tiang = lokal(o, yaw, 0.0D, -3.0D);
			ItemStack panji = DiplomasiMandiri.panji(level, lawan);
			if (!panji.isEmpty()) {
				k.bendera = k.barang(level, o, yaw, panji, 0.0D, 2.35D, -3.0D, 1.6F, 0.0F);
			}
			Interaction grip = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
			if (grip != null) {
				Vec3 at = k.tiang;
				grip.snapTo(at.x, o.y, at.z, yaw, 0.0F);
				grip.setWidth(1.1F);
				grip.setHeight(3.6F);
				grip.setResponse(true);
				grip.addTag(TAG);
				if (level.addFreshEntity(grip)) {
					k.pegangan = grip;
					into.add(grip);
				}
			}
			// cages for captives, to the left of the tent
			for (int i = 0; i < sel; i++) {
				double lx = -2.0D + i * 2.6D;
				double lz = -5.6D;
				Vec3 centre = lokal(o, yaw, lx, lz);
				k.sel.add(centre);
				List<Entity> bars = new ArrayList<>();
				BlockState ew = Blocks.IRON_BARS.defaultBlockState().setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
				BlockState ns = Blocks.IRON_BARS.defaultBlockState().setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
				float fx = (float) lx;
				float fz = (float) lz;
				bars.add(k.balok(level, o, yaw, ew, new Vector3f(fx - 0.75F, 0.0F, fz - 1.25F), none, new Vector3f(1.5F, 2.2F, 1.0F)));
				bars.add(k.balok(level, o, yaw, ew, new Vector3f(fx - 0.75F, 0.0F, fz + 0.25F), none, new Vector3f(1.5F, 2.2F, 1.0F)));
				bars.add(k.balok(level, o, yaw, ns, new Vector3f(fx - 1.25F, 0.0F, fz - 0.75F), none, new Vector3f(1.0F, 2.2F, 1.5F)));
				bars.add(k.balok(level, o, yaw, ns, new Vector3f(fx + 0.25F, 0.0F, fz - 0.75F), none, new Vector3f(1.0F, 2.2F, 1.5F)));
				bars.add(k.balok(level, o, yaw, Blocks.SPRUCE_SLAB.defaultBlockState(), new Vector3f(fx - 0.8F, 2.2F, fz - 0.8F), none,
						new Vector3f(1.6F, 1.0F, 1.6F)));
				bars.removeIf(e -> e == null);
				k.jeruji.add(bars);
			}
			level.playSound(null, c, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.6F, 0.8F);
			return k;
		}

		/** A block display at local offset {@code t} (before the camp's yaw), rotated by {@code rot}, scaled by {@code s}. */
		@Nullable Entity balok(ServerLevel level, Vec3 o, float yaw, BlockState state, Vector3f t, Quaternionf rot, Vector3f s) {
			Display.BlockDisplay d = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.EVENT);
			if (d == null) {
				return null;
			}
			d.setBlockState(state);
			d.setTransformation(new Transformation(t, rot, s, null));
			d.snapTo(o.x, o.y, o.z, yaw, 0.0F);
			d.setViewRange(1.2F);
			d.addTag(TAG);
			if (!level.addFreshEntity(d)) {
				return null;
			}
			bagian.add(d);
			return d;
		}

		/** An item display at local (x, y, z) facing the camp's yaw plus {@code turn} degrees. */
		Display.@Nullable ItemDisplay barang(ServerLevel level, Vec3 o, float yaw, ItemStack stack, double x, double y, double z, float scale, float turn) {
			Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.EVENT);
			if (d == null) {
				return null;
			}
			Vec3 at = lokal(o, yaw, x, z);
			d.setItemStack(stack);
			d.setItemTransform(ItemDisplayContext.FIXED);
			d.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
			d.snapTo(at.x, o.y + y, at.z, yaw + turn, 0.0F);
			d.setViewRange(1.2F);
			d.addTag(TAG);
			if (!level.addFreshEntity(d)) {
				return null;
			}
			bagian.add(d);
			return d;
		}

		/** Smoke over the campfire. */
		void asap(ServerLevel level) {
			if (api != null) {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, api.x, api.y + 0.6D, api.z, 1, 0.1D, 0.1D, 0.1D, 0.01D);
			}
		}

		/** The banner was pulled: its display goes. */
		void cabut(ServerLevel level) {
			if (bendera != null && !bendera.isRemoved()) {
				level.sendParticles(ParticleTypes.POOF, bendera.getX(), bendera.getY() + 0.5D, bendera.getZ(), 8, 0.3D, 0.6D, 0.3D, 0.02D);
				bendera.discard();
			}
		}

		/** Cage {@code i} opens: its bars go. */
		void bukaSel(ServerLevel level, int i) {
			if (i < 0 || i >= jeruji.size()) {
				return;
			}
			for (Entity e : jeruji.get(i)) {
				if (!e.isRemoved()) {
					level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0D, e.getZ(), 4, 0.4D, 0.6D, 0.4D, 0.01D);
					e.discard();
				}
			}
		}
	}

	/** The world position of camp-local {@code (x, z)} (local +z rotated by {@code yaw} as the display renderer does). */
	static Vec3 lokal(Vec3 o, float yaw, double x, double z) {
		double r = Math.toRadians(-yaw);
		double cos = Math.cos(r);
		double sin = Math.sin(r);
		return new Vec3(o.x + x * cos + z * sin, o.y, o.z - x * sin + z * cos);
	}

	/** The dye nearest the sect's ink colour (camp cloth). */
	static DyeColor warna(String sekte) {
		int rgb = SekteApi.diplomasi().color(sekte);
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		DyeColor best = DyeColor.RED;
		long bestD = Long.MAX_VALUE;
		for (DyeColor dye : DyeColor.values()) {
			int c = dye.getTextureDiffuseColor();
			long dr = (c >> 16 & 0xFF) - r;
			long dg = (c >> 8 & 0xFF) - g;
			long db = (c & 0xFF) - b;
			long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
			if (d < bestD) {
				bestD = d;
				best = dye;
			}
		}
		return best;
	}

	// ================================================================== helpers shared with SerbuanKompleks and PemburuSekte

	/** The ground column at (x, z) (motion blocking, no leaves). */
	static BlockPos tanah(ServerLevel level, int x, int z) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Realm ordinal of the enemy band: the player's realm, one up or down by the tier gap, 1-8. */
	static int ranah(ServerPlayer player, String sekte, String lawan) {
		int r = Cultivators.get(player).realm().ordinal() + Integer.signum(GabungApi.tier(lawan) - GabungApi.tier(sekte));
		return Math.max(1, Math.min(8, r));
	}

	/** Demonic sects send demonic rogues. */
	static int varian(String lawan) {
		return SekteApi.diplomasi().aliran(lawan) == 2 ? RogueCultivators.VARIANT_DEMONIC : RogueCultivators.VARIANT_ROGUE;
	}

	/** One of 8 compass words for the direction (dx, dz) (north = -z). */
	static Component arah(double dx, double dz) {
		double deg = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = Math.floorMod((int) Math.round(deg / 45.0D), 8);
		return Component.translatable("kultivator.gabung.misi_perang.arah." + sector);
	}

	// ================================================================== sheets and chat

	/**
	 * The war entries of {@code gabung_papan}'s {@code daftar} (tab {@code perang}): {@code id}, {@code tab}, {@code mutu}
	 * ("perang"), {@code jenis}, {@code nama}, {@code teks}, {@code pk}, {@code skor}, {@code keadaan} ({@code ok},
	 * {@code aktif}, {@code batas}, {@code terkunci}) with {@code alasan}, {@code x}/{@code z} when the place is known,
	 * {@code sisa_tick} while running. Empty without war.
	 */
	public static ListTag lembar(ServerPlayer player) {
		ListTag out = new ListTag();
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		String lawan = PanggilanPerang.lawan(server, sekte);
		if (sekte == null || lawan == null) {
			return out;
		}
		Jalan running = JALAN.get(player.getUUID());
		for (Jenis jenis : Jenis.values()) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, jenis.id);
			e.putString(LembarKunci.TAB, GabungLembar.Papan.TAB_PERANG);
			e.putString(GabungLembar.Papan.MUTU_MISI, "perang");
			e.putString(LembarKunci.JENIS, jenis.id);
			LembarKunci.teks(e, LembarKunci.NAMA, jenis.nama());
			LembarKunci.teks(e, LembarKunci.TEKS, jenis.teks());
			e.putInt(LembarKunci.PK, jenis == Jenis.BELA_GERBANG ? jenis.pk * BelaGerbang.GELOMBANG : jenis.pk);
			e.putInt(LembarKunci.SKOR, jenis.skorMenang);
			BlockPos at = tempat(sekte, lawan, jenis);
			if (running != null && running.jenis == jenis) {
				e.putString(LembarKunci.KEADAAN, LembarKunci.AKTIF);
				at = running.titik;
				long left = running.tahap == Tahap.BERLANGSUNG ? running.batasWaktu() - (running.level.getGameTime() - running.dibuat)
						: KEDALUWARSA - (running.level.getGameTime() - running.dibuat);
				e.putLong(LembarKunci.SISA_TICK, Math.max(0L, left));
			} else {
				Component why = alasan(player, jenis);
				if (why == null) {
					e.putString(LembarKunci.KEADAAN, LembarKunci.K_OK);
				} else {
					boolean today = running == null && GabungData.get(server).anggota(player.getUUID()).bagian(PanggilanPerang.BAGIAN)
							.getCompoundOrEmpty(HARI).getLongOr(jenis.id, -1L) == GabungApi.hari(server);
					e.putString(LembarKunci.KEADAAN, today ? LembarKunci.K_BATAS : LembarKunci.K_TERKUNCI);
					e.putBoolean(LembarKunci.TERKUNCI, true);
					LembarKunci.teks(e, LembarKunci.ALASAN, why);
				}
			}
			if (at != null) {
				e.putInt(LembarKunci.X, at.getX());
				e.putInt(LembarKunci.Z, at.getZ());
			}
			out.add(e);
		}
		return out;
	}

	/** The {@code misi} list of {@code gabung_politik}'s war tab: {@code id}, {@code nama}, {@code selesai} (done in this war). */
	public static ListTag lembarPolitik(ServerPlayer player) {
		ListTag out = new ListTag();
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		CompoundTag p = PanggilanPerang.perang(server, sekte);
		if (p == null) {
			return out;
		}
		CompoundTag mine = GabungData.get(server).anggota(player.getUUID()).bagian(PanggilanPerang.BAGIAN);
		String tanda = sekte + ":" + p.getLongOr(PanggilanPerang.MULAI, 0L);
		CompoundTag n = tanda.equals(mine.getStringOr(TANDA, "")) ? mine.getCompoundOrEmpty(JUMLAH) : new CompoundTag();
		for (Jenis jenis : Jenis.values()) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, jenis.id);
			LembarKunci.teks(e, LembarKunci.NAMA, jenis.nama());
			e.putBoolean(GabungLembar.Politik.SELESAI, n.getIntOr(jenis.id, 0) > 0);
			out.add(e);
		}
		return out;
	}

	/** Where mission {@code jenis} of {@code sekte} takes place (for the map mark), or null. */
	static @Nullable BlockPos tempat(String sekte, String lawan, Jenis jenis) {
		return switch (jenis) {
			case BELA_GERBANG, KAWAL_BEKAL -> GabungApi.titik(sekte, TitikGabung.GERBANG_KOMPLEKS);
			case DUEL_JUARA -> GabungApi.titik(sekte, TitikGabung.ARENA);
			case SERBU_POS -> garis(sekte, lawan, 0.60D);
			case JEMPUT_TAWANAN -> garis(sekte, lawan, 0.75D);
		};
	}

	/** The mission list in chat with {@code [Terima]} buttons. */
	static void cetak(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		String lawan = PanggilanPerang.lawan(server, sekte);
		if (sekte == null || lawan == null) {
			return;
		}
		Jalan running = JALAN.get(player.getUUID());
		for (Jenis jenis : Jenis.values()) {
			MutableComponent line = Component.literal(" ").append(jenis.nama().withStyle(ChatFormatting.WHITE))
					.append(Component.translatable("kultivator.gabung.misi_perang.baris", jenis.skorMenang,
							jenis == Jenis.BELA_GERBANG ? jenis.pk * BelaGerbang.GELOMBANG : jenis.pk).withStyle(ChatFormatting.GRAY))
					.append(" ");
			if (running != null && running.jenis == jenis) {
				line.append(Component.translatable("kultivator.gabung.misi_perang.berjalan").withStyle(ChatFormatting.AQUA)).append(" ")
						.append(PanggilanPerang.tombol("kultivator.gabung.misi_perang.tombol.batal", "/kultivasi gabung perang batal",
								"kultivator.gabung.misi_perang.tombol.batal.tip", ChatFormatting.RED));
			} else {
				Component why = alasan(player, jenis);
				if (why == null) {
					line.append(PanggilanPerang.tombol("kultivator.gabung.misi_perang.tombol.terima", "/kultivasi gabung perang terima " + jenis.id,
							"kultivator.gabung.misi_perang.tombol.terima.tip", ChatFormatting.GREEN));
				} else {
					line.append(why.copy().withStyle(ChatFormatting.DARK_GRAY));
				}
			}
			player.sendSystemMessage(line);
		}
	}

	private MisiPerang() {
	}
}
