package dev.kultivator.sect.build.engine;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.BuildFxIds;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The builder's own blocks never turn into items (DESIGN 21.13): a player breaking a sect cell gets the vanilla break
 * cancelled and the placed cost refunded to the site owner's ledger ({@code sectBuildBreakRefund}, creative too, no XP);
 * explosions take sect cells without drops and credit them ({@code sectBuildExplosionCredit}, through
 * {@code SectExplosionMixin}); natural monster spawning on sect cells is refused ({@code sectBuildSpawnProof}, through
 * {@code SectSpawnMixin}, the image of Yue Lu Hall's covering formation, <i>A Record of a Mortal's Journey</i> ch. 153).
 * Breaking someone else's sect block logs a rate-limited line in that hall's Catatan.
 */
public final class BreakRefund {

	/** Catatan lines about others breaking sect blocks: at most one per breaker per minute. */
	private static final long LOG_TICKS = 1200L;
	private static final Map<UUID, Long> LAST_LOG = new HashMap<>();

	static void init() {
		PlayerBlockBreakEvents.BEFORE.register(BreakRefund::beforeBreak);
	}

	private static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel server)) {
			return true;
		}
		CellIndex index = CellIndex.get(server.getServer());
		if (!index.isPlaced(server, pos)) {
			return true;
		}
		UUID owner = index.owner(server, pos);
		UUID key = owner == null ? new UUID(0L, 0L) : owner;
		Site site = BuildEngine.INSTANCE.siteOf(owner);
		if (site == null || !Classifier.ownStill(server, pos, state)) {
			// A released site, or a block the builder never places on a stale bit (a player's chest, shulker box, beacon):
			// an ordinary block, broken the vanilla way with its contents.
			index.setPlaced(server, pos, key, false);
			return true;
		}
		if (!KultivatorConfig.get().sectBuildBreakRefund) {
			// No refund: the vanilla break drops the block; the cell is no longer the sect's and repair leaves it alone.
			index.setPlaced(server, pos, key, false);
			index.setRemoved(server, pos, key, true);
			return true;
		}
		Bill refund = BuildPlacer.refundRemove(server, site, pos, index);
		Vec3 at = Vec3.atCenterOf(pos);
		Fx.send(server, at, at, FxType.SECT_BUILD, BuildFxIds.DECONSTRUCT, Block.getId(state), 1.0F);
		if (player instanceof ServerPlayer serverPlayer) {
			Bahan best = null;
			long amount = 0L;
			for (Bahan cat : Bahan.values()) {
				if (refund.centi(cat) > amount) {
					amount = refund.centi(cat);
					best = cat;
				}
			}
			if (best != null) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.blueprint.refund", Msg.number(amount / 100.0D), best.displayName())
						.withColor(0x9FF3E6));
			}
			if (!serverPlayer.getUUID().equals(site.ledgerOwner()) && !site.showcase) {
				long now = server.getGameTime();
				Long last = LAST_LOG.get(serverPlayer.getUUID());
				if (last == null || now - last >= LOG_TICKS) {
					LAST_LOG.put(serverPlayer.getUUID(), now);
					Halls.log(site.ledgerOwner(), Component.translatable("kultivator.blueprint.log.broken", serverPlayer.getName()));
				}
			}
		}
		return false;
	}

	/** {@code SectSpawnMixin}: true when natural monster spawning must be refused above {@code below}. */
	public static boolean spawnBlocked(ServerLevel level, MobCategory category, BlockPos below) {
		if (category != MobCategory.MONSTER || !KultivatorConfig.get().sectBuildSpawnProof) {
			return false;
		}
		CellIndex index = CellIndex.get(level.getServer());
		return index.hasAny(level) && index.isPlaced(level, below);
	}

	/**
	 * {@code SectExplosionMixin} (destroying explosions only): takes the sect cells out of an explosion, removing them
	 * without drops and crediting them. A cell whose site is gone or whose block the builder never places is left to the
	 * vanilla explosion (its bit cleared).
	 */
	public static void explosion(ServerLevel level, List<BlockPos> targets) {
		if (!KultivatorConfig.get().sectBuildExplosionCredit || targets.isEmpty()) {
			return;
		}
		CellIndex index = CellIndex.get(level.getServer());
		if (!index.hasAny(level)) {
			return;
		}
		for (Iterator<BlockPos> it = targets.iterator(); it.hasNext(); ) {
			BlockPos pos = it.next();
			if (!index.isPlaced(level, pos)) {
				continue;
			}
			UUID owner = index.owner(level, pos);
			Site site = BuildEngine.INSTANCE.siteOf(owner);
			if (site == null || !Classifier.ownStill(level, pos, level.getBlockState(pos))) {
				index.setPlaced(level, pos, owner == null ? new UUID(0L, 0L) : owner, false);
				continue;
			}
			it.remove();
			BuildPlacer.explosionRemove(level, site, pos, index);
		}
	}

	private BreakRefund() {
	}
}
