package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaBudget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The four entity types of DESIGN 41 (41.11.3), registered always (rule K1) from {@link Masuk#registerContent()}; all
 * {@code noSave}, {@code noSummon}, no loot table, fire immune, refused on load as a safety net (the {@code KotaEntities}
 * pattern). They appear only while an entrance test runs and are counted under {@link #ANGGARAN} (41.12.3: every 41 entity
 * on a server &le; {@link #ANGGARAN_MAKS}; the cap is set by {@link Masuk#init()}). The walls of the maze are not tracked one by
 * one: {@code LabirinBatu} checks the budget once for its whole maze.
 * <ul>
 *   <li>{@code kultivator:boneka_ujian} {@link BonekaUjianEntity} 0.6 x 1.8, PEDANG "Seratus Tebasan" (max 6)</li>
 *   <li>{@code kultivator:dinding_ilusi} {@link DindingIlusiEntity} 1 x 3, FORMASI "Labirin Batu" (max 48)</li>
 *   <li>{@code kultivator:pilar_qi} {@link PilarQiEntity} 0.8 x 3, UNSUR "Lima Pilar Qi" (5)</li>
 *   <li>{@code kultivator:cawan_ujian} {@link CawanUjianEntity} 0.5 x 0.4, RACUN "Tiga Cawan" (3)</li>
 * </ul>
 * Renderers and models are 41V's ({@code client/sekte/gabung/model/**}); the class panji ({@code panji_kelas}) is a client
 * drawing, not an entity.
 */
public final class GabungEntities {

	/** {@link DuniaBudget} kind of every 41 entity (tests, war squads and hunters count their bodies here too). */
	public static final String ANGGARAN = "gabung";
	/** 41.12.3: at most 24 entities of 41 per server. */
	public static final int ANGGARAN_MAKS = 24;

	private static final ResourceKey<EntityType<?>> BONEKA_KEY = key("boneka_ujian");
	private static final ResourceKey<EntityType<?>> DINDING_KEY = key("dinding_ilusi");
	private static final ResourceKey<EntityType<?>> PILAR_KEY = key("pilar_qi");
	private static final ResourceKey<EntityType<?>> CAWAN_KEY = key("cawan_ujian");

	public static final EntityType<BonekaUjianEntity> BONEKA_UJIAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, BONEKA_KEY,
			EntityType.Builder.<BonekaUjianEntity>of(BonekaUjianEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.5F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(6)
					.build(BONEKA_KEY));

	public static final EntityType<DindingIlusiEntity> DINDING_ILUSI = Registry.register(BuiltInRegistries.ENTITY_TYPE, DINDING_KEY,
			EntityType.Builder.<DindingIlusiEntity>of(DindingIlusiEntity::new, MobCategory.MISC)
					.sized(1.0F, 3.0F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(6)
					.updateInterval(20)
					.build(DINDING_KEY));

	public static final EntityType<PilarQiEntity> PILAR_QI = Registry.register(BuiltInRegistries.ENTITY_TYPE, PILAR_KEY,
			EntityType.Builder.<PilarQiEntity>of(PilarQiEntity::new, MobCategory.MISC)
					.sized(0.8F, 3.0F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(8)
					.updateInterval(10)
					.build(PILAR_KEY));

	public static final EntityType<CawanUjianEntity> CAWAN_UJIAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, CAWAN_KEY,
			EntityType.Builder.<CawanUjianEntity>of(CawanUjianEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.4F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(4)
					.build(CAWAN_KEY));

	private static boolean registered;

	/** Loads the types above and the load guard; safe to call twice (both physical sides). */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD || !milik41(entity));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// The maze walls are one maze for the budget (48 invisible colliders would eat the cap of 24 by themselves).
			if (milik41(entity) && !(entity instanceof DindingIlusiEntity)) {
				DuniaBudget.track(ANGGARAN, entity);
			}
		});
		// The corak tests' hooks (41B-C): idle until a test starts, and a test only starts while 41 runs.
		UjianCorak.init();
	}

	/** True for an entity of one of the four 41 types. */
	public static boolean milik41(Entity entity) {
		return entity instanceof BonekaUjianEntity || entity instanceof DindingIlusiEntity || entity instanceof PilarQiEntity
				|| entity instanceof CawanUjianEntity;
	}

	private static ResourceKey<EntityType<?>> key(String name) {
		return ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id(name));
	}

	private GabungEntities() {
	}
}
