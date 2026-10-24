package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.TujuanProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Where journeys go (DESIGN 39.6.1): only places the world already knows. Towns from {@code Towns.known} (old towns,
 * the Immortal City, the 38 capital and themed cities), sites of {@code DuniaSites} (ruins and caves of 34, the 38 caves,
 * springs and peaks, villages, road shrines, NPC sect gates and compounds), and the destinations other modules add
 * ({@code MandiriApi.addTujuanProvider}). The radius grows with the realm: Qi Refining 800, Foundation 1500, Golden Core
 * 2500, Nascent Soul and above 4000 blocks; journeys stay in the hall's dimension.
 * <p>
 * The hall keeps a cache in {@code MandiriData.kotaDikenal} rebuilt at most once a world day; when no town is known at
 * all, the hall locates the nearest town once a day ({@code DuniaSites.locate}, at most 64 chunks), never per disciple
 * and never per tick.
 */
public final class Tujuan {

	private static final TagKey<Structure> TAG_KOTA = TagKey.create(Registries.STRUCTURE, Kultivator.id("kota"));

	/** Site kinds a journey kind visits, as {@code DuniaSites} kind prefixes with their default tier. */
	private record Jenis(String prefix, JenisJalan jalan, int tier) {
	}

	private static final List<Jenis> SITUS = List.of(
			new Jenis("reruntuhan/gua_kecil", JenisJalan.RERUNTUHAN, 1),
			new Jenis("reruntuhan/gua_agung", JenisJalan.RERUNTUHAN, 2),
			new Jenis("reruntuhan/gua_kuno", JenisJalan.RERUNTUHAN, 3),
			new Jenis("menara/menara", JenisJalan.RERUNTUHAN, 3),
			new Jenis("gua/makam", JenisJalan.RERUNTUHAN, 2),
			new Jenis("gua/sarang_binatang", JenisJalan.BERBURU, 2),
			new Jenis("gua/pertapa", JenisJalan.GUA_PERTAPA, 1),
			new Jenis("gua/urat_roh", JenisJalan.GUA_PERTAPA, 2),
			new Jenis("situs/mata_air_roh", JenisJalan.GUA_PERTAPA, 1),
			new Jenis("situs/puncak_petir", JenisJalan.GUA_PERTAPA, 2),
			new Jenis("situs/panggung_bintang", JenisJalan.ALAM_RAHASIA, 3),
			new Jenis("kampung/", JenisJalan.DESA, 1),
			new Jenis("desa/desa", JenisJalan.DESA, 1),
			new Jenis("sekte/gerbang", JenisJalan.SEKTE_TAMU, 1),
			new Jenis("sekte/kompleks", JenisJalan.SEKTE_TAMU, 1),
			new Jenis("pos/kuil_jalan", JenisJalan.ZIARAH, 0),
			new Jenis("pos/singgah", JenisJalan.BERKELANA, 1),
			new Jenis("pos/tonggak", JenisJalan.BERKELANA, 1));

	/** The farthest a disciple of this realm travels (blocks). */
	public static int radius(DiscipleData data) {
		Realm realm = data.realm();
		if (realm.ordinal() >= Realm.NASCENT_SOUL.ordinal()) {
			return 4000;
		}
		if (realm == Realm.GOLDEN_CORE) {
			return 2500;
		}
		if (realm == Realm.FOUNDATION) {
			return 1500;
		}
		return 800;
	}

	/**
	 * The destinations of kind {@code jenis} for {@code owner}'s hall at {@code aula} within {@code radius}, nearest first,
	 * without the forbidden ones of {@code kebijakan}. Reads the hall cache (rebuilt once a day).
	 */
	public static List<TujuanProvider.Tujuan> daftar(ServerLevel level, UUID owner, BlockPos aula, JenisJalan jenis, int radius,
			Kebijakan kebijakan) {
		List<TujuanProvider.Tujuan> out = new ArrayList<>();
		for (TujuanProvider.Tujuan t : cache(level, owner, aula)) {
			if (t.jenis() == jenis || jenis == JenisJalan.JUAL && t.jenis() == JenisJalan.BELANJA
					|| jenis == JenisJalan.MISI_GILDA && t.jenis() == JenisJalan.BELANJA) {
				if (jarak(aula, t.pos()) <= radius && !dilarang(kebijakan, t)) {
					out.add(jenis == t.jenis() ? t : new TujuanProvider.Tujuan(t.id(), jenis, t.pos(), t.tier(), t.nama(), t.town(), t.situs()));
				}
			}
		}
		for (TujuanProvider provider : MandiriApi.tujuanProviders()) {
			try {
				for (TujuanProvider.Tujuan t : provider.tujuan(level, owner, aula, jenis, radius)) {
					if (t != null && jarak(aula, t.pos()) <= radius && !dilarang(kebijakan, t)) {
						out.add(t);
					}
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("A Mandiri destination provider failed", exception);
			}
		}
		if (jenis == JenisJalan.SEKTE_TAMU) {
			out.removeIf(t -> !sekteDikenal(owner, t));
		}
		out.sort(java.util.Comparator.comparingDouble(t -> jarak(aula, t.pos())));
		return out;
	}

	/** Horizontal distance in blocks. */
	public static double jarak(BlockPos a, BlockPos b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
	}

	private static boolean dilarang(Kebijakan kebijakan, TujuanProvider.Tujuan t) {
		if (kebijakan == null) {
			return false;
		}
		if (kebijakan.dilarang(t.id()) || t.situs() != null && kebijakan.dilarang(t.situs())) {
			return true;
		}
		String kind = jenisSitus(t);
		return kind != null && (kebijakan.dilarang(kind) || kind.startsWith("kota_tema/") && kebijakan.dilarang(kind.substring(10)));
	}

	/** The site kind stored with a cached destination ({@code kota/kota_besar} ...), or null. */
	static @Nullable String jenisSitus(TujuanProvider.Tujuan t) {
		int bar = t.nama().indexOf('#');
		return t.nama().startsWith("situs#") && bar >= 0 ? t.nama().substring(bar + 1) : null;
	}

	/** A sect gate or compound counts only when the hall has met that sect (and it is not at war with it). */
	private static boolean sekteDikenal(UUID owner, TujuanProvider.Tujuan t) {
		String sect = sekteDi(t.pos());
		if (sect == null) {
			return false;
		}
		Sikap sikap = SekteApi.diplomasi().stance(owner, sect);
		return SekteApi.diplomasi().met(owner, sect) && sikap != Sikap.PERANG && sikap != Sikap.BERMUSUHAN;
	}

	/** The NPC sect whose gate is nearest to {@code pos} (within 160 blocks), or null. */
	static @Nullable String sekteDi(BlockPos pos) {
		String best = null;
		double bestD = 160.0D;
		for (String sect : SekteApi.diplomasi().sects()) {
			BlockPos gate = SekteApi.diplomasi().gate(sect);
			if (gate == null) {
				gate = SekteApi.diplomasi().plannedGate(sect);
			}
			if (gate != null) {
				double d = jarak(gate, pos);
				if (d < bestD) {
					bestD = d;
					best = sect;
				}
			}
		}
		return best;
	}

	// ================================================================== the hall cache

	private static List<TujuanProvider.Tujuan> cache(ServerLevel level, UUID owner, BlockPos aula) {
		MinecraftServer server = level.getServer();
		CompoundTag cache = MandiriApi.aula(server, owner, MandiriIds.A_KOTA);
		long hari = dev.kultivator.dunia.kota.Kota.day(server);
		if (cache.getLongOr("hariCek", -1L) != hari || !cache.getStringOr("dim", "").equals(level.dimension().identifier().toString())) {
			bangun(level, owner, aula, cache, hari);
			MandiriApi.tandai(server);
		}
		ListTag list = cache.getListOrEmpty("tujuan");
		List<TujuanProvider.Tujuan> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = list.getCompoundOrEmpty(i);
			JenisJalan jenis = JenisJalan.byId(one.getStringOr("jenis", ""));
			if (jenis != null) {
				out.add(new TujuanProvider.Tujuan(one.getStringOr("id", ""), jenis, BlockPos.of(one.getLongOr("pos", 0L)), one.getIntOr("tier", 1),
						one.getStringOr("nama", ""), one.getLongOr("town", 0L), one.contains("situs") ? one.getStringOr("situs", "") : null));
			}
		}
		return out;
	}

	/** Forgets the cache (a stele moved, a test). */
	public static void lupakan(MinecraftServer server, UUID owner) {
		MandiriApi.aula(server, owner, MandiriIds.A_KOTA).putLong("hariCek", -1L);
	}

	private static void bangun(ServerLevel level, UUID owner, BlockPos aula, CompoundTag cache, long hari) {
		MinecraftServer server = level.getServer();
		ResourceKey<Level> dim = level.dimension();
		ListTag list = new ListTag();
		int towns = 0;
		for (long town : Towns.known(server)) {
			ResourceKey<Level> townDim = Towns.dimension(server, town);
			BlockPos centre = Towns.centre(server, town);
			if (centre == null || townDim == null || !townDim.equals(dim) || jarak(aula, centre) > 4000.0D) {
				continue;
			}
			String kind = Towns.jenis(server, town);
			int tier = Towns.JENIS_KOTA_BESAR.equals(kind) ? 1 : kind.startsWith(Towns.JENIS_KOTA_TEMA) ? 2 : 1;
			list.add(tag("kota:" + Long.toHexString(town), JenisJalan.BELANJA, centre, tier, "situs#" + kind, town, null));
			towns++;
		}
		for (Jenis j : SITUS) {
			for (DuniaSites.Site site : DuniaSites.near(level, aula, 4000, j.prefix())) {
				if (site.kind().equals(JalanIds.SITUS_TAWANAN)) {
					continue;
				}
				int tier = Math.max(j.tier(), site.tier());
				list.add(tag("situs:" + Long.toHexString(site.id()), j.jalan(), site.pos(), tier, "situs#" + site.kind(), 0L,
						Long.toString(site.id())));
				if (list.size() > 160) {
					break;
				}
			}
		}
		if (towns == 0 && cache.getLongOr("hariCari", -1L) != hari) {
			cache.putLong("hariCari", hari);
			try {
				DuniaSites.Site found = DuniaSites.locate(level, TAG_KOTA, aula, DuniaSites.MAX_LOCATE_CHUNKS, Towns.JENIS_KOTA, 1);
				if (found != null) {
					list.add(tag("situs:" + Long.toHexString(found.id()), JenisJalan.BELANJA, found.pos(), 1, "situs#" + Towns.JENIS_KOTA,
							found.pos().asLong(), Long.toString(found.id())));
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.debug("Mandiri: town locate failed", exception);
			}
		}
		cache.put("tujuan", list);
		cache.putLong("hariCek", hari);
		cache.putString("dim", dim.identifier().toString());
	}

	private static CompoundTag tag(String id, JenisJalan jenis, BlockPos pos, int tier, String nama, long town, @Nullable String situs) {
		CompoundTag one = new CompoundTag();
		one.putString("id", id);
		one.putString("jenis", jenis.id());
		one.putLong("pos", pos.asLong());
		one.putInt("tier", tier);
		one.putString("nama", nama);
		one.putLong("town", town);
		if (situs != null) {
			one.putString("situs", situs);
		}
		return one;
	}

	private Tujuan() {
	}
}
