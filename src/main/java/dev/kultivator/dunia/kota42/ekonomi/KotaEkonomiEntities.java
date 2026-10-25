package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import net.minecraft.world.entity.EntityType;

/**
 * The two economy entity types of DESIGN 42 (42.12.2), registered always by {@link #register()}; {@code noSave},
 * {@code noSummon}, no loot table, fire immune, refused on load and counted under {@link KotaAcaraEntities#ANGGARAN} (the
 * shared 42 helpers and load guard live in {@link KotaAcaraEntities}). Renderers are 42V's.
 * <ul>
 *   <li>{@code kultivator:lapak_hantu} {@link LapakHantuEntity} 2 x 1.5 (Ghost Market stall, 42.6.7)</li>
 *   <li>{@code kultivator:tenda_lapak} {@link TendaLapakEntity} 2 x 2.4 (rented market stall, 42.6.2)</li>
 * </ul>
 */
public final class KotaEkonomiEntities {

	public static final EntityType<LapakHantuEntity> LAPAK_HANTU = KotaAcaraEntities.mekanisme("lapak_hantu", LapakHantuEntity::new, 2.0F, 1.5F, 6, 20);
	public static final EntityType<TendaLapakEntity> TENDA_LAPAK = KotaAcaraEntities.mekanisme("tenda_lapak", TendaLapakEntity::new, 2.0F, 2.4F, 8, 20);

	/** Loads the types above and the shared 42 load guard; safe to call twice. */
	public static void register() {
		KotaAcaraEntities.register();
	}

	private KotaEkonomiEntities() {
	}
}
