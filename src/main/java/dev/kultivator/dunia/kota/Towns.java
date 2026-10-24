package dev.kultivator.dunia.kota;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Rencana;
import dev.kultivator.dunia.tempat.RencanaRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The towns the server has seen (DESIGN 34.5.1, 34.5.4, 38.B.2.6): a town is named by its site id (the {@code kota/kota}
 * or {@code kota/kota_abadi} site of its structure start, the same for every anchor of one town), its centre is the
 * square's anchor (else the structure start), and its record lives in {@link KotaData} ({@code pos}, {@code dim},
 * {@code abadi}, {@code jenis}).
 * <p>
 * Planned places of DESIGN 38 are towns too: every anchor of the eleven district starts of a capital
 * ({@code kultivator:kota_besar/*}) names the capital's {@code kota/kota_besar} site, and every anchor of a themed city
 * ({@code kultivator:kota_tema/*}) its {@code kota/kota_tema} site. Their site is published by the {@code kota/situs}
 * anchor of the central district, whose structure start is the chunk of the plan's centre, so the id is known from the
 * plan alone ({@link #situsRencana}) and is the same for all districts (38KS).
 * <p>
 * Towns are safe ground: no rival ambush starts within {@link #safeRadius} blocks of the centre ({@link #SAFE_RADIUS}
 * for a town, {@link #SAFE_RADIUS_KOTA_BESAR} for a capital, {@link #SAFE_RADIUS_KOTA_TEMA} for a themed city).
 */
public final class Towns {

	/** Safe radius of an ordinary town (34.5.4); also the anchor search radius of rival ambush checks. */
	public static final int SAFE_RADIUS = 80;
	/** Safe radius of a capital (38.B.2.6): the walls, the slum and the caravan field. */
	public static final int SAFE_RADIUS_KOTA_BESAR = 320;
	/** Safe radius of a themed city (38.T.14.3). */
	public static final int SAFE_RADIUS_KOTA_TEMA = 160;

	public static final String JENIS_KOTA = "kota/kota";
	public static final String JENIS_ABADI = "kota/kota_abadi";
	public static final String JENIS_KOTA_BESAR = "kota/kota_besar";
	public static final String JENIS_KOTA_TEMA = "kota/kota_tema";

	/** How far from a plan's centre an anchor may stand and still belong to it (capital: outer districts at 296 + 80). */
	private static final int JANGKAU_RENCANA = 420;

	/** The town an anchor belongs to: its id (0 when the anchor is no town's). */
	public static long townOf(DuniaAnchorEntity anchor) {
		String jenis = jenisOf(anchor);
		if ((JENIS_KOTA_BESAR.equals(jenis) || JENIS_KOTA_TEMA.equals(jenis)) && anchor.level() instanceof ServerLevel level) {
			long planned = planned(level, anchor.blockPosition(), jenis);
			if (planned != 0L) {
				return planned;
			}
		}
		return anchor.siteOf(jenis);
	}

	/** The site kind of the town an anchor belongs to ({@code kota/kota}, {@code kota/kota_abadi}, {@code kota/kota_besar}, ...). */
	public static String jenisOf(DuniaAnchorEntity anchor) {
		String structure = anchor.structure();
		if (structure.startsWith("kultivator:kota_besar/")) {
			return JENIS_KOTA_BESAR;
		}
		if (structure.startsWith("kultivator:kota_tema/")) {
			return JENIS_KOTA_TEMA;
		}
		return isAbadi(anchor) ? JENIS_ABADI : JENIS_KOTA;
	}

	public static boolean isAbadi(DuniaAnchorEntity anchor) {
		return anchor.what().startsWith("abadi") || anchor.structure().endsWith("kota_abadi");
	}

	/**
	 * The site id of the planned place of kind {@code jenis} ({@code kota/kota_besar} or {@code kota/kota_tema}) whose centre
	 * lies within {@value #JANGKAU_RENCANA} blocks of {@code pos}, or 0. Pure plan data: no chunk is read.
	 */
	public static long planned(ServerLevel level, BlockPos pos, String jenis) {
		if (level.dimension() != Level.OVERWORLD) {
			return 0L;
		}
		long seed = level.getSeed();
		Kandidat best = null;
		double bestSq = (double) JANGKAU_RENCANA * JANGKAU_RENCANA;
		for (String id : RencanaRegistry.ids()) {
			boolean match = JENIS_KOTA_BESAR.equals(jenis) ? "kota_besar".equals(id) : id.startsWith("kota_tema_");
			if (!match) {
				continue;
			}
			Rencana plan = RencanaRegistry.get(id);
			if (plan == null) {
				continue;
			}
			for (Kandidat k : plan.kandidat(seed)) {
				double dx = k.pusatX() - pos.getX();
				double dz = k.pusatZ() - pos.getZ();
				double d = dx * dx + dz * dz;
				if (d <= bestSq) {
					bestSq = d;
					best = k;
				}
			}
		}
		return best == null ? 0L : situsRencana(level.dimension(), best, jenis);
	}

	/** The site id the central district of planned place {@code k} publishes for kind {@code jenis}. */
	public static long situsRencana(ResourceKey<Level> dimension, Kandidat k, String jenis) {
		return DuniaSites.idOf(dimension, new BlockPos((k.chunkX() << 4) + 8, 0, (k.chunkZ() << 4) + 8), jenis);
	}

	/** Records the town (first anchor seen) and, for the square, its centre. */
	static void remember(ServerLevel level, DuniaAnchorEntity anchor, long town) {
		KotaData data = KotaData.get(level.getServer());
		CompoundTag tag = data.town(town);
		boolean changed = false;
		if (!tag.contains("dim")) {
			tag.putString("dim", level.dimension().identifier().toString());
			tag.putBoolean("abadi", isAbadi(anchor));
			BlockPos origin = anchor.origin() != null ? anchor.origin() : anchor.blockPosition();
			tag.putLong("pos", origin.asLong());
			changed = true;
		}
		String jenis = jenisOf(anchor);
		if (!jenis.equals(tag.getStringOr("jenis", ""))) {
			tag.putString("jenis", jenis);
			changed = true;
		}
		if (("alun".equals(anchor.what()) || "abadi_gerbang".equals(anchor.what())) && !tag.getBooleanOr("centred", false)) {
			tag.putLong("pos", anchor.blockPosition().asLong());
			tag.putBoolean("centred", true);
			changed = true;
		}
		if (changed) {
			data.touch();
		}
	}

	/** The site kind of a known town ({@code kota/kota} when it never said). */
	public static String jenis(MinecraftServer server, long town) {
		CompoundTag tag = KotaData.get(server).town(town);
		String jenis = tag.getStringOr("jenis", "");
		if (!jenis.isEmpty()) {
			return jenis;
		}
		return tag.getBooleanOr("abadi", false) ? JENIS_ABADI : JENIS_KOTA;
	}

	/** The safe radius of a known town by its kind (80 town, 320 capital, 160 themed city). */
	public static int safeRadius(MinecraftServer server, long town) {
		return switch (jenis(server, town)) {
			case JENIS_KOTA_BESAR -> SAFE_RADIUS_KOTA_BESAR;
			case JENIS_KOTA_TEMA -> SAFE_RADIUS_KOTA_TEMA;
			default -> SAFE_RADIUS;
		};
	}

	/** True when the known town is a capital (DESIGN 38.B). */
	public static boolean isKotaBesar(MinecraftServer server, long town) {
		return JENIS_KOTA_BESAR.equals(jenis(server, town));
	}

	/** The centre of a known town, or null. */
	public static @Nullable BlockPos centre(MinecraftServer server, long town) {
		CompoundTag tag = KotaData.get(server).town(town);
		return tag.contains("pos") ? BlockPos.of(tag.getLongOr("pos", 0L)) : null;
	}

	public static @Nullable ResourceKey<Level> dimension(MinecraftServer server, long town) {
		CompoundTag tag = KotaData.get(server).town(town);
		Identifier id = Identifier.tryParse(tag.getStringOr("dim", ""));
		return id == null || !tag.contains("dim") ? null : ResourceKey.create(Registries.DIMENSION, id);
	}

	/** The id of the known town whose centre is within {@code radius} of {@code pos} in {@code level}, or 0. */
	public static long near(ServerLevel level, BlockPos pos, int radius) {
		long best = 0L;
		double bestSq = (double) radius * radius;
		for (long id : known(level.getServer())) {
			CompoundTag tag = KotaData.get(level.getServer()).town(id);
			if (!level.dimension().identifier().toString().equals(tag.getStringOr("dim", ""))) {
				continue;
			}
			BlockPos centre = BlockPos.of(tag.getLongOr("pos", 0L));
			double dx = centre.getX() - pos.getX();
			double dz = centre.getZ() - pos.getZ();
			double d = dx * dx + dz * dz;
			if (d <= bestSq) {
				bestSq = d;
				best = id;
			}
		}
		return best;
	}

	/** The known town whose own safe radius ({@link #safeRadius}) covers {@code pos}, nearest first, or 0. */
	public static long within(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		long best = 0L;
		double bestSq = Double.MAX_VALUE;
		for (long id : known(server)) {
			CompoundTag tag = KotaData.get(server).town(id);
			if (!level.dimension().identifier().toString().equals(tag.getStringOr("dim", ""))) {
				continue;
			}
			BlockPos centre = BlockPos.of(tag.getLongOr("pos", 0L));
			double dx = centre.getX() - pos.getX();
			double dz = centre.getZ() - pos.getZ();
			double d = dx * dx + dz * dz;
			int r = safeRadius(server, id);
			if (d <= (double) r * r && d < bestSq) {
				bestSq = d;
				best = id;
			}
		}
		return best;
	}

	/** True when {@code pos} lies inside a town's safe ground (no rival ambush; radius by town kind). */
	public static boolean inTown(ServerLevel level, BlockPos pos) {
		return within(level, pos) != 0L;
	}

	/** Every known town id. */
	public static List<Long> known(MinecraftServer server) {
		return new ArrayList<>(KotaData.get(server).townIds());
	}

	private Towns() {
	}
}
