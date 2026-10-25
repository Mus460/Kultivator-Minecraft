package dev.kultivator.dunia.kota42.acara;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The town-life entity types of DESIGN 42 (42.12.2) and the shared registration of every 42 entity: {@link #mekanisme}
 * and {@link #daftar} are used by {@code ekonomi/KotaEkonomiEntities}, {@code sekte/KotaSekteEntities},
 * {@code tumbuh/KotaTumbuhEntities} and {@code hewan/HewanEntities} too, so {@link #semua()} lists all 20 types and one load
 * guard covers them. Every 42 type is {@code noSave}, {@code noSummon}, without loot table, registered always (switches
 * gate behaviour only), refused on load as a safety net (the {@code KotaEntities} pattern) and counted under
 * {@link #ANGGARAN} in {@link DuniaBudget} (the cap, 42.13.3: at most 96, is set by the kota42 root init; spawners check
 * {@code DuniaBudget.mayAdd(level, ANGGARAN)} first). The scenery types extend {@code tema/bersama/TemaMekanismeEntity}
 * (synced {@code varian/keadaan/mulai/nilai}, site binding, invulnerable, no gravity); renderers are 42V's.
 * <ul>
 *   <li>{@code kultivator:barongsai} {@link BarongsaiEntity} 1.2 x 1.6</li>
 *   <li>{@code kultivator:panggung_keliling} {@link PanggungKelilingEntity} 5 x 3</li>
 *   <li>{@code kultivator:tenda_pasar} {@link TendaPasarEntity} 2 x 2.4</li>
 *   <li>{@code kultivator:api_kecil} {@link ApiKecilEntity} 1 x 1.2</li>
 *   <li>{@code kultivator:barang_tercecer} {@link BarangTercecerEntity} 0.4 x 0.3</li>
 *   <li>{@code kultivator:usungan_peti} {@link UsunganPetiEntity} 1 x 1</li>
 *   <li>{@code kultivator:meja_jamuan} {@link MejaJamuanEntity} 1.6 x 0.8</li>
 *   <li>{@code kultivator:meja_dadu} {@link MejaDaduEntity} 1.3 x 0.9</li>
 *   <li>{@code kultivator:layar_bayang} {@link LayarBayangEntity} 2 x 1.6</li>
 * </ul>
 */
public final class KotaAcaraEntities {

	/** {@link DuniaBudget} kind of every 42 entity. */
	public static final String ANGGARAN = "kota42";
	/** 42.13.3: at most 96 entities of 42 per server. */
	public static final int ANGGARAN_MAKS = 96;

	private static final List<EntityType<?>> SEMUA = new ArrayList<>();
	private static final Set<EntityType<?>> MILIK = new HashSet<>();

	public static final EntityType<BarongsaiEntity> BARONGSAI = mekanisme("barongsai", BarongsaiEntity::new, 1.2F, 1.6F, 10, 2);
	public static final EntityType<PanggungKelilingEntity> PANGGUNG_KELILING = mekanisme("panggung_keliling", PanggungKelilingEntity::new, 5.0F, 3.0F, 10, 20);
	public static final EntityType<TendaPasarEntity> TENDA_PASAR = mekanisme("tenda_pasar", TendaPasarEntity::new, 2.0F, 2.4F, 8, 20);
	public static final EntityType<ApiKecilEntity> API_KECIL = mekanisme("api_kecil", ApiKecilEntity::new, 1.0F, 1.2F, 8, 10);
	public static final EntityType<BarangTercecerEntity> BARANG_TERCECER = mekanisme("barang_tercecer", BarangTercecerEntity::new, 0.4F, 0.3F, 4, 20);
	public static final EntityType<UsunganPetiEntity> USUNGAN_PETI = mekanisme("usungan_peti", UsunganPetiEntity::new, 1.0F, 1.0F, 8, 2);
	public static final EntityType<MejaJamuanEntity> MEJA_JAMUAN = mekanisme("meja_jamuan", MejaJamuanEntity::new, 1.6F, 0.8F, 6, 20);
	public static final EntityType<MejaDaduEntity> MEJA_DADU = mekanisme("meja_dadu", MejaDaduEntity::new, 1.3F, 0.9F, 6, 10);
	public static final EntityType<LayarBayangEntity> LAYAR_BAYANG = mekanisme("layar_bayang", LayarBayangEntity::new, 2.0F, 1.6F, 8, 20);

	private static boolean registered;

	/** Loads the types above and installs the load guard and budget tracking of every 42 type; safe to call twice. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD || !milik42(entity));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (milik42(entity)) {
				DuniaBudget.track(ANGGARAN, entity);
			}
		});
	}

	/** True for an entity of any 42 type (scenery, economy, sect, growth and animals). */
	public static boolean milik42(Entity entity) {
		return MILIK.contains(entity.getType());
	}

	/** Every 42 entity type in registration order (renderer registration checks, test commands). */
	public static List<EntityType<?>> semua() {
		return Collections.unmodifiableList(SEMUA);
	}

	/**
	 * Registers a 42 scenery type: {@code MISC}, {@code w} x {@code h}, fire immune, never saved or summoned, no loot table,
	 * tracked within {@code trackChunks} chunks, position updates every {@code updateInterval} ticks.
	 */
	public static <T extends Entity> EntityType<T> mekanisme(String id, EntityType.EntityFactory<T> factory, float w, float h,
			int trackChunks, int updateInterval) {
		return daftar(id, EntityType.Builder.of(factory, MobCategory.MISC)
				.sized(w, h)
				.fireImmune()
				.noSave()
				.noSummon()
				.noLootTable()
				.clientTrackingRange(trackChunks)
				.updateInterval(updateInterval));
	}

	/** Registers a 42 type from a full builder (the caller sets {@code noSave/noSummon/noLootTable}) and adds it to the guard. */
	public static <T extends Entity> EntityType<T> daftar(String id, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id(id));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		SEMUA.add(type);
		MILIK.add(type);
		return type;
	}

	private KotaAcaraEntities() {
	}
}
