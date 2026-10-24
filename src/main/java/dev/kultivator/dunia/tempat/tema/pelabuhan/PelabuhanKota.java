package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 * The live Kota Pelabuhan towns of this server session (DESIGN 38.T.3) and the shared state helpers of the theme
 * package.
 * <p>
 * A town becomes live when one of its {@code pelabuhan/*} anchors loads ({@link PelabuhanAnchors}). It is keyed by the
 * town id {@link Hidup#situs} (the {@code kota_tema/pelabuhan} site id of {@link TemaKota}, the same id
 * {@code TemaKalender}, {@code TemaNet} and {@code KotaTemaData.kota} use) and also keeps its Nama Baik town id
 * {@link Hidup#town} ({@code kota/kota_tema} of 38KS {@code Towns}). A test structure placed outside a planned town
 * becomes its own small town keyed by the anchor's site id, so every system works on a test placement too.
 * <p>
 * Persistent state lives in {@code KotaTemaData}: {@link #kota} per town ({@code kota("pelabuhan", situs)}) and
 * {@link #pemain} per player ({@code bagian("pelabuhan")["p_<uuid>"]}). New worlds only: no versions, no migration.
 */
public final class PelabuhanKota {

	/** Theme id ({@code KotaTemaData.bagian}, calendar, commands). */
	public static final String TEMA = "pelabuhan";
	/** A position this far from the centre still belongs to the town (the 288 square plus the breakwater). */
	public static final int JANGKAU_KOTA = 220;
	/** Half side of the walled square around the centre district. */
	public static final int SETENGAH = 150;

	/** One loaded harbour town. */
	public static final class Hidup {
		public final long situs;
		public final ResourceKey<Level> dimensi;
		/** The planned town, or null for a test placement. */
		public final @Nullable TemaKota.Kota kota;
		public long town;
		public BlockPos pusat;
		/** Loaded anchors of this town by {@code what}. */
		public final Map<String, List<DuniaAnchorEntity>> jangkar = new LinkedHashMap<>();

		Hidup(long situs, ResourceKey<Level> dimensi, @Nullable TemaKota.Kota kota, BlockPos pusat) {
			this.situs = situs;
			this.dimensi = dimensi;
			this.kota = kota;
			this.pusat = pusat.immutable();
		}

		/** The loaded anchors of one kind (empty when none). */
		public List<DuniaAnchorEntity> jangkar(String what) {
			List<DuniaAnchorEntity> list = jangkar.get(what);
			if (list == null) {
				return List.of();
			}
			list.removeIf(DuniaAnchorEntity::isRemoved);
			return list;
		}

		/** The loaded anchor of kind {@code what} whose data {@code nomor} is {@code nomor}, or null. */
		public @Nullable DuniaAnchorEntity jangkar(String what, int nomor) {
			for (DuniaAnchorEntity a : jangkar(what)) {
				if (PelabuhanAnchors.angka(a, "nomor", -1) == nomor) {
					return a;
				}
			}
			return null;
		}

		/** Horizontal distance from the centre. */
		public double jarak(Vec3 at) {
			double dx = at.x - (pusat.getX() + 0.5D);
			double dz = at.z - (pusat.getZ() + 0.5D);
			return Math.sqrt(dx * dx + dz * dz);
		}

		/** The town's display name ("Pelabuhan Cahaya Zaitun"). */
		public Component nama(MinecraftServer server) {
			return kota != null ? kota.nama(server.overworld().getSeed()) : Component.translatable("kultivator.kota_tema.pelabuhan.nama_uji");
		}
	}

	private static final Map<Long, Hidup> KOTA = new LinkedHashMap<>();

	// ================================================================== registry

	/** Registers the anchor's town (from {@link PelabuhanAnchors#loaded}); never null for an overworld anchor. */
	static Hidup daftar(ServerLevel level, DuniaAnchorEntity anchor) {
		BlockPos at = anchor.blockPosition();
		TemaKota.Kota born = TemaKota.di(level, at);
		Hidup kota;
		if (born != null && born.tema() == Tema.PELABUHAN) {
			kota = KOTA.get(born.situsId());
			if (kota == null) {
				BlockPos centre = born.distrik("pasar_laut");
				kota = new Hidup(born.situsId(), level.dimension(), born, centre != null ? centre : born.pusat());
				KOTA.put(kota.situs, kota);
			}
		} else {
			Hidup near = terdekat(level, Vec3.atCenterOf(at), JANGKAU_KOTA);
			if (near != null) {
				kota = near;
			} else {
				long id = anchor.site() != 0L ? anchor.site() : at.asLong();
				kota = KOTA.computeIfAbsent(id, k -> new Hidup(k, level.dimension(), null, at));
			}
		}
		if (kota.town == 0L) {
			long town = Towns.townOf(anchor);
			kota.town = town != 0L ? town : kota.situs;
		}
		List<DuniaAnchorEntity> list = kota.jangkar.computeIfAbsent(anchor.what(), k -> new ArrayList<>());
		if (!list.contains(anchor)) {
			list.add(anchor);
		}
		return kota;
	}

	/** Forgets one anchor (unload); the town stays known. */
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

	/** The town with town id {@code situs} (or Nama Baik id), or null. */
	public static @Nullable Hidup cari(long situs) {
		Hidup kota = KOTA.get(situs);
		if (kota != null) {
			return kota;
		}
		for (Hidup each : KOTA.values()) {
			if (each.town == situs) {
				return each;
			}
		}
		return null;
	}

	/** The live town whose centre is nearest {@code at} within {@code range} blocks (same dimension), or null. */
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

	/** The live town {@code player} stands in, or null. */
	public static @Nullable Hidup di(ServerPlayer player) {
		return player.level() instanceof ServerLevel level ? terdekat(level, player.position(), JANGKAU_KOTA) : null;
	}

	/** The live town of an anchor (registered when it loaded), or null. */
	static @Nullable Hidup dari(DuniaAnchorEntity anchor) {
		for (Hidup kota : KOTA.values()) {
			List<DuniaAnchorEntity> list = kota.jangkar.get(anchor.what());
			if (list != null && list.contains(anchor)) {
				return kota;
			}
		}
		return null;
	}

	// ================================================================== persistent state

	/** The theme section {@code bagian("pelabuhan")}. */
	public static CompoundTag bagian(MinecraftServer server) {
		return KotaTemaData.get(server).bagian(TEMA);
	}

	/** Per-town state {@code kota("pelabuhan", situs)}. */
	public static CompoundTag kota(MinecraftServer server, long situs) {
		return KotaTemaData.get(server).kota(TEMA, situs);
	}

	/** A child compound of {@code parent}, created and attached on first use. */
	public static CompoundTag anak(CompoundTag parent, String key) {
		CompoundTag tag = parent.getCompoundOrEmpty(key);
		if (!parent.contains(key)) {
			parent.put(key, tag);
		}
		return tag;
	}

	/** Per-player state of the theme ({@code bagian("pelabuhan")["p_<uuid>"]}), created on first use. */
	public static CompoundTag pemain(MinecraftServer server, UUID player) {
		return anak(bagian(server), "p_" + player);
	}

	/** Marks the theme data dirty. */
	public static void ubah(MinecraftServer server) {
		KotaTemaData.get(server).ubah();
	}

	// ================================================================== time

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

	/** True while the world thunders (the harbour's sea storm, read only; never changed). */
	public static boolean badai(ServerLevel level) {
		return level.getServer().overworld().isThundering();
	}

	// ================================================================== hashing

	/** A stable 64-bit mix (deterministic per place and salt). */
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

	// ================================================================== items

	/** Gives a stack (made by the caller just now) to the player's inventory, or at his feet when full. */
	public static void beri(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (player.getInventory().add(stack) && stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack.copy());
		item.setTarget(player.getUUID());
		item.setNoPickUpDelay();
		player.level().addFreshEntity(item);
		stack.setCount(0);
	}

	// ================================================================== switches

	/** True while the theme's behaviour runs ({@code kotaTema.aktif} and the harbour switches). */
	public static boolean aktif() {
		return TemaConfig.aktif(Tema.PELABUHAN);
	}

	/** The harbour switches section. */
	public static TemaConfig.Pelabuhan cfg() {
		return TemaConfig.pelabuhan();
	}

	private PelabuhanKota() {
	}
}
