package dev.kultivator.dunia.tempat.ui;

import dev.kultivator.Kultivator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Registration and placement of {@code kultivator:papan_hidup} (DESIGN 38.O.2 model by 38MD, content by 38UI). The type
 * is always registered (38.0 rule 17); boards are {@code noSave} and re-made by their anchors with {@link #pasang},
 * which reuses a board already standing within 1.5 blocks, so calling it on every chunk load is safe. Modules then fill
 * the sheets with {@link PapanHidupEntity#setLembar} (rumours 38KS/38ST, wanted posters 38KS, mission slips 38EK).
 */
public final class PapanHidup {

	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("papan_hidup"));
	public static final EntityType<PapanHidupEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
			EntityType.Builder.<PapanHidupEntity>of(PapanHidupEntity::new, MobCategory.MISC)
					.sized(2.2F, 2.4F)
					.noSave()
					.noSummon()
					.noLootTable()
					.fireImmune()
					.clientTrackingRange(6)
					.build(KEY));

	/** Loads the class (the type registers in its static initialiser). Called from {@link TempatLayar#init()}. */
	public static void init() {
		Kultivator.LOGGER.debug("Papan hidup type {}", BuiltInRegistries.ENTITY_TYPE.getKey(TYPE));
	}

	/**
	 * Puts a board of {@code jenis} ({@code PapanHidupEntity.JENIS_*}) standing on {@code kaki} (the block its posts stand
	 * on top of), facing {@code yaw} degrees; returns the existing board there instead when one stands within 1.5 blocks.
	 */
	public static @Nullable PapanHidupEntity pasang(ServerLevel level, BlockPos kaki, float yaw, String jenis) {
		Vec3 at = Vec3.atBottomCenterOf(kaki.above());
		List<PapanHidupEntity> ada = level.getEntitiesOfClass(PapanHidupEntity.class, new AABB(at, at).inflate(1.5D));
		if (!ada.isEmpty()) {
			PapanHidupEntity board = ada.get(0);
			board.setJenis(jenis);
			return board;
		}
		PapanHidupEntity board = TYPE.create(level, EntitySpawnReason.STRUCTURE);
		if (board == null) {
			return null;
		}
		board.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		board.setYHeadRot(yaw);
		board.setJenis(jenis);
		return level.addFreshEntity(board) ? board : null;
	}

	private PapanHidup() {
	}
}
