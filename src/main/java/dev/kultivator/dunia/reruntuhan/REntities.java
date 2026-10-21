package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.reruntuhan.gua.BayanganEntity;
import dev.kultivator.dunia.reruntuhan.gua.BonekaBatuEntity;
import dev.kultivator.dunia.reruntuhan.gua.SisaJiwaEntity;
import dev.kultivator.dunia.reruntuhan.menara.PenjagaLantaiEntity;
import dev.kultivator.dunia.reruntuhan.penjara.IblisKecilEntity;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The entity types of module R (DESIGN 34.6 R). Every one is never written to disk ({@code noSave} plus the
 * {@code ALLOW_LOAD} refusal of {@link RGuard}: state lives in the anchors and the module's saved data), cannot be
 * summoned and has no loot table. Puzzle parts share {@link TekaPartEntity} (role by type), doors and phantom walls
 * share {@link ColliderEntity}.
 */
public final class REntities {

	public static final EntityType<TekaPartEntity> TEKA_PLAT = part("teka_plat", TekaPartEntity.PLATE, 1.0F, 0.2F);
	public static final EntityType<TekaPartEntity> TEKA_LAMPU = part("teka_lampu", TekaPartEntity.LAMP, 0.7F, 0.9F);
	public static final EntityType<TekaPartEntity> TEKA_CINCIN = part("teka_cincin", TekaPartEntity.RING, 0.9F, 0.9F);
	public static final EntityType<TekaPartEntity> TEKA_TIMBANGAN = part("teka_timbangan", TekaPartEntity.PAN, 3.0F, 0.3F);
	public static final EntityType<TekaPartEntity> SEGEL_JIMAT = part("segel_jimat", TekaPartEntity.TALISMAN, 0.8F, 1.4F);
	public static final EntityType<ColliderEntity> TEKA_DINDING = collider("teka_dinding");
	public static final EntityType<ColliderEntity> GERBANG_SEGEL = collider("gerbang_segel");

	public static final EntityType<SisaJiwaEntity> SISA_JIWA = register("sisa_jiwa",
			EntityType.Builder.<SisaJiwaEntity>of(SisaJiwaEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F), 8);
	public static final EntityType<BayanganEntity> BAYANGAN = register("bayangan",
			EntityType.Builder.<BayanganEntity>of(BayanganEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).eyeHeight(1.62F), 8);
	public static final EntityType<BonekaBatuEntity> BONEKA_BATU = register("boneka_batu",
			EntityType.Builder.<BonekaBatuEntity>of(BonekaBatuEntity::new, MobCategory.MONSTER).sized(1.5F, 3.0F), 8);
	public static final EntityType<RajaIblisEntity> RAJA_IBLIS = register("raja_iblis",
			EntityType.Builder.<RajaIblisEntity>of(RajaIblisEntity::new, MobCategory.MONSTER).sized(2.2F, 4.4F), 10);
	public static final EntityType<IblisKecilEntity> IBLIS_KECIL = register("iblis_kecil",
			EntityType.Builder.<IblisKecilEntity>of(IblisKecilEntity::new, MobCategory.MONSTER).sized(0.7F, 2.0F).eyeHeight(1.7F), 8);
	public static final EntityType<PenjagaLantaiEntity> PENJAGA_LANTAI = register("penjaga_lantai",
			EntityType.Builder.<PenjagaLantaiEntity>of(PenjagaLantaiEntity::new, MobCategory.MONSTER).sized(2.0F, 2.4F), 10);

	private static EntityType<TekaPartEntity> part(String id, int role, float width, float height) {
		return register(id, EntityType.Builder.<TekaPartEntity>of((type, level) -> new TekaPartEntity(type, level, role), MobCategory.MISC)
				.sized(width, height), 6);
	}

	private static EntityType<ColliderEntity> collider(String id) {
		return register(id, EntityType.Builder.<ColliderEntity>of(ColliderEntity::new, MobCategory.MISC).sized(1.0F, 3.0F), 6);
	}

	private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder<T> builder, int tracking) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id(id));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				builder.fireImmune().noSave().noSummon().noLootTable().clientTrackingRange(tracking).build(key));
	}

	/** Registers the default attributes of the living types. */
	static void init() {
		FabricDefaultAttributeRegistry.register(SISA_JIWA, SisaJiwaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BAYANGAN, BayanganEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BONEKA_BATU, BonekaBatuEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(RAJA_IBLIS, RajaIblisEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(IBLIS_KECIL, IblisKecilEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PENJAGA_LANTAI, PenjagaLantaiEntity.createAttributes());
	}

	private REntities() {
	}
}
