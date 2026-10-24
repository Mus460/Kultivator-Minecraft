package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.ToDoubleBiFunction;

/**
 * The public face of DESIGN 38 places (contract C38.W1, owner 38W). Every 38 module and every later design reads
 * places only through here. The place index has three sources:
 * <ul>
 *   <li>planned places (capital, sect compounds, themed cities): the district boxes of every plan, counted only when
 *       the place passes its shared terrain verdict ({@link TempatZones#rencanaDi}); site id = the published site of
 *       that kind near the place centre, else {@link #situsRencana};</li>
 *   <li>runtime boxes modules publish from their anchors ({@code TempatZones.daftar}: villages, posts, seals);</li>
 *   <li>published sites ({@code DuniaSites}) for {@link #dekat}.</li>
 * </ul>
 * Bounded place factors ({@link #faktor}) and named compound spots ({@link #spot}) come from providers that their
 * owner modules register ({@link #daftarFaktor}, {@link #daftarSpot}); with none registered the answers stay neutral.
 * Nothing here loads or generates a chunk: piece and point lookups only read loaded chunks.
 */
public final class TempatApi {

	/** Place factor keys that cross-module hooks may ask for (38.0 rule 4; each bounded by its owner). */
	public static final List<String> FAKTOR = List.of("kesengsaraan", "iblis_hati", "warisan", "bintang", "binatang_xp");

	/** Bounds of every factor key {min, max} (38.0 rule 4: tribulation damage >= 0.85, other chances <= +10 points). */
	private static final Map<String, double[]> BATAS = Map.of(
			"kesengsaraan", new double[]{0.85D, 1.0D},
			"iblis_hati", new double[]{0.5D, 1.0D},
			"warisan", new double[]{1.0D, 1.10D},
			"bintang", new double[]{1.0D, 2.0D},
			"binatang_xp", new double[]{1.0D, 1.25D});

	private record Faktor(String kunci, ToDoubleBiFunction<ServerPlayer, String> fungsi) {
	}

	private static final List<Faktor> FAKTOR_PROVIDERS = new CopyOnWriteArrayList<>();
	private static volatile @Nullable MinecraftServer SERVER;
	private static final List<BiFunction<String, String, BlockPos>> SPOT_PROVIDERS = new CopyOnWriteArrayList<>();

	/**
	 * Registers a place factor provider for {@code kunci} (38GU: Puncak Petir tribulation 0.90, Gua Cermin Hati, Makam
	 * Warisan, Panggung Bintang, Sarang Binatang). The provider answers 1.0 when the player is not at its place; all
	 * answers are multiplied and clamped to the key's bounds.
	 */
	public static void daftarFaktor(String kunci, ToDoubleBiFunction<ServerPlayer, String> provider) {
		FAKTOR_PROVIDERS.add(new Faktor(kunci, provider));
	}

	/** Registers a named-spot provider ({@code (sekteId, titik) -> pos or null}; 38DS compounds, 38TK themed cities). */
	public static void daftarSpot(BiFunction<String, String, BlockPos> provider) {
		SPOT_PROVIDERS.add(provider);
	}

	/** From {@code TempatSpine}: the running server (null when stopped). */
	static void server(@Nullable MinecraftServer server) {
		SERVER = server;
	}

	/** The place at {@code pos}, or null. */
	public static @Nullable TempatInfo tempatDi(ServerLevel level, BlockPos pos) {
		if (!TempatConfig.get().aktif) {
			return null;
		}
		if (level.dimension() == Level.OVERWORLD) {
			PutusanTanah.Sumber sumber = TempatZones.sumber(level);
			for (TempatZones.Masuk m : TempatZones.rencanaDi(level, pos.getX(), pos.getZ())) {
				Distrik d = m.distrik();
				if (d == null || !m.rencana().putusan(sumber, d).layak()) {
					continue;
				}
				String jenis = jenisRencana(d.kandidat().rencana());
				return new TempatInfo(situsDi(level, d.kandidat(), jenis), jenis, kotakKandidat(level.getSeed(), m.rencana(), d.kandidat()),
						kekuatanRencana(d.kandidat().rencana()), d.id());
			}
		}
		for (TempatZones.Dinamis d : TempatZones.dinamisDi(level.dimension(), pos.getX(), pos.getZ())) {
			return new TempatInfo(d.situsId(), d.jenis(), d.kotak(), d.kekuatan(), null);
		}
		return null;
	}

	/** Places within {@code r} blocks of {@code pos} whose kind starts with {@code awalanJenis} ("" = all), nearest first. */
	public static List<TempatInfo> dekat(ServerLevel level, BlockPos pos, int r, String awalanJenis) {
		List<TempatInfo> out = new ArrayList<>();
		List<Long> seen = new ArrayList<>();
		if (level.dimension() == Level.OVERWORLD && TempatConfig.get().aktif) {
			PutusanTanah.Sumber sumber = TempatZones.sumber(level);
			long seed = level.getSeed();
			for (String id : RencanaRegistry.ids()) {
				Rencana plan = RencanaRegistry.get(id);
				for (Kandidat k : plan.kandidat(seed)) {
					if (Math.hypot(k.pusatX() - pos.getX(), k.pusatZ() - pos.getZ()) > r + 300) {
						continue;
					}
					String jenis = jenisRencana(id);
					if (!jenis.startsWith(awalanJenis)) {
						continue;
					}
					Distrik first = pertama(plan, seed, k);
					if (first == null || !plan.putusan(sumber, first).layak()) {
						continue;
					}
					long situs = situsDi(level, k, jenis);
					seen.add(situs);
					out.add(new TempatInfo(situs, jenis, kotakKandidat(seed, plan, k), kekuatanRencana(id), null));
				}
			}
		}
		for (DuniaSites.Site s : DuniaSites.near(level, pos, r, awalanJenis)) {
			if (seen.contains(s.id())) {
				continue;
			}
			BoundingBox box = null;
			int strength = s.tier();
			for (TempatZones.Dinamis d : TempatZones.semuaDinamis()) {
				if (d.situsId() == s.id()) {
					box = d.kotak();
					strength = Math.max(strength, d.kekuatan());
				}
			}
			if (box == null) {
				box = new BoundingBox(s.pos().getX() - 16, s.pos().getY() - 8, s.pos().getZ() - 16, s.pos().getX() + 16, s.pos().getY() + 16,
						s.pos().getZ() + 16);
			}
			out.add(new TempatInfo(s.id(), s.kind(), box, strength, null));
		}
		out.sort(Comparator.comparingDouble(t -> {
			BlockPos c = t.kotak().getCenter();
			return Math.hypot(c.getX() - pos.getX(), c.getZ() - pos.getZ());
		}));
		return out;
	}

	/**
	 * Where a player arriving at {@code site} (teleport, cloud journey, quest pointer) should stand: the capital's central
	 * square (district {@code pusat}, on the street floor), a themed city's {@code kota_tema/tiba} anchor (outside the
	 * Skull Gate of Kota Iblis), a village's bell square ({@code kampung/lonceng}) or a road post's yard. Null when the
	 * place has no arrival point (the caller then uses its old rule).
	 */
	public static @Nullable BlockPos arrival(DuniaSites.Site site) {
		MinecraftServer server = SERVER;
		ServerLevel level = server == null ? null : server.getLevel(site.dim());
		if (level == null) {
			return null;
		}
		if (site.kind().equals("kota/kota_besar") && level.dimension() == Level.OVERWORLD) {
			PutusanTanah.Sumber sumber = TempatZones.sumber(level);
			for (Kandidat k : RencanaKotaBesar.of(level.getSeed()).kandidat()) {
				if (Math.hypot(k.pusatX() - site.pos().getX(), k.pusatZ() - site.pos().getZ()) > 300) {
					continue;
				}
				Putusan p = RencanaKotaBesar.akhir(sumber, k);
				if (p.layak()) {
					int[] o = dev.kultivator.dunia.tempat.worldgen.TataLetakStructure.rotate(net.minecraft.world.level.block.Rotation.values()[k.putar()], 0, 20);
					return new BlockPos(k.pusatX() + o[0], p.dasarY(), k.pusatZ() + o[1]);
				}
			}
		}
		String prefix = site.kind().startsWith("kota_tema/") ? "kota_tema/tiba" : site.kind().startsWith("kampung/") ? "kampung/lonceng"
				: site.kind().startsWith("pos/") ? site.kind() : null;
		if (prefix != null) {
			DuniaAnchorEntity a = DuniaAnchors.nearest(level, Vec3.atCenterOf(site.pos()), prefix, 320.0D);
			if (a != null) {
				return a.blockPosition();
			}
		}
		return null;
	}

	/**
	 * A standing spot at column {@code (x, z)} near {@code nearY} (38.0 rule 12). Outside every place: the
	 * {@code MOTION_BLOCKING_NO_LEAVES} surface when it is solid, not liquid, with two free blocks above. Inside a planned
	 * place: the street floor of its terrain verdict ({@code dasarY}) when that cell is open (never the roof). Inside a
	 * runtime place box: null (its module knows its points).
	 */
	public static @Nullable BlockPos groundSpot(ServerLevel level, int x, int z, int nearY) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		if (TempatZones.inside(level, x, z)) {
			if (level.dimension() != Level.OVERWORLD) {
				return null;
			}
			PutusanTanah.Sumber sumber = TempatZones.sumber(level);
			for (TempatZones.Masuk m : TempatZones.rencanaDi(level, x, z)) {
				Distrik d = m.distrik();
				if (d == null) {
					continue;
				}
				Putusan p = m.rencana().putusan(sumber, d);
				if (p.layak()) {
					BlockPos feet = new BlockPos(x, p.dasarY() + d.dy(), z);
					return berdiri(level, feet) ? feet : null;
				}
			}
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= level.getMinY() || Math.abs(y - nearY) > 48) {
			return null;
		}
		BlockPos feet = new BlockPos(x, y, z);
		return berdiri(level, feet) ? feet : null;
	}

	private static boolean berdiri(ServerLevel level, BlockPos feet) {
		BlockState below = level.getBlockState(feet.below());
		if (!below.isFaceSturdy(level, feet.below(), Direction.UP) || !below.getFluidState().isEmpty()) {
			return false;
		}
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}

	/** A named spot of an NPC sect's compound ({@code puncak}, {@code arena}, ...), or null (providers, 38.C.3.2). */
	public static @Nullable BlockPos spot(String sekteId, String titik) {
		for (BiFunction<String, String, BlockPos> provider : SPOT_PROVIDERS) {
			try {
				BlockPos p = provider.apply(sekteId, titik);
				if (p != null) {
					return p;
				}
			} catch (RuntimeException e) {
				Kultivator.LOGGER.warn("Tempat spot provider failed for {} {}", sekteId, titik, e);
			}
		}
		return null;
	}

	/** True when {@code player} has the 38 screen {@code apa} open (sessions tracked by {@link TempatLayar}). */
	public static boolean viewing(ServerPlayer player, String apa) {
		return TempatLayar.sedangMelihat(player, apa);
	}

	/**
	 * The generated pieces of place {@code situsId} (in start order, then piece order), from LOADED chunks only: for a
	 * planned place every district start, else the structure start holding the site position.
	 */
	public static List<PotonganInfo> potongan(ServerLevel level, long situsId) {
		List<PotonganInfo> out = new ArrayList<>();
		DuniaSites.Site site = DuniaSites.byId(level.getServer(), situsId);
		BlockPos at = site != null ? site.pos() : null;
		if (level.dimension() == Level.OVERWORLD) {
			long seed = level.getSeed();
			for (String id : RencanaRegistry.ids()) {
				Rencana plan = RencanaRegistry.get(id);
				for (Kandidat k : plan.kandidat(seed)) {
					String jenis = jenisRencana(id);
					boolean match = situsRencana(k, jenis) == situsId
							|| at != null && kotakKandidat(seed, plan, k).isInside(new BlockPos(at.getX(), 0, at.getZ()));
					if (!match) {
						continue;
					}
					for (Distrik d : plan.semuaDistrik(seed)) {
						if (d.kandidat().equals(k)) {
							pieces(level, d.chunkX(), d.chunkZ(), out);
						}
					}
					return out;
				}
			}
		}
		if (at != null && level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
			StructureStart start = level.structureManager().getStructureWithPieceAt(at,
					holder -> holder.unwrapKey().map(key -> Kultivator.MOD_ID.equals(key.identifier().getNamespace())).orElse(false));
			if (start.isValid()) {
				addPieces(start, out);
			}
		}
		return out;
	}

	private static void pieces(ServerLevel level, int cx, int cz, List<PotonganInfo> out) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
		if (chunk == null) {
			return;
		}
		for (StructureStart start : chunk.getAllStarts().values()) {
			if (start.isValid()) {
				addPieces(start, out);
			}
		}
	}

	private static void addPieces(StructureStart start, List<PotonganInfo> out) {
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single) {
				try {
					Identifier template = single.getTemplateLocation();
					out.add(new PotonganInfo(template, pool.getPosition(), pool.getRotation(), pool.getBoundingBox()));
				} catch (RuntimeException ignored) {
					// an inline template (no location): nothing to read points from
				}
			}
		}
	}

	/** The life points of place {@code situsId} of type {@code jenis} ("" = all), world coordinates (loaded pieces). */
	public static List<Titik> titik(ServerLevel level, long situsId, String jenis) {
		List<Titik> out = new ArrayList<>();
		for (PotonganInfo p : potongan(level, situsId)) {
			for (Titik t : TitikTemplat.dunia(level.getServer(), p.templat(), p.pos(), p.rot())) {
				if (jenis.isEmpty() || t.jenis().equals(jenis) || jenis.endsWith(":") && t.jenis().startsWith(jenis)) {
					out.add(t);
				}
			}
		}
		return out;
	}

	/**
	 * The bounded place factor {@code kunci} ({@link #FAKTOR}) for {@code player} where he stands: e.g. 0.90 tribulation
	 * damage on Puncak Petir. 1.0 means no effect. Providers are multiplied and the result clamped to the key's bounds.
	 */
	public static double faktor(ServerPlayer player, String kunci) {
		if (!TempatConfig.get().aktif) {
			return 1.0D;
		}
		double f = 1.0D;
		for (Faktor provider : FAKTOR_PROVIDERS) {
			if (!provider.kunci().equals(kunci)) {
				continue;
			}
			try {
				double v = provider.fungsi().applyAsDouble(player, kunci);
				if (Double.isFinite(v) && v > 0.0D) {
					f *= v;
				}
			} catch (RuntimeException e) {
				Kultivator.LOGGER.warn("Tempat factor provider failed for {}", kunci, e);
			}
		}
		double[] b = BATAS.get(kunci);
		return b == null ? f : Math.clamp(f, b[0], b[1]);
	}

	/** The site kind of a plan's places ({@code kota/kota_besar}, {@code sekte/kompleks}, {@code kota_tema/<tema>}). */
	public static String jenisRencana(String rencana) {
		if (rencana.equals(RencanaKotaBesar.ID)) {
			return "kota/kota_besar";
		}
		if (rencana.equals("kompleks_sekte")) {
			return "sekte/kompleks";
		}
		if (rencana.startsWith("kota_tema_")) {
			return "kota_tema/" + rencana.substring("kota_tema_".length());
		}
		return "tempat/" + rencana;
	}

	/** The synthetic site id of a planned place (used until its site anchor publishes the real one). */
	public static long situsRencana(Kandidat k, String jenis) {
		return DuniaSites.idOf(Level.OVERWORLD, new BlockPos(k.pusatX(), 0, k.pusatZ()), jenis);
	}

	private static long situsDi(ServerLevel level, Kandidat k, String jenis) {
		List<DuniaSites.Site> near = DuniaSites.near(level, new BlockPos(k.pusatX(), 64, k.pusatZ()), 260, jenis);
		return near.isEmpty() ? situsRencana(k, jenis) : near.getFirst().id();
	}

	private static int kekuatanRencana(String rencana) {
		return rencana.equals(RencanaKotaBesar.ID) ? 5 : rencana.startsWith("kota_tema_") ? 4 : 3;
	}

	private static @Nullable Distrik pertama(Rencana plan, long seed, Kandidat k) {
		for (Distrik d : plan.semuaDistrik(seed)) {
			if (d.kandidat().equals(k)) {
				return d;
			}
		}
		return null;
	}

	/** The box of every district of {@code k} (safe boxes when the plan has them, else keep-out boxes). */
	private static BoundingBox kotakKandidat(long seed, Rencana plan, Kandidat k) {
		if (plan == RencanaKotaBesar.INSTANCE) {
			return RencanaKotaBesar.kotakKota(k);
		}
		BoundingBox box = null;
		for (ZonaRencana z : plan.zonaDistrik(seed)) {
			if (!z.distrik().kandidat().equals(k)) {
				continue;
			}
			BoundingBox b = z.aman() != null ? z.aman() : z.kotak();
			box = box == null ? b : BoundingBox.encapsulatingBoxes(List.of(box, b)).orElse(box);
		}
		return box != null ? box : new BoundingBox(k.pusatX() - 64, -64, k.pusatZ() - 64, k.pusatX() + 64, 320, k.pusatZ() + 64);
	}

	private TempatApi() {
	}
}
