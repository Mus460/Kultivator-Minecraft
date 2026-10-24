package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The live Kota Oasis towns of this server session (DESIGN 38.T.4) and the shared state helpers of the theme package.
 * <p>
 * A town becomes "live" when one of its {@code tema_oasis/*} anchors loads ({@link OasisAnchors}); it is keyed by its
 * town id {@link Hidup#situs} (the {@code kota_tema/oasis} site id of {@code TemaKota}, the same id the resident schedule
 * {@code HidupApi.timpaJadwal}, the event calendar and {@code KotaTemaData.kota} use) and also knows its Nama Baik town id
 * {@link Hidup#town} ({@code kota/kota_tema}, the reputation of the 34 town system). The town centre is the
 * centre of district {@code mata_air} ({@code TemaKota.Kota.pusat}); the {@code tema_oasis/pusat} anchor (Alun Oasis)
 * refines it when it loads. The walled town is the 288 x 288 square around the centre (rotation does not
 * matter for a square); the Lapangan Kafilah lies outside it.
 * <p>
 * Persistent state lives in {@code KotaTemaData}: {@link #kota} per town ({@code bagian("oasis")["kota_<situs>"]}) and
 * {@link #pemain} per player ({@code bagian("oasis")["p_<uuid>"]}). New worlds only: no versions, no migration.
 */
public final class OasisKota {

	/** Theme id ({@code KotaTemaData.bagian}, calendar, commands). */
	public static final String TEMA = "oasis";
	/** Site kind of the town ({@code Tema.OASIS.jenisSitus()}). */
	public static final String JENIS = "kota_tema/oasis";
	/** Half side of the walled square (3 x 96 / 2 = 144, plus the wall). */
	public static final int SETENGAH_TEMBOK = 148;
	/** A position this far from the centre still belongs to the town (walls + caravan field). */
	public static final int JANGKAU_KOTA = 300;

	/** One loaded oasis town. */
	public static final class Hidup {
		public final long situs;
		public final ResourceKey<Level> dimensi;
		public long town;
		public BlockPos pusat;
		public boolean pusatPasti;
		public @Nullable BoundingBox kotak;
		/** Loaded anchors of this town by {@code what} ({@code kuil_air}, {@code kandang}, ...). */
		public final Map<String, List<DuniaAnchorEntity>> jangkar = new LinkedHashMap<>();

		Hidup(long situs, ResourceKey<Level> dimensi, BlockPos pusat) {
			this.situs = situs;
			this.dimensi = dimensi;
			this.pusat = pusat.immutable();
		}

		/** The loaded anchors of one kind (empty list when none). */
		public List<DuniaAnchorEntity> jangkar(String what) {
			List<DuniaAnchorEntity> list = jangkar.get(what);
			if (list == null) {
				return List.of();
			}
			list.removeIf(DuniaAnchorEntity::isRemoved);
			return list;
		}

		/** The first loaded anchor of one kind, or null. */
		public @Nullable DuniaAnchorEntity satu(String what) {
			List<DuniaAnchorEntity> list = jangkar(what);
			return list.isEmpty() ? null : list.getFirst();
		}

		/** True inside the walls (the 3 x 3 districts), whatever the town's rotation. */
		public boolean dalamTembok(BlockPos pos) {
			return Math.abs(pos.getX() - pusat.getX()) <= SETENGAH_TEMBOK && Math.abs(pos.getZ() - pusat.getZ()) <= SETENGAH_TEMBOK;
		}

		/** Horizontal distance from the centre. */
		public double jarak(Vec3 at) {
			double dx = at.x - (pusat.getX() + 0.5D);
			double dz = at.z - (pusat.getZ() + 0.5D);
			return Math.sqrt(dx * dx + dz * dz);
		}
	}

	private static final Map<Long, Hidup> KOTA = new LinkedHashMap<>();

	// ================================================================== registry

	/** Registers the anchor's town (from {@link OasisAnchors#loaded}); null when the anchor is not inside an oasis town. */
	static @Nullable Hidup daftar(ServerLevel level, DuniaAnchorEntity anchor) {
		BlockPos at = anchor.blockPosition();
		TemaKota.Kota born = TemaKota.di(level, at);
		if (born == null || born.tema() != Tema.OASIS) {
			// outside a planned oasis (a test structure, a ruin anchor): the nearest live town, else none
			Hidup near = terdekat(level, Vec3.atCenterOf(at), JANGKAU_KOTA);
			if (near == null) {
				return null;
			}
			List<DuniaAnchorEntity> list = near.jangkar.computeIfAbsent(anchor.what(), k -> new ArrayList<>());
			if (!list.contains(anchor)) {
				list.add(anchor);
			}
			return near;
		}
		long situs = born.situsId();
		Hidup kota = KOTA.get(situs);
		if (kota == null) {
			kota = new Hidup(situs, level.dimension(), born.pusat());
			kota.kotak = born.kotak();
			kota.pusatPasti = true;
			KOTA.put(situs, kota);
		}
		if (kota.town == 0L) {
			long town = Towns.townOf(anchor);
			kota.town = town != 0L ? town : situs;
		}
		if (OasisAnchors.PUSAT.equals(anchor.what())) {
			kota.pusat = at.immutable();
		}
		List<DuniaAnchorEntity> list = kota.jangkar.computeIfAbsent(anchor.what(), k -> new ArrayList<>());
		if (!list.contains(anchor)) {
			list.add(anchor);
		}
		return kota;
	}

	/** Forgets one anchor (unload); the town stays known (its data is in {@code KotaTemaData}). */
	static void lupakan(DuniaAnchorEntity anchor) {
		for (Hidup kota : KOTA.values()) {
			List<DuniaAnchorEntity> list = kota.jangkar.get(anchor.what());
			if (list != null) {
				list.remove(anchor);
			}
		}
	}

	/** Server stop. */
	static void reset() {
		KOTA.clear();
	}

	/** Every town seen this session. */
	public static Collection<Hidup> semua() {
		return Collections.unmodifiableCollection(KOTA.values());
	}

	/** The town with place id or town id {@code id}, else the one a published site of that id stands in; or null. */
	public static @Nullable Hidup cari(@Nullable MinecraftServer server, long id) {
		Hidup kota = KOTA.get(id);
		if (kota != null) {
			return kota;
		}
		for (Hidup each : KOTA.values()) {
			if (each.town == id) {
				return each;
			}
		}
		if (server != null) {
			DuniaSites.Site site = DuniaSites.byId(server, id);
			if (site != null) {
				for (Hidup each : KOTA.values()) {
					if (each.dimensi.equals(site.dim()) && each.jarak(Vec3.atCenterOf(site.pos())) <= JANGKAU_KOTA) {
						return each;
					}
				}
			}
		}
		return null;
	}

	/** The live town whose centre is nearest {@code at} within {@code range} blocks, or null. */
	public static @Nullable Hidup terdekat(ServerLevel level, Vec3 at, double range) {
		Hidup best = null;
		double bestD = range;
		for (Hidup kota : KOTA.values()) {
			if (!kota.dimensi.equals(level.dimension())) {
				continue;
			}
			double d = kota.jarak(at);
			if (d <= bestD) {
				bestD = d;
				best = kota;
			}
		}
		return best;
	}

	/** The live town {@code player} stands in (walls or caravan field), or null. */
	public static @Nullable Hidup di(ServerPlayer player) {
		return player.level() instanceof ServerLevel level ? terdekat(level, player.position(), JANGKAU_KOTA) : null;
	}

	/** True when {@code pos} lies inside the walls of a live oasis town. */
	public static boolean dalamTembok(ServerLevel level, BlockPos pos) {
		Hidup kota = terdekat(level, Vec3.atCenterOf(pos), JANGKAU_KOTA);
		return kota != null && kota.dalamTembok(pos);
	}

	// ================================================================== persistent state

	/** The theme section {@code bagian("oasis")}. */
	public static CompoundTag bagian(MinecraftServer server) {
		return KotaTemaData.get(server).bagian(TEMA);
	}

	/** Per-town state {@code kota("oasis", situs)}. */
	public static CompoundTag kota(MinecraftServer server, long situs) {
		return KotaTemaData.get(server).kota(TEMA, situs);
	}

	/** Per-player state of the theme ({@code bagian("oasis")["p_<uuid>"]}), created on first use. */
	public static CompoundTag pemain(MinecraftServer server, UUID player) {
		CompoundTag part = bagian(server);
		String key = "p_" + player;
		CompoundTag tag = part.getCompoundOrEmpty(key);
		if (!part.contains(key)) {
			part.put(key, tag);
		}
		return tag;
	}

	/** Marks the theme data dirty. */
	public static void ubah(MinecraftServer server) {
		KotaTemaData.get(server).ubah();
	}

	/** The world day (overworld clock / 24000). */
	public static long hari(ServerLevel level) {
		return TemaKalender.hari(level);
	}

	/** The overworld time of day 0-23999. */
	public static long jam(ServerLevel level) {
		return TemaKalender.jam(level);
	}

	/** The hour 0-23 of the 38.N.3 schedule (tick 0 = 06:00). */
	public static int jamHari(ServerLevel level) {
		return (int) ((jam(level) / 1000L + 6L) % 24L);
	}

	/** The distance ring of 38.C.9 (1: < 1000 from the world centre, 2: < 3000, 3: < 8000, 4: beyond). */
	public static int cincin(BlockPos pos) {
		double d = Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ());
		if (d < 1000.0D) {
			return 1;
		}
		if (d < 3000.0D) {
			return 2;
		}
		return d < 8000.0D ? 3 : 4;
	}

	/** Puts {@code stack} in the player's inventory, the rest on the ground at his feet (only he may pick it up at first). */
	public static void beri(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack copy = stack.copy();
		if (player.getInventory().add(copy) && copy.isEmpty()) {
			return;
		}
		if (!copy.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), copy);
			item.setTarget(player.getUUID());
			item.setPickUpDelay(10);
			player.level().addFreshEntity(item);
		}
	}

	/** A stable 64-bit mix (deterministic per seed, place and salt). */
	public static long hash(long a, long b, long salt) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		h *= 0x94D049BB133111EBL;
		return h ^ (h >>> 32);
	}

	/** {@link #hash} as a double in [0, 1). */
	public static double acak(long a, long b, long salt) {
		return (hash(a, b, salt) >>> 11) * 0x1.0p-53;
	}

	/** True while the theme's behaviour runs ({@code kotaTema.aktif} and the oasis worldgen/behaviour not switched off). */
	public static boolean aktif() {
		return dev.kultivator.dunia.tempat.tema.TemaConfig.aktif(Tema.OASIS);
	}

	private OasisKota() {
	}
}
