package dev.kultivator.dunia.kota42.sekte;

import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import net.minecraft.world.entity.EntityType;

/**
 * The sect-and-town entity type of DESIGN 42 (42.12.2): {@code kultivator:batu_uji} {@link BatuUjiEntity} 0.6 x 0.9 (the
 * recruitment office's root-testing stone, 42.8.1). Registered always by {@link #register()} through the shared helper of
 * {@link KotaAcaraEntities} (noSave, refused on load, budget {@link KotaAcaraEntities#ANGGARAN}). The branch sign block
 * {@code papan_cabang} is registered with the other 42 blocks in {@code ekonomi/KotaEkonomiBlocks}. Renderer: 42V.
 */
public final class KotaSekteEntities {

	public static final EntityType<BatuUjiEntity> BATU_UJI = KotaAcaraEntities.mekanisme("batu_uji", BatuUjiEntity::new, 0.6F, 0.9F, 6, 20);

	/** Loads the type above and the shared 42 load guard; safe to call twice. */
	public static void register() {
		KotaAcaraEntities.register();
	}

	private KotaSekteEntities() {
	}
}
