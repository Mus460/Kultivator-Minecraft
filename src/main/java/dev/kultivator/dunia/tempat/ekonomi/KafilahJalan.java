package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Road caravans (DESIGN 38.C.6.3, 38.M.3 mission {@code kafilah}): trade caravans that exist as data travelling between
 * two known towns at 0.8 blocks a second. A body (a {@link GerobakEntity} cart, or the themed body of 38TK's
 * {@code TemaApi.tubuhKafilah}: a camel string or a junk, with its merchant and two guards walking beside) appears only
 * while a player is within 64 blocks of the caravan's place, and leaves with him; nothing is saved in chunks. An escorted
 * caravan only moves while its escort is within 48 blocks, and once per trip bandits may wait on the road (35 %, never in
 * a safe place, under the shared enemy pressure of {@code Sibuk}); the cart halts until they are beaten. At most
 * {@link #MAKS} caravans in the world. Screen {@code kafilah}: caravan masters, wandering traders and porters.
 */
public final class KafilahJalan {

	public static final int MAKS = 8;
	public static final double TUBUH_JARAK = 64.0D;
	public static final double KAWAL_JARAK = 48.0D;
	public static final double PELUANG_BEGAL = 0.35D;
	private static final String KUNCI = "kafilah_jalan";
	private static final Set<NpcRole> PERAN = Set.of(NpcRole.KAFILAH_BASHI, NpcRole.PEDAGANG_KELILING, NpcRole.KULI);

	/** Bodies of loaded caravans: caravan id → [cart or themed lead, merchant, guard, guard]. */
	private static final Map<Long, Entity[]> TUBUH = new HashMap<>();
	/** The caravan a player picked on the kafilah screen (read by the guild mission it starts). */
	private static final Map<UUID, Long> PILIHAN = new HashMap<>();

	static void init() {
		TempatLayar.daftar(LembarKunci.Kafilah.LAYAR, KafilahJalan::aksi, KafilahJalan::bangun);
	}

	static void bersihkanSemua() {
		for (Entity[] bodies : TUBUH.values()) {
			hapus(bodies);
		}
		TUBUH.clear();
		PILIHAN.clear();
	}

	private static ListTag daftar(MinecraftServer server) {
		CompoundTag global = EkonomiData.get(server).global();
		ListTag list = global.getList(KUNCI).orElse(null);
		if (list == null) {
			list = new ListTag();
			global.put(KUNCI, list);
		}
		return list;
	}

	private static @Nullable CompoundTag cari(MinecraftServer server, long id) {
		for (net.minecraft.nbt.Tag each : daftar(server)) {
			if (each instanceof CompoundTag caravan && caravan.getLongOr("id", -1L) == id) {
				return caravan;
			}
		}
		return null;
	}

	private static @Nullable MinecraftServer server;

	static void setServer(@Nullable MinecraftServer current) {
		server = current;
	}

	/** True while caravan {@code id} travels (the cart body asks every tick). */
	static boolean hidup(long id) {
		MinecraftServer current = server;
		return current != null && cari(current, id) != null && !cariSelesai(current, id);
	}

	private static boolean cariSelesai(MinecraftServer current, long id) {
		CompoundTag caravan = cari(current, id);
		return caravan == null || caravan.getBooleanOr("tiba", false);
	}

	/** True once caravan {@code id} arrived. */
	static boolean tiba(long id) {
		MinecraftServer current = server;
		if (current == null) {
			return false;
		}
		CompoundTag caravan = cari(current, id);
		return caravan != null && caravan.getBooleanOr("tiba", false);
	}

	/** Progress 0..1 of caravan {@code id}. */
	static double kemajuan(long id) {
		MinecraftServer current = server;
		CompoundTag caravan = current == null ? null : cari(current, id);
		return caravan == null ? 0.0D : kemajuan(caravan);
	}

	private static double kemajuan(CompoundTag caravan) {
		return Math.min(1.0D, caravan.getLongOr("maju", 0L) / (double) Math.max(1L, caravan.getLongOr("durasi", 1L)));
	}

	private static Vec3 posisi(CompoundTag caravan) {
		double t = kemajuan(caravan);
		double x = caravan.getIntOr("ax", 0) + (caravan.getIntOr("tx", 0) - caravan.getIntOr("ax", 0)) * t + 0.5D;
		double z = caravan.getIntOr("az", 0) + (caravan.getIntOr("tz", 0) - caravan.getIntOr("az", 0)) * t + 0.5D;
		return new Vec3(x, 0.0D, z);
	}

	private static @Nullable ServerLevel level(MinecraftServer current, CompoundTag caravan) {
		Identifier id = Identifier.tryParse(caravan.getStringOr("dim", "minecraft:overworld"));
		return id == null ? null : current.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Life

	/** Every 20 ticks: departures (once a minute), progress, bodies, ambushes. */
	static void tick(MinecraftServer current, long tick) {
		server = current;
		if (!EkonomiConfig.get().kafilah) {
			return;
		}
		ListTag list = daftar(current);
		if (tick % 1200L == 0L) {
			berangkat(current, list);
		}
		long now = Barang.waktu(current);
		boolean changed = false;
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag caravan = list.getCompoundOrEmpty(i);
			long id = caravan.getLongOr("id", 0L);
			if (caravan.getBooleanOr("tiba", false)) {
				hapus(TUBUH.remove(id));
				if (now - caravan.getLongOr("tiba_pada", now) > 24000L) {
					list.remove(i);
					changed = true;
				}
				continue;
			}
			ServerLevel level = level(current, caravan);
			if (level == null) {
				list.remove(i);
				changed = true;
				continue;
			}
			Vec3 pos = posisi(caravan);
			boolean jalan = !caravan.getBooleanOr("tahan", false);
			UUID escort = uuid(caravan.getStringOr("kawal", ""));
			ServerPlayer guard = escort == null ? null : current.getPlayerList().getPlayer(escort);
			if (escort != null && (guard == null || guard.level() != level || horizontal(guard.position(), pos) > KAWAL_JARAK)) {
				jalan = false;
			}
			if (jalan) {
				caravan.putLong("maju", caravan.getLongOr("maju", 0L) + 20L);
				changed = true;
			}
			if (guard != null && jalan) {
				begal(level, guard, caravan, pos);
			}
			if (caravan.getLongOr("maju", 0L) >= caravan.getLongOr("durasi", 1L)) {
				caravan.putBoolean("tiba", true);
				caravan.putLong("tiba_pada", now);
				changed = true;
				// 42.6.3: an arrived caravan restocks the three scarcest goods of the town it reached
				dev.kultivator.dunia.kota42.ekonomi.StokKota.kafilahTiba(current, caravan.getLongOr("tujuan", 0L));
				if (guard != null) {
					Msg.chat(guard, Component.translatable("kultivator.tempat_ekonomi.kafilah.tiba", KotaNames.town(caravan.getLongOr("tujuan", 0L)))
							.withStyle(ChatFormatting.GREEN));
				}
				continue;
			}
			tubuh(level, caravan, posisi(caravan), jalan);
		}
		if (changed) {
			EkonomiData.get(current).touch();
		}
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static @Nullable UUID uuid(String text) {
		try {
			return text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** A new caravan between a known town and its nearest neighbour 300-3000 blocks away (one per call). */
	private static void berangkat(MinecraftServer current, ListTag list) {
		if (list.size() >= MAKS) {
			return;
		}
		List<Long> towns = Towns.known(current);
		if (towns.size() < 2) {
			return;
		}
		RandomSource random = current.overworld().getRandom();
		long from = towns.get(random.nextInt(towns.size()));
		BlockPos a = Towns.centre(current, from);
		ResourceKey<Level> dim = Towns.dimension(current, from);
		if (a == null || dim == null || !Level.OVERWORLD.equals(dim)) {
			return;
		}
		long to = 0L;
		BlockPos b = null;
		double best = Double.MAX_VALUE;
		for (long other : towns) {
			BlockPos centre = Towns.centre(current, other);
			if (other == from || centre == null || !dim.equals(Towns.dimension(current, other))) {
				continue;
			}
			double d = centre.distSqr(a);
			if (d > 300.0D * 300.0D && d < 3000.0D * 3000.0D && d < best) {
				best = d;
				to = other;
				b = centre;
			}
		}
		if (b == null) {
			return;
		}
		for (net.minecraft.nbt.Tag each : list) {
			if (each instanceof CompoundTag c && c.getLongOr("asal", 0L) == from && c.getLongOr("tujuan", 0L) == to && !c.getBooleanOr("tiba", false)) {
				return;
			}
		}
		double distance = Math.sqrt(best);
		CompoundTag caravan = new CompoundTag();
		caravan.putLong("id", EkonomiData.get(current).serial("kafilah_jalan"));
		caravan.putLong("asal", from);
		caravan.putLong("tujuan", to);
		caravan.putInt("ax", a.getX());
		caravan.putInt("az", a.getZ());
		caravan.putInt("tx", b.getX());
		caravan.putInt("tz", b.getZ());
		caravan.putString("dim", dim.identifier().toString());
		caravan.putLong("durasi", Math.max(1200L, Math.round(distance / GerobakEntity.LAJU)));
		caravan.putLong("maju", 0L);
		caravan.putInt("muatan", random.nextInt(3));
		caravan.putInt("look", random.nextInt());
		caravan.putString("rupa", TemaApi.rupaKafilah(from, to));
		list.add(caravan);
		EkonomiData.get(current).touch();
	}

	/** Shows, moves or removes the body of a caravan. */
	private static void tubuh(ServerLevel level, CompoundTag caravan, Vec3 pos, boolean jalan) {
		long id = caravan.getLongOr("id", 0L);
		boolean near = false;
		for (ServerPlayer player : level.players()) {
			if (horizontal(player.position(), pos) < TUBUH_JARAK) {
				near = true;
				break;
			}
		}
		Entity[] bodies = TUBUH.get(id);
		if (!near || !level.isPositionEntityTicking(BlockPos.containing(pos))) {
			if (bodies != null) {
				hapus(TUBUH.remove(id));
			}
			return;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
		Vec3 ground = new Vec3(pos.x, y, pos.z);
		Vec3 target = new Vec3(caravan.getIntOr("tx", 0) + 0.5D, y, caravan.getIntOr("tz", 0) + 0.5D);
		float yaw = (float) (Math.atan2(target.z - ground.z, target.x - ground.x) * (180.0D / Math.PI)) - 90.0F;
		if (bodies == null || bodies[0] == null || bodies[0].isRemoved()) {
			hapus(bodies);
			bodies = buat(level, caravan, ground, target, yaw);
			if (bodies == null) {
				return;
			}
			TUBUH.put(id, bodies);
		}
		Entity lead = bodies[0];
		if (lead instanceof GerobakEntity cart) {
			cart.setBerhenti(!jalan);
			if (horizontal(cart.position(), ground) > 6.0D) {
				cart.snapTo(ground.x, ground.y, ground.z, yaw, 0.0F);
			}
		} else if (horizontal(lead.position(), ground) > 2.0D) {
			lead.snapTo(ground.x, ground.y, ground.z, yaw, 0.0F);
		}
		for (int i = 1; i < bodies.length; i++) {
			if (bodies[i] instanceof KultivatorNpc npc && !npc.isRemoved()) {
				double side = i == 1 ? 0.0D : i == 2 ? 2.2D : -2.2D;
				Vec3 at = lead.position().add(Math.cos(Math.toRadians(yaw)) * side, 0.0D, Math.sin(Math.toRadians(yaw)) * side);
				if (npc.distanceToSqr(at) > 9.0D) {
					npc.getNavigation().moveTo(at.x, at.y, at.z, 1.0D);
				}
			}
		}
	}

	private static @Nullable Entity[] buat(ServerLevel level, CompoundTag caravan, Vec3 ground, Vec3 target, float yaw) {
		long id = caravan.getLongOr("id", 0L);
		String rupa = caravan.getStringOr("rupa", TemaApi.RUPA_GEROBAK);
		Entity lead = TemaApi.tubuhKafilah(level, rupa, ground, yaw);
		if (lead == null) {
			if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC, ground)) {
				return null;
			}
			GerobakEntity cart = EkonomiEntities.GEROBAK.create(level, EntitySpawnReason.EVENT);
			if (cart == null) {
				return null;
			}
			cart.snapTo(ground.x, ground.y, ground.z, yaw, 0.0F);
			cart.setMuatan(caravan.getIntOr("muatan", 0));
			cart.ikat(id, target);
			if (!level.addFreshEntity(cart)) {
				return null;
			}
			lead = cart;
		}
		long town = caravan.getLongOr("asal", 0L);
		Entity[] bodies = new Entity[4];
		bodies[0] = lead;
		BlockPos base = BlockPos.containing(ground);
		bodies[1] = HidupApi.tubuh(level, town, NpcRole.PEDAGANG_KELILING, base.offset(1, 0, 0), yaw);
		bodies[2] = HidupApi.tubuh(level, town, NpcRole.PENJAGA, base.offset(2, 0, 1), yaw);
		bodies[3] = HidupApi.tubuh(level, town, NpcRole.PENJAGA, base.offset(-2, 0, 1), yaw);
		for (int i = 1; i < 4; i++) {
			if (bodies[i] instanceof KultivatorNpc npc) {
				npc.setLonely(TUBUH_JARAK, 200);
				npc.memory().putBoolean("kafilah", true);
			}
		}
		return bodies;
	}

	private static void hapus(@Nullable Entity[] bodies) {
		if (bodies == null) {
			return;
		}
		for (Entity body : bodies) {
			if (body instanceof KultivatorNpc npc) {
				npc.vanish();
			} else if (body != null && !body.isRemoved()) {
				body.discard();     // the cart, or 38TK's themed lead (camel string, junk) from TemaApi.tubuhKafilah
			}
		}
	}

	/** The one ambush of an escorted trip, in the middle of the road (35 %, outside safe places, enemy pressure). */
	private static void begal(ServerLevel level, ServerPlayer guard, CompoundTag caravan, Vec3 pos) {
		double t = kemajuan(caravan);
		if (caravan.getBooleanOr("begal_dicek", false) || t < 0.35D || t > 0.65D) {
			return;
		}
		caravan.putBoolean("begal_dicek", true);
		RandomSource random = RandomSource.create(caravan.getLongOr("id", 0L) * 977L ^ level.getSeed());
		BlockPos at = BlockPos.containing(pos.x, guard.getY(), pos.z);
		if (random.nextDouble() >= PELUANG_BEGAL || DuniaApi.safeGround(level, at) || Sibuk.why(guard) != null || Sibuk.enemyRefusal(guard) != null) {
			return;
		}
		long id = caravan.getLongOr("id", 0L);
		MinecraftServer current = level.getServer();
		int band = Barang.pita(guard);
		int[] tiers = band >= 4 ? new int[] {2, 2, 1} : new int[] {1, 1, 1};
		Vec3 ambush = pos.add(random.nextBoolean() ? 10.0D : -10.0D, guard.getY(), random.nextBoolean() ? 8.0D : -8.0D);
		int n = RogueCultivators.spawnBand(level, ambush, tiers, Math.max(1, band), RogueCultivators.ROLE_QUEST, at, cleared -> {
			CompoundTag c = cari(current, id);
			if (c != null) {
				c.putBoolean("tahan", false);
				EkonomiData.get(current).touch();
			}
		});
		if (n > 0) {
			caravan.putBoolean("tahan", true);
			Sibuk.enemyTaken(guard, "kafilah.begal");
			Msg.chat(guard, Component.translatable("kultivator.tempat_ekonomi.kafilah.begal").withStyle(ChatFormatting.RED));
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Escort (guild mission kafilah)

	/**
	 * Binds the player as escort of a caravan leaving town {@code town} (the one picked on the kafilah screen, else the
	 * nearest waiting one); returns its id, 0 when none waits.
	 */
	static long kawal(ServerPlayer player, long town, long misiId) {
		MinecraftServer current = player.level().getServer();
		Long picked = PILIHAN.remove(player.getUUID());
		CompoundTag best = picked == null ? null : cari(current, picked);
		if (best == null) {
			double bestSq = Double.MAX_VALUE;
			for (net.minecraft.nbt.Tag each : daftar(current)) {
				if (!(each instanceof CompoundTag c) || c.getBooleanOr("tiba", false) || !c.getStringOr("kawal", "").isEmpty()) {
					continue;
				}
				double d = horizontal(player.position(), posisi(c));
				if ((town == 0L || c.getLongOr("asal", 0L) == town) && d < bestSq && kemajuan(c) < 0.3D) {
					bestSq = d;
					best = c;
				}
			}
		}
		if (best == null || !best.getStringOr("kawal", "").isEmpty()) {
			return 0L;
		}
		best.putString("kawal", player.getUUID().toString());
		best.putLong("misi", misiId);
		EkonomiData.get(current).touch();
		return best.getLongOr("id", 0L);
	}

	/** Frees caravan {@code id} of its escort (mission given up). */
	static void lepas(long id) {
		MinecraftServer current = server;
		CompoundTag caravan = current == null ? null : cari(current, id);
		if (caravan != null) {
			caravan.remove("kawal");
			caravan.putBoolean("tahan", false);
			EkonomiData.get(current).touch();
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Screen kafilah

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		MinecraftServer current = player.level().getServer();
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.kafilah.judul", LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_LUAR, npc);
		ListTag rows = new ListTag();
		for (net.minecraft.nbt.Tag each : daftar(current)) {
			if (!(each instanceof CompoundTag caravan) || caravan.getBooleanOr("tiba", false)) {
				continue;
			}
			Vec3 pos = posisi(caravan);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Long.toString(caravan.getLongOr("id", 0L)));
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.kafilah.nama", KotaNames.person(caravan.getIntOr("look", 0))));
			LembarKunci.teks(row, LembarKunci.Kafilah.ASAL, KotaNames.town(caravan.getLongOr("asal", 0L)));
			LembarKunci.teks(row, LembarKunci.TUJUAN, KotaNames.town(caravan.getLongOr("tujuan", 0L)));
			row.putInt(LembarKunci.X, (int) pos.x);
			row.putInt(LembarKunci.Z, (int) pos.z);
			row.putFloat(LembarKunci.KEMAJUAN, (float) kemajuan(caravan));
			row.putInt(LembarKunci.RISIKO, (int) Math.round(PELUANG_BEGAL * 100.0D));
			int rank = rankKawal(caravan);
			row.putLong(LembarKunci.Kafilah.IMBALAN, MisiGilda.imbalan(Barang.pita(player), rank, RandomSource.create(caravan.getLongOr("id", 0L))));
			Component why = null;
			if (!caravan.getStringOr("kawal", "").isEmpty()) {
				why = Component.translatable("kultivator.tempat_ekonomi.kafilah.sudah_dikawal");
			} else if (kemajuan(caravan) >= 0.3D) {
				why = Component.translatable("kultivator.tempat_ekonomi.kafilah.sudah_jauh");
			} else if (Barang.pita(player) < MisiGilda.PITA_MIN[rank]) {
				why = Component.translatable("kultivator.tempat_ekonomi.misi.pangkat_terlalu_tinggi");
			}
			row.putBoolean(LembarKunci.BISA, why == null);
			if (why != null) {
				LembarKunci.teks(row, LembarKunci.ALASAN, why);
			}
			rows.add(row);
		}
		tag.put(LembarKunci.DAFTAR, rows);
		if (rows.isEmpty()) {
			Lembar.kosong(tag, "kultivator.tempat_ekonomi.kafilah.kosong");
		}
		return tag;
	}

	/** Escort rank by distance: D up to 800 blocks, C up to 1600, B beyond. */
	private static int rankKawal(CompoundTag caravan) {
		double dx = caravan.getIntOr("tx", 0) - caravan.getIntOr("ax", 0);
		double dz = caravan.getIntOr("tz", 0) - caravan.getIntOr("az", 0);
		double d = Math.sqrt(dx * dx + dz * dz);
		return d <= 800.0D ? JenisMisi.D : d <= 1600.0D ? JenisMisi.C : JenisMisi.B;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		String[] parts = LembarKunci.urai(op);
		if (npc == null || !LembarKunci.Kafilah.OP_KAWAL.equals(parts[0])) {
			return;
		}
		MinecraftServer current = player.level().getServer();
		long id = LembarKunci.argLong(parts, 1, -1L);
		CompoundTag caravan = cari(current, id);
		if (caravan == null || !caravan.getStringOr("kawal", "").isEmpty() || kemajuan(caravan) >= 0.3D) {
			return;
		}
		JenisMisi jenis = null;
		for (JenisMisi each : EkonomiApi.jenisMisi()) {
			if ("kafilah".equals(each.id())) {
				jenis = each;
			}
		}
		if (jenis == null) {
			return;
		}
		PILIHAN.put(player.getUUID(), id);
		MisiGilda.Tawaran tawaran = new MisiGilda.Tawaran(id * 7919L % 1_000_000_000L + 1L, jenis, rankKawal(caravan), caravan.getIntOr("tx", 0),
				caravan.getIntOr("tz", 0), 0);
		MisiGilda.terima(player, npc, tawaran, npc.anchorSite(), caravan.getLongOr("asal", 0L));
		PILIHAN.remove(player.getUUID());
	}

	private KafilahJalan() {
	}
}
