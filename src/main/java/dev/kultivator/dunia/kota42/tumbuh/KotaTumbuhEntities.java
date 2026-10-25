package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import net.minecraft.world.entity.EntityType;

/**
 * The growth entity type of DESIGN 42 (42.12.2): {@code kultivator:tenda_pengungsi} {@link TendaPengungsiEntity} 1.6 x 1.8
 * (refugee tent while a really destroyed house is rebuilt, 42.9.5). Registered always by {@link #register()} through the
 * shared helper of {@link KotaAcaraEntities} (noSave, refused on load, budget {@link KotaAcaraEntities#ANGGARAN}).
 * Renderer: 42V.
 */
public final class KotaTumbuhEntities {

	public static final EntityType<TendaPengungsiEntity> TENDA_PENGUNGSI = KotaAcaraEntities.mekanisme("tenda_pengungsi", TendaPengungsiEntity::new, 1.6F, 1.8F, 8, 40);

	/** Loads the type above and the shared 42 load guard; safe to call twice. */
	public static void register() {
		KotaAcaraEntities.register();
	}

	private KotaTumbuhEntities() {
	}
}
