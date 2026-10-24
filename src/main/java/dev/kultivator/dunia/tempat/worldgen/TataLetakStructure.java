package dev.kultivator.dunia.tempat.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.LotPick;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.Rencana;
import dev.kultivator.dunia.tempat.RencanaRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code kultivator:tata_letak} (DESIGN 38.B.2.3): a structure that lays fixed lots (position AND rotation fixed by a
 * shared deterministic plan, not jigsaw) for one district of a multi-start place: the eleven districts of a capital,
 * the 1-3 districts of a sect compound, the 9-10 districts of a themed city. JSON fields besides the vanilla structure
 * settings: {@code rencana} (plan id of {@link RencanaRegistry}), {@code distrik}, {@code lot} (list of {@link Lot})
 * and {@code gaya} (biome styles, {@link Gaya}).
 * <p>
 * Generation: (1) the plan says whether this chunk starts this district; (2) the plan's shared terrain verdict
 * ({@link PutusanTanah}) must be suitable, so all districts of one place are born or none; (3) the style is the first
 * {@code gaya} whose biomes contain the biome at the place centre, and a pool {@code <pool>_<akhiran>} replaces
 * {@code <pool>} when it is registered; (4) each lot element is a hashed weighted pick ({@link LotPick}), its rotation
 * the place rotation plus the lot's own {@code putar}, its north-west corner (before rotation) at {@code dx, dz} from
 * the district centre; (5) every lot becomes a vanilla {@code PoolElementStructurePiece} (piece type JIGSAW), so
 * starts save and load in the vanilla format. The lot floor (template {@code y = 4}, 4 foundation layers below)
 * replaces the ground surface block: piece {@code y = dasarY - 1 - 4 + dy}; the piece ground level delta is 5, so
 * {@code beard_box} shapes terrain up to the street. All lots of a district stay within +-80 blocks of the start
 * chunk (inside the 8-chunk reference range of {@code ChunkGenerator.createReferences}).
 */
public final class TataLetakStructure extends Structure {

	/** Lot floor height inside a lot template (4 foundation layers under it), tools/tempat_lib.py LANTAI_Y. */
	public static final int LANTAI_Y = 4;

	/**
	 * One lot: {@code dx, dz} from the district centre to the lot's north-west corner before rotation, {@code dy} extra
	 * floor height, {@code pool} the template pool, {@code putar} the lot's own clockwise quarter turns.
	 */
	public record Lot(int dx, int dz, int dy, Holder<StructureTemplatePool> pool, int putar) {

		public static final Codec<Lot> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(-160, 160).fieldOf("dx").forGetter(Lot::dx),
				Codec.intRange(-160, 160).fieldOf("dz").forGetter(Lot::dz),
				Codec.intRange(-64, 128).optionalFieldOf("dy", 0).forGetter(Lot::dy),
				StructureTemplatePool.CODEC.fieldOf("pool").forGetter(Lot::pool),
				Codec.intRange(0, 3).optionalFieldOf("putar", 0).forGetter(Lot::putar))
				.apply(i, Lot::new));
	}

	/** A biome style: when the place centre biome is in {@code biomes}, lot pools gain the suffix {@code _akhiran}. */
	public record Gaya(HolderSet<Biome> biomes, String akhiran) {

		public static final Codec<Gaya> CODEC = RecordCodecBuilder.create(i -> i.group(
				RegistryCodecs.holderSet(Registries.BIOME).fieldOf("biomes").forGetter(Gaya::biomes),
				Codec.STRING.fieldOf("akhiran").forGetter(Gaya::akhiran))
				.apply(i, Gaya::new));
	}

	public static final MapCodec<TataLetakStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Codec.STRING.fieldOf("rencana").forGetter(TataLetakStructure::rencana),
			Codec.STRING.fieldOf("distrik").forGetter(TataLetakStructure::distrik),
			Lot.CODEC.listOf().fieldOf("lot").forGetter(TataLetakStructure::lots),
			Gaya.CODEC.listOf().optionalFieldOf("gaya", List.of()).forGetter(TataLetakStructure::gaya))
			.apply(i, TataLetakStructure::new));

	private final String rencana;
	private final String distrik;
	private final List<Lot> lots;
	private final List<Gaya> gaya;

	public TataLetakStructure(StructureSettings settings, String rencana, String distrik, List<Lot> lots, List<Gaya> gaya) {
		super(settings);
		this.rencana = rencana;
		this.distrik = distrik;
		this.lots = List.copyOf(lots);
		this.gaya = List.copyOf(gaya);
	}

	public String rencana() {
		return rencana;
	}

	public String distrik() {
		return distrik;
	}

	public List<Lot> lots() {
		return lots;
	}

	public List<Gaya> gaya() {
		return gaya;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		Rencana plan = RencanaRegistry.get(rencana);
		Distrik d = plan.distrikDi(context.seed(), context.chunkPos().x(), context.chunkPos().z());
		if (d == null || !d.id().equals(distrik)) {
			return Optional.empty();
		}
		Putusan verdict = plan.putusan(PutusanTanah.Sumber.of(context), d);
		if (!verdict.layak()) {
			return Optional.empty();
		}
		String akhiran = styleOf(context, d, verdict.dasarY());
		Registry<StructureTemplatePool> pools = context.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		StructureTemplateManager manager = context.structureTemplateManager();
		Rotation place = d.putar();
		List<PoolElementStructurePiece> pieces = new ArrayList<>(lots.size());
		for (int index = 0; index < lots.size(); index++) {
			Lot lot = lots.get(index);
			StructureTemplatePool pool = styled(pools, lot.pool(), akhiran);
			StructurePoolElement element = LotPick.pilih(context.seed(), d.kandidat(), distrik, index, pool);
			if (element == EmptyPoolElement.INSTANCE) {
				continue;
			}
			Rotation own = Rotation.values()[lot.putar()];
			Vec3i size = element.getSize(manager, Rotation.NONE);
			int[] corner = cornerOffset(own, size.getX(), size.getZ());
			int[] world = rotate(place, lot.dx() + corner[0], lot.dz() + corner[1]);
			BlockPos pos = new BlockPos(d.pusatX() + world[0], verdict.dasarY() - 1 - LANTAI_Y + d.dy() + lot.dy(), d.pusatZ() + world[1]);
			Rotation rotation = place.getRotated(own);
			BoundingBox box = element.getBoundingBox(manager, pos, rotation);
			pieces.add(new PoolElementStructurePiece(manager, element, pos, LANTAI_Y + 1, rotation, box, LiquidSettings.APPLY_WATERLOGGING));
		}
		if (pieces.isEmpty()) {
			return Optional.empty();
		}
		BlockPos start = new BlockPos(d.pusatX(), verdict.dasarY(), d.pusatZ());
		return Optional.of(new GenerationStub(start, builder -> pieces.forEach(builder::addPiece)));
	}

	private String styleOf(GenerationContext context, Distrik d, int y) {
		if (gaya.isEmpty()) {
			return "";
		}
		Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(d.kandidat().pusatX()), QuartPos.fromBlock(y),
				QuartPos.fromBlock(d.kandidat().pusatZ()));
		for (Gaya g : gaya) {
			if (g.biomes().contains(biome)) {
				return g.akhiran();
			}
		}
		return "";
	}

	private static StructureTemplatePool styled(Registry<StructureTemplatePool> pools, Holder<StructureTemplatePool> base, String akhiran) {
		if (!akhiran.isEmpty()) {
			Optional<ResourceKey<StructureTemplatePool>> key = base.unwrapKey();
			if (key.isPresent()) {
				ResourceKey<StructureTemplatePool> styledKey = ResourceKey.create(Registries.TEMPLATE_POOL,
						key.get().identifier().withSuffix("_" + akhiran));
				Optional<Holder.Reference<StructureTemplatePool>> found = pools.get(styledKey);
				if (found.isPresent()) {
					return found.get().value();
				}
			}
		}
		return base.value();
	}

	/** Offset keeping a template rotated by {@code r} about its origin inside its unrotated cell {@code [0, w) x [0, d)}. */
	public static int[] cornerOffset(Rotation r, int w, int d) {
		return switch (r) {
			case CLOCKWISE_90 -> new int[]{d - 1, 0};
			case CLOCKWISE_180 -> new int[]{w - 1, d - 1};
			case COUNTERCLOCKWISE_90 -> new int[]{0, w - 1};
			default -> new int[]{0, 0};
		};
	}

	/** {@code (x, z)} rotated about the origin exactly as {@code StructureTemplate.transform} with pivot 0. */
	public static int[] rotate(Rotation r, int x, int z) {
		return switch (r) {
			case CLOCKWISE_90 -> new int[]{-z, x};
			case CLOCKWISE_180 -> new int[]{-x, -z};
			case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
			default -> new int[]{x, z};
		};
	}

	@Override
	public StructureType<?> type() {
		return TempatTypes.TATA_LETAK;
	}
}
