package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.LembarKotaDasar;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The lot board of a growth plot (DESIGN 42.11.13: the plot sheet "opened at the lot sign"). Worldgen draws a log post with
 * a plank and a slab two blocks to the left of the lot's gate, seen from the street ({@code kota_hidup_kit.kavling}); the
 * client paints "Kavling Kota" on it. Right-clicking that post with an empty hand opens the plot sheet for that lot
 * ({@code LembarKotaDasar.bukaDi}, writes allowed within reach of the post). Any wooden block on the street row of the
 * fence ring, one to three blocks beside the gate and up to three high, counts as the post; nothing else changes.
 */
final class PapanKavling {

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		UseBlockCallback.EVENT.register(PapanKavling::klik);
	}

	private static InteractionResult klik(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !KotaConfig.tumbuh().aktif || !player.getMainHandItem().isEmpty() || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!state.is(BlockTags.PLANKS) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.WOODEN_SLABS)) {
			return InteractionResult.PASS;
		}
		TempatInfo place = TempatApi.tempatDi(level, pos);
		if (place == null) {
			return InteractionResult.PASS;
		}
		for (Titik t : TempatApi.titik(level, place.situsId(), "kavling")) {
			if (!papan(t, pos)) {
				continue;
			}
			for (Kavling.Info k : Kavling.daftar(place.situsId())) {
				if (k.pos().equals(t.pos())) {
					LembarKotaDasar.setPilih(sp, KotaLembar.Kavling.LAYAR, Integer.toString(k.id()));
					break;
				}
			}
			return LembarKotaDasar.bukaDi(sp, KotaLembar.Kavling.LAYAR, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.PASS;
	}

	/** True when {@code pos} is where the board post of the lot {@code t} stands (street row of the ring, 1-3 beside the gate, 1-3 high). */
	static boolean papan(Titik t, BlockPos pos) {
		BoundingBox box = t.kotak();
		Direction h = t.hadap();
		if (!h.getAxis().isHorizontal() || pos.getY() < box.minY() + 1 || pos.getY() > box.minY() + 3) {
			return false;
		}
		BlockPos c = box.getCenter();
		boolean zAxis = h.getAxis() == Direction.Axis.Z;
		int ring = zAxis ? (h == Direction.SOUTH ? box.maxZ() + 1 : box.minZ() - 1) : (h == Direction.EAST ? box.maxX() + 1 : box.minX() - 1);
		if ((zAxis ? pos.getZ() : pos.getX()) != ring) {
			return false;
		}
		int side = Math.abs(zAxis ? pos.getX() - c.getX() : pos.getZ() - c.getZ());
		return side >= 1 && side <= 3;
	}

	private PapanKavling() {
	}
}
